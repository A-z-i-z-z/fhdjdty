package dev.pocketagent.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Black night: dark variants only, black behind, the panels one step lighter, and contrast that never drops.
 * The reference ratios of the formula (black on white 21.0, #777777 on black about 4.69) were recomputed with a
 * script before they were written here.
 */
class BlackNightTest {
    // WCAG 2.1 relative luminance and contrast ratio.
    private fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val black = Color(0xFF000000.toInt())

    @Test
    fun formulaReferenceValues() {
        assertEquals(21.0, ratio(Color.Black, Color.White), 0.001)
        assertEquals(4.689, ratio(Color(0xFF777777.toInt()), Color.Black), 0.005)
    }

    @Test
    fun lightIsUnchanged() {
        for (theme in ThemeId.entries) for (accent in AccentId.entries) {
            val c = paletteFor(theme, false, accent)
            assertSame("$theme/$accent", c, c.withBlackNight())
            assertSame("$theme/$accent", c, c.withBlackNight(true))
        }
    }

    @Test
    fun offIsUnchanged() {
        for (theme in ThemeId.entries) for (accent in AccentId.entries) {
            val c = paletteFor(theme, true, accent)
            assertSame("$theme/$accent", c, c.withBlackNight(false))
        }
    }

    @Test
    fun onIsTheDefault() {
        val c = paletteFor(ThemeId.NEWROZ, true, AccentId.THEME)
        assertEquals(c.withBlackNight(true), c.withBlackNight())
    }

    @Test
    fun darkBackgrounds() {
        for (theme in ThemeId.entries) for (accent in AccentId.entries) {
            val old = paletteFor(theme, true, accent)
            val n = old.withBlackNight()
            val id = "$theme/$accent"
            assertEquals(id, black, n.bg)
            assertEquals(id, black, n.bgDeep)
            assertEquals(id, old.bg, n.surface)
            assertEquals(id, old.surface, n.surfaceHigh)
            // Everything else is the old palette's.
            val expected = old.copy(bg = n.bg, bgDeep = n.bgDeep, surface = n.surface, surfaceHigh = n.surfaceHigh)
            assertEquals(id, expected, n)
            assertTrue(id, n.isDark)
        }
    }

    @Test
    fun scrimStaysBlackInDark() {
        for (theme in ThemeId.entries) {
            val old = paletteFor(theme, true, AccentId.THEME)
            assertEquals(old.scrim, old.withBlackNight().scrim)
        }
    }

    @Test
    fun contrastNeverDrops() {
        for (theme in ThemeId.entries) for (accent in AccentId.entries) {
            val old = paletteFor(theme, true, accent)
            val n = old.withBlackNight()
            val foregrounds = listOf(
                "text" to old.text, "textMuted" to old.textMuted, "accentSoft" to old.accentSoft, "accent" to old.accent,
                "error" to old.error, "success" to old.success, "warning" to old.warning, "outline" to old.outline,
            )
            val backgrounds = listOf(
                Triple("bg", old.bg, n.bg), Triple("bgDeep", old.bgDeep, n.bgDeep),
                Triple("surface", old.surface, n.surface), Triple("surfaceHigh", old.surfaceHigh, n.surfaceHigh),
            )
            for ((fgName, fg) in foregrounds) for ((bgName, was, now) in backgrounds) {
                val before = ratio(fg, was)
                val after = ratio(fg, now)
                assertTrue("$theme/$accent $fgName on $bgName: $after < $before", after + 1e-9 >= before)
            }
        }
    }

    @Test
    fun bodyTextStillPassesOnBlackAndTheShiftedSurfaces() {
        for (theme in ThemeId.entries) for (accent in AccentId.entries) {
            val n = paletteFor(theme, true, accent).withBlackNight()
            val id = "$theme/$accent"
            assertTrue("$id text on bg", ratio(n.text, n.bg) >= 7.0)
            assertTrue("$id text on surface", ratio(n.text, n.surface) >= 4.5)
            assertTrue("$id text on surfaceHigh", ratio(n.text, n.surfaceHigh) >= 4.5)
            assertTrue("$id muted on bg", ratio(n.textMuted, n.bg) >= 4.5)
            assertTrue("$id muted on surface", ratio(n.textMuted, n.surface) >= 4.5)
        }
    }
}
