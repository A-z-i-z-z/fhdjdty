# Wave 0, area STAGE: the stage (feature 0.8) — contract draft

Reads: `docs/phase4-plan.md` 568-766, findings A8 (line 236) and A14 (line 242); `docs/design-language.md` 6.1 and 11.
Real code read at commit 344829f: `ui/pixel/{HearthScene,PixelImage,SpriteBank,Fire,Mascot,Avatars}.kt`, `PxlParserTest`, `dist/craft_art.py`.
Written for the PASS case; no experiment of section 7 changes this area (see section 10).

Ground rules of Phase 3 apply (one owner per file; no Gradle; English texts only; Kotlin gotchas of AGENTS.md: init order, no `*.pxl` inside KDoc, nested block comments).
Extra rules of this area:
- Everything in `ui/pixel/stage/` is plain Kotlin: no `android.*`, `androidx.*`, no `java.awt`. Colours are ARGB `Int`s. A test scans the sources and fails on such an import.
- Every file you do not list in section 2 (anything outside `ui/pixel/`, `tools/art/`, `app/src/test/.../ui/pixel/`) is not yours.
- "Not one pixel different" means: same layers, same order, same Float arithmetic, same blend. It does not mean bit-equal to Skia on translucent pixels (see 11.3).

## 1. Scope

Order of work inside the session (the order matters; step 1 must happen before any edit):

| # | Work item | Core |
|---|---|---|
| 1 | **Capture the "before".** Before editing anything, copy today's drawing code into the test source `LegacyHearthReference.kt` (recipe in section 8). The reference is the oracle; if you edit `HearthScene.kt` first it is lost (the lead can still read it with `git show 344829f:app/src/main/java/dev/pocketagent/app/ui/pixel/HearthScene.kt`). | yes |
| 2 | Split `SpriteBank.kt`: sprite classes and `PxlParser` go to `Sprite.kt` (same package, no import changes anywhere); add pure `SpriteLoader`, `SpriteRaster`; `SpriteBank` gets `preload`, `isReady`, implements `SpriteSource`. | yes |
| 3 | Core in `ui/pixel/stage/`: `Argb`, `PixelStage`, `StageGrid`, `SceneArt` (sky, glow, Bayer), `EmberSet`, `ScenePalette`, `SceneState`, `SceneQuality`, `SceneRenderer`, `FrameGate`. `SceneRenderer.render` is a statement-for-statement port of `drawHearth` and its five helpers (HearthScene.kt:235-459) with `ox = oy = 0` and `p = 1`. Do not improve anything. | yes |
| 4 | Compose side: `StageSurface` (bitmap ring + one nearest-neighbour draw), `rememberSceneClock` (display-tied, capped), `rememberSpritesReady`. `HearthScene` becomes a thin wrapper (same signature, same modifiers). `rememberClock` delegates to the new clock. `SpriteBitmaps.build` delegates to `SpriteRaster`. | yes |
| 5 | Off-main sprite load: `SpriteBank.preload(context)` starts a daemon thread; `init`/`get`/`find`/`all` block until it is done (they already take the same lock), so every call site compiles and works as today. CONFIG calls `preload` from `PocketApp` (request R1). `HearthScene` never blocks composition: it draws nothing until `rememberSpritesReady()` is true. | yes |
| 6 | Parity tests (section 8): new renderer vs. the legacy reference at device resolution, exact equality. | yes |
| 7 | `tools/art/`: `craft_art.py` moved from `dist/` with a CLI, `pxl.py`, `README.md`, `contact_sheet.py`. | yes (README + craft_art); `contact_sheet.py` cut-able |

**Cut first, in this order, if the session runs out:**
1. `tools/art/contact_sheet.py` (and `pxl.py` with it). Loses: one picture of all 112 sprites. Fallback exists: `tools\pixel-preview.ps1 -Sheet` already makes one sheet per `.pxl` file.
2. Lifecycle pause in `rememberSceneClock` (keep the 30 fps gate). Loses: the clock keeps waking a backgrounded app (invalidation only; nothing is drawn while hidden). ROBOT's `gfxinfo` run shows whether it matters.
3. `PixelStage.toBitmap` extension (`ui/pixel/PixelStageBitmap.kt`). Loses nothing now: its first caller is the widget of Wave 10.
Never cut: the parity test, the off-main load, the 30 fps gate, the move of `craft_art.py`.

## 2. Files (STAGE owns these exclusively)

