# Wave 1 survey: the state of Home, the pixel stage, the theme, the debug links and the engine

Inventory for the planners of "Wave 1, Home: the living hearth" (`docs/phase4-plan.md` lines 768 to 917).
Facts only, read from the files at HEAD `fe0e0ea` (working tree had one unrelated modified file,
`tools/phone-lib.ps1`, which was not touched). Paths are relative to the repository root. Java source
paths are shortened: `J/` means `app/src/main/java/dev/pocketagent/app/`. `~` before a line number means
the line was located by search, not read in full. Nothing was built, run or changed.

Totals verified: 112 sprites in 6 `.pxl` files (`app/src/main/assets/pixel/`); 60 `PixelIcon` values
and 60 `icon_*` sprites; 135 glyphs in the pre-Wave-0 pixel font golden file; 215 known contrast
failures in `PaletteContrastTest`.

---

## A. ui/home (Home)

Files: `J/ui/home/HomeScreen.kt` (454 lines), `J/ui/home/HomeParts.kt` (146), `J/ui/home/HomeScrim.kt` (62).

### Composables

| Symbol | Where | Signature | What it shows / reads |
|---|---|---|---|
| `HomeScreen` | HomeScreen.kt:98 | `fun HomeScreen()` (no parameters) | The whole Home tab. Reads `LocalNavigator`, `PocketApp.from(ctx)` (`app.chat` bots and conversations, `app.engine.busy`, `app.presencePrefs.settings`, `app.routineScheduler.nextRunAt`, `app.usage.days`, `app.runtime.isRootfsValid()`, `app.sounds`), `MainViewModel` (`vm.state`, `vm.start()`, `vm.stop()`, `vm.savedConfigIncomplete()`), `Pocket.colors`, `Pocket.motion`, `rememberHaptics()`. |
| `greeting` (private) | HomeScreen.kt:388 | `@Composable private fun greeting(): String` | Hour of day (Calendar): 5 to 11 morning, 12 to 17 afternoon, 18 to 21 evening, else night. |
| `StatusCard` (private) | HomeScreen.kt:402 | `(s: RuntimeState, onStart, onStop, onDetails)` | Title from `RuntimeState`, first-start hint, error reason (monospace, 3 lines max), progress bar, one button (Cancel while busy, Stop when Running, Start otherwise, plus Details on Error). |
| `PixelHeading` | HomeParts.kt:54 | `internal fun PixelHeading(text: String, modifier: Modifier = Modifier)` | `PixelText` big pixel title when `Pocket.appearance.pixelFontTitles`, else bold 24 sp system text. |
| `PixelProgressBar` | HomeParts.kt:65 | `internal fun PixelProgressBar(fraction: Float?, modifier: Modifier = Modifier)` | 12 dp canvas, 4 dp steps. `null` = indeterminate block sliding with `Loop.SLIDE`. Reads `Pocket.anim`. |
| `BotCard` | HomeParts.kt:90 | `internal fun BotCard(bot: Bot, onClick: () -> Unit, modifier: Modifier = Modifier)` | 96 dp card, `BotAvatar(bot, 48.dp)` and name. Label `ui_home_chat_with`. |
| `NewBotCard` | HomeParts.kt:106 | `internal fun NewBotCard(onClick: () -> Unit, modifier: Modifier = Modifier)` | 96 dp accent-bordered card, plus icon, `ui_home_new_bot`. |
| `HomeTile` | HomeParts.kt:121 | `internal fun HomeTile(icon: PixelIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null)` | Panel with icon 26 dp, label, optional muted subtitle. `fillMaxHeight` so a row of two matches. |
| `HomeStatusScrim` | HomeScrim.kt:41 | `internal fun BoxScope.HomeStatusScrim(listState: LazyListState)` | Box as tall as the status bar, drawn over the list; alpha from scroll position. Returns early when `practiceBannerShows()` (import from `J/ui/settings`). Colour from `hearthSkyTop(colors)` (`J/ui/pixel/HearthScene.kt:42`). |
| `statusScrimAlpha` | HomeScrim.kt:27 | `internal fun statusScrimAlpha(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, sceneSizePx: Int, statusBarPx: Int): Float` | Pure. 0 at top, linear to 1 when the scene's bottom reaches the status bar, 1 after the scene scrolls away. |
| `HearthScene` (used here) | `J/ui/pixel/HearthScene.kt:66` | see section B | Called at HomeScreen.kt:197 inside the first list item. |
| `NewChatSheet` (used here) | `J/ui/chat/NewChatSheet.kt` | opened when `showNew` (HomeScreen.kt:382) | Group chat creation from the "group" button. |
| `ConversationRow` (used here) | `J/ui/chat` | HomeScreen.kt:345 | Recent conversation row. |

### Local state inside HomeScreen (HomeScreen.kt:121 to 133)

- `quick` (rememberSaveable, the quick-talk text), `showNew`, `tapMood: MascotMood?`, `taps` (cumulative tap
  count, never reset), `streak` (taps inside the current 1.2 s chain), `lastTapMs`, `celebration`,
  `quipRes: Int?` (string resource id of the bubble), `stoke: Animatable<Float>`.
- Timers: `tapMood` cleared after 1900 ms (line 131); `quipRes` after 2400 ms (line 132); `celebration` after
  4200 ms (line 133).

### Derived values

- `hearth: HearthState` (HomeScreen.kt:141 to 146): `NotInstalled` and `Stopped` give COLD; `Extracting` and
  `Starting` give KINDLING; `Running` gives BLAZING; `Error` gives SMOKE.
- `sceneProgress` (147 to 151): Extracting gives `fraction * 0.9`; Starting gives 0.95; otherwise null.
- `canChat` (138 to 139): `inputAvailable(s, setupIncomplete, presenceSettings.wakeOnSend, runtimeInstalled)`.
- `todayTokens` (117 to 118): `app.usage.todayTotal()`, remembered on `usageDays`.
- `unreadTotal` (119): sum of `unread` over conversations.

### Layout today, top to bottom

The whole screen is `Box(fillMaxSize) { LazyColumn(...) ; HomeStatusScrim(listState) }` (HomeScreen.kt:159 to
380). The LazyColumn has `contentPadding = PaddingValues(bottom = Space.xl)` and
`verticalArrangement = spacedBy(Space.l)` (lines 160 to 165). Items in order:

1. `scene` (167 to 222): a Box with `contentDescription = ui_home_mascot_cd` and a clickable (the hearth tap,
   below). Inside: `HearthScene(hearth, Modifier.fillMaxWidth().height(280.dp), progress, mood = tapMood,
   stokeBoost = stoke.value, celebration)` (197 to 204), then the quip bubble (205 to 220): top-start,
   under the status bar, `surface` 92% alpha, border `fireHigh` when celebrating else `outline`, 12 sp text.
2. `status` (225 to 231): `PixelHeading(greeting())`, `Text(ui_home_tagline)`, `StatusCard(...)` with
   `onStart = vm.start()`, `onStop = vm.stop()`, `onDetails = Route.Settings("runtime")`.
3. `setup` (234 to 251), only when `setupIncomplete`: warning-bordered panel, KEY icon, `ui_home_setup_title`,
   `ui_home_setup_body`, `PixelButton(ui_home_setup_btn)` to `Route.Settings("provider")`.
4. `talk` (254 to 299): `SectionHeader(ui_home_talk_title)`; when `canChat`, a `PocketTextField` (placeholder
   `ui_home_talk_hint` when Running, else `ui_home_talk_hint_wake`) and a SEND `PixelIconButton`
   (`contentDescription = ui_home_send`). Send opens (or creates, if busy) the Hermes 1:1 chat and calls
   `app.engine.send`. On refusal `PetNotes.show(ui_send_not_accepted, ERROR)`. Otherwise a panel with
   `ui_home_talk_hint_off`.
5. `bots` (302 to 311): `SectionHeader(ui_home_bots)`; a LazyRow of `BotCard` per bot (to `startChat`, which
   opens a 1:1 chat) and a `NewBotCard` (to `Route.BotEditor(null)`).
6. `group` (313 to 318): `PixelButton(ui_home_group_btn, kind = SECONDARY)` sets `showNew = true`.
7. `recent` (321 to 349): header `ui_home_recent`; plural `ui_home_unread` when `unreadTotal > 0` (goes to
   `Route.Chats` tab); `ui_home_see_all` when more than 4 conversations; `ui_home_no_chats` when none; else the
   first 4 `ConversationRow`s (`convs.take(4)`).
8. `tiles` (352 to 376): row 1 `HomeTile(ui_home_tile_hermes)` to `Route.Hermes` and `HomeTile(ui_home_tile_terminal)`
   to `Route.Terminal`; row 2 `HomeTile(ui_home_tile_routines, subtitle = ui_home_routines_next(dayTimeLabel(nextRun))
   or ui_home_routines_none)` to `Route.Routines`, and `HomeTile(ui_home_tile_saved)` to `Route.Starred`;
   then, when `todayTokens > 0`, `ui_home_usage_today(compactTokens(...))` to `Route.Settings("usage")`.

Strings used by Home (all in `res/values/strings_chat.xml` unless noted; Home itself names no others):
`ui_home_mascot_cd`, `ui_home_quip_1`..`ui_home_quip_4`, `ui_home_quip_overdrive`, `ui_home_tagline`,
`ui_home_status_notinstalled`, `ui_home_status_extracting` (takes percent), `ui_home_status_slow`,
`ui_home_status_starting`, `ui_home_status_running`, `ui_home_status_stopped`, `ui_home_status_error`,
`ui_home_hint_first`, `ui_home_btn_cancel`, `ui_home_btn_stop`, `ui_home_btn_start`, `ui_home_btn_details`,
`ui_home_greet_morning`, `ui_home_greet_afternoon`, `ui_home_greet_evening`, `ui_home_greet_night`,
`ui_home_setup_title`, `ui_home_setup_body`, `ui_home_setup_btn`, `ui_home_talk_title`, `ui_home_talk_hint`,
`ui_home_talk_hint_wake`, `ui_home_talk_hint_off`, `ui_home_send`, `ui_send_not_accepted`, `ui_home_bots`,
`ui_home_chat_with` (takes name), `ui_home_new_bot`, `ui_home_group_btn`, `ui_home_recent`,
`ui_home_unread` (plural, `R.plurals`), `ui_home_see_all`, `ui_home_no_chats`, `ui_home_tile_hermes`,
`ui_home_tile_terminal`, `ui_home_tile_routines`, `ui_home_routines_next` (takes time), `ui_home_routines_none`,
`ui_home_tile_saved`, `ui_home_usage_today` (takes tokens). `ui_home_status_slow` is chosen when
`s.step.startsWith(RuntimeManager.STILL_STARTING)` (HomeScreen.kt:409).

