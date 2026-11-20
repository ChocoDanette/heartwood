"""Generates models, blockstates, loot, tags and names for the variable-thickness boughs.
Run from the repository root: python3 tools/gen_boughs.py

A bough model is drawn pointing UP (towards its parent) and rotated by the blockstate:
a core cube of half-size r, and an arm of the same thickness reaching the surface of the
parent's core (half-size pr), which may lie inside the neighbouring block.
"""
import json, os

NS = "heartwood"
R = "src/main/resources"
# wood: (texture namespace, english, french)
WOODS = {
    "oak": ("minecraft", "Oak", "de chêne"), "spruce": ("minecraft", "Spruce", "d'épicéa"),
    "birch": ("minecraft", "Birch", "de bouleau"), "jungle": ("minecraft", "Teak", "de teck"),
    "acacia": ("minecraft", "Acacia", "d'acacia"), "dark_oak": ("minecraft", "Beech", "de hêtre"),
    "mangrove": ("minecraft", "Mangrove", "de palétuvier"), "cherry": ("minecraft", "Cherry", "de cerisier"),
    "ash": (NS, "Ash", "de frêne"), "scots_pine": (NS, "Scots Pine", "de pin sylvestre"),
    "chestnut": (NS, "Chestnut", "de châtaignier"), "maple": (NS, "Maple", "d'érable"),
}
ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
       "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}

def w(path, data):
    path = os.path.join(R, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def model(side, end, r, pr):
    lo, hi = 8 - r, 8 + r
    sides = lambda uv: {d: {"uv": uv, "texture": "#side"} for d in ("north", "south", "east", "west")}
    core = {"from": [lo, lo, lo], "to": [hi, hi, hi],
            "faces": sides([lo, lo, hi, hi]) | {"up": {"uv": [lo, lo, hi, hi], "texture": "#end"},
                                                 "down": {"uv": [lo, lo, hi, hi], "texture": "#end"}}}
    elements = [core]
    if pr is not None:
        top = 24 - pr
        if top > hi:
            elements.append({"from": [lo, hi, lo], "to": [hi, top, hi], "faces": sides([lo, 0, hi, top - hi])})
    return {"parent": "minecraft:block/block", "ambientocclusion": r >= 4,
            "textures": {"side": side, "end": end, "particle": side}, "elements": elements}

def notch_model(side, end, r, pr):
    """A vertical trunk piece hanging DOWN from its parent, with a V notch cut into its north face."""
    lo, hi = 8 - r, 8 + r
    depth = max(2, round(r * 0.8))
    elements = []
    for y in range(lo, hi):
        yc = y + 0.5
        d = max(0, round(depth * (1 - abs(yc - 8) / r)))
        bark = {"uv": [lo, 16 - (y + 1), hi, 16 - y], "texture": "#side"}
        faces = {"south": bark, "east": {"uv": [lo + d, 16 - (y + 1), hi, 16 - y], "texture": "#side"},
                 "west": {"uv": [lo, 16 - (y + 1), hi - d, 16 - y], "texture": "#side"},
                 "north": {"uv": [lo, 16 - (y + 1), hi, 16 - y], "texture": "#end" if d else "#side"},
                 "up": {"uv": [lo, lo + d, hi, hi], "texture": "#end"},
                 "down": {"uv": [lo, lo + d, hi, hi], "texture": "#end"}}
        elements.append({"from": [lo, y, lo + d], "to": [hi, y + 1, hi], "faces": faces})
    bottom = pr - 8
    if bottom < lo:
        sides = {f: {"uv": [lo, 0, hi, lo - bottom], "texture": "#side"} for f in ("north", "south", "east", "west")}
        elements.append({"from": [lo, bottom, lo], "to": [hi, lo, hi], "faces": sides})
    return {"parent": "minecraft:block/block", "ambientocclusion": True,
            "textures": {"side": side, "end": end, "particle": side}, "elements": elements}

NOTCH_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

def main():
    en, fr = {}, {}
    tag = []
    for wood, (tns, e, f) in WOODS.items():
        b = f"{wood}_bough"
        side, end = f"{tns}:block/{wood}_log", f"{tns}:block/{wood}_log_top"
        variants = {}
        for r in range(1, 9):
            w(f"assets/{NS}/models/block/bough/{wood}_{r}.json", model(side, end, r, None))
            for pr in range(r, 9):
                w(f"assets/{NS}/models/block/bough/{wood}_{r}_{pr}.json", model(side, end, r, pr))
                if r >= 2:
                    w(f"assets/{NS}/models/block/bough/{wood}_{r}_{pr}_notch.json", notch_model(side, end, r, pr))
            for pr in range(1, 9):
                for notch in ("none", "north", "south", "west", "east"):
                    key = lambda parent: f"notch={notch},parent={parent},parent_radius={pr},radius={r}"
                    variants[key("none")] = {"model": f"{NS}:block/bough/{wood}_{r}"}
                    for d, rot in ROT.items():
                        m = f"{NS}:block/bough/{wood}_{r}_{max(r, pr)}"
                        if d == "down" and notch != "none" and r >= 2:
                            variants[key(d)] = {"model": m + "_notch"} | ({"y": NOTCH_Y[notch]} if NOTCH_Y[notch] else {})
                        else:
                            variants[key(d)] = {"model": m} | rot
        w(f"assets/{NS}/blockstates/{b}.json", {"variants": variants})

        green = f"{NS}:green_{wood}_log"
        radius = lambda lo, hi: {"condition": "minecraft:block_state_property", "block": f"{NS}:{b}",
                                 "properties": {"radius": {"min": str(lo), "max": str(hi)}}}
        # Bucking by thickness: twigs give sticks, poles a log one time in two, thick pieces a green log.
        sticks = lambda n: {"type": "minecraft:item", "name": "minecraft:stick"} | (
            {"functions": [{"function": "minecraft:set_count", "count": n}]} if n != 1 else {})
        boom = {"condition": "minecraft:survives_explosion"}
        w(f"data/{NS}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "conditions": [radius(1, 1), boom, {"condition": "minecraft:random_chance", "chance": 0.5}],
             "entries": [sticks(1)]},
            {"rolls": 1, "conditions": [radius(2, 2), boom], "entries": [sticks(1)]},
            {"rolls": 1, "conditions": [radius(3, 3), boom], "entries": [sticks(2)]},
            {"rolls": 1, "conditions": [radius(4, 4), boom], "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": green, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}]},
                sticks(3)]}]},
            {"rolls": 1, "conditions": [radius(5, 8), boom], "entries": [{"type": "minecraft:item", "name": green}]}]})
        tag.append(f"{NS}:{b}")
        en[f"block.{NS}.{b}"] = f"{e} Bough"; fr[f"block.{NS}.{b}"] = f"Branche {f}"

    for t in ("logs", "logs_that_burn"):
        p = os.path.join(R, f"data/minecraft/tags/block/{t}.json")
        cur = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
        for v in tag:
            if v not in cur["values"]: cur["values"].append(v)
        w(f"data/minecraft/tags/block/{t}.json", cur)
    p = os.path.join(R, "data/minecraft/tags/block/mineable/axe.json"); cur = json.load(open(p))
    for v in tag:
        if v not in cur["values"]: cur["values"].append(v)
    json.dump(cur, open(p, "w"), indent=2)
    for lang, d in (("en_us", en), ("fr_fr", fr)):
        p = os.path.join(R, f"assets/{NS}/lang/{lang}.json")
        cur = json.load(open(p, encoding="utf-8")); cur.update(d)
        json.dump(cur, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)

if __name__ == "__main__":
    main()
