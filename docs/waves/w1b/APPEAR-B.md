# Wave 1b, area APPEAR-B: contract draft

Package `dev.pocketagent.app` is written `app`. `J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`. Today's line numbers were read in the work branch's tree (HEAD `32cf233` plus the uncommitted Wave 0 defect fixes). **Everything this area takes from Wave 1a is taken from `docs/waves/w1a-contract.md` (registry 3.4, 3.5, 3.7, 3.9, amendments 5.8, rulings R4 and R12, open point 10) and not from today's code.** Where the text says "1a's slice A" it was also read in the 1a worktree `dist/wt-w1` (branch `w1a`: `ui/theme/AppearancePin.kt`, `BlackNight.kt`, `voice/SceneSounds.kt`, and `Appearance.kt` with the seven new fields); those files are not in the work branch yet, and the 1b contract is re-read against 1a's final text before the first 1b agent starts. Sources: `docs/phase4-plan.md` 1.5, 1.6 and 1.9 (lines 831, 841, 882 to 884), 7.1 "two halves" (888 to 889), section 13 (decisions 2 and 19 only: both assumed as in SPLIT section 12); `docs/design-language.md` 2.3 (line 122, wallpaper accent; the contrast rules just above it), 4 (haptics, lines 198 to 230), 5 (sound), 7 (Pêt outside Home, 405 to 407), pattern 7 and pattern 8, 8.15 (Settings is a gear), 8.16 (six kinds, five states), 13.2 (rooms), 13.5 (no case change in code); `docs/waves/w1b/SPLIT.md` 3, 4.8, 5, 6, 8 (E-B3), 9, 10, 11, 13; `docs/waves/w1/APPEAR.md` (the 1a draft, same shape); `docs/waves/w0-contract.md` 3.4 to 3.6, 3.12.

## 1. What the plan and the design language ask

| # | Item (SPLIT id) | Where | Kind |
|---|---|---|---|
| P1 | **Wallpaper accent** (B22): "one more `AccentId` that takes the system accent colour", Android 12 and later; stored by name under the existing `accent` key so the previous build ignores it; a contrast guard keeps text readable in every theme; `PaletteContrastTest` must not get worse | plan l.882 to 883; DL 2.3 l.122; SPLIT 4.8 | core |
| P2 | **Pêt in chats** (B23, the row only): the switch that 1a wrote as a private composable and did not compose; BARS places the small Pêt over the chat top bar and reads `Appearance.petInChatsVisible` | plan l.831, l.883; DL 7 l.405 to 407; 1a 3.7, A6, open point 7 | core |
| P3 | **The old language rows go** from `AppearanceSection.kt` and `SettingsScreen.kt`, in the same commit as LANG's new `ui/settings/LanguageSection.kt` (a duplicate would not compile) | SPLIT 3 last row, 4.6, 4.8 | core |
| P4 | **Settings as a pushed screen** (B7, screen part): the back arrow is always there now that Settings leaves the tab bar; SHELL owns the route, BARS the gear | plan l.841; DL 8.1 l.455 to 456, 8.15 l.741 | core |
| P5 | **Crackle volume follows the fire** (B27): the quiet crackle loop gets louder while the agent works, from `FireFeed.level()` | 1a ruling F6, registry 3.4 ("via SceneSounds in 1b") | extra, first to be cut |

Binding rules that apply: DL 2.3 (contrast is tested, not hoped for: body text 4.5 to 1, outlines and components 3 to 1, a failing pair fails the build), principle 12 (English first, new names are plain English words: the row is called "Wallpaper colour"), principle 13 (spoken labels, text at double size, mirrored), DL 4 (a selection is `TICK`), DL 8.16 (every screen is one of six kinds with five states: both screens here are the Settings kind of List and state it in section 3.9), DL 13.5 (no capital made in code). Plan decisions 2 (test twin) and 19 (a wave may ship without its extras) are assumed as SPLIT 12 says; **no decision of section 13 is specific to this area**.

## 2. What exists today, and what 1a will have changed by then

**Today (work branch):**
- `J/ui/theme/Appearance.kt`: `enum class AccentId { THEME, FLAME, SAFFRON, MEADOW, SKY, ROSE, NEWT, SNOW }` (line 13); the store reads an enum by name through `parse` (lines 89 to 90): **an unknown name gives the default**, which is what makes the stored value `WALLPAPER` harmless to the previous build. `K_ACCENT = "accent"` (line 95) is written by `update` (line 54).
- `J/ui/theme/Palettes.kt:34`: `fun paletteFor(theme: ThemeId, dark: Boolean, accent: AccentId): PocketColors`; lines 43 to 52 a `when (accent)` over the eight fixed accents (`AccentId.THEME -> return base`); lines 53 to 56 copy five fields from a private `AccentSet`: `accent`, `accentSoft`, `onAccent`, `userBubble`, `onUserBubble`. The 12 palettes and 16 accent sets are private in `object Palettes`.
- `J/ui/theme/PocketTheme.kt:66 to 68`: `val colors = remember(appearance.theme, dark, appearance.accent) { paletteFor(...) }`.
- `J/ui/settings/AppearanceSection.kt`: accent grid at lines 80 to 100 (`AccentId.entries.chunked(4)`, a 44 dp swatch per accent, `selectable(role = Role.RadioButton)`, no tag, no haptic, the swatch is `paletteFor(ap.theme, dark, a).accent`); `// ---- dark mode ----` at 102; the mascot switch at 141 (`ctl_ap_mascot`, subtitle `ctl_ap_mascot_sub`: "Pêt lives on the home screen and in empty places."); `accentLabel` (206 to 215, an exhaustive `when`); `ThemeCard(id, dark, accent, selected, modifier, onClick)` (240 to 270) paints a preview with `paletteFor(id, dark, accent)`; `LanguageSection()` (272 to 285: System, English, Türkçe, Français).
- `J/ui/settings/SettingsScreen.kt:63`: `fun SettingsScreen(section: String?)`; **line 79: `onBack = if (section != null) ({ nav.back(); Unit }) else null`**, so the arrow exists only when a section was asked for, because today Settings is the root of a tab; line 114 `Section.LANGUAGE -> LanguageSection()`; lines 174 to 177 the summary `languageName(ap.language)`; lines 184 to 190 `internal fun languageName(code)` (English, Türkçe, Français, else `ctl_lang_system`).
- `J/ui/AppRoot.kt`: the five tabs at 204 to 210 (Settings is the fifth, `NavTags.TAB_SETTINGS`); line 249 the back handler is `if (!nav.back()) nav.tab(Route.Home)`; line 263: a route that shows no bar gets `windowInsetsPadding(bottomSafeInsets())`, so a pushed Settings needs no padding of its own; lines 348 to 349 `AppLink.Usage` goes `tab(Settings())` then `go(Settings("usage"))`. Seven screens already push `Route.Settings("<section>")` with `nav.go` (Home 229, 245, 371; Hermes 160; Terminal 136; ChatInputArea 202), so a Settings entry on top of a screen already exists and already goes back with `nav.back()`.
- `T/ui/theme/PaletteContrastTest.kt:260`: `for (accent in AccentId.entries)` over 6 themes x 2 variants, with the 215 known failures as a ratchet (ids like `KAWA/dark/FLAME:outline-on-bg`). **Adding an enum value without touching this loop would add failing ids for `WALLPAPER` (the same outline and accent failures that `THEME` has) that are not in the list, and fail the test.**
- Nothing reads `AccentId` outside those files (searched `app/src`).

