package dev.pocketagent.app.ui.diag

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.diag.CrashOffer
import dev.pocketagent.app.diag.CrashReports
import dev.pocketagent.app.diag.ExitKind
import dev.pocketagent.app.diag.ExitReason
import dev.pocketagent.app.diag.ExitReasons
import dev.pocketagent.app.runtime.GuestCodeKind
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PetNotes
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.ErrorText
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// Kind of screen: parts of the Dashboard-kind section Settings > Diagnostics (cards) and a dialog over any screen.
// Five states (design language 8.16): see the table of docs/waves/w0/CONFIG.md 3.9.

/** The sentence for an exit kind (no full stop), used in the card rows and in the crash dialog. */
@StringRes
internal fun exitSentenceRes(kind: ExitKind): Int = when (kind) {
    ExitKind.CRASH -> R.string.cfg_exit_crash
    ExitKind.NATIVE_CRASH -> R.string.cfg_exit_native
    ExitKind.ANR -> R.string.cfg_exit_anr
    ExitKind.START_FAILED -> R.string.cfg_exit_start
    ExitKind.LOW_MEMORY -> R.string.cfg_exit_memory
    ExitKind.KILLED -> R.string.cfg_exit_killed
    ExitKind.USER -> R.string.cfg_exit_user
    ExitKind.UPDATED -> R.string.cfg_exit_updated
    ExitKind.SELF -> R.string.cfg_exit_self
    ExitKind.OTHER -> R.string.cfg_exit_other
}

/** Date and time of an exit in the language in force (the platform formatter). */
internal fun formatExitTime(timestamp: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(timestamp))

/**
 * Two read-only cards for Settings > Diagnostics: why the app last stopped, and the code Hermes runs from its folders that the
 * app did not write. No buttons (the Export logs button belongs to the caller).
 */
@Composable
fun DiagnosticsExtras() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        ExitReasonsCard()
        GuestCodeCard()
    }
}

@Composable
private fun ExtrasCard(tag: String, title: String, content: @Composable () -> Unit) {
    val c = Pocket.colors
    PixelPanel(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}, color = c.surface, border = c.outline) {
        Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            CText(title, color = c.accent, size = 12.sp, weight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ExitReasonsCard() {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val oldAndroid = Build.VERSION.SDK_INT < ExitReasons.MIN_SDK
    // null = still reading (Loading); the read happens off the main thread.
    val exits by produceState<List<ExitReason>?>(null, ctx) {
        value = if (oldAndroid) emptyList() else withContext(Dispatchers.IO) {
            ExitReasons.read(ctx, 12).filter { it.processName == ctx.packageName }.take(5)
        }
    }
    ExtrasCard("diag.exitReasons", stringResource(R.string.cfg_diag_exits_title)) {
        val list = exits
        when {
            oldAndroid -> Muted(stringResource(R.string.cfg_diag_exits_old_android))
            list == null -> Muted(stringResource(R.string.ctl_loading))
            list.isEmpty() -> Muted(stringResource(R.string.cfg_diag_exits_none))
            else -> list.forEach { e ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    CText(stringResource(exitSentenceRes(ExitReasons.kind(e.reason))), size = 14.sp)
                    Muted(formatExitTime(e.timestamp, locale), size = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GuestCodeCard() {
    val ctx = LocalContext.current
    val rt = remember { PocketApp.from(ctx).runtime }
    // The scan runs at every Hermes start: reading again when the state changes keeps the card current.
    val state by rt.state.collectAsState()
    val entries = remember(state) { rt.guestCodeReport }
    ExtrasCard("diag.guestCode", stringResource(R.string.cfg_diag_guest_title)) {
        if (entries.isEmpty()) {
            Muted(stringResource(R.string.cfg_diag_guest_none))
        } else {
            CText(pluralStringResource(R.plurals.cfg_diag_guest_found, entries.size, entries.size), size = 13.sp)
            entries.forEach { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    val kindLabel = when (e.kind) {
                        GuestCodeKind.HOOK -> stringResource(R.string.cfg_guest_kind_hook)
                        GuestCodeKind.PLUGIN -> stringResource(R.string.cfg_guest_kind_plugin)
                    }
                    CText(kindLabel, color = Pocket.colors.textMuted, size = 12.sp, weight = FontWeight.Bold)
                    CText(e.path, size = 13.sp, mono = true)
                }
            }
        }
    }
}

/**
 * A dialog over whatever screen is open, shown while a crash of the app is waiting to be offered (see [CrashReports]). "Copy
 * report" puts a redacted report on the clipboard; "Not now" only marks the crash as seen. Nothing is ever sent anywhere.
 */
@Composable
fun CrashReportOffer() {
    val ctx = LocalContext.current
    val reports = remember { PocketApp.from(ctx).crashReports }
    val offer by reports.offer.collectAsState()
    offer?.let { CrashOfferDialog(it, reports) }
}

@Composable
private fun CrashOfferDialog(offer: CrashOffer, reports: CrashReports) {
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    var copying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val newest = offer.crashes.first()
    val sentence = stringResource(exitSentenceRes(ExitReasons.kind(newest.reason)))
    val whenText = formatExitTime(newest.timestamp, locale)
    val copiedText = stringResource(R.string.cfg_crash_copied)
    PocketDialog(
        title = stringResource(R.string.cfg_crash_title),
        onDismiss = { reports.dismiss() },
        confirm = {
            PixelButton(
                stringResource(R.string.cfg_crash_copy),
                onClick = {
                    if (!copying) {
                        copying = true
                        failed = false
                        scope.launch {
                            // The note is shown inside the IO block: copyReport() clears the offer, which removes this dialog
                            // (and cancels this scope) before a continuation on the main thread could run.
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    reports.copyReport()
                                    PetNotes.show(copiedText, PetNoteKind.SUCCESS)
                                    true
                                } catch (e: Exception) {
                                    AppLog.w("Diag", "crash report not copied: ${e.javaClass.simpleName}")
                                    false
                                }
                            }
                            copying = false
                            if (!ok) failed = true
                        }
                    }
                },
                modifier = Modifier.testTag("crash.copy"),
                enabled = !copying,
            )
        },
        dismiss = {
            PixelButton(
                stringResource(R.string.cfg_crash_not_now),
                onClick = { reports.dismiss() },
                modifier = Modifier.testTag("crash.dismiss"),
                kind = ButtonKind.GHOST,
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().testTag("crash.offer"), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            CText(stringResource(R.string.cfg_crash_body, sentence, whenText))
            if (failed) ErrorText(stringResource(R.string.cfg_crash_copy_failed))
        }
    }
}
