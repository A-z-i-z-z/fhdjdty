package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeatherParticlesTest {
    private val seed = 20261125L

    @Test fun dropSetDeterministic() {
        val a = DropSet(96, 7L)
        val b = DropSet(96, 7L)
        assertArrayEquals(a.xFrac, b.xFrac, 0f)
        assertArrayEquals(a.speedFrac, b.speedFrac, 0f)
        assertArrayEquals(a.phase, b.phase, 0f)
        assertArrayEquals(a.swayPx, b.swayPx, 0f)
        assertArrayEquals(a.lengthPx, b.lengthPx)
        assertTrue(a.big.contentEquals(b.big))
        val c = DropSet(96, 8L)
        assertFalse(a.xFrac.contentEquals(c.xFrac))
        assertFalse(a.phase.contentEquals(c.phase))
        // the first drops do not depend on the capacity
        val small = DropSet(10, 7L)
        for (i in 0 until 10) assertEquals(a.xFrac[i], small.xFrac[i], 0f)
    }

    @Test fun dropSetRanges() {
        val d = DropSet(4096, seed)
        assertEquals(4096, d.capacity)
        var big = 0
        for (i in 0 until d.capacity) {
            assertTrue(d.xFrac[i] >= 0f && d.xFrac[i] < 1f)
            assertTrue(d.speedFrac[i] >= 0f && d.speedFrac[i] < 1f)
            assertTrue(d.phase[i] >= 0f && d.phase[i] < 1f)
            assertTrue(d.lengthPx[i] == 2 || d.lengthPx[i] == 3)
            assertTrue(d.swayPx[i] >= 1f && d.swayPx[i] <= 3f)
            if (d.big[i]) big++
        }
        // about 15 per cent are big flakes
        assertTrue("big: $big", big in 480..750)
        assertEquals(96, DropSet(96, seed).capacity)
        assertEquals(4096, DropSet(100_000, seed).capacity)
        val none = DropSet(-5, seed)
        assertEquals(0, none.capacity)
        assertEquals(0, none.scaledCount(10, 155))
    }

    @Test fun scaledCountFollowsWidth() {
        val d = DropSet(96, seed)
        assertEquals(18, d.scaledCount(18, 155))
        assertEquals(36, d.scaledCount(18, 310))
        assertEquals(9, d.scaledCount(18, 78)) // 9.03
        assertEquals(1, d.scaledCount(18, 8)) // 0.93
        assertEquals(96, d.scaledCount(90, 310)) // capped at the capacity
        assertEquals(0, d.scaledCount(0, 155))
        assertEquals(0, d.scaledCount(-4, 155))
        assertEquals(0, d.scaledCount(18, 0))
        assertEquals(44, d.scaledCount(44, 155))
        assertEquals(34, d.scaledCount(34, 155))
    }

    @Test fun positionsInsideWrapped() {
        val r = Random(99)
        val d = DropSet(96, seed)
        for (width in intArrayOf(1, 8, 120, 155, 176, 300)) {
            for (span in intArrayOf(1, 20, 90, 150)) {
                for (n in 0 until 400) {
                    val t = r.nextLong(-5_000_000L, 50_000_000L)
                    val i = r.nextInt(96)
                    val drift = r.nextFloat() * 4f - 2f
                    val y = d.yAt(i, t, span, 70f, 110f, 1.3f)
                    assertTrue("y $y span $span", y in 0 until span)
                    val x = d.xAt(i, width, y, drift)
                    assertTrue("x $x width $width", x in 0 until width)
                    val sx = d.xAt(i, width, y, drift, t, 1.3f)
                    assertTrue("sway x $sx width $width", sx in 0 until width)
                }
            }
        }
        // 10 000 random times on the Home stage
        for (n in 0 until 10_000) {
            val t = r.nextLong(0L, 1_000_000_000L)
            val i = r.nextInt(96)
            val y = d.yAt(i, t, 150, 14f, 26f)
            val x = d.xAt(i, 155, y, -0.35f * 0.6f, t, 1.3f)
            assertTrue(y in 0 until 150)
            assertTrue(x in 0 until 155)
        }
        // nothing to do on an empty stage
        assertEquals(0, d.yAt(0, 123L, 0, 70f, 110f))
        assertEquals(0, d.yAt(0, 123L, -4, 70f, 110f))
        assertEquals(0, d.xAt(0, 0, 3, 0.5f))
    }

    @Test fun dropsFallAtTheirSpeed() {
        val d = DropSet(96, seed)
        for (i in 0 until 96) {
            val speed = d.speedOf(i, 70f, 110f)
            assertTrue(speed >= 70f && speed <= 110f)
            val y0 = d.yAt(i, 0L, 400, 70f, 110f)
            val y1 = d.yAt(i, 1000L, 400, 70f, 110f)
            val moved = Math.floorMod(y1 - y0, 400)
            val whole = speed.toInt()
            assertTrue("drop $i moved $moved at $speed", moved == whole || moved == whole + 1)
            // heavier rain is faster, far rain slower
            val fast = Math.floorMod(d.yAt(i, 1000L, 400, 70f, 110f, 1.3f) - y0, 400)
            val slow = Math.floorMod(d.yAt(i, 1000L, 400, 70f, 110f, 0.7f) - y0, 400)
            assertTrue(slow <= moved && moved <= fast)
        }
        // a drop starts at its phase
        for (i in 0 until 96) {
            val y = d.yAt(i, 0L, 100, 70f, 110f)
            val want = (d.phase[i] * 100f).toInt()
            assertTrue("drop $i: $y against $want", Math.abs(y - want) <= 1)
        }
        // time stands still when the time is the same
        assertEquals(d.yAt(5, 4242L, 150, 14f, 26f), d.yAt(5, 4242L, 150, 14f, 26f))
    }

    @Test fun slantAndSway() {
        val d = DropSet(96, seed)
        for (i in 0 until 96) {
            val base = d.xAt(i, 1000, 0, 0.5f)
            val slanted = d.xAt(i, 1000, 10, 0.5f)
            // the slant is 0.5 column per row: 10 rows move it 5 columns (floor of a sum may differ by one)
            val dx = slanted - base
            assertTrue("drop $i slanted by $dx", dx == 5 || dx == 4 || dx == 6 || dx == 5 - 1000 || dx == 5 + 1000)
            // wind to the left moves it the other way
            val left = d.xAt(i, 1000, 10, -0.5f)
            val dl = left - base
            assertTrue("drop $i left by $dl", dl == -5 || dl == -4 || dl == -6 || dl == 1000 - 5 || dl == 1000 - 6 || dl == 1000 - 4)
            // sway stays within three columns of the unswayed column, whatever the time
            for (t in longArrayOf(0L, 1234L, 5_000L, 77_777L)) {
                val swayed = d.xAt(i, 1000, 0, 0f, t, 1.3f)
                var diff = Math.floorMod(swayed - d.xAt(i, 1000, 0, 0f), 1000)
                if (diff > 500) diff -= 1000
                assertTrue("drop $i sway $diff", diff in -4..4)
            }
        }
        // no sway rate means no sway
        assertEquals(d.xAt(3, 155, 20, 0.2f), d.xAt(3, 155, 20, 0.2f, 99_999L, 0f))
    }

    // reference values of the hash, recomputed with a script (the splitmix64 finaliser, seed 20261125)
    @Test fun lightningHashMatchesTheReferenceImplementation() {
        val flags = (0L until 12L).map { if (WeatherLightning.flashes(it, seed)) 1 else 0 }
        assertEquals(listOf(0, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1), flags)
        val negative = (-6L until 0L).map { if (WeatherLightning.flashes(it, seed)) 1 else 0 }
        assertEquals(listOf(1, 1, 0, 1, 1, 1), negative)
        assertEquals(5079, (0L until 10_000L).count { WeatherLightning.flashes(it, seed) })
        val slots = listOf(1L, 3L, 7L, 8L, 11L, 12L)
        val refs = listOf(0.79498f, 0.30015f, 0.36854f, 0.63314f, 0.69159f, 0.60731f)
        for ((k, s) in slots.withIndex()) assertEquals("slot $s", refs[k], WeatherLightning.fraction(s, seed), 2e-6f)
    }

    @Test fun lightningNeverCloserThan20s() {
        val slotsToScan = 1500L
        val rising = ArrayList<Long>()
        var prev = 0
        var t = 0L
        val end = slotsToScan * WeatherLightning.SLOT_MS
        while (t < end) {
            val p = lightningPhase(t, seed)
            if (p == 1 && prev != 1) rising.add(t)
            prev = p
            t += 10L
        }
        val flashing = (0L until slotsToScan).count { WeatherLightning.flashes(it, seed) }
        assertEquals(flashing, rising.size)
        for (i in 1 until rising.size) assertTrue("${rising[i]} - ${rising[i - 1]}", rising[i] - rising[i - 1] >= 20_000L)
        // every flash begins four seconds into its slot
        for (r in rising) assertEquals(4_000L, r % 20_000L)
    }

    @Test fun lightningFractionOfFlashingSlots() {
        val n = 10_000L
        val count = (0L until n).count { WeatherLightning.flashes(it, seed) }
        val share = count / n.toDouble()
        assertTrue("share $share", share in 0.4..0.6)
        for (s in longArrayOf(1L, 2L, 99L, -7L)) {
            val c = (0L until n).count { WeatherLightning.flashes(it, s) }
            assertTrue("seed $s: ${c / n.toDouble()}", c / n.toDouble() in 0.4..0.6)
        }
        // other seeds give other patterns
        val differing = (0L until n).count { WeatherLightning.flashes(it, 1L) != WeatherLightning.flashes(it, 2L) }
        assertTrue("differing $differing", differing > 3_000)
    }

    @Test fun lightningFramesAreAsSpecified() {
        val slot = (0L until 100L).first { WeatherLightning.flashes(it, seed) }
        val base = slot * 20_000L + 4_000L
        assertEquals(0, lightningPhase(base - 1, seed))
        assertEquals(1, lightningPhase(base, seed))
        assertEquals(1, lightningPhase(base + 79, seed))
        assertEquals(0, lightningPhase(base + 80, seed))
        assertEquals(0, lightningPhase(base + 100, seed))
        assertEquals(0, lightningPhase(base + 149, seed))
        assertEquals(2, lightningPhase(base + 150, seed))
        assertEquals(2, lightningPhase(base + 200, seed))
        assertEquals(2, lightningPhase(base + 249, seed))
        assertEquals(0, lightningPhase(base + 250, seed))
        assertEquals(0, lightningPhase(base + 10_000, seed))
        // a slot without a flash is dark all through
        val dark = (0L until 100L).first { !WeatherLightning.flashes(it, seed) }
        for (ms in 0L until 20_000L step 50L) assertEquals(0, lightningPhase(dark * 20_000L + ms, seed))
        // the rest of the flashing slot outside the frames
        for (ms in 0L until 20_000L step 50L) {
            val p = lightningPhase(slot * 20_000L + ms, seed)
            val expected = when {
                ms in 4_000L until 4_080L -> 1
                ms in 4_150L until 4_250L -> 2
                else -> 0
            }
            if (ms % 50L == 0L && (ms < 4_000L || ms >= 4_250L)) assertEquals(0, p)
            if (ms == 4_000L) assertEquals(1, p)
            if (ms in 4_150L until 4_250L) assertEquals(expected, p)
        }
    }

    @Test fun lightningWorksBeforeTheEpoch() {
        for (t in longArrayOf(-1L, -20_000L, -123_456_789L, Long.MIN_VALUE / 4)) {
            val p = lightningPhase(t, seed)
            assertTrue("$t: $p", p in 0..2)
        }
        // slot -1 flashes for this seed (reference): its flash is 4000 ms into the slot, at -16000
        assertEquals(1, lightningPhase(-16_000L, seed))
        assertEquals(2, lightningPhase(-16_000L + 150L, seed))
        assertEquals(0, lightningPhase(-16_001L, seed))
    }

    @Test fun lightningPositionStaysOnTheStage() {
        var sum = 0.0
        var n = 0
        for (s in 0L until 5_000L) {
            if (!WeatherLightning.flashes(s, seed)) continue
            val f = WeatherLightning.fraction(s, seed)
            assertTrue("slot $s: $f", f >= 0.15f && f <= 0.85f)
            sum += f
            n++
        }
        assertTrue(n > 2_000)
        assertEquals(0.5, sum / n, 0.03)
    }
}