**What 1a will have changed (contract 3.7, 5.8; slice A read in `dist/wt-w1`):** `Appearance` has seven more fields (`sky`, `blackNight`, `classicFire`, `petInChats`, `rainSound`, `crackle`, `tiltFire`) and `val Appearance.petInChatsVisible = showMascot && petInChats`; `AppearanceStore` keeps a `stored` flow and an `effective` flow, `pin(p: AppearancePin?)` (a no-op unless `BuildConfig.TEST_HOOKS`; memory only) and `isPinned`; `AppearancePin(val change: (Appearance) -> Appearance)` with `parse(text)` over `AppearancePinGrammar` and `AppearancePinItems.applyTo` (items `black classic pet tilt rain crackle haptics theme dark anim`; **no `accent` item and no `mascot` item**); `PocketColors.withBlackNight(on)`; `PocketTheme` builds `colors` as `paletteFor(...).withBlackNight(appearance.blackNight && dark)`; `AppearanceSection.kt` has the rows of 1a's `AppearanceScene.kt` inserted at the anchors of APPEAR 3.5, `ThemeCard` knows `blackNight`, the Pêt row exists as a private composable that nobody composes (R15 and 3.7: "NOT composed in this half"); `voice/SceneSounds.kt` has `SceneSounds`, `LoopMath` (`rainLevel`, `crackleLevel(blazing, enabled)` = flat 0.25f, `fade`), `HearthCues`, `SoundFiles`, `SoundLoop`; `FireFeed.level()` exists in `service/FireFeed.kt` (pure on the state and the clock, polled, never pushed); `LanguageSection` and `languageName` are untouched by 1a (APPEAR.md line 35). The tags `appearance.pet` and `AppearanceTags.PET` are 1a's.

## 3. The design

### 3.1 `J/ui/theme/Appearance.kt` (1a file; APPEAR-B is its only 1b editor)

