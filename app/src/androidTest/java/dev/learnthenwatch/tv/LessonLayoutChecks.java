package dev.learnthenwatch.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static dev.learnthenwatch.tv.LearningState.*;

/** Real Android layout and input regression checks; all study writes use an isolated database. */
final class LessonLayoutChecks {
    private final Instrumentation instrument;
    private final Context context;
    private Activity activity;
    private LearningRepository repository;
    private LessonScreen screen;
    private int assertions;
    private final String database = "instrumentation-ui.db", preferences = "instrumentation-ui-settings";
    private int finished;

    private LessonLayoutChecks(Instrumentation instrument, Context context) { this.instrument = instrument; this.context = context; }
    static int run(Instrumentation instrument, Context context) throws Exception {
        LessonLayoutChecks checks = new LessonLayoutChecks(instrument, context);
        try { checks.run(); return checks.assertions; }
        finally { checks.close(); }
    }
    private void run() throws Exception {
        activity = instrument.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle(250);
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
        VocabularyCatalog real;
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open("curriculum.tsv"), StandardCharsets.UTF_8)) { real = VocabularyCatalog.load(reader); }
        List<VocabularyCatalog.Item> items = new ArrayList<>();
        String[] words = {"apple", "bridge", "clever", "festival", "library", "journey"};
        String[] meanings = {"苹果", "桥", "聪明的", "节日", "图书馆", "旅程"};
        for (int n = 0; n < words.length; n++) items.add(new VocabularyCatalog.Item("ui_" + n, "KET", "探险", words[n], meanings[n],
                n == 0 ? "I ate an apple on the bridge during our long and wonderful journey to the quiet library." : "We remember the word " + words[n] + ".",
                "我们把新的发现记在旅行日记里，并和朋友一起分享今天的快乐。", "audio/intentionally-missing-for-ui-test.mp3"));
        // Add actual longest content too, to exercise rendering rather than a hand-selected short example.
        VocabularyCatalog.Item longest = real.items.get(0);
        for (VocabularyCatalog.Item i : real.items) if (i.example.length() > longest.example.length()) longest = i;
        items.add(new VocabularyCatalog.Item("ui_long", "KET", "长文本", longest.word,
                "为实现目标而不断努力并与伙伴一起克服困难的能力和勇气", longest.example,
                "同一个词在不同的句子与文化环境中可能表达不同的意思。", longest.audio));
        repository = new LearningRepository(context, new VocabularyCatalog(items), new LearningRepository.Clock() {
            public String day() { return "2030-02-01"; }
            public long now() { return 1896134400000L; }
        }, database, preferences);
        repository.setPin("2468");
        ParentSettings settings = repository.settings(); settings.taskCount = 3; settings.autoSpeak = false; repository.saveSettings(settings);
        attach(create(false, null), 960, 540);
        check(repository.currentTask(repository.currentSession().id).needsIntro == false, "no separate new-word confirmation page");
        validate(); save("question");
        check(((Button) find("study:replay")).getText().toString().equals("← 重听"), "replay shortcut has a left arrow");
        check(((Button) find("study:unknown")).getText().toString().equals("不认识 →"), "unknown shortcut has a right arrow");
        check(find("study:audio-status").getVisibility() != View.VISIBLE, "bottom-left instructional text is hidden");
        check(find("study:replay").getLeft() > screen.view().getWidth() / 2, "shortcut buttons sit at bottom right");

        click("study:unknown"); settle(230);
        check(repository.snapshot().totalAnswered == 1 && repository.snapshot().firstCorrect == 0, "unknown is recorded honestly");
        check(find("study:advance") != null, "incorrect answer has one acknowledgement"); validate(); save("explanation");
        click("study:advance"); settle(250);
        // Deliberately missing audio must offer a same-item text fallback, never silently complete it.
        await(() -> find("study:unknown") instanceof Button
                && "换文字题 →".contentEquals(((Button) find("study:unknown")).getText()),
                "audio failure has a visible fallback");
        click("study:unknown"); settle(230);
        Session session = repository.currentSession(); Task second = repository.currentTask(session.id);
        check(second.type.equals("WORD") && repository.snapshot().totalAnswered == 1, "fallback preserves uncompleted task");
        click("study:option:" + second.correct);
        check(find("study:advance") == null, "correct answers do not require a continue button");
        settle(300);
        check(find("study:option:" + second.correct) != null, "answer feedback briefly remains before automatic next");
        settle(850);
        Task third = repository.currentTask(session.id);
        check(!third.id.equals(second.id), "correct answer automatically advances"); validate(); save("sentence");
        click("study:option:" + third.correct); right(); settle(280);
        Task correction = repository.currentTask(session.id);
        check(correction.correction, "main tasks flow directly into corrections");
        click("study:unknown"); click("study:advance"); settle(220);
        click("study:unknown");
        check(repository.currentTask(session.id).correctionAttempts == 2, "guided correction requires actual attempts");
        click("study:advance"); settle(250);
        check(repository.snapshot().balanceMs == 600000, "single acknowledgement completes guidance and awards once");
        check(repository.snapshot().totalAnswered == 3 && repository.snapshot().firstCorrect == 2, "UI preserves first-attempt stats");
        check(find("study:finish") != null, "completion has one explicit viewing action"); validate(); save("complete");
        check("已完成 3 / 3".contentEquals(find("study:progress").getContentDescription()), "completion progress follows configured task count");
        click("study:finish"); click("study:finish"); check(finished == 1, "repeated finish click invokes viewing once");

        // Pausing on answer feedback resumes from the persisted next task.
        attach(create(false, null), 960, 540);
        session = repository.currentSession(); Task current = repository.currentTask(session.id);
        click("study:option:" + current.correct);
        main(() -> screen.pauseAudio()); settle(800);
        main(() -> screen.resume()); settle(230);
        check(find("study:option:0") != null, "resume renders current task after interrupted transition");
        int count = repository.snapshot().totalAnswered;
        main(() -> {
            boolean consumed = screen.view().dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2));
            check(consumed, "held confirmation repeat is consumed");
        });
        check(repository.snapshot().totalAnswered == count, "held remote key does not answer the next task");

        // Use both normal and compact TV viewports, including a very long phrase/meaning/translation.
        VocabularyCatalog.Item[] extremes = { items.get(items.size() - 1),
            new VocabularyCatalog.Item("ui_phrase", "KET", "出行", "tourist information centre", "为实现目标而不断努力并与伙伴一起克服困难的能力和勇气",
                    "The tourist information centre has maps and helpful advice for every young visitor.",
                    "旅游信息中心提供地图，并为每一位年轻的游客提供有用的建议。", longest.audio) };
        for (int height : new int[]{540, 480}) for (VocabularyCatalog.Item item : extremes) {
            attach(create(false, item), height == 540 ? 960 : 853, height);
            validate(); if (height == 480) save("compact-wordcard");
        }
        // Demos must follow the same single-screen layout without changing the wallet or progress.
        long balance = repository.snapshot().balanceMs; int learned = repository.snapshot().learnedCount;
        attach(create(true, null), 960, 540); validate();
        click("study:unknown"); click("study:advance"); settle(220);
        check(repository.snapshot().balanceMs == balance && repository.snapshot().learnedCount == learned, "demo remains read-only");
    }
    private LessonScreen create(boolean demo, VocabularyCatalog.Item review) {
        final LessonScreen[] result = new LessonScreen[1];
        main(() -> result[0] = new LessonScreen(activity, repository, () -> finished++, () -> { }, demo, review));
        return result[0];
    }
    private void attach(LessonScreen value, int widthDp, int heightDp) {
        main(() -> {
            if (screen != null) screen.close(); screen = value;
            FrameLayout host = new FrameLayout(activity);
            FrameLayout.LayoutParams dimensions = new FrameLayout.LayoutParams(TvUiTheme.dp(activity, widthDp), TvUiTheme.dp(activity, heightDp));
            dimensions.gravity = android.view.Gravity.CENTER;
            host.addView(screen.view(), dimensions); activity.setContentView(host);
        }); settle(260);
    }
    private View find(String tag) { return screen.view().findViewWithTag(tag); }
    private void click(String tag) {
        main(() -> {
            View view = find(tag); check(view != null && view.isEnabled(), "reachable action " + tag);
            view.performClick();
        });
    }
    private void settle(int ms) { SystemClock.sleep(ms); main(() -> { }); }
    private void await(java.util.function.BooleanSupplier condition, String message) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = new boolean[1];
        do {
            main(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) { check(true, message); return; }
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(message + " (timed out waiting for UI state)");
    }
    private void right() {
        main(() -> {
            screen.view().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
            screen.view().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
        });
    }
    private void validate() {
        main(() -> {
            check(screen.view().getWidth() > 0 && screen.view().getHeight() > 0, "screen is measured on TV");
            checkView(screen.view());
        });
    }
    private void checkView(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        check(!(view instanceof ScrollView), "learning screen never scrolls");
        if (view instanceof TextView && ((TextView) view).getText().length() > 0) {
            TextView text = (TextView) view; Layout lines = text.getLayout();
            check(lines != null && lines.getLineCount() > 0, "visible text is laid out: " + text.getText());
            int last = lines.getLineCount() - 1;
            check(lines.getLineEnd(last) == text.length() && lines.getEllipsisCount(last) == 0, "complete text is visible: " + text.getText());
            int available = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
            check(lines.getLineBottom(last) <= available + 2, "text fits fixed height: " + text.getText());
            int textWidth = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
            for (int line = 0; line < lines.getLineCount(); line++)
                check(lines.getLineMax(line) <= textWidth + 2, "visible text fits fixed width: " + text.getText());
        }
        if (view instanceof Button) {
            Rect bounds = new Rect(); view.getDrawingRect(bounds); ((ViewGroup) screen.view()).offsetDescendantRectToMyCoords(view, bounds);
            check(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= screen.view().getWidth() && bounds.bottom <= screen.view().getHeight(), "button and focus ring stay on screen");
            check(view.getScaleX() == 1 && view.getScaleY() == 1 && view.getBackground() instanceof InsetDrawable, "focus border uses safe inward drawing");
            check(view.getPaddingLeft() >= TvUiTheme.dp(context, 18), "focus styling preserves text padding");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkView(group.getChildAt(i));
        }
    }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private void save(String name) {
        main(() -> {
            View root = screen.view(); Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(image));
            File file = new File(context.getExternalFilesDir(null), "qa-" + name + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            catch (Exception e) { throw new IllegalStateException(e); }
            finally { image.recycle(); }
        });
    }
    private void close() {
        if (screen != null) main(() -> screen.close());
        if (repository != null) repository.closeForTests();
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
    }
    private void main(Runnable action) {
        final Throwable[] failure = new Throwable[1];
        instrument.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError("TV UI assertion failed", failure[0]);
    }
}
