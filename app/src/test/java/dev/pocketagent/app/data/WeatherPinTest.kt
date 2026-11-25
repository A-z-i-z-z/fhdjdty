package dev.pocketagent.app.data

import dev.pocketagent.app.ui.pixel.stage.CloudCover
import dev.pocketagent.app.ui.pixel.stage.FogLevel
import dev.pocketagent.app.ui.pixel.stage.Precip
import dev.pocketagent.app.ui.pixel.stage.toLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherPinTest {
    private fun pin(text: String): WeatherPoint = WeatherPin.parse(text) ?: throw AssertionError("rejected: $text")

    @Test fun presetsAndItems() {
        val storm = pin("storm")
        assertEquals(0L, storm.timeMs)
        assertEquals(WeatherCondition.HEAVY_RAIN, storm.condition)
        assertEquals(6.0, storm.precipMm, 0.0)
        assertTrue(storm.thunder)
        assertEquals(12.0, storm.temperatureC, 0.0)
        assertEquals(95, storm.cloudPercent)
        assertEquals(1.0 + 13.0 * 0.7, storm.windMs, 1e-9)
        assertEquals(270.0, storm.windFromDeg, 0.0)

        val clear = pin("clear")
        assertEquals(WeatherCondition.CLEAR, clear.condition)
        assertEquals(5, clear.cloudPercent)
        assertEquals(18.0, clear.temperatureC, 0.0)

        val drizzle = pin("drizzle")
        assertEquals(WeatherCondition.LIGHT_RAIN, drizzle.condition)
        assertEquals(0.6, drizzle.precipMm, 0.0)
        assertEquals(65, drizzle.cloudPercent)
        assertEquals(9.0, drizzle.temperatureC, 0.0)

        val blizzard = pin("blizzard")
        assertEquals(WeatherCondition.HEAVY_SNOW, blizzard.condition)
        assertEquals(90.0, blizzard.windFromDeg, 0.0)
        assertEquals(-6.0, blizzard.temperatureC, 0.0)

        val mist = pin("mist")
        assertEquals(WeatherCondition.FOG, mist.condition)
        assertEquals(0.5, mist.windMs, 0.0)
        assertEquals(4.0, mist.temperatureC, 0.0)

        // the item forms of the debug link
        val a = pin("cloud:3,rain:heavy,wind:0.6,temp:7")
        assertEquals(WeatherCondition.HEAVY_RAIN, a.condition)
        assertEquals(95, a.cloudPercent)
        assertEquals(1.0 + 13.0 * 0.6, a.windMs, 1e-9)
        assertEquals(7.0, a.temperatureC, 0.0)
        assertFalse(a.thunder)
        assertEquals(WeatherCondition.SLEET, pin("sleet").condition)
        assertEquals(1.0, pin("sleet").precipMm, 0.0)
        assertEquals(WeatherCondition.LIGHT_SNOW, pin("snow:light").condition)
        assertEquals(0.6, pin("snow:light").precipMm, 0.0)
        assertEquals(WeatherCondition.HEAVY_SNOW, pin("snow:heavy").condition)
        assertEquals(4.0, pin("fog:light").windMs, 0.0)
        assertEquals(WeatherCondition.CLOUDY, pin("cloud:3").condition)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, pin("cloud:1").condition)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, pin("cloud:2").condition)
        assertEquals(WeatherCondition.CLEAR, pin("cloud:0").condition)
        assertEquals(30, pin("cloud:1").cloudPercent)
        assertEquals(65, pin("cloud:2").cloudPercent)
        // whitespace and case are forgiven, a later item of the same kind wins, a preset takes later items
        assertEquals(3.0, pin(" Temp:3 , CLOUD:2 ").temperatureC, 0.0)
        assertEquals(2.0, pin("temp:1,temp:2").temperatureC, 0.0)
        assertEquals(21.0, pin("storm,temp:21").temperatureC, 0.0)
        assertEquals(WeatherCondition.LIGHT_SNOW, pin("rain:heavy,snow:light").condition)
        assertEquals(0.6, pin("rain:heavy,snow:light").precipMm, 0.0)
        // wind direction and calm
        assertEquals(270.0, pin("wind:0.5").windFromDeg, 0.0)
        assertEquals(90.0, pin("wind:-0.5").windFromDeg, 0.0)
        assertEquals(0.0, pin("wind:0").windMs, 0.0)
        // an explicit wind wins over the wind that comes with fog, as long as the fog keeps its kind
        assertEquals(1.0 + 13.0 * 0.5, pin("fog:light,wind:0.5").windMs, 1e-9)
        assertEquals(FogLevel.LIGHT, pin("fog:light,wind:0.5").toLook().fog)
        assertEquals(1.0 + 13.0 * 0.05, pin("fog:dense,wind:0.05").windMs, 1e-9)
        assertEquals(FogLevel.DENSE, pin("fog:dense,wind:0.05").toLook().fog)
        assertEquals(FogLevel.DENSE, pin("fog:dense,wind:0").toLook().fog)
        // a wind that would silently turn the fog into the other kind rejects the pin (13 * x + 1 = 2.0 at x = 1/13)
        assertNull(WeatherPin.parse("fog:dense,wind:0.5"))
        assertNull(WeatherPin.parse("fog:dense,wind:-0.5"))
        assertNull(WeatherPin.parse("fog:dense,wind:0.08"))
        assertNull(WeatherPin.parse("fog:light,wind:0"))
        assertNull(WeatherPin.parse("fog:light,wind:0.05"))
        // a later rain drops the fog, so its wind rule goes with it
        assertNotNull(WeatherPin.parse("fog:dense,rain:light,wind:0.5"))
        // a later rain forgets the wind of an earlier fog
        assertEquals(0.0, pin("fog:light,rain:light").windMs, 0.0)
    }

    @Test fun badItemRejectsAll() {
        val bad = listOf(
            "", " ", ",", "storm,", ",storm", "off", "weather", "cloud", "cloud:4", "cloud:-1", "cloud:x", "cloud:1.5",
            "rain", "rain:medium", "rain:", "snow:x", "sleet:light", "fog", "fog:thick", "thunder:yes", "wind", "wind:2", "wind:-1.1",
            "wind:abc", "wind:NaN", "temp:61", "temp:-61", "temp:hot", "flash:bright", "flash:", "storm,nonsense", "cloud:3,,rain:light",
            "cloud:3;rain:light", "temp:Infinity",
        )
        for (text in bad) assertNull(text, WeatherPin.parse(text))
        // one bad item among good ones rejects the whole value
        assertNull(WeatherPin.parse("cloud:3,rain:heavy,wind:9"))
        assertNotNull(WeatherPin.parse("cloud:3,rain:heavy,wind:1"))
        assertNotNull(WeatherPin.parse("temp:60"))
        assertNotNull(WeatherPin.parse("temp:-60"))
    }

    @Test fun pinGivesTheExpectedLook() {
        val storm = pin("storm").toLook()
        assertEquals(CloudCover.HEAVY, storm.cloud)
        assertEquals(Precip.RAIN_HEAVY, storm.precip)
        assertEquals(0.7f, storm.wind, 1e-6f)
        assertTrue(storm.thunder)
        assertEquals(0, storm.flash)

        val blizzard = pin("blizzard").toLook()
        assertEquals(CloudCover.HEAVY, blizzard.cloud)
        assertEquals(Precip.SNOW_HEAVY, blizzard.precip)
        assertEquals(-0.6f, blizzard.wind, 1e-6f)
        assertFalse(blizzard.thunder)

        val mist = pin("mist").toLook()
        assertEquals(FogLevel.DENSE, mist.fog)
        assertEquals(CloudCover.NONE, mist.cloud)
        assertEquals(Precip.NONE, mist.precip)
        assertEquals(FogLevel.LIGHT, pin("fog:light").toLook().fog)

        assertEquals(CloudCover.LIGHT, pin("cloud:1").toLook().cloud)
        assertEquals(CloudCover.MEDIUM, pin("cloud:2").toLook().cloud)
        assertEquals(CloudCover.HEAVY, pin("cloud:3").toLook().cloud)
        assertEquals(CloudCover.NONE, pin("cloud:0").toLook().cloud)
        assertTrue(pin("clear").toLook().isClear)

        val drizzle = pin("drizzle").toLook()
        assertEquals(CloudCover.MEDIUM, drizzle.cloud)
        assertEquals(Precip.RAIN_LIGHT, drizzle.precip)
        assertEquals(0f, drizzle.wind, 0f)

        // rain alone raises the cloud, snow:light too, sleet and thunder as the look rules say
        assertEquals(CloudCover.MEDIUM, pin("rain:light").toLook().cloud)
        assertEquals(CloudCover.MEDIUM, pin("snow:light").toLook().cloud)
        assertEquals(CloudCover.MEDIUM, pin("sleet").toLook().cloud)
        assertEquals(CloudCover.HEAVY, pin("thunder").toLook().cloud)
        assertEquals(Precip.SLEET, pin("sleet").toLook().precip)
        assertEquals(0.6f, pin("wind:0.6").toLook().wind, 1e-6f)
        assertEquals(-1f, pin("wind:-1").toLook().wind, 1e-6f)
        assertEquals(1f, pin("wind:1").toLook().wind, 1e-6f)
    }

    @Test fun flashItems() {
        assertEquals(1, pin("storm,flash").toLook().flash)
        assertEquals(2, pin("storm,flash:dim").toLook().flash)
        assertEquals(0, pin("storm").toLook().flash)
        assertEquals(2, pin("flash,flash:dim").toLook().flash)
        assertEquals(1, pin("flash:dim,flash").toLook().flash)
        assertTrue(pin("storm,flash").toLook().thunder)
        // the pin point carries the flash; a parsed forecast never does
        assertEquals(1, pin("flash").flash)
        assertEquals(0, pin("storm").flash)
    }

    @Test fun defaultTemperatureIs11() {
        assertEquals(11.0, pin("cloud:1").temperatureC, 0.0)
        assertEquals(11.0, pin("rain:light").temperatureC, 0.0)
        assertEquals(11.0, pin("thunder").temperatureC, 0.0)
    }

    @Test fun pinReadoutLine() {
        // "11°, light rain": the number the line shows for a pin of temp:11 and for Fahrenheit
        assertEquals("11", formatTemperature(pin("temp:11,rain:light").temperatureC, false))
        assertEquals("52", formatTemperature(pin("temp:11,rain:light").temperatureC, true))
    }
}
