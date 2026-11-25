#!/usr/bin/env python3
"""
Hand-directed pixel art builder for Agiri (PocketAgent) — Art Pass v2 (polished after visual review).
Outputs clean .pxl sprite files using hue-shifted palettes, organic silhouettes,
colored outlines, delicate 1px rim lights, and crisp mountain facets.
"""
import sys

sys.dont_write_bytecode = True  # no __pycache__ folders in the working tree

import argparse
import contextlib
import io
import math
import os
import shutil
import tempfile

# This file lives in tools/art/: the repository root is two levels up.
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
COMMITTED_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "pixel")
# PIXEL_DIR is where the build_* functions write. They read it when they run, so main() may reassign it
# (--out, --check) before it calls them.
PIXEL_DIR = COMMITTED_DIR

BAYER4 = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]


def bayer(x, y):
    return (BAYER4[y & 3][x & 3] + 0.5) / 16.0


def make_grid(w, h, fill="."):
    return [[fill for _ in range(w)] for _ in range(h)]


def put(g, x, y, ch):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = ch


def get(g, x, y):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        return g[y][x]
    return "."


def ellipse(g, cx, cy, rx, ry, ch):
    h, w = len(g), len(g[0])
    for y in range(max(0, int(cy - ry - 1)), min(h, int(cy + ry + 2))):
        for x in range(max(0, int(cx - rx - 1)), min(w, int(cx + rx + 2))):
            dx = (x + 0.5 - cx) / rx
            dy = (y + 0.5 - cy) / ry
            if dx * dx + dy * dy <= 1.0:
                g[y][x] = ch


def outline(g, outline_ch, skip=(".",)):
    h, w = len(g), len(g[0])
    src = [row[:] for row in g]
    for y in range(h):
        for x in range(w):
            if src[y][x] in skip:
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[ny][nx] not in skip:
                        g[y][x] = outline_ch
                        break


def write_pxl(path, header_lines, palette, sprites):
    lines = list(header_lines)
    lines.append("palette")
    for ch, col in palette:
        lines.append(f"{ch} {col}")
    for name, w, h, frames in sprites:
        lines.append(f"sprite {name} {w} {h}")
        for dur, g in frames:
            assert len(g) == h, f"{name}: height {len(g)} != {h}"
            lines.append(f"frame {dur}")
            for r in g:
                row = "".join(r)
                assert len(row) == w, f"{name}: width {len(row)} != {w}"
                lines.append(row)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Wrote {path} ({len(sprites)} sprites)")


# =============================================================================
# 1. FIRE.PXL — Rich crimson->orange->gold->tight-cream Newroz flames + stone ring
# =============================================================================

FIRE_PALETTE = [
    (".", "none"),
    ("o", "#170E22"),  # deep plum outline
    ("s", "#342C48"),  # stone shadow
    ("S", "#52496B"),  # stone mid
    ("h", "#7B7196"),  # stone cool highlight
    ("w", "#B0603E"),  # stone fire-bounce mid
    ("W", "#EA9252"),  # stone fire-bounce bright
    ("a", "#261E31"),  # cold ash dark
    ("A", "#483E54"),  # cold ash light
    ("l", "#3B1E1A"),  # charred log dark
    ("L", "#663626"),  # wood log warm
    ("g", "#A81F1A"),  # crimson outer glow
    ("r", "#DC3818"),  # deep flame red-orange
    ("f", "#F7691A"),  # rich flame orange
    ("m", "#FFA726"),  # bright saffron orange
    ("y", "#FFDF4F"),  # bright gold
    ("c", "#FFF9D8"),  # tight incandescent cream-white heart
]


