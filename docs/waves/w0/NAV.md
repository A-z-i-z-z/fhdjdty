# Wave 0 contract, area NAV: navigation v2, robot hooks at the root, debug links, manifest

Draft written 2026-11-25 for `docs/waves/w0-contract.md`. Written for the PASS case of experiments S1 and S3 (fallbacks in section 10).
Everything below was read in the code on this date: `ui/Nav.kt` (101 lines), `ui/AppRoot.kt` (468), `MainActivity.kt` (73),
`AppLinks.kt` (88), `AndroidManifest.xml` (200), `res/xml/shortcuts.xml`. Ground rules are those of `docs/phase3-plan.md`: no Gradle, edit only your files,
English texts only, nothing is committed. Packages are under `dev.pocketagent.app`.

## 1. Scope

Everything is core (Wave 0 has no extras). Work items:

| # | Item (plan) | What it is |
|---|---|---|
| N1 | 0.7, A6 | `NavStack`: pure back stack where every entry has a stable id, plus the string codec for saving |
| N2 | 0.7 | `Navigator` v2 on top of N1; the whole old API stays; adds `entries`, `currentEntry`, `replace`, `reset` |
| N3 | 0.7, A6 | One `SaveableStateHolder` in the shell: each entry's `rememberSaveable` and list state is kept while the entry is on the stack, and dropped when it leaves it |
| N4 | 0.7, patterns 7+8 | `NavDisplay`: a `SeekableTransitionState` + `Transition.AnimatedContent` keyed by entry id. Push, pop and tab motion from design-language section 3 with the tokens of DESIGN; REDUCED and OFF; right-to-left signs |
| N5 | 0.7, pattern 9 | One `SharedTransitionLayout` around the screen area, the two locals, `Modifier.navSharedElement` / `navSharedBounds`, `SharedKeys` |
| N6 | 0.7, A7 | Back gesture drives the outgoing screen (`PredictiveBackHandler`, manifest opt-in) |
| N7 | 0.7 | The five tab roots keep their state when the user switches tab (stable root ids); not only entries "on the stack" |
| N8 | 0.7, pattern 8 | Bottom bar: the ember travels from the old tab to the new one along a small arc in five steps (single ember overlay); bar visibility follows the transition's target |
| N9 | 0.3 | `testTagsAsResourceId` at the root, `NavTags`, `Modifier.robotRoot()`, tags on the bottom bar, the screen hosts and the share dialog |
| N10 | 0.3, 0.11 | One debug-only link (action `dev.pocketagent.app.DEBUG`) with extras `route`, `clock`, `lang`, `rtl`, `reset` |
| N11 | 0.3 | `AppClock`: the app-wide clock the pinned time feeds (STAGE reads it) |
| N12 | 0.11 | `DebugOverrides` (language, forced right-to-left, in memory only) applied by `LocaleScope`; `LocalAppLanguage` |
| N13 | all | Manifest: `enableOnBackInvokedCallback`; `shortcuts.xml` takes the package from the build; the debug logs for the robot (route line, saved-state size) |

**Cut first if the session runs out** (in this order):
1. **N8, the ember arc.** The ember jumps from tab to tab as today; only a motion detail is lost. The visibility-from-target part of N8 stays (it is needed by N6).
2. **N5, shared elements** (and with them every consumer line in section 7). Pattern 9 reduces to pattern 7: an avatar fades and slides with its screen instead of travelling. Needs S3 anyway.
3. **N7, tab-root retention.** Entries on a stack still keep their state (A6 fixed); a list on a tab root returns to the top when the user leaves the tab and comes back, as today.

Not cuttable: N1 to N4, N9 to N13 (the robot and every later wave stand on them). N6 is core but falls back by flag (section 10).

## 2. Files (NAV owns them exclusively)

