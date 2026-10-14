package com.vanyaliving.invoice;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The look of the login and registration screens: a deep indigo-to-teal gradient with soft light circles, the
 * app mark and name on top, and a white rounded card carrying the form. Inputs are pill-shaped with a leading
 * icon; the main button carries the same gradient. Everything is built in code (the app has no layout XML).
 */
final class AuthUi {
    private AuthUi() {}

    static final int INDIGO = 0xFF1A237E, BLUE = 0xFF1E5AA8, TEAL = 0xFF00897B, INK = 0xFF1B2733, MUTED = 0xFF607D8B, FIELD = 0xFFF4F6F9, LINE = 0xFFDDE3EA;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    /** Status and navigation bars in the gradient's colours with light icons, so the page reads as one surface. */
    static void colourBars(Activity a) {
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        a.getWindow().setStatusBarColor(INDIGO);
        a.getWindow().setNavigationBarColor(0xFF0B3C5D);
        View decor = a.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    /** Full-screen gradient with three translucent circles; the content scrolls over it. */
    static FrameLayout background(Context c, View content) {
        FrameLayout frame = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{INDIGO, 0xFF0B3C5D, TEAL});
        frame.setBackground(bg);
        frame.addView(circle(c, 260, 0x22FFFFFF), circleLp(c, 260, Gravity.TOP | Gravity.END, -90, -110));
        frame.addView(circle(c, 160, 0x1AFFFFFF), circleLp(c, 160, Gravity.TOP | Gravity.START, -60, 140));
        frame.addView(circle(c, 320, 0x14FFFFFF), circleLp(c, 320, Gravity.BOTTOM | Gravity.START, -140, -160));
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private static View circle(Context c, int sizeDp, int color) {
        View v = new View(c);
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.OVAL); gd.setColor(color);
        v.setBackground(gd);
        return v;
    }

