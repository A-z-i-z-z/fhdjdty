package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Ease
import dev.pocketagent.app.ui.theme.Loop
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.designUnitPx
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.ui.theme.snapToDesignPx

private const val BAND_STEPS = 12

/**
 * A placeholder in the shape of what is loading: a stepped block of the raised surface colour with a 25 percent
 * dither band that sweeps over it in twelve steps per [Loop.SHIMMER]. It stands still at REDUCED and OFF. The
 * width comes from the modifier. Spoken as "loading". Tag: skeleton.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, corner: Int = Corner.chip) {
    val c = Pocket.colors
    val motion = Pocket.motion
    val description = stringResource(R.string.ctl_loading)
    val phase: State<Float>? = if (motion.level == AnimLevel.FULL) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0f, targetValue = 1f, label = "shimmer",
            animationSpec = motion.loop(Loop.SHIMMER, RepeatMode.Restart, Ease.steps(BAND_STEPS)),
        )
    } else null
    val band = c.surface
    Spacer(
        modifier
            .height(height)
            .clip(PixelShape(corner))
            .background(c.surfaceHigh)
            .testTag("skeleton")
            .semantics { contentDescription = description }
            .drawWithCache {
                val unit = designUnitPx(density)
                val brush = ditherBrush(unit, 2, band)
                val w = size.width
                val h = size.height
                val bandW = snapToDesignPx(w / 3f, unit).coerceAtLeast(unit.toFloat())
                onDrawBehind {
                    val t = phase?.value ?: 0.4f
                    val x = snapToDesignPx(-bandW + (w + bandW) * t, unit)
                    drawRect(brush, Offset(x, 0f), Size(bandW, h))
                }
            },
    )
}
