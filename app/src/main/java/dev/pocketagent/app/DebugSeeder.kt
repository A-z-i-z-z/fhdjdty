package dev.pocketagent.app

import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.routines.RoutineKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import dev.pocketagent.app.routines.Routine as SeedRoutine

/**
 * Test data for the phone robot, made inside the app by the debug link's `seed` extra (`DebugLinks.apply`, which logs the line
 * [run] returns as `I/Seed: <line>`). It replaces seeding by `run-as`, which the optimised build cannot do, and it works on the
 * live repositories, so nothing is written behind the app's back.
 *
 * Twin only: [run] throws [IllegalStateException] unless `BuildConfig.IS_TWIN` (the twin is the test copy of the app; the owner's
 * daily app never seeds, even in a debug build). Seeded chats and routines have ids that start with [SEED_PREFIX] so that
 * [Spec.Clear] removes exactly them. This object does not log: the caller writes the returned line.
 *
 * Grammar of [parse]: `chats:<n>`, `chat:<messages>:<chars>`, `routine:<minutesFromNow>`, `usage:<limit>:<WARN|BLOCK|OFF>`, `clear`.
 */
object DebugSeeder {
    /** Every seeded chat and routine id starts with this. */
    const val SEED_PREFIX = "seed-"

    const val MAX_CHATS = 500
    const val MAX_MESSAGES = 2000
    const val MAX_CHARS = 4_000
    const val MAX_MINUTES = 1440
    const val MAX_LIMIT = 2_000_000_000L

    sealed interface Spec {
        /** [count] chats without messages, only a title and a preview, newest first (for the chat-list scroll). */
        data class Chats(val count: Int) : Spec

        /** One chat of [messages] messages (user and bot alternate) of [chars] characters each. */
        data class Chat(val messages: Int, val chars: Int) : Spec

        /** One routine that runs once, [minutesFromNow] minutes from now, in its own chat. */
        data class Routine(val minutesFromNow: Int) : Spec

        /** Sets the daily token budget to [limit] tokens in [mode]. */
        data class Usage(val limit: Long, val mode: BudgetMode) : Spec

        /** Removes every seeded chat and routine and switches the daily budget off. */
        data object Clear : Spec
    }

    private val DIGITS = Regex("^[0-9]{1,10}$")

    /** Pure. Null for anything that is not in the grammar or is out of range. */
    fun parse(text: String): Spec? {
        val parts = text.trim().split(":")
        fun num(i: Int, max: Long, min: Long = 1L): Long? {
            val s = parts.getOrNull(i) ?: return null
            if (!DIGITS.matches(s)) return null
            val v = s.toLong()
            return if (v in min..max) v else null
        }
        return when (parts[0]) {
            "clear" -> if (parts.size == 1) Spec.Clear else null
            "chats" -> if (parts.size == 2) num(1, MAX_CHATS.toLong())?.let { Spec.Chats(it.toInt()) } else null
            "chat" -> {
                if (parts.size != 3) return null
                val m = num(1, MAX_MESSAGES.toLong()) ?: return null
                val c = num(2, MAX_CHARS.toLong()) ?: return null
                Spec.Chat(m.toInt(), c.toInt())
            }
            "routine" -> if (parts.size == 2) num(1, MAX_MINUTES.toLong())?.let { Spec.Routine(it.toInt()) } else null
            "usage" -> {
                if (parts.size != 3) return null
                val limit = num(1, MAX_LIMIT, min = 0L) ?: return null
                val mode = when (parts[2].uppercase()) {
                    "WARN" -> BudgetMode.WARN
                    "BLOCK" -> BudgetMode.BLOCK
                    "OFF" -> BudgetMode.OFF
                    else -> return null
                }
                Spec.Usage(limit, mode)
            }
            else -> null
        }
    }

    /**
     * Makes the data and returns the one log line ("chat <id>", "chats <n> newest <id>", "routine <id> at <epoch ms>",
     * "usage <limit> <MODE>", "clear chats=<n> routines=<m>"). Throws [IllegalStateException] unless this is the twin build.
     *
     * BLOCKING: call off the main thread (it waits up to 5 s for the chat data and a big `chat:` seed makes thousands of writes);
     * may throw [IllegalStateException] (not the twin, or `clear` before the chats are loaded).
     */
    fun run(app: PocketApp, spec: Spec): String = run(app, spec, BuildConfig.IS_TWIN)

    internal fun run(app: PocketApp, spec: Spec, isTwin: Boolean): String {
        requireTwin(isTwin)
        return when (spec) {
            is Spec.Chats -> seedChats(app, spec.count)
            is Spec.Chat -> seedChat(app, spec.messages, spec.chars)
            is Spec.Routine -> seedRoutine(app, spec.minutesFromNow)
            is Spec.Usage -> {
                app.usage.setBudget(UsageBudget(spec.limit, spec.mode))
                "usage ${spec.limit} ${spec.mode.name}"
            }
            Spec.Clear -> clear(app)
        }
    }

