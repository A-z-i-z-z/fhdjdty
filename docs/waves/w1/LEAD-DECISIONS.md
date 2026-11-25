# Wave 1a: the lead's decisions on the drafts

Written 2026-11-25 by the lead after the eight drafts (`docs/waves/w1/<AREA>.md`). Where an area file
and this file disagree, this file wins; where the contract (`docs/waves/w1a-contract.md`) and this file
disagree, the contract wins (it is written later). Everything here that is a matter of taste or belongs
to the owner is "assumed, owner may change".

## What this half is

Wave 1a of `docs/phase4-plan.md` section 7.1: the scene and the real sky (1.2), the weather built but
dark (1.3), the fire (1.4), Pêt (1.5), Home 2.0 (1.1) and the Appearance and sound parts that belong
to them (1.9). Wave 1b (the tab bar and the Library, the splash, "What's new", the Welcome, the ten
languages) is not built now.

## Decisions that hold for every area

- **The sky follows the real day by default** (plan decision 5). "Always night" is exactly today's
  picture: today's 46 stars, today's moon, no dithered horizon, so the parity test of Wave 0 keeps
  proving it. The dithered horizon and the real stars belong to "follow the day" and "always day".
- **The fire:** the hand-keyed flame stays; the living edge and the sparks are on by default;
  "Classic fire" gives today's fire back. One setting, in `Appearance` (APPEAR provides it, FIRE
  reads it; FIRE keeps no preferences file of its own).
- **The weather ships dark.** No request can be made while `BuildConfig.WEATHER_CONTACT` is empty,
  and it is empty. The switch is not shown. Everything is proven with a pinned weather.
- **No new permission, no new library.**
- **Downloads.** Two public data files are needed to derive tables, and only for that: the HYG star
  catalogue (the plan names it) and Natural Earth's populated places (public domain) for the city
  list. They are downloaded into `dist/` (git-ignored), each into its own empty folder, read as data
  only, never committed; the derived tables and the scripts are committed, and `NOTICE` gets the
  credit each licence asks for, with the licence text read from the downloaded file itself. Nothing
  else is downloaded: no sounds, no fonts, no art.
- **Sounds** are made by a committed script (`tools/art/make_sounds.py`, filtered noise and tones);
  `NOTICE` says so. A cue without a file stays silent. WAV is fine up to 300 KB in total.
- **Cultural and regional days:** the lists are data, none is on by default, and only days whose date
  is a fixed calendar date or follows from the sky (an equinox, a computed new or full moon) are
  shipped now; lists that need a table of uncertain dates wait for Wave 12. An installation that
  already exists (it has a Linux image stamp or chat files) keeps the day it has today switched on,
  with today's behaviour.
- **Art:** new art replaces old only where a reviewer who looked at the contact sheet says it is
  better. A sprite that fails is left out, the code falls back to what exists, and the report lists it.
  New mascot frames live in a new file `assets/pixel/mascot_anim.pxl`; `mascot.pxl` is not edited.
  World art comes from the generator (`tools/art/craft_art.py`), and its `--check` keeps passing.
- **Stored data:** optional new fields only. The backup does not carry the new preferences in this
  half (listed as a follow-up).
- **The proof runs on the owner-debug build over the owner's own app**, so nothing may write user
  data in bulk and every pin (clock, place, sky, days, weather, Pêt, theme) is in memory, through the
  debug link.

## Who owns what the plan's table left open

| File | Owner in 1a |
|---|---|
| `ui/pixel/HearthScene.kt`, `ui/pixel/SkyHost.kt` and the other Compose glue of the scene | SCENE |
| `DebugLinks.kt`, `DebugOverrides.kt`: every new extra any area needs (`sky`, `days`, `weather`, `place`, `pet`, `theme`, `dark`) | WEATHER (the plan gives it "the debug pin"); the other areas put their extras under REQUESTS and the contract lists them all |
| `data/Places.kt` (the zones, the cities, the search) | WEATHER; the place rows and the city sheet on the Appearance screen are APPEAR's |
| `ui/AppRoot.kt` (two small edits: status-bar icon polarity from the sky, the theme pin) | nobody in stage 1; INTEGRATE applies the exact lines the contract spells out |
| `app/build.gradle.kts` (`WEATHER_CONTACT`, empty) and `NOTICE` | INTEGRATE, from the exact lines in the contract |
| `ui/components/PetNote.kt` | PET |

## Per area

- **SCENE:** the 220-degree panorama is accepted (a taste point for the owner's look). The plan's
  eclipse table and the planets stay extras.
- **WEATHER:** the city list comes from Natural Earth (about a thousand places by population); no
  hand-made include list. The service's terms are re-read when the feature is switched on; the
  contract marks what was written from memory. Procedural clouds are judged on stills.
- **FIRE:** the drafted settle, tilt direction and the one-second hold are accepted.
- **PET:** the deep-night rule and the idle rhythm are accepted as drafted. Pêt's tap area is its own
  sprite box; where it overlaps the fire, the fire wins. No "hide Pêt" flag in this half.
- **ART-PET:** no wider clip family; `pet_poke` is the first cut. The small head for notes replaces
  today's only if it passes review.
- **ART-WORLD:** empty-state pictures are 32 by 32. Moon dates in the proof come from SCENE's table.
- **HOME:** a strip a little taller than 96 dp is fine. The hero takes the design language's share.
  Swipes are pin and mark read or unread (archive needs a field: Wave 2). No overflow menu yet: the
  labelled button of the status row is the labelled way. The question before putting the fire out
  while an answer is running stays.
- **APPEAR:** the sky's own days have no switch (on); each regional list has one (off). The row order
  is the draft's.

## The order of the build

1. **Stage 1, in parallel:** SCENE, WEATHER, FIRE, PET, ART-PET, ART-WORLD, HOME, APPEAR, each followed
   by its own Opus review and one round of fixes.
2. **Stage 2:** DATA (one agent that may download: derives `assets/sky/stars.bin` and the city table
   with the committed scripts, makes the sounds, renders the contact sheets and the side-by-side
   pictures of the fire).
3. **Stage 3:** LOOK (a reviewer that looks at the contact sheets and pictures; what fails is cut).
4. **Stage 4:** TRANSLATE (Turkish and French for every new key; the stretched language).
5. **Stage 5:** INTEGRATE (the edits it owns, compile the three variants, unit tests, every check).
6. **Final review** (Opus, by lens), fixes, then the phone.
