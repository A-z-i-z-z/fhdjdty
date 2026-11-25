# Wave 1a contract: "The living hearth, first half" (written 2026-11-25)

**This contract overrides the area files (`docs/waves/w1/<AREA>.md`) wherever they differ, and `docs/waves/w1/LEAD-DECISIONS.md` where it says so. Every build agent reads this contract first (sections 1 to 3, then the amendments of its own area in section 5), then its own area file, and applies the amendments before writing a line.** The area files stay as they are; they remain the source for everything this contract does not mention (formulas, behaviour, tests, costs, risks). Plan: `docs/phase4-plan.md` 7.1 (lines 768 to 917, the half "0.3a"); model: `docs/waves/w0-contract.md`, whose registry (its section 3) is implemented as written and is the API this wave stands on. Rulings that every agent must know are `#R1` to `#R14` (3.0); the resolution of every request is section 4, cited by the ids S1, W3, F9 and so on.

Eight areas build in parallel (stage 1): SCENE, WEATHER, FIRE, PET, ART-PET, ART-WORLD, HOME, APPEAR; then DATA (stage 2), LOOK (stage 3), TRANSLATE (stage 4), INTEGRATE (stage 5, with LOOK-2 after its compile). In the tables "J/" is `app/src/main/java/dev/pocketagent/app/`, "T/" is `app/src/test/java/dev/pocketagent/app/`, "R/" is `app/src/main/res/`, "A/" is `app/src/main/assets/`, "stage/" is `J/ui/pixel/stage/` (pure Kotlin: no `android.` and no `androidx.` import, scanned by `StageCoreIsPureTest`), `app` is the package `dev.pocketagent.app`.

## 1. Ground rules

### 1.1 For every build agent

- Rules of `w0-contract.md` 1.1 hold as written (own files only; no Gradle, no adb, no git, no phone; English only in your own strings file and prefix; every key carries `<!-- t: ... | kind: ... -->`; `t()` and `tn()` in composables; no case change of display text; tokens `Space`, `Corner`, `Dur`, `Loop`, `Flourish`, `Pocket.motion`, `HapticCue`, `PetNotes`, `PocketSheet`, `ConfirmDialog`; unit tests JUnit 4 and JVM only with `Assume.assumeTrue(file.exists())` for repo files; pure logic takes time, place, files and randomness as parameters; final report lists files, deviations from the registry (name the symbol), `REQUESTS`, what you could not verify; never write a name, project id, e-mail or device serial). **Changes for this half:**
  - Where w0 1.1 says "no git": the lead's ruling 12 of w0 still holds (build agents never run a git command that changes tree or index; reading is fine).
  - You may run only your own scripts' `--selftest` and `--check` with Python 3.12 (`python -I`), `sh -n`, `rg --no-config`. **You download nothing.** Only DATA (stage 2) downloads (7.1).
  - A new test or script that needs a file another stage produces (`stars.bin`, the city table, a WAV, `mascot_anim.pxl`) must `Assume` it exists; the code must run with the file missing (every consumer in the registry has a stated fallback).
