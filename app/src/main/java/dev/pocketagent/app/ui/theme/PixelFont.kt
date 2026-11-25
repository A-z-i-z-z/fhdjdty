package dev.pocketagent.app.ui.theme

import java.text.Normalizer

/**
 * One glyph on the 12-row font grid. [rows] always has [PixelFont.ROWS] strings of [width] chars,
 * 'x' = pixel on, '.' = off.
 */
class PixelGlyph(val width: Int, val rows: Array<String>)

/**
 * Hand-drawn bitmap font. Glyphs are 5 px wide (some narrower: i l j I ! . , : ; ( ) etc.) and live on
 * a 12-row grid so every glyph of a line shares one baseline:
 *
 *   rows 0-1   accent marks over capitals (acute, grave, circumflex, diaeresis, breve, dot)
 *   row  2     gap (accents over lowercase sit on rows 2-3)
 *   rows 3-9   capitals, digits and ascenders (7 px high); lowercase x-height is rows 5-9
 *   rows 10-11 descenders and cedillas (g j p q y , ; ç ş)
 *
 * Covers A-Z a-z 0-9, ASCII punctuation, and Turkish / Kurdish / French letters
 * (Ç Ş Ğ Ü Ö İ I Ê Î Û É È À Â Ô Ù Ë Ï and lowercase incl. dotless ı). Typographic quotes, dashes and the
 * ellipsis are mapped to ASCII by [prepare]. Anything else has no glyph: see PixelText for the fallback.
 */
object PixelFont {
    const val ROWS = 12
    const val CAP_TOP = 3
    const val CAP_HEIGHT = 7
    const val X_TOP = 5

    /** First grid row below the baseline. */
    const val BASELINE_ROW = 10
    const val SPACE_WIDTH = 3
    const val SPACING = 1

    private val glyphs = HashMap<Char, PixelGlyph>(256)

    fun glyph(c: Char): PixelGlyph? = if (c == ' ') SPACE else glyphs[c]

    private val SPACE = PixelGlyph(SPACE_WIDTH, Array(ROWS) { "..." })

    /** Normalises (NFC) and maps typographic punctuation to what the font can draw. */
    fun prepare(text: String): String {
        var t = text
        if (!Normalizer.isNormalized(t, Normalizer.Form.NFC)) t = Normalizer.normalize(t, Normalizer.Form.NFC)
        var needsMap = false
        for (ch in t) if (ch.code > 0x7E && MAP.containsKey(ch)) { needsMap = true; break }
        if (!needsMap) return t
        val sb = StringBuilder(t.length + 4)
        for (ch in t) {
            val m = if (ch.code > 0x7E) MAP[ch] else null
            if (m != null) sb.append(m) else sb.append(ch)
        }
        return sb.toString()
    }

    /** Width in grid columns of one line (no newline), including 1 px spacing between glyphs. */
    fun lineCols(line: String): Int {
        var w = 0
        var first = true
        for (ch in line) {
            val g = glyph(ch) ?: continue
            if (!first) w += SPACING
            w += g.width
            first = false
        }
        return w
    }

    /**
     * Width in grid columns of [text] on one line, spacing included, or null when a character has no glyph
     * (or the text has a line break): then PixelText would fall back to the system font. Pure.
     */
    fun textCols(text: String): Int? {
        val t = prepare(text).replace('\t', ' ')
        var w = 0
        var first = true
        for (ch in t) {
            if (ch < ' ') return null
            val g = glyph(ch) ?: return null
            if (!first) w += SPACING
            w += g.width
            first = false
        }
        return w
    }

    /**
     * Kerning of one pair: 0, or -1 when the second glyph moves one column closer. Only pairs whose strokes
     * cannot touch in the same row are listed (a unit test proves it). Off in Wave 0: PixelText applies it
     * only when asked for.
     */
    fun kern(prev: Char, next: Char): Int = if (KERN.contains((prev.code shl 16) or next.code)) -1 else 0

    /** The kerned pairs, two characters each. */
    val KERN_PAIRS: List<String> = listOf("AT", "LT", "LV", "LY", "TA", "FA", "T.", "T,", "Y.", "V.", "r.", "r,")

    private val KERN: HashSet<Int> = HashSet<Int>().also { set ->
        for (p in KERN_PAIRS) set.add((p[0].code shl 16) or p[1].code)
    }

    // ---------------------------------------------------------------- data

    private val MAP = mapOf(
        '‘' to "'", '’' to "'", '‚' to ",", '“' to "\"", '”' to "\"",
        '–' to "-", '—' to "-", '−' to "-", '…' to "...", ' ' to " ",
    )

