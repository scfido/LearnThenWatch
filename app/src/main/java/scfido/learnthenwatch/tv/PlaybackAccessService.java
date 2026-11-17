package scfido.learnthenwatch.tv;

import static scfido.learnthenwatch.tv.TvUiTheme.Role.*;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Playback detection and gate lifecycle. Learning and time state live in the repository. */
public final class PlaybackAccessService extends AccessibilityService {
    static volatile PlaybackAccessService instance;
    public static final String TARGET = "com.xiaodianshi.tv.yst";
    private static volatile PlaybackAccessStatus liveStatus = new PlaybackAccessStatus(PlaybackAccessStatus.Kind.CONNECTING, "等待系统连接");
    private final Handler handler = new Handler();
    private final PlaybackTimer clock = new PlaybackTimer(0);
    private final PlayerWindowTracker playback = new PlayerWindowTracker(TARGET, BuildConfig.APPLICATION_ID);
    private LearningRepository repository;
    private TvUiTheme theme;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private LearningGateActivity gateActivity;
    private long gateWindow;
    private View gate;
    private LessonScreen screen;
    private boolean focusHeld;
    private boolean focusRequested;
    private boolean shuttingDown = true;
    private long generation;
    private long lastDiagnostic;
    private String gateError;
    private long samples;
    private PlaybackTransitionOverlay transitionOverlay;
    private boolean enteringStudy;
    private PlaybackVolumeFade volumeFade;
    private long sampleDelayMs = 1000;

