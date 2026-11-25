package dev.pocketagent.app.ui.pixel.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetBrainIdleTest {
    private val world = PetWorld()
    private val all = PetTestKit.ALL

    private fun weights(w: PetWorld, last: PetAnim? = null, avail: Set<PetAnim> = all) = PetBrain.idleWeights(w, last, avail)

    private fun assertWeights(expected: Map<PetAnim, Int>, actual: Map<PetAnim, Int>) {
        for ((k, v) in expected) assertEquals("weight of $k", v, actual[k])
    }

    @Test fun weightsTable() {
        val d = weights(world)
        assertEquals(9, d.size)
        assertWeights(
            mapOf(
                PetAnim.BLINK to 28, PetAnim.TAIL to 16, PetAnim.WARM to 12, PetAnim.YAWN to 12, PetAnim.STRETCH to 5,
                PetAnim.FIREFLY to 16, PetAnim.POKE to 7, PetAnim.HAPPY to 6, PetAnim.SNOW to 0,
            ),
            d,
        )
        // day: no fireflies, a sleepier yawn is rarer
        val day = weights(PetWorld(sunAltitudeDeg = 30f, localMinutes = 14 * 60))
        assertEquals(0, day[PetAnim.FIREFLY])
        assertEquals(3, day[PetAnim.YAWN])
        // morning at sun 0 (neither night nor day), 06:30
        val morning = weights(PetWorld(sunAltitudeDeg = 0f, localMinutes = 6 * 60 + 30))
        assertEquals(15, morning[PetAnim.STRETCH])
        assertEquals(12, morning[PetAnim.YAWN])
        assertEquals(8, morning[PetAnim.FIREFLY])
        // night and morning at 06:00: the multipliers compound
        val dawn = weights(PetWorld(sunAltitudeDeg = -10f, localMinutes = 6 * 60))
        assertEquals(24, dawn[PetAnim.YAWN])
        assertEquals(15, dawn[PetAnim.STRETCH])
        // day and morning: 6 * 0.5 * 2
        assertEquals(6, weights(PetWorld(sunAltitudeDeg = 20f, localMinutes = 8 * 60))[PetAnim.YAWN])
        // a weak fire: warming is less needed, poking more
        val weak = weights(world.copy(fireLevel = 0.3f))
        assertEquals(6, weak[PetAnim.WARM])
        assertEquals(14, weak[PetAnim.POKE])
        assertEquals(12, weights(world.copy(fireLevel = 0.5f))[PetAnim.WARM])
        // snow
        val snow = weights(world.copy(snow = 1))
        assertEquals(40, snow[PetAnim.SNOW])
        assertEquals(0, snow[PetAnim.FIREFLY])
        assertEquals(0, snow[PetAnim.POKE])
        // never twice in a row
        assertEquals(0, weights(world, last = PetAnim.BLINK)[PetAnim.BLINK])
        assertEquals(16, weights(world, last = PetAnim.BLINK)[PetAnim.TAIL])
        // an animation without a sprite has no weight
        val classicOnly = weights(world, avail = PetSet.CLASSIC)
        for ((a, w) in classicOnly) if (!a.isClassic) assertEquals("$a", 0, w)
        assertEquals(6, classicOnly[PetAnim.HAPPY])
    }

    @Test fun pickWeightedWalksTheTable() {
        val w = mapOf(PetAnim.BLINK to 2, PetAnim.TAIL to 0, PetAnim.WARM to 3)
        assertEquals(PetAnim.BLINK, PetBrain.pickWeighted(w, 0))
        assertEquals(PetAnim.BLINK, PetBrain.pickWeighted(w, 1))
        assertEquals(PetAnim.WARM, PetBrain.pickWeighted(w, 2))
        assertEquals(PetAnim.WARM, PetBrain.pickWeighted(w, 4))
        assertEquals(null, PetBrain.pickWeighted(w, 5))
        assertEquals(null, PetBrain.pickWeighted(emptyMap(), 0))
    }

    @Test fun distributionFollowsWeights() {
        val w = weights(world)
        val total = w.values.sum()
        val rng = Random(99L)
        val counts = HashMap<PetAnim, Int>()
        val draws = 20_000
        repeat(draws) {
            val a = PetBrain.pickWeighted(w, rng.nextInt(total))
            assertNotNull(a)
            counts[a!!] = (counts[a] ?: 0) + 1
        }
        for ((anim, weight) in w) {
            val expected = weight.toDouble() / total
            val got = (counts[anim] ?: 0).toDouble() / draws
            assertEquals("share of $anim", expected, got, 0.03)
        }
    }

    /** Idle actions found in a run: (action pose, the idle pose it replaced). */
    private class Action(val pose: PetPose, val idleBefore: PetPose)

    private fun actionsOf(log: List<Pair<Long, PetPose>>): List<Action> {
        val out = ArrayList<Action>()
        var prev: PetPose? = null
        for ((_, p) in log) {
            val q = prev
            if (q != null && q.anim == PetAnim.IDLE && p.anim != PetAnim.IDLE) out.add(Action(p, q))
            prev = p
        }
        return out
    }

    private val sixHours = 6L * 3_600_000L

    @Test fun gapIsTwoOrThreeCycles() {
        val log = PetTestKit.simulate(PetBrain(42L, all), 0L, sixHours, 33L, { world })
        val actions = actionsOf(log)
        assertTrue("only ${actions.size} actions in six hours", actions.size > 1500)
        var long = 0
        for (a in actions) {
            val gap = a.pose.startMs - a.idleBefore.startMs
            assertTrue("gap $gap", gap == 5720L || gap == 8580L)
            if (gap == 8580L) long++
        }
        val share = long.toDouble() / actions.size
        assertTrue("share of long gaps $share", share in 0.28..0.38)
    }

    @Test fun startsOnCycleBoundary() {
        val log = PetTestKit.simulate(PetBrain(7L, all), 0L, 3_600_000L, 33L, { world })
        val actions = actionsOf(log)
        assertTrue(actions.size > 200)
        for (a in actions) assertEquals(0L, (a.pose.startMs - a.idleBefore.startMs) % PetBrain.IDLE_CYCLE_MS)
    }

    @Test fun neverSameTwice() {
        val log = PetTestKit.simulate(PetBrain(5L, all), 0L, 3_600_000L, 33L, { world })
        val actions = actionsOf(log)
        for (i in 1 until actions.size) {
            assertTrue("twice ${actions[i].pose.anim} at ${actions[i].pose.startMs}", actions[i].pose.anim != actions[i - 1].pose.anim)
        }
    }

    @Test fun everyActionIsAnAllowedOne() {
        val log = PetTestKit.simulate(PetBrain(9L, all), 0L, 3_600_000L, 33L, { world })
        val seen = actionsOf(log).map { it.pose.anim }.toSet()
        val allowed = setOf(
            PetAnim.BLINK, PetAnim.TAIL, PetAnim.WARM, PetAnim.YAWN, PetAnim.STRETCH, PetAnim.FIREFLY, PetAnim.POKE, PetAnim.HAPPY,
        )
        assertTrue("unexpected action in $seen", allowed.containsAll(seen))
        assertEquals(allowed, seen)
    }

    @Test fun noIdleOutsideBaseIdle() {
        val cases = mapOf(
            PetAnim.SHELTER to world.copy(rain = 1),
            PetAnim.THINK to world.copy(busy = true),
            PetAnim.CARRY to world.copy(waitingResult = true),
            PetAnim.SLEEP to world.copy(fire = PetFire.COLD, fireLevel = 0f),
        )
        for ((expected, w) in cases) {
            val log = PetTestKit.simulate(PetBrain(1L, all), 0L, 600_000L, 33L, { w })
            for ((t, p) in log) assertEquals("at $t in $expected", expected, p.anim)
        }
    }

    @Test fun noIdleOutsideBaseIdleWithClassicSprites() {
        // SHELTER and CARRY are drawn as IDLE here, but the base is not IDLE: no idle actions (HAPPY is the only one)
        val cases = listOf(world.copy(rain = 1), world.copy(waitingResult = true))
        for (w in cases) {
            val log = PetTestKit.simulate(PetBrain(1L, PetSet.CLASSIC), 0L, 600_000L, 33L, { w })
            for ((t, p) in log) assertEquals("at $t", PetAnim.IDLE, p.anim)
        }
        // while a plain idle base does play the hop now and then
        val calm = PetTestKit.simulate(PetBrain(1L, PetSet.CLASSIC), 0L, 600_000L, 33L, { world })
        assertTrue(calm.any { it.second.anim == PetAnim.HAPPY })
    }

    @Test fun warmPlaysOneOrTwo() {
        val log = PetTestKit.simulate(PetBrain(21L, all), 0L, sixHours, 33L, { world })
        val runs = ArrayList<Int>()
        var starts = HashSet<Long>()
        var inWarm = false
        for ((_, p) in log) {
            if (p.anim == PetAnim.WARM) {
                starts.add(p.startMs)
                inWarm = true
            } else if (inWarm) {
                runs.add(starts.size)
                starts = HashSet()
                inWarm = false
            }
        }
        assertTrue(runs.isNotEmpty())
        assertTrue("runs $runs", runs.all { it == 1 || it == 2 })
        assertTrue(runs.contains(1))
        assertTrue(runs.contains(2))
    }

    @Test fun snowActionsAppearAndNoFireflyOrPoke() {
        val snowing = world.copy(snow = 1)
        val log = PetTestKit.simulate(PetBrain(2L, all), 0L, 3_600_000L, 33L, { snowing })
        val seen = actionsOf(log).map { it.pose.anim }.toSet()
        assertTrue(PetAnim.SNOW in seen)
        assertTrue(PetAnim.FIREFLY !in seen)
        assertTrue(PetAnim.POKE !in seen)
    }

    @Test fun actionLengthsAreTheAnimationLengths() {
        val log = PetTestKit.simulate(PetBrain(13L, all), 0L, 1_800_000L, 33L, { world })
        // the pose after an action is the idle pose again and starts at the exact end of the action
        var i = 1
        var checked = 0
        while (i < log.size) {
            val prev = log[i - 1].second
            val p = log[i].second
            if (prev.anim != PetAnim.IDLE && p.anim == PetAnim.IDLE && !prev.anim.loops) {
                val len = prev.anim.durationMs.toLong()
                val actionStart = actionStartBefore(log, i - 1)
                val total = p.startMs - actionStart
                assertTrue("${prev.anim}: $total", total == len || (prev.anim == PetAnim.WARM && total == 2 * len))
                checked++
            }
            i++
        }
        assertTrue(checked > 50)
    }

    private fun actionStartBefore(log: List<Pair<Long, PetPose>>, index: Int): Long {
        // walk back over the poses of the same action (WARM restarts its start every run)
        var j = index
        var start = log[j].second.startMs
        while (j > 0 && log[j - 1].second.anim == log[index].second.anim) {
            j--
            start = minOf(start, log[j].second.startMs)
        }
        return start
    }
}
