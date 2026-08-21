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
# Die beiden Kabelstärken in Blockpixeln — dieselben Werte wie bei AE2 fuer
# ummantelte und dichte Kabel, und dieselben wie in CableLayout.java.
THIN = 6
DENSE = 10


def cable_models():
    """Kern und Arm eines Kabels.

    <b>Die UV-Angaben tragen die ganze Arbeit.</b> Minecraft legt sonst auf
    jede Fläche denselben Ausschnitt, und die Maserung liefe je nach Lage quer
    statt längs — genau der Fehler, den man im Spiel als Wellblech sah.

    Die Textur ist ein Kreuz: Ihr Randbereich ist quer zur Achse gleichmäßig
    und liefert die Längsansicht, ihre Mitte trägt den Querschnitt. Deshalb
    nimmt eine Längsfläche den Rand in Laufrichtung und die Mitte quer dazu —
    dieselbe Aufteilung, die Applied Energistics in Code macht.

    Ein Arm wird nur nach Norden gebaut; die Blockstate dreht ihn. Die Drehung
    nimmt die Textur mit, also stimmt die Ausrichtung in allen sechs
    Richtungen.
    """
    for size in (THIN, DENSE):
        lo = (16 - size) // 2
        hi = lo + size
        name = "cable" if size == THIN else "dense_cable"
        textures = {"cable": texture("cable"), "particle": texture("cable")}

        # Querschnitt: die Mitte der Textur, in beiden Achsen das Profil.
        quer = [lo, lo, hi, hi]

        write(A + "/models/block/%s_core.json" % name, {
            "textures": textures,
            "elements": [{
                "from": [lo, lo, lo],
                "to": [hi, hi, hi],
                "faces": {f: {"texture": "#cable", "tintindex": 0, "uv": quer}
                          for f in ("north", "south", "east", "west", "up", "down")},
            }],
        })

        write(A + "/models/block/%s_arm.json" % name, {
            "textures": textures,
            "elements": [{
                "from": [lo, lo, 0],
                "to": [hi, hi, lo],
                "faces": {
                    # Stirnfläche an der Blockkante: der Querschnitt.
                    "north": {"texture": "#cable", "tintindex": 0, "uv": quer,
                              "cullface": "north"},
                    # Seiten: quer liegt in der Höhe, längs in der Tiefe.
                    "east": {"texture": "#cable", "tintindex": 0, "uv": [0, lo, lo, hi]},
                    "west": {"texture": "#cable", "tintindex": 0, "uv": [0, lo, lo, hi]},
                    # Oben und unten: quer liegt in der Breite.
                    "up": {"texture": "#cable", "tintindex": 0, "uv": [lo, 0, hi, lo]},
                    "down": {"texture": "#cable", "tintindex": 0, "uv": [lo, 0, hi, lo]},
                },
            }],
        })

        # In der Hand ein durchgehendes Rohr, sonst sähe man nur einen Würfel.
        write(A + "/models/block/%s_inventory.json" % name, {
            "textures": textures,
            "elements": [{
                "from": [lo, lo, 0],
                "to": [hi, hi, 16],
                "faces": {
                    "north": {"texture": "#cable", "tintindex": 0, "uv": quer},
                    "south": {"texture": "#cable", "tintindex": 0, "uv": quer},
                    "east": {"texture": "#cable", "tintindex": 0, "uv": [0, lo, 16, hi]},
                    "west": {"texture": "#cable", "tintindex": 0, "uv": [0, lo, 16, hi]},
                    "up": {"texture": "#cable", "tintindex": 0, "uv": [lo, 0, hi, 16]},
                    "down": {"texture": "#cable", "tintindex": 0, "uv": [lo, 0, hi, 16]},
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

    # Kabel: ein Kern, dazu ein Arm je Verbindung. Die Farbe kommt nicht aus
    # der Blockstate — sie wird zur Laufzeit eingefärbt, sonst brauchte jede
    # der siebzehn Farben einen eigenen Satz Modelle.
    rotations = {
        "north": {},
        "south": {"y": 180},
        "east": {"y": 90},
        "west": {"y": 270},
        "up": {"x": 270},
        "down": {"x": 90},
    }
    for size, name in ((THIN, "cable"), (DENSE, "dense_cable")):
        parts = [{"apply": {"model": block(name + "_core")}}]
        for direction, rotation in rotations.items():
            apply = {"model": block(name + "_arm")}
            apply.update(rotation)
            parts.append({"when": {direction: "true"}, "apply": apply})
        write(A + "/blockstates/%s.json" % name, {"multipart": parts})

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

    # Display hängt flach an der Wand, in vier Richtungen.
    variants = {}
    for direction, rotation in {"north": {}, "south": {"y": 180},
                                "east": {"y": 90}, "west": {"y": 270}}.items():
        entry = {"model": block("display")}
        entry.update(rotation)
        variants["facing=" + direction] = entry
    write(A + "/blockstates/display.json", {"variants": variants})

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

    # Zwei Pixel tief an der Wand. Ein eigenes Modell statt orientable,
    # weil es kein Würfel ist.
    write(A + "/models/block/display.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("display_side"),
            "front": texture("display_front"),
            "side": texture("display_side"),
        },
        "elements": [{
            "from": [0, 0, 14],
            "to": [16, 16, 16],
            "faces": {
                "north": {"texture": "#front"},
                "south": {"texture": "#side", "cullface": "south"},
                "east": {"texture": "#side"},
                "west": {"texture": "#side"},
                "up": {"texture": "#side"},
                "down": {"texture": "#side"},
            },
        }],
    })
    write(A + "/models/item/display.json", {"parent": block("display")})

    write(A + "/models/block/terminal.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": texture("machine_top"),
            "front": texture("terminal_front"),
            "side": texture("terminal_side"),
        },
    })

    for name in ("controller", "connector", "terminal", "display"):
        write(A + "/models/item/" + name + ".json", {"parent": block(name)})
    write(A + "/models/item/label_gun.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/label_gun"},
    })
    write(A + "/models/item/network_analyser.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/network_analyser"},
    })


# ---- Loot und Rezepte ----------------------------------------------------

def loot_and_recipes():
    for name in ("controller", "connector", "terminal", "display"):
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

    write(D + "/recipe/display.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IGI", "GRG", "IGI"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "G": {"item": "minecraft:glass_pane"},
            "R": {"item": "minecraft:redstone"},
        },
        "result": {"id": MOD + ":display", "count": 1},
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

    # Der Analysator: Redstone fuer das Messen, Quarz fuer die Anzeige, Eisen
    # fuer das Gehaeuse. Nicht teuer — wer ihn braucht, hat schon ein Problem.
    write(D + "/recipe/network_analyser.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": [" Q ", "IRI", " I "],
        "key": {
            "Q": {"item": "minecraft:quartz"},
            "R": {"item": "minecraft:redstone"},
            "I": {"item": "minecraft:iron_ingot"},
        },
        "result": {"id": MOD + ":network_analyser", "count": 1},
    })

    # Das Kabel faellt als der Gegenstand seiner Farbe.
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
        "values": [MOD + ":controller", MOD + ":cable", MOD + ":connector",
                   MOD + ":terminal", MOD + ":display"],
    })


if __name__ == "__main__":
    print("Blockstates:")
    blockstates()
    print("Modelle:")
    models()
    print("Loot und Rezepte:")
    loot_and_recipes()
