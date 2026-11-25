package dev.pocketagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SearchViewTest {
    @Test fun noMatches() {
        val v = searchViewOf(active = true, query = "zzz", matches = emptyList(), pos = Int.MAX_VALUE, flashId = null)
        assertEquals(-1, v.position)
        assertNull(v.currentId)
        assertNull(v.outlineId)
        assertEquals("zzz", v.query)
    }

    @Test fun positionIsClamped() {
        val ids = listOf("a", "b", "c")
        val last = searchViewOf(true, "x", ids, Int.MAX_VALUE, null)
        assertEquals(2, last.position)
        assertEquals("c", last.currentId)
        assertEquals("c", last.outlineId)
        val first = searchViewOf(true, "x", ids, -5, null)
        assertEquals(0, first.position)
        assertEquals("a", first.currentId)
    }

    @Test fun flashOutlineWhenNotSearching() {
        val v = searchViewOf(active = false, query = "", matches = emptyList(), pos = 0, flashId = "m1")
        assertFalse(v.active)
        assertNull(v.currentId)
        assertEquals("m1", v.outlineId)
        // While searching, the flash does not win over the current match.
        val s = searchViewOf(true, "q", listOf("a"), 0, "m1")
        assertEquals("a", s.outlineId)
    }
}
