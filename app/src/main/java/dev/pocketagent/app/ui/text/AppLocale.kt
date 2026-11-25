package dev.pocketagent.app.ui.text

import java.util.Locale

/**
 * The locale the screens speak: the language chosen in the app ([language], a tag such as "tr" or "en-XZ"; empty
 * means the system language), else [configurationLocale], the first locale of the configuration in force. Pure.
 *
 * The chosen language wins over the configuration on purpose. A window of its own (a dialog, a menu, a popup) gets
 * the PHONE's configuration from the system, but the app-level language reaches it through a composition local
 * that no window replaces. A tag that names no language is ignored.
 */
fun resolveAppLocale(language: String, configurationLocale: Locale): Locale {
    if (language.isBlank()) return configurationLocale
    val chosen = try {
        Locale.forLanguageTag(language.trim())
    } catch (_: Exception) {
        return configurationLocale
    }
    return if (chosen.language.isEmpty()) configurationLocale else chosen
}
