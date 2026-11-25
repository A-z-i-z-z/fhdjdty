# Wave 1a, area APPEAR: contract draft

Package `dev.pocketagent.app` is written `app`. Paths are under `app/src/main/java/dev/pocketagent/app/` (`J/`) unless they start with `app/` or `tools/`. Line numbers were read from the files at HEAD `fe0e0ea`. Sources used: plan 1.9 (lines 881-884), design language 2.3 (black night, line 120), 4 (haptics), 5 (sound), 6.2 (sky names, days), 7 (Pêt outside Home, line 406), `docs/waves/w0-contract.md` 1 to 3, `dist/w1a/survey.md`, `dist/w1a/draft-WEATHER.md`. **Design language section 8 has no description of an Appearance screen** (grep: the word does not occur): the layout of section 3.5 is mine, built from the names in 6.2 ("Sky: follows the day / always night / always day") and the existing screen.

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| A1 | `Appearance` gets 7 fields (`sky`, `blackNight`, `classicFire`, `petInChats`, `days`, `rainSound`, `crackle`), stored keys, the one-time migration that keeps Newroz on for an existing installation | core |
| A2 | `AppearanceStore.pin(...)` (memory only) and `AppearancePin.parse` for the debug link extra `appearance` | core |
| A3 | Rows in Appearance: Sky (3 chips), Place (row and city sheet), Weather switch (absent without a contact), Days (one switch per list of `SceneCalendar`), Classic fire, Black night, Pêt in chats, Touch feedback (3 chips) | core |
| A4 | `PocketColors.withBlackNight()` and its use in `PocketTheme` and the theme cards | core |
| A5 | Sound cues `WAKE`, `SLEEP` (per pack) played by `SceneSounds` on hearth changes | core |
| A6 | Rain loop (`SoundLoop.RAIN`): engine loop support (fade 400 ms), `SceneSounds`, the row "Sound of rain" | core |
| A7 | `tools/sounds/make_sounds.py` and the six generated files | core (without files the cues and loops stay silent) |
| A8 | Crackle loop (`SoundLoop.CRACKLE`) and its row | extra |
| A9 | `appearance` pin items `theme`, `dark`, `anim` (so one scenario can photograph both themes) | extra |

Cut order (first cut first): A8, A9, A6 (the rain row and loop; the engine keeps `setLoop` as an empty body), black night in the theme cards, the Days rows (the field and the migration stay), A5. Never cut: A1, A2 (other areas read the fields and the proof needs the pin), the Sky chips, the Weather row's hiding rule.

## 2. Files (I own them exclusively)

Create:
- `J/ui/theme/BlackNight.kt`: pure; `PocketColors.withBlackNight`.
- `J/ui/theme/AppearancePin.kt`: pure; the grammar of the debug extra.
- `J/ui/settings/AppearanceScene.kt`: the new rows (`SceneRows`, `TouchSoundRows`, `PlacePickerSheet`) and `AppearanceTags`.
- `J/voice/SceneSounds.kt`: `SceneSounds` composable and the pure `hearthCue`.
- `app/src/main/res/values/strings_appearance.xml` (prefix `apr_`).
- `app/src/main/res/raw/sfx_hearth_wake.wav`, `sfx_hearth_sleep.wav`, `sfx_studio_wake.wav`, `sfx_studio_sleep.wav`, `sfx_loop_rain.wav`, `sfx_loop_crackle.wav` (generated; `keep.xml` already keeps `@raw/sfx_*`, so `keep.xml` is not touched).
- `tools/sounds/make_sounds.py`.
- Tests: `app/src/test/java/dev/pocketagent/app/ui/theme/{AppearanceModelTest,AppearanceMigrationTest,AppearancePinTest,BlackNightTest}.kt`, `.../voice/{SoundCuesTest,SoundFilesTest}.kt`.

Modify:
- `J/ui/theme/Appearance.kt` (fields, keys, migration, pin).
- `J/ui/settings/AppearanceSection.kt` (anchors in 3.5; `LanguageSection` at line 273 stays as it is: LANG takes it out in Wave 1b).
- `J/voice/SoundEngine.kt` (two cues, loops, file lookup).
- `J/ui/theme/PocketTheme.kt` lines 66-68 only (the `remember` that builds `colors`). No other 1a area lists this file; I take it for this one edit.

Not touched on purpose: `voice/VoicePrefs.kt` and `ui/settings/VoiceSoundSection.kt` (not mine: crackle and rain live in `Appearance`), `data/BackupManager.kt` (`PREF_SCHEMA` at line 244; risk 3), `NOTICE` (request, section 7).

## 3. Shared symbols I PROVIDE

### 3.1 Appearance model (`app.ui.theme`, `Appearance.kt`)

