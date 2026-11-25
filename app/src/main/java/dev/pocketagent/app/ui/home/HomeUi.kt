package dev.pocketagent.app.ui.home

import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.pixel.HearthState
import java.time.Instant
import java.time.ZoneId

/**
 * Home's state as plain values (Wave 1a, HOME.md 3.2): the pure part of this file, written in the first slice.
 *
 * Still to come in a later slice, in this same file: the immutable marker of the Compose runtime on the data
 * classes below (so that equal inputs skip recomposition), `rememberHomeUi(vm)` (collects the flows and calls
 * [buildHomeUi]) and the class `HomeActions` (registry 3.8). Nothing here reads a clock, a file or a store:
 * the time and the zone arrive in [HomeInputs].
 */
enum class HomeKind { FIRST_RUN, LOADING, EMPTY, READY, ERROR }

/** One row of the Recent list: the chat, whether an answer is being written into it, and its unsent draft ("" = none). */
data class RecentItem(val conv: Conversation, val generating: Boolean, val draft: String)

data class HomeInputs(
    val runtime: RuntimeState,
    val setupIncomplete: Boolean,
    val canChat: Boolean,
    val chatsLoaded: Boolean,
    val bots: List<Bot>,
    /** As the repository keeps them: pinned first, then newest first. */
    val conversations: List<Conversation>,
    /** Ids of the chats an answer is being written into. */
    val busy: Set<String>,
    /** Unsent drafts by chat id. */
    val drafts: Map<String, String>,
    val nextRoutineAt: Long?,
    val routineCount: Int,
    val todayTokens: Long,
    val budget: UsageBudget,
    val nowMs: Long,
    val zone: ZoneId,
)

data class HomeUi(
    val kind: HomeKind,
    val hearth: HearthState,
    val progress: Float?,
    val dayPart: DayPart,
    /** The first [HomeUi.RECENT_COUNT] conversations. */
    val recent: List<RecentItem>,
    val unreadTotal: Int,
    /** True when there are more conversations than [HomeUi.RECENT_COUNT]. */
    val moreChats: Boolean,
    /** True when no chat has a preview yet. */
    val isNewUser: Boolean,
    val woodpile: Int?,
    val todayTokens: Long,
    val suggestions: List<Suggestion>,
) {
    companion object {
        /** How many conversations the Recent list on Home shows. */
        const val RECENT_COUNT = 4
    }
}

/** The scene shows 90 percent of the unpacking as the fire catching; the last tenth is the start. */
private const val EXTRACT_SHARE = 0.9f

/** While Hermes starts, the fire is nearly there. */
private const val STARTING_PROGRESS = 0.95f

fun buildHomeUi(i: HomeInputs): HomeUi {
    val s = i.runtime
    val hearth = when (s) {
        RuntimeState.NotInstalled, is RuntimeState.Stopped -> HearthState.COLD
        is RuntimeState.Extracting, is RuntimeState.Starting -> HearthState.KINDLING
        RuntimeState.Running -> HearthState.BLAZING
        is RuntimeState.Error -> HearthState.SMOKE
    }
    val progress: Float? = when (s) {
        is RuntimeState.Extracting -> s.fraction.coerceIn(0f, 1f) * EXTRACT_SHARE
        is RuntimeState.Starting -> STARTING_PROGRESS
        else -> null
    }

    val convs = i.conversations
    val isNewUser = convs.none { it.lastPreview.isNotBlank() }
    val kind = when {
        s is RuntimeState.Error -> HomeKind.ERROR
        !i.chatsLoaded -> HomeKind.LOADING
        s == RuntimeState.NotInstalled || s is RuntimeState.Extracting || i.setupIncomplete -> HomeKind.FIRST_RUN
        isNewUser -> HomeKind.EMPTY
        else -> HomeKind.READY
    }

    val recent = convs.take(HomeUi.RECENT_COUNT).map { RecentItem(it, it.id in i.busy, i.drafts[it.id].orEmpty()) }
    val hour = Instant.ofEpochMilli(i.nowMs).atZone(i.zone).hour

    // Before the lists are read there is no knowledge of the person yet: a chip such as "What can you do?"
    // would flash for a person who has many chats.
    val suggestions = if (!i.chatsLoaded) {
        emptyList()
    } else {
        val newest = convs.maxByOrNull { it.updatedAt }
        HomeSuggestions.pick(
            SuggestionInput(
                nowMs = i.nowMs,
                zone = i.zone,
                lastChat = newest?.let { LastChat(it.id, it.title, it.updatedAt, it.lastPreview.isNotBlank()) },
                unreadRoutineResults = convs.filter { it.routineId.isNotBlank() && it.unread > 0 }
                    .map { RoutineResult(it.id, it.title, it.updatedAt) },
                routineCount = i.routineCount,
                chatCount = convs.size,
                isNewUser = isNewUser,
                canChat = i.canChat,
            ),
        )
    }

    return HomeUi(
        kind = kind,
        hearth = hearth,
        progress = progress,
        dayPart = dayPartOf(hour),
        recent = recent,
        unreadTotal = convs.sumOf { it.unread },
        moreChats = convs.size > HomeUi.RECENT_COUNT,
        isNewUser = isNewUser,
        woodpile = Woodpile.level(i.todayTokens, i.budget),
        todayTokens = i.todayTokens,
        suggestions = suggestions,
    )
}
