package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class FireSparksTest {
    private val high = 0xFFFFC857.toInt()
    private val mid = 0xFFF7691A.toInt()
    private val low = 0xFFD9381E.toInt()

    private fun newSet() = SparkSet(FireTuning.SPARK_CAPACITY, FireTuning.SPARK_SEED)

    private fun draw(
        set: SparkSet, count: Int, t: Long, wind: Float = 0f, strength: Float = 1f,
        originX: Float = 100f, originY: Float = 70f, half: Float = 6f, rise: Float = 30f,
        w: Int = 200, h: Int = 100,
    ): PixelStage {
        val stage = PixelStage(w, h)
        stage.drawSparks(set, count, t, originX, originY, half, rise, wind, high, mid, low, strength)
        return stage
    }

    private fun lit(stage: PixelStage): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until stage.height) for (x in 0 until stage.width) if (stage.pixels[y * stage.width + x] != 0) out.add(x to y)
        return out
    }

    /** A time at which spark [i] has the age [u] (within 0.02) and is not in its flicker-off moment. */
    private fun timeAtAge(set: SparkSet, i: Int, u: Float): Long {
        var x = u - set.phase[i]
        while (x < 1f) x += 1f
        var t = (x * set.periodMs[i]).toLong()
        while (((t / 80L + i * 7L) % 11L).toInt() == 0) t += 1L
        val actual = ((t.toFloat() / set.periodMs[i]) + set.phase[i]) % 1f
        assertEquals("age of spark $i", u, actual, 0.02f)
        return t
    }

    @Test fun poolHasTheContractedRanges() {
        val set = newSet()
        assertEquals(32, set.capacity)
        for (i in 0 until set.capacity) {
            assertTrue(set.spawnX[i] >= -1f && set.spawnX[i] <= 1f)
            assertTrue(set.periodMs[i] >= 1500f && set.periodMs[i] <= 3800f)
            assertTrue(set.phase[i] >= 0f && set.phase[i] < 1f)
            assertTrue(set.swayAmp[i] >= 0.8f && set.swayAmp[i] <= 2.6f)
            assertTrue(set.swayFreq[i] >= 0.9f && set.swayFreq[i] <= 2.1f)
            assertTrue(set.windGain[i] >= 0.5f && set.windGain[i] <= 1.3f)
        }
        assertTrue("some big sparks", set.big.any { it })
        assertTrue("some small sparks", set.big.any { !it })
        // seeded: a second pool is the same pool, another seed is another pool
        val again = newSet()
        assertArrayEquals(set.spawnX, again.spawnX, 0f)
        assertArrayEquals(set.windGain, again.windGain, 0f)
        assertFalse(set.spawnX.contentEquals(SparkSet(32, 32L).spawnX))
    }

    @Test fun countClampsToCapacity() {
        val set = newSet()
        for (t in listOf(0L, 700L, 1_900L, 4_321L)) {
            val full = draw(set, 32, t)
            assertArrayEquals(full.pixels, draw(set, 100, t).pixels)
            assertArrayEquals(full.pixels, draw(set, Int.MAX_VALUE, t).pixels)
        }
        assertTrue(lit(draw(set, 0, 1_000L)).isEmpty())
        assertTrue(lit(draw(set, -5, 1_000L)).isEmpty())
        assertTrue("more sparks light more pixels", lit(draw(set, 32, 2_000L)).size > lit(draw(set, 4, 2_000L)).size)
    }

    @Test fun positionsArePureInTimeAndWind() {
        val set = newSet()
        val first = draw(set, 32, 1_234L, wind = 0.4f)
        draw(set, 32, 9_999L, wind = -0.7f)          // another frame in between changes nothing
        assertArrayEquals(first.pixels, draw(set, 32, 1_234L, wind = 0.4f).pixels)
        assertArrayEquals(first.pixels, draw(newSet(), 32, 1_234L, wind = 0.4f).pixels)
        assertFalse(first.pixels.contentEquals(draw(set, 32, 1_234L, wind = -0.4f).pixels))
        assertFalse(first.pixels.contentEquals(draw(set, 32, 2_500L, wind = 0.4f).pixels))
    }

    @Test fun windMovesSparksByDriftFormula() {
        // pools of one spark (the first spark of a pool does not depend on its capacity): one pixel at u near 0.8
        for (seed in 1L..8L) {
            val set = SparkSet(1, seed)
            val t = timeAtAge(set, 0, 0.8f)
            val u = ((t.toFloat() / set.periodMs[0]) + set.phase[0]) % 1f
            val calm = lit(draw(set, 1, t, wind = 0f))
            val windy = lit(draw(set, 1, t, wind = 1f))
            assertEquals("seed $seed calm", 1, calm.size)
            assertEquals("seed $seed windy", 1, windy.size)
            assertEquals("the wind does not change the height", calm[0].second, windy[0].second)
            val drift = FireWind.sparkDrift(1f, u, set.windGain[0])
            val lo = floor(drift).toInt()
            val moved = windy[0].first - calm[0].first
            assertTrue("seed $seed: moved $moved cells, drift $drift", moved == lo || moved == lo + 1)
            // and to the left for the opposite wind
            val back = lit(draw(set, 1, t, wind = -1f))
            assertEquals(1, back.size)
            val movedBack = calm[0].first - back[0].first
            assertTrue("seed $seed: moved back $movedBack cells, drift $drift", movedBack == lo || movedBack == lo + 1)
        }
    }

    @Test fun windZeroEqualsNoWindTerm() {
        val set = newSet()
        for (t in listOf(0L, 333L, 1_500L, 6_000L)) {
            assertArrayEquals(draw(set, 32, t, wind = 0f).pixels, draw(set, 32, t, wind = -0f).pixels)
        }
    }

    @Test fun zeroStrengthDrawsNothing() {
        val set = newSet()
        for (t in listOf(0L, 500L, 1_700L)) {
            assertTrue(lit(draw(set, 32, t, strength = 0f)).isEmpty())
            assertTrue(lit(draw(set, 32, t, strength = 0.02f)).isEmpty())
        }
        assertTrue(lit(draw(set, 32, 500L, strength = 1f)).isNotEmpty())
    }

    @Test fun coloursCoolWithAge() {
        val set = newSet()
        val cases = listOf(0.2f to high, 0.5f to mid, 0.85f to low)
        for ((u, base) in cases) {
            val t = timeAtAge(set, 0, u)
            val stage = draw(set, 1, t)
            val pixels = lit(stage)
            assertTrue("spark 0 at age $u is visible", pixels.isNotEmpty())
            for ((x, y) in pixels) {
                assertEquals("age $u", base and 0xFFFFFF, stage.pixels[y * stage.width + x] and 0xFFFFFF)
            }
        }
    }

    @Test fun fadesInAndOut() {
        val set = newSet()
        // just born: transparent enough to be skipped or faint; mid-life: full strength; the end: faint
        val young = draw(set, 1, timeAtAge(set, 0, 0.02f))
        val adult = draw(set, 1, timeAtAge(set, 0, 0.5f))
        val old = draw(set, 1, timeAtAge(set, 0, 0.9f))
        fun alphaOf(s: PixelStage): Int = lit(s).maxOfOrNull { (x, y) -> s.pixels[y * s.width + x] ushr 24 } ?: 0
        assertEquals(255, alphaOf(adult))
        assertTrue(alphaOf(young) < 255)
        assertTrue(alphaOf(old) in 1..254)
    }

    @Test fun noSparkOutsideRiseBox() {
        val set = newSet()
        val originX = 100f
        val originY = 70f
        val half = 6f
        val rise = 30f
        // widest reach: spawn + sway (at most 2.6 * 1.3) + drift (at most 12 * 1.3), plus the second cell of a big spark
        val reach = half + 2.6f * 1.3f + 12f * 1.3f + 2f
        var any = 0
        for (wind in listOf(-1f, 0f, 1f)) {
            var t = 0L
            while (t < 20_000L) {
                val stage = draw(set, 32, t, wind = wind, originX = originX, originY = originY, half = half, rise = rise, h = 120)
                for ((x, y) in lit(stage)) {
                    any++
                    assertTrue("x $x at t=$t wind=$wind", x >= originX - reach && x <= originX + reach)
                    assertTrue("y $y at t=$t", y >= originY - rise - 1f && y <= originY + 1f)
                }
                t += 137L
            }
        }
        assertTrue(any > 1_000)
    }

    @Test fun sparkPlanFollowsTheContractNumbers() {
        assertEquals(19, FireSparkPlan.count(12, 0.5f, false))
        assertEquals(4, FireSparkPlan.count(4, 0f, false))
        assertEquals(32, FireSparkPlan.count(20, 1.5f, true))
        assertEquals(18, FireSparkPlan.count(4, 0f, true))
        assertEquals(10, FireSparkPlan.count(50, 0f, false, capacity = 10))
        assertEquals(0, FireSparkPlan.count(-9, 0f, false))
        assertEquals(131f, FireSparkPlan.originY(157, 1f), 0f)
        assertEquals(138f, FireSparkPlan.originY(157, 0.5f), 0f)
        assertEquals(145f, FireSparkPlan.originY(157, 0f), 0f)
        assertEquals(10f, FireSparkPlan.halfWidth(1f, 1f), 1e-6f)
        assertEquals(4f, FireSparkPlan.halfWidth(0f, 0f), 0f)
        assertEquals(16f, FireSparkPlan.rise(0f, 0f), 0f)
        assertEquals(50f, FireSparkPlan.rise(1f, 1f), 1e-5f)
        assertEquals(0.865f, FireSparkPlan.strength(0.7f, 0f), 1e-5f)
        assertEquals(1f, FireSparkPlan.strength(1f, 1.5f), 0f)
    }
}
