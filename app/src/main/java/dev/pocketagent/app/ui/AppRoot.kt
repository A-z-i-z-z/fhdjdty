package dev.pocketagent.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.AppLink
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.DebugLinks
import dev.pocketagent.app.DebugOverrides
import dev.pocketagent.app.IncomingShare
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.ui.bots.BotEditorScreen
import dev.pocketagent.app.ui.bots.BotsScreen
import dev.pocketagent.app.ui.chat.AvatarStack
import dev.pocketagent.app.ui.chat.ChatListScreen
import dev.pocketagent.app.ui.chat.ChatScreen
import dev.pocketagent.app.ui.chat.ChatShareInbox
import dev.pocketagent.app.ui.chat.StarredScreen
import dev.pocketagent.app.ui.chat.bottomSafeInsets
import dev.pocketagent.app.ui.chat.titleOf
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.DesignGallery
import dev.pocketagent.app.ui.components.PetNoteHost
import dev.pocketagent.app.ui.components.PetNoteKind
import dev.pocketagent.app.ui.components.PetNotes
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.diag.CrashReportOffer
import dev.pocketagent.app.ui.hermes.HermesScreen
import dev.pocketagent.app.ui.hermes.TerminalScreen
import dev.pocketagent.app.ui.home.HomeScreen
import dev.pocketagent.app.ui.nav.NavDisplay
import dev.pocketagent.app.ui.nav.NavTags
import dev.pocketagent.app.ui.nav.robotRoot
import dev.pocketagent.app.ui.routines.RoutineEditorScreen
import dev.pocketagent.app.ui.routines.RoutinesScreen
import dev.pocketagent.app.ui.settings.PracticeBanner
import dev.pocketagent.app.ui.settings.SettingsScreen
import dev.pocketagent.app.ui.text.ProvideTexts
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.PocketTheme
import dev.pocketagent.app.ui.theme.SystemStatusBarIcons
import dev.pocketagent.app.ui.theme.motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.Texts

/**
 * The root of the UI: applies the saved appearance and in-app language, owns the navigator, the animated
 * screen switch and the bottom bar, and handles window insets (status bar, navigation bar, keyboard) for
 * every screen: screens themselves must not add status-bar padding.
 */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { AppearanceStore.get(ctx) }
    val appearance by store.appearance.collectAsState()
    // In a build with test hooks the debug link can force a language and a layout direction (in memory only);
    // otherwise both are null and the saved language rules.
    val overrideLanguage by DebugOverrides.language.collectAsState()
    val overrideRtl by DebugOverrides.forceRtl.collectAsState()
    LocaleScope(overrideLanguage ?: appearance.language, overrideRtl) {
        ProvideTexts {
            PocketTheme(appearance) { AppShell() }
        }
    }
}

// ---- in-app language ------------------------------------------------------------------------------------------

/** The language in force for the screens: "" = the system language; includes the debug override of a test build. */
val LocalAppLanguage: ProvidableCompositionLocal<String> = staticCompositionLocalOf { "" }

private class LocalizedContext(base: Context, private val localized: Context) : ContextWrapper(base) {
    override fun getResources(): Resources = localized.resources
}

private class LocaleHolder(val context: Context, val configuration: Configuration, val direction: LayoutDirection)

/**
 * Makes `stringResource` (and anything reading the locals below) follow [language] ("" = the system language).
 * [forceRtl] (a debug override, normally null) forces right-to-left (true) or left-to-right (false) whatever the language.
 * The wrapper keeps the Activity reachable through the context chain, which activity-compose relies on.
 */
