package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.TypingIndicator
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.BubbleStyle
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.Loop
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.ui.theme.rememberHaptics
import dev.pocketagent.app.voice.SoundCue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The pieces of one chat message (agent CHAT). MessageRow takes only stable inputs so that, while a reply
// streams, only the streaming row recomposes.

private val AVATAR = 34.dp

/** Long press with haptic feedback, plus the same action for accessibility services. */
@Composable
private fun Modifier.longPressAction(label: String, action: () -> Unit): Modifier {
    val haptics = rememberHaptics()
    val current by rememberUpdatedState(action)
    return this
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                haptics.play(HapticCue.HOLD)
                current()
            })
        }
        .semantics { onLongClick(label = label) { current(); true } }
}

@Composable
internal fun MessageRow(
    msg: Message,
    bot: Bot?,
    layout: RowLayout,
    actions: ChatRowActions,
    botsById: Map<String, Bot>,
    convBotIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.ui_chat_message_cd)
    val press = { actions.onLongPress(msg) }
    val accent = Pocket.colors.accent
    val grouping = layout.grouping
    Column(modifier.fillMaxWidth().padding(top = if (grouping.showHeader) 8.dp else 0.dp)) {
        if (grouping.showDay) DayChip(msg.createdAt)
        // The wrapper is always there (only its modifier changes) so highlighting never resets the state of the row below.
        Box(
            Modifier.fillMaxWidth().then(
                if (layout.highlighted) Modifier.border(2.dp, accent, RoundedCornerShape(8.dp)).padding(2.dp) else Modifier,
            ),
        ) {
            when (msg.role) {
                Role.USER -> UserMessage(msg, grouping.isLastInRun, label, press, actions.onOpenImage)
                Role.BOT -> BotMessage(
                    msg, bot, grouping.showHeader, layout.showName, grouping.isLastInRun, label, press,
                    botsById, convBotIds, layout.isGroup,
                    layout.canRegenerate, layout.isSpeaking, actions.onSpeak, actions.onCopy, actions.onFork, actions.onRetry,
                    actions.onRunCode, actions.onChatWithBot, actions.onAddBotToChat, actions.onEditBot,
                )
                Role.TOOL -> ToolCard(msg, label, press, layout.toolIndex, actions.onOpenMemory) { actions.onOpenToolOutput(msg) }
                Role.ERROR -> ErrorCard(msg, layout.canRetry, label, press, actions.onRetry, actions.onCopy)
                Role.NOTE -> Text(
                    msg.text, color = Pocket.colors.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp).testTag("chat.msg.note"),
                )
            }
        }
    }
}

