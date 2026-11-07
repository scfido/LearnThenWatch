package local.mytv.english;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
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
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Playback detection and gate lifecycle. Learning and time state live in the repository. */
public final class LearningService extends AccessibilityService {
    static LearningService instance;
    public static final String TARGET = "com.xiaodianshi.tv.yst";
    private static volatile String diagnostic = "未生效 · 无障碍服务未连接";
    private final Handler handler = new Handler();
    private final PlaybackClock clock = new PlaybackClock(0);
    private StudyRepository repository;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private WindowManager windows;
    private View gate;
    private StudyScreen screen;
    private boolean focusHeld;
    private boolean playerWindow;
    private boolean shuttingDown;
    private String foregroundPackage = "";
    private long generation;
    private long lastDiagnostic;
    private String gateError;

    public static boolean isConnected() { return instance != null; }
    public static String status() { return diagnostic; }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (shuttingDown) return;
            try { sample(); }
            catch (RuntimeException error) {
                clock.reset();
                diagnostic = "未生效 · 数据读取失败，请进入家长配置检查";
                Log.e("EnglishQuiz", "Control state failed", error);
                try {
                    if (screenOn() && targetPlayer()) showError("学习数据暂时无法读取，请重试或请家长检查。");
                } catch (RuntimeException displayError) {
                    Log.e("EnglishQuiz", "Could not show control error", displayError);
                }
            } finally {
                if (!shuttingDown) handler.postDelayed(this, 1000);
            }
        }
    };

    @Override protected void onServiceConnected() {
        handler.removeCallbacksAndMessages(null);
        dismissGate();
        shuttingDown = false;
        instance = this;
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        focusHeld = false;
                        final long token = generation;
                        handler.postDelayed(() -> {
                            if (token == generation && gate != null && !shuttingDown) acquireFocus();
                        }, 350);
                    }
                }, handler).build();
        clock.reset();
        diagnostic = "检查管控状态…";
        handler.post(poll);
        Log.i("EnglishQuiz", "Learning exchange monitor connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String owner = String.valueOf(event.getPackageName());
        String name = String.valueOf(event.getClassName());
        if (TARGET.equals(owner)) {
            foregroundPackage = owner;
            if (name.endsWith("Activity")) {
                playerWindow = name.contains("CtsActivity") || name.contains("VideoPlayActivity")
                        || name.contains("PlayerActivity");
            }
        } else if (!getPackageName().equals(owner) && !owner.equals("com.android.systemui")
                && !owner.equals("android") && !owner.equals("null")) {
            foregroundPackage = owner;
            playerWindow = false;
            dismissGate();
            clock.reset();
        } else if (getPackageName().equals(owner) && name.endsWith("SettingsActivity")) {
            foregroundPackage = owner;
            dismissGate();
            clock.reset();
        }
    }

    private boolean screenOn() {
        return ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
    }

    private boolean targetPlayer() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            String owner = String.valueOf(root.getPackageName());
            // Overlay focus belongs to us. External window events invalidate its underlying owner.
            if (gate != null && getPackageName().equals(owner))
                return TARGET.equals(foregroundPackage) && playerWindow;
            if (!TARGET.equals(owner)) return false;
            foregroundPackage = TARGET;
            boolean detected = playerWindow || hasPlayer(root);
            if (detected) playerWindow = true;
            return detected;
        } finally { root.recycle(); }
    }

    private boolean hasPlayer(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        if (id != null && (id.endsWith(":id/cts_video_play") || id.endsWith(":id/video_play_view")
                || id.endsWith(":id/video_player"))) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (hasPlayer(child)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }

    private void sample() {
        if (repository == null) repository = StudyRepository.get(this);
        AppSettings settings = repository.settings();
        if (!repository.hasPin()) {
            diagnostic = "未生效 · 请家长先设置 PIN";
            dismissGate(); clock.reset(); return;
        }
        if (!settings.enabled) {
            diagnostic = "未生效 · 家长已关闭管控";
            dismissGate(); clock.reset(); return;
        }
        boolean target = screenOn() && targetPlayer();
        if (!target) {
            dismissGate(); clock.reset();
            diagnostic = "正在生效 · 等待云视听小电视播放";
            return;
        }
        StudyModels.Snapshot state = repository.snapshot();
        if (gate != null) {
            clock.reset();
            diagnostic = focusHeld ? "正在生效 · 学习中，已请求暂停播放"
                    : "正在生效 · 学习页面已显示，播放器未确认暂停";
            return;
        }
        if (state.balanceMs <= 0 || state.dailyRemainingMs <= 0) {
            clock.reset(); showGate(); return;
        }
        long before = clock.elapsed();
        clock.tick(SystemClock.elapsedRealtime(), audio.isMusicActive());
        long consumed = clock.elapsed() - before;
        if (consumed > 0) state = repository.consumeViewing(consumed);
        diagnostic = "正在生效 · 本轮剩余 " + ((state.balanceMs + 59999) / 60000) + " 分钟";
        if (state.balanceMs <= 0 || state.dailyRemainingMs <= 0) {
            clock.reset(); showGate();
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastDiagnostic >= 15000) {
            lastDiagnostic = now;
            Log.i("EnglishQuiz", "Viewing balanceMs=" + state.balanceMs
                    + " usedMs=" + state.todayUsedMs + " audio=" + audio.isMusicActive());
        }
    }

    void settingsChanged() {
        clock.reset(); dismissGate();
        // Never overwrite repository state with a cached service snapshot.
        if (!shuttingDown) handler.post(() -> { try { sample(); } catch (RuntimeException e) {
            Log.e("EnglishQuiz", "Settings refresh failed", e);
            diagnostic = "未生效 · 配置读取失败";
        } });
    }

    void preview() {
        startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void showGate() {
        if (gate != null || shuttingDown) return;
        try {
            if (repository.snapshot().dailyRemainingMs <= 0) { showDailyLimit(); return; }
            buildStudyGate();
        } catch (RuntimeException error) {
            Log.e("EnglishQuiz", "Could not create learning gate", error);
            showError("学习内容暂时无法打开，请重试或请家长检查。");
        }
    }

    private void buildStudyGate() {
        final long token = ++generation;
        screen = new StudyScreen(this, repository, () -> {
            if (token != generation) return;
            try {
                StudyModels.Snapshot state = repository.snapshot();
                if (state.balanceMs > 0 && state.dailyRemainingMs > 0) dismissGate();
                else leaveToHome();
            } catch (RuntimeException e) { showError("观看额度读取失败，请重试。"); }
        }, this::leaveToHome, false);
        attach(screen.view());
    }

    private LinearLayout simplePage(String title, String message) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER);
        page.setPadding(dp(48), dp(24), dp(48), dp(24));
        page.setBackgroundColor(Color.rgb(255, 248, 233));
        TextView heading = new TextView(this);
        heading.setText(title); heading.setTextSize(34); heading.setTextColor(Color.rgb(22, 125, 141));
        heading.setGravity(Gravity.CENTER); page.addView(heading);
        TextView body = new TextView(this);
        body.setText(message); body.setTextSize(22); body.setGravity(Gravity.CENTER);
        body.setTextColor(Color.rgb(53, 72, 78)); body.setPadding(0, dp(20), 0, dp(20));
        page.addView(body); return page;
    }

    private void addButton(LinearLayout page, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setTextSize(23);
        button.setTextColor(Color.rgb(22, 78, 89)); button.setPadding(dp(20), dp(8), dp(20), dp(8));
        button.setBackground(buttonBackground(false));
        button.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(buttonBackground(focused));
            v.animate().translationY(focused ? -dp(1) : 0).setDuration(130).start();
        });
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(460), dp(62));
        size.topMargin = dp(12); page.addView(button, size);
    }

    private GradientDrawable buttonBackground(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14)); shape.setColor(focused ? Color.rgb(222, 240, 232) : Color.WHITE);
        shape.setStroke(dp(focused ? 3 : 1), Color.rgb(22, 125, 141)); return shape;
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
        addButton(page, "打开学习 APP", () -> {
            dismissGate();
            startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        attach(page);
    }

    private void acquireFocus() {
        if (audio == null || focus == null) return;
        focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focusHeld) {
            diagnostic = "正在生效 · 学习页面已显示，播放器未确认暂停";
            Log.w("EnglishQuiz", "Player did not grant transient audio focus");
        }
    }

    private void attach(View content) {
        LinearLayout wrapper = new LinearLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
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
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(content, new LinearLayout.LayoutParams(-1, -1));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.setTitle("EnglishLearningGate");
        try {
            windows.addView(wrapper, params); gate = wrapper;
            clock.reset(); acquireFocus(); wrapper.requestFocus(View.FOCUS_DOWN);
            Log.i("EnglishQuiz", "Learning gate shown");
        } catch (RuntimeException error) {
            if (screen != null) { screen.close(); screen = null; }
            diagnostic = "未生效 · 无法显示学习页面，请家长检查";
            Log.e("EnglishQuiz", "Could not attach learning gate", error);
        }
    }

    private void leaveToHome() {
        dismissGate(); clock.reset(); foregroundPackage = ""; playerWindow = false;
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void dismissGate() {
        generation++; gateError = null;
        if (screen != null) { screen.close(); screen = null; }
        if (gate != null && windows != null) {
            try { windows.removeViewImmediate(gate); }
            catch (RuntimeException error) { Log.w("EnglishQuiz", "Gate already detached", error); }
        }
        gate = null;
        if (focusHeld && audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focusHeld = false;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void shutdown() {
        shuttingDown = true; handler.removeCallbacksAndMessages(null);
        dismissGate(); clock.reset();
        if (instance == this) instance = null;
        diagnostic = "未生效 · 无障碍服务未连接";
    }

    @Override public void onInterrupt() { dismissGate(); clock.reset(); }
    @Override public boolean onUnbind(Intent intent) { shutdown(); return super.onUnbind(intent); }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }

    @Override protected void dump(FileDescriptor descriptor, PrintWriter writer, String[] args) {
        writer.println("EnglishQuiz status=" + diagnostic + " gate=" + (gate != null));
        if (repository != null) try {
            StudyModels.Snapshot state = repository.snapshot();
            writer.println("day=" + state.day + " balanceMs=" + state.balanceMs
                    + " usedMs=" + state.todayUsedMs + " dailyRemainingMs=" + state.dailyRemainingMs);
        } catch (RuntimeException error) { writer.println("Data unavailable: " + error.getClass().getSimpleName()); }
    }
}
