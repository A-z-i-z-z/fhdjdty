package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** The sun's place: right ascension and declination of date, apparent ecliptic longitude, equation of time in minutes. */
class SunPos(val raDeg: Double, val decDeg: Double, val lambdaDeg: Double, val eotMinutes: Double)

/** The moon's place (geocentric, no parallax): right ascension and declination of date, ecliptic longitude and latitude. */
class MoonPos(val raDeg: Double, val decDeg: Double, val lambdaDeg: Double, val betaDeg: Double)

class AltAz(val altDeg: Double, val azDeg: Double)

/**
 * The clock of the sky: sidereal time, the sun, the moon, precession, altitude and azimuth, sunrise and sunset.
 * Pure maths on a UTC instant in milliseconds, in Double. Accuracy (stated, not hidden): the sun about 0.01 degree,
 * the moon about 0.3 degree (parallax of up to 1 degree ignored), stars from J2000 within 0.1 degree until 2035
 * (no proper motion, aberration, nutation or refraction), sunrise and sunset within a minute or two.
 */
object SkyClock {
    private const val DAY_MS = 86_400_000.0

    private fun rad(deg: Double): Double = Math.toRadians(deg)

    private fun deg(rad: Double): Double = Math.toDegrees(rad)

    private fun mod360(x: Double): Double = x - 360.0 * floor(x / 360.0)

    /** Julian day of the instant. */
    fun julianDay(epochMs: Long): Double = epochMs / DAY_MS + 2_440_587.5

    private fun centuries(epochMs: Long): Double = (julianDay(epochMs) - 2_451_545.0) / 36525.0

    /** Greenwich mean sidereal time in degrees, Meeus 12.4. */
    fun gmstDeg(epochMs: Long): Double {
        val d = julianDay(epochMs) - 2_451_545.0
        val t = d / 36525.0
        return mod360(280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38_710_000.0)
    }

    /** Local sidereal time in degrees; [lonDeg] east positive. */
    fun lstDeg(epochMs: Long, lonDeg: Double): Double = mod360(gmstDeg(epochMs) + lonDeg)

