package dev.pocketagent.app.ui.home

import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.pixel.HearthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeUiTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val now = ZonedDateTime.of(2027, 1, 15, 22, 0, 0, 0, istanbul).toInstant().toEpochMilli()

    private fun conv(
        id: String,
        preview: String = "hello",
        updatedAt: Long = now,
        unread: Int = 0,
        routineId: String = "",
        pinned: Boolean = false,
        title: String = "Chat $id",
    ) = Conversation(
        id = id, title = title, botIds = listOf("hermes"), createdAt = 1L, updatedAt = updatedAt,
        pinned = pinned, lastPreview = preview, unread = unread, routineId = routineId,
    )

    private fun inputs(
        runtime: RuntimeState = RuntimeState.Running,
        setupIncomplete: Boolean = false,
        canChat: Boolean = true,
        loaded: Boolean = true,
        convs: List<Conversation> = listOf(conv("a")),
        busy: Set<String> = emptySet(),
        drafts: Map<String, String> = emptyMap(),
        routineCount: Int = 1,
        tokens: Long = 0L,
        budget: UsageBudget = UsageBudget(),
        nowMs: Long = now,
        zone: ZoneId = istanbul,
    ) = HomeInputs(
        runtime = runtime, setupIncomplete = setupIncomplete, canChat = canChat, chatsLoaded = loaded,
        bots = emptyList(), conversations = convs, busy = busy, drafts = drafts,
        nextRoutineAt = null, routineCount = routineCount, todayTokens = tokens, budget = budget, nowMs = nowMs, zone = zone,
    )

    private fun kindOf(i: HomeInputs) = buildHomeUi(i).kind

    @Test fun kindOrder() {
        val err = RuntimeState.Error("boom")
        // Error beats everything
        assertEquals(HomeKind.ERROR, kindOf(inputs(runtime = err, loaded = false, setupIncomplete = true, convs = emptyList())))
        assertEquals(HomeKind.ERROR, kindOf(inputs(runtime = err)))
        // Loading beats FirstRun, Empty and Ready
        assertEquals(HomeKind.LOADING, kindOf(inputs(runtime = RuntimeState.NotInstalled, loaded = false)))
        assertEquals(HomeKind.LOADING, kindOf(inputs(loaded = false, convs = emptyList())))
        assertEquals(HomeKind.LOADING, kindOf(inputs(loaded = false)))
        // FirstRun beats Empty and Ready
        assertEquals(HomeKind.FIRST_RUN, kindOf(inputs(runtime = RuntimeState.NotInstalled, convs = emptyList())))
        assertEquals(HomeKind.FIRST_RUN, kindOf(inputs(runtime = RuntimeState.NotInstalled)))
        assertEquals(HomeKind.FIRST_RUN, kindOf(inputs(runtime = RuntimeState.Extracting(0.3f, "x"), convs = emptyList())))
        assertEquals(HomeKind.FIRST_RUN, kindOf(inputs(setupIncomplete = true, convs = emptyList())))
        assertEquals(HomeKind.FIRST_RUN, kindOf(inputs(setupIncomplete = true)))
        // Empty beats Ready; Stopped and Starting are not first runs
        assertEquals(HomeKind.EMPTY, kindOf(inputs(convs = emptyList())))
        assertEquals(HomeKind.EMPTY, kindOf(inputs(convs = listOf(conv("a", preview = "")))))
        assertEquals(HomeKind.EMPTY, kindOf(inputs(runtime = RuntimeState.Stopped("user"), convs = emptyList())))
        assertEquals(HomeKind.EMPTY, kindOf(inputs(runtime = RuntimeState.Starting("x"), convs = emptyList())))
        assertEquals(HomeKind.READY, kindOf(inputs()))
        assertEquals(HomeKind.READY, kindOf(inputs(runtime = RuntimeState.Stopped("user"))))
        assertEquals(HomeKind.READY, kindOf(inputs(runtime = RuntimeState.Starting("x"))))
    }

    @Test fun hearthAndProgress() {
        fun ui(r: RuntimeState) = buildHomeUi(inputs(runtime = r))
        assertEquals(HearthState.COLD, ui(RuntimeState.NotInstalled).hearth)
        assertNull(ui(RuntimeState.NotInstalled).progress)
        assertEquals(HearthState.COLD, ui(RuntimeState.Stopped("x")).hearth)
        assertNull(ui(RuntimeState.Stopped("x")).progress)
        val ex = ui(RuntimeState.Extracting(0.5f, "x"))
        assertEquals(HearthState.KINDLING, ex.hearth)
        assertEquals(0.45f, ex.progress!!, 1e-6f)
        assertEquals(0.9f, ui(RuntimeState.Extracting(7f, "x")).progress!!, 1e-6f)
        assertEquals(0f, ui(RuntimeState.Extracting(-3f, "x")).progress!!, 1e-6f)
        val st = ui(RuntimeState.Starting("x"))
        assertEquals(HearthState.KINDLING, st.hearth)
        assertEquals(0.95f, st.progress!!, 1e-6f)
        assertEquals(HearthState.BLAZING, ui(RuntimeState.Running).hearth)
        assertNull(ui(RuntimeState.Running).progress)
        assertEquals(HearthState.SMOKE, ui(RuntimeState.Error("e")).hearth)
        assertNull(ui(RuntimeState.Error("e")).progress)
    }

    @Test fun recentIsFirstFour() {
        val six = (1..6).map { conv("c$it") }
        val ui = buildHomeUi(inputs(convs = six))
        assertEquals(listOf("c1", "c2", "c3", "c4"), ui.recent.map { it.conv.id })
        assertTrue(ui.moreChats)
        assertFalse(buildHomeUi(inputs(convs = six.take(4))).moreChats)
        assertTrue(buildHomeUi(inputs(convs = six.take(5))).moreChats)
        assertEquals(2, buildHomeUi(inputs(convs = six.take(2))).recent.size)
        assertEquals(4, HomeUi.RECENT_COUNT)
    }

    @Test fun unreadTotalAndMore() {
        val convs = listOf(conv("a", unread = 2), conv("b"), conv("c", unread = 1), conv("d"), conv("e", unread = 4))
        val ui = buildHomeUi(inputs(convs = convs))
        // the total counts every chat, not only the four shown
        assertEquals(7, ui.unreadTotal)
        assertTrue(ui.moreChats)
        assertEquals(0, buildHomeUi(inputs(convs = emptyList())).unreadTotal)
    }

    @Test fun isNewUserByPreview() {
        assertTrue(buildHomeUi(inputs(convs = emptyList())).isNewUser)
        assertTrue(buildHomeUi(inputs(convs = listOf(conv("a", preview = ""), conv("b", preview = "   ")))).isNewUser)
        assertFalse(buildHomeUi(inputs(convs = listOf(conv("a", preview = ""), conv("b", preview = "hi")))).isNewUser)
    }

    @Test fun draftsAttach() {
        val convs = listOf(conv("a"), conv("b"), conv("c"))
        val ui = buildHomeUi(inputs(convs = convs, busy = setOf("b", "zzz"), drafts = mapOf("a" to "half a thought", "zzz" to "x")))
        val a = ui.recent[0]
        val b = ui.recent[1]
        val c = ui.recent[2]
        assertEquals("half a thought", a.draft)
        assertFalse(a.generating)
        assertEquals("", b.draft)
        assertTrue(b.generating)
        assertEquals("", c.draft)
        assertFalse(c.generating)
    }

    @Test fun suggestionsGatedByCanChat() {
        val withChat = inputs(convs = listOf(conv("a")), routineCount = 1)
        // the last chat has messages and is fresh: one chip
        assertEquals(listOf("continue:a"), buildHomeUi(withChat).suggestions.map { it.id })
        assertTrue(buildHomeUi(withChat.copy(canChat = false)).suggestions.isEmpty())
        // a new person is offered the capabilities chip, but not while the chats are still loading
        val fresh = inputs(convs = emptyList(), routineCount = 0)
        assertEquals(listOf("idea:capabilities"), buildHomeUi(fresh).suggestions.map { it.id })
        assertTrue(buildHomeUi(fresh.copy(chatsLoaded = false)).suggestions.isEmpty())
        assertTrue(buildHomeUi(fresh.copy(canChat = false)).suggestions.isEmpty())
    }

    @Test fun suggestionsSeeRoutineResultsAndNewestChat() {
        val convs = listOf(
            conv("pinnedOld", updatedAt = now - 9 * 86_400_000L, pinned = true),
            conv("routineChat", updatedAt = now - 5_000L, unread = 1, routineId = "rt1", title = "Morning brief"),
            conv("newest", updatedAt = now - 1_000L),
            conv("readRoutine", updatedAt = now - 2_000L, unread = 0, routineId = "rt2"),
        )
        val out = buildHomeUi(inputs(convs = convs, routineCount = 2)).suggestions
        // the pinned one is first in the list but the newest chat is the one to continue
        assertEquals(listOf("routine:routineChat", "continue:newest"), out.map { it.id })
        assertEquals("Morning brief", out[0].arg)
        assertEquals("Chat newest", out[1].arg)
        // a chat of a routine with nothing unread is no routine result
        assertTrue(out.none { it.id == "routine:readRoutine" })
    }

    @Test fun routineIdeaCountsAllChats() {
        val convs = (1..3).map { conv("c$it", updatedAt = now - 30L * 86_400_000L) }
        val out = buildHomeUi(inputs(convs = convs, routineCount = 0)).suggestions
        assertEquals(listOf("idea:routine"), out.map { it.id })
        // 22:00 in Istanbul is not morning
        assertEquals(SuggestionText.ROUTINE_IDEA, out[0].text)
    }

    @Test fun dayPartFollowsTheZone() {
        fun part(h: Int, m: Int = 0, zone: ZoneId = istanbul): DayPart {
            val ms = ZonedDateTime.of(2027, 1, 15, h, m, 0, 0, zone).toInstant().toEpochMilli()
            return buildHomeUi(inputs(nowMs = ms, zone = zone)).dayPart
        }
        assertEquals(DayPart.NIGHT, part(22))
        assertEquals(DayPart.MORNING, part(7, 30))
        assertEquals(DayPart.AFTERNOON, part(13))
        assertEquals(DayPart.EVENING, part(18, 30))
        // the same instant is another part of the day in another zone
        val instant = ZonedDateTime.of(2027, 1, 15, 6, 59, 0, 0, istanbul).toInstant().toEpochMilli()
        assertEquals(DayPart.MORNING, buildHomeUi(inputs(nowMs = instant, zone = istanbul)).dayPart)
        assertEquals(DayPart.EVENING, buildHomeUi(inputs(nowMs = instant, zone = ZoneId.of("America/Los_Angeles"))).dayPart)
    }

    @Test fun woodpileAndTokensPassThrough() {
        val warn = UsageBudget(100_000L, BudgetMode.WARN)
        val ui = buildHomeUi(inputs(tokens = 50_000L, budget = warn))
        assertEquals(3, ui.woodpile)
        assertEquals(50_000L, ui.todayTokens)
        assertNull(buildHomeUi(inputs(tokens = 50_000L, budget = UsageBudget())).woodpile)
        assertEquals(0, buildHomeUi(inputs(tokens = 100_000L, budget = UsageBudget(100_000L, BudgetMode.BLOCK))).woodpile)
    }

    @Test fun sameInputsGiveEqualUi() {
        val i = inputs(convs = (1..5).map { conv("c$it", unread = it % 2) })
        assertEquals(buildHomeUi(i), buildHomeUi(i))
        assertEquals(buildHomeUi(i).hashCode(), buildHomeUi(i).hashCode())
    }
}
