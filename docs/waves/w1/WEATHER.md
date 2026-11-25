# Wave 1a, area WEATHER: contract draft

Package `dev.pocketagent.app` is written `app`. Paths are under `app/src/main/java/dev/pocketagent/app/` (`J/`) unless they start with `app/` or `tools/`. Line numbers were read from the files at HEAD `fe0e0ea`. BINDING items taken from `docs/design-language.md` 6.2: five weather layers (cloud in three covers with the stars dimming behind it; rain in two strengths, slanted by the wind, small splashes; snow that drifts and settles as a white line on the ridges; fog as a dithered band that eats the far ridges; wind in sparks, grass and flame), far lightning of two frames of a lit horizon at most once in twenty seconds, one line "11°, light rain" with the credit, a clear sky when the weather is off. Section 11 (scene under 1 ms) binds section 10.

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| W1 | `Places`: zone table lookup (`Place`, `PlaceTable.forZone`), alias rows, UTC-offset fallback; offline city list, normalised search, `PlaceChoice`, `resolvePlace` (pure) | core |
| W3 | `PlaceStore` (prefs, assets, in-memory pin), `rememberPlace()` for SCENE; `PlaceRows()` and `WeatherSwitchRow()` for Appearance (place picker sheet) | core |
| W5 | `tools/places/make_places.py` and the two generated assets | core (the app works with empty assets: offset fallback) |
| W6 | Weather model, MET symbol mapping, JSON parse, cache JSON, readout formatting | core |
| W7 | THE GUARD (`WeatherGuard`), `WeatherPolicy`, `WeatherClient`, `WeatherEngine`, `WeatherStore` (dark ship) | core |
| W8 | Weather pin (`WeatherPin`), the two debug-link extras `weather`, `place`; `WeatherLook`, `WeatherPoint.toLook()`, `WeatherWind`, star and body visibility | core |
| W10 | Layers: clouds, rain, snow, fog (`WeatherLayers`, `WeatherArt`, `DropSet`) | core |
| W11 | `rememberWeather()` host (refresh loop while Home is started), `weatherNoteText()`; strings `wx_*`; all tests of section 8 | core |
| E1 | Rain splashes on the meadow | extra |
| E2 | Far lightning (`lightningPhase`, lit horizon, the `flash` pin) | extra |
| E3 | Snow settling as a white line on the ridges (`WeatherArt.topEdges`, `snowCaps`) | extra |
| E4 | Splashes on the hearth stones | extra |
| E5 | JVM stills dump (`WeatherStillsDump`, PNG per look in `build/weather-stills/`) | extra |

Cut order (first cut first): E5, E4, E2, E1, E3, then clouds from three tones to two. Never cut: W6 to W8, W11, the guard, the pin, the Places API. A cut layer keeps its method as an empty body.

## 2. Files (I own them exclusively)

Create:
- `J/data/Places.kt`: pure (no Android import): `Place`, `PlaceSource`, `City`, `ZoneRow`, `PlaceChoice`, `PlaceTable`, `resolvePlace`, `roundCoord`, `formatCoord`, `zoneCityName`.
- `J/data/PlaceStore.kt`: Android: prefs `place`, asset loading, flows, pin.
- `J/data/Weather.kt`: pure: `WeatherCondition`, `WeatherPoint`, `WeatherSeries`, `WeatherParser`, `WeatherPin`, `WeatherGuard`, `WeatherPolicy`, `WeatherClient`, `HttpTransport`, `WeatherEngine`, `WeatherStorage`, formatting.
- `J/data/WeatherStore.kt`: Android: prefs `weather`, `files/weather/cache.json`, `HttpUrlTransport`, flows, the only `HttpURLConnection` of the weather.
- `J/ui/pixel/stage/WeatherLook.kt`, `WeatherParticles.kt`, `WeatherArt.kt`, `WeatherLayers.kt`: pure Kotlin (no `android.` or `androidx.` import: `StageCoreIsPureTest.kt` scans every file of `ui/pixel/stage`).
- `J/ui/pixel/WeatherHost.kt`: Compose glue (deviation from the ownership table, which puts all of it in `stage/`: a composable there fails `StageCoreIsPureTest.kt:14-30`).
- `J/ui/settings/PlaceWeatherSettings.kt`: the two Appearance rows and the place sheet.
- `app/src/main/res/values/strings_weather.xml` (prefix `wx_`).
- `app/src/main/assets/places/zones.tsv`, `app/src/main/assets/places/cities.tsv` (generated).
- `tools/places/make_places.py`, `tools/places/force-include.txt`.
- `app/src/test/resources/weather/locationforecast-sample.json`.
- Tests: `app/src/test/java/dev/pocketagent/app/data/{PlacesTest,WeatherParseTest,WeatherPinTest,WeatherGuardTest,WeatherPolicyTest,WeatherEngineTest,WeatherIsolationTest}.kt`, `.../ui/pixel/stage/{WeatherLookTest,WeatherParticlesTest,WeatherLayersTest,WeatherStillsDump}.kt`.

Modify: `J/DebugLinks.kt` and `app/src/test/java/dev/pocketagent/app/DebugLinksTest.kt` (the pin is mine; no other 1a area lists this file, so I take it for the whole half; other areas that need an extra send me the line, section 7).

## 3. Shared symbols I PROVIDE

