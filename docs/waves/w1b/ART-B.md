# Wave 1b, area ART-B: contract draft

Written 2026-11-25. `J/` is `app/src/main/java/dev/pocketagent/app/`, `T/` is `app/src/test/java/dev/pocketagent/app/`, `R/` is
`app/src/main/res/`, `A/` is `app/src/main/assets/pixel/`; package `dev.pocketagent.app` is written `app`. Read for this draft: `docs/waves/w1b/SPLIT.md`
(4.7, 3, 5, 6, 8, 9, 11); `docs/phase4-plan.md` 7.1 (items 1.6, 1.7, 1.8), 4.1; `docs/design-language.md` 2.2, pattern 8, 8.15, 8.16, 9, 13; the 1a
contract (`docs/waves/w1a-contract.md`: 3.10, 5.6, 7.1, 7.2) and `docs/waves/w1/ART-WORLD.md`, `HOME.md`, `APPEAR.md` (for the shape); and the tree at the
work branch: `J/ui/theme/PixelFont.kt` (324 lines), `T/ui/theme/PixelFontGoldenTest.kt`, `PixelFontTest.kt`, `app/src/test/resources/pixelfont-v1.golden` (135
lines), `A/pixel/icons.pxl`, `J/ui/components/PixelIcon.kt`, `PixelText.kt`, `J/ui/pixel/PixelImage.kt`, `Sprite.kt`, `tools/art/*`, `tools/pxl-to-vector.ps1`,
`R/values/themes.xml`. **Everything that 1a creates (`illustrations.pxl`, `IllustrationId`, the review verdicts, the changed `craft_art.py` and README) is taken
from the 1a contract and not from today's code: 1a is built in `dist/wt-w1` and is not in the work branch.** Scratch checks for this draft (a Python simulation
of the font's `def`/`accent` rules, and a run of `tools/pxl-to-vector.ps1` into the scratchpad) were made outside the repository; the numbers quoted below come
from them and are marked "simulated".

## 1. What the plan and the design language ask

