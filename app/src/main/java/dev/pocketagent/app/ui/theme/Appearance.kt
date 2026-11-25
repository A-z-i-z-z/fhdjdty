package dev.pocketagent.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeId { NEWROZ, ZAGROS, SEMENDER, NERGIZ, KAWA, ROJ }

/** THEME = the theme's own accent. */
enum class AccentId { THEME, FLAME, SAFFRON, MEADOW, SKY, ROSE, NEWT, SNOW }

/** Every theme has a dark and a light variant. */
enum class DarkMode { SYSTEM, DARK, LIGHT }

enum class AnimLevel { FULL, REDUCED, OFF }

enum class BubbleStyle { PIXEL, SOFT }

@Immutable
data class Appearance(
    val theme: ThemeId = ThemeId.NEWROZ,
    val accent: AccentId = AccentId.THEME,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val textScale: Float = 1f, /* 0.85..1.3 */
    val animations: AnimLevel = AnimLevel.FULL,
    val showMascot: Boolean = true,
    val bubbleStyle: BubbleStyle = BubbleStyle.PIXEL,
    val language: String = "", /* "", "en", "tr", "fr" */
    val pixelFontTitles: Boolean = true,
    /** Touch feedback; no screen for it in Wave 0 (the default is the full vocabulary). */
    val haptics: HapticLevel = HapticLevel.FULL,
)

/**
 * Persists [Appearance] in SharedPreferences "appearance" and exposes it as a StateFlow.
 * Only stores; applying the language (locale switch) is the app root's job.
 */
class AppearanceStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())
    val appearance: StateFlow<Appearance> = state.asStateFlow()

    fun update(change: (Appearance) -> Appearance) {
        val next = sanitize(change(state.value))
        if (next == state.value) return
        state.value = next
        prefs.edit()
            .putString(K_THEME, next.theme.name)
            .putString(K_ACCENT, next.accent.name)
            .putString(K_DARK, next.darkMode.name)
            .putFloat(K_SCALE, next.textScale)
            .putString(K_ANIM, next.animations.name)
            .putBoolean(K_MASCOT, next.showMascot)
            .putString(K_BUBBLE, next.bubbleStyle.name)
            .putString(K_LANG, next.language)
            .putBoolean(K_PIXEL_TITLES, next.pixelFontTitles)
            .putString(K_HAPTICS, next.haptics.name)
            .apply()
    }

    private fun load(): Appearance {
        val d = Appearance()
        return sanitize(
            Appearance(
                theme = parse(prefs.getString(K_THEME, null), d.theme),
                accent = parse(prefs.getString(K_ACCENT, null), d.accent),
                darkMode = parse(prefs.getString(K_DARK, null), d.darkMode),
                textScale = prefs.getFloat(K_SCALE, d.textScale),
                animations = parse(prefs.getString(K_ANIM, null), d.animations),
                showMascot = prefs.getBoolean(K_MASCOT, d.showMascot),
                bubbleStyle = parse(prefs.getString(K_BUBBLE, null), d.bubbleStyle),
                language = prefs.getString(K_LANG, d.language) ?: "",
                pixelFontTitles = prefs.getBoolean(K_PIXEL_TITLES, d.pixelFontTitles),
                haptics = parse(prefs.getString(K_HAPTICS, null), d.haptics),
            )
        )
    }

    private fun sanitize(a: Appearance): Appearance {
        val scale = if (a.textScale.isNaN()) 1f else a.textScale.coerceIn(0.85f, 1.3f)
        return if (scale == a.textScale) a else a.copy(textScale = scale)
    }

    private inline fun <reified E : Enum<E>> parse(name: String?, default: E): E =
        if (name == null) default else enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val PREFS = "appearance"
        private const val K_THEME = "theme"
        private const val K_ACCENT = "accent"
        private const val K_DARK = "darkMode"
        private const val K_SCALE = "textScale"
        private const val K_ANIM = "animations"
        private const val K_MASCOT = "showMascot"
        private const val K_BUBBLE = "bubbleStyle"
        private const val K_LANG = "language"
        private const val K_PIXEL_TITLES = "pixelFontTitles"
        private const val K_HAPTICS = "haptics"

        @Volatile private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) {
                instance ?: AppearanceStore(context.applicationContext).also { instance = it }
            }
    }
}
