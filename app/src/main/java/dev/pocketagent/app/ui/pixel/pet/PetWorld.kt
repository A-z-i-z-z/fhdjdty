package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.stage.Precip
import dev.pocketagent.app.ui.pixel.stage.WeatherLook

/** The fire as Pêt sees it: one to one with the app's hearth state. */
enum class PetFire { COLD, KINDLING, BLAZING, SMOKE }

/**
 * What Pêt is told about the moment, once per frame. Time and place come in as numbers so the brain stays pure:
 * [sunAltitudeDeg] is the height of the sun in the DRAWN sky, [localMinutes] the minute of the day in the phone's
 * own time zone.
 */
data class PetWorld(
    val fire: PetFire = PetFire.BLAZING,
    val fireLevel: Float = 1f,
    val sunAltitudeDeg: Float = -30f,
    val localMinutes: Int = 21 * 60,
    val rain: Int = 0,
    val snow: Int = 0,
    val busy: Boolean = false,
    val toolRunning: Boolean = false,
    val waitingResult: Boolean = false,
    val celebrating: Boolean = false,
    val listening: Boolean = false,
    val animate: Boolean = true,
    val lively: Boolean = true,
) {
    /** The sun is at least 12 degrees below the horizon and it is between 23:00 and 05:00. */
    val deepNight: Boolean
        get() = sunAltitudeDeg <= DEEP_NIGHT_SUN_DEG && (localMinutes >= DEEP_NIGHT_FROM_MIN || localMinutes < DEEP_NIGHT_UNTIL_MIN)

    val night: Boolean get() = sunAltitudeDeg < NIGHT_SUN_DEG
    val day: Boolean get() = sunAltitudeDeg > DAY_SUN_DEG

    /** Between 05:00 and 10:00 (local). */
    val morning: Boolean get() = localMinutes >= MORNING_FROM_MIN && localMinutes < MORNING_UNTIL_MIN

    companion object {
        const val DEEP_NIGHT_SUN_DEG = -12f
        const val DEEP_NIGHT_FROM_MIN = 1380
        const val DEEP_NIGHT_UNTIL_MIN = 300
        const val NIGHT_SUN_DEG = -6f
        const val DAY_SUN_DEG = 6f
        const val MORNING_FROM_MIN = 300
        const val MORNING_UNTIL_MIN = 600

        /**
         * The fire level the scene itself uses for each hearth state: COLD 0, KINDLING 0.18 + 0.72 * progress
         * (0.5 when unknown), BLAZING 1, SMOKE 0.2.
         */
        fun defaultFireLevel(fire: PetFire, progress: Float?): Float = when (fire) {
            PetFire.COLD -> 0f
            PetFire.KINDLING -> 0.18f + 0.72f * (progress ?: 0.5f).coerceIn(0f, 1f)
            PetFire.BLAZING -> 1f
            PetFire.SMOKE -> 0.2f
        }
    }
}

/**
 * What the layer draws: [anim] (never one that is unavailable: the brain resolves first), [startMs] the scene time at
 * which the animation began (the frame is the sprite's frame at `t - startMs`), [shadow] -1 none or 0 to 3 the length
 * level of the shadow sprite, [holdFrame] >= 0 draws that frame only (animation off, debug pin).
 */
data class PetPose(val anim: PetAnim, val startMs: Long, val shadow: Int = 1, val holdFrame: Int = -1)

/** Things that happen in the app that Pêt reacts to; `Tap` carries the running tap count. */
sealed interface PetEvent {
    data object MemorySaved : PetEvent
    data object Failed : PetEvent
    data object Forged : PetEvent
    data object Approval : PetEvent
    data class Tap(val count: Int) : PetEvent
    data object Overdrive : PetEvent
}

fun petFireOf(h: HearthState): PetFire = when (h) {
    HearthState.COLD -> PetFire.COLD
    HearthState.KINDLING -> PetFire.KINDLING
    HearthState.BLAZING -> PetFire.BLAZING
    HearthState.SMOKE -> PetFire.SMOKE
}

/** 0 none, 1 light, 2 heavy (sleet counts as rain). */
fun petRainOf(look: WeatherLook): Int = look.rainLevel

/** 0 none, 1 light snow, 2 heavy snow. */
fun petSnowOf(look: WeatherLook): Int = when (look.precip) {
    Precip.SNOW_LIGHT -> 1
    Precip.SNOW_HEAVY -> 2
    else -> 0
}
