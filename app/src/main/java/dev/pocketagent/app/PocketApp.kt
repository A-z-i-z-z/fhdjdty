package dev.pocketagent.app

import android.app.Application
import android.content.Context
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.diag.Diagnostics
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import dev.pocketagent.app.runtime.WakeLocks
import dev.pocketagent.app.util.AppLog

class PocketApp : Application() {
    lateinit var runtime: RuntimeManager; private set
    lateinit var diagnostics: Diagnostics; private set
    lateinit var wakeLocks: WakeLocks; private set

    override fun onCreate() {
        super.onCreate()
        Diag.install()
        AppLog.init(this)
        val paths = Paths(this)
        val settings = Settings(this)
        val secrets = SecretStore(paths, settings)
        wakeLocks = WakeLocks(this)
        runtime = RuntimeManager(this, paths, settings, secrets, wakeLocks)
        diagnostics = Diagnostics(this, runtime)
        AppLog.i("App", "started build ${BuildConfig.GIT_COMMIT} (${BuildConfig.VERSION_NAME})")
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(Thread.getDefaultUncaughtExceptionHandler()))
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
