package dev.pocketagent.app.ui.home

import dev.pocketagent.app.ui.pixel.HearthState

/** What a gesture on the fire asks for. */
enum class FireAction { LIGHT, STOKE, PUT_OUT, CANCEL_START, NONE }

enum class FireGesture { TAP, HOLD }

/**
 * The fire is the switch of Home: a tap lights or stokes it, a hold puts it out. Pure: the gesture code in the hero measures
 * the time and the movement, this object decides what they mean.
 */
object FireSwitch {
    /** A tap is an up within this time. */
    const val TAP_MAX_MS = 300L

    /** A hold fires once this time has passed with no movement. The fire layer's ring is full at the same moment. */
    const val HOLD_MS = 700L

    /** The ring is hidden until this time. */
    const val HOLD_GRACE_MS = 150L

    /**
     * TAP: cold lights, kindling and blazing stoke, smoke lights again (a retry). HOLD: blazing puts out, kindling cancels
     * the start, cold and smoke do nothing.
     */
    fun reduce(g: FireGesture, hearth: HearthState): FireAction = when (g) {
        FireGesture.TAP -> when (hearth) {
            HearthState.COLD -> FireAction.LIGHT
            HearthState.KINDLING -> FireAction.STOKE
            HearthState.BLAZING -> FireAction.STOKE
            HearthState.SMOKE -> FireAction.LIGHT
        }
        FireGesture.HOLD -> when (hearth) {
            HearthState.BLAZING -> FireAction.PUT_OUT
            HearthState.KINDLING -> FireAction.CANCEL_START
            HearthState.COLD -> FireAction.NONE
            HearthState.SMOKE -> FireAction.NONE
        }
    }

    /**
     * What a finger that was down for [heldMs] means: a TAP when it was up within [TAP_MAX_MS] and did not [moved]; a HOLD when
     * [HOLD_MS] passed and it did not move; null (nothing happens) for anything else, in particular for any finger that moved.
     */
    fun classify(heldMs: Long, moved: Boolean): FireGesture? {
        if (moved) return null
        return when {
            heldMs <= TAP_MAX_MS -> FireGesture.TAP
            heldMs >= HOLD_MS -> FireGesture.HOLD
            else -> null
        }
    }

    /**
     * True when the finger is more than [slopPx] away from where it went down: `dx * dx + dy * dy > slop * slop`. Such a finger
     * is a scroll, not a tap or a hold. It fails closed: a NaN in any input counts as moved, so odd pointer data can never
     * turn a resting finger into a hold (which on a blazing fire would stop Hermes).
     */
    fun movedBeyond(dxPx: Float, dyPx: Float, slopPx: Float): Boolean = !(dxPx * dxPx + dyPx * dyPx <= slopPx * slopPx)
}

/** The ring that fills while the fire is held. The scene draws it (20 dots); this is only its fraction. */
object FireRing {
    /** 0 until [FireSwitch.HOLD_GRACE_MS], then linear up to 1 at [FireSwitch.HOLD_MS]. */
    fun fraction(heldMs: Long): Float {
        val span = (FireSwitch.HOLD_MS - FireSwitch.HOLD_GRACE_MS).toFloat()
        return ((heldMs - FireSwitch.HOLD_GRACE_MS).toFloat() / span).coerceIn(0f, 1f)
    }
}
