# Wave 1a, area HOME: contract draft

`J/` = `app/src/main/java/dev/pocketagent/app/`; package `dev.pocketagent.app` is written `app`. Line numbers were read from the files at HEAD `fe0e0ea`. BINDING from `docs/design-language.md`: 8.1 (Home), pattern 10 (collapsing hero), pattern 15/16 (celebrate, fail), section 4 (fire: tap lights or stokes, hold puts out with a ring of embers; swipe rows with `HOLD` at the threshold), 8.16 (five states), 10 (the scene read aloud), 11 (scene under 1 ms, 95 % of frames within 8.3 ms), 13.5 (headings in sentence case, never made by code). Kind of screen: a custom **Stage over a List** (hero = Stage, below = List). Home has no top bar and keeps the five tabs as they are (Wave 1b changes them).

## 1. Scope

| # | Work item | Kind |
|---|---|---|
| H1 | Pure core: `HomeMetrics`, `DayPart`, date format, `HeroInk`, `HomeUi` + `buildHomeUi`, five states | core |
| H2 | `HomeSuggestions.pick` and the chip row under the prompt (three chips at most; a chip never sends, it opens or fills the field) | core |
| H3 | Collapsing hero: full 46 % of the container, strip of 96 dp, `collapse` fed to SCENE, pinned strip texts, greeting cross-fade | core |
| H4 | The prompt card overlapping the hero's lower edge (today's Ask field, wake-on-send and send logic unchanged) | core |
| H5 | The fire as switch: `FireSwitch`, hold ring, three tap targets (fire, Pêt, sky), labelled status-row button, stop confirmation when an answer is running, haptics and sounds | core |
| H6 | Status line + progress + first-run hint + error card (the old StatusCard's states move here; `home.state`, `home.start` keep working) | core |
| H7 | Avatar row (snapping, "+" at the end, group button as a text button in the heading row) | core |
| H8 | Recent chats with `SwipeRow` (right: pin or unpin; left: mark read or unread), long-press `ActionSheet` as the labelled way, unread badge and "N unread" link kept | core |
| H9 | Illustrated tiles (4), usage line, woodpile value (`Woodpile`) handed to the scene | core |
| H10 | Row slots for later waves (`HomeRow`, `HomeRows`, `HomeSlot`), zero rows registered in 1a | core |
| H11 | Entrance once per process (`HomeEntrance`): scene reveal 900 ms, card stagger 36 ms x 6 | core |
| H12 | Tap quips and the 7-tap overdrive moved to the pure `HearthTaps` (behaviour unchanged) | core |
| H13 | Status-bar icon polarity follows the sky (`HomeChrome`) | core (needs 7.LEAD) |
| H14 | Skeleton (loading) shapes for avatars and recent rows | core |
| H15 | Scene read aloud (`hm_scene_cd`) with custom accessibility actions on the hero | core |
| X1 | Tap on the woodpile: Pêt note with the numbers and a link to Settings, usage | extra |
| X2 | Snap fling in the avatar row (`rememberSnapFlingBehavior`) and blink offsets per avatar | extra |
| X3 | Stagger of the cards (kept: the scene reveal alone is core) | extra |
| X4 | Star names on sky tap (`HearthInfo.skyNoteAt`, SCENE) | extra |

Cut order: X4, X2, X3, X1, then H14 (plain `Spacer`s), then the strip's cross-fade (strip title simply appears at `collapse >= 0.9`). Never cut: H1, H3, H5, H6, H10. Everything moves out of the old file in small steps: the old `HomeScreen` body is replaced, not patched.

## 2. Files (I own them exclusively)

Create (all `J/ui/home/` unless shown; every pure file has no `android.` or `androidx.` import so it is JVM-testable; Compose files may):
- `HomeMetrics.kt` (pure): `HomeMetrics`, `DayPart`, `dayPartOf`, `HomeFormat`, `HeroInk`, `smoothstep`.
- `HomeUi.kt` (pure): `HomeKind`, `HomeInputs`, `HomeUi`, `RecentItem`, `buildHomeUi`.
- `HomeSuggestions.kt` (pure): `HomeSuggestions`, `Suggestion`, `SuggestionAction`, `SuggestionInput`.
- `FireSwitch.kt` (pure): `FireSwitch`, `FireAction`, `FireGesture`, `HearthTaps`, `FireRing`.
- `Woodpile.kt` (pure): `Woodpile`.
- `HomeEntrance.kt`: `HomeEntrance` (process flag, pure decision).
- `HomeChrome.kt`: `HomeChrome` (status-bar polarity state).
- `HomeTags.kt`: `HomeTags`.
- `HomeRow.kt`: `HomeRow`, `HomeSlot`, `HomeRowEnv`, `HomeHeading`.
- `rows/HomeRows.kt`: the registry (the ONE file later waves touch, one appended line each, section 3.8).
- `HomeHero.kt` (Compose): `HomeHero`, hit targets, hold ring, strip overlay, `rememberHeroCollapse`.
- `HomeStatus.kt` (Compose): `HomeStatusRow`, `HomeErrorCard`, `HomeFirstRun`.
- `HomeAsk.kt` (Compose): `HomeAskCard`, `SuggestionChips`.
- `HomeLists.kt` (Compose): `AvatarRow`, `RecentSection`, `HomeTiles`, `HomeSkeletons`, `HomeChatMenu`.
- `app/src/main/res/values/strings_home.xml` (prefix `hm_`).
- Tests (section 8) under `app/src/test/java/dev/pocketagent/app/ui/home/`.

Modify:
- `HomeScreen.kt`: rewritten; `fun HomeScreen()` keeps its name and no parameters (called at `J/ui/AppRoot.kt:420` and `:432`).
- `HomeParts.kt`: keeps `PixelHeading`, `PixelProgressBar`, `BotCard`, `NewBotCard`; `HomeTile` gains `art: String? = null` (3.9); `BotCard` and `NewBotCard` become the avatar-row items (restyled, same names).

Not touched: `HomeScrim.kt`. `statusScrimAlpha` (HomeScrim.kt:27) is pinned by `NotificationGroupsTest.kt:113-134` and stays byte for byte; `HomeStatusScrim` (line 41) is no longer called (the pinned strip covers the status bar) and stays as dead code until a later cleanup.

## 3. Shared symbols I PROVIDE (package `app.ui.home`)

### 3.1 Metrics, day parts, ink (pure, `HomeMetrics.kt`)
```kotlin
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }
fun dayPartOf(hour: Int): DayPart            // 5..11 MORNING, 12..17 AFTERNOON, 18..21 EVENING, else NIGHT (today's greeting(), HomeScreen.kt:388)
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float   // 0..1, NaN gives 0
object HomeMetrics {
    const val HERO_FRACTION = 0.46f; const val HERO_MIN_DP = 300f; const val HERO_MAX_DP = 440f
    const val STRIP_DP = 96f                  // content height of the collapsed strip, below the status bar
    const val OVERLAP_DP = 28f                // the prompt card covers the hero's lower edge by this much
    const val BAND_DP = 84f                   // bottom band of the hero with no scene object in it: greeting + status text (56) + overlap (28)
    const val FIRE_ROWS = 38                  // logical rows a fully visible fire needs (SceneAnchors.FIRE_FOOTPRINT_ROWS, 4.1)
    const val COLLAPSE_STEPS = 32
    fun heroFullDp(containerHeightDp: Float): Float            // (0.46 * h) coerced to 300..440; NaN or <= 0 gives 300
    fun stripPx(statusBarPx: Int, density: Float, gridScale: Int): Int   // statusBarPx + max(round(96 * density), FIRE_ROWS * gridScale)
    fun collapseRangePx(heroFullPx: Int, stripPx: Int): Int    // max(1, heroFullPx - stripPx)
    fun collapseOf(firstIndex: Int, firstOffsetPx: Int, rangePx: Int): Float   // index > 0 gives 1; else offset / range coerced 0..1; range <= 0 gives 1
    fun quantize(collapse: Float): Float                        // round(c * 32) / 32, coerced 0..1, NaN gives 0
    fun greetingAlpha(collapse: Float): Float                   // 1 - smoothstep(0.15, 0.55, c)
    fun stripAlpha(collapse: Float): Float                      // smoothstep(0.55, 0.95, c)
}
object HomeFormat {
    fun date(now: ZonedDateTime, locale: Locale, bestPattern: String): String   // DateTimeFormatter.ofPattern(bestPattern, locale); production passes android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"); on a bad pattern returns ISO date
}
object HeroInk {                                               // text colour on the hero's bottom band
    fun pick(bandArgb: Int): Int                               // 0xFFF4EBDD when the band's luminance < 0.45, else 0xFF1B1220
    fun needsShadow(ink: Int, bandArgb: Int): Boolean          // contrast ratio below 4.5: caller draws a 1u shadow in the opposite colour
}
```

### 3.2 State (pure, `HomeUi.kt`) and its builder
```kotlin
enum class HomeKind { FIRST_RUN, LOADING, EMPTY, READY, ERROR }
@Immutable data class RecentItem(val conv: Conversation, val generating: Boolean, val draft: String)
@Immutable data class HomeInputs(
    val runtime: RuntimeState, val setupIncomplete: Boolean, val canChat: Boolean, val chatsLoaded: Boolean,
    val bots: List<Bot>, val conversations: List<Conversation>, val busy: Set<String>, val drafts: Map<String, String>,
    val nextRoutineAt: Long?, val routineCount: Int, val todayTokens: Long, val budget: UsageBudget, val nowMs: Long, val zone: ZoneId)
@Immutable data class HomeUi(
    val kind: HomeKind, val hearth: HearthState, val progress: Float?, val dayPart: DayPart,
    val recent: List<RecentItem>,            // first 4 of conversations (already sorted by the repository)
    val unreadTotal: Int, val moreChats: Boolean /* more than 4 */, val isNewUser: Boolean /* no chat has a preview */,
    val woodpile: Int?, val todayTokens: Long, val suggestions: List<Suggestion>)
fun buildHomeUi(i: HomeInputs): HomeUi
@Composable fun rememberHomeUi(vm: MainViewModel): HomeUi        // collects the flows listed in 4.1, one HomeUi, recomputed only when an input changes
```
`buildHomeUi` rules: `hearth` and `progress` exactly as `HomeScreen.kt:141-151` today (Extracting gives `fraction * 0.9`, Starting 0.95). `kind`: `ERROR` when `runtime is Error`; else `LOADING` when `!chatsLoaded`; else `FIRST_RUN` when `runtime == NotInstalled || runtime is Extracting || setupIncomplete`; else `EMPTY` when `isNewUser`; else `READY`. `isNewUser = conversations.none { it.lastPreview.isNotBlank() }`. `woodpile = Woodpile.level(...)`. `suggestions = HomeSuggestions.pick(...)` with `routineCount` and the conversations that have `routineId` set and `unread > 0`.

### 3.3 Suggestions (pure, `HomeSuggestions.kt`)
```kotlin
enum class SuggestionText { ROUTINE_RESULT, CONTINUE_CHAT, ROUTINE_IDEA_MORNING, ROUTINE_IDEA, WHAT_CAN_YOU_DO }
sealed interface SuggestionAction {
    data class OpenChat(val convId: String) : SuggestionAction
    data object NewRoutine : SuggestionAction                  // Route.RoutineEditor(null)
    data class Prefill(val prompt: SuggestionText) : SuggestionAction   // puts the prompt text into the Ask field, never sends
}
@Immutable data class Suggestion(val id: String, val text: SuggestionText, val arg: String?, val action: SuggestionAction)   // arg = chat title for the two chat texts
data class LastChat(val convId: String, val title: String, val updatedAt: Long, val hasMessages: Boolean)
data class RoutineResult(val convId: String, val title: String, val updatedAt: Long)
data class SuggestionInput(val nowMs: Long, val zone: ZoneId, val lastChat: LastChat?, val unreadRoutineResults: List<RoutineResult>,
                           val routineCount: Int, val chatCount: Int, val isNewUser: Boolean, val canChat: Boolean)
object HomeSuggestions { const val MAX = 3; const val CONTINUE_WINDOW_MS = 604_800_000L; fun pick(i: SuggestionInput, max: Int = MAX): List<Suggestion> }
```
Rules, in this order, stop at `max` (coerced 0..5), no duplicate `id`: (1) `!canChat` gives an empty list; (2) newest unread routine result: `ROUTINE_RESULT`, id `routine:<convId>`, `OpenChat`; (3) `lastChat` with `hasMessages` and `nowMs - updatedAt <= 7 days` and not already listed: `CONTINUE_CHAT`, id `continue:<convId>`, `OpenChat`; (4) `routineCount == 0 && !isNewUser && chatCount >= 3`: `ROUTINE_IDEA_MORNING` when the hour in `zone` is 5..11, else `ROUTINE_IDEA`, id `idea:routine`, `NewRoutine`; (5) `isNewUser`: `WHAT_CAN_YOU_DO`, id `idea:capabilities`, `Prefill`. Pure, no randomness; stable order. The chip text is built in the UI from `hm_sugg_*` and `arg` (title cut at 24 characters with an ellipsis by the UI).

### 3.4 The fire switch (pure, `FireSwitch.kt`)
```kotlin
enum class FireAction { LIGHT, STOKE, PUT_OUT, CANCEL_START, NONE }
enum class FireGesture { TAP, HOLD }
object FireSwitch {
    const val TAP_MAX_MS = 300L; const val HOLD_MS = 700L; const val HOLD_GRACE_MS = 150L   // ring is hidden until 150 ms, full at 700
    fun reduce(g: FireGesture, hearth: HearthState): FireAction
    fun classify(heldMs: Long, moved: Boolean): FireGesture?     // heldMs <= 300 and !moved gives TAP; >= 700 gives HOLD; else null (cancelled)
}
```
`reduce` table: TAP: COLD gives LIGHT, KINDLING STOKE, BLAZING STOKE, SMOKE LIGHT (retry). HOLD: BLAZING gives PUT_OUT, KINDLING CANCEL_START, COLD and SMOKE NONE. `LIGHT` calls `vm.start()`, `PUT_OUT` and `CANCEL_START` call `vm.stop()` (confirmation first when `busy` is not empty, 3.7), `STOKE` is today's tap.
```kotlin
class HearthTaps(private val now: () -> Long = System::currentTimeMillis) {   // today's chain, HomeScreen.kt:170-190
    class Result(val overdrive: Boolean, val taps: Int, val quipIndex: Int /* 0..3, (taps - 1) % 4 */, val mood: MascotMood /* WAVING when taps is even, else HAPPY; HAPPY on overdrive */)
    fun tap(): Result      // streak + 1 when now - last <= 1200 ms else 1; streak >= 7 gives overdrive and resets streak; taps never resets
}
object FireRing { fun fraction(heldMs: Long): Float; fun embers(fraction: Float, total: Int = 12): Int }   // 0 until 150 ms, linear to 1 at 700; embers lit = floor(fraction * total)
```

### 3.5 Woodpile (pure, `Woodpile.kt`)
```kotlin
object Woodpile {
    fun level(usedToday: Long, budget: UsageBudget): Int?    // null when budget.mode == OFF or dailyTokens <= 0; else left = 1 - used / limit coerced 0..1; left > 0.80 gives 5, > 0.60 gives 4, > 0.40 gives 3, > 0.20 gives 2, > 0 gives 1, 0 gives 0
    fun left(usedToday: Long, budget: UsageBudget): Float?   // the same fraction, null as above
}
```
Meaning of the level: the frame index of the sprite `scene_woodpile` (4.2): 0 = ash only (used up, or blocked), 5 = full. WARN or BLOCK both show it; only the number differs.

### 3.6 Entrance and chrome
```kotlin
object HomeEntrance {
    @Volatile var played: Boolean                              // process-wide, false at every cold start
    fun shouldPlay(played: Boolean, level: AnimLevel, clockPinned: Boolean, testHooks: Boolean): Boolean   // pure: !played && level != OFF && !(testHooks && clockPinned)
    fun markPlayed(); fun reset()                              // reset is for the debug link's reset=1 (7.WEATHER)
    const val REVEAL_MS = 900; const val STAGGER_MS = 36; const val MAX_STAGGERED = 6   // 6 * 36 + FAST (150) = 366 ms <= 400
}
object HomeChrome { val statusIconsDark: State<Boolean> }       // true when the sky behind the status bar is bright (HearthInfo.topArgb luminance >= 0.5); false while Home is not composed
```

### 3.7 Composables and the labelled controls
```kotlin
@Composable fun HomeScreen()                                     // unchanged name and parameters
@Composable internal fun HomeHero(ui: HomeUi, info: HearthInfo, collapse: () -> Float, heroFullDp: Dp, actions: HomeActions, modifier: Modifier = Modifier)
@Immutable class HomeActions(val light: () -> Unit, val putOut: () -> Unit, val cancel: () -> Unit, val stoke: () -> Unit, val openDetails: () -> Unit, val skyTap: (xFrac: Float, yFrac: Float) -> Unit)
@Composable internal fun HomeStatusRow(ui: HomeUi, runtime: RuntimeState, actions: HomeActions, ink: Color, modifier: Modifier = Modifier)
@Composable fun HomeHeading(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null)   // sentence case as written, heading semantics, gutter padding, no uppercase()
```
Hero layout, top to bottom, all inside one Box that fills the width: (a) `HearthScene(...)` (4.1) at `heroFullDp` (46 % of the container, 300..440); (b) two small transparent hit targets above the list, positioned from `sceneAnchors` (fire: the fire rect grown to at least 48 dp square; Pêt: its 32-px rect grown the same way); the SKY is not an overlay: its tap handler (`detectTapGestures`, taps only) is on the list's item 0 spacer, so drags in the sky still scroll the list, and it acts only when the touch lies above `groundTop`; (c) the text band, in the lowest `BAND_DP - OVERLAP_DP` = 56 dp: greeting in the pixel font (`PixelHeading`, 3 dp pixels, or `Type.title` when `pixelFontTitles` is off) at the start, the date (`Type.label`) at the end on the same baseline, and under them the status line (`Type.body`, tag `home.state`) with the compact action button at the end; ink from `HeroInk.pick(info.bandArgb)`; (d) the progress bar (`PixelProgressBar`, 8 dp tall variant not needed: the existing 12 dp one) only while Extracting or Starting, directly under the status line. The prompt card (3.7 `HomeAskCard`) starts `OVERLAP_DP` above the hero's bottom edge.
Collapse mechanics: the list's item 0 is a transparent `Spacer` of height `heroFullPx - overlapPx` (always at least the collapse range because the strip is taller than the overlap); `collapse = HomeMetrics.collapseOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, range)` is read ONLY in `graphicsLayer {}` and draw lambdas, never in composition. The hero sits behind the list (z 0) with `graphicsLayer { translationY = -collapse * range }`; the scene gets `HomeMetrics.quantize(collapse)` (at most 33 distinct values) and moves its own layers (sky 0.35, ridges 0.6, ground 1.0, design pattern 10). At collapse 1 the visible part is `stripPx(...)` tall, pinned above the list (z 2): fire at the start, `app_name` in the pixel font and the status line beside it (starting 96 dp from the start edge; `stripAlpha` fades them in while `greetingAlpha` fades the greeting, date and band text out), the labelled button hidden (the strip is tappable as a whole: it scrolls to the top). Pinned strip texts carry tag `home.strip`.
Fire as a switch: the fire target uses one `pointerInput` with `awaitEachGesture` (down, then `withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation() }`); `FireSwitch.classify`/`reduce` decide; while held the ring (12 pixel embers around the fire, `FireRing`) fills. `LIGHT`: `HapticCue.STOKE`, `SoundCue.WAKE`, `vm.start()`. `STOKE`: today's behaviour (`HapticCue.STOKE`, `SoundCue.SPARK`, quip, mood, `stoke` boost 1.2 to 0 over `Flourish.STOKE`). Overdrive: `HapticCue.HOLD`, `SoundCue.FORGE`, `celebration = true` for 4200 ms. `PUT_OUT`/`CANCEL_START`: `HapticCue.HOLD`, `SoundCue.SLEEP`, `vm.stop()`; when `busy` is not empty a `ConfirmDialog` (`hm_stop_title`, `hm_stop_body`, `ui_home_btn_stop`, `danger = true`) comes first (`vm.stop()` kills a running answer's tools, `AGENTS.md`). A tap on Pêt is a hearth tap (`HearthTaps.tap()`) in every state. A tap on the sky calls `actions.skyTap`: `PetNotes.show(weatherNoteText(point))` when `rememberWeather().point != null`, else `HearthInfo.skyNoteAt` (X4), else nothing. The same actions exist without a gesture: the status row's button (`ui_home_btn_start` / `ui_home_btn_stop` / `ui_home_btn_cancel`, tags `home.start` / `home.stop` / `home.cancel`, one at a time) and the hero's `customActions` (`hm_act_light`, `hm_act_out`, `hm_act_stoke`). In Error the status row shows no button; the error card has `home.start` (retry) and `home.error.details`.
`HomeAskCard(ui, canChat, runtime, value, onValueChange, onSend, modifier)`: `PixelPanel` raised, today's `PocketTextField` (placeholder `ui_home_talk_hint` when Running else `ui_home_talk_hint_wake`, `minLines 1`, `maxLines 4`) and `PixelIconButton(SEND)` (label `ui_home_send`), tags `home.ask`, `home.send`; the send path is today's `HomeScreen.kt:270-286` unchanged (open or create the 1:1 Hermes chat, `engine.send`, `PetNotes` on refusal). When `!canChat` the card shows today's `ui_home_talk_hint_off` panel. `SuggestionChips`: a wrapping row of `PocketChip` (tags `home.suggest.<i>`); `OpenChat` navigates, `NewRoutine` goes to `Route.RoutineEditor(null)`, `Prefill` sets the field and requests focus.

