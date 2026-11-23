package dev.pocketagent.app

import android.app.Application
import android.content.Context
import android.net.Uri
import dev.pocketagent.app.data.AttachmentStore
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.diag.Diagnostics
import dev.pocketagent.app.hermes.HermesHome
import dev.pocketagent.app.hermes.TerminalSession
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import dev.pocketagent.app.runtime.WakeLocks
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.util.AppLog
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.voice.SoundEngine
import dev.pocketagent.app.voice.SupertonicModelStore
import dev.pocketagent.app.voice.VoiceEngine
import dev.pocketagent.app.voice.VoicePrefs
import dev.pocketagent.app.voice.voiceOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Content shared from another Android app via ACTION_SEND, awaiting chat target selection in AppRoot. */
data class IncomingShare(
    val text: String? = null,
    val uri: Uri? = null,
    val mime: String? = null,
)

class PocketApp : Application() {
    lateinit var runtime: RuntimeManager; private set
    lateinit var diagnostics: Diagnostics; private set
    lateinit var wakeLocks: WakeLocks; private set
    /** Bots, conversations and messages (JSON files in filesDir/chat). */
    lateinit var chat: ChatRepository; private set
    /** Photos and documents attached to chats. */
    lateinit var attachments: AttachmentStore; private set
    /** Runs the conversations against Hermes's API. */
    lateinit var engine: ChatEngine; private set
    /** Hermes's SOUL.md / memories / skills / logs. */
    lateinit var hermesHome: HermesHome; private set
    /** One command at a time inside the Hermes rootfs. */
    lateinit var terminal: TerminalSession; private set
    /** Voice & Sound preferences. */
    lateinit var voicePrefs: VoicePrefs; private set
    /** On-device Supertonic 3 INT8 TTS model manager. */
    lateinit var ttsModels: SupertonicModelStore; private set
    /** Unified Supertonic 3 / system TTS playback engine. */
    lateinit var voice: VoiceEngine; private set
    /** Curated CC0 tactile sound effects engine. */
    lateinit var sounds: SoundEngine; private set

    /** Pending Android ACTION_SEND payload waiting for the user to choose a chat. */
    val incomingShare = MutableStateFlow<IncomingShare?>(null)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Diag.install()
        AppLog.init(this)
        // Right after the log exists and before anything else is constructed: a crash while building the runtime
        // (build 8's) must reach app.log. It depends on AppLog only.
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(Thread.getDefaultUncaughtExceptionHandler()))
        val paths = Paths(this)
        val settings = Settings(this)
        val secrets = SecretStore(paths, settings)
        wakeLocks = WakeLocks(this)
        runtime = RuntimeManager(this, paths, settings, secrets, wakeLocks)
        diagnostics = Diagnostics(this, runtime)
        // Pixel sprites: a broken sprite file must never stop the app from starting.
        try { dev.pocketagent.app.ui.pixel.SpriteBank.init(this) } catch (e: Throwable) { AppLog.e("App", "sprite bank failed to load", e) }
        chat = ChatRepository(this)
        attachments = AttachmentStore(this, paths)
        engine = ChatEngine(
            chat, runtime.api, wakeLocks,
            provider = { ProviderConfig.providerName(settings.providerInputs("")) },
            isReady = { runtime.state.value == RuntimeState.Running },
            imageLoader = { attachments.toDataUrl(it) },
        )
        hermesHome = HermesHome(paths)
        terminal = TerminalSession(runtime, paths.terminalHistory)
        voicePrefs = VoicePrefs.get(this)
        ttsModels = SupertonicModelStore(this)
        voice = VoiceEngine(this, ttsModels, voicePrefs)
        sounds = SoundEngine(this, voicePrefs)
        observeEngineAudio()
        AppLog.i("App", "started Agiri build ${BuildConfig.GIT_COMMIT} (${BuildConfig.VERSION_NAME})")
    }

    private fun observeEngineAudio() {
        appScope.launch {
            engine.events.collect { ev ->
                when (ev) {
                    is EngineEvent.TurnCompleted -> {
                        sounds.play(SoundCue.COMPLETE)
                        if (voicePrefs.settings.value.autoSpeak && ev.text.isNotBlank()) {
                            val lang = AppearanceStore.get(this@PocketApp).appearance.value.language
                            val botOverride = chat.bot(ev.botId).voiceOverride()
                            voice.speak(ev.messageId, ev.text, lang, botOverride)
                        }
                    }
                    is EngineEvent.ToolFinished -> sounds.play(SoundCue.TOOL)
                    is EngineEvent.MemorySaved -> sounds.play(SoundCue.MEMORY)
                    is EngineEvent.BotForged -> sounds.play(SoundCue.FORGE)
                    is EngineEvent.ApprovalRequested -> sounds.play(SoundCue.APPROVAL)
                    is EngineEvent.TurnFailed -> sounds.play(SoundCue.ERROR)
                }
            }
        }
    }

    private class CrashLogger(private val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            AppLog.e("CRASH", "uncaught exception in ${t.name}", e)
            next?.uncaughtException(t, e)
        }
    }

    companion object {
        fun from(context: Context): PocketApp = context.applicationContext as PocketApp
    }
}
