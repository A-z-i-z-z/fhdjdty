package dev.pocketagent.app.ui.home

import dev.pocketagent.app.ui.pixel.HearthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FireSwitchTest {
    @Test fun tapTable() {
        assertEquals(FireAction.LIGHT, FireSwitch.reduce(FireGesture.TAP, HearthState.COLD))
        assertEquals(FireAction.STOKE, FireSwitch.reduce(FireGesture.TAP, HearthState.KINDLING))
        assertEquals(FireAction.STOKE, FireSwitch.reduce(FireGesture.TAP, HearthState.BLAZING))
        assertEquals(FireAction.LIGHT, FireSwitch.reduce(FireGesture.TAP, HearthState.SMOKE))
    }

    @Test fun holdTable() {
        assertEquals(FireAction.NONE, FireSwitch.reduce(FireGesture.HOLD, HearthState.COLD))
        assertEquals(FireAction.CANCEL_START, FireSwitch.reduce(FireGesture.HOLD, HearthState.KINDLING))
        assertEquals(FireAction.PUT_OUT, FireSwitch.reduce(FireGesture.HOLD, HearthState.BLAZING))
        assertEquals(FireAction.NONE, FireSwitch.reduce(FireGesture.HOLD, HearthState.SMOKE))
    }

    @Test fun classifyBoundaries() {
        assertEquals(FireGesture.TAP, FireSwitch.classify(0L, false))
        assertEquals(FireGesture.TAP, FireSwitch.classify(300L, false))
        assertNull(FireSwitch.classify(301L, false))
        assertNull(FireSwitch.classify(699L, false))
        assertEquals(FireGesture.HOLD, FireSwitch.classify(700L, false))
        assertEquals(FireGesture.HOLD, FireSwitch.classify(5000L, false))
    }

    @Test fun moveBeyondSlopCancels() {
        assertNull(FireSwitch.classify(700L, true))
        assertNull(FireSwitch.classify(100L, true))
        // a finger exactly at the slop has not moved beyond it; just above has
        assertFalse(FireSwitch.movedBeyond(3f, 4f, 5f))
        assertTrue(FireSwitch.movedBeyond(3f, 4.01f, 5f))
        assertTrue(FireSwitch.movedBeyond(-6f, 0f, 5f))
        assertFalse(FireSwitch.movedBeyond(0f, 0f, 5f))
        // fails closed: NaN counts as moved, so a hold can never fire from bad pointer data
        assertTrue(FireSwitch.movedBeyond(Float.NaN, 0f, 5f))
        assertTrue(FireSwitch.movedBeyond(0f, Float.NaN, 5f))
        assertTrue(FireSwitch.movedBeyond(0f, 0f, Float.NaN))
    }

    @Test fun consumedCancels() {
        // a consumed change counts as moved = true: the hold never fires
        assertNull(FireSwitch.classify(800L, true))
    }

    @Test fun holdOnlyAfterHoldMs() {
        assertNull(FireSwitch.classify(FireSwitch.HOLD_MS - 1L, false))
        assertEquals(FireGesture.HOLD, FireSwitch.classify(FireSwitch.HOLD_MS, false))
        assertEquals(700L, FireSwitch.HOLD_MS)
        assertEquals(300L, FireSwitch.TAP_MAX_MS)
        assertEquals(150L, FireSwitch.HOLD_GRACE_MS)
    }

    @Test fun ringFraction() {
        assertEquals(0f, FireRing.fraction(0L), 0f)
        assertEquals(0f, FireRing.fraction(150L), 0f)
        assertEquals(1f, FireRing.fraction(700L), 1e-6f)
        assertEquals(1f, FireRing.fraction(5000L), 0f)
        assertEquals(0.5f, FireRing.fraction(425L), 1e-6f)   // halfway between 150 and 700
        assertEquals(0f, FireRing.fraction(-20L), 0f)
        var prev = -1f
        for (ms in 0L..900L step 10L) {
            val f = FireRing.fraction(ms)
            assertTrue(f >= prev)
            prev = f
        }
    }

    /** FIRE owns `FireTuning.HOLD_MS`; its ring is full when this gesture fires. Read by reflection so this test compiles without that class. */
    @Test fun holdMsEqualsFireTuningWhenItExists() {
        val cls: Class<*>? = try {
            Class.forName("dev.pocketagent.app.ui.pixel.stage.FireTuning")
        } catch (_: ClassNotFoundException) {
            null
        }
        assumeTrue(cls != null)
        val type = cls!!
        val value: Any? = try {
            type.getField("HOLD_MS").get(null)
        } catch (_: NoSuchFieldException) {
            val instance = type.getField("INSTANCE").get(null)
            type.getMethod("getHOLD_MS").invoke(instance)
        }
        assertEquals(FireSwitch.HOLD_MS, (value as Number).toLong())
    }
}
