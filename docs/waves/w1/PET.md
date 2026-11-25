# Wave 1a, area PET: contract draft

Package `dev.pocketagent.app` is written `app`. Paths are under `app/src/main/java/dev/pocketagent/app/` (`J/`) unless they start with `app/` or `tools/`. Line numbers were read from the files at HEAD `fe0e0ea`. BINDING items taken from `docs/design-language.md` section 7 and 3: Pêt is a small character with a brain that picks something every 4 to 9 seconds from weighted options that depend on the moment; one reaction at a time, queued, never interrupting itself; 13 new animations (`pet_blink`, `pet_tail`, `pet_warm`, `pet_yawn`, `pet_stretch`, `pet_firefly`, `pet_poke`, `pet_read`, `pet_write`, `pet_carry`, `pet_startled`, `pet_dance`, `pet_listen`) plus the weather's two (`pet_shelter`, `pet_snow`), 32 x 32, 4 to 8 frames; a shadow that lengthens away from the fire; Pêt notes (head and bubble, rises from the bottom, can be swiped away); a 16 x 16 Pêt for top bars (1b places it). Plan 1.5: shelter under a leaf when it rains, sleep when the night is deep.

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| P1 | Types: `PetAnim` (22 states), `PetPose`, `PetWorld`, `PetEvent`, `PetArt` | core |
| P2 | `PetBrain`: pure state machine, seeded RNG, time as a parameter: base pose from the world, event reactions, transition reactions (yawn then sleep, stretch on waking, startle on smoke), idle scheduler, queue, fallback for missing sprites, backwards-time resync | core |
| P3 | Shadow gauge (hysteresis) inside the brain | core |
| P4 | `PetLayer` (draws shadow and pose into the stage) and `PetPlacement` (feet point, hit rectangle) | core |
| P5 | `PetController` + `rememberPetController()`: engine events, busy and tool state, flags from Home, tap, TEST_HOOKS log | core |
| P6 | `PetTaps` (the 7-tap chain moves here) and `PetQuips` (contextual quips by hour, weather, pose) | core |
| P7 | `PetDebug` pin (`petpose` link extra) | core (the proof needs it) |
| P8 | Art contract for `mascot_life.pxl` and `PetArtTest` | core |
| P9 | `PetNote.kt`: rise from the bottom and swipe away | core, small |
| P10 | `PetPeek` composable (the small Pêt; 1b places it) | extra |
| P11 | Idle options `FIREFLY`, `POKE`, `TAIL`, `WARM`; animation `pet_listen` (no consumer before Wave 8) | extra |

Cut order (first cut first): P10, `pet_listen`, P9 swipe, `pet_snow` with the `SNOW` idle option, `FIREFLY`, `POKE`, `TAIL`, `WARM`, the contextual quips (the four classic quips stay). Never cut: P1 to P6, P7, `pet_shelter`, `pet_read`, `pet_write`, `pet_startled`, `pet_yawn`, `pet_stretch`, `pet_carry`, `pet_dance`. A cut animation is simply absent from the art file: the fallback of 3.1 covers it.

## 2. Files (I own them exclusively)

Create (pure Kotlin, no `android.` or `androidx.` import):
- `J/ui/pixel/pet/PetAnim.kt`: `PetAnim`, `PetArt`.
- `J/ui/pixel/pet/PetWorld.kt`: `PetFire`, `PetWorld`, `PetPose`, `PetEvent`, adapters from `HearthState` and `WeatherLook`.
- `J/ui/pixel/pet/PetBrain.kt`: `PetBrain`, shadow gauge, idle weights.
- `J/ui/pixel/pet/PetLayer.kt`: `PetLayer`, `PetPlacement`.
- `J/ui/pixel/pet/PetTaps.kt`: `PetTaps`, `PetQuip`, `PetQuips`, `PetTapResult`, `PetTapAction`.
- `J/ui/pixel/pet/PetDebug.kt`: pure `parse`, gated `pin` (imports `BuildConfig` only).

Create (Android):
- `J/ui/pixel/pet/PetHost.kt`: `PetController`, `rememberPetController`, `PetQuip.res()`.
- `J/ui/pixel/pet/PetPeek.kt` (extra): `PetPeek`.
- `app/src/main/res/values/strings_pet.xml` (prefix `pet_`).

Modify: `J/ui/components/PetNote.kt` (only `PetNoteHost` and one new `internal object PetNoteSwipe`; `PetNoteQueue`, `PetNotes`, `PetNote`, tags and every public signature stay).

Tests (JUnit 4, JVM), all under `app/src/test/java/dev/pocketagent/app/ui/`: `pixel/pet/{PetBrainTest,PetBrainIdleTest,PetLayerTest,PetPlacementTest,PetTapsTest,PetQuipsTest,PetDebugTest,PetArtTest,PetCoreIsPureTest}.kt`, `components/PetNoteSwipeTest.kt`. `PetNoteQueueTest` stays unchanged.

