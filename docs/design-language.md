# Hearthlight 2: Agirî's design language (look, motion, touch, sound)

Written 2026-11-24 as part of the road to 1.0 (`docs/phase4-plan.md`); second edition the same day,
with the screens of the creative workspace, research, voice and the world's scripts, and revised on
2026-11-25 after two independent reviews (the pixel grid in real pixels, plain content, the map of
the app, the kinds of screen and their states). This file is the
lasting reference for everyone who touches the interface: it says how the app should look, move, feel
and sound, and why. It extends "Identity and art direction" in `docs/phase1-contract.md` (themes, the
`.pxl` format, Pêt and the fire stay exactly as defined there). Where this file and older code
disagree, this file is the target and the code is what changes. Waves 0 to 2 of the plan build its
foundation (tokens, motion, components, the scene, the chat); the Canvas arrives with Wave 5, the
voice stage with Wave 8, the widgets, the wallpaper and the clock with Wave 10, and the scripts of the
world with Wave 12.

**A character without a flag.** The hearth, the mountain and Pêt are the app's character, and the six
themes keep the names they shipped with. Everything new is named in plain English, nothing in the
interface assumes where its user lives, and days and scenes of a culture or a region are things the
user switches on (section 6.2), never defaults.

The current state it starts from was surveyed on the same day (file and line references are in
`docs/phase4-plan.md`, "What the survey found"). In short: the art engine is good, the screens are
functional, and almost nothing between them is designed: fifteen unrelated animation durations, no
motion, spacing or type tokens (about 600 hard-coded `dp` and 290 `sp` values), no gestures beyond tap
and long-press, rounded and stepped corners mixed on one screen, and a night scene that is cut off with
a hard edge above a cream page in the light themes.

## 1. Four rules

Everything below follows from four rules. When a case is not covered, apply them.

1. **One fire lights everything.** The hearth is the only light source of the whole app, not only of the
   home scene. Surfaces have a lit side and a shaded side, the accent colour is firelight, and the fire
   tells you what the agent is doing: cold when Hermes sleeps, kindling while it wakes, steady while it
   waits, taller and brighter while it works, a puff of smoke when something fails. A person who never
   reads a status text should still know the state of the agent from the fire.
2. **Pixels step, layouts glide.** Pixel art moves the way pixel art moves: in whole logical pixels and
   in few frames. Everything that is layout (scrolling, sheets, pages, the keyboard, shared elements)
   moves with smooth physics at the display's full 120 Hz. A sprite never slides by half a pixel and a
   page never jumps in steps. This one rule is what makes the app feel like a crafted pixel game and not
   like a web page with a pixel skin.
3. **Calm by default, alive on purpose.** A resting screen is quiet: slow loops (2 to 4 frames per
   second), nothing blinking at the edge of the eye. Life comes from small reactions to what the user
   and the agent do, and every event gets exactly one clear flourish. No animation ever blocks input,
   every animation can be interrupted, and frequent actions never take longer than 220 ms.
4. **The frame is pixel, the content is plain.** What the user and the agent make (a document, a
   slide, a chart of real numbers, a photo, a web page) is content, and content is shown cleanly, in
   its own look, at full sharpness. The app's character lives in the frame around it: the panel, the
   bar, the controls, the motion. Pixel styling is never laid over somebody's document, and a made
   thing never has to look like the app in order to be shown by it.

## 2. Tokens

New file `ui/theme/Tokens.kt` (spacing, type, shape, elevation) and `ui/theme/Motion.kt`. After Wave 0 no
screen may contain a raw `tween(…)`, and new code uses the tokens instead of literal `dp` / `sp` values
for anything the tables below name.

### 2.1 Space and shape

The design pixel `u` is two density-independent pixels by intent and a whole number of real device
pixels in fact: `u` = 2 dp rounded to the nearest whole device pixel (5, 6 or 7 on today's phones,
depending on the screen's density setting). Everything drawn in the pixel look (panels, borders,
stepped corners, the pixel font, sprites) is measured in `u`, so it lands on whole device pixels on
every screen and never blurs. Layout spacing stays in dp.

| Token | Value | Use |
|---|---|---|
| `Space.xxs / xs / s / m / l / xl / xxl` | 2 / 4 / 8 / 12 / 16 / 24 / 32 dp | Every gap and padding |
| `Space.gutter` | 16 dp | Left and right screen margin |
| `Space.touch` | 48 dp | Smallest touch target (already true for buttons; made true for message actions) |
| `Corner.chip / card / sheet` | 1 / 2 / 3 steps of `u` | Stepped corners, the only corner style of the pixel look |
| `Border.thin / focus` | 1u / 1u accent plus dithered glow | Outline, and focused or selected state |

