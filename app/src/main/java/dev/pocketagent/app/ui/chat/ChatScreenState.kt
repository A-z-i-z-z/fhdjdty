package dev.pocketagent.app.ui.chat

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Navigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PetNotes
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.HapticPlayer
import dev.pocketagent.app.ui.theme.rememberHaptics
import dev.pocketagent.app.voice.SoundCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import dev.pocketagent.app.ui.text.Texts

// The state of one open chat (Wave 0, STREAM). Everything the old ChatBody kept in local variables lives here, so the four
// composables of the screen (top area, message list, input area, dialogs) can each read only what they draw and a
// keystroke in the input no longer recomposes the message list.

// ---- small pure shapes (unit tested) -----------------------------------------------------------------------------------

/** What the screen needs to know about the message list without watching every message. */
@Immutable
internal data class ChatShape(
    val count: Int,
    val lastId: String?,
    val lastBotId: String?,
    val lastBotIsLast: Boolean,
    val lastStreaming: Boolean,
)

internal fun chatShapeOf(msgs: List<Message>): ChatShape = ChatShape(
    count = msgs.size,
    lastId = msgs.lastOrNull()?.id,
    lastBotId = msgs.lastOrNull { it.role == Role.BOT }?.id,
    lastBotIsLast = msgs.lastOrNull { it.role != Role.NOTE }?.role == Role.BOT,
    lastStreaming = msgs.lastOrNull()?.streaming == true,
)

/** Find in chat: the matches (oldest first), the current one, and which message gets the outline. */
@Immutable
internal data class SearchView(
    val active: Boolean,
    val query: String,
    val matchIds: List<String>,
    val position: Int,
    val currentId: String?,
    val outlineId: String?,
)

internal fun searchViewOf(active: Boolean, query: String, matches: List<String>, pos: Int, flashId: String?): SearchView {
    val position = if (matches.isEmpty()) -1 else pos.coerceIn(0, matches.lastIndex)
    val currentId = if (active) matches.getOrNull(position) else null
    return SearchView(active, query, matches, position, currentId, if (active) currentId else flashId)
}

/** Where a message sits inside a run of messages of the same speaker. */
@Immutable
internal data class RowGrouping(val showHeader: Boolean, val isLastInRun: Boolean, val showDay: Boolean)

internal fun groupingOf(
    older: Message?,
    m: Message,
    newer: Message?,
    sameDay: (Long, Long) -> Boolean = ::sameCalendarDay,
): RowGrouping = RowGrouping(
    showHeader = older == null || older.role != m.role || older.botId != m.botId,
    isLastInRun = newer == null || newer.role != m.role || newer.botId != m.botId,
    showDay = older == null || !sameDay(older.createdAt, m.createdAt),
)

/** Everything about how one row is drawn that is not the message itself. A value type: an equal layout lets the row skip. */
@Immutable
internal data class RowLayout(
    val grouping: RowGrouping,
    val showName: Boolean,
    val isGroup: Boolean,
    val canRetry: Boolean,
    val canRegenerate: Boolean,
    val isSpeaking: Boolean,
    val highlighted: Boolean,
    /** Position among the tool cards, newest first (0 = newest); -1 on any other row. Only used for the robot's tag. */
    val toolIndex: Int = -1,
)

/**
 * What a row can do. Built once per chat screen; every lambda reads the current values from the state when it runs, so the
 * instance never changes and rows that did not change are skipped.
 */
@Stable
internal class ChatRowActions(
    val onLongPress: (Message) -> Unit,
    val onRetry: () -> Unit,
    val onCopy: (String) -> Unit,
    val onSpeak: (Message) -> Unit,
    val onFork: (Message) -> Unit,
    val onRunCode: (lang: String, code: String) -> Unit,
    val onOpenImage: (Attachment) -> Unit,
    val onOpenMemory: () -> Unit,
    val onChatWithBot: (String) -> Unit,
    val onAddBotToChat: (String) -> Unit,
    val onEditBot: (String) -> Unit,
    val onOpenToolOutput: (Message) -> Unit,
)

