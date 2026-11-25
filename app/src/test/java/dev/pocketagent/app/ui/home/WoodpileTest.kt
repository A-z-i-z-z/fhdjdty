package dev.pocketagent.app.ui.home

import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.UsageBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WoodpileTest {
    private val warn100 = UsageBudget(100L, BudgetMode.WARN)

    @Test fun hiddenWithoutBudget() {
        assertNull(Woodpile.level(10L, UsageBudget(100L, BudgetMode.OFF)))
        assertNull(Woodpile.level(10L, UsageBudget(0L, BudgetMode.WARN)))
        assertNull(Woodpile.level(10L, UsageBudget(-5L, BudgetMode.BLOCK)))
        assertNull(Woodpile.level(10L, UsageBudget()))
        assertNull(Woodpile.left(10L, UsageBudget(100L, BudgetMode.OFF)))
        assertNull(Woodpile.left(10L, UsageBudget(0L, BudgetMode.BLOCK)))
    }

    @Test fun levelTable() {
        // used out of a limit of 100; left > 80 gives 5, > 60 gives 4, > 40 gives 3, > 20 gives 2, > 0 gives 1, else 0
        val table = listOf(
            0L to 5, 19L to 5, 20L to 4, 39L to 4, 40L to 3, 59L to 3, 60L to 2, 79L to 2,
            80L to 1, 99L to 1, 100L to 0, 200L to 0,
        )
        for ((used, level) in table) assertEquals("used $used", level, Woodpile.level(used, warn100))
    }

    @Test fun levelTableScalesWithTheLimit() {
        val b = UsageBudget(100_000L, BudgetMode.BLOCK)
        assertEquals(5, Woodpile.level(19_999L, b))
        assertEquals(4, Woodpile.level(20_000L, b))
        assertEquals(3, Woodpile.level(40_000L, b))
        assertEquals(2, Woodpile.level(60_000L, b))
        assertEquals(1, Woodpile.level(80_000L, b))
        assertEquals(1, Woodpile.level(99_999L, b))
        assertEquals(0, Woodpile.level(100_000L, b))
    }

    @Test fun warnAndBlockAgree() {
        for (used in listOf(0L, 33L, 66L, 100L)) {
            assertEquals(
                Woodpile.level(used, UsageBudget(100L, BudgetMode.WARN)),
                Woodpile.level(used, UsageBudget(100L, BudgetMode.BLOCK)),
            )
        }
    }

    @Test fun negativeUsageCountsAsNone() {
        assertEquals(5, Woodpile.level(-50L, warn100))
        assertEquals(1f, Woodpile.left(-50L, warn100)!!, 0f)
    }

    @Test fun hugeNumbersDoNotOverflow() {
        val big = UsageBudget(Long.MAX_VALUE, BudgetMode.WARN)
        assertEquals(5, Woodpile.level(0L, big))
        assertEquals(0, Woodpile.level(Long.MAX_VALUE, big))
        assertEquals(0, Woodpile.level(Long.MAX_VALUE, warn100))
    }

    @Test fun leftFraction() {
        assertEquals(1f, Woodpile.left(0L, warn100)!!, 0f)
        assertEquals(0.75f, Woodpile.left(25L, warn100)!!, 1e-6f)
        assertEquals(0.5f, Woodpile.left(50L, warn100)!!, 1e-6f)
        assertEquals(0f, Woodpile.left(100L, warn100)!!, 0f)
        assertEquals(0f, Woodpile.left(500L, warn100)!!, 0f)
    }
}