    public static boolean isConnected() { return instance != null; }
    public static PlaybackAccessStatus monitorStatus(Context context, LearningRepository repository) {
        try {
            AccessibilityManager manager = (AccessibilityManager) context.getSystemService(ACCESSIBILITY_SERVICE);
            boolean authorized = false;
            ComponentName expected = new ComponentName(context, PlaybackAccessService.class);
            if (manager != null) for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (expected.equals(ComponentName.unflattenFromString(service.getId()))) { authorized = true; break; }
            }
            return PlaybackAccessStatus.resolve(repository.hasPin(), repository.settings().enabled, authorized, isConnected(), liveStatus);
        } catch (RuntimeException error) {
            return new PlaybackAccessStatus(PlaybackAccessStatus.Kind.ERROR, "未生效 · 无法读取监控状态，请重试");
        }
    }

    private void status(PlaybackAccessStatus.Kind kind, String message) { liveStatus = new PlaybackAccessStatus(kind, message); }

    private final Runnable retryFocus = () -> { if (gate != null && !shuttingDown) acquireFocus(); };

    private void requestSample() {
        if (shuttingDown) return;
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (shuttingDown) return;
            try { sample(); }
            catch (RuntimeException error) {
                if (volumeFade != null) volumeFade.pause();
                clock.reset();
                status(PlaybackAccessStatus.Kind.ERROR, "未生效 · 数据读取失败，请进入家长配置检查");
                Log.e("LearnThenWatch", "Control state failed", error);
                try {
                    if (screenOn() && targetWindow() == PlayerWindowTracker.State.PLAYER) showError("学习数据暂时无法读取，请重试或请家长检查。");
                } catch (RuntimeException displayError) {
                    Log.e("LearnThenWatch", "Could not show control error", displayError);
                }
            } finally {
                handler.removeCallbacks(this);
                if (!shuttingDown) handler.postDelayed(this, sampleDelayMs);
            }
        }
    };

    @Override protected void onServiceConnected() {
        shutdown();
        shuttingDown = false;
        instance = this;
        repository = null;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        theme = new TvUiTheme(this, ThemeMode.AUTO);
        transitionOverlay = new PlaybackTransitionOverlay(this, theme);
        volumeFade = new PlaybackVolumeFade(this);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        focusHeld = false;
                        handler.removeCallbacks(retryFocus);
                        if (gate != null && !shuttingDown) handler.postDelayed(retryFocus, 350);
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN && gate != null) focusHeld = true;
                }, handler).build();
        clock.reset();
        samples = 0; lastDiagnostic = 0;
        status(PlaybackAccessStatus.Kind.CONNECTING, "检查管控状态…");
        requestSample();
        Log.i("LearnThenWatch", "Learning exchange monitor connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (shuttingDown) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            playback.activity(String.valueOf(event.getPackageName()), String.valueOf(event.getClassName()), event.getWindowId());
            if (playback.definitelyOther()) { dismissGate(); clock.reset(); }
        }
        requestSample();
    }

    private boolean screenOn() {
        return ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
    }

    private PlayerWindowTracker.State targetWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        // Accessibility overlays can own input focus while the actual application changes below them.
        // Inspect application windows before relying on the overlay's remembered underlying owner.
        if (root == null || getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            java.util.List<AccessibilityWindowInfo> visible = getWindows();
            try {
                for (AccessibilityWindowInfo window : visible) {
                    if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo app = window.getRoot(); if (app == null) continue;
                    try {
                        String owner = String.valueOf(app.getPackageName());
                        if ("android".equals(owner) || "com.android.systemui".equals(owner)
                                || (gate != null && getPackageName().equals(owner))) continue;
                        return playback.root(owner, app.getWindowId(), TARGET.equals(owner) && hasPlayer(app), false);
                    } finally { app.recycle(); }
                }
            } finally {
                for (AccessibilityWindowInfo window : visible) window.recycle();
                if (root != null) root.recycle();
            }
            // A missing application window is transient, not proof the child left the player.
            return gateActivity != null && gateActivity.hasWindowFocus()
                    ? playback.root(getPackageName(), -1, false, true) : PlayerWindowTracker.State.UNKNOWN;
        }
        try {
            String owner = String.valueOf(root.getPackageName());
            return playback.root(owner, root.getWindowId(), TARGET.equals(owner) && hasPlayer(root), gate != null);
        } finally { root.recycle(); }
    }

    private boolean hasPlayer(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        if (PlayerWindowTracker.isPlayerViewId(id)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (hasPlayer(child)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }

    private void sample() {
        sampleDelayMs = 1000;
        samples++;
        if (repository == null) { repository = LearningRepository.get(this); appearanceChanged(); }
        ParentSettings settings = repository.settings();
        if (!repository.hasPin()) {
            status(PlaybackAccessStatus.Kind.NEEDS_PIN, "未生效 · 请家长先设置 PIN");
            dismissGate(); clock.reset(); return;
        }
        if (!settings.enabled) {
            status(PlaybackAccessStatus.Kind.DISABLED, "未生效 · 家长已关闭管控");
            dismissGate(); clock.reset(); return;
        }
        if (!screenOn()) {
            dismissGate(); clock.reset(); playback.reset();
            status(PlaybackAccessStatus.Kind.WAITING, "正在生效 · 待机中，不计时"); return;
        }
        PlayerWindowTracker.State target = targetWindow();
        if (target == PlayerWindowTracker.State.OTHER) {
            dismissGate(); clock.reset();
            status(PlaybackAccessStatus.Kind.WAITING, "正在生效 · 等待云视听小电视播放");
            return;
        }
        if (target == PlayerWindowTracker.State.UNKNOWN) {
            if (volumeFade != null) volumeFade.pause();
            transitionOverlay.hideWarning();
            clock.reset();
            if (gate != null) gateStatus();
            else status(PlaybackAccessStatus.Kind.WAITING, "正在生效 · 等待播放窗口确认，不计时");
            return;
        }
        LearningState.Snapshot state = repository.snapshot();
        if (gate != null || enteringStudy) {
            clock.reset();
            gateStatus();
            return;
        }
        if (state.balanceMs <= 0 || state.dailyRemainingMs <= 0) {
            if (volumeFade != null) volumeFade.update(0, true);
            clock.reset(); showGate(); return;
        }
        long before = clock.elapsed();
        boolean playing = audio.isMusicActive();
        clock.tick(SystemClock.elapsedRealtime(), playing);
        long consumed = clock.elapsed() - before;
        if (consumed > 0) state = repository.consumeViewing(consumed);
        long remaining = PlaybackTransitionOverlay.remaining(state.balanceMs, state.dailyRemainingMs);
        if (volumeFade != null) volumeFade.update(remaining, playing);
        if (remaining > 0 && remaining <= PlaybackTransitionOverlay.WARNING_MS)
            sampleDelayMs = playing && remaining <= ViewingVolumeFade.FADE_MS ? 100 : 500;
        status(playing ? PlaybackAccessStatus.Kind.WATCHING : PlaybackAccessStatus.Kind.WAITING,
                playing ? "正在计时 · 本轮剩余 " + ((state.balanceMs + 59999) / 60000) + " 分钟" : "正在生效 · 播放已暂停，不计时");
        if (state.balanceMs <= 0 || state.dailyRemainingMs <= 0) {
            clock.reset(); showGate();
        } else {
            // Warning failures must never prevent charging or the time-expired gate.
            try { transitionOverlay.update(remaining, playing); }
            catch (RuntimeException error) { Log.w("LearnThenWatch", "Could not show viewing warning", error); }
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastDiagnostic >= 15000) {
            lastDiagnostic = now;
            Log.i("LearnThenWatch", "Viewing balanceMs=" + state.balanceMs
                    + " usedMs=" + state.todayUsedMs + " audio=" + audio.isMusicActive());
        }
    }

    void settingsChanged() {
        clock.reset(); dismissGate();
        appearanceChanged();
        // Never overwrite repository state with a cached service snapshot.
        requestSample();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        appearanceChanged();
    }

    void appearanceChanged() {
        if (theme == null) return;
        ThemeMode mode = repository == null ? ThemeMode.AUTO : repository.settings().themeMode;
        theme.refresh(mode, gate);
        if (screen != null) screen.refreshTheme();
        if (transitionOverlay != null) transitionOverlay.refreshTheme(mode);
        if (gateActivity != null) theme.applyWindow(gateActivity, true);
    }

    private void gateStatus() {
        if (gateError != null) status(PlaybackAccessStatus.Kind.ERROR, "未生效 · " + gateError);
        else status(PlaybackAccessStatus.Kind.LEARNING, focusHeld ? "正在生效 · 学习中，已请求暂停播放"
                : "正在生效 · 学习页面已显示，播放器未确认暂停");
    }

    void preview() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void showGate() {
        if (gate != null || enteringStudy || shuttingDown) return;
        try {
            if (repository.snapshot().dailyRemainingMs <= 0) { showDailyLimit(); return; }
            showStudyTransition();
        } catch (RuntimeException error) {
            Log.e("LearnThenWatch", "Could not create learning gate", error);
            showError("学习内容暂时无法打开，请重试或请家长检查。");
        }
    }

    private void showStudyTransition() {
        enteringStudy = true;
        buildStudyGate();
    }

    private void buildStudyGate() {
        final long token = ++generation;
        screen = new LessonScreen(this, repository, () -> {
            if (token != generation) return;
            try {
                LearningState.Snapshot state = repository.snapshot();
                if (state.balanceMs > 0 && state.dailyRemainingMs > 0) dismissGate();
                else leaveToHome();
            } catch (RuntimeException e) { showError("观看额度读取失败，请重试。"); }
        }, this::leaveToHome, this::leaveToLearningHome, false, null, enteringStudy);
        attach(screen.view());
    }

    private LinearLayout simplePage(String title, String message) {
        appearanceChanged();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER);
        page.setPadding(dp(48), dp(24), dp(48), dp(24));
        theme.backgroundColor(page, BACKGROUND);
        TextView heading = theme.text(title, 34, ACCENT);
        heading.setGravity(Gravity.CENTER); page.addView(heading);
        TextView body = theme.text(message, 22, TEXT); body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(20), 0, dp(20));
        page.addView(body); return page;
    }

    private void addButton(LinearLayout page, String label, Runnable action) {
        Button button = theme.button(label, false, action); button.setTextSize(23);
        button.setPadding(dp(20), dp(8), dp(20), dp(8));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(460), dp(62));
        size.topMargin = dp(12); page.addView(button, size);
    }

    private void showDailyLimit() {
        LinearLayout page = simplePage("今天的观看时间用完啦", "今天已经完成约定的观看计划。\n可以继续英语探险，明天学习后再换观看时间。");
        addButton(page, "继续学习 · 不兑换时间", () -> { dismissGate(); buildStudyGate(); });
        attach(page);
    }

    private void showError(String message) {
        if (gate != null && message.equals(gateError)) return;
        dismissGate(); gateError = message;
        LinearLayout page = simplePage("需要一点帮助", message);
        addButton(page, "重试", () -> {
            dismissGate(); repository = null;
            // The existing periodic poll will retry; do not create a second polling loop.
        });
        addButton(page, getString(R.string.open_app), () -> {
            dismissGate();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        attach(page);
    }

    private void acquireFocus() {
        if (audio == null || focus == null) return;
        focusRequested = true;
        try { focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED; }
        catch (RuntimeException error) { focusHeld = false; Log.w("LearnThenWatch", "Could not request player audio focus", error); }
        gateStatus();
        if (!focusHeld) {
            Log.w("LearnThenWatch", "Player did not grant transient audio focus");
        }
    }

    private void attach(View content) {
        if (transitionOverlay != null) transitionOverlay.hideWarning();
        FrameLayout wrapper = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (SystemVolumeKeys.dispatch(PlaybackAccessService.this, event)) return true;
                int key = event.getKeyCode();
                if (key == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) leaveToHome();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        || key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_MEDIA_NEXT)
                    return true;
                return super.dispatchKeyEvent(event);
            }
        };
        if (gate instanceof FrameLayout) {
            FrameLayout current = (FrameLayout) gate;
            current.removeAllViews();
            current.addView(content, new FrameLayout.LayoutParams(-1, -1));
            current.requestFocus(View.FOCUS_DOWN);
            return;
        }
        wrapper.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        theme.backgroundColor(wrapper, BACKGROUND);
        if (enteringStudy) wrapper.setAlpha(0);
        wrapper.addView(content, new FrameLayout.LayoutParams(-1, -1));
        gate = wrapper;
        try {
            clock.reset(); if (!focusRequested) acquireFocus();
            long window = ++gateWindow;
            startActivity(new Intent(this, LearningGateActivity.class)
                    .putExtra("gateWindow", window)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            handler.postDelayed(() -> {
                if (window != gateWindow || gate == null || gateActivity != null || shuttingDown) return;
                dismissGate();
                status(PlaybackAccessStatus.Kind.ERROR, "未生效 · 无法显示学习页面，请家长检查");
            }, 5000);
            Log.i("LearnThenWatch", "Learning gate application window requested");
        } catch (RuntimeException error) {
            dismissGate();
            status(PlaybackAccessStatus.Kind.ERROR, "未生效 · 无法显示学习页面，请家长检查");
            Log.e("LearnThenWatch", "Could not open learning gate", error);
        }
    }

    boolean attachGateActivity(LearningGateActivity activity, long window) {
        if (shuttingDown || gate == null || window != gateWindow) return false;
        gateActivity = activity; activity.showGate(gate);
        appearanceChanged();
        if (screen != null) screen.resume();
        if (screen != null && (enteringStudy || screen.isPreparing())) {
            enteringStudy = true;
            final long token = generation;
            final LessonScreen lesson = screen;
            // Fade the translucent ordinary window over the live player. Keeping the
            // transition below system UI also preserves native volume throughout it.
            gate.postOnAnimation(() -> {
                if (token != generation || screen != lesson || gateActivity != activity) return;
                gate.animate().alpha(1).setDuration(600).withEndAction(() -> {
                    if (token != generation || screen != lesson) return;
                    enteringStudy = false;
                    if (volumeFade != null) volumeFade.restore();
                    lesson.beginPreparation();
                }).start();
            });
        } else if (volumeFade != null) volumeFade.restore();
        return true;
    }

    void gateActivityStopped(LearningGateActivity activity) {
        if (activity == gateActivity) { dismissGate(); clock.reset(); }
    }

    void gateActivityDestroyed(LearningGateActivity activity, boolean changingConfiguration) {
        if (activity != gateActivity) return;
        if (changingConfiguration) { activity.releaseGate(gate); gateActivity = null; }
        else { dismissGate(); clock.reset(); }
    }

    private void leaveToHome() {
        dismissGate(); clock.reset(); playback.reset();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void leaveToLearningHome() {
        // Cover the player with the destination before releasing its transient audio focus.
        startActivity(new Intent(this, MainActivity.class).putExtra("learningHome", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        dismissGate(); clock.reset(); playback.reset();
    }

    private void dismissGate() {
        if (volumeFade != null) volumeFade.restore();
        handler.removeCallbacks(retryFocus);
        enteringStudy = false;
        if (transitionOverlay != null) transitionOverlay.close();
        if (gate != null) gate.animate().cancel();
        generation++; gateError = null;
        if (screen != null) { screen.view().animate().cancel(); screen.close(); screen = null; }
        LearningGateActivity activity = gateActivity;
        gateActivity = null;
        if (activity != null) {
            activity.releaseGate(gate);
            // Removing the entire task can make the TV firmware kill the service process,
            // including a newly requested gate during a rapid settings/rebind sequence.
            activity.finish();
        }
        gate = null;
        if (focusRequested && audio != null && focus != null) try { audio.abandonAudioFocusRequest(focus); }
        catch (RuntimeException error) { Log.w("LearnThenWatch", "Could not release player audio focus", error); }
        focusHeld = false; focusRequested = false;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void shutdown() {
        shuttingDown = true; handler.removeCallbacksAndMessages(null);
        dismissGate(); clock.reset(); playback.reset();
        if (volumeFade != null) { volumeFade.close(); volumeFade = null; }
        if (instance == this) instance = null;
        status(PlaybackAccessStatus.Kind.CONNECTING, "等待系统连接 · 无障碍服务未连接");
    }

    @Override public void onInterrupt() { dismissGate(); clock.reset(); playback.reset(); requestSample(); }
    @Override public boolean onUnbind(Intent intent) { shutdown(); return super.onUnbind(intent); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }

    @Override protected void dump(FileDescriptor descriptor, PrintWriter writer, String[] args) {
        writer.println("LearnThenWatch state=" + liveStatus.kind + " status=" + liveStatus.message + " gate=" + (gate != null)
                + " samples=" + samples + " connected=" + (instance == this) + " shuttingDown=" + shuttingDown);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) try {
            java.util.Set<String> ids = new java.util.LinkedHashSet<>(); collectIds(root, ids);
            writer.println("rootPackage=" + root.getPackageName() + " rootClass=" + root.getClassName()
                    + " windowId=" + root.getWindowId() + " viewIds=" + ids);
        } finally { root.recycle(); }
        if (repository != null) try {
            LearningState.Snapshot state = repository.snapshot();
            writer.println("day=" + state.day + " balanceMs=" + state.balanceMs
                    + " usedMs=" + state.todayUsedMs + " dailyRemainingMs=" + state.dailyRemainingMs);
        } catch (RuntimeException error) { writer.println("Data unavailable: " + error.getClass().getSimpleName()); }
    }

    private void collectIds(AccessibilityNodeInfo node, java.util.Set<String> ids) {
        if (ids.size() >= 64) return;
        if (node.getViewIdResourceName() != null) ids.add(node.getViewIdResourceName());
        for (int n = 0; n < node.getChildCount() && ids.size() < 64; n++) {
            AccessibilityNodeInfo child = node.getChild(n); if (child == null) continue;
            try { collectIds(child, ids); } finally { child.recycle(); }
        }
    }
}
