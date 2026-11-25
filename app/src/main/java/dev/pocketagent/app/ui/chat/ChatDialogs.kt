package dev.pocketagent.app.ui.chat

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ActionSheet
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.ConfirmDialog
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.SheetAction
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.voice.voiceOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pocketagent.app.data.Role as MsgRole
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.Texts

/**
 * Every menu, sheet and dialog of a chat. The launchers for the attach and export pickers sit at the top, always composed
 * (a launcher must not appear and disappear with a dialog). The messages are read at the moment a sheet opens or an action
 * runs; nothing here watches them.
 */
@Composable
internal fun ChatDialogs(state: ChatScreenState, conv: Conversation, shape: ChatShape) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = state.app
    val repo = state.repo
    val engine = state.engine
    val convId = conv.id
    val c = Pocket.colors
    val vm: MainViewModel = viewModel()

    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val ap by AppearanceStore.get(ctx).appearance.collectAsState()

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val memberBots = conv.botIds.mapNotNull { botsById[it] }
    val generating = convId in busySet
    val title = conversationTitle(conv, botsById)

    // ---- attachment pickers ----
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = state.pendingCameraFile
        state.pendingCameraFile = null
        if (ok && file != null && file.isFile) {
            state.attachImport {
                try { app.attachments.importImage(Uri.fromFile(file), file.name) }
                finally { file.delete() }
            }
        } else {
            file?.delete()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.attachImport { app.attachments.importImage(uri) }
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.attachImport { app.attachments.importDocument(uri) }
    }

    // ---- export to a file ----
    val exportLabels = ExportLabels(
        you = t(R.string.ui_export_you),
        tool = t(R.string.ui_export_tool),
        error = t(R.string.ui_export_error),
        bot = t(R.string.ui_export_bot),
    )
    val exportNoStream = t(R.string.ui_export_no_stream)
    val exportEmpty = t(R.string.ui_export_empty)
    val buildExport: () -> String = {
        exportMarkdown(title, state.messagesNow(), exportLabels) { m -> botsById[m.botId]?.name ?: exportLabels.bot }
    }
    // The text is NOT carried across the file picker (the process may die while it is open): it is rebuilt from the
    // stored messages when the picker returns the Uri.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            state.scope.launch {
                val failure: String? = withContext(Dispatchers.IO) {
                    try {
                        val snapshot = repo.messagesSnapshot(convId)
                        if (snapshot.isEmpty()) {
                            // The picker already created the file: do not leave an empty one behind.
                            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
                            return@withContext exportEmpty
                        }
                        val names = repo.bots.value.associateBy { it.id }
                        val text = exportMarkdown(title, snapshot, exportLabels) { m -> names[m.botId]?.name ?: exportLabels.bot }
                        val out = ctx.contentResolver.openOutputStream(uri, "wt")
                        if (out == null) {
                            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
                            return@withContext exportNoStream
                        }
                        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        null
                    } catch (e: Exception) {
                        e.message ?: e.javaClass.simpleName
                    }
                }
                if (failure == null) {
                    state.say(Texts.get(ctx, R.string.ui_export_saved), PetNoteKind.SUCCESS)
                } else {
                    state.say(Texts.get(ctx, R.string.ui_export_failed, failure), PetNoteKind.ERROR)
                }
            }
        }
    }

    // ---- menus and dialogs ----
    if (state.menu) {
        val acts = ArrayList<SheetAction>()
        acts += SheetAction(PixelIcon.SLIDERS, t(R.string.ui_ctrl_title)) { state.showControls = true }
        if (shape.count > 0) acts += SheetAction(PixelIcon.SEARCH, t(R.string.ui_chat_find)) { state.openSearch("") }
        acts += SheetAction(PixelIcon.SKILL, t(R.string.ui_ctrl_skills)) { state.skillPickerQuery = "" }
        if (shape.count > 0) acts += SheetAction(PixelIcon.FORK, t(R.string.ui_ctrl_fork)) { state.doFork(null) }
        if (shape.count > 0) acts += SheetAction(PixelIcon.SHARE, t(R.string.ui_act_export)) { state.exportMenu = true }
        acts += SheetAction(PixelIcon.EDIT, t(R.string.ui_act_rename)) { state.rename = true }
        acts += SheetAction(PixelIcon.PIN, t(if (conv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) {
            repo.setPinned(convId, !conv.pinned)
        }
        if (conv.isGroup) acts += SheetAction(PixelIcon.GROUP, t(R.string.ui_act_members)) { state.showMembers = true }
        acts += SheetAction(PixelIcon.SCROLL, t(R.string.ui_act_inspector)) { nav.go(Route.Hermes) }
        acts += SheetAction(PixelIcon.REFRESH, t(R.string.ui_act_clear), danger = true) { state.confirmClear = true }
        acts += SheetAction(PixelIcon.TRASH, t(R.string.ui_act_delete), danger = true) { state.confirmDelete = true }
        ActionSheet(title, acts) { state.menu = false }
    }

    state.actionsFor?.let { m ->
        val acts = ArrayList<SheetAction>()
        if (m.text.isNotBlank()) {
            val isSp = app.voice.speakingId.value == m.id
            acts += SheetAction(
                if (isSp) PixelIcon.STOP else PixelIcon.VOLUME,
                t(if (isSp) R.string.ui_act_stop_speak else R.string.ui_act_speak),
            ) {
                val b = m.botId?.let { botsById[it] } ?: if (m.role == MsgRole.BOT) memberBots.firstOrNull() else null
                app.voice.toggleSpeak(m.id, m.text, ap.language, b.voiceOverride())
            }
        }
        acts += SheetAction(PixelIcon.COPY, t(R.string.ui_act_copy)) {
            copyToClipboard(ctx, m.text)
            app.sounds.play(SoundCue.TAP)
        }
        if ((m.role == MsgRole.USER || m.role == MsgRole.BOT) && m.text.isNotBlank()) {
            val starredNow = state.messagesNow().firstOrNull { it.id == m.id }?.starred ?: m.starred
            acts += SheetAction(
                if (starredNow) PixelIcon.STAR else PixelIcon.STAR_OFF,
                t(if (starredNow) R.string.ui_act_unstar else R.string.ui_act_star),
            ) { repo.setStarred(convId, m.id, !starredNow) }
        }
        acts += SheetAction(PixelIcon.FORK, t(R.string.ui_act_fork)) { state.doFork(m.id) }
        acts += SheetAction(PixelIcon.SCROLL, t(R.string.ui_act_select)) { state.selectFor = m }
        acts += SheetAction(PixelIcon.SHARE, t(R.string.ui_act_share)) { shareText(ctx, m.text) }
        if (m.role == MsgRole.USER && !generating) {
            acts += SheetAction(PixelIcon.EDIT, t(R.string.ui_act_edit)) {
                // Park the half-typed draft; cancel or a finished edit gives it back.
                if (state.editing == null) state.preEditDraft = state.input
                state.editing = m; state.input = m.text
            }
        }
        if (m.role == MsgRole.BOT && m.id == shape.lastBotId && shape.lastBotIsLast && !generating) {
            acts += SheetAction(PixelIcon.REFRESH, t(R.string.ui_act_regen)) { engine.regenerateLast(convId) }
        }
        acts += SheetAction(PixelIcon.TRASH, t(R.string.ui_act_delete), danger = true) { repo.deleteMessage(convId, m.id) }
        ActionSheet(null, acts) { state.actionsFor = null }
    }

    if (state.exportMenu) {
        val failedLabel = t(R.string.ui_export_failed, "")
        ActionSheet(
            t(R.string.ui_export_title),
            listOf(
                SheetAction(PixelIcon.SHARE, t(R.string.ui_act_share)) { shareText(ctx, buildExport()) },
                SheetAction(PixelIcon.DOWNLOAD, t(R.string.ui_export_save)) {
                    try {
                        exportLauncher.launch(exportFileName(title))
                    } catch (e: Exception) {
                        state.say(failedLabel + (e.message ?: ""), PetNoteKind.ERROR)
                    }
                },
            ),
        ) { state.exportMenu = false }
    }

    state.selectFor?.let { m ->
        PocketDialog(
            title = t(R.string.ui_chat_select_title), onDismiss = { state.selectFor = null },
            confirm = { PixelButton(t(R.string.ui_dlg_close), onClick = { state.selectFor = null }) },
        ) {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(m.text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp) }
            }
        }
    }

    if (state.rename) {
        RenameDialog(
            initial = conv.title.ifBlank { title }, onSave = { repo.renameConversation(convId, it) },
            onDismiss = { state.rename = false },
        )
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = t(R.string.ui_dlg_delete_title), body = t(R.string.ui_dlg_delete_body, title),
            confirmLabel = t(R.string.ui_dlg_delete_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.deleteConversation(convId) },
            onDismiss = { state.confirmDelete = false },
            danger = true,
            closeOnConfirm = true,
        )
    }
    if (state.confirmClear) {
        ConfirmDialog(
            title = t(R.string.ui_dlg_clear_title), body = t(R.string.ui_dlg_clear_body),
            confirmLabel = t(R.string.ui_dlg_clear_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.clearConversation(convId) },
            onDismiss = { state.confirmClear = false },
            danger = true,
            closeOnConfirm = true,
        )
    }
    if (state.showMembers) {
        MembersDialog(conv, onDismiss = { state.showMembers = false })
    }
    if (state.showAttach) {
        AttachSheet(
            onCamera = {
                try {
                    val (file, uri) = app.attachments.createCameraCaptureTarget()
                    state.pendingCameraFile = file
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    state.say(Texts.get(ctx, R.string.ui_attach_failed, e.message ?: "camera"), PetNoteKind.ERROR)
                }
            },
            onGallery = { galleryLauncher.launch("image/*") },
            onDocument = { docLauncher.launch(arrayOf("*/*")) },
            onDismiss = { state.showAttach = false },
        )
    }
    state.previewImage?.let { att ->
        ImagePreviewDialog(att) { state.previewImage = null }
    }
    if (state.showControls) {
        ChatControlsSheet(
            conv = conv,
            modelSuggestions = ProviderConfig.suggestions(vm.authMode, vm.endpoint),
            onSelectMode = { mode ->
                repo.setMode(convId, mode)
                app.sounds.play(SoundCue.TAP)
            },
            onSelectOverrides = { model, effort ->
                repo.setOverrides(convId, model, effort)
                app.sounds.play(SoundCue.TAP)
            },
            onOpenSkills = { state.skillPickerQuery = "" },
            onForkChat = { state.doFork(null) },
            onDismiss = { state.showControls = false },
            usage = chatUsageTotals(state.messagesNow()),
        )
    }
    state.skillPickerQuery?.let { initialQ ->
        SkillPickerSheet(
            initialQuery = initialQ,
            onUseSkill = { skill ->
                val prompt = Texts.get(ctx, R.string.ui_skills_picker_prompt, skill.name)
                state.input = if (state.input.isBlank()) prompt else "${state.input}\n$prompt"
                app.sounds.play(SoundCue.TAP)
            },
            onOpenInspector = { nav.go(Route.Hermes) },
            onDismiss = { state.skillPickerQuery = null },
        )
    }
    state.toolOutputFor?.let { id ->
        ToolOutputSheet(id, state.messagesFlow) { state.toolOutputFor = null }
    }
    state.pendingTerminalCmd?.let { cmd ->
        PocketDialog(
            title = t(R.string.ui_run_term_title),
            onDismiss = { state.pendingTerminalCmd = null },
            confirm = {
                PixelButton(
                    t(R.string.ui_chat_code_run),
                    onClick = {
                        state.pendingTerminalCmd = null
                        app.sounds.play(SoundCue.TAP)
                        app.terminal.run(cmd)
                        nav.go(Route.Terminal)
                    },
                    icon = PixelIcon.PLAY,
                )
            },
            dismiss = {
                PixelButton(t(R.string.ui_dlg_cancel), onClick = { state.pendingTerminalCmd = null }, kind = ButtonKind.GHOST)
            },
        ) {
            Text(t(R.string.ui_run_term_body), color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Box(
                Modifier.fillMaxWidth().background(c.codeBg).border(1.dp, c.outline).heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()).padding(8.dp),
            ) {
                Text(cmd, color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Group members (tap to add or remove a bot) and who answers. */
@Composable
private fun MembersDialog(conv: Conversation, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { PocketApp.from(ctx).chat }
    val bots by repo.bots.collectAsState()
    val c = Pocket.colors
    PocketDialog(
        title = t(R.string.ui_chat_members_title), onDismiss = onDismiss,
        confirm = { PixelButton(t(R.string.ui_dlg_close), onClick = onDismiss) },
    ) {
        Text(t(R.string.ui_chat_mode_title), color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketChip(
                t(R.string.ui_new_mode_all), conv.groupMode == GroupMode.ALL_IN_TURN,
                { repo.setGroupMode(conv.id, GroupMode.ALL_IN_TURN) }, Modifier.weight(1f),
            )
            PocketChip(
                t(R.string.ui_new_mode_mention), conv.groupMode == GroupMode.MENTION_ONLY,
                { repo.setGroupMode(conv.id, GroupMode.MENTION_ONLY) }, Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            bots.forEach { b ->
                val member = b.id in conv.botIds
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Checkbox) {
                            val next = if (member) conv.botIds - b.id else conv.botIds + b.id
                            if (next.isNotEmpty()) repo.setConversationBots(conv.id, next)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotAvatar(b, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(b.name, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (member) PixelIconView(PixelIcon.CHECK, tint = c.success, size = 20.dp)
                }
            }
        }
    }
}
