package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide when a bottom sheet can be seen (defect S1 of the Wave 0 phone proof: at the normal animation
 * speed the sheet stayed below the screen and its scrim ate every touch). No device is needed: the slide is plain data.
 */
class SheetSlideTest {
    // ---- animations on --------------------------------------------------------------------------------------

    @Test
    fun withAnimationsOnTheSheetStartsOneHeightDown() {
        val s = SheetSlide.opening(animated = true)
        assertEquals(SheetPhase.ENTERING, s.phase)
        assertEquals(1f, s.hidden, 0f)
        assertEquals(800f, s.translationPx(800f), 0.001f)
        assertEquals(0f, s.openFraction(800f), 0f)
    }

    @Test
    fun theGlideBringsItHomeFrameByFrame() {
        var s = SheetSlide.opening(animated = true)
        s = s.enterFrame(0.5f)
        assertEquals(400f, s.translationPx(800f), 0.001f)
        assertEquals(0.5f, s.openFraction(800f), 0.001f)
        s = s.enterFrame(0f).rested()
        assertEquals(SheetPhase.OPEN, s.phase)
        assertEquals(0f, s.translationPx(800f), 0f)
        assertEquals(1f, s.openFraction(800f), 0f)
    }

    @Test
    fun aSpringThatOvershootsNeverLiftsTheSheetAboveItsRest() {
        val s = SheetSlide.opening(animated = true).enterFrame(-0.03f)
        assertEquals(0f, s.hidden, 0f)
        assertEquals(0f, s.translationPx(800f), 0f)
    }

    // ---- animations off -------------------------------------------------------------------------------------

    @Test
    fun withAnimationsOffTheSheetIsAtRestFromTheFirstFrame() {
        val s = SheetSlide.opening(animated = false)
        assertEquals(SheetPhase.OPEN, s.phase)
        assertEquals(0f, s.hidden, 0f)
        // Even before any measurement and without a single effect or frame: nothing is hidden.
        assertEquals(0f, s.translationPx(0f), 0f)
        assertEquals(0f, s.translationPx(800f), 0f)
        assertEquals(1f, s.openFraction(0f), 0f)
        assertEquals(1f, s.openFraction(800f), 0f)
        assertEquals(0, s.drive)
    }

    // ---- the height is not measured yet ---------------------------------------------------------------------

    @Test
    fun anUnmeasuredSheetNeverHidesBehindAGuessedHeight() {
        val entering = SheetSlide.opening(animated = true)
        assertEquals(0f, entering.translationPx(0f), 0f)
        assertEquals(0f, entering.translationPx(Float.NaN), 0f)
        assertEquals(0f, entering.translationPx(-5f), 0f)
        // The scrim does not need the height to know the sheet is still away.
        assertEquals(0f, entering.openFraction(0f), 0f)
    }

    @Test
    fun aDragBeforeTheFirstMeasurementIsKeptButHasNoFractionYet() {
        val s = SheetSlide.opening(animated = false).dragged(40f)
        assertEquals(40f, s.dragPx, 0f)
        assertEquals(1f, s.openFraction(0f), 0f)
        assertEquals(0f, s.translationPx(0f), 0f)
        assertEquals(40f, s.translationPx(800f), 0f)
    }

    // ---- dragging, releasing ---------------------------------------------------------------------------------

    @Test
    fun theFingerPullsTheSheetDownButNeverUp() {
        var s = SheetSlide.opening(animated = false)
        s = s.dragged(-30f)
        assertEquals(0f, s.dragPx, 0f)
        s = s.dragged(120f).dragged(-20f)
        assertEquals(100f, s.dragPx, 0f)
        assertEquals(100f, s.translationPx(800f), 0f)
        assertEquals(1f - 100f / 800f, s.openFraction(800f), 0.001f)
        assertEquals(s, s.dragged(Float.NaN))
    }

    @Test
    fun letGoOfAShortPullTheSheetGlidesHomeFromWhereItWas() {
        val pulled = SheetSlide.opening(animated = false).dragged(200f)
        val released = pulled.releasedOpen(800f)
        assertEquals(SheetPhase.OPEN, released.phase)
        assertEquals(0f, released.dragPx, 0f)
        assertEquals(0.25f, released.hidden, 0.0001f)
        // The picture does not jump at the moment of release.
        assertEquals(pulled.translationPx(800f), released.translationPx(800f), 0.001f)
        // A new motion is started.
        assertEquals(pulled.drive + 1, released.drive)
    }

    // ---- dismiss ----------------------------------------------------------------------------------------------

    @Test
    fun closingStartsFromWhereTheSheetIsAndEndsOneHeightDown() {
        val open = SheetSlide.opening(animated = false)
        val closing = open.closing(800f)
        assertNotNull(closing)
        closing!!
        assertEquals(SheetPhase.CLOSING, closing.phase)
        assertEquals(1f, closing.closeTarget, 0f)
        assertEquals(open.drive + 1, closing.drive)
        var s = closing.exitFrame(0.6f)
        assertEquals(480f, s.translationPx(800f), 0.001f)
        s = s.exitFrame(1f)
        assertEquals(800f, s.translationPx(800f), 0f)
        assertEquals(0f, s.openFraction(800f), 0f)
    }

    @Test
    fun aSecondCloseRequestChangesNothing() {
        val closing = SheetSlide.opening(animated = false).closing(800f)!!
        assertNull(closing.closing(800f))
    }

    @Test
    fun aClosingSheetIgnoresDragsOpeningFramesAndRelease() {
        val closing = SheetSlide.opening(animated = false).closing(800f)!!.exitFrame(0.4f)
        assertEquals(closing, closing.dragged(50f))
        assertEquals(closing, closing.enterFrame(0f))
        assertEquals(closing, closing.releasedOpen(800f))
        assertEquals(closing, closing.rested())
    }

