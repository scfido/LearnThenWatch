package scfido.learnthenwatch.tv;

import android.content.Context;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Keeps media-volume feedback visible above accessibility learning content. */
class VolumeFeedbackLayout extends FrameLayout {
    private TextView indicator;
    private final Runnable hideVolume = () -> {
        if (indicator != null) indicator.setVisibility(GONE);
    };

    VolumeFeedbackLayout(Context context) { super(context); }

    boolean dispatchVolumeKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN
                && key != KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            AudioManager manager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) {
                if (key != KeyEvent.KEYCODE_VOLUME_MUTE || event.getRepeatCount() == 0) {
                    int direction = key == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                            : key == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
                    manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
                }
                if (indicator == null || indicator.getParent() != this) {
                    indicator = TvUiTheme.text(getContext(), "", 21, TvUiTheme.INK);
                    indicator.setTag("study:volume");
                    indicator.setGravity(Gravity.CENTER);
                    int padding = TvUiTheme.dp(getContext(), 18);
                    indicator.setPadding(padding, padding, padding, padding);
                    indicator.setBackground(TvUiTheme.shape(getContext(), TvUiTheme.WHITE, TvUiTheme.TEAL, 2, 16));
                    LayoutParams size = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                    size.setMargins(padding, padding, padding, padding);
                    addView(indicator, size);
                }
                int current = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                indicator.setText(manager.isStreamMute(AudioManager.STREAM_MUSIC) ? "媒体音量 · 静音"
                        : "媒体音量  " + current + " / " + max);
                indicator.setVisibility(VISIBLE); indicator.bringToFront();
                removeCallbacks(hideVolume); postDelayed(hideVolume, 2000);
            }
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(hideVolume);
        if (indicator != null) indicator.setVisibility(GONE);
        super.onDetachedFromWindow();
    }
}
