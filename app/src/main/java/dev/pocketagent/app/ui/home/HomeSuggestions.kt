package dev.pocketagent.app.ui.home

import java.time.Instant
import java.time.ZoneId

/**
 * The chips under the prompt on Home (Wave 1a, HOME.md 3.3). Pure: every input is a parameter, nothing is random,
 * the order is stable. A chip never sends anything: it opens a chat, opens the routine editor, or fills the prompt.
 *
 * The compose stability annotation (the immutable marker of the Compose runtime) is deliberately not written here:
 * this file is pure Kotlin for the first slice and a later slice may add it to the data classes below.
 */

/** Which sentence a chip shows; the UI maps it to a `hm_sugg_*` string and fills `arg` into it where it has a slot. */
enum class SuggestionText { ROUTINE_RESULT, CONTINUE_CHAT, ROUTINE_IDEA_MORNING, ROUTINE_IDEA, WHAT_CAN_YOU_DO }

sealed interface SuggestionAction {
    data class OpenChat(val convId: String) : SuggestionAction

    /** Opens the routine editor for a new routine (`Route.RoutineEditor(null)`). */
    data object NewRoutine : SuggestionAction

    /** Puts the prompt text into the Ask field and never sends it. */
    data class Prefill(val prompt: SuggestionText) : SuggestionAction
}

/** [arg] is the chat title for the two chat texts and null for the others. */
data class Suggestion(val id: String, val text: SuggestionText, val arg: String?, val action: SuggestionAction)

/** The most recently updated chat, whether or not it has messages. */
data class LastChat(val convId: String, val title: String, val updatedAt: Long, val hasMessages: Boolean)

/** A chat that belongs to a routine and has an answer the person has not seen. */
data class RoutineResult(val convId: String, val title: String, val updatedAt: Long)

data class SuggestionInput(
    val nowMs: Long,
    val zone: ZoneId,
    val lastChat: LastChat?,
    val unreadRoutineResults: List<RoutineResult>,
    val routineCount: Int,
    val chatCount: Int,
    val isNewUser: Boolean,
    val canChat: Boolean,
)

object HomeSuggestions {
    const val MAX = 3

    /** Seven days: a chat older than this is not offered for continuing. */
    const val CONTINUE_WINDOW_MS = 604_800_000L

    /** The routine idea needs a person who already chats a bit: three chats or more. */
    private const val IDEA_MIN_CHATS = 3

    /** The first and the last hour (local) that count as morning, same table as the greeting. */
    private const val MORNING_FIRST_HOUR = 5
    private const val MORNING_LAST_HOUR = 11

    private const val HARD_MAX = 5

    /**
     * Rules, in this order, stopping at [max] (coerced to 0..5) and never listing one id twice:
     * 1. nothing when the person cannot chat;
     * 2. the newest unread routine result;
     * 3. the last chat, when it has messages, was updated within seven days and is not the chat of rule 2;
     * 4. an idea for a routine, when there is none yet, the person is not new and has at least three chats
     *    (the morning wording when the local hour is 5 to 11);
     * 5. "What can you do?" for a new person.
     */
    fun pick(i: SuggestionInput, max: Int = MAX): List<Suggestion> {
        val limit = max.coerceIn(0, HARD_MAX)
        if (!i.canChat || limit == 0) return emptyList()
        val out = ArrayList<Suggestion>(limit)
        val ids = HashSet<String>()
        val convs = HashSet<String>()

        fun add(s: Suggestion, convId: String? = null) {
            if (out.size >= limit) return
            if (convId != null && convId in convs) return
            if (!ids.add(s.id)) return
            if (convId != null) convs.add(convId)
            out.add(s)
        }

        // (2) the newest unread routine result; on a tie the earlier entry of the list wins
        val result = i.unreadRoutineResults.maxByOrNull { it.updatedAt }
        if (result != null) {
            add(
                Suggestion("routine:" + result.convId, SuggestionText.ROUTINE_RESULT, result.title, SuggestionAction.OpenChat(result.convId)),
                result.convId,
            )
        }

        // (3) continue the last chat
        val last = i.lastChat
        if (last != null && last.hasMessages && i.nowMs - last.updatedAt <= CONTINUE_WINDOW_MS) {
            add(
                Suggestion("continue:" + last.convId, SuggestionText.CONTINUE_CHAT, last.title, SuggestionAction.OpenChat(last.convId)),
                last.convId,
            )
        }

        // (4) the routine idea
        if (i.routineCount == 0 && !i.isNewUser && i.chatCount >= IDEA_MIN_CHATS) {
            val hour = Instant.ofEpochMilli(i.nowMs).atZone(i.zone).hour
            val text = if (hour in MORNING_FIRST_HOUR..MORNING_LAST_HOUR) SuggestionText.ROUTINE_IDEA_MORNING else SuggestionText.ROUTINE_IDEA
            add(Suggestion("idea:routine", text, null, SuggestionAction.NewRoutine))
        }

        // (5) a new person
        if (i.isNewUser) {
            add(
                Suggestion(
                    "idea:capabilities", SuggestionText.WHAT_CAN_YOU_DO, null,
                    SuggestionAction.Prefill(SuggestionText.WHAT_CAN_YOU_DO),
                ),
            )
        }
        return out
    }
}
