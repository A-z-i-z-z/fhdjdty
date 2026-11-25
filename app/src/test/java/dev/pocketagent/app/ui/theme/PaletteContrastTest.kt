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
 * The set was left empty by the agent that wrote the test (it could not run it). INTEGRATE ran the test once and pasted
 * the printed ids here (215: outline-on-bg, outline-on-surface and accent-on-bg at 3:1): ids look like NEWROZ/dark/FLAME:outline-on-bg.
 */
class PaletteContrastTest {
    companion object {
        val KNOWN_FAILURES: Set<String> = setOf(
            "KAWA/dark/FLAME:outline-on-bg",
            "KAWA/dark/FLAME:outline-on-surface",
            "KAWA/dark/MEADOW:outline-on-bg",
            "KAWA/dark/MEADOW:outline-on-surface",
            "KAWA/dark/NEWT:outline-on-bg",
            "KAWA/dark/NEWT:outline-on-surface",
            "KAWA/dark/ROSE:outline-on-bg",
            "KAWA/dark/ROSE:outline-on-surface",
            "KAWA/dark/SAFFRON:outline-on-bg",
            "KAWA/dark/SAFFRON:outline-on-surface",
            "KAWA/dark/SKY:outline-on-bg",
            "KAWA/dark/SKY:outline-on-surface",
            "KAWA/dark/SNOW:outline-on-bg",
            "KAWA/dark/SNOW:outline-on-surface",
            "KAWA/dark/THEME:outline-on-bg",
            "KAWA/dark/THEME:outline-on-surface",
            "KAWA/light/FLAME:accent-on-bg",
            "KAWA/light/FLAME:outline-on-bg",
            "KAWA/light/MEADOW:accent-on-bg",
            "KAWA/light/MEADOW:outline-on-bg",
            "KAWA/light/NEWT:accent-on-bg",
            "KAWA/light/NEWT:outline-on-bg",
            "KAWA/light/ROSE:accent-on-bg",
            "KAWA/light/ROSE:outline-on-bg",
            "KAWA/light/SAFFRON:accent-on-bg",
            "KAWA/light/SAFFRON:outline-on-bg",
            "KAWA/light/SKY:accent-on-bg",
            "KAWA/light/SKY:outline-on-bg",
            "KAWA/light/SNOW:accent-on-bg",
            "KAWA/light/SNOW:outline-on-bg",
            "KAWA/light/THEME:accent-on-bg",
            "KAWA/light/THEME:outline-on-bg",
            "NERGIZ/dark/FLAME:outline-on-surface",
            "NERGIZ/dark/MEADOW:outline-on-surface",
            "NERGIZ/dark/NEWT:outline-on-surface",
            "NERGIZ/dark/ROSE:outline-on-surface",
            "NERGIZ/dark/SAFFRON:outline-on-surface",
            "NERGIZ/dark/SKY:outline-on-surface",
            "NERGIZ/dark/SNOW:outline-on-surface",
            "NERGIZ/dark/THEME:outline-on-surface",
            "NERGIZ/light/FLAME:accent-on-bg",
            "NERGIZ/light/FLAME:outline-on-bg",
            "NERGIZ/light/FLAME:outline-on-surface",
            "NERGIZ/light/MEADOW:accent-on-bg",
            "NERGIZ/light/MEADOW:outline-on-bg",
            "NERGIZ/light/MEADOW:outline-on-surface",
            "NERGIZ/light/NEWT:accent-on-bg",
            "NERGIZ/light/NEWT:outline-on-bg",
            "NERGIZ/light/NEWT:outline-on-surface",
            "NERGIZ/light/ROSE:outline-on-bg",
            "NERGIZ/light/ROSE:outline-on-surface",
            "NERGIZ/light/SAFFRON:accent-on-bg",
            "NERGIZ/light/SAFFRON:outline-on-bg",
            "NERGIZ/light/SAFFRON:outline-on-surface",
            "NERGIZ/light/SKY:outline-on-bg",
            "NERGIZ/light/SKY:outline-on-surface",
            "NERGIZ/light/SNOW:accent-on-bg",
            "NERGIZ/light/SNOW:outline-on-bg",
            "NERGIZ/light/SNOW:outline-on-surface",
            "NERGIZ/light/THEME:accent-on-bg",
            "NERGIZ/light/THEME:outline-on-bg",
            "NERGIZ/light/THEME:outline-on-surface",
            "NEWROZ/dark/FLAME:outline-on-bg",
            "NEWROZ/dark/FLAME:outline-on-surface",
            "NEWROZ/dark/MEADOW:outline-on-bg",
            "NEWROZ/dark/MEADOW:outline-on-surface",
            "NEWROZ/dark/NEWT:outline-on-bg",
            "NEWROZ/dark/NEWT:outline-on-surface",
            "NEWROZ/dark/ROSE:outline-on-bg",
            "NEWROZ/dark/ROSE:outline-on-surface",
            "NEWROZ/dark/SAFFRON:outline-on-bg",
            "NEWROZ/dark/SAFFRON:outline-on-surface",
            "NEWROZ/dark/SKY:outline-on-bg",
            "NEWROZ/dark/SKY:outline-on-surface",
            "NEWROZ/dark/SNOW:outline-on-bg",
            "NEWROZ/dark/SNOW:outline-on-surface",
            "NEWROZ/dark/THEME:outline-on-bg",
            "NEWROZ/dark/THEME:outline-on-surface",
            "NEWROZ/light/FLAME:accent-on-bg",
            "NEWROZ/light/FLAME:outline-on-bg",
            "NEWROZ/light/FLAME:outline-on-surface",
            "NEWROZ/light/MEADOW:accent-on-bg",
            "NEWROZ/light/MEADOW:outline-on-bg",
            "NEWROZ/light/MEADOW:outline-on-surface",
            "NEWROZ/light/NEWT:accent-on-bg",
            "NEWROZ/light/NEWT:outline-on-bg",
            "NEWROZ/light/NEWT:outline-on-surface",
            "NEWROZ/light/ROSE:outline-on-bg",
            "NEWROZ/light/ROSE:outline-on-surface",
            "NEWROZ/light/SAFFRON:accent-on-bg",
            "NEWROZ/light/SAFFRON:outline-on-bg",
            "NEWROZ/light/SAFFRON:outline-on-surface",
            "NEWROZ/light/SKY:outline-on-bg",
            "NEWROZ/light/SKY:outline-on-surface",
            "NEWROZ/light/SNOW:accent-on-bg",
            "NEWROZ/light/SNOW:outline-on-bg",
            "NEWROZ/light/SNOW:outline-on-surface",
            "NEWROZ/light/THEME:accent-on-bg",
            "NEWROZ/light/THEME:outline-on-bg",
            "NEWROZ/light/THEME:outline-on-surface",
            "ROJ/dark/FLAME:outline-on-bg",
            "ROJ/dark/FLAME:outline-on-surface",
            "ROJ/dark/MEADOW:outline-on-bg",
            "ROJ/dark/MEADOW:outline-on-surface",
            "ROJ/dark/NEWT:outline-on-bg",
            "ROJ/dark/NEWT:outline-on-surface",
            "ROJ/dark/ROSE:outline-on-bg",
            "ROJ/dark/ROSE:outline-on-surface",
            "ROJ/dark/SAFFRON:outline-on-bg",
            "ROJ/dark/SAFFRON:outline-on-surface",
            "ROJ/dark/SKY:outline-on-bg",
            "ROJ/dark/SKY:outline-on-surface",
            "ROJ/dark/SNOW:outline-on-bg",
            "ROJ/dark/SNOW:outline-on-surface",
            "ROJ/dark/THEME:outline-on-bg",
            "ROJ/dark/THEME:outline-on-surface",
            "ROJ/light/FLAME:accent-on-bg",
            "ROJ/light/FLAME:outline-on-bg",
            "ROJ/light/FLAME:outline-on-surface",
            "ROJ/light/MEADOW:accent-on-bg",
            "ROJ/light/MEADOW:outline-on-bg",
            "ROJ/light/MEADOW:outline-on-surface",
            "ROJ/light/NEWT:accent-on-bg",
            "ROJ/light/NEWT:outline-on-bg",
            "ROJ/light/NEWT:outline-on-surface",
            "ROJ/light/ROSE:outline-on-bg",
            "ROJ/light/ROSE:outline-on-surface",
            "ROJ/light/SAFFRON:accent-on-bg",
            "ROJ/light/SAFFRON:outline-on-bg",
            "ROJ/light/SAFFRON:outline-on-surface",
            "ROJ/light/SKY:accent-on-bg",
            "ROJ/light/SKY:outline-on-bg",
            "ROJ/light/SKY:outline-on-surface",
            "ROJ/light/SNOW:accent-on-bg",
            "ROJ/light/SNOW:outline-on-bg",
            "ROJ/light/SNOW:outline-on-surface",
            "ROJ/light/THEME:outline-on-bg",
            "ROJ/light/THEME:outline-on-surface",
            "SEMENDER/dark/FLAME:outline-on-bg",
            "SEMENDER/dark/FLAME:outline-on-surface",
            "SEMENDER/dark/MEADOW:outline-on-bg",
            "SEMENDER/dark/MEADOW:outline-on-surface",
            "SEMENDER/dark/NEWT:outline-on-bg",
            "SEMENDER/dark/NEWT:outline-on-surface",
            "SEMENDER/dark/ROSE:outline-on-bg",
            "SEMENDER/dark/ROSE:outline-on-surface",
            "SEMENDER/dark/SAFFRON:outline-on-bg",
            "SEMENDER/dark/SAFFRON:outline-on-surface",
            "SEMENDER/dark/SKY:outline-on-bg",
            "SEMENDER/dark/SKY:outline-on-surface",
            "SEMENDER/dark/SNOW:outline-on-bg",
            "SEMENDER/dark/SNOW:outline-on-surface",
            "SEMENDER/dark/THEME:outline-on-bg",
            "SEMENDER/dark/THEME:outline-on-surface",
            "SEMENDER/light/FLAME:accent-on-bg",
            "SEMENDER/light/FLAME:outline-on-bg",
            "SEMENDER/light/FLAME:outline-on-surface",
            "SEMENDER/light/MEADOW:accent-on-bg",
            "SEMENDER/light/MEADOW:outline-on-bg",
            "SEMENDER/light/MEADOW:outline-on-surface",
            "SEMENDER/light/NEWT:accent-on-bg",
            "SEMENDER/light/NEWT:outline-on-bg",
            "SEMENDER/light/NEWT:outline-on-surface",
            "SEMENDER/light/ROSE:outline-on-bg",
            "SEMENDER/light/ROSE:outline-on-surface",
            "SEMENDER/light/SAFFRON:accent-on-bg",
            "SEMENDER/light/SAFFRON:outline-on-bg",
            "SEMENDER/light/SAFFRON:outline-on-surface",
            "SEMENDER/light/SKY:accent-on-bg",
            "SEMENDER/light/SKY:outline-on-bg",
            "SEMENDER/light/SKY:outline-on-surface",
            "SEMENDER/light/SNOW:accent-on-bg",
            "SEMENDER/light/SNOW:outline-on-bg",
            "SEMENDER/light/SNOW:outline-on-surface",
            "SEMENDER/light/THEME:accent-on-bg",
            "SEMENDER/light/THEME:outline-on-bg",
            "SEMENDER/light/THEME:outline-on-surface",
            "ZAGROS/dark/FLAME:outline-on-bg",
            "ZAGROS/dark/FLAME:outline-on-surface",
            "ZAGROS/dark/MEADOW:outline-on-bg",
            "ZAGROS/dark/MEADOW:outline-on-surface",
            "ZAGROS/dark/NEWT:outline-on-bg",
            "ZAGROS/dark/NEWT:outline-on-surface",
            "ZAGROS/dark/ROSE:outline-on-bg",
            "ZAGROS/dark/ROSE:outline-on-surface",
            "ZAGROS/dark/SAFFRON:outline-on-bg",
            "ZAGROS/dark/SAFFRON:outline-on-surface",
            "ZAGROS/dark/SKY:outline-on-bg",
            "ZAGROS/dark/SKY:outline-on-surface",
            "ZAGROS/dark/SNOW:outline-on-bg",
            "ZAGROS/dark/SNOW:outline-on-surface",
            "ZAGROS/dark/THEME:outline-on-bg",
            "ZAGROS/dark/THEME:outline-on-surface",
            "ZAGROS/light/FLAME:accent-on-bg",
            "ZAGROS/light/FLAME:outline-on-bg",
            "ZAGROS/light/FLAME:outline-on-surface",
            "ZAGROS/light/MEADOW:accent-on-bg",
            "ZAGROS/light/MEADOW:outline-on-bg",
            "ZAGROS/light/MEADOW:outline-on-surface",
            "ZAGROS/light/NEWT:accent-on-bg",
            "ZAGROS/light/NEWT:outline-on-bg",
            "ZAGROS/light/NEWT:outline-on-surface",
            "ZAGROS/light/ROSE:outline-on-bg",
            "ZAGROS/light/ROSE:outline-on-surface",
            "ZAGROS/light/SAFFRON:accent-on-bg",
            "ZAGROS/light/SAFFRON:outline-on-bg",
            "ZAGROS/light/SAFFRON:outline-on-surface",
            "ZAGROS/light/SKY:outline-on-bg",
            "ZAGROS/light/SKY:outline-on-surface",
            "ZAGROS/light/SNOW:accent-on-bg",
            "ZAGROS/light/SNOW:outline-on-bg",
            "ZAGROS/light/SNOW:outline-on-surface",
            "ZAGROS/light/THEME:accent-on-bg",
            "ZAGROS/light/THEME:outline-on-bg",
            "ZAGROS/light/THEME:outline-on-surface",
        )
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
