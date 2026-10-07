package id.zani.rec;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

/** Pemutar sederhana bawaan framework (tanpa library tambahan). */
public class PlayerActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final VideoView vv = new VideoView(this);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        f.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        setContentView(f);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);
        vv.setOnPreparedListener(m -> vv.start());
        vv.setOnErrorListener((m, w, e) -> {
            Toast.makeText(this, "Video tidak bisa diputar", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });
        if (getIntent().getData() != null) vv.setVideoURI(getIntent().getData()); else finish();
    }
}
