package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EclipseTableTest {
    private fun wrap180(x: Double): Double = x - 360.0 * Math.floor((x + 180.0) / 360.0)

    @Test fun tableIsSortedAndComplete() {
        val t = Eclipses.TABLE
        assertEquals(24, t.size)
        for (i in 1 until t.size) assertTrue("sorted at $i", t[i].epochMs > t[i - 1].epochMs)
        assertEquals(12, t.count { it.lunar })
        assertEquals(12, t.count { !it.lunar })
        for (e in t) assertTrue(e.kind in 0..2)
        assertTrue("solar eclipses are never penumbral", t.filter { !it.lunar }.all { it.kind >= 1 })
        // from 2026 to the end of 2030
        assertTrue(t.first().epochMs >= SkyTestKit.utc(2026, 1, 1))
        assertTrue(t.last().epochMs < SkyTestKit.utc(2031, 1, 1))
    }

    @Test fun solarEclipsesAtNewMoonAndLunarOnesAtFullMoon() {
        // the moon's longitude is within a degree of the sun's (solar) or opposite it (lunar) at the greatest moment
        for (e in Eclipses.TABLE) {
            val elong = SkyClock.elongationDeg(e.epochMs)
            val target = if (e.lunar) 180.0 else 0.0
            val off = Math.abs(wrap180(elong - target))
            assertTrue("eclipse at ${e.epochMs} (lunar=${e.lunar}) is $off degrees from its syzygy", off < 1.0)
        }
    }

    private fun first(lunar: Boolean, kind: Int): Eclipse = Eclipses.TABLE.first { it.lunar == lunar && it.kind == kind }

    @Test fun redMoonWindow() {
        val total = first(true, 2)
        assertEquals(1f, Eclipses.redAt(total.epochMs), 1e-6f)
        assertEquals(0.5f, Eclipses.redAt(total.epochMs + 30 * 60_000L), 1e-4f)
        assertEquals(0.5f, Eclipses.redAt(total.epochMs - 30 * 60_000L), 1e-4f)
        assertEquals(0f, Eclipses.redAt(total.epochMs + 60 * 60_000L), 0f)
        assertEquals(0f, Eclipses.redAt(total.epochMs - 61 * 60_000L), 0f)
        val partial = first(true, 1)
        assertEquals(1f, Eclipses.redAt(partial.epochMs), 1e-6f)
        assertEquals(0.5f, Eclipses.redAt(partial.epochMs + 20 * 60_000L), 1e-4f)
        assertEquals(0f, Eclipses.redAt(partial.epochMs + 40 * 60_000L), 0f)
    }

    @Test fun penumbralAndSolarEclipsesLeaveTheMoonPale() {
        assertEquals(0f, Eclipses.redAt(first(true, 0).epochMs), 0f)
        for (e in Eclipses.TABLE.filter { !it.lunar }) assertEquals(0f, Eclipses.redAt(e.epochMs), 0f)
    }

    @Test fun outsideEveryWindowThereIsNoRedMoon() {
        assertEquals(0f, Eclipses.redAt(SkyTestKit.utc(2027, 5, 5)), 0f)
        assertEquals(0f, Eclipses.redAt(SkyTestKit.utc(2035, 1, 1)), 0f)
        assertEquals(0f, Eclipses.redAt(SkyTestKit.utc(2020, 1, 1)), 0f)
    }
}