```kotlin
enum class SkyMode { REAL, NIGHT, DAY }   // "follows the day", "always night", "always day"
@Immutable data class Appearance(
    /* the ten existing fields, unchanged and in the same order */
    val sky: SkyMode = SkyMode.REAL,
    val blackNight: Boolean = false,        // dark variants only
    val classicFire: Boolean = false,       // true gives today's fire back
    val petInChats: Boolean = true,
    val days: Set<String> = emptySet(),     // ids of the switched-on lists of SceneCalendar
    val rainSound: Boolean = true,          // plays only while the scene shows rain, so it is silent while weather is dark
    val crackle: Boolean = false,
)
val Appearance.petInChatsVisible: Boolean get() = showMascot && petInChats
object DayLists {
    const val NEWROZ = "newroz"; const val MAX = 32
    fun isId(s: String): Boolean                       // matches [a-z0-9_]{1,32}
    fun clean(ids: Collection<String>): Set<String>    // keeps valid ids, at most MAX, unknown ids are kept (a later list needs no change here)
}
internal fun isExistingInstall(firstInstallMs: Long, lastUpdateMs: Long, toleranceMs: Long = 2_000L): Boolean   // lastUpdateMs - firstInstallMs > toleranceMs
internal fun initialDays(existingInstall: Boolean): Set<String>                                              // existing: setOf(DayLists.NEWROZ), fresh: emptySet()
// AppearancePin: see 3.2 (the class lives in AppearancePin.kt)
// AppearanceStore gains:
fun pin(p: AppearancePin?)     // no-op unless BuildConfig.TEST_HOOKS; memory only; null unpins; the flow then shows p.change(stored)
val isPinned: Boolean
```
Readers (exact names, composable form `Pocket.appearance.<name>`, non-composable `AppearanceStore.get(ctx).appearance.value.<name>`): SCENE `sky`, `days`, `blackNight`; FIRE `classicFire`; PET `petInChatsVisible`; SceneSounds `rainSound`, `crackle`; every composable `haptics` through the existing `rememberHaptics()` (live). The effective values (pin included) are what the flow emits. `update` always edits the stored value, never the pinned one.

### 3.2 Pin grammar (`AppearancePin.kt`, pure)

```kotlin
class AppearancePin(val change: (Appearance) -> Appearance) {
    companion object { const val OFF = "off"; fun parse(text: String): AppearancePin? }   // null when any item is bad (the whole value is rejected); "off" is handled by the caller (Setting.Clear)
}
```
Items, comma separated, each `key:value` (lower case): `sky:real|night|day`, `black:0|1`, `classic:0|1`, `pet:0|1`, `haptics:off|subtle|full`, `days:none` or `days:<id>[+<id>...]` (ids by `DayLists.isId`), `rain:0|1`, `crackle:0|1`, and (A9) `theme:newroz|zagros|semender|nergiz|kawa|roj`, `dark:system|dark|light`, `anim:full|reduced|off`. A repeated key: the last wins. Empty text: null.

### 3.3 Black night (`BlackNight.kt`, pure)

```kotlin
fun PocketColors.withBlackNight(on: Boolean = true): PocketColors
```
Returns `this` when `!on` or `!isDark`. Otherwise `copy(bg = Black, bgDeep = Black, surface = oldBg, surfaceHigh = oldSurface)`; everything else (text, outline, accent, bubbles, `codeBg`, fire) is unchanged. Proof of contrast: every old background colour is replaced by black or by a colour that was already a background of the same palette, and black is darker than all of them, so every pair that passed `PaletteContrastTest` still passes; `BlackNightTest` checks it, `KNOWN_FAILURES` is not touched.

### 3.4 Sound (`app.voice`)

```kotlin
enum class SoundCue { COMPLETE, SEND, TOOL, ERROR, APPROVAL, FORGE, MEMORY, SPARK, TAP, MIC, WAKE, SLEEP }   // two appended, order of the old ten unchanged
enum class SoundLoop { RAIN, CRACKLE }
// SoundEngine gains (constructor unchanged: SoundEngine(context, prefs))
fun setLoop(loop: SoundLoop, level: Float)   // any thread; level 0..1 of the loop's base gain; 0 fades out and stops; same level again is a no-op
fun stopLoops()                              // fades every loop out (400 ms)
object SoundFiles {
    fun cueName(pack: SoundPack, cue: SoundCue): String?   // "sfx_hearth_wake"; null for SoundPack.OFF; rule: "sfx_" + pack.name.lowercase() + "_" + cue.name.lowercase()
    fun loopName(loop: SoundLoop): String                  // "sfx_loop_rain", "sfx_loop_crackle"
}
object LoopMath {
    const val FADE_MS = 400L; const val RAIN_BASE = 0.55f; const val CRACKLE_BASE = 0.25f
    fun fade(from: Float, to: Float, elapsedMs: Long, durationMs: Long = FADE_MS): Float   // linear, elapsed clamped to 0..duration
    fun rainLevel(rainLevel: Int, enabled: Boolean): Float          // 0 or !enabled gives 0f; 1 gives 0.4f; 2 and above 0.8f
    fun crackleLevel(blazing: Boolean, enabled: Boolean): Float     // 0.25f when both, else 0f
}
// app.voice (SceneSounds.kt)
fun hearthCue(prev: HearthState?, next: HearthState): SoundCue?   // (COLD or KINDLING) to BLAZING gives WAKE; BLAZING to COLD gives SLEEP; null prev, same state, any other pair: null
@Composable fun SceneSounds(hearth: HearthState, rainLevel: Int)   // HOME calls it once inside Home
```
Engine behaviour: **cues** play exactly as today (`play`, lines 98-118): pack OFF, silent ringer, volume at most 0.01 and a missing sample return silently; gains WAKE `vol * 0.8`, SLEEP `vol * 0.65`. **Files of the new cues and loops are looked up with `context.resources.getIdentifier(name, "raw", context.packageName)`**, never `R.raw.<name>`: a missing file gives id 0, the cue stays silent and the build still compiles. The ten old cues keep their `R.raw` maps. Loops: `SoundPool.play(sample, 0f, 0f, 1, -1, 1f)` (loop = -1), then volume steps every 40 ms on the main looper through `LoopMath.fade` to `vol * base * level`; at 0 the stream is stopped. A loop is held at level 0 whenever the same gates as a cue fail (pack OFF, silent ringer, volume at most 0.01) or `AppForeground.isForeground` is false; the engine re-checks the gates on every `setLoop` call and on every fade step. One `AppLog.i("Sound", "loop RAIN level=0.8")` line per change of target; one `Sound: loaded <n> of <m>, missing: <names>` line at start; with `BuildConfig.TEST_HOOKS` also `Sound: play WAKE pack=HEARTH` per new cue.
`SceneSounds`: remembers the previous `HearthState` (null at first composition: no sound at start), plays `hearthCue` through `PocketApp.from(ctx).sounds`; inside `repeatOnLifecycle(STARTED)` (same pattern and imports as `ui/pixel/SceneClock.kt:14-39`) it sets the loops from `LoopMath.rainLevel(rainLevel, ap.rainSound)` and `LoopMath.crackleLevel(hearth == BLAZING, ap.crackle)` and re-sets them when the sound pack or volume (`app.voicePrefs.settings`) changes; leaving STARTED or the composition calls `stopLoops()`.

