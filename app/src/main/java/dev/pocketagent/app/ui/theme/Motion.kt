package dev.pocketagent.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// Motion tokens of docs/design-language.md section 2.4. After Wave 0 no screen holds a raw tween: it asks
// the MotionScheme (Pocket.motion) for a named spec, and the scheme already knows the effective level.

/** Durations in milliseconds. */
object Dur {
    const val TAP = 90
    const val FAST = 150
    const val BASE = 220
    const val SLOW = 320
    const val SCENE = 480
    const val EPIC = 900
}

object Ease {
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** [n] equal jumps: a fraction f gives floor(f * n) / n, and 1 from f = 1 on. Pixel movement. */
    fun steps(n: Int): Easing {
        val count = max(1, n)
        return Easing { f ->
            when {
                f.isNaN() || f <= 0f -> 0f
                f >= 1f -> 1f
                else -> floor(f * count) / count
            }
        }
    }
}

/** Layout physics. Raw and level-blind: screens use [MotionScheme], which also handles reduced and off. */
object Springs {
    const val SNAPPY_STIFFNESS = 1400f
    const val SNAPPY_DAMPING = 0.90f
    const val GLIDE_STIFFNESS = 380f
    const val GLIDE_DAMPING = 0.86f
    const val BOUNCY_STIFFNESS = 600f
    const val BOUNCY_DAMPING = 0.55f

    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = SNAPPY_DAMPING, stiffness = SNAPPY_STIFFNESS)
    fun <T> glide(): SpringSpec<T> = spring(dampingRatio = GLIDE_DAMPING, stiffness = GLIDE_STIFFNESS)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = BOUNCY_DAMPING, stiffness = BOUNCY_STIFFNESS)
}

/** Periods of the loops that exist, in milliseconds. */
object Loop {
    const val GLOW = 1700
    const val TYPING = 1000
    const val CARET = 520
    const val EMBER = 700
    const val SLIDE = 1100
    const val SHIMMER = 1400
    const val EMBER_LINE = 1200
}

/** The two durations above EPIC that exist today. */
object Flourish {
    const val STOKE = 1200
    const val SHOWER = 2400
}

/**
 * The animation level in force: the lowest of the app setting, the system animator scale (zero or not a
 * number means OFF) and battery saver (at most REDUCED). Order: FULL above REDUCED above OFF.
 */
fun effectiveAnimLevel(setting: AnimLevel, animatorScale: Float, powerSave: Boolean): AnimLevel {
    if (animatorScale.isNaN() || animatorScale <= 0f) return AnimLevel.OFF
    if (powerSave && setting == AnimLevel.FULL) return AnimLevel.REDUCED
    return setting
}

/**
 * What a screen asks for. OFF snaps everything; REDUCED clamps tweens to 150 ms and turns springs and the
 * stepped pop into a plain 150 ms tween; FULL plays the real specs. Call sites never branch on the level
 * to pick a spec.
 */
@Immutable
class MotionScheme(val level: AnimLevel) {
    private fun <T> timed(ms: Int, easing: Easing): FiniteAnimationSpec<T> = when (level) {
        AnimLevel.OFF -> snap()
        AnimLevel.REDUCED -> tween(minOf(ms, REDUCED_MAX_MS), easing = easing)
        AnimLevel.FULL -> tween(ms, easing = easing)
    }

    private fun <T> physics(full: SpringSpec<T>): FiniteAnimationSpec<T> = when (level) {
        AnimLevel.OFF -> snap()
        AnimLevel.REDUCED -> tween(Dur.FAST, easing = Ease.standard)
        AnimLevel.FULL -> full
    }

    fun <T> tap(): FiniteAnimationSpec<T> = timed(Dur.TAP, Ease.standard)
    fun <T> fast(): FiniteAnimationSpec<T> = timed(Dur.FAST, Ease.standard)
    fun <T> base(): FiniteAnimationSpec<T> = timed(Dur.BASE, Ease.standard)
    fun <T> slow(): FiniteAnimationSpec<T> = timed(Dur.SLOW, Ease.standard)
    fun <T> scene(): FiniteAnimationSpec<T> = timed(Dur.SCENE, Ease.standard)

    fun <T> glide(): FiniteAnimationSpec<T> = physics(Springs.glide())
    fun <T> snappy(): FiniteAnimationSpec<T> = physics(Springs.snappy())
    fun <T> bouncy(): FiniteAnimationSpec<T> = physics(Springs.bouncy())

    /** Pattern 2: a small thing appears in three steps. */
    fun <T> pop(): FiniteAnimationSpec<T> = when (level) {
        AnimLevel.OFF -> snap()
        AnimLevel.REDUCED -> tween(Dur.FAST, easing = Ease.standard)
        AnimLevel.FULL -> tween(Dur.FAST, easing = Ease.steps(3))
    }

    /** Only with a Dur or Flourish constant. */
    fun <T> custom(durationMs: Int, easing: Easing = Ease.standard): FiniteAnimationSpec<T> = timed(durationMs, easing)

    /**
     * A loop of [periodMs]. REDUCED runs it 1.8 times slower. At OFF it returns the FULL spec: callers that
     * must not start a loop at OFF branch on [loops], as TypingIndicator does.
     */
    fun <T> loop(
        periodMs: Int,
        repeatMode: RepeatMode = RepeatMode.Restart,
        easing: Easing = LinearEasing,
    ): InfiniteRepeatableSpec<T> {
        val period = if (level == AnimLevel.REDUCED) (periodMs * REDUCED_LOOP_FACTOR).roundToInt() else periodMs
        return infiniteRepeatable(tween(period, easing = easing), repeatMode)
    }

    /** False at OFF: no loop should run. */
    val loops: Boolean get() = level != AnimLevel.OFF
    val particles: Boolean get() = level == AnimLevel.FULL
    val parallax: Boolean get() = level == AnimLevel.FULL
    val shaders: Boolean get() = level == AnimLevel.FULL

    /** Frames per second of the living scene. */
    val sceneFps: Int
        get() = when (level) {
            AnimLevel.FULL -> 30
            AnimLevel.REDUCED -> 10
            AnimLevel.OFF -> 0
        }

    private companion object {
        const val REDUCED_MAX_MS = 150
        const val REDUCED_LOOP_FACTOR = 1.8f
    }
}

/** The effective level: what [Pocket.anim] returns. PocketTheme provides it. */
val LocalAnimLevel: ProvidableCompositionLocal<AnimLevel> = staticCompositionLocalOf { AnimLevel.FULL }
val LocalMotion: ProvidableCompositionLocal<MotionScheme> = staticCompositionLocalOf { MotionScheme(AnimLevel.FULL) }

/** Read it in the composable body, never inside a lambda: val motion = Pocket.motion. */
val Pocket.motion: MotionScheme
    @Composable get() = LocalMotion.current

/**
 * Like Modifier.offset, with both axes rounded to whole multiples of [pixel] (in device pixels), so a
 * sprite never sits on half a pixel while it travels.
 */
fun Modifier.pixelOffset(pixel: Dp = 2.dp, offset: Density.() -> Offset): Modifier =
    this.offset {
        val unit = max(1, pixel.roundToPx())
        val o = offset.invoke(this)
        IntOffset(snapToDesignPx(o.x, unit).roundToInt(), snapToDesignPx(o.y, unit).roundToInt())
    }
