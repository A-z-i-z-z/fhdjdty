package dev.pocketagent.app.data

import dev.pocketagent.app.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Weather, the pure half: the data model, the reading of MET Norway's answer, the guard that keeps the
 * feature dark until a contact exists, the policy that spaces requests, the client that builds a request and
 * the engine that is the only code which calls the transport. No Android class is used here: time, place,
 * text and the network (HttpTransport) come in as parameters. The Android half is WeatherStore.
 */

enum class WeatherCondition {
    CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG, LIGHT_RAIN, RAIN, HEAVY_RAIN, SLEET, LIGHT_SNOW, SNOW, HEAVY_SNOW,
}

/**
 * One hour of weather. [flash] is not weather: only a debug pin sets it (0 by the clock, 1 forced bright,
 * 2 forced dim); parsed points always carry 0. It is last and has a default so every constructor call of the
 * registry still compiles.
 */
data class WeatherPoint(
    val timeMs: Long,
    val temperatureC: Double,
    val windMs: Double,
    val windFromDeg: Double,
    val cloudPercent: Int,
    val precipMm: Double,
    val condition: WeatherCondition,
    val thunder: Boolean,
    val flash: Int = 0,
)

/** The forecast points of one place (sorted by time). */
class WeatherSeries(val placeKey: String, val points: List<WeatherPoint>) {
    /** The latest point at or before [nowMs]; null when none or when it is older than [maxGapMs]. */
    fun at(nowMs: Long, maxGapMs: Long = 2 * 3_600_000L): WeatherPoint? {
        var best: WeatherPoint? = null
        for (p in points) {
            if (p.timeMs <= nowMs && (best == null || p.timeMs > best.timeMs)) best = p
        }
        if (best == null) return null
        return if (nowMs - best.timeMs > maxGapMs) null else best
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("place", placeKey)
        o.put("points", pointsToJson(points))
        return o.toString()
    }

