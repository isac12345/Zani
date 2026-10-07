package id.zani.rec;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Perekam layar. Sengaja sederhana supaya tidak membebani game:
 * MediaRecorder (encoder hardware) menerima frame langsung dari VirtualDisplay lewat Surface,
 * jadi tidak ada penyalinan frame di CPU dan tidak ada thread tambahan selama merekam.
 */
public class RecorderService extends Service {
    static final String A_BEGIN = "id.zani.rec.BEGIN", A_PAUSE = "id.zani.rec.PAUSE",
            A_STOP = "id.zani.rec.STOP", A_SHOT = "id.zani.rec.SHOT";
    private static final int NID = 11, NDONE = 12;
    private static final String CH = "rec", CHD = "done";
    private static final int PIF = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaProjection proj;
    private MediaProjection.Callback pcb;
    private MediaRecorder rec;
    private VirtualDisplay vd;
    private Surface recSurface;
    private Uri outUri;
    private File outFile;
    private ParcelFileDescriptor outPfd;
    private Caps.Cfg cfg;
    private boolean micUse, shotOnly, shotBusy, closed;
    private ImageReader ir;
    private HandlerThread ht;
    private final Runnable shotTimeout = () -> {
        restoreSurface();
        toast("Screenshot gagal, coba lagi");
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int f, int id) {
        String a = in == null ? null : in.getAction();
        if (a == null) { stopSelf(); return START_NOT_STICKY; }
        switch (a) {
            case A_BEGIN: begin(in); break;
            case A_PAUSE: togglePause(); break;
            case A_STOP: stopRec(); break;
            case A_SHOT: shot(); break;
            default: break;
        }
        return START_NOT_STICKY;
    }

    // ---------------------------------------------------------------- mulai
    private void begin(Intent in) {
        closed = false;
        int rc = in.getIntExtra("rc", 0);
        Intent data = in.getParcelableExtra("data");
        shotOnly = "shot".equals(in.getStringExtra("mode"));
        micUse = !shotOnly && Prefs.b(this, Prefs.MIC, true)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        try {
            startFg(micUse);
        } catch (Exception e) {
            fail("Tidak bisa memulai layanan rekam");
            return;
        }
        if (data == null || (State.mode != State.IDLE && !shotOnly)) { fail("Izin rekam layar tidak valid"); return; }
        try {
            proj = ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(rc, data);
            pcb = new MediaProjection.Callback() {
                @Override public void onStop() { h.post(() -> { if (State.mode >= State.REC) stopRec(); else cleanup(); }); }
            };
            proj.registerCallback(pcb, h);
        } catch (Exception e) {
            fail("Izin rekam layar ditolak sistem");
            return;
        }
        if (shotOnly) { oneShot(); return; }
        if (freeBytes() < 150L * 1024 * 1024) { fail("Penyimpanan hampir penuh"); return; }
        if (Prefs.b(this, Prefs.CD, true)) countdown(3); else startRec();
    }

