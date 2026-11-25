package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract of SceneRenderer.render for whoever calls it later (a widget, a preview). */
class SceneRendererTest {
    private val sprites: SpriteSource by lazy { TestSprites.all() }

    private fun frame(
        renderer: SceneRenderer, state: HearthState, t: Long, quality: SceneQuality = SceneQuality.FULL,
        stoke: Float = 0f, w: Int = 155, h: Int = 105, progress: Float? = null,
        palette: ScenePalette = TestPalettes.DARK,
    ): IntArray {
        val stage = PixelStage(w, h)
        renderer.render(stage, SceneState(state, palette, progress, null, stoke), t, quality)
        return stage.pixels
    }

    @Test fun sameInputsGiveSameFrame() {
        val r = SceneRenderer(sprites)
        val a = frame(r, HearthState.BLAZING, 3000L)
        val b = frame(r, HearthState.BLAZING, 3000L)
        assertArrayEquals(a, b)
        // and a second renderer, built later, draws the same
        assertArrayEquals(a, frame(SceneRenderer(sprites), HearthState.BLAZING, 3000L))
    }

    @Test fun timeChangesTheFrameWhenBlazing() {
        val r = SceneRenderer(sprites)
        val a = frame(r, HearthState.BLAZING, 1000L)
        val b = frame(r, HearthState.BLAZING, 3000L)
        assertFalse("the scene is frozen", a.contentEquals(b))
    }

    @Test fun plainDrawsNoParticles() {
        val r = SceneRenderer(sprites)
        val plain = frame(r, HearthState.BLAZING, 3000L, SceneQuality.PLAIN)
        val full = frame(r, HearthState.BLAZING, 3000L, SceneQuality.FULL)
        assertFalse("PLAIN looks like FULL", plain.contentEquals(full))
        assertArrayEquals(plain, frame(SceneRenderer(sprites), HearthState.BLAZING, 3000L, SceneQuality.PLAIN))
        // PLAIN has a still picture apart from the stars, the fire and Pet: nothing from the particle layers,
        // so it is far from blank
        assertTrue(plain.count { it != 0 } > plain.size / 2)
    }

    @Test fun everyStateDrawsAndHasAnOpaqueBackground() {
        val r = SceneRenderer(sprites)
        for (s in HearthState.entries) {
            val px = frame(r, s, 2222L, progress = 0.5f)
            assertTrue("$s left holes in the picture", px.all { (it ushr 24) != 0 })
        }
    }

    @Test fun differentStatesDrawDifferentFires() {
        val r = SceneRenderer(sprites)
        val cold = frame(r, HearthState.COLD, 0L, SceneQuality.PLAIN)
        val blazing = frame(r, HearthState.BLAZING, 0L, SceneQuality.PLAIN)
        assertFalse(cold.contentEquals(blazing))
    }

    @Test fun lightAndDarkPalettesDiffer() {
        val r = SceneRenderer(sprites)
        val dark = frame(r, HearthState.COLD, 0L, SceneQuality.PLAIN, palette = TestPalettes.DARK)
        val light = frame(r, HearthState.COLD, 0L, SceneQuality.PLAIN, palette = TestPalettes.LIGHT)
        assertFalse(dark.contentEquals(light))
        // a palette that changes back gives the first picture again (the caches follow the palette)
        assertArrayEquals(dark, frame(r, HearthState.COLD, 0L, SceneQuality.PLAIN, palette = TestPalettes.DARK))
    }

    @Test fun tinyStageDrawsNothing() {
        val r = SceneRenderer(sprites)
        val narrow = PixelStage(7, 100)
        r.render(narrow, SceneState(HearthState.BLAZING, TestPalettes.DARK), 1000L)
        assertEquals(0, narrow.pixels.count { it != 0 })
        val low = PixelStage(100, SceneRenderer.GROUND_H + 9)
        r.render(low, SceneState(HearthState.BLAZING, TestPalettes.DARK), 1000L)
        assertEquals(0, low.pixels.count { it != 0 })
        // the smallest stage that draws
        val smallest = PixelStage(8, SceneRenderer.GROUND_H + 10)
        r.render(smallest, SceneState(HearthState.COLD, TestPalettes.DARK), 0L)
        assertTrue(smallest.pixels.any { it != 0 })
    }

    @Test fun everyRequiredSpriteExists() {
        assertEquals(10 + MascotMood.entries.size, SceneRenderer.REQUIRED_SPRITES.size)
        assertEquals(SceneRenderer.REQUIRED_SPRITES.size, SceneRenderer.REQUIRED_SPRITES.toSet().size)
        for (name in SceneRenderer.REQUIRED_SPRITES) assertNotNull(name, sprites[name])
        assertEquals("scene_far", SceneRenderer.REQUIRED_SPRITES.first())
        assertEquals("fire_high", SceneRenderer.REQUIRED_SPRITES[9])
    }

    @Test fun aMissingSpriteFailsAtConstruction() {
        val partial = SpriteSource { name -> if (name == "scene_far") throw IllegalArgumentException("Unknown sprite '$name'") else sprites[name] }
        assertThrows(IllegalArgumentException::class.java) { SceneRenderer(partial) }
    }

    @Test fun stokeBoostIsCoerced() {
        val r = SceneRenderer(sprites)
        val high = frame(r, HearthState.BLAZING, 3000L, stoke = 2.5f)
        val max = frame(r, HearthState.BLAZING, 3000L, stoke = 1.5f)
        assertArrayEquals(max, high)
        val none = frame(r, HearthState.BLAZING, 3000L, stoke = 0f)
        assertFalse(none.contentEquals(max))
    }
}
