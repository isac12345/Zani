package id.zani.rec;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Locale;

/** Status rekaman bersama (semua di main thread). */
final class State {
    static final int IDLE = 0, COUNT = 1, REC = 2, PAUSE = 3;
    static int mode = IDLE;
    static int count;
    static long base;     // elapsedRealtime saat mulai (dikurangi durasi jeda)
    static long pauseAt;

    interface L { void changed(); }

    private static final ArrayList<L> LS = new ArrayList<>();

    static void add(L l) { if (!LS.contains(l)) LS.add(l); }

    static void rm(L l) { LS.remove(l); }

    static void fire() {
        for (L l : new ArrayList<>(LS)) l.changed();
    }

    static int secs() {
        long e = mode == PAUSE ? pauseAt - base : SystemClock.elapsedRealtime() - base;
        return (int) Math.max(0, e / 1000);
    }

    static String fmt(int s) {
        int h = s / 3600, m = (s % 3600) / 60, x = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, x) : String.format(Locale.US, "%02d:%02d", m, x);
    }
}
