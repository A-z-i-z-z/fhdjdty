# Wave 1a, area SCENE: contract draft

Package `dev.pocketagent.app` is written `app`; `J/` = `app/src/main/java/dev/pocketagent/app/`. Facts and line numbers were read at HEAD `fe0e0ea` (survey `dist/w1a/survey.md`, `SceneRenderer.kt`, `SceneModel.kt`, `HearthScene.kt`, `StageGrid.kt`, `AppRoot.kt:234`). BINDING from `docs/design-language.md` 6.2 (seven moments, three star sizes and tints, sixteen moon shapes, real arcs, seasons, three day groups, setting "follows the day / always night / always day", dithered horizon) and section 11 (scene under 1 ms per frame on the optimised build). Every reference number below was computed with an independent prototype of the stated formulas (Python, scratch only); the build agent MUST recompute it against its Kotlin and may not trust it. All `stage/` files are pure Kotlin (no `android.`/`androidx.` import: `StageCoreIsPureTest.kt:14-30`); `java.time` is allowed.

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| S1 | `SkyClock`: GMST/LST, sun (NOAA), moon (low precision), precession, alt/az, sunrise/sunset, solar longitude, moon phase | core |
| S2 | `SkyMoments` (seven moments, blend), `SkyPalette` (tint table from the theme's night palette), `SkyFrame` and `SkyFrames.build` | core |
| S3 | Real stars: `assets/sky/stars.bin`, `tools/art/stars.py`, `StarTable`, `StarLayer`, fallback to today's 46 stars when the table is missing | core |
| S4 | `SkyProjection` (the sky strip above the ridges), sun disc, moon (sixteen procedural shapes, hemisphere-correct), meteors | core |
| S5 | Renderer pipeline: `SceneState` growth, hooks for weather, fire, Pêt, tinted ridges/ground, the dithered horizon, classic path kept statement-for-statement | core |
| S6 | Seasons by date and hemisphere (procedural overlays, `SeasonSetting`) | core |
| S7 | `SceneCalendar` (data format, sky days, app days, user days, culture lists), `SceneDaysStore`, migration that keeps Newroz for existing installs | core |
| S8 | `SkyHost`: `rememberSkyFrame`, `HomeSky` (frame, sky top colour, status-bar icon polarity), `SkyPins` (debug pins), star asset loading | core |
| S9 | Theme variant: accent bias in every tint (core); one prop sprite per theme (E4) | core / extra |
| E1 | Four planets (Standish elements), names of stars and planets, `skyBodyAt` for the tap | extra |
| E2 | Table of eclipses 2026-2030 and the red moon | extra |
| E3 | Effects `LANTERNS` and `FIREWORKS` (the data lines are core, the drawing is extra) | extra |
| E4 | Theme props from ART-WORLD (`scene_prop_*`) | extra |
| E5 | Season sprites from ART-WORLD replacing the procedural overlays (`scene_ground_<season>`, `scene_near_<season>`) | extra |
| E6 | Moonlight on the meadow (`moonLight`) | extra |

Cut order (first cut first): E5, E4, E3, E6, E2, E1, then seasons down to winter and spring, then `MOON_GLOW`. Never cut S1 to S8 or the classic path. A cut feature keeps its symbol with an empty body (`Planets.draw`, `SkyHit`). Taste gate (b): the default is "follows the day" (lead decision 5); "always night" is the classic picture (section 3.6).

## 2. Files (I own them exclusively)

Create, pure (`J/ui/pixel/stage/`): `SkyModel.kt` (enums, `SkyFrame`, `BodyPos`), `SkyClock.kt`, `SkyMoments.kt` (moments and `SkyPalette`), `SkyFrames.kt`, `SkyProjection.kt`, `Stars.kt`, `Planets.kt` (E1), `SkyBodies.kt` (sun, moon, meteors, `skyBodyAt`), `Seasons.kt`, `SceneCalendar.kt` (days and eclipses E2), `SceneLayers.kt` (hook interfaces, `SceneGeometry`, `SceneInput`), `SkyTint.kt` (tinted sprite cache, horizon dither).
Create, Android: `J/ui/pixel/SkyHost.kt` (`rememberSkyFrame`, `HomeSky`, `SkyPins`, `SceneDaysStore`, star loading; deviation from the ownership table: a composable cannot live in `stage/`).
Modify: `J/ui/pixel/stage/SceneModel.kt` (`SceneState` grows), `J/ui/pixel/stage/SceneRenderer.kt` (constructor, pipeline), `J/ui/pixel/HearthScene.kt` (hosts the frame and the other areas' inputs; signature unchanged), `NOTICE` (HYG block, and the three blocks WEATHER asked for, text in `dist/w1a/draft-WEATHER.md` section 7).
Create, data and tools: `app/src/main/assets/sky/stars.bin`, `app/src/main/assets/sky/stars.source.txt`, `tools/art/stars.py`, `app/src/main/res/values/strings_sky.xml` (prefix `sky_`).
Tests (JVM, `app/src/test/java/dev/pocketagent/app/`): `ui/pixel/stage/{SkyClockTest,SkyMomentsTest,SkyProjectionTest,StarTableTest,SkyFramesTest,SkyBodiesTest,SeasonsTest,SceneCalendarTest,SceneRendererSkyTest,PlanetsTest,EclipseTableTest}.kt`, `ui/pixel/SkyPinsTest.kt`. `SceneRendererParityTest.kt` and `LegacyHearthReference.kt` stay UNCHANGED and must pass.

## 3. Shared symbols I PROVIDE

### 3.1 Enums and the frame (`app.ui.pixel.stage`, `SkyModel.kt`)

```kotlin
enum class SkyMode { FOLLOW, NIGHT, DAY }                       // Appearance.sky; NIGHT = the classic picture
enum class SkyMoment { NIGHT, DAWN, SUNRISE, DAY, GOLDEN, SUNSET, DUSK }
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
enum class SeasonSetting { AUTO, SPRING, SUMMER, AUTUMN, WINTER, OFF }   // OFF = no overlay
class BodyPos(val altDeg: Float, val azDeg: Float)              // geometric (no refraction), az from north clockwise
class SkyFrame(                                                  // immutable, identity equality (one instance per rebuild)
    val mode: SkyMode,                    // FOLLOW or DAY only (build never returns a frame for NIGHT)
    val epochMs: Long, val latDeg: Double, val lonDeg: Double, val lstDeg: Double,
    val sun: BodyPos, val moon: BodyPos, val moonPhase16: Int /* 0 new, 4 first quarter, 8 full, 12 last quarter */,
    val moonWaxing: Boolean, val moonIllum: Float, val moonRed: Float /* 0..1, E2 */, val moonLight: Float /* 0..1, E6 */,
    val evening: Boolean, val a: SkyMoment, val b: SkyMoment, val blend: Float, val moment: SkyMoment /* blend < 0.5 ? a : b */,
    val daylight: Float /* 0 night .. 1 day: smoothstep(-12, 6, sunAlt) */, val depressionDeg: Float /* max(0, -sunAlt) */,
    val skyTop: Int, val skyBottom: Int, val farRamp: IntArray, val lift: Float /* 0..1 in steps of 1/16 */, val sunColor: Int,
    val season: Season?, val facingAz: Float /* 180 when lat >= 0 else 0 */,
    val meteorPeriodMs: Int /* 14000, 3500 on a shower night */, val ridgeFires: Int /* 0 none, 1 quiet, 2 festive */,
    val lanterns: Boolean, val fireworks: Boolean, val moonGlow: Boolean, val sunGold: Boolean, val petHat: Boolean,
    val iconsDark: Boolean /* status-bar icons should be dark over skyTop: luma(skyTop) >= 0.55 */,
    val dayIds: List<String>, val planets: List<PlanetPos>,
) { fun debugLine(): String }   // "mode=FOLLOW moment=DAY a=DAY b=DAY blend=0.00 sun=49.2/177.2 moon=8/0.99 lst=247.4 season=SPRING days=newroz-ridge,newroz"
class SkyInputs(
    val epochMs: Long, val latDeg: Double, val lonDeg: Double, val zone: java.time.ZoneId, val mode: SkyMode,
    val night: ScenePalette,              // today's hearthPalette(colors): the NIGHT moment IS this palette
    val accent: Int, val variant: String /* ThemeId name */, val blackNight: Boolean,
    val lists: Set<String>, val userDays: List<UserDay>, val firstLaunchEpochDay: Long?, val seasons: SeasonSetting,
)
object SkyFrames { fun build(i: SkyInputs): SkyFrame? }         // null for SkyMode.NIGHT
```
`DAY` mode: moment DAY, blend 0, sun alt 52 az `facingAz`, no moon, no planets, no stars (`daylight` 1), seasons and days still apply.

### 3.2 `SkyClock` (pure object; formulas and accuracy are the contract)

```kotlin
class SunPos(val raDeg: Double, val decDeg: Double, val lambdaDeg: Double, val eotMinutes: Double)
class MoonPos(val raDeg: Double, val decDeg: Double, val lambdaDeg: Double, val betaDeg: Double)
class AltAz(val altDeg: Double, val azDeg: Double)
object SkyClock {
    fun julianDay(epochMs: Long): Double                         // epochMs / 86_400_000.0 + 2_440_587.5
    fun gmstDeg(epochMs: Long): Double                           // Meeus 12.4: 280.46061837 + 360.98564736629 (JD-2451545) + 0.000387933 T^2 - T^3/38710000, mod 360
    fun lstDeg(epochMs: Long, lonDeg: Double): Double            // (gmst + lon) mod 360, east positive
    fun sun(epochMs: Long): SunPos                               // NOAA solar position (L0, M, C, true longitude, omega correction, obliquity with 0.00256 cos omega), equation of time as in the NOAA sheet; about 0.01 deg
    fun moon(epochMs: Long): MoonPos                             // low-precision lunar series (Schlyter/Meeus truncation: L', D, M, M', F; longitude terms 6.289 sin M', 1.274 sin(2D-M'), 0.658 sin 2D, 0.214 sin 2M', -0.186 sin M, -0.114 sin 2F, +0.059, +0.057, +0.053, +0.046, +0.041, -0.035, -0.030; latitude 8 terms led by 5.128 sin F); about 0.3 deg, geocentric (parallax up to 1 deg ignored)
    fun elongationDeg(epochMs: Long): Double                     // (moon.lambda - sun.lambda) mod 360
    fun moonPhase16(elongationDeg: Double): Int                  // floorMod(floor(e / 22.5 + 0.5), 16): 348.7 gives 15, 348.8 gives 0, 11.2 gives 0, 11.3 gives 1
    fun moonIllumination(elongationDeg: Double): Float           // (1 - cos e) / 2
    fun precess(raDeg: Double, decDeg: Double, epochMs: Long, out: DoubleArray)   // Meeus 21.2 zeta, z, theta rotation from J2000; no proper motion, aberration, nutation or refraction (error < 0.1 deg to 2035)
    fun altAz(raDeg: Double, decDeg: Double, lstDeg: Double, latDeg: Double): AltAz   // H = lst - ra; az = atan2(sin H, cos H sin lat - tan dec cos lat) + 180, from north clockwise
    fun sunriseUtcMs(epochDay: Long, latDeg: Double, lonDeg: Double): Long?   // NOAA: zenith 90.833, two passes (event time re-evaluated); epochDay = UTC date counted from 1970-01-01 whose 12:00 UTC is the reference; null in polar day or night
    fun sunsetUtcMs(epochDay: Long, latDeg: Double, lonDeg: Double): Long?
    fun solarLongitudeDeg(epochMs: Long): Double                 // = sun().lambdaDeg
}
```
### 3.3 Moments and palettes (`SkyMoments.kt`)

```kotlin
class MomentBlend(val a: SkyMoment, val b: SkyMoment, val t: Float)       // t = 0 gives a, 1 gives b
object SkyMoments { fun at(sunAltDeg: Double, evening: Boolean): MomentBlend }
```
`evening` = hour angle in (0, 180) (after the meridian). Keys in sun-altitude degrees, linear segments with `t = smoothstep(fraction)`: MORNING NIGHT <= -18, DAWN -9, SUNRISE -0.8, DAY >= 12 (segments -18..-9, -9..-0.8, -0.8..12); EVENING DAY >= 14, GOLDEN 6, SUNSET -0.8, DUSK -8, NIGHT <= -18 (segments 14..6, 6..-0.8, -0.8..-8, -8..-18). Outside the ends: (NIGHT,NIGHT,0) or (DAY,DAY,0).

```kotlin
class SkyColors(val skyTop: Int, val skyBottom: Int, val farRamp: IntArray, val lift: Float)
object SkyPalette {
    class Tint(val top: Int, val bottom: Int, val topMix: Float, val bottomMix: Float, val lift: Float)
    val TINTS: Map<SkyMoment, Tint>                               // table below; NIGHT has mix 0 and lift 0
    const val THEME_BIAS = 0.10f; const val SUN_GOLD_WARM = 0.25f
    fun colors(night: ScenePalette, blend: MomentBlend, accent: Int, blackNight: Boolean, warm: Float): SkyColors
}
```
| moment | tint top | tint bottom | topMix | bottomMix | lift |
|---|---|---|---|---|---|
| NIGHT | - | - | 0 | 0 | 0 |
| DAWN | `3A4A8C` | `E59A7C` | 0.75 | 0.70 | 0.35 |
| SUNRISE | `4F7BC4` | `FFB86B` | 0.85 | 0.85 | 0.50 |
| DAY | `3D7FD0` | `9CCBF0` | 0.92 | 0.92 | 0.80 |
| GOLDEN | `4A7CC0` | `F2C277` | 0.88 | 0.85 | 0.55 |
| SUNSET | `3B4E8F` | `F27F5A` | 0.85 | 0.88 | 0.50 |
| DUSK | `1F2A5C` | `7A4E86` | 0.75 | 0.70 | 0.30 |

Rule: for a moment `m`, `top(m) = lerp(night.skyTop, lerp(tint.top, accent, THEME_BIAS), topMix)`, same for bottom; with `warm > 0` the tint bottom of SUNRISE, DAY, GOLDEN, SUNSET is first lerped by `warm` towards `F2B35A`. The frame colour is `lerp(top(a), top(b), t)` (and bottom, lift). `blackNight`: `skyTop` and `skyBottom` are lerped to `FF000000` by `(1 - lift)` weight of the night part, i.e. `lerp(c, black, 1 - lift)` (so at NIGHT they are pure black, at DAY unchanged). Far ridge: `farRamp[i] = lerp(night.farRamp[i], lerp(skyBottom, DAY_RIDGE[i], 0.5), lift)` with `DAY_RIDGE = [2D3F6E, 46609A, 6E8DC4, A4BEE6]`. At lift 0 every colour equals the classic palette exactly. `lift` is quantised to 1/16 before use (cache keys). Sun colour: `lerp(FFF4C2, FF9A4A, 1 - clamp(sunAlt / 12, 0, 1))`.

### 3.4 Projection and stars

```kotlin
object SkyProjection {
    const val FOV_AZ_DEG = 220f; const val ALT_TOP_DEG = 80f; const val TOP_ROW = 3; const val HORIZON_BELOW_GROUND = 24; const val NONE = -1
    fun horizonRow(groundTop: Int): Int                                  // groundTop - 24
    fun facingAz(latDeg: Double): Float                                  // 180 (south) when lat >= 0, else 0 (north)
    fun project(azDeg: Double, altDeg: Double, facingAz: Float, width: Int, horizonRow: Int): Int   // packed (y shl 16) or x, or NONE
}
```
The strip is the logical framebuffer above the ridges: the stage is about 155 to 160 by 108 to 110 on the S24 Ultra (`StageGrid.forScene`: scale `ceil(width/176)`; `HearthScene.kt:96` fixes 280 dp), ground top `g = height - 34`, the far ridge top lies between `g-31` and `g-7` (`scene_far` is placed at `g-38`, its silhouette starts at sprite rows 7 to 31), so `horizonRow = g - 24` is the mean ridge line; objects below it are hidden by the ridges drawn later. Projection (the strip looks south when `lat >= 0`, north otherwise, so east is on the left looking south and on the right looking north): `d = wrap180(az - facingAz)`; visible when `abs(d) <= 110` and `-1 <= alt <= 80`; `x = floor(width / 2f + d * width / 220f + 0.5f)`; `y = floor(horizonRow - alt * (horizonRow - 3) / 80f + 0.5f)`; `NONE` when `horizonRow < 12` or the result lies outside 0 until width, 0..horizonRow+1. Sky pixels are therefore 0.70 px per degree across and about 0.6 px per degree up; this is a panorama, not a conformal map (documented in the KDoc). Check: Sirius at Oslo (az 145.55, alt 8.05, width 155, horizonRow 52) lands at (53, 47).

Binary layout of `assets/sky/stars.bin` (little endian): header 12 bytes `"AGST"`, `u8 version = 1`, `u8 magLimitX10 (45)`, `u16 count N`, `u16 nameCount M`, `u16 reserved = 0`; then N records of 8 bytes, brightest first (ties by HYG id): `u16 ra` (`round(raDeg / 360 * 65536) mod 65536`), `i16 dec` (`round(decDeg / 90 * 32767)`), `i8 magX10` (`round(mag * 10)`), `u8 tint` (0 blue-white `ci < 0.15`, 1 white `< 0.75`, 2 warm; unknown `ci` gives 1), `u16 nameId` (0 none, else 1-based index of the name table); then M names each `u8 length (1..24)` and that many UTF-8 bytes (the HYG `proper` of the 64 brightest named stars, without "Sol"). Expected size 7.5 to 9 KB, N 800 to 1000.

```kotlin
class StarTable(val raDeg: FloatArray, val decDeg: FloatArray, val mag: FloatArray, val tint: ByteArray, val nameId: ShortArray, val names: List<String>) {
    val size: Int
    companion object { val EMPTY: StarTable; fun parse(bytes: ByteArray): StarTable }   // never throws: bad magic, version, count, truncation, out-of-range gives EMPTY
}
class StarLayer(val table: StarTable) {
    var visibleCount: Int                                                // after update
    fun update(frame: SkyFrame, width: Int, horizonRow: Int)             // recomputes screen arrays only when (epochMs / 60000, lat, lon rounded to 0.1, width, horizonRow) changed; precession once per UTC day
    fun draw(stage: PixelStage, frame: SkyFrame, visibility: Float, timeMs: Long, quality: SceneQuality)
}
```
Drawing: tiers by magnitude (`STAR_MAG_LIMIT = 4.5f`, tunable in the Look step): `mag <= 2.0` a plus of five (centre full alpha, four arms alpha 0.45), `mag <= 3.0` one bright pixel, else one dim pixel (alpha 0.35 + 0.35 * twinkle). Tints (ARGB): `BCD2FF`, `EEEBFF`, `FFD9A8`. Twinkle `tw = (sin(t * 0.0014 * rate + phase) + 1) / 2` with `rate = 0.6 + 0.2 * (i % 5)`, `phase = i * 2.399963` (golden angle), only for `mag > 2.0`, none in PLAIN. Visibility per star: `a = clamp((depressionDeg - thr) / 3, 0, 1) * visibility` with `thr = 1.0 + 1.5 * (mag + 1.5)` (bright stars appear first at dusk, mag 4.5 at 10 deg depression); `visibility` = WEATHER's `look.starVisibility()`. Fallback: with `StarTable.EMPTY` the renderer draws `SceneArt.stars(wl, g)` (today's 46) with the same alpha rule, so the sky is never empty.

Script (`tools/art/stars.py`, stdlib only; the catalogue is downloaded by whoever runs it and is NOT committed, the derived `stars.bin` is):
`python tools/art/stars.py --hyg <hyg_v*.csv|.csv.gz> [--out app/src/main/assets/sky/stars.bin] [--mag-limit 4.5] [--names 64] [--source-note app/src/main/assets/sky/stars.source.txt]` writes the table and the note (catalogue file name, SHA-256, row count, N, M, the licence line the lead read in the catalogue's README); `python tools/art/stars.py --verify [--bin <path>]` validates the committed file without the catalogue (magic, N 800 to 1000, sorted, ranges, names, size under 12 KB; exit 1 on failure; this is the CI form, to be added to the unit job by the lead); `python tools/art/stars.py --selftest` parses an inline 6-row CSV (Sirius, Polaris, a dim star, "Sol" row id 0, a row without `mag`, a row of mag 5.2) and checks the bytes. Columns used (case-insensitive): `id, ra` (hours), `dec` (degrees), `mag`, `ci`, `proper`. Rows with `id == 0` or no `mag` are skipped; ra hours times 15.

### 3.5 Bodies, hit test, days, seasons

```kotlin
object SkyBodies {
    fun moonImage(phase16: Int, waxing: Boolean, northern: Boolean, red: Float): IntArray   // 13 x 13 ARGB, cached by (phase16, litOnRight, red bucket); disc radius 5.8; MOON_SIZE = 13
    fun drawSun(stage: PixelStage, x: Int, y: Int, frame: SkyFrame, visibility: Float)         // disc 9 x 9 (radius 4: FFF4C2 core, sun colour rim), halo ring radius 5 to 9 dithered alpha 0.18 (`SceneArt.bayer`)
    fun drawMoon(stage: PixelStage, x: Int, y: Int, frame: SkyFrame, visibility: Float)        // halo radius 10 (13 when frame.moonGlow), alpha 0.08 + 0.09 * norm as `drawMoonHalo`, then the image
    fun drawMeteors(stage: PixelStage, timeMs: Long, width: Int, horizonRow: Int, periodMs: Int, depressionDeg: Float)   // today's streak (720 ms, 6 steps) every periodMs, only when depression >= 12; start x from the cycle index
}
```
Moon shape: for pixel `(dx, dy)` in -6..6 inside radius 5.8, `u = dx / sqrt(5.8^2 - dy^2)`, lit when `litOnRight ? u > cos(2 pi p) : u < -cos(2 pi p)` with `p = phase16 / 16` (litOnRight = `moonWaxing == northern`; `northern` = `lat >= 0`, the view then faces south); lit colour `FFF6DC` (red moon `8A3A2A` lerped by `red`), the dark side is drawn as `2A2F52` alpha 0.45 so the silhouette stays (earthshine); phase 0 is only that dark disc. Sixteen shapes, symmetric: lit count of `i` equals that of `16 - i`.

```kotlin
// E1
class PlanetPos(val id: Int /* 0 Mercury .. 4 Saturn */, val altDeg: Float, val azDeg: Float, val mag: Float)
object Planets { fun positions(epochMs: Long, latDeg: Double, lonDeg: Double): List<PlanetPos>; fun draw(stage: PixelStage, frame: SkyFrame, width: Int, horizonRow: Int, visibility: Float) }
class SkyBodyRef(val kind: Int /* 0 star 1 planet 2 moon 3 sun */, val name: String? /* star proper name */, val planetId: Int, val phase16: Int)
fun skyBodyAt(frame: SkyFrame, table: StarTable, boxWidthPx: Float, boxHeightPx: Float, tapX: Float, tapY: Float): SkyBodyRef?   // maps device px to stage px with StageGrid.forScene, radius 2 stage px; sun, moon, planets, then stars with mag <= 3.0 that have a name; null otherwise
```
Planets: Standish "Keplerian elements for approximate positions of the major planets" Table 1 (1800-2050), per century: `a, e, I, L, varpi, Omega` with rates; Kepler equation by Newton to 1e-6; geocentric ecliptic, then equatorial with the obliquity of the date; the elements go into the file exactly as in `dist/w1a/` notes of the build agent's source (Mercury `0.38709927 0.20563593 7.00497902 252.25032350 77.45779628 48.33076593` and rates `0.00000037 0.00001906 -0.00594749 149472.67411175 0.16047689 -0.12534081`, Venus, Earth-Moon barycentre, Mars, Jupiter, Saturn likewise); accuracy under 1 deg (below one pixel). Planets draw as steady points (no twinkle): Mercury `D9D2C0`, Venus `FFF8E0` with four arms alpha 0.35, Mars `FF8F6A`, Jupiter `FFE9B8`, Saturn `E8D7A0`; shown when alt >= 2 and `depressionDeg >= 1 + 1.5 * (mag + 1.5)` using fixed mags Mercury 0, Venus -4, Mars 1, Jupiter -2, Saturn 0.5.

```kotlin
// SceneCalendar.kt, pure
enum class DayEffect { SUN_GOLD, METEORS, MOON_GLOW, ECLIPSE, PAPER_HAT, RIDGE_FIRES, LANTERNS, FIREWORKS }
enum class DayGroup { SKY, APP, CULTURE }
class DayList(val id: String, val group: DayGroup, val defaultOn: Boolean)
class SceneDay(val id: String, val list: String, val rule: String, val effect: DayEffect, val strength: Float)
class UserDay(val month: Int, val day: Int, val name: String)          // name 1..24 chars; at most 12 days
class ActiveDays(val ids: List<String>, val strength: Map<DayEffect, Float>)   // strongest strength per effect
object SceneCalendar {
    val LISTS: List<DayList>; val DAYS: List<SceneDay>                   // parsed from DAYS_DATA at class init; a bad line is skipped
    fun parse(text: String): List<SceneDay>
    fun active(date: java.time.LocalDate, zone: java.time.ZoneId, lists: Set<String>, user: List<UserDay>, firstLaunchEpochDay: Long?): ActiveDays
    fun defaultLists(existingInstall: Boolean): Set<String>              // {sky, app} plus {newroz} when existingInstall
}
```
Data (`DAYS_DATA`, one line per day, `list|id|rule|effect|strength`; adding a day is one line). Rules: `fixed:MM-DD`, `fixed:MM-DD..MM-DD` (may cross the new year), `always`, `sunlon:<deg>` (the local day on which the apparent solar longitude crosses the value: longitude at local midnight is below and at the next local midnight is at or above, with wrap), `moon:new|full` (the elongation crosses 0 or 180 inside the local day, same method), `dates:YYYY-MM-DD+N,...` (N days from the date), `anniversary` (month and day of the first launch), `eclipse` (E2).
```
sky|equinox-mar|sunlon:0|SUN_GOLD|0.6      sky|solstice-jun|sunlon:90|SUN_GOLD|1      sky|equinox-sep|sunlon:180|SUN_GOLD|0.6
sky|solstice-dec|sunlon:270|SUN_GOLD|1     sky|quadrantids|fixed:01-03..01-04|METEORS|0.8   sky|perseids|fixed:08-11..08-13|METEORS|1
sky|geminids|fixed:12-13..12-14|METEORS|1  sky|fullmoon|moon:full|MOON_GLOW|1   sky|eclipse|eclipse|ECLIPSE|1
app|anniversary|anniversary|PAPER_HAT|1    (user days: effect PAPER_HAT, list app)
newroz|newroz-ridge|always|RIDGE_FIRES|0.5  newroz|newroz|fixed:03-20..03-22|RIDGE_FIRES|1   midsummer|midsummer|fixed:06-23..06-24|RIDGE_FIRES|1
newyear|newyear|fixed:12-31..01-01|FIREWORKS|1   bonfirenight|bonfirenight|fixed:11-05..11-05|FIREWORKS|1
lunarnewyear|lny|dates:2026-02-17+3,2027-02-06+3,2028-01-26+3,2029-02-13+3,2030-02-03+3|LANTERNS|1
diwali|diwali|dates:2026-11-08+3,2027-10-29+3,2028-10-17+3,2029-11-05+3,2030-10-26+3|LANTERNS|1
```
Lists: `sky` and `app` default on; `newroz midsummer newyear lunarnewyear diwali bonfirenight` default off (group CULTURE). Effects in the frame: `SUN_GOLD` sets `sunGold` (palette `warm = 0.25`); `METEORS` sets `meteorPeriodMs = 3500`; `MOON_GLOW` sets `moonGlow`; `PAPER_HAT` sets `petHat`; `RIDGE_FIRES` strength >= 0.75 gives `ridgeFires = 2` (festive: 6 spots, +0.15 alpha), else 1 (today's three spots), drawn only when `daylight < 0.5`, BLAZING and FULL quality; `LANTERNS` (E3): 8 warm 1-pixel dots rising 6 px per second from the meadow, alpha 0.7, wrap; `FIREWORKS` (E3): every 7 s one burst above the far ridge (8 dots on a ring growing 1 to 7 px over 900 ms, one of three festive colours); `ECLIPSE`: see E2.

```kotlin
// E2 (SceneCalendar.kt)
class Eclipse(val epochMs: Long, val lunar: Boolean, val kind: Int /* 0 penumbral 1 partial 2 total or annular */)
object Eclipses { val TABLE: List<Eclipse>; fun redAt(epochMs: Long): Float }   // lunar kind 1 or 2: 1 - |dt| / halfWindow, halfWindow 40 min (partial) or 60 min (total); else 0
```
Table (UTC, from NASA's lists as I know them; each checked by the elongation test, 3.9): solar 2026-02-17 12:12, 2026-08-12 17:46, 2027-02-06 16:00, 2027-08-02 10:07, 2028-01-26 15:08, 2028-07-22 02:56, 2029-01-14 17:13, 2029-06-12 04:06, 2029-07-11 15:37, 2029-12-05 15:03, 2030-06-01 06:29, 2030-11-25 06:51; lunar 2026-03-03 11:33 (total), 2026-08-28 04:13 (partial), 2027-02-20 23:13 (pen.), 2027-07-18 16:03 (pen.), 2027-08-17 07:14 (pen.), 2028-01-12 04:13 (partial), 2028-07-06 18:20 (partial), 2028-12-31 16:52 (total), 2029-06-26 03:22 (total), 2029-12-20 22:42 (total), 2030-06-15 18:33 (partial), 2030-12-09 22:28 (pen.). After 2030 there is no eclipse effect (the table ends; follow-up).

Seasons (`Seasons.kt`): `fun seasonFor(date: LocalDate, latDeg: Double, setting: SeasonSetting): Season?` : `OFF` null; fixed settings win; AUTO by calendar months, northern (lat >= 0) Mar to May SPRING, Jun to Aug SUMMER, Sep to Nov AUTUMN, Dec to Feb WINTER, southern shifted by six months; `abs(lat) < 15` gives SUMMER all year. The classic picture has no season. Looks (procedural overlays, built once per (season, stage width, lift step) as 176 x 34 ARGB and blitted at `(artX, g)` right after the ground; deterministic `java.util.Random(7 + season.ordinal)`, only on opaque ground pixels): WINTER snow on the ground (opaque pixels of `scene_ground` in rows 0..5 blended 0.8 to `E9EEFF` where `bayer < 0.7`, rows 6..11 `< 0.45`, rows 12..33 `< 0.25`) and one dithered white row (`F4F6FF`, bayer < 0.5) on the top edge of `scene_near` (columns' first opaque row); SPRING 36 flower pixels in ground rows 3 to 20 (`F7A8C8 FFF3E0 FFE066 C9A8F5`); SUMMER 40 golden tufts of 1 x 2 pixels in rows 2 to 10 (`C9A64A`, `9A7F34`); AUTUMN `scene_near` opaque pixels with `bayer < 0.5` blended 0.55 to `5B2E1E` and 24 leaf pixels in rows 2 to 14 (`C9622A A8441E E0A030`). Optional E5: `SpriteSource.find("scene_ground_<season lower case>")` and `("scene_near_<season>")` replace the procedural result when present.

### 3.6 The renderer (`SceneModel.kt`, `SceneLayers.kt`, `SceneRenderer.kt`)

```kotlin
interface SceneInput                                                // marker; FIRE and PET implement their own
class SceneGeometry(val width: Int, val height: Int, val groundTop: Int, val artX: Int, val hearthX: Int, val hearthBase: Int, val horizonRow: Int)   // hearthX = (width * 0.6f).toInt(), hearthBase = height - 3 (the classic values)
interface FireLayer { fun glow(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality); fun flame(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality) }
fun interface PetLayer { fun draw(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality) }
data class SceneState(
    /* the six existing parameters, unchanged and in the same order */
    val sky: SkyFrame? = null,                       // null = the classic night, today's picture
    val weather: WeatherLook = WeatherLook.CLEAR,    // WEATHER's request
    val pageColor: Int = 0,                          // 0 = no dithered horizon, else the opaque page colour (Pocket.colors.bg)
    val fire: SceneInput? = null, val pet: SceneInput? = null,   // FIRE's and PET's per-frame inputs; read only by their own layers
)
class SceneRenderer(sprites: SpriteSource, stars: StarTable = StarTable.EMPTY, fire: FireLayer? = null, pet: PetLayer? = null, weather: WeatherLayers = WeatherLayers())
fun interface SpriteSource { operator fun get(name: String): Sprite; fun find(name: String): Sprite? = try { get(name) } catch (e: IllegalArgumentException) { null } }
```
`SpriteSource.find` is a default method (existing implementations and `TestSprites` keep compiling). The default arguments keep `SceneRenderer(SpriteBank)` and the tests compiling. A `null` layer means the built-in code of today runs.

Layer order when `state.sky != null` (back to front; the weather calls are WEATHER's exact order, 3.5 of its draft; weather calls are made in BOTH paths and draw nothing for CLEAR):
1 sky gradient (`SceneArt.sky` cached by (w, h, top, bottom)); 2 stars (`StarLayer.draw`, visibility `look.starVisibility()`) or, with an empty table, the old 46; 2b planets (E1); 3 meteors (FULL, and `look.cloud` below MEDIUM); 4 moon (`drawMoon`, alpha `look.bodyVisibility()`; hidden when `moon.alt < -1`, in DAY mode and when `daylight > 0.85` the moon is drawn at alpha 0.55); 4a sun (`drawSun`, alpha `bodyVisibility`); **4b `weather.lightning`, 4c `weather.clouds`**; 5 far ridge with `frame.farRamp`; 6 near ridge (tinted, season); **6b `weather.snowCaps`, 6c `weather.fog`, 6d `weather.precipFar`**; 7 ridge fires; 8 ground (tinted) then the season overlay; 8b moonlight (E6): `lerp` of the meadow rows toward `9FB4E6` by `0.10 * moonLight` at night; 9 `fire.glow` (or built-in glow); 10 `pet.draw` (or built-in); 11 `fire.flame` (built-in flame, smoke and embers when null); 12 fireflies (night only, `daylight < 0.35`, FULL); 12b lanterns and fireworks (E3); 13 foreground grass (tinted); **14 `weather.precipNear`**; 15 horizon dither (3.7). Without a sky (classic) the order is today's 15 steps with the same statements, the weather calls inserted at WEATHER's places, and step 15 of this list at the end. `WeatherGeometry(wl, hl, g, fx, fbase)` and `WeatherColors(frame?.daylight ?: 0f, skyBottom)` are built from the same numbers as `SceneGeometry`. Tinted sprites: `SkyTint.tinted(sprite, tint, amountStep16): IntArray` keeps each pixel's alpha, lerps rgb towards `tint` by `amount = base * lift` (near `2F4A44` 0.35, ground `56684A` 0.50, foreground `3A4A34` 0.50), cached per (sprite, step); at lift 0 the original raster is used (the same array as classic).

Classic = parity: with `sky == null` and `pageColor == 0` the output equals `LegacyHearthReference` for every case of `SceneRendererParityTest` (new fields default; no new statement runs). In production `HearthScene` passes `pageColor`, so the classic picture differs from today in exactly the four bottom rows (3.7); this is the intended fix of finding A9 and `pageColor = 0` switches it off.

### 3.7 The dithered horizon and the host (`SkyTint.kt`, `SkyHost.kt`)

`fun SkyTint.horizon(stage: PixelStage, pageColor: Int)`: when `pageColor != 0`, in the last four rows (`y = height-4 .. height-1`) set the pixel to `pageColor` when `bayer(x, y) < c` with `c = 0.15, 0.40, 0.65, 0.90` (so the last row is about 90 per cent page colour); nothing else changes.

```kotlin
object HomeSky {                                                    // app.ui.pixel; process-wide, written by rememberSkyFrame
    val frame: StateFlow<SkyFrame?>; val skyTop: StateFlow<Int> /* 0 until known; classic: hearthSkyTop */; val iconsDark: StateFlow<Boolean>
}
@Composable fun rememberSkyFrame(): State<SkyFrame?>               // null for always night; read ONLY inside draw lambdas; first value computed synchronously; then repeatOnLifecycle(STARTED) { rebuild; delay(30_000) }; also rebuilds when the pinned clock, place, mode, lists or theme change
@Composable fun rememberStarTable(): StarTable                      // assets/sky/stars.bin on Dispatchers.Default, process-cached, EMPTY on failure (AppLog.w once)
object SkyPins { fun mode(): SkyMode?; fun lists(): Set<String>?; fun setMode(m: SkyMode?); fun setLists(s: Set<String>?); fun clear(); fun parseMode(text: String): SkyMode?; fun parseLists(text: String): Set<String>? }   // memory only; setters no-ops unless BuildConfig.TEST_HOOKS; "off" gives null
class SceneDaysStore private constructor(context: Context) {       // companion fun get(context: Context): SceneDaysStore
    val lists: StateFlow<Set<String>>; val seasons: StateFlow<SeasonSetting>; val userDays: StateFlow<List<UserDay>>
    fun setList(id: String, on: Boolean); fun setSeasons(s: SeasonSetting); fun addUserDay(month: Int, day: Int, name: String): Boolean; fun removeUserDay(index: Int)
}
```
`HearthScene` (signature unchanged): collects `rememberPlace()` (WEATHER), `rememberWeather().look`, `rememberSkyFrame()`, `rememberStarTable()`, FIRE's and PET's inputs, builds `SceneRenderer(SpriteBank, stars, FireLayers.create(classic), PetLayers.create(), WeatherLayers())` in `remember(ready, stars, classic)`, and builds `SceneState(state, palette, progress, mood, stoke, celebration, sky, look, Pocket.colors.bg.toArgb(), fireInput, petInput)` inside `drawBehind`; the `Triple(scene, t, quality)` stamp stays. `SkyMode.NIGHT` (or the pin) gives `sky = null`. In TEST_HOOKS builds `AppLog.i("Sky", frame.debugLine())` once per change of moment or minute.

Status-bar rule: the sky owns `HomeSky.iconsDark` (true when the luma of the frame's `skyTop` is at least 0.55, so a day sky gets dark icons). Who is told: not AppRoot's code by me; see REQUESTS (LEAD for `AppRoot.kt:234`, HOME for `HomeScrim.kt`).

## 4. Shared symbols I CONSUME

| From | Form |
|---|---|
| Wave 0 | `AppClock.nowMillis()` and `AppClock.pinned: StateFlow<Long?>`; `PixelStage` (`plot`, `fillRect`, `blit`), `Argb.lerp/withAlpha/over`, `SceneArt.bayer/sky/stars/glow`, `SpriteSource`, `SpriteBank.find`, `SpriteRaster.argb`, `rememberSceneClock`, `StageSurface`, `StageGrid.forScene`, `SceneQuality`, `ScenePalette`, `hearthPalette(colors)`, `hearthSkyTop`; `Pocket.colors/appearance/anim`; `BuildConfig.TEST_HOOKS`; `AppLog.i/w`; `t(@StringRes, vararg)`; `PetNotes.show` (used by HOME, not by me) |
| WEATHER | `Place(latitude, longitude, name, source, countryCode)` and `rememberPlace(): Place`; `WeatherLook` (`CLEAR`, `isClear`, `cloud`, `starVisibility()`, `bodyVisibility()`); `rememberWeather(): WeatherView` (`.look`); `WeatherLayers` (the six methods) with `WeatherGeometry(width, height, groundTop, hearthX, hearthBase)` and `WeatherColors(daylight, skyBottom)`; `WeatherArt.topEdges` is not used by me (my snow caps are mine) |
| APPEAR | `Appearance.sky: SkyMode = SkyMode.FOLLOW`, `Appearance.blackNight: Boolean = false` (stored in prefs `appearance`, keys `sky`, `blackNight`, by name), `Appearance.classicFire: Boolean` (FIRE's switch, read by HearthScene); the rows call my `SceneDaysStore` |
| FIRE | `object FireLayers { fun create(classic: Boolean): FireLayer? }` (null = built-in classic code); `@Composable fun rememberFireInput(): SceneInput?` in `ui/pixel/FireHost.kt`; its input class implements `SceneInput` |
| PET | `object PetLayers { fun create(): PetLayer? }`; `@Composable fun rememberPetInput(mood: MascotMood?, hearth: HearthState, sky: State<SkyFrame?>, look: WeatherLook): SceneInput?` in `ui/pixel/pet/`; PET reads `SkyFrame.daylight`, `petHat`, `moment`, `season` |
| ART-WORLD | optional: `scene_prop_zagros`, `scene_prop_semender`, `scene_prop_nergiz`, `scene_prop_kawa`, `scene_prop_roj` (each at most 40 x 20 px, 1 frame, 1000 ms, transparent `.`, bottom row sits at `height - 12` and the left edge at x = 6, behind the foreground grass; absent gives nothing); `scene_ground_<spring|summer|autumn|winter>` (176 x 34) and `scene_near_<season>` (176 x 26), same palette chars as `scene.pxl`, replacing the procedural overlays (E4, E5). No sprite is required: the picture is complete without them |

## 5. Strings: `res/values/strings_sky.xml`, prefix `sky_` (English only; TRANSLATE adds tr and fr; each key carries its own `<!-- t: ... | kind: ... -->` line)

| Key | English | Note |
|---|---|---|
| `sky_moment_night`, `_dawn`, `_sunrise`, `_day`, `_golden`, `_sunset`, `_dusk` | Night, Dawn, Sunrise, Day, Golden hour, Sunset, Dusk | spoken label of the sky and the Appearance preview; up to 14 chars each \| body |
| `sky_moon_new`, `_waxing_crescent`, `_first_quarter`, `_waxing_gibbous`, `_full`, `_waning_gibbous`, `_last_quarter`, `_waning_crescent` | New moon, Waxing crescent, First quarter, Waxing gibbous, Full moon, Waning gibbous, Last quarter, Waning crescent | phase name for `phase16 / 2`; up to 20 chars \| body |
| `sky_planet_mercury`, `_venus`, `_mars`, `_jupiter`, `_saturn` | Mercury, Venus, Mars, Jupiter, Saturn | E1; the planet's name in a Pêt note \| pet |
| `sky_note_star` | %1$s, a star | E1; Pêt note after a tap on a named star; %1$s the star's proper name (Latin, not translated) \| pet |
| `sky_note_planet` | %1$s, a planet | E1; %1$s a `sky_planet_*` text \| pet |
| `sky_note_moon` | %1$s, %2$d percent lit | E1; %1$s a `sky_moon_*` text, %2$d 0 to 100 \| pet |
| `sky_note_sun` | The sun | E1 \| pet |
| `sky_scene_cd` | Sky: %1$s, %2$s | spoken label of the scene; %1$s a `sky_moment_*`, %2$s a `sky_moon_*`; up to 40 chars \| body |
| `sky_list_sky`, `sky_list_sky_sub` | The sky's own days / Solstices, equinoxes, meteor showers, full moons and eclipses. | title up to 24 chars; sub up to 2 lines, keep the list \| row |
| `sky_list_app`, `sky_list_app_sub` | Your days / The anniversary of your first launch and the days you add. | \| row |
| `sky_list_newroz`, `_sub` | Newroz / Spring bonfires on the ridges, around 20 to 22 March. | name of the day kept in every language \| row |
| `sky_list_midsummer`, `_sub` | Midsummer / Bonfires on the ridges, around 23 and 24 June. | \| row |
| `sky_list_newyear`, `_sub` | New Year's Eve / Distant fireworks on 31 December and 1 January. | \| row |
| `sky_list_bonfirenight`, `_sub` | Bonfire Night / Distant fireworks on 5 November. | \| row |
| `sky_list_lunarnewyear`, `_sub` | Lunar New Year / Lanterns rising for three days. | \| row |
| `sky_list_diwali`, `_sub` | Diwali / Lanterns rising for three days. | \| row |
| `sky_season_auto`, `_spring`, `_summer`, `_autumn`, `_winter`, `_off` | Follow the date, Spring, Summer, Autumn, Winter, Off | chips of the seasons choice; up to 16 chars \| chip |
| `sky_mode_follow`, `_night`, `_day` | Follows the day, Always night, Always day | chips of the sky choice (APPEAR draws them with my texts); up to 18 chars \| chip |

## 6. Stored data (the previous version ignores all of it)

| Item | Default | Why the old app ignores it |
|---|---|---|
| Prefs `appearance`: keys `sky` (enum name), `blackNight` (boolean), written by APPEAR | `FOLLOW`, false | the old `AppearanceStore.load` reads known keys by name only |
| New prefs file `scene_days` (mine): `lists` (comma-joined ids), `seasons` (enum name), `user_days` (JSON `[{"m":3,"d":21,"n":"name"}]`, at most 12), `migrated` (int 1) | `lists = defaultLists(existing)`, `seasons = AUTO` | a file the old code never opens |
| Migration: when `migrated` is absent, `existing` = `files/rootfs.stamp` exists or `files/chat/` has any file; then `lists` becomes `sky,app` plus `newroz` for an existing install (it keeps today's bonfires: `newroz-ridge` draws the three quiet ridge fires every night as today, the festival strength on 20 to 22 March), `sky,app` for a new one; `migrated=1` is written once (one small write) | | same |
| Assets `sky/stars.bin` (about 8 KB), `sky/stars.source.txt` | shipped | new files in the APK |
| Not stored: the frame, pins (`SkyPins`), star projection caches. Not in backup (`BackupCore.PREF_SCHEMA` untouched, as for WEATHER) | | |

The stored new fields are optional; deleting `scene_days` only re-runs the migration. No bulk write; the first-launch date comes from `PackageInfo.firstInstallTime` read once into the frame inputs (nothing stored).

## 7. REQUESTS to other areas

- WEATHER (owner of `DebugLinks.kt` for this half): two extras, in memory, `BuildConfig.TEST_HOOKS` only, `reset=1` calls `SkyPins.clear()`: `sky` = `follow|night|day|off` (`SkyPins.setMode(SkyPins.parseMode(v))`, a bad value is listed in `rejected`) and `days` = comma list of list ids or `off` (`SkyPins.setLists(SkyPins.parseLists(v))`); add both names to the list at `DebugLinks.kt:126`, two fields at the END of `DebugRequest` with `Setting.Keep` defaults, apply them after `place`; two tests in `DebugLinksTest.kt`.
- APPEAR: `Appearance.kt`: `val sky: SkyMode = SkyMode.FOLLOW`, `val blackNight: Boolean = false` (last parameters, stored and loaded by name like `haptics`); `AppearanceSection.kt`: sky chips (`sky_mode_*`), place rows (WEATHER's `PlaceRows()`), seasons chips (`sky_season_*`, `SceneDaysStore.setSeasons`), the days rows (one `PocketSwitchRow` per `SceneCalendar.LISTS` with `sky_list_<id>` and `_sub`, `SceneDaysStore.setList`), user days (add: month, day, name; remove), black night switch.
- HOME: `HomeScrim.kt`: scrim colour from `HomeSky.skyTop` (fallback `hearthSkyTop(colors)` while 0); the early comment "the colour is dark in every theme" is no longer true. `HomeScreen.kt:167-195` tap: before the stoke action call `skyBodyAt(HomeSky.frame.value, table, w, h, x, y)` for taps above the ridges (E1); `ui_home_mascot_cd` stays but add `sky_scene_cd` as the scene's spoken label when a frame exists.
- LEAD: `AppRoot.kt:234` becomes `SystemStatusBarIcons(darkIcons = ((route != Route.Home || practiceOn) && !c.isDark) || (route == Route.Home && !practiceOn && HomeSky.iconsDark.collectAsState().value))` (SHELL is Wave 1b); CI unit job gets `python3 tools/art/stars.py --verify` and `--selftest`; download the HYG catalogue once, read its licence line and version, run the script, commit `stars.bin` and `stars.source.txt`; plan 1.2 text "about 900 stars" becomes "800 to 1000".
- FIRE: implement `FireLayer` and `FireLayers.create`, `rememberFireInput`; `FireLayer.glow` replaces steps 9, `flame` replaces flame, living edge, smoke, sparks (not fireflies); their per-frame values come in `SceneState.fire`. They read `SceneGeometry` for positions.
- PET: implement `PetLayer` and `PetLayers.create`, `rememberPetInput`; Pêt's draw is step 10 (between the glow and the flame).
- ART-WORLD: the optional sprites of section 4 (E4, E5); nothing required.
- ROBOT-T: scenario `tools/scenarios/sky-pins.json` (section 9), known tags unchanged (`stage.hearth`); `app.log` lines `I/Sky:` are the check.
- TRANSLATE: Turkish and French for every `sky_*` key.

## 8. Tests (JUnit 4, JVM; time, place and randomness are parameters; repo files read with `Assume.assumeTrue(file.exists())`)

| Class | Test: what it proves (reference values; the build agent recomputes each against its code and fixes the code, never the number, unless the number is shown wrong against the cited source) |
|---|---|
| `SkyClockTest` | `gmstMatchesMeeus`: J2000 (2000-01-01T12:00Z) 280.46061837 deg (1e-6); 1987-04-10T00:00Z 13h10m46.3668s = 197.693195 deg (3e-5); 1987-04-10T19:21Z 8h34m57.0896s = 128.737873 deg (3e-5); Meeus examples 12.a, 12.b |
| | `sunriseSunsetSixPlaces` (tolerance 2 min; the computed value, then the published one recalled from timeanddate.com, to be checked once): London 2027-06-21 03:43 / 20:21 UTC (published 04:43 / 21:21 BST); New York 2026-12-21 07:16 / 16:31 EST (16:32); Oslo 2026-12-21 09:18 / 15:11 CET (09:18 / 15:12); Istanbul 2027-03-21 07:06 / 19:16 (+03); Sydney 2026-12-21 05:40 / 20:05 AEDT (05:41 / 20:05); Diyarbakir 2027-03-21 06:21 / 18:31 (+03); `polarDayAndNight`: Tromso (69.65, 18.96) 2026-12-21 and 2027-06-21 give null for both |
| | `sunNoonAltitudeIsGeometric`: peak altitude within 20 min of solar noon equals `90 - abs(lat - dec)` (0.05 deg) at Istanbul 2027-03-21 (49.2), Oslo 2026-12-21 (6.6), London 2027-06-21 (61.9) |
| | `equinoxLongitude`: sun lambda 0.000 deg at 2027-03-20T20:25Z and 180.0 deg at 2026-09-23T00:05Z, 270.0 at 2026-12-21T20:50Z, 90.0 at 2027-06-21T14:10Z (tolerance 0.03) |
| | `moonElongationAtKnownSyzygies` (tolerance 1.5 deg; the prototype gives under 0.2): new moons 2000-01-06T18:14Z, 2017-08-21T18:30Z, 2024-04-08T18:21Z (0 deg); full moons 2000-01-21T04:40Z, 2022-11-08T11:02Z, 2025-03-14T06:55Z (180 deg); `moonPhase16Buckets` (0.01 gives 0, 11.2 gives 0, 11.3 gives 1, 90 gives 4, 179.98 gives 8, 270 gives 12, 348.7 gives 15, 348.8 gives 0) |
| | `starPlacesAtInstants` (tolerance 0.15 deg; J2000 ra/dec: Sirius 101.2871 -16.7161, Vega 279.2347 +38.7837, Arcturus 213.9153 +19.1824, Polaris 37.9546 +89.2641): Sirius at Oslo (59.9139, 10.7522) 2027-01-15T20:00Z alt 8.05 az 145.55; Vega at Istanbul (41.0082, 28.9784) 2027-07-15T19:00Z alt 65.43 az 84.52; Arcturus at Sydney (-33.8688, 151.2093) 2027-04-10T14:00Z alt 35.31 az 16.80; also confirm each against a planetarium once |
| | `starCulmination`: scanning one minute steps over 2027-01-15 the maximum altitude equals `90 - abs(lat - dec)` (0.1 deg) with dec precessed: Sirius at Oslo 13.34 az 180, Vega at Istanbul 87.80, Arcturus at Sydney 37.07 az 0, Polaris at Oslo 60.54 az 0 (az check mod 360 within 1 deg, not for Vega) |
| `SkyMomentsTest` | `keysMorningEvening`: (-20, morning) NIGHT/NIGHT 0; (-9) DAWN exactly; (-0.8) SUNRISE; (15) DAY; evening (14) DAY, (6) GOLDEN, (-0.8) SUNSET, (-8) DUSK, (-18) NIGHT; `continuous`: sweeping -25..25 in 0.05 deg, `blend` never changes by more than 0.08 per step and stays in 0..1; `istanbulEquinoxSequence`: sun alt -7.72 (03:30Z) DAWN side of morning, -0.94 (04:06Z) near SUNRISE, 5.48 (04:40Z), evening 13.56 (15:00Z) GOLDEN/DAY blend, -0.68 (16:16Z) near SUNSET, -8.94 (17:00Z) near DUSK, -14.49 (17:30Z) DUSK/NIGHT blend |
| `SkyFramesTest` | `nightModeIsNull`; `nightMomentEqualsClassicPalette` (alt -30: top, bottom and ramp equal `TestPalettes.DARK` exactly, lift 0); `dayIsBlueAndBright` (DAY top: blue channel above red, luma above 0.4; `iconsDark` true for each theme accent in a list of six, false at night); `colourContinuity` (sweep altitude, no channel jumps above 6 per 0.1 deg step); `blackNightIsBlackAtNight`; `seasonTable` (2027-01-15 at 59.9N WINTER, at -33.9 SUMMER; 2027-04-10 at 41N SPRING, at -33.9 AUTUMN; Singapore 1.35N SUMMER in January; setting OFF null); `deterministic` (two builds equal field by field); `dayModeHasNoStars` ; `facingNorthInSouth` (`facingAz` 0 for lat -33.9); `daysFlow`: 2027-03-21 at Diyarbakir with lists {sky, app, newroz} gives `ridgeFires == 2`, `sunGold == true` (equinox on 2027-03-20 UTC; local day depends on the zone: test both 03-20 and 03-21 with their zones) |
| `SkyProjectionTest` | `centreAndSides` (width 155, horizonRow 52: az 180 alt 0 gives (77, 52); az 270 alt 30 right of centre when facing south, left when facing north); `edges` (alt 80 gives y = 3, alt -2 NONE, az 291 NONE, az 290 visible); `siriusOslo` (53, 47); `tinyStage` (`horizonRow < 12` gives NONE) |
| `StarTableTest` | `parsesSyntheticTable` (a 3-star table built in the test from the layout of 3.4: exact ra, dec, mag, tint, name); `badInputsGiveEmpty` (truncated, wrong magic, version 2, count beyond length); `realAsset` (Assume): N 800 to 1000, sorted by mag, first is Sirius (mag -1.46 within 0.05, ra 101.29, dec -16.72, within 0.03), Polaris present, all three tints used, every `nameId` valid; `projectionCache` (second `update` with the same key does not recompute: a counter); `sirius` projected for Oslo at the instant above lands within 1 px of (53, 47) on a 155 stage with `horizonRow = 52`; `rotatesWestward` (one hour later a star in the south moves right by about 10 px: 15 deg times 0.70 px) |
| `SkyBodiesTest` | `moonSixteenShapes` (16 images, distinct, lit counts symmetric, 8 is the largest, 0 has none); `moonHemisphere` (waxing northern lit on the right half, waxing southern on the left); `sunDisc` (centre pixel is `FFF4C2`); `meteorsOnlyWhenDark`; `skyBodyAt` (E1: a tap on the moon, on Sirius, on empty sky) |
| `SeasonsTest` | `overlayDeterministic`, `overlayOnlyOnOpaqueGround` (every overlay pixel has an opaque ground pixel under it), `fourSeasonsDiffer`, `winterCapsOnRidgeEdge`, `classicHasNoSeason` |
| `SceneCalendarTest` | `dataParses` (every line of `DAYS_DATA` gives a day; ids unique; lists known; only `sky` and `app` default on); `fixedAcrossNewYear` (12-31..01-01); `equinoxExactlyOneDay` (2027-03-19..21 in UTC: exactly 03-20; 2026-09-22..24: exactly 09-23); `solsticeDec2026` (exactly 12-21); `fullMoonDay` (2025-03-13..15 UTC: exactly 03-14); `datesTable` (2027-02-06 to 02-08 active, 02-09 not); `lunarNewYearAndDiwaliAreNewMoons` (elongation at 12:00Z of each table date within 14 deg of 0 or 360); `listsOffByDefault` (a culture day is inactive unless its list is on); `userDay` and `anniversary`; `migration` (`defaultLists(true)` has newroz, `false` not) |
| `SceneRendererSkyTest` | (uses `TestSprites.all()`, `TestPalettes`) `classicUnchangedByNewFields` (state with new fields at defaults equals the parity reference for three cases); `dayDiffersFromNight`; `deterministic`; `neverOutside` (widths 8, 120, 155, 176, 300; heights 44 to 200); `dayShowsNoStarPixels` (no pixel of the three star tints above `horizonRow` at daylight 1); `horizonDither` (last row at least 80 per cent `pageColor`, row `height-5` untouched); `plainIsStatic` (PLAIN at two times equal); `fallbackStarsWithEmptyTable`; `hooksCalledInOrder` (fake `FireLayer`, `PetLayer` record the order glow, pet, flame) |
| `PlanetsTest`, `EclipseTableTest` (extras) | planets: elongation of the planet from the sun at 180 deg within 1.5 deg (the elements are J2000 and the sun is of date, 0.35 deg) at Mars 2025-01-16T02:00Z (prototype 179.7), Jupiter 2026-01-10T16:00Z (179.3), Saturn 2025-09-21T12:00Z (179.5); eclipses: table sorted, each solar entry has elongation within 1 deg of 0 and each lunar within 1 deg of 180 (the prototype gives under 0.2 for all 24), `redAt` is 0 outside the window and 1 at the maximum |
| `SkyPinsTest` | `parseMode` (`follow`, `NIGHT`, `day`, `off` gives null, junk null); `parseLists` (`sky,newroz`, `off`, unknown ids dropped, empty gives empty set); setters are inert when `TEST_HOOKS` is false (the test calls the pure parsers only) |

## 9. Proof on the phone (owner-debug, safe: memory pins and pictures)

Pins: `clock` (existing), `place` and `weather` (WEATHER), `sky=follow|night|day`, `days=sky,app,newroz` (new, 7). The theme and the dark or light mode are set once through Appearance by the lead (NEWROZ dark, NERGIZ light). Link form: `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route home --es clock 2027-03-21T20:30 --es place 41.0,29.0,Istanbul --es sky follow --es weather off --es token <T>` (the robot adds the token). Stills, each in NEWROZ dark and NERGIZ light, Istanbul unless said: (1) `sky night`: identical to build 32 apart from the four dithered bottom rows (parity shot with animations Off); (2) the seven moments on 2027-03-21 at Istanbul local 06:30, 07:06, 13:00, 18:40, 19:16, 20:00, 21:00 (sunrise 07:06 and sunset 19:16, +03): dawn, sunrise, day, golden hour, sunset, dusk, night (the sun's disc and arc visible and moving from east on the left); (3) 2027-01-15T21:00 at Oslo (59.9, 10.7): Sirius low in the south-east at about (53, 47) on a 155 stage, Polaris not shown (north is off the strip), the moon per its phase; the same hour at Sydney (`place -33.9,151.2`): the strip faces north, the moon lit on the other side; (4) the planetarium check: three stars and the moon at 2027-01-15T20:00Z Oslo against Stellarium or similar, by eye; (5) seasons: 2027-01-15, 04-10, 07-15, 10-15 at Istanbul and the southern mirror at Sydney; (6) 2027-08-12T23:30 (Perseids, `days sky`): meteors every few seconds; 2027-08-17T05:00Z lunar-eclipse window if E2; (7) `days sky,app,newroz` on 2027-03-21 evening: festive ridge fires; without newroz on the same date none (taste: new install) and with the migrated list on any other date the three quiet fires; (8) status-bar: a day shot with dark icons over the sky and the scrim colour matching; (9) Appearance with the sky chips, the seasons, the days rows (APPEAR's pictures). Log check: `app.log` has `I/Sky: mode=FOLLOW moment=... sun=.../... moon=.../... season=...` once per change. Smoothness: `phone-perf` Home scenario pinned at 2027-03-21T20:30 (night, stars) and at 13:00 (day) against `sky night`: scene draw difference at most 0.15 ms on the optimised build; the debug build is measured only for the record (today about 10 ms per frame on the debug build, most of it outside my part: the scale-up and Compose). Taste gate (b) pictures: dawn, day, dusk, night in NEWROZ and NERGIZ next to the classic night.

## 10. Cost

| What | Per frame (30 fps at most, nothing while Home is off screen) | Per minute or on change |
|---|---|---|
| Gradient | blit of `w x (g + 8)` about 13 000 opaque pixels (as today) | rebuild only when the two colours change (a few times per hour, about 0.1 ms) |
| Stars | at most about 300 visible stars: one plot each, a plus for about 40, one `sin` for dim ones (about 20 000 pixel operations) | projection 900 stars x (one cos, one atan2, one sin): once per minute or on a size or place change; precession once per UTC day (about 15 000 trig, under 1 ms, on the draw thread once) |
| Sun, moon | about 250 pixels (cached 13 x 13 images, 16 shapes) | none |
| Tinted ridges and ground, overlay | the same blit count as today plus one 176 x 34 overlay blit (6 000 reads, few writes) | `SkyTint` rebuild when `lift` steps change (about 1/16 steps: roughly every 3 minutes at dawn), 3 sprites x 6 000 pixels |
| Horizon dither | 640 pixels | none |
| Meteors, ridge fires | as today (FULL only) | none |
| Frame rebuild (`SkyFrames.build`) | none | every 30 s while Home is started: about 1 000 flops, calendar lookups, under 0.3 ms |
| Total added | about 30 000 simple pixel operations, estimated 0.1 ms on the optimised build (budget 0.15 ms for this part inside 1 ms) | memory: stars 8 KB table, 4 KB arrays, caches under 150 KB |

No allocation per frame (arrays are sized at construction, `StarLayer` reuses its arrays, the tint and overlay caches are keyed). PLAIN quality: no twinkle, no meteors, no fires, no lanterns, one frozen look. Off screen: `rememberSceneClock` stops below STARTED, and the frame loop is `repeatOnLifecycle(STARTED)`, so nothing runs while Home is hidden. No sensor, no network, no permission.

## 11. Risks, and what in the plan or the design language is wrong given the real code

1. `docs/design-language.md` 6.1 shows `SceneState(hearth, intensity, sky: SkyState, season, theme, pet, wind, event, budgetLeft)` and `object SceneRenderer`; the real `SceneState` is a six-field data class (`SceneModel.kt:46-53`) and `SceneRenderer` is a class that owns its caches (`SceneRenderer.kt:22`). I grow the real class with defaulted fields and carry the sky as one `SkyFrame`; FIRE and PET carry their own inputs behind `SceneInput`.
2. 6.1 says the hero framebuffer is 180 x 160. `StageGrid.forScene` (`StageGrid.kt:17-25`) and `HearthScene.kt:96` give about 155 to 160 by 108 to 110, and `artX = (width - 176) / 2` is negative (the 176 px art is cropped at the sides). The sky strip above the ridges is only `g - 31` rows at the highest peak and about 52 on average, so the projection and every size use the stage's own numbers, never 176, 180 or 160.
3. The sky hint "about 900 stars" is a density risk: about 450 are above the horizon and about 40 per cent of the strip lies behind ridges, which can look busy against today's 46. `STAR_MAG_LIMIT` and the dim-pixel alpha are the two knobs for the Look step. The panorama is not conformal (0.70 px per degree across, 0.6 up); constellations look squashed near the top. A true planetarium comparison is by eye for three stars and the moon only, as the plan says.
4. `SceneRendererParityTest` stays valid only because the classic path is untouched and `pageColor` defaults to 0 (`LegacyHearthReference.kt:10-20`). Any edit of a classic statement must be mirrored there or it fails. Always night is therefore today's picture with today's 46 stars and today's crescent; if the owner prefers real stars at night in that mode, `SkyMode.NIGHT` can build a frame pinned to a night instant instead (one line in `SkyHost`) at the price of the parity reference being gone for production (the test keeps the `sky == null` path).
5. Light-theme finding A9 (the plan cites `HearthScene.kt:189`, a stale line; the palette is at `HearthScene.kt:38-56`): the night sky was always dark in light themes. Only `pageColor` (the dithered horizon) and the day sky fix it; the status-bar polarity needs `AppRoot.kt:234` and `HomeScrim.kt:34-37`, whose comments assume a dark sky in every theme. If the lead does not take those two edits, the day sky shows white icons on a pale blue strip (a visible defect): the two edits are part of this half.
6. `AppClock` has had no reader (`AppClock.kt:10-12`, survey F): the sky is the first one, with a frozen pinned clock, so a pinned shot never moves; the real weather uses the real clock (WEATHER risk 5), so a pinned date with real weather can disagree.
7. `HomeScreen.kt:167-195` makes the whole scene one tap target for the stoke; tapping a star there needs HOME to route taps above the ridge to `skyBodyAt` first (E1). The plan's "tap on a bright star shows its name" and WEATHER's "tap on the sky shows the weather" share that tap: HOME decides the order (body first, then weather).
8. The plan says the star credit is CC BY-SA 4.0. HYG v3 files carried CC BY-SA 2.5 and the later versions 4.0: the lead reads the licence of the downloaded version and `stars.source.txt` records it; the NOTICE block says "derived table shared under the same terms" and names HYG (David Nash, with data from Hipparcos, the Yale Bright Star Catalog and the Gliese catalogue). The committed `stars.bin` is the shared derived table, which is the one share-alike piece the plan allows.
9. Accuracy limits stated, not hidden: the sun about 0.01 deg, the moon about 0.3 deg and parallax up to 1 deg ignored, stars within 0.1 deg until 2035 (no proper motion), refraction and the apparent disc size ignored, planets under 1 deg. The Standish elements and the eclipse and festival dates are written from memory of the sources: every one is checked by a test against an independent fact (oppositions, elongation at each eclipse, new-moon days for the two lunar calendars), and the festival dates for Diwali 2028 to 2030 and the Lunar New Year 2029 and 2030 are the least certain (the tests allow 14 degrees of elongation); a wrong date is a data error, not a bug.
10. The first-launch anniversary uses `PackageInfo.firstInstallTime`, which is the update time on some devices after a reinstall; a user who reinstalls gets a new anniversary. Acceptable.
11. An existing install keeps the three quiet ridge fires every blazing night only while the `newroz` list is on (default on for existing installs). If the owner switches it off in Appearance the fires vanish; that is the user's choice, not a silent loss. New installs never see them unless they choose Newroz.
12. The seasons are meteorological and procedural; the near ridge is almost black (`a`, `A`, `T` only), so autumn and winter on it rely on dithered overlays that the reviewer must judge on a contact sheet; if they fail, ART-WORLD's `scene_near_<season>` (E5) is the escape, with the same call.
13. Kotlin traps for an agent that cannot compile: no slash-star inside a comment ("the stage folder", "the pixel files"); `0xFFxxxxxx` needs `.toInt()`; use `Math.floorMod` for angles, `Locale.ROOT` for any formatting, Double for astronomy and Float only for pixel maths; declare arrays before the `init` that fills them; `SpriteSource.find` default must catch `IllegalArgumentException`; `enum` lists in tests are explicit (no kotlin-reflect); `java.time` only (no `android.text.format`); the `stage` folder must keep no Android import.
