package dev.pocketagent.app.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Corner
import kotlin.math.min

/** The three stepped shapes of the pixel look (Corner.chip, card and sheet), ready for clip and background. */
object PixelShapes {
    val chip: Shape = PixelShape(Corner.chip)
    val card: Shape = PixelShape(Corner.card)
    val sheet: Shape = PixelShape(Corner.sheet)
}

/**
 * One corner's staircase as points, from (0, n * p) to (n * p, 0): the corner of a rectangle cut in [n] steps
 * of [p] pixels (a step is one move across and one move up). n = 0 gives the single point (0, 0). Pure.
 */
fun stepCornerOffsets(n: Int, p: Float): List<Pair<Float, Float>> {
    val k = if (n < 0) 0 else n
    val out = ArrayList<Pair<Float, Float>>(1 + 2 * k)
    out.add(0f to k * p)
    for (i in k - 1 downTo 0) {
        out.add((k - i) * p to (i + 1) * p)
        out.add((k - i) * p to i * p)
    }
    return out
}

/**
 * A rectangle with stepped corners on top and square corners below: the shape of a bottom sheet that is
 * clipped by the screen edge. The sheet component itself slides a normal panel half off the screen
 * instead; this shape is the fallback of the area file (a wrapped Material sheet).
 */
@Stable
class PixelTopShape(private val corner: Int = Corner.sheet, private val pixel: Dp = 2.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = density.designPx(pixel)
        val w = size.width
        val h = size.height
        val k = min(corner, (min(w, h) / p / 2f).toInt()).coerceAtLeast(0)
        val left = stepCornerOffsets(k, p)
        val path = Path().apply {
            moveTo(0f, h)
            for ((x, y) in left) lineTo(x, y)
            for (i in left.indices.reversed()) lineTo(w - left[i].first, left[i].second)
            lineTo(w, h)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is PixelTopShape && other.corner == corner && other.pixel == pixel
    override fun hashCode(): Int = corner * 31 + pixel.hashCode()
}
