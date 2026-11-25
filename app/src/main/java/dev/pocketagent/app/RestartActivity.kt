package dev.pocketagent.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.File

/**
 * The "process phoenix" of the app: restarts the whole process (used after "Restore from backup", whose data must be read by a
 * fresh process).
 *
 * An activity started in the same process dies with it, so this one lives in its OWN process (`android:process=":restart"` in the
 * manifest). From there it kills the main process, starts [MainActivity] in a new task (which creates a new main process), and
 * ends its own process.
 *
 * It must never touch [PocketApp]'s members: [PocketApp.onCreate] returns at once in this process ([RestartProcess]), so every
 * `lateinit` member there stays uninitialised.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent?.getIntExtra(EXTRA_MAIN_PID, -1) ?: -1
        // Never kill ourselves by mistake (a missing extra is -1; the pid of this process is not the main one).
        if (mainPid > 0 && mainPid != Process.myPid()) Process.killProcess(mainPid)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_MAIN_PID = "main_pid"
    }
}

/** Which process the code runs in. Pure and Android-free where it can be (the unit tests cover [isRestartProcess]). */
object RestartProcess {
    /** The suffix of the process name declared for [RestartActivity] (`<package>:restart`). */
    const val SUFFIX = ":restart"

    /** True when [processName] (e.g. `dev.pocketagent.app:restart`) is the process of [RestartActivity]. */
    fun isRestartProcess(processName: String?): Boolean = processName != null && processName.trim().endsWith(SUFFIX)

    /**
     * The name of the current process: `Application.getProcessName()` on API 28+, else `/proc/self/cmdline` (the process name is
     * its first NUL-terminated word). Null when it cannot be determined, which counts as "the main process".
     */
    fun currentProcessName(): String? = try {
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else File("/proc/self/cmdline").readBytes().takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8).ifEmpty { null }
    } catch (_: Throwable) {
        null
    }
}
