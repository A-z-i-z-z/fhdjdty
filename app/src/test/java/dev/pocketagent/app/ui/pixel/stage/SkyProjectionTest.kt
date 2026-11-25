package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyProjectionTest {
    private fun x(p: Int): Int = p and 0xFFFF

    private fun y(p: Int): Int = p ushr 16

    private fun visible(az: Double, alt: Double, facing: Float = 180f, width: Int = 155, horizon: Int = 52): Boolean =
        SkyProjection.project(az, alt, facing, width, horizon) != SkyProjection.NONE

    @Test fun centreAndSides() {
        val c = SkyProjection.project(180.0, 0.0, 180f, 155, 52)
        assertEquals(77, x(c))
        assertEquals(52, y(c))
        // facing south, west (az 270) is to the right and east (az 90) to the left
        val west = SkyProjection.project(270.0, 30.0, 180f, 155, 52)
        val east = SkyProjection.project(90.0, 30.0, 180f, 155, 52)
        assertTrue(x(west) > 77)
        assertTrue(x(east) < 77)
        // facing north the other way round: east (az 90) is to the right
        val eastN = SkyProjection.project(90.0, 30.0, 0f, 155, 52)
        val westN = SkyProjection.project(270.0, 30.0, 0f, 155, 52)
        assertTrue(x(eastN) > 77)
        assertTrue(x(westN) < 77)
    }

    @Test fun edges() {
        assertEquals(3, y(SkyProjection.project(180.0, 80.0, 180f, 155, 52)))
        assertEquals(SkyProjection.NONE, SkyProjection.project(180.0, -2.0, 180f, 155, 52))
        assertEquals(SkyProjection.NONE, SkyProjection.project(180.0, 80.5, 180f, 155, 52))
        assertTrue(visible(290.0, 10.0))
        assertEquals(154, x(SkyProjection.project(290.0, 10.0, 180f, 155, 52)))
        assertEquals(SkyProjection.NONE, SkyProjection.project(291.0, 10.0, 180f, 155, 52))
        assertTrue(visible(70.0, 10.0))
        assertEquals(0, x(SkyProjection.project(70.0, 10.0, 180f, 155, 52)))
        assertEquals(SkyProjection.NONE, SkyProjection.project(69.0, 10.0, 180f, 155, 52))
        // just below the horizon still shows (the ridges hide it), one row past the horizon at most
        assertEquals(53, y(SkyProjection.project(180.0, -1.0, 180f, 155, 52)))
    }

    @Test fun azimuthWraps() {
        // facing north (0): az 350 and az 10 are both close to the centre, either side
        val a = SkyProjection.project(350.0, 20.0, 0f, 155, 52)
        val b = SkyProjection.project(10.0, 20.0, 0f, 155, 52)
        assertTrue(x(a) < 77 && x(b) > 77)
        assertEquals((x(b) - 77).toDouble(), (77 - x(a)).toDouble(), 1.0)
    }

    @Test fun siriusOslo() {
        // Sirius at Oslo on 2027-01-15 20:00 UTC: azimuth 145.55, altitude 8.05
        val p = SkyProjection.project(145.55, 8.05, 180f, 155, 52)
        assertEquals(53, x(p))
        assertEquals(47, y(p))
    }

    @Test fun tinyStage() {
        assertEquals(SkyProjection.NONE, SkyProjection.project(180.0, 10.0, 180f, 155, 11))
        assertEquals(SkyProjection.NONE, SkyProjection.project(180.0, 10.0, 180f, 1, 52))
    }

    @Test fun helpers() {
        assertEquals(180f, SkyProjection.facingAz(41.0), 0f)
        assertEquals(180f, SkyProjection.facingAz(0.0), 0f)
        assertEquals(0f, SkyProjection.facingAz(-33.9), 0f)
        assertEquals(76, SkyProjection.horizonRow(100))
    }

    @Test fun pixelsPerDegreeAcrossAboutSevenTenths() {
        val a = x(SkyProjection.project(180.0, 20.0, 180f, 155, 52))
        val b = x(SkyProjection.project(190.0, 20.0, 180f, 155, 52))
        assertEquals(7.0, (b - a).toDouble(), 1.0)
    }
}