    /** The sun by the NOAA solar position formulas (about 0.01 degree), with the NOAA equation of time. */
    fun sun(epochMs: Long): SunPos {
        val jc = centuries(epochMs)
        val l0 = mod360(280.46646 + jc * (36000.76983 + jc * 0.0003032))
        val m = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val ecc = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val c = sin(rad(m)) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(rad(2.0 * m)) * (0.019993 - 0.000101 * jc) +
            sin(rad(3.0 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * jc
        val lambda = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val ra = mod360(deg(atan2(cos(rad(eps)) * sin(rad(lambda)), cos(rad(lambda)))))
        val dec = deg(asin(sin(rad(eps)) * sin(rad(lambda))))
        val y = tan(rad(eps / 2.0)).let { it * it }
        val eot = 4.0 * deg(
            y * sin(rad(2.0 * l0)) - 2.0 * ecc * sin(rad(m)) +
                4.0 * ecc * y * sin(rad(m)) * cos(rad(2.0 * l0)) -
                0.5 * y * y * sin(rad(4.0 * l0)) - 1.25 * ecc * ecc * sin(rad(2.0 * m)),
        )
        return SunPos(ra, dec, mod360(lambda), eot)
    }

    /**
     * The moon by the main terms of Meeus chapter 47 (13 terms of longitude, 8 of latitude, the factor E for the
     * terms with the sun's anomaly). About 0.3 degree; no nutation, no parallax.
     */
    fun moon(epochMs: Long): MoonPos {
        val t = centuries(epochMs)
        val lp = mod360(218.3164477 + 481267.88123421 * t)
        val d = mod360(297.8501921 + 445267.1114034 * t)
        val m = mod360(357.5291092 + 35999.0502909 * t)
        val mp = mod360(134.9633964 + 477198.8675055 * t)
        val f = mod360(93.2720950 + 483202.0175233 * t)
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        fun s(a: Double): Double = sin(rad(a))
        val lon = 6.288774 * s(mp) + 1.274027 * s(2.0 * d - mp) + 0.658314 * s(2.0 * d) + 0.213618 * s(2.0 * mp) -
            0.185116 * e * s(m) - 0.114332 * s(2.0 * f) + 0.058793 * s(2.0 * d - 2.0 * mp) +
            0.057066 * e * s(2.0 * d - m - mp) + 0.053322 * s(2.0 * d + mp) + 0.045758 * e * s(2.0 * d - m) -
            0.040923 * e * s(m - mp) - 0.034720 * s(d) - 0.030383 * e * s(m + mp)
        val lat = 5.128122 * s(f) + 0.280602 * s(mp + f) + 0.277693 * s(mp - f) + 0.173237 * s(2.0 * d - f) +
            0.055413 * s(2.0 * d - mp + f) + 0.046271 * s(2.0 * d - mp - f) + 0.032573 * s(2.0 * d + f) +
            0.017198 * s(2.0 * mp + f)
        val lambda = mod360(lp + lon)
        val eps = rad(23.4392911 - 0.0130042 * t)
        val sl = sin(rad(lambda))
        val cl = cos(rad(lambda))
        val sb = sin(rad(lat))
        val cb = cos(rad(lat))
        val ra = mod360(deg(atan2(sl * cos(eps) - tan(rad(lat)) * sin(eps), cl)))
        val dec = deg(asin(sb * cos(eps) + cb * sin(eps) * sl))
        return MoonPos(ra, dec, lambda, lat)
    }

    /** Moon minus sun in ecliptic longitude, 0 to 360: 0 new moon, 180 full moon. */
    fun elongationDeg(epochMs: Long): Double = mod360(moon(epochMs).lambdaDeg - sun(epochMs).lambdaDeg)

    /** Sixteen phases: 0 new, 4 first quarter, 8 full, 12 last quarter. */
    fun moonPhase16(elongationDeg: Double): Int = Math.floorMod(floor(elongationDeg / 22.5 + 0.5).toInt(), 16)

    /** The lit fraction of the disc, 0 to 1. */
    fun moonIllumination(elongationDeg: Double): Float = ((1.0 - cos(rad(elongationDeg))) / 2.0).toFloat()

    /**
     * Brings a J2000 position to the equinox of the date (Meeus 21.2). [out] gets right ascension (0 to 360) in
     * index 0 and declination in index 1, both in degrees. No proper motion, aberration or nutation.
     */
    fun precess(raDeg: Double, decDeg: Double, epochMs: Long, out: DoubleArray) {
        val t = centuries(epochMs)
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0
        val cd = cos(rad(decDeg))
        val sd = sin(rad(decDeg))
        val raz = rad(raDeg + zeta)
        val a = cd * sin(raz)
        val b = cos(rad(theta)) * cd * cos(raz) - sin(rad(theta)) * sd
        val c = sin(rad(theta)) * cd * cos(raz) + cos(rad(theta)) * sd
        out[0] = mod360(deg(atan2(a, b)) + z)
        out[1] = deg(asin(c.coerceIn(-1.0, 1.0)))
    }

    /** Altitude and azimuth (from north, clockwise) of a body with this right ascension and declination. */
    fun altAz(raDeg: Double, decDeg: Double, lstDeg: Double, latDeg: Double): AltAz {
        val h = rad(lstDeg - raDeg)
        val la = rad(latDeg)
        val de = rad(decDeg)
        val alt = deg(asin((sin(la) * sin(de) + cos(la) * cos(de) * cos(h)).coerceIn(-1.0, 1.0)))
        val az = mod360(deg(atan2(sin(h), cos(h) * sin(la) - tan(de) * cos(la))) + 180.0)
        return AltAz(alt, az)
    }

    /** Minutes after 00:00 UTC of one sun event of the solar day around [epochDay] (can be below 0 or above 1440). */
    private fun eventMinutes(epochMs: Long, latDeg: Double, lonDeg: Double, rise: Boolean): Double? {
        val s = sun(epochMs)
        val c = cos(rad(90.833)) / (cos(rad(latDeg)) * cos(rad(s.decDeg))) - tan(rad(latDeg)) * tan(rad(s.decDeg))
        if (c > 1.0 || c < -1.0 || c.isNaN()) return null
        var ha = deg(acos(c))
        if (!rise) ha = -ha
        return 720.0 - 4.0 * (lonDeg + ha) - s.eotMinutes
    }

    private fun eventUtcMs(epochDay: Long, latDeg: Double, lonDeg: Double, rise: Boolean): Long? {
        val base = epochDay * 86_400_000L
        val first = eventMinutes(base + 43_200_000L, latDeg, lonDeg, rise) ?: return null
        val second = eventMinutes(base + (first * 60_000.0).roundToLong(), latDeg, lonDeg, rise) ?: return null
        return base + (second * 60_000.0).roundToLong()
    }

    /**
     * NOAA sunrise (zenith 90.833 degrees, event time evaluated twice) of the solar day whose transit falls on
     * [epochDay], the UTC date counted from 1970-01-01. Null in polar day or polar night. The instant can lie on
     * the previous or the next UTC date for far eastern or western places.
     */
    fun sunriseUtcMs(epochDay: Long, latDeg: Double, lonDeg: Double): Long? = eventUtcMs(epochDay, latDeg, lonDeg, true)

    fun sunsetUtcMs(epochDay: Long, latDeg: Double, lonDeg: Double): Long? = eventUtcMs(epochDay, latDeg, lonDeg, false)

    fun solarLongitudeDeg(epochMs: Long): Double = sun(epochMs).lambdaDeg
}
