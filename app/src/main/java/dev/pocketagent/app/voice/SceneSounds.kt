package dev.pocketagent.app.voice

import dev.pocketagent.app.ui.pixel.HearthState

// Wave 1a, APPEAR: the pure part of the scene sounds (slice A). Nothing here touches Android.
//
// A later slice (the one that edits SoundEngine.kt) adds to THIS file, and must not declare these symbols again in
// SoundEngine.kt:
//   - `fun hearthCue(prev: HearthState?, next: HearthState): SoundCue?`, which is
//       when (HearthCues.turnOf(prev, next)) { WAKE -> SoundCue.WAKE; SLEEP -> SoundCue.SLEEP; null -> null }
//     once SoundCue has its two appended entries WAKE and SLEEP (they do not exist yet, so it cannot be written now);
//   - the composable `SceneSounds(hearth, rainLevel)`.
// Keep everything below as it is.

/** The two looping sounds of the home scene. Played by SoundEngine.setLoop, which a later slice adds. */
enum class SoundLoop { RAIN, CRACKLE }

/** Names of the sound files in res/raw (looked up with getIdentifier, so a missing file is silent, never a build error). */
object SoundFiles {
    /** "sfx_hearth_wake", "sfx_studio_sleep"; null when the pack is OFF. */
    fun cueName(pack: SoundPack, cue: SoundCue): String? = cueFile(pack, cue.name)

    /** The same rule from a cue's enum name (for cues a test or a later slice names before the enum has them). */
    internal fun cueFile(pack: SoundPack, cueEnumName: String): String? = when (pack) {
        SoundPack.OFF -> null
        SoundPack.HEARTH, SoundPack.STUDIO -> "sfx_" + pack.name.lowercase() + "_" + cueEnumName.lowercase()
    }

    fun loopName(loop: SoundLoop): String = when (loop) {
        SoundLoop.RAIN -> "sfx_loop_rain"
        SoundLoop.CRACKLE -> "sfx_loop_crackle"
    }
}

/** The arithmetic of the loops: fades and the level each scene state asks for. */
object LoopMath {
    const val FADE_MS = 400L
    const val RAIN_BASE = 0.55f
    const val CRACKLE_BASE = 0.25f

    private const val RAIN_LIGHT = 0.4f
    private const val RAIN_HEAVY = 0.8f
    private const val CRACKLE_LEVEL = 0.25f

    /** Linear from [from] to [to]; [elapsedMs] is clamped to 0..[durationMs]; a zero or negative duration gives [to]. */
    fun fade(from: Float, to: Float, elapsedMs: Long, durationMs: Long = FADE_MS): Float {
        if (durationMs <= 0L) return to
        val f = elapsedMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
        return from + (to - from) * f
    }

    /** 0 and a disabled switch give 0f; 1 gives 0.4f (light rain); 2 and above 0.8f (heavy rain). */
    fun rainLevel(rainLevel: Int, enabled: Boolean): Float = when {
        !enabled || rainLevel <= 0 -> 0f
        rainLevel == 1 -> RAIN_LIGHT
        else -> RAIN_HEAVY
    }

    /** 0.25f while the fire blazes and the switch is on, else 0f. */
    fun crackleLevel(blazing: Boolean, enabled: Boolean): Float = if (blazing && enabled) CRACKLE_LEVEL else 0f
}

/** Which scene cue a change of the hearth plays: the decision, before it is turned into a [SoundCue]. */
object HearthCues {
    enum class Turn { WAKE, SLEEP }

    /**
     * (COLD or KINDLING) to BLAZING is a wake, BLAZING to COLD a sleep. No previous state (the first composition),
     * the same state, and every other pair play nothing.
     */
    fun turnOf(prev: HearthState?, next: HearthState): Turn? {
        if (prev == null) return null
        return when {
            (prev == HearthState.COLD || prev == HearthState.KINDLING) && next == HearthState.BLAZING -> Turn.WAKE
            prev == HearthState.BLAZING && next == HearthState.COLD -> Turn.SLEEP
            else -> null
        }
    }
}
