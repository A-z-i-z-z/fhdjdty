package dev.pocketagent.app.voice

import dev.pocketagent.app.ui.pixel.HearthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File names, loop arithmetic and the hearth-change decision of the scene sounds. The slice that adds WAKE and SLEEP
 * to SoundCue and writes `hearthCue` adds `cueEnumOrder` (the old ten keep their ordinals, WAKE 10, SLEEP 11) and
 * `hearthCueMapsTurns` here.
 */
class SoundCuesTest {
    private val oldTen = listOf(
        SoundCue.COMPLETE to "complete", SoundCue.SEND to "send", SoundCue.TOOL to "tool", SoundCue.ERROR to "error",
        SoundCue.APPROVAL to "approval", SoundCue.FORGE to "forge", SoundCue.MEMORY to "memory", SoundCue.SPARK to "spark",
        SoundCue.TAP to "tap", SoundCue.MIC to "mic",
    )

    @Test
    fun cueFileNames() {
        for ((cue, word) in oldTen) {
            assertEquals("sfx_hearth_$word", SoundFiles.cueName(SoundPack.HEARTH, cue))
            assertEquals("sfx_studio_$word", SoundFiles.cueName(SoundPack.STUDIO, cue))
            assertNull(SoundFiles.cueName(SoundPack.OFF, cue))
        }
        // The two cues of this wave, by their enum names.
        assertEquals("sfx_hearth_wake", SoundFiles.cueFile(SoundPack.HEARTH, "WAKE"))
        assertEquals("sfx_hearth_sleep", SoundFiles.cueFile(SoundPack.HEARTH, "SLEEP"))
        assertEquals("sfx_studio_wake", SoundFiles.cueFile(SoundPack.STUDIO, "WAKE"))
        assertEquals("sfx_studio_sleep", SoundFiles.cueFile(SoundPack.STUDIO, "SLEEP"))
        assertNull(SoundFiles.cueFile(SoundPack.OFF, "WAKE"))
    }

    @Test
    fun loopFileNames() {
        assertEquals("sfx_loop_rain", SoundFiles.loopName(SoundLoop.RAIN))
        assertEquals("sfx_loop_crackle", SoundFiles.loopName(SoundLoop.CRACKLE))
        assertEquals(listOf(SoundLoop.RAIN, SoundLoop.CRACKLE), SoundLoop.entries.toList())
    }

    @Test
    fun fadeCurve() {
        assertEquals(0f, LoopMath.fade(0f, 1f, 0L), 0f)
        assertEquals(0.5f, LoopMath.fade(0f, 1f, 200L), 1e-6f)
        assertEquals(1f, LoopMath.fade(0f, 1f, 400L), 0f)
        // Clamped past both ends.
        assertEquals(0f, LoopMath.fade(0f, 1f, -50L), 0f)
        assertEquals(1f, LoopMath.fade(0f, 1f, 4_000L), 0f)
        // Reverse direction.
        assertEquals(0.8f, LoopMath.fade(0.8f, 0f, 0L), 1e-6f)
        assertEquals(0.4f, LoopMath.fade(0.8f, 0f, 200L), 1e-6f)
        assertEquals(0f, LoopMath.fade(0.8f, 0f, 400L), 1e-6f)
        // A custom duration, and a zero or negative duration gives the target at once.
        assertEquals(0.25f, LoopMath.fade(0f, 1f, 100L, 400L), 1e-6f)
        assertEquals(0.5f, LoopMath.fade(0f, 1f, 50L, 100L), 1e-6f)
        assertEquals(0.7f, LoopMath.fade(0.2f, 0.7f, 0L, 0L), 0f)
        assertEquals(0.7f, LoopMath.fade(0.2f, 0.7f, 10L, -5L), 0f)
        assertEquals(400L, LoopMath.FADE_MS)
    }

    @Test
    fun fadeIsMonotonicOverTheDefaultDuration() {
        var last = LoopMath.fade(0.1f, 0.9f, 0L)
        for (ms in 40L..400L step 40L) {
            val v = LoopMath.fade(0.1f, 0.9f, ms)
            assertTrue("fade went backwards at $ms ms", v >= last)
            last = v
        }
        assertEquals(0.9f, last, 1e-6f)
    }

    @Test
    fun rainLevels() {
        assertEquals(0f, LoopMath.rainLevel(0, true), 0f)
        assertEquals(0f, LoopMath.rainLevel(-1, true), 0f)
        assertEquals(0f, LoopMath.rainLevel(2, false), 0f)
        assertEquals(0f, LoopMath.rainLevel(1, false), 0f)
        assertEquals(0.4f, LoopMath.rainLevel(1, true), 0f)
        assertEquals(0.8f, LoopMath.rainLevel(2, true), 0f)
        assertEquals(0.8f, LoopMath.rainLevel(5, true), 0f)
    }

    @Test
    fun crackleLevels() {
        assertEquals(0.25f, LoopMath.crackleLevel(true, true), 0f)
        assertEquals(0f, LoopMath.crackleLevel(true, false), 0f)
        assertEquals(0f, LoopMath.crackleLevel(false, true), 0f)
        assertEquals(0f, LoopMath.crackleLevel(false, false), 0f)
    }

    @Test
    fun baseGains() {
        assertEquals(0.55f, LoopMath.RAIN_BASE, 0f)
        assertEquals(0.25f, LoopMath.CRACKLE_BASE, 0f)
    }

    @Test
    fun hearthTurnTable() {
        val states = HearthState.entries
        val expected = mapOf(
            (HearthState.COLD to HearthState.BLAZING) to HearthCues.Turn.WAKE,
            (HearthState.KINDLING to HearthState.BLAZING) to HearthCues.Turn.WAKE,
            (HearthState.BLAZING to HearthState.COLD) to HearthCues.Turn.SLEEP,
        )
        for (prev in states) for (next in states) {
            assertEquals("$prev to $next", expected[prev to next], HearthCues.turnOf(prev, next))
        }
        // Named pairs of the contract.
        assertNull(HearthCues.turnOf(null, HearthState.BLAZING))
        assertNull(HearthCues.turnOf(null, HearthState.COLD))
        assertNull(HearthCues.turnOf(HearthState.BLAZING, HearthState.SMOKE))
        assertNull(HearthCues.turnOf(HearthState.SMOKE, HearthState.COLD))
        assertNull(HearthCues.turnOf(HearthState.SMOKE, HearthState.BLAZING))
        assertNull(HearthCues.turnOf(HearthState.BLAZING, HearthState.BLAZING))
        assertNull(HearthCues.turnOf(HearthState.BLAZING, HearthState.KINDLING))
    }
}