### 3.8 Row slots for later waves
```kotlin
enum class HomeSlot { AFTER_SUGGESTIONS, AFTER_AVATARS, AFTER_RECENT }     // the tiles always come last
@Immutable class HomeRowEnv(val nowMs: Long, val zone: ZoneId, val dayPart: DayPart)
interface HomeRow {
    val id: String                      // unique, lower case, becomes the tag home.row.<id>
    val slot: HomeSlot
    val order: Int                      // ascending inside a slot, ties by id
    @Composable fun hasContent(env: HomeRowEnv): Boolean     // called in HomeScreen's body BEFORE the list is built; must be cheap; false = the row does not exist
    @Composable fun Content(env: HomeRowEnv, modifier: Modifier)   // draws its own HomeHeading and body; no outer padding other than the gutter
}
object HomeRows { val all: List<HomeRow>; const val MAX_VISIBLE = 5 }       // rows/HomeRows.kt
internal fun <T> orderRows(rows: List<T>, slotOf: (T) -> HomeSlot, orderOf: (T) -> Int, idOf: (T) -> String): List<T>   // pure
```
A later wave registers a row by creating its own file `ui/home/rows/<Name>Row.kt` with `internal object <Name>Row : HomeRow` and appending ONE line `<Name>Row,` to the `listOf(` in `rows/HomeRows.kt`; that single line is the only edit to HOME's files by any later wave, and the wave's contract names the agent who appends it. HomeScreen shows `orderRows(all.filter { it.hasContent(env) }).take(MAX_VISIBLE)` (an inline `filter` may call a composable); in 1a the list is empty and nothing is drawn.

