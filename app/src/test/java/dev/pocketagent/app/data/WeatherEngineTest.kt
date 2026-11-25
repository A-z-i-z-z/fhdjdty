package dev.pocketagent.app.data

import dev.pocketagent.app.data.WeatherTestRig.Companion.HOUR
import dev.pocketagent.app.data.WeatherTestRig.Companion.MIN
import dev.pocketagent.app.data.WeatherTestRig.Companion.OSLO
import dev.pocketagent.app.data.WeatherTestRig.Companion.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEngineTest {
    private val contact = "https://example.org/agiri"

    private class Recorder(val reply: HttpReply) : HttpTransport {
        var url: String? = null
        var headers: Map<String, String>? = null
        var calls = 0
        override fun get(url: String, headers: Map<String, String>): HttpReply {
            calls++
            this.url = url
            this.headers = headers
            return reply
        }
    }

    private fun okReply(vararg headers: Pair<String, String>) =
        HttpReply(200, mapOf(*headers), WeatherTestRig.bodyAt(T0))

    @Test fun requestShape() {
        val rec = Recorder(okReply())
        val r = WeatherClient.fetch(rec, OSLO, contact, "0.2.17", null, T0)
        assertTrue(r is FetchResult.Fresh)
        assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=59.9&lon=10.7", rec.url)
        val h = rec.headers!!
        assertEquals(setOf("User-Agent", "Accept"), h.keys)
        assertEquals("Agiri/0.2.17 $contact", h["User-Agent"])
        assertEquals("application/json", h["Accept"])
        assertEquals(1, rec.calls)
        // with a Last-Modified to ask against
        val rec2 = Recorder(okReply())
        WeatherClient.fetch(rec2, OSLO, contact, "0.2.17", "Wed, 25 Nov 2026 11:41:00 GMT", T0)
        assertEquals(setOf("User-Agent", "Accept", "If-Modified-Since"), rec2.headers!!.keys)
        assertEquals("Wed, 25 Nov 2026 11:41:00 GMT", rec2.headers!!["If-Modified-Since"])
    }

    @Test fun requestThroughTheEngineHasTheSameShape() {
        val rig = WeatherTestRig()
        rig.refresh(OSLO)
        assertEquals(listOf("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=59.9&lon=10.7"), rig.urls)
        assertEquals(setOf("User-Agent", "Accept"), rig.headers[0].keys)
        assertEquals("Agiri/0.2.17 ${rig.contact}", rig.headers[0]["User-Agent"])
    }

    @Test fun urlHasOnlyTheRoundedPlace() {
        assertEquals(WeatherClient.ENDPOINT + "?lat=59.9&lon=10.7", WeatherClient.url(OSLO))
        assertEquals(WeatherClient.ENDPOINT + "?lat=-33.9&lon=151.2", WeatherClient.url(Place(-33.87, 151.22, "Sydney", PlaceSource.CITY, "AU")))
        assertEquals(WeatherClient.ENDPOINT + "?lat=0.0&lon=0.0", WeatherClient.url(Place(-0.04, -0.01, "Null", PlaceSource.PINNED)))
        assertEquals(WeatherClient.ENDPOINT + "?lat=10.8&lon=-10.8", WeatherClient.url(Place(10.75, -10.75, "Rounded", PlaceSource.PINNED)))
        val u = WeatherClient.url(OSLO)
        assertEquals(1, u.count { it == '?' })
        assertEquals(1, u.count { it == '&' })
        assertTrue(!u.contains("Oslo"))
        assertTrue(WeatherClient.ENDPOINT.startsWith("https://"))
    }

    @Test fun userAgentSanitisesVersion() {
        assertEquals("Agiri/0.2.17-local1 c@d.ef", WeatherClient.userAgent("Agiri", "0.2.17-local1", "c@d.ef"))
        assertEquals("Agiri/a_b c@d.ef", WeatherClient.userAgent("Agiri", "a b", "c@d.ef"))
        assertEquals("Agiri/1.0_beta_x c@d.ef", WeatherClient.userAgent("Agiri", "1.0+beta/x", "c@d.ef"))
        assertEquals("Agiri/_ c@d.ef", WeatherClient.userAgent("Agiri", "é", "c@d.ef"))
        assertEquals("Agiri/0.2.17_debug c@d.ef", WeatherClient.userAgent("Agiri", "0.2.17\tdebug", "c@d.ef"))
        assertEquals("A_b/1 c@d.ef", WeatherClient.userAgent("A b", "1", "c@d.ef"))
    }

    @Test fun expiresParsing() {
        fun expiresOf(vararg headers: Pair<String, String>): Long {
            val r = WeatherClient.fetch(Recorder(okReply(*headers)), OSLO, contact, "1", null, T0)
            return (r as FetchResult.Fresh).expiresAtMs
        }
        // RFC 1123
        assertEquals(T0 + 30 * MIN, expiresOf("expires" to "Wed, 25 Nov 2026 12:30:00 GMT"))
        assertEquals(T0 + 2 * HOUR, expiresOf("expires" to WeatherTestRig.httpDate(T0 + 2 * HOUR)))
        // the name of the header is matched without regard to case
        assertEquals(T0 + 30 * MIN, expiresOf("Expires" to "Wed, 25 Nov 2026 12:30:00 GMT"))
        // missing, or not a date: one hour from now
        assertEquals(T0 + HOUR, expiresOf())
        assertEquals(T0 + HOUR, expiresOf("expires" to "soon"))
        assertEquals(T0 + HOUR, expiresOf("expires" to ""))
        assertEquals(T0 + HOUR, expiresOf("expires" to "2026-11-25T12:30:00Z"))
        // Last-Modified is kept as the raw text
        val r = WeatherClient.fetch(Recorder(okReply("last-modified" to "Wed, 25 Nov 2026 11:41:00 GMT")), OSLO, contact, "1", null, T0)
        assertEquals("Wed, 25 Nov 2026 11:41:00 GMT", (r as FetchResult.Fresh).lastModified)
        assertNull((WeatherClient.fetch(Recorder(okReply()), OSLO, contact, "1", null, T0) as FetchResult.Fresh).lastModified)
    }

    @Test fun replyCodes() {
        fun code(c: Int, body: String? = WeatherTestRig.bodyAt(T0)): FetchResult =
            WeatherClient.fetch(Recorder(HttpReply(c, emptyMap(), body)), OSLO, contact, "1", null, T0)
        assertTrue(code(200) is FetchResult.Fresh)
        assertTrue(code(203) is FetchResult.Fresh)
        assertEquals(FetchResult.Failed(200, 0L), code(200, "nonsense"))
        assertEquals(FetchResult.Failed(203, 0L), code(203, null))
        assertEquals(FetchResult.NotModified(T0 + HOUR, null), code(304, null))
        assertEquals(FetchResult.Failed(403, 24 * HOUR), code(403))
        assertEquals(FetchResult.Failed(429, HOUR), code(429))
        assertEquals(FetchResult.Failed(400, 6 * HOUR), code(400))
        assertEquals(FetchResult.Failed(404, 6 * HOUR), code(404))
        assertEquals(FetchResult.Failed(499, 6 * HOUR), code(499))
        assertEquals(FetchResult.Failed(500, 0L), code(500))
        assertEquals(FetchResult.Failed(503, 0L), code(503))
        assertEquals(FetchResult.Failed(301, 0L), code(301))
        assertEquals(FetchResult.Failed(0, 0L), WeatherClient.fetch({ _, _ -> throw java.io.IOException("x") }, OSLO, contact, "1", null, T0))
        assertEquals(FetchResult.Failed(0, 0L), WeatherClient.fetch({ _, _ -> throw IllegalStateException("x") }, OSLO, contact, "1", null, T0))
    }

    @Test fun notModifiedKeepsTheAskedForDateWhenTheServerSendsNone() {
        val r = WeatherClient.fetch(Recorder(HttpReply(304, emptyMap(), null)), OSLO, contact, "1", "Wed, 25 Nov 2026 11:41:00 GMT", T0)
        assertEquals(FetchResult.NotModified(T0 + HOUR, "Wed, 25 Nov 2026 11:41:00 GMT"), r)
    }

    @Test fun parsedSeriesCarriesThePlaceKey() {
        val r = WeatherClient.fetch(Recorder(okReply()), OSLO, contact, "1", null, T0) as FetchResult.Fresh
        assertEquals("59.9,10.7", r.series.placeKey)
        assertEquals(6, r.series.points.size)
    }

    @Test fun disabledOrBackgroundNoCall() {
        val rig = WeatherTestRig()
        assertEquals(RefreshOutcome.Skipped(WaitReason.DISABLED, 0L), rig.refresh(OSLO, enabled = false))
        assertEquals(RefreshOutcome.Skipped(WaitReason.BACKGROUND, 0L), rig.refresh(OSLO, foreground = false))
        assertEquals(RefreshOutcome.Skipped(WaitReason.DISABLED, 0L), rig.refresh(OSLO, enabled = false, foreground = false))
        assertEquals(0, rig.times.size)
        assertEquals(0, rig.storage.saves)
        // and once both are fine, the request goes
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        assertEquals(1, rig.times.size)
    }

    @Test fun forgetClearsStorage() {
        val rig = WeatherTestRig()
        rig.refresh(OSLO)
        assertNotNull(rig.storage.saved)
        assertNotNull(rig.engine.current(OSLO, T0))
        rig.engine.forget()
        assertNull(rig.storage.saved)
        assertEquals(1, rig.storage.clears)
        assertNull(rig.engine.current(OSLO, T0))
        // forgotten means forgotten: the next call asks again at once, though the old answer was fresh
        rig.now = T0 + MIN
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        assertEquals(2, rig.times.size)
    }

    @Test fun forgetAlsoForgetsTheRequestList() {
        val rig = WeatherTestRig()
        for (i in 0 until 6) {
            rig.now = T0 + i * MIN
            rig.refresh(WeatherTestRig.placeAt(10.0 + 2 * i, 20.0))
        }
        rig.now = T0 + 6 * MIN
        assertEquals(WaitReason.BUDGET, (rig.refresh(WeatherTestRig.placeAt(60.0, 20.0)) as RefreshOutcome.Skipped).reason)
        rig.engine.forget()
        assertEquals(RefreshOutcome.Fetched, rig.refresh(WeatherTestRig.placeAt(60.0, 20.0)))
    }

    @Test fun storedCacheIsUsedWithoutAnyRequest() {
        val rig = WeatherTestRig()
        val series = WeatherParser.parse(WeatherTestRig.bodyAt(T0), OSLO.key(), 12, T0)!!
        rig.storage.saved = WeatherCache(OSLO.key(), series, T0, T0 + 30 * MIN, T0, 0, 0L, null)
        // the engine reads the stored cache on its first call and shows it
        assertNotNull(rig.engine.current(OSLO, T0 + 5 * MIN))
        // and the policy knows it is fresh
        rig.now = T0 + 10 * MIN
        val out = rig.refresh(OSLO) as RefreshOutcome.Skipped
        assertEquals(WaitReason.FRESH, out.reason)
        assertEquals(T0 + HOUR, out.untilMs)
        assertEquals(0, rig.times.size)
    }

    @Test fun blockedCacheOnDiskStillBlocks() {
        val rig = WeatherTestRig()
        rig.storage.saved = WeatherCache(OSLO.key(), null, 0L, 0L, T0 - MIN, 1, T0 + 5 * HOUR, null)
        rig.now = T0
        assertEquals(RefreshOutcome.Skipped(WaitReason.BLOCKED, T0 + 5 * HOUR), rig.refresh(OSLO))
        assertEquals(0, rig.times.size)
    }

    @Test fun brokenStorageDoesNotBreakTheEngine() {
        val broken = object : WeatherStorage {
            override fun load(): WeatherCache? = throw IllegalStateException("disk")
            override fun save(c: WeatherCache) { throw IllegalStateException("disk") }
            override fun clear() { throw IllegalStateException("disk") }
        }
        val now = T0
        val engine = WeatherEngine({ _, _ -> HttpReply(200, emptyMap(), WeatherTestRig.bodyAt(now)) }, broken, contact, "1") { now }
        assertEquals(RefreshOutcome.Fetched, engine.refreshIfDue(true, true, OSLO))
        assertNotNull(engine.current(OSLO, now))
        engine.forget()
        assertNull(engine.current(OSLO, now))
    }
}
