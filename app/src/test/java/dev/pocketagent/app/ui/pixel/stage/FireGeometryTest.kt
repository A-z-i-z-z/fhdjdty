package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireGeometryTest {
    @Test fun matchesTodaysConstants() {
        val g = FireGeometry(SceneGeometry.of(176, 160))
        assertEquals(105, g.fx)
        assertEquals(157, g.base)
        assertEquals(89, g.spriteX)
        assertEquals(122, g.spriteY)
        assertEquals(93, FireGeometry(SceneGeometry.of(155, 160)).fx)
        // the old renderer's own statements
        for ((w, h) in listOf(155 to 105, 176 to 160, 200 to 120, 120 to 80)) {
            val fg = FireGeometry(SceneGeometry.of(w, h))
            assertEquals((w * 0.6f).toInt(), fg.fx)
            assertEquals(h - 3, fg.base)
            assertEquals(fg.fx - 16, fg.spriteX)
            assertEquals(fg.base - 35, fg.spriteY)
        }
    }

    @Test fun followsTheBandAndTheCollapse() {
        // a 31 row band, fully collapsed: the land moved down by the band, so the base is where it was without a band
        val collapsed = FireGeometry(SceneGeometry.of(155, 120, bandRows = 31, collapse = 1f))
        assertEquals(120 - 3, collapsed.base)
        assertEquals(120 - 3 - 35, collapsed.spriteY)
        // open, with the band: the fire sits above the band
        val open = FireGeometry(SceneGeometry.of(155, 120, bandRows = 31, collapse = 0f))
        assertEquals(120 - 3 - 31, open.base)
        assertEquals(93, open.fx)
        // the entrance lifts the land: a revealed-at-0 fire is lower than the settled one
        val entering = FireGeometry(SceneGeometry.of(155, 120, reveal = 0f))
        assertTrue(entering.base > FireGeometry(SceneGeometry.of(155, 120)).base)
    }

    @Test fun hitRectScalesWithGrid() {
        val g = FireGeometry(SceneGeometry.of(176, 160))
        val grid = StageGrid(scale = 7, width = 176, height = 160, offsetX = 3, offsetY = 5)
        val hit = g.hitRect(grid, minPx = 10)
        assertEquals(3 + (g.fx - 12) * 7, hit.left)
        assertEquals(5 + (g.base - 29) * 7, hit.top)
        assertEquals(24 * 7, hit.width)
        assertEquals(30 * 7, hit.height)
        assertEquals(hit.left + 168, hit.right)
        assertEquals(hit.top + 210, hit.bottom)
    }

    @Test fun hitRectGrowsToMinTouch() {
        val g = FireGeometry(SceneGeometry.of(176, 160))
        val grid = StageGrid(scale = 1, width = 176, height = 160, offsetX = 0, offsetY = 0)
        val small = g.hitRect(grid, minPx = 1)
        assertEquals(24, small.width)
        assertEquals(30, small.height)
        val big = g.hitRect(grid, minPx = 126)
        assertTrue(big.width >= 126)
        assertTrue(big.height >= 126)
        // grown around its centre
        assertEquals(small.left + small.width / 2, big.left + big.width / 2)
        assertEquals(small.top + small.height / 2, big.top + big.height / 2)
        // an odd growth still gives at least the minimum
        val odd = g.hitRect(grid, minPx = 127)
        assertEquals(127, odd.width)
        assertEquals(127, odd.height)
        // a box that is big enough is left alone
        val scaled = g.hitRect(StageGrid(scale = 7, width = 176, height = 160, offsetX = 0, offsetY = 0), minPx = 100)
        assertEquals(24 * 7, scaled.width)
    }

    @Test fun hitBoxStaysClearOfThePetAndTheWoodpile() {
        // Pet spans fx - 43 .. fx - 12 and the woodpile starts 18 columns right of the fire: the box is between
        val geo = SceneGeometry.of(176, 160)
        val g = FireGeometry(geo)
        val grid = StageGrid(scale = 1, width = 176, height = 160, offsetX = 0, offsetY = 0)
        val hit = g.hitRect(grid, minPx = 1)
        assertTrue(hit.right <= geo.woodpileX + 6)
        assertTrue(hit.left >= g.fx - 12)
    }
}