| Item | Source | Words |
|---|---|---|
| B21 the pixel font learns about 130 glyphs | plan 1.8 (lines 860 to 862); DL 2.2 (lines 96 to 99), 13.1 | "the Cyrillic alphabet in both cases and the accented Latin letters still missing"; "Cyrillic follows in Wave 1 and Greek in Wave 12"; tier 1 is "the app's own pixel font, drawn by hand"; a title with a character the tier cannot draw drops one tier (DL 13.1) |
| B25 tab icon frames (five icons, four frames each) and the Library icon | plan table line 902; DL pattern 8 (line 176); DL 9 (`PixelIconView`: "frame-animated icons: ... the five tab icons"); SPLIT 2 (the lead's ruling in `ART-WORLD.md` line 227 moved them to 1b) | "The new tab's icon plays its 3 to 4 frame animation once" |
| B24 (pictures) the empty-state pictures that 1a's review cut | plan 1.5; 1a contract 3.10, cut order 9; DL 9, 8.16 (empty state: "one of Pêt's small pictures, one sentence ..., and the one action") | 1a draws `empty_bubble` as core, the other seven and `tile_usage`, `tile_library`, `empty_gate` as extras; 1b draws what is missing and has a consumer |
| B10 (pictures) the three pictures of the free-key guide | plan 1.7 (line 847: "a three-picture guide to a Google AI Studio key") | the WELCOME areas draw them and write the captions; this area draws the pictures |
| B3 (vector) the splash icon | plan 1.6 (lines 835 to 837); SPLIT 4.3, 4.7 | "the system's splash shows the flame, which then travels to its place in the scene"; the file is `R/drawable/ic_splash_flame.xml`, made from the fire's pixels |
| B29 (a reviewer looked at every new sprite and glyph on a contact sheet) | plan "Proven when" (lines 915 to 917); SPLIT 10 item 8 | this area produces the sheets' inputs and the generator; DATA-B renders, LOOK-B judges |

Rules that bind this area: DL 13.1 (the ten languages are tier 1: nothing may fall to the system font because a letter is missing); DL 13.5 (capitals are
written by the author and never made by code, so the font needs both cases of every letter); DL 1 rule 3 (calm, slow loops: nothing flickers); 1a ruling
"a sprite that is not clearly better than what it replaces is cut" (1a 7.2) for sprites; for glyphs the verdict is FIX, never CUT (a missing letter breaks a
title, section 6). Principle 12: new names are plain English (`library`, `agent`, `basket`, `key`).

## 2. What exists today, and what 1a will have changed by then

### 2.1 The pixel font (read in the tree)

- `J/ui/theme/PixelFont.kt`: `object PixelFont`, `HashMap<Char, PixelGlyph>(256)`, grid of 12 rows (`ROWS`), capitals on rows 3 to 9 (`CAP_TOP = 3`,
  `CAP_HEIGHT = 7`), x-height on rows 5 to 9 (`X_TOP = 5`), descenders on rows 10 and 11, accent marks on rows 0 and 1 over capitals and rows 2 and 3 over
  lowercase, row 2 a gap. Helpers: `def(c, top, spec)` (rows joined by `/`, padded to the longest), `overlay`, `accent(c, base, mark, startRow)` (centres the
  mark in the base glyph with `(base.width - mark.length) / 2`, Kotlin integer division towards zero, and never clears a base pixel). Marks declared before
  `init`: `ACUTE GRAVE CIRC DIAER BREVE DOT CIRC3 DIAER3 DOT3 CEDILLA`. `init` defines v1 and ends with `defV2()` ("Wave 0 additions, last: the v1 glyphs above are
  never edited"). `MAP` (typographic punctuation to ASCII) is declared before `init`; `prepare()` applies NFC and then `MAP` to characters above 0x7E.
- 148 characters have a glyph (134 of v1, 14 of v2) plus the space. **v2 already has** `Æ æ Œ œ Ÿ ÿ « » € £ ° ± ↑ ↓` (`defV2`), so the French row of SPLIT 4.6
  ("Æ æ Œ œ Ÿ ÿ « »") is **already covered**: French needs nothing from 1b. v1 has `Ç Ş Ğ Ü Ö İ Ê Î Û É È À Â Ô Ù Ë Ï` and the lowercase of the same, with `ı`
  (dotless) as the base of `î ï`.
- `PixelTitle` (`J/ui/components/PixelText.kt` line 280) draws the pixel font only when `PixelFont.textCols(text)` is not null, else the whole title in the
  system font (the DL 13.1 rule, already true). `PixelText` itself falls back per character (used directly by `SectionHeader.kt`, `EmptyState.kt`,
  `PocketDialog.kt`, `HomeParts.kt`); this draft changes neither (risk 7).
- Tests today: `PixelFontGoldenTest.v1GlyphsUnchanged` compares the 135 lines of `pixelfont-v1.golden` (format `U+XXXX <width> <12 rows joined by |>`, written once from the
  commit before Wave 0 with `PIXELFONT_GOLDEN_WRITE=1`); `PixelFontTest` has `coversEverythingInShippedStrings`, which reads every `strings*.xml` of every `values*` folder
  under `src/main/res` and fails on any character above 0x7E that `prepare` plus `glyph` cannot draw (so every new `values-ru` and the like is covered the moment it
  exists), `v2GlyphsStayOnGrid`, `kerningNeverOverlapsOrTouches` and a floor of 140 glyphs.

### 2.2 The sprites and the splash

- `A/pixel/icons.pxl`: 60 sprites `icon_<name>`, 12 by 12, one frame, palette `. none` and `x #FFFFFF` (white, tinted at draw time). `PixelIcon` (`J/ui/components/PixelIcon.kt`) is the
  enum with `spriteName = "icon_" + name.lowercase()`; `PixelIconView` draws `PixelImage(sprite, tint = ...)` and draws nothing when the sprite is missing. The bar of
  `J/ui/AppRoot.kt` (lines 205 to 209) uses `HEARTH, CHATS, BOTS, SCROLL, GEAR`.
- `PixelImage(name, frame, tint, ramp, playing)` (`J/ui/pixel/PixelImage.kt`, the two overloads at lines 188 and 212): `frame = n` or `playing = false` draws that frame and never animates; animation level OFF shows
  frame 0. `Sprite.frames` is a list of `SpriteFrame(indices, durationMs)`, so a frame's own duration is data.
- `SpriteBank` loads every `.pxl` in `assets/pixel` in sorted file order; a duplicate name keeps the first; a bad file is skipped whole (1a gotcha). `PxlParserTest.everyShippedFileParsesAndNamesAreUnique` therefore covers any new file at once.
- No illustrations exist today; `EmptyState.kt` draws `Mascot(IDLE)` at 96 dp (as 1a notes at `EmptyState.kt:44`).
- The splash: `R/values/themes.xml` has `Theme.PocketAgent` with the plum window colour `#1D1220` and `windowSplashScreenBackground` (same colour) and no icon. `R/drawable/`
  holds the launcher vectors `ic_launcher_*` made by `tools/pxl-to-vector.ps1` (header comment "Generated by tools/pxl-to-vector.ps1 ... Edit the .pxl, not this file.").
- `tools/art/`: `pxl.py` (strict reader, `parse`, `parse_file`), `contact_sheet.py` (options `--file --max-width --scale --frames --sprite --bg --ramp --out`; **no tint option**, so white `x` icons are invisible on a light
  background), `craft_art.py` (three generators: `fire`, `scene`, `appicon`), `README.md` (says "six text files").

### 2.3 What 1a will have changed (from the 1a contract) and what it leaves alone

| 1a change | Effect on this area |
|---|---|
| `A/pixel/illustrations.pxl` (generated by `craft_art.py`): tiles `tile_hermes`, `tile_terminal`, `tile_routines`, `tile_saved` and `empty_bubble` core; `empty_scroll`, `_stool`, `_jar`, `_hourglass`, `_lantern`, `_boat`, `_cup`, `tile_usage`, `tile_library`, `empty_gate` extras that LOOK may cut one by one (1a 3.10, 5.6, 7.2) | the file is not in the work branch yet; this area never edits it and never reuses a name that is in it (section 3.6 rule) |
| `J/ui/pixel/Illustrations.kt` (`IllustrationId`, pure enum, 15 entries) | not editable here; names this area adds to the catalogue live in `J/ui/pixel/ArtBSprites.kt` (new, mine) |
| `tools/art/craft_art.py` edited, `tools/art/README.md` edited (rows for the new files) | this area adds `craft_b.py` and **appends** one new section at the end of the README so the two edits merge without conflict |
| review verdicts `docs/waves/w1/look/review-ART-WORLD.txt` (lines `<name> KEEP|FIX|CUT`) | the list of 1a pictures that have to be redrawn here is read from it (or from the finished `illustrations.pxl`) at the start of slice B |
| unchanged by 1a (1a section 2.10) | `PixelFont.kt`, `A/pixel/icons.pxl`, `PixelIcon.kt`, `PixelImage.kt`, `PixelText.kt`, `fire.pxl` (byte-identical, so the splash vector can be made from it) |

## 3. The design

### 3.1 Files (all mine; every file has one owner)

| File | Kind | Content |
|---|---|---|
| `J/ui/theme/PixelFont.kt` | edit (the only 1b editor) | marks `TILDE`, `CARON`, `DACUTE`, `OGON_R`, `OGON_E`; `alias()`; `defV3()` (+ `defV3Names()`); `MAP` additions; capacity 512 |
| `T/ui/theme/PixelFontGoldenTest.kt` | edit | `v2AndV3GlyphsUnchanged` and a write mode for the new golden; `v1GlyphsUnchanged` untouched |
| `app/src/test/resources/pixelfont-b.golden` | new | the lines of every glyph that is not in `pixelfont-v1.golden` (v2 and v3: 14 + 127 = 141 lines) |
| `T/ui/theme/PixelFontCoverageTest.kt`, `PixelFontRulesTest.kt`, `PixelFontSheetDump.kt` | new | section 4 |
| `A/pixel/tabicons.pxl`, `icons_b.pxl`, `illustrations_b.pxl` | new, **generated** by `craft_b.py` | section 3.4 |
| `J/ui/pixel/ArtBSprites.kt`, `T/ui/pixel/ArtBSpritesTest.kt`, `T/ui/pixel/ArtBAssetsTest.kt`, `T/ui/pixel/SplashFlameVectorTest.kt` | new | pure names and timing of the sprites; tests (these files, with `PixelFontRulesTest.kt` of section 4, are an addition to SPLIT 4.7's list: open point 1) |
| `R/drawable/ic_splash_flame.xml` | new | section 3.5 |
| `tools/art/craft_b.py`, `tools/art/README.md` (appended section) | new, edit | section 3.6 |
| `docs/waves/w1b/look/review-ART-B.txt`, the sheets in the same folder | LOOK-B writes | section 5 |

No strings file, no manifest entry, no `NOTICE` line (everything is drawn here; no font, no sound, no download).

### 3.2 The glyphs

#### 3.2.1 The audit (which characters each of the ten languages needs, and what is missing)

"Exists" is checked against `glyph()` today. The audit is also what `PixelFontCoverageTest.tenAlphabetsHaveGlyphs` holds as a table (so it needs no other area's code).

| Language | Letters beyond ASCII that its titles use | Missing today | Added by 1b |
|---|---|---|---|
| English, Indonesian | none | none | none |
| Español | `Á É Í Ó Ú Ü Ñ á é í ó ú ü ñ ¿ ¡ « »` | `Á Í Ó Ú Ñ á í ó ú ñ ¿ ¡` | 12 |
| Français | `À Â Æ Ç É È Ê Ë Î Ï Ô Œ Ù Û Ü Ÿ` and the lowercase, `« »` | none (v2 has `Æ æ Œ œ Ÿ ÿ « »`) | 0 |
| Deutsch | `Ä Ö Ü ä ö ü ß` (`„ “` are mapped, 3.2.4) | `Ä ä ß` | 3 |
| Português | `Á À Â Ã Ç É Ê Í Ó Ô Õ Ú` and the lowercase | `Ã Õ ã õ` (the others are counted under Español) | 4 |
| Italiano | `À È É Ì Ò Ó Ù` and the lowercase | `Ì Ò ì ò` (`Ó ó` counted under Español) | 4 |
| Türkçe | `Ç Ğ İ I Ö Ş Ü Â Î Û ç ğ ı i ö ş ü â î û` | none | 0 |
| Kurdî (Kurmancî) | `Ç Ê Î Ş Û ç ê î ş û` | none | 0 |
| Русский | `А` to `Я`, `Ё`, `а` to `я`, `ё` (66 letters), `« »` | all 66 | 66 |
| **Required total** | | | **12 + 3 + 4 + 4 = 23 Latin, 66 Cyrillic = 89** |

(`Á Í Ó Ú Ñ Ä Ã Õ Ì Ò` are the 10 capitals and `á í ó ú ñ ä ã õ ì ò` the 10 lowercase letters; with `ß ¿ ¡` that is 23. `Ó ó Á á Í í Ú ú` are counted once, under Español, although Portuguese and Italian use them too.)

The plan's "about 130" is met by a **names tier** of 38 more Latin letters (`Č Š Ž Ě Ř Ň Ć Ń Ś Ź Ý Ż Ő Ű Ă Ą Ę Ł Ø` and lowercase, listed in 3.2.2): letters that appear in people's names and in
chat titles a user types, outside the ten alphabets. **89 + 38 = 127 new glyphs.** The names tier is an extra (cut first among the glyphs, section 6); a title with such a letter then drops to the system font as DL 13.1 says.

#### 3.2.2 The exact code (to be pasted into `PixelFont.kt`; checked in the simulation)

New marks, declared **next to the existing marks, before `init`** (Kotlin initialises in textual order, AGENTS.md "Hard-won facts"):

```kotlin
    private val TILDE = listOf(".xx.x", "x.xx.")
    private val CARON = BREVE                       // at this size a caron and a breve are the same two rows
    private val DACUTE = listOf("..x.x", ".x.x.")
    private val OGON_R = listOf("....x", "...x.")   // under the right foot of A, a, E
    private val OGON_E = listOf("...x.", "....x")   // under the end of the bottom bar of e
```

`MAP` additions (keys by escape so no editor turns them into a space), appended to the existing `mapOf(...)`:
`' ' to " "`, `' ' to " "`, `'‐' to "-"`, `'‑' to "-"`, `'„' to "\""`, `'ʼ' to "'"`, `'‹' to "<"`, `'›' to ">"`, `'№' to "No"`, `'ª' to "a"`, `'º' to "o"`.
(French narrow no-break space, thin space, hyphens, the German opening quote, the modifier apostrophe of Kurdish, single guillemets, the Russian numero sign, the Spanish ordinals. The existing keys stay.)

`init` ends with `defV2()` and then `defV3()` (**one added line**). The glyph map's initial capacity becomes 512 (no data change).

```kotlin
    private fun alias(c: Char, base: Char) { glyphs[c] = glyphs[base] ?: return }   // the same shape object

    /** Pixel font v3 (Wave 1b): Latin accents of es, de, pt, it; the Cyrillic alphabet; the names tier. Needs v1 and v2. */
    private fun defV3() {
        // ---- Latin, required (23): capitals take marks on rows 0-1, lowercase on rows 2-3, i-forms use the dotless base
        accent('Á', 'A', ACUTE, 0); accent('Í', 'I', ACUTE, 0); accent('Ó', 'O', ACUTE, 0); accent('Ú', 'U', ACUTE, 0)
        accent('Ñ', 'N', TILDE, 0); accent('Ä', 'A', DIAER, 0); accent('Ã', 'A', TILDE, 0); accent('Õ', 'O', TILDE, 0)
        accent('Ì', 'I', GRAVE, 0); accent('Ò', 'O', GRAVE, 0)
        accent('á', 'a', ACUTE, 2); accent('í', 'ı', ACUTE, 2); accent('ó', 'o', ACUTE, 2); accent('ú', 'u', ACUTE, 2)
        accent('ñ', 'n', TILDE, 2); accent('ä', 'a', DIAER, 2); accent('ã', 'a', TILDE, 2); accent('õ', 'o', TILDE, 2)
        accent('ì', 'ı', GRAVE, 2); accent('ò', 'o', GRAVE, 2)
        def('ß', 3, ".xxx./x...x/x...x/x.xx./x...x/x...x/x.xx.")
        def('¿', 3, "..x../...../..x../.x.../x..../x...x/.xxx.")     // the flipped question mark
        def('¡', 3, "x/./x/x/x/x/x")                                  // the flipped exclamation mark

        // ---- Cyrillic, required (66). Eighteen letters have the shape of a Latin letter and are aliases of it.
        for ((cy, la) in "АВЕКМНОРСТХ".zip("ABEKMHOPCTX")) alias(cy, la)       // А В Е К М Н О Р С Т Х (11)
        for ((cy, la) in "аеорсху".zip("aeopcxy")) alias(cy, la)                // а е о р с х у (7)
        // capitals, rows 3 to 9 (Ц and Щ have a tail on row 10)
        def('Б', 3, "xxxxx/x..../x..../xxxx./x...x/x...x/xxxx.")
        def('Г', 3, "xxxxx/x..../x..../x..../x..../x..../x....")
        def('Д', 3, ".xxxx/.x..x/.x..x/.x..x/.x..x/xxxxx/x...x")
        def('Ж', 3, "x.x.x/x.x.x/.xxx./..x../.xxx./x.x.x/x.x.x")
        def('З', 3, ".xxx./x...x/....x/.xxx./....x/x...x/.xxx.")
        def('И', 3, "x...x/x...x/x..xx/x.x.x/xx..x/x...x/x...x")
        def('Л', 3, "..xxx/.x..x/.x..x/.x..x/.x..x/x...x/x...x")
        def('П', 3, "xxxxx/x...x/x...x/x...x/x...x/x...x/x...x")
        def('У', 3, "x...x/x...x/x...x/.xxxx/....x/...x./xxx..")
        def('Ф', 3, "..x../.xxx./x.x.x/x.x.x/x.x.x/.xxx./..x..")
        def('Ц', 3, "x...x/x...x/x...x/x...x/x...x/x...x/xxxxx/....x")
        def('Ч', 3, "x...x/x...x/x...x/.xxxx/....x/....x/....x")
        def('Ш', 3, "x.x.x/x.x.x/x.x.x/x.x.x/x.x.x/x.x.x/xxxxx")
        def('Щ', 3, "x.x.x/x.x.x/x.x.x/x.x.x/x.x.x/x.x.x/xxxxx/....x")
        def('Ъ', 3, "xx.../.x.../.x.../.xxx./.x..x/.x..x/.xxx.")
        def('Ы', 3, "x....x/x....x/x....x/xxx..x/x..x.x/x..x.x/xxx..x")
        def('Ь', 3, "x.../x.../x.../xxx./x..x/x..x/xxx.")
        def('Э', 3, ".xxx./x...x/....x/..xxx/....x/x...x/.xxx.")
        def('Ю', 3, "x..xx./x.x..x/x.x..x/xxx..x/x.x..x/x.x..x/x..xx.")
        def('Я', 3, ".xxxx/x...x/x...x/.xxxx/..x.x/.x..x/x...x")
        accent('Ё', 'E', DIAER, 0)      // the same picture as Ë: a declared alias in the tests
        accent('Й', 'И', BREVE, 0)      // И must be defined first
        // lowercase, rows 5 to 9 unless noted (б and ф rise to row 3, ф and у descend; д ц щ have a tail)
        def('б', 3, "..xxx/.x.../x..../xxxx./x...x/x...x/.xxx.")
        def('в', 5, "xxxx./x...x/xxxx./x...x/xxxx.")
        def('г', 5, "xxxx/x.../x.../x.../x...")
        def('д', 5, ".xxxx/.x..x/.x..x/.x..x/xxxxx/x...x")
        def('ж', 5, "x.x.x/x.x.x/.xxx./x.x.x/x.x.x")
        def('з', 5, "xxxx./....x/.xxx./....x/xxxx.")
        def('и', 5, "x...x/x..xx/x.x.x/xx..x/x...x")
        def('к', 5, "x..x./x.x../xx.../x.x../x..x.")
        def('л', 5, "..xxx/.x..x/.x..x/x...x/x...x")
        def('м', 5, "x...x/xx.xx/x.x.x/x...x/x...x")
        def('н', 5, "x...x/x...x/xxxxx/x...x/x...x")
        def('п', 5, "xxxxx/x...x/x...x/x...x/x...x")
        def('т', 5, "xxxxx/..x../..x../..x../..x..")
        def('ф', 3, "..x../..x../.xxx./x.x.x/x.x.x/x.x.x/.xxx./..x../..x..")
        def('ц', 5, "x...x/x...x/x...x/x...x/xxxxx/....x")
        def('ч', 5, "x...x/x...x/.xxxx/....x/....x")
        def('ш', 5, "x.x.x/x.x.x/x.x.x/x.x.x/xxxxx")
        def('щ', 5, "x.x.x/x.x.x/x.x.x/x.x.x/xxxxx/....x")
        def('ъ', 5, "xx.../.x.../.xxx./.x..x/.xxx.")
        def('ы', 5, "x....x/x....x/xxx..x/x..x.x/xxx..x")
        def('ь', 5, "x.../x.../xxx./x..x/xxx.")
        def('э', 5, ".xxx./....x/..xxx/....x/.xxx.")
        def('ю', 5, "x..xx./x.x..x/xxx..x/x.x..x/x..xx.")
        def('я', 5, ".xxxx/x...x/.xxxx/.x..x/x...x")
        accent('ё', 'e', DIAER, 2)      // the same picture as ë
        accent('й', 'и', BREVE, 2)

        defV3Names()
    }

    /** The names tier (38, an extra): letters of people's names outside the ten alphabets. Delete the call to cut it. */
    private fun defV3Names() {
        for ((c, b) in listOf('Č' to 'C', 'Š' to 'S', 'Ž' to 'Z', 'Ě' to 'E', 'Ř' to 'R', 'Ň' to 'N')) accent(c, b, CARON, 0)
        for ((c, b) in listOf('Ć' to 'C', 'Ń' to 'N', 'Ś' to 'S', 'Ź' to 'Z', 'Ý' to 'Y')) accent(c, b, ACUTE, 0)
        accent('Ż', 'Z', DOT, 0); accent('Ő', 'O', DACUTE, 0); accent('Ű', 'U', DACUTE, 0); accent('Ă', 'A', BREVE, 0)
        for ((c, b) in listOf('č' to 'c', 'š' to 's', 'ž' to 'z', 'ě' to 'e', 'ř' to 'r', 'ň' to 'n')) accent(c, b, CARON, 2)
        for ((c, b) in listOf('ć' to 'c', 'ń' to 'n', 'ś' to 's', 'ź' to 'z', 'ý' to 'y')) accent(c, b, ACUTE, 2)
        accent('ż', 'z', DOT, 2); accent('ő', 'o', DACUTE, 2); accent('ű', 'u', DACUTE, 2); accent('ă', 'a', BREVE, 2)
        accent('Ą', 'A', OGON_R, 10); accent('ą', 'a', OGON_R, 10); accent('Ę', 'E', OGON_R, 10); accent('ę', 'e', OGON_E, 10)
        def('Ł', 3, "x..../x..../x.x../xx.../x..../x..../xxxxx")
        def('ł', 3, "xx./.x./.xx/xx./.x./.x./xxx")
        def('Ø', 3, ".xxx./x..xx/x.x.x/x.x.x/x.x.x/xx..x/.xxx.")
        def('ø', 5, ".xxxx/x..xx/x.x.x/xx..x/xxxx.")
    }
```

Count: Latin 23 + Cyrillic (18 aliases + 20 drawn capitals + `Ё Й` + 24 drawn lowercase + `ё й` = 66) = **89**; names tier: caron 12, acute 10, dot 2, double acute 4, breve 2, ogonek 4 and the four drawn letters `Ł ł Ø ø` = **38**. All 127 were rendered in the simulation. The simulation also
found: **no two glyphs of the whole font have the same shape except the twenty declared aliases** (`А/A В/B С/C Е/E К/K М/M Н/H О/O Р/P Т/T Х/X а/a с/c е/e о/o р/p х/x у/y` and `Ё/Ë ё/ë`), the dotless `ı` is the base of `í ì`
(a 3-wide glyph takes the 5-wide acute through the centring rule: the mark lands on columns 1 and 2), and the caron on the 4-wide `r` lands on columns 1 to 3.

Design notes (so a reviewer knows what was intended): `З` is round with a centred middle bar (`.xxx.`), `Э` has its bar run to the right edge (`..xxx`), `з` is flat, `э` round: they differ by one pixel row each and are the likeliest to be mistaken (LOOK-B checks them, section 5; the written
fallback is to give `Э` a bar from column 1 and `З` a notch at row 4). `ъ` has its serif on the top left, `ь` has none and is one column narrower. `ц ч ш щ` differ by the last-row tail and the middle strokes. `ß` is drawn with open stem gaps on rows 6 and 9 so it does not read as `B`
(the proposed alternative, if it does: rows `.xxx./x...x/x...x/x.xx./x...x/x...x/xxxx.`). `Ы Ю ы ю` are 6 wide, `Ь ь г` are 4 wide, `¡` is 1 wide, every other new glyph is 3 or 5 wide. Spacing, kerning and `PixelText` layout need no change.

#### 3.2.3 Frozen data

`pixelfont-v1.golden` stays as it is. `pixelfont-b.golden` is written by `PixelFontGoldenTest` in write mode (`PIXELFONT_GOLDEN_B_WRITE=1`): every character from 0x20 to 0x2FFF that has a glyph and is not in the v1 file, same line format. It is written **after LOOK-B has judged the sheet and the FIX round is done**, once, by the slice's integrator, and a later change of any glyph must regenerate it in the same commit with the reviewer's name in the message (1a's rule for hashes). Until the file exists `v2AndV3GlyphsUnchanged` fails with the same message style as the v1 test.

#### 3.2.4 What the translators may use

Titles, tabs and chips (kinds `title`, `tab`, `chip`) are drawn in the pixel font: every character in them must be in the alphabets above, ASCII, or one of: `' " - ... · • → ← × ✓ « » ¿ ¡ °`. Body texts are in the system font and may hold anything the system font can show. `MAP` accepts the typographic marks of 3.2.2 in any text. This sentence goes into the glossaries' room rules (request to LANG, section 7).

### 3.3 The tests of the font and the sheet (details in section 4)

`PixelFontSheetDump` (`T/ui/theme/`): a JUnit test that does nothing unless the environment variable `FONT_SHEET_DIR` (an absolute path) is set; then it writes with `javax.imageio` (same pattern as 1a's `SceneStillsDump`): `font-new-dark.png`, `font-new-light.png` (the 127 new glyphs in groups with captions, a grid cell is 8 by 12 font pixels at zoom 6, faint guide lines on rows 3 (cap line), 5 (x-height) and 10 (baseline), the group names `latin-accents`, `cyrillic-capitals`, `cyrillic-lowercase`, `punctuation`, `names-tier`), `font-all-dark.png` (every glyph), `font-specimens-dark.png` and `font-specimens-light.png`
(ten lines, one per language, at zoom 4 and again at zoom 2, plus the same lines at the three sizes that `pixelFontPx` gives (about 1.5u, 2u and 2.5u, DL 2.2) drawn at the phone's own pixel size, so LOOK-B sees what the phone shows). Colours: dark sheet background `#2B2B33` with ink `#F3EAFB`, light sheet `#F4EEE4` with ink `#2A1F33`.
The specimen lines (they are also the proposed new lines of the debug gallery, request to INTEGRATE-B):

| Code | Line |
|---|---|
| en | `Home Chats Library Bots Agent` |
| es | `Inicio Biblioteca ¿Qué quieres? ¡Hola, Ñandú! Más aún: bebé, canción` |
| fr | `Accueil Bibliothèque « Où ça ? » Œuvre Ça Ÿ Âme Île Noël` |
| de | `Größe Ärger Übung Straße Schließen Öffnen` |
| pt | `Início Ação Coração Põe Avó Máquina Você` |
| it | `Più è già però Città Libertà Perché Ìì Òò` |
| tr | `İstanbul ışık Çağrı Ğğ Şş Ü Ö â î û` |
| ru | `Привет, мир! Съешь ещё этих мягких французских булок, да выпей чаю. ЁЖЩЪЫЭЮЯ` |
| id | `Beranda Obrolan Perpustakaan Bot Agen` |
| ku | `Têkilî Şev Çûn Dîtin Hêlîn Rûniştin` |

The dump runs **in slice B**, in the first `-Test` run after this area's files land (it needs only the JVM and no 1a code); SPLIT 4.7 says "after INTEGRATE-B compiles", which would be after the review that needs it (open point 2).

### 3.4 The sprites

All three files are generated by `craft_b.py` (3.6), never edited by hand. Sprite names are unique across all `.pxl` files. **No sprite here may reuse a char with a different colour inside one file.**

#### 3.4.1 `tabicons.pxl`: the five tab icons (B25)

Palette: `. none`, `x #FFFFFF` (tinted at draw time, as `icons.pxl`). Each sprite is 12 by 12 with **four frames**: frame 0 is the rest picture and equals the static icon it is named after (`frame 1000`); frames 1, 2, 3 are the play, 90 ms each (`frame 90`; 90 ms is `Dur.TAP`, three of them 270 ms, between `BASE` 220 and `SLOW` 320). The bar plays frames 1, 2, 3 once when the tab is chosen and lands on frame 0.
**The sprites are chosen by index**: they must be drawn with `PixelImage(name, frame = n, tint = ..., playing = false)` and never with `playing = true` (that loops all four frames, a flash every 1.27 s; same gotcha as 1a's `scene_woodpile`).

| Sprite | Frame 0 equals | What moves (the recipe, in the operations of 3.6) | Opaque pixels, frames 0 to 3 (simulated) | Pixels changed between neighbouring frames: 0 to 1, 1 to 2, 2 to 3, 3 to 0 |
|---|---|---|---|---|
| `tabicon_home` | `icon_hearth` | the flame tip flicks: f1 `move(rows 0-1, dx -1)`; f2 `move(rows 0-2, dx +1)`; f3 `clear (0,6)` (the tip is gone), then it returns | 74, 74, 74, 73 | 4, 10, 7, 1 |
| `tabicon_chats` | `icon_chats` | the tail wags and the second text line comes last: f1 `move(rows 9-10, dx -1)`; f2 `move(rows 9-10, dx +1)`; f3 `clear row 6 cols 2-5` | 55, 55, 55, 51 | 4, 6, 8, 4 |
| `tabicon_library` | `icon_library` (3.4.2) | the middle book is pulled out of the shelf and put back: f1 `move(rows 3-10, cols 5-7, dy -1)`; f2 `dy -2`; f3 `dy -1` | 71, 71, 71, 71 | 10, 10, 10, 10 |
| `tabicon_bots` | `icon_bots` | a blink: f1 fills the top row of the two eyes (cells (4,3) (4,4) (4,7) (4,8)); f2 also fills the second row (5,3) (5,4) (5,7) (5,8) and clears the antenna (0,5) (0,6); f3 equals f1 | 84, 88, 90, 88 | 4, 6, 6, 4 |
| `tabicon_agent` | `icon_scroll` | the lines of the scroll are written one by one: f1 clears the three lines (row 5 cols 3-7, row 7 cols 3-7, row 9 cols 3-5); f2 restores row 5; f3 restores rows 5 and 7; frame 0 has all three | 53, 40, 45, 50 | 13, 5, 5, 3 |

The Agent tab keeps the scroll that the Hermes tab has today (the plan renames the tab; the owner knows the scroll): open point 4. The Home tab keeps the flame. Rules (tested): frame 0 equals the static icon; every neighbouring pair of frames (including 3 to 0) differs in 1 to 14 pixels; the opaque count of frames 1 to 3 is within 70 to 130 per cent of frame 0; no operation moves a pixel off the 12 by 12 grid (the generator asserts it).
Under animation level OFF the bar shows frame 0 and plays nothing; under REDUCED it plays as under FULL (it is three still frames, no particles).

#### 3.4.2 `icons_b.pxl`: `icon_library` (12 by 12, one frame, palette `x`)

```
............
............
............
.....xxx....
.....xxx.xxx
.xxx.x.x.xxx
.xxx.xxx.xxx
.x.x.xxx.xxx
.xxx.xxx.x.x
.xxx.x.x.xxx
.xxx.xxx.xxx
xxxxxxxxxxxx
```
Three books on a shelf (71 opaque pixels, simulated): the middle one is tallest, the dots are spine labels. `PixelIcon.LIBRARY` (SHELL) draws it; if LOOK-B cuts it the fallback is the existing `icon_archive` (a box), named in `ArtBSprites` (3.4.5) so SHELL has one place to look. This picture is a proposal: the builder may redraw it, but frame 0 of `tabicon_library` must keep equal to it (the generator reads both from the same data).

#### 3.4.3 `illustrations_b.pxl`: the key guide, the Library's pictures and what 1a cut

Palette (one block, the same 21 chars and colours as 1a's `illustrations.pxl`, 1a contract 3.5, so the pictures sit together): `. none`, `o #170E22` (plum outline), `s #342C48`, `S #52496B`, `h #7B7196` (stone), `w #B0603E`, `W #EA9252` (fire-lit), `l #3B1E1A`, `L #663626` (wood), `g #A81F1A`, `r #DC3818`, `f #F7691A`, `m #FFA726`, `y #FFDF4F`, `c #FFF9D8` (fire), `p #F1E4C0`, `q #C9B287`, `i #3A2A22` (parchment, shade, ink), `n #2F6B50`, `N #4E9A6B` (green), `v #3B4A7A`, `V #8FA5DE` (glass, water). No char `1` to `4` (those are tint ramp chars).
Rules (the same as 1a's `illustrationRules`, tested here for this file): tiles `tile_*` 24 by 24, one frame, at most 7 colours including the outline, no opaque pixel in the outer ring; pictures `empty_*` and `wel_key_*` 32 by 32, two frames (`frame 1600`, `frame 160`), at most 10 colours, a margin of one pixel, frame 1 differs from frame 0 in 1 to 12 pixels; the largest 8-connected island holds at least 90 per cent of the opaque pixels; the outline `o` is present on every object; light comes from the lower right (the fire); drawn at 3 dp per pixel (96 dp) for pictures and 2 dp per pixel (48 dp) for tiles; dither only with the 4 by 4 Bayer matrix; objects of the hearth, never Pêt; **no letters, no digits, no logo, no brand mark** (the key guide must not copy any provider's interface).
They are decorative: the caller's text speaks (no spoken label here).

**The key guide (B10), core, always drawn** (the WELCOME areas place them and caption them; three pictures, one idea each, readable without a word):

| Sprite | What it says | Composition (32 by 32; x from the left, y from the top) | Colours |
|---|---|---|---|
| `wel_key_1` | "open the page": a small window with a globe in it | a window x 3 to 28, y 6 to 25: outline `o`, frame `s`, a title bar y 7 to 9 in `S` with three dots `h` at x 6, 9, 12 (y 8), paper `p` inside (x 5 to 26, y 11 to 23) with `q` on the lowest two rows and the right edge; a globe centred at (16,17), radius 5: disc `V`, shade `v` on the lower right, two land patches `N` with `n` edges, a one-pixel meridian in `v`. Frame 1: the land patches move one pixel (the globe turns), 6 to 12 pixels change | `o s S h p q V v N n` (10) |
| `wel_key_2` | "make a key": a key lying on a stone, sparkling | a key from upper right to lower left: a bow (ring) centred (22,10), outer radius 5, hole transparent, in `m` with its lower-right rim `y` and upper-left shade `w`; a shaft 3 pixels wide to (8,24); two teeth at the end in `m` with `y` edges; a flat stone under it y 25 to 29 in `S`, top `h`, shade `s`; two sparkle crosses of `c` (3 pixels each) at (5,6) and (27,22). Frame 1: the sparkles swap to single dots and move one pixel (4 to 8 pixels change) | `o w m y c S h s` (8) |
| `wel_key_3` | "put it in Agirî": a key about to enter the hearth's keyhole | a stone block x 4 to 27, y 12 to 29: face `S`, top edge `h`, right and bottom shade `s`, outline `o`; a keyhole in `o` (a disc of radius 2 at (16,19) and a slot down to y 25); the key above it, pointing down: a bow at y 2 to 8 and a shaft to y 14 in `m`/`y`/`w`; a small flame on the block's top right at (24, 6 to 11) in `r f m y`. Frame 1: the flame leans one pixel, 3 to 8 pixels change | `o S h s m y w r f` (9) |

**Pictures with a consumer in 1b, drawn when 1a did not ship them** (names are those of 1a's catalogue, `IllustrationId`; the sentences are 1a's 3.5, repeated so this file is complete):

| Sprite | Shows | Consumer in 1b |
|---|---|---|
| `empty_scroll` | a blank open scroll `p q i` and a quill | `ChatMessageList.kt` (a new chat), BARS |
| `empty_stool` | an empty three-legged stool `L l w W` by a cold stone `s S h` | `BotsScreen.kt` (no bots), BARS |
| `empty_hourglass` | an hourglass in a wooden frame `L l`, glass `v V`, one sand grain `y` falling | `RoutinesScreen.kt`, BARS |
| `empty_jar` | a glass jar `v V` with two stars `y c` and room for more | the Saved shelf (`StarredList`), LIBRARY |
| `empty_cup` | a tipped clay cup `w W L` with a thin wisp `h S`: something went wrong | the Library's Error state |

**New in 1b, always drawn:** `empty_basket`, 32 by 32, two frames: a low wicker basket (x 5 to 26, y 16 to 27) woven in a diagonal checker of `w` and `L`, its rim `W` on the lit side, a handle arc in `L` (x 8 to 23, y 6 to 16), the opening a dark ellipse `l` (y 15 to 17) so it reads as empty, a ground shadow `s` under it and one `c` sparkle on the rim that moves one pixel in frame 1. Colours `o l L w W s c` (7). Consumer: the Files shelf's empty state (LIBRARY: "the files you give the agent will be here"). It is not in 1a's `IllustrationId`, so its name is in `ArtBSprites` (3.4.5).

**Extras with no consumer in 1b** (drawn last, only when absent from 1a's file; later waves use them): `tile_library` (24 by 24: three scrolls `p q i` standing in a basket `w W L`, 7 colours), `tile_usage` (24 by 24: a log stack `L l` with rings `m`), `empty_lantern` (a lit lantern on bare ground), `empty_boat` (a paper boat on a still pond), `empty_gate` (a closed gate with a keyhole). Briefs as 1a 3.5.

**No Welcome picture per step**: the Welcome is one continuous scene (DL 8.4), so the scene is the picture; the three key pictures above are the only pictures this area draws for it (decision, SPLIT 4.7 left it open).

**Which names are drawn** is decided by the generator from what 1a shipped, so no name can exist twice: `ONE_A_NAMES` lists the 15 names of 1a's catalogue. If `A/pixel/illustrations.pxl` is **absent** (slice B before 1a is merged), none of `ONE_A_NAMES` is emitted and only `wel_key_1..3` and `empty_basket` are; if it is **present**, every wanted name of `ONE_A_NAMES` that it does not contain is emitted, in the order consumers first, extras last. After 1a merges, INTEGRATE-B runs `craft_b.py` once more (the CI `--check` fails until it has been run: that is the reminder).

#### 3.4.4 Review and cuts

Sheets and verdicts as in 1a 7.1/7.2 (section 5). A picture that is not clearly good enough is cut and the consumer keeps its fallback; the three key pictures are not cut but redrawn (a text-only guide would lose B10's point; fallback if one cannot be made good: the WELCOME UI shows that step as a card with the pixel-font numeral and no picture).

#### 3.4.5 `J/ui/pixel/ArtBSprites.kt` (new, pure, no Android import)

```kotlin
package dev.pocketagent.app.ui.pixel

/** The five animated tab icons (tabicons.pxl). Drawn by index: PixelImage(name, frame = TabIconSprites.frameAfter(t), tint = ..., playing = false). */
object TabIconSprites {
    const val HOME = "tabicon_home"; const val CHATS = "tabicon_chats"; const val LIBRARY = "tabicon_library"
    const val BOTS = "tabicon_bots"; const val AGENT = "tabicon_agent"
    val ALL: List<String> = listOf(HOME, CHATS, LIBRARY, BOTS, AGENT)
    const val FRAME_COUNT = 4
    const val STEP_MS = 90L
    const val PLAY_MS = 270L                       // 3 * STEP_MS
    /** The frame to show [elapsedMs] after the tab was chosen: 1, 2, 3 for 90 ms each, then 0 (the rest picture). A negative time and a time at or past PLAY_MS give 0. */
    fun frameAfter(elapsedMs: Long): Int
}

/** The Welcome's key guide (illustrations_b.pxl): 32 by 32, 2 frames; draw at 96 dp (3 dp a pixel) or 64 dp (2 dp). */
object WelcomeSprites {
    const val KEY_1 = "wel_key_1"; const val KEY_2 = "wel_key_2"; const val KEY_3 = "wel_key_3"
    val KEYS: List<String> = listOf(KEY_1, KEY_2, KEY_3)
}

/** Names that 1b adds to 1a's catalogue (IllustrationId). Every consumer keeps a fallback when SpriteBank.find returns null. */
object ExtraSprites {
    const val ICON_LIBRARY = "icon_library"; const val ICON_LIBRARY_FALLBACK = "icon_archive"
    const val EMPTY_BASKET = "empty_basket"; const val TILE_LIBRARY = "tile_library"
}
```
`frameAfter`: `if (elapsedMs < 0 || elapsedMs >= PLAY_MS) 0 else 1 + (elapsedMs / STEP_MS).toInt()`.

### 3.5 The splash flame, `R/drawable/ic_splash_flame.xml` (B3, vector)

Made once with the existing tool from the fire's own pixels, the sprite `fire_mid`, frame 0 (32 by 36: flame, logs and ring of stones, the picture of the hearth), one sprite pixel = 4 viewport units, in a 288 by 288 viewport (the size of an Android 12 splash icon; the system shows a circle of two thirds of it, radius 96 units):

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\pxl-to-vector.ps1 -File app\src\main\assets\pixel\fire.pxl -Sprite fire_mid -Frame 0 -Out app\src\main\res\drawable\ic_splash_flame.xml -Pixel 4 -Viewport 288 -SizeDp 288
```
Simulated run: 13 colours, 3,149 bytes, the art spans x 84 to 204 and y 108 to 212 (120 by 104 units), the farthest corner of any rectangle from the centre is **74.4 units, inside the 96 of the mask**. The file is committed as a source (a generated file with its header comment), not produced by `craft_b.py` (the PowerShell tool is the only writer, so there is one implementation). The splash shows plum `#1D1220` behind it (START's theme). Whether the handoff animation to the scene works is START's experiment S8; the vector is the same either way (a plain fade uses it too). If START prefers a flame without stones, a second drawing is a request to this area (open point 11); the default stays the whole hearth because it is the picture the scene shows, so the handoff lands on identical pixels.
`SplashFlameVectorTest` (section 4) holds the radius and the colours.

### 3.6 The generator `tools/art/craft_b.py` (new) and the README

```
python tools/art/craft_b.py [--only icons|tabicons|illustrations] [--out DIR] [--check] [--selftest] [--report] [--preview-dir DIR]
```
(`python3` on CI.) Starts with `sys.dont_write_bytecode = True` and `sys.path.insert(0, HERE)` (python `-I` does not put its folder on the path). It imports `pxl` (the strict reader) and **nothing from `craft_art.py`** (1a edits that file; no coupling). Integers and strings only: no `random`, no floats, so every platform produces the same bytes.

| Function | Meaning |
|---|---|
| `write_pxl(path, header_lines, palette, sprites)` | the same text format as `craft_art.write_pxl` (palette block, `sprite name w h`, `frame ms`, rows; LF; trailing newline), **parses its own text with `pxl.parse` before writing** and exits 1 on any error (a bad row would make the app skip the whole file); prints `Wrote <path> (<n> sprites)` |
| `static_grid(sprites, name)` | frame 0 of a sprite of `icons.pxl` (read with `pxl.parse_file`) as a grid of `.`/`x` |
| `move(g, r0, r1, c0, c1, dx, dy)`, `clear(g, cells)`, `fill(g, cells)`, `copy(g)` | the operations of the table in 3.4.1; `move` asserts that nothing leaves the grid |
| `build_icons_b()`, `build_tabicons()`, `build_illustrations_b()` | the three files; the last one applies the `ONE_A_NAMES` rule of 3.4.3 |
| `BUILDERS = {"icons": ("icons_b.pxl", ...), "tabicons": ("tabicons.pxl", ...), "illustrations": ("illustrations_b.pxl", ...)}` | `--only`, `--check` (builds into a temporary folder, prints `ok <file>` or `DIFF <file>`, exit 1 on any difference, writes nothing in the tree), `--out` (writes into another folder) as in `craft_art.py` |
| `--selftest` (exit 0 or 1, under 5 s, no Pillow) | the numeric rules of section 4 that need no JVM: frame 0 equals the static icon, neighbouring frames differ by 1 to 14 pixels, opaque counts, colour counts, sizes, frames, margins, islands, names unique against every other `.pxl` in the folder, `ONE_A_NAMES` not duplicated, palette chars equal across the three files where they overlap |
| `--report` | one line per sprite: name, size, frames, colours, opaque pixels, pixels changed between frames (the numbers DATA-B pastes next to the sheets) |
| `--preview-dir DIR` | writes ink-tinted copies for the sheets (the white `x` icons are invisible on a light background and `contact_sheet.py` has no tint option): `DIR/tabicons-dark.pxl`, `tabicons-light.pxl`, `icons_b-dark.pxl`, `icons_b-light.pxl` with `x` = `#F3EAFB` and `#2A1F33` |

README: **only one new section is appended at the end** of `tools/art/README.md`, headed "Wave 1b art (`craft_b.py`)": the three generated files in the same table style, the commands above, the contact-sheet commands of section 5, the rule "frame 0 of a tab icon is the static icon: after editing `icons.pxl` run `craft_b.py`", and the splash vector command of 3.5. The existing rows and sentences (and whatever 1a changes in them) are not touched; the sentence "six text files" becomes true or false depending on 1a, and INTEGRATE-B corrects the count (request).

### 3.7 States, motion, haptics, sound, texts, tags, stored data (this area has no screen)

- **The five states** (DL 8.16) do not apply: this area makes assets, not a screen. What each asset does when it is missing or when animation is off is its **fallback**: a missing `tabicon_*` draws nothing (SHELL falls back to the static icon `PixelIcon.*`); `icon_library` falls back to `icon_archive`; a missing `empty_*`, `wel_key_*` or `tile_*` falls back to `Mascot` or to the icon as the consumer's draft says; the splash with no vector shows only the plum window; a title with a letter without a glyph is drawn whole in the system font by `PixelTitle` (and statically prevented by the tests); animation level OFF shows frame 0 of every sprite.
- **Motion:** the tab icons play 3 frames of `Dur.TAP` (90 ms) once on selection (DL pattern 8); the picture pairs loop slowly (1600 ms rest, 160 ms spark, as 1a's empties) through `PixelImage` and obey the animation level like every sprite. Nothing here animates in the scene.
- **Haptics and sound:** none. The tab tap's haptic and click belong to SHELL's bar (`HapticCue` and the UI tap cue); this area adds no cue.
- **Texts:** no strings, no spoken labels (the pictures are decorative; the tab label is SHELL's `shell_tab_*`; the captions of the key guide are WELCOME's `wel_*`).
- **Test tags and debug links:** none new. The proof uses the existing debug-link route `gallery` and the other areas' scenarios (section 5).
- **Stored data and migration:** none. Assets and resources only: no preference, no file, nothing in a backup. The rollback APK has its own assets and is unaffected; the previous build never sees the new files. An installation that exists changes nothing on disk.

## 4. Unit tests (JUnit 4, JVM; files are read with `Assume.assumeTrue(file.exists())`)

| Class | Test | What it proves |
|---|---|---|
| `PixelFontGoldenTest` | `v1GlyphsUnchanged` (existing, untouched) | the 135 v1 lines still equal |
| | `v2AndV3GlyphsUnchanged` | every line of `pixelfont-b.golden` equals the current glyph (the new glyphs and v2 are frozen after LOOK-B) |
| `PixelFontCoverageTest` | `tenAlphabetsHaveGlyphs` | for the audit table of 3.2.1 (kept inside the test: English and Indonesian A-Z a-z; es, fr, de, pt, it, tr, ku, ru as listed there) every letter has a glyph |
| | `cyrillicRangeIsComplete` | U+0410 to U+044F, U+0401 and U+0451 all have a glyph, and their count is 66 |
| | `glyphCountFloor` | `glyph()` answers for at least 238 characters (149 today including the space, plus 89), and for at least 276 when `Ł` has a glyph (the names tier is in) |
| | `typographyIsMapped` | every key of `MAP` prepares to characters that all have a glyph (so `„ ’ ‑ №` never reach the fallback) |
| | `shippedTitlesDrawInPixelFont` (`Assume`: resources found and a folder `values-ru` exists) | for every `values*` folder and every key whose kind is `title`, `tab` or `chip` (from the note `| kind: X -->` or from the globs of `tools/strings-kinds.txt` `[kinds]`), `PixelFont.textCols(text) != null` after placeholder removal (a second part to run once the translations exist; until then the alphabets carry it) |
| `PixelFontLangTableTest` (added in slice C, when LANG's `LangTable` accessor is final, request 6) | `everyAlphabetIsDrawable` | every `LangTable` entry's `alphabet` string has a glyph for each of its letters |
| `PixelFontRulesTest` | `accentedLettersKeepTheirBase` | for each of the 58 composed glyphs (the 20 Latin accent letters of the required set, `Ё Й ё й`, and the 34 composed letters of the names tier) every row outside the mark rows equals the base glyph's row (the base letter is never damaged) and the mark rows hold only accent pixels in the allowed rows: capitals 0 to 1, lowercase 2 to 3, ogonek 10 to 11 |
| | `newGlyphsStayOnGrid` | every new glyph has 12 rows of one width, `x` and `.` only, is not empty, pixels only on its allowed rows (capitals 3 to 9, Ц Щ also 10, lowercase 5 to 9, б ф from 3, ф and д ц щ below 9 as drawn, accents as above) |
| | `shapesAreDistinctExceptDeclaredAliases` | grouping all glyphs by shape, the only groups of more than one are the twenty declared aliases of 3.2.2 (catches a pasted duplicate such as `З` equal to `Э`) |
| | `aliasesShareTheLatinShape` | the 18 Cyrillic aliases have the rows of their Latin glyph; `Ё` equals `Ë`, `ё` equals `ë`. The table of the test writes the Cyrillic letters as code points (`0x0410` and so on), so a Latin letter typed by mistake into the Kotlin source cannot pass for a Cyrillic one |
| | `widthsAreSane` | every new glyph is 1 to 6 wide, and only `Ы Ю ы ю` are 6, `¡` is 1, the rest 3 to 5 |
| | `prepareKeepsNewLettersWhole` | `PixelFont.prepare("Ёж й ñ ß")` returns the same text (NFC composes `е` + U+0308 to `ё`, `и` + U+0306 to `й`) and `textCols` of the ten specimen lines is not null |
| `PixelFontSheetDump` | `writeSheets` | writes the PNGs of 3.3 when `FONT_SHEET_DIR` is set; skipped otherwise |
| `ArtBSpritesTest` | `frameAfterSteps` | -1 gives 0, 0 and 89 give 1, 90 and 179 give 2, 180 and 269 give 3, 270 and 10,000 give 0 |
| | `namesAreUniqueAndWellFormed` | all constants match `[a-z0-9_]+`, no duplicate, `TabIconSprites.ALL.size == 5` |
| | `everyNameExistsInTheAssets` (`Assume`) | every constant of the three objects except `ExtraSprites.TILE_LIBRARY` and `ICON_LIBRARY_FALLBACK` names a sprite of some `.pxl` file in the folder; `icon_archive` exists in `icons.pxl` |
| `ArtBAssetsTest` | `newFilesParseAndNamesAreUnique` | the three files parse with `PxlParser` and share no name with each other or with any other `.pxl` file in the folder |
| | `tabIconsHaveFourFramesAndRest` | each is 12 by 12, 4 frames, durations 1000, 90, 90, 90, palette chars `x` only |
| | `tabIconRestEqualsTheStaticIcon` | frame 0 equals frame 0 of `icon_hearth`, `icon_chats`, `icon_library`, `icon_bots`, `icon_scroll` (pixel by pixel) |
| | `tabIconFramesChangeALittle` | neighbouring frames (3 to 0 included) differ in 1 to 14 pixels; frames 1 to 3 hold 70 to 130 per cent of frame 0's opaque pixels |
| | `libraryIconRules` | `icon_library` is 12 by 12, one frame, `x` only, 50 to 90 opaque pixels, row 11 is full |
| | `illustrationRules` | for every sprite present in `illustrations_b.pxl`: the size, frame, colour-count, margin, island and frame-difference rules of 3.4.3 by prefix (`tile_` and `empty_`/`wel_key_`) |
| | `wantedNamesAreEmitted` | `wel_key_1..3` and `empty_basket` are in `illustrations_b.pxl` (the only presence assertions: they are core and are never cut, only redrawn) |
| | `iconsPxlIsUntouched` | the CRC32 of `icons.pxl` (CRLF normalised) equals the value recorded in the test (the builder records it when the file is first read in slice B) |
| `SplashFlameVectorTest` | `vectorIsInsideTheSplashCircle` | the file parses as XML, `viewportWidth = viewportHeight = 288`, `width = height = 288dp`, and every corner of every `M x,y h w v h` rectangle lies within 96 units of (144, 144) |
| | `vectorColoursComeFromTheFire` | every `fillColor` is a colour of the palette of `fire.pxl`; the header comment names `fire_mid` |
| Existing | `PxlParserTest.everyShippedFileParsesAndNamesAreUnique`, `PixelFontTest.*` (all of them, unchanged) | still green; `coversEverythingInShippedStrings` now also covers the nine new language folders |

`craft_b.py --selftest` repeats the sprite rules in Python (so CI can run them without the JVM, optional) and `craft_b.py --check` is the CI step (request to INTEGRATE-B).

## 5. How it is proven (no phone needed for most; owner package: pictures only, `"safe": true`)

**Without the phone (slice B).** DATA-B (mechanical; no downloads) runs, from the repository root:
1. `python tools/art/craft_b.py` then `--check`, `--selftest`, `--report > docs/waves/w1b/look/artb-report.txt`; `python tools/art/craft_b.py --preview-dir dist/art/w1b-preview`.
2. The JVM tests of section 4, with `FONT_SHEET_DIR=<absolute path to docs/waves/w1b/look>` set for `PixelFontSheetDump` (the lead's test runner, `tools\local-build.ps1 -Test`).
3. Sheets into `docs/waves/w1b/look/` (each PNG at most 400 KB; Pillow is installed; fallback `tools/pixel-preview.ps1`, never a download):
   `python tools/art/contact_sheet.py --file dist/art/w1b-preview/tabicons-dark.pxl --frames all --scale 12 --bg "#2B2B33" --out docs/waves/w1b/look/tabicons-dark.png` and the same with `tabicons-light.pxl`, `--bg "#F4EEE4"` (`tabicons-light.png`); both again at `--scale 2` (`tabicons-dark-2x.png`, `-light-2x.png`: the size on the phone is 24 dp, 2 dp a pixel);
   `icons_b-dark.png`, `icons_b-light.png` from the preview of `icons_b`; `--file app/src/main/assets/pixel/illustrations_b.pxl --frames all --scale 6` on `--bg "#2B2B33"` (`illustrations-b-dark.png`) and on `--bg "#F4EEE4"` (`illustrations-b-light.png`); plus the PNGs of 3.3.
**LOOK-B** (a reviewer who did not write the art; looks at the PNGs only) writes `docs/waves/w1b/look/review-ART-B.txt`, one line per sprite `<name> KEEP`, `<name> FIX <what>` or `<name> CUT <reason>`, and one line per glyph group `font:latin-accents`, `font:cyrillic-capitals`, `font:cyrillic-lowercase`, `font:punctuation`, `font:names-tier`, `font:specimens` with `KEEP` or `FIX <letters and why>` (a glyph is never CUT; the names tier as a group may be `CUT`). `LookReviewFilesTest` (1a's INTEGRATE writes it; request 8) is extended to the three new `.pxl` files and this review file. Checklist (every answer yes, or fix or cut):
- Glyphs: each new letter is readable at 1x and at 2x on both backgrounds; the pairs `З Э`, `з э`, `ъ ь`, `Ъ Ь`, `ц ч`, `ш щ`, `и й`, `в б`, `у ч` are told apart; the Cyrillic capitals have the same weight and height as the Latin ones (rows 3 to 9); accent marks never touch the letter below (a gap row stays), the tilde, the acute and the grave read as different marks on `Ñ Ã Á Ì`; `ß` is not `B`; `¿ ¡` read as flipped; `Ы` is not wider than its neighbours look; the ten specimen lines look like one font; no letter looks smaller or bolder than its neighbours.
- Tab icons: frame 0 is the icon the owner knows (hearth, chats, bots, scroll) or the new library; the four frames read as one small movement with no flicker; frames 1 to 3 stay inside the icon's bounds; the library reads as books.
- Pictures: one object each, says what its step or screen is without a word, plum outline visible on the dark and the light background, not a copy of Pêt, no letters or logos, at most 10 colours, no stray pixel except designed sparkles, the second frame is a calm change; the three key pictures read as a sequence (page, key, lock).

**On the phone (T and P, after INTEGRATE-B; the twin or owner-debug with pins and pictures only).**
- Glyphs in context: the debug-link route `gallery` (`--es route gallery`, exists in Wave 0) shows the specimen block of `DesignGallery.kt`; after INTEGRATE-B appends the ten lines of 3.3 it is photographed once in NEWROZ dark and once in NERGIZ light (the robot adds the token; no data is written). The full proof is the `w1b-languages` scenario (SHELL/lead): the five main screens in the ten languages, in which every title, tab and chip is drawn in the pixel font; `PixelFontCoverageTest.shippedTitlesDrawInPixelFont` is its static twin.
- Tab icons: the five tabs photographed at rest in both themes (scenario `w1b-tabs`, SHELL); the animation is judged on one screen recording of the twin while tapping each tab (`adb shell screenrecord`, three seconds, frames read from the video); the numbers (`frameAfter`) are unit-tested.
- Key pictures: part of the Welcome's scenario (`w1b-welcome`, T, wiped twin).
- Splash flame: START's experiment S8 on the twin (cold start to first frame under 700 ms; the flame visible inside the circle).
- Old data: nothing is written by this area; `dist\owner-backup\` is not touched.

**Only the owner's eyes can judge:** whether the Cyrillic and the new accented letters feel like the same hand-drawn font; whether the Agent tab's scroll should stay; whether the tab animations feel calm; whether the three key pictures help someone who has never seen a key page; the taste of the library icon.

## 6. Core and extras, and the order of cutting

**Core (ships):** the 89 required glyphs and the `MAP` additions; the golden, the coverage and rules tests and the sheet dump; `tabicons.pxl` (five sprites, four frames) and `icon_library`; `wel_key_1..3`; `empty_basket`; the 1a pictures that have a 1b consumer (`empty_scroll`, `empty_stool`, `empty_hourglass`, `empty_jar`, `empty_cup`) when 1a did not ship them; `ic_splash_flame.xml`; `craft_b.py` with `--check` and `--selftest`; the README section; `ArtBSprites.kt`.
**Extras, first cut first:** 1. `--report` and `--preview-dir` (the sheets can then be made from a hand-tinted copy); 2. `tile_usage`, `empty_gate`, `empty_boat`, `empty_lantern`, `tile_library` (no consumer in 1b); 3. the second frame of the pictures (a one-frame picture still passes `illustrationRules` with the frame rule skipped); 4. the names tier (38 glyphs: delete the call `defV3Names()` and the names rows of the tests, the golden is written without them); 5. frames 1 to 3 of the tab icons one icon at a time, the least good first (a tab icon with only the rest frame makes SHELL's bar play nothing for that tab); 6. `empty_cup`, `empty_jar` (their consumers fall back to `Mascot`).
**Never cut:** the Latin accents and the Cyrillic alphabet (a title missing one letter drops to the system font, which breaks the plan's "ten languages in the app's own font"), `icon_library` (with its fallback `icon_archive` named), the rest frames of the tab icons, `wel_key_1..3` (redrawn, not dropped), `ic_splash_flame.xml`, the golden freeze.

## 7. REQUESTS to other areas, and what this area provides

**Requests (exact):**
1. **SHELL** (`PocketBottomBar.kt`, `PixelIcon.kt`): draw the five tabs with `PixelImage(name = TabIconSprites.<NAME>, frame = TabIconSprites.frameAfter(elapsedMs), tint = ..., playing = false)`; on selection start a clock and stop it at `TabIconSprites.PLAY_MS`; under animation level OFF use frame 0; fall back to the static `PixelIcon` when `SpriteBank.find(name)` is null. Add `PixelIcon.LIBRARY` (sprite `icon_library`); if `SpriteBank.find("icon_library")` is null use `ExtraSprites.ICON_LIBRARY_FALLBACK`. The tab's sprite for the Agent tab is `TabIconSprites.AGENT`.
2. **BARS** (`EmptyState(art = ...)`): at the four call sites pass `IllustrationId.EMPTY_BUBBLE.sprite` (Chats), `EMPTY_STOOL.sprite` (Bots), `EMPTY_SCROLL.sprite` (a new chat), `EMPTY_HOURGLASS.sprite` (Routines) (1a's names, found across all `.pxl` files whether 1a or this area drew them); fallback `Mascot` when `SpriteBank.find` is null; draw at 96 dp (3 dp a pixel).
3. **LIBRARY:** Files shelf empty state `ExtraSprites.EMPTY_BASKET`; Saved shelf empty `IllustrationId.EMPTY_JAR.sprite`; Error state `IllustrationId.EMPTY_CUP.sprite`; fallback `Mascot`; the shelf title icon `icon_library`.
4. **WELCOME-CORE** (`KeyGuide.kt`): the three pictures are `WelcomeSprites.KEYS` (in this order: page, key, lock); **WELCOME-UI** (`KeyGuideUi.kt`): draw each with `PixelImage(name, Modifier.size(96.dp))` or 64 dp (2 dp a pixel; never a non-integer scale), the pictures are decorative, the captions are the UI's own `wel_*` strings; no step text inside the picture.
5. **START:** `R.drawable.ic_splash_flame` (288 dp, the art inside the 96-unit radius); `windowSplashScreenAnimatedIcon` in `values-v31/themes.xml` points at it; the window colour stays `#1D1220`.
6. **LANG:** (a) expose, in `J/ui/text/Languages.kt`, `object LangTable { val ALL: List<LangEntry> }` and `class LangEntry(val tag: String, ..., val alphabet: String)` where `alphabet` lists every letter that language's titles use, both cases, plus its non-ASCII punctuation, equal to the audit of 3.2.1 (names may differ: tell the lead and `PixelFontLangTableTest` follows); (b) put 3.2.4 (the characters allowed in titles, tabs and chips, and the `MAP` marks allowed anywhere) into the room rules of every glossary and into the translators' briefs; (c) a translator who needs a letter this font lacks writes it in a body text or asks the lead: it is never worked around with a look-alike letter.
7. **INTEGRATE-B:** (a) CI: after the step "Generated pixel art is current" add `python3 tools/art/craft_b.py --check` (and `--selftest` in the data-and-art step); (b) after 1a merges, run `python tools/art/craft_b.py` once so `illustrations_b.pxl` holds exactly the 1a pictures that are missing, then `--check`; (c) `tools/size-budget.txt`: this area adds about 25 KB of text assets and about 6 KB of Kotlin (the 2 MiB of 1b is not at risk); (d) `NOTICE`: nothing to add; (e) append the ten specimen lines of 3.3 to `SPECIMEN` in `J/ui/components/DesignGallery.kt` (a debug-only screen nobody owns in 1b) so the phone proof has something to photograph; (f) correct the sentence "six text files" in `tools/art/README.md` after 1a and 1b both merged.
8. **LEAD / INTEGRATE-B:** extend `LookReviewFilesTest` (1a writes it) to `tabicons.pxl`, `icons_b.pxl`, `illustrations_b.pxl` and the review file `review-ART-B.txt`, and the golden write (`PIXELFONT_GOLDEN_B_WRITE=1`) after LOOK-B.
9. **LEAD:** confirm the four extra Kotlin and test files of 3.1 (open point 1), the Agent tab's scroll (open point 4), and tell the builder at the start of slice B whether 1a's review file exists (3.4.3).

**This area provides:**
- `PixelFont` glyphs for the ten alphabets (`glyph`, `textCols`, `prepare`; same signatures); the `MAP` marks; `pixelfont-b.golden`.
- Sprites: `tabicon_home|chats|library|bots|agent` (12 by 12, 4 frames), `icon_library`, `wel_key_1|2|3`, `empty_basket`, and (when 1a cut them) `empty_scroll`, `empty_stool`, `empty_hourglass`, `empty_jar`, `empty_cup`, `tile_library`, `tile_usage`, `empty_lantern`, `empty_boat`, `empty_gate`.
- `TabIconSprites`, `WelcomeSprites`, `ExtraSprites` (section 3.4.5); the drawable `ic_splash_flame`.
- `tools/art/craft_b.py` (`--check`, `--selftest`, `--report`, `--preview-dir`) and the sheet commands.
- The audit table of 3.2.1 (for LANG's `alphabet` strings) and 3.2.4 (the allowed characters).

## 8. Risks

1. **Drawn without an eye in the loop.** The glyphs and sprites were simulated as text, not seen as pictures. The weak ones are `З/Э`, `ß`, `Ы`, `Ю`, the library icon and the three key pictures. LOOK-B and one FIX round are part of the slice; the fallbacks are written (3.2.2 design notes, 3.4.4).
2. **Tab icons played by a consumer that loops them.** `playing = true` would flash every 1.27 s. The names are in a pure object with the timing function and a comment; SHELL's test should assert the call is index-driven (request 1).
3. **`craft_b.py --check` depends on files other areas edit** (`icons.pxl`, `illustrations.pxl`): it shows DIFF when they change, which is intended (frame 0 must equal the static icon; the 1a names must not be duplicated), and the fix is one run of the script. After 1a merges nobody may forget it: the CI step is the reminder.
4. **Name collision with 1a** if the generator is run in slice B and 1a ships a picture afterwards: prevented by the `ONE_A_NAMES` rule (3.4.3) and caught by `PxlParserTest` (a duplicate name keeps the first by file order and would silently draw 1a's or mine).
5. **The golden freezes a mistake** if it is written before LOOK-B: the order in 3.2.3 prevents it; a later FIX regenerates it in the same commit.
6. **Languages that need a letter outside the 89 + 38** (Czech `ď ť ů ľ`, Vietnamese, Polish `ś` is covered, Romanian `ș ț`, Ukrainian `і ї є ґ`, Kurdish Sorani and others): out of scope (the ten); a title with such a letter falls to the system font whole, as DL 13.1 says; Wave 12 adds scripts.
7. **`PixelText` mixes tiers per character** (`SectionHeader`, `EmptyState` titles and `PocketDialog` titles call it directly, not `PixelTitle`): DL 13.1 says a title with an unknown character drops whole. Not changed in 1b (no owner; the tests keep it from happening for the ten languages); noted for Wave 1b INTEGRATE or a later wave.
8. **The splash circle.** The vector keeps every corner inside 74.4 of 96 units, so Android's mask cuts nothing; but where the system draws the splash larger or smaller than 288 dp the flame scales with it, and whether the handoff lands on the scene's flame is S8's result, not this area's.
9. **Size of `PixelFont.kt`:** about 150 added lines; a `when`-free table, no cost at runtime (the map fills once at class load: 127 more `PixelGlyph` objects of 12 short strings each, well under 100 KB).
10. **Typography the translators invent** (an `ʼ` or a `‑` we did not list, `Ǝ`): `PixelFontTest.coversEverythingInShippedStrings` already fails on any character above 0x7E that cannot be drawn, so it is caught at once, in every folder.

## 9. Open points for the lead (each has a default, so the builder can start)

1. **Five extra files** (`ArtBSprites.kt`, `ArtBSpritesTest.kt`, `ArtBAssetsTest.kt`, `SplashFlameVectorTest.kt`, `PixelFontRulesTest.kt`; and `PixelFontLangTableTest.kt` in slice C) are not in SPLIT 4.7's file list. Default: confirmed; they are new files nobody else owns.
2. **The font sheet dump runs in slice B** (the first `-Test` run after this area's files land), not "after INTEGRATE-B compiles" as SPLIT 4.7 says, because LOOK-B needs it before slice C. Default: slice B.
3. **The numbers.** The required set is 89 glyphs (23 Latin, 66 Cyrillic), not "about 130"; the plan's figure is reached with a 38-glyph names tier (127 in all). French needs none of SPLIT's listed glyphs (Wave 0's `defV2` has them). Default: ship all 127, the names tier cut first.
4. **The Agent tab's icon is the scroll** (the Hermes tab's icon today; the plan only renames the tab). Default: scroll; the owner may prefer another of the 60 icons, which changes one line of `craft_b.py` and the frames of `tabicon_agent`.
5. **Welcome pictures per step: none.** The scene is the picture (DL 8.4); only the three key pictures are drawn. Default: as stated.
6. **`LangTable`'s accessor names** (request 6a) are LANG's to fix; this area's coverage test has its own table so nothing waits.
7. **`empty_basket` is not in 1a's `IllustrationId`.** Default: consumers use `ExtraSprites.EMPTY_BASKET`; the lead may ask 1a's INTEGRATE to add an entry instead (then `ExtraSprites` loses the constant).
8. **Which 1a pictures were cut** is unknown until 1a's LOOK has run. Default: the generator decides from `illustrations.pxl` (3.4.3); the lead tells the builder whether the review file exists.
9. **The key pictures' size.** 32 by 32 at 96 dp, three in a row need 288 dp plus gaps (fits 360 dp phones with 16 dp gutters but not with larger gaps); WELCOME-UI may draw them at 64 dp (2 dp a pixel). Default: WELCOME-UI chooses either integer scale.
10. **Splash flame with or without stones.** Default: the whole hearth (`fire_mid`, frame 0), because it is what the scene shows. If S8 shows that a flame-only crop lands better, START asks and this area makes a second vector (the PowerShell tool has no crop option, so it needs a small sprite `splash_flame` in `illustrations_b.pxl` and a second run).
11. **`PixelText`'s per-character fallback** (risk 7): leave, or give it to INTEGRATE-B as a one-line change (`textCols == null` draws the whole text in the system font). Default: leave.