@Composable
private fun LocaleScope(language: String, forceRtl: Boolean?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConf = LocalConfiguration.current
    val holder = remember(language, forceRtl, base, baseConf) {
        val locale = if (language.isBlank()) null else Locale.forLanguageTag(language)
        if (locale == null && forceRtl == null) {
            LocaleHolder(base, baseConf, if (baseConf.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr)
        } else {
            val conf = Configuration(baseConf)
            var rtl = baseConf.layoutDirection == View.LAYOUT_DIRECTION_RTL
            if (locale != null) {
                conf.setLocale(locale)
                conf.setLayoutDirection(locale)
                rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
            }
            if (forceRtl != null) {
                rtl = forceRtl
                val dir = if (forceRtl) Configuration.SCREENLAYOUT_LAYOUTDIR_RTL else Configuration.SCREENLAYOUT_LAYOUTDIR_LTR
                conf.screenLayout = (conf.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK.inv()) or dir
            }
            LocaleHolder(
                LocalizedContext(base, base.createConfigurationContext(conf)), conf,
                if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            )
        }
    }
    // One code path for every language (system included), so switching never restarts the tree below.
    CompositionLocalProvider(
        LocalContext provides holder.context,
        LocalConfiguration provides holder.configuration,
        LocalResources provides holder.context.resources,
        LocalLayoutDirection provides holder.direction,
        LocalAppLanguage provides language,
    ) { content() }
}

// ---- shell ---------------------------------------------------------------------------------------------------------

private class TabSpec(val route: Route, val icon: PixelIcon, val label: Int, val tag: String)

private val TABS = listOf(
    TabSpec(Route.Home, PixelIcon.HEARTH, R.string.ui_nav_home, NavTags.TAB_HOME),
    TabSpec(Route.Chats, PixelIcon.CHATS, R.string.ui_nav_chats, NavTags.TAB_CHATS),
    TabSpec(Route.Bots, PixelIcon.BOTS, R.string.ui_nav_bots, NavTags.TAB_BOTS),
    TabSpec(Route.Hermes, PixelIcon.SCROLL, R.string.ui_nav_hermes, NavTags.TAB_HERMES),
    TabSpec(Route.Settings(), PixelIcon.GEAR, R.string.ui_nav_settings, NavTags.TAB_SETTINGS),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppShell() {
    val nav = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val share by app.incomingShare.collectAsState()
    val link by app.pendingLink.collectAsState()
    val scope = rememberCoroutineScope()
    val c = Pocket.colors
    val motion = Pocket.motion
    val route = nav.current
    val imeOpen = WindowInsets.isImeVisible
    val showBar = route.showsBar() && !imeOpen
    // Height of the bar's own content (without the navigation bar inset): the pet's note rests just above it.
    var barHeight by remember { mutableStateOf(0.dp) }

    // HomeScreen always paints the dark Zagros night sky full-bleed behind the status bar, even in light themes.
    SystemStatusBarIcons(darkIcons = route != Route.Home && !c.isDark)

    // Links from notifications, launcher shortcuts, the Quick Settings tile and the debug link (see AppLinks / MainActivity).
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        // On a cold start the chats are still loading: wait before looking a chat up or creating one.
        if (l is AppLink.OpenChat || l is AppLink.NewChat || l is AppLink.DebugOpen) app.chat.loaded.first { it }
        // No suspension point below this line, so the consume-and-navigate step cannot be cancelled half way.
        if (!app.pendingLink.compareAndSet(l, null)) return@LaunchedEffect
        navigateToLink(nav, app, l)
    }

    // Back: pop the stack; on the root of a tab other than Home go to Home; on Home the system leaves the app.
    // A plain back handler on purpose: the back gesture of this phone arrives as one key press at the end (experiment
    // S1), so the pop motion plays after it. A screen with its own enabled BackHandler is deeper and wins.
    BackHandler(enabled = nav.canPop || route != Route.Home) {
        if (!nav.back()) nav.tab(Route.Home)
    }

    CompositionLocalProvider(LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().robotRoot()) {
            Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (!showBar) Modifier.windowInsetsPadding(bottomSafeInsets()) else Modifier),
                ) {
                    NavDisplay(nav, Modifier.fillMaxSize()) { entry ->
                        // PocketTopBar handles status-bar inset once with bgDeep; HomeScreen bleeds its night sky full-bleed.
                        Box(Modifier.fillMaxSize().background(c.bg).testTag(NavTags.screen(entry.route))) { RouteContent(entry.route) }
                    }
                }
                AnimatedVisibility(
                    visible = showBar,
                    enter = if (motion.level == AnimLevel.OFF) EnterTransition.None else slideInVertically(motion.fast()) { it } + fadeIn(motion.fast()),
                    exit = if (motion.level == AnimLevel.OFF) ExitTransition.None else slideOutVertically(motion.fast()) { it } + fadeOut(motion.fast()),
                ) {
                    PocketBottomBar(route, nav, onHeight = { barHeight = it })
                }
            }

            // On top of the screens: the practice-model banner (no layout effect), the crash offer, the share dialog,
            // and last the pet's notes.
            PracticeBanner(Modifier.align(Alignment.TopCenter).statusBarsPadding())
            CrashReportOffer()

            share?.let { payload ->
                IncomingShareDialog(
                    share = payload,
                    onDismiss = { app.incomingShare.value = null },
                    onPickConv = { convId ->
                        app.incomingShare.value = null
                        scope.launch {
                            val importedAtt = if (payload.uri != null) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        if (payload.mime?.startsWith("image/", ignoreCase = true) == true) {
                                            app.attachments.importImage(payload.uri)
                                        } else {
                                            app.attachments.importDocument(payload.uri)
                                        }
                                    }
                                } catch (e: Exception) {
                                    PetNotes.show(Texts.get(ctx, R.string.ui_attach_failed, e.message ?: "share"), PetNoteKind.ERROR)
                                    null
                                }
                            } else null
                            ChatShareInbox.enqueue(convId, payload.text, importedAtt)
                            nav.go(Route.Chat(convId))
                        }
                    },
                )
            }

            PetNoteHost(
                Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                    .padding(bottom = if (showBar) barHeight else 0.dp),
            )
        }
    }
}

