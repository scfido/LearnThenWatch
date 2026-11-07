package dev.learnthenwatch.tv;

import dev.learnthenwatch.tv.PlaybackAccessStatus;
import dev.learnthenwatch.tv.PlayerWindowTracker;
import static dev.learnthenwatch.tv.PlaybackAccessStatus.Kind.*;
import static dev.learnthenwatch.tv.PlayerWindowTracker.State.*;

public final class PlaybackAccessPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @org.junit.Test
    public void regression() {
        PlaybackAccessStatus live = new PlaybackAccessStatus(WATCHING, "counting");
        check(PlaybackAccessStatus.resolve(false, true, true, true, live).kind == NEEDS_PIN, "PIN is required even with an authorized connected service");
        check(PlaybackAccessStatus.resolve(true, false, true, true, live).kind == DISABLED, "parent switch overrides a running service");
        check(PlaybackAccessStatus.resolve(true, true, false, false, live).kind == NEEDS_PERMISSION, "missing permission needs an actionable status");
        check(PlaybackAccessStatus.resolve(true, true, true, false, live).kind == CONNECTING, "enabled permission must not imply a bound service");
        check(PlaybackAccessStatus.resolve(true, true, true, true, live) == live, "connected service supplies the live immutable status");
        PlaybackAccessStatus error = new PlaybackAccessStatus(ERROR, "failed");
        check(PlaybackAccessStatus.resolve(true, true, true, true, error).kind == ERROR, "a runtime fault must not look ready");

        PlayerWindowTracker window = new PlayerWindowTracker("video", "english");
        check(PlayerWindowTracker.isPlayerViewId("com.xiaodianshi.tv.yst:id/ui_fl_play_root"), "current TV player can be recognized after late service connection");
        check(PlayerWindowTracker.isPlayerViewId("com.xiaodianshi.tv.yst:id/cts_video_play"), "previous player container remains supported");
        check(!PlayerWindowTracker.isPlayerViewId("com.xiaodianshi.tv.yst:id/controller_root") && !PlayerWindowTracker.isPlayerViewId(null), "generic controls and absent ids do not invent playback");
        check(window.root(null, -1, false, false) == UNKNOWN, "no root is unknown rather than a confirmed app switch");
        check(window.root("video", 5, true, false) == PLAYER, "existing player can be found without an activity event after reconnect");
        check(window.root("english", 6, false, true) == PLAYER, "overlay focus retains its underlying confirmed player");
        check(window.root(null, -1, false, true) == UNKNOWN, "a temporarily missing overlay root does not invent evidence");
        window.activity("launcher", "HomeActivity", 7);
        check(window.definitelyOther(), "Home invalidates player before a new root is available");
        check(window.root("english", 6, false, true) == OTHER, "stale overlay focus cannot hide Home");
        window.activity("video", "CtsActivity", 8);
        check(window.root("video", 8, false, false) == PLAYER, "confirmed player activity is scoped to its window");
        check(window.root("video", 9, false, false) == OTHER, "new same-package window cannot reuse a stale activity hint");
        window.activity("video", "PlayerActivity", 10);
        window.activity("video", "MainActivity", 10);
        check(window.definitelyOther(), "returning to a list clears playback even if the window id is reused");
        check(window.root("video", 10, false, false) == OTHER, "browsing never counts as playback");
        window.activity("video", "VideoPlayActivity", 11);
        window.root("video", 11, true, false);
        window.activity("com.android.systemui", "VolumeDialog", 12);
        check(window.root("com.android.systemui", 12, false, true) == UNKNOWN, "system panels suspend confirmation");
        check(window.root("english", 13, false, true) == PLAYER, "system panel does not discard the learning session");
        window.activity("english", "MainActivity", 14);
        check(window.definitelyOther(), "opening the launcher dismisses a service gate");
        window.reset();
        check(window.root("english", 15, false, true) == OTHER, "reconnect cannot reuse pre-disconnect player evidence");
        check(window.root("video", 16, false, false) == OTHER, "reset does not invent playback on the target list");
        System.out.println("PASS: monitor readiness, transient roots, overlay ownership, Home, list transitions and reconnect");
    }
}
