"""Generates 16x16 wood textures (bark, end grain, stripped, planks) for Heartwood species.

Usage: python3 tools/gen_wood_textures.py <output dir>
Each species is defined by a few colours; the patterns are procedural and seeded,
so the output is reproducible.
"""
import math, random, sys, os
from PIL import Image

# Real colours, as 5-shade palettes from darkest to lightest.
# bark: outer bark; wood: sawn/stripped wood; heart: optional darker heartwood for end grain.
SPECIES = {
    # Beech: smooth silver-grey bark; pale pinkish-buff wood.
    "beech": dict(bark=[(92, 90, 84), (116, 114, 107), (138, 136, 128), (157, 155, 147), (176, 174, 166)],
                  wood=[(170, 128, 98), (192, 150, 118), (212, 172, 140), (226, 190, 158), (238, 206, 176)],
                  style="smooth"),
    # Ash: pale grey bark with interlaced diamond ridges; creamy white-tan wood.
    "ash":   dict(bark=[(84, 82, 74), (108, 105, 96), (130, 127, 116), (150, 147, 136), (170, 167, 156)],
                  wood=[(178, 160, 122), (200, 183, 145), (218, 202, 166), (232, 218, 184), (243, 232, 202)],
                  style="diamond", leaf="pinnate", sapling_leaf=(96, 140, 60)),
    # Scots pine: orange-red flaky plates; yellowish sapwood, reddish heartwood.
    "scots_pine": dict(bark=[(88, 50, 34), (124, 70, 44), (160, 92, 56), (190, 116, 70), (214, 142, 90)],
                  wood=[(184, 136, 80), (206, 160, 100), (222, 182, 122), (234, 200, 142), (244, 216, 162)],
                  heart=[(160, 98, 58), (182, 118, 72)],
                  style="plates", leaf="needles", sapling_leaf=(56, 98, 60)),
    # Sweet chestnut: grey-brown bark with deep spiralling fissures; light brown wood.
    "chestnut": dict(bark=[(64, 56, 48), (88, 78, 68), (110, 99, 87), (130, 119, 106), (148, 138, 124)],
                  wood=[(150, 112, 70), (172, 132, 86), (192, 152, 104), (208, 170, 122), (222, 188, 142)],
                  heart=[(134, 96, 58), (154, 114, 72)],
                  style="spiral", leaf="long", sapling_leaf=(90, 132, 56)),
    # Sycamore maple: pinkish-grey bark flaking off in orange-buff patches; very pale cream wood.
    "maple": dict(bark=[(98, 90, 86), (122, 113, 108), (144, 135, 128), (164, 155, 146), (184, 168, 146)],
                  wood=[(196, 180, 150), (214, 200, 172), (230, 218, 192), (240, 230, 208), (248, 241, 224)],
                  style="flaky", leaf="lobed", sapling_leaf=(104, 146, 58)),
    # Teak: grey-brown fibrous bark with long furrows; golden-brown wood.
    "teak":  dict(bark=[(70, 60, 50), (94, 82, 68), (116, 103, 87), (136, 122, 104), (154, 140, 120)],
                  wood=[(128, 82, 40), (150, 100, 52), (172, 120, 64), (190, 138, 78), (206, 156, 94)],
                  heart=[(118, 74, 36), (138, 90, 46)],
                  style="fissured"),
}
for _s in SPECIES.values():
    _s["barkp"], _s["woodp"] = _s["bark"], _s["wood"]
    # Single colours used by doors, signs and boats.
    _s["bark_dark"], _s["bark"] = _s["barkp"][1], _s["barkp"][2]
    _s["wood_dark"], _s["wood"] = _s["woodp"][1], _s["woodp"][3]

def jitter(c, r, amt):
    n = r.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c)

def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def _px(c, r, amt=3):
    return jitter(c, r, amt)

