package dev.pocketagent.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The timing policy of the engine's writes: a leading write as before, plus exactly one trailing write. */
class StreamPushTest {
    @Test fun firstChangePushesAtOnce() {
        val p = StreamPush()
        assertEquals(StreamPush.Action.PUSH, p.onChange(1_000))
    }

    @Test fun changeInsideIntervalArmsOneTimer() {
        val p = StreamPush()
        p.onChange(1_000)
        assertEquals(StreamPush.Action.ARM, p.onChange(1_010))
    }

    @Test fun manyChangesArmOnlyOnce() {
        val p = StreamPush()
        p.onChange(1_000)
        assertEquals(StreamPush.Action.ARM, p.onChange(1_010))
        assertEquals(StreamPush.Action.NONE, p.onChange(1_020))
        assertEquals(StreamPush.Action.NONE, p.onChange(1_030))
        assertEquals(StreamPush.Action.NONE, p.onChange(1_040))
    }

    @Test fun timerWaitsUntilSixtyMsAfterTheLastChange() {
        val p = StreamPush()
        p.onChange(1_000)
        p.onChange(1_010)   // armed, due at 1_070
        p.onChange(1_030)   // the last change moves the due time to 1_090
        assertEquals(30L, p.onTimer(1_060))
        assertEquals(10L, p.onTimer(1_080))
    }

    @Test fun timerPushesWhenQuiet() {
        val p = StreamPush()
        p.onChange(1_000)
        p.onChange(1_010)
        assertEquals(0L, p.onTimer(1_070))
        // The write happened: a new change right after it is inside the interval again and arms a fresh timer.
        assertEquals(StreamPush.Action.ARM, p.onChange(1_080))
    }

    @Test fun timerEndsWhenNothingPending() {
        val p = StreamPush()
        p.onChange(1_000)       // pushed at once: nothing pending
        p.onChange(1_010)       // armed
        p.onChange(1_200)       // far enough from the last write: a leading push, nothing pending any more
        assertEquals(-1L, p.onTimer(1_300))
    }

    @Test fun leadingPushWhileArmedLeavesTimerHarmless() {
        val p = StreamPush()
        p.onChange(1_000)
        assertEquals(StreamPush.Action.ARM, p.onChange(1_010))
        // A leading push (more than the interval after the last write) happens while the timer is still armed.
        assertEquals(StreamPush.Action.PUSH, p.onChange(1_100))
        // The timer wakes later: nothing is pending, it ends without a write.
        assertEquals(-1L, p.onTimer(1_130))
        // And a new burst can arm a new timer.
        assertEquals(StreamPush.Action.ARM, p.onChange(1_140))
    }

    @Test fun forcedPushClearsPending() {
        val p = StreamPush()
        p.onChange(1_000)
        p.onChange(1_010)
        p.onForced(1_020)
        assertEquals(-1L, p.onTimer(1_100))
        // The forced write counts as the last write: a change right after it is inside the interval.
        assertEquals(StreamPush.Action.ARM, p.onChange(1_030))
    }
}
