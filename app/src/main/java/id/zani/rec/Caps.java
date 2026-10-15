package id.zani.rec;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;

/** Menghitung konfigurasi rekam yang aman untuk HP ini (encoder hardware + refresh rate layar). */
final class Caps {
    static final class Cfg {
        int w, h, fps, bps;
        boolean hevc;
        String note = "";

        String desc() {
            return w + "x" + h + " · " + fps + " fps · " + String.format(java.util.Locale.US, "%.1f", bps / 1e6) + " Mbps"
                    + (hevc ? " · HEVC" : "") + note;
        }
    }

    static int even(int v) { return Math.max(2, v & ~1); }

    static Display display(Context c) {
        return ((DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
    }

    static int maxHz(Context c) {
        float m = 60f;
        try {
            Display d = display(c);
            m = d.getRefreshRate();
            for (Display.Mode x : d.getSupportedModes()) m = Math.max(m, x.getRefreshRate());
        } catch (Exception ignore) { }
        return Math.round(m);
    }

    private static boolean hasEncoder(String mime) {
        try {
            for (MediaCodecInfo i : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!i.isEncoder()) continue;
                for (String t : i.getSupportedTypes()) if (t.equalsIgnoreCase(mime)) return true;
            }
        } catch (Exception ignore) { }
        return false;
    }

    static boolean software(String n) {
        n = n.toLowerCase(java.util.Locale.US);
        return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.");
    }

    /** Cari encoder (hardware dulu) yang mendukung ukuran tsb. */
    private static MediaCodecInfo.VideoCapabilities find(String mime, int w, int h) {
        MediaCodecInfo.VideoCapabilities soft = null;
        try {
            for (MediaCodecInfo i : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!i.isEncoder()) continue;
                for (String t : i.getSupportedTypes()) {
                    if (!t.equalsIgnoreCase(mime)) continue;
                    MediaCodecInfo.VideoCapabilities vc = i.getCapabilitiesForType(mime).getVideoCapabilities();
                    if (vc == null || !vc.isSizeSupported(w, h)) continue;
                    if (!software(i.getName())) return vc;
                    if (soft == null) soft = vc;
                }
            }
        } catch (Exception ignore) { }
        return soft;
    }

    static Cfg resolve(Context c) {
        DisplayMetrics dm = new DisplayMetrics();
        display(c).getRealMetrics(dm);
        int sw = dm.widthPixels, sh = dm.heightPixels;
        int ori = Prefs.i(c, Prefs.ORI, 0);
        if ((ori == 1 && sw > sh) || (ori == 2 && sh > sw)) { int t = sw; sw = sh; sh = t; }
        Cfg o = new Cfg();
        int res = Prefs.i(c, Prefs.RES, 720);
        int sd = Math.min(sw, sh);
        if (res > 0 && res < sd) {
            float s = (float) res / sd;
            o.w = even(Math.round(sw * s));
            o.h = even(Math.round(sh * s));
        } else {
            o.w = even(sw);
            o.h = even(sh);
        }
        o.fps = Math.max(10, Prefs.i(c, Prefs.FPS, 30));
        boolean wantHevc = Prefs.i(c, Prefs.CODEC, 0) == 1;
        o.hevc = wantHevc && hasEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC);
        int br = Prefs.i(c, Prefs.BR, 0);
        String mime = o.hevc ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
        MediaCodecInfo.VideoCapabilities vc = null;
        if (Prefs.b(c, Prefs.SAFE, true)) {
            int w = o.w, h = o.h;
            for (int i = 0; i < 8 && vc == null; i++) {
                vc = find(mime, w, h);
                if (vc == null) { w = even(w * 3 / 4); h = even(h * 3 / 4); }
            }
            if (vc != null && (w != o.w || h != o.h)) { o.w = w; o.h = h; o.note += " · resolusi dibatasi encoder"; }
            int hz = maxHz(c);
            int f = o.fps;
            if (f > hz) f = hz;
            if (vc != null) {
                try {
                    Range<Double> r = vc.getSupportedFrameRatesFor(o.w, o.h);
                    f = (int) Math.min(f, Math.floor(r.getUpper()));
                } catch (Exception ignore) { }
            }
            f = Math.max(24, f);
            if (f < o.fps) o.note += " · fps dibatasi HP";
            o.fps = f;
        }
        double bpp = o.hevc ? 0.045 : 0.07;
        long bps = br > 0 ? br * 1000000L : (long) (o.w * (double) o.h * o.fps * bpp);
        bps = Math.max(1500000L, Math.min(bps, br > 0 ? 100000000L : 30000000L));
        if (vc != null) {
            try { bps = Math.min(bps, vc.getBitrateRange().getUpper()); } catch (Exception ignore) { }
        }
        o.bps = (int) bps;
        return o;
    }

    /** Turunkan konfigurasi satu tingkat kalau encoder menolak. Return null jika sudah habis. */
    static Cfg degrade(Cfg c) {
        Cfg n = new Cfg();
        n.w = c.w; n.h = c.h; n.fps = c.fps; n.bps = c.bps; n.hevc = c.hevc; n.note = c.note;
        if (n.fps > 60) { n.fps = 60; }
        else if (n.fps > 30) { n.fps = 30; }
        else if (n.hevc) { n.hevc = false; }
        else {
            n.w = even(n.w * 3 / 4); n.h = even(n.h * 3 / 4);
            n.bps = (int) (n.bps * 0.7);
            if (Math.min(n.w, n.h) < 360) return null;
        }
        n.note += " · otomatis diturunkan";
        return n;
    }
}