    companion object {
        fun fromJson(text: String): WeatherSeries? {
            return try {
                val o = JSONObject(text)
                if (!o.has("place") || !o.has("points")) return null
                WeatherSeries(o.getString("place"), pointsFromJson(o.getJSONArray("points")))
            } catch (_: Exception) {
                null
            }
        }

        internal fun pointsToJson(points: List<WeatherPoint>): JSONArray {
            val arr = JSONArray()
            for (p in points) {
                val o = JSONObject()
                o.put("t", p.timeMs)
                o.put("c", finite(p.temperatureC))
                o.put("w", finite(p.windMs))
                o.put("d", finite(p.windFromDeg))
                o.put("k", p.cloudPercent)
                o.put("p", finite(p.precipMm))
                o.put("s", p.condition.name)
                o.put("h", p.thunder)
                arr.put(o)
            }
            return arr
        }

        /** Bad entries are skipped, the rest is kept. */
        internal fun pointsFromJson(arr: JSONArray): List<WeatherPoint> {
            val out = ArrayList<WeatherPoint>()
            for (i in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(i)
                    val c = WeatherCondition.valueOf(o.getString("s"))
                    out.add(
                        WeatherPoint(
                            o.getLong("t"), o.getDouble("c"), o.getDouble("w"), o.getDouble("d"),
                            o.getInt("k"), o.getDouble("p"), c, o.getBoolean("h"),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
            return out
        }

        private fun finite(v: Double): Double = if (v.isFinite()) v else 0.0
    }
}

/** Reads locationforecast 2.0 "compact" and turns MET's symbol codes into [WeatherCondition]. */
object WeatherParser {
    private const val HOUR_MS = 3_600_000L

    /**
     * Reads the answer of MET Norway. Entries before one hour before [nowMs] are dropped, the first [maxPoints]
     * kept, sorted by time. Null when no entry is usable. [nowMs] is the one addition to the registry form
     * (a trailing parameter with a default): the clock is a parameter, never read inside the parser's logic.
     */
    fun parse(json: String, placeKey: String, maxPoints: Int = 12, nowMs: Long = System.currentTimeMillis()): WeatherSeries? {
        return try {
            val root = JSONObject(json)
            val ts = root.optJSONObject("properties")?.optJSONArray("timeseries") ?: return null
            val earliest = nowMs - HOUR_MS
            val out = ArrayList<WeatherPoint>()
            for (i in 0 until ts.length()) {
                val entry = ts.optJSONObject(i) ?: continue
                val p = entryToPoint(entry, earliest) ?: continue
                out.add(p)
            }
            if (out.isEmpty()) return null
            out.sortBy { it.timeMs }
            val cap = maxPoints.coerceAtLeast(1)
            WeatherSeries(placeKey, if (out.size > cap) ArrayList(out.subList(0, cap)) else out)
        } catch (_: Exception) {
            null
        }
    }

    private fun entryToPoint(entry: JSONObject, earliestMs: Long): WeatherPoint? {
        val t = try {
            Instant.parse(entry.optString("time", "")).toEpochMilli()
        } catch (_: Exception) {
            return null
        }
        if (t < earliestMs) return null
        val data = entry.optJSONObject("data") ?: return null
        val d = data.optJSONObject("instant")?.optJSONObject("details") ?: return null
        val temp = num(d, "air_temperature") ?: return null
        val wind = num(d, "wind_speed") ?: return null
        val from = num(d, "wind_from_direction") ?: return null
        val cloud = num(d, "cloud_area_fraction") ?: return null
        val sym = symbolOf(data.optJSONObject("next_1_hours"), 1.0)
            ?: symbolOf(data.optJSONObject("next_6_hours"), 6.0)
            ?: return null
        val cond = conditionOf(sym.first, sym.second)
        return WeatherPoint(
            t, temp, wind, from, cloud.roundToInt().coerceIn(0, 100), sym.second ?: 0.0, cond.first, cond.second,
        )
    }

    /** The symbol code and the precipitation per hour of one "next_N_hours" block, or null without a symbol. */
    private fun symbolOf(block: JSONObject?, hours: Double): Pair<String, Double?>? {
        if (block == null) return null
        val code = block.optJSONObject("summary")?.optString("symbol_code", "") ?: ""
        if (code.isEmpty()) return null
        val mm = num(block.optJSONObject("details"), "precipitation_amount")
        return Pair(code, if (mm == null) null else (mm / hours).coerceAtLeast(0.0))
    }

    private fun num(o: JSONObject?, key: String): Double? {
        if (o == null || !o.has(key) || o.isNull(key)) return null
        val v = o.optDouble(key, Double.NaN)
        return if (v.isFinite()) v else null
    }

    /**
     * MET symbol code to (condition, thunder). The suffix _day, _night or _polartwilight is dropped; the rest is
     * matched by substring (so MET's own typos "lightssleetandthunder" and "lightssnowandthunder" work): sleet,
     * snow, rain (also rainshowers), fog, then clearsky, fair, partlycloudy, cloudy; unknown gives CLOUDY.
     * Strength: a code that starts with "light" is light, with "heavy" heavy; plain rain or snow is heavy from
     * 1.0 mm per hour on, else light. Sleet is one value.
     */
    fun conditionOf(symbolCode: String, precipMm: Double?): Pair<WeatherCondition, Boolean> {
        var s = symbolCode.trim().lowercase(Locale.ROOT)
        for (suffix in SUFFIXES) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length - suffix.length)
                break
            }
        }
        val thunder = s.contains("thunder")
        val light = s.startsWith("light")
        val heavy = s.startsWith("heavy")
        val plainHeavy = (precipMm ?: 0.0) >= 1.0
        val cond = when {
            s.contains("sleet") -> WeatherCondition.SLEET
            s.contains("snow") -> when {
                light -> WeatherCondition.LIGHT_SNOW
                heavy -> WeatherCondition.HEAVY_SNOW
                plainHeavy -> WeatherCondition.HEAVY_SNOW
                else -> WeatherCondition.LIGHT_SNOW
            }
            s.contains("rain") -> when {
                light -> WeatherCondition.LIGHT_RAIN
                heavy -> WeatherCondition.HEAVY_RAIN
                plainHeavy -> WeatherCondition.HEAVY_RAIN
                else -> WeatherCondition.LIGHT_RAIN
            }
            s.contains("fog") -> WeatherCondition.FOG
            s.contains("clearsky") -> WeatherCondition.CLEAR
            s.contains("fair") -> WeatherCondition.FAIR
            s.contains("partlycloudy") -> WeatherCondition.PARTLY_CLOUDY
            s.contains("cloudy") -> WeatherCondition.CLOUDY
            else -> WeatherCondition.CLOUDY
        }
        return Pair(cond, thunder)
    }

