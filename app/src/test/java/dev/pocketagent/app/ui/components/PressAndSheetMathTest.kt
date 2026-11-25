package dev.pocketagent.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The gesture decisions: how far a press sinks, whether a sheet closes, whether a swipe fires. */
class PressAndSheetMathTest {
    @Test
    fun sinkPixelsRounds() {
        assertEquals(0, sinkPixels(0f, 2))
        assertEquals(1, sinkPixels(0.49f, 2))
        assertEquals(1, sinkPixels(0.5f, 2))
        assertEquals(2, sinkPixels(1f, 2))
        assertEquals(2, sinkPixels(0.49f, 5))
        assertEquals(3, sinkPixels(0.5f, 5))
        assertEquals(5, sinkPixels(1f, 5))
        assertEquals(0, sinkPixels(-1f, 5))
        assertEquals(5, sinkPixels(2f, 5))
        assertEquals(0, sinkPixels(Float.NaN, 5))
        assertEquals(0, sinkPixels(1f, 0))
    }

    @Test
    fun sheetSettleByVelocityAndDistance() {
        assertEquals(SheetOutcome.OPEN, SheetPhysics.settle(100f, 0f, 1000f))
        assertEquals(SheetOutcome.OPEN, SheetPhysics.settle(340f, 0f, 1000f))
        assertEquals(SheetOutcome.CLOSE, SheetPhysics.settle(360f, 0f, 1000f))
        assertEquals(SheetOutcome.CLOSE, SheetPhysics.settle(0f, 1300f, 1000f))
        assertEquals(SheetOutcome.OPEN, SheetPhysics.settle(0f, 1200f, 1000f))
        assertEquals(SheetOutcome.CLOSE, SheetPhysics.settle(400f, -1000f, 1000f))
    }

    @Test
    fun sheetSettleUpwardFlingKeepsOpen() {
        assertEquals(SheetOutcome.OPEN, SheetPhysics.settle(900f, -1300f, 1000f))
        assertEquals(SheetOutcome.OPEN, SheetPhysics.settle(900f, -1200f, 1000f))
    }

    @Test
    fun scrimStepFollowsTheOpenFraction() {
        assertEquals(0, scrimStepFor(0f))
        assertEquals(2, scrimStepFor(0.5f))
        assertEquals(4, scrimStepFor(1f))
        assertEquals(4, scrimStepFor(2f))
        assertEquals(0, scrimStepFor(-1f))
        assertEquals(0, scrimStepFor(Float.NaN))
    }

    @Test
    fun swipeProgressClamped() {
        assertEquals(0f, SwipeMath.progress(0f, 400f), 1e-6f)
        assertEquals(0.5f, SwipeMath.progress(80f, 400f), 1e-6f)
        assertEquals(0.5f, SwipeMath.progress(-80f, 400f), 1e-6f)
        assertEquals(1f, SwipeMath.progress(160f, 400f), 1e-6f)
        assertEquals(1f, SwipeMath.progress(900f, 400f), 1e-6f)
        assertEquals(0f, SwipeMath.progress(50f, 0f), 1e-6f)
        assertEquals(0f, SwipeMath.progress(Float.NaN, 400f), 1e-6f)
    }

    @Test
    fun swipeSettleTriggersAtFortyPercentOrFling() {
        assertEquals(SwipeOutcome.TRIGGER, SwipeMath.settle(170f, 0f, 400f))
        assertEquals(SwipeOutcome.TRIGGER, SwipeMath.settle(-200f, 0f, 400f))
        assertEquals(SwipeOutcome.TRIGGER, SwipeMath.settle(30f, 1500f, 400f))
        assertEquals(SwipeOutcome.TRIGGER, SwipeMath.settle(-30f, -1500f, 400f))
        // a fast fling the other way is a change of mind
        assertEquals(SwipeOutcome.RETURN, SwipeMath.settle(30f, -1500f, 400f))
    }

    @Test
    fun swipeSettleSmallDragReturns() {
        assertEquals(SwipeOutcome.RETURN, SwipeMath.settle(10f, 100f, 400f))
        assertEquals(SwipeOutcome.RETURN, SwipeMath.settle(159f, 1000f, 400f))
        assertEquals(SwipeOutcome.RETURN, SwipeMath.settle(0f, 5000f, 400f))
        assertEquals(SwipeOutcome.RETURN, SwipeMath.settle(100f, 100f, 0f))
    }
}