- **One shape language.** Stepped corners everywhere. The rounded pieces that crept in (group
  participant pills, the day chip, code blocks, the error card, the action icons' ripple) become
  stepped. Rounded shapes remain only where the user chose them (`BubbleStyle.SOFT`) and on photos.
- **Elevation is light, not blur.** Four levels, drawn with lines and dither, never with a soft shadow:
  0 flat; 1 raised (1u shade line on the side away from the fire, 1u highlight towards it); 2 floating
  (level 1 plus a dithered drop shadow, offset 2u, 25 % coverage); 3 modal (level 2 over a dither scrim).

### 2.2 Type

| Role | Font | Size / line | Use |
|---|---|---|---|
| `Type.display` | Pixel font; one glyph pixel is about 3u, 2u or 1.5u, always a whole number of device pixels | n/a | Screen titles, the greeting, big numbers |
| `Type.title` | System sans, semibold | 16 / 22 sp | Card titles, chat titles that do not fit the pixel font |
| `Type.body` | System sans | 15 / 22 sp | Text, user bubbles |
| `Type.reading` | System sans | 15.5 / 24 sp | Long bot answers (the open layout of section 8.2) |
| `Type.label` | System sans, medium | 12 / 16 sp | Meta lines, chips, captions |
| `Type.mono` | Monospace | 13 / 19 sp | Code, tool output, terminal |

- A title that does not fit the pixel font at 2u drops to 1.5u, then to `Type.title`: it is never cut to
  "Sadece uname…" while half the bar is free.
- The pixel font follows the text-size setting in three steps (about 1.5u, 2u and 2.5u, each rounded
  to whole device pixels) instead of ignoring it. At the largest system text sizes it stops growing
  at 3u and titles wrap instead; nothing is cut at double text size.
- Pixel font v2: every letter of the Latin-script languages the app ships, with its dots and accents
  (the capital İ loses its dot today), tabular digits, a kerning table for the worst pairs, arrows and
  a check mark. Cyrillic follows in Wave 1 and Greek in Wave 12; section 13 says what happens for
  every other script.

### 2.3 Colour

`PocketColors` stays. Added roles: `glow` (firelight for ambient effects), `rim` and `shade` (the two
bevel lines), `scrim`, and six signal colours for the kinds of work the agent does (`sigTerminal`,
`sigFile`, `sigWeb`, `sigMemory`, `sigPhone`, `sigAgent`), all derived from the theme so they never clash.
The nine kinds of step in the work timeline share these six: skills and code use the file colour, the
browser uses the web colour, and the icon tells them apart. Steps that later waves add (a canvas item,
a picture, a reader of a research) reuse the same six in the same way.

Two more families, both derived from the theme and both checked by the contrast test:
- **Data colours** `data1` to `data6` for the series of a chart, the speakers of a transcript and the
  members of a council. Each has a fill pattern as its twin (solid, checker, diagonal, dots, horizontal
  lines, vertical lines), so six of anything can be told apart without colour.
- **Marks** for sources: `markOpened`, `markUnknown`, `markWarn`. Each also has a shape of its own
  (section 8.9), for the same reason.

- **Contrast is tested, not hoped for.** A unit test (`PaletteContrastTest`) checks every theme, dark
  and light, with every accent: body text on each surface at least 4.5:1, large text, icons and outlines
  at least 3:1. A failing pair fails the build.
- **True black.** A switch "Black night" turns the background of dark variants to `#000000` and lifts
  the surfaces one step (the phone has an AMOLED screen).
- **Wallpaper accent** (Android 12 and later): one more `AccentId` that takes the system accent colour.

### 2.4 Motion

```kotlin
// ui/theme/Motion.kt
object Dur { const val TAP = 90; const val FAST = 150; const val BASE = 220; const val SLOW = 320; const val SCENE = 480; const val EPIC = 900 }  // ms
object Ease {
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)        // most things
    val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)      // things arriving
    val exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)       // things leaving
    fun steps(n: Int): Easing                                 // n equal jumps: pixel movement
}
object Springs {            // layout physics
    val snappy  // stiffness 1400, damping 0.90: presses, toggles, small returns
    val glide   // stiffness  380, damping 0.86: sheets, pages, shared elements, following a finger
    val bouncy  // stiffness  600, damping 0.55: celebrations only
}
/** What a screen asks for. Resolved from the effective animation level, so call sites never branch on it. */
class MotionScheme(val level: AnimLevel) {
    fun <T> tap(): FiniteAnimationSpec<T>; fun <T> fast(): FiniteAnimationSpec<T>
    fun <T> base(): FiniteAnimationSpec<T>; fun <T> slow(): FiniteAnimationSpec<T>
    fun <T> glide(): FiniteAnimationSpec<T>; fun <T> pop(): FiniteAnimationSpec<T>
    val particles: Boolean; val parallax: Boolean; val shaders: Boolean; val sceneFps: Int   // 30 / 10 / 0
}
val Pocket.motion: MotionScheme @Composable get
fun Modifier.pixelSnap(pixel: Dp = 2.dp): Modifier     // rounds a graphicsLayer translation to whole design pixels
```

**Effective animation level** = the lower of three things: the app setting (FULL / REDUCED / OFF), the
system setting (animator duration scale 0 means OFF; the app ignores it today), and battery saver (at
most REDUCED). REDUCED means: fades up to 150 ms, no parallax, no particles, no shaders, scene at 10
frames per second. OFF means: everything snaps, one still frame.

**Choreography.** At most one hero animation at a time. Staggers are 30 to 40 ms and cover at most six
items. An entrance never exceeds 400 ms in total and never delays input. Long flourishes (`SCENE`,
`EPIC`) are only for rare moments: the first launch, lighting the fire, a celebration. One thing
looks like an exception and is not: the kindling on Home, the first time the app opens in a process,
follows the real seconds Hermes needs to start (section 8.1). It is a progress display, not an
entrance, and the screen can be used from its first frame while it plays.

## 3. Motion patterns

The complete catalogue. A new interaction picks one of these instead of inventing a timing.

| # | Pattern | Specification |
|---|---|---|
| 1 | **Press** | A pressable pixel surface sinks 1u down and loses its shade line within one frame, and returns with `snappy`. Today the sink is instant both ways, and filled buttons sink without a ripple while the others ripple without sinking |
| 2 | **Appear / vanish** (small things: chips, badges, the jump button) | Scale 0.9 to 1 in three steps with a fade, `FAST`; vanish in `TAP` |
| 3 | **List changes** | `Modifier.animateItem()` on every list (chats, bots, routines, saved messages, settings): fade `FAST`, placement `glide`. Never on a row whose text is still streaming |
| 4 | **Sheet** | Rises with `glide`, follows the finger, flings away by velocity. Behind it the dither scrim (pattern 6). One sheet component for the whole app instead of five differently styled ones |
| 5 | **Dialog** | Panel pops in (pattern 2) over the dither scrim |
| 6 | **Dither scrim** | The signature of the app. Instead of fading a black layer, a 4x4 ordered-dither pattern fills in: 0 to 50 % coverage in four steps over `FAST`. Drawn as one tiled bitmap, works on every Android version |
| 7 | **Push / pop a screen** | Incoming: 24 dp to 0 with a fade, `BASE`, `Ease.enter`. Outgoing: fades and moves 8 dp the other way. With the back gesture the outgoing screen follows the finger, shrinks to 94 % and reveals the screen below, then settles with `glide` (if experiment S1 of the plan passes; otherwise the same motion plays after the gesture) |
| 8 | **Tab switch** | Content cross-fades in `FAST`. In the bottom bar the ember leaves the old tab and lands on the new one along a small arc in five steps, trailing two sparks. The new tab's icon plays its 3 to 4 frame animation once |
| 9 | **Shared elements** | The same thing stays the same thing: a chat row's avatar and title travel into the chat's top bar; a bot card into the editor's header; the home prompt into the first bubble; a thumbnail into the image viewer. `glide`, pixel-snapped at rest |
| 10 | **Collapsing hero** (Home) | The scene shrinks from about 46 % of the screen to a 96 dp "hearth strip" while scrolling; its layers move at different speeds (sky 0.35, ridges 0.6, ground 1.0); the greeting cross-fades into the strip's title. The fire never leaves the screen |
| 11 | **Streaming text** | Text is released from a smoothing buffer every frame at a speed that follows the backlog (section 8.2), the last few characters fade in, and an ember caret flickers at 6 frames per second |
| 12 | **Thinking** | A dithered highlight band sweeps over the word "Thinking…" every 1.4 s in twelve steps, and the fire in the top bar's status line burns higher |
| 13 | **Work timeline** | Each tool step slides in from 8 dp below in `FAST`; the running step shows a four-frame ember spinner and a live timer; a finished step draws its check mark in three frames; when the answer starts, the timeline folds to one line with `glide` |
| 14 | **Send** | The bubble flies out of the input field (shared bounds, `BASE`); the field empties with a one-frame flash; the send button turns into the stop button in two steps |
| 15 | **Celebrate** | Twelve embers burst and fall within 600 ms, with the `rise` haptic. Only for: the first answer ever, a routine or bot created, a backup finished, a keepsake earned. Never for ordinary replies |
| 16 | **Fail** | The affected element shakes twice by 2u in 180 ms, with the `double` haptic; on Home the fire coughs a puff of soot |
| 17 | **Counter** | A changing number (tokens, unread, budget) rolls its digits by one step in `FAST` |
| 18 | **Loading** | Dither-shimmer blocks in the shape of the content. No spinners on empty screens |
| 19 | **Idle life** | Avatars blink every 3 to 6 s; Pêt lives (section 6); fire at 8 to 12 frames per second |
| 20 | **Keyboard** | The input bar and the list ride the keyboard's own animation frame by frame |
| 21 | **Version scrubber** | A row of ticks, one per version, the current one lit. Dragging moves a lens along the row with a `TICK` per version; text content cross-fades in `FAST` while the changed lines flash once in `glow`; pictures and pages change with a wipe that follows the finger. Letting go settles with `snappy` |
| 22 | **Canvas rise** | The card's bounds become the sheet's (shared bounds, `glide`); the sheet follows the finger between its two rests (half and full) and flings by velocity. Going full, the chat does not vanish: it shrinks into the one-line bar at the bottom |
| 23 | **Suggestion** | Added text arrives on a band of `glow` that fades to nothing in `SLOW` once accepted. Rejected or removed text burns away to the right in six steps, leaving three embers that fall. One hunk at a time, never the whole page at once |
| 24 | **Slide change** | Three choices and no more: a cut (default), a fade (`BASE`), and the dither dissolve (pattern 6 run from 0 to 100 % over two `FAST`). The same three in the app and on the second screen |
| 25 | **Camp lights** | A scout walks in two-frame steps along a dotted path at four frames per second; when its notes arrive the camp's window lights in three frames and one spark rises. Counters roll (pattern 17) |
| 26 | **Price** | A price card rises like any card (pattern 2), its amount rolls into place once (pattern 17), and then nothing on it moves. Creating plays no celebration |
| 27 | **Level** | A voice's loudness is drawn as stepped bars, eight levels, updated at thirty frames per second; a recording is a steady square that never blinks |
| 28 | **Weather turns** | Rain, snow, fog and cloud are layers that fade in and out over `SCENE`; a change of weather is never a cut, and never louder than the fire |

## 4. Touch

One haptic vocabulary, exposed as `Haptics.play(HapticCue)`, built on `View.performHapticFeedback` (no
permission, follows the system's touch-feedback setting) with a richer pattern where the phone supports
vibration primitives. Setting: Off / Subtle (confirm, error and long-press only) / Full.

| Cue | When | Platform constant (fallback on older Android) |
|---|---|---|
| `TICK` | Chip, switch, list selection, slider step | `SEGMENT_FREQUENT_TICK` (`CLOCK_TICK`) |
| `CLICK` | Button | `VIRTUAL_KEY` |
| `SEND` | A message leaves | `CONFIRM` (`VIRTUAL_KEY`) |
| `RISE` | The answer is complete (only while the chat is on screen), a celebration | `CONFIRM`, or the primitives quick-rise plus tick |
| `DOUBLE` | An error, a refused action | `REJECT` (`LONG_PRESS`) |
| `HOLD` | Long-press menu opens, a drag crosses its threshold | `LONG_PRESS`, `GESTURE_THRESHOLD_ACTIVATE` |
| `STOKE` | Tapping the fire | Three low ticks, 40 ms apart: a crackle |
| `PULSE` | Voice mode starts listening | One soft low tick |

Gestures become part of the language (none exist today beyond tap and long-press):

- **Back** by swiping from the edge, with the screen following the finger (pattern 7).
- **Chat row:** swipe right to pin or unpin, swipe left to archive; a long swipe left deletes after an
  undo bar. The row shows the action's icon growing as the threshold nears, with `HOLD` when it is crossed.
- **Message:** swipe right to quote it in the next message; double-tap to star; long-press for the menu.
- **Fire:** tap to stoke; tap a cold fire to light it; press and hold a burning fire to put it out (a
  ring of embers fills while holding, so it cannot happen by accident).
- **Sheets** drag down to close; the image viewer drags down to close and pinches to zoom.
- **Canvas:** drag the card's sheet up for the full canvas and down to return it to the chat; drag
  along the scrubber to travel through versions; pinch to zoom a page, a diagram or a slide.
- **Slides:** swipe to move on; hold for the pointer; drag a slide on the light table to reorder it.
- **The sky:** tap a bright star or a planet for its name; tap the sky for the weather line.

Every one of these has a labelled control that does the same (rule 6 of the plan).

## 5. Sound

The ten cues stay. Added: `WAKE` (the fire catches, a soft whoosh), `SLEEP` (a hiss), `LISTEN` and
`HEARD` (voice mode), `ROUTINE` (a small bell for a scheduled result that arrives while the app is open),
`SCROLL` (paper, for a saved file) and an optional looping `CRACKLE` on the home screen and in voice mode
(off by default, very quiet, pauses with the screen). The second edition adds `PAGE` (a slide turns),
`CAMP` (a camp of a research lights up), `REC_ON` and `REC_OFF`, and two more optional loops that
follow the weather, `RAIN` and `WIND`. A price card is silent on purpose.

Rules: CC0 sources only, each recorded in `NOTICE`; a cue is at most 0.7 s long; nothing plays while the
ringer is silent or the app is in the background (both already true); the loop fades in and out over
400 ms and stops when anything else starts playing.

## 6. The living scene

The home scene becomes a small engine with a clear contract, because many other places will use it:
the collapsed strip, voice mode, the onboarding, the home-screen widget, the live wallpaper and the
bedside clock.

### 6.1 Architecture: `PixelStage`

Today the scene is drawn as hundreds of rectangles per frame through Compose's draw scope, which is why
it cannot be reused outside a composable. New design: every layer draws into one small logical
framebuffer (an `IntArray`; 180 x 160 pixels for the home hero), which is copied into a bitmap and
scaled to the screen once, with nearest-neighbour filtering.

**Whole numbers only, and one scale per layer.** A scene pixel is a whole number of device pixels,
chosen per surface so that the scene covers it; what is left over is filled by more sky above and
more ground below, never by stretching. The framebuffer therefore follows the surface: about 180 by
160 for the home hero, taller for the full-screen surfaces (the talk, the Welcome, the wallpaper,
the bedside clock), smaller for a widget. Inside one layer every sprite has the same scale. A
16-pixel Pêt in a top bar and a 32-pixel Pêt on Home are two sprites, not one sprite at two scales.

```kotlin
// ui/pixel/stage/
class PixelStage(val width: Int, val height: Int) { val pixels: IntArray; fun blit(sprite, x, y); fun plot(x, y, argb); fun toBitmap(scale: Int): Bitmap }
data class SceneState(val hearth: HearthState, val intensity: Float, val sky: SkyState, val season: Season,
    val theme: ThemeId, val pet: PetPose, val wind: Float, val event: SceneEvent?, val budgetLeft: Float?)
object SceneRenderer { fun render(stage: PixelStage, state: SceneState, timeMs: Long, quality: SceneQuality) }   // pure Android, no Compose
@Composable fun HearthScene(...)      // the existing entry point, now a thin wrapper around the renderer
```

The renderer has no Compose dependency, so a widget or a notification can ask it for a still picture.
It runs at 30 frames per second at most (pixel art gains nothing from 120), pauses whenever it is not
visible, and costs a fraction of a millisecond per frame.

### 6.2 The sky follows the real sky

- **Sky clock.** `SkyState` is computed from the phone's clock and a place. The place needs no
  permission: by default it is guessed from the phone's time zone (a small table of zones with a
  typical latitude and longitude), the user can pick a city from an offline list, or allow the phone's
  approximate location. The sun's height gives one of seven moments (night, dawn, sunrise, day, golden
  hour, sunset, dusk) and a smooth blend between their palettes. The sun and the moon travel on their
  real arcs, and two layers of pixel clouds drift by day.
