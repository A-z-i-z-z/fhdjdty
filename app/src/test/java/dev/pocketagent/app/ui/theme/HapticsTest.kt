package dev.pocketagent.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The cue table: what each cue plays at each level on each Android version. */
class HapticsTest {
    private val cues = listOf(
        HapticCue.TICK, HapticCue.CLICK, HapticCue.SEND, HapticCue.RISE,
        HapticCue.DOUBLE, HapticCue.HOLD, HapticCue.STOKE, HapticCue.PULSE,
    )
    private val sdks = listOf(26, 29, 30, 34)

    /** The table of the design language section 4, written out on its own. */
    private fun expected(cue: HapticCue, sdk: Int): List<HapticKind> {
        val tick = if (sdk >= 34) HapticKind.SEGMENT_FREQUENT_TICK else HapticKind.CLOCK_TICK
        val confirm = if (sdk >= 30) HapticKind.CONFIRM else HapticKind.VIRTUAL_KEY
        return when (cue) {
            HapticCue.TICK -> listOf(tick)
            HapticCue.CLICK -> listOf(HapticKind.VIRTUAL_KEY)
            HapticCue.SEND -> listOf(confirm)
            HapticCue.RISE -> listOf(confirm)
            HapticCue.DOUBLE -> listOf(if (sdk >= 30) HapticKind.REJECT else HapticKind.LONG_PRESS)
            HapticCue.HOLD -> listOf(HapticKind.LONG_PRESS)
            HapticCue.STOKE -> listOf(tick, tick, tick)
            HapticCue.PULSE -> listOf(HapticKind.CLOCK_TICK)
        }
    }

    @Test
    fun planMatrix() {
        for (cue in cues) {
            for (sdk in sdks) {
                val full = Haptics.planFor(cue, HapticLevel.FULL, sdk)
                assertNotNull("$cue at $sdk", full)
                assertEquals("$cue at $sdk", expected(cue, sdk), full!!.kinds)
                assertNull("$cue off at $sdk", Haptics.planFor(cue, HapticLevel.OFF, sdk))
                val subtle = Haptics.planFor(cue, HapticLevel.SUBTLE, sdk)
                if (cue in SUBTLE_CUES) {
                    assertEquals("$cue subtle at $sdk", expected(cue, sdk), subtle!!.kinds)
                } else {
                    assertNull("$cue subtle at $sdk", subtle)
                }
            }
        }
    }

    @Test
    fun subtleOnlyConfirmErrorHold() {
        val kept = cues.filter { Haptics.planFor(it, HapticLevel.SUBTLE, 34) != null }.toSet()
        assertEquals(setOf(HapticCue.SEND, HapticCue.RISE, HapticCue.DOUBLE, HapticCue.HOLD), kept)
    }

    @Test
    fun stokeIsThreeTicks40Apart() {
        for (sdk in sdks) {
            val plan = Haptics.planFor(HapticCue.STOKE, HapticLevel.FULL, sdk)!!
            assertEquals(3, plan.kinds.size)
            assertEquals(40L, plan.gapMs)
        }
        for (cue in cues - HapticCue.STOKE) {
            val plan = Haptics.planFor(cue, HapticLevel.FULL, 34)!!
            assertEquals(1, plan.kinds.size)
        }
    }

    private companion object {
        val SUBTLE_CUES = setOf(HapticCue.SEND, HapticCue.RISE, HapticCue.DOUBLE, HapticCue.HOLD)
    }
}
