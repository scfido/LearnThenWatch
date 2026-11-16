package scfido.learnthenwatch.tv;

/** A recoverable, temporary media-volume change. No learning or viewing state is written here. */
final class ViewingVolumeFade {
    static final long FADE_MS = 3000;
    static final class State {
        final int level;
        final boolean muted;
        State(int level, boolean muted) { this.level = level; this.muted = muted; }
        boolean same(State other) { return level == other.level && muted == other.muted; }
    }
    static final class RestorePoint {
        final int original;
        final State before;
        final int target;
        RestorePoint(int original, State before, int target) {
            this.original = original; this.before = before; this.target = target;
        }
        boolean owns(State current) {
            // The process can stop immediately before or after the system write.
            return before.same(current) || (current.level == target && (!current.muted || target == 0));
        }
    }
    interface Access {
        State current();
        boolean supported();
        RestorePoint pending();
        void save(RestorePoint point);
        void clear();
        void setLevel(int level);
        void unmute();
    }
    private final Access access;
    private State last;
    private int original;
    private boolean active, suppressed;
    ViewingVolumeFade(Access access) { this.access = access; }

    void update(long remaining, boolean playing) {
        if (!playing || remaining > FADE_MS) {
            restore();
            if (remaining > FADE_MS) suppressed = false;
            return;
        }
        if (!active) {
            if (remaining <= 0 || suppressed || !access.supported()) return;
            State current = access.current();
            if (current.muted || current.level <= 0) return;
            original = current.level; last = current; active = true;
        }
        State current = access.current();
        if (!last.same(current)) {
            // A remote, another app, or an output-route change takes precedence.
            active = false; suppressed = true; access.clear(); return;
        }
        int target = (int) Math.round(original * Math.max(0, remaining) / (double) FADE_MS);
        if (target == current.level) return;
        access.save(new RestorePoint(original, current, target)); // Durable BEFORE touching system volume.
        access.setLevel(target);
        last = access.current();
        if (last.level != target) { restore(); suppressed = true; }
    }

    void restore() {
        if (active && last != null && !last.same(access.current())) suppressed = true;
        active = false;
        RestorePoint pending = access.pending();
        if (pending == null) return;
        State current = access.current();
        if (pending.owns(current)) {
            access.setLevel(pending.original);
            if (access.current().muted) access.unmute();
            State restored = access.current();
            if (restored.level != pending.original || restored.muted)
                throw new IllegalStateException("Media volume restoration not confirmed");
        } else suppressed = true;
        access.clear();
    }
    void cancel() { restore(); suppressed = false; }
    void timeout() { restore(); suppressed = true; }
}
