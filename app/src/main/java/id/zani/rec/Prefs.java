package id.zani.rec;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String MIC = "mic", CD = "cd", RES = "res", FPS = "fps", BR = "br", ORI = "ori", CODEC = "codec",
            SAFE = "safe", HIDEREC = "hiderec", SNAP = "snap", SIZE = "size", ALPHA = "alpha",
            B_REC = "b_rec", B_PS = "b_ps", B_SS = "b_ss", B_MIC = "b_mic", B_HD = "b_hd",
            POSX = "px", POSY = "py", FLOAT_ON = "float_on", INT = "int_audio", APPS = "apps";

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("zani", Context.MODE_PRIVATE);
    }

    static boolean b(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }

    static String s(Context c, String k, String d) { return p(c).getString(k, d); }

    static void putS(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }

    static int i(Context c, String k, int d) { return p(c).getInt(k, d); }

    static void put(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }

    static void put(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
}
