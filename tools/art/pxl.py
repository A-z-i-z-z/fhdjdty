"""
Strict reader for the .pxl sprite text format, the Python twin of PxlParser.kt (same rules, same messages).

Every problem raises PxlError whose message starts with "file:line: ", exactly like the Kotlin parser.
The format is described in docs/phase1-contract.md, section "Shared pixel format".

Used by contact_sheet.py. Not used by the app or by craft_art.py.
"""
import sys

sys.dont_write_bytecode = True

import re

DEFAULT_FRAME_MS = 120

_NAME = re.compile(r"[A-Za-z0-9_]+\Z")
_INT = re.compile(r"[+-]?[0-9]+\Z")
_HEX = re.compile(r"[0-9A-Fa-f]+\Z")


class PxlError(ValueError):
    """A problem in a .pxl file. The message always starts with "file:line: "."""


class Sprite:
    """name, width, height; frames = [(indices, duration_ms)] with one palette index per pixel (-1 = transparent,
    row-major); palette = [(char, argb)] with argb as an unsigned 32-bit integer (alpha first)."""

    def __init__(self, name, width, height, frames, palette):
        self.name = name
        self.width = width
        self.height = height
        self.frames = frames
        self.palette = palette

    def ramp_slots(self):
        """Palette index of the tint-ramp chars '1'..'4' (-1 when the sprite does not use them)."""
        slots = []
        for k in range(4):
            ch = chr(ord("1") + k)
            slots.append(next((i for i, (c, _) in enumerate(self.palette) if c == ch), -1))
        return slots

    def rgba(self, frame, ramp=None):
        """The frame as a list of (r, g, b, a) tuples, row-major. [ramp] is a list of up to four 0xAARRGGBB
        values that replace the palette chars '1'..'4' (a shorter list repeats its last colour)."""
        pal = [argb for _, argb in self.palette]
        if ramp:
            for k, slot in enumerate(self.ramp_slots()):
                if slot >= 0:
                    pal[slot] = ramp[min(k, len(ramp) - 1)]
        out = []
        for idx in self.frames[frame][0]:
            if idx < 0:
                out.append((0, 0, 0, 0))
            else:
                v = pal[idx]
                out.append(((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255))
        return out


def _parse_color(text, fail):
    if text == "none":
        return None
    if (len(text) != 7 and len(text) != 9) or text[0] != "#":
        fail("bad colour '%s' (use #RRGGBB, #AARRGGBB or none)" % text)
    digits = text[1:]
    if not _HEX.match(digits):
        fail("bad colour '%s' (not hexadecimal)" % text)
    v = int(digits, 16)
    return (0xFF000000 | v) if len(text) == 7 else v


def parse(file_name, text):
    """Parses the text of one file and returns its sprites in order. Raises PxlError."""
    lines = text.split("\n")
    out = []
    seen = set()
    pal = {}  # char -> argb or None ("none"); insertion order kept
    cur = None
    i = 0

    def fail(line, msg):
        raise PxlError("%s:%d: %s" % (file_name, line, msg))

    def finish(p):
        if not p["frames"]:
            fail(p["line"], "sprite '%s' has no frames" % p["name"])
        out.append(Sprite(p["name"], p["w"], p["h"], list(p["frames"]), p["entries"]))

    while i < len(lines):
        raw = lines[i]
        if raw.endswith("\r"):
            raw = raw[:-1]
        i += 1
        line_no = i
        if raw.strip() == "" or raw.startswith("#"):
            continue
        tok = raw.strip().split()
        if tok[0] == "palette" and len(tok) == 1:
            if cur is not None:
                finish(cur)
            cur = None
            pal = {}
        elif tok[0] == "sprite" and len(tok) == 4:
            if cur is not None:
                finish(cur)
            name = tok[1]
            if not _NAME.match(name):
                fail(line_no, "bad sprite name '%s' (letters, digits, underscore)" % name)
            if name in seen:
                fail(line_no, "duplicate sprite name '%s' in this file" % name)
            seen.add(name)
            if not _INT.match(tok[2]):
                fail(line_no, "bad sprite width '%s'" % tok[2])
            if not _INT.match(tok[3]):
                fail(line_no, "bad sprite height '%s'" % tok[3])
            w = int(tok[2])
            h = int(tok[3])
            if w <= 0 or h <= 0 or w > 4096 or h > 4096:
                fail(line_no, "bad sprite size %dx%d" % (w, h))
            entries = []
            index = {}
            for ch, argb in pal.items():
                if argb is None:
                    index[ch] = -1
                else:
                    index[ch] = len(entries)
                    entries.append((ch, argb))
            cur = {"name": name, "w": w, "h": h, "line": line_no, "entries": entries, "index": index, "frames": []}
        elif tok[0] == "frame" and len(tok) <= 2:
            if cur is None:
                fail(line_no, "'frame' before any 'sprite'")
            ms = DEFAULT_FRAME_MS
            if len(tok) == 2:
                if not _INT.match(tok[1]):
                    fail(line_no, "bad frame duration '%s'" % tok[1])
                ms = int(tok[1])
                if ms <= 0:
                    fail(line_no, "frame duration must be positive, got %d" % ms)
            w, h = cur["w"], cur["h"]
            idx = [0] * (w * h)
            for r in range(h):
                if i >= len(lines):
                    fail(len(lines), "sprite '%s' frame %d ends after %d of %d rows"
                         % (cur["name"], len(cur["frames"]), r, h))
                row = lines[i]
                if row.endswith("\r"):
                    row = row[:-1]
                i += 1
                if len(row) != w:
                    fail(i, "row has %d chars, expected %d (sprite '%s', frame %d, row %d)"
                         % (len(row), w, cur["name"], len(cur["frames"]), r))
                for x in range(w):
                    ch = row[x]
                    if ch == ".":
                        idx[r * w + x] = -1
                    elif ch in cur["index"]:
                        idx[r * w + x] = cur["index"][ch]
                    else:
                        fail(i, "char '%s' at column %d is not in the palette (sprite '%s')" % (ch, x, cur["name"]))
            cur["frames"].append((idx, ms))
        elif len(tok) == 2 and len(tok[0]) == 1:
            ch = tok[0]
            if ch == "#":
                fail(line_no, "'#' cannot be a palette char")
            if ch == "." and tok[1] != "none":
                fail(line_no, "'.' is always transparent")
            pal[ch] = _parse_color(tok[1], lambda msg, n=line_no: fail(n, msg))
        else:
            fail(line_no, "cannot parse line '%s'" % raw.strip())
    if cur is not None:
        finish(cur)
    return out


def parse_file(path):
    """Reads and parses one file (UTF-8). The name in messages is the file's base name, as in the app."""
    import os
    with open(path, "r", encoding="utf-8", newline="") as f:
        return parse(os.path.basename(path), f.read())
