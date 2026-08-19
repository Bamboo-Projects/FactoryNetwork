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

    # Kabel: ein Kern plus je ein Arm pro Verbindung.
    parts = [{"apply": {"model": block("cable_core")}}]
    rotations = {
        "north": {},
        "south": {"y": 180},
        "east": {"y": 90},
        "west": {"y": 270},
        "up": {"x": 270},
        "down": {"x": 90},
    }
    for direction, rotation in rotations.items():
        apply = {"model": block("cable_arm")}
        apply.update(rotation)
        parts.append({"when": {direction: "true"}, "apply": apply})
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

    # Kern des Kabels: ein Würfel von 6 Pixeln Kantenlänge in der Mitte.
    write(A + "/models/block/cable_core.json", {
        "textures": {"cable": texture("cable"), "particle": texture("cable")},
        "elements": [{
            "from": [5, 5, 5],
            "to": [11, 11, 11],
            "faces": {
                face: {"texture": "#cable", "cullface": None} for face in
                ("north", "south", "east", "west", "up", "down")
            },
        }],
    })

    # Arm nach Norden; die Blockstate dreht ihn in die anderen Richtungen.
    write(A + "/models/block/cable_arm.json", {
        "textures": {"cable": texture("cable"), "particle": texture("cable")},
        "elements": [{
            "from": [5, 5, 0],
            "to": [11, 11, 5],
            "faces": {
                "north": {"texture": "#cable", "cullface": "north"},
                "east": {"texture": "#cable"},
                "west": {"texture": "#cable"},
                "up": {"texture": "#cable"},
                "down": {"texture": "#cable"},
            },
        }],
    })

    # Zum Halten in der Hand braucht das Kabel ein vollständiges Modell.
    write(A + "/models/block/cable_inventory.json", {
        "textures": {"cable": texture("cable"), "particle": texture("cable")},
        "elements": [{
            "from": [5, 5, 0],
            "to": [11, 11, 16],
            "faces": {
                face: {"texture": "#cable"} for face in
                ("north", "south", "east", "west", "up", "down")
            },
        }],
    })

    write(A + "/models/block/connector.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": texture("machine_top"),
            "front": texture("connector_front"),
            "side": texture("connector_side"),
        },
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
    write(A + "/models/item/cable.json", {"parent": block("cable_inventory")})
    write(A + "/models/item/label_gun.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/label_gun"},
    })


# ---- Loot und Rezepte ----------------------------------------------------

def loot_and_recipes():
    for name in ("controller", "cable", "connector", "terminal"):
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