- **Real stars.** About 900 stars down to magnitude 4.5, each at its true place for the hour and the
  place, in three sizes (one pixel; one bright pixel; a plus of five) and three tints (blue-white,
  white, warm). They turn slowly through the night and fade at dawn. The four bright planets are
  steady points that do not twinkle. A tap on a bright star or a planet shows its name in a small
  Pêt note. The sky is the one part of the scene that is never invented.
- **Tonight's real moon.** The moon's phase is calculated from the date (synodic month of 29.53 days)
  and drawn in one of sixteen shapes, at its real place in the sky. A full moon lights the meadow a
  little.
- **The weather, if the user allows it.** Five layers that can be combined: cloud (three covers, and
  stars dim behind them), rain (two strengths, slanted by the wind, small splashes on the stones),
  snow (flakes that drift and settle as a white line on the ridges), fog (a dithered band that eats
  the far ridges), and wind (sparks and grass lean, the flame bends). Far lightning is two frames of a
  lit horizon, at most once in twenty seconds. A tap on the sky shows one line, "11°, light rain",
  with the credit to the weather service. With the weather off the sky is simply clear.
- **Seasons.** By the date and the hemisphere: flowers in the grass in spring, golden grass in summer,
  russet trees in autumn, snow on the ground in winter and Pêt's breath as small puffs. Can be pinned
  or switched off.