### 3.5 Settings rows (`app.ui.settings`, `AppearanceScene.kt`; screen kind: settings list, states below)

Order on the screen, with the anchor in `AppearanceSection.kt` where the new block goes (find by the quoted text, not by line):
1. unchanged: themes, accent, dark mode (`// ---- dark mode ----`).
2. **after the dark-mode `ChipRow`**: `PocketSwitchRow(apr_black_title, ap.blackNight, ..., subtitle = apr_black_sub)` tag `appearance.black`; when `!dark` (the local `dark` of line 65) a `Muted(apr_black_light_note)` under it.
3. unchanged: text size, animations, bubbles (`// ---- bubbles ----`).
4. **after the bubbles block, before the mascot switch**: `SceneRows(ap, store)`:
   - `SubHeading(apr_scene_head)`.
   - Sky: `CText(apr_sky_title)`, `Muted(apr_sky_sub)`, a `ChipRow` of three `PocketChip` (`apr_sky_real`, `apr_sky_night`, `apr_sky_day`), tags `appearance.sky.real|night|day` on each chip.
   - Place: a `PixelPanel` row (tag `appearance.place`): title `wx_place_title`, line `wx_place_auto(place.name)` or `wx_place_picked(city.name, city.countryCode)` from `PlaceStore.get(ctx).choice` and `.place`, `Muted(wx_place_sub)`, a `PixelButton(wx_place_change, kind = SECONDARY)` that opens `PlacePickerSheet`.
   - Weather: only when `WeatherStore.get(ctx).available`: `PocketSwitchRow(wx_switch_title, enabled, setEnabled, subtitle = wx_switch_sub)` tag `appearance.weather`, then `Muted(wx_credit)`. When not available **nothing at all is composed** (no row, no spacer).
   - Days: only when `SceneCalendar.lists` is not empty: `CText(apr_days_title)`, `Muted(apr_days_sub)`, one `PocketSwitchRow(t(list.titleRes), id in ap.days, ..., subtitle = t(list.noteRes))` per list, tag `appearance.days.<id>`.
   - Classic fire: `PocketSwitchRow(apr_classic_title, ap.classicFire, ..., subtitle = apr_classic_sub)` tag `appearance.classicFire`.
5. unchanged mascot switch, then **directly after it** `PocketSwitchRow(apr_pet_title, ap.petInChats, ..., subtitle = apr_pet_sub)` tag `appearance.pet`.
6. unchanged pixel-font switch, then **after it** `TouchSoundRows(ap, store)`:
   - `CText(apr_haptics_title)`, `Muted(apr_haptics_sub)`, `ChipRow` of `apr_haptics_off|subtle|full`, tags `appearance.haptics.off|subtle|full`; a tap stores the level and plays `Haptics.play(LocalView.current, HapticCue.SEND, newLevel)` as the preview (SUBTLE and FULL feel it, OFF does not).
   - Rain: only when `WeatherStore.available`: `PocketSwitchRow(apr_rain_title, ap.rainSound, ..., subtitle = apr_rain_sub)` tag `appearance.rain`.
   - Crackle (A8): `PocketSwitchRow(apr_crackle_title, ap.crackle, ..., subtitle = apr_crackle_sub)` tag `appearance.crackle`.
7. unchanged app icon block.

Other edits in `AppearanceSection.kt`: `ThemeCard(...)` gets `blackNight: Boolean` and uses `paletteFor(id, dark, accent).withBlackNight(blackNight && dark)`; the call at line 74 passes `ap.blackNight`. Every new click plays `SoundCue.TAP` through `PocketApp.from(ctx).sounds` only where the old rows do (icon picker); switches and chips already carry their own haptic. Only `Space`, `Corner`, `CText`, `Muted`, `SubHeading`, `ChipRow`, `ChoiceRow` (all in `CtlUi.kt`) and the Wave 0 components are used; no new `N.dp` equal to a token.

