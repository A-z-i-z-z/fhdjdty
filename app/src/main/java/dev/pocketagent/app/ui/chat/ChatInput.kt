package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.PendingApproval
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.data.SlashCommands.SlashCommandDef
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
import androidx.compose.ui.platform.testTag
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.ui.text.t

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
    val motion = Pocket.motion
    val on = Pocket.anim == AnimLevel.FULL
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (on && trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, motion.scene<Float>())
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
    /** The Hermes rootfs is unpacked (`RuntimeManager.isRootfsValid()`); without it a send cannot wake anything. */
    runtimeInstalled: Boolean,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    sparkTrigger: Int,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
    onOpenSetup: () -> Unit,
    attachments: List<Attachment> = emptyList(),
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onSlashPick: (SlashCommandDef) -> Unit = {},
    listening: Boolean = false,
    onMicClick: () -> Unit = {},
    /** Wake-on-send is on: the input stays usable while Hermes sleeps; sending lights the fire first. */
    wakeOnSend: Boolean = false,
    /** The engine is waiting for Hermes to start before it delivers the message of this chat. */
    waking: Boolean = false,
) {
    val c = Pocket.colors
    val canChat = inputAvailable(runtime, setupIncomplete, wakeOnSend, runtimeInstalled)
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.outline))
        if (!canChat) {
            UnavailableCard(runtime, onStart, onOpenSetup)
        } else {
            WakingBanner(waking)
            val slashSuggestions = if (!editing && !busy) SlashCommands.suggestions(text) else emptyList()
            SlashCommandPalette(slashSuggestions, onSlashPick)

            val query = if (isGroup && slashSuggestions.isEmpty()) mentionQuery(text) else null
            val suggestions = if (query != null) mentionSuggestions(query, members) else emptyList()
            AnimatedVisibility(suggestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.s, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    suggestions.forEach { bot ->
                        val label = t(R.string.ui_chat_mention_cd, bot.name)
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)
                                .clickable(onClickLabel = label, role = Role.Button) { onTextChange(applyMention(text, bot)) }
                                .padding(start = Space.xs, end = Space.m, top = Space.xs, bottom = Space.xs),
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
                Row(Modifier.fillMaxWidth().padding(start = Space.m, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    PixelIconView(PixelIcon.EDIT, tint = c.accent, size = 18.dp)
                    Spacer(Modifier.width(Space.s))
                    Text(t(R.string.ui_chat_editing), color = c.accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    PixelIconButton(PixelIcon.CLOSE, onClick = onCancelEdit, contentDescription = t(R.string.ui_chat_edit_cancel))
                }
            }
            AnimatedVisibility(attachments.isNotEmpty() && !editing) {
                PendingAttachmentsStrip(attachments, onRemoveAttachment)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s), verticalAlignment = Alignment.Bottom) {
                if (!editing) {
                    PixelIconButton(
                        PixelIcon.CLIP,
                        onClick = onAttachClick,
                        modifier = Modifier.testTag(RobotTags.CHAT_ATTACH),
                        contentDescription = t(R.string.ui_attach_cd),
                        tint = if (!busy) c.textMuted else c.outline,
                        enabled = !busy,
                    )
                    Spacer(Modifier.width(Space.xxs))
                }
                PocketTextField(
                    value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f).testTag(RobotTags.CHAT_INPUT),
                    placeholder = t(
                        when {
                            isGroup && mentionOnly -> R.string.ui_chat_input_hint_mention
                            isGroup -> R.string.ui_chat_input_hint_group
                            else -> R.string.ui_chat_input_hint
                        },
                    ),
                    minLines = 1, maxLines = 6,
                )
                Spacer(Modifier.width(Space.xxs))
                if (!busy && !editing) {
                    PixelIconButton(
                        if (listening) PixelIcon.WAVE else PixelIcon.MIC,
                        onClick = onMicClick,
                        modifier = Modifier.testTag(RobotTags.CHAT_MIC),
                        contentDescription = t(if (listening) R.string.ui_voice_listening else R.string.ui_voice_mic_cd),
                        tint = if (listening) c.fireMid else c.textMuted,
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (busy) {
                        PixelIconButton(PixelIcon.STOP, onClick = onStop, modifier = Modifier.testTag(RobotTags.CHAT_STOP), contentDescription = t(R.string.ui_chat_stop), tint = c.error)
                    } else {
                        val ready = text.isNotBlank() || attachments.isNotEmpty()
                        PixelIconButton(
                            PixelIcon.SEND, onClick = onSend, modifier = Modifier.testTag(RobotTags.CHAT_SEND), contentDescription = t(R.string.ui_chat_send),
                            tint = if (ready) c.accent else c.textMuted, enabled = ready,
                        )
                    }
                    SparkBurst(sparkTrigger)
                }
            }
        }
    }
}