### 3.1 Places (`app.data`, file `Places.kt`, pure)
```kotlin
enum class PlaceSource { ZONE, CITY, OFFSET, PINNED }
data class Place(val latitude: Double, val longitude: Double, val name: String, val source: PlaceSource, val countryCode: String = "") {
    val isSouthern: Boolean get() = latitude < 0.0
    fun key(): String                       // "59.9,10.7": both coordinates through formatCoord(.., 1)
}
data class City(val name: String, val nativeName: String, val countryCode: String, val latitude: Double, val longitude: Double, val populationK: Int)
data class ZoneRow(val zoneId: String, val countryCode: String, val latitude: Double, val longitude: Double)
sealed interface PlaceChoice { data object Auto : PlaceChoice; data class Picked(val city: City) : PlaceChoice }
class PlaceTable(val zones: List<ZoneRow>, val cities: List<City>) {
    fun forZone(zoneId: String, standardOffsetSeconds: Int = 0): Place   // ZONE (exact id, then alias rows), else OFFSET: lon = offset/240.0 (15 deg per hour), lat = 35.0, name = zoneCityName(zoneId)
    fun search(query: String, limit: Int = 8): List<City>                // normalised; empty below 2 characters; prefix of the name or of a word, then substring; ties by populationK desc, then name; limit coerced to 1..20
    companion object {
        val EMPTY: PlaceTable
        fun parse(zonesTsv: String, citiesTsv: String): PlaceTable      // never throws; lines starting with # and bad lines skipped; lat -90..90, lon -180..180
        fun normalize(s: String): String                                 // NFD, drop marks, lowercase with Locale.ROOT, then dotless i to i, sharp s to ss, o-slash to o, ae-ligature to ae, d-bar to d, l-stroke to l
    }
}
fun resolvePlace(choice: PlaceChoice, pinned: Place?, zoneId: String, standardOffsetSeconds: Int, table: PlaceTable): Place  // pinned, else Picked (source CITY), else table.forZone
fun roundCoord(value: Double, decimals: Int = 1): Double                // BigDecimal(value.toString()).setScale(decimals, HALF_UP); never negative zero
fun formatCoord(value: Double, decimals: Int = 1): String               // Locale.ROOT, "-0.0" becomes "0.0"
fun zoneCityName(zoneId: String): String                                // "America/Port_of_Spain" gives "Port of Spain"; no slash gives the id
```
Asset formats (UTF-8, LF, first lines `# source ...`): `zones.tsv` = `zoneId TAB CC TAB lat TAB lon` (2 decimals); `cities.tsv` = `name TAB nativeName TAB CC TAB lat TAB lon TAB populationK` (nativeName empty when equal to name).
```kotlin
class PlaceStore private constructor(context: Context) {            // companion fun get(context: Context): PlaceStore
    val choice: StateFlow<PlaceChoice>
    val place: StateFlow<Place>                                     // resolvePlace(choice, pin, ZoneId.systemDefault(), ...); never null; SCENE reads this
    val table: PlaceTable                                           // lazy, assets, PlaceTable.EMPTY and an AppLog warning when unreadable
    fun setAuto(); fun setCity(city: City); fun search(query: String, limit: Int = 8): List<City>
    fun refreshZone()                                               // recompute when the phone's time zone changed
    fun pinPlace(p: Place?)                                         // no-op unless BuildConfig.TEST_HOOKS; memory only
}
// app.ui.pixel (WeatherHost.kt)
@Composable fun rememberPlace(): Place                              // collects PlaceStore.place, calls refreshZone() on every ON_RESUME
```
### 3.2 Weather data (`app.data`, `Weather.kt`, pure)
```kotlin
enum class WeatherCondition { CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG, LIGHT_RAIN, RAIN, HEAVY_RAIN, SLEET, LIGHT_SNOW, SNOW, HEAVY_SNOW }
data class WeatherPoint(val timeMs: Long, val temperatureC: Double, val windMs: Double, val windFromDeg: Double,
                        val cloudPercent: Int, val precipMm: Double, val condition: WeatherCondition, val thunder: Boolean)
class WeatherSeries(val placeKey: String, val points: List<WeatherPoint>) {
    fun at(nowMs: Long, maxGapMs: Long = 2 * 3_600_000L): WeatherPoint?   // latest point with timeMs <= now, null when it is older than maxGap or none
    fun toJson(): String; companion fun fromJson(text: String): WeatherSeries?
}
object WeatherParser {
    fun parse(json: String, placeKey: String, maxPoints: Int = 12): WeatherSeries?                // locationforecast 2.0 compact; null when no usable entry
    fun conditionOf(symbolCode: String, precipMm: Double?): Pair<WeatherCondition, Boolean>      // (condition, thunder)
}
object WeatherPin { fun parse(text: String): WeatherPoint? }          // grammar in 3.6; timeMs is 0
fun formatTemperature(celsius: Double, fahrenheit: Boolean): String  // "11°", "52°F" is NOT used here: see keys wx_temp, wx_temp_f; this returns only the rounded integer text: Math.round, "-0" becomes "0"
fun usesFahrenheit(countryCode: String): Boolean                      // US LR BS KY BZ PW FM MH
```
JSON paths read by `parse` (locationforecast 2.0 compact, as I know it): `properties.timeseries[]` each with `time` (ISO 8601 UTC, `Instant.parse`), `data.instant.details.{air_temperature, wind_speed, wind_from_direction, cloud_area_fraction}` (all four required, else the entry is skipped), symbol and precipitation from `data.next_1_hours` (`summary.symbol_code`, `details.precipitation_amount`), else from `data.next_6_hours` (precipitation divided by 6), else the entry is skipped. Entries before `now - 1 h` are dropped, the first `maxPoints` kept, the list sorted by time. Anything else in the document is ignored (no coordinates, no elevation are stored).

