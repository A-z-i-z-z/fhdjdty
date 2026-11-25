package dev.pocketagent.app.ui.hermes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.hermes.HermesHome
import dev.pocketagent.app.hermes.LogKind
import dev.pocketagent.app.hermes.SkillInfo
import dev.pocketagent.app.hermes.SkillTemplate
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.ConfirmDialog
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.ChipRow
import dev.pocketagent.app.ui.settings.ErrorText
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.settings.copyText
import dev.pocketagent.app.ui.settings.shareText
import dev.pocketagent.app.ui.settings.toast
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.Texts

// ---- Skills --------------------------------------------------------------------------------------------------

@Composable
internal fun SkillsTab(hvm: HermesEditorsViewModel, installed: Boolean, running: Boolean, onRestart: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = hvm.home
    var skills by remember { mutableStateOf<List<SkillInfo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var newError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SkillInfo?>(null) }
    val createdMsg = t(R.string.ctl_skill_created)
    val deletedMsg = t(R.string.ctl_skill_deleted)
    val badNameMsg = t(R.string.ctl_skill_name_bad)
    val existsMsg = t(R.string.ctl_skill_exists)

    LaunchedEffect(installed, reload) {
        skills = null; failed = false
        skills = withContext(Dispatchers.IO) {
            try { home.listSkills() } catch (e: Exception) { failed = true; emptyList() }
        }
    }

    if (showNew) NewSkillDialog(
        error = newError,
        onDismiss = { showNew = false; newError = null },
        onCreate = { category, name, description ->
            newError = null
            scope.launch {
                var created: SkillInfo? = null
                var error: String? = null
                withContext(Dispatchers.IO) {
                    try { created = home.createSkill(category, name, description) }
                    catch (e: IllegalArgumentException) { error = badNameMsg }
                    catch (e: java.io.IOException) { error = existsMsg }
                    catch (e: Exception) { error = Texts.get(ctx, R.string.ctl_skill_create_failed, e.message ?: e.javaClass.simpleName) }
                }
                val info = created
                if (info != null) { showNew = false; reload++; hvm.openSkill = info; toast(ctx, createdMsg) } else newError = error
            }
        },
    )
    confirmDelete?.let { s ->
        ConfirmDialog(
            title = t(R.string.ctl_skill_delete_title, s.name), body = t(R.string.ctl_skill_delete_body),
            confirmLabel = t(R.string.ctl_skill_delete), danger = true,
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        try { home.deleteSkill(s.path); null } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
                    }
                    if (err == null) { hvm.forgetSkill(s.path); reload++; toast(ctx, deletedMsg) }
                    else toast(ctx, Texts.get(ctx, R.string.ctl_skill_delete_failed, err), PetNoteKind.ERROR)
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }

    val current = hvm.openSkill
    if (current != null) {
        val state = hvm.skillState(current)
        LaunchedEffect(state) { if (!state.loaded) state.reload() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(t(R.string.ctl_skills_back), onClick = { hvm.openSkill = null }, icon = PixelIcon.BACK, kind = ButtonKind.GHOST)
                CText(current.name, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                PixelIconButton(
                    PixelIcon.TRASH, onClick = { confirmDelete = current },
                    modifier = Modifier.testTag(RobotTags.HERMES_SKILL_DELETE),
                    contentDescription = t(R.string.ctl_skill_delete), tint = Pocket.colors.error,
                )
            }
            DocEditor(
                state, info = current.path, explain = current.description.ifBlank { t(R.string.ctl_skill_explain) },
                installed = installed, running = running, onRestart = onRestart, modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val all = skills
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(t(R.string.ctl_skills_explain))
            PixelButton(
                t(R.string.ctl_skill_new), onClick = { newError = null; showNew = true },
                modifier = Modifier.testTag(RobotTags.HERMES_SKILL_NEW), icon = PixelIcon.PLUS, kind = ButtonKind.SECONDARY, enabled = installed,
            )
            PocketTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = t(R.string.ctl_skills_search), singleLine = true)
        }
        when {
            all == null -> Muted(t(R.string.ctl_loading), Modifier.padding(12.dp))
            failed -> CText(t(R.string.ctl_skills_failed), color = Pocket.colors.error, size = 13.sp, modifier = Modifier.padding(12.dp))
            all.isEmpty() -> Muted(t(if (installed) R.string.ctl_skills_empty else R.string.ctl_doc_not_installed), Modifier.padding(12.dp))
            else -> {
                val q = query.trim().lowercase()
                val shown = if (q.isEmpty()) all else all.filter {
                    it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.category.lowercase().contains(q)
                }
                val groups = shown.groupBy { it.category.ifBlank { "general" } }.toSortedMap()
                if (shown.isEmpty()) Muted(t(R.string.ctl_skills_no_match), Modifier.padding(12.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (category, list) ->
                        item(key = "h-$category") { SectionHeader("$category (${list.size})") }
                        items(list, key = { it.path }) { skill ->
                            PixelPanel(Modifier.fillMaxWidth().testTag(RobotTags.hermesSkillRow(skill.name)).clickable(onClickLabel = t(R.string.ctl_skills_open)) { hvm.openSkill = skill }) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    CText(skill.name + if (hvm.skillDirty(skill.path)) " •" else "", size = 15.sp, weight = FontWeight.SemiBold)
                                    if (skill.description.isNotBlank()) Muted(skill.description, size = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Name, description and category of a new skill; the SKILL.md itself comes from [SkillTemplate]. */
@Composable
private fun NewSkillDialog(error: String?, onDismiss: () -> Unit, onCreate: (category: String, name: String, description: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("custom") }
    val nameOk = SkillTemplate.isValidName(name.trim())
    val catOk = category.isBlank() || SkillTemplate.isValidName(category.trim())
    PocketDialog(
        title = t(R.string.ctl_skill_new_title), onDismiss = onDismiss,
        confirm = {
            PixelButton(t(R.string.ctl_skill_create), enabled = nameOk && catOk, onClick = { onCreate(category.trim(), name.trim(), description.trim()) }, modifier = Modifier.testTag(RobotTags.HERMES_SKILL_CREATE))
        },
        dismiss = { PixelButton(t(R.string.cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        Muted(t(R.string.ctl_skill_new_help))
        PocketTextField(name, { name = it.take(SkillTemplate.MAX_NAME) }, Modifier.fillMaxWidth().testTag(RobotTags.HERMES_SKILL_NAME), label = t(R.string.ctl_skill_f_name), singleLine = true, monospace = true)
        if (name.isNotEmpty() && !nameOk) ErrorText(t(R.string.ctl_skill_name_bad))
        PocketTextField(description, { description = it.take(SkillTemplate.MAX_DESCRIPTION) }, Modifier.fillMaxWidth(), label = t(R.string.ctl_skill_f_desc), minLines = 2, maxLines = 4)
        PocketTextField(category, { category = it.take(SkillTemplate.MAX_NAME) }, Modifier.fillMaxWidth(), label = t(R.string.ctl_skill_f_category), singleLine = true, monospace = true)
        if (!catOk) ErrorText(t(R.string.ctl_skill_name_bad))
        if (error != null) ErrorText(error)
    }
}

// ---- Config (read-only) ------------------------------------------------------------------------------------

@Composable
internal fun ConfigTab(home: HermesHome, installed: Boolean, onOpenProvider: () -> Unit) {
    val ctx = LocalContext.current
    val state = remember { DocState({ home.read(HermesDoc.CONFIG) }, { }) }
    LaunchedEffect(installed) { state.reload() }
    val copied = t(R.string.ctl_copied)
    val c = Pocket.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(t(R.string.ctl_config_explain))
        val info = remember(state.original) { runCatching { home.docInfo(HermesDoc.CONFIG) }.getOrDefault("") }
        CText(info, color = c.textMuted, size = 11.sp, mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(t(R.string.ctl_config_open_provider), onClick = onOpenProvider, icon = PixelIcon.GEAR, kind = ButtonKind.SECONDARY)
            PixelButton(t(R.string.ctl_copy), onClick = { copyText(ctx, "config.yaml", state.text); toast(ctx, copied) }, icon = PixelIcon.COPY, kind = ButtonKind.GHOST, enabled = state.text.isNotEmpty())
        }
        when {
            state.loading && !state.loaded -> Muted(t(R.string.ctl_loading))
            state.error != null -> CText(t(R.string.ctl_doc_error, state.error ?: ""), color = c.error, size = 13.sp)
            !state.exists -> Muted(t(if (installed) R.string.ctl_doc_missing else R.string.ctl_doc_not_installed))
            else -> PixelPanel(Modifier.fillMaxWidth(), color = c.codeBg) {
                SelectionContainer {
                    CText(state.text, color = c.text, size = 12.sp, mono = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }
}

// ---- Logs ----------------------------------------------------------------------------------------------------

@Composable
internal fun LogsTab(home: HermesHome) {
    val ctx = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(LogKind.APP.name) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    val c = Pocket.colors

    LaunchedEffect(kind, reload) {
        loading = true
        text = withContext(Dispatchers.IO) {
            try { home.tailLog(LogKind.valueOf(kind), 80_000) } catch (e: Exception) { "(" + (e.message ?: e.javaClass.simpleName) + ")" }
        }
        loading = false
    }
    val lines = remember(text) { if (text.isEmpty()) emptyList() else text.lines().takeLast(1500) }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, kind) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    val copied = t(R.string.ctl_copied)

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow {
            PocketChip(t(R.string.ctl_log_app), selected = kind == LogKind.APP.name, onClick = { kind = LogKind.APP.name })
            PocketChip(t(R.string.ctl_log_runtime), selected = kind == LogKind.RUNTIME.name, onClick = { kind = LogKind.RUNTIME.name })
            PocketChip(t(R.string.ctl_log_api), selected = kind == LogKind.API.name, onClick = { kind = LogKind.API.name })
        }
        Muted(t(when (kind) {
            LogKind.RUNTIME.name -> R.string.ctl_log_runtime_help
            LogKind.API.name -> R.string.ctl_log_api_help
            else -> R.string.ctl_log_app_help
        }), size = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(t(R.string.ctl_refresh), onClick = { reload++ }, icon = PixelIcon.REFRESH, kind = ButtonKind.SECONDARY)
            PixelButton(t(R.string.ctl_copy), onClick = { copyText(ctx, "PocketAgent log", text); toast(ctx, copied) }, icon = PixelIcon.COPY, kind = ButtonKind.GHOST, enabled = text.isNotEmpty())
            PixelButton(t(R.string.ctl_share), onClick = { shareText(ctx, "PocketAgent log", text) }, icon = PixelIcon.SHARE, kind = ButtonKind.GHOST, enabled = text.isNotEmpty())
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(c.codeBg)) {
            if (lines.isEmpty()) {
                Muted(t(if (loading) R.string.ctl_loading else R.string.ctl_log_empty), Modifier.padding(12.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(8.dp)) {
                    items(lines.size) { i ->
                        CText(lines[i], color = c.text, size = 11.sp, mono = true, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}
