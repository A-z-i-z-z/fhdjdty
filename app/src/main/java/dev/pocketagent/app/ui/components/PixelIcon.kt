package dev.pocketagent.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.pixel.PixelImage
import dev.pocketagent.app.ui.pixel.SpriteBank
import dev.pocketagent.app.ui.theme.Pocket

/**
 * UI icons, 12x12 monochrome sprites in assets/pixel/icons.pxl, named `icon_<lowercase enum name>`.
 * Draw them at a multiple of 12 dp (24 default, 36, 48) so every sprite pixel is a whole number of dp.
 */
enum class PixelIcon {
    HEARTH, CHATS, BOTS, SCROLL, TERMINAL, GEAR, SEND, STOP, PLUS, BACK, MORE, COPY, EDIT, TRASH, PIN, GROUP,
    TOOL, MEMORY, SKILL, SOUL, SEARCH, REFRESH, PLAY, CHECK, CLOSE, PALETTE, GLOBE, BUG, DOWN, UP, SPARK,
    SHARE, USER, KEY, INFO, MIC, CLIP, CAMERA, IMAGE, FILE, BOLT, FORGE,
    VOLUME, MUTE, FORK, SLIDERS, FILTER, APPICON, WAVE, MUSIC,
    CLOCK, STAR, STAR_OFF, BELL, CHART, ARCHIVE, DOWNLOAD, UPLOAD, MOON, LINK;

    /** Sprite name in the sprite bank. */
    val spriteName: String get() = "icon_" + name.lowercase()
}

/** Decorative by default; pass [contentDescription] when the icon stands alone and carries meaning. */
@Composable
fun PixelIconView(
    icon: PixelIcon,
    modifier: Modifier = Modifier,
    tint: Color = Pocket.colors.text,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val sprite = remember(icon) { SpriteBank.find(icon.spriteName) }
    val base = modifier.size(size)
    val sem = if (contentDescription != null) base.semantics { this.contentDescription = contentDescription } else base
    if (sprite == null) {
        Spacer(sem)
    } else {
        PixelImage(sprite = sprite, modifier = sem, tint = tint)
    }
}
