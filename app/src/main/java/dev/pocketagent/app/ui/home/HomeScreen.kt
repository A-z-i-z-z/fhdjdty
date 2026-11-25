package dev.pocketagent.app.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.chat.ConversationRow
import dev.pocketagent.app.ui.chat.NewChatSheet
import dev.pocketagent.app.ui.chat.accepted
import dev.pocketagent.app.ui.chat.compactTokens
import dev.pocketagent.app.ui.chat.dayTimeLabel
import dev.pocketagent.app.ui.chat.inputAvailable
import dev.pocketagent.app.ui.chat.openOneToOne
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.pixel.HearthScene
import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.voice.SoundCue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** The home tab: a living hearth scene that mirrors the runtime, the main action, quick chat, bots and recents. */
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val vm: MainViewModel = viewModel()
    val haptic = LocalHapticFeedback.current
    val c = Pocket.colors

    val state by vm.state.collectAsState()
    val s: RuntimeState = state
    val bots by repo.bots.collectAsState()
    val convs by repo.conversations.collectAsState()
    val busy by app.engine.busy.collectAsState()
    val botsById = remember(bots) { bots.associateBy { it.id } }
    val presenceSettings by app.presencePrefs.settings.collectAsState()
    val nextRun by app.routineScheduler.nextRunAt.collectAsState()
    val usageDays by app.usage.days.collectAsState()
    // todayTotal() reads the store's own "today"; the days list changing is what makes it worth asking again.
    val todayTokens = remember(usageDays) { app.usage.todayTotal() }
    val unreadTotal = remember(convs) { convs.sumOf { it.unread } }

    var quick by rememberSaveable { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var tapMood by remember { mutableStateOf<MascotMood?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var celebration by remember { mutableStateOf(false) }
    var quipRes by remember { mutableStateOf<Int?>(null) }
    val stoke = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tapMood, taps) { if (tapMood != null) { delay(1900); tapMood = null } }
    LaunchedEffect(quipRes, taps) { if (quipRes != null) { delay(2400); quipRes = null } }
    LaunchedEffect(celebration) { if (celebration) { delay(4200); celebration = false } }

    val setupIncomplete = vm.savedConfigIncomplete()
    // The quick-talk field is usable while Hermes sleeps when wake-on-send may light the fire for the sender.
    // Same rule as the engine's canWake: wake-on-send needs an unpacked rootfs (a failed extraction leaves an Error without one).
    val runtimeInstalled = remember(s) { app.runtime.isRootfsValid() }
    val canChat = inputAvailable(s, setupIncomplete, presenceSettings.wakeOnSend, runtimeInstalled)

    val hearth = when (s) {
        RuntimeState.NotInstalled, is RuntimeState.Stopped -> HearthState.COLD
        is RuntimeState.Extracting, is RuntimeState.Starting -> HearthState.KINDLING
        RuntimeState.Running -> HearthState.BLAZING
        is RuntimeState.Error -> HearthState.SMOKE
    }
    val sceneProgress: Float? = when (s) {
        is RuntimeState.Extracting -> s.fraction.coerceIn(0f, 1f) * 0.9f
        is RuntimeState.Starting -> 0.95f
        else -> null
    }

    val startChat: (String) -> Unit = { botId ->
        val conv = repo.openOneToOne(botId)
        nav.go(Route.Chat(conv.id))
    }

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- the scene: tap the mascot for a little joy (7 quick taps = Newroz overdrive) ----
        item(key = "scene") {
            val cd = stringResource(R.string.ui_home_mascot_cd)
            val quips = remember { listOf(R.string.ui_home_quip_1, R.string.ui_home_quip_2, R.string.ui_home_quip_3, R.string.ui_home_quip_4) }
            Box(
                Modifier.fillMaxWidth()
                    .semantics { contentDescription = cd }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        val now = System.currentTimeMillis()
                        streak = if (now - lastTapMs <= 1200L) streak + 1 else 1
                        lastTapMs = now
                        taps++
                        if (streak >= 7) {
                            streak = 0
                            celebration = true
                            tapMood = MascotMood.HAPPY
                            quipRes = R.string.ui_home_quip_overdrive
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            app.sounds.play(SoundCue.FORGE)
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            app.sounds.play(SoundCue.SPARK)
                            tapMood = if (taps % 2 == 0) MascotMood.WAVING else MascotMood.HAPPY
                            quipRes = quips[(taps - 1) % quips.size]
                        }
                        scope.launch {
                            stoke.snapTo(1.2f)
                            stoke.animateTo(0f, tween(1200))
                        }
                    },
            ) {
                HearthScene(
                    hearth,
                    Modifier.fillMaxWidth().height(280.dp),
                    progress = sceneProgress,
                    mood = tapMood,
                    stokeBoost = stoke.value,
                    celebration = celebration,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = quipRes != null,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                ) {
                    quipRes?.let { resId ->
                        Box(
                            Modifier.background(c.surface.copy(alpha = 0.92f))
                                .border(2.dp, if (celebration) c.fireHigh else c.outline)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(resId), color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ---- greeting and status ----
        item(key = "status") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelHeading(greeting())
                Text(stringResource(R.string.ui_home_tagline), color = c.textMuted, fontSize = 14.sp)
                StatusCard(s, onStart = { vm.start() }, onStop = { vm.stop() }, onDetails = { nav.go(Route.Settings("runtime")) })
            }
        }

        // ---- setup prompt ----
        if (setupIncomplete) {
            item(key = "setup") {
                PixelPanel(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = c.surface, border = c.warning) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIconView(PixelIcon.KEY, tint = c.warning, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.ui_home_setup_title), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(R.string.ui_home_setup_body), color = c.textMuted, fontSize = 14.sp)
                        PixelButton(
                            stringResource(R.string.ui_home_setup_btn), onClick = { nav.go(Route.Settings("provider")) },
                            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.GEAR,
                        )
                    }
                }
            }
        }

        // ---- talk to Hermes ----
        item(key = "talk") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(stringResource(R.string.ui_home_talk_title))
                Spacer(Modifier.height(6.dp))
                if (canChat) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        PocketTextField(
                            value = quick, onValueChange = { quick = it }, modifier = Modifier.weight(1f),
                            placeholder = stringResource(
                                if (s == RuntimeState.Running) R.string.ui_home_talk_hint else R.string.ui_home_talk_hint_wake,
                            ),
                            minLines = 1, maxLines = 4,
                        )
                        Spacer(Modifier.width(4.dp))
                        PixelIconButton(
                            PixelIcon.SEND, contentDescription = stringResource(R.string.ui_home_send),
                            onClick = {
                                val text = quick.trim()
                                if (text.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // A busy chat would ignore the message: talk in a fresh 1:1 chat instead.
                                    var conv = repo.openOneToOne(BotPresets.HERMES_ID)
                                    if (conv.id in busy) conv = repo.createConversation(listOf(BotPresets.HERMES_ID))
                                    val accepted = app.engine.send(conv.id, text).accepted()
                                    if (accepted) {
                                        app.sounds.play(SoundCue.SEND)
                                        quick = ""
                                        nav.go(Route.Chat(conv.id))
                                    } else {
                                        Toast.makeText(ctx, R.string.ui_send_not_accepted, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            tint = if (quick.isNotBlank()) c.accent else c.textMuted, enabled = quick.isNotBlank(),
                        )
                    }
                } else {
                    PixelPanel(Modifier.fillMaxWidth(), color = c.bgDeep) {
                        Text(
                            stringResource(R.string.ui_home_talk_hint_off), color = c.textMuted, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
            }
        }

        // ---- bots ----
        item(key = "bots") {
            Column {
                SectionHeader(stringResource(R.string.ui_home_bots), Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
                LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(bots, key = { it.id }) { bot -> BotCard(bot, onClick = { startChat(bot.id) }) }
                    item(key = "new-bot") { NewBotCard(onClick = { nav.go(Route.BotEditor(null)) }) }
                }
            }
        }

        item(key = "group") {
            PixelButton(
                stringResource(R.string.ui_home_group_btn), onClick = { showNew = true },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), icon = PixelIcon.GROUP, kind = ButtonKind.SECONDARY,
            )
        }

        // ---- recent conversations ----
        item(key = "recent") {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.ui_home_recent), Modifier.weight(1f))
                    if (unreadTotal > 0) {
                        Text(
                            stringResource(R.string.ui_home_unread, unreadTotal), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(role = Role.Button) { nav.tab(Route.Chats) }.padding(8.dp),
                        )
                    }
                    if (convs.size > 4) {
                        Text(
                            stringResource(R.string.ui_home_see_all), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(role = Role.Button) { nav.tab(Route.Chats) }.padding(8.dp),
                        )
                    }
                }
                if (convs.isEmpty()) {
                    Text(
                        stringResource(R.string.ui_home_no_chats), color = c.textMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    convs.take(4).forEach { cv ->
                        ConversationRow(cv, botsById, cv.id in busy, onClick = { nav.go(Route.Chat(cv.id)) }, onLongClick = null)
                    }
                }
            }
        }

        // ---- inspector, terminal, routines, saved messages, today's usage ----
        item(key = "tiles") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // IntrinsicSize.Min: the two tiles of a row are equally tall (HomeTile fills the height).
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTile(PixelIcon.SCROLL, stringResource(R.string.ui_home_tile_hermes), { nav.go(Route.Hermes) }, Modifier.weight(1f))
                    HomeTile(PixelIcon.TERMINAL, stringResource(R.string.ui_home_tile_terminal), { nav.go(Route.Terminal) }, Modifier.weight(1f))
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTile(
                        PixelIcon.CLOCK, stringResource(R.string.ui_home_tile_routines), { nav.go(Route.Routines) }, Modifier.weight(1f),
                        subtitle = nextRun?.let { stringResource(R.string.ui_home_routines_next, dayTimeLabel(it)) }
                            ?: stringResource(R.string.ui_home_routines_none),
                    )
                    HomeTile(PixelIcon.STAR, stringResource(R.string.ui_home_tile_saved), { nav.go(Route.Starred) }, Modifier.weight(1f))
                }
                if (todayTokens > 0L) {
                    Text(
                        stringResource(R.string.ui_home_usage_today, compactTokens(todayTokens, dev.pocketagent.app.ui.chat.currentLocale())),
                        color = c.textMuted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.go(Route.Settings("usage")) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
    // Over the list, under nothing else: keeps scrolled content from running into the clock and icons.
    HomeStatusScrim(listState)
    }

    if (showNew) {
        NewChatSheet(initialGroup = true, onDismiss = { showNew = false }, onCreated = { id -> showNew = false; nav.go(Route.Chat(id)) })
    }
}