# Bark furrows per species: (count, length range, drift). Drift > 0 makes furrows lean
# a little (chestnut's twist), drift < 0 lets them wander both ways (ash's interlacing).
BARK = {"smooth": (2, (4, 9), 0), "flaky": (3, (5, 10), 0), "diamond": (4, (6, 12), -1),
        "spiral": (5, (8, 16), 1), "plates": (4, (5, 11), 0), "fissured": (5, (10, 16), 0)}

def bark(s, r):
    """Log side drawn like vanilla oak: low-contrast vertical streaks in the mid shades,
    long thin dark furrows with a lighter edge on their right, and a few species marks."""
    P, st = s["barkp"], s["style"]
    count, (lo, hi), drift = BARK[st]
    grid = [[2] * 16 for _ in range(16)]
    for x in range(16):
        y = r.randrange(16)
        for _ in range(4):
            ln = r.randint(3, 8)
            shade = r.choice((2, 2, 3, 1)) if st not in ("smooth", "flaky") else r.choice((2, 3, 3))
            for k in range(ln):
                grid[(y + k) % 16][x] = shade
            y += ln
    xs = r.sample(range(0, 16, 2), count) if count <= 8 else list(range(0, 16, 2))
    for x0 in xs:
        for _ in range(2):
            x, y0, ln = x0, r.randrange(16), r.randint(lo, hi)
            for k in range(ln):
                y = (y0 + k) % 16
                if drift > 0 and k and k % 5 == 0: x += 1
                if drift < 0 and r.random() < 0.15: x += r.choice((-1, 1))
                grid[y][x % 16] = 0 if r.random() < 0.65 else 1
                if r.random() < 0.45 and grid[y][(x + 1) % 16] >= 2:
                    grid[y][(x + 1) % 16] = 3
    if st == "smooth":    # beech: short horizontal marks
        for _ in range(3):
            x, y = r.randrange(16), r.randrange(16)
            grid[y][x] = grid[y][(x + 1) % 16] = 1
    elif st == "flaky":   # maple: a few lighter flakes
        for _ in range(3):
            x, y = r.randrange(16), r.randrange(16)
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                grid[(y + dy) % 16][(x + dx) % 16] = 4
    elif st == "plates":  # pine: bright orange flakes between furrows
        for _ in range(10):
            x, y = r.randrange(16), r.randrange(16)
            if grid[y][x] >= 2: grid[y][x] = 4
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), _px(P[grid[y][x]], r, 2))
    return img

def _rings(s, r, bark_rim):
    """End grain in the vanilla manner: 1 px rim, then rounded-square growth rings drawn
    as thin darker lines on a light ground; heartwood tints the centre when the species has one."""
    W, B = s["woodp"], s["barkp"]
    heart = s.get("heart")
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if max(dx, dy) > 7:
                c = B[r.choice((1, 2, 2, 3))] if bark_rim else W[1]
            else:
                d = 0.65 * max(dx, dy) + 0.35 * math.hypot(dx, dy)
                inner = heart is not None and d < 3.6
                ground = heart[1] if inner else (W[3] if int(d) % 2 else W[4] if d > 4 else W[3])
                line = heart[0] if inner else W[2]
                ring_line = abs(d - round(d / 2) * 2) < 0.45 and d > 0.9
                c = line if ring_line else ground
                if d < 0.9: c = line
            img.putpixel((x, y), _px(c, r, 2))
    return img

def end_grain(s, r):
    return _rings(s, r, True)

def stripped_end(s, r):
    return _rings(s, r, False)

def stripped_side(s, r):
    """Stripped log side: long vertical grain streaks in the wood palette."""
    W = s["woodp"]
    img = Image.new("RGB", (16, 16))
    for x in range(16):
        y = 0
        while y < 16:
            ln = r.randint(3, 8)
            shade = r.choice((2, 3, 3, 3, 3, 4))
            if x in (0, 15) and r.random() < 0.5: shade = 2
            for k in range(ln):
                img.putpixel((x, (y + k) % 16), _px(W[shade], r, 2))
            y += ln
    for x in (3, 8, 12):
        for y in range(16):
            if r.random() < 0.6: img.putpixel((x, y), _px(W[1], r, 2))
    return img

