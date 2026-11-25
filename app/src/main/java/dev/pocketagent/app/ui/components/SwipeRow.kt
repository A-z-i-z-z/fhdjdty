package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.ui.theme.rememberHaptics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One action behind a swipeable row. [label] is also its spoken name. */
class SwipeAction(
    val icon: PixelIcon,
    val label: String,
    val color: Color,
    val danger: Boolean = false,
    val onTrigger: () -> Unit,
)

enum class SwipeOutcome { TRIGGER, RETURN }

/** Pure: the numbers behind the swipe. A side acts at 40 percent of the row's width. */
object SwipeMath {
    const val THRESHOLD = 0.4f
    const val FLING_PX_PER_SEC = 1000f

    /** 0 at rest, 1 when the threshold is reached, kept between 0 and 1. */
    fun progress(offsetPx: Float, widthPx: Float): Float {
        if (offsetPx.isNaN() || widthPx <= 0f) return 0f
        return (abs(offsetPx) / (THRESHOLD * widthPx)).coerceIn(0f, 1f)
    }

    /** TRIGGER past the threshold, or with a fling faster than 1000 px/s in the direction of the drag. */
    fun settle(offsetPx: Float, velocityPxPerSec: Float, widthPx: Float): SwipeOutcome {
        if (offsetPx.isNaN() || offsetPx == 0f || widthPx <= 0f) return SwipeOutcome.RETURN
        val past = abs(offsetPx) >= THRESHOLD * widthPx
        val flung = abs(velocityPxPerSec) > FLING_PX_PER_SEC && velocityPxPerSec * offsetPx > 0f
        return if (past || flung) SwipeOutcome.TRIGGER else SwipeOutcome.RETURN
    }
}

/**
 * A row with swipe actions. Dragging towards the end side reveals [startActions] (towards the start side the
 * [endActions]; mirrored in a right-to-left layout). The icon grows from 0.6 to 1 as the threshold nears and
 * plays the HOLD cue when it is crossed; letting go past it, or with a fast fling, calls the first action's
 * onTrigger and the row springs back. Every action is also a custom accessibility action of the row, which is
 * one accessibility element, so a labelled way always exists. Built for Wave 1 and later screens.
 */
@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    startActions: List<SwipeAction> = emptyList(),
    endActions: List<SwipeAction> = emptyList(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = Pocket.motion
    val haptics = rememberHaptics()
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var widthPx by remember { mutableIntStateOf(0) }
    // Logical offset: above zero reveals the start actions, below zero the end actions.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val canStart = startActions.isNotEmpty()
    val canEnd = endActions.isNotEmpty()

    val dragState = rememberDraggableState { dx ->
        var next = offsetPx + dx * sign
        if (!canStart) next = min(next, 0f)
        if (!canEnd) next = max(next, 0f)
        val limit = widthPx.toFloat()
        next = next.coerceIn(-limit, limit)
        offsetPx = next
        val reached = SwipeMath.progress(next, limit) >= 1f
        if (reached && !armed) haptics.play(HapticCue.HOLD)
        armed = reached
    }
    val drag = if (enabled && (canStart || canEnd)) {
        Modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity ->
                val outcome = SwipeMath.settle(offsetPx, velocity * sign, widthPx.toFloat())
                if (outcome == SwipeOutcome.TRIGGER) {
                    val action = if (offsetPx > 0f) startActions.firstOrNull() else endActions.firstOrNull()
                    action?.onTrigger()
                }
                armed = false
                animate(initialValue = offsetPx, targetValue = 0f, animationSpec = motion.snappy()) { v, _ -> offsetPx = v }
            },
        )
    } else {
        Modifier
    }

    val reveal: SwipeAction? = when {
        offsetPx > 0f -> startActions.firstOrNull()
        offsetPx < 0f -> endActions.firstOrNull()
        else -> null
    }
    val all = startActions + endActions
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { widthPx = it.width }) {
        if (reveal != null) {
            val progress = SwipeMath.progress(offsetPx, widthPx.toFloat())
            Box(
                Modifier.matchParentSize().background(reveal.color).padding(horizontal = Space.l),
                contentAlignment = if (offsetPx > 0f) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                PixelIconView(
                    reveal.icon,
                    modifier = Modifier.graphicsLayer {
                        val k = 0.6f + 0.4f * progress
                        scaleX = k
                        scaleY = k
                    },
                    tint = if (reveal.color.luminance() > 0.5f) Color.Black else Color.White,
                    size = 24.dp,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset((offsetPx * sign).roundToInt(), 0) }
                .then(drag)
                .semantics(mergeDescendants = true) {
                    if (all.isNotEmpty()) {
                        customActions = all.map { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } }
                    }
                },
        ) { content() }
    }
}
