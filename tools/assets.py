# -*- coding: utf-8 -*-
"""Erzeugt Blockstates, Modelle, Loot-Tables und Rezepte."""
import json
import os

ROOT = r"D:\Projekte\FactoryNetwork\src\main\resources"
MOD = "factorynetwork"


def write(relative, data):
    path = os.path.join(ROOT, relative.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("  " + relative)


CABLE_COLOURS = [
    "none", "white", "orange", "magenta", "light_blue", "yellow", "lime",
    "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
    "red", "black",
]


# Wo die Stränge im Block liegen, je nach Anzahl.
#
# Allein ist ein Strang sechs Pixel dick — so kennt man Rohre, und ein
# einzelnes Kabel soll aussehen wie bisher. Ab zwei wird geteilt und alle
# werden vier Pixel dick; mehr passt nicht in sechzehn Pixel, ohne dass sie
# sich berühren.
STRAND_LAYOUTS = {
    1: [(5, 5)],
    2: [(3, 6), (9, 6)],
    3: [(2, 6), (6, 2), (10, 6)],
    4: [(3, 3), (9, 3), (3, 9), (9, 9)],
}


def strand_size(count):
    return 6 if count == 1 else 4


def cable_models():
    """Kern und Arm für jede Anzahl von Strängen und jede Position.

    Die Farbe steckt nicht im Modell: Jeder Strang bekommt einen eigenen
    Tintindex, und der Client färbt ihn aus der BlockEntity ein. Sonst
    bräuchte jede Farbkombination ein eigenes Modell.
    """
    textures = {"cable": texture("cable"), "particle": texture("cable")}
    faces = ("north", "south", "east", "west", "up", "down")

    for count, positions in STRAND_LAYOUTS.items():
        size = strand_size(count)
        for index, (x, y) in enumerate(positions):
            # Feste UV-Angabe. Ohne sie schneidet Minecraft die Textur nach
            # Blockkoordinaten zu, und ein Strang bei x=9 bekäme einen anderen
            # Ausschnitt als einer bei x=3 — dasselbe Rohr sähe je nach Lage
            # im Block anders aus.
            face = {"texture": "#cable", "tintindex": index,
                    "uv": [0, 0, size, size]}
            long_face = {"texture": "#cable", "tintindex": index,
                         "uv": [0, 0, size, 16]}

            # Der Kern sitzt in der Tiefe immer mittig — verteilt wird nur
            # in der Fläche, die man beim Blick auf das Kabel sieht.
            depth = (16 - size) // 2

            write(A + "/models/block/cable_core_%d_%d.json" % (count, index), {
                "textures": textures,
                "elements": [{
                    "from": [x, y, depth],
                    "to": [x + size, y + size, depth + size],
                    "faces": {f: dict(face) for f in faces},
                }],
            })

            # Arm nach Norden; die Blockstate dreht ihn in die anderen
            # Richtungen. Er reicht von der Blockkante bis an den Kern.
            write(A + "/models/block/cable_arm_%d_%d.json" % (count, index), {
                "textures": textures,
                "elements": [{
                    "from": [x, y, 0],
                    "to": [x + size, y + size, depth],
                    "faces": {
                        "north": dict(face, cullface="north"),
                        "east": {"texture": "#cable", "tintindex": index,
                                 "uv": [0, 0, depth, size]},
                        "west": {"texture": "#cable", "tintindex": index,
                                 "uv": [0, 0, depth, size]},
                        "up": {"texture": "#cable", "tintindex": index,
                               "uv": [0, 0, size, depth]},
                        "down": {"texture": "#cable", "tintindex": index,
                                 "uv": [0, 0, size, depth]},
                    },
                }],
            })

    # In der Hand ein durchgehendes Rohr, sonst sähe man nur einen Würfel.
    write(A + "/models/block/cable_inventory.json", {
        "textures": textures,
        "elements": [{
            "from": [5, 5, 0],
            "to": [11, 11, 16],
            "faces": {
                "north": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 6, 6]},
                "south": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 6, 6]},
                "east": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 16, 6]},
                "west": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 16, 6]},
                "up": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 6, 16]},
                "down": {"texture": "#cable", "tintindex": 0, "uv": [0, 0, 6, 16]},
            },
        }],
    })
    for colour in CABLE_COLOURS:
        name = "cable" if colour == "none" else colour + "_cable"
        write(A + "/models/item/%s.json" % name, {"parent": block("cable_inventory")})


def block(name):
    return MOD + ":block/" + name


def texture(name):
    return MOD + ":block/" + name


A = "assets/" + MOD
D = "data/" + MOD


# ---- Blockstates ---------------------------------------------------------

