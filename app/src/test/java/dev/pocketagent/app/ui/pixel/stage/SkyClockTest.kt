package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SkyClockTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun fromJulianDay(jd: Double): Long = Math.round((jd - 2_440_587.5) * 86_400_000.0)

    private fun wrap180(x: Double): Double = x - 360.0 * Math.floor((x + 180.0) / 360.0)

    @Test fun gmstMatchesMeeus() {
        assertEquals(280.46061837, SkyClock.gmstDeg(utc(2000, 1, 1, 12)), 1e-6)
        assertEquals(197.693195, SkyClock.gmstDeg(utc(1987, 4, 10)), 3e-5)
        assertEquals(128.737873, SkyClock.gmstDeg(utc(1987, 4, 10, 19, 21)), 3e-5)
        assertEquals(2_451_545.0, SkyClock.julianDay(utc(2000, 1, 1, 12)), 1e-9)
        assertEquals(100.0, SkyClock.lstDeg(utc(2000, 1, 1, 12), 100.0 - 280.46061837), 1e-6)
    }

    @Test fun sunFollowsMeeusExample25a() {
        // 1992 October 13, 0h: apparent longitude 199.90895, right ascension 198.38083, declination -7.78507
        val s = SkyClock.sun(fromJulianDay(2_448_908.5))
        assertEquals(199.90895, s.lambdaDeg, 0.003)
        assertEquals(198.38083, s.raDeg, 0.003)
        assertEquals(-7.78507, s.decDeg, 0.003)
    }

    @Test fun moonFollowsMeeusExample47a() {
        // 1992 April 12, 0h TD: longitude 133.162655 (geometric), latitude -3.229126, right ascension 134.688470, declination 13.768368
        val m = SkyClock.moon(fromJulianDay(2_448_724.5))
        assertEquals(133.1627, m.lambdaDeg, 0.03)
        assertEquals(-3.2291, m.betaDeg, 0.01)
        assertEquals(134.6885, m.raDeg, 0.03)
        assertEquals(13.7684, m.decDeg, 0.02)
    }

    @Test fun precessionFollowsMeeusExample21b() {
        val out = DoubleArray(2)
        // theta Persei with its proper motion already applied, carried to 2028 November 13.19
        val ra0 = (2.0 + 44.0 / 60.0 + 12.975 / 3600.0) * 15.0
        val dec0 = 49.0 + 13.0 / 60.0 + 39.90 / 3600.0
        SkyClock.precess(ra0, dec0, fromJulianDay(2_462_088.69), out)
        assertEquals((2.0 + 46.0 / 60.0 + 11.331 / 3600.0) * 15.0, out[0], 1e-4)
        assertEquals(49.0 + 20.0 / 60.0 + 54.54 / 3600.0, out[1], 1e-4)
        SkyClock.precess(101.2871, -16.7161, utc(2000, 1, 1, 12), out)
        assertEquals(101.2871, out[0], 1e-6)
        assertEquals(-16.7161, out[1], 1e-6)
    }

    private class Place(val name: String, val lat: Double, val lon: Double, val y: Int, val mo: Int, val d: Int)

    private fun minutesOfDayUtc(ms: Long, y: Int, mo: Int, d: Int): Double = (ms - utc(y, mo, d)) / 60_000.0

    private fun checkSunTimes(p: Place, riseUtcMin: Double, setUtcMin: Double) {
        val day = LocalDate.of(p.y, p.mo, p.d).toEpochDay()
        val rise = SkyClock.sunriseUtcMs(day, p.lat, p.lon)
        val set = SkyClock.sunsetUtcMs(day, p.lat, p.lon)
        assertNotNull("${p.name} sunrise", rise)
        assertNotNull("${p.name} sunset", set)
        assertEquals("${p.name} sunrise", riseUtcMin, minutesOfDayUtc(rise!!, p.y, p.mo, p.d), 2.0)
        assertEquals("${p.name} sunset", setUtcMin, minutesOfDayUtc(set!!, p.y, p.mo, p.d), 2.0)
    }

    @Test fun sunriseSunsetSixPlaces() {
        // published local times, written here as UTC minutes after 00:00 of the date (Sydney's sunrise falls on the day before)
        checkSunTimes(Place("London", 51.5074, -0.1278, 2027, 6, 21), 3 * 60 + 43.0, 20 * 60 + 21.0)
        checkSunTimes(Place("New York", 40.7128, -74.0060, 2026, 12, 21), 12 * 60 + 16.0, 21 * 60 + 31.5)
        checkSunTimes(Place("Oslo", 59.9139, 10.7522, 2026, 12, 21), 8 * 60 + 18.0, 14 * 60 + 11.5)
        checkSunTimes(Place("Istanbul", 41.0082, 28.9784, 2027, 3, 21), 4 * 60 + 6.0, 16 * 60 + 16.0)
        checkSunTimes(Place("Sydney", -33.8688, 151.2093, 2026, 12, 21), 18 * 60 + 40.0 - 24 * 60, 9 * 60 + 5.0)
        checkSunTimes(Place("Diyarbakir", 37.9144, 40.2306, 2027, 3, 21), 3 * 60 + 21.0, 15 * 60 + 31.0)
    }

    @Test fun polarDayAndNight() {
        val winter = LocalDate.of(2026, 12, 21).toEpochDay()
        val summer = LocalDate.of(2027, 6, 21).toEpochDay()
        assertNull(SkyClock.sunriseUtcMs(winter, 69.65, 18.96))
        assertNull(SkyClock.sunsetUtcMs(winter, 69.65, 18.96))
        assertNull(SkyClock.sunriseUtcMs(summer, 69.65, 18.96))
        assertNull(SkyClock.sunsetUtcMs(summer, 69.65, 18.96))
    }

    private fun peakAltitude(lat: Double, lon: Double, y: Int, mo: Int, d: Int): DoubleArray {
        var best = -99.0
        var bestMs = 0L
        val base = utc(y, mo, d)
        for (minute in 0 until 1440) {
            val t = base + minute * 60_000L
            val s = SkyClock.sun(t)
            val alt = SkyClock.altAz(s.raDeg, s.decDeg, SkyClock.lstDeg(t, lon), lat).altDeg
            if (alt > best) {
                best = alt
                bestMs = t
            }
        }
        return doubleArrayOf(best, SkyClock.sun(bestMs).decDeg)
    }

    @Test fun sunNoonAltitudeIsGeometric() {
        val ist = peakAltitude(41.0082, 28.9784, 2027, 3, 21)
        assertEquals(90.0 - Math.abs(41.0082 - ist[1]), ist[0], 0.05)
        assertEquals(49.2, ist[0], 0.1)
        val oslo = peakAltitude(59.9139, 10.7522, 2026, 12, 21)
        assertEquals(90.0 - Math.abs(59.9139 - oslo[1]), oslo[0], 0.05)
        assertEquals(6.6, oslo[0], 0.1)
        val london = peakAltitude(51.5074, -0.1278, 2027, 6, 21)
        assertEquals(90.0 - Math.abs(51.5074 - london[1]), london[0], 0.05)
        assertEquals(61.9, london[0], 0.1)
    }

    @Test fun equinoxLongitude() {
        assertEquals(0.0, wrap180(SkyClock.solarLongitudeDeg(utc(2027, 3, 20, 20, 25))), 0.03)
        assertEquals(180.0, SkyClock.solarLongitudeDeg(utc(2026, 9, 23, 0, 5)), 0.03)
        assertEquals(270.0, SkyClock.solarLongitudeDeg(utc(2026, 12, 21, 20, 50)), 0.03)
        assertEquals(90.0, SkyClock.solarLongitudeDeg(utc(2027, 6, 21, 14, 10)), 0.03)
    }

    private fun elongationError(ms: Long, target: Double): Double = wrap180(SkyClock.elongationDeg(ms) - target)

    @Test fun moonElongationAtKnownSyzygies() {
        val news = listOf(utc(2000, 1, 6, 18, 14), utc(2017, 8, 21, 18, 30), utc(2024, 4, 8, 18, 21))
        val fulls = listOf(utc(2000, 1, 21, 4, 40), utc(2022, 11, 8, 11, 2), utc(2025, 3, 14, 6, 55))
        for (t in news) assertEquals(0.0, elongationError(t, 0.0), 1.5)
        for (t in fulls) assertEquals(0.0, elongationError(t, 180.0), 1.5)
    }

    @Test fun moonPhase16Buckets() {
        assertEquals(0, SkyClock.moonPhase16(0.01))
        assertEquals(0, SkyClock.moonPhase16(11.2))
        assertEquals(1, SkyClock.moonPhase16(11.3))
        assertEquals(4, SkyClock.moonPhase16(90.0))
        assertEquals(8, SkyClock.moonPhase16(179.98))
        assertEquals(12, SkyClock.moonPhase16(270.0))
        assertEquals(15, SkyClock.moonPhase16(348.7))
        assertEquals(0, SkyClock.moonPhase16(348.8))
        assertEquals(0f, SkyClock.moonIllumination(0.0), 1e-6f)
        assertEquals(0.5f, SkyClock.moonIllumination(90.0), 1e-6f)
        assertEquals(1f, SkyClock.moonIllumination(180.0), 1e-6f)
    }

    private class Star(val name: String, val ra: Double, val dec: Double)

    private val sirius = Star("Sirius", 101.2871, -16.7161)
    private val vega = Star("Vega", 279.2347, 38.7837)
    private val arcturus = Star("Arcturus", 213.9153, 19.1824)
    private val polaris = Star("Polaris", 37.9546, 89.2641)

    private fun place(s: Star, lat: Double, lon: Double, t: Long): AltAz {
        val rd = DoubleArray(2)
        SkyClock.precess(s.ra, s.dec, t, rd)
        return SkyClock.altAz(rd[0], rd[1], SkyClock.lstDeg(t, lon), lat)
    }

    @Test fun starPlacesAtInstants() {
        val a = place(sirius, 59.9139, 10.7522, utc(2027, 1, 15, 20))
        assertEquals(8.05, a.altDeg, 0.15)
        assertEquals(145.55, a.azDeg, 0.15)
        val b = place(vega, 41.0082, 28.9784, utc(2027, 7, 15, 19))
        assertEquals(65.43, b.altDeg, 0.15)
        assertEquals(84.52, b.azDeg, 0.15)
        val c = place(arcturus, -33.8688, 151.2093, utc(2027, 4, 10, 14))
        assertEquals(35.31, c.altDeg, 0.15)
        assertEquals(16.80, c.azDeg, 0.15)
    }

    private fun culmination(s: Star, lat: Double, lon: Double, y: Int, mo: Int, d: Int): DoubleArray {
        var best = -99.0
        var bestAz = 0.0
        val base = utc(y, mo, d)
        for (minute in 0 until 1440) {
            val a = place(s, lat, lon, base + minute * 60_000L)
            if (a.altDeg > best) {
                best = a.altDeg
                bestAz = a.azDeg
            }
        }
        val rd = DoubleArray(2)
        SkyClock.precess(s.ra, s.dec, base + 12 * 3_600_000L, rd)
        return doubleArrayOf(best, bestAz, rd[1])
    }

    @Test fun starCulmination() {
        val s = culmination(sirius, 59.9139, 10.7522, 2027, 1, 15)
        assertEquals(90.0 - Math.abs(59.9139 - s[2]), s[0], 0.1)
        assertEquals(13.34, s[0], 0.1)
        assertEquals(0.0, wrap180(s[1] - 180.0), 1.0)
        val v = culmination(vega, 41.0082, 28.9784, 2027, 1, 15)
        assertEquals(90.0 - Math.abs(41.0082 - v[2]), v[0], 0.1)
        assertEquals(87.80, v[0], 0.1)
        val a = culmination(arcturus, -33.8688, 151.2093, 2027, 1, 15)
        assertEquals(90.0 - Math.abs(-33.8688 - a[2]), a[0], 0.1)
        assertEquals(37.07, a[0], 0.1)
        assertEquals(0.0, wrap180(a[1]), 1.0)
        val p = culmination(polaris, 59.9139, 10.7522, 2027, 1, 15)
        assertEquals(90.0 - Math.abs(59.9139 - p[2]), p[0], 0.1)
        assertEquals(60.54, p[0], 0.1)
        assertEquals(0.0, wrap180(p[1]), 1.0)
    }

    @Test fun altAzOfTheCelestialPoles() {
        // the north pole of the sky stands at the observer's latitude, due north
        val a = SkyClock.altAz(10.0, 90.0, 123.0, 41.0)
        assertEquals(41.0, a.altDeg, 1e-9)
        assertTrue(Math.abs(wrap180(a.azDeg)) < 1e-6)
        // a body on the meridian south of the zenith at the equator of the sky stands at 90 - lat, due south
        val b = SkyClock.altAz(50.0, 0.0, 50.0, 41.0)
        assertEquals(49.0, b.altDeg, 1e-9)
        assertEquals(180.0, b.azDeg, 1e-6)
    }
}
