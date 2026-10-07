package id.zani.rec;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;

final class Ui {
    interface IntCb { void on(int i); }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int REC = 0xFFFF4D5E, AMBER = 0xFFF0A500;
    static int BG, SF, SF2, INK, MUTE, PRI, ONPRI, PC;
    static boolean DARK;

    static void init(Context c) {
        DARK = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (DARK) {
            BG = 0xFF121018; SF = 0xFF1C1926; SF2 = 0xFF272336; INK = 0xFFECE8FA; MUTE = 0xFFA39DB8;
            PRI = 0xFFB9AEFF; ONPRI = 0xFF20105F; PC = 0xFF3A3470;
        } else {
            BG = 0xFFF6F3FB; SF = 0xFFFFFFFF; SF2 = 0xFFECE8F6; INK = 0xFF1D1A2B; MUTE = 0xFF6B6580;
            PRI = 0xFF5A45E0; ONPRI = 0xFFFFFFFF; PC = 0xFFDDD7FF;
        }
    }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable rr(Context c, int col, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(col);
        g.setCornerRadius(dp(c, rDp));
        return g;
    }

    static GradientDrawable circle(int col) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(col);
        return g;
    }

    static TextView tv(Context c, String t, float sp, int col, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(col);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static void clipRound(View v, final float rDp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(view.getContext(), rDp));
            }
        });
    }

    static void clipOval(View v) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rr(c, SF, 28));
        l.setPadding(dp(c, 18), dp(c, 6), dp(c, 18), dp(c, 6));
        return l;
    }

    static LinearLayout row(Context c, String t, String sub, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(c, 13), 0, dp(c, 13));
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(tv(c, t, 16, INK, false));
        if (sub != null) l.addView(tv(c, sub, 13, MUTE, false));
        r.addView(l, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (right != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
            p.leftMargin = dp(c, 12);
            r.addView(right, p);
        }
        return r;
    }

    /** Tambah baris ke kartu, dengan garis pemisah jika bukan baris pertama. */
    static void add(Context c, LinearLayout card, View row) {
        if (card.getChildCount() > 0) {
            View d = new View(c);
            d.setBackgroundColor(SF2);
            card.addView(d, new LinearLayout.LayoutParams(MATCH, dp(c, 1)));
        }
        card.addView(row);
    }

    static Switch sw(Context c, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setChecked(on);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ONPRI, MUTE}));
        s.setTrackTintList(new ColorStateList(st, new int[]{PRI, SF2}));
        s.setOnCheckedChangeListener(l);
        return s;
    }

    static LinearLayout seg(final Context c, String[] labs, int sel, final IntCb cb) {
        final LinearLayout g = new LinearLayout(c);
        g.setBackground(rr(c, SF2, 20));
        g.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        for (int i = 0; i < labs.length; i++) {
            final int k = i;
            TextView t = tv(c, labs[i], 13, MUTE, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 11), dp(c, 7), dp(c, 11), dp(c, 7));
            t.setOnClickListener(v -> { paint(c, g, k); cb.on(k); });
            g.addView(t);
        }
        paint(c, g, sel);
        return g;
    }

    static void paint(Context c, LinearLayout g, int sel) {
        for (int i = 0; i < g.getChildCount(); i++) {
            TextView t = (TextView) g.getChildAt(i);
            boolean on = i == sel;
            t.setTextColor(on ? ONPRI : MUTE);
            t.setBackground(on ? rr(c, PRI, 17) : null);
        }
    }

    static LinearLayout tile(Context c, int icon, String label, boolean on) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 8), dp(c, 16), dp(c, 8), dp(c, 16));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        t.addView(i, new LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)));
        TextView x = tv(c, label, 13, INK, true);
        x.setGravity(Gravity.CENTER);
        t.addView(x, lp(c, WRAP, WRAP, 0, 8, 0, 0));
        paintTile(c, t, on);
        return t;
    }

    static void paintTile(Context c, LinearLayout t, boolean on) {
        t.setBackground(rr(c, on ? PRI : SF, 26));
        int col = on ? ONPRI : INK;
        ((ImageView) t.getChildAt(0)).setColorFilter(col);
        ((TextView) t.getChildAt(1)).setTextColor(col);
    }

    static TextView chip(Context c, String t, boolean ok) {
        TextView v = tv(c, t, 13, ok ? ONPRI : INK, true);
        v.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        v.setBackground(rr(c, ok ? PRI : SF2, 18));
        return v;
    }

    static SeekBar seek(Context c, int min, int max, int val) {
        SeekBar s = new SeekBar(c);
        s.setMax(max - min);
        s.setProgress(val - min);
        ColorStateList cs = ColorStateList.valueOf(PRI);
        s.setProgressTintList(cs);
        s.setThumbTintList(cs);
        s.setProgressBackgroundTintList(ColorStateList.valueOf(SF2));
        return s;
    }
}
