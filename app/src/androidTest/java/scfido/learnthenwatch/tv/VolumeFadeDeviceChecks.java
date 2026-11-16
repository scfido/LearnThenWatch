package scfido.learnthenwatch.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;

/** Exercises the real stream and watchdog, restoring the pre-test volume in all cases. */
final class VolumeFadeDeviceChecks {
    static int run(Instrumentation instrument, Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int original = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        if (muted || original < 2) throw new AssertionError("Volume fade device check needs an audible media level >= 2");
        final PlaybackVolumeFade[] controller = new PlaybackVolumeFade[1];
        try {
            instrument.runOnMainSync(() -> {
                controller[0] = new PlaybackVolumeFade(context);
                controller[0].update(1500, true);
            });
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == Math.round(original / 2f), "half-volume fade");
            phase(instrument, "VOLUME_SILENT: fade has no system volume indicator"); SystemClock.sleep(1200);
            instrument.runOnMainSync(() -> controller[0].update(1500, false));
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original, "pause restores media volume");
            phase(instrument, "VOLUME_SILENT: restoration has no system volume indicator"); SystemClock.sleep(700);
            instrument.runOnMainSync(() -> {
                controller[0].update(1500, true);
                controller[0].close();
            });
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original, "service close restores volume");
            instrument.runOnMainSync(() -> {
                controller[0] = new PlaybackVolumeFade(context);
                controller[0].update(1500, true);
                // Recreate the controller from its on-disk recovery point.
                controller[0] = new PlaybackVolumeFade(context);
            });
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original, "persisted recovery restores volume");
            instrument.runOnMainSync(() -> {
                controller[0].update(1500, true);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, original - 1, 0);
                controller[0].update(1000, true); controller[0].restore();
            });
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original - 1, "manual volume takes precedence");
            instrument.runOnMainSync(() -> {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0);
                controller[0].update(30000, true); controller[0].update(1500, true);
            });
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) < original, "watchdog starts with a live fade");
            SystemClock.sleep(6600);
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original, "independent six-second alarm restores volume");
            instrument.runOnMainSync(() -> controller[0].update(500, true));
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC) == original, "watchdog cancels the remaining fade");
            instrument.runOnMainSync(() -> {
                controller[0].update(30000, true);
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                controller[0].update(1500, true); controller[0].restore();
            });
            check(audio.isStreamMute(AudioManager.STREAM_MUSIC), "original mute is preserved");
            return 9;
        } finally {
            instrument.runOnMainSync(() -> {
                if (controller[0] != null) controller[0].close();
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0);
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
            });
        }
    }
    private static void phase(Instrumentation instrument, String message) {
        Bundle b = new Bundle(); b.putString("stream", message + "\n"); instrument.sendStatus(1, b);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