/** A slim line above the input while the engine waits for Hermes to start (the message is already shown and will be sent). */
@Composable
private fun WakingBanner(visible: Boolean) {
    val c = Pocket.colors
    AnimatedVisibility(visible) {
        Row(
            Modifier.fillMaxWidth().background(c.accentSoft.copy(alpha = 0.16f)).padding(horizontal = Space.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GeneratingEmber(size = 16.dp)
            Spacer(Modifier.width(Space.s))
            Text(t(R.string.ui_chat_waking_banner), color = c.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun UnavailableCard(runtime: RuntimeState, onStart: () -> Unit, onOpenSetup: () -> Unit) {
    val c = Pocket.colors
    val waking = runtime is RuntimeState.Extracting || runtime is RuntimeState.Starting
    Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        when {
            waking -> Text(t(R.string.ui_chat_starting), color = c.text, fontSize = 15.sp)
            runtime == RuntimeState.Running -> {
                Text(t(R.string.ui_chat_setup_body), color = c.text, fontSize = 15.sp)
                PixelButton(t(R.string.ui_chat_setup_btn), onClick = onOpenSetup, modifier = Modifier.fillMaxWidth(), icon = PixelIcon.GEAR)
            }
            else -> {
                Text(t(R.string.ui_chat_off_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(t(R.string.ui_chat_off_body), color = c.textMuted, fontSize = 14.sp)
                PixelButton(
                    t(R.string.ui_home_btn_start), onClick = onStart, modifier = Modifier.fillMaxWidth(),
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
        Modifier.fillMaxWidth().background(c.surface).border(2.dp, c.warning).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIconView(PixelIcon.KEY, tint = c.warning, size = 20.dp)
            Spacer(Modifier.width(Space.s))
            Text(t(R.string.ui_chat_appr_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!botName.isNullOrBlank()) Text(t(R.string.ui_chat_appr_who, botName), color = c.textMuted, fontSize = 13.sp)
        if (approval.description.isNotBlank()) Text(approval.description, color = c.text, fontSize = 14.sp)
        if (approval.command.isNotBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.codeBg).padding(Space.s)) {
                Text(
                    approval.command, color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                    maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The last answer did not reach Hermes: say so here; the buttons below stay usable (and so does Stop).
        approval.errorLine?.let {
            Text(it, color = c.error, fontSize = 13.sp, modifier = Modifier.testTag("chat.approval.error"))
        }
        val choices = approval.choices.ifEmpty { listOf("once", "deny") }
        choices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { ch ->
                    PixelButton(
                        approvalLabel(ch), onClick = { onChoice(ch) }, modifier = Modifier.weight(1f).testTag("chat.approval.$ch"),
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
    "once" -> t(R.string.ui_chat_appr_once)
    "session" -> t(R.string.ui_chat_appr_session)
    "always" -> t(R.string.ui_chat_appr_always)
    "deny" -> t(R.string.ui_chat_appr_deny)
    else -> choice
}
