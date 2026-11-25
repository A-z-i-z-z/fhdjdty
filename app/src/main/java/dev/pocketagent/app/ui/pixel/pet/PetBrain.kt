package dev.pocketagent.app.ui.pixel.pet

import kotlin.random.Random

/**
 * Pêt's mind: a pure state machine. Time (the scene clock), the world and the events come in as parameters, the
 * randomness is a seeded generator, so the same script gives the same poses. One thread calls [step], any thread
 * may call [post].
 *
 * What it does, in the order of a step:
 *  1. a finished reaction ends and the next queued one starts at once, else the base pose comes back (its start
 *     is the end of the reaction, so the idle animation restarts at its first frame);
 *  2. waiting events become reactions (a failure startles at once, the others wait for the current reaction);
 *  3. the base pose follows the world (fire, celebration, deep night, listening, work, rain); a change of base
 *     can add a reaction (yawn before sleep, stretch on waking, startle at smoke);
 *  4. when the base is the idle pose and nothing plays, an idle action is drawn every two or three idle cycles.
 */
class PetBrain(seed: Long, available: Set<PetAnim> = PetSet.CLASSIC) {
    private class Reaction(val anim: PetAnim, val cls: Int, val start: Long, val end: Long)
    private class Pending(val anim: PetAnim, val lengthMs: Int, val cls: Int)

    private val rng = Random(seed)

    @Volatile
    private var avail: Set<PetAnim> = withClassic(available)

    private val inbox = ArrayDeque<PetEvent>()
    private val queue = ArrayDeque<Pending>()

    private var started = false
    private var lastNow = 0L
    private var lastBase: PetAnim? = null
    private var baseStart = 0L
    private var cur: Reaction? = null
    private var idleStart = 0L
    private var nextIdleAt = 0L
    private var lastIdle: PetAnim? = null
    private var awakeUntil = Long.MIN_VALUE
    private var shadowInit = false
    private var shadowLevel = 1
    private var shadowChangedAt = 0L
    private var lastPose: PetPose? = null

    /** Thread-safe. At most [EVENT_INBOX_MAX] events wait (the oldest is dropped); they are timed at the next step. */
    fun post(event: PetEvent) {
        synchronized(inbox) {
            inbox.addLast(event)
            while (inbox.size > EVENT_INBOX_MAX) inbox.removeFirst()
        }
    }

    /** [PetSet.CLASSIC] is always added. Takes effect at the next step. */
    fun setAvailable(set: Set<PetAnim>) {
        avail = withClassic(set)
    }

    /**
     * The pose at scene time [nowMs]. The same instance comes back while nothing changes; calling twice with the same
     * time is harmless. When [nowMs] is lower than the last one (a new scene started) the brain forgets reaction, queue
     * and schedule and starts clean (waiting events stay).
     */
    fun step(nowMs: Long, world: PetWorld): PetPose {
        val a = avail
        if (!started || nowMs < lastNow) resync()
        lastNow = nowMs
        val level = shadowFor(nowMs, world.fireLevel)

        if (!world.animate) {
            takeEvents()
            cur = null
            queue.clear()
            lastBase = null
            val still = resolve(baseOf(world, nowMs), a)
            return emit(PetPose(still, 0L, level, 0))
        }

        advance(nowMs, a)
        val events = takeEvents()
        if (events != null) for (e in events) handle(e, world, nowMs, a)

        val raw = baseOf(world, nowMs)
        if (raw != lastBase) onBaseChange(lastBase, raw, nowMs, a)
        if (cur == null) startFromQueue(nowMs, a)

        val resolvedBase = resolve(raw, a)
        if (cur == null && raw == PetAnim.IDLE && nowMs >= nextIdleAt) {
            if (world.lively && nowMs - nextIdleAt < IDLE_CYCLE_MS) {
                startIdle(world, a)
                advance(nowMs, a)
            } else {
                rebaseIdle(nowMs)
            }
        }

        val c = cur
        val pose = if (c != null) {
            val an = resolve(c.anim, a)
            PetPose(an, startOf(an, c.start, c.end - c.start, nowMs), level, -1)
        } else {
            PetPose(resolvedBase, startOf(resolvedBase, baseStart, Long.MAX_VALUE, nowMs), level, -1)
        }
        return emit(pose)
    }

    // ---- reactions ----------------------------------------------------------------------------------------------