// ---- the state holder ----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@Stable
internal class ChatScreenState(
    val convId: String,
    val app: PocketApp,
    val scope: CoroutineScope,
    val listState: LazyListState,
    initialInput: String,
) : RememberObserver {
    val repo: ChatRepository get() = app.chat
    val engine: ChatEngine get() = app.engine

    /** The messages of this chat. Declared first (init order): [shape] and [matches] are derived from it. */
    val messagesFlow: StateFlow<List<Message>> = app.chat.messages(convId)

    /** For event handlers: reads the messages now, never makes a composable observe them. */
    fun messagesNow(): List<Message> = messagesFlow.value

    // Refreshed by rememberChatScreenState on every composition (language changes, a renamed conversation).
    lateinit var context: Context
    lateinit var haptics: HapticPlayer
    lateinit var conversation: Conversation
    lateinit var nav: Navigator

    // ---- input ----
    var input: String by mutableStateOf(initialInput)
    var editing: Message? by mutableStateOf(null)

    /** The draft that was in the input when "Edit" replaced it with the old message; restored when the edit ends. */
    var preEditDraft: String by mutableStateOf("")
    var pendingAttachments: List<Attachment> by mutableStateOf(emptyList())
    var pendingCameraFile: File? by mutableStateOf(null)
    var spark: Int by mutableIntStateOf(0)
    var celebrateTrigger: Int by mutableIntStateOf(0)

    // ---- voice input ----
    var listening: Boolean by mutableStateOf(false)
    var voiceBaseText: String by mutableStateOf("")
    var activeRecognizer: SpeechRecognizer? by mutableStateOf(null)

    // ---- dialogs ----
    var menu: Boolean by mutableStateOf(false)
    var showControls: Boolean by mutableStateOf(false)
    var showAttach: Boolean by mutableStateOf(false)
    var rename: Boolean by mutableStateOf(false)
    var confirmDelete: Boolean by mutableStateOf(false)
    var confirmClear: Boolean by mutableStateOf(false)
    var showMembers: Boolean by mutableStateOf(false)
    var exportMenu: Boolean by mutableStateOf(false)
    var skillPickerQuery: String? by mutableStateOf(null)
    var pendingTerminalCmd: String? by mutableStateOf(null)
    var previewImage: Attachment? by mutableStateOf(null)
    var actionsFor: Message? by mutableStateOf(null)
    var selectFor: Message? by mutableStateOf(null)

    /** Id of the tool message whose full output is open in the sheet. */
    var toolOutputFor: String? by mutableStateOf(null)

    // ---- find in chat ----
    var searching: Boolean by mutableStateOf(false)
    var searchQuery: String by mutableStateOf("")
    var searchPos: Int by mutableIntStateOf(0)
    var flashId: String? by mutableStateOf(null)

    // ---- list ----
    /** Whether the list follows the newest message (true until the user scrolls away from the bottom). */
    var stick: Boolean by mutableStateOf(true)

    // ---- derived, collected off the message flow so no composable has to watch every message ----
    private val shapeState = mutableStateOf(chatShapeOf(messagesFlow.value))
    val shape: State<ChatShape> get() = shapeState
    private val matchesState = mutableStateOf<List<String>>(emptyList())
    val matches: State<List<String>> get() = matchesState

    private var collectors: List<Job> = emptyList()

    override fun onRemembered() {
        if (collectors.isNotEmpty()) return
        collectors = listOf(
            scope.launch {
                messagesFlow.map(::chatShapeOf).distinctUntilChanged().collect { shapeState.value = it }
            },
            scope.launch {
                snapshotFlow { searching to searchQuery }
                    .flatMapLatest { (on, q) ->
                        if (!on) flowOf<List<String>>(emptyList())
                        else messagesFlow.map { findMatchingMessageIds(it, q) }
                    }
                    .distinctUntilChanged()
                    .collect { matchesState.value = it }
            },
        )
    }

    override fun onForgotten() = stopCollecting()

    override fun onAbandoned() = stopCollecting()

    private fun stopCollecting() {
        collectors.forEach { it.cancel() }
        collectors = emptyList()
    }

    // ---- bots of this chat, read when an event handler runs ----

    fun botsNow(): Map<String, Bot> = repo.bots.value.associateBy { it.id }

    fun memberBotsNow(): List<Bot> {
        val byId = botsNow()
        return conversation.botIds.mapNotNull { byId[it] }
    }

    val isGroup: Boolean get() = conversation.isGroup

    // ---- small actions ----

    fun openSearch(q: String) {
        searching = true
        searchQuery = q
        searchPos = Int.MAX_VALUE
    }

    fun closeSearch() {
        searching = false
        searchQuery = ""
    }

    /** Stops and releases the speech recognizer, if one is running. */
    fun cancelVoice() {
        runCatching { activeRecognizer?.cancel() }
        runCatching { activeRecognizer?.destroy() }
        activeRecognizer = null
        listening = false
    }

    fun scrollToNewest() {
        stick = true
        scope.launch { listState.animateScrollToItem(0) }
    }

    /** The one place this screen tells the user something short (a toast before; a note from Pet now). */
    fun say(text: String, kind: PetNoteKind = PetNoteKind.INFO) {
        PetNotes.show(text, kind)
    }

    /** The engine refuses a message it cannot deliver (Hermes asleep and no wake-on-send, or nothing installed): never silently. */
    fun toastNotSent() {
        say(Texts.get(context, R.string.ui_send_not_accepted), PetNoteKind.ERROR)
    }

    fun doSend() {
        if (listening || activeRecognizer != null) cancelVoice()
        val t = input.trim()
        val ed = editing
        if (ed != null) {
            if (t.isNotEmpty()) {
                haptics.play(HapticCue.TICK)
                val ok = engine.editAndResend(convId, ed.id, t).accepted()
                if (ok) {
                    // Back to the draft that was being typed before the edit began (the saver writes it again).
                    input = preEditDraft; preEditDraft = ""
                    editing = null; spark++; stick = true
                    scope.launch { listState.animateScrollToItem(0) }
                } else {
                    toastNotSent()
                }
            }
        } else {
            val slash = SlashCommands.parse(t)
            if (slash != null) {
                haptics.play(HapticCue.TICK)
                input = ""
                executeSlash(slash)
                scope.launch { listState.animateScrollToItem(0) }
            } else if (t.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                haptics.play(HapticCue.TICK)
                val mentionTargets = if (isGroup) mentionedBotIds(t, memberBotsNow()) else emptyList()
                val ok = if (conversation.mode == ChatMode.LOOP) {
                    engine.runLoop(convId, t, 3, pendingAttachments, mentionTargets).accepted()
                } else {
                    engine.send(convId, t, mentionTargets, pendingAttachments).accepted()
                }
                if (ok) {
                    app.sounds.play(SoundCue.SEND)
                    input = ""; pendingAttachments = emptyList(); spark++; stick = true
                    scope.launch { listState.animateScrollToItem(0) }
                } else {
                    toastNotSent()
                }
            }
        }
    }

    fun doFork(upToId: String?) {
        scope.launch {
            val suffix = Texts.get(context, R.string.ui_fork_suffix)
            val forked = withContext(Dispatchers.IO) { repo.forkConversation(convId, upToId, suffix) }
            if (forked != null) {
                app.sounds.play(SoundCue.TAP)
                say(Texts.get(context, R.string.ui_fork_created), PetNoteKind.SUCCESS)
                nav.go(Route.Chat(forked.id))
            }
        }
    }

    fun attachImport(loader: suspend () -> Attachment) {
        scope.launch {
            try {
                val att = withContext(Dispatchers.IO) { loader() }
                pendingAttachments = pendingAttachments + att
            } catch (e: Exception) {
                say(Texts.get(context, R.string.ui_attach_failed, e.message ?: e.javaClass.simpleName), PetNoteKind.ERROR)
            }
        }
    }
}

