package local.mytv.english;

/** Immutable, read-only control status shared by the launcher and parent checks. */
public final class MonitorStatus {
    public enum Kind { NEEDS_PIN, DISABLED, NEEDS_PERMISSION, CONNECTING, WAITING, WATCHING, LEARNING, ERROR }
    public final Kind kind;
    public final String message;

    public MonitorStatus(Kind kind, String message) { this.kind = kind; this.message = message; }

    public static MonitorStatus resolve(boolean hasPin, boolean enabled, boolean authorized,
            boolean connected, MonitorStatus live) {
        if (!hasPin) return new MonitorStatus(Kind.NEEDS_PIN, "未生效 · 请家长先设置 PIN");
        if (!enabled) return new MonitorStatus(Kind.DISABLED, "未生效 · 家长已关闭管控");
        if (!authorized) return new MonitorStatus(Kind.NEEDS_PERMISSION, "未生效 · 请开启英语学习无障碍服务");
        if (!connected) return new MonitorStatus(Kind.CONNECTING, "等待系统连接 · 无障碍已开启");
        return live;
    }
}