```kotlin
enum class AccentId {
    THEME, FLAME, SAFFRON, MEADOW, SKY, ROSE, NEWT, SNOW,
    WALLPAPER;                                            // appended LAST: the eight old names and their order are unchanged
    companion object { val FIXED: List<AccentId> = entries.filter { it != WALLPAPER } }   // the eight accents that have a fixed palette (the grid, the contrast test)
}
/** What the screens draw: WALLPAPER on a phone that cannot supply the colour is the theme's own accent. The stored value is never touched. */
fun Appearance.effectiveAccent(wallpaperAvailable: Boolean): AccentId =
    if (accent == AccentId.WALLPAPER && !wallpaperAvailable) AccentId.THEME else accent
```
Two edits in `AppearanceStore` (1a's text): in `pin(p)`, directly after `pinned = p` and before `publish()`, the line `SystemAccent.setPinned(p?.systemAccent)` (it is inside the `TEST_HOOKS` guard 1a already has). No new key, no new field of `Appearance`, no change in `update`, `load` or `sanitize`: the stored name comes and goes through `K_ACCENT` as every accent does.

### 3.2 `J/ui/theme/SystemAccent.kt` (new; the pure parts need no Android class, so slice A can build them)

```kotlin
@Immutable class SystemAccentTones private constructor(private val argb: IntArray) {   // the first system accent colour (Android's "accent1") as a ramp, ARGB ints
    fun at(stop: Int): Color                    // stop must be one of STOPS, else IllegalArgumentException
    val isUsable: Boolean                       // every alpha 0xFF; luminance never rises along STOPS (slack 0.0001); luminance(at(0)) - luminance(at(1000)) >= 0.5; at(500) strictly between the ends
    fun toArgbList(): List<Int>
    override fun equals(other: Any?): Boolean   // by content, so it can be a remember key
    override fun hashCode(): Int
    override fun toString(): String             // "SystemAccentTones(500=#RRGGBB)" (the debug line; never the whole ramp)
    companion object {
        val STOPS: List<Int> = listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)   // the 13 stops of android.R.color.system_accent1_<stop>
        fun of(argb: IntArray): SystemAccentTones?          // null unless exactly 13 values
        fun fromSeed(rgb: Int): SystemAccentTones           // a ramp in the seed's hue and saturation (HSL), lightness (100 - stop / 10) per cent; stop 0 exactly white, stop 1000 exactly black. FOR TESTS AND THE DEBUG PIN ONLY: the phone's own ramp is used in production
    }
}
internal object AccentContrast { fun luminance(c: Color): Double; fun ratio(a: Color, b: Color): Double }   // WCAG 2.1, gamma 2.4; reference: black on white 21.0
@Immutable class AccentRoles(val accent: Color, val soft: Color, val onAccent: Color, val userBubble: Color, val onUserBubble: Color)
object AccentGuard {
    const val TEXT = 4.5; const val COMPONENT = 3.0           // the two numbers of PaletteContrastTest
    fun roles(base: PocketColors, tones: SystemAccentTones): AccentRoles?   // null when some role has no tone that passes
    fun apply(base: PocketColors, tones: SystemAccentTones?): PocketColors  // base when tones is null or not usable, when roles is null, or when acceptable(result) is false
    fun acceptable(c: PocketColors): Boolean
}
object SystemAccent {
    const val MIN_SDK = 31
    fun supported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK                  // pure; the app passes Build.VERSION.SDK_INT
    fun read(context: Context): SystemAccentTones?                           // null below API 31, on any exception (Resources.NotFoundException and the like) and when !isUsable; one getColor per stop; one line "Theme: system accent 500=#RRGGBB" in AppLog when it changes and BuildConfig.TEST_HOOKS
    val pinned: StateFlow<SystemAccentTones?>                               // memory only, null in a build without test hooks
    fun setPinned(t: SystemAccentTones?)                                    // no-op unless BuildConfig.TEST_HOOKS
}
@Composable fun rememberSystemAccentTones(wanted: Boolean): SystemAccentTones?   // null when !wanted; else the pin, else SystemAccent.read(LocalContext.current); remember keys: wanted, pin, context, LocalConfiguration.current
```
**The guard (the ladders are the whole design; each candidate is a stop of the ramp, tried in order, the first that passes wins; `bg`, `bgDeep`, `surface`, `surfaceHigh` are the theme's own, before black night).**

| Role | Dark variant: candidates, then test | Light variant: candidates, then test |
|---|---|---|
| `accent` (fill of buttons, switches, selected chips) | 200, 300, 100, 400, 50; ratio to `bg` and to `surface` at least `COMPONENT` | 600, 500, 700, 400; the same test |
| `onAccent` (text on the fill) | 800, 900, 1000, then 0; ratio to the chosen `accent` at least `TEXT` | 0, 1000; the same test |
| `soft` (the accent as text or icon) | 100, 50, 0, 200; the smallest ratio over `bg`, `bgDeep`, `surface`, `surfaceHigh` at least `TEXT` | 700, 800, 900, 1000; the same test |
| `userBubble` with `onUserBubble` | bubble 700, 600, 800, 500 with text 50, 0, 100 (first pair with ratio at least `TEXT`); the bubble's ratio to `bg` at least 1.5 so it can be seen | bubble 100, 200, 50 with text 900, 1000; the bubble's ratio to `bg` at least 1.1 |

Any role without a passing candidate makes `roles` null and `apply` return `base` (the theme's own accent): the colour is never shown unless it is readable. Black and white always reach about 4.58 against any colour, so `onAccent` cannot be the cause. `acceptable(c)` is the union of the accent checks of `PaletteContrastTest`: `onAccent` on `accent` at `TEXT`; `accentSoft` on `bg`, `bgDeep`, `surface`, `surfaceHigh` at `TEXT`; `accent` on `bg` and `surface` at `COMPONENT`; `onUserBubble` on `userBubble` at `TEXT`. **Black night** (1a, applied after this) only replaces the dark backgrounds by black or by a colour that was already a background of the same palette and is darker than the one it replaces, so every pair that passed still passes (1a 3.3 proof); `AccentTest` checks it again. `fireLow/Mid/High`, `ember`, `glow`, bubbles of the bot and the tools, `codeBg`, `error`, `success`, `warning`, `outline`, `text`, `textMuted` and all backgrounds are never touched: the fire stays fire.

### 3.3 `J/ui/theme/Palettes.kt` and `J/ui/theme/PocketTheme.kt`

`paletteFor(theme: ThemeId, dark: Boolean, accent: AccentId, tones: SystemAccentTones? = null): PocketColors` (the three-argument calls in `AppearanceSection.kt`, `PocketTheme.kt:37` and the contrast test still compile). In the `when (accent)`: `AccentId.WALLPAPER -> return AccentGuard.apply(base, tones)`. The doc comment above the function gets one sentence: the fixed accents are checked by the unit test, the wallpaper accent by the guard at run time. With `tones == null` the result equals `AccentId.THEME`'s.

`PocketTheme` (on top of 1a's edit of the same `remember`): 
```kotlin
val tones = rememberSystemAccentTones(appearance.accent == AccentId.WALLPAPER)
val colors = remember(appearance.theme, dark, appearance.accent, appearance.blackNight, tones) {
    paletteFor(appearance.theme, dark, appearance.accent, tones).withBlackNight(appearance.blackNight && dark)
}
```
Cost: 13 `getColor` calls and about 40 contrast ratios when the theme, the variant, the accent, black night or the ramp change; nothing per frame. `rememberSystemAccentTones` returns null at once for every other accent.

### 3.4 `J/ui/theme/AppearancePin.kt` (1a file; APPEAR-B is its only 1b editor: three more items)

`AppearancePin` gets a second constructor parameter with a default, `class AppearancePin(val change: (Appearance) -> Appearance, val systemAccent: SystemAccentTones? = null)`; `parse` passes the items' ramp. `AppearancePinItems` gets three more nullable fields at the END (`accent: AccentId?`, `mascot: Boolean?`, `wallpaper: SystemAccentTones?`) and `applyTo` gets `accent = accent ?: a.accent`, `showMascot = mascot ?: a.showMascot`. New items of the grammar (lower case; values matched in `when` tables, no case change):
- `accent:theme|flame|saffron|meadow|sky|rose|newt|snow|wallpaper`
- `mascot:0|1` (the master switch `showMascot`; the chat row's note and BARS's peek both depend on it, so a picture needs it)
- `wallpaper:<six lower-case hex digits>` (e.g. `wallpaper:e0407a`): the ramp comes from `SystemAccentTones.fromSeed`. It does not change `accent` by itself; with it a picture of the wallpaper accent is the same on every phone, and the phone's own wallpaper is never touched. Upper case, `#`, five or seven digits: the whole value is rejected, as for any bad item.

The grammar of 1a (whole value rejected on one bad item, last wins, empty gives null) is unchanged. `reset=1` calls `AppearanceStore.pin(null)` (1a 3.9), which now also clears the ramp. No new debug extra and no `DebugLinks.kt` change: the items travel through the existing `appearance` extra.

### 3.5 `J/ui/settings/AppearanceSection.kt` (edits by anchor, the quoted text, not by line)

1. **Accent grid** (`// ---- accent ----`): iterate `AccentId.FIXED.chunked(4)` instead of `AccentId.entries`; `val tones = rememberSystemAccentTones(true)`; `val wallpaperOk = tones != null`; `val chosen = ap.effectiveAccent(wallpaperOk)`; `on = chosen == a`. `accentLabel` gets the ninth branch `AccentId.WALLPAPER -> R.string.apr_wallpaper_title` (it keeps the `when` exhaustive; the grid never shows it). The existing swatches gain `HapticCue.TICK` through `rememberHaptics()` in their `selectable` lambda (DL 4: a selection is a TICK; today they have none).
2. **Wallpaper row**, directly after the grid's last `Row` and before `// ---- dark mode ----`, composed **only while `wallpaperOk`** (nothing at all, no spacer, otherwise):
   `private fun WallpaperAccentRow(ap: Appearance, dark: Boolean, tones: SystemAccentTones, store: AppearanceStore)`: a `PixelPanel(Modifier.fillMaxWidth().testTag(AppearanceBTags.WALLPAPER).selectable(selected = ap.accent == AccentId.WALLPAPER, role = Role.RadioButton) { haptics.play(HapticCue.TICK); store.update { it.copy(accent = AccentId.WALLPAPER) } }, color = if (selected) c.surfaceHigh else c.bgDeep, border = if (selected) c.accent else c.outline, corner = Corner.chip)`; inside, a `Row(padding Space.s, spacedBy Space.m)`: the swatch (a `Box` of `SWATCH_DP.dp`, `private const val SWATCH_DP = 44f`, filled with `paletteFor(ap.theme, dark, AccentId.WALLPAPER, tones).accent`, bordered as the grid's swatches are, the check icon on it when selected with tint `.onAccent` of that palette), then a `Column(Modifier.weight(1f))` with `CText(t(apr_wallpaper_title), 14.sp, SemiBold)` and `Muted(t(apr_wallpaper_sub), 12.sp)`. The swatch is the guarded result, so the picture shows what the user gets. When `ap.accent == WALLPAPER` and `AccentGuard.roles(paletteFor(ap.theme, dark, AccentId.THEME), tones) == null`, a `Muted(t(apr_wallpaper_fallback))` with tag `AppearanceBTags.WALLPAPER_NOTE` sits under the panel (the colour was too pale and the theme's own accent is in use).
3. **Pêt in chats row**, directly after the mascot `PocketSwitchRow` (the line with `ctl_ap_mascot_sub`) and before the pixel-font switch: `private fun PetPeekSwitchRow(ap: Appearance, store: AppearanceStore)` = `PocketSwitchRow(t(R.string.apr_pet_title), ap.petInChats, { v -> store.update { it.copy(petInChats = v) } }, subtitle = t(R.string.apr_pet_sub), modifier = Modifier.testTag(AppearanceTags.PET))`; when `!ap.showMascot` a `Muted(t(R.string.apr_pet_off_note))` (tag `AppearanceBTags.PET_NOTE`) follows it, because the master switch wins (`petInChatsVisible`). The keys `apr_pet_title` and `apr_pet_sub` are 1a's (it wrote them; they are unused until now). The name is distinct from the one 1a used for its private composable on purpose (a private and an internal function of one name in one package may clash); 1a's dead composable is deleted by INTEGRATE-B or left, but must not be composed (section 8, risk 3).
4. `ThemeCard` keeps its signature; inside it `val tones = rememberSystemAccentTones(accent == AccentId.WALLPAPER)` and `paletteFor(id, dark, accent, tones)` (1a's `.withBlackNight(...)` stays). The preview of a theme therefore shows the wallpaper accent when it is the chosen one.
5. **Delete** `LanguageSection()` (lines 272 to 285 at the work branch's tip) and its comment, **in the same commit** as LANG's `ui/settings/LanguageSection.kt`. Imports that become unused (`LocalContext` stays: the icon block uses it) are removed.
6. `object AppearanceBTags` at the end of the file (1a's `AppearanceTags` is not edited; "exactly these members"): `WALLPAPER = "appearance.accent.wallpaper"`, `WALLPAPER_NOTE = "appearance.accent.wallpaper.note"`, `PET_NOTE = "appearance.pet.note"`.

No `N.dp` literal, no case change, no `delay(<digits>` in any new line (1a 1.1, the lint ratchet; baselines never rise, 1a ruling 2): the swatch size is the named constant, gaps are `Space.*`, corners `Corner.chip`. Only components of Wave 0 and `CText`, `Muted` from `CtlUi.kt`.

### 3.6 `J/ui/settings/SettingsScreen.kt`

- `PocketTopBar(title = t(R.string.ctl_settings_title), onBack = { if (!nav.back()) nav.tab(Route.Home) })`: **always present**, and the same rule as the system back handler of `AppRoot.kt:249`, so a Settings entry that is alone on the stack (an old saved stack, a link that built one) leads Home instead of doing nothing. Import `dev.pocketagent.app.ui.Route`. The signature `fun SettingsScreen(section: String?)` is unchanged (SHELL's `RouteContent` call stays).
- **Delete** `internal fun languageName(code)` (lines 184 to 190) in the same commit as LANG's file, which defines `@Composable internal fun languageName(code: String): String` in the same package with the same signature, so `summaryOf` (line 176) and the `Section.LANGUAGE -> LanguageSection()` line (114) compile unchanged.
- Nothing else moves: the ten sections, their keys (`RobotTags.settingsSection`), the open-one-at-a-time rule, `LaunchedEffect(section)` that opens and scrolls to a section, the two dialogs. A pushed Settings starts with every section closed unless a section was asked for (a pushed entry gets a fresh id, so its `rememberSaveable` starts clean: that is today's behaviour on a first visit).
- Bottom inset: nothing to add, `AppRoot.kt:263` pads a route without a bar.

### 3.7 `J/voice/SceneSounds.kt` (1a file; extra B27)

In `LoopMath` (1a's object) one pure function, the old `crackleLevel(blazing, enabled)` stays as it is (1a's `crackleLevels` test pins it):
```kotlin
const val CRACKLE_STEPS = 5; const val CRACKLE_POLL_MS = 400L
fun crackleFollow(fireLevel: Float, blazing: Boolean, enabled: Boolean): Float   // 0f unless blazing && enabled; else 0.25f + 0.05f * k, k = (fireLevel.coerceIn(0f, 1f) * 5f + 0.5f).toInt(), NaN counts as 0; so 0.25 (idle fire, today's flat value) to 0.50 (a tool is running)
```
In `SceneSounds` the `crackle` value stops being `rememberUpdatedState(LoopMath.crackleLevel(...))`: a `LaunchedEffect(hearth == BLAZING, ap.crackle)` inside the STARTED scope writes a `mutableFloatStateOf` every `CRACKLE_POLL_MS` from `LoopMath.crackleFollow(FireFeed.level(), blazing, ap.crackle)` while both are true (and once with 0f when either turns false), and the existing `snapshotFlow` reads that state. `setLoop` already ignores an unchanged level, so at most five distinct targets exist and the poll costs one function call every 400 ms on Home only. Sound is not an animation: the animation level does not gate it; the engine's own gates (pack OFF, silent ringer, volume under 0.01, background) still hold. The `fire` pin (`level:0.9`) drives it, so `app.log` shows `Sound: loop CRACKLE level=0.5` without anyone listening.

### 3.8 Strings: `R/values/strings_appearance_b.xml` (prefix `apr_`; English only here, LANG stage 4 adds the other nine languages; each key carries its note)

| Key | English | Note (the `t:` and `kind:` parts of the comment above the key) |
|---|---|---|
| `apr_wallpaper_title` | Wallpaper colour | title of the row and the accent's name; the colour the phone takes from its wallpaper; up to 20 characters; kind: row |
| `apr_wallpaper_sub` | Uses the accent colour your phone takes from its wallpaper, and follows it when you change the wallpaper. | under the title; 2 lines; kind: body |
| `apr_wallpaper_fallback` | This wallpaper's colour is too pale to read on this theme, so the theme's own accent is used. | muted line shown only when the guard refused the colour; 2 lines; kind: body |
| `apr_pet_off_note` | Pêt is switched off in the row above, so Pêt stays out of chats too. | muted line under the "Pêt in chats" switch while the master switch is off; `Pêt` is the mascot's name, never translated; 2 lines; kind: body |

No `%`, no plural, no exclamation mark. Nothing else is new: `ctl_ap_*`, `apr_pet_*`, `shell_*` are others'. **Keys that this change leaves unused** (the lint ratchet counts an unused key as a rise): `ctl_lang_intro`, `ctl_lang_system`, `ctl_lang_system_sub` in `strings_control.xml` and its `values-tr` and `values-fr` copies, unless LANG's new section reuses them. That is LANG's and INTEGRATE-B's to settle (section 7); APPEAR-B does not touch `strings_control.xml`.

### 3.9 The five states, motion, haptics, sound

Both screens are the **Settings kind of List** (DL 8.16): a title bar, rows of one height, sections that open and close.

| State | Appearance section (the new rows) | Settings screen (pushed) |
|---|---|---|
| Loading | None: the ramp is 13 synchronous reads (well under a millisecond), the other rows read flows that have defaults. Nothing shimmers | None of its own. The Usage summary shows "no usage yet" for the first frame until its flow arrives (today's behaviour, unchanged) |
| Empty | Wallpaper row: **absent** (no row, no spacer) below Android 12, when the read fails or the ramp is unusable. A stored `WALLPAPER` is then drawn as the theme's own accent and the grid shows "Theme" selected (`effectiveAccent`), so no row looks empty or half selected | Not applicable: the list is fixed (ten sections) |
| Content | Row unselected (grid swatch selected) or selected (row border in the accent, check on the swatch); the Pêt row on or off; the off-note while the master switch is off | The list, one section open at most |
| Error | The guard refused the colour: `apr_wallpaper_fallback`, the theme's accent in use. Nothing can "fail" otherwise | The two existing dialogs (import result, diagnostics copy), unchanged |
| Offline | Not applicable: nothing here touches the network | Not applicable for this area; the sections say it themselves |
| Needs permission | Not applicable: the system accent colours need no permission and the manifest does not change | Not applicable for this area |

**Motion:** the colour change is instant (a theme is not animated; the whole tree recomposes once); the wallpaper row has no entry motion of its own (it is part of the section's list). Settings arrives and leaves by pattern 7, which `NavDisplay` plays for a route of rank 1; after the back swipe on this phone (S1: no progress is reported) the pop plays after the gesture, as for every screen. **Haptics:** `HapticCue.TICK` on a swatch, on the wallpaper row (through `rememberHaptics()`, which already follows the level setting) and, as today, on the switch rows (`PocketSwitchRow` plays it itself). **Sound:** none for these rows, as for the other rows of this screen (`SoundCue.TAP` stays on the icon picker only). The crackle follows the fire (3.7) and is silent for every gate the engine has.

### 3.10 Test tags (exact; one tag per node)

`appearance.accent.wallpaper` (the row; its `selected` state is readable in a `uiautomator dump`), `appearance.accent.wallpaper.note`, `appearance.pet` (1a's `AppearanceTags.PET`, composed from this wave on: **exactly one node**), `appearance.pet.note`, and the existing `top.back`, `top.title`, `screen.settings`, `settings.section.<key>`. INTEGRATE-B adds the three new ones to `T/ScenarioFilesTest.kt` (known tags; `appearance.pet` is already known from 1a).

### 3.11 Stored data and migration (optional, ignored by the previous build)

| Item | Default | Previous build |
|---|---|---|
| prefs `appearance`, key `accent`, new value `WALLPAPER` | absent; the default stays `THEME` for everyone (no installation is switched to it) | `parse` (Appearance.kt:89 to 90) turns an unknown name into the default, so it draws the theme's own accent and does not crash; if that build then changes any setting it writes `accent = THEME` (the choice is lost, nothing else) |
| prefs `appearance`, key `petInChats` | 1a's, default true | unchanged: 1a's key, now with a row to change it |
| `SystemAccent.pinned`, the three new pin items | memory only, `TEST_HOOKS` only | never written |

**No migration, no write at start, no new file.** On the owner's phone this area writes nothing unless the owner taps the wallpaper row. `BackupCore.PREF_SCHEMA` has `accent` as a string already, so `WALLPAPER` round-trips; restored on a phone below Android 12 it is drawn as the theme's own accent (`effectiveAccent`) and comes back to life on a newer phone. The new keys of 1a (`petInChats` and the rest) are still not in the schema (1a follow-up 9, SPLIT 7).

## 4. Unit tests (JUnit 4, JVM; Compose `Color` is already used by `PaletteContrastTest`)

| Class (file under `T/`) | Tests (name: what it proves) |
|---|---|
| `ui/theme/AccentTest` (new) | `enumKeepsTheOldOrderAndAddsWallpaperLast` (the eight names in today's order, `WALLPAPER` last, `valueOf("WALLPAPER")`); `fixedAccentsAreTheOldEight`; `stopsAreThirteenAndAscend`; `tonesNeedThirteenValues` (`of` with 12 or 14 gives null); `fromSeedRampDarkensStopByStop` (luminance never rises, stop 0 white, stop 1000 black) for 24 seeds; `usableRejectsFlatReversedAndTransparentRamps`; `supportedFromApi31` (30 false, 31 true, 37 true); `luminanceAndRatioMatchReferenceValues` (black on white 21.0; `#777777` on black about 4.69; same-colour 1.0); `thresholdsMatchTheContrastTest` (`TEXT` 4.5, `COMPONENT` 3.0); `paletteForWithoutTonesIsTheTheme` (all 12 variants equal the `THEME` palette); `guardPassesTheChecksForManySeeds` (seeds every 15 degrees of hue x saturation 0.2, 0.6, 1.0, and white, black and grey, x 6 themes x 2 variants: `AccentGuard.acceptable(result)` or the result equals the base; and again after `withBlackNight(true)` for the dark variants); `guardTouchesOnlyTheFiveAccentRoles` (every other field of `PocketColors` equals the base, so the fire and the backgrounds stay); `guardPrefersTheFirstCandidateThatPasses` (a mid-blue seed in NEWROZ dark gets stop 200 as `accent`, stop 100 as `soft`; in NEWROZ light stop 600 and 700: pins the ladders so a refactor cannot change the look silently); `degenerateRampsGiveTheTheme` (all white, all black, flat grey, a reversed ramp: `apply` returns the base); `rolesAreNullWhenNoCandidatePasses`; `effectiveAccentFallsBackOnlyForWallpaper` |
| `ui/theme/AppearanceModelTest` (1a's; additions) | `accentDefaultStaysTheme` (`Appearance().accent == THEME`); `wallpaperDoesNotChangeTheOtherDefaults`; `effectiveAccentTable` (WALLPAPER with and without the colour, a fixed accent in both cases) |
| `ui/theme/AppearancePinTest` (1a's; additions) | `accentItemsParse` (all nine names); `badAccentRejectsTheWholeValue`; `wallpaperSeedParses`; `wallpaperSeedRejectsUpperCaseHashAndWrongLength`; `mascotItemParses`; `wallpaperAloneDoesNotChangeTheAccent`; `pinCarriesTheRamp` (`systemAccent` is non-null exactly when the item was given); `changeAppliesAccentAndMascotAndLeavesTheRest` |
| `ui/theme/PaletteContrastTest` (the existing test; **one line**) | the loop at line 260 becomes `for (accent in AccentId.FIXED)`, with a comment that the wallpaper accent is checked by `AccentTest` (its colour is not fixed). The 215 known failures stay as they are; no id of the new value exists |
| `voice/SoundCuesTest` (1a's; additions, extra) | `crackleFollowTable` (not blazing 0, disabled 0, level 0 gives 0.25, 0.2 gives 0.30, 0.6 gives 0.40, 1 gives 0.50, below 0 and NaN count as 0, above 1 as 1); `crackleFollowIsMonotoneAndBounded` (0 to 1 in steps of 0.01); `crackleFollowAtIdleEqualsTheFlatLevel` (`crackleFollow(0f, true, true) == crackleLevel(true, true)`: cutting B27 changes nothing audible at idle) |
| `ui/settings/SettingsBackSourceTest` (new, reads the source with `Assume.assumeTrue(file.exists())`) | `backArrowIsNotConditional`: `SettingsScreen.kt` has no `if (section != null)` on the `onBack` line and passes a non-null lambda; `noOldLanguageName`: the file does not define `fun languageName(` (LANG's file does). A text guard, honest about being one: the proof is on the phone (section 5) |

`SettingsScreen`, `AppearanceSection` and `rememberSystemAccentTones` are not JVM-testable; they are proved on the phone.

## 5. Proof on the phone

**Safe on the owner's package** (owner-debug, `"safe": true`: memory pins, navigation and pictures only; nothing that stores, no `type`):
- **P1 Settings is pushed.** `--es route settings:appearance` (SHELL's `stackFor` must give `[Home, Settings]`, request in section 7): `screen.settings` and `top.back` present, no `nav.bar` node; tap `top.back`: `screen.home`. Then Home, tap `home.gear` (BARS), `screen.settings`, `top.back`, `screen.home`; the same from the Agent tab with `top.gear`: back is `screen.hermes`. Navigation only.
- **P2 The wallpaper row exists on this phone** (Android 17): open `settings:appearance`, scroll to it: node `appearance.accent.wallpaper` present, `selected="false"` with no pin; `app.log` has `Theme: system accent 500=#RRGGBB` (this is the answer to the first half of E-B3: readable at target 28). Nothing is tapped.
- **P3 Pictures with a pinned ramp** (`appearance accent:wallpaper,wallpaper:e0407a,theme:<t>,dark:<d>`): Appearance (the row selected, the swatch and the check), then Home, Chats, Bots and a chat with a primary button, a selected chip and a switch visible, for two seeds (`e0407a` pink, `2f9e6b` green) x NEWROZ and NERGIZ x dark and light = 8 pictures per screen set; then the same with `black:1`. `appearance accent:theme` for the comparison. A pale seed (`f2f2f0`) must show `appearance.accent.wallpaper.note` or a readable accent, never an unreadable one.
- **P4 The Pêt row:** with `pet:1`, `pet:0` and `mascot:0` pins: node `appearance.pet` appears exactly once in the dump; `appearance.pet.note` only with `mascot:0`. With BARS in the tree, a chat opened while the practice model streams shows the small Pêt only for `pet:1` and `mascot:1`.
- **P5 No old language rows:** the Language section of Settings shows LANG's picker and no "Türkçe, Français" list of the old kind (LANG's pins).
- **P6 Crackle follows the fire (extra):** `appearance crackle:1` with `fire level:0.9`, Home on screen, the hearth Running: `app.log` shows `Sound: loop CRACKLE level=0.5`; then `fire level:0`: `level=0.25`; leaving Home: no further line. Nothing is audible to the reader of the log, which is the point.

**Twin only (T; `pm clear` and stored settings are allowed there):** tap `appearance.accent.wallpaper` and read `selected="true"` and the new `accent` value in `shared_prefs/appearance.xml` (read), tap the "Theme" swatch to restore; restart the app and see the choice kept; **E-B3 second half** (does the colour follow the wallpaper): the owner changes the wallpaper colour once on the twin while the app is open (or the lead allows a system-setting change); the expected result is either an activity recreation with the new colour or the new colour at the next start, and the log line shows the new `500`. If the ramp never changes, the row is still correct (it shows the phone's current colour) and the finding goes to the lead.

**Only the owner's eyes can judge:** whether the wallpaper accent looks good on each of the six themes (taste: the ladders choose the readable tones, not the pretty ones), whether "Wallpaper colour" and its sentence read well, where the row sits (below the grid), whether the crackle's rise is pleasant (the numbers 0.25 to 0.50 are mine) and whether the quiet text under the Pêt switch is wanted.

## 6. Core and extras, and the order of cutting

Core: P1 (enum, ramp, guard, `paletteFor`, `PocketTheme`, the row, the pin items `accent` and `wallpaper`), P2 (the row), P3 (the deletions, which must land with LANG's file), P4 (the back arrow). Extra: P5 (crackle follows the fire). **Cut first to last:** 1. P5 (`LoopMath.crackleFollow` stays a pure function with its test, `SceneSounds` keeps the flat level); 2. `apr_wallpaper_fallback` and its note (the guard still falls back silently); 3. `apr_pet_off_note`; 4. the pin item `wallpaper:<hex>` (a picture then uses the phone's own ramp and cannot compare two seeds); 5. the pin item `mascot`. **Never cut:** `AccentId.WALLPAPER` last in the enum, the guard (a colour is never shown unless it passes), `AccentId.FIXED` in the contrast test, the always-present back arrow, the deletion of the old language code (the build would not compile), the rule "no write at start". A cut symbol keeps an empty body or `null` (1a rule), never a missing name.

**Build order inside slice C** (each step compiles, passes the tests and can be committed alone): (1) slice A (no 1a needed): `SystemAccent.kt` pure parts, `AccentTest` (the parts that do not need `AccentId.WALLPAPER` or `paletteFor`); (2) `AccentId.WALLPAPER`, `FIXED`, `effectiveAccent`, `paletteFor` branch, the one-line contrast-test edit, the exhaustive `when` in `AppearanceSection.accentLabel`, the rest of `AccentTest` (all in one commit: the enum value breaks three `when`s and the contrast loop until they are fixed); (3) `SystemAccent.read/pinned`, `rememberSystemAccentTones`, `PocketTheme`, the pin items and the store hook; (4) the section rows and strings; (5) with LANG: the two deletions; `SettingsScreen` back arrow (can go earlier, it depends on SHELL's `showsBar` only for looks); (6) the extra.

## 7. REQUESTS to other areas (exact) and what this area provides

**To SHELL:** (1) `Route.Settings` must not show the bar and counts as a pushed screen (`showsBar() == false`, `rank() == 1`), with `tabIndex()` that of the tab below; its root id 5 may stay unused. (2) `DebugLinks.stackFor` (today line 271: `is Route.Settings -> if (route.section == null) listOf(route) else listOf(Route.Settings(null), route)`) must give `listOf(Route.Home, route)` for every Settings route, so that `top.back` has somewhere to go and P1 can be photographed. (3) `AppLink.Usage` goes Home, then `Settings("usage")` (their plan already says so). (4) The back arrow of Settings is `nav.back()` with the Home fallback; SHELL does not add a second back handler.
**To BARS:** the gear calls `nav.go(Route.Settings())` (a push, never `nav.tab`), so back returns to the screen the gear was on; Settings itself has no gear. `ChatTopArea` reads `Pocket.appearance.petInChatsVisible` (1a registry), which this row now makes changeable; the tag of the row is `appearance.pet`.
**To LANG:** the new `ui/settings/LanguageSection.kt` must define `@Composable internal fun LanguageSection()` and `@Composable internal fun languageName(code: String): String` in package `dev.pocketagent.app.ui.settings`, land **in the same commit** as the two deletions, and say whether it reuses `ctl_lang_intro`, `ctl_lang_system`, `ctl_lang_system_sub`.
**To INTEGRATE-B / the lead:** (1) known tags of 3.10; (2) retire the three `ctl_lang_*` keys named above from `strings_control.xml`, `values-tr`, `values-fr` if LANG does not reuse them (the ratchet never rises); (3) the stretched language and the nine translations of the four `apr_` keys of 3.8 (stage 4); (4) a scenario `w1b-appearance` (safe) with P1 to P6, written after INTEGRATE-B; (5) confirm that this area edits three 1a files that SPLIT 4.8 does not name: `J/ui/theme/AppearancePin.kt`, `T/ui/theme/AppearancePinTest.kt` and `T/ui/theme/PaletteContrastTest.kt` (a one-line change of the existing test, see section 4), and delete or leave 1a's private Pêt-row composable (it must not be composed).
**To 1a (through the lead):** nothing is needed before 1a is merged. If 1a's `AppearanceScene.kt` names its private Pêt composable `PetPeekSwitchRow`, APPEAR-B renames its own (a compile clash is the only risk).
**This area provides:** `AccentId.WALLPAPER`, `AccentId.FIXED`, `Appearance.effectiveAccent`, `paletteFor(theme, dark, accent, tones)`, `SystemAccentTones`, `SystemAccent`, `rememberSystemAccentTones`, `AccentGuard`, the pin items `accent:`, `mascot:` and `wallpaper:`, the composed `appearance.pet` row, a Settings screen that always has its back arrow, `LoopMath.crackleFollow` (extra). WELCOME, START, LIBRARY and ART-B need nothing from it; every screen they draw inherits the accent through `Pocket.colors`.

## 8. Risks

1. **The wallpaper colour may not be what the plan hopes (experiment E-B3).** `android.R.color.system_accent1_*` are API 31 constants and are read through resources, which should work at target 28; on One UI the ramp follows its own "Color palette" setting and may be the AOSP default blue when that is off; and a wallpaper change may recreate the activity (this one declares `orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode` only) or may not reach the app until the next start. The row is correct either way (it shows the colour read at that moment); the fallback if the read is useless is that the row is not offered and B22 is reported as not shipped.
2. **Contrast is a guard, not a promise of beauty.** A pale or very dark ramp can make the guard fall back to the theme's own accent; the user sees the note, never an unreadable colour. The test covers seeds, not every phone.
3. **Two Pêt rows.** If 1a's INTEGRATE composes its private row after all, `appearance.pet` would appear twice. Check on the first compile of slice C: one node in the dump (P4) and one call of `AppearanceTags.PET` in `AppearanceSection.kt` or `AppearanceScene.kt` that is reachable.
4. **The enum value reaches code this area does not own.** Three `when`s and one test loop are known; a fourth (a future gallery, a test) would fail to compile and is fixed by the owner of that file with `AccentId.FIXED`, never by weakening the guard.
5. **Rollback.** The previous build shows the theme's accent for a stored `WALLPAPER` and overwrites it with `THEME` at its next settings change: harmless, said in 3.11.
6. **Pin side effect.** `SystemAccent.pinned` is a process-wide flow like 1a's other pins; `reset=1` clears it through `AppearanceStore.pin(null)`; in a build without test hooks `setPinned` does nothing, so a release build can never be given a ramp from outside.
7. **Kotlin traps** (1a 1.1 and w0 1.4 hold): declare `argb` before anything in `SystemAccentTones` that reads it; `Resources.getColor(int, Theme)` needs API 23 and the SDK check for the constants' use; the enum's companion reads `entries` after the entries exist (fine, but a `val` in the companion must not be touched from an enum constructor); no slash-star inside a KDoc; `0xFF...` literals need `.toInt()`; `Color` ARGB from `Int` is `Color(argb)` with the alpha already in the value.
8. **Crackle poll (extra).** One call every 400 ms on Home while crackle is on and the hearth burns; stops with STARTED. If a measurement shows anything, the extra is the first to go.

## 9. Open points for the lead

1. **Three 1a files claimed** (`AppearancePin.kt`, `AppearancePinTest.kt`, `PaletteContrastTest.kt`; section 7). Default: yes, they are APPEAR's by nature and nobody else edits them in 1b.
2. **Wallpaper accent as the default on Android 12 and later?** Default: no; `THEME` stays the default for everyone because the Newroz orange is the identity, and the row is one tap away. Taste, the owner may change.
3. **How to change the system colour for the second half of E-B3** without the owner's hands (a system setting; the lead's `-AllowSystemSettings` rule). Default: the first half only until the owner is at the phone; the code does not depend on the answer.
4. **Which of 1a's `ctl_lang_*` texts LANG reuses** decides whether three keys are retired (section 7). Default: LANG says so in its draft; otherwise INTEGRATE-B retires them.
5. **The crackle numbers** (0.25 idle to 0.50 at full work, five steps, 400 ms): by ear on the owner's phone, only after the extra is built. Default as written.
