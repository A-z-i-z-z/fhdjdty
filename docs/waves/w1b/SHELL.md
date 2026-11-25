# Wave 1b, area SHELL: tab bar, routes and gates (contract draft)

`J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`; package `dev.pocketagent.app` is written `app`. Line numbers were read in the work branch (`ccr-bb6d7bcc-9eojyu`) after the merge of Wave 1a slice A (HEAD `9fb25f1`): `ui/Nav.kt` 157 lines, `ui/AppRoot.kt` 488, `ui/nav/NavStack.kt` 158, `DebugLinks.kt` 280 are still exactly Wave 0's, because 1a has not yet touched them. **Everything that is a 1a symbol (`HomeSky`, `FireFeed`, `HomeTags`, `HearthScene` and so on) is taken from `docs/waves/w1a-contract.md` (its section 3 and 7.5), not from today's code; Wave 1a's slices C and D are not merged and the names are re-read against 1a's final contract before this draft is built.** Edits to `AppRoot.kt`, `DebugLinks.kt`, `Nav.kt` are found by quoted text, never by line number, because 1a's INTEGRATE edits two lines of `AppRoot.kt` and 1a's LINKS rewrites a large part of `DebugLinks.kt` before SHELL starts.

Sources: `docs/waves/w1b/SPLIT.md` (4.1, 5, 6, 9, 10, 11, 13); plan `docs/phase4-plan.md` 1.6 (lines 835 to 841), 1.7 (843 to 856), 4.1 (415 to 436), 8 (S1 and S8) and the plan's table row "SHELL"; `docs/design-language.md` pattern 8 (line 176), 8.1 last paragraph (455 to 456), 8.4, 8.15, 8.16, 10, 13.2, 13.3; `docs/waves/w0-contract.md` 3.3 (navigation), 3.4 (tokens, motion, haptics), 3.12 (tags); the code files named in section 2. Taste calls are "assumed, owner may change".

## 1. What the plan and the design language ask of SHELL