`PlacePickerSheet(onDismiss: () -> Unit)` (private to the file): `PocketSheet(title = wx_place_sheet_title)`; a `PocketTextField(singleLine = true, placeholder = wx_place_search_hint)` tag `appearance.place.search`; results from `PlaceStore.get(ctx).search(query, 8)` run in `LaunchedEffect(query) { delay(120); withContext(Dispatchers.Default) { ... } }`; each result is a `ChoiceRow(selected = it == current city, title = wx_place_picked(name, cc), subtitle = nativeName when not blank)` tag `appearance.place.result.<i>`; a tap calls `store.setCity(city)`, `TICK` haptic, then `close()` of the `PocketSheetScope`; below the list a `PixelButton(wx_place_use_zone, kind = SECONDARY)` tag `appearance.place.auto` that calls `store.setAuto()` and closes. Five states: **empty query** (hint and the current place line, no list); **loading** (the 120 ms before the first result: previous list stays); **results**; **no result** (query of 2 or more characters: `wx_place_none`); **error** (`table.cities.isEmpty()`, assets unreadable: `apr_place_unavailable`, the button stays). Empty states of the other rows: Days hidden, Weather hidden.

```kotlin
object AppearanceTags {
    const val BLACK = "appearance.black"; const val PLACE = "appearance.place"; const val PLACE_SEARCH = "appearance.place.search"
    const val PLACE_AUTO = "appearance.place.auto"; const val WEATHER = "appearance.weather"; const val CLASSIC = "appearance.classicFire"
    const val PET = "appearance.pet"; const val RAIN = "appearance.rain"; const val CRACKLE = "appearance.crackle"
    fun sky(m: SkyMode) = "appearance.sky." + m.name.lowercase()   // real, night, day
    fun haptics(l: HapticLevel) = "appearance.haptics." + l.name.lowercase()
    fun day(id: String) = "appearance.days.$id"; fun result(i: Int) = "appearance.place.result.$i"
}
```
(The place and weather tag strings are exactly the ones in `draft-WEATHER.md` 3.7.)

### 3.6 Script (`tools/sounds/make_sounds.py`)

`python tools/sounds/make_sounds.py [--out app/src/main/res/raw] [--check] [--selftest]` (Python 3.12, standard library only: `wave`, `struct`, `math`, `random.Random(20261125)`; no package, no download). Writes the six files, mono, 16-bit PCM, **16 000 Hz**, and prints one line `wrote <file> <seconds> s <bytes> B peak <x>` per file. `--check` writes nothing and exits 1 when a file is missing or breaks the limits of `SoundFilesTest` (it does not compare bytes: `math.sin` may round differently on another platform). `--selftest` renders each recipe to memory and checks length, peak and loop seam, prints `SOUNDS SELFTEST OK`. Recipes (noise = uniform random in -1..1; `lp(f)` and `hp(f)` one-pole filters; all envelopes start and end at 0, the first and last 4 ms are faded so a cue never clicks; peak normalised to the value given):

| File | Length | Recipe | Peak |
|---|---|---|---|
| `sfx_hearth_wake` | 0.62 s | noise through `lp` sweeping 300 to 1400 Hz over 0 to 0.35 s and back to 500 Hz, attack 40 ms, exponential decay to 0.62 s; plus a sine gliding 90 to 140 Hz (gain 0.45, attack 30 ms, decay 0.30 s); plus three 2 ms noise ticks at 0.12, 0.20, 0.33 s (gain 0.2) | 0.80 |
| `sfx_studio_wake` | 0.55 s | as above without the ticks, `lp` 500 to 2400 Hz, sine 160 to 260 Hz | 0.80 |
| `sfx_hearth_sleep` | 0.65 s | noise through `hp(1500)` then `lp` falling 6000 to 1200 Hz, attack 20 ms, decay time constant 0.18 s; plus a sine gliding 110 to 70 Hz (gain 0.25, from 0.35 s) | 0.70 |
| `sfx_studio_sleep` | 0.60 s | as above, `hp(2500)`, no sine | 0.70 |
| `sfx_loop_rain` | 2.4 s | noise through `hp(400)` and `lp(5000)`, slowly modulated by a `lp(3)` noise (plus or minus 15 per cent), plus 40 random drop ticks (1 to 2 ms, 3 to 7 kHz, gain 0.08 to 0.20); rendered 2.6 s and made seamless: `out[i] = x[i]*sin(pi/2*i/X) + x[L+i]*cos(pi/2*i/X)` for the first X = 3 200 samples (0.2 s), L = 38 400 | 0.70 |
| `sfx_loop_crackle` | 3.0 s | Poisson pops at 9 per second (a 1 to 6 ms noise burst with exponential decay, differenced for a bright tone, level log-uniform 0.05 to 0.5) over a `lp(250)` rumble at 0.04; same seam rule | 0.80 |

Total of the six files: at most 300 KB (the plan gives all sound 1 MB; the old twenty files are 73 KB). Sources and licence: see section 6 and 11.

## 4. Shared symbols I CONSUME

