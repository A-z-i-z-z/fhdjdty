package dev.pocketagent.app.ui.nav

/** What happened to the stack between two frames, as far as the motion is concerned. */
internal enum class NavMotionKind {
    /** The top did not change (or nothing is known yet): no motion. */
    NONE,

    /** A screen was put on top of the one that was on top: it slides in. */
    PUSH,

    /** The screen on top was removed and the one below it is back: the top screen slides away. */
    POP,

    /** From one tab root to another: a cross-fade. */
    TAB,
}

/**
 * Classifies a change of the stack from the entry ids before ([oldIds]) and after ([newIds]), both bottom to top.
 * Pure, so it is unit tested.
 *
 * Two tab roots in a row is [NavMotionKind.TAB]. Otherwise the old top still on the stack below the new top is a
 * [NavMotionKind.PUSH], and the new top found below the old top is a [NavMotionKind.POP]. When neither holds (a link
 * that rebuilds the stack, a replaced screen) the depth decides: deeper or equal is a push, shallower a pop.
 */
internal fun navMotionKind(oldIds: List<Long>, newIds: List<Long>): NavMotionKind {
    if (oldIds.isEmpty() || newIds.isEmpty()) return NavMotionKind.NONE
    val oldTop = oldIds.last()
    val newTop = newIds.last()
    if (oldTop == newTop) return NavMotionKind.NONE
    if (oldIds.size == 1 && newIds.size == 1) return NavMotionKind.TAB
    val oldTopInNew = newIds.indexOf(oldTop)
    if (oldTopInNew in 0 until newIds.lastIndex) return NavMotionKind.PUSH
    val newTopInOld = oldIds.indexOf(newTop)
    if (newTopInOld in 0 until oldIds.lastIndex) return NavMotionKind.POP
    return if (newIds.size >= oldIds.size) NavMotionKind.PUSH else NavMotionKind.POP
}

/** +1 when horizontal motion points to the right (left-to-right layout), -1 in a right-to-left layout. */
internal fun slideSign(rtl: Boolean): Int = if (rtl) -1 else 1
