"""Generates the tree seeds: item textures and models, the seed mound, names, compost values, and
leaf/branch loot that drops seeds instead of saplings.
Run from the repository root, AFTER gen_species.py (which rewrites the species' leaves loot):
    python3 tools/gen_seeds.py <vanilla leaves loot dir>
The vanilla loot dir holds minecraft's <wood>_leaves.json loot tables for 1.21.1
(from github.com/misode/mcmeta, branch 1.21.1-data, data/minecraft/loot_table/blocks).
"""
import glob, json, math, os, sys
from PIL import Image

NS = "heartwood"
R = "src/main/resources"

# seed item: (sapling it replaces, english, french)
SEEDS = {
    "acorn": ("minecraft:oak_sapling", "Acorn", "Gland"),
    "birch_seeds": ("minecraft:birch_sapling", "Birch Seeds", "Graines de bouleau"),
    "spruce_seeds": ("minecraft:spruce_sapling", "Spruce Seeds", "Graines d'épicéa"),
    "teak_seeds": ("minecraft:jungle_sapling", "Teak Seeds", "Graines de teck"),
    "acacia_seeds": ("minecraft:acacia_sapling", "Acacia Seeds", "Graines d'acacia"),
    "beechnut": ("minecraft:dark_oak_sapling", "Beechnut", "Faîne"),
    "cherry_pit": ("minecraft:cherry_sapling", "Cherry Pit", "Noyau de cerise"),
    "ash_key": (f"{NS}:ash_sapling", "Ash Key", "Samare de frêne"),
    "scots_pine_seeds": (f"{NS}:scots_pine_sapling", "Scots Pine Seeds", "Graines de pin sylvestre"),
    "sweet_chestnut": (f"{NS}:chestnut_sapling", "Sweet Chestnut", "Châtaigne"),
    "maple_key": (f"{NS}:maple_sapling", "Maple Key", "Samare d'érable"),
}