| From | Form |
|---|---|
| Wave 0 | `Pocket.colors`, `Pocket.appearance`, `Pocket.motion`; `PocketSheet(onDismiss, modifier, title, maxHeightFraction, content: PocketSheetScope.() -> Unit)` with `PocketSheetScope.close(then)`; `PocketSwitchRow(title, checked, onCheckedChange, subtitle, modifier)`; `PocketChip(text, selected, onClick, modifier, icon)`; `PocketTextField`; `PixelButton(text, onClick, modifier, icon, kind, enabled)`; `PixelPanel`; `Space`, `Corner`; `HapticCue`, `HapticLevel`, `Haptics.play(view, cue, level)`, `rememberHaptics()`; `t(@StringRes id, vararg args)`; `AppForeground.isForeground`; `AppLog.i`; `BuildConfig.TEST_HOOKS`; `CText`, `Muted`, `SubHeading`, `ChipRow`, `ChoiceRow` (`CtlUi.kt`); `PocketApp.from(ctx).sounds` and `.voicePrefs`; `HearthState` (`ui/pixel/HearthScene.kt:27`) |
| WEATHER | `PlaceStore.get(ctx)`: `choice: StateFlow<PlaceChoice>` (`Auto`, `Picked(city)`), `place: StateFlow<Place>` (`name`), `search(query, limit): List<City>`, `setCity(City)`, `setAuto()`, `table.cities`; `City(name, nativeName, countryCode, ...)`; `WeatherStore.get(ctx)`: `available: Boolean`, `enabled: StateFlow<Boolean>`, `setEnabled(Boolean)`; strings `wx_place_title`, `wx_place_sub`, `wx_place_auto`, `wx_place_picked`, `wx_place_change`, `wx_place_sheet_title`, `wx_place_search_hint`, `wx_place_none`, `wx_place_use_zone`, `wx_switch_title`, `wx_switch_sub`, `wx_credit` (all as in `draft-WEATHER.md` 3.1, 3.4, 5) |
| SCENE | `object SceneCalendar { val lists: List<DayList> }` and `class DayList(val id: String, @StringRes val titleRes: Int, @StringRes val noteRes: Int)` in `app.ui.pixel.stage` (ids pass `DayLists.isId`; the first list is `DayLists.NEWROZ`); SCENE owns the two strings per list (`sky_`-prefixed) |
| HOME | calls `SceneSounds(hearth, rainLevel)` with `rainLevel = rememberWeather().look.rainLevel` (WEATHER, 0 to 2) |
| LEAD / BUILD | nothing new from Gradle |

## 5. Strings: `res/values/strings_appearance.xml`, prefix `apr_` (English only; TRANSLATE adds tr and fr). Each key carries `<!-- t: ... | kind: ... -->` directly above it.

| Key | English | Note |
|---|---|---|
| `apr_scene_head` | Sky and scene | small accent heading above the group of rows; up to 20 chars, row |
| `apr_sky_title` | Sky | title of the sky choice; one word; row |
| `apr_sky_sub` | The sun, moon and stars follow your place and the clock. Night and day stay fixed. | under the title; 2 lines; body |
| `apr_sky_real` | Follows the day | chip; the sky changes with the real time; up to 18 chars; chip |
| `apr_sky_night` | Always night | chip; up to 16 chars; chip |
| `apr_sky_day` | Always day | chip; up to 16 chars; chip |
| `apr_days_title` | Days | title of the group of days of a culture or region; one word; row |
| `apr_days_sub` | Days of a culture or a region that add one small touch to the scene. None is on unless you switch it on. | 2 to 3 lines; body |
| `apr_classic_title` | Classic fire | switch title; up to 20 chars; row |
| `apr_classic_sub` | Turns off the living edge and the sparks around the flame. The hand-drawn flame stays as it was. | 2 lines; body |
| `apr_black_title` | Black night | switch title; dark themes get a pure black background; up to 16 chars; row |
| `apr_black_sub` | Pure black behind the dark themes, with the panels one step lighter. Saves battery on OLED screens. | 2 lines; body |
| `apr_black_light_note` | Only used while a dark theme is showing. | muted line under the switch, shown while a light theme is showing; 1 line; body |
| `apr_pet_title` | Pêt in chats | switch title; Pêt is the mascot's name, never translate it; up to 18 chars; row |
| `apr_pet_sub` | Pêt may look over the top bar of a chat while the agent works. | 2 lines; body |
| `apr_haptics_title` | Touch feedback | title of the three chips; up to 20 chars; row |
| `apr_haptics_sub` | Subtle keeps only confirmations, errors and long presses. | 2 lines; body |
| `apr_haptics_off` | Off | chip; up to 10 chars; chip |
| `apr_haptics_subtle` | Subtle | chip; up to 12 chars; chip |
| `apr_haptics_full` | Full | chip; up to 12 chars; chip |
| `apr_rain_title` | Sound of rain | switch title; up to 20 chars; row |
| `apr_rain_sub` | A soft rain sound on the home screen while the scene shows rain. | 2 lines; body |
| `apr_crackle_title` | Fire crackle | switch title; up to 18 chars; row |
| `apr_crackle_sub` | A very quiet crackle while the fire burns on the home screen. | 2 lines; body |
| `apr_place_unavailable` | The city list could not be read. The place stays guessed from the time zone. | error line in the city sheet; 2 lines; body |

No `%` outside placeholders (there are none). WEATHER's `wx_*` keys are used as they are (section 4); SCENE's list titles are theirs.

## 6. Stored data (the previous version of the app ignores all of it)

