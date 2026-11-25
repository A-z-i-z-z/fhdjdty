# Wave 1a, area FIRE: contract draft

Package `dev.pocketagent.app` is written `app`. `J/` is `app/src/main/java/dev/pocketagent/app/`. Line numbers were read at HEAD `fe0e0ea` (SceneRenderer.kt, HearthScene.kt, HomeScreen.kt, ChatEngine.kt, DebugLinks.kt). BINDING from `docs/design-language.md` 6.3 (hand-keyed core, living edge, glow that breathes, wind from tilt, intensity continuous, heat haze Android 13+ FULL only), 4 (fire: tap to stoke or light, hold to put out, a ring of embers fills while holding), 3 pattern 19 (fire 8 to 12 fps: the five sprites already run at 95 to 130 ms a frame), section 11 (scene under 1 ms), section 10 (animation never carries information alone). Findings cited: A8 (scene cost), A13 (the fire is the switch).

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| F1 | `FireFeed`: process-wide "how hard is the agent working", pure decay maths, four call sites in `ChatEngine.kt` | core |
| F2 | Move the fire drawing out of `SceneRenderer` into `FireLayer` (glow, flame, sparks) with a CLASSIC path that is today's picture pixel for pixel | core |
| F3 | Reaction table: asleep, waking, running idle (settled), working, roar, stoke; sprite choice by level, glow breathing | core |
| F4 | Living edge: seeded tongues above the flame, side licks, wind lean of the upper rows. The five sprites are never edited and no opaque sprite pixel is ever overdrawn | core |
| F5 | Sparks with wind (replace the embers when not classic) | core |
| F6 | "Classic fire" switch: `FirePrefs`, `FireSettingsRows()` | core |
| F7 | Switch support for HOME: `FireGeometry.hitRect`, LIGHT and DOUSE transitions (900 and 700 ms), hold ring | core |
| F8 | `FireDriver` (Compose glue), `FireDebug` pin and the `fire` link extra, `I/Fire:` log lines | core |
| F9 | Side-by-side pictures for the taste gate (JVM dump test) | core |
| E1 | Wind from tilting the phone (`FireTiltController`, accelerometer only while Home is on screen at FULL) | extra |
| E2 | Heat haze (AGSL `RenderEffect`, API 33+, FULL, not classic) | extra |

