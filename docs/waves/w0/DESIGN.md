## DESIGN: tokens, motion, touch, components v2, pixel font v2

Plan features 0.5 and 0.6. Owner of `ui/theme/*`, `ui/components/*` and their tests. Binding sources: `docs/design-language.md` sections 1 to 5 and 9 (token names and values below are copied from it), plan 0.5/0.6. Everything is core; Wave 0 has no extras. Package of every new symbol is stated in section 3. Ground rules of Phase 3 apply: no Gradle, no commit, only the files of section 2, English texts only, `stringResource(R.string.x)` only (never `getString`, so WORDS can convert mechanically), no `tween(`, `Toast.makeText`, `RoundedCornerShape(`, literal `N.dp` that equals a token, or literal duration in new code (the ratchet lint counts them).

### 1. Scope (work items)

| # | Item | Result |
|---|---|---|
| 1.1 | `Tokens.kt`: `Space`, `Corner`, `Border`, `Type`, `PixelType`, `Elevation`, the design pixel `u`, bevel colour functions | One place for every dp, step and style the tables of design language 2.1 and 2.2 name |
| 1.2 | `Motion.kt`: `Dur`, `Ease`, `Springs`, `Loop`, `Flourish`, `MotionScheme`, `effectiveAnimLevel`, `LocalMotion`, `Pocket.motion`, `Modifier.pixelOffset` | Every timing is a name; no call site branches on `AnimLevel` any more |
| 1.3 | `SystemMotion.kt`: reads the system animator scale and battery saver; `PocketTheme` provides the effective level, so `Pocket.anim` returns the effective level | The app obeys "remove animations" and battery saver (finding A2) |
| 1.4 | `Haptics.kt`: `HapticCue`, `HapticLevel`, `HapticPlayer`, pure `HapticPlan`; `Appearance.haptics` (default FULL, no UI in Wave 0) | One vocabulary; today's `pixelTap`/`pixelTick` and 9 `performHapticFeedback` calls map onto it |
| 1.5 | Panel variants, press pattern, `PixelShapes`, `DitherScrim` | Pattern 1 on every pressable pixel surface, pattern 6 as a component |
| 1.6 | `PocketSheet` (+ `ActionSheet`, `SheetAction`), `PocketDialog` (pattern 5), `ConfirmDialog` | One sheet, one confirm dialog for 5 sheets and 3 dialogs |
| 1.7 | `PetNote`, `PetNotes`, `PetNoteHost` | Replaces 12 toasts |
| 1.8 | `SwipeRow`, `Skeleton`, `EmberLine`, `PocketTopBar(ember=)` | Built and shown in the gallery; no screen uses them before Wave 1/2 |
| 1.9 | Pixel font v2 (details in 3.9): added glyphs, kerning table (off by default), text-size steps, `PixelTitle`, `PixelFont.textCols` | Defects of finding A12 that are real, without changing any picture at default settings |
| 1.10 | `PaletteContrastTest` with a ratchet list | Contrast is tested; no colour changes |
| 1.11 | `DesignGallery.kt`: one screen with every component, variant and state, plus a specimen of every glyph | The look proof (mirrored, double text, stretched language) and the robot can photograph it; NAV adds the debug route |
| 1.12 | `ui/components/README.md` rewritten for the above | Screen authors read it |

Cut first, in this order, if the session runs out: (a) 1.8 `SwipeRow` (first caller is Wave 2: the app loses nothing today, the Wave 2 agents build it); (b) 1.8 `Skeleton` and `EmberLine` (first callers Wave 1/2); (c) the drag and fling physics of `PocketSheet`: fall back to wrapping Material3 `ModalBottomSheet` with `shape = PixelTopShape`, our grabber and the platform scrim (the app then loses the dither scrim and the finger-following sheet until Wave 1, and the sheets still become one component). Never cut: 1.1 to 1.7, 1.10, because SWEEP and the lint depend on them.

### 2. Files (exclusive)

