package dev.pocketagent.app.ui.pixel.stage

import java.time.ZoneId
import java.util.Locale

/** What the home sky does. NIGHT is today's picture (no frame at all), FOLLOW the real sky, DAY a fixed bright noon. */
enum class SkyMode { FOLLOW, NIGHT, DAY }

/** The seven looks of the sky. The order is the order of a day that starts in the dark. */
enum class SkyMoment { NIGHT, DAWN, SUNRISE, DAY, GOLDEN, SUNSET, DUSK }

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/** What the user chose for the seasons: follow the date, a fixed season, or none. OFF draws no overlay. */
enum class SeasonSetting { AUTO, SPRING, SUMMER, AUTUMN, WINTER, OFF }

/** Where a body stands: geometric (no refraction), the azimuth counted from north, clockwise. */
class BodyPos(val altDeg: Float, val azDeg: Float)

/**
 * Everything the scene needs to know about the sky at one instant and one place. Immutable; one instance per
 * rebuild, so equality is identity (the renderer's stamp compares by reference).
 *
 * [mode] is FOLLOW or DAY (the builder never returns a frame for NIGHT). The colours are already blended:
 * [skyTop], [skyBottom], [farRamp] and [lift] (0 night to 1 day, in steps of 1/16) are what the renderer uses
 * and what tints the ridges and the ground. [a], [b] and [blend] name the two moments the frame sits between
 * ([moment] is the nearer one).
 */
class SkyFrame(
    val mode: SkyMode,
    val epochMs: Long, val latDeg: Double, val lonDeg: Double, val lstDeg: Double,
    val sun: BodyPos, val moon: BodyPos,
    val moonPhase16: Int, // 0 new, 4 first quarter, 8 full, 12 last quarter
    val moonWaxing: Boolean, val moonIllum: Float,
    val moonRed: Float, // 0..1, the red moon of a lunar eclipse
    val moonLight: Float, // 0..1, how much moonlight lies on the meadow
    val evening: Boolean, val a: SkyMoment, val b: SkyMoment, val blend: Float, val moment: SkyMoment,
    val daylight: Float, // 0 night .. 1 day: smoothstep(-12, 6, sun altitude)
    val depressionDeg: Float, // max(0, -sun altitude)
    val skyTop: Int, val skyBottom: Int, val farRamp: IntArray, val lift: Float, val sunColor: Int,
    val season: Season?, val facingAz: Float,
    val meteorPeriodMs: Int, // 14000, 3500 on a shower night
    val ridgeFires: Int, // 0 none, 1 quiet (today's three), 2 festive
    val fireworks: Boolean, val moonGlow: Boolean, val sunGold: Boolean,
    val petHat: Boolean, // computed and tested; nobody draws it in Wave 1a
    val iconsDark: Boolean, // status-bar icons should be dark over skyTop: luma(skyTop) >= 0.40
    val dayIds: List<String>, val planets: List<PlanetPos>,
) {
    /** One line for the app log, e.g. `mode=FOLLOW moment=DAY a=DAY b=DAY blend=0.00 sun=49.2/177.2 moon=8/0.99 lst=247.4 season=SPRING days=newroz-ridge,newroz`. */
    fun debugLine(): String {
        val seasonText = season?.name ?: "none"
        val days = if (dayIds.isEmpty()) "none" else dayIds.joinToString(",")
        return String.format(
            Locale.ROOT, "mode=%s moment=%s a=%s b=%s blend=%.2f sun=%.1f/%.1f moon=%d/%.2f lst=%.1f season=%s days=%s",
            mode.name, moment.name, a.name, b.name, blend, sun.altDeg, sun.azDeg, moonPhase16, moonIllum, lstDeg, seasonText, days,
        )
    }
}

/**
 * What [SkyFrames.build] needs. [night] is today's hearth palette (the NIGHT moment IS this palette), [accent]
 * the theme accent (it biases every tint a little), [variant] the theme name (kept for later looks), [zone] the
 * phone's own zone (the date, the season and the days come from it, never from the place).
 */
class SkyInputs(
    val epochMs: Long, val latDeg: Double, val lonDeg: Double, val zone: ZoneId, val mode: SkyMode,
    val night: ScenePalette,
    val accent: Int, val variant: String, val blackNight: Boolean,
    val lists: Set<String>, val userDays: List<UserDay>, val firstLaunchEpochDay: Long?, val seasons: SeasonSetting,
)
