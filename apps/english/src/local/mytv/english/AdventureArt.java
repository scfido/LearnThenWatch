package local.mytv.english;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Original vector companion; animation follows attachment and window visibility. */
public final class AdventureArt extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    private boolean happy;
    public AdventureArt(Context context) {
        super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }
    public void happy() { happy = true; invalidate(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startMotion(); }
    @Override protected void onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) startMotion(); else stopMotion();
    }
    private void startMotion() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }
    private void stopMotion() { if (animator != null) { animator.cancel(); animator = null; } }
    private void fill(int color) { paint.setStyle(Paint.Style.FILL); paint.setColor(color); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = Math.min(getWidth() / 220f, getHeight() / 176f);
        canvas.save(); canvas.translate((getWidth() - 220 * unit) / 2, (getHeight() - 176 * unit) / 2); canvas.scale(unit, unit);
        fill(TvTheme.MINT); canvas.drawCircle(110, 86, 75, paint);
        fill(0x18158477); canvas.drawOval(new RectF(60, 148, 158, 161), paint);
        canvas.translate(0, (float) Math.sin(phase * Math.PI * 2) * (happy ? 6 : 3));
        fill(TvTheme.TEAL); canvas.drawRoundRect(new RectF(61, 48, 157, 144), 37, 37, paint);
        canvas.save(); canvas.rotate(happy ? -25 : -8, 66, 97); canvas.drawOval(new RectF(44, 87, 75, 120), paint); canvas.restore();
        canvas.save(); canvas.rotate(happy ? 25 : 8, 152, 97); canvas.drawOval(new RectF(145, 87, 176, 120), paint); canvas.restore();
        fill(TvTheme.WHITE); canvas.drawOval(new RectF(78, 69, 108, 104), paint); canvas.drawOval(new RectF(112, 69, 142, 104), paint);
        fill(TvTheme.INK);
        boolean blink = phase > .87f && phase < .92f;
        if (happy || blink) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3.2f);
            canvas.drawArc(new RectF(86, 80, 100, 94), 190, 160, false, paint);
            canvas.drawArc(new RectF(120, 80, 134, 94), 190, 160, false, paint);
        } else { canvas.drawCircle(94, 88, 5, paint); canvas.drawCircle(126, 88, 5, paint); }
        fill(TvTheme.GOLD);
        Path beak = new Path(); beak.moveTo(103, 106); beak.lineTo(117, 106); beak.lineTo(110, 115); beak.close(); canvas.drawPath(beak, paint);
        canvas.drawRoundRect(new RectF(81, 36, 139, 54), 7, 7, paint);
        fill(0xFFF3AC91); canvas.drawCircle(78, 108, 7, paint); canvas.drawCircle(142, 108, 7, paint);
        fill(TvTheme.GOLD); canvas.drawOval(new RectF(76, 139, 97, 150), paint); canvas.drawOval(new RectF(122, 139, 143, 150), paint);
        star(canvas, 177, 43, 10 + (float) Math.sin(phase * Math.PI * 2) * 2, TvTheme.GOLD);
        star(canvas, 43, 61, 6, 0xFFF3AC91);
        canvas.restore();
    }
    private static void star(Canvas c, float x, float y, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float radius = i % 2 == 0 ? size : size * .43f;
            float px = x + (float) Math.cos(angle) * radius, py = y + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close(); c.drawPath(path, p);
    }
    public static void celebrate(FrameLayout parent) {
        View burst = new View(parent.getContext()) {
            float progress;
            ValueAnimator motion;
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow(); motion = ValueAnimator.ofFloat(0, 1); motion.setDuration(1100);
                motion.setInterpolator(new DecelerateInterpolator());
                motion.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); }); motion.start();
            }
            @Override protected void onDetachedFromWindow() { if (motion != null) motion.cancel(); super.onDetachedFromWindow(); }
            @Override protected void onDraw(Canvas canvas) {
                if (progress >= 1) return;
                Paint confetti = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (int i = 0; i < 28; i++) {
                    float x = getWidth() * (i / 28f) + (float) Math.sin(i * 7) * progress * 65;
                    float y = -25 + progress * getHeight() * (0.5f + (i % 6) * .12f);
                    confetti.setColor(new int[]{TvTheme.GOLD, TvTheme.TEAL, 0xFFF3AC91, 0xFF8BBFE6}[i % 4]);
                    confetti.setAlpha((int) ((1 - progress) * 230));
                    canvas.save(); canvas.rotate(progress * (i % 2 == 0 ? 220 : -220), x, y);
                    canvas.drawRoundRect(x, y, x + 7, y + 15, 2, 2, confetti); canvas.restore();
                }
            }
        };
        burst.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(burst, new FrameLayout.LayoutParams(-1, -1));
        burst.postDelayed(() -> { if (burst.getParent() == parent) parent.removeView(burst); }, 1200);
    }
}