def blockstates():
    write(A + "/blockstates/controller.json",
          {"variants": {"": {"model": block("controller")}}})

    # Kabel: Die Anzahl der Stränge wählt die Kerne, die Verbindungen die
    # Arme. Die Farben kommen nicht aus der Blockstate — sie werden zur
    # Laufzeit eingefärbt.
    rotations = {
        "north": {},
        "south": {"y": 180},
        "east": {"y": 90},
        "west": {"y": 270},
        "up": {"x": 270},
        "down": {"x": 90},
    }
    parts = []
    for count, positions in STRAND_LAYOUTS.items():
        for index in range(len(positions)):
            parts.append({
                "when": {"strands": str(count)},
                "apply": {"model": block("cable_core_%d_%d" % (count, index))},
            })
            for direction, rotation in rotations.items():
                apply = {"model": block("cable_arm_%d_%d" % (count, index))}
                apply.update(rotation)
                parts.append({
                    "when": {"strands": str(count), direction: "true"},
                    "apply": apply,
                })
    write(A + "/blockstates/cable.json", {"multipart": parts})

    # Connector zeigt in sechs Richtungen.
    facing = {
        "north": {},
        "south": {"y": 180},
        "east": {"y": 90},
        "west": {"y": 270},
        "up": {"x": 270},
        "down": {"x": 90},
    }
    variants = {}
    for direction, rotation in facing.items():
        entry = {"model": block("connector")}
        entry.update(rotation)
        variants["facing=" + direction] = entry
    write(A + "/blockstates/connector.json", {"variants": variants})

    # Terminal steht immer aufrecht.
    horizontal = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}}
    variants = {}
    for direction, rotation in horizontal.items():
        entry = {"model": block("terminal")}
        entry.update(rotation)
        variants["facing=" + direction] = entry
    write(A + "/blockstates/terminal.json", {"variants": variants})


# ---- Modelle -------------------------------------------------------------

def models():
    write(A + "/models/block/controller.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": texture("controller_top"),
            "bottom": texture("controller_top"),
            "side": texture("controller_side"),
        },
    })

    cable_models()

    write(A + "/models/block/connector.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("connector_side"),
            "top": texture("machine_top"),
            "front": texture("connector_front"),
            "back": texture("connector_back"),
            "side": texture("connector_side"),
        },
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#top", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#front", "cullface": "north"},
                "south": {"texture": "#back", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    })

    write(A + "/models/block/terminal.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": texture("machine_top"),
            "front": texture("terminal_front"),
            "side": texture("terminal_side"),
        },
    })

    for name in ("controller", "connector", "terminal"):
        write(A + "/models/item/" + name + ".json", {"parent": block(name)})
    write(A + "/models/item/label_gun.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/label_gun"},
    })


# ---- Loot und Rezepte ----------------------------------------------------

def loot_and_recipes():
    for name in ("controller", "connector", "terminal"):
        write(D + "/loot_table/blocks/" + name + ".json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": MOD + ":" + name}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    # Rezepte: bewusst günstig. Wer die Mod spielt, will programmieren,
    # nicht erst eine Materialkette abarbeiten.
    write(D + "/recipe/controller.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "IRI", "III"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": MOD + ":controller", "count": 1},
    })

    write(D + "/recipe/cable.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "RRR", "III"],
        "key": {
            "I": {"item": "minecraft:iron_nugget"},
            "R": {"item": "minecraft:redstone"},
        },
        "result": {"id": MOD + ":cable", "count": 8},
    })

    write(D + "/recipe/connector.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" I ", "IRI", " I "],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "R": {"item": "minecraft:redstone"},
        },
        "result": {"id": MOD + ":connector", "count": 2},
    })

    write(D + "/recipe/terminal.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IGI", "IRI", "III"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "G": {"item": "minecraft:glass_pane"},
            "R": {"item": "minecraft:redstone"},
        },
        "result": {"id": MOD + ":terminal", "count": 1},
    })

    write(D + "/recipe/label_gun.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["IN", "S ", "S "],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "N": {"item": "minecraft:gold_nugget"},
            "S": {"item": "minecraft:stick"},
        },
        "result": {"id": MOD + ":label_gun", "count": 1},
    })

    # Das Kabel wirft seine Stränge selbst aus — die Tabelle sieht die
    # BlockEntity nicht und wüsste nicht, welche Farben drinstecken.
    write(D + "/loot_table/blocks/cable.json", {"type": "minecraft:block", "pools": []})

    # Färben: ein Farbstoff auf ein beliebiges Kabel. Über das Tag statt über
    # siebzehn Gegenstände einzeln — sonst wären es zweihundertzweiundsiebzig
    # Rezepte statt sechzehn.
    for colour in CABLE_COLOURS[1:]:
        write(D + "/recipe/%s_cable.json" % colour, {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": "factorynetwork_cable",
            "ingredients": [{"tag": "c:cables"}, {"item": "minecraft:%s_dye" % colour}],
            "result": {"id": MOD + ":%s_cable" % colour, "count": 1},
        })

    # Entfärben mit einem Wassereimer, wie bei Applied Energistics. Ohne das
    # wäre ein gefärbtes Kabel eine Sackgasse.
    write(D + "/recipe/cable_uncolour.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "group": "factorynetwork_cable",
        "ingredients": [{"tag": "c:cables"}, {"item": "minecraft:water_bucket"}],
        "result": {"id": MOD + ":cable", "count": 1},
    })

    # Alle Kabel unter einem Tag, damit die Rezepte jede Farbe annehmen.
    write("data/c/tags/item/cables.json", {
        "values": [MOD + ":cable"] + [MOD + ":%s_cable" % c for c in CABLE_COLOURS[1:]],
    })

    # Spitzhacke reicht zum Abbauen.
    write(D + "/tags/block/mineable/pickaxe.json", {
        "values": [MOD + ":controller", MOD + ":cable", MOD + ":connector", MOD + ":terminal"],
    })


if __name__ == "__main__":
    print("Blockstates:")
    blockstates()
    print("Modelle:")
    models()
    print("Loot und Rezepte:")
    loot_and_recipes()
