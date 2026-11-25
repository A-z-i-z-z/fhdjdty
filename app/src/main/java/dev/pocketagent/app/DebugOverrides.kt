package dev.pocketagent.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Language and layout direction forced by the debug link, so a screenshot in another language or in a right-to-left
 * layout can be taken without touching the saved settings. Kept in memory only, never persisted, and never set in a
 * build without test hooks. Only composition code reads it (the language scope of the shell): services and other code
 * without a screen keep the saved language on purpose.
 */
object DebugOverrides {
    private val languageState = MutableStateFlow<String?>(null)
    private val rtlState = MutableStateFlow<Boolean?>(null)

    /** A BCP-47 tag, or null for no override. */
    val language: StateFlow<String?> = languageState.asStateFlow()

    /** True forces right-to-left, false forces left-to-right, null follows the language. */
    val forceRtl: StateFlow<Boolean?> = rtlState.asStateFlow()

    fun setLanguage(tag: String?) {
        if (!BuildConfig.TEST_HOOKS) return
        languageState.value = tag?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setForceRtl(value: Boolean?) {
        if (!BuildConfig.TEST_HOOKS) return
        rtlState.value = value
    }

    fun clear() {
        languageState.value = null
        rtlState.value = null
    }
}
