package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SceneGeometry is the one source of positions. With the neutral inputs every field equals the constant of the
 * old picture (the numbers of SceneRenderer before Wave 1a: g = height - 34, fx = (width * 0.6f).toInt(),
 * fbase = height - 3, the fore sprite at height - 11, the art centred at (width - 176) / 2).
 * The other reference values were recomputed with a throwaway script (integer maths, half up) before they were
 * written here; none of them sits on a rounding tie.
 */
class SceneGeometryTest {
    @Test fun neutralInputsGiveTheOldConstants() {
        val geo = SceneGeometry.of(176, 160)
        assertEquals(126, geo.groundTop)            // g = 160 - 34
        assertEquals(88, geo.farRidgeTop)           // g - 38
        assertEquals(112, geo.nearRidgeTop)         // g - 14
        assertEquals(149, geo.foreTop)              // height - 11
        assertEquals(105, geo.hearthX)              // (176 * 0.6f).toInt()
        assertEquals(157, geo.hearthBase)           // height - 3
        assertEquals(0, geo.artX)
        assertEquals(102, geo.horizonRow)           // g - 24
        assertEquals(0, geo.landDy)
        assertEquals(0, geo.ridgeDy)
        assertEquals(0, geo.skyDy)
        assertEquals(123, geo.woodpileX)
        assertEquals(142, geo.woodpileY)
        assertTrue(geo.showProps)
        assertEquals(0, geo.bandRows)
        assertEquals(176, geo.width)
        assertEquals(160, geo.height)
    }

    @Test fun neutralInputsOnTheRealStageSize() {
        // the S24 Ultra stage without a band is about 155 by 110
        val geo = SceneGeometry.of(155, 110)
        assertEquals(110 - 34, geo.groundTop)
        assertEquals((155 * 0.6f).toInt(), geo.hearthX)
        assertEquals(110 - 3, geo.hearthBase)
        assertEquals(110 - 11, geo.foreTop)
        assertEquals(-10, geo.artX)                 // the 176 px art is cropped at both sides: -21 / 2 truncates to -10
    }

    @Test fun fullCollapseMovesTheLandByTheBand() {
        val geo = SceneGeometry.of(155, 120, 31, 1f)
        assertEquals(31, geo.landDy)
        assertEquals(21, geo.ridgeDy)               // max(round(0.6 * 31) = 19, 31 - 10 = 21)
        assertEquals(11, geo.skyDy)                 // round(0.35 * 31) = 10.85
        assertEquals(55, 120 - 34 - 31)
        assertEquals(86, geo.groundTop)             // g0 + landDy
        assertEquals(38, geo.farRidgeTop)           // g0 - 38 + ridgeDy
        assertEquals(62, geo.nearRidgeTop)
        assertEquals(109, geo.foreTop)              // 120 - 11 - 31 + 31
        assertEquals(93, geo.hearthX)
        assertEquals(117, geo.hearthBase)           // 120 - 3 - 31 + 31
        assertEquals(42, geo.horizonRow)            // g0 - 24 + skyDy
        assertEquals(111, geo.woodpileX)
        assertEquals(102, geo.woodpileY)
        assertFalse(geo.showProps)
    }

    @Test fun uncollapsedWithABandSitsHigher() {
        val geo = SceneGeometry.of(155, 120, 31, 0f)
        assertEquals(55, geo.groundTop)             // 120 - 34 - 31
        assertEquals(17, geo.farRidgeTop)
        assertEquals(41, geo.nearRidgeTop)
        assertEquals(78, geo.foreTop)
        assertEquals(86, geo.hearthBase)
        assertEquals(31, geo.horizonRow)
        assertEquals(0, geo.landDy)
        assertTrue(geo.showProps)
        // the fire stays at the same distance from the ground whatever the band
        assertEquals(31, geo.hearthBase - geo.groundTop)
    }

    @Test fun halfCollapse() {
        val geo = SceneGeometry.of(155, 120, 31, 0.5f)
        assertEquals(16, geo.landDy)                // round(15.5) half up
        assertEquals(10, geo.ridgeDy)               // max(round(9.6) = 10, 6)
        assertEquals(6, geo.skyDy)                  // round(5.6)
        assertEquals(71, geo.groundTop)
        assertEquals(27, geo.farRidgeTop)
        assertEquals(51, geo.nearRidgeTop)
        assertEquals(94, geo.foreTop)
        assertEquals(102, geo.hearthBase)
        assertEquals(37, geo.horizonRow)
        assertTrue(geo.showProps)
    }

