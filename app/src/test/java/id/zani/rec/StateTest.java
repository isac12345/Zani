package id.zani.rec;

import org.junit.Test;
import static org.junit.Assert.*;

public class StateTest {
    @Test public void fmt_detik() { assertEquals("00:00", State.fmt(0)); }
    @Test public void fmt_menit() { assertEquals("01:05", State.fmt(65)); }
    @Test public void fmt_jam() { assertEquals("1:01:01", State.fmt(3661)); }

    @Test public void desc_tulis() {
        Caps.Cfg c = new Caps.Cfg();
        c.w = 1280; c.h = 720; c.fps = 30; c.bps = 6000000;
        assertEquals("1280x720 · 30 fps · 6.0 Mbps", c.desc());
    }
}
