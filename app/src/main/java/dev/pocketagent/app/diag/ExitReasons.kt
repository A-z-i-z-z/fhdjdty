package dev.pocketagent.app.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dev.pocketagent.app.util.AppLog

/** One record of Android's history of how a process of this app ended (a plain copy of ApplicationExitInfo). */
data class ExitReason(
    val timestamp: Long,
    val pid: Int,
    val processName: String,
    val reason: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String,
)

/** What an exit reason means for the person, in ten kinds. */
enum class ExitKind { CRASH, NATIVE_CRASH, ANR, START_FAILED, LOW_MEMORY, KILLED, USER, UPDATED, SELF, OTHER }

/**
 * Result of [ExitReasons.decide]. [offer] is what the crash dialog may show (newest first); [newSeenUpTo] is non-null only on the
 * very first look of a build, where it is the baseline to store (nothing is offered then).
 */
data class CrashDecision(val offer: List<ExitReason>, val newSeenUpTo: Long?)

/** Structured reader of the process exit history, plus the pure rules that decide what is worth offering. */
object ExitReasons {
    /** The first Android version that has the exit history (Android 11). */
    const val MIN_SDK = 30

    /** Android's name of an exit reason (the table the diagnostics dump always used). */
    fun name(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"; 4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"; 8 -> "PERMISSION_CHANGE"; 9 -> "EXCESSIVE_RESOURCE_USAGE"; 10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"; 12 -> "DEPENDENCY_DIED"; 13 -> "OTHER"; 14 -> "FREEZER"; 15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"; else -> "UNKNOWN"
    }

    fun kind(reason: Int): ExitKind = when (reason) {
        4 -> ExitKind.CRASH
        5 -> ExitKind.NATIVE_CRASH
        6 -> ExitKind.ANR
        7 -> ExitKind.START_FAILED
        3 -> ExitKind.LOW_MEMORY
        2, 9, 14 -> ExitKind.KILLED
        10, 11 -> ExitKind.USER
        16 -> ExitKind.UPDATED
        1 -> ExitKind.SELF
        else -> ExitKind.OTHER
    }

    /** A crash, a native crash, an ANR or a start that failed. Never a kill from outside or a close by the person. */
    fun isCrash(reason: Int): Boolean = when (kind(reason)) {
        ExitKind.CRASH, ExitKind.NATIVE_CRASH, ExitKind.ANR, ExitKind.START_FAILED -> true
        else -> false
    }

    /**
     * Which exits may be offered in the crash dialog: only the main process, only crashes, only those newer than [seenUpTo], newest
     * first. [seenUpTo] == null is the first look of this build: nothing is offered and the newest timestamp (0 when there is none)
     * comes back as the baseline, so installing a build never shows an old crash.
     */
    fun decide(all: List<ExitReason>, mainProcess: String, seenUpTo: Long?): CrashDecision {
        if (seenUpTo == null) return CrashDecision(emptyList(), all.maxOfOrNull { it.timestamp } ?: 0L)
        val offer = all
            .filter { it.processName == mainProcess && isCrash(it.reason) && it.timestamp > seenUpTo }
            .sortedByDescending { it.timestamp }
        return CrashDecision(offer, null)
    }

    /** One line of the diagnostics dump. */
    fun describe(e: ExitReason, formatTime: (Long) -> String): String =
        "${formatTime(e.timestamp)}  reason=${name(e.reason)}(${e.reason}) status=${e.status} importance=${e.importance} " +
            "pss=${e.pssKb}KB rss=${e.rssKb}KB pid=${e.pid} process=${e.processName}  desc=${e.description}"

    /** The newest [max] exits of this app's processes, newest first. Android before 11 or any failure gives an empty list. */
    fun read(context: Context, max: Int = 20): List<ExitReason> {
        if (Build.VERSION.SDK_INT < MIN_SDK) return emptyList()
        return try {
            readApi30(context, max)
        } catch (e: Throwable) {
            AppLog.w("Diag", "exit reasons could not be read: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readApi30(context: Context, max: Int): List<ExitReason> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getHistoricalProcessExitReasons(context.packageName, 0, max).map { i ->
            ExitReason(
                timestamp = i.timestamp, pid = i.pid, processName = i.processName ?: "", reason = i.reason, status = i.status,
                importance = i.importance, pssKb = i.pss, rssKb = i.rss, description = i.description ?: "",
            )
        }.sortedByDescending { it.timestamp }
    }
}
