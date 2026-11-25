package dev.pocketagent.app.ui.theme

/**
 * The debug link's `appearance` pin (Wave 1a, APPEAR; `TEST_HOOKS` builds only, memory only). [change] maps the stored
 * [Appearance] to the one the screens should see.
 *
 * Grammar: comma separated items, each `key:value`, lower case. The whole text is rejected (null) when one item is
 * bad; a repeated key: the last one wins. `off` is not an item: the caller turns it into "no pin".
 *
 *   black:0|1  classic:0|1  pet:0|1  tilt:0|1  rain:0|1  crackle:0|1
 *   haptics:off|subtle|full
 *   theme:newroz|zagros|semender|nergiz|kawa|roj
 *   dark:system|dark|light
 *   anim:full|reduced|off
 *
 * `sky` and `days` are debug extras of their own, not items here.
 *
 * Slice A of Wave 1a holds the grammar and the application of the four fields that exist today (theme, dark mode,
 * animations, haptics). The slice that adds the seven new fields to [Appearance] adds the six marked lines in
 * [AppearancePinItems.applyTo] and nothing else in this file.
 */
class AppearancePin(val change: (Appearance) -> Appearance) {
    companion object {
        /** Null when any item is bad. */
        fun parse(text: String): AppearancePin? {
            val items = AppearancePinGrammar.parse(text) ?: return null
            return AppearancePin { stored -> items.applyTo(stored) }
        }
    }
}

/** What a pin says; a null field leaves the stored value alone. */
internal data class AppearancePinItems(
    val theme: ThemeId? = null,
    val dark: DarkMode? = null,
    val anim: AnimLevel? = null,
    val haptics: HapticLevel? = null,
    val black: Boolean? = null,
    val classic: Boolean? = null,
    val pet: Boolean? = null,
    val tilt: Boolean? = null,
    val rain: Boolean? = null,
    val crackle: Boolean? = null,
) {
    fun applyTo(a: Appearance): Appearance = a.copy(
        theme = theme ?: a.theme,
        darkMode = dark ?: a.darkMode,
        animations = anim ?: a.animations,
        haptics = haptics ?: a.haptics,
        // Later slice, once Appearance has the fields (add these six lines to the copy call above):
        //   blackNight = black ?: a.blackNight,
        //   classicFire = classic ?: a.classicFire,
        //   petInChats = pet ?: a.petInChats,
        //   tiltFire = tilt ?: a.tiltFire,
        //   rainSound = rain ?: a.rainSound,
        //   crackle = crackle ?: a.crackle,
    )
}

/** The grammar only: text to items. No case change (the lint ratchet): every value is matched in a `when` table. */
internal object AppearancePinGrammar {
    fun parse(text: String): AppearancePinItems? {
        if (text.isBlank()) return null
        var items = AppearancePinItems()
        for (raw in text.split(',')) {
            val item = raw.trim()
            val colon = item.indexOf(':')
            if (colon <= 0) return null
            val key = item.substring(0, colon).trim()
            val value = item.substring(colon + 1).trim()
            if (value.isEmpty()) return null
            items = when (key) {
                "black" -> items.copy(black = flag(value) ?: return null)
                "classic" -> items.copy(classic = flag(value) ?: return null)
                "pet" -> items.copy(pet = flag(value) ?: return null)
                "tilt" -> items.copy(tilt = flag(value) ?: return null)
                "rain" -> items.copy(rain = flag(value) ?: return null)
                "crackle" -> items.copy(crackle = flag(value) ?: return null)
                "haptics" -> items.copy(haptics = haptics(value) ?: return null)
                "theme" -> items.copy(theme = theme(value) ?: return null)
                "dark" -> items.copy(dark = dark(value) ?: return null)
                "anim" -> items.copy(anim = anim(value) ?: return null)
                else -> return null
            }
        }
        return items
    }

    private fun flag(v: String): Boolean? = when (v) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun haptics(v: String): HapticLevel? = when (v) {
        "off" -> HapticLevel.OFF
        "subtle" -> HapticLevel.SUBTLE
        "full" -> HapticLevel.FULL
        else -> null
    }

    private fun theme(v: String): ThemeId? = when (v) {
        "newroz" -> ThemeId.NEWROZ
        "zagros" -> ThemeId.ZAGROS
        "semender" -> ThemeId.SEMENDER
        "nergiz" -> ThemeId.NERGIZ
        "kawa" -> ThemeId.KAWA
        "roj" -> ThemeId.ROJ
        else -> null
    }

    private fun dark(v: String): DarkMode? = when (v) {
        "system" -> DarkMode.SYSTEM
        "dark" -> DarkMode.DARK
        "light" -> DarkMode.LIGHT
        else -> null
    }

    private fun anim(v: String): AnimLevel? = when (v) {
        "full" -> AnimLevel.FULL
        "reduced" -> AnimLevel.REDUCED
        "off" -> AnimLevel.OFF
        else -> null
    }
}
