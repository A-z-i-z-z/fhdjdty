package app.yerel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yerel.core.AppSettings
import app.yerel.core.ChatMessage
import app.yerel.core.GenStats
import app.yerel.core.ModelEntry
import app.yerel.core.Role
import app.yerel.core.ToolCall
import app.yerel.core.ToolCatalog
import app.yerel.core.formatBytes
import app.yerel.data.ConversationSummary
import app.yerel.engine.EngineStatus
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpen: (Screen) -> Unit) {
    val settings by vm.settings.collectAsState()
    val conv by vm.conversation.collectAsState()
    val live by vm.live.collectAsState()
    val status by vm.engineStatus.collectAsState()
    val models by vm.models.collectAsState()
    val chats by vm.chats.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    val studio by vm.studio.collectAsState()
    val codeActions = remember { CodeActions(open = vm::openStudio) }
    val continuing by vm.continuing.collectAsState()
    val selected = models.firstOrNull { it.id == settings.lastModelId }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ChatDrawer(
                chats = chats,
                currentId = conv.id,
                onNew = { vm.newChat(); scope.launch { drawer.close() } },
                onOpenChat = { vm.openChat(it); scope.launch { drawer.close() } },
                onDelete = vm::deleteChat,
                onRename = vm::renameChat,
                onScreen = { s -> scope.launch { drawer.close() }; onOpen(s) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Menü") }
                    },
                    title = { ModelChip(selected, status, onClick = { showPicker = true }) },
                    actions = {
                        IconButton(onClick = vm::newChat, enabled = conv.messages.isNotEmpty()) {
                            Icon(Icons.Rounded.AddComment, "Yeni sohbet")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (conv.messages.isEmpty() && live == null) {
                        EmptyState(
                            hasModel = selected != null,
                            downloading = downloads.isNotEmpty(),
                            onModels = { onOpen(Screen.MODELS) },
                            onQuickStart = { vm.downloadCatalog(app.yerel.core.Catalog.models.first { it.repo.startsWith("Qwen/Qwen3-0.6B") }); onOpen(Screen.MODELS) },
                            onSuggestion = vm::send,
                            toolsOn = settings.agentEnabled,
                        )
                    } else {
                        androidx.compose.runtime.CompositionLocalProvider(LocalCodeActions provides codeActions) {
                            MessageList(
                                messages = conv.messages.filter { it.id != continuing },
                                live = live,
                                status = status,
                                settings = settings,
                                onRegenerate = vm::regenerate,
                                onContinue = vm::continueLast,
                                onEdit = { editing = it },
                            )
                        }
                    }
                }
                Composer(
                    settings = settings,
                    generating = live != null,
                    ctxUsed = conv.messages.lastOrNull { it.stats != null }?.stats,
                    onSend = vm::send,
                    onStop = vm::stop,
                    onToggleTools = { vm.updateSettings { s -> s.copy(agentEnabled = !s.agentEnabled) } },
                    onToggleThinking = { vm.updateSettings { s -> s.copy(thinking = !s.thinking) } },
                    onToggleCode = { vm.updateSettings { s -> s.copy(codeMode = !s.codeMode) } },
                )
            }
        }
    }

    if (showPicker) {
        ModelPickerSheet(
            models = models,
            selectedId = settings.lastModelId,
            status = status,
            onSelect = { vm.selectModel(it.id); showPicker = false },
            onManage = { showPicker = false; onOpen(Screen.MODELS) },
            onUnload = { vm.unloadModel(); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    studio?.let { (block, tab) ->
        CodeStudio(
            initial = block, initialTab = tab, allowNetwork = settings.previewNetwork,
            runJs = vm::runJs,
            onAskFix = vm::sendAuto,
            onDismiss = vm::closeStudio,
        )
    }

    editing?.let { msg ->
        var text by remember(msg.id) { mutableStateOf(msg.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Mesajı düzenle") },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10) },
            confirmButton = {
                TextButton(onClick = { vm.editAndResend(msg.id, text); editing = null }, enabled = text.isNotBlank()) { Text("Gönder") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ModelChip(model: ModelEntry?, status: EngineStatus, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (dot, sub) = when {
        model == null -> scheme.outline to "Model yok"
        status is EngineStatus.Loading && status.modelId == model.id -> scheme.tertiary to "Yükleniyor %${(status.progress * 100).toInt()}"
        status is EngineStatus.Ready && status.modelId == model.id -> scheme.primary to "Hazır" + when {
            status.info?.device?.contains("Hexagon", true) == true || status.info?.device?.contains("HTP") == true -> " · NPU"
            status.info?.device?.startsWith("CPU") == false -> " · GPU"
            else -> ""
        }
        status is EngineStatus.Failed && status.modelId == model.id -> scheme.error to "Hata"
        else -> scheme.outline to "Bellekte değil"
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(
                model?.name ?: "Model seç", style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(dot, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(sub, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatDrawer(
    chats: List<ConversationSummary>,
    currentId: String,
    onNew: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onScreen: (Screen) -> Unit,
) {
    var menuFor by remember { mutableStateOf<ConversationSummary?>(null) }
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(310.dp)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppMark(28.dp)
                Spacer(Modifier.width(10.dp))
                Text("Yerel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            NavigationDrawerItem(
                label = { Text("Yeni sohbet") }, icon = { Icon(Icons.Rounded.AddComment, null) }, selected = false, onClick = onNew,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Modeller") }, icon = { Icon(Icons.Rounded.Download, null) }, selected = false, onClick = { onScreen(Screen.MODELS) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Hafıza") }, icon = { Icon(Icons.Rounded.Psychology, null) }, selected = false, onClick = { onScreen(Screen.MEMORY) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Ayarlar") }, icon = { Icon(Icons.Rounded.Settings, null) }, selected = false, onClick = { onScreen(Screen.SETTINGS) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Sohbetler", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            )
            if (chats.isEmpty()) {
                Text(
                    "Henüz sohbet yok", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(chats, key = { it.id }) { c ->
                    Box {
                        NavigationDrawerItem(
                            label = { Text(c.title.ifBlank { "Sohbet" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = c.id == currentId,
                            onClick = { onOpenChat(c.id) },
                            badge = {
                                IconButton(onClick = { menuFor = c }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Edit, "Düzenle", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).height(48.dp),
                        )
                        DropdownMenu(expanded = menuFor?.id == c.id, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Yeniden adlandır") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { renaming = c; menuFor = null })
                            DropdownMenuItem(text = { Text("Sil") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { onDelete(c.id); menuFor = null })
                        }
                    }
                }
            }
        }
    }
    renaming?.let { c ->
        var text by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Sohbeti yeniden adlandır") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(c.id, text); renaming = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(
    hasModel: Boolean,
    downloading: Boolean,
    onModels: () -> Unit,
    onQuickStart: () -> Unit,
    onSuggestion: (String) -> Unit,
    toolsOn: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppMark(56.dp)
        Spacer(Modifier.height(18.dp))
        Text(if (hasModel) "Bugün ne yapalım?" else "Yerel'e hoş geldin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasModel) "Her şey telefonunda çalışır; mesajların hiçbir sunucuya gitmez."
            else "Sohbete başlamak için telefonuna küçük bir dil modeli indir.",
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (!hasModel) {
            if (downloading) {
                Text("İndirme sürüyor… Modeller ekranından takip edebilirsin.", color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onModels) { Text("İndirmeleri göster") }
            } else {
                Surface(onClick = onQuickStart, shape = RoundedCornerShape(18.dp), color = scheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Download, null, tint = scheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hızlı başlangıç: Qwen3 0.6B", style = MaterialTheme.typography.titleSmall)
                            Text("~640 MB · düşünür, araç kullanır · test için ideal", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onModels) { Text("Diğer modeller, Hugging Face bağlantısı veya dosyadan yükle") }
            }
        } else {
            val suggestions = buildList {
                add("Bana hızlı ve sağlıklı bir akşam yemeği tarifi ver")
                add("Bu e-postayı daha kibar yaz: Toplantıya gelemiyorum.")
                if (toolsOn) add("Hesapla: 2499 TL'nin %18 KDV'si ne kadar?")
                if (toolsOn) add("Webde ara: bugünün önemli teknoloji haberleri")
                if (!toolsOn) add("Kuantum bilgisayarları 5 yaşındaki birine anlat")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    Surface(
                        onClick = { onSuggestion(s) }, shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainer, modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

/** The app's mark: a rounded square in the accent color with a spark. */
@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).background(scheme.primary, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", color = scheme.onPrimary, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    live: LiveReply?,
    status: EngineStatus,
    settings: AppSettings,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onEdit: (ChatMessage) -> Unit,
) {
    val toolResults = remember(messages) { messages.filter { it.role == Role.TOOL }.associateBy { it.toolCallId } }
    val visible = remember(messages) { messages.filter { it.role != Role.TOOL }.asReversed() }
    val lastAssistantId = messages.lastOrNull { it.role == Role.ASSISTANT }?.id
    val state = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (state.firstVisibleItemIndex <= 1) state.animateScrollToItem(0)
    }
    LazyColumn(
        state = state,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        if (live != null) {
            item(key = "live") { LiveBubble(live, status) }
        }
        items(visible, key = { it.id }) { m ->
            when {
                m.role == Role.USER && m.auto -> AutoNote(m)
                m.role == Role.USER -> UserBubble(m, onEdit = { onEdit(m) }, canEdit = live == null)
                else -> AssistantMessage(
                    m, toolResults, settings.showStats,
                    isLast = m.id == lastAssistantId && live == null,
                    generating = live != null,
                    onRegenerate = onRegenerate,
                    onContinue = onContinue,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(m: ChatMessage, onEdit: () -> Unit, canEdit: Boolean) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box {
            Surface(
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(max = 320.dp).padding(start = 48.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Text(m.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Kopyala") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                    clipboard.setText(AnnotatedString(m.content)); menu = false
                })
                if (canEdit) {
                    DropdownMenuItem(text = { Text("Düzenle ve yeniden gönder") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = {
                        onEdit(); menu = false
                    })
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    m: ChatMessage,
    toolResults: Map<String?, ChatMessage>,
    showStats: Boolean,
    isLast: Boolean,
    generating: Boolean,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (m.reasoning.isNotBlank()) {
            ThinkingBlock(m.reasoning, live = false)
            Spacer(Modifier.height(8.dp))
        }
        m.toolCalls.forEach { call ->
            val result = toolResults[call.id]
            ToolCard(call, result, running = result == null && generating)
            Spacer(Modifier.height(6.dp))
        }
        if (m.content.isNotBlank()) MarkdownText(m.content)
        m.error?.let { err -> ErrorCard(err) }
        if (m.content.isNotBlank() || m.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (m.content.isNotBlank()) {
                    SmallAction(Icons.Rounded.ContentCopy, "Kopyala") { clipboard.setText(AnnotatedString(m.content)) }
                }
                if (isLast) SmallAction(Icons.Rounded.Refresh, "Yeniden oluştur", onRegenerate)
                if (showStats) m.stats?.let { StatsLine(it) }
            }
            if (isLast && m.stats?.stop == "length") {
                androidx.compose.material3.OutlinedButton(onClick = onContinue, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Devam et")
                }
            }
        }
    }
}

/** Feedback the app sent to the model (automatic code check, "fix this error"). */
@Composable
private fun AutoNote(m: ChatMessage) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val problems = remember(m.content) { m.content.lines().count { it.startsWith("- ") } }
    Surface(
        shape = RoundedCornerShape(14.dp), color = scheme.tertiaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, null, Modifier.size(17.dp), tint = scheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (m.content.startsWith("Automatic check")) "Otomatik kontrol: $problems sorun bulundu, düzeltme istendi"
                    else "Hata modele gönderildi, düzeltme istendi",
                    style = MaterialTheme.typography.labelLarge, color = scheme.onTertiaryContainer, modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text(m.content, style = MonoStyle.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = scheme.onSurfaceVariant, maxLines = 30, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, label, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatsLine(s: GenStats) {
    val parts = buildList {
        if (s.genTokens > 0) add(String.format(Locale.ROOT, "%.1f tok/sn", s.tokensPerSecond))
        add("${s.genTokens} token")
        if (s.promptTokens - s.reusedTokens > 8 && s.promptMs > 0) add(String.format(Locale.ROOT, "ön işleme %.0f tok/sn", s.prefillPerSecond))
        if (s.draftTokens > 0) add("tahmin %${s.draftAccepted * 100 / s.draftTokens} isabet")
        if (s.stop == "length") add("uzunluk sınırı")
        if (s.stop == "ctx_full") add("bağlam doldu")
        if (s.stop == "cancel") add("durduruldu")
    }
    Text(
        parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ErrorCard(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ThinkingBlock(reasoning: String, live: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lightbulb, null, Modifier.size(16.dp).then(if (live) Modifier.alpha(pulse()) else Modifier), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (live) "Düşünüyor…" else "Düşünce süreci",
                style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        }
        // closed until tapped, also while the model is still thinking
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(start = 8.dp, top = 4.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxSize().background(scheme.outlineVariant))
                Spacer(Modifier.width(10.dp))
                Text(reasoning.trim(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun pulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    return a
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "web_search" -> Icons.Rounded.Search
    "fetch_url" -> Icons.Rounded.Public
    "wikipedia" -> Icons.AutoMirrored.Rounded.MenuBook
    "calculator" -> Icons.Rounded.Calculate
    "get_datetime" -> Icons.Rounded.Schedule
    "remember" -> Icons.Rounded.Bookmark
    "forget" -> Icons.Rounded.BookmarkBorder
    "run_javascript" -> Icons.Rounded.PlayArrow
    "check_code" -> Icons.Rounded.Code
    else -> Icons.Rounded.Build
}

private fun toolArgSummary(call: ToolCall): String = try {
    val o = JSONObject(call.arguments.ifBlank { "{}" })
    listOf("query", "url", "expression", "fact", "memory_id", "language", "code").firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } } ?: ""
} catch (e: Exception) {
    call.arguments.take(60)
}

@Composable
private fun ToolCard(call: ToolCall, result: ChatMessage?, running: Boolean) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val title = ToolCatalog.byName(call.name)?.title ?: call.name
    val failed = result?.error != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = result != null) { open = !open },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(toolIcon(call.name), null, Modifier.size(18.dp), tint = if (failed) scheme.error else scheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    val arg = toolArgSummary(call)
                    if (arg.isNotBlank()) {
                        Text(arg, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                when {
                    running -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    result != null -> {
                        Text(
                            result.reasoning.take(40), style = MaterialTheme.typography.labelSmall,
                            color = if (failed) scheme.error else scheme.onSurfaceVariant, maxLines = 1,
                            modifier = Modifier.widthIn(max = 120.dp), overflow = TextOverflow.Ellipsis,
                        )
                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                    }
                }
            }
            if (open && result != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    prettyResult(result.content), style = MonoStyle.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = scheme.onSurfaceVariant, maxLines = 40, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun prettyResult(s: String): String = try {
    JSONObject(s).toString(2).take(3000)
} catch (e: Exception) {
    s.take(3000)
}

@Composable
private fun LiveBubble(live: LiveReply, status: EngineStatus) {
    Column(Modifier.fillMaxWidth()) {
        if (status is EngineStatus.Loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Model belleğe yükleniyor… %${(status.progress * 100).toInt()}", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
            return@Column
        }
        if (live.reasoning.isNotBlank()) {
            ThinkingBlock(live.reasoning, live = live.content.isBlank() && live.toolCalls.isEmpty())
            Spacer(Modifier.height(8.dp))
        }
        // tool calls being written; once sent they show (with a spinner) on the saved message above
        live.toolCalls.forEach { ToolCard(it, null, running = false); Spacer(Modifier.height(6.dp)) }
        when {
            live.content.isNotBlank() -> MarkdownText(live.content + " ●")
            live.reasoning.isBlank() && live.activeTool == null && live.toolCalls.isEmpty() -> TypingDots()
        }
        if (live.nGen > 0 && live.tps > 0) {
            Text(
                String.format(Locale.ROOT, "%.1f tok/sn · %d token", live.tps, live.nGen),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i",
            )
            Box(Modifier.size(8.dp).alpha(a).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

@Composable
private fun Composer(
    settings: AppSettings,
    generating: Boolean,
    ctxUsed: GenStats?,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onToggleTools: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleCode: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 2.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Box(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                if (text.isEmpty()) {
                    Text("Mesaj yaz…", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                ToggleChip(Icons.Rounded.Build, "Araçlar", settings.agentEnabled, onToggleTools)
                Spacer(Modifier.width(6.dp))
                ToggleChip(Icons.Rounded.Lightbulb, "Düşün", settings.thinking, onToggleThinking)
                Spacer(Modifier.width(6.dp))
                ToggleChip(Icons.Rounded.Code, "Kod", settings.codeMode, onToggleCode)
                Spacer(Modifier.weight(1f))
                ctxUsed?.takeIf { it.nCtx > 0 }?.let {
                    Text(
                        "${shortK(it.ctxUsed)}/${shortK(it.nCtx)}", style = MaterialTheme.typography.labelSmall,
                        color = if (it.ctxUsed > it.nCtx * 0.85) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (generating) {
                    FilledIconButton(onClick = onStop, modifier = Modifier.size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.onSurface, contentColor = scheme.surface)) {
                        Icon(Icons.Rounded.Stop, "Durdur", Modifier.size(20.dp))
                    }
                } else {
                    FilledIconButton(
                        onClick = { onSend(text); text = "" },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, "Gönder", Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

private fun shortK(n: Int) = if (n >= 1024) String.format(Locale.ROOT, "%.1fK", n / 1024.0).replace(".0K", "K") else n.toString()

@Composable
private fun ToggleChip(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (on) scheme.primaryContainer else scheme.surfaceContainer,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.height(32.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (on) scheme.primary else scheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<ModelEntry>,
    selectedId: String?,
    status: EngineStatus,
    onSelect: (ModelEntry) -> Unit,
    onManage: () -> Unit,
    onUnload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Model", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            if (models.isEmpty()) {
                Text("Henüz model yok.", color = scheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            models.forEach { m ->
                val loaded = status is EngineStatus.Ready && status.modelId == m.id
                Surface(
                    onClick = { onSelect(m) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (m.id == selectedId) scheme.primaryContainer else scheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(m.info?.quantName?.ifBlank { null }, formatBytes(m.sizeBytes), m.info?.arch, if (loaded) "bellekte" else null)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        }
                        if (m.id == selectedId) Icon(Icons.Rounded.Check, null, tint = scheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                TextButton(onClick = onManage) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Model ekle / yönet")
                }
                Spacer(Modifier.weight(1f))
                if (status is EngineStatus.Ready) {
                    TextButton(onClick = onUnload) { Text("Bellekten kaldır") }
                }
            }
        }
    }
}
