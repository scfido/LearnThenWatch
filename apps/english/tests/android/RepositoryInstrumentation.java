package local.mytv.english;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import static local.mytv.english.StudyModels.*;

/** Separate, signed test APK. Uses isolated DB/preferences and never changes the child's records. */
public final class RepositoryInstrumentation extends Instrumentation {
    private int assertions;
    private Context context;
    private StudyRepository repo;
    private final FakeClock clock = new FakeClock();
    private final String dbName = "instrumentation-study.db";
    private final String prefsName = "instrumentation-settings";
    private Bundle arguments;

    private static final class FakeClock implements StudyRepository.Clock {
        String date = "2030-01-01";
        long time = 1893456000000L;
        public String day() { return date; }
        public long now() { return time; }
    }

    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle(); context = getTargetContext();
        try {
            if (arguments != null && "true".equals(arguments.getString("remote"))) {
                int checks = RemoteUiChecks.run(this, context);
                result.putString("stream", "PASS: " + checks + " real remote navigation assertions; isolated data cleaned.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && "true".equals(arguments.getString("ui"))) {
                int checks = StudyUiChecks.run(this, context);
                result.putString("stream", "PASS: " + checks + " TV layout/input assertions; isolated UI data cleaned.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && "gate".equals(arguments.getString("screen"))) {
                gateProbe();
                result.putString("stream", "PASS: isolated accessibility gate probe finished; singleton restored.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            if (arguments != null && "monitor".equals(arguments.getString("screen"))) {
                int checks = MonitorServiceChecks.run(this, context, !"false".equals(arguments.getString("standby")));
                result.putString("stream", "PASS: " + checks + " actual monitor lifecycle/playback assertions; isolated data cleaned.\n");
                finish(Activity.RESULT_OK, result); return;
            }
            defaultsAndPin();
            correctionRecoveryAndReward();
            dailyLimitsAndRollover();
            sessionConfiguration();
            transactionRollback();
            concurrentAward();
            newWordLimit();
            earlyReviewKeepsDueDate();
            completionTimeEligibility();
            staleAnswerSnapshots();
            if (arguments != null && arguments.containsKey("screen")) previewScreen(arguments.getString("screen"));
            result.putString("stream", "PASS: " + assertions + " Android SQLite assertions; isolated data cleaned.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            android.util.Log.e("EnglishQuizTest", "Repository test failed", error);
            result.putString("stream", "FAIL after " + assertions + " assertions: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally { cleanup(); }
    }

    private Curriculum fixture() {
        String[] words = {"apple", "bridge", "clever", "festival", "library", "journey",
                "achieve", "adapt", "ambitious", "analyse", "career", "challenge"};
        String[] meanings = {"苹果", "桥", "聪明的", "节日", "图书馆", "旅程", "实现", "适应", "有抱负的", "分析", "职业", "挑战"};
        String[] examples = {"I ate an apple.", "We crossed the bridge.", "She is a clever student.",
                "We enjoy the festival.", "The library is open.", "We enjoyed the journey.",
                "You can achieve your goal.", "We adapt to new situations.", "She is ambitious.",
                "We analyse the results.", "He enjoys his career.", "This is a challenge."};
        List<Curriculum.Item> items = new ArrayList<>();
        for (int i = 0; i < words.length; i++) items.add(new Curriculum.Item("test_" + i,
                i < 6 ? "KET" : "PET", "学习与生活", words[i], meanings[i], examples[i], "", "audio/test.mp3"));
        return new Curriculum(items);
    }

    private void fresh() {
        cleanup(); clock.date = "2030-01-01"; clock.time = 1893456000000L;
        repo = new StudyRepository(context, fixture(), clock, dbName, prefsName);
    }

    private void cleanup() {
        if (repo != null) { repo.closeForTests(); repo = null; }
        if (context != null) {
            context.deleteDatabase(dbName);
            context.getSharedPreferences(prefsName, 0).edit().clear().commit();
        }
    }

    private void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }

    private void expectFailure(Runnable operation, String message) {
        boolean failed = false;
        try { operation.run(); } catch (RuntimeException expected) { failed = true; }
        check(failed, message);
    }

    private void defaultsAndPin() {
        fresh(); AppSettings s = repo.settings();
        check(s.taskCount == 5 && s.rewardMinutes == 10 && s.dailyLimitMinutes == 60 && s.dailyNewLimit == 5, "default rules");
        check(repo.snapshot().balanceMs == 0 && !repo.hasPin(), "new/legacy time never becomes a reward");
        Session noPin = finishRound();
        check(repo.completeAndGrant(noPin.id) == 0, "no PIN means no viewing reward");
        repo.setPin("2468"); check(repo.verifyPin("2468"), "PIN verification");
        for (int i = 0; i < 5; i++) check(!repo.verifyPin("1357"), "incorrect PIN rejected");
        check(!repo.verifyPin("2468"), "PIN lock survives valid input during cooldown");
        clock.time += 31000; check(repo.verifyPin("2468"), "PIN lock expires");
    }

    private Session finishRound() {
        Session s = repo.ensureSession(); int guard = 0;
        Task t;
        while ((t = repo.currentTask(s.id)) != null) {
            if (++guard > 100) throw new AssertionError("session did not terminate");
            if (t.needsIntro) repo.introduceItem(s.id, t.id);
            repo.submitAnswer(s.id, t.id, t.correct);
        }
        return s;
    }

    private void correctionRecoveryAndReward() {
        fresh(); repo.setPin("2468"); Session s = repo.ensureSession();
        Task first = repo.currentTask(s.id); String taskId = first.id;
        expectFailure(() -> repo.completeAndGrant(s.id), "unfinished session cannot award");
        String[] order = first.options.clone();
        repo.introduceItem(s.id, first.id);
        repo.closeForTests(); repo = new StudyRepository(context, fixture(), clock, dbName, prefsName);
        check(repo.ensureSession().id.equals(s.id), "unfinished session recovered");
        first = repo.currentTask(s.id);
        check(Arrays.equals(order, first.options) && !first.needsIntro, "question order and introduction recovered");
        repo.submitAnswer(s.id, first.id, (first.correct + 1) % first.options.length);
        int total = repo.snapshot().totalAnswered;
        repo.submitAnswer(s.id, first.id, first.correct);
        check(repo.snapshot().totalAnswered == total, "duplicate main answer is idempotent");
        Task t;
        while ((t = repo.currentTask(s.id)) != null && !t.correction) {
            if (t.needsIntro) repo.introduceItem(s.id, t.id);
            repo.submitAnswer(s.id, t.id, t.correct);
        }
        check(t != null && t.id.equals(taskId) && t.correction, "corrections follow all main tasks");
        expectFailure(() -> repo.completeAndGrant(s.id), "uncorrected error cannot award");
        for (int i = 0; i < 2; i++) {
            t = repo.currentTask(s.id);
            repo.submitAnswer(s.id, t.id, (t.correct + 1) % t.options.length);
        }
        t = repo.currentTask(s.id); repo.confirmGuidedCorrection(s.id, t.id);
        check(repo.snapshot().totalAnswered == 5 && repo.snapshot().firstCorrect == 4, "guidance preserves first-attempt accuracy");
        check(repo.completeAndGrant(s.id) == 600000, "one corrected round earns ten minutes");
        repo.consumeViewing(1000);
        check(repo.completeAndGrant(s.id) == 600000 && repo.snapshot().balanceMs == 599000, "repeated award does not refill wallet");
        Session practice = finishRound();
        check(repo.completeAndGrant(practice.id) == 0 && repo.snapshot().balanceMs == 599000, "voluntary learning cannot stack balance");
        repo.closeForTests(); repo = new StudyRepository(context, fixture(), clock, dbName, prefsName);
        check(repo.snapshot().balanceMs == 599000 && repo.snapshot().todayUsedMs == 1000, "consumption persists across repository restart");
    }

    private void dailyLimitsAndRollover() {
        fresh(); repo.setPin("2468");
        Session s = finishRound(); repo.completeAndGrant(s.id); repo.consumeViewing(60000);
        AppSettings settings = repo.settings(); settings.dailyLimitMinutes = 3; repo.saveSettings(settings);
        check(repo.snapshot().balanceMs == 120000, "lower limit immediately clips balance");
        repo.consumeViewing(Long.MAX_VALUE);
        check(repo.snapshot().balanceMs == 0 && repo.snapshot().todayUsedMs == 180000, "consumption clamps to balance and daily cap");
        s = finishRound(); check(repo.completeAndGrant(s.id) == 0, "daily cap blocks rewards but not learning");
        clock.date = "2030-01-02"; clock.time += 86400000;
        check(repo.snapshot().todayUsedMs == 0 && repo.snapshot().balanceMs == 0, "new day resets consumption and old balance");
        s = finishRound(); check(repo.completeAndGrant(s.id) == 180000, "short remaining daily quota caps reward");
        String awarded = s.id;
        clock.date = "2030-01-03"; clock.time += 86400000;
        repo.snapshot(); repo.completeAndGrant(awarded);
        check(repo.snapshot().balanceMs == 0, "old completed session never restores expired balance");
    }

    private void sessionConfiguration() {
        fresh(); repo.setPin("2468"); Session before = repo.ensureSession();
        AppSettings s = repo.settings(); s.taskCount = 3; s.rewardMinutes = 2; s.stage = "PET"; repo.saveSettings(s);
        check(repo.ensureSession().taskCount == 5 && repo.ensureSession().rewardMs == 600000, "active round retains task and reward snapshot");
        finishRound(); check(repo.completeAndGrant(before.id) == 600000, "active round retains original reward");
        Session next = repo.ensureSession();
        check(next.taskCount == 3 && next.rewardMs == 120000 && next.stage.equals("PET"), "next round uses updated settings");
    }

    private void transactionRollback() {
        fresh(); repo.setPin("2468"); Session s = finishRound();
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, 0, null);
        db.execSQL("CREATE TRIGGER qa_refuse_reward BEFORE UPDATE OF balance ON wallet WHEN NEW.balance>OLD.balance BEGIN SELECT RAISE(ABORT,'qa simulated storage failure'); END");
        expectFailure(() -> repo.completeAndGrant(s.id), "award transaction reports storage failure");
        check(repo.snapshot().balanceMs == 0 && repo.snapshot().todayRounds == 0, "failed award rolls back completion and counters");
        db.execSQL("DROP TRIGGER qa_refuse_reward"); db.close();
        check(repo.completeAndGrant(s.id) == 600000 && repo.snapshot().todayRounds == 1, "retry after failed transaction grants exactly once");
    }

    private void concurrentAward() throws Exception {
        fresh(); repo.setPin("2468"); final Session s = finishRound();
        final CountDownLatch start = new CountDownLatch(1); final Throwable[] failures = new Throwable[2];
        Thread[] threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try { start.await(); repo.completeAndGrant(s.id); } catch (Throwable e) { failures[index] = e; }
            }); threads[i].start();
        }
        start.countDown(); for (Thread thread : threads) thread.join();
        check(failures[0] == null && failures[1] == null, "concurrent callers complete successfully");
        check(repo.snapshot().balanceMs == 600000 && repo.snapshot().todayRounds == 1, "concurrent award has one ledger effect");
    }

