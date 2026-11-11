package scfido.learnthenwatch.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import scfido.learnthenwatch.tv.ScreenHistory.Screen;
import static scfido.learnthenwatch.tv.LearningState.*;

/** Real D-pad/confirm/back events, isolated from the child's preferences and database. */
final class RemoteNavigationChecks {
    private final Instrumentation instrument;
    private final Context context;
    private MainActivity activity;
    private LearningRepository repository;
    private Object originalSingleton;
    private Field singleton;
    private int assertions;
    private String reportedPage = "";
    private final String database = "instrumentation-remote.db", preferences = "instrumentation-remote-settings";
    private RemoteNavigationChecks(Instrumentation i, Context c) { instrument = i; context = c; }
    static int run(Instrumentation i, Context c) throws Exception {
        RemoteNavigationChecks checks = new RemoteNavigationChecks(i, c);
        try { checks.run(); return checks.assertions; } finally { checks.close(); }
    }
    private void run() throws Exception {
        if (PlaybackAccessService.instance != null) throw new IllegalStateException("Temporarily disable this app's service before isolated remote tests");
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
        VocabularyCatalog full;
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open("curriculum.tsv"), "UTF-8")) { full = VocabularyCatalog.load(reader); }
        List<VocabularyCatalog.Item> items = new ArrayList<>(); items.addAll(full.stage("KET").subList(0, 65)); items.addAll(full.stage("PET").subList(0, 25));
        repository = new LearningRepository(context, new VocabularyCatalog(items), new LearningRepository.Clock() {
            public String day() { return "2031-01-01"; } public long now() { return 1924992000000L; }
        }, database, preferences);
        repository.setPin("2468"); ParentSettings settings = repository.settings(); settings.autoSpeak = false; settings.taskCount = 3; repository.saveSettings(settings);
        singleton = LearningRepository.class.getDeclaredField("singleton"); singleton.setAccessible(true);
        originalSingleton = singleton.get(null); singleton.set(null, repository);
        activity = (MainActivity) instrument.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle(250); checkPage("home"); checkFocus("home:learn"); noReturnButtons(); save("home");

        menu("map"); checkPage("map"); checkFocus("map:unit:0");
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("map:unit:60");
        save("map");
        View card = focus(); check(card instanceof ViewGroup && countFocusableChildren((ViewGroup) card) == 0, "entire unit tile is one focus target");
        key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("unit"); check(route().index == 60, "confirm enters selected unit directly");
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN);
        String wordFocus = focusTag(); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("word"); noReturnButtons();
        long played = audioGeneration(); key(KeyEvent.KEYCODE_DPAD_LEFT);
        check(audioGeneration() > played, "left key replays word card audio");
        played = audioGeneration(); key(KeyEvent.KEYCODE_DPAD_CENTER); check(audioGeneration() > played, "word card confirm also replays audio");
        save("word");
        key(KeyEvent.KEYCODE_BACK); checkPage("unit"); checkFocus(wordFocus);
        key(KeyEvent.KEYCODE_BACK); checkPage("map"); checkFocus("map:unit:60");
        check(route().scroll > 0, "map return retains a scrolled destination");
        key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP); settle(300);
        checkFocus("map:unit:0");
        check(((android.widget.ScrollView) field(activity, "scroll")).getScrollY() == 0, "first card restores entire page header");
        key(KeyEvent.KEYCODE_BACK); checkPage("home");

        // A normal answer selection keeps its focus when left is used to replay.
        key(KeyEvent.KEYCODE_DPAD_RIGHT); checkFocus("home:learn"); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("study");
        key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("study:option:1");
        String taskId = repository.currentTask(repository.currentSession().id).id; int before = repository.snapshot().totalAnswered;
        played = audioGeneration(); key(KeyEvent.KEYCODE_DPAD_LEFT);
        check(audioGeneration() > played, "left key replays directly from an answer"); checkFocus("study:option:1");
        check(repository.snapshot().totalAnswered == before && repository.currentTask(repository.currentSession().id).id.equals(taskId), "replay never changes answer or task");
        key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("study:option:2"); key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("study:unknown");
        key(KeyEvent.KEYCODE_DPAD_UP); checkFocus("study:option:2");
        Task mainTask = repository.currentTask(repository.currentSession().id);
        for (int n = 2; n > mainTask.correct; n--) key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        long replayDeadline = SystemClock.uptimeMillis() + 8000;
        while (((Task) field(field(activity, "study"), "active")).id.equals(mainTask.id)
                && SystemClock.uptimeMillis() < replayDeadline) settle(50);
        Task listening = repository.currentTask(repository.currentSession().id);
        check("LISTEN".equals(listening.type), "correct answer automatically reaches listening");
        for (int n = 0; n < 24 && !enabled("study:option:0"); n++) settle(100);
        if (enabled("study:option:0")) {
            key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("study:option:1");
            ParentSettings silent = repository.settings(); int savedVolume = silent.volume; silent.volume = 0; repository.saveSettings(silent);
            key(KeyEvent.KEYCODE_DPAD_LEFT); settle(180); checkFocus("study:option:1");
            check(repository.snapshot().totalAnswered == before + 1, "failed replay after listening never submits an answer or steals focus");
            silent.volume = savedVolume; repository.saveSettings(silent);
            int unknownBefore = repository.snapshot().totalAnswered;
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(repository.snapshot().totalAnswered == unknownBefore + 1, "right without confirming records unknown");
            check(repository.snapshot().firstCorrect == before + 1, "unknown does not count as an independent correct answer");
            check(enabled("study:advance"), "unknown displays explanation before next task");
            key(KeyEvent.KEYCODE_DPAD_RIGHT); settle(300);
        }
        key(KeyEvent.KEYCODE_BACK); checkPage("home"); checkFocus("home:learn");

        seedLearning(); menu("records"); checkFocus("records:review"); save("records"); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("review");
        String reviewFocus = focusTag(); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("word");
        key(KeyEvent.KEYCODE_BACK); checkPage("review"); checkFocus(reviewFocus);
        key(KeyEvent.KEYCODE_BACK); checkPage("records"); checkFocus("records:review");
        key(KeyEvent.KEYCODE_BACK); checkPage("home");

        menu("parent"); checkPage("pin"); noReturnButtons(); key(KeyEvent.KEYCODE_BACK); checkPage("home"); checkFocus("nav:parent");
        key(KeyEvent.KEYCODE_DPAD_CENTER); unlock(); checkPage("parent"); checkFocus("parent:enabled");
        int tasksBefore = repository.settings().taskCount;
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER);
        checkPage("parent"); checkFocus("parent:tasks");
        check(repository.settings().taskCount != tasksBefore, "confirm changes the whole setting row and preserves focus");
        save("parent");
        seek("parent:unit", KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("unitpicker");
        key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("unitpicker:1"); key(KeyEvent.KEYCODE_DPAD_CENTER);
        checkPage("parent"); checkFocus("parent:unit"); check(repository.settings().currentUnit == 1, "unit selection returns to setting and applies chosen unit");
        key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:unit");

        seek("parent:monitor", KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("monitor");
        checkFocus("monitor:system"); noReturnButtons();
        PlaybackAccessStatus monitor = PlaybackAccessService.monitorStatus(context, repository);
        check(((TextView) activity.getWindow().getDecorView().findViewWithTag("monitor:status")).getText().toString().equals(monitor.message), "monitor page uses shared live status");
        save("monitor"); key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:monitor");

        seek("parent:sources", KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("sources");
        String sourceFocus = focusTag(); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("license"); noReturnButtons();
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_BACK); checkPage("sources"); checkFocus(sourceFocus);
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:sources");
        seek("parent:demo", KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("study");
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:demo");
        key(KeyEvent.KEYCODE_DPAD_DOWN); checkFocus("parent:records"); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("records");
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:records");

        seek("parent:reset", KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("confirm");
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:reset");
        int learnedBefore = repository.snapshot().learnedCount;
        key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("pin");
        key(KeyEvent.KEYCODE_BACK); checkPage("confirm"); checkFocus("confirm:yes");
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:reset");
        check(repository.snapshot().learnedCount == learnedBefore, "back from destructive confirmation never clears records");
        seek("parent:pin", KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("pin");
        key(KeyEvent.KEYCODE_BACK); checkPage("parent"); checkFocus("parent:pin");
        key(KeyEvent.KEYCODE_BACK); checkPage("home"); menu("parent"); checkPage("pin"); unlock();

        // Actual background/foreground transition must reauthenticate protected pages, not expose stale controls.
        key(KeyEvent.KEYCODE_HOME); settle(250);
        main(() -> activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        settle(400); checkPage("pin");
        key(KeyEvent.KEYCODE_BACK); checkPage("home");
        menu("parent"); unlock(); seek("parent:monitor", KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("monitor");
        key(KeyEvent.KEYCODE_HOME); settle(250);
        main(() -> activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        settle(400); checkPage("pin"); key(KeyEvent.KEYCODE_BACK); checkPage("home");
        menu("parent"); unlock(); seek("parent:records", KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_CENTER); checkPage("records");
        key(KeyEvent.KEYCODE_HOME); settle(250);
        main(() -> activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        settle(400); checkPage("records");
        key(KeyEvent.KEYCODE_BACK); checkPage("pin"); // The actual parent destination is protected after backgrounding.
        key(KeyEvent.KEYCODE_BACK); checkPage("home");
        key(KeyEvent.KEYCODE_BACK); check(activity.isFinishing(), "back from the root exits without a navigation loop");
    }
    private void seedLearning() {
        Session session = repository.ensureSession(); Task task; int guard = 0;
        while ((task = repository.currentTask(session.id)) != null) {
            if (++guard > 30) throw new AssertionError("fixture session stuck");
            if (task.needsIntro) repository.introduceItem(session.id, task.id);
            repository.submitAnswer(session.id, task.id, task.correct);
        }
        repository.completeAndGrant(session.id);
    }
    private void menu(String name) {
        if (!focusTag().startsWith("nav:")) key(KeyEvent.KEYCODE_DPAD_LEFT);
        for (int n = 0; n < 4; n++) key(KeyEvent.KEYCODE_DPAD_UP);
        int count = new String[]{"home", "map", "records", "parent"}.length;
        for (int n = 0; n < count && !focusTag().equals("nav:" + name); n++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        checkFocus("nav:" + name); key(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    private void unlock() {
        checkPage("pin"); checkFocus("pin:1");
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); // 2
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER); // 4
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); // 6
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER); // 8
        checkFocus("pin:确认"); key(KeyEvent.KEYCODE_DPAD_CENTER);
    }
    private void seek(String tag, int direction) {
        for (int n = 0; n < 40 && !focusTag().equals(tag); n++) key(direction);
        checkFocus(tag);
    }
    private void key(int key) {
        if (!reportedPage.equals(route().name)) {
            reportedPage = route().name; android.os.Bundle status = new android.os.Bundle();
            status.putString("stream", "REMOTE verifying " + reportedPage + "\n"); instrument.sendStatus(1, status);
        }
        instrument.sendKeyDownUpSync(key); settle(100);
    }
    private void settle(int ms) { SystemClock.sleep(ms); main(() -> { }); }
    private View focus() { final View[] result = new View[1]; main(() -> result[0] = activity.getCurrentFocus()); return result[0]; }
    private String focusTag() { View view = focus(); return view != null && view.getTag() != null ? view.getTag().toString() : ""; }
    private boolean enabled(String tag) {
        final boolean[] result = new boolean[1];
        main(() -> { View view = activity.getWindow().getDecorView().findViewWithTag(tag); result[0] = view != null && view.isEnabled(); });
        return result[0];
    }
    private Screen route() { return ((ScreenHistory) field(activity, "navigation")).current(); }
    private void checkPage(String name) { check(route().name.equals(name), "expected page " + name + ", got " + route().name); }
    private void checkFocus(String tag) { check(focusTag().equals(tag), "expected focus " + tag + ", got " + focusTag()); }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private long audioGeneration() {
        Object screen = field(activity, "study"); Object audio = field(screen, "audio"); return (Long) field(audio, "generation");
    }
    private int countFocusableChildren(ViewGroup group) {
        int count = 0;
        for (int n = 0; n < group.getChildCount(); n++) {
            View child = group.getChildAt(n); if (child.isFocusable()) count++;
            if (child instanceof ViewGroup) count += countFocusableChildren((ViewGroup) child);
        }
        return count;
    }
    private void noReturnButtons() { main(() -> noReturnButtons(activity.getWindow().getDecorView())); }
    private void noReturnButtons(View view) {
        if (view.isFocusable() && view instanceof TextView) check(!((TextView) view).getText().toString().contains("返回"), "no soft return control");
        if (view instanceof ViewGroup) for (int n = 0; n < ((ViewGroup) view).getChildCount(); n++) noReturnButtons(((ViewGroup) view).getChildAt(n));
    }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private void save(String name) {
        main(() -> {
            View root = activity.getWindow().getDecorView(); Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(image));
            try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), "qa-remote-" + name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
            catch (Exception e) { throw new IllegalStateException(e); }
            finally { image.recycle(); }
        });
    }
    private void main(Runnable action) {
        final Throwable[] error = new Throwable[1]; instrument.runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } });
        if (error[0] != null) throw new AssertionError("Remote UI check failed", error[0]);
    }
    private void close() throws Exception {
        if (activity != null) { main(() -> activity.finish()); SystemClock.sleep(200); }
        if (singleton != null) singleton.set(null, originalSingleton);
        if (repository != null) repository.closeForTests();
        context.deleteDatabase(database); context.getSharedPreferences(preferences, 0).edit().clear().commit();
    }
}
