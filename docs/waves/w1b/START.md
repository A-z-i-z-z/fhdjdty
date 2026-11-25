# Wave 1b, area START: contract draft ("the splash (experiment S8) and What's new")

Package `dev.pocketagent.app` is written `app`. `J/` is `app/src/main/java/dev/pocketagent/app/`, `T/` is `app/src/test/java/dev/pocketagent/app/`, `R/` is `app/src/main/res/`, `A/` is `app/src/main/assets/`. Line numbers were read in the work tree on 2026-11-25 (other agents are editing it, so find code by the quoted text). Sources: `docs/waves/w1b/SPLIT.md` (4.3, 6, 8 S8, 9, 10, 11, 13), `docs/phase4-plan.md` 1.6 (lines 834 to 841), section 6 step 9 (540 to 541), section 8 row S8 (2953), `docs/design-language.md` 2.4 (animation level), 3 (patterns 2, 7, 18), 7 (Pêt notes, 405 to 409), 8.16 (six kinds, five states), 9, 10, 11 (line 849: cold start to Home's first frame under 700 ms), 13.2, 13.5; `docs/waves/w0-contract.md` (PetNotes, `t()`, the debug link, tokens); `docs/waves/w1a-contract.md` sections 3.4, 3.8, 3.10, 3.11, 6.2 and 7.5; `docs/waves/w1/HOME.md` 3.6 (the entrance) and `FIRE.md` (the fire's hit box).

**What this draft takes from Wave 1a, and how.** 1a is being built in `dist/wt-w1` and is not in the work branch. Everything below that touches a 1a symbol (`FireTags.HIT`, `FireGeometry.hitRect`, `HomeHero`, `HomeEntrance`, `PetNoteHost`) is taken from the 1a contract's registry (its section 3) and its area drafts, not from today's code, and each such place says so. START needs from 1a only: the fire's box in window pixels (BARS publishes it, section 7), and the unchanged signature of `PetNotes.show` (1a only edits `PetNoteHost`, PET.md line 38).

## 1. What the plan and the design language ask

| Ask | Where | Meaning here |
|---|---|---|
| "the start screen: the system's splash shows the flame, which then travels to its place in the scene (experiment S8; without it, a plain fade)" (B3) | plan 1.6, l.835 to 837; S8 l.2953 | The theme carries the flame as the splash icon (all API 31 and later). An exit listener moves the icon's view to the fire's place and fades the splash. If S8 fails, the fade ships; the fade and the flame icon are the core, the travel is the cut-first part (SPLIT 11, item 5) |
| Cold start to the first frame of Home under 700 ms; no frame over 16.7 ms during a transition | DL 11 (l.848 to 849) | Holding the splash for Home's first frame must stay inside the 700 ms budget: the hold has a cap (5.3), measured by the existing `phone-perf` scenario P6 |
| "What's new": after an update a Pêt note opens one short page per version, `assets/whatsnew/<version>.md`, in every interface language, drawn by the app's own Markdown view (B4) | plan 1.6, l.838 to 839; section 6 step 9 | `assets/whatsnew/0.3.md` plus `0.3.<code>.md` (LANG, stage 4); the reader draws it with `MarkdownText` (`ui/chat/MarkdownView.kt`) |
| Pêt notes replace toasts; a note is two lines, 80 characters at most | DL 7 (l.408 to 409), 13.2 | the note is posted with `PetNotes.show` (exists, w0) |
| Every screen is one of six kinds with five states | DL 8.16 | the reader is a **Detail, reader** (one column of 62 to 70 characters, a bar with the title and at most three actions); states in 3.7 |
| Texts: no exclamation marks except Pêt's; capitals written by the author; sentence case | DL 13.5 | the page and the keys follow it; a unit test pins the page |
| A new user never sees the note; an existing installation sees it once per page | SPLIT 4.3 | the rule is pure and tested (3.2) |
| Plan decisions assumed (all: "assumed, owner may change"): 3 (the ten languages: the nine pages exist for them), 19 (a wave may ship without its extras), 2 (every experiment on the twin) | SPLIT 12 | S8 and every destructive step run on the twin only |

## 2. What exists today, and what 1a changes by then

| Thing | Today (work tree) | By the time 1b builds |
|---|---|---|
| Splash | `R/values/themes.xml` (9 lines): `Theme.PocketAgent`, parent `android:Theme.Material.NoActionBar`, plum `windowBackground` and `colorBackground` `#1D1220`, bars `#130A16`, and one API 31 attribute in the base folder: `android:windowSplashScreenBackground #1D1220` (line 8). No `values-v31`, no icon, no listener. The manifest applies the theme to the whole application (`AndroidManifest.xml` line 33) and `minSdk` is 26, `targetSdk` 28 (`app/build.gradle.kts` lines 67 and 70). No library for splash screens exists (`gradle/libs.versions.toml` has no `core-splashscreen`), so only the platform API (31 and later) is used | unchanged by 1a. ART-B adds `R/drawable/ic_splash_flame.xml` (SPLIT 4.7) |
| `MainActivity` | `app/MainActivity.kt`: `onCreate` (line 32) calls `enableEdgeToEdge()`, `super.onCreate`, `handleIncomingIntent(intent)` and `setContent { AppRoot() }` (line 37); `launchMode="singleTop"`, `configChanges` includes `uiMode` and `screenLayout`; the six launcher aliases all point at it (manifest lines 66 to 154) | unchanged by 1a |
| Home's first frame | `HomeScreen` shows a night sky scene at once; Hermes states change the fire | 1a's `HomeHero` (HOME-HERO) draws the scene and the fire. Its **entrance** (`HomeEntrance`, HOME.md 3.6) plays once per process at the first Home composition: the land and the fire rise by up to 48 rows over `REVEAL_MS = 900`; the fire's own hit box (`FireGeometry.hitRect`, `FireTags.HIT = "home.fire"`, 3.4 of the 1a contract) is built from the final geometry (`reveal = 1`), so it already stands at the fire's final place |
| Pêt notes | `ui/components/PetNote.kt`: `PetNotes.show(text, kind, actionLabel, onAction)` (line 209; any thread, never throws), 80-character room, 6 s with an action, a note waits for a started host and is dropped after 8 s (`STALE_MS`); `PetNoteHost` is placed last in the root box of `AppShell` (`ui/AppRoot.kt` line 310) | 1a (PET) adds the rise and the swipe inside `PetNoteHost` only; every public signature stays |
| Markdown | `ui/chat/MarkdownView.kt`: `MarkdownText(text, modifier, color = Pocket.colors.onBotBubble, fontSize = 15.sp, onRunCode = null)` is public; `ui/chat/Markdown.kt`: `parseMarkdown` is pure and public; links open only for `http`, `https`, `mailto` | unchanged |
| The language in force | `LocalAppLanguage` (`AppRoot.kt` line 150, `""` = system) and `resolveAppLocale(language, configurationLocale)` (`ui/text/AppLocale.kt`); the text function `Texts.get(ctx, id, args)` and `t()` | unchanged; LANG keeps `Appearance.language` as the interface language (SPLIT 4.6) |
| Animation level outside Compose | `effectiveAnimLevel(setting, animatorScale, powerSave)` (pure, `ui/theme/Motion.kt` line 95) and `readSystemMotion(context)` (internal, `ui/theme/SystemMotion.kt`); `Appearance.animations` | unchanged |
| Version name | `BuildConfig.VERSION_NAME` is `0.2.<ciRun>` plus `-local<n>` and `-test` (`app/build.gradle.kts` line 50 and 250); INTEGRATE-B makes it `0.3.<ciRun>...` in slice F | the page key is its first two numbers (3.2) |
| A place for stored prefs | the pattern of `AppearanceStore.get(ctx)` and `PresencePrefs.get(ctx)`: a singleton over `SharedPreferences`, a `StateFlow`, `apply()` | the preferences file `whatsnew` does not exist yet |
| Assets | `A/pixel/`, `A/text/protected-keys.txt`; no `A/whatsnew/` | the folder is new |
| Tests / tags | `petnote` and `petnote.action` exist; nothing named `whatsnew` or `splash` | `ScenarioFilesTest` learns the new tags (INTEGRATE-B, section 7) |