@Composable
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.ui_home_greet_morning
            in 12..17 -> R.string.ui_home_greet_afternoon
            in 18..21 -> R.string.ui_home_greet_evening
            else -> R.string.ui_home_greet_night
        },
    )
}

/** What the fire is doing in plain words, with the one button that fits. */
@Composable
private fun StatusCard(s: RuntimeState, onStart: () -> Unit, onStop: () -> Unit, onDetails: () -> Unit) {
    val c = Pocket.colors
    val busyNow = s is RuntimeState.Extracting || s is RuntimeState.Starting
    val title = when (s) {
        RuntimeState.NotInstalled -> stringResource(R.string.ui_home_status_notinstalled)
        is RuntimeState.Extracting -> stringResource(R.string.ui_home_status_extracting, (s.fraction.coerceIn(0f, 1f) * 100).toInt())
        is RuntimeState.Starting ->
            stringResource(if (s.step.startsWith("still starting")) R.string.ui_home_status_slow else R.string.ui_home_status_starting)
        RuntimeState.Running -> stringResource(R.string.ui_home_status_running)
        is RuntimeState.Stopped -> stringResource(R.string.ui_home_status_stopped)
        is RuntimeState.Error -> stringResource(R.string.ui_home_status_error)
    }
    val firstStart = s == RuntimeState.NotInstalled || s is RuntimeState.Extracting
    PixelPanel(Modifier.fillMaxWidth(), color = c.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (firstStart) Text(stringResource(R.string.ui_home_hint_first), color = c.textMuted, fontSize = 13.sp)
            if (s is RuntimeState.Error && s.reason.isNotBlank()) {
                Text(
                    s.reason, color = c.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            when (s) {
                is RuntimeState.Extracting -> PixelProgressBar(s.fraction)
                is RuntimeState.Starting -> PixelProgressBar(null)
                else -> Unit
            }
            when {
                busyNow -> PixelButton(
                    stringResource(R.string.ui_home_btn_cancel), onClick = onStop,
                    modifier = Modifier.fillMaxWidth(), kind = ButtonKind.GHOST,
                )
                s == RuntimeState.Running -> PixelButton(
                    stringResource(R.string.ui_home_btn_stop), onClick = onStop,
                    modifier = Modifier.fillMaxWidth(), icon = PixelIcon.STOP, kind = ButtonKind.SECONDARY,
                )
                else -> {
                    PixelButton(
                        stringResource(R.string.ui_home_btn_start), onClick = onStart,
                        modifier = Modifier.fillMaxWidth(), icon = PixelIcon.PLAY,
                    )
                    if (s is RuntimeState.Error) {
                        PixelButton(
                            stringResource(R.string.ui_home_btn_details), onClick = onDetails,
                            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.INFO, kind = ButtonKind.GHOST,
                        )
                    }
                }
            }
        }
    }
}
