package dev.pocketagent.app.ui.theme

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The effective animation level rule and what each level means for the named specs. */
class MotionTest {
    private fun duration(spec: Any?): Int {
        assertTrue("expected a tween, got $spec", spec is TweenSpec<*>)
        return (spec as TweenSpec<*>).durationMillis
    }

    @Test
    fun durationsAreTheTable() {
        assertEquals(90, Dur.TAP)
        assertEquals(150, Dur.FAST)
        assertEquals(220, Dur.BASE)
        assertEquals(320, Dur.SLOW)
        assertEquals(480, Dur.SCENE)
        assertEquals(900, Dur.EPIC)
        assertEquals(1700, Loop.GLOW)
        assertEquals(1000, Loop.TYPING)
        assertEquals(520, Loop.CARET)
        assertEquals(700, Loop.EMBER)
        assertEquals(1100, Loop.SLIDE)
        assertEquals(1400, Loop.SHIMMER)
        assertEquals(1200, Loop.EMBER_LINE)
        assertEquals(1200, Flourish.STOKE)
        assertEquals(2400, Flourish.SHOWER)
        val full = MotionScheme(AnimLevel.FULL)
        assertEquals(90, duration(full.tap<Float>()))
        assertEquals(150, duration(full.fast<Float>()))
        assertEquals(220, duration(full.base<Float>()))
        assertEquals(320, duration(full.slow<Float>()))
        assertEquals(480, duration(full.scene<Float>()))
        assertEquals(900, duration(full.custom<Float>(Dur.EPIC)))
        assertEquals(150, duration(full.pop<Float>()))
        assertTrue(full.glide<Float>() is SpringSpec<*>)
        assertTrue(full.snappy<Float>() is SpringSpec<*>)
        assertTrue(full.bouncy<Float>() is SpringSpec<*>)
    }

    @Test
    fun stepsEasing() {
        val e = Ease.steps(3)
        assertEquals(0f, e.transform(0f), 1e-6f)
        assertEquals(1f / 3f, e.transform(0.34f), 1e-6f)
        assertEquals(2f / 3f, e.transform(0.67f), 1e-6f)
        assertEquals(1f, e.transform(1f), 1e-6f)
        assertEquals(1f, e.transform(1.5f), 1e-6f)
        assertEquals(0f, e.transform(0.2f), 1e-6f)
    }

    @Test
    fun effectiveLevelTruthTable() {
        fun rank(l: AnimLevel) = when (l) {
            AnimLevel.OFF -> 0
            AnimLevel.REDUCED -> 1
            AnimLevel.FULL -> 2
        }
        val levels = listOf(AnimLevel.FULL, AnimLevel.REDUCED, AnimLevel.OFF)
        for (setting in levels) {
            for (scale in listOf(0f, 0.5f, 1f, Float.NaN, -1f)) {
                for (save in listOf(false, true)) {
                    var r = rank(setting)
                    if (scale.isNaN() || scale <= 0f) r = 0
                    if (save) r = minOf(r, 1)
                    val want = levels.first { rank(it) == r }
                    assertEquals("$setting scale $scale save $save", want, effectiveAnimLevel(setting, scale, save))
                }
            }
        }
    }

    @Test
    fun offSnapsEverything() {
        val off = MotionScheme(AnimLevel.OFF)
        val specs = listOf<Any>(
            off.tap<Float>(), off.fast<Float>(), off.base<Float>(), off.slow<Float>(), off.scene<Float>(),
            off.glide<Float>(), off.snappy<Float>(), off.bouncy<Float>(), off.pop<Float>(),
            off.custom<Float>(Flourish.SHOWER),
        )
        for (s in specs) assertTrue("expected snap, got $s", s is SnapSpec<*>)
    }

    @Test
    fun reducedClampsTo150() {
        val r = MotionScheme(AnimLevel.REDUCED)
        assertEquals(90, duration(r.tap<Float>()))
        assertEquals(150, duration(r.fast<Float>()))
        assertEquals(150, duration(r.base<Float>()))
        assertEquals(150, duration(r.slow<Float>()))
        assertEquals(150, duration(r.scene<Float>()))
        assertEquals(150, duration(r.custom<Float>(Flourish.SHOWER)))
        assertEquals(150, duration(r.glide<Float>()))
        assertEquals(150, duration(r.snappy<Float>()))
        assertEquals(150, duration(r.bouncy<Float>()))
        assertEquals(150, duration(r.pop<Float>()))
    }

    @Test
    fun loopScalesAtReduced() {
        fun period(level: AnimLevel, ms: Int): Int {
            val spec: InfiniteRepeatableSpec<Float> = MotionScheme(level).loop(ms)
            return duration(spec.animation)
        }
        assertEquals(1000, period(AnimLevel.FULL, 1000))
        assertEquals(1800, period(AnimLevel.REDUCED, 1000))
        // OFF returns the FULL spec: callers branch on loops
        assertEquals(1000, period(AnimLevel.OFF, 1000))
        val back: InfiniteRepeatableSpec<Float> = MotionScheme(AnimLevel.FULL).loop(500, RepeatMode.Reverse)
        assertEquals(RepeatMode.Reverse, back.repeatMode)
    }

    @Test
    fun sceneFpsAndFlags() {
        val full = MotionScheme(AnimLevel.FULL)
        assertEquals(30, full.sceneFps)
        assertTrue(full.loops && full.particles && full.parallax && full.shaders)
        val reduced = MotionScheme(AnimLevel.REDUCED)
        assertEquals(10, reduced.sceneFps)
        assertTrue(reduced.loops)
        assertFalse(reduced.particles || reduced.parallax || reduced.shaders)
        val off = MotionScheme(AnimLevel.OFF)
        assertEquals(0, off.sceneFps)
        assertFalse(off.loops || off.particles || off.parallax || off.shaders)
    }
}
