package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.PxlParser
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FireEdgeTest {
    private val sprites: SpriteSource by lazy { TestSprites.all() }
    private val names = listOf("fire_cold", "fire_out", "fire_low", "fire_mid", "fire_high")

    private val synthText = """
        palette
        g #A81F1A
        r #DC3818
        f #F7691A
        o #170E22
        sprite synth 8 8
        frame 100
        ........
        ...g....
        ...r.f..
        ..grf...
        ..gfr...
        ..rrf...
        .oooooo.
        ........
    """.trimIndent()

    private val synth: Sprite by lazy { PxlParser.parse("synthetic.pxl", synthText).single() }

    private val ox = 20
    private val oy = 20

    private fun edgeOnly(sprite: Sprite, frame: Int, t: Long, edge: Float, wind: Float): PixelStage {
        val stage = PixelStage(100, 100)
        FireEdge.draw(
            stage, FlameShape.of(sprite, frame), SpriteRaster.plain(sprite, frame), sprite.width, sprite.height,
            ox, oy, t, edge, wind,
        )
        return stage
    }

    private fun lit(stage: PixelStage): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until stage.height) for (x in 0 until stage.width) if (stage.pixels[y * stage.width + x] != 0) out.add(x to y)
        return out
    }

    /** The stage positions that the sprite, leaning with [wind], covers: worked out here, independent of FireEdge. */
    private fun coveredPositions(sprite: Sprite, frame: Int, wind: Float): Set<Pair<Int, Int>> {
        val raster = SpriteRaster.plain(sprite, frame)
        val out = HashSet<Pair<Int, Int>>()
        for (row in 0 until sprite.height) for (col in 0 until sprite.width) {
            if ((raster[row * sprite.width + col] ushr 24) != 0) out.add((ox + col + FireWind.shift(row, wind)) to (oy + row))
        }
        return out
    }

    @Test fun flameShapeTopsOnSyntheticSprite() {
        val shape = FlameShape.of(synth, 0)
        assertArrayEquals(intArrayOf(-1, -1, 3, 1, 3, -1, -1, -1), shape.top)
        assertArrayEquals(intArrayOf(-1, 3, 3, 2, 2, 2, -1, -1), shape.left)
        assertArrayEquals(intArrayOf(-1, 3, 5, 4, 4, 4, -1, -1), shape.right)
        assertEquals(1, shape.firstRow)
    }

    @Test fun strayPixelsAreSkipped() {
        val shape = FlameShape.of(synth, 0)
        // the lone 'f' at column 5, row 2 has no flame pixel under it: not a top ...
        assertEquals(-1, shape.top[5])
        // ... but it still counts for the extent of its row, which the side licks follow
        assertEquals(5, shape.right[2])
        // stones are not flame
        assertEquals(-1, shape.left[6])
    }

    @Test fun flameShapeOfRealSprites() {
        val cold = FlameShape.of(sprites["fire_cold"], 0)
        assertEquals(-1, cold.firstRow)
        assertTrue(cold.top.all { it == -1 })
        for (name in listOf("fire_low", "fire_mid", "fire_high")) {
            val sprite = sprites[name]
            for (frame in sprite.frames.indices) {
                val shape = FlameShape.of(sprite, frame)
                assertEquals(sprite.width, shape.top.size)
                assertEquals(sprite.height, shape.left.size)
                assertTrue("$name $frame has a flame", shape.firstRow in 0..22)
                for (top in shape.top) assertTrue(top in -1..22)
                for (row in 0 until sprite.height) {
                    if (row > 22) {
                        assertEquals(-1, shape.left[row])
                        assertEquals(-1, shape.right[row])
                    } else if (shape.left[row] >= 0) {
                        assertTrue(shape.left[row] <= shape.right[row])
                    }
                }
            }
        }
    }

    @Test fun flameShapesCacheKeepsOneShapePerFrame() {
        val cache = FlameShapes()
        val high = sprites["fire_high"]
        val a = cache.get(high, 0)
        assertSame(a, cache.get(high, 0))
        assertNotSame(a, cache.get(high, 1))
        assertNotSame(a, cache.get(sprites["fire_mid"], 0))
        assertArrayEquals(FlameShape.of(high, 0).top, a.top)
    }

    @Test fun neverOverdrawsOpaqueSpritePixels() {
        var edgePixels = 0
        for (name in names) {
            val sprite = sprites[name]
            for (frame in sprite.frames.indices) {
                val raster = SpriteRaster.plain(sprite, frame)
                for (wind in listOf(0f, 0.8f, -0.8f)) {
                    val covered = coveredPositions(sprite, frame, wind)
                    for (t in listOf(0L, 130L, 520L, 1_040L)) {
                        for (edge in listOf(1f, 0.5f)) {
                            // the sprite first, the edge on top of it: every sprite pixel must survive
                            val stage = PixelStage(100, 100)
                            FireEdge.blitLeaned(stage, sprite, frame, ox, oy, wind)
                            FireEdge.draw(
                                stage, FlameShape.of(sprite, frame), raster, sprite.width, sprite.height,
                                ox, oy, t, edge, wind,
                            )
                            for (row in 0 until sprite.height) for (col in 0 until sprite.width) {
                                val src = raster[row * sprite.width + col]
                                if ((src ushr 24) == 0) continue
                                val x = ox + col + FireWind.shift(row, wind)
                                assertEquals("$name f$frame wind $wind t $t edge $edge at $x,${oy + row}", src, stage.pixels[(oy + row) * stage.width + x])
                            }
                            // the edge alone never lands on a covered position
                            val only = edgeOnly(sprite, frame, t, edge, wind)
                            for (p in lit(only)) {
                                edgePixels++
                                assertFalse("$name f$frame wind $wind t $t: edge on a sprite pixel $p", p in covered)
                            }
                        }
                    }
                }
            }
        }
        assertTrue("the edge drew something somewhere", edgePixels > 200)
    }

    @Test fun windZeroFlameEqualsClassicBlit() {
        for (name in names) {
            val sprite = sprites[name]
            for (frame in sprite.frames.indices) {
                val plain = PixelStage(100, 100)
                plain.blit(sprite, frame, ox, oy)
                for (wind in listOf(0f, 0.05f, -0.09f, Float.NaN)) {
                    val leaned = PixelStage(100, 100)
                    FireEdge.blitLeaned(leaned, sprite, frame, ox, oy, wind)
                    assertArrayEquals("$name f$frame wind $wind", plain.pixels, leaned.pixels)
                }
            }
        }
    }

    @Test fun windLeansTheUpperRowsOnly() {
        val sprite = sprites["fire_high"]
        val plain = PixelStage(100, 100)
        plain.blit(sprite, 0, ox, oy)
        val leaned = PixelStage(100, 100)
        FireEdge.blitLeaned(leaned, sprite, 0, ox, oy, 1f)
        assertFalse(plain.pixels.contentEquals(leaned.pixels))
        assertEquals("no pixel is lost", lit(plain).size, lit(leaned).size)
        // rows 22 and below are where they were
        for (row in 22 until sprite.height) {
            for (x in 0 until 100) {
                assertEquals("row $row", plain.pixels[(oy + row) * 100 + x], leaned.pixels[(oy + row) * 100 + x])
            }
        }
        // the tip leans to the right (row 6 by 2 pixels, see the shift table), to the left for the opposite wind
        val lean = FireWind.shift(6, 1f)
        assertEquals(2, lean)
        val right = lit(leaned).filter { it.second == oy + 6 }.minOf { it.first }
        val flat = lit(plain).filter { it.second == oy + 6 }.minOf { it.first }
        assertEquals(flat + lean, right)
        val back = PixelStage(100, 100)
        FireEdge.blitLeaned(back, sprite, 0, ox, oy, -1f)
        assertEquals(flat - lean, lit(back).filter { it.second == oy + 6 }.minOf { it.first })
    }

    @Test fun edgeZeroDrawsNothingExtra() {
        val high = sprites["fire_high"]
        for (edge in listOf(0f, -1f, Float.NaN)) {
            assertTrue("edge $edge", lit(edgeOnly(high, 0, 0L, edge, 0f)).isEmpty())
        }
        // a frame with no flame has nothing to grow an edge from
        assertTrue(lit(edgeOnly(sprites["fire_cold"], 0, 0L, 1f, 0f)).isEmpty())
        // and a positive edge does draw on a flame
        assertTrue(lit(edgeOnly(high, 0, 0L, 1f, 0f)).isNotEmpty())
    }

    @Test fun tongueLengthNeverAboveMax() {
        assertEquals(1, FireEdge.maxTongue(0f))
        assertEquals(3, FireEdge.maxTongue(0.5f))
        assertEquals(5, FireEdge.maxTongue(1f))
        assertEquals(5, FireEdge.maxTongue(7f))
        for (name in listOf("fire_low", "fire_mid", "fire_high")) {
            val sprite = sprites[name]
            for (frame in sprite.frames.indices) {
                val first = FlameShape.of(sprite, frame).firstRow
                for (edge in listOf(0.25f, 0.5f, 1f)) {
                    for (t in 0L..2_000L step 130L) {
                        for ((_, y) in lit(edgeOnly(sprite, frame, t, edge, 0f))) {
                            // a tongue rises at most maxLen rows above the first row of the flame
                            assertTrue("$name f$frame edge $edge t $t: row ${y - oy} above ${first - FireEdge.maxTongue(edge)}",
                                y - oy >= first - FireEdge.maxTongue(edge))
                        }
                    }
                }
            }
        }
    }

    @Test fun sameInputsSamePixels() {
        val sprite = sprites["fire_mid"]
        for (t in listOf(0L, 777L, 12_345L)) {
            assertArrayEquals(edgeOnly(sprite, 2, t, 0.8f, 0.5f).pixels, edgeOnly(sprite, 2, t, 0.8f, 0.5f).pixels)
        }
    }

    @Test fun timeChangesTongues() {
        val sprite = sprites["fire_high"]
        val a = edgeOnly(sprite, 0, 0L, 1f, 0f)
        val b = edgeOnly(sprite, 0, 520L, 1f, 0f)
        assertFalse("the edge is frozen", a.pixels.contentEquals(b.pixels))
        assertTrue(lit(a).isNotEmpty() && lit(b).isNotEmpty())
    }

    @Test fun edgeLeansWithTheSprite() {
        val sprite = sprites["fire_high"]
        val calm = lit(edgeOnly(sprite, 0, 0L, 1f, 0f))
        val windy = lit(edgeOnly(sprite, 0, 0L, 1f, 1f))
        assertTrue(calm.isNotEmpty() && windy.isNotEmpty())
        // the tongues sit above the tip, where the lean is strongest: the mean column moves right
        assertTrue(windy.sumOf { it.first }.toDouble() / windy.size > calm.sumOf { it.first }.toDouble() / calm.size)
    }

    @Test fun noiseInRangeAndSeeded() {
        var lo = 1f
        var hi = 0f
        for (a in 0 until 100) for (b in 0 until 100) {
            val v = FireNoise.hash01(a, b, FireTuning.SEED_TONGUE)
            assertTrue(v in 0f..1f)
            if (v < lo) lo = v
            if (v > hi) hi = v
            assertEquals(v, FireNoise.hash01(a, b, FireTuning.SEED_TONGUE), 0f)
        }
        assertTrue("spread over the range", lo < 0.05f && hi > 0.95f)
        // different seeds are different noise
        var same = 0
        for (a in 0 until 50) if (FireNoise.hash01(a, 3, FireTuning.SEED_TONGUE) == FireNoise.hash01(a, 3, FireTuning.SEED_LICK_LEFT)) same++
        assertTrue(same < 5)
        // the smooth noise: on a cell boundary it is the hash of that cell, between two cells it stays between them
        for (x in 0 until 40) {
            assertEquals(FireNoise.hash01(x, 2, 11), FireNoise.value(x, 520L, 260, 11), 0f)
            val a = FireNoise.hash01(x, 2, 11)
            val b = FireNoise.hash01(x, 3, 11)
            val mid = FireNoise.value(x, 650L, 260, 11)
            assertTrue(mid >= minOf(a, b) - 1e-6f && mid <= maxOf(a, b) + 1e-6f)
            assertEquals(a + (b - a) * 0.5f, mid, 1e-5f)
        }
        for (t in 0L..5_000L step 37L) assertTrue(FireNoise.value(7, t, 200, 23) in 0f..1f)
    }

    @Test fun coreColoursMatchFirePxl() {
        val palette = sprites["fire_mid"].palette
        fun colour(letter: Char): Int = palette.first { it.char == letter }.argb
        assertEquals(colour('g'), FireEdge.COL_G)
        assertEquals(colour('r'), FireEdge.COL_R)
        assertEquals(colour('f'), FireEdge.COL_F)
        assertEquals(colour('m'), FireEdge.COL_M)
    }
}
