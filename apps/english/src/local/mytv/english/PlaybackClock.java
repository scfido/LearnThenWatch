package local.mytv.english;

/** Counts confirmed playback, never charging a disconnected polling interval. */
public final class PlaybackClock {
    private long accumulated;
    private long previous = -1;
    private boolean wasPlaying;
    public PlaybackClock(long accumulated) { this.accumulated = accumulated; }
    public long tick(long now, boolean playing) {
        if (previous >= 0 && wasPlaying && playing) {
            accumulated += Math.max(0, Math.min(2000, now - previous));
        }
        previous = now;
        wasPlaying = playing;
        return accumulated;
    }
    public void reset() { accumulated = 0; previous = -1; wasPlaying = false; }
    public long elapsed() { return accumulated; }
}
