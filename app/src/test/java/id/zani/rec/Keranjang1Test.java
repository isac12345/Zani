package id.zani.rec;

import org.junit.Test;
import static org.junit.Assert.*;

public class Keranjang1Test {
    @Test public void even_ganjil_jadi_genap() {
        assertEquals(1280, Caps.even(1281));
        assertEquals(720, Caps.even(720));
        assertEquals(2, Caps.even(0));
    }
    @Test public void software_ketahuan_palsu() {
        assertTrue(Caps.software("OMX.google.h264.encoder"));
        assertTrue(Caps.software("c2.android.avc.encoder"));
        assertFalse(Caps.software("OMX.qcom.video.encoder.avc"));
    }
    @Test public void clip_jaga_suara() {
        assertEquals(32767, AudioCapture.clip(99999));
        assertEquals(-32768, AudioCapture.clip(-99999));
        assertEquals(1000, AudioCapture.clip(1000));
    }
}
