package dev.pocketagent.app.ui.pixel

import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.pixel.stage.SceneQuality
import dev.pocketagent.app.ui.pixel.stage.ScenePalette
import dev.pocketagent.app.ui.pixel.stage.SceneRenderer
import dev.pocketagent.app.ui.pixel.stage.SceneState
import dev.pocketagent.app.ui.pixel.stage.StageGrid
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.PocketColors

enum class HearthState { COLD, KINDLING, BLAZING, SMOKE }

// ---------------------------------------------------------------------------------------------------
// The scene lives on a logical pixel grid, drawn by the pure code in the stage package (SceneRenderer)
// into a PixelStage and put on the screen by StageSurface at a whole-number scale. The art layers
// (scene.pxl) are 176 logical px wide; the grid is whatever fits the surface at that scale (see
// StageGrid.forScene). Everything is bottom-anchored, the sky grows upwards.
// Layer order: sky bands, stars, moon, far ridge, near ridge + trees, ground, fire glow, newt, fire,
// smoke, embers, foreground grass. tools/pixel-preview.ps1 shows the sprites.
// ---------------------------------------------------------------------------------------------------

// far-ridge tint ramp (1 darkest .. 4 lightest): moonlit violet, mixed a little with the theme's bg
private val FAR_RAMP = longArrayOf(0xFF1F1838, 0xFF2D2552, 0xFF443D7A, 0xFF6B67AA)

/** The colour of the top of the night sky (always dark, also in light themes). Home's status-bar scrim uses the same one. */
internal fun hearthSkyTop(colors: PocketColors): Color = if (colors.isDark) colors.bgDeep else Color(0xFF130A16)

/** The theme turned into the plain ARGB colours the scene renderer works with. */
internal fun hearthPalette(colors: PocketColors): ScenePalette {
    val skyBase = if (colors.isDark) colors.bg else Color(0xFF1D1220)
    return ScenePalette(
        skyTop = hearthSkyTop(colors).toArgb(),
        skyBottom = lerp(skyBase, colors.fireLow, 0.16f).toArgb(),
        farRamp = IntArray(4) { lerp(Color(FAR_RAMP[it]), skyBase, 0.2f).toArgb() },
        glow = colors.fireMid.toArgb() or (0xFF shl 24),
        fireLow = colors.fireLow.toArgb(),
        fireMid = colors.fireMid.toArgb(),
        fireHigh = colors.fireHigh.toArgb(),
    )
}

/**
 * The home hero: a night over the Zagros, a stone ring with a hand-keyed fire, drifting embers and Pet.
 * Fills the width; give it a height of about 250-320 dp (default 280 dp). [progress] (0..1) drives KINDLING.
 * [mood] overrides the mascot's mood (null = derived: COLD sleeping, KINDLING thinking, BLAZING idle with
 * an occasional happy hop, SMOKE error). Animations follow Pocket.anim: REDUCED is slower and has no
 * particles, OFF is one still frame. Draws nothing until the sprite bank is ready (a moment after start).
 */
@Composable
fun HearthScene(
    state: HearthState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    mood: MascotMood? = null,
    stokeBoost: Float = 0f,
    celebration: Boolean = false,
) {
    val ready by rememberSpritesReady()
    val colors = Pocket.colors
    val anim = Pocket.anim
    val clock = rememberSceneClock(anim != AnimLevel.OFF)
    val palette = remember(colors) { hearthPalette(colors) }
    val renderer = remember(ready) {
        if (!ready) {
            null
        } else {
            try {
                SceneRenderer(SpriteBank)
            } catch (e: Exception) {
                Log.e("PocketAgent/Pixel", "scene sprites missing", e)
                null
            }
        }
    }
    val surface = rememberStageSurface()

    Spacer(
        modifier
            .fillMaxWidth()
            .height(280.dp)
            .clipToBounds()
            .testTag("stage.hearth")
            .drawBehind {
                val r = renderer ?: return@drawBehind
                val t = clock.longValue / (if (anim == AnimLevel.REDUCED) 2 else 1)
                val scene = SceneState(state, palette, progress, mood, stokeBoost.coerceIn(0f, 1.5f), celebration)
                val quality = if (anim == AnimLevel.FULL) SceneQuality.FULL else SceneQuality.PLAIN
                surface.draw(this, StageGrid.forScene(size.width, size.height), Triple(scene, t, quality)) {
                    r.render(it, scene, t, quality)
                }
            },
    )
}
