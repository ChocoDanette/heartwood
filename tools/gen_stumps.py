"""Generates the stumps a felled tree of boughs leaves: models, blockstates, loot, tags and names.
Run from the repository root, AFTER gen_boughs.py: python3 tools/gen_stumps.py

A stump is drawn as felled towards north: the front (fall side) is the low floor of the notch,
then a thin ragged hinge of wood left standing across the stump, then the back, cut a little
higher by the back cut. The blockstate turns it to the side the tree fell.
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_boughs import WOODS, NS, R, w, _segments, _u

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
FRONT_H, BACK_H, HINGE_H = 5, 7, 9


def box(x0, x1, z0, z1, h, lo, hi, zlo, zhi, ragged=False):
    """A block of wood from the ground up to h, split at block edges so the bark is not stretched."""
    out = []
    xs = _segments(x0, x1)
    if ragged:   # splinters: narrow strips of uneven height
        xs = [(a, min(a + 2, c1)) for c0, c1 in xs for a in range(c0, c1, 2)]
    for a0, a1 in xs:
        for b0, b1 in _segments(z0, z1):
            ux0, ux1 = _u(a0, a1); uz0, uz1 = _u(b0, b1)
            top = h - ((a0 // 2) * 7 % 3 if ragged else 0)
            v = [16 - top, 16]
            faces = {"up": {"uv": [ux0, uz0, ux1, uz1], "texture": "#end"},
                     "down": {"uv": [ux0, uz0, ux1, uz1], "texture": "#end"}}
            # Bark on the outside of the stump; split wood where the cut opens onto a lower part.
            if b0 == z0: faces["north"] = {"uv": [ux0, v[0], ux1, v[1]], "texture": "#side" if b0 == zlo else "#end"}
            if b1 == z1: faces["south"] = {"uv": [ux0, v[0], ux1, v[1]], "texture": "#side" if b1 == zhi else "#end"}
            if a0 == lo or ragged: faces["west"] = {"uv": [uz0, v[0], uz1, v[1]], "texture": "#side"}
            if a1 == hi or ragged: faces["east"] = {"uv": [uz0, v[0], uz1, v[1]], "texture": "#side"}
            out.append({"from": [a0, 0, b0], "to": [a1, top, b1], "faces": faces})
    return out


def stump_model(side, end, r):
    lo, hi = 8 - r, 8 + r
    width = hi - lo
    front = max(1, round(width * 0.35))
    hinge = max(1, round(width * 0.12))
    z1, z2 = lo + front, lo + front + hinge
    elements = box(lo, hi, lo, z1, FRONT_H, lo, hi, lo, hi) \
        + box(lo, hi, z1, z2, HINGE_H if r >= 2 else BACK_H, lo, hi, lo, hi, ragged=True) \
        + box(lo, hi, z2, hi, BACK_H, lo, hi, lo, hi)
    return {"parent": "minecraft:block/block", "ambientocclusion": True,
            "textures": {"side": side, "end": end, "particle": side}, "elements": elements}


def main():
    en, fr = {}, {}
    tag = []
    for wood, (tns, e, f) in WOODS.items():
        b = f"{wood}_stump"
        side, end = f"{tns}:block/{wood}_log", f"{tns}:block/{wood}_log_top"
        variants = {}
        for r in range(1, 17):
            w(f"assets/{NS}/models/block/stump/{wood}_{r}.json", stump_model(side, end, r))
            for facing, y in FACING_Y.items():
                variants[f"facing={facing},radius={r}"] = {"model": f"{NS}:block/stump/{wood}_{r}"} | ({"y": y} if y else {})
        w(f"assets/{NS}/blockstates/{b}.json", {"variants": variants}, compact=True)

        green = f"{NS}:green_{wood}_log"
        radius = lambda lo_, hi_: {"condition": "minecraft:block_state_property", "block": f"{NS}:{b}",
                                   "properties": {"radius": {"min": str(lo_), "max": str(hi_)}}}
        w(f"data/{NS}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "conditions": [radius(1, 3)], "entries": [{"type": "minecraft:item", "name": "minecraft:stick"}]},
            {"rolls": 1, "conditions": [radius(4, 16)], "entries": [{"type": "minecraft:item", "name": green}]}]})
        tag.append(f"{NS}:{b}")
        en[f"block.{NS}.{b}"] = f"{e} Stump"; fr[f"block.{NS}.{b}"] = f"Souche {f}"

    p = os.path.join(R, "data/minecraft/tags/block/mineable/axe.json"); cur = json.load(open(p))
    for v in tag:
        if v not in cur["values"]: cur["values"].append(v)
    json.dump(cur, open(p, "w"), indent=2)
    for lang, d in (("en_us", en), ("fr_fr", fr)):
        p = os.path.join(R, f"assets/{NS}/lang/{lang}.json")
        cur = json.load(open(p, encoding="utf-8")); cur.update(d)
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(cur, fh, indent=2, ensure_ascii=False)
            fh.write("\n")


if __name__ == "__main__":
    main()
