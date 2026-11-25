package dev.pocketagent.app.ui.chat

import dev.pocketagent.app.data.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowGroupingTest {
    private val sameDay: (Long, Long) -> Boolean = { a, b -> a / 1000 == b / 1000 }

    @Test fun firstMessageShowsHeaderAndDay() {
        val m = testMessage("a", Role.USER, createdAt = 5_000)
        val g = groupingOf(null, m, null, sameDay)
        assertTrue(g.showHeader)
        assertTrue(g.showDay)
        assertTrue(g.isLastInRun)
    }

    @Test fun sameRoleAndBotIsAGroup() {
        val older = testMessage("a", Role.BOT, "hermes", createdAt = 5_000)
        val m = testMessage("b", Role.BOT, "hermes", createdAt = 5_100)
        val newer = testMessage("c", Role.BOT, "hermes", createdAt = 5_200)
        val g = groupingOf(older, m, newer, sameDay)
        assertFalse(g.showHeader)
        assertFalse(g.isLastInRun)
        assertFalse(g.showDay)
        // Another bot, or another role, starts a new run.
        val other = testMessage("d", Role.BOT, "coder", createdAt = 5_100)
        assertTrue(groupingOf(older, other, null, sameDay).showHeader)
        val user = testMessage("e", Role.USER, createdAt = 5_100)
        assertTrue(groupingOf(older, user, null, sameDay).showHeader)
        assertTrue(groupingOf(older, m, user, sameDay).isLastInRun)
    }

    @Test fun dayChangeShowsDay() {
        val older = testMessage("a", Role.USER, createdAt = 1_000)
        val m = testMessage("b", Role.USER, createdAt = 9_000)
        assertTrue(groupingOf(older, m, null, sameDay).showDay)
        assertEquals(false, groupingOf(older, testMessage("c", Role.USER, createdAt = 1_500), null, sameDay).showDay)
    }
}
