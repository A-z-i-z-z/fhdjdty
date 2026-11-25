package dev.pocketagent.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First slice: the pure ordering of row slots ([orderRows]) with a plain stand-in for a row. The tests that need
 * the interface `HomeRow` and the registry `HomeRows` (ids unique and lower case, the registry empty in Wave 1a,
 * the cap of five visible rows) are added together with those symbols in the slice that writes them.
 */
class HomeRowRegistryTest {
    private data class Fake(val id: String, val slot: HomeSlot, val order: Int)

    private fun sorted(rows: List<Fake>) = orderRows(rows, { it.slot }, { it.order }, { it.id })

    @Test fun orderBySlotThenOrderThenId() {
        val rows = listOf(
            Fake("z", HomeSlot.AFTER_RECENT, 0),
            Fake("b", HomeSlot.AFTER_AVATARS, 5),
            Fake("a", HomeSlot.AFTER_AVATARS, 5),
            Fake("c", HomeSlot.AFTER_AVATARS, 1),
            Fake("s2", HomeSlot.AFTER_SUGGESTIONS, 9),
            Fake("s1", HomeSlot.AFTER_SUGGESTIONS, -3),
        )
        assertEquals(listOf("s1", "s2", "c", "a", "b", "z"), sorted(rows).map { it.id })
    }

    @Test fun slotsFollowTheDeclarationOrder() {
        assertEquals(
            listOf(HomeSlot.AFTER_SUGGESTIONS, HomeSlot.AFTER_AVATARS, HomeSlot.AFTER_RECENT),
            HomeSlot.values().toList(),
        )
    }

    @Test fun orderIsIndependentOfInputOrder() {
        val rows = listOf(
            Fake("x", HomeSlot.AFTER_RECENT, 2), Fake("y", HomeSlot.AFTER_RECENT, 2), Fake("w", HomeSlot.AFTER_AVATARS, 7),
            Fake("v", HomeSlot.AFTER_SUGGESTIONS, 0),
        )
        val expected = sorted(rows)
        assertEquals(expected, sorted(rows.reversed()))
        assertEquals(expected, sorted(rows.shuffled(kotlin.random.Random(7))))
        assertEquals(listOf("v", "w", "x", "y"), expected.map { it.id })
    }

    @Test fun emptyAndSingle() {
        assertTrue(sorted(emptyList()).isEmpty())
        val one = Fake("only", HomeSlot.AFTER_AVATARS, 0)
        assertEquals(listOf(one), sorted(listOf(one)))
    }

    @Test fun inputIsNotChanged() {
        val rows = listOf(Fake("b", HomeSlot.AFTER_RECENT, 1), Fake("a", HomeSlot.AFTER_SUGGESTIONS, 1))
        val copy = rows.toList()
        sorted(rows)
        assertEquals(copy, rows)
    }

    @Test fun capAfterFilteringKeepsTheFirstFive() {
        // HomeScreen filters the rows that have content and then takes the first MAX_VISIBLE (five) of the ordered list
        val rows = (1..8).map { Fake("r$it", HomeSlot.AFTER_RECENT, it) }
        val shown = sorted(rows.filter { it.order % 2 == 1 || it.order >= 6 }).take(5)
        assertEquals(listOf("r1", "r3", "r5", "r6", "r7"), shown.map { it.id })
    }

    @Test fun equalKeysKeepInputOrder() {
        // a duplicate id is a mistake the registry test of a later slice rejects; the sort itself stays stable
        val first = Fake("dup", HomeSlot.AFTER_RECENT, 1)
        val second = Fake("dup", HomeSlot.AFTER_RECENT, 1).copy()
        val out = sorted(listOf(first, second))
        assertTrue(out[0] === first && out[1] === second)
    }
}
