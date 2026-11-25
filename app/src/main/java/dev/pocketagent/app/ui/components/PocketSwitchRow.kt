package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.ui.theme.rememberHaptics
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-width settings row: title (+ optional subtitle) on the start side, a pixel toggle on the end side.
 * The whole row is the touch target (min 56 dp) and announces itself as a switch. [modifier] goes first, so a
 * caller can tag or size the row.
 */
@Composable
fun PocketSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = Pocket.colors
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { haptics.play(HapticCue.TICK); onCheckedChange(it) },
            )
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = c.text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(Space.m))
        PixelSwitch(checked)
    }
}

/** 24 x 12 design pixels (48 x 24 dp): bordered stepped track, square thumb that hops pixel by pixel. */
@Composable
private fun PixelSwitch(checked: Boolean) {
    val c = Pocket.colors
    val motion = Pocket.motion
    val t = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motion.fast(),
        label = "pixelSwitch",
    )
    val track = if (checked) c.accent else c.bgDeep
    val border = if (checked) lerp(c.accent, Color.Black, 0.55f) else c.outline
    val thumb = if (checked) c.onAccent else c.textMuted
    Spacer(
        Modifier
            .size(48.dp, 24.dp)
            .drawWithCache {
                val p = max(1f, floor(minOf(size.width / 24f, size.height / 12f)))
                val ox = (size.width - 24 * p) / 2f
                val oy = (size.height - 12 * p) / 2f
                val outer = Path().apply { stepRect(0f, 0f, 24 * p, 12 * p, 2, p) }
                val inner = Path().apply { stepRect(p, p, 23 * p, 11 * p, 1, p) }
                val thumbPath = Path().apply { stepRect(0f, 0f, 8 * p, 8 * p, 1, p) }
                val thumbShade = lerp(thumb, Color.Black, 0.3f)
                onDrawBehind {
                    translate(ox, oy) {
                        drawPath(outer, border)
                        drawPath(inner, track)
                        val k = (t.value * 12f).roundToInt()
                        if (k >= 6) drawRect(c.onAccent.copy(alpha = 0.55f), Offset(5 * p, 4 * p), Size(p, 4 * p))
                        translate((2 + k) * p, 2 * p) {
                            drawPath(thumbPath, thumb)
                            drawRect(thumbShade, Offset(p, 7 * p), Size(6 * p, p))
                        }
                    }
                }
            },
    )
}
