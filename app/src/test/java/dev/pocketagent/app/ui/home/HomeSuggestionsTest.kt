package dev.pocketagent.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeSuggestionsTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val losAngeles = ZoneId.of("America/Los_Angeles")
    private val noon = ZonedDateTime.of(2027, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    private fun input(
        nowMs: Long = noon,
        zone: ZoneId = ZoneId.of("UTC"),
        lastChat: LastChat? = null,
        results: List<RoutineResult> = emptyList(),
        routines: Int = 1,
        chats: Int = 1,
        isNew: Boolean = false,
        canChat: Boolean = true,
    ) = SuggestionInput(nowMs, zone, lastChat, results, routines, chats, isNew, canChat)

    private fun ids(l: List<Suggestion>) = l.map { it.id }

    @Test fun emptyWhenCannotChat() {
        val i = input(
            canChat = false,
            lastChat = LastChat("c1", "Trip", noon, true),
            results = listOf(RoutineResult("r1", "Brief", noon)),
            isNew = true,
        )
        assertTrue(HomeSuggestions.pick(i).isEmpty())
    }

    @Test fun unreadRoutineResultFirst() {
        val i = input(
            results = listOf(RoutineResult("old", "Old brief", noon - 5_000L), RoutineResult("new", "New brief", noon - 1_000L)),
            lastChat = LastChat("c1", "Trip", noon - 10_000L, true),
        )
        val out = HomeSuggestions.pick(i)
        assertEquals(listOf("routine:new", "continue:c1"), ids(out))
        val first = out[0]
        assertEquals(SuggestionText.ROUTINE_RESULT, first.text)
        assertEquals("New brief", first.arg)
        assertEquals(SuggestionAction.OpenChat("new"), first.action)
    }

    @Test fun continueNeedsMessagesAndSevenDays() {
        val week = HomeSuggestions.CONTINUE_WINDOW_MS
        assertEquals(604_800_000L, week)
        // 7 days minus 1 ms: yes
        assertEquals(listOf("continue:c1"), ids(HomeSuggestions.pick(input(lastChat = LastChat("c1", "Trip", noon - week + 1L, true)))))
        // exactly 7 days: yes (the window is inclusive)
        assertEquals(listOf("continue:c1"), ids(HomeSuggestions.pick(input(lastChat = LastChat("c1", "Trip", noon - week, true)))))
        // 7 days plus 1 ms: no
        assertTrue(HomeSuggestions.pick(input(lastChat = LastChat("c1", "Trip", noon - week - 1L, true))).isEmpty())
        // no messages: no
        assertTrue(HomeSuggestions.pick(input(lastChat = LastChat("c1", "Trip", noon, false))).isEmpty())
        val c = HomeSuggestions.pick(input(lastChat = LastChat("c1", "Trip", noon, true))).single()
        assertEquals(SuggestionText.CONTINUE_CHAT, c.text)
        assertEquals("Trip", c.arg)
        assertEquals(SuggestionAction.OpenChat("c1"), c.action)
    }

    @Test fun continueNotDuplicatedOfResult() {
        val i = input(
            results = listOf(RoutineResult("same", "Brief", noon)),
            lastChat = LastChat("same", "Brief", noon, true),
        )
        assertEquals(listOf("routine:same"), ids(HomeSuggestions.pick(i)))
    }

    @Test fun routineIdeaOnlyWithoutRoutines() {
        val none = HomeSuggestions.pick(input(routines = 0, chats = 5))
        assertEquals(listOf("idea:routine"), ids(none))
        assertEquals(SuggestionAction.NewRoutine, none[0].action)
        assertNull(none[0].arg)
        assertTrue(HomeSuggestions.pick(input(routines = 1, chats = 5)).isEmpty())
    }

    @Test fun routineIdeaNeedsThreeChats() {
        assertTrue(HomeSuggestions.pick(input(routines = 0, chats = 2)).isEmpty())
        assertEquals(listOf("idea:routine"), ids(HomeSuggestions.pick(input(routines = 0, chats = 3))))
        // a new person gets the other chip, never the routine idea, however many chats
        assertEquals(listOf("idea:capabilities"), ids(HomeSuggestions.pick(input(routines = 0, chats = 9, isNew = true))))
    }

    @Test fun morningVariantByZoneHour() {
        // 2027-01-15 06:59 in Istanbul (+03:00) is 03:59 UTC, which is 19:59 the evening before in Los Angeles (-08:00)
        val instant = ZonedDateTime.of(2027, 1, 15, 6, 59, 0, 0, istanbul).toInstant().toEpochMilli()
        val inIstanbul = HomeSuggestions.pick(input(nowMs = instant, zone = istanbul, routines = 0, chats = 4)).single()
        val inLosAngeles = HomeSuggestions.pick(input(nowMs = instant, zone = losAngeles, routines = 0, chats = 4)).single()
        assertEquals(SuggestionText.ROUTINE_IDEA_MORNING, inIstanbul.text)
        assertEquals(SuggestionText.ROUTINE_IDEA, inLosAngeles.text)
        assertEquals("idea:routine", inIstanbul.id)
        assertEquals("idea:routine", inLosAngeles.id)
    }

    @Test fun morningHourEdges() {
        fun textAt(hour: Int, minute: Int): SuggestionText {
            val ms = ZonedDateTime.of(2027, 1, 15, hour, minute, 0, 0, istanbul).toInstant().toEpochMilli()
            return HomeSuggestions.pick(input(nowMs = ms, zone = istanbul, routines = 0, chats = 4)).single().text
        }
        assertEquals(SuggestionText.ROUTINE_IDEA, textAt(4, 59))
        assertEquals(SuggestionText.ROUTINE_IDEA_MORNING, textAt(5, 0))
        assertEquals(SuggestionText.ROUTINE_IDEA_MORNING, textAt(11, 59))
        assertEquals(SuggestionText.ROUTINE_IDEA, textAt(12, 0))
        assertEquals(SuggestionText.ROUTINE_IDEA, textAt(0, 0))
    }

    @Test fun newUserGetsOnlyCapabilities() {
        val out = HomeSuggestions.pick(input(isNew = true, routines = 0, chats = 0))
        assertEquals(1, out.size)
        val s = out[0]
        assertEquals("idea:capabilities", s.id)
        assertEquals(SuggestionText.WHAT_CAN_YOU_DO, s.text)
        assertNull(s.arg)
        assertEquals(SuggestionAction.Prefill(SuggestionText.WHAT_CAN_YOU_DO), s.action)
    }

    @Test fun maxHonouredAndCoerced() {
        // three chips available: result, continue, routine idea
        val i = input(
            results = listOf(RoutineResult("r1", "Brief", noon)),
            lastChat = LastChat("c1", "Trip", noon - 1_000L, true),
            routines = 0,
            chats = 6,
        )
        assertEquals(3, HomeSuggestions.pick(i).size)
        assertEquals(0, HomeSuggestions.pick(i, 0).size)
        assertEquals(1, HomeSuggestions.pick(i, 1).size)
        assertEquals("routine:r1", HomeSuggestions.pick(i, 1)[0].id)
        assertEquals(2, HomeSuggestions.pick(i, 2).size)
        assertEquals(3, HomeSuggestions.pick(i, 3).size)
        assertEquals(3, HomeSuggestions.pick(i, 99).size)   // 99 is coerced to 5, only three exist
        assertEquals(0, HomeSuggestions.pick(i, -4).size)
        assertEquals(3, HomeSuggestions.MAX)
    }

    @Test fun idsUnique() {
        val i = input(
            results = listOf(RoutineResult("r1", "Brief", noon)),
            lastChat = LastChat("c1", "Trip", noon, true),
            routines = 0,
            chats = 6,
        )
        val l = ids(HomeSuggestions.pick(i, 5))
        assertEquals(l.size, l.toSet().size)
    }

    @Test fun deterministic() {
        val i = input(
            results = listOf(RoutineResult("r1", "Brief", noon), RoutineResult("r2", "Other", noon)),
            lastChat = LastChat("c1", "Trip", noon, true),
            routines = 0,
            chats = 6,
        )
        assertEquals(HomeSuggestions.pick(i), HomeSuggestions.pick(i))
        // a tie between two results keeps the first of the list
        assertEquals("routine:r1", HomeSuggestions.pick(i)[0].id)
    }
}
