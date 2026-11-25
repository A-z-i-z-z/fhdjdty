package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherArtTest {
    private fun solid(a: Int) = (a shl 24) or 0x336699

    @Test fun topEdgesFindTheFirstSolidRow() {
        // 4 columns, 5 rows: column 0 solid from row 2, column 1 from row 0, column 2 empty, column 3 from row 4
        val w = 4
        val h = 5
        val px = IntArray(w * h)
        for (y in 2 until h) px[y * w + 0] = solid(255)
        for (y in 0 until h) px[y * w + 1] = solid(255)
        px[4 * w + 3] = solid(255)
        assertArrayEquals(intArrayOf(2, 0, -1, 4), WeatherArt.topEdges(px, w, h))
    }

    @Test fun alphaThresholdIs128() {
        val w = 3
        val h = 3
        val px = IntArray(w * h)
        // column 0: faint rows first (alpha 127), solid from row 2
        px[0 * w + 0] = solid(127)
        px[1 * w + 0] = solid(127)
        px[2 * w + 0] = solid(128)
        // column 1: alpha 128 at row 1
        px[1 * w + 1] = solid(128)
        // column 2: only alpha 127
        px[0 * w + 2] = solid(127)
        assertArrayEquals(intArrayOf(2, 1, -1), WeatherArt.topEdges(px, w, h))
        assertEquals(128, WeatherArt.EDGE_ALPHA)
    }

    @Test fun degenerateSizes() {
        assertEquals(0, WeatherArt.topEdges(IntArray(0), 0, 0).size)
        assertEquals(0, WeatherArt.topEdges(IntArray(10), -3, 4).size)
        assertArrayEquals(intArrayOf(-1, -1), WeatherArt.topEdges(IntArray(0), 2, 0))
        // an array that is too short counts as transparent where it ends
        val px = IntArray(5)
        px[1] = solid(255)
        px[4] = solid(255)
        assertArrayEquals(intArrayOf(-1, 0, -1), WeatherArt.topEdges(px, 3, 3))
    }

    @Test fun cloudTonesKnob() {
        assertEquals(3, WeatherArt.CLOUD_TONES)
    }
}