    @Test fun aBigBandOnTheHeroStage() {
        val half = SceneGeometry.of(155, 150, 84, 0.5f)
        assertEquals(42, half.landDy)
        assertEquals(32, half.ridgeDy)              // max(round(25.2) = 25, 42 - 10 = 32)
        assertEquals(15, half.skyDy)                // round(14.7)
        assertEquals(74, half.groundTop)
        assertEquals(26, half.farRidgeTop)
        assertEquals(50, half.nearRidgeTop)
        assertEquals(97, half.foreTop)
        assertEquals(105, half.hearthBase)
        assertEquals(23, half.horizonRow)
        assertEquals(90, half.woodpileY)

        val full = SceneGeometry.of(155, 150, 84, 1f)
        assertEquals(84, full.landDy)
        assertEquals(74, full.ridgeDy)
        assertEquals(29, full.skyDy)                // round(29.4)
        assertEquals(116, full.groundTop)
        assertEquals(68, full.farRidgeTop)
        assertEquals(147, full.hearthBase)
        assertEquals(37, full.horizonRow)
        assertFalse(full.showProps)
    }

    @Test fun collapseIsMonotone() {
        var prev = SceneGeometry.of(155, 150, 84, 0f)
        for (i in 1..32) {
            val cur = SceneGeometry.of(155, 150, 84, i / 32f)
            assertTrue("landDy at $i", cur.landDy >= prev.landDy)
            assertTrue("ridgeDy at $i", cur.ridgeDy >= prev.ridgeDy)
            assertTrue("skyDy at $i", cur.skyDy >= prev.skyDy)
            assertTrue("groundTop at $i", cur.groundTop >= prev.groundTop)
            assertTrue("farRidgeTop at $i", cur.farRidgeTop >= prev.farRidgeTop)
            assertTrue("nearRidgeTop at $i", cur.nearRidgeTop >= prev.nearRidgeTop)
            assertTrue("foreTop at $i", cur.foreTop >= prev.foreTop)
            assertTrue("hearthBase at $i", cur.hearthBase >= prev.hearthBase)
            assertTrue("horizonRow at $i", cur.horizonRow >= prev.horizonRow)
            // once the props are hidden they stay hidden
            if (!prev.showProps) assertFalse("showProps at $i", cur.showProps)
            prev = cur
        }
        assertEquals(84, prev.landDy)
    }

    @Test fun ridgesFollowTheLandAtAtLeastTenRowsLess() {
        for (i in 0..32) {
            val g = SceneGeometry.of(155, 150, 84, i / 32f)
            assertTrue("ridgeDy never exceeds landDy at $i", g.ridgeDy <= g.landDy)
            assertTrue("ridgeDy is never negative at $i", g.ridgeDy >= 0)
            assertTrue("land - ridge at most ten rows at $i", g.landDy - g.ridgeDy <= 10)
        }
    }

    @Test fun revealZeroLiftsTheLandByFortyEightRows() {
        val geo = SceneGeometry.of(155, 120, 31, 0f, 0f)
        assertEquals(48, geo.landDy)
        assertEquals(48, geo.ridgeDy)
        assertEquals(0, geo.skyDy)                  // the entrance never moves the sky
        assertEquals(103, geo.groundTop)            // 55 + 48
        assertEquals(65, geo.farRidgeTop)
        assertEquals(89, geo.nearRidgeTop)
        assertEquals(126, geo.foreTop)
        assertEquals(134, geo.hearthBase)
        assertEquals(31, geo.horizonRow)            // g0 - 24, the sky stays
        assertEquals(48, SceneGeometry.ENTER_ROWS)
    }

    @Test fun revealSweep() {
        assertEquals(24, SceneGeometry.of(155, 120, 31, 0f, 0.4f).landDy)   // smoothstep(0.5) = 0.5
        assertEquals(0, SceneGeometry.of(155, 120, 31, 0f, 0.8f).landDy)
        assertEquals(0, SceneGeometry.of(155, 120, 31, 0f, 1f).landDy)
        var prev = Int.MAX_VALUE
        for (i in 0..40) {
            val d = SceneGeometry.of(155, 120, 31, 0f, i / 40f).landDy
            assertTrue("landDy at reveal step $i", d <= prev)
            prev = d
        }
        assertEquals(0, prev)
    }