| File | Change |
|---|---|
| `app/src/main/java/dev/pocketagent/app/ui/pixel/Sprite.kt` | NEW. Moved verbatim from `SpriteBank.kt` lines 6-175: `PaletteEntry`, `SpriteFrame`, `Sprite`, `PxlException`, `PxlParser`. One addition to `Sprite`: `internal val rasters: Array<IntArray?> = arrayOfNulls(frames.size)` (cache of plain ARGB frames, pure). No Android import. |
| `.../ui/pixel/SpriteBank.kt` | Keeps only `object SpriteBank` (was 177-259). Now `: SpriteSource`. Adds `preload`, `isReady`. `load` delegates to `SpriteLoader`. |
| `.../ui/pixel/SpriteLoader.kt` | NEW, pure: the loading rules of `SpriteBank.load` (skip a broken file, report duplicates). |
| `.../ui/pixel/SpriteRaster.kt` | NEW, pure: sprite frame to ARGB `IntArray` (the arithmetic of `SpriteBitmaps.build`, PixelImage.kt:82-100). |
| `.../ui/pixel/PixelImage.kt` | `SpriteBitmaps.build` calls `SpriteRaster.argb`; `rememberClock` delegates to `rememberSceneClock`. Everything else byte-identical (all `internal` helpers stay: `drawSpriteAt`, `drawSpriteFit`, `px`, `fitScale`, `rememberSpriteFrame`, `preferSpriteSize`). |
| `.../ui/pixel/HearthScene.kt` | Keeps `enum class HearthState`, `hearthSkyTop`, `HearthScene`, plus new `internal fun hearthPalette(colors: PocketColors): ScenePalette`. Deleted (moved to stage/): `SceneSprites`, `SceneCache`, `drawHearth`, the five helpers, consts, `bayer`, `lerpArgb`. |
| `.../ui/pixel/Fire.kt` | `EmberSet` moves to `stage/Embers.kt` (import added). `Fire`, `fireSpriteName`, `Embers`, and the DrawScope `drawEmbers` stay as they are (`Embers` has no caller anywhere; dead code, left alone on purpose). |
| `.../ui/pixel/StageSurface.kt` | NEW. Compose: `StageSurface`, `rememberStageSurface`. |
| `.../ui/pixel/SceneClock.kt` | NEW. Compose: `rememberSceneClock`, `rememberSpritesReady`. |
| `.../ui/pixel/PixelStageBitmap.kt` | NEW (cut item 3). `PixelStage.toBitmap`. |
| `.../ui/pixel/stage/Argb.kt` | NEW. Blend and colour helpers. |
| `.../ui/pixel/stage/PixelStage.kt` | NEW. The framebuffer. |
| `.../ui/pixel/stage/StageGrid.kt` | NEW. Whole-number scale and offsets of a surface. |
| `.../ui/pixel/stage/SceneArt.kt` | NEW. Dithered sky, glow, Bayer matrix (the IntArray code of `SceneCache.sky/glow`, HearthScene.kt:106-182). |
| `.../ui/pixel/stage/Embers.kt` | NEW. `EmberSet` (moved) and `PixelStage.drawEmbers`. |
| `.../ui/pixel/stage/SceneModel.kt` | NEW. `ScenePalette`, `SceneState`, `SceneQuality`, `SpriteSource`. |
| `.../ui/pixel/stage/SceneRenderer.kt` | NEW. The renderer. |
| `.../ui/pixel/stage/FrameGate.kt` | NEW. The 30 fps gate, pure. |
| `app/src/test/java/dev/pocketagent/app/ui/pixel/stage/*.kt` | NEW tests and `LegacyHearthReference.kt`, `TestSprites.kt` (section 8). `PxlParserTest.kt` (same folder one level up) stays and must stay green unchanged. |
| `tools/art/craft_art.py`, `pxl.py`, `contact_sheet.py`, `README.md` | NEW (section 3, last table). |

Not touched: `Avatars.kt`, `Mascot.kt` (they keep drawing through `DrawScope`; they are 28 px tiles and 32 px sprites, not scenes). `dist/craft_art.py` stays where it is (git-ignored); the README says the copy in `tools/art/` is the one that counts.

## 3. Shared symbols you PROVIDE

### 3.1 Unchanged public API (call sites in other areas must keep compiling; do not rename, do not change a signature)

`SpriteBank.init(context: Context)`, `.get(name: String): Sprite`, `.find(name: String): Sprite?`, `.all(): Map<String, Sprite>`, `.loadErrors: List<String>`; `Sprite`, `SpriteFrame`, `PaletteEntry`, `PxlParser.parse(fileName: String, text: String): List<Sprite>`, `PxlException`;
`PixelImage(sprite: Sprite, modifier, frame: Int?, tint: Color?, ramp: List<Color>?, playing: Boolean)` and the `name: String` overload; `Mascot`, `MascotMood`, `Avatar`, `AvatarId`, `avatarIdOf`, `Fire`, `Embers`;
`HearthScene(state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null, stokeBoost: Float = 0f, celebration: Boolean = false)`, `enum class HearthState { COLD, KINDLING, BLAZING, SMOKE }`, `internal fun hearthSkyTop(colors: PocketColors): Color`;
internal: `rememberSpriteFrame`, `rememberClock(active: Boolean): LongState`, `SpriteBitmaps.plain/get`, `drawSpriteAt`, `drawSpriteFit`, `px`, `fitScale`.
Callers today: `PocketApp.kt:113`, `ui/components/PixelIcon.kt:41,47`, `ui/home/HomeScreen.kt:188`, `ui/home/HomeScrim.kt:51`, `ui/settings/AppearanceSection.kt:177-178`, `PxlParserTest.kt`.

### 3.2 New: `SpriteBank` additions (package `dev.pocketagent.app.ui.pixel`)