    private void newWordLimit() {
        fresh(); repo.setPin("2468");
        for (int i = 0; i < 6; i++) { Session s = finishRound(); repo.completeAndGrant(s.id); }
        check(repo.history().get(0).newCount == 5, "daily new-word limit respected across rounds");
        int learned = 0; for (ItemProgress p : repo.progress("KET")) if (p.learned) learned++;
        check(learned == 5, "at most two new items per round and five per day");
    }

    private ItemProgress itemProgress(String itemId) {
        for (ItemProgress p : repo.progress("KET")) if (p.itemId.equals(itemId)) return p;
        throw new AssertionError("missing progress " + itemId);
    }

    private void earlyReviewKeepsDueDate() {
        fresh(); repo.setPin("2468");
        AppSettings settings = repo.settings(); settings.taskCount = 1; settings.dailyNewLimit = 30;
        repo.saveSettings(settings);
        // Introduce every fixture item, so day-three practice cannot select an unseen word instead.
        for (int i = 0; i < 6; i++) { Session s = finishRound(); repo.completeAndGrant(s.id); }
        check(repo.snapshot().learnedCount == 6, "review fixture has six learned items");
        clock.date = "2030-01-02"; clock.time += 86400000;
        for (int i = 0; i < 6; i++) { Session s = finishRound(); repo.completeAndGrant(s.id); }
        for (ItemProgress p : repo.progress("KET"))
            check(p.reviewStep == 1 && p.dueDay.equals("2030-01-05"), "due independent recall advances to three-day interval");
        clock.date = "2030-01-03"; clock.time += 86400000;
        Session early = repo.ensureSession(); Task task = repo.currentTask(early.id);
        String itemId = task.itemId;
        repo.submitAnswer(early.id, task, task.correct); repo.completeAndGrant(early.id);
        ItemProgress after = itemProgress(itemId);
        check(after.reviewStep == 1 && after.dueDay.equals("2030-01-05"), "early practice does not postpone scheduled review");
    }

