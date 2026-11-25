package dev.pocketagent.app.ui.chat

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.voice.voiceOverride
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.pocketagent.app.data.Role as MsgRole
import dev.pocketagent.app.ui.text.t

/** The default of Modifier.animateItem's placement: today's behaviour for rows that move because something was inserted. */
private val DefaultPlacement: FiniteAnimationSpec<IntOffset> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/**
 * The bottom-anchored message list: stick-to-newest, scrolling to a found message or a deep link, the jump button, the empty
 * state. It is the only part of the chat screen that watches the messages; typing in the input does not touch it.
 */
@Composable
internal fun ChatMessageList(state: ChatScreenState, conv: Conversation, search: SearchView, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val repo = state.repo
    val engine = state.engine
    val convId = conv.id
    val msgs by state.messagesFlow.collectAsState()
    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val speaking by engine.speaking.collectAsState()
    val speakingMsgId by state.app.voice.speakingId.collectAsState()
    val scrollTarget by ChatScrollTarget.pending.collectAsState()
    val anim = Pocket.anim
    val listState = state.listState

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val memberBots = conv.botIds.mapNotNull { botsById[it] }
    val isGroup = conv.isGroup
    val generating = convId in busySet
    val speakingBot = speaking[convId]?.let { botsById[it] }
    val shape = chatShapeOf(msgs)
    val showPending = generating && (shape.count == 0 || !shape.lastStreaming)
    val reversed = remember(msgs) { msgs.asReversed() }
    // Position of every TOOL row among the tool rows (0 = newest card), -1 for other rows; one pass per list.
    val toolIdx = remember(reversed) {
        var n = 0
        IntArray(reversed.size) { i -> if (reversed[i].role == MsgRole.TOOL) n++ else -1 }
    }

    val actions = remember(state) { buildRowActions(state) }

    LaunchedEffect(listState) {
        // Remember where the *user* left the list: data changes alone never flip this.
        var wasScrolling = false
        snapshotFlow {
            listState.isScrollInProgress to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2)
        }.collect { (scrolling, atBottom) ->
            if (scrolling || wasScrolling) state.stick = atBottom
            wasScrolling = scrolling
        }
    }
    val newestKey = if (showPending) "pending" else reversed.firstOrNull()?.id
    LaunchedEffect(newestKey) { if (state.stick) listState.animateScrollToItem(0) }

    // Find in chat: scroll to the current match.
    val currentMatchId = search.currentId
    LaunchedEffect(currentMatchId) {
        val id = currentMatchId ?: return@LaunchedEffect
        val idx = reversed.indexOfFirst { it.id == id }
        if (idx >= 0) {
            state.stick = false
            listState.animateScrollToItem(idx + (if (showPending) 1 else 0), -(listState.layoutInfo.viewportSize.height / 3))
        }
    }

    // A deep link to one message (saved messages, a search hit): scroll there once it is loaded, flash its outline for a moment.
    val myTarget = scrollTarget?.takeIf { it.convId == convId }
    val targetReady = myTarget != null && reversed.any { it.id == myTarget.messageId }
    LaunchedEffect(myTarget, targetReady) {
        val t = myTarget ?: return@LaunchedEffect
        if (!targetReady) {
            delay(5000L) // the messages may still be loading; give up after a while
            ChatScrollTarget.consume(t)
            return@LaunchedEffect
        }
        val idx = reversed.indexOfFirst { it.id == t.messageId }
        if (idx >= 0) {
            state.stick = false
            state.flashId = t.messageId
            state.scope.launch {
                delay(2500L)
                if (state.flashId == t.messageId) state.flashId = null
            }
            listState.scrollToItem(idx + (if (showPending) 1 else 0), -(listState.layoutInfo.viewportSize.height / 3))
        }
        ChatScrollTarget.consume(t)
    }

    Box(modifier) {
        if (shape.count == 0 && !generating) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 16.dp).testTag("chat.empty"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    t(R.string.ui_chat_empty_title), t(R.string.ui_chat_empty_body),
                )
                EmptyChatQuickActions { cmd ->
                    if (cmd.endsWith(" ")) state.input = cmd
                    else SlashCommands.parse(cmd)?.let { state.executeSlash(it) }
                }
            }
        }
        LazyColumn(
            state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize().testTag("chat.list"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showPending) {
                item(key = "pending", contentType = "pending") {
                    PendingRow(
                        speakingBot ?: memberBots.firstOrNull(), showName = isGroup,
                        modifier = Modifier.testTag("chat.pending"),
                    )
                }
            }
            itemsIndexed(reversed, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { index, m ->
                val older = reversed.getOrNull(index + 1)
                val newer = reversed.getOrNull(index - 1)
                val layout = RowLayout(
                    grouping = groupingOf(older, m, newer),
                    showName = isGroup,
                    isGroup = isGroup,
                    canRetry = m.id == shape.lastId && !generating,
                    canRegenerate = m.role == MsgRole.BOT && m.id == shape.lastBotId && shape.lastBotIsLast && !generating,
                    isSpeaking = speakingMsgId == m.id,
                    highlighted = m.id == search.outlineId,
                    toolIndex = toolIdx.getOrElse(index) { -1 },
                )
                // A growing row must not start a new placement animation for every row below it on every update.
                val rowModifier = when {
                    anim == AnimLevel.OFF -> Modifier
                    m.streaming -> Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                    else -> Modifier.animateItem(placementSpec = if (shape.lastStreaming) null else DefaultPlacement)
                }
                MessageRow(
                    msg = m,
                    bot = m.botId?.let { botsById[it] } ?: if (m.role == MsgRole.BOT) memberBots.firstOrNull() else null,
                    layout = layout,
                    actions = actions,
                    botsById = botsById,
                    convBotIds = conv.botIds,
                    modifier = rowModifier.testTag("chat.msg.$index"),
                )
            }
        }
        NewrozCelebrationOverlay(state.celebrateTrigger)
        val jumpLabel = t(R.string.ui_chat_jump)
        androidx.compose.animation.AnimatedVisibility(
            visible = !state.stick, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(),
        ) {
            Box(
                Modifier.size(44.dp).background(c.surfaceHigh).border(2.dp, c.outline)
                    .testTag("chat.jump")
                    .semantics { contentDescription = jumpLabel }
                    .clickable(role = Role.Button) { state.scrollToNewest() },
                contentAlignment = Alignment.Center,
            ) { PixelIconView(PixelIcon.DOWN, tint = c.text, size = 22.dp) }
        }
    }
}