| File | Change |
|---|---|
| `ui/Nav.kt` | Modify. Add `NavEntry`, rewrite `Navigator` on `NavStack` (old API kept), move `Route.showsBar()`, `tabIndex()`, `rank()` here as `internal` (bodies unchanged from `AppRoot.kt:176-191`). `Route`, `encode()`, `decodeRoute()`, `LocalNavigator` unchanged |
| `ui/AppRoot.kt` | Modify. `LocaleScope` gets the debug overrides and provides `LocalAppLanguage`; `AppShell` is rebuilt around `NavDisplay` (replaces `routeTransition` at 193-219, `AnimatedContent` at 260-268, `BackHandler` at 250); `PocketBottomBar` gets tags and the ember; `navigateToLink` gets the `DebugOpen` case. Private composables `IncomingShareDialog`, `BottomTab`, `RouteContent` keep their place. No new `stringResource(`, no new literal `tween(`/`RoundedCornerShape(`/`Toast`, no `left`/`right` words |
| `MainActivity.kt` | Modify. Debug link handled first in `handleIncomingIntent`; debug-only saved-state size log in `onSaveInstanceState` |
| `AppLinks.kt` | Modify. `AppLink.DebugOpen`, `AppLinks.ACTION_DEBUG`. The five existing actions stay literal |
| `AndroidManifest.xml` | Modify (section 6) |
| `res/xml/shortcuts.xml` | Modify: lines 12, 23, 34 `targetPackage`. **Not in the plan's table; NAV claims it (Phase 3 SURFACE owned `res/xml/*`). Lead to confirm** |
| `ui/nav/NavStack.kt` | New. Pure Kotlin (no Compose, no Android): ids, operations, codec |
| `ui/nav/NavMotion.kt` | New. Pure: `NavMotionKind`, `navMotionKind(...)` |
| `ui/nav/NavDisplay.kt` | New. `internal fun NavDisplay(...)` composable: transition, state holder, shared layout, back gesture |
| `ui/nav/NavScopes.kt` | New. Locals, `SharedKeys`, `navSharedElement`, `navSharedBounds` |
| `ui/nav/NavFlags.kt` | New. Two compile-time switches for the S1/S3 fallbacks |
| `ui/nav/NavTags.kt` | New. Tag strings, `Modifier.robotRoot()` |
| `DebugLinks.kt`, `DebugOverrides.kt`, `AppClock.kt` | New, package `dev.pocketagent.app` next to `AppLinks.kt` |
| Tests (new) | `app/src/test/java/dev/pocketagent/app/ui/nav/NavStackTest.kt`, `NavMotionTest.kt`; `.../DebugLinksTest.kt`, `AppClockTest.kt`; `AppLinksTest.kt` modified (two lines: the new action constant and `DebugOpen`) |

## 3. Shared symbols NAV provides (exact)

### 3.1 Navigator (all in `dev.pocketagent.app.ui`, file `Nav.kt`)

```kotlin
@Immutable data class NavEntry(val id: Long, val route: Route)   // id: unique in one Navigator, never reused, survives process death

class Navigator internal constructor(private val stack: NavStack) {
    constructor(initial: List<Route> = listOf(Route.Home)) : this(NavStack(initial))   // as today
    val entries: List<NavEntry>        // bottom..top, never empty, snapshot state (read in composition recomposes)
    val currentEntry: NavEntry         // = entries.last()
    val current: Route                 // KEPT, = currentEntry.route
    val canPop: Boolean                // KEPT
    fun go(route: Route)               // KEPT, push with a fresh id; no-op when route == current; Route.Home means tab(Route.Home)
    fun back(): Boolean                // KEPT, pops the top entry; false (nothing changes) when only the root is left
    fun tab(route: Route)              // KEPT, stack becomes [route]; the root keeps the stable id of its tab (see 3.2); other entries are dropped
    fun replace(route: Route)          // NEW, top entry gets a NEW id and the new route (its saved state is dropped); on a root entry it behaves like tab(route); no-op when equal
    fun reset(routes: List<Route>)     // NEW, whole stack replaced; empty list = [Home]; entry 0 gets a root id when it is a tab root, the others fresh ids
    internal fun encoded(): List<String>
    companion object { val Saver: Saver<Navigator, Any> }   // KEPT name; new string format, old format still readable (3.2)
}
val LocalNavigator: ProvidableCompositionLocal<Navigator>      // KEPT
internal fun Route.showsBar(): Boolean; internal fun Route.tabIndex(): Int; internal fun Route.rank(): Int
```

**Call sites of the old navigator in files NAV does not own: none change.** The API above is a superset (checked with grep of `LocalNavigator`, `nav.go(`, `nav.back(`, `nav.tab(`, `nav.canPop`, `nav.current`: only `go`, `back`, `tab` are used outside `AppRoot.kt`, in the 12 files under `ui/` that read `LocalNavigator.current`). Nothing is listed for SWEEP.

### 3.2 NavStack (`ui/nav/NavStack.kt`, package `dev.pocketagent.app.ui.nav`; `internal`, used by tests and `Navigator`)

