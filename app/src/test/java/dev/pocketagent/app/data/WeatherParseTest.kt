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
import org.junit.Assume
import org.junit.Test
import java.time.Instant

class WeatherParseTest {
    private val t0 = Instant.parse("2026-11-25T12:00:00Z").toEpochMilli()
    private val hour = 3_600_000L

    private fun sample(): String? =
        javaClass.getResourceAsStream("/weather/locationforecast-sample.json")?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun point(
        cloud: Int = 50, condition: WeatherCondition = WeatherCondition.CLOUDY, windMs: Double = 3.0, from: Double = 270.0,
        thunder: Boolean = false, temp: Double = 11.0, time: Long = 0L,
    ) = WeatherPoint(time, temp, windMs, from, cloud, 0.0, condition, thunder)

    @Test fun sampleParses() {
        val json = sample()
        Assume.assumeTrue("the sample file is not on the test classpath", json != null)
        val s = WeatherParser.parse(json!!, "59.9,10.7", 12, t0)
        assertNotNull(s)
        s!!
        assertEquals("59.9,10.7", s.placeKey)
        assertEquals(8, s.points.size)
        val first = s.points[0]
        assertEquals(t0, first.timeMs)
        assertEquals(4.2, first.temperatureC, 1e-9)
        assertEquals(5.1, first.windMs, 1e-9)
        assertEquals(250.0, first.windFromDeg, 1e-9)
        assertEquals(88, first.cloudPercent)
        assertEquals(0.4, first.precipMm, 1e-9)
        assertEquals(WeatherCondition.LIGHT_RAIN, first.condition)
        assertFalse(first.thunder)
        val conds = s.points.map { it.condition }
        assertEquals(
            listOf(
                WeatherCondition.LIGHT_RAIN, WeatherCondition.HEAVY_RAIN, WeatherCondition.HEAVY_RAIN, WeatherCondition.SLEET,
                WeatherCondition.CLOUDY, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLEAR, WeatherCondition.FOG,
            ),
            conds,
        )
        assertEquals(listOf(false, false, true, true, false, false, false, false), s.points.map { it.thunder })
        // hourly, sorted
        for (i in s.points.indices) assertEquals(t0 + i * hour, s.points[i].timeMs)
        // the last entry has only a six hour block: 0.6 mm over six hours is 0.1 mm an hour
        assertEquals(0.1, s.points[7].precipMm, 1e-9)
        assertEquals(0.4, s.points[0].precipMm, 1e-9)
    }

    @Test fun sampleHasNothingPersonal() {
        val json = sample()
        Assume.assumeTrue("the sample file is not on the test classpath", json != null)
        val text = json!!
        assertTrue(text.contains("59.9") && text.contains("10.7"))
        assertFalse(text.contains("@"))
        assertFalse(text.contains("http"))
    }

    @Test fun sampleWindowDropsOldEntriesAndCapsPoints() {
        val json = sample()
        Assume.assumeTrue("the sample file is not on the test classpath", json != null)
        // two hours later: entries before one hour ago (13:00) are gone, 13:00 is kept
        val late = WeatherParser.parse(json!!, "k", 12, t0 + 2 * hour)
        assertEquals(7, late!!.points.size)
        assertEquals(t0 + hour, late.points[0].timeMs)
        val capped = WeatherParser.parse(json, "k", 3, t0)
        assertEquals(3, capped!!.points.size)
        assertEquals(t0 + 2 * hour, capped.points[2].timeMs)
        assertNull(WeatherParser.parse(json, "k", 12, t0 + 30 * hour))
        assertEquals(1, WeatherParser.parse(json, "k", 0, t0)!!.points.size)
    }

