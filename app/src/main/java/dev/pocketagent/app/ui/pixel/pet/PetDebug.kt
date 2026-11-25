package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A pinned pose for the phone proof: [anim] (null with [classicOnly]) shown whatever the world says, [frame] only that
 * frame (null = the animation plays), or [classicOnly] = Pêt behaves as if only the seven classic sprites exist.
 */
data class PetPin(val anim: PetAnim?, val frame: Int?, val classicOnly: Boolean)

object PetDebug {
    private const val CLASSIC_WORD = "classic"
    private val pinState = MutableStateFlow<PetPin?>(null)

    /** The current pin; null when none. Memory only. */
    val pinned: StateFlow<PetPin?> get() = pinState

    /** Sets or clears the pin. Does nothing unless the build allows test hooks. */
    fun pin(p: PetPin?) {
        if (!BuildConfig.TEST_HOOKS) return
        pinState.value = p
    }

    /**
     * `<anim>` or `<anim>:<frame>` (the lower-case name of a [PetAnim]; the frame in `0 until frames`), or `classic`.
     * Null for anything else, including `off` (that one is the caller's).
     */
    fun parse(text: String): PetPin? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t == CLASSIC_WORD) return PetPin(null, null, true)
        if (t.any { it in 'A'..'Z' }) return null
        val colon = t.indexOf(':')
        val name = if (colon < 0) t else t.substring(0, colon)
        val anim = PetAnim.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        if (colon < 0) return PetPin(anim, null, false)
        val digits = t.substring(colon + 1)
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val frame = digits.toIntOrNull() ?: return null
        if (frame !in 0 until anim.frames) return null
        return PetPin(anim, frame, false)
    }
}
