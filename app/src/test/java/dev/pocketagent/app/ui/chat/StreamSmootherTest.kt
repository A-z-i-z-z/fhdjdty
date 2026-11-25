package dev.pocketagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSmootherTest {
    private val ms = 1_000_000L

    /** Plays frames of [frameMs] over [totalMs]; returns the smoother's `shown` after every frame. */
    private fun run(
        s: StreamSmoother,
        target: CharSequence,
        totalMs: Long,
        frameMs: Long = 16,
        startMs: Long = 0,
        finished: Boolean = false,
    ): List<Int> {
        val out = ArrayList<Int>()
        var t = startMs
        while (t <= startMs + totalMs) {
            out += s.step(t * ms, target, finished)
            t += frameMs
        }
        return out
    }

    @Test fun neverReleasesMoreThanTheTarget() {
        val s = StreamSmoother()
        val target = "x".repeat(37)
        for (shown in run(s, target, 5_000)) assertTrue(shown in 0..target.length)
        assertEquals(target.length, s.shown)
    }

    @Test fun releasedCountOnlyGrowsWhileTargetGrows() {
        val s = StreamSmoother()
        val sb = StringBuilder()
        var last = 0
        var t = 0L
        while (t < 4_000) {
            if (t % 100 == 0L) sb.append("hello world ")
            val shown = s.step(t * ms, sb, false)
            assertTrue("shown went back from $last to $shown", shown >= last)
            last = shown
            t += 8
        }
    }

    @Test fun speedFollowsTheBacklog() {
        val s = StreamSmoother()
        val target = "a".repeat(350)
        s.step(0, target, false)
        val shown = s.step(100 * ms, target, false)
        // 350 chars of backlog at 0.35 s: about 1000 chars per second, so about 100 after a tenth of a second.
        assertTrue("shown=$shown", shown in 85..115)
    }

    @Test fun neverSlowerThanFortyPerSecond() {
        val s = StreamSmoother()
        val target = "a".repeat(4)
        s.step(0, target, false)
        var t = 0L
        while (t < 1_000) { t += 16; s.step(t * ms, target, false) }
        // 4 chars of backlog would be 11 chars per second by the backlog rule; the floor of 40 shows all 4 within 100 ms.
        assertEquals(4, s.shown)
        val s2 = StreamSmoother()
        val longer = "a".repeat(10)
        s2.step(0, longer, false)
        // After 0.5 s at least 20 characters could have been released at the floor: all 10 are out.
        var u = 0L
        while (u < 500) { u += 16; s2.step(u * ms, longer, false) }
        assertEquals(10, s2.shown)
    }

    @Test fun steadyArrivalSettlesNearThreeHundredFiftyMsBehind() {
        val s = StreamSmoother()
        val sb = StringBuilder()
        var arrived = 0
        var t = 0L
        val frame = 8L // about 120 Hz
        var backlog = 0
        while (t <= 5_000) {
            val shouldHave = (t * 200 / 1000).toInt()
            while (arrived < shouldHave) { sb.append('a'); arrived++ }
            val shown = s.step(t * ms, sb, false)
            backlog = sb.length - shown
            t += frame
        }
        // 200 characters per second at 0.35 s of backlog is 70 characters.
        assertTrue("backlog=$backlog", backlog in 45..95)
    }

    @Test fun finishFlushesWithin150Ms() {
        val s = StreamSmoother()
        val target = "b".repeat(2_000)
        s.step(0, target, false)
        s.step(16 * ms, target, false)
        var t = 16L
        // The answer finishes now; everything is out within 150 ms (plus one frame).
        val finishAt = t
        while (t <= finishAt + 170) { t += 8; s.step(t * ms, target, true) }
        assertEquals(target.length, s.shown)
        assertFalse(s.hasBacklog(target.length))
    }

    @Test fun firstFrameAfterIdleReleasesNothing() {
        val s = StreamSmoother()
        val sb = StringBuilder("abcdef")
        run(s, sb, 1_000)
        assertEquals(6, s.shown)
        // Idle for a long time: the smoother has reset its frame time.
        s.step(5_000 * ms, sb, false)
        sb.append("0123456789")
        val first = s.step(5_016 * ms, sb, false)
        assertEquals("the first frame of new backlog releases nothing", 6, first)
        val next = s.step(5_032 * ms, sb, false)
        assertTrue(next >= 6)
    }

    @Test fun cutNeverSplitsASurrogatePair() {
        val text = "ab😀cd" // a, b, a smiley (two chars), c, d
        assertEquals(4, StreamSmoother.safeCut(text, 3, false))
        assertEquals(4, StreamSmoother.safeCut(text, 3, true))
        assertEquals(2, StreamSmoother.safeCut(text, 2, false))
        assertEquals(text.length, StreamSmoother.safeCut(text, 99, false))
        // Frame by frame the shown count never lands between the two halves.
        val s = StreamSmoother()
        for (shown in run(s, text, 2_000, 5)) {
            assertFalse(shown == 3)
        }
    }

    @Test fun cutNeverEndsInsideAMarkerRun() {
        val text = "ab**cd**ef"
        // Between the two stars of the first pair: moved to the end of the run.
        assertEquals(4, StreamSmoother.safeCut(text, 3, false))
        // A cut right before a run is fine, and so is one after it.
        assertEquals(2, StreamSmoother.safeCut(text, 2, false))
        assertEquals(4, StreamSmoother.safeCut(text, 4, false))
        // Backticks are markers too.
        assertEquals(5, StreamSmoother.safeCut("a ```b", 3, false))
        // A line starting with hashes: the cut does not stop between them.
        val heading = "x\n## Title"
        assertEquals(4, StreamSmoother.safeCut(heading, 3, false))
        // A hash inside a line is just text.
        assertEquals(3, StreamSmoother.safeCut("a ##b", 3, false))
    }

    @Test fun cutIgnoresMarkersWhenFinished() {
        val text = "ab**cd**ef"
        assertEquals(3, StreamSmoother.safeCut(text, 3, true))
        assertEquals(3, StreamSmoother.safeCut("x\n## Title", 3, true))
    }

    @Test fun shrinkingTargetIsClamped() {
        val s = StreamSmoother()
        val long = "c".repeat(100)
        run(s, long, 2_000)
        assertEquals(100, s.shown)
        val shorter = long.substring(0, 60)
        val shown = s.step(3_000 * ms, shorter, true)
        assertEquals(60, shown)
    }

    @Test fun initialShownReplaysOnlyFreshMessages() {
        assertEquals(0, StreamSmoother.initialShown(500, 200))
        assertEquals(0, StreamSmoother.initialShown(500, 1_500))
        assertEquals(500, StreamSmoother.initialShown(500, 1_501))
        assertEquals(500, StreamSmoother.initialShown(500, 60_000))
        assertEquals(0, StreamSmoother.initialShown(0, 60_000))
        assertEquals(40, StreamSmoother.initialShown(40, 9_000, replayWindowMs = 100))
    }
}
