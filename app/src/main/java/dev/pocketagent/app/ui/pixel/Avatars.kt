package dev.pocketagent.app.ui.pixel

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class AvatarId { NEWT, WOLF, FOX, OWL, EAGLE, GOAT, PARTRIDGE, BEE, LYNX, CAT, ROBOT, SUN, MOUNTAIN, HORSE, BEAR, DRAGON }

/** Safe parse of a stored avatar name (any case); unknown, empty or null falls back to the newt. */
fun avatarIdOf(name: String?): AvatarId {
    val n = name?.trim() ?: return AvatarId.NEWT
    return AvatarId.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } ?: AvatarId.NEWT
}

internal fun AvatarId.spriteName(): String = "avatar_" + name.lowercase()

private val DEEP = Color(0xFF12081C)

/** The tint ramp (darkest to lightest) the 24x24 portraits use for the user's colour. */
internal fun avatarRamp(color: Color): List<Color> = listOf(
    lerp(color, DEEP, 0.55f),
    lerp(color, DEEP, 0.28f),
    color,
    lerp(color, Color.White, 0.38f),
)

private const val TILE = 28   // logical px: 24 sprite + 2 px margin each side

/** A stepped (pixel) rounded rectangle without allocation: three overlapping-free bands. */
private fun DrawScope.steppedRect(c: Color, x: Float, y: Float, cells: Int, cut: Int, u: Float) {
    val w = cells * u
    // middle band full width, top and bottom bands inset by 'cut' cells, and the corner step in between
    px(c, x, y + cut * u, w, (cells - 2 * cut) * u)
    px(c, x + cut * u, y, (cells - 2 * cut) * u, cut * u)
    px(c, x + cut * u, y + (cells - cut) * u, (cells - 2 * cut) * u, cut * u)
    if (cut >= 2) {
        // second step of the corner: one cell wider than the top/bottom band
        px(c, x + u, y + u, w - 2 * u, u)
        px(c, x + u, y + (cells - 2) * u, w - 2 * u, u)
    }
}

/**
 * A bot's face: a 24x24 pixel portrait on a stepped tile in a darker shade of [color]; the portrait's main
 * colours are [color]'s tint ramp. [animated] adds a subtle blink (frames are offset per avatar so a list
 * of them does not blink in unison). The size is rounded down to whole pixels of a 28 px grid.
 */
@Composable
fun Avatar(id: AvatarId, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, animated: Boolean = false) {
    SpriteBank.init(LocalContext.current)
    val sprite = remember(id) { SpriteBank.get(id.spriteName()) }
    val ramp = remember(color) { avatarRamp(color).map { it.toArgb() }.toIntArray() }
    val tile = remember(color) { lerp(color, DEEP, 0.78f) }
    val border = remember(color) { lerp(color, DEEP, 0.58f) }
    val offset = remember(id, color) { ((id.ordinal * 977L + (color.toArgb() and 0xFFFF)) % sprite.totalMs) }
    val frame = rememberSpriteFrame(sprite, if (animated) null else 0, animated, offset)
    Spacer(
        modifier.size(size).drawBehind {
            val u = floor(min(this.size.width, this.size.height) / TILE).coerceAtLeast(1f)
            val side = TILE * u
            val x0 = ((this.size.width - side) / 2f).roundToInt().toFloat()
            val y0 = ((this.size.height - side) / 2f).roundToInt().toFloat()
            steppedRect(border, x0, y0, TILE, 2, u)
            steppedRect(tile, x0 + u, y0 + u, TILE - 2, 1, u)
            val f = frame.intValue.coerceIn(0, sprite.frames.size - 1)
            val img = SpriteBitmaps.get(sprite, f, false, 0, ramp)
            drawSpriteAt(img, (x0 + 2f * u).roundToInt(), (y0 + 2f * u).roundToInt(), u.roundToInt())
        },
    )
}
