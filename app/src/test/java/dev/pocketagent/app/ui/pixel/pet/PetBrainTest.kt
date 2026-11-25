package dev.pocketagent.app.ui.pixel.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Shared by the brain tests: a world with the sun and the clock set, and a simple simulation loop. */
internal object PetTestKit {
    val ALL: Set<PetAnim> = PetAnim.entries.toSet()

    const val NIGHT_23_30 = 23 * 60 + 30
    const val NOON = 12 * 60

    val DEEP_NIGHT = PetWorld(sunAltitudeDeg = -30f, localMinutes = NIGHT_23_30)

    /** Steps [brain] from [from] to [to] (inclusive) every [stepMs]; events are posted before the step at their time. */
    fun simulate(
        brain: PetBrain,
        from: Long,
        to: Long,
        stepMs: Long,
        worldAt: (Long) -> PetWorld,
        events: Map<Long, List<PetEvent>> = emptyMap(),
    ): List<Pair<Long, PetPose>> {
        val out = ArrayList<Pair<Long, PetPose>>()
        var t = from
        while (t <= to) {
            events[t]?.forEach { brain.post(it) }
            out.add(t to brain.step(t, worldAt(t)))
            t += stepMs
        }
        return out
    }

    /** The anims of a simulation with consecutive duplicates removed. */
    fun sequence(log: List<Pair<Long, PetPose>>): List<PetAnim> {
        val out = ArrayList<PetAnim>()
        for ((_, p) in log) if (out.isEmpty() || out.last() != p.anim) out.add(p.anim)
        return out
    }
}

class PetBrainTest {
    private val world = PetWorld()
    private fun brain(seed: Long = 1L, avail: Set<PetAnim> = PetTestKit.ALL) = PetBrain(seed, avail)

    private fun baseOf(w: PetWorld): PetAnim = brain().step(0L, w).anim

    @Test fun sameSeedSameScript() {
        val events = mapOf(
            59_994L to listOf<PetEvent>(PetEvent.MemorySaved),
            120_021L to listOf<PetEvent>(PetEvent.Tap(1)),
            299_970L to listOf<PetEvent>(PetEvent.Failed, PetEvent.Approval),
            599_973L to listOf<PetEvent>(PetEvent.Overdrive),
        )
        val worldAt = { t: Long -> if (t in 400_000L..420_000L) world.copy(fire = PetFire.COLD, fireLevel = 0f) else world }
        fun run(seed: Long) = PetTestKit.simulate(brain(seed), 0L, 1_200_000L, 33L, worldAt, events).map { it.second }
        assertEquals(run(7L), run(7L))
    }

    @Test fun differentSeedDiffers() {
        fun run(seed: Long) = PetTestKit.simulate(brain(seed), 0L, 1_200_000L, 33L, { world }).map { it.second }
        assertNotEquals(run(1L), run(2L))
    }

    @Test fun baseTable() {
        assertEquals(PetAnim.SLEEP, baseOf(world.copy(fire = PetFire.COLD)))
        assertEquals(PetAnim.ERROR, baseOf(world.copy(fire = PetFire.SMOKE)))
        assertEquals(PetAnim.THINK, baseOf(world.copy(fire = PetFire.KINDLING)))
        assertEquals(PetAnim.DANCE, baseOf(world.copy(celebrating = true)))
        assertEquals(PetAnim.SLEEP, baseOf(PetTestKit.DEEP_NIGHT))
        assertEquals(PetAnim.THINK, baseOf(PetTestKit.DEEP_NIGHT.copy(busy = true)))
        assertEquals(PetAnim.LISTEN, baseOf(world.copy(listening = true)))
        assertEquals(PetAnim.READ, baseOf(world.copy(busy = true, toolRunning = true)))
        assertEquals(PetAnim.THINK, baseOf(world.copy(busy = true)))
        assertEquals(PetAnim.CARRY, baseOf(world.copy(waitingResult = true)))
        assertEquals(PetAnim.SHELTER, baseOf(world.copy(rain = 1)))
        assertEquals(PetAnim.SHELTER, baseOf(world.copy(rain = 2)))
        assertEquals(PetAnim.IDLE, baseOf(world))
        assertEquals(PetAnim.IDLE, baseOf(world.copy(snow = 2)))
    }

