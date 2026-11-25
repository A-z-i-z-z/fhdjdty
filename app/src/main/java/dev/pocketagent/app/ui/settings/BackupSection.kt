package dev.pocketagent.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.BackupException
import dev.pocketagent.app.data.BackupManager
import dev.pocketagent.app.data.BackupReason
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.util.FileUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** What the section is doing: nothing, working (with the text to show), or a result line. */
private sealed interface BackupUi {
    data object Idle : BackupUi
    data class Working(val labelRes: Int) : BackupUi
    data class Done(val text: String) : BackupUi
    data class Failed(val text: String, val restartNeeded: Boolean = false) : BackupUi
    /** The file was read and is valid; waiting for the user's yes. [uri] is read again for the restore. */
    data class Confirm(val uri: Uri, val summary: BackupManager.Summary) : BackupUi
    data object Restarting : BackupUi
}

/**
 * Settings > Backup: save everything to one zip and restore it. Credentials are never part of the file (the text says so).
 * Reading and writing happen off the main thread; a restore needs a yes first and ends with the app restarting.
 */
@Composable
internal fun BackupSection() {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val scope = rememberCoroutineScope()
    val c = Pocket.colors
    var includeAttachments by remember { mutableStateOf(true) }
    var includeHermes by remember { mutableStateOf(true) }
    var ui by remember { mutableStateOf<BackupUi>(BackupUi.Idle) }

    val busyMsg = stringResource(R.string.ctl_bak_busy)

    fun sizeText(bytes: Long) = FileUtil.formatBytes(bytes)

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val options = BackupManager.Options(includeAttachments, includeHermes)
        ui = BackupUi.Working(R.string.ctl_bak_creating)
        scope.launch {
            ui = try {
                val s = withContext(Dispatchers.IO) {
                    // The document exists before we write to it. Whatever goes wrong (also the screen going away: this block is
                    // blocking and runs to its end), a half-written file with the normal backup name must not stay behind.
                    try {
                        val out = ctx.contentResolver.openOutputStream(uri, "wt")
                            ?: throw BackupException("The chosen location cannot be written.", reason = BackupReason.CANNOT_WRITE)
                        out.use { app.backup.export(it, options) }
                    } catch (t: Throwable) {
                        discardDocument(ctx, uri)
                        throw t
                    }
                }
                BackupUi.Done(ctx.getString(R.string.ctl_bak_done, s.conversations, s.bots, s.routines, s.files, sizeText(s.bytes)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupUi.Failed(ctx.getString(R.string.ctl_bak_failed, backupErrorText(ctx, e)))
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui = BackupUi.Working(R.string.ctl_bak_reading)
        scope.launch {
            ui = try {
                val s = withContext(Dispatchers.IO) {
                    val input = ctx.contentResolver.openInputStream(uri)
                        ?: throw BackupException("The chosen file cannot be read.", reason = BackupReason.CANNOT_READ)
                    input.use { app.backup.inspect(it) }
                }
                BackupUi.Confirm(uri, s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupUi.Failed(ctx.getString(R.string.ctl_bak_restore_failed, backupErrorText(ctx, e)))
            }
        }
    }

    fun startRestore(uri: Uri) {
        if (app.engine.busy.value.isNotEmpty()) { ui = BackupUi.Failed(ctx.getString(R.string.ctl_bak_restore_failed, busyMsg)); return }
        ui = BackupUi.Working(R.string.ctl_bak_restoring)
        scope.launch {
            ui = try {
                withContext(Dispatchers.IO) {
                    val input = ctx.contentResolver.openInputStream(uri)
                        ?: throw BackupException("The chosen file cannot be read.", reason = BackupReason.CANNOT_READ)
                    input.use { app.backup.restore(it) }
                    // Restart in the same blocking step as the restore, not after it: this coroutine belongs to the section, and
                    // if the user leaves the section meanwhile it is cancelled when withContext returns. The restore has by then
                    // stopped Hermes and suspended every store, so without a restart nothing more would ever be saved.
                    // restartApp only starts an activity and posts to the main looper, so it is safe on this thread.
                    app.backup.restartApp()
                }
                BackupUi.Restarting
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                BackupUi.Failed(ctx.getString(R.string.ctl_bak_restore_failed, backupErrorText(ctx, e)), e.restartNeeded)
            } catch (e: Exception) {
                BackupUi.Failed(ctx.getString(R.string.ctl_bak_restore_failed, backupErrorText(ctx, e)))
            }
        }
    }

    val working = ui is BackupUi.Working || ui is BackupUi.Restarting

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(stringResource(R.string.ctl_bak_intro))
        CtlCard(color = c.bgDeep) {
            SubHeading(stringResource(R.string.ctl_bak_inside_title))
            CText(stringResource(R.string.ctl_bak_inside), size = 13.sp)
            CText(stringResource(R.string.ctl_bak_never), size = 13.sp, color = c.success)
        }

        // ---- create ----
        SubHeading(stringResource(R.string.ctl_bak_create_title))
        PocketSwitchRow(
            stringResource(R.string.ctl_bak_opt_attach), includeAttachments, { includeAttachments = it },
            subtitle = stringResource(R.string.ctl_bak_opt_attach_sub),
        )
        PocketSwitchRow(
            stringResource(R.string.ctl_bak_opt_hermes), includeHermes, { includeHermes = it },
            subtitle = stringResource(R.string.ctl_bak_opt_hermes_sub),
        )
        PixelButton(
            stringResource(R.string.ctl_bak_create),
            onClick = { createLauncher.launch(defaultFileName()) },
            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.DOWNLOAD, enabled = !working,
        )

        // ---- restore ----
        SubHeading(stringResource(R.string.ctl_bak_restore_title))
        Muted(stringResource(R.string.ctl_bak_restore_help), size = 12.sp)
        PixelButton(
            stringResource(R.string.ctl_bak_restore),
            onClick = { openLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.UPLOAD, kind = ButtonKind.SECONDARY, enabled = !working,
        )

        // ---- state ----
        when (val u = ui) {
            is BackupUi.Working -> {
                CText(stringResource(u.labelRes), size = 13.sp)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceHigh)
            }
            is BackupUi.Done -> CText(u.text, size = 13.sp, color = c.success)
            is BackupUi.Failed -> {
                ErrorText(u.text)
                if (u.restartNeeded) PixelButton(stringResource(R.string.ctl_bak_restart_now), onClick = { app.backup.restartApp() }, icon = PixelIcon.REFRESH)
            }
            BackupUi.Restarting -> CText(stringResource(R.string.ctl_bak_restoring), size = 13.sp)
            BackupUi.Idle, is BackupUi.Confirm -> Unit
        }
    }

    (ui as? BackupUi.Confirm)?.let { u ->
        val s = u.summary
        val locale = dev.pocketagent.app.ui.chat.currentLocale()
        val made = if (s.createdAt > 0L) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(s.createdAt)) else "?"
        ConfirmDialog(
            title = stringResource(R.string.ctl_bak_restore_confirm_title),
            text = stringResource(
                R.string.ctl_bak_restore_confirm_body, made, s.appVersion.ifBlank { "?" }, s.conversations, s.bots, s.routines, s.files, sizeText(s.bytes),
            ),
            confirmLabel = stringResource(R.string.ctl_bak_restore_confirm),
            danger = true,
            onConfirm = { ui = BackupUi.Idle; startRestore(u.uri) },
            onDismiss = { ui = BackupUi.Idle },
        )
    }
}

/** The text for a failure in the app language. Reasons without a translation fall back to the exception's own message. */
internal fun backupErrorText(ctx: Context, e: Throwable): String {
    if (e !is BackupException) return e.message ?: e.javaClass.simpleName
    val res = when (e.reason) {
        BackupReason.NOT_BACKUP -> R.string.ctl_bak_err_not_backup
        BackupReason.NEWER_VERSION -> R.string.ctl_bak_err_newer
        BackupReason.TOO_LARGE -> R.string.ctl_bak_err_too_large
        BackupReason.TOO_MANY_FILES -> R.string.ctl_bak_err_too_many
        BackupReason.UNSAFE -> R.string.ctl_bak_err_unsafe
        BackupReason.DAMAGED -> R.string.ctl_bak_err_damaged
        BackupReason.CANNOT_READ -> R.string.ctl_bak_err_read
        BackupReason.CANNOT_WRITE -> R.string.ctl_bak_err_write
        BackupReason.BUSY -> R.string.ctl_bak_busy
        BackupReason.TERMINAL_RUNNING -> R.string.ctl_bak_err_terminal
        BackupReason.UNPACK_FAILED -> R.string.ctl_bak_err_unpack
        BackupReason.SAFETY_COPY_FAILED -> R.string.ctl_bak_err_safety
        BackupReason.HALFWAY -> R.string.ctl_bak_err_halfway
        BackupReason.OTHER -> null
    }
    return if (res != null) ctx.getString(res) else (e.message ?: "")
}

/** Removes a document that was created for a backup that then failed (or empties it when the provider cannot delete). Never throws. */
private fun discardDocument(ctx: Context, uri: Uri) {
    val deleted = try { DocumentsContract.deleteDocument(ctx.contentResolver, uri) } catch (_: Throwable) { false }
    if (!deleted) try { ctx.contentResolver.openOutputStream(uri, "wt")?.close() } catch (_: Throwable) { }
}

/** `agiri-backup-20261124-1830.zip` in the device's time. */
private fun defaultFileName(): String {
    val f = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
    return "agiri-backup-" + f.format(Date()) + ".zip"
}
