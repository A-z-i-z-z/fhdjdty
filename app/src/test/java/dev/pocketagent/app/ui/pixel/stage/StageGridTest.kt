package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class StageGridTest {
    private fun grid(w: Float, h: Float): StageGrid {
        val g = StageGrid.forScene(w, h)
        assertNotNull("grid for ${w}x$h", g)
        return g!!
    }

    private fun check(w: Float, h: Float, scale: Int, gw: Int, gh: Int, ox: Int, oy: Int) {
        val g = grid(w, h)
        assertEquals("scale of ${w}x$h", scale, g.scale)
        assertEquals("width of ${w}x$h", gw, g.width)
        assertEquals("height of ${w}x$h", gh, g.height)
        assertEquals("offsetX of ${w}x$h", ox, g.offsetX)
        assertEquals("offsetY of ${w}x$h", oy, g.offsetY)
    }

    @Test fun forSceneMatchesToday() {
        check(1080f, 735f, 7, 155, 105, -2, 0)
        check(720f, 560f, 5, 144, 112, 0, 0)
        check(1440f, 1000f, 9, 160, 112, 0, -8)
        check(412f, 280f, 3, 138, 94, -1, -2)
    }

    @Test fun theGridAlwaysCoversTheSurface() {
        for (w in listOf(300f, 411f, 720f, 1080f, 1081f, 1440f, 2000f)) {
            for (h in listOf(200f, 561f, 735f, 1000f)) {
                val g = grid(w, h)
                assertEquals("whole numbers only", ceil(w / 176f).toInt().coerceAtLeast(1), g.scale)
                assertTrue("grid narrower than $w", g.width * g.scale >= w)
                assertTrue("grid lower than $h", g.height * g.scale >= h)
                assertTrue("grid must overhang, never leave a gap", g.offsetX <= 0 && g.offsetY <= 0)
            }
        }
    }

    @Test fun tooSmallIsNull() {
        assertNull(StageGrid.forScene(7.9f, 300f))
        assertNull(StageGrid.forScene(300f, 7.9f))
        assertNotNull(StageGrid.forScene(8f, 8f))
    }
}
