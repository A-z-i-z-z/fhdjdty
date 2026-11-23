package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.pocketagent.app.data.Role as MsgRole

/**
 * One conversation: message list (bottom-anchored, sticks to the newest message only while you are at the
 * bottom), input bar, menus. Resolves the conversation and hands over to [ChatBody].
 */
@Composable
fun ChatScreen(convId: String) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val repo = remember { PocketApp.from(ctx).chat }
    val convs by repo.conversations.collectAsState()
    val loaded by repo.loaded.collectAsState()
    val conv = convs.firstOrNull { it.id == convId }
    val convNow by rememberUpdatedState(conv)
    var everSeen by remember(convId) { mutableStateOf(false) }

    // A deleted conversation closes the screen. A brand new one may need a moment to show up in the list.
    LaunchedEffect(convId, conv != null, loaded) {
        if (conv != null) everSeen = true
        else if (loaded) {
            delay(if (everSeen) 0L else 1500L)
            if (convNow == null && !nav.back()) nav.tab(Route.Chats)
        }
    }

    if (conv == null) {
        Column(Modifier.fillMaxSize()) {
            PocketTopBar(title = "", onBack = { if (!nav.back()) nav.tab(Route.Chats) })
            if (loaded) {
                Text(
                    stringResource(R.string.ui_chat_not_found), color = Pocket.colors.textMuted, fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    } else {
        ChatBody(conv)
    }
}

@Composable
private fun ChatBody(conv: Conversation) {
    val convId = conv.id
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val engine = app.engine
    val vm: MainViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val c = Pocket.colors

    val runtime by vm.state.collectAsState()
    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val speaking by engine.speaking.collectAsState()
    val statusMap by engine.status.collectAsState()
    val approvals by engine.approvals.collectAsState()
    val msgsFlow = remember(convId) { repo.messages(convId) }
    val msgs by msgsFlow.collectAsState()

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val members = conv.botIds.map { botsById[it] }
    val memberBots = members.filterNotNull()
    val isGroup = conv.isGroup
    val generating = convId in busySet
    val speakingBot = speaking[convId]?.let { botsById[it] }
    val title = conversationTitle(conv, botsById)
    val subtitle = when {
        generating && !statusMap[convId].isNullOrBlank() -> statusMap[convId].orEmpty()
        generating && speakingBot != null -> stringResource(R.string.ui_chat_typing_who, speakingBot.name)
        generating -> stringResource(R.string.ui_chat_typing)
        isGroup -> memberBots.joinToString(", ") { it.name }
        else -> memberBots.firstOrNull()?.tagline.orEmpty()
    }.takeIf { it.isNotBlank() }

    // ---- input state ----
    var input by remember(convId) { mutableStateOf(ChatDrafts[convId]) }
    DisposableEffect(convId) { onDispose { ChatDrafts[convId] = input } }
    var editing by remember(convId) { mutableStateOf<Message?>(null) }
    var spark by remember { mutableIntStateOf(0) }

    // ---- dialogs ----
    var menu by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var selectFor by remember { mutableStateOf<Message?>(null) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }

    // ---- list ----
    val reversed = remember(msgs) { msgs.asReversed() }
    val listState = rememberLazyListState()
    val showPending = generating && (msgs.isEmpty() || !msgs.last().streaming)
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        // Remember where the *user* left the list: data changes alone never flip this.
        var wasScrolling = false
        snapshotFlow {
            listState.isScrollInProgress to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2)
        }.collect { (scrolling, atBottom) ->
            if (scrolling || wasScrolling) stick = atBottom
            wasScrolling = scrolling
        }
    }
    val newestKey = if (showPending) "pending" else reversed.firstOrNull()?.id
    LaunchedEffect(newestKey) { if (stick) listState.animateScrollToItem(0) }

    val lastBotId = msgs.lastOrNull { it.role == MsgRole.BOT }?.id
    val lastBotIsLast = msgs.lastOrNull { it.role != MsgRole.NOTE }?.role == MsgRole.BOT
    val lastId = msgs.lastOrNull()?.id

    val doSend: () -> Unit = {
        val t = input.trim()
        if (t.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val ed = editing
            val ok = (if (ed != null) engine.editAndResend(convId, ed.id, t)
            else engine.send(convId, t, if (isGroup) mentionedBotIds(t, memberBots) else emptyList())).accepted()
            if (ok) { input = ""; editing = null; spark++; stick = true }
            if (ok) scope.launch { listState.animateScrollToItem(0) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PocketTopBar(
            title = title,
            onBack = { if (!nav.back()) nav.tab(Route.Chats) },
            subtitle = subtitle,
            leading = { AvatarStack(members, 36.dp) },
            actions = {
                if (conv.pinned) PixelIconView(PixelIcon.PIN, tint = c.accent, size = 18.dp)
                PixelIconButton(PixelIcon.MORE, onClick = { menu = true }, contentDescription = stringResource(R.string.ui_chat_more))
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isEmpty() && !generating) {
                EmptyState(
                    stringResource(R.string.ui_chat_empty_title), stringResource(R.string.ui_chat_empty_body),
                    Modifier.align(Alignment.Center),
                )
            }
            val animateItems = Pocket.anim != AnimLevel.OFF
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showPending) {
                    item(key = "pending", contentType = "pending") {
                        PendingRow(speakingBot ?: memberBots.firstOrNull(), showName = isGroup)
                    }
                }
                itemsIndexed(reversed, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { index, m ->
                    val older = reversed.getOrNull(index + 1)
                    val newer = reversed.getOrNull(index - 1)
                    MessageRow(
                        msg = m,
                        bot = m.botId?.let { botsById[it] } ?: if (m.role == MsgRole.BOT) memberBots.firstOrNull() else null,
                        showHeader = older == null || older.role != m.role || older.botId != m.botId,
                        showName = isGroup,
                        isLastInRun = newer == null || newer.role != m.role || newer.botId != m.botId,
                        showDay = older == null || !sameCalendarDay(older.createdAt, m.createdAt),
                        canRetry = m.id == lastId && !generating,
                        onLongPress = { actionsFor = it },
                        onRetry = { engine.regenerateLast(convId) },
                        onCopy = { copyToClipboard(ctx, it) },
                        modifier = if (animateItems) Modifier.animateItem() else Modifier,
                    )
                }
            }
            val jumpLabel = stringResource(R.string.ui_chat_jump)
            androidx.compose.animation.AnimatedVisibility(
                visible = !stick, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(),
            ) {
                Box(
                    Modifier.size(44.dp).background(c.surfaceHigh).border(2.dp, c.outline)
                        .semantics { contentDescription = jumpLabel }
                        .clickable(role = Role.Button) { stick = true; scope.launch { listState.animateScrollToItem(0) } },
                    contentAlignment = Alignment.Center,
                ) { PixelIconView(PixelIcon.DOWN, tint = c.text, size = 22.dp) }
            }
        }
        val approval = approvals[convId]
        if (approval != null) ApprovalCard(approval, botsById[approval.botId]?.name) { engine.resolveApproval(convId, it) }
        ChatInputBar(
            text = input, onTextChange = { input = it },
            members = memberBots, isGroup = isGroup, mentionOnly = conv.groupMode == GroupMode.MENTION_ONLY,
            busy = generating, runtime = runtime, setupIncomplete = vm.savedConfigIncomplete(),
            editing = editing != null, onCancelEdit = { editing = null; input = "" },
            sparkTrigger = spark, onSend = doSend, onStop = { engine.stop(convId) },
            onStart = { vm.start() }, onOpenSetup = { nav.go(Route.Settings("provider")) },
        )
    }

    // ---- menus and dialogs ----
    if (menu) {
        val acts = ArrayList<SheetAction>()
        acts += SheetAction(PixelIcon.EDIT, stringResource(R.string.ui_act_rename)) { rename = true }
        acts += SheetAction(PixelIcon.PIN, stringResource(if (conv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) {
            repo.setPinned(convId, !conv.pinned)
        }
        if (isGroup) acts += SheetAction(PixelIcon.GROUP, stringResource(R.string.ui_act_members)) { showMembers = true }
        acts += SheetAction(PixelIcon.SCROLL, stringResource(R.string.ui_act_inspector)) { nav.go(Route.Hermes) }
        acts += SheetAction(PixelIcon.REFRESH, stringResource(R.string.ui_act_clear), danger = true) { confirmClear = true }
        acts += SheetAction(PixelIcon.TRASH, stringResource(R.string.ui_act_delete), danger = true) { confirmDelete = true }
        ActionSheet(title, acts) { menu = false }
    }

    actionsFor?.let { m ->
        val acts = ArrayList<SheetAction>()
        acts += SheetAction(PixelIcon.COPY, stringResource(R.string.ui_act_copy)) { copyToClipboard(ctx, m.text) }
        acts += SheetAction(PixelIcon.SCROLL, stringResource(R.string.ui_act_select)) { selectFor = m }
        acts += SheetAction(PixelIcon.SHARE, stringResource(R.string.ui_act_share)) { shareText(ctx, m.text) }
        if (m.role == MsgRole.USER && !generating) {
            acts += SheetAction(PixelIcon.EDIT, stringResource(R.string.ui_act_edit)) { editing = m; input = m.text }
        }
        if (m.role == MsgRole.BOT && m.id == lastBotId && lastBotIsLast && !generating) {
            acts += SheetAction(PixelIcon.REFRESH, stringResource(R.string.ui_act_regen)) { engine.regenerateLast(convId) }
        }
        acts += SheetAction(PixelIcon.TRASH, stringResource(R.string.ui_act_delete), danger = true) { repo.deleteMessage(convId, m.id) }
        ActionSheet(null, acts) { actionsFor = null }
    }

    selectFor?.let { m ->
        PocketDialog(
            title = stringResource(R.string.ui_chat_select_title), onDismiss = { selectFor = null },
            confirm = { PixelButton(stringResource(R.string.ui_dlg_close), onClick = { selectFor = null }) },
        ) {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(m.text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp) }
            }
        }
    }

    if (rename) RenameDialog(initial = conv.title.ifBlank { title }, onSave = { repo.renameConversation(convId, it) }, onDismiss = { rename = false })

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ui_dlg_delete_title), body = stringResource(R.string.ui_dlg_delete_body, title),
            confirmLabel = stringResource(R.string.ui_dlg_delete_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.deleteConversation(convId) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.ui_dlg_clear_title), body = stringResource(R.string.ui_dlg_clear_body),
            confirmLabel = stringResource(R.string.ui_dlg_clear_confirm),
            onConfirm = { if (generating) engine.stop(convId); repo.clearConversation(convId) },
            onDismiss = { confirmClear = false },
        )
    }
    if (showMembers) {
        MembersDialog(conv, onDismiss = { showMembers = false })
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
        title = stringResource(R.string.ui_chat_members_title), onDismiss = onDismiss,
        confirm = { PixelButton(stringResource(R.string.ui_dlg_close), onClick = onDismiss) },
    ) {
        Text(stringResource(R.string.ui_chat_mode_title), color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketChip(
                stringResource(R.string.ui_new_mode_all), conv.groupMode == GroupMode.ALL_IN_TURN,
                { repo.setGroupMode(conv.id, GroupMode.ALL_IN_TURN) }, Modifier.weight(1f),
            )
            PocketChip(
                stringResource(R.string.ui_new_mode_mention), conv.groupMode == GroupMode.MENTION_ONLY,
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