    /** [spec] = rows joined by '/', first row sits on grid row [top]. */
    private fun def(c: Char, top: Int, spec: String) {
        val src = spec.split('/')
        var w = 0
        for (r in src) if (r.length > w) w = r.length
        val rows = Array(ROWS) { r ->
            val i = r - top
            if (i in src.indices) src[i].padEnd(w, '.') else ".".repeat(w)
        }
        glyphs[c] = PixelGlyph(w, rows)
    }

    private fun overlay(base: PixelGlyph, startRow: Int, marks: List<String>): PixelGlyph {
        val rows = base.rows.map { it.toCharArray() }
        for ((i, m) in marks.withIndex()) {
            val r = startRow + i
            if (r !in rows.indices) continue
            // centre the mark in the glyph when widths differ
            val off = (base.width - m.length) / 2
            for ((j, ch) in m.withIndex()) {
                val x = j + off
                if (ch == 'x' && x in 0 until base.width) rows[r][x] = 'x'
            }
        }
        return PixelGlyph(base.width, Array(ROWS) { String(rows[it]) })
    }

    private fun accent(c: Char, base: Char, mark: List<String>, startRow: Int) {
        val b = glyphs[base] ?: return
        glyphs[c] = overlay(b, startRow, mark)
    }

    private val ACUTE = listOf("...x.", "..x..")
    private val GRAVE = listOf(".x...", "..x..")
    private val CIRC = listOf("..x..", ".x.x.")
    private val DIAER = listOf(".....", ".x.x.")
    private val BREVE = listOf(".x.x.", "..x..")
    private val DOT = listOf(".....", "..x..")
    private val CIRC3 = listOf(".x.", "x.x")
    private val DIAER3 = listOf("...", "x.x")
    private val DOT3 = listOf("...", ".x.")
    private val CEDILLA = listOf("..x..", ".xx..")

