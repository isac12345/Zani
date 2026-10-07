package id.zani.rec;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Tombol melayang di atas semua aplikasi/game. Satu-satunya tempat mulai & stop rekam. */
public class FloatingService extends Service implements State.L {
    static boolean running;
    static final String A_REFRESH = "id.zani.rec.REFRESH", A_QUIT = "id.zani.rec.QUIT";
    private static final int PIF = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private LinearLayout root, menu;
    private FrameLayout box;
    private GradientDrawable ring;
    private TextView badge, cdTv;
    private ImageButton bRec, bPs, bSs, bMic, bHd;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean open, wasOpen, drag;
    private int bs, side, ox, sw, sh, sx, sy;
    private float dx, dy;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (State.mode == State.REC && badge != null) {
                badge.setText(State.fmt(State.secs()));
                h.postDelayed(this, 1000);
            }
        }
    };

    static void refresh(Context c) {
        if (running) {
            try { c.startService(new Intent(c, FloatingService.class).setAction(A_REFRESH)); } catch (Exception ignore) { }
        }
    }

    static void stopAll(Context c) {
        Prefs.put(c, Prefs.FLOAT_ON, false);
        c.stopService(new Intent(c, FloatingService.class));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Prefs.put(this, Prefs.FLOAT_ON, true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("float", "Tombol melayang", NotificationManager.IMPORTANCE_LOW));
        PendingIntent quit = PendingIntent.getService(this, 9, new Intent(this, FloatingService.class).setAction(A_QUIT), PIF);
        Notification n = new Notification.Builder(this, "float")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Zani aktif")
                .setContentText("Buka game, ketuk tombol Zani untuk merekam")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PIF))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), "Matikan", quit).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(21, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(21, n);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        build();
        State.add(this);
        changed();
    }

    @Override
    public int onStartCommand(Intent in, int f, int id) {
        String a = in == null ? null : in.getAction();
        if (A_QUIT.equals(a)) { stopAll(this); }
        else if (A_REFRESH.equals(a) && wm != null) { closeIfOpen(); build(); changed(); }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (wm != null) { build(); changed(); }
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        State.rm(this);
        if (root != null && wm != null) { try { wm.removeView(root); } catch (Exception ignore) { } }
        root = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- tampilan
    private void build() {
        if (root != null) { try { wm.removeView(root); } catch (Exception ignore) { } }
        Ui.init(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        sw = dm.widthPixels;
        sh = dm.heightPixels;
        bs = Ui.dp(this, 48 * Prefs.i(this, Prefs.SIZE, 100) / 100f);
        int m = Ui.dp(this, 2);

        ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x00000000);
        ring.setStroke(m, 0xFFFFFFFF);
        box = new FrameLayout(this);
        box.setBackground(ring);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_zani);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.clipOval(icon);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(bs - 2 * m, bs - 2 * m);
        ip.setMargins(m, m, m, m);
        box.addView(icon, ip);

        cdTv = Ui.tv(this, "", 22, 0xFFFFFFFF, true);
        cdTv.setGravity(Gravity.CENTER);
        cdTv.setBackground(Ui.circle(0xAA000000));
        cdTv.setVisibility(View.GONE);
        box.addView(cdTv, new FrameLayout.LayoutParams(bs, bs));

        badge = Ui.tv(this, "", 10, 0xFFFFFFFF, true);
        badge.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
        badge.setBackground(Ui.rr(this, Ui.REC, 8));
        badge.setVisibility(View.GONE);
        box.addView(badge, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        menu = new LinearLayout(this);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 6);
        menu.setPadding(pad, pad, pad, pad);
        menu.setBackground(Ui.rr(this, 0xE6201A30, 26));
        bRec = Prefs.b(this, Prefs.B_REC, true) ? mk(R.drawable.ic_rec, "rec") : null;
        bPs = Prefs.b(this, Prefs.B_PS, true) ? mk(R.drawable.ic_pause, "ps") : null;
        bSs = Prefs.b(this, Prefs.B_SS, true) ? mk(R.drawable.ic_shot, "ss") : null;
        bMic = Prefs.b(this, Prefs.B_MIC, true) ? mk(Prefs.b(this, Prefs.MIC, true) ? R.drawable.ic_mic : R.drawable.ic_mic_off, "mic") : null;
        bHd = Prefs.b(this, Prefs.B_HD, true) ? mk(R.drawable.ic_hide, "hd") : null;
        if (bRec == null && bPs == null && bSs == null && bMic == null && bHd == null) bRec = mk(R.drawable.ic_rec, "rec");
        menu.setVisibility(View.GONE);

        root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER_VERTICAL);
        lp = new WindowManager.LayoutParams(Ui.WRAP, Ui.WRAP, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        side = Prefs.i(this, Prefs.POSX, 0);
        int y = Prefs.i(this, Prefs.POSY, Ui.dp(this, 180));
        lp.x = side == 0 ? 0 : sw - bs;
        lp.y = Math.max(0, Math.min(y, sh - bs));
        open = false;
        order();
        box.setOnTouchListener(touch);
        root.setAlpha(idleAlpha());
        wm.addView(root, lp);
    }

    private float idleAlpha() { return Prefs.i(this, Prefs.ALPHA, 100) / 100f; }

    private ImageButton mk(int res, final String k) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = Ui.dp(this, 9);
        b.setPadding(p, p, p, p);
        b.setBackground(Ui.circle(0x24FFFFFF));
        b.setOnClickListener(v -> act(k));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        l.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        menu.addView(b, l);
        return b;
    }

    private void order() {
        root.removeAllViews();
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        if (side == 0) {
            root.addView(box, new LinearLayout.LayoutParams(bs, bs));
            mp.leftMargin = Ui.dp(this, 6);
            root.addView(menu, mp);
        } else {
            mp.rightMargin = Ui.dp(this, 6);
            root.addView(menu, mp);
            root.addView(box, new LinearLayout.LayoutParams(bs, bs));
        }
    }

    private void upd() {
        if (root == null) return;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignore) { }
    }

    private void openMenu() {
        if (open) return;
        open = true;
        ox = lp.x;
        menu.setVisibility(View.VISIBLE);
        root.setAlpha(1f);
        root.measure(0, 0);
        int w = root.getMeasuredWidth();
        if (side == 1) lp.x = ox - (w - bs);
        if (lp.x + w > sw) lp.x = sw - w;
        if (lp.x < 0) lp.x = 0;
        upd();
    }

    private void closeMenu() {
        if (!open) return;
        open = false;
        menu.setVisibility(View.GONE);
        lp.x = ox;
        root.setAlpha(idleAlpha());
        upd();
    }

    private void closeIfOpen() { if (open) closeMenu(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(v, Math.max(lo, hi))); }

    private final View.OnTouchListener touch = (v, e) -> {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                wasOpen = open;
                if (open) closeMenu();
                drag = false;
                dx = e.getRawX();
                dy = e.getRawY();
                sx = lp.x;
                sy = lp.y;
                root.setAlpha(idleAlpha());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                if (!drag && Math.abs(mx) + Math.abs(my) > Ui.dp(this, 8)) drag = true;
                if (drag) {
                    lp.x = clamp(sx + (int) mx, 0, sw - bs);
                    lp.y = clamp(sy + (int) my, 0, sh - bs);
                    upd();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (drag) finishDrag();
                else if (!wasOpen && e.getActionMasked() == MotionEvent.ACTION_UP) openMenu();
                return true;
            }
            default:
                return false;
        }
    };

    private void finishDrag() {
        side = lp.x + bs / 2 < sw / 2 ? 0 : 1;
        order();
        Prefs.put(this, Prefs.POSX, side);
        Prefs.put(this, Prefs.POSY, lp.y);
        if (Prefs.b(this, Prefs.SNAP, true)) {
            int tx = side == 0 ? 0 : sw - bs;
            if (tx != lp.x) {
                ValueAnimator a = ValueAnimator.ofInt(lp.x, tx);
                a.setDuration(150);
                a.addUpdateListener(an -> { lp.x = (Integer) an.getAnimatedValue(); upd(); });
                a.start();
            }
        }
    }

    // ---------------------------------------------------------------- aksi tombol
    private void act(String k) {
        int m = State.mode;
        switch (k) {
            case "rec":
                if (m == State.IDLE) { closeMenu(); startCapture("rec"); }
                else RecorderServiceCmd(RecorderService.A_STOP);
                break;
            case "ps":
                if (m == State.REC || m == State.PAUSE) RecorderServiceCmd(RecorderService.A_PAUSE);
                else toast("Jeda aktif saat merekam");
                break;
            case "ss":
                if (m == State.REC || m == State.PAUSE) RecorderServiceCmd(RecorderService.A_SHOT);
                else if (m == State.IDLE) { closeMenu(); startCapture("shot"); }
                break;
            case "mic": {
                boolean on = !Prefs.b(this, Prefs.MIC, true);
                Prefs.put(this, Prefs.MIC, on);
                if (bMic != null) bMic.setImageResource(on ? R.drawable.ic_mic : R.drawable.ic_mic_off);
                toast(m == State.REC || m == State.PAUSE ? "Berlaku di rekaman berikutnya"
                        : on ? "Mikrofon nyala" : "Mikrofon mati");
                break;
            }
            case "hd":
                closeMenu();
                root.setAlpha(0.25f);
                break;
            default:
                break;
        }
    }

    private void RecorderServiceCmd(String a) {
        try { startService(new Intent(this, RecorderService.class).setAction(a)); } catch (Exception ignore) { }
    }

    private void startCapture(String mode) {
        try {
            startActivity(new Intent(this, CaptureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra("mode", mode));
        } catch (Exception e) {
            toast("Buka Zani sekali, lalu coba lagi");
        }
    }

    private void toast(String m) { Toast.makeText(getApplicationContext(), m, Toast.LENGTH_SHORT).show(); }

    // ---------------------------------------------------------------- sinkron status
    @Override
    public void changed() {
        if (root == null) return;
        int m = State.mode;
        boolean live = m == State.REC || m == State.PAUSE;
        ring.setStroke(Ui.dp(this, 2), live ? (m == State.PAUSE ? Ui.AMBER : Ui.REC) : 0xFFFFFFFF);
        box.invalidate();
        h.removeCallbacks(tick);
        if (live) {
            badge.setText(State.fmt(State.secs()));
            badge.setBackground(Ui.rr(this, m == State.PAUSE ? Ui.AMBER : Ui.REC, 8));
            badge.setVisibility(View.VISIBLE);
            if (m == State.REC) h.postDelayed(tick, 1000);
        } else {
            badge.setVisibility(View.GONE);
        }
        if (m == State.COUNT) { cdTv.setText(String.valueOf(State.count)); cdTv.setVisibility(View.VISIBLE); }
        else cdTv.setVisibility(View.GONE);
        if (bRec != null) {
            bRec.setImageResource(m == State.IDLE ? R.drawable.ic_rec : R.drawable.ic_stop);
            bRec.setBackground(Ui.circle(m == State.IDLE ? 0x24FFFFFF : Ui.REC));
        }
        if (bPs != null) bPs.setImageResource(m == State.PAUSE ? R.drawable.ic_play : R.drawable.ic_pause);
        if (m != State.IDLE) closeIfOpen();
        boolean hide = live && Prefs.b(this, Prefs.HIDEREC, false);
        root.setVisibility(hide ? View.GONE : View.VISIBLE);
    }
}