def planks(s, r):
    """Planks in the vanilla layout: four 4 px boards, dark seam at the bottom of each,
    one end joint per board, horizontal grain streaks inside."""
    W = s["woodp"]
    img = Image.new("RGB", (16, 16))
    joints = {0: 9, 1: 3, 2: 12, 3: 6}
    for b in range(4):
        for yy in range(4):
            y = b * 4 + yy
            x = 0
            while x < 16:
                ln = r.randint(2, 6)
                shade = r.choice((2, 3, 3, 3, 3, 3)) if yy < 3 else 1
                if yy == 0 and r.random() < 0.25: shade = 4
                for k in range(ln):
                    if x + k < 16:
                        img.putpixel((x + k, y), _px(W[shade], r, 2))
                x += ln
        for yy in range(3):
            img.putpixel((joints[b], b * 4 + yy), _px(W[1], r, 2))
    for y in range(3, 16, 4):
        for x in range(16):
            img.putpixel((x, y), _px(W[0] if r.random() < 0.3 else W[1], r, 2))
    return img

def leaves(s, r):
    """Grayscale leaves with transparency (tinted in game)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def put(x, y, v):
        img.putpixel((x % 16, y % 16), (v, v, v, 255))
    if s["leaf"] == "pinnate":
        # Ash: compound leaves, small leaflets along diagonal stalks.
        for sx, sy in ((1, 2), (8, 0), (4, 8), (11, 7), (0, 12), (9, 13)):
            for i in range(5):
                x, y = sx + i, sy + i // 2
                put(x, y, 70)
                put(x, y - 1, r.randint(150, 200)); put(x, y + 1, r.randint(130, 190))
        for y in range(16):
            for x in range(16):
                if img.getpixel((x, y))[3] == 0 and r.random() < 0.72:
                    put(x, y, r.randint(105, 185))
    elif s["leaf"] in ("long", "lobed"):
        # Broad leaves: chestnut long serrated leaves, maple five-lobed leaves; dense cover.
        for y in range(16):
            for x in range(16):
                if r.random() < 0.78:
                    put(x, y, r.randint(110, 190))
        for _ in range(7):
            cx, cy = r.randrange(16), r.randrange(16)
            if s["leaf"] == "long":
                for k in range(4):
                    put(cx + k, cy + k // 2, 70)
            else:
                for dx, dy in ((0, 0), (0, -1), (-1, 0), (1, 0), (-1, 1), (1, 1)):
                    put(cx + dx, cy + dy, 75)
    else:
        # Pine: tufts of long needles.
        for _ in range(9):
            cx, cy = r.randrange(16), r.randrange(16)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                for k in range(1, 4):
                    if r.random() < 0.8:
                        put(cx + dx * k, cy + dy * k, r.randint(120, 200))
            put(cx, cy, 80)
    return img

def sapling(s, r):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    stem = s["bark_dark"]
    for y in range(8, 16):
        img.putpixel((7, y), stem + (255,))
    leaf = s["sapling_leaf"]
    if s["leaf"] in ("pinnate", "long", "lobed"):
        for y, xs in ((3, (5, 6, 8, 9)), (4, (4, 5, 6, 7, 8, 9, 10)), (6, (3, 4, 5, 6, 8, 9, 10, 11)),
                      (7, (5, 6, 8, 9)), (9, (4, 5, 9, 10)), (10, (5, 9))):
            for x in xs:
                img.putpixel((x, y), jitter(leaf, r, 12) + (255,))
        for y in range(3, 8):
            img.putpixel((7, y), stem + (255,))
    else:
        for y in range(2, 11):
            half = (y - 1) // 2
            for x in range(7 - half, 8 + half):
                if r.random() < 0.85:
                    img.putpixel((x, y), jitter(leaf, r, 10) + (255,))
    return img

# Door / trapdoor styles: "panel" (framed panels, glazed top) or "rustic" (vertical boards, Z brace).
DOOR_STYLE = {"beech": "panel", "ash": "panel", "maple": "panel",
              "scots_pine": "rustic", "chestnut": "rustic", "teak": "rustic"}
IRON, IRON_LIGHT = (58, 58, 62), (140, 140, 146)

def _board(s, r, x, y, seams):
    c = s["wood"]
    if x in seams:
        c = s["wood_dark"]
    elif r.random() < 0.12:
        c = mix(s["wood"], s["wood_dark"], 0.5)
    return jitter(c, r, 4) + (255,)

def door_halves(s, r, style):
    """Returns (top, bottom) 16x16 RGBA textures."""
    frame = mix(s["wood_dark"], (0, 0, 0), 0.15)
    top, bot = Image.new("RGBA", (16, 16)), Image.new("RGBA", (16, 16))
    if style == "rustic":
        seams = {0, 5, 10, 15}
        for img in (top, bot):
            for y in range(16):
                for x in range(16):
                    img.putpixel((x, y), _board(s, r, x, y, seams))
        # Ledges (horizontal rails) and a diagonal brace on the lower half.
        for img, rows in ((top, (3, 4)), (bot, (11, 12))):
            for y in rows:
                for x in range(1, 15):
                    img.putpixel((x, y), jitter(frame if y == rows[1] else s["wood_dark"], r, 3) + (255,))
        for img, (y0, y1) in ((top, (5, 15)), (bot, (0, 10))):
            for y in range(y0, y1 + 1):
                x = 2 + round((y - y0) * 11 / (y1 - y0)) if img is top else 2 + round((y - y0 + 6) * 11 / 16)
                for dx in (0, 1):
                    if 0 < x + dx < 15:
                        img.putpixel((x + dx, y), jitter(s["wood_dark"], r, 3) + (255,))
        # Small window slit in the top half.
        for y in range(7, 10):
            for x in range(6, 10):
                top.putpixel((x, y), (0, 0, 0, 0))
    else:
        for img in (top, bot):
            for y in range(16):
                for x in range(16):
                    img.putpixel((x, y), jitter(s["wood"], r, 4) + (255,))
            for y in range(16):
                for x in (0, 1, 14, 15):
                    img.putpixel((x, y), jitter(s["wood_dark"] if x in (0, 15) else s["wood"], r, 3) + (255,))
        # Glazed top: four panes in a frame.
        for y in range(2, 13):
            for x in range(3, 13):
                if x in (7, 8) or y == 7:
                    top.putpixel((x, y), jitter(s["wood_dark"], r, 3) + (255,))
                else:
                    top.putpixel((x, y), (0, 0, 0, 0))
        for x in range(2, 14):
            top.putpixel((x, 1), jitter(frame, r, 3) + (255,)); top.putpixel((x, 13), jitter(frame, r, 3) + (255,))
        # Two sunken panels on the bottom.
        for y0, y1 in ((2, 7), (9, 14)):
            for y in range(y0, y1 + 1):
                for x in range(3, 13):
                    edge = y in (y0, y1) or x in (3, 12)
                    c = s["wood_dark"] if edge else mix(s["wood"], s["wood_dark"], 0.2)
                    bot.putpixel((x, y), jitter(c, r, 3) + (255,))
    # Iron handle on the opening side (top of the lower half).
    for y in range(0, 3):
        bot.putpixel((13, y), IRON + (255,))
    bot.putpixel((12, 1), IRON_LIGHT + (255,))
    # Hinges on the other side.
    for img, y in ((top, 12), (bot, 13)):
        for x in range(0, 3):
            img.putpixel((x, y), IRON + (255,))
    return top, bot

def trapdoor(s, r, style):
    img = Image.new("RGBA", (16, 16))
    frame = s["wood_dark"]
    for y in range(16):
        for x in range(16):
            if style == "rustic":
                img.putpixel((x, y), _board(s, r, x, y, {0, 5, 10, 15}))
            else:
                edge = x in (0, 15) or y in (0, 15)
                img.putpixel((x, y), jitter(frame if edge else s["wood"], r, 4) + (255,))
    if style == "rustic":
        for y in (2, 3, 12, 13):
            for x in range(16):
                img.putpixel((x, y), jitter(frame, r, 3) + (255,))
    else:
        # Four openings, like a grate.
        for x0, y0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 4):
                    img.putpixel((x, y), (0, 0, 0, 0))
    return img

def door_item(top, bot):
    full = Image.new("RGBA", (16, 32)); full.paste(top, (0, 0)); full.paste(bot, (0, 16))
    small = full.resize((8, 16), Image.NEAREST)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); img.paste(small, (4, 0))
    return img

def _fill(img, box, fn):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), fn(x, y))

def _plank_px(s, r, rows=4):
    def f(x, y):
        c = s["wood"]
        if y % rows == rows - 1 or (x + (y // rows) * 7) % 16 == 0:
            c = s["wood_dark"]
        elif r.random() < 0.12:
            c = mix(s["wood"], s["wood_dark"], 0.5)
        return jitter(c, r, 4) + (255,)
    return f

def _grain_px(s, r):
    # Stripped wood seen along the grain (vertical streaks).
    lines = {x for x in range(64) if r.random() < 0.3}
    return lambda x, y: jitter(s["wood_dark"] if x in lines and r.random() < 0.7 else s["wood"], r, 5) + (255,)

def sign_entity(s, r):
    """64x32 standing sign texture: board (0,0)-(52,14), post (0,14)-(8,30)."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _fill(img, (0, 0, 52, 14), _plank_px(s, r, 4))
    post = mix(s["wood_dark"], s["bark_dark"], 0.4)
    _fill(img, (0, 14, 8, 30), lambda x, y: jitter(post, r, 5) + (255,))
    return img

