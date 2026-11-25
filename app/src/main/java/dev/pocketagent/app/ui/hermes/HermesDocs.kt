package dev.pocketagent.app.ui.hermes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.hermes.MemoryFormat
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.ConfirmDialog
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.ChipRow
import dev.pocketagent.app.ui.settings.CtlCard
import dev.pocketagent.app.ui.settings.ErrorText
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn

/** Hermes's memory files hold entries separated by a fixed delimiter; the format facts live in ENGINE's [MemoryFormat]. */
internal const val MEMORY_BUDGET = MemoryFormat.MEMORY_LIMIT
internal const val USER_BUDGET = MemoryFormat.USER_LIMIT

internal fun parseEntries(text: String): List<String> = MemoryFormat.entries(text)

internal fun joinEntries(entries: List<String>): String = MemoryFormat.join(entries)

/** Working copy of one text file: what is on disk (`original`) and what the user is typing (`text`). */
internal class DocState(private val loader: () -> String?, private val saver: (String) -> Unit) {
    var original by mutableStateOf("")
    var text by mutableStateOf("")
    var exists by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var justSaved by mutableStateOf(false)
    val dirty: Boolean get() = loaded && text != original

    suspend fun reload() {
        loading = true; error = null; justSaved = false
        try {
            val r = withContext(Dispatchers.IO) { loader() }
            exists = r != null; original = r ?: ""; text = original; loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }

    /** True when the file on disk changed since it was loaded (Hermes edits its own memory); the save was not done. */
    var conflict by mutableStateOf(false)

    /** Writes [text]. Unless [force], first checks that the file still holds what was loaded; otherwise raises [conflict]. */
    suspend fun save(force: Boolean = false) {
        error = null
        val toWrite = text
        try {
            val clash = withContext(Dispatchers.IO) {
                if (!force && (loader() ?: "") != original) true else { saver(toWrite); false }
            }
            if (clash) { conflict = true; return }
            conflict = false
            original = toWrite; exists = true; justSaved = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun revert() { text = original; justSaved = false; error = null; conflict = false }
}

/**
 * Editor for one Hermes text file (SOUL.md, MEMORY.md, USER.md or a skill): explanation, dirty indicator,
 * optional character budget, entries view for the memory files, save with confirmation, revert and restart.
 */
@Composable
internal fun DocEditor(
    state: DocState,
    info: String,
    explain: String,
    installed: Boolean,
    running: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    budget: Int? = null,
    entries: Boolean = false,
    restartNeeded: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var rawMode by remember { mutableStateOf(!entries) }
    var confirmSave by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }   // index into the entries; -1 = a new entry
    var editText by remember { mutableStateOf("") }
    val c = Pocket.colors

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(explain)
        if (info.isNotBlank()) CText(info, color = c.textMuted, size = 11.sp, mono = true)
        if (!installed) Muted(t(R.string.ctl_doc_not_installed), size = 13.sp)
        if (state.loading && !state.loaded) Muted(t(R.string.ctl_loading))
        state.error?.let { ErrorText(t(R.string.ctl_doc_error, it)) }
        if (state.loaded && !state.exists && !state.dirty) Muted(t(R.string.ctl_doc_missing), size = 13.sp)

        if (budget != null) BudgetMeter(joinEntries(parseEntries(state.text)).length, budget)

        // ---- status + mode ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.dirty -> CText(t(R.string.ctl_unsaved), color = c.warning, size = 13.sp, modifier = Modifier.weight(1f))
                state.justSaved -> CText(t(R.string.ctl_saved), color = c.success, size = 13.sp, modifier = Modifier.weight(1f))
                else -> Box(Modifier.weight(1f))
            }
            if (entries) {
                PocketChip(t(R.string.ctl_mode_entries), selected = !rawMode, onClick = { rawMode = false })
                PocketChip(t(R.string.ctl_mode_raw), selected = rawMode, onClick = { rawMode = true })
            }
        }

