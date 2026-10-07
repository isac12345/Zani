package id.zani.rec;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

/** Activity transparan: hanya untuk meminta izin rekam layar dari sistem, lalu menyerahkan ke RecorderService. */
public class CaptureActivity extends Activity {
    private String mode = "rec";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String m = getIntent().getStringExtra("mode");
        if (m != null) mode = m;
        if (State.mode != State.IDLE) { finish(); return; }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), 7);
        } catch (Exception e) {
            Toast.makeText(this, "Rekam layar tidak tersedia", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int rq, int rc, Intent data) {
        super.onActivityResult(rq, rc, data);
        if (rq == 7 && rc == RESULT_OK && data != null) {
            Intent s = new Intent(this, RecorderService.class)
                    .setAction(RecorderService.A_BEGIN)
                    .putExtra("rc", rc)
                    .putExtra("data", data)
                    .putExtra("mode", mode);
            try {
                startForegroundService(s);
            } catch (Exception e) {
                Toast.makeText(this, "Gagal memulai layanan rekam", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Dibatalkan", Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
