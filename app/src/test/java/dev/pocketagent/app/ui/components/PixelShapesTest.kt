package dev.pocketagent.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure geometry behind the sheet corners and the scrim. */
class PixelShapesTest {
    @Test
    fun stepCornerOffsetsForN1N2N3() {
        assertEquals(listOf(0f to 2f, 2f to 2f, 2f to 0f), stepCornerOffsets(1, 2f))
        assertEquals(listOf(0f to 6f, 3f to 6f, 3f to 3f, 6f to 3f, 6f to 0f), stepCornerOffsets(2, 3f))
        assertEquals(
            listOf(0f to 3f, 1f to 3f, 1f to 2f, 2f to 2f, 2f to 1f, 3f to 1f, 3f to 0f),
            stepCornerOffsets(3, 1f),
        )
        assertEquals(listOf(0f to 0f), stepCornerOffsets(0, 5f))
        for (n in 0..4) {
            val pts = stepCornerOffsets(n, 4f)
            assertEquals(1 + 2 * n, pts.size)
            assertEquals(0f to n * 4f, pts.first())
            assertEquals(n * 4f to 0f, pts.last())
            for (i in 1 until pts.size) {
                assertTrue("x never goes back", pts[i].first >= pts[i - 1].first)
                assertTrue("y never goes down", pts[i].second <= pts[i - 1].second)
            }
        }
    }

    @Test
    fun ditherCellCounts() {
        assertEquals(listOf(0, 2, 4, 6, 8), (0..4).map { DitherPattern.cells(it).size })
        assertEquals(0, DitherPattern.cells(-3).size)
        assertEquals(8, DitherPattern.cells(9).size)
    }

    @Test
    fun ditherStepsAreSupersets() {
        for (s in 1..DitherPattern.STEPS) {
            assertTrue("step $s contains step ${s - 1}", DitherPattern.cells(s).containsAll(DitherPattern.cells(s - 1)))
        }
        for (s in 0..DitherPattern.STEPS) {
            assertTrue(DitherPattern.cells(s).all { it in 0..15 })
        }
    }

    @Test
    fun ditherStep4IsBalanced() {
        val cells = DitherPattern.cells(4)
        for (row in 0 until 4) assertEquals("row $row", 2, (0 until 4).count { (row * 4 + it) in cells })
        for (col in 0 until 4) assertEquals("column $col", 2, (0 until 4).count { (it * 4 + col) in cells })
    }
}
