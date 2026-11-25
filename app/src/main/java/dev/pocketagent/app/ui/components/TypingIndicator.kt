package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.Loop
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.motion
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Three small embers that hop one after another (in whole-pixel steps). Reduced animation hops once
 * a pixel and slower; OFF shows three still embers.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier, color: Color = Pocket.colors.fireMid) {
    val motion = Pocket.motion
    val hot = Pocket.colors.fireHigh
    val cool = Pocket.colors.fireLow
    val description = stringResource(R.string.design_typing)
    val phase: State<Float>? = if (motion.loops) {
        rememberInfiniteTransition(label = "typing").animateFloat(
            initialValue = 0f, targetValue = 1f, label = "hop",
            animationSpec = motion.loop(Loop.TYPING),
        )
    } else null
    val maxHop = if (motion.particles) 3 else 1
    Spacer(
        modifier
            .size(width = 39.dp, height = 21.dp)
            .semantics { contentDescription = description }
            .drawWithCache {
                // grid: 3 embers of 3 x 4 pixels, 2 pixel gaps = 13 wide; 3 pixels of hop room on top
                val p = max(1f, floor(minOf(size.width / 13f, size.height / 7f)))
                val ox = (size.width - 13 * p) / 2f
                val oy = (size.height - 7 * p) / 2f
                val unit = Size(p, p)
                onDrawBehind {
                    val t = phase?.value ?: 0f
                    for (i in 0 until 3) {
                        val ph = ((t - i * 0.17f) % 1f + 1f) % 1f
                        val hop = if (phase != null && ph < 0.45f) sin(PI.toFloat() * ph / 0.45f) else 0f
                        val k = (hop * maxHop).roundToInt()
                        val x = ox + i * 5 * p
                        val y = oy + (3 - k) * p
                        drawRect(hot, Offset(x + p, y), unit)
                        drawRect(color, Offset(x, y + p), Size(3 * p, 2 * p))
                        drawRect(cool, Offset(x + p, y + 3 * p), unit)
                    }
                }
            },
    )
}