- **Days.** A table of dates changes the scene, in three groups. *The sky's own days* are on by
  default: the solstices and equinoxes (a low gold sun, the longest shadows), the large meteor showers
  with real shooting stars, full moons, eclipses. *The app's and the user's days:* the anniversary of
  the first launch, when Pêt wears a paper hat, and days the user adds. *Days of a culture or a
  region* are lists the user switches on, each with one small touch in the scene (lanterns, lights,
  bonfires on the ridges, distant fireworks; the spring bonfires the app has shown since its first
  version are one of these lists). None of the third group is on by default. The table is data, so adding a day
  is one line.
- **Setting.** "Sky: follows the day / always night / always day". The light themes finally get a
  scene that belongs to them, and the hard edge between the scene and the page becomes a dithered
  horizon in the page's own colour.

### 6.3 The fire

- **Hand-keyed core, living edge.** The five hand-drawn flame sprites remain the body of the fire (they
  give it character). Around them a tiny simulation adds what frames cannot: flame tongues that never
  repeat, sparks that follow the wind, a glow that breathes. The flame leans with the wind value, which
  comes from tilting the phone and from scrolling fast.
- **The fire reacts to the agent.** Intensity is a continuous value, not five states: each streamed
  piece of text feeds it a little, a running tool makes it roar, an idle minute lets it settle. In voice
  mode its height follows the loudness of the speaking voice.
- **Firewood is the budget.** A woodpile beside the hearth shows what is left of today's budget (counted
  in tokens today, in money once Wave 4 knows prices): full in the morning, shrinking with use, three logs when the warning is near, empty when blocked. The
  one number the owner cares about most becomes visible without reading a number. No budget set means
  no woodpile.
- **Heat haze** (Android 13 and later, FULL only): a small shader bends the air above the flame.

### 6.4 The scene is the control

The status card under the scene ("Hermes is awake and ready" with a full-width button) goes away. The
state is told by the fire and a short line under the greeting, and the fire itself is the switch
(section 4). For screen readers and for clarity the same action stays reachable as a labelled button in
the home screen's menu, and the first-run wizard teaches the gesture once.

### 6.5 One scene per theme

