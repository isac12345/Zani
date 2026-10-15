package id.zani.rec;

import org.junit.Test;
import static org.junit.Assert.*;

public class MoreTest {
    private static Caps.Cfg cfg(int w, int h, int fps, boolean hevc) {
        Caps.Cfg c = new Caps.Cfg();
        c.w = w; c.h = h; c.fps = fps; c.bps = 6000000; c.hevc = hevc;
        return c;
    }

    @Test public void degrade_fps120_jadi60() {
        assertEquals(60, Caps.degrade(cfg(1280, 720, 120, false)).fps);
    }
    @Test public void degrade_fps45_jadi30() {
        assertEquals(30, Caps.degrade(cfg(1280, 720, 45, false)).fps);
    }
    @Test public void degrade_hevc_dimatikan() {
        assertFalse(Caps.degrade(cfg(1280, 720, 30, true)).hevc);
    }
    @Test public void degrade_ukuran_mengecil() {
        Caps.Cfg n = Caps.degrade(cfg(1280, 720, 30, false));
        assertEquals(960, n.w);
        assertEquals(540, n.h);
    }
    @Test public void degrade_mentok_return_null() {
        assertNull(Caps.degrade(cfg(480, 360, 30, false)));
    }

    @Test public void listener_dipanggil_dan_bisa_dihapus() {
        final int[] hit = {0};
        State.L a = () -> hit[0]++;
        State.L b = () -> hit[0]++;
        State.add(a); State.add(b);
        State.fire();
        assertEquals(2, hit[0]);
        State.rm(a);
        State.fire();
        assertEquals(3, hit[0]);
        State.rm(b);
    }
}
