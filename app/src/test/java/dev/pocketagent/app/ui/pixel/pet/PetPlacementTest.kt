package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.ui.pixel.stage.SceneGeometry
import dev.pocketagent.app.ui.pixel.stage.StageGrid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPlacementTest {
    @Test fun formulaMatchesRenderer() {
        for (w in listOf(120, 155, 160, 176)) {
            for (h in listOf(60, 120)) {
                val geo = SceneGeometry.of(w, h)
                // the old renderer: fx = (w * 0.6f).toInt() and fbase = h - 3; Pêt's feet are 27 left of the fire
                val fx = (w * 0.6f).toInt()
                val fbase = h - 3
                assertEquals("feetX for $w x $h", fx - 27, PetPlacement.feetX(geo))
                assertEquals("feetY for $w x $h", fbase, PetPlacement.feetY(geo))
                // the sprite's left edge stays where the old code drew it (fx - 43)
                assertEquals(fx - 43, PetPlacement.feetX(geo) - 16)
                assertEquals(fbase - 31, PetPlacement.feetY(geo) - 31)
            }
        }
    }

    @Test fun feetFollowTheHearthWithABand() {
        val geo = SceneGeometry.of(155, 150, 30, 0f)
        assertEquals(150 - 3 - 30, PetPlacement.feetY(geo))
        val collapsed = SceneGeometry.of(155, 150, 30, 1f)
        assertEquals(150 - 3, PetPlacement.feetY(collapsed))
        assertEquals(geo.hearthX - 27, PetPlacement.feetX(geo))
    }

    @Test fun hitRect() {
        val geo = SceneGeometry.of(176, 150)           // hearthX 105, hearthBase 147: feet (78, 147), box x 62..93, y 116..147
        val grid = StageGrid(5, 176, 150, 0, 0)
        val r = PetPlacement.hitRect(grid, geo)
        // grown 3 stage pixels left, top and bottom, not right
        assertArrayEquals(floatArrayOf(295f, 565f, 470f, 755f), r, 0f)
        // offsets move the whole rectangle
        val moved = PetPlacement.hitRect(StageGrid(5, 176, 150, -4, 7), geo)
        assertArrayEquals(floatArrayOf(291f, 572f, 466f, 762f), moved, 0f)
        // a small box grows to the left, and up and down by the same amount
        val big = PetPlacement.hitRect(grid, geo, minPx = 200f)
        assertArrayEquals(floatArrayOf(270f, 560f, 470f, 760f), big, 0f)
        // a box that is big enough is left alone
        assertArrayEquals(r, PetPlacement.hitRect(grid, geo, minPx = 150f), 0f)
    }

    @Test fun hitsAtScales() {
        val geo = SceneGeometry.of(176, 150)
        for (scale in 5..9) {
            for ((ox, oy) in listOf(0 to 0, -3 to 11, 8 to 0)) {
                val grid = StageGrid(scale, 176, 150, ox, oy)
                val r = PetPlacement.hitRect(grid, geo)
                val midX = (r[0] + r[2]) / 2f
                val midY = (r[1] + r[3]) / 2f
                assertTrue("middle at scale $scale", PetPlacement.hits(midX, midY, grid, geo))
                assertTrue(PetPlacement.hits(r[0], midY, grid, geo))
                assertFalse(PetPlacement.hits(r[0] - 0.5f, midY, grid, geo))
                assertTrue(PetPlacement.hits(r[2] - 0.5f, midY, grid, geo))
                assertFalse("the right edge belongs to the fire", PetPlacement.hits(r[2], midY, grid, geo))
                assertTrue(PetPlacement.hits(midX, r[1], grid, geo))
                assertFalse(PetPlacement.hits(midX, r[1] - 0.5f, grid, geo))
                assertTrue(PetPlacement.hits(midX, r[3] - 0.5f, grid, geo))
                assertFalse(PetPlacement.hits(midX, r[3], grid, geo))
                // the box is 32 plus 3 stage pixels wide and 32 plus 3 plus 3 high
                assertEquals(35f, (r[2] - r[0]) / scale, 0f)
                assertEquals(38f, (r[3] - r[1]) / scale, 0f)
            }
        }
    }

    @Test fun minimumSizeIsHonoured() {
        val geo = SceneGeometry.of(176, 150)
        val grid = StageGrid(5, 176, 150, 0, 0)
        val r = PetPlacement.hitRect(grid, geo, minPx = 300f)
        assertEquals(300f, r[2] - r[0], 0f)
        assertEquals(300f, r[3] - r[1], 0f)
        assertEquals(470f, r[2], 0f)
        assertTrue(PetPlacement.hits(r[0] + 1f, (r[1] + r[3]) / 2f, grid, geo, minPx = 300f))
    }
}
