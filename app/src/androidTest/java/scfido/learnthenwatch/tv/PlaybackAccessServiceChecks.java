package scfido.learnthenwatch.tv;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import static scfido.learnthenwatch.tv.LearningState.*;

/** Driven by check_playback_access.py using adb, without UiAutomation disconnecting accessibility. */
final class PlaybackAccessServiceChecks {
    static final String ACTION = BuildConfig.APPLICATION_ID + ".test.MONITOR_COMMAND";
    private final Instrumentation instrument;
    private final Context context;
    private final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
    private final String database = "instrumentation-monitor.db", preferences = "instrumentation-monitor-settings";
    private final Field singleton, serviceRepo, serviceGate, sampleCount, studyScreen, onFinished;
    private LearningRepository repo;
    private Object original;
    private boolean installed, registered;
    private int assertions;
    private boolean testStandby;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!ACTION.equals(i.getAction())) return;
            String command = i.getStringExtra("command");
            if ("open_video".equals(command)) openVideo(); else if (command != null) commands.add(command);
        }
    };

    private void openVideo() {
        PlaybackAccessService service = PlaybackAccessService.instance;
        if (!installed || service == null) return;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!PlaybackAccessService.TARGET.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return;
            for (String id : new String[]{"com.xiaodianshi.tv.yst.tab:id/image_layout1", "com.xiaodianshi.tv.yst.tab:id/rvCards"}) {
                java.util.List<AccessibilityNodeInfo> tiles = root.findAccessibilityNodeInfosByViewId(id);
                try { for (AccessibilityNodeInfo tile : tiles) if (clickTile(tile)) return; }
                finally { for (AccessibilityNodeInfo tile : tiles) tile.recycle(); }
            }
        } finally { root.recycle(); }
    }
    private boolean clickTile(AccessibilityNodeInfo node) {
        if (!node.isVisibleToUser()) return false;
        if (node.isClickable() && node.isEnabled()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int n = 0; n < node.getChildCount(); n++) {
            AccessibilityNodeInfo child = node.getChild(n); if (child == null) continue;
            try { if (clickTile(child)) return true; } finally { child.recycle(); }
        }
        return false;
    }

    private PlaybackAccessServiceChecks(Instrumentation i, Context c) throws Exception {
        instrument = i; context = c;
        singleton = field(LearningRepository.class, "singleton"); serviceRepo = field(PlaybackAccessService.class, "repository");
        serviceGate = field(PlaybackAccessService.class, "gate"); sampleCount = field(PlaybackAccessService.class, "samples");
        studyScreen = field(PlaybackAccessService.class, "screen"); onFinished = field(LessonScreen.class, "onFinished");
    }
    static int run(Instrumentation i, Context c, boolean standby) throws Exception {
        PlaybackAccessServiceChecks checks = new PlaybackAccessServiceChecks(i, c);
        checks.testStandby = standby;
        try { checks.run(); return checks.assertions; } finally { checks.close(); }
    }
    private void run() throws Exception {
        check(PlaybackAccessService.instance == null, "disable only the English service before isolated monitor checks");
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
        VocabularyCatalog curriculum;
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open("curriculum.tsv"), "UTF-8")) { curriculum = VocabularyCatalog.load(reader); }
        repo = new LearningRepository(context, curriculum, new LearningRepository.Clock() {
            public String day() { return java.time.LocalDate.now().toString(); }
            public long now() { return System.currentTimeMillis(); }
        }, database, preferences);
        repo.setPin("2468"); ParentSettings settings = repo.settings(); settings.enabled = true; settings.autoSpeak = false; repo.saveSettings(settings);
        synchronized (LearningRepository.class) { original = singleton.get(null); singleton.set(null, repo); installed = true; }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, new IntentFilter(ACTION));
        registered = true;
        phase("READY", "Enable service and start real target playback");
        await(() -> gate() != null, 60000, "zero-balance playback shows a gate");
        check(repo.snapshot().balanceMs == 0, "first viewing requires learning");
        View first = gate();
        main(() -> {
            for (int n = 0; n < 30; n++) {
                AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
                PlaybackAccessService.instance.onAccessibilityEvent(event); event.recycle();
            }
        });
        settle(1500); check(gate() == first, "duplicate window changes retain one gate and the active screen");
        check(repo.snapshot().todayUsedMs == 0, "learning does not consume viewing time");
        PlaybackAccessService service = PlaybackAccessService.instance;
        main(() -> { for (int n = 0; n < 4; n++) service.onServiceConnected(); });
        await(() -> gate() != null, 5000, "runtime reconnect restores gate from repository");
        main(() -> { for (int n = 0; n < 25; n++) service.settingsChanged(); });
        await(() -> gate() != null, 5000, "settings burst refreshes the gate");
        settle(1000); long beforeSamples = samples(); settle(3100);
        check(samples() - beforeSamples <= 5, "reconnect and settings bursts leave one periodic poll chain");
        check(repo.snapshot().balanceMs == 0 && repo.snapshot().todayUsedMs == 0, "reconnect grants and charges no time");
        phase("GATE_VISIBLE", "Real overlay attached; awarding one isolated learning round");
        Session finished = repo.ensureSession(); Task task; int guard = 0;
        while ((task = repo.currentTask(finished.id)) != null) {
            if (++guard > 80) throw new AssertionError("fixture session stuck");
            if (task.needsIntro) repo.introduceItem(finished.id, task.id);
            repo.submitAnswer(finished.id, task.id, task.correct);
        }
        check(repo.completeAndGrant(finished.id) == 600000, "one round grants configured ten minutes");
        main(() -> {
            try {
                Object activeScreen = studyScreen.get(service);
                if (activeScreen == null) throw new AssertionError("Player left the foreground during the award; dismiss player dialogs before rerunning monitor checks");
                ((Runnable) onFinished.get(activeScreen)).run();
            }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        });
        await(() -> gate() == null && repo.snapshot().todayUsedMs > 1000, 15000, "completion releases overlay/focus and actual playback resumes counting");
        phase("PAUSE", "Pause the real player"); command("paused");
        settle(1500);
        check(!audio().isMusicActive(), "player audio actually paused");
        long paused = repo.snapshot().todayUsedMs; settle(2500);
        check(repo.snapshot().todayUsedMs == paused, "pause is not charged");
        check(gate() == null, "paused player with balance is not gated");
        if (testStandby) {
            phase("SLEEP", "Put television in standby"); command("asleep");
            await(() -> !interactive(), 5000, "screen is actually in standby"); settle(1300);
            long sleeping = repo.snapshot().todayUsedMs; settle(2000);
            check(repo.snapshot().todayUsedMs == sleeping && gate() == null, "standby charges no time and has no overlay");
            phase("WAKE", "Wake and resume target playback"); command("awake");
            await(() -> interactive() && repo.snapshot().todayUsedMs > sleeping, 20000, "wake resumes confirmed playback without charging standby");
            check(repo.snapshot().todayUsedMs - sleeping < 6000, "standby interval is not backfilled");
        } else {
            phase("RESUME", "Resume playback; physical standby explicitly excluded on this firmware"); command("resumed");
            await(() -> repo.snapshot().todayUsedMs > paused, 15000, "actual resumed playback counts again");
        }
        repo.consumeViewing(Math.max(0, repo.snapshot().balanceMs - 4000));
        long expiryStart = SystemClock.elapsedRealtime();
        await(() -> gate() != null, 8000, "actual playback exhausts a short balance and shows learning");
        check(repo.snapshot().balanceMs == 0, "expiry is based on persisted zero balance");
        check(SystemClock.elapsedRealtime() - expiryStart < 6500, "gate appears within the one-second sampling tolerance");
        String sessionId = repo.currentSession().id;
        phase("BACK", "Press Back on the learning overlay"); command("back");
        await(() -> gate() == null, 5000, "Back exits viewing and removes the overlay"); settle(1200);
        check(repo.currentSession().id.equals(sessionId), "Back preserves unfinished learning");
        phase("REENTER", "Return to the video and play"); command("reentered");
        await(() -> gate() != null, 15000, "returning to playback cannot bypass zero balance");
        phase("HOME", "Press Home while learning"); command("home");
        await(() -> gate() == null, 5000, "Home switches away and removes the overlay"); settle(1300);
        check(repo.currentSession().id.equals(sessionId), "Home preserves unfinished learning");
        phase("REENTER_AGAIN", "Return to playback for system rebind"); command("reentered");
        await(() -> gate() != null, 15000, "gate returns after Home");
        PlaybackAccessService old = PlaybackAccessService.instance;
        phase("REBIND", "Disconnect and re-enable the English accessibility service"); command("rebound");
        await(() -> PlaybackAccessService.instance != null && PlaybackAccessService.instance != old && gate() != null, 15000, "actual system unbind/rebind recreates one gate");
        check(repo.currentSession().id.equals(sessionId) && repo.snapshot().balanceMs == 0, "system rebind retains task and balance");
        check(repo.snapshot().todayRounds == 1, "lifecycle events never issue duplicate rewards");
        phase("DONE", "All monitor assertions passed");
    }
    private void command(String expected) {
        await(() -> expected.equals(commands.poll()), 45000, "driver command " + expected);
    }
    private void phase(String event, String detail) {
        Bundle b = new Bundle(); b.putString("stream", "MONITOR_" + event + ": " + detail + "\n"); instrument.sendStatus(1, b);
    }
    private View gate() {
        final View[] result = new View[1]; main(() -> {
            PlaybackAccessService s = PlaybackAccessService.instance;
            try { if (s != null && serviceRepo.get(s) == repo) result[0] = (View) serviceGate.get(s); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }); return result[0];
    }
    private long samples() {
        final long[] result = new long[1]; main(() -> {
            try { result[0] = sampleCount.getLong(PlaybackAccessService.instance); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }); return result[0];
    }
    private boolean interactive() { return ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isInteractive(); }
    private AudioManager audio() { return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE); }
    private void main(Runnable r) {
        final Throwable[] failure = new Throwable[1];
        instrument.runOnMainSync(() -> { try { r.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError("Monitor check failed on the main thread", failure[0]);
    }
    private void settle(int ms) { SystemClock.sleep(ms); main(() -> { }); }
    private void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private void await(BooleanSupplier predicate, long timeout, String message) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (!predicate.getAsBoolean()) {
            if (SystemClock.elapsedRealtime() > end) {
                java.io.StringWriter details = new java.io.StringWriter();
                main(() -> { if (PlaybackAccessService.instance != null) PlaybackAccessService.instance.dump(null, new java.io.PrintWriter(details), new String[0]); });
                throw new AssertionError(message + "\n" + details);
            }
            settle(100);
        }
        assertions++;
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private void close() throws Exception {
        if (registered) context.unregisterReceiver(receiver);
        if (installed) main(() -> {
            PlaybackAccessService s = PlaybackAccessService.instance;
            try {
                if (s != null && serviceRepo.get(s) == repo) { s.onUnbind(new Intent()); serviceRepo.set(s, null); }
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            synchronized (LearningRepository.class) { try { singleton.set(null, original); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
        });
        if (repo != null) repo.closeForTests();
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
    }
}