/** Applies a consumed [AppLink] to the back stack. */
private fun navigateToLink(nav: Navigator, app: PocketApp, link: AppLink) {
    when (link) {
        is AppLink.OpenChat -> {
            val target = Route.Chat(link.convId)
            if (app.chat.conversation(link.convId) == null) {
                nav.tab(Route.Chats) // the chat was deleted meanwhile: show the list instead
            } else if (nav.current != target) {
                nav.tab(Route.Chats)
                nav.go(target)
            }
        }
        AppLink.NewChat -> {
            // Reuse an untouched empty chat with plain Hermes so repeated taps do not pile up empty chats.
            val reusable = app.chat.conversations.value.firstOrNull {
                it.botIds == listOf(BotPresets.HERMES_ID) && it.title.isBlank() && it.lastPreview.isBlank() && !it.pinned
            }
            val conv = reusable ?: app.chat.createConversation(listOf(BotPresets.HERMES_ID))
            nav.tab(Route.Chats)
            nav.go(Route.Chat(conv.id))
        }
        AppLink.Terminal -> {
            nav.tab(Route.Hermes)
            nav.go(Route.Terminal)
        }
        AppLink.Routines -> {
            nav.tab(Route.Home)
            nav.go(Route.Routines)
        }
        AppLink.Usage -> {
            nav.tab(Route.Settings())
            nav.go(Route.Settings("usage"))
        }
        is AppLink.DebugOpen -> nav.reset(DebugLinks.stackFor(link.route))
    }
}

@Composable
private fun IncomingShareDialog(
    share: IncomingShare,
    onDismiss: () -> Unit,
    onPickConv: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val convs by app.chat.conversations.collectAsState()
    val bots by app.chat.bots.collectAsState()
    val botsById = remember(bots) { bots.associateBy { it.id } }
    val untitled = t(R.string.ui_chats_untitled)
    val c = Pocket.colors
    PocketDialog(
        title = t(R.string.ui_share_target_title),
        onDismiss = onDismiss,
        dismiss = { PixelButton(t(R.string.ui_dlg_cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        Column(Modifier.testTag(NavTags.SHARE_DIALOG)) {
            if (!share.text.isNullOrBlank()) {
                Text(
                    share.text.take(160),
                    color = c.textMuted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            PixelButton(
                t(R.string.ui_share_new_chat),
                onClick = {
                    val fresh = app.chat.createConversation(listOf(BotPresets.HERMES_ID))
                    onPickConv(fresh.id)
                },
                modifier = Modifier.fillMaxWidth().testTag(NavTags.SHARE_NEW),
                icon = PixelIcon.PLUS,
            )
            if (convs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    convs.take(8).forEach { cv ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPickConv(cv.id) }
                                .testTag(NavTags.SHARE_ITEM)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarStack(cv.botIds.map { botsById[it] }, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                titleOf(cv, botsById, untitled),
                                color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteContent(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Chats -> ChatListScreen()
        is Route.Chat -> ChatScreen(route.convId)
        Route.Bots -> BotsScreen()
        is Route.BotEditor -> BotEditorScreen(route.botId)
        Route.Hermes -> HermesScreen()
        Route.Terminal -> TerminalScreen()
        is Route.Settings -> SettingsScreen(route.section)
        Route.Routines -> RoutinesScreen()
        is Route.RoutineEditor -> RoutineEditorScreen(route.routineId)
        Route.Starred -> StarredScreen()
        // The design gallery exists only in a build with test hooks; any other build shows Home.
        Route.Gallery -> if (BuildConfig.TEST_HOOKS) DesignGallery() else HomeScreen()
    }
}

// ---- bottom bar -------------------------------------------------------------------------------------------------

@Composable
private fun PocketBottomBar(route: Route, nav: Navigator, onHeight: (Dp) -> Unit) {
    val c = Pocket.colors
    val density = LocalDensity.current
    val selectedIndex = route.tabIndex()
    Column(
        Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(bottomSafeInsets())
            .onSizeChanged { size -> onHeight(with(density) { size.height.toDp() }) }
            .testTag(NavTags.BAR),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.outline))
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            TABS.forEachIndexed { index, tab ->
                BottomTab(
                    tab = tab, selected = index == selectedIndex, modifier = Modifier.weight(1f),
                    onClick = { nav.tab(tab.route) },
                )
            }
        }
    }
}

@Composable
private fun BottomTab(tab: TabSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pocket.colors
    val motion = Pocket.motion
    val label = t(tab.label)
    val emberWidth by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = if (motion.level == AnimLevel.OFF) snap<Dp>() else motion.fast<Dp>(),
        label = "ember",
    )
    Column(
        modifier.fillMaxSize().selectable(selected = selected, role = Role.Tab, onClick = onClick).testTag(tab.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelIconView(tab.icon, tint = if (selected) c.accent else c.textMuted, size = 24.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            label, color = if (selected) c.text else c.textMuted, fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        // The ember under the active tab: a small two-tone flame bar that grows in.
        Box(Modifier.height(3.dp).width(emberWidth).background(c.fireMid)) {
            Box(Modifier.height(1.dp).width(emberWidth / 2).align(Alignment.TopCenter).background(c.fireHigh))
        }
    }
}