Findings of the reading that shape the design:

1. **The splash is a platform feature at target 28.** Android 12 (API 31) and later shows a system splash for every app, built from the theme's `windowSplashScreen*` attributes; `Activity.getSplashScreen()` and `SplashScreen.setOnExitAnimationListener` are platform API 31 and work through the platform, not through a library. Whether they behave at target 28 on this phone is exactly S8. Below API 31 nothing exists and the plum window background carries the start (no white flash).
2. **The platform has no "keep the splash on screen" call** (that is `setKeepOnScreenCondition` of the library, which the project does not have). The documented platform way is a pre-draw listener on the content view that returns false until the app is ready (3.5). Compose composes and lays out while drawing is held, so the fire's box can be published before the first draw.
3. **The 1a entrance moves the fire at the moment of the splash.** The hit box stands at the final place, but the scene's own fire needs 900 ms to rise into it. The flight is therefore timed to `Dur.SCENE` (480 ms), when the scene's fire is within about a quarter of its travel, and the target is the final place (3.4, risk 3).
4. **`PetNotes` drops a note that waits for a host longer than 8 s**, and a started host is needed to show one (`PetNoteQueue.attach`). The note must therefore be posted while the activity is started, after the first frames, never during the splash or the Welcome.
5. **The Welcome's unpacking writes `files/rootfs.stamp` from step one** (SPLIT 4.5), so "does a rootfs stamp exist" cannot tell a fresh user from an existing installation after the Welcome has started. What's new therefore does not derive anything: it uses the shell's knowledge that the Welcome is on the stack (the `hold` parameter) and a mark written when the Welcome ends (3.2).

## 3. The design

### 3.1 Files

Create:

| File | Content |
|---|---|
| `J/ui/pixel/FireAnchor.kt` | `FireTarget`, `FireAnchor` (3.4). Pure Kotlin and `kotlinx.coroutines.flow`; no `android.` import |
| `J/ui/pixel/SplashPlan.kt` (new, not in the SPLIT list) | pure decisions and numbers of the splash: `SplashPlan`, `Handoff` (3.5). No `android.` import |
| `J/SplashHandoff.kt` (new, not in the SPLIT list; package `app`, next to `MainActivity`) | the only Android code of the splash: the hold, the exit listener, the watchdog (3.5). Everything API 31 is inside `if (Build.VERSION.SDK_INT >= 31)` |
| `J/ui/whatsnew/WhatsNewLogic.kt` | pure: page key, comparison, language code, asset names, the decision (3.2) |
| `J/ui/whatsnew/WhatsNewStore.kt` | `WhatsNewStore` (prefs `whatsnew`) and `WhatsNewPages` (asset reader) (3.3) |
| `J/ui/whatsnew/WhatsNewHost.kt` | `@Composable fun WhatsNewHost(hold: Boolean, onOpen: () -> Unit)` (3.6) |
| `J/ui/whatsnew/WhatsNewScreen.kt` | `@Composable fun WhatsNewScreen()` and `object WhatsNewTags` (3.7) |
| `J/ui/whatsnew/WhatsNewDebug.kt` | `WhatsNewPin`, `WhatsNewDebug` (3.8). Extra: cut order 4 |
| `A/whatsnew/0.3.md` | the English page (3.9) |
| `R/values-v31/themes.xml` | the splash theme for API 31 and later (3.4) |
| `R/values/strings_start.xml` | prefix `wn_` (3.10) |
| `T/ui/whatsnew/WhatsNewLogicTest.kt`, `WhatsNewAssetsTest.kt` | section 4 |
| `T/ui/pixel/FireAnchorTest.kt`, `SplashPlanTest.kt`, `SplashThemeFilesTest.kt` (new, not in the SPLIT list) | section 4 |

Modify: `J/MainActivity.kt` (one line and one import, 3.5), `R/values/themes.xml` (one line removed, 3.4). **Nothing else**: the manifest is not touched (no permission, no component; the theme is already applied to the application), no library is added, `AppRoot.kt`, `Nav.kt`, `DebugLinks.kt`, `HomeHero.kt` and `PetNote.kt` are other areas' files and are only reached through the requests of section 7.

### 3.2 The rules of What's new (`ui/whatsnew/WhatsNewLogic.kt`, pure)

```kotlin
package app.ui.whatsnew
object WhatsNewLogic {
    const val DIR = "whatsnew"                 // asset folder
    const val MAX_PAGE_CHARS = 1200            // pinned by WhatsNewAssetsTest

    /** The first two numbers of a version name: "0.3.17-local2-test" gives "0.3", "1.0.0" gives "1.0", "0.55.3" gives "0.55".
     *  Leading zeros are dropped ("0.03.1" gives "0.3"). Null when the name has fewer than two numbers or a part is not 1 to 4 digits. */
    fun pageKey(versionName: String): String?
    /** Numeric, part by part (0.55 is newer than 0.6). A text that is not a key sorts below every key (and equal to another such text). */
    fun compareKeys(a: String, b: String): Int
    /** "pt-BR" and "pt_BR" give "pt"; "in" gives "id"; "iw" gives "he"; "ji" gives "yi"; the tag's language part, ASCII, 2 or 3 letters, lower-cased by a table
     *  (no .lowercase() call: the lint ratchet counts it in ui/). Anything else gives "". */
    fun languageCode(tag: String): String
    /** Asset names to try, first that opens wins: ["whatsnew/0.3.tr.md", "whatsnew/0.3.md"]; for "" or "en" only the English one;
     *  an empty list when [key] is not "<digits>.<digits>" (nothing that could climb out of the folder is ever built). */
    fun assetNames(key: String, language: String): List<String>
    /** The newer of the stored mark and [key] (a mark that is not a key is replaced); never goes down (a rolled-back app keeps what it saw). */
    fun mergeSeen(stored: String?, key: String): String

    enum class Why { NO_KEY, HOLD, NO_PAGE, ALREADY_SEEN, NEWER_THAN_APP, UPDATED, EXISTING_FIRST }
    data class Decision(val show: Boolean, val key: String?, val why: Why)
    /** [key] = pageKey of this build, [seen] = the stored mark or null, [hold] = the Welcome is on the stack, [pageExists] = the English page of [key] exists. */
    fun decide(key: String?, seen: String?, hold: Boolean, pageExists: Boolean): Decision
}
```

The decision, in this order (first row that applies):

| # | Condition | Result | Meaning |
|---|---|---|---|
| 1 | `key == null` | no, `NO_KEY` | the version name has no two numbers: nothing is shown |
| 2 | `hold` | no, `HOLD` | the Welcome is on the stack: a new user never sees the note; the Welcome's end writes the mark (below) |
| 3 | `!pageExists` | no, `NO_PAGE` | a version without a page shows nothing (also keeps a wave that forgot the page silent) |
| 4 | `seen == null` | **show**, `EXISTING_FIRST` | with `hold` false and no mark, this is an installation that existed before What's new did (the owner's, a restored phone): it sees the note once |
| 5 | `compareKeys(seen, key) == 0` | no, `ALREADY_SEEN` | |
| 6 | `compareKeys(seen, key) > 0` | no, `NEWER_THAN_APP`, | a rolled-back build shows nothing and the mark is not lowered |
| 7 | `compareKeys(seen, key) < 0` (an unreadable `seen` sorts below every key) | **show**, `UPDATED` | an update |

**The mark.** A new user is protected by `hold` and by the mark the Welcome writes: `WhatsNewStore.markCurrentSeen(context)` is called by WELCOME-CORE inside `FirstRun.markDone` and `FirstRun.markSkipped`, **before** the shell leaves the Welcome (request R-WELCOME). So a user who finishes or skips the Welcome has `seen = <current key>` and sees the first note only at the next update. A user who quits half way and returns is still held. The Host marks the page seen **when it posts the note**, so a note that is swiped away or times out is not posted again; the page itself stays reachable (3.7 and open point 1).

