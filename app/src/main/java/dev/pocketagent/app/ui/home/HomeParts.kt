package dev.pocketagent.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.ui.chat.BotAvatar
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PixelText
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket

// Small building blocks of the home screen (agent CHAT).

/** Title in the bitmap font, or a bold system font when the user turned pixel titles off. */
@Composable
internal fun PixelHeading(text: String, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    if (Pocket.appearance.pixelFontTitles) PixelText(text, modifier, color = c.text, pixel = 3.dp)
    else Text(text, modifier, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A chunky pixel progress bar. [fraction] null = indeterminate (a block sliding back and forth).
 * The fill grows in 4 dp steps so it keeps the pixel look.
 */
@Composable
internal fun PixelProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val anim = Pocket.anim
    val target = (fraction ?: 0f).coerceIn(0f, 1f)
    val smooth by animateFloatAsState(target, if (anim == AnimLevel.OFF) snap<Float>() else tween<Float>(400), label = "progress")
    val t = rememberInfiniteTransition(label = "indeterminate")
    val slide = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "slide")
    Canvas(modifier.fillMaxWidth().height(12.dp).background(c.bgDeep).border(2.dp, c.outline)) {
        val step = 4.dp.toPx()
        if (fraction != null) {
            val w = (size.width * smooth / step).toInt() * step
            if (w > 0f) drawRect(c.fireMid, Offset.Zero, Size(w, size.height))
            if (w > step) drawRect(c.fireHigh, Offset.Zero, Size(w, step / 2f))
        } else {
            val w = (size.width * 0.3f / step).toInt() * step
            val x = ((size.width - w) * (if (anim == AnimLevel.OFF) 0.5f else slide.value) / step).toInt() * step
            drawRect(c.fireMid, Offset(x, 0f), Size(w, size.height))
            drawRect(c.fireHigh, Offset(x, 0f), Size(w, step / 2f))
        }
    }
}

/** A bot in the carousel: avatar and name; tap opens a chat with it. */
@Composable
internal fun BotCard(bot: Bot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val label = stringResource(R.string.ui_home_chat_with, bot.name)
    PixelPanel(modifier.width(96.dp).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), color = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BotAvatar(bot, 48.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                bot.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun NewBotCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    PixelPanel(modifier.width(96.dp).clickable(role = Role.Button, onClick = onClick), color = c.bg, border = c.accent) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { PixelIconView(PixelIcon.PLUS, tint = c.accent, size = 28.dp) }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ui_home_new_bot), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun HomeTile(icon: PixelIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    PixelPanel(modifier.clickable(role = Role.Button, onClick = onClick), color = c.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelIconView(icon, tint = c.accent, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
