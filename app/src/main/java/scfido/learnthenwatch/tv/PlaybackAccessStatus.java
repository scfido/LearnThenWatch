package scfido.learnthenwatch.tv;

/** Immutable, read-only control status shared by the launcher and parent checks. */
public final class PlaybackAccessStatus {
    public enum Kind { NEEDS_PIN, DISABLED, NEEDS_PERMISSION, CONNECTING, WAITING, WATCHING, LEARNING, ERROR }
    public final Kind kind;
    public final String message;

    public PlaybackAccessStatus(Kind kind, String message) { this.kind = kind; this.message = message; }

    public static PlaybackAccessStatus resolve(boolean hasPin, boolean enabled, boolean authorized,
            boolean connected, PlaybackAccessStatus live) {
        if (!hasPin) return new PlaybackAccessStatus(Kind.NEEDS_PIN, "未生效 · 请家长先设置 PIN");
        if (!enabled) return new PlaybackAccessStatus(Kind.DISABLED, "未生效 · 家长已关闭管控");
        if (!authorized) return new PlaybackAccessStatus(Kind.NEEDS_PERMISSION, "未生效 · 请开启「学后看 · 观看管控」");
        if (!connected) return new PlaybackAccessStatus(Kind.CONNECTING, "等待系统连接 · 无障碍已开启");
        return live;
    }
}