    private void startFg(boolean mic) {
        Notification n = notif();
        if (Build.VERSION.SDK_INT >= 29) {
            int t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (mic && Build.VERSION.SDK_INT >= 30) t |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NID, n, t);
        } else {
            startForeground(NID, n);
        }
    }

    private void countdown(final int n) {
        State.mode = State.COUNT;
        State.count = n;
        State.fire();
        h.postDelayed(() -> {
            if (State.mode != State.COUNT) return;
            if (n <= 1) startRec(); else countdown(n - 1);
        }, 1000);
    }

    private void startRec() {
        cfg = Caps.resolve(this);
        boolean ok = false;
        String err = "";
        for (int tries = 0; tries < 5 && cfg != null && !ok; tries++) {
            try {
                ok = tryStart(cfg);
            } catch (Exception e) {
                err = String.valueOf(e.getMessage());
                releaseRecorder(false);
                cfg = Caps.degrade(cfg);
            }
        }
        if (!ok || cfg == null) { fail("Gagal memulai rekaman: " + err); return; }
        State.base = SystemClock.elapsedRealtime();
        State.mode = State.REC;
        State.fire();
        refreshNotif();
        toast(cfg.desc());
    }

    private boolean tryStart(Caps.Cfg c) throws Exception {
        openOutput();
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        rec = r;
        if (micUse) r.setAudioSource(MediaRecorder.AudioSource.MIC);
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        r.setOutputFile(outPfd.getFileDescriptor());
        r.setVideoEncoder(c.hevc ? MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
        r.setVideoSize(c.w, c.h);
        r.setVideoFrameRate(c.fps);
        r.setVideoEncodingBitRate(c.bps);
        if (micUse) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
        }
        long free = freeBytes();
        if (free > 0 && free < Long.MAX_VALUE / 2) r.setMaxFileSize(Math.max(50L << 20, free - (120L << 20)));
        r.setOnInfoListener((mr, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                    || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) h.post(this::stopRec);
        });
        r.setOnErrorListener((mr, what, extra) -> h.post(this::stopRec));
        r.prepare();
        recSurface = r.getSurface();
        int dpi = getResources().getDisplayMetrics().densityDpi;
        if (vd == null) {
            vd = proj.createVirtualDisplay("Zani", c.w, c.h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recSurface, null, null);
        } else {
            vd.resize(c.w, c.h, dpi);
            vd.setSurface(recSurface);
        }
        r.start();
        return true;
    }

    // ---------------------------------------------------------------- jeda / stop
    private void togglePause() {
        try {
            if (State.mode == State.REC) {
                rec.pause();
                State.pauseAt = SystemClock.elapsedRealtime();
                State.mode = State.PAUSE;
            } else if (State.mode == State.PAUSE) {
                rec.resume();
                State.base += SystemClock.elapsedRealtime() - State.pauseAt;
                State.mode = State.REC;
            } else {
                return;
            }
        } catch (Exception e) {
            toast("Jeda tidak didukung di HP ini");
            return;
        }
        State.fire();
        refreshNotif();
    }

    private void stopRec() {
        if (State.mode == State.IDLE || State.mode == State.COUNT || rec == null) { cleanup(); return; }
        boolean valid = true;
        try { rec.stop(); } catch (Exception e) { valid = false; }
        releaseRecorder(valid);
        State.mode = State.IDLE;
        State.fire();
        if (valid) {
            toast("Tersimpan di Movies/Zani");
            doneNotif();
        } else {
            toast("Rekaman terlalu singkat");
        }
        cleanup();
    }

    private void releaseRecorder(boolean keep) {
        if (rec != null) {
            try { rec.reset(); } catch (Exception ignore) { }
            try { rec.release(); } catch (Exception ignore) { }
            rec = null;
        }
        if (recSurface != null) { try { recSurface.release(); } catch (Exception ignore) { } recSurface = null; }
        closeOutput(keep);
    }

    private void cleanup() {
        if (closed) return;
        closed = true;
        h.removeCallbacksAndMessages(null);
        if (rec != null) releaseRecorder(false);
        if (vd != null) { try { vd.release(); } catch (Exception ignore) { } vd = null; }
        if (ir != null) { try { ir.close(); } catch (Exception ignore) { } ir = null; }
        if (ht != null) { ht.quitSafely(); ht = null; }
        if (proj != null) {
            try { if (pcb != null) proj.unregisterCallback(pcb); } catch (Exception ignore) { }
            try { proj.stop(); } catch (Exception ignore) { }
            proj = null;
        }
        State.mode = State.IDLE;
        State.fire();
        stopForeground(true);
        stopSelf();
    }

    private void fail(String m) {
        toast(m);
        closeOutput(false);
        cleanup();
    }

    @Override
    public void onDestroy() {
        if (!closed) cleanup();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- berkas keluaran
    private static String ts() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }

    private void openOutput() throws Exception {
        closeOutput(false);
        String name = "Zani_" + ts() + ".mp4";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Zani");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            outUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (outUri == null) throw new IOException("MediaStore menolak berkas");
            outPfd = getContentResolver().openFileDescriptor(outUri, "rw");
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Zani");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            outFile = new File(dir, name);
            outPfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
    }

    private void closeOutput(boolean keep) {
        if (outPfd != null) { try { outPfd.close(); } catch (Exception ignore) { } outPfd = null; }
        if (outUri != null) {
            try {
                if (keep) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(outUri, v, null, null);
                    lastUri = outUri;
                } else {
                    getContentResolver().delete(outUri, null, null);
                }
            } catch (Exception ignore) { }
            outUri = null;
        }
        if (outFile != null) {
            if (keep) {
                MediaScannerConnection.scanFile(this, new String[]{outFile.getPath()}, null, null);
                lastUri = Uri.fromFile(outFile);
            } else {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            outFile = null;
        }
    }

    private Uri lastUri;

    private static long freeBytes() {
        try {
            return new StatFs(Environment.getExternalStorageDirectory().getPath()).getAvailableBytes();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    // ---------------------------------------------------------------- screenshot
    /** Saat merekam: pinjam VirtualDisplay sesaat ke ImageReader, lalu kembalikan ke perekam. */
    private void shot() {
        if (vd == null || recSurface == null || cfg == null || shotBusy
                || (State.mode != State.REC && State.mode != State.PAUSE)) return;
        shotBusy = true;
        final int w = cfg.w, hh = cfg.h;
        ht = new HandlerThread("zani-shot", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        ir = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2);
        ir.setOnImageAvailableListener(r -> {
            Image im = null;
            try {
                im = r.acquireLatestImage();
                if (im == null) return;
                r.setOnImageAvailableListener(null, null);
                final Bitmap bm = toBitmap(im, w, hh);
                im.close();
                im = null;
                h.post(this::restoreSurface);
                saveBitmap(bm);
            } catch (Throwable t) {
                h.post(this::restoreSurface);
            } finally {
                if (im != null) { try { im.close(); } catch (Exception ignore) { } }
            }
        }, new Handler(ht.getLooper()));
        try {
            vd.setSurface(ir.getSurface());
        } catch (Exception e) {
            restoreSurface();
            return;
        }
        h.postDelayed(shotTimeout, 2500);
    }

    private void restoreSurface() {
        h.removeCallbacks(shotTimeout);
        try { if (vd != null && recSurface != null) vd.setSurface(recSurface); } catch (Exception ignore) { }
        if (ir != null) { try { ir.close(); } catch (Exception ignore) { } ir = null; }
        if (ht != null) { ht.quitSafely(); ht = null; }
        shotBusy = false;
    }

    /** Screenshot saat tidak merekam: sesi sekali pakai. */
    private void oneShot() {
        DisplayMetrics dm = new DisplayMetrics();
        Caps.display(this).getRealMetrics(dm);
        final int w = dm.widthPixels, hh = dm.heightPixels;
        ht = new HandlerThread("zani-shot", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        ir = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2);
        ir.setOnImageAvailableListener(r -> {
            Image im = null;
            try {
                im = r.acquireLatestImage();
                if (im == null) return;
                r.setOnImageAvailableListener(null, null);
                Bitmap bm = toBitmap(im, w, hh);
                im.close();
                im = null;
                saveBitmap(bm);
            } catch (Throwable ignore) {
            } finally {
                if (im != null) { try { im.close(); } catch (Exception ignore2) { } }
            }
            h.post(this::cleanup);
        }, new Handler(ht.getLooper()));
        try {
            vd = proj.createVirtualDisplay("ZaniShot", w, hh, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, null);
        } catch (Exception e) {
            fail("Screenshot gagal");
            return;
        }
        h.postDelayed(this::cleanup, 3500);
    }

    private static Bitmap toBitmap(Image im, int w, int hh) {
        Image.Plane p = im.getPlanes()[0];
        ByteBuffer b = p.getBuffer();
        int ps = p.getPixelStride(), rs = p.getRowStride();
        int pad = rs - ps * w;
        Bitmap bm = Bitmap.createBitmap(w + pad / ps, hh, Bitmap.Config.ARGB_8888);
        bm.copyPixelsFromBuffer(b);
        if (pad > 0) {
            Bitmap c = Bitmap.createBitmap(bm, 0, 0, w, hh);
            bm.recycle();
            return c;
        }
        return bm;
    }

    private void saveBitmap(Bitmap bm) {
        String name = "Zani_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".jpg";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Zani");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new IOException("insert");
                OutputStream os = getContentResolver().openOutputStream(u);
                bm.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.close();
                v.clear();
                v.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(u, v, null, null);
            } else {
                File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Zani");
                //noinspection ResultOfMethodCallIgnored
                d.mkdirs();
                File f = new File(d, name);
                FileOutputStream os = new FileOutputStream(f);
                bm.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.close();
                MediaScannerConnection.scanFile(this, new String[]{f.getPath()}, null, null);
            }
            toastBg("Screenshot tersimpan");
        } catch (Exception e) {
            toastBg("Gagal menyimpan screenshot");
        } finally {
            bm.recycle();
        }
    }

    // ---------------------------------------------------------------- notifikasi & toast
    private Notification notif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Perekaman", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PIF));
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        boolean live = State.mode == State.REC || State.mode == State.PAUSE;
        if (live) {
            b.setContentTitle(State.mode == State.PAUSE ? "Rekaman dijeda" : "Merekam layar");
            b.setShowWhen(true);
            b.setUsesChronometer(State.mode == State.REC);
            b.setWhen(System.currentTimeMillis() - State.secs() * 1000L);
            b.addAction(act(State.mode == State.PAUSE ? "Lanjut" : "Jeda", A_PAUSE, 1));
            b.addAction(act("Stop", A_STOP, 2));
            b.addAction(act("Screenshot", A_SHOT, 3));
        } else {
            b.setContentTitle(shotOnly ? "Mengambil screenshot" : "Menyiapkan rekaman");
        }
        return b.build();
    }

    private Notification.Action act(String t, String a, int rc) {
        PendingIntent pi = PendingIntent.getService(this, rc, new Intent(this, RecorderService.class).setAction(a), PIF);
        return new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), t, pi).build();
    }

    private void refreshNotif() {
        try { getSystemService(NotificationManager.class).notify(NID, notif()); } catch (Exception ignore) { }
    }

    private void doneNotif() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHD, "Rekaman tersimpan", NotificationManager.IMPORTANCE_LOW));
            Intent i = new Intent(this, PlayerActivity.class).setData(lastUri);
            Notification n = new Notification.Builder(this, CHD)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Rekaman tersimpan")
                    .setContentText("Ketuk untuk memutar")
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(this, 5, i, PIF))
                    .build();
            if (lastUri != null) nm.notify(NDONE, n);
        } catch (Exception ignore) { }
    }

    private void toast(String m) { Toast.makeText(getApplicationContext(), m, Toast.LENGTH_SHORT).show(); }

    private void toastBg(final String m) { h.post(() -> toast(m)); }
}