    @Test fun collapseAndRevealCombine() {
        val geo = SceneGeometry.of(155, 150, 84, 0.3f, 0.4f)
        assertEquals(49, geo.landDy)                // round(25.2) = 25, plus 24
        assertEquals(39, geo.ridgeDy)               // max(15, 15) + 24
        assertEquals(9, geo.skyDy)                  // round(8.75)
        assertEquals(81, geo.groundTop)
        assertEquals(33, geo.farRidgeTop)
        assertEquals(112, geo.hearthBase)
        assertEquals(17, geo.horizonRow)
        assertTrue(geo.showProps)
    }

    @Test fun propsAreHiddenFromThreeQuartersOfTheCollapse() {
        assertTrue(SceneGeometry.of(155, 150, 84, 0.74f).showProps)
        assertFalse(SceneGeometry.of(155, 150, 84, 0.75f).showProps)
        assertFalse(SceneGeometry.of(155, 150, 84, 1f).showProps)
        assertTrue(SceneGeometry.of(155, 150, 0, 0f).showProps)
        // without a band the collapse input still decides: the flag follows the clamped collapse
        assertFalse(SceneGeometry.of(155, 150, 0, 1f).showProps)
    }

    @Test fun inputsAreClampedAndNaNIsNeutral() {
        val neutral = SceneGeometry.of(155, 120, 31)
        val nan = SceneGeometry.of(155, 120, 31, Float.NaN, Float.NaN)
        assertEquals(neutral.groundTop, nan.groundTop)
        assertEquals(neutral.hearthBase, nan.hearthBase)
        assertEquals(neutral.landDy, nan.landDy)
        val over = SceneGeometry.of(155, 120, 31, 7f, 9f)
        val one = SceneGeometry.of(155, 120, 31, 1f, 1f)
        assertEquals(one.groundTop, over.groundTop)
        val under = SceneGeometry.of(155, 120, 31, -3f, -2f)
        val zero = SceneGeometry.of(155, 120, 31, 0f, 0f)
        assertEquals(zero.groundTop, under.groundTop)
        // a negative band counts as none
        assertEquals(SceneGeometry.of(155, 120).groundTop, SceneGeometry.of(155, 120, -5, 1f).groundTop)
    }

    @Test fun woodpileSitsRightOfTheFire() {
        for (collapse in listOf(0f, 0.3f, 0.9f)) {
            val geo = SceneGeometry.of(155, 150, 84, collapse)
            assertEquals(geo.hearthX + 18, geo.woodpileX)
            assertEquals(geo.hearthBase - 15, geo.woodpileY)
        }
    }

    @Test fun relationsHoldForEveryInput() {
        for (w in intArrayOf(8, 120, 155, 176, 300)) {
            for (h in intArrayOf(44, 110, 150, 200)) {
                for (band in intArrayOf(0, 31, 84)) {
                    for (c in floatArrayOf(0f, 0.4f, 1f)) {
                        for (r in floatArrayOf(0f, 0.4f, 1f)) {
                            val g = SceneGeometry.of(w, h, band, c, r)
                            assertEquals(g.groundTop + 23, g.foreTop)
                            assertEquals(g.groundTop + 31, g.hearthBase)
                            assertEquals(g.groundTop - 38 + (g.ridgeDy - g.landDy), g.farRidgeTop)
                            assertEquals(24, g.nearRidgeTop - g.farRidgeTop)
                            assertEquals((w * 0.6f).toInt(), g.hearthX)
                            assertEquals((w - 176) / 2, g.artX)
                        }
                    }
                }
            }
        }
    }

    @Test fun tinyStagesDoNotThrow() {
        val g = SceneGeometry.of(10, 40)
        assertEquals(6, g.groundTop)
        assertEquals(6, g.hearthX)
        assertEquals(37, g.hearthBase)
        assertEquals(-83, g.artX)
        assertEquals(-18, g.horizonRow)             // below the top: the sky strip is hidden on such a stage
    }

    @Test fun constants() {
        assertEquals(34, SceneGeometry.GROUND_H)
        assertEquals(38, SceneGeometry.FIRE_FOOTPRINT_ROWS)
    }
}
