package id.zani.rec;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile Pengaturan Cepat: nyalakan/matikan tombol melayang. */
public class ZaniTile extends TileService {
    @Override public void onStartListening() { upd(); }

    private void upd() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(FloatingService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (FloatingService.running) {
            FloatingService.stopAll(this);
        } else if (Settings.canDrawOverlays(this)) {
            try { startForegroundService(new Intent(this, FloatingService.class)); } catch (Exception ignore) { }
        } else {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::upd, 500);
    }
}
