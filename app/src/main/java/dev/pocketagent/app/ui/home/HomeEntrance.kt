package dev.pocketagent.app.ui.home

import dev.pocketagent.app.ui.theme.AnimLevel

/**
 * Home's entrance (the scene reveal and the staggered cards) plays once per process. The decision is pure; the flag is the only
 * state and lives in memory, so every cold start plays it again.
 */
object HomeEntrance {
    /** Process-wide, false at every cold start. */
    @Volatile
    var played: Boolean = false

    /** The scene reveals over this long. */
    const val REVEAL_MS = 900

    /** Each card starts this long after the one before. */
    const val STAGGER_MS = 36

    /** Only the first cards are staggered; 6 * 36 plus the fast duration (150) stays within 400 ms. */
    const val MAX_STAGGERED = 6

    /**
     * True when the entrance should play: it has not played yet, animations are not off, and the clock is not pinned by a
     * test build (a robot picture of a pinned moment must not catch the scene half revealed). REDUCED plays.
     */
    fun shouldPlay(played: Boolean, level: AnimLevel, clockPinned: Boolean, testHooks: Boolean): Boolean =
        !played && level != AnimLevel.OFF && !(testHooks && clockPinned)

    fun markPlayed() {
        played = true
    }

    /** For the debug link's reset: the next Home composition plays the entrance again. */
    fun reset() {
        played = false
    }
}
