package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGateTest {
    /** How many of [frames] frames, [spacingNanos] apart, the gate lets through. */
    private fun accepted(gate: FrameGate, spacingNanos: Long, frames: Int = 600): Int {
        var n = 0
        for (i in 0 until frames) if (gate.accept(i * spacingNanos)) n++
        return n
    }

    @Test fun firstFrameAccepted() {
        assertTrue(FrameGate().accept(123_456_789L))
        assertTrue(FrameGate().accept(0L))
    }

    @Test fun sixtyHzEverySecondFrame() {
        assertEquals(300, accepted(FrameGate(), 16_666_667L))
    }

    @Test fun ninetyHzEveryThirdFrame() {
        assertEquals(200, accepted(FrameGate(), 11_111_111L))
    }

    @Test fun oneTwentyHzEveryFourthFrame() {
        assertEquals(150, accepted(FrameGate(), 8_333_333L))
    }

    @Test fun slowDisplayEveryFrame() {
        assertEquals(600, accepted(FrameGate(), 41_666_667L))
    }

    @Test fun jitterWithinSlackStillAccepted() {
        val gate = FrameGate()
        assertTrue(gate.accept(0L))
        assertTrue(gate.accept(31_500_000L))
        assertTrue(gate.accept(63_000_000L))
        assertFalse(gate.accept(70_000_000L))
    }

    @Test fun resetRestartsCadence() {
        val gate = FrameGate()
        assertTrue(gate.accept(0L))
        assertFalse(gate.accept(10_000_000L))
        gate.reset()
        assertTrue(gate.accept(11_000_000L))
        assertFalse(gate.accept(12_000_000L))
    }

    @Test fun otherRatesWork() {
        // 15 per second on a 60 Hz display: every fourth frame
        assertEquals(150, accepted(FrameGate(maxFps = 15), 16_666_667L))
        // 60 per second on a 60 Hz display: every frame
        assertEquals(600, accepted(FrameGate(maxFps = 60), 16_666_667L))
    }

    @Test fun rateIsChecked() {
        assertThrows(IllegalArgumentException::class.java) { FrameGate(maxFps = 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameGate(maxFps = 241) }
    }
}