| Item | Default | Why the old app ignores it |
|---|---|---|
| prefs `appearance`, keys `sky` (enum name), `blackNight`, `classicFire`, `petInChats`, `rainSound`, `crackle` (booleans), `days` (string set), `daysInit` (boolean marker) | absent: `REAL`, false, false, true, true, false, `emptySet()` (after the migration below), marker absent | `AppearanceStore.load()` of the old code reads its ten keys by name and `update()` only `put`s its own, so extra keys are neither read nor removed; an unknown enum name already falls back to the default (`parse`, line 89) |
| the migration (once, in `load()`, before the first read) | if `daysInit` is absent: if `days` is also absent write `days = initialDays(isExistingInstall(firstInstallTime, lastUpdateTime))` ; in every case write `daysInit = true` (one `apply()` of two keys, no other write) | `firstInstallTime` and `lastUpdateTime` come from `packageManager.getPackageInfo(packageName, 0)` inside try/catch (failure: not existing). There is no version number in the file, so "existing installation" is: the package was updated after it was first installed (more than 2 s apart). The owner's phone: installed long ago, updated by Wave 0 and by this build, so Newroz is on (today's bonfires on the ridges stay). A fresh install, or a restore on a new phone, gets none. The marker freezes the answer: a later CI update changes nothing. A restore (`BackupManager.applyPrefs`, line 199) clears the whole file, so the migration runs again on the next start and decides by the same rule |
| `AppearancePin` and the stored/pinned split | memory only | never written |
| `res/raw` six sound files, `tools/sounds/make_sounds.py` | shipped | new files in the APK |

Not in the backup (`BackupCore.PREF_SCHEMA`, `data/BackupManager.kt:245-247`, which already lacks `haptics`; follow-up in risk 3).

## 7. REQUESTS to other areas

- WEATHER: `ui/settings/PlaceWeatherSettings.kt` (their item W4, `PlaceRows()`, `WeatherSwitchRow()`, `WeatherTags`): **drop it**; the lead's split gives APPEAR the rows and the city sheet, written against their data API (section 4). Keep the `wx_*` strings and the tag strings. If W4 is already built, the lead picks one file: the other is deleted; both must not be called.
- WEATHER: `J/DebugLinks.kt` (they own it for this half): new extra `appearance` (`EXTRA_APPEARANCE = "appearance"`, add to the list at line 126), `DebugRequest` gets at the END `val appearance: Setting<AppearancePin> = Setting.Keep`; `parseParts`: `off` gives `Setting.Clear`, otherwise `AppearancePin.parse(v)?.let { Setting.Set(it) } ?: rejected.add(EXTRA_APPEARANCE)`; `apply`: Clear calls `AppearanceStore.get(app).pin(null)`, Set calls `.pin(p)`, **after `place` and before `lang`** (order: reset, clock, weather, place, appearance, lang, rtl, nav, seed, route); `reset=1` also unpins. Test lines in `DebugLinksTest`: `appearanceExtraParses`, `appearanceBadItemRejected`, `resetUnpinsAppearance`, `releaseBuildIgnoresAppearance`.
- HOME: `ui/home/HomeScreen.kt`: one call `SceneSounds(hearth = hearth, rainLevel = weather.look.rainLevel)` next to the `HearthScene(...)` call (survey A: `hearth` at HomeScreen.kt:141; `weather` from `rememberWeather()`); remove nothing else. The existing tap sounds (`SPARK`, `FORGE`) stay.
- SCENE: (1) provide `SceneCalendar.lists` and `DayList` as in section 4; (2) read `Pocket.appearance.sky`, `.days`; the Newroz bonfires (`drawDistantNewrozFires`, survey B step 7) are drawn only when `DayLists.NEWROZ in days` (this is how today's look is kept for an existing installation and goes away for a new one); (3) `SceneRendererParityTest` and `LegacyHearthReference` must be retired or updated by SCENE in the same change, because the default sky now follows the real day and a fresh install has no Newroz.
- FIRE: read `Pocket.appearance.classicFire` in the composable that builds the scene state (never in a draw lambda); true means today's fire exactly (no living edge, no sparks beyond today's embers).
- PET: read `Pocket.appearance.petInChatsVisible` for the small Pêt over the chat top bar and for any other new Pêt in the chat screens; `showMascot` keeps governing Home, Hermes and the empty states.
- ROBOT-T / lead: `ScenarioFilesTest.kt` known tags: add `appearance.black`, `appearance.place`, `appearance.place.search`, `appearance.place.auto`, `appearance.weather`, `appearance.classicFire`, `appearance.pet`, `appearance.rain`, `appearance.crackle`, and the patterns `appearance\.sky\.(real|night|day)`, `appearance\.haptics\.(off|subtle|full)`, `appearance\.days\.[a-z0-9_]+`, `appearance\.place\.result\.\d+`; new safe scenario `tools/scenarios/appearance-pins.json` with the steps of section 9.
- LEAD / INTEGRATE (one agent edits `NOTICE`, so the blocks of all areas do not collide): append (1) "Sound cues. Twenty UI cues in app/src/main/res/raw (sfx_hearth_*.ogg, sfx_studio_*.ogg): romainsimon/uisfx, CC0 1.0 (as recorded in README.md line 34; the exact upstream pack names are not in the repository and must be written here by whoever can look them up)". (2) "Generated sound cues and loops (sfx_*_wake.wav, sfx_*_sleep.wav, sfx_loop_*.wav): written by tools/sounds/make_sounds.py from noise and sine waves; no third-party material; released under CC0 1.0 together with the script (the lead confirms this dedication)". `NOTICE` today has **no entry for the existing uisfx files** (survey H): this fixes that debt too.
- BUILD / lead: `tools/size-budget.txt` sound share: the six files add at most 300 KB.

## 8. Tests (JUnit 4, JVM; time, ids and files are parameters; repo files read with `Assume.assumeTrue(file.exists())`)

