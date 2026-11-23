package dev.pocketagent.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.PixelFont
import dev.pocketagent.app.ui.theme.Pocket
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
) {
    val px = with(LocalDensity.current) { pixel.toPx() }.roundToInt().coerceAtLeast(1)
    Spacer(modifier.then(PixelTextElement(text, color, shadow, px, maxLines, center)))
}

private data class PixelTextElement(
    val text: String,
    val color: Color,
    val shadow: Color?,
    val px: Int,
    val maxLines: Int,
    val center: Boolean,
) : ModifierNodeElement<PixelTextNode>() {
    override fun create() = PixelTextNode(text, color, shadow, px, maxLines, center)

    override fun update(node: PixelTextNode) {
        node.update(text, color, shadow, px, maxLines, center)
    }
}

private class PixelTextNode(
    private var text: String,
    private var color: Color,
    private var shadow: Color?,
    private var px: Int,
    private var maxLines: Int,
    private var center: Boolean,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode, SemanticsModifierNode {
    private var built: Built? = null

    fun update(text: String, color: Color, shadow: Color?, px: Int, maxLines: Int, center: Boolean) {
        val relayout = text != this.text || px != this.px || maxLines != this.maxLines ||
            center != this.center || (shadow != null) != (this.shadow != null)
        val semanticsChanged = text != this.text
        this.text = text; this.color = color; this.shadow = shadow
        this.px = px; this.maxLines = maxLines; this.center = center
        if (relayout) invalidateMeasurement()
        if (semanticsChanged) invalidateSemantics()
        invalidateDraw()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val b = build(text, px, maxW, constraints.minWidth, maxLines, center, shadow != null)
        built = b
        val w = b.width.coerceIn(constraints.minWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
        val h = b.height.coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        return layout(w, h) {}
    }

    override fun ContentDrawScope.draw() {
        val b = built ?: return
        val s = shadow
        val paint = b.paint
        if (s != null) {
            translate(px.toFloat(), px.toFloat()) {
                drawPath(b.path, s)
                if (paint != null) drawExtras(b, paint, s)
            }
        }
        drawPath(b.path, color)
        if (paint != null) drawExtras(b, paint, color)
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExtras(b: Built, paint: Paint, c: Color) {
        paint.color = c.toArgb()
        val canvas = drawContext.canvas.nativeCanvas
        for (e in b.extras) canvas.drawText(e.s, e.x, e.y, paint)
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        this.text = AnnotatedString(this@PixelTextNode.text)
    }
}

private class Extra(val s: String, val x: Float, val y: Float)

private class Built(val width: Int, val height: Int, val path: Path, val extras: List<Extra>, val paint: Paint?)

/**
 * Wraps and lays out [raw] into one Path of pixel-aligned rectangles (device pixels, no scaling at draw
 * time). Runs during measure only; nothing here executes in the draw loop.
 */
private fun build(
    raw: String, px: Int, maxW: Int, minW: Int, maxLines: Int, center: Boolean, hasShadow: Boolean,
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

    fun strWidth(s: String): Int {
        var w = 0
        var n = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            w += cpWidth(cp)
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

    val path = Path()
    val extras = ArrayList<Extra>(0)
    val pxf = px.toFloat()
    for ((li, line) in lines.withIndex()) {
        var x = if (center) (w - shadowExtra - widths[li]) / 2 else 0
        val y0 = li * (lineH + px)
        var i = 0
        var first = true
        while (i < line.length) {
            val cp = line.codePointAt(i)
            i += Character.charCount(cp)
            if (!first) x += px
            first = false
            val g = if (cp < 0x10000) PixelFont.glyph(cp.toChar()) else null
            if (g != null) {
                for (r in 0 until PixelFont.ROWS) {
                    val row = g.rows[r]
                    var c = 0
                    while (c < g.width) {
                        if (row[c] == 'x') {
                            val start = c
                            while (c < g.width && row[c] == 'x') c++
                            path.addRect(Rect(x + start * pxf, y0 + r * pxf, x + c * pxf, y0 + (r + 1) * pxf))
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
    return Built(w, h, path, extras, paint)
}