/**
 * The state of the chat [conv], kept while the screen is open, plus the effects that belong to the whole screen: the draft
 * saver (a moment after the last keystroke, never while editing a sent message), text and files shared into this chat from
 * other apps, and releasing the speech recognizer.
 */
@Composable
internal fun rememberChatScreenState(conv: Conversation): ChatScreenState {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()
    val nav = LocalNavigator.current
    val state = remember(conv.id) {
        ChatScreenState(conv.id, app, scope, listState, app.drafts[conv.id]).also {
            it.context = ctx
            it.haptics = haptics
            it.conversation = conv
            it.nav = nav
        }
    }
    SideEffect {
        state.context = ctx
        state.haptics = haptics
        state.conversation = conv
        state.nav = nav
    }

    LaunchedEffect(state) {
        snapshotFlow { if (state.editing == null) state.input else null }.collectLatest { text ->
            if (text != null) {
                delay(300)
                app.drafts[state.convId] = text
            }
        }
    }
    DisposableEffect(state) {
        onDispose {
            if (state.editing == null) app.drafts[state.convId] = state.input
        }
    }
    LaunchedEffect(state) {
        ChatShareInbox.pending.map { it[state.convId] }.distinctUntilChanged().collect {
            val items = ChatShareInbox.take(state.convId)
            if (items.isNotEmpty()) {
                val sharedTexts = items.mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
                if (sharedTexts.isNotEmpty()) {
                    val joined = sharedTexts.joinToString("\n")
                    state.input = if (state.input.isBlank()) joined else "${state.input.trimEnd()}\n$joined"
                }
                val sharedAtts = items.mapNotNull { it.attachment }
                if (sharedAtts.isNotEmpty()) {
                    state.pendingAttachments = state.pendingAttachments + sharedAtts
                }
            }
        }
    }
    DisposableEffect(state) {
        onDispose {
            runCatching { state.activeRecognizer?.destroy() }
            state.activeRecognizer = null
        }
    }
    return state
}