New, `ui/theme/`: `Tokens.kt`, `Motion.kt`, `SystemMotion.kt`, `Haptics.kt`.
New, `ui/components/`: `PixelShapes.kt` (`PixelShapes`, `PixelTopShape`, pure `stepCornerOffsets`), `PixelPress.kt`, `DitherScrim.kt` (+ pure `DitherPattern`), `PocketSheet.kt`, `ConfirmDialog.kt`, `PetNote.kt`, `SwipeRow.kt`, `Skeleton.kt`, `EmberLine.kt`, `DesignGallery.kt`.
Modified, `ui/theme/`: `PocketTheme.kt` (provides `LocalAnimLevel`, `LocalMotion`; `Pocket.anim` reads the effective level; adds `Pocket.motion`), `Appearance.kt` (field `haptics`, key `"haptics"`), `Palettes.kt` (two fields at the END of `PocketColors`, defaults derived: `glow: Color = fireMid`, `scrim: Color = if (isDark) Color.Black else Color(0xFF2A1A24)`; the palette tables are not touched), `PixelFont.kt` (additions only, v1 `def(` lines stay byte for byte; new glyphs in a `defV2()` called last in `init`), `PixelIndication.kt` (only if the sink needs it; otherwise untouched).
Modified, `ui/components/`: `PixelPanel.kt` (variants; `pixelSurface` takes `pressDepthPx`; `pixelTap`/`pixelTick` deleted, only components used them), `PixelButton.kt` (press pattern, `Loop.GLOW`, haptics), `PixelText.kt` (text-size steps, kerning switch), `PocketChip.kt`, `PocketSwitchRow.kt` (`motion.fast()`), `PocketDialog.kt` (scrim, pop-in), `PocketTopBar.kt` (`ember`, `PixelTitle` for the title), `TypingIndicator.kt` (`Loop.TYPING`), `EmptyState.kt` and `SectionHeader.kt` (tokens only), `README.md`.
Strings: `res/values/strings_design.xml` (existing, mine). Tests: section 8. Not touched: `PixelIcon.kt`, `PocketTextField.kt` (no token-equal literals worth the risk), `AppIconManager.kt`, sound (`voice/*` is nobody's in Wave 0; design language section 5 adds cues only from Wave 1).

### 3. Shared symbols I PROVIDE

#### 3.1 `ui/theme/Tokens.kt` (package `dev.pocketagent.app.ui.theme`)
```kotlin
object Space { val xxs: Dp = 2.dp; val xs: Dp = 4.dp; val s: Dp = 8.dp; val m: Dp = 12.dp; val l: Dp = 16.dp
               val xl: Dp = 24.dp; val xxl: Dp = 32.dp; val gutter: Dp = 16.dp; val touch: Dp = 48.dp }
object Corner { const val chip = 1; const val card = 2; const val sheet = 3 }   // steps of the design pixel u
object Border { const val thin = 1; const val focus = 1 }                       // multiples of u
object Type { val title: TextStyle   // 16/22 sp SemiBold
              val body: TextStyle    // 15/22 sp      (= MaterialTheme bodyMedium today)
              val reading: TextStyle // 15.5/24 sp
              val label: TextStyle   // 12/16 sp Medium (= labelMedium today)
              val mono: TextStyle }  // 13/19 sp FontFamily.Monospace
object PixelType { val small: Dp = 1.5.dp; val title: Dp = 2.dp; val big: Dp = 3.dp }  // glyph pixel sizes in use today
enum class Elevation { FLAT, RAISED, FLOATING, MODAL }
fun designUnitPx(density: Float): Int                 // max(1, round(2f * density)); 2.0 -> 4, 2.625 -> 5, 3.0 -> 6, 3.5 -> 7
fun Density.u(steps: Int = 1): Float                  // steps * designUnitPx(density)
fun snapToDesignPx(valuePx: Float, unitPx: Int): Float // round(valuePx / unitPx) * unitPx
fun pixelFontPx(pixelDp: Float, density: Float, fontScale: Float, follow: Boolean): Int
fun bevelRim(face: Color, dark: Boolean): Color       // lerp(face, White, if (dark) 0.10f else 0.55f): today's formula
fun bevelShade(face: Color, dark: Boolean): Color     // lerp(face, Black, if (dark) 0.22f else 0.07f): today's formula
```
Behaviour: `pixelFontPx` multiplies `pixelDp` by m: fontScale < 0.95 gives 0.75, up to 1.15 gives 1.0, up to 1.6 gives 1.25, above gives 1.5 (if `follow`; else 1.0); the result in dp is capped at `max(pixelDp, 3f)`; returns `max(1, round(dp * density))`. At fontScale 1.0 it equals today's `pixel.toPx().roundToInt()`. `fontScale` is `LocalDensity.current.fontScale` (PocketTheme already multiplies the system scale by `Appearance.textScale`).

#### 3.2 `ui/theme/Motion.kt`
```kotlin
object Dur { const val TAP = 90; const val FAST = 150; const val BASE = 220; const val SLOW = 320; const val SCENE = 480; const val EPIC = 900 }
object Ease { val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f); val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
              val exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f); fun steps(n: Int): Easing }  // fraction f -> floor(f*n)/n, 1f at f >= 1
object Springs { const val SNAPPY_STIFFNESS = 1400f; const val SNAPPY_DAMPING = 0.90f; const val GLIDE_STIFFNESS = 380f; const val GLIDE_DAMPING = 0.86f
                 const val BOUNCY_STIFFNESS = 600f; const val BOUNCY_DAMPING = 0.55f
                 fun <T> snappy(): SpringSpec<T>; fun <T> glide(): SpringSpec<T>; fun <T> bouncy(): SpringSpec<T> }  // raw, level-blind; screens use MotionScheme
object Loop { const val GLOW = 1700; const val TYPING = 1000; const val CARET = 520; const val EMBER = 700; const val SLIDE = 1100; const val SHIMMER = 1400; const val EMBER_LINE = 1200 }
object Flourish { const val STOKE = 1200; const val SHOWER = 2400 }          // the two durations above EPIC that exist today
fun effectiveAnimLevel(setting: AnimLevel, animatorScale: Float, powerSave: Boolean): AnimLevel
@Immutable class MotionScheme(val level: AnimLevel) {
    fun <T> tap(): FiniteAnimationSpec<T>   // tween(Dur.TAP, Ease.standard)
    fun <T> fast(): FiniteAnimationSpec<T>; fun <T> base(): FiniteAnimationSpec<T>; fun <T> slow(): FiniteAnimationSpec<T>; fun <T> scene(): FiniteAnimationSpec<T>
    fun <T> glide(): FiniteAnimationSpec<T>   // Springs.glide
    fun <T> snappy(): FiniteAnimationSpec<T>  // Springs.snappy
    fun <T> bouncy(): FiniteAnimationSpec<T>  // Springs.bouncy
    fun <T> pop(): FiniteAnimationSpec<T>     // pattern 2: tween(Dur.FAST, Ease.steps(3))
    fun <T> custom(durationMs: Int, easing: Easing = Ease.standard): FiniteAnimationSpec<T>   // only with Flourish.* or Dur.*
    fun <T> loop(periodMs: Int, repeatMode: RepeatMode = RepeatMode.Restart, easing: Easing = LinearEasing): InfiniteRepeatableSpec<T>
    val loops: Boolean        // level != OFF: callers that must not start a loop at OFF branch on this, as TypingIndicator does today
    val particles: Boolean; val parallax: Boolean; val shaders: Boolean   // true only at FULL
    val sceneFps: Int         // FULL 30, REDUCED 10, OFF 0
}
val LocalAnimLevel: ProvidableCompositionLocal<AnimLevel>; val LocalMotion: ProvidableCompositionLocal<MotionScheme>
val Pocket.motion: MotionScheme @Composable get()        // extension on the existing object Pocket
fun Modifier.pixelOffset(pixel: Dp = 2.dp, offset: Density.() -> Offset): Modifier   // Modifier.offset with both axes snapped by snapToDesignPx
```
Contracts. `effectiveAnimLevel` = the lowest of `setting`, OFF if `animatorScale <= 0f` or NaN, and REDUCED if `powerSave` (order FULL > REDUCED > OFF). Level OFF: every `FiniteAnimationSpec` is `snap()`. REDUCED: tweens are clamped to 150 ms; springs and `pop` become `tween(Dur.FAST, Ease.standard)`; `loop` periods are multiplied by 1.8 (TypingIndicator today: 1000 to 1800). `loop` at OFF returns the FULL spec (callers branch on `loops`).

#### 3.3 `ui/theme/SystemMotion.kt`
```kotlin
@Immutable data class SystemMotionState(val animatorScale: Float, val powerSave: Boolean)
@Composable fun rememberSystemMotion(): SystemMotionState
```
Reads `Settings.Global.ANIMATOR_DURATION_SCALE` (default 1f, try/catch) through a `ContentObserver` and `PowerManager.isPowerSaveMode` through a `BroadcastReceiver` for `PowerManager.ACTION_POWER_SAVE_MODE_CHANGED`; both registered and removed in a `DisposableEffect`; refreshed on resume. `PocketTheme` calls it once and provides `LocalAnimLevel`/`LocalMotion`. `Pocket.anim` now returns the EFFECTIVE level, so the 20 existing readers (survey, section 7) obey the system with no edit; `Pocket.appearance.animations` stays the raw setting for the Settings chips.

#### 3.4 `ui/theme/Haptics.kt`
```kotlin
enum class HapticCue { TICK, CLICK, SEND, RISE, DOUBLE, HOLD, STOKE, PULSE }
enum class HapticLevel { OFF, SUBTLE, FULL }          // stored in Appearance.haptics by name; default FULL
enum class HapticKind { SEGMENT_FREQUENT_TICK, CLOCK_TICK, VIRTUAL_KEY, CONFIRM, REJECT, LONG_PRESS, GESTURE_THRESHOLD_ACTIVATE }
class HapticPlan(val kinds: List<HapticKind>, val gapMs: Long)
object Haptics {
    fun planFor(cue: HapticCue, level: HapticLevel, sdk: Int): HapticPlan?         // pure; null = nothing
    fun play(view: View, cue: HapticCue, level: HapticLevel = HapticLevel.FULL)
}
@Stable class HapticPlayer { fun play(cue: HapticCue) }
@Composable fun rememberHaptics(): HapticPlayer       // view from LocalView, level from Pocket.appearance.haptics
```
Table (design language 4; first constant, fallback below the API in brackets): TICK `SEGMENT_FREQUENT_TICK` (API 34; else `CLOCK_TICK`); CLICK `VIRTUAL_KEY`; SEND `CONFIRM` (API 30; else `VIRTUAL_KEY`); RISE `CONFIRM` (else `VIRTUAL_KEY`; the vibration-primitive variant is NOT built); DOUBLE `REJECT` (API 30; else `LONG_PRESS`); HOLD `LONG_PRESS` (and `GESTURE_THRESHOLD_ACTIVATE` is not used); STOKE three `TICK`, `gapMs = 40`, posted with `View.postDelayed`; PULSE one `CLOCK_TICK`. Level OFF: null for all. SUBTLE: only SEND, RISE, DOUBLE, HOLD. No permission is needed (`View.performHapticFeedback`).

#### 3.5 Panel, shapes, press, scrim (package `dev.pocketagent.app.ui.components`)
```kotlin
enum class PanelVariant { FLAT, RAISED, INSET, GLOWING, FLOATING }
@Composable fun PixelPanel(modifier: Modifier = Modifier, color: Color = Pocket.colors.surface, border: Color = Pocket.colors.outline,
    corner: Int = 2, pixel: Dp = 2.dp, highlight: Boolean = true, variant: PanelVariant = PanelVariant.RAISED, content: @Composable BoxScope.() -> Unit)
object PixelShapes { val chip: Shape; val card: Shape; val sheet: Shape }          // PixelShape(Corner.x)
class PixelTopShape(corner: Int = Corner.sheet, pixel: Dp = 2.dp) : Shape          // stepped top corners, square bottom
fun stepCornerOffsets(n: Int, p: Float): List<Pair<Float, Float>>                  // pure: one corner's staircase, from (0, n*p) to (n*p, 0)
@Stable class PixelPress { val pressed: Boolean; val sinkPx: Int }
@Composable fun rememberPixelPress(interactionSource: InteractionSource, sink: Dp = 2.dp): PixelPress
fun Modifier.pixelPressOffset(press: PixelPress): Modifier                         // offset { IntOffset(0, press.sinkPx) }
fun sinkPixels(depth: Float, maxPx: Int): Int                                      // pure: (depth * maxPx) rounded, 0..maxPx
object DitherPattern { fun cells(step: Int): Set<Int>; const val STEPS = 4 }       // pure; cell index = row * 4 + col in the 4x4 grid
@Composable fun DitherScrim(step: Int, modifier: Modifier = Modifier, color: Color = Pocket.colors.scrim)   // step 0..4: 0 draws nothing, 4 = 50 %
```
Contracts. `PixelPanel` with `highlight = true` and the default variant draws exactly today's pixels (`RAISED`; `highlight = false` and `variant == RAISED` means `FLAT`); `INSET` swaps the lit and shade lines; `GLOWING` = RAISED plus the two glow rings in `Pocket.colors.glow` at alpha 0.2; `FLOATING` = RAISED plus a dithered drop shadow (offset 2u, `DitherPattern.cells(2)` = 25 %). Existing positional callers are unaffected (`variant` sits after `highlight`). `DitherPattern.cells(step)`: Bayer 4x4 order `[0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5]`, cell `i` is on when `bayer[i] < 2 * step`: 0, 2, 4, 6 or 8 of 16 cells, each step a superset of the one before. The tile is one `ImageBitmap` of `4u x 4u` device pixels, cached per `(u, step, color)`, drawn with a repeated `ImageShader`. `sinkPx` rises to the full depth in the frame of the press and returns with `motion.snappy()` rounded each frame to whole pixels (OFF: instant). The lip of `pixelSurface` is drawn always and the face translates by `pressDepthPx`; at depth = lip height the picture is today's pressed picture.

#### 3.6 Sheet and dialogs
```kotlin
interface PocketSheetScope : ColumnScope { fun close(then: () -> Unit = {}) }       // animates out, then onDismiss(), then then()
@Composable fun PocketSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null,
    maxHeightFraction: Float = 0.92f, content: @Composable PocketSheetScope.() -> Unit)
class SheetAction(val icon: PixelIcon, val label: String, val danger: Boolean = false, val onClick: () -> Unit)   // public, same shape as ChatCommon's
@Composable fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit)                  // same signature as ChatCommon.kt:279
@Composable fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = false, closeOnConfirm: Boolean = false, dismissLabel: String? = null)
@Composable fun PocketDialog(title: String, onDismiss: () -> Unit, confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)                     // UNCHANGED signature
```
`PocketSheet`: a `Dialog(usePlatformDefaultWidth = false)` window; full-screen `DitherScrim` (step follows the sheet's open fraction, `tween(Dur.FAST, Ease.steps(4))`, OFF snaps) over which a bottom-anchored `PixelPanel(corner = Corner.sheet)` with a pixel grabber (a 12 x 2 u bar in `Pocket.colors.outline`), `widthIn(max = 640.dp)`, `heightIn(max = fraction of the screen)`, `imePadding()`, navigation-bar padding as `ChatCommon.bottomSafeInsets` does (copied, private), and the optional `title` (`Type.label`, `textMuted`). Open: from fully down to 0 with `motion.glide()`. Only the grabber and title strip drag (`draggable`, vertical); release settles by `SheetPhysics.settle`; scrim tap, back press and the grabber's click action ("Close", `R.string.ui_dlg_close`) call `close()`. Content scrolls on its own; no nested-scroll coupling. While open it raises `PetNotes` overlay count. `ConfirmDialog` (`danger` selects `ButtonKind.DANGER` or `PRIMARY`; dismiss button `GHOST` with `dismissLabel ?: R.string.cancel`): `onConfirm()` is called; when `closeOnConfirm`, `onDismiss()` is called right after; otherwise the caller closes the dialog itself (the behaviour of the settings and routines variants). `PocketDialog`: the platform dim is removed (`(LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)`; when the cast fails the platform dim stays and the own scrim is skipped), a `DitherScrim` fills in over `Dur.FAST`, the panel pops in with `motion.pop()` (scale 0.9 to 1 and fade); no exit animation.

Pure: `object SheetPhysics { fun settle(offsetPx: Float, velocityPxPerSec: Float, heightPx: Float): SheetOutcome /* OPEN or CLOSE */ }`: CLOSE when velocity > 1200 or (velocity > -1200 and offset > 0.35 * height).

#### 3.7 Notes
```kotlin
enum class PetNoteKind { INFO, SUCCESS, ERROR }     // Pêt mood IDLE, HAPPY, ERROR
object PetNotes {
    fun show(text: String, kind: PetNoteKind = PetNoteKind.INFO, actionLabel: String? = null, onAction: (() -> Unit)? = null)  // any thread
    fun clear()
}
@Composable fun PetNoteHost(modifier: Modifier = Modifier)     // NAV places it once, see section 7
class PetNoteQueue(private val clock: () -> Long) { /* pure */ }
```
`show` never throws and never needs a Context. Until a host is composed, notes wait in the queue (a note older than 8 s when the host attaches is dropped). Rules in `PetNoteQueue`: one note visible; at most 3 waiting (the oldest without an action is dropped first); the same text and kind within 1,000 ms of the last is ignored; visible time `displayMillis(text, hasAction) = if (hasAction) 6000 else (2200 + 40 * length).coerceIn(2500, 5000)`; the timer is paused while `PocketSheet` or `PocketDialog` is open (a note raised from under a dialog is hidden by its scrim; the pause keeps it from expiring unseen). Look: a `PixelPanel(variant = FLOATING, color = surfaceHigh, corner = 2)` with `Mascot(mood, size = 32.dp)` at the start, the text in `Type.body` (max 2 lines), an optional `PixelButton(kind = GHOST)` for the action, and a dismiss click action (`R.string.design_note_dismiss`); pops in with `motion.pop()`, leaves with `motion.tap()`; `semantics { liveRegion = LiveRegionMode.Polite }`; text room 80 characters.

#### 3.8 Swipe, skeleton, ember
```kotlin
class SwipeAction(val icon: PixelIcon, val label: String, val color: Color, val danger: Boolean = false, val onTrigger: () -> Unit)
@Composable fun SwipeRow(modifier: Modifier = Modifier, startActions: List<SwipeAction> = emptyList(), endActions: List<SwipeAction> = emptyList(),
    enabled: Boolean = true, content: @Composable () -> Unit)
object SwipeMath { fun progress(offsetPx: Float, widthPx: Float): Float; fun settle(offsetPx: Float, velocityPxPerSec: Float, widthPx: Float): SwipeOutcome }
@Composable fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, corner: Int = Corner.chip)      // width from the modifier
@Composable fun EmberLine(active: Boolean, modifier: Modifier = Modifier)                                  // 2u high, fixed layout height
@Composable fun PocketTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, ember: Boolean = false)                                  // ember is new and LAST
@Composable fun PixelTitle(text: String, modifier: Modifier = Modifier, color: Color = Pocket.colors.text, pixel: Dp = PixelType.title, shadow: Color? = null)
```
`SwipeRow`: `startActions` revealed by dragging toward the end side in left-to-right layouts (mirrored through `LocalLayoutDirection`); each side acts at 40 % of the width, shows its icon growing from 0.6 to 1.0 as `progress` goes 0 to 1, plays `HapticCue.HOLD` when it crosses; releasing past the threshold or with velocity above 1,000 px/s calls the first action's `onTrigger` and springs back (`motion.snappy()`); otherwise it springs back. Rule 6 of the plan: every `SwipeAction` is also exposed as a `semantics { customActions }` entry, so a labelled way always exists. `Skeleton`: base fill `surfaceHigh`, a 25 % dither band in `surface` stepping 12 positions per `Loop.SHIMMER` period (`Ease.steps(12)`); still at REDUCED and OFF; `contentDescription = R.string.ctl_loading`. `EmberLine`: inactive draws nothing; active draws a pixel line in the fire ramp (`fireLow`, `fireMid`, `fireHigh`) whose run lengths flicker at 6 steps per second (`Loop.EMBER_LINE`); at OFF it is a still line; description `R.string.design_ember_active`. `PixelTitle`: picks the first of [scaled, unscaled, 0.75 x base] glyph pixel sizes that fits one line (`PixelFont.textCols(text) != null` and `cols * px <= available`), else Material `Text(style = Type.title)`; maxLines is 1.

#### 3.9 Pixel font v2 (what changes, and what is proved not to)
1. Added glyphs, new `defV2()`: `Æ æ Œ œ` (hand drawn, 7 columns wide), `Ÿ ÿ` (`accent(..., 'Y', DIAER, 0)` and `'y', DIAER, 2)`), `« » € £ ° ± ↑ ↓`. The definitive list is whatever the coverage test finds in `res/values*/strings*.xml` that is neither a glyph, nor in `PixelFont.MAP`, nor in the test's short documented list of expected fallbacks. Cyrillic (Wave 1) and Greek (Wave 12) are not added.
2. `İ` is NOT changed: `PixelFont.kt:237` already draws it with a dot on row 1. The screenshot `dist/phone-shots/09_group_chat.png` shows a title "Ikiniz de..." where the dot is missing; the font cannot have caused it. The text reaches the font as a plain `I`, so some code turned a lowercase `i` into `I` with the root locale (see risk 11.2). A test pins that `İ` differs from `I`.
3. Tabular digits: the ten digit glyphs are already 5 wide; the test pins it. No change.
4. Kerning: `PixelFont.kern(prev: Char, next: Char): Int` (0 or -1) from a table of pairs (`AV AT AY LT LV LY PA TA VA YA FA T. T, Y. V. r. r,`; the exact set is the agent's, within the overlap rule of the test). `PixelText` gets `kerning: Boolean = false`; the default stays false in Wave 0, so no text moves. Wave 1 flips the default after the owner looks at the gallery.
5. `PixelText(text, modifier, color, pixel, shadow, maxLines, center, followTextScale: Boolean = true, kerning: Boolean = false)` (new parameters last). The glyph pixel in device pixels is `pixelFontPx(pixel.value, density, fontScale, followTextScale)`: identical to today at font scale 1.0, three steps otherwise. New `PixelFont.textCols(text: String): Int?` (columns including the 1-pixel spacing, `null` when a character has no glyph) is pure and is what `PixelTitle` measures with.
6. Proof that nothing else changes: the v1 `def(` and `accent(` lines are not edited (the diff of `PixelFont.kt` is additions only, which the lead checks with `git diff -U0`), and `PixelFontGoldenTest` compares every v1 character's width and rows with a golden file made from the commit before Wave 0 (procedure in section 8).

#### 3.10 Old pattern to new call (for SWEEP, STREAM, NAV)
| Old | New |
|---|---|
| `Toast.makeText(ctx, X, Toast.LENGTH_SHORT).show()` (12 sites) | `PetNotes.show(X)`; failures `PetNotes.show(X, PetNoteKind.ERROR)` (attach failed `AppRoot:296`, `ChatScreen:395`, `:1014`, send not accepted `ChatScreen:525`, `HomeScreen:274`, export failed, voice unavailable, `ChatScreen:967`); saved, created, started, copied, forked: `SUCCESS` |
| `toast(ctx, m)` (`CtlUi.kt:204`) and `showToast(ctx, m)` (`RoutineUiParts.kt:119`) | Keep the function and its signature, change the body to `PetNotes.show(message, kind)` and add a last parameter `kind: PetNoteKind = PetNoteKind.INFO`; the 15 call sites stay as they are, except `BotShareUi.kt:116, 126, 130` and `HermesTabs.kt:120`, which pass `PetNoteKind.ERROR` |
| private `ConfirmDialog` of `ChatCommon.kt:314` (named `body`, default `danger = true`, calls `onDismiss()` after `onConfirm()`) | delete it; `import dev.pocketagent.app.ui.components.ConfirmDialog`; every call adds `closeOnConfirm = true` and, if it has no `danger =`, `danger = true` (3 calls: `ChatListScreen.kt:239`, `ChatScreen.kt:988`, `:996`) |
| `ConfirmDialog` of `CtlUi.kt:150` and the private one of `RoutineEditorScreen.kt:410` (named `text`, `danger = false`, caller closes) | delete both; import the components one; rename the argument `text =` to `body =` in every call (`BotEditorScreen:384,389`, `BotShareUi:211`, `HermesDocs:196`, `HermesScreen:164`, `HermesTabs:110`, `RoutineEditorScreen:327,332`, `BackupSection:197`, `RuntimeSection:115`, `UsageSection:197`, `VoiceSoundSection:285`); the 6 files that import `dev.pocketagent.app.ui.settings.ConfirmDialog` change that import |
| `SheetAction`, `ActionSheet` of `ChatCommon.kt:274, 279` | delete; import `ui.components.ActionSheet` and `ui.components.SheetAction`; calls are unchanged (`ChatListScreen:221-229`, `ChatScreen:901-962`). STREAM adds the two imports to `ChatScreen.kt` at once: an explicit import wins over the same-package declaration, so the file compiles before and after SWEEP deletes the old ones |
| `ModalBottomSheet(...)` in `AttachmentUi.kt:70`, `ChatQuickBar.kt:187`, `NewChatSheet.kt:71`, `SkillPickerSheet.kt:108` (the fifth is `ActionSheet`) | `PocketSheet(onDismiss = onDismiss, title = ...) { ...same Column content... }`; drop `rememberModalBottomSheetState`, `containerColor`, `shape`, `dragHandle`, and the `windowInsetsPadding(bottomSafeInsets())` wrapper (the sheet pads itself); `title` = the existing small heading `Text` if the sheet has one |
| `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` (`ChatListScreen:277`, `ChatMessages:96`, `ChatScreen:657,663`, `HomeScreen:174`) | `haptics.play(HapticCue.HOLD)` with `val haptics = rememberHaptics()` |
| `...HapticFeedbackType.TextHandleMove` (`ChatMessages:283`, `ChatScreen:693,707,712`, `HomeScreen:177,264`) | `haptics.play(HapticCue.TICK)` (HomeScreen fire stoke: `HapticCue.STOKE`) |
| `tween(ms)` finite (list of today's literals) | `140`, `100`, `150`, `120`, `90` -> `motion.fast()` or `motion.tap()` (up to 110 tap, up to 170 fast); `180`, `200`, `240`, `250` -> `motion.base()`; `400`, `520` -> `motion.scene()`; `1200` -> `motion.custom(Flourish.STOKE)`; `2400` -> `motion.custom(Flourish.SHOWER, LinearEasing)`; `snap()` branches on OFF go away |
| `infiniteRepeatable(tween(N, ...), mode)` | `motion.loop(Loop.X, mode, easing)`: `ChatCommon:263` -> `Loop.EMBER` (Reverse); `ChatMessages:455` -> `Loop.CARET` (Reverse, Linear); `HomeParts:70` -> `Loop.SLIDE` (Reverse, Linear) |
| `spring(dampingRatio = ..MediumBouncy ...)` (`ChatMessages:287`) | `motion.bouncy()` |
| `Pocket.anim == AnimLevel.X` branches | still valid (now the effective level); prefer `motion.loops` / `motion.particles` |
| literal `dp` equal to a token (2, 4, 8, 12, 16, 24, 32, 48) | `Space.xxs` ... `Space.touch`; only exact equals, a number is never changed; `16.dp` side margins `Space.gutter` |

Components that change behaviour for callers without changing signature: `PixelButton` (all kinds sink and spring back; GHOST keeps its tint), `PixelIconButton` (sinks its icon 1u and keeps the tint), `PocketChip` (sinks, no tint), `PocketSwitchRow` (haptic TICK, `motion.fast()`), `PocketDialog` (scrim, pop-in). Source-compatible: `PixelPanel` (+`variant`), `PixelText` (+2 trailing), `PocketTopBar` (+`ember` last). Removed (internal, components only): `View.pixelTap`, `View.pixelTick`; changed (internal, components only): `pixelSurface(..., pressed: (() -> Boolean)?)` becomes `pressDepthPx: (() -> Int)? = null`.

### 4. Shared symbols I CONSUME

| From | Symbol (exact) |
|---|---|
| STAGE | `fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 96.dp)` and `enum class MascotMood { IDLE, THINKING, TALKING, HAPPY, SLEEPING, ERROR, WAVING }` in `ui/pixel/Mascot.kt` (exists; must not change) |
| existing | `PixelIcon`, `PixelIconView(icon, modifier, tint, size, contentDescription)`, `PixelText`, `Pocket.colors`, `Pocket.appearance`, `paletteFor(ThemeId, Boolean, AccentId)`, `R.string.cancel`, `R.string.ui_dlg_close`, `R.string.ctl_loading`, `R.string.design_back`, `R.string.design_typing` |
| NAV | the placement of `PetNoteHost` and the debug route (section 7); `testTagsAsResourceId` at the root |
| TEXT / WORDS | later turns `stringResource(R.string.x)` in my files into `t(`: I write no other text API |

### 5. Strings (`res/values/strings_design.xml`, prefix `design_`)

| Key | English | Translator note |
|---|---|---|
| `design_note_dismiss` | `Dismiss` | Screen-reader label of the action that hides a small note at the bottom of the screen. One word, a verb |
| `design_ember_active` | `Working` | Screen-reader label of a thin animated line at the top of a chat that shows the assistant is busy. One word |

Everything else reuses existing keys that exist in all three languages (`cancel`, `ui_dlg_close`, `ctl_loading`), so no English text appears in a Turkish or French screen except these two screen-reader labels until the translation step.

### 6. Manifest entries

None. Haptics use `View.performHapticFeedback` (no `VIBRATE` permission), the dialogs are Compose `Dialog` windows, the battery-saver receiver is registered at run time.

### 7. REQUESTS to other areas

- NAV: `ui/AppRoot.kt`: place `PetNoteHost(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = <height of the bottom bar when `showBar`, else 0.dp>))` as the LAST child of the root `Box` inside `PocketTheme`; replace `AppRoot.kt:296` `Toast.makeText` with `PetNotes.show(msg, PetNoteKind.ERROR)`; migrate its tweens (lines 195-199, 272-273, 447) with the table in 3.10; add a debug-only route (guarded by `BuildConfig.DEBUG`, so R8 drops it) that shows `DesignGallery()` from `ui/components/DesignGallery.kt` (`@Composable fun DesignGallery()`, no parameters), with the link `agiri://debug/route/designgallery` or whatever form NAV chooses for the generic "open any route" link, and tell ROBOT the exact string.
- ROBOT: `tools/lint-ui.ps1`: exclude `ui/theme/Motion.kt`, `ui/theme/Tokens.kt` and `ui/theme/PocketTheme.kt` (the Material fallback shapes cannot be stepped) from the four counts; do not count `Dur.`, `Loop.` or `Flourish.` arguments as literal durations; add a fifth counter for `performHapticFeedback(` and `HapticFeedbackType` outside `Haptics.kt`, and one for `ModalBottomSheet(` outside `PocketSheet.kt`. Take the baseline after the integration compile.
- TEXT: `tools/strings-check.ps1`: tolerate (warn) `design_note_dismiss` and `design_ember_active` missing from `values-tr` and `values-fr` until the Wave 0 translation pass, or the lead supplies those four texts.
- SWEEP and STREAM: do the replacements of 3.10 in the files they own; STREAM also owns `ChatMessages.kt:455` (`Loop.CARET`), `ChatMessages.kt:287`, `ChatScreen.kt` toasts.
- Lead (file has no owner): `data/BackupManager.kt` `BackupCore.PREF_SCHEMA`: add `"haptics" to 's'` under `"appearance"`, so a restore keeps the setting. Harmless if skipped (unknown keys are not restored, the default is FULL).

### 8. Tests (JVM, JUnit 4; all logic is a pure function or class, no Android calls; Compose `Color`, `Easing` and `Dp` are fine on the JVM)

New package `app/src/test/java/dev/pocketagent/app/ui/theme/` and `.../ui/components/`.
| Class | Tests | Proves |
|---|---|---|
| `TokensTest` | `spaceValues`, `designUnitAtDensities` (2.0, 2.625, 3.0, 3.5, 0.5 gives 4, 5, 6, 7, 1), `pixelFontPxIdenticalAtScaleOne` (equals `round(pixel * density)` for 1.5, 2, 3 dp at densities 2.0 to 3.5), `pixelFontPxSteps` (0.9, 1.0, 1.3, 1.8), `pixelFontPxCapsAtThreeDp`, `bevelFormulasEqualOldOnes` (against `lerp` with the constants of PixelPanel.kt), `snapRounds` | Tokens are the numbers of the design language and nothing moved at default text size |
| `MotionTest` | `durationsAreTheTable`, `stepsEasing` (n = 3: 0, 0.33, 0.66, 1 at fractions 0, 0.34, 0.67, 1), `effectiveLevelTruthTable` (all 3 x scales {0, 0.5, 1, NaN} x 2), `offSnapsEverything`, `reducedClampsTo150`, `loopScalesAtReduced` (1000 gives 1800), `sceneFpsAndFlags` | The effective level rule and what each level means |
| `HapticsTest` | `planMatrix` (8 cues x 3 levels x sdk 26, 29, 30, 34), `subtleOnlyConfirmErrorHold`, `stokeIsThreeTicks40Apart` | The cue table and fallbacks |
| `ContrastTest`: `PaletteContrastTest` | `bodyTextOnSurfaces`, `mutedTextOnSurfaces`, `accentSoftOnBgAndSurface`, `onAccentOnAccent`, `bubblesOnTheirFaces`, `textOnCodeBg`, `signalColoursOnBgAndSurface`, `outlinesAndAccentAtThree`, each over 6 themes x 2 variants x 8 accents with the thresholds 7.0 (text on bg), 4.5 and 3.0 | Contrast, with a ratchet: `KNOWN_FAILURES: Set<String>` of ids like `NEWROZ/dark/FLAME:outline-on-bg`; the test fails on a failure not in the set and on a set entry that now passes. The agent cannot run it: it leaves the set EMPTY, and the integration agent runs it once and pastes the printed ids. Expect `outline` pairs to be there |
| `PixelFontTest` | `everyGlyphRowHasItsWidth`, `digitsAreTabular`, `dottedCapitalIDiffersFromI`, `coversEverythingInShippedStrings` (reads `src/main/res/values*/strings*.xml` relative to the module directory, collects every code point above 0x7E, must each have a glyph, be in `PixelFont.prepare`'s map, or be in the test's `EXPECTED_FALLBACK`), `textColsNullOnUnknown`, `kerningNeverOverlapsOrTouches` (for each pair in the table, moving the second glyph one column left puts none of its pixels on a pixel of the first), `v2GlyphsStayOnGrid` (rows 0-1 marks, capitals 3-9, descenders 10-11) | Font v2 coverage and rules |
| `PixelFontGoldenTest` | `v1GlyphsUnchanged` | Compares every character that existed before Wave 0 (width and rows) with `app/src/test/resources/pixelfont-v1.golden` (one line per char: `U+XXXX width row0|row1|...`). Making the golden: the test has a writer branch that runs when env var `PIXELFONT_GOLDEN_WRITE` is `1` and writes the file instead of comparing. The integration agent runs it ONCE against commit `344829f` (a scratch worktree with only this test file added), commits the file, and the real run compares. A file missing makes the test fail with that instruction |
| `PixelShapesTest` | `stepCornerOffsetsForN1N2N3` (point lists, monotonic, first (0, n*p), last (n*p, 0)), `ditherCellCounts` (0, 2, 4, 6, 8), `ditherStepsAreSupersets`, `ditherStep4IsBalanced` (the 50 % pattern has 2 cells on in every row and every column) | The pure geometry behind the sheet corners and the scrim |
| `PressAndSheetMathTest` | `sinkPixelsRounds` (depth 0, 0.49, 0.5, 1; max 2 and 5), `sheetSettleByVelocityAndDistance`, `sheetSettleUpwardFlingKeepsOpen`, `swipeProgressClamped`, `swipeSettleTriggersAtFortyPercentOrFling`, `swipeSettleSmallDragReturns` | Gesture decisions |
| `PetNoteQueueTest` | `oneVisibleAtATime`, `waitingCapOfThreeDropsOldestWithoutAction`, `duplicateWithinASecondIgnored`, `displayMillisFormula` (empty 2500, 80 chars 5000, with action 6000), `pausedWhileOverlayOpen`, `staleNotesDroppedOnAttach` (8 s), `clearEmptiesAll` | The queue rules |
| `PanelVariantTest` | `raisedWithHighlightEqualsToday`, `highlightFalseMeansFlat`, `variantResolution` (pure `fun resolveVariant(highlight: Boolean, variant: PanelVariant): PanelVariant` in `PixelPanel.kt`) | Existing panels keep their pixels |
| `PixelTitleTest` | `pickTitleStepFirstThatFits`, `pickTitleStepNullOnUnknownChar`, `pickTitleStepNullWhenNothingFits` (pure `fun pickTitleStep(cols: Int?, availablePx: Int, candidatesPx: List<Int>): Int?`) | The title chain |

Existing tests must still pass; the only existing test that touches this area is `PxlParserTest` (reads `PixelIcon.entries`), which is unaffected. The look itself (rendered pixels of panels, sheet, note) is proved by the gallery screenshots of the lead (design language 12), not by unit tests.

### 9. Robot hooks

Plain `testTag` strings (NAV sets `testTagsAsResourceId` at the root). `PocketSheet`: `sheet` (panel), `sheet.scrim`, `sheet.handle`, `sheet.title`; `ActionSheet` rows `sheet.action.0`, `sheet.action.1`, and so on. `PocketDialog`: `dialog` (panel), `dialog.title`; `ConfirmDialog`: `dialog.confirm`, `dialog.cancel`. `PetNoteHost`: `petnote` (the note), `petnote.action`. `SwipeRow` rows: the caller's tag; its custom actions carry the action labels. `Skeleton`: `skeleton`. `EmberLine`: `ember`. `DesignGallery`: root `gallery`, and a button per overlay: `gallery.sheet`, `gallery.dialog`, `gallery.note`, `gallery.note.error`, `gallery.note.action`; a text-scale and a layout-direction switch are NOT here (the robot forces those with `adb shell settings put system font_scale 2.0` and the NAV debug link for a mirrored layout). Scenarios the robot can run against the gallery: open sheet, expect `sheet`, tap `sheet.scrim`, expectNot `sheet`; `gallery.note` then expect text and `petnote`. Practice-model keywords: none. The twin needs no private file for any of it.

### 10. Experiment branches

None of S1 to S7 changes this section. If S2 fails (R8 breaks something looked up by name): DESIGN has no reflection; the only name-keyed value is `HapticLevel`, stored by `name` and read with `enumValues`, which `-dontobfuscate` already keeps; nothing to change. If S1 or S3 fails (back swipe progress, shared elements): `Springs.glide` and `Ease.enter/exit` are still what NAV plays after the gesture; no change to my symbols. If S4 fails: nothing here.

### 11. Risks, and what in the plan is wrong or impossible

1. "Apart from motion nothing looks different" cannot hold for 0.6: a sheet with stepped corners, a dither scrim, a pixel grabber, a Pêt note instead of a platform toast and a pressed GHOST/icon/chip state ARE visible changes; they are what the component list exists for (`docs/design-language.md:780-797`). I read the constraint as: no screen changes layout, content or colour. Visible differences the owner will see, all intended: sheets (shape, scrim, grabber), dialogs (scrim, pop-in), notes instead of toasts, a pressed ghost button, icon button or chip sinking, haptics on API 34 using the segment tick, pixel font text at non-default text size (A12) and a title that used to be cut with an ellipsis now stepping down. The lead should confirm this reading before the build.
2. A12 says the capital `İ` "loses its dot" in the pixel font. The font draws it (`PixelFont.kt:237`, dot on row 1); the screenshot shows "Ikiniz de...". The cause is a root-locale case change in code that nobody in this table owns (candidates from the survey list F: `ChatScreen.kt:634`, `SettingsScreen.kt:158`, `HermesHome.kt:297`; the chat title is probably built where the first line is capitalised). Needs a lead decision: a one-line fix in the owner of that file, or STREAM/WORDS.
3. `docs/design-language.md:84` and `:93` give the glyph pixel as 3u, 2u, 1.5u and 1.5u, 2u, 2.5u, but u is 2 dp (`:59`) and today's titles use a 2 dp glyph pixel (`PixelText(pixel = 2.dp)` everywhere). Read literally, titles would triple. I used the numbers as dp (1.5, 2, 2.5, cap 3), which equals today at font scale 1. 
4. `fun Modifier.pixelSnap(pixel)` (design language `:148`) cannot round "a graphicsLayer translation": a modifier cannot read a translation set by another one. Replaced by `Modifier.pixelOffset(pixel, offset)` and the pure `snapToDesignPx`.
5. `rim` and `shade` as `PocketColors` roles (`:103`) cannot reproduce today's bevel, which is computed from the panel's own colour, not the theme's. They are the functions `bevelRim` and `bevelShade`. Only `glow` and `scrim` become fields. The `sig*`, `data1..6` and mark colours of `:104-115` are NOT in Wave 0 (no user until Waves 2, 5, 7); say so in the Wave 1 contract.
6. `PaletteContrastTest` "a failing pair fails the build" would break the Wave 0 build the moment a pair fails, and colours must not change. The ratchet list makes it honest: current failures are listed, new ones fail. Likely entries: `outline` against `bg` at 3.0 (the survey could not check; the palette comment only claims text and bubbles).
7. `SectionHeader.kt:37` upper-cases text in code, against the text hygiene of 0.11 (capitals are written by the author). Removing it changes the look (all section labels), so it stays in Wave 0. The lead decides if Wave 1 writes the capitals into the strings.
8. `PixelIconButton` falls back to `icon.name.lowercase()` as its spoken label (`PixelButton.kt:178`): English enum names read aloud. I keep it (a null would be worse); the robot's label check will flag every caller without a `contentDescription`, and fixing them belongs to the owner of each screen.
9. `PocketSheet` as a custom `Dialog` window: edge-to-edge and insets inside a dialog window are the risk (a gap above the navigation bar, the keyboard). It copies `PocketDialog`'s proven `imePadding` setup. Check on the phone first thing; fallback is cut item (c).
10. A note raised while a dialog or sheet is open sits behind its window; handled by the pause in `PetNoteQueue`, but a long list of notes can still queue up behind a dialog that stays open.
11. `Mascot(size = 32.dp)` in the note is snapped to whole pixels by STAGE's `PixelImage`; at densities where 32 dp is not a whole multiple of the 32-pixel sprite it may look soft. STAGE's renderer owns that.
12. With the effective level now in `Pocket.anim`, Compose's own animations already honour the system scale through the window recomposer's motion scale; the new code only fixes the app's own `if (anim == ...)` branches. Not verified on the phone.
13. Kotlin gotchas applied: in `PixelFont.kt` `defV2()` is called last in `init` and the kerning table is declared above `init`; no KDoc may contain `/*` or `*/` (write `strings_*.xml` as "the strings files", never with a glob); no `<string>` value with a leading or trailing space.