    init {
        // ---- capitals (7 rows from row 3)
        def('A', 3, ".xxx./x...x/x...x/xxxxx/x...x/x...x/x...x")
        def('B', 3, "xxxx./x...x/x...x/xxxx./x...x/x...x/xxxx.")
        def('C', 3, ".xxxx/x..../x..../x..../x..../x..../.xxxx")
        def('D', 3, "xxxx./x...x/x...x/x...x/x...x/x...x/xxxx.")
        def('E', 3, "xxxxx/x..../x..../xxxx./x..../x..../xxxxx")
        def('F', 3, "xxxxx/x..../x..../xxxx./x..../x..../x....")
        def('G', 3, ".xxxx/x..../x..../x.xxx/x...x/x...x/.xxxx")
        def('H', 3, "x...x/x...x/x...x/xxxxx/x...x/x...x/x...x")
        def('I', 3, "xxx/.x./.x./.x./.x./.x./xxx")
        def('J', 3, "....x/....x/....x/....x/....x/x...x/.xxx.")
        def('K', 3, "x...x/x..x./x.x../xx.../x.x../x..x./x...x")
        def('L', 3, "x..../x..../x..../x..../x..../x..../xxxxx")
        def('M', 3, "x...x/xx.xx/x.x.x/x.x.x/x...x/x...x/x...x")
        def('N', 3, "x...x/xx..x/xx..x/x.x.x/x..xx/x..xx/x...x")
        def('O', 3, ".xxx./x...x/x...x/x...x/x...x/x...x/.xxx.")
        def('P', 3, "xxxx./x...x/x...x/xxxx./x..../x..../x....")
        def('Q', 3, ".xxx./x...x/x...x/x...x/x.x.x/x..x./.xx.x")
        def('R', 3, "xxxx./x...x/x...x/xxxx./x.x../x..x./x...x")
        def('S', 3, ".xxxx/x..../x..../.xxx./....x/....x/xxxx.")
        def('T', 3, "xxxxx/..x../..x../..x../..x../..x../..x..")
        def('U', 3, "x...x/x...x/x...x/x...x/x...x/x...x/.xxx.")
        def('V', 3, "x...x/x...x/x...x/x...x/x...x/.x.x./..x..")
        def('W', 3, "x...x/x...x/x...x/x.x.x/x.x.x/xx.xx/x...x")
        def('X', 3, "x...x/x...x/.x.x./..x../.x.x./x...x/x...x")
        def('Y', 3, "x...x/x...x/.x.x./..x../..x../..x../..x..")
        def('Z', 3, "xxxxx/....x/...x./..x../.x.../x..../xxxxx")

        // ---- digits
        def('0', 3, ".xxx./x...x/x..xx/x.x.x/xx..x/x...x/.xxx.")
        def('1', 3, "..x../.xx../..x../..x../..x../..x../.xxx.")
        def('2', 3, ".xxx./x...x/....x/...x./..x../.x.../xxxxx")
        def('3', 3, "xxxx./....x/....x/.xxx./....x/....x/xxxx.")
        def('4', 3, "...x./..xx./.x.x./x..x./xxxxx/...x./...x.")
        def('5', 3, "xxxxx/x..../xxxx./....x/....x/x...x/.xxx.")
        def('6', 3, ".xxx./x..../x..../xxxx./x...x/x...x/.xxx.")
        def('7', 3, "xxxxx/....x/...x./..x../.x.../.x.../.x...")
        def('8', 3, ".xxx./x...x/x...x/.xxx./x...x/x...x/.xxx.")
        def('9', 3, ".xxx./x...x/x...x/.xxxx/....x/....x/.xxx.")

        // ---- lowercase ascenders (7 rows from row 3)
        def('b', 3, "x..../x..../xxxx./x...x/x...x/x...x/xxxx.")
        def('d', 3, "....x/....x/.xxxx/x...x/x...x/x...x/.xxxx")
        def('f', 3, "..xx/.x../xxx./.x../.x../.x../.x..")
        def('h', 3, "x..../x..../xxxx./x...x/x...x/x...x/x...x")
        def('k', 3, "x..../x..../x..x./x.x../xx.../x.x../x..x.")
        def('l', 3, "xx./.x./.x./.x./.x./.x./xxx")
        def('i', 3, ".x./.../xx./.x./.x./.x./xxx")
        def('j', 3, "..x/.../..x/..x/..x/..x/..x/..x/xx.")
        def('t', 4, ".x../xxx./.x../.x../.x../..xx")
        // ---- lowercase x-height (5 rows from row 5)
        def('a', 5, ".xxx./....x/.xxxx/x...x/.xxxx")
        def('c', 5, ".xxx./x...x/x..../x...x/.xxx.")
        def('e', 5, ".xxx./x...x/xxxxx/x..../.xxx.")
        def('m', 5, "xxxx./x.x.x/x.x.x/x.x.x/x.x.x")
        def('n', 5, "xxxx./x...x/x...x/x...x/x...x")
        def('o', 5, ".xxx./x...x/x...x/x...x/.xxx.")
        def('r', 5, "x.xx/xx../x.../x.../x...")
        def('s', 5, ".xxxx/x..../.xxx./....x/xxxx.")
        def('u', 5, "x...x/x...x/x...x/x...x/.xxxx")
        def('v', 5, "x...x/x...x/x...x/.x.x./..x..")
        def('w', 5, "x...x/x...x/x.x.x/x.x.x/.x.x.")
        def('x', 5, "x...x/.x.x./..x../.x.x./x...x")
        def('z', 5, "xxxxx/...x./..x../.x.../xxxxx")
        def('ı', 5, "xx./.x./.x./.x./xxx") // dotless i
        // ---- descenders (x-height + 2 rows)
        def('g', 5, ".xxxx/x...x/x...x/x...x/.xxxx/....x/.xxx.")
        def('p', 5, "xxxx./x...x/x...x/x...x/xxxx./x..../x....")
        def('q', 5, ".xxxx/x...x/x...x/x...x/.xxxx/....x/....x")
        def('y', 5, "x...x/x...x/x...x/.xxxx/....x/....x/.xxx.")

        // ---- punctuation and symbols
        def('!', 3, "x/x/x/x/x/./x")
        def('"', 3, "x.x/x.x")
        def('#', 3, ".x.x./.x.x./xxxxx/.x.x./xxxxx/.x.x./.x.x.")
        def('$', 3, "..x../.xxxx/x.x../.xxx./..x.x/xxxx./..x..")
        def('%', 3, "xx..x/xx.x./...x./..x../.x.../.x.xx/x..xx")
        def('&', 3, ".xx../x..x./x.x../.x.../x.x.x/x..x./.xx.x")
        def('\'', 3, "x/x")
        def('(', 3, ".x/x./x./x./x./x./.x")
        def(')', 3, "x./.x/.x/.x/.x/.x/x.")
        def('*', 4, "x.x.x/.xxx./xxxxx/.xxx./x.x.x")
        def('+', 4, "..x../..x../xxxxx/..x../..x..")
        def(',', 8, "xx/xx/x.")
        def('-', 6, "xxx")
        def('.', 8, "xx/xx")
        def('/', 3, "....x/....x/...x./..x../.x.../x..../x....")
        def(':', 5, "xx/xx/../xx/xx")
        def(';', 5, "xx/xx/../xx/xx/x.")
        def('<', 3, "...x./..x../.x.../x..../.x.../..x../...x.")
        def('=', 5, "xxxxx/...../xxxxx")
        def('>', 3, ".x.../..x../...x./....x/...x./..x../.x...")
        def('?', 3, ".xxx./x...x/....x/...x./..x../...../..x..")
        def('@', 3, ".xxx./x...x/x.xxx/x.x.x/x.xx./x..../.xxxx")
        def('[', 3, "xxx/x../x../x../x../x../xxx")
        def('\\', 3, "x..../x..../.x.../..x../...x./....x/....x")
        def(']', 3, "xxx/..x/..x/..x/..x/..x/xxx")
        def('^', 3, ".x./x.x")
        def('_', 10, "xxxxx")
        def('`', 3, "x./.x")
        def('{', 3, "..x/.x./.x./x../.x./.x./..x")
        def('|', 3, "x/x/x/x/x/x/x/x/x")
        def('}', 3, "x../.x./.x./..x/.x./.x./x..")
        def('~', 6, ".xx.x/x.xx.")
        def('•', 5, ".x./xxx/.x.")      // bullet
        def('·', 6, "x")                 // middle dot
        def('→', 4, "..x../...x./xxxxx/...x./..x..")   // arrow right
        def('←', 4, "..x../.x.../xxxxx/.x.../..x..")   // arrow left
        def('×', 4, "x...x/.x.x./..x../.x.x./x...x")   // multiplication sign
        def('✓', 4, "....x/...xx/x.xx./.xx../..x..")   // check mark

        // ---- accented capitals (marks on rows 0-1)
        accent('Ç', 'C', CEDILLA, 10)
        accent('Ş', 'S', CEDILLA, 10)
        accent('Ğ', 'G', BREVE, 0)
        accent('Ü', 'U', DIAER, 0)
        accent('Ö', 'O', DIAER, 0)
        accent('İ', 'I', DOT3, 0)
        accent('Ê', 'E', CIRC, 0)
        accent('Î', 'I', CIRC3, 0)
        accent('Û', 'U', CIRC, 0)
        accent('É', 'E', ACUTE, 0)
        accent('È', 'E', GRAVE, 0)
        accent('À', 'A', GRAVE, 0)
        accent('Â', 'A', CIRC, 0)
        accent('Ô', 'O', CIRC, 0)
        accent('Ù', 'U', GRAVE, 0)
        accent('Ë', 'E', DIAER, 0)
        accent('Ï', 'I', DIAER3, 0)
        // ---- accented lowercase (marks on rows 2-3)
        accent('ç', 'c', CEDILLA, 10)
        accent('ş', 's', CEDILLA, 10)
        accent('ğ', 'g', BREVE, 2)
        accent('ü', 'u', DIAER, 2)
        accent('ö', 'o', DIAER, 2)
        accent('ê', 'e', CIRC, 2)
        accent('î', 'ı', CIRC3, 2)
        accent('û', 'u', CIRC, 2)
        accent('é', 'e', ACUTE, 2)
        accent('è', 'e', GRAVE, 2)
        accent('à', 'a', GRAVE, 2)
        accent('â', 'a', CIRC, 2)
        accent('ô', 'o', CIRC, 2)
        accent('ù', 'u', GRAVE, 2)
        accent('ë', 'e', DIAER, 2)
        accent('ï', 'ı', DIAER3, 2)

        // Wave 0 additions, last: the v1 glyphs above are never edited (PixelFontGoldenTest proves it).
        defV2()
    }