def hanging_entity(s, r):
    """64x32 hanging sign: beam (0,0)-(40,6), chains (0,6)-(38,12), board (0,12)-(32,24)."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _fill(img, (0, 0, 40, 6), _grain_px(s, r))
    _fill(img, (0, 12, 32, 24), _plank_px(s, r, 3))
    for x0 in (0, 3, 6, 9):  # two vertical chains, front and back faces
        for y in range(6, 12):
            if y % 2 == 0:
                img.putpixel((x0 + 1, y), IRON + (255,))
            else:
                img.putpixel((x0, y), IRON_LIGHT + (255,)); img.putpixel((x0 + 2, y), IRON + (255,))
    for x0 in (14, 26):  # V-shaped chains
        for y in range(6, 12):
            for x in (x0 + (y - 6), x0 + 11 - (y - 6)):
                img.putpixel((x, y), (IRON if y % 2 else IRON_LIGHT) + (255,))
    return img

def hanging_gui(s, r):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    _fill(img, (0, 4, 16, 16), _plank_px(s, r, 3))
    for y in range(0, 4):
        for x in (3, 12):
            img.putpixel((x, y), (IRON if y % 2 else IRON_LIGHT) + (255,))
    return img

def boat_entity(s, r, chest):
    """128x64 boat (128x128 with chest). Hull and paddles in planks, paddle blades darker."""
    img = Image.new("RGBA", (128, 128 if chest else 64), (0, 0, 0, 0))
    _fill(img, (0, 0, 62, 51), _plank_px(s, r, 3))
    _fill(img, (62, 0, 128, 40), _grain_px(s, r))
    if chest:
        cw, cd = (162, 120, 60), (108, 76, 38)
        _fill(img, (0, 59, 48, 96), lambda x, y: jitter(cd if (y in (63, 71, 75, 83, 95) or x % 12 == 0) else cw, r, 5) + (255,))
        _fill(img, (0, 59, 6, 64), lambda x, y: jitter((200, 200, 205), r, 6) + (255,))
    return img

def sign_item(s, r):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    f = _plank_px(s, r, 3)
    _fill(img, (1, 2, 15, 10), f)
    for x in range(1, 15):
        img.putpixel((x, 2), jitter(s["wood_dark"], r, 3) + (255,)); img.putpixel((x, 9), jitter(s["wood_dark"], r, 3) + (255,))
    for y in range(2, 10):
        img.putpixel((1, y), jitter(s["wood_dark"], r, 3) + (255,)); img.putpixel((14, y), jitter(s["wood_dark"], r, 3) + (255,))
    post = mix(s["wood_dark"], s["bark_dark"], 0.4)
    for y in range(10, 16):
        img.putpixel((7, y), jitter(post, r, 4) + (255,)); img.putpixel((8, y), jitter(post, r, 4) + (255,))
    return img

def hanging_item(s, r):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    _fill(img, (1, 0, 15, 2), _grain_px(s, r))
    for y in range(2, 6):
        for x in (4, 11):
            img.putpixel((x, y), (IRON if y % 2 else IRON_LIGHT) + (255,))
    _fill(img, (2, 6, 14, 14), _plank_px(s, r, 3))
    for x in range(2, 14):
        img.putpixel((x, 13), jitter(s["wood_dark"], r, 3) + (255,))
    return img

def boat_item(s, r, chest):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = s["wood_dark"]
    rows = {9: (1, 15), 10: (1, 15), 11: (2, 14), 12: (3, 13)}
    for y, (a, b) in rows.items():
        for x in range(a, b):
            c = dark if y in (9, 12) or x in (a, b - 1) else s["wood"]
            img.putpixel((x, y), jitter(c, r, 4) + (255,))
    for i in range(7):  # paddle across the hull
        img.putpixel((3 + i, 2 + i), jitter(mix(s["wood"], dark, 0.4), r, 3) + (255,))
    if chest:
        cw, cd = (162, 120, 60), (108, 76, 38)
        for y in range(4, 9):
            for x in range(8, 14):
                c = cd if y in (4, 8) or x in (8, 13) else cw
                img.putpixel((x, y), c + (255,))
        img.putpixel((10, 6), (200, 200, 205, 255)); img.putpixel((11, 6), (200, 200, 205, 255))
    return img

def main(out):
    os.makedirs(out, exist_ok=True)
    for name, s in SPECIES.items():
        r = random.Random(name)
        for suffix, fn in (("bark", bark), ("end", end_grain), ("stripped_side", stripped_side),
                           ("stripped_end", stripped_end), ("planks", planks)):
            fn(s, r).save(os.path.join(out, f"{name}_{suffix}.png"))
        style = DOOR_STYLE[name]
        top, bot = door_halves(s, r, style)
        top.save(os.path.join(out, f"{name}_door_top.png")); bot.save(os.path.join(out, f"{name}_door_bottom.png"))
        trapdoor(s, r, style).save(os.path.join(out, f"{name}_trapdoor.png"))
        door_item(top, bot).save(os.path.join(out, f"{name}_door_item.png"))
        for suffix, img in (("sign_entity", sign_entity(s, r)), ("hanging_entity", hanging_entity(s, r)),
                            ("hanging_gui", hanging_gui(s, r)), ("boat_entity", boat_entity(s, r, False)),
                            ("chest_boat_entity", boat_entity(s, r, True)), ("sign_item", sign_item(s, r)),
                            ("hanging_sign_item", hanging_item(s, r)), ("boat_item", boat_item(s, r, False)),
                            ("chest_boat_item", boat_item(s, r, True))):
            img.save(os.path.join(out, f"{name}_{suffix}.png"))
        if "leaf" in s:
            leaves(s, r).save(os.path.join(out, f"{name}_leaves.png"))
            sapling(s, r).save(os.path.join(out, f"{name}_sapling.png"))

if __name__ == "__main__":
    main(sys.argv[1])