    private val SUFFIXES = arrayOf("_day", "_night", "_polartwilight")
}

/**
 * The text of a debug pin as a [WeatherPoint] (timeMs 0). Items are joined by commas:
 * cloud:0..3, rain:light|heavy, snow:light|heavy, sleet, fog:light|dense, thunder, wind:-1..1, temp:-60..60,
 * flash, flash:dim, or a preset name (clear, storm, drizzle, blizzard, mist) that expands to its items. A later
 * item of the same kind wins; rain, snow, sleet and fog share one condition (the last of them). One bad item
 * rejects the whole value (null); so does "off", which the caller handles itself. The kind of a fog follows from the
 * wind alone (calm below 2.0 m/s is dense), so "fog:dense" with a wind of 2.0 m/s or more (wind beyond 1/13 either way)
 * and "fog:light" with a calm wind are rejected too, instead of showing the other kind.
 */
object WeatherPin {
    private const val DEFAULT_TEMP_C = 11.0
    private val CLOUD_PERCENT = intArrayOf(5, 30, 65, 95)

    private val PRESETS: Map<String, String> = mapOf(
        "clear" to "cloud:0,temp:18",
        "storm" to "cloud:3,rain:heavy,thunder,wind:0.7,temp:12",
        "drizzle" to "cloud:2,rain:light,temp:9",
        "blizzard" to "cloud:3,snow:heavy,wind:-0.6,temp:-6",
        "mist" to "fog:dense,temp:4",
    )

    fun parse(text: String): WeatherPoint? {
        val items = ArrayList<String>()
        for (raw in text.split(',')) {
            val item = raw.trim().lowercase(Locale.ROOT)
            if (item.isEmpty()) return null
            val preset = PRESETS[item]
            if (preset != null) items.addAll(preset.split(',')) else items.add(item)
        }
        var cloud = 0
        var condition: WeatherCondition? = null
        var precipMm = 0.0
        var fogWind: Double? = null
        var fogDense: Boolean? = null
        var windX: Double? = null
        var thunder = false
        var temp = DEFAULT_TEMP_C
        var flash = 0
        for (item in items) {
            val colon = item.indexOf(':')
            val key = if (colon < 0) item else item.substring(0, colon)
            val arg: String? = if (colon < 0) null else item.substring(colon + 1)
            when (key) {
                "cloud" -> {
                    val n = arg?.toIntOrNull() ?: return null
                    if (n < 0 || n > 3) return null
                    cloud = n
                }
                "rain" -> {
                    when (arg) {
                        "light" -> { condition = WeatherCondition.LIGHT_RAIN; precipMm = 0.6 }
                        "heavy" -> { condition = WeatherCondition.HEAVY_RAIN; precipMm = 6.0 }
                        else -> return null
                    }
                    fogWind = null
                    fogDense = null
                }
                "snow" -> {
                    when (arg) {
                        "light" -> { condition = WeatherCondition.LIGHT_SNOW; precipMm = 0.6 }
                        "heavy" -> { condition = WeatherCondition.HEAVY_SNOW; precipMm = 6.0 }
                        else -> return null
                    }
                    fogWind = null
                    fogDense = null
                }
                "sleet" -> {
                    if (arg != null) return null
                    condition = WeatherCondition.SLEET
                    precipMm = 1.0
                    fogWind = null
                    fogDense = null
                }
                "fog" -> {
                    when (arg) {
                        "dense" -> { fogWind = 0.5; fogDense = true }
                        "light" -> { fogWind = 4.0; fogDense = false }
                        else -> return null
                    }
                    condition = WeatherCondition.FOG
                    precipMm = 0.0
                }
                "thunder" -> {
                    if (arg != null) return null
                    thunder = true
                }
                "wind" -> {
                    val x = arg?.toDoubleOrNull() ?: return null
                    if (!x.isFinite() || x < -1.0 || x > 1.0) return null
                    windX = x
                }
                "temp" -> {
                    val x = arg?.toDoubleOrNull() ?: return null
                    if (!x.isFinite() || x < -60.0 || x > 60.0) return null
                    temp = x
                }
                "flash" -> {
                    flash = when (arg) {
                        null -> 1
                        "dim" -> 2
                        else -> return null
                    }
                }
                else -> return null
            }
        }
        val cond = condition ?: when (cloud) {
            0 -> WeatherCondition.CLEAR
            1, 2 -> WeatherCondition.PARTLY_CLOUDY
            else -> WeatherCondition.CLOUDY
        }
        val wx = windX
        val windMs: Double
        val fromDeg: Double
        if (wx != null) {
            windMs = if (wx == 0.0) 0.0 else 1.0 + 13.0 * abs(wx)
            fromDeg = if (wx < 0.0) 90.0 else 270.0
        } else {
            windMs = fogWind ?: 0.0
            fromDeg = 270.0
        }
        // How dense a fog looks comes only from the wind (toLook: calm below 2.0 m/s is dense). A wind value that
        // would turn the requested fog into the other kind is a bad pin, not a silent change of picture.
        val wantDense = fogDense
        if (wantDense != null && (windMs < 2.0) != wantDense) return null
        return WeatherPoint(0L, temp, windMs, fromDeg, CLOUD_PERCENT[cloud], precipMm, cond, thunder, flash)
    }
}