- **Cut rule.** If your part does not finish, cut only items of your own cut list, in the order of section 9, and only symbols section 3 marks "cuttable". A cut item keeps its symbol with an empty body or `null`. Never cut a symbol another area calls.
- **Gotchas of `w0-contract.md` 1.4 hold by reference** (init order, no slash-star inside comments, `0xFF...` needs `.toInt()`, aapt2 trims strings, `env VAR=` and install paths, Windows PowerShell 5.1, `kotlin-reflect` absent, no `recycle()`, `PixelPanel` fill, no opaque status-bar colour). **New in this half** (found by the drafts):
  - Time zone: every sky, season, day and Pêt hour comes from the phone's zone (`ZoneId.systemDefault()`), never from the pinned place. A pinned clock for a place in another zone must carry an offset (`2027-01-15T21:00+01:00`; `AppClock.parse` accepts it).
  - Astronomy in `Double`, pixels in `Float`; angles with `Math.floorMod`; `Locale.ROOT` in every number format; `BigDecimal(value.toString())`, never `BigDecimal(value)`; `kotlin.random.Random(seed)` or an integer hash for anything seeded, never `Math.random` or `java.util.Random`; `org.json` `optDouble` is NaN for a missing key, test with `has`.
  - `data object` needs Kotlin 2.x (the project has it). Arrays are declared before the `init` that fills them. `SharedPreferences.getStringSet` returns a live set: copy it. `getIdentifier(name, "raw", packageName)` for the six new sound files, never `R.raw.<name>` (a missing file must not break the build).
  - `Sprite.frameAt` always loops: one-shot and intro or outro playing is code, and a sprite chosen by index (`scene_woodpile`, `pet_head`) is drawn with `PixelStage.blit(sprite, frame, x, y)` or `PixelImage(name, frame = n, playing = false)`, never animated.
  - `SpriteLoader` skips a bad `.pxl` file as a whole and a duplicate name keeps the first: every new sprite lives in a NEW file; `scene.pxl`, `mascot.pxl`, `fire.pxl` stay byte-identical (hash and `--check` tests).
  - Python scripts: `sys.dont_write_bytecode = True`, `sys.path.insert(0, HERE)` (`python -I` does not put the script folder on the path), output identical on Windows and Linux (integers and seeded generators only; a float compared with a threshold goes through `round(v, 6)`), LF line endings, UTF-8 without BOM.
  - `LegacyHearthReference.kt` and `SceneRendererParityTest.kt` are **not retired and not edited** (#R1). Every new `SceneState` field defaults to "off" so the old picture is still proven.

### 1.2 The safety of the owner's data (the proof runs on the owner's own app, the owner-debug build)

- Rules of `w0-contract.md` 1.2 and 1.3 hold (variants, `TEST_HOOKS`, `IS_TWIN`, owner-debug runs only `"safe": true` scenarios, rollback APK stays in `dist\rollback\`). Add:
- **No stored format changes in a way the Wave 0 build cannot read**; every new preference or file is a file or key the old code never opens (section 6 lists each with its default). **Exactly one stored write happens on the owner's phone without the owner touching a setting: `scene_days.migrated` (one small write at the first Home composition).** Nothing else writes at start: `Appearance` has no migration marker in this half (#R4), no cache file exists while the weather is dark, the place stays "auto" until the owner picks one.
- **Every pin is in memory** (`BuildConfig.TEST_HOOKS` only, cleared by `reset=1`): clock, place, weather, sky, days, appearance (incl. theme and dark), fire, Pêt, woodpile. A robot scenario never taps a setting row that stores (chips, switches, the city list, a user day); it may open the screen and read. If a picture needs a setting, it uses the pin.
- **No request leaves the phone**: `BuildConfig.WEATHER_CONTACT` is empty in every variant and `WeatherGuard.contactOk("")` is false, so `WeatherClient.fetch` returns `Blocked` before it builds a header (test `engineWithEmptyContactNeverCallsTransport`). The switch is not shown. After the proof run `app.log` has no `Weather: request` line.
- The Linux image is never touched (`AGENTS.md`; w0 1.2 last bullet). Hermes is not started, stopped or changed by any test of this wave except through the interface exactly as w0 allows (a practice chat deleted afterwards).
- No new permission, no new library, no manifest entry (section 6.3). The accelerometer needs no permission; it is registered only while Home is on screen at FULL animation, not classic, switch on (FIRE 3.4), and the proof checks `dumpsys sensorservice`.
- Taste decisions in the drafts and here are "assumed, owner may change" (LEAD-DECISIONS rule); only the two taste gates (the fire, the default sky) wait for the owner.

### 1.3 Budgets (design language 11; measured, reported, never claimed)

| Budget | Value | Measured where |
|---|---|---|
| Scene per frame, whole | under 1 ms on the optimised build; 95 percent of frames within 8.3 ms | needs the twin (R8 build); owner-debug gives the debug numbers for the record |
| Shares inside it | SCENE sky 0.15 ms, WEATHER 0.25 ms worst case, FIRE 0.15 ms, PET 0.05 ms, rest as today | `phone-perf` Home scenarios with and without each pin |
| Allocation per frame | none (arrays sized at construction, caches keyed) | unit tests where the drafts name them |
| Size | APK growth in this half at most 1 MB: stars 9 KB, places 90 KB, art about 300 KB text (about 60 KB packed), sounds at most 300 KB | `tools/size-budget.txt` (INTEGRATE) |
| Network | none (dark) | proof item 8.4 |
| Sensors, timers | none off Home; no wake lock | proof item 8.3 |

## 2. Ownership

One row per file or folder; each file appears exactly once. "Stage" is the stage that writes it (1 = the eight areas, 2 DATA, 3 LOOK, 4 TRANSLATE, 5 INTEGRATE). A file named in a draft and not in this table is **dropped**; dropped files are listed in 2.12. Where two drafts claimed a file the loser's need is an accepted request (section 4).

### 2.1 SCENE (stage 1)

| Files | Notes |
|---|---|
| stage/`SkyModel.kt`, `SkyClock.kt`, `SkyMoments.kt` (moments, `SkyPalette`), `SkyFrames.kt`, `SkyProjection.kt`, `Stars.kt`, `Planets.kt` (E1), `SkyBodies.kt`, `Seasons.kt`, `SceneCalendar.kt` (days, E2 eclipses), `SceneLayers.kt` (`SceneGeometry`, `FireLayer` interface), `SkyTint.kt` | new, pure |
| stage/`SceneModel.kt`, `SceneRenderer.kt` | modified (3.1, 3.3, amendments 5.1) |
| J/ui/pixel/`SkyHost.kt` (`rememberSkyFrame`, `rememberStarTable`, `HomeSky`, `HearthInfo`, `rememberHearthInfo`, `SkyPins`, `SceneDaysStore`, `DayListTexts`) | new, Android |
| J/ui/pixel/`HearthScene.kt` | modified: the ONLY owner (HOME, FIRE, PET, WEATHER send requests, 4) |
| A/sky/`stars.bin`, A/sky/`stars.source.txt` | produced by DATA (SCENE owns the script) |
| tools/art/`stars.py` | new |
| R/values/`strings_sky.xml` (`sky_`) | new |
| T/ui/pixel/stage/`SkyClockTest`, `SkyMomentsTest`, `SkyProjectionTest`, `StarTableTest`, `SkyFramesTest`, `SkyBodiesTest`, `SeasonsTest`, `SceneCalendarTest`, `SceneRendererSkyTest`, `SceneGeometryTest`, `PlanetsTest`, `EclipseTableTest`, `SceneStillsDump` (new: env `SCENE_STILLS_DIR`, section 7.4); T/ui/pixel/`SkyPinsTest` | `SceneRendererParityTest`, `LegacyHearthReference`, `SceneRendererTest`, `TestSprites` stay unchanged |

### 2.2 WEATHER (stage 1)

| Files | Notes |
|---|---|
| J/data/`Places.kt`, `PlaceStore.kt`, `Weather.kt`, `WeatherStore.kt` | new |
| stage/`WeatherLook.kt`, `WeatherParticles.kt`, `WeatherArt.kt`, `WeatherLayers.kt` | new, pure |
| J/ui/pixel/`WeatherHost.kt` (`rememberPlace`, `rememberWeather`, `weatherNoteText`) | new |
| J/`DebugLinks.kt`, T/`DebugLinksTest.kt` | modified for the WHOLE half: the only owner; every extra of 3.9 is written here (the other areas' lines are in 5.2) |
| R/values/`strings_weather.xml` (`wx_`) | new |
| tools/places/`make_places.py`; A/places/`zones.tsv`, `cities.tsv` (DATA) | `force-include.txt` dropped (#R10) |
| T/resources/weather/`locationforecast-sample.json`; T/data/`PlacesTest`, `WeatherParseTest`, `WeatherPinTest`, `WeatherGuardTest`, `WeatherPolicyTest`, `WeatherEngineTest`, `WeatherIsolationTest`; T/ui/pixel/stage/`WeatherLookTest`, `WeatherParticlesTest`, `WeatherLayersTest`, `WeatherStillsDump` (env `WEATHER_STILLS_DIR`) | |

### 2.3 FIRE (stage 1)

| Files | Notes |
|---|---|
| J/service/`FireMath.kt`, `FireFeed.kt`, `FireDebug.kt` | new, pure |
| stage/`FireSpec.kt`, `FireGeometry.kt`, `FireLayer.kt` (class `FireLayerImpl`), `FireEdge.kt`, `FireSparks.kt`, `FireWind.kt`, `FireHazeShader.kt` | new, pure; `Fire.kt` (old composables) untouched |
| J/ui/pixel/`FireHost.kt`, `FireTiltSensor.kt`, `FireHaze.kt` | new; `FirePrefs.kt` dropped (#R4) |
| J/ui/settings/`FireSettings.kt` (`FireSettingsRows()`: the tilt row only) | new |
| J/data/`ChatEngine.kt` | modified: the four lines of FIRE 3.1, nothing else |
| R/values/`strings_fire.xml` (`fire_`: `fire_tilt_title`, `fire_tilt_sub` only) | new |
| T/service/`FireMathTest`, `FireFeedCoreTest`, `FireDebugTest`; T/data/`ChatEngineFireFeedTest`; T/ui/pixel/stage/`FireResolveTest`, `FireClassicParityTest` (+ its `LegacyFireReference`), `FireEdgeTest`, `FireWindTest`, `FireSparksTest`, `FireLayerTest`, `FireGeometryTest`, `FireTiltTest`, `FireHazeTest`, `FireCoreSpritesPinnedTest`, `FireLayerCostTest`, `FireTasteSheetDump` (env `FIRE_SHEET_DIR`) | `T/resources/fire/core-sprites.golden` is written by INTEGRATE (2.9) |

### 2.4 PET (stage 1)

| Files | Notes |
|---|---|
| J/ui/pixel/pet/`PetAnim.kt` (`PetAnim`, `PetSet`), `PetWorld.kt`, `PetBrain.kt`, `PetLayer.kt` (`PetLayer`, `PetPlacement`), `PetTaps.kt`, `PetDebug.kt`, `PetHost.kt`, `PetPeek.kt` | new; the five pure ones import no `android.`, `androidx.`, `java.awt.` |
| J/ui/components/`PetNote.kt` | modified (swipe, rise, `pet_head` face with fallback) |
| R/values/`strings_pet.xml` (`pet_`) | new |
| T/ui/pixel/pet/`PetBrainTest`, `PetBrainIdleTest`, `PetLayerTest`, `PetPlacementTest`, `PetTapsTest`, `PetQuipsTest`, `PetDebugTest`, `PetCoreIsPureTest`; T/ui/components/`PetNoteSwipeTest` | PET's `PetArtTest` is dropped (ART-PET owns the art tests) |

### 2.5 ART-PET (stage 1)

| Files | Notes |
|---|---|
| tools/art/`pet_clips.py` | new generator |
| A/pixel/`mascot_anim.pxl` | generated by DATA from the script (never edited by hand); `mascot.pxl` untouched |
| J/ui/pixel/`PetArt.kt` (`PetArt`, `PetClip`, `PetClipSpec`, `PetClipKind`) | new, pure |
| T/ui/pixel/`PetArtTest.kt` | new |

### 2.6 ART-WORLD (stage 1)

| Files | Notes |
|---|---|
| tools/art/`craft_art.py` (new builders only; `build_scene_pxl`, `build_fire_pxl`, `build_appicon_pxl` not touched; also runs `pet_clips.py`), `tools/art/README.md` (also the rows of ART-PET and of the scripts of 7.1) | `contact_sheet.py`, `pxl.py` untouched (`--tint-file` cut) |
| A/pixel/`scene_seasons.pxl`, `scene_props.pxl`, `illustrations.pxl` | generated by DATA; `scene.pxl` regenerates byte-identical (no header lines, no `D`) |
| stage/`SceneSprites.kt` (`SceneSprites`, `PropLayer`, `ScenePropSpec`, `SceneProps`); J/ui/pixel/`Illustrations.kt` | new, pure |
| T/ui/pixel/stage/`ArtWorldAssetsTest`, `SceneSpritesTest`; T/ui/pixel/`IllustrationsTest` | `SceneRoles*` dropped |

### 2.7 HOME (stage 1)

| Files | Notes |
|---|---|
| J/ui/home/`HomeMetrics.kt`, `HomeUi.kt`, `HomeSuggestions.kt`, `FireSwitch.kt` (`FireSwitch`, `FireAction`, `FireGesture`, `FireRing`), `Woodpile.kt`, `HomeEntrance.kt`, `HomeDebug.kt` (woodpile pin), `HomeTags.kt`, `HomeRow.kt`, `rows/HomeRows.kt`, `HomeHero.kt`, `HomeStatus.kt`, `HomeAsk.kt`, `HomeLists.kt` | new; `HomeChrome.kt` and `HearthTaps` dropped (#R6, #R7) |
| J/ui/home/`HomeScreen.kt` (rewritten, `fun HomeScreen()` unchanged), `HomeParts.kt` (`HomeTile` gains `art`), | modified; `HomeScrim.kt` untouched |
| R/values/`strings_home.xml` (`hm_`) | new |
| T/ui/home/`HomeSuggestionsTest`, `HomeMetricsTest`, `FireSwitchTest`, `WoodpileTest`, `HomeUiTest`, `HomeRowRegistryTest`, `HomeEntranceTest`, `HomeTagsTest` | `HearthTapsTest` dropped; `NotificationGroupsTest` stays green |

### 2.8 APPEAR (stage 1)

| Files | Notes |
|---|---|
| J/ui/theme/`BlackNight.kt`, `AppearancePin.kt`; J/ui/theme/`Appearance.kt`, `PocketTheme.kt` (the `remember` that builds `colors`, nothing else) | |
| J/ui/settings/`AppearanceScene.kt` (rows, `PlacePickerSheet`, `AppearanceTags`), `AppearanceSection.kt` (anchors of APPEAR 3.5) | the place rows and the city sheet are APPEAR's (lead); `PlaceWeatherSettings.kt` of WEATHER is dropped |
| J/voice/`SceneSounds.kt`; J/voice/`SoundEngine.kt` | |
| R/values/`strings_appearance.xml` (`apr_`); R/raw/`sfx_hearth_wake.wav`, `sfx_hearth_sleep.wav`, `sfx_studio_wake.wav`, `sfx_studio_sleep.wav`, `sfx_loop_rain.wav`, `sfx_loop_crackle.wav` (DATA) | `keep.xml` untouched |
| tools/art/`make_sounds.py` (lead: not `tools/sounds/`) | |
| T/ui/theme/`AppearanceModelTest`, `AppearancePinTest`, `BlackNightTest`; T/voice/`SoundCuesTest`, `SoundFilesTest` | `AppearanceMigrationTest` dropped (#R4) |

### 2.9 Later stages and shared files

| Files | Owner, stage |
|---|---|
| A/sky/`stars.bin`, `stars.source.txt`; A/places/`zones.tsv`, `cities.tsv`; R/raw/six `sfx_*.wav`; A/pixel/`mascot_anim.pxl`, `scene_seasons.pxl`, `scene_props.pxl`, `illustrations.pxl` (and `scene.pxl`, unchanged); `docs/waves/w1/look/*.png`, `docs/waves/w1/look/review-<AREA>.txt` (contact sheets and verdicts) | DATA writes the first four groups (2), LOOK writes the verdicts and edits only per 7.2 (3) |
| `R/values-tr/` and `R/values-fr/`: `strings_sky.xml`, `strings_weather.xml`, `strings_fire.xml`, `strings_pet.xml`, `strings_home.xml`, `strings_appearance.xml`; `app/src/debug/res/values-en-rXZ/` (regenerated) | TRANSLATE (4) |
| J/ui/`AppRoot.kt` (two lines, 7.5); `app/build.gradle.kts` (two lines, 7.5); `NOTICE` (7.5); `.github/workflows/build.yml` (7.5); `tools/size-budget.txt`; T/`ScenarioFilesTest.kt` (known tags, 3.11); `app/src/test/resources/fire/core-sprites.golden`; `docs/waves/w1/look/fire-*.png`, `scene-*.png`, `weather-*.png` | INTEGRATE (5) |
| `tools/scenarios/w1a-*.json` (scenario files of section 8) | the lead's robot pass after INTEGRATE (not a build agent; `"safe": true` only) |
| `AGENTS.md`, `PROGRESS.md`, `docs/phase4-plan.md` (plan 1.2 text "about 900 stars" becomes "800 to 1000"; plan 1.5 "eight empty pictures" stays), `docs/APP_OVERVIEW.md`, `docs/design-language.md` | the lead |

### 2.10 Nobody edits (even where a draft asked)

`J/ui/home/HomeScrim.kt` (pinned by `NotificationGroupsTest`, #R6), `J/data/BackupManager.kt` (`PREF_SCHEMA` untouched; follow-up 9), `J/ui/pixel/Mascot.kt`, `Avatars.kt`, `Fire.kt`, `A/pixel/fire.pxl`, `A/pixel/mascot.pxl`, `A/pixel/scene.pxl`, `A/pixel/appicon.pxl`, `A/pixel/avatars.pxl`, `A/pixel/icons.pxl`, `R/raw/keep.xml`, `R/values/strings_chat.xml` (so `ui_home_mascot_cd` keeps saying "salamander", unused: HOME uses `hm_pet_cd`), `J/ui/nav/*`, `J/DebugOverrides.kt`, `AndroidManifest.xml`, `runtime/*`, `rootfs/*`, `T/ui/pixel/stage/LegacyHearthReference.kt`, `SceneRendererParityTest.kt`, `StageCoreIsPureTest.kt`, `T/ui/pixel/PxlParserTest.kt`.

### 2.11 Where two drafts claimed one file (decided; the loser's need is in section 4)

| File or thing | Claimants | Owner (rule used) |
|---|---|---|
| `HearthScene.kt` | SCENE, FIRE (10), PET, HOME | SCENE (lead table) |
| `DebugLinks.kt` and its test | WEATHER, SCENE, FIRE, PET, APPEAR, ART-WORLD | WEATHER (lead table) |
| place rows and city sheet | WEATHER (`PlaceWeatherSettings.kt`), APPEAR | APPEAR (lead) |
| `AppRoot.kt`, `NOTICE`, `build.gradle.kts` | SCENE, HOME, WEATHER, ART-WORLD, APPEAR ask "LEAD" | INTEGRATE (lead table) |
| sound script | APPEAR `tools/sounds/`, lead `tools/art/` | `tools/art/make_sounds.py` (lead) |
| new Pêt frames | PET `mascot_life.pxl`, ART-PET `mascot_anim.pxl`, plan `mascot.pxl` | `mascot_anim.pxl` (lead) |
| `PetArt` | PET (object in `pet`), ART-PET (object in `ui/pixel`) | ART-PET keeps `PetArt`; PET's helper is `PetSet` (#R9) |
| `FireLayer` | SCENE (interface), FIRE (class) | interface `FireLayer` in `SceneLayers.kt` (SCENE); class `FireLayerImpl` (FIRE) |
| `PetLayer` | SCENE (interface), PET (class) | class `PetLayer` (PET); SCENE has no interface |
| the days store | SCENE `SceneDaysStore`, APPEAR `Appearance.days` | `SceneDaysStore` (SCENE) (#R4) |
| `SkyMode` | SCENE (`FOLLOW`), APPEAR (`REAL`) | SCENE's, `FOLLOW` (#R3) |
| classic fire setting | FIRE `FirePrefs`, APPEAR `Appearance.classicFire` | `Appearance.classicFire` (lead) |
| tap on Pêt chain | HOME `HearthTaps`, PET `PetTaps` | `PetTaps` (#R7) |
| status-bar icon polarity | SCENE `HomeSky`, HOME `HomeChrome` | `HomeSky.iconsDark` (#R6) |
| geometry | SCENE `SceneGeometry`, FIRE `FireGeometry`, WEATHER `WeatherGeometry`, HOME `SceneAnchors`, PET `PetPlacement` | `SceneGeometry` is the one source; the others are thin views (3.3) |
| `findOrNull` / `find` | ART-WORLD, SCENE | `SpriteSource.find` (SCENE) |

### 2.12 Dropped files and symbols (a build agent must not create them)

`J/ui/settings/PlaceWeatherSettings.kt`, `WeatherTags` (WEATHER); `tools/places/force-include.txt`; `J/ui/pixel/FirePrefs.kt`, `FireTags.LOG`'s own file, `FireDriver`'s `FirePrefs` use (FIRE); `PetArt` of the `pet` package, `PetArt.NOT_SHIPPED`, `pet_mini`, `mascot_life.pxl`, `PetSignals` (PET, HOME); `HomeChrome`, `HearthTaps`, `SceneAnchors`, `sceneAnchors()`, `FireRing.embers` use (HOME); `SceneRoles`, `SceneFamily`, `scene_sky.pxl`, `scene_moon_phases`, `scene_sun`, `scene_cloud_*`, `previews/*.json`, `--tint-file`, `moonFrame` (ART-WORLD, #R11); `SceneInput`, `FireLayers`, `PetLayers`, `rememberFireInput`, `rememberPetInput`, the `PetLayer` interface (SCENE, #R2); `DayLists`, `isExistingInstall`, `initialDays`, the `daysInit` marker, `Appearance.days`, `SkyMode.REAL`, `apr_sky_real|night|day` (APPEAR); `tools/sounds/` (APPEAR).

## 3. Shared-symbol registry

One form per symbol. Where drafts disagreed the loser is named in section 4 and the consequence is in section 5. "cuttable" = may be an empty body or `null` when the area runs out of time (section 9); every other symbol must exist. Package of a symbol is the folder it sits in (`stage` = `app.ui.pixel.stage`, `pixel` = `app.ui.pixel`, `pet` = `app.ui.pixel.pet`, `home` = `app.ui.home`, `theme` = `app.ui.theme`, `data` = `app.data`, `service` = `app.service`, `voice` = `app.voice`, `settings` = `app.ui.settings`, `components` = `app.ui.components`).

### 3.0 The rulings every agent must know

| # | Ruling |
|---|---|
| R1 | **The parity oracle stays** (`SceneRendererParityTest`, `LegacyHearthReference`, unchanged). Every new `SceneState` field defaults to "off"; with all defaults the output is today's picture pixel for pixel. HOME's and APPEAR's request to retire it is rejected: the production path adds fields, it does not change the classic statements. |
| R2 | **No marker interfaces.** `SceneState` carries the real types: `fire: FireParams`, `pet: PetPose?`, `weather: WeatherLook`, `sky: SkyFrame?`. `SceneInput`, `FireLayers`, `PetLayers`, `rememberFireInput`, `rememberPetInput` do not exist. SCENE's renderer owns three collaborators: `FireLayer` (interface, `FireLayerImpl` by FIRE, which also contains today's classic fire code moved verbatim), `PetLayer` (PET's class), `WeatherLayers` (WEATHER's class). |
| R3 | `SkyMode { FOLLOW, NIGHT, DAY }` (SCENE, `stage`), stored by enum name, pin tokens `follow\|night\|day`. "Always night" is today's picture (sky frame `null`, today's 46 stars, today's moon, no dithered horizon). |
| R4 | **One days store, one classic switch, no marker.** `SceneDaysStore` (SCENE, prefs `scene_days`) holds lists, seasons and user days and does the one migration (existing install = `files/rootfs.stamp` exists or `files/chat/` has any file, lead rule). APPEAR has no `days`, no `DayLists`, no `daysInit`. `Appearance.classicFire` is the only fire setting; `FirePrefs` does not exist; the tilt switch is `Appearance.tiltFire` (default true), its row is FIRE's `FireSettingsRows()`. |
| R5 | **One geometry**: `SceneGeometry` (3.3). `FireGeometry`, `PetPlacement`, `WeatherGeometry` are views built from it; nobody computes `(width * 0.6f).toInt()`, `height - 3` or `height - 34` outside `SceneGeometry.of` and the classic statements that LegacyHearthReference mirrors. |
| R6 | The status-bar icon polarity is `HomeSky.iconsDark` (SCENE); `HomeChrome` does not exist; `HomeScrim.kt` is untouched and `HomeStatusScrim` stays dead code; the pinned strip of HOME covers the status bar. |
| R7 | Pêt's tap chain is `PetTaps` only (7 taps in chains of at most 1200 ms; only taps on Pêt count). The fire's gestures are HOME's `FireSwitch` (tap 300 ms, hold 700 ms, ring grace 150 ms); the hold ring is drawn by FIRE inside the scene (20 dots) from `fireHold`. `SoundCue.WAKE` and `SLEEP` are played only by APPEAR's `SceneSounds` on hearth changes; HOME plays haptics and the tap sounds `SPARK` and `FORGE` as today. |
| R8 | **The tint mechanism is SCENE's `SkyTint`** (blend of whole sprites towards a colour). The role table (`SceneRoles`) is cut. Sun and moon are drawn procedurally (`SkyBodies`). Season looks are procedural overlays (core); ART-WORLD's `scene_far_<season>` and `scene_ground_<season>` replace the procedural result only when present AND the look review kept them (deleting a sprite from the generated file is the whole "cut"). |
| R9 | Pêt art follows ART-PET: file `mascot_anim.pxl`, the sprite table of 3.10, `PetArt` (`pixel`) with `PetClip`; PET's `PetAnim` frame counts and times are those of that table; PET's availability helper is `PetSet`; the shadow has four levels (0 to 3, 48 by 5); the small Pêt for top bars is `pet_peek` (16 by 10), the note face is `pet_head` (16 by 16, four faces); outros of LOOP clips are not played in this half (extra, 9). |
| R10 | The city list is Natural Earth populated places by population plus capitals, no hand-made list; `PlaceRows` and the city sheet are APPEAR's code written against WEATHER's `PlaceStore`. |
| R11 | ART-WORLD cut from this half: role table, `scene_sky.pxl` (moon phases, sun), `scene_cloud_*`, `--tint-file`, previews, `D` crowns, header lines in `scene.pxl`. `scene.pxl` regenerates byte-identical. Clouds are WEATHER's procedural ones only. |
| R12 | All debug-link extras are written by WEATHER in `DebugLinks.kt` (3.9). `theme` and `dark` are extras that feed the appearance pin; `DebugOverrides.kt` and `AppRoot.kt` get no theme code (the pinned `AppearanceStore` flow already reaches `PocketTheme`). |
| R13 | Sounds: `tools/art/make_sounds.py` writes six WAV files (16 kHz mono, at most 300 KB together); DATA runs it and commits the files; a cue without a file is silent. No other sound is added or downloaded. |
| R14 | The taste defaults are the lead's: sky follows the real day; living fire and sparks on, "Classic fire" gives today's back; weather dark; cultural days all off (an existing install keeps Newroz); sky's own days always on. Only days with a fixed date or a date computed from the sky ship: **Lunar New Year and Diwali (the `dates:` tables), `DayEffect.LANTERNS` and `SkyFrame.lanterns` are not built** (lead: tables of uncertain dates wait for Wave 12). |

### 3.1 SCENE: state, renderer, hooks (`stage`)

```kotlin
data class SceneState(
    val hearth: HearthState, val palette: ScenePalette, val progress: Float? = null, val mood: MascotMood? = null,
    val stokeBoost: Float = 0f, val celebration: Boolean = false,                      // the six existing, same order
    val sky: SkyFrame? = null,                 // null = classic night (today)
    val weather: WeatherLook = WeatherLook.CLEAR,
    val pageColor: Int = 0,                    // 0 = no dithered horizon; else the opaque page colour (Pocket.colors.bg)
    val fire: FireParams = FireParams.CLASSIC,
    val pet: PetPose? = null,                  // null = today's pose code
    val collapse: Float = 0f,                  // 0..1, already quantised to 1/32 by the caller (HomeMetrics.quantize)
    val reveal: Float = 1f,                    // 0..1, 1 = no entrance effect
    val bottomBandRows: Int = 0,               // logical rows of ground-only band below the hearth
    val woodpile: Int? = null,                 // frame 0..5 of scene_woodpile, null = not drawn
    val nightFires: Boolean = true,            // true = today's three ridge fires in the classic (sky == null) path; HearthScene sets it from "newroz" in the days lists
)
fun interface SpriteSource { operator fun get(name: String): Sprite; fun find(name: String): Sprite? = try { get(name) } catch (e: IllegalArgumentException) { null } }
class SceneRenderer(sprites: SpriteSource, stars: StarTable = StarTable.EMPTY, fire: FireLayer = FireLayerImpl(sprites),
                    pet: PetLayer = PetLayer(sprites), weather: WeatherLayers = WeatherLayers()) {
    fun render(stage: PixelStage, state: SceneState, timeMs: Long, quality: SceneQuality = SceneQuality.FULL)
}
interface FireLayer {                          // SceneLayers.kt; implemented by FireLayerImpl (FIRE)
    fun glow(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)     // step 9
    fun flame(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)    // step 11: sprite, living edge, wind lean, hold ring
    fun sparks(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)   // step 11c, after SCENE's smoke; classic = today's embers
}
```
`REQUIRED_SPRITES` stays the 17 of today; every new sprite is fetched with `find` and may be absent. `SceneRenderer(SpriteBank)` and the tests' one-argument form keep compiling. `FireLayerImpl.glow/flame/sparks` read `state.fire`, `state.hearth`, `state.palette`, `state.progress`, `state.stokeBoost`, `state.celebration`, `state.weather.wind`; with `state.fire.classic` they draw exactly the statements of today's `SceneRenderer` (moved verbatim).

### 3.2 SCENE: sky, days, host (`stage` and `pixel`)

| Symbol | Exact form | Consumers, behaviour |
|---|---|---|
| `SkyMode`, `Season`, `SeasonSetting`, `SkyMoment`, `BodyPos(altDeg: Float, azDeg: Float)` | enums and class as SCENE 3.1 | APPEAR (`SkyMode`, `SeasonSetting`), ART-WORLD (`Season`), PET (`sun`) |
| `SkyFrame` | class as SCENE 3.1, identity equality. **Fields read outside SCENE:** `sun.altDeg: Float`, `daylight: Float` (0 night, 1 day), `moment: SkyMoment`, `season: Season?`, `petHat: Boolean`, `skyTop: Int`, `iconsDark: Boolean`, `facingAz: Float`, `debugLine(): String` | PET (via HearthScene), HOME (via `HearthInfo`) |
| `SkyClock`, `SkyMoments`, `SkyPalette`, `SkyFrames.build(SkyInputs): SkyFrame?`, `SkyProjection`, `StarTable`, `StarLayer`, `SkyBodies`, `Seasons`, `SceneCalendar`, `SkyTint` | as SCENE 3.2 to 3.7 (formulas and tables are SCENE's) | inside SCENE |
| `SkyTint` additions (amendment 5.1) | `fun SkyTint.dimmed(sprite: Sprite, frame: Int, step16: Int): IntArray` (rgb lerped to black by `0.45 * step16 / 16`, alpha kept, cached per (sprite, frame, step), step 0 returns `SpriteRaster.argb`) | props, woodpile: step = `round((1 - daylight) * 16)` |
| `SceneCalendar.LISTS: List<DayList>`, `class DayList(val id: String, val group: DayGroup, val defaultOn: Boolean)`, `enum DayGroup { SKY, APP, CULTURE }` | as SCENE 3.5; ids today: `sky app newroz midsummer newyear bonfirenight` (no `lunarnewyear`, no `diwali`: lists that need a table of uncertain dates wait for Wave 12, lead) | APPEAR rows |
| `object DayListTexts { @StringRes fun title(id: String): Int; @StringRes fun sub(id: String): Int }` in `SkyHost.kt` | `0` for an unknown id; keys `sky_list_<id>` and `sky_list_<id>_sub` | APPEAR (skips a list whose title is 0) |
| `SceneDaysStore.get(context)` | `lists: StateFlow<Set<String>>`, `seasons: StateFlow<SeasonSetting>`, `userDays: StateFlow<List<UserDay>>`, `setList(id, on)`, `setSeasons(s)`, `addUserDay(month, day, name): Boolean`, `removeUserDay(index)`; prefs `scene_days` (6) | APPEAR rows; `rememberSkyFrame`; HearthScene |
| `SkyPins` | `mode(): SkyMode?`, `lists(): Set<String>?`, `setMode(SkyMode?)`, `setLists(Set<String>?)`, `clear()`, `parseMode(text): SkyMode?`, `parseLists(text): Set<String>?`; inert unless `TEST_HOOKS`; pin beats store | WEATHER's `DebugLinks` |
| `HomeSky` (object in `pixel`) | `frame: StateFlow<SkyFrame?>`, `skyTop: StateFlow<Int>` (0 until known; classic: `hearthSkyTop` ARGB), `iconsDark: StateFlow<Boolean>` (luma of `skyTop` >= 0.55; false when classic) | INTEGRATE's `AppRoot` line, `rememberHearthInfo` |
| `@Composable fun rememberSkyFrame(): State<SkyFrame?>`, `rememberStarTable(): StarTable` | as SCENE 3.7; the frame is read only inside draw lambdas | HearthScene |
| `@Immutable class HearthInfo(val topArgb: Int, val bandArgb: Int, val daylight: Float, val skyLabel: String = "", val skyNoteAt: (xFrac: Float, yFrac: Float) -> String? = { _, _ -> null })`; `@Composable fun rememberHearthInfo(): HearthInfo` | HOME (hero ink, strip, sky tap, spoken label). `topArgb` = `HomeSky.skyTop`; `bandArgb` = colour of the tinted ground at (`hearthX`, last ground row), `0xFF1D2A26` before the sprites are ready; `daylight` 0 when classic; `skyLabel` = `t(sky_scene_cd, momentName, moonName)` or `""` when classic; `skyNoteAt` = the Pêt-note text of `skyBodyAt` for a named star, planet, the moon or the sun (E1), else null. Recomputed once a minute and when mode, place, theme or lists change |

**Tint table (R8; replaces the role table).** `SkyTint.tinted(sprite, frame, tint, step16)` keeps each pixel's alpha and blends its rgb towards `tint` by `base * lift` (`lift` quantised to 1/16, cached per sprite, frame and step; step 0 returns the baked raster, so the classic night is pixel-identical). Art is baked as follows and must not be redrawn for the day:

| Layer | Sprite (baked at) | Method | Tint colour | Base amount |
|---|---|---|---|---|
| far ridge | `scene_far`, `scene_far_<season>` (night) | `farRamp` of the frame (today's ramp at lift 0) | from `SkyFrame.farRamp` | frame |
| near ridge | `scene_near` (night) | `tinted` | `2F4A44` | 0.35 |
| ground | `scene_ground`, `scene_ground_<season>` (night) | `tinted` | `56684A` | 0.50 |
| foreground grass | `scene_fore` (night) | `tinted` | `3A4A34` | 0.50 |
| props, woodpile | `prop_*`, `scene_woodpile` (**daylight**) | `dimmed` | black | `0.45 * (1 - daylight)` |
| sun, moon, stars, meteors, clouds, rain | drawn by code | own colours (SCENE 3.3 to 3.5, WEATHER 3.5) | | |
| tiles, empties, Pêt, fire | not tinted | | | |

Season sprites of ART-WORLD are baked in the same night look as the base sprites, so the same tint makes them match.

### 3.3 Geometry (`stage`, `SceneLayers.kt`; the one source, R5)

```kotlin
class SceneGeometry private constructor(
    val width: Int, val height: Int, val bandRows: Int,                         // height = the whole stage, band included
    val groundTop: Int, val farRidgeTop: Int, val nearRidgeTop: Int, val foreTop: Int,
    val hearthX: Int, val hearthBase: Int, val artX: Int, val horizonRow: Int,
    val landDy: Int, val ridgeDy: Int, val skyDy: Int,                          // rows the land, the ridges and the sky are moved down by collapse and entrance
    val woodpileX: Int, val woodpileY: Int, val showProps: Boolean,
) {
    companion object {
        const val GROUND_H = 34; const val FIRE_FOOTPRINT_ROWS = 38; const val ENTER_ROWS = 48
        fun of(width: Int, height: Int, bandRows: Int = 0, collapse: Float = 0f, reveal: Float = 1f): SceneGeometry
    }
}
```
Formulas (integers, `q = collapse.coerceIn(0, 1)`): `g0 = height - 34 - bandRows`; `cDy = round(q * bandRows)`; `ridgeC = max(round(0.6 * cDy), cDy - 10)`; `enter = round((1 - smoothstep(0, 0.8, reveal)) * 48)`; `landDy = cDy + enter`; `ridgeDy = ridgeC + enter`; `skyDy = round(0.35 * cDy)`; `groundTop = g0 + landDy`; `farRidgeTop = g0 - 38 + ridgeDy`; `nearRidgeTop = g0 - 14 + ridgeDy`; `foreTop = height - 11 - bandRows + landDy`; `hearthX = (width * 0.6f).toInt()`; `hearthBase = height - 3 - bandRows + landDy`; `artX = (width - 176) / 2`; `horizonRow = g0 - 24 + skyDy`; `woodpileX = hearthX + 18`; `woodpileY = hearthBase - 15` (28 by 16 sprite, bottom row on the feet line, centre at `hearthX + 32`, outside the fire's 24 by 30 hit box and its 32 px sprite); `showProps = q < 0.75`. With `bandRows = 0, collapse = 0, reveal = 1` every field equals today's constant (`SceneGeometryTest`). Rows from `height - bandRows + landDy` to `height - 1` are filled by repeating the row above (the ground band); when `landDy >= bandRows` there is no fill. Pêt, woodpile and `precipNear` splashes are not drawn when `!showProps`.

Views (thin, tested equal to `SceneGeometry`): `class FireGeometry(val geo: SceneGeometry) { val fx get() = geo.hearthX; val base get() = geo.hearthBase; val spriteX = fx - 16; val spriteY = base - 35; fun hitRect(grid: StageGrid, minPx: Int): FireHit }`; `PetPlacement.feetX(geo) = geo.hearthX - 27`, `feetY(geo) = geo.hearthBase`; `WeatherGeometry(width, height, groundTop, hearthX, hearthBase, farRidgeTop = groundTop - 38, nearRidgeTop = groundTop - 14)` built by SCENE with the real ridge tops.

### 3.4 FIRE (`service` and `stage` and `pixel`)

| Symbol | Exact form | Notes |
|---|---|---|
| `FireFeedState`, `FireMath`, `FireFeedCore`, `FireFeed` | as FIRE 3.1; **plus** `FireFeed.toolsRunning: StateFlow<Int>` (tool keys in the state, never negative; updated in the same CAS as `tools`) | PET reads `toolsRunning`; PET, APPEAR (crackle level, via SceneSounds in 1b) read `level()` |
| `FireParams(classic: Boolean = true, level: Float = 0f, wind: Float = 0f, holdProgress: Float = 0f, transition: FireTransition? = null)`, `FireParams.CLASSIC`, `FireTransition`, `FireSprite`, `FireSpec`, `FireTuning`, `FireResolve` | as FIRE 3.2 with `FireTuning.HOLD_MS = 700L` (#R7) and the one addition `FireResolve.intensityOf(hearth, progress, stokeBoost, celebration, fire, timeMs): Float` (the `FireSpec.intensity` of that call) | `intensityOf` feeds `PetWorld.fireLevel` |
| `FireLayerImpl(sprites: SpriteSource) : FireLayer` | behaviour of FIRE 3.3 (classic verbatim, living edge, sparks, ring); `weatherWind` is `state.weather.wind` | SCENE |
| `FireGeometry(geo)`, `FireHit(left, top, right, bottom)` | 3.3 | HOME (hit box) |
| `object FireTags { const val HIT = "home.fire" }` | one tag, HOME writes it (#R7) | robot |
| `@Composable fun rememberFireDriver(hearth: HearthState, clock: LongState, hold: () -> Float = { 0f }, onTransition: (FireTransition.Kind) -> Unit = {}): FireDriver`; `FireDriver.params(sceneMs: Long): FireParams` (draw lambda only); `hazeActive: Boolean`; `@Composable fun Modifier.fireHaze(driver: FireDriver, sceneBottomAnchored: Boolean = true): Modifier` | HearthScene calls them. The driver reads `Pocket.appearance.classicFire` and `.tiltFire` in composition, never in a draw lambda; the `fire` pin overrides |
| `FireDebugPin`, `FireDebug` | as FIRE 3.5 | `fire` extra |
| `FireSettingsRows()` (`settings`) | the tilt row only: `PocketSwitchRow(fire_tilt_title, Appearance.tiltFire, ..., fire_tilt_sub)`, tag `appearance.tilt`; composed only when `!classicFire` | APPEAR calls it after its classic row |

### 3.5 PET (`pet` and `components`)

```kotlin
enum class PetAnim(val sprite: String, val frames: Int, val durationMs: Int, val loops: Boolean, val fallback: PetAnim?) {   // numbers = the sprite table of 3.10
    IDLE("pet_idle", 8, 2860, true, null), THINK("pet_thinking", 7, 1650, true, IDLE), TALK("pet_talking", 8, 880, true, IDLE), HAPPY("pet_happy", 9, 1300, false, IDLE),
    SLEEP("pet_sleeping", 6, 3500, true, IDLE), ERROR("pet_error", 6, 1380, true, IDLE), WAVE("pet_waving", 8, 1220, false, IDLE),
    BLINK("pet_blink", 4, 290, false, IDLE), TAIL("pet_tail", 6, 600, false, IDLE), WARM("pet_warm", 7, 1620, false, IDLE), YAWN("pet_yawn", 6, 1420, false, IDLE),
    STRETCH("pet_stretch", 6, 1370, false, IDLE), FIREFLY("pet_firefly", 8, 1180, false, IDLE), POKE("pet_poke", 8, 810, false, IDLE),
    READ("pet_read", 6, 1400, true, THINK), WRITE("pet_write", 7, 1080, false, IDLE), CARRY("pet_carry", 6, 1400, true, IDLE), STARTLED("pet_startled", 6, 560, false, IDLE),
    DANCE("pet_dance", 8, 850, true, HAPPY), LISTEN("pet_listen", 6, 1480, true, THINK), SHELTER("pet_shelter", 6, 960, true, IDLE), SNOW("pet_snow", 8, 1250, false, IDLE);
    val isClassic: Boolean
}
object PetSet { val CLASSIC: Set<PetAnim>; fun availableIn(find: (String) -> Sprite?): Set<PetAnim> }   // CLASSIC + every new anim whose PetArt.clip(sprite, find) != null
data class PetPose(val anim: PetAnim, val startMs: Long, val shadow: Int = 1, val holdFrame: Int = -1)      // shadow -1 none, 0..3 level of pet_shadow
```
`PetWorld`, `PetFire`, `PetEvent`, `PetBrain` (`post`, `setAvailable`, `step`), `PetTaps`, `PetQuip(s)`, `PetTapResult`, `PetDebug`, `PetPin`, `PetController`, `rememberPetController()`, `PetPeek`: as PET 3.1 to 3.7 with these changes: `PetBrain.IDLE_CYCLE_MS` stays 2860 (the idle sprite), the idle gap stays 2 or 3 cycles; the shadow gauge has four levels, up thresholds 0.15, 0.45, 0.80 of `fireLevel`, down thresholds 0.10, 0.38, 0.72, at most one change per 500 ms; `PetLayer(sprites: SpriteSource)` with `fun draw(stage: PixelStage, pose: PetPose, feetX: Int, feetY: Int, timeMs: Long)` and `val available: Set<PetAnim>`; the shadow is blitted first at `(feetX - 34, feetY - 2)` (= Pêt's origin `(feetX - 16, feetY - 31)` plus `(-18, +29)`), alpha 1 (the sprite carries its own alpha), skipped when `shadow < 0` or the sprite is missing; frames: non-loop anims `PetArt.clip(...).frameAtShot(t - startMs)` clamped to the last frame; loop anims of `PetClipKind.LOOP` use `frameAtLoop`; loop anims of kind SHOT (`DANCE`) and the classic loops use `Sprite.frameAt(t - startMs)`; `holdFrame >= 0` wins. `PetPlacement`: `feetX(geo)`, `feetY(geo)`, `hitRect(grid: StageGrid, geo: SceneGeometry, minPx: Float = 0f): FloatArray`, `hits(xPx, yPx, grid, geo, minPx): Boolean` (the box grown 3 stage pixels left, top, bottom, not right). `PetWorld.fireLevel` is `FireResolve.intensityOf(...)` (SCENE fills it). `PetNoteSwipe` as PET 3.8.

### 3.6 WEATHER (`data`, `stage`, `pixel`)

As WEATHER 3.1 to 3.5 and 3.4 with these exact changes: no `PlaceWeatherSettings.kt`, no `WeatherTags`; `WeatherGeometry` takes the two ridge tops (3.3); `WeatherLayers` method order and arguments unchanged; `WeatherView(point, look)`; `rememberWeather()`, `rememberPlace()`, `weatherNoteText(point)`, `WeatherStore.available/enabled/setEnabled/pin/current`, `PlaceStore.choice/place/table/search/setAuto/setCity/refreshZone/pinPlace`, `Place`, `City`, `PlaceChoice`, `WeatherLook`, `WeatherWind`, `WeatherPin.parse` as drafted. `WeatherLook.rainLevel` (0, 1, 2) feeds `SceneSounds` and `PetWorld.rain`; `PetWorld.snow` is 1 for `Precip.SNOW_LIGHT`, 2 for `SNOW_HEAVY`.

```kotlin
// WeatherLayers: every method returns at once for its part of the look being NONE; CLEAR draws nothing (SCENE calls them in the order of 5.1)
class WeatherLayers(seed: Long = 20261125L) {
    fun lightning(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, timeMs: Long, quality: SceneQuality)
    fun clouds(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun snowCaps(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, farEdge: IntArray?, nearEdge: IntArray?, artX: Int)
    fun fog(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun precipFar(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
    fun precipNear(stage: PixelStage, look: WeatherLook, geo: WeatherGeometry, colors: WeatherColors, timeMs: Long, quality: SceneQuality)
}
class WeatherColors(val daylight: Float, val skyBottom: Int)            // SCENE: frame.daylight and frame.skyBottom, or 0f and palette.skyBottom when classic
data class WeatherLook(val cloud: CloudCover = NONE, val precip: Precip = NONE, val fog: FogLevel = NONE, val wind: Float = 0f, val thunder: Boolean = false, val flash: Int = 0) // WeatherLook.CLEAR, rainLevel, starVisibility(), bodyVisibility()
```
SCENE passes `farEdge`, `nearEdge` from `WeatherArt.topEdges(pixels, 176, h)` computed when it builds its ridge caches and indexes them by `x - artX`.


### 3.7 APPEAR (`theme`, `voice`, `settings`)

```kotlin
data class Appearance(/* the ten existing fields, same order */ val sky: SkyMode = SkyMode.FOLLOW, val blackNight: Boolean = false, val classicFire: Boolean = false,
    val petInChats: Boolean = true, val rainSound: Boolean = true, val crackle: Boolean = false, val tiltFire: Boolean = true)
val Appearance.petInChatsVisible: Boolean get() = showMascot && petInChats
class AppearancePin(val change: (Appearance) -> Appearance) { companion object { fun parse(text: String): AppearancePin? } }   // null = any item bad
// AppearanceStore gains the members: fun pin(p: AppearancePin?)  and  val isPinned: Boolean      // memory only, TEST_HOOKS only; the flow shows p.change(stored)
fun PocketColors.withBlackNight(on: Boolean = true): PocketColors
enum class SoundCue { COMPLETE, SEND, TOOL, ERROR, APPROVAL, FORGE, MEMORY, SPARK, TAP, MIC, WAKE, SLEEP }   // two appended
enum class SoundLoop { RAIN, CRACKLE };  SoundEngine.setLoop(loop: SoundLoop, level: Float);  SoundEngine.stopLoops()
fun hearthCue(prev: HearthState?, next: HearthState): SoundCue?;  @Composable fun SceneSounds(hearth: HearthState, rainLevel: Int)
object AppearanceTags { /* strings of 3.11 */ }
```
Readers: SCENE `rememberSkyFrame` reads `sky` and `blackNight` through `Pocket.appearance` (composition) after `SkyPins.mode()`; `FireDriver` `classicFire`, `tiltFire`; `SceneSounds` `rainSound`, `crackle`; `PocketTheme` `blackNight`; PET in 1b `petInChatsVisible`. Pin items (comma separated, lower case, the whole value is rejected when one is bad, a repeated key: the last wins): `black:0|1`, `classic:0|1`, `pet:0|1`, `tilt:0|1`, `haptics:off|subtle|full`, `rain:0|1`, `crackle:0|1`, `theme:newroz|zagros|semender|nergiz|kawa|roj`, `dark:system|dark|light`, `anim:full|reduced|off`. (`sky:` and `days:` are not items: `sky` and `days` are extras, R12.) `AppearancePin.parse` is also what `theme=` and `dark=` extras use (3.9). Rows: the order and anchors of APPEAR 3.5 with these changes: sky chips use `sky_mode_follow|night|day` and tags `appearance.sky.follow|night|day`; the place row, the city sheet (`PlacePickerSheet`) and the weather row (composed only when `WeatherStore.available`) are APPEAR's code; seasons chips (`sky_season_*`, tags `appearance.seasons.<name>`) call `SceneDaysStore.setSeasons`; one switch row per `SceneCalendar.LISTS` entry with `group != SKY` and a non-zero `DayListTexts.title`, tag `appearance.days.<id>`, calling `SceneDaysStore.setList`; classic fire switch (tag `appearance.classicFire`), then `FireSettingsRows()`; the Pêt in chats row is written as a private composable and NOT composed in this half (nobody reads `petInChatsVisible` before 1b); user days (add and remove, `apr_userday_*`) are an extra.

### 3.8 HOME and the scene host (`pixel`, `home`)

```kotlin
@Composable fun HearthScene(
    state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null, stokeBoost: Float = 0f, celebration: Boolean = false,   // unchanged six
    collapse: () -> Float = { 0f }, reveal: () -> Float = { 1f }, bottomBand: Dp = 0.dp, woodpile: Int? = null,
    fireHold: () -> Float = { 0f }, onFireTransition: (FireTransition.Kind) -> Unit = {}, pet: PetController? = null,
)
```
The height comes from `modifier` (the forced `.height(280.dp)` is removed; HOME always passes a height); the stage is allocated once for the full size; `collapse` and `reveal` are read in the draw phase only; `bandRows = round(bottomBand.toPx() / grid.scale)`; `SceneState.collapse` gets the value quantised by `HomeMetrics.quantize`. The tag `stage.hearth` stays on the `Spacer`. HOME's `HomeMetrics`, `HomeUi`, `HomeSuggestions`, `FireSwitch`, `Woodpile`, `HomeEntrance`, `HomeRow(s)` are internal to HOME except: `HomeRows` and `HomeRow` (the registration point for later waves, HOME 3.8), `HomeDebug` (`val woodpile: StateFlow<Int?>`, `fun pinWoodpile(level: Int?)`, inert unless `TEST_HOOKS`), `HomeEntrance.reset()` (called by `DebugLinks` reset), `HomeTags` (3.11). HOME calls `SceneSounds(hearth, rememberWeather().look.rainLevel)` once, `rememberPetController()` once, passes `fireHold` (`FireRing.fraction(heldMs)` of its gesture) and `onFireTransition` (haptics only).

### 3.9 Debug link: every new extra (WEATHER writes all; `TEST_HOOKS` only; in memory; `reset=1` clears all)

| Extra | Value forms (a bad value goes to `rejected` and nothing is applied) | Applies through |
|---|---|---|
| `weather` | `off`; presets `clear storm drizzle blizzard mist`; or items `cloud:0..3 rain:light\|heavy snow:light\|heavy sleet fog:light\|dense thunder wind:-1..1 temp:-60..60 flash flash:dim` (WEATHER 3.6) | `WeatherStore.pin(WeatherPin.parse(v))` |
| `place` | `off`; `lat,lon`; `lat,lon,name` (lat -90..90, lon -180..180) | `PlaceStore.pinPlace(Place(.., PINNED))` |
| `sky` | `follow\|night\|day\|off` | `SkyPins.setMode(SkyPins.parseMode(v))` |
| `days` | `off`; or ids joined by commas (`sky,app,newroz`) | `SkyPins.setLists(SkyPins.parseLists(v))` |
| `appearance` | `off` or the items of 3.7 | `AppearanceStore.get(app).pin(AppearancePin.parse(v))` |
| `theme` | `newroz\|zagros\|semender\|nergiz\|kawa\|roj` (no `off`) | appended to the appearance text as `theme:<v>` |
| `dark` | `1\|0\|system` | appended as `dark:dark\|light\|system` |
| `fire` | `off` or items `level:0..1 wind:-1..1 classic living hold:0..1 light@0..1 douse@0..1 light douse haze:on\|off` (FIRE 3.5) | `FireDebug.apply(FireDebugPin.parse(v))` |
| `pet` | `off`; `<anim>` or `<anim>:<frame>` (lower-case `PetAnim` name, `poke`, `read`, ...); `classic` | `PetDebug.pin(PetDebug.parse(v))` |
| `wood` | `off` or `0..5` | `HomeDebug.pinWoodpile(n)` |

`DebugRequest` gets at its END, in this order, parameters with `Setting.Keep` defaults: `weather: Setting<WeatherPoint>`, `place: Setting<Place>`, `sky: Setting<SkyMode>`, `days: Setting<Set<String>>`, `appearance: Setting<AppearancePin>`, `fire: Setting<FireDebugPin>`, `pet: Setting<PetPin>`, `wood: Setting<Int>`. `apply` order: reset, clock, weather, place, sky, days, appearance, fire, pet, wood, lang, rtl, nav, seed, then the route. `reset=1` also calls `WeatherStore.pin(null)`, `PlaceStore.pinPlace(null)`, `SkyPins.clear()`, `AppearanceStore.pin(null)`, `FireDebug.clear()`, `PetDebug.pin(null)`, `HomeDebug.pinWoodpile(null)`, `HomeEntrance.reset()`. `theme` and `dark` merge into the one appearance pin together with the `appearance` extra. The extra names are added to the read list at `DebugLinks.parse`. Link form (the robot adds the token): `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route home --es clock 2027-03-21T20:30 --es place 41.0,29.0,Istanbul --es sky follow --es weather off --es theme newroz --es dark dark --es token <T>`.

### 3.10 Sprites (names, sizes, frames, timing, anchor, fallback). Frame times in ms; "idx" = chosen by index, drawn with `blit(sprite, frame, ...)`

| Sprite (file) | Size, frames, ms | Anchor and use | If missing |
|---|---|---|---|
| `pet_idle` ... `pet_waving` (`mascot.pxl`) | 32 x 32, unchanged | origin `(feetX - 16, feetY - 31)` | required (throws as today) |
| `pet_blink` | 32 x 32, 4: 70 90 70 60 (290) once | same origin; feet row 31; first frame one step from rest, last = `pet_idle` f0 | PET skips the decoration |
| `pet_tail` | 6: 90 110 140 110 90 60 (600) once | same | skip |
| `pet_warm` | 7: 100 120 400 400 400 120 80 (1620) once | same | skip |
| `pet_yawn` | 6: 120 140 500 400 160 100 (1420) once | same | skip |
| `pet_stretch` | 6: 100 140 500 400 130 100 (1370) once | same | skip |
| `pet_firefly` | 8: 160 160 160 160 140 140 160 100 (1180) once | same | skip |
| `pet_poke` | 8: 110 110 90 100 90 120 110 80 (810) once | same (first cut) | skip |
| `pet_read` | 6, LOOP intro 1 outro 1: 100 300 300 300 300 100 (1400) | same | `THINK` |
| `pet_write` | 7: 100 150 150 150 150 300 80 (1080) once | same | skip |
| `pet_carry` | 6 LOOP 1/4/1: 100 300 300 300 300 100 (1400) | same | `IDLE` |
| `pet_startled` | 6: 60 80 100 80 140 100 (560) once; airborne 1 2 3 | same | skip |
| `pet_dance` | 8: 110 110 100 110 110 100 120 90 (850) once clip, looped whole by the layer while held | same | `HAPPY` |
| `pet_listen` | 6 LOOP 1/4/1: 100 320 320 320 320 100 (1480) | same | `THINK` |
| `pet_shelter` | 6 LOOP 1/4/1: 120 180 180 180 180 120 (960) | same | `IDLE` |
| `pet_snow` | 8: 150 130 130 130 130 220 260 100 (1250) once | same | skip |
| `pet_shadow` | 48 x 5, 4 frames (idx 0..3 = length level), 1000 each | origin `(feetX - 34, feetY - 2)` | no shadow |
| `pet_head` | 16 x 16, 4 faces idx (0 neutral, 1 blink, 2 happy, 3 worried) | PetNote, drawn 32 px (2x) | `Mascot` as today |
| `pet_small` | 16 x 16, 4: 700 90 700 300 | later waves | none |
| `pet_peek` | 16 x 10, 6: 120 120 500 500 90 120 once | `PetPeek`, bottom row on a bar's lower edge | nothing drawn |
| `scene_far`, `scene_near`, `scene_ground`, `scene_fore`, `scene_moon` (`scene.pxl`) | 176x46, 176x26, 176x34, 176x11, 15x15; 1 frame | unchanged; hashes pinned (ART-WORLD 8) | required |
| `scene_far_summer`, `_autumn`, `_winter` (`scene_seasons.pxl`) | 176 x 46, 1; same silhouette as `scene_far` | replaces `scene_far` for that season through `SkyTint` | procedural overlay and `scene_far` |
| `scene_ground_summer`, `_autumn`, `_winter` | 176 x 34, 1; same silhouette and clearing as `scene_ground` | replaces `scene_ground` for that season | procedural overlay and `scene_ground` |
| `scene_woodpile` (`scene_props.pxl`) | 28 x 16, 6 frames idx (frame k = k logs, 0 = ash and a stump), 1000 each | blit at `(woodpileX, woodpileY)`, bottom row 15 on the feet line | nothing drawn |
| `prop_zagros` | 40 x 30, 1 | `SceneProps.forTheme(ZAGROS)`: ON_GROUND, art x 16, last row 16 | no prop |
| `prop_nergiz` | 28 x 12, 1 | NERGIZ: ON_GROUND art x 18 last row 27 and art x 140 last row 29 | no prop |
| `prop_semender` (extra) | 42 x 14, 2: 900 900 | SEMENDER: ON_GROUND art x 16 last row 31 | no prop |
| `prop_kawa` (extra) | 30 x 20, 2: 700 700 | KAWA: ON_GROUND art x 16 last row 30 | no prop |
| `prop_roj` (extra) | 56 x 30, 1 | ROJ: BEHIND_FAR_RIDGE art x 89 last row -22 | no prop |
| `tile_hermes`, `tile_terminal`, `tile_routines`, `tile_saved` (`illustrations.pxl`) | 24 x 24, 1, at most 7 colours | `HomeTile(art = name)`, 48 dp | the icon |
| `empty_bubble` | 32 x 32, 2: 1600 160 | Home Recent empty state, 96 dp | `Mascot(IDLE)` |
| `empty_scroll`, `_stool`, `_jar`, `_hourglass`, `_lantern`, `_boat`, `_cup`; `tile_usage`, `tile_library`, `empty_gate` (extras) | 32 x 32 (tiles 24 x 24), 2 / 1 | later waves, by name | the caller's old picture |

The `pet_read` row is missing in ART-PET's table: this row is the ruling. The art palette letters are each file's own (ART-PET: the 24 letters of `mascot.pxl` plus `G l L n N p v V`); names are unique across all files.

### 3.11 Test tags (exact strings; one tag per node)

| Tag | Written by |
|---|---|
| `stage.hearth` (existing) | SCENE (`HearthScene`) |
| `home.state`, `home.start` (existing, one `home.start` node while NotInstalled, Stopped or Error); `home.hero`, `home.fire` (the fire hit box, `FireTags.HIT`), `home.pet`, `home.sky`, `home.strip`, `home.stop`, `home.cancel`, `home.error`, `home.error.details`, `home.ask`, `home.send`, `home.suggest.<i>`, `home.avatars`, `home.avatar.<i>`, `home.avatar.new`, `home.group`, `home.recent`, `home.recent.<i>`, `home.recent.empty`, `home.tile.hermes\|terminal\|routines\|saved`, `home.usage`, `home.woodpile`, `home.skeleton`, `home.row.<id>` | HOME |
| `appearance.black`, `appearance.sky.follow\|night\|day`, `appearance.place`, `appearance.place.search`, `appearance.place.auto`, `appearance.place.result.<i>`, `appearance.weather` (absent), `appearance.seasons.auto\|spring\|summer\|autumn\|winter\|off`, `appearance.days.<id>`, `appearance.classicFire`, `appearance.tilt`, `appearance.pet`, `appearance.rain`, `appearance.crackle`, `appearance.haptics.off\|subtle\|full`, `appearance.userday.add`, `appearance.userday.<i>` | APPEAR (`AppearanceTags`; `appearance.tilt` is written by FIRE's row through `AppearanceTags.TILT`) |
| `petnote`, `petnote.action` (existing) | PET (`PetNoteHost`) |

INTEGRATE adds exactly these to `ScenarioFilesTest` known tags (patterns for the indexed ones).

### 3.12 Strings (file, prefix, and every key that crosses an area)

| Area | File (`R/values/`) | Prefix | Crossing keys (writer then reader) |
|---|---|---|---|
| SCENE | `strings_sky.xml` | `sky_` | `sky_mode_follow\|night\|day`, `sky_season_*`, `sky_list_*` and `_sub`, `sky_moment_*`, `sky_moon_*` (APPEAR rows; SCENE `skyLabel`); `sky_scene_cd` (SCENE for HOME); `sky_note_*`, `sky_planet_*` (Pêt notes) |
| WEATHER | `strings_weather.xml` | `wx_` | `wx_place_*`, `wx_switch_*`, `wx_credit` (APPEAR rows and sheet); `wx_note`, `wx_cond_*`, `wx_temp`, `wx_temp_f` (HOME's sky tap); `wx_scene_cd` (HOME) |
| FIRE | `strings_fire.xml` | `fire_` | `fire_tilt_title`, `fire_tilt_sub` (its own row). The drafted `fire_classic_*` are not created |
| PET | `strings_pet.xml` | `pet_` | the eight `pet_quip_*` (HOME shows the bubble through `PetQuip.res()`); reuses `ui_home_quip_1..4`, `ui_home_quip_overdrive` |
| HOME | `strings_home.xml` | `hm_` | `hm_scene_cd` (the hero's spoken label; the sky node adds `HearthInfo.skyLabel` and `wx_scene_cd`), `hm_pet_cd`, `hm_woodpile_*` |
| APPEAR | `strings_appearance.xml` | `apr_` | `apr_sky_title`, `apr_sky_sub`, `apr_days_*`, `apr_classic_*`, `apr_black_*`, `apr_pet_*`, `apr_haptics_*`, `apr_rain_*`, `apr_crackle_*`, `apr_place_unavailable`, `apr_userday_*` (extra). `apr_sky_real\|night\|day` are not created |
| ART-PET, ART-WORLD | none | | |

### 3.13 Sound cues and files

`SoundCue.WAKE` ((COLD or KINDLING) to BLAZING, gain `vol * 0.8`), `SoundCue.SLEEP` (BLAZING to COLD, `vol * 0.65`); files `sfx_hearth_wake`, `sfx_hearth_sleep`, `sfx_studio_wake`, `sfx_studio_sleep` (rule `sfx_<pack>_<cue>`), loops `sfx_loop_rain` (level 0.4 light, 0.8 heavy; base gain 0.55) and `sfx_loop_crackle` (0.25 while BLAZING and the switch is on; base 0.25), all `.wav`, 16 kHz mono 16-bit, looked up with `getIdentifier`. Logs (TEST_HOOKS): `I/Sound: loaded <n> of <m>, missing: <names>`, `I/Sound: loop RAIN level=0.8`, `I/Sound: play WAKE pack=HEARTH`. Other log lines: `I/Sky: <frame.debugLine()>` (once per change of moment or minute), `I/Fire: look sprite=.. classic=.. level=..`, `I/Fire: tilt on|off`, `I/Pet: anim=<NAME> base=<NAME>`, `I/Weather: skipped NO_CONTACT` (once).

## 4. Resolution of every REQUEST

"A" = accepted as asked, "A*" = accepted changed (the change is stated), "R" = rejected (reason). A request to "LEAD" or "ROBOT-T" lands on the owner named in 2.9. Ids: S = SCENE, W = WEATHER, F = FIRE, P = PET, AP = ART-PET, AW = ART-WORLD, H = HOME, A = APPEAR (section 7 of each draft, in order).

| # | From to | Request | Ruling |
|---|---|---|---|
| S1 | SCENE to WEATHER | extras `sky`, `days` in `DebugLinks` | A (3.9); `sky` accepts `off`; apply order changed |
| S2 | SCENE to APPEAR | `Appearance.sky`, `blackNight`; sky chips, place rows via WEATHER's `PlaceRows()`, seasons chips, days rows, user days, black night | A*: `SkyMode` is SCENE's (#R3); place rows are APPEAR's own code (#R10); days rows from `SceneCalendar.LISTS`; user days an extra |
| S3 | SCENE to HOME | scrim colour from `HomeSky.skyTop` | R: `HomeScrim.kt` untouched (#R6); the strip covers the status bar |
| S4 | SCENE to HOME | tap above the ridges calls `skyBodyAt` first; `sky_scene_cd` as the scene's label | A*: through `HearthInfo.skyNoteAt` and `skyLabel`; order body, then weather, else nothing |
| S5 | SCENE to LEAD | `AppRoot.kt:234` uses `HomeSky.iconsDark` | A: INTEGRATE, spelled in 7.5 |
| S6 | SCENE to LEAD | CI runs `stars.py --verify` and `--selftest`; download HYG; plan text 800 to 1000 | A: INTEGRATE (build.yml), DATA (download), the lead (plan text) |
| S7 | SCENE to FIRE | implement `FireLayer`, `FireLayers.create`, `rememberFireInput` | A*: `FireLayerImpl` with three methods and `SceneGeometry`; no `FireLayers`, no input class (#R2) |
| S8 | SCENE to PET | `PetLayer`, `PetLayers.create`, `rememberPetInput` | A*: PET's class; no factory; `SceneState.pet: PetPose?` (#R2) |
| S9 | SCENE to ART-WORLD | optional `scene_prop_<theme>`, `scene_ground_<s>`, `scene_near_<s>` | A*: props are ART-WORLD's `prop_<theme>` with `SceneProps.forTheme`; `scene_near_<s>` not drawn (procedural near overlay only); `scene_far_<s>` and `scene_ground_<s>` as 3.10 |
| S10 | SCENE to ROBOT-T, TRANSLATE | scenario `sky-pins.json`; tr and fr | A: scenarios of 8, TRANSLATE 4 |
| W1 | WEATHER to BUILD | `WEATHER_CONTACT` build field | A: INTEGRATE, two lines in 7.5 |
| W2 | WEATHER to SCENE | `SceneState.weather`, layer calls at the stated places, star and body visibility, edge arrays, three NOTICE blocks, parity unaffected | A (5.1); NOTICE by INTEGRATE; `WeatherGeometry` extended (3.3) |
| W3 | WEATHER to APPEAR | call `PlaceRows()` and `WeatherSwitchRow()`; rain loop follows `rainLevel` | A*: no such composables; APPEAR writes the rows against `PlaceStore` and `WeatherStore`; the rain loop is `SceneSounds` |
| W4 | WEATHER to HOME | tap above `groundTop` shows `weatherNoteText`; label `wx_scene_cd` | A (after the body check of S4) |
| W5 | WEATHER to ROBOT-T, lead | known tags; scenario `weather-pins.json` | A: tags 3.11 (INTEGRATE); scenarios 8 |
| W6 | WEATHER to LEAD | (1) ledger line for what leaves the phone; (2) decision 6, the contact (public in the APK; recommend a project URL); (3) "to verify" list before the switch is shown; (4) w0 contract 3.3 line | the lead (not a build task); noted in 10 |
| W7 | WEATHER to TRANSLATE | `wx_*` tr and fr | A |
| F1 | FIRE to SCENE | `SceneState.fire: FireParams` last field | A*: fields in the order of 3.1 |
| F2 | FIRE to SCENE | renderer edits: remove fire lookups, call `fireLayer.glow/flame/sparks`, keep smoke | A*: hooks take `SceneGeometry`; ctor param `fire: FireLayer = FireLayerImpl(sprites)`; smoke stays SCENE's |
| F3 | FIRE to SCENE | `HearthScene`: `fireHold`, `onFireTransition`, `rememberFireDriver`, `.fireHaze`, params in `SceneState` | A (3.8) |
| F4 | FIRE to HOME | overlay on `FireGeometry.hitRect`, tap lights or stokes, hold puts out, labelled entry, sounds and haptics from `onFireTransition`, fix `ui_home_mascot_cd` | A*: hold 700 ms (#R7); tag `home.fire`; WAKE and SLEEP sounds only from `SceneSounds`; the string stays (nobody edits `strings_chat.xml`), HOME uses `hm_pet_cd` |
| F5 | FIRE to WEATHER | extra `fire` | A (3.9) |
| F6 | FIRE to APPEAR | call `FireSettingsRows()`; crackle volume follows `FireFeed.level()` | A*: the row is the tilt row only; crackle stays a flat 0.25 (extra later) |
| F7 | FIRE to PET | read `FireFeed.state` and `level()`; position from `FireGeometry` | A*: PET reads `toolsRunning`, `FireResolve.intensityOf` through SCENE, and `SceneGeometry` |
| F8 | FIRE to ART-WORLD | never edit the five `fire_*` sprites or `build_fire_pxl`; no prop over the 24 by 30 hit box | A (woodpile left edge `hearthX + 18`) |
| F9 | FIRE to INTEGRATE | write `core-sprites.golden` with `FIRE_GOLDEN_WRITE=1`, run `FireTasteSheetDump`, copy the PNGs | A (7.5, 7.4) |
| F10 | FIRE to BUILD, manifest | nothing | A |
| P1 | PET to SCENE | `SceneState.pet`; `petLayer.draw` replaces lines 141 to 153; `HearthScene` param `pet`, builds `PetWorld` | A (3.8, 5.1) |
| P2 | PET to HOME | `rememberPetController`, `tap()`, `flags()`, `PetPlacement.hits`, bubble, haptics, sounds, celebration 4200 ms, tag `home.pet`, remove `tapMood` and the chain | A (R7); `hits` takes the geometry |
| P3 | PET to FIRE | `FireFeed.toolsRunning` | A (3.4) |
| P4 | PET to WEATHER | extra `petpose` | A*: named `pet` (lead's list), grammar of PET |
| P5 | PET to ROBOT-T, lead | tag `home.pet`; scenario `pet-poses.json`; `AGENTS.md` after review | A (tags INTEGRATE; scenarios 8; the lead) |
| P6 | PET to ART-PET, ART-WORLD | art contract in `mascot_life.pxl` | R: the lead's file is `mascot_anim.pxl` with ART-PET's table (#R9); PET's numbers change to it |
| P7 | PET to LANG, TRANSLATE | eight `pet_quip_*` | A |
| AP1 | ART-PET to PET | use `PetArt.clip(name, SpriteBank::find)`; never `get`; playing rules; one reaction at a time; mapping of events to clips | A (3.5) |
| AP2 | ART-PET to PET | `PetNote` draws `pet_head` (faces 0, 2, 3), fallback `Mascot`; peek uses `pet_peek` on `appearance.showMascot` | A*: peek is `PetPeek`, not placed in 1a; the face only if the clip passes the look (absent = fallback) |
| AP3 | ART-PET to PET (link owner) | pin `pet=<clip>[:frame]` | A* (3.9 grammar: `<anim>[:<frame>]`) |
| AP4 | ART-PET to SCENE | new sprites optional (`REQUIRED_SPRITES` stays); shadow blit before Pêt | A; the shadow is PET's `PetLayer` job, inside `draw` |
| AP5 | ART-PET to ART-WORLD | `craft_art.py` also runs `pet_clips.py`; README rows | A (2.6) |
| AP6 | ART-PET to LEAD | `AGENTS.md` repo map; size budget line | the lead; INTEGRATE (budget numbers) |
| AW1.1 to 1.2 | ART-WORLD to SCENE | `Season` as given; every new sprite through `findOrNull`; `REQUIRED_SPRITES` 17 | A*: `Season` is SCENE's; `SpriteSource.find` |
| AW1.3 | ART-WORLD to SCENE | tint table through `SceneRoles.recolor`, KEEP in classic | R (#R8, #R11): `SkyTint` |
| AW1.4 | ART-WORLD to SCENE | layer order for sun, moon, clouds, props, woodpile | A* for props and woodpile (5.1 order); sun, moon, clouds are not sprites (#R8, #R11) |
| AW1.5 | ART-WORLD to SCENE | woodpile centre at least `fx + 27`; nothing drawn at collapse 1 | A (`woodpileX = hearthX + 18`, `showProps`) |
| AW1.6 | ART-WORLD to SCENE | rotate the moon for a southern place | A*: SCENE's procedural moon is hemisphere-correct |
| AW1.7 to 1.8 | ART-WORLD to SCENE | at most two tinted rasters per frame; parity can stay | A (cache keys); A (#R1) |
| AW2 | ART-WORLD to WEATHER | `clouds()` may blit `scene_cloud_*` | R (#R11) |
| AW3 | ART-WORLD to HOME | tiles, `empty_bubble` (2 frames), woodpile frame is HOME's level | A |
| AW4 | ART-WORLD to LEAD | extras `theme`, `dark`, `wood`; `DebugOverrides.theme/dark`; AppRoot reads them | A*: extras yes (3.9); no `DebugOverrides`, no AppRoot theme code (#R12) |
| AW5 | ART-WORLD to BUILD | CI `craft_art.py --selftest` | A (7.5) |
| AW6 | ART-WORLD to ROBOT-T | scenario `world-art.json` | A (8) |
| AW7, AW8 | ART-WORLD to ART-PET, FIRE | only the combined check; fire untouched | A |
| H1 | HOME to SCENE | `HearthScene` params, `HearthInfo`, `SceneAnchors`, remove `.height(280.dp)`, parallax, reveal, band, woodpile, retire parity | A*: params (3.8); `HearthInfo` (3.2); `SceneGeometry` instead of `SceneAnchors` (R5); collapse and reveal as the formulas of 3.3; parity stays (#R1) |
| H2 | HOME to ART-WORLD | tiles, woodpile, `empty_bubble` as 4.1 of HOME | A (3.10) |
| H3 | HOME to PET | `PetSignals` | R: `PetController` (#R7) |
| H4 | HOME to WEATHER | `HomeEntrance.reset()` in `reset`; `rememberWeather`, `weatherNoteText` | A (3.9) |
| H5 | HOME to APPEAR | `SoundCue.WAKE`, `SLEEP` | A (3.7) |
| H6 | HOME to LEAD | `AppRoot` line; archive and delete-with-undo do not exist (swipe: pin and mark read) | A (7.5); the swipe note is already in LEAD-DECISIONS (pin and mark read only; archive needs a field, Wave 2) |
| H7 | HOME to ROBOT-T, lead | tags; `home-sky`, `home-gestures`, `home-collapse` scenarios | A (3.11, 8) |
| H8, H9 | HOME to INTEGRATE, TRANSLATE | `stage.hearth` stays on SCENE's `Spacer`, `home.hero` is HOME's; tr and fr | A |
| A1 | APPEAR to WEATHER | drop `PlaceWeatherSettings.kt` | A |
| A2 | APPEAR to WEATHER | extra `appearance`, apply between `place` and `lang` | A* (3.9: after `days`, before `fire`) |
| A3 | APPEAR to HOME | call `SceneSounds(hearth, rainLevel)` once | A |
| A4 | APPEAR to SCENE | provide `SceneCalendar.lists`/`DayList`; Newroz ridge fires only when `newroz` is a switched-on list; retire parity | A*: `LISTS`, `DayListTexts`; `nightFires` in the classic path and `ridgeFires` in the frame; parity stays |
| A5 | APPEAR to FIRE | read `classicFire` in composition | A |
| A6 | APPEAR to PET | read `petInChatsVisible` for the peek | A (1b places it) |
| A7 | APPEAR to ROBOT-T | tags, `appearance-pins.json` | A (3.11, 8) |
| A8 | APPEAR to LEAD | NOTICE: uisfx CC0 entry (also repairs the old gap) and the generated-sounds entry | A: INTEGRATE, text in 7.5 |
| A9 | APPEAR to BUILD | sound share in `tools/size-budget.txt` | A: INTEGRATE |

## 5. Amendments per area (apply before writing a line; exact)

### 5.1 SCENE

1. `SceneModel.kt`: `SceneState` as 3.1 (fields in that order, defaults as shown); `SpriteSource.find` as 3.1. Delete `SceneInput`, `FireLayers`, `PetLayers`, `FireLayer`'s old signature, the `PetLayer` interface, `rememberFireInput/PetInput` from the draft. `SceneLayers.kt` holds `SceneGeometry` (3.3) and the `FireLayer` interface only.
2. `SceneRenderer`: constructor as 3.1. Final layer order, back to front (`g0 = height - 34 - bandRows`): 1 sky gradient over the whole stage; 2 stars (`StarLayer` or the old 46) at `+skyDy`, alpha times `look.starVisibility()`; 2b planets (E1); 3 meteors (FULL, `look.cloud` below MEDIUM); 4 moon, 4a sun (alpha `look.bodyVisibility()`); 4b `weather.lightning`; 4c `weather.clouds`; props BEHIND_FAR_RIDGE (`SceneProps`, dimmed); 5 far ridge at `farRidgeTop`; 6 near ridge at `nearRidgeTop`; 6b `snowCaps`; 6c `fog`; 6d `precipFar`; 7 ridge fires (`frame.ridgeFires`, or in the classic path today's three when `state.nightFires`); 8 ground at `groundTop` (tinted; season sprite via `find` when present, else the procedural overlay), season overlay, ON_GROUND props (dimmed), 8b moonlight (E6); 9 `fire.glow`; 10 Pêt (`state.pet != null && geo.showProps` gives `petLayer.draw(stage, pose, geo.hearthX - 27, geo.hearthBase, t)`; `pet == null` runs the old lines at the new coordinates); 10b woodpile (`state.woodpile` and `geo.showProps` and the sprite found: `blit(sprite, k, geo.woodpileX, geo.woodpileY)`, dimmed); 11 `fire.flame`; 11b smoke (SCENE's own, at `hearthX`, `hearthBase - 8`); 11c `fire.sparks`; 12 fireflies; 12b fireworks (E3); 13 foreground grass at `foreTop` (tinted); 13b band fill; 14 `weather.precipNear`; 15 horizon dither. The weather calls are made in both paths and draw nothing for CLEAR. With all defaults every coordinate equals today's and the output equals `LegacyHearthReference`.
3. `reveal` and `collapse` only change `SceneGeometry` (3.3); the sky gradient is always full. `quality = PLAIN` freezes everything as the drafts say.
4. `HearthScene.kt` (you are its only owner): signature of 3.8. It collects `rememberPlace()`, `rememberWeather().look`, `rememberSkyFrame()`, `rememberStarTable()`, `SceneDaysStore.lists`, `Pocket.appearance.classicFire` (through the driver), builds `SceneRenderer(SpriteBank, stars)` in `remember(ready, stars)`, calls `rememberFireDriver(state, clock, fireHold, onFireTransition)` and `Modifier.fireHaze(driver)` (before `drawBehind`), and in `drawBehind` builds `SceneState(.., sky, look, Pocket.colors.bg.toArgb(), driver.params(t), pose, collapse(), reveal(), bandRows, woodpile, nightFires = "newroz" in lists)`. `pose = pet?.poseAt(t, world)` with `PetWorld(fire = petFireOf(state), fireLevel = FireResolve.intensityOf(...), sunAltitudeDeg = frame?.sun?.altDeg ?: -30f, localMinutes = hour * 60 + minute of AppClock.now(zone), rain = look.rainLevel, snow = petSnowOf(look), animate = anim != OFF, lively = anim == FULL)` (flags busy, tool, waiting, celebrating, listening are ORed in by the controller). The stamp stays `Triple(scene, t, quality)`. `HearthScene` keeps ignoring `showMascot`.
5. `SkyHost.kt`: also `HearthInfo`, `rememberHearthInfo`, `DayListTexts` (3.2). `SkyPins` and `SceneDaysStore` as the draft. In `TEST_HOOKS` builds `AppLog.i("Sky", frame.debugLine())` once per change of moment or minute.
6. `SkyTint.dimmed` (3.2). E4 and E5: props come from `SceneProps.forTheme(Pocket.appearance.theme)`; ground and far season sprites by `SceneSprites.ground(season)` and `far(season)`; `scene_near_<s>` is not used (delete E5's near part).
7. Not yours any more: `AppRoot.kt` line, `NOTICE` (HYG block), `HomeScrim` request, the scenario file, CI lines, the plan text; you only write the script, the code and the tests. The HYG block text is produced by DATA into `stars.source.txt` (7.1).
8. Remove from your draft: the `lunarnewyear` and `diwali` lines of `DAYS_DATA`, the `LANTERNS` effect, `SkyFrame.lanterns`, the strings `sky_list_lunarnewyear*` and `sky_list_diwali*`, the tests `datesTable` and `lunarNewYearAndDiwaliAreNewMoons`; the `dates:` rule may stay in the parser (no shipped line uses it). E3 is `FIREWORKS` only (New Year's Eve and Bonfire Night). Add the constants of 5.9.
9. New tests: `SceneGeometryTest` (`of(176, 160)` gives today's `g`, `fx`, `fbase`, `foreTop`; `of(155, 120, 31, 1f)` gives `landDy = 31`; monotone in `collapse`; `reveal` 0 gives `enter = 48`; `showProps` false from 0.75); `SceneRendererSkyTest.hooksCalledInOrder` uses a fake `FireLayer` and the real `PetLayer` with `TestSprites`; `SceneStillsDump` writes PNGs when `SCENE_STILLS_DIR` is set (the stills listed in 7.4) with `javax.imageio`, nothing otherwise.

### 5.2 WEATHER

1. `DebugLinks.kt` and `DebugLinksTest.kt`: all of 3.9 (names added to the read list; eight new `DebugRequest` fields at the end in that order; apply order; `reset` calls; `theme` and `dark` merged into the appearance text before `AppearancePin.parse`; a bad `theme` or `dark` or `wood` goes to `rejected`). Tests: `extrasParse`, `badValuesRejected`, `applyOrder`, `resetClearsAllPins`, `releaseBuildIgnoresAll`, `themeAndDarkMergeIntoOnePin`. You call `FireDebug`, `PetDebug`, `HomeDebug`, `HomeEntrance.reset`, `SkyPins`, `AppearanceStore.pin` by the signatures of 3.2, 3.4, 3.5, 3.7, 3.8.
2. Do not create `PlaceWeatherSettings.kt` or `WeatherTags`; your tag strings live in `AppearanceTags` (APPEAR). `WeatherGeometry` as 3.3. No `SoundEngine` request.
3. `make_places.py`: `--cities` also reads Natural Earth GeoJSON (`properties`: `nameascii`, `name`, `adm0_a3`/`iso_a2`, `pop_max`, `featurecla`; coordinates from `geometry`) and CSV; `--backward` may be omitted when `--tzdata-zi <tzdata.zi>` gives the `L` link lines; `--force-include` stays optional and unused; the script prints the count and, when rows are outside 800 to 1500, exits 1 so DATA changes `--min-pop`. Column names are to be verified against the real file by DATA (from memory in the draft).
4. Keep `weatherNoteText`, `rememberWeather`, `rememberPlace` exactly as 3.6 (HOME, SCENE and APPEAR call them; two callers are safe).

### 5.3 FIRE

1. No `FirePrefs`. `FireDriver` reads `Pocket.appearance.classicFire` and `.tiltFire` in `rememberFireDriver` (composition) and passes plain values to `params()`. `FireSettingsRows()` is the tilt row only (3.4); delete the `fire_classic_*` keys.
2. Implement `FireLayerImpl : FireLayer` (3.1); `FireGeometry(geo)` (3.3); `FireTuning.HOLD_MS = 700L`; `FireTags.HIT = "home.fire"`; `FireResolve.intensityOf`; `FireFeed.toolsRunning`. `FireLayerImpl` takes positions only from `geo` (`hearthX`, `hearthBase`; sprite origin `(hearthX - 16, hearthBase - 35)`); `weatherWind` is `state.weather.wind`. Smoke and fireflies stay SCENE's.
3. `FireGeometryTest.matchesTodaysConstants` uses `SceneGeometry.of(176, 160)`; add a case with `bandRows 31, collapse 1`. `FireClassicParityTest` is unchanged in intent (it needs only `LegacyFireReference`). `FireTasteSheetDump` writes into `FIRE_SHEET_DIR` and `FireCoreSpritesPinnedTest` `Assume`s the golden (INTEGRATE writes it).
4. The four `ChatEngine.kt` lines as FIRE 3.1, nothing else; `ChatEngineFireFeedTest` keeps its own copied helpers.
5. The hold ring is drawn from `state.fire.holdProgress`; HOME's gesture feeds `fireHold`.

### 5.4 PET

1. `PetAnim` numbers as 3.5 (the art table); `PetSet` instead of `PetArt`; no `NOT_SHIPPED`, no `MINI`, no `mascot_life.pxl`; `PetArtTest` is not yours. `PetLayer` as 3.5 (`PetArt.clip` from ART-PET; the classic seven through `SpriteSource.get`); `PetPlacement` takes `geo`.
2. Idle weights and the world rules stay as PET 3.2; only `SHADOW` gauge changes (3.5); `PetBrain` timings use the new durations (the `idleWeights` test numbers do not change; `gapIsTwoOrThreeCycles` stays).
3. The pin extra is `pet` (3.9); `PetDebug.parse` as drafted.
4. `PetNote.kt`: swipe and rise as drafted; the face is `pet_head` (frame 0 INFO, 2 SUCCESS, 3 ERROR) drawn at 32 dp as one image with `playing = false`, `Mascot` when `SpriteBank.find("pet_head")` is null; `PetNoteQueue`, `PetNotes`, tags unchanged.
5. `PetHost.kt`: `toolRunning = FireFeed.toolsRunning.value > 0`; `PetController.poseAt` returns `PetPose(.., shadow in -1..3)`. `PetPeek` draws `pet_peek` (extra; nobody places it).
6. `PetCoreIsPureTest` covers `PetAnim.kt`, `PetWorld.kt`, `PetBrain.kt`, `PetLayer.kt`, `PetTaps.kt`, `PetDebug.kt`.

### 5.5 ART-PET

1. Add the `pet_read` row of 3.10; ids and numbers of every clip are those of 3.10 (they equal your 3.1 except `pet_read`). Output `mascot_anim.pxl`; `--check` is called by `craft_art.py` (ART-WORLD wires it).
2. `PetArt` stays in `J/ui/pixel/PetArt.kt`; `shadowFrame` thresholds as drafted (`PetLayer` does not call it; the brain's gauge does the hysteresis). No `NOT_SHIPPED`: a dropped clip is simply absent from the file.
3. Review verdicts go to `docs/waves/w1/look/review-ART-PET.txt` and the sheets to `docs/waves/w1/look/pet-*.png` (not `dist/w1a/pet/`); the list of 7.2 is your checklist; a rejected clip is removed from `CLIPS` and the file regenerated. Your debug pin is PET's `pet` extra.
4. `PetArtTest` keeps its rows; the row `shadowFrameThresholds` stays; `everySpriteInTheFileHasASpecOrIsAStill` also allows `pet_head`, `pet_small`, `pet_peek`, `pet_shadow`.

### 5.6 ART-WORLD

1. Cuts of #R11. `craft_art.py --only` takes `fire|scene|seasons|props|illustrations|appicon|pet`; plain run and `--check` cover `scene.pxl` (must stay byte-identical: the five hashes of your section 8 are the test), `scene_seasons.pxl`, `scene_props.pxl`, `illustrations.pxl`, `fire.pxl`, `appicon.pxl` and `pet_clips.py`. Do not refactor `build_scene_pxl`; add new builders beside it. `--selftest` as drafted without roles, moon or clouds.
2. `scene_seasons.pxl`: `scene_far_summer|autumn|winter`, `scene_ground_summer|autumn|winter` by your season rules. `scene_props.pxl`: `scene_woodpile` (6 frames, bottom row 15 opaque in every frame), `prop_zagros`, `prop_nergiz` core; `prop_semender`, `prop_kawa`, `prop_roj` extras. `illustrations.pxl`: four tiles and `empty_bubble` core, the other empties and `tile_usage`, `tile_library`, `empty_gate` extras. No new letter may reuse a char with a different colour inside one file.
3. Kotlin: `SceneSprites` has `NEAR`, `FORE`, `MOON`, `WOODPILE`, `WOODPILE_FRAMES = 6`, `far(season)`, `ground(season)`, and `SceneProps`, `ScenePropSpec`, `PropLayer` exactly as you drafted; no `CLOUDS`, `SUN`, `moonFrame`, `findOrNull`. `Season` is SCENE's (3.2). `IllustrationId` as drafted.
4. Tests: `ArtWorldAssetsTest` (five hashes, catalogue, season silhouettes equal, snow line order, woodpile, illustration rules), `SceneSpritesTest` (names, props clear of Pêt and the flame for widths 144, 155, 160, 176 with `hearthX = (0.6 * w).toInt()` and the woodpile rectangle `[hearthX + 18, hearthX + 46)`), `IllustrationsTest`. Drop `SceneRolesTest`, `moonPhasesWaxAndWane`, `cloudsLookLikeClouds`.
5. README: the generated-files table including `mascot_anim.pxl` (ART-PET's row text: "generated by `pet_clips.py`; commands `python tools/art/pet_clips.py`, `--check`, `--report [NAME]`, `--selftest`"), and the rows of `stars.py`, `make_places.py`, `make_sounds.py` (7.1).
6. Contact sheets are made by DATA with the commands of 7.1; you write no PNG.

### 5.7 HOME

1. `FireSwitch.HOLD_MS = 700L` (test asserts it equals `FireTuning.HOLD_MS` when that class exists). Delete `HearthTaps`, `HomeChrome`, `sceneAnchors`. Pêt taps: `PetController.tap()` (3.5); fire taps: `FireSwitch`; `FireRing` keeps `fraction(heldMs)` (passed as `fireHold`); the ring is drawn by the scene.
2. Hit targets: `geo = SceneGeometry.of(stageW, stageH, bandRows, collapseQ)` from `StageGrid.forScene`; fire rect `FireGeometry(geo).hitRect(grid, 48.dp px)`, Pêt rect `PetPlacement.hitRect(grid, geo, 48.dp px)` (the fire wins the overlap), sky = taps above `geo.groundTop` on the list's item 0 (`detectTapGestures`, taps only), tags `home.fire`, `home.pet`, `home.sky`; no Pêt or woodpile target while `!geo.showProps`.
3. Sky tap: `HearthInfo.skyNoteAt(x, y)` first, then `weatherNoteText(point)` when `rememberWeather().point != null`, else nothing. Sky node label: `info.skyLabel` plus `wx_scene_cd` when a point exists plus `hm_sky_cd` as the click label.
4. `HomeDebug` (3.8) and `Woodpile` level: the pinned level wins when `TEST_HOOKS`. `HomeEntrance.reset()` is public.
5. Calls: `HearthScene(.., collapse = { c }, reveal = { r }, bottomBand = 84.dp, woodpile = ui.woodpile, fireHold, onFireTransition, pet)`, `SceneSounds(hearth, rainLevel)`, `rememberPetController()`, `rememberHearthInfo()`. Haptics in `onFireTransition`; sounds `SPARK` and `FORGE` as today; never `WAKE` or `SLEEP`.
6. Strings: `ui_home_mascot_cd` is not used; no change to `strings_chat.xml`. The swipe actions are pin and mark read or unread (LEAD-DECISIONS).
7. Everything else as drafted. Remove "Retire `SceneRendererParityTest` first" from your assumptions.

### 5.8 APPEAR

1. `Appearance` per 3.7: add `sky`, `blackNight`, `classicFire`, `petInChats`, `rainSound`, `crackle`, `tiltFire`; no `days`, no `DayLists`, no migration, no `daysInit`, no `isExistingInstall`. Keys stored by name; unknown names fall back. `AppearanceStore.pin` and `isPinned` as drafted (the flow shows `pin.change(stored)`; `update` edits the stored value).
2. `AppearancePin` grammar of 3.7 (no `sky:`, no `days:`). `BlackNight` as drafted. `PocketTheme.kt` edit as drafted.
3. Rows per 3.7: the Weather row is composed only when `WeatherStore.get(ctx).available`; the Place row and `PlacePickerSheet` as drafted with the five states; seasons chips; days rows for `group != SKY`; classic fire then `FireSettingsRows()`; the Pêt row is not composed; Touch feedback chips as drafted; the Sound of rain row only when the weather is available; crackle row (extra). User days (extra): `PocketTextField` for the name, two number chips or a short list for month and day, `SceneDaysStore.addUserDay`, rows with a delete button, strings `apr_userday_title`, `_sub`, `_add`, `_name_hint`, `_none`; at most 12.
4. Sounds as drafted; the script is `tools/art/make_sounds.py` (R13); `SoundFilesTest.noticeNamesSources` `Assume`s `NOTICE` and checks it only after INTEGRATE (it fails softly before: `Assume`).
5. `SceneSounds`: `hearthCue(prev, next)` as drafted; loops from `LoopMath.rainLevel(rainLevel, ap.rainSound)` and `LoopMath.crackleLevel(hearth == BLAZING, ap.crackle)`.
6. Remove `AppearanceMigrationTest`; `AppearanceModelTest.defaultsAreToday` has the seven new defaults.

### 5.9 Look knobs (each owner declares its constants in stage 1 so LOOK-2 can turn them without logic)

| Constant | Owner, file | Default | LOOK-2 may set |
|---|---|---|---|
| `STAR_MAG_LIMIT`, `STAR_DIM_BASE` | SCENE `Stars.kt` | 4.5f, 0.35f | 3.0 to 4.5; 0.2 to 0.5 |
| `Seasons.SPRING_ON`, `SUMMER_ON`, `AUTUMN_ON`, `WINTER_ON` | SCENE `Seasons.kt` | true | false removes that season's procedural overlay |
| `SkyPalette.THEME_BIAS` | SCENE `SkyMoments.kt` | 0.10f | 0 to 0.2 |
| `FireTuning.HIGH_AT`, `IDLE_INTENSITY`, `SIDE_LICKS: Boolean` | FIRE `FireSpec.kt` | 0.30f, 0.70f, true | 0.2 to 0.4; 0.6 to 1.0; false |
| `WeatherArt.CLOUD_TONES` | WEATHER `WeatherArt.kt` | 3 | 2 (cut order of WEATHER) |

## 6. Strings, stored data, manifest

### 6.1 Strings
Files and prefixes are in 3.12. TRANSLATE (stage 4) writes `values-tr` and `values-fr` for every key of the six files, plus nothing for ART-PET and ART-WORLD. Rules as `w0-contract.md` section 6 (every key gets tr and fr in this wave; placeholders identical; length within `floor(room * 1.4)`; the note of each key is the brief; no exclamation marks). Specifics: `Pêt`, `Agirî`, the names of days (`Newroz`, `Midsummer`, `Bonfire Night`, `New Year's Eve`: the name of the day is kept or given in its usual local form) and star proper names stay as written; planet and moon-phase names are translated; `wx_temp` and `wx_temp_f` keep `%1$d°`; `wx_note` keeps its `\n`; `sky_note_moon` keeps `%1$s` and `%2$d`; `fire_tilt_sub` keeps the sentence about the sensor; `apr_*` rows keep their order of facts. Every English key written by an area carries its own `<!-- t: ... | kind: ... -->`. `strings-check.ps1` must exit 0 (2 means a ratchet got worse: fix the text), `make-test-language.ps1` regenerates `app/src/debug/res/values-en-rXZ/`.

### 6.2 Stored data (the Wave 0 build ignores all of it; the rollback APK is unaffected)

| Item | Default | Why the old code ignores it |
|---|---|---|
| prefs `appearance` keys `sky` (enum name `FOLLOW\|NIGHT\|DAY`), `blackNight`, `classicFire`, `petInChats`, `rainSound`, `crackle`, `tiltFire` | `FOLLOW`, false, false, true, true, false, true when absent | `AppearanceStore.load` reads its ten keys by name; `update` writes only its own; an unknown enum name already falls back to the default |
| prefs `scene_days`: `lists` (comma ids), `seasons` (`AUTO` default), `user_days` (JSON, at most 12), `migrated` (1) | absent: `lists = defaultLists(existing)` = `sky,app` plus `newroz` for an existing install; the one write of 1.2 is `migrated` | a file the old code never opens |
| prefs `place` (`mode`, `city_*`), prefs `weather` (`enabled`) | auto; false | files the old code never opens; nothing is written until the owner picks a city |
| `files/weather/cache.json` | never created while the contact is empty | a folder the old code never opens |
| assets `sky/stars.bin`, `sky/stars.source.txt`, `places/zones.tsv`, `places/cities.tsv`; `pixel/mascot_anim.pxl`, `scene_seasons.pxl`, `scene_props.pxl`, `illustrations.pxl`; `res/raw/sfx_*.wav` (six) | shipped | new files in the APK |
| not stored | pins, the sky frame, `FireFeed`, wind, `PetController`, `HomeEntrance.played`, collapse state | memory only |
| backup | none of the new prefs is in `BackupCore.PREF_SCHEMA` (follow-up 9 of section 10): a restore returns them to the defaults | harmless |

### 6.3 Manifest
**No change.** No permission (the accelerometer needs none; no location, no storage, no camera), no `uses-feature`, no `<queries>`, no component. `INTERNET` exists and no request leaves while the contact is empty. `res/raw/keep.xml` already keeps `@raw/sfx_*`. The twin's overlays are unaffected.

## 7. The later stages

### 7.1 DATA (stage 2, one agent; the only one that downloads; touches no code and no art script except the two bounded fixes below)
Downloads, each into its own NEW empty folder under `dist/data-w1a/` (git-ignored), read as data only, never executed; every Python call is `python -I`; record file name, size and SHA-256 of each in the report. Addresses are from the drafts and from memory: DATA opens the licence or readme text of each download, copies the licence line into `stars.source.txt` (stars) and the report (the rest), and **stops and reports if a licence differs from the lead's table**.

| Input | Source | Folder | Licence expected |
|---|---|---|---|
| HYG star catalogue (newest `hyg_v*.csv` or `.csv.gz`, columns `id ra dec mag ci proper`) | the HYG-Database repository (`https://github.com/astronexus/HYG-Database`, folder `hyg/CURRENT`) | `dist/data-w1a/hyg/` | CC BY-SA (version read from the file: 2.5 or 4.0) |
| Natural Earth populated places, "simple", scale 10m (GeoJSON or CSV) | `https://github.com/nvkelso/natural-earth-vector` (`geojson/ne_10m_populated_places_simple.geojson`) or `https://www.naturalearthdata.com/downloads/10m-cultural-vectors/` | `dist/data-w1a/ne/` | public domain (terms page saved as text next to it) |
| IANA zone data: `zone.tab` and `backward` (or `tzdata.zi`) | the system `/usr/share/zoneinfo` or the installed Python `tzdata` package first (no download); else the newest `tzdata<year>x.tar.gz` from `https://data.iana.org/time-zones/releases/` | `dist/data-w1a/tz/` | public domain |

Steps (repo root, Windows PowerShell 5.1 or Git Bash; Python 3.12 and Pillow are installed, no `pip`):
1. `python tools/art/stars.py --hyg dist/data-w1a/hyg/<file> --out app/src/main/assets/sky/stars.bin --source-note app/src/main/assets/sky/stars.source.txt`; then `--verify` and `--selftest`. Expect N 800 to 1000, at most 12 KB. Commit set: `stars.bin`, `stars.source.txt`; the catalogue is not committed.
2. `python tools/places/make_places.py --zone-tab <zone.tab> --backward <backward> --cities <ne file> --format ne --min-pop 500000 --out app/src/main/assets/places`; then `--check` and `--selftest`. Expect 800 to 1500 cities, at least 400 zones, at most 90 KB; if the count is outside, change `--min-pop` and say so. Fix 1 (allowed): a wrong Natural Earth column name in the script, then re-run, reported.
3. `python tools/art/make_sounds.py` (writes `app/src/main/res/raw/sfx_*.wav`, six files, at most 300 KB together); then `--check`, `--selftest`. Fix 2 (allowed): none; a failing script goes back to APPEAR.
4. `python tools/art/pet_clips.py`, `--check`, `--selftest`, `--report > docs/waves/w1/look/pet-report.txt`; `python tools/art/craft_art.py`, `--check`, `--selftest`. `git diff --stat` (read only) must show `scene.pxl`, `fire.pxl`, `mascot.pxl`, `appicon.pxl` unchanged.
5. Contact sheets into `docs/waves/w1/look/` (each PNG at most 400 KB, the folder at most 6 MB with 7.4's; Pillow present; fallback `tools/pixel-preview.ps1`, never a download): `python tools/art/contact_sheet.py --file app/src/main/assets/pixel/mascot_anim.pxl --frames all --scale 8 --max-width 2400 --out docs/waves/w1/look/pet-all.png`; the same with `--scale 1` and `--scale 2` (`pet-1x.png`, `pet-2x.png`); per clip `--sprite <name> --frames all --scale 12 --out docs/waves/w1/look/pet-<name>.png` (the 15 clips); `--sprite pet_idle --sprite <clip> --frames first --scale 10 --out ...pet-vs-<name>.png` (15); for `scene_seasons.pxl`, `scene_props.pxl` (`--frames all --scale 6`) and `illustrations.pxl` (`--frames all --scale 6`): one sheet each on `--bg "#2B2B33"` (`-dark.png`) and one on `--bg "#F4EEE4"` (`-light.png`); `scene.pxl` `--scale 5` for comparison (`world-base.png`).
6. Report: counts and sizes, hashes, licence lines, anything skipped. DATA does not run Gradle, JVM tests or the phone.

### 7.2 LOOK (stage 3, a reviewer who did not write the art; looks at the PNGs, judges, cuts)
Per sheet, one line per sprite in `docs/waves/w1/look/review-<AREA>.txt`: `KEEP`, `FIX <what>` (reported, not done here) or `CUT <reason>`. A sprite that is not clearly better than what it replaces is cut (LEAD-DECISIONS).

| Sheet | Checklist (every answer yes, or cut) |
|---|---|
| Pêt clips (`pet-*.png`) | ART-PET 9 items 1 to 9: silhouette reads at 1x and 2x; eyes never drift; no stray pixel; feet on the ground line and hops 2 to 4 px; first and last frame are the rest pose and loops close; outline closed and light from the right on body and prop; the prop reads at 1x; no flicker (neighbour frames differ by at most 140 cells without a reason); not a copy of `pet_happy` or `pet_waving`. Also: `pet_shadow` grows to the left only; `pet_head` four faces read as neutral, blink, happy, worried; `pet_peek` six frames |
| Seasons (`world-seasons-*.png`) | only palette chars of the file; no vertical seam over 176 columns; the snow line moves in the order winter, spring, autumn, summer; the winter clearing around the fire is bare; the meadow reads in each season at scale 1 and 4 on both backgrounds; silhouettes equal `scene_far` and `scene_ground` |
| Props and woodpile | one object each; light from the lower right; woodpile frames 0 to 5 read as growing; frame-to-frame change small; nothing meets the fire's box |
| Tiles and empties | one object each, says what its screen is, plum outline visible on the dark and the light background, not a copy of Pêt, at most 7 or 10 colours |

What **cut** means in code: a sprite is deleted from its generator's list (`CLIPS` in `pet_clips.py`; the builder list in `craft_art.py`) and the file regenerated; `--check` and the tests then pass (`PetArt.clip` returns null; season, prop and tile fallbacks of 3.10 apply). LOOK edits nothing else in stage 3. LOOK-2 (after INTEGRATE, 7.4) may in addition change only the constants of 5.9 and delete sprites, then asks INTEGRATE to re-run the checks. The two **taste gates** are prepared by LOOK-2 as side-by-side pictures for the owner: the fire (`gate-fire-dark.png`, `gate-fire-light.png`: classic idle against living idle, working, roar) and the default sky (`gate-sky-dark.png`, `gate-sky-light.png`: dawn, day, dusk, night next to the classic night).

### 7.3 TRANSLATE (stage 4)
Inputs: the final English files of 3.12; `strings-check.ps1` parity errors as the work list; the key notes; the glossary rule of w0 section 6. Outputs: the `values-tr` and `values-fr` files of the six areas; regenerated `values-en-rXZ`. Checks as `w0-contract.md` 8.3 (`strings-check.ps1` exit 0, `make-test-language.ps1 -Check` exit 0 and `-SelfTest`). A second agent reads each language for length, placeholders and tone (report only).

### 7.4 LOOK-2: the stills and the gate pictures (after INTEGRATE has compiled)
Run once, after 7.5 steps 1 to 3 are green: `$env:SCENE_STILLS_DIR="<abs repo>\docs\waves\w1\look"; $env:FIRE_SHEET_DIR=$env:SCENE_STILLS_DIR; $env:WEATHER_STILLS_DIR=$env:SCENE_STILLS_DIR` then `... local-build.ps1 -Test` once; it writes `scene-*.png` (SCENE: the classic night; the seven moments at Istanbul on 2027-03-21 in a dark and a light palette; Oslo night with real stars and moon; the four seasons by day; the southern mirror; props per theme; `wood` 0 to 5), `fire-*.png` (FIRE: its four sheets), `weather-*.png` (WEATHER: one per look, day and night); at most 300 KB each. Then LOOK-2 reviews them (7.2) and composes the gate pictures from them with a throwaway Pillow script kept outside the repo; remove the three variables.

### 7.5 INTEGRATE (stage 5, one agent)
Commands, in this order (repo root; none installs on a phone; `pwsh` does not exist on this PC):
1. `powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1` (twin, compile), then `... -OwnerDebug`, then `... -Release` (R8: fill dontwarn lines, run `tools/ci/apk-size.sh`, write the measured `app_bytes` into `tools/size-budget.txt`: `owner_baseline_bytes` becomes the new value and `owner_ceiling_bytes` baseline + 1048576). Fix the seams (below) until the three build.
2. `... local-build.ps1 -Test` and `-Test -OwnerDebug` (exit 0 or 2 only; four symlink tests are the expected 2). Run once with `$env:FIRE_GOLDEN_WRITE="1"` to write `app/src/test/resources/fire/core-sprites.golden`, then once without; `Remove-Item Env:FIRE_GOLDEN_WRITE`.
3. `python tools/art/craft_art.py --check`, `--selftest`; `python tools/art/stars.py --verify`; `python tools/places/make_places.py --check`; `python tools/art/make_sounds.py --check`; `lint-ui.ps1 -Check`; `strings-check.ps1`; `make-test-language.ps1 -Check`; `-SelfTest` of `phone-test`, `phone-perf`, `lint-ui`; `sh -n` on every shell script. Ripgrep checks, each must give no hit: `rg --no-config "HearthTaps|HomeChrome|FirePrefs|SceneInput|FireLayers|PetLayers|SkyMode\.REAL|DayLists|PlaceWeatherSettings|mascot_life|scene_cloud|SceneRoles" app/src tools`; `openConnection` appears only in `HermesApi.kt`, `SupertonicModelStore.kt`, `WeatherStore.kt`.
4. The stills: see 7.4 (run after steps 1 to 3).
5. Edits INTEGRATE owns, spelled:
   - `J/ui/AppRoot.kt`: add `import dev.pocketagent.app.ui.pixel.HomeSky` after `import dev.pocketagent.app.ui.nav.robotRoot`; replace the two comment lines and the call above `// Links from notifications` by `// Home paints the real sky full-bleed behind the status bar: its icons follow the sky's brightness (HomeSky.iconsDark; the classic night keeps light icons). While the practice banner is on, the area behind the status bar is the banner's own deep background instead.` and `SystemStatusBarIcons(darkIcons = ((route != Route.Home || practiceOn) && !c.isDark) || (route == Route.Home && !practiceOn && HomeSky.iconsDark.collectAsState().value))` (`collectAsState` is already imported there).
   - `app/build.gradle.kts`: directly above the line `android {` add `val weatherContact: String = (providers.gradleProperty("weatherContact").orNull ?: System.getenv("AGIRI_WEATHER_CONTACT") ?: "").trim().replace("\\", "").replace("\"", "").take(120)`; in `defaultConfig` directly after `buildConfigField("int", "CI_RUN", "$ciRun")` add `buildConfigField("String", "WEATHER_CONTACT", "\"$weatherContact\"")`. No value is written in any committed file or CI step.
   - `.github/workflows/build.yml`: after the step "Generated pixel art is current" add a step "Data and art scripts pass their checks" with `run: |` and the lines `python3 tools/art/craft_art.py --selftest`, `python3 tools/art/stars.py --verify`, `python3 tools/art/stars.py --selftest`, `python3 tools/places/make_places.py --check`, `python3 tools/places/make_places.py --selftest`, `python3 tools/art/make_sounds.py --check`, `python3 tools/art/make_sounds.py --selftest` (`pet_clips.py --check` runs inside `craft_art.py --check`). Pushing a workflow change needs the lead's token with the `workflow` scope.
   - `NOTICE` (append, wording kept short; licence texts copied from the downloaded files through `stars.source.txt` and the DATA report, never from memory): (1) "Bright star table (HYG Database by David Nash; data from Hipparcos, the Yale Bright Star Catalog and the Gliese catalogue), licence `<line copied from stars.source.txt>`: app/src/main/assets/sky/stars.bin is a derived table shared under the same terms; script tools/art/stars.py." (2) "IANA Time Zone Database (public domain): zone coordinates derived by tools/places/make_places.py." (3) "Natural Earth populated places (public domain): offline city list derived by tools/places/make_places.py." (4) "MET Norway locationforecast (CC BY 4.0 and NLOD 2.0): fetched at run time only when the user turns the weather on; credit: Weather data: MET Norway; nothing of it is bundled." (5) "Sound cues. Twenty UI cues in app/src/main/res/raw (sfx_hearth_*.ogg, sfx_studio_*.ogg): romainsimon/uisfx, CC0 1.0 (as recorded in README.md; the exact upstream pack names are to be added by whoever can look them up)." (6) "Generated sound cues and loops (sfx_*_wake.wav, sfx_*_sleep.wav, sfx_loop_*.wav): written by tools/art/make_sounds.py from noise and sine waves; no third-party material; released under CC0 1.0 together with the script (the lead confirms the dedication)." If DATA had to use GeoNames, add its CC BY 4.0 block instead of (3).
   - `T/ScenarioFilesTest.kt`: add the tags of 3.11 to the known set.
6. May fix itself: Gradle and AGP spelling, imports, visibility, `@OptIn`, exhaustive `when`, init order, name clashes against 2.11 and 2.12 (keep the contract's name), the order of `DebugRequest` fields to 3.9, missing `internal`, argument renames forced by this contract, the golden file, the size numbers, regenerating `values-en-rXZ` after any English edit. Must not: change a registry signature, change behaviour or looks, edit `LegacyHearthReference.kt` or `SceneRendererParityTest.kt` to make parity pass, delete or weaken a test, touch `tools\phone-*.ps1`, `tools\scenarios\` or the phone, commit. Reports: every fix by file, every registry deviation, every test it could not make green in three tries (the failing assertion and file), every `LEFT`.

## 8. Proof (plan "Proven when", this half; the plan's Welcome and ten-languages items belong to Wave 1b)

Categories: **P** = owner-debug on the phone (pins and pictures only, `"safe": true`); **T** = needs the twin (waits for the owner's one "Install anyway" tap); **U** = unit test or CI; **E** = the owner's eyes. The phone's zone is assumed `Europe/Istanbul` (+03:00) for every clock written without an offset (open point 2).

| # | Item | Cat | How |
|---|---|---|---|
| 1 | pinned pictures of dawn, day, dusk and night in two themes (NEWROZ dark, NERGIZ light) | P | pins P02 to P08 |
| 2 | a winter day, rain, snow, a night of shooting stars | P | P12, P15, P13 |
| 3 | the star field of a pinned date and place against a planetarium: three bright stars and the moon by eye | P | P09 to P11 pictures; the lead compares with an offline planetarium; unit tests `starPlacesAtInstants` and `moonElongationAtKnownSyzygies` carry the numbers (U) |
| 4 | weather switch off: no request leaves the phone | P | `app.log` has no `Weather: request`, one `Weather: skipped NO_CONTACT`; `dumpsys netstats` shows no weather traffic; the switch node is absent |
| 5 | weather on: exactly one request an hour | U | `WeatherPolicyTest.oneRequestPerPlacePerHour`, `WeatherGuardTest`; a live check waits for a contact (not this half) |
| 6 | Home scrolls within budget with the scene alive; the scene costs under 1 ms a frame | P (debug, for the record) and T (R8, the claim) | `phone-perf` Home scroll and idle scenarios before and after, `storm` and `sky night` as comparisons |
| 7 | ten minutes on Home leave no sensor registered | P | `dumpsys sensorservice` during and after; tilt log lines; no drain figure is claimed (T) |
| 8 | a reviewer looked at every new sprite on a contact sheet | U | the files `docs/waves/w1/look/review-*.txt` exist and every sprite of the shipped files has a KEEP line (a unit test `Assume`s the files) |
| 9 | the owner passed the two taste gates | E | `gate-fire-*.png`, `gate-sky-*.png` and the phone pictures P01 to P08, P16 |
| 10 | Home 2.0: collapse, entrance, fire as switch (tap lights, hold puts out), labelled entries, Pêt and sky taps, suggestions, status states, text at 2.0 and mirrored | P (hold with no answer running), T (the confirmation with a running practice answer, swipe rows, loading, error and first-run states, chats) | P18 |
| 11 | Pêt reacts to rain, the hour, the fire, taps, events | P (pins and log lines), T (`MemorySaved`, `Failed`, `Approval`, `waitingResult`) | P17 |
| 12 | the sounds: WAKE, SLEEP, rain loop, crackle | P (log lines, ringer silent gives none), U (`SoundCuesTest`, `SoundFilesTest`); the lead plays the six files once on the PC | P20 |
| 13 | migration on the owner's phone keeps Newroz | P | read only: `run-as dev.pocketagent.app cat shared_prefs/scene_days.xml` shows `migrated` and `newroz` |
| 14 | appearance rows, the city sheet read only (never pick) | P | P19 |
| 15 | old data unchanged after the run | P | chats, bots and settings of `dist\owner-backup\` compared read-only (w0 1.2 G6) |

Pins (every link starts with `reset 1` and ends with the token the robot adds; `theme newroz --es dark dark` or `theme nergiz --es dark light` for the two themes; `appearance anim:off` for parity shots). Shots are at 1080 px width, route `home` unless said:

| Id | Pins | Check |
|---|---|---|
| P01 | `clock 2027-03-21T20:30 place 41.0,29.0,Istanbul sky night weather off appearance anim:off` | equals build 32 except the four dithered rows |
| P02..P08 | `sky follow`, same place, clocks `2027-03-21T06:30`, `T07:06`, `T13:00`, `T18:40`, `T19:16`, `T20:00`, `T21:00` | dawn, sunrise, day, golden, sunset, dusk, night; dark icons by day; log `I/Sky: mode=FOLLOW moment=` |
| P09 | `clock 2027-01-15T21:00+01:00 place 59.9,10.7,Oslo sky follow` | Sirius near (53, 47) on a 155 stage; moon by phase; Polaris not shown |
| P10 | `clock 2027-01-15T21:00+11:00 place -33.9,151.2,Sydney` | the strip faces north; the moon lit on the other side |
| P11 | P09 with the three stars and moon marked | planetarium comparison by eye |
| P12 | `sky follow place Istanbul clock 2027-01-15T13:00, 2027-04-10T13:00, 2027-07-15T13:00, 2027-10-15T13:00`; Sydney `2027-07-15T13:00+10:00` | four seasons; the southern flip shows winter |
| P13 | `clock 2027-08-12T23:30 days sky place Istanbul` | meteors every few seconds |
| P14 | `days sky,app,newroz` and `days sky,app` at `2027-03-21T21:00`; no `days` pin at `2027-07-15T22:30` | festive ridge fires; none; the owner's migrated quiet three |
| P15 | `weather` values of WEATHER 9 (`cloud:1..3`, `rain:light`, `rain:heavy,wind:0.6` and `-0.6`, `snow:light`, `snow:heavy,cloud:3`, `blizzard`, `fog:light`, `fog:dense`, `storm,flash`, `storm,flash:dim`, `drizzle` at `2027-03-21T18:30`) at `2027-01-15T22:00` and `2027-07-15T13:00` | each look; stars dim behind cloud; caps on ridges; tap on the sky shows "11°, light rain" and the credit |
| P16 | `fire` values of FIRE 9 (`classic`, `living,level:0`, `level:0.6`, `level:1`, `wind:0.8`, `wind:-0.8`, `light@0.5`, `douse@0.5`, `hold:0.6`, `haze:on`, `haze:off`) at `2027-03-21T20:30` | the fire pictures; `I/Fire: look`; node `home.fire` at least 48 dp |
| P17 | `pet <anim>:<mid frame>` for the 22 anims at `2027-01-15T19:00+01:00` Oslo NEWROZ dark; five again in NERGIZ light (`idle sleep shelter dance carry`); behaviours a to h of PET 9 with `weather rain:heavy,cloud:3`, `clock 2027-01-15T03:00+01:00`, `weather snow:light`, `pet classic` | `I/Pet: anim=` lines; a Pêt note swiped |
| P18 | Home at P02..P08 clocks; scroll 60, 160, 300 dp; `lang tr`, `lang fr`, `lang en-XZ`, font scale 2.0 (needs `-AllowSystemSettings`, the lead's), `rtl 1`; entrance frames after `reset 1` with no clock pin; `wood 0..5` | collapse, strip, ink contrast, mirror, six woodpile levels outside the fire's box |
| P19 | `route settings:appearance appearance black:1,haptics:subtle` | rows in the order of 3.7; `appearance.weather` absent; city sheet opened with `ist` typed (İstanbul listed); black page `#000000` in dark only |
| P20 | `theme zagros\|semender\|nergiz\|kawa\|roj` at `2027-03-21T20:30` and `2027-07-15T13:00` (ROJ also `T06:15`); `weather rain:heavy` with `appearance rain:1`; Hermes stopped then started from Home | props visible; `I/Sound: loop RAIN level=0.8`; `play WAKE` once at Running and `play SLEEP` once at Stopped, none at start |

## 9. Core and extras: one list, and the order of cutting

**Core (ships):** SCENE: clock, moments, palette, frame, real stars with fallback, projection, sun, moon, meteors, renderer pipeline, procedural seasons, calendar, store and migration, host, tint, dither; WEATHER: places, store, parse, guard, policy, client, engine, pin, look, clouds, rain, snow, fog, host; FIRE: feed, resolve, classic path, living edge, sparks, ring, geometry, driver, pin; PET: types, brain, layer, taps, controller, pin, note swipe; ART-PET: generator, manifest, shadow, `pet_blink`, `pet_tail`, `pet_yawn`, `pet_stretch`, `pet_read`, `pet_carry`, `pet_startled`, `pet_dance`, `pet_head`, `pet_small`, `pet_peek`; ART-WORLD: generator helpers and checks, season sprites, `scene_woodpile`, `prop_zagros`, `prop_nergiz`, four tiles, `empty_bubble`; HOME: H1 to H15; APPEAR: fields, pin, rows, black night, WAKE and SLEEP; DATA, LOOK, TRANSLATE, INTEGRATE.

**Cut order, first cut first** (a cut item keeps its symbol with an empty body or null):
1. FIRE E2 heat haze. 2. FIRE E1 tilt (and `FireSettingsRows`). 3. APPEAR crackle loop and row. 4. HOME X4 star names on sky tap, then X2, X3, X1. 5. PET `PetPeek`, then `pet_listen`. 6. WEATHER E5 stills dump, E4 hearth splashes, E2 lightning, E1 splashes, E3 snow caps. 7. SCENE E6 moonlight, E3 fireworks, E2 eclipses, E1 planets and star names. 8. APPEAR user days, then the rain loop and row, then black night in the theme cards. 9. ART-WORLD extras: `prop_roj`, `prop_kawa`, `prop_semender`, `tile_usage`, `tile_library`, `empty_gate`, the seven empties other than `empty_bubble`; then the season sprites of one season at a time (the procedural overlay remains); then `prop_zagros`, `prop_nergiz`. 10. ART-PET: `pet_poke`, `pet_warm`, `pet_firefly`, then `pet_snow`, `pet_shelter`, `pet_write`, then `pet_dance`, `pet_startled`, `pet_carry`, `pet_read`, `pet_stretch`, `pet_yawn`, `pet_tail`, `pet_blink` last. 11. PET: contextual quips (the four classic stay), the `FIREFLY`, `POKE`, `TAIL`, `WARM` idle options, the `SNOW` option; not built at all in this half: the outro frames of LOOP clips (the loop is left by a cut). 12. WEATHER clouds from three tones to two. 13. SCENE seasons down to winter and spring only; `MOON_GLOW`. 14. HOME skeletons (plain spacers), then the strip's cross-fade, then the entrance stagger. 15. FIRE side licks, LIGHT and DOUSE sprite steps (the hold ring stays), glow breathing. Never cut: anything section 3 does not mark cuttable, the classic path, the parity test, the guard, the pins, `FireFeed`, the Places API, `HearthInfo`, `HomeSky`.

## 10. Open points for the lead (only what could not be decided from the material)

1. **Collapse composition (3.3).** HOME's "fire at the start of the strip" and the parallax factors are not in SCENE's draft; the formulas of 3.3 are my reading (land moves down by the band, ridges by 0.6, sky by 0.35, ridge shift at least `landDy - 10` so no sky gap opens over the 12 overlapping rows). If LOOK-2 finds it ugly, the fallback is: SCENE ignores `collapse`, HOME only translates the hero and pins the strip. Say which you prefer if you have a view now.
2. **The phone's time zone.** Every pinned clock without an offset is read in the phone's zone; I assumed Europe/Istanbul (+03:00). `adb shell getprop persist.sys.timezone` (read only) settles it before P02.
3. **Source addresses and licences are from memory** (HYG file name and licence version, the Natural Earth GeoJSON path, the IANA fallback). DATA verifies the licence text in each download and stops on a mismatch; the HYG version (2.5 or 4.0) decides the NOTICE wording and whether "same terms" is exact.
4. **LOOK-2 after INTEGRATE.** Your order puts LOOK (3) before INTEGRATE (5), but every scene, fire and weather picture is a JVM test that needs the compile. Art sheets are judged at stage 3, code pictures at LOOK-2; accept or reorder.
5. **`pet_read`** is missing from ART-PET's timing table; 3.10 gives it (6 frames, 1400 ms, LOOP 1/4/1). Change it only if you want another rhythm.
6. **The `app` day list** (the anniversary of the first launch) is shown with a switch, default on; the `sky` list has no switch. The plan says the app's anniversary is on by default; the switch is my addition for people who do not want a paper hat.
7. **Dead strings.** `ui_home_mascot_cd` ("salamander") stays unused in `strings_chat.xml` because no 1a area owns it; the `apr_pet_*` row strings exist while the row is not composed (1b places it). Both are harmless; a later wave removes or uses them.
8. **Contact and terms.** The contact baked into the APK (plan decision 6) and the re-reading of MET's terms before the switch is ever shown stay yours (WEATHER request W6). WEATHER risk 8 (the terms, the endpoint, the credit wording, the `Retry-After` behaviour, the 203 reply, the symbol-code typos) is written from memory and the "to verify" list stands; so are the Natural Earth column names and the HYG column names (DATA checks both against the real files).
9. **Backup follow-up.** `BackupCore.PREF_SCHEMA` lacks `haptics` and every key of this half (appearance additions, `scene_days`, `place`, `weather`); a restore returns them to defaults. File it for the wave that next edits `BackupManager`.
