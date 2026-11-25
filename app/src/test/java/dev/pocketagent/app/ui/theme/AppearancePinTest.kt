package dev.pocketagent.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The grammar of the `appearance` debug pin. Slice A covers the grammar of all ten items and the application of the
 * four fields that exist today (theme, dark mode, animations, haptics); the slice that adds the seven new fields to
 * Appearance adds `changeAppliesTheNewFields` here.
 */
class AppearancePinTest {
    private fun items(text: String): AppearancePinItems? = AppearancePinGrammar.parse(text)

    @Test
    fun eachItemParses() {
        assertEquals(true, items("black:1")!!.black)
        assertEquals(false, items("black:0")!!.black)
        assertEquals(true, items("classic:1")!!.classic)
        assertEquals(false, items("classic:0")!!.classic)
        assertEquals(true, items("pet:1")!!.pet)
        assertEquals(false, items("pet:0")!!.pet)
        assertEquals(true, items("tilt:1")!!.tilt)
        assertEquals(false, items("tilt:0")!!.tilt)
        assertEquals(true, items("rain:1")!!.rain)
        assertEquals(false, items("rain:0")!!.rain)
        assertEquals(true, items("crackle:1")!!.crackle)
        assertEquals(false, items("crackle:0")!!.crackle)
        assertEquals(HapticLevel.OFF, items("haptics:off")!!.haptics)
        assertEquals(HapticLevel.SUBTLE, items("haptics:subtle")!!.haptics)
        assertEquals(HapticLevel.FULL, items("haptics:full")!!.haptics)
        assertEquals(ThemeId.NEWROZ, items("theme:newroz")!!.theme)
        assertEquals(ThemeId.ZAGROS, items("theme:zagros")!!.theme)
        assertEquals(ThemeId.SEMENDER, items("theme:semender")!!.theme)
        assertEquals(ThemeId.NERGIZ, items("theme:nergiz")!!.theme)
        assertEquals(ThemeId.KAWA, items("theme:kawa")!!.theme)
        assertEquals(ThemeId.ROJ, items("theme:roj")!!.theme)
        assertEquals(DarkMode.SYSTEM, items("dark:system")!!.dark)
        assertEquals(DarkMode.DARK, items("dark:dark")!!.dark)
        assertEquals(DarkMode.LIGHT, items("dark:light")!!.dark)
        assertEquals(AnimLevel.FULL, items("anim:full")!!.anim)
        assertEquals(AnimLevel.REDUCED, items("anim:reduced")!!.anim)
        assertEquals(AnimLevel.OFF, items("anim:off")!!.anim)
    }

    @Test
    fun everyThemeNameIsKnown() {
        // The six theme names of the grammar are exactly the enum, so a new theme cannot be forgotten silently.
        val parsed = listOf("newroz", "zagros", "semender", "nergiz", "kawa", "roj").map { items("theme:$it")!!.theme }
        assertEquals(ThemeId.entries.toSet(), parsed.toSet())
    }

    @Test
    fun severalItemsInOneValue() {
        val i = items("black:1,classic:0,haptics:subtle,theme:nergiz,dark:light,anim:reduced,rain:1,crackle:0,pet:1,tilt:0")!!
        assertEquals(
            AppearancePinItems(
                theme = ThemeId.NERGIZ, dark = DarkMode.LIGHT, anim = AnimLevel.REDUCED, haptics = HapticLevel.SUBTLE,
                black = true, classic = false, pet = true, tilt = false, rain = true, crackle = false,
            ),
            i,
        )
    }

    @Test
    fun spacesAroundItemsAreTolerated() {
        val i = items(" black:1 , classic : 0 ")!!
        assertEquals(true, i.black)
        assertEquals(false, i.classic)
    }

    @Test
    fun badItemRejectsAll() {
        val bad = listOf(
            "", "   ", "off", "black", "black:", ":1", "black:2", "black:true", "black:-1", "black:11",
            "sky:night", "sky:follow", "days:newroz", "days:none", "unknown:1", "BLACK:1", "black:1,", ",black:1",
            "black:1,,classic:0", "black:1;classic:0", "black:1,classic:2", "black:1,sky:night",
            "theme:Newroz", "theme:purple", "theme:", "dark:true", "dark:DARK", "dark:auto", "anim:fast", "anim:on",
            "haptics:medium", "haptics:on", "haptics:1", "rain:yes", "crackle:on", "tilt:", "pet:x",
            "dark:dark:light", "black:1:2",
        )
        for (text in bad) assertNull("'$text' must be rejected", items(text))
        for (text in bad) assertNull("'$text' must be rejected by parse", AppearancePin.parse(text))
    }

    @Test
    fun oneBadItemRejectsTheGoodOnesToo() {
        assertNull(items("black:1,classic:2"))
        assertNull(items("theme:kawa,dark:purple"))
    }

    @Test
    fun lastWins() {
        assertEquals(false, items("black:1,black:0")!!.black)
        assertEquals(true, items("black:0,classic:0,black:1")!!.black)
        assertEquals(ThemeId.ROJ, items("theme:newroz,dark:dark,theme:roj")!!.theme)
        assertEquals(DarkMode.DARK, items("theme:newroz,dark:dark,theme:roj")!!.dark)
    }

    @Test
    fun anItemNotNamedStaysNull() {
        val i = items("black:1")!!
        assertNull(i.classic)
        assertNull(i.theme)
        assertNull(i.dark)
        assertNull(i.anim)
        assertNull(i.haptics)
        assertNull(i.pet)
        assertNull(i.tilt)
        assertNull(i.rain)
        assertNull(i.crackle)
    }

    @Test
    fun parseGivesAPinOrNull() {
        assertNotNull(AppearancePin.parse("theme:zagros"))
        assertNull(AppearancePin.parse("theme:atlantis"))
    }

    @Test
    fun changeAppliesTheExistingFieldsAndLeavesTheRest() {
        val stored = Appearance(
            theme = ThemeId.KAWA, darkMode = DarkMode.SYSTEM, animations = AnimLevel.FULL, haptics = HapticLevel.FULL,
            accent = AccentId.ROSE, textScale = 1.2f, showMascot = false, language = "tr", pixelFontTitles = false,
        )
        val pin = AppearancePin.parse("theme:nergiz,dark:light,anim:off,haptics:off")!!
        val shown = pin.change(stored)
        assertEquals(stored.copy(theme = ThemeId.NERGIZ, darkMode = DarkMode.LIGHT, animations = AnimLevel.OFF, haptics = HapticLevel.OFF), shown)
        // The stored value itself is a value and is never touched.
        assertEquals(ThemeId.KAWA, stored.theme)
    }

    @Test
    fun aPinOverADefaultAppearance() {
        val shown = AppearancePin.parse("theme:roj")!!.change(Appearance())
        assertEquals(Appearance(theme = ThemeId.ROJ), shown)
    }
}
