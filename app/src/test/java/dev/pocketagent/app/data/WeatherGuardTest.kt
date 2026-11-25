package dev.pocketagent.app.data

import dev.pocketagent.app.util.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WeatherGuardTest {
    private val oslo = Place(59.9496, 10.7461, "Oslo", PlaceSource.ZONE, "NO")

    private class CountingTransport : HttpTransport {
        val calls = AtomicInteger(0)
        override fun get(url: String, headers: Map<String, String>): HttpReply {
            calls.incrementAndGet()
            return HttpReply(200, emptyMap(), "{}")
        }
    }

    private class MemoryStorage : WeatherStorage {
        var saved: WeatherCache? = null
        var saves = 0
        override fun load(): WeatherCache? = saved
        override fun save(c: WeatherCache) { saved = c; saves++ }
        override fun clear() { saved = null }
    }

    @Test fun contactTable() {
        val yes = listOf("me@example.org", "https://example.org/x", "https://a.bc", "a.b-c_d+e@sub.example.co", "ME@EXAMPLE.ORG")
        val no = listOf(
            "", " ", "a", "me@example", "x@y", "http://example.org", "https://abc", "https://", "me@exa mple.org", "me@example.org\n",
            "me@example.org\t", " me@example.org", "me@example.org ", "me@@example.org", "@example.org", "me@.org", "me@example..org",
            "me@example.o", "me@example.org.", "ftp://example.org", "me example.org", "https://exa mple.org", "me@exämple.org",
            "https://example.org/\u0000", "https://example.org/\u007f", "me@-example.org",
        )
        for (c in yes) assertTrue("should be ok: $c", WeatherGuard.contactOk(c))
        for (c in no) assertFalse("should not be ok: " + c.replace("\n", "\\n"), WeatherGuard.contactOk(c))
        // length: 120 is the limit
        val url120 = "https://" + "a".repeat(112)
        assertEquals(120, url120.length)
        assertTrue(WeatherGuard.contactOk(url120))
        assertFalse(WeatherGuard.contactOk(url120 + "a"))
        assertFalse(WeatherGuard.contactOk("a".repeat(121)))
        // 6 is the shortest
        assertTrue(WeatherGuard.contactOk("a@b.co"))
        assertFalse(WeatherGuard.contactOk("a@b.c"))
    }

    @Test fun emptyContactNeverCallsTransport() {
        val tr = CountingTransport()
        for (contact in listOf("", " ", "nope", "x@y", "http://example.org")) {
            val r = WeatherClient.fetch(tr, oslo, contact, "0.2.17", null, 1_000L)
            assertTrue(contact, r === FetchResult.Blocked)
        }
        assertEquals(0, tr.calls.get())
    }

    @Test fun engineWithEmptyContactNeverCallsTransport() {
        val tr = CountingTransport()
        val st = MemoryStorage()
        var now = 1_795_608_000_000L
        val engine = WeatherEngine(tr, st, "", "0.2.17") { now }
        for (i in 0 until 100) {
            now += i * 7 * 60_000L
            val out = engine.refreshIfDue(enabled = true, foreground = true, place = oslo)
            assertTrue("call $i: $out", out is RefreshOutcome.Skipped && out.reason == WaitReason.NO_CONTACT)
        }
        assertEquals(0, tr.calls.get())
        assertEquals(0, st.saves)
        assertNull(st.saved)
        // a bad contact is as good as none, even with every other condition met
        val bad = WeatherEngine(tr, st, "me@localhost", "0.2.17") { now }
        for (i in 0 until 10) {
            val out = bad.refreshIfDue(true, true, oslo)
            assertEquals(RefreshOutcome.Skipped(WaitReason.NO_CONTACT, 0L), out)
        }
        assertEquals(0, tr.calls.get())
        assertNull(engine.current(oslo, now))
    }

    @Test fun contactIsNeverInTheLogOrTheCacheFile() {
        val st = MemoryStorage()
        val tr = HttpTransport { _, _ -> HttpReply(200, mapOf("expires" to "Wed, 25 Nov 2026 13:00:00 GMT"), SAMPLE_ONE) }
        val engine = WeatherEngine(tr, st, "https://example.org/agiri", "0.2.17") { 1_795_608_000_000L }
        assertEquals(RefreshOutcome.Fetched, engine.refreshIfDue(true, true, oslo))
        val json = WeatherCacheJson.toJson(st.saved!!)
        assertFalse(json.contains("example.org"))
        assertFalse(json.contains("Agiri"))
        assertTrue(json.contains("59.9,10.7"))
    }

    @Test fun contactIsNeverInTheLogLines() {
        val seen = ArrayList<String>()
        val old = AppLog.redactor
        AppLog.redactor = { seen.add(it); it }
        try {
            val contact = "https://example.org/agiri"
            val okTransport = HttpTransport { _, _ -> HttpReply(200, mapOf("expires" to "Wed, 25 Nov 2026 13:00:00 GMT"), SAMPLE_ONE) }
            val okEngine = WeatherEngine(okTransport, MemoryStorage(), contact, "0.2.17") { 1_795_608_000_000L }
            assertEquals(RefreshOutcome.Fetched, okEngine.refreshIfDue(true, true, oslo))
            // a skip (the switch is off) is logged too
            okEngine.refreshIfDue(false, true, oslo)
            val failTransport = HttpTransport { _, _ -> HttpReply(503, emptyMap(), "") }
            val failEngine = WeatherEngine(failTransport, MemoryStorage(), contact, "0.2.17") { 1_795_608_000_000L }
            assertTrue(failEngine.refreshIfDue(true, true, oslo) is RefreshOutcome.Failed)
        } finally {
            AppLog.redactor = old
        }
        assertTrue("the engine logged nothing at all", seen.isNotEmpty())
        assertTrue(seen.toString(), seen.none { it.contains("example.org") || it.contains("Agiri/") || it.contains("agiri") })
        assertTrue(seen.toString(), seen.any { it.contains("request 59.9,10.7") })
        assertTrue(seen.toString(), seen.any { it.contains("answer failed code 503") })
    }

    private companion object {
        const val SAMPLE_ONE =
            """{"properties":{"timeseries":[{"time":"2026-11-25T12:00:00Z","data":{"instant":{"details":{"air_temperature":4.2,"wind_speed":5.1,"wind_from_direction":250,"cloud_area_fraction":88.3}},"next_1_hours":{"summary":{"symbol_code":"lightrain"},"details":{"precipitation_amount":0.4}}}}]}}"""
    }
}
