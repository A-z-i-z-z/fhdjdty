package dev.pocketagent.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

// One haptic vocabulary (docs/design-language.md section 4), built on View.performHapticFeedback: no
// permission, and it follows the system's touch-feedback setting. The vibration-primitive variant of RISE is
// not built.

enum class HapticCue { TICK, CLICK, SEND, RISE, DOUBLE, HOLD, STOKE, PULSE }

/** The setting: nothing, only the confirm, error and long-press cues, or all of them. Stored by name. */
enum class HapticLevel { OFF, SUBTLE, FULL }

/** The platform constants a cue can resolve to. */
enum class HapticKind {
    SEGMENT_FREQUENT_TICK, CLOCK_TICK, VIRTUAL_KEY, CONFIRM, REJECT, LONG_PRESS, GESTURE_THRESHOLD_ACTIVATE,
}

/** [kinds] played one after another, [gapMs] apart. */
class HapticPlan(val kinds: List<HapticKind>, val gapMs: Long)

object Haptics {
    private const val STOKE_GAP_MS = 40L

    /** Pure: what a cue plays at a level on an Android version; null means nothing. */
    fun planFor(cue: HapticCue, level: HapticLevel, sdk: Int): HapticPlan? {
        if (level == HapticLevel.OFF) return null
        if (level == HapticLevel.SUBTLE) {
            when (cue) {
                HapticCue.SEND, HapticCue.RISE, HapticCue.DOUBLE, HapticCue.HOLD -> Unit
                else -> return null
            }
        }
        val tick = if (sdk >= 34) HapticKind.SEGMENT_FREQUENT_TICK else HapticKind.CLOCK_TICK
        val confirm = if (sdk >= 30) HapticKind.CONFIRM else HapticKind.VIRTUAL_KEY
        return when (cue) {
            HapticCue.TICK -> HapticPlan(listOf(tick), 0L)
            HapticCue.CLICK -> HapticPlan(listOf(HapticKind.VIRTUAL_KEY), 0L)
            HapticCue.SEND -> HapticPlan(listOf(confirm), 0L)
            HapticCue.RISE -> HapticPlan(listOf(confirm), 0L)
            HapticCue.DOUBLE -> HapticPlan(listOf(if (sdk >= 30) HapticKind.REJECT else HapticKind.LONG_PRESS), 0L)
            HapticCue.HOLD -> HapticPlan(listOf(HapticKind.LONG_PRESS), 0L)
            HapticCue.STOKE -> HapticPlan(listOf(tick, tick, tick), STOKE_GAP_MS)
            HapticCue.PULSE -> HapticPlan(listOf(HapticKind.CLOCK_TICK), 0L)
        }
    }

    fun play(view: View, cue: HapticCue, level: HapticLevel = HapticLevel.FULL) {
        val plan = planFor(cue, level, Build.VERSION.SDK_INT) ?: return
        try {
            plan.kinds.forEachIndexed { i, kind ->
                val constant = constantOf(kind)
                if (i == 0 || plan.gapMs <= 0L) {
                    view.performHapticFeedback(constant)
                } else {
                    view.postDelayed({ view.performHapticFeedback(constant) }, plan.gapMs * i)
                }
            }
        } catch (_: Throwable) {
            // Haptics are a nicety: a view that refuses must never break a tap.
        }
    }

    private fun constantOf(kind: HapticKind): Int = when (kind) {
        HapticKind.SEGMENT_FREQUENT_TICK -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        HapticKind.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
        HapticKind.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
        HapticKind.REJECT -> HapticFeedbackConstants.REJECT
        HapticKind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        HapticKind.GESTURE_THRESHOLD_ACTIVATE -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    }
}

/** Plays cues on one view at the level of the setting at the time of the call. */
@Stable
class HapticPlayer internal constructor(private val view: View, private val level: () -> HapticLevel) {
    fun play(cue: HapticCue) {
        Haptics.play(view, cue, level())
    }
}

@Composable
fun rememberHaptics(): HapticPlayer {
    val view = LocalView.current
    val level = rememberUpdatedState(Pocket.appearance.haptics)
    return remember(view) { HapticPlayer(view) { level.value } }
}
