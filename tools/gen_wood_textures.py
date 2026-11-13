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
    if s["leaf"] == "pinnate":
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

def main(out):
    os.makedirs(out, exist_ok=True)
    for name, s in SPECIES.items():
        r = random.Random(name)
        for suffix, fn in (("bark", bark), ("end", end_grain), ("stripped_side", stripped_side),
                           ("stripped_end", stripped_end), ("planks", planks)):
            fn(s, r).save(os.path.join(out, f"{name}_{suffix}.png"))
        if "leaf" in s:
            leaves(s, r).save(os.path.join(out, f"{name}_leaves.png"))
            sapling(s, r).save(os.path.join(out, f"{name}_sapling.png"))

if __name__ == "__main__":
    main(sys.argv[1])