    internal fun requireTwin(isTwin: Boolean) {
        check(isTwin) { "DebugSeeder runs only on the twin build" }
    }

    /**
     * Pure: exactly [chars] characters of deterministic text for message [index] (0 based), starting with its number, never ending
     * in a space.
     */
    internal fun messageText(index: Int, chars: Int): String {
        if (chars <= 0) return ""
        val sb = StringBuilder("Seed message ${index + 1}. ")
        var w = index
        while (sb.length < chars) {
            sb.append(WORDS[w % WORDS.size]).append(' ')
            w++
        }
        val cut = sb.substring(0, chars)
        return if (cut.last() == ' ') cut.dropLast(1) + "." else cut
    }

    private val WORDS = listOf(
        "ember", "hearth", "kettle", "river", "lantern", "meadow", "pebble", "thistle", "harbour", "compass", "orchard", "willow",
        "bridge", "almond", "falcon", "garden", "window", "silver", "morning", "bramble",
    )

    private fun awaitLoaded(app: PocketApp): Boolean =
        runBlocking { withTimeoutOrNull(5_000L) { app.chat.loaded.first { it } } == true }

    /**
     * Creates a conversation and gives it a seeded id in the same update: the repository has no "add with this id" call, but its
     * `updateConversation` hands the whole conversation to the lambda, so the id can be set there.
     */
    private fun newSeededConversation(app: PocketApp, botIds: List<String>, title: String, updatedAt: Long, preview: String): String {
        val created = app.chat.createConversation(botIds, title)
        val id = SEED_PREFIX + created.id
        app.chat.updateConversation(created.id) { it.copy(id = id, createdAt = updatedAt, updatedAt = updatedAt, lastPreview = preview) }
        return id
    }

    private fun seedChats(app: PocketApp, count: Int): String {
        awaitLoaded(app)
        val bots = app.chat.bots.value.map { it.id }.ifEmpty { listOf(BotPresets.HERMES_ID) }
        val now = System.currentTimeMillis()
        var newest = ""
        for (i in 0 until count) {
            // Every seventh chat is a group of two, so both avatar shapes get scrolled.
            val ids = if (i % 7 == 6 && bots.size > 1) listOf(bots[0], bots[1]) else listOf(bots[i % bots.size])
            val id = newSeededConversation(
                app, ids, title = "Seed chat ${i + 1}", updatedAt = now - i * 60_000L,
                preview = "Seed preview ${i + 1}: " + WORDS[i % WORDS.size] + " " + WORDS[(i + 5) % WORDS.size],
            )
            if (i == 0) newest = id
        }
        return "chats $count newest $newest"
    }

    private fun seedChat(app: PocketApp, messages: Int, chars: Int): String {
        awaitLoaded(app)
        val now = System.currentTimeMillis()
        val id = newSeededConversation(app, listOf(BotPresets.HERMES_ID), title = "Seed chat", updatedAt = now, preview = "")
        val start = now - (messages + 5) * 1_000L
        for (m in 0 until messages) {
            val user = m % 2 == 0
            app.chat.addMessage(
                Message(
                    id = app.chat.newId(), convId = id, role = if (user) Role.USER else Role.BOT,
                    botId = if (user) null else BotPresets.HERMES_ID, text = messageText(m, chars), createdAt = start + m * 1_000L,
                ),
            )
        }
        return "chat $id"
    }

    private fun seedRoutine(app: PocketApp, minutesFromNow: Int): String {
        val id = SEED_PREFIX + app.routines.newId()
        val at = System.currentTimeMillis() + minutesFromNow * 60_000L
        app.routines.save(
            SeedRoutine(id = id, name = "Seed routine", prompt = "hello from a seeded routine", botId = BotPresets.HERMES_ID, kind = RoutineKind.ONCE, onceAt = at),
        )
        app.routineScheduler.rescheduleAll()
        return "routine $id at $at"
    }

    private fun clear(app: PocketApp): String {
        check(awaitLoaded(app)) { "the chat data is not loaded yet" }
        val chats = app.chat.conversations.value.map { it.id }.filter { it.startsWith(SEED_PREFIX) }
        for (id in chats) app.chat.deleteConversation(id)
        val routines = app.routines.routines.value.map { it.id }.filter { it.startsWith(SEED_PREFIX) }
        for (id in routines) app.routineScheduler.deleteRoutine(id)
        app.usage.setBudget(UsageBudget())
        return "clear chats=${chats.size} routines=${routines.size}"
    }
}
