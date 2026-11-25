package dev.pocketagent.app.ui.pixel.stage

import java.time.LocalDate
import kotlin.math.abs

/**
 * The season of the scene: which of the four it is on a date at a latitude, and the switches of the look. The
 * overlays themselves (snow, flowers, tufts, leaves) are drawn by the renderer in a later part and read these
 * switches; this object holds only the pure rule.
 */
object Seasons {
    /** Look knobs: false removes the procedural overlay of that season (the plain ground stays). */
    const val SPRING_ON = true
    const val SUMMER_ON = true
    const val AUTUMN_ON = true
    const val WINTER_ON = true

    /** Whether the overlay of [season] may be drawn at all (the four switches above). */
    fun enabled(season: Season): Boolean = when (season) {
        Season.SPRING -> SPRING_ON
        Season.SUMMER -> SUMMER_ON
        Season.AUTUMN -> AUTUMN_ON
        Season.WINTER -> WINTER_ON
    }

    /**
     * The season on [date] at latitude [latDeg]. OFF gives null; a fixed setting wins; AUTO follows the calendar months
     * (north: March to May spring, June to August summer, September to November autumn, December to February winter;
     * south: shifted by six months); within 15 degrees of the equator it is summer all year.
     */
    fun seasonFor(date: LocalDate, latDeg: Double, setting: SeasonSetting): Season? {
        when (setting) {
            SeasonSetting.OFF -> return null
            SeasonSetting.SPRING -> return Season.SPRING
            SeasonSetting.SUMMER -> return Season.SUMMER
            SeasonSetting.AUTUMN -> return Season.AUTUMN
            SeasonSetting.WINTER -> return Season.WINTER
            SeasonSetting.AUTO -> Unit
        }
        if (abs(latDeg) < 15.0) return Season.SUMMER
        val m = date.monthValue
        val north = when (m) {
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            9, 10, 11 -> Season.AUTUMN
            else -> Season.WINTER
        }
        if (latDeg >= 0.0) return north
        return when (north) {
            Season.SPRING -> Season.AUTUMN
            Season.SUMMER -> Season.WINTER
            Season.AUTUMN -> Season.SPRING
            Season.WINTER -> Season.SUMMER
        }
    }
}