    /** Pixel font v2: ligatures, the diaeresis on Y, guillemets and a few symbols. Needs the v1 glyphs. */
    private fun defV2() {
        // ligatures, 7 columns wide (capitals on rows 3 to 9, lowercase on rows 5 to 9)
        def('Æ', 3, "..xxxxx/.xx.x../.x..x../xxxxxx./x...x../x...x../x...xxx")
        def('æ', 5, ".xx.xx./...x..x/.xxxxxx/x..x.../.xxxxxx")
        def('Œ', 3, ".xxxxxx/x..x.../x..x.../x..xxx./x..x.../x..x.../.xxxxxx")
        def('œ', 5, ".xx.xx./x..x..x/x..xxxx/x..x.../.xx.xxx")
        // the diaeresis on Y (the capital sits on rows 3 to 9, so the mark is on rows 0 and 1)
        accent('Ÿ', 'Y', DIAER, 0)
        accent('ÿ', 'y', DIAER, 2)
        // guillemets on the x-height rows
        def('«', 5, "..x.x/.x.x./x.x../.x.x./..x.x")
        def('»', 5, "x.x../.x.x./..x.x/.x.x./x.x..")
        // symbols
        def('€', 3, ".xxxx/x..../xxxx./x..../xxxx./x..../.xxxx")
        def('£', 3, "..xx./.x..x/.x.../xxxx./.x.../.x.../xxxxx")
        def('°', 3, ".x./x.x/.x.")
        def('±', 3, "..x../..x../xxxxx/..x../..x../...../xxxxx")
        def('↑', 3, "..x../.x.x./x.x.x/..x../..x../..x../..x..")
        def('↓', 3, "..x../..x../..x../..x../x.x.x/.x.x./..x..")
    }
}
