package dev.pocketagent.app.ui.bots

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotShare
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.components.ConfirmDialog
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.settings.toast
import dev.pocketagent.app.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn
import dev.pocketagent.app.ui.text.Texts

/** Largest bot file the import reads (50 bots with the longest allowed prompt are about 1 MB). */
private const val MAX_IMPORT_BYTES = 3_000_000

/** Largest text sent through the share sheet: bigger payloads can fail inside Android, so those go to a file. */
private const val MAX_SHARE_CHARS = 150_000

/** How the bots of this screen leave the app: into a file the user picks, or as text through the share sheet. */
internal class BotExporter(val saveToFile: (List<Bot>) -> Unit, val share: (List<Bot>) -> Unit)

/** The kinds of problem [dev.pocketagent.app.data.BotShare.decode] reports, so the UI can show them in the user's language. */
internal enum class BotImportProblem { EMPTY, TOO_LARGE, NOT_JSON, NOT_BOTS, NEWER, NONE, TOO_MANY, FIELD, MALFORMED, OTHER }

/**
 * Sorts the English reason of an `IllegalArgumentException` from `BotShare.decode` into a [BotImportProblem]. `BotShare` belongs to
 * another area and has no reason codes, so this reads its (fixed) sentences; BotShareUiTest feeds real `decode` failures through it,
 * so a changed sentence shows up as a failing test rather than as a silently English message.
 */
internal fun botImportProblem(message: String?): BotImportProblem {
    val m = message.orEmpty()
    return when {
        m == "The file is empty." -> BotImportProblem.EMPTY
        m == "The file is too large." -> BotImportProblem.TOO_LARGE
        m.contains("not valid JSON") -> BotImportProblem.NOT_JSON
        m.contains("not an Agiri bots file") -> BotImportProblem.NOT_BOTS
        m.contains("newer version") -> BotImportProblem.NEWER
        m == "The file contains no bots." -> BotImportProblem.NONE
        m.startsWith("The file contains more than") -> BotImportProblem.TOO_MANY
        m.contains("longer than") || m.endsWith("has no name.") -> BotImportProblem.FIELD
        m == "The file has no list of bots." || m.endsWith("is not an object.") -> BotImportProblem.MALFORMED
        else -> BotImportProblem.OTHER
    }
}

private fun botImportProblemRes(p: BotImportProblem): Int? = when (p) {
    BotImportProblem.EMPTY -> R.string.ctl_bots_err_empty
    BotImportProblem.TOO_LARGE -> R.string.ctl_bots_import_too_large
    BotImportProblem.NOT_JSON -> R.string.ctl_bots_err_json
    BotImportProblem.NOT_BOTS -> R.string.ctl_bots_err_not_bots
    BotImportProblem.NEWER -> R.string.ctl_bots_err_newer
    BotImportProblem.NONE -> R.string.ctl_bots_import_none
    BotImportProblem.TOO_MANY -> R.string.ctl_bots_err_many
    BotImportProblem.FIELD -> R.string.ctl_bots_err_field
    BotImportProblem.MALFORMED -> R.string.ctl_bots_err_malformed
    BotImportProblem.OTHER -> null
}

/** The reason text of a failed import in the app language; unknown reasons fall back to the original message. */
private fun importReasonText(ctx: Context, message: String?): String {
    val res = botImportProblemRes(botImportProblem(message))
    return if (res != null) Texts.get(ctx, res) else (message ?: "")
}

/** A file name such as `kawa.agiri-bots.json` (one bot) or `my-bots.agiri-bots.json`. */
internal fun botFileName(bots: List<Bot>): String {
    val base = if (bots.size == 1) {
        bots[0].name.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").take(40)
    } else ""
    return base.ifEmpty { "my-bots" } + BotShare.FILE_SUFFIX
}

@Composable
internal fun rememberBotExporter(): BotExporter {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val savedMsg = t(R.string.ctl_bots_exported)
    val failedMsg = t(R.string.ctl_bots_export_failed)
    val tooBigMsg = t(R.string.ctl_bots_export_too_big)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pending
        pending = null
        if (uri != null && text != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast(ctx, savedMsg)
            } catch (_: Exception) { toast(ctx, failedMsg, PetNoteKind.ERROR) }
        }
    }
    return remember(launcher) {
        BotExporter(
            saveToFile = { bots -> if (bots.isNotEmpty()) { pending = BotShare.encode(bots); launcher.launch(botFileName(bots)) } },
            share = { bots ->
                val text = if (bots.isEmpty()) "" else BotShare.encode(bots)
                when {
                    text.isEmpty() -> Unit
                    text.length > MAX_SHARE_CHARS -> toast(ctx, tooBigMsg, PetNoteKind.ERROR)
                    else -> try {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, botFileName(bots)).putExtra(Intent.EXTRA_TEXT, text)
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { toast(ctx, failedMsg, PetNoteKind.ERROR) }
                }
            },
        )
    }
}

