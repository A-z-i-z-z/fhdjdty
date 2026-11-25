package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class FireResolveTest {
    private val living = FireParams(classic = false)

    private fun resolve(
        hearth: HearthState, progress: Float? = null, stoke: Float = 0f, celebration: Boolean = false,
        fire: FireParams = living, t: Long = 0L,
    ): FireSpec = FireResolve.resolve(FireSpec(), hearth, progress, stoke, celebration, fire, t)

    /** The `when` of the old SceneRenderer (sprite and intensity of the classic fire), written out again. */
    private fun oldSprite(hearth: HearthState, progress: Float?, t: Long): Pair<FireSprite, Float> = when (hearth) {
        HearthState.COLD -> FireSprite.COLD to 0f
        HearthState.KINDLING -> {
            val pr = (progress ?: 0.5f).coerceIn(0f, 1f)
            val sprite = if (pr < 0.15f) FireSprite.OUT else if (pr < 0.5f) FireSprite.LOW else if (pr < 0.85f) FireSprite.MID else FireSprite.HIGH
            sprite to (0.18f + 0.72f * pr)
        }
        HearthState.BLAZING -> FireSprite.HIGH to 1f
        HearthState.SMOKE -> {
            val slot = (t / 170L).toInt()
            val on = ((slot * 7 + slot / 3) % 5 + 5) % 5 < 3
            (if (on) FireSprite.LOW else FireSprite.OUT) to (if (on) 0.3f else 0.1f)
        }
    }

    @Test fun classicMapsLikeToday() {
        val times = listOf(0L, 169L, 170L, 500L, 1_000L, 1_700L, 3_333L, 9_999L)
        val progresses = listOf<Float?>(null, 0.1f, 0.15f, 0.4f, 0.5f, 0.84f, 0.85f, 0.9f, 1f)
        for (hearth in HearthState.entries) {
            for (p in progresses) {
                for (t in times) {
                    val spec = resolve(hearth, p, fire = FireParams.CLASSIC, t = t)
                    val (sprite, intensity) = oldSprite(hearth, p, t)
                    assertEquals("$hearth $p $t", sprite, spec.sprite)
                    assertEquals("$hearth $p $t", intensity, spec.intensity, 0f)
                    assertEquals(0f, spec.edge, 0f)
                    // the old ember count: 4 when smoking, else round(3 + 21 * intensity)
                    val base = if (hearth == HearthState.SMOKE) 4 else (3f + 21f * intensity).roundToInt()
                    assertEquals(base, spec.sparkBase)
                }
            }
        }
    }

    @Test fun classicIgnoresLevelWindAndTheLivingFields() {
        val plain = resolve(HearthState.BLAZING, fire = FireParams.CLASSIC)
        val loud = resolve(HearthState.BLAZING, fire = FireParams(classic = true, level = 1f, wind = 1f, holdProgress = 1f))
        assertEquals(plain.sprite, loud.sprite)
        assertEquals(plain.intensity, loud.intensity, 0f)
        assertEquals(FireSprite.HIGH, loud.sprite)
        assertEquals(1f, loud.intensity, 0f)
    }

    @Test fun blazingIdleIsMidAndWorkingIsHigh() {
        val idle = resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0f))
        assertEquals(FireSprite.MID, idle.sprite)
        assertEquals(FireTuning.IDLE_INTENSITY, idle.intensity, 1e-6f)
        assertEquals(FireSprite.MID, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0.29f)).sprite)
        assertEquals(FireSprite.HIGH, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0.30f)).sprite)
        val roar = resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 1f))
        assertEquals(FireSprite.HIGH, roar.sprite)
        assertEquals(1f, roar.intensity, 1e-6f)
        assertEquals(1f, roar.edge, 1e-6f)
        // in between: intensity 0.70 + 0.30 * level, edge 0.25 + 0.75 * level
        val mid = resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0.5f))
        assertEquals(0.85f, mid.intensity, 1e-6f)
        assertEquals(0.625f, mid.edge, 1e-6f)
        assertEquals(0.5f, mid.level, 0f)
    }

    @Test fun sparkBaseGrowsWithLevel() {
        assertEquals(4, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0f)).sparkBase)
        assertEquals(20, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 1f)).sparkBase)
        assertEquals(12, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0.5f)).sparkBase)
        // a tool step: level 0.9 gives round(4 + 14.4) = 18
        assertEquals(18, resolve(HearthState.BLAZING, fire = FireParams(classic = false, level = 0.9f)).sparkBase)
    }

    @Test fun stokeLiftsLevel() {
        val idle = resolve(HearthState.BLAZING, stoke = 0f)
        assertEquals(FireSprite.MID, idle.sprite)
        val stoked = resolve(HearthState.BLAZING, stoke = 1.2f)
        assertEquals(FireSprite.HIGH, stoked.sprite)
        assertEquals(0.72f, stoked.level, 1e-5f)
        assertEquals(1.2f, stoked.boost, 1e-6f)
        // a full stoke is worth 0.9
        assertEquals(0.9f, resolve(HearthState.BLAZING, stoke = 1.5f).level, 1e-6f)
        assertEquals(0.9f, resolve(HearthState.BLAZING, stoke = 9f).level, 1e-6f)
        // the work level wins when it is higher
        assertEquals(0.95f, resolve(HearthState.BLAZING, stoke = 0.3f, fire = FireParams(classic = false, level = 0.95f)).level, 1e-6f)
        // the boost: stoke plus 0.5 for a celebration, at most 1.5
        assertEquals(0.5f, resolve(HearthState.BLAZING, celebration = true).boost, 1e-6f)
        assertEquals(1.5f, resolve(HearthState.BLAZING, stoke = 1.4f, celebration = true).boost, 0f)
    }

    @Test fun kindlingEdgeGrowsWithProgress() {
        assertEquals(0f, resolve(HearthState.KINDLING, 0.1f).edge, 0f)
        assertEquals(0f, resolve(HearthState.KINDLING, 0.149f).edge, 0f)
        assertEquals(0.6f * 0.15f, resolve(HearthState.KINDLING, 0.15f).edge, 1e-6f)
        assertEquals(0.24f, resolve(HearthState.KINDLING, 0.4f).edge, 1e-6f)
        assertEquals(0.54f, resolve(HearthState.KINDLING, 0.9f).edge, 1e-6f)
        // same sprite and intensity as the classic fire
        for (p in listOf(0.1f, 0.4f, 0.7f, 0.9f)) {
            val a = resolve(HearthState.KINDLING, p)
            val b = resolve(HearthState.KINDLING, p, fire = FireParams.CLASSIC)
            assertEquals(b.sprite, a.sprite)
            assertEquals(b.intensity, a.intensity, 0f)
        }
        // classic never has an edge
        assertEquals(0f, resolve(HearthState.KINDLING, 0.9f, fire = FireParams.CLASSIC).edge, 0f)
    }

    @Test fun smokeAndColdHaveNoEdge() {
        for (t in listOf(0L, 170L, 340L, 510L, 680L)) {
            assertEquals(0f, resolve(HearthState.SMOKE, t = t).edge, 0f)
            assertEquals(oldSprite(HearthState.SMOKE, null, t).first, resolve(HearthState.SMOKE, t = t).sprite)
        }
        val cold = resolve(HearthState.COLD, stoke = 0f)
        assertEquals(FireSprite.COLD, cold.sprite)
        assertEquals(0f, cold.intensity, 0f)
        assertEquals(0f, cold.edge, 0f)
        // the work level does not light a cold fire
        assertEquals(FireSprite.COLD, resolve(HearthState.COLD, fire = FireParams(classic = false, level = 1f)).sprite)
    }

    @Test fun lightStepsSpritesAtQuarters() {
        val light = FireParams(classic = false, transition = FireTransition(FireTransition.Kind.LIGHT, startMs = 1_000L))
        val steps = listOf(0L to FireSprite.OUT, 225L to FireSprite.LOW, 450L to FireSprite.MID, 675L to FireSprite.HIGH)
        for ((e, sprite) in steps) {
            val spec = resolve(HearthState.BLAZING, fire = light, t = 1_000L + e)
            assertEquals("e=$e", sprite, spec.sprite)
            val f = e / 900f
            assertEquals(0.15f + 0.75f * f, spec.intensity, 1e-5f)
            assertEquals(0.8f * f, spec.edge, 1e-5f)
            assertTrue(spec.boost >= 0.8f * (1f - f) - 1e-5f)
        }
        // a burst of sparks at the start
        assertTrue(resolve(HearthState.BLAZING, fire = light, t = 1_000L).boost >= 0.79f)
        // before the start: elapsed 0; after 900 ms the transition is over and the hearth table is back
        assertEquals(FireSprite.OUT, resolve(HearthState.KINDLING, 0.1f, fire = light, t = 0L).sprite)
        val over = resolve(HearthState.BLAZING, fire = light, t = 1_900L)
        assertEquals(FireSprite.MID, over.sprite)
        assertEquals(FireTuning.IDLE_INTENSITY, over.intensity, 1e-6f)
    }

    @Test fun douseStepsAtQuarters() {
        val douse = FireParams(classic = false, transition = FireTransition(FireTransition.Kind.DOUSE, startMs = 0L))
        val steps = listOf(0L to FireSprite.HIGH, 175L to FireSprite.MID, 350L to FireSprite.LOW, 525L to FireSprite.OUT)
        for ((e, sprite) in steps) {
            val spec = resolve(HearthState.COLD, fire = douse, t = e)
            assertEquals("e=$e", sprite, spec.sprite)
            val f = e / 700f
            assertEquals(0.9f - 0.8f * f, spec.intensity, 1e-5f)
            assertEquals(0.8f * (1f - f), spec.edge, 1e-5f)
        }
        // over: the cold fire (the smoke afterwards is the scene's)
        assertEquals(FireSprite.COLD, resolve(HearthState.COLD, fire = douse, t = 700L).sprite)
    }

    @Test fun transitionsIgnoredInClassic() {
        val tr = FireTransition(FireTransition.Kind.LIGHT, startMs = 0L)
        for (hearth in HearthState.entries) {
            val with = resolve(hearth, 0.4f, fire = FireParams(classic = true, transition = tr), t = 300L)
            val without = resolve(hearth, 0.4f, fire = FireParams.CLASSIC, t = 300L)
            assertEquals(without.sprite, with.sprite)
            assertEquals(without.intensity, with.intensity, 0f)
            assertEquals(without.boost, with.boost, 0f)
        }
    }

    @Test fun frozenTransitionUsesFrozenElapsed() {
        val frozen = FireParams(classic = false, transition = FireTransition(FireTransition.Kind.LIGHT, 0L, frozenElapsedMs = 450L))
        for (t in listOf(0L, 99_999L)) {
            val spec = resolve(HearthState.BLAZING, fire = frozen, t = t)
            assertEquals(FireSprite.MID, spec.sprite)
            assertEquals(0.525f, spec.intensity, 1e-5f)
        }
    }

    @Test fun nanInputsAreZero() {
        val nan = resolve(HearthState.BLAZING, stoke = Float.NaN, fire = FireParams(classic = false, level = Float.NaN))
        assertEquals(FireSprite.MID, nan.sprite)
        assertEquals(0f, nan.level, 0f)
        assertEquals(0f, nan.boost, 0f)
        assertEquals(FireTuning.IDLE_INTENSITY, nan.intensity, 1e-6f)
        val k = resolve(HearthState.KINDLING, Float.NaN)
        assertEquals(oldSprite(HearthState.KINDLING, null, 0L).first, k.sprite)
    }

    @Test fun resolveDoesNotAllocate() {
        val out = FireSpec()
        val a = FireResolve.resolve(out, HearthState.BLAZING, null, 0f, false, living, 0L)
        val b = FireResolve.resolve(out, HearthState.COLD, null, 0f, false, living, 10L)
        assertSame(out, a)
        assertSame(out, b)
        assertEquals(FireSprite.COLD, out.sprite)
    }

    /** Independent reference values from the contract's formulas (recomputed by hand and with a script). */
    @Test fun intensityMatchesTheContractsFormulas() {
        // KINDLING: 0.18 + 0.72 * progress
        assertEquals(0.612f, FireResolve.intensityOf(HearthState.KINDLING, 0.6f, 0f, false, living, 0L), 1e-5f)
        assertEquals(0.612f, resolve(HearthState.KINDLING, 0.6f).intensity, 1e-5f)
        // living BLAZING: 0.70 + 0.30 * max(level, 0.9 * stoke / 1.5); level 0.4, stoke 0.8 gives leff 0.48
        val fire = FireParams(classic = false, level = 0.4f)
        assertEquals(0.844f, FireResolve.intensityOf(HearthState.BLAZING, null, 0.8f, false, fire, 0L), 1e-5f)
        assertEquals(0.844f, resolve(HearthState.BLAZING, stoke = 0.8f, fire = fire).intensity, 1e-5f)
        // DOUSE frozen at 300 ms: 0.9 - 0.8 * 300 / 700
        val douse = FireParams(classic = false, transition = FireTransition(FireTransition.Kind.DOUSE, 0L, frozenElapsedMs = 300L))
        assertEquals(0.557143f, FireResolve.intensityOf(HearthState.COLD, null, 0f, false, douse, 0L), 1e-5f)
        // LIGHT at t = 400: 0.15 + 0.75 * 400 / 900
        val light = FireParams(classic = false, transition = FireTransition(FireTransition.Kind.LIGHT, 0L))
        assertEquals(0.483333f, FireResolve.intensityOf(HearthState.BLAZING, null, 0f, false, light, 400L), 1e-5f)
        // classic BLAZING is 1, COLD 0
        assertEquals(1f, FireResolve.intensityOf(HearthState.BLAZING, null, 0f, false, FireParams.CLASSIC, 0L), 0f)
        assertEquals(0f, FireResolve.intensityOf(HearthState.COLD, null, 0f, false, FireParams.CLASSIC, 0L), 0f)
    }

    /** A guard: [FireResolve.resolve] and [FireResolve.intensityOf] must never drift apart (the first calls the second). */
    @Test fun intensityOfAgreesWithResolveEverywhere() {
        val trs = listOf(
            null,
            FireTransition(FireTransition.Kind.LIGHT, 0L),
            FireTransition(FireTransition.Kind.DOUSE, 0L, frozenElapsedMs = 300L),
        )
        for (hearth in HearthState.entries) {
            for (classic in listOf(true, false)) {
                for (tr in trs) {
                    for (level in listOf(0f, 0.4f, 1f)) {
                        for (stoke in listOf(0f, 0.8f)) {
                            for (t in listOf(0L, 400L, 2_000L)) {
                                val fire = FireParams(classic = classic, level = level, transition = tr)
                                val spec = FireResolve.resolve(FireSpec(), hearth, 0.6f, stoke, false, fire, t)
                                val i = FireResolve.intensityOf(hearth, 0.6f, stoke, false, fire, t)
                                assertEquals("$hearth $classic $tr $level $stoke $t", spec.intensity, i, 0f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun tuningConstantsAreTheContractsNumbers() {
        assertEquals(900L, FireTuning.LIGHT_MS)
        assertEquals(700L, FireTuning.DOUSE_MS)
        assertEquals(700L, FireTuning.HOLD_MS)
        assertEquals(0.30f, FireTuning.HIGH_AT, 0f)
        assertEquals(0.70f, FireTuning.IDLE_INTENSITY, 0f)
        assertEquals(20, FireTuning.RING_DOTS)
        assertEquals(dev.pocketagent.app.ui.theme.Dur.EPIC.toLong(), FireTuning.LIGHT_MS)
    }
}