        // ---- body ----
        if (!entries || rawMode) {
            PocketTextField(
                state.text, { state.text = it; state.justSaved = false }, Modifier.fillMaxWidth().testTag(RobotTags.HERMES_DOC_EDITOR),
                minLines = 12, monospace = true, placeholder = t(R.string.ctl_doc_placeholder),
            )
            Muted(tn(R.plurals.ctl_chars, state.text.length, state.text.length), size = 12.sp)
        } else {
            val list = parseEntries(state.text)
            if (list.isEmpty()) Muted(t(R.string.ctl_entries_empty), size = 13.sp)
            list.forEachIndexed { i, entry ->
                PixelPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CText(entry, size = 14.sp, modifier = Modifier.weight(1f).padding(end = 4.dp))
                        Column {
                            PixelIconButton(PixelIcon.EDIT, onClick = { editing = i; editText = entry }, contentDescription = t(R.string.ctl_entry_edit))
                            PixelIconButton(PixelIcon.TRASH, onClick = {
                                state.text = joinEntries(list.filterIndexed { j, _ -> j != i }); state.justSaved = false
                            }, contentDescription = t(R.string.ctl_entry_delete), tint = c.error)
                        }
                    }
                }
            }
            PixelButton(t(R.string.ctl_entry_add), onClick = { editing = -1; editText = "" }, icon = PixelIcon.PLUS, kind = ButtonKind.SECONDARY)
        }

        // ---- actions ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelButton(t(R.string.ctl_revert), onClick = { state.revert() }, kind = ButtonKind.GHOST, enabled = state.dirty)
            PixelButton(t(R.string.ctl_save), onClick = { confirmSave = true }, modifier = Modifier.testTag(RobotTags.HERMES_DOC_SAVE), icon = PixelIcon.CHECK, enabled = state.dirty && installed)
        }
        if (state.justSaved && restartNeeded) {
            CtlCard(color = c.accentSoft, border = c.accent) {
                if (running) {
                    CText(t(R.string.ctl_restart_to_apply_body), size = 14.sp)
                    PixelButton(t(R.string.ctl_restart_to_apply), onClick = onRestart, icon = PixelIcon.REFRESH)
                } else {
                    CText(t(R.string.ctl_applies_next_start), size = 14.sp)
                }
            }
        }
        Box(Modifier.height(24.dp))
    }

    if (confirmSave) ConfirmDialog(
        title = t(R.string.ctl_save_confirm_title), body = t(R.string.ctl_save_confirm_body),
        confirmLabel = t(R.string.ctl_save),
        onConfirm = { confirmSave = false; scope.launch { state.save() } }, onDismiss = { confirmSave = false },
    )
    if (state.conflict) PocketDialog(
        title = t(R.string.ctl_conflict_title), onDismiss = { state.conflict = false },
        confirm = { PixelButton(t(R.string.ctl_conflict_overwrite), kind = ButtonKind.DANGER, onClick = { scope.launch { state.save(force = true) } }) },
        dismiss = { PixelButton(t(R.string.cancel), onClick = { state.conflict = false }, kind = ButtonKind.GHOST) },
    ) {
        CText(t(R.string.ctl_conflict_body))
        PixelButton(t(R.string.ctl_conflict_reload), kind = ButtonKind.SECONDARY, icon = PixelIcon.REFRESH,
            onClick = { state.conflict = false; scope.launch { state.reload() } })
    }
    editing?.let { idx ->
        PocketDialog(
            title = t(if (idx < 0) R.string.ctl_entry_add else R.string.ctl_entry_edit),
            onDismiss = { editing = null },
            confirm = {
                PixelButton(t(R.string.ctl_ok_apply), enabled = editText.isNotBlank(), onClick = {
                    val list = parseEntries(state.text).toMutableList()
                    val cleaned = editText.replace("§", "").trim()
                    if (idx < 0) list.add(cleaned) else if (idx < list.size) list[idx] = cleaned
                    state.text = joinEntries(list.filter { it.isNotBlank() }); state.justSaved = false
                    editing = null
                })
            },
            dismiss = { PixelButton(t(R.string.cancel), onClick = { editing = null }, kind = ButtonKind.GHOST) },
        ) {
            PocketTextField(editText, { editText = it }, Modifier.fillMaxWidth(), minLines = 4, maxLines = 10)
        }
    }
}

/** "1,230 / 2,200 characters" with a bar that turns warning/error colour near and over the budget. */
@Composable
internal fun BudgetMeter(used: Int, budget: Int) {
    val c = Pocket.colors
    val fraction = (used.toFloat() / budget).coerceIn(0f, 1f)
    val color = when {
        used > budget -> c.error
        used > budget * 0.85 -> c.warning
        else -> c.success
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(c.surfaceHigh).border(1.dp, c.outline)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
        CText(t(R.string.ctl_budget, used, budget), color = if (used > budget) c.error else c.textMuted, size = 12.sp)
        if (used > budget) ErrorText(t(R.string.ctl_budget_over))
    }
}
