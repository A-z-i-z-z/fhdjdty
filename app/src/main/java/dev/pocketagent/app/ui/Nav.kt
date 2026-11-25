package dev.pocketagent.app.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf

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
}

/**
 * A tiny back stack. The stack is snapshot state, so reading [current] in composition recomposes when it
 * changes. [Navigator.Saver] keeps it across process death / configuration changes.
 */
class Navigator(initial: List<Route> = listOf(Route.Home)) {
    private val stack = mutableStateListOf<Route>().apply { addAll(initial.ifEmpty { listOf(Route.Home) }) }

    /** The screen on top of the stack. */
    val current: Route get() = stack[stack.size - 1]

    /** True when there is a screen below [current] to go back to. */
    val canPop: Boolean get() = stack.size > 1

    /** Pushes [route] (a no-op when it is already on top). Going to Home resets the stack to Home. */
    fun go(route: Route) {
        if (route == Route.Home) { tab(Route.Home); return }
        if (route != current) stack.add(route)
    }

    /** Pops one screen; false when already at the root (the caller then decides what back means). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        return true
    }

    /** Resets the stack to a single root tab. */
    fun tab(route: Route) {
        stack[0] = route
        while (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    internal fun encoded(): List<String> = stack.map { it.encode() }

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { it.encoded() },
            restore = { keys -> Navigator(keys.mapNotNull { decodeRoute(it) }) },
        )
    }
}

val LocalNavigator: ProvidableCompositionLocal<Navigator> =
    staticCompositionLocalOf { error("No Navigator provided: wrap the screen in AppRoot()") }

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
    else -> null
}
