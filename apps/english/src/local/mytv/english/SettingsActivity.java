package local.mytv.english;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import local.mytv.english.RemoteNavigation.Screen;

/** D-pad-first launcher. A destination remembers both its entry point and its focused tile. */
public final class SettingsActivity extends Activity {
    public static boolean foreground;
    private StudyRepository repository;
    private StudyScreen study;
    private final RemoteNavigation navigation = new RemoteNavigation(new Screen("home"));
    private Screen pendingGate;
    private String pageName = "home";
    private LinearLayout content;
    private ScrollView scroll;
    private View pageRoot, defaultFocus;
    private final List<View> targets = new ArrayList<>(), menu = new ArrayList<>();
    private final Map<String, View> menuByName = new HashMap<>();
    private Button[][] pinGrid;
    private final Handler handler = new Handler();
    private int renderVersion;
    private long confirmVersion;
    private TextView balanceLabel, dailyLabel, statusLabel, monitorAdvice, pinDots, pinMessage;
    private boolean parentAuthenticated, confirmingPin, pinSetup, pinBusy;
    private String pendingPin, pinInput = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (foreground) { refreshHomeCounters(); handler.postDelayed(this, 1000); } }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            repository = StudyRepository.get(this);
            if (state != null) {
                ArrayList<Bundle> saved = state.getParcelableArrayList("navigation");
                if (saved != null) {
                    List<Screen> entries = new ArrayList<>();
                    for (Bundle b : saved) {
                        String name = b.getString("page", "home");
                        boolean transientForm = "pin".equals(name) || "confirm".equals(name);
                        if (transientForm) name = "parent";
                        Screen entry = new Screen(name, transientForm ? "" : b.getString("argument", ""), transientForm ? 0 : b.getInt("index"));
                        entry.focus = b.getString("focus"); entry.contentFocus = b.getString("contentFocus"); entry.scroll = b.getInt("scroll");
                        if (entries.isEmpty() || !entries.get(entries.size() - 1).same(entry)) entries.add(entry);
                    }
                    navigation.restore(entries);
                }
            }
            renderCurrent();
        } catch (RuntimeException e) { fatal(); }
    }
    @Override public void onResume() {
        super.onResume(); foreground = true; handler.removeCallbacks(tick); handler.post(tick);
        if (repository == null) return;
        if (study != null) study.resume(); else renderCurrent();
    }
    @Override public void onPause() {
        remember(); foreground = false; handler.removeCallbacks(tick);
        if (study != null) study.pauseAudio();
        parentAuthenticated = false; pinInput = ""; pendingPin = null; confirmingPin = false; pinBusy = false;
        if ("pin".equals(navigation.current().name) && "change-new".equals(navigation.current().argument)) navigation.backTo("parent");
        super.onPause();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        remember(); ArrayList<Bundle> saved = new ArrayList<>();
        for (Screen entry : navigation.entries()) {
            Bundle b = new Bundle(); b.putString("page", entry.name); b.putString("argument", entry.argument); b.putInt("index", entry.index);
            b.putString("focus", entry.focus); b.putString("contentFocus", entry.contentFocus); b.putInt("scroll", entry.scroll); saved.add(b);
        }
        state.putParcelableArrayList("navigation", saved); super.onSaveInstanceState(state);
    }
    @Override public void onDestroy() { stopMedia(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onBackPressed() { goBack(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if ("pin".equals(pageName)) {
            if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) {
                if (event.getAction() == KeyEvent.ACTION_UP) pinDigit(Integer.toString(key - KeyEvent.KEYCODE_0)); return true;
            }
            if (key == KeyEvent.KEYCODE_DEL) { if (event.getAction() == KeyEvent.ACTION_UP) pinDelete(); return true; }
        }
        if (study == null && event.getAction() == KeyEvent.ACTION_DOWN && !menu.isEmpty()) {
            View focus = getCurrentFocus();
            if (key == KeyEvent.KEYCODE_DPAD_UP && !targets.isEmpty() && focus == targets.get(0) && scroll != null) {
                scroll.scrollTo(0, 0); return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && focus != null && !menu.contains(focus)) {
                navigation.current().contentFocus = tag(focus);
                View active = menuByName.get(section()); if (active != null) { active.requestFocus(); return true; }
            }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT && menu.contains(focus)) {
                View next = find(navigation.current().contentFocus);
                if (next == null || !next.isFocusable()) next = defaultFocus;
                if (next != null) { next.requestFocus(); return true; }
            }
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() > 0) return true;
                confirmVersion = renderVersion;
            } else if (event.getAction() == KeyEvent.ACTION_UP && confirmVersion != renderVersion) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void remember() {
        if (pageRoot == null || !pageRoot.isLaidOut()) return;
        View focus = getCurrentFocus(); String key = tag(focus);
        if (key != null) {
            navigation.current().focus = key;
            if (!menu.contains(focus)) navigation.current().contentFocus = key;
        }
        if (scroll != null) navigation.current().scroll = scroll.getScrollY();
    }
    private static String tag(View view) { return view != null && view.getTag() instanceof String ? (String) view.getTag() : null; }
    private View find(String key) { return key == null || pageRoot == null ? null : pageRoot.findViewWithTag(key); }
    private void go(Screen target) {
        remember();
        if ("home".equals(target.name)) { navigation.reset(target); parentAuthenticated = false; }
        else navigation.open(target);
        renderCurrent();
    }
    private void repaint() { remember(); renderCurrent(); }
    private void goBack() {
        boolean cancelGate = "pin".equals(navigation.current().name);
        pendingGate = null; pinInput = ""; pendingPin = null;
        Screen previous = navigation.back();
        while (cancelGate && previous != null && protectedPage(previous.name) && !parentAuthenticated) previous = navigation.back();
        if (previous == null) { stopMedia(); super.onBackPressed(); return; }
        if ("home".equals(previous.name)) parentAuthenticated = false;
        renderCurrent();
    }
    private void returnToParent() {
        if (!navigation.backTo("parent")) { navigation.reset(new Screen("home")); navigation.open(new Screen("parent")); }
        renderCurrent();
    }
    private static boolean protectedPage(String name) {
        return "parent".equals(name) || "monitor".equals(name) || "confirm".equals(name) || "unitpicker".equals(name) || "sources".equals(name) || "license".equals(name);
    }
    private String section() {
        List<Screen> entries = navigation.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            String name = entries.get(i).name;
            if ("home".equals(name) || "map".equals(name) || "records".equals(name) || "parent".equals(name)) return name;
        }
        return "home";
    }
    private void stopMedia() { if (study != null) { study.close(); study = null; } }
    private void renderCurrent() {
        if (repository == null) return;
        stopMedia(); renderVersion++; scroll = null; pageRoot = null; defaultFocus = null;
        balanceLabel = null; dailyLabel = null; statusLabel = null; monitorAdvice = null; pinGrid = null;
        targets.clear(); menu.clear(); menuByName.clear();
        Screen destination = navigation.current();
        if (protectedPage(destination.name) && !parentAuthenticated) {
            pendingGate = destination; navigation.replace(new Screen("pin", "unlock")); destination = navigation.current();
        }
        pageName = destination.name;
        try {
            switch (destination.name) {
                case "home": home(); break;
                case "map": map(); break;
                case "unit": unit(destination.argument, destination.index); break;
                case "word": word(destination.argument); break;
                case "study": startStudy("demo".equals(destination.argument)); break;
                case "records": records(); break;
                case "review": reviewList(); break;
                case "parent": parent(); break;
                case "monitor": monitor(); break;
                case "pin": renderPin(destination.argument); break;
                case "confirm": confirmAction(destination.argument); break;
                case "unitpicker": unitPicker(); break;
                case "sources": sources(); break;
                case "license": license(destination.argument); break;
                default: navigation.reset(new Screen("home")); home();
            }
            if (study == null && pageRoot != null) wireFocus();
        } catch (RuntimeException e) { fatal(); }
    }

    private void shell(String title, String subtitle) {
        boolean modal = "pin".equals(pageName) || "confirm".equals(pageName);
        LinearLayout root = TvTheme.row(this); root.setGravity(Gravity.TOP); root.setBackgroundColor(TvTheme.CREAM);
        root.setPadding(dp(30), dp(22), dp(30), dp(22));
        if (!modal) {
            LinearLayout nav = TvTheme.column(this); nav.setPadding(0, dp(6), dp(18), 0);
            nav.addView(new AdventureArt(this), new LinearLayout.LayoutParams(-1, dp(88)));
            nav.addView(TvTheme.title(this, "英语探险", 23));
            menuItem(nav, "学习首页", "home"); menuItem(nav, "探险地图", "map");
            menuItem(nav, "学习记录", "records"); menuItem(nav, "家长配置", "parent");
            root.addView(nav, new LinearLayout.LayoutParams(dp(168), -1));
        }
        scroll = new ScrollView(this) {
            @Override public void requestChildFocus(View child, View focused) {
                super.requestChildFocus(child, focused);
                // Android normally reveals only the first card, leaving the nonfocusable title above it hidden.
                if (!targets.isEmpty() && focused == targets.get(0)) post(() -> scrollTo(0, 0));
            }
        }; scroll.setSmoothScrollingEnabled(false); scroll.setFillViewport(true); scroll.setClipChildren(false); scroll.setClipToPadding(false);
        content = TvTheme.column(this); content.setPadding(dp(modal ? 96 : 10), dp(4), dp(modal ? 96 : 10), dp(18));
        content.addView(TvTheme.title(this, title, 30));
        if (subtitle != null && !subtitle.isEmpty()) TvTheme.add(content, TvTheme.text(this, subtitle, 17, TvTheme.MUTED), 5);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2)); root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        pageRoot = root; setContentView(root); TvTheme.enter(content);
    }
    private int dp(int value) { return TvTheme.dp(this, value); }
    private void menuItem(LinearLayout nav, String label, String name) {
        Button item = TvTheme.button(this, label, name.equals(section()), () -> {
            if ("parent".equals(name) && !protectedPage(navigation.current().name)) parentAuthenticated = false;
            go(new Screen(name));
        });
        item.setTextSize(18); item.setId(View.generateViewId()); item.setTag("nav:" + name);
        TvTheme.add(nav, item, 10); menu.add(item); menuByName.put(name, item);
    }
    private void register(View view, String key) {
        if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
        view.setTag(key); targets.add(view); if (defaultFocus == null) defaultFocus = view;
    }
    private LinearLayout tile(String key, String title, String detail, Runnable action) {
        LinearLayout tile = TvTheme.card(this, TvTheme.WHITE);
        tile.addView(TvTheme.title(this, title, 23));
        if (detail != null && !detail.isEmpty()) TvTheme.add(tile, TvTheme.text(this, detail, 18, TvTheme.MUTED), 6);
        TvTheme.selectableCard(tile, TvTheme.WHITE, action); register(tile, key); TvTheme.add(content, tile, 12); return tile;
    }
    private void action(String key, String label, Runnable action) { tile(key, label, "", action); }
    private void wireFocus() {
        if (pinGrid != null) {
            for (int r = 0; r < 4; r++) for (int c = 0; c < 3; c++) {
                Button key = pinGrid[r][c]; key.setNextFocusUpId(pinGrid[Math.max(0, r - 1)][c].getId());
                key.setNextFocusDownId(pinGrid[Math.min(3, r + 1)][c].getId());
                key.setNextFocusLeftId(pinGrid[r][Math.max(0, c - 1)].getId()); key.setNextFocusRightId(pinGrid[r][Math.min(2, c + 1)].getId());
            }
        } else {
            View activeMenu = menuByName.get(section());
            for (int n = 0; n < targets.size(); n++) {
                View target = targets.get(n); target.setNextFocusUpId(targets.get(Math.max(0, n - 1)).getId());
                target.setNextFocusDownId(targets.get(Math.min(targets.size() - 1, n + 1)).getId());
                target.setNextFocusRightId(target.getId()); target.setNextFocusLeftId(activeMenu == null ? target.getId() : activeMenu.getId());
            }
        }
        for (int n = 0; n < menu.size(); n++) {
            View item = menu.get(n); item.setNextFocusUpId(menu.get(Math.max(0, n - 1)).getId());
            item.setNextFocusDownId(menu.get(Math.min(menu.size() - 1, n + 1)).getId()); item.setNextFocusLeftId(item.getId());
            if (defaultFocus != null) item.setNextFocusRightId(defaultFocus.getId());
        }
        final int version = renderVersion; final Screen entry = navigation.current();
        pageRoot.post(() -> {
            if (renderVersion != version || navigation.current() != entry) return;
            if (scroll != null) scroll.scrollTo(0, entry.scroll);
            View saved = find(entry.focus); if (saved != null && saved.isFocusable()) saved.requestFocus();
            else if (defaultFocus != null) defaultFocus.requestFocus();
        });
    }

    private void home() {
        AppSettings settings = repository.settings(); StudyModels.Snapshot state = repository.snapshot();
        shell("出发吧！", "");
        LinearLayout hero = TvTheme.card(this, TvTheme.MINT), row = TvTheme.row(this), copy = TvTheme.column(this);
        copy.addView(TvTheme.text(this, settings.stage + " / " + ("KET".equals(settings.stage) ? "A2 基础巩固" : "B1 进阶应用"), 17, TvTheme.TEAL));
        TvTheme.add(copy, TvTheme.title(this, state.activeSessionId != null ? "继续探险  ›" : "开始探险  ›", 28), 6);
        StudyModels.Session current = repository.currentSession(); int count = current != null ? current.taskCount : settings.taskCount;
        long reward = Math.min(current != null ? current.rewardMs : settings.rewardMinutes * 60000L, state.dailyRemainingMs);
        TvTheme.add(copy, TvTheme.text(this, state.dailyRemainingMs == 0 ? "今天继续学，明天再换时" : (state.balanceMs > 0 ? "自由练习" : count + " 题 → +" + TvTheme.minutes(reward)), 21, TvTheme.INK), 8);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(new AdventureArt(this), new LinearLayout.LayoutParams(dp(138), dp(110)));
        hero.addView(row); TvTheme.selectableCard(hero, TvTheme.MINT, () -> go(new Screen("study"))); register(hero, "home:learn"); TvTheme.add(content, hero, 14);
        LinearLayout metrics = TvTheme.row(this), balance = TvTheme.card(this, TvTheme.WHITE), daily = TvTheme.card(this, TvTheme.WHITE);
        balance.addView(TvTheme.text(this, state.balanceMs > 0 ? "本轮可看 · 确认观看 ›" : "本轮可看", 17, TvTheme.MUTED));
        balanceLabel = TvTheme.title(this, TvTheme.duration(state.balanceMs), 31); TvTheme.add(balance, balanceLabel, 4);
        if (state.balanceMs > 0) { TvTheme.selectableCard(balance, TvTheme.WHITE, this::openPlayer); register(balance, "home:watch"); }
        daily.addView(TvTheme.text(this, "今日还可看", 17, TvTheme.MUTED)); dailyLabel = TvTheme.title(this, TvTheme.minutes(state.dailyRemainingMs), 31); TvTheme.add(daily, dailyLabel, 4);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1); size.rightMargin = dp(12);
        metrics.addView(balance, size); metrics.addView(daily, new LinearLayout.LayoutParams(0, -2, 1)); TvTheme.add(content, metrics, 14);
        TvTheme.add(content, TvTheme.text(this, "已学 " + state.learnedCount + " · 待复习 " + state.dueCount + " · 已掌握 " + state.masteredCount, 18, TvTheme.MUTED), 14);
        statusLabel = TvTheme.text(this, "", 15, TvTheme.MUTED); TvTheme.add(content, statusLabel, 8); refreshHomeCounters();
    }
    private void refreshHomeCounters() {
        if (repository == null) return;
        try {
            if ("home".equals(pageName)) {
                StudyModels.Snapshot state = repository.snapshot();
                if (balanceLabel != null) balanceLabel.setText(TvTheme.duration(state.balanceMs));
                if (dailyLabel != null) dailyLabel.setText(TvTheme.minutes(state.dailyRemainingMs));
            }
            if (statusLabel != null) {
                MonitorStatus status = LearningService.monitorStatus(this, repository);
                statusLabel.setText(status.message);
                if (monitorAdvice != null) monitorAdvice.setText(monitorAdvice(status.kind));
            }
        } catch (RuntimeException error) { if (statusLabel != null) statusLabel.setText("本机数据暂不可用，请联系家长"); }
    }
    private void startStudy(boolean demo) {
        study = new StudyScreen(this, repository, () -> {
            if (!demo && repository.snapshot().balanceMs > 0) { goBack(); openPlayer(); } else goBack();
        }, this::goBack, demo);
        pageRoot = study.view(); setContentView(pageRoot);
    }
    private void map() {
        String stage = repository.settings().stage; shell(stage + " 探险地图", "");
        List<Curriculum.Item> items = repository.curriculum().stage(stage); Map<String, StudyModels.ItemProgress> progress = new HashMap<>(); int learnedTotal = 0;
        for (StudyModels.ItemProgress p : repository.progress(stage)) { progress.put(p.itemId, p); if (p.learned) learnedTotal++; }
        TvTheme.add(content, TvTheme.text(this, "已学 " + learnedTotal + " / " + items.size() + " · " + ((items.size() + 19) / 20) + " 个单元", 20, TvTheme.TEAL), 10);
        int current = Math.min(repository.settings().currentUnit, Math.max(0, (items.size() - 1) / 20));
        for (int unit = current; unit * 20 < items.size(); unit++) {
            boolean done = true;
            for (int n = unit * 20; n < Math.min(unit * 20 + 20, items.size()); n++) if (!progress.get(items.get(n).id).learned) { done = false; break; }
            if (!done) { current = unit; break; }
        }
        for (int first = 0; first < items.size(); first += 20) {
            final int start = first; int end = Math.min(first + 20, items.size()), learned = 0, mastered = 0;
            for (int n = first; n < end; n++) { StudyModels.ItemProgress p = progress.get(items.get(n).id); if (p.learned) learned++; if (p.mastered) mastered++; }
            String marker = first / 20 == current ? "● 当前旅程  " : (learned == end - first ? "✓ " : "○ ");
            StringBuilder words = new StringBuilder(); for (int n = first; n < Math.min(first + 3, end); n++) { if (words.length() > 0) words.append(" · "); words.append(items.get(n).word); }
            LinearLayout card = tile("map:unit:" + first, marker + (first / 20 + 1) + "  " + items.get(first).topic + "  ›",
                    "已学 " + learned + "/" + (end - first) + " · 已掌握 " + mastered + "\n" + words, () -> go(new Screen("unit", stage, start)));
            if (first / 20 == current) defaultFocus = card;
        }
    }
    private void unit(String stage, int first) {
        shell(stage + " · 第 " + (first / 20 + 1) + " 单元", ""); List<Curriculum.Item> items = repository.curriculum().stage(stage);
        for (int n = first; n < Math.min(first + 20, items.size()); n++) {
            Curriculum.Item item = items.get(n); tile("unit:word:" + item.id, item.word + "  ›", item.meaning, () -> go(new Screen("word", item.id)));
        }
    }
    private void word(String id) {
        Curriculum.Item item = repository.curriculum().get(id); if (item == null) throw new IllegalStateException("词卡暂不可用");
        study = new StudyScreen(this, repository, this::goBack, this::goBack, false, item); pageRoot = study.view(); setContentView(pageRoot);
    }
    private void records() {
        StudyModels.Snapshot state = repository.snapshot(); shell("我的学习足迹", "");
        LinearLayout overview = tile("records:review", "我的成长 · 复习与错题  ›", "", () -> go(new Screen("review")));
        overview.addView(new LearningCharts(this, state), new LinearLayout.LayoutParams(-1, dp(110)));
        LinearLayout week = tile("records:week", "最近 7 天", "", null);
        week.addView(new LearningCharts(this, repository.history(), state.day), new LinearLayout.LayoutParams(-1, dp(160)));
    }
    private void reviewList() {
        shell("待复习与错题", ""); boolean found = false;
        for (StudyModels.ItemProgress p : repository.progress(repository.settings().stage)) if (p.wrongCount > 0 || (p.learned && !p.mastered)) {
            Curriculum.Item item = repository.curriculum().get(p.itemId); if (item == null) continue; found = true;
            tile("review:word:" + item.id, item.word + "  ›", item.meaning + " · " + (p.wrongCount > 0 ? "错题 " + p.wrongCount + " 次" : "复习中") + " · 下次 " + p.dueDay, () -> go(new Screen("word", item.id)));
        }
        if (!found) TvTheme.add(content, TvTheme.text(this, "暂时没有待复习词汇", 22, TvTheme.MUTED), 24);
    }

    private interface Change { void apply(AppSettings settings); }
    private static int cycle(int value, int[] values) { for (int n = 0; n < values.length; n++) if (values[n] == value) return values[(n + 1) % values.length]; return values[0]; }
    private void setting(String key, String label, String value, Change update) {
        tile(key, label, value, () -> {
            if (!parentAuthenticated) { renderCurrent(); return; }
            try { remember(); AppSettings settings = repository.settings(); update.apply(settings); repository.saveSettings(settings); notifyService(); renderCurrent(); }
            catch (RuntimeException e) { toast("设置未保存，请重试"); }
        });
    }
    private void sectionTitle(String label) { TvTheme.add(content, TvTheme.title(this, label, 22), 20); }
    private void parent() {
        AppSettings s = repository.settings(); shell("家长配置", "确认键切换设置 · 每日上限即时生效");
        statusLabel = TvTheme.text(this, "", 18, TvTheme.TEAL); TvTheme.add(content, statusLabel, 10); refreshHomeCounters();
        sectionTitle("观看规则");
        setting("parent:enabled", "观看管控", s.enabled ? "开启" : "关闭", v -> v.enabled = !v.enabled);
        setting("parent:tasks", "每轮主任务", s.taskCount + " 项", v -> v.taskCount = cycle(v.taskCount, new int[]{3, 5, 8, 10}));
        setting("parent:reward", "每轮兑换", s.rewardMinutes + " 分钟", v -> v.rewardMinutes = cycle(v.rewardMinutes, new int[]{5, 10, 15, 20, 30}));
        setting("parent:limit", "每日观看上限", s.dailyLimitMinutes + " 分钟", v -> v.dailyLimitMinutes = cycle(v.dailyLimitMinutes, new int[]{15, 30, 45, 60, 90, 120}));
        sectionTitle("学习计划");
        setting("parent:stage", "学习阶段", s.stage, v -> { v.stage = "KET".equals(v.stage) ? "PET" : "KET"; v.currentUnit = 0; });
        action("parent:unit", "学习起点 · 第 " + (s.currentUnit + 1) + " 单元  ›", () -> go(new Screen("unitpicker")));
        setting("parent:new", "每日新词上限", s.dailyNewLimit + " 个", v -> v.dailyNewLimit = cycle(v.dailyNewLimit, new int[]{2, 5, 8, 10, 15}));
        sectionTitle("学习辅助");
        setting("parent:volume", "发音音量", s.volume + "%", v -> v.volume = cycle(v.volume, new int[]{25, 50, 80, 100}));
        setting("parent:speak", "单词自动发音", s.autoSpeak ? "开启" : "关闭", v -> v.autoSpeak = !v.autoSpeak);
        setting("parent:hints", "中文辅助", s.chineseHints ? "开启" : "关闭", v -> v.chineseHints = !v.chineseHints);
        sectionTitle("系统与记录"); action("parent:monitor", "监控检查 · 开机恢复  ›", () -> go(new Screen("monitor")));
        action("parent:system", "电视系统设置  ›", this::openSystemSettings);
        action("parent:demo", "体验演示  ›", () -> go(new Screen("study", "demo")));
        action("parent:records", "观看与学习记录  ›", () -> go(new Screen("records")));
        action("parent:sources", "课程与内容来源  ›", () -> go(new Screen("sources")));
        sectionTitle("PIN 与本机数据"); action("parent:pin", "修改家长 PIN  ›", () -> go(new Screen("pin", "change-old")));
        action("parent:defaults", "恢复默认配置  ›", () -> go(new Screen("confirm", "defaults")));
        action("parent:reset", "清除学习与观看记录  ›", () -> go(new Screen("confirm", "reset")));
    }
    private void monitor() {
        shell("监控检查", "开机后在后台恢复，保留电视原画面");
        LinearLayout card = TvTheme.card(this, TvTheme.WHITE);
        statusLabel = TvTheme.title(this, "", 24); statusLabel.setTag("monitor:status"); card.addView(statusLabel);
        monitorAdvice = TvTheme.text(this, "", 19, TvTheme.MUTED); TvTheme.add(card, monitorAdvice, 12);
        TvTheme.add(content, card, 16);
        action("monitor:system", "开启 / 检查无障碍服务  ›", this::openSystemSettings);
        TvTheme.add(content, TvTheme.text(this, "在电视无障碍设置开启「英语学习换时管控」。\n授权保留时，系统会在开机时重新连接，无需先打开英语应用。", 19, TvTheme.MUTED), 16);
        TvTheme.add(content, TvTheme.text(this, "只计云视听小电视的实际播放。零余额先学习；观看时间用完再次学习。暂停、待机和学习期间不扣时。", 19, TvTheme.MUTED), 12);
        TvTheme.add(content, TvTheme.text(this, "部分电视待机会强停应用并关闭无障碍授权。若唤醒后未生效，请重新开启服务；可在电视管家检查后台运行限制。", 17, TvTheme.MUTED), 10);
        refreshHomeCounters();
    }
    private String monitorAdvice(MonitorStatus.Kind kind) {
        switch (kind) {
            case NEEDS_PIN: return "请先在家长配置设置四位 PIN，再开启无障碍服务。";
            case DISABLED: return "请在家长配置开启「观看管控」。";
            case NEEDS_PERMISSION: return "请进入电视系统设置，在无障碍中开启「英语学习换时管控」。";
            case CONNECTING: return "无障碍已开启，正在等待系统连接。若持续未连接，请在系统设置关闭再开启该服务，并允许应用后台运行。";
            case ERROR: return "请检查系统无障碍服务，或重新打开应用重试。学习记录会保留。";
            default: return "服务已连接。重启后由系统恢复；如电视管家限制后台运行，请允许本应用后台运行。";
        }
    }
    private void unitPicker() {
        AppSettings settings = repository.settings(); List<Curriculum.Item> items = repository.curriculum().stage(settings.stage);
        shell("选择学习起点", "");
        for (int first = 0; first < items.size(); first += 20) {
            final int unit = first / 20;
            LinearLayout card = tile("unitpicker:" + unit, (unit == settings.currentUnit ? "● " : "") + "第 " + (unit + 1) + " 单元  ›", items.get(first).topic + " · " + items.get(first).word, () -> {
                if (!parentAuthenticated) { renderCurrent(); return; }
                AppSettings updated = repository.settings(); updated.currentUnit = unit; repository.saveSettings(updated); notifyService(); returnToParent();
            }); if (unit == settings.currentUnit) defaultFocus = card;
        }
    }
    private void confirmAction(String mode) {
        boolean reset = "reset".equals(mode);
        shell(reset ? "清除本机学习记录？" : "恢复默认配置？", reset ? "学习进度、错题、观看余额与历史将清除，无法撤销。" : "观看与学习配置恢复默认值，保留 PIN 和学习记录。");
        tile("confirm:cancel", "保留当前数据", "", this::goBack);
        tile("confirm:yes", reset ? "确认清除  ›" : "确认恢复  ›", "需要再次验证 PIN", () -> go(new Screen("pin", "action-" + mode)));
    }
    private void sources() {
        shell("课程与内容来源", "版本 " + Curriculum.VERSION);
        TvTheme.add(content, TvTheme.text(this, "KET " + repository.curriculum().stage("KET").size() + " 项 · PET " + repository.curriculum().stage("PET").size() + " 项", 23, TvTheme.TEAL), 16);
        try {
            String[] files = getAssets().list("licenses");
            if (files != null) for (String name : files) action("sources:" + name, name + "  ›", () -> go(new Screen("license", name)));
        } catch (Exception e) { toast("来源列表暂不可用"); }
    }
    private void license(String name) {
        if (name.contains("/") || name.contains("..")) throw new IllegalArgumentException("来源路径无效");
        shell("来源与授权", name);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("licenses/" + name), "UTF-8"))) {
            StringBuilder all = new StringBuilder(); String line; while ((line = reader.readLine()) != null) all.append(line).append('\n');
            TextView text = TvTheme.text(this, all.toString(), 18, TvTheme.INK); text.setFocusable(true); register(text, "license:text");
            text.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) scroll.smoothScrollBy(0, dp(key == KeyEvent.KEYCODE_DPAD_DOWN ? 180 : -180));
                return true;
            }); TvTheme.add(content, text, 16);
        } catch (Exception e) { throw new IllegalStateException("来源说明暂不可用", e); }
    }

    private void renderPin(String mode) {
        pinSetup = !repository.hasPin() || "change-new".equals(mode); pinInput = ""; pendingPin = null; confirmingPin = false; pinBusy = false;
        shell(pinSetup ? "设置家长 PIN" : "验证家长 PIN", mode.startsWith("action-") ? "修改本机数据前再次验证" : "");
        LinearLayout box = TvTheme.card(this, TvTheme.WHITE);
        pinDots = TvTheme.title(this, "○   ○   ○   ○", 31); pinDots.setGravity(Gravity.CENTER); box.addView(pinDots);
        pinMessage = TvTheme.text(this, "输入 4 位数字", 18, TvTheme.MUTED); pinMessage.setGravity(Gravity.CENTER); TvTheme.add(box, pinMessage, 6);
        pinGrid = new Button[4][3];
        for (int r = 0; r < 4; r++) {
            LinearLayout line = TvTheme.row(this);
            for (int c = 0; c < 3; c++) {
                final String label = r < 3 ? Integer.toString(r * 3 + c + 1) : new String[]{"删除", "0", "确认"}[c];
                Button key = TvTheme.button(this, label, r == 3 && c == 2, () -> {
                    if ("删除".equals(label)) pinDelete(); else if ("确认".equals(label)) pinConfirm(); else pinDigit(label);
                }); key.setTextSize(23); register(key, "pin:" + label); pinGrid[r][c] = key;
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(55), 1); size.setMargins(dp(5), dp(4), dp(5), 0); line.addView(key, size);
            }
            TvTheme.add(box, line, 0);
        }
        TvTheme.add(content, box, 16);
    }
    private void pinDigit(String digit) {
        if (pinInput.length() < 4) pinInput += digit; updateDots();
        if (pinInput.length() == 4 && pinGrid != null) pinGrid[3][2].requestFocus();
    }
    private void pinDelete() { if (!pinInput.isEmpty()) pinInput = pinInput.substring(0, pinInput.length() - 1); updateDots(); }
    private void updateDots() { StringBuilder dots = new StringBuilder(); for (int n = 0; n < 4; n++) dots.append(n < pinInput.length() ? "●   " : "○   "); pinDots.setText(dots); }
    private void pinConfirm() {
        if (pinBusy) return;
        if (pinInput.length() != 4) { pinMessage.setText("请输入完整的 4 位数字"); return; }
        try {
            String mode = navigation.current().argument;
            if (pinSetup) {
                if (!confirmingPin) { pendingPin = pinInput; pinInput = ""; confirmingPin = true; updateDots(); pinMessage.setText("再输入一次，确认 PIN"); pinGrid[0][0].requestFocus(); return; }
                if (!pinInput.equals(pendingPin)) { pinInput = ""; pendingPin = null; confirmingPin = false; updateDots(); pinMessage.setText("两次不同，请重新设置"); pinGrid[0][0].requestFocus(); return; }
                repository.setPin(pinInput); notifyService();
            } else if (!repository.verifyPin(pinInput)) { pinInput = ""; updateDots(); pinMessage.setText("PIN 不正确，请重试；连续错误将短暂锁定"); pinGrid[0][0].requestFocus(); return; }
            pinBusy = true; parentAuthenticated = true; pinInput = ""; pendingPin = null;
            if ("change-old".equals(mode)) { navigation.replace(new Screen("pin", "change-new")); renderCurrent(); }
            else if (mode.startsWith("action-")) {
                if ("action-reset".equals(mode)) repository.resetLearning(); else repository.restoreDefaults();
                notifyService(); returnToParent();
            } else if ("change-new".equals(mode)) returnToParent();
            else { navigation.replace(pendingGate != null ? pendingGate : new Screen("parent")); pendingGate = null; renderCurrent(); }
        } catch (RuntimeException error) { pinBusy = false; pinMessage.setText("操作未完成，请重试"); }
    }
    private void notifyService() { if (LearningService.instance != null) LearningService.instance.settingsChanged(); }
    private void toast(String message) { android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show(); }
    private void openPlayer() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(LearningService.TARGET);
        if (intent == null) { toast("请先安装云视听小电视"); return; }
        try { startActivity(intent); } catch (RuntimeException e) { toast("暂时无法打开播放器"); }
    }
    private void openSystemSettings() {
        Intent settings = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
        try { startActivity(settings != null ? settings : new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (RuntimeException error) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }
    private void fatal() {
        stopMedia(); pageName = "error"; scroll = null; targets.clear(); menu.clear(); menuByName.clear();
        LinearLayout root = TvTheme.column(this); root.setPadding(dp(48), dp(30), dp(48), dp(30)); root.setBackgroundColor(TvTheme.CREAM);
        root.addView(TvTheme.title(this, "需要一点帮助", 30)); TvTheme.add(root, TvTheme.text(this, "本机课程或学习数据暂时无法读取", 22, TvTheme.MUTED), 16);
        Button retry = TvTheme.button(this, "重试", true, () -> { try { repository = StudyRepository.get(this); renderCurrent(); } catch (RuntimeException e) { fatal(); } });
        TvTheme.add(root, retry, 24); register(retry, "error:retry"); defaultFocus = retry; pageRoot = root; setContentView(root); retry.requestFocus();
    }
}
