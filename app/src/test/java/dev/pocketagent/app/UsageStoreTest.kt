package dev.pocketagent.app

import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.data.UsageDay
import dev.pocketagent.app.data.UsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UsageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L   // some instant in 2027
    private fun store(f: File = File(tmp.root, "chat/usage.json")) = UsageStore(f, CoroutineScope(Dispatchers.IO), { now }, 20L)
    private fun today() = UsageStore.dayOf(now)
    private val dayMs = 36L * 3600 * 1000   // 36 hours always lands on another calendar day, DST or not

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(10)
        }
    }

    @Test fun turnsAddUpPerDayAndModel() {
        val s = store()
        s.record("", 100, 20)
        s.record("", 50, 10)
        s.record("google/x", 7, 3)
        val rows = s.days.value
        assertEquals(2, rows.size)
        val hermes = rows.single { it.model == "" }
        assertEquals(UsageDay(today(), "", 2, 150, 30), hermes)
        assertEquals(UsageDay(today(), "google/x", 1, 7, 3), rows.single { it.model == "google/x" })
        assertEquals(190L, s.todayTotal())
    }

    @Test fun aTurnWithoutTokensIsNotRecordedAndNegativesAreClamped() {
        val s = store()
        s.record("", 0, 0)
        assertTrue(s.days.value.isEmpty())
        s.record("", -5, 10)
        assertEquals(UsageDay(today(), "", 1, 0, 10), s.days.value.single())
    }

    @Test fun todayTotalOnlyCountsToday() {
        val s = store()
        s.record("", 100, 100)
        now += dayMs
        assertEquals(0L, s.todayTotal())
        s.record("", 5, 5)
        assertEquals(10L, s.todayTotal())
        assertEquals("newest day first", listOf(today(), UsageStore.dayOf(now - dayMs)), s.days.value.map { it.day })
    }

    @Test fun rowsOfADayAreOrderedByTokensAndOldDaysBeyond400AreDropped() {
        val s = store()
        s.record("small", 1, 1)
        s.record("big", 100, 100)
        assertEquals(listOf("big", "small"), s.days.value.map { it.model })
        val first = java.time.LocalDate.of(2024, 1, 1)
        val rows = (0 until 450).map { UsageDay(first.plusDays(it.toLong()).toString(), "", 1, 1, 1) }
        val kept = UsageStore.normalize(rows)
        assertEquals(400, kept.size)
        assertEquals("newest day first", first.plusDays(449).toString(), kept.first().day)
        assertEquals("the 50 oldest days are gone", first.plusDays(50).toString(), kept.last().day)
    }

    @Test fun overBudgetFollowsModeAndLimit() {
        val s = store()
        s.record("", 600, 400)
        assertFalse("no budget set", s.overBudget())
        s.setBudget(UsageBudget(1000, BudgetMode.OFF))
        assertFalse("mode OFF never counts", s.overBudget())
        s.setBudget(UsageBudget(1000, BudgetMode.WARN))
        assertTrue(s.overBudget())
        s.setBudget(UsageBudget(1001, BudgetMode.BLOCK))
        assertFalse(s.overBudget())
        s.setBudget(UsageBudget(0, BudgetMode.BLOCK))
        assertFalse("0 = no limit", s.overBudget())
        s.setBudget(UsageBudget(-5, BudgetMode.BLOCK))
        assertEquals("negative limits are clamped", 0L, s.budget.value.dailyTokens)
        s.setBudget(UsageBudget(1000, BudgetMode.BLOCK))
        now += dayMs
        assertFalse("a new day starts at zero", s.overBudget())
    }

    @Test fun everythingSurvivesARestartAndClearKeepsTheBudget() {
        val f = File(tmp.root, "chat/usage.json")
        val a = store(f)
        a.record("", 100, 20)
        a.record("google/x", 1, 2)
        a.setBudget(UsageBudget(5000, BudgetMode.WARN))
        a.flush()
        val b = store(f)
        assertEquals(a.days.value, b.days.value)
        assertEquals(UsageBudget(5000, BudgetMode.WARN), b.budget.value)
        b.clear()
        b.flush()
        val c = store(f)
        assertTrue(c.days.value.isEmpty())
        assertEquals(UsageBudget(5000, BudgetMode.WARN), c.budget.value)
    }

    @Test fun writesAreDebounced() {
        val f = File(tmp.root, "chat/usage.json")
        val s = store(f)
        s.record("", 1, 1)
        waitFor { f.isFile }
        assertTrue(f.readText().contains("\"days\""))
    }

    @Test fun suspendedWritesNeverReachTheDisk() {
        val f = File(tmp.root, "chat/usage.json")
        val s = store(f)
        s.record("", 1, 1)
        s.flush()
        val before = f.readText()
        s.suspendWrites()
        s.record("", 500, 500)
        s.setBudget(UsageBudget(10, BudgetMode.BLOCK))
        s.flush()
        Thread.sleep(150)
        assertEquals(before, f.readText())
        assertEquals("still counted in memory", 1002L, s.todayTotal())
    }

    @Test fun aCorruptFileIsKeptAsBadAndTheStoreStartsEmpty() {
        val f = File(tmp.root, "chat/usage.json")
        f.parentFile.mkdirs()
        f.writeText("{ nope")
        val s = store(f)
        assertTrue(s.days.value.isEmpty())
        assertEquals("{ nope", File(f.parentFile, "usage.json.bad").readText())
        s.record("", 1, 1)
        s.flush()
        assertEquals(1, store(f).days.value.size)
    }

    @Test fun malformedRowsAreSkipped() {
        val f = File(tmp.root, "chat/usage.json")
        f.parentFile.mkdirs()
        f.writeText("""{"v":1,"days":[{"day":"nonsense","in":5},{"day":"2026-01-02","model":"m","turns":2,"in":10,"out":4},"junk"],"budget":{"dailyTokens":-3,"mode":"WHATEVER"}}""")
        val s = store(f)
        assertEquals(listOf(UsageDay("2026-01-02", "m", 2, 10, 4)), s.days.value)
        assertEquals(UsageBudget(0, BudgetMode.OFF), s.budget.value)
    }
}
