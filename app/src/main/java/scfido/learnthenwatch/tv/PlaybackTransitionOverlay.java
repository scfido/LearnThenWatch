package scfido.learnthenwatch.tv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** A thin, non-interactive accessibility window for the final viewing seconds. */
final class PlaybackTransitionOverlay {
    static final long WARNING_MS = 30000;
    private final Context context;
    private final TvUiTheme theme;
    private final WindowManager windows;
    private FrameLayout warning;
    private View bar;

    PlaybackTransitionOverlay(Context context, TvUiTheme theme) {
        this.context = context; this.theme = theme;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    static long remaining(long balance, long daily) { return Math.max(0, Math.min(balance, daily)); }
    static boolean shouldWarn(long remaining) { return remaining > 0 && remaining <= WARNING_MS; }

    void update(long remaining, boolean playing) {
        if (!shouldWarn(remaining)) { hideWarning(); return; }
        float fraction = remaining / (float) WARNING_MS;
        if (warning == null) {
            warning = new FrameLayout(context);
            warning.setTag("watch:countdown");
            theme.backgroundColor(warning, TvUiTheme.Role.WARNING_TRACK);
            warning.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            bar = new View(context); theme.backgroundColor(bar, TvUiTheme.Role.ACCENT); bar.setPivotX(0);
            bar.setScaleX(fraction);
            warning.addView(bar, new FrameLayout.LayoutParams(-1, -1));
            try { windows.addView(warning, params(TvUiTheme.dp(context, 4))); }
            catch (RuntimeException error) { warning = null; bar = null; throw error; }
        }
        bar.animate().cancel();
        // A pause or delayed sample may confirm slightly less elapsed time than
        // predicted. Freeze at the displayed width instead of growing backwards.
        float displayed = Math.min(bar.getScaleX(), fraction);
        bar.setScaleX(displayed);
        // Interpolate toward the next sample continuously, rather than moving for
        // 250 ms and then stopping. Only repository-confirmed time drives the gate.
        if (playing) bar.animate().scaleX(Math.min(displayed, Math.max(0, remaining - 1000) / (float) WARNING_MS))
                .setInterpolator(new android.view.animation.LinearInterpolator()).setDuration(1000).start();
    }

    void refreshTheme(ThemeMode mode) { theme.refresh(mode, warning); }

    private WindowManager.LayoutParams params(int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("LearnThenWatchTransition");
        return params;
    }

    void hideWarning() {
        if (warning == null) return;
        bar.animate().cancel(); windows.removeViewImmediate(warning); warning = null; bar = null;
    }

    void close() { hideWarning(); }
}