def draw_stone_ring(g, warm=0.0):
    stones_back = [
        (8, 25, 3.2, 1.9),
        (13, 24, 3.3, 1.8),
        (19, 24, 3.3, 1.8),
        (24, 25, 3.2, 1.9),
    ]
    for cx, cy, rx, ry in stones_back:
        for y in range(21, 29):
            for x in range(3, 29):
                dx = (x + 0.5 - cx) / rx
                dy = (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    if dy < -0.25:
                        ch = "W" if warm > 0.65 else ("w" if warm > 0.2 else "h")
                    elif dy < 0.25:
                        ch = "w" if warm > 0.55 else "S"
                    else:
                        ch = "s"
                    put(g, x, y, ch)
                elif d <= 1.55 and get(g, x, y) == ".":
                    put(g, x, y, "o")

    for y in range(25, 31):
        for x in range(7, 25):
            dx = (x + 0.5 - 16.0) / 8.5
            dy = (y + 0.5 - 28.0) / 3.0
            if dx * dx + dy * dy <= 1.0:
                if warm <= 0.01:
                    put(g, x, y, "A" if (x + y) % 3 == 0 else "a")
                else:
                    put(g, x, y, "g" if (x * 3 + y) % 4 != 0 else "l")

    for t in range(-5, 6):
        lx1 = 16 + t
        ly1 = int(round(27.5 - t * 0.26))
        put(g, lx1, ly1, "L" if warm > 0.2 else "l")
        put(g, lx1, ly1 + 1, "g" if warm > 0.5 else "l")
        lx2 = 16 + t
        ly2 = int(round(27.2 + t * 0.24))
        put(g, lx2, ly2, "L")
        put(g, lx2, ly2 + 1, "l")


def draw_front_stones(g, warm=0.0):
    stones_front = [
        (5, 28, 3.0, 2.2),
        (27, 28, 3.0, 2.2),
        (9, 31, 3.4, 2.1),
        (23, 31, 3.4, 2.1),
        (16, 32, 4.1, 2.2),
    ]
    for cx, cy, rx, ry in stones_front:
        for y in range(25, 36):
            for x in range(1, 31):
                dx = (x + 0.5 - cx) / rx
                dy = (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    facing_fire = dy < -0.25 and abs(x + 0.5 - 16.0) < 12.0
                    if facing_fire and warm > 0.5:
                        ch = "W" if dy < -0.55 and warm > 0.75 else "w"
                    elif dy < -0.3:
                        ch = "w" if warm > 0.35 else "h"
                    elif dy < 0.25:
                        ch = "S"
                    else:
                        ch = "s"
                    put(g, x, y, ch)
                elif d <= 1.5 and get(g, x, y) in (".", "o"):
                    put(g, x, y, "o")
    for x in range(3, 29):
        for y in range(34, 26, -1):
            if get(g, x, y) not in (".", "o"):
                if y + 1 < 36 and get(g, x, y + 1) == ".":
                    put(g, x, y + 1, "o")
                break


def draw_organic_flame(g, phase, height, width, sway_amp, licks=3, sparks=True):
    """Balanced rich color fire: crimson edge -> deep red-orange -> vibrant orange -> saffron -> gold -> small cream heart."""
    base_y = 28.8
    cx0 = 16.0
    plumes = [
        (0.0, 1.0, 1.0, 0.0, 1.0),
        (-width * 0.50, 0.64, 0.52, 2.1, -0.85),
        (width * 0.48, 0.68, 0.54, 4.2, 0.9),
    ]
    if licks >= 4:
        plumes.append((-width * 0.76, 0.40, 0.34, 1.2, -1.1))
        plumes.append((width * 0.74, 0.44, 0.35, 3.5, 1.1))

    heat = [[0.0] * 32 for _ in range(36)]
    for y in range(2, 30):
        for x in range(4, 28):
            best = 0.0
            for xoff, hs, ws, ph, sdir in plumes:
                ph_h = height * hs * (0.91 + 0.09 * math.sin(phase + ph))
                dy = base_y - (y + 0.5)
                if dy < -0.5 or dy > ph_h + 1.5:
                    continue
                u = max(0.0, min(1.0, dy / max(1.0, ph_h)))
                curve = math.sin(u * 4.5 - phase + ph) * sway_amp * sdir * (u ** 0.95)
                tip_lean = math.sin(phase + ph) * sway_amp * 0.5 * (u ** 1.45)
                px_c = cx0 + xoff * (1.0 - 0.30 * u) + curve + tip_lean
                half_w = width * ws * (0.55 * math.sin(min(1.0, u * 1.16) * math.pi) + 0.46 * (1.0 - u) ** 1.35)
                half_w = max(0.35, half_w)
                dist_x = abs((x + 0.5) - px_c) / half_w
                tip_pen = max(0.0, (dy - ph_h) / 1.3)
                val = 1.0 - (dist_x ** 1.55) - tip_pen
                if val > best:
                    best = val
            heat[y][x] = best

    for y in range(2, 30):
        for x in range(4, 28):
            v = heat[y][x]
            if v <= 0.03:
                continue
            dy = base_y - (y + 0.5)
            u = max(0.0, min(1.0, dy / max(1.0, height)))
            # Balanced temperature gradient: rich warm orange/red envelope with tight glowing gold/cream core
            core_dist = math.hypot((x + 0.5 - cx0) / (width * 0.42), (dy - height * 0.20) / (height * 0.28))
            t = v * (1.0 - 0.48 * u)
            if core_dist < 0.72 and v > 0.65:
                ch = "c"
            elif core_dist < 1.18 and t > 0.52:
                ch = "y"
            elif t > 0.48:
                ch = "m"
            elif t > 0.30:
                ch = "f"
            elif t > 0.14:
                ch = "r"
            else:
                ch = "g"
            put(g, x, y, ch)

    if sparks:
        for i in range(3 if height > 15 else 2):
            su = ((phase / (2 * math.pi)) + i * 0.37) % 1.0
            sy = int(round(base_y - height * (0.76 + su * 0.48)))
            sx = int(round(cx0 + math.sin(su * 5.0 + i * 2.2) * (width * 0.52) + (i - 1) * 1.8))
            if 1 <= sy < 24 and 5 <= sx < 27 and get(g, sx, sy) == ".":
                put(g, sx, sy, "y" if su < 0.4 else ("m" if su < 0.72 else "r"))


def build_fire_pxl():
    sprites = []
    g = make_grid(32, 36)
    draw_stone_ring(g, warm=0.0)
    draw_front_stones(g, warm=0.0)
    sprites.append(("fire_cold", 32, 36, [(1000, g)]))

    out_frames = []
    for f in range(4):
        g = make_grid(32, 36)
        pulse = 0.25 + 0.12 * math.sin(f * math.pi / 2)
        draw_stone_ring(g, warm=pulse)
        for ex, ey, ph in [(13, 27, 0), (16, 26, 1), (19, 27, 2), (15, 28, 3), (18, 28, 1)]:
            bright = ((f + ph) % 4) in (1, 2)
            put(g, ex, ey, "m" if bright else "r")
        draw_front_stones(g, warm=pulse)
        out_frames.append((260, g))
    sprites.append(("fire_out", 32, 36, out_frames))

    low_frames = []
    for f in range(6):
        g = make_grid(32, 36)
        phase = f * 2 * math.pi / 6
        draw_stone_ring(g, warm=0.55)
        draw_organic_flame(g, phase, height=11.5, width=5.6, sway_amp=1.5, licks=3, sparks=True)
        draw_front_stones(g, warm=0.55)
        low_frames.append((130, g))
    sprites.append(("fire_low", 32, 36, low_frames))

    mid_frames = []
    for f in range(8):
        g = make_grid(32, 36)
        phase = f * 2 * math.pi / 8
        draw_stone_ring(g, warm=0.82)
        draw_organic_flame(g, phase, height=17.5, width=7.2, sway_amp=2.1, licks=4, sparks=True)
        draw_front_stones(g, warm=0.82)
        mid_frames.append((110, g))
    sprites.append(("fire_mid", 32, 36, mid_frames))

    high_frames = []
    for f in range(8):
        g = make_grid(32, 36)
        phase = f * 2 * math.pi / 8
        draw_stone_ring(g, warm=1.0)
        draw_organic_flame(g, phase, height=23.5, width=8.6, sway_amp=2.6, licks=5, sparks=True)
        draw_front_stones(g, warm=1.0)
        high_frames.append((95, g))
    sprites.append(("fire_high", 32, 36, high_frames))

    write_pxl(
        os.path.join(PIXEL_DIR, "fire.pxl"),
        [
            "# Agiri Newroz hearth fire, 32x36 px. Hand-shaded volcanic stones, crossed logs, rich curling flames.",
        ],
        FIRE_PALETTE,
        sprites,
    )


# =============================================================================
# 2. SCENE.PXL — Seamless geological mountain facets (ZERO vertical seam artifacts!)
# =============================================================================

SCENE_PALETTE = [
    (".", "none"),
    ("1", "#1B1536"),  # far ridge shadow (themed tint ramp 1..4)
    ("2", "#2C2454"),  # far ridge body
    ("3", "#463D7D"),  # far ridge lit slope
    ("4", "#706BB0"),  # far ridge moonlight rim
    ("w", "#858DC8"),  # snow shadow
    ("V", "#B3B9E8"),  # snow mid
    ("W", "#E2E8FF"),  # snow bright moonlight
    ("a", "#171128"),  # near hills body
    ("A", "#271E42"),  # near hills rim
    ("T", "#0E091A"),  # near pine/cedar silhouettes
    ("h", "#181122"),  # ground deep
    ("g", "#251C30"),  # ground mid
    ("G", "#342742"),  # ground hilltop rim
    ("j", "#45334F"),  # warm clearing soil
    ("l", "#26493E"),  # alpine grass dark
    ("L", "#3D6E59"),  # alpine grass lit
    ("N", "#1C352E"),  # alpine grass deep
    ("p", "#FFF6C8"),  # Nergiz petal cream
    ("P", "#F5B82E"),  # Nergiz golden cup
    ("s", "#504766"),  # meadow stone shadow
    ("S", "#7B7196"),  # meadow stone top
    ("x", "#2E263D"),  # stone base shadow
    ("f", "#0C0713"),  # foreground silhouette deep
    ("F", "#1D142A"),  # foreground silhouette highlight
    ("m", "#FFF2C2"),  # moon warm cream
    ("M", "#DEC47A"),  # moon terminator gold
    ("n", "#FFFDF0"),  # moon brightest rim
]


def build_scene_pxl():
    sprites = []
    W, H = 176, 46
    far = make_grid(W, H)

    # Compose mountain range out of 6 overlapping alpine massifs (back-to-front painter's algorithm -> ZERO vertical seams!)
    # Each massif: (peak_x, peak_y, left_slope, right_slope, snow_h)
    massifs = [
        (46, 15, 0.62, 0.72, 6),   # back saddle peak
        (164, 15, 0.68, 0.65, 6),  # far right shoulder
        (144, 12, 0.72, 0.75, 8),  # Lesser Agiri!
        (83, 13, 0.68, 0.72, 8),   # central Zagros peak
        (25, 12, 0.75, 0.70, 8),   # western peak
        (117, 7, 0.68, 0.76, 11),  # Monumental Greater Agiri summit in foreground!
    ]
    for px, py, sl, sr, sh in massifs:
        for y in range(py, H):
            dy = y - py
            # Natural organic jagged ridge crease curving slightly left as it descends
            crease = px - int(round(dy * 0.22 + math.sin(dy * 0.42) * 1.4))
            xl = int(round(px - dy / sl - math.sin(dy * 0.35) * 1.1))
            xr = int(round(px + dy / sr + math.cos(dy * 0.30) * 1.1))
            for x in range(max(0, xl), min(W, xr + 1)):
                lit = x >= crease
                # Snow line drips organically along gullies
                snow_edge = sh + int(round(math.sin((x - px) * 0.55) * 1.6))
                if dy < snow_edge:
                    if lit:
                        ch = "W" if (x - crease < 5 or dy < snow_edge - 3) else "V"
                    else:
                        ch = "V" if dy <= 1 else "w"
                elif dy == snow_edge and (x + y) % 2 == 0:
                    ch = "V" if lit else "w"
                else:
                    # Smooth atmospheric valley mist near bottom
                    mist = (y - 28) / 18.0
                    if mist > 0.55 and bayer(x, y) < (mist - 0.45):
                        ch = "2"
                    elif lit:
                        dist_c = x - crease
                        if dist_c <= 2 and dy < sh + 12:
                            ch = "4"
                        elif dy < sh + 10:
                            ch = "3" if bayer(x, y) > 0.25 else "2"
                        else:
                            ch = "3" if bayer(x, y) > 0.55 else "2"
                    else:
                        if dy < sh + 9:
                            ch = "1"
                        elif dy < sh + 17:
                            ch = "1" if bayer(x, y) > 0.45 else "2"
                        else:
                            ch = "2"
                put(far, x, y, ch)
    sprites.append(("scene_far", W, H, [(1000, far)]))

    # ---- scene_near 176x26 ----
    near = make_grid(W, 26)
    hill_y = [int(round(13.0 + math.sin(x * 0.048) * 3.0 + math.cos(x * 0.11 + 1.2) * 1.6)) for x in range(W)]
    for x in range(W):
        hy = hill_y[x]
        put(near, x, hy, "A")
        if hy + 1 < 26 and (x % 2 == 0):
            put(near, x, hy + 1, "A")
        for y in range(hy + 1, 26):
            if get(near, x, y) == ".":
                put(near, x, y, "a")
    trees = [
        (6, 7), (11, 5), (15, 8), (22, 6), (27, 9), (31, 6), (40, 5), (45, 7),
        (51, 10), (55, 7), (61, 6), (67, 8), (74, 5), (86, 6), (92, 9), (96, 11),
        (101, 8), (105, 6), (114, 7), (121, 9), (126, 6), (138, 5), (146, 8),
        (151, 6), (160, 7), (167, 9), (171, 6),
    ]
    for tx, th in trees:
        base = hill_y[tx] + 2
        for dy in range(th):
            py = base - th + dy
            half = (dy * 3) // max(4, th)
            if dy > 2 and dy % 2 == 1:
                half = max(0, half - 1)
            for dx in range(-half, half + 1):
                put(near, tx + dx, py, "T")
    sprites.append(("scene_near", W, 26, [(1000, near)]))

    # ---- scene_ground 176x34 ----
    ground = make_grid(W, 34)
    top_y = [int(round(4.0 + math.sin(x * 0.042 - 0.6) * 2.2 + math.cos(x * 0.095) * 1.1)) for x in range(W)]
    for x in range(W):
        ty = top_y[x]
        for y in range(ty, 34):
            dy = y - ty
            cdx = (x - 102) / 36.0
            cdy = (y - 22) / 11.0
            in_clearing = cdx * cdx + cdy * cdy
            if dy <= 1:
                ch = "G"
            elif dy <= 3 and bayer(x, y) < 0.45:
                ch = "G"
            elif in_clearing < 0.85:
                ch = "j" if (in_clearing < 0.42 or bayer(x, y) < 0.45) else "g"
            elif y < 18:
                ch = "g"
            elif y < 22:
                ch = "g" if bayer(x, y) > (y - 18) / 4.5 else "h"
            else:
                ch = "h"
            put(ground, x, y, ch)

    tufts = [
        (10, 12), (18, 17), (26, 10), (35, 22), (44, 14), (52, 26), (14, 27),
        (140, 13), (148, 20), (156, 11), (163, 24), (169, 15), (145, 28), (62, 11),
    ]
    for gx, gy in tufts:
        put(ground, gx - 2, gy - 1, "L")
        put(ground, gx - 1, gy, "l")
        put(ground, gx, gy - 2, "L")
        put(ground, gx, gy - 1, "l")
        put(ground, gx, gy, "N")
        put(ground, gx + 1, gy, "l")
        put(ground, gx + 2, gy - 1, "l")

    flowers = [(19, 15), (43, 12), (149, 18), (162, 22), (33, 21)]
    for fx, fy in flowers:
        put(ground, fx, fy + 1, "l")
        put(ground, fx, fy + 2, "N")
        put(ground, fx - 1, fy, "p")
        put(ground, fx + 1, fy, "p")
        put(ground, fx, fy - 1, "p")
        put(ground, fx, fy, "P")

    pebbles = [(28, 25, 4), (57, 19, 3), (137, 25, 4), (158, 18, 3)]
    for px0, py0, pw in pebbles:
        for i in range(pw):
            put(ground, px0 + i, py0, "S" if i < pw - 1 else "s")
            put(ground, px0 + i, py0 + 1, "s" if i > 0 else "x")
        put(ground, px0 + 1, py0 + 2, "x")
    sprites.append(("scene_ground", W, 34, [(1000, ground)]))

    # ---- scene_fore 176x11 ----
    fore = make_grid(W, 11)
    for x in range(W):
        put(fore, x, 10, "f")
        if x % 3 != 0:
            put(fore, x, 9, "f")
    for x in range(2, W - 2, 3):
        edge = min(x, W - 1 - x)
        max_h = 8 if edge < 28 else (5 if edge < 52 else 3)
        h = 2 + ((x * 7 + 3) % max_h)
        lean = 1 if (x % 6 == 0) else (-1 if (x % 9 == 0) else 0)
        for dy in range(h):
            bx = x + (lean if dy > h // 2 else 0)
            by = 9 - dy
            put(fore, bx, by, "F" if dy == h - 1 else "f")
    sprites.append(("scene_fore", W, 11, [(1000, fore)]))

    # ---- scene_moon 15x15 ----
    moon = make_grid(15, 15)
    for y in range(15):
        for x in range(15):
            d1 = math.hypot(x + 0.5 - 7.2, y + 0.5 - 7.5)
            d2 = math.hypot(x + 0.5 - 10.3, y + 0.5 - 6.1)
            if d1 <= 5.8 and d2 >= 4.95:
                if d1 > 4.6 and x < 7:
                    ch = "n"
                elif d2 < 5.75:
                    ch = "M"
                else:
                    ch = "m"
                put(moon, x, y, ch)
    put(moon, 11, 5, "n")
    sprites.append(("scene_moon", 15, 15, [(1000, moon)]))

    write_pxl(
        os.path.join(PIXEL_DIR, "scene.pxl"),
        [
            "# Agiri Hearth scene layers: Monumental Mount Agiri & Zagros range, cedar foothills, alpine meadow with Nergiz flowers, moon.",
            "# Chars 1..4 on scene_far are the themed tint ramp.",
        ],
        SCENE_PALETTE,
        sprites,
    )


# =============================================================================
# 4. APPICON.PXL — Art-pass v2: balanced flame warmth, 1px rim lights, sharp forge star
# =============================================================================

APPICON_PALETTE = [
    (".", "none"),
    ("o", "#140A20"),
    ("a", "#1B0F2E"),
    ("b", "#26153C"),
    ("c", "#341B47"),
    ("d", "#4A224A"),
    ("e", "#632D46"),
    ("f", "#843D3E"),
    ("g", "#A8523A"),
    ("n", "#29204E"),
    ("N", "#463C75"),
    ("p", "#6B61A6"),
    ("q", "#0E0718"),
    ("w", "#FFF8E4"),
    ("k", "#EE6A76"),
    ("v", "#C23622"),
    ("V", "#F76420"),
    ("u", "#FFA03C"),
    ("y", "#FFD644"),
    ("Y", "#FFF4A8"),
    ("i", "#C68B1C"),
    ("r", "#B8221A"),
    ("R", "#E63E1A"),
    ("F", "#FF781E"),
    ("M", "#FFB42E"),
    ("C", "#FFF9DC"),
    ("s", "#3A3250"),
    ("S", "#61567C"),
    ("h", "#9A90B8"),
    ("H", "#D2DAF8"),
    ("z", "#101B2E"),
    ("Z", "#1B2E4E"),
    ("t", "#2B4670"),
    ("T", "#4A6FA0"),
    ("G", "#237A46"),
    ("L", "#3FA660"),
    ("K", "#1B1618"),
    ("D", "#2E262A"),
    ("I", "#554B54"),
    ("x", "#FFFFFF"),
]


def make_radial_bg(top_ch, mid_ch, low_ch, glow_cx, glow_cy, glow_ramp, stars=True, ridge_ch=None):
    """Crisp 45x45 pixel sky/glow background (maps cleanly to 108x108 at 2.4 units/px, keeps vector XML tiny)."""
    S = 45
    bg = make_grid(S, S, top_ch)
    bands = [top_ch, mid_ch, low_ch]
    gcx = glow_cx / 2.0
    gcy = glow_cy / 2.0
    for y in range(S):
        t = y / float(S - 1) * (len(bands) - 1)
        i0 = min(len(bands) - 2, int(t))
        frac = t - i0
        for x in range(S):
            bg[y][x] = bands[i0 + 1] if frac > bayer(x, y) else bands[i0]
    if glow_ramp:
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - gcx, y - gcy) / 18.0
                if d < 1.0:
                    f = (1.0 - d) * len(glow_ramp)
                    lv = min(len(glow_ramp) - 1, int(f) + (1 if (f - int(f)) > bayer(x, y) else 0))
                    if lv > 0:
                        bg[y][x] = glow_ramp[lv - 1]
    if stars:
        star_pts = [(8, 8, "H"), (14, 6, "w"), (33, 7, "Y"), (37, 12, "H"), (10, 15, "h"), (30, 13, "w"), (22, 7, "h"), (36, 6, "h")]
        for sx, sy, sc in star_pts:
            put(bg, sx, sy, sc)
    if ridge_ch:
        for x in range(S):
            ry = int(round(36.5 + math.sin(x * 0.17) * 2.0 + math.cos(x * 0.38) * 1.1))
            for y in range(ry, S):
                bg[y][x] = ridge_ch
    return bg


def fg_to_mono(fg):
    m = make_grid(44, 44)
    for y in range(44):
        for x in range(44):
            ch = fg[y][x]
            if ch != "." and ch != "q":
                m[y][x] = "x"
    return m


def apply_right_rim(g, x_min, y_min, y_max, targets=("N", "p"), rim_ch="M"):
    """Apply a crisp 1-pixel rim light ONLY on the rightmost surface pixel of each row."""
    for y in range(y_min, y_max):
        for x in range(42, x_min - 1, -1):
            if get(g, x, y) in targets:
                put(g, x, y, rim_ch)
                break


def draw_icon_newroz():
    """Style 1 (Default): Sculpted Pet beside a lush scarlet-orange-gold Newroz flame."""
    fg = make_grid(44, 44)
    base_y = 35.5
    cx = 28.5
    for y in range(7, 36):
        for x in range(18, 40):
            dy = base_y - y
            u = max(0.0, min(1.0, dy / 25.5))
            sway = math.sin(u * 3.8 - 0.2) * 2.2 - u * 1.1
            main_w = 7.8 * (0.55 * math.sin(min(1.0, u * 1.12) * math.pi) + 0.45 * (1.0 - u) ** 1.35)
            d1 = abs(x - (cx + sway)) / max(0.75, main_w)
            u2 = max(0.0, min(1.0, dy / 16.5))
            d2 = abs(x - (34.0 + math.sin(u2 * 2.8) * 1.1)) / max(0.55, 3.6 * (1.0 - u2 ** 1.35)) if dy < 17 else 9.0
            v = max(1.0 - d1 ** 1.55, 1.0 - d2 ** 1.55)
            if v > 0.06:
                core_d = math.hypot((x - (cx - 0.5)) / 3.4, (dy - 6.0) / 6.5)
                t = v * (1.0 - 0.45 * u)
                if core_d < 0.72 and v > 0.68:
                    ch = "C"
                elif core_d < 1.25 and t > 0.50:
                    ch = "y"
                elif t > 0.46:
                    ch = "M"
                elif t > 0.26:
                    ch = "F"
                elif t > 0.12:
                    ch = "R"
                else:
                    ch = "r"
                put(fg, x, y, ch)
    # Floating golden sparks (not outlined into heavy blobs — crisp 1px)
    # Rounded volcanic hearth stones under flame
    for scx, scy, srx, sry in [(22.5, 36.2, 3.2, 2.0), (28.5, 37.2, 3.8, 2.1), (34.5, 36.2, 3.2, 2.0)]:
        for y in range(33, 41):
            for x in range(18, 40):
                d = ((x - scx) / srx) ** 2 + ((y - scy) / sry) ** 2
                if d <= 1.0:
                    put(fg, x, y, "u" if (y < scy - 0.8 and abs(x - scx) < srx * 0.65) else ("S" if y <= scy + 0.3 else "s"))

    # Pet on the left
    tail_pts = [(6, 35), (5, 34), (4, 32), (4, 30), (5, 28), (6, 27)]
    for i, (tx, ty) in enumerate(tail_pts):
        put(fg, tx, ty, "N" if i % 2 == 0 else "y")
        put(fg, tx + 1, ty, "u" if i > 3 else "n")
    ellipse(fg, 14.5, 31.0, 6.0, 5.4, "N")
    ellipse(fg, 13.0, 31.5, 4.4, 4.1, "n")
    ellipse(fg, 15.2, 31.8, 3.5, 3.5, "V")
    ellipse(fg, 16.0, 31.0, 1.9, 1.9, "u")
    ellipse(fg, 13.8, 33.2, 1.9, 1.4, "v")
    ellipse(fg, 10.5, 36.2, 2.5, 1.6, "N")
    ellipse(fg, 17.5, 36.2, 2.5, 1.6, "N")
    put(fg, 10, 36, "y"); put(fg, 17, 36, "y")
    ellipse(fg, 14.5, 20.5, 7.4, 6.1, "N")
    ellipse(fg, 14.0, 18.5, 5.9, 3.5, "p")
    # Crisp 1px fire rim light along Pet's right silhouette ONLY
    apply_right_rim(fg, 17, 15, 26, ("N", "p"), "u")
    apply_right_rim(fg, 17, 27, 35, ("N", "n"), "u")
    for sx, sy in [(11, 16), (12, 16), (16, 15), (17, 16), (14, 15), (10, 29), (11, 30)]:
        put(fg, sx, sy, "Y" if sy < 17 else "y")
    for ex in (11, 17):
        ellipse(fg, ex, 20.5, 2.0, 2.2, "q")
        put(fg, ex - 1, 19, "w"); put(fg, ex, 19, "w"); put(fg, ex + 1, 21, "y")
    put(fg, 8, 22, "k"); put(fg, 9, 22, "k")
    put(fg, 19, 22, "k"); put(fg, 20, 22, "k")
    for mx in (13, 14, 15):
        put(fg, mx, 23, "k")
    put(fg, 12, 23, "o"); put(fg, 16, 23, "o")
    outline(fg, "o")
    # Delicate bright sparks outside the outline so they twinkle lightly
    put(fg, 24, 7, "Y"); put(fg, 33, 10, "y"); put(fg, 22, 11, "M")
    bg = make_radial_bg("a", "b", "c", 54, 52, ["d", "e", "f", "g"], stars=True, ridge_ch="o")
    return fg, bg, fg_to_mono(fg)


def draw_icon_pet():
    """Style 2: Close-up portrait of Pet with delicate 1px golden rim highlight."""
    fg = make_grid(44, 44)
    ellipse(fg, 22.0, 34.0, 11.2, 6.4, "n")
    ellipse(fg, 22.0, 35.0, 6.4, 4.4, "V")
    ellipse(fg, 23.0, 33.8, 3.6, 2.3, "u")
    ellipse(fg, 22.0, 20.5, 13.2, 10.4, "N")
    ellipse(fg, 22.0, 17.0, 10.4, 5.8, "p")
    # Delicate 1px warm golden rim along right cheek/temple only!
    apply_right_rim(fg, 28, 12, 29, ("N", "p"), "M")
    apply_right_rim(fg, 28, 30, 37, ("n",), "u")
    for sx, sy, r in [(14, 13, 1.5), (21.5, 11.5, 1.7), (29, 13, 1.5), (12, 18, 1.1), (31, 18, 1.1), (14, 32, 1.2), (30, 32, 1.2)]:
        ellipse(fg, sx, sy, r, r, "y")
        put(fg, int(round(sx)), int(round(sy)), "Y")
    for ex in (16.0, 28.0):
        ellipse(fg, ex, 21.0, 3.3, 3.5, "q")
        ellipse(fg, ex - 1.0, 19.6, 1.1, 1.1, "w")
        put(fg, int(ex + 1), 22, "y"); put(fg, int(ex + 2), 22, "M")
    ellipse(fg, 11.5, 24.5, 1.7, 1.1, "k")
    ellipse(fg, 32.5, 24.5, 1.7, 1.1, "k")
    for mx in range(19, 26):
        put(fg, mx, 25, "k")
    put(fg, 18, 24, "o"); put(fg, 26, 24, "o")
    outline(fg, "o")
    bg = make_radial_bg("a", "b", "c", 45, 48, ["d", "e", "f"], stars=True)
    return fg, bg, fg_to_mono(fg)


def draw_icon_flame():
    """Style 3: Calligraphic sacred Agiri Newroz flame crest with rich scarlet->orange->gold->cream depth."""
    fg = make_grid(44, 44)
    base_y = 35.5
    cx = 22.0
    for y in range(6, 36):
        for x in range(7, 37):
            dy = base_y - y
            u = max(0.0, min(1.0, dy / 27.5))
            sway = math.sin(u * 4.0 - 0.35) * 2.5
            w_main = 11.6 * (0.56 * math.sin(min(1.0, u * 1.12) * math.pi) + 0.44 * (1.0 - u) ** 1.3)
            d1 = abs(x - (cx + sway)) / max(0.75, w_main)
            uL = max(0.0, min(1.0, dy / 20.0))
            dL = abs(x - (13.2 - math.sin(uL * 2.9) * 1.4)) / max(0.55, 4.4 * (1.0 - uL ** 1.3)) if dy < 20 else 9.0
            uR = max(0.0, min(1.0, dy / 22.0))
            dR = abs(x - (30.8 + math.sin(uR * 3.1) * 1.4)) / max(0.55, 4.6 * (1.0 - uR ** 1.3)) if dy < 22 else 9.0
            v = max(1.0 - d1 ** 1.55, 1.0 - dL ** 1.55, 1.0 - dR ** 1.55)
            if v > 0.06:
                core_d = math.hypot((x - cx) / 4.4, (dy - 7.5) / 8.0)
                t = v * (1.0 - 0.45 * u)
                if core_d < 0.72 and v > 0.68:
                    ch = "C"
                elif core_d < 1.25 and t > 0.50:
                    ch = "y"
                elif t > 0.46:
                    ch = "M"
                elif t > 0.27:
                    ch = "F"
                elif t > 0.13:
                    ch = "R"
                else:
                    ch = "r"
                put(fg, x, y, ch)
    for scx, scy, srx, sry in [(12.5, 35.5, 3.6, 2.2), (18.5, 37.2, 4.0, 2.3), (25.5, 37.2, 4.0, 2.3), (31.5, 35.5, 3.6, 2.2)]:
        for y in range(32, 41):
            for x in range(7, 37):
                d = ((x - scx) / srx) ** 2 + ((y - scy) / sry) ** 2
                if d <= 1.0:
                    put(fg, x, y, "u" if (y < scy - 0.8 and abs(x - scx) < srx * 0.65) else ("S" if y <= scy + 0.4 else "s"))
    outline(fg, "o")
    put(fg, 16, 7, "Y"); put(fg, 29, 9, "y"); put(fg, 22, 4, "M")
    bg = make_radial_bg("K", "o", "a", 45, 50, ["d", "e", "f", "g"], stars=False)
    return fg, bg, fg_to_mono(fg)


def draw_icon_zagros():
    """Style 4: Twin snow-capped Mount Agiri peaks with natural diagonal ridge crease + beacon."""
    fg = make_grid(44, 44)
    for y in range(6, 17):
        for x in range(8, 19):
            if math.hypot(x - 13.0, y - 11.5) <= 4.3 and math.hypot(x - 15.2, y - 10.3) >= 3.6:
                put(fg, x, y, "C" if x < 12 else "Y")
    for y in range(11, 36):
        for x in range(5, 39):
            d_big = abs(x - 26.5) - (y - 11.0) * 0.72
            d_sm = abs(x - 14.0) - (y - 19.0) * 0.80
            if d_big <= 0.3:
                dy = y - 11
                crease = 26.5 - dy * 0.18 + math.sin(dy * 0.6) * 1.1
                lit = x >= crease
                snow_line = 8 + int(round(math.sin((x - 26) * 0.7) * 1.2))
                if dy < snow_line:
                    ch = "w" if lit else "H"
                elif dy == snow_line and x % 2 == 0:
                    ch = "h"
                else:
                    ch = "T" if lit else "t"
                put(fg, x, y, ch)
            elif d_sm <= 0.3:
                dy = y - 19
                crease = 14.0 - dy * 0.16
                lit = x >= crease
                if dy < 5:
                    ch = "w" if lit else "H"
                else:
                    ch = "T" if lit else "Z"
                put(fg, x, y, ch)
    for x in range(6, 38):
        put(fg, x, 35, "z"); put(fg, x, 36, "o")
    for bx, by, bc in [(20, 31, "R"), (20, 32, "F"), (19, 33, "F"), (20, 33, "Y"), (21, 33, "F"), (19, 34, "M"), (20, 34, "C"), (21, 34, "M")]:
        put(fg, bx, by, bc)
    outline(fg, "o")
    put(fg, 22, 8, "w"); put(fg, 34, 10, "Y"); put(fg, 27, 13, "H")
    bg = make_radial_bg("z", "Z", "a", 45, 62, ["b", "c", "e"], stars=True)
    return fg, bg, fg_to_mono(fg)


def draw_icon_kawa():
    """Style 5: Blacksmith Kawa's forge — anvil + razor-sharp 8-point forged molten star burst!"""
    fg = make_grid(44, 44)
    cx, cy = 22.0, 15.5
    # Slender concave 4-point main star + delicate diagonal rays (NO blocky square!)
    for y in range(4, 27):
        for x in range(8, 36):
            dx = abs(x - cx)
            dy = abs(y - cy)
            # Hyperbolic concave star metric: (dx^0.55 + dy^0.55) creates classic pixel-art sparkle star!
            s4 = (dx ** 0.58) + (dy ** 0.58)
            r = math.hypot(dx, dy)
            # Slender diagonal glint
            u = abs(dx - dy)
            on_diag = u <= 0.75 and r <= 7.2
            if r <= 2.3:
                put(fg, x, y, "C")
            elif s4 <= 4.35 and r <= 10.5:
                put(fg, x, y, "Y" if r < 4.5 else ("M" if r < 7.5 else "F"))
            elif on_diag:
                put(fg, x, y, "y" if r < 5.0 else "F")
    # Sturdy iron anvil
    for y in range(25, 37):
        for x in range(7, 37):
            in_horn = (25 <= y <= 28) and (7 + (y - 25) * 2 <= x <= 14)
            in_top = (25 <= y <= 29) and (13 <= x <= 35)
            in_neck = (30 <= y <= 33) and (16 <= x <= 29)
            in_base = (34 <= y <= 36) and (11 <= x <= 34)
            if in_horn or in_top or in_neck or in_base:
                if y == 25:
                    ch = "M" if 17 <= x <= 27 else "I"
                elif y == 26:
                    ch = "F" if 18 <= x <= 26 else "I"
                elif y <= 29 or y == 34:
                    ch = "I" if x < 24 else "D"
                else:
                    ch = "D" if x < 25 else "K"
                put(fg, x, y, ch)
    outline(fg, "o")
    # Twinkling airborne forge embers
    for sx, sy, sc in [(11, 10, "Y"), (33, 9, "M"), (10, 21, "F"), (34, 20, "Y"), (16, 7, "F"), (29, 7, "Y")]:
        put(fg, sx, sy, sc)
    bg = make_radial_bg("K", "D", "K", 45, 36, ["e", "f", "g"], stars=False)
    return fg, bg, fg_to_mono(fg)


def draw_icon_roj():
    """Style 6: Crisp symmetrical 16-rayed golden Newroz Sun (Roj) — clean rays with zero merged pixels!"""
    fg = make_grid(44, 44)
    cx, cy = 22.0, 22.0
    for y in range(4, 40):
        for x in range(4, 40):
            dx = x - cx
            dy = y - cy
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            # 16 clean evenly spaced alternating long/short solar rays (symmetric across all 4 axes!)
            ray = math.cos(ang * 16.0)
            is_major = math.cos(ang * 8.0) > 0.0
            max_r = 16.8 if is_major else 14.5
            if r <= 8.2:
                if r <= 3.6:
                    put(fg, x, y, "C")
                elif r <= 6.2:
                    put(fg, x, y, "Y")
                else:
                    put(fg, x, y, "y")
            elif r <= max_r and ray > (0.48 + (r - 8.2) * 0.065):
                put(fg, x, y, "Y" if r < 11.0 else ("y" if r < 13.8 else "M"))
    outline(fg, "o")
    bg = make_radial_bg("c", "e", "f", 45, 44, ["f", "g"], stars=False)
    for x in range(45):
        ry = int(round(34 + math.sin(x * 0.15) * 2.2))
        for y in range(ry, 45):
            bg[y][x] = "L" if y < ry + 2 else "G"
    return fg, bg, fg_to_mono(fg)


def build_appicon_pxl():
    notif_rows = [
        "....................",
        "...........x........",
        "..........xx........",
        ".........xxx........",
        ".........xxxx.......",
        "........xxxxx.......",
        ".......xxxxxxx......",
        ".......xxxxxxx..x...",
        "......xxxxxxxx.xx...",
        ".....xxxx.xxxxxxx...",
        "..x..xxx...xxxxxx...",
        "..xx.xxx...xxxxxxx..",
        ".xxxxxxx...xxxxxxx..",
        ".xxxxxx.....xxxxxx..",
        ".xxxxxx..x..xxxxxx..",
        ".xxxxxx.xxx.xxxxxx..",
        ".xxxxxxxxxxxxxxxxx..",
        "..xxxxxxxxxxxxxxx...",
        "..xxxxxxxxxxxxxxx...",
        "...xxxxxxxxxxxxx....",
        ".....xxxxxxxxx......",
        "....................",
    ]
    notif = [list(r) for r in notif_rows]

    newroz_fg, newroz_bg, newroz_mono = draw_icon_newroz()
    pet_fg, pet_bg, pet_mono = draw_icon_pet()
    flame_fg, flame_bg, flame_mono = draw_icon_flame()
    zagros_fg, zagros_bg, zagros_mono = draw_icon_zagros()
    kawa_fg, kawa_bg, kawa_mono = draw_icon_kawa()
    roj_fg, roj_bg, roj_mono = draw_icon_roj()

    sprites = [
        ("icon_fg", 44, 44, [(1000, newroz_fg)]),
        ("icon_bg", 45, 45, [(1000, newroz_bg)]),
        ("icon_mono", 44, 44, [(1000, newroz_mono)]),
        ("notif_flame", 20, 22, [(1000, notif)]),
        ("icon_pet_fg", 44, 44, [(1000, pet_fg)]),
        ("icon_pet_bg", 45, 45, [(1000, pet_bg)]),
        ("icon_pet_mono", 44, 44, [(1000, pet_mono)]),
        ("icon_flame_fg", 44, 44, [(1000, flame_fg)]),
        ("icon_flame_bg", 45, 45, [(1000, flame_bg)]),
        ("icon_flame_mono", 44, 44, [(1000, flame_mono)]),
        ("icon_zagros_fg", 44, 44, [(1000, zagros_fg)]),
        ("icon_zagros_bg", 45, 45, [(1000, zagros_bg)]),
        ("icon_zagros_mono", 44, 44, [(1000, zagros_mono)]),
        ("icon_kawa_fg", 44, 44, [(1000, kawa_fg)]),
        ("icon_kawa_bg", 45, 45, [(1000, kawa_bg)]),
        ("icon_kawa_mono", 44, 44, [(1000, kawa_mono)]),
        ("icon_roj_fg", 44, 44, [(1000, roj_fg)]),
        ("icon_roj_bg", 45, 45, [(1000, roj_bg)]),
        ("icon_roj_mono", 44, 44, [(1000, roj_mono)]),
    ]

    write_pxl(
        os.path.join(PIXEL_DIR, "appicon.pxl"),
        [
            "# Agiri customizable launcher icons (6 styles: Newroz, Pet, Flame, Zagros, Kawa, Roj) + notif_flame.",
        ],
        APPICON_PALETTE,
        sprites,
    )


# =============================================================================
# Command line
# =============================================================================

# The three files this script generates. avatars.pxl, mascot.pxl and icons.pxl have no generator: they are
# the source themselves and are edited by hand (see README.md).
BUILDERS = {
    "fire": ("fire.pxl", build_fire_pxl),
    "scene": ("scene.pxl", build_scene_pxl),
    "appicon": ("appicon.pxl", build_appicon_pxl),
}


def _selected(only):
    return [only] if only else list(BUILDERS)


def _check(only):
    """Builds into a temporary folder and compares the bytes with the committed files. Writes nothing in the tree."""
    global PIXEL_DIR
    tmp = tempfile.mkdtemp(prefix="craft_art_check_")
    ok = True
    try:
        PIXEL_DIR = tmp
        for key in _selected(only):
            name, build = BUILDERS[key]
            with contextlib.redirect_stdout(io.StringIO()):
                build()
            committed = os.path.join(COMMITTED_DIR, name)
            with open(os.path.join(tmp, name), "rb") as f:
                fresh = f.read()
            have = None
            if os.path.isfile(committed):
                with open(committed, "rb") as f:
                    have = f.read()
            if have == fresh:
                print(f"ok {name}")
            else:
                print(f"DIFF {name}")
                ok = False
    finally:
        PIXEL_DIR = COMMITTED_DIR
        shutil.rmtree(tmp, ignore_errors=True)
    return 0 if ok else 1


def main(argv=None):
    global PIXEL_DIR
    parser = argparse.ArgumentParser(
        description="Generates fire.pxl, scene.pxl and appicon.pxl into app/src/main/assets/pixel/.")
    parser.add_argument("--only", choices=sorted(BUILDERS), help="build only this file")
    parser.add_argument("--out", metavar="DIR", help="write into DIR (created) instead of the assets folder")
    parser.add_argument("--check", action="store_true",
                        help="build into a temporary folder and compare with the committed files; "
                             "prints ok or DIFF per file, exit 0 when all are equal, 1 otherwise; writes nothing")
    args = parser.parse_args(argv)
    if args.check:
        if args.out:
            parser.error("--check and --out cannot be combined")
        return _check(args.only)
    if args.out:
        PIXEL_DIR = os.path.abspath(args.out)
        os.makedirs(PIXEL_DIR, exist_ok=True)
    for key in _selected(args.only):
        BUILDERS[key][1]()
    print("Art pass v2 complete!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