    @Test
    fun closingFromAPullKeepsTheDepthAndMayGoFurtherThanOneHeight() {
        val pulled = SheetSlide.opening(animated = false).dragged(1000f)
        val closing = pulled.closing(800f)!!
        assertEquals(1.25f, closing.hidden, 0.0001f)
        assertEquals(1.25f, closing.closeTarget, 0.0001f)
        assertEquals(0f, closing.dragPx, 0f)
        assertEquals(pulled.translationPx(800f), closing.translationPx(800f), 0.001f)
    }

    @Test
    fun closingWhileStillEnteringStartsFromTheCurrentFrame() {
        val mid = SheetSlide.opening(animated = true).enterFrame(0.5f)
        val closing = mid.closing(800f)!!
        assertEquals(0.5f, closing.hidden, 0f)
        assertEquals(1f, closing.closeTarget, 0f)
    }

    // ---- re-open ----------------------------------------------------------------------------------------------

    @Test
    fun aSheetOpenedAgainStartsAsFreshAsTheFirstOne() {
        val first = SheetSlide.opening(animated = true).enterFrame(0f).rested().closing(800f)!!.exitFrame(1f)
        val again = SheetSlide.opening(animated = true)
        assertEquals(SheetSlide(SheetPhase.ENTERING, hidden = 1f), again)
        assertFalse(again == first)
        assertEquals(0, again.drive)
        // And with the animation setting changed in between, the new sheet follows the new setting.
        assertEquals(SheetSlide(SheetPhase.OPEN, hidden = 0f), SheetSlide.opening(animated = false))
    }

    // ---- stalled motion -----------------------------------------------------------------------------------------

    @Test
    fun theStallLimitIsOneAndAHalfSeconds() {
        assertEquals(1500L, SheetSlide.STALL_MS)
    }

    @Test
    fun withoutASpecTheMotionIsAppliedAtOnceAndNeedsNoFrameClock() = runBlocking {
        var s = SheetSlide.opening(animated = true)
        playSlide(1f, 0f, null, { v -> s = s.enterFrame(v) })
        assertEquals(0f, s.hidden, 0f)
    }

    @Test
    fun aDeadFrameClockIsFinishedByForceSoTheSheetCannotStayInvisible() {
        var s = SheetSlide.opening(animated = true)
        var stalls = 0
        runBlocking(DeadClock) {
            playSlide(1f, 0f, spring(), { v -> s = s.enterFrame(v) }, stallMs = 30L, onStall = { stalls++ })
        }
        assertEquals(0f, s.hidden, 0f)
        assertEquals(0f, s.translationPx(800f), 0f)
        assertEquals(1, stalls)
    }

    @Test
    fun aDeadFrameClockStillLetsTheSheetClose() {
        var s = SheetSlide.opening(animated = false).closing(800f)!!
        val target = s.closeTarget
        runBlocking(DeadClock) {
            playSlide(s.hidden, target, spring(), { v -> s = s.exitFrame(v) }, stallMs = 30L)
        }
        assertEquals(1f, s.hidden, 0f)
        assertEquals(SheetPhase.CLOSING, s.phase)
    }

    @Test
    fun framesThatStopAfterAPartialMoveAreFinishedByForceToo() {
        var s = SheetSlide.opening(animated = true)
        var stalls = 0
        val clock = TickThenStopClock(ticks = 2)
        runBlocking(clock) {
            playSlide(1f, 0f, spring(), { v -> s = s.enterFrame(v) }, stallMs = 30L, onStall = { stalls++ })
        }
        // The clock really delivered its two frames and then went quiet, yet the slide ends at rest.
        assertEquals(2, clock.delivered)
        assertEquals(1, stalls)
        assertEquals(0f, s.hidden, 0f)
        assertEquals(0f, s.translationPx(800f), 0f)
    }

    @Test
    fun framesThatStopAfterAPartialMoveStillLetTheSheetClose() {
        var s = SheetSlide.opening(animated = false).closing(800f)!!
        val target = s.closeTarget
        runBlocking(TickThenStopClock(ticks = 2)) {
            playSlide(s.hidden, target, spring(), { v -> s = s.exitFrame(v) }, stallMs = 30L)
        }
        assertEquals(1f, s.hidden, 0f)
    }

    @Test
    fun aWorkingFrameClockPlaysTheWholeGlideWithoutTheWatchdog() {
        var s = SheetSlide.opening(animated = true)
        var frames = 0
        var stalls = 0
        runBlocking(TickingClock()) {
            playSlide(1f, 0f, spring(), { v -> frames++; s = s.enterFrame(v) }, onStall = { stalls++ })
        }
        assertEquals(0f, s.hidden, 0f)
        assertTrue("expected several frames, got $frames", frames > 2)
        assertEquals(0, stalls)
    }
}

/** A frame clock that never ticks: what a window whose frames do not arrive looks like to an animation. */
private object DeadClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = awaitCancellation()
}

/** A frame clock that delivers [ticks] frames and then never again: frames that stop half way through a motion. */
private class TickThenStopClock(private val ticks: Int) : MonotonicFrameClock {
    var delivered = 0
        private set
    private var now = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        if (delivered >= ticks) awaitCancellation()
        yield()
        delivered++
        now += 16_000_000L
        return onFrame(now)
    }
}

/** A frame clock that ticks every 16 ms of fake time, as soon as it is asked. */
private class TickingClock : MonotonicFrameClock {
    private var now = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        yield()
        now += 16_000_000L
        return onFrame(now)
    }
}
