package dev.pocketagent.app.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.hermes.MemoryFormat
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.ThemeId
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.voice.voiceOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import dev.pocketagent.app.data.Role as MsgRole

/**
 * One conversation: message list (bottom-anchored, sticks to the newest message only while you are at the
 * bottom), input bar, menus. Resolves the conversation and hands over to [ChatBody].
 */
@Composable
fun ChatScreen(convId: String) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val repo = remember { PocketApp.from(ctx).chat }
    val convs by repo.conversations.collectAsState()
    val loaded by repo.loaded.collectAsState()
    val conv = convs.firstOrNull { it.id == convId }
    val convNow by rememberUpdatedState(conv)
    var everSeen by remember(convId) { mutableStateOf(false) }

    // A deleted conversation closes the screen. A brand new one may need a moment to show up in the list.
    LaunchedEffect(convId, conv != null, loaded) {
        if (conv != null) everSeen = true
        else if (loaded) {
            delay(if (everSeen) 0L else 1500L)
            if (convNow == null && !nav.back()) nav.tab(Route.Chats)
        }
    }

    if (conv == null) {
        Column(Modifier.fillMaxSize()) {
            PocketTopBar(title = "", onBack = { if (!nav.back()) nav.tab(Route.Chats) })
            if (loaded) {
                Text(
                    stringResource(R.string.ui_chat_not_found), color = Pocket.colors.textMuted, fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    } else {
        ChatBody(conv)
    }
}

@Composable
private fun ChatBody(conv: Conversation) {
    val convId = conv.id
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val engine = app.engine
    val vm: MainViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val c = Pocket.colors

    val runtime by vm.state.collectAsState()
    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val speaking by engine.speaking.collectAsState()
    val statusMap by engine.status.collectAsState()
    val approvals by engine.approvals.collectAsState()
    val speakingMsgId by app.voice.speakingId.collectAsState()
    val ap by AppearanceStore.get(ctx).appearance.collectAsState()
    val msgsFlow = remember(convId) { repo.messages(convId) }
    val msgs by msgsFlow.collectAsState()

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val members = conv.botIds.map { botsById[it] }
    val memberBots = members.filterNotNull()
    val isGroup = conv.isGroup
    val generating = convId in busySet
    val speakingBot = speaking[convId]?.let { botsById[it] }
    val title = conversationTitle(conv, botsById)
    val subtitle = when {
        generating && !statusMap[convId].isNullOrBlank() -> statusMap[convId].orEmpty()
        generating && speakingBot != null -> stringResource(R.string.ui_chat_typing_who, speakingBot.name)
        generating -> stringResource(R.string.ui_chat_typing)
        isGroup -> memberBots.joinToString(", ") { it.name }
        else -> memberBots.firstOrNull()?.tagline.orEmpty()
    }.takeIf { it.isNotBlank() }

    // ---- input state ----
    var input by remember(convId) { mutableStateOf(ChatDrafts[convId]) }
    DisposableEffect(convId) { onDispose { ChatDrafts[convId] = input } }
    var editing by remember(convId) { mutableStateOf<Message?>(null) }
    var spark by remember { mutableIntStateOf(0) }
    var pendingAttachments by remember(convId) { mutableStateOf<List<Attachment>>(emptyList()) }
    val shareInbox by ChatShareInbox.pending.collectAsState()
    LaunchedEffect(convId, shareInbox[convId]) {
        val items = ChatShareInbox.take(convId)
        if (items.isNotEmpty()) {
            val sharedTexts = items.mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
            if (sharedTexts.isNotEmpty()) {
                val joined = sharedTexts.joinToString("\n")
                input = if (input.isBlank()) joined else "${input.trimEnd()}\n$joined"
            }
            val sharedAtts = items.mapNotNull { it.attachment }
            if (sharedAtts.isNotEmpty()) {
                pendingAttachments = pendingAttachments + sharedAtts
            }
        }
    }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var celebrateTrigger by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var voiceBaseText by remember { mutableStateOf("") }
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(convId) {
        onDispose {
            runCatching { activeRecognizer?.destroy() }
            activeRecognizer = null
        }
    }

    // ---- dialogs ----
    var menu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var skillPickerQuery by remember { mutableStateOf<String?>(null) }
    var pendingTerminalCmd by remember { mutableStateOf<String?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Attachment?>(null) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var selectFor by remember { mutableStateOf<Message?>(null) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }

    // ---- voice input (STT: inline SpeechRecognizer + RecognizerIntent fallback) ----
    val sttLocaleTag = remember(ap.language) {
        when (ap.language.lowercase().take(2)) {
            "tr" -> "tr-TR"
            "fr" -> "fr-FR"
            "en" -> "en-US"
            else -> java.util.Locale.getDefault().toLanguageTag()
        }
    }
    val sttIntentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        listening = false
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                input = if (input.isBlank()) spoken else "${input.trimEnd()} $spoken"
            }
        }
    }
    val launchVoiceDialog: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.ui_voice_prompt))
        }
        try {
            app.sounds.play(SoundCue.MIC)
            listening = true
            sttIntentLauncher.launch(intent)
        } catch (_: Exception) {
            listening = false
            Toast.makeText(ctx, ctx.getString(R.string.ui_voice_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
    val startInlineVoice: () -> Unit = {
        if (listening) {
            runCatching { activeRecognizer?.stopListening() }
            listening = false
        } else if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
            activeRecognizer?.let { runCatching { it.destroy() } }
            activeRecognizer = sr
            voiceBaseText = input.trimEnd()
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    runCatching { sr.destroy() }
                    if (activeRecognizer === sr) activeRecognizer = null
                    if (
                        error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        error != SpeechRecognizer.ERROR_CLIENT
                    ) {
                        launchVoiceDialog()
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        input = if (voiceBaseText.isBlank()) spoken else "$voiceBaseText $spoken"
                    }
                    runCatching { sr.destroy() }
                    if (activeRecognizer === sr) activeRecognizer = null
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (part.isNotEmpty()) {
                        input = if (voiceBaseText.isBlank()) part else "$voiceBaseText $part"
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            app.sounds.play(SoundCue.MIC)
            listening = true
            runCatching { sr.startListening(intent) }.onFailure {
                listening = false
                launchVoiceDialog()
            }
        } else {
            launchVoiceDialog()
        }
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startInlineVoice() else launchVoiceDialog()
    }
    val onMicTap: () -> Unit = {
        if (listening) {
            runCatching { activeRecognizer?.stopListening() }
            listening = false
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInlineVoice()
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val doFork: (String?) -> Unit = { upToId ->
        scope.launch {
            val suffix = ctx.getString(R.string.ui_fork_suffix)
            val forked = withContext(Dispatchers.IO) { repo.forkConversation(convId, upToId, suffix) }
            if (forked != null) {
                app.sounds.play(SoundCue.TAP)
                Toast.makeText(ctx, ctx.getString(R.string.ui_fork_created), Toast.LENGTH_SHORT).show()
                nav.go(Route.Chat(forked.id))
            }
        }
    }

    // ---- attachment pickers ----
    val attachImport: (suspend () -> Attachment) -> Unit = { loader ->
        scope.launch {
            try {
                val att = withContext(Dispatchers.IO) { loader() }
                pendingAttachments = pendingAttachments + att
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.ui_attach_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (ok && file != null && file.isFile) {
            attachImport {
                try { app.attachments.importImage(Uri.fromFile(file), file.name) }
                finally { file.delete() }
            }
        } else {
            file?.delete()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachImport { app.attachments.importImage(uri) }
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachImport { app.attachments.importDocument(uri) }
    }

    // ---- list ----
    val reversed = remember(msgs) { msgs.asReversed() }
    val listState = rememberLazyListState()
    val showPending = generating && (msgs.isEmpty() || !msgs.last().streaming)
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        // Remember where the *user* left the list: data changes alone never flip this.
        var wasScrolling = false
        snapshotFlow {
            listState.isScrollInProgress to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2)
        }.collect { (scrolling, atBottom) ->
            if (scrolling || wasScrolling) stick = atBottom
            wasScrolling = scrolling
        }
    }
    val newestKey = if (showPending) "pending" else reversed.firstOrNull()?.id
    LaunchedEffect(newestKey) { if (stick) listState.animateScrollToItem(0) }

    val lastBotId = msgs.lastOrNull { it.role == MsgRole.BOT }?.id
    val lastBotIsLast = msgs.lastOrNull { it.role != MsgRole.NOTE }?.role == MsgRole.BOT
    val lastId = msgs.lastOrNull()?.id

    val executeSlash: (SlashCommands.ParsedSlash) -> Unit = { parsed ->
        val arg = parsed.arg
        val mentionTargets = if (isGroup) mentionedBotIds(arg, memberBots) else emptyList()
        when (parsed.name) {
            "plan", "goal", "ultracode" -> {
                val targetMode = when (parsed.name) {
                    "plan" -> ChatMode.PLAN
                    "goal" -> ChatMode.GOAL
                    else -> ChatMode.ULTRACODE
                }
                repo.setMode(convId, targetMode)
                if (arg.isNotBlank() || pendingAttachments.isNotEmpty()) {
                    val sent = engine.send(convId, arg, mentionTargets, pendingAttachments).accepted()
                    if (sent) { pendingAttachments = emptyList(); spark++; stick = true }
                } else {
                    val modeLabel = ctx.getString(
                        when (targetMode) {
                            ChatMode.PLAN -> R.string.ui_mode_banner_plan
                            ChatMode.GOAL -> R.string.ui_mode_banner_goal
                            else -> R.string.ui_mode_banner_ultracode
                        },
                    )
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_mode_on_note, modeLabel))
                }
            }
            "loop" -> {
                repo.setMode(convId, ChatMode.LOOP)
                val (passes, body) = SlashCommands.parseLoopArgs(arg)
                if (body.isNotBlank() || pendingAttachments.isNotEmpty()) {
                    val sent = engine.runLoop(convId, body, passes, pendingAttachments, mentionTargets).accepted()
                    if (sent) { pendingAttachments = emptyList(); spark++; stick = true }
                } else {
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_mode_on_note, ctx.getString(R.string.ui_mode_banner_loop)))
                }
            }
            "normal" -> {
                repo.setMode(convId, ChatMode.NORMAL)
                engine.addLocalNote(convId, ctx.getString(R.string.ui_mode_off_note))
            }
            "forge" -> {
                if (arg.isBlank()) {
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_forge_usage))
                } else {
                    val sent = engine.send(convId, "/forge $arg", mentionTargets, pendingAttachments).accepted()
                    if (sent) { pendingAttachments = emptyList(); spark++; stick = true }
                }
            }
            "skills" -> {
                skillPickerQuery = arg
            }
            "remember" -> {
                if (arg.isBlank()) {
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_remember_usage))
                } else {
                    scope.launch {
                        val cleaned = arg.replace("§", "").trim()
                        val result = withContext(Dispatchers.IO) {
                            val cur = app.hermesHome.read(HermesDoc.USER).orEmpty()
                            val entries = MemoryFormat.entries(cur) + cleaned
                            val used = MemoryFormat.usedChars(entries)
                            if (used > MemoryFormat.USER_LIMIT) {
                                false to used
                            } else {
                                app.hermesHome.write(HermesDoc.USER, MemoryFormat.join(entries))
                                engine.notifyMemoryUpdated()
                                true to used
                            }
                        }
                        val note = if (result.first) {
                            ctx.getString(R.string.ui_cmd_remember_saved, result.second, MemoryFormat.USER_LIMIT)
                        } else {
                            ctx.getString(R.string.ui_cmd_remember_full, result.second, MemoryFormat.USER_LIMIT)
                        }
                        engine.addLocalNote(convId, note)
                    }
                }
            }
            "memory" -> {
                scope.launch {
                    val note = withContext(Dispatchers.IO) {
                        val memRaw = app.hermesHome.read(HermesDoc.MEMORY).orEmpty()
                        val usrRaw = app.hermesHome.read(HermesDoc.USER).orEmpty()
                        val memEntries = MemoryFormat.entries(memRaw)
                        val usrEntries = MemoryFormat.entries(usrRaw)
                        if (memEntries.isEmpty() && usrEntries.isEmpty()) {
                            ctx.getString(R.string.ui_cmd_memory_empty)
                        } else {
                            buildString {
                                appendLine(ctx.getString(R.string.ui_cmd_memory_header))
                                appendLine("MEMORY.md (${MemoryFormat.usedChars(memEntries)}/${MemoryFormat.MEMORY_LIMIT}):")
                                memEntries.takeLast(5).forEach { appendLine("  • ${it.take(120)}") }
                                appendLine("USER.md (${MemoryFormat.usedChars(usrEntries)}/${MemoryFormat.USER_LIMIT}):")
                                usrEntries.takeLast(5).forEach { appendLine("  • ${it.take(120)}") }
                            }.trim()
                        }
                    }
                    engine.addLocalNote(convId, note)
                }
            }
            "theme" -> {
                val pick = ThemeId.entries.firstOrNull { it.name.equals(arg.trim(), ignoreCase = true) }
                if (pick == null) {
                    val names = ThemeId.entries.joinToString(", ") { it.name.lowercase() }
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_theme_usage, names))
                } else {
                    AppearanceStore.get(ctx).update { it.copy(theme = pick) }
                    engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_theme_switched, pick.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }))
                }
            }
            "export" -> {
                val md = buildString {
                    appendLine("# $title")
                    appendLine()
                    msgs.forEach { m ->
                        when (m.role) {
                            MsgRole.USER -> appendLine("**You:** ${m.text}")
                            MsgRole.BOT -> appendLine("**${botsById[m.botId]?.name ?: "Bot"}:** ${m.text}")
                            MsgRole.TOOL -> appendLine("> Tool `${m.toolName ?: "tool"}`: ${m.text}")
                            MsgRole.NOTE -> appendLine("_${m.text}_")
                            MsgRole.ERROR -> appendLine("**Error:** ${m.text}")
                        }
                        appendLine()
                    }
                }.trim()
                shareText(ctx, md)
            }
            "clear" -> confirmClear = true
            "help" -> {
                val note = buildString {
                    appendLine(ctx.getString(R.string.ui_cmd_help_header))
                    SlashCommands.visible.forEach { def -> appendLine("• ${def.usage}") }
                }.trim()
                engine.addLocalNote(convId, note)
            }
            "pet" -> {
                val idx = kotlin.random.Random.nextInt(3)
                val note = when (idx) {
                    0 -> ctx.getString(R.string.ui_cmd_pet_quip1, bots.size)
                    1 -> ctx.getString(R.string.ui_cmd_pet_quip2)
                    else -> ctx.getString(R.string.ui_cmd_pet_quip3, msgs.size)
                }
                engine.addLocalNote(convId, note)
            }
            "newroz" -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                app.sounds.play(SoundCue.FORGE)
                celebrateTrigger++
                engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_newroz_msg))
            }
            "agir", "fire" -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                app.sounds.play(SoundCue.FORGE)
                celebrateTrigger++
                engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_agir_msg))
            }
            "roll" -> {
                val spec = arg.lowercase()
                val note = if (spec == "coin") {
                    ctx.getString(if (kotlin.random.Random.nextBoolean()) R.string.ui_cmd_roll_coin_heads else R.string.ui_cmd_roll_coin_tails)
                } else {
                    val sides = spec.removePrefix("d").toIntOrNull()?.coerceIn(2, 1000) ?: 20
                    val rolled = kotlin.random.Random.nextInt(1, sides + 1)
                    ctx.getString(R.string.ui_cmd_roll_dice, sides, rolled)
                }
                engine.addLocalNote(convId, note)
            }
        }
    }

    val doSend: () -> Unit = {
        if (listening || activeRecognizer != null) {
            runCatching { activeRecognizer?.cancel() }
            runCatching { activeRecognizer?.destroy() }
            activeRecognizer = null
            listening = false
        }
        val t = input.trim()
        val ed = editing
        if (ed != null) {
            if (t.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val ok = engine.editAndResend(convId, ed.id, t).accepted()
                if (ok) { input = ""; editing = null; spark++; stick = true }
                if (ok) scope.launch { listState.animateScrollToItem(0) }
            }
        } else {
            val slash = SlashCommands.parse(t)
            if (slash != null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                input = ""
                executeSlash(slash)
                scope.launch { listState.animateScrollToItem(0) }
            } else if (t.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val mentionTargets = if (isGroup) mentionedBotIds(t, memberBots) else emptyList()
                val ok = if (conv.mode == ChatMode.LOOP) {
                    engine.runLoop(convId, t, 3, pendingAttachments, mentionTargets).accepted()
                } else {
                    engine.send(convId, t, mentionTargets, pendingAttachments).accepted()
                }
                if (ok) {
                    app.sounds.play(SoundCue.SEND)
                    input = ""; pendingAttachments = emptyList(); spark++; stick = true
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }

    val hasOverrides = conv.mode != ChatMode.NORMAL || conv.modelOverride.isNotBlank() || conv.effortOverride.isNotBlank()
    Column(Modifier.fillMaxSize()) {
        PocketTopBar(
            title = title,
            onBack = { if (!nav.back()) nav.tab(Route.Chats) },
            subtitle = subtitle,
            leading = { AvatarStack(members, 36.dp) },
            actions = {
                if (conv.pinned) PixelIconView(PixelIcon.PIN, tint = c.accent, size = 18.dp)
                PixelIconButton(
                    PixelIcon.SLIDERS,
                    onClick = { showControls = true },
                    contentDescription = stringResource(R.string.ui_ctrl_title),
                    tint = if (hasOverrides) c.accent else c.text,
                )
                PixelIconButton(PixelIcon.MORE, onClick = { menu = true }, contentDescription = stringResource(R.string.ui_chat_more))
            },
        )
        if (isGroup) {
            GroupParticipantBar(
                members = memberBots,
                speakingBotId = speaking[convId],
                groupMode = conv.groupMode,
                onMentionBot = { b ->
                    val prefix = if (input.isBlank() || input.endsWith(" ")) "" else " "
                    input = "$input$prefix@${mentionName(b)} "
                },
                onEditMembers = { showMembers = true },
            )
        }
        ActiveModeBanner(conv.mode) {
            repo.setMode(convId, ChatMode.NORMAL)
            engine.addLocalNote(convId, ctx.getString(R.string.ui_mode_off_note))
        }
        ChatOverridesBanner(
            modelOverride = conv.modelOverride,
            effortOverride = conv.effortOverride,
            onOpenControls = { showControls = true },
            onClear = { repo.setOverrides(convId, "", "") },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isEmpty() && !generating) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        stringResource(R.string.ui_chat_empty_title), stringResource(R.string.ui_chat_empty_body),
                    )
                    EmptyChatQuickActions { cmd ->
                        if (cmd.endsWith(" ")) input = cmd
                        else SlashCommands.parse(cmd)?.let(executeSlash)
                    }
                }
            }
            val animateItems = Pocket.anim != AnimLevel.OFF
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showPending) {
                    item(key = "pending", contentType = "pending") {
                        PendingRow(speakingBot ?: memberBots.firstOrNull(), showName = isGroup)
                    }
                }
                itemsIndexed(reversed, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { index, m ->
                    val older = reversed.getOrNull(index + 1)
                    val newer = reversed.getOrNull(index - 1)
                    MessageRow(
                        msg = m,
                        bot = m.botId?.let { botsById[it] } ?: if (m.role == MsgRole.BOT) memberBots.firstOrNull() else null,
                        showHeader = older == null || older.role != m.role || older.botId != m.botId,
                        showName = isGroup,
                        isLastInRun = newer == null || newer.role != m.role || newer.botId != m.botId,
                        showDay = older == null || !sameCalendarDay(older.createdAt, m.createdAt),
                        canRetry = m.id == lastId && !generating,
                        onLongPress = { actionsFor = it },
                        onRetry = { engine.regenerateLast(convId) },
                        onCopy = {
                            copyToClipboard(ctx, it)
                            app.sounds.play(SoundCue.TAP)
                        },
                        modifier = if (animateItems) Modifier.animateItem() else Modifier,
                        botsById = botsById,
                        convBotIds = conv.botIds,
                        isGroup = isGroup,
                        canRegenerate = m.role == MsgRole.BOT && m.id == lastBotId && lastBotIsLast && !generating,
                        isSpeaking = speakingMsgId == m.id,
                        onSpeak = { target ->
                            val b = target.botId?.let { botsById[it] } ?: memberBots.firstOrNull()
                            app.voice.toggleSpeak(target.id, target.text, ap.language, b.voiceOverride())
                        },
                        onFork = { target -> doFork(target.id) },
                        onRunCode = { lang, code -> pendingTerminalCmd = SyntaxHighlight.toTerminalCommand(lang, code) },
                        onOpenImage = { previewImage = it },
                        onOpenMemory = { nav.go(Route.Hermes) },
                        onChatWithBot = { botId ->
                            val next = repo.openOneToOne(botId)
                            nav.go(Route.Chat(next.id))
                        },
                        onAddBotToChat = { botId ->
                            repo.setConversationBots(convId, conv.botIds + botId)
                        },
                        onEditBot = { botId -> nav.go(Route.BotEditor(botId)) },
                    )
                }
            }
            NewrozCelebrationOverlay(celebrateTrigger)
            val jumpLabel = stringResource(R.string.ui_chat_jump)
            androidx.compose.animation.AnimatedVisibility(
                visible = !stick, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(),
            ) {
                Box(
                    Modifier.size(44.dp).background(c.surfaceHigh).border(2.dp, c.outline)
                        .semantics { contentDescription = jumpLabel }
                        .clickable(role = Role.Button) { stick = true; scope.launch { listState.animateScrollToItem(0) } },
                    contentAlignment = Alignment.Center,
                ) { PixelIconView(PixelIcon.DOWN, tint = c.text, size = 22.dp) }
            }
        }
        val approval = approvals[convId]
        if (approval != null) ApprovalCard(approval, botsById[approval.botId]?.name) { engine.resolveApproval(convId, it) }
        ChatInputBar(
            text = input, onTextChange = { input = it },
            members = memberBots, isGroup = isGroup, mentionOnly = conv.groupMode == GroupMode.MENTION_ONLY,
            busy = generating, runtime = runtime, setupIncomplete = vm.savedConfigIncomplete(),
            editing = editing != null, onCancelEdit = { editing = null; input = "" },
            sparkTrigger = spark, onSend = doSend, onStop = { engine.stop(convId) },
            onStart = { vm.start() }, onOpenSetup = { nav.go(Route.Settings("provider")) },
            attachments = pendingAttachments,
            onAttachClick = { showAttach = true },
            onRemoveAttachment = { id -> pendingAttachments = pendingAttachments.filterNot { it.id == id } },
            onSlashPick = { def ->
                val instant = def.name in setOf("normal", "memory", "skills", "export", "clear", "help")
                if (instant) {
                    input = ""
                    SlashCommands.parse("/${def.name}")?.let(executeSlash)
                } else {
                    input = "/${def.name} "
                }
            },
            listening = listening,
            onMicClick = onMicTap,
        )
    }

    // ---- menus and dialogs ----
    if (menu) {
        val acts = ArrayList<SheetAction>()
        acts += SheetAction(PixelIcon.SLIDERS, stringResource(R.string.ui_ctrl_title)) { showControls = true }
        acts += SheetAction(PixelIcon.SKILL, stringResource(R.string.ui_ctrl_skills)) { skillPickerQuery = "" }
        if (msgs.isNotEmpty()) acts += SheetAction(PixelIcon.FORK, stringResource(R.string.ui_ctrl_fork)) { doFork(null) }
        acts += SheetAction(PixelIcon.EDIT, stringResource(R.string.ui_act_rename)) { rename = true }
        acts += SheetAction(PixelIcon.PIN, stringResource(if (conv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) {
            repo.setPinned(convId, !conv.pinned)
        }
        if (isGroup) acts += SheetAction(PixelIcon.GROUP, stringResource(R.string.ui_act_members)) { showMembers = true }
        acts += SheetAction(PixelIcon.SCROLL, stringResource(R.string.ui_act_inspector)) { nav.go(Route.Hermes) }
        acts += SheetAction(PixelIcon.REFRESH, stringResource(R.string.ui_act_clear), danger = true) { confirmClear = true }
        acts += SheetAction(PixelIcon.TRASH, stringResource(R.string.ui_act_delete), danger = true) { confirmDelete = true }
        ActionSheet(title, acts) { menu = false }
    }

    actionsFor?.let { m ->
        val acts = ArrayList<SheetAction>()
        if (m.text.isNotBlank()) {
            val isSp = speakingMsgId == m.id
            acts += SheetAction(
                if (isSp) PixelIcon.STOP else PixelIcon.VOLUME,
                stringResource(if (isSp) R.string.ui_act_stop_speak else R.string.ui_act_speak),
            ) {
                val b = m.botId?.let { botsById[it] } ?: if (m.role == MsgRole.BOT) memberBots.firstOrNull() else null
                app.voice.toggleSpeak(m.id, m.text, ap.language, b.voiceOverride())
            }
        }
        acts += SheetAction(PixelIcon.COPY, stringResource(R.string.ui_act_copy)) {
            copyToClipboard(ctx, m.text)
            app.sounds.play(SoundCue.TAP)
        }
        acts += SheetAction(PixelIcon.FORK, stringResource(R.string.ui_act_fork)) { doFork(m.id) }
        acts += SheetAction(PixelIcon.SCROLL, stringResource(R.string.ui_act_select)) { selectFor = m }
        acts += SheetAction(PixelIcon.SHARE, stringResource(R.string.ui_act_share)) { shareText(ctx, m.text) }
        if (m.role == MsgRole.USER && !generating) {
            acts += SheetAction(PixelIcon.EDIT, stringResource(R.string.ui_act_edit)) { editing = m; input = m.text }
        }
        if (m.role == MsgRole.BOT && m.id == lastBotId && lastBotIsLast && !generating) {
            acts += SheetAction(PixelIcon.REFRESH, stringResource(R.string.ui_act_regen)) { engine.regenerateLast(convId) }
        }
        acts += SheetAction(PixelIcon.TRASH, stringResource(R.string.ui_act_delete), danger = true) { repo.deleteMessage(convId, m.id) }
        ActionSheet(null, acts) { actionsFor = null }
    }

    selectFor?.let { m ->
        PocketDialog(
            title = stringResource(R.string.ui_chat_select_title), onDismiss = { selectFor = null },
            confirm = { PixelButton(stringResource(R.string.ui_dlg_close), onClick = { selectFor = null }) },
        ) {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(m.text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp) }
            }
        }
    }

    if (rename) RenameDialog(initial = conv.title.ifBlank { title }, onSave = { repo.renameConversation(convId, it) }, onDismiss = { rename = false })

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ui_dlg_delete_title), body = stringResource(R.string.ui_dlg_delete_body, title),
            confirmLabel = stringResource(R.string.ui_dlg_delete_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.deleteConversation(convId) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.ui_dlg_clear_title), body = stringResource(R.string.ui_dlg_clear_body),
            confirmLabel = stringResource(R.string.ui_dlg_clear_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.clearConversation(convId) },
            onDismiss = { confirmClear = false },
        )
    }
    if (showMembers) {
        MembersDialog(conv, onDismiss = { showMembers = false })
    }
    if (showAttach) {
        AttachSheet(
            onCamera = {
                try {
                    val (file, uri) = app.attachments.createCameraCaptureTarget()
                    pendingCameraFile = file
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(ctx, ctx.getString(R.string.ui_attach_failed, e.message ?: "camera"), Toast.LENGTH_SHORT).show()
                }
            },
            onGallery = { galleryLauncher.launch("image/*") },
            onDocument = { docLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showAttach = false },
        )
    }
    previewImage?.let { att ->
        ImagePreviewDialog(att) { previewImage = null }
    }
    if (showControls) {
        ChatControlsSheet(
            conv = conv,
            modelSuggestions = ProviderConfig.suggestions(vm.authMode, vm.endpoint),
            onSelectMode = { mode ->
                repo.setMode(convId, mode)
                app.sounds.play(SoundCue.TAP)
            },
            onSelectOverrides = { model, effort ->
                repo.setOverrides(convId, model, effort)
                app.sounds.play(SoundCue.TAP)
            },
            onOpenSkills = { skillPickerQuery = "" },
            onForkChat = { doFork(null) },
            onDismiss = { showControls = false },
        )
    }
    skillPickerQuery?.let { initialQ ->
        SkillPickerSheet(
            initialQuery = initialQ,
            onUseSkill = { skill ->
                val prompt = ctx.getString(R.string.ui_skills_picker_prompt, skill.name)
                input = if (input.isBlank()) prompt else "$input\n$prompt"
                app.sounds.play(SoundCue.TAP)
            },
            onOpenInspector = { nav.go(Route.Hermes) },
            onDismiss = { skillPickerQuery = null },
        )
    }
    pendingTerminalCmd?.let { cmd ->
        PocketDialog(
            title = stringResource(R.string.ui_run_term_title),
            onDismiss = { pendingTerminalCmd = null },
            confirm = {
                PixelButton(
                    stringResource(R.string.ui_chat_code_run),
                    onClick = {
                        pendingTerminalCmd = null
                        app.sounds.play(SoundCue.TAP)
                        app.terminal.run(cmd)
                        nav.go(Route.Terminal)
                    },
                    icon = PixelIcon.PLAY,
                )
            },
            dismiss = {
                PixelButton(stringResource(R.string.ui_dlg_cancel), onClick = { pendingTerminalCmd = null }, kind = ButtonKind.GHOST)
            },
        ) {
            Text(stringResource(R.string.ui_run_term_body), color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Box(
                Modifier.fillMaxWidth().background(c.codeBg).border(1.dp, c.outline).heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()).padding(8.dp),
            ) {
                Text(cmd, color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Group members (tap to add or remove a bot) and who answers. */
@Composable
private fun MembersDialog(conv: Conversation, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { PocketApp.from(ctx).chat }
    val bots by repo.bots.collectAsState()
    val c = Pocket.colors
    PocketDialog(
        title = stringResource(R.string.ui_chat_members_title), onDismiss = onDismiss,
        confirm = { PixelButton(stringResource(R.string.ui_dlg_close), onClick = onDismiss) },
    ) {
        Text(stringResource(R.string.ui_chat_mode_title), color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketChip(
                stringResource(R.string.ui_new_mode_all), conv.groupMode == GroupMode.ALL_IN_TURN,
                { repo.setGroupMode(conv.id, GroupMode.ALL_IN_TURN) }, Modifier.weight(1f),
            )
            PocketChip(
                stringResource(R.string.ui_new_mode_mention), conv.groupMode == GroupMode.MENTION_ONLY,
                { repo.setGroupMode(conv.id, GroupMode.MENTION_ONLY) }, Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            bots.forEach { b ->
                val member = b.id in conv.botIds
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Checkbox) {
                            val next = if (member) conv.botIds - b.id else conv.botIds + b.id
                            if (next.isNotEmpty()) repo.setConversationBots(conv.id, next)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotAvatar(b, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(b.name, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (member) PixelIconView(PixelIcon.CHECK, tint = c.success, size = 20.dp)
                }
            }
        }
    }
}
