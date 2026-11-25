package dev.pocketagent.app.data

import dev.pocketagent.app.data.WeatherTestRig.Companion.HOUR
import dev.pocketagent.app.data.WeatherTestRig.Companion.MIN
import dev.pocketagent.app.data.WeatherTestRig.Companion.OSLO
import dev.pocketagent.app.data.WeatherTestRig.Companion.T0
import dev.pocketagent.app.data.WeatherTestRig.Companion.placeAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WeatherPolicyTest {
    private fun cache(
        key: String = "k", expires: Long = 0L, attempt: Long = 0L, failures: Int = 0, blocked: Long = 0L, fetched: Long = attempt,
    ) = WeatherCache(key, null, fetched, expires, attempt, failures, blocked, null)

    private fun decide(
        now: Long = T0, enabled: Boolean = true, contact: Boolean = true, fg: Boolean = true, key: String = "k",
        cache: WeatherCache? = null, reqs: List<Long> = emptyList(),
    ) = WeatherPolicy.decide(now, enabled, contact, fg, key, cache, reqs)

    @Test fun decideOrder() {
        // each reason alone
        assertEquals(Decision.Wait(WaitReason.DISABLED, 0L), decide(enabled = false))
        assertEquals(Decision.Wait(WaitReason.NO_CONTACT, 0L), decide(contact = false))
        assertEquals(Decision.Wait(WaitReason.BACKGROUND, 0L), decide(fg = false))
        assertEquals(Decision.Wait(WaitReason.BLOCKED, T0 + HOUR), decide(cache = cache(blocked = T0 + HOUR)))
        assertEquals(Decision.Go, decide())
        // in combination the first listed wins
        assertEquals(Decision.Wait(WaitReason.DISABLED, 0L), decide(enabled = false, contact = false, fg = false))
        assertEquals(Decision.Wait(WaitReason.NO_CONTACT, 0L), decide(contact = false, fg = false))
        assertEquals(Decision.Wait(WaitReason.BACKGROUND, 0L), decide(fg = false, cache = cache(blocked = T0 + HOUR)))
        val sixNow = List(6) { T0 - it * MIN }
        assertEquals(
            Decision.Wait(WaitReason.BLOCKED, T0 + 5 * MIN),
            decide(cache = cache(blocked = T0 + 5 * MIN), reqs = sixNow),
        )
        // the block is checked before the place: a block holds for every place
        assertEquals(Decision.Wait(WaitReason.BLOCKED, T0 + 5 * MIN), decide(key = "other", cache = cache(blocked = T0 + 5 * MIN)))
        // a block in the past is no block
        assertEquals(Decision.Go, decide(cache = cache(blocked = T0 - 1)))
        assertEquals(Decision.Go, decide(cache = cache(blocked = T0)))
    }

    @Test fun budgetCountsTheLastHourOnly() {
        // five requests in the last hour: still room
        assertEquals(Decision.Go, decide(reqs = List(5) { T0 - it * MIN }))
        // six: stop until the oldest leaves the window
        val six = List(6) { T0 - (50 - it * 10) * MIN } // 50, 40, 30, 20, 10, 0 minutes ago
        assertEquals(Decision.Wait(WaitReason.BUDGET, T0 - 50 * MIN + HOUR), decide(reqs = six))
        // a request exactly an hour old no longer counts
        val edge = listOf(T0 - HOUR) + List(5) { T0 - it * MIN }
        assertEquals(Decision.Go, decide(reqs = edge))
        // more than six: the wait ends when only five are left in the window
        val eight = List(8) { T0 - (56 - it * 8) * MIN }
        val d = decide(reqs = eight) as Decision.Wait
        assertEquals(WaitReason.BUDGET, d.reason)
        assertEquals(eight.sorted()[2] + HOUR, d.untilMs)
        // an unsorted list gives the same answer
        assertEquals(decide(reqs = six), decide(reqs = six.reversed()))
        // old requests are ignored
        assertEquals(Decision.Go, decide(reqs = List(10) { T0 - 2 * HOUR - it }))
    }

    @Test fun timingOfTheSamePlace() {
        val c = cache(expires = T0 + 30 * MIN, attempt = T0 - 10 * MIN)
        // fresh: before the expiry; due is the later of the expiry and an hour after the attempt
        assertEquals(Decision.Wait(WaitReason.FRESH, T0 + 50 * MIN), decide(cache = c))
        // the expiry has passed but the hour has not
        val late = cache(expires = T0 - 5 * MIN, attempt = T0 - 10 * MIN)
        assertEquals(Decision.Wait(WaitReason.HOURLY, T0 + 50 * MIN), decide(cache = late))
        // at the due time the request goes
        assertEquals(Decision.Go, decide(now = T0 + 50 * MIN, cache = late))
        assertEquals(Decision.Wait(WaitReason.HOURLY, T0 + 50 * MIN), decide(now = T0 + 50 * MIN - 1, cache = late))
        // an expiry later than an hour is honoured
        val far = cache(expires = T0 + 3 * HOUR, attempt = T0)
        assertEquals(Decision.Wait(WaitReason.FRESH, T0 + 3 * HOUR), decide(now = T0 + 2 * HOUR, cache = far))
        assertEquals(Decision.Go, decide(now = T0 + 3 * HOUR, cache = far))
        // another place goes at once, whatever the cache says
        assertEquals(Decision.Go, decide(key = "other", cache = c))
        // after failures the expiry is ignored and the backoff rules
        val failed = cache(expires = T0 + 5 * HOUR, attempt = T0 - 5 * MIN, failures = 1)
        assertEquals(Decision.Wait(WaitReason.BACKOFF, T0 + 10 * MIN), decide(cache = failed))
        assertEquals(Decision.Go, decide(now = T0 + 10 * MIN, cache = failed))
    }

    @Test fun backoffProgression() {
        val minutes = (0..8).map { WeatherPolicy.backoffMs(it) / MIN }
        assertEquals(listOf(0L, 15L, 30L, 60L, 120L, 120L, 120L, 120L, 120L), minutes)
        assertEquals(0L, WeatherPolicy.backoffMs(-3))
        assertEquals(WeatherPolicy.BACKOFF_MAX_MS, WeatherPolicy.backoffMs(1_000))
        assertEquals(WeatherPolicy.BACKOFF_MAX_MS, WeatherPolicy.backoffMs(Int.MAX_VALUE))
    }

    @Test fun engineBackoffFollowsTheProgression() {
        val rig = WeatherTestRig()
        rig.next = { rig.status(500) }
        val out = rig.refresh(OSLO)
        assertEquals(RefreshOutcome.Failed(500), out)
        for (minute in 1..360) {
            rig.now = T0 + minute * MIN
            rig.refresh(OSLO)
        }
        // 0, then +15, +30, +60, +120, +120 minutes
        assertEquals(listOf(0L, 15L, 45L, 105L, 225L, 345L), rig.times.map { (it - T0) / MIN })
        assertEquals(6, rig.storage.saved!!.failures)
    }

    @Test fun serverErrorBacksOff() {
        val rig = WeatherTestRig()
        rig.next = { rig.status(503) }
        assertEquals(RefreshOutcome.Failed(503), rig.refresh(OSLO))
        assertEquals(1, rig.storage.saved!!.failures)
        rig.now = T0 + 14 * MIN
        val out = rig.refresh(OSLO) as RefreshOutcome.Skipped
        assertEquals(WaitReason.BACKOFF, out.reason)
        assertEquals(T0 + 15 * MIN, out.untilMs)
        assertEquals(1, rig.times.size)
        // a good answer resets everything
        rig.next = { rig.ok() }
        rig.now = T0 + 15 * MIN
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        assertEquals(0, rig.storage.saved!!.failures)
        assertEquals(0L, rig.storage.saved!!.blockedUntilMs)
    }

    @Test fun exceptionIsAFailureWithCodeZero() {
        val rig = WeatherTestRig()
        rig.next = { throw IOException("offline") }
        assertEquals(RefreshOutcome.Failed(0), rig.refresh(OSLO))
        assertEquals(1, rig.storage.saved!!.failures)
        assertEquals(0L, rig.storage.saved!!.blockedUntilMs)
        // an answer that is not weather is a failure too
        val rig2 = WeatherTestRig()
        rig2.next = { HttpReply(200, emptyMap(), "<html>sorry</html>") }
        assertEquals(RefreshOutcome.Failed(200), rig2.refresh(OSLO))
        rig2.next = { HttpReply(200, emptyMap(), null) }
        rig2.now = T0 + 15 * MIN
        assertEquals(RefreshOutcome.Failed(200), rig2.refresh(OSLO))
    }

    @Test fun blocked403For24h() {
        val rig = WeatherTestRig()
        rig.next = { rig.status(403) }
        assertEquals(RefreshOutcome.Failed(403), rig.refresh(OSLO))
        assertEquals(T0 + 24 * HOUR, rig.storage.saved!!.blockedUntilMs)
        for (step in listOf(1L, 30L, 3 * 60L, 23 * 60L + 59L)) {
            rig.now = T0 + step * MIN
            val out = rig.refresh(OSLO) as RefreshOutcome.Skipped
            assertEquals(WaitReason.BLOCKED, out.reason)
            assertEquals(T0 + 24 * HOUR, out.untilMs)
        }
        assertEquals(1, rig.times.size)
        rig.now = T0 + 24 * HOUR - 1
        assertEquals(WaitReason.BLOCKED, (rig.refresh(OSLO) as RefreshOutcome.Skipped).reason)
        rig.next = { rig.ok() }
        rig.now = T0 + 24 * HOUR
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        assertEquals(2, rig.times.size)
        assertEquals(0L, rig.storage.saved!!.blockedUntilMs)
    }

    @Test fun blocked429For1h() {
        val rig = WeatherTestRig()
        rig.next = { rig.status(429) }
        assertEquals(RefreshOutcome.Failed(429), rig.refresh(OSLO))
        assertEquals(T0 + HOUR, rig.storage.saved!!.blockedUntilMs)
        rig.now = T0 + HOUR - 1
        assertEquals(WaitReason.BLOCKED, (rig.refresh(OSLO) as RefreshOutcome.Skipped).reason)
        rig.next = { rig.ok() }
        rig.now = T0 + HOUR
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
    }

    @Test fun otherClientErrorsBlockForSixHours() {
        val rig = WeatherTestRig()
        rig.next = { rig.status(404) }
        assertEquals(RefreshOutcome.Failed(404), rig.refresh(OSLO))
        assertEquals(T0 + 6 * HOUR, rig.storage.saved!!.blockedUntilMs)
        rig.now = T0 + 6 * HOUR - 1
        assertEquals(WaitReason.BLOCKED, (rig.refresh(OSLO) as RefreshOutcome.Skipped).reason)
        // a redirect is an ordinary failure, not a block
        val rig2 = WeatherTestRig()
        rig2.next = { rig2.status(301) }
        assertEquals(RefreshOutcome.Failed(301), rig2.refresh(OSLO))
        assertEquals(0L, rig2.storage.saved!!.blockedUntilMs)
    }

    @Test fun oneRequestPerPlacePerHour() {
        val rig = WeatherTestRig()
        rig.next = { rig.ok(expiresInMs = 30 * MIN) }
        var skips = 0
        for (k in 0..36) { // three hours, a tick every five minutes, both ends included
            rig.now = T0 + k * 5 * MIN
            val out = rig.refresh(OSLO)
            if (out is RefreshOutcome.Skipped) {
                skips++
                assertTrue("$out", out.reason == WaitReason.FRESH || out.reason == WaitReason.HOURLY)
            }
        }
        assertEquals(listOf(0L, 60L, 120L, 180L), rig.times.map { (it - T0) / MIN })
        for (i in 1 until rig.times.size) assertTrue(rig.times[i] - rig.times[i - 1] >= 3_600_000L)
        assertEquals(37 - 4, skips)
    }

    @Test fun expiresLaterThanHourIsHonoured() {
        val rig = WeatherTestRig()
        rig.next = { rig.ok(expiresInMs = 2 * HOUR) }
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        rig.now = T0 + HOUR
        val out = rig.refresh(OSLO) as RefreshOutcome.Skipped
        assertEquals(WaitReason.FRESH, out.reason)
        assertEquals(T0 + 2 * HOUR, out.untilMs)
        rig.now = T0 + 2 * HOUR - 1
        assertEquals(WaitReason.FRESH, (rig.refresh(OSLO) as RefreshOutcome.Skipped).reason)
        rig.now = T0 + 2 * HOUR
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        assertEquals(2, rig.times.size)
    }

    @Test fun missingExpiresMeansAnHour() {
        val rig = WeatherTestRig()
        rig.next = { HttpReply(200, emptyMap(), WeatherTestRig.bodyAt(rig.now)) }
        rig.refresh(OSLO)
        assertEquals(T0 + HOUR, rig.storage.saved!!.expiresAtMs)
        rig.next = { HttpReply(200, mapOf("expires" to "tomorrow-ish"), WeatherTestRig.bodyAt(rig.now)) }
        rig.now = T0 + HOUR
        rig.refresh(OSLO)
        assertEquals(T0 + 2 * HOUR, rig.storage.saved!!.expiresAtMs)
    }

    @Test fun placeChangeGoesButBudgetStops() {
        val rig = WeatherTestRig()
        rig.next = { rig.ok() }
        val outcomes = ArrayList<RefreshOutcome>()
        for (i in 0 until 7) {
            rig.now = T0 + i * MIN
            outcomes.add(rig.refresh(placeAt(10.0 + 2 * i, 20.0)))
        }
        assertEquals(6, rig.times.size)
        for (i in 0 until 6) assertEquals(RefreshOutcome.Fetched, outcomes[i])
        // the seventh place is held back until the first request is an hour old
        assertEquals(RefreshOutcome.Skipped(WaitReason.BUDGET, T0 + HOUR), outcomes[6])
        // half an hour later the budget still holds for yet another place
        rig.now = T0 + 30 * MIN
        assertEquals(WaitReason.BUDGET, (rig.refresh(placeAt(40.0, 20.0)) as RefreshOutcome.Skipped).reason)
        assertEquals(6, rig.times.size)
        // an hour after the first request there is room again
        rig.now = T0 + HOUR
        assertEquals(RefreshOutcome.Fetched, rig.refresh(placeAt(40.0, 20.0)))
        assertEquals(7, rig.times.size)
    }

    @Test fun notModifiedKeepsSeriesAndMovesExpiry() {
        val rig = WeatherTestRig()
        val lm = "Wed, 25 Nov 2026 11:41:00 GMT"
        rig.next = { rig.ok(expiresInMs = 30 * MIN, lastModified = lm) }
        assertEquals(RefreshOutcome.Fetched, rig.refresh(OSLO))
        val firstSeries = rig.storage.saved!!.series!!
        assertEquals(lm, rig.storage.saved!!.lastModified)
        // an hour later the server says nothing changed
        rig.now = T0 + HOUR
        rig.next = { rig.notModified(expiresInMs = 45 * MIN) }
        assertEquals(RefreshOutcome.NotModified, rig.refresh(OSLO))
        assertEquals(lm, rig.headers[1]["If-Modified-Since"])
        val c = rig.storage.saved!!
        assertEquals(firstSeries.points, c.series!!.points)
        assertEquals(T0 + HOUR + 45 * MIN, c.expiresAtMs)
        assertEquals(T0 + HOUR, c.fetchedAtMs)
        assertEquals(T0 + HOUR, c.lastAttemptMs)
        assertEquals(0, c.failures)
        assertEquals(lm, c.lastModified)
        // the old series is still shown, now with the newer fetch time
        assertNotNull(rig.engine.current(OSLO, T0 + HOUR + 10 * MIN))
        // due again an hour after the last attempt, later than the expiry
        rig.now = T0 + HOUR + 59 * MIN
        assertEquals(WaitReason.HOURLY, (rig.refresh(OSLO) as RefreshOutcome.Skipped).reason)
        rig.now = T0 + 2 * HOUR
        rig.next = { rig.notModified() }
        assertEquals(RefreshOutcome.NotModified, rig.refresh(OSLO))
    }

    @Test fun ifModifiedSinceOnlyForSamePlace() {
        val rig = WeatherTestRig()
        val lm = "Wed, 25 Nov 2026 11:41:00 GMT"
        rig.next = { rig.ok(lastModified = lm) }
        rig.refresh(OSLO)
        assertNull(rig.headers[0]["If-Modified-Since"])
        // another place: no header, though the cache holds a Last-Modified for Oslo
        val bergen = placeAt(60.39, 5.32)
        rig.now = T0 + 5 * MIN
        rig.refresh(bergen)
        assertEquals(2, rig.times.size)
        assertNull(rig.headers[1]["If-Modified-Since"])
        // back at Oslo: the cache is Bergen's now, so again no header
        rig.now = T0 + 10 * MIN
        rig.refresh(OSLO)
        assertNull(rig.headers[2]["If-Modified-Since"])
        // Oslo asked for twice in a row (due again after an hour): the header is sent
        rig.next = { rig.ok(lastModified = lm) }
        rig.now = T0 + 80 * MIN
        rig.refresh(OSLO)
        assertEquals(lm, rig.headers[3]["If-Modified-Since"])
    }

    @Test fun staleCacheShowsClear() {
        val rig = WeatherTestRig()
        rig.next = { rig.ok(expiresInMs = HOUR) }
        rig.refresh(OSLO)
        assertNotNull(rig.engine.current(OSLO, T0))
        assertNotNull(rig.engine.current(OSLO, T0 + 2 * HOUR + 59 * MIN))
        // exactly three hours old is still shown, one millisecond more is not
        assertNotNull(rig.engine.current(OSLO, T0 + 3 * HOUR))
        assertNull(rig.engine.current(OSLO, T0 + 3 * HOUR + 1))
        assertEquals(WeatherPolicy.MAX_DISPLAY_AGE_MS, 3 * HOUR)
    }

    @Test fun otherPlaceCacheNotShown() {
        val rig = WeatherTestRig()
        rig.refresh(OSLO)
        assertNotNull(rig.engine.current(OSLO, T0))
        assertNull(rig.engine.current(placeAt(60.39, 5.32), T0))
        // the same rounded place is the same place
        assertNotNull(rig.engine.current(placeAt(59.94, 10.74), T0))
        assertNull(rig.engine.current(placeAt(59.8, 10.7), T0))
    }

    @Test fun currentPicksThePointOfTheHour() {
        val rig = WeatherTestRig()
        rig.refresh(OSLO)
        assertEquals(4.5, rig.engine.current(OSLO, T0)!!.temperatureC, 1e-9)
        assertEquals(5.5, rig.engine.current(OSLO, T0 + HOUR + 1)!!.temperatureC, 1e-9)
        assertEquals(6.5, rig.engine.current(OSLO, T0 + 2 * HOUR + 59 * MIN)!!.temperatureC, 1e-9)
        // before the first point there is nothing
        assertNull(rig.engine.current(OSLO, T0 - 1))
    }
}