### 3.9 Tiles and avatar row
`HomeTile(icon, label, onClick, modifier, subtitle, art: String? = null)`: when `art != null` and `SpriteBank.find(art) != null` the tile draws `PixelImage(art, size 48 dp)` (24 px at 2 dp) instead of the monochrome icon; when the sprite is missing it falls back to the icon (today's look). The four tiles and their art names: hermes `tile_hermes`, terminal `tile_terminal`, routines `tile_routines` (subtitle `ui_home_routines_next` / `ui_home_routines_none`), saved `tile_saved`. `AvatarRow(bots, onBot, onNew, onGroup)`: `LazyRow`, item width 72 dp (touch 48 dp or more), `BotAvatar(bot, 48.dp)` (24 px at 2 dp) and the name (`Type.label`, one line), contentPadding gutter, `spacedBy(Space.s)`, "+" item (`ui_home_new_bot`) last; tags `home.avatars`, `home.avatar.<i>`, `home.avatar.new`.
`RecentSection`: heading is the existing `ui_home_recent`, with the existing "N unread" and "See all" links to the Chats tab (`nav.tab(Route.Chats)`); rows are `SwipeRow(startActions = [pin or unpin], endActions = [mark read or mark unread])` around `ConversationRow(conv, botsById, generating, onClick, onLongClick = menu, draft)` (`J/ui/chat/ChatListScreen.kt:266`) in a `Box(Modifier.background(c.bg))` (the swipe's reveal layer sits behind the content, `SwipeRow.kt:134-153`, so the content must be opaque). Colours: pin `c.accent`, read `c.surfaceHigh`; icons `PixelIcon.PIN`, `CHECK` / `BELL`; labels `ui_act_pin`, `ui_act_unpin`, `ui_act_mark_read`, `ui_act_mark_unread` (existing keys). Actions call `repo.setPinned` and `repo.markRead` / `repo.markUnread`. `HomeChatMenu`: long press opens an `ActionSheet` with the same two actions plus "Open" (labelled way, section 10 of the design language). `Modifier.animateItem()` is not used (rows are inside one `Column`, not list items).

## 4. Shared symbols I CONSUME

### 4.1 From SCENE and ART-WORLD (exact forms I need; if SCENE's draft names them differently its draft wins and HOME changes one call site)
| Symbol | Form | Notes |
|---|---|---|
| `HearthScene` | `@Composable fun HearthScene(state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null, stokeBoost: Float = 0f, celebration: Boolean = false, collapse: () -> Float = { 0f }, reveal: () -> Float = { 1f }, bottomBand: Dp = 0.dp, woodpile: Int? = null)` in `app.ui.pixel` | The height is taken from `modifier` (today `HearthScene.kt:96` forces `.height(280.dp)` after the modifier: SCENE removes that). Defaults reproduce today's picture, so `SceneRendererParityTest` can stay until SCENE retires it. The stage is allocated once for the full hero size; `collapse` and `reveal` are read only in the draw phase |
| `HearthInfo`, `rememberHearthInfo()` | `@Immutable class HearthInfo(val topArgb: Int, val bandArgb: Int, val daylight: Float, val skyNoteAt: (xFrac: Float, yFrac: Float) -> String? = { _, _ -> null })`; `@Composable fun rememberHearthInfo(): HearthInfo` (reads `AppClock`, the place, the theme and the sky mode; recomputes once a minute) | `topArgb`: colour of the sky behind the status bar; `bandArgb`: ground colour in the bottom band; `daylight` 0 night to 1 day |
| `SceneAnchors`, `sceneAnchors` | pure, in `app.ui.pixel.stage`: `class SceneAnchors(val width: Int, val height: Int, val fireX: Int, val fireBase: Int, val petX: Int, val groundTop: Int, val woodpileX: Int, val woodpileY: Int) { companion object { const val FIRE_FOOTPRINT_ROWS = 38 } }`, `fun sceneAnchors(width: Int, height: Int, bottomBandRows: Int, collapse: Float): SceneAnchors` (logical pixels) | one definition for the renderer and the hit targets. HOME converts a logical point to dp with `StageGrid.forScene(widthPx, heightPx)` (`scale`, `offsetX`, `offsetY`): `dp = (offset + logical * scale) / density` |
| `SceneState` | gets `collapse`, `reveal`, `woodpile: Int?`, `bottomBandRows: Int` (SCENE's own fields behind the composable's parameters) | HOME never builds a `SceneState` |
| Art by name (ART-WORLD, in `illustrations.pxl` and `scene*.pxl`) | `tile_hermes`, `tile_terminal`, `tile_routines`, `tile_saved`: 24 x 24, 1 frame (1000 ms), own palette, transparent background, at most 7 colours, drawn for 2 dp per pixel on a dark or light page. `scene_woodpile`: 28 x 16, 6 frames (each 1000 ms, never played; the frame is chosen by index), frame k shows k logs (0 = ash and a charred stump, 5 = a full stack), bottom edge at row 15, anchor = bottom centre. `empty_bubble`: 32 x 32, 1 frame, an empty speech bubble with a small flame spark, used by the Recent empty state | a tile whose sprite is missing keeps its icon; a missing `scene_woodpile` draws nothing; a missing `empty_bubble` shows the old `Mascot(IDLE)` (EmptyState behaviour) |
| `SoundCue.WAKE`, `SoundCue.SLEEP` | enum entries added by APPEAR in `voice/SoundEngine.kt:11` | played by `app.sounds.play(cue)` |

### 4.2 From PET, WEATHER, SHELL
| From | Form |
|---|---|
| PET | `object PetSignals { fun tapped(streak: Int); fun lit(); fun putOut() }` in `app.ui.pixel.pet` (proposed; three calls, all optional in behaviour: HOME still passes `mood` and `celebration` to the scene) |
| WEATHER | `@Composable fun rememberWeather(): WeatherView` (`WeatherView.point: WeatherPoint?`), `@Composable fun weatherNoteText(point: WeatherPoint): String`, both in `app.ui.pixel` (draft-WEATHER 3.4) |
| WEATHER (7) | `DebugLinks.apply`: call `HomeEntrance.reset()` in the `reset` branch |
| Wave 0 | `AppClock.now(zone: ZoneId): ZonedDateTime`, `AppClock.pinned: StateFlow<Long?>`; `Pocket.colors`, `Pocket.anim`, `Pocket.motion`, `Pocket.appearance.pixelFontTitles`; `Space`, `Dur`, `Flourish`, `Loop`, `Ease`, `HapticCue.{STOKE, HOLD, TICK, RISE}`, `rememberHaptics()`; `SwipeRow(modifier, startActions, endActions, enabled, content)`, `SwipeAction(icon: PixelIcon, label: String, color: Color, danger: Boolean = false, onTrigger: () -> Unit)` (`SwipeRow.kt:44-87`; only the FIRST action of each side fires on a swipe, every action is a TalkBack custom action); `ActionSheet(title, actions, onDismiss)`, `SheetAction(icon, label, danger, onClick)`, `ConfirmDialog(title, body, confirmLabel, onConfirm, onDismiss, danger, closeOnConfirm, dismissLabel)`, `Skeleton(modifier, height: Dp, corner: Int)` (`Skeleton.kt:40`), `PocketChip(text, selected, onClick, modifier, icon)`, `PixelPanel(modifier, color, border, corner, pixel, highlight, variant, content)`, `PixelText`, `PixelIconButton(icon, onClick, modifier, contentDescription, tint, enabled)`, `PetNotes.show(text, kind, actionLabel, onAction)`, `t()`, `tn()`, `appLocale()`; `RobotTags.HOME_STATE`, `HOME_START`; `PixelImage(name, modifier, frame, ...)` (`PixelImage.kt:212`), `SpriteBank.find(name)`, `BotAvatar(bot, size, modifier, animated)` (`ChatCommon.kt:93`), `ConversationRow` (`ChatListScreen.kt:266`), `titleOf` (`ChatCommon.kt:123`), `inputAvailable` (`ChatLogic.kt:22`), `compactTokens`, `dayTimeLabel`, `openOneToOne`, `accepted`; repositories: `ChatRepository.bots/conversations/loaded/setPinned/markRead/markUnread/createConversation` (`ChatRepository.kt:45-49, 200, 221-224, 187`), `RoutineStore.routines` (`RoutineStore.kt:109`), `RoutineScheduler.nextRunAt` (`RoutineScheduler.kt:59`), `UsageStore.todayTotal()/days/budget` (`UsageStore.kt:51-52, 89`), `MainViewModel.start()/stop()/savedConfigIncomplete()` (`MainViewModel.kt:183-184, 95`), `ChatEngine.busy/send` |

## 5. Strings: `res/values/strings_home.xml`, prefix `hm_` (English only; TRANSLATE adds Turkish and French). Existing keys reused unchanged: `ui_home_status_*`, `ui_home_hint_first`, `ui_home_btn_start/stop/cancel/details`, `ui_home_greet_*`, `ui_home_talk_hint`, `_hint_wake`, `_hint_off`, `ui_home_send`, `ui_home_setup_*`, `ui_home_new_bot`, `ui_home_group_btn`, `ui_home_recent`, `ui_home_unread` (plural), `ui_home_see_all`, `ui_home_quip_1..4`, `ui_home_quip_overdrive`, `ui_home_tile_*`, `ui_home_routines_*`, `ui_home_usage_today`, `ui_act_pin/unpin/mark_read/mark_unread`, `ui_send_not_accepted`, `ctl_loading`, `app_name`. Left unused (kept, not deleted): `ui_home_tagline`, `ui_home_talk_title`, `ui_home_bots`, `ui_home_mascot_cd` (says "salamander", survey item 1), `ui_home_no_chats`, `ui_home_chat_with` is still used by the avatar items.

| Key | English | Note (`t:` ... `kind:`) |
|---|---|---|
| `hm_scene_cd` | The hearth, %1$s, %2$s | t: spoken label of the whole hero; %1$s day part word (hm_part_*), %2$s state of the fire (hm_fire_*); about 40 characters, read aloud, not shown \| kind: body |
| `hm_part_morning`, `hm_part_afternoon`, `hm_part_evening`, `hm_part_night` | morning, afternoon, evening, night | t: day part inside hm_scene_cd; lower case, one word \| kind: body |
| `hm_fire_out`, `hm_fire_catching`, `hm_fire_lit`, `hm_fire_smoking` | the fire is out, the fire is catching, the fire is lit, the fire is smoking | t: state of the fire inside hm_scene_cd (cold, kindling, blazing, error); lower case \| kind: body |
| `hm_act_light`, `hm_act_out`, `hm_act_stoke` | Light the fire, Put out the fire, Stoke the fire | t: custom accessibility actions of the hero; read aloud by a screen reader, up to 22 characters \| kind: body |
| `hm_sky_cd` | The sky. Double tap for the weather. | t: spoken label of the sky target, only when a weather reading exists \| kind: body |
| `hm_fire_cd` | The fire | t: spoken label of the fire target; the state is added by the screen reader from the custom actions \| kind: body |
| `hm_pet_cd` | Pêt, the newt. Tap to say hello. | t: spoken label of the Pêt target; Pêt is a name, keep it; replaces ui_home_mascot_cd (which wrongly said salamander) \| kind: body |
| `hm_stop_title` | Put out the fire? | t: title of the confirmation shown when an answer is still running; up to 24 characters \| kind: title |
| `hm_stop_body` | Hermes is still answering. Putting out the fire stops the answer. | t: body of that dialog; 2 lines \| kind: body |
| `hm_sugg_continue` | Continue: %1$s | t: chip under the prompt; %1$s is the title of the last chat, cut at 24 characters; whole chip up to 34 characters \| kind: chip |
| `hm_sugg_result` | New: %1$s | t: chip for a routine's unread result; %1$s the chat title cut at 24 characters; up to 30 characters \| kind: chip |
| `hm_sugg_routine_morning` | Set up a morning brief | t: chip that opens the routine editor, shown in the morning; up to 26 characters \| kind: chip |
| `hm_sugg_routine` | Make a routine | t: chip that opens the routine editor; up to 20 characters \| kind: chip |
| `hm_sugg_capabilities` | What can you do? | t: chip that fills the prompt for a new user; up to 20 characters \| kind: chip |
| `hm_prompt_capabilities` | What can you do for me? Show me three examples. | t: the text put into the prompt field by the chip above; written as the user would say it; up to 60 characters \| kind: body |
| `hm_section_around` | Around the fire | t: heading above the avatar row, sentence case as written \| kind: title |
| `hm_recent_empty_title` | No chats yet | t: empty state of Recent, up to 18 characters \| kind: title |
| `hm_recent_empty_body` | Ask anything above and your chats will rest here. | t: empty state body, 2 lines \| kind: body |
| `hm_recent_empty_action` | Start a chat | t: button that puts the cursor in the prompt; up to 18 characters \| kind: button |
| `hm_error_title` | The fire went out | t: title of the error card when Hermes failed to run; the reason follows in monospace \| kind: title |
| `hm_woodpile_cd` | Today: %1$s of %2$s tokens left | t: spoken and note text of the woodpile; %1$s tokens left (compact, such as 42k), %2$s the daily limit \| kind: pet |
| `hm_woodpile_used` | Today's wood is used up | t: same, when nothing is left \| kind: pet |
| `hm_menu_open` | Open | t: first entry of the long-press menu of a recent chat; up to 12 characters \| kind: row |

## 6. Stored data

None. Home adds no preference, file or database field. The prompt text stays `rememberSaveable` (today). `HomeEntrance.played`, `HomeChrome`, the collapse state and the ring are in memory. APPEAR owns the sky, place and days settings that Home only reads through `rememberHearthInfo()`. Nothing the previous version could misread exists, and a rollback to build 32 or Wave 0 changes nothing on disk. The conversation list is read, never written, except `setPinned`, `markRead` and `markUnread` (existing methods, one conversation per call, no bulk write).

## 7. REQUESTS to other areas

- SCENE: `ui/pixel/HearthScene.kt:66-109` and `stage/*`: the parameters, `HearthInfo`/`rememberHearthInfo`, `SceneAnchors`/`sceneAnchors` and the sprites of 4.1; remove the forced `.height(280.dp)` at line 96; at `collapse = 1` keep the whole fire (`FIRE_FOOTPRINT_ROWS` = 38 rows) inside the strip, with the fire's centre within 64 dp of the start edge and nothing else (Pêt, woodpile) drawn; parallax factors sky 0.35, ridges 0.6, ground 1.0 by `collapse` with the shift quantised to whole logical pixels; `reveal` 0 shows sky only, 0.4 ridges risen, 0.7 fire cold, 1 as today; a bottom band of `bottomBand` dp (84) below the hearth with only ground and foreground grass; blit `scene_woodpile` frame `woodpile` at (`woodpileX`, `woodpileY`) when it is not null. Retire `SceneRendererParityTest` first (its kdoc, survey K) because the band changes `fbase`.
- ART-WORLD: the four tile sprites, `scene_woodpile` and `empty_bubble` exactly as 4.1; contact sheet of the tiles in both page colours.
- PET: `PetSignals` (4.2); `Mascot`/`PetNote` unchanged for Home; the quip texts stay in `strings_chat.xml` and HOME keeps selecting them.
- WEATHER: `DebugLinks.apply` reset branch calls `HomeEntrance.reset()`; `rememberWeather`/`weatherNoteText` as in their draft.
- APPEAR: `SoundCue.WAKE` and `SLEEP`; HOME reads no new setting. "Classic fire" and "Pêt in chats" are not Home's.
- LEAD (AppRoot belongs to SHELL, Wave 1b, so nobody owns it in 1a): `J/ui/AppRoot.kt:234` becomes `SystemStatusBarIcons(darkIcons = ((route != Route.Home || practiceOn) && !c.isDark) || (route == Route.Home && !practiceOn && HomeChrome.statusIconsDark.value))`; without it Home keeps light status icons over a bright day sky (illegible). Also the plan's swipe actions: archive and delete-with-undo do not exist in the data layer (section 11); NOTICE/docs need nothing from HOME.
- ROBOT-T / LEAD: `ScenarioFilesTest` known tags: the list in section 9; new scenarios `home-sky.json`, `home-gestures.json`, `home-collapse.json` (safe on owner-debug) with the steps of section 9.
- INTEGRATE: the tag `stage.hearth` stays on SCENE's `Spacer` (`HearthScene.kt:98`); `home.hero` is mine and sits on the hero's Box.
- TRANSLATE: Turkish and French for every `hm_*` key.

## 8. Tests (JUnit 4, JVM; time, zone and randomness are parameters; no Android type in the files under test)

| Class | Tests: what each proves |
|---|---|
| `HomeSuggestionsTest` | `emptyWhenCannotChat`; `unreadRoutineResultFirst` (newest of two, `OpenChat`); `continueNeedsMessagesAndSevenDays` (7 days minus 1 ms yes, plus 1 ms no, `hasMessages = false` no); `continueNotDuplicatedOfResult` (same convId listed once); `routineIdeaOnlyWithoutRoutines` (`routineCount` 0 yes, 1 no) and `routineIdeaNeedsThreeChats`; `morningVariantByZoneHour` (06:59 in Europe/Istanbul vs the same instant in America/Los_Angeles); `newUserGetsOnlyCapabilities`; `maxHonouredAndCoerced` (max 0, 1, 3, 99 gives 0, 1, 3, at most 3 available); `idsUnique`; `deterministic` (same input twice gives equal lists) |
| `HomeMetricsTest` | `dayPartTable` (hours 4, 5, 11, 12, 17, 18, 21, 22, 0; reference: today's `greeting()`, `HomeScreen.kt:388`); `heroFullDp` (800 gives 368, 1000 gives 440, 600 gives 300, NaN gives 300); `stripPx` (1080 px wide, density 2.625, scale 7, status 95: 95 + max(252, 266) = 361; density 3.5, scale 9 at 1440: 105 + max(336, 342) = 447); `collapseOf` (index 1 gives 1, offset 0 gives 0, half range gives 0.5, range 0 gives 1, negative offset gives 0); `quantizeSteps` (33 distinct values over 0..1, NaN gives 0); `crossFadeNeverEmpty` (for 101 values of c from 0 to 1, `max(greetingAlpha, stripAlpha) >= 0.4`, so the band is never blank; both are monotone; 1 and 0 at the ends); `dateFormatFixedPattern` ("EEE d MMM", Locale.ENGLISH, 2027-01-15 gives "Fri 15 Jan"; with `Locale("tr")` "Cum 15 Oca"); `inkByLuminance` (white band gives dark ink, `0xFF130A16` gives light ink); `needsShadowBelowFour` |
| `FireSwitchTest` | `tapTable` and `holdTable` (all 8 cells of 3.4); `classifyBoundaries` (300 ms yes, 301 no; 699 null, 700 HOLD; `moved` gives null); `ringFraction` (0 at 150, 1 at 700, linear, clamps) and `embersLit` (12 at 1, 0 at 0, 6 at 0.5) |
| `HearthTapsTest` | `sevenInChainIsOverdrive` (gaps of 1200 ms: the 7th is `overdrive`, streak resets); `gapOf1201BreaksChain`; `tapsNeverReset` (after an overdrive taps keeps counting); `quipCycle` (indices 0,1,2,3,0 for taps 1..5); `moodParity` (WAVING when taps even, HAPPY odd; overdrive HAPPY). Reference: `HomeScreen.kt:170-190` |
| `WoodpileTest` | `hiddenWithoutBudget` (OFF, limit 0, negative); `levelTable` (used/limit 0, 0.19, 0.20, 0.39, 0.40, 0.59, 0.60, 0.79, 0.80, 0.99, 1.0, 2.0 against the thresholds of 3.5 with exact boundary cells); `leftFraction` |
| `HomeUiTest` | `kindOrder` (the five kinds from crafted `HomeInputs`: Error beats Loading beats FirstRun beats Empty beats Ready); `hearthAndProgress` (Extracting 0.5 gives 0.45; Starting 0.95; Running none) as today; `recentIsFirstFour`; `unreadTotalAndMore`; `isNewUserByPreview`; `draftsAttach`; `suggestionsGatedByCanChat` |
| `HomeRowRegistryTest` | `orderBySlotThenOrderThenId`; `idsUniqueAndLowerCase` (also for `HomeRows.all`); `registryEmptyInWave1a` (documents the slot; the next wave edits this test with its line); `capFive` (`take(MAX_VISIBLE)` after filtering) |
| `HomeEntranceTest` | `shouldPlayTable` (played, OFF level, pinned clock with and without test hooks, REDUCED plays); `markAndReset`; `staggerBudget` (6 * 36 + 150 <= 400) |
| `HomeTagsTest` | `existingTagsUnchanged` (`home.state`, `home.start` equal `RobotTags.HOME_STATE/HOME_START`); `newTagsUnique` and match `^home\.[a-z.]+(\.\d+)?$`; `oneStartTagPerState` documented as a comment test |
| `NotificationGroupsTest` (existing) | stays green: `statusScrimAlpha` untouched |

Pure-ness: `HomeMetrics`, `HomeSuggestions`, `FireSwitch`, `HearthTaps(now)`, `Woodpile`, `buildHomeUi` and `HomeEntrance.shouldPlay` take every input as a parameter; the composables only collect flows and call them. Calendar facts need no table: the date test uses `java.time` with fixed instants; the only reference value is the English and Turkish short weekday and month names of the JDK.

## 9. Proof on the phone (owner-debug, safe scenarios only; the robot adds the token)

Link: `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route home --es clock 2027-01-15T22:00 --es place 59.9,10.7,Oslo --es weather rain:heavy,cloud:3,wind:0.6,temp:7 --es token <T>`. Pins I use (none is mine): `route`, `clock`, `lang`, `rtl`, `reset` (Wave 0); `place`, `weather` (WEATHER); SCENE's sky pin if it adds one. Entrance shots use `reset=1` and NO clock pin (a pinned clock skips the entrance).
New tags (all in `HomeTags`, exact): `home.hero`, `home.fire`, `home.pet`, `home.sky`, `home.strip`, `home.stop`, `home.cancel`, `home.error`, `home.error.details`, `home.ask`, `home.send`, `home.suggest.<i>`, `home.avatars`, `home.avatar.<i>`, `home.avatar.new`, `home.group`, `home.recent`, `home.recent.<i>`, `home.recent.empty`, `home.tile.hermes`, `home.tile.terminal`, `home.tile.routines`, `home.tile.saved`, `home.usage`, `home.woodpile`, `home.skeleton`, `home.row.<id>`. Kept: `home.state` (status line, always present), `home.start` (exactly one node while the state is NotInstalled, Stopped or Error), `stage.hearth` (SCENE).
Screenshots (each in NEWROZ dark and NERGIZ light, Running unless said): (1) 2027-01-15T22:00, expanded, night greeting, Oslo; (2) 2027-03-21T07:30 morning; (3) 2027-07-15T13:00 afternoon, bright sky, status icons dark (needs 7.LEAD); (4) 2027-03-21T18:30 evening; (5) the same moment at scroll 60, 160 and 300 dp (`swipe` op, `times`): mid-collapse and the strip, text cross-fade, fire never leaves; (6) the strip with `weather rain:heavy` (ink on the band, contrast); (7) fire states: Running; after a hold of 900 ms on the fire (`input swipe x y x y 900`, the ring mid-hold at 450 ms) Stopped and cold; one tap relights (fire catching, then lit); (8) `lang tr`, `lang fr`, `lang en-XZ` (stretched), font scale 2.0 and `rtl 1` (mirrored) in NEWROZ dark; (9) swipe a recent row right (pin) and left (mark read) at half way and past the threshold; long-press menu; (10) an account with a budget (WARN, limit 100000 on the twin through `seed usage:`; on owner-debug read the real budget if set) showing woodpile levels 5 and 1; (11) the entrance frames at 0, 300, 600, 900, 1200 ms after `reset=1` (HomeEntrance replayed); (12) no chats / first run / loading / error: JVM tests plus the twin when it can be installed (they need a wipe or a failed start).
Waits: `home.state` text, `home.hero`, `home.fire`, `home.ask`, `home.recent.0`. Behaviour proof: `home.start` visible with Hermes stopped, tap it, `home.state` becomes the running text; with a running practice answer (practice keyword `LONG`) the stop confirmation appears on a hold. Smoothness: `phone-perf` Home scroll scenario, before (build 32 state in `dist/`: 12 % of idle frames within 8.3 ms) and after, debug build for the record, the optimised build for the budget; `dumpsys gfxinfo` janky share and the 95th percentile frame time while scrolling from the top to the strip and back.

## 10. Cost

| What | Per frame | Per minute | Cached or avoided |
|---|---|---|---|
| Scene | one `SceneRenderer.render` per 30 fps tick (SCENE's budget under 1 ms on the optimised build; today about 10 ms idle on debug). Scrolling adds NO render per display frame: `collapse` is quantised to 33 values, the stage is allocated once for the full hero, only a changed quantised value or a clock tick redraws | the sky recompute is SCENE's, once a minute | the hero never relays out: `translationY` in a `graphicsLayer` lambda |
| Collapse | one `derivedStateOf` read in a draw lambda; two alpha lambdas for the cross-fade; no recomposition | none | `listState` is read only in lambdas; `HomeUi` is `@Immutable` and equal inputs skip |
| Hit targets | three small boxes with `offset { }` lambdas; `pointerInput` coroutines only while a finger is down | none | positions from one `sceneAnchors` call remembered per (size, quantised collapse) |
| Status and progress | `PixelProgressBar` animates only while Extracting or Starting (existing); the ring draws 12 pixel squares only during a hold | none | |
| Lists | LazyColumn of at most 9 items plus the registered rows (none in 1a); 4 `SwipeRow` pairs of state; avatar `LazyRow` | none | recent rows keyed by id; `ConversationRow` as in the Chats tab |
| Clock text | none per frame | one recomposition of the greeting and date when the minute changes (a `produceState` that sleeps until the next minute, only while STARTED; with a pinned clock it never ticks) | `HomeFormat.date` pattern fetched once per locale |
| Entrance | 900 ms of scene reveal plus six staggered cards, once per process; skipped when `Pocket.anim` is OFF or the clock is pinned | | |
| Sensors, timers, network | none | none | Home registers nothing; the weather request (`rememberWeather`) is WEATHER's and is dark |

Memory: no bitmap of its own; tile art goes through `SpriteBitmaps` (LRU 8 MB, `PixelImage.kt:59`). Nothing runs while the screen is not STARTED (`repeatOnLifecycle`, as `rememberSceneClock`). Budget claim to measure in 9: Home composition adds under 0.3 ms per frame during a scroll on the optimised build; Home with the scene alive scrolls with at least 95 % of frames within 8.3 ms (design language 11).

## 11. Risks, and where the plan or the design language is wrong or impossible given the real code

1. **Swipe actions.** Plan section 4 and design 4 say "swipe right pins, swipe left archives, a long swipe left deletes after an undo bar". `Conversation` (`data/Models.kt:84-102`) has no archived field, and `ChatRepository.deleteConversation` (`ChatRepository.kt:329`) removes the messages with no restore, so archive and undo do not exist. Built: right pins or unpins, left marks read or unread (both reversible, both existing methods). `SwipeRow` fires only the first action per side (`SwipeRow.kt:117-118`); the second and later ones exist only as TalkBack actions, so no destructive action goes into a list.
2. **`SwipeRow` needs an opaque row.** Its colour layer sits behind the content (`SwipeRow.kt:134-153`) and the content is one merged accessibility node (line 157): `ConversationRow` is wrapped in a `Box` with `c.bg`, and its own click label is kept by the merge.
3. **Strip height.** Design says 96 dp; the fire sprite alone is 36 rows plus the ground below it, and one logical pixel is 7 device pixels on a 1080-wide phone (`StageGrid.kt:17-25`), about 101 dp at density 2.625. `HomeMetrics.stripPx` takes `max(96 dp, 38 rows)`: the strip may be a few dp taller than 96 on some phones. 46 % of the container (about 368 dp on an 800 dp container) is much more than today's fixed 280 dp (`HearthScene.kt:96`), so the first screen shows the prompt card lower: the constants are one place (`HomeMetrics`) for the Look step to change.
4. **Fire and Pêt targets steal drags.** The two targets sit above the list; a drag that starts exactly on the fire or on Pêt does not scroll the list (Compose gives pointer input to the topmost hit sibling, and `sharePointerInputWithSiblings` is not public). They are small (the fire is about 80 dp wide) and the sky tap lives on the list's own item 0, so the rest of the scene scrolls normally. Accepted; the Look step checks on the phone that a swipe from the middle of the scene still scrolls. When the hero is at least half collapsed the targets move with the scene and stay small.
5. **Status-bar icons.** `AppRoot.kt:232-234` forces light icons on Home because the sky was always dark; a real day sky (plan 1.2, in light themes especially) makes them unreadable without request 7.LEAD. The hero's top 24 dp can add a dark dither plate if the request is refused (not built: noted for the reviewer).
6. **Hero text ink.** The greeting sits on the scene's ground band; its colour is only known to SCENE (`HearthInfo.bandArgb`). If SCENE cannot supply it, `HeroInk` falls back to `Pocket.colors.text` on a `scrim` plate (`PocketColors.scrim`), costing the "on the scene" look.
7. **`AppClock` is read by nobody yet** (survey F, `AppClock.kt:10-12`): Home's greeting and date read `AppClock.now(zone)` so the robot's pinned clock works; `dayTimeLabel` and `relativeTime` (`ChatCommon.kt:192-223`) still use `System.currentTimeMillis()`, so recent rows show real elapsed times next to a pinned greeting. Acceptable; they are not mine.
8. **`SectionHeader` upper-cases** (`SectionHeader.kt:38`), against design 13.5 (headings in sentence case, never made by code); Home uses its own `HomeHeading`. The old Home also double-padded it (`HomeScreen.kt:304`).
9. **Hold to put out stops running answers.** `vm.stop()` ends the gateway and kills running tool commands (`AGENTS.md`, hard-won facts); the Stop button today has no confirmation. Built: a `ConfirmDialog` only when `engine.busy` is not empty (a rule the plan did not ask for).
10. **Design says a microphone in the prompt card** (8.1 sketch). Voice input lives in the chat (`ChatInput.kt:94, 151`), not in Home; not built in 1a (text field and send only).
11. **Plan 1.1 "the entrance, timed to the real ten seconds Hermes needs".** That is progress (design language 2.4), which already exists as KINDLING with `progress`; the entrance is only the 900 ms reveal and the stagger, so it never exceeds the 400 ms rule for cards.
12. **Plan 1.1 "from Wave 8 Talk by the fire"** needs no hook now: `SuggestionText` is an enum and `SuggestionInput` can grow a field with a default.
13. **Later rows cannot register without a line in a file of mine** (Kotlin has no registration without reflection or a service file, which R8 would break); the one-line append in `rows/HomeRows.kt` is the cheapest honest answer, and `HomeRowRegistryTest` documents it.
14. **`ui_home_mascot_cd` says "salamander"** (`strings_chat.xml:47`); replaced by `hm_pet_cd`.
15. **Kotlin traps for an agent that cannot compile:** no slash-star inside a comment (write "the strings files"); the first list item and `listState` must be declared before the `Box` that reads them; `0xFF...` literals need `.toInt()` when used as `Int`; `Modifier.graphicsLayer { }` and `drawBehind { }` lambdas may read states, the composition body must not read `collapse()`; declare every `by lazy`/property before the `init` that uses it; `awaitEachGesture` needs `androidx.compose.foundation.gestures` imports; format dates with an explicit `Locale`; the plural `ui_home_unread` takes the count twice (`tn(..., n, n)`).
16. **Parity oracle.** The 84 dp band changes `fbase` (`SceneRenderer.kt:105-139` uses `height - 3`), so `SceneRendererParityTest` fails as soon as Home passes `bottomBand`; SCENE retires the oracle (request 7.SCENE) before HOME's first integrated build.
