package dev.pocketagent.app.ui.pixel

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MascotMood { IDLE, THINKING, TALKING, HAPPY, SLEEPING, ERROR, WAVING }

/** Sprite name in mascot.pxl for each mood. */
internal fun MascotMood.spriteName(): String = when (this) {
    MascotMood.IDLE -> "pet_idle"
    MascotMood.THINKING -> "pet_thinking"
    MascotMood.TALKING -> "pet_talking"
    MascotMood.HAPPY -> "pet_happy"
    MascotMood.SLEEPING -> "pet_sleeping"
    MascotMood.ERROR -> "pet_error"
    MascotMood.WAVING -> "pet_waving"
}

/**
 * "Pet" (Kurmanji for spark), the Kurdistan newt. 32x32 sprites; the size is rounded down to a whole
 * multiple of 32 px for crisp pixels (96 dp is 8x on a 3x-density phone). Animations follow Pocket.anim.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    PixelImage(mood.spriteName(), modifier.size(size))
}
