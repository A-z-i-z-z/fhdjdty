package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The "Chats" tab: search, pinned chats, all chats, and a button to start a new chat or group. */
@Composable
fun ChatListScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val convs by repo.conversations.collectAsState()
    val bots by repo.bots.collectAsState()
    val busy by app.engine.busy.collectAsState()
    val c = Pocket.colors
    val botsById = remember(bots) { bots.associateBy { it.id } }
    val untitled = stringResource(R.string.ui_chats_untitled)

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var showNew by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    var renameFor by remember { mutableStateOf<Conversation?>(null) }
    var deleteFor by remember { mutableStateOf<Conversation?>(null) }
    val q = query.trim()

    // Message search runs off the main thread, a moment after the last keystroke.
    val hits by produceState(emptyList<Pair<Conversation, Message>>(), q) {
        if (q.isEmpty()) value = emptyList()
        else {
            delay(250)
            value = withContext(Dispatchers.Default) { repo.search(q).take(60) }
        }
    }

    val filteredConvs = remember(convs, filter) {
        when (filter) {
            "PINNED" -> convs.filter { it.pinned }
            "GROUPS" -> convs.filter { it.isGroup }
            "DIRECT" -> convs.filter { !it.isGroup }
            "MODES" -> convs.filter { it.mode != ChatMode.NORMAL || it.modelOverride.isNotBlank() || it.effortOverride.isNotBlank() }
            else -> convs
        }
    }
    val matchingConvs = remember(filteredConvs, botsById, q) {
        if (q.isEmpty()) filteredConvs
        else filteredConvs.filter { cv ->
            titleOf(cv, botsById, untitled).contains(q, ignoreCase = true) ||
                cv.botIds.any { botsById[it]?.name?.contains(q, ignoreCase = true) == true }
        }
    }
    val openChat: (Conversation) -> Unit = { nav.go(Route.Chat(it.id)) }
    val longPress: (Conversation) -> Unit = { menuFor = it }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PocketTopBar(title = stringResource(R.string.ui_chats_title))
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PocketTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.ui_chats_search), singleLine = true,
                )
                if (query.isNotEmpty()) {
                    PixelIconButton(PixelIcon.CLOSE, onClick = { query = "" }, contentDescription = stringResource(R.string.ui_dlg_close))
                } else Spacer(Modifier.width(8.dp))
            }
            if (convs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PocketChip(stringResource(R.string.ui_filter_all), filter == "ALL", { filter = "ALL" })
                    PocketChip(stringResource(R.string.ui_filter_pinned), filter == "PINNED", { filter = "PINNED" }, icon = PixelIcon.PIN)
                    PocketChip(stringResource(R.string.ui_filter_groups), filter == "GROUPS", { filter = "GROUPS" }, icon = PixelIcon.GROUP)
                    PocketChip(stringResource(R.string.ui_filter_direct), filter == "DIRECT", { filter = "DIRECT" }, icon = PixelIcon.USER)
                    PocketChip(stringResource(R.string.ui_filter_modes), filter == "MODES", { filter = "MODES" }, icon = PixelIcon.BOLT)
                }
            }
            when {
                convs.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        stringResource(R.string.ui_chats_empty_title), stringResource(R.string.ui_chats_empty_body),
                        action = { PixelButton(stringResource(R.string.ui_chats_new), onClick = { showNew = true }, icon = PixelIcon.PLUS) },
                    )
                }
                q.isEmpty() && matchingConvs.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(R.string.ui_filter_empty), color = c.textMuted, fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                q.isNotEmpty() && matchingConvs.isEmpty() && hits.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(R.string.ui_chats_no_results, q), color = c.textMuted, fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (q.isEmpty()) {
                        val pinned = matchingConvs.filter { it.pinned }
                        val rest = matchingConvs.filter { !it.pinned }
                        if (pinned.isNotEmpty()) {
                            item(key = "h-pinned") { ListLabel(stringResource(R.string.ui_chats_pinned)) }
                            items(pinned, key = { it.id }) { cv ->
                                ConversationRow(cv, botsById, cv.id in busy, { openChat(cv) }, { longPress(cv) })
                            }
                            if (rest.isNotEmpty()) item(key = "h-all") { ListLabel(stringResource(R.string.ui_chats_all)) }
                        }
                        items(rest, key = { it.id }) { cv ->
                            ConversationRow(cv, botsById, cv.id in busy, { openChat(cv) }, { longPress(cv) })
                        }
                    } else {
                        if (matchingConvs.isNotEmpty()) {
                            item(key = "h-convs") { ListLabel(stringResource(R.string.ui_chats_found_convs)) }
                            items(matchingConvs, key = { "c-" + it.id }) { cv ->
                                ConversationRow(cv, botsById, cv.id in busy, { openChat(cv) }, { longPress(cv) })
                            }
                        }
                        if (hits.isNotEmpty()) {
                            item(key = "h-msgs") { ListLabel(stringResource(R.string.ui_chats_matches)) }
                            items(hits, key = { (cv, m) -> "m-" + cv.id + "-" + m.id }) { (cv, m) ->
                                HitRow(cv, m, q, botsById, untitled) { openChat(cv) }
                            }
                        }
                    }
                }
            }
        }
        if (convs.isNotEmpty()) {
            PixelButton(
                stringResource(R.string.ui_chats_new), onClick = { showNew = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), icon = PixelIcon.PLUS,
            )
        }
    }

    if (showNew) {
        NewChatSheet(initialGroup = false, onDismiss = { showNew = false }, onCreated = { id -> showNew = false; nav.go(Route.Chat(id)) })
    }
    menuFor?.let { cv ->
        val name = titleOf(cv, botsById, untitled)
        val acts = listOf(
            SheetAction(PixelIcon.PIN, stringResource(if (cv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) { repo.setPinned(cv.id, !cv.pinned) },
            SheetAction(PixelIcon.EDIT, stringResource(R.string.ui_act_rename)) { renameFor = cv },
            SheetAction(PixelIcon.TRASH, stringResource(R.string.ui_act_delete), danger = true) { deleteFor = cv },
        )
        ActionSheet(name, acts) { menuFor = null }
    }
    renameFor?.let { cv ->
        RenameDialog(
            initial = titleOf(cv, botsById, untitled), onSave = { repo.renameConversation(cv.id, it) },
            onDismiss = { renameFor = null },
        )
    }
    deleteFor?.let { cv ->
        val name = titleOf(cv, botsById, untitled)
        ConfirmDialog(
            title = stringResource(R.string.ui_dlg_delete_title), body = stringResource(R.string.ui_dlg_delete_body, name),
            confirmLabel = stringResource(R.string.ui_dlg_delete_confirm),
            onConfirm = {
                if (cv.id in busy) app.engine.stop(cv.id)
                repo.deleteConversation(cv.id)
            },
            onDismiss = { deleteFor = null },
        )
    }
}

/** One chat in a list: avatar (stack for groups), title, preview, time, pulsing ember while it is answering. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    conv: Conversation,
    botsById: Map<String, Bot>,
    generating: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Pocket.colors
    val haptic = LocalHapticFeedback.current
    val members = conv.botIds.map { botsById[it] }
    val untitled = stringResource(R.string.ui_chats_untitled)
    val title = titleOf(conv, botsById, untitled)
    val preview = remember(conv.lastPreview) { markdownToPlain(conv.lastPreview) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 68.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(members, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conv.pinned) {
                    PixelIconView(PixelIcon.PIN, tint = c.accent, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(conv.updatedAt), color = c.textMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (generating) stringResource(R.string.ui_chats_generating) + "…"
                    else preview.ifBlank { stringResource(R.string.ui_chats_empty_preview) },
                    color = if (generating) c.accent else c.textMuted, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (generating) {
                    Spacer(Modifier.width(8.dp))
                    GeneratingEmber(size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun HitRow(conv: Conversation, msg: Message, q: String, botsById: Map<String, Bot>, untitled: String, onClick: () -> Unit) {
    val c = Pocket.colors
    val members = conv.botIds.map { botsById[it] }
    val snippet = remember(msg.text, q, c.accent) { highlight(msg.text, q, c.accent) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .combinedClickableCompat(onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(members, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titleOf(conv, botsById, untitled), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(snippet, color = c.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(relativeTime(msg.createdAt), color = c.textMuted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

/** The text around the first match of [q], with the match in bold accent. */
private fun highlight(text: String, q: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString {
    val flat = text.replace('\n', ' ')
    val idx = flat.indexOf(q, ignoreCase = true)
    if (idx < 0) return AnnotatedString(flat.take(120))
    val start = (idx - 30).coerceAtLeast(0)
    val end = (idx + q.length + 70).coerceAtMost(flat.length)
    return buildAnnotatedString {
        if (start > 0) append("…")
        append(flat.substring(start, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(flat.substring(idx, idx + q.length)) }
        append(flat.substring(idx + q.length, end))
        if (end < flat.length) append("…")
    }
}