`conditionOf` rule: strip a trailing `_day`, `_night` or `_polartwilight`; thunder = contains `thunder`; the rest by substring (so MET's own typos `lightssleetandthunder`, `lightssnowandthunder` work): contains `sleet` then SLEET; `snow` then snow; `rain` (also `rainshowers`) then rain; `fog` then FOG; else `clearsky` CLEAR, `fair` FAIR, `partlycloudy` PARTLY_CLOUDY, `cloudy` CLOUDY, unknown gives CLOUDY. Strength: starts with `light` then LIGHT_*, starts with `heavy` then HEAVY_*, plain `rain` or `snow` then HEAVY_* when `precipMm >= 1.0` else LIGHT_*; sleet is one value.

### 3.3 The guard, policy, client, engine (`Weather.kt`, pure; this is what makes the feature dark)
```kotlin
object WeatherGuard {
    fun contactOk(contact: String): Boolean   // trimmed equals itself, 6..120 chars, no whitespace or control char, and either https://<4+ chars> or local@host.tld
}
fun interface HttpTransport { fun get(url: String, headers: Map<String, String>): HttpReply }
class HttpReply(val code: Int, val headers: Map<String, String> /* names lower-case */, val body: String?)
enum class WaitReason { DISABLED, NO_CONTACT, BACKGROUND, BLOCKED, BUDGET, FRESH, HOURLY, BACKOFF }
sealed interface Decision { data object Go : Decision; data class Wait(val reason: WaitReason, val untilMs: Long) : Decision }
data class WeatherCache(val placeKey: String, val series: WeatherSeries?, val fetchedAtMs: Long, val expiresAtMs: Long,
                        val lastAttemptMs: Long, val failures: Int, val blockedUntilMs: Long, val lastModified: String?)
object WeatherPolicy {
    const val MIN_GAP_MS = 3_600_000L; const val MAX_REQUESTS_PER_HOUR = 6; const val MAX_DISPLAY_AGE_MS = 10_800_000L
    const val BACKOFF_BASE_MS = 900_000L; const val BACKOFF_MAX_MS = 7_200_000L
    const val BLOCK_403_MS = 86_400_000L; const val BLOCK_429_MS = 3_600_000L; const val BLOCK_4XX_MS = 21_600_000L
    fun backoffMs(failures: Int): Long                                  // 0 gives 0; n gives min(900000 * 2^(n-1), 7200000)
    fun decide(nowMs: Long, enabled: Boolean, contactOk: Boolean, foreground: Boolean, placeKey: String,
               cache: WeatherCache?, recentRequestsMs: List<Long>): Decision
}
```
`decide`, checked in this order: `!enabled` DISABLED, `!contactOk` NO_CONTACT, `!foreground` BACKGROUND, `cache.blockedUntilMs > now` BLOCKED, requests of the last hour `>= 6` BUDGET, no cache or other `placeKey` Go, else `due = max(if (failures == 0) expiresAtMs else 0, lastAttemptMs + (if (failures == 0) MIN_GAP_MS else backoffMs(failures)))`; `now >= due` Go, else Wait(FRESH if before expiry, else HOURLY or BACKOFF) until `due`. Per place at most one request an hour, in total at most six an hour.
```kotlin
object WeatherClient {
    const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    fun url(place: Place): String                          // ENDPOINT + "?lat=59.9&lon=10.7" (formatCoord(roundCoord(x,1),1)); nothing else in the query
    fun userAgent(appName: String, version: String, contact: String): String   // "Agiri/0.2.17 <contact>"; version chars outside [A-Za-z0-9._-] become "_"
    fun fetch(transport: HttpTransport, place: Place, contact: String, appVersion: String, ifModifiedSince: String?, nowMs: Long): FetchResult
}
sealed interface FetchResult {
    data class Fresh(val series: WeatherSeries, val expiresAtMs: Long, val lastModified: String?) : FetchResult
    data class NotModified(val expiresAtMs: Long, val lastModified: String?) : FetchResult
    data class Failed(val code: Int, val blockForMs: Long) : FetchResult   // code 0 = no answer (exception), blockFor 0 = ordinary backoff
    data object Blocked : FetchResult                                       // the contact is not ok: transport was NOT called
}
```
`fetch` calls `WeatherGuard.contactOk(contact)` first and returns Blocked before it builds any header. Headers: `User-Agent`, `Accept: application/json`, `If-Modified-Since` when given. Replies: 200 and 203 parse (null series gives Failed(200, 0)); 304 NotModified; 403 Failed(403, 24 h); 429 Failed(429, 1 h); other 4xx Failed(code, 6 h); 5xx and exceptions Failed(code or 0, 0). `Expires` is read with `DateTimeFormatter.RFC_1123_DATE_TIME`; missing or unreadable gives `nowMs + 3_600_000`; `Last-Modified` is kept as the raw text.
```kotlin
interface WeatherStorage { fun load(): WeatherCache?; fun save(c: WeatherCache); fun clear() }
sealed interface RefreshOutcome { data object Fetched; data object NotModified; data class Skipped(val reason: WaitReason, val untilMs: Long); data class Failed(val code: Int) }
class WeatherEngine(transport: HttpTransport, storage: WeatherStorage, contact: String, appVersion: String, nowMs: () -> Long = System::currentTimeMillis) {
    @Synchronized fun refreshIfDue(enabled: Boolean, foreground: Boolean, place: Place): RefreshOutcome   // BLOCKING; the only code that calls transport; logs one AppLog line per request and per change of skip reason
    @Synchronized fun current(place: Place, nowMs: Long): WeatherPoint?   // cache series of the same placeKey, at(now), and fetchedAt not older than MAX_DISPLAY_AGE_MS; else null
    @Synchronized fun forget()                                            // storage.clear(), forget the request list
}
```
### 3.4 Store and host (Android)
```kotlin
class WeatherStore private constructor(context: Context) {            // companion fun get(context: Context): WeatherStore
    val available: Boolean                                            // WeatherGuard.contactOk(BuildConfig.WEATHER_CONTACT): false today
    val enabled: StateFlow<Boolean>                                   // prefs weather/enabled, default false; reads false whenever !available
    fun setEnabled(on: Boolean)                                       // ignored when !available; off calls engine.forget()
    val revision: StateFlow<Int>                                      // +1 on every change of enabled, cache, pin
    fun current(nowMs: Long = System.currentTimeMillis()): WeatherPoint?   // pinned ?: (if (enabled && available) engine.current(...) else null); null = clear sky
    fun pin(p: WeatherPoint?)                                         // no-op unless BuildConfig.TEST_HOOKS; memory only; works with empty contact and the switch off
    val isPinned: Boolean
    suspend fun refreshIfDue()                                        // withContext(IO) engine.refreshIfDue(enabled, AppForeground.isForeground.value, PlaceStore.get(ctx).place.value)
}
// app.ui.pixel (WeatherHost.kt)
@Immutable class WeatherView(val point: WeatherPoint?, val look: WeatherLook)
@Composable fun rememberWeather(): WeatherView        // recomputes on revision and every 60 s; runs repeatOnLifecycle(STARTED) { refreshIfDue(); delay(300_000) } so a request can only start while a screen that calls it is on screen; two callers are safe (the policy refuses the second)
@Composable fun weatherNoteText(point: WeatherPoint): String   // t(R.string.wx_note, temp, condition, credit); Fahrenheit by appLocale().country
```
### 3.5 Look and layers (`app.ui.pixel.stage`, pure)
```kotlin
enum class CloudCover { NONE, LIGHT, MEDIUM, HEAVY }
enum class Precip { NONE, RAIN_LIGHT, RAIN_HEAVY, SLEET, SNOW_LIGHT, SNOW_HEAVY }
enum class FogLevel { NONE, LIGHT, DENSE }
data class WeatherLook(val cloud: CloudCover = CloudCover.NONE, val precip: Precip = Precip.NONE, val fog: FogLevel = FogLevel.NONE,
                       val wind: Float = 0f /* -1..1, 0.1 steps; positive blows to screen right (wind from the west) */,
                       val thunder: Boolean = false, val flash: Int = 0 /* 0 by the clock, 1 forced bright, 2 forced dim; only the pin sets it */) {
    val isClear: Boolean; val wet: Boolean /* precip != NONE */; val rainLevel: Int /* 0 none, 1 light, 2 heavy (rain and sleet) */
    fun starVisibility(): Float      // cloud NONE 1.0, LIGHT 0.75, MEDIUM 0.4, HEAVY 0.1; times fog LIGHT 0.85, DENSE 0.5
    fun bodyVisibility(): Float      // sun and moon: 0.25 + 0.75 * starVisibility()
    companion object { val CLEAR: WeatherLook }
}
fun WeatherPoint.toLook(): WeatherLook
object WeatherWind {
    fun driftCells(wind: Float, u: Float, maxCells: Float = 6f): Float   // sideways drift of a spark or flake at age fraction u: wind * maxCells * u
    fun grassLeanPx(wind: Float): Int                                      // roundToInt(wind * 2), -2..2
    fun flameLean(wind: Float): Float                                      // wind coerced to -1..1
}
```
`toLook` rules: cloud from `cloudPercent` (below 20 NONE, below 50 LIGHT, below 85 MEDIUM, else HEAVY), raised to at least MEDIUM with any precipitation and to HEAVY with heavy precipitation or thunder; precip from the condition (LIGHT_RAIN RAIN_LIGHT, RAIN and HEAVY_RAIN RAIN_HEAVY, SLEET, LIGHT_SNOW SNOW_LIGHT, SNOW and HEAVY_SNOW SNOW_HEAVY); fog FOG gives DENSE when `windMs < 2.0` else LIGHT; wind: `m = clamp((windMs - 1) / 13, 0, 1)`, `east = -sin(radians(windFromDeg))`, `wind = (if (east >= 0) 1 else -1) * m * (0.5 + 0.5 * abs(east))` rounded to 0.1.
```kotlin
class WeatherGeometry(val width: Int, val height: Int, val groundTop: Int, val hearthX: Int, val hearthBase: Int) {
    val farRidgeTop = groundTop - 38; val nearRidgeTop = groundTop - 14        // the constants of SceneRenderer.kt:98-99
}
class WeatherColors(val daylight: Float /* 0 night, 1 day */, val skyBottom: Int /* opaque ARGB */)
class WeatherLayers(seed: Long = 20261125L) {                                  // internal caches, one thread, no Android
    fun lightning(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, timeMs: Long, quality: SceneQuality)          // E2
    fun clouds(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun snowCaps(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, farEdge: IntArray?, nearEdge: IntArray?, artX: Int) // E3
    fun fog(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun precipFar(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun precipNear(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)   // includes E1, E4
}
object WeatherArt { fun topEdges(px: IntArray, w: Int, h: Int): IntArray }     // per column the first row with alpha >= 128, else -1
internal fun lightningPhase(timeMs: Long, seed: Long): Int                     // 0 none, 1 bright, 2 dim
```
Every `WeatherLayers` method returns at once when `look.isClear` (or its own part of the look is NONE) and draws NOTHING for CLEAR, so the clear scene stays pixel-identical (`SceneRendererParityTest`). Call order for SCENE, back to front, between the existing steps of `SceneRenderer.render` (survey B): 1 sky, 2 stars with alpha times `look.starVisibility()`, 3 shooting star only when `look.cloud` is below MEDIUM, 4 moon with alpha times `look.bodyVisibility()`, **4b `lightning`, 4c `clouds`**, 5 far ridge, 6 near ridge, **6b `snowCaps`, 6c `fog`, 6d `precipFar`**, 7 to 15 unchanged, **16 `precipNear` after the foreground grass**. `snowCaps` edges: SCENE computes `WeatherArt.topEdges(farPixels, 176, 46)` when it rebuilds its far-ridge cache and once for `scene_near`.

Behaviour of the layers (all positions are functions of `timeMs`, the seed and the stage size; nothing is allocated per frame):
- **Clouds.** Strip image of `2 * stage.width` by 22, 28 or 36 rows (LIGHT, MEDIUM, HEAVY), built once per (width, cover, daylight bucket `round(daylight * 3)`): seeded ellipse puffs of radius 5 to 12 with flat bottoms, three tones (top, mid, shade; the shade by `SceneArt.bayer`), alpha 0.70, 0.82, 0.92. Puff counts: 3, 6, 10. Day tones lerp from `F2EFF8`, `CBC6DE`, `9C97B8` (day) to `5A5878`, `3C3A58`, `26253E` (night). Placed at y `4 + max(0, (farRidgeTop - 48) / 3)`, clipped at `farRidgeTop - 2`, scrolled by `floor(t / 1000 * (0.9 + 1.4 * abs(wind)))` px (direction `sign(wind)`, right when 0), wrapped by two blits.
- **Rain.** `DropSet(capacity 96, seed)` (seeded arrays: x fraction, speed, phase, length). Per layer counts for a 155-pixel-wide stage (scaled by width / 155, capped at 96): RAIN_LIGHT far 18, near 14; RAIN_HEAVY far 34, near 44; SLEET rain 16 and snow 10 (near only). Fall speed 70 to 110 px/s (heavy times 1.3, far times 0.7), streak of 2 or 3 pixels, slant `wind * 0.5` px per px; colours `B7C4DC` day to `8795B8` night, alpha far 0.35, near 0.70. Far rain falls to `groundTop + 6`, near rain to the stage bottom.
- **Snow.** Same `DropSet`, speed 14 to 26 px/s, sway `sin` amplitude 1 to 3 px, 15 per cent are 2 by 2; counts SNOW_LIGHT far 14, near 12; SNOW_HEAVY far 30, near 34; colour `F4F6FF`, alpha far 0.55, near 0.90; drift `wind * 0.35` px per px.
- **Snow caps (E3).** One pixel in the top row of each ridge column, dithered 50 per cent for SNOW_LIGHT, two rows solid for SNOW_HEAVY, colour `F4F6FF`; a pure function of the look (no accumulation), so a pinned shot shows it at once.
- **Fog.** Strip `2 * width` by 40 rows from `farRidgeTop - 4` to `groundTop - 2`, density a triangle peaking at `groundTop - 24` (LIGHT 0.45, DENSE 0.85), pixel set when `density > SceneArt.bayer(x, y)`, colour lerp of `skyBottom` and `F2EFF8` by daylight, alpha 0.55 (LIGHT) and 0.80 (DENSE); drifts 1 px per 1200 ms.
- **Splashes (E1, E4).** Up to 6 (light) or 16 (heavy) slots, period 700 to 1100 ms, positions from an integer hash of (slot, cycle) over the meadow rows `groundTop + 4 .. height - 16`; one dot for 120 ms, then three pixels in a row for 120 ms, alpha 0.5. E4: two more slots at `hearthX - 20` and `hearthX + 18`, row `hearthBase - 1`.
- **Lightning (E2).** Slots of 20 000 ms; a slot has a flash when `hash(slot, seed) % 100 < 50`, always at 4000 ms into the slot (so two flashes are never closer than 20 s), position fraction 0.15 to 0.85 from the hash; frames: 0 to 80 ms bright (alpha 0.50), 80 to 150 dark, 150 to 250 ms dim (alpha 0.22); a glow of colour `DDE6FF` over rows `farRidgeTop - 24 .. farRidgeTop + 8`, falling off with the distance from the flash x (half width 0.45 of the stage) and dithered. Only with `look.thunder`, FULL quality. `look.flash` 1 or 2 forces the frame for the robot.
- **PLAIN quality** (Pocket.anim REDUCED or OFF): no lightning, no splashes; clouds and fog at offset 0; precipitation as one frozen frame (`t = 0`) at half the near count.

### 3.6 Debug link (`J/DebugLinks.kt`, new extras; in memory, `BuildConfig.TEST_HOOKS` only, `reset=1` clears both)
| Extra | Value | Effect |
|---|---|---|
| `weather` | `off` unpins; presets `clear`, `storm`, `drizzle`, `blizzard`, `mist`; or items joined by commas: `cloud:0..3`, `rain:light\|heavy`, `snow:light\|heavy`, `sleet`, `fog:light\|dense`, `thunder`, `wind:-1..1`, `temp:-60..60`, `flash`, `flash:dim` | `WeatherStore.pin(WeatherPin.parse(v))`; a bad item rejects the whole value (`rejected` lists `weather`) |
| `place` | `off`, or `lat,lon` or `lat,lon,name` | `PlaceStore.pinPlace(Place(lat, lon, name or "Pinned", PINNED))` |

Presets: `storm` = `cloud:3,rain:heavy,thunder,wind:0.7,temp:12`; `drizzle` = `cloud:2,rain:light,temp:9`; `blizzard` = `cloud:3,snow:heavy,wind:-0.6,temp:-6`; `mist` = `fog:dense,temp:4`; `clear` = `cloud:0,temp:18`. Default temperature 11. Mapping to a point: `cloud:0` CLEAR 5 per cent, `cloud:1` PARTLY_CLOUDY 30, `cloud:2` PARTLY_CLOUDY 65, `cloud:3` CLOUDY 95; `rain:light` LIGHT_RAIN 0.6 mm, `rain:heavy` HEAVY_RAIN 6.0, `snow:*` likewise, `sleet` SLEET 1.0, `fog:dense` windMs 0.5, `fog:light` windMs 4; `wind:x` windMs = 0 when x is 0 else `1 + 13 * abs(x)`, from 270 degrees when x > 0 else 90. `DebugRequest` gets two parameters at the END with defaults: `val weather: Setting<WeatherPoint> = Setting.Keep, val place: Setting<Place> = Setting.Keep`. `apply` order becomes: reset, clock, weather, place, lang, rtl, nav, seed, then the route.

### 3.7 Settings rows (`app.ui.settings`, `PlaceWeatherSettings.kt`)
```kotlin
@Composable fun PlaceRows()          // row "Place" (current name and where it comes from), button "Change place" opening a PocketSheet with a PocketTextField, up to 8 result rows, and "Use the time zone"
@Composable fun WeatherSwitchRow()   // draws NOTHING while !WeatherStore.available; otherwise a PocketSwitchRow with the privacy sentence and the credit
object WeatherTags { const val PLACE = "appearance.place"; const val SEARCH = "appearance.place.search"; const val AUTO = "appearance.place.auto"; const val WEATHER = "appearance.weather"; fun result(i: Int) = "appearance.place.result.$i" }
```
### 3.8 Script
`python tools/places/make_places.py --zone-tab <zone.tab> --backward <backward> --cities <file> --format ne|geonames [--min-pop 500000] [--force-include tools/places/force-include.txt] [--out app/src/main/assets/places] [--check] [--selftest]`. Without `--zone-tab` it looks at `/usr/share/zoneinfo/zone.tab`, then at the Python `tzdata` package. Zones: every row of `zone.tab` (about 420) plus one alias row per `Link` of `backward` (about 250, e.g. `Asia/Calcutta` with the coordinates of `Asia/Kolkata`). Cities: population at least `--min-pop` OR a national capital (Natural Earth `featurecla` starts with `Admin-0 capital`, GeoNames feature code `PPLC`) OR named in `force-include.txt` (50 to 80 regional centres under the cut, one per line, chosen by the lead); target 1 200 rows (cap 1 500, file at most 90 KB); the script prints counts and every forced name it did not find, and writes the source file name, release and SHA-256 in the header lines. Licences: IANA tz database public domain; Natural Earth public domain (preferred); GeoNames `cities15000` CC BY 4.0 (fallback, credit in NOTICE). The column names of the Natural Earth file are to be verified against the real file when the script is written (`nameascii`, `name`, `iso_a2`, `latitude`, `longitude`, `pop_max`, `featurecla` as I know them). `--selftest` parses a 5-line inline sample of each format and checks the output rows.

## 4. Shared symbols I CONSUME

| From | Form |
|---|---|
| Wave 0 | `app.util.AppLog.i(tag, msg)`, `AppLog.w(tag, msg, e)`; `BuildConfig.TEST_HOOKS`, `BuildConfig.VERSION_NAME`; `app.service.AppForeground.isForeground: StateFlow<Boolean>`; `PixelStage`, `Argb.withAlpha/lerp/over`, `SceneArt.bayer(x: Int, y: Int): Float`, `SceneQuality` (all in `stage`); `Setting`, `DebugRequest` (`DebugLinks.kt`); `t(@StringRes id, vararg args)` and `appLocale()` (`app.ui.text`); `PocketSheet`, `PocketSwitchRow`, `PocketTextField`, `PixelButton`, `Space`, `Pocket.colors` |
| BUILD (new) | `BuildConfig.WEATHER_CONTACT: String`, empty today (section 7) |
| SCENE | calls my layers in the order of 3.5 and passes `WeatherColors(daylight, skyBottom)` and a `WeatherGeometry` built from its own `g = height - GROUND_H`; owns `SceneState.weather`; computes `daylight` from its sky state |
| APPEAR | calls `PlaceRows()` and `WeatherSwitchRow()` in the Sky group of `AppearanceSection.kt` |
| HOME / SCENE | the tap on the sky calls `PetNotes.show(weatherNoteText(point))` when `rememberWeather().point != null` |
| FIRE, PET | read `WeatherLook` through `SceneState.weather`: FIRE uses `WeatherWind.driftCells` and `flameLean`; PET uses `look.wet`, `look.cloud` |

## 5. Strings: `res/values/strings_weather.xml`, prefix `wx_` (English only; TRANSLATE adds tr and fr)

| Key | English | Note (`t:` and kind) |
|---|---|---|
| `wx_place_title` | Place | row title in Appearance; up to 14 chars \| row |
| `wx_place_sub` | Used to calculate the sky. It stays on the phone unless the weather is on. | subtitle of the place row; 2 lines \| row |
| `wx_place_auto` | Guessed from the time zone: %1$s | %1$s is a city name such as Oslo; \| row |
| `wx_place_picked` | %1$s, %2$s | %1$s city, %2$s two-letter country code; keep the comma \| row |
| `wx_place_change` | Change place | button opening the sheet; up to 18 chars \| button |
| `wx_place_sheet_title` | Choose your place | sheet title \| title |
| `wx_place_search_hint` | Search a city | placeholder of the search field \| body |
| `wx_place_none` | No city found | empty result line \| body |
| `wx_place_use_zone` | Use the time zone | button that goes back to the guess; up to 20 chars \| button |
| `wx_switch_title` | Weather in the scene | switch title \| row |
| `wx_switch_sub` | Shows the weather of your place: rain, snow, fog and clouds. Asks MET Norway about once an hour while the app is open and sends only your place rounded to about 10 km. | privacy sentence under the switch; keep both facts; 4 lines \| row |
| `wx_credit` | Weather data: MET Norway | credit under the sky readout and in Appearance; the service asks for it \| body |
| `wx_note` | %1$s, %2$s\n%3$s | Pêt note after a tap on the sky; %1$s temperature (wx_temp), %2$s condition (wx_cond_*), %3$s credit; the newline separates the two lines \| pet |
| `wx_scene_cd` | Weather: %1$s, %2$s | spoken label of the sky; %1$s temperature, %2$s condition \| body |
| `wx_temp` | %1$d° | Celsius; %1$d is a whole number, may be negative; no space unless the language needs one \| body |
| `wx_temp_f` | %1$d°F | Fahrenheit, for US-like regions \| body |
| `wx_cond_clear`, `wx_cond_fair`, `wx_cond_partly`, `wx_cond_cloudy`, `wx_cond_fog` | clear, mostly clear, partly cloudy, cloudy, fog | condition after the comma in the line "11°, light rain"; lower case in English, up to 16 chars each \| body |
| `wx_cond_light_rain`, `wx_cond_rain`, `wx_cond_heavy_rain`, `wx_cond_sleet` | light rain, rain, heavy rain, sleet | same \| body |
| `wx_cond_light_snow`, `wx_cond_snow`, `wx_cond_heavy_snow`, `wx_cond_thunder` | light snow, snow, heavy snow, thunderstorm | same; `thunder` is shown whenever the point has thunder \| body |

Each key carries its own `<!-- t: ... | kind: ... -->` line in the file (the table groups three to five keys per row for the draft only). No `%` sign outside placeholders; the newline in `wx_note` is a literal `\n`.

## 6. Stored data (the previous version ignores all of it)

| Item | Default | Why the old app ignores it |
|---|---|---|
| SharedPreferences `place`: `mode` ("auto" or "city"), `city_name`, `city_native`, `city_cc`, `city_lat`, `city_lon` (strings, 2 decimals), `city_pop` | absent means auto | a file the old code never opens |
| SharedPreferences `weather`: `enabled` | false | same |
| `files/weather/cache.json` (+ `.tmp`): `{"v":1,"place":"59.9,10.7","fetched":ms,"expires":ms,"attempt":ms,"failures":n,"blocked":ms,"lastModified":"...","points":[...]}`, 12 points of about 80 bytes; the place is the rounded coordinate and nothing else | none; deleted when the switch goes off; a corrupt file is deleted and ignored | a folder the old code never opens; written atomically (tmp then rename), no bulk write |
| Assets `places/zones.tsv`, `places/cities.tsv` | shipped | new files in the APK |
| Build value `BuildConfig.WEATHER_CONTACT` | `""` | build constant |
| Not stored: pinned weather and place (memory); not in backup (`BackupCore.PREF_SCHEMA` is untouched, so a restore leaves place and weather at their defaults; follow-up, section 11) | | |

## 7. REQUESTS

- BUILD: `app/build.gradle.kts`, `defaultConfig` next to line 205: `buildConfigField("String", "WEATHER_CONTACT", "\"$weatherContact\"")` with, above `android {`, `val weatherContact: String = (providers.gradleProperty("weatherContact").orNull ?: System.getenv("AGIRI_WEATHER_CONTACT") ?: "").trim().replace("\\", "").replace("\"", "").take(120)`; default empty in every variant; CI passes nothing today (no `build.yml` change until the owner decides).
- SCENE: draw none of the sky's own day clouds while `weather.cloud != NONE` (risk 12). `ui/pixel/stage/SceneModel.kt:46`: `SceneState` gains `val weather: WeatherLook = WeatherLook.CLEAR` (last parameter). `SceneRenderer.kt`: create `WeatherLayers()` in the class body, make the calls of 3.5 at the stated places, multiply star and moon alpha, skip the shooting star under MEDIUM cloud, build `WeatherGeometry(wl, hl, g, fx, fbase)` and `WeatherColors`, compute the two edge arrays. `HearthScene.kt:102`: `val weather = rememberWeather()` and pass `weather.look`; also `rememberPlace()` for the sky. NOTICE: append three blocks (text: "IANA Time Zone Database (public domain): zone coordinates derived by tools/places/make_places.py"; "Natural Earth populated places (public domain) or GeoNames cities15000 (CC BY 4.0, Contains data from GeoNames): offline city list"; "MET Norway locationforecast (CC BY 4.0 and NLOD 2.0): fetched at run time only when the user turns the weather on, credit Weather data: MET Norway; nothing of it is bundled"). `SceneRendererParityTest` is unaffected while the look is CLEAR.
- APPEAR: `AppearanceSection.kt`: call `PlaceRows()` then `WeatherSwitchRow()` in the sky group; `SoundEngine.kt`: the rain loop follows `WeatherLook.rainLevel` (0 off, 1 soft, 2 loud) read from `rememberWeather().look`; Home or the scene host passes it.
- HOME: the tap on the part of the scene above `groundTop` shows `PetNotes.show(weatherNoteText(point))` when the point is not null, and sets the spoken label `wx_scene_cd` on the sky.
- ROBOT-T / lead: `ScenarioFilesTest.kt` known tags: add `appearance.place`, `appearance.place.search`, `appearance.place.auto`, `appearance.weather` and the pattern `appearance\.place\.result\.\d+`; new scenario `tools/scenarios/weather-pins.json` with the steps of section 9 (safe on owner-debug: it only pins in memory and takes pictures).
- LEAD: (1) the line for the list of what leaves the phone (plan principle 11): "weather, off by default and dark until a contact exists: the rounded place (one decimal), the app name and version and the contact in the User-Agent, to api.met.no, at most once an hour while the app is open; the service sees the phone's IP address as with any request"; (2) decision 6 of the plan, the contact: it is baked into the APK and so is public; recommend a project URL (https page), not an e-mail; (3) section 11 "to verify" list before the switch is ever shown; (4) `docs/waves/w0-contract.md` 3.3 line for `DebugLinks`: two extras and the new `apply` order.
- TRANSLATE: Turkish and French for every `wx_*` key.

## 8. Tests (JUnit 4, JVM; time, place and randomness are parameters; files read with `Assume.assumeTrue(file.exists())`)

| Class | Tests (name: what it proves) |
|---|---|
| `PlacesTest` | `zoneLookupMatchesZoneTab`: Europe/Oslo (+5955+01045 = 59.917, 10.750), Europe/Istanbul (+4101+02858 = 41.017, 28.967), America/New_York (+404251-0740023 = 40.714, -74.006), Australia/Sydney (-3352+15113 = -33.867, 151.217), Asia/Kolkata (+2232+08822 = 22.533, 88.367), each within 0.05 deg, from an inline 5-row table and, when present, from the asset; reference: IANA `zone.tab` |
| | `aliasResolves` (Asia/Calcutta gives Kolkata's place); `unknownZoneFallsBackToOffset` (lon = offset / 240, lat 35.0, source OFFSET); `emptyTableStillGivesAPlace`; `normalizeTable`: İstanbul to istanbul, Diyarbakır to diyarbakir, Zürich to zurich, Łódź to lodz, Reykjavík to reykjavik, Straße to strasse; `searchRanking`: prefix before substring before nothing, then population desc, limit honoured; `searchBelowTwoCharsIsEmpty`; `searchFindsNativeName`; `parseSkipsBadLines`: comment, blank, short, non-numeric and out-of-range rows are dropped, the rest kept; `resolveOrder`: pinned beats picked beats zone; `southernFlag`; `assetsParse` (Assume): at least 400 zones, 800 to 1 500 cities, no duplicate zone id, all coordinates in range; `roundCoordHalfUp`: 59.9496 gives 59.9; 10.75 gives 10.8; -10.75 gives -10.8; -0.04 gives 0.0 (no negative zero); `formatCoordIgnoresLocale`: with the default locale set to Turkish, 10.5 gives "10.5" |
| `WeatherParseTest` | `sampleParses`: the resource has eight hand-built entries in the documented locationforecast 2.0 compact shape (first at 2026-11-25T12:00:00Z, 4.2 C, 5.1 m/s from 250 deg, 88.3 per cent cloud, symbol `lightrain`, 0.4 mm), covering lightrain, plain rain 2.4 mm, heavyrainandthunder, lightssleetandthunder, cloudy, partlycloudy_night, clearsky_day, fog; no coordinates but 59.9, 10.7 and nothing personal; to be replaced by one real recording when the feature is first switched on |
| | `conditionTable`: every MET symbol family gives the condition of 3.2, including the typo forms and the `_day`, `_night`, `_polartwilight` suffixes, unknown gives CLOUDY; `lookFromFirstPoint`: LIGHT_RAIN gives cloud HEAVY, precip RAIN_LIGHT, wind 0.3 (m = 0.315, east = 0.94, factor 0.97); `plainRainStrengthByAmount` (0.9 mm LIGHT, 1.0 mm HEAVY); `fogDenseOnlyWhenCalm`; `thunderRaisesCloud`; `windSignFromDirection` (270 deg positive, 90 deg negative, 0 deg positive and smaller); `atPicksLatestPointAndExpires`; `jsonRoundTrip`; `brokenJsonGivesNull`; `missingFieldsSkipEntry`; `temperatureText` (-0.4 gives "0", 11.5 gives "12", -3.5 gives "-4"); `fahrenheitRegions` |
| `WeatherPinTest` | `presetsAndItems`; `badItemRejectsAll`; `pinGivesTheExpectedLook` for storm, blizzard, mist, `cloud:1`; `flashItems` set `look.flash`; `defaultTemperatureIs11` |
| `WeatherGuardTest` | `contactTable`: "" , " ", "a", "me@example.org" true, "https://example.org/x" true, "http://example.org" false, "x@y" false, "me@exa mple.org" false, a string with a newline false, 121 chars false, " me@example.org" false |
| | `emptyContactNeverCallsTransport`: `WeatherClient.fetch` gives Blocked, calls == 0; `engineWithEmptyContactNeverCallsTransport`: `refreshIfDue(enabled = true, foreground = true, ...)` gives Skipped(NO_CONTACT), calls == 0, over 100 invocations at different times |
| `WeatherPolicyTest` | `decideOrder` (each reason alone and in combination, the first listed wins); `oneRequestPerPlacePerHour`: simulated 3 hours with a tick every 5 minutes, a fake transport answering 200 with Expires 30 minutes ahead: the request times are at least 3 600 000 apart; `expiresLaterThanHourIsHonoured`; `placeChangeGoesButBudgetStops`: seven place changes in an hour allow six; `backoffProgression` (0, 15, 30, 60, 120, 120 minutes); `blocked403For24h`, `blocked429For1h`, `serverErrorBacksOff`; `notModifiedKeepsSeriesAndMovesExpiry`; `ifModifiedSinceOnlyForSamePlace`; `staleCacheShowsClear` (older than 3 hours); `otherPlaceCacheNotShown` |
| `WeatherEngineTest` | `requestShape`: URL is exactly `https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=59.9&lon=10.7` for (59.9496, 10.7461), headers are `User-Agent` of the form "Agiri/<version> <contact>", `Accept`, and nothing else; `userAgentSanitisesVersion` ("0.2.17-local1" stays, "a b" gives "a_b"); `expiresParsing` (RFC 1123, missing gives +1 h); `disabledOrBackgroundNoCall`; `forgetClearsStorage`; storage is an in-memory fake |
| `WeatherIsolationTest` | `onlyKnownFilesOpenConnections` (Assume): in `J/`, the text `openConnection` appears only in `HermesApi.kt`, `SupertonicModelStore.kt` and `WeatherStore.kt`; `api.met.no` appears only in `Weather.kt`; `WeatherEngine.refreshIfDue` is the only caller of `HttpTransport.get` |
| `WeatherLookTest` | `toLookTable`, `starVisibilityTable` (the 4 by 3 products of 3.5), `bodyVisibilityFloor` (never below 0.25), `quantisedWind`, `windHelpers` (`grassLeanPx(0.5) == 1`, `driftCells(1, 1) == 6`), `clearIsClear` |
| `WeatherParticlesTest` | `dropSetDeterministic` (same seed same arrays, different seed differs), `positionsInsideWrapped` (x in 0 until width after wrap for 10 000 random times), `lightningNeverCloserThan20s` over 10 000 slots, fraction of flashing slots within 0.4 to 0.6, `lightningFrames` (0 to 80 ms bright, 150 to 250 ms dim, else 0) |
| `WeatherLayersTest` | `clearDrawsNothing`: every method with CLEAR leaves a filled stage unchanged; `deterministic`: two renders of the same (look, t, size) are equal; `eachLookChangesPixels` for cloud 1 to 3, both rains, sleet, both snows, both fogs; `neverOutsideStage` for widths 8, 120, 155, 176, 300 and heights 60 to 200 (no exception, the stage array length unchanged); `plainIsFrozen`: PLAIN at two times gives equal pixels; `thunderOnlyWithThunder`; `flashForced`; `snowCapsFollowEdges` (cap pixels only in columns with an edge); `starsDimBehindCloud` is SCENE's test |
| `WeatherStillsDump` (E5) | with `-Dweather.stills=<dir>` writes one PNG per look (day and night) with `javax.imageio`; otherwise does nothing |
| `DebugLinksTest` (edit) | `weatherAndPlaceExtras` parse, `weatherBadValueRejected`, `placeRangeChecked`, `resetClearsPins`, release build ignores both |

Reference values for the sky itself (sunrise, moon phase, stars) belong to SCENE; mine are zone.tab above and the MET symbol list (`api.met.no` documentation, "WeatherSymbols"), both to be checked once by the reviewer.

## 9. Proof on the phone (owner-debug, safe: memory pins and pictures only; no request can leave while `WEATHER_CONTACT` is empty)

Link command (the robot adds the token): `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route home --es clock 2027-01-15T22:00 --es place 59.9,10.7,Oslo --es weather rain:heavy,cloud:3,wind:0.6,temp:7`. Pins needed: `weather` (value forms of 3.6), `place` (`lat,lon[,name]` or `off`), and SCENE's `clock`. Stills (each in NEWROZ dark and NERGIZ light): (1) clear baseline, identical to today; (2) `cloud:1`, `cloud:2`, `cloud:3` at 2027-01-15T22:00 (stars dimming; the moon half hidden at `cloud:2`); (3) `rain:light`, `rain:heavy,wind:0.6`, `rain:heavy,wind:-0.6` (slant both ways), at 22:00 and at 2027-07-15T13:00; (4) `snow:light`, `snow:heavy,cloud:3` (caps on both ridges) and `blizzard`; (5) `fog:light`, `fog:dense` (far ridge eaten); (6) `storm,flash` and `storm,flash:dim` (lit horizon); (7) `drizzle` with the clock at dusk 2027-03-21T18:30; (8) a tap on the sky with `temp:11,rain:light` showing the note "11°, light rain" and the credit (needs HOME's tap), once in `lang tr` (TRANSLATE) and once pinned to US (`place 40.7,-74.0,New York`, expects "52°F" when the phone's region is US); (9) Appearance with the Place row, the picker sheet open with "ist" typed (İstanbul listed) and a picked city; the weather switch must be ABSENT (no `appearance.weather` node). Network proof: after the whole run `app.log` has no line `Weather: request` and `dumpsys netstats` shows no traffic for the app's uid from a weather source; the app log line `Weather: skipped NO_CONTACT` (once) appears when Home is opened with a pin. Tags: `appearance.place`, `appearance.place.search`, `appearance.place.auto`, `appearance.place.result.<i>`, `appearance.weather` (absent), plus SCENE's `stage.hearth`. Smoothness: `phone-perf` Home scenario with `storm` pinned against the same without: difference in rendered scene time at most 0.25 ms on the optimised build; the debug build is measured only for the record. A later manual proof once a contact exists (not this half): one request in `app.log` after switching on, none for the next 59 minutes.

## 10. Cost

| What | Per frame (scene at most 30 fps, none while Home is off screen) | Cached |
|---|---|---|
| CLEAR | 0: every method returns first | |
| Clouds | one strip of at most `width * 36` px drawn as two blits (about 5 000 `Argb.over` for 155 x 36) | strip per (width, cover, daylight bucket), at most 2 * 155 * 36 * 4 = 45 KB, rebuilt on a key change only |
| Fog | band of `width * 40` px, about 45 per cent set by the dither (about 2 800 `over`) | strip per (width, level, colour), 50 KB |
| Rain or snow | at most 78 particles x 2 to 3 plots (about 230 plots), heavy case; splashes at most 18 x 3 plots | `DropSet` arrays at construction |
| Snow caps | at most 2 x width plots (310) | edge arrays built by SCENE with the ridge cache |
| Lightning | 0 for 99 per cent of the time; 5 000 `over` during 250 ms of a flash | none |
| Worst case (blizzard or storm) | about 13 000 pixel operations, estimated 0.12 ms on the optimised build against a budget of 0.25 ms for all weather (the scene as a whole stays under 1 ms, design language 11) | |
| Memory | under 200 KB in all | |

No allocation per frame (the particle code works on the `DropSet` arrays and `Int` hashes; `Argb.over` allocates nothing). No own timer: the layers run inside `SceneRenderer.render`, which `rememberSceneClock` gates at 30 fps (`FrameGate`) and stops while the lifecycle is below STARTED, so Home off screen costs 0. PLAIN quality draws one frozen frame. Per minute: `rememberWeather` recomputes the look once a minute (a table lookup, skipped while disabled and unpinned); with the switch on, one check every 5 minutes runs the policy (microseconds) and at most one HTTPS request an hour of about 10 to 40 KB, never in the background, never while the contact is empty. Disk: one 1 KB file.

## 11. Risks and mismatches

1. `docs/design-language.md` 6.1 says `object SceneRenderer { fun render(...) }`, but `SceneRenderer.kt:22` is a class holding its caches; the layers are therefore an instance owned by it.
2. Design language 6.1 says the hero framebuffer is "180 x 160". `StageGrid.forScene` (`StageGrid.kt:17-25`) makes the width `ceil(widthPx / ceil(widthPx / 176))`: 155 pixels for a 1 080 px phone, about 160 on the S24 Ultra, and the height is 280 dp divided by the scale (about 120). All layers therefore take sizes from `stage` and from `WeatherGeometry`, never from 176, 180 or 160. The renderer's own `artX = (wl - 176) / 2` is negative on such stages (`SceneRenderer.kt:70`), so ridge edge arrays are indexed by `x - artX`.
3. The ownership table puts every weather file under `ui/pixel/stage/`, but `StageCoreIsPureTest.kt:14-30` forbids `android.` and `androidx.` imports there; the Compose glue is in `ui/pixel/WeatherHost.kt`, the Android store in `data/`.
4. `SceneState` (`SceneModel.kt:46-53`) has no weather, sky or place field, and `SceneRendererParityTest` fails on any change of the clear picture: the layers draw nothing for CLEAR, so SCENE can keep the oracle until it changes the sky on purpose.
5. `AppClock` has no production reader (survey F); weather deliberately uses the real clock (`System.currentTimeMillis`) for cache and display decisions and the pin for pictures; with a pinned clock and a real weather the two can disagree.
6. Plan 1.3 says "the request carries the place rounded to one decimal"; with the zone guess the place is the typical point of the time zone, not the phone, which is even less personal; the picked city is sent as picked. The user-agent carries the app name, version and the contact (the contact is public in the APK: owner decision 6).
7. Plan 1.3 says "only while the app or its widget is in use": this half has no widget; `rememberWeather` is the only trigger, so the weather stops when Home is not on screen (also when the app is open on Chats).
8. MET terms as I know them, each "to be verified against the terms when the feature is switched on": free use under CC BY 4.0 and NLOD 2.0 with credit (the exact credit wording `wx_credit` is a guess); the endpoint `locationforecast/2.0/compact` is current and not deprecated; a unique identifying User-Agent with a contact is required (generic ones get 403); respect `Expires` and send `If-Modified-Since` with the last `Last-Modified`; coordinates at most four decimals (we send one); HTTPS only; a limit of 20 requests a second per application; 203 means a deprecated product; 429 and 403 mean stop (we stop for 1 h and 24 h; whether `Retry-After` is sent is unknown); whether an app with many installs must go through its own proxy (one owner, one phone today, so not a concern now, but it is for a public release). Also that `symbol_code` lists include the `lightssleetandthunder` typo.
9. No network access in this task: the sample response is hand-built from the documented shape, so the parser is proven against my reading of the format. Record one real response (one `curl` with the contact) when the feature is first switched on and add it as a second sample.
10. Backup: `place` and `weather` prefs are not in `BackupCore.PREF_SCHEMA` (`BackupManager.kt:244`, nobody edits it in Wave 0 or this half), so a restore forgets the picked city; add them in a later half.
11. `DebugLinks.kt` is shared with other areas' needs (SCENE's likely `sky` pin): they send me the exact extra and I add it; nobody else edits the file in this half.
12. Two cloud systems: design language 6.2 gives the SCENE sky "two layers of pixel clouds drift by day" and the weather its own cloud cover. Both would stack. Rule (see section 7, SCENE): while `look.cloud != NONE` SCENE draws none of its own clouds; `WeatherLayers.clouds` is the only cloud layer then. Which one is chosen for CLEAR is SCENE's.
13. Kotlin traps for a build agent that cannot compile: no slash-star inside a comment (write "the stage folder"); `0xFFxxxxxx` literals need `.toInt()`; `BigDecimal(value.toString())` not `BigDecimal(value)`; format numbers with `Locale.ROOT`; declare `DropSet` arrays before the `init` that fills them; `org.json` parses `JSONObject.optDouble` as NaN when missing, test with `has`.
14. Taste risk: procedural clouds may look worse than drawn ones. The Look step needs a reviewer on the contact sheet (E5 gives the stills); if the clouds fail, ART-WORLD draws three cloud sprites and `clouds()` blits them instead (same signature).
15. The city list is a judgement (what is large enough, which regional centres are forced in); the lead owns `force-include.txt`, and nothing in it is on by default or tied to a culture's day.