    private void completionTimeEligibility() {
        fresh(); repo.setPin("2468");
        Session first = finishRound(); repo.completeAndGrant(first.id);
        Session beganAsPractice = repo.ensureSession();
        check(!beganAsPractice.rewardEligible, "a round begun with a balance is initially practice");
        repo.consumeViewing(600000);
        check(repo.currentSession().rewardEligible, "round eligibility follows balance at completion");
        Session finished = finishRound();
        check(finished.id.equals(beganAsPractice.id), "learning round is preserved when old balance runs out");
        check(repo.completeAndGrant(finished.id) == 600000 && repo.snapshot().balanceMs == 600000,
                "round completed after balance exhaustion earns one new allowance");
        Session stillHasBalance = finishRound();
        check(repo.completeAndGrant(stillHasBalance.id) == 0 && repo.snapshot().balanceMs == 600000,
                "round completed with remaining balance does not stack time");
        repo.consumeViewing(600000); repo.completeAndGrant(stillHasBalance.id);
        check(repo.snapshot().balanceMs == 0, "completed practice never earns a delayed reward");
    }

    private void staleAnswerSnapshots() {
        fresh(); repo.setPin("2468");
        AppSettings settings = repo.settings(); settings.taskCount = 1; repo.saveSettings(settings);
        Session session = repo.ensureSession(); Task main = repo.currentTask(session.id);
        repo.introduceItem(session.id, main.id); main = repo.currentTask(session.id);
        int wrong = (main.correct + 1) % main.options.length;
        repo.submitAnswer(session.id, main, wrong);
        repo.submitAnswer(session.id, main, wrong);
        Task correction = repo.currentTask(session.id);
        check(correction.correction && correction.correctionAttempts == 0,
                "stale last-main click cannot become a correction attempt");
        repo.submitAnswer(session.id, correction, wrong);
        repo.submitAnswer(session.id, correction, wrong);
        check(repo.currentTask(session.id).correctionAttempts == 1,
                "duplicate correction snapshot cannot increase attempt count twice");
        Task second = repo.currentTask(session.id); repo.submitAnswer(session.id, second, wrong);
        Task guided = repo.currentTask(session.id); repo.confirmGuidedCorrection(session.id, guided.id);
        check(repo.completeAndGrant(session.id) == 600000 && repo.snapshot().totalAnswered == 1
                        && repo.snapshot().firstCorrect == 0,
                "guided completion preserves the original first-attempt record");
    }

