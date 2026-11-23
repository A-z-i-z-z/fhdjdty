package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.PocketColors
import kotlin.math.roundToInt

enum class ButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }

private class ButtonLook(
    val face: Color, val border: Color, val lip: Color?, val highlight: Color?, val label: Color, val filled: Boolean,
)

private fun buttonLook(kind: ButtonKind, c: PocketColors, enabled: Boolean): ButtonLook {
    val a = if (enabled) 1f else 0.42f
    return when (kind) {
        ButtonKind.PRIMARY -> ButtonLook(
            face = c.accent.copy(alpha = a),
            border = lerp(c.accent, Color.Black, 0.55f).copy(alpha = a),
            lip = lerp(c.accent, Color.Black, 0.45f).copy(alpha = a),
            highlight = lerp(c.accent, Color.White, 0.38f).copy(alpha = a),
            label = c.onAccent.copy(alpha = a), filled = true,
        )
        ButtonKind.SECONDARY -> ButtonLook(
            face = c.surfaceHigh.copy(alpha = a),
            border = c.outline.copy(alpha = a),
            lip = lerp(c.outline, c.bgDeep, 0.5f).copy(alpha = a),
            highlight = lerp(c.surfaceHigh, Color.White, if (c.isDark) 0.12f else 0.5f).copy(alpha = a),
            label = c.text.copy(alpha = a), filled = true,
        )
        ButtonKind.GHOST -> ButtonLook(
            face = Color.Transparent, border = Color.Transparent, lip = null, highlight = null,
            label = c.accentSoft.copy(alpha = a), filled = false,
        )
        ButtonKind.DANGER -> {
            val fg = if (c.isDark) c.bgDeep else c.bg
            ButtonLook(
                face = c.error.copy(alpha = a),
                border = lerp(c.error, Color.Black, 0.5f).copy(alpha = a),
                lip = lerp(c.error, Color.Black, 0.42f).copy(alpha = a),
                highlight = lerp(c.error, Color.White, 0.3f).copy(alpha = a),
                label = fg.copy(alpha = a), filled = true,
            )
        }
    }
}

/**
 * Pixel button. The face sits on a one-pixel lip and sinks onto it while pressed; PRIMARY has a slow
 * ember glow (static when animations are reduced or off). At least 48 dp high. Label is in the system
 * font (semibold) so long translated strings wrap instead of overflowing.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PixelIcon? = null,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val c = Pocket.colors
    val anim = Pocket.anim
    val look = remember(kind, c, enabled) { buttonLook(kind, c, enabled) }
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val view = LocalView.current
    val pixel = 2.dp
    val sinkPx = with(LocalDensity.current) { pixel.toPx() }.roundToInt().coerceAtLeast(1)

    val wantsGlow = kind == ButtonKind.PRIMARY && enabled
    val glowState: State<Float>? = if (wantsGlow && anim == AnimLevel.FULL) {
        rememberInfiniteTransition(label = "pixelButtonGlow").animateFloat(
            initialValue = 0.10f, targetValue = 0.26f, label = "glow",
            animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
        )
    } else null
    val glowColor = if (wantsGlow) c.fireMid else null

    val surface = if (look.filled) {
        Modifier.pixelSurface(
            face = look.face, border = look.border, corner = 2, pixel = pixel,
            highlight = look.highlight, glow = glowColor,
            glowAlpha = { glowState?.value ?: 0.16f },
            lip = look.lip, pressed = { pressed.value },
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .then(surface)
            .clip(PixelShape(2, pixel))
            .clickable(
                interactionSource = interaction,
                indication = if (look.filled) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = { view.pixelTap(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .offset { IntOffset(0, if (look.filled && pressed.value) sinkPx else 0) }
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + if (look.filled) pixel else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) PixelIconView(icon, tint = look.label, size = 24.dp)
            Text(
                text = text,
                color = look.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/**
 * Icon-only button: 48 dp touch target, 24 dp icon, hard-edged press tint. [contentDescription] is read
 * by screen readers; give one that says what the button does.
 */
@Composable
fun PixelIconButton(
    icon: PixelIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Pocket.colors.text,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    val label = contentDescription ?: icon.name.lowercase()
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(PixelShape(2, 2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = { view.pixelTap(); onClick() })
            .semantics { this.contentDescription = label }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        PixelIconView(icon, tint = tint, size = 24.dp)
    }
}
