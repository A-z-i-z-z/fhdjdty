package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanetsTest {
    /** Ecliptic longitude of date of a planet, from its right ascension and declination of date. */
    private fun longitude(id: Int, epochMs: Long): Double {
        val rd = DoubleArray(2)
        Planets.raDec(id, epochMs, rd)
        val t = (SkyClock.julianDay(epochMs) - 2_451_545.0) / 36525.0
        val eps = Math.toRadians(23.4392911 - 0.0130042 * t)
        val ra = Math.toRadians(rd[0])
        val dec = Math.toRadians(rd[1])
        val lam = Math.toDegrees(Math.atan2(Math.sin(ra) * Math.cos(eps) + Math.tan(dec) * Math.sin(eps), Math.cos(ra)))
        return lam - 360.0 * Math.floor(lam / 360.0)
    }

    private fun elongationFromSun(id: Int, epochMs: Long): Double {
        val d = longitude(id, epochMs) - SkyClock.solarLongitudeDeg(epochMs)
        return d - 360.0 * Math.floor(d / 360.0)
    }

    private fun separation(id: Int, epochMs: Long): Double {
        val e = elongationFromSun(id, epochMs)
        return if (e > 180.0) 360.0 - e else e
    }

    @Test fun superiorPlanetsAtOpposition() {
        // the planet is 180 degrees from the sun: the elements are J2000 and the sun is of date, so allow a degree
        assertEquals(180.0, elongationFromSun(2, utc(2025, 1, 16, 2)), 1.0) // Mars
        assertEquals(180.0, elongationFromSun(3, utc(2026, 1, 10, 16)), 1.0) // Jupiter
        assertEquals(180.0, elongationFromSun(4, utc(2025, 9, 21, 12)), 1.0) // Saturn
    }

    @Test fun inferiorPlanetsStayNearTheSun() {
        // the greatest elongations are 28 degrees (Mercury) and 47.8 degrees (Venus)
        var mercury = 0.0
        var venus = 0.0
        var day = 0
        while (day < 730) {
            val t = utc(2026, 1, 1) + day * 86_400_000L
            mercury = Math.max(mercury, separation(0, t))
            venus = Math.max(venus, separation(1, t))
            day += 2
        }
        assertTrue("Mercury $mercury", mercury > 20.0 && mercury < 28.5)
        assertTrue("Venus $venus", venus > 44.0 && venus < 48.0)
    }

    @Test fun positionsAtAKnownNight() {
        // Oslo-like place (45 N, 10 E), 2025-01-16 02:00 UTC: Mars high in the west, Jupiter low, the rest below the horizon
        val list = Planets.positions(utc(2025, 1, 16, 2), 45.0, 10.0)
        val ids = list.map { it.id }
        assertEquals(listOf(2, 3), ids)
        val mars = list.first { it.id == 2 }
        assertEquals(54.5f, mars.altDeg, 0.5f)
        assertEquals(248.4f, mars.azDeg, 0.5f)
        assertEquals(1f, mars.mag, 0f)
        assertEquals(-2f, list.first { it.id == 3 }.mag, 0f)
        for (p in list) assertTrue(p.altDeg >= -1f)
    }

    @Test fun magnitudesAreTheFixedOnes() {
        assertEquals(0f, Planets.magnitude(0), 0f)
        assertEquals(-4f, Planets.magnitude(1), 0f)
        assertEquals(1f, Planets.magnitude(2), 0f)
        assertEquals(-2f, Planets.magnitude(3), 0f)
        assertEquals(0.5f, Planets.magnitude(4), 0f)
    }

    @Test fun visibilityRule() {
        // Venus (mag -4) is out as soon as the sun is 1 degree down; Saturn (0.5) needs about 4 degrees; below 2 degrees of altitude nothing
        assertTrue(Planets.visible(20f, -4f, 1f))
        assertFalse(Planets.visible(20f, 0.5f, 3f))
        assertTrue(Planets.visible(20f, 0.5f, 4.1f))
        assertFalse(Planets.visible(1.5f, -4f, 20f))
        assertFalse(Planets.visible(20f, 1f, 0f))
    }

    @Test fun framePlanetsComeFromTheSameFunction() {
        val f = SkyTestKit.frame(utc(2025, 1, 16, 2), 45.0, 10.0)
        val direct = Planets.positions(f.epochMs, f.latDeg, f.lonDeg)
        assertEquals(direct.size, f.planets.size)
        assertNotNull(f.planets.firstOrNull { it.id == 2 })
        for (i in direct.indices) {
            assertEquals(direct[i].id, f.planets[i].id)
            assertEquals(direct[i].altDeg, f.planets[i].altDeg, 0f)
        }
    }

    @Test fun precessionIsApplied() {
        // the declination of date differs from the J2000 one by a few tenths of a degree twenty years on: Mars in 2025
        val rd = DoubleArray(2)
        Planets.raDec(2, utc(2025, 1, 16, 2), rd)
        assertEquals(119.12, rd[0], 0.3)
        assertEquals(25.11, rd[1], 0.3)
    }
}
