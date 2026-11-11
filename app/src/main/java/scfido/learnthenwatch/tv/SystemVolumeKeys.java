package scfido.learnthenwatch.tv;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

/** Requests the TV's own volume UI when a volume key reaches a learning window. */
final class SystemVolumeKeys {
    private SystemVolumeKeys() { }

    static boolean dispatch(Context context, KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN
                && key != KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager != null && (key != KeyEvent.KEYCODE_VOLUME_MUTE || event.getRepeatCount() == 0)) {
                int direction = key == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                        : key == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            }
        }
        return true;
    }
}
