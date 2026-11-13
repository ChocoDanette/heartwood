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

def main(out):
    os.makedirs(out, exist_ok=True)
    for name, s in SPECIES.items():
        r = random.Random(name)
        for suffix, fn in (("bark", bark), ("end", end_grain), ("stripped_side", stripped_side),
                           ("stripped_end", stripped_end), ("planks", planks)):
            fn(s, r).save(os.path.join(out, f"{name}_{suffix}.png"))

if __name__ == "__main__":
    main(sys.argv[1])