Not mine: `Mascot.kt`, `MascotMood` and `spriteName()` stay untouched (About, empty states, Hermes screen, notes, gallery keep today's seven poses); `ui_home_quip_*` keys stay where they are (HOME and LANG own `strings_chat.xml`).

## 3. Shared symbols I PROVIDE

### 3.1 Types (`app.ui.pixel.pet`, pure)

```kotlin
enum class PetAnim(val sprite: String, val frames: Int, val durationMs: Int, val loops: Boolean, val fallback: PetAnim?) {
    IDLE("pet_idle", 8, 2860, true, null),        THINK("pet_thinking", 7, 1650, true, IDLE),   TALK("pet_talking", 8, 880, true, IDLE),
    HAPPY("pet_happy", 9, 1300, false, IDLE),     SLEEP("pet_sleeping", 6, 3500, true, IDLE),   ERROR("pet_error", 6, 1380, true, IDLE),
    WAVE("pet_waving", 8, 1220, false, IDLE),
    BLINK("pet_blink", 4, 280, false, IDLE),      TAIL("pet_tail", 6, 660, false, IDLE),        WARM("pet_warm", 8, 1200, false, IDLE),
    YAWN("pet_yawn", 7, 1180, false, IDLE),       STRETCH("pet_stretch", 8, 1040, false, IDLE), FIREFLY("pet_firefly", 8, 960, false, IDLE),
    POKE("pet_poke", 8, 1040, false, IDLE),       READ("pet_read", 6, 1320, true, THINK),       WRITE("pet_write", 8, 1440, false, IDLE),
    CARRY("pet_carry", 4, 1040, true, IDLE),      STARTLED("pet_startled", 5, 490, false, IDLE), DANCE("pet_dance", 8, 1120, true, HAPPY),
    LISTEN("pet_listen", 4, 1200, true, THINK),   SHELTER("pet_shelter", 6, 1440, true, IDLE),  SNOW("pet_snow", 8, 1200, false, IDLE);
    val isClassic: Boolean   // the first seven: IDLE to WAVE, the sprites of mascot.pxl that always exist
}
object PetArt {
    val CLASSIC: Set<PetAnim>                                      // IDLE THINK TALK HAPPY SLEEP ERROR WAVE
    val NOT_SHIPPED: Set<PetAnim>                                  // animations the art review rejected; empty today; PetArtTest accepts exactly the file minus this set
    const val SHADOW = "pet_shadow"; const val MINI = "pet_mini"   // see 3.6
    fun availableIn(find: (String) -> Sprite?): Set<PetAnim>       // CLASSIC plus every new anim whose sprite exists; never throws
}
data class PetPose(val anim: PetAnim, val startMs: Long, val shadow: Int = 1, val holdFrame: Int = -1)
// startMs: scene time at which the animation began (frame = sprite.frameAt(t - startMs)); shadow: -1 none, 0 short, 1 medium, 2 long; holdFrame >= 0 draws that frame only (OFF level, debug pin)
enum class PetFire { COLD, KINDLING, BLAZING, SMOKE }
data class PetWorld(
    val fire: PetFire = PetFire.BLAZING, val fireLevel: Float = 1f,           // fireLevel 0..1
    val sunAltitudeDeg: Float = -30f, val localMinutes: Int = 21 * 60,         // of the DRAWN sky; minute of day in the phone's zone
    val rain: Int = 0, val snow: Int = 0,                                      // 0 none, 1 light, 2 heavy
    val busy: Boolean = false, val toolRunning: Boolean = false, val waitingResult: Boolean = false,
    val celebrating: Boolean = false, val listening: Boolean = false,
    val animate: Boolean = true,                                               // false = Pocket.anim OFF: one still pose, no reactions
    val lively: Boolean = true,                                                // true only at FULL: random idle actions allowed
) {
    val deepNight: Boolean   // sunAltitudeDeg <= -12f && (localMinutes >= 1380 || localMinutes < 300)   (23:00 to 05:00)
    val night: Boolean       // sunAltitudeDeg < -6f
    val day: Boolean         // sunAltitudeDeg > 6f
    val morning: Boolean     // localMinutes in 300 until 600
    companion object { fun defaultFireLevel(fire: PetFire, progress: Float?): Float }   // COLD 0, KINDLING 0.18 + 0.72 * progress (0.5 when null), BLAZING 1, SMOKE 0.2: the renderer's own numbers (SceneRenderer.kt:111-123)
}
sealed interface PetEvent {
    data object MemorySaved : PetEvent; data object Failed : PetEvent; data object Forged : PetEvent; data object Approval : PetEvent
    data class Tap(val count: Int) : PetEvent; data object Overdrive : PetEvent
}
fun petFireOf(h: HearthState): PetFire                // 1:1 by name
fun petRainOf(look: WeatherLook): Int                 // look.rainLevel (0, 1, 2)
fun petSnowOf(look: WeatherLook): Int                 // SNOW_LIGHT 1, SNOW_HEAVY 2, else 0 (sleet counts as rain)
```
Fallback rule: an animation whose sprite is missing, rejected (`NOT_SHIPPED`) or broken resolves to `fallback`, repeatedly, ending at `IDLE`; a pose is never an unavailable anim, the brain resolves before it emits. A reaction whose fallback chain ends at `IDLE` while the anim was a decoration (BLINK, TAIL, WARM, YAWN, STRETCH, FIREFLY, POKE, WRITE, STARTLED, SNOW) is skipped entirely (no invisible pause); `DANCE` falls back to `HAPPY`, `READ` and `LISTEN` to `THINK`, so today's pose appears there. No exception is ever thrown for a missing new sprite.

### 3.2 The brain (`PetBrain.kt`, pure; one thread calls `step`, any thread calls `post`)

```kotlin
class PetBrain(seed: Long, available: Set<PetAnim> = PetArt.CLASSIC) {
    fun post(event: PetEvent)                         // thread-safe; at most 8 wait, oldest dropped; events are timed at the next step
    fun setAvailable(set: Set<PetAnim>)               // CLASSIC is always added; takes effect at the next step
    fun step(nowMs: Long, world: PetWorld): PetPose   // the same instance is returned while nothing changes; calling twice with equal nowMs is harmless
    companion object { const val IDLE_CYCLE_MS = 2860; const val GAP_CYCLES_SHORT = 2; const val GAP_CYCLES_LONG = 3; const val MAX_QUEUE = 3
                       const val OVERDRIVE_MS = 4200; const val AWAKE_GRACE_MS = 20_000
                       internal fun idleWeights(world: PetWorld, last: PetAnim?, available: Set<PetAnim>): Map<PetAnim, Int> }
}
```
`nowMs` is the scene clock (`rememberSceneClock`), not wall time; it restarts at 0 when a new scene starts: when `nowMs` is lower than the last one the brain forgets reaction, queue and schedule and starts clean at `nowMs` (events already posted stay).

Base pose from the world, first match wins:
1. `fire` COLD gives SLEEP, SMOKE gives ERROR, KINDLING gives THINK; 2. `celebrating` DANCE; 3. `deepNight && !busy && !listening` and no awake grace: SLEEP; 4. `listening` LISTEN; 5. `busy`: READ when `toolRunning`, else THINK; 6. `waitingResult` CARRY; 7. `rain > 0` SHELTER; 8. IDLE (snow does not change the base: the `SNOW` idle option plays).
`animate == false`: the pose is the base with `startMs = 0`, `holdFrame = 0`; no reactions, no idle.

Reactions (one at a time). Classes: IDLE (spontaneous), EVENT, URGENT. An URGENT reaction replaces any current one that is not URGENT (the replaced one is dropped, not queued); an EVENT reaction never interrupts, it waits in a queue of at most `MAX_QUEUE` (a duplicate anim is merged, the oldest is dropped when full); IDLE reactions start only with an empty queue and no reaction. When a reaction ends the next queued one starts at once, else the base returns with `startMs = end` (so `IDLE` restarts at its frame 0).

| Trigger | Reaction | Class | Length |
|---|---|---|---|
| `PetEvent.Failed` | STARTLED | URGENT | 490 |
| `PetEvent.MemorySaved` | WRITE | EVENT | 1440 |
| `PetEvent.Forged` | HAPPY | EVENT | 1300 |
| `PetEvent.Approval` | WAVE | EVENT | 1220 |
| `PetEvent.Tap(n)` | base SLEEP caused by `deepNight`: grace of `AWAKE_GRACE_MS` (base becomes IDLE, the transition below plays STRETCH); base SLEEP otherwise (fire out): STRETCH; base CARRY: none (the tap opens the result); else `n` even WAVE, `n` odd HAPPY (today's rule, HomeScreen.kt:188) | URGENT | anim length |
| `PetEvent.Overdrive` | DANCE held | URGENT | `OVERDRIVE_MS` |
| base becomes SLEEP (from any other) | YAWN, then SLEEP | EVENT | 1180 |
| base leaves SLEEP | STRETCH | EVENT | 1040 |
| base becomes ERROR (smoke) | STARTLED | URGENT | 490 |
| first step, or any other base change | none (the new base begins at once) | | |

Idle scheduler (only when the base is IDLE, `lively`, nothing playing): the base IDLE has a start (`idleStart`: the first step, or the end of the last reaction) and the next action is due `k` whole idle cycles later, `k = GAP_CYCLES_SHORT` with probability 2/3 (`rng.nextInt(3) < 2`) else `GAP_CYCLES_LONG`: 5.72 s or 8.58 s, which stays inside the design language's 4 to 9 s (section 7) and starts every action on the rest pose of `pet_idle` (3.6), so the sprite never pops mid-breath (average gap 6.7 s). The action is a weighted draw from `idleWeights` (integer weights, multipliers compound in this order, result truncated):

| Anim | Weight | Multipliers |
|---|---|---|
| BLINK | 28 | |
| TAIL | 16 | |
| WARM | 12 | `fireLevel < 0.5` x0.5 |
| YAWN | 6 | `night` x2, `day` x0.5, `morning` x2 |
| STRETCH | 5 | `morning` x3 |
| FIREFLY | 8 | `night` x2, `day` 0, `snow > 0` 0 |
| POKE | 7 | `fireLevel < 0.5` x2, `snow > 0` 0 |
| HAPPY | 6 | today's hop, kept |
| SNOW | 0 | 40 when `snow > 0` |

A weight is 0 when the anim is unavailable or equals the previous action (never twice in a row). WARM plays 1 or 2 times (`rng.nextInt(2)`). A reaction posted while IDLE-class is playing waits (EVENT) or replaces it (URGENT).

Shadow gauge: frame 0 below 0.20, 1 from 0.20, 2 from 0.65 of `fireLevel`; going down it needs 0.10 and 0.55 (hysteresis); at most one change per 500 ms; a frame is never -1 in the scene.

### 3.3 Drawing (`PetLayer.kt`, pure)

```kotlin
object PetPlacement {
    fun feetX(stageWidth: Int): Int     // (stageWidth * 0.6f).toInt() - 27   (SceneRenderer.kt:106 fire x, minus 27: the sprite's left edge stays fx - 43)
    fun feetY(stageHeight: Int): Int    // stageHeight - 3                     (SceneRenderer.kt:107)
    /** Device-pixel rectangle [left, top, right, bottom] of Pêt's touch area: the 32 x 32 box grown by 3 stage pixels left, top and bottom (not right: the fire wins the overlap), and at least minPx wide and high (grown left, top and bottom). */
    fun hitRect(grid: StageGrid, minPx: Float = 0f): FloatArray
    fun hits(xPx: Float, yPx: Float, grid: StageGrid, minPx: Float = 0f): Boolean
}
class PetLayer(sprites: SpriteSource) {        // looks up the seven classic sprites (throws IllegalArgumentException if one is missing, exactly as SceneRenderer does today) and tries the others (missing ones are only absent from `available`)
    val available: Set<PetAnim>
    fun draw(stage: PixelStage, pose: PetPose, feetX: Int, feetY: Int, timeMs: Long)
}
```
`draw`: resolves `pose.anim` through the fallback chain; frame = `holdFrame` (clamped) when >= 0, else `sprite.frameAt(timeMs - startMs)` for loops and the same clamped to the last frame once `timeMs - startMs >= sprite.totalMs` for once-anims; the shadow first (`pet_shadow` frame `pose.shadow` at `(feetX - 16, feetY - 3)`, alpha 0.45, skipped when the sprite is absent or `shadow < 0`), then the pet at `(feetX - 16, feetY - 31)`, alpha 1. Everything clips at the stage edge (`PixelStage.blit` clips). With `pose = PetPose(IDLE, 0, -1)` the pixels equal today's `stage.blit(pet, pet.frameAt(t), fx - 43, fbase - 31)` exactly (test `matchesLegacyBlit`).

### 3.4 Taps and quips (`PetTaps.kt`, pure)

```kotlin
class PetTaps(private val chainMs: Long = 1200L) {            // HomeScreen.kt:174-190 moved here
    class Count(val count: Int, val streak: Int, val overdrive: Boolean)
    fun tap(nowMs: Long): Count     // streak = if (now - last <= chainMs) streak + 1 else 1; count++; the 7th in a chain gives overdrive = true and resets the streak
}
enum class PetQuip { CLASSIC_1, CLASSIC_2, CLASSIC_3, CLASSIC_4, OVERDRIVE, SLEEPY, NIGHT, MORNING, RAIN, SNOW, READING, WRITING, OOPS }
object PetQuips { fun pick(world: PetWorld, anim: PetAnim, count: Int, overdrive: Boolean): PetQuip }
enum class PetTapAction { NONE, OPEN_WAITING_RESULT }
class PetTapResult(val quip: PetQuip, val overdrive: Boolean, val action: PetTapAction)
```
`pick` order: overdrive gives OVERDRIVE; by pose SLEEP gives SLEEPY, WRITE WRITING, READ READING, SHELTER RAIN, SNOW SNOW, STARTLED or ERROR OOPS; then for odd `count` only: `morning` gives MORNING, `deepNight` gives NIGHT; else `CLASSIC_n` with `n = (count - 1) % 4 + 1`. `PetQuip.res()` (in `PetHost.kt`): CLASSIC_1..4 are `R.string.ui_home_quip_1..4`, OVERDRIVE is `R.string.ui_home_quip_overdrive` (existing keys), the rest are 5.

### 3.5 Controller and debug (Android, `PetHost.kt`; `PetDebug.kt`)

```kotlin
class PetController internal constructor(seed: Long = System.nanoTime()) {
    fun poseAt(timeMs: Long, scene: PetWorld): PetPose                    // main thread (draw lambda); ORs the engine and Home flags into `scene`, applies the debug pin, logs changes
    fun tap(nowMs: Long = System.currentTimeMillis()): PetTapResult       // counts (PetTaps), posts Tap or Overdrive, picks the quip from the last pose; action OPEN_WAITING_RESULT when the last pose was CARRY
    fun flags(waitingResult: Boolean, celebrating: Boolean, listening: Boolean = false)   // Home calls it from a SideEffect
    fun post(event: PetEvent)
}
@Composable fun rememberPetController(): PetController
```
`rememberPetController` creates the controller once, and inside `repeatOnLifecycle(STARTED)` collects `PocketApp.from(ctx).engine.events` (`MemorySaved` gives `PetEvent.MemorySaved`, `BotForged` gives `Forged`, `ApprovalRequested` gives `Approval`, `TurnFailed` gives `Failed`; `TurnStarted`, `TurnCompleted`, `ToolFinished` are ignored: the base follows `busy` and `toolRunning`), `engine.busy` (busy = non-empty) and `FireFeed.toolsRunning` (running = value > 0); after `rememberSpritesReady()` is true it calls `brain.setAvailable(PetArt.availableIn(SpriteBank::find))`. Events are only collected while a Pêt scene is on screen (the engine bus has no replay, survey G); the base state is correct on return anyway because it comes from the flows. With `BuildConfig.TEST_HOOKS` every change of `pose.anim` writes `AppLog.i("Pet", "anim=<NAME> base=<NAME>")`.

```kotlin
class PetPin(val anim: PetAnim?, val frame: Int?, val classicOnly: Boolean)
object PetDebug {                                   // PetDebug.kt
    fun parse(text: String): PetPin?                // "off" is the caller's; "<anim>" or "<anim>:<frame>" (lower-case enum name, frame 0 until frames); "classic" (availability = CLASSIC only: proves the fallback on the phone); null when bad
    val pinned: StateFlow<PetPin?>; fun pin(p: PetPin?)   // no-op unless BuildConfig.TEST_HOOKS; memory only
}
```
A pinned anim returns `PetPose(anim, startMs = timeMs - timeMs % anim.durationMs, shadow = 1, holdFrame = frame ?: -1)` whatever the world says; `classic` makes the controller call `setAvailable(CLASSIC)`, so the brain emits only classic poses (the layer still loads the others).

### 3.6 Art contract (for ART-PET; sprite names, sizes, timing)

File: **`app/src/main/assets/pixel/mascot_life.pxl`** (new; deviation from the ownership table, which says `mascot.pxl`; reason: `SpriteLoader.load` skips a bad file as a whole, so one broken block in `mascot.pxl` would take the seven classic sprites and the whole scene with it; `mascot.pxl` sorts first, so duplicate names would keep the old ones). The file starts with the 22 palette lines of `mascot.pxl` copied exactly (chars `.` `k` `K` `b` `B` `c` `r` `R` `o` `O` `q` `y` `Y` `d` `e` `w` `a` `m` `t` `s` `S` `z` `f` `g` `h`, light from the right) and may append at most these props: `n #3C7A38` and `N #78B454` (leaf), `i #E6F2FF` (flake), `p #E8D8B0` and `P #B89A5C` (parchment, notebook), `v #7FA8E0` (rain, optional); colours may be adjusted, the chars may not. No `1` to `4` chars (tint ramp).

Common to every 32 x 32 sprite: **anchor = the feet**: column 16 of the bottom row 31 is `(feetX, feetY)`, so Pêt's body stands where `pet_idle` stands and no animation shifts it; nothing opaque in column 0, column 31 or row 0 (no clipping); light from the right; the newt faces the fire (right). `rest` means: frame 0 and the last frame are pixel-identical to `pet_idle` frame 0 (`PetArtTest.restPose`), so an action starts and ends on the pose the brain returns to.

| Sprite | Frames | ms per frame | Total ms | Avg fps | Mode | rest | What it shows |
|---|---|---|---|---|---|---|---|
| `pet_blink` | 4 | 80, 60, 60, 80 | 280 | 14.3 | once | yes | slow double blink |
| `pet_tail` | 6 | 110 each | 660 | 9.1 | once | yes | tail sways once |
| `pet_warm` | 8 | 150 each | 1200 | 6.7 | once | yes | hands to the fire, rubs |
| `pet_yawn` | 7 | 140, 140, 160, 300, 160, 140, 140 | 1180 | 5.9 | once | yes | big yawn |
| `pet_stretch` | 8 | 130 each | 1040 | 7.7 | once | yes | arms and back stretch |
| `pet_firefly` | 8 | 120 each | 960 | 8.3 | once | yes | follows a firefly with the head, tongue flick (the firefly is drawn by the scene) |
| `pet_poke` | 8 | 130 each | 1040 | 7.7 | once | yes | pokes the fire with a twig |
| `pet_read` | 6 | 220 each | 1320 | 4.5 | loop | no | reads a scroll |
| `pet_write` | 8 | 180 each | 1440 | 5.6 | once | yes | writes in a notebook (`p`, `P`) |
| `pet_carry` | 4 | 260 each | 1040 | 3.8 | loop | no | holds a scroll with a badge, gentle bob |
| `pet_startled` | 5 | 70, 70, 90, 110, 150 | 490 | 10.2 | once | no | jumps, tail up |
| `pet_dance` | 8 | 140 each | 1120 | 7.1 | loop | no | hops side to side (first and last frame equal for a seamless loop) |
| `pet_listen` | 4 | 300 each | 1200 | 3.3 | loop | no | head tilted, listening |
| `pet_shelter` | 6 | 240 each | 1440 | 4.2 | loop | no | under a big leaf (`n`, `N`) that drips |
| `pet_snow` | 8 | 150 each | 1200 | 6.7 | once | yes | a flake falls, Pêt catches it |

Also in the file: **`pet_shadow`** 32 x 6, 3 frames of 1000 ms (code picks the frame, it does not animate): frame 0 a 14-pixel ellipse centred, frame 1 a 20-pixel ellipse whose centre is 2 pixels left, frame 2 a 28-pixel ellipse whose centre is 4 pixels left (away from the fire); only char `e`; anchor: column 16, between rows 2 and 3 (drawn at `feetY - 3`). **`pet_mini`** 16 x 16, 6 frames, ms 120, 1400, 400, 900, 400, 100 (3320 loop): half-risen, up, look left, up, look right, blink; anchor column 8, bottom row 15; no props. These two are extras of the art (cut with P10 and the shadow's frame 2 only if the reviewer rejects them; without `pet_shadow` the layer draws no shadow). `tools/art/contact_sheet.py` (`--file app/src/main/assets/pixel/mascot_life.pxl --frames all --scale 6`) is the review picture; a sprite rejected on it is deleted from the file and added to `PetArt.NOT_SHIPPED` (PET edits that line on the lead's word).

## 4. Shared symbols I CONSUME

| From | Form |
|---|---|
| Wave 0 | `PixelStage.blit(sprite: Sprite, frame: Int, x: Int, y: Int, alpha: Float = 1f)`; `Sprite.frameAt(timeMs: Long): Int`, `Sprite.totalMs`, `Sprite.frames`; `SpriteSource` (`ui.pixel.stage`); `SpriteBank.find(name): Sprite?`; `rememberSpritesReady()`; `StageGrid(scale, width, height, offsetX, offsetY)`; `HearthState`; `PocketApp.from(ctx).engine.events: SharedFlow<EngineEvent>` and `engine.busy: StateFlow<Set<String>>`; `EngineEvent.{MemorySaved, BotForged, ApprovalRequested, TurnFailed}` (package `app.data`); `app.util.AppLog.i`; `BuildConfig.TEST_HOOKS`; `Pocket.motion.pop()`, `tap()`; `Space.m`; `PixelPanel`, `PetNotes.dismissVisible()`; `t(R.string.design_note_dismiss)` |
| SCENE | calls `PetLayer` and `PetPlacement`, adds `SceneState.pet`, builds `PetWorld` (7) |
| WEATHER | `WeatherLook.rainLevel: Int` and `WeatherLook.precip: Precip` (`SNOW_LIGHT`, `SNOW_HEAVY`) in `app.ui.pixel.stage`; the `DebugLinks` extra of 7 |
| FIRE | `app.service.FireFeed.toolsRunning: StateFlow<Int>` (tool calls running now, summed over chats, never negative); nothing else of FireFeed |
| HOME | calls `rememberPetController()`, `tap()`, `flags()`, `PetPlacement.hits` (7) |
| APPEAR | `Appearance.petInChatsVisible` is read by whoever places `PetPeek` in 1b, not by PET in 1a (`PetPeek` takes `enabled`) |

### 3.7 PetPeek (extra; `PetPeek.kt`)

```kotlin
@Composable fun PetPeek(working: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true)
```
Draws `pet_mini` (one whole multiple of 16 px, at most 32 dp) while `working && enabled && Pocket.anim != AnimLevel.OFF`, sliding up from below its clip edge (`Space.m` and `motion.fast()`), sliding down and gone when not; draws nothing and never throws when the sprite is missing; decorative (`clearAndSetSemantics {}`). Nobody places it in 1a (SHELL or STREAM in 1b).

### 3.8 PetNote changes (P9, `PetNote.kt`)

```kotlin
internal object PetNoteSwipe {
    const val DISMISS_FRACTION = 0.35f; const val FLING_PX_PER_S = 1500f
    fun shouldDismiss(offsetPx: Float, widthPx: Float, velocityPxPerS: Float): Boolean   // |offset| >= 0.35 * width, or |velocity| >= 1500 in the direction of the offset with |offset| >= 0.05 * width
    fun alpha(offsetPx: Float, widthPx: Float): Float                                     // 1 - min(1, |offset| / (0.6 * width)), never below 0
}
```
In `PetNoteHost`: (a) the panel gets `translationY = (1f - k) * Space.m.toPx()` next to the existing scale and alpha, so the note rises while it pops in (design language 7, "rises from the bottom"); (b) `Modifier.draggable(Orientation.Horizontal)` writes `translationX`, multiplies alpha by `PetNoteSwipe.alpha`, and on stop `PetNoteSwipe.shouldDismiss(...)` calls `PetNotes.dismissVisible()`, else the offset springs back (`motion.tap()`); the offset resets when the note id changes. Unchanged: kind-to-mood map, 32 dp `Mascot`, tap to dismiss with its label, live region, tags `petnote` and `petnote.action`, `displayMillis` (2.5 to 5 s; design language says "two seconds", the Wave 0 minimum of 2.5 s is kept for reading time).

## 5. Strings (`res/values/strings_pet.xml`, prefix `pet_`, English only; every key carries `<!-- t: ... | kind: pet -->`)

| Key | English | Note |
|---|---|---|
| `pet_quip_sleepy` | Mmm... five more minutes. | bubble above Pêt when the sleeping Pêt is tapped; up to 44 chars; Pêt is the mascot's name, never translate it |
| `pet_quip_night` | Late shift? I will keep the fire company. | bubble when tapped awake in the deep night; up to 44 chars |
| `pet_quip_morning` | Good morning! The mountain is waking up. | bubble in the early morning; up to 44 chars |
| `pet_quip_rain` | Rain on my leaf. The fire stays dry. | bubble while Pêt shelters from rain; up to 44 chars |
| `pet_quip_snow` | A snowflake! I almost caught it. | bubble while it snows; up to 44 chars |
| `pet_quip_reading` | Shh. I am reading what the tool found. | bubble while a tool runs and Pêt reads; up to 44 chars |
| `pet_quip_writing` | Noted! It is in my notebook now. | bubble just after the agent saved a memory; up to 44 chars |
| `pet_quip_oops` | Whoa! That did not go well. | bubble after an error; up to 44 chars |

No other text: Pêt notes carry text from the call sites, `PetPeek` is decorative, the swipe has no label (the tap with `design_note_dismiss` is the labelled control).

## 6. Stored data

None. The brain, the tap chain and the pin are in memory; a new scene starts clean. The sprites are an APK asset the old app never reads. The `Appearance.petInChats` field is APPEAR's. Nothing is written to disk, nothing for the previous version to ignore.

## 7. REQUESTS to other areas

- SCENE: `ui/pixel/stage/SceneModel.kt:46` `SceneState` gains `val pet: PetPose? = null` (a defaulted parameter, after the others). `SceneRenderer.kt`: in the class body `private val petLayer = PetLayer(sprites)`; replace lines 141 to 153 (mood, hop, `stage.blit(pet, ...)`) by `val pose = state.pet; if (pose != null) petLayer.draw(stage, pose, PetPlacement.feetX(wl), PetPlacement.feetY(hl), t) else { the old lines }`; keep the order pet, then fire sprite (line 154); keep `REQUIRED_SPRITES` unchanged (the new sprites are optional). `HearthScene.kt:66`: new last parameter `pet: PetController? = null`; in the `drawBehind` lambda (line 99) build `val world = PetWorld(fire = petFireOf(state), fireLevel = FIRE's level if it exists else PetWorld.defaultFireLevel(petFireOf(state), progress), sunAltitudeDeg = altitude of the drawn sky in degrees (the real sun for "follows the day"; -30f for "always night"; 45f for "always day"), localMinutes = hour * 60 + minute from `AppClock.now()` in the phone's zone, rain = petRainOf(look), snow = petSnowOf(look), animate = anim != AnimLevel.OFF, lively = anim == AnimLevel.FULL)` and pass `pet = pet?.poseAt(t, world)` into `SceneState`. The legacy `mood` and `celebration` parameters stay (null `pet` keeps today's picture and `SceneRendererParityTest` green; any non-null pose changes the picture on purpose: shadow and new poses).
- HOME: `HomeScreen.kt`: `val pet = rememberPetController()` and pass it to `HearthScene(... pet = pet)`; `SideEffect { pet.flags(waitingResult = <an unread routine result exists>, celebrating = celebration) }`. Replace the tap block (lines 167 to 195 now): the scene's tap handler (`detectTapGestures`) tests the fire's own area first (the switch), then `PetPlacement.hits(x, y, StageGrid.forScene(widthPx, heightPx)!!, minPx = 48.dp.toPx())`; a Pêt tap calls `val r = pet.tap()`, plays haptic `STOKE` (or `HOLD` when `r.overdrive`) and sound `SPARK` (or `FORGE`), shows `t(r.quip.res())` in the bubble (2400 ms, as now), sets `celebration = r.overdrive` for 4200 ms, and when `r.action == OPEN_WAITING_RESULT` opens the waiting conversation; remove `tapMood`, `taps`, `streak`, `lastTapMs` and pass `mood = null`. Tag the Pêt area `home.pet`. `res/values/strings_chat.xml:47` `ui_home_mascot_cd` says "salamander": Pêt is a newt (whoever owns that file). Keep `ui_home_quip_1..4` and `ui_home_quip_overdrive` (PET uses them).
- FIRE: `service/FireFeed.kt`: `val toolsRunning: StateFlow<Int>` as in section 4, incremented where `ChatEngine.Turn` creates a TOOL message with status "running" (ChatEngine.kt:613) and decremented when that call gets a status other than "running" and when the turn finishes (`finishLocked`, line 690).
- WEATHER: `DebugLinks.kt`: extra `petpose` (`EXTRA_PETPOSE = "petpose"`; add it to the name list at line 126): `off` gives `PetDebug.pin(null)`, otherwise `PetDebug.parse(value)`; a bad value goes to `rejected`; `reset=1` calls `PetDebug.pin(null)`; `DebugRequest` gets `val petpose: Setting<PetPin> = Setting.Keep` at the END; apply order: after `place`. Test line in `DebugLinksTest`: `petposeExtra`, `petposeBadRejected`, `resetClearsPetpose`.
- ROBOT-T / lead: `ScenarioFilesTest` known tags: add `home.pet`; new scenario `tools/scenarios/pet-poses.json` (section 9). `AGENTS.md` and the About text may mention the new behaviour only after the review.
- ART-PET: section 3.6 in full (file name, palette, 15 + 2 sprites, rest rule, contact sheet). ART-WORLD: nothing (the leaf, the flake and the notebook are inside Pêt's sprites; the firefly Pêt follows is the scene's own).
- LANG / TRANSLATE: Turkish and French for the eight `pet_quip_*` keys.

## 8. Tests (JUnit 4, JVM; time, randomness and the world are parameters; files read with `Assume.assumeTrue(file.exists())`)

| Class | Tests (name: what it proves) |
|---|---|
| `PetBrainTest` | `sameSeedSameScript` (two brains, same seed, same events and steps of 33 ms over 20 simulated minutes: equal pose logs); `differentSeedDiffers`; `baseTable` (every row of the base list of 3.2, and precedence pairs: COLD beats busy, celebrating beats deepNight, busy beats rain, rain beats IDLE, snow keeps IDLE); `deepNightTable` (-30 at 23:00 true, at 04:59 true, at 05:00 false, at 12:00 false; -11.9 at 23:00 false; -12 at 23:00 true; +30 at 03:00 false), thresholds are my rule, the sun heights come from SCENE's tests; `failedStartlesAtOnce` (STARTLED in the same step, back to base after 490 ms with `startMs` = that time); `urgentReplacesIdleButNotUrgent`; `eventWaitsForReaction` (Memory during Approval waits, starts at its end); `queueCapThree` and `duplicateMerged`; `yawnThenSleepWhenFireGoesOut`; `stretchOnWaking` (both causes); `startleWhenSmokeBegins`; `firstStepNoTransition`; `tapParity` (odd HAPPY, even WAVE); `tapOnSleepingFireOutStretches`; `tapAtDeepNightGivesGraceThen20s` (awake for `AWAKE_GRACE_MS`, asleep after); `tapOnCarryDoesNothing`; `overdriveDancesFor4200`; `timeGoingBackwardsResyncs`; `repeatedStepSameInstance`; `notAnimateIsStill` (holdFrame 0, no reactions, no idle); `notLivelyNoIdleActions` (events still react); `fallbackNeverEmitsUnavailable` (available = CLASSIC, 1 hour of random events and worlds: every pose anim is classic; DANCE gives HAPPY, READ gives THINK; skipped decorations leave no pause); `setAvailableMidwayNeverEmitsMissing`; `shadowHysteresis` (level jittering 0.6 +- 0.04 never changes the frame; a ramp 0 to 1 gives 0, 1, 2; at most one change per 500 ms) |
| `PetBrainIdleTest` | `weightsTable` (exact numbers: default world BLINK 28, TAIL 16, WARM 12, YAWN 12, STRETCH 5, FIREFLY 16, POKE 7, HAPPY 6; day: FIREFLY 0, YAWN 3; morning at sun 0, 06:30: STRETCH 15, YAWN 12; night and morning at 06:00: YAWN 24, STRETCH 15; `fireLevel` 0.3: WARM 6, POKE 14; `snow` 1: SNOW 40, FIREFLY 0, POKE 0; last BLINK: BLINK 0; unavailable: 0); `gapIsTwoOrThreeCycles` (over 1 hour every gap between the end of a reaction and the next idle start is 5720 or 8580 ms, and the share of 8580 is within 0.28 to 0.38); `startsOnCycleBoundary`; `neverSameTwice`; `distributionFollowsWeights` (20 000 draws from a fixed seed within 3 percent of the weights); `noIdleOutsideBaseIdle` (SHELTER, THINK, CARRY, SLEEP show none for 10 minutes); `warmPlaysOneOrTwo` |
| `PetLayerTest` | `matchesLegacyBlit` (fake sprites and, with `Assume`, `TestSprites.all()`: pose `IDLE`, startMs 0, shadow -1 gives the exact pixels of the old blit for t in 0, 1234, 7700, for all seven classic anims); `shadowUnderPet` (shadow pixels drawn first, pet pixels win); `holdFrame`; `onceClampsToLastFrame`; `missingNewSpriteFallsBack` (a source that throws for new names: `available == CLASSIC`, drawing a pose of each new anim does not throw and equals the fallback's pixels); `missingClassicThrows`; `neverOutsideStage` (feet at every corner and a 8 x 44 stage: no exception, array length unchanged) |
| `PetPlacementTest` | `formulaMatchesRenderer` (feetX and feetY for widths 120, 155, 160, 176 and heights 60, 120 equal `fx - 27` and `fbase` computed as `SceneRenderer.kt:106-107`); `hitRect` (inside, 3 stage pixels outside left, top and bottom, not right; min size grows left, top and bottom only); `hitsAtScales` (scale 5 to 9, offsets) |
| `PetTapsTest` | `sevenInChain` (7 taps with gaps <= 1200 give overdrive on the 7th and streak 0); `gapBreaksChain` (1201 ms); `countKeepsCounting` across overdrives (today's parity behaviour); `firstTapNeverChains` |
| `PetQuipsTest` | `orderTable` (each rule of 3.4, overdrive first); `classicRotation` ((count - 1) % 4); `oddOnlyContextual`; `everyQuipHasResource` (`PetQuip.res()` maps all 13 and the eight new ids exist: uses `R`) |
| `PetDebugTest` | `parseForms` (`read`, `read:3`, `classic`, `off` is not mine, `blink:9` rejected, `PHONE` rejected, frame outside `0 until frames` rejected); `pinGatedByTestHooks` |
| `PetArtTest` (Assume the file) | `sizesAndTimes` (every sprite present in `mascot_life.pxl` and `mascot.pxl` has the width, height, frame count and total ms of its `PetAnim`; the message names the sprite and the difference); `everyNameShippedOrListed` (file sprites = all new names minus `NOT_SHIPPED`); `restPose` (frame 0 and last frame equal `pet_idle` frame 0 for the `rest: yes` rows); `noClipping` (column 0, column 31, row 0 transparent in every frame); `palettePrefixAndProps` (first 22 entries equal `mascot.pxl`'s, extra chars within `n N i p P v`); `shadowAndMini` (32 x 6 with 3 frames, only char `e`; 16 x 16 with 6 frames); `loopSeamDance` (first and last frame equal) |
| `PetCoreIsPureTest` | the six pure files of section 2 contain no `import android.`, `import androidx.` or `import java.awt.` (the existing `StageCoreIsPureTest` scans `ui/pixel/stage` only) |
| `PetNoteSwipeTest` | `dismissThreshold` (0.34 width no, 0.35 yes, either side); `flingDismisses` (velocity 1600 with offset 0.06 width yes; opposite sign no; offset 0.04 no); `alphaCurve` (0 gives 1, 0.6 width gives 0, never negative); `queueUntouched` (a `PetNoteQueue` smoke case: the existing class behaves as before) |

References for anything astronomical: none are computed here. `sunAltitudeDeg` comes from SCENE's `SkyClock` and its tests (known sunrise times, moon phases, three stars); the 23:00 to 05:00 and -12 degree limits of `deepNight` are this draft's rule (assumed, owner may change).

## 9. Proof on the phone (owner-debug; safe: pins and pictures; chats only through the interface on the practice model, deleted afterwards)

Link (the robot adds the token): `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route home --es clock 2027-01-15T19:00 --es place 59.9,10.7,Oslo --es petpose read:2`. Pins: `petpose` (3.5: `<anim>`, `<anim>:<frame>`, `classic`, `off`), SCENE's `clock`, WEATHER's `weather` and `place`, APPEAR's `appearance` (`theme:`, `dark:`, `anim:`), the existing `reset`. The new log line `I/Pet: anim=... base=...` and tags `stage.hearth`, `home.pet`, `petnote`, `petnote.action`.

1. Poses, NEWROZ dark, 19:00 Oslo, scene crop, one picture per anim at its middle frame: all 22 anims by lower-case enum name (`idle`, `think`, `talk`, `happy`, `sleep`, `error`, `wave`, `blink`, `tail`, `warm`, `yawn`, `stretch`, `firefly`, `poke`, `read`, `write`, `carry`, `startled`, `dance`, `listen`, `shelter`, `snow`), the frame given as `<anim>:<k>` with `k` the middle one; plus `idle` with the Hermes state COLD, KINDLING and BLAZING (the three shadow frames). Five of them again in NERGIZ light: `idle`, `sleep`, `shelter`, `dance`, `carry` (readability on the always-dark scene).
2. Behaviour from the world, no pose pin: (a) `weather rain:heavy,cloud:3` at 2027-01-15T19:00: `app.log` shows `anim=SHELTER` within 2 s; picture; `weather off`: Pêt returns to IDLE. (b) `clock 2027-01-15T03:00` Oslo with Hermes running: `anim=SLEEP`; at 2027-01-15T19:00 not. (c) `weather snow:light` at 19:00: SNOW appears among the log lines within 90 s (idle weights), no FIREFLY, no POKE. (d) A practice-model chat with a tool step from Home's quick field (practice switched on through Settings, chat deleted afterwards): log `anim=THINK`, then `READ` while the tool runs, then back to IDLE; at 03:00 first `STRETCH` then THINK. (e) Fire off (Stop in Home): `YAWN` then `SLEEP`; start: `STRETCH`. (f) Tap on Pêt: bubble, WAVE or HAPPY alternating; 7 quick taps: OVERDRIVE bubble and `DANCE` for 4.2 s; a tap on the fire does not start Pêt's chain. (g) `petpose classic` for two minutes: the log shows only classic anim names and no crash (proves the fallback on the phone; the missing-sprite case itself is `PetLayerTest`). (h) a Pêt note (`PetNotes.show` from the gallery's `gallery.note`): rise and swipe away left and right; screenshot mid-swipe.
3. Not provable on owner-debug (no tool and no way to fail on the practice model, no unread routine result without the twin's seed): `MEMORY_SAVED` to WRITE, `Failed` to STARTLED, `Approval` to WAVE, `waitingResult` to CARRY and its tap action: unit tests plus `petpose write`, `startled`, `carry` for the pictures; the twin run is a later follow-up.
4. Smoothness: `phone-perf` Home idle scenario before and after: scene time rises by at most 0.05 ms on the optimised build; the debug build is measured for the record (about 10 ms a frame today, survey).

## 10. Cost

| What | Per frame (scene at most 30 fps, none while Home is off screen) | Cached |
|---|---|---|
| `PetBrain.step` | about 40 integer operations, no allocation (the pose object changes only at a transition, the idle draw happens once every 5.7 or 8.6 s) | none |
| `PetLayer.draw` | one 32 x 32 blit (at most 1024 `Argb.over`, about 450 opaque) and one 32 x 6 blit (192), about 0.01 ms optimised | the plain pixels of each frame in `Sprite.rasters` (`SpriteRaster.plain`), filled on first use |
| `PetController` | two idle collectors (events, busy count) that wake on engine activity only; `poseAt` is a copy of one small data class | none |
| `PetNoteHost` | one drag state; the rise adds one multiply per frame during the pop only | none |
Memory: 17 new sprites of about 6 frames at 1024 `Int` indices (4 KB) plus the same again as rasters: about 0.8 MB. Disk: `mascot_life.pxl` about 100 KB of text, inside the plan's 1.5 MB art share. Per minute: about 8 idle draws, no timers (the scene clock is the only clock and `rememberSceneClock` stops below STARTED), no disk, no network.

## 11. Risks and mismatches

1. `SpriteLoader.load` (`SpriteLoader.kt:15`) skips a bad file as a whole, so the fifteen new animations go in their own file (3.6) instead of `mascot.pxl` as the ownership table says; one malformed block then costs only the new life, never the scene.
2. Design language 6.1 gives `SceneState` a non-null `pet: PetPose`; the real `SceneState` (`SceneModel.kt:46`) must keep today's picture for `SceneRendererParityTest`, so `pet` is nullable and the old path stays until the oracle is retired (the lead's call; survey K).
3. "Every 4 to 9 seconds" (design language 7) becomes exactly 5.72 s or 8.58 s (two or three idle cycles), because an action must start on `pet_idle`'s rest pose; the design's range holds but is not uniform. A reviewer who prefers a freer rhythm either accepts a pop mid-breath (then the gap is drawn freely from 4000 to 9000 ms) or has ART-PET make every idle frame a valid rest pose.
4. Tapping: today any tap anywhere on the scene moves Pêt and stokes the fire (`HomeScreen.kt:173`). With the fire as the switch (plan 1.1) the Pêt tap area is the sprite box only and the overlap with the fire (sprite box ends at `fx - 11`, fire starts at `fx - 16`) goes to the fire. About 5 stage pixels (about 35 px on a 1080 px phone) are the fire's.
5. `MascotMood.TALKING` / `pet_talking` has no code path today (survey D) and none in 1a: the engine has no per-text-chunk event outside FireFeed's internals; the state exists in `PetAnim` for a later wave.
6. `EngineEvent` has no `ToolStarted` and adding one would break exhaustive `when`s (`PocketApp.kt:200-214`); the tool state therefore comes from FIRE's `toolsRunning` (7). If FIRE does not provide it, `READ` never shows and the brain still works (one line in `PetHost.kt` to change).
7. `ui_home_mascot_cd` says "salamander" (`strings_chat.xml:47`); Pêt is a newt everywhere else.
8. The plan says the notes "stay two seconds"; Wave 0 keeps 2.5 s as the minimum for reading (`PetNoteQueue.displayMillis`); not changed.
9. `HearthScene` ignores `Appearance.showMascot` (survey A) today and so does this design: hiding Pêt in the scene would need a `visible` flag on `PetPose`, not built (the legacy path draws Pêt too).
10. The 15 animations are not drawn yet and their quality is unknown: every sprite is judged on the contact sheet; rejected ones are deleted and listed in `NOT_SHIPPED`, and the app still looks like today there (3.1). The first review round may drop several; nothing else depends on one particular anim except `READ`, `SHELTER` and `STARTLED`, which are the weather's and the engine's visible answer.
11. Thread rule: `step` is called from the draw lambda on the main thread; `post` may come from any thread. Do not call `step` from two places for one brain (the pose would advance twice with different times).
12. Kotlin traps for a build agent that cannot compile: no slash-star inside a comment (write "the pixel folder"); `data object` needs Kotlin 1.9 or later (the project is on 2.x, WEATHER uses it too); `kotlin.random.Random(seed)` for the brain (a fixed algorithm), never `java.util.Random` or `Math.random`; declare properties before the `init` that reads them; integer weights, no float rounding in `idleWeights`; `0xFF...` colour literals need `.toInt()`.