Test tags on Home (`J/ui/RobotTags.kt`): `home.state` = `RobotTags.HOME_STATE` (RobotTags.kt:29, used on the
status title at HomeScreen.kt:417); `home.start` = `RobotTags.HOME_START` (RobotTags.kt:30, used on the Start
button at HomeScreen.kt:442); `stage.hearth` (literal, HearthScene.kt:98). The Home tiles, bot cards and
quick field carry no tags.

### The hearth tap and the 7-tap overdrive (HomeScreen.kt:167 to 195)

- The clickable is on the Box that wraps `HearthScene`; `indication = null`, `role = Role.Button`.
- Each tap: `now = currentTimeMillis`; `streak = streak + 1` if `now - lastTapMs <= 1200`, else 1;
  `taps++`.
- Seventh tap in a chain (`streak >= 7`): `streak = 0`, `celebration = true`, `tapMood = HAPPY`,
  `quipRes = ui_home_quip_overdrive`, haptic `HapticCue.HOLD`, sound `SoundCue.FORGE`.
- Any other tap: haptic `HapticCue.STOKE`, sound `SoundCue.SPARK`, `tapMood = WAVING` when `taps` is even
  else `HAPPY`, `quipRes = quips[(taps - 1) % 4]` (the four `ui_home_quip_1..4` ids, line 169).
- Both branches: `stoke.snapTo(1.2f)` then `stoke.animateTo(0f, motion.custom(Flourish.STOKE))`
  (Flourish.STOKE = 1200 ms, `J/ui/theme/Motion.kt:87`).
- Note for Wave 1: the overdrive is "7 taps with gaps of at most 1.2 s"; the parity counter `taps` keeps
  counting across overdrives. The comment at HomeScreen.kt:166 says the same.
- The scene receives the effects only as parameters: `mood`, `stokeBoost`, `celebration` (HearthScene
  parameters 70 to 72, HomeScreen.kt:200 to 203).

---

## B. ui/pixel and ui/pixel/stage

Files in `J/ui/pixel/`: Avatars.kt (83 lines), Fire.kt (88), HearthScene.kt (109), Mascot.kt (29),
PixelImage.kt (223), PixelStageBitmap.kt (19), SceneClock.kt (73), Sprite.kt (175), SpriteBank.kt (102),
SpriteLoader.kt (45), SpriteRaster.kt (41), StageSurface.kt (95).
Files in `J/ui/pixel/stage/`: Argb.kt (59), Embers.kt (63), FrameGate.kt (31), PixelStage.kt (110),
SceneArt.kt (89), SceneModel.kt (56), SceneRenderer.kt (331), StageGrid.kt (27).
The stage package is pure Kotlin (no Android types), with one exception noted below.

### Public types and functions (exact signatures)

Stage package:

- `class PixelStage(val width: Int, val height: Int)` (PixelStage.kt:10). `val pixels: IntArray`;
  `fun clear(argb: Int = 0)` (17); `fun plot(x: Int, y: Int, argb: Int)` (21); `fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int)` (27);
  `fun blit(src: IntArray, srcW: Int, srcH: Int, x: Int, y: Int, alpha: Float = 1f)` (44);
  `fun blit(sprite: Sprite, frame: Int, x: Int, y: Int, alpha: Float = 1f)` (68);
  `fun scaleInto(dst: IntArray, dstW: Int, dstH: Int, grid: StageGrid)` (78; whole-number nearest scale).
- `class StageGrid(val scale: Int, val width: Int, val height: Int, val offsetX: Int, val offsetY: Int)` (StageGrid.kt:10);
  companion `fun forScene(widthPx: Float, heightPx: Float): StageGrid?` (17): scale = ceil(width / 176),
  null below 8 px, centred horizontally, bottom-anchored.
- `class SceneRenderer(sprites: SpriteSource)` (SceneRenderer.kt:22); `fun render(stage: PixelStage, state: SceneState, timeMs: Long, quality: SceneQuality = SceneQuality.FULL)` (57);
  companion `const val ART_W = 176`, `const val GROUND_H = 34` (311 to 312), `val REQUIRED_SPRITES: List<String>` (326).
  Not thread-safe. Sprite lookups happen once in the constructor (23 to 33).
- `fun interface SpriteSource { operator fun get(name: String): Sprite }` (SceneModel.kt:8).
- `class ScenePalette(val skyTop: Int, val skyBottom: Int, val farRamp: IntArray, val glow: Int, val fireLow: Int, val fireMid: Int, val fireHigh: Int)` (SceneModel.kt:17). ARGB ints, content equality and hash.
- `data class SceneState(val hearth: HearthState, val palette: ScenePalette, val progress: Float? = null, val mood: MascotMood? = null, val stokeBoost: Float = 0f, val celebration: Boolean = false)` (SceneModel.kt:46).
- `enum class SceneQuality(val particles: Boolean) { FULL(true), PLAIN(false) }` (SceneModel.kt:56).
- `object SceneArt` (SceneArt.kt:20): `const val GLOW_LEVELS = 8` (21); `fun bayer(x: Int, y: Int): Float` (26);
  `fun sky(w: Int, h: Int, top: Int, bottom: Int): IntArray` (29); `fun stars(w: Int, g: Int): StarField` (47);
  `fun glow(level: Int, color: Int): GlowImage` (65). Classes `GlowImage` (SceneArt.kt:9) and `StarField` (SceneArt.kt:12).
- `internal class EmberSet(val capacity: Int, seed: Long)` (stage/Embers.kt:11): arrays spawnX, periodMs, phase, swayAmp, swayFreq, drift, big.
- `internal fun PixelStage.drawEmbers(set, count, t, originX, originY, halfWidthCells, riseCells, high, mid, low, strength)` (Embers.kt:40).
- `class FrameGate(val maxFps: Int = 30, val slackNanos: Long = 2_000_000L)` (FrameGate.kt:8); `fun accept(frameTimeNanos: Long): Boolean` (18); `fun reset()` (28).
- `object Argb` (Argb.kt:9): `pack(a, r, g, b)`, `alpha255(alpha: Float)`, `withAlpha(rgb: Int, alpha: Float)`, `scaleAlpha(argb: Int, alpha255: Int)`, `lerp(a: Int, b: Int, t: Float)`, `over(dst: Int, src: Int)`.

Pixel package:

