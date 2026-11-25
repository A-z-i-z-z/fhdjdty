package dev.pocketagent.app.ui.hermes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.pocketagent.app.hermes.TermLine
import dev.pocketagent.app.ui.theme.PocketColors

/** Brightest the console may be dimmed to: a colour darker than this is lifted towards white so it stays readable on the dark console. */
private const val MIN_LUMINANCE = 0.34f

private fun readable(c: Color): Color {
    if (c.luminance() >= MIN_LUMINANCE) return c
    // Blend towards white in steps just big enough to reach the minimum (at most 85 %).
    var f = 0.1f
    var out = c
    while (out.luminance() < MIN_LUMINANCE && f <= 0.85f) { out = lerp(c, Color.White, f); f += 0.1f }
    return out
}

private fun brighter(c: Color): Color = lerp(c, Color.White, 0.28f)

/**
 * The 16 ANSI colours as the console shows them (indexes as in [dev.pocketagent.app.hermes.AnsiSpan.fg]): red, green, yellow
 * and magenta come from the theme (error, success, warning, accent), the rest are fixed hues; each is lifted to be readable on
 * the console, which is dark on every theme. [text] is the console's normal output colour and [muted] its dim colour.
 */
internal fun ansiPalette(c: PocketColors, text: Color, muted: Color): List<Color> {
    val red = readable(c.error)
    val green = readable(c.success)
    val yellow = readable(c.warning)
    val blue = Color(0xFF6C9BEF)
    val magenta = readable(c.accent)
    val cyan = Color(0xFF5FC9C9)
    return listOf(
        Color(0xFF7D746A), red, green, yellow, blue, magenta, cyan, text,
        muted, brighter(red), brighter(green), brighter(yellow), brighter(blue), brighter(magenta), brighter(cyan), Color.White,
    )
}

/** The line's text with its colour and bold runs applied; offsets that do not fit the text are clamped. */
internal fun ansiAnnotated(line: TermLine, palette: List<Color>): AnnotatedString = buildAnnotatedString {
    append(line.text)
    val n = line.text.length
    for (s in line.spans) {
        val start = s.start.coerceIn(0, n)
        val end = s.end.coerceIn(start, n)
        if (end <= start) continue
        val color = if (s.fg in palette.indices) palette[s.fg] else Color.Unspecified
        addStyle(SpanStyle(color = color, fontWeight = if (s.bold) FontWeight.Bold else null), start, end)
    }
}
