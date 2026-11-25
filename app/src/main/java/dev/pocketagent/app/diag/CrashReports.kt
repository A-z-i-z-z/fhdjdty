package dev.pocketagent.app.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The crashes the dialog offers a report for, newest first. Never empty. */
data class CrashOffer(val crashes: List<ExitReason>)

/**
 * Remembers which crashes of this app's main process the person has already been told about, and offers a copyable report for
 * the ones they have not. The offer is only ever a dialog while the app is open: never a notification, never sent anywhere.
 *
 * State lives in the SharedPreferences file `crash_reports` (key `seen_up_to`). The previous version of the app ignores it.
 */
class CrashReports(
    private val context: Context,
    private val diagnostics: Diagnostics,
    private val prefs: SharedPreferences = context.getSharedPreferences("crash_reports", Context.MODE_PRIVATE),
    private val reader: () -> List<ExitReason> = { ExitReasons.read(context) },
) {
    private val _offer = MutableStateFlow<CrashOffer?>(null)

    /** Null = nothing to offer. */
    val offer: StateFlow<CrashOffer?> = _offer

    /**
     * Looks at the exit history and updates [offer]. BLOCKING (system and file calls): call it off the main thread.
     * The first run of a build stores a baseline and offers nothing; later runs offer crashes newer than the stored mark.
     */
    fun evaluate() {
        try {
            val seen = if (prefs.contains(KEY_SEEN)) prefs.getLong(KEY_SEEN, 0L) else null
            val decision = ExitReasons.decide(reader(), context.packageName, seen)
            decision.newSeenUpTo?.let { prefs.edit().putLong(KEY_SEEN, it).apply() }
            _offer.value = if (decision.offer.isEmpty()) null else CrashOffer(decision.offer)
            if (decision.offer.isNotEmpty()) AppLog.i("Diag", "crash report offered for ${decision.offer.size} exit(s)")
        } catch (e: Throwable) {
            AppLog.w("Diag", "crash check failed: ${e.javaClass.simpleName}")
        }
    }

    /** The text that goes to the clipboard: a short header, one line per offered crash, then the redacted diagnostics dump. */
    fun reportText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        val crashes = _offer.value?.crashes.orEmpty()
        val header = buildString {
            appendLine("Agirî crash report")
            appendLine("package: ${context.packageName}  version: ${BuildConfig.VERSION_NAME}  commit: ${BuildConfig.GIT_COMMIT}  variant: ${BuildConfig.APP_VARIANT}")
            appendLine("process exits that were reported as crashes (newest first):")
            if (crashes.isEmpty()) appendLine("  (none)")
            crashes.forEach { appendLine("  " + ExitReasons.describe(it) { ts -> fmt.format(Date(ts)) }) }
            appendLine()
        }
        return Diag.redactor.redact(header) + diagnostics.buildForClipboard().text
    }

    /**
     * Puts [reportText] on the clipboard and then marks the shown crashes as seen. BLOCKING: call it off the main thread.
     * Throws when the clipboard refuses; the offer then stays, so the dialog stays open and the person can try again.
     */
    fun copyReport() {
        val text = reportText()
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Agirî crash report", text))
        dismiss()
    }

    /** Marks the shown crashes as seen and clears [offer]. */
    fun dismiss() {
        val shown = _offer.value ?: return
        val newest = shown.crashes.maxOfOrNull { it.timestamp } ?: 0L
        prefs.edit().putLong(KEY_SEEN, maxOf(prefs.getLong(KEY_SEEN, 0L), newest)).apply()
        _offer.value = null
    }

    private companion object {
        const val KEY_SEEN = "seen_up_to"
    }
}