| Source | Ask |
|---|---|
| Plan 1.6 line 835 | "Bottom bar v2 (the hopping ember, tab icons that play once when chosen)" |
| Plan 1.6 line 840 and 4.1 lines 421 to 428 | The tab bar changes once, in Wave 1: **Home, Chats, Library, Bots, Agent**; "a wave may add a shelf or a row, never a new top-level place"; one agent owns the map (the owner of `ui/Nav.kt`) |
| Plan 4.1 lines 429 to 430, DL 8.1 lines 455 to 456, DL 8.15 | Settings leaves the bar for a gear on Home and in the Agent tab. SHELL owns only the route side (Settings is a pushed screen); the gear is BARS's, the Settings screen's back arrow is APPEAR-B's |
| Plan 1.7, DL 8.4, 8.16 (Flow and Stage) | The Welcome is a route of its own at the bottom of the stack; "it never shows again after it was finished or skipped, and never to an existing installation" (the rule is WELCOME's; SHELL provides the gate: the initial stack) |
| Plan 1.6 line 838 | "What's new" opens as a reader page from a Pêt note: a route `WhatsNew` |
| DL pattern 8 (line 176) | Tab switch: content cross-fades in `FAST`; "the ember leaves the old tab and lands on the new one along a small arc in five steps, trailing two sparks. The new tab's icon plays its 3 to 4 frame animation once" |
| DL 13.2 | A tab label has room for 10 characters in English (14 in a translation); "when the text is longer: the bar shows icons only, with the label under the chosen tab" |
| DL 10, plan principle 13 | A spoken label on every control, text at double size, mirrored layout |
| DL 13.3 | The bar mirrors (order of tabs) in a right-to-left language; the scene never mirrors (not SHELL's) |
| DL 2.1 | Everything drawn in the pixel look is measured in the design pixel `u` (whole device pixels) |
| SPLIT 4.1 | Items B1 (bar v2), B5 (the tab set), the route part of B7 (Settings as a pushed route), the initial stack from the Welcome's `FirstRun`, `AppRoot` (keeps 1a's `HomeSky.iconsDark` line), the debug-link routes and the extras `firstrun` and `whatsnew`; two passes: **pass 1 = slice C (Library)**, **pass 2 = slice D (Welcome and What's new)** |

## 2. What exists today, and what 1a will have changed by then

**Today (read).**
- `J/ui/Nav.kt`: `sealed interface Route` (line 12) with 12 kinds; `Navigator` (42) over `NavStack`; `Navigator.go` (61: pushes, and going to Home resets the stack), `tab` (73), `reset` (85), `Saver` (97); `Route.tabIndex()` (108: Home group 0, Chats 1, Bots 2, Hermes and Terminal 3, Settings 4), `showsBar()` (117: true for Home, Chats, Bots, Hermes, Settings), `rank()` (123), `encode()` (125), `decodeRoute()` (140).
- `J/ui/nav/NavStack.kt`: root ids Home 1, Chats 2, Bots 3, Hermes 4, Settings without a section 5 (`rootIdFor`, about line 95), `isRootId = id in 1L..5L` (line 104), `FIRST_FREE_ID = 100L` (92), `encode`/`decode` with a saved counter. `NavMotion.kt` classifies a change by entry ids only (two depth-1 stacks give `TAB`). `NavTags.kt`: `BAR`, `TAB_HOME/CHATS/BOTS/HERMES/SETTINGS`, `screen(route)` for 12 kinds. `NavDisplay.kt`: one `AnimatedContent` keyed by entry id, state kept per id in a `SaveableStateHolder`, root ids are never dropped.
- `J/ui/AppRoot.kt`: `TabSpec` and `TABS` (lines 202 to 210: five tabs, the fifth is `Route.Settings()` with `PixelIcon.GEAR`); `AppShell` (214): `rememberSaveable(saver = Navigator.Saver) { Navigator() }` (215), the status-bar line `SystemStatusBarIcons(darkIcons = (route != Route.Home || practiceOn) && !c.isDark)` (234), the link effect (237), `BackHandler(enabled = nav.canPop || route != Route.Home)` (249), the practice banner, `NavDisplay`, the bar inside an `AnimatedVisibility` (270), `PetNoteHost` (310, bottom padding `barHeight` while the bar shows); `navigateToLink` (319; `AppLink.Usage` does `nav.tab(Route.Settings())` then `nav.go(Route.Settings("usage"))`, line 347); `RouteContent` (418); `PocketBottomBar` (439: a 2 dp outline line, a 60 dp row of five cells, `testTag(nav.bar)`, `onSizeChanged` reports its height) and `BottomTab` (461: a 24 dp `PixelIconView`, a 10.5 sp label, one line, ellipsis, and a 3 dp two-tone ember that grows from 0 to 16 dp under the selected cell; `selectable(selected, role = Role.Tab)`, tag per tab). The five labels are `ui_nav_home|chats|bots|hermes|settings` in `R/values/strings_chat.xml` lines 4 to 8 (and in `values-tr`, `values-fr`, and in the generated debug folders `app/src/debug/res/values-en/` and `values-en-rXZ/`).
- **`J/ui/text/Texts.kt` line 244 uses `R.string.ui_nav_home`** as the probe of `TestLanguage.isAvailable` (it asks whether the stretched language resolves it to a string starting with "["). SPLIT does not mention this: deleting `ui_nav_home` breaks the build. Handled in 3.7.
- `J/ui/components/PixelIcon.kt`: the enum of 60 icons ends `... MOON, LINK`; `spriteName = "icon_" + name.lowercase()`. **`T/text/EnumLabelsTest` requires, for every `PixelIcon`, an entry in `EnumLabels.ICONS` (`J/ui/text/EnumLabels.kt`) whose key is exactly `eng_icon_<lowercase name>` and that key in an English strings file; `T/ui/pixel/PxlParserTest` requires a sprite `icon_<name>` in some `.pxl`.** SPLIT lists neither. Handled in 3.7.
- `J/DebugLinks.kt`: `DebugRequest(reset, route, clock, language, rtl, seed, nav, rejected)`; `parse` reads seven extras (line 126); `stackFor` (269): `Home, Chats, Bots, Hermes` alone; `Settings` alone or `[Settings(null), Settings(section)]`; `Starred` over `Chats`; `Terminal` over `Hermes`; the rest over Home. `T/DebugLinksTest.stackForEveryRoute` asserts these.
- Callers that use `Route.Settings` as a tab or as a push: `HomeScreen.kt:229, 245, 371` (push), `ChatInputArea.kt:202`, `HermesScreen.kt:160`, `TerminalScreen.kt:136` (push), `AppRoot.kt:348` (tab). Callers that open a tab by `nav.go`: `HomeScreen.kt:356` (Hermes tile), `ChatDialogs.kt:170, 319` and `ChatMessageList.kt:234` (open the inspector from a chat: a push on purpose, back returns to the chat).
- Tools that name `tab.settings`: `tools/scenarios/smoke-launch.json:16`, `smoke-tabs-labels.json:20`, `smoke-hermes-running.json:9`, `perf/P5-transitions.json` (five taps, lines 51, 61, 71, 81, 91; SPLIT's list misses this file), `tools/phone-lib.ps1:1753` (`Set-PaPracticeSwitch`), `T/ScenarioFilesTest.kt` known tags (line 51), `T/ui/nav/NavTagsTest`.

**What 1a will have changed by then (from `w1a-contract.md`, not from code).**
- `J/ui/AppRoot.kt`: INTEGRATE (7.5 step 5) adds `import dev.pocketagent.app.ui.pixel.HomeSky` and replaces the two comment lines and the `SystemStatusBarIcons(...)` call by `SystemStatusBarIcons(darkIcons = ((route != Route.Home || practiceOn) && !c.isDark) || (route == Route.Home && !practiceOn && HomeSky.iconsDark.collectAsState().value))`. SHELL replaces that call again by the pure `StatusBarRule` (3.3 and 3.6), which gives the same answers (proved in `TabModelTest`).
- `J/DebugLinks.kt`: LINKS appends to `DebugRequest`, after `rejected`, the parameters `weather, place, sky, days, appearance, fire, pet, wood` (all `Setting<...> = Setting.Keep`), reads their extras, applies them in the order reset, clock, weather, place, sky, days, appearance, fire, pet, wood, lang, rtl, nav, seed, then the route, and `reset=1` also clears their pins (3.9 of the 1a contract). SHELL appends two more at the end.
- `HomeTags.TILE_SAVED`, `HomeTags.TILE_HERMES` and the Home tiles (`HomeLists.kt`) exist; their targets (`nav.go(Route.Starred)` and `nav.go(Route.Hermes)`) are changed by BARS (section 7). `Appearance.petInChatsVisible`, `FireFeed` and the rest are not SHELL's.
- The scene files and `HearthScene` do not matter to SHELL except that Home paints its own sky behind the status bar (unchanged rule: `HomeSky.iconsDark`).

## 3. The design

### 3.0 Decisions taken here (so the lead need not)

1. The route `Hermes` keeps its key `hermes`, its tag `tab.hermes` and its class; only the label becomes "Agent" (SPLIT open point 2). `MainTab.AGENT` maps to it.
2. **Settings is a pushed route with no bar.** `Route.Settings.showsBar()` becomes false; Home's gear and the Agent tab's gear do `nav.go(Route.Settings())`. A pushed Settings gets a fresh entry id each time, so its open section and scroll position are not kept between visits (they were, while it was a tab). Settings reached by `AppLink.Usage` or by the debug link is `[Home, Settings]`.
3. `Route.Starred` stays and is a pushed detail with no bar; it counts as the Library tab (`tabIndex` 2) and its debug stack stays `[Chats, Starred]` because that is how the app reaches it (the star button of the chat list).
4. **A tab root is opened with `nav.tab(...)`, never with `nav.go(...)`**; `go` is a push and stays one (the inspector from a chat relies on it). The Home tiles use `nav.tab(Route.Library(LibraryShelf.SAVED))` and `nav.tab(Route.Hermes)` (request to BARS).
5. The shelf of `Route.Library(shelf)` is a **request**, not an identity: every `Library` route has the root id 6, `null` means "keep the shelf the screen shows", and AppShell passes the shelf of the **live** top entry (3.6, step 5), because `AnimatedContent` may reuse the content of an equal id.
6. Label room is decided by **measured width** (DL 13.2's characters are the guide, the pixels are the rule); the bar never cuts a label and never wraps it.
7. `route=welcome` and the extra `firstrun` are accepted by the debug link **only on the twin** (`BuildConfig.IS_TWIN`), like `seed`: the Welcome starts unpacking the Linux image, and nothing may do that on the owner's package by a test. `route=whatsnew` and the extra `whatsnew` work on every test build (read only, memory only).
8. While the Welcome is the bottom entry, links from outside (`OpenChat`, `NewChat`, `Terminal`, `Routines`, `Usage`) are **dropped with a log line**, not applied: they would replace the stack and leave the Welcome unfinished and not skipped. Debug links still work. A share (`IncomingShareDialog`) still opens a chat on top of the Welcome (back returns to it).
9. Back on the Welcome: SHELL's `BackHandler` is disabled while the top route is `Welcome` (the Welcome installs its own on every step; on step one back is the system's, which leaves the app; it was not skipped, so it shows again at the next start).

### 3.1 `J/ui/Nav.kt`

Pass 1 adds `Library`; pass 2 adds `Welcome` and `WhatsNew`. KDoc of `Route` becomes "Tabs: Home, Chats, Library, Bots, Agent (the route `Hermes`). Settings is pushed from a gear."

```kotlin
sealed interface Route {
    // ... the twelve kinds as today ...
    /** The Library (a tab). [shelf] is a request ("files", "saved"); null keeps the shelf the screen shows. */
    data class Library(val shelf: String? = null) : Route
    /** The first-run wizard. Only ever the bottom entry of the stack; no bar. */
    data object Welcome : Route
    /** The reader of this version's "What's new" page. Pushed over whatever is open; no bar. */
    data object WhatsNew : Route
}
```
Key grammar: `internal fun isShelfKey(s: String): Boolean` = `^[a-z][a-z0-9_]{0,15}$` (a `Regex` constant; no case change).

| Route | key (`encode`) | `showsBar` | `tabIndex` | `rank` |
|---|---|---|---|---|
| Home | `home` | true | 0 | 0 |
| Chats | `chats` | true | 1 | 0 |
| Chat | `chat:<id>` | false | 1 | 1 |
| **Library** | `library`, `library:<shelf>` | **true** | **2** | 0 |
| Bots | `bots` | true | 3 | 0 |
| BotEditor | `botnew`, `bot:<id>` | false | 3 | 1 |
| Hermes (Agent) | `hermes` | true | 4 | 0 |
| Terminal | `terminal` | false | 4 | 1 |
| **Settings** | `settings`, `settings:<s>` | **false** | 0 (not used: no bar) | **1** |
| Routines, RoutineEditor, Gallery | unchanged | false | 0 | 1 |
| Starred | `starred` | false | **2** | 1 |
| **Welcome** | `welcome` | false | 0 | 1 |
| **WhatsNew** | `whatsnew` | false | 0 | 1 |

`decodeRoute`: `"library"` gives `Library(null)`; `"library:<x>"` gives `Library(x)` only when `isShelfKey(x)`, else `null` (an unknown route, listed in `rejected` by the debug link); `"welcome"`, `"whatsnew"`. `Route.tabIndex()` becomes `TabModel.tabOf(this).index` (one table, in `TabModel`). Old saved stacks and links decode as before; a new key read by the previous build is an unknown key, dropped by its `decode` (the rollback APK is unaffected).

### 3.2 `J/ui/nav/NavStack.kt`, `NavTags.kt`, `NavMotion.kt`

- `rootIdFor`: `Home 1, Chats 2, Bots 3, Hermes 4, Settings(section == null) 5 (legacy: only an old saved stack or a direct call puts it at the bottom; nothing puts it on the bar), Library(any shelf) 6`, else null. `const val LAST_ROOT_ID = 6L`; `isRootId(id) = id in 1L..LAST_ROOT_ID`; `FIRST_FREE_ID = 100L` unchanged; the class KDoc lists the new table. `Welcome` and `WhatsNew` have no root id: a `Welcome` at the bottom gets a fresh id, so its saved state is dropped when the stack is reset to Home (what we want). `tab(Library("saved"))` over `Library("files")` yields `NavEntry(6, Library("saved"))`: same id, different route.
- A stack that starts with `Settings(null)` (old saved state `5|settings`) is legal: no bar, back goes to Home through AppShell's rule (3.6, step 4).
- `NavTags`: `TAB_LIBRARY = "tab.library"`; `TAB_SETTINGS` is **removed**; `screen(route)` adds `Library -> "screen.library"`, `Welcome -> "screen.welcome"`, `WhatsNew -> "screen.whatsnew"` (15 kinds). `BAR`, `TAB_HOME/CHATS/BOTS/HERMES` and the share tags unchanged.
- `NavMotion.kt`: **no code change** (`navMotionKind` reads ids only). Tests prove the cases that matter now: Welcome to Home is `TAB` (cross-fade), Home to Settings is `PUSH`, Settings back is `POP`, tab to tab is `TAB`.

### 3.3 `J/ui/TabModel.kt` (new, pure: no `android.` or `androidx.` import)

```kotlin
package dev.pocketagent.app.ui

internal enum class MainTab(val tag: String, val spriteName: String) {
    HOME(NavTags.TAB_HOME, "tabicon_home"), CHATS(NavTags.TAB_CHATS, "tabicon_chats"), LIBRARY(NavTags.TAB_LIBRARY, "tabicon_library"),
    BOTS(NavTags.TAB_BOTS, "tabicon_bots"), AGENT(NavTags.TAB_HERMES, "tabicon_agent");
    val index: Int get() = ordinal
    val root: Route get() = when (this) { HOME -> Route.Home; CHATS -> Route.Chats; LIBRARY -> Route.Library(); BOTS -> Route.Bots; AGENT -> Route.Hermes }
}
internal enum class LabelMode { ALL, SELECTED_ONLY }

internal object TabModel {
    fun tabOf(route: Route): MainTab           // the table of 3.1: Settings, Routines, RoutineEditor, Gallery, Welcome, WhatsNew -> HOME; Starred -> LIBRARY
    fun selected(stack: List<Route>): MainTab  // tabOf(stack.first()); an empty stack gives HOME. The tab a pushed screen belongs to is that of the entry at the bottom of the stack
    const val LABEL_ROOM_CHARS = 10            // DL 13.2, English; the unit test checks the English labels against it; at run time the measured rule below decides
    fun labelMode(labelWidthsPx: List<Int>, roomPx: Int): LabelMode      // ALL when every width <= roomPx, else SELECTED_ONLY; an empty list or roomPx <= 0 gives ALL
    const val SELECTED_WEIGHT = 1.8f
    fun cellWeights(mode: LabelMode, selected: MainTab): List<Float>     // five numbers: ALL -> all 1f; SELECTED_ONLY -> 1f, and SELECTED_WEIGHT for the selected tab
    fun backEnabled(canPop: Boolean, route: Route): Boolean              // canPop || (route != Home && route != Welcome)
}

/** The ember's hop (DL pattern 8). Pure, so the steps can be tested. */
internal object EmberHop {
    const val STEPS = 5                                  // equal jumps (Ease.steps(5))
    val LIFT_U: IntArray = intArrayOf(0, 1, 2, 2, 1, 0)  // the arc: lift in design pixels after k hops, k = 0..5 (extra E2)
    fun step(fraction: Float): Int                       // round(fraction * 5) coerced 0..5; NaN gives 0
    fun lift(step: Int): Int                             // LIFT_U[step coerced 0..5]
    fun sparkSteps(step: Int): List<Int>                 // extra E1: the two earlier landing places still glowing: for step in 1..4 listOf(step - 1, step - 2) without negatives; else empty
}

/** The icon of the chosen tab plays once (DL pattern 8). The sprite's frame 0 is the rest picture. */
internal object TabIconPlay {
    fun frames(frameCount: Int): List<Int>               // frameCount <= 1 gives empty; else 1 until frameCount, then 0 (4 frames: [1, 2, 3, 0])
}

/** The status-bar icon colour of a route: the same answers as 1a's line for Home, one more case for the Welcome. */
internal object StatusBarRule {
    fun darkIcons(route: Route, practiceOn: Boolean, themeDark: Boolean, homeSkyDark: Boolean, welcomeDark: Boolean): Boolean = when {
        practiceOn -> !themeDark                // the banner's own deep background sits behind the status bar
        route == Route.Home -> homeSkyDark      // the real sky; false in the classic night (HomeSky.iconsDark rule R6)
        route == Route.Welcome -> welcomeDark   // WelcomeChrome.iconsDark: the scene's top colour
        else -> !themeDark
    }
}
```
All five `MainTab.root` routes are `showsBar()` true; `TabModelTest` proves it.

### 3.4 `J/ui/PocketBottomBar.kt` (new; the bar moves out of `AppRoot.kt`)

```kotlin
@Composable
internal fun PocketBottomBar(selected: MainTab, nav: Navigator, onHeight: (Dp) -> Unit)
```
(Deviation from SPLIT 6, which says `route`: the first parameter is the tab, because AppShell keeps showing the last tab while the bar slides out; see 3.6. Internal to the shell: only `AppRoot.kt` calls it.)

**Layout.** One `Column` (tag `nav.bar`, background `c.surface`, `windowInsetsPadding(bottomSafeInsets())`, `onSizeChanged { onHeight(height.toDp()) }`, as today): the outline line (`Space.xxs` high, `c.outline`); a `Box` with `heightIn(min = BAR_ROW_DP.dp)` (`private const val BAR_ROW_DP = 60f`; `heightIn` instead of today's fixed `height(60.dp)` so text at double size grows the bar instead of cutting it; `PetNoteHost` already follows `barHeight`) holding a `Row` of five cells (`Modifier.weight(weights[i])`) and, over it, the ember layer. **Cell** (`BottomTab`): `Column` centred, `Modifier.selectable(selected, role = Role.Tab, onClick)`, tag `tab.*`; the glyph (`Space.xl` = 24 dp, tint `c.accent` when selected else `c.textMuted`, as today); `Spacer(Space.xxs)`; the **label slot**: a `Text` (10.5 sp via a named `TextStyle` constant, one line, ellipsis, `c.text` semi-bold when selected else `c.textMuted`), always laid out so icons never change height between modes, with `alpha` 1 when the mode shows it and 0 when it does not (animated with `motion.fast()`); then a reserved strip for the ember (`2u` high plus `Space.xs`). **Spoken label:** each cell sets `semantics { contentDescription = label }` and the label `Text` is `clearAndSetSemantics {}`, so the cell is read once ("Library, tab, selected") in both modes and the robot's `desc` selectors keep working.

**Glyph.** `val sprite = SpriteBank.find(tab.spriteName)`: when present, `PixelImage(sprite, Modifier.size(Space.xl), frame = frame, tint = tint, playing = false)` with `frame` from the icon player below (0 at rest); when null (ART-B's `tabicons.pxl` not merged, or a sprite cut) the fallback is the static `PixelIconView(icon, tint, Space.xl)` with `HOME -> HEARTH, CHATS -> CHATS, LIBRARY -> LIBRARY (icon_library; its sprite is core in ART-B, see 3.7), BOTS -> BOTS, AGENT -> SCROLL`. Nothing else in the bar depends on the art.

**Labels and the label-room rule.** `val measurer = rememberTextMeasurer()`; `widths = labels.map { measurer.measure(it, style(SemiBold)).size.width }` remembered on the labels, density and font scale; `roomPx = screenWidthPx / 5 - 2 * Space.xs.toPx()` where `screenWidthPx` comes from `LocalConfiguration.screenWidthDp` (known in the first composition, so the bar never flickers between modes); `mode = TabModel.labelMode(widths, roomPx)`. `ALL`: every label shows. `SELECTED_ONLY`: only the selected tab's label shows and its cell takes `SELECTED_WEIGHT` (the others share the rest; the weight animates with `motion.fast()`: extra E3). The mode is logged (TEST_HOOKS): `I/Nav: bar mode=SELECTED_ONLY room=<px> widest=<px>` on every change. At text scale 2.0 most languages land in `SELECTED_ONLY`; that is the intended answer of DL 13.2, not a failure.

**The ember (core: a stepped slide).** One ember drawn by `drawBehind` in the layer over the row, in whole design pixels: `u = designUnitPx(density)`, width `8u`, height `2u` (top `u` in `c.fireHigh`, bottom `u` in `c.fireMid`; today's 16 dp by 3 dp), x snapped with `snapToDesignPx`, sitting above the bottom edge of the row by `Space.xs`. State: `centers = mutableStateListOf(0f x5)` filled by `onGloballyPositioned { positionInParent().x + size.width / 2f }` on each cell (so a mirrored layout and the changing weights are followed); `hop = remember { Animatable(1f) }`; `fromX`. On every change of `selected` after the first measure: `fromX = current x`; `hop.snapTo(0f)`; `hop.animateTo(1f, motion.custom(Dur.BASE, Ease.steps(EmberHop.STEPS)))`; x = `lerp(fromX, centers[selected.index], hop.value)`, read **only inside the draw lambda** (no recomposition per frame). Until the first measure the ember is not drawn; at the first measure it is placed without animating. `AnimLevel.OFF` gives `snap` through `motion.custom`; REDUCED a 150 ms tween (the scheme clamps). **Extra E2, the arc:** y lifted by `EmberHop.lift(EmberHop.step(hop.value)) * u`. **Extra E1, two sparks:** at `EmberHop.sparkSteps(step)` the two earlier landing places draw a `u` by `u` square in `c.fireMid` at 60 and 30 percent alpha. Both extras are off when `!Pocket.motion.particles` (REDUCED and OFF).

**The icon plays once.** `selected` changing after the bar's first composition (a tap or a link) starts, in the new cell, `for (f in TabIconPlay.frames(sprite.frames.size)) { frame = f; delay(Dur.TAP) }` (90 ms, the sprite's own frame time, a token and not a literal), ending on frame 0; it never loops (`playing = false`, the frame is chosen by index; 1a gotcha: `Sprite.frameAt` always loops). It plays at FULL only; REDUCED and OFF show the rest picture. It does **not** play when the bar comes back into view after a chat (the bar's first composition after the slide-in has nothing "previous") nor when returning to the first tab by back.

**Press, haptic, sound.** The cell keeps `selectable`'s default indication. A tap on a tab that is not the selected one calls `haptics.play(HapticCue.TICK)` (`rememberHaptics()`, DL 4: TICK is for chips and list selection) and then `nav.tab(tab.root)`; a tap on the selected tab calls `nav.tab(tab.root)` without a haptic (it still resets a pushed stack above that root, as today). No sound (DL 5 names none for the bar).

### 3.5 What the bar looks like (sketch; the Library shelves are LIBRARY's)

```
ALL labels (English, 360 dp)             SELECTED_ONLY (French or text scale 1.3 and up)
┌───────────────────────────────────┐    ┌───────────────────────────────────┐
│▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│    │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│
│  ⌂      ▢      ▤      ☻      ≡    │    │  ⌂      ▢     ▤        ☻     ≡   │
│ Home   Chats Library  Bots  Agent │    │               Bibliothèque        │
│  ▀▀                               │    │          ▀▀▀▀                     │  the ember (8u x 2u) hops
└───────────────────────────────────┘    └───────────────────────────────────┘  to the chosen tab in 5 steps
```

### 3.6 `J/ui/AppRoot.kt` (anchors by quoted text)

**Pass 1 (slice C).**
1. Remove `private class TabSpec`, `private val TABS`, `private fun PocketBottomBar`, `private fun BottomTab` and the imports they alone used (`animateDpAsState`, `snap`, `selectable`, `TextAlign`, `PixelIconView`, `onSizeChanged` and any other the compiler marks unused; keep `FontWeight`, `Role`, `LocalDensity` if the share dialog or others still use them).
2. In `AppShell`: move `val ctx = LocalContext.current` above the `nav` line (pass 2 needs it there). Bar tab: `var lastBarTab by remember { mutableStateOf(MainTab.HOME) }`; `SideEffect { if (route.showsBar()) lastBarTab = TabModel.tabOf(route) }`; `val barTab = if (route.showsBar()) TabModel.tabOf(route) else lastBarTab`. Replace the call inside the `AnimatedVisibility` by `PocketBottomBar(barTab, nav, onHeight = { barHeight = it })`.
3. Replace the line `SystemStatusBarIcons(darkIcons = ...)` (and the comment above it that 1a rewrote) by:
   `val homeSkyDark by HomeSky.iconsDark.collectAsState()` and `SystemStatusBarIcons(darkIcons = StatusBarRule.darkIcons(route, practiceOn, c.isDark, homeSkyDark, welcomeDark = false))`, with the comment "Home paints the real sky full-bleed behind the status bar: its icons follow the sky's brightness (`HomeSky.iconsDark`; the classic night keeps light icons). While the practice banner is on, the area behind the status bar is the banner's own deep background instead." Imports `HomeSky` (1a: it is already added by INTEGRATE) and `StatusBarRule`.
4. `BackHandler(enabled = TabModel.backEnabled(nav.canPop, route))` (the body stays: `if (!nav.back()) nav.tab(Route.Home)`).
5. **Live route.** In the `NavDisplay(nav, ...) { entry -> ... }` lambda use `val live = nav.entries.firstOrNull { it.id == entry.id }?.route ?: entry.route` and call `RouteContent(live)` (the tag stays `NavTags.screen(entry.route)`; the kind of screen never changes under one id). Reason: `Library(shelf)` replaces the route under the same id 6 (a debug link or the tile), and `AnimatedContent` may keep the old `entry` object for that key; reading the stack's state makes the new shelf reach `LibraryScreen` either way.
6. `navigateToLink`: `AppLink.Usage -> { nav.tab(Route.Home); nav.go(Route.Settings("usage")) }`; `is AppLink.DebugOpen -> nav.reset(DebugLinks.stackFor(link.route))` as today.
7. `RouteContent`: `is Route.Library -> LibraryScreen(route.shelf)` (import `app.ui.library.LibraryScreen`); `Route.Settings -> SettingsScreen(route.section)` unchanged.

**Pass 2 (slice D).**
8. `val nav = rememberSaveable(saver = Navigator.Saver) { Navigator(initialRoutes(ctx)) }` with `private fun initialRoutes(ctx: Context): List<Route>` = `try { FirstRun.initialRoutes(ctx).ifEmpty { listOf(Route.Home) } } catch (e: Exception) { AppLog.w("Nav", "first-run check failed, starting on Home", e); listOf(Route.Home) }`, and, when `BuildConfig.TEST_HOOKS`, one line `AppLog.i("Nav", "initial=<keys>")` (only when the lambda ran, that is, not on a restore). **The first-frame rule:** the decision is made synchronously before the first composition (one preferences read and two `File.exists()` inside `FirstRun.initialRoutes`), so Home is never composed and then replaced by the Welcome; a restored stack wins over the decision (it already holds the right screen).
9. `StatusBarRule.darkIcons(..., welcomeDark = welcomeDark)` with `val welcomeDark by WelcomeChrome.iconsDark.collectAsState()`.
10. `RouteContent`: `Route.Welcome -> WelcomeScreen()` (import `app.ui.onboarding.WelcomeScreen`), `Route.WhatsNew -> WhatsNewScreen()` (import `app.ui.whatsnew.WhatsNewScreen`).
11. After `PetNoteHost(...)`: `if (nav.entries.first().route != Route.Welcome) WhatsNewHost()` (START's host posts the Pêt note and reads `LocalNavigator.current`, which is provided around this whole `Box`). The Welcome's end sets "seen" before it resets the stack (request to WELCOME), so the host does not post a note to a new user.
12. `navigateToLink`: first lines `if (nav.entries.first().route == Route.Welcome && link !is AppLink.DebugOpen) { AppLog.i("Nav", "link ignored while the Welcome is open: ${link::class.simpleName}"); return }` (decision 8 of 3.0).

### 3.7 `J/ui/components/PixelIcon.kt`, `J/ui/text/EnumLabels.kt`, `J/ui/text/Texts.kt` (pass 1)

- `PixelIcon` gains `LIBRARY` appended last (after `LINK`); sprite name `icon_library` comes from ART-B's `icons_b.pxl` (core; slice B precedes C; `PxlParserTest.everyShippedFileParsesAndNamesAreUnique` fails if the sprite is missing, which is the right alarm).
- `EnumLabels.ICONS` gains `"LIBRARY" to R.string.eng_icon_library` (one line; `EnumLabelsTest` proves it). **The key `eng_icon_library` ("Library") is written in `R/values/strings_shell.xml`** (the test reads every English file, not only `strings_engine.xml`), with a translator note, so SHELL does not touch the engine strings file.
- `Texts.kt` line 244: `R.string.ui_nav_home` becomes `R.string.shell_tab_home` (one token; the stretched language folder has the key after the regeneration of section 8). SPLIT does not list this file; it is a Wave 0 file that no other 1b area edits (open point 2).

### 3.8 `J/DebugLinks.kt` and `T/DebugLinksTest.kt`

**Pass 1.** `decodeRoute` already accepts `library`, `library:<shelf>`, `whatsnew` and `welcome` after 3.1; `stackFor` becomes:
```
Route.Home, Route.Chats, Route.Bots, Route.Hermes, is Route.Library, Route.Welcome -> listOf(route)
is Route.Settings -> listOf(Route.Home, route)            // was: a tab; now pushed (Settings(section) goes straight over Home, as the app does)
Route.WhatsNew -> listOf(Route.Home, route)
Route.Starred -> listOf(Route.Chats, route)               // unchanged
// the rest unchanged
```
Every first entry still `showsBar()` except `Welcome` (the test says so explicitly). `parseParts`: `route=welcome` is accepted only when `isTwin`, else the route is `null` and `"route"` goes to `rejected` (as `seed`).

**Pass 2.** New extras (appended to the read list after 1a's): `EXTRA_FIRSTRUN = "firstrun"` with values `reset | done | off`, `EXTRA_WHATSNEW = "whatsnew"` with values `pending | off`. `DebugRequest` gets two more parameters at the very end, after 1a's `wood`: `firstRun: Setting<FirstRunDebug.Mode> = Setting.Keep`, `whatsNew: Setting<WhatsNewDebug.Mode> = Setting.Keep` (`off` is `Setting.Clear`; a bad value goes to `rejected`; `firstrun` on a build that is not the twin goes to `rejected`). `apply` order: reset, clock, weather, place, sky, days, appearance, fire, pet, wood, **firstrun, whatsnew**, lang, rtl, nav, seed, then the route. `Set` calls `FirstRunDebug.pin(mode)` / `WhatsNewDebug.pin(mode)`, `Clear` calls `pin(null)`, and `reset=1` also calls both `pin(null)`. Meaning (owners define, SHELL only forwards): `firstrun=reset` makes `FirstRun.decide` answer "show" whatever the files say and `done` makes it answer "never"; `whatsnew=pending` makes `WhatsNewLogic` answer "show a note" and, while pinned, writes nothing to the `whatsnew` preferences. The link line: `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity --es route welcome --es firstrun reset --es token <T>` (twin only).

### 3.9 Strings: `R/values/strings_shell.xml` (prefix `shell_`, plus `eng_icon_library`), and the deletion of `ui_nav_*`

English (each key carries its `<!-- t: ... | kind: ... -->`; tab labels `kind: tab`: room 10, translation 14):

| Key | English | kind | Note (summary) |
|---|---|---|---|
| `shell_tab_home` | Home | tab | first tab, the start screen |
| `shell_tab_chats` | Chats | tab | the list of conversations |
| `shell_tab_library` | Library | tab | the shelves: files and saved messages, later more |
| `shell_tab_bots` | Bots | tab | the characters the user chats with |
| `shell_tab_agent` | Agent | tab | the agent itself: its mind, skills, logs, terminal |
| `shell_gear_cd` | Settings | row | spoken name of the gear button on Home and in the Agent tab (BARS uses it); read aloud, not drawn |
| `eng_icon_library` | Library | row | spoken name of the library icon when no other description is given; one or two words |

The five keys `ui_nav_home`, `ui_nav_chats`, `ui_nav_bots`, `ui_nav_hermes`, `ui_nav_settings` and the comment `<!-- bottom bar -->` are deleted from `R/values/strings_chat.xml`, `R/values-tr/strings_chat.xml`, `R/values-fr/strings_chat.xml` in the same change (the lint ratchet counts an unused key and baselines never rise; SHELL is the only 1b agent that touches `strings_chat.xml`). **`ResourceParityTest` requires every English file and key to exist in `values-tr` and `values-fr` with the same placeholders, so SHELL writes `R/values-tr/strings_shell.xml` and `R/values-fr/strings_shell.xml` itself, in the same slice** (seven short keys; wording is a proposal that LANG's glossaries may later change): tr: Ana sayfa, Sohbetler, Kütüphane, Botlar, Ajan, Ayarlar, Kütüphane; fr: Accueil, Discussions, Bibliothèque, Bots, Agent, Réglages, Bibliothèque (every tr and fr tab label is at most 14 characters; "Bibliothèque" is 12 and will put the French bar into the icons-only mode on a 360 dp phone, by design). The nine new languages come from LANG's stage 4. `tools/strings-kinds.txt` keeps its `tab ui_nav_*` line (harmless; LANG may drop it): the notes carry the kind.

### 3.10 Test tags (exact; one per node)

`nav.bar` (the bar column), `tab.home`, `tab.chats`, **`tab.library`**, `tab.bots`, `tab.hermes` (the Agent tab; the tag keeps the route's key), `screen.library`, `screen.welcome`, `screen.whatsnew` (written by the host `Box` in AppShell, as every `screen.*`). **Removed: `tab.settings`.** INTEGRATE-B adds the four new tags to `ScenarioFilesTest` and keeps `tab.settings` in the known set until the lead's rewrite of the scenarios that use it, then drops it.

### 3.11 Stored data and migration

SHELL stores nothing. No new preferences, no new file. The saved navigation state (`Navigator.Saver`, in the activity's saved-state bundle) gains the keys `library`, `library:<shelf>`, `welcome`, `whatsnew` (a build that does not know them drops them and falls back to Home: rollback-safe). An installation that exists keeps working unchanged: its start screen is Home, and `FirstRun.initialRoutes` (pass 2) answers `[Home]` for it without writing (WELCOME's rule). The only first-start difference for the owner is the bar: five tabs, Settings behind a gear.

### 3.12 The five states

| Part | Loading | Empty | Content | Error | Offline | Needs permission |
|---|---|---|---|---|---|---|
| The bar | not applicable: no data | not applicable | `ALL` (every label), `SELECTED_ONLY` (label room), hidden (a screen without bar, the keyboard open) | not applicable | not applicable: nothing here uses the network | not applicable |
| The shell's choice of first screen (pass 2) | the decision is synchronous; nothing to wait for | not applicable | Welcome or Home | `FirstRun.initialRoutes` throws: Home, one warning line in `app.log`, the app is never blocked by it | same as content | not applicable |
| Hosts of `Library`, `Welcome`, `WhatsNew` | each screen's own five states are LIBRARY's, WELCOME's, START's; SHELL's host only draws `c.bg` and the screen tag | | | | | |

### 3.13 Motion, haptics, sound, accessibility (by token)

| Thing | Token | Notes |
|---|---|---|
| Bar slides in and out | `motion.fast()` (existing `AnimatedVisibility`) | unchanged |
| Ember hop | `motion.custom(Dur.BASE, Ease.steps(EmberHop.STEPS))` | stepped; OFF snaps; REDUCED 150 ms |
| Ember arc, sparks | `designUnitPx`, `particles` flag | extras, FULL only |
| Tab icon frames | `Dur.TAP` per frame, index-chosen | FULL only |
| Label fade, cell weights | `motion.fast()` | weights are extra E3 |
| Tab content | the existing `NavDisplay` cross-fade (`FAST`) | unchanged |
| Haptic | `HapticCue.TICK` on a change of tab | none on a re-tap |
| Sound | none | |
| Spoken | each cell: label as `contentDescription`, `Role.Tab`, selected state; the bar is not a group node (no extra spoken line) | reading order: content first, bar last, as today |
| Mirrored | the `Row` mirrors; the ember follows measured centres | proved by the mirrored picture |

## 4. Unit tests (by name)

All JVM only; every test that reads a repository file uses `Assume`. Existing tests are updated in the same slice.

- **`T/ui/TabModelTest` (new):** `tabsAreInTheDocumentedOrder` (HOME, CHATS, LIBRARY, BOTS, AGENT; tags `tab.home` ... `tab.hermes`; every `root.showsBar()`); `everyRouteBelongsToOneTab` (the table of 3.1, all 15 kinds); `starredCountsAsLibrary`; `settingsAndOverlaysBelongToHome`; `selectedIsTheTabOfTheBottomEntry` (`[Hermes, Settings(null)]` gives AGENT; `[Chats, Chat]` gives CHATS; empty gives HOME); `labelModeAllWhenEveryLabelFits`, `labelModeSelectedOnlyWhenOneDoesNot`, `labelModeEdgeEqualRoomFits`, `labelModeEmptyOrNoRoomIsAll`; `cellWeights` (all 1f in ALL; the selected one 1.8f in SELECTED_ONLY; five entries); `englishTabLabelsFitTheRoom` (reads `R/values/strings_shell.xml` with `Assume`: the five `shell_tab_*` are at most `LABEL_ROOM_CHARS` code points); `trAndFrTabLabelsAreAtMost14`; `backRule` (Home at the root: false; other tab roots: true; Welcome at the root: false; any `canPop`: true); `emberHopSteps` (`step(0f)=0`, `step(0.2f)=1`, `step(1f)=5`, NaN 0; `lift` table `0,1,2,2,1,0`; `sparkSteps(3)=[2,1]`, `sparkSteps(1)=[0]`, `sparkSteps(0)` and `sparkSteps(5)` empty); `tabIconFrames` (4 frames `[1,2,3,0]`; 1 or 0 frames empty); `statusBarRuleMatchesTheOldLine` (a table over route kinds, `practiceOn`, `themeDark`, `homeSkyDark`: equals `((route != Home || practiceOn) && !themeDark) || (route == Home && !practiceOn && homeSkyDark)` whenever `welcomeDark` is false and the route is not Welcome) and `statusBarRuleWelcome`.
- **`T/ui/nav/NavStackTest` (updated):** `tabKeepsStableRootIdPerTab` adds Library 6 and keeps Settings(null) 5; `libraryShelfIsNotIdentity` (`rootIdFor(Library("saved")) == 6 == rootIdFor(Library())`); `tabFromOneShelfToAnotherKeepsId6` (entries differ by route, id equal); `isRootIdOnlyForOneToSix` (replaces `isRootIdOnlyForOneToFive`: 0 false, 6 true, 7 false, 99 false); `welcomeAtTheBottomGetsAFreshIdAndHomeReplacesIt` (`reset([Welcome])`: id >= 100; `tab(Home)` gives `[NavEntry(1, Home)]`); `encodeDecodeRoundTripWithNewRoutes` (`library`, `library:files`, `welcome`, `whatsnew`); `decodeKeepsLibraryRootIdAtTheBottom` (`["6|library:files"]` gives id 6); `decodeDropsABadShelfKey` (`library:Bad Key`, `library:`); `decodeOldSavedSettingsRoot` (`["#100","5|settings"]` gives `[NavEntry(5, Settings(null))]`, and `back()` is false).
- **`T/ui/nav/NavMotionTest` (updated):** `everyRouteHasBarAndTabIndexAndRank` with the table of 3.1 (Settings now `false, 0, 1`; Hermes 4; Terminal 4; Bots 3; Starred 2; Library, `Library("saved")` `true, 2, 0`; Welcome, WhatsNew `false, 0, 1`); `welcomeToHomeIsATabFade` (`navMotionKind([100], [1]) == TAB`); `homeToSettingsIsAPushAndBackIsAPop`; `tabToLibraryIsATabFade`.
- **`T/ui/nav/NavTagsTest` (updated):** `screenTagsAreDistinct` with 15 kinds (15 distinct tags, `screen.library`, `screen.welcome`, `screen.whatsnew`); `theNamedTagsAreTheDocumentedOnes` (`tab.library`; `TAB_SETTINGS` is gone, so the assertion is deleted); `mainTabTagsAreDistinctAndStartWithTab`; the library shelf does not change the screen tag.
- **`T/AppLinksTest` (updated):** `libraryAndWelcomeAndWhatsNewRoundTrip`; `libraryEncodings` (`library`, `library:saved`); `libraryRejectsABadShelf`; `existingRoutesStillRoundTrip` unchanged.
- **`T/DebugLinksTest` (updated):** `routeKeysParse` adds `library`, `library:files`, `library:saved`, `whatsnew`; `welcomeRouteOnlyOnTheTwin` (`isTwin = false`: `route == null`, `"route"` in `rejected`; `true`: `Route.Welcome`); `stackForEveryRoute` with the 15 kinds and the new expectations (`[Home, Settings(null)]`, `[Home, Settings("usage")]`, `[Library("saved")]`, `[Welcome]`, `[Home, WhatsNew]`, `[Chats, Starred]` unchanged; `first().showsBar()` for every route except `Welcome`); pass 2: `firstrunAndWhatsNewExtras` (forms `reset|done|off` and `pending|off`; `firstrun` rejected when not twin; a bad value is rejected and applies nothing; the two new parameters are the last two and default to `Keep`).
- **Compile-time:** every `when (route)` over `Route` stays exhaustive (the compiler is the test): `Nav.kt` (four functions), `NavTags.screen`, `RouteContent`, `DebugLinks.stackFor`.
- Existing tests that must stay green unchanged: `EnumLabelsTest` (needs the entry and the key of 3.7), `PxlParserTest` (needs `icon_library`), `ResourceParityTest` (needs 3.9's tr and fr files), `StretchedLanguageTest` (the probe of `Texts.kt`), `ScenarioFilesTest.everyTagIsKnown` (INTEGRATE-B).

## 5. Proof on the phone

Categories as in the SPLIT: P = owner-debug build, pins and pictures only, `"safe": true` scenarios; T = the twin only; U = unit test; E = the owner's eyes. The robot scenarios are the lead's (after INTEGRATE-B); this section says what each must read.

| # | Item | Cat | How |
|---|---|---|---|
| 1 | Five tabs, no sixth, Settings not among them | P | link `route home`; the dump has `nav.bar` and exactly `tab.home`, `tab.chats`, `tab.library`, `tab.bots`, `tab.hermes` (content-desc Home, Chats, Library, Bots, Agent), no `tab.settings`; the selected tab has `selected="true"` |
| 2 | Every tab opens | P | tap each; `app.log` shows `I/Nav: top=library id=6 stack=[6\|library]` and the screen tag appears; back from Library goes to Home (`nav` log `top=home id=1`) |
| 3 | Settings is pushed, with no bar, and back returns | P | link `route settings` gives `stack=[1\|home,<id>\|settings]`, `nav.bar` absent, back returns to Home; link `route settings:usage` likewise; link `route library:saved` then `route library:files` shows the shelf change under one id 6 (the live-route rule) |
| 4 | Process death keeps the tab | P | `am kill` with Library open, reopen: Library again (as the existing `nav-state-restore`, with `library`) |
| 5 | The ember hop and the icon frames | P, E | pictures at rest in both themes and in `anim:off`; the motion itself is read from a screen recording and from `phone-perf` P5 (tab-switch frame times; the hop adds no frame over 16.7 ms; at 120 Hz 8.3 ms is the target); only the owner's eyes can judge whether it hops pleasantly and whether the icon frames look right |
| 6 | Label room | P | `lang=en-XZ` (stretched), `lang=fr`, `lang=tr`, and text scale 1.3 and 2.0 (the lead's `-AllowSystemSettings`): `app.log` has `I/Nav: bar mode=SELECTED_ONLY` where expected; pictures show only the chosen label, never a cut one; `a11y` finds a content-desc on every tab |
| 7 | Mirrored layout | P | `rtl 1`: the bar's order is mirrored, the ember lands under the chosen tab |
| 8 | Welcome is the first frame on a wiped twin | T | `pm clear` of the **twin only**, cold start: the first `Nav` lines are `initial=[welcome]` and `top=welcome`, with no `top=home` before; links sent during the Welcome are ignored (log line); the Welcome never returns after it ends and the app restarts |
| 9 | An existing installation starts on Home | P | owner-debug cold start: `initial=[home]`, and `shared_prefs/settings.xml` has no `firstRun` entry (read only) |
| 10 | What's new opens and returns | T, P | `whatsnew pending` on the twin shows the note; its action opens `screen.whatsnew`; back returns; `route whatsnew` on the owner-debug opens the page read only |
| 11 | No new permission, component or manifest line | U | the manifest diff of both passes is empty |

**Only the owner's eyes can judge:** how the hop feels (five steps over `Dur.BASE`, the arc, the sparks); whether the 8u by 2u ember and the tab icons read well at 24 dp; the icons-only mode in French and Indonesian; whether "Agent" instead of "Hermes" is understood on the tab; the cross-fade from the Welcome into Home followed by Home's own entrance.

## 6. Core and extras, and the order of cutting

**Core (never cut):** the tab set; `Route.Library` with its root id; `MainTab`/`TabModel`; `PocketBottomBar` with the stepped ember slide and the label-room rule; `Route.Settings` as a pushed screen; the status-bar rule; `PixelIcon.LIBRARY` with its label; the five strings and their tr and fr; the deletion of `ui_nav_*` and the `Texts.kt` probe; `Route.Welcome`, `Route.WhatsNew` and the initial stack with its first-frame rule and the gate that catches a throwing `FirstRun`; the debug-link routes and extras; all updated tests.
**Extras, first cut first:** E1 the two sparks; E2 the arc (SPLIT item 6); E3 the animated cell weights (weights snap instead); E4 the icon play-once (the rest frame only, when ART-B's frames are cut or late); E5 the ember's stepped travel falls back to a plain `motion.fast()` slide. A cut item keeps its symbol with an empty body (`EmberHop.lift` returns 0, `TabIconPlay.frames` returns empty).

## 7. REQUESTS to other areas, and what SHELL provides

**Provides (exact).** `Route.Library(shelf: String? = null)` (keys `library`, `library:<shelf>`), `Route.Welcome` (`welcome`), `Route.WhatsNew` (`whatsnew`); `NavTags.TAB_LIBRARY`, tags `screen.library|welcome|whatsnew`; `NavStack.rootIdFor`, `LAST_ROOT_ID = 6`; `MainTab`, `TabModel`, `LabelMode`, `EmberHop`, `TabIconPlay`, `StatusBarRule` (internal, in `J/ui/TabModel.kt`); `PocketBottomBar(selected, nav, onHeight)`; `PixelIcon.LIBRARY`; strings `shell_tab_*`, `shell_gear_cd`, `eng_icon_library`; debug routes `library`, `library:<shelf>`, `welcome` (twin only), `whatsnew`; extras `firstrun`, `whatsnew`; the rule "a tab root is opened with `nav.tab`".

**Requests (exact).**
- **BARS:** (1) the gear opens Settings with `LocalNavigator.current.go(Route.Settings())`, label `t(R.string.shell_gear_cd)`; (2) Home tiles: Saved is `nav.tab(Route.Library(LibraryShelf.SAVED))`, Hermes is `nav.tab(Route.Hermes)` (today `nav.go(Route.Hermes)` at `HomeScreen.kt:356`; `go` would push a second Agent screen over Home); (3) the Agent tab's title key is BARS's (`bar_agent_title`); (4) `ChatListScreen`'s star button keeps `nav.go(Route.Starred)`.
- **LIBRARY:** `@Composable fun LibraryScreen(shelf: String?)` and `object LibraryShelf { const val FILES = "files"; const val SAVED = "saved" }`; `shelf == null` keeps the current shelf (state kept with `rememberSaveable`), a non-null shelf selects it (`LaunchedEffect(shelf)`) and an unknown key is treated as null; the screen does not write `screen.library` (AppShell does); `Route.Starred` keeps working and its back stays `nav.back()`.
- **WELCOME-CORE:** `FirstRun.initialRoutes(context: Context): List<Route>` (synchronous, never empty, never throws, no write on an existing installation); `object FirstRunDebug { enum class Mode { RESET, DONE }; fun parse(text: String): Mode?; fun pin(mode: Mode?) }`; the end of the Welcome (done or skipped) calls `WhatsNewStore.markSeen(...)`, writes its flag, and **then** `LocalNavigator.current.reset(listOf(Route.Home))`; a rehearsal or a pinned run writes nothing.
- **WELCOME-UI:** `@Composable fun WelcomeScreen()` with its own `BackHandler` on every step; `object WelcomeChrome { val iconsDark: StateFlow<Boolean> }`; the scene may reach the bottom edge only if the Welcome wants it: AppShell pads a screen without a bar by `bottomSafeInsets()` (as for chat).
- **START:** `@Composable fun WhatsNewHost()` (no parameters; reads `LocalNavigator.current`; the note's action does `nav.go(Route.WhatsNew)`); `@Composable fun WhatsNewScreen()` (its own top bar with back = `nav.back()`); `object WhatsNewDebug { enum class Mode { PENDING }; fun parse(text: String): Mode?; fun pin(mode: Mode?) }`, no write to the `whatsnew` preferences while pinned. If the first screen is the Welcome the splash cannot hand the flame to Home's fire: START's listener must fade when no `FireAnchor` target arrives in the time S8 allows.
- **ART-B:** sprites `tabicon_home|chats|library|bots|agent` (12 by 12, 4 frames, frame 0 equal to the static icon, 90 ms per frame, ink-only so the tint applies) and `icon_library` (core: `PixelIcon.LIBRARY` needs it for `PxlParserTest`); unique names.
- **APPEAR-B:** `SettingsScreen(section: String?)` keeps its signature; its back arrow is always present (it is always pushed now); the gear is not in the Settings screen.
- **LANG:** no conflict: SHELL edits `Texts.kt` line 244 (one token) and `EnumLabels.kt` (one line); the nine new languages translate the seven keys of `strings_shell.xml` (kind `tab`, room 10 and 14).
- **INTEGRATE-B:** add the tags of 3.10 to `ScenarioFilesTest` (keep `tab.settings` until the lead removes it); regenerate `app/src/debug/res/values-en/` and `values-en-rXZ/` with `tools/make-test-language.ps1` after pass 1 (they still hold the five `ui_nav_*` keys; the stretched probe needs `shell_tab_home` there).
- **The lead's robot pass** (not SHELL's files): `tools/scenarios/smoke-launch.json:16`, `smoke-tabs-labels.json:20`, `smoke-hermes-running.json:9`, `perf/P5-transitions.json` (the five `tab.settings` taps; replace by `home.gear` or the debug link `route settings`, and add `tab.library` to the cycle), `tools/phone-lib.ps1:1753` (`Set-PaPracticeSwitch`: open Settings by the link, then the Runtime section); the `desc` alternative `Hermes` of `tab.hermes` becomes `Agent`; `routes.json` gains `library`, `library:files`, `library:saved`, `whatsnew` (`welcome` is twin only); `nav-state-restore.json` gains a Library step; new scenarios `w1b-tabs` (safe), `w1b-library` (safe), `w1b-welcome` and `w1b-whatsnew` (twin).

## 8. Risks

1. **`AppRoot.kt`, `DebugLinks.kt` and `Nav.kt` are edited by several waves of work at once** (the Wave 0 defect fixes, 1a's INTEGRATE and LINKS). Mitigation: anchors by quoted text, a build and `-Test` after every pass, and SHELL starts only after 1a is merged.
2. **A missing sprite must not break the bar:** the glyph falls back to a static `PixelIcon`; but `PixelIcon.LIBRARY` itself needs `icon_library` (`PxlParserTest`), so slice B must precede slice C.
3. **Same-id route replacement** (`Library` shelves): handled by the live-route read; a unit test cannot prove `AnimatedContent`'s behaviour, so it is proof item 3.
4. **Scenarios break until the lead rewrites them** (`tab.settings`): on the phone only, not in unit tests (the known-tags set keeps the old tag until then).
5. **Text measurement cost and flicker:** one `TextMeasurer` pass per label set, remembered on labels, density and font scale; the room comes from `screenWidthDp`, so the first frame already has the right mode.
6. **Ember in a mirrored layout or while weights animate:** centres are measured, not computed; proof item 7.
7. **The Welcome and the practice banner:** the banner is a row above every screen, so during the guided chat it sits above the scene; `StatusBarRule` sends the status-bar icons to the banner's rule while it shows (`practiceOn` wins), which is right.
8. **Links ignored during the Welcome** might swallow a notification tap on a twin that is mid-Welcome; accepted (decision 8).
9. **Stale debug string folders** until INTEGRATE-B regenerates them: the build still compiles (extra translations are allowed), only `make-test-language.ps1 -Check` complains.
10. **The Settings screen's state is no longer kept** between visits (3.0 decision 2); `AppearanceSection` and others keep their own persisted settings, so nothing user-visible is lost beyond which section was open.
11. **Hermes tile as `nav.tab`:** back from the Agent tab then goes to Home through `nav.tab(Home)` (a root has nothing below it), the same place a push would have returned to; the Home screen's state (id 1) is kept.

## 9. Open points for the lead

1. **Every area's new English file needs its Turkish and French at once, or `ResourceParityTest` is red between slices** (SPLIT 4.6 puts the 1b keys of tr and fr in stage 4, after the slices). This draft makes SHELL write `values-tr` and `values-fr` for its seven keys (3.9). Decide for the contract: either every area does the same (recommended: 1a did it), or the test skips files that 1b has registered as "awaiting stage 4".
2. **`J/ui/text/Texts.kt` (one token) and `J/ui/text/EnumLabels.kt` (one line) are edited by SHELL**, files that SPLIT gives to nobody in 1b. Confirm, or move the two edits to LANG (they are needed for the build in pass 1, so LANG would have to land them before).
3. **Links during the Welcome are dropped** (3.0 decision 8), and **`route welcome` is twin only** (decision 7): the Welcome cannot be photographed on the owner-debug build. The alternative is a rehearsal pin in WELCOME that guarantees no write, no unpacking and no practice-model switch, which the owner-debug could then use; it needs WELCOME's agreement and a stronger safety proof.
4. **The `tab.settings` tag and known-tags entry** are removed from the code now and from `ScenarioFilesTest` only after the lead's scenario rewrite. If the rewrite comes late, the lead may prefer to keep `"tab.settings"` known for longer; no code consequence.
5. **Hop duration** is `Dur.BASE` (220 ms) for five steps; `Dur.FAST` (150 ms) would match the content cross-fade exactly. A taste call for the owner's eyes (assumed BASE; one constant).
