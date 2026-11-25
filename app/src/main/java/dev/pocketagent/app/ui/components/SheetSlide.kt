package dev.pocketagent.app.ui.components

// The slide of a bottom sheet as plain data. PocketSheet keeps one SheetSlide in a state and every change is a
// pure function here, so the rules (what is visible when, what a stalled animation does) can be tested without
// a device. Nothing in this file touches Compose or Android.

/** Where a sheet is in its life. ENTERING and OPEN look the same; CLOSING ignores every drag. */
enum class SheetPhase { ENTERING, OPEN, CLOSING }

/**
 * [hidden] is how much of the sheet's own height is still below the screen: 0 = at rest, 1 = a whole sheet
 * height down. It is a fraction on purpose, so the picture never depends on a measured height: the layer turns it
 * into pixels with its own size at draw time, and an unmeasured sheet (height 0) simply has nothing to move.
 * [dragPx] is how far a finger pulled the sheet down. [drive] counts the motions that were started (open,
 * settle after a drag, close); PocketSheet restarts its animation whenever it changes.
 */
data class SheetSlide(
    val phase: SheetPhase,
    val hidden: Float,
    val dragPx: Float = 0f,
    val drive: Int = 0,
) {
    /** The vertical translation of the sheet's layer. 0 while the height is not measured (nothing to hide yet). */
    fun translationPx(heightPx: Float): Float {
        if (!(heightPx > 0f)) return 0f
        return nonNeg(hidden) * heightPx + nonNeg(dragPx)
    }

    /** 0 closed .. 1 open, for the dither scrim. Needs no height to answer for a sheet that is open or closed. */
    fun openFraction(heightPx: Float): Float {
        val dragged = if (heightPx > 0f) nonNeg(dragPx) / heightPx else 0f
        return (1f - nonNeg(hidden) - dragged).coerceIn(0f, 1f)
    }

    /** One frame of the opening or settling animation. Ignored once the sheet is closing. */
    fun enterFrame(value: Float): SheetSlide =
        if (phase == SheetPhase.CLOSING) this else copy(hidden = nonNeg(value))

    /** One frame of the closing animation. Ignored unless the sheet is closing. */
    fun exitFrame(value: Float): SheetSlide =
        if (phase == SheetPhase.CLOSING) copy(hidden = nonNeg(value)) else this

    /** The finger moved by [delta] pixels (positive = down). The sheet never goes above its rest. */
    fun dragged(delta: Float): SheetSlide {
        if (phase == SheetPhase.CLOSING || delta.isNaN()) return this
        return copy(dragPx = (dragPx + delta).coerceAtLeast(0f))
    }

    /** The finger let go and the sheet stays: the pull becomes part of [hidden] and a new motion brings it home. */
    fun releasedOpen(heightPx: Float): SheetSlide {
        if (phase == SheetPhase.CLOSING) return this
        return copy(phase = SheetPhase.OPEN, hidden = folded(heightPx), dragPx = 0f, drive = drive + 1)
    }

    /** The sheet is asked to close. Null when it already is, so a second request changes nothing. */
    fun closing(heightPx: Float): SheetSlide? {
        if (phase == SheetPhase.CLOSING) return null
        return copy(phase = SheetPhase.CLOSING, hidden = folded(heightPx), dragPx = 0f, drive = drive + 1)
    }

    /** Where the closing animation ends: a whole height down, or further when the sheet was pulled past that. */
    val closeTarget: Float get() = maxOf(1f, nonNeg(hidden))

    /** The opening motion is over: the sheet rests. A sheet that is closing stays closing. */
    fun rested(): SheetSlide =
        if (phase == SheetPhase.CLOSING) this else copy(phase = SheetPhase.OPEN, hidden = 0f)

    private fun folded(heightPx: Float): Float =
        if (heightPx > 0f) nonNeg(hidden) + nonNeg(dragPx) / heightPx else nonNeg(hidden)

    companion object {
        /** No frame may be missing for this long while a motion runs before it is finished by force (see playSlide). */
        const val STALL_MS = 1500L

        /**
         * A new sheet. With animations on it starts one height down and slides in; with animations off it is at rest
         * from the very first frame and needs no frame callback, no measurement and no effect to be seen.
         */
        fun opening(animated: Boolean): SheetSlide =
            if (animated) SheetSlide(SheetPhase.ENTERING, hidden = 1f) else SheetSlide(SheetPhase.OPEN, hidden = 0f)
    }
}

private fun nonNeg(v: Float): Float = if (v.isNaN()) 0f else v.coerceAtLeast(0f)
