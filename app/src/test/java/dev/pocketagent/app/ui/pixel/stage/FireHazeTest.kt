package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireHazeTest {
    @Test fun agslHasUniforms() {
        val src = FireHazeShader.SOURCE
        for (needle in listOf(
            "uniform shader content", "uniform float2 fire", "uniform float scale", "uniform float time",
            "uniform float strength", "half4 main(float2", "content.eval",
        )) {
            assertTrue("missing '$needle'", src.contains(needle))
        }
        assertFalse("AGSL has no preprocessor", src.contains('#'))
        assertFalse("no Kotlin template left in the text", src.contains('$'))
        // the box of the haze: 14 columns either side, from 55 to 20 rows above the base
        assertTrue(src.contains("14.0 * scale"))
        assertTrue(src.contains("55.0 * scale"))
        assertTrue(src.contains("20.0 * scale"))
    }

    @Test fun curveIsSmoothstep() {
        assertEquals(0f, FireHazeCurve.strength(0.35f), 0f)
        assertEquals(1f, FireHazeCurve.strength(0.9f), 0f)
        assertEquals(0f, FireHazeCurve.strength(0f), 0f)
        assertEquals(1f, FireHazeCurve.strength(1f), 0f)
        assertEquals(0.5f, FireHazeCurve.strength(0.625f), 1e-4f)
        assertEquals(0f, FireHazeCurve.strength(Float.NaN), 0f)
        var last = 0f
        for (i in 0..100) {
            val s = FireHazeCurve.strength(i / 100f)
            assertTrue(s >= last - 1e-6f && s in 0f..1f)
            last = s
        }
    }

    @Test fun hysteresisOnAt05OffAt035() {
        assertFalse(FireHazeCurve.next(false, 0.49f))
        assertTrue(FireHazeCurve.next(false, 0.5f))
        assertTrue(FireHazeCurve.next(false, 1f))
        assertTrue("stays on between the two", FireHazeCurve.next(true, 0.36f))
        assertTrue(FireHazeCurve.next(true, 0.5f))
        assertFalse(FireHazeCurve.next(true, 0.35f))
        assertFalse(FireHazeCurve.next(true, 0f))
        // between the two levels the answer is the old one
        assertFalse(FireHazeCurve.next(false, 0.4f))
        assertTrue(FireHazeCurve.next(true, 0.4f))
        assertFalse(FireHazeCurve.next(true, Float.NaN))
        // a level wobbling around 0.45 does not make it flicker
        var on = false
        var changes = 0
        for (level in listOf(0.52f, 0.46f, 0.44f, 0.48f, 0.43f, 0.49f, 0.36f, 0.45f, 0.34f, 0.47f, 0.51f)) {
            val next = FireHazeCurve.next(on, level)
            if (next != on) changes++
            on = next
        }
        assertEquals(3, changes)
    }

    @Test fun shiftsAreWholeScenePixels() {
        for (row in 0..60) {
            assertEquals(0, FireHazeCurve.shiftCells(row, 1.7f, 0f))
            for (strength in listOf(0.3f, 1f)) {
                val c = FireHazeCurve.shiftCells(row, 0.9f, strength)
                assertTrue(c in -1..1)
            }
        }
        var left = 0
        var right = 0
        for (row in 0..60) {
            val c = FireHazeCurve.shiftCells(row, 0.9f, 1f)
            if (c < 0) left++
            if (c > 0) right++
        }
        assertTrue("the haze moves rows both ways", left > 5 && right > 5)
        // it moves with time
        val a = (0..60).map { FireHazeCurve.shiftCells(it, 0f, 1f) }
        val b = (0..60).map { FireHazeCurve.shiftCells(it, 0.3f, 1f) }
        assertTrue(a != b)
    }
}