Each theme owns a variant of the scene, built from the same layers with its own palette and one or two
props: **Newroz** the bonfire night (today's scene); **Zagros** snow and pines under a cold sky;
**Semender** a stream between rocks with glowing mushrooms, Pêt's home; **Nergiz** a spring meadow full
of narcissus; **Kawa** the blacksmith's forge with an anvil and flying sparks; **Roj** a sunrise with
the sun climbing behind the mountain.

**Scene packs** (Wave 12) add other places built from the same layers: a sea coast with a lighthouse
far out, a steppe under a wide sky, a desert with dunes and a well, a forest clearing, a city's roofs
at night. Every pack works with every theme, every season, the real sky and the weather. The fire,
the woodpile and Pêt are the same everywhere: the place changes, the hearth does not.

### 6.6 The scene at work

The scene tells what the agent is doing even when the user is not asking.

- **A research:** the path to the camps of section 8.9 leaves the hearth towards the ridges. While a
  research runs, small lights move along it; on Home, the far camps that are lit can be counted.
- **Night shift:** the fire is kept low and a lantern hangs at the edge of the scene. Each finished job
  leaves a scroll beside the woodpile; in the morning Pêt holds them.
- **Waiting for the network** (the outbox): a small hourglass stands on a stone by the fire.
- **A focus log:** one marked log lies across the fire and burns down along its length over the
  chosen time, like a slow progress bar that nobody has to read.
- **A private chat:** the flame burns in a cool colour, and no sparks leave it.

### 6.7 The wallpaper and the bedside clock

Both are drawn by `SceneRenderer`, so they are the same world.

- **The live wallpaper** is the scene without controls: sky, ridges, hearth, Pêt. The lower third is
  kept dark and quiet, because the phone's own icons and dock sit on it. It shifts by a few pixels as
  the home pages are swiped (the sky least, the ground most). It moves for four seconds after the
  screen turns on and then holds a still frame that is redrawn once a minute. It never draws while
  hidden.
- **The bedside clock** is the night scene at a fifth of its brightness, on true black: the fire as
  embers, the real stars turning, the time in the pixel font at 6u, the next alarm or task in
  `Type.label` beneath it, and one line when the night shift works ("2 jobs running"). Every element
  drifts by one design pixel a minute along a small loop, so nothing burns into the screen. A tap
  brightens it for five seconds.

### 6.8 Keepsakes

Small objects, 8 by 8 to 12 by 12 pixels, that appear in the scene for things that really happened: a
lantern for the first research, a book on a stone for the first document, a flag on the ridge for a
study deck learnt to the end, a second log bench after a year. At most seven are shown at once (the
newest; the rest are on the shelf). They never glow, never count days and never ask for anything. A
tap shows one line: what it stands for, and when.

## 7. Pêt

Pêt stops being a sprite that changes with the engine state and becomes a small character.

- **A brain with moods.** Every 4 to 9 seconds `PetBrain` picks something to do from weighted options
  that depend on the moment: blink, sway the tail, look into the fire, warm the hands, yawn, stretch,
  chase a firefly, poke the fire with a twig; read a scroll while a tool runs; write in a notebook when
  a memory is saved; carry a scroll with a badge when a routine's answer waits (tap it to open); sleep
  when the fire is out, wake with a stretch; jump at an error; dance at a celebration; wave hello.
  One reaction at a time, queued, never interrupting itself.
- **New animations** (32 x 32, 4 to 8 frames each): `pet_blink`, `pet_tail`, `pet_warm`, `pet_yawn`,
  `pet_stretch`, `pet_firefly`, `pet_poke`, `pet_read`, `pet_write`, `pet_carry`, `pet_startled`,
  `pet_dance`, `pet_listen` (voice mode). Plus a shadow that lengthens away from the fire.
- **Pêt and the world** (second edition): `pet_shelter` (under a leaf when it rains), `pet_snow`
  (catching a flake), `pet_lantern` (keeping watch in the night shift), `pet_scrolls` (holding the
  night's results in the morning), `pet_headphones` (while the recorder runs), `pet_point` (beside the
  timer in the presenter's view), `pet_sleep_deep` (the bedside clock). Wave 1 draws fifteen: the
  thirteen above and the weather's two. The other five arrive with the waves that use them
  (`pet_point` in Wave 6, `pet_headphones` in 8, `pet_lantern` and `pet_scrolls` in 9,
  `pet_sleep_deep` in 10).
- **Pêt outside Home.** A 16 x 16 Pêt may look over the top bar of a chat while the agent works (a
  setting, on by default). Every empty state gets its own little picture (an empty speech bubble, an
  hourglass, a jar of stars, a lantern for "nothing found"). And the plain Android toasts (twelve call
  sites) are replaced by **Pêt notes**: Pêt's head and a speech bubble that rises from the bottom,
  stays two seconds and can be swiped away.

## 8. Screens

### 8.1 Home

```
┌──────────────────────────────────┐
│ 21:02                     ▂▄▆ 90 │  status bar over the sky; icon colour follows the sky's brightness
│   ·    ✦          ☾              │  tonight's real moon
│  ✦   ·     ·   ✦      ·          │
│   ▲▲        ▲▲▲ Agirî      ▲▲    │  ridges: parallax on scroll and on tilt
│ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ │
│     (Pêt)   🔥    ≡≡≡ woodpile   │  fire = state and switch; woodpile = today's budget
│ Good evening          Tue 24 Nov │  greeting in the pixel font, on the scene
│ The fire is burning.             │  one plain status line
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │
│ │ Ask anything…          🎤  ➤ │ │  the prompt card overlaps the scene's lower edge
│ └──────────────────────────────┘ │
│ (Continue: Trip plan) (Morning   │  three suggestions that change with the moment
│  brief) (What can you do?)       │
│                                  │
│ Around the fire                  │  headings in sentence case, written so by the author
│  (Hermes) (Rebel) (Coder) (+)    │  avatars in a snapping row, each with an idle blink
│                                  │
│ Recent                           │
│  ▢ Routine test       16 min  ①  │  rows with swipe actions
│  ▢ Hello              22 min     │
│                                  │
│ ┌───────────┐ ┌───────────┐      │
│ │ Routines  │ │ Saved     │      │  illustrated tiles (24 x 24 art), not monochrome icons
│ │ next 08:00│ │ 3 scrolls │      │
│ └───────────┘ └───────────┘      │
└──────────────────────────────────┘
```

Scrolled, the scene is a 96 dp strip: the fire at the left, "Agirî" and the status line beside it.
The first time the app opens in a process, the scene assembles itself (sky, then ridges rising, then
the fire catching, timed to the real ten seconds Hermes needs to start) and the cards rise in a short
stagger. The screen can be used from its first frame: the kindling is progress, not an entrance
(section 2.4). Every later visit shows the finished screen at once.

Later waves add rows to this screen, and each row appears only when it has something to show:
"Spaces" (from Wave 2, section 8.10), "Due today" (tasks and study cards), "Pinned" (canvas items),
and in the morning the night's report. Home never shows an empty row, and never more than five rows
before the tiles. Under it the bottom bar has five tabs (section 8.15); Settings is a gear at the
top of the screen.

### 8.2 A chat

```
┌──────────────────────────────────┐
│ ← (av) Trip plan            ⌕  ≡ │  title; the subtitle is the live status
│        thinking…  ▁▂▃▅▃▂         │  a thin line of fire under the bar burns while the agent works
├──────────────────────────────────┤
│                  ╔═════════════╗ │
│                  ║ user message║ │  the user: an ember-coloured bubble on the right
│                  ╚═════════════╝ │
│ (av) Hermes                      │
│ ▸ Worked 12 s · 3 steps        ⌄ │  the work timeline, folded
│ The answer runs the full width   │  the bot: no box, the full width, `Type.reading`
│ of the screen like a page…       │
│ ┌ bash ───────────── ▶ Run  ⧉ ┐  │  code: stepped frame, scrolls sideways, never wraps code
│ │ uname -a                    │  │
│ └─────────────────────────────┘  │
│ 21:58 · 8 s · 1.2k in · 340 out ⋯│  one quiet meta line; actions open from ⋯ or a long-press
├──────────────────────────────────┤
│ ⊕ │ Message…                │ 🎤 │  the bar grows with the text; send and stop are one morphing button
└──────────────────────────────────┘
```

- **Open layout for answers.** A long answer in a bordered box 84 % wide, beside an avatar column,
  wastes a third of the screen and makes every reply look like a form. The default becomes: bot answers
  without a box across the full width, user messages as bubbles. `BubbleStyle` gets a third value,
  `OPEN` (the new default); `PIXEL` and `SOFT` stay for those who prefer boxes.
- **Smooth streaming.** The engine delivers text in 60 ms lumps, which is visible as stutter on a 120 Hz
  screen. A smoothing buffer in the UI releases characters every frame: speed = backlog / 0.35 s, never
  slower than 40 characters per second, flushed within 150 ms when the stream ends, cut only at safe
  places (never inside a surrogate pair or a Markdown marker). The last eight characters fade in.
- **Incremental Markdown.** Blocks that are finished are parsed once and never again; only the block
  still being written is re-parsed. Today the whole message is re-parsed on every update.
- **One line of actions.** The row of four icons under every answer becomes a single ⋯ on older answers
  and stays as a row only under the newest one.
- **Quiet extras.** A floating day label while scrolling; the jump-to-latest button shows how many
  messages arrived; tapping the title scrolls to the top; an ambient glow of `glow` colour, dithered,
  breathes at the bottom edge of the screen while the agent works (rule 1 outside the home screen).

### 8.3 Voice: Fireside

```
┌──────────────────────────────────┐
│ ✕                       Hermes ▾ │
│     ✦      ·     ☾        ·      │
│   ▲▲▲          ▲▲▲▲              │
│           (Pêt listens)          │
│               🔥🔥               │  the flame's height is the speaking voice
│             · ∘ ○ ∘ ·            │  a ring of embers is your own voice
│  "…and the lantern was still     │  captions (can be switched off)
│   burning when they came back."  │
│  ( ⏸ )   ( ◉ look )  ( ● )  ( ⌨ )│  pause · look · talk / interrupt · back to typing
└──────────────────────────────────┘
```

The full-screen scene is the whole interface. Nothing else moves. "Look" opens the phone's own
camera for one photo; the app has no viewfinder of its own, because that would need the camera
permission (plan, feature 8.13). Back on the stage, the photo that was sent stands beside the fire
at the size of a playing card, with "1 picture sent" beneath it, and fades after a few seconds.

### 8.4 First run: Welcome

Five steps on one continuous scene that grows lighter with each: choose the language (the phone's own
first, each language under its own name); meet Pêt; connect a model (with a plain guide to a free
key, and one sentence on what a free key means); the kindling, where the progress of unpacking is the fire catching while Pêt shows in short
lines what the app can do; the first hello, or a guided chat that needs no key at all. Two questions
are asked on the way and both can be skipped: may the sky show the weather, and which days should the
scene know. It can be left at any step and never appears again.

### 8.5 The Canvas

```
 a card in the chat          the half sheet               the full canvas
┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
│▤ Three days in Lisb… │   │ ← (av) Trip plan     │   │▤ Three days in…   v3 │
│ # Three days in Lis… │   │ …the chat stays live │   │ ╷╷╷╷┃╷╷  Edit  ⇪  ⋯  │
│ Day 1: Alfama, the   │   ├───────── ▂▂ ─────────┤   ├──────────────────────┤
│ castle, fado at…     │   │▤ Three days in…   v3 │   │ # Three days in      │
│ ▁▂▃▅ writing…     v1 │   │ # Three days in Lisb │   │   Lisbon             │
└──────────────────────┘   │ Day 1: Alfama, the   │   │ Day 1: Alfama, the   │
                           │ castle, fado at…     │   │ castle, fado at…     │
                           └──────────────────────┘   ├──────────────────────┤
                                                      │ Ask for a change… ➤  │
                                                      └──────────────────────┘
```

- **The card** is a raised panel with a kind tab on its upper left: a 12 by 12 two-tone icon on a
  stepped tab, one for each of the twelve kinds. Its preview has a fixed height of 96 dp: the first
  lines for text, a picture for everything visual (what runs in the sandbox is shown as a picture of
  itself and runs live only in the full canvas). Its foot shows the ember line and
  "writing…" while the item streams, and the version and size afterwards.
- **Paper.** Inside the frame, content lies on paper: the theme's lightest surface by day, a warm dark
  paper one step above the background by night, never pure white in the dark. A document can be
  switched to white paper by those who want to see it as it will print.
- **The half sheet** takes 62 % of the height. There is no scrim: the chat above it stays live and can
  be typed into. **The full canvas** keeps one line of chat at the bottom. Between the two the sheet
  follows the finger (pattern 22).
- **The keyboard wins.** A half sheet, a chat and a keyboard do not fit on one phone screen. When
  the keyboard opens, the sheet drops to a peek of 96 dp (its bar and two lines of the item); it
  rises again when the keyboard closes.
- **The bar:** the title in `Type.title`, the scrubber (pattern 21) as a row of ticks with the current
  one lit and named versions flagged, then Edit, Share and More.
- **Select and ask:** a raised row of chips floats above the selection (Improve · Shorter · Longer ·
  Simpler · Fix · Translate · Explain · Ask…) and scrolls sideways.
- **Review changes:** added text on a band of `glow` with an underline in the accent; removed text
  struck through in `shade`; each hunk has two small buttons in the margin (✓ and ✕); pattern 23 plays
  on each decision.

How each kind looks (rule 4: the content is plain):

| Kind | Look |
|---|---|
| Document | A column of 62 to 70 characters at most; headings in three sizes (22, 18 and 16 sp) in the paper's own ink, with nothing from the app's theme on the page; the table of contents as a side sheet; footnotes at the foot |
| Code | `Type.mono`, line numbers in `shade`, a wrap switch; the console under it is an inset panel that ends with a chip for the exit code |
| Page, app | Live only in the full canvas, in a device frame one design pixel wide with chips for the width (Phone, Tablet, Wide); a picture of itself in the card and the half sheet; the strip of the page's own errors under it, and "Fix it" |
| Drawing, diagram | On paper, with pan and zoom. A diagram is drawn in the ink of the paper it stands on (dark on light paper, light on dark) with one neutral accent; its own rounded shapes are left as they are |
| Chart | **Plain by default:** clean bars, lines and slices in the colours of the document or deck the chart belongs to, each series with its pattern (section 2.3), thin axes, labels in `Type.label`. **The pixel chart** is a second style: bars a whole number of design pixels wide with 1u gaps, lines drawn in steps with square markers, a pie as a stepped circle of at most six slices, bars that grow once in six steps. It is the style of the app's own figures (Usage, the recap) and of the Hearth deck theme, and a choice for everything else; it is never the default for the owner's content. In both styles a tap shows the value in a small raised bubble |
| Table | A raised header row that stays; every other row tinted by 6 %; numbers right-aligned in tabular digits; the sorted column with a three-frame arrow; the chosen cell in `Border.focus` |
| PDF, picture | The page or the picture on a neutral ground, a strip of pages, pinch to zoom |

### 8.6 Slides

A slide is content. Its look comes from the deck's theme, never from the app's theme. Charts, tables
and pictures on a slide are plain; only Hearth uses the pixel chart.

| Deck theme | Ground | Title | Text | Accent | Title type | Mark |
|---|---|---|---|---|---|---|
| Hearth | `1B1410` | `FFB454` | `F2E6D8` | `E2552B` | The pixel font | A stepped block in the corner |
| Paper | `FFFDF8` | `1F1B16` | `3A342C` | `C2410C` | Serif | A thin rule under the title |
| Night | `0F172A` | `F8FAFC` | `CBD5E1` | `38BDF8` | Sans | A bar on the leading edge |
| Mono | `FFFFFF` | `000000` | `222222` | `000000` | Monospace | An underline |
| Mountain | `F1F5F4` | `1F3A3D` | `33494B` | `2F7F6F` | Sans | A ridge line along the foot |
| Blueprint | `0B3C5D` | `FFFFFF` | `D9E8F5` | `F2C14E` | Monospace | A faint grid |
| Bold | `FFD60A` | `111111` | `111111` | `C1121F` | Condensed, heavy | One large block |
| Pastel | `FFF1F2` | `4A3F55` | `5B5165` | `BE185D` | Sans | A soft circle |

Every pair of colours in this table passes the contrast test of section 2.3; the deck themes are part
of `PaletteContrastTest`. A deck has to look the same on a computer, so each theme names two
typefaces: the one the app draws with, and the one the exported file asks for, taken from those that
Windows and macOS both carry (elsewhere the office program puts in its nearest).

| Deck theme | On the phone | In the exported file |
|---|---|---|
| Hearth | The app's pixel font for titles, the system's sans for text | Courier New, bold, for titles (the export sheet says that the pixel font stays on the phone); Arial for text |
| Paper | The system's serif | Georgia |
| Night, Mountain, Pastel | The system's sans | Arial |
| Mono, Blueprint | The system's monospace | Courier New |
| Bold | The system's condensed sans, heavy | Arial Black for titles, Arial for text |

- **Fit, do not overflow.** Margins of 5 %. A title has two lines and shrinks in three steps (40, 34,
  28 points on a 16:9 slide); bullets have six lines and shrink from 24 to 18 points. What still does
  not fit is flagged "overfull" on the light table, with "Split in two" beside it.
- **The light table:** three slides in a row, each with its number. A slide lifted for dragging rises
  to elevation 2 and its neighbours glide apart.
- **Presenting.** Black around the slide; the three changes of pattern 24; the pointer is an ember dot
  with a two-frame flicker. The presenter's view on the phone:

```
┌──────────────────────────────────┐
│ 12:41     ◷ 07:32 / 15:00  4 / 12 │  clock · time used and planned · slide
│ ┌───────────────┐ ┌──────────┐   │
│ │ this slide    │ │ next     │   │
│ └───────────────┘ └──────────┘   │
│ Say why the cycle never ends…    │  notes in `Type.reading`, scrolling
│                                  │
│  ( ◂ )         ( ▸ )        ( ✕ )│
└──────────────────────────────────┘
```

  The timer changes colour in the last minute and never blinks.

### 8.7 Pictures

```
┌──────────────────────────────────┐
│ ▣ A picture                      │
│ "a lighthouse in a storm, as a   │
│  woodcut"                      ✎ │
│ Model …flash-image     Size 1K   │
│ ──────────────────────────────── │
│ About 4 cents       today 0 of 0 │
│      ( Cancel )      ( Create )  │
└──────────────────────────────────┘
```

- **The price card.** The amount is set in `Type.title` and is never smaller than the buttons' text.
  Cancel and Create have the same weight: Create is not the glowing primary button, so it is never
  pressed out of habit. Nothing on the card moves once it has appeared (pattern 26).
- **The Pictures shelf** (of the Library): pictures in stepped frames, two or three to a row, each with a small tag for its
  price or "free" (made on the phone). A long press lifts one for its menu.
- **Pixelize:** before and after as one picture with a seam that can be dragged; under it the grid as
  a slider with five stops, the palettes as chips showing their colours, the dithering as chips.
- **A sticker** gets an outline of one white and one shaded design pixel.

### 8.8 Recipes

A recipe card: a 24 by 24 illustration, a title, one line that begins "You get…", and a chip with its
usual cost. The gallery is a grid of two columns under headings in the pixel font. Starting one opens
its question card; on Send the card folds into the first message (pattern 14).

### 8.9 Research and sources

```
│  ▲        ▲▲      ⌂ prices      ▲        │  camps: dark until their notes arrive
│    ⌂ market   · · ✦     ⌂ reviews        │  scouts carry a light along dotted paths
│ 🔥── · ── · ──⌂ history                  │
│ 14 pages · 9 sources · 6 min · $0.03  ⏸ ■│  counters roll; pause and stop
```

- **The plan card** lists the sub-questions as rows that can be edited, and under them the estimate
  in three figures (pages, minutes, money) in the pixel font. Start has the same weight as Edit.
- **The expedition** is a strip of the scene 120 dp high inside the chat: the hearth at the leading
  edge, a camp per sub-question (12 by 12, a tent with a window), scouts of 3 by 4 pixels (pattern
  25). A tap on a camp opens its notes in a sheet. Folded, it is one line with the counters.
- **A source chip** is a chip one step high with the number in the pixel font. Its mark sits on the
  corner and has a shape as well as a colour: a filled square (opened; with a tick inside when a
  quote matches), a hollow square (not opened), a notched triangle (quote not found), a small page
  (from your own file). Its touch area is 48 dp whatever its drawn size; where chips stand closer
  together than that, a tap opens the list of that sentence's sources.
- **The source card:** the title, the site and the date it was opened, the quoted evidence set as a
  quotation with the matching words marked, then Open and "Read here". One quiet line stands under
  every card: "The mark says the page was read. It does not say the page is right."
- **The reader:** the page's text in the document column of 8.5, the cited passage on a band of
  `glow`, and nothing else.

### 8.10 Spaces

- A space has a cover (a 48 by 32 pixel picture from a set of two dozen, or one of the user's own
  pictures pixelized) and an accent.
- On Home, spaces are a row called "Spaces": each a small shelter by its own little fire, scrolling
  sideways. Entering one cross-fades the accent in `SLOW`, and the top of the screen becomes the
  cover as a band with a dithered lower edge.
- The cost of a space's instructions is shown where they are written, as a counter that rolls while
  the user types.

### 8.11 The interpreter

```
┌──────────────────────────────────┐
│ ¿ɐsǝɯ ɐun uǝuǝᴉʇ?                │  the far half is turned around
│ Español                  ▁▃▅▃▁   │
├──────────────────────────────────┤
│ English                  ▁▂▁     │
│ Do you have a table?             │  large type, the level of the voice beside the name
│          ( hold to talk )        │
└──────────────────────────────────┘
```

Each half has its own tint (the accent, and `data2`), the name of its language in that language, the
sentence in the largest size that fits on three lines, and a level (pattern 27).

### 8.12 The recorder

A calm screen: the time in the pixel font at 4u, the level as stepped bars, the live text beneath it in
`Type.reading` with a chip in a data colour before each speaker's lines. A steady square in the bar,
and the word "Recording". In the transcript afterwards, a sentence that is playing sits on a band of
`glow`, and a tap on any sentence plays from there.

### 8.13 The ledger and the gate

- **The ledger line** under an answer is one row in `Type.label` with icons of 8 by 8 pixels for its
  kinds (the model, a search, a page, a connection, the phone, the app itself). It is never coloured,
  because it is not a warning.
- **The ledger sheet** groups the same kinds, each with its count, and each row can be opened.
- **An approval card** says the reason first, in plain words, and the tool second. Allow and Deny have
  the same weight; "Always allow" is a text button under them. Nothing on it hurries the user.
- **The waiting room** shows what the agent wants to keep with the same marks as "Review changes" in
  the Canvas, so a change to a skill reads like a change to a document.

### 8.14 Outside the app

- **Widgets** are pictures from `SceneRenderer` at a whole multiple of the pixel grid for their real
  size, with at most one line of text in the system's font.
- **The quick question** is a sheet with stepped top corners over whatever app is open, on a dither
  scrim of 25 %. The answer uses the open layout of 8.2 at the same sizes.
- **The rewrite sheet** shows before and after with the marks of "Review changes", and one large
  "Replace".
- **Notifications:** the bot's pixel avatar as the large icon, the status of work as text, never an
  animation.

### 8.15 The map

Five tabs, and no sixth (plan, section 4.1): **Home**, **Chats**, **Library**, **Bots**, **Agent**.
Settings is a gear at the top of Home and of the Agent tab. A wave adds a shelf to the Library, a row
to Home or a section to a screen that exists; it never adds a top-level place.

- **The Library's shelves** are a row of tabs under its title that scrolls sideways, each with its
  count: Made, Files, Pictures, Saved, Reading list, Inbox, Recordings, Cards. A shelf that is empty
  is not shown, except the first two.
- **A space** is a filter chip at the top of Chats and of the Library, and a row on Home. It is never
  a place of its own.
- **The Agent tab** is a list of six rooms, each a row with one figure: Mind, Abilities, Skills and
  packs, Connections, Safety and privacy, Terminal. A switch lives in exactly one of them, and other
  screens link to it instead of repeating it.

### 8.16 Six kinds of screen, and their five states

Most screens of the later waves are not drawn in this file, and do not need to be: each is one of six
kinds, and each kind has one layout.

| Kind | Layout | Examples |
|---|---|---|
| List | A title bar that collapses, filter chips, rows of one height with swipe actions, a floating action only where there is one main action | Chats, the Library's shelves, tasks, routines, recipes, connections |
| Detail, reader | One column of 62 to 70 characters, a bar with the title and at most three actions, everything else under More | A source, a saved page, a memory entry, a file |
| Dashboard | Cards on the grid, each with one figure in the pixel font and one line of text; charts in the pixel style, because the figures are the app's own | Usage, prompt weight, storage, the recap, Safety and privacy |
| Editor | The content, a bar of tools that rides the keyboard, undo and redo, and a line that says "saved" | A bot, a routine, a recipe, a theme, the Canvas editors |
| Stage | The scene full screen, at most four controls along the bottom, nothing else moves | Fireside, the Welcome, the bedside clock, presenting |
| Flow | One question per step, a progress line of whole steps, Back always there, nothing lost on leaving | The Welcome, a research plan, a backup, the move to a new phone |

Every screen is specified in five states, and a contract that lacks one of them is not finished:

| State | What it shows |
|---|---|
| Empty | One of Pêt's small pictures, one sentence that says what will be here, and the one action that fills it |
| Loading | Dither-shimmer blocks in the shape of the content (pattern 18); never a spinner on an empty screen |
| Error | What happened, what was kept, what can be done (section 13.5), with Retry where a retry can help |
| Offline | What still works without the network, said once in a quiet line; nothing is greyed out that works |
| Needs permission | Why, in one sentence, before Android's own dialog; and what the screen does without it |

A wave's contract carries a sketch, in the style of this file, of every new screen that is not one of
the six kinds as they stand.

## 9. Components v2

One implementation each, in `ui/components`, replacing today's duplicates (three private confirm dialogs,
five differently styled sheets, twelve toasts).

| Component | What changes |
|---|---|
| `PixelPanel` | Variants flat, raised, inset, glowing; bevel lines from `rim` / `shade` |
| `PixelButton`, `PixelIconButton` | Press pattern 1 on every kind; the primary button's glow becomes a slow dither shimmer |
| `PocketSheet` | The one bottom sheet: drag physics, dither scrim, stepped top corners, a pixel grabber |
| `PocketDialog`, `ConfirmDialog` | Pattern 5; one confirm dialog for the whole app |
| `PocketSwitchRow`, `PocketChip`, `PocketSlider`, `PocketTabs` | Pixel thumb that slides in steps with a spark; a slider that exists (text scale and speed use chips today) |
| `SwipeRow` | Row with swipe actions (section 4) |
| `PetNote` | Replaces `Toast` |
| `Skeleton` | Dither shimmer placeholder |
| `PixelIconView` | Two-tone icons (a second palette character for the accent pixel) and frame-animated icons: send, star, trash, refresh, bell, mic, check and the five tab icons |
| `EmberLine` | The thin line of fire that shows activity (chat bar, notifications inside the app) |
| `PocketTopBar` | Collapsing variant; hosts shared elements and `EmberLine` |

Added by the later waves, each built once and used everywhere:

| Component | What it is | Wave |
|---|---|---|
| `AskCard` | A question with real controls (one choice, several, text, number, date) and one Send | 2 |
| `ResultCard` | The family of native cards: link, table, file, and later place, event, task, timer, contact | 2, 9 |
| `VersionPager` | "2 / 3" under an answer, and the compare view | 2 |
| `LedgerLine`, `LedgerSheet` | Section 8.13 | 3 |
| `ApprovalCard` | The one card for every ask of the gate, in the chat and as a notification | 3 |
| `CanvasCard`, `CanvasSheet`, `VersionScrubber` | Section 8.5, patterns 21 and 22 | 5 |
| `ChartView`, `DataGrid` | The chart of section 8.5 in its plain and its pixel style, and the table; also used inside slides and by Usage | 5 |
| `ChangeMarks` | Added, removed and decided text (pattern 23); used by the Canvas, the waiting room and the rewrite sheet | 5 |
| `SlideView`, `LightTable` | Section 8.6 | 6 |
| `PriceCard` | Section 8.7; the only way anything with a price is ever asked for | 6 |
| `SourceChip`, `SourceCard`, `MarkBadge` | Section 8.9 | 7 |
| `CampStrip` | The expedition of section 8.9 | 7 |
| `LevelBars`, `MiniPlayer` | Pattern 27; the small player bar with its queue | 8 |
| `KeepsakeShelf` | Section 6.8 | 11 |

Larger pictures: tiles, empty states and the wizard use 24 x 24 illustrated sprites at 2u per pixel.
Small interface icons stay 12 x 12 (crisp at 24 dp).

## 10. Accessibility

- Three checks are made in every wave and not saved for the last one (plan, principle 13): a spoken
  label on every control, a screenshot at double text size, and a mirrored screenshot.
- Everything that can be done by a gesture can be done by a labelled control (the fire, swipe actions).
- A touch target is 48 dp even where the drawn thing is smaller (a source chip, a version tick, a
  mark in the margin).
- Screen-reader pass with TalkBack on every screen, with the reading order written down per screen.
- Text scale reaches the pixel font (section 2.2); layouts are checked at the largest setting.
- Animated content never carries information that is not also in text or a label.
- Contrast is enforced by test (section 2.3).
- Colour is never the only carrier: chart series have patterns, source marks have shapes, speakers
  have names (section 2.3).
- The scene can be read aloud: one sentence that says the hour, the weather and the state of the fire
  ("evening, light rain, the fire is lit"), updated when any of the three changes.
- Sounds have captions where they carry meaning (a result arrived, a recording started).
- A high-contrast theme, and a reading font on request for those who read more easily with one.
- Mirrored layouts for languages written from right to left follow section 13.3.

## 11. Smoothness is a number

"Smooth" is checked, not felt. The phone draws at 120 Hz, so a frame has 8.3 ms.

| Scenario (scripted with adb, measured with `dumpsys gfxinfo`) | Budget |
|---|---|
| Scrolling Home, the chat list and a 200-message chat | At least 95 % of frames within 8.3 ms; none over 33 ms |
| A 2,000-character answer streaming into a long chat | At least 95 % within 8.3 ms |
| Opening a chat, going back, switching tabs | No frame over 16.7 ms during the transition |
| Cold start to the first frame of Home | Under 700 ms |
| Home scene running | Under 1 ms of drawing per frame; 0 when not visible |
| A long document streaming into the Canvas | At least 95 % within 8.3 ms |
| Dragging the Canvas sheet between half and full; dragging the scrubber | No frame over 16.7 ms |
| A table of 3,000 rows, a deck of 40 slides on the light table | At least 95 % within 8.3 ms while scrolling |
| The live wallpaper | No frame drawn while hidden; under one per cent of battery an hour with the screen on |
| Memory at the heaviest moment (a run with readers, a web view, a speech model) | Inside the room measured in Wave 0 (plan, feature 0.12), with a quarter to spare |

The budgets apply to the optimised build (plan, Wave 0): a debug build of a Compose app is several times
slower and is what the owner has been using so far.

## 12. How visual work is reviewed

1. Every interface agent finishes with screenshots of its screens in the default dark and the default
   light theme (Newroz and Nergiz), taken on the phone by the lead, plus one in the stretched test
   language, one at double text size and one mirrored, from Wave 0 on.
2. A reviewer with eyes compares them with this file: tokens used, one shape language, alignment to the
   grid of `u`, contrast, the four rules, the five states of section 8.16, and whether every text has
   the room section 13.2 gives it.
3. Five **taste gates** are the owner's to decide, each shown as pictures side by side before the work
   continues: (a) the fire, hand-keyed with a living edge against the alternatives; (b) the sky
   following the day or always night as the default; (c) open layout or boxes as the default for
   answers; (d) the Canvas panel in its three states; (e) the eight deck themes.

## 13. Type and layout for every language

### 13.1 Three tiers of type

Titles and labels keep the app's character wherever they can; body text has always been set in the
system's font, so reading is the same in every language.

| Tier | Scripts | How |
|---|---|---|
| 1. The app's own pixel font, drawn by hand | Latin with every accent, Cyrillic (Wave 1), Greek (Wave 12) | Glyphs on the 12-row grid, bundled |
| 2. Open pixel fonts as script packs | Chinese, Japanese and Korean with Ark Pixel at 12 pixels; Arabic and Hebrew with Handjet, if its letters join correctly on Android (experiment S41 of the plan) | A download per script, under an open font licence, set at the size where its grid meets ours |
| 3. The system's font | Everything else (Indic scripts, Thai, and any script whose pack is missing or fails) | Semibold, inside the same panels and at the same optical size. Wider spacing between letters only where letters stand apart; never in a script whose letters join or stack |

A screen never mixes tiers inside one word. If a title contains a character its tier cannot draw, the
whole title drops one tier.

### 13.2 Room for words

English is short. A translated label is on average a third longer, and the stretched test language of
Wave 0 is 40 % longer, so a design is checked in it before any translation exists.

| Control | Room (characters, in English) | When the text is longer |
|---|---|---|
| Tab label | 10 | The bar shows icons only, with the label under the chosen tab |
| Button | 22 | The button grows to the width of its row, then takes two lines. A button's text is never cut |
| Chip | 18 | The row of chips scrolls sideways |
| Screen title | 28 | It steps down in size (section 2.2), then takes two lines |
| The first line of a row | 40 | It ends in an ellipsis, and the full text is one tap away |
| A Pêt note | 80 | Two lines; longer notes are a mistake in writing, not in layout |
| A widget's label | 14 | The widget drops the label and keeps the picture |

### 13.3 What mirrors

In a language written from right to left the layout is mirrored: the order of things in a row, the
side a user's bubble sits on, the back arrow, the direction of a swipe, of the scrubber and of a
progress line.

What never mirrors: the scene (east is east), clocks, charts and their axes, code, media controls,
phone numbers, slides (a deck has a direction of its own), and the pictures of the Canvas.

### 13.4 Mixed text

Direction is decided per paragraph by its first strong letter, so an answer can hold a right-to-left
paragraph, a left-to-right code block and a table without one bending the other. Lists and quotations
align with their own paragraph. Numbers, dates, sizes and money come from the platform's formatters
for the chosen language, including its own digits where it has them; code keeps Western digits.

### 13.5 The voice of the text

Written once in English, then carried into every language by its glossary (plan, feature 1.8).

- Speak to the user directly and simply; use the familiar form of address where a language has one,
  unless its glossary says otherwise.
- A button says what happens ("Create", "Replace", "Send to the model"), never "OK".
- An error says what happened, what was kept, and what can be done, in that order, and never blames.
- No exclamation marks, except Pêt's. No idioms and no jokes that depend on English.
- Pêt's lines are written afresh for each language, from a description of Pêt's character and not
  from the English lines; they are a set of the same size, not a translation. The model that
  translates writes them, and nobody claims a human author: a language no speaker has read stays
  marked "machine translated" (plan, feature 1.8).
- Capitals are written by the author and never made by code; headings are in sentence case.
- The English source avoids forms that force a gender on the reader or on the agent. Where a
  language has to choose, its glossary says how.
- Words the app made its own (Canvas, Space, Fireside, ledger, woodpile) are translated once in the
  glossary and then always the same way.
