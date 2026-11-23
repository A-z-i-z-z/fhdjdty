package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.PendingApproval
import dev.pocketagent.app.data.Transcript
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import kotlin.math.cos
import kotlin.math.sin

// The input area of a chat (agent CHAT): text field, send / stop, @mentions, the "fire is out" card.

// ---- @mentions ------------------------------------------------------------------------------------------------

/** A bot is mentioned as @ followed by its full name (the engine also accepts its first word). */
internal fun mentionName(bot: Bot): String = bot.name.trim()

/** The text typed after the last "@" when it is still being typed at the end of [text], else null. */
internal fun mentionQuery(text: String): String? {
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !text[at - 1].isWhitespace()) return null
    val q = text.substring(at + 1)
    return if (q.any { it.isWhitespace() }) null else q
}

internal fun applyMention(text: String, bot: Bot): String =
    text.substring(0, text.lastIndexOf('@').coerceAtLeast(0)) + "@" + mentionName(bot) + " "

/** Members whose name (or first word) starts with what was typed after the "@". */
internal fun mentionSuggestions(query: String, members: List<Bot>): List<Bot> {
    val q = Transcript.fold(query)
    return members.filter { b ->
        val n = Transcript.fold(mentionName(b))
        n.startsWith(q) || n.split(' ').any { it.startsWith(q) }
    }
}

/** Ids of the members @mentioned in [text] (same rules as the engine). */
internal fun mentionedBotIds(text: String, members: List<Bot>): List<String> = Transcript.mentionedBotIds(text, members)

// ---- spark burst ----------------------------------------------------------------------------------------------

/** A small burst of embers over the send button each time [trigger] changes (appearance animations FULL only). */
@Composable
internal fun SparkBurst(trigger: Int, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val on = Pocket.anim == AnimLevel.FULL
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (on && trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = LinearOutSlowInEasing))
        }
    }
    val colors = remember(c) { listOf(c.fireHigh, c.fireMid, c.ember) }
    Canvas(modifier.size(48.dp)) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val px = 3.dp.toPx()
        for (i in 0 until 10) {
            val ang = Math.toRadians(200.0 + i * 15.5)
            val dist = 16.dp.toPx() + (22 + (i % 3) * 9).dp.toPx() * p
            val x = cx + (cos(ang) * dist).toFloat()
            val y = cy + (sin(ang) * dist).toFloat()
            drawRect(colors[i % 3].copy(alpha = 1f - p), Offset(x - px / 2f, y - px / 2f), Size(px, px))
        }
    }
}

// ---- the bar ------------------------------------------------------------------------------------------------------

@Composable
internal fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    members: List<Bot>,
    isGroup: Boolean,
    mentionOnly: Boolean,
    busy: Boolean,
    runtime: RuntimeState,
    setupIncomplete: Boolean,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    sparkTrigger: Int,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val c = Pocket.colors
    val canChat = runtime == RuntimeState.Running && !setupIncomplete
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.outline))
        if (!canChat) {
            UnavailableCard(runtime, onStart, onOpenSetup)
        } else {
            val query = if (isGroup) mentionQuery(text) else null
            val suggestions = if (query != null) mentionSuggestions(query, members) else emptyList()
            AnimatedVisibility(suggestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { bot ->
                        val label = stringResource(R.string.ui_chat_mention_cd, bot.name)
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                                .clickable(onClickLabel = label, role = Role.Button) { onTextChange(applyMention(text, bot)) }
                                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotAvatar(bot, 24.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("@" + mentionName(bot), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            AnimatedVisibility(editing) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIconView(PixelIcon.EDIT, tint = c.accent, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_chat_editing), color = c.accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    PixelIconButton(PixelIcon.CLOSE, onClick = onCancelEdit, contentDescription = stringResource(R.string.ui_chat_edit_cancel))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                PocketTextField(
                    value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f),
                    placeholder = stringResource(
                        when {
                            isGroup && mentionOnly -> R.string.ui_chat_input_hint_mention
                            isGroup -> R.string.ui_chat_input_hint_group
                            else -> R.string.ui_chat_input_hint
                        },
                    ),
                    minLines = 1, maxLines = 6,
                )
                Spacer(Modifier.width(4.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (busy) {
                        PixelIconButton(PixelIcon.STOP, onClick = onStop, contentDescription = stringResource(R.string.ui_chat_stop), tint = c.error)
                    } else {
                        val ready = text.isNotBlank()
                        PixelIconButton(
                            PixelIcon.SEND, onClick = onSend, contentDescription = stringResource(R.string.ui_chat_send),
                            tint = if (ready) c.accent else c.textMuted, enabled = ready,
                        )
                    }
                    SparkBurst(sparkTrigger)
                }
            }
        }
    }
}

@Composable
private fun UnavailableCard(runtime: RuntimeState, onStart: () -> Unit, onOpenSetup: () -> Unit) {
    val c = Pocket.colors
    val waking = runtime is RuntimeState.Extracting || runtime is RuntimeState.Starting
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            waking -> Text(stringResource(R.string.ui_chat_starting), color = c.text, fontSize = 15.sp)
            runtime == RuntimeState.Running -> {
                Text(stringResource(R.string.ui_chat_setup_body), color = c.text, fontSize = 15.sp)
                PixelButton(stringResource(R.string.ui_chat_setup_btn), onClick = onOpenSetup, modifier = Modifier.fillMaxWidth(), icon = PixelIcon.GEAR)
            }
            else -> {
                Text(stringResource(R.string.ui_chat_off_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ui_chat_off_body), color = c.textMuted, fontSize = 14.sp)
                PixelButton(
                    stringResource(R.string.ui_home_btn_start), onClick = onStart, modifier = Modifier.fillMaxWidth(),
                    icon = PixelIcon.PLAY, kind = ButtonKind.PRIMARY,
                )
            }
        }
    }
}

// ---- permission requests ---------------------------------------------------------------------------------------

/** Hermes asks before it runs a flagged command; the turn waits for the answer. */
@Composable
internal fun ApprovalCard(approval: PendingApproval, botName: String?, onChoice: (String) -> Unit) {
    val c = Pocket.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface).border(2.dp, c.warning).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIconView(PixelIcon.KEY, tint = c.warning, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ui_chat_appr_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!botName.isNullOrBlank()) Text(stringResource(R.string.ui_chat_appr_who, botName), color = c.textMuted, fontSize = 13.sp)
        if (approval.description.isNotBlank()) Text(approval.description, color = c.text, fontSize = 14.sp)
        if (approval.command.isNotBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.codeBg).padding(8.dp)) {
                Text(
                    approval.command, color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                    maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val choices = approval.choices.ifEmpty { listOf("once", "deny") }
        choices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch ->
                    PixelButton(
                        approvalLabel(ch), onClick = { onChoice(ch) }, modifier = Modifier.weight(1f),
                        kind = when (ch) { "deny" -> ButtonKind.DANGER; "once" -> ButtonKind.PRIMARY; else -> ButtonKind.SECONDARY },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun approvalLabel(choice: String): String = when (choice) {
    "once" -> stringResource(R.string.ui_chat_appr_once)
    "session" -> stringResource(R.string.ui_chat_appr_session)
    "always" -> stringResource(R.string.ui_chat_appr_always)
    "deny" -> stringResource(R.string.ui_chat_appr_deny)
    else -> choice
}
