"""Generates the resources (models, blockstates, loot, recipes, tags, worldgen, lang)
for the species declared in Species.java. Run from the repository root:
    python3 tools/gen_species.py
"""
import json, os

NS = "heartwood"
R = "src/main/resources"
SPECIES = {
    "ash": dict(en="Ash", fr="frêne", fr_de="de frêne", fr_en="en frêne",
                biomes=["minecraft:forest", "minecraft:flower_forest", "minecraft:meadow", "minecraft:plains"],
                count=1, chance_plains=True,
                trunk={"type": "heartwood:organic_trunk_placer", "base_height": 9, "height_rand_a": 5, "height_rand_b": 0, "branches": {"lean": 1, "count": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6}, "length": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 5}, "start": 0.45, "slope": 0.8, "fork": 0.35}},
                foliage={"type": "heartwood:cluster_foliage_placer", "radius": 2, "offset": 0, "height": 4, "irregularity": 0.4},
                size={"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0, "min_clipped_height": 4}),
    "scots_pine": dict(en="Scots Pine", fr="pin sylvestre", fr_de="de pin sylvestre", fr_en="en pin sylvestre",
                biomes=["minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:windswept_forest",
                        "minecraft:windswept_hills", "minecraft:grove"],
                count=2, chance_plains=False,
                trunk={"type": "heartwood:organic_trunk_placer", "base_height": 12, "height_rand_a": 5, "height_rand_b": 0, "branches": {"lean": 2, "count": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 5}, "length": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3}, "start": 0.75, "slope": 0.35, "fork": 0.2}},
                foliage={"type": "heartwood:cluster_foliage_placer", "radius": 2, "offset": 0, "height": 2, "irregularity": 0.35},
                size={"type": "minecraft:two_layers_feature_size", "limit": 2, "lower_size": 0, "upper_size": 2}),
    "chestnut": dict(en="Chestnut", fr="châtaignier", fr_de="de châtaignier", fr_en="en châtaignier",
                biomes=["minecraft:forest", "minecraft:windswept_forest", "minecraft:dark_forest"],
                count=1, chance_plains=False,
                trunk={"type": "heartwood:organic_trunk_placer", "base_height": 6, "height_rand_a": 3, "height_rand_b": 0, "branches": {"lean": 1, "count": {"type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 7}, "length": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6}, "start": 0.4, "slope": 0.5, "fork": 0.45}},
                foliage={"type": "heartwood:cluster_foliage_placer", "radius": 3, "offset": 0, "height": 3, "irregularity": 0.35},
                size={"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0, "min_clipped_height": 4}),
    "maple": dict(en="Maple", fr="érable", fr_de="d'érable", fr_en="en érable",
                biomes=["minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:meadow"],
                count=1, chance_plains=False,
                trunk={"type": "heartwood:organic_trunk_placer", "base_height": 7, "height_rand_a": 3, "height_rand_b": 0, "branches": {"lean": 1, "count": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6}, "length": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}, "start": 0.45, "slope": 0.85, "fork": 0.3}},
                foliage={"type": "heartwood:cluster_foliage_placer", "radius": 3, "offset": 0, "height": 4, "irregularity": 0.3},
                size={"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2}),
}

def w(path, data):
    path = os.path.join(R, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def rl(p): return f"{NS}:{p}"
def blk(p): return f"{NS}:block/{p}"

def self_drop(b):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": rl(b)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

def pillar_states(model, horizontal):
    return {"variants": {"axis=y": {"model": model},
                         "axis=z": {"model": horizontal, "x": 90},
                         "axis=x": {"model": horizontal, "x": 90, "y": 90}}}

def stairs_states(b):
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    v = {}
    for facing, base in rot.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = blk(b) if shape == "straight" else blk(b + ("_inner" if shape.startswith("inner") else "_outer"))
                y = base
                if half == "bottom" and shape in ("inner_left", "outer_left"): y += 270
                if half == "top" and shape in ("inner_right", "outer_right"): y += 90
                y %= 360
                e = {"model": model}
                if half == "top": e["x"] = 180
                if y: e["y"] = y
                if half == "top" or y: e["uvlock"] = True
                v[f"facing={facing},half={half},shape={shape}"] = e
    return {"variants": v}

def gate_states(b):
    rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    v = {}
    for facing, y in rot.items():
        for in_wall in ("false", "true"):
            for open_ in ("false", "true"):
                m = b + ("_wall" if in_wall == "true" else "") + ("_open" if open_ == "true" else "")
                e = {"model": blk(m), "uvlock": True}
                if y: e["y"] = y
                v[f"facing={facing},in_wall={in_wall},open={open_}"] = e
    return {"variants": v}

def fence_states(b):
    parts = [{"apply": {"model": blk(b + "_post")}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        a = {"model": blk(b + "_side"), "uvlock": True}
        if y: a["y"] = y
        parts.append({"when": {side: "true"}, "apply": a})
    return {"multipart": parts}

def door_states(b):
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    v = {}
    for facing, y0 in base.items():
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for open_ in ("false", "true"):
                    m = b + ("_bottom_" if half == "lower" else "_top_") + hinge + ("_open" if open_ == "true" else "")
                    y = (y0 + (0 if open_ == "false" else (90 if hinge == "left" else 270))) % 360
                    e = {"model": blk(m)}
                    if y: e["y"] = y
                    for powered in ("false", "true"):
                        v[f"facing={facing},half={half},hinge={hinge},open={open_},powered={powered}"] = e
    return {"variants": v}

def trapdoor_states(b):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for facing, y in rot.items():
        for half in ("bottom", "top"):
            for open_ in ("false", "true"):
                if open_ == "true":
                    e = {"model": blk(b + "_open")}
                    if half == "top": e["x"] = 180; yy = (y + 180) % 360
                    else: yy = y
                else:
                    e = {"model": blk(b + "_" + half)}; yy = y
                if yy: e["y"] = yy
                for powered in ("false", "true"):
                    for wl in ("false", "true"):
                        v[f"facing={facing},half={half},open={open_},powered={powered},waterlogged={wl}"] = e
    return {"variants": v}

def button_states(b):
    v = {}
    floor = {"east": 90, "north": 0, "south": 180, "west": 270}
    ceil = {"east": 270, "north": 180, "south": 0, "west": 90}
    for face in ("floor", "wall", "ceiling"):
        for facing in floor:
            for powered in ("false", "true"):
                e = {"model": blk(b + ("_pressed" if powered == "true" else ""))}
                y = ceil[facing] if face == "ceiling" else floor[facing]
                if face == "ceiling": e["x"] = 180
                if face == "wall": e["x"] = 90; e["uvlock"] = True
                if y: e["y"] = y
                v[f"face={face},facing={facing},powered={powered}"] = e
    return {"variants": v}

def door_extras(name, s, en, fr, tags, axe_list, item_tex=None):
    """Door, trapdoor, button and pressure plate for a Heartwood species."""
    planks = f"{name}_planks"
    door, trap, button, plate = f"{name}_door", f"{name}_trapdoor", f"{name}_button", f"{name}_pressure_plate"
    T = blk
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        w(f"assets/{NS}/models/block/{door}_{part}.json", {"parent": f"minecraft:block/door_{part}",
            "textures": {"bottom": T(door + "_bottom"), "top": T(door + "_top")}, "render_type": "minecraft:cutout"})
    w(f"assets/{NS}/blockstates/{door}.json", door_states(door))
    w(f"assets/{NS}/models/item/{door}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{door}"}})
    for part in ("bottom", "top", "open"):
        w(f"assets/{NS}/models/block/{trap}_{part}.json", {"parent": f"minecraft:block/template_orientable_trapdoor_{part}",
            "textures": {"texture": T(trap)}, "render_type": "minecraft:cutout"})
    w(f"assets/{NS}/blockstates/{trap}.json", trapdoor_states(trap))
    w(f"assets/{NS}/models/item/{trap}.json", {"parent": blk(trap + "_bottom")})
    for suffix, parent in (("", "button"), ("_pressed", "button_pressed"), ("_inventory", "button_inventory")):
        w(f"assets/{NS}/models/block/{button}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"texture": T(planks)}})
    w(f"assets/{NS}/blockstates/{button}.json", button_states(button))
    w(f"assets/{NS}/models/item/{button}.json", {"parent": blk(button + "_inventory")})
    for suffix, parent in (("", "pressure_plate_up"), ("_down", "pressure_plate_down")):
        w(f"assets/{NS}/models/block/{plate}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"texture": T(planks)}})
    w(f"assets/{NS}/blockstates/{plate}.json", {"variants": {"powered=false": {"model": blk(plate)}, "powered=true": {"model": blk(plate + "_down")}}})
    w(f"assets/{NS}/models/item/{plate}.json", {"parent": blk(plate)})

    w(f"data/{NS}/loot_table/blocks/{door}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": rl(door), "conditions": [{"condition": "minecraft:block_state_property",
            "block": rl(door), "properties": {"half": "lower"}}]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for b in (trap, button, plate):
        w(f"data/{NS}/loot_table/blocks/{b}.json", self_drop(b))

    P = lambda pat, res, n, cat="redstone", group=None: {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pat,
        "key": {"#": {"item": rl(planks)}}, "result": {"id": rl(res), "count": n}} | ({"group": group} if group else {})
    w(f"data/{NS}/recipe/{door}.json", P(["##", "##", "##"], door, 3, group="wooden_door"))
    w(f"data/{NS}/recipe/{trap}.json", P(["###", "###"], trap, 2, group="wooden_trapdoor"))
    w(f"data/{NS}/recipe/{plate}.json", P(["##"], plate, 1, group="wooden_pressure_plate"))
    w(f"data/{NS}/recipe/{button}.json", {"type": "minecraft:crafting_shapeless", "category": "redstone", "group": "wooden_button",
        "ingredients": [{"item": rl(planks)}], "result": {"id": rl(button), "count": 1}})

    tags["wooden_doors"].append(rl(door)); tags["wooden_trapdoors"].append(rl(trap))
    tags["wooden_buttons"].append(rl(button)); tags["wooden_pressure_plates"].append(rl(plate))
    axe_list += [door, trap, button, plate]

    E, F = s["en"], s
    for b, e, f in ((door, f"{E} Door", f"Porte {F['fr_en']}"), (trap, f"{E} Trapdoor", f"Trappe {F['fr_en']}"),
                    (button, f"{E} Button", f"Bouton {F['fr_en']}"), (plate, f"{E} Pressure Plate", f"Plaque de pression {F['fr_en']}")):
        en[f"block.{NS}.{b}"] = e; fr[f"block.{NS}.{b}"] = f

def signs_and_boats(name, s, en, fr, tags, axe_list):
    planks, slog = f"{name}_planks", f"stripped_{name}_log"
    sign, wsign, hang, whang = f"{name}_sign", f"{name}_wall_sign", f"{name}_hanging_sign", f"{name}_wall_hanging_sign"
    boat, cboat = f"{name}_boat", f"{name}_chest_boat"
    for b, particle in ((sign, planks), (hang, slog)):
        w(f"assets/{NS}/models/block/{b}.json", {"textures": {"particle": blk(particle)}})
    for b, m in ((sign, sign), (wsign, sign), (hang, hang), (whang, hang)):
        w(f"assets/{NS}/blockstates/{b}.json", {"variants": {"": {"model": blk(m)}}})
    for it in (sign, hang, boat, cboat):
        w(f"assets/{NS}/models/item/{it}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{it}"}})
    w(f"data/{NS}/loot_table/blocks/{sign}.json", self_drop(sign))
    w(f"data/{NS}/loot_table/blocks/{hang}.json", self_drop(hang))
    w(f"data/{NS}/recipe/{sign}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "group": "wooden_sign",
        "pattern": ["###", "###", " X "], "key": {"#": {"item": rl(planks)}, "X": {"item": "minecraft:stick"}},
        "result": {"id": rl(sign), "count": 3}})
    w(f"data/{NS}/recipe/{hang}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "group": "hanging_sign",
        "pattern": ["X X", "###", "###"], "key": {"#": {"item": rl(slog)}, "X": {"item": "minecraft:chain"}},
        "result": {"id": rl(hang), "count": 6}})
    w(f"data/{NS}/recipe/{boat}.json", {"type": "minecraft:crafting_shaped", "category": "transportation", "group": "boat",
        "pattern": ["# #", "###"], "key": {"#": {"item": rl(planks)}}, "result": {"id": rl(boat), "count": 1}})
    w(f"data/{NS}/recipe/{cboat}.json", {"type": "minecraft:crafting_shapeless", "category": "transportation", "group": "chest_boat",
        "ingredients": [{"item": "minecraft:chest"}, {"item": rl(boat)}], "result": {"id": rl(cboat), "count": 1}})
    tags["b:standing_signs"].append(rl(sign)); tags["b:wall_signs"].append(rl(wsign))
    tags["b:ceiling_hanging_signs"].append(rl(hang)); tags["b:wall_hanging_signs"].append(rl(whang))
    tags["i:signs"].append(rl(sign)); tags["i:hanging_signs"].append(rl(hang))
    tags["i:boats"].append(rl(boat)); tags["i:chest_boats"].append(rl(cboat))
    axe_list += [sign, wsign, hang, whang]
    E, F = s["en"], s
    for b, e, f in ((sign, f"{E} Sign", f"Pancarte {F['fr_en']}"), (wsign, f"{E} Wall Sign", f"Pancarte {F['fr_en']}"),
                    (hang, f"{E} Hanging Sign", f"Pancarte suspendue {F['fr_en']}"),
                    (whang, f"{E} Wall Hanging Sign", f"Pancarte suspendue {F['fr_en']}")):
        en[f"block.{NS}.{b}"] = e; fr[f"block.{NS}.{b}"] = f
    en[f"item.{NS}.{boat}"] = f"{E} Boat"; fr[f"item.{NS}.{boat}"] = f"Bateau {F['fr_en']}"
    en[f"item.{NS}.{cboat}"] = f"{E} Boat with Chest"; fr[f"item.{NS}.{cboat}"] = f"Bateau {F['fr_en']} avec coffre"

def main():
    en, fr = {}, {}
    tags = {k: [] for k in ("logs_that_burn", "planks", "leaves", "saplings", "wooden_stairs", "wooden_slabs",
                           "wooden_fences", "fence_gates",
                           "wooden_doors", "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates")}
    axe_list = []
    for k in ("b:standing_signs", "b:wall_signs", "b:ceiling_hanging_signs", "b:wall_hanging_signs",
              "i:signs", "i:hanging_signs", "i:boats", "i:chest_boats"):
        tags[k] = []
    for name, s in SPECIES.items():
        log, wood, slog, swood = f"{name}_log", f"{name}_wood", f"stripped_{name}_log", f"stripped_{name}_wood"
        planks, stairs, slab, fence, gate = f"{name}_planks", f"{name}_stairs", f"{name}_slab", f"{name}_fence", f"{name}_fence_gate"
        leaves, sapling = f"{name}_leaves", f"{name}_sapling"
        T = lambda t: blk(t)

        # Models
        w(f"assets/{NS}/models/block/{log}.json", {"parent": "minecraft:block/cube_column", "textures": {"end": T(log + "_top"), "side": T(log)}})
        w(f"assets/{NS}/models/block/{log}_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": T(log + "_top"), "side": T(log)}})
        w(f"assets/{NS}/models/block/{wood}.json", {"parent": "minecraft:block/cube_column", "textures": {"end": T(log), "side": T(log)}})
        w(f"assets/{NS}/models/block/{slog}.json", {"parent": "minecraft:block/cube_column", "textures": {"end": T(slog + "_top"), "side": T(slog)}})
        w(f"assets/{NS}/models/block/{slog}_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": T(slog + "_top"), "side": T(slog)}})
        w(f"assets/{NS}/models/block/{swood}.json", {"parent": "minecraft:block/cube_column", "textures": {"end": T(slog), "side": T(slog)}})
        w(f"assets/{NS}/models/block/{planks}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": T(planks)}})
        tex = {"bottom": T(planks), "side": T(planks), "top": T(planks)}
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            w(f"assets/{NS}/models/block/{stairs}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": tex})
        w(f"assets/{NS}/models/block/{slab}.json", {"parent": "minecraft:block/slab", "textures": tex})
        w(f"assets/{NS}/models/block/{slab}_top.json", {"parent": "minecraft:block/slab_top", "textures": tex})
        for suffix, parent in (("_post", "fence_post"), ("_side", "fence_side"), ("_inventory", "fence_inventory")):
            w(f"assets/{NS}/models/block/{fence}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"texture": T(planks)}})
        for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"),
                               ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
            w(f"assets/{NS}/models/block/{gate}{suffix}.json", {"parent": f"minecraft:block/{parent}", "textures": {"texture": T(planks)}})
        w(f"assets/{NS}/models/block/{leaves}.json", {"parent": "minecraft:block/leaves", "textures": {"all": T(leaves)}, "render_type": "minecraft:cutout_mipped"})
        w(f"assets/{NS}/models/block/{sapling}.json", {"parent": "minecraft:block/cross", "textures": {"cross": T(sapling)}, "render_type": "minecraft:cutout"})

        # Blockstates
        w(f"assets/{NS}/blockstates/{log}.json", pillar_states(blk(log), blk(log + "_horizontal")))
        w(f"assets/{NS}/blockstates/{slog}.json", pillar_states(blk(slog), blk(slog + "_horizontal")))
        w(f"assets/{NS}/blockstates/{wood}.json", pillar_states(blk(wood), blk(wood)))
        w(f"assets/{NS}/blockstates/{swood}.json", pillar_states(blk(swood), blk(swood)))
        for b in (planks, leaves, sapling):
            w(f"assets/{NS}/blockstates/{b}.json", {"variants": {"": {"model": blk(b)}}})
        w(f"assets/{NS}/blockstates/{stairs}.json", stairs_states(stairs))
        w(f"assets/{NS}/blockstates/{slab}.json", {"variants": {"type=bottom": {"model": blk(slab)},
            "type=top": {"model": blk(slab + "_top")}, "type=double": {"model": blk(planks)}}})
        w(f"assets/{NS}/blockstates/{fence}.json", fence_states(fence))
        w(f"assets/{NS}/blockstates/{gate}.json", gate_states(gate))

        # Item models
        for b in (log, wood, slog, swood, planks, stairs, slab, gate, leaves):
            w(f"assets/{NS}/models/item/{b}.json", {"parent": blk(b)})
        w(f"assets/{NS}/models/item/{fence}.json", {"parent": blk(fence + "_inventory")})
        w(f"assets/{NS}/models/item/{sapling}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": T(sapling)}})

        # Heartwood felling blocks for this species
        w(f"assets/{NS}/models/block/notched_{log}.json", {"parent": f"{NS}:block/notched_log_template",
            "textures": {"side": T(log), "end": T(log + "_top"), "cut": T(slog + "_top"), "inner": T(slog)}})
        w(f"assets/{NS}/blockstates/notched_{log}.json", {"variants": {f"facing={f}": ({"model": blk('notched_' + log)} | ({"y": r} if r else {}))
            for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        w(f"assets/{NS}/blockstates/felled_{log}.json", pillar_states(blk(log), blk(log + "_horizontal")))
        w(f"assets/{NS}/models/block/green_{log}.json", {"parent": f"{NS}:block/tinted_column", "textures": {"side": T(log), "end": T(log + "_top")}})
        w(f"assets/{NS}/blockstates/green_{log}.json", {"variants": {"axis=x": {"model": blk('green_' + log), "x": 90, "y": 90},
            "axis=y": {"model": blk('green_' + log)}, "axis=z": {"model": blk('green_' + log), "x": 90}}})
        w(f"assets/{NS}/models/item/green_{log}.json", {"parent": blk('green_' + log)})
        w(f"assets/{NS}/models/block/{name}_branches.json", {"parent": blk(leaves), "render_type": "minecraft:cutout_mipped"})
        w(f"assets/{NS}/blockstates/{name}_branches.json", {"variants": {"": {"model": blk(name + '_branches')}}})

        # Loot
        for b in (log, wood, slog, swood, planks, stairs, fence, gate, sapling, f"green_{log}"):
            w(f"data/{NS}/loot_table/blocks/{b}.json", self_drop(b))
        for b in (f"notched_{log}", f"felled_{log}"):
            d = self_drop(f"green_{log}"); w(f"data/{NS}/loot_table/blocks/{b}.json", d)
        w(f"data/{NS}/loot_table/blocks/{slab}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": rl(slab), "functions": [{"function": "minecraft:set_count",
                "count": 2, "add": False, "conditions": [{"condition": "minecraft:block_state_property", "block": rl(slab),
                "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]}]}]})
        shears_or_silk = {"condition": "minecraft:any_of", "terms": [
            {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
            {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
        w(f"data/{NS}/loot_table/blocks/{leaves}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": rl(leaves), "conditions": [shears_or_silk]},
                {"type": "minecraft:item", "name": rl(sapling), "conditions": [{"condition": "minecraft:survives_explosion"},
                    {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}]}]},
            {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:inverted", "term": shears_or_silk}],
             "entries": [{"type": "minecraft:item", "name": "minecraft:stick", "conditions": [{"condition": "minecraft:table_bonus",
                "enchantment": "minecraft:fortune", "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}],
                "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False},
                              {"function": "minecraft:explosion_decay"}]}]}]})
        axe = {"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:axes"}}
        w(f"data/{NS}/loot_table/blocks/{name}_branches.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [axe],
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}]}]}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": rl(sapling)}],
             "conditions": [axe, {"condition": "minecraft:random_chance", "chance": 0.08}]}]})

        # Recipes
        logs_tag = f"#{NS}:{name}_logs"
        w(f"data/{NS}/tags/item/{name}_logs.json", {"values": [rl(log), rl(wood), rl(slog), rl(swood)]})
        w(f"data/{NS}/tags/block/{name}_logs.json", {"values": [rl(log), rl(wood), rl(slog), rl(swood)]})
        P = lambda pat, key, res, n: {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pat, "key": key,
                                       "result": {"id": rl(res), "count": n}}
        w(f"data/{NS}/recipe/{planks}.json", {"type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
            "ingredients": [{"tag": f"{NS}:{name}_logs"}], "result": {"id": rl(planks), "count": 4}})
        w(f"data/{NS}/recipe/{wood}.json", P(["##", "##"], {"#": {"item": rl(log)}}, wood, 3))
        w(f"data/{NS}/recipe/{swood}.json", P(["##", "##"], {"#": {"item": rl(slog)}}, swood, 3))
        w(f"data/{NS}/recipe/{stairs}.json", P(["#  ", "## ", "###"], {"#": {"item": rl(planks)}}, stairs, 4))
        w(f"data/{NS}/recipe/{slab}.json", P(["###"], {"#": {"item": rl(planks)}}, slab, 6))
        w(f"data/{NS}/recipe/{fence}.json", P(["W#W", "W#W"], {"W": {"item": rl(planks)}, "#": {"item": "minecraft:stick"}}, fence, 3))
        w(f"data/{NS}/recipe/{gate}.json", P(["#W#", "#W#"], {"W": {"item": rl(planks)}, "#": {"item": "minecraft:stick"}}, gate, 1))

        tags["logs_that_burn"].append(f"#{NS}:{name}_logs"); tags["planks"].append(rl(planks)); tags["leaves"].append(rl(leaves))
        tags["saplings"].append(rl(sapling)); tags["wooden_stairs"].append(rl(stairs)); tags["wooden_slabs"].append(rl(slab))
        tags["wooden_fences"].append(rl(fence)); tags["fence_gates"].append(rl(gate))

        # Worldgen
        w(f"data/{NS}/worldgen/configured_feature/{name}.json", {"type": "minecraft:tree", "config": {
            "ignore_vines": True, "force_dirt": False, "minimum_size": s["size"],
            "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
            "trunk_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": rl(log), "Properties": {"axis": "y"}}},
            "foliage_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": rl(leaves),
                "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}},
            "trunk_placer": s["trunk"], "foliage_placer": s["foliage"], "decorators": []}})
        w(f"data/{NS}/worldgen/placed_feature/{name}.json", {"feature": rl(name), "placement": [
            {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
                {"weight": 9, "data": 0}, {"weight": 1, "data": s["count"] * 2}]}} if s["chance_plains"] else
            {"type": "minecraft:count", "count": s["count"]},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
            {"type": "minecraft:biome"},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                "state": {"Name": rl(sapling), "Properties": {"stage": "0"}}}}]})
        w(f"data/{NS}/neoforge/biome_modifier/{name}_trees.json", {"type": "neoforge:add_features",
            "biomes": s["biomes"], "features": rl(name), "step": "vegetal_decoration"})

        # Lang
        E, F = s["en"], s
        for b, e, f in ((log, f"{E} Log", f"Bûche {F['fr_de']}"), (wood, f"{E} Wood", f"Bois {F['fr_de']}"),
                        (slog, f"Stripped {E} Log", f"Bûche {F['fr_de']} écorcée"), (swood, f"Stripped {E} Wood", f"Bois {F['fr_de']} écorcé"),
                        (planks, f"{E} Planks", f"Planches {F['fr_de']}"), (stairs, f"{E} Stairs", f"Escalier {F['fr_en']}"),
                        (slab, f"{E} Slab", f"Dalle {F['fr_en']}"), (fence, f"{E} Fence", f"Barrière {F['fr_en']}"),
                        (gate, f"{E} Fence Gate", f"Portillon {F['fr_en']}"), (leaves, f"{E} Leaves", f"Feuilles {F['fr_de']}"),
                        (sapling, f"{E} Sapling", f"Pousse {F['fr_de']}"),
                        (f"notched_{log}", f"Notched {E} Log", f"Bûche {F['fr_de']} entaillée"),
                        (f"felled_{log}", f"Felled {E} Log", f"Tronc abattu {F['fr_de']}"),
                        (f"green_{log}", f"Green {E} Log", f"Bûche verte {F['fr_de']}"),
                        (f"{name}_branches", f"{E} Branches", f"Branches {F['fr_de']}")):
            en[f"block.{NS}.{b}"] = e; fr[f"block.{NS}.{b}"] = f
        door_extras(name, s, en, fr, tags, axe_list)
        signs_and_boats(name, s, en, fr, tags, axe_list)
        en[f"item.{NS}.green_{log}"] = en[f"block.{NS}.green_{log}"]; fr[f"item.{NS}.green_{log}"] = fr[f"block.{NS}.green_{log}"]

    for t, values in tags.items():
        if ":" in t:
            kind, tag = t.split(":")
            w(f"data/minecraft/tags/{'block' if kind == 'b' else 'item'}/{tag}.json", {"replace": False, "values": values})
            continue
        for kind in ("block", "item"):
            if t == "logs_that_burn" and kind == "block":
                values_b = [v for v in values]
            w(f"data/minecraft/tags/{kind}/{t}.json", {"replace": False, "values": values})
    w(f"data/minecraft/tags/block/overworld_natural_logs.json", {"replace": False, "values": [rl(f"{n}_log") for n in SPECIES]})
    for lang, d in (("en_us", en), ("fr_fr", fr)):
        p = os.path.join(R, f"assets/{NS}/lang/{lang}.json")
        cur = json.load(open(p, encoding="utf-8")); cur.update(d)
        json.dump(cur, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    # Mineable tags
    p = os.path.join(R, "data/minecraft/tags/block/mineable/axe.json"); cur = json.load(open(p))
    for n in SPECIES:
        for b in (f"notched_{n}_log", f"felled_{n}_log", f"green_{n}_log", f"{n}_branches"):
            if rl(b) not in cur["values"]: cur["values"].append(rl(b))
    for b in axe_list:
        if rl(b) not in cur["values"]: cur["values"].append(rl(b))
    json.dump(cur, open(p, "w"), indent=2)

if __name__ == "__main__":
    main()
