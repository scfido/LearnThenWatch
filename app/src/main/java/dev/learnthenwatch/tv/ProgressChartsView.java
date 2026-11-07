package dev.learnthenwatch.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.time.LocalDate;
import java.util.List;

/** Offline charts drawn at the actual TV size, with real calendar gaps and independent units. */
final class ProgressChartsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LearningState.Snapshot snapshot;
    private final LearningState.DayStats[] days = new LearningState.DayStats[7];
    private LocalDate end;
    ProgressChartsView(Context context, LearningState.Snapshot state) {
        super(context); snapshot = state;
        setContentDescription("已学 " + state.learnedCount + "，已掌握 " + state.masteredCount + "，待复习 " + state.dueCount
                + "，首次正确 " + state.firstCorrect + " / " + state.totalAnswered);
    }
    ProgressChartsView(Context context, List<LearningState.DayStats> history, String today) {
        super(context); end = LocalDate.parse(today); StringBuilder description = new StringBuilder("最近七天");
        for (int i = 0; i < 7; i++) {
            String date = end.minusDays(6 - i).toString();
            for (LearningState.DayStats day : history) if (date.equals(day.day)) { days[i] = day; break; }
            description.append("；").append(date).append(" 完成 ").append(days[i] == null ? 0 : days[i].rounds)
                    .append(" 轮，观看 ").append(days[i] == null ? 0 : Math.round(days[i].usedMs / 60000f)).append(" 分钟");
        }
        setContentDescription(description);
    }
    private float d(float n) { return TvUiTheme.dp(getContext(), n); }
    private void text(Canvas canvas, String value, float x, float y, int size, int color) {
        paint.setColor(color); paint.setTextSize(d(size)); paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(value, x, y, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (snapshot != null) {
            float cell = getWidth() / 4f;
            int[] counts = {snapshot.learnedCount, snapshot.masteredCount, snapshot.dueCount};
            String[] labels = {"已学", "已掌握", "待复习"};
            int[] colors = {TvUiTheme.TEAL, TvUiTheme.TEAL, TvUiTheme.ERROR};
            for (int i = 0; i < 3; i++) {
                float x = cell * (i + .5f);
                text(canvas, Integer.toString(counts[i]), x, d(45), 30, colors[i]);
                text(canvas, labels[i], x, d(87), 18, TvUiTheme.MUTED);
            }
            float x = cell * 3.5f, y = d(42), radius = Math.min(d(30), cell * .36f);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(d(8)); paint.setColor(TvUiTheme.LINE);
            RectF circle = new RectF(x-radius, y-radius, x+radius, y+radius); canvas.drawArc(circle, -90, 360, false, paint);
            paint.setColor(TvUiTheme.TEAL);
            canvas.drawArc(circle, -90, snapshot.totalAnswered == 0 ? 0 : 360f*snapshot.firstCorrect/snapshot.totalAnswered, false, paint);
            text(canvas, snapshot.totalAnswered == 0 ? "—" : Math.round(100f*snapshot.firstCorrect/snapshot.totalAnswered) + "%", x, y+d(7), 20, TvUiTheme.TEAL);
            text(canvas, "首次正确率", x, d(99), 16, TvUiTheme.MUTED);
            return;
        }
        int maxRounds = 1; float maxMinutes = 1;
        for (LearningState.DayStats day : days) if (day != null) { maxRounds = Math.max(maxRounds, day.rounds); maxMinutes = Math.max(maxMinutes, day.usedMs/60000f); }
        float cell = getWidth()/7f, baseline = d(127), height = d(74);
        text(canvas, "绿色 · 轮数 0–" + maxRounds, getWidth()*.25f, d(22), 16, TvUiTheme.TEAL);
        text(canvas, "黄色 · 分钟 0–" + (int)Math.ceil(maxMinutes), getWidth()*.75f, d(22), 16, TvUiTheme.INK);
        for (int i = 0; i < 7; i++) {
            float x = (i+.5f)*cell, rounds = days[i] == null ? 0 : days[i].rounds, minutes = days[i] == null ? 0 : days[i].usedMs/60000f;
            float bar = Math.min(d(15), cell*.18f);
            paint.setColor(TvUiTheme.TEAL); canvas.drawRoundRect(x-bar-d(2), baseline-height*rounds/maxRounds, x-d(2), baseline, d(4), d(4), paint);
            paint.setColor(TvUiTheme.GOLD); canvas.drawRoundRect(x+d(2), baseline-height*minutes/maxMinutes, x+bar+d(2), baseline, d(4), d(4), paint);
            text(canvas, (int)rounds + " / " + Math.round(minutes), x, d(48), 14, TvUiTheme.MUTED);
            LocalDate date = end.minusDays(6-i);
            text(canvas, i == 6 ? "今天" : date.getMonthValue()+"/"+date.getDayOfMonth(), x, d(153), 16, TvUiTheme.INK);
        }
    }
}