    private void previewScreen(String kind) throws Exception {
        final Activity activity = startActivitySync(new Intent(getTargetContext(), SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if ("study".equals(kind)) {
            cleanup();
            repo = new StudyRepository(context, StudyRepository.get(context).curriculum(), clock, dbName, prefsName);
            repo.setPin("2468"); final StudyRepository visualRepo = repo;
            runOnMainSync(() -> activity.setContentView(new StudyScreen(activity, visualRepo, () -> { }, () -> { }, false).view()));
        }
        // Bounded preview window lets adb capture the rendered TV surface while instrumentation is alive.
        probeStatus("PREVIEW_READY", "Isolated " + kind + " screen is ready for remote navigation and screenshot capture.");
        Thread.sleep(25000);
    }

    /**
     * An opt-in test-only bridge for adb verification of the actual service/overlay.
     * No UiAutomation is used: the shell owner enables and restores accessibility itself.
     * Never reads or writes the child's preferences or wallet.
     */
    private void gateProbe() throws Exception {
        final Field singleton = StudyRepository.class.getDeclaredField("singleton");
        final Field serviceRepository = LearningService.class.getDeclaredField("repository");
        final Field serviceGate = LearningService.class.getDeclaredField("gate");
        final Method shutdown = LearningService.class.getDeclaredMethod("shutdown");
        singleton.setAccessible(true); serviceRepository.setAccessible(true);
        serviceGate.setAccessible(true); shutdown.setAccessible(true);
        final AtomicBoolean finishRequested = new AtomicBoolean();
        final Object[] previousSingleton = new Object[1];
        final RuntimeException[] startupError = new RuntimeException[1];
        final String finishAction = "local.mytv.english.test.GATE_FINISH";
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (finishAction.equals(intent.getAction())) finishRequested.set(true);
            }
        };
        boolean registered = false, installed = false;
        try {
            runOnMainSync(() -> {
                if (LearningService.instance != null)
                    startupError[0] = new IllegalStateException("Disable this app's accessibility service before the isolated gate probe");
            });
            if (startupError[0] != null) throw startupError[0];
            cleanup();
            Curriculum realCourse;
            try (InputStreamReader reader = new InputStreamReader(context.getAssets().open("curriculum.tsv"), StandardCharsets.UTF_8)) {
                realCourse = Curriculum.load(reader);
            }
            repo = new StudyRepository(context, realCourse, new StudyRepository.Clock() {
                public String day() { return LocalDate.now().toString(); }
                public long now() { return System.currentTimeMillis(); }
            }, dbName, prefsName);
            repo.setPin("2468"); AppSettings settings = repo.settings(); settings.enabled = true; repo.saveSettings(settings);
            synchronized (StudyRepository.class) {
                previousSingleton[0] = singleton.get(null); singleton.set(null, repo); installed = true;
            }
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, new IntentFilter(finishAction), Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, new IntentFilter(finishAction));
            registered = true;
            probeStatus("READY_GATE", "Isolated repository ready. Enable service and open target playback. "
                    + "After capture remove this service, broadcast " + finishAction + ", then restore original accessibility settings after test completion.");
            long started = SystemClock.elapsedRealtime(), gateSeenAt = -1;
            boolean rewarded = false, reported = false;
            while (!finishRequested.get() && SystemClock.elapsedRealtime() - started < 75000) {
                final boolean[] visible = new boolean[1];
                runOnMainSync(() -> {
                    LearningService service = LearningService.instance;
                    try { visible[0] = service != null && serviceGate.get(service) != null && serviceRepository.get(service) == repo; }
                    catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                });
                long now = SystemClock.elapsedRealtime();
                if (visible[0] && gateSeenAt < 0) { gateSeenAt = now; probeStatus("GATE_VISIBLE", "Actual service gate is attached to the isolated repository."); }
                if (!rewarded && gateSeenAt >= 0 && now - gateSeenAt >= 20000) {
                    Session session = finishRound(); long grant = repo.completeAndGrant(session.id);
                    runOnMainSync(() -> {
                        LearningService service = LearningService.instance;
                        if (service != null) service.settingsChanged();
                    });
                    rewarded = true; probeStatus("GATE_REWARDED", "Granted isolated rewardMs=" + grant + "; observe overlay removal and resumed playback.");
                }
                if (!reported && now - started >= 40000) {
                    Snapshot state = repo.snapshot();
                    probeStatus("GATE_SNAPSHOT", "gateSeen=" + (gateSeenAt >= 0) + " rewarded=" + rewarded
                            + " balanceMs=" + state.balanceMs + " usedMs=" + state.todayUsedMs
                            + "; remove temporary service and send GATE_FINISH now.");
                    reported = true;
                }
                Thread.sleep(250);
            }
            Snapshot state = repo.snapshot();
            probeStatus("GATE_DONE", "balanceMs=" + state.balanceMs + " usedMs=" + state.todayUsedMs
                    + " rounds=" + state.todayRounds + " gateSeen=" + (gateSeenAt >= 0));
            if (gateSeenAt < 0 || !rewarded || state.todayUsedMs <= 0 || state.todayRounds != 1)
                throw new AssertionError("Gate probe requires real playback, a visible gate, one reward and observed viewing consumption");
        } finally {
            if (registered) context.unregisterReceiver(receiver);
            if (installed) {
                // Timeout safety: never close a DB while this service still has a polling callback.
                try {
                    runOnMainSync(() -> {
                        LearningService service = LearningService.instance;
                        try {
                            if (service != null && serviceRepository.get(service) == repo) {
                                shutdown.invoke(service); serviceRepository.set(service, null);
                            }
                        } catch (Exception e) { throw new IllegalStateException("Could not stop isolated gate probe", e); }
                    });
                } finally {
                    synchronized (StudyRepository.class) { singleton.set(null, previousSingleton[0]); }
                }
            }
            // onStart's finally closes and deletes only the isolated fixture files.
        }
    }

    private void probeStatus(String event, String description) {
        Bundle status = new Bundle(); status.putString("event", event);
        status.putString("stream", event + ": " + description + "\n"); sendStatus(1, status);
    }
}
