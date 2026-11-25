package dev.pocketagent.app

import android.app.Application
import android.content.Context
import android.net.Uri
import dev.pocketagent.app.data.AttachmentStore
import dev.pocketagent.app.data.BackupManager
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.DraftStore
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.data.UsageStore
import dev.pocketagent.app.diag.CrashReports
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.diag.Diagnostics
import dev.pocketagent.app.hermes.HermesHome
import dev.pocketagent.app.hermes.TerminalSession
import dev.pocketagent.app.routines.RoutineScheduler
import dev.pocketagent.app.routines.RoutineStore
import dev.pocketagent.app.runtime.GuestPrefs
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.PracticeModel
import dev.pocketagent.app.runtime.PresencePrefs
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.RuntimeAwait
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import dev.pocketagent.app.runtime.WakeLocks
import dev.pocketagent.app.service.AppForeground
import dev.pocketagent.app.service.Presence
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
import kotlinx.coroutines.flow.first
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
    /** The web-lookup switch and the practice-model switch (the guest's generated configuration depends on them). */
    lateinit var guestPrefs: GuestPrefs; private set
    /** Reads Android's exit records at start and offers a copyable report after a crash. */
    lateinit var crashReports: CrashReports; private set
    lateinit var wakeLocks: WakeLocks; private set
    /** Bots, conversations and messages (JSON files in filesDir/chat). */
    lateinit var chat: ChatRepository; private set
    /** Photos and documents attached to chats. */
    lateinit var attachments: AttachmentStore; private set
    /** Token statistics per day and model, and the daily budget. */
    lateinit var usage: UsageStore; private set
    /** Unsent message text per chat. */
    lateinit var drafts: DraftStore; private set
    /** Auto-start, wake-on-send, notification and idle-stop settings. */
    lateinit var presencePrefs: PresencePrefs; private set
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
    /** Scheduled prompts. */
    lateinit var routines: RoutineStore; private set
    lateinit var routineScheduler: RoutineScheduler; private set
    /** Backup and restore of the app's data. */
    lateinit var backup: BackupManager; private set
    /** Notifications, auto-start, auto-restart and idle stop. */
    lateinit var presence: Presence; private set

    /** Pending Android ACTION_SEND payload waiting for the user to choose a chat. */
    val incomingShare = MutableStateFlow<IncomingShare?>(null)

    /** A navigation request from a notification, shortcut, tile or the text-selection menu, consumed by AppRoot. */
    val pendingLink = MutableStateFlow<AppLink?>(null)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The ":restart" process (RestartActivity) only kills the main process and starts it again: it must not build anything
        // (no runtime, no stores, no alarms), and all the lateinit members below stay uninitialised there.
        if (RestartProcess.isRestartProcess(RestartProcess.currentProcessName())) return
        Diag.install()
        AppLog.init(this)
        // Texts first (nothing below shows a text yet): a failure here is logged and never stops the app from starting.
        try { dev.pocketagent.app.ui.text.Texts.install(this) } catch (e: Throwable) { AppLog.e("App", "texts failed to install", e) }
        // Right after the log exists and before anything else is constructed: a crash while building the runtime
        // (build 8's) must reach app.log. It depends on AppLog only.
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(Thread.getDefaultUncaughtExceptionHandler()))
        // Builds with test hooks only: the secret a debug link must carry (never throws).
        DebugLinks.ensureToken(filesDir)
        val paths = Paths(this)
        val settings = Settings(this)
        // Before anything that can call ProviderConfig.savedProblems: it reads the practice flag this sets.
        guestPrefs = GuestPrefs.get(this)
        val secrets = SecretStore(paths, settings)
        wakeLocks = WakeLocks(this)
        runtime = RuntimeManager(this, paths, settings, secrets, wakeLocks)
        diagnostics = Diagnostics(this, runtime)
        crashReports = CrashReports(this, diagnostics)
        // Pixel sprites load in the background: a broken sprite file must never stop the app from starting.
        try { dev.pocketagent.app.ui.pixel.SpriteBank.preload(this) } catch (e: Throwable) { AppLog.e("App", "sprite bank failed to load", e) }
        chat = ChatRepository(this)
        attachments = AttachmentStore(this, paths)
        usage = UsageStore(this)
        drafts = DraftStore(this)
        presencePrefs = PresencePrefs.get(this)
        // Everything the engine reads lazily (presencePrefs, usage, runtime) exists before the first message can be sent.
        engine = ChatEngine(
            chat, runtime.api, wakeLocks,
            // The practice switch flips at once but Hermes changes its configuration only after its asynchronous restart: while either
            // side says practice, a per-chat or per-bot model override goes out as the practice provider, never as a real one.
            provider = { if (runtime.lastGuestOptions.practice || GuestPrefs.practiceActive) PracticeModel.PROVIDER else ProviderConfig.providerName(settings.providerInputs("")) },
            isReady = { runtime.state.value == RuntimeState.Running },
            imageLoader = { attachments.toDataUrl(it) },
            canWake = { presencePrefs.settings.value.wakeOnSend && runtime.isRootfsValid() },
            awaitReady = { cancelled -> RuntimeAwait.awaitRunning(runtime, 6 * 60_000L, cancelled) },
            usage = usage,
        )
        hermesHome = HermesHome(paths)
        terminal = TerminalSession(runtime, paths.terminalHistory)
        voicePrefs = VoicePrefs.get(this)
        ttsModels = SupertonicModelStore(this)
        voice = VoiceEngine(this, ttsModels, voicePrefs)
        sounds = SoundEngine(this, voicePrefs)
        routines = RoutineStore(this)
        routineScheduler = RoutineScheduler(this)
        backup = BackupManager(this)
        presence = Presence(this)
        observeEngineAudio()
        pruneDraftsWhenChatsAreLoaded()
        // Last: these may read any member above.
        try { presence.install() } catch (e: Throwable) { AppLog.e("App", "presence failed to install", e) }
        try { routineScheduler.rescheduleAll() } catch (e: Throwable) { AppLog.e("App", "routine alarms could not be scheduled", e) }
        wave0Slots()
        AppLog.i(
            "App",
            "started Agiri build ${BuildConfig.GIT_COMMIT} (${BuildConfig.VERSION_NAME}) variant=${BuildConfig.APP_VARIANT} " +
                "signing=${BuildConfig.SIGNING_KIND} package=${BuildConfig.APPLICATION_ID}",
        )
    }

    /**
     * WAVE 0 SLOTS: the one place other areas ask for an init line. One try/catch per slot; never construct anything slow here.
     * An area that needs a line writes `slot: <AREA>: <exact line>` in its report for the next wave.
     */
    private fun wave0Slots() {
        // slot: CRASH: read Android's exit records of the last run and prepare the crash offer (blocking calls, so off the main thread).
        try { appScope.launch(Dispatchers.IO) { try { crashReports.evaluate() } catch (e: Throwable) { AppLog.e("App", "crash report check failed", e) } } }
        catch (e: Throwable) { AppLog.e("App", "crash report slot failed", e) }
    }

    /** Drafts of deleted chats are dropped, but only once the chat list is really loaded (it is empty until then). */
    private fun pruneDraftsWhenChatsAreLoaded() {
        appScope.launch {
            try {
                chat.loaded.first { it }
                // A chat list that failed to load is empty without the chats being gone: keep the drafts then.
                if (chat.indexIntact) drafts.prune(chat.conversations.value.map { it.id }.toSet())
                else AppLog.w("App", "chat index not read cleanly; drafts are not pruned this run")
            } catch (e: Throwable) {
                AppLog.e("App", "pruning drafts failed", e)
            }
        }
    }

    private fun observeEngineAudio() {
        appScope.launch {
            engine.events.collect { ev ->
                // Turns that nobody is watching (a routine at night, an inline reply from a notification, a chat the owner
                // left) are announced by their notifications only: no chime and no reading aloud while the app is in the background.
                if (!AppForeground.isForeground.value) return@collect
                when (ev) {
                    is EngineEvent.TurnCompleted -> {
                        sounds.play(SoundCue.COMPLETE)
                        // A routine's answer is read aloud only when its chat is the one on screen.
                        val unattendedRoutine = !chat.conversation(ev.convId)?.routineId.isNullOrEmpty() && chat.activeConversation != ev.convId
                        if (voicePrefs.settings.value.autoSpeak && ev.text.isNotBlank() && !unattendedRoutine) {
                            val lang = AppearanceStore.get(this@PocketApp).appearance.value.language
                            val botOverride = chat.bot(ev.botId).voiceOverride()
                            voice.speak(ev.messageId, ev.text, lang, botOverride)
                        }
                    }
                    is EngineEvent.TurnStarted -> {}
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