**Stored data (optional fields only).** One new preferences file, `whatsnew`, one key `seen` (a string such as `0.3`). The previous build never opens the file; a rollback APK is unaffected. It is not in `BackupCore.PREF_SCHEMA` (SPLIT 7: `BackupManager.kt` stays untouched), so a restore returns it to "absent", which the rule reads as an existing installation. **This is the one write that START makes on the owner's phone without a setting being touched**: at the first start of the 1b build the note is posted once and `seen` is written (`whatsnew.xml`, one `apply()`). Nothing is written at any other start. No migration is needed: an installation that already exists simply has no file.

### 3.3 Store and pages (`ui/whatsnew/WhatsNewStore.kt`)

```kotlin
class WhatsNewStore private constructor(context: Context) {
    val seen: StateFlow<String?>                       // null = no file or no key
    fun markSeen(key: String)                          // seen = WhatsNewLogic.mergeSeen(seen, key); one apply(); no write when unchanged
    companion object {
        const val PREFS = "whatsnew"; const val K_SEEN = "seen"
        fun get(context: Context): WhatsNewStore       // singleton over applicationContext, as AppearanceStore.get
        /** markSeen(WhatsNewLogic.pageKey(BuildConfig.VERSION_NAME)) ; does nothing when the name has no key. WELCOME calls this. */
        fun markCurrentSeen(context: Context)
    }
}
sealed interface PageResult {
    class Found(val page: WhatsNewPage) : PageResult
    data object Missing : PageResult                   // no candidate file exists, not even the English one
    class Failed(val cause: Throwable) : PageResult    // a file exists but could not be read, or is not valid UTF-8
}
class WhatsNewPage(val key: String, val language: String, val assetName: String, val text: String)   // language = the language of the file served ("en" for the English fallback)
object WhatsNewPages {
    const val MAX_BYTES = 32 * 1024                    // a page larger than this is a mistake in writing: Failed, not a crash
    fun exists(context: Context, key: String): Boolean                      // the English page opens (blocking; call off the main thread)
    fun load(context: Context, key: String, language: String): PageResult  // tries WhatsNewLogic.assetNames in order; FileNotFoundException goes on to the next, any other error is Failed
    /** Debug builds only (BuildConfig.TEST_HOOKS): the newest key that has an English page, so a twin whose version is still 0.2 can show the 0.3 page. Null in every other build. */
    fun newestKey(context: Context): String?
    fun keyFor(context: Context, versionName: String): String?              // pageKey(versionName), or (TEST_HOOKS and that key has no page) newestKey
}
```
`WhatsNewStore` uses `SharedPreferences` so it is not unit-tested; every decision it takes is `WhatsNewLogic.mergeSeen`. Reading and writing run on the caller's thread (preferences are loaded once in memory; `apply()` is asynchronous).

### 3.4 The splash: files, `FireAnchor`, the theme

**`FireAnchor` (`ui/pixel/FireAnchor.kt`).** Where the fire is on screen, for the splash only.

```kotlin
@Immutable data class FireTarget(val xPx: Float, val yPx: Float, val sizePx: Float)   // the centre of the flame's own box and its height, in WINDOW pixels
object FireAnchor {
    val target: StateFlow<FireTarget?>
    fun current(): FireTarget?                  // = target.value
    fun open()                                   // MainActivity.onCreate, every instance: target = null, open = true, fireExpected = true
    fun close()                                  // the splash is gone (or there is none): target = null, open = false; publish becomes a no-op
    fun publish(xPx: Float, yPx: Float, sizePx: Float)   // any thread; ignored when closed, when a number is not finite, when x or y is negative or sizePx <= 0; an equal value is a no-op
    fun expectFire(expected: Boolean)            // SHELL: false when the first screen is not Home (a Welcome, a link), so the hold does not wait for a fire that will not come
    val fireExpectedNow: Boolean
}
```
Window pixels: the root view's coordinates (the app is edge to edge, so the window is the screen; the practice banner moves content down inside the window, which `boundsInWindow` already counts).

**`R/values/themes.xml`** keeps the plum `windowBackground`, `colorBackground` and both bar colours and **loses** line 8 (`android:windowSplashScreenBackground`), which moves to the new folder. **`R/values-v31/themes.xml`** (new) restates the whole style (a resource folder replaces a style, it does not merge):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- API 31 and later: the system's splash. The window colours repeat the base file on purpose (SplashThemeFilesTest keeps them equal). -->
    <style name="Theme.PocketAgent" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#1D1220</item>
        <item name="android:colorBackground">#1D1220</item>
        <item name="android:statusBarColor">#130A16</item>
        <item name="android:navigationBarColor">#130A16</item>
        <item name="android:windowSplashScreenBackground">#1D1220</item>
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_splash_flame</item>
        <item name="android:windowSplashScreenAnimationDuration">300</item>
    </style>
</resources>
```
The icon item is written **only when `R/drawable/ic_splash_flame.xml` exists** (ART-B, slice B); without it the line is left out and the system shows the launcher icon, which is a working fallback (a missing drawable would fail the build). No `windowSplashScreenIconBackgroundColor`: the whole 288 dp icon area is drawn on the plum background, and the system masks it to a circle of about two thirds (192 dp), which is why ART-B draws the flame inside that circle (request R-ART). The animation duration is ignored for a still vector (kept as the documented upper bound of an animated one). All six launcher aliases use this one theme: the splash shows the flame whichever icon the user picked (S8 checks that the system does not substitute the alias's own icon). The plum background is used at every hour and in every theme, as the window background always was; Home's sky then takes over (risk 5).

### 3.5 The splash: the hold, the exit, the numbers

**`SplashPlan` (`ui/pixel/SplashPlan.kt`, pure).**

```kotlin
object SplashPlan {
    const val HANDOFF_SHIPS = false          // the lead sets true after S8 passes (open point 2); a debug file can force it (below)
    const val HOLD_ENABLED = true            // false = no pre-draw hold (cut order 3)
    const val HOLD_MAX_MS = 500L             // longest wait for Home's first frame, counted from MainActivity.onCreate; S8 may change it (budget: cold start under 700 ms)
    const val HANDOFF_MS = Dur.SCENE.toLong()   // 480: the flame's flight (design language: SCENE is for rare moments such as the first launch)
    const val FADE_MS = Dur.FAST.toLong()        // 150: the plain fade, and the fade of the plum behind the flight
    const val LAND_MS = Dur.TAP.toLong()         // 90: the flame fades out as it lands
    const val WATCHDOG_MS = 1500L            // after this the splash is removed whatever happened (a splash that never leaves would cover the app)
    const val FLAME_FRACTION = 0.40f         // how much of the icon view's height the drawn flame covers, centred; ART-B draws the vector to it (R-ART)

