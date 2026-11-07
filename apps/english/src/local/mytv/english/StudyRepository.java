package local.mytv.english;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.json.JSONArray;
import org.json.JSONException;
import static local.mytv.english.StudyModels.*;

/** The only writer of study progress, rewards and viewing consumption. */
public final class StudyRepository {
    interface Clock { String day(); long now(); }
    private static StudyRepository singleton;
    private final Database helper;
    private final SharedPreferences prefs;
    private final Curriculum course;
    private final Clock clock;
    private final Random random = new SecureRandom();

    public static synchronized StudyRepository get(Context context) {
        if (singleton == null) {
            Context app = context.getApplicationContext();
            try (InputStreamReader reader = new InputStreamReader(app.getAssets().open("curriculum.tsv"), StandardCharsets.UTF_8)) {
                singleton = new StudyRepository(app, Curriculum.load(reader), new Clock() {
                    public String day() { return LocalDate.now().toString(); }
                    public long now() { return System.currentTimeMillis(); }
                }, "study.db", "study_settings");
            } catch (Exception error) {
                throw new IllegalStateException("离线课程读取失败，请重新安装完整安装包", error);
            }
        }
        return singleton;
    }

    // Package-visible dependency injection for the on-device transactional tests.
    StudyRepository(Context context, Curriculum course, Clock clock, String databaseName, String preferenceName) {
        this.course = course; this.clock = clock;
        prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        if (!prefs.contains("migrated")) {
            boolean oldEnabled = context.getSharedPreferences("learning", Context.MODE_PRIVATE).getBoolean("enabled", true);
            if (!prefs.edit().putBoolean("enabled", oldEnabled).putBoolean("migrated", true).commit())
                throw new IllegalStateException("配置保存失败");
        }
        helper = new Database(context, databaseName);
        helper.getWritableDatabase();
    }

    public Curriculum curriculum() { return course; }

    public synchronized AppSettings settings() {
        AppSettings s = new AppSettings();
        s.taskCount = prefs.getInt("taskCount", 5); s.rewardMinutes = prefs.getInt("rewardMinutes", 10);
        s.dailyLimitMinutes = prefs.getInt("dailyLimitMinutes", 60); s.dailyNewLimit = prefs.getInt("dailyNewLimit", 5);
        s.currentUnit = prefs.getInt("currentUnit", 0); s.volume = prefs.getInt("volume", 80);
        s.stage = prefs.getString("stage", "KET"); s.autoSpeak = prefs.getBoolean("autoSpeak", true);
        s.chineseHints = prefs.getBoolean("chineseHints", true); s.enabled = prefs.getBoolean("enabled", true);
        return s;
    }

    public synchronized void saveSettings(AppSettings settings) {
        AppSettings s = settings.copy(); s.validate();
        boolean saved = prefs.edit().putInt("taskCount", s.taskCount).putInt("rewardMinutes", s.rewardMinutes)
                .putInt("dailyLimitMinutes", s.dailyLimitMinutes).putInt("dailyNewLimit", s.dailyNewLimit)
                .putInt("currentUnit", s.currentUnit).putInt("volume", s.volume).putString("stage", s.stage)
                .putBoolean("autoSpeak", s.autoSpeak).putBoolean("chineseHints", s.chineseHints)
                .putBoolean("enabled", s.enabled).commit();
        if (!saved) throw new IllegalStateException("配置保存失败");
        snapshot(); // A lower daily limit clips the wallet immediately.
    }

    public synchronized Snapshot snapshot() {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Snapshot result = snapshot(db);
            db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }

