package dev.pocketagent.app.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Shared by the weather engine and policy tests: an engine on a settable clock with an in-memory cache and a
 * transport that records every call and answers what [next] says. Nothing here touches the network or a file.
 */
internal class WeatherTestRig(val contact: String = "https://example.org/agiri", val version: String = "0.2.17") {
    class MemoryStorage : WeatherStorage {
        var saved: WeatherCache? = null
        var saves = 0
        var clears = 0
        override fun load(): WeatherCache? = saved
        override fun save(c: WeatherCache) {
            saved = c
            saves++
        }
        override fun clear() {
            saved = null
            clears++
        }
    }

    var now: Long = T0
    val times = ArrayList<Long>()
    val urls = ArrayList<String>()
    val headers = ArrayList<Map<String, String>>()
    var next: () -> HttpReply = { ok() }
    val storage = MemoryStorage()
    val transport = HttpTransport { url, h ->
        times.add(now)
        urls.add(url)
        headers.add(HashMap(h))
        next()
    }
    val engine = WeatherEngine(transport, storage, contact, version) { now }

    /** An answer 200 whose Expires is [expiresInMs] after the clock, with a body of hourly points from this hour. */
    fun ok(expiresInMs: Long = 1_800_000L, lastModified: String? = null, status: Int = 200): HttpReply {
        val h = HashMap<String, String>()
        h["expires"] = httpDate(now + expiresInMs)
        if (lastModified != null) h["last-modified"] = lastModified
        return HttpReply(status, h, bodyAt(now))
    }

    fun notModified(expiresInMs: Long = 1_800_000L): HttpReply =
        HttpReply(304, mapOf("expires" to httpDate(now + expiresInMs)), null)

    fun status(code: Int): HttpReply = HttpReply(code, emptyMap(), "")

    fun refresh(place: Place, enabled: Boolean = true, foreground: Boolean = true): RefreshOutcome =
        engine.refreshIfDue(enabled, foreground, place)

    companion object {
        /** 2026-11-25T12:00:00Z */
        const val T0 = 1_795_608_000_000L
        const val MIN = 60_000L
        const val HOUR = 3_600_000L

        val OSLO = Place(59.9496, 10.7461, "Oslo", PlaceSource.ZONE, "NO")

        fun placeAt(lat: Double, lon: Double) = Place(lat, lon, "Test", PlaceSource.PINNED)

        fun httpDate(ms: Long): String =
            DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC))

        /** A locationforecast body with six hourly entries from the start of the hour of [nowMs]. */
        fun bodyAt(nowMs: Long): String {
            val base = Math.floorDiv(nowMs, HOUR) * HOUR
            val sb = StringBuilder("""{"properties":{"timeseries":[""")
            for (i in 0 until 6) {
                if (i > 0) sb.append(',')
                val time = Instant.ofEpochMilli(base + i * HOUR).toString()
                sb.append("""{"time":"""").append(time).append("""","data":{"instant":{"details":{"air_temperature":""")
                sb.append(4 + i).append(""".5,"wind_speed":5.1,"wind_from_direction":250,"cloud_area_fraction":88.3}},""")
                sb.append(""""next_1_hours":{"summary":{"symbol_code":"lightrain"},"details":{"precipitation_amount":0.4}}}}""")
            }
            sb.append("]}}")
            return sb.toString()
        }
    }
}