def w(path, data):
    path = os.path.join(R, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


class Sprite:
    def __init__(self):
        self.img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((x, y), hexc(c) if isinstance(c, str) else c)

    def ellipse(self, cx, cy, rx, ry, pal, light=(-0.6, -0.6), angle=0.0):
        """Filled, shaded ellipse; pal = [dark, mid-dark, mid, light, highlight]."""
        ca, sa = math.cos(angle), math.sin(angle)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                u, v = dx * ca + dy * sa, -dx * sa + dy * ca
                d = (u / rx) ** 2 + (v / ry) ** 2
                if d > 1:
                    continue
                shade = (dx / rx) * light[0] + (dy / ry) * light[1]
                edge = d > 0.7
                i = 2 + round(shade * 1.4) - (1 if edge else 0)
                self.px(x, y, pal[max(0, min(4, i))])

    def outline(self, c):
        src = self.img.copy()
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3] == 0 and any(
                        0 <= x + a < 16 and 0 <= y + b < 16 and src.getpixel((x + a, y + b))[3] > 0
                        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.px(x, y, c)

    def save(self, name):
        p = os.path.join(R, f"assets/{NS}/textures/item/{name}.png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        self.img.save(p)


NUT = ["#3b2414", "#5a3820", "#7a4e2c", "#9a6a3e", "#c08a56"]
PALE = ["#6e5a3c", "#8f7650", "#ad9466", "#c8b07e", "#e2cc9a"]
WING = ["#7a6440", "#9c8352", "#b9a067", "#d3bb80", "#e8d49c"]
GREEN_WING = ["#5d6a32", "#76843e", "#909e4c", "#a9b660", "#c4cf7e"]


def acorn():
    s = Sprite()
    s.ellipse(8, 9.5, 3.6, 4.6, ["#4e3a1a", "#6c5024", "#8a6830", "#a6823e", "#c09c56"])
    cap = ["#3a2c1a", "#55402a", "#6e5638", "#86704a", "#a08a60"]
    s.ellipse(8, 5.6, 4.4, 2.4, cap)
    for x, y in ((6, 5), (8, 4), (10, 5), (7, 6), (9, 6)):
        s.px(x, y, "#3a2c1a")
    s.px(8, 2, "#55402a"); s.px(8, 3, "#55402a")
    s.outline("#24180c"); s.save("acorn")


def nut(name, pal, base, tri=False):
    s = Sprite()
    if tri:
        # beechnut: a three-sided nut, pointed at the top
        for y in range(3, 14):
            half = (y - 2) * 4.2 / 11
            for x in range(16):
                dx = x + 0.5 - 8
                if abs(dx) <= half:
                    i = 2 + (1 if dx < -half / 3 else 0) - (1 if dx > half / 2 else 0) - (1 if y > 11 else 0)
                    s.px(x, y, pal[max(0, min(4, i))])
        for y in range(5, 13):
            s.px(8, y, pal[1])
    else:
        s.ellipse(8, 8.5, 5.2, 5.0, pal)
        for x in range(4, 12):
            for y in range(11, 14):
                if s.img.getpixel((x, y))[3] > 0:
                    s.px(x, y, base[2 + (x % 3 == 0)])
        s.px(8, 3, pal[0]); s.px(8, 2, PALE[1])
        s.px(6, 6, pal[4]); s.px(7, 6, pal[4])
    s.outline("#1e120a"); s.save(name)


def samara(name, seed_pal, wing_pal, pair=False, angle=0.6):
    s = Sprite()
    def one(cx, cy, a):
        ca, sa = math.cos(a), math.sin(a)
        # wing: a long rounded blade from the seed
        for t in range(0, 9):
            px, py = cx + ca * t, cy - sa * t
            wdt = 1.8 if t < 7 else 1.2
            s.ellipse(px, py, wdt, wdt, wing_pal)
        for t in range(1, 9):
            s.px(round(cx + ca * t - 0.5 + sa * 0.9), round(cy - sa * t - 0.5 + ca * 0.9), wing_pal[1])
        s.ellipse(cx, cy, 2.0, 1.6, seed_pal, angle=-a)
    if pair:
        one(8.5, 12.5, math.pi - angle)
        one(8.5, 12.5, angle)
    else:
        one(4.5, 12.5, angle)
    s.outline("#2a1f10"); s.save(name)


def seeds(name, seed_pal, wing_pal=None, n=3):
    s = Sprite()
    spots = [(5, 10), (10, 7), (9, 12), (6, 5)][:n]
    for x, y in spots:
        if wing_pal:
            s.ellipse(x + 1.6, y - 1.6, 2.2, 1.4, wing_pal, angle=-0.8)
        s.ellipse(x, y, 1.9, 1.5, seed_pal, angle=0.4)
    s.outline("#24180c"); s.save(name)


def textures():
    acorn()
    nut("beechnut", NUT, PALE, tri=True)
    nut("sweet_chestnut", ["#3a1e10", "#5c3018", "#7c4422", "#9c5c32", "#c48450"], PALE)
    s = Sprite()
    s.ellipse(8, 8.5, 3.8, 4.6, PALE)
    for y in range(5, 13):
        s.px(8, y, PALE[1])
    s.outline("#4a3a22"); s.save("cherry_pit")
    samara("ash_key", ["#3e3018", "#56442a", "#6e5838", "#86704a", "#a08a60"], WING, angle=0.75)
    samara("maple_key", ["#5c4a2a", "#7a6236", "#967a44", "#b0945a", "#cab274"], GREEN_WING, pair=True, angle=0.75)
    seeds("birch_seeds", ["#5a4630", "#76603e", "#92784c", "#ad9260", "#c8ac7a"], WING, n=3)
    seeds("spruce_seeds", ["#3c2a1c", "#563c26", "#705032", "#8a6642", "#a88058"], ["#8a6a48", "#a8845a", "#c09a6a", "#d4b07e", "#e4c694"], n=2)
    seeds("scots_pine_seeds", ["#2e2218", "#463424", "#5e4630", "#78583e", "#946e52"], ["#7a5e3e", "#967650", "#b08c60", "#c6a274", "#dab88a"], n=2)
    seeds("teak_seeds", ["#5a4024", "#78562e", "#966c3a", "#b0844a", "#c89c60"], n=3)
    seeds("acacia_seeds", ["#2a2016", "#3e3020", "#54422c", "#6a5638", "#826c48"], n=4)


def main():
    vanilla = sys.argv[1] if len(sys.argv) > 1 else None
    textures()
    en, fr = {}, {}
    for name, (sap, e, f) in SEEDS.items():
        w(f"assets/{NS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        en[f"item.{NS}.{name}"] = e; fr[f"item.{NS}.{name}"] = f
    en[f"block.{NS}.seed_mound"] = "Planted Seed"; fr[f"block.{NS}.seed_mound"] = "Semis"

    # The planted seed: a small mound of the ground it was planted in.
    grounds = {"dirt": ("dirt", "dirt", False), "grass": ("grass_block_top", "dirt", True),
               "coarse_dirt": ("coarse_dirt", "coarse_dirt", False), "podzol": ("podzol_top", "dirt", False),
               "mycelium": ("mycelium_top", "dirt", False), "rooted_dirt": ("rooted_dirt", "rooted_dirt", False),
               "mud": ("mud", "mud", False), "moss": ("moss_block", "moss_block", False)}
    variants = {}
    for g, (top, side, tint) in grounds.items():
        up = {"texture": "#top"} | ({"tintindex": 0} if tint else {})
        faces = lambda: {d: {"texture": "#side"} for d in ("north", "south", "east", "west")} | {"up": dict(up)}
        w(f"assets/{NS}/models/block/seed_mound_{g}.json", {
            "parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"top": f"minecraft:block/{top}", "side": f"minecraft:block/{side}", "particle": f"minecraft:block/{side}"},
            "elements": [
                {"from": [3, 0, 3], "to": [13, 2, 13], "faces": faces()},
                {"from": [5, 2, 5], "to": [11, 3, 11], "faces": faces()},
                {"from": [4, 0, 6], "to": [12, 2.5, 10], "faces": faces()},
            ]})
        variants[f"ground={g}"] = [{"model": f"{NS}:block/seed_mound_{g}", "y": y} for y in (0, 90, 180, 270)]
    old_model = os.path.join(R, f"assets/{NS}/models/block/seed_mound.json")
    if os.path.exists(old_model): os.remove(old_model)
    w(f"assets/{NS}/blockstates/seed_mound.json", {"variants": variants})

    # Seeds compost like saplings.
    w("data/neoforge/data_maps/item/compostables.json",
      {"values": {f"{NS}:{n}": {"chance": 0.3} for n in SEEDS}})

    # Leaves and branches drop seeds instead of saplings.
    swap = {sap: f"{NS}:{n}" for n, (sap, _, _) in SEEDS.items()}
    def patch(obj):
        if isinstance(obj, dict):
            return {k: (swap.get(v, v) if k == "name" else patch(v)) for k, v in obj.items()}
        if isinstance(obj, list):
            return [patch(v) for v in obj]
        return obj
    for p in glob.glob(os.path.join(R, f"data/{NS}/loot_table/blocks/*_leaves.json")) + \
             glob.glob(os.path.join(R, f"data/{NS}/loot_table/blocks/*_branches.json")):
        data = json.load(open(p, encoding="utf-8"))
        w(os.path.relpath(p, R), patch(data))
    if vanilla:
        for p in sorted(glob.glob(os.path.join(vanilla, "*_leaves.json"))):
            data = json.load(open(p, encoding="utf-8"))
            patched = patch(data)
            if patched != data:
                w(f"data/minecraft/loot_table/blocks/{os.path.basename(p)}", patched)

    for lang, d in (("en_us", en), ("fr_fr", fr)):
        p = os.path.join(R, f"assets/{NS}/lang/{lang}.json")
        cur = json.load(open(p, encoding="utf-8"))
        cur.update(d)
        w(f"assets/{NS}/lang/{lang}.json", cur)


if __name__ == "__main__":
    main()