| Class | Tests (name: what it proves) |
|---|---|
| `AppearanceModelTest` | `defaultsAreToday`: `Appearance()` has sky REAL, blackNight false, classicFire false, petInChats true, days empty, rainSound true, crackle false and the ten old fields unchanged; `petInChatsVisible` table (showMascot x petInChats); `dayListsClean`: `Newroz` and `a-b` and a 33-character id dropped, `newroz`, `x_1` kept, more than 32 ids cut to 32, order irrelevant; `dayListsIsId` |
| `AppearanceMigrationTest` | `existingInstallRule`: (1000, 1000) false, (1000, 3000) false, (1000, 3001) true, (5000, 1000) false; `initialDaysTable`: existing gives `{newroz}`, fresh gives empty; `newroz` equals `DayLists.NEWROZ` and `SceneCalendar.lists.first().id` (Assume SCENE exists) |
| `AppearancePinTest` | `eachItemParses` (all 11 keys); `badItemRejectsAll` (`sky:purple`, `black:2`, `days:A`, unknown key, empty, trailing comma); `lastWins`; `daysNoneAndList` (`days:none`, `days:newroz+x1`); `changeAppliesToAppearance` (a pin on a default `Appearance` gives the expected copy and leaves untouched fields alone) |
| `BlackNightTest` | `lightIsUnchanged`; `offIsUnchanged`; `darkBackgrounds` (bg and bgDeep are `#000000`, surface equals old bg, surfaceHigh equals old surface, everything else equal) for all 6 themes and 8 accents; `contrastNeverDrops`: for text, textMuted, accentSoft, error, success, warning, outline, the contrast on each new background is at least the contrast on the old background it replaces (WCAG 2.1 formula, relative luminance with gamma 2.4; reference values: black on white 21.0, `#777777` on black about 4.69, W3C "Understanding SC 1.4.3"; the test carries its own 12-line formula) |
| `SoundCuesTest` | `cueFileNames`: the rule gives the ten old names, `sfx_hearth_wake`, `sfx_studio_sleep`, null for OFF; `loopFileNames`; `fadeCurve` (0 at 0 ms, 0.5 at 200, 1 at 400, clamped past both ends, reverse direction, zero duration gives `to`); `rainLevels` (0 and disabled give 0, 1 gives 0.4, 2 and 5 give 0.8); `crackleLevels`; `hearthCueTable` (null to BLAZING null, COLD to BLAZING WAKE, KINDLING to BLAZING WAKE, BLAZING to COLD SLEEP, BLAZING to SMOKE null, SMOKE to COLD null, same state null); `cueEnumOrder` (the old ten keep their ordinals, WAKE 10, SLEEP 11) |
| `SoundFilesTest` (Assume the six files exist) | `wavHeaders`: RIFF, WAVE, PCM, mono, 16 bit, 16 000 Hz; `lengths`: cues 0.3 to 0.7 s, loops 2.0 to 4.0 s; `peaks`: 0.1 to 0.95 of full scale; `loopsSeamless`: the difference of the last and first sample at most 3 000 and the mean absolute value of the first and last 160 samples within a factor 2; `sizes`: each file at most 140 KB, all six at most 300 KB; `noticeNamesSources` (Assume `NOTICE`): contains `uisfx`, `CC0` and `tools/sounds/make_sounds.py` |

Pure by construction: `Appearance`, `AppearancePin`, `withBlackNight`, `LoopMath`, `hearthCue`, `SoundFiles`, `isExistingInstall`, `initialDays` take values, not a `Context`, a clock or a file. `AppearanceStore`, `SoundEngine`, the composables and the PackageInfo read are not tested on the JVM (the proof is on the phone).

## 9. Proof on the phone (owner-debug, safe: memory pins and pictures only; the one stored change is the migration marker)

Link: `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route settings:appearance --es appearance sky:night,black:1,haptics:subtle` (the robot adds `token`). Pins I need: `appearance` (3.2), SCENE's `clock`, WEATHER's `weather` and `place`.

Stills (each in NEWROZ dark and NERGIZ light via `appearance theme:..,dark:..`): (1) Appearance top to bottom at three scroll positions, default pins: the Black night row, Sky chips with REAL selected, Place row ("Guessed from the time zone: ..."), **no node `appearance.weather`**, Days rows, Classic fire, Pêt in chats, Touch feedback chips; (2) `home` with `clock 2027-01-15T22:00` and `sky:real`, `sky:night`, `sky:day` (the three must differ; `night` is today's look); (3) `black:1` on Home and on Appearance: the page background pixel is `#000000` in the dark shot and unchanged in the light shot; (4) the city sheet open with `ist` typed (İstanbul listed), tag `appearance.place.result.0` present, then `appearance.place.auto` tapped (needs the twin or a returned choice: on the owner's app only open and read, never pick) ; (5) `classic:1` against `classic:0` side by side on Home (FIRE judges); (6) `days:newroz` against `days:none` on `home` with a pinned clock near 21 March (SCENE judges).
Checks that are not pictures: (a) **migration on the owner's phone**: after the first start of the build, `adb shell run-as dev.pocketagent.app cat shared_prefs/appearance.xml` has `daysInit` true and `days` containing `newroz` (read only); (b) `app.log` has `Sound: loaded 6 of 6` (or lists the missing names); (c) rain loop: pin `weather rain:heavy` and `appearance rain:1`, open Home, `app.log` shows `Sound: loop RAIN level=0.8`, then `weather off` shows `level=0.0`, then leaving Home (tab) shows no further line and `stopLoops`; (d) WAKE and SLEEP: start and stop Hermes from Home with Home on screen, `app.log` shows `Sound: play WAKE pack=HEARTH` once when the state reaches Running and `play SLEEP` once at Stopped, none at app start; (e) with the phone silenced (`dumpsys audio` ringer mode) none of the three appears. Tags: `appearance.*` of 3.5, `settings.section.appearance`, `stage.hearth`.

