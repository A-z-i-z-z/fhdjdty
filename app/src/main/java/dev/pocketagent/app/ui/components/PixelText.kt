package dev.pocketagent.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.PixelFont
import dev.pocketagent.app.ui.theme.PixelType
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Type
import dev.pocketagent.app.ui.theme.pixelFontPx
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Text in the hand-drawn bitmap font (see [PixelFont]): crisp, nearest-neighbour, 1 px letter spacing.
 * One font pixel is [pixel] dp rounded to a whole number of device pixels, so a 2.dp pixel at 420 dpi is
 * 5 device pixels and the capital height is 7 font pixels. The layout box is 12 font pixels high
 * (room for accents above and descenders below), so it is vertically centred on the capitals.
 *
 * It wraps at word boundaries to the width it is given (like Text), honours [maxLines] with a "..."
 * ellipsis, and with [center] centres each line inside the available width. Characters the font does not
 * have are drawn with the system font at matching height. [shadow] adds a 1-pixel drop shadow.
 *
 * The font pixel follows the text-size setting in three steps (see [pixelFontPx]) unless [followTextScale] is
 * false; at the default text size it is exactly the old rounded size. [kerning] pulls the listed pairs one
 * column closer (off by default). A line that is cut with "..." reports it to [CutProbe].
 *
 * Implementation: a plain Layout whose measure pass builds a list of pixel rectangles (stored in a
 * holder) and a drawBehind that fills them with drawRect.
 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.text,
    pixel: Dp = 2.dp,
    shadow: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    center: Boolean = false,
    followTextScale: Boolean = true,
    kerning: Boolean = false,
) {
    val density = LocalDensity.current
    val px = pixelFontPx(pixel.value, density.density, density.fontScale, followTextScale)
    val holder = remember { BuiltHolder() }
    val label = text
    val hasShadow = shadow != null
    Layout(
        modifier = modifier
            .semantics { this.text = AnnotatedString(label) }
            .drawBehind {
                val b = holder.built
                if (b != null && b.source == label && b.px == px) {
                    if (shadow != null) {
                        translate(px.toFloat(), px.toFloat()) { drawBuilt(b, shadow) }
                    }
                    drawBuilt(b, color)
                }
            },
        measurePolicy = MeasurePolicy { _, constraints ->
            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
            val b = build(label, px, maxW, constraints.minWidth, maxLines, center, hasShadow, kerning)
            val before = holder.built
            if (b.truncated && (before == null || !before.truncated || before.source != b.source || before.width != b.width)) {
                CutProbe.report(label)
            }
            holder.built = b
            val w = b.width.coerceIn(constraints.minWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
            val h = b.height.coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
            layout(w, h) {}
        },
    )
}

private class BuiltHolder {
    @Volatile var built: Built? = null
}

private fun DrawScope.drawBuilt(b: Built, c: Color) {
    val r = b.rects
    var i = 0
    while (i < b.count) {
        drawRect(c, Offset(r[i], r[i + 1]), Size(r[i + 2] - r[i], r[i + 3] - r[i + 1]))
        i += 4
    }
    val paint = b.paint
    if (paint != null) {
        paint.color = c.toArgb()
        val canvas = drawContext.canvas.nativeCanvas
        for (e in b.extras) canvas.drawText(e.s, e.x, e.y, paint)
    }
}

private class Extra(val s: String, val x: Float, val y: Float)

private class Built(
    val source: String, val px: Int, val width: Int, val height: Int,
    val rects: FloatArray, val count: Int, val extras: List<Extra>, val paint: Paint?,
    val truncated: Boolean = false,
)

/**
 * Wraps and lays out [raw] into a list of pixel-aligned rectangles (device pixels, no scaling at draw
 * time). Runs during measure only; nothing here executes in the draw loop.
 */
private fun build(
    raw: String, px: Int, maxW: Int, minW: Int, maxLines: Int, center: Boolean, hasShadow: Boolean,
    kerning: Boolean = false,
): Built {
    val text = PixelFont.prepare(raw).replace('\t', ' ').filter { it >= ' ' || it == '\n' }
    val shadowExtra = if (hasShadow) px else 0
    val limit = if (maxW == Int.MAX_VALUE) Int.MAX_VALUE else max(px, maxW - shadowExtra)

    var paint: Paint? = null
    fun paintOf(): Paint {
        var p = paint
        if (p == null) {
            p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = 9.8f * px // capital height of about 7 font pixels
            paint = p
        }
        return p
    }

    fun cpWidth(cp: Int): Int {
        if (cp < 0x10000) {
            val g = PixelFont.glyph(cp.toChar())
            if (g != null) return g.width * px
        }
        return ceil(paintOf().measureText(String(Character.toChars(cp)))).toInt()
    }

    fun kernOf(prev: Int, next: Int): Int =
        if (kerning && prev in 1 until 0x10000 && next in 1 until 0x10000) PixelFont.kern(prev.toChar(), next.toChar()) * px else 0

    fun strWidth(s: String): Int {
        var w = 0
        var n = 0
        var i = 0
        var prev = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            w += cpWidth(cp) + kernOf(prev, cp)
            prev = cp
            n++
        }
        return if (n == 0) 0 else w + (n - 1) * px
    }

    fun ellipsize(s: String): String {
        if (limit == Int.MAX_VALUE) return "$s..."
        var t = s.trimEnd()
        while (t.isNotEmpty() && strWidth("$t...") > limit) t = t.dropLast(1).trimEnd()
        return "$t..."
    }

    // ---- wrap into lines
    var lines = ArrayList<String>()
    var truncated = false
    for (para in text.split('\n')) {
        if (limit == Int.MAX_VALUE) { lines.add(para); continue }
        var cur = ""
        for (word in para.split(' ')) {
            val cand = if (cur.isEmpty()) word else "$cur $word"
            if (strWidth(cand) <= limit) { cur = cand; continue }
            if (cur.isNotEmpty()) { lines.add(cur); cur = "" }
            var rest = word
            while (rest.isNotEmpty() && strWidth(rest) > limit) {
                var k = rest.length
                while (k > 1 && strWidth(rest.substring(0, k)) > limit) k--
                lines.add(rest.substring(0, k))
                rest = rest.substring(k)
            }
            cur = rest
        }
        lines.add(cur)
    }
    if (maxLines in 1 until lines.size) {
        lines = ArrayList(lines.subList(0, maxLines))
        truncated = true
    }
    if (truncated) lines[lines.size - 1] = ellipsize(lines[lines.size - 1])
    if (lines.isEmpty()) lines.add("")

    // ---- measure and place
    val widths = IntArray(lines.size) { strWidth(lines[it]) }
    val natural = (widths.maxOrNull() ?: 0) + shadowExtra
    val w = max(natural, minW)
    val lineH = PixelFont.ROWS * px
    val h = lines.size * lineH + (lines.size - 1) * px + shadowExtra

    var rects = FloatArray(256)
    var count = 0
    fun addRect(l: Float, t: Float, r: Float, b: Float) {
        if (count + 4 > rects.size) rects = rects.copyOf(rects.size * 2)
        rects[count] = l; rects[count + 1] = t; rects[count + 2] = r; rects[count + 3] = b
        count += 4
    }
    val extras = ArrayList<Extra>(0)
    val pxf = px.toFloat()
    for ((li, line) in lines.withIndex()) {
        var x = if (center) (w - shadowExtra - widths[li]) / 2 else 0
        val y0 = li * (lineH + px)
        var i = 0
        var first = true
        var prev = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            i += Character.charCount(cp)
            if (!first) x += px + kernOf(prev, cp)
            first = false
            prev = cp
            val g = if (cp < 0x10000) PixelFont.glyph(cp.toChar()) else null
            if (g != null) {
                for (r in 0 until PixelFont.ROWS) {
                    val row = g.rows[r]
                    var c = 0
                    while (c < g.width) {
                        if (row[c] == 'x') {
                            val start = c
                            while (c < g.width && row[c] == 'x') c++
                            addRect(x + start * pxf, y0 + r * pxf, x + c * pxf, y0 + (r + 1) * pxf)
                        } else c++
                    }
                }
                x += g.width * px
            } else {
                extras.add(Extra(String(Character.toChars(cp)), x.toFloat(), (y0 + PixelFont.BASELINE_ROW * px).toFloat()))
                x += cpWidth(cp)
            }
        }
    }
    return Built(raw, px, w, h, rects, count, extras, paint, truncated)
}

