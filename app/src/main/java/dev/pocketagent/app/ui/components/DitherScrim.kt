package dev.pocketagent.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.designUnitPx

/**
 * The 4 x 4 ordered-dither patterns behind the scrim (pattern 6 of the design language). Step n of 0..4 turns on
 * the cells whose Bayer value is below 2 * n: 0, 2, 4, 6 or 8 of 16 cells, each step a superset of the one
 * before. Cell index = row * 4 + column. Pure.
 */
object DitherPattern {
    const val STEPS = 4

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun cells(step: Int): Set<Int> {
        val s = step.coerceIn(0, STEPS)
        val out = LinkedHashSet<Int>()
        for (i in BAYER.indices) if (BAYER[i] < 2 * s) out.add(i)
        return out
    }
}

/** One tile of a pattern as an image of 4 x 4 design pixels, cached per (pixel, step, colour). */
internal object DitherTiles {
    private val cache = HashMap<Triple<Int, Int, Int>, ImageBitmap>()

    @Synchronized
    fun tile(unitPx: Int, step: Int, color: Color): ImageBitmap {
        val key = Triple(unitPx, step, color.toArgb())
        cache[key]?.let { return it }
        if (cache.size > MAX_TILES) cache.clear()
        val u = unitPx.coerceAtLeast(1)
        val bitmap = ImageBitmap(4 * u, 4 * u)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { this.color = color }
        for (cell in DitherPattern.cells(step)) {
            val left = (cell % 4) * u.toFloat()
            val top = (cell / 4) * u.toFloat()
            canvas.drawRect(left, top, left + u, top + u, paint)
        }
        cache[key] = bitmap
        return bitmap
    }

    private const val MAX_TILES = 48
}

/** A brush that repeats the dither tile. Draw it with drawRect or drawPath; the pattern starts at the origin. */
internal fun ditherBrush(unitPx: Int, step: Int, color: Color): ShaderBrush =
    ShaderBrush(ImageShader(DitherTiles.tile(unitPx, step, color), TileMode.Repeated, TileMode.Repeated))

/**
 * The signature of the app: instead of fading a black layer, a dither pattern fills in. [step] 0 draws
 * nothing, 4 covers half of the pixels. Drawn as one repeated tile, so it works on every Android version.
 */
@Composable
fun DitherScrim(step: Int, modifier: Modifier = Modifier, color: Color = Pocket.colors.scrim) {
    val s = step.coerceIn(0, DitherPattern.STEPS)
    if (s == 0) return
    val unit = designUnitPx(LocalDensity.current.density)
    val brush = remember(unit, s, color) { ditherBrush(unit, s, color) }
    Box(modifier.drawBehind { drawRect(brush) })
}
