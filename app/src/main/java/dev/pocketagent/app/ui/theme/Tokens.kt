package dev.pocketagent.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The tokens of docs/design-language.md section 2.1 and 2.2. New code uses these names instead of literal
// numbers; a number is never changed to fit a token (only exact equals are replaced).

/** Gaps and paddings. Layout spacing stays in dp; everything drawn in the pixel look is measured in the design pixel. */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Left and right screen margin. */
    val gutter: Dp = 16.dp

    /** Smallest touch target. */
    val touch: Dp = 48.dp
}

/** Stepped corners, in steps of the design pixel. */
object Corner {
    const val chip = 1
    const val card = 2
    const val sheet = 3
}

/** Border widths, in multiples of the design pixel. */
object Border {
    const val thin = 1
    const val focus = 1
}

/** Text styles of the system font (the pixel font has its own sizes, see [PixelType]). */
object Type {
    val title: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val reading: TextStyle = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp)
    val label: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val mono: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace)
}

/** The glyph pixel sizes of the pixel font that are in use today. */
object PixelType {
    val small: Dp = 1.5.dp
    val title: Dp = 2.dp
    val big: Dp = 3.dp
}

/** Four levels of elevation, drawn with lines and dither, never with a soft shadow. */
enum class Elevation { FLAT, RAISED, FLOATING, MODAL }

/**
 * The design pixel in whole device pixels: two dp, rounded to the nearest device pixel, at least one.
 * 2.0 gives 4, 2.625 gives 5, 3.0 gives 6, 3.5 gives 7.
 */
fun designUnitPx(density: Float): Int = max(1, (2f * density).roundToInt())

/** [steps] design pixels in device pixels. */
fun Density.u(steps: Int = 1): Float = steps * designUnitPx(density).toFloat()

/** Rounds [valuePx] to a whole multiple of [unitPx]. */
fun snapToDesignPx(valuePx: Float, unitPx: Int): Float {
    if (unitPx <= 0) return valuePx
    return (valuePx / unitPx).roundToInt() * unitPx.toFloat()
}

/**
 * Device pixels of one pixel-font pixel. [pixelDp] is multiplied by 0.75 below font scale 0.95, 1.0 up to
 * 1.15, 1.25 up to 1.6 and 1.5 above (when [follow]); the result in dp is capped at the larger of [pixelDp]
 * and 3, and rounded to whole device pixels, at least one. At font scale 1.0 this is exactly the old
 * round(pixelDp * density).
 */
fun pixelFontPx(pixelDp: Float, density: Float, fontScale: Float, follow: Boolean): Int {
    val m = when {
        !follow -> 1f
        fontScale < 0.95f -> 0.75f
        fontScale <= 1.15f -> 1f
        fontScale <= 1.6f -> 1.25f
        else -> 1.5f
    }
    val dp = min(pixelDp * m, max(pixelDp, 3f))
    return max(1, (dp * density).roundToInt())
}

/** The lit edge colour of a bevel on [face]: today's formula. */
fun bevelRim(face: Color, dark: Boolean): Color = lerp(face, Color.White, if (dark) 0.10f else 0.55f)

/** The shaded edge colour of a bevel on [face]: today's formula. */
fun bevelShade(face: Color, dark: Boolean): Color = lerp(face, Color.Black, if (dark) 0.22f else 0.07f)