```kotlin
internal class NavStack(initial: List<Route> = listOf(Route.Home)) {
    val entries: List<NavEntry>                  // copy, bottom..top
    fun go(route: Route): Boolean                // true when the stack changed
    fun back(): Boolean
    fun tab(route: Route)
    fun replace(route: Route)
    fun reset(routes: List<Route>)
    fun encode(): List<String>                   // ["#<nextId>", "<id>|<route key>", ...] route key = Route.encode()
    companion object {
        const val FIRST_FREE_ID = 100L           // fresh ids start here; 1..5 are the root ids
        fun rootIdFor(route: Route): Long?       // Home 1, Chats 2, Bots 3, Hermes 4, Settings(null) 5; everything else null
        fun isRootId(id: Long): Boolean          // id in 1..5
        fun decode(saved: List<String>): NavStack
    }
}
```
Rules: entry 0 gets `rootIdFor(route)` when not null, else a fresh id; every other entry a fresh id (so a second "new bot" editor never inherits the first one's text, and the same route twice on one stack has two ids). `decode`: a first element starting with `#` is the saved counter; an element `digits|key` keeps its id; a bare key (the old format) gets a fresh id; unknown keys are dropped; an id below 100 that does not match `rootIdFor(route)` or an id used twice is replaced by a fresh one; an empty result is `[Home]`; the counter becomes `max(saved, highest id + 1, 100)`.

### 3.3 Saved state (the rule every area relies on)

`AppShell` holds `val holder = rememberSaveableStateHolder()`. Each entry's content is `holder.SaveableStateProvider(entry.id) { ... }`, so a screen's `rememberSaveable`, `rememberLazyListState()` and `rememberScrollState()` come back where they were. An entry's state is removed (`holder.removeState(entry.id)`) when its content leaves the composition and `nav.entries` no longer holds the id and `!NavStack.isRootId(id)`. The `DisposableEffect` that does this must be written BEFORE the `SaveableStateProvider` call in the same composable, so it is disposed after the provider has saved. Plain `remember { }` state is not kept (as today). Screens need no change.

### 3.4 Scopes and shared elements (`ui/nav/NavScopes.kt`)

```kotlin
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?>   // staticCompositionLocalOf { null }; provided by NavDisplay
val LocalNavAnimatedScope: ProvidableCompositionLocal<AnimatedVisibilityScope?>      // compositionLocalOf { null }; provided per entry (the AnimatedContentScope)
val LocalNavEntry: ProvidableCompositionLocal<NavEntry?>                             // compositionLocalOf { null }; the entry whose content this is
object SharedKeys {
    fun chatAvatar(convId: String): Any    // chat row avatar (ChatListScreen, HomeScreen) <-> avatar in the chat's top bar
    fun chatTitle(convId: String): Any     // chat row title <-> the top bar title
    fun botAvatar(botId: String): Any      // bot card avatar (BotsScreen) <-> avatar in the editor's header
    fun botCard(botId: String): Any        // bot card as a whole <-> the editor's header (bounds)
}
@Composable fun Modifier.navSharedElement(key: Any): Modifier
@Composable fun Modifier.navSharedBounds(key: Any, resize: Boolean = false): Modifier
```
Behaviour: both return `this` unchanged when either local is null, when `NavFlags.SHARED_ELEMENTS` is false, or when `Pocket.motion.level == AnimLevel.OFF`. Otherwise `navSharedElement` is `sharedElement(rememberSharedContentState(key), animatedVisibilityScope = LocalNavAnimatedScope.current, boundsTransform = { _, _ -> Pocket.motion.glide<Rect>() })` and `navSharedBounds` is the same with `sharedBounds` (`resize` picks `ResizeMode.ScaleToBounds()` over `RemeasureToBounds`). Declaring: the screen that leaves and the screen that arrives each put the same `SharedKeys.x(id)` on the element, inside their own composition (inside the entry's content, never inside a dialog or sheet). A key may appear once per screen.

### 3.5 Debug link, overrides, clock (package `dev.pocketagent.app`)

```kotlin
// AppLinks.kt
sealed interface AppLink { /* existing */ data class DebugOpen(val route: Route) : AppLink }   // only ever made by DebugLinks
object AppLinks { /* existing */ const val ACTION_DEBUG = "dev.pocketagent.app.DEBUG" }

// DebugLinks.kt
sealed interface Setting<out T> { data object Keep : Setting<Nothing>; data object Clear : Setting<Nothing>; data class Set<T>(val value: T) : Setting<T> }
data class DebugRequest(val reset: Boolean, val route: Route?, val clock: Setting<Long>, val language: Setting<String>,
                        val rtl: Setting<Boolean>, val rejected: List<String>)   // rejected = names of extras that could not be parsed
object DebugLinks {
    const val EXTRA_ROUTE = "route"; const val EXTRA_CLOCK = "clock"; const val EXTRA_LANG = "lang"; const val EXTRA_RTL = "rtl"; const val EXTRA_RESET = "reset"
    fun parse(intent: Intent?, debug: Boolean = BuildConfig.DEBUG, zone: ZoneId = ZoneId.systemDefault()): DebugRequest?
    internal fun parseParts(action: String?, extras: Map<String, String?>, flags: Int, debug: Boolean, zone: ZoneId): DebugRequest?
    fun apply(req: DebugRequest): Route?     // applies reset/clock/lang/rtl at once, logs each rejected name with AppLog.w("Debug", ..), returns the route to open or null
    fun stackFor(route: Route): List<Route>  // the stack a route is opened with, see below
}
// DebugOverrides.kt: in memory, never persisted, never set in a release build
object DebugOverrides {
    val language: StateFlow<String?>         // BCP-47 tag; null = no override
    val forceRtl: StateFlow<Boolean?>        // null = follow the language
    fun setLanguage(tag: String?); fun setForceRtl(value: Boolean?); fun clear()
}
// AppClock.kt
object AppClock {
    val pinned: StateFlow<Long?>                          // epoch millis of the pinned instant, null = real time
    fun nowMillis(): Long                                 // pinned instant, else System.currentTimeMillis()
    fun now(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime
    fun pin(epochMillis: Long); fun unpin()               // only DebugLinks.apply calls these
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Long?   // pure; null when it cannot be read
}
// ui/AppRoot.kt
val LocalAppLanguage: ProvidableCompositionLocal<String>   // staticCompositionLocalOf { "" }; the language in force ("" = system), debug override included
```
A pinned clock is **frozen** (it does not advance). Only code that asks `AppClock` sees it (STAGE's sky, Wave 1's calendar); message timestamps and alarms keep using the real time.

**The link, exact command line** (robot's `link` step; the twin's component needs the FULL class name because the package is `dev.pocketagent.app.debug` and `.MainActivity` would resolve to `dev.pocketagent.app.debug.MainActivity`, which does not exist):
```
adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity --es route chat:abc123 --es clock 2027-03-21T20:30 --es lang tr --es rtl 1 --es token <T>
```
`T` is `adb shell run-as <pkg> cat files/debug-link.token` (trimmed). The app creates that file at its first start and never rotates it. A link without the right token is ignored and `app.log` gets one line "debug link refused".
| Extra (all `--es`) | Values | Effect |
|---|---|---|
| `route` | A `Route.encode()` key: `home chats chat:<id> bots botnew bot:<id> hermes terminal settings settings:<section> routines routinenew routine:<id> starred` | Stack becomes `DebugLinks.stackFor(route)` through `nav.reset`, after the chats have loaded. Unknown key: listed in `rejected`, nothing opens |
| `clock` | `2027-03-21T20:30` or with seconds, or with an offset (`...+03:00`, `...Z`); local form is read in the device zone; `off` | Pin / unpin `AppClock` |
| `lang` | A BCP-47 tag (`en`, `tr`, `fr`, or the stretched test language's tag as TEXT names it); `system` | Set / clear `DebugOverrides.language`. NAV forwards the string unchanged |
| `rtl` | `1 true on` / `0 false off` / `auto` | Force right-to-left / left-to-right / follow the language |
| `reset` | `1` | Clears clock, language and rtl first; then the other extras apply |
Missing extras mean `Keep`. No `route` extra = the screen stays. `stackFor`: tab roots `[route]`; `Chat` and `Starred` `[Chats, route]`; `BotEditor` `[Bots, route]`; `Terminal` `[Hermes, route]`; `Routines` `[Home, route]`; `RoutineEditor` `[Home, Routines, route]`; `Settings(section != null)` `[Settings(null), route]`. In a release build `parse` returns null for this action, whatever the extras, so the intent is ignored (there is no manifest filter for it: the activity is addressed by name).

### 3.6 Robot hooks and flags

```kotlin
// ui/nav/NavTags.kt
object NavTags {
    const val BAR = "nav.bar"
    const val TAB_HOME = "tab.home"; const val TAB_CHATS = "tab.chats"; const val TAB_BOTS = "tab.bots"; const val TAB_HERMES = "tab.hermes"; const val TAB_SETTINGS = "tab.settings"
    const val SHARE_DIALOG = "share.dialog"; const val SHARE_NEW = "share.new"; const val SHARE_ITEM = "share.item"
    fun screen(route: Route): String        // "screen.home" "screen.chats" "screen.chat" "screen.bots" "screen.boteditor" "screen.hermes" "screen.terminal" "screen.settings" "screen.routines" "screen.routineeditor" "screen.starred"
}
@OptIn(ExperimentalComposeUiApi::class) fun Modifier.robotRoot(): Modifier = semantics { testTagsAsResourceId = true }
// ui/nav/NavFlags.kt
internal object NavFlags { const val BACK_GESTURE_DRIVES_SCREEN = true; const val SHARED_ELEMENTS = true }
```
`robotRoot()` is applied once at the root of `AppShell`. It does NOT reach windows of their own (`Dialog`, `ModalBottomSheet`): DESIGN applies it inside `PocketDialog` and the sheet (section 7).

## 4. Shared symbols NAV consumes

| From | Symbol (as in design-language 2.4) | Used for |
|---|---|---|
| DESIGN | `val Pocket.motion: MotionScheme @Composable get`; `class MotionScheme { val level: AnimLevel /* effective: app, system, battery saver */; fun <T> fast()/base()/glide(): FiniteAnimationSpec<T> }` | Level for N4/N5/N6; `glide<Float>()` settles the back gesture; `glide<Rect>()` for shared bounds |
| DESIGN | `object Dur { const val TAP, FAST, BASE: Int }` (ms), `object Ease { val enter: Easing; val exit: Easing; fun steps(n: Int): Easing }` | Push/pop/tab specs; ember arc (`Ease.steps(5)`) |
| DESIGN | `enum class AnimLevel { FULL, REDUCED, OFF }` stays in `ui/theme/Appearance.kt` | |
| DESIGN | The replacement for `Toast.makeText` (the "PetNote" host): **signature to be named by DESIGN** | Only `AppRoot.kt:296`; NAV leaves the toast until the symbol exists (open question 1) |
| TEXT / WORDS | `t(id, vararg args)` | WORDS replaces the 5 `stringResource(` and 1 `getString(` left in `AppRoot.kt` after NAV is done; NAV adds none |
| CONFIG | `PocketApp.pendingLink: MutableStateFlow<AppLink?>`, `PocketApp.chat.loaded`, `PocketApp.from(ctx)` unchanged | Link delivery |
| BUILD | `BuildConfig.DEBUG` (generated, `buildConfig = true` is already on); `resValue("string", "pa_application_id", <applicationId>)` with `buildFeatures.resValues = true` | Debug gate; `shortcuts.xml` |

Nothing from STREAM, STAGE or SWEEP is consumed.

## 5. Strings

None. NAV adds no visible text (the bottom bar labels are the existing `ui_nav_*` keys; no new content descriptions). The file `strings_nav.xml` with prefix `nav_` is reserved and is NOT created. Debug link values and `AppLog` lines are developer-only and not translated.

## 6. Manifest entries NAV writes

| Where | Entry | Why |
|---|---|---|
| `<application>` (line 22-33) | `android:enableOnBackInvokedCallback="true"` | Back gesture progress reaches the app (N6, experiment S1). Also makes the system's own back-to-home animation play at Home |
| `<application>` line 31 | `android:supportsRtl="true"` | **Already present.** Plan 0.11 asks for it; nothing to add |
| `FileProvider` line 194 | `${applicationId}.fileprovider` | Already uses the placeholder. Code derives the authority from `packageName` (`AttachmentStore.kt:32`, `MainViewModel.kt:223`). No change |
| `res/xml/shortcuts.xml` 12, 23, 34 | `android:targetPackage="@string/pa_application_id"` (was the literal `dev.pocketagent.app`) | A static shortcut of the twin must start the twin. Resource files take no manifest placeholder; BUILD's `resValue` is the way (the E0 experiment built it). `targetClass="dev.pocketagent.app.MainActivity"` stays: class names follow the namespace, not the applicationId. **Untried at run time** (the E0 install was blocked): if the launcher refuses the reference, fallback is a copy of the file in `app/src/debug/res/xml/` with the literal twin package (BUILD owns that folder) |

What must become `${applicationId}` in the manifest: nothing more. Everything else that spells `dev.pocketagent.app` is a class name (namespace) or an intent action. **The five link actions (`AppLinks.kt:22-26`) and the four in `NotificationActionReceiver.kt:119-123`, `AgentService.kt:127`, `RoutineScheduler.kt:324` stay literal**: all are used with explicit components (no manifest filter names them), so equal strings in the owner's app and the twin cannot collide; deriving them from `BuildConfig.APPLICATION_ID` would break `AppLinksTest.actionNamesAreTheDocumentedOnes` in the debug unit tests and every PendingIntent already posted on the owner's phone. No new component, permission, filter or `<queries>` entry. `androidx.profileinstaller` brings its own receiver through the library manifest merge; NAV writes nothing for it.

## 7. REQUESTS to other areas

- BUILD: `app/build.gradle.kts`: `buildFeatures { resValues = true }` and `resValue("string", "pa_application_id", "<applicationId of the variant>")` in both build types (twin: `dev.pocketagent.app.debug`).
- BUILD: `tools/local-build.ps1` line 499 (`$AppId/$AppId.MainActivity`): the component for the twin is `dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity`; same for every `am start`, `cmd statusbar ... FireTileService` (`dev.pocketagent.app.debug/dev.pocketagent.app.service.FireTileService`). `.MainActivity` shorthand is wrong for the twin.
- BUILD: keep `release` at `BuildConfig.DEBUG == false` (the default). If shortcuts fail at run time (section 6): `app/src/debug/res/xml/shortcuts.xml` with the literal twin package.
- ROBOT: the `link` step builds the command line of 3.5 (action, FULL class name, `--es` extras). Back is `adb shell input keyevent KEYCODE_BACK` (no progress; the same pop motion plays) or a scripted edge swipe `adb shell input swipe <x1> <y> <x2> <y> <ms>` for S1-style measuring. Wait about 400 ms after a link before dumping (two screens exist during a transition, so `screen.*` tags can appear twice).
- ROBOT: a screen's tag may not be exported to `uiautomator` (a Compose node with only a tag is not always in the accessibility tree). The sure signal is the debug line in `app.log`: `I/Nav: top=<route key> id=<n> stack=[<id|key>,...]`, written by NAV on every change of the top entry in a debug build. Use `expect` on that line if `screen.*` is missing.
- DESIGN: apply `Modifier.robotRoot()` (from `dev.pocketagent.app.ui.nav`) to the content root inside `PocketDialog` and the new `PocketSheet`; both are separate windows and the root setting does not reach them. Confirm that `Pocket.motion.level` is the effective level and that `Dur`/`Ease` are as quoted in section 4. Name the toast replacement's exact signature.
- TEXT: if the language pack source needs the active language inside a composable, read `LocalAppLanguage` (it includes the debug override) instead of `AppearanceStore`; non-composable code (services) keeps reading the saved language and ignores the override on purpose.
- STREAM (only if S3 passes): in the chat's top bar put `Modifier.navSharedElement(SharedKeys.chatAvatar(convId))` on the avatar and `Modifier.navSharedBounds(SharedKeys.chatTitle(convId))` on the title. Nothing else in `ChatScreen.kt` depends on NAV; its `BackHandler(enabled = searching)` (line 267) keeps priority over NAV's handler because it is registered deeper.
- SWEEP (only if S3 passes): in `ChatListScreen.kt` (conversation row, line ~118) and `HomeScreen.kt` (conversation rows, ~336) the same two modifiers with the row's `convId`; in `BotsScreen.kt` `SharedKeys.botAvatar` / `botCard` on the card, in `BotEditorScreen.kt` the matching header. Keys once per screen.
- WORDS: `AppRoot.kt` has no `left`/`right` words; nothing to do there beyond the text calls.
- LEAD: see the open questions in the report (ownership of `shortcuts.xml`, the toast, tab-root retention).

## 8. Tests (JVM, no instrumentation; all logic under test is Compose-free)

`NavStack` holds all stack logic as plain Kotlin; `Navigator` is a thin wrapper that mirrors `entries` into one `mutableStateOf` and is not unit-tested. `navMotionKind`, `DebugLinks.parseParts`, `stackFor`, `AppClock.parse` and `NavTags.screen` are pure.

| Class `.` test | Proves |
|---|---|
| `NavStackTest.pushGivesIncreasingFreshIds` | `go` ids start at 100, never repeat |
| `.goOnSameTopIsNoOp` | Same route on top: no new entry, same ids, returns false |
| `.sameRouteTwiceOnStackHasTwoIds` | `Chat(a), Hermes, Chat(a)` gives three distinct ids |
| `.goHomeResetsToHomeRoot` | `go(Route.Home)` leaves `[Home]` with id 1 |
| `.backPopsTopAndFalseAtRoot` | Pops one; at the root returns false and changes nothing |
| `.tabKeepsStableRootIdPerTab` | Home 1, Chats 2, Bots 3, Hermes 4, `Settings(null)` 5; `Settings("usage")` as a root gets a fresh id |
| `.tabDropsEverythingAboveRootAndKeepsRootId` | `[Chats, Chat, Hermes]` then `tab(Chats)` gives `[Chats(2)]` |
| `.replaceGivesNewIdAndKeepsDepth` | Top replaced by a fresh id, size unchanged; on a root it equals `tab` |
| `.resetBuildsStackWithRootIdAtBottom` | `reset([Chats, Chat(x)])` gives id 2 then a fresh id; empty list gives `[Home]` |
| `.encodeDecodeRoundTripKeepsIdsAndCounter` | Same entries, same ids, next push does not reuse an id |
| `.decodeOldFormatGivesFreshIds` | `["chats","chat:x"]` loads, ids fresh |
| `.decodeDropsUnknownKeysAndFixesBadIds` | Unknown keys dropped; duplicate id and a root id on the wrong route are replaced; empty gives `[Home]`; counter at least highest id + 1 |
| `.isRootIdOnlyForOneToFive` | Boundary values 0, 1, 5, 6, 99, 100 |
| `NavMotionTest.pushPopTabNone` | `navMotionKind(oldIds, newIds)`: new top above old top PUSH; old top gone and new top below POP; root to root TAB; same top NONE |
| `.deepLinkIntoDetailIsPush`, `.detailToRootByTabIsPop`, `.replaceOfDetailIsPush` | The "neither relation" cases decide by new/old depth |
| `.everyRouteHasBarAndTabIndexAndRank` | `showsBar`, `tabIndex`, `rank` for all 11 route kinds equal today's values (copied from `AppRoot.kt:176-191`) |
| `.slideSignFollowsLayoutDirection` | `slideSign(rtl: Boolean): Int` is +1 / -1 |
| `DebugLinksTest.releaseBuildIgnoresTheAction` | `debug = false` gives null for any extras |
| `.wrongActionIsNull` | Other actions, `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` gives null |
| `.routeKeysParse` | Every key of the table in 3.5 gives the route `decodeRoute` gives; unknown key goes to `rejected`, route stays null |
| `.clockForms` | Local, seconds, offset, `Z`, `off`, garbage (rejected) |
| `.langAndRtlForms` | `tr` Set, `system` Clear, blank rejected; the six rtl words; `auto` Clear |
| `.resetFlagAndKeep` | Missing extras are `Keep`; `reset=1` sets the flag |
| `.stackForEveryRoute` | The table of 3.5; the last element is always the route; the first is always a tab root |
| `AppClockTest.pinFreezesAndUnpinReleases` | `nowMillis()` equals the pin twice in a row; after `unpin` within a second of the system clock |
| `.parseUsesZone` | `2027-03-21T20:30` in `UTC` and in `+03:00` differ by 3 h |
| `NavTagsTest.screenTagsAreDistinct` | One distinct non-empty tag per route kind |
| `AppLinksTest` (existing, edited) | Existing cases unchanged; `ACTION_DEBUG` literal; `parseParts(ACTION_DEBUG, ...)` is null (the debug action is not an `AppLink` of the normal parser) |

What unit tests cannot prove (robot scenarios, section 9): that state really comes back, that the animation plays, that the debug link works on the phone.

## 9. Robot hooks

- **Tags:** `nav.bar`; `tab.home`, `tab.chats`, `tab.bots`, `tab.hermes`, `tab.settings` (each also `selected` through `Role.Tab`); `screen.<kind>` on every entry host (kinds in 3.6); `share.dialog`, `share.new`, `share.item` in the incoming-share dialog. `testTagsAsResourceId` is on at the root of the shell.
- **Log line (debug builds):** `I/Nav: top=<key> id=<n> stack=[...]` on every change of the top entry, and `I/Nav: saved state <n> bytes` on every `onSaveInstanceState` (the retained states of N3 and N7 all go into that bundle; Android crashes an app whose saved state passes about 1 MB).
- **Links:** section 3.5. Proof scenarios NAV needs: (a) scroll the chat list, open a chat, press back: list at the same place; (b) scroll Home or Bots, switch tab and back: same place (N7); (c) type a name in the routine editor, press back (confirm discard), open a new routine editor: it is empty (fresh id per push); (d) every route by `route=`; (e) `rtl=1` photograph of each screen, `lang=<test language>`; (f) `clock=` then screenshot of Home (for STAGE); (g) `kill` then reopen: stack restored from the saver; (h) edge swipe back from a chat with the finger held halfway, screenshot, release.
- No practice-model keyword is needed by NAV.

## 10. Experiment branches

- **S1 passes (assumed):** progress values arrive at `PredictiveBackHandler`. Build as in N6: `PredictiveBackHandler(enabled = nav.canPop && NavFlags.BACK_GESTURE_DRIVES_SCREEN) { progress -> ... }`; per event `seekState.seekTo(ev.progress, targetState = belowId)`; if the flow completes after at least one event, `seekState.animateTo(belowId, Pocket.motion.glide())` and then `nav.back()`; on `CancellationException` after an event `seekState.animateTo(seekState.currentState)` and rethrow; with no event at all (button, 3-button navigation) just `nav.back()` and let `NavDisplay`'s `LaunchedEffect(topId)` animate with the transition's own specs. `LaunchedEffect(topId)` does `if (seekState.targetState != topId) seekState.animateTo(topId)`. A plain `BackHandler(enabled = !nav.canPop && route != Route.Home) { nav.tab(Route.Home) }` stays for tab roots. The revealed screen is composed during the gesture, so the bottom bar's visibility reads `seekState.targetState`.
- **If S1 fails:** set `NavFlags.BACK_GESTURE_DRIVES_SCREEN = false`. The shell then uses `BackHandler(enabled = nav.canPop || route != Route.Home) { if (!nav.back()) nav.tab(Route.Home) }` as today and the pop motion plays after the gesture ends (the plan's register line for S1). Keep the manifest attribute only if S1 shows it does no harm (the system back-to-home animation); otherwise remove it. Nothing else changes.
- **S3 passes (assumed):** N5 as in 3.4.
- **If S3 fails** (flicker, or a frame over 8.3 ms): set `NavFlags.SHARED_ELEMENTS = false`. `NavDisplay` then does not create the `SharedTransitionLayout` and provides null scopes; both modifiers return `this`; the consumers' lines stay and do nothing. Pattern 9 reduces to pattern 7 (fade and slide), the plan's register line for S3.
- Opt-ins: `SeekableTransitionState`, the shared transition classes and `testTagsAsResourceId` may or may not still be marked experimental in Compose 1.13 (animation 1.13.0 and activity-compose 1.13.0, where `PredictiveBackHandler` is not deprecated, are in the Gradle cache). Write the `@OptIn(...)` and delete one only if the compiler says it cannot resolve the annotation; an unnecessary opt-in is a warning.

## 11. Risks, and where the plan is wrong or impossible

1. **A6 confirmed, with its cause** (`AppRoot.kt:260-268`): `AnimatedContent(targetState = route)` is keyed by the `Route` value; the outgoing content leaves the composition when its exit ends, and `remember` or `rememberSaveable` state goes with it. `rememberSaveable` state would also be saved in the activity bundle only for the one visible screen. Both are fixed by N1 to N3.
2. **Plan 0.11 "supportsRtl in the manifest" is already done** (`AndroidManifest.xml:31`). The real RTL gaps are in Compose code: slide offsets are absolute pixels, so N4 multiplies them by a sign taken from `LocalLayoutDirection`.
3. **Plan 0.1 "link actions take the package from the build"** is not done for the actions, on purpose (section 6). It is done for `shortcuts.xml`, the only place that must name the twin.
4. **Plan 0.3 "`testTagsAsResourceId` at its root"** does not reach `Dialog` and `ModalBottomSheet` (own windows); `PocketDialog` is used at 16 sites and `ModalBottomSheet` at 5 (survey-counts section B). Hence the request to DESIGN. A Compose node that has only a tag may not appear in `uiautomator`; hence the log line.
5. **Saved-state size.** N3 and N7 keep up to five tab roots plus the stack in the saved bundle, and Android saves it every time the app goes to the background. Editors hold their texts with `rememberSaveable` (`BotEditorScreen.kt:134-143`, `RoutineEditorScreen.kt:120-131`; the bot prompt can be long). The debug line in section 9 measures it; if it nears 500 KB the first measure is to drop root retention (N7), which is why it is on the cut list.
6. **Removal order.** `removeState` must run after the provider saved (3.3). Wrong order loses state silently or keeps a leak; the robot scenarios (a), (b) catch the first.
7. **The twin cannot be started by `.MainActivity`** and may not install at all (Play Protect blocked the first install in experiment E0). Everything the robot needs from NAV is also reachable in the owner's build only through the real UI; the debug link exists in debug builds only.
8. **Gesture and screen-level back handlers.** A screen with its own enabled `BackHandler` (dirty editors `BotEditorScreen.kt:165`, `RoutineEditorScreen.kt:170`, the Hermes screen `HermesScreen.kt:88`, chat search `ChatScreen.kt:267`) is deeper than NAV's handler and wins: that back has no preview, as today.
9. **Both screens exist during a transition**, including the outgoing one's tags and its running animations (the Home scene keeps drawing until its exit ends). It is the same cost as today's `AnimatedContent`.
10. **Kotlin gotchas that apply here** (`AGENTS.md`): in `Navigator` the `NavStack` property must be declared before the `mutableStateOf` that reads it (init order); no `/*` inside KDoc, so write route keys without a glob such as `chat:*`; a `<string>` is never needed here. `kotlin-reflect` is not on the classpath, so tests list routes explicitly.
11. **Statusbar icon rule** `SystemStatusBarIcons(darkIcons = route != Route.Home && !c.isDark)` (`AppRoot.kt:237`) keeps reading the top route; it flips at once at the start of a transition, as today.
12. **Behaviour change to expect, not a bug:** Settings with a section (pushed over Home) is classified by the stack, so it now PUSHes with the slide (today it cross-slides like a tab, because it shows the bar); the bar stays visible.