/**
 * The "Share bots" block of the Bots screen: export all of the user's own bots (file or share sheet) and import bots from a file
 * or from the clipboard. An import always asks first, lists the names, and adds the bots as copies with new ids.
 */
@Composable
internal fun BotShareSection() {
    val ctx = LocalContext.current
    val repo = remember { PocketApp.from(ctx).chat }
    val bots by repo.bots.collectAsState()
    val mine = remember(bots) { bots.filter { !it.builtIn } }
    val exporter = rememberBotExporter()
    val scope = rememberCoroutineScope()
    var incoming by remember { mutableStateOf<List<Bot>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val emptyClip = t(R.string.ctl_bots_import_empty)

    fun parse(text: String) {
        try {
            val list = BotShare.decode(text)
            if (list.isEmpty()) error = Texts.get(ctx, R.string.ctl_bots_import_failed, Texts.get(ctx, R.string.ctl_bots_import_none)) else incoming = list
        } catch (e: IllegalArgumentException) {
            error = Texts.get(ctx, R.string.ctl_bots_import_failed, importReasonText(ctx, e.message))
        } catch (e: Exception) {
            error = Texts.get(ctx, R.string.ctl_bots_import_failed, e.message ?: e.javaClass.simpleName)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { String(FileUtil.readLimited(it, MAX_IMPORT_BYTES + 1), Charsets.UTF_8) }
                } catch (_: Exception) { null }
            }
            if (text == null) error = Texts.get(ctx, R.string.ctl_bots_import_failed, "?")
            else if (text.length > MAX_IMPORT_BYTES) error = Texts.get(ctx, R.string.ctl_bots_import_failed, Texts.get(ctx, R.string.ctl_bots_import_too_large))
            else parse(text)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(t(R.string.ctl_bots_share_title))
        Muted(t(R.string.ctl_bots_share_help))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                t(R.string.ctl_bots_export_file), onClick = { exporter.saveToFile(mine) },
                modifier = Modifier.weight(1f).testTag(RobotTags.BOTS_EXPORT), icon = PixelIcon.UPLOAD, kind = ButtonKind.SECONDARY, enabled = mine.isNotEmpty(),
            )
            PixelButton(
                t(R.string.ctl_bots_export_share), onClick = { exporter.share(mine) },
                modifier = Modifier.weight(1f), icon = PixelIcon.SHARE, kind = ButtonKind.SECONDARY, enabled = mine.isNotEmpty(),
            )
        }
        if (mine.isEmpty()) Muted(t(R.string.ctl_bots_export_none), size = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                t(R.string.ctl_bots_import_file),
                onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f).testTag(RobotTags.BOTS_IMPORT), icon = PixelIcon.DOWNLOAD, kind = ButtonKind.SECONDARY,
            )
            PixelButton(
                t(R.string.ctl_bots_import_clip),
                onClick = {
                    val text = clipboardText(ctx)
                    if (text.isNullOrBlank()) error = emptyClip else parse(text)
                },
                modifier = Modifier.weight(1f), icon = PixelIcon.COPY, kind = ButtonKind.SECONDARY,
            )
        }
    }

    incoming?.let { list ->
        val names = list.take(8).joinToString("\n") { "• " + it.name } + if (list.size > 8) "\n…" else ""
        ConfirmDialog(
            title = tn(R.plurals.ctl_bots_import_title, list.size, list.size),
            body = t(R.string.ctl_bots_import_body, names),
            confirmLabel = t(R.string.ctl_bots_import_confirm),
            onConfirm = {
                val now = System.currentTimeMillis()
                list.forEach { repo.saveBot(it.copy(id = repo.newId(), builtIn = false, createdAt = now)) }
                incoming = null
                toast(ctx, Texts.getQuantity(ctx, R.plurals.ctl_bots_imported, list.size, list.size))
            },
            onDismiss = { incoming = null },
        )
    }
    error?.let { msg ->
        PocketDialog(
            title = t(R.string.ctl_bots_import_error_title), onDismiss = { error = null },
            confirm = { PixelButton(t(R.string.ok), onClick = { error = null }) },
        ) { CText(msg) }
    }
}

private fun clipboardText(ctx: Context): String? = try {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(ctx)?.toString()
} catch (_: Exception) { null }
