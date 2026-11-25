package dev.pocketagent.app.ui.pixel.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetTapsTest {
    @Test fun sevenInChain() {
        val taps = PetTaps()
        var t = 5_000L
        for (n in 1..6) {
            val c = taps.tap(t)
            assertEquals(n, c.count)
            assertEquals(n, c.streak)
            assertFalse(c.overdrive)
            t += 1200L
        }
        val seventh = taps.tap(t)
        assertTrue(seventh.overdrive)
        assertEquals(7, seventh.count)
        assertEquals(0, seventh.streak)
    }

    @Test fun gapBreaksChain() {
        val taps = PetTaps()
        var t = 0L
        for (n in 1..6) {
            taps.tap(t)
            t += 1000L
        }
        // 1201 ms after the sixth tap (which was at t - 1000): the chain starts over
        val c = taps.tap(t - 1000L + 1201L)
        assertEquals(1, c.streak)
        assertFalse(c.overdrive)
        assertEquals(7, c.count)
    }

    @Test fun exactlyTheChainGapStillChains() {
        val taps = PetTaps()
        taps.tap(0L)
        assertEquals(2, taps.tap(1200L).streak)
        assertEquals(1, taps.tap(1200L + 1201L).streak)
    }

    @Test fun countKeepsCountingAcrossOverdrives() {
        val taps = PetTaps()
        var t = 0L
        repeat(7) {
            taps.tap(t)
            t += 100L
        }
        val eighth = taps.tap(t)
        assertEquals(8, eighth.count)
        assertEquals(1, eighth.streak)
        assertFalse(eighth.overdrive)
        // six more in a chain: the next overdrive is the 14th tap
        var last = eighth
        repeat(6) {
            t += 100L
            last = taps.tap(t)
        }
        assertTrue(last.overdrive)
        assertEquals(14, last.count)
    }

    @Test fun firstTapNeverChains() {
        for (start in listOf(0L, 500L, 1_000_000L)) {
            val c = PetTaps().tap(start)
            assertEquals(1, c.count)
            assertEquals(1, c.streak)
            assertFalse(c.overdrive)
        }
    }

    @Test fun customChainLength() {
        val taps = PetTaps(chainMs = 300L)
        taps.tap(0L)
        assertEquals(2, taps.tap(300L).streak)
        assertEquals(1, taps.tap(601L).streak)
    }
}