    public synchronized Snapshot consumeViewing(long deltaMs) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Snapshot before = snapshot(db);
            long consumed = ViewingPolicy.consume(deltaMs, before.balanceMs, before.dailyRemainingMs);
            if (consumed > 0) {
                db.execSQL("UPDATE wallet SET balance=balance-? WHERE id=1", new Object[]{consumed});
                db.execSQL("UPDATE days SET used=used+? WHERE day=?", new Object[]{consumed, clock.day()});
            }
            Snapshot result = snapshot(db); db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }

    public synchronized Session ensureSession() {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Session existing = activeSession(db);
            if (existing != null) { db.setTransactionSuccessful(); return existing; }
            AppSettings s = settings(); Snapshot snap = snapshot(db);
            List<Curriculum.Item> pool = course.stage(s.stage);
            if (pool.size() < 3) throw new IllegalStateException("本阶段课程内容不足");
            List<Curriculum.Item> selection = selectItems(db, s, pool);
            String id = UUID.randomUUID().toString();
            ContentValues session = new ContentValues(); session.put("id", id); session.put("stage", s.stage);
            session.put("created_day", clock.day()); session.put("created_ms", clock.now());
            session.put("reward_ms", s.rewardMinutes * 60000L); session.put("task_count", s.taskCount);
            session.put("eligible", hasPin() && snap.balanceMs == 0 && snap.dailyRemainingMs > 0 ? 1 : 0);
            session.put("status", "ACTIVE"); session.put("granted_ms", 0);
            db.insertOrThrow("sessions", null, session);
            for (int i = 0; i < s.taskCount; i++) {
                Curriculum.Item item = selection.get(i % selection.size());
                SessionEngine.Question q = SessionEngine.question(item, pool, i, random);
                ContentValues task = new ContentValues(); task.put("id", id + ":" + i); task.put("session_id", id);
                task.put("position", i); task.put("item_id", item.id); task.put("type", q.type);
                task.put("prompt", q.prompt); task.put("options", encode(q.options)); task.put("correct", q.correct);
                task.put("explanation", q.explanation); task.put("first_answer", -1); task.put("first_correct", 0);
                task.put("correction_attempts", 0); task.put("corrected", 0);
                db.insertOrThrow("tasks", null, task);
            }
            Session result = session(db, id); db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }

    public synchronized Session currentSession() {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try { settleDay(db); Session s = activeSession(db); db.setTransactionSuccessful(); return s; }
        finally { db.endTransaction(); }
    }

    public synchronized Task currentTask(String sessionId) {
        return currentTask(helper.getReadableDatabase(), sessionId);
    }

    public synchronized void introduceItem(String sessionId, String taskId) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Task task = requireCurrent(db, sessionId, taskId);
            ensureProgress(db, course.get(task.itemId));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** A failed listening task becomes an explicit, persisted text exercise. */
    public synchronized void useTextFallback(String sessionId, String taskId) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            Task task = requireCurrent(db, sessionId, taskId);
            if ("LISTEN".equals(task.type)) {
                Curriculum.Item item = course.get(task.itemId);
                SessionEngine.Question q = SessionEngine.question(item, course.stage(item.stage), 0, random);
                ContentValues v = new ContentValues(); v.put("type", q.type); v.put("prompt", q.prompt);
                v.put("options", encode(q.options)); v.put("correct", q.correct); v.put("explanation", q.explanation);
                db.update("tasks", v, "id=?", new String[]{taskId});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized AnswerResult submitAnswer(String sessionId, String taskId, int optionIndex) {
        return submitAnswerInternal(sessionId, taskId, optionIndex, null);
    }

    /** UI callers submit the displayed phase/attempt snapshot, so stale clicks are harmless. */
    public synchronized AnswerResult submitAnswer(String sessionId, Task displayedTask, int optionIndex) {
        if (displayedTask == null) throw new IllegalArgumentException("缺少题目快照");
        return submitAnswerInternal(sessionId, displayedTask.id, optionIndex, displayedTask);
    }

    private AnswerResult submitAnswerInternal(String sessionId, String taskId, int optionIndex, Task displayedTask) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Task next = currentTask(db, sessionId);
            if (next == null || !next.id.equals(taskId)) {
                AnswerResult previous = previousAnswer(db, sessionId, taskId);
                db.setTransactionSuccessful(); return previous;
            }
            if (displayedTask != null && (!next.type.equals(displayedTask.type)
                    || displayedTask.correction != next.correction
                    || displayedTask.correctionAttempts != next.correctionAttempts)) {
                AnswerResult previous = previousAnswer(db, sessionId, taskId);
                db.setTransactionSuccessful(); return previous;
            }
            if (optionIndex < 0 || optionIndex >= next.options.length) throw new IllegalArgumentException("无效选项");
            if (next.needsIntro) throw new IllegalStateException("请先完成新词认识");
            boolean right = optionIndex == next.correct;
            ContentValues v = new ContentValues();
            if (!next.correction) {
                v.put("first_answer", optionIndex); v.put("first_correct", right ? 1 : 0);
                v.put("corrected", right ? 1 : 0);
                db.execSQL("UPDATE days SET total_answered=total_answered+1,first_correct=first_correct+? WHERE day=?",
                        new Object[]{right ? 1 : 0, clock.day()});
                updateReview(db, next.itemId, right);
            } else {
                v.put("correction_attempts", next.correctionAttempts + 1);
                if (right) v.put("corrected", 1);
            }
            db.update("tasks", v, "id=?", new String[]{taskId});
            updateReady(db, sessionId);
            AnswerResult result = answerResult(db, sessionId, right, !next.correction, !right);
            db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }

    public synchronized AnswerResult confirmGuidedCorrection(String sessionId, String taskId) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Task t = requireCurrent(db, sessionId, taskId);
            if (!t.correction || t.correctionAttempts < 2)
                throw new IllegalStateException("请先尝试订正，再完成引导练习");
            ContentValues v = new ContentValues(); v.put("corrected", 1);
            db.update("tasks", v, "id=?", new String[]{taskId}); updateReady(db, sessionId);
            AnswerResult r = answerResult(db, sessionId, true, false, false);
            r.message = "已完成引导订正，这个知识点会安排复习";
            db.setTransactionSuccessful(); return r;
        } finally { db.endTransaction(); }
    }

    /** Mark completion and award once, in the same SQLite transaction. */
    public synchronized long completeAndGrant(String sessionId) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            settleDay(db); Session s = session(db, sessionId);
            if (s == null) throw new IllegalArgumentException("学习轮次不存在");
            if ("COMPLETE".equals(s.status)) { db.setTransactionSuccessful(); return s.grantedMs; }
            if (scalar(db, "SELECT COUNT(*) FROM tasks WHERE session_id=?", sessionId) != s.taskCount)
                throw new IllegalStateException("学习任务不完整，请联系家长处理");
            if (currentTask(db, sessionId) != null) throw new IllegalStateException("请完成主任务和全部订正");
            Snapshot snap = snapshot(db);
            long granted = ViewingPolicy.grant(snap.balanceMs, s.rewardMs, snap.dailyRemainingMs,
                    s.rewardEligible && hasPin());
            ContentValues v = new ContentValues(); v.put("status", "COMPLETE");
            v.put("granted_ms", granted); db.update("sessions", v, "id=?", new String[]{sessionId});
            if (granted > 0) db.execSQL("UPDATE wallet SET balance=? WHERE id=1", new Object[]{granted});
            db.execSQL("UPDATE days SET rounds=rounds+1 WHERE day=?", new Object[]{clock.day()});
            db.setTransactionSuccessful(); return granted;
        } finally { db.endTransaction(); }
    }

    public synchronized List<ItemProgress> progress(String stage) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Map<String, ItemProgress> existing = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT item_id,stage,due_day,wrong_count,review_step,mastered,last_review_day FROM progress WHERE stage=?",
                new String[]{stage})) {
            while (c.moveToNext()) {
                ItemProgress p = new ItemProgress(); p.itemId = c.getString(0); p.stage = c.getString(1);
                p.dueDay = c.getString(2); p.wrongCount = c.getInt(3); p.reviewStep = c.getInt(4);
                p.mastered = c.getInt(5) != 0; p.learned = !c.getString(6).isEmpty(); existing.put(p.itemId, p);
            }
        }
        List<ItemProgress> result = new ArrayList<>();
        for (Curriculum.Item item : course.stage(stage)) {
            ItemProgress p = existing.get(item.id);
            if (p == null) { p = new ItemProgress(); p.itemId = item.id; p.stage = stage; p.dueDay = ""; }
            result.add(p);
        }
        return result;
    }

    public synchronized List<DayStats> history() {
        snapshot(); List<DayStats> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT day,used,rounds,first_correct,total_answered,new_count FROM days ORDER BY day DESC LIMIT 30", null)) {
            while (c.moveToNext()) {
                DayStats d = new DayStats(); d.day = c.getString(0); d.usedMs = c.getLong(1); d.rounds = c.getInt(2);
                d.firstCorrect = c.getInt(3); d.totalAnswered = c.getInt(4); d.newCount = c.getInt(5); result.add(d);
            }
        }
        return result;
    }

    public synchronized boolean hasPin() { return prefs.contains("pinHash") && prefs.contains("pinSalt"); }

    public synchronized void setPin(String pin) {
        if (pin == null || !pin.matches("[0-9]{4}")) throw new IllegalArgumentException("请设置四位数字 PIN");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        String hash = Base64.getEncoder().encodeToString(hashPin(pin, salt));
        if (!prefs.edit().putString("pinSalt", Base64.getEncoder().encodeToString(salt)).putString("pinHash", hash)
                .putInt("pinFailures", 0).putLong("pinLockUntil", 0).commit()) throw new IllegalStateException("PIN 保存失败");
    }

    public synchronized boolean verifyPin(String pin) {
        if (!hasPin() || pin == null || !pin.matches("[0-9]{4}") || clock.now() < pinLockUntil()) return false;
        byte[] expected = Base64.getDecoder().decode(prefs.getString("pinHash", ""));
        byte[] salt = Base64.getDecoder().decode(prefs.getString("pinSalt", ""));
        boolean right = MessageDigest.isEqual(expected, hashPin(pin, salt));
        int failures = right ? 0 : prefs.getInt("pinFailures", 0) + 1;
        long until = failures >= 5 ? clock.now() + 30000L : 0;
        prefs.edit().putInt("pinFailures", failures >= 5 ? 0 : failures).putLong("pinLockUntil", until).commit();
        return right;
    }

    public synchronized long pinLockUntil() { return prefs.getLong("pinLockUntil", 0); }

    public synchronized void restoreDefaults() { saveSettings(new AppSettings()); }

    /** This deliberately preserves the parent's PIN and preferences. UI requires authentication. */
    public synchronized void resetLearning() {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("tasks", null, null); db.delete("sessions", null, null); db.delete("progress", null, null);
            db.delete("days", null, null); db.execSQL("UPDATE wallet SET day='',balance=0 WHERE id=1");
            settleDay(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    synchronized void closeForTests() { helper.close(); }

    private Snapshot snapshot(SQLiteDatabase db) {
        Snapshot s = new Snapshot(); s.day = clock.day();
        try (Cursor c = db.rawQuery("SELECT balance FROM wallet WHERE id=1", null)) { c.moveToFirst(); s.balanceMs = c.getLong(0); }
        try (Cursor c = db.rawQuery("SELECT used,rounds,first_correct,total_answered FROM days WHERE day=?", new String[]{s.day})) {
            c.moveToFirst(); s.todayUsedMs = c.getLong(0); s.todayRounds = c.getInt(1);
            s.firstCorrect = c.getInt(2); s.totalAnswered = c.getInt(3);
        }
        s.dailyRemainingMs = ViewingPolicy.dailyRemaining(settings().dailyLimitMinutes * 60000L, s.todayUsedMs);
        long clamped = Math.min(s.balanceMs, s.dailyRemainingMs);
        if (clamped != s.balanceMs) { db.execSQL("UPDATE wallet SET balance=? WHERE id=1", new Object[]{clamped}); s.balanceMs = clamped; }
        String stage = settings().stage;
        s.learnedCount = (int) scalar(db, "SELECT COUNT(*) FROM progress WHERE stage=? AND last_review_day!=''", stage);
        s.dueCount = (int) scalar(db, "SELECT COUNT(*) FROM progress WHERE stage=? AND last_review_day!='' AND mastered=0 AND due_day<=?", stage, s.day);
        s.masteredCount = (int) scalar(db, "SELECT COUNT(*) FROM progress WHERE stage=? AND mastered=1", stage);
        Session active = activeSession(db); s.activeSessionId = active == null ? null : active.id;
        return s;
    }

    private void settleDay(SQLiteDatabase db) {
        String today = clock.day();
        try (Cursor c = db.rawQuery("SELECT day FROM wallet WHERE id=1", null)) {
            c.moveToFirst();
            if (!today.equals(c.getString(0))) db.execSQL("UPDATE wallet SET day=?,balance=0 WHERE id=1", new Object[]{today});
        }
        ContentValues day = new ContentValues(); day.put("day", today);
        db.insertWithOnConflict("days", null, day, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private List<Curriculum.Item> selectItems(SQLiteDatabase db, AppSettings s, List<Curriculum.Item> pool) {
        Map<String, ItemProgress> known = new LinkedHashMap<>();
        for (ItemProgress p : progress(s.stage)) if (p.learned) known.put(p.itemId, p);
        List<Curriculum.Item> chosen = new ArrayList<>(); Set<String> ids = new HashSet<>();
        List<ItemProgress> due = new ArrayList<>();
        for (ItemProgress p : known.values()) if (!p.mastered && p.dueDay.compareTo(clock.day()) <= 0) due.add(p);
        Collections.sort(due, new Comparator<ItemProgress>() {
            public int compare(ItemProgress a, ItemProgress b) {
                int d = a.dueDay.compareTo(b.dueDay); return d != 0 ? d : Integer.compare(b.wrongCount, a.wrongCount);
            }
        });
        for (ItemProgress p : due) {
            if (chosen.size() >= s.taskCount) break;
            chosen.add(course.get(p.itemId)); ids.add(p.itemId);
        }
        int newCount = (int) scalar(db, "SELECT new_count FROM days WHERE day=?", clock.day());
        int newSlots = Math.max(0, Math.min(2, s.dailyNewLimit - newCount));
        int start = (int) Math.min(s.currentUnit * 20L, Math.max(0, pool.size() - 1L));
        for (int offset = 0; offset < pool.size() && newSlots > 0 && chosen.size() < s.taskCount; offset++) {
            Curriculum.Item item = pool.get((start + offset) % pool.size());
            if (!known.containsKey(item.id) && ids.add(item.id)) { chosen.add(item); newSlots--; }
        }
        // Fill with previously learned material, then reuse selected new items in other question types.
        List<Curriculum.Item> review = new ArrayList<>();
        for (Curriculum.Item i : pool) if (known.containsKey(i.id)) review.add(i);
        Collections.shuffle(review, random);
        for (Curriculum.Item item : review) {
            if (chosen.size() >= s.taskCount) break;
            if (ids.add(item.id)) chosen.add(item);
        }
        if (chosen.isEmpty()) throw new IllegalStateException("暂无可用学习项，请检查每日新词配置");
        return chosen;
    }

    private Session activeSession(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT id FROM sessions WHERE status!='COMPLETE' ORDER BY created_ms,id LIMIT 1", null)) {
            return c.moveToFirst() ? session(db, c.getString(0)) : null;
        }
    }

    private Session session(SQLiteDatabase db, String id) {
        try (Cursor c = db.rawQuery("SELECT id,stage,status,reward_ms,task_count,eligible,granted_ms FROM sessions WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) return null;
            Session s = new Session(); s.id = c.getString(0); s.stage = c.getString(1); s.status = c.getString(2);
            s.rewardMs = c.getLong(3); s.taskCount = c.getInt(4); s.grantedMs = c.getLong(6);
            long balance = scalar(db, "SELECT balance FROM wallet WHERE id=1");
            long used = scalar(db, "SELECT used FROM days WHERE day=?", clock.day());
            s.rewardEligible = hasPin() && balance == 0
                    && ViewingPolicy.dailyRemaining(settings().dailyLimitMinutes * 60000L, used) > 0;
            s.completedTasks = (int) scalar(db, "SELECT COUNT(*) FROM tasks WHERE session_id=? AND first_answer>=0", id);
            s.correctionCount = (int) scalar(db, "SELECT COUNT(*) FROM tasks WHERE session_id=? AND first_answer>=0 AND first_correct=0 AND corrected=0", id);
            return s;
        }
    }

    private Task currentTask(SQLiteDatabase db, String id) {
        if (id == null) return null;
        try (Cursor c = db.rawQuery("SELECT t.id,t.item_id,t.type,t.prompt,t.options,t.correct,t.explanation,t.first_answer,t.correction_attempts "
                + "FROM tasks t JOIN sessions s ON s.id=t.session_id WHERE t.session_id=? AND s.status!='COMPLETE' "
                + "AND (t.first_answer<0 OR (t.first_correct=0 AND t.corrected=0)) ORDER BY CASE WHEN t.first_answer<0 THEN 0 ELSE 1 END,t.position LIMIT 1", new String[]{id})) {
            if (!c.moveToFirst()) return null;
            Task t = new Task(); t.id = c.getString(0); t.itemId = c.getString(1); t.type = c.getString(2);
            t.prompt = c.getString(3); t.options = decode(c.getString(4)); t.correct = c.getInt(5);
            t.explanation = c.getString(6); t.correction = c.getInt(7) >= 0; t.correctionAttempts = c.getInt(8);
            t.introduced = scalar(db, "SELECT COUNT(*) FROM progress WHERE item_id=?", t.itemId) > 0;
            t.needsIntro = !t.introduced; return t;
        }
    }

    private Task requireCurrent(SQLiteDatabase db, String sessionId, String taskId) {
        Task current = currentTask(db, sessionId);
        if (current == null || !current.id.equals(taskId)) throw new IllegalStateException("题目已更新，请继续当前任务");
        return current;
    }

    private void ensureProgress(SQLiteDatabase db, Curriculum.Item item) {
        if (item == null) throw new IllegalStateException("课程条目丢失");
        if (scalar(db, "SELECT COUNT(*) FROM progress WHERE item_id=?", item.id) > 0) return;
        ContentValues p = new ContentValues(); p.put("item_id", item.id); p.put("stage", item.stage);
        p.put("introduced_day", clock.day()); p.put("due_day", SessionEngine.dueDate(clock.day(), 0));
        p.put("last_review_day", ""); p.put("review_step", 0); p.put("mastered", 0); p.put("wrong_count", 0);
        db.insertOrThrow("progress", null, p);
        db.execSQL("UPDATE days SET new_count=new_count+1 WHERE day=?", new Object[]{clock.day()});
    }

    private void updateReview(SQLiteDatabase db, String itemId, boolean independentlyCorrect) {
        try (Cursor c = db.rawQuery("SELECT introduced_day,due_day,last_review_day,review_step,wrong_count FROM progress WHERE item_id=?", new String[]{itemId})) {
            if (!c.moveToFirst()) throw new IllegalStateException("请先认识新词");
            String today = clock.day(); int step = c.getInt(3);
            ContentValues p = new ContentValues();
            if (!independentlyCorrect) {
                p.put("wrong_count", c.getInt(4) + 1); p.put("review_step", 0); p.put("mastered", 0);
                p.put("due_day", SessionEngine.dueDate(today, 0)); p.put("last_review_day", today);
            } else if (!today.equals(c.getString(2))) {
                boolean due = step < SessionEngine.REVIEW_DAYS.length
                        && c.getString(1).compareTo(today) <= 0 && !today.equals(c.getString(0));
                int next = SessionEngine.nextReviewStep(step, true, due); p.put("review_step", next);
                if (due) {
                    p.put("mastered", next >= SessionEngine.REVIEW_DAYS.length ? 1 : 0);
                    p.put("due_day", next >= SessionEngine.REVIEW_DAYS.length ? "" : SessionEngine.dueDate(today, next));
                }
                p.put("last_review_day", today);
            }
            if (p.size() > 0) db.update("progress", p, "item_id=?", new String[]{itemId});
        }
    }

    private void updateReady(SQLiteDatabase db, String id) {
        if (currentTask(db, id) == null) db.execSQL("UPDATE sessions SET status='READY' WHERE id=? AND status='ACTIVE'", new Object[]{id});
    }

    private AnswerResult answerResult(SQLiteDatabase db, String id, boolean right, boolean first, boolean correction) {
        AnswerResult r = new AnswerResult(); r.correct = right; r.firstAttempt = first; r.needsCorrection = correction;
        r.sessionComplete = currentTask(db, id) == null;
        r.message = right ? "答对了，继续前进！" : (first ? "先看讲解，轮末一起订正。" : "再看看例句，你可以的。");
        return r;
    }

    private AnswerResult previousAnswer(SQLiteDatabase db, String sessionId, String taskId) {
        try (Cursor c = db.rawQuery("SELECT first_correct,corrected FROM tasks WHERE id=? AND session_id=? AND first_answer>=0", new String[]{taskId, sessionId})) {
            if (!c.moveToFirst()) throw new IllegalStateException("题目已更新，请继续当前任务");
            boolean completed = c.getInt(0) != 0 || c.getInt(1) != 0;
            return answerResult(db, sessionId, completed, false, !completed);
        }
    }

    private static long scalar(SQLiteDatabase db, String sql, String... args) {
        try (Cursor c = db.rawQuery(sql, args)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    private static String encode(String[] values) {
        JSONArray a = new JSONArray(); for (String s : values) a.put(s); return a.toString();
    }

    private static String[] decode(String encoded) {
        try {
            JSONArray a = new JSONArray(encoded); String[] values = new String[a.length()];
            for (int i = 0; i < values.length; i++) values[i] = a.getString(i); return values;
        } catch (JSONException e) { throw new IllegalStateException("学习任务已损坏", e); }
    }

    private static byte[] hashPin(String pin, byte[] salt) {
        PBEKeySpec key = new PBEKeySpec(pin.toCharArray(), salt, 50000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("PIN 加密不可用", e); }
        finally { key.clearPassword(); }
    }

    private static final class Database extends SQLiteOpenHelper {
        Database(Context c, String name) { super(c, name, null, 1); }
        @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE wallet(id INTEGER PRIMARY KEY CHECK(id=1),day TEXT NOT NULL,balance INTEGER NOT NULL CHECK(balance>=0))");
            db.execSQL("INSERT INTO wallet VALUES(1,'',0)");
            db.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY,used INTEGER NOT NULL DEFAULT 0,rounds INTEGER NOT NULL DEFAULT 0,first_correct INTEGER NOT NULL DEFAULT 0,total_answered INTEGER NOT NULL DEFAULT 0,new_count INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,stage TEXT NOT NULL,created_day TEXT NOT NULL,created_ms INTEGER NOT NULL,reward_ms INTEGER NOT NULL,task_count INTEGER NOT NULL,eligible INTEGER NOT NULL,status TEXT NOT NULL,granted_ms INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,position INTEGER NOT NULL,item_id TEXT NOT NULL,type TEXT NOT NULL,prompt TEXT NOT NULL,options TEXT NOT NULL,correct INTEGER NOT NULL,explanation TEXT NOT NULL,first_answer INTEGER NOT NULL DEFAULT -1,first_correct INTEGER NOT NULL DEFAULT 0,correction_attempts INTEGER NOT NULL DEFAULT 0,corrected INTEGER NOT NULL DEFAULT 0,UNIQUE(session_id,position))");
            db.execSQL("CREATE INDEX task_session ON tasks(session_id,position)");
            db.execSQL("CREATE TABLE progress(item_id TEXT PRIMARY KEY,stage TEXT NOT NULL,introduced_day TEXT NOT NULL,due_day TEXT NOT NULL,last_review_day TEXT NOT NULL,review_step INTEGER NOT NULL,mastered INTEGER NOT NULL DEFAULT 0,wrong_count INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE INDEX progress_due ON progress(stage,due_day)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new IllegalStateException("不支持的学习数据库版本");
        }
    }
}
