# Wave 1b, area BARS: contract draft

`J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`; package `dev.pocketagent.app` is written `app`. Written 2026-11-25 against the work branch tree (files read at that moment) and against `docs/waves/w1a-contract.md` (its registry, section 3, and its amendments, section 5). **Everything this draft takes from Wave 1a is taken from that contract, not from today's code: 1a is not in the work branch.** I also looked at the 1a worktree `dist/wt-w1` (read only, unmerged, still moving): `service/FireFeed.kt` (`FireFeed.level()`, `FireFeed.state`), `ui/pixel/pet/PetPeek.kt` (`PetPeek(working, modifier, enabled)`) and `ui/home/HomeTags.kt` exist there as the registry spells them; `HomeHero.kt`, `HomeLists.kt`, `HomeStatus.kt` and the rewritten `HomeScreen.kt` (1a slice D) do not exist yet. So every statement about those four files below is **drafted against the registry (1a 3.8, `HOME.md` 3.7 and 3.9) and must be re-read against the 1a final files before the first BARS agent starts slice C.** Split and file ownership: `docs/waves/w1b/SPLIT.md` 4.2.

BINDING from `docs/design-language.md`: 3 pattern 12 (thinking: the fire in the top bar burns higher), 8.1 (Home: Settings is a gear at the top of the screen), 8.2 (a chat: a thin line of fire under the bar burns while the agent works), 7 last bullet (Pêt outside Home; every empty state its own picture), 8.15 (the map: gear on Home and on the Agent tab), 8.16 (six kinds, five states), 9 (components: `EmberLine`, `PocketTopBar`, larger pictures), 10 (a spoken label on every control, 48 dp touch, reading order written down), 13.2 (screen title room 28), 13.5 (no case change by code). Plan: `docs/phase4-plan.md` 1.4 (the thin fire line in the chat's top bar reads `FireFeed`), 1.5 (the small Pêt, eight empty pictures), 1.6 (gear, fire line in top bars), 4.1 (Settings behind a gear on Home and in the Agent tab).

## 1. Scope

What the plan and the design language ask of BARS, one row each (B-numbers are SPLIT.md section 2):

| # | Work item | Plan / DL | Kind |
|---|---|---|---|
| G1 | The Settings gear on Home: a labelled 48 dp button at the top end, tag `home.gear`, opens `Route.Settings()` pushed above Home | B7; plan 4.1; DL 8.1 l.455 | core |
| G2 | The gear in the Agent tab's top bar and, as a general feature, in `PocketTopBar` (`onSettings`, tag `top.gear`) | B7; DL 8.15 | core |
| G3 | The Agent tab's title: "Agent" (new key `bar_agent_title`), the Hermes tile on Home gets the same label | B5 (label), B7 | core |
| L1 | `EmberLine` takes a level (`level: () -> Float`) read in the draw phase only; `EmberLevel` pure mapping from level to a lit share and tones | B2; plan 1.4; DL pattern 12 | core |
| L2 | The chat's top bar burns while the chat generates, at the level of `FireFeed.level()` | B2; DL 8.2 | core |
| P1 | `PocketTopBar.overEdge` slot and the small Pêt (`PetPeek`) placed over the chat bar's lower edge while the chat generates, when `Appearance.petInChatsVisible` | B23 (placement); PET draft 3.7 | core (draws nothing if 1a cut `PetPeek` or its sprite) |
| E1 | `EmptyState` gains `art: String? = null`; one `art =` argument at four call sites; fallback is today's picture | B24 (placement) | core |
| T1 | Home tile targets: the Saved tile opens `Route.Library(LibraryShelf.SAVED)`, the Hermes tile stays `Route.Hermes` and gets the Agent label | B5, B6 | core |
| A1 | One `FireAnchor.publish(...)` call in `HomeHero.kt` for the splash (START's S8) | B3 | core call, but the handoff it feeds is START's cut-first extra |
| D1 | Debug pin `BarsDebug` and the extra `bars`: forces the line and the peek on in any chat (memory only, `TEST_HOOKS`) | proof | extra |
| X1 | The Agent tab's bar burns while any turn is open anywhere (`FireFeed.state`) | B2 | extra |
| X2 | `DesignGallery.kt` rows that show the five levels of the line, a bar with the gear, a bar with the peek and five empty states, so the owner's own build can be photographed without touching a chat | proof | extra, but wanted |

Cut order, first cut first (a cut item keeps its symbol with an empty body or a default): X1, X2, D1, A1 (only if START cuts the handoff), P1 (the symbol `overEdge` stays), the `art =` argument of the three call sites other than `ChatListScreen.kt` (the parameter stays), the level steps of `EmberLine` (the parameter stays and is ignored: the line is today's line). **Never cut:** G1, G2, G3, T1, the `EmptyState.art` parameter, the `EmberLine.level` parameter, the chat's line (L2).

## 2. What exists today, and what 1a will have changed by then

Today (work branch):
- `J/ui/components/PocketTopBar.kt` (117 lines): parameters `title, onBack, subtitle, leading, actions, ember, titleModifier` (lines 42-50); one Column with the status-bar inset, a 56 dp row, a 2 dp (`Space.xxs`) border line; `ember = true` puts `EmberLine(active = true)` on that line (line 114). Tags `top.back` (69), `top.title` (83). **Nobody passes `ember = true` except `DesignGallery.kt:69`** (`grep "ember = "`): the chat has no fire line today.
- `J/ui/components/EmberLine.kt` (93 lines): `EmberLine(active, modifier)`; 7 flicker pictures per `Loop.EMBER_LINE` (1200 ms), runs of 2 to 9 design pixels in `fireLow/fireMid/fireHigh` chosen by `emberMix(step, run)` (bits 0-2 the length, bits 3-4 the colour); at `AnimLevel.OFF` (`!motion.loops`) the line stands still; spoken label `design_ember_active` ("Working"), tag `ember`. `emberMix` is `internal`.
- `J/ui/components/EmptyState.kt` (74 lines): `EmptyState(title, body, modifier, action)`; the picture is `Mascot(IDLE, 96.dp)` when `Appearance.showMascot`, else `PixelIconView(SPARK, 48.dp)` (lines 43-47).
- `J/ui/chat/ChatTopArea.kt` (134 lines): `PocketTopBar(...)` at 65-98 with `onBack`, `subtitle`, `leading = AvatarStack`, actions (pin, find, controls, more), `titleModifier` for the shared bounds; `generating = convId in busySet` (line 49).
- `J/ui/hermes/HermesScreen.kt` (185 lines): `PocketTopBar(title = t(R.string.ctl_hermes_title), subtitle = t(R.string.ctl_hermes_subtitle))` at line 94, no actions; `nav` is already in scope (line 71).
- The four call sites of `EmptyState` that are BARS's: `J/ui/chat/ChatListScreen.kt:167` (no chats), `J/ui/bots/BotsScreen.kt:117` (no bots), `J/ui/chat/ChatMessageList.kt:146` (a chat with no messages, inside the `chat.empty` column), `J/ui/routines/RoutinesScreen.kt:132` (no routines). The fifth, `J/ui/chat/StarredScreen.kt:77`, is LIBRARY's (it becomes `StarredList`).
- `J/ui/home/HomeScreen.kt` (454 lines, replaced by 1a): the four tiles at lines 354-365 call `nav.go(Route.Hermes)`, `nav.go(Route.Terminal)`, `nav.go(Route.Routines)`, `nav.go(Route.Starred)`; there is no gear, no top bar.
- Strings: `ctl_hermes_title` ("Hermes", `R/values/strings_control.xml:90`, the same word in `values-tr` and `values-fr`), `ctl_hermes_subtitle` ("The agent behind every bot", line 91), `ui_home_tile_hermes` ("Hermes inspector", `strings_chat.xml:45`), `ui_home_tile_saved` ("Saved messages", line 331). The icon `PixelIcon.GEAR` (sprite `icon_gear`) exists. `PixelIconButton` is 48 dp (`Space.touch`) with a 24 dp icon, plays `HapticCue.CLICK`, and takes `contentDescription` (`PixelButton.kt:169`).
- Tests: none exist for `EmberLine`, `PocketTopBar` or `EmptyState`.

By the time BARS builds (from the 1a contract):
- `FireFeed.level(): Float` and `FireFeed.state: StateFlow<FireFeedState>` (3.4; `FireFeedState(heat, heatAtMs, tools: Set<String>, turns: Set<String>)`); level 0 when idle, floor 0.35 while a turn is open, 0.90 while a tool runs, text raises it to at most 0.70, half-life 9 s (`FireMath`). A pin (`fire` extra `level:0..1`) overrides it. Never cut in 1a.
- `PetPeek(working: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true)` in `app.ui.pixel.pet` (3.5, an **extra** of 1a, cut order item 5, "nobody places it in 1a"): draws `pet_peek` (16 x 10, six frames, 1450 ms, played once) at a whole-number scale of at most 32 dp wide, slides up from below its own clip edge with `motion.fast()` while `working && enabled && Pocket.anim != OFF`, slides down when not, draws nothing and never throws when the sprite is missing, `clearAndSetSemantics {}`. The box is the sprite's size at that scale (about 30 x 19 dp at density 2.625).
- `val Appearance.petInChatsVisible: Boolean` (= `showMascot && petInChats`, default true; 3.7). The row that edits `petInChats` is APPEAR-B's.
- `IllustrationId` in `J/ui/pixel/Illustrations.kt` (1a ART-WORLD 3.3): `.sprite` names `empty_bubble`, `empty_scroll`, `empty_stool`, `empty_jar`, `empty_hourglass`, `empty_lantern`, `empty_boat`, `empty_cup`, `empty_gate` (32 x 32, 2 frames 1600 and 160 ms) and `tile_hermes`, `tile_saved` ... (24 x 24). In 1a only `empty_bubble` and the four tiles are core; LOOK may cut any other; ART-B (1b) draws what was cut under the same names. The mapping of picture to screen is ART-WORLD 3.5: `empty_bubble` chats list, `empty_scroll` an empty chat, `empty_stool` bots, `empty_hourglass` routines, `empty_jar` saved.
- `HomeTags` (3.8) with `HERO, FIRE, STRIP, TILE_HERMES, TILE_SAVED` and the rest; `HomeSky`, `HearthInfo`; Home 2.0: `HomeScreen()` is one `BoxWithConstraints` with `HomeHero(...)` behind the list, the list (item 0 is `HomeHeroSpacer`), the pinned strip and the fire and Pêt hit targets above the list; `HomeTile(icon, label, onClick, modifier, subtitle, art)`; the four tiles are drawn by `HomeTiles` in `HomeLists.kt` (`HOME.md` 3.9). The fire's hit rectangle is `FireGeometry(geo).hitRect(grid, minPx)` (3.3), positioned with an `offset { }` lambda, tag `home.fire`.
- Wave 0 (`docs/waves/w0-contract.md`): `LocalNavigator`, `Navigator.go/tab/back`, `Space`, `Corner`, `Pocket.colors/anim/motion/appearance`, `PixelShape(corner, pixel)`, `AppLog`, `BuildConfig.TEST_HOOKS`, `t()`.
- Wave 1b (siblings, this contract set): SHELL gives `Route.Library(shelf)`, `shell_gear_cd` ("Settings"), `Route.Settings` as a pushed route; LIBRARY gives `LibraryShelf.SAVED`; START gives `FireAnchor`; APPEAR-B gives the Pêt-in-chats row and the back arrow of Settings; ART-B draws the missing pictures.

## 3. The design

### 3.1 New files (BARS owns them; none exists today)

| File | Content | Pure? |
|---|---|---|
| `J/ui/components/EmberLevel.kt` | `internal object EmberLevel` (3.3) | yes, no import |
| `J/ui/components/BarTags.kt` | `object BarTags` (3.7) | yes |
| `J/ui/components/BarsPin.kt` | `class BarsPin`, `object BarsRules` (3.8) | yes |
| `J/ui/components/BarsDebug.kt` | `object BarsDebug` (3.8) | no (Compose state, `BuildConfig`, `AppLog`) |
| `J/ui/home/HomeGearMath.kt` | `internal object HomeGearMath` (3.4) | yes |
| `J/ui/home/HomeGear.kt` | `@Composable fun HomeGear(modifier)` (3.4) | no |
| `R/values/strings_bars.xml` | prefix `bar_`: one key (3.9) | |

### 3.2 `PocketTopBar` (edited): the gear and the overlay slot

```kotlin
@Composable
fun PocketTopBar(
    title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, ember: Boolean = false, titleModifier: Modifier = Modifier,   // the seven that exist, same order
    emberLevel: () -> Float = EmberLevel.FULL,                      // read in the draw phase by EmberLine; only used while ember is true
    overEdge: (@Composable BoxScope.() -> Unit)? = null,            // drawn over the bar's lower edge, see below
    onSettings: (() -> Unit)? = null,                                // last, as SPLIT 4.2 says
)
```
All existing callers use named arguments or only the first parameters (`grep "PocketTopBar("`: `BotEditorScreen`, `BotsScreen`, `ChatListScreen`, `ChatScreen`, `ChatTopArea`, `StarredScreen`, `DesignGallery`, `HermesScreen`, `TerminalScreen`, `RoutineEditorScreen`, `RoutinesScreen`, `SettingsScreen`); the three new parameters are appended, so none changes.
- **Gear.** After `actions()` and before the closing `Spacer(Space.xs)`: `if (onSettings != null) PixelIconButton(PixelIcon.GEAR, onSettings, Modifier.testTag(BarTags.TOP_GEAR), contentDescription = t(R.string.shell_gear_cd))`. It is the last (outermost) action, so on every screen that has it the gear is in the same place (centre 28 dp below the status bar, 4 dp + 24 dp from the end edge), which is also where Home's gear sits (3.4). In a right-to-left layout it is on the left with the row; `GEAR` does not mirror.
- **Line.** `EmberLine(active = true, modifier = ..., level = emberLevel)` (the call at line 114; nothing else of the bar changes: the 2 dp line keeps its height, so the layout does not move).
- **Overlay.** The existing Column is wrapped in `Box(Modifier.fillMaxWidth()) { Column(...) { ...unchanged... }; if (overEdge != null) overEdge() }`. The Box is exactly as tall as the Column (status inset, row, line), so `Modifier.align(Alignment.BottomEnd)` inside `overEdge` lands on the line's lower edge. The overlay is decorative and must not take touches (a `Spacer` or a drawing, no `clickable`); a node without pointer input lets a touch fall through to the button below.
- KDoc updated: tags `top.back`, `top.title`, `top.gear`; the contract of the three new parameters.

### 3.3 `EmberLine` and `EmberLevel`

```kotlin
@Composable
fun EmberLine(active: Boolean, modifier: Modifier = Modifier, level: () -> Float = EmberLevel.FULL)
```
The parameter is read as `val levelNow by rememberUpdatedState(level)` and **only inside `onDrawBehind`** (`EmberLevel.step(levelNow())`), never in composition, so a new lambda reaches the cached drawing and a changing level never recomposes. The flicker phase still comes from the one infinite transition at `Loop.EMBER_LINE`; at `OFF` (`!motion.loops`) the line stands still and is drawn once at the level of that moment; REDUCED loops as today. `active = false` still draws nothing and keeps its height.

```kotlin
internal object EmberLevel {                          // pure, no import, JVM-testable
    const val TONE_LOW = 0; const val TONE_MID = 1; const val TONE_HIGH = 2
    const val TOP_STEP = 4                            // steps 0..4
    val FULL: () -> Float                             // { 1f }: the default, the step that is today's picture
    fun clamp(level: Float): Float                    // NaN gives 0; coerced to 0..1
    fun step(level: Float): Int                       // edges 0.10, 0.30, 0.55, 0.85: below 0.10 is 0, below 0.30 is 1, below 0.55 is 2, below 0.85 is 3, else 4
    fun litEighths(step: Int): Int                    // 3, 4, 5, 6, 8 (the step is coerced to 0..4)
    fun runLit(h: Int, step: Int): Boolean            // ((h ushr 5) and 7) < litEighths(step)
    fun tone(h: Int, step: Int): Int                  // TONES[step][(h shr 3) and 3]
}
```
`h` is `emberMix(flickerStep, run)` of today's code. The base row (`fireLow`) is always lit as today; the **top row** is drawn run by run as today, except that a run is skipped when `!runLit(h, step)` and its colour is `tone(h, step)` (`TONE_LOW` is `fireLow`, `TONE_MID` `fireMid`, `TONE_HIGH` `fireHigh`). Run length stays `2 + (h and 7)`, so bits 0-2 (length), 3-4 (tone) and 5-7 (lit) of the hash are independent. Tone tables (`[step][(h shr 3) and 3]`): step 0 `[0,0,0,1]`, step 1 `[0,0,1,1]`, step 2 `[0,1,1,1]`, step 3 `[0,1,2,1]`, **step 4 `[1,2,1,0]`, which is exactly today's `when` (mid, high, mid, low)**. With `litEighths(4) = 8` every run is lit, so **the default level `FULL` draws today's picture, pixel for pixel**. What each step means on a chat bar: 0 the line is alive but resting (about 3 of 8 runs lit, low tone), 1 and 2 a turn is open and nothing has arrived yet (the floor 0.35 is step 2), 3 text is arriving (0.55 to 0.85; text alone caps at 0.70), 4 a tool runs (0.90): the full line. Look knobs (LOOK-2 may turn them without logic): the four edges, `litEighths`, the tone rows.

```kotlin
object EmberFeed { val level: () -> Float = { FireFeed.level() } }   // in EmberLine.kt; the one feed every bar uses
```
(A named holder instead of a top-level `val`, because `ui.components` is a shared package: no new top-level symbols outside the registry.)

### 3.4 Home's gear: `HomeGearMath.kt`, `HomeGear.kt`, and the edits of 1a's Home files

```kotlin
internal object HomeGearMath {                        // pure
    const val TOUCH_DP = 48f                          // = Space.touch.value (a test equals it)
    const val PLATE_DP = 40f                          // the plate behind the icon
    const val PLATE_ALPHA = 0.72f                     // look knob
    const val RESERVE_DP = 56f                        // room the strip's text leaves at the end: 48 + 4 + 4
    const val TRAVERSAL_INDEX = -1f                   // reading order, see 5.3
}
@Composable fun HomeGear(modifier: Modifier = Modifier)
```
`HomeGear` draws, in a `Box` with `statusBarsPadding()`, `padding(top = Space.xs, end = Space.xs)` and `semantics { isTraversalGroup = true; traversalIndex = HomeGearMath.TRAVERSAL_INDEX }`: a plate (`Spacer(Modifier.size(HomeGearMath.PLATE_DP.dp).background(c.bgDeep.copy(alpha = HomeGearMath.PLATE_ALPHA), PixelShape(Corner.card, Space.xxs)))`) and on it `PixelIconButton(PixelIcon.GEAR, onClick = { nav.go(Route.Settings()) }, Modifier.testTag(BarTags.HOME_GEAR), contentDescription = t(R.string.shell_gear_cd))`, with the icon in `Pocket.colors.text`. **Why a plate and not an ink picked from the sky:** the top of Home is the live sky (any colour from night blue to a bright day) and, collapsed, the strip's band; the icon on a plate of the theme's own deep background keeps its contrast whatever is behind it, in both themes (a test, 4.4); the alternative, `HeroInk.pick(info.topArgb)`, fails on the mid-luminance day sky that 1a's own contract notes (luma 0.43 to 0.47). Not `PixelPanel` (its border colour shows through a translucent fill, 1a gotcha); a plain `background` with `PixelShape`.

Edits to 1a's files (BARS is their only 1b editor; each is small; the exact text is found in the 1a final files):
1. **`HomeScreen.kt`**: `HomeGear(Modifier.align(Alignment.TopEnd))` as the **last child** of the screen's root box, after the hero, the list and the strip, so it is above them in z-order and in hit testing (1a risk 4: the topmost hit sibling gets the touch; the gear must not sit inside `HomeHero`, which is drawn behind the list). The strip's "tap to scroll to the top" is below the gear, so the gear wins. No other change to `HomeScreen.kt`.
2. **`HomeHero.kt`**: (a) the pinned strip's text column (title and status line, tag `home.strip`) gets `padding(end = HomeGearMath.RESERVE_DP.dp)` so a long status never runs under the gear (`RESERVE_DP` is a named Float constant with `.dp` applied: the lint ratchet counts only a digit literal followed by `.dp`); (b) the one `FireAnchor.publish` call (A1): on the fire hit box (tag `home.fire`) `Modifier.onGloballyPositioned { c -> ... }`: when the quantised collapse is 0 and the stage is laid out, `val p = c.positionInWindow()` and `FireAnchor.publish(p.x + c.size.width / 2f, p.y + c.size.height / 2f, sizePx)`, where `sizePx` is the larger side of `FireGeometry(geo).hitRect(grid, 0f)` (the natural, ungrown rectangle: the flame itself) as a Float. The rest position is what the splash must land on, so nothing is published while the hero is collapsed. `FireAnchor` ignores equal values (START). Slice D, after START's file exists; if it does not exist the call is left out and reported.
3. **`HomeLists.kt`** (the four tiles of `HomeTiles`; if the lambdas live in `HomeScreen.kt` they are edited there): the Saved tile's `onClick` becomes `{ nav.go(Route.Library(LibraryShelf.SAVED)) }` (label `ui_home_tile_saved` and art `tile_saved` stay; `Route.Starred` stays alive for old links); the Hermes tile keeps `{ nav.go(Route.Hermes) }` (the route and its key `hermes` do not change; only the tab's label does) and its label becomes `t(R.string.bar_agent_title)`; art `tile_hermes` stays; Terminal and Routines are untouched. `go`, not `tab`, as today: the back gesture returns to Home, and a pushed `Library` or `Hermes` entry shows the bar with its own tab selected (request R4). Tags `home.tile.*` are unchanged.
4. `HomeParts.kt` and `HomeStatus.kt` are **not** edited (SPLIT lists them only because their final shape was not known; this draft needs neither).

### 3.5 The Agent tab (`HermesScreen.kt`)
Line 94 becomes `PocketTopBar(title = t(R.string.bar_agent_title), subtitle = t(R.string.ctl_hermes_subtitle), onSettings = { nav.go(Route.Settings()) })`. Nothing else moves (the status panel, the six inspector tabs, the terminal button; the six rooms are Waves 3, 4 and 11). `ctl_hermes_title` is no longer used: its three one-line deletions (`values`, `values-tr`, `values-fr` of `strings_control.xml`) are part of the same change (the strings check counts an unused key as a warning and baselines never rise, 1a ruling 2). Unsaved edits in the Soul, Memory and About-you tabs survive the push to Settings and back (`HermesEditorsViewModel` is an activity-scoped view model; checked on the phone, 5.1 item 6).

### 3.6 The chat's line and Pêt's peek (`ChatTopArea.kt`)
```kotlin
val pin = BarsDebug.pinned                                        // snapshot state, null outside test builds
val emberOn = BarsRules.emberOn(generating, pin)
val peekOn = BarsRules.peekWorking(generating, pin)
PocketTopBar(
    ...the six arguments that exist (title, onBack, subtitle, leading, actions, titleModifier)...,
    ember = emberOn, emberLevel = EmberFeed.level,
    overEdge = if (Pocket.appearance.petInChatsVisible) {
        { PetPeek(working = peekOn, modifier = Modifier.align(Alignment.BottomEnd).padding(end = Space.xs, bottom = Space.xxs).testTag(BarTags.peek(peekOn))) }
    } else null,
)
```
- The line burns while **this chat** generates (`generating`, which includes the wake-on-send moment) and at the level of the **process-wide** fire. That is a small lie when another chat runs a tool (the line of this chat then roars while it still only waits); accepted, it is one fire (plan 1.4). When the turn ends the line stops at once (no tail: simple, and a tail would speak "Working" after the answer).
- The peek stands on the bar's lower edge at the end side. `bottom = Space.xxs` is the border line's height: the line stays visible under the sprite, and because `PetPeek` clips at its own bottom edge the head seems to rise from behind the line. It overlaps the lower 3 dp of the "more" button's icon area, which is empty in that glyph, and the tail of a long subtitle; both are accepted and judged by the owner's eyes (5.4). Touches fall through (no pointer input).
- `PetPeek` is composed only while `petInChatsVisible` is true (the setting off removes the node, so its tag is absent: `chat.peek` or `chat.peek.working` present means "composed"), and the sprite missing or animations OFF draw nothing inside it, as 1a built it. `PetPeek` is decorative for screen readers; the spoken facts are the subtitle ("typing...") and `design_ember_active`.
- A group chat behaves the same way (`generating` is per conversation).
- Nothing else in the file changes (shared bounds, find bar, participant bar, banners).

### 3.7 Tags
```kotlin
object BarTags {                                                  // BarTags.kt, pure
    const val TOP_GEAR = "top.gear"; const val HOME_GEAR = "home.gear"
    const val PEEK = "chat.peek"; const val PEEK_WORKING = "chat.peek.working"
    const val EMBER = "ember"                                      // exists since Wave 0, kept
    fun peek(working: Boolean): String = if (working) PEEK_WORKING else PEEK
}
```
`home.gear` is not added to 1a's `HomeTags` (an object of HOME's files); it lives here, and `HomeGearTagsTest` proves it equals no `HomeTags`, `NavTags` or `RobotTags` constant. INTEGRATE-B adds `top.gear`, `home.gear`, `chat.peek`, `chat.peek.working` to `ScenarioFilesTest`'s known tags (`ember` is known).

### 3.8 The debug pin (D1, extra)
```kotlin
class BarsPin(val ember: Boolean, val peek: Boolean) { companion object { fun parse(text: String): BarsPin? } }   // pure
object BarsRules {                                                                                               // pure
    fun emberOn(generating: Boolean, pin: BarsPin?): Boolean = generating || pin?.ember == true
    fun peekWorking(generating: Boolean, pin: BarsPin?): Boolean = generating || pin?.peek == true
}
object BarsDebug { val pinned: BarsPin?; fun pin(p: BarsPin?) }     // memory only; pin() does nothing unless BuildConfig.TEST_HOOKS; logs "I/Bars: pin ember=<b> peek=<b>" (or "cleared")
```
Grammar of the debug extra `bars`: `off` (the extra's `Setting.Clear`; handled by `DebugLinks`, not by `parse`), or items `ember`, `peek` joined by commas, in either order, repeats allowed, spaces around an item trimmed, lower case only (a `when` over the trimmed item, no `lowercase()`); anything else, an empty text or an empty item makes `parse` return null and the extra is `rejected`. The pin forces the line (with `fire level:...` giving its height) and the peek on in whatever chat is open, so a chat can be photographed on the owner's build without sending a message; `reset=1` calls `BarsDebug.pin(null)`. `DebugLinks.kt` is SHELL's: request R3.

### 3.9 Strings (`R/values/strings_bars.xml`, prefix `bar_`, English only; stage 4 writes `tr` and `fr`)
Reused unchanged: `design_ember_active` ("Working", the line's spoken label), `design_back`, `ctl_hermes_subtitle`, `ui_home_tile_saved`, `shell_gear_cd` (SHELL's key, English "Settings", used by `PocketTopBar` and `HomeGear`).

| Key | English | Note |
|---|---|---|
| `bar_agent_title` | Agent | `<!-- t: The name of the agent screen: the title of its tab and the label of the Home tile that opens it. "Agent" as in an AI agent, one word, sentence case; the tab label next to it is shell_tab_agent and must be the same word \| kind: title -->` |

That is the whole file: the gear, the line and the peek speak through keys that already exist, and the peek is decorative. Keys removed in the same change so none is left unused: `ctl_hermes_title` (BARS, in the three `strings_control.xml`), `ui_home_tile_hermes` (SHELL, in the three `strings_chat.xml`, request R2).

### 3.10 `EmptyState` (edited) and the four call sites
```kotlin
@Composable fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, art: String? = null)
internal object EmptyStateRules {                                    // pure, in EmptyState.kt
    const val ART_DP = 96f                                            // 32 px at 3 dp: the box today's Mascot has
    enum class Picture { ART, MASCOT, SPARK }
    fun picture(art: String?, artFound: Boolean, showMascot: Boolean): Picture   // art != null && artFound: ART; else showMascot: MASCOT; else SPARK
}
```
`art` is the last parameter, so the positional call at `StarredScreen.kt:77` and the others keep compiling. In composition: `val found = remember(art) { art != null && runCatching { SpriteBank.find(art) != null }.getOrDefault(false) }`; then `ART` draws `PixelImage(art, Modifier.size(EmptyStateRules.ART_DP.dp))` (two frames at 1600 and 160 ms, a blink; animated by `Pocket.anim` as every sprite; decorative, no semantics), `MASCOT` and `SPARK` are today's code unchanged. **The art ignores `showMascot`** (these are objects of the hearth, never Pêt: ART-WORLD 3.5); only the fallback follows it. Title, body and action are unchanged. The four call-site edits, one argument each (names from `IllustrationId`, so a renamed sprite is a compile error, not a blank):

| File:line | Argument added |
|---|---|
| `J/ui/chat/ChatListScreen.kt:167` | `art = IllustrationId.EMPTY_BUBBLE.sprite` |
| `J/ui/chat/ChatMessageList.kt:146` | `art = IllustrationId.EMPTY_SCROLL.sprite` |
| `J/ui/bots/BotsScreen.kt:117` | `art = IllustrationId.EMPTY_STOOL.sprite` |
| `J/ui/routines/RoutinesScreen.kt:132` | `art = IllustrationId.EMPTY_HOURGLASS.sprite` |

A sprite that LOOK cut in 1a and ART-B did not redraw simply is not found: the screen shows Pêt as today. `StarredScreen.kt` (`empty_jar`), the Library's shelves (`empty_lantern`, `empty_boat`, `empty_cup`, `empty_gate`) and any later screen pass their own `art`.

### 3.11 Stored data and migration
**None.** BARS adds no preference, file or database field. It reads `Appearance.petInChatsVisible` (1a's field, default true) and writes nothing. A rollback to the previous build changes nothing on disk. On an installation that already exists (the owner's): after the update the Settings tab is gone from the bar (SHELL) and the gear is there; the first chat that generates shows the line and, because "Pêt in chats" defaults to on, the small Pêt (plan 1.5 says on by default; the owner may switch it off in Appearance once APPEAR-B's row exists, 7).

### 3.12 The five states of what BARS touches
| Surface (kind) | Loading | Empty | Content | Error | Offline | Needs permission |
|---|---|---|---|---|---|---|
| Home gear (part of Home, a Stage over a List) | present in every state of Home from the first frame (1a's skeletons are under it) | present | present | present (the error card is 1a's) | not applicable: Settings needs no network | not applicable |
| Agent tab (today's Hermes screen, a Dashboard) | runtime `Extracting` or `Starting`: the status panel as today, with the gear and the title already there | `NotInstalled`: today's install button; the tab's real empty state is Wave 3 | running: as today | runtime `Error`: status dot and detail as today, Start button | not applicable (the agent is local) | not applicable |
| Chat's line and peek (part of a Detail screen) | the line burns from the moment the send is accepted, the wake-on-send moment included; the peek rises | no line, no peek | idle bar | the turn fails: `generating` ends, the line and the peek leave; the failure itself is the chat's (pattern 16) | not affected (the feed is local) | not applicable |
| `EmptyState` (the Empty state itself) | the host screen's skeleton shows first, never this | one picture, one sentence, one action, as 8.16 says | n/a | n/a (the Error picture `empty_cup` is the host's) | the host passes `empty_boat` | the host passes `empty_gate` |

### 3.13 Motion, haptics, sound, cost
- Motion: the line uses `Loop.EMBER_LINE` and `Ease.steps(7)` (unchanged) and steps between its five levels in one frame (a pixel look, no tween); the peek's rise and fall is `PetPeek`'s `motion.fast()` and plays once; the gear's press is `PixelIconButton`'s pattern 1 (`rememberPixelPress`); `Pocket.anim` OFF: still line, no peek. No new duration is written (the lint ratchet counts `delay(<digits>` and `durationMillis`).
- Haptics: `HapticCue.CLICK` from `PixelIconButton` on the gear; nothing else. Sound: none.
- Cost: one `FireFeed.level()` (a flow read, a clock read, one `pow`) per flicker step (about six a second) only while a turn runs and the chat is open; no recomposition from the level; no allocation per frame except what the line already does. Home adds one 48 dp button and one `onGloballyPositioned` callback.
- Ink and size knobs for the Look step: `HomeGearMath.PLATE_ALPHA`, `PLATE_DP`; `EmberLevel` edges, `litEighths` and tone rows; the peek's `bottom` offset.

### 3.14 Sketches (design language 8.16: only the parts that are new)
```
Home, expanded                         Home, scrolled (strip)                 Agent tab
┌─────────────────────────────┐       ┌─────────────────────────────┐       ┌─────────────────────────────┐
│ 21:02                  ▂▄▆ │       │ 21:02                  ▂▄▆ │       │ 21:02                  ▂▄▆ │
│                   ┌──┐     │       │ 🔥 Agirî             ┌──┐  │       │ Agent                 ┌──┐  │
│     ✦    ☾        │⚙ │     │       │    The fire is lit.  │⚙ │  │       │ The agent behind...   │⚙ │  │
│                   └──┘     │       │                      └──┘  │       ├─────────────────────────────┤
│  ridges, fire, Pêt …       │       └─────────────────────────────┘
Chat bar while it answers
│ ← (av) Trip plan             ⌕ ≡ ⋯ │   no gear in a chat: the middle icon is the controls sliders
│        thinking…             ╭◖◗╮  │   Pêt's head rises from behind the line, at the end
│▓▒▓▓▒▓▒▓▓▓▒▓▓▓▒▓▒▓▓▒▓▓▓▒▓▒▓▓▒▓▒▓▓▒ │   the line burns at the fire's level
```
(The chat's middle icon is `SLIDERS`, the chat controls; no gear is added to a chat.)

## 4. Unit tests (JUnit 4, JVM; the first three need nothing from 1a)

SPLIT lists `EmberLevelTest` and `HomeGearTagsTest`; `EmptyStateArtTest` and `BarsPinTest` are two more new files of mine (request R10).

| Class (file) | Tests: what each proves |
|---|---|
| `EmberLevelTest` (`T/ui/components/`) | `nanAndInfinityClamp` (NaN, +/- infinity, -0.1, 1.1); `stepEdges` (0.0, 0.099, 0.10, 0.299, 0.30, 0.549, 0.55, 0.849, 0.85, 1.0, 7.0, NaN give 0 0 1 1 2 2 3 3 4 4 4 0); `defaultFeedIsFullAndFullIsTheTopStep`; `litEighthsRiseAndTopIsAll` (3 4 5 6 8, strictly rising, the last is 8); `topStepLightsEveryRun` (for flicker steps 0..6 and runs 0..399, `runLit(emberMix(a, b), 4)` is true: the default picture keeps every run); `topStepTonesEqualToday` (for `h` in 0..255, `tone(h, 4)` equals today's `when ((h shr 3) and 3)` mid, high, mid, low); `lowStepsNeverUseHigh` (steps 0 and 1); `brightnessRisesWithEveryStep` (`litEighths * mean(tone row)` strictly rises); `litShareFollowsEighths` (over 4,000 hashes of `emberMix` the lit share is within 0.04 of `litEighths / 8` for steps 0..3); `toneAlwaysInRange`; `stepArgumentIsCoerced` (-3 and 99 behave as 0 and 4, no exception) |
| `HomeGearTagsTest` (`T/ui/home/`) | `tagStringsAreExact` (`top.gear`, `home.gear`, `chat.peek`, `chat.peek.working`, `ember`); `peekTagFollowsWorking`; `tagsFollowThePattern` (`^(top\|home\|chat)\.[a-z]+(\.[a-z]+)*$`); `noTagEqualsAnExistingOne` (compares the BarTags constants with the static string fields of `HomeTags`, `NavTags` and `RobotTags` by Java reflection; `Assume`s that `HomeTags` exists, so it runs before and after 1a is merged); `plateFitsInsideTheTouchTarget` (`PLATE_DP <= TOUCH_DP - 8`, `TOUCH_DP` equals `Space.touch.value`); `reserveCoversTheTouchTargetAndTheGutters` (`RESERVE_DP >= TOUCH_DP + 2 * Space.xs.value`); `plateAlphaIsAProperFraction`; `gearContrastOverAnySky` (for every `ThemeId`, dark and light, accent `THEME` via `paletteFor`: `PocketColors.text` over the plate composited over black, over white and over 0x808080 has a contrast ratio of at least 3.0, the figure for a graphic; the smallest ratio is printed) |
| `EmptyStateArtTest` (`T/ui/components/`) | `artWinsWhenFound`; `artIgnoresTheMascotSetting` (found art and `showMascot = false` still gives ART); `missingArtFallsBackToMascotWhenOn`; `missingArtFallsBackToSparkWhenOff`; `noArtKeepsTodaysPicture` (the two cells with `art = null`); `artBoxIsThreeDpPerSpritePixel` (`ART_DP / 32 == 3`, a whole multiple) |
| `BarsPinTest` (`T/ui/components/`) | `parsesTheThreeForms` (`ember`, `peek`, `ember,peek`, `peek,ember`); `repeatsAndSpacesAreFine` (`ember,ember`, `ember, peek`); `badValuesAreRejectedWhole` (empty, `,`, `ember,`, `Ember`, `ember;peek`, `ember,fire`, `off` all give null); `rulesCombineGenerationAndPin` (the 2 x 3 truth tables of `emberOn` and `peekWorking`: generation wins, a pin adds, no pin changes nothing) |

Existing tests that must stay green: `PaletteContrastTest` (not touched), `NavTagsTest` and `ScenarioFilesTest` (SHELL and INTEGRATE-B), 1a's `HomeTagsTest` (its regex `^home\.[a-z.]+(\.\d+)?$` is applied to `HomeTags` only, which BARS does not edit).

## 5. Proof on the phone

Categories as in SPLIT 10: **P** the owner-debug build with pins and pictures only, `"safe": true`, nothing that stores; **T** needs the twin (it waits for the owner's one "Install anyway" tap); **U** unit test; **E** the owner's eyes. The scenario file is `w1b-bars` (P; the lead writes it after INTEGRATE-B, with the `safeNames` edit) and a twin part `w1b-bars-chat` (T). Pins used, all existing or promised: `reset`, `route`, `clock`, `place`, `theme`, `dark`, `lang`, `rtl` (Wave 0 and 1a), `fire` (`level:0..1`, 1a FIRE), `bars` (D1, request R3). Every link starts with `reset 1`.

### 5.1 P (owner-debug, safe)
1. **Gear on Home.** `route home --es clock 2027-01-15T22:00 --es place 59.9,10.7,Oslo --es theme newroz --es dark dark`: wait `home.hero`; expect `home.gear` with bounds at least 48 dp square (a `uiautomator` dump gives them), its right edge within 8 dp of the screen's and its top below the status bar; picture. Again for `--es clock 2027-07-15T13:00 --es theme nergiz --es dark 0` (a bright sky: the contrast check) and for a dawn clock `2027-03-21T06:30`. Tap `home.gear`: expect `screen.settings`; back: expect `home.hero`. A second tap in the first 300 ms gives one Settings screen, not two (`go` ignores a route that is already on top).
2. **Gear in the strip.** Same pins, `swipe` up until `home.strip` is present: `home.gear` is still present with the same bounds (compared with item 1, within 2 px); the strip's status text ends before the gear (its right edge is left of the gear's left edge); picture. Tapping the strip away from the gear still scrolls to the top.
3. **Agent tab.** `route hermes`: expect `top.title` with the text "Agent" and `top.gear`; the gear's centre is 28 dp below the status bar (the same place as Home's gear); tap it: `screen.settings`; back: `screen.hermes` (the tag SHELL gives), the tab stays selected. Pictures in NEWROZ dark and NERGIZ light.
4. **Tiles.** On Home scroll to the tiles: `home.tile.hermes` shows the text "Agent"; tap it: the Agent tab is on top with the bar showing it selected, back returns to Home. `home.tile.saved`: expect `screen.library` and the Saved shelf selected (`library.shelf.saved`, LIBRARY's tag); back returns to Home. (The Library's old Saved route is checked by the lead: `route starred` still opens the saved messages.)
5. **The gallery** (X2): `route gallery`: pictures of `EmberLine` at 0.05, 0.2, 0.4, 0.7 and 1.0 (steps 0 to 4; the 1.0 line must equal the picture of build 0.2: the parity claim), a `PocketTopBar` with `onSettings`, a bar with `overEdge` showing `PetPeek(working = true)` (the peek rises, plays, stays on its last frame), and `EmptyState` with each of `empty_bubble`, `empty_scroll`, `empty_stool`, `empty_hourglass`, `empty_jar` and with a name that does not exist (Pêt, as today). Two themes. The art is only as good as ART-B's sheets; a missing sprite is the fallback picture, not a failure.
6. **No layout shift.** Pictures of the Chats, Bots, Routines and Terminal top bars before and after the change (the `PocketTopBar` rewrap): same heights and positions within 1 px. Soul text typed on the Agent tab survives the push to Settings and back (read only: the robot types nothing; the lead checks once by hand, or the twin scenario does).
7. **Language and layout.** `lang tr`, `lang fr`, `lang en-XZ` (the stretched test language), `rtl 1`, font scale 2.0 (the lead's `-AllowSystemSettings`) on Home (expanded and strip) and on the Agent tab: the gear is present and not cut, the title "Agent" and its translation fit (room 28), the strip's text does not run under the gear, the gear is on the left in `rtl 1` and the scene is not mirrored (DL 13.3). `strings-check.ps1 -ScanDump` on the dumps.
8. **A chat with the pin** (optional, only if the lead accepts opening one of the owner's chats, which can mark it read: a conversation with no unread badge): `route chat:<id> --es bars ember,peek --es fire level:0.9`: expect `ember` and `chat.peek.working`; pictures at level 0.1, 0.4, 0.7 and 0.9; `--es appearance pet:0` removes `chat.peek.working` and `chat.peek` (the node is not composed). If the lead declines, this item is item T3.
9. **Splash anchor.** With START's log line (assumed, request R8) the published centre equals the centre of `home.fire`'s bounds within 2 px and `sizePx` equals the natural fire rectangle; nothing is published at a collapsed scroll position.
10. **Labels.** `sweep-labels` (the existing robot sweep) finds a spoken label on `home.gear` and `top.gear` (`shell_gear_cd`) and none missing elsewhere.

### 5.2 T (the twin; the practice model is free)
1. `seed chat` and a message with the practice keyword `LONG`: while it streams `ember` is present and `chat.peek.working` appears; the line's picture changes with the level; when the answer ends both go (the peek's exit plays, then only `chat.peek` remains). `THINK` and `RUN_TOOL` give the other levels (a tool is step 4). Pictures at four moments.
2. A group chat (two bots) answers in turn: the line and the peek stay on until the last bot ends.
3. `appearance pet:0` during a stream: no peek in the next chat; with `anim:off` the line is still and there is no peek.
4. Empty states on a wiped twin (`seed clear`): Chats shows `empty_bubble`, a new chat shows `empty_scroll`, Routines shows `empty_hourglass`; Bots shows `empty_stool` only if the bot list can be emptied (`seed` may not allow it: then item 5.1.5 carries it). A cut sprite shows Pêt.
5. `phone-perf` of the chat while streaming with and without the line: no frame over 16.7 ms added by the line.

### 5.3 Reading order (written down, DL 10)
Home: gear first (`traversalIndex = -1`), then the hero's spoken scene, then the prompt and what 1a lists. Agent tab: the bar's title, subtitle, gear, then the status panel. The chat bar: back, avatar, title and subtitle, the actions; the line says "Working" (`design_ember_active`) and the peek is skipped. Only a person with TalkBack can confirm that `traversalIndex` is honoured in that group; the robot cannot.

### 5.4 What only the owner's eyes can judge
The gear's plate (is it a button over the sky, or too heavy), whether the line's five levels read as "burning higher", whether the peek over the bar's end is charming or in the way (and whether it should sit left of the action icons instead), the empty pictures next to their sentences, the word "Agent" for the tab and the tile, and the TalkBack order.

## 6. Core and extras, order of cutting
Core: G1, G2, G3, L1, L2, P1, E1, T1, A1. Extras: X1, X2, D1. Order of cutting (first cut first): 1. X1 the Agent bar's line; 2. X2 the gallery rows (the pictures then come from the twin); 3. D1 the pin (the chat is then proven on the twin only); 4. A1 if START cuts the handoff; 5. P1's placement (`overEdge` stays); 6. the `art =` argument of `ChatMessageList`, `BotsScreen`, `RoutinesScreen` (the parameter stays, `ChatListScreen` keeps its bubble); 7. the level steps (`EmberLine` ignores `level`). Never cut: G1, G2, G3, T1, L2, the `EmptyState.art` and `EmberLine.level` parameters. A cut keeps the symbol with an empty body or a default.

## 7. REQUESTS to other areas (exact) and what BARS provides

**Requests**
- **R1 SHELL.** `shell_gear_cd` (English "Settings", `kind: button`, a spoken label that is never shown) exists in `strings_shell.xml` in the same slice as BARS's edits; `PocketTopBar` and `HomeGear` use `R.string.shell_gear_cd`.
- **R2 SHELL.** Delete `ui_home_tile_hermes` from `strings_chat.xml` and its `values-tr` and `values-fr` copies in the same change as the five `ui_nav_*` keys (you are the only 1b editor of that file); BARS's `HomeLists.kt` edit stops using it.
- **R3 SHELL (`DebugLinks.kt`).** The extra `bars`: `DebugRequest.bars: Setting<BarsPin>` appended after your `firstrun` and `whatsnew` parameters, read at `parse` with the other extras, applied right after them: `off` is `Setting.Clear` (calls `BarsDebug.pin(null)`), any other text goes to `BarsPin.parse(v)`, null means `rejected`, else `Setting.Set` (calls `BarsDebug.pin(p)`); `reset=1` also calls `BarsDebug.pin(null)`. `DebugLinksTest` gets the rows.
- **R4 SHELL.** `nav.go(Route.Library(LibraryShelf.SAVED))` and `nav.go(Route.Hermes)` pushed above Home must show the bar with their own tab selected (hop and icon included) and return to Home on back; `nav.go(Route.Settings())` pushed above Home or above the Agent tab hides the bar (`showsBar()` false) and returns to the screen below on back, with the tab selection unchanged underneath. `Route.Hermes` keeps its key `hermes` and tag `screen.hermes`.
- **R5 LIBRARY.** `LibraryShelf.SAVED` is a public constant; `LibraryScreen(shelf)` treats its argument as the initial shelf of every new entry (the selected shelf is state per entry id, so a push with another argument shows that shelf).
- **R6 START.** `FireAnchor.publish(xPx: Float, yPx: Float, sizePx: Float)` with `xPx, yPx` = the **centre** of the fire in window pixels and `sizePx` = the larger side of its natural rectangle; callable on the main thread any number of times; an equal call changes nothing. Slice D: until the file exists BARS leaves the call out.
- **R7 ART-B.** The names of 3.10 (`empty_bubble`, `empty_scroll`, `empty_stool`, `empty_hourglass`, `empty_jar`) exist in `illustrations_b.pxl` for every one that 1a's LOOK cut, 32 x 32, two frames, drawn at 96 dp, with a plum outline that shows on dark and light.
- **R8 START (log).** A `TEST_HOOKS` log line `I/Splash: anchor x=<n> y=<n> size=<n>` when `target` changes, so 5.1 item 9 can compare.
- **R9 LANG.** `bar_agent_title` into `values-tr` and `values-fr` (stage 4) and the ten glossaries: the word for "Agent" is one word, the same in `shell_tab_agent` and `bar_agent_title`.
- **R10 lead.** (a) BARS owns two more new test files (`EmptyStateArtTest`, `BarsPinTest`), the six new source files of 3.1 that SPLIT 4.2 does not name (`EmberLevel.kt`, `BarTags.kt`, `BarsPin.kt`, `BarsDebug.kt`, `HomeGearMath.kt`, `HomeGear.kt`) and three one-purpose edits outside SPLIT's list: `J/ui/components/DesignGallery.kt` (additions only, X2), `R/values*/strings_control.xml` (delete `ctl_hermes_title`), and `ui/components/README.md` (rows for `onSettings`, `overEdge`, `emberLevel`, `EmptyState.art`, appended once the Wave 0 fixes that modify it today are committed). (b) INTEGRATE-B: the four tags of 3.7 in `ScenarioFilesTest`; `tools/lint-ui` and `strings-check` baselines are not raised (the new files start at 0 and use `Space`, `Corner` and named constants).
- **R11 INTEGRATE-B.** Before slice C starts, re-read the final 1a `HomeHero.kt`, `HomeScreen.kt`, `HomeLists.kt` and correct section 3.4 where a name or a place differs; 3.4 is the only part of this draft that depends on files that did not exist when it was written.

**BARS provides** (all in `app.ui.components` unless said): `PocketTopBar(..., emberLevel, overEdge, onSettings)`; `EmberLine(active, modifier, level)`; `EmberLevel`; `EmberFeed.level`; `EmptyState(..., art)` and `EmptyStateRules`; `BarTags` (`top.gear`, `home.gear`, `chat.peek`, `chat.peek.working`); `BarsPin`, `BarsRules`, `BarsDebug` (`pin`, `pinned`); in `app.ui.home`: `HomeGear`, `HomeGearMath`. Later waves: a screen whose top bar shows work passes `ember = <its busy flag>, emberLevel = EmberFeed.level`; a screen that wants Settings in its bar passes `onSettings`; any empty screen passes `art = IllustrationId.<NAME>.sprite`.

## 8. Risks
1. **Home's files do not exist yet.** 3.4 is written against the registry; the worst case is that the gear has to be put in another parent than I think. The rule that does not change: the gear is above the list in z-order (a gear inside `HomeHero`, which is behind the list, would receive no touch). R11 covers it.
2. **The plate over a live sky** is a taste call (1a leaves the sky to the day); the contrast test proves 3:1 for any background, the owner judges the look. If the plate is rejected, the fallback is `HeroInk.pick(topArgb)` with a shadow and a contrast that fails on the mid-luminance sky; the plate stays the default.
3. **The `PocketTopBar` rewrap** (a Box around the Column) touches twelve screens. It changes nothing in layout by construction (the Box wraps the Column's size), and 5.1 item 6 compares pictures before and after.
4. **The peek overlaps** the lower 3 dp of the "more" icon area and the tail of a long subtitle; with no subtitle and a long title it touches the title's descenders. Accepted for the first picture; the cheap alternatives are the peek left of the action icons or only in chats whose subtitle is empty.
5. **`PetPeek` may not exist** (1a cut item 5): then it is an empty composable and BARS draws nothing, as designed; or its parameter order may differ: BARS calls it with named arguments.
6. **One fire for all chats** (3.6): the line of a waiting chat can roar because another chat runs a tool. Accepted; a per-conversation level would need `FireFeed` to expose one (a later request).
7. **Orphaned keys.** If `ctl_hermes_title` or `ui_home_tile_hermes` is left unused, the strings check warns and the baseline (939 warnings) would rise, which a ruling forbids. They are removed in the same changes that stop using them; if SHELL's deletion (R2) does not land in the same slice, BARS keeps the old label key for the tile until it does.
8. **`SpriteBank.find` before the bank is loaded** throws (`all()` needs the application): `EmptyState` wraps it in `runCatching`; the picture then falls back and the next composition finds the sprite.
9. **`PixelImage(name)` throws for an unknown name** (`SpriteBank.get`): `EmptyState` only calls it after `find` succeeded.
10. **A pushed `Library` or `Hermes` above Home** relies on SHELL's bar logic (R4). If a pushed tab root does not show the bar, the fallback is `nav.tab(...)` for both tiles (the back gesture then leaves the app instead of returning to Home: worse).
11. **Release builds contain `BarsDebug`** (inert: `pin()` returns first when `TEST_HOOKS` is false); a few bytes, no behaviour.
12. **Kotlin traps** for an agent that cannot compile: no slash-star inside a comment; `rememberUpdatedState(level)` is read inside `onDrawBehind`, never in the body; `PixelShape(Corner.card, Space.xxs)` instead of a literal; `Modifier.align` exists only inside the `BoxScope` of `overEdge`; `HomeGear` imports `androidx.compose.ui.semantics.isTraversalGroup` and `traversalIndex`; a default argument `EmberLevel.FULL` must be a stable `val`, not a new lambda; `0xFF...` literals need `.toInt()`; the plural-free strings file carries its note comment on the line above its key.

## 9. Open points for the lead (each has a default; the draft can be built as written)
1. **Ownership additions** of R10 (a): the new files, the two extra test files and the three one-purpose edits outside SPLIT 4.2's list. Default: accepted.
2. **Look decisions that wait for the first pictures** (not blocking): the gear's plate against a sky-coloured ink; the peek's place at the end of the chat bar against left of the action icons. Default: plate, end of the bar, both "assumed, owner may change".
3. **The Hermes tile and Saved tile use `go`** (back returns to Home), not `tab`. Default: `go`, which depends on SHELL's R4.
4. **START's `FireAnchor.publish` types and the log line** (R6, R8) are asked, not agreed: START's draft is written in parallel. Default: as asked here; the lead reconciles in the contract.
5. **Whether the owner's own chat may be opened for the pinned picture** (5.1 item 8: opening can mark a chat read). Default: no; the gallery (5.1 item 5) and the twin carry it.
6. **"Pêt in chats" on by default** shows the owner the small Pêt in every answering chat after the update (plan 1.5: "a setting, on by default"). Default: on; the owner may change.
