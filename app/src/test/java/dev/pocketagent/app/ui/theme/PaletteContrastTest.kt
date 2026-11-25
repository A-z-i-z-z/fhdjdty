package dev.pocketagent.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast is tested, not hoped for: every theme, in its dark and its light variant, with every accent.
 * A pair that fails and is not in [KNOWN_FAILURES] fails the test; so does an entry of [KNOWN_FAILURES] that now
 * passes. Colours do not change in Wave 0, so the list holds what is already true today.
 *
 * The set was left EMPTY by the agent that wrote the test (it cannot run it). The integration step runs the test
 * once and pastes the printed ids here: ids look like NEWROZ/dark/FLAME:outline-on-bg.
 */
class PaletteContrastTest {
    companion object {
        val KNOWN_FAILURES: Set<String> = emptySet()
    }

    private class Check(val name: String, val fg: Color, val bg: Color, val min: Double)

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

    /** Runs the checks of one group over every theme, variant and accent and compares with the ratchet list. */
    private fun run(names: Set<String>, checks: (PocketColors) -> List<Check>) {
        val failures = sortedSetOf<String>()
        for (theme in ThemeId.entries) {
            for (dark in listOf(true, false)) {
                for (accent in AccentId.entries) {
                    val colors = paletteFor(theme, dark, accent)
                    val combo = "${theme.name}/${if (dark) "dark" else "light"}/${accent.name}"
                    for (c in checks(colors)) {
                        if (ratio(c.fg, c.bg) < c.min) failures.add("$combo:${c.name}")
                    }
                }
            }
        }
        val known = KNOWN_FAILURES.filter { it.substringAfter(':') in names }.toSet()
        val unexpected = failures - known
        val stale = known - failures
        assertTrue(
            "New contrast failures (fix the colour, or paste into KNOWN_FAILURES):\n" + unexpected.joinToString("\n"),
            unexpected.isEmpty(),
        )
        assertTrue(
            "These KNOWN_FAILURES now pass, remove them:\n" + stale.joinToString("\n"),
            stale.isEmpty(),
        )
    }

    @Test
    fun bodyTextOnSurfaces() = run(setOf("text-on-bg", "text-on-bgDeep", "text-on-surface", "text-on-surfaceHigh")) { c ->
        listOf(
            Check("text-on-bg", c.text, c.bg, 7.0),
            Check("text-on-bgDeep", c.text, c.bgDeep, 4.5),
            Check("text-on-surface", c.text, c.surface, 4.5),
            Check("text-on-surfaceHigh", c.text, c.surfaceHigh, 4.5),
        )
    }

    @Test
    fun mutedTextOnSurfaces() = run(setOf("muted-on-bg", "muted-on-bgDeep", "muted-on-surface", "muted-on-surfaceHigh")) { c ->
        listOf(
            Check("muted-on-bg", c.textMuted, c.bg, 4.5),
            Check("muted-on-bgDeep", c.textMuted, c.bgDeep, 4.5),
            Check("muted-on-surface", c.textMuted, c.surface, 4.5),
            Check("muted-on-surfaceHigh", c.textMuted, c.surfaceHigh, 4.5),
        )
    }

    @Test
    fun accentSoftOnBgAndSurface() = run(setOf("accentSoft-on-bg", "accentSoft-on-surface")) { c ->
        listOf(
            Check("accentSoft-on-bg", c.accentSoft, c.bg, 4.5),
            Check("accentSoft-on-surface", c.accentSoft, c.surface, 4.5),
        )
    }

    @Test
    fun onAccentOnAccent() = run(setOf("onAccent-on-accent")) { c ->
        listOf(Check("onAccent-on-accent", c.onAccent, c.accent, 4.5))
    }

    @Test
    fun bubblesOnTheirFaces() = run(setOf("user-bubble", "bot-bubble", "tool-bubble")) { c ->
        listOf(
            Check("user-bubble", c.onUserBubble, c.userBubble, 4.5),
            Check("bot-bubble", c.onBotBubble, c.botBubble, 4.5),
            Check("tool-bubble", c.onToolBubble, c.toolBubble, 4.5),
        )
    }

    @Test
    fun textOnCodeBg() = run(setOf("text-on-codeBg")) { c ->
        listOf(Check("text-on-codeBg", c.text, c.codeBg, 4.5))
    }

    @Test
    fun signalColoursOnBgAndSurface() = run(
        setOf(
            "error-on-bg", "error-on-surface", "success-on-bg", "success-on-surface",
            "warning-on-bg", "warning-on-surface",
        ),
    ) { c ->
        listOf(
            Check("error-on-bg", c.error, c.bg, 4.5),
            Check("error-on-surface", c.error, c.surface, 4.5),
            Check("success-on-bg", c.success, c.bg, 4.5),
            Check("success-on-surface", c.success, c.surface, 4.5),
            Check("warning-on-bg", c.warning, c.bg, 4.5),
            Check("warning-on-surface", c.warning, c.surface, 4.5),
        )
    }

    @Test
    fun outlinesAndAccentAtThree() = run(setOf("outline-on-bg", "outline-on-surface", "accent-on-bg")) { c ->
        listOf(
            Check("outline-on-bg", c.outline, c.bg, 3.0),
            Check("outline-on-surface", c.outline, c.surface, 3.0),
            Check("accent-on-bg", c.accent, c.bg, 3.0),
        )
    }
}