    enum class Exit { SNAP, FADE, HANDOFF }
    enum class HoldEnd { TARGET, NO_FIRE, CAP }
    enum class Forced { SNAP, FADE, HANDOFF }
    /** null = keep holding. Order: a target wins, then "no fire expected", then the cap. */
    fun holdEnd(elapsedMs: Long, hasTarget: Boolean, fireExpected: Boolean): HoldEnd?
    /** OFF gives SNAP; REDUCED gives FADE; FULL gives HANDOFF only when [handoffOn] and [hasIcon] and [target] is not null, else FADE. */
    fun exitKind(level: AnimLevel, hasIcon: Boolean, target: FireTarget?, handoffOn: Boolean): Exit
    /** dx, dy = target minus the icon view's centre; scale = target.sizePx / (iconHeightPx * FLAME_FRACTION), clamped to 0.05..1 (the flame never grows). Null when iconHeightPx <= 0. */
    fun handoff(iconCx: Float, iconCy: Float, iconHeightPx: Float, target: FireTarget): Handoff?
    /** The text of files/debug-splash.txt: "snap", "fade" or "handoff" (trimmed, ASCII case-insensitive by a table); anything else null. */
    fun parseForced(text: String?): Forced?
}
@Immutable data class Handoff(val dx: Float, val dy: Float, val scale: Float)
```

**`SplashHandoff` (`J/SplashHandoff.kt`).** `internal object SplashHandoff { fun install(activity: Activity) }`, called once from `MainActivity.onCreate`, **after `setContent { AppRoot() }`** (the content view must exist; the exit listener is still set before the first frame, which is drawn after `onResume`). In `MainActivity.kt` the only edits are the import and the line `SplashHandoff.install(this)` after the `setContent` line. Behaviour:

1. `FireAnchor.open()`. If `SDK_INT < 31`: `FireAnchor.close()` and return (nothing else exists below 31).
2. `startedAt = SystemClock.uptimeMillis()`. In a build with `TEST_HOOKS` read `files/debug-splash.txt` (written by hand by the lead with `run-as` on the twin during S8; never by the robot) through `SplashPlan.parseForced`; `Forced.HANDOFF` or `FADE` or `SNAP` overrides the plan below.
3. **The hold** (only when `SplashPlan.HOLD_ENABLED`): `val content = activity.findViewById<View>(android.R.id.content)`; add an `OnPreDrawListener` that evaluates `SplashPlan.holdEnd(now - startedAt, FireAnchor.current() != null, FireAnchor.fireExpectedNow)`; while it is null it returns `false` (the frame is not drawn; Android schedules the next traversal itself, which is the documented use); when it is not null it removes itself, logs `Splash: hold released after <n> ms (<TARGET|NO_FIRE|CAP>)` and returns `true`. A hold that has ended is never started again. Composition, layout and `onGloballyPositioned` still run while drawing is held.
4. **The exit** (`activity.splashScreen.setOnExitAnimationListener { provider -> ... }`; the whole body inside `try`, with `provider.remove()` in the `catch` and in a handler post `SplashPlan.WATCHDOG_MS` after the call so the splash can never stay):
   - `level = effectiveAnimLevel(AppearanceStore.get(activity).appearance.value.animations, <animator scale from Settings.Global.ANIMATOR_DURATION_SCALE>, <PowerManager.isPowerSaveMode>)`; `kind = SplashPlan.exitKind(level, provider.iconView != null, FireAnchor.current(), SplashPlan.HANDOFF_SHIPS)` or the forced one.
   - `SNAP`: `provider.remove()`. `FADE`: `provider.view.animate().alpha(0f).setDuration(FADE_MS).withEndAction { provider.remove() }`.
   - `HANDOFF`: icon centre from `iconView.getLocationInWindow` plus half its size; `h = SplashPlan.handoff(cx, cy, iconView.height, target)`; the icon view animates `translationX = h.dx`, `translationY = h.dy`, `scaleX = scaleY = h.scale` over `HANDOFF_MS` (an `AccelerateDecelerateInterpolator`; the pixel look comes from the flame itself, there is nothing to snap) while the splash view's background fades from opaque to transparent over the same time (`ValueAnimator` on `view.background.mutate().alpha`), so the scene is uncovered while the flame flies; the icon then fades out over `LAND_MS` and `provider.remove()` is called. If `h` is null, the plan was `FADE`.
   - Every path ends with `FireAnchor.close()` and `Splash: exit <kind> level=<level> icon=<w>x<h> target=<x>,<y>,<size> dx=<> dy=<> scale=<> after <n> ms`.
5. If an exit listener could not be set (an exception on this device), the system's own exit plays and `FireAnchor.close()` is called after `WATCHDOG_MS` by a handler.

No timer, sensor or receiver outlives the splash; the listener, the watchdog and the pre-draw listener are one-shot. The code is outside `ui/`, so the lint ratchet does not count it; `FireAnchor.kt` and `SplashPlan.kt` use `Dur` constants and no `N.dp`.

**Core and S8.** Core (ships whatever S8 says): the theme with the flame, the system's own exit, the hold with its cap, `FireAnchor`. Cut-first: the flight (`HANDOFF_SHIPS`), which waits for S8's verdict. The plain fade is `Exit.FADE`.

### 3.6 The note (`ui/whatsnew/WhatsNewHost.kt`)

```kotlin
@Composable fun WhatsNewHost(hold: Boolean, onOpen: () -> Unit)   // draws nothing; SHELL places it once, beside PetNoteHost
```
Constants (private, named): `NOTE_DELAY_MS = 1500L` (after Home's entrance and the splash flight, which together take at most 900 ms). Behaviour, in one `LaunchedEffect(hold, WhatsNewDebug.ticket)` that runs only while the lifecycle is `STARTED` (`repeatOnLifecycle`), so a note is never posted to a stopped activity (the queue would drop it after 8 s):

1. `hold` true: return (no read, no write).
2. wait `NOTE_DELAY_MS`.
3. `key = WhatsNewPages.keyFor(ctx, BuildConfig.VERSION_NAME)`; `seen = if pin == PENDING then null else WhatsNewStore.get(ctx).seen.value`; `exists = withContext(Dispatchers.IO) { key != null && WhatsNewPages.exists(ctx, key) }`; `d = WhatsNewLogic.decide(key, seen, hold = false, exists)`; log `WhatsNew: decide key=<k> seen=<s> -> <show|no> (<why>)`.
4. If `d.show`: `PetNotes.show(Texts.get(ctx, R.string.wn_note, d.key), PetNoteKind.INFO, Texts.get(ctx, R.string.wn_note_action), onOpen)` and, unless a pin is set, `WhatsNewStore.get(ctx).markSeen(d.key)`.

Texts are read with `Texts.get(LocalContext.current, ...)` (the localized context of `LocaleScope`, as `AppRoot` does at its attach-failed note), so the note follows the in-app language. Haptics: none of its own (the note's tap and action are the existing ones). Sound: none (a Pêt note is silent). Motion: the note's own pop (pattern 2, `PetNoteHost`).

### 3.7 The reader (`ui/whatsnew/WhatsNewScreen.kt`): kind Detail, reader

```kotlin
@Composable fun WhatsNewScreen()
object WhatsNewTags { const val PAGE = "whatsnew.page"; const val SKELETON = "whatsnew.skeleton"; const val EMPTY = "whatsnew.empty"
                      const val ERROR = "whatsnew.error"; const val RETRY = "whatsnew.retry"; const val CLOSE = "whatsnew.close" }
