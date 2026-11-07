package dev.learnthenwatch.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** Plays bundled pronunciation without requiring a network or a TV TTS engine. */
public final class PronunciationPlayer {
    public interface Result { void done(boolean success); }
    private final Context context;
    private MediaPlayer player;
    private long generation;
    public PronunciationPlayer(Context context) { this.context = context; }
    public void play(String asset, int volume, Result callback) {
        stop();
        final long token = generation;
        try {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (volume <= 0 || (manager != null && (manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                    || manager.isStreamMute(AudioManager.STREAM_MUSIC)))) {
                new Handler(Looper.getMainLooper()).post(() -> { if (token == generation) callback.done(false); });
                return;
            }
            MediaPlayer next = new MediaPlayer(); player = next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            try (AssetFileDescriptor fd = context.getAssets().openFd(asset)) {
                next.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            float gain = Math.max(0f, Math.min(1f, volume / 100f)); next.setVolume(gain, gain);
            next.setOnPreparedListener(p -> {
                if (token == generation) try { p.start(); }
                catch (RuntimeException e) { stop(); callback.done(false); }
            });
            next.setOnCompletionListener(p -> { if (token == generation) { stop(); callback.done(true); } });
            next.setOnErrorListener((p, what, extra) -> { if (token == generation) { stop(); callback.done(false); } return true; });
            next.prepareAsync();
        } catch (Exception error) {
            stop(); final long failureToken = generation;
            new Handler(Looper.getMainLooper()).post(() -> { if (failureToken == generation) callback.done(false); });
        }
    }
    public void stop() {
        generation++;
        if (player != null) { try { player.release(); } catch (RuntimeException ignored) { } player = null; }
    }
}