/**
 * Pure: the first of [candidatesPx] (device pixels of one font pixel, in order of preference) at which a title of
 * [cols] grid columns fits in [availablePx]; null when [cols] is null (a character without a glyph) or none fits.
 */
fun pickTitleStep(cols: Int?, availablePx: Int, candidatesPx: List<Int>): Int? {
    if (cols == null) return null
    for (px in candidatesPx) if (cols.toLong() * px <= availablePx) return px
    return null
}

/**
 * A one-line title in the pixel font that is never cut while half the bar is free: it tries the font pixel
 * that follows the text-size setting, then the plain [pixel], then three quarters of it, and only when none of
 * them fits (or a character has no glyph) it falls back to the system font in [Type.title], cut with "...".
 */
@Composable
fun PixelTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.text,
    pixel: Dp = PixelType.title,
    shadow: Color? = null,
) {
    val density = LocalDensity.current
    val cols = remember(text) { PixelFont.textCols(text) }
    BoxWithConstraints(modifier) {
        val scaled = pixelFontPx(pixel.value, density.density, density.fontScale, true)
        val plain = pixelFontPx(pixel.value, density.density, density.fontScale, false)
        val small = max(1, (plain * 0.75f).roundToInt())
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val reserve = if (shadow != null) scaled else 0
        val pick = pickTitleStep(cols, if (room == Int.MAX_VALUE) room else max(0, room - reserve), listOf(scaled, plain, small).distinct())
        if (pick != null) {
            PixelText(
                text = text, color = color, pixel = with(density) { pick.toDp() }, shadow = shadow,
                maxLines = 1, followTextScale = false,
            )
        } else {
            Text(text = text, color = color, style = Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