```
The route and its screen tag `screen.whatsnew` belong to SHELL. The screen needs only `LocalNavigator` (exists) and so compiles before `Route.WhatsNew` does. Layout: `PocketTopBar(title = wn_title, onBack = { if (!nav.back()) nav.tab(Route.Home) })` (the bar holds no other action); under it a `verticalScroll` column, `widthIn(max = READING_MAX_DP.dp)` (a named Float constant, 520, about 66 characters at the reading size `Type.reading`), centred, side padding `Space.gutter`; content `MarkdownText(page.text, color = Pocket.colors.text, fontSize = Type.reading.fontSize)`; after it `Space.xl` and `PixelButton(wn_close, kind = PRIMARY)`, tag `whatsnew.close`, which does the same as back. Extra (cut order 1): when `Pocket.appearance.showMascot`, a 64 dp `Mascot(MascotMood.HAPPY)` above the page. The language is `WhatsNewLogic.languageCode(resolveAppLocale(LocalAppLanguage.current, LocalConfiguration.current.locales[0]).toLanguageTag())`; the page key is `WhatsNewPages.keyFor(ctx, BuildConfig.VERSION_NAME)`, or the pin's `MISSING` key (a key no file has) when pinned. The page loads in `produceState` on `Dispatchers.IO` keyed on (key, language, retry counter) and logs `WhatsNew: page key=<k> lang=<l> file=<asset or none>`.

The five states (DL 8.16):

| State | What shows |
|---|---|
| **Loading** | after 120 ms (named `SKELETON_DELAY_MS`, so a read that takes 5 ms never flashes): a `Skeleton` heading bar (about 60 per cent wide) and five `Skeleton` lines inside the same column, tag `whatsnew.skeleton`; never a spinner |
| **Content** | the page; cross-fade from loading in `motion.fast()` (`Crossfade`); the tag `whatsnew.page` is on the column that holds the page. A link in a page opens in the browser through the Markdown view's handler (only http, https, mailto) |
| **Empty** (`PageResult.Missing`: no file for this version, not even English) | `EmptyState(wn_empty_title, wn_empty_body, action = PixelButton(wn_close, SECONDARY))`, tag `whatsnew.empty` on its wrapper (Pêt's picture as today; `art =` is BARS's parameter and is not used here) |
| **Error** (`PageResult.Failed`: unreadable, not UTF-8, larger than 32 KB) | `EmptyState(wn_error_title, wn_error_body, action = PixelButton(wn_retry))`, tag `whatsnew.error`, the button tag `whatsnew.retry` raises the retry counter; what was kept is said in the body (nothing is touched) |
| **Offline** | **not applicable**: the pages are inside the app; nothing is fetched |
| **Needs permission** | **not applicable**: no permission is used |

Accessibility and layout: the top bar title is the screen's heading; the Close and Retry buttons carry their text as their label; text at double size scrolls inside the same column (nothing is fixed-height); mirrored layout (`rtl 1`) mirrors the bar and the button row, the page's text is laid out per paragraph by the Markdown view (all ten languages are left to right; a right-to-left page is Wave 12, which will set `LocalLayoutDirection` from `WhatsNewPage.language`). Haptics: the buttons' own. Sound: none. Motion: the push and pop of the route (pattern 7, SHELL) and the cross-fade above; at animation level OFF everything snaps (`Pocket.motion`).

### 3.8 The pin (`ui/whatsnew/WhatsNewDebug.kt`, extra)

```kotlin
enum class WhatsNewPin { PENDING, MISSING, BROKEN }
object WhatsNewDebug {
    val pin: StateFlow<WhatsNewPin?>
    val ticket: StateFlow<Int>                        // raised by every pin() so the Host decides again
    fun pin(p: WhatsNewPin?)                          // no-op unless BuildConfig.TEST_HOOKS; memory only; null unpins
    fun parse(text: String): WhatsNewPin?             // "pending", "missing", "broken"; anything else null ("off" is the caller's Clear)
    fun clear()                                       // reset=1
}
```
`PENDING`: the Host treats `seen` as absent and writes nothing (so the note appears although the page was seen). `MISSING`: the reader looks for a key no file has: the Empty state. `BROKEN`: the first load of the reader fails once (`Failed`); Retry succeeds: the Error state and its way out. All three are memory only and inert in a build without test hooks (the shell's debug link already ignores everything there). The debug link line and its test belong to SHELL (section 7): extra `whatsnew` with `pending|missing|broken|off`, applied after `wood` (the 1a order of 3.9), `reset=1` calls `WhatsNewDebug.clear()`.

### 3.9 The English page `A/whatsnew/0.3.md` (the exact text START writes; at most 1,200 characters)

```markdown
# Agirî 0.3: a living home

Home is now a small world under the real sky of your hour.

