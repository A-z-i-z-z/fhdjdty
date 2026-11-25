package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.data.WeatherCondition
import dev.pocketagent.app.data.WeatherPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class WeatherLookTest {
    private fun point(
        cloud: Int = 10, condition: WeatherCondition = WeatherCondition.CLEAR, windMs: Double = 0.0, from: Double = 270.0,
        thunder: Boolean = false, flash: Int = 0,
    ) = WeatherPoint(0L, 11.0, windMs, from, cloud, 0.0, condition, thunder, flash)

    @Test fun toLookTable() {
        // cloud thresholds 20, 50, 85
        val byPercent = listOf(0 to CloudCover.NONE, 19 to CloudCover.NONE, 20 to CloudCover.LIGHT, 49 to CloudCover.LIGHT,
            50 to CloudCover.MEDIUM, 84 to CloudCover.MEDIUM, 85 to CloudCover.HEAVY, 100 to CloudCover.HEAVY)
        for ((p, c) in byPercent) assertEquals("$p", c, point(cloud = p, condition = WeatherCondition.CLOUDY).toLook().cloud)
        // precipitation, with the cloud it forces
        data class Row(val cond: WeatherCondition, val precip: Precip, val cloudAt5: CloudCover)
        val rows = listOf(
            Row(WeatherCondition.CLEAR, Precip.NONE, CloudCover.NONE),
            Row(WeatherCondition.FAIR, Precip.NONE, CloudCover.NONE),
            Row(WeatherCondition.PARTLY_CLOUDY, Precip.NONE, CloudCover.NONE),
            Row(WeatherCondition.CLOUDY, Precip.NONE, CloudCover.NONE),
            Row(WeatherCondition.FOG, Precip.NONE, CloudCover.NONE),
            Row(WeatherCondition.LIGHT_RAIN, Precip.RAIN_LIGHT, CloudCover.MEDIUM),
            Row(WeatherCondition.RAIN, Precip.RAIN_HEAVY, CloudCover.HEAVY),
            Row(WeatherCondition.HEAVY_RAIN, Precip.RAIN_HEAVY, CloudCover.HEAVY),
            Row(WeatherCondition.SLEET, Precip.SLEET, CloudCover.MEDIUM),
            Row(WeatherCondition.LIGHT_SNOW, Precip.SNOW_LIGHT, CloudCover.MEDIUM),
            Row(WeatherCondition.SNOW, Precip.SNOW_HEAVY, CloudCover.HEAVY),
            Row(WeatherCondition.HEAVY_SNOW, Precip.SNOW_HEAVY, CloudCover.HEAVY),
        )
        for (r in rows) {
            val look = point(cloud = 5, condition = r.cond).toLook()
            assertEquals(r.cond.name, r.precip, look.precip)
            assertEquals(r.cond.name, r.cloudAt5, look.cloud)
            // a cover that is already thicker stays
            val thick = point(cloud = 95, condition = r.cond).toLook()
            assertEquals(r.cond.name, CloudCover.HEAVY, thick.cloud)
        }
        // medium cover with light rain stays medium
        assertEquals(CloudCover.MEDIUM, point(cloud = 60, condition = WeatherCondition.LIGHT_RAIN).toLook().cloud)
        // thunder makes it heavy, and is carried
        val th = point(cloud = 5, thunder = true).toLook()
        assertEquals(CloudCover.HEAVY, th.cloud)
        assertTrue(th.thunder)
        // fog: dense only when calm
        assertEquals(FogLevel.DENSE, point(condition = WeatherCondition.FOG, windMs = 1.9).toLook().fog)
        assertEquals(FogLevel.LIGHT, point(condition = WeatherCondition.FOG, windMs = 2.0).toLook().fog)
        assertEquals(FogLevel.NONE, point(condition = WeatherCondition.CLOUDY, windMs = 0.0).toLook().fog)
        // flash is carried as it is
        assertEquals(2, point(flash = 2).toLook().flash)
        assertEquals(0, point().toLook().flash)
    }

    @Test fun starVisibilityTable() {
        val cloud = mapOf(CloudCover.NONE to 1.0f, CloudCover.LIGHT to 0.75f, CloudCover.MEDIUM to 0.4f, CloudCover.HEAVY to 0.1f)
        val fog = mapOf(FogLevel.NONE to 1.0f, FogLevel.LIGHT to 0.85f, FogLevel.DENSE to 0.5f)
        var cells = 0
        for ((c, cv) in cloud) {
            for ((f, fv) in fog) {
                val look = WeatherLook(cloud = c, fog = f)
                assertEquals("$c $f", cv * fv, look.starVisibility(), 1e-6f)
                cells++
            }
        }
        assertEquals(12, cells)
        assertEquals(1f, WeatherLook.CLEAR.starVisibility(), 0f)
        // rain and wind do not hide the stars by themselves
        assertEquals(1f, WeatherLook(precip = Precip.RAIN_HEAVY, wind = 0.5f).starVisibility(), 0f)
    }

    @Test fun bodyVisibilityFloor() {
        var lowest = 1f
        for (c in CloudCover.values()) {
            for (f in FogLevel.values()) {
                val look = WeatherLook(cloud = c, fog = f)
                val b = look.bodyVisibility()
                assertEquals(0.25f + 0.75f * look.starVisibility(), b, 1e-6f)
                assertTrue("$c $f: $b", b >= 0.25f && b <= 1f)
                if (b < lowest) lowest = b
            }
        }
        assertEquals(0.25f + 0.75f * 0.05f, lowest, 1e-6f)
        assertEquals(1f, WeatherLook.CLEAR.bodyVisibility(), 0f)
    }

    @Test fun quantisedWind() {
        var d = 0.0
        while (d <= 30.0) {
            var deg = 0.0
            while (deg < 360.0) {
                val w = point(windMs = d, from = deg).toLook().wind
                assertTrue("$d $deg: $w", w >= -1f && w <= 1f)
                val tenths = w * 10f
                assertTrue("$d $deg: $w", abs(tenths - tenths.roundToInt()) < 1e-4f)
                deg += 7.0
            }
            d += 0.5
        }
        // reference values: speed 8.8 m/s gives strength 0.6; from the west full, from north or south 0.5 of it
        assertEquals(0.6f, point(windMs = 8.8, from = 270.0).toLook().wind, 1e-6f)
        assertEquals(-0.6f, point(windMs = 8.8, from = 90.0).toLook().wind, 1e-6f)
        assertEquals(0.3f, point(windMs = 8.8, from = 0.0).toLook().wind, 1e-6f)
        assertEquals(0.3f, point(windMs = 8.8, from = 180.0).toLook().wind, 1e-6f)
        // the first sample point: 5.1 m/s from 250 degrees
        assertEquals(0.3f, point(windMs = 5.1, from = 250.0).toLook().wind, 1e-6f)
        // calm
        assertEquals(0f, point(windMs = 0.0).toLook().wind, 0f)
        assertEquals(0f, point(windMs = 1.0).toLook().wind, 0f)
    }

    @Test fun windHelpers() {
        assertEquals(1, WeatherWind.grassLeanPx(0.5f))
        assertEquals(2, WeatherWind.grassLeanPx(1f))
        assertEquals(-2, WeatherWind.grassLeanPx(-1f))
        assertEquals(0, WeatherWind.grassLeanPx(0f))
        assertEquals(0, WeatherWind.grassLeanPx(0.2f))
        assertEquals(2, WeatherWind.grassLeanPx(3f))
        assertEquals(-2, WeatherWind.grassLeanPx(-3f))
        assertEquals(6f, WeatherWind.driftCells(1f, 1f), 0f)
        assertEquals(0f, WeatherWind.driftCells(1f, 0f), 0f)
        assertEquals(-1.5f, WeatherWind.driftCells(-0.5f, 0.5f), 1e-6f)
        assertEquals(2f, WeatherWind.driftCells(1f, 1f, 2f), 0f)
        assertEquals(1f, WeatherWind.flameLean(2f), 0f)
        assertEquals(-1f, WeatherWind.flameLean(-3f), 0f)
        assertEquals(0.4f, WeatherWind.flameLean(0.4f), 0f)
    }

    @Test fun clearIsClear() {
        assertTrue(WeatherLook.CLEAR.isClear)
        assertTrue(WeatherLook().isClear)
        assertEquals(WeatherLook(), WeatherLook.CLEAR)
        assertFalse(WeatherLook(cloud = CloudCover.LIGHT).isClear)
        assertFalse(WeatherLook(precip = Precip.SNOW_LIGHT).isClear)
        assertFalse(WeatherLook(fog = FogLevel.LIGHT).isClear)
        assertFalse(WeatherLook(thunder = true).isClear)
        assertFalse(WeatherLook(flash = 1).isClear)
        // wind alone draws nothing in the weather layers
        assertTrue(WeatherLook(wind = 0.7f).isClear)
        assertFalse(WeatherLook.CLEAR.wet)
        assertEquals(0, WeatherLook.CLEAR.rainLevel)
    }

    @Test fun wetAndRainLevel() {
        val expected = mapOf(
            Precip.NONE to 0, Precip.RAIN_LIGHT to 1, Precip.RAIN_HEAVY to 2, Precip.SLEET to 1, Precip.SNOW_LIGHT to 0, Precip.SNOW_HEAVY to 0,
        )
        for (p in Precip.values()) {
            val look = WeatherLook(precip = p)
            assertEquals(p.name, expected[p], look.rainLevel)
            assertEquals(p.name, p != Precip.NONE, look.wet)
        }
    }

    @Test fun geometryDefaultsAreTheScenesRidgeOffsets() {
        val g = WeatherGeometry(155, 120, 80, 93, 117)
        assertEquals(42, g.farRidgeTop)
        assertEquals(66, g.nearRidgeTop)
        val h = WeatherGeometry(155, 120, 80, 93, 117, farRidgeTop = 30, nearRidgeTop = 60)
        assertEquals(30, h.farRidgeTop)
        assertEquals(60, h.nearRidgeTop)
        assertEquals(80, h.groundTop)
        val c = WeatherColors(0.25f, 0xFF102030.toInt())
        assertEquals(0.25f, c.daylight, 0f)
        assertEquals(0xFF102030.toInt(), c.skyBottom)
    }
}
