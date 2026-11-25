package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.data.WeatherCondition
import dev.pocketagent.app.data.WeatherPoint
import kotlin.math.abs
import kotlin.math.sin

/*
 * The look of the weather as the scene needs it: three small enums, the quantised wind and the few numbers other
 * layers read (star and body visibility, the rain level). Pure Kotlin: this folder is scanned for Android imports.
 */

enum class CloudCover { NONE, LIGHT, MEDIUM, HEAVY }

enum class Precip { NONE, RAIN_LIGHT, RAIN_HEAVY, SLEET, SNOW_LIGHT, SNOW_HEAVY }

enum class FogLevel { NONE, LIGHT, DENSE }

/**
 * What the sky shows. [wind] runs from -1 to 1 in steps of 0.1; positive blows to the right of the screen (wind
 * from the west). [flash] is 0 by the clock, 1 forced bright, 2 forced dim; only a debug pin sets it.
 * The clear look draws nothing.
 */
data class WeatherLook(
    val cloud: CloudCover = CloudCover.NONE,
    val precip: Precip = Precip.NONE,
    val fog: FogLevel = FogLevel.NONE,
    val wind: Float = 0f,
    val thunder: Boolean = false,
    val flash: Int = 0,
) {
    /** True when no weather layer has anything to draw (wind alone draws nothing in the weather layers). */
    val isClear: Boolean
        get() = cloud == CloudCover.NONE && precip == Precip.NONE && fog == FogLevel.NONE && !thunder && flash == 0

    /** True while something falls. */
    val wet: Boolean get() = precip != Precip.NONE

    /** 0 none, 1 light, 2 heavy; rain and sleet count, snow does not. */
    val rainLevel: Int
        get() = when (precip) {
            Precip.RAIN_LIGHT, Precip.SLEET -> 1
            Precip.RAIN_HEAVY -> 2
            else -> 0
        }

    /** How much of the stars shows through: the cloud cover times the fog. */
    fun starVisibility(): Float {
        val c = when (cloud) {
            CloudCover.NONE -> 1.0f
            CloudCover.LIGHT -> 0.75f
            CloudCover.MEDIUM -> 0.4f
            CloudCover.HEAVY -> 0.1f
        }
        val f = when (fog) {
            FogLevel.NONE -> 1.0f
            FogLevel.LIGHT -> 0.85f
            FogLevel.DENSE -> 0.5f
        }
        return c * f
    }

    /** Sun and moon: a quarter of them always shows, the rest follows [starVisibility]. */
    fun bodyVisibility(): Float = 0.25f + 0.75f * starVisibility()

    companion object {
        val CLEAR: WeatherLook = WeatherLook()
    }
}

/** The look of one forecast point (or pin). */
fun WeatherPoint.toLook(): WeatherLook {
    val fromPercent = when {
        cloudPercent < 20 -> CloudCover.NONE
        cloudPercent < 50 -> CloudCover.LIGHT
        cloudPercent < 85 -> CloudCover.MEDIUM
        else -> CloudCover.HEAVY
    }
    val precip = when (condition) {
        WeatherCondition.LIGHT_RAIN -> Precip.RAIN_LIGHT
        WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN -> Precip.RAIN_HEAVY
        WeatherCondition.SLEET -> Precip.SLEET
        WeatherCondition.LIGHT_SNOW -> Precip.SNOW_LIGHT
        WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW -> Precip.SNOW_HEAVY
        else -> Precip.NONE
    }
    var cloud = fromPercent
    if (precip != Precip.NONE && cloud.ordinal < CloudCover.MEDIUM.ordinal) cloud = CloudCover.MEDIUM
    if (precip == Precip.RAIN_HEAVY || precip == Precip.SNOW_HEAVY || thunder) cloud = CloudCover.HEAVY
    val fog = if (condition == WeatherCondition.FOG) {
        if (windMs < 2.0) FogLevel.DENSE else FogLevel.LIGHT
    } else {
        FogLevel.NONE
    }
    return WeatherLook(
        cloud = cloud,
        precip = precip,
        fog = fog,
        wind = windOf(windMs, windFromDeg),
        thunder = thunder,
        flash = flash,
    )
}

/** -1..1 in steps of 0.1: strength from the speed, sign and a little more or less from the direction. */
private fun windOf(windMs: Double, fromDeg: Double): Float {
    val m = ((windMs - 1.0) / 13.0).coerceIn(0.0, 1.0)
    // rounded to 9 decimals so that a wind from exactly south or north is not decided by floating point noise
    val east = Math.round(-sin(Math.toRadians(fromDeg)) * 1e9) / 1e9
    val strength = m * (0.5 + 0.5 * abs(east))
    val tenths = Math.round(strength * 10.0).toInt()
    if (tenths == 0) return 0f
    return ((if (east >= 0.0) tenths else -tenths) / 10.0).toFloat()
}

/** What wind does to sparks, flakes, grass and flame. */
object WeatherWind {
    /** Sideways drift in stage cells of a spark or flake at the age fraction [u] (0 to 1). */
    fun driftCells(wind: Float, u: Float, maxCells: Float = 6f): Float = wind * maxCells * u

    /** Whole stage pixels the grass leans, -2..2. */
    fun grassLeanPx(wind: Float): Int = Math.round(wind * 2f).coerceIn(-2, 2)

    /** The flame's lean, -1..1. */
    fun flameLean(wind: Float): Float = wind.coerceIn(-1f, 1f)
}

/**
 * The scene's geometry as the weather layers need it (built by the scene from its own geometry). The two ridge
 * tops are the real ones, not guessed from [groundTop].
 */
class WeatherGeometry(
    val width: Int,
    val height: Int,
    val groundTop: Int,
    val hearthX: Int,
    val hearthBase: Int,
    val farRidgeTop: Int = groundTop - 38,
    val nearRidgeTop: Int = groundTop - 14,
)

/** What the layers need of the sky's colours: [daylight] 0 night to 1 day, [skyBottom] the opaque ARGB at the horizon. */
class WeatherColors(val daylight: Float, val skyBottom: Int)
