package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireWindTest {
    @Test fun shiftTable() {
        assertEquals(3, FireWind.shift(0, 1f))
        assertEquals(2, FireWind.shift(11, 1f))
        assertEquals(0, FireWind.shift(22, 1f))
        assertEquals(-3, FireWind.shift(0, -1f))
        assertEquals(-2, FireWind.shift(11, -1f))
        assertEquals(0, FireWind.shift(22, -1f))
        // halves round away from zero, in both directions (not roundToInt: it would give -1 for -1.5)
        assertEquals(2, FireWind.shift(11, 1f))
        assertEquals(-2, FireWind.shift(11, -1f))
        // a wind under 0.1 shifts nothing anywhere
        for (row in -2..25) {
            assertEquals(0, FireWind.shift(row, 0.05f))
            assertEquals(0, FireWind.shift(row, -0.099f))
            assertEquals(0, FireWind.shift(row, 0f))
        }
        // outside the rows 0..22
        assertEquals(0, FireWind.shift(23, 1f))
        assertEquals(0, FireWind.shift(-1, 1f))
        assertEquals(0, FireWind.shift(35, -1f))
        // a NaN wind is no wind, and a wind beyond 1 is 1
        assertEquals(0, FireWind.shift(0, Float.NaN))
        assertEquals(3, FireWind.shift(0, 7f))
        assertEquals(-3, FireWind.shift(0, -7f))
    }

    @Test fun shiftNeverGrowsTowardsTheFeet() {
        for (wind in listOf(0.1f, 0.3f, 0.5f, 0.8f, 1f)) {
            var last = Int.MAX_VALUE
            for (row in 0..22) {
                val s = FireWind.shift(row, wind)
                assertTrue("wind $wind row $row", s in 0..3 && s <= last)
                last = s
                assertEquals(-s, FireWind.shift(row, -wind))
            }
        }
    }

    @Test fun totalClamps() {
        assertEquals(0.5f, FireWind.total(0.2f, 0.3f), 1e-6f)
        assertEquals(1f, FireWind.total(0.8f, 0.8f), 0f)
        assertEquals(-1f, FireWind.total(-0.8f, -0.8f), 0f)
        assertEquals(0f, FireWind.total(0.5f, -0.5f), 0f)
        assertEquals(0.4f, FireWind.total(Float.NaN, 0.4f), 0f)
        assertEquals(-0.4f, FireWind.total(-0.4f, Float.NaN), 0f)
    }

    @Test fun sparkDriftIsLinearInAge() {
        // the formula: w * 12 * gain * u
        assertEquals(0f, FireWind.sparkDrift(1f, 0f, 1f), 0f)
        assertEquals(12f, FireWind.sparkDrift(1f, 1f, 1f), 1e-5f)
        assertEquals(6f, FireWind.sparkDrift(1f, 0.5f, 1f), 1e-5f)
        assertEquals(9.6f, FireWind.sparkDrift(1f, 0.8f, 1f), 1e-5f)
        assertEquals(-4.8f, FireWind.sparkDrift(-0.5f, 0.8f, 1f), 1e-5f)
        assertEquals(15.6f, FireWind.sparkDrift(1f, 1f, 1.3f), 1e-4f)
        assertEquals(0f, FireWind.sparkDrift(0f, 0.7f, 1.3f), 0f)
        // linear: doubling the age doubles the drift
        assertEquals(2f * FireWind.sparkDrift(0.7f, 0.3f, 0.9f), FireWind.sparkDrift(0.7f, 0.6f, 0.9f), 1e-5f)
    }

    @Test fun tiltDeadzoneAndSaturation() {
        assertEquals(0f, TiltWind.fromAccelX(0f), 0f)
        assertEquals(0f, TiltWind.fromAccelX(1.0f), 0f)
        assertEquals(0f, TiltWind.fromAccelX(-1.0f), 0f)
        assertEquals(0f, TiltWind.fromAccelX(0.4f), 0f)
        assertEquals(-0.5f, TiltWind.fromAccelX(2.75f), 1e-6f)
        assertEquals(0.5f, TiltWind.fromAccelX(-2.75f), 1e-6f)
        assertEquals(-1f, TiltWind.fromAccelX(4.5f), 1e-6f)
        assertEquals(-1f, TiltWind.fromAccelX(9f), 0f)
        assertEquals(1f, TiltWind.fromAccelX(-4.5f), 1e-6f)
        assertEquals(1f, TiltWind.fromAccelX(-9.81f), 0f)
        assertEquals(0f, TiltWind.fromAccelX(Float.NaN), 0f)
    }

    @Test fun lowPassHalfAfterOneTau() {
        // dt 250 ms, tau 250 ms: alpha 0.5
        assertEquals(0.5f, TiltWind.smooth(0f, 1f, 250L, 250L), 1e-6f)
        assertEquals(0.5f, TiltWind.smooth(0f, 1f, 250L), 1e-6f)
        assertEquals(0.1f, TiltWind.smooth(0.3f, -0.1f, 250L), 1e-6f)
        // no time, no change; a long time nearly reaches the sample
        assertEquals(0.3f, TiltWind.smooth(0.3f, 1f, 0L), 0f)
        assertEquals(1f, TiltWind.smooth(0f, 1f, 100_000L), 1e-2f)
        // the target is never overshot
        var w = 0f
        repeat(50) { w = TiltWind.smooth(w, 0.7f, 33L); assertTrue(w <= 0.7f) }
        assertEquals(0.7f, w, 0.05f)
    }
}
