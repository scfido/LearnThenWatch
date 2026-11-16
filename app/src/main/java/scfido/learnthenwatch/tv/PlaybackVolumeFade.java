package scfido.learnthenwatch.tv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/** Silent media-volume writes with a durable recovery point and an independent watchdog. */
final class PlaybackVolumeFade {
    private static PlaybackVolumeFade owner;
    private final ViewingVolumeFade fade;

    PlaybackVolumeFade(Context context) {
        fade = new ViewingVolumeFade(new SystemAccess(context.getApplicationContext()));
        safely(fade::restore);
        owner = this;
    }
    void update(long remaining, boolean playing) {
        try { fade.update(remaining, playing); }
        catch (RuntimeException error) {
            Log.w("LearnThenWatch", "Media fade stopped; restoring original volume", error);
            safely(fade::timeout);
        }
    }
    void restore() { safely(fade::cancel); }
    void pause() { safely(fade::restore); }
    void close() { restore(); if (owner == this) owner = null; }
    static void recover(Context context) {
        if (owner != null) safely(owner.fade::timeout);
        else safely(() -> new ViewingVolumeFade(new SystemAccess(context.getApplicationContext())).restore());
    }
    private static void safely(Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException error) { Log.w("LearnThenWatch", "Temporary media-volume operation failed; recovery retained", error); }
    }

    private static final class SystemAccess implements ViewingVolumeFade.Access {
        final AudioManager audio;
        final AlarmManager alarms;
        final PendingIntent watchdog;
        final SharedPreferences journal;
        SystemAccess(Context context) {
            audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            watchdog = PendingIntent.getBroadcast(context, 0, new Intent(context, VolumeRestoreReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            journal = context.getSharedPreferences("volume_fade_recovery", Context.MODE_PRIVATE);
        }
        public ViewingVolumeFade.State current() {
            return new ViewingVolumeFade.State(audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                    audio.isStreamMute(AudioManager.STREAM_MUSIC));
        }
        public boolean supported() {
            return audio != null && !audio.isVolumeFixed() && alarms != null
                    && (Build.VERSION.SDK_INT < 28 || audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) == 0)
                    && (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms());
        }
        public ViewingVolumeFade.RestorePoint pending() {
            if (!journal.getBoolean("pending", false)) return null;
            return new ViewingVolumeFade.RestorePoint(journal.getInt("original", 0),
                    new ViewingVolumeFade.State(journal.getInt("before", 0), journal.getBoolean("beforeMuted", false)),
                    journal.getInt("target", 0));
        }
        public void save(ViewingVolumeFade.RestorePoint point) {
            boolean first = !journal.getBoolean("pending", false);
            if (!journal.edit().putBoolean("pending", true).putInt("original", point.original)
                    .putInt("before", point.before.level).putBoolean("beforeMuted", point.before.muted)
                    .putInt("target", point.target).commit()) throw new IllegalStateException("Could not save media volume");
            // If this cannot be armed, do not perform the first volume change.
            if (first) alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 6000, watchdog);
        }
        public void clear() {
            if (!journal.edit().clear().commit()) throw new IllegalStateException("Could not clear media-volume recovery");
            if (alarms != null) alarms.cancel(watchdog);
        }
        public void setLevel(int level) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0); }
        public void unmute() { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0); }
    }
}
