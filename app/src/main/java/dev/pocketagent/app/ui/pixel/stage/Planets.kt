package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** One planet as the strip shows it: [id] 0 Mercury, 1 Venus, 2 Mars, 3 Jupiter, 4 Saturn; [mag] a fixed brightness. */
class PlanetPos(val id: Int, val altDeg: Float, val azDeg: Float, val mag: Float)

/**
 * The five naked-eye planets from the approximate Keplerian elements of E. M. Standish, "Keplerian Elements for
 * Approximate Positions of the Major Planets", Table 1 (valid 1800 to 2050, J2000 ecliptic): an element is its value
 * at J2000 plus its rate per Julian century times the centuries since J2000. The earth is the earth-moon barycentre
 * (the moon's offset of about 0.00003 AU is far below a pixel). The result is the geocentric J2000 position,
 * rotated to the equinox of the date with [SkyClock.precess]. Accuracy: under 1 degree (a pixel is about 1.4 degrees
 * across the strip). No light time, no parallax, no refraction.
 */
object Planets {
    /** Planet ids: 0 Mercury, 1 Venus, 2 Mars, 3 Jupiter, 4 Saturn. */
    const val COUNT = 5

    /** The fixed magnitudes the strip uses for the visibility rule (Mercury 0, Venus -4, Mars 1, Jupiter -2, Saturn 0.5). */
    private val MAGS = floatArrayOf(0f, -4f, 1f, -2f, 0.5f)

    fun magnitude(id: Int): Float = MAGS[id]

    // Rows: the earth first, then the five planets in id order. Columns: a, e, I, L, long.peri, long.node.
    private val BASE = arrayOf(
        doubleArrayOf(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0),
        doubleArrayOf(0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593),
        doubleArrayOf(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255),
        doubleArrayOf(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891),
        doubleArrayOf(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909),
        doubleArrayOf(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448),
    )
    private val RATE = arrayOf(
        doubleArrayOf(0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0),
        doubleArrayOf(0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081),
        doubleArrayOf(0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418),
        doubleArrayOf(0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343),
        doubleArrayOf(-0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106),
        doubleArrayOf(-0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794),
    )

    private const val OBLIQUITY_J2000_DEG = 23.43928

    private fun rad(d: Double): Double = Math.toRadians(d)

    /** Heliocentric ecliptic J2000 coordinates (AU) of row [row] of the tables (0 earth, 1 to 5 the planets) into [out]. */
    private fun helio(row: Int, t: Double, out: DoubleArray) {
        val b = BASE[row]
        val r = RATE[row]
        val a = b[0] + r[0] * t
        val e = b[1] + r[1] * t
        val inc = b[2] + r[2] * t
        val l = b[3] + r[3] * t
        val varpi = b[4] + r[4] * t
        val node = b[5] + r[5] * t
        val w = varpi - node
        var m = (l - varpi) % 360.0
        if (m > 180.0) m -= 360.0
        if (m < -180.0) m += 360.0
        val eStar = Math.toDegrees(e)
        var ea = m + eStar * sin(rad(m))
        for (i in 0 until 40) {
            val dm = m - (ea - eStar * sin(rad(ea)))
            val de = dm / (1.0 - e * cos(rad(ea)))
            ea += de
            if (abs(de) < 1e-9) break
        }
        val xp = a * (cos(rad(ea)) - e)
        val yp = a * sqrt(1.0 - e * e) * sin(rad(ea))
        val cw = cos(rad(w))
        val sw = sin(rad(w))
        val co = cos(rad(node))
        val so = sin(rad(node))
        val ci = cos(rad(inc))
        val si = sin(rad(inc))
        out[0] = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp
        out[1] = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp
        out[2] = (sw * si) * xp + (cw * si) * yp
    }

    /**
     * Right ascension (index 0, 0 to 360) and declination (index 1) of planet [id] for the equinox of the date,
     * in degrees, written into [out].
     */
    fun raDec(id: Int, epochMs: Long, out: DoubleArray) {
        val t = (SkyClock.julianDay(epochMs) - 2_451_545.0) / 36525.0
        val p = DoubleArray(3)
        val e = DoubleArray(3)
        helio(id + 1, t, p)
        helio(0, t, e)
        val x = p[0] - e[0]
        val y = p[1] - e[1]
        val z = p[2] - e[2]
        val eps = rad(OBLIQUITY_J2000_DEG)
        val ye = y * cos(eps) - z * sin(eps)
        val ze = y * sin(eps) + z * cos(eps)
        var ra = Math.toDegrees(atan2(ye, x))
        ra -= 360.0 * floor(ra / 360.0)
        val dec = Math.toDegrees(atan2(ze, hypot(x, ye)))
        SkyClock.precess(ra, dec, epochMs, out)
    }

    /** The planets that stand at or above -1 degree at this instant and place, in id order. */
    fun positions(epochMs: Long, latDeg: Double, lonDeg: Double): List<PlanetPos> {
        val lst = SkyClock.lstDeg(epochMs, lonDeg)
        val rd = DoubleArray(2)
        val out = ArrayList<PlanetPos>(COUNT)
        for (id in 0 until COUNT) {
            raDec(id, epochMs, rd)
            val aa = SkyClock.altAz(rd[0], rd[1], lst, latDeg)
            if (aa.altDeg >= -1.0) out.add(PlanetPos(id, aa.altDeg.toFloat(), aa.azDeg.toFloat(), MAGS[id]))
        }
        return out
    }

    /**
     * Whether a planet of magnitude [mag] at altitude [altDeg] is bright enough against a sky whose sun is
     * [depressionDeg] below the horizon: from 2 degrees up, once `depression >= 1 + 1.5 * (mag + 1.5)`.
     */
    fun visible(altDeg: Float, mag: Float, depressionDeg: Float): Boolean =
        altDeg >= 2f && depressionDeg >= 1f + 1.5f * (mag + 1.5f)
}
