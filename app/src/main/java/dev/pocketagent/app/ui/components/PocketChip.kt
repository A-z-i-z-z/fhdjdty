package dev.pocketagent.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.rememberHaptics

/**
 * Selectable pill. Visually 36 dp high, with a 48 dp touch target. Selected = accent fill with the
 * accent's own dark border; unselected = surface with an outline. It sinks one design pixel while pressed
 * (no tint) and gives the TICK cue.
 */
@Composable
fun PocketChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PixelIcon? = null,
) {
    val c = Pocket.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPixelPress(interaction)
    val face = if (selected) c.accent else c.surface
    val border = if (selected) lerp(c.accent, Color.Black, 0.55f) else c.outline
    val label = if (selected) c.onAccent else c.text
    val hl = if (selected) lerp(c.accent, Color.White, 0.35f) else lerp(c.surface, Color.White, if (c.isDark) 0.08f else 0.5f)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pixelSurface(
                face = face, border = border, corner = Corner.chip, pixel = 2.dp, highlight = hl,
                pressDepthPx = { press.sinkPx },
            )
            .clip(PixelShape(Corner.chip, 2.dp))
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction, indication = null, role = Role.Button,
                onClick = { haptics.play(HapticCue.TICK); onClick() },
            )
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PixelIconView(icon, tint = label, size = 24.dp, modifier = Modifier.pixelPressOffset(press))
        Text(
            text = text,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.pixelPressOffset(press),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}