    private static FrameLayout.LayoutParams circleLp(Context c, int sizeDp, int gravity, int xDp, int yDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), gravity);
        boolean end = (gravity & Gravity.END) == Gravity.END, bottom = (gravity & Gravity.BOTTOM) == Gravity.BOTTOM;
        lp.setMargins(end ? 0 : dp(c, xDp), bottom ? 0 : dp(c, yDp), end ? dp(c, xDp) : 0, bottom ? dp(c, yDp) : 0);
        return lp;
    }

    /** App mark (the launcher icon on a white rounded tile), the name and a tagline, centred. */
    static LinearLayout brand(Context c, String tagline) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable tile = new GradientDrawable(); tile.setColor(Color.WHITE); tile.setCornerRadius(dp(c, 22));
        logo.setBackground(tile);
        logo.setClipToOutline(true);
        logo.setElevation(dp(c, 10));
        logo.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        box.addView(logo, new LinearLayout.LayoutParams(dp(c, 84), dp(c, 84)));
        TextView name = new TextView(c);
        name.setText("BlitzBook");
        name.setTextSize(32); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); name.setTextColor(Color.WHITE);
        name.setLetterSpacing(0.02f);
        name.setPadding(0, dp(c, 14), 0, 0);
        name.setShadowLayer(dp(c, 6), 0, dp(c, 2), 0x55000000);
        box.addView(name);
        TextView tag = new TextView(c);
        tag.setText(tagline);
        tag.setTextSize(13.5f); tag.setTextColor(0xE6FFFFFF); tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), 0);
        box.addView(tag);
        return box;
    }

    /** White rounded card that carries the form. */
    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 22), dp(c, 24), dp(c, 22), dp(c, 22));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.WHITE); gd.setCornerRadius(dp(c, 24));
        card.setBackground(gd);
        card.setElevation(dp(c, 14));
        card.setClipToOutline(true);
        return card;
    }

    static TextView heading(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(22); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setTextColor(INK);
        return t;
    }

    static TextView sub(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(13.5f); t.setTextColor(MUTED);
        t.setPadding(0, dp(c, 4), 0, dp(c, 18));
        return t;
    }

    /** Pill input with a leading icon; the box turns blue when it has focus. */
    static EditText input(Context c, String hint, int iconRes, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0xFF9AA8B4);
        e.setTextColor(INK);
        e.setTextSize(15);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setMinHeight(dp(c, 54));
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setCompoundDrawablePadding(dp(c, 12));
        if (iconRes != 0) {
            Drawable d = c.getDrawable(iconRes);
            if (d != null) { d = d.mutate(); d.setTint(0xFF7B8A99); d.setBounds(0, 0, dp(c, 22), dp(c, 22)); e.setCompoundDrawablesRelative(d, null, null, null); }
        }
        e.setBackground(fieldBackground(c, false));
        e.setOnFocusChangeListener((v, has) -> {
            v.setBackground(fieldBackground(c, has));
            Drawable[] ds = e.getCompoundDrawablesRelative();
            if (ds[0] != null) ds[0].setTint(has ? BLUE : 0xFF7B8A99);
        });
        return e;
    }

    static GradientDrawable fieldBackground(Context c, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(focused ? Color.WHITE : FIELD);
        gd.setCornerRadius(dp(c, 16));
        gd.setStroke(dp(c, focused ? 2 : 1), focused ? BLUE : LINE);
        return gd;
    }

    /** A password field with an eye button that shows or hides what is typed. */
    static LinearLayout passwordRow(Context c, EditText password) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(password, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton eye = new ImageButton(c);
        eye.setImageResource(R.drawable.ic_eye);
        eye.setColorFilter(0xFF7B8A99);
        eye.setBackground(null);
        eye.setContentDescription("Show password");
        eye.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        eye.setOnClickListener(v -> {
            boolean shown = password.getTransformationMethod() instanceof HideReturnsTransformationMethod;
            int sel = password.getSelectionEnd();
            password.setTransformationMethod(shown ? PasswordTransformationMethod.getInstance() : HideReturnsTransformationMethod.getInstance());
            eye.setImageResource(shown ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            eye.setContentDescription(shown ? "Show password" : "Hide password");
            password.setSelection(Math.max(0, sel));
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44));
        elp.setMargins(-dp(c, 50), 0, dp(c, 6), 0);
        row.addView(eye, elp);
        return row;
    }

    /** The main action: gradient pill with white bold text and a pressed state. */
    static Button primary(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(c, 54));
        b.setElevation(dp(c, 6));
        b.setStateListAnimator(null);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BLUE, TEAL}); normal.setCornerRadius(dp(c, 16));
        GradientDrawable pressed = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{INDIGO, 0xFF00695C}); pressed.setCornerRadius(dp(c, 16));
        GradientDrawable off = new GradientDrawable(); off.setColor(0xFFB0BEC5); off.setCornerRadius(dp(c, 16));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, off);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        b.setBackground(s);
        return b;
    }

    /** A quieter white pill button with a coloured outline (secondary actions). */
    static Button secondary(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(BLUE);
        b.setMinHeight(dp(c, 50));
        b.setStateListAnimator(null);
        GradientDrawable normal = new GradientDrawable(); normal.setColor(Color.WHITE); normal.setCornerRadius(dp(c, 16)); normal.setStroke(dp(c, 1.5f), BLUE);
        GradientDrawable pressed = new GradientDrawable(); pressed.setColor(0xFFE3F2FD); pressed.setCornerRadius(dp(c, 16)); pressed.setStroke(dp(c, 1.5f), BLUE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        b.setBackground(s);
        return b;
    }

    static TextView link(Context c, String text, int gravity) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setTextColor(BLUE);
        t.setGravity(gravity);
        t.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
        return t;
    }

    /** Small translucent chips under the card, naming what the app does. */
    static LinearLayout featureChips(Context c, String... labels) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (String l : labels) {
            TextView t = new TextView(c);
            t.setText(l); t.setTextSize(11.5f); t.setTextColor(Color.WHITE); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
            GradientDrawable gd = new GradientDrawable(); gd.setColor(0x2EFFFFFF); gd.setCornerRadius(dp(c, 14)); gd.setStroke(dp(c, 1), 0x55FFFFFF);
            t.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(c, 4), 0, dp(c, 4), 0);
            row.addView(t, lp);
        }
        return row;
    }

    static LinearLayout.LayoutParams gap(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(c, topDp), 0, 0);
        return lp;
    }

    /** Card slides up and fades in; the brand settles from slightly above. */
    static void enter(View brand, View card) {
        brand.setAlpha(0f); brand.setTranslationY(-dp(brand.getContext(), 24));
        brand.animate().alpha(1f).translationY(0).setDuration(600).start();
        card.setAlpha(0f); card.setTranslationY(dp(card.getContext(), 48));
        card.animate().alpha(1f).translationY(0).setStartDelay(150).setDuration(650).start();
    }

    static int textType() { return InputType.TYPE_CLASS_TEXT; }
}
