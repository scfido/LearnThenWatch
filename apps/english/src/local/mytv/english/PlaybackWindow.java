package local.mytv.english;

/** Window evidence is deliberately separate from audio activity and the persistent wallet. */
public final class PlaybackWindow {
    public enum State { UNKNOWN, OTHER, PLAYER }
    private final String target, own;
    private String owner = "";
    private int activityWindow = -1;
    private boolean playerActivity;
    private State lastRoot = State.UNKNOWN;

    public PlaybackWindow(String target, String own) { this.target = target; this.own = own; }

    public static boolean isPlayerViewId(String id) {
        return id != null && (id.endsWith(":id/cts_video_play") || id.endsWith(":id/video_play_view")
                || id.endsWith(":id/video_player") || id.endsWith(":id/ui_fl_play_root"));
    }

    public void reset() { owner = ""; activityWindow = -1; playerActivity = false; lastRoot = State.UNKNOWN; }
    public boolean definitelyOther() { return lastRoot == State.OTHER; }

    public void activity(String packageName, String className, int windowId) {
        if (target.equals(packageName)) {
            owner = target;
            if (className != null && className.endsWith("Activity")) {
                activityWindow = windowId;
                playerActivity = className.contains("CtsActivity") || className.contains("VideoPlayActivity")
                        || className.contains("PlayerActivity");
                lastRoot = playerActivity ? State.PLAYER : State.OTHER;
            } else if (activityWindow != windowId) {
                activityWindow = -1; playerActivity = false;
            }
        } else if (packageName != null && !"null".equals(packageName) && !system(packageName)
                && (!own.equals(packageName) || (className != null && className.endsWith("SettingsActivity")))) {
            owner = packageName; activityWindow = -1; playerActivity = false; lastRoot = State.OTHER;
        }
    }

    public State root(String packageName, int windowId, boolean hasPlayer, boolean overlay) {
        if (packageName == null || "null".equals(packageName) || system(packageName)) return State.UNKNOWN;
        // Our focused overlay hides the player's root. Only the underlying confirmed owner is retained.
        if (own.equals(packageName) && overlay) return target.equals(owner) ? lastRoot : State.OTHER;
        owner = packageName;
        if (!target.equals(packageName)) {
            activityWindow = -1; playerActivity = false; return lastRoot = State.OTHER;
        }
        if (activityWindow != windowId) { activityWindow = -1; playerActivity = false; }
        return lastRoot = hasPlayer || playerActivity ? State.PLAYER : State.OTHER;
    }

    private static boolean system(String name) { return "android".equals(name) || "com.android.systemui".equals(name); }
}