- The sky follows the clock: tonight's moon and the stars that are really overhead.
- The fire burns higher while the agent works. Tap it to light it. Hold it to put it out.
- Pêt has more to do, and looks over a chat while the agent is busy.
- Five tabs: Home, Chats, Library, Bots and Agent. Settings is the gear at the top.
- The Library keeps your saved messages and the files you gave the agent.
- A first start that leads you in step by step, with a practice chat that needs no key.
- Ten languages. A language that nobody has proofread is marked in the picker.
```

About 640 characters, one heading, one line, seven items, no link, no exclamation mark, capitals written by hand. Where each claim stands (the lead checks every line against what shipped at slice F and cuts a line whose feature was cut; the page must never promise the weather: it is dark in this wave, SPLIT 7):

| Line | Depends on | If cut |
|---|---|---|
| the sky | 1a SKY and SCENE (core) | the line goes |
| the fire | 1a FIRE and HOME `FireSwitch` (core) | the line goes |
| Pêt looks over a chat | BARS places `PetPeek` and APPEAR-B composes its row (both core) | the line goes |
| five tabs and the gear | SHELL and BARS | never cut (never-cut list) |
| the Library | LIBRARY | never cut |
| the first start | WELCOME | cut with the Welcome |
| ten languages and the mark | LANG; the mark is `reviewed == false` in `LangTable` | the line goes |

The nine other pages (`0.3.es.md`, `fr`, `de`, `pt`, `it`, `tr`, `ru`, `id`, `ku`) are written by LANG's stage 4 from this text and its glossaries (request R-LANG): same structure (one heading, one line, seven items), `Agirî` and `Pêt` kept, at most 1,200 characters each, the user addressed as each glossary says.

### 3.10 Texts: `R/values/strings_start.xml`, prefix `wn_` (English only; LANG translates; each key carries a note)

| Key | English | Note (`t:` what it says, where, placeholders, room; `kind`) |
|---|---|---|
| `wn_note` | Agirî %1$s is ready. Here is what changed. | Pêt note posted once after an update; `%1$s` is the version such as 0.3; Agirî is the app name, keep it; at most 80 characters, two lines; kind pet |
| `wn_note_action` | See what's new | the action of that note; opens the page; up to 22 characters; kind button |
| `wn_title` | What's new | title of the page's top bar and the settings row's name; up to 28 characters; kind title |
| `wn_close` | Got it | button at the end of the page and in the empty state; closes the page; up to 22 characters; kind button |
| `wn_retry` | Try again | button in the error state; reads the page again; up to 22 characters; kind button |
| `wn_empty_title` | No page for this version | title of the empty state; up to 28 characters; kind title |
| `wn_empty_body` | This version comes without a page of changes. Nothing is wrong. | body of the empty state; 2 lines; kind body |
| `wn_error_title` | The page could not be read | title of the error state; up to 28 characters; kind title |
| `wn_error_body` | Your chats and settings are untouched. Try again. | body of the error state: what was kept and what can be done; 2 lines; kind body |
| `wn_about_row` | What's new in this version | row under Settings, About, that opens the page; used only if open point 1 is accepted; up to 40 characters; kind row |

No exclamation mark, no `%` outside the one placeholder, sentence case. The note is within the 80-character room (a placeholder counts 2). Turkish and French for the nine keys come in stage 4 with every other 1b key.

### 3.11 Debug-link extras and the pins the proof needs

| Need | Provided by | Form |
|---|---|---|
| Open the reader | SHELL (`route whatsnew`) | the route key is `whatsnew` |
| Post the note now, in memory | START + SHELL | `--es whatsnew pending` |
| The empty and error states | START + SHELL | `--es whatsnew missing`, `--es whatsnew broken` |
| Another language for the page | exists | `--es lang tr` (the page falls back to English until LANG writes `0.3.tr.md`; the log line shows which file was served) |
| Stretched language and mirrored layout | exists | `lang en-XZ`, `rtl 1` |
| The splash | none (it happens before any link can arrive) | `files/debug-splash.txt` on the twin, written by the lead by hand with `run-as` during S8; and the log lines of 3.5 |

## 4. Unit tests (JUnit 4, JVM; repo files with `Assume.assumeTrue(file.exists())`)

**`WhatsNewLogicTest`**
- `pageKeyTable`: `0.3.17` and `0.3.17-local2-test` and `0.3` give `0.3`; `0.2.0` gives `0.2`; `1.0.0` and `1.0` give `1.0`; `0.55.3` gives `0.55`; `0.03.1` gives `0.3`; ` 0.3.1 ` (spaces) gives `0.3`; empty, `abc`, `0`, `v0.3.1`, `0.x.1`, `12345.1.0` give null.
- `compareKeysNumeric`: `0.55` after `0.6`; `0.3` before `0.4`; `1.0` after `0.99`; equal keys give 0; `junk` below `0.1`; two junk texts equal.
- `languageCodeTable`: `pt-BR` and `pt_BR` give `pt`; `in` and `id` and `in-ID` give `id`; `iw` gives `he`; `en-XZ` gives `en`; `TR` gives `tr`; `ku` gives `ku`; empty, `x`, `1234`, `../` give empty.
- `assetNamesTable`: `("0.3","en")` and `("0.3","")` give only `whatsnew/0.3.md`; `("0.3","tr")` gives `[whatsnew/0.3.tr.md, whatsnew/0.3.md]`; `("0.3","id")` the `id` file; a key `../0.3`, `0.3.1`, `a.b` or empty gives an empty list.
- `mergeSeenNeverGoesDown`: `(null,"0.3")` gives `0.3`; `("0.3","0.2")` gives `0.3`; `("0.3","0.4")` gives `0.4`; `("junk","0.3")` gives `0.3`.
- `decideTable`: one assertion per row of 3.2 (rows 1 to 7, each `why`), including: `hold` wins over a missing page and over `seen == null`; `seen == null` with a page shows; a downgrade (`seen 0.4`, key `0.3`) shows nothing; unreadable `seen` shows.
- `showsOnceThenNever`: `decide` shows, `mergeSeen` of the shown key, `decide` again does not show; after a new key it shows once more.
- `newUserNeverSeesIt`: with `hold = true` at every start of a pretend Welcome, then `seen` = key after it, then the same version: nothing at any step; the next version shows once.

**`WhatsNewAssetsTest`** (reads `app/src/main/assets/whatsnew/*.md`; Assume the folder exists)
- `fileNamesAreKeysAndCodes`: every name matches `<digits>.<digits>.md` or `<digits>.<digits>.<a-z two or three>.md`; every code is one of `es fr de pt it tr ru id ku` (the ten of LANG minus English; a second test of LANG's `LangTable` is added by LANG).
- `englishExistsForEveryKey`: every key that has a language file has `<key>.md`.
- `pagesAreValid`: UTF-8 without BOM, LF only, at most `WhatsNewLogic.MAX_PAGE_CHARS` characters; the first line starts with `# `; `parseMarkdown` gives a `Heading` first and at least 3 `Item`s; no `<` followed by a letter or `/` (no HTML); no `![` (no image); every link is `https://`; no exclamation mark; no line made only of capitals (no heading made by shouting).
- `structureMatchesEnglish`: each language page has the same number of headings, paragraphs and items as `<key>.md` (a translator cannot drop a line).
- `currentVersionHasAPage`: reads `baseVersionName` from `app/build.gradle.kts` (Assume it matches `"0.<n>.`) and asserts `whatsnew/0.<n>.md` exists, so a wave cannot ship without its page (plan 6 step 9). It starts to matter when INTEGRATE-B sets `0.3`.

**`FireAnchorTest`**: `publishIgnoresBadNumbers` (NaN, infinite, negative x or y, size 0 or negative give no target); `publishStoresAndEqualIsNoop`; `closedAnchorIgnoresPublish`; `openResetsEverything` (target null, `fireExpectedNow` true); `closeDropsTarget`; `expectFireToggles`.

**`SplashPlanTest`**: `holdEndOrder` (target wins over cap; `!fireExpected` gives `NO_FIRE`; at 499 ms nothing, at 500 `CAP`); `exitKindTable` (OFF gives SNAP whatever else; REDUCED gives FADE; FULL with `handoffOn`, an icon and a target gives HANDOFF; without any one of the three gives FADE); `handoffMath` (icon centre (540, 1200), height 864, target (700, 900, 90): `dx` 160, `dy` -300, `scale` 90 / (864 * 0.40) = 0.2604; a target larger than the icon clamps the scale to 1; height 0 gives null); `parseForced` (`handoff`, ` FADE ` and `Snap` parse, `x` and null give null); `constantsInBudget` (`HOLD_MAX_MS` below 700, `HANDOFF_MS` equals `Dur.SCENE`, `FADE_MS` equals `Dur.FAST`, `FLAME_FRACTION` between 0.2 and 0.6).

**`SplashThemeFilesTest`** (Assume the three files exist): the base `themes.xml` has `windowBackground`, `colorBackground` and both bar colours equal to the `values-v31` file's; the v31 file's `windowSplashScreenBackground` equals its `windowBackground`; the base file has no `windowSplashScreen` item; if the v31 file names `@drawable/ic_splash_flame` then `R/drawable/ic_splash_flame.xml` exists and starts with a `<vector` element with `android:viewportWidth` equal to `android:viewportHeight`.

## 5. How it is proven

Categories as in SPLIT 10: **P** owner-debug build (the owner's package) with pins and pictures only (`"safe": true`), **T** the twin only, **U** unit test, **E** the owner's eyes. S8 needs the twin and the phone; the phone is away until the owner says so (`PROGRESS.md`), and nothing experimental goes on the owner's package.

### 5.1 What's new

| # | Check | Cat | How |
|---|---|---|---|
| 1 | The rule (the table of 3.2) | U | `WhatsNewLogicTest` |
| 2 | The page is valid in every language | U | `WhatsNewAssetsTest` |
| 3 | The note, its action and the page, in memory | P | link `route home --es whatsnew pending`; wait 2.5 s; nodes `petnote` and `petnote.action` present; picture `whatsnew-note` (dark and light default themes); tap `petnote.action`; nodes `screen.whatsnew` and `whatsnew.page` present; the page text contains "Agirî 0.3"; `app.log` has `WhatsNew: decide ... -> show (EXISTING_FIRST)` and `WhatsNew: page key=0.3 lang=en file=whatsnew/0.3.md`; no `whatsnew.xml` written (read-only `run-as ls shared_prefs`) |
| 4 | The language fallback | P | `--es lang tr --es route whatsnew`: the log names the file served (the English one until LANG's page exists, then `0.3.tr.md`); picture; then `lang en-XZ` and `rtl 1` pictures; at font scale 2.0 (the lead's `-AllowSystemSettings`) the page scrolls and the Close button is reachable |
| 5 | Empty and error states | P | `--es whatsnew missing --es route whatsnew`: node `whatsnew.empty`, picture; `--es whatsnew broken --es route whatsnew`: node `whatsnew.error`, tap `whatsnew.retry`, node `whatsnew.page`; pictures |
| 6 | A real update shows once, a restart never again | T | on the twin: app stopped, `run-as <twin> sh -c "printf '%s' '<?xml version=...?><map><string name=\"seen\">0.2</string></map>' > shared_prefs/whatsnew.xml"`; start: the note appears within 3 s; `whatsnew.xml` now has `seen` 0.3 (read through `run-as cat`); force-stop and start again: no note, the log says `ALREADY_SEEN` |
| 7 | A new user never sees it | T | `pm clear` of the twin (the Linux image unpacks again, about a minute): once WELCOME is merged, the Welcome shows, no note; after finishing or skipping it `whatsnew.xml` has `seen` 0.3 and no note appears; the next start none. **Until WELCOME is merged** a cleared twin starts on Home with no mark and shows the note once (the rule's row 4): this is expected in the interim and is not a defect |
| 8 | The owner's existing installation | P | on the first start of the 1b build over the owner's data (owner-debug): the note appears once; read-only afterwards: `run-as dev.pocketagent.app cat shared_prefs/whatsnew.xml` has `seen` 0.3 and nothing else new from START; second start: no note; `dist\owner-backup\` compared read only: old data unchanged |
| 9 | No new permission, component or manifest line | U | the manifest diff of the slice is empty |

Scenario files (the lead writes them after INTEGRATE-B, with the `safeNames` edit): `w1b-whatsnew-pins` (rows 3 to 5, `"safe": true`: the pins are memory only and the taps only navigate) and `w1b-whatsnew` (rows 6 and 7, twin only: they write a preference and clear the app).

### 5.2 The splash (experiment S8, the twin)

One hour at most (plan rule 2). Build the twin with the 1b theme and `ic_splash_flame`, install it over the installed twin (an update, so Play Protect does not block it), then:

| # | Question | How | Passes when |
|---|---|---|---|
| a | Does the flame show, from the launcher and from `am start`? | force-stop; start from the launcher; `am start -W -n <twin>/dev.pocketagent.app.MainActivity`; `adb shell screenrecord` for 4 s; watch the frames | the flame is on the plum background; the system's own exit plays when no listener is set (set `files/debug-splash.txt` empty) |
| b | Does the listener receive the view? | `app.log` `Splash: exit ...` line: view class, `icon=<w>x<h>` not null | the line exists with a non-zero icon size |
| c | Each of the six launcher aliases | enable each alias in Appearance on the twin (a twin may change its own icon) and start it by its component (`.LauncherPet` and the others of the manifest) | the splash shows the theme's flame, not the alias's own icon (eyes, one frame each) |
| d | Is the hold needed and cheap? | `phone-perf -Scenario P6` (7 cold starts) with `HOLD_ENABLED` true and false; the log's `hold released after <n> ms (<why>)` | median cold start to Home's first frame under 700 ms with the hold; `n` at most 500 ms; reason `TARGET` on a Home start; `NO_FIRE` when a link opens another first screen |
| e | Does the flight land on the fire? | `files/debug-splash.txt` = `handoff`; start; the log's `dx dy scale` and the `FireAnchor` target; screenrecord frames at 0, 160, 320, 480 ms | the flame ends within one fire-width of the scene's fire when the scene's fire has risen (the 900 ms entrance), at 1 point of the flight at most one frame over 16.7 ms (`dumpsys gfxinfo` framestats read once per start, `IntendedVsync` and `FrameCompleted`) |
| f | Reduced motion | the lead's `Set-PaSystemScales` to animator scale 0, then start; battery saver | exit kind `SNAP` (scale 0) or `FADE` (REDUCED); no flight |
| g | Safety | start with Hermes stopped, running, and with a link `route chat:<id>` on a cold start | the splash is always gone within `WATCHDOG_MS`; no crash in `app.log`; a hot start shows no splash at all |
| h | Light theme, day sky | `appearance theme:nergiz,dark:light` pin is not available before the splash; set the theme in Appearance on the twin and start in daylight | the plum-to-sky change is judged by eyes (E) |

**The verdict goes into `AGENTS.md` "Hard-won facts" and flips `SplashPlan.HANDOFF_SHIPS` (the lead).** If (b) fails (no view received) or (d) or (e) fails: the fade ships, the code of the flight stays behind `HANDOFF_SHIPS = false`. If the flame does not show at all at target 28 (a): the theme item is removed, the system shows the launcher icon, and the rest (the hold, What's new) is unaffected. Frames for the pictures: the PC may have no tool to cut a screen recording into pictures (open point 3).

**Only the owner's eyes can judge:** whether the flame in the splash is right (taste, size, whether it reads as the fire), whether the flight feels like the fire arriving or like a gimmick, the jump from plum to a day sky, and the wording of the note and the page in English (and Turkish when the owner reads that page).

## 6. Core and extras, and the order of cutting (first cut first)

Core: `WhatsNewLogic`, `WhatsNewStore`, `WhatsNewPages`, the Host, the reader with its five states, the English page, the nine keys, the theme with the flame icon, the hold with its cap, `FireAnchor`, the plain fade. Extras (SPLIT 11): the flight, `WhatsNewDebug`. Order:

1. the Pêt above the page in the reader (3.7);
2. the extra pins `missing` and `broken` (the `pending` pin stays);
3. the flight (`HANDOFF_SHIPS` stays false; `FireAnchor` stays, BARS's call becomes a no-op in effect);
4. the hold (`HOLD_ENABLED = false`; the system drops the splash at the first draw);
5. `WhatsNewDebug` altogether (the robot then writes the mark by `run-as` on the twin; the symbols stay as empty bodies: `pin` does nothing, `parse` returns null).

**Never cut:** `WhatsNewLogic.decide` and its tests, the rule that a new user and a user in the Welcome never see the note, the page file, the reader, the theme (the base window colour), the watchdog (a splash that cannot leave is the worst failure of this area). A cut item keeps its symbol with an empty body. Slices: **A** (pure, no 1a): `WhatsNewLogic`, `FireAnchor`, `SplashPlan`, the English page, the four pure tests and `WhatsNewAssetsTest`; **D** (needs slice C for the route and ART-B's drawable): the store and pages, the Host, the reader, the debug pin, `SplashHandoff`, `MainActivity` and theme edits, the strings; S8 on the twin decides the last switch.

## 7. REQUESTS to other areas (exact), and what START provides

**To SHELL** (`Nav.kt`, `nav/*`, `AppRoot.kt`, `DebugLinks.kt`; slice D, pass 2):
- R-SHELL-1: `Route.WhatsNew` (key `whatsnew`, `showsBar()` false, a detail route: `tabIndex` of Home, `rank` 1), `NavTags.screen(Route.WhatsNew) = "screen.whatsnew"`, `RouteContent`: `Route.WhatsNew -> WhatsNewScreen()`.
- R-SHELL-2: in `AppShell`, directly before the `PetNoteHost(` call: `WhatsNewHost(hold = nav.entries.any { it.route == Route.Welcome }, onOpen = { nav.go(Route.WhatsNew) })`. This differs from the SPLIT's `WhatsNewHost()`: the Host takes the two values so it needs neither `Nav.kt` nor `Route`. It must be inside `CompositionLocalProvider(LocalNavigator ...)` and inside `PocketTheme` (it uses `LocalContext`).
- R-SHELL-3: in `AppShell`, after `val route = nav.current`: `SideEffect { FireAnchor.expectFire(nav.current == Route.Home) }` (import `app.ui.pixel.FireAnchor`), so the splash does not wait for a fire when the first screen is a Welcome or a link.
- R-SHELL-4: `DebugLinks`: extra `whatsnew` (`EXTRA_WHATSNEW = "whatsnew"`, in the read list), a `DebugRequest` field `whatsnew: Setting<WhatsNewPin> = Setting.Keep` appended after `wood`; `parseParts`: `off` gives `Setting.Clear`, otherwise `WhatsNewDebug.parse(v)?.let { Setting.Set(it) } ?: rejected.add(EXTRA_WHATSNEW)`; `apply`: Clear calls `WhatsNewDebug.pin(null)`, Set calls `WhatsNewDebug.pin(p)`, after `wood` and before `lang`; `reset=1` calls `WhatsNewDebug.clear()`; route `whatsnew` in `stackFor` gives `[Home, WhatsNew]`. `DebugLinksTest` lines: `whatsnewExtraParses`, `whatsnewBadValueRejected`, `resetClearsWhatsNew`, `releaseBuildIgnoresWhatsNew`.
- R-SHELL-5: the initial stack of a cold start is built before the first composition (SHELL's `FirstRun.initialRoutes`), so `expectFire` sees the true first screen.

**To BARS** (`ui/home/*`, 1a files; slice C or D): R-BARS-1: where the hero builds the fire's hit box (the node tagged `FireTags.HIT`, `home.fire`), add `Modifier.onGloballyPositioned { c -> val r = c.boundsInWindow(); FireAnchor.publish(r.center.x, r.center.y, r.height) }` on a node whose bounds are the flame's **own** box (`FireGeometry.hitRect(grid, minPx = 0)`: 24 by 30 logical rows times the scale, **without** the 48 dp touch growth), at the fire's **final** place (the hit box is built from the final geometry, `reveal = 1`), and only while the hero's collapse reading is 0 (a scrolled hero need not publish; `FireAnchor` closes itself after the splash, so a call is cheap and never harmful). One call site, no state, no scrolling hook. If BARS prefers to publish from the scene's `Spacer` (tag `stage.hearth`) plus `hitRect`'s numbers, the result must be the same window rectangle. Import `app.ui.pixel.FireAnchor`.

**To WELCOME-CORE** (`FirstRun.kt`): R-WELCOME: call `WhatsNewStore.markCurrentSeen(context)` inside `FirstRun.markDone(...)` and `FirstRun.markSkipped(...)`, before the shell leaves the Welcome. (A user who ends the Welcome never sees the note for this version.) `FirstRunRulesTest` may assert that a fake store was called; START's rule needs nothing else from the Welcome.

**To ART-B** (`R/drawable/ic_splash_flame.xml`): R-ART: a vector drawable with `android:viewportWidth` equal to `android:viewportHeight` (288 is natural: the splash icon area is 288 dp); transparent background; the flame in its pixel proportions (the fire's 24 by 30 box, so the width is 0.8 of the height), **centred in the drawable**, its height **0.40 of the viewport** (`SplashPlan.FLAME_FRACTION`; if ART-B must draw another fraction it tells START and the constant and its test change, nothing else), everything inside the central circle of 0.667 of the width (the system masks to it); colours from the fire sprite's palette; one `<path>` per colour, each path under 9 KB (the Android limit noted in `AGENTS.md`). Review on LOOK-B's contact sheet on the plum `#1D1220`.

**To LANG** (stage 4): R-LANG: (1) the nine keys of 3.10 into every language of the ten, with their notes; `wn_about_row` only if open point 1 is accepted; (2) the nine pages `A/whatsnew/0.3.<code>.md` for `es fr de pt it tr ru id ku` (the code in a file name is the stored tag: `id`, never `in`; `ku`), by the structure and limits of 3.9, from the English text and the glossaries; (3) `WhatsNewAssetsTest` is the check; (4) the languages' own `LangTable` test that every non-English code has a page is LANG's.

**To INTEGRATE-B:** `ScenarioFilesTest` known tags: `whatsnew.page`, `whatsnew.skeleton`, `whatsnew.empty`, `whatsnew.error`, `whatsnew.retry`, `whatsnew.close` (and `screen.whatsnew` from SHELL); `baseVersionName` 0.3 in slice F (the page key follows it, `currentVersionHasAPage` then bites); the two baselines for the new files only (they start at 0; no rise); `tools/size-budget.txt` (the page, the vector and the code are under 20 KB; the nine pages under 12 KB more).

**To the lead:** (1) writes `files/debug-splash.txt` by hand during S8 and flips `SplashPlan.HANDOFF_SHIPS`; (2) the scenarios of 5.1; (3) records in `AGENTS.md` the S8 result and the rule "What's new marks a page seen when the note is posted"; (4) checks each line of the page against what shipped (3.9); (5) open point 1.

**START provides:** `FireAnchor.publish` and `FireAnchor.expectFire` (BARS, SHELL); `WhatsNewHost(hold, onOpen)`, `WhatsNewScreen()`, `WhatsNewTags`, `WhatsNewDebug.parse/pin/clear` (SHELL); `WhatsNewStore.markCurrentSeen(context)` (WELCOME); `WhatsNewLogic.languageCode` and the page file rules (LANG); the nine `wn_` keys (LANG). It calls, and changes nothing in: `PetNotes.show`, `MarkdownText`, `parseMarkdown`, `PocketTopBar`, `EmptyState`, `Skeleton`, `PixelButton`, `Texts.get`, `resolveAppLocale`, `LocalAppLanguage`, `effectiveAnimLevel`, `AppearanceStore`, `AppLog`, `Dur`, `Space`, `Type`.

## 8. Risks

1. **The hold can lengthen the cold start.** It is capped at `HOLD_MAX_MS` (500 ms from `onCreate`) and is measured by P6 against the 700 ms budget; `HOLD_ENABLED` is the switch. A Home that is slow to compose costs the full cap on every start.
2. **A splash that never leaves.** Setting an exit listener makes the app responsible for `remove()`. Mitigations: `try`/`catch` with `remove()`, the watchdog at 1,500 ms, `Forced.SNAP`, and S8 row g. This is the one defect that would cover the whole app, which is why the watchdog is never cut.
3. **The flame lands before the scene's fire does.** The entrance (900 ms) lifts the scene's fire while the flame flies for 480 ms; at the landing the fire is within roughly a quarter of its travel of the target. If it looks wrong on the phone: lengthen `HANDOFF_MS` to `Dur.EPIC` (900), or ask HOME-HERO to start the entrance when the splash is gone (a 1a file; a request for the lead), or ship the fade. Owner's eyes decide.
4. **The platform may not call the listener at target 28, or may substitute the launcher icon** (S8 a, b, c). Fallbacks are in 5.2; nothing else depends on it.
5. **Plum to sky.** The splash background is the old window plum in every theme and hour; with 1a's real sky a bright day makes a hard change at the end of the fade. A per-hour colour cannot be read before the first frame. Accepted for 0.3; the alternative (a neutral colour) is a later taste decision.
6. **The mark is written when the note is posted.** A note that the user does not see (the phone is put down) is spent. Reopening the page needs the row of open point 1; without it the page can only be reached by the debug link.
7. **A missing or badly translated page** falls back to English; a wrong claim in a page is a writing mistake no test catches except the structure check: the lead's check of 3.9 at slice F is the guard.
8. **Screen readers do not hear the page's headings as headings**: `MarkdownView.kt` carries no semantics for them. It is not a START file and nobody owns it in 1b; a follow-up for the wave that next edits chat Markdown. The top bar title is read as the heading.
9. **`FireAnchor` coordinates assume the app window is the whole screen.** On a phone with a display cutout or in split screen the splash's own view and the window can differ; a start in split screen simply gets a worse landing, never a failure (the handoff is a fade-out of an overlay).
10. **API differences between One UI builds** (the exit listener's view tree: `iconView` can be null, the splash can carry a branding image). Every access is null-checked; a null `iconView` gives `Exit.FADE`.
11. **Time of the note against other overlays.** A crash-report offer or a share dialog may be open when the note is posted; the note queue pauses its timer while a sheet or a dialog is open (`overlayOpened`), so it is not lost, only late.
12. **Pre-draw hold and `onGloballyPositioned`.** The plan relies on layout callbacks running while drawing is held. If S8 d shows the target arrives only after the first draw, `holdEnd` returns `CAP` every time and the hold only costs time: then `HOLD_ENABLED` is set false and the flight uses what is published at the exit.
13. **Shared package.** `FireAnchor.kt` and `SplashPlan.kt` sit in the shared package `app.ui.pixel`: every helper is private to its file or a member of its own object; no top-level function is added (1a contract 1.1).

## 9. Open points for the lead (each has a default so the area can be built)

1. **A way to read the page again.** The note is posted once; afterwards the page is reachable only through the debug link. Proposal: one row "What's new in this version" (`wn_about_row`, tag `settings.whatsnew`) under Settings, About, that calls `nav.go(Route.WhatsNew)`; About is in `ui/settings/RuntimeSection.kt` (line 211), which no 1b area owns. Default: not built; the lead names the owner (INTEGRATE-B or SHELL), about five lines. Without it the "mark when posted" rule loses pages the user did not catch.
2. **`HANDOFF_SHIPS`.** Default false; the lead flips it to true when S8 rows b, d, e pass on the twin.
3. **Cutting a screen recording into pictures.** S8 e and the LOOK of the splash want frames; the PC has no frame tool that is known to exist. Default: the numbers (log lines, `gfxinfo`, P6) are the proof and the lead looks at the recording itself; if a tool is installed, the frames go to `dist\`.
4. **The page's claims at slice F.** Default: START writes 3.9 as above and the lead cuts any line whose feature was cut (the table lists the provider of each line).
