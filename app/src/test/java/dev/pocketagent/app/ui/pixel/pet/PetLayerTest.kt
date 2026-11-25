package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.ui.pixel.PaletteEntry
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteFrame
import dev.pocketagent.app.ui.pixel.stage.PixelStage
import dev.pocketagent.app.ui.pixel.stage.SpriteSource
import dev.pocketagent.app.ui.pixel.stage.TestSprites
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PetLayerTest {
    private val feetX = 50
    private val feetY = 50

    private fun colour(sprite: Int, frame: Int): Int = 0xFF000000.toInt() or (((sprite * 29 + 40) and 0xFF) shl 16) or (((frame * 23 + 20) and 0xFF) shl 8) or 0x40

    /** A sprite whose frames are one solid colour each (so a pixel tells which frame was drawn). */
    private fun solid(name: String, w: Int, h: Int, frames: Int, totalMs: Int, id: Int): Sprite {
        val palette = List(frames) { PaletteEntry('a' + it, colour(id, it)) }
        val each = totalMs / frames
        val list = List(frames) { k ->
            val ms = if (k == frames - 1) totalMs - each * (frames - 1) else each
            SpriteFrame(IntArray(w * h) { k }, ms)
        }
        return Sprite(name, w, h, list, palette)
    }

    /** Frames with a pattern of their own and holes, to compare whole pictures. */
    private fun patterned(name: String, frames: Int, totalMs: Int, id: Int): Sprite {
        val palette = List(4) { PaletteEntry('a' + it, colour(id + it, it + id)) }
        val each = totalMs / frames
        val list = List(frames) { k ->
            val ms = if (k == frames - 1) totalMs - each * (frames - 1) else each
            SpriteFrame(IntArray(32 * 32) { i -> if ((i + k) % 7 == 0) -1 else (i / 32 + i + k + id) % 4 }, ms)
        }
        return Sprite(name, 32, 32, list, palette)
    }

    private fun sourceOf(map: Map<String, Sprite>): SpriteSource =
        SpriteSource { name -> map[name] ?: throw IllegalArgumentException("Unknown sprite '$name'") }

    private fun classicMap(fake: (PetAnim) -> Sprite = { patterned(it.sprite, it.frames, it.durationMs, it.ordinal) }): MutableMap<String, Sprite> {
        val m = LinkedHashMap<String, Sprite>()
        for (a in PetAnim.entries) if (a.isClassic) m[a.sprite] = fake(a)
        return m
    }

    private fun newMap(): MutableMap<String, Sprite> {
        val m = classicMap()
        for (a in PetAnim.entries) if (!a.isClassic) m[a.sprite] = patterned(a.sprite, a.frames, a.durationMs, a.ordinal)
        return m
    }

    private fun drawn(layer: PetLayer, pose: PetPose, t: Long, w: Int = 80, h: Int = 60, fx: Int = feetX, fy: Int = feetY): IntArray {
        val stage = PixelStage(w, h)
        layer.draw(stage, pose, fx, fy, t)
        return stage.pixels
    }

    private fun legacy(sprite: Sprite, t: Long): IntArray {
        val stage = PixelStage(80, 60)
        stage.blit(sprite, sprite.frameAt(t), feetX - 16, feetY - 31)
        return stage.pixels
    }

    @Test fun matchesLegacyBlitWithFakeSprites() {
        val map = classicMap()
        val layer = PetLayer(sourceOf(map))
        for (a in PetAnim.entries) {
            if (!a.isClassic) continue
            val sprite = map.getValue(a.sprite)
            val times = if (a.loops) listOf(0L, 1234L, 7700L) else listOf(0L, sprite.totalMs / 2L)
            for (t in times) {
                val pose = PetPose(a, 0L, -1)
                assertArrayEquals("$a at $t", legacy(sprite, t), drawn(layer, pose, t))
            }
        }
    }

    @Test fun matchesLegacyBlitWithShippedSprites() {
        val src = TestSprites.all()
        val layer = PetLayer(src)
        for (a in PetAnim.entries) {
            if (!a.isClassic) continue
            val sprite = src[a.sprite]
            val times = if (a.loops) listOf(0L, 1234L, 7700L) else listOf(0L, sprite.totalMs / 2L)
            for (t in times) {
                assertArrayEquals("$a at $t", legacy(sprite, t), drawn(layer, PetPose(a, 0L, -1), t))
            }
        }
    }

    @Test fun startMsMovesTheAnimation() {
        val map = classicMap()
        val layer = PetLayer(sourceOf(map))
        val idle = map.getValue("pet_idle")
        assertArrayEquals(legacy(idle, 300L), drawn(layer, PetPose(PetAnim.IDLE, 1000L, -1), 1300L))
    }

    @Test fun shadowUnderPet() {
        val map = classicMap { solid(it.sprite, 32, 32, it.frames, it.durationMs, it.ordinal) }
        map["pet_shadow"] = solid("pet_shadow", 48, 5, 4, 4000, 99)
        val layer = PetLayer(sourceOf(map))
        val pet = colour(PetAnim.IDLE.ordinal, 0)
        val shadow2 = colour(99, 2)
        val px = drawn(layer, PetPose(PetAnim.IDLE, 0L, 2), 0L)
        fun at(x: Int, y: Int) = px[y * 80 + x]
        assertEquals("pet wins over the shadow", pet, at(40, 49))
        assertEquals("shadow beside the pet", shadow2, at(20, 50))
        assertEquals("shadow below the pet", shadow2, at(60, 52))
        assertEquals("shadow starts 34 left of the feet", shadow2, at(16, 50))
        assertEquals(0, at(15, 50))
        assertEquals("shadow is 5 rows high", 0, at(20, 53))
        assertEquals("shadow starts 2 rows above the feet row", 0, at(20, 47))
        assertEquals(shadow2, at(20, 48))
        assertEquals(0, at(20, 10))
        // pet box: x 34..65, y 19..50
        assertEquals(pet, at(34, 19))
        assertEquals(pet, at(65, 50))
        assertEquals(0, at(66, 30))
        // no shadow: the pixels beside the pet stay empty
        assertEquals(0, drawn(layer, PetPose(PetAnim.IDLE, 0L, -1), 0L)[50 * 80 + 20])
        // another level is another frame, beyond the last one is the last one
        assertEquals(colour(99, 0), drawn(layer, PetPose(PetAnim.IDLE, 0L, 0), 0L)[50 * 80 + 20])
        assertEquals(colour(99, 3), drawn(layer, PetPose(PetAnim.IDLE, 0L, 9), 0L)[50 * 80 + 20])
    }

    @Test fun noShadowSpriteNoShadow() {
        val layer = PetLayer(sourceOf(classicMap()))
        val withLevel = drawn(layer, PetPose(PetAnim.IDLE, 0L, 3), 0L)
        val without = drawn(layer, PetPose(PetAnim.IDLE, 0L, -1), 0L)
        assertArrayEquals(without, withLevel)
    }

    @Test fun holdFrame() {
        val map = classicMap { solid(it.sprite, 32, 32, it.frames, it.durationMs, it.ordinal) }
        val layer = PetLayer(sourceOf(map))
        for (t in listOf(0L, 500L, 12_345L)) {
            assertEquals(colour(PetAnim.IDLE.ordinal, 3), drawn(layer, PetPose(PetAnim.IDLE, 0L, -1, holdFrame = 3), t)[40 * 80 + 40])
        }
        assertEquals(colour(PetAnim.IDLE.ordinal, 7), drawn(layer, PetPose(PetAnim.IDLE, 0L, -1, holdFrame = 99), 0L)[40 * 80 + 40])
        assertEquals(colour(PetAnim.IDLE.ordinal, 0), drawn(layer, PetPose(PetAnim.IDLE, 0L, -1, holdFrame = 0), 5000L)[40 * 80 + 40])
    }

    @Test fun onceClampsToLastFrame() {
        val map = classicMap { solid(it.sprite, 32, 32, it.frames, it.durationMs, it.ordinal) }
        val layer = PetLayer(sourceOf(map))
        val happy = PetAnim.HAPPY.ordinal
        fun frameOf(since: Long) = drawn(layer, PetPose(PetAnim.HAPPY, 1000L, -1), 1000L + since)[40 * 80 + 40]
        assertEquals(colour(happy, 0), frameOf(0L))
        assertEquals(colour(happy, 0), frameOf(-500L))
        assertEquals(colour(happy, 8), frameOf(PetAnim.HAPPY.durationMs - 1L))
        assertEquals(colour(happy, 8), frameOf(PetAnim.HAPPY.durationMs.toLong()))
        assertEquals(colour(happy, 8), frameOf(100_000L))
        // a loop keeps going
        val idle = PetAnim.IDLE.ordinal
        val t0 = drawn(layer, PetPose(PetAnim.IDLE, 0L, -1), 0L)[40 * 80 + 40]
        val t1 = drawn(layer, PetPose(PetAnim.IDLE, 0L, -1), PetAnim.IDLE.durationMs.toLong())[40 * 80 + 40]
        assertEquals(colour(idle, 0), t0)
        assertEquals(t0, t1)
    }

    @Test fun availableTellsWhichSpritesWereFound() {
        assertEquals(PetSet.CLASSIC, PetLayer(sourceOf(classicMap())).available)
        assertEquals(PetAnim.entries.toSet(), PetLayer(sourceOf(newMap())).available)
        // a sprite with the wrong number of frames is as good as missing
        val map = newMap()
        map["pet_blink"] = patterned("pet_blink", 3, 290, 1)
        val layer = PetLayer(sourceOf(map))
        assertTrue(PetAnim.BLINK !in layer.available)
        assertTrue(PetAnim.TAIL in layer.available)
    }

    @Test fun missingNewSpriteFallsBack() {
        val map = classicMap()
        map["pet_shadow"] = solid("pet_shadow", 48, 5, 4, 4000, 99)
        val layer = PetLayer(sourceOf(map))
        assertEquals(PetSet.CLASSIC, layer.available)
        for (a in PetAnim.entries) {
            if (a.isClassic) continue
            var target = a
            while (!target.isClassic) target = target.fallback!!
            for (t in listOf(0L, 500L, 5000L)) {
                val got = drawn(layer, PetPose(a, 0L, 1), t)
                val want = drawn(layer, PetPose(target, 0L, 1), t)
                assertArrayEquals("$a falls back to $target at $t", want, got)
            }
        }
        assertEquals(PetAnim.HAPPY, PetAnim.DANCE.fallback)
        assertEquals(PetAnim.THINK, PetAnim.READ.fallback)
        assertEquals(PetAnim.THINK, PetAnim.LISTEN.fallback)
    }

    @Test fun aSourceThatThrowsForNewNamesIsFine() {
        val map = classicMap()
        val src = SpriteSource { name ->
            if (name in map) map.getValue(name) else throw IllegalStateException("broken source for $name")
        }
        val layer = PetLayer(src)
        assertEquals(PetSet.CLASSIC, layer.available)
        drawn(layer, PetPose(PetAnim.SHELTER, 0L, 1), 100L)
    }

    @Test fun newClipsPlayByTheirKind() {
        val map = classicMap { patterned(it.sprite, it.frames, it.durationMs, it.ordinal) }
        for (a in PetAnim.entries) if (!a.isClassic) map[a.sprite] = solid(a.sprite, 32, 32, a.frames, a.durationMs, a.ordinal)
        val layer = PetLayer(sourceOf(map))
        assertEquals(PetAnim.entries.toSet(), layer.available)
        fun px(anim: PetAnim, since: Long, hold: Int = -1) = drawn(layer, PetPose(anim, 0L, -1, hold), since)[40 * 80 + 40]
        val blink = PetAnim.BLINK.ordinal
        // a once clip: first frame, then second (72 ms per frame), then the last frame for ever
        assertEquals(colour(blink, 0), px(PetAnim.BLINK, 0L))
        assertEquals(colour(blink, 1), px(PetAnim.BLINK, 100L))
        assertEquals(colour(blink, 3), px(PetAnim.BLINK, 289L))
        assertEquals(colour(blink, 3), px(PetAnim.BLINK, 20_000L))
        assertEquals(colour(blink, 2), px(PetAnim.BLINK, 20_000L, hold = 2))
        // a loop clip with an intro and an outro: frame 0 once, then frames 1 to 4 for ever, never the last one
        val shelter = PetAnim.SHELTER.ordinal
        val allowed = (1..4).map { colour(shelter, it) }.toSet()
        assertEquals(colour(shelter, 0), px(PetAnim.SHELTER, 0L))
        for (t in listOf(200L, 1500L, 9_999L, 123_456L)) assertTrue("frame at $t", px(PetAnim.SHELTER, t) in allowed)
        // the dance clip is a one-shot clip that the layer repeats while the pose lasts
        val dance = PetAnim.DANCE.ordinal
        assertEquals(colour(dance, 0), px(PetAnim.DANCE, 0L))
        assertEquals(colour(dance, 0), px(PetAnim.DANCE, PetAnim.DANCE.durationMs.toLong()))
        assertNotEquals(colour(dance, 0), px(PetAnim.DANCE, 500L))
    }

    @Test fun missingClassicThrows() {
        for (a in PetAnim.entries) {
            if (!a.isClassic) continue
            val map = classicMap()
            map.remove(a.sprite)
            try {
                PetLayer(sourceOf(map))
                fail("${a.sprite} is required")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains(a.sprite))
            }
        }
    }

    @Test fun neverOutsideStage() {
        val map = newMap()
        map["pet_shadow"] = solid("pet_shadow", 48, 5, 4, 4000, 99)
        val layer = PetLayer(sourceOf(map))
        val corners = listOf(0 to 0, 7 to 0, 0 to 43, 7 to 43, -50 to -50, 100 to 100, 7 to -3, -3 to 60)
        for ((x, y) in corners) {
            for (a in PetAnim.entries) {
                val stage = PixelStage(8, 44)
                layer.draw(stage, PetPose(a, 0L, 2), x, y, 700L)
                assertEquals(8 * 44, stage.pixels.size)
            }
        }
    }
}