Cut order (first cut first): E2, E1 (keep `FireParams.wind` and the pin), side licks, windGain of sparks, LIGHT/DOUSE sprite steps (keep the hold ring), glow breathing. Never cut: F1, the CLASSIC path and its parity test, `FireGeometry`, the `FireLayer` API of 3.3. A cut part keeps its symbol with an empty body. Not built: wind from fast scrolling (HOME has no velocity source), a soot cough on `TurnFailed` (needs a fifth call site), a voice-loudness feed (Wave 8 calls `FireFeedCore.kick`), the crackle loop (APPEAR's; I only expose the level).
No new art, no new palette entry: FIRE needs nothing from ART-WORLD except that `fire.pxl` stays as it is (section 7).

## 2. Files (exclusive)

Create (pure Kotlin, no `android.`/`androidx.` import; `StageCoreIsPureTest.kt:14-30` scans `ui/pixel/stage`):
- `J/service/FireMath.kt`: `FireFeedState`, `FireMath` (constants, `levelAt`, transitions). No Android, no `BuildConfig`.
- `J/service/FireFeed.kt`: `FireFeedCore`, `object FireFeed`.
- `J/service/FireDebug.kt`: `FireDebugPin`, `object FireDebug` (in-memory pins; `apply` gated on `BuildConfig.TEST_HOOKS`).
- `J/ui/pixel/stage/FireSpec.kt`: `FireTransition`, `FireParams`, `FireSprite`, `FireSpec`, `FireTuning`, `FireResolve`.
- `J/ui/pixel/stage/FireGeometry.kt`: `FireGeometry`, `FireHit`.
- `J/ui/pixel/stage/FireLayer.kt`: `FireLayer` (glow, flame, hold ring).
- `J/ui/pixel/stage/FireEdge.kt`: `FlameShape`, `FireNoise`, edge drawing.
- `J/ui/pixel/stage/FireSparks.kt`: `SparkSet`, `PixelStage.drawSparks`.
- `J/ui/pixel/stage/FireWind.kt`: `FireWind`, `TiltWind`, `TiltApi`, `FireTiltPolicy`, `FireTiltController`.
- `J/ui/pixel/stage/FireHazeShader.kt`: `FireHazeShader` (AGSL text constant), `FireHazeCurve`.
- `J/ui/pixel/FireHost.kt` (Android, deliberately outside `stage/`; the plan puts all of it in `stage/`): `FireDriver`, `rememberFireDriver`, `FireTags`.
- `J/ui/pixel/FireTiltSensor.kt`: `SensorTiltApi`. `J/ui/pixel/FireHaze.kt`: `Modifier.fireHaze`. `J/ui/pixel/FirePrefs.kt`: `FirePrefs`.
- `J/ui/settings/FireSettings.kt`: `FireSettingsRows()`.
- `app/src/main/res/values/strings_fire.xml` (prefix `fire_`).
- Tests (section 8) under `app/src/test/java/dev/pocketagent/app/{service,data,ui/pixel/stage,ui/pixel}/`, `app/src/test/resources/fire/core-sprites.golden`.

Modify (the only tracked files I touch): `J/data/ChatEngine.kt` (the four lines of 3.1, nothing else).
Not touched, left dead: `J/ui/pixel/Fire.kt` (`Fire()`, `Embers()` have no call site, survey B).

## 3. Shared symbols I PROVIDE

### 3.1 FireFeed (`app.service`, pure)
```kotlin
data class FireFeedState(val heat: Float = 0f, val heatAtMs: Long = 0L, val tools: Set<String> = emptySet(), val turns: Set<String> = emptySet())
object FireMath {
    const val HALF_LIFE_MS = 9_000L; const val CUT = 0.01f
    const val TURN_FLOOR = 0.35f; const val TOOL_FLOOR = 0.90f; const val TEXT_CAP = 0.70f; const val TEXT_STEP = 0.03f
    const val START_KICK = 0.55f; const val TOOL_KICK = 0.90f
    fun decayed(heat: Float, ageMs: Long): Float            // heat * 2^(-age/HALF_LIFE_MS); age <= 0 returns heat; result < CUT returns 0
    fun floorOf(s: FireFeedState): Float                    // tools nonempty TOOL_FLOOR, else turns nonempty TURN_FLOOR, else 0
    fun levelAt(s: FireFeedState, nowMs: Long): Float       // max(floorOf(s), decayed(s.heat, nowMs - s.heatAtMs)), 0..1
    fun settledAtMs(s: FireFeedState): Long?                // null while floorOf(s) > 0; else heatAtMs + ceil(HALF_LIFE_MS * log2(heat / CUT)); heatAtMs when heat < CUT
    fun turnStarted(s: FireFeedState, conv: String, nowMs: Long): FireFeedState
    fun textArrived(s: FireFeedState, conv: String, nowMs: Long): FireFeedState
    fun toolStarted(s: FireFeedState, conv: String, callId: String, nowMs: Long): FireFeedState
    fun toolFinished(s: FireFeedState, conv: String, callId: String, nowMs: Long): FireFeedState
    fun turnEnded(s: FireFeedState, conv: String, nowMs: Long): FireFeedState
    fun kick(s: FireFeedState, level: Float, nowMs: Long): FireFeedState
}
class FireFeedCore(private val clock: () -> Long) {
    val state: StateFlow<FireFeedState>                     // changes only on events, never on the clock
    fun level(): Float                                      // pinned ?: FireMath.levelAt(state.value, clock())
    fun turnStarted(convId: String); fun textArrived(convId: String)
    fun toolStarted(convId: String, callId: String); fun toolFinished(convId: String, callId: String)
    fun turnEnded(convId: String); fun kick(level: Float)
    fun pin(level: Float?); fun reset()                     // pin: in memory, null releases; reset: state to FireFeedState(), pin null
}
object FireFeed { val core: FireFeedCore /* clock = System.nanoTime() / 1_000_000 */; val state; fun level(): Float; /* same methods as the core, delegating */ }
```
Weights (one table, `FireMath` constants): turn starts: heat to at least 0.55, floor 0.35 while the conversation has a turn open. Each text piece (Delta or Reasoning): heat plus 0.03, never above 0.70 through text (heat already above 0.70 is left alone); also opens a turn (idempotent). Tool running: heat to at least 0.90, floor 0.90 while any tool of any conversation runs (key `"$convId|$callId"`; a repeated running event only re-kicks). Removing a floor (tool finished, turn ended) first bakes `levelAt(old state)` into `heat`, so the fire eases down from where it was: 0.90 to 0.35 over about 10 s, and after the last turn ends down to 0 (a settled fire). `turnEnded(conv)` removes that conversation's turn and every tool key starting with `"$conv|"`. Mutations are CAS updates on a `MutableStateFlow` (thread safe). Reference values: 0.70 after 9 s is 0.35; 0.70 after 60 s is 0.0069, so below `CUT` and exactly 0 ("an idle minute").
Read by: FIRE's `FireDriver`; the chat top bar fire line and the widget (Wave 1b and later) through `FireFeed.level()` and `FireFeed.state`; PET (reaction to work); APPEAR (crackle volume).

The call sites in `data/ChatEngine.kt` (HEAD lines; the three places of the plan are the three functions or branches P1 to P3, P1 has two lines):
| Place | Where | Added line |
|---|---|---|
| P1 turn | `generate()`, directly after `_events.tryEmit(EngineEvent.TurnStarted(convId, bot.id))` (444) and in the `finally` that sets `run.handle = null` (448-450) | `FireFeed.turnStarted(convId)`; `FireFeed.turnEnded(convId)` (without the second a cancelled tool run would leave the fire at 0.90 for ever; it also runs on every error exit of the stream) |
| P2 text | `Turn.handle`, branch `ChatEvent.Delta`, right after `if (d.isEmpty()) return` (579, so thought summaries count too); branch `ChatEvent.Reasoning`, right after `if (reasoning.length >= MAX_REASONING_CHARS) return` (596) | `FireFeed.textArrived(convId)` |
| P3 tool | `Turn.handle`, branch `ChatEvent.Tool`, after the `if (existing != null) {...} else {...}` (610-616), before `if (ev.status.lowercase() != "running")` (617) | `if (ev.status.lowercase() == "running") FireFeed.toolStarted(convId, key) else FireFeed.toolFinished(convId, key)` (`key` is the existing `val key`, 607) |
No `EngineEvent.ToolStarted` exists (survey G), so the feed hooks the stream, not the event bus. Import `dev.pocketagent.app.service.FireFeed`. Nothing else in the file changes.

### 3.2 Parameters, geometry, resolution (`app.ui.pixel.stage`, pure)
```kotlin
data class FireTransition(val kind: Kind, val startMs: Long, val frozenElapsedMs: Long? = null) {
    enum class Kind { LIGHT, DOUSE }
    fun elapsed(timeMs: Long): Long          // frozenElapsedMs ?: max(0, timeMs - startMs)
    fun active(timeMs: Long): Boolean        // elapsed < (LIGHT FireTuning.LIGHT_MS, DOUSE FireTuning.DOUSE_MS)
}
data class FireParams(val classic: Boolean = true, val level: Float = 0f, val wind: Float = 0f, val holdProgress: Float = 0f, val transition: FireTransition? = null) {
    companion object { val CLASSIC: FireParams }   // = FireParams(); today's fire
}  // level 0..1 from FireFeed; wind -1..1 positive to screen right (tilt, pin); holdProgress 0..1; NaN is read as 0
enum class FireSprite { COLD, OUT, LOW, MID, HIGH }   // fire_cold, fire_out, fire_low, fire_mid, fire_high
class FireSpec { var sprite: FireSprite; var intensity: Float; var edge: Float; var level: Float; var boost: Float; var sparkBase: Int }
object FireTuning {
    const val LIGHT_MS = 900L (= Dur.EPIC); const val DOUSE_MS = 700L; const val HOLD_MS = 1000L
    const val HIGH_AT = 0.30f; const val IDLE_INTENSITY = 0.70f; const val SPARK_WIND_CELLS = 12f; const val SHEAR_ROWS = 22; const val SHEAR_MAX_PX = 3f
    const val RING_DOTS = 20; const val RING_RX = 21; const val RING_RY = 13; const val RING_CY_UP = 14   // ring centre (fx, base - 14)
}
object FireResolve {
    fun resolve(out: FireSpec, hearth: HearthState, progress: Float?, stokeBoost: Float, celebration: Boolean, fire: FireParams, timeMs: Long): FireSpec  // fills and returns out; no allocation
}
class FireGeometry(val stageWidth: Int, val stageHeight: Int) {
    val fx: Int          // (stageWidth * 0.6f).toInt(): SceneRenderer.kt:106
    val base: Int        // stageHeight - 3: SceneRenderer.kt:107
    val spriteX: Int; val spriteY: Int   // fx - 16, base - 35 (SceneRenderer.kt:154)
    fun hitRect(grid: StageGrid, minPx: Int): FireHit    // device px relative to the surface: logical (fx-12, base-29, 24 x 30) times grid.scale plus grid.offsetX/Y, grown around its centre to at least minPx in each direction
}
data class FireHit(val left: Int, val top: Int, val right: Int, val bottom: Int) { val width: Int; val height: Int }
```
Resolution table (`FireResolve`, `Leff = max(level, min(1, stokeBoost / 1.5) * 0.9)`, `boost = (stokeBoost.coerceIn(0, 1.5) + 0.5 if celebration).coerceIn(0, 1.5)`, as in SceneRenderer.kt:68, 126):
| Hearth | classic = true | classic = false |
|---|---|---|
| COLD | `fire_cold`, intensity 0, no edge | same; sparks only when boost above 0.05 or celebration (as today) |
| KINDLING (progress `pr`) | sprite out / low / mid / high at 0.15 / 0.5 / 0.85, intensity `0.18 + 0.72 pr` (as today) | same sprite and intensity; edge 0 for `pr` below 0.15, else `0.6 pr`; sparks as today |
| BLAZING | `fire_high`, intensity 1 | sprite HIGH when `Leff >= HIGH_AT` else MID; intensity `0.70 + 0.30 Leff`; edge `0.25 + 0.75 Leff`; sparkBase `round(4 + 16 Leff)` |
| SMOKE | alternating low/out at 170 ms, intensity 0.3 or 0.1 (as today) | same, edge 0 |
| LIGHT transition `e = elapsed`, e below 900 | ignored | sprite by `e / 225` = OUT, LOW, MID, HIGH; intensity `0.15 + 0.75 e/900`; edge `0.8 e/900`; boost at least `0.8 (1 - e/900)` (a burst of sparks) |
| DOUSE transition, e below 700 | ignored | sprite by `e / 175` = HIGH, MID, LOW, OUT; intensity `0.9 - 0.8 e/700`; edge `0.8 (1 - e/700)`; the smoke afterwards is SCENE's COLD smoke |
`holdProgress` does not enter `FireSpec`: the ring is drawn by `FireLayer.flame` in both modes (gesture feedback, 0 draws nothing).
Expected feel: Running with an idle agent shows `fire_mid`, glow level 5; a turn lifts the fire to `fire_high` within 5 text pieces (0.55 at once) and the sparks to about 18; a tool step makes it roar (Leff 0.9, 20 sparks, edge 0.93); eleven seconds after the last piece it eases back to `fire_mid`, a minute later the sparks are at their idle 4.

### 3.3 `FireLayer` (the interface to SCENE's renderer)
```kotlin
class FireLayer(sprites: SpriteSource) {      // asks for fire_cold, fire_out, fire_low, fire_mid, fire_high; owns EmberSet(40, 21L), SparkSet(32, 31L), the glow cache, FlameShape and plain-pixel caches
    fun glow(stage: PixelStage, state: SceneState, geo: FireGeometry, timeMs: Long, quality: SceneQuality)                       // replaces SceneRenderer.kt:105-139 (back of Pêt)
    fun flame(stage: PixelStage, state: SceneState, geo: FireGeometry, timeMs: Long, quality: SceneQuality, weatherWind: Float = 0f)   // replaces line 154 (after Pêt): sprite, edge, wind lean, hold ring
    fun sparks(stage: PixelStage, state: SceneState, geo: FireGeometry, timeMs: Long, quality: SceneQuality, weatherWind: Float = 0f)  // replaces lines 164-177 (after SCENE's smoke, before the fireflies)
}
```
It reads from `SceneState` only the existing names `hearth, palette, progress, stokeBoost, celebration` and one new field `fire: FireParams` (section 7, SCENE). `weatherWind` is `WeatherLook.wind` (-1..1, positive blows right; WEATHER 3.5), passed by SCENE as `state.weather.wind`; in classic mode it is ignored. Combined wind `FireWind.total(weatherWind, fire.wind) = (a + b).coerceIn(-1, 1)`.
Behaviour contract:
- **Classic** (`fire.classic`): the code of SceneRenderer.kt 105-139, 154, 164-177 moved verbatim (same constants, same `EmberSet(40, 21L)`, same hash flicker only when `quality.particles`); output equals today's byte for byte. No edge, no sparks, no wind, no transition.
- **Glow** (not classic): the same level maths with `intensity` from `FireResolve`; with `particles` the hash flicker stays and a breath is added: `s = sin(t * 2 PI / 3400)`, `+1` level above 0.6, `-1` below -0.6.
- **Flame** (not classic): blit of the chosen sprite at `(geo.spriteX, geo.spriteY)` with `sprite.frameAt(t)`; when `FireWind.shift(row, wind)` is non-zero for some row the rows 0 to 22 are copied pixel by pixel with that shift (all other rows, and everything at wind 0, go through the plain `stage.blit`, so at wind 0 the pixels equal the classic ones). Edge only when `quality.particles` and the sprite is LOW, MID or HIGH. Never plots over a pixel where the sprite is opaque (tests prove it).
- **Living edge** (`FireEdge.kt`, seed constants 11, 23, 29 in `FireTuning`): `FlameShape.of(sprite, frame)` scans rows 0 to 22 for flame letters `g r f m y c` and returns `top[32]` (first row of a column whose flame pixel has a flame pixel in the next one or two rows, which skips the stray hand-keyed spark pixels; -1 if none), `left[36]`, `right[36]` (first and last flame column per row, -1 if none), cached per (sprite, frame), at most 22 entries. Tongues: `maxLen = 1 + round(4 * edge)`; for each column `x` with `top[x] >= 0`: `n = FireNoise.value(x shr 1, t, 260, 11)`, `prof = clamp(1 - |x - 15.5| / 9, 0.15, 1)`, `len = floor(n * prof * (maxLen + 0.999))`; for `k` in 1..len a pixel at row `top[x] - k` (plus the lean shift of that row): `k = 1` colour `F7691A`, middle `DC3818`, the last (when `len >= 2`) `A81F1A` at alpha 0.75 and only where `SceneArt.bayer(x, y) < 0.7`. Side licks for rows `top..22`: `n = FireNoise.value(40 + row, t, 200, 23 or 29)`; when `n > 0.68 - 0.25 * edge` one pixel at `left[row] - 1` (`A81F1A`, alpha 0.8) and, with the other seed, at `right[row] + 1` (`DC3818`, alpha 0.8). The four colours are the palette lines `g r f m` of `fire.pxl` as constants (a test compares them with the file). `FireNoise.hash01(a: Int, b: Int, seed: Int): Float` = integer hash (`h = a*374761393 + b*668265263 + seed*1274126177; h = (h xor (h ushr 13)) * 1274126177; h = h xor (h ushr 16); (h and 0xFFFF) / 65535f`); `FireNoise.value(x: Int, tMs: Long, periodMs: Int, seed: Int): Float` = smoothstep blend of `hash01(x, k, seed)` and `hash01(x, k + 1, seed)` with `k = tMs / periodMs`.
- **Wind lean** `FireWind.shift(row: Int, wind: Float): Int` = 0 outside rows 0..22, else `sign(wind) * round(abs(wind) * 3 * (22 - row) / 22)` (never `roundToInt` of a negative number: it rounds -1.5 to -1). Wind 1: row 0 shifts 3, row 11 shifts 2, row 22 shifts 0; `abs(wind) < 0.1` shifts nothing anywhere.
- **Hold ring** (both modes, after the sprite): 20 dots at angle `-90 deg + i * 18 deg` on an ellipse of radius 21 by 13 around `(fx, base - 14)`; dot `i` is lit (`palette.fireHigh`, alpha 0.9) when `i < ceil(20 * holdProgress)`, else dim (`palette.fireLow`, alpha 0.25); nothing when `holdProgress <= 0`.
- **Sparks** (not classic, `quality.particles`; classic keeps the embers): `SparkSet(capacity 32, seed 31)` arrays `spawnX -1..1, periodMs 1500..3800, phase, swayAmp 0.8..2.6, swayFreq 0.9..2.1, windGain 0.5..1.3, big (18 %)`. `PixelStage.drawSparks(set, count, t, originX, originY, halfWidthCells, riseCells, wind, high, mid, low, strength)`: per spark `u = ((t / period) + phase) % 1`, skipped when `((t / 80 + i * 7) % 11) == 0`, `rise = floor(riseCells * (1 - (1 - u)^2))`, `dx = floor(spawnX * halfWidth + sway + FireWind.sparkDrift(wind, u, windGain))`, `FireWind.sparkDrift(w, u, g) = w * 12 * g * u`, alpha `strength`, faded in over `u < 0.06` and out over `u > 0.7`, colour high / mid / low at `u` 0.3 / 0.65, size 2 while `big && u < 0.35`. Count `min(32, sparkBase + round(boost * 14) + 14 if celebration)`; origin `(fx, base - 12 - floor(14 * intensity))`, `halfWidth = 4 + 3 * intensity + 3 * boost`, `riseCells = 16 + 22 * intensity + 12 * boost`, `strength = min(1, 0.55 + 0.45 * intensity + 0.25 * boost)`. Positions are a pure function of `t` and `wind`, so a wind change moves a spark by at most `12 * gain` cells over its life: the driver smooths the wind (3.4).

### 3.4 Android host (`app.ui.pixel`)
```kotlin
object FireTags { const val HIT = "fire.hit" /* HOME puts it on the fire's hit box */ ; const val LOG = "Fire" }
class FireDriver {
    fun params(sceneMs: Long): FireParams                // call ONLY inside the draw lambda: reads FireFeed.level(), tilt wind, prefs, pins, the transition, hold(); allocates one FireParams
    val hazeActive: Boolean                              // hysteresis of FireHazeCurve: on at level 0.5, off at 0.35; false when classic, not FULL, SDK below 33 or the pin says off
}
@Composable fun rememberFireDriver(hearth: HearthState, clock: LongState, hold: () -> Float = { 0f }, onTransition: (FireTransition.Kind) -> Unit = {}): FireDriver
@Composable fun Modifier.fireHaze(driver: FireDriver, sceneBottomAnchored: Boolean = true): Modifier   // no-op below API 33; renderEffect null while !hazeActive
class FirePrefs { val classic: StateFlow<Boolean>; val tilt: StateFlow<Boolean>; fun setClassic(b: Boolean); fun setTilt(b: Boolean); companion fun get(context: Context): FirePrefs }
@Composable fun FireSettingsRows()                       // in app.ui.settings: PocketSwitchRow "Classic fire", PocketSwitchRow "Tilt the phone to blow the fire"
```
- `rememberFireDriver` remembers the previous `hearth`; COLD to non-COLD sets `FireTransition(LIGHT, clock.longValue)` and calls `onTransition(LIGHT)` once; BLAZING or KINDLING to COLD sets DOUSE and calls `onTransition(DOUSE)`; the first composition and COLD to SMOKE set nothing (HOME's entrance owns the first minute). Transitions apply only when not classic.
- Wind passed on is `0.85 * previous + 0.15 * target` once per frame of the scene clock (a low pass of about 180 ms at 30 fps), quantised to 0.05; the pin is not smoothed.
- Tilt (E1): `FireTiltPolicy.shouldRun(homeOnTop, lifecycleStarted, fullAnimation, classic, tiltEnabled, hasSensor)` is the AND of its six arguments; `homeOnTop = LocalNavigator.current.current == Route.Home`, `fullAnimation = Pocket.anim == AnimLevel.FULL`. The driver registers through `SensorTiltApi` (`TYPE_ACCELEROMETER`, `SENSOR_DELAY_UI`, no permission) when the policy turns true and unregisters when it turns false, when the lifecycle stops and in `onDispose` (a scrolled-away scene leaves the lazy list and the composition). `TiltWind.fromAccelX(ax: Float): Float` = `-sign(ax) * clamp((abs(ax) - 1.0) / 3.5, 0, 1)` (m/s squared; the flame leans to the raised edge like a real flame; flip `TiltWind.SIGN` to change); `TiltWind.smooth(prev: Float, sample: Float, dtMs: Long, tauMs: Long = 250): Float` with `alpha = dt / (tau + dt)`. `interface TiltApi { fun register(onAccelX: (Float, Long) -> Unit): Boolean; fun unregister() }`; `class FireTiltController(api: TiltApi)` with `fun setRunning(run: Boolean)`, `val registered: Boolean`, `@Volatile var wind: Float`, and a log line `AppLog.i("Fire", "tilt on")` / `"tilt off"` on each change (the driver does the logging, the pure controller returns the change).
- Haze (E2): AGSL in `FireHazeShader.SOURCE`, uniforms `shader content; float2 fire /* device px of the flame centre, layer coordinates */; float scale /* device px per scene pixel */; float time /* seconds */; float strength`. It snaps `p` to scene pixels (`q = floor(p / scale) * scale`), inside the box 28 scene pixels wide and 35 high above the flame top (x within 14, y from `base - 55` to `base - 20`) samples `content.eval(q + float2(w * scale, 0) + scale * 0.5)` with `w = floor(sin(row * 0.9 + time * 5.0) + 0.5)` times `strength` (so whole scene pixels, -1 to +1), outside it `content.eval(p)`. `FireHazeCurve.strength(level: Float): Float` = smoothstep(0.35, 0.9, level). Implemented with `android.graphics.RuntimeShader` + `RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()` in `Modifier.graphicsLayer { renderEffect = ... }` (the grid comes from `StageGrid.forScene(size.width, size.height)` inside the layer lambda); the effect object is rebuilt per frame only while active.

### 3.5 Debug pin (`app.service.FireDebug`, in memory, `BuildConfig.TEST_HOOKS` only, cleared by `reset=1`)
```kotlin
data class FireDebugPin(val level: Float? = null, val wind: Float? = null, val classic: Boolean? = null, val hold: Float? = null, val transition: FireTransition? = null, val start: FireTransition.Kind? = null, val haze: Boolean? = null) {
    companion object { fun parse(text: String): FireDebugPin? }   // null = bad value
}
object FireDebug { @Volatile var pin: FireDebugPin; fun apply(p: FireDebugPin?, enabled: Boolean = BuildConfig.TEST_HOOKS); fun clear() }
```
Grammar of the `fire` link extra: `off` (clear), or items joined by commas: `level:0..1` (also `FireFeed.pin`), `wind:-1..1`, `classic`, `living`, `hold:0..1`, `light@0..1` and `douse@0..1` (a transition frozen at that fraction of its duration: `frozenElapsedMs = fraction * duration`), `light` and `douse` (start now, consumed once by the driver), `haze:on|off`. A bad item rejects the whole value. Pins are not stored; `living` and `classic` override `FirePrefs` without writing it.

### 3.6 Scripts
No script. The taste sheets come from the JVM test of 8 (`FireTasteSheetDump`): `$env:FIRE_SHEET_DIR="<abs dir>"; powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 -Test` writes `fire-compare-dark.png`, `fire-compare-light.png`, `fire-states-dark.png`, `fire-states-light.png` into that folder (nothing is written and the test is skipped when the variable is empty). The PNG writer uses `java.awt.image.BufferedImage` and `ImageIO` in test code (`PixelStage.toBitmap` is Android and not usable on the JVM; design language 6.1 lists it under `stage` by mistake).

## 4. Shared symbols I CONSUME

| From | Form |
|---|---|
| Wave 0 | `PixelStage` (`plot`, `fillRect`, `blit(IntArray..)`, `blit(Sprite, frame, x, y, alpha)`), `Argb.withAlpha/over`, `SceneArt.bayer/glow/GLOW_LEVELS`, `GlowImage`, `EmberSet`, `drawEmbers` (Embers.kt, unchanged), `SpriteSource`, `Sprite.frameAt`, `SpriteRaster.plain`, `ScenePalette`, `SceneQuality`, `StageGrid` (`scale`, `offsetX`, `offsetY`); `HearthState`; `rememberSceneClock`; `Pocket.anim`, `AnimLevel`, `LocalNavigator`, `Route.Home`; `AppLog.i`; `BuildConfig.TEST_HOOKS`; `PocketSwitchRow`, `t()`; `Dur.EPIC` (only in a test) |
| SCENE | `SceneState` keeps `hearth, palette, progress, mood, stokeBoost, celebration` and gains `val fire: FireParams = FireParams.CLASSIC` at the END; the renderer owns one `FireLayer` and calls it as in section 7; `state.weather.wind` as `weatherWind` |
| WEATHER | `WeatherLook.wind` through SCENE (no type of mine imports it); the `fire` link extra in `DebugLinks.kt` (section 7) |
| HOME | the hit box overlay, the hold gesture and the tap action; passes `hold: () -> Float` |
| ART-WORLD | `fire.pxl` sprites `fire_cold fire_out fire_low fire_mid fire_high`, 32 by 36, palette letters `o s S h w W a A l L g r f m y c`, frame counts 1, 4, 6, 8, 8 and times 1000, 260, 130, 110, 95 ms, exactly as at HEAD |

## 5. Strings: `res/values/strings_fire.xml`, prefix `fire_` (English only)

| Key | English | Note |
|---|---|---|
| `fire_classic_title` | Classic fire | `<!-- t: switch title in Appearance, sky group; up to 18 chars \| kind: row -->` |
| `fire_classic_sub` | Turns off the living flame edge, the wind in the sparks and the fire that settles when the agent is idle. The fire looks as it did in earlier versions. | subtitle under the switch; up to 3 lines; \| row |
| `fire_tilt_title` | Tilt the phone to blow the fire | switch title; up to 30 chars; \| row |
| `fire_tilt_sub` | The flame and the sparks lean when you tilt the phone. The motion sensor is on only while Home is open. | subtitle; up to 3 lines; the sentence about the sensor must stay \| row |
No other text: the fire switch's spoken labels belong to HOME (`home_`), the scene sentence to SCENE.

## 6. Stored data

| Item | Where | Default | Why the previous version ignores it |
|---|---|---|---|
| `classic` (Boolean), `tilt` (Boolean) | new SharedPreferences file `fire` | false, true | the old code never opens that file; nothing else changes. Not in `BackupCore.PREF_SCHEMA` (nobody edits `BackupManager`): a restore returns to the defaults, harmless |
| FireFeed state, wind, pins, transitions | memory only | empty | nothing is stored |
No file, no change to chats, bots or any existing key. No bulk write. Safe on owner-debug.

## 7. REQUESTS to other areas

- SCENE: `J/ui/pixel/stage/SceneModel.kt`: add `val fire: FireParams = FireParams.CLASSIC` as the LAST field of `SceneState` (existing positional calls in `SceneRendererParityTest.kt:55` stay valid).
- SCENE: `J/ui/pixel/stage/SceneRenderer.kt`: remove the five `fire_*` lookups (28-32), the `embers` field (34), the glow cache fields and `glow()` (50-51, 212-221); add `private val fireLayer = FireLayer(sprites)` and `val geo = FireGeometry(wl, hl)` after `g`/`artX`; replace 105-139 by `fireLayer.glow(stage, state, geo, t, quality)`; keep the pet blit with `geo.fx - 43`, `geo.base - 31`; replace line 154 by `fireLayer.flame(stage, state, geo, t, quality, weatherWind)`; keep the smoke block (157-163) but with `geo.fx` and `geo.base - 8`; replace 164-177 by `fireLayer.sparks(stage, state, geo, t, quality, weatherWind)`; the fireflies stay after it. Draw order stays glow, Pêt, flame, smoke, sparks, fireflies, grass. `REQUIRED_SPRITES` unchanged. `weatherWind = state.weather.wind` (0f while there is no weather field).
- SCENE (`J/ui/pixel/HearthScene.kt`): add parameters `fireHold: () -> Float = { 0f }` and `onFireTransition: (FireTransition.Kind) -> Unit = {}`; `val fire = rememberFireDriver(state, clock, fireHold, onFireTransition)` after the clock (77); `.fireHaze(fire)` in the `Spacer` chain before `drawBehind`; build the scene with `SceneState(..., fire = fire.params(t))` at 102. Keep the stamp as it is (`Triple(scene, t, quality)` already contains the params).
- HOME (`ui/home/HomeScreen.kt`): replace the whole-scene `clickable` (167-195) by an overlay on the hit box from `FireGeometry(stageW, stageH).hitRect(grid, 48.dp in px)` tagged `FireTags.HIT`, `Role.Button`; tap on a cold fire lights it (`vm.start()`), tap on a burning one stokes (existing `stoke` code), hold of `FireTuning.HOLD_MS` puts it out (`vm.stop()`) while an `Animatable` drives `fireHold`; a labelled menu entry does the same (rule 6); sounds WAKE and SLEEP and the haptics go in `onFireTransition`. The 7-tap overdrive may stay on the rest of the scene. `ui_home_mascot_cd` says "salamander" (survey): fix the text with the other Home strings.
- WEATHER (`J/DebugLinks.kt`, `DebugLinksTest.kt`): add `EXTRA_FIRE = "fire"` to the constants (62-73) and the read list (126); in `parseParts` (133-215) `FireDebugPin.parse(text("fire"))`, a null result goes to `rejected`; append `val fire: Setting<FireDebugPin> = Setting.Keep` to `DebugRequest` (after your two fields); `apply` runs it after `weather` and `place`: `Setting.Set` calls `FireDebug.apply(pin)`, and `reset=1` calls `FireDebug.clear()`. Tests: `off`, `level:0.6,wind:-0.5`, `light@0.5`, `bad`.
- APPEAR (`ui/settings/AppearanceSection.kt`): call `FireSettingsRows()` in the sky group (after the weather rows). `voice/SoundEngine.kt`: the optional crackle loop volume follows `FireFeed.level()` (0 at settle); WAKE and SLEEP are played by HOME from `onFireTransition`.
- PET: the reaction to work reads `FireFeed.state` (turns non-empty) and `FireFeed.level()`; Pêt's position comes from `FireGeometry.fx` and `base`.
- ART-WORLD: do not edit the five `fire_*` sprites nor `build_fire_pxl` in `tools/art/craft_art.py` (CI runs `craft_art.py --check`); a woodpile or any prop must not cover the hit box of 3.2 (24 by 30 logical pixels around the stones).
- INTEGRATE: run the tests once with `FIRE_GOLDEN_WRITE=1` to create `app/src/test/resources/fire/core-sprites.golden`, then commit it; run `FireTasteSheetDump` with `FIRE_SHEET_DIR` and copy the four PNGs to `dist/w1a/fire/`.
- BUILD/manifest: nothing. The accelerometer needs no permission and no `uses-feature`.

## 8. Tests (JUnit 4, JVM; time, wind, level and noise are parameters)

| Class (package) | Tests and what they prove |
|---|---|
| `FireMathTest` (`service`) | `decayHalvesEveryNineSeconds` (0.70 at 9 s is 0.35); `decayCutsBelowOnePercent` (0.70 at 60 s is 0.0); `levelAtTakesLargerOfFloorAndHeat`; `turnStartedKicksToAtLeast055AndOpensFloor`; `textNeverRaisesAboveCap` (1,000 pieces give 0.70; heat 0.9 stays 0.9); `toolStartedKicksToAtLeast09AndFloors`; `toolFinishedBakesFloorThenEases` (0.90 stays 0.90 at the call, 0.45 nine seconds later, never below 0.35 while the turn is open); `turnEndedClearsOnlyThatConversation` (two conversations, tool keys by prefix); `idleMinuteSettles` (stream, end, +60 s gives exactly 0); `highSpriteWindowAfterStreamEnds` (0.70 baked: 0.324 at 10 s, 0.278 at 12 s); `settledAtMsMatchesLevel` (null while floored; level is 0 at that instant and above 0 one second before); `levelStaysBetween0And1`; `kickRaisesNeverLowers` |
| `FireFeedCoreTest` (`service`) | `stateEmitsOnEventsOnly` (no emission while time passes); `pinOverridesAndReleases`; `resetClearsEverything`; `fourThreadsTenThousandCallsEndConsistent` (every turn ended: both sets empty) |
| `ChatEngineFireFeedTest` (`data`) | with the fake backend of `ChatEngineTest` (copied helpers, that file is not edited) and `FireFeed.core.reset()` in `@Before`: `completedTurnLeavesNoFloor` (sets empty after, heat above 0); `streamingOpensTurnAndRaisesHeat` (observed from a backend callback); `toolRunningThenCompletedMovesFloor`; `cancelledMidToolReleasesTheFloor` (the Fake ends with `ChatOutcome.Cancelled` after a running tool: tools set empty); `failedStreamReleases` (TransportError); `groupChatEndsEachBotsTurn` |
| `FireResolveTest` (`stage`) | `classicMapsLikeToday` (all four hearth states, progress 0.1, 0.4, 0.9, equal to the old `when`); `blazingIdleIsMidAndWorkingIsHigh` (0.29 MID, 0.30 HIGH); `stokeLiftsLevel` (stoke 1.2 on idle gives HIGH); `kindlingEdgeGrowsWithProgress`; `smokeAndColdHaveNoEdge`; `lightStepsSpritesAtQuarters` (0, 225, 450, 675 ms: OUT LOW MID HIGH); `douseStepsAtQuarters`; `transitionsIgnoredInClassic`; `frozenTransitionUsesFrozenElapsed`; `nanLevelIsZero`; `resolveDoesNotAllocate` (same `FireSpec` instance returned) |
| `FireClassicParityTest` (`stage`) | with `LegacyFireReference` (test file, the fire statements of `LegacyHearthReference.kt` 273-345 copied verbatim and drawn alone on a blank stage): `glowFlameSparksMatchOldCodeAcrossCases` for the 14 cases and 3 sizes of `SceneRendererParityTest` (it does not depend on SCENE keeping that oracle) |
| `FireEdgeTest` (`stage`) | `flameShapeTopsOnSyntheticSprite` (an inline 8 by 8 pxl through `PxlParser`); `strayPixelsAreSkipped`; `neverOverdrawsOpaqueSpritePixels` (all five sprites, all frames, edge 1, wind 0 and 0.8: every pixel opaque in the sprite equals the plain blit, or is the shifted copy of it when wind is not 0); `windZeroFlameEqualsClassicBlit`; `edgeZeroDrawsNothingExtra`; `tongueLengthNeverAboveMax`; `sameInputsSamePixels`; `timeChangesTongues` (t 0 and 520); `noiseInRangeAndSeeded` (`hash01` in 0..1 for 10,000 inputs, deterministic, different seeds differ); `sideLicksStayOutsideFlame`; `coreColoursMatchFirePxl` (constants equal palette lines g r f m of the file; `Assume` file exists) |
| `FireWindTest` (`stage`) | `shiftTable` (wind 1: rows 0, 11, 22 give 3, 2, 0; wind -1 gives -3, -2, 0; wind 0.05 gives 0 everywhere; row 23 and -1 give 0); `totalClamps`; `sparkDriftIsLinearInAge` (equals `WeatherWind.driftCells(wind, u, 12f)` for gain 1 when WEATHER's file is present, else its formula `w * 12 * u`); `tiltDeadzoneAndSaturation` (ax 0 and 1.0 give 0; 2.75 gives -0.5; 4.5 and 9 give -1; -4.5 gives 1); `lowPassHalfAfterOneTau` (dt 250 ms, tau 250: alpha 0.5) |
| `FireSparksTest` (`stage`) | `countClampsToCapacity`; `positionsArePureInTimeAndWind`; `windMovesSparksByDriftFormula` (the same spark at wind 0 and 1, `u` near 0.8: x differs by `floor` of 9.6 ± 1); `zeroStrengthDrawsNothing`; `coloursCoolWithAge`; `noSparkOutsideRiseBox` |
| `FireLayerTest` (`stage`) | `holdRingLitCountFollowsProgress` (0.3 gives 6 lit, 1.0 gives 20, 0 draws nothing, both modes); `plainQualityDrawsNoEdgeNoSparks`; `coldDrawsColdSprite`; `levelRaisesGlow` (pixel sum of the glow area at level 1 above level 0); `glowBreathChangesLevelBySigns`; `layerOrderGlowUnderFlame` |
| `FireGeometryTest` (`stage`) | `matchesTodaysConstants` (`FireGeometry(176, 160)`: `fx` 105, `base` 157, `spriteX` 89, `spriteY` 122; `FireGeometry(155, 160).fx` 93); `hitRectScalesWithGrid` (scale 7, offsets 3 and 5: left `3 + (fx - 12) * 7`); `hitRectGrowsToMinTouch` (scale 1, min 126: width and height at least 126, centre unchanged) |
| `FireTiltTest` (`stage`) | `policyIsTheAndOfSix` (64 combinations); `registersOnceWhenRunning` and `unregistersWhenPolicyFalse` with a counting fake `TiltApi`; `neverRegistersWhenNotAllowed`; `setRunningTwiceRegistersOnce`; `windUpdatesFromSamples`; `unregisterOnDispose` |
| `FireHazeTest` (`stage`) | `agslHasUniforms` (the source contains `uniform shader content`, `float2 fire`, `float scale`, `float time`, `float strength` and no `#`); `curveIsSmoothstep` (0.35 gives 0, 0.9 gives 1); `hysteresisOnAt05OffAt035` |
| `FireDebugTest` (`service`) | `parseGrammar` (all items, `off`, bad values, `light@1.5` rejected); `applyIgnoredWithoutTestHooks`; `clearReleasesFeedPin` |
| `FireCoreSpritesPinnedTest` (`stage`) | the five sprites' size, palette letters, frame count, frame times and a SHA-256 of all frame rows equal `core-sprites.golden` (`Assume` file exists; `FIRE_GOLDEN_WRITE=1` rewrites it) |
| `FireLayerCostTest` (`stage`) | `noAllocationPerFrame` (after warm-up, 300 frames of the roar case allocate under 64 KB through `com.sun.management.ThreadMXBean`, `Assume` it exists); `frameCostLogged` (2,000 frames, average printed, asserts under 2 ms) |
| `FireTasteSheetDump` (`stage`) | dumps the four PNGs of 3.6 from `SceneRenderer` with `FireParams` rows: classic idle, living idle (level 0), working (0.6), roar (1.0), roar wind +0.8, roar wind -0.8, each at t 0, 150, 300, 450, 600, 750 ms, crop 80 by 64 logical pixels around the fire at 4 times, left label; states sheet: cold, kindling 0.1 / 0.4 / 0.9, smoke, `light@0.25/0.5/0.75`, `douse@0.25/0.5/0.75`, hold 0.3 / 0.6 / 1.0 |
Reference values come from the formulas above (no external source; nothing astronomical).

## 9. Proof on the phone (owner-debug, safe scenarios: they read, pin, photograph)

Link values (`--es fire ...`, with `--es clock 2027-03-21T20:30 --es reset 1` first so the sky is the same in every picture; themes Newroz dark and Nergiz light through Appearance):
| Name | Value | Picture or check |
|---|---|---|
| `fire-classic` | `classic` | today's fire; must equal the build-32 picture of `dist/` by eye |
| `fire-idle` | `living,level:0` | settled `fire_mid`, small glow, 4 sparks |
| `fire-working` | `living,level:0.6` | `fire_high`, edge 0.7 |
| `fire-roar` | `living,level:1` | roar |
| `fire-wind-right` / `-left` | `living,level:1,wind:0.8` / `wind:-0.8` | lean, sparks blow |
| `fire-light-half` / `fire-douse-half` | `living,light@0.5` / `living,douse@0.5` | transition frame |
| `fire-hold` | `living,hold:0.6` | 12 of 20 dots lit |
| `fire-haze-on/off` | `living,level:1,haze:on` / `haze:off` | same picture with and without; gfxinfo pair |
Scenarios: (1) the pictures above for both themes, plus `stage.hearth` tag present; (2) `fire-feed-live`: practice model on through the interface (owner-debug rule), send a prompt from Home, poll `app.log` for `I/Fire: look ... level=` lines (the driver writes `I/Fire: look sprite=<S> classic=<b> level=<0.00> edge=<0.00>` at most every 500 ms and only when the sprite or the level in steps of 0.1 changed; TEST_HOOKS only) and photograph when level is at least 0.55; after the answer, `level=0.00` within 70 s; (3) `fire-tilt-registration`: with Home open `adb shell dumpsys sensorservice` lists the package with the accelerometer and `app.log` has `I/Fire: tilt on`; switch to Chats: `tilt off` within 1 s and the package is gone from the dump; Home then Back/Home key: gone; Appearance classic on: gone; animation level Reduced through the Appearance setting: gone; after ten minutes on Home and then leaving it, the dump has no connection. A real tilt (the leaning itself) stays a manual check by the owner; the pictures use the `wind` pin. (4) `fire-hit`: the node tagged `fire.hit` has size at least 48 dp both ways. (5) smoothness: `phone-perf.ps1 -Mode Smooth` on Home idle for classic, living, living with haze.
Not provable without the twin or a stopped Hermes: COLD, KINDLING, SMOKE pictures (the JVM sheet shows them); the start of a real wake (`fire-light`) with `light`/`douse` links stands in.

## 10. Cost

| What | Per frame | Per minute | How it stays inside the budget |
|---|---|---|---|
| FireFeed | 0 (a read of one `StateFlow` value and a power of two, in the draw lambda) | a few CAS updates per streamed piece, no emission on the clock | state changes only on events; no coroutine, no timer |
| Classic path | what the scene spends today (moved, not added) | | the 14 cases of the parity test pin it |
| Edge | about 100 to 150 `plot` calls (32 columns of at most 5 pixels, 40 rows of licks), one array read per column for the shape, no allocation | `FlameShape` computed once per (sprite, frame): 22 scans of 32 by 23 pixels, ever | shapes and plain pixels cached in the layer, arrays preallocated; `FireLayerCostTest` |
| Wind lean | 1,152 pixel copies only while some row shifts; otherwise the normal blit | | idle phone: wind 0, no copies |
| Sparks | at most 32 trig calls and plots (the classic embers are at most 40) | | same cost class as today's embers |
| Hold ring | 20 plots while the finger is down, 0 otherwise | | |
| Tilt sensor (E1) | a callback every about 60 ms: two float operations, no state write (a `@Volatile` float read at draw) | none while off | registered only while the policy of 3.4 is true; proven by 9 (3) |
| Haze (E2) | one offscreen layer for the scene (about 0.8 megapixels) and one AGSL pass on the GPU; the effect is rebuilt per frame while active | none when off | `renderEffect = null` unless level 0.5 or more (off at 0.35), FULL, SDK 33+, not classic; acceptance: median and 90th percentile frame time with haze on at most 1 ms above haze off in `phone-perf`; otherwise cut E2 |
Share of the budget (design language 11, scene under 1 ms on the optimised build): FIRE at most 0.15 ms of it. The scene cost today (about 10 ms a frame, debug, survey) is the whole-scene redraw at 30 fps through `StageSurface` (the stamp includes `t`, so every tick renders); FIRE adds no second pass and changes nothing about that. REDUCED: no edge, no sparks, no haze, no tilt, sprite and glow mapping still follow the level; OFF: one still frame at the level of the first composition.

## 11. Risks, and what in the plan or design language is wrong or impossible

1. Plan 1.4 says "three places in `ChatEngine.kt`"; three are not enough: without `turnEnded` in `generate()`'s `finally` (448-450) a cancelled turn with a running tool keeps the fire at 0.90 for ever (`ChatOutcome.Cancelled` emits nothing, ChatEngine.kt:705). Three places (P1 with two lines, P2 with two branches, P3) are the minimum.
2. Plan 899 gives FIRE `ui/pixel/stage/Fire*.kt` for the tilt sensor and shader, but `StageCoreIsPureTest.kt:14-30` forbids `android.` and `androidx.` imports under `stage/`: the Android parts live in `ui/pixel/Fire*.kt` and `ui/settings/FireSettings.kt` (a deviation).
3. Design language 6.1 shows `SceneState(intensity, wind, ...)` and `object SceneRenderer`; the real `SceneState` (SceneModel.kt:46-53) has no intensity or wind and `SceneRenderer` is a class with caches (survey B). My `FireParams` rides in one new field instead; 6.1 also lists `toBitmap` under `PixelStage`, but it is an Android extension (`PixelStageBitmap.kt:12`).
4. `SceneRendererParityTest` fails on any change of the picture unless the oracle is retired (survey K). The CLASSIC path keeps it green as long as SCENE changes nothing else; my own `FireClassicParityTest` does not depend on it.
5. The settling look changes today's resting picture on purpose: a Running fire with an idle agent moves from `fire_high` (intensity 1) to `fire_mid` (0.70). This is the taste gate; `FireTuning.HIGH_AT` and `IDLE_INTENSITY` are the two numbers to move, and "Classic fire" gives the old picture back.
6. The 7-tap overdrive, the stoke tap and the whole-scene `clickable` (HomeScreen.kt:167-195) collide with "tap the fire to light or stoke, hold to put out": HOME must split the gestures between the fire's hit box and the rest of the scene (a missing request would leave two overlapping click targets).
7. Pêt overlaps the left edge of the fire's hit box by one logical pixel (Pêt spans `fx - 43` to `fx - 11`, the box starts at `fx - 12`); HOME's tap on Pêt must not be eaten.
8. A sprite switch MID/HIGH at level 0.30 has no hysteresis (a pure function of the level keeps the render testable); text pieces climb through it in one step and decay crosses it once, so flicker is only possible when a floor and a decaying heat meet at exactly 0.30, which the constants (0.35, 0.70, 0.90) avoid.
9. Wind in a spark's path is applied to its whole life: a sudden change of wind moves every live spark at once (at most 12 cells). The driver's low pass hides it; with the debug pin (unsmoothed) a jump is visible by design.
10. `HearthScene.kt` is in no area's row of the ownership table; I assume SCENE edits it (section 7). If two areas edit it, the integration agent must merge the parameter list and the `SceneState(...)` line.
11. Heat haze needs the whole scene on an offscreen layer; on a phone that is slow at that it costs more than the flame is worth, hence E2 is the first cut and has its own acceptance number.
12. The accelerometer is the only sensor used; Android 12 and later limit its rate above 200 Hz without a permission, and `SENSOR_DELAY_UI` (about 16 Hz) is far below, so no permission and no manifest line are needed. A phone without one simply never registers (`hasSensor` false).
13. `FireFeed` is per process: a routine that runs while the process is alive but the app is hidden feeds it, and nothing draws; a widget or a notification in a dead process sees level 0.
14. I could not compile or run anything. The numbers in 3.3 (sizes, colours, counts) were chosen against the sprite rows of `fire.pxl` (flame in columns 10 to 23, tips at rows 10 to 16 with a few stray hand-keyed spark pixels above them in rows 3 to 9, stones from row 23); the first phone pictures may need `FireTuning` and the `FireEdge` constants adjusted, never the sprites.