/**
 * The actions of a row. Each lambda looks at the state when it runs, so the instance is built once per chat screen and never
 * changes: that is what lets a row whose message did not change skip recomposition.
 */
private fun buildRowActions(state: ChatScreenState): ChatRowActions = ChatRowActions(
    onLongPress = { state.actionsFor = it },
    onRetry = { state.engine.regenerateLast(state.convId) },
    onCopy = {
        copyToClipboard(state.context, it)
        state.app.sounds.play(SoundCue.TAP)
    },
    onSpeak = { target ->
        val b = target.botId?.let { state.botsNow()[it] } ?: state.memberBotsNow().firstOrNull()
        val language = AppearanceStore.get(state.context).appearance.value.language
        state.app.voice.toggleSpeak(target.id, target.text, language, b.voiceOverride())
    },
    onFork = { target -> state.doFork(target.id) },
    onRunCode = { lang, code -> state.pendingTerminalCmd = SyntaxHighlight.toTerminalCommand(lang, code) },
    onOpenImage = { state.previewImage = it },
    onOpenMemory = { state.nav.go(Route.Hermes) },
    onChatWithBot = { botId ->
        val next = state.repo.openOneToOne(botId)
        state.nav.go(Route.Chat(next.id))
    },
    onAddBotToChat = { botId ->
        state.repo.setConversationBots(state.convId, state.conversation.botIds + botId)
    },
    onEditBot = { botId -> state.nav.go(Route.BotEditor(botId)) },
    onOpenToolOutput = { state.toolOutputFor = it.id },
)