- `enum class HearthState { COLD, KINDLING, BLAZING, SMOKE }` (HearthScene.kt:27).
- `internal fun hearthSkyTop(colors: PocketColors): Color` (HearthScene.kt:42): `bgDeep` in dark themes, `#130A16` in light.
- `internal fun hearthPalette(colors: PocketColors): ScenePalette` (HearthScene.kt:45): built from the theme colours, far-ridge ramp `FAR_RAMP` lerped 20% to the sky base (HearthScene.kt:39, 50).
- `@Composable fun HearthScene(state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null, stokeBoost: Float = 0f, celebration: Boolean = false)` (HearthScene.kt:66). Height is fixed at 280 dp inside (line 96). Tag `stage.hearth` (98). Only caller: HomeScreen.kt:197.
- `@Composable fun rememberSceneClock(active: Boolean, maxFps: Int = 30): LongState` (SceneClock.kt:30). Ticks with `withFrameNanos`, gated by `FrameGate(maxFps)`, runs only while the lifecycle is STARTED (`repeatOnLifecycle`), value is ms and resumes without jumping. Read it only inside draw lambdas.
- `@Composable fun rememberSpritesReady(): State<Boolean>` (SceneClock.kt:59): loads `SpriteBank` on `Dispatchers.Default` when not yet ready.
- `@Composable internal fun rememberClock(active: Boolean): LongState` (PixelImage.kt:177): wrapper of `rememberSceneClock`, used by `Embers`.
- `@Composable internal fun rememberSpriteFrame(sprite: Sprite, fixedFrame: Int?, playing: Boolean, offsetMs: Long = 0L): IntState` (PixelImage.kt:148).
- `@Composable fun PixelImage(sprite: Sprite, modifier: Modifier = Modifier, frame: Int? = null, tint: Color? = null, ramp: List<Color>? = null, playing: Boolean = true)` (PixelImage.kt:188).
- `@Composable fun PixelImage(name: String, modifier: Modifier = Modifier, frame: Int? = null, tint: Color? = null, ramp: List<Color>? = null, playing: Boolean = true)` (PixelImage.kt:212); calls `SpriteBank.init(LocalContext.current)` (220).
- `internal object SpriteBitmaps` (PixelImage.kt:41): `plain(sprite, frame)` (63), `get(sprite, frame, hasTint, tint, ramp): ImageBitmap` (72), LRU 8 MB (59).
- `internal fun fireSpriteName(intensity: Float): String` (Fire.kt:20): <0.15 `fire_out`, <0.45 `fire_low`, <0.8 `fire_mid`, else `fire_high`.
- `@Composable fun Fire(modifier: Modifier = Modifier, intensity: Float = 1f)` (Fire.kt:32). NO call site in app code (grep).
- `@Composable fun Embers(modifier: Modifier = Modifier, density: Float = 1f)` (Fire.kt:70). NO call site in app code (grep). Uses `EmberSet(40, 7L)`, 3 dp cells, 28 particles at density 1.
- `enum class MascotMood { IDLE, THINKING, TALKING, HAPPY, SLEEPING, ERROR, WAVING }` (Mascot.kt:9).
- `internal fun MascotMood.spriteName(): String` (Mascot.kt:12).
- `@Composable fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 96.dp)` (Mascot.kt:27).
- `enum class AvatarId` (Avatars.kt:20; NEWT, WOLF, FOX, OWL, EAGLE, GOAT, PARTRIDGE, BEE, LYNX, CAT, ROBOT, SUN, MOUNTAIN, HORSE, BEAR, DRAGON); `fun avatarIdOf(name: String?): AvatarId` (23); `internal fun AvatarId.spriteName()` (28) = `"avatar_" + lowercase`; `internal fun avatarRamp(color: Color): List<Color>` (33).
- `@Composable fun Avatar(id: AvatarId, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, animated: Boolean = false)` (Avatars.kt:62). Uses `SpriteBitmaps.get` with a per-colour ramp.
- `object SpriteBank : SpriteSource` (SpriteBank.kt:22): `val loadErrors: List<String>` (32), `val isReady: Boolean` (35), `fun init(context: Context)` (37), `fun preload(context: Context)` (49; starts a daemon thread named `pixel-preload`, line 60), `override fun get(name: String): Sprite` (69, throws IllegalArgumentException), `fun find(name: String): Sprite?` (73), `fun all(): Map<String, Sprite>` (76). Private `load` (84) reads `assets/pixel/*.pxl` sorted by name. `preload` is called from `J/PocketApp.kt:127`.
- `object SpriteLoader` (SpriteLoader.kt:7): `class Result(val sprites: Map<String, Sprite>, val errors: List<String>)` (8); `fun load(files: List<Pair<String, () -> String>>, onError: (file: String, e: Throwable) -> Unit = { _, _ -> }): Result` (15). A bad file is skipped as a whole; a duplicate name keeps the first.
- `class Sprite(val name: String, val width: Int, val height: Int, val frames: List<SpriteFrame>, val palette: List<PaletteEntry>)` (Sprite.kt:10); `val totalMs`; `fun frameAt(timeMs: Long): Int` (30); `fun msUntilNextFrame(timeMs: Long): Int` (43).
- `class SpriteFrame(val indices: IntArray, val durationMs: Int)` (Sprite.kt:7); `class PaletteEntry(val char: Char, val argb: Int)` (Sprite.kt:4).
- `class PxlException(message: String) : IllegalArgumentException(message)` (Sprite.kt:57).
- `object PxlParser` (Sprite.kt:71): `const val DEFAULT_FRAME_MS = 120` (74); `fun parse(fileName: String, text: String): List<Sprite>` (83).
- `object SpriteRaster` (SpriteRaster.kt:9): `fun argb(sprite: Sprite, frame: Int, hasTint: Boolean = false, tint: Int = 0, ramp: IntArray? = null): IntArray` (14); `fun plain(sprite: Sprite, frame: Int): IntArray` (34).
- `class StageSurface` (StageSurface.kt:25): ring of 3 bitmaps; `fun draw(scope: DrawScope, grid: StageGrid?, stamp: Any, render: (PixelStage) -> Unit)` (39); `fun release()` (81). `@Composable fun rememberStageSurface(): StageSurface` (91).
- `fun PixelStage.toBitmap(scale: Int = 1): Bitmap` (PixelStageBitmap.kt:12). The only `android.graphics` user in the stage path.
- `internal fun DrawScope.drawSpriteFit(img)` (PixelImage.kt:94), `drawSpriteAt(img, x, y, scale, alpha)` (111), `DrawScope.px(color, x, y, w, h)` (124).

### SceneRenderer.render: layer order (SceneRenderer.kt:57 to 185)

Drawn back to front, one pass, into a PixelStage at logical resolution. `g = stage height - GROUND_H` (34) is the
top of the ground; `artX = (width - 176) / 2`. Quality PLAIN skips every item marked FULL.

1. Sky: `sky(wl, g + 8, palette.skyTop, palette.skyBottom)` blitted at (0, 0) (line 73). Cached per size and palette.
2. Stars: `starField(wl, g)`, 46 stars, each twinkles from a sine of `t` (lines 74 to 86). Every 9th star with level above 0 gets a cross. Always drawn.
3. Shooting star (FULL only, lines 89 to 91): every 14 s, a 720 ms streak.
4. Moon halo (`drawMoonHalo`, line 96) and the moon sprite `scene_moon` at (0.76 * width, 20) (lines 94 to 97).
5. Far ridge: `scene_far` tinted by `palette.farRamp` (`SpriteRaster.argb` with a ramp, cached) at (artX, g - 38) (line 98).
6. Near ridge: `scene_near` at (artX, g - 14) (line 99).
7. Distant Newroz bonfires (FULL and BLAZING only, lines 100 to 102; `drawDistantNewrozFires`).
8. Ground: `scene_ground` at (artX, g) (line 103).
9. Fire glow: `SceneArt.glow(level, palette.glow)` blitted centred on (fx, fbase - 8), where fx = 0.6 * width and fbase = height - 3 (lines 105 to 139). Level from intensity and stoke boost; FULL adds a flicker.
10. Pêt: `pets[mood.ordinal]` at (fx - 43, fbase - 31) (lines 141 to 153). Mood: the `mood` parameter when set, else COLD SLEEPING, KINDLING THINKING, BLAZING IDLE, SMOKE ERROR. FULL and BLAZING without an override: HAPPY for the last 1400 ms of every 9000 ms (lines 148 to 150).
11. Fire: the `fire_*` sprite at (fx - 16, fbase - 35) (lines 154). COLD: `fire_cold`. KINDLING: by progress (under 0.15 `fire_out`, under 0.5 `fire_low`, under 0.85 `fire_mid`, else `fire_high`). BLAZING: `fire_high`. SMOKE: alternates `fire_low` and `fire_out` every 170 ms.
12. Smoke (FULL only, lines 157 to 163; `drawSmoke`): COLD 2 puffs, SMOKE 6 strong puffs, KINDLING 2 puffs only when progress under 0.3.
13. Embers (FULL only, lines 164 to 177): `stage.drawEmbers(embers, count, ...)`, pool of 40 (`EmberSet(40, 21L)`, line 34).
14. Fireflies (FULL only, BLAZING or KINDLING, lines 178 to 180; `drawFireflies`): 6 pulsing points over the meadow flanks.
15. Foreground grass: `scene_fore` at (artX, height - 11) (line 184).

Sprites the renderer asks for (constructor lines 23 to 33; `REQUIRED_SPRITES` at lines 326 to 329):
- `scene.pxl`: `scene_far`, `scene_near`, `scene_ground`, `scene_fore`, `scene_moon`.
- `fire.pxl`: `fire_cold`, `fire_out`, `fire_low`, `fire_mid`, `fire_high`.
- `mascot.pxl`: `pet_idle`, `pet_thinking`, `pet_talking`, `pet_happy`, `pet_sleeping`, `pet_error`, `pet_waving`
  (all seven `MascotMood` values, by `spriteName()`).

Fixed by the renderer: `ART_W = 176` (SceneRenderer.kt:311), so the scale is the smallest whole number that
covers the width (StageGrid.kt:19).

### What SceneState holds, and who builds it

- Built in `HearthScene` at `J/ui/pixel/HearthScene.kt:102` inside the `drawBehind` lambda, once per draw:
  `SceneState(state, palette, progress, mood, stokeBoost.coerceIn(0f, 1.5f), celebration)`.
- Palette: `remember(colors) { hearthPalette(colors) }` (HearthScene.kt:78). Recomputed only when the theme
  colours change.
- The time argument `t` is `clock.longValue` (HearthScene.kt:101), divided by 2 when `AnimLevel.REDUCED`; quality
  is FULL only when `AnimLevel.FULL`, else PLAIN (HearthScene.kt:103).
- So SceneState holds: the four-state `HearthState`, the palette, `progress` (KINDLING only), the tap mood
  override, the stoke boost (0 to 1.5) and the celebration flag. It holds no time of day, no fire level
  between the four states, no weather and no sky colour that depends on the clock.
- Who supplies these: HomeScreen (`hearth` from `RuntimeState`, `sceneProgress`, `tapMood`, `stoke.value`,
  `celebration`). No other caller of `HearthScene` exists in the app.
- `HearthScene` does not read `Appearance.showMascot`; Home shows the scene unconditionally (HomeScreen.kt:197).
  `showMascot` is read only at `ui/components/EmptyState.kt:43`, `ui/hermes/HermesScreen.kt:100`.

### Exceptions to "pure" in the stage package

- `stage/SceneRenderer.kt` imports `J/ui/pixel/HearthState`, `MascotMood`, `Sprite`, `SpriteRaster`, and
  the extension `spriteName`, all from the parent package (pure Kotlin, no Android).
- `stage/Argb.kt`, `PixelStage.kt`, `FrameGate.kt` and `StageGrid.kt` have no Android imports.
- Android code in the scene path: `HearthScene.kt` (Log, Compose), `SceneClock.kt` (lifecycle, Context),
  `StageSurface.kt` (Bitmap), `PixelStageBitmap.kt` (Bitmap).

---

## C. assets/pixel (the six .pxl files) and tools/art

### Header-level inventory (parsed from `app/src/main/assets/pixel/*.pxl`)