@Composable
private fun DayChip(ms: Long) {
    val c = Pocket.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            dayLabel(ms), color = c.textMuted, fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** The message bubble: stepped pixel corners or soft rounded ones, per the appearance setting. */
@Composable
private fun Bubble(
    color: Color,
    border: Color,
    own: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (Pocket.appearance.bubbleStyle) {
        BubbleStyle.PIXEL -> PixelPanel(modifier, color = color, border = border, corner = 2) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
        BubbleStyle.SOFT -> {
            val shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (own) 16.dp else 4.dp, bottomEnd = if (own) 4.dp else 16.dp,
            )
            Box(modifier.clip(shape).background(color).padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
    }
}

@Composable
private fun maxBubbleWidth() = (LocalConfiguration.current.screenWidthDp * 0.84f).dp

/** Time, duration and tokens of a message, with a small star when it is saved. */
@Composable
private fun Meta(msg: Message, modifier: Modifier = Modifier, showStar: Boolean = true) {
    val c = Pocket.colors
    val time = clockText(msg.createdAt)
    val dur = msg.durationMs?.let {
        if (msg.role == Role.BOT && it >= 1000) " · " + stringResource(R.string.ui_chat_sec, (it / 1000).toInt()) else ""
    } ?: ""
    val usage = if (msg.role == Role.BOT && (msg.tokensIn > 0L || msg.tokensOut > 0L)) {
        val locale = currentLocale()
        " · " + stringResource(R.string.ui_usage_meta, compactTokens(msg.tokensIn, locale), compactTokens(msg.tokensOut, locale))
    } else ""
    Row(modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time + dur + usage, color = c.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (msg.starred && showStar) {
            Spacer(Modifier.width(4.dp))
            PixelIconView(PixelIcon.STAR, tint = c.accent, size = 12.dp, contentDescription = stringResource(R.string.ui_star_cd))
        }
    }
}

// ---- user ---------------------------------------------------------------------------------------------------------

@Composable
private fun UserMessage(
    msg: Message,
    showTime: Boolean,
    label: String,
    onLongPress: () -> Unit,
    onOpenImage: (Attachment) -> Unit,
) {
    val c = Pocket.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Bubble(
            c.userBubble, lerp(c.userBubble, Color.Black, 0.3f), own = true,
            modifier = Modifier.widthIn(max = maxBubbleWidth()).testTag("chat.msg.user").longPressAction(label, onLongPress),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.attachments.isNotEmpty()) {
                    MessageAttachmentsView(msg.attachments, onOpenImage)
                }
                if (msg.text.isNotBlank()) {
                    Text(msg.text, color = c.onUserBubble, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
        if (showTime || msg.starred) Meta(msg)
    }
}

// ---- bot ------------------------------------------------------------------------------------------------------------

@Composable
private fun BotMessage(
    msg: Message, bot: Bot?, showHeader: Boolean, showName: Boolean, showTime: Boolean,
    label: String, onLongPress: () -> Unit,
    botsById: Map<String, Bot>,
    convBotIds: List<String>,
    isGroup: Boolean,
    canRegenerate: Boolean,
    isSpeaking: Boolean,
    onSpeak: (Message) -> Unit,
    onCopy: (String) -> Unit,
    onFork: (Message) -> Unit,
    onRegenerate: () -> Unit,
    onRunCode: (lang: String, code: String) -> Unit,
    onChatWithBot: (String) -> Unit,
    onAddBotToChat: (String) -> Unit,
    onEditBot: (String) -> Unit,
) {
    val c = Pocket.colors
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val motion = Pocket.motion
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(0f) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.width(AVATAR).graphicsLayer { translationY = bounce.value },
        ) {
            if (showHeader) {
                BotAvatar(
                    bot, AVATAR,
                    modifier = Modifier.pointerInput(msg.id) {
                        detectTapGestures(onDoubleTap = {
                            haptics.play(HapticCue.TICK)
                            PocketApp.from(ctx).sounds.play(SoundCue.SPARK)
                            scope.launch {
                                bounce.snapTo(-14f)
                                bounce.animateTo(0f, motion.bouncy())
                            }
                        })
                    },
                )
            }
        }
        Column(Modifier.widthIn(max = maxBubbleWidth() - AVATAR)) {
            if (showHeader && showName) {
                Text(
                    bot?.name ?: stringResource(R.string.ui_chat_deleted_bot),
                    color = lerp(botColor(bot), c.text, 0.35f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            Bubble(
                c.botBubble, if (isSpeaking) c.accent else c.outline, own = false,
                modifier = Modifier.testTag(if (msg.streaming) "chat.msg.bot.live" else "chat.msg.bot").longPressAction(label, onLongPress),
            ) {
                Column {
                    if (msg.reasoning.isNotBlank()) ReasoningBlock(msg)
                    when {
                        msg.text.isBlank() && msg.streaming && msg.reasoning.isBlank() -> TypingIndicator()
                        msg.text.isBlank() && msg.streaming -> Unit
                        msg.text.isBlank() -> Text(
                            stringResource(R.string.ui_chat_empty_reply), color = c.textMuted, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                        )
                        else -> StreamingMarkdown(
                            text = msg.text, streaming = msg.streaming, createdAtMs = msg.createdAt,
                            color = c.onBotBubble, onRunCode = onRunCode,
                        )
                    }
                    if (msg.streaming && msg.text.isNotBlank()) BlinkingCaret()
                }
            }
            msg.forgedBotIds.forEach { forgedId ->
                val forgedBot = botsById[forgedId]
                if (forgedBot != null) {
                    ForgedBotCard(
                        bot = forgedBot,
                        inCurrentChat = forgedBot.id in convBotIds,
                        isGroupChat = isGroup,
                        onChatNow = { onChatWithBot(forgedBot.id) },
                        onAddToChat = { onAddBotToChat(forgedBot.id) },
                        onEdit = { onEditBot(forgedBot.id) },
                    )
                }
            }
            val showMeta = showTime || msg.tokensIn > 0L || msg.tokensOut > 0L || msg.starred
            if (!msg.streaming && msg.text.isNotBlank()) {
                BotQuickActions(
                    msg = msg,
                    showTime = showMeta,
                    isSpeaking = isSpeaking,
                    canRegenerate = canRegenerate,
                    onSpeak = { onSpeak(msg) },
                    onCopy = { onCopy(msg.text) },
                    onFork = { onFork(msg) },
                    onRegenerate = onRegenerate,
                )
            } else if (showMeta && !msg.streaming) {
                Meta(msg)
            }
        }
    }
}

/** Sleek inline micro-action strip beneath completed bot replies: Speak (Supertonic 3), Copy, Fork, Regenerate. */
@Composable
private fun BotQuickActions(
    msg: Message,
    showTime: Boolean,
    isSpeaking: Boolean,
    canRegenerate: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val c = Pocket.colors
    var copied by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1400); copied = false } }
    Row(
        Modifier.padding(top = 2.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        InlineActionIcon(
            icon = if (isSpeaking) PixelIcon.STOP else PixelIcon.VOLUME,
            tint = if (isSpeaking) c.accent else c.textMuted,
            cd = stringResource(if (isSpeaking) R.string.ui_act_stop_speak else R.string.ui_act_speak),
            onClick = onSpeak,
        )
        InlineActionIcon(
            icon = if (copied) PixelIcon.CHECK else PixelIcon.COPY,
            tint = if (copied) c.success else c.textMuted,
            cd = stringResource(R.string.ui_act_copy),
            onClick = { onCopy(); copied = true },
        )
        InlineActionIcon(
            icon = PixelIcon.FORK,
            tint = c.textMuted,
            cd = stringResource(R.string.ui_act_fork),
            onClick = onFork,
        )
        if (canRegenerate) {
            InlineActionIcon(
                icon = PixelIcon.REFRESH,
                tint = c.textMuted,
                cd = stringResource(R.string.ui_act_regen),
                onClick = onRegenerate,
            )
        }
        if (showTime) {
            Spacer(Modifier.width(4.dp))
            Meta(msg, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun InlineActionIcon(icon: PixelIcon, tint: Color, cd: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = cd }
            .clickable(role = SemRole.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelIconView(icon, tint = tint, size = 15.dp)
    }
}

@Composable
private fun ReasoningBlock(msg: Message) {
    val c = Pocket.colors
    var open by rememberSaveable(msg.id) { mutableStateOf(false) }
    val live = msg.streaming && msg.text.isBlank()
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            Modifier.testTag("chat.thought").clickable(role = SemRole.Button) { open = !open }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) GeneratingEmber(size = 14.dp) else PixelIconView(PixelIcon.SPARK, tint = c.textMuted, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (live) R.string.ui_chat_thinking else R.string.ui_chat_thought),
                color = c.textMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.width(4.dp))
            PixelIconView(if (open) PixelIcon.UP else PixelIcon.DOWN, tint = c.textMuted, size = 12.dp)
        }
        AnimatedVisibility(open) {
            Text(
                msg.reasoning.take(8000), color = c.textMuted, fontSize = 13.sp, fontStyle = FontStyle.Italic, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

/** The blinking ember after the last streamed character. */
@Composable
private fun BlinkingCaret() {
    val c = Pocket.colors
    val anim = Pocket.anim
    val motion = Pocket.motion
    val t = rememberInfiniteTransition(label = "caret")
    val a = t.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = motion.loop(Loop.CARET, RepeatMode.Reverse, LinearEasing), label = "caretAlpha",
    )
    Box(
        Modifier.padding(top = 4.dp).size(width = 8.dp, height = 14.dp)
            .graphicsLayer { alpha = if (anim == AnimLevel.OFF) 1f else a.value }
            .background(c.ember),
    )
}

// ---- tool --------------------------------------------------------------------------------------------------------

@Composable
private fun ToolCard(
    msg: Message, label: String, onLongPress: () -> Unit, toolIndex: Int, onOpenMemory: () -> Unit, onOpenOutput: () -> Unit,
) {
    val c = Pocket.colors
    val running = isToolRunning(msg)
    val failed = isToolFailed(msg)
    val isMemory = msg.toolName?.contains("memory", ignoreCase = true) == true
    val preview = msg.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
    val openLabel = stringResource(R.string.stm_tool_card_open)
    Row(Modifier.fillMaxWidth().padding(start = AVATAR + 8.dp)) {
        PixelPanel(
            Modifier.widthIn(max = maxBubbleWidth()).testTag("chat.msg.tool").longPressAction(label, onLongPress),
            color = c.toolBubble, border = if (isMemory && !running && !failed) c.accent else c.outline, corner = 1,
        ) {
            // The card shows the first line; the whole output opens in a sheet (the old inline scroll box is gone).
            Column(
                Modifier.testTag("chat.tool.$toolIndex")
                    .clickable(onClickLabel = openLabel, role = SemRole.Button, onClick = onOpenOutput)
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIconView(if (isMemory) PixelIcon.MEMORY else PixelIcon.TOOL, tint = if (isMemory) c.accent else c.onToolBubble, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isMemory && !running && !failed) stringResource(R.string.ui_chat_memory_updated)
                        else msg.toolName ?: stringResource(R.string.ui_chat_tool_cd),
                        color = c.onToolBubble, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (running) {
                        GeneratingEmber(size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_chat_tool_running), color = c.onToolBubble.copy(alpha = 0.7f), fontSize = 11.sp)
                    } else if (failed) {
                        Text(stringResource(R.string.ui_chat_tool_stopped), color = c.error, fontSize = 11.sp)
                    } else {
                        PixelIconView(PixelIcon.CHECK, tint = c.success, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_chat_tool_done), color = c.onToolBubble.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    PixelIconView(PixelIcon.MORE, tint = c.onToolBubble.copy(alpha = 0.7f), size = 12.dp)
                }
                if (preview.isNotEmpty()) {
                    Text(
                        preview, color = c.onToolBubble.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (isMemory && !running && !failed) {
                    Spacer(Modifier.height(6.dp))
                    PocketChip(
                        stringResource(R.string.ui_chat_memory_open), false, onOpenMemory,
                        modifier = Modifier.testTag("chat.memory.open"), icon = PixelIcon.MEMORY,
                    )
                }
            }
        }
    }
}

// ---- error --------------------------------------------------------------------------------------------------------

@Composable
private fun ErrorCard(
    msg: Message, canRetry: Boolean, label: String,
    onLongPress: () -> Unit, onRetry: () -> Unit, onCopy: (String) -> Unit,
) {
    val c = Pocket.colors
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().padding(start = AVATAR + 8.dp).testTag("chat.msg.error").clip(shape)
            .background(c.error.copy(alpha = 0.12f)).border(1.dp, c.error.copy(alpha = 0.6f), shape)
            .longPressAction(label, onLongPress).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PixelIconView(PixelIcon.INFO, tint = c.error, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                msg.text, color = c.text, fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRetry) PocketChip(stringResource(R.string.ui_chat_retry), false, onRetry, icon = PixelIcon.REFRESH)
            PocketChip(stringResource(R.string.ui_act_copy), false, { onCopy(msg.text) }, icon = PixelIcon.COPY)
        }
    }
}

// ---- waiting for the first token ----------------------------------------------------------------------------

/** Shown while a bot has been asked but nothing has arrived yet. */
@Composable
internal fun PendingRow(bot: Bot?, showName: Boolean, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    Row(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotAvatar(bot, AVATAR, animated = true)
        Column {
            if (showName && bot != null) {
                Text(
                    bot.name, color = lerp(botColor(bot), c.text, 0.35f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            Bubble(c.botBubble, c.outline, own = false) { TypingIndicator() }
        }
    }
}