## 10. Cost

| What | Per frame | Per minute | Why it stays in budget |
|---|---|---|---|
| Rows, sheet | 0 (a settings screen is not the Home scene) | 0 | the city search runs on `Dispatchers.Default` after 120 ms, at most 8 rows composed |
| `Appearance` change | one recomposition of the whole tree (`LocalAppearance` is a static local) | only when the user changes a setting | unchanged behaviour of the existing screen; the new fields change rarely; nothing reads a field in a draw lambda |
| `withBlackNight` | 0 | one `copy` per change of theme, dark mode or switch | inside the existing `remember` of `PocketTheme` |
| `SceneSounds` | 0 | LaunchedEffect keys change only on a hearth change, a rain level change, a setting change | no timer of its own |
| Loops | 0 on the UI thread; the mixing is native | a fade is 10 `setVolume` posts over 400 ms, only when a target changes | 2 loops at most, within the pool's 4 streams; stopped at level 0, in the background, while the ringer is silent |
| Memory | six decoded samples: about 0.25 MB | | cues 4 x about 20 KB, loops about 80 and 100 KB |
| Disk and APK | at most 300 KB in `res/raw`, one marker write at the first start | | `tools/size-budget.txt` |
Battery: no wake lock, no sensor, no network. A loop plays only while Home is STARTED and the app is in the foreground.

## 11. Risks, and mismatches with the plan and the code

1. **Overlap with WEATHER**: their draft (W4, `PlaceWeatherSettings.kt`) and this one both build the place rows and the weather switch. The lead's task gives them to APPEAR; request in section 7. If both are built, `AppearanceSection` would show two rows; INTEGRATE keeps one.
2. **Existing-installation rule is a heuristic** (no version number exists, and an `appearance` file written by the old app can be empty when the owner never changed a setting, so "the file exists" proves nothing). `PackageInfo` times are robust on a sideloaded `adb install -r`, but a user who uninstalls and reinstalls the same build is "fresh" and loses Newroz, by design. Failure of the read means "fresh".
3. **Backup**: `BackupCore.PREF_SCHEMA` (`data/BackupManager.kt:245-247`) lists nine of the ten old keys (not `haptics`) and none of mine, and `applyPrefs` (line 199) does `edit().clear()` before restoring: a restore wipes `sky`, `days`, `haptics` and the marker. Nobody edits that file in this half; follow-up: add the new keys and `haptics` to the schema.
4. **Plan vs lead on days**: design language 6.2 says the sky's own days (solstices, meteor showers, full moons) are on by default; the lead says no list is on by default. I give them no switch: they belong to the real sky and are SCENE's, shown only while `sky == REAL`. Only culture and region lists have switches. SCENE should confirm.
5. **"Loop stops when anything else starts playing"** (design language 5) is not buildable here: `SoundPool` has no audio focus, and the voice engine (`VoiceEngine.kt`) is protected. Read as: the loop is held at 0 while the ringer is silent, the app is in the background, the pack is OFF, volume is at most 0.01 or Home is not on screen. Voice mode (Wave 8) must call `stopLoops()` when it speaks; nothing calls it today.
6. **No Ogg encoder in the standard library**, so the new files are WAV (PCM). Android's `SoundPool` plays them. If an agent has the exact CC0 upstream (romainsimon/uisfx) offline and finds a better cue it may add a `.ogg` of the same base name instead; it may not download anything else and may not add a file whose licence it cannot read. Without a file the cue stays silent (`getIdentifier` returns 0), which is acceptable for a dark first half.
7. `NOTICE` lacks entries for the existing sounds, the Supertonic model and sherpa-onnx (survey H); only the sound lines are in this contract.
8. The scene's `hearthSkyTop(colors)` (`ui/pixel/HearthScene.kt:42`) uses `bgDeep` in dark themes, so black night makes the sky's top black; with the real sky SCENE computes its own colours and should treat black night as a floor, not an input.
9. `LocalAppearance` is `staticCompositionLocalOf` (`PocketTheme.kt:38`): every field change recomposes the whole tree; for the pin this is only in tests. `update` writes all keys on every change (Appearance.kt:52-63): the seven new ones are added there.
10. `showMascot` (subtitle `ctl_ap_mascot_sub`: "home screen and in empty places") and `petInChats` overlap in meaning; the extension `petInChatsVisible` makes `showMascot = false` win. The old subtitle is TEXT's/WORDS's and is not edited here.
11. Kotlin traps for a build agent that cannot compile: `SharedPreferences.getStringSet` returns a live set that must be copied (`.toSet()`), and `putStringSet` needs a new set; declare `appContext` and `prefs` before `stored` and `state` in `AppearanceStore` (textual init order); no slash-star inside a comment; `getIdentifier` needs `context.packageName` (the twin's differs from the namespace and that is right); `SoundPool.play` with `loop = -1` returns stream 0 on failure; the fade handler must be created on `Looper.getMainLooper()`.
12. Light themes with black night: the switch is shown but has no effect while a light variant is showing (the note says so); the preview cards follow `dark` the same way.
13. Taste: the WAKE and SLEEP recipes are procedural; a reviewer must listen once (the lead plays the six files on the PC) before they ship; a cue that sounds wrong is deleted, not shipped (the engine then stays silent for it).