    private fun entry(
        time: String = "2026-11-25T12:00:00Z", temp: String? = "4.2", wind: String? = "5.1", dir: String? = "250", cloud: String? = "88.3",
        next1: String? = """"next_1_hours":{"summary":{"symbol_code":"lightrain"},"details":{"precipitation_amount":0.4}}""",
        next6: String? = null,
    ): String {
        val details = listOfNotNull(
            temp?.let { """"air_temperature":$it""" }, wind?.let { """"wind_speed":$it""" },
            dir?.let { """"wind_from_direction":$it""" }, cloud?.let { """"cloud_area_fraction":$it""" },
        ).joinToString(",")
        val blocks = listOfNotNull(next1, next6).joinToString(",")
        return """{"time":"$time","data":{"instant":{"details":{$details}}${if (blocks.isEmpty()) "" else ",$blocks"}}}"""
    }

    private fun doc(vararg entries: String) = """{"properties":{"timeseries":[${entries.joinToString(",")}]}}"""

    @Test fun missingFieldsSkipEntry() {
        val good = entry()
        for (bad in listOf(
            entry(temp = null), entry(wind = null), entry(dir = null), entry(cloud = null),
            entry(next1 = null, next6 = null), entry(time = "yesterday"), entry(next1 = """"next_1_hours":{"details":{}}"""),
        )) {
            val s = WeatherParser.parse(doc(bad, good), "k", 12, t0)
            assertEquals(bad, 1, s!!.points.size)
        }
        assertNull(WeatherParser.parse(doc(entry(temp = null)), "k", 12, t0))
        assertNull(WeatherParser.parse("""{"properties":{"timeseries":[]}}""", "k", 12, t0))
        assertNull(WeatherParser.parse("""{"properties":{}}""", "k", 12, t0))
        assertNull(WeatherParser.parse("""{}""", "k", 12, t0))
    }