    private fun resync() {
        started = true
        cur = null
        queue.clear()
        lastBase = null
        awakeUntil = Long.MIN_VALUE
        shadowInit = false
        lastPose = null
        lastIdle = null
    }

    private fun takeEvents(): List<PetEvent>? = synchronized(inbox) {
        if (inbox.isEmpty()) {
            null
        } else {
            val list = ArrayList<PetEvent>(inbox)
            inbox.clear()
            list
        }
    }

    /** Ends reactions whose time is up; the next queued one starts at the exact end of the previous one. */
    private fun advance(now: Long, a: Set<PetAnim>) {
        while (true) {
            val c = cur ?: return
            if (now < c.end) return
            cur = null
            if (!startFromQueue(c.end, a)) {
                baseStart = c.end
                idleStart = c.end
                scheduleIdle()
            }
        }
    }

    /** Starts the first queued reaction that can be shown at [at]. False when none starts. */
    private fun startFromQueue(at: Long, a: Set<PetAnim>): Boolean {
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val r = resolve(p.anim, a)
            if (skipped(p.anim, r)) continue
            cur = Reaction(r, p.cls, at, at + p.lengthMs)
            return true
        }
        return false
    }

    /** A decoration without a sprite is skipped, not replaced by an idle pose (no invisible pause). */
    private fun skipped(wanted: PetAnim, resolved: PetAnim): Boolean = resolved == PetAnim.IDLE && wanted != PetAnim.IDLE

    /**
     * [dropIfUrgentBusy]: an urgent reaction that arrives while another urgent one plays is thrown away instead of
     * queued (a chain of taps must not pile up hops that would delay what comes next).
     */
    private fun react(
        anim: PetAnim,
        cls: Int,
        lengthMs: Int,
        now: Long,
        a: Set<PetAnim>,
        dropIfUrgentBusy: Boolean = false,
    ) {
        val r = resolve(anim, a)
        if (skipped(anim, r)) return
        val c = cur
        when (cls) {
            CLASS_URGENT -> {
                if (c == null || c.cls != CLASS_URGENT) cur = Reaction(r, CLASS_URGENT, now, now + lengthMs)
                else if (!dropIfUrgentBusy) enqueue(anim, lengthMs, CLASS_URGENT)
            }
            else -> {
                if (c == null && queue.isEmpty()) cur = Reaction(r, CLASS_EVENT, now, now + lengthMs)
                else enqueue(anim, lengthMs, CLASS_EVENT)
            }
        }
    }

    private fun enqueue(anim: PetAnim, lengthMs: Int, cls: Int) {
        for (p in queue) if (p.anim == anim) return
        if (queue.size >= MAX_QUEUE) queue.removeFirst()
        queue.addLast(Pending(anim, lengthMs, cls))
    }

    private fun handle(e: PetEvent, world: PetWorld, now: Long, a: Set<PetAnim>) {
        when (e) {
            PetEvent.Failed -> react(PetAnim.STARTLED, CLASS_URGENT, PetAnim.STARTLED.durationMs, now, a)
            PetEvent.MemorySaved -> react(PetAnim.WRITE, CLASS_EVENT, PetAnim.WRITE.durationMs, now, a)
            PetEvent.Forged -> react(PetAnim.HAPPY, CLASS_EVENT, PetAnim.HAPPY.durationMs, now, a)
            PetEvent.Approval -> react(PetAnim.WAVE, CLASS_EVENT, PetAnim.WAVE.durationMs, now, a)
            PetEvent.Overdrive -> {
                // the celebration wins over everything that plays or waits for an urgent turn (the tap hops before it)
                queue.removeAll { it.cls == CLASS_URGENT }
                cur = Reaction(resolve(PetAnim.DANCE, a), CLASS_URGENT, now, now + OVERDRIVE_MS)
            }
            is PetEvent.Tap -> {
                val raw = baseOf(world, now)
                if (raw == PetAnim.SLEEP) {
                    if (sleepsBecauseOfNight(world)) {
                        // wake up for a while: the change of base below plays the stretch
                        awakeUntil = now + AWAKE_GRACE_MS
                    } else {
                        react(PetAnim.STRETCH, CLASS_URGENT, PetAnim.STRETCH.durationMs, now, a, dropIfUrgentBusy = true)
                    }
                } else if (raw != PetAnim.CARRY) {
                    val an = if (e.count % 2 == 0) PetAnim.WAVE else PetAnim.HAPPY
                    react(an, CLASS_URGENT, an.durationMs, now, a, dropIfUrgentBusy = true)
                }
            }
        }
    }

    private fun onBaseChange(old: PetAnim?, raw: PetAnim, now: Long, a: Set<PetAnim>) {
        // a change of base that looks the same (READ missing, so it is drawn as THINK) must not restart the loop
        val sameLook = old != null && resolve(old, a) == resolve(raw, a)
        if (!sameLook) baseStart = now
        idleStart = now
        scheduleIdle()
        val c = cur
        if (!sameLook && c != null && c.cls == CLASS_IDLE) cur = null
        lastBase = raw
        if (old == null) return
        if (raw == PetAnim.SLEEP) {
            react(PetAnim.YAWN, CLASS_EVENT, PetAnim.YAWN.durationMs, now, a)
        } else if (old == PetAnim.SLEEP) {
            react(PetAnim.STRETCH, CLASS_EVENT, PetAnim.STRETCH.durationMs, now, a)
        }
        if (raw == PetAnim.ERROR) react(PetAnim.STARTLED, CLASS_URGENT, PetAnim.STARTLED.durationMs, now, a)
    }

    // ---- the base pose ------------------------------------------------------------------------------------------

    /** True when the base is SLEEP only because of the deep night (the fire is burning). */
    private fun sleepsBecauseOfNight(world: PetWorld): Boolean =
        world.fire == PetFire.BLAZING && !world.celebrating && world.deepNight && !world.busy && !world.listening

    /** The base animation from the world, before availability is considered; first match wins. */
    private fun baseOf(world: PetWorld, now: Long): PetAnim = when {
        world.fire == PetFire.COLD -> PetAnim.SLEEP
        world.fire == PetFire.SMOKE -> PetAnim.ERROR
        world.fire == PetFire.KINDLING -> PetAnim.THINK
        world.celebrating -> PetAnim.DANCE
        world.deepNight && !world.busy && !world.listening && now >= awakeUntil -> PetAnim.SLEEP
        world.listening -> PetAnim.LISTEN
        world.busy -> if (world.toolRunning) PetAnim.READ else PetAnim.THINK
        world.waitingResult -> PetAnim.CARRY
        world.rain > 0 -> PetAnim.SHELTER
        else -> PetAnim.IDLE
    }

    /** Follows the fallback chain until the animation is available (IDLE always is). */
    private fun resolve(anim: PetAnim, a: Set<PetAnim>): PetAnim {
        var x = anim
        while (x != PetAnim.IDLE && x !in a) x = x.fallback ?: PetAnim.IDLE
        return x
    }

    /**
     * The start time to put in the pose. A looping animation keeps its start. A one-shot that has to last longer
     * than one run (a warm-up played twice, a dance replaced by the happy hop) starts again every run, so the layer,
     * which stays on the last frame of a one-shot, sees it play again.
     */
    private fun startOf(anim: PetAnim, start: Long, lengthMs: Long, now: Long): Long {
        if (anim.loops || lengthMs <= anim.durationMs) return start
        val runs = (now - start).coerceAtLeast(0L) / anim.durationMs
        return start + runs * anim.durationMs
    }

    private fun emit(pose: PetPose): PetPose {
        val last = lastPose
        if (last != null && last == pose) return last
        lastPose = pose
        return pose
    }

    // ---- idle actions -------------------------------------------------------------------------------------------

    /** The next action is due [GAP_CYCLES_SHORT] (two in three) or [GAP_CYCLES_LONG] idle cycles after [idleStart]. */
    private fun scheduleIdle() {
        val cycles = if (rng.nextInt(3) < 2) GAP_CYCLES_SHORT else GAP_CYCLES_LONG
        nextIdleAt = idleStart + cycles.toLong() * IDLE_CYCLE_MS
    }

    /** Skips ahead on the cycle grid without playing anything (not lively, or the clock jumped). */
    private fun rebaseIdle(now: Long) {
        idleStart = nextIdleAt + (now - nextIdleAt) / IDLE_CYCLE_MS * IDLE_CYCLE_MS
        scheduleIdle()
    }

    private fun startIdle(world: PetWorld, a: Set<PetAnim>) {
        val start = nextIdleAt
        val weights = idleWeights(world, lastIdle, a)
        var total = 0
        for (w in weights.values) total += w
        if (total <= 0) {
            idleStart = start
            scheduleIdle()
            return
        }
        val pick = pickWeighted(weights, rng.nextInt(total)) ?: PetAnim.BLINK
        lastIdle = pick
        val plays = if (pick == PetAnim.WARM) 1 + rng.nextInt(2) else 1
        cur = Reaction(pick, CLASS_IDLE, start, start + pick.durationMs.toLong() * plays)
    }

    // ---- the shadow ---------------------------------------------------------------------------------------------

    /** Level 0..3 from the fire level with hysteresis, at most one step per [SHADOW_MIN_MS]. */
    private fun shadowFor(now: Long, f: Float): Int {
        if (!shadowInit) {
            var l = 0
            while (l < SHADOW_UP.size && f >= SHADOW_UP[l]) l++
            shadowLevel = l
            shadowChangedAt = now - SHADOW_MIN_MS
            shadowInit = true
            return l
        }
        if (now - shadowChangedAt >= SHADOW_MIN_MS) {
            if (shadowLevel < SHADOW_UP.size && f >= SHADOW_UP[shadowLevel]) {
                shadowLevel++
                shadowChangedAt = now
            } else if (shadowLevel > 0 && f < SHADOW_DOWN[shadowLevel - 1]) {
                shadowLevel--
                shadowChangedAt = now
            }
        }
        return shadowLevel
    }

    companion object {
        const val IDLE_CYCLE_MS = 2860
        const val GAP_CYCLES_SHORT = 2
        const val GAP_CYCLES_LONG = 3
        const val MAX_QUEUE = 3
        const val OVERDRIVE_MS = 4200
        const val AWAKE_GRACE_MS = 20_000

        private const val EVENT_INBOX_MAX = 8
        private const val CLASS_IDLE = 0
        private const val CLASS_EVENT = 1
        private const val CLASS_URGENT = 2
        private const val SHADOW_MIN_MS = 500L
        private val SHADOW_UP = floatArrayOf(0.15f, 0.45f, 0.80f)
        private val SHADOW_DOWN = floatArrayOf(0.10f, 0.38f, 0.72f)

        private fun withClassic(set: Set<PetAnim>): Set<PetAnim> {
            val out = LinkedHashSet<PetAnim>(PetSet.CLASSIC)
            out.addAll(set)
            return out
        }

        /**
         * The weight of every idle action (all nine keys, in a fixed order; 0 = not possible now). Integer maths:
         * the multipliers compound as one fraction and the result is truncated once. An action that is unavailable or
         * equal to [last] has weight 0 (never the same twice in a row).
         */
        internal fun idleWeights(world: PetWorld, last: PetAnim?, available: Set<PetAnim>): Map<PetAnim, Int> {
            val out = LinkedHashMap<PetAnim, Int>()
            val dark = world.fireLevel < 0.5f
            val snowing = world.snow > 0

            fun weigh(anim: PetAnim, base: Int, num: Int, den: Int): Int =
                if (anim !in available || anim == last) 0 else base * num / den

            out[PetAnim.BLINK] = weigh(PetAnim.BLINK, 28, 1, 1)
            out[PetAnim.TAIL] = weigh(PetAnim.TAIL, 16, 1, 1)
            out[PetAnim.WARM] = weigh(PetAnim.WARM, 12, 1, if (dark) 2 else 1)

            var yn = 1
            var yd = 1
            if (world.night) yn *= 2
            if (world.day) yd *= 2
            if (world.morning) yn *= 2
            out[PetAnim.YAWN] = weigh(PetAnim.YAWN, 6, yn, yd)

            out[PetAnim.STRETCH] = weigh(PetAnim.STRETCH, 5, if (world.morning) 3 else 1, 1)

            val fireflies = if (world.day || snowing) 0 else 8
            out[PetAnim.FIREFLY] = weigh(PetAnim.FIREFLY, fireflies, if (world.night) 2 else 1, 1)

            out[PetAnim.POKE] = weigh(PetAnim.POKE, if (snowing) 0 else 7, if (dark) 2 else 1, 1)
            out[PetAnim.HAPPY] = weigh(PetAnim.HAPPY, 6, 1, 1)
            out[PetAnim.SNOW] = weigh(PetAnim.SNOW, if (snowing) 40 else 0, 1, 1)
            return out
        }

        /** Picks by a roll in `0 until sum(weights)`; null when the roll is outside (or all weights are 0). */
        internal fun pickWeighted(weights: Map<PetAnim, Int>, roll: Int): PetAnim? {
            var r = roll
            for ((anim, w) in weights) {
                if (w <= 0) continue
                if (r < w) return anim
                r -= w
            }
            return null
        }
    }
}
