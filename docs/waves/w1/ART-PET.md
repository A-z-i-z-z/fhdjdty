# Wave 1a contract draft: ART-PET (Pêt's fifteen animations, the shadow, the small Pêt)

Written 2026-11-25 from the files at HEAD `fe0e0ea`. Facts below were measured with `tools/art/pxl.py` on `mascot.pxl`, not remembered. Drafts for the plan: `docs/phase4-plan.md` 1.5 (line 829), design language section 7 (lines 385 to 409), `docs/phase1-contract.md` (the `.pxl` format).

## 0. Facts about Pêt as drawn today (the rig is built on these)

- 32 x 32 frames, one palette of 24 letters (`k K b B c r R o O q y Y d e w a m t s S z f g h`, `mascot.pxl` lines 3 to 28). Light comes from the right: rim pixels `R r K` on the right edge, outline `k` elsewhere. Rest pose = `pet_idle` frame 0: 545 opaque pixels, one 8-connected island, bounding box x 1..27, y 5..31, feet on row 31.
- Rest coordinates (x,y, inclusive): HEAD x7..27 y5..18 (top outline row 5, chin row 18); EYES left block x9..14, right block x18..23, rows 10..14 (dark `e`, highlight `w` at (10,11)(11,11)(10,12) and (19,11)(20,11)(19,12)); cheeks `t` (8,16)(9,16)(23,16)(24,16); mouth `m` x15..18 row 17; TORSO x7..27 y19..27 (belly `o` x14..21); FEET y28..31; TAIL x1..9 y18..29 (tip x1..5 rows 19..25, joins the body on rows 26..28).
- Existing pieces the rig copies, all measured: closed eyes = `pet_idle` f7 rect (8,10)-(24,15); head 1 px lower, eyes open = `pet_idle` f4 rect (7,6)-(27,19); head lower, eyes closed = f3 same rect; head lower, mouth open with tongue (`mttm` at row 19) = `pet_talking` f1 rect (7,6)-(27,20); tail up = `pet_idle` f6 rect (0,18)-(9,29), tail down = f1 rect (0,19)-(9,29); right arm raised = `pet_waving` f3 rect (25,5)-(31,22); both arms up and hopping = `pet_happy` f1 (bottom row 30), f2 and f3 (bottom 28), f4 (29); landing = f5 (bottom 31, three sparkles). Looking right = highlights one pixel right (what `pet_thinking` f0 does).
- Room for props: rows 0..4 above the head; columns 28..31 on rows 0..21; columns 0..6 on rows 0..17. **The fire sprite is blitted after Pêt** (`SceneRenderer.kt:153` to 154, Pêt origin `fx-43`, fire origin `fx-16`) so fire columns 0..4 cover Pêt columns 27..31; `fire_high` frame 0 has opaque pixels in columns 1..4 on rows 23..33, which land on Pêt rows 19..29 (fire row minus 4). Rule: **no prop pixel at x >= 28 on rows >= 22.**
- Existing edge-outline ratio (share of the main island's edge pixels that are `k K e`): 0.80 to 0.97 in all 51 frames. Existing hop height 3 px (`pet_happy` f2, f3 bottom row 28).
- Prototype run (scratch, not in the repo): `look`, `lids` and `closed` below give clean faces; pasting the waving arm unchanged (dy 0) reads as a raised arm; **pasting it lowered by 5 or 8 rows reads as a flipper**, so lowered arms are typed patches (3.3), never shifted copies.

## 1. Scope

Core = the rig, the manifest, the review tooling, and the clips marked C. Cut order, first to last (a cut clip is not drawn and not listed; nothing else changes):

| # | Work item | Class | Cut order |
|---|---|---|---|
| 1 | `tools/art/pet_clips.py` (rig, clips, `--check`, `--report`, `--selftest`) and `mascot_anim.pxl` | core | never |
| 2 | `PetArt` manifest and `PetArtTest` | core | never |
| 3 | `pet_shadow` (4 lengths) | core | never |
| 4 | `pet_blink`, `pet_tail`, `pet_yawn`, `pet_stretch`, `pet_read`, `pet_carry`, `pet_startled`, `pet_dance` | core | 11 to 4, in this reverse order: dance, startled, carry, read, stretch, yawn, tail, blink last |
| 5 | `pet_head` (note icon, 4 faces), `pet_small` (16 x 16 body), `pet_peek` (16 x 10, over the top bar) | core | after 4 |
| 6 | `pet_listen`, `pet_write`, `pet_snow`, `pet_shelter` | extra but wanted | 3 to 6 |
| 7 | `pet_warm`, `pet_firefly` | extra | 2 |
| 8 | `pet_poke` (weakest: the fire's stones hide the arm's reach, see 11) | extra | 1, cut first |

A clip that fails the review (section 9) is dropped, listed in `dist/w1a/pet/review.txt` and left out; Pêt then keeps today's pose there (PET falls back to `pet_idle` when `PetArt.clip` returns null).

## 2. Files

| File | Action | One line |
|---|---|---|
| `tools/art/pet_clips.py` | create | The generator: reads `mascot.pxl`, composes the clips from parts, writes `mascot_anim.pxl`. Python 3.12, stdlib only, `sys.dont_write_bytecode = True`, imports `pxl.py` from its own folder |
| `app/src/main/assets/pixel/mascot_anim.pxl` | create (generated, never edited by hand) | The 15 clips, `pet_shadow`, `pet_head`, `pet_small`, `pet_peek` |
| `app/src/main/assets/pixel/mascot.pxl` | **not touched** | Deviation from the plan table (it names `mascot.pxl`): the seven moods stay byte-identical, and a syntax error in the new file loses only the new file (`SpriteBank.kt:19` to 21 skips a bad file as a whole), never Pêt himself |
| `app/src/main/java/dev/pocketagent/app/ui/pixel/PetArt.kt` | create | Pure Kotlin manifest (3.4). Not under `ui/pixel/pet/` (PET owns that folder) |
| `app/src/test/java/dev/pocketagent/app/ui/pixel/PetArtTest.kt` | create | Section 8 |

## 3. Shared symbols PROVIDED

### 3.1 Sprite names, sizes, palette (the file contract)

All 15 clips are 32 x 32, drawn in the same frame as `pet_idle`: **blit origin, feet row (31) and hip position are identical, so every clip is drawn at the same `(x, y)` as `pet_idle`** (today `fx-43, fbase-31`). No per-sprite anchors exist. Frame durations are in ms, per frame, in the `.pxl` `frame <ms>` lines. `intro/outro` count frames played once at the start/end of a LOOP.

| Sprite | Kind | Frames (intro/loop/outro) | ms per frame | Total ms | Airborne frames |
|---|---|---|---|---|---|
| `pet_blink` | SHOT | 4 | 70, 90, 70, 60 | 290 | none |
| `pet_tail` | SHOT | 6 | 90, 110, 140, 110, 90, 60 | 600 | none |
| `pet_warm` | SHOT | 7 | 100, 120, 400, 400, 400, 120, 80 | 1620 | none |
| `pet_yawn` | SHOT | 6 | 120, 140, 500, 400, 160, 100 | 1420 | none |
| `pet_stretch` | SHOT | 6 | 100, 140, 500, 400, 130, 100 | 1370 | none |
| `pet_firefly` | SHOT | 8 | 160, 160, 160, 160, 140, 140, 160, 100 | 1180 | none |
| `pet_poke` | SHOT | 8 | 110, 110, 90, 100, 90, 120, 110, 80 | 810 | none |
| `pet_read` | `head(dn)` and `look(dx,1)` in 0..4. 0 intro: SCROLL_READ at (10,23), `look(0,1)`; 1 SCROLL_READ (10,20), dx -1; 2 dx 0; 3 dx 1; 4 dx 0; 5 outro and last frame: `rest()` exactly (the scroll is gone; the lowering happens in frame 0 only on the way in) |
| `pet_write` | SHOT | 7 | 100, 150, 150, 150, 150, 300, 80 | 1080 | none |
| `pet_carry` | LOOP | 6 (1/4/1) | 100, 300, 300, 300, 300, 100 | 1400 | none |
| `pet_startled` | SHOT | 6 | 60, 80, 100, 80, 140, 100 | 560 | 1, 2, 3 |
| `pet_dance` | SHOT | 8 | 110, 110, 100, 110, 110, 100, 120, 90 | 850 | 1, 2, 4, 5, 6 |
| `pet_listen` | LOOP | 6 (1/4/1) | 100, 320, 320, 320, 320, 100 | 1480 | none |
| `pet_shelter` | LOOP | 6 (1/4/1) | 120, 180, 180, 180, 180, 120 | 960 | none |
| `pet_snow` | SHOT | 8 | 150, 130, 130, 130, 130, 220, 260, 100 | 1250 | none |
| `pet_shadow` | STILL | 48 x 5, 4 frames (a length level, not a time) | 1000 each | n/a | n/a |
| `pet_head` | STILL | 16 x 16, 4 faces: 0 neutral, 1 blink, 2 happy, 3 worried | 1000 each | n/a | n/a |
| `pet_small` | LOOP | 16 x 16, 4 frames | 700, 90, 700, 300 | 1790 | none |
| `pet_peek` | SHOT | 16 x 10, 6 frames | 120, 120, 500, 500, 90, 120 | 1450 | n/a |

Playing rules (PET codes against these): a SHOT is played once from frame 0 to the end, then the scene shows `pet_idle` from its frame 0 (the last SHOT frame is pixel-identical to it, so there is no jump). A LOOP plays `[0, intro)`, then repeats `[intro, n-outro)` while its condition holds, and on the end of the condition plays `[n-outro, n)` once and cuts to `pet_idle` frame 0 (the last frame is identical to it). `Sprite.frameAt` loops the whole sprite and must NOT be used for clips; PET indexes `Sprite.frames` itself. STILL sprites are shown with a fixed frame index (`PixelImage(frame = n)`).

**Palette of `mascot_anim.pxl`:** the 24 letters of `mascot.pxl` with identical colours (the generator copies the block from `mascot.pxl`, so they cannot drift) plus eight new letters, each used only by props and the shadow:

| Letter | Colour | Why |
|---|---|---|
| `G` | `#5FAE4B` | leaf body (`pet_shelter`) |
| `l` | `#A6DC72` | leaf light side (right, towards the fire) |
| `L` | `#2F6B3A` | leaf shade, outline and stem |
| `n` | `#7A4A2A` | twig and pencil wood |
| `N` | `#4A2A1A` | twig and pencil shade |
| `p` | `#D9C7A0` | paper shade (scroll, notebook) |
| `v` | `#700F0818` | shadow, light coverage (`#AARRGGBB`, alpha 0x70) |
| `V` | `#A00F0818` | shadow, core (alpha 0xA0) |

No pure black or pure white anywhere. Props are outlined with `k` (shaded side `K` on the right), lit with `R/g/Y` on the right edge, like Pêt.

### 3.2 The generator and its command lines

`python tools/art/pet_clips.py` (from the repository root) writes `app/src/main/assets/pixel/mascot_anim.pxl`, prints `Wrote <path> (<n> sprites, <m> frames)`. Flags: `--out DIR` writes there instead; `--check` writes nothing, prints `ok mascot_anim.pxl` or `DIFF mascot_anim.pxl`, exit 1 on a difference (same convention as `craft_art.py`); `--report [NAME]` prints per frame the bounding box, bottom row, island count, edge-outline ratio, changed cells against the previous frame and against rest, and the eye-centroid check of 8 (the numbers the tests assert); `--selftest` asserts every rig operation (below) on a hand-computed 8 x 8 grid, exit 0. Deterministic: no randomness, no `sin`, output sorted by the order of the table in 3.1 (shadow, head, small, peek last).

Structure of the script (one file, in this order): `load_rest()` (reads `mascot.pxl` with `pxl.parse_file`, builds named source grids), `PROPS` (typed patches, 3.3), the rig operations, `CLIPS` (a list of `(name, [(ms, recipe), ...])`), `SMALL` (the 16 x 16 hand-typed frames), `SHADOW`, `emit()`. A grid is a 32 x 32 list of lists of single characters, `.` transparent.

**Rig operations** (applied in the order written in a recipe, each returns the grid; a later op overwrites an earlier one):

| Op | Exact behaviour |
|---|---|
| `rest()` | `pet_idle` frame 0 |
| `src(name, f)` | whole frame `f` of an existing sprite of `mascot.pxl` |
| `head(kind)` | `kind` in `up` (rest), `dn` (f4 rect (7,6)-(27,19)), `dn_closed` (f3 same rect), `talk` (`pet_talking` f1 rect (7,6)-(27,20)); clears rows 5..20 x7..27 of the grid, pastes the rect. `up2` = head 1 px higher than rest: paste the rest head rect at dy -1, then copy rest row 18 (x9..24) into row 18 so the neck stays closed |
| `look(dx, dy)` | the six highlight pixels of the rest face become `e`, then are redrawn displaced by `(dx, dy)`, each clamped inside its eye block (x9..14 / x18..23, rows 10..13). With `head dn`/`talk` add 1 to every y. Range dx -1..1, dy -1..1 |
| `lids(n)` | n = 1 or 2: rows 10..9+n of both eye blocks filled with `B` (half-closed) |
| `closed()` | paste `pet_idle` f7 rect (8,10)-(24,15) over the eyes (all cells, `.` included); with `head dn` use f3 rect (8,11)-(24,16) |
| `wide()` | the two highlights become 2 x 2 blocks `w` at (10..11, 11..12) and (19..20, 11..12) |
| `tail(src)` | `src` in `up`, `down`: clear rect (0,18)-(9,29), paste that rect of f6 / f1 |
| `tailstretch(n, curl)` | n in -3..3, curl in -1..1: on the rest tail tip (x0..5 rows 19..25) insert (n < 0, tail goes up) or delete (n > 0) \|n\| copies of rest row 22 slice x0..5, the tip rows above row 22 shift by `curl` in x; rows 26..28 (the joint) never change; assert no opaque pixel leaves x0..9 |
| `arm_up(dy)` | the `pet_waving` f3 rect (25,5)-(31,22) pasted at dy 0 only (dy > 0 is forbidden, see 0) |
| `arm_fwd(y)` | the typed patch `ARM_FWD` (3.3) pasted with its top row at `y` (15 or 17) |
| `hop(n)` | n >= 0: the whole grid moves up n px; rows that leave the top must be empty (assert); rows entering at the bottom are empty (feet leave the ground; the shadow stays) |
| `down(n)` | the whole grid moves down n px (assert the bottom row stays <= 31); used to plant a lifted source frame on the ground |
| `prop(name, x, y)` | paste a typed patch from `PROPS` with its top-left at (x, y); `.` does not overwrite, anything else does; asserts: inside 32 x 32; no cell at x >= 28 on rows >= 22; no overlap with the eye blocks except where the recipe says `over_eyes` |
| `px(x, y, ch)` | one pixel (sparkles, drops, confetti) with the same bounds assertions |

### 3.3 Typed patches (the only hand-drawn pixels)

Each is typed as text in `PROPS`, outlined `k`/`K`, lit on the right. The generator asserts the stated size; the agent draws them looking at the nearest existing art (the arm patch beside `pet_waving`, the scrolls beside the avatar art).

| Patch | Size | Where it is used | What it shows |
|---|---|---|---|
| `ARM_FWD` | 6 x 4 | `pet_warm` (y 15 and 17), `pet_poke` (y 17), `pet_write` | The right forearm held out to the right at chest height: top and bottom rows `k`, hand end `y`-spotted with `R` rim and `K` edge, forearm `b`/`c`, joins the torso at x26 |
| `SCROLL_READ` | 14 x 7 | `pet_read` at (10, 20), intro frame at (10, 23) | A scroll held open across the belly: rolled ends 2 px wide (`d`, `a`), page `w` with a `p` lower row, three text lines in `a`, `k` outline top and bottom |
| `SCROLL_CARRY` | 11 x 5 | `pet_carry` at (17, 21) (bob: y 21 and 20) | A small rolled scroll tied with `y`, `k` outline |
| `BADGE` | 3 x 3 | `pet_carry` at (26, 18) | `h` ring, `g` centre; frame 2 and 4 swap `h` for `O` (it pulses) |
| `NOTEBOOK` | 11 x 7 | `pet_write` at (10, 21) | Cover `q` with `K` spine on the left, page edge `w` on the right, `k` outline |
| `PENCIL` | 1 x 4 | `pet_write`, drawn with `ARM_FWD` at y 17, tip toward the notebook | `y` body, `n` tip, `e` point |
| `TWIG` | 3 x 2 | `pet_poke` at (29, 21) | `n` with `N` underside |
| `LEAF` | 16 x 5 | `pet_shelter` at (9, 0) | A leaf over the head: `L` outline, `G` body, `l` on its right half, a `L` vein row; stem `L` from its right end (x24..25, row 4) down to the raised hand at (28, 5) |
| `FLAKE` | 3 x 3 | `pet_snow` | plus shape: `w` centre, `z` arms |
| `FIREFLY` | 3 x 3 | `pet_firefly` | plus shape: `Y` centre, `g` arms; centre toggles `Y`/`g` every frame |
| `BANG` | 2 x 6 | `pet_startled` at (28, 0) | exclamation mark: rows 0..3 `y` with `d` right column, row 5 `y` dot |
| `ARCS` | 3 x 7 | `pet_listen` at (28, 8) | two sound arcs `z`; frames alternate 1 and 2 arcs |
| `SHADOW` | 48 x 5 | `pet_shadow` | see 3.5 |

### 3.4 Clip recipes (frame by frame, pixel offsets)

Notation: frame number = index; every recipe starts from `rest()` unless it says `src(...)`. "Dark" and "light" refer to the head rect of 0. The last frame of every clip is exactly `rest()`; the first is one visible step from it.

| Clip | Frames |
|---|---|
| `pet_blink` | 0 `lids(1)`; 1 `closed()`; 2 `lids(1)`; 3 `rest()` |
| `pet_tail` | 0 `tailstretch(-1,0)`; 1 `tailstretch(-3,0)` `look(-1,0)`; 2 `tailstretch(-4,1)` `look(-1,0)`; 3 `tailstretch(-3,0)` `look(-1,0)`; 4 `tailstretch(-1,0)`; 5 `rest()` |
| `pet_warm` | 0 `look(1,0)`; 1 `look(1,0)` `arm_fwd(17)`; 2 `look(1,0)` `arm_fwd(15)`; 3 `lids(2)` `arm_fwd(15)`; 4 `look(1,0)` `arm_fwd(15)`; 5 `look(1,0)` `arm_fwd(17)`; 6 `rest()` |
| `pet_yawn` | 0 `head(dn)`; 1 `head(talk)`; 2 `head(talk)` `closed()`; 3 `head(up2)` `closed()` with the mouth of `talk` pasted at row 17 (rest row 17 x14..19 replaced by `kmmmmk` widened to `kmmmmmmk` x13..20); 4 `head(dn_closed)`; 5 `rest()` |
| `pet_stretch` | 0 `src(pet_idle,4)`; 1 `src(pet_happy,1)` then `down(1)` (feet on row 31); 2 and 3 the same grid, 3 with `tail(up)`; 4 `src(pet_idle,4)`; 5 `rest()` |
| `pet_firefly` | fly path (centre x,y) 0 (29,4); 1 (24,2); 2 (19,1); 3 (14,2); 4 (9,3); 5 (5,6); 6 none; 7 none. Face: 0 `look(1,-1)`; 1 `look(1,-1)`; 2 `look(0,-1)` `head(up2)`; 3 `look(-1,-1)` `head(up2)`; 4 `look(-1,0)` `head(up2)`; 5 `look(-1,0)`; 6 `lids(1)`; 7 `rest()`. The fly pixels may not touch the head outline |
| `pet_poke` | `look(1,0)` in 0..6. 0 `arm_fwd(17)`; 1 `arm_fwd(17)` `prop(TWIG,29,21)`; 2 same, TWIG at (30,20) and `px(30,16,g)` `px(31,14,f)`; 3 as 1; 4 as 2 with `px(29,15,f)`; 5 as 1 with `px(31,17,g)`; 6 `arm_fwd(17)`; 7 `rest()` |
| `pet_read` | `head(dn)` and `look(dx,1)` in 0..4. 0 intro: SCROLL_READ at (10,23), `look(0,1)`; 1 SCROLL_READ (10,20), dx -1; 2 dx 0; 3 dx 1; 4 dx 0; 5 outro: SCROLL_READ (10,23), `look(0,1)`; the last frame must still equal rest, so frame 5 is `rest()` with only the scroll lowered into the belly (rows 23..25 visible) and the face of frame 0; **exception to the "last frame is rest" rule is not allowed: frame 5 = `rest()` exactly, frame 0 is the one with props** |
| `pet_write` | 0 `head(dn)` `look(0,1)` NOTEBOOK (10,23); 1 NOTEBOOK (10,21) `arm_fwd(17)` PENCIL beside the notebook edge, `head(dn)` `look(0,1)`; 2 pencil 1 px lower; 3 as 1; 4 as 2; 5 `look(0,0)` NOTEBOOK (10,21) closed eyes `closed()`, `px(26,4,g)` `px(25,3,Y)` `px(27,3,Y)` `px(26,2,Y)`; 6 `rest()` |
| `pet_carry` | 0 intro: SCROLL_CARRY (17,22), no badge; 1 SCROLL_CARRY (17,21) BADGE `h`; 2 (17,20) BADGE `O`, `tailstretch(-1,0)`; 3 (17,21) BADGE `h` `lids(2)`; 4 (17,20) BADGE `O`; 5 `rest()` |
| `pet_startled` | 0 `head(dn)` `wide()`; 1 `hop(3)` `wide()` `tail(up)` BANG; 2 `hop(4)` `wide()` `tail(up)` BANG; 3 `hop(2)` `wide()` BANG; 4 `head(dn)` `wide()` BANG; 5 `rest()` |
| `pet_dance` | 0 `arm_up(0)` `tail(up)`; 1 `src(pet_happy,2)`; 2 `src(pet_happy,4)`; 3 `arm_up(0)` `tail(down)`; 4 `src(pet_happy,3)`; 5 `src(pet_happy,4)`; 6 `src(pet_happy,2)` plus `px` confetti (3,3,g) (28,2,t) (2,9,z) (29,9,Y); 7 `rest()`. The sequence must contain at most 4 frames pixel-identical to any frame of `pet_happy` |
| `pet_listen` | `head(dn)` in 1..4. 0 intro `arm_up(0)` `look(1,0)`; 1 `look(1,0)` ARCS 1 arc; 2 `look(0,0)` ARCS 2 arcs; 3 `lids(2)` ARCS 1 arc; 4 `look(1,0)` ARCS 2 arcs; 5 `rest()` (the arm lowered, arcs gone) |
| `pet_shelter` | `head(dn)`, LEAF at (9,0) and the stem in 0..4; the raised hand is `arm_up(0)`. Rain pixels `z` (1 x 2 streaks) in the free columns x0..6 rows 0..16 and x28..31 rows 0..21 only: frames 1 to 4 move every streak 4 rows down and start a new one at the top; a `z` splash pixel on the leaf's upper edge in frames 2 and 4; frame 3 `lids(2)`; 0 intro LEAF at (9,1) with `arm_up(0)`; 5 `rest()` |
| `pet_snow` | flake centre (x,y) 0 (16,0); 1 (17,2); 2 (15,4); 3 (16,7); 4 (16,10); 5 (16,13) on the nose; 6 `px(19,11,z)` `px(20,10,z)`; 7 none. Face: 0..4 `head(up2)` `look(0,-1)`; 5 `look(0,0)` `lids(1)`; 6 `closed()`; 7 `rest()`. The flake may cross the forehead (`over_eyes` is not needed: it passes between the eyes at x15..17) |

### 3.5 Shadow and the small Pêts

**`pet_shadow`** (48 x 5, 4 frames = length levels 0..3): drawn first, then Pêt on top; blit origin `(petX - 18, petY + 29)` where `(petX, petY)` is Pêt's 32 x 32 origin, so Pêt's feet row 31 is row 2 of the shadow. The fire is on Pêt's right, so the shadow grows to the left; its right end (x = 46) is fixed. With `xl = [14, 9, 4, 0][level]` the rows are: row 0 `v` on x(xl+8)..40; row 1 `V` on x(xl+4)..44 with a `v` checker on x(xl+1)..(xl+3); row 2 `V` on x xl..46; row 3 `V` on x(xl+2)..45; row 4 `v` on x(xl+6)..42. Level 0 uses `v` wherever `V` is written (fire out: only a faint contact shadow). Only `.`, `v`, `V` occur.

**Small Pêt** (hand-typed text in `SMALL`, 16 x 16 or 16 x 10, same palette; a mid-size model draws each frame next to the downscaled draft that `--draft-small` prints, a 2 x 2 priority downscale `k > e > w > others` of the matching big frame): `pet_head`: head only, outline `k` x1..14 y2..13, eyes `e` 3 x 3 at (3..5,6..8) and (10..12,6..8) with a `w` highlight at (3,6) and (10,6), cheeks `t` (2,10) (13,10), mouth `m` x6..9 y11, spots `y` (4,3) (8,2) (11,3), rim `R/r/K` on the right column; face 1 = eyes as a `k` line, face 2 = happy `^ ^` eyes and a wider mouth, face 3 = worried (highlights lower, `m` mouth bent, one `z` sweat pixel at (14,4)). `pet_small`: the whole body 16 x 16 in the rest pose, feet on row 15, tail left; frame 1 blinks, frame 3 has the tail tip 1 px up. `pet_peek`: head and two paws over an edge: the last row (row 9) is where the bar's lower edge is; frame 0 head up 4 px lower (only the top 4 rows visible), 1 up 2 px lower, 2 fully up looking left, 3 looking right, 4 blink, 5 sinks 2 px; eyes `e` with `w` highlight as in `pet_head`.

### 3.6 `PetArt` (Kotlin, file `ui/pixel/PetArt.kt`, package `dev.pocketagent.app.ui.pixel`, no Android imports)

```kotlin
enum class PetClipKind { SHOT, LOOP }
class PetClipSpec(val name: String, val kind: PetClipKind, val frames: Int, val intro: Int, val outro: Int,
                  val totalMs: Int, val airborne: Set<Int>)
class PetClip(val sprite: Sprite, val spec: PetClipSpec) {
    val loopStart: Int get() = spec.intro                 // first frame of the repeating part
    val loopEnd: Int get() = spec.frames - spec.outro     // exclusive
    fun frameAtShot(msSinceStart: Long): Int?             // null when the clip is over (>= totalMs)
    fun frameAtLoop(msSinceStart: Long): Int              // intro once, then the loop, forever
    fun frameAtOutro(msSinceOutro: Long): Int?            // null when over
}
object PetArt {
    const val W = 32; const val H = 32; const val GROUND_ROW = 31
    const val SHADOW = "pet_shadow"; const val SHADOW_W = 48; const val SHADOW_H = 5
    const val SHADOW_DX = -18; const val SHADOW_DY = 29      // blit origin relative to Pêt's origin
    const val HEAD = "pet_head"; const val SMALL = "pet_small"; const val PEEK = "pet_peek"
    val SPECS: List<PetClipSpec>                             // the table of 3.1, in that order
    val NAMES: List<String>                                  // SPECS names
    fun spec(name: String): PetClipSpec?
    fun clip(name: String, find: (String) -> Sprite?): PetClip? // null when the sprite is missing or its frame count differs from the spec
    fun shadowFrame(intensity: Float): Int                   // <0.15 -> 0, <0.45 -> 1, <0.8 -> 2, else 3 (same thresholds as fireSpriteName, Fire.kt:20)
    const val HEAD_NEUTRAL = 0; const val HEAD_BLINK = 1; const val HEAD_HAPPY = 2; const val HEAD_WORRIED = 3
}
```
Behaviour: `frameAtShot(t)` walks the sprite's own frame durations, `t < 0` gives 0; `frameAtLoop` plays `[0, intro)` once then wraps inside `[intro, frames - outro)`; `frameAtOutro` plays `[frames - outro, frames)` then null. `clip()` is the only way PET reaches the art: a dropped clip simply returns null.

## 4. Shared symbols CONSUMED

| From | Symbol | Form |
|---|---|---|
| existing code | `PxlParser.parse`, `Sprite`, `SpriteFrame` (`Sprite.kt`), `SpriteBank.find(name): Sprite?` | unchanged |
| PET | nothing consumed at build time | PET consumes this contract (3.1, 3.6) |
| existing art | `mascot.pxl` sprites `pet_idle`, `pet_talking`, `pet_waving`, `pet_happy` | read by the generator and the tests, never changed |
| tools | `tools/art/pxl.py` (`parse_file`), `tools/art/contact_sheet.py` | unchanged |

## 5. Strings

None. This area is pictures. Reported to PET/HOME: `ui_home_mascot_cd` (`strings_chat.xml` line 47, survey D) calls Pêt "the salamander" while the app says newt; a spoken label for a clip is PET's.

## 6. Stored data

None. One new asset file (`mascot_anim.pxl`, about 115 KB, about 16 KB compressed). The previous app version does not contain it, and an app that finds it ignores unknown names; no preference, no stored format, nothing a rollback could trip on. Replacing the APK is the only way it arrives.

## 7. REQUESTS to other areas

- PET: `ui/pixel/pet/*`: use `PetArt.clip(name, SpriteBank::find)`; never `SpriteBank.get` for a clip name (it throws); play clips by 3.1; keep one reaction at a time; the clip's last frame is `pet_idle` frame 0, so switch back with no transition. Own the mapping events to clips (examples: tool running to `pet_read` loop, routine answer waiting to `pet_carry` loop, memory saved to `pet_write`, error to `pet_startled`, celebration to `pet_dance`, voice mode to `pet_listen`, rain to `pet_shelter`, snow to `pet_snow`, fire kept low in the evening to `pet_yawn`, a long idle to `pet_stretch`).
- PET: `ui/components/PetNote.kt` (line 351 draws `Mascot` at 32 dp): draw `pet_head` at 16 x 16 scaled 2x u with `frame = HEAD_NEUTRAL / HEAD_HAPPY / HEAD_WORRIED` for INFO / SUCCESS / ERROR; fall back to the old `Mascot` when `SpriteBank.find(PetArt.HEAD)` is null. The top-bar peek uses `PetArt.PEEK`, bottom row on the bar's lower edge, only while the agent works and `appearance.showMascot`.
- PET (debug pin through the link owner): `pet=<clip name>[:<frame>]` (value form `pet_poke` or `pet_poke:3`; `pet=auto` or no value clears): the scene plays that clip, repeating, or shows that frame frozen; only with `BuildConfig.TEST_HOOKS`. The proof of 9 needs it.
- SCENE (`SceneRenderer.kt`): `REQUIRED_SPRITES` (line 326) must stay the seven moods; reach the new sprites only through an optional lookup (a missing sprite must never throw in the scene constructor, lines 23 to 33). Blit `pet_shadow` frame `PetArt.shadowFrame(intensity)` at `(petX + SHADOW_DX, petY + SHADOW_DY)` before Pêt (layer 10, line 153), intensity = the fire's continuous level (FIRE).
- ART-WORLD: `tools/art/craft_art.py` `--check` (and the plain run) also calls `python tools/art/pet_clips.py --check` (resp. the plain command) and combines the exit codes, so the existing CI step (`.github/workflows/build.yml:57`) covers the new file without a workflow change; `tools/art/README.md`: add `mascot_anim.pxl` as generated by `pet_clips.py`, and the three commands of 3.2.
- Lead: `AGENTS.md` "Repo map" gets the new tool and file; assets budget line in `tools/size-budget.txt` if it counts assets (115 KB raw).

## 8. Tests (`PetArtTest`, JUnit 4, JVM; reads `app/src/main/assets/pixel/*.pxl` with `Assume.assumeTrue(dir.isDirectory)`; all pure functions of the parsed sprites)

| Test | Proves |
|---|---|
| `everySpriteInTheFileHasASpecOrIsAStill` | every sprite in `mascot_anim.pxl` is a name in `PetArt.SPECS` or one of shadow, head, small, peek; no sprite also exists in another file |
| `shippedClipsMatchTheirSpec` | for each spec whose sprite exists: 32 x 32, frame count, `intro + outro < frames` for LOOP, `totalMs` equal, each duration 40..600 |
| `existingMoodsAreUntouched` | `mascot.pxl` still has the seven sprites with the frame counts and durations of survey C, and `pet_idle` frame 0 has 545 opaque cells |
| `everyClipEndsOnTheRestPose` | last frame equals `pet_idle` frame 0 cell for cell (SHOT and LOOP outro); first frame differs from rest by 1..140 cells |
| `loopsWrapSmoothly` | LOOP: the last loop frame and the first loop frame differ by at most 90 cells |
| `feetStayOnTheGroundLine` | in every non-airborne frame an opaque cell exists on row 31 at some x in 4..27; airborne frames have no opaque cell on row 31; no frame has an opaque cell in row 0 outside props columns |
| `noStrayPixels` | per frame, 8-connected islands: the largest holds at least 85 % of opaque cells; islands other than the largest number at most 8 in `pet_dance`, `pet_shelter`, `pet_snow`, `pet_firefly`, at most 4 elsewhere; no island of one cell lies in an eye block |
| `outlineRule` | the share of the largest island's edge cells coloured `k K e` is at least 0.78 (the lowest existing value is 0.80) |
| `eyesDoNotDrift` | in every frame the centroid x of dark `e` cells with x < 16 is within 1.0 of the rest value and the distance between the two eye centroids equals the rest distance within 0.5 (closed-eye frames exempt from the distance) |
| `propsAvoidTheStones` | no opaque cell at x >= 28 on rows >= 22 that is not also opaque in rest |
| `paletteIsTheOldOnePlusEight` | the first 24 palette entries equal `mascot.pxl`'s; the new letters have exactly the colours of 3.1; unused letters are allowed |
| `shadowGrowsLeftOnly` | 48 x 5, 4 frames; only `v` and `V`; the rightmost opaque column is the same in all frames; the leftmost opaque column is 14, 9, 4, 0; opaque cell counts strictly increase |
| `shadowFrameThresholds` | `shadowFrame(0.14f)=0`, `(0.15f)=1`, `(0.44f)=1`, `(0.45f)=2`, `(0.79f)=2`, `(0.8f)=3`, `(5f)=3`, `(-1f)=0`, `(NaN)=0` |
| `smallSpritesHaveTheirSizes` | `pet_head` 16 x 16 x 4, `pet_small` 16 x 16 x 4, `pet_peek` 16 x 10 x 6 |
| `clipPlaybackIsExact` | on a synthetic 3-frame SHOT: `frameAtShot` at 0, a boundary minus 1, the boundary, total minus 1 and total (null); LOOP with intro 1 and outro 1 wraps inside `[1, n-1)`; `frameAtOutro` ends with null; `clip()` returns null for a missing sprite and for a frame-count mismatch |

Python side: `pet_clips.py --selftest` (every rig op on a hand-computed 8 x 8 grid) and `--check` in CI via `craft_art.py` (section 7, ART-WORLD). The unit tests need no Python.

## 9. Proof on the phone

- **The review (before anything ships; looks at pictures, not at code).** `python tools/art/pet_clips.py` then:
  `python tools/art/contact_sheet.py --file app/src/main/assets/pixel/mascot_anim.pxl --frames all --scale 8 --max-width 2400 --out dist/w1a/pet/sheet-all.png`
  and per clip `... --sprite pet_poke --frames all --scale 12 --out dist/w1a/pet/pet_poke.png`; the 1x check `--scale 1` and `--scale 2` (what the phone shows is about 6 device pixels per art pixel, which is `--scale 6` on a 176-px wide scene). The old and new beside each other: `--sprite pet_idle --sprite <clip> --frames first --scale 10`. A reviewer who has not written the clip looks at each PNG and fills `dist/w1a/pet/review.txt`, one line per sprite: `KEEP` or `DROP <reason>`. Checklist, every answer yes or the clip is dropped: (1) the silhouette tells what Pêt is doing at 1x and 2x; (2) the eyes never drift between frames (the two eyes keep the same distance and level, except for a declared look or lid); (3) no stray pixel (no single pixel beside the body except declared sparks, drops, flakes); (4) feet on the ground line in every non-airborne frame, a hop is 2 to 4 px; (5) first and last frames are the rest pose, loops close without a jump; (6) outline `k`/`K` closed and light from the right on body and prop; (7) the prop reads as what it is at 1x; (8) the frames do not flicker (no frame differs from its neighbour by more than 140 cells without a reason); (9) the clip is not a copy of `pet_happy` or `pet_waving`. **An animation that fails is dropped and reported, never shipped**: it is removed from `CLIPS`, the file regenerated, the name listed under "dropped" in the final report; `PetArt.clip` then returns null and Pêt keeps today's pose. The generator never ships a clip on its own say-so: `--report` numbers must also pass 8.
- **Debug-link pins** (TEST_HOOKS only, in memory): `pet=<name>[:<frame>]` (section 7, PET), `clock=2027-03-21T21:00` (any pinned moment works; art does not depend on it), and the fire level of FIRE's pin for the shadow length.
- **Screenshots** (owner-debug build, route Home, theme NEWROZ dark, animations FULL, at 1080 px width): each of the 15 clips frozen at its peak frame (blink 1, tail 2, warm 3, yawn 2, stretch 2, firefly 2, poke 2, read 2, write 2, carry 2, startled 2, dance 1, listen 2, shelter 2, snow 5) via `pet=<name>:<f>`: 15 images; the shadow at fire levels 0.1, 0.3, 0.6, 0.9 (4 images); one clip in NERGIZ light to see the colours on the light theme (`pet_read`); a Pêt note with `pet_head` in INFO, SUCCESS, ERROR; the chat top bar with `pet_peek`. Test tags from PET: `stage.hearth` (exists), `petnote` (exists), a new `chat.peek` on the peek (PET).
- Not provable on the phone: smoothness of the hand-over between clips (a person watches a 10 s screen recording once).

## 10. Cost

- Per frame: the scene already blits Pêt (1024 cells, `PixelStage.blit`, no allocation); this part adds the shadow blit (240 cells) and nothing else: no new draw path, no allocation, one table lookup for the frame. About 1.3 k cell operations, well under 0.02 ms on the optimised build, inside the 1 ms scene budget. The clip frame index is two integer divisions on a 30 fps tick that already runs.
- Per minute: PET starts one clip every 4 to 9 s, so at most 15 starts a minute; each start is an array read.
- Memory: parsing adds 98 + 14 frames; `IntArray(1024)` per 32 x 32 frame = 4 KB, so about 0.45 MB resident, plus 4 KB per frame that is ever played (`Sprite.rasters`, `SpriteRaster.plain`), at most 0.45 MB more. Parse time of 115 KB of text is a few milliseconds on the `pixel-preload` thread (`SpriteBank.kt:49` to 62), not on the main thread.
- APK: about 16 KB compressed (art share 1.5 MB of the plan, section 5).

## 11. Risks, and what in the plan or the design language does not fit the real code

1. **The 32 x 32 frame is almost full.** Pêt fills x1..27, y5..31, leaving 5 rows above the head and 4 columns on the right, and the fire's stones (drawn after Pêt) hide columns 27..31 below row 22 (0, `SceneRenderer.kt:153` to 154, `fire_high` rows 23..33). Design language 7 says "32 x 32, 4 to 8 frames" for all clips; props that reach the fire (`pet_poke`, a longer twig, a warm hand reaching out) have no room, which is why `pet_poke` is the first cut and `pet_warm` holds its hand at chest height beside the body. A clip family with real reach would need 40 x 32 frames and an origin shift of 8 px in the scene (SCENE and PET), not planned here.
2. **Hand-typed patches are the quality risk.** The body, head, eyes, tail and arms of Pêt come from the existing art by rect copies; only the 13 patches of 3.3, the 16 x 16 frames and the shadow are drawn new. If a patch reads badly the clip is dropped (section 9). The lowered arm was tried in a prototype and failed, hence `ARM_FWD` as a typed patch.
3. **Deviation:** the plan names `mascot.pxl`; new frames go to `mascot_anim.pxl` (reason in 2). If the lead wants one file, the generator can append to `mascot.pxl`, but then `mascot.pxl` becomes a generated file and loses its hand-edited status.
4. **`pet_dance` reuses `pet_happy` frames** (f1 to f4). It is a new sequence with a lean-free arm-up beat and confetti, but if the review finds it reads as `pet_happy` it is dropped.
5. **`look into the fire`** (design language 7 lists it as a PetBrain option) has no clip of its own; `pet_warm` frame 0 and the `look(1,0)` face exist. PET may use `pet_thinking` frames or ask for a clip.
6. **The design language says every animation "4 to 8 frames"**; `pet_small` loops and `pet_head`/`pet_shadow` are stills with 4 frames, which fits. LOOP clips with an intro and an outro are 6 frames, within the range.
7. `Sprite.frameAt` always loops (`Sprite.kt:30`): the one-shot and intro/outro playing is code in `PetClip`, not in the sprite. `PixelImage` with `frame = null` would loop a SHOT clip: PET must pass explicit frames.
8. `SpriteBank.load` loads every `.pxl` in the folder and a duplicate name in a second file keeps the first and is only logged (survey B, `SpriteLoader`); `PxlParserTest` fails on a duplicate across files, so name clashes are caught by CI.
9. The generator's output must be identical on Windows and Linux (CI `--check`): it uses only integers and fixed tables, no maths library.
10. The reviewer must be a different agent from the one that wrote the clip: a single agent that draws and judges its own art will keep everything.
