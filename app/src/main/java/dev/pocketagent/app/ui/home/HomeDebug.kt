package dev.pocketagent.app.ui.home

import dev.pocketagent.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The woodpile pin of the debug link (the `wood` extra). Memory only: nothing is stored, and the debug link's reset clears it.
 * Inert in a build without test hooks. While a level is pinned it wins over the level worked out from the usage.
 */
object HomeDebug {
    private val woodpileState = MutableStateFlow<Int?>(null)

    /** The pinned woodpile level (0..5), or null while nothing is pinned. */
    val woodpile: StateFlow<Int?> = woodpileState.asStateFlow()

    /** Pins a level (clamped to 0..5), or clears the pin with null. Does nothing unless [enabled] (test hooks). */
    fun pinWoodpile(level: Int?, enabled: Boolean = BuildConfig.TEST_HOOKS) {
        if (!enabled) return
        woodpileState.value = level?.coerceIn(0, Woodpile.FULL)
    }
}
