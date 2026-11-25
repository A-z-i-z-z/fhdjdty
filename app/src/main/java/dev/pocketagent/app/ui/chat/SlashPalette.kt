package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.pocketagent.app.R
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.data.SlashCommands.SlashCategory
import dev.pocketagent.app.data.SlashCommands.SlashCommandDef
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket

@Composable
internal fun SlashCommandPalette(
    commands: List<SlashCommandDef>,
    onPick: (SlashCommandDef) -> Unit,
) {
    val c = Pocket.colors
    AnimatedVisibility(commands.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(c.bgDeep)
                .border(2.dp, c.outline)
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            commands.forEach { cmd ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onPick(cmd) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PixelIconView(slashIcon(cmd.name), tint = c.accent, size = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            cmd.usage, color = c.text, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(slashDescRes(cmd.name)), color = c.textMuted,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isMode = cmd.category == SlashCategory.MODE
                    Text(
                        stringResource(if (isMode) R.string.ui_slash_mode_badge else R.string.ui_slash_action_badge),
                        color = if (isMode) c.onAccent else c.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(if (isMode) c.accent else c.surfaceHigh)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActiveModeBanner(mode: ChatMode, onClear: () -> Unit) {
    if (mode == ChatMode.NORMAL) return
    val c = Pocket.colors
    val label = when (mode) {
        ChatMode.PLAN -> stringResource(R.string.ui_mode_banner_plan)
        ChatMode.GOAL -> stringResource(R.string.ui_mode_banner_goal)
        ChatMode.ULTRACODE -> stringResource(R.string.ui_mode_banner_ultracode)
        ChatMode.LOOP -> stringResource(R.string.ui_mode_banner_loop)
        ChatMode.NORMAL -> ""
    }
    val icon = when (mode) {
        ChatMode.PLAN -> PixelIcon.SCROLL
        ChatMode.GOAL -> PixelIcon.SPARK
        ChatMode.ULTRACODE -> PixelIcon.TERMINAL
        ChatMode.LOOP -> PixelIcon.REFRESH
        ChatMode.NORMAL -> PixelIcon.BOLT
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.accentSoft.copy(alpha = 0.16f))
            .border(1.dp, c.accent)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconView(icon, tint = c.accent, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        PixelIconButton(
            PixelIcon.CLOSE, onClick = onClear,
            contentDescription = stringResource(R.string.ui_mode_exit_cd), tint = c.textMuted,
        )
    }
}

@Composable
internal fun EmptyChatQuickActions(onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        PocketChip("/plan", false, { onPick("/plan ") }, icon = PixelIcon.SCROLL)
        PocketChip("/ultracode", false, { onPick("/ultracode ") }, icon = PixelIcon.TERMINAL)
        PocketChip("/forge", false, { onPick("/forge ") }, icon = PixelIcon.FORGE)
        PocketChip("/skills", false, { onPick("/skills") }, icon = PixelIcon.SKILL)
    }
}

/** Non-blocking festive Newroz ember shower triggered by the secret `/newroz` command. */
@Composable
internal fun NewrozCelebrationOverlay(trigger: Int) {
    if (trigger <= 0 || Pocket.anim == AnimLevel.OFF) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2400, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    val palette = remember {
        listOf(
            Color(0xFFFEBD11), // saffron gold
            Color(0xFFED1C24), // Newroz crimson
            Color(0xFF278E43), // spring green
            Color(0xFFFF7A2E), // flame ember
            Color(0xFFFFF1DC), // bright spark
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val px = 5.dp.toPx()
        for (i in 0 until 44) {
            val seedX = ((i * 73 + 19) % 100) / 100f
            val speed = 0.7f + (i % 5) * 0.22f
            val drift = kotlin.math.sin((p * 9f + i).toDouble()).toFloat() * 14.dp.toPx()
            val x = seedX * size.width + drift
            val y = -20.dp.toPx() + p * speed * (size.height + 60.dp.toPx())
            if (y < 0f || y > size.height) continue
            val alpha = ((1f - p) * 1.2f).coerceIn(0f, 1f)
            drawRect(palette[i % palette.size].copy(alpha = alpha), Offset(x, y), Size(px, px))
        }
    }
}

private fun slashIcon(name: String): PixelIcon = when (name) {
    "plan" -> PixelIcon.SCROLL
    "goal" -> PixelIcon.SPARK
    "ultracode" -> PixelIcon.TERMINAL
    "loop" -> PixelIcon.REFRESH
    "normal" -> PixelIcon.CHECK
    "forge" -> PixelIcon.FORGE
    "skills" -> PixelIcon.SKILL
    "remember", "memory" -> PixelIcon.MEMORY
    "theme" -> PixelIcon.PALETTE
    "find" -> PixelIcon.SEARCH
    "export" -> PixelIcon.SHARE
    "clear" -> PixelIcon.TRASH
    else -> PixelIcon.BOLT
}

private fun slashDescRes(name: String): Int = when (name) {
    "plan" -> R.string.ui_slash_desc_plan
    "goal" -> R.string.ui_slash_desc_goal
    "ultracode" -> R.string.ui_slash_desc_ultracode
    "loop" -> R.string.ui_slash_desc_loop
    "normal" -> R.string.ui_slash_desc_normal
    "forge" -> R.string.ui_slash_desc_forge
    "skills" -> R.string.ui_slash_desc_skills
    "remember" -> R.string.ui_slash_desc_remember
    "memory" -> R.string.ui_slash_desc_memory
    "theme" -> R.string.ui_slash_desc_theme
    "find" -> R.string.ui_slash_desc_find
    "export" -> R.string.ui_slash_desc_export
    "clear" -> R.string.ui_slash_desc_clear
    else -> R.string.ui_slash_desc_help
}