/** The whole degrees of [celsius] (or of its Fahrenheit value) as text, rounded half away from zero; "-0" is "0". */
fun formatTemperature(celsius: Double, fahrenheit: Boolean): String {
    val v = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
    if (v.isNaN()) return "0"
    val r: Long = if (v < 0.0) -Math.round(-v) else Math.round(v)
    return r.toString()
}

/** Regions that show Fahrenheit: US LR BS KY BZ PW FM MH. */
fun usesFahrenheit(countryCode: String): Boolean = when (countryCode.trim().uppercase(Locale.ROOT)) {
    "US", "LR", "BS", "KY", "BZ", "PW", "FM", "MH" -> true
    else -> false
}

/** The one question that decides whether the weather may ever leave the phone: is there a usable contact? */
object WeatherGuard {
    /**
     * True for a contact MET can write to: exactly as typed (no space around), 6 to 120 printable ASCII
     * characters, no whitespace or control character, and either "https://" plus at least 4 characters, or
     * local@host.tld.
     */
    fun contactOk(contact: String): Boolean {
        if (contact.length < 6 || contact.length > 120) return false
        if (contact != contact.trim()) return false
        for (ch in contact) {
            if (ch.code < 0x21 || ch.code > 0x7E) return false
        }
        if (contact.startsWith("https://")) return contact.length - "https://".length >= 4
        return emailOk(contact)
    }

