package dev.pocketagent.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Coverage and rules of the pixel font, v1 and v2. */
class PixelFontTest {
    /** Characters the font does not draw on purpose: the text falls back to the system font, which shows nothing. */
    private val expectedFallback: Set<Int> = setOf(
        0x200B, // zero width space
        0xFE0F, // variation selector 16 (asks for the emoji look of the character before it)
    )

    private fun allGlyphChars(): List<Char> = (0x20..0x2FFF).map { it.toChar() }.filter { PixelFont.glyph(it) != null }

    @Test
    fun everyGlyphRowHasItsWidth() {
        val chars = allGlyphChars()
        assertTrue("the font has glyphs", chars.size >= 140)
        for (ch in chars) {
            val g = PixelFont.glyph(ch)!!
            assertEquals("rows of U+%04X".format(ch.code), PixelFont.ROWS, g.rows.size)
            for (row in g.rows) {
                assertEquals("row width of U+%04X".format(ch.code), g.width, row.length)
                assertTrue("only x and dots in U+%04X".format(ch.code), row.all { it == 'x' || it == '.' })
            }
        }
    }

    @Test
    fun digitsAreTabular() {
        for (d in '0'..'9') assertEquals("digit $d", 5, PixelFont.glyph(d)!!.width)
    }

    @Test
    fun dottedCapitalIDiffersFromI() {
        val plain = PixelFont.glyph('I')!!
        val dotted = PixelFont.glyph('İ')!!
        assertNotEquals(plain.rows.toList(), dotted.rows.toList())
        assertTrue("the dot sits on row 1", dotted.rows[1].contains('x'))
        assertFalse("a plain I has no dot", plain.rows[1].contains('x'))
    }

    @Test
    fun coversEverythingInShippedStrings() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
        Assume.assumeTrue("resource folder not found from ${File(".").absolutePath}", res != null)
        val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val escape = Regex("""\\u([0-9a-fA-F]{4})""")
        val missing = sortedSetOf<Int>()
        val valuesDirs = res!!.listFiles { f -> f.isDirectory && f.name.startsWith("values") } ?: emptyArray()
        for (dir in valuesDirs) {
            val files = dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") } ?: continue
            for (file in files) {
                var text = comment.replace(file.readText(Charsets.UTF_8), "")
                text = escape.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
                var i = 0
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    i += Character.charCount(cp)
                    if (cp <= 0x7E || cp in expectedFallback) continue
                    val drawn = PixelFont.prepare(String(Character.toChars(cp)))
                    if (!drawn.all { PixelFont.glyph(it) != null }) missing.add(cp)
                }
            }
        }
        assertTrue(
            "Characters in the strings files that the pixel font cannot draw: " +
                missing.joinToString(" ") { "U+%04X".format(it) },
            missing.isEmpty(),
        )
    }

    @Test
    fun textColsNullOnUnknown() {
        val a = PixelFont.glyph('a')!!.width
        val b = PixelFont.glyph('b')!!.width
        assertEquals(a + PixelFont.SPACING + b, PixelFont.textCols("ab"))
        assertEquals(0, PixelFont.textCols(""))
        assertNotNull(PixelFont.textCols("Çağ İıŞ êîû"))
        assertNull(PixelFont.textCols("日本"))
        assertNull(PixelFont.textCols("two\nlines"))
        // typographic punctuation is mapped before it is measured
        assertEquals(PixelFont.textCols("a-b"), PixelFont.textCols("a–b"))
    }

    @Test
    fun kerningNeverOverlapsOrTouches() {
        assertTrue(PixelFont.KERN_PAIRS.isNotEmpty())
        for (pair in PixelFont.KERN_PAIRS) {
            assertEquals(2, pair.length)
            val first = PixelFont.glyph(pair[0])!!
            val second = PixelFont.glyph(pair[1])!!
            assertEquals("kern of $pair", -1, PixelFont.kern(pair[0], pair[1]))
            // Pulled one column closer the two glyphs sit side by side with no gap: no row may have the last
            // pixel of the first and the first pixel of the second both on (that would merge two strokes).
            for (r in 0 until PixelFont.ROWS) {
                val a = first.rows[r].last() == 'x'
                val b = second.rows[r].first() == 'x'
                assertFalse("$pair touches on row $r", a && b)
            }
        }
        assertEquals(0, PixelFont.kern('A', 'B'))
        assertEquals(0, PixelFont.kern('.', 'T'))
        // the pair table is directional: T then A is kerned, and so is A then T, but not every swap
        assertEquals(-1, PixelFont.kern('T', 'A'))
        assertEquals(0, PixelFont.kern('V', 'A'))
    }

    @Test
    fun v2GlyphsStayOnGrid() {
        // allowed rows per character: capitals 3 to 9, lowercase on the x-height rows 5 to 9
        val rows: Map<Char, (Int) -> Boolean> = mapOf(
            'Æ' to { r: Int -> r in 3..9 },
            'æ' to { r: Int -> r in 5..9 },
            'Œ' to { r: Int -> r in 3..9 },
            'œ' to { r: Int -> r in 5..9 },
            'Ÿ' to { r: Int -> r == 1 || r in 3..9 }, // the diaeresis on row 1
            'ÿ' to { r: Int -> r == 3 || r in 5..11 }, // the diaeresis on row 3, the descender below
            '«' to { r: Int -> r in 5..9 },
            '»' to { r: Int -> r in 5..9 },
            '€' to { r: Int -> r in 3..9 },
            '£' to { r: Int -> r in 3..9 },
            '°' to { r: Int -> r in 3..5 },
            '±' to { r: Int -> r in 3..9 },
            '↑' to { r: Int -> r in 3..9 },
            '↓' to { r: Int -> r in 3..9 },
        )
        for ((ch, allowed) in rows) {
            val g = PixelFont.glyph(ch)
            assertNotNull("U+%04X has a glyph".format(ch.code), g)
            var any = false
            for (r in 0 until PixelFont.ROWS) {
                for (c in g!!.rows[r].indices) {
                    if (g.rows[r][c] == 'x') {
                        any = true
                        assertTrue("U+%04X has a pixel on row $r".format(ch.code), allowed(r))
                    }
                }
            }
            assertTrue("U+%04X is not empty".format(ch.code), any)
        }
        for (ch in listOf('Æ', 'æ', 'Œ', 'œ')) {
            assertEquals("ligature width of U+%04X".format(ch.code), 7, PixelFont.glyph(ch)!!.width)
        }
        // v2 never changed a v1 letter: the plain Y has no mark on row 1, the one with the diaeresis has
        assertFalse(PixelFont.glyph('Y')!!.rows[1].contains('x'))
        assertTrue(PixelFont.glyph('Ÿ')!!.rows[1].contains('x'))
    }
}
