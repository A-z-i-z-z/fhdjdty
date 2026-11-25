package dev.pocketagent.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Existing panels keep their pixels: an old call resolves to the variant it always drew. */
class PanelVariantTest {
    @Test
    fun raisedWithHighlightEqualsToday() {
        assertEquals(PanelVariant.RAISED, resolveVariant(true, PanelVariant.RAISED))
    }

    @Test
    fun highlightFalseMeansFlat() {
        assertEquals(PanelVariant.FLAT, resolveVariant(false, PanelVariant.RAISED))
    }

    @Test
    fun variantResolution() {
        val others = listOf(PanelVariant.FLAT, PanelVariant.INSET, PanelVariant.GLOWING, PanelVariant.FLOATING)
        for (v in others) {
            assertEquals(v, resolveVariant(true, v))
            assertEquals(v, resolveVariant(false, v))
        }
        assertEquals(5, PanelVariant.values().size)
    }
}