Format (parser `J/ui/pixel/Sprite.kt:83` to 165; described in `docs/phase1-contract.md`, section "Shared pixel
format"): `palette` block of `<char> <#AARRGGBB|none>` lines, then `sprite <name> <w> <h>`, then `frame [ms]`
blocks of exactly h rows of w characters, `.` = transparent. Names are `[A-Za-z0-9_]+` and unique across all
files. Default frame time 120 ms.

| File | Lines | Sprites | Sprite (w x h), frames, frame times (ms) |
|---|---|---|---|
| `appicon.pxl` | 900 | 19 | icon_fg 44x44 (1 frame, 1000); icon_bg 45x45 (1); icon_mono 44x44 (1); notif_flame 20x22 (1); icon_pet_fg 44x44; icon_pet_bg 45x45; icon_pet_mono 44x44; icon_flame_fg; icon_flame_bg; icon_flame_mono; icon_zagros_fg; icon_zagros_bg; icon_zagros_mono; icon_kawa_fg; icon_kawa_bg; icon_kawa_mono; icon_roj_fg; icon_roj_bg; icon_roj_mono (all single-frame, 1000 ms; the bg ones 45x45, the rest 44x44) |
| `avatars.pxl` | 835 | 16 | avatar_newt, avatar_wolf, avatar_fox, avatar_owl, avatar_eagle, avatar_goat, avatar_partridge, avatar_bee, avatar_lynx, avatar_cat, avatar_robot, avatar_sun, avatar_mountain, avatar_horse, avatar_bear, avatar_dragon: each 24x24, 2 frames, times 3100, 170 |
| `fire.pxl` | 1023 | 5 | fire_cold 32x36, 1 frame (1000); fire_out 32x36, 4 frames (260 x 4); fire_low 32x36, 6 frames (130 x 6); fire_mid 32x36, 8 frames (110 x 8); fire_high 32x36, 8 frames (95 x 8) |
| `icons.pxl` | 845 | 60 | 12x12, 1 frame each (1000): icon_hearth, icon_chats, icon_bots, icon_scroll, icon_terminal, icon_gear, icon_send, icon_stop, icon_plus, icon_back, icon_more, icon_copy, icon_edit, icon_trash, icon_pin, icon_group, icon_tool, icon_memory, icon_skill, icon_soul, icon_search, icon_refresh, icon_play, icon_check, icon_close, icon_palette, icon_globe, icon_bug, icon_down, icon_up, icon_spark, icon_share, icon_user, icon_key, icon_info, icon_mic, icon_clip, icon_camera, icon_image, icon_file, icon_bolt, icon_forge, icon_volume, icon_mute, icon_fork, icon_sliders, icon_filter, icon_appicon, icon_wave, icon_music, icon_clock, icon_star, icon_star_off, icon_bell, icon_chart, icon_archive, icon_download, icon_upload, icon_moon, icon_link |
| `mascot.pxl` | 1751 | 7 | pet_idle 32x32, 8 frames (560, 260, 520, 110, 380, 420, 500, 110); pet_thinking, 7 frames (360, 140, 300, 110, 300, 140, 300); pet_talking, 8 frames (110 each); pet_happy, 9 frames (130, 90, 100, 120, 90, 90, 120, 360, 200); pet_sleeping, 6 frames (700, 500, 600, 500, 500, 700); pet_error, 6 frames (280, 200, 120, 260, 300, 220); pet_waving, 8 frames (140 x 6, 120, 260) |
| `scene.pxl` | 173 | 5 | scene_far 176x46 (1 frame, 1000); scene_near 176x26 (1); scene_ground 176x34 (1); scene_fore 176x11 (1); scene_moon 15x15 (1) |

Total: 112 sprites (19 + 16 + 5 + 60 + 7 + 5). Every `icon_*` name matches a `PixelIcon` enum value
(`J/ui/components/PixelIcon.kt:25` to 34; 60 values, `spriteName = "icon_" + lowercase`).

### Who uses each sprite

- `pet_*` (7): `J/ui/pixel/Mascot.kt:12` to 19 and `Mascot()` (27), used at `J/ui/components/DesignGallery.kt:188`
  to 190, `J/ui/components/EmptyState.kt:44`, `J/ui/components/PetNote.kt:351` (mood map at 251 to 255),
  `J/ui/hermes/HermesScreen.kt:101`, `J/ui/settings/RuntimeSection.kt:217`. And by `SceneRenderer` (all seven).
- `fire_*` (5): `SceneRenderer` (all five). `Fire.kt` maps `fire_out/low/mid/high` in `fireSpriteName` (Fire.kt:20 to 24),
  but `Fire()` has no call site.
- `scene_*` (5): `SceneRenderer` only.
- `avatar_*` (16): `Avatar()` (Avatars.kt:62) via `avatarIdOf(bot.avatar)` and `AvatarId.spriteName()`. Call sites:
  `J/ui/chat/ChatCommon.kt:93` (`BotAvatar`), `J/ui/bots/BotEditorScreen.kt:215, 227, 426`,
  `J/ui/bots/BotsScreen.kt:165, 196`.
- `icon_*` (60): `PixelIcon` (components/PixelIcon.kt:33), used by `PixelIconView` everywhere (for example
  `J/ui/home/HomeParts.kt:136`, `J/ui/chat/ChatMessages.kt:397`).
- `icon_<theme>_fg` and `icon_<theme>_bg` (12 for six themes): `J/ui/theme/AppIconManager.kt:17` to 22 (NEWROZ, PET,
  FLAME, ZAGROS, KAWA, ROJ). Also used for the settings preview (`J/ui/settings/AppearanceSection.kt:179` to 180;
  `bgSprite`/`fgSprite` at AppIconManager.kt:15 to 16).
- `icon_*_mono` (6): not used by Kotlin. The launcher XML `res/drawable/ic_launcher_*monochrome.xml` (generated by
  `tools/pxl-to-vector.ps1`) carries them.
- `notif_flame`: `res/drawable/ic_notification.xml` (generated).

### tools/art

Files: `tools/art/craft_art.py`, `tools/art/contact_sheet.py`, `tools/art/pxl.py` (strict reader, the Python twin
of `PxlParser`), `tools/art/README.md`. Also `tools/pixel-preview.ps1` and `tools/pxl-to-vector.ps1` (PowerShell,
older tools).

`craft_art.py` (1051 lines) generates three files:
- `fire.pxl` from `build_fire_pxl` (line 268; output at 318). Sprites: fire_cold, fire_out, fire_low, fire_mid, fire_high.
- `scene.pxl` from `build_scene_pxl` (line 363; output at 536). Sprites: scene_far, scene_near, scene_ground, scene_fore, scene_moon.
- `appicon.pxl` from `build_appicon_pxl` (line 915; output at 972). Sprites: the 18 icon_* launcher sprites and notif_flame (19 with the icon_fg/bg/mono triples). Per-theme drawers `draw_icon_newroz` (647), `draw_icon_pet` (722), `draw_icon_flame` (750), `draw_icon_zagros` (795), `draw_icon_kawa` (837), `draw_icon_roj` (883).
- Registry `BUILDERS` (lines 988 to 990). CLI (`main`, line 1027): `--only {fire,scene,appicon}`, `--out DIR` (writes elsewhere), `--check` (writes nothing; prints `ok <file>` or `DIFF <file>`; exit 1 on a difference; this is what CI runs).
- Commands (from `tools/art/README.md`):
  - `python tools/art/craft_art.py` (writes the three files; prints `Wrote <path> (<n> sprites)` and `Art pass v2 complete!`)
  - `python tools/art/craft_art.py --only fire`
  - `python tools/art/craft_art.py --out dist/art-test`
  - `python tools/art/craft_art.py --check`
- Uses `math.sin` and fractional powers; README notes platform rounding risk.

`contact_sheet.py` (164 lines, needs Pillow): a viewer, not a source. It renders a PNG of sprites to
`dist/art/contact-sheet.png` by default. Options (`main`, line 67 onward): `--file` (a `.pxl` or a folder;
default the app assets), `--max-width` (default 2400), `--scale` (default 4), `--frames first|all`,
`--sprite NAME` (repeatable), `--bg` (default `#2B2B33`), `--ramp "#c1,#c2,#c3,#c4"` (tint for palette chars 1 to 4), `--out`.
Example commands (README): `python tools/art/contact_sheet.py` (all 112, first frame);
`python tools/art/contact_sheet.py --file app/src/main/assets/pixel/fire.pxl --frames all --scale 6`;
`python tools/art/contact_sheet.py --sprite pet_idle --sprite pet_happy --scale 8 --ramp "#3B2A5A,#5E448C,#8A6AC8,#BFA4F2"`.

Which sheets have no generator: `avatars.pxl`, `mascot.pxl` and `icons.pxl` are hand-edited (tools/art/README.md
table). The contact sheet is a viewer, so it has no generator either.

Adding a sprite (README): hand-edited files get a block; generated files change `craft_art.py` and run it.
`PxlParserTest` checks that every shipped file parses and names are unique.

---

## D. The mascot (Pêt) today

Pêt is a newt, drawn 32 x 32 (`J/ui/pixel/Mascot.kt:22`: "Pet ... the Kurdistan newt"; the About string says
"PÊT, THE NEWT", `res/values/strings_control.xml:214`).

### Poses and animation (`mascot.pxl`, sprite names in `MascotMood`)

| Mood | Sprite | Frames, times (ms) |
|---|---|---|
| IDLE | pet_idle | 8 frames: 560, 260, 520, 110, 380, 420, 500, 110 |
| THINKING | pet_thinking | 7 frames: 360, 140, 300, 110, 300, 140, 300 |
| TALKING | pet_talking | 8 frames: 110 each |
| HAPPY | pet_happy | 9 frames: 130, 90, 100, 120, 90, 90, 120, 360, 200 |
| SLEEPING | pet_sleeping | 6 frames: 700, 500, 600, 500, 500, 700 |
| ERROR | pet_error | 6 frames: 280, 200, 120, 260, 300, 220 |
| WAVING | pet_waving | 8 frames: 140 x 6, 120, 260 |

`TALKING` is only mapped in `Mascot.kt:15` and loaded by `SceneRenderer.pets` (all seven moods are loaded); no code path selects `MascotMood.TALKING` (grep).
Animation follows `Pocket.anim` via `rememberSpriteFrame` (PixelImage.kt:148 to 175; REDUCED is slower; OFF is frame 0).

### Where Pêt is drawn

- Home hearth scene: `SceneRenderer.render` step 10 (section B), mood by hearth state (COLD SLEEPING, KINDLING
  THINKING, BLAZING IDLE, SMOKE ERROR), an overridden mood on tap (WAVING on even taps, HAPPY on odd, HAPPY on
  the overdrive), and a HAPPY hop every 9 s when FULL.
- About (Settings, `J/ui/settings/RuntimeSection.kt:210`): `Mascot(if (petTaps >= 5) HAPPY else WAVING, size = 80.dp)`
  (217 to 220), tap increments `petTaps` (220). Five taps reveal the stats (`ctl_about_secret_title`,
  `ctl_about_secret_body`, lines 227 to 234).
- Settings Runtime status: `J/ui/settings/RuntimeSection.kt` (the Mascot near line 217 is inside About; the
  runtime status mascot is `J/ui/hermes/HermesScreen.kt:100` to 108): IDLE Running, THINKING Starting/Extracting,
  ERROR on Error, else SLEEPING; 56 dp; shown when `appearance.showMascot`.
- Empty states: `J/ui/components/EmptyState.kt:43` to 44: IDLE, 96 dp, when `showMascot`.
- Notes (`J/ui/components/PetNote.kt`): every Pêt note draws a 32 dp mascot (line 351). Mood by note kind:
  INFO IDLE, SUCCESS HAPPY, ERROR ERROR (251 to 255).
- Design gallery: `J/ui/components/DesignGallery.kt:188` to 190 (IDLE, HAPPY, ERROR at 64 dp).

### Quips and where their texts live

All in `res/values/strings_chat.xml` (values-tr and values-fr hold translations; not inspected here):
- Hearth tap bubbles: `ui_home_quip_1` (line 225), `ui_home_quip_2` (226), `ui_home_quip_3` (227), `ui_home_quip_4` (228). Chosen by `quips[(taps - 1) % 4]` in HomeScreen.kt:169 and 189.
- Overdrive bubble: `ui_home_quip_overdrive` (229), HomeScreen.kt:182.
- Hearth accessibility label: `ui_home_mascot_cd` (line 47): "Pêt, the salamander. Tap to say hello." **Inconsistent**: the rest of the app calls Pêt a newt; this string says salamander.
- Slash command `/pet`: `ui_cmd_pet_quip1` (216, takes bot count), `ui_cmd_pet_quip2` (217), `ui_cmd_pet_quip3` (218, takes message count); chosen in `J/ui/chat/ChatSlashActions.kt:145` to 147.
- Pet notes use free text from the call site (`PetNotes.show(...)`), not a quip table.

---

## E. ui/theme

Files: `J/ui/theme/`: Appearance.kt (112 lines), AppIconManager.kt (64), Haptics.kt (95), Motion.kt (190), Palettes.kt (413), PixelFont.kt (324), PixelIndication.kt (55), PocketTheme.kt (205), SystemMotion.kt (95), Tokens.kt (105).

### Appearance (Appearance.kt)

- Enums: `ThemeId { NEWROZ, ZAGROS, SEMENDER, NERGIZ, KAWA, ROJ }` (line 10); `AccentId { THEME, FLAME, SAFFRON, MEADOW, SKY, ROSE, NEWT, SNOW }` (13); `DarkMode { SYSTEM, DARK, LIGHT }` (16); `AnimLevel { FULL, REDUCED, OFF }` (18); `BubbleStyle { PIXEL, SOFT }` (20).
- `@Immutable data class Appearance` (22 to 35), fields and defaults:
  - `theme: ThemeId = NEWROZ`
  - `accent: AccentId = THEME`
  - `darkMode: DarkMode = SYSTEM`
  - `textScale: Float = 1f` (clamped 0.85 to 1.3 by `sanitize`, line 84 to 87)
  - `animations: AnimLevel = FULL`
  - `showMascot: Boolean = true`
  - `bubbleStyle: BubbleStyle = PIXEL`
  - `language: String = ""` (`""`, `"en"`, `"tr"`, `"fr"`)
  - `pixelFontTitles: Boolean = true`
  - `haptics: HapticLevel = HapticLevel.FULL` (line 34)
- `class AppearanceStore private constructor(context)` (41 to 112). `val appearance: StateFlow<Appearance>` (46); `fun update(change: (Appearance) -> Appearance)` (48). `companion fun get(context: Context): AppearanceStore` (107), singleton.
- Storage: SharedPreferences named `"appearance"` (PREFS, line 93). Keys: `theme`, `accent`, `darkMode`, `textScale` (float), `animations`, `showMascot` (boolean), `bubbleStyle`, `language`, `pixelFontTitles` (boolean), `haptics` (lines 94 to 103). Enum values stored by `name`; unknown names fall back to the default (`parse`, line 89).
- Read in composition: `Pocket.appearance` (`LocalAppearance`, PocketTheme.kt:38, 45).

### Palettes (Palettes.kt)

- `@Immutable data class PocketColors` (Palettes.kt:16 to 28), roles:
  `isDark`, `bg`, `bgDeep`, `surface`, `surfaceHigh`, `outline`, `text`, `textMuted`, `accent` (fill, paired with
  `onAccent`), `accentSoft` (text/icon accent, at least 4.5:1), `onAccent`, `fireLow`, `fireMid`, `fireHigh`,
  `ember`, `userBubble`, `onUserBubble`, `botBubble`, `onBotBubble`, `toolBubble`, `onToolBubble`, `codeBg`,
  `error`, `success`, `warning`, and derived `glow` (default `fireMid`) and `scrim` (default black in dark,
  `#2A1A24` in light).
- `fun paletteFor(theme: ThemeId, dark: Boolean, accent: AccentId): PocketColors` (34 to 58). Base palette
  per theme and variant, then an optional accent override.
- Base palettes (private `object Palettes`, line 62): newrozDark (63), newrozLight (91), zagrosDark (119),
  zagrosLight (147), semenderDark (175), semenderLight (203), nergizDark (231), nergizLight (259), kawaDark (287),
  kawaLight (315), rojDark (343), rojLight (371). Accent sets (399 to 412): flame, saffron, meadow, sky, rose,
  newt, snow, each dark and light, each with accent, soft, onAccent, user bubble and text on the bubble.
- Documented contrast promise (Palettes.kt:31 to 33): text at least 7:1 on bg; body and muted text at least 4.5:1
  on every surface; onAccent at least 4.5:1; bubbles at least 4.5:1. Enforced by `PaletteContrastTest` (section K).

### Tokens (Tokens.kt)

- `object Space` (20 to 34): `xxs` 2 dp, `xs` 4, `s` 8, `m` 12, `l` 16, `xl` 24, `xxl` 32, `gutter` 16 (line 30), `touch` 48 (line 33).
- `object Corner` (37 to 41): `chip` 1, `card` 2, `sheet` 3 (Int).
- `object Border` (44 to 47): `thin` 1, `focus` 1 (Int).
- `object Type` (50 to 56): `TextStyle`s `title` (16 sp, semibold), `body` (15), `reading` (15.5), `label` (12, medium), `mono` (13, monospace).
- `object PixelType` (59 to 63): `small` 1.5 dp, `title` 2 dp, `big` 3 dp.
- `enum class Elevation { FLAT, RAISED, FLOATING, MODAL }` (66).
- Functions: `fun designUnitPx(density: Float): Int` (72); `fun Density.u(steps: Int = 1): Float` (75); `fun snapToDesignPx(valuePx: Float, unitPx: Int): Float` (78); `fun pixelFontPx(pixelDp: Float, density: Float, fontScale: Float, follow: Boolean): Int` (89); `fun bevelRim(face: Color, dark: Boolean): Color` (102); `fun bevelShade(face: Color, dark: Boolean): Color` (105).

### Motion (Motion.kt)

- `object Dur` (33 to 39): `TAP` 90, `FAST` 150, `BASE` 220, `SLOW` 320, `SCENE` 480, `EPIC` 900 (ms, const).
- `object Ease` (42): `standard`, `enter`, `exit` (cubic beziers), `fun steps(n: Int): Easing` (48).
- `object Springs` (61): `SNAPPY_*`, `GLIDE_*`, `BOUNCY_*` constants; `fun <T> snappy()`, `glide()`, `bouncy()`.
- `object Loop` (75 to 82): `GLOW` 1700, `TYPING` 1000, `CARET` 520, `EMBER` 700, `SLIDE` 1100, `SHIMMER` 1400, `EMBER_LINE` 1200 (ms).
- `object Flourish` (86 to 88): `STOKE` 1200, `SHOWER` 2400 (ms).
- `fun effectiveAnimLevel(setting: AnimLevel, animatorScale: Float, powerSave: Boolean): AnimLevel` (95): NaN or <= 0 animator scale gives OFF; power save with FULL gives REDUCED; else the setting.
- `class MotionScheme(val level: AnimLevel)` (107): `fun <T> tap()`, `fast()`, `base()`, `slow()`, `scene()`, `glide()`, `snappy()`, `bouncy()`, `pop()` (131), `custom(durationMs: Int, easing: Easing = Ease.standard)` (138), `loop(...)` (144); `val loops`, `particles`, `parallax`, `shaders` (FULL only); `val sceneFps: Int` (160 to 166: FULL 30, REDUCED 10, OFF 0).
- `val LocalAnimLevel` (174), `val LocalMotion` (175), `val Pocket.motion: MotionScheme` (178), `fun Modifier.pixelOffset(pixel: Dp = 2.dp, offset: Density.() -> Offset)` (185).

Animation level chain: `rememberSystemMotion()` (SystemMotion.kt:48) gives `SystemMotionState(animatorScale, powerSave)`
(line 26); `PocketTheme` computes `effectiveAnimLevel(appearance.animations, system.animatorScale, system.powerSave)` (PocketTheme.kt:78 to 80) and provides `MotionScheme(level)`.

### Haptics (Haptics.kt)

- `enum class HapticCue { TICK, CLICK, SEND, RISE, DOUBLE, HOLD, STOKE, PULSE }` (16).
- `enum class HapticLevel { OFF, SUBTLE, FULL }` (19). Stored in `Appearance.haptics`.
- `enum class HapticKind` (22), `class HapticPlan(val kinds: List<HapticKind>, val gapMs: Long)` (27).
- `object Haptics` (29): `fun planFor(cue: HapticCue, level: HapticLevel, sdk: Int): HapticPlan?` (33), `fun play(view: View, cue: HapticCue, level: HapticLevel = FULL)` (55).
- `class HapticPlayer internal constructor(view, level)` (84): `fun play(cue: HapticCue)` (85).
- `@Composable fun rememberHaptics(): HapticPlayer` (91): reads `Pocket.appearance.haptics` through `rememberUpdatedState`.

### Pocket and the theme root (PocketTheme.kt)

- `val LocalPocketColors` (37), `val LocalAppearance` (38).
- `object Pocket` (41): `val colors: PocketColors` (42), `val appearance: Appearance` (45), `val anim: AnimLevel` (48).
- `@Composable fun PocketTheme(appearance: Appearance, content: @Composable () -> Unit)` (59): dark decision (61 to 65), palette (66), Material scheme (69), density (73 to 76), motion (78 to 80).
- `@Composable fun SystemStatusBarIcons(darkIcons: Boolean)` (192).

### PixelFont (PixelFont.kt)

- `class PixelGlyph(val width: Int, val rows: Array<String>)` (9). `object PixelFont` (24): `ROWS` 12, `CAP_TOP` 3, `CAP_HEIGHT` 7, `X_TOP` 5, `BASELINE_ROW` 10, `SPACE_WIDTH` 3, `SPACING` 1 (lines 25 to 33).
- `fun glyph(c: Char): PixelGlyph?` (37; space is a special 3-wide glyph). `fun prepare(text: String): String` (42; maps typographic characters, see below). `fun lineCols(line: String): Int` (57). `fun textCols(text: String): Int?` (73; null when a glyph is missing). `fun kern(prev: Char, next: Char): Int` (92). `val KERN_PAIRS` (95): AT, LT, LV, LY, TA, FA, T., T,, Y., V., r., r,.
- Substitutions in `prepare` (MAP, lines 103 to 107): `‘` and `’` to `'`; `‚` to `,`; `“` and `”` to `"`; `–`, `—`, `−` to `-`; `…` to `...`; space.
- Glyph set (defined in `init` at lines ~150 to 300, `defV2()` at 304 to 323). Verified by the golden file: 135 glyphs in the v1 set (`app/src/test/resources/pixelfont-v1.golden`):
  - Capitals A to Z (lines 153 to 178).
  - Digits 0 to 9 (181 to 190).
  - Lowercase: ascenders b d f h k l i j t; x-height a c e m n o r s u v w x z and dotless `ı` (216); descenders g p q y (218 to 221).
  - ASCII punctuation: `! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ \` { | } ~` (223 to 256), plus `•` (256), `·` (257), `→` (258), `←` (259), `×` (260), `✓` (261).
  - Accented capitals (264 to 280): Ç Ş Ğ Ü Ö İ Ê Î Û É È À Â Ô Ù Ë Ï. (Ÿ is a v2 addition.)
  - Accented lowercase (282 to 297): ç ş ğ ü ö ê î û é è à â ô ù ë ï.
  - v2 additions (Wave 0): Æ, æ, Œ, œ (307 to 309), Ÿ, ÿ (311 to 312), guillemets « and » (315 to 316), € £ ° ± ↑ ↓ (319 to 323). Source: `defV2` lines 304 to 323.
- The golden test protects the 135 v1 glyphs only.

---

## F. The clock and the debug links (Wave 0)

### AppClock (`J/AppClock.kt`, object at line 18)

- `val pinned: StateFlow<Long?>` (backed by a MutableStateFlow, null = real time).
- `fun nowMillis(): Long` (pinned instant or `System.currentTimeMillis()`).
- `fun now(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime`.
- `fun pin(epochMillis: Long)`, `fun unpin()` (only the debug link calls them).
- `fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Long?` (pure; accepts offset forms or local date-time).
- The KDoc says the home sky would read it (AppClock.kt:10 to 12), but **no production code reads `AppClock.now` or `nowMillis`**. `J/` grep: only `DebugLinks.kt` (171, 227, 233, 234) and `AppClockTest.kt` use it.

### DebugOverrides (`J/DebugOverrides.kt`, object at line 13)

- `val language: StateFlow<String?>`, `val forceRtl: StateFlow<Boolean?>`.
- `fun setLanguage(tag: String?)` and `fun setForceRtl(value: Boolean?)`: both no-ops unless `BuildConfig.TEST_HOOKS`.
- `fun clear()`.
- Read by `J/ui/AppRoot.kt:138` to 139 (language and direction overrides). In memory only.

### DebugLinks (`J/DebugLinks.kt`, object at line 61)

- Action: `AppLinks.ACTION_DEBUG = "dev.pocketagent.app.DEBUG"` (`J/AppLinks.kt:31`). Addressed to MainActivity by name, no manifest filter.
- Extras (constants at DebugLinks.kt:62 to 73): `token` (`EXTRA_TOKEN`, required; checked against `files/debug-link.token`, `TOKEN_FILE` line 63), `route` (67), `clock` (68), `lang` (69), `rtl` (70), `reset` (71), `seed` (72), `nav` (73).
- Entry points: `ensureToken(filesDir, debug)` (81; called from `J/PocketApp.kt:116`), `parse(intent, tokenFile, debug, zone): DebugRequest?` (111; called from `J/MainActivity.kt:66`), `parseParts(action, extras, flags, debug, zone, isTwin): DebugRequest?` (133; pure), `apply(app, req): Route?` (223; called from `MainActivity.kt:111`), `stackFor(route): List<Route>` (269; `AppRoot.kt:351`).
- `data class DebugRequest(reset, route, clock: Setting<Long>, language: Setting<String>, rtl: Setting<Boolean>, seed: String?, nav: String?, rejected: List<String>)` (DebugLinks.kt:25 to 35). `sealed Setting` (Keep, Clear, Set) at lines 16 to 18.
- `apply` order (kdoc at 218 to 222 and code 223 to 249): reset (AppClock.unpin, DebugOverrides.clear, NavFlags.reset), clock, language, rtl, nav flags, seed. `seed` is honoured only by the twin build (`isTwin`, parameter at line 139, check at line 206).
- Extras read in `parse` (line 126): only the seven names listed. `parseParts` reads them through `text(name)` (line 146).

### Adding a new debug extra (for Wave 1 if a link is needed)

1. Add the constant next to `EXTRA_NAV` (DebugLinks.kt:67 to 73).
2. Add the name to the `listOf(...)` at DebugLinks.kt:126, or the intent extra is never read.
3. In `parseParts` (133 to 215) read it with `text(name)`, validate, and add the name to `rejected` when bad (pattern: the clock block at 166 to 172, the seed block at 204 to 206, the nav block at 210 to 212).
4. Add a field to `data class DebugRequest` (25 to 35) and pass it in the `return DebugRequest(...)` at DebugLinks.kt:215.
5. Apply it in `apply()` (223 to 249) in the documented order.
6. Document it in the kdoc block (DebugLinks.kt:35 to 60) and the unit tests (`app/src/test/.../DebugLinksTest.kt`).

### NavFlags (`J/ui/nav/NavFlags.kt`, 80 lines)

- `internal data class NavFlagChange(val back: Boolean?, val shared: Boolean?, val reset: Boolean)`.
- `internal fun parseNavFlags(text: String?): NavFlagChange?`: keys `back`, `shared`, `reset`; values 1/0, true/false, on/off; null for blank or unknown.
- `internal object NavFlags`: `@Volatile var backGestureDrivesScreen = false`, `@Volatile var sharedElements = true`, `val revision: MutableIntState`, `fun sharedElementsOn(): Boolean`, `fun reset()`, `fun apply(change: NavFlagChange)`. Changed only through the debug link's `nav` extra.

---

## G. ChatEngine and the state a "how hard is the agent working" feed would use

File: `J/data/ChatEngine.kt` (728 lines). Public surface, in order of lines:

### Events (EngineEvent, sealed, lines 25 to 43)

- `TurnStarted(convId, botId)` (33): a bot begins to answer; emitted at ChatEngine.kt:444 in `generate`.
- `TurnCompleted(convId, messageId, botId, text, tokensIn, tokensOut)` (34 to 37): emitted at line 699.
- `ToolFinished(convId, toolName)` (38): emitted at line 622 when a non-memory tool reaches a status other than "running".
- `MemorySaved(convId)` (39): emitted at line 620 when a tool name contains "memory" and has finished.
- `BotForged(convId, botIds)` (40): emitted at line 564.
- `ApprovalRequested(convId)` (41): emitted at line 632.
- `TurnFailed(convId)` (42): emitted at lines 394, 411, 702, 708, 712.
- `EngineEvent.ToolStarted` does **not** exist. A tool start is only visible as a `ChatEvent.Tool` with status "running" (see below), and no EngineEvent is emitted for it.

### Flows on ChatEngine (lines 149 to 171)

- `val busy: StateFlow<Set<String>>` (161): conversation ids that are generating. Backed by `_busy`, added in `startRun` (~318), removed in its `finally` (~331).
- `val speaking: StateFlow<Map<String, String>>` (163): convId to botId while a bot answers. Set at line ~443 in `generate`, cleared in `startRun` (~329).
- `val status: StateFlow<Map<String, String>>` (165): convId to text from Hermes `Status` events (ChatEvent.Status, line 627).
- `val approvals: StateFlow<Map<String, PendingApproval>>` (167).
- `val memoryUpdates: StateFlow<Int>` (169).
- `val events: SharedFlow<EngineEvent>` (171); backed by `MutableSharedFlow(extraBufferCapacity = 64)` (155). No replay: a collector that starts late misses earlier events.

### Where a turn starts, text arrives, a tool starts and finishes, a turn ends or fails

- `fun send(...)` (199) to `runLoop` (219) to `startRun(convId, body)` (315) to `runTurn` (341) to `generate(run, convId, botId, ephemeralUserTurn)` (401).
- Turn start: `_speaking.update` and `_events.tryEmit(TurnStarted)` at lines 443 and 444, right before `backend.stream(req, handle) { ev -> turn.onEvent(ev) }` (~449).
- `ensureReady` (376): a turn may first wake Hermes (`STATUS_WAKING`); when it cannot, it adds an error note and returns false (lines 377 to 380; the TurnFailed emit is at 394).
- Budget BLOCK check at the start of `generate` (~404 to 412): emits TurnFailed (411).
- `private inner class Turn(convId, botId, modelId)` (459): per-reply state (text, reasoning, bubble, tools map `tools`, usage).
  - `Turn.push(force)` (489) and `writeNow` (505), `armTrailing` (511), `closeBubble` (527): UI streaming and persistence cadence (60 ms, `PUSH_INTERVAL_MS` 60 at companion).
  - `fun onEvent(ev: ChatEvent) = synchronized(lock) { handle(ev) }` (571). `private fun handle(ev)` (573).
  - Text arrives: `ChatEvent.Delta` (575 to 601). Thought-summary detection via `ThoughtFilter.isThoughtBlock` (582).
  - Reasoning: `ChatEvent.Reasoning` (594).
  - Tool starts and updates: `ChatEvent.Tool` (603 to 626). Status "running" creates the TOOL message (613) and records it in `tools[callId]`; any later event with the same callId updates it. When the status is not "running", the memory tool sets `memoryUpdates` and emits `MemorySaved` (618 to 620), any other tool emits `ToolFinished` (622).
  - Usage: `ChatEvent.Usage` (626) stores prompt and completion tokens for the turn.
  - Status: `ChatEvent.Status` (627).
  - Approval: `ChatEvent.Approval` (628 to 635): stores `PendingApproval`, emits `ApprovalRequested` (632).
  - `StreamError` (636), `Finish` (640).
- Turn end: `fun finish(outcome: ChatOutcome): Boolean` (675) calls `finishLocked` (681):
  - clears approvals (682), closes the bubble, marks running tool messages `completed` (Completed) or `interrupted` (690).
  - `recordUsage()` (691; defined at 658): `UsageStore.record(modelId, tin, tout)`, WARN note when the daily total crosses the limit.
  - outcome `Completed` (693): `TurnCompleted` (699), or `TurnFailed` (702) if `hadError`.
  - `Cancelled` (705): nothing emitted.
  - `HttpError` (706 to 708): error note, `TurnFailed` (708).
  - `TransportError` (709 to 712): error note, `TurnFailed` (712).
- Outcomes (`J/api/HermesApi.kt:20` to 26): `ChatOutcome.Completed`, `Cancelled`, `HttpError(code, body)`, `TransportError(message)`.
- Chat events (`J/api/Sse.kt:37` to 61): `Delta(text)`, `Reasoning(text)`, `Tool(tool, label, status, callId)`, `Finish(reason, errorCode)`, `StreamError(message, raw)`, `Status(kind, text)`, `Approval(...)`, `Usage(promptTokens, completionTokens, totalTokens)`, `Done`.

### Presence and AgentService (runtime and background states)

- `RuntimeState` (`J/runtime/RuntimeState.kt:3` to 9): `NotInstalled`, `Extracting(fraction, detail)`, `Starting(step)`, `Running`, `Stopped(reason)`, `Error(reason)`.
- `RuntimeManager`: `val state: StateFlow<RuntimeState>` (`J/runtime/RuntimeManager.kt:53` to 54), `val foregroundNeeded: StateFlow<Boolean>` (59).
- `AgentService` (`J/service/AgentService.kt:33`): `onCreate` (42), `onStartCommand` (78), `onDestroy` (93). Collects `combine(rt.foregroundNeeded, rt.state, app.engine.busy)` (55). Notification text (56 to 69): Running with busy chats uses plural `prs_notif_running_answering`; Running uses `notif_running`; Extracting uses `notif_extracting` with percent; other states use `notif_starting`. Updates only when the text changes.
- `Presence` (`J/service/Presence.kt`, class at 135): `watchState` (205) tracks `runningSince`; `watchEvents` (271) collects the engine events and acts only on `TurnCompleted` (285) and `TurnFailed` (295); `watchApprovals` (305), `notifyApproval` (324). `watchIdle` (243), `checkIdle` (253) use `anyChatBusy = engine.busy.value.isNotEmpty()` (259).
- Sound on events: `J/PocketApp.kt:200` to 214 maps `TurnCompleted` to COMPLETE (200), `ToolFinished` to TOOL (210), `MemorySaved` to MEMORY (211), `BotForged` to FORGE (212), `ApprovalRequested` to APPROVAL (213), `TurnFailed` to ERROR (214).

Hooks a "how hard the agent is working" feed would attach to, already present: `engine.busy` (which chats),
`engine.speaking` (which bot), `engine.status` (Hermes status text), `ChatEvent.Tool` with status "running" and
later finished (`Turn.handle`, 603 to 626; the only per-tool signal), `ChatEvent.Usage` (626; tokens per turn,
known only at the end), `ChatEvent.Reasoning` and `Delta` (575, 594; no counter exists). Missing: an EngineEvent for
tool start, a per-turn tool count, and any replay of events. `RuntimeState` is separate from chat activity.

---

## H. voice/SoundEngine

File: `J/voice/SoundEngine.kt` (119 lines). Class `SoundEngine(context, prefs: VoicePrefs, ...)` (constructor from line 38); `SoundPool` created at line 43; `loadAll()` at 66.

### Cues (`enum class SoundCue`, SoundEngine.kt:11 to 35)

| Cue | Meaning (kdoc) | hearth file | studio file |
|---|---|---|---|
| COMPLETE | reply finished (chime) | `sfx_hearth_complete` | `sfx_studio_complete` |
| SEND | sending a message | `sfx_hearth_send` | `sfx_studio_send` |
| TOOL | tool step finished | `sfx_hearth_tool` | `sfx_studio_tool` |
| ERROR | error | `sfx_hearth_error` | `sfx_studio_error` |
| APPROVAL | Hermes asks permission | `sfx_hearth_approval` | `sfx_studio_approval` |
| FORGE | Bot Forge, Newroz overdrive | `sfx_hearth_forge` | `sfx_studio_forge` |
| MEMORY | Hermes updated memory | `sfx_hearth_memory` | `sfx_studio_memory` |
| SPARK | tap on Pêt or the fire | `sfx_hearth_spark` | `sfx_studio_spark` |
| TAP | copy, fork, switch | `sfx_hearth_tap` | `sfx_studio_tap` |
| MIC | voice recording begins | `sfx_hearth_mic` | `sfx_studio_mic` |

### Packs and files

- `enum class SoundPack { HEARTH, STUDIO, OFF }` in `J/voice/VoicePrefs.kt:12`. Stored in `VoicePrefs` settings (`soundPack`, `soundVolume`).
- `res/raw/`: 20 `.ogg` files (10 `sfx_hearth_*`, 10 `sfx_studio_*`) and `keep.xml`. Mapping in `loadAll` (68 to 90).
- Packs are chosen by `prefs.settings.value.soundPack`, overridable per call with `forcePack` (used by the settings preview, `J/ui/settings/VoiceSoundSection.kt:242` and 248).

### How a cue is played

`fun play(cue: SoundCue, forcePack: SoundPack? = null)` (98 to 118):
1. Reads the settings; `pack = forcePack ?: cfg.soundPack`; OFF returns.
2. Silent ringer mode returns, unless `forcePack` is set.
3. Volume `cfg.soundVolume` clamped 0 to 1; 0.01 or less returns.
4. `SoundPool.play(sid, gain, gain, 1, 0, 1f)` with `gain`: TOOL and TAP times 0.65; SPARK and SEND times 0.78; others the volume. Wrapped in `runCatching`.

Call sites (grep): `PocketApp.kt:200` to 214 (engine events); `ui/home/HomeScreen.kt:184` (FORGE), `:187` (SPARK), `:279` (SEND);
`ui/chat/ChatDialogs.kt` (TAP at 190, 299, 303, 317, 335); `ui/chat/ChatInputArea.kt:94, 151` (MIC);
`ui/chat/ChatMessageList.kt:224` (TAP); `ui/chat/ChatMessages.kt:263` (SPARK); `ui/chat/ChatScreenState.kt:334` (SEND), `:349` (TAP);
`ui/chat/ChatSlashActions.kt:153, 159` (FORGE); `ui/chat/MarkdownView.kt:234` (TAP); `ui/settings/AppearanceSection.kt:159` (TAP);
`ui/settings/VoiceSoundSection.kt` (previews, 242 to 280).

### NOTICE: current sound entries

`NOTICE` (repository root, 2374 bytes) has **no entry for the sound files**. It lists: proot (GPL-2.0-or-later),
talloc (LGPL-3.0-or-later), Debian trixie userland, Hermes Agent (MIT), Python and uv, Android app dependencies
(AndroidX, Compose, Material 3, Kotlin, kotlinx.coroutines, Apache Commons Compress, XZ for Java), and a GPL/LGPL
source offer. It says nothing about:
- the CC0 UI sounds from `romainsimon/uisfx` (credited only in `README.md:34`, `SoundEngine.kt:35` and `AGENTS.md`);
- the Supertonic 3 speech model downloaded from Hugging Face (`SupertonicModelStore.kt:254`);
- sherpa-onnx (the speech runtime AAR, `AGENTS.md`).

---

## I. The budget and today's tokens (data/UsageStore.kt)

- `class UsageStore internal constructor(file: File, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis, debounceMs: Long = 800L)` (34). Production constructor `constructor(context: Context)` (40) uses `filesDir/chat/usage.json` on `Dispatchers.IO`. Created in `J/PocketApp.kt:130` (`usage = UsageStore(this)`; `lateinit` at PocketApp.kt:68).
- Types: `data class UsageDay(day: String, model: String, turns: Int, tokensIn: Long, tokensOut: Long)` (22); `enum class BudgetMode { OFF, WARN, BLOCK }` (24); `data class UsageBudget(dailyTokens: Long = 0L, mode: BudgetMode = OFF)` (27).
- Public API:
  - `val days: StateFlow<List<UsageDay>>` (51).
  - `val budget: StateFlow<UsageBudget>` (52).
  - `fun record(model: String, tokensIn: Long, tokensOut: Long)` (59).
  - `fun setBudget(b: UsageBudget)` (78; clamps `dailyTokens` at 0).
  - `fun todayTotal(): Long` (89): sum of tokens for today's rows (in and out, all models).
  - `fun overBudget(): Boolean` (95 to 97): `mode != OFF && dailyTokens > 0 && todayTotal() >= dailyTokens`. A limit of 0 means no limit.
  - `fun clear()` (101; keeps the budget), `fun flush()` (111), `fun suspendWrites()` (128).
  - `companion`: `MAX_DAYS = 400` (187), `fun dayOf(millis: Long, zone: ZoneId = ...): String` (191, `yyyy-MM-dd`).
- File format: one JSON object with `days` and `budget` (load at 148 to 183; encode at 207). Written with a debounce.
- Who reads "today's tokens": Home (`J/ui/home/HomeScreen.kt:116` to 118, shown at 367 to 374 when > 0, linked to Settings usage). Engine: `recordUsage` (`ChatEngine.kt:658`), `generate` BLOCK check.
- Budget UI: `J/ui/settings/UsageSection.kt:164` to 176 (`setBudget`, presets chips with `PocketChip`, and OFF, WARN, BLOCK chips). Tags: `usage.today`, `usage.budget.limit`, `usage.budget.warn`, `usage.budget.block` (`RobotTags.kt:68` to 71).
- Woodpile: `todayTotal()` and `budget.value.dailyTokens` give the ratio. `overBudget()` is the existing "full" test.

---

## J. Permissions, network and sensors

### Manifest (`app/src/main/AndroidManifest.xml`, `uses-permission` lines 4 to 11)

1. `android.permission.INTERNET` (4)
2. `android.permission.ACCESS_NETWORK_STATE` (5)
3. `android.permission.FOREGROUND_SERVICE` (6)
4. `android.permission.WAKE_LOCK` (7)
5. `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (8)
6. `android.permission.POST_NOTIFICATIONS` (9)
7. `android.permission.RECORD_AUDIO` (10)
8. `android.permission.RECEIVE_BOOT_COMPLETED` (11)

Absent: CAMERA (deliberately, per AGENTS.md), location (fine or coarse), any sensor permission, any storage permission. There is no `uses-feature`. `<queries>` at line 13 declares `android.speech.RecognitionService` (15) and `TTS_SERVICE`.

### Network and other outbound use in app code

Searched for `HttpURLConnection`, `openConnection`, `URL(`, `OkHttp`, `Socket`, `https://`. Findings:

1. `J/api/HermesApi.kt:235` to 237: `URL(base() + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection`; `base()` (110) is `http://127.0.0.1:<port>` (`Paths.API_HOST`, `J/runtime/Paths.kt:68`). Loopback only, to Hermes in the guest. `conn` fields at 93 and 176.
2. `J/voice/SupertonicModelStore.kt:183` to 208: model download with `HttpURLConnection` over `URL(current)`; redirects followed by hand (208). `BASE_URL` (254) = `https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/main/`. Only outbound internet call made by the app's own Kotlin code (about 139 MB, requested by the user). `activeConn` (declared at line 49) is the connection that cancellation closes.
3. `J/runtime/RuntimeManager.kt:436`: `ServerSocket(0, 1, InetAddress.getByName(API_HOST))`: picks a free loopback port. Not outbound.
4. `J/runtime/RuntimeManager.kt:~741`: the Debug self-test builds a guest shell command with `getent hosts` and `curl -sS -I -m 8 https://<host>/` for `probeHosts()`. This runs inside the proot guest, not in the app JVM.
5. `J/runtime/ProviderConfig.kt:52` (`EXPRESS_HOST = "https://aiplatform.googleapis.com"`), used at 139 to 140 to write the guest's Vertex base URL. Hermes calls it, not the app code.

Location and sensors: searched `LocationManager`, `FusedLocation`, `SensorManager`, `LocationListener`, `getLastKnownLocation`, `Geocoder`, `ACCESS_FINE`, `ACCESS_COARSE`: no matches in app code or manifest.

Speech: `RECORD_AUDIO` is for `SpeechRecognizer` (ChatInputArea voice input) and the `RecognizerIntent` fallback; no network from the app beyond the list above.

---

## K. Unit tests that pin the look

Directory: `app/src/test/java/dev/pocketagent/app/ui/`.

### `pixel/stage/SceneRendererParityTest.kt` (126 lines)

- `parityAcrossStatesAndTimes`: for each of three surface sizes (1080x735, 720x560, 1440x1000) and two palettes (`TestPalettes.DARK`, `TestPalettes.LIGHT`), each of 14 `Case`s (COLD t=0 and t=4321; KINDLING 0.1, 0.4, 0.9; BLAZING t=1234, t=7700 hop, t=14300 shooting star, stoke 0.8, celebration, plain quality, WAVING override; SMOKE; COLD with stoke 1.2) is rendered two ways: `LegacyHearthReference.render(...)` (the old code, a type-only copy at commit 344829f) and the new path (`StageGrid.forScene` + `SceneRenderer.render` + `PixelStage.scaleInto`). The pixel arrays must match exactly. Failure message gives the first difference (x, y, expected, got, count).
- `theReferenceDrawsSomething`: guards the oracle (blank reference is a failure).
- `parityMessageNamesFirstDifference`: tests `ParityDiff`.
- What a scene change must do: any change to the picture fails this test unless (a) `LegacyHearthReference` is updated to the new picture or (b) the file is deleted "when a wave changes the picture on purpose" (its kdoc, `LegacyHearthReference.kt` lines 10 to 20). Wave 1 changes to the scene therefore have to delete or move this oracle first. Also `SceneRendererTest.kt` exists beside it.

### `theme/PixelFontGoldenTest.kt` (67 lines)

- Reads `app/src/test/resources/pixelfont-v1.golden` (135 lines; one line per glyph: `U+XXXX width rows|joined`) and compares every line with `PixelFont.glyph(c)` for that code point. Any changed or vanished v1 glyph fails.
- New glyphs are fine (v2 is not in the golden). Setting `PIXELFONT_GOLDEN_WRITE=1` rewrites the file (only for a deliberate re-baseline).
- Also `theme/PixelFontTest.kt` exists (not read).

### `theme/PaletteContrastTest.kt` (354 lines)

- For every theme (6), variant (dark, light) and accent (8 values including THEME), checks pairs: text on bg, bgDeep, surface, surfaceHigh (7:1 level for body text per kdoc; tests at 283 to 291); muted text (293 to 300); accentSoft on bg and surface (303); onAccent on accent (311); bubbles on their faces (316 to 322); text on codeBg (325 to 327); signal colours (error, success, warning) on bg and surface (330 to 344); outlines and accent at 3:1 (347 to 350).
- `KNOWN_FAILURES: Set<String>` (kdoc at 11 to 16; the set has 215 ids, for example `KAWA/dark/FLAME:outline-on-bg`, `KAWA/light/FLAME:accent-on-bg`). A failing pair not in the set fails the test; a set entry that now passes also fails the test (stale). So a palette change that fixes a pair must remove its id; a new colour must pass every pair or be added with a reason.
- Adding or changing a colour in `Palettes.kt` triggers the full matrix (6 x 2 x 8 = 96 combinations).

### Other tests that touch this area (names only, not read)

`ui/pixel/PxlParserTest.kt` (every shipped `.pxl` parses; names unique); `ui/pixel/stage/`: `ArgbTest`, `FrameGateTest`, `PixelStageTest`, `SceneRendererTest`, `SpriteLoaderTest`, `SpriteRasterTest`, `StageCoreIsPureTest`, `StageGridTest`, `TestSprites` (a fake sprite source for tests); `ui/theme/`: `HapticsTest`, `MotionTest`, `PixelFontTest`, `TokensTest`. Outside the ui folder: `AppClockTest`, `DebugLinksTest`, `AppLinksTest`, `UsageStoreTest`, `ChatEngineTest`.

---

## Items noticed for the Wave 1 planners (facts, not recommendations)

1. `ui_home_mascot_cd` says "salamander" (`res/values/strings_chat.xml:47`); Pêt is a newt everywhere else.
2. `HearthScene` ignores `Appearance.showMascot` on Home (HomeScreen.kt:197), while `EmptyState` and `HermesScreen` honour it.
3. `AppClock` is built for the home sky but no code reads it yet (section F).
4. `Fire()` and `Embers()` composables have no call sites; the scene uses its own copy of the same code in `stage/`.
5. `NOTICE` has no sound or speech-model entries (section H).
6. `SceneRendererParityTest` will fail on any change to the picture unless the oracle is retired first (section K).
7. The engine event bus has no replay and no tool-start event (section G).
8. `icon_<theme>_mono` sprites are used only by launcher XML, not Kotlin code (section C).
9. Hearth tap: the "7 taps" rule is a chain with gaps of at most 1.2 s, and `taps` counts across chains (section A).
10. `TALKING` pose (`pet_talking`) is in the renderer's list but no code path selects `MascotMood.TALKING` that I found (section D).