```kotlin
fun interface SpriteSource { operator fun get(name: String): Sprite }   // in stage/SceneModel.kt, package ...ui.pixel.stage
object SpriteBank : SpriteSource {
    val isReady: Boolean                                   // true once every file was parsed (errors or not)
    fun preload(context: Context)                          // non-blocking, idempotent, never throws; parses on a daemon thread "pixel-preload"
    override fun get(name: String): Sprite                 // unchanged behaviour (throws IllegalArgumentException on unknown name)
    // init/find/all/loadErrors: as today. They block while a preload is running (same lock), so no caller needs a change.
}
object SpriteLoader {                                      // pure
    class Result(val sprites: Map<String, Sprite>, val errors: List<String>)
    fun load(files: List<Pair<String, () -> String>>, onError: (file: String, e: Throwable) -> Unit = { _, _ -> }): Result
}   // files in the order given (SpriteBank passes them sorted by name). A PxlException message is kept as is ("file:line: msg"); any other exception becomes "$file: ${e.javaClass.simpleName}: ${e.message}"; a duplicate name gives "$file: sprite '$name' is already defined in $other" and the first one wins.
object SpriteRaster {                                      // pure
    fun argb(sprite: Sprite, frame: Int, hasTint: Boolean = false, tint: Int = 0, ramp: IntArray? = null): IntArray
    fun plain(sprite: Sprite, frame: Int): IntArray        // cached in sprite.rasters[frame]; never mutate the result
}
```
`argb` = lines 83-98 of PixelImage.kt exactly: palette copy, ramp chars '1'..'4' replaced by `ramp[min(k, ramp.size-1)]` through `sprite.rampSlots`, tint: `a = (((pal ushr 24) * (tint ushr 24)) + 127) / 255`, colour `tint and 0xFFFFFF`, index -1 gives 0.

### 3.3 New: the core (package `dev.pocketagent.app.ui.pixel.stage`, no Android types)

```kotlin
object Argb {
    fun pack(a: Int, r: Int, g: Int, b: Int): Int
    fun alpha255(alpha: Float): Int                      // (alpha.coerceIn(0f,1f) * 255f + 0.5f).toInt()   (what Color.toArgb does)
    fun withAlpha(rgb: Int, alpha: Float): Int           // (alpha255(alpha) shl 24) or (rgb and 0xFFFFFF)
    fun scaleAlpha(argb: Int, alpha255: Int): Int        // alpha' = ((argb ushr 24) * alpha255 + 127) / 255, colour kept
    fun lerp(a: Int, b: Int, t: Float): Int              // = lerpArgb of HearthScene.kt:58-65 (opaque result, per channel roundToInt)
    fun over(dst: Int, src: Int): Int                    // straight-alpha source-over, see below
}
```
`over(dst, src)`: `sa = src ushr 24`; `sa == 255` returns `src`; `sa == 0` returns `dst`; `da == 0` returns `src`; `da == 255` returns `0xFF000000 | c` with `c = (s*sa + d*(255-sa) + 127) / 255` per channel (e.g. `over(0xFF0000FF, 0x80FF0000) == 0xFF80007F`); otherwise `oa = sa + (da*(255-sa)+127)/255` and `c = (s*sa*255 + d*da*(255-sa) + oa*255/2) / (oa*255)`.

