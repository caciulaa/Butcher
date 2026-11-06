"""The banner: the whole word BUTCHER in the icon's style - gold pixel letters on the dark badge, the B's corner chopped.

144 x 32 pixel art written 8x up (1152 x 256) to docs/banner.png, for the Modrinth page and the README. It is not in
the jar. The letters share the icon's 18-row, 4-pixel stroke, its light from the top left, its pixel of depth and its
outline, so the B here is the icon's B.

    python3 tools/gen_banner.py
"""
import os
from PIL import Image, ImageDraw

import gen_icon as icon

W, H = 144, 32
GAP = 3

LETTERS = {
    "B": icon.B,
    "U": ["####......####"] * 15 + ["##############", ".############.", "..##########.."],
    "T": ["################"] * 4 + ["......####......"] * 14,
    "C": ["..#############", ".##############", "###############"] + ["####..........."] * 12
         + ["###############", ".##############", "..#############"],
    "H": ["####......####"] * 7 + ["##############"] * 4 + ["####......####"] * 7,
    "E": ["##############"] * 4 + ["####.........."] * 3 + ["############.."] * 4 + ["####.........."] * 3
         + ["##############"] * 4,
    "R": ["#############...", "##############..", "###############.", "####......#####.", "####.......####.",
          "####.......####.", "####......#####.", "##############..", "#############...", "####.....####...",
          "####......####..", "####......####..", "####.......####.", "####.......####.", "####........####",
          "####........####", "####........####", "####........####"],
}


def letter(rows, ox, oy):
    px = {}
    h = len(rows)
    for r, row in enumerate(rows):
        w = len(row)
        for c, ch in enumerate(row):
            if ch != '#':
                continue
            col = icon.GOLD
            if r <= 1 or c <= 1:
                col = icon.LIGHT
            if r >= h - 2 or c >= w - 2:
                col = icon.SHADE
            px[(ox + c, oy + r)] = col
    return px


def chop(px, k):
    """The icon's cut, along x + y = k."""
    out = {}
    for (x, y), col in px.items():
        s = x + y - k
        if s == 0:
            continue
        out[(x - 1, y - 1) if s < 0 else (x, y)] = col
    for (x, y) in px:
        s = x + y - k
        if s == -1:
            out[(x - 1, y - 1)] = icon.HOT_PIECE
        if s == 1:
            out[(x, y)] = icon.HOT_LETTER
    return out


def banner():
    word = "BUTCHER"
    total = sum(len(LETTERS[ch][0]) for ch in word) + GAP * (len(word) - 1)
    x = (W - total) // 2 + 1
    oy = 7
    px = {}
    for i, ch in enumerate(word):
        part = letter(LETTERS[ch], x, oy)
        if i == 0:
            part = chop(part, x + oy + icon.CUT)
        px.update(part)
        x += len(LETTERS[ch][0]) + GAP
    # Depth, then the ink outline, exactly as the icon draws them.
    deep = dict(px)
    for (a, b) in px:
        if (a + 1, b + 1) not in deep:
            deep[(a + 1, b + 1)] = icon.DEPTH
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    p = im.load()
    for (a, b), c in deep.items():
        p[a, b] = c
    out = im.copy()
    o = out.load()
    for b in range(H):
        for a in range(W):
            if p[a, b][3]:
                continue
            if any(0 <= a + dx < W and 0 <= b + dy < H and p[a + dx, b + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[a, b] = icon.INK
    # Sparks off the B's cut, as on the icon.
    bx = (W - total) // 2 + 1
    k = bx + oy + icon.CUT
    for dx, a in ((0, 255), (1, 190), (2, 120)):
        for sx in (bx + 6 + dx, bx - 3 - dx):
            o[sx, k - sx] = icon.SPARK + (a,)
    badge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(badge)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=6, fill=(28, 31, 40, 255), outline=(60, 52, 40, 255))
    d.line((6, 1, W - 7, 1), fill=(90, 80, 60, 255))
    badge.alpha_composite(out)
    return badge


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    dest = os.path.join(here, "..", "docs", "banner.png")
    banner().resize((W * 8, H * 8), Image.NEAREST).save(dest)
    print("wrote", os.path.normpath(dest))
