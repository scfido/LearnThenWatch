package local.mytv.english;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
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
import java.util.Random;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class LearningService extends AccessibilityService {
    static LearningService instance;
    private static final String TARGET = "com.xiaodianshi.tv.yst";
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private SharedPreferences preferences;
    private PlaybackClock clock;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private WindowManager windows;
    private View quiz;
    private boolean preview;
    private boolean focusHeld;
    private boolean playerWindow;
    private boolean wasPlaying;
    private int lastQuestion = -1;
    private long lastSave;
    private long lastDiagnostic;
    private String windowDiagnostic = "unknown";
    private boolean wasScreenOn;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try { sample(); } catch (RuntimeException e) { Log.e("EnglishQuiz", "Monitor error", e); }
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onServiceConnected() {
        handler.removeCallbacksAndMessages(null);
        if (quiz != null) dismiss(false);
        instance = this;
        preferences = getSharedPreferences("learning", MODE_PRIVATE);
        clock = new PlaybackClock(preferences.getLong("elapsed", 0));
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (quiz != null && change == AudioManager.AUDIOFOCUS_LOSS) {
                        handler.postDelayed(() -> { if (quiz != null) acquireFocus(); }, 300);
                    }
                }, handler).build();
        handler.post(poll);
        Log.i("EnglishQuiz", "Monitor connected; interval=120 seconds");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        String owner = String.valueOf(event.getPackageName());
        if (quiz != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && (owner.equals("com.mitv.tvhome") || owner.equals("com.xiaomi.mitv.settings")
                    || owner.equals("com.android.tv.settings"))) dismiss(false);
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && TARGET.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
            String name = String.valueOf(event.getClassName());
            if (name.endsWith("Activity")) {
                playerWindow = name.contains("CtsActivity") || name.contains("VideoPlayActivity")
                        || name.contains("PlayerActivity");
            }
        }
    }

    private boolean targetPlayer() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { windowDiagnostic = "no active window"; return false; }
        try {
            windowDiagnostic = "package=" + root.getPackageName() + " playerWindow=" + playerWindow;
            if (!TARGET.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return false;
            return playerWindow || hasPlayer(root);
        } finally { root.recycle(); }
    }

    private boolean hasPlayer(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        if (id != null && (id.endsWith(":id/cts_video_play") || id.endsWith(":id/video_play_view")
                || id.endsWith(":id/video_player"))) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean found;
            try { found = hasPlayer(child); } finally { child.recycle(); }
            if (found) return true;
        }
        return false;
    }

    private void sample() {
        boolean screenOn = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
        if (quiz != null) {
            // Home/settings remain available. Hide the quiz outside the viewing app;
            // retain the pending question and require it when viewing returns.
            if (!screenOn) dismiss(false);
            return;
        }
        if (!preferences.getBoolean("enabled", true)) { clock.tick(SystemClock.elapsedRealtime(), false); return; }
        boolean target = screenOn && targetPlayer();
        if (preferences.getBoolean("due", false) && target) { showQuiz(false); return; }
        boolean playing = target && audio.isMusicActive();
        long now = SystemClock.elapsedRealtime();
        if (now - lastDiagnostic >= 15000) {
            Log.i("EnglishQuiz", "Detection: " + windowDiagnostic + " target=" + target
                    + " music=" + audio.isMusicActive() + " elapsedMs=" + clock.elapsed());
            lastDiagnostic = now;
        }
        clock.tick(now, playing);
        if (playing != wasPlaying || screenOn != wasScreenOn) {
            Log.i("EnglishQuiz", "playing=" + playing + " elapsedMs=" + clock.elapsed());
            wasPlaying = playing; wasScreenOn = screenOn;
        }
        if (now - lastSave >= 5000) {
            preferences.edit().putLong("elapsed", clock.elapsed()).apply(); lastSave = now;
        }
        if (clock.elapsed() >= 120000) {
            preferences.edit().putBoolean("due", true).putLong("elapsed", 120000).commit();
            showQuiz(false);
        }
    }

    void settingsChanged() {
        if (!preferences.getBoolean("enabled", true)) {
            dismiss(false); clock.reset();
            preferences.edit().putBoolean("due", false).putLong("elapsed", 0).apply();
        }
    }
    void preview() { if (quiz == null) showQuiz(true); }

    private void acquireFocus() {
        focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        Log.i("EnglishQuiz", "Pause focus granted=" + focusHeld);
    }

    private void showQuiz(boolean isPreview) {
        if (quiz != null) return;
        preview = isPreview;
        Questions question = new Questions(random, lastQuestion);
        lastQuestion = question.index;
        LinearLayout page = new LinearLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY
                        || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return true;
                return super.dispatchKeyEvent(event);
            }
        };
        page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER);
        page.setPadding(100, 30, 100, 30); page.setBackgroundColor(Color.rgb(15, 23, 42));
        text(page, isPreview ? "英语小挑战 · 预览" : "看了两分钟，学一个新知识", 23, Color.rgb(125, 211, 252));
        text(page, question.prompt, 32, Color.WHITE);
        TextView feedback = text(page, "用遥控器选择答案，答对继续观看", 19, Color.LTGRAY);
        Button[] choices = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int index = i;
            Button button = new Button(this); choices[i] = button;
            button.setText((char) ('A' + i) + "   " + question.options[i]);
            button.setTextSize(23); button.setTextColor(Color.WHITE);
            button.setPadding(25, 4, 25, 4); button.setAllCaps(false);
            button.setBackground(background(false));
            button.setOnFocusChangeListener((v, focused) -> v.setBackground(background(focused)));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(780, 78);
            size.topMargin = 12; page.addView(button, size);
            button.setOnClickListener(v -> {
                if (index == question.correct) {
                    feedback.setText("答对了！继续观看吧。");
                    for (Button option : choices) option.setEnabled(false);
                    Log.i("EnglishQuiz", "Correct answer; preview=" + preview);
                    handler.postDelayed(() -> dismiss(true), 650);
                } else {
                    feedback.setText("再想一想，你可以的！");
                    Log.i("EnglishQuiz", "Incorrect answer; question remains visible");
                }
            });
        }
        if (isPreview) {
            Button close = new Button(this); close.setText("结束预览");
            close.setOnClickListener(v -> dismiss(false)); page.addView(close);
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.setTitle("EnglishQuiz");
        try {
            windows.addView(page, params); quiz = page;
            if (!isPreview) acquireFocus();
            choices[0].requestFocus();
            Log.i("EnglishQuiz", "Question shown; preview=" + isPreview);
        } catch (RuntimeException error) { Log.e("EnglishQuiz", "Could not show question", error); }
    }

    private GradientDrawable background(boolean focused) {
        GradientDrawable result = new GradientDrawable(); result.setCornerRadius(14);
        result.setColor(focused ? Color.rgb(3, 105, 161) : Color.rgb(30, 41, 59));
        result.setStroke(focused ? 4 : 1, focused ? Color.rgb(125, 211, 252) : Color.rgb(71, 85, 105));
        return result;
    }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER); view.setPadding(0, 10, 0, 10);
        parent.addView(view); return view;
    }

    private void dismiss(boolean correct) {
        if (quiz == null) return;
        try { windows.removeView(quiz); }
        catch (IllegalArgumentException detached) { Log.d("EnglishQuiz", "Overlay already detached"); }
        quiz = null;
        if (correct && !preview) {
            clock.reset();
            preferences.edit().putBoolean("due", false).putLong("elapsed", 0).commit();
        }
        if (focusHeld) { audio.abandonAudioFocusRequest(focus); focusHeld = false; }
    }
    @Override public void onInterrupt() { }
    @Override protected void dump(FileDescriptor descriptor, PrintWriter writer, String[] args) {
        writer.println("EnglishQuiz enabled=" + (preferences != null && preferences.getBoolean("enabled", true))
                + " due=" + (preferences != null && preferences.getBoolean("due", false))
                + " elapsedMs=" + (clock == null ? -1 : clock.elapsed())
                + " quizVisible=" + (quiz != null));
    }
    @Override public boolean onUnbind(Intent intent) {
        handler.removeCallbacksAndMessages(null); dismiss(false);
        if (preferences != null && clock != null) preferences.edit().putLong("elapsed", clock.elapsed()).commit();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); dismiss(false);
        if (preferences != null && clock != null) preferences.edit().putLong("elapsed", clock.elapsed()).commit();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
