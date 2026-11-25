package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.motion
import kotlin.math.roundToInt

/**
 * Pattern 1, the press: a pressable pixel surface sinks into its lip in the very frame of the press and comes
 * back with the snappy spring, rounded every frame to whole device pixels (at OFF it comes back at once).
 */
@Stable
class PixelPress internal constructor(
    private val pressedState: State<Boolean>,
    private val depthState: State<Float>,
    private val maxPx: Int,
) {
    val pressed: Boolean get() = pressedState.value

    /** How far down the face sits now, in whole device pixels, 0 to the depth of the press. */
    val sinkPx: Int get() = if (pressedState.value) maxPx else sinkPixels(depthState.value, maxPx)
}

/** Pure: [depth] (0 to 1) of [maxPx] device pixels, rounded and kept between 0 and [maxPx]. */
fun sinkPixels(depth: Float, maxPx: Int): Int {
    if (depth.isNaN() || maxPx <= 0) return 0
    return (depth * maxPx).roundToInt().coerceIn(0, maxPx)
}

@Composable
fun rememberPixelPress(interactionSource: InteractionSource, sink: Dp = 2.dp): PixelPress {
    val pressed = interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val maxPx = with(density) { sink.toPx() }.roundToInt().coerceAtLeast(1)
    val motion = Pocket.motion
    val depth = remember { Animatable(0f) }
    LaunchedEffect(pressed.value, motion) {
        if (pressed.value) {
            depth.snapTo(1f)
        } else if (depth.value > 0f) {
            depth.snapTo(1f)
            depth.animateTo(0f, motion.snappy())
        }
    }
    return remember(pressed, depth, maxPx) { PixelPress(pressed, depth.asState(), maxPx) }
}

/** Moves the content down by the press depth. Reads the depth at layout time, so no recomposition. */
fun Modifier.pixelPressOffset(press: PixelPress): Modifier = offset { IntOffset(0, press.sinkPx) }
