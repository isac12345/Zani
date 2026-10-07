package id.zani.rec;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int M = Ui.MATCH, W = Ui.WRAP;
    private final LinearLayout[] pg = new LinearLayout[4];
    private final ScrollView[] sv = new ScrollView[4];
    private final LinearLayout[] nb = new LinearLayout[4];
    private int cur = 0, sortMode = 0;
    private Switch swFloat;
    private TextView subTv, infoTv;
    private boolean ignore;
    private LinearLayout listBox, pvRow;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final ExecutorService thEx = Executors.newFixedThreadPool(2);
    private final LruCache<Long, Bitmap> thumbs = new LruCache<>(40);

    private static final class Vid { long id, dur, size; String name; }

    // ================================================================= siklus hidup
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        if (!Ui.DARK) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        for (int i = 0; i < 4; i++) {
            sv[i] = new ScrollView(this);
            sv[i].setVerticalScrollBarEnabled(false);
            pg[i] = new LinearLayout(this);
            pg[i].setOrientation(LinearLayout.VERTICAL);
            pg[i].setPadding(d(16), d(12), d(16), d(110));
            sv[i].addView(pg[i], new FrameLayout.LayoutParams(M, W));
            root.addView(sv[i], new FrameLayout.LayoutParams(M, M));
        }
        int[] ic = {R.drawable.ic_nav_home, R.drawable.ic_nav_float, R.drawable.ic_nav_list, R.drawable.ic_nav_set};
        String[] lb = {"Beranda", "Melayang", "Rekaman", "Setelan"};
        LinearLayout nav = new LinearLayout(this);
        nav.setBackground(Ui.rr(this, Ui.SF, 32));
        nav.setElevation(d(8));
        nav.setPadding(d(6), d(6), d(6), d(6));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout nbtn = new LinearLayout(this);
            nbtn.setGravity(Gravity.CENTER_VERTICAL);
            nbtn.setPadding(d(16), 0, d(16), 0);
            ImageView iv = new ImageView(this);
            iv.setImageResource(ic[i]);
            nbtn.addView(iv, new LinearLayout.LayoutParams(d(24), d(24)));
            TextView t = Ui.tv(this, lb[i], 14, Ui.INK, true);
            t.setPadding(d(8), 0, 0, 0);
            nbtn.addView(t);
            nbtn.setOnClickListener(v -> show(k));
            nb[i] = nbtn;
            nav.addView(nbtn, new LinearLayout.LayoutParams(W, d(50)));
        }
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(W, W, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nl.bottomMargin = d(16);
        root.addView(nav, nl);
        setContentView(root);
        for (int i = 0; i < 4; i++) fill(i);
        show(0);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill(0);
        if (cur == 2) loadList();
    }

    @Override
    protected void onDestroy() {
        ex.shutdown();
        thEx.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] p, int[] r) {
        super.onRequestPermissionsResult(rq, p, r);
        fill(0);
    }

    private void handle(Intent i) {
        if (i != null && "id.zani.rec.TOGGLE".equals(i.getAction())) {
            i.setAction(null);
            setFloat(!FloatingService.running);
        }
    }

    private int d(float v) { return Ui.dp(this, v); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private void show(int i) {
        cur = i;
        for (int j = 0; j < 4; j++) {
            sv[j].setVisibility(j == i ? View.VISIBLE : View.GONE);
            boolean on = j == i;
            nb[j].setBackground(on ? Ui.rr(this, Ui.PC, 26) : null);
            ((ImageView) nb[j].getChildAt(0)).setColorFilter(on ? Ui.INK : Ui.MUTE);
            nb[j].getChildAt(1).setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (i == 2) loadList();
    }

    private void fill(int i) {
        pg[i].removeAllViews();
        switch (i) {
            case 0: home(); break;
            case 1: floatPage(); break;
            case 2: recs(); break;
            default: settings(); break;
        }
    }

    // ================================================================= izin & tombol melayang
    private boolean hasMic() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    private boolean hasNotif() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPerms() {
        ArrayList<String> a = new ArrayList<>();
        if (!hasMic()) a.add(Manifest.permission.RECORD_AUDIO);
        if (!hasNotif()) a.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            a.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!a.isEmpty()) requestPermissions(a.toArray(new String[0]), 1);
    }

    private void setFloat(boolean on) {
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                toast("Izinkan Zani tampil di atas aplikasi lain");
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                updateFloatUi();
                return;
            }
            askPerms();
            try { startForegroundService(new Intent(this, FloatingService.class)); } catch (Exception e) { toast("Gagal menyalakan tombol melayang"); }
        } else {
            FloatingService.stopAll(this);
        }
        h.postDelayed(this::updateFloatUi, 350);
    }

    private void updateFloatUi() {
        if (swFloat != null) {
            ignore = true;
            swFloat.setChecked(FloatingService.running);
            ignore = false;
        }
        if (subTv != null) subTv.setText(FloatingService.running ? "Tombol melayang aktif" : "Tombol melayang mati");
    }

    private TextView permChip(boolean ok, final Runnable r) {
        TextView c = Ui.chip(this, ok ? "Aktif" : "Atur", ok);
        if (!ok) c.setOnClickListener(v -> r.run());
        return c;
    }

    // ================================================================= halaman 0: beranda
    private void home() {
        final Context c = this;
        LinearLayout p = pg[0];
        FrameLayout ban = new FrameLayout(c);
        Ui.clipRound(ban, 34);
        ImageView bi = new ImageView(c);
        bi.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bi.setImageResource(R.drawable.banner);
        ban.addView(bi, new FrameLayout.LayoutParams(M, M));
        View sh = new View(c);
        sh.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xEB120C28, 0x00120C28}));
        ban.addView(sh, new FrameLayout.LayoutParams(M, M));
        LinearLayout br = new LinearLayout(c);
        br.setGravity(Gravity.CENTER_VERTICAL);
        br.setPadding(d(18), d(18), d(18), d(18));
        ImageView ic = new ImageView(c);
        ic.setImageResource(R.drawable.ic_zani);
        ic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.clipRound(ic, 20);
        br.addView(ic, new LinearLayout.LayoutParams(d(64), d(64)));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(d(14), 0, 0, 0);
        tx.addView(Ui.tv(c, "Zani", 30, 0xFFFFFFFF, true));
        subTv = Ui.tv(c, "", 14, 0xDDFFFFFF, false);
        tx.addView(subTv);
        br.addView(tx);
        ban.addView(br, new FrameLayout.LayoutParams(M, W, Gravity.BOTTOM));
        p.addView(ban, Ui.lp(c, M, d(210), 0, 0, 0, 14));

        LinearLayout mc = Ui.card(c);
        swFloat = Ui.sw(c, FloatingService.running, (v, on) -> { if (!ignore) setFloat(on); });
        Ui.add(c, mc, Ui.row(c, "Tombol melayang", "Muncul di atas game dan aplikasi. Rekam langsung dari sana.", swFloat));
        p.addView(mc, Ui.lp(c, M, W, 0, 0, 0, 14));
        updateFloatUi();

        LinearLayout g = new LinearLayout(c);
        final LinearLayout tm = Ui.tile(c, R.drawable.ic_mic, "Mikrofon", Prefs.b(c, Prefs.MIC, true));
        tm.setOnClickListener(v -> {
            boolean n = !Prefs.b(c, Prefs.MIC, true);
            Prefs.put(c, Prefs.MIC, n);
            Ui.paintTile(c, tm, n);
            FloatingService.refresh(c);
        });
        final LinearLayout tc = Ui.tile(c, R.drawable.ic_timer, "Hitung mundur", Prefs.b(c, Prefs.CD, true));
        tc.setOnClickListener(v -> {
            boolean n = !Prefs.b(c, Prefs.CD, true);
            Prefs.put(c, Prefs.CD, n);
            Ui.paintTile(c, tc, n);
        });
        g.addView(tm, Ui.lp(c, 0, W, 0, 0, 5, 0));
        ((LinearLayout.LayoutParams) tm.getLayoutParams()).weight = 1f;
        g.addView(tc, Ui.lp(c, 0, W, 5, 0, 0, 0));
        ((LinearLayout.LayoutParams) tc.getLayoutParams()).weight = 1f;
        p.addView(g, Ui.lp(c, M, W, 0, 0, 0, 14));

        final int[] rv = {480, 720, 1080};
        int r = Prefs.i(c, Prefs.RES, 720);
        int sel = r == 480 ? 0 : r == 720 ? 1 : r == 1080 ? 2 : -1;
        LinearLayout qc = Ui.card(c);
        Ui.add(c, qc, Ui.row(c, "Kualitas", "Makin kecil, makin hemat ruang",
                Ui.seg(c, new String[]{"480p", "720p", "1080p"}, sel, i -> { Prefs.put(c, Prefs.RES, rv[i]); fill(3); })));
        p.addView(qc, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout pc = Ui.card(c);
        Ui.add(c, pc, Ui.row(c, "Tampil di atas aplikasi lain", "Wajib untuk tombol melayang",
                permChip(Settings.canDrawOverlays(c), () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))));
        Ui.add(c, pc, Ui.row(c, "Mikrofon", null, permChip(hasMic(), this::askPerms)));
        if (Build.VERSION.SDK_INT >= 33) Ui.add(c, pc, Ui.row(c, "Notifikasi", null, permChip(hasNotif(), this::askPerms)));
        p.addView(pc, Ui.lp(c, M, W, 0, 0, 0, 10));

        TextView n = Ui.tv(c, "Cara pakai: buka game, ketuk tombol Zani, lalu pilih rekam.", 13, Ui.MUTE, false);
        n.setGravity(Gravity.CENTER);
        p.addView(n, Ui.lp(c, M, W, 8, 0, 8, 0));
    }

    // ================================================================= halaman 1: atur tombol melayang
    private static final String[] BK = {Prefs.B_REC, Prefs.B_PS, Prefs.B_SS, Prefs.B_MIC, Prefs.B_HD};
    private static final int[] BI = {R.drawable.ic_rec, R.drawable.ic_pause, R.drawable.ic_shot, R.drawable.ic_mic, R.drawable.ic_hide};
    private static final String[] BL = {"Mulai/Stop rekam", "Jeda/Lanjut", "Screenshot", "Mikrofon cepat", "Sembunyikan sementara"};

    private int enabledCount() {
        int n = 0;
        for (String k : BK) if (Prefs.b(this, k, true)) n++;
        return n;
    }

    private void floatPage() {
        final Context c = this;
        LinearLayout p = pg[1];
        FrameLayout pv = new FrameLayout(c);
        Ui.clipRound(pv, 30);
        ImageView wi = new ImageView(c);
        wi.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wi.setImageResource(R.drawable.wallpaper);
        pv.addView(wi, new FrameLayout.LayoutParams(M, M));
        View dim = new View(c);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x77000000, 0x00000000}));
        pv.addView(dim, new FrameLayout.LayoutParams(M, M));
        pvRow = new LinearLayout(c);
        pvRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(W, W, Gravity.START | Gravity.CENTER_VERTICAL);
        rl.leftMargin = d(10);
        pv.addView(pvRow, rl);
        p.addView(pv, Ui.lp(c, M, d(330), 0, 0, 0, 8));
        TextView hint = Ui.tv(c, "Pratinjau tombol. Di HP, tombol ini melayang di atas game dan aplikasi.", 13, Ui.MUTE, false);
        hint.setGravity(Gravity.CENTER);
        p.addView(hint, Ui.lp(c, M, W, 8, 0, 8, 12));
        drawPv();

        LinearLayout bc = Ui.card(c);
        for (int i = 0; i < BK.length; i++) {
            final String k = BK[i];
            Switch s = Ui.sw(c, Prefs.b(c, k, true), (bv, on) -> {
                if (ignore) return;
                if (!on && enabledCount() <= 1) {
                    ignore = true;
                    bv.setChecked(true);
                    ignore = false;
                    toast("Minimal satu tombol harus aktif");
                    return;
                }
                Prefs.put(c, k, on);
                drawPv();
                FloatingService.refresh(c);
            });
            Ui.add(c, bc, Ui.row(c, BL[i], null, s));
        }
        p.addView(bc, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout sc = Ui.card(c);
        slider(sc, "Ukuran tombol", 70, 130, Prefs.SIZE);
        slider(sc, "Transparansi saat diam", 30, 100, Prefs.ALPHA);
        p.addView(sc, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout oc = Ui.card(c);
        Ui.add(c, oc, Ui.row(c, "Menempel ke tepi layar", "Rapi dan tidak menutupi tombol game",
                Ui.sw(c, Prefs.b(c, Prefs.SNAP, true), (v, on) -> Prefs.put(c, Prefs.SNAP, on))));
        Ui.add(c, oc, Ui.row(c, "Sembunyikan tombol saat merekam", "Tidak ikut terekam. Kontrol lewat notifikasi.",
                Ui.sw(c, Prefs.b(c, Prefs.HIDEREC, false), (v, on) -> Prefs.put(c, Prefs.HIDEREC, on))));
        p.addView(oc, Ui.lp(c, M, W, 0, 0, 0, 10));
    }

    private void slider(LinearLayout card, String title, final int min, int max, final String key) {
        final Context c = this;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, d(13), 0, d(8));
        int val = Prefs.i(c, key, 100);
        LinearLayout top = new LinearLayout(c);
        TextView t = Ui.tv(c, title, 16, Ui.INK, false);
        final TextView vt = Ui.tv(c, val + "%", 14, Ui.MUTE, true);
        top.addView(t, new LinearLayout.LayoutParams(0, W, 1f));
        top.addView(vt);
        box.addView(top);
        SeekBar sb = Ui.seek(c, min, max, val);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                int v = pr + min;
                vt.setText(v + "%");
                if (user) { Prefs.put(c, key, v); drawPv(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { FloatingService.refresh(c); }
        });
        box.addView(sb);
        Ui.add(c, card, box);
    }

    private void drawPv() {
        if (pvRow == null) return;
        Context c = this;
        pvRow.removeAllViews();
        float sc = Prefs.i(c, Prefs.SIZE, 100) / 100f;
        int bsz = d(48 * sc), m = d(2);
        FrameLayout bx = new FrameLayout(c);
        GradientDrawable rg = Ui.circle(0x00000000);
        rg.setStroke(m, 0xFFFFFFFF);
        bx.setBackground(rg);
        ImageView iv = new ImageView(c);
        iv.setImageResource(R.drawable.ic_zani);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.clipOval(iv);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(bsz - 2 * m, bsz - 2 * m);
        ip.setMargins(m, m, m, m);
        bx.addView(iv, ip);
        bx.setAlpha(Prefs.i(c, Prefs.ALPHA, 100) / 100f);
        pvRow.addView(bx, new LinearLayout.LayoutParams(bsz, bsz));
        LinearLayout mn = new LinearLayout(c);
        mn.setGravity(Gravity.CENTER_VERTICAL);
        mn.setPadding(d(6), d(6), d(6), d(6));
        mn.setBackground(Ui.rr(c, 0xE6201A30, 26));
        for (int i = 0; i < BK.length; i++) {
            if (!Prefs.b(c, BK[i], true)) continue;
            ImageView b = new ImageView(c);
            b.setImageResource(BI[i]);
            b.setBackground(Ui.circle(i == 0 ? Ui.REC : 0x24FFFFFF));
            b.setPadding(d(9), d(9), d(9), d(9));
            mn.addView(b, Ui.lp(c, d(40), d(40), 2, 0, 2, 0));
        }
        pvRow.addView(mn, Ui.lp(c, W, W, 6, 0, 0, 0));
    }

    // ================================================================= halaman 2: rekaman
    private void recs() {
        final Context c = this;
        LinearLayout p = pg[2];
        LinearLayout sg = Ui.seg(c, new String[]{"Terbaru", "Terlama", "Terbesar"}, sortMode, i -> { sortMode = i; loadList(); });
        p.addView(sg, Ui.lp(c, W, W, 0, 4, 0, 8));
        infoTv = Ui.tv(c, "", 13, Ui.MUTE, false);
        p.addView(infoTv, Ui.lp(c, M, W, 6, 0, 6, 10));
        listBox = new LinearLayout(c);
        listBox.setOrientation(LinearLayout.VERTICAL);
        p.addView(listBox, new LinearLayout.LayoutParams(M, W));
    }

    private void loadList() {
        if (listBox == null) return;
        final int sm = sortMode;
        ex.execute(() -> {
            ArrayList<Vid> out = new ArrayList<>();
            long total = 0;
            String[] pr = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE};
            String sel, arg;
            if (Build.VERSION.SDK_INT >= 29) { sel = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?"; arg = "Movies/Zani%"; }
            else { sel = MediaStore.Video.Media.DATA + " LIKE ?"; arg = "%/Movies/Zani/%"; }
            String order = sm == 1 ? MediaStore.Video.Media.DATE_ADDED + " ASC"
                    : sm == 2 ? MediaStore.Video.Media.SIZE + " DESC" : MediaStore.Video.Media.DATE_ADDED + " DESC";
            try (Cursor q = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pr, sel, new String[]{arg}, order)) {
                while (q != null && q.moveToNext()) {
                    Vid v = new Vid();
                    v.id = q.getLong(0);
                    v.name = q.getString(1);
                    v.dur = q.getLong(2);
                    v.size = q.getLong(3);
                    out.add(v);
                    total += v.size;
                }
            } catch (Exception ignore) { }
            final long tot = total;
            h.post(() -> showList(out, tot));
        });
    }

    private void showList(ArrayList<Vid> list, long total) {
        if (listBox == null || isDestroyed()) return;
        listBox.removeAllViews();
        infoTv.setText(list.isEmpty() ? "" : list.size() + " rekaman · " + Formatter.formatShortFileSize(this, total));
        if (list.isEmpty()) {
            TextView e = Ui.tv(this, "Belum ada rekaman.\nNyalakan tombol melayang, lalu ketuk tombol Zani saat main.", 14, Ui.MUTE, false);
            e.setGravity(Gravity.CENTER);
            listBox.addView(e, Ui.lp(this, M, W, 8, 40, 8, 0));
            return;
        }
        for (Vid v : list) listBox.addView(card(v), Ui.lp(this, M, W, 0, 0, 0, 12));
    }

    private ImageButton iconBtn(int res) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(Ui.INK);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setBackground(Ui.circle(Ui.SF2));
        b.setPadding(d(9), d(9), d(9), d(9));
        return b;
    }

    private View card(final Vid v) {
        final Context c = this;
        final Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v.id);
        LinearLayout cd = new LinearLayout(c);
        cd.setOrientation(LinearLayout.VERTICAL);
        cd.setBackground(Ui.rr(c, Ui.SF, 28));
        cd.setPadding(d(10), d(10), d(10), d(10));
        FrameLayout th = new FrameLayout(c);
        th.setBackgroundColor(Ui.SF2);
        Ui.clipRound(th, 20);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        th.addView(iv, new FrameLayout.LayoutParams(M, M));
        ImageView pl = new ImageView(c);
        pl.setImageResource(R.drawable.ic_play);
        pl.setColorFilter(0xFFFFFFFF);
        th.addView(pl, new FrameLayout.LayoutParams(d(44), d(44), Gravity.CENTER));
        TextView du = Ui.tv(c, State.fmt((int) (v.dur / 1000)), 12, 0xFFFFFFFF, false);
        du.setBackground(Ui.rr(c, 0x99000000, 10));
        du.setPadding(d(8), d(2), d(8), d(2));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(W, W, Gravity.BOTTOM | Gravity.END);
        dl.setMargins(0, 0, d(10), d(10));
        th.addView(du, dl);
        th.setOnClickListener(x -> startActivity(new Intent(c, PlayerActivity.class).setData(uri)));
        cd.addView(th, new LinearLayout.LayoutParams(M, d(150)));
        thumb(v.id, iv);

        LinearLayout meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(d(8), d(10), 0, d(2));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView nm = Ui.tv(c, v.name == null ? "Rekaman" : v.name, 14, Ui.INK, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tx.addView(nm);
        tx.addView(Ui.tv(c, Formatter.formatShortFileSize(c, v.size), 13, Ui.MUTE, false));
        meta.addView(tx, new LinearLayout.LayoutParams(0, W, 1f));
        ImageButton sh = iconBtn(R.drawable.ic_share);
        sh.setOnClickListener(x -> {
            Intent s = new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(s, null));
        });
        ImageButton de = iconBtn(R.drawable.ic_del);
        de.setOnClickListener(x -> new AlertDialog.Builder(c).setMessage("Hapus rekaman ini?")
                .setPositiveButton("Hapus", (dg, w) -> {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception e) { toast("Tidak bisa menghapus"); }
                    loadList();
                }).setNegativeButton("Batal", null).show());
        meta.addView(sh, Ui.lp(c, d(38), d(38), 0, 0, 6, 0));
        meta.addView(de, new LinearLayout.LayoutParams(d(38), d(38)));
        cd.addView(meta);
        return cd;
    }

    private void thumb(final long id, final ImageView iv) {
        Bitmap b = thumbs.get(id);
        if (b != null) { iv.setImageBitmap(b); return; }
        thEx.execute(() -> {
            Bitmap t = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    Uri u = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    t = getContentResolver().loadThumbnail(u, new Size(480, 270), null);
                } else {
                    t = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                }
            } catch (Exception ignore) { }
            if (t != null) {
                thumbs.put(id, t);
                final Bitmap f = t;
                h.post(() -> iv.setImageBitmap(f));
            }
        });
    }

    // ================================================================= halaman 3: setelan
    private static int idx(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private void settings() {
        final Context c = this;
        LinearLayout p = pg[3];
        final int[] rv = {0, 1080, 720, 480}, fv = {24, 30, 60, 90, 120}, bv = {0, 2, 4, 8, 12, 20, 30, 50};
        final String[] bl = {"Otomatis", "2 Mbps", "4 Mbps", "8 Mbps", "12 Mbps", "20 Mbps", "30 Mbps", "50 Mbps"};

        LinearLayout k1 = Ui.card(c);
        Ui.add(c, k1, Ui.row(c, "Resolusi", null, Ui.seg(c, new String[]{"Layar", "1080p", "720p", "480p"},
                idx(rv, Prefs.i(c, Prefs.RES, 720)), i -> { Prefs.put(c, Prefs.RES, rv[i]); fill(0); })));
        Ui.add(c, k1, Ui.row(c, "Frame rate", "120 hanya jika layar HP 120 Hz", Ui.seg(c, new String[]{"24", "30", "60", "90", "120"},
                idx(fv, Prefs.i(c, Prefs.FPS, 30)), i -> Prefs.put(c, Prefs.FPS, fv[i]))));
        Ui.add(c, k1, Ui.row(c, "Orientasi", null, Ui.seg(c, new String[]{"Auto", "Potret", "Lanskap"},
                Prefs.i(c, Prefs.ORI, 0), i -> Prefs.put(c, Prefs.ORI, i))));
        Ui.add(c, k1, Ui.row(c, "Codec", "H.264 paling aman dan ringan", Ui.seg(c, new String[]{"H.264", "HEVC"},
                Prefs.i(c, Prefs.CODEC, 0), i -> Prefs.put(c, Prefs.CODEC, i))));
        p.addView(k1, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout k2 = Ui.card(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, d(13), 0, d(8));
        LinearLayout top = new LinearLayout(c);
        top.addView(Ui.tv(c, "Bitrate", 16, Ui.INK, false), new LinearLayout.LayoutParams(0, W, 1f));
        int bi = Math.max(0, idx(bv, Prefs.i(c, Prefs.BR, 0)));
        final TextView bt = Ui.tv(c, bl[bi], 14, Ui.MUTE, true);
        top.addView(bt);
        box.addView(top);
        SeekBar sb = Ui.seek(c, 0, bv.length - 1, bi);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                bt.setText(bl[pr]);
                if (user) Prefs.put(c, Prefs.BR, bv[pr]);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(sb);
        Ui.add(c, k2, box);
        p.addView(k2, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout k3 = Ui.card(c);
        Ui.add(c, k3, Ui.row(c, "Batas aman otomatis", "Sesuaikan fps, resolusi, bitrate ke kemampuan encoder dan layar HP",
                Ui.sw(c, Prefs.b(c, Prefs.SAFE, true), (v, on) -> Prefs.put(c, Prefs.SAFE, on))));
        Ui.add(c, k3, Ui.row(c, "Rekam suara mikrofon", null,
                Ui.sw(c, Prefs.b(c, Prefs.MIC, true), (v, on) -> { Prefs.put(c, Prefs.MIC, on); fill(0); })));
        p.addView(k3, Ui.lp(c, M, W, 0, 0, 0, 14));

        LinearLayout k4 = Ui.card(c);
        Ui.add(c, k4, Ui.row(c, "Layar HP", "Refresh rate maksimum " + Caps.maxHz(c) + " Hz", null));
        Ui.add(c, k4, Ui.row(c, "Folder simpan", "Movies/Zani dan Pictures/Zani", null));
        Ui.add(c, k4, Ui.row(c, "Encoder", "Hardware (MediaRecorder), tanpa salinan frame di CPU", null));
        Ui.add(c, k4, Ui.row(c, "Zani 1.0", "Ringan, tanpa iklan, tanpa internet", null));
        p.addView(k4, Ui.lp(c, M, W, 0, 0, 0, 10));
    }
}
