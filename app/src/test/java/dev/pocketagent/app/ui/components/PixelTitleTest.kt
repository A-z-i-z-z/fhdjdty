package dev.pocketagent.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The title chain: the first size that fits wins, and a missing glyph or no fit falls back to the system font. */
class PixelTitleTest {
    @Test
    fun pickTitleStepFirstThatFits() {
        val candidates = listOf(8, 6, 5, 4)
        assertEquals(8, pickTitleStep(10, 100, candidates))
        assertEquals(6, pickTitleStep(15, 100, candidates))
        assertEquals(5, pickTitleStep(20, 100, candidates))
        assertEquals(4, pickTitleStep(25, 100, candidates))
    }

    @Test
    fun pickTitleStepNullOnUnknownChar() {
        assertNull(pickTitleStep(null, 1000, listOf(8, 6, 4)))
    }

    @Test
    fun pickTitleStepNullWhenNothingFits() {
        assertNull(pickTitleStep(26, 100, listOf(8, 6, 5, 4)))
        assertNull(pickTitleStep(1, 0, listOf(8, 6)))
        assertNull(pickTitleStep(5, 100, emptyList()))
    }
}
