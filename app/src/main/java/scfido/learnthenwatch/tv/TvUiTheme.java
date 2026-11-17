package scfido.learnthenwatch.tv;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared TV dimensions, colours and visible remote-control focus. */
public final class TvUiTheme {
    public enum Role {
        BACKGROUND(R.color.theme_light_background, R.color.theme_dark_background),
        SURFACE(R.color.theme_light_surface, R.color.theme_dark_surface),
        TEXT(R.color.theme_light_text, R.color.theme_dark_text),
        MUTED(R.color.theme_light_muted, R.color.theme_dark_muted),
        ACCENT(R.color.theme_light_accent, R.color.theme_dark_accent),
        ACCENT_SURFACE(R.color.theme_light_accent_surface, R.color.theme_dark_accent_surface),
        PRIMARY(R.color.theme_light_primary, R.color.theme_dark_primary),
        ON_PRIMARY(R.color.theme_light_on_primary, R.color.theme_dark_on_primary),
        GOLD(R.color.theme_light_gold, R.color.theme_dark_gold),
        LINE(R.color.theme_light_line, R.color.theme_dark_line),
        ERROR(R.color.theme_light_error, R.color.theme_dark_error),
        WARNING_TRACK(R.color.theme_light_warning_track, R.color.theme_dark_warning_track);

        private final int lightResource, darkResource;
        Role(int lightResource, int darkResource) {
            this.lightResource = lightResource; this.darkResource = darkResource;
        }
    }

    /** Immutable palette shared by all style bindings in one refresh. */
    private static final class Palette {
        public final boolean dark;
        private final int[] values = new int[Role.values().length];
        private Palette(Context context, boolean dark) {
            this.dark = dark;
            for (Role role : Role.values())
                values[role.ordinal()] = context.getColor(dark ? role.darkResource : role.lightResource);
        }
        public int color(Role role) { return values[role.ordinal()]; }
    }

    private interface BackgroundStyle { void apply(Palette colors); }
    private final Palette light, dark;
    private final Context context;
    private Palette colors;

    public TvUiTheme(Context context, ThemeMode mode) {
        this.context = context;
        light = new Palette(context, false); dark = new Palette(context, true);
        refresh(mode, null);
    }
    public int color(Role role) { return colors.color(role); }
    public boolean isDark() { return colors.dark; }

