package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The picture did not change: the new path (StageGrid, SceneRenderer, PixelStage.scaleInto) must give the
 * exact pixels of the old drawing code, which LegacyHearthReference keeps as a type-only translation.
 * Both sides use Argb.over, so a bug in the blend would hide here; ArgbTest covers it, and the phone check
 * with animations off is the second witness (see docs/waves/w0/STAGE.md).
 */
class SceneRendererParityTest {
    private class Case(
        val name: String,
        val state: HearthState,
        val t: Long,
        val progress: Float? = null,
        val mood: MascotMood? = null,
        val stoke: Float = 0f,
        val celebration: Boolean = false,
        val particles: Boolean = true,
    )

    private val cases = listOf(
        Case("cold t=0", HearthState.COLD, 0L),
        Case("cold t=4321", HearthState.COLD, 4321L),
        Case("kindling 0.1", HearthState.KINDLING, 2500L, progress = 0.1f),
        Case("kindling 0.4", HearthState.KINDLING, 2500L, progress = 0.4f),
        Case("kindling 0.9", HearthState.KINDLING, 2500L, progress = 0.9f),
        Case("blazing t=1234", HearthState.BLAZING, 1234L),
        Case("blazing t=7700 (hop)", HearthState.BLAZING, 7700L),
        Case("blazing t=14300 (shooting star)", HearthState.BLAZING, 14300L),
        Case("blazing stoke 0.8", HearthState.BLAZING, 3000L, stoke = 0.8f),
        Case("blazing celebration", HearthState.BLAZING, 5000L, celebration = true),
        Case("smoke t=9876", HearthState.SMOKE, 9876L),
        Case("blazing waving", HearthState.BLAZING, 4000L, mood = MascotMood.WAVING),
        Case("cold stoke 1.2 (embers while cold)", HearthState.COLD, 3300L, stoke = 1.2f),
        Case("blazing plain", HearthState.BLAZING, 3000L, particles = false),
    )

    private val sizes = listOf(1080 to 735, 720 to 560, 1440 to 1000)

    /** The new path at device resolution. */
    private fun renderNew(
        renderer: SceneRenderer, w: Int, h: Int, c: Case, palette: ScenePalette,
    ): IntArray {
        val grid = StageGrid.forScene(w.toFloat(), h.toFloat())
        assertNotNull("no grid for ${w}x$h", grid)
        val stage = PixelStage(grid!!.width, grid.height)
        val state = SceneState(c.state, palette, c.progress, c.mood, c.stoke, c.celebration)
        renderer.render(stage, state, c.t, if (c.particles) SceneQuality.FULL else SceneQuality.PLAIN)
        val out = IntArray(w * h)
        stage.scaleInto(out, w, h, grid)
        return out
    }

    @Test fun parityAcrossStatesAndTimes() {
        val sprites = TestSprites.all()
        val renderer = SceneRenderer(sprites)
        for ((w, h) in sizes) {
            for ((paletteName, palette) in listOf("dark" to TestPalettes.DARK, "light" to TestPalettes.LIGHT)) {
                for (c in cases) {
                    val expected = LegacyHearthReference.render(
                        sprites, w, h, c.state, c.progress, c.mood, c.stoke, c.celebration, c.t, c.particles, palette,
                    )
                    val actual = renderNew(renderer, w, h, c, palette)
                    val diff = ParityDiff.firstDifference(expected, actual, w)
                    if (diff != null) fail("${w}x$h, $paletteName palette, case '${c.name}': $diff")
                }
            }
        }
    }

    @Test fun theReferenceDrawsSomething() {
        // guards the oracle itself: an empty reference would make the parity test pass for any empty renderer
        val sprites = TestSprites.all()
        val ref = LegacyHearthReference.render(
            sprites, 1080, 735, HearthState.BLAZING, null, null, 0f, false, 3000L, true, TestPalettes.DARK,
        )
        assertTrue("reference picture is blank", ref.count { it != 0 } > ref.size / 2)
        assertTrue("reference has no opaque pixels", ref.any { (it ushr 24) == 255 })
    }

    @Test fun parityMessageNamesFirstDifference() {
        val a = IntArray(12) { 0xFF000000.toInt() or it }
        assertNull(ParityDiff.firstDifference(a, a.copyOf(), 4))
        val b = a.copyOf()
        b[6] = 0xFF123456.toInt()          // x = 2, y = 1 in a picture 4 wide
        val msg = ParityDiff.firstDifference(a, b, 4)
        assertNotNull(msg)
        assertTrue(msg!!, msg.contains("x=2"))
        assertTrue(msg, msg.contains("y=1"))
        assertTrue(msg, msg.contains("ff000006"))
        assertTrue(msg, msg.contains("ff123456"))
        assertTrue(msg, msg.contains("1 of 12"))
        // a different length is reported as such
        assertTrue(ParityDiff.firstDifference(a, IntArray(8), 4)!!.contains("length"))
        assertNull(ParityDiff.firstDifference(IntArray(0), IntArray(0), 4))
    }
}

/** Formats where two pictures first differ. */
internal object ParityDiff {
    fun firstDifference(expected: IntArray, actual: IntArray, width: Int): String? {
        if (expected.size != actual.size) return "length ${expected.size} against ${actual.size}"
        var first = -1
        var count = 0
        for (i in expected.indices) {
            if (expected[i] != actual[i]) {
                if (first < 0) first = i
                count++
            }
        }
        if (first < 0) return null
        val x = first % width
        val y = first / width
        return "first difference at x=$x y=$y: expected ${hex(expected[first])}, got ${hex(actual[first])} ($count of ${expected.size} pixels differ)"
    }

    private fun hex(v: Int): String = String.format("%08x", v)
}
