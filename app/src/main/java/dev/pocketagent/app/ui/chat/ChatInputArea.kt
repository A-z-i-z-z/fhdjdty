package dev.pocketagent.app.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.R
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.ui.text.Texts

/**
 * The bottom of the chat: the approval card (when the agent asks), the input bar, and voice input with its launchers. It
 * reads the text being typed, so it is the part that recomposes on every keystroke; the message list does not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatInputArea(state: ChatScreenState, conv: Conversation) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = state.app
    val repo = state.repo
    val engine = state.engine
    val convId = conv.id
    val vm: MainViewModel = viewModel()

    val runtime by vm.state.collectAsState()
    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val statusMap by engine.status.collectAsState()
    val approvals by engine.approvals.collectAsState()
    val ap by AppearanceStore.get(ctx).appearance.collectAsState()
    val presenceSettings by app.presencePrefs.settings.collectAsState()

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val memberBots = conv.botIds.mapNotNull { botsById[it] }
    val isGroup = conv.isGroup
    val generating = convId in busySet
    // Wake-on-send: the message is shown and the engine is lighting the fire before it delivers it.
    val waking = statusMap[convId] == ChatEngine.STATUS_WAKING
    // Whether the Hermes rootfs is unpacked: re-read only when the runtime state changes. Must match the engine's canWake.
    val runtimeInstalled = remember(runtime) { app.runtime.isRootfsValid() }

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
        state.listening = false
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                state.input = if (state.input.isBlank()) spoken else "${state.input.trimEnd()} $spoken"
            }
        }
    }
    val launchVoiceDialog: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, Texts.get(ctx, R.string.ui_voice_prompt))
        }
        try {
            app.sounds.play(SoundCue.MIC)
            state.listening = true
            sttIntentLauncher.launch(intent)
        } catch (_: Exception) {
            state.listening = false
            state.say(Texts.get(ctx, R.string.ui_voice_unavailable), PetNoteKind.ERROR)
        }
    }
    val startInlineVoice: () -> Unit = {
        if (state.listening) {
            runCatching { state.activeRecognizer?.stopListening() }
            state.listening = false
        } else if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
            state.activeRecognizer?.let { runCatching { it.destroy() } }
            state.activeRecognizer = sr
            state.voiceBaseText = state.input.trimEnd()
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { state.listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { state.listening = false }
                override fun onError(error: Int) {
                    state.listening = false
                    runCatching { sr.destroy() }
                    if (state.activeRecognizer === sr) state.activeRecognizer = null
                    if (
                        error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        error != SpeechRecognizer.ERROR_CLIENT
                    ) {
                        launchVoiceDialog()
                    }
                }
                override fun onResults(results: Bundle?) {
                    state.listening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        state.input = if (state.voiceBaseText.isBlank()) spoken else "${state.voiceBaseText} $spoken"
                    }
                    runCatching { sr.destroy() }
                    if (state.activeRecognizer === sr) state.activeRecognizer = null
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val part = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (part.isNotEmpty()) {
                        state.input = if (state.voiceBaseText.isBlank()) part else "${state.voiceBaseText} $part"
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
            state.listening = true
            runCatching { sr.startListening(intent) }.onFailure {
                state.listening = false
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
        if (state.listening) {
            runCatching { state.activeRecognizer?.stopListening() }
            state.listening = false
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInlineVoice()
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        val approval = approvals[convId]
        if (approval != null) {
            // The card scrolls inside a limited height, so the input field keeps its room (a long command with the keyboard open
            // used to fill the whole area and squeeze the field).
            val maxCard = (LocalConfiguration.current.screenHeightDp * (if (WindowInsets.isImeVisible) 0.22f else 0.4f)).dp
            Box(Modifier.fillMaxWidth().heightIn(max = maxCard).verticalScroll(rememberScrollState()).testTag("chat.approval")) {
                ApprovalCard(approval, botsById[approval.botId]?.name) { engine.resolveApproval(convId, it) }
            }
        }
        ChatInputBar(
            text = state.input, onTextChange = { state.input = it },
            members = memberBots, isGroup = isGroup, mentionOnly = conv.groupMode == GroupMode.MENTION_ONLY,
            busy = generating, runtime = runtime, setupIncomplete = vm.savedConfigIncomplete(),
            runtimeInstalled = runtimeInstalled,
            editing = state.editing != null,
            onCancelEdit = { state.editing = null; state.input = state.preEditDraft; state.preEditDraft = "" },
            sparkTrigger = state.spark, onSend = { state.doSend() }, onStop = { engine.stop(convId) },
            onStart = { vm.start() }, onOpenSetup = { nav.go(Route.Settings("provider")) },
            attachments = state.pendingAttachments,
            onAttachClick = { state.showAttach = true },
            onRemoveAttachment = { id -> state.pendingAttachments = state.pendingAttachments.filterNot { it.id == id } },
            onSlashPick = { def ->
                val instant = def.name in setOf("normal", "memory", "skills", "export", "clear", "help")
                if (instant) {
                    state.input = ""
                    SlashCommands.parse("/${def.name}")?.let { state.executeSlash(it) }
                } else {
                    state.input = "/${def.name} "
                }
            },
            listening = state.listening,
            onMicClick = onMicTap,
            wakeOnSend = presenceSettings.wakeOnSend,
            waking = waking,
        )
    }
}
