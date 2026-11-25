package dev.pocketagent.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.bevelRim
import dev.pocketagent.app.ui.theme.bevelShade
import dev.pocketagent.app.ui.theme.designUnitPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Appends a rectangle whose four corners are cut in a staircase of [n] steps of [p] device pixels
 * (n = 1: one missing pixel; n = 2: a 2-step chamfer). Coordinates are device pixels.
 */
internal fun Path.stepRect(l: Float, t: Float, r: Float, b: Float, n: Int, p: Float) {
    val k = min(n, (min(r - l, b - t) / p / 2f).toInt()).coerceAtLeast(0)
    if (k == 0) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
        return
    }
    moveTo(l, t + k * p)
    for (i in k - 1 downTo 0) {
        lineTo(l + (k - i) * p, t + (i + 1) * p)
        lineTo(l + (k - i) * p, t + i * p)
    }
    lineTo(r - k * p, t)
    for (i in 0 until k) {
        lineTo(r - (k - i) * p, t + (i + 1) * p)
        lineTo(r - (k - i - 1) * p, t + (i + 1) * p)
    }
    lineTo(r, b - k * p)
    for (i in 0 until k) {
        lineTo(r - (i + 1) * p, b - (k - i) * p)
        lineTo(r - (i + 1) * p, b - (k - i - 1) * p)
    }
    lineTo(l + k * p, b)
    for (i in 0 until k) {
        lineTo(l + (k - i) * p, b - (i + 1) * p)
        lineTo(l + (k - i - 1) * p, b - (i + 1) * p)
    }
    close()
}

/** Whole device pixels for one design pixel. */
internal fun Density.designPx(pixel: Dp): Float = max(1f, pixel.toPx().roundToInt().toFloat())

/** Rectangle with staircase corners ([corner] steps of [pixel]); use with clip / background. */
@Stable
class PixelShape(private val corner: Int = 2, private val pixel: Dp = 2.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = density.designPx(pixel)
        return Outline.Generic(Path().apply { stepRect(0f, 0f, size.width, size.height, corner, p) })
    }

    override fun equals(other: Any?): Boolean = other is PixelShape && other.corner == corner && other.pixel == pixel
    override fun hashCode(): Int = corner * 31 + pixel.hashCode()
}

/**
 * Draws the pixel look behind a component: 1-pixel border, face, optional lit top line / shaded bottom
 * line, optional ember glow rings outside, optional dithered drop shadow, optional bottom "lip" that the face
 * sinks into when pressed. The lip is drawn always; the face moves down by [pressDepthPx] (at the depth of
 * the lip the picture is the old pressed picture). All paths are built once per size in the cache block;
 * the draw block only fills them.
 */
internal fun Modifier.pixelSurface(
    face: Color,
    border: Color,
    corner: Int = 2,
    pixel: Dp = 2.dp,
    highlight: Color? = null,
    shade: Color? = null,
    glow: Color? = null,
    glowAlpha: () -> Float = { 0.2f },
    lip: Color? = null,
    pressDepthPx: (() -> Int)? = null,
    shadow: Color? = null,
): Modifier = drawWithCache {
    val p = designPx(pixel)
    val w = size.width
    val lipH = if (lip != null) p else 0f
    val h = size.height - lipH
    val n = min(corner, (min(w, h) / p / 2f).toInt()).coerceAtLeast(0)
    val outer = Path().apply { stepRect(0f, 0f, w, h, n, p) }
    val inner = Path().apply { stepRect(p, p, w - p, h - p, max(n - 1, 0), p) }
    val lipPath = if (lip != null) Path().apply { stepRect(0f, p, w, h + p, n, p) } else null
    val ring1 = if (glow != null) Path().apply { stepRect(-p, -p, w + p, h + p, n + 1, p) } else null
    val ring2 = if (glow != null) Path().apply { stepRect(-2 * p, -2 * p, w + 2 * p, h + 2 * p, n + 2, p) } else null
    val shadowPath = if (shadow != null) Path().apply { stepRect(2 * p, 2 * p, w + 2 * p, h + 2 * p, n, p) } else null
    val shadowBrush = if (shadow != null) ditherBrush(designUnitPx(density), 2, shadow) else null
    val hlX = if (n >= 1) n * p else p
    val hlW = w - 2 * hlX
    val hlSize = Size(max(hlW, 0f), p)
    onDrawBehind {
        if (glow != null && ring1 != null && ring2 != null) {
            val a = glowAlpha()
            drawPath(ring2, glow, alpha = a * 0.45f)
            drawPath(ring1, glow, alpha = a)
        }
        if (shadowPath != null && shadowBrush != null) drawPath(shadowPath, shadowBrush)
        val sink = pressDepthPx?.invoke()?.toFloat() ?: 0f
        if (lipPath != null && lip != null) drawPath(lipPath, lip)
        translate(0f, sink) {
            drawPath(outer, border)
            drawPath(inner, face)
            if (highlight != null && hlW > 0f) drawRect(highlight, Offset(hlX, p), hlSize)
            if (shade != null && hlW > 0f) drawRect(shade, Offset(hlX, h - 2 * p), hlSize)
        }
    }
}

/** The five looks of a panel. RAISED is today's panel. */
enum class PanelVariant { FLAT, RAISED, INSET, GLOWING, FLOATING }

/** Pure: an old call with `highlight = false` and no variant means FLAT; every other variant stands as given. */
fun resolveVariant(highlight: Boolean, variant: PanelVariant): PanelVariant =
    if (!highlight && variant == PanelVariant.RAISED) PanelVariant.FLAT else variant

/**
 * Stone-and-wood style container: stepped pixel corners, a pixel-thick [border], and a lit top edge plus
 * shaded bottom edge for depth ([variant]: RAISED lit above and shaded below, INSET the other way round,
 * FLAT neither, GLOWING raised plus two rings of firelight, FLOATING raised plus a dithered drop shadow).
 * Content is clipped to the shape; add your own padding. With [highlight] true and the default variant it
 * draws exactly the pixels it always drew.
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.surface,
    border: Color = Pocket.colors.outline,
    corner: Int = 2,
    pixel: Dp = 2.dp,
    highlight: Boolean = true,
    variant: PanelVariant = PanelVariant.RAISED,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Pocket.colors
    val dark = c.isDark
    val shape = remember(corner, pixel) { PixelShape(corner, pixel) }
    val v = resolveVariant(highlight, variant)
    val lit = bevelRim(color, dark)
    val shaded = bevelShade(color, dark)
    val hl = when (v) {
        PanelVariant.FLAT -> null
        PanelVariant.INSET -> shaded
        else -> lit
    }
    val sh = when (v) {
        PanelVariant.FLAT -> null
        PanelVariant.INSET -> lit
        else -> shaded
    }
    Box(
        modifier = modifier
            .pixelSurface(
                face = color, border = border, corner = corner, pixel = pixel, highlight = hl, shade = sh,
                glow = if (v == PanelVariant.GLOWING) c.glow else null,
                shadow = if (v == PanelVariant.FLOATING) c.scrim else null,
            )
            .clip(shape),
        content = content,
    )
}