    @Test fun basePrecedence() {
        assertEquals(PetAnim.SLEEP, baseOf(world.copy(fire = PetFire.COLD, busy = true, toolRunning = true)))
        assertEquals(PetAnim.SLEEP, baseOf(world.copy(fire = PetFire.COLD, celebrating = true)))
        assertEquals(PetAnim.ERROR, baseOf(world.copy(fire = PetFire.SMOKE, celebrating = true)))
        assertEquals(PetAnim.DANCE, baseOf(PetTestKit.DEEP_NIGHT.copy(celebrating = true)))
        assertEquals(PetAnim.LISTEN, baseOf(world.copy(listening = true, busy = true)))
        assertEquals(PetAnim.THINK, baseOf(world.copy(busy = true, rain = 2)))
        assertEquals(PetAnim.CARRY, baseOf(world.copy(waitingResult = true, rain = 2)))
        assertEquals(PetAnim.THINK, baseOf(world.copy(busy = true, waitingResult = true)))
    }

    @Test fun deepNightTable() {
        fun deep(sun: Float, minutes: Int) = PetWorld(sunAltitudeDeg = sun, localMinutes = minutes).deepNight
        assertTrue(deep(-30f, 23 * 60))
        assertTrue(deep(-30f, 4 * 60 + 59))
        assertFalse(deep(-30f, 5 * 60))
        assertFalse(deep(-30f, 12 * 60))
        assertFalse(deep(-11.9f, 23 * 60))
        assertTrue(deep(-12f, 23 * 60))
        assertFalse(deep(30f, 3 * 60))
        assertTrue(deep(-30f, 0))
        assertFalse(deep(-30f, 22 * 60 + 59))
        // and the brain follows it
        assertEquals(PetAnim.SLEEP, baseOf(PetWorld(sunAltitudeDeg = -30f, localMinutes = 4 * 60 + 59)))
        assertEquals(PetAnim.IDLE, baseOf(PetWorld(sunAltitudeDeg = -30f, localMinutes = 5 * 60)))
        assertEquals(PetAnim.IDLE, baseOf(PetWorld(sunAltitudeDeg = -11.9f, localMinutes = 23 * 60)))
    }

    @Test fun nightDayMorningFlags() {
        assertTrue(PetWorld(sunAltitudeDeg = -6.1f).night)
        assertFalse(PetWorld(sunAltitudeDeg = -6f).night)
        assertTrue(PetWorld(sunAltitudeDeg = 6.1f).day)
        assertFalse(PetWorld(sunAltitudeDeg = 6f).day)
        assertTrue(PetWorld(localMinutes = 300).morning)
        assertTrue(PetWorld(localMinutes = 599).morning)
        assertFalse(PetWorld(localMinutes = 600).morning)
        assertFalse(PetWorld(localMinutes = 299).morning)
    }

    @Test fun defaultFireLevels() {
        assertEquals(0f, PetWorld.defaultFireLevel(PetFire.COLD, null), 0f)
        assertEquals(1f, PetWorld.defaultFireLevel(PetFire.BLAZING, null), 0f)
        assertEquals(0.2f, PetWorld.defaultFireLevel(PetFire.SMOKE, 0.9f), 0f)
        assertEquals(0.54f, PetWorld.defaultFireLevel(PetFire.KINDLING, null), 1e-6f)
        assertEquals(0.18f, PetWorld.defaultFireLevel(PetFire.KINDLING, 0f), 1e-6f)
        assertEquals(0.90f, PetWorld.defaultFireLevel(PetFire.KINDLING, 1f), 1e-6f)
        assertEquals(0.90f, PetWorld.defaultFireLevel(PetFire.KINDLING, 5f), 1e-6f)
    }

