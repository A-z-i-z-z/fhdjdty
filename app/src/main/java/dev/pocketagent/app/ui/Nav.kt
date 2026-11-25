package dev.pocketagent.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import dev.pocketagent.app.ui.nav.NavStack

/** Every screen of the app. Tabs: Home, Chats, Bots, Hermes, Settings. Routines belong to Home, Starred to Chats. */
sealed interface Route {
    data object Home : Route
    data object Chats : Route
    data class Chat(val convId: String) : Route
    data object Bots : Route
    data class BotEditor(val botId: String?) : Route
    data object Hermes : Route
    data object Terminal : Route
    data class Settings(val section: String? = null) : Route
    data object Routines : Route
    /** [routineId] null = a new routine. */
    data class RoutineEditor(val routineId: String?) : Route
    /** Saved (starred) messages of all chats. */
    data object Starred : Route
    /** The design gallery. Reachable only through the debug link; a build without test hooks shows Home instead. */
    data object Gallery : Route
}

/**
 * One screen on the back stack. [id] is unique inside one [Navigator], never reused, and survives process death, so
 * state kept for a screen (scroll position, typed text) can be found again by it.
 */
@Immutable
data class NavEntry(val id: Long, val route: Route)

/**
 * The back stack. The entries are snapshot state, so reading [entries], [currentEntry] or [current] in composition
 * recomposes when the stack changes. [Navigator.Saver] keeps it, ids included, across process death. All the
 * stack logic lives in [NavStack] (plain Kotlin, unit tested); this class only mirrors it into Compose state.
 */
class Navigator internal constructor(private val stack: NavStack) {
    constructor(initial: List<Route> = listOf(Route.Home)) : this(NavStack(initial))

    // Declared after [stack] (it reads it): Kotlin initialises properties in textual order.
    private val state = mutableStateOf(stack.entries)

    /** The whole stack, bottom to top. Never empty. */
    val entries: List<NavEntry> get() = state.value

    /** The entry on top of the stack. */
    val currentEntry: NavEntry get() = entries.last()

    /** The screen on top of the stack. */
    val current: Route get() = currentEntry.route

    /** True when there is a screen below [current] to go back to. */
    val canPop: Boolean get() = entries.size > 1

    /** Pushes [route] with a fresh id (a no-op when it is already on top). Going to Home resets the stack to Home. */
    fun go(route: Route) {
        if (stack.go(route)) sync()
    }

    /** Pops one screen; false when already at the root (the caller then decides what back means). */
    fun back(): Boolean {
        val popped = stack.back()
        if (popped) sync()
        return popped
    }

    /** Resets the stack to a single root tab. A tab root keeps the stable id of its tab. */
    fun tab(route: Route) {
        stack.tab(route)
        sync()
    }

    /** Swaps the top screen for [route] under a new id (its saved state is dropped). On a root it works like [tab]. */
    fun replace(route: Route) {
        stack.replace(route)
        sync()
    }

    /** Replaces the whole stack. An empty list gives Home. */
    fun reset(routes: List<Route>) {
        stack.reset(routes)
        sync()
    }

    private fun sync() {
        state.value = stack.entries
    }

    internal fun encoded(): List<String> = stack.encode()

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { it.encoded() },
            restore = { keys -> Navigator(NavStack.decode(keys)) },
        )
    }
}

val LocalNavigator: ProvidableCompositionLocal<Navigator> =
    staticCompositionLocalOf { error("No Navigator provided: wrap the screen in AppRoot()") }

/** Index into the five tabs (Home, Chats, Bots, Hermes, Settings) of the tab a route belongs to. */
internal fun Route.tabIndex(): Int = when (this) {
    Route.Home, Route.Routines, is Route.RoutineEditor, Route.Gallery -> 0
    Route.Chats, is Route.Chat, Route.Starred -> 1
    Route.Bots, is Route.BotEditor -> 2
    Route.Hermes, Route.Terminal -> 3
    is Route.Settings -> 4
}

/** The bottom bar is shown on the five tab roots only (not in a chat, an editor or the terminal). */
internal fun Route.showsBar(): Boolean = when (this) {
    Route.Home, Route.Chats, Route.Bots, Route.Hermes, is Route.Settings -> true
    is Route.Chat, is Route.BotEditor, Route.Terminal, Route.Routines, is Route.RoutineEditor, Route.Starred, Route.Gallery -> false
}

/** 0 for a tab root, 1 for a detail screen pushed on top of one. */
internal fun Route.rank(): Int = if (showsBar()) 0 else 1

internal fun Route.encode(): String = when (this) {
    Route.Home -> "home"
    Route.Chats -> "chats"
    is Route.Chat -> "chat:$convId"
    Route.Bots -> "bots"
    is Route.BotEditor -> if (botId == null) "botnew" else "bot:$botId"
    Route.Hermes -> "hermes"
    Route.Terminal -> "terminal"
    is Route.Settings -> if (section == null) "settings" else "settings:$section"
    Route.Routines -> "routines"
    is Route.RoutineEditor -> if (routineId == null) "routinenew" else "routine:$routineId"
    Route.Starred -> "starred"
    Route.Gallery -> "gallery"
}

internal fun decodeRoute(s: String): Route? = when {
    s == "home" -> Route.Home
    s == "chats" -> Route.Chats
    s.startsWith("chat:") -> Route.Chat(s.removePrefix("chat:"))
    s == "bots" -> Route.Bots
    s == "botnew" -> Route.BotEditor(null)
    s.startsWith("bot:") -> Route.BotEditor(s.removePrefix("bot:"))
    s == "hermes" -> Route.Hermes
    s == "terminal" -> Route.Terminal
    s == "settings" -> Route.Settings(null)
    s.startsWith("settings:") -> Route.Settings(s.removePrefix("settings:"))
    s == "routines" -> Route.Routines
    s == "routinenew" -> Route.RoutineEditor(null)
    s.startsWith("routine:") -> Route.RoutineEditor(s.removePrefix("routine:"))
    s == "starred" -> Route.Starred
    s == "gallery" -> Route.Gallery
    else -> null
}
