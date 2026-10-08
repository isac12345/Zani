package id.zani.rec;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/** Mencatat error fatal ke berkas supaya bisa dilihat di Setelan > Laporan error terakhir. */
public class ZaniApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                FileOutputStream f = new FileOutputStream(new File(getFilesDir(), "crash.txt"));
                f.write((new Date() + " [" + t.getName() + "]\n" + sw).getBytes());
                f.close();
            } catch (Throwable ignore) { }
            if (def != null) def.uncaughtException(t, e);
        });
    }
}
