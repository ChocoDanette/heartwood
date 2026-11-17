"""Generates 16x16 wood textures (bark, end grain, stripped, planks) for Heartwood species.

Usage: python3 tools/gen_wood_textures.py <output dir>
Each species is defined by a few colours; the patterns are procedural and seeded,
so the output is reproducible.
"""
import math, random, sys, os
from PIL import Image

SPECIES = {
    # name: bark, bark_dark, wood, wood_dark, style
    "beech": dict(bark=(150, 150, 142), bark_dark=(112, 112, 106), wood=(214, 182, 150), wood_dark=(190, 156, 124), style="smooth"),
    "ash":   dict(bark=(138, 136, 124), bark_dark=(100, 98, 90),   wood=(226, 210, 172), wood_dark=(200, 182, 142), style="diamond",
                  leaf="pinnate", sapling_leaf=(96, 140, 60)),
    "scots_pine": dict(bark=(150, 92, 60), bark_dark=(96, 58, 40), wood=(220, 178, 118), wood_dark=(190, 146, 88), style="plates",
                  leaf="needles", sapling_leaf=(56, 98, 60)),
    "chestnut": dict(bark=(118, 108, 96), bark_dark=(76, 68, 60), wood=(204, 164, 104), wood_dark=(174, 134, 78), style="spiral",
                  leaf="long", sapling_leaf=(90, 132, 56)),
    "maple": dict(bark=(132, 124, 112), bark_dark=(96, 88, 80), wood=(232, 214, 184), wood_dark=(208, 188, 156), style="flaky",
                  leaf="lobed", sapling_leaf=(104, 146, 58)),
    "teak":  dict(bark=(112, 94, 74),   bark_dark=(78, 64, 50),    wood=(176, 124, 64),  wood_dark=(146, 98, 48),  style="fissured"),
}

def jitter(c, r, amt):
    n = r.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c)

def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def bark(s, r):
    img = Image.new("RGB", (16, 16))
    if s["style"] == "smooth":
        # Smooth grey bark: soft mottling, a few short horizontal lenticels.
        for y in range(16):
            for x in range(16):
                t = 0.5 + 0.5 * math.sin(x * 0.9 + r.random()) * math.sin(y * 0.5 + 1.3)
                img.putpixel((x, y), jitter(mix(s["bark"], s["bark_dark"], 0.25 * t), r, 6))
        for _ in range(5):
            x, y = r.randrange(0, 14), r.randrange(0, 16)
            for dx in range(r.randint(2, 3)):
                img.putpixel(((x + dx) % 16, y), jitter(s["bark_dark"], r, 4))
    elif s["style"] == "diamond":
        # Ash: interlaced ridges forming a diamond (net) pattern.
        for y in range(16):
            for x in range(16):
                a = (x + y) % 6 == 0 or (x - y) % 6 == 0
                c = s["bark_dark"] if a and r.random() < 0.8 else s["bark"]
                img.putpixel((x, y), jitter(c, r, 6))
    elif s["style"] == "plates":
        # Scots pine: thick reddish plates separated by dark cracks.
        # Irregular columns, each cut by cracks at irregular heights.
        cols, x = [], 0
        while x < 16:
            wdt = r.randint(3, 5); cols.append((x, min(16, x + wdt))); x += wdt
        for x0, x1 in cols:
            cracks, y = set(), r.randrange(0, 4)
            while y < 16:
                cracks.add(y); y += r.randint(3, 6)
            for x in range(x0, x1):
                for y in range(16):
                    edge = x == x0 and r.random() < 0.9
                    crack = y in cracks and r.random() < 0.85
                    c = s["bark_dark"] if edge or crack else mix(s["bark"], (205, 135, 85), 0.3 * r.random())
                    img.putpixel((x, y), jitter(c, r, 5))
    elif s["style"] == "spiral":
        # Chestnut: deep fissures twisting diagonally around the trunk.
        for y in range(16):
            for x in range(16):
                f = (x + y // 3) % 4 == 0
                c = s["bark_dark"] if f and r.random() < 0.9 else s["bark"]
                img.putpixel((x, y), jitter(c, r, 7))
    elif s["style"] == "flaky":
        # Maple (sycamore): smooth bark flaking off in small patches.
        for y in range(16):
            for x in range(16):
                img.putpixel((x, y), jitter(s["bark"], r, 6))
        for _ in range(7):
            x0, y0 = r.randrange(16), r.randrange(16)
            for dx in range(r.randint(2, 3)):
                for dy in range(r.randint(1, 2)):
                    img.putpixel(((x0 + dx) % 16, (y0 + dy) % 16), jitter(mix(s["bark"], (190, 170, 130), 0.5), r, 6))
            img.putpixel(((x0 - 1) % 16, y0), jitter(s["bark_dark"], r, 4))
    else:
        # Fissured bark: vertical ridges with dark furrows.
        furrows = sorted(r.sample(range(16), 4))
        for x in range(16):
            for y in range(16):
                c = s["bark"]
                if x in furrows and r.random() < 0.85:
                    c = s["bark_dark"]
                elif (x - 1) in furrows:
                    c = mix(s["bark"], (255, 255, 255), 0.08)
                img.putpixel((x, y), jitter(c, r, 7))
    return img

def end_grain(s, r):
    img = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = max(abs(dx), abs(dy))  # square rings, like vanilla
            if d >= 7:
                c = jitter(s["bark"], r, 6)
            else:
                ring = int(math.hypot(dx, dy) * 0.95 + 0.3) in (1, 3, 5)
                c = jitter(s["wood_dark"] if ring else s["wood"], r, 5)
            img.putpixel((x, y), c)
    return img

def stripped_end(s, r):
    img = end_grain(s, r)
    for y in range(16):
        for x in range(16):
            if max(abs(x - 7.5), abs(y - 7.5)) >= 7:
                img.putpixel((x, y), jitter(s["wood_dark"], r, 5))
    return img

def stripped_side(s, r):
    img = Image.new("RGB", (16, 16))
    lines = set(r.sample(range(16), 5))
    for x in range(16):
        for y in range(16):
            c = s["wood_dark"] if x in lines and r.random() < 0.7 else s["wood"]
            img.putpixel((x, y), jitter(c, r, 5))
    return img

def planks(s, r):
    img = Image.new("RGB", (16, 16))
    seams = {0: 3, 1: 11, 2: 6, 3: 14}
    for y in range(16):
        board = y // 4
        for x in range(16):
            c = s["wood"]
            if y % 4 == 3 or x == seams[board]:
                c = s["wood_dark"]
            elif r.random() < 0.15:
                c = mix(s["wood"], s["wood_dark"], 0.5)
            img.putpixel((x, y), jitter(c, r, 4))
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
