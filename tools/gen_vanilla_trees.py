"""Gives vanilla trees organic shapes by overriding their configured features.

Usage: python3 tools/gen_vanilla_trees.py <dir with the vanilla 1.21.1 configured_feature JSON>
Each listed feature keeps its vanilla blocks, size and decorators (bees, vines, cocoa);
only the trunk and foliage placers are replaced. Giant 2x2 trees (mega spruce/pine/jungle),
azalea, mangrove and the jungle bush are left as they are.
"""
import json, os, sys

OUT = "src/main/resources/data/minecraft/worldgen/configured_feature"

def U(a, b): return {"type": "minecraft:uniform", "min_inclusive": a, "max_inclusive": b}

def shape(height, rand, lean, count, length, start, slope, fork, radius, fheight, irr, taper=0.0):
    trunk = {"type": "heartwood:organic_trunk_placer", "base_height": height, "height_rand_a": rand, "height_rand_b": 0,
             "branches": {"lean": lean, "count": count, "length": length, "start": start, "slope": slope,
                          "fork": fork, "taper": taper}}
    foliage = {"type": "heartwood:cluster_foliage_placer", "radius": radius, "offset": 0, "height": fheight, "irregularity": irr}
    return trunk, foliage

SHAPES = {
    "oak":        shape(5, 2, 1, U(3, 5), U(2, 3), 0.5, 0.7, 0.3, 2, 3, 0.35),
    "fancy_oak":  shape(7, 3, 1, U(5, 7), U(3, 5), 0.4, 0.6, 0.45, 2, 3, 0.35),
    "birch":      shape(6, 2, 1, U(2, 4), U(1, 2), 0.6, 1.0, 0.2, 2, 3, 0.3),
    "super_birch": shape(9, 4, 1, U(3, 5), U(1, 3), 0.6, 1.0, 0.2, 2, 3, 0.3),
    "spruce":     None,  # keeps its vanilla cone; only the trunk leans (see main)
    "pine":       shape(9, 3, 2, U(3, 5), U(2, 3), 0.7, 0.35, 0.2, 2, 2, 0.35),
    "beech":      shape(8, 4, 1, U(5, 7), U(3, 5), 0.45, 0.55, 0.4, 3, 3, 0.3),   # dark oak
    "teak":       shape(10, 4, 0, U(3, 5), U(2, 4), 0.7, 0.5, 0.3, 2, 3, 0.35),   # jungle
    "acacia":     shape(5, 2, 2, U(2, 3), U(3, 4), 0.6, 0.8, 0.5, 3, 2, 0.3),
    "cherry":     shape(5, 2, 1, U(3, 4), U(3, 4), 0.5, 0.7, 0.4, 3, 3, 0.3),
}

FEATURES = {
    "oak": "oak", "oak_bees_0002": "oak", "oak_bees_002": "oak", "oak_bees_005": "oak", "swamp_oak": "oak",
    "fancy_oak": "fancy_oak", "fancy_oak_bees": "fancy_oak", "fancy_oak_bees_0002": "fancy_oak",
    "fancy_oak_bees_002": "fancy_oak", "fancy_oak_bees_005": "fancy_oak",
    "birch": "birch", "birch_bees_0002": "birch", "birch_bees_002": "birch", "birch_bees_005": "birch",
    "super_birch_bees": "super_birch", "super_birch_bees_0002": "super_birch",
    "spruce": "spruce", "pine": "pine",
    "dark_oak": "beech",
    "jungle_tree": "teak", "jungle_tree_no_vine": "teak",
    "acacia": "acacia",
    "cherry": "cherry", "cherry_bees_005": "cherry",
}

def main(src):
    os.makedirs(OUT, exist_ok=True)
    for name, kind in FEATURES.items():
        data = json.load(open(os.path.join(src, name + ".json")))
        cfg = data["config"]
        if SHAPES[kind] is None:
            t = cfg["trunk_placer"]
            cfg["trunk_placer"] = {"type": "heartwood:organic_trunk_placer", "base_height": t["base_height"],
                "height_rand_a": t["height_rand_a"], "height_rand_b": t["height_rand_b"],
                "branches": {"lean": 1, "count": 0, "length": 1, "start": 1.0, "slope": 0.0, "fork": 0.0}}
        else:
            cfg["trunk_placer"], cfg["foliage_placer"] = SHAPES[kind]
        if SHAPES[kind] is not None:
            cfg["minimum_size"] = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}
        # Leaves and logs must stay persistent=false and axis=y in the providers, as in vanilla.
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(data, f, indent=2)

if __name__ == "__main__":
    main(sys.argv[1])
