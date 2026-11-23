package dev.pocketagent.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.bots.BotEditorScreen
import dev.pocketagent.app.ui.bots.BotsScreen
import dev.pocketagent.app.ui.chat.ChatListScreen
import dev.pocketagent.app.ui.chat.ChatScreen
import dev.pocketagent.app.ui.chat.bottomSafeInsets
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.hermes.HermesScreen
import dev.pocketagent.app.ui.hermes.TerminalScreen
import dev.pocketagent.app.ui.home.HomeScreen
import dev.pocketagent.app.ui.settings.SettingsScreen
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.PocketTheme
import java.util.Locale

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
    LocaleScope(appearance.language) {
        PocketTheme(appearance) { AppShell() }
    }
}

// ---- in-app language ------------------------------------------------------------------------------------------

private class LocalizedContext(base: Context, private val localized: Context) : ContextWrapper(base) {
    override fun getResources(): Resources = localized.resources
}

private class LocaleHolder(val context: Context, val configuration: Configuration, val direction: LayoutDirection)

/**
 * Makes `stringResource` (and anything reading the locals below) follow [language] ("" = the system language).
 * The wrapper keeps the Activity reachable through the context chain, which activity-compose relies on.
 */
@Composable
private fun LocaleScope(language: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConf = LocalConfiguration.current
    val holder = remember(language, base, baseConf) {
        val locale = if (language.isBlank()) null else Locale.forLanguageTag(language)
        if (locale == null) {
            LocaleHolder(base, baseConf, if (baseConf.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr)
        } else {
            val conf = Configuration(baseConf)
            conf.setLocale(locale)
            conf.setLayoutDirection(locale)
            val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
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
    ) { content() }
}

// ---- shell ---------------------------------------------------------------------------------------------------------

private class TabSpec(val route: Route, val icon: PixelIcon, val label: Int)

private val TABS = listOf(
    TabSpec(Route.Home, PixelIcon.HEARTH, R.string.ui_nav_home),
    TabSpec(Route.Chats, PixelIcon.CHATS, R.string.ui_nav_chats),
    TabSpec(Route.Bots, PixelIcon.BOTS, R.string.ui_nav_bots),
    TabSpec(Route.Hermes, PixelIcon.SCROLL, R.string.ui_nav_hermes),
    TabSpec(Route.Settings(), PixelIcon.GEAR, R.string.ui_nav_settings),
)

/** Index into [TABS] of the tab a route belongs to. */
private fun Route.tabIndex(): Int = when (this) {
    Route.Home -> 0
    Route.Chats, is Route.Chat -> 1
    Route.Bots, is Route.BotEditor -> 2
    Route.Hermes, Route.Terminal -> 3
    is Route.Settings -> 4
}

/** The bottom bar is shown on the five tab roots only (not in a chat, an editor or the terminal). */
private fun Route.showsBar(): Boolean = when (this) {
    Route.Home, Route.Chats, Route.Bots, Route.Hermes, is Route.Settings -> true
    is Route.Chat, is Route.BotEditor, Route.Terminal -> false
}

/** 0 for a tab root, 1 for a detail screen pushed on top of one. */
private fun Route.rank(): Int = if (showsBar()) 0 else 1

private fun routeTransition(from: Route, to: Route, anim: AnimLevel): ContentTransform {
    if (anim == AnimLevel.OFF) return ContentTransform(EnterTransition.None, ExitTransition.None)
    if (anim == AnimLevel.REDUCED) return ContentTransform(fadeIn(tween(140)), fadeOut(tween(100)))
    val spec = tween<IntOffset>(240, easing = FastOutSlowInEasing)
    val fadeInSpec = tween<Float>(200)
    val fadeOutSpec = tween<Float>(140)
    val fromRank = from.rank()
    val toRank = to.rank()
    return when {
        toRank > fromRank || (toRank == 1 && fromRank == 1) -> ContentTransform(
            slideInHorizontally(spec) { it / 4 } + fadeIn(fadeInSpec),
            slideOutHorizontally(spec) { -it / 8 } + fadeOut(fadeOutSpec),
        )
        toRank < fromRank -> ContentTransform(
            slideInHorizontally(spec) { -it / 8 } + fadeIn(fadeInSpec),
            slideOutHorizontally(spec) { it / 4 } + fadeOut(fadeOutSpec),
            targetContentZIndex = -1f,
        )
        else -> {
            val dir = if (to.tabIndex() >= from.tabIndex()) 1 else -1
            ContentTransform(
                slideInHorizontally(spec) { dir * it / 10 } + fadeIn(fadeInSpec),
                fadeOut(fadeOutSpec),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppShell() {
    val nav = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    val c = Pocket.colors
    val anim = Pocket.anim
    val route = nav.current
    val imeOpen = WindowInsets.isImeVisible
    val showBar = route.showsBar() && !imeOpen

    // Back: pop the stack; on the root of a tab other than Home go to Home; on Home the system leaves the app.
    BackHandler(enabled = nav.canPop || route != Route.Home) {
        if (!nav.back()) nav.tab(Route.Home)
    }

    CompositionLocalProvider(LocalNavigator provides nav) {
        Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (!showBar) Modifier.windowInsetsPadding(bottomSafeInsets()) else Modifier),
            ) {
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { routeTransition(initialState, targetState, anim) },
                    label = "route",
                ) { r ->
                    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) { RouteContent(r) }
                }
            }
            AnimatedVisibility(
                visible = showBar,
                enter = if (anim == AnimLevel.OFF) EnterTransition.None else slideInVertically(tween(180)) { it } + fadeIn(tween(180)),
                exit = if (anim == AnimLevel.OFF) ExitTransition.None else slideOutVertically(tween(140)) { it } + fadeOut(tween(140)),
            ) {
                PocketBottomBar(route, nav)
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
    }
}

// ---- bottom bar -------------------------------------------------------------------------------------------------

@Composable
private fun PocketBottomBar(route: Route, nav: Navigator) {
    val c = Pocket.colors
    val selectedIndex = route.tabIndex()
    Column(Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(bottomSafeInsets())) {
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
    val anim = Pocket.anim
    val label = stringResource(tab.label)
    val emberWidth by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = if (anim == AnimLevel.OFF) snap<Dp>() else tween<Dp>(180),
        label = "ember",
    )
    Column(
        modifier.fillMaxSize().selectable(selected = selected, role = Role.Tab, onClick = onClick),
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
