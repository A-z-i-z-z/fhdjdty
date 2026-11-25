# tools/art: the pixel art tools

All of Agirî's pixel art lives in six text files in `app/src/main/assets/pixel/`. This folder holds the
scripts that make or look at them. Run everything from the repository root. Python 3.12 is installed on the
owner's PC as `python` (on Linux CI it is `python3`); only `contact_sheet.py` needs a package (Pillow).

## Which files are generated and which are the source

| File | What it holds | Where it comes from |
|---|---|---|
| `fire.pxl` | the fire sprites (cold, out, low, mid, high) | **generated** by `craft_art.py` |
| `scene.pxl` | the scene layers of Home: far ridge, near ridge, ground, foreground, moon | **generated** by `craft_art.py` |
| `appicon.pxl` | the six switchable launcher icons and the notification flame | **generated** by `craft_art.py` |
| `avatars.pxl` | the 16 bot avatars | the file itself, **edited by hand** (no generator exists) |
| `mascot.pxl` | Pêt, the Kurdistan newt, in every mood | the file itself, **edited by hand** (no generator exists) |
| `icons.pxl` | the 12 by 12 interface icons | the file itself, **edited by hand** (no generator exists) |

Never edit a generated file by hand: the next run of the script would overwrite the change, and CI
(`craft_art.py --check`) fails when a generated file differs from what the script produces. Change the
script, run it, commit both. The copy of `craft_art.py` in this folder is the one that counts; the old copy
in the git-ignored `dist/` folder is not used any more.

## Commands

```sh
# write the three generated files into app/src/main/assets/pixel/
python tools/art/craft_art.py

# only one of them: fire, scene or appicon
python tools/art/craft_art.py --only fire

# write into another folder (created) and leave the app's files alone
python tools/art/craft_art.py --out dist/art-test

# compare with the committed files, write nothing: prints "ok <file>" or "DIFF <file>" for each,
# exit 0 when all three are equal, 1 otherwise (this is what CI runs)
python tools/art/craft_art.py --check
```

A normal run prints `Wrote <path> (<n> sprites)` for every file and then `Art pass v2 complete!`.

Platform note: the generator uses `math.sin` and fractional powers, whose last digit comes from the
platform's maths library. `--check` was run on Windows (Python 3.12) and on Linux (Ubuntu 24.04 under WSL,
glibc, Python 3.12) and gave `ok` for all three files on both, so CI on Linux compares equal. If a future
platform ever prints `DIFF` with no edit to the art, round every `sin`/`**` result (for example
`round(x, 9)`) before it is compared with a threshold, then re-run on both platforms.

### The contact sheet (needs Pillow: `pip install pillow`)

```sh
# one picture of all 112 sprites, first frame of each, written to dist/art/contact-sheet.png
python tools/art/contact_sheet.py

# fire frames side by side, big
python tools/art/contact_sheet.py --file app/src/main/assets/pixel/fire.pxl --frames all --scale 6

# two sprites, with a tint ramp for the palette chars 1 to 4
python tools/art/contact_sheet.py --sprite pet_idle --sprite pet_happy --scale 8 --ramp "#3B2A5A,#5E448C,#8A6AC8,#BFA4F2"
```

Options: `--file` (a `.pxl` file or a folder), `--max-width` (default 2400), `--scale` (default 4),
`--frames first|all`, `--sprite NAME` (repeatable), `--bg #RRGGBB`, `--ramp "#c1,#c2,#c3,#c4"`, `--out`.
It prints one line per sprite (`name`, size, number of frames, file), then the path and size of the sheet.
A problem in a `.pxl` file is printed as `file:line: message` on stderr and the exit code is 1.
`pxl.py` is the strict reader it uses, the Python twin of the app's `PxlParser`.

### The older PowerShell tools (unchanged, still here)

- `tools/pixel-preview.ps1`: one PNG per sprite with every frame side by side, optional contact sheet per file.
  `powershell -NoProfile -ExecutionPolicy Bypass -File tools\pixel-preview.ps1 -File app\src\main\assets\pixel\mascot.pxl -Sprite pet_idle -Scale 10`
- `tools/pxl-to-vector.ps1`: turns a sprite into the Android vector XML of a launcher icon.

## How to add a sprite

1. In a hand-edited file (`avatars.pxl`, `mascot.pxl`, `icons.pxl`): add a `sprite <name> <w> <h>` block with
   one or more `frame [ms]` blocks of exactly `h` rows of `w` characters, below the `palette` block it uses.
   In a generated file: add it to the matching `build_*` function of `craft_art.py` and run the script.
2. Sprite names are unique across all six files (letters, digits, underscore). The app finds a sprite by name:
   `SpriteBank.get("name")`, or `PixelImage("name")` in a composable.
3. Look at it: `python tools/art/contact_sheet.py --sprite <name> --scale 10`. A syntax error shows up here as
   `file:line: message`, the same message the app logs.
4. Run the unit tests (`PxlParserTest` checks that every shipped file parses and the names are unique).

## The format

The `.pxl` text format (palette blocks, `.` for transparent, `none`, frames with durations, the tint ramp
chars `1` to `4`) is described in `docs/phase1-contract.md`, section "Shared pixel format".