    /** Recolour the existing tree so input, audio and timed lesson transitions keep running. */
    public void refresh(ThemeMode mode, View root) {
        boolean systemDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        colors = mode.isDark(systemDark) ? dark : light;
        if (root != null) applyTree(root);
    }
    private void applyTree(View view) {
        Object textRole = view.getTag(R.id.theme_text_color);
        if (view instanceof TextView && textRole instanceof Role)
            ((TextView) view).setTextColor(color((Role) textRole));
        applyBackground(view);
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) applyTree(group.getChildAt(n));
        }
    }
    private void applyBackground(View view) {
        Object style = view.getTag(R.id.theme_background);
        if (style instanceof BackgroundStyle) ((BackgroundStyle) style).apply(colors);
    }
    private void backgroundStyle(View view, BackgroundStyle style) {
        view.setTag(R.id.theme_background, style); style.apply(colors);
    }
    public void textColor(TextView view, Role role) {
        view.setTag(R.id.theme_text_color, role); view.setTextColor(color(role));
    }
    public void backgroundColor(View view, Role role) {
        backgroundStyle(view, palette -> view.setBackgroundColor(palette.color(role)));
    }
    public void background(View view, Role fill, Role stroke, int width, int radius) {
        backgroundStyle(view, palette -> view.setBackground(shape(view.getContext(), palette.color(fill), palette.color(stroke), width, radius)));
    }
    public ForegroundColorSpan accentSpan() {
        return new ForegroundColorSpan(color(Role.ACCENT)) {
            @Override public void updateDrawState(TextPaint paint) { paint.setColor(color(Role.ACCENT)); }
        };
    }
    public void applyWindow(Activity activity, boolean gate) {
        View existing = activity.getWindow().peekDecorView();
        if (existing != null && Boolean.valueOf(isDark()).equals(existing.getTag(R.id.theme_window_dark))) return;
        activity.setTheme(gate ? (isDark() ? R.style.LearningGateThemeDark : R.style.LearningGateTheme)
                : (isDark() ? R.style.AppThemeDark : R.style.AppThemeLight));
        if (!gate) activity.getWindow().setBackgroundDrawable(new ColorDrawable(color(Role.BACKGROUND)));
        activity.getWindow().setStatusBarColor(color(Role.BACKGROUND));
        activity.getWindow().setNavigationBarColor(color(Role.BACKGROUND));
        View decor = activity.getWindow().getDecorView();
        int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int visibility = decor.getSystemUiVisibility() & ~lightBars;
        decor.setSystemUiVisibility(isDark() ? visibility : visibility | lightBars);
        decor.setTag(R.id.theme_window_dark, isDark());
    }
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
    public TextView text(String value, int size, Role role) {
        TextView v = new TextView(context); v.setText(value); v.setTextSize(size); textColor(v, role);
        v.setIncludeFontPadding(true); v.setLineSpacing(dp(context, 2), 1);
        return v;
    }
    public TextView title(String value, int size) {
        TextView v = text(value, size, Role.TEXT); v.setTypeface(null, Typeface.BOLD); return v;
    }
    public Button button(String label, boolean primary, Runnable action) {
        Context c = context;
        Button b = new Button(c); b.setText(label); b.setTextSize(20); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        b.setMinHeight(dp(c, 50)); b.setMinimumHeight(dp(c, 50));
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setDefaultFocusHighlightEnabled(false);
        backgroundStyle(b, palette -> {
            boolean focused = b.hasFocus(), correct = Boolean.TRUE.equals(b.getTag(R.id.theme_correct_answer));
            boolean filled = focused || primary;
            Role fill = correct ? Role.ACCENT_SURFACE : (filled ? Role.PRIMARY : Role.SURFACE);
            Role stroke = focused ? Role.GOLD : (correct ? Role.ACCENT : Role.LINE);
            Role text = correct ? Role.ACCENT : (filled ? Role.ON_PRIMARY : Role.TEXT);
            buttonStyle(b, palette.color(fill), palette.color(stroke), focused || correct ? 3 : 1);
            b.setTextColor(palette.color(text));
        });
        b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
        b.setOnFocusChangeListener((v, focused) -> {
            applyBackground(b);
            // Keep the entire focus ring inside the measured button, including at scroll edges.
            b.animate().translationY(focused ? -dp(c, 1) : 0).setDuration(130).start();
        });
        b.setOnClickListener(v -> action.run()); return b;
    }
    public void correctAnswer(Button button) {
        button.setTag(R.id.theme_correct_answer, true); applyBackground(button);
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
    public void selectableCard(LinearLayout card, Role fill, Runnable action) {
        card.setFocusable(true); card.setClickable(action != null);
        card.setDefaultFocusHighlightEnabled(false);
        card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        backgroundStyle(card, palette -> {
            boolean focused = card.hasFocus();
            int left = card.getPaddingLeft(), top = card.getPaddingTop(), right = card.getPaddingRight(), bottom = card.getPaddingBottom();
            card.setBackground(new InsetDrawable(shape(card.getContext(), palette.color(focused ? Role.ACCENT_SURFACE : fill),
                    palette.color(focused ? Role.ACCENT : Role.LINE), focused ? 3 : 1, 20), dp(card.getContext(), 3)));
            card.setPadding(left, top, right, bottom);
        });
        card.setOnFocusChangeListener((v, focused) -> {
            applyBackground(card);
            card.animate().translationY(focused ? -dp(card.getContext(), 1) : 0).setDuration(130).start();
        });
        if (action != null) card.setOnClickListener(v -> action.run());
    }
    public LinearLayout card(Role fill) {
        Context c = context;
        LinearLayout card = column(c); card.setPadding(dp(c, 23), dp(c, 18), dp(c, 23), dp(c, 18));
        background(card, fill, Role.LINE, fill == Role.SURFACE ? 1 : 0, 20); return card;
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
