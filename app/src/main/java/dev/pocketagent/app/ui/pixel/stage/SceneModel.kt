package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.pixel.Sprite

/** Where the renderer finds its sprites by name. The sprite bank is one; tests bring their own. */
fun interface SpriteSource {
    operator fun get(name: String): Sprite
}

/**
 * Every colour the scene needs, as opaque ARGB ints (the Compose side turns theme colours into these).
 * [farRamp] holds the four tint colours of the far ridge, darkest first. [glow] is the opaque colour of the
 * fire glow. Equality is by content, because [SceneState] sits in a stamp that is compared every frame.
 */
class ScenePalette(
    val skyTop: Int,
    val skyBottom: Int,
    val farRamp: IntArray,
    val glow: Int,
    val fireLow: Int,
    val fireMid: Int,
    val fireHigh: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScenePalette) return false
        return skyTop == other.skyTop && skyBottom == other.skyBottom && farRamp.contentEquals(other.farRamp) &&
            glow == other.glow && fireLow == other.fireLow && fireMid == other.fireMid && fireHigh == other.fireHigh
    }

    override fun hashCode(): Int {
        var h = skyTop
        h = h * 31 + skyBottom
        h = h * 31 + farRamp.contentHashCode()
        h = h * 31 + glow
        h = h * 31 + fireLow
        h = h * 31 + fireMid
        h = h * 31 + fireHigh
        return h
    }
}

/** What the scene shows at one moment (the clock is a separate argument of the renderer). */
data class SceneState(
    val hearth: HearthState,
    val palette: ScenePalette,
    val progress: Float? = null,
    val mood: MascotMood? = null,
    val stokeBoost: Float = 0f,
    val celebration: Boolean = false,
)

/** PLAIN draws no moving extras: no shooting star, distant fires, smoke, embers, fireflies, glow flicker, hop. */
enum class SceneQuality(val particles: Boolean) { FULL(true), PLAIN(false) }
