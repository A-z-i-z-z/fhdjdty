package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SkyMomentsTest {
    private fun assertMoment(m: MomentBlend, a: SkyMoment, b: SkyMoment, t: Float) {
        assertEquals(a, m.a)
        assertEquals(b, m.b)
        assertEquals(t, m.t, 1e-6f)
    }

    @Test fun keysMorningEvening() {
        assertMoment(SkyMoments.at(-20.0, false), SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
        assertMoment(SkyMoments.at(-18.0, false), SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
        assertMoment(SkyMoments.at(-9.0, false), SkyMoment.DAWN, SkyMoment.SUNRISE, 0f)
        assertMoment(SkyMoments.at(-0.8, false), SkyMoment.SUNRISE, SkyMoment.DAY, 0f)
        assertMoment(SkyMoments.at(12.0, false), SkyMoment.DAY, SkyMoment.DAY, 0f)
        assertMoment(SkyMoments.at(15.0, false), SkyMoment.DAY, SkyMoment.DAY, 0f)
        assertMoment(SkyMoments.at(14.0, true), SkyMoment.DAY, SkyMoment.DAY, 0f)
        assertMoment(SkyMoments.at(6.0, true), SkyMoment.GOLDEN, SkyMoment.SUNSET, 0f)
        assertMoment(SkyMoments.at(-0.8, true), SkyMoment.SUNSET, SkyMoment.DUSK, 0f)
        assertMoment(SkyMoments.at(-8.0, true), SkyMoment.DUSK, SkyMoment.NIGHT, 0f)
        assertMoment(SkyMoments.at(-18.0, true), SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
        // between two keys: the middle of a segment is exactly half
        assertEquals(0.5f, SkyMoments.at(-4.9, false).t, 1e-4f)
        assertEquals(SkyMoment.DAWN, SkyMoments.at(-4.9, false).a)
        assertEquals(0.5f, SkyMoments.at(10.0, true).t, 1e-4f)
        assertEquals(SkyMoment.DAY, SkyMoments.at(10.0, true).a)
    }

    private fun weights(m: MomentBlend): Map<SkyMoment, Float> {
        val out = HashMap<SkyMoment, Float>()
        out[m.a] = 1f - m.t
        out[m.b] = (out[m.b] ?: 0f) + m.t
        return out
    }

    @Test fun continuous() {
        // the pair (a, b, t) changes at a key, the look does not: compare the weight of each of the seven moments
        for (evening in listOf(false, true)) {
            var prev: Map<SkyMoment, Float>? = null
            var alt = -25.0
            while (alt <= 25.0) {
                val m = SkyMoments.at(alt, evening)
                assertTrue("blend in 0..1 at $alt", m.t >= 0f && m.t <= 1f)
                val w = weights(m)
                val p = prev
                if (p != null) {
                    for (k in SkyMoment.values()) {
                        val d = Math.abs((w[k] ?: 0f) - (p[k] ?: 0f))
                        assertTrue("weight of $k jumps by $d at $alt (evening=$evening)", d <= 0.08f)
                    }
                }
                prev = w
                alt += 0.05
            }
        }
    }

    private fun sunAlt(lat: Double, lon: Double, y: Int, mo: Int, d: Int, h: Int, mi: Int): Double {
        val t = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()
        val s = SkyClock.sun(t)
        return SkyClock.altAz(s.raDeg, s.decDeg, SkyClock.lstDeg(t, lon), lat).altDeg
    }

    @Test fun istanbulEquinoxSequence() {
        val lat = 41.0082
        val lon = 28.9784
        val alt0330 = sunAlt(lat, lon, 2027, 3, 21, 3, 30)
        assertEquals(-7.72, alt0330, 0.02)
        val m0330 = SkyMoments.at(alt0330, false)
        assertEquals(SkyMoment.DAWN, m0330.a)
        assertEquals(SkyMoment.SUNRISE, m0330.b)
        assertTrue(m0330.t < 0.15f)

        val alt0406 = sunAlt(lat, lon, 2027, 3, 21, 4, 6)
        assertEquals(-0.94, alt0406, 0.02)
        val m0406 = SkyMoments.at(alt0406, false)
        assertEquals(SkyMoment.SUNRISE, if (m0406.t > 0.9f) m0406.b else m0406.a)

        val alt0440 = sunAlt(lat, lon, 2027, 3, 21, 4, 40)
        assertEquals(5.48, alt0440, 0.02)
        val m0440 = SkyMoments.at(alt0440, false)
        assertEquals(SkyMoment.SUNRISE, m0440.a)
        assertEquals(SkyMoment.DAY, m0440.b)
        assertTrue(m0440.t > 0.3f && m0440.t < 0.7f)

        val alt1500 = sunAlt(lat, lon, 2027, 3, 21, 15, 0)
        assertEquals(13.56, alt1500, 0.02)
        val m1500 = SkyMoments.at(alt1500, true)
        assertEquals(SkyMoment.DAY, m1500.a)
        assertEquals(SkyMoment.GOLDEN, m1500.b)
        assertTrue(m1500.t < 0.1f)

        val alt1616 = sunAlt(lat, lon, 2027, 3, 21, 16, 16)
        assertEquals(-0.68, alt1616, 0.02)
        val m1616 = SkyMoments.at(alt1616, true)
        assertEquals(SkyMoment.GOLDEN, m1616.a)
        assertEquals(SkyMoment.SUNSET, m1616.b)
        assertTrue(m1616.t > 0.9f)

        val alt1700 = sunAlt(lat, lon, 2027, 3, 21, 17, 0)
        assertEquals(-8.94, alt1700, 0.02)
        val m1700 = SkyMoments.at(alt1700, true)
        assertEquals(SkyMoment.DUSK, m1700.a)
        assertEquals(SkyMoment.NIGHT, m1700.b)
        assertTrue(m1700.t < 0.1f)

        val alt1730 = sunAlt(lat, lon, 2027, 3, 21, 17, 30)
        assertEquals(-14.49, alt1730, 0.02)
        val m1730 = SkyMoments.at(alt1730, true)
        assertEquals(SkyMoment.DUSK, m1730.a)
        assertEquals(SkyMoment.NIGHT, m1730.b)
        assertTrue(m1730.t > 0.5f && m1730.t < 0.9f)
    }
}
