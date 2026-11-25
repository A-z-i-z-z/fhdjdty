package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.PaletteEntry
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteFrame
import dev.pocketagent.app.ui.pixel.SpriteRaster
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SkyTint: the reference channel values were recomputed with a throwaway script (blend towards the tint, each
 * channel rounded half up) and none sits within 0.02 of a rounding tie.
 */
class SkyTintTest {
    private val sky = 0xFF80C0FF.toInt()
    private val ghost = 0x80FFFFFF.toInt()
    private val grass = 0xFF3A5F2B.toInt()

    /** 2 x 2: sky blue, a half-transparent white, nothing, grass green; and a second frame that is all blue. */
    private fun sprite(name: String = "t"): Sprite = Sprite(
        name, 2, 2,
        listOf(SpriteFrame(intArrayOf(0, 1, -1, 2), 100), SpriteFrame(intArrayOf(0, 0, 0, 0), 100)),
        listOf(PaletteEntry('a', sky), PaletteEntry('b', ghost), PaletteEntry('c', grass)),
    )

    @Before fun clean() {
        SkyTint.clearCache()
    }

    @Test fun stepZeroIsTheBakedRaster() {
        val s = sprite()
        assertSame(SpriteRaster.plain(s, 0), SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 0))
        assertSame(SpriteRaster.plain(s, 0), SkyTint.tinted(s, 0, SkyTint.GROUND_TINT, -4))
        assertSame(SpriteRaster.plain(s, 0), SkyTint.dimmed(s, 0, 0))
        assertSame(SpriteRaster.plain(s, 1), SkyTint.dimmed(s, 1, -2))
        assertEquals(0, SkyTint.cacheSize())
    }

    @Test fun fullStepBlendsTowardsTheTint() {
        val s = sprite()
        val out = SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16)      // amount 0.35
        assertEquals(4, out.size)
        assertEquals(0xFF6497BE.toInt(), out[0])
        assertEquals(0x80B6C0BE.toInt(), out[1])                    // the alpha byte 0x80 is kept
        assertEquals(0, out[2])                                     // transparent stays exactly 0
        assertEquals(0xFF365834.toInt(), out[3])
    }

    @Test fun halfAndQuarterSteps() {
        val s = sprite()
        val half = SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 8)      // amount 0.175
        assertEquals(0xFF72ABDE.toInt(), half[0])
        assertEquals(0x80DBDFDE.toInt(), half[1])
        assertEquals(0xFF385B2F.toInt(), half[3])
        val quarter = SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 4)   // amount 0.0875
        assertEquals(0xFF79B6EF.toInt(), quarter[0])
        assertEquals(0xFF395D2D.toInt(), quarter[3])
    }

    @Test fun lowestStep() {
        val s = sprite()
        val out = SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 1)       // amount 0.021875
        assertEquals(0xFF7EBDFB.toInt(), out[0])
        assertEquals(0xFF3A5F2C.toInt(), out[3])
    }

    @Test fun eachLayerHasItsOwnBaseAmount() {
        assertEquals(0.35f, SkyTint.baseFor(SkyTint.NEAR_TINT), 0f)
        assertEquals(0.50f, SkyTint.baseFor(SkyTint.GROUND_TINT), 0f)
        assertEquals(0.50f, SkyTint.baseFor(SkyTint.FORE_TINT), 0f)
        assertEquals(0.50f, SkyTint.baseFor(0x123456), 0f)
        assertEquals(0.35f, SkyTint.baseFor(0xFF2F4A44.toInt()), 0f)   // the alpha byte of the tint is ignored
        val s = sprite()
        // ground base 0.5 at step 16 is an amount of 0.5: (0x80 + (0x56 - 0x80) * 0.5) = 107 = 0x6B
        val ground = SkyTint.tinted(s, 0, SkyTint.GROUND_TINT, 16)
        assertEquals(0x6B, (ground[0] shr 16) and 0xFF)
        // the explicit form gives the same raster as the four-argument form
        assertArrayEquals(
            SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16),
            SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16, 0.35f),
        )
    }

    @Test fun stepIsClamped() {
        val s = sprite()
        assertArrayEquals(SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16), SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 99))
        assertSame(SpriteRaster.plain(s, 0), SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, -1))
    }

    @Test fun theBakedRasterIsNeverMutated() {
        val s = sprite()
        val before = SpriteRaster.plain(s, 0).copyOf()
        SkyTint.tinted(s, 0, SkyTint.FORE_TINT, 16)
        SkyTint.dimmed(s, 0, 16)
        assertArrayEquals(before, SpriteRaster.plain(s, 0))
    }

    @Test fun dimmedReferenceValues() {
        val warm = Sprite(
            "warm", 2, 1, listOf(SpriteFrame(intArrayOf(0, 1), 100)),
            listOf(PaletteEntry('a', 0xFFFFC080.toInt()), PaletteEntry('b', 0x80204060.toInt())),
        )
        val full = SkyTint.dimmed(warm, 0, 16)
        assertEquals(0xFF8C6A46.toInt(), full[0])
        assertEquals(0x80122335.toInt(), full[1])
        val half = SkyTint.dimmed(warm, 0, 8)
        assertEquals(0xFFC69563.toInt(), half[0])
        assertEquals(0x8019324A.toInt(), half[1])
    }

    @Test fun blackAndTransparentStayPut() {
        val s = Sprite(
            "dark", 2, 1, listOf(SpriteFrame(intArrayOf(0, -1), 100)),
            listOf(PaletteEntry('a', 0xFF000000.toInt())),
        )
        assertEquals(0xFF000000.toInt(), SkyTint.dimmed(s, 0, 16)[0])
        assertEquals(0, SkyTint.dimmed(s, 0, 16)[1])
        // a black pixel tinted towards 2F4A44 at 0.35: 47 * 0.35 = 16.45, 74 * 0.35 = 25.9, 68 * 0.35 = 23.8
        assertEquals(0xFF101A18.toInt(), SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16)[0])
    }

    @Test fun resultsAreCachedPerSpriteFrameStepAndColour() {
        val s = sprite()
        val a = SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 8)
        assertSame(a, SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 8))
        assertEquals(1, SkyTint.cacheSize())
        assertNotSame(a, SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 9))
        assertNotSame(a, SkyTint.tinted(s, 1, SkyTint.NEAR_TINT, 8))
        assertNotSame(a, SkyTint.tinted(s, 0, SkyTint.GROUND_TINT, 8))
        // a tint and a dim of the same step are different entries
        val d = SkyTint.dimmed(s, 0, 8)
        assertSame(d, SkyTint.dimmed(s, 0, 8))
        assertNotSame(a, d)
        // an equal looking sprite is another sprite: the key is the identity
        assertNotSame(a, SkyTint.tinted(sprite(), 0, SkyTint.NEAR_TINT, 8))
        assertEquals(6, SkyTint.cacheSize())
    }

    @Test fun cacheIsBounded() {
        val s = sprite()
        for (i in 0 until SkyTint.CACHE_LIMIT * 3) {
            SkyTint.tinted(s, 0, 0x100000 + i, 1 + (i % 16))
        }
        assertTrue(SkyTint.cacheSize() <= SkyTint.CACHE_LIMIT)
        // after it started again a repeated call still works and still returns an equal raster
        assertArrayEquals(
            SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16),
            SkyTint.tinted(s, 0, SkyTint.NEAR_TINT, 16),
        )
    }

    // -- the dithered horizon ---------------------------------------------------------------------------------

    private val page = 0xFFABCDEF.toInt()
    private val base = 0xFF112233.toInt()

    private fun filled(w: Int, h: Int): PixelStage {
        val st = PixelStage(w, h)
        st.pixels.fill(base)
        return st
    }

    private fun xsOfPage(st: PixelStage, y: Int): List<Int> =
        (0 until st.width).filter { st.pixels[y * st.width + it] == page }

    @Test fun horizonPatternInTheLastFourRows() {
        val st = filled(16, 8)
        SkyTint.horizon(st, page)
        for (y in 0 until 4) assertEquals("row $y untouched", emptyList<Int>(), xsOfPage(st, y))
        // Bayer value below the share 0.15, 0.40, 0.65, 0.90 of the row
        assertEquals(listOf(0, 4, 8, 12), xsOfPage(st, 4))
        assertEquals(listOf(1, 5, 9, 13), xsOfPage(st, 5))
        assertEquals(listOf(0, 2, 3, 4, 6, 7, 8, 10, 11, 12, 14, 15), xsOfPage(st, 6))
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15), xsOfPage(st, 7))
    }

    @Test fun horizonLeavesOtherPixelsAlone() {
        val st = filled(16, 8)
        SkyTint.horizon(st, page)
        for (y in 0 until 8) for (x in 0 until 16) {
            val p = st.pixels[y * 16 + x]
            assertTrue("pixel $x,$y is the page or the base", p == page || p == base)
            if (y < 4) assertEquals(base, p)
        }
    }

    @Test fun horizonWithoutPageColourDoesNothing() {
        val st = filled(16, 8)
        SkyTint.horizon(st, 0)
        assertTrue(st.pixels.all { it == base })
    }

    @Test fun horizonCoverGrowsTowardsTheBottom() {
        val st = filled(64, 12)
        SkyTint.horizon(st, page)
        val counts = (8 until 12).map { xsOfPage(st, it).size }
        // 64 columns: a quarter, a quarter, three quarters, three quarters (rows 8..11 are y and 3 = 0..3)
        assertEquals(listOf(16, 16, 48, 48), counts)
        assertTrue(counts.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0, xsOfPage(st, 7).size)
    }

    @Test fun horizonLastRowIsAtLeastThreeQuartersPage() {
        // the cover of the last row depends on y and 3: 75 per cent for 1 and 3, 100 per cent for 0 and 2
        for (h in 4..11) {
            val st = filled(64, h)
            SkyTint.horizon(st, page)
            val want = if (((h - 1) and 1) == 0) 64 else 48
            assertEquals("height $h", want, xsOfPage(st, h - 1).size)
        }
    }

    @Test fun horizonIsDrawnOpaque() {
        val st = filled(16, 8)
        SkyTint.horizon(st, 0x00ABCDEF)
        assertEquals(0xFFABCDEF.toInt(), st.pixels[4 * 16 + 0])
    }

    @Test fun horizonOnVeryLowStages() {
        val one = filled(8, 1)
        SkyTint.horizon(one, page)                                  // the single row is the last of four: share 0.90
        assertTrue(xsOfPage(one, 0).isNotEmpty())
        val three = filled(8, 3)
        SkyTint.horizon(three, page)                                // must not throw
        assertEquals(3, three.height)
    }

    @Test fun horizonIsDeterministic() {
        val a = filled(37, 9)
        val b = filled(37, 9)
        SkyTint.horizon(a, page)
        SkyTint.horizon(b, page)
        assertArrayEquals(a.pixels, b.pixels)
    }
}