```kotlin
class PixelStage(val width: Int, val height: Int) {       // require(width > 0 && height > 0)
    val pixels: IntArray                                   // row-major ARGB, straight alpha, 0 = transparent; length width*height
    fun clear(argb: Int = 0)
    fun plot(x: Int, y: Int, argb: Int)                    // src-over, clipped
    fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int)   // src-over, clipped, w/h <= 0 draws nothing
    fun blit(src: IntArray, srcW: Int, srcH: Int, x: Int, y: Int, alpha: Float = 1f)   // src-over per pixel, clipped; source alpha 0 skipped; alpha is applied with Argb.scaleAlpha(src, Argb.alpha255(alpha))
    fun blit(sprite: Sprite, frame: Int, x: Int, y: Int, alpha: Float = 1f)            // = blit(SpriteRaster.plain(sprite, frame), ...)
    fun scaleInto(dst: IntArray, dstW: Int, dstH: Int, grid: StageGrid)   // nearest neighbour: every stage pixel becomes grid.scale x grid.scale device pixels at (grid.offsetX, grid.offsetY), clipped to dst; dst pixels outside are left as they are; stage pixels are copied (not blended)
}
class StageGrid(val scale: Int, val width: Int, val height: Int, val offsetX: Int, val offsetY: Int) {
    companion object {
        /** The rule of HearthScene.kt:243-248. null when widthPx < 8f or heightPx < 8f. */
        fun forScene(widthPx: Float, heightPx: Float): StageGrid?   // scale = ceil(w/176).coerceAtLeast(1); width = ceil(w/scale); height = ceil(h/scale); offsetX = ((w - width*scale)/2f).roundToInt(); offsetY = (h - height*scale).roundToInt()
    }
}
class ScenePalette(val skyTop: Int, val skyBottom: Int, val farRamp: IntArray /*4, darkest first*/, val glow: Int /*opaque*/, val fireLow: Int, val fireMid: Int, val fireHigh: Int) { equals/hashCode by content (farRamp contentEquals) }
data class SceneState(val hearth: HearthState, val palette: ScenePalette, val progress: Float? = null, val mood: MascotMood? = null, val stokeBoost: Float = 0f, val celebration: Boolean = false)
enum class SceneQuality(val particles: Boolean) { FULL(true), PLAIN(false) }   // PLAIN = no shooting star, distant fires, smoke, embers, fireflies, glow flicker, Pêt's hop
fun interface SpriteSource { operator fun get(name: String): Sprite }
class SceneRenderer(sprites: SpriteSource) {              // NOT thread-safe: one instance per surface, one thread at a time. Resolves its sprites in init (throws if missing).
    fun render(stage: PixelStage, state: SceneState, timeMs: Long, quality: SceneQuality = SceneQuality.FULL)
    companion object {
        const val ART_W = 176; const val GROUND_H = 34
        val REQUIRED_SPRITES: List<String>   // the 12 scene/fire names + pet_idle..pet_waving (7), in this order: scene_far, scene_near, scene_ground, scene_fore, scene_moon, fire_cold, fire_out, fire_low, fire_mid, fire_high, then MascotMood.entries.map { it.spriteName() }
    }
}
class FrameGate(val maxFps: Int = 30, val slackNanos: Long = 2_000_000L) {   // require(maxFps in 1..240)
    fun accept(frameTimeNanos: Long): Boolean   // true for the first call; then true when frameTimeNanos - lastAccepted >= 1_000_000_000L / maxFps - slackNanos, and lastAccepted = frameTimeNanos
    fun reset()
}
```
Behaviour of `render` (contract for anyone who calls it later): draws into a stage of any size, layers in this fixed painter's order — sky (logical 0,0, height `g+8`), stars (46, twinkle by `timeMs`), shooting star*, moon halo, moon, far ridge (tinted by `palette.farRamp`), near ridge, distant fires*, ground, glow, Pêt, fire sprite, smoke*, embers*, fireflies*, foreground grass (`*` = `quality.particles` only; BLAZING/KINDLING conditions as in today's code). `g = stage.height - GROUND_H`, art centred at `artX = (stage.width - ART_W) / 2`. Every sprite is blitted at scale 1 of the stage: one scale per surface. It does not clear the stage first (caller clears). `stokeBoost` is coerced to 0..1.5 inside. If `stage.width < 8 || stage.height < GROUND_H + 10` it draws nothing (guard for small widgets; today's code would throw on such sizes, which never happened because Home is 280 dp).

### 3.4 New: Compose side (package `dev.pocketagent.app.ui.pixel`)

```kotlin
class StageSurface {                                      // remember it; holds a PixelStage and 3 reusable ARGB_8888 bitmaps (ring)
    fun draw(scope: DrawScope, grid: StageGrid?, stamp: Any, render: (PixelStage) -> Unit)
}
@Composable fun rememberStageSurface(): StageSurface
@Composable fun rememberSceneClock(active: Boolean, maxFps: Int = 30): LongState
@Composable fun rememberSpritesReady(): State<Boolean>
internal fun hearthPalette(colors: PocketColors): ScenePalette
fun PixelStage.toBitmap(scale: Int = 1): Bitmap         // PixelStageBitmap.kt (cut item 3): scaled copy, ARGB_8888, never HARDWARE
```
- `StageSurface.draw`: `grid == null` draws nothing. Re-allocates stage and ring when `grid.width/height` change. If `stamp == lastStamp` and grid unchanged it only redraws the previous bitmap (no `render`). Otherwise: `stage.clear()`, `render(stage)`, `ring[i].setPixels(stage.pixels, 0, w, 0, 0, w, h)`, then ONE `scope.drawImage(bitmap, IntOffset.Zero, IntSize(w,h), IntOffset(grid.offsetX, grid.offsetY), IntSize(w*scale, h*scale), filterQuality = FilterQuality.None)`. Ring of three because the render thread may still read the previous frame's bitmap: never write to the bitmap that was drawn last. Never `recycle()` (a recycled bitmap in a pending display list crashes); `release` only drops references.
- `rememberSceneClock(active)`: milliseconds since it started, `0` while `active` is false. `withFrameNanos` (display tied) and `FrameGate(maxFps)`: the value is written only when the gate accepts, so at 60/90/120 Hz it changes every 2nd/3rd/4th frame (30 per second), never more. First value is written on the first frame. While the lifecycle is below STARTED the loop is suspended (`repeatOnLifecycle(Lifecycle.State.STARTED)`) and time resumes from the last value, it does not jump. Read the returned state only inside draw lambdas.
- `rememberSpritesReady()`: `produceState(SpriteBank.isReady)`; when false it runs `SpriteBank.init(context)` on `Dispatchers.Default` and then flips to true. `HearthScene` shows nothing (same size, transparent) until true.
- `HearthScene` body: `val ready by rememberSpritesReady()`; `colors = Pocket.colors`, `anim = Pocket.anim`; `clock = rememberSceneClock(anim != AnimLevel.OFF)`; `palette = remember(colors) { hearthPalette(colors) }`; `renderer = remember(ready) { if (ready) SceneRenderer(SpriteBank) else null }`; `surface = rememberStageSurface()`; `Spacer(modifier.fillMaxWidth().height(280.dp).clipToBounds().testTag("stage.hearth").drawBehind { ... })`, in the draw lambda: `t = clock.longValue / (if (anim == AnimLevel.REDUCED) 2 else 1)`, `state = SceneState(state, palette, progress, mood, stokeBoost.coerceIn(0f, 1.5f), celebration)`, `quality = if (anim == AnimLevel.FULL) FULL else PLAIN`, `surface.draw(this, StageGrid.forScene(size.width, size.height), Triple(state, t, quality)) { renderer.render(it, state, t, quality) }`.
- `hearthPalette` = HearthScene.kt:214-218 verbatim in values (`hearthSkyTop(colors).toArgb()`, `skyBase = if (colors.isDark) colors.bg else Color(0xFF1D1220)`, `skyBottom = lerp(skyBase, colors.fireLow, 0.16f).toArgb()`, `farRamp = IntArray(4) { lerp(Color(FAR_RAMP[it]), skyBase, 0.2f).toArgb() }`, `glow = colors.fireMid.toArgb() or (0xFF shl 24)`, fire colours `.toArgb()`). The Oklab `lerp` of Compose stays here, which is why the pure tests feed fixed palettes.

### 3.5 Scripts (`tools/art/`), run from the repository root; Python 3.12 is on the owner's PC as `python` (Linux CI: `python3`)

| Command | Output |
|---|---|
| `python tools/art/craft_art.py` | Writes `fire.pxl`, `scene.pxl`, `appicon.pxl` into `app/src/main/assets/pixel/` (same bytes as today, verified); prints `Wrote <path> (<n> sprites)` per file, then `Art pass v2 complete!`. Exit 0. |
| `python tools/art/craft_art.py --only fire\|scene\|appicon` | Only that file. |
| `python tools/art/craft_art.py --out <dir>` | Same, into `<dir>` (created). |
| `python tools/art/craft_art.py --check` | Builds into a temp dir, compares bytes with the committed files; prints `ok <file>` or `DIFF <file>` per file; exit 0 when all equal, 1 otherwise. Never touches the working tree. |
| `python tools/art/contact_sheet.py [--file <pxl or dir>] [--max-width 2400] [--scale 4] [--frames first\|all] [--sprite <name>]... [--bg #RRGGBB] [--ramp "#c1,#c2,#c3,#c4"] [--out dist/art/contact-sheet.png]` | One PNG of every sprite (default: all 112 in `assets/pixel`), checkerboard background, name under each sprite, shelf-packed up to `--max-width`. Stdout: one line `name<TAB>WxH<TAB>Nframes<TAB>file` per sprite, then `contact sheet: <absolute path> (<W>x<H> px, <N> sprites)`. A parse problem prints `file:line: message` to stderr and exits 1. Needs Pillow (`pip install pillow`; 11.3.0 is on the owner's PC). |
| `tools/pixel-preview.ps1`, `tools/pxl-to-vector.ps1` | Unchanged, stay where they are. The README links them. |

`craft_art.py` changes against `dist/craft_art.py`: `ROOT` is two levels up from the script (`os.path.join(os.path.dirname(__file__), "..", "..")`; verified: with that single change the three files regenerate byte-identical to the committed ones); `PIXEL_DIR` is reassigned from `--out` before any `build_*` runs (they read the global at call time); an `argparse` block replaces the `__main__` body; `sys.dont_write_bytecode = True` at the top of every script (no `__pycache__` in the tree). Nothing else is edited: the art stays as it is. `pxl.py`: strict parser with the rules of `PxlParser` (same messages, `file:line: ...`), used by `contact_sheet.py`.
README must say: which of the six files are generated (3: fire, scene, appicon) and which are the source themselves, edited by hand (3: `avatars.pxl`, `mascot.pxl`, `icons.pxl`; no generator exists for them); how to add a sprite; the three commands above; the preview scripts; the format reference (`docs/phase1-contract.md`, section "Shared pixel format").

## 4. Shared symbols you CONSUME

| From | Symbol (exact) | Used for |
|---|---|---|
| DESIGN (`ui/theme`) | `Pocket.anim: AnimLevel @Composable get()` with `AnimLevel { FULL, REDUCED, OFF }` (today `ui/theme/PocketTheme.kt:44`, `Appearance.kt:18`). Keep the name and the type; DESIGN's "effective level" (system setting, battery saver) must come out of this same property. | Quality and clock |
| DESIGN | `Pocket.colors: PocketColors` with `isDark: Boolean`, `bg`, `bgDeep`, `fireLow`, `fireMid`, `fireHigh` as `androidx.compose.ui.graphics.Color` (`Palettes.kt:17-20`). | `hearthPalette`, `hearthSkyTop` |
| NAV | `testTagsAsResourceId` set at the root so that `Modifier.testTag("stage.hearth")` shows up in `uiautomator dump`. | Robot (section 9) |
| CONFIG | `AppLog.e(tag: String, msg: String, e: Throwable)` as used at `PocketApp.kt:113`. | Only in R1 |
| Libraries already on the classpath | `androidx.lifecycle:lifecycle-runtime-compose` 2.11.1 (`androidx.lifecycle.compose.LocalLifecycleOwner`, `androidx.lifecycle.repeatOnLifecycle`), `androidx.compose.runtime.withFrameNanos`. No new dependency. | Clock |

## 5. Strings

None. This area has no user-visible text (`ui/pixel` has 0 `stringResource(`, 0 `Toast`). No strings file, no key prefix is taken. `Avatars.kt:28` (`name.lowercase()`) builds a sprite id, not a text: TEXT's and WORDS' case lint must leave it.

## 6. Manifest entries

None.

## 7. REQUESTS to other areas

- R1 **CONFIG**: `PocketApp.kt` line 113: replace `try { dev.pocketagent.app.ui.pixel.SpriteBank.init(this) } catch (e: Throwable) { AppLog.e("App", "sprite bank failed to load", e) }` by `dev.pocketagent.app.ui.pixel.SpriteBank.preload(this)` (non-blocking, never throws, logs through `Log.e` and `SpriteBank.loadErrors`). Without it nothing breaks: `init` is simply still called by the first composable, as today.
- R2 **CONFIG** (optional, one line): show `SpriteBank.loadErrors` in Diagnostics when not empty (it is `List<String>`, "file:line: message").
- R3 **BUILD** (optional): in `.github/workflows/build.yml`, a step on the unit-test job after checkout: `python3 tools/art/craft_art.py --check`. It fails when someone edits a generated `.pxl` by hand or the script drifts.
- R4 **ROBOT**: the parity check on the phone (section 9) needs the Home screen reachable by `link`, and a way to set Settings > Appearance > Animations to Off before the shot (the preference file is `shared_prefs/appearance.xml`, key `animations`, `Appearance.kt:89-98`).
- R5 **DESIGN**: keep `PixelImage(...)`, `SpriteBank.find` and `Mascot(...)` stable; `PetNote` may build on them. If DESIGN needs a Pêt head at 16 px it is a new sprite (STAGE's art, a later wave), not a scaled one.

## 8. Tests (JVM unit tests, `app/src/test/java/dev/pocketagent/app/ui/pixel/stage/`; no Android classes)

Helpers: `TestSprites.all(): SpriteSource` (reads `app/src/main/assets/pixel/*.pxl` with `PxlParser`, same directory lookup as `PxlParserTest.assetDir()`, returns a map-backed source; tests `return` early when the directory is not found, like `PxlParserTest`). `TestPalettes.DARK` and `LIGHT`: fixed `ScenePalette` constants (any plausible values; dark: skyTop `0xFF12081C`, skyBottom `0xFF2A1630`, light: skyTop `0xFF130A16`, skyBottom `0xFF2B1B2E`; farRamp the four `FAR_RAMP` values; fire colours `0xFFD9381E, 0xFFF7691A, 0xFFFFC857`).

**How the "before" is captured (item 1, before any edit).** `LegacyHearthReference.kt` (test source, header: "copy of HearthScene.kt/PixelImage.kt/Fire.kt at commit 344829f; delete when a wave changes the picture on purpose"):
`internal object LegacyHearthReference { fun render(sprites: SpriteSource, widthPx: Int, heightPx: Int, state: HearthState, progress: Float?, mood: MascotMood?, stokeBoost: Float, celebration: Boolean, t: Long, particles: Boolean, palette: ScenePalette): IntArray }` — an ARGB array of device size, 0 where nothing was drawn.
Copy `drawHearth`, `drawMoonHalo`, `drawDistantNewrozFires`, `drawFireflies`, `drawShootingStar`, `drawSmoke`, `drawEmbers`, `EmberSet`, `SceneCache.sky/stars/glow`, `bayer`, `lerpArgb`, and the body of `SpriteBitmaps.build`, then change types only:
| Today | In the reference |
|---|---|
| `DrawScope.px(color, x, y, w, h)` | `RefCanvas.rect(argb, x: Float, y: Float, w: Float, h: Float)`: rounds each to Int, clips, `Argb.over` per device pixel |
| `drawSpriteAt(img, x, y, scale, alpha)` | `RefCanvas.image(px: IntArray, w, h, x, y, scale, alpha)`: every source pixel becomes scale x scale device pixels, `Argb.scaleAlpha(src, Argb.alpha255(alpha))`, `Argb.over`, clipped |
| `Color.copy(alpha = a)` | `Argb.withAlpha(rgb, a)` |
| `Color(0xFF....)` constants | `0xFF....toInt()` |
| `colors.fireLow/Mid/High`, `skyTop`, `skyBottom`, `farRamp`, `glowColor` | the same fields of `palette` |
| `Bitmap.createBitmap(px, w, h, ARGB_8888).asImageBitmap()` | the `IntArray` itself |
Keep every Float expression, every constant and the order of statements. `ox, oy, p` are computed from `widthPx/heightPx` with the formula of HearthScene.kt:243-248 (not from `StageGrid`, so the grid is tested independently). The only shared code is `Argb.over/withAlpha/scaleAlpha` (covered by `ArgbTest`).

| Test class | Tests | Proves |
|---|---|---|
| `SceneRendererParityTest` | `parityAcrossStatesAndTimes`: for sizes (1080,735), (720,560), (1440,1000) and every one of these 14 cases: COLD t=0; COLD t=4321; KINDLING progress 0.1 / 0.4 / 0.9 at t=2500; BLAZING t=1234; BLAZING t=7700 (Pêt's hop); BLAZING t=14300 (shooting star); BLAZING stoke 0.8 t=3000; BLAZING celebration t=5000; SMOKE t=9876; BLAZING with `mood = MascotMood.WAVING`; COLD stoke 1.2 (embers while cold); BLAZING `PLAIN` t=3000. Each in `TestPalettes.DARK` and `LIGHT`. The new path is `StageGrid.forScene` + `SceneRenderer.render` + `scaleInto`; the old is the reference; arrays must be equal, the failure message names size, case and the first differing pixel (x, y, both values). | the picture did not change |
| | `parityMessageNamesFirstDifference` (the helper that formats it is called with two arrays that differ at one pixel) | the diagnostics work |
| `SceneRendererTest` | `sameInputsGiveSameFrame`; `timeChangesTheFrameWhenBlazing`; `plainDrawsNoParticles` (BLAZING t=3000: PLAIN differs from FULL; PLAIN at t=3000 equals PLAIN at t=3000 with a second renderer); `tinyStageDrawsNothing` (width 7 or height 43: no exception, all zero); `everyRequiredSpriteExists` (`REQUIRED_SPRITES` all resolve); `stokeBoostIsCoerced` (2.5 equals 1.5) | contract of `render` |
| `StageGridTest` | `forSceneMatchesToday`: (1080,735) gives scale 7, 155x105, offset (-2, 0); (720,560): 5, 144x112, (0, 0); (1440,1000): 9, 160x112, (0, -8); (412,280): 3, 138x94, (-1, -2); `tooSmallIsNull` (7.9f x 300f and 300f x 7.9f) | the whole-number rule |
| `PixelStageTest` | `fillRectClipsToBounds`; `blitClipsNegativeOffsets`; `blitSkipsTransparentSource`; `blitAppliesGlobalAlphaToSourceAlpha`; `clearZeroes`; `scaleIntoPlacesBlocksWithOffsetAndClips` (2x2 stage, scale 3, offset (-1, 2)) | framebuffer and scaling |
| `ArgbTest` | `overOpaqueSourceReplaces`; `overTransparentSourceKeepsDestination`; `overHalfAlphaRoundsHalfUp` (`over(0xFF0000FF, 0x80FF0000) == 0xFF80007F`); `overOnTransparentDestinationKeepsSource`; `withAlphaMatchesToArgb` (0.25 gives 64, 0.5 gives 128, 1 gives 255, -1 gives 0, 2 gives 255); `lerpEndpointsAndMiddle` | blend and colour maths |
| `FrameGateTest` | `firstFrameAccepted`; `sixtyHzEverySecondFrame`; `ninetyHzEveryThirdFrame`; `oneTwentyHzEveryFourthFrame` (frame spacings 16_666_667 / 11_111_111 / 8_333_333 ns, 600 frames each: accepted count is 300 / 200 / 150); `slowDisplayEveryFrame` (24 Hz); `jitterWithinSlackStillAccepted` (31_500_000 ns apart); `resetRestartsCadence` | cap of 30 per second, tied to the display |
| `SpriteRasterTest` | `plainMatchesPaletteIndices`; `rampReplacesCharsOneToFour`; `rampShorterThanFourRepeatsLast`; `tintScalesAlphaAndReplacesRgb`; `plainIsCachedOnTheSprite` (same array instance twice) | extraction from `SpriteBitmaps.build` is exact |
| `SpriteLoaderTest` | `brokenFileIsSkippedOthersLoad`; `duplicateNameAcrossFilesIsReported` (first wins, message as above); `unreadableFileIsReportedWithClassName`; `errorKeepsFileLineMessage` | loading rules without Android |
| `StageCoreIsPureTest` | `noAndroidImportsInStage`: every `.kt` under `.../ui/pixel/stage/` (and `Sprite.kt`, `SpriteLoader.kt`, `SpriteRaster.kt`) has no line starting `import android.`, `import androidx.`, `import java.awt.` | the core stays testable and reusable by widgets |
| `PxlParserTest` (existing, unchanged) | all | the move of the parser lost nothing |

## 9. Robot hooks

- testTag (exact): `stage.hearth` on the Home scene's `Spacer` (inside `HearthScene`, so the robot gets the scene's bounds from the dump and can crop screenshots). No other screen is added by this area.
- Phone parity check (ROBOT, not a unit test): install the owner's current debug build 32 (old renderer), Settings > Appearance > Animations Off, open Home, `shot` cropped to `stage.hearth`; install the new build, same steps, same shot. Animations Off is deterministic in both builds (clock 0, no particles). Compare with a per-pixel diff: pass when at most 1 channel level differs and fewer than 0.5 % of pixels differ (the translucent glow and halo may differ by one level between Skia and the software blend; everything opaque must be identical). Then one more shot with Animations Full as a sanity look only (twinkle makes it non-comparable).
- Smoothness: ROBOT's `home-idle` scenario in `phone-perf` records `gfxinfo` with Home idle for 10 s: the scene must cost under 1 ms of drawing per frame (design language 11) and the new frames must arrive at no more than 30 per second (the number of distinct frames between two `gfxinfo` resets).
- Practice-model keywords or debug links: none needed.

## 10. Experiment branches

No experiment of section 7 of the plan (S1-S7) changes this area. If S2 fails (R8 breaks a name lookup): nothing in `ui/pixel` is found by name at run time except `Class.forName("android.app.ActivityThread").getMethod("currentApplication")` (`SpriteBank.kt:255`), a platform class that is never renamed; the fallback of S2 (shrinking off, still not debuggable and compiled ahead of time) changes nothing here. S1, S3 (navigation, shared elements) touch screens, not the scene.

## 11. Risks, and where the plan or the design language is wrong given the real code

1. **The "pixel comparison before and after" cannot be run against today's drawing on the JVM** (plan 0.8 line 667-668): today's code needs `DrawScope`, `Bitmap` and Skia. The contract replaces it with a type-only translation of today's code (section 8) compared at device resolution, plus a phone check with animations off. If the translation is wrong in the same way as the new code, the JVM test cannot see it; the phone check is the second witness. Say so in the review brief.
2. **Skia versus software blend.** Opaque pixels are identical; translucent ones (glow, moon halo, fireflies, smoke, embers, any `#AARRGGBB` palette entry) may differ by one level per channel from what Skia produced. Acceptable under "nothing looks different"; the tolerance of section 9 reflects it.
3. **Design language 6.1 differs from this contract on three points**, on purpose: `SceneRenderer` is a `class` (it owns caches: sky, stars, glow, far-ridge ramp), not an `object`; `toBitmap` is an extension in the Android side because a JVM test has no `Bitmap` (`isReturnDefaultValues` would return null); the core is free of all `android.*`, not only of Compose. The sketch's `SceneState` fields (`sky`, `season`, `theme`, `pet`, `wind`, `event`, `budgetLeft`) do not exist yet: Wave 1 adds them at the end with defaults, never reordering. The "180 x 160" framebuffer is really `ceil(w/ceil(w/176))` by `ceil(h/scale)`: 155 x 105 to 160 x 112 on phones.
4. **Plan 0.8 "sprites are parsed off the main thread at start"** is only half in this area: the call that blocks the main thread is `PocketApp.kt:113` (CONFIG's file). Without R1 the first composable still triggers a blocking `init` as today.
5. **Plan A14 is only half true**: `craft_art.py` builds 3 of the 6 `.pxl` files (fire, scene, appicon). `avatars.pxl`, `mascot.pxl`, `icons.pxl` have no generator anywhere; the `.pxl` is their source. The README says so. The old script's `ROOT` assumes it sits in `dist/` (one level below the repo); in `tools/art/` it must go two levels up or it writes into `tools/app/...` (what happens if you just copy it).
6. **Bitmap reuse under hardware rendering**: writing into the bitmap the render thread is still reading shows torn frames. Hence the ring of three and the rule "never recycle". A reviewer should look at `StageSurface` first.
7. **Draw-phase work**: `render` runs inside `drawBehind` on the UI thread (about 17,000 pixels, a few dozen blits). Budget under 1 ms; measured by ROBOT, not by a unit test. The `stamp` check keeps scrolling from re-rendering an unchanged frame.
8. **`Embers` and the DrawScope `drawEmbers` in `Fire.kt` stay** (no caller in the whole tree); a second copy of the ember maths therefore exists next to the core's `drawEmbers`. Wave 1 deletes the old one.
9. **Kotlin traps for the implementer**: `0xFF6E6496` is a `Long` (write `0xFF6E6496.toInt()`); `roundToInt()` of -2.5f is -2 (Java `Math.round`), which is what `StageGrid` needs; `(wl - ART_W) / 2` is Int division towards zero (-21/2 = -10); do not write `*.pxl` inside a KDoc comment (nested comment, AGENTS.md); `SceneState`'s `ScenePalette` needs a hand-written `equals` (an `IntArray` in a data class compares by reference, and `StageSurface`'s stamp relies on equality); in `SpriteBank` the `init` order is `lock` before `sprites` (already so; keep it); `preload` must set its "started" flag before starting the thread.
10. **The core still names three types that live in non-pure files of the same module**: `HearthState` (HearthScene.kt), `MascotMood` and `MascotMood.spriteName()` (Mascot.kt). Loading them does not load Compose (`PxlParserTest` already does it), and the purity test only checks imports; moving them would break every other area's imports, so they stay.
11. **REDUCED** keeps today's behaviour: time runs at half speed and no particles (`SceneQuality.PLAIN`), stars still twinkle; **OFF**: clock stays 0, one still frame, drawn once.
