package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.Ease
import dev.pocketagent.app.ui.theme.Loop
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.designUnitPx
import dev.pocketagent.app.ui.theme.motion
import kotlin.math.min
import dev.pocketagent.app.ui.text.t

/** Number of flicker pictures per [Loop.EMBER_LINE]: a little under six a second. */
private const val EMBER_STEPS = 7

/** Pure: a well mixed non-negative number from two small ones (which picture, which run). */
internal fun emberMix(a: Int, b: Int): Int {
    var h = a * 0x45d9f3b + b * 0x119de1f3 + 0x3c6ef372
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    return h and 0x7fffffff
}

/**
 * The thin line of fire that shows the assistant is busy: two design pixels high, a line of runs in the fire
 * ramp whose lengths and colours flicker. Inactive it draws nothing, but it keeps its height, so the layout does
 * not move. At OFF the line stands still. Spoken as "working". Tag: ember.
 */
@Composable
fun EmberLine(active: Boolean, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val motion = Pocket.motion
    val dens = LocalDensity.current
    val unit = designUnitPx(dens.density)
    val lineHeight = with(dens) { (2 * unit).toDp() }
    val description = t(R.string.design_ember_active)
    val phase: State<Float>? = if (active && motion.loops) {
        rememberInfiniteTransition(label = "emberLine").animateFloat(
            initialValue = 0f, targetValue = 1f, label = "flicker",
            animationSpec = motion.loop(Loop.EMBER_LINE, RepeatMode.Restart, Ease.steps(EMBER_STEPS)),
        )
    } else null
    val base = modifier.fillMaxWidth().height(lineHeight)
    val look = if (active) {
        base
            .testTag("ember")
            .semantics { contentDescription = description }
            .drawWithCache {
                val u = designUnitPx(density).toFloat()
                val cols = (size.width / u).toInt() + 1
                onDrawBehind {
                    val step = ((phase?.value ?: 0f) * EMBER_STEPS).toInt().coerceIn(0, EMBER_STEPS - 1)
                    // the base row is always lit; the top row flickers in runs
                    drawRect(c.fireLow, Offset(0f, u), Size(size.width, u))
                    var x = 0
                    var run = 0
                    while (x < cols) {
                        val h = emberMix(step, run)
                        val len = 2 + (h and 7)
                        val color = when ((h shr 3) and 3) {
                            0 -> c.fireMid
                            1 -> c.fireHigh
                            2 -> c.fireMid
                            else -> c.fireLow
                        }
                        val width = min(len, cols - x) * u
                        drawRect(color, Offset(x * u, 0f), Size(width, u))
                        x += len
                        run++
                    }
                }
            }
    } else {
        base
    }
    Spacer(look)
}
