package dev.pocketagent.app

import android.app.Application
import android.content.Context
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
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
import dev.pocketagent.app.util.AppLog

class PocketApp : Application() {
    lateinit var runtime: RuntimeManager; private set
    lateinit var diagnostics: Diagnostics; private set
    lateinit var wakeLocks: WakeLocks; private set
    /** Bots, conversations and messages (JSON files in filesDir/chat). */
    lateinit var chat: ChatRepository; private set
    /** Runs the conversations against Hermes's API. */
    lateinit var engine: ChatEngine; private set
    /** Hermes's SOUL.md / memories / skills / logs. */
    lateinit var hermesHome: HermesHome; private set
    /** One command at a time inside the Hermes rootfs. */
    lateinit var terminal: TerminalSession; private set

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
        engine = ChatEngine(
            chat, runtime.api, wakeLocks,
            provider = { ProviderConfig.providerName(settings.providerInputs("")) },
            isReady = { runtime.state.value == RuntimeState.Running },
        )
        hermesHome = HermesHome(paths)
        terminal = TerminalSession(runtime, paths.terminalHistory)
        AppLog.i("App", "started build ${BuildConfig.GIT_COMMIT} (${BuildConfig.VERSION_NAME})")
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
