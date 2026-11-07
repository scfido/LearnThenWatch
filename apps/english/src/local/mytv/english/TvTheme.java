package local.mytv.english;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared TV dimensions, colours and visible remote-control focus. */
public final class TvTheme {
    public static final int CREAM = Color.rgb(255, 249, 238);
    public static final int INK = Color.rgb(31, 58, 62);
    public static final int MUTED = Color.rgb(92, 112, 112);
    public static final int TEAL = Color.rgb(21, 132, 119);
    public static final int MINT = Color.rgb(222, 244, 231);
    public static final int GOLD = Color.rgb(249, 205, 94);
    public static final int WHITE = Color.WHITE;
    public static final int LINE = Color.rgb(209, 223, 214);
    public static final int ERROR = Color.rgb(164, 65, 45);

    private TvTheme() { }
    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
    public static GradientDrawable shape(Context c, int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(c, radius));
        if (width > 0) d.setStroke(dp(c, width), stroke);
        return d;
    }
    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false); v.setClipToPadding(false);
        return v;
    }
    public static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setClipChildren(false); v.setClipToPadding(false);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    public static TextView text(Context c, String value, int size, int color) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(true); v.setLineSpacing(dp(c, 2), 1);
        return v;
    }
    public static TextView title(Context c, String value, int size) {
        TextView v = text(c, value, size, INK); v.setTypeface(null, Typeface.BOLD); return v;
    }
    public static Button button(Context c, String label, boolean primary, Runnable action) {
        Button b = new Button(c); b.setText(label); b.setTextSize(20); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        b.setMinHeight(dp(c, 50)); b.setMinimumHeight(dp(c, 50));
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setTextColor(primary ? WHITE : INK);
        buttonStyle(b, primary ? TEAL : WHITE, LINE, 1);
        b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
        b.setOnFocusChangeListener((v, focused) -> {
            buttonStyle(b, focused ? TEAL : (primary ? TEAL : WHITE), focused ? GOLD : LINE, focused ? 3 : 1);
            b.setTextColor((focused || primary) ? WHITE : INK);
            // Keep the entire focus ring inside the measured button, including at scroll edges.
            b.animate().translationY(focused ? -dp(c, 1) : 0).setDuration(130).start();
        });
        b.setOnClickListener(v -> action.run()); return b;
    }
    public static void buttonStyle(Button b, int fill, int stroke, int width) {
        Context c = b.getContext();
        int left = b.getPaddingLeft(), top = b.getPaddingTop(), right = b.getPaddingRight(), bottom = b.getPaddingBottom();
        b.setBackground(new InsetDrawable(shape(c, fill, stroke, width, 16), dp(c, 4)));
        b.setPadding(left, top, right, bottom);
        b.setElevation(0);
    }
    public static void enter(View v) {
        v.setAlpha(0); v.setTranslationX(dp(v.getContext(), 14));
        v.animate().alpha(1).translationX(0).setDuration(190).start();
    }
    /** One focus stop for the entire content tile; descendants never act like separate controls. */
    public static void selectableCard(LinearLayout card, int fill, Runnable action) {
        card.setFocusable(true); card.setClickable(action != null);
        card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        card.setOnFocusChangeListener((v, focused) -> {
            int left = card.getPaddingLeft(), top = card.getPaddingTop(), right = card.getPaddingRight(), bottom = card.getPaddingBottom();
            card.setBackground(new InsetDrawable(shape(card.getContext(), focused ? MINT : fill,
                    focused ? TEAL : LINE, focused ? 3 : 1, 20), dp(card.getContext(), 3)));
            card.setPadding(left, top, right, bottom);
            card.animate().translationY(focused ? -dp(card.getContext(), 1) : 0).setDuration(130).start();
        });
        if (action != null) card.setOnClickListener(v -> action.run());
    }
    public static LinearLayout card(Context c, int fill) {
        LinearLayout card = column(c); card.setPadding(dp(c, 23), dp(c, 18), dp(c, 23), dp(c, 18));
        card.setBackground(shape(c, fill, LINE, fill == WHITE ? 1 : 0, 20)); return card;
    }
    public static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(parent.getContext(), top); parent.addView(child, p);
    }
    public static String duration(long ms) {
        long seconds = Math.max(0, (ms + 999) / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    public static String minutes(long ms) { return Math.max(0, (ms + 59999) / 60000) + " 分钟"; }
}