    @Test fun sixHourBlockIsUsedAndDividedWhenNoOneHourBlock() {
        val e = entry(next1 = null, next6 = """"next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":12.0}}""")
        val p = WeatherParser.parse(doc(e), "k", 12, t0)!!.points[0]
        assertEquals(2.0, p.precipMm, 1e-9)
        assertEquals(WeatherCondition.HEAVY_RAIN, p.condition)
        // a one hour block wins over the six hour block
        val both = entry(next6 = """"next_6_hours":{"summary":{"symbol_code":"snow"},"details":{"precipitation_amount":12.0}}""")
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherParser.parse(doc(both), "k", 12, t0)!!.points[0].condition)
        // no precipitation amount at all: zero
        val none = entry(next1 = """"next_1_hours":{"summary":{"symbol_code":"cloudy"}}""")
        assertEquals(0.0, WeatherParser.parse(doc(none), "k", 12, t0)!!.points[0].precipMm, 0.0)
    }

    @Test fun unsortedEntriesAreSorted() {
        val a = entry(time = "2026-11-25T14:00:00Z")
        val b = entry(time = "2026-11-25T12:00:00Z")
        val c = entry(time = "2026-11-25T13:00:00Z")
        val s = WeatherParser.parse(doc(a, b, c), "k", 12, t0)!!
        assertEquals(listOf(t0, t0 + hour, t0 + 2 * hour), s.points.map { it.timeMs })
    }

    @Test fun cloudPercentIsClamped() {
        val s = WeatherParser.parse(doc(entry(cloud = "104.6")), "k", 12, t0)!!
        assertEquals(100, s.points[0].cloudPercent)
    }

    @Test fun brokenJsonGivesNull() {
        assertNull(WeatherParser.parse("", "k", 12, t0))
        assertNull(WeatherParser.parse("not json", "k", 12, t0))
        assertNull(WeatherParser.parse("{\"properties\":", "k", 12, t0))
        assertNull(WeatherParser.parse("[1,2,3]", "k", 12, t0))
        assertNull(WeatherSeries.fromJson("garbage"))
        assertNull(WeatherSeries.fromJson("{}"))
        assertNull(WeatherCacheJson.fromJson("garbage"))
        assertNull(WeatherCacheJson.fromJson("{\"v\":2,\"place\":\"1.0,1.0\"}"))
    }

    @Test fun conditionTable() {
        fun c(code: String, mm: Double? = null) = WeatherParser.conditionOf(code, mm)
        // clear, fair, cloud
        assertEquals(Pair(WeatherCondition.CLEAR, false), c("clearsky_day"))
        assertEquals(Pair(WeatherCondition.CLEAR, false), c("clearsky_night"))
        assertEquals(Pair(WeatherCondition.CLEAR, false), c("clearsky_polartwilight"))
        assertEquals(Pair(WeatherCondition.FAIR, false), c("fair_day"))
        assertEquals(Pair(WeatherCondition.FAIR, false), c("fair_polartwilight"))
        assertEquals(Pair(WeatherCondition.PARTLY_CLOUDY, false), c("partlycloudy_night"))
        assertEquals(Pair(WeatherCondition.PARTLY_CLOUDY, false), c("partlycloudy_day"))
        assertEquals(Pair(WeatherCondition.CLOUDY, false), c("cloudy"))
        assertEquals(Pair(WeatherCondition.FOG, false), c("fog"))
        // rain families
        assertEquals(Pair(WeatherCondition.LIGHT_RAIN, false), c("lightrain"))
        assertEquals(Pair(WeatherCondition.LIGHT_RAIN, false), c("lightrainshowers_day"))
        assertEquals(Pair(WeatherCondition.LIGHT_RAIN, true), c("lightrainandthunder"))
        assertEquals(Pair(WeatherCondition.LIGHT_RAIN, true), c("lightrainshowersandthunder_night"))
        assertEquals(Pair(WeatherCondition.HEAVY_RAIN, false), c("heavyrain"))
        assertEquals(Pair(WeatherCondition.HEAVY_RAIN, false), c("heavyrainshowers_polartwilight"))
        assertEquals(Pair(WeatherCondition.HEAVY_RAIN, true), c("heavyrainandthunder"))
        assertEquals(Pair(WeatherCondition.HEAVY_RAIN, true), c("heavyrainshowersandthunder_day"))
        // snow families
        assertEquals(Pair(WeatherCondition.LIGHT_SNOW, false), c("lightsnow"))
        assertEquals(Pair(WeatherCondition.LIGHT_SNOW, false), c("lightsnowshowers_night"))
        assertEquals(Pair(WeatherCondition.HEAVY_SNOW, false), c("heavysnow"))
        assertEquals(Pair(WeatherCondition.HEAVY_SNOW, true), c("heavysnowandthunder"))
        assertEquals(Pair(WeatherCondition.HEAVY_SNOW, true), c("heavysnowshowersandthunder_day"))
        // sleet is one value, with MET's own typos
        assertEquals(Pair(WeatherCondition.SLEET, false), c("sleet"))
        assertEquals(Pair(WeatherCondition.SLEET, false), c("lightsleet"))
        assertEquals(Pair(WeatherCondition.SLEET, false), c("heavysleetshowers_day"))
        assertEquals(Pair(WeatherCondition.SLEET, true), c("sleetandthunder"))
        assertEquals(Pair(WeatherCondition.SLEET, true), c("lightssleetandthunder"))
        assertEquals(Pair(WeatherCondition.SLEET, true), c("heavysleetshowersandthunder_night"))
        assertEquals(Pair(WeatherCondition.LIGHT_SNOW, true), c("lightssnowandthunder"))
        assertEquals(Pair(WeatherCondition.LIGHT_SNOW, true), c("lightssnowshowersandthunder_day"))
        // unknown gives CLOUDY, case and space are forgiven
        assertEquals(Pair(WeatherCondition.CLOUDY, false), c("something_new"))
        assertEquals(Pair(WeatherCondition.CLOUDY, false), c(""))
        assertEquals(Pair(WeatherCondition.LIGHT_RAIN, false), c(" LightRain_Day "))
    }

    @Test fun plainRainStrengthByAmount() {
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherParser.conditionOf("rain", 0.9).first)
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherParser.conditionOf("rain", 1.0).first)
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherParser.conditionOf("rain", null).first)
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherParser.conditionOf("rainshowers_day", 0.0).first)
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherParser.conditionOf("rainshowers_day", 2.4).first)
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherParser.conditionOf("snow", 0.9).first)
        assertEquals(WeatherCondition.HEAVY_SNOW, WeatherParser.conditionOf("snow", 1.0).first)
        // a prefix wins over the amount
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherParser.conditionOf("lightrain", 9.0).first)
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherParser.conditionOf("heavyrain", 0.0).first)
    }

    @Test fun lookFromFirstPoint() {
        val json = sample()
        Assume.assumeTrue("the sample file is not on the test classpath", json != null)
        val first = WeatherParser.parse(json!!, "k", 12, t0)!!.points[0]
        val look = first.toLook()
        assertEquals(CloudCover.HEAVY, look.cloud) // 88 per cent
        assertEquals(Precip.RAIN_LIGHT, look.precip)
        assertEquals(FogLevel.NONE, look.fog)
        // m = (5.1 - 1) / 13 = 0.315, east = -sin(250 deg) = 0.94, factor 0.97: 0.306, quantised 0.3
        assertEquals(0.3f, look.wind, 1e-6f)
        assertFalse(look.thunder)
        assertEquals(0, look.flash)
    }

    @Test fun toLookTable() {
        // cloud by percent
        assertEquals(CloudCover.NONE, point(cloud = 19, condition = WeatherCondition.CLEAR).toLook().cloud)
        assertEquals(CloudCover.LIGHT, point(cloud = 20, condition = WeatherCondition.FAIR).toLook().cloud)
        assertEquals(CloudCover.LIGHT, point(cloud = 49).toLook().cloud)
        assertEquals(CloudCover.MEDIUM, point(cloud = 50).toLook().cloud)
        assertEquals(CloudCover.MEDIUM, point(cloud = 84).toLook().cloud)
        assertEquals(CloudCover.HEAVY, point(cloud = 85).toLook().cloud)
        // precipitation by condition
        assertEquals(Precip.RAIN_LIGHT, point(condition = WeatherCondition.LIGHT_RAIN).toLook().precip)
        assertEquals(Precip.RAIN_HEAVY, point(condition = WeatherCondition.RAIN).toLook().precip)
        assertEquals(Precip.RAIN_HEAVY, point(condition = WeatherCondition.HEAVY_RAIN).toLook().precip)
        assertEquals(Precip.SLEET, point(condition = WeatherCondition.SLEET).toLook().precip)
        assertEquals(Precip.SNOW_LIGHT, point(condition = WeatherCondition.LIGHT_SNOW).toLook().precip)
        assertEquals(Precip.SNOW_HEAVY, point(condition = WeatherCondition.SNOW).toLook().precip)
        assertEquals(Precip.SNOW_HEAVY, point(condition = WeatherCondition.HEAVY_SNOW).toLook().precip)
        assertEquals(Precip.NONE, point(condition = WeatherCondition.CLOUDY).toLook().precip)
        // any precipitation raises the cloud to at least MEDIUM, heavy precipitation to HEAVY
        assertEquals(CloudCover.MEDIUM, point(cloud = 5, condition = WeatherCondition.LIGHT_RAIN).toLook().cloud)
        assertEquals(CloudCover.MEDIUM, point(cloud = 5, condition = WeatherCondition.SLEET).toLook().cloud)
        assertEquals(CloudCover.MEDIUM, point(cloud = 30, condition = WeatherCondition.LIGHT_SNOW).toLook().cloud)
        assertEquals(CloudCover.MEDIUM, point(cloud = 60, condition = WeatherCondition.LIGHT_RAIN).toLook().cloud)
        assertEquals(CloudCover.HEAVY, point(cloud = 5, condition = WeatherCondition.HEAVY_RAIN).toLook().cloud)
        assertEquals(CloudCover.HEAVY, point(cloud = 5, condition = WeatherCondition.HEAVY_SNOW).toLook().cloud)
        // a clear point is clear
        assertTrue(point(cloud = 5, condition = WeatherCondition.CLEAR).toLook().isClear)
    }

    @Test fun thunderRaisesCloud() {
        val look = point(cloud = 5, condition = WeatherCondition.CLEAR, thunder = true).toLook()
        assertEquals(CloudCover.HEAVY, look.cloud)
        assertTrue(look.thunder)
        assertFalse(point(cloud = 5, condition = WeatherCondition.CLEAR).toLook().thunder)
    }

    @Test fun fogDenseOnlyWhenCalm() {
        assertEquals(FogLevel.DENSE, point(condition = WeatherCondition.FOG, windMs = 1.99).toLook().fog)
        assertEquals(FogLevel.LIGHT, point(condition = WeatherCondition.FOG, windMs = 2.0).toLook().fog)
        assertEquals(FogLevel.LIGHT, point(condition = WeatherCondition.FOG, windMs = 6.0).toLook().fog)
        assertEquals(FogLevel.NONE, point(condition = WeatherCondition.CLOUDY, windMs = 0.0).toLook().fog)
    }

    @Test fun windSignFromDirection() {
        val west = point(windMs = 8.8, from = 270.0).toLook().wind
        val east = point(windMs = 8.8, from = 90.0).toLook().wind
        val north = point(windMs = 8.8, from = 0.0).toLook().wind
        val south = point(windMs = 8.8, from = 180.0).toLook().wind
        assertEquals(0.6f, west, 1e-6f)
        assertEquals(-0.6f, east, 1e-6f)
        assertTrue(north > 0f && north < west)
        assertEquals(0.3f, north, 1e-6f)
        assertEquals(0.3f, south, 1e-6f) // exactly south is not decided by floating point noise: positive, like north
        assertTrue(south > 0f && south < west)
        // calm and strong
        assertEquals(0f, point(windMs = 1.0, from = 270.0).toLook().wind, 0f)
        assertEquals(0f, point(windMs = 0.0, from = 90.0).toLook().wind, 0f)
        assertEquals(1f, point(windMs = 40.0, from = 270.0).toLook().wind, 1e-6f)
        assertEquals(-1f, point(windMs = 40.0, from = 90.0).toLook().wind, 1e-6f)
        // never negative zero
        val z = point(windMs = 1.1, from = 90.0).toLook().wind
        assertEquals(0f, z, 0f)
        assertEquals(Float.POSITIVE_INFINITY, 1f / z, 0f)
    }

    @Test fun atPicksLatestPointAndExpires() {
        val pts = listOf(
            point(time = t0, temp = 1.0), point(time = t0 + hour, temp = 2.0), point(time = t0 + 2 * hour, temp = 3.0),
        )
        val s = WeatherSeries("k", pts)
        assertNull(s.at(t0 - 1))
        assertEquals(1.0, s.at(t0)!!.temperatureC, 0.0)
        assertEquals(1.0, s.at(t0 + hour - 1)!!.temperatureC, 0.0)
        assertEquals(2.0, s.at(t0 + hour)!!.temperatureC, 0.0)
        assertEquals(3.0, s.at(t0 + 2 * hour + 1_800_000L)!!.temperatureC, 0.0)
        // the last point is good for two hours (the default gap), then the series has nothing
        assertEquals(3.0, s.at(t0 + 4 * hour)!!.temperatureC, 0.0)
        assertNull(s.at(t0 + 4 * hour + 1))
        // a wider gap
        assertNotNull(s.at(t0 + 9 * hour, 8 * hour))
        assertNull(WeatherSeries("k", emptyList()).at(t0))
    }

    @Test fun jsonRoundTrip() {
        val pts = listOf(
            WeatherPoint(t0, 4.2, 5.1, 250.0, 88, 0.4, WeatherCondition.LIGHT_RAIN, false),
            WeatherPoint(t0 + hour, -3.5, 12.25, 10.5, 0, 6.0, WeatherCondition.HEAVY_SNOW, true),
            WeatherPoint(t0 + 2 * hour, 0.0, 0.0, 0.0, 100, 0.0, WeatherCondition.FOG, false),
        )
        val s = WeatherSeries("59.9,10.7", pts)
        val back = WeatherSeries.fromJson(s.toJson())
        assertNotNull(back)
        assertEquals("59.9,10.7", back!!.placeKey)
        assertEquals(pts, back.points)
        // an entry that cannot be read is skipped, the rest is kept
        val damaged = """{"place":"k","points":[{"t":1,"c":1.0,"w":1.0,"d":1.0,"k":1,"p":0.0,"s":"NOPE","h":false},{"t":2,"c":1.0,"w":1.0,"d":1.0,"k":1,"p":0.0,"s":"CLEAR","h":false},{"t":3}]}"""
        val d = WeatherSeries.fromJson(damaged)!!
        assertEquals(1, d.points.size)
        assertEquals(2L, d.points[0].timeMs)
    }

    @Test fun cacheJsonRoundTrip() {
        val series = WeatherSeries("59.9,10.7", listOf(WeatherPoint(t0, 4.2, 5.1, 250.0, 88, 0.4, WeatherCondition.LIGHT_RAIN, false)))
        val c = WeatherCache("59.9,10.7", series, t0, t0 + hour, t0 + 5, 2, t0 + 7 * hour, "Wed, 25 Nov 2026 11:41:00 GMT")
        val back = WeatherCacheJson.fromJson(WeatherCacheJson.toJson(c))!!
        assertEquals("59.9,10.7", back.placeKey)
        assertEquals(c.fetchedAtMs, back.fetchedAtMs)
        assertEquals(c.expiresAtMs, back.expiresAtMs)
        assertEquals(c.lastAttemptMs, back.lastAttemptMs)
        assertEquals(2, back.failures)
        assertEquals(c.blockedUntilMs, back.blockedUntilMs)
        assertEquals("Wed, 25 Nov 2026 11:41:00 GMT", back.lastModified)
        assertEquals(series.points, back.series!!.points)
        // without series and header
        val bare = WeatherCache("0.0,0.0", null, 0L, 0L, 5L, 1, 0L, null)
        val b2 = WeatherCacheJson.fromJson(WeatherCacheJson.toJson(bare))!!
        assertNull(b2.series)
        assertNull(b2.lastModified)
        assertEquals(1, b2.failures)
        // the file holds the rounded place and numbers, nothing else of the phone
        val text = WeatherCacheJson.toJson(c)
        assertTrue(text.contains("\"v\":1"))
        assertTrue(text.contains("\"place\":\"59.9,10.7\""))
    }

    @Test fun temperatureText() {
        assertEquals("0", formatTemperature(-0.4, false))
        assertEquals("12", formatTemperature(11.5, false))
        assertEquals("-4", formatTemperature(-3.5, false))
        assertEquals("11", formatTemperature(11.4, false))
        assertEquals("0", formatTemperature(0.0, false))
        assertEquals("-1", formatTemperature(-0.6, false))
        assertEquals("52", formatTemperature(11.0, true))
        assertEquals("21", formatTemperature(-6.0, true))
        assertEquals("32", formatTemperature(0.0, true))
        assertEquals("0", formatTemperature(-17.9, true))
        assertEquals("0", formatTemperature(Double.NaN, false))
    }

    @Test fun fahrenheitRegions() {
        for (cc in listOf("US", "LR", "BS", "KY", "BZ", "PW", "FM", "MH", "us")) assertTrue(cc, usesFahrenheit(cc))
        for (cc in listOf("TR", "NO", "FR", "GB", "CA", "", "USA")) assertFalse(cc, usesFahrenheit(cc))
    }
}
