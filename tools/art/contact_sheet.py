#!/usr/bin/env python3
"""
One picture of every sprite: a contact sheet of the .pxl files, on a checkerboard so transparency shows,
with the sprite's name under it. Needs Pillow (pip install pillow). Run from the repository root:

  python tools/art/contact_sheet.py
  python tools/art/contact_sheet.py --file app/src/main/assets/pixel/fire.pxl --frames all --scale 6
  python tools/art/contact_sheet.py --sprite pet_idle --sprite pet_happy --scale 8

Stdout: one line per sprite, "name<TAB>WxH<TAB>Nframes<TAB>file", then "contact sheet: <path> (<W>x<H> px, <N> sprites)".
A problem in a .pxl file prints "file:line: message" on stderr and the exit code is 1.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import os

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import pxl  # noqa: E402  (the strict reader next to this file)

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_FILE = os.path.join(ROOT, "app", "src", "main", "assets", "pixel")
DEFAULT_OUT = os.path.join(ROOT, "dist", "art", "contact-sheet.png")

PAD = 6        # space around every cell
LABEL_GAP = 3  # between the sprite and its name
CHECK = 8      # size of one checkerboard square, in sheet pixels


def parse_color(text):
    t = text.strip()
    if not t.startswith("#") or len(t) not in (7, 9):
        raise argparse.ArgumentTypeError("colour must look like #RRGGBB or #AARRGGBB, got '%s'" % text)
    try:
        v = int(t[1:], 16)
    except ValueError:
        raise argparse.ArgumentTypeError("colour is not hexadecimal: '%s'" % text)
    return (0xFF000000 | v) if len(t) == 7 else v


def parse_ramp(text):
    parts = [p for p in text.split(",") if p.strip()]
    if not 1 <= len(parts) <= 4:
        raise argparse.ArgumentTypeError("--ramp wants one to four colours separated by commas")
    return [parse_color(p) for p in parts]


def collect_files(target):
    if os.path.isdir(target):
        return sorted(os.path.join(target, n) for n in os.listdir(target) if n.endswith(".pxl"))
    return [target]


def load_sprites(files):
    """[(sprite, file name)] in file order, then the order inside the file."""
    out = []
    for path in files:
        for s in pxl.parse_file(path):
            out.append((s, os.path.basename(path)))
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Contact sheet of every sprite in the .pxl files.")
    parser.add_argument("--file", default=DEFAULT_FILE, help="a .pxl file or a folder of them (default: the app's assets)")
    parser.add_argument("--max-width", type=int, default=2400, help="width at which the shelves wrap (default 2400)")
    parser.add_argument("--scale", type=int, default=4, help="sheet pixels per sprite pixel (default 4)")
    parser.add_argument("--frames", choices=["first", "all"], default="first", help="draw the first frame or all of them")
    parser.add_argument("--sprite", action="append", default=[], metavar="NAME", help="only this sprite (repeatable)")
    parser.add_argument("--bg", type=parse_color, default=parse_color("#2B2B33"), help="base colour of the checkerboard")
    parser.add_argument("--ramp", type=parse_ramp, default=None, help="four colours for the palette chars 1 to 4")
    parser.add_argument("--out", default=DEFAULT_OUT, help="the PNG to write (default dist/art/contact-sheet.png)")
    args = parser.parse_args(argv)
    if args.scale < 1 or args.max_width < 64:
        parser.error("--scale must be at least 1 and --max-width at least 64")

    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        print("contact_sheet.py needs Pillow: pip install pillow", file=sys.stderr)
        return 1

    try:
        entries = load_sprites(collect_files(args.file))
    except pxl.PxlError as e:
        print(str(e), file=sys.stderr)
        return 1
    except OSError as e:
        print("cannot read %s: %s" % (args.file, e), file=sys.stderr)
        return 1
    if args.sprite:
        wanted = set(args.sprite)
        missing = wanted - {s.name for s, _ in entries}
        if missing:
            print("no such sprite: %s" % ", ".join(sorted(missing)), file=sys.stderr)
            return 1
        entries = [(s, f) for s, f in entries if s.name in wanted]
    if not entries:
        print("no sprites found in %s" % args.file, file=sys.stderr)
        return 1

    font = ImageFont.load_default()
    probe = ImageDraw.Draw(Image.new("RGB", (4, 4)))

    def text_size(text):
        box = probe.textbbox((0, 0), text, font=font)
        return box[2] - box[0], box[3] - box[1]

    base = ((args.bg >> 16) & 255, (args.bg >> 8) & 255, args.bg & 255)
    light = tuple(min(255, c + 24) for c in base)

    cells = []  # (sprite, frames to draw, cell width, cell height, label)
    for s, _ in entries:
        n = len(s.frames) if args.frames == "all" else 1
        art_w = (s.width * args.scale + PAD) * n - PAD
        art_h = s.height * args.scale
        label = s.name
        lw, lh = text_size(label)
        cells.append((s, n, max(art_w, lw) + 2 * PAD, art_h + LABEL_GAP + lh + 2 * PAD, label))

    # shelf packing: left to right, a new shelf when the next cell would pass the maximum width
    placed = []
    x = y = shelf_h = sheet_w = 0
    for cell in cells:
        cw, ch = cell[2], cell[3]
        if x > 0 and x + cw > args.max_width:
            y += shelf_h
            x = shelf_h = 0
        placed.append((cell, x, y))
        x += cw
        shelf_h = max(shelf_h, ch)
        sheet_w = max(sheet_w, x)
    sheet_h = y + shelf_h

    sheet = Image.new("RGB", (sheet_w, sheet_h), base)
    px = sheet.load()
    for yy in range(sheet_h):
        for xx in range(sheet_w):
            if ((xx // CHECK) + (yy // CHECK)) % 2:
                px[xx, yy] = light
    draw = ImageDraw.Draw(sheet)
    for (s, n, cw, ch, label), cx, cy in placed:
        for f in range(n):
            img = Image.new("RGBA", (s.width, s.height))
            img.putdata(s.rgba(f, args.ramp))
            big = img.resize((s.width * args.scale, s.height * args.scale), Image.NEAREST)
            sheet.paste(big, (cx + PAD + f * (s.width * args.scale + PAD), cy + PAD), big)
        draw.text((cx + PAD, cy + PAD + s.height * args.scale + LABEL_GAP), label, fill=(235, 235, 240), font=font)

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    for s, f in entries:
        print("%s\t%dx%d\t%d\t%s" % (s.name, s.width, s.height, len(s.frames), f))
    print("contact sheet: %s (%dx%d px, %d sprites)" % (out, sheet_w, sheet_h, len(entries)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
