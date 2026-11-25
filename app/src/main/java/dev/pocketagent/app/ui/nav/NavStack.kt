package dev.pocketagent.app.ui.nav

import dev.pocketagent.app.ui.NavEntry
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.decodeRoute
import dev.pocketagent.app.ui.encode

/**
 * The back stack as plain Kotlin (no Compose, no Android), so every rule is unit tested. Each entry has a stable id.
 *
 * Ids: the bottom entry of a tab gets the id of its tab (Home 1, Chats 2, Bots 3, Hermes 4, Settings 5; see
 * [rootIdFor]), so the state of a tab root can be found again whenever the user comes back to the tab. Every other
 * entry gets a fresh id from a counter that never goes back (starting at [FIRST_FREE_ID]), so a second "new bot"
 * editor never inherits the text of the first one and the same route twice on one stack has two ids.
 * Internal callers only; the screens use the public [dev.pocketagent.app.ui.Navigator].
 */
internal class NavStack(initial: List<Route> = listOf(Route.Home)) {
    // Declared before the init block that reads them: Kotlin initialises properties in textual order.
    private var nextId: Long = FIRST_FREE_ID
    private var list: List<NavEntry> = emptyList()

    init {
        list = entriesFor(initial)
    }

    /** The stack, bottom to top. Never empty. The list is never changed in place, so callers may keep it. */
    val entries: List<NavEntry> get() = list

    /** Pushes [route] with a fresh id. Home resets to Home. True when the stack changed. */
    fun go(route: Route): Boolean {
        if (route == Route.Home) {
            val before = list
            tab(Route.Home)
            return before != list
        }
        if (route == list.last().route) return false
        list = list + NavEntry(fresh(), route)
        return true
    }

    /** Pops the top entry; false (nothing changes) when only the root is left. */
    fun back(): Boolean {
        if (list.size <= 1) return false
        list = list.dropLast(1)
        return true
    }

    /** The stack becomes the single root [route]. The bottom entry keeps its id when it is already that route. */
    fun tab(route: Route) {
        val bottom = list.first()
        val root = if (bottom.route == route) bottom else NavEntry(rootIdFor(route) ?: fresh(), route)
        list = listOf(root)
    }

    /** The top entry gets a NEW id and [route]. On a root it works like [tab]. A no-op when [route] is on top already. */
    fun replace(route: Route) {
        if (list.size == 1) {
            tab(route)
            return
        }
        if (list.last().route == route) return
        list = list.dropLast(1) + NavEntry(fresh(), route)
    }

    /** The whole stack is replaced; an empty list gives Home. */
    fun reset(routes: List<Route>) {
        list = entriesFor(routes)
    }

    /** `["#<next id>", "<id>|<route key>", ...]` (route key = [encode]). */
    fun encode(): List<String> {
        val out = ArrayList<String>(list.size + 1)
        out += "#$nextId"
        for (e in list) out += "${e.id}|${e.route.encode()}"
        return out
    }

    private fun fresh(): Long = nextId++

    private fun entriesFor(routes: List<Route>): List<NavEntry> {
        val wanted = routes.ifEmpty { listOf(Route.Home) }
        return wanted.mapIndexed { i, r -> NavEntry(if (i == 0) rootIdFor(r) ?: fresh() else fresh(), r) }
    }

    private fun restore(restored: List<NavEntry>, counter: Long) {
        list = restored
        nextId = counter
    }

    companion object {
        /** Fresh ids start here; 1 to 5 are the root ids. */
        const val FIRST_FREE_ID = 100L

        /** The stable id of a tab root: Home 1, Chats 2, Bots 3, Hermes 4, Settings (no section) 5; null for everything else. */
        fun rootIdFor(route: Route): Long? = when (route) {
            Route.Home -> 1L
            Route.Chats -> 2L
            Route.Bots -> 3L
            Route.Hermes -> 4L
            is Route.Settings -> if (route.section == null) 5L else null
            else -> null
        }

        fun isRootId(id: Long): Boolean = id in 1L..5L

        /**
         * Reads what [encode] wrote. A first element starting with a hash sign is the saved counter. An element
         * "digits|key" keeps its id; a bare key (the old format) gets a fresh one; unknown keys are dropped; the
         * bottom entry gets the root id of its route; an id below [FIRST_FREE_ID] anywhere else, or one used twice,
         * is replaced by a fresh one; an empty result is Home; the counter becomes the largest of the saved one,
         * the highest id plus one and [FIRST_FREE_ID].
         */
        fun decode(saved: List<String>): NavStack {
            var savedCounter = 0L
            var start = 0
            val head = saved.firstOrNull()
            if (head != null && head.startsWith("#")) {
                savedCounter = head.substring(1).toLongOrNull() ?: 0L
                start = 1
            }
            val parsed = ArrayList<Parsed>()
            for (i in start until saved.size) {
                val item = saved[i]
                val bar = item.indexOf('|')
                val idText = if (bar > 0) item.substring(0, bar) else null
                val id = idText?.takeIf { t -> t.length <= 18 && t.all { ch -> ch in '0'..'9' } }?.toLongOrNull()
                val key = if (id != null) item.substring(bar + 1) else item
                val route = decodeRoute(key) ?: continue
                parsed += Parsed(id, route)
            }
            val stack = NavStack(listOf(Route.Home))
            if (parsed.isEmpty()) {
                stack.restore(stack.list, maxOf(savedCounter, FIRST_FREE_ID))
                return stack
            }
            val used = HashSet<Long>()
            val kept = ArrayList<Long?>(parsed.size)
            for ((i, p) in parsed.withIndex()) {
                val rootId = if (i == 0) rootIdFor(p.route) else null
                val savedId = p.id
                val keep: Long? = when {
                    rootId != null -> rootId
                    savedId != null && savedId >= FIRST_FREE_ID && savedId !in used -> savedId
                    else -> null
                }
                if (keep != null) used += keep
                kept += keep
            }
            var counter = maxOf(savedCounter, FIRST_FREE_ID)
            for (id in used) if (id >= FIRST_FREE_ID && id + 1 > counter) counter = id + 1
            val restored = parsed.mapIndexed { i, p -> NavEntry(kept[i] ?: counter++, p.route) }
            stack.restore(restored, counter)
            return stack
        }
    }

    private class Parsed(val id: Long?, val route: Route)
}