    @Test fun failedStartlesAtOnce() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Failed)
        val p = b.step(1000L, world)
        assertEquals(PetAnim.STARTLED, p.anim)
        assertEquals(1000L, p.startMs)
        assertEquals(PetAnim.STARTLED, b.step(1559L, world).anim)
        val back = b.step(1560L, world)
        assertEquals(PetAnim.IDLE, back.anim)
        assertEquals(1560L, back.startMs)
    }

    @Test fun urgentReplacesIdleButNotUrgent() {
        val b = brain(3L)
        // let an idle action start
        var t = 0L
        while (b.step(t, world).anim == PetAnim.IDLE) {
            t += 33L
            assertTrue("no idle action in 60 s", t < 60_000L)
        }
        val idleAnim = b.step(t, world).anim
        assertNotEquals(PetAnim.IDLE, idleAnim)
        b.post(PetEvent.Failed)
        t += 33L
        assertEquals(PetAnim.STARTLED, b.step(t, world).anim)
        val startledStart = t
        // a tap (urgent) while an urgent reaction plays is dropped, not queued
        b.post(PetEvent.Tap(1))
        assertEquals(PetAnim.STARTLED, b.step(t + 10L, world).anim)
        val end = startledStart + PetAnim.STARTLED.durationMs
        assertEquals(PetAnim.STARTLED, b.step(end - 1L, world).anim)
        val back = b.step(end, world)
        assertEquals(PetAnim.IDLE, back.anim)
        assertEquals(end, back.startMs)
    }

    @Test fun overdrivePreemptsAnUrgentReaction() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Failed)
        assertEquals(PetAnim.STARTLED, b.step(1000L, world).anim)
        b.post(PetEvent.Overdrive)
        val d = b.step(1100L, world)
        assertEquals(PetAnim.DANCE, d.anim)
        assertEquals(1100L, d.startMs)
        assertEquals(PetAnim.DANCE, b.step(1100L + 4199L, world).anim)
        assertEquals(PetAnim.IDLE, b.step(1100L + 4200L, world).anim)
    }

    @Test fun overdriveAfterTapChain() {
        val b = brain()
        b.step(0L, world)
        var t = 1000L
        for (n in 1..6) {
            b.post(PetEvent.Tap(n))
            b.step(t, world)
            t += 200L
        }
        b.post(PetEvent.Overdrive)
        val d = b.step(t, world)
        assertEquals(PetAnim.DANCE, d.anim)
        assertEquals(t, d.startMs)
        assertEquals(PetAnim.DANCE, b.step(t + 4199L, world).anim)
        val after = b.step(t + 4200L, world)
        assertEquals(PetAnim.IDLE, after.anim)
        assertEquals(t + 4200L, after.startMs)
    }

    @Test fun queuedUrgentKeepsItsClass() {
        // Failed plays, then smoke starts: its STARTLED waits (queued as urgent) and a tap must not cut it short
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Failed)
        b.step(1000L, world)                                         // STARTLED plays until 1490
        b.step(1100L, world.copy(fire = PetFire.SMOKE, fireLevel = 0.2f))   // the smoke startle is queued (same anim: merged)
        b.step(1490L, world.copy(fire = PetFire.SMOKE, fireLevel = 0.2f))
        // Overdrive plus a queued urgent: a second failure queues behind the dance, then plays as urgent
        b.post(PetEvent.Overdrive)
        b.step(2000L, world)
        b.post(PetEvent.Failed)
        assertEquals(PetAnim.DANCE, b.step(2100L, world).anim)
        val s = b.step(2000L + 4200L, world)
        assertEquals(PetAnim.STARTLED, s.anim)
        b.post(PetEvent.Tap(1))
        assertEquals(PetAnim.STARTLED, b.step(2000L + 4200L + 100L, world).anim)
        assertEquals(PetAnim.STARTLED, b.step(2000L + 4200L + 559L, world).anim)
    }

    @Test fun sameLookDoesNotRestartTheLoop() {
        // READ is missing: it is drawn as THINK, so busy, tool, busy keeps one THINK loop
        val b = brain(1L, PetSet.CLASSIC)
        val busy = world.copy(busy = true)
        val p0 = b.step(0L, busy)
        assertEquals(PetAnim.THINK, p0.anim)
        val p1 = b.step(500L, busy.copy(toolRunning = true))
        assertEquals(PetAnim.THINK, p1.anim)
        assertEquals(0L, p1.startMs)
        val p2 = b.step(1000L, busy)
        assertEquals(0L, p2.startMs)
        // with READ available it does change and restart
        val c = brain()
        c.step(0L, busy)
        val r = c.step(500L, busy.copy(toolRunning = true))
        assertEquals(PetAnim.READ, r.anim)
        assertEquals(500L, r.startMs)
    }

    @Test fun eventWaitsForReaction() {
        val b = brain()
        b.post(PetEvent.Approval)
        assertEquals(PetAnim.WAVE, b.step(0L, world).anim)
        b.post(PetEvent.MemorySaved)
        assertEquals(PetAnim.WAVE, b.step(100L, world).anim)
        assertEquals(PetAnim.WAVE, b.step(1219L, world).anim)
        val write = b.step(1220L, world)
        assertEquals(PetAnim.WRITE, write.anim)
        assertEquals(1220L, write.startMs)
        val back = b.step(1220L + 1080L, world)
        assertEquals(PetAnim.IDLE, back.anim)
        assertEquals(2300L, back.startMs)
    }

    @Test fun queueCapThree() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Forged)
        b.step(10L, world)                       // HAPPY plays
        b.post(PetEvent.MemorySaved)
        b.post(PetEvent.Approval)
        b.step(20L, world)                       // queue: WRITE, WAVE
        b.step(30L, world.copy(fire = PetFire.COLD, fireLevel = 0f))      // YAWN queued
        val log = ArrayList<Pair<Long, PetPose>>()
        var t = 40L
        while (t <= 5600L) {
            log.add(t to b.step(t, world))       // fire back: STRETCH queued, WRITE (the oldest) dropped
            t += 10L
        }
        val seq = PetTestKit.sequence(log)
        assertFalse("WRITE must have been dropped: $seq", PetAnim.WRITE in seq)
        assertEquals(listOf(PetAnim.HAPPY, PetAnim.WAVE, PetAnim.YAWN, PetAnim.STRETCH, PetAnim.IDLE), seq)
    }

    @Test fun duplicateMerged() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Forged)
        b.step(10L, world)
        b.post(PetEvent.Approval)
        b.post(PetEvent.Approval)
        b.post(PetEvent.Approval)
        val log = PetTestKit.simulate(b, 20L, 4000L, 10L, { world })
        assertEquals(listOf(PetAnim.HAPPY, PetAnim.WAVE, PetAnim.IDLE), PetTestKit.sequence(log))
    }

    @Test fun yawnThenSleepWhenFireGoesOut() {
        val b = brain()
        b.step(0L, world)
        val cold = world.copy(fire = PetFire.COLD, fireLevel = 0f)
        val y = b.step(1000L, cold)
        assertEquals(PetAnim.YAWN, y.anim)
        assertEquals(1000L, y.startMs)
        assertEquals(PetAnim.YAWN, b.step(1000L + 1419L, cold).anim)
        val s = b.step(1000L + 1420L, cold)
        assertEquals(PetAnim.SLEEP, s.anim)
        assertEquals(2420L, s.startMs)
    }

    @Test fun yawnThenSleepAtNightfall() {
        val b = brain()
        b.step(0L, world)
        assertEquals(PetAnim.YAWN, b.step(500L, PetTestKit.DEEP_NIGHT).anim)
        assertEquals(PetAnim.SLEEP, b.step(500L + 1420L, PetTestKit.DEEP_NIGHT).anim)
    }

    @Test fun stretchOnWaking() {
        val cold = world.copy(fire = PetFire.COLD, fireLevel = 0f)
        // fire lit again
        val b = brain()
        b.step(0L, cold)
        assertEquals(PetAnim.SLEEP, b.step(100L, cold).anim)
        val st = b.step(5000L, world)
        assertEquals(PetAnim.STRETCH, st.anim)
        assertEquals(5000L, st.startMs)
        val after = b.step(5000L + 1370L, world)
        assertEquals(PetAnim.IDLE, after.anim)
        assertEquals(6370L, after.startMs)
        // the morning light ends the deep night
        val c = brain()
        c.step(0L, PetTestKit.DEEP_NIGHT)
        assertEquals(PetAnim.STRETCH, c.step(3000L, world.copy(sunAltitudeDeg = 5f, localMinutes = 6 * 60)).anim)
    }

    @Test fun startleWhenSmokeBegins() {
        val b = brain()
        b.step(0L, world)
        val smoke = world.copy(fire = PetFire.SMOKE, fireLevel = 0.2f)
        val s = b.step(2000L, smoke)
        assertEquals(PetAnim.STARTLED, s.anim)
        assertEquals(2000L, s.startMs)
        val e = b.step(2000L + 560L, smoke)
        assertEquals(PetAnim.ERROR, e.anim)
        assertEquals(2560L, e.startMs)
    }

    @Test fun firstStepNoTransition() {
        assertEquals(PetAnim.SLEEP, brain().step(0L, world.copy(fire = PetFire.COLD)).anim)
        assertEquals(PetAnim.ERROR, brain().step(0L, world.copy(fire = PetFire.SMOKE)).anim)
        assertEquals(PetAnim.SLEEP, brain().step(0L, PetTestKit.DEEP_NIGHT).anim)
        assertEquals(PetAnim.THINK, brain().step(0L, world.copy(busy = true)).anim)
    }

    @Test fun otherBaseChangesBeginAtOnce() {
        val b = brain()
        b.step(0L, world)
        val p = b.step(777L, world.copy(busy = true))
        assertEquals(PetAnim.THINK, p.anim)
        assertEquals(777L, p.startMs)
        val q = b.step(900L, world.copy(busy = true, toolRunning = true))
        assertEquals(PetAnim.READ, q.anim)
        assertEquals(900L, q.startMs)
    }

    @Test fun tapParity() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Tap(1))
        assertEquals(PetAnim.HAPPY, b.step(100L, world).anim)
        val c = brain()
        c.step(0L, world)
        c.post(PetEvent.Tap(2))
        assertEquals(PetAnim.WAVE, c.step(100L, world).anim)
        val d = brain()
        d.step(0L, world)
        d.post(PetEvent.Tap(7))
        assertEquals(PetAnim.HAPPY, d.step(100L, world).anim)
    }

    @Test fun tapOnSleepingFireOutStretches() {
        val cold = world.copy(fire = PetFire.COLD, fireLevel = 0f)
        val b = brain()
        b.step(0L, cold)
        b.post(PetEvent.Tap(1))
        val p = b.step(100L, cold)
        assertEquals(PetAnim.STRETCH, p.anim)
        assertEquals(100L, p.startMs)
        assertEquals(PetAnim.SLEEP, b.step(100L + 1370L, cold).anim)
    }

    @Test fun tapAtDeepNightGivesGraceThen20s() {
        val w = PetTestKit.DEEP_NIGHT.copy(lively = false)
        val b = brain()
        assertEquals(PetAnim.SLEEP, b.step(0L, w).anim)
        b.post(PetEvent.Tap(1))
        val s = b.step(100L, w)
        assertEquals(PetAnim.STRETCH, s.anim)
        assertEquals(PetAnim.IDLE, b.step(100L + 1370L, w).anim)
        assertEquals(PetAnim.IDLE, b.step(100L + PetBrain.AWAKE_GRACE_MS - 1L, w).anim)
        val y = b.step(100L + PetBrain.AWAKE_GRACE_MS, w)
        assertEquals(PetAnim.YAWN, y.anim)
        assertEquals(PetAnim.SLEEP, b.step(100L + PetBrain.AWAKE_GRACE_MS + 1420L, w).anim)
    }

    @Test fun tapOnCarryDoesNothing() {
        val w = world.copy(waitingResult = true)
        val b = brain()
        val first = b.step(0L, w)
        b.post(PetEvent.Tap(1))
        val p = b.step(100L, w)
        assertEquals(PetAnim.CARRY, p.anim)
        assertSame(first, p)
    }

    @Test fun overdriveDancesFor4200() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Overdrive)
        val d = b.step(1000L, world)
        assertEquals(PetAnim.DANCE, d.anim)
        assertEquals(1000L, d.startMs)
        assertEquals(PetAnim.DANCE, b.step(1000L + 4199L, world).anim)
        val back = b.step(1000L + 4200L, world)
        assertEquals(PetAnim.IDLE, back.anim)
        assertEquals(5200L, back.startMs)
    }

    @Test fun timeGoingBackwardsResyncs() {
        val b = brain()
        b.step(0L, world)
        b.post(PetEvent.Approval)
        b.step(100_000L, world)                   // WAVE plays from 100_000
        val p = b.step(50L, world)                // a new scene: the clock restarted
        assertEquals(PetAnim.IDLE, p.anim)
        assertEquals(50L, p.startMs)
        // an event posted before the restart stays
        b.step(200_000L, world)
        b.post(PetEvent.Failed)
        val q = b.step(40L, world)
        assertEquals(PetAnim.STARTLED, q.anim)
        assertEquals(40L, q.startMs)
    }

    @Test fun repeatedStepSameInstance() {
        val b = brain()
        val p1 = b.step(0L, world)
        val p2 = b.step(0L, world)
        assertSame(p1, p2)
        assertSame(p1, b.step(10L, world))
        assertSame(p1, b.step(2000L, world))
    }

    @Test fun notAnimateIsStill() {
        val still = world.copy(animate = false)
        val b = brain()
        b.post(PetEvent.Failed)
        val log = PetTestKit.simulate(b, 0L, 120_000L, 33L, { still }, mapOf(5000L to listOf(PetEvent.Overdrive, PetEvent.Tap(1))))
        for ((_, p) in log) {
            assertEquals(PetAnim.IDLE, p.anim)
            assertEquals(0L, p.startMs)
            assertEquals(0, p.holdFrame)
        }
        // a still pose of another base
        assertEquals(PetAnim.SLEEP, brain().step(0L, still.copy(fire = PetFire.COLD)).anim)
        assertEquals(0, brain().step(0L, still.copy(fire = PetFire.COLD)).holdFrame)
    }

    @Test fun animateComesBackCleanly() {
        val b = brain()
        b.step(0L, world.copy(animate = false))
        val p = b.step(1000L, world)
        assertEquals(PetAnim.IDLE, p.anim)
        assertEquals(1000L, p.startMs)
        assertEquals(-1, p.holdFrame)
    }

    @Test fun notLivelyNoIdleActions() {
        val calm = world.copy(lively = false)
        val log = PetTestKit.simulate(brain(), 0L, 600_000L, 33L, { calm })
        for ((_, p) in log) assertEquals(PetAnim.IDLE, p.anim)
        // events still react
        val b = brain()
        b.step(0L, calm)
        b.post(PetEvent.Failed)
        assertEquals(PetAnim.STARTLED, b.step(100L, calm).anim)
        b.step(5000L, calm)
        b.post(PetEvent.Forged)
        assertEquals(PetAnim.HAPPY, b.step(5100L, calm).anim)
    }

    @Test fun livelyAgainAfterCalmFiresAtOnceAtMostOnce() {
        val b = brain()
        PetTestKit.simulate(b, 0L, 100_000L, 33L, { world.copy(lively = false) })
        val log = PetTestKit.simulate(b, 100_000L, 130_000L, 33L, { world })
        // actions come back, spaced like always
        assertTrue(log.any { it.second.anim != PetAnim.IDLE })
    }

    @Test fun fallbackNeverEmitsUnavailable() {
        val r = Random(5)
        val b = brain(11L, PetSet.CLASSIC)
        var world = PetWorld()
        var t = 0L
        var nextChange = 0L
        while (t <= 3_600_000L) {
            if (t >= nextChange) {
                world = PetWorld(
                    fire = PetFire.entries[if (r.nextInt(4) == 0) r.nextInt(4) else 2],
                    fireLevel = r.nextFloat(),
                    sunAltitudeDeg = (r.nextInt(90) - 45).toFloat(),
                    localMinutes = r.nextInt(1440),
                    rain = r.nextInt(3), snow = r.nextInt(3),
                    busy = r.nextInt(4) == 0, toolRunning = r.nextBoolean(),
                    waitingResult = r.nextInt(5) == 0, celebrating = r.nextInt(8) == 0, listening = r.nextInt(8) == 0,
                    lively = true,
                )
                nextChange = t + 500L + r.nextInt(30_000)
            }
            if (r.nextInt(300) == 0) {
                b.post(
                    when (r.nextInt(6)) {
                        0 -> PetEvent.MemorySaved; 1 -> PetEvent.Failed; 2 -> PetEvent.Forged
                        3 -> PetEvent.Approval; 4 -> PetEvent.Tap(r.nextInt(20)); else -> PetEvent.Overdrive
                    },
                )
            }
            val p = b.step(t, world)
            assertTrue("emitted ${p.anim} with only the classic sprites", p.anim.isClassic)
            assertTrue(p.shadow in 0..3)
            t += 33L
        }
    }

    @Test fun fallbackTargets() {
        val classic = PetSet.CLASSIC
        assertEquals(PetAnim.HAPPY, brain(1L, classic).step(0L, world.copy(celebrating = true)).anim)
        assertEquals(PetAnim.THINK, brain(1L, classic).step(0L, world.copy(busy = true, toolRunning = true)).anim)
        assertEquals(PetAnim.THINK, brain(1L, classic).step(0L, world.copy(listening = true)).anim)
        assertEquals(PetAnim.IDLE, brain(1L, classic).step(0L, world.copy(rain = 2)).anim)
        assertEquals(PetAnim.IDLE, brain(1L, classic).step(0L, world.copy(waitingResult = true)).anim)
    }

    @Test fun skippedDecorationLeavesNoPause() {
        val b = brain(1L, PetSet.CLASSIC)
        val first = b.step(0L, world)
        b.post(PetEvent.Failed)                  // STARTLED has no sprite: nothing at all happens
        val p = b.step(1000L, world)
        assertEquals(PetAnim.IDLE, p.anim)
        assertEquals(first.startMs, p.startMs)
        b.post(PetEvent.MemorySaved)             // WRITE has none either
        assertEquals(first.startMs, b.step(1100L, world).startMs)
        b.post(PetEvent.Approval)                // WAVE is classic and plays
        assertEquals(PetAnim.WAVE, b.step(1200L, world).anim)
    }

    @Test fun overdriveWithoutDanceSpriteShowsTheHappyHop() {
        val b = brain(1L, PetSet.CLASSIC)
        b.step(0L, world)
        b.post(PetEvent.Overdrive)
        val a = b.step(1000L, world)
        assertEquals(PetAnim.HAPPY, a.anim)
        assertEquals(1000L, a.startMs)
        // 4200 ms is longer than one hop (1300): the hop starts again so the layer shows it moving
        val later = b.step(1000L + 1400L, world)
        assertEquals(PetAnim.HAPPY, later.anim)
        assertEquals(1000L + 1300L, later.startMs)
    }

    @Test fun setAvailableMidwayNeverEmitsMissing() {
        val b = brain(1L, PetTestKit.ALL)
        val celebrate = world.copy(celebrating = true)
        assertEquals(PetAnim.DANCE, b.step(0L, celebrate).anim)
        b.setAvailable(emptySet())               // classic is always added
        assertEquals(PetAnim.HAPPY, b.step(33L, celebrate).anim)
        b.setAvailable(PetTestKit.ALL)
        assertEquals(PetAnim.DANCE, b.step(66L, celebrate).anim)
        // a reaction that is playing is resolved again every step
        val c = brain(1L, PetTestKit.ALL)
        c.step(0L, world)
        c.post(PetEvent.Overdrive)
        assertEquals(PetAnim.DANCE, c.step(100L, world).anim)
        c.setAvailable(PetSet.CLASSIC)
        assertTrue(c.step(133L, world).anim.isClassic)
    }

    @Test fun shadowHysteresis() {
        // between the way down from level 2 (below 0.38) and the way up from level 1 (from 0.45): no change, ever
        val r = Random(3)
        val jitterAt1 = PetTestKit.simulate(brain(), 0L, 600_000L, 100L, { world.copy(fireLevel = 0.41f + (r.nextFloat() - 0.5f) * 0.04f) })
        assertTrue(jitterAt1.all { it.second.shadow == 1 })
        val jitterAt2 = PetTestKit.simulate(
            brain(), 0L, 600_000L, 100L,
            { t -> world.copy(fireLevel = if (t == 0L) 0.6f else 0.41f + (r.nextFloat() - 0.5f) * 0.04f) },
        )
        assertTrue(jitterAt2.all { it.second.shadow == 2 })
        // a ramp up gives 0, 1, 2, 3 in order
        val up = PetTestKit.simulate(brain(), 0L, 10_000L, 50L, { world.copy(fireLevel = it / 10_000f) })
        assertEquals(listOf(0, 1, 2, 3), distinctRuns(up.map { it.second.shadow }))
        // and a ramp down gives 3, 2, 1, 0
        val down = PetTestKit.simulate(brain(), 0L, 10_000L, 50L, { world.copy(fireLevel = 1f - it / 10_000f) })
        assertEquals(listOf(3, 2, 1, 0), distinctRuns(down.map { it.second.shadow }))
    }

    @Test fun shadowAtMostOneChangePer500ms() {
        val flip = PetTestKit.simulate(brain(), 0L, 60_000L, 50L, { t -> world.copy(fireLevel = if ((t / 50L) % 2L == 0L) 0f else 1f) })
        var lastChange = Long.MIN_VALUE
        var lastLevel = flip.first().second.shadow
        for ((t, p) in flip) {
            if (p.shadow != lastLevel) {
                if (lastChange != Long.MIN_VALUE) assertTrue("two changes within 500 ms at $t", t - lastChange >= 500L)
                lastChange = t
                lastLevel = p.shadow
            }
        }
        assertTrue(lastChange != Long.MIN_VALUE)
    }

    @Test fun shadowNeverNegative() {
        val log = PetTestKit.simulate(brain(), 0L, 60_000L, 33L, { t -> world.copy(fireLevel = (t % 7000L) / 7000f) })
        assertTrue(log.all { it.second.shadow in 0..3 })
    }

    private fun distinctRuns(values: List<Int>): List<Int> {
        val out = ArrayList<Int>()
        for (v in values) if (out.isEmpty() || out.last() != v) out.add(v)
        return out
    }
}