    private fun emailOk(s: String): Boolean {
        val at = s.indexOf('@')
        if (at < 1 || at != s.lastIndexOf('@')) return false
        val host = s.substring(at + 1)
        if (host.length < 4 || host.startsWith(".") || host.endsWith(".") || host.startsWith("-") || host.contains("..")) return false
        val dot = host.lastIndexOf('.')
        if (dot < 1 || host.length - dot - 1 < 2) return false
        for (ch in host) {
            if (!(ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.')) return false
        }
        for (ch in s.substring(0, at)) {
            if (!(ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '%' || ch == '+' || ch == '-')) return false
        }
        return true
    }
}

/** The one place that talks to the network is behind this: the Android half supplies a real one. */
fun interface HttpTransport {
    fun get(url: String, headers: Map<String, String>): HttpReply
}

/** [headers] have lower-case names. */
class HttpReply(val code: Int, val headers: Map<String, String>, val body: String?)

enum class WaitReason { DISABLED, NO_CONTACT, BACKGROUND, BLOCKED, BUDGET, FRESH, HOURLY, BACKOFF }

sealed interface Decision {
    data object Go : Decision
    data class Wait(val reason: WaitReason, val untilMs: Long) : Decision
}

/** What the engine remembers between calls (and the Android half keeps on disk). */
data class WeatherCache(
    val placeKey: String,
    val series: WeatherSeries?,
    val fetchedAtMs: Long,
    val expiresAtMs: Long,
    val lastAttemptMs: Long,
    val failures: Int,
    val blockedUntilMs: Long,
    val lastModified: String?,
)

/** When a request may go out: per place at most one an hour, in total at most six an hour, backoff after failures. */
object WeatherPolicy {
    const val MIN_GAP_MS = 3_600_000L
    const val MAX_REQUESTS_PER_HOUR = 6
    const val MAX_DISPLAY_AGE_MS = 10_800_000L
    const val BACKOFF_BASE_MS = 900_000L
    const val BACKOFF_MAX_MS = 7_200_000L
    const val BLOCK_403_MS = 86_400_000L
    const val BLOCK_429_MS = 3_600_000L
    const val BLOCK_4XX_MS = 21_600_000L

    private const val HOUR_MS = 3_600_000L

    /** 0 failures give 0; n failures give min(900000 * 2^(n-1), 7200000). */
    fun backoffMs(failures: Int): Long {
        if (failures <= 0) return 0L
        if (failures >= 5) return BACKOFF_MAX_MS
        return minOf(BACKOFF_BASE_MS shl (failures - 1), BACKOFF_MAX_MS)
    }

    /**
     * Checked in this order: not enabled, no usable contact, not in the foreground, blocked by an earlier answer,
     * six requests in the last hour; then Go when there is no cache or it is for another place, else Go once the
     * expiry (while nothing failed) and the gap since the last attempt (an hour, or the backoff) have passed.
     */
    fun decide(
        nowMs: Long,
        enabled: Boolean,
        contactOk: Boolean,
        foreground: Boolean,
        placeKey: String,
        cache: WeatherCache?,
        recentRequestsMs: List<Long>,
    ): Decision {
        if (!enabled) return Decision.Wait(WaitReason.DISABLED, 0L)
        if (!contactOk) return Decision.Wait(WaitReason.NO_CONTACT, 0L)
        if (!foreground) return Decision.Wait(WaitReason.BACKGROUND, 0L)
        if (cache != null && cache.blockedUntilMs > nowMs) return Decision.Wait(WaitReason.BLOCKED, cache.blockedUntilMs)
        val inWindow = ArrayList<Long>()
        for (r in recentRequestsMs) {
            if (r > nowMs - HOUR_MS) inWindow.add(r)
        }
        if (inWindow.size >= MAX_REQUESTS_PER_HOUR) {
            inWindow.sort()
            return Decision.Wait(WaitReason.BUDGET, inWindow[inWindow.size - MAX_REQUESTS_PER_HOUR] + HOUR_MS)
        }
        if (cache == null || cache.placeKey != placeKey) return Decision.Go
        val clean = cache.failures <= 0
        val gap = if (clean) MIN_GAP_MS else backoffMs(cache.failures)
        val due = maxOf(if (clean) cache.expiresAtMs else 0L, cache.lastAttemptMs + gap)
        if (nowMs >= due) return Decision.Go
        val reason = when {
            !clean -> WaitReason.BACKOFF
            nowMs < cache.expiresAtMs -> WaitReason.FRESH
            else -> WaitReason.HOURLY
        }
        return Decision.Wait(reason, due)
    }
}

sealed interface FetchResult {
    data class Fresh(val series: WeatherSeries, val expiresAtMs: Long, val lastModified: String?) : FetchResult
    data class NotModified(val expiresAtMs: Long, val lastModified: String?) : FetchResult

    /** code 0 means no answer (an exception); blockForMs 0 means ordinary backoff. */
    data class Failed(val code: Int, val blockForMs: Long) : FetchResult

    /** The contact is not ok: the transport was NOT called. */
    data object Blocked : FetchResult
}

/** Builds the request MET asks for and reads the answer. */
object WeatherClient {
    const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    private const val APP_NAME = "Agiri"
    private const val DEFAULT_EXPIRY_MS = 3_600_000L

    /** The endpoint with the place rounded to one decimal (about 10 km) and nothing else in the query. */
    fun url(place: Place): String =
        ENDPOINT + "?lat=" + formatCoord(roundCoord(place.latitude, 1), 1) + "&lon=" + formatCoord(roundCoord(place.longitude, 1), 1)

    /** "Agiri/0.2.17 contact": characters of the version outside A-Z a-z 0-9 . _ - become "_". */
    fun userAgent(appName: String, version: String, contact: String): String =
        clean(appName) + "/" + clean(version) + " " + contact

    private fun clean(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val ok = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == '-'
            sb.append(if (ok) ch else '_')
        }
        return sb.toString()
    }

    /**
     * One request. Returns [FetchResult.Blocked] before it builds any header when the contact is not ok.
     * 200 and 203 are read (an unreadable body is Failed(code, 0)); 304 is NotModified; 403 blocks for 24 hours,
     * 429 for 1 hour, other 4xx for 6 hours; 5xx, other codes and exceptions are ordinary failures.
     */
    fun fetch(
        transport: HttpTransport,
        place: Place,
        contact: String,
        appVersion: String,
        ifModifiedSince: String?,
        nowMs: Long,
    ): FetchResult {
        if (!WeatherGuard.contactOk(contact)) return FetchResult.Blocked
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = userAgent(APP_NAME, appVersion, contact)
        headers["Accept"] = "application/json"
        if (ifModifiedSince != null) headers["If-Modified-Since"] = ifModifiedSince
        val reply = try {
            transport.get(url(place), headers)
        } catch (_: Exception) {
            return FetchResult.Failed(0, 0L)
        }
        val code = reply.code
        val lastModified = header(reply, "last-modified")
        val expires = parseHttpDate(header(reply, "expires")) ?: (nowMs + DEFAULT_EXPIRY_MS)
        return when {
            code == 200 || code == 203 -> {
                val body = reply.body
                val series = if (body == null) null else WeatherParser.parse(body, place.key(), 12, nowMs)
                if (series == null) FetchResult.Failed(code, 0L) else FetchResult.Fresh(series, expires, lastModified)
            }
            code == 304 -> FetchResult.NotModified(expires, lastModified ?: ifModifiedSince)
            code == 403 -> FetchResult.Failed(403, WeatherPolicy.BLOCK_403_MS)
            code == 429 -> FetchResult.Failed(429, WeatherPolicy.BLOCK_429_MS)
            code in 400..499 -> FetchResult.Failed(code, WeatherPolicy.BLOCK_4XX_MS)
            else -> FetchResult.Failed(code, 0L)
        }
    }

    private fun header(reply: HttpReply, lowerName: String): String? {
        val direct = reply.headers[lowerName]
        if (direct != null) return direct
        for ((k, v) in reply.headers) {
            if (k.equals(lowerName, ignoreCase = true)) return v
        }
        return null
    }

    private fun parseHttpDate(text: String?): Long? {
        if (text == null || text.isBlank()) return null
        return try {
            ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

/** Where the engine keeps its cache (a file in the Android half, a variable in a test). */
interface WeatherStorage {
    fun load(): WeatherCache?
    fun save(c: WeatherCache)
    fun clear()
}

sealed interface RefreshOutcome {
    data object Fetched : RefreshOutcome
    data object NotModified : RefreshOutcome
    data class Skipped(val reason: WaitReason, val untilMs: Long) : RefreshOutcome
    data class Failed(val code: Int) : RefreshOutcome
}

/**
 * Runs the policy and, when it says Go, the one request. This is the only code that calls the transport (through
 * [WeatherClient.fetch]). It logs one line per request and one per change of the reason for waiting; the lines never
 * contain the contact.
 */
class WeatherEngine(
    private val transport: HttpTransport,
    private val storage: WeatherStorage,
    private val contact: String,
    private val appVersion: String,
    nowMs: () -> Long = System::currentTimeMillis,
) {
    private val clock: () -> Long = nowMs
    private val requests = ArrayList<Long>()
    private var cache: WeatherCache? = null
    private var loaded = false
    private var lastSkip: WaitReason? = null

    /** BLOCKING (a network call may happen): call it off the main thread. */
    @Synchronized
    fun refreshIfDue(enabled: Boolean, foreground: Boolean, place: Place): RefreshOutcome {
        val now = clock()
        load()
        val key = place.key()
        val decision = WeatherPolicy.decide(now, enabled, WeatherGuard.contactOk(contact), foreground, key, cache, requests)
        if (decision is Decision.Wait) {
            if (decision.reason != lastSkip) {
                lastSkip = decision.reason
                AppLog.i(TAG, "skipped " + decision.reason.name)
            }
            return RefreshOutcome.Skipped(decision.reason, decision.untilMs)
        }
        lastSkip = null
        requests.removeAll { it <= now - HOUR_MS }
        requests.add(now)
        AppLog.i(TAG, "request $key")
        val before = cache
        val prev: WeatherCache? = if (before != null && before.placeKey == key) before else null
        val result = WeatherClient.fetch(transport, place, contact, appVersion, prev?.lastModified, now)
        return when (result) {
            is FetchResult.Fresh -> {
                cache = WeatherCache(key, result.series, now, result.expiresAtMs, now, 0, 0L, result.lastModified)
                save()
                AppLog.i(TAG, "answer ok " + result.series.points.size + " points")
                RefreshOutcome.Fetched
            }
            is FetchResult.NotModified -> {
                cache = if (prev != null) {
                    prev.copy(
                        fetchedAtMs = now, expiresAtMs = result.expiresAtMs, lastAttemptMs = now,
                        failures = 0, blockedUntilMs = 0L, lastModified = result.lastModified,
                    )
                } else {
                    WeatherCache(key, null, now, result.expiresAtMs, now, 0, 0L, result.lastModified)
                }
                save()
                AppLog.i(TAG, "answer not modified")
                RefreshOutcome.NotModified
            }
            is FetchResult.Failed -> {
                val base = prev ?: WeatherCache(key, null, 0L, 0L, now, 0, 0L, null)
                cache = base.copy(
                    lastAttemptMs = now,
                    failures = (base.failures + 1).coerceAtMost(MAX_FAILURES),
                    blockedUntilMs = if (result.blockForMs > 0L) now + result.blockForMs else base.blockedUntilMs,
                )
                save()
                AppLog.i(TAG, "answer failed code " + result.code)
                RefreshOutcome.Failed(result.code)
            }
            is FetchResult.Blocked -> RefreshOutcome.Skipped(WaitReason.NO_CONTACT, 0L)
        }
    }

    /** The point to show: from the cache of the same place, at [nowMs], fetched at most 3 hours ago; else null. */
    @Synchronized
    fun current(place: Place, nowMs: Long): WeatherPoint? {
        load()
        val c = cache ?: return null
        if (c.placeKey != place.key()) return null
        if (nowMs - c.fetchedAtMs > WeatherPolicy.MAX_DISPLAY_AGE_MS) return null
        return c.series?.at(nowMs)
    }

    /** Deletes the stored cache and forgets the request list (the switch went off). */
    @Synchronized
    fun forget() {
        try {
            storage.clear()
        } catch (_: Exception) {
        }
        cache = null
        loaded = true
        requests.clear()
        lastSkip = null
    }

    private fun load() {
        if (loaded) return
        loaded = true
        cache = try {
            storage.load()
        } catch (_: Exception) {
            null
        }
    }

    private fun save() {
        val c = cache ?: return
        try {
            storage.save(c)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "Weather"
        const val HOUR_MS = 3_600_000L
        const val MAX_FAILURES = 30
    }
}

/** The cache as the JSON of files/weather/cache.json: the rounded place and numbers, nothing else. */
object WeatherCacheJson {
    private const val VERSION = 1

    fun toJson(c: WeatherCache): String {
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("place", c.placeKey)
        o.put("fetched", c.fetchedAtMs)
        o.put("expires", c.expiresAtMs)
        o.put("attempt", c.lastAttemptMs)
        o.put("failures", c.failures)
        o.put("blocked", c.blockedUntilMs)
        if (c.lastModified != null) o.put("lastModified", c.lastModified)
        val s = c.series
        if (s != null) o.put("points", WeatherSeries.pointsToJson(s.points))
        return o.toString()
    }

    /** Null for anything that is not a cache of version 1. */
    fun fromJson(text: String): WeatherCache? {
        return try {
            val o = JSONObject(text)
            if (o.optInt("v", 0) != VERSION) return null
            val place = o.getString("place")
            val series = if (o.has("points")) WeatherSeries(place, WeatherSeries.pointsFromJson(o.getJSONArray("points"))) else null
            WeatherCache(
                place, series, o.getLong("fetched"), o.getLong("expires"), o.getLong("attempt"),
                o.getInt("failures"), o.getLong("blocked"),
                if (o.has("lastModified")) o.getString("lastModified") else null,
            )
        } catch (_: Exception) {
            null
        }
    }
}
