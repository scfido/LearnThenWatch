package dev.learnthenwatch.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A bounded TV canvas: confirm to answer, left to replay, right to continue, no scrolling. */
public final class LessonScreen {
    private final Context context;
    private final LearningRepository repository;
    private final Runnable onFinished, onExit;
    private final boolean demo;
    private final VocabularyCatalog.Item review;
    private final FrameLayout root;
    private final PronunciationPlayer audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> answers = new ArrayList<>();
    private LearningState.Session session;
    private LearningState.Task active;
    private LinearLayout page, body, left, choices, footer;
    private TextView status;
    private Button secondary, replayButton;
    private boolean autoAdvance;
    private final Runnable autoNext = this::transitionNext;
    private Runnable replayAction, nextAction;
    private boolean transitioning;
    private LearningMascotView companion;
    private boolean closed, paused, submitting, heard, audioFailed;
    private long generation, keyGeneration = -1;
    private int demoIndex;

    public LessonScreen(Context context, LearningRepository repository, Runnable onFinished, Runnable onExit, boolean demo) {
        this(context, repository, onFinished, onExit, demo, null);
    }
    LessonScreen(Context context, LearningRepository repository, Runnable onFinished, Runnable onExit, boolean demo, VocabularyCatalog.Item review) {
        this.context = context; this.repository = repository; this.onFinished = onFinished; this.onExit = onExit; this.demo = demo;
        this.review = review;
        root = new FrameLayout(context) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                int key = event.getKeyCode();
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT && review == null && active != null) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !transitioning) {
                        if (nextAction != null) nextAction.run();
                        else if (!submitting) answer(active, (active.correct + 1) % active.options.length, true);
                    }
                    return true;
                }
                if (key == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) { pauseAudio(); onExit.run(); }
                    return true;
                }
                if (transitioning) return true;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && replayAction != null) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        replayAction.run();
                    }
                    return true;
                }
                if (review != null && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
                    if (event.getAction() == KeyEvent.ACTION_UP && replayAction != null) replayAction.run();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() > 0) return true;
                        keyGeneration = generation;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && keyGeneration != generation) return true;
                }
                return super.dispatchKeyEvent(event);
            }
            @Override protected void onDetachedFromWindow() { pauseAudio(); super.onDetachedFromWindow(); }
        };
        root.setTag("study:root"); root.setBackgroundColor(TvUiTheme.CREAM);
        root.setFocusable(true); root.setFocusableInTouchMode(true);
        root.setClipChildren(false); root.setClipToPadding(false);
        audio = new PronunciationPlayer(context); render();
    }
    public View view() { return root; }
    public void pauseAudio() { paused = true; generation++; handler.removeCallbacksAndMessages(null); audio.stop(); }
    public void resume() { paused = false; render(); }
    public void close() { closed = true; pauseAudio(); }

    private int dp(float value) { return TvUiTheme.dp(context, value); }
    private TextView fit(CharSequence value, int max, int min) {
        TextView text = TvUiTheme.text(context, "", max, TvUiTheme.INK);
        text.setText(value); text.setGravity(Gravity.CENTER_VERTICAL);
        text.setAutoSizeTextTypeUniformWithConfiguration(min, max, 1, TypedValue.COMPLEX_UNIT_SP);
        text.setLineSpacing(0, 1.06f); return text;
    }
    private void fixedText(LinearLayout parent, CharSequence text, int max, int min, int height) {
        parent.addView(fit(text, max, min), new LinearLayout.LayoutParams(-1, dp(height)));
    }
    private void reset(String label, int done, int total) {
        generation++; handler.removeCallbacksAndMessages(null); audio.stop();
        replayAction = null; nextAction = null; transitioning = false; autoAdvance = false; secondary = null; replayButton = null;
        root.removeAllViews(); answers.clear(); active = null; submitting = false; heard = false; audioFailed = false;
        page = TvUiTheme.column(context); page.setPadding(dp(30), dp(18), dp(30), dp(18));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = TvUiTheme.row(context);
        TextView stage = fit(label, 18, 15); stage.setTextColor(TvUiTheme.TEAL);
        LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(dp(124), -1);
        header.addView(stage, badge);
        View trail = new Trail(context, done, Math.max(1, total)); trail.setTag("study:progress");
        trail.setContentDescription("已完成 " + done + " / " + total);
        header.addView(trail, new LinearLayout.LayoutParams(0, -1, 1));
        companion = new LearningMascotView(context); header.addView(companion, new LinearLayout.LayoutParams(dp(64), -1));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        body = TvUiTheme.row(context); body.setBaselineAligned(false); body.setGravity(Gravity.TOP);
        body.setTag("study:body");
        LinearLayout.LayoutParams main = new LinearLayout.LayoutParams(-1, 0, 1); main.topMargin = dp(14);
        page.addView(body, main);
        left = TvUiTheme.column(context); left.setPadding(dp(22), dp(14), dp(22), dp(14));
        left.setBackground(TvUiTheme.shape(context, TvUiTheme.WHITE, TvUiTheme.LINE, 1, 24));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.2f));
        choices = TvUiTheme.column(context);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -1, 1); right.leftMargin = dp(22);
        body.addView(choices, right);
        footer = TvUiTheme.row(context);
        LinearLayout.LayoutParams bottom = new LinearLayout.LayoutParams(-1, dp(54)); bottom.topMargin = dp(12);
        page.addView(footer, bottom);
        status = fit("", 18, 15); status.setTag("study:audio-status"); status.setTextColor(TvUiTheme.MUTED);
        status.setVisibility(View.INVISIBLE);
        footer.addView(status, new LinearLayout.LayoutParams(0, -1, 1));
        TvUiTheme.enter(body);
    }
    private void render() {
        if (closed || paused) return;
        try {
            if (review != null) { showReview(); return; }
            if (demo) { renderDemo(); return; }
            if (session == null) session = repository.ensureSession();
            LearningState.Task task = repository.currentTask(session.id);
            if (task == null) { finishRound(); return; }
            // Presentation registers the new item; no separate introduction/confirmation page.
            if (task.needsIntro) {
                repository.introduceItem(session.id, task.id);
                task = repository.currentTask(session.id);
            }
            session = repository.currentSession();
            if (session == null || task == null) throw new IllegalStateException("任务已更新，请重试");
            showQuestion(task);
        } catch (RuntimeException error) { failure(error); }
    }
    private VocabularyCatalog.Item item(LearningState.Task task) {
        VocabularyCatalog.Item value = repository.curriculum().get(task.itemId);
        if (value == null) throw new IllegalStateException("课程内容缺失，请家长检查");
        return value;
    }
    private void showQuestion(LearningState.Task task) {
        String label = demo ? "体验" : (task.correction ? "错题再练" : session.stage);
        reset(label, demo ? demoIndex : session.completedTasks, demo ? 3 : session.taskCount);
        active = task; VocabularyCatalog.Item value = item(task);
        if (task.correction && task.correctionAttempts >= 2) { showGuided(task); return; }
        boolean listening = "LISTEN".equals(task.type);
        if (listening) {
            fixedText(left, "听一听", 32, 26, 60);
            left.addView(new LearningMascotView(context), new LinearLayout.LayoutParams(-1, 0, 1));
            fixedText(left, "♪  ♪  ♪", 32, 26, 58);
        } else if ("SENTENCE".equals(task.type)) {
            fixedText(left, "补全句子", 24, 20, 38);
            String blank = value.example.replaceFirst("(?i)(?<![a-z])" + Pattern.quote(value.word) + "(?![a-z])", "____");
            TextView sentence = fit(blank, 28, 19); sentence.setTag("study:example");
            left.addView(sentence, new LinearLayout.LayoutParams(-1, 0, 1));
            fixedText(left, "填入表示「" + value.meaning + "」的词", 22, 17, 74);
        } else if ("CONTEXT".equals(task.type)) {
            fixedText(left, value.word, 35, 26, 64);
            TextView sentence = fit(highlight(value.example, value.word), 28, 19); sentence.setTag("study:example");
            left.addView(sentence, new LinearLayout.LayoutParams(-1, 0, 1));
            fixedText(left, "这里的意思是？", 22, 19, 44);
        } else {
            TextView word = fit(value.word, 50, 27); word.setTag("study:word");
            word.setTypeface(null, android.graphics.Typeface.BOLD); left.addView(word, new LinearLayout.LayoutParams(-1, 0, 1));
            left.addView(new LearningMascotView(context), new LinearLayout.LayoutParams(-1, 0, 1.1f));
            fixedText(left, "选出词义", 22, 19, 40);
        }
        for (int n = 0; n < task.options.length; n++) {
            final int index = n;
            Button option = TvUiTheme.button(context, (char) ('A' + n) + "   " + task.options[n], false, () -> answer(task, index, false));
            option.setTag("study:option:" + n); option.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            option.setId(View.generateViewId());
            option.setAutoSizeTextTypeUniformWithConfiguration(18, 27, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, 0, 1); if (n > 0) slot.topMargin = dp(10);
            choices.addView(option, slot); answers.add(option); option.setEnabled(!listening);
        }
        addReplay(value, listening);
        secondary = footerButton(task.correction ? "看提示 →" : "不认识 →", false, () -> answer(task, (task.correct + 1) % task.options.length, true), 150);
        secondary.setTag("study:unknown"); secondary.setEnabled(!listening);
        verticalChoices();
        status.setText("← 重听 · → 不认识");
        if (!listening) answers.get(0).requestFocus(); else root.requestFocus();
        if (repository.settings().autoSpeak || listening) play(value, listening);
    }
    private CharSequence highlight(String sentence, String word) {
        SpannableString text = new SpannableString(sentence);
        Matcher matcher = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(sentence);
        if (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(TvUiTheme.TEAL), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }
    private Button footerButton(String title, boolean primary, Runnable action, int width) {
        Button button = TvUiTheme.button(context, title, primary, action); button.setTextSize(18);
        button.setId(View.generateViewId());
        button.setNextFocusUpId(button.getId()); button.setNextFocusDownId(button.getId());
        button.setNextFocusLeftId(button.getId()); button.setNextFocusRightId(button.getId());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(width), -1); size.leftMargin = dp(10);
        footer.addView(button, size); return button;
    }
    private void addReplay(VocabularyCatalog.Item value, boolean required) {
        replayAction = () -> play(value, required);
        replayButton = footerButton("← 重听", false, () -> replayAction.run(), 150);
        replayButton.setTag("study:replay");
        // The left key invokes this shortcut directly; keep answer navigation on the right-hand action.
        replayButton.setFocusable(false);
    }
    private void verticalChoices() {
        for (int n = 0; n < answers.size(); n++) {
            Button answer = answers.get(n);
            answer.setNextFocusUpId(answers.get(Math.max(0, n - 1)).getId());
            answer.setNextFocusDownId(n + 1 < answers.size() ? answers.get(n + 1).getId() : secondary.getId());
            answer.setNextFocusLeftId(answer.getId()); answer.setNextFocusRightId(answer.getId());
        }
        secondary.setNextFocusUpId(answers.get(answers.size() - 1).getId());
    }
    private void play(VocabularyCatalog.Item value, boolean required) {
        final long token = generation;
        if (autoAdvance) handler.removeCallbacks(autoNext);
        if (replayButton != null) replayButton.setText("← 播放中");
        status.setText("♪ … · ← 重听");
        audio.play(value.audio, repository.settings().volume, success -> {
            if (closed || paused || token != generation) return;
            if (replayButton != null) replayButton.setText(success ? "← 重听" : "← 重试");
            status.setText(success ? (review != null ? "← / 确认 重听" : nextAction != null ? "← 重听 · → 下一个" : "← 重听 · → 不认识") : "发音不可用 · ← 重试");
            if (success && required && !submitting) {
                heard = true; audioFailed = false;
                for (Button answer : answers) answer.setEnabled(true);
                if (secondary != null) { secondary.setEnabled(true); secondary.setText(active.correction ? "看提示 →" : "不认识 →"); }
                if (root.findFocus() == root || root.findFocus() == null) answers.get(0).requestFocus();
            } else if (!success && required && !submitting && !heard) {
                audioFailed = true; secondary.setEnabled(true); secondary.setText("换文字题 →"); secondary.requestFocus();
            }
            if (autoAdvance) handler.postDelayed(autoNext, 400);
        });
    }
    private void answer(LearningState.Task task, int choice, boolean unknown) {
        if (closed || paused || submitting) return;
        if (audioFailed && unknown) {
            try {
                if (demo) {
                    LessonPlanner.Question text = LessonPlanner.question(item(task), repository.curriculum().stage(item(task).stage), 0, new Random(43));
                    task.type = text.type; task.options = text.options; task.correct = text.correct; showQuestion(task);
                } else { repository.useTextFallback(session.id, task.id); render(); }
            } catch (RuntimeException e) { failure(e); }
            return;
        }
        if ("LISTEN".equals(task.type) && !heard) return;
        submitting = true; audio.stop();
        for (Button answer : answers) answer.setEnabled(false);
        if (secondary != null) secondary.setEnabled(false);
        try {
            boolean correct = !unknown && choice == task.correct;
            if (demo) demoIndex++;
            else correct = repository.submitAnswer(session.id, task, choice).correct;
            if (correct) {
                Button selected = answers.get(choice);
                TvUiTheme.buttonStyle(selected, TvUiTheme.MINT, TvUiTheme.TEAL, 3); selected.setTextColor(TvUiTheme.TEAL);
                selected.setText("✓  " + task.options[choice]); selected.setAlpha(.6f); selected.animate().alpha(1).setDuration(180).start();
                status.setText("答对啦！"); status.setTextColor(TvUiTheme.TEAL); companion.happy();
                status.setText("答对啦！ · → 下一个");
                nextAction = this::transitionNext;
                secondary.setText("下一个 →"); secondary.setEnabled(true);
                secondary.setOnClickListener(v -> transitionNext());
                autoAdvance = true; handler.postDelayed(autoNext, 900);
            } else {
                detail(item(task));
                for (int n = 0; n < answers.size(); n++) {
                    Button option = answers.get(n);
                    if (n == task.correct) { TvUiTheme.buttonStyle(option, TvUiTheme.MINT, TvUiTheme.TEAL, 3); option.setTextColor(TvUiTheme.TEAL); option.setText("✓  " + task.options[n]); }
                    else option.setAlpha(.32f);
                }
                clearFooter(unknown ? "← 重听 · 一起记住它" : "← 重听 · 再记一下");
                addReplay(item(task), false);
                boolean guided = !demo && task.correction && repository.currentTask(session.id).correctionAttempts >= 2;
                nextAction = () -> {
                    if (guided) try { repository.confirmGuidedCorrection(session.id, task.id); } catch (RuntimeException e) { failure(e); return; }
                    transitionNext();
                };
                status.setText("← 重听 · → 下一个");
                Button next = footerButton(guided || !task.correction ? "记住了  →" : "再试一次  →", true, () -> nextAction.run(), 162); next.setTag("study:advance"); next.requestFocus();
                left.setTranslationX(-dp(5)); left.animate().translationX(0).setDuration(170).start();
            }
        } catch (RuntimeException error) { failure(error); }
    }
    private void clearFooter(String text) {
        footer.removeAllViews(); status = fit(text, 18, 15); status.setTag("study:audio-status"); status.setTextColor(TvUiTheme.MUTED);
        status.setVisibility(View.INVISIBLE);
        footer.addView(status, new LinearLayout.LayoutParams(0, -1, 1));
    }
    private void transitionNext() {
        if (transitioning || closed || paused) return;
        transitioning = true; autoAdvance = false; handler.removeCallbacks(autoNext); audio.stop();
        body.animate().cancel();
        body.animate().alpha(0).translationX(-dp(26)).setDuration(140).start();
        later(this::render, 150);
    }
    private void detail(VocabularyCatalog.Item value) {
        left.removeAllViews(); left.setBackground(TvUiTheme.shape(context, TvUiTheme.WHITE, TvUiTheme.LINE, 1, 24));
        TextView word = fit(value.word, 35, 24); word.setTypeface(null, android.graphics.Typeface.BOLD); word.setTag("study:word");
        left.addView(word, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView meaning = fit(value.meaning, 25, 18); meaning.setTextColor(TvUiTheme.TEAL);
        left.addView(meaning, new LinearLayout.LayoutParams(-1, dp(64)));
        if (value.naturalExample()) {
            TextView example = fit(highlight(value.example, value.word), 23, 17); example.setTag("study:example");
            left.addView(example, new LinearLayout.LayoutParams(-1, 0, 1));
            if (repository.settings().chineseHints) {
                TextView translation = fit(value.translation, 19, 16); translation.setTextColor(TvUiTheme.MUTED);
                left.addView(translation, new LinearLayout.LayoutParams(-1, dp(64)));
            }
        } else left.addView(new LearningMascotView(context), new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void showGuided(LearningState.Task task) {
        detail(item(task));
        LearningMascotView partner = new LearningMascotView(context); partner.happy(); choices.addView(partner, new LinearLayout.LayoutParams(-1, 0, 1));
        fixedText(choices, "一起记住它", 26, 22, 64);
        clearFooter(""); addReplay(item(task), false);
        nextAction = () -> {
            if (submitting) return; submitting = true;
            try { repository.confirmGuidedCorrection(session.id, task.id); transitionNext(); } catch (RuntimeException e) { failure(e); }
        };
        status.setText("← 重听 · → 下一个");
        Button confirm = footerButton("我记住了  →", true, () -> nextAction.run(), 170); confirm.setTag("study:advance"); confirm.requestFocus();
    }
    private void later(Runnable action, int delay) {
        final long token = generation;
        handler.postDelayed(() -> { if (!closed && !paused && token == generation) action.run(); }, delay);
    }
    private void finishRound() {
        long granted = repository.completeAndGrant(session.id);
        LearningState.Snapshot state = repository.snapshot();
        result(granted > 0 ? "+ " + TvUiTheme.minutes(granted) : "本轮完成", state);
    }
    private void showReview() {
        reset(review.stage + " 词卡", 0, 1); detail(review);
        choices.addView(new LearningMascotView(context), new LinearLayout.LayoutParams(-1, 0, 1));
        fixedText(choices, review.topic, 23, 18, 54);
        clearFooter(""); addReplay(review, false);
        status.setText("← / 确认 重听"); root.requestFocus();
        if (repository.settings().autoSpeak) play(review, false);
    }
    private void result(String title, LearningState.Snapshot state) {
        int total = demo ? 3 : session.taskCount;
        reset(demo ? "体验完成" : "本轮完成", total, total); companion.happy();
        LearningMascotView partner = new LearningMascotView(context); partner.happy(); left.addView(partner, new LinearLayout.LayoutParams(-1, -1));
        TextView headline = fit(title, 40, 28); headline.setTextColor(TvUiTheme.TEAL); headline.setTypeface(null, android.graphics.Typeface.BOLD);
        choices.addView(headline, new LinearLayout.LayoutParams(-1, 0, 1));
        String summary = demo ? "" : "本轮可看 " + TvUiTheme.duration(state.balanceMs) + "\n今日还可看 " + TvUiTheme.minutes(state.dailyRemainingMs);
        choices.addView(fit(summary, 23, 19), new LinearLayout.LayoutParams(-1, 0, 1));
        String reason = demo ? "" : (state.dailyRemainingMs == 0 ? "今天的观看时间用完啦" : (!repository.hasPin() ? "请家长先设置 PIN" : ""));
        fixedText(choices, reason, 18, 15, 44);
        clearFooter("");
        if (demo) { status.setText("体验完成 · 按返回键退出"); root.requestFocus(); LearningMascotView.celebrate(root); return; }
        Button done = footerButton(state.balanceMs > 0 ? "继续观看  →" : "再学一轮", true, () -> {
            if (submitting) return; submitting = true;
            try {
                if (!demo && repository.snapshot().balanceMs > 0) { pauseAudio(); onFinished.run(); }
                else { session = null; render(); }
            } catch (RuntimeException error) { failure(error); }
        }, 248); done.setTag("study:finish"); done.requestFocus(); LearningMascotView.celebrate(root);
    }
    private void renderDemo() {
        if (demoIndex >= 3) { result("体验完成！", null); return; }
        List<VocabularyCatalog.Item> items = repository.curriculum().stage(repository.settings().stage);
        if (items.size() < 3) throw new IllegalStateException("课程尚未准备好");
        VocabularyCatalog.Item value = items.get(demoIndex);
        LessonPlanner.Question q = LessonPlanner.question(value, items, demoIndex, new Random(43 + demoIndex));
        LearningState.Task task = new LearningState.Task(); task.id = "demo_" + demoIndex; task.itemId = value.id;
        task.type = q.type; task.prompt = q.prompt; task.options = q.options; task.correct = q.correct; task.explanation = q.explanation;
        showQuestion(task);
    }
    private void failure(RuntimeException error) {
        if (closed) return;
        reset("需要一点帮助", 0, 1);
        fixedText(left, "暂时无法继续", 30, 24, 76);
        String message = error.getMessage() == null ? "请家长检查课程或存储" : error.getMessage();
        left.addView(fit(message, 23, 18), new LinearLayout.LayoutParams(-1, 0, 1));
        choices.addView(new LearningMascotView(context), new LinearLayout.LayoutParams(-1, -1));
        clearFooter("进度已保留"); Button retry = footerButton("重试", true, this::render, 170); retry.requestFocus();
    }
    private static final class Trail extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int done, total;
        Trail(Context c, int done, int total) { super(c); this.done = done; this.total = total; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float inset = TvUiTheme.dp(getContext(), 16), width = getWidth() - inset * 2, y = getHeight() / 2f;
            paint.setStrokeWidth(TvUiTheme.dp(getContext(), 3)); paint.setColor(TvUiTheme.LINE);
            c.drawLine(inset, y, getWidth() - inset, y, paint);
            int count = Math.min(total, 10);
            for (int n = 0; n < count; n++) {
                float x = inset + (count == 1 ? width / 2 : n * width / (count - 1));
                paint.setColor(n < Math.ceil(done * count / (float) total) ? TvUiTheme.TEAL : TvUiTheme.LINE);
                c.drawCircle(x, y, TvUiTheme.dp(getContext(), n == Math.min(done, count - 1) ? 7 : 5), paint);
            }
        }
    }
}
