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
# Die beiden Kabelstärken in Blockpixeln — dieselben Werte wie bei AE2 für
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

    for sort in ("cable", "dense_cable"):
        for colour in CABLE_COLOURS:
            name = sort if colour == "none" else colour + "_" + sort
            write(A + "/models/item/%s.json" % name,
                  {"parent": block(sort + "_inventory")})


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

    # Presse: Front mit Stempel und Amboss, sonst Maschinengehäuse.
    write(A + "/models/block/press.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": MOD + ":block/press_front",
            "side": MOD + ":block/machine_top",
            "top": MOD + ":block/machine_top",
        },
    })
    press_variants = {}
    for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                ("south", {"y": 180}), ("west", {"y": 270})):
        entry = {"model": block("press")}
        entry.update(rotation)
        press_variants["facing=" + direction] = entry
    write(A + "/blockstates/press.json", {"variants": press_variants})

    # Router: ein voller Würfel, auf allen Seiten gleich. Welche Bahn eine
    # Seite führt, malt der Renderer darüber — als Blockzustand wären es
    # 15625 Kombinationen für dieselbe Auskunft.
    write(A + "/blockstates/router.json",
          {"variants": {"": {"model": block("router")}}})
    write(A + "/models/block/router.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": MOD + ":block/router_side"},
    })

    # Die beiden Erze: schlichte Würfel mit eigener Textur.
    for ore in ("crystal_ore", "deepslate_crystal_ore"):
        write(A + "/blockstates/%s.json" % ore,
              {"variants": {"": {"model": block(ore)}}})
        write(A + "/models/block/%s.json" % ore, {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": MOD + ":block/" + ore},
        })
        write(A + "/models/item/%s.json" % ore, {"parent": block(ore)})

    # Laufwerk: Front mit Schächten, sonst Maschinengehäuse.
    write(A + "/models/block/drive.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": MOD + ":block/drive_front",
            "side": MOD + ":block/machine_top",
            "top": MOD + ":block/machine_top",
        },
    })
    drive_variants = {}
    for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                ("south", {"y": 180}), ("west", {"y": 270})):
        entry = {"model": block("drive")}
        entry.update(rotation)
        drive_variants["facing=" + direction] = entry
    write(A + "/blockstates/drive.json", {"variants": drive_variants})

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

    # Das Erz gibt Rohkristalle, mit Glueck mehr — wie jedes Vanilla-Erz.
    for ore in ("crystal_ore", "deepslate_crystal_ore"):
        write(D + "/loot_table/blocks/%s.json" % ore, {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {
                            "type": "minecraft:item",
                            "name": MOD + ":raw_crystal",
                            "conditions": [{
                                "condition": "minecraft:match_tool",
                                "predicate": {"predicates": {
                                    "minecraft:enchantments": [{
                                        "enchantments": "minecraft:silk_touch",
                                        "levels": {"min": 1},
                                    }],
                                }},
                            }],
                        },
                        {
                            "type": "minecraft:item",
                            "name": MOD + ":raw_crystal",
                            "functions": [
                                {"function": "minecraft:set_count",
                                 "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                                {"function": "minecraft:apply_bonus",
                                 "enchantment": "minecraft:fortune",
                                 "formula": "minecraft:ore_drops"},
                            ],
                        },
                    ],
                }],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    for name in ("controller", "connector", "terminal", "display", "drive", "press"):
        write(A + "/models/item/" + name + ".json", {"parent": block(name)})
    write(A + "/models/item/label_gun.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/label_gun"},
    })
    write(A + "/models/item/network_analyser.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": MOD + ":item/network_analyser"},
    })
    for tier in ("k1", "k4", "k16", "k64"):
        write(A + "/models/item/cell_%s.json" % tier, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": MOD + ":item/cell_" + tier},
        })
    write(A + "/models/item/drive.json", {"parent": block("drive")})
    write(A + "/models/item/router.json", {"parent": block("router")})
    for name in ("crystal", "plate", "stamp_plate", "stamp_logic", "stamp_memory",
                 "stamp_network", "core_logic", "core_memory", "core_network"):
        write(A + "/models/item/%s.json" % name, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": MOD + ":item/" + name},
        })
    write(A + "/models/item/press.json", {"parent": block("press")})
    write(A + "/models/item/raw_crystal.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": MOD + ":item/raw_crystal"},
    })


def worldgen():
    """Wo das Erz in der Welt liegt.

    Zwei Vorkommen, wie bei Vanilla-Erzen üblich: eines in mittlerer Höhe,
    eines tief unten. Die Zahlen sind bewusst zurückhaltend — in einem Pack
    mit zweihundert Erzen ist ein weiteres schnell zu viel, und die Kette
    braucht keine grossen Mengen.
    """
    write(D + "/worldgen/configured_feature/crystal_ore.json", {
        "type": "minecraft:ore",
        "config": {
            "size": 6,
            "discard_chance_on_air_exposure": 0.0,
            "targets": [
                {
                    "target": {"predicate_type": "minecraft:tag_match",
                               "tag": "minecraft:stone_ore_replaceables"},
                    "state": {"Name": MOD + ":crystal_ore"},
                },
                {
                    "target": {"predicate_type": "minecraft:tag_match",
                               "tag": "minecraft:deepslate_ore_replaceables"},
                    "state": {"Name": MOD + ":deepslate_crystal_ore"},
                },
            ],
        },
    })

    write(D + "/worldgen/placed_feature/crystal_ore.json", {
        "feature": MOD + ":crystal_ore",
        "placement": [
            {"type": "minecraft:count", "count": 4},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range",
             "height": {"type": "minecraft:trapezoid",
                        "min_inclusive": {"absolute": -48},
                        "max_inclusive": {"absolute": 48}}},
            {"type": "minecraft:biome"},
        ],
    })

    # Der Biome-Modifier haengt das Vorkommen in jede Oberwelt.
    write(D + "/neoforge/biome_modifier/crystal_ore.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": MOD + ":crystal_ore",
        "step": "underground_ores",
    })


# ---- Loot und Rezepte ----------------------------------------------------

def loot_and_recipes():
    for name in ("controller", "connector", "terminal", "display", "drive",
                 "press", "router"):
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
        "pattern": ["PLP", "LRL", "PLP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "L": {"item": MOD + ":core_logic"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": MOD + ":controller", "count": 1},
    })

    write(D + "/recipe/cable.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "NNN", "III"],
        "key": {
            "I": {"item": "minecraft:iron_nugget"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":cable", "count": 12},
    })

    write(D + "/recipe/connector.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "PNP", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":connector", "count": 2},
    })

    write(D + "/recipe/terminal.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PGP", "GLG", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "G": {"item": "minecraft:glass_pane"},
            "L": {"item": MOD + ":core_logic"},
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

    # Der Router: die Kreuzung des dicken Kabels. Er kostet ein dickes
    # Kabel und den Netzwerkkern, der die Bahnen auseinanderhält.
    write(D + "/recipe/router.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" P ", "CNC", " P "],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":dense_cable"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":router", "count": 1},
    })

    # Das Laufwerk: ein Gehäuse mit Schächten.
    write(D + "/recipe/drive.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "MCM", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "M": {"item": MOD + ":core_memory"},
            "C": {"item": "minecraft:chest"},
        },
        "result": {"id": MOD + ":drive", "count": 1},
    })

    # Die Zellen. Die kleinste aus Quarz, jede weitere aus vier der
    # vorherigen — so kostet eine 64k genau vierundsechzig kleine, und die
    # Zahl im Namen stimmt mit dem Preis überein.
    write(D + "/recipe/cell_k1.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PCP", "CMC", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "M": {"item": MOD + ":core_memory"},
        },
        "result": {"id": MOD + ":cell_k1", "count": 1},
    })
    for kleiner, groesser in (("k1", "k4"), ("k4", "k16"), ("k16", "k64")):
        write(D + "/recipe/cell_%s.json" % groesser, {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": [" C ", "CIC", " C "],
            "key": {
                "C": {"item": MOD + ":cell_" + kleiner},
                "I": {"item": "minecraft:iron_ingot"},
            },
            "result": {"id": MOD + ":cell_" + groesser, "count": 1},
        })

    # ---- Die Fertigungskette -------------------------------------------

    # Die Presse selbst: noch von Hand, sonst käme man nie hinein.
    write(D + "/recipe/press.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["IPI", "IRI", "III"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "P": {"item": "minecraft:piston"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": MOD + ":press", "count": 1},
    })

    # Die Stempel: teuer, aber einmalig. Ein Diamant im Kopf, damit sie halten.
    stempel = {
        "plate": "minecraft:iron_ingot",
        "logic": "minecraft:gold_ingot",
        "memory": "minecraft:redstone_block",
        "network": "minecraft:copper_ingot",
    }
    for kind, kern in stempel.items():
        write(D + "/recipe/stamp_%s.json" % kind, {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" D ", "MKM", "III"],
            "key": {
                "D": {"item": "minecraft:diamond"},
                "M": {"item": MOD + ":raw_crystal"},
                "K": {"item": kern},
                "I": {"item": "minecraft:iron_ingot"},
            },
            "result": {"id": MOD + ":stamp_" + kind, "count": 1},
        })

    # Und was die Presse daraus macht.
    def press_recipe(name, stamp, material, result, count=1, energy=2000, ticks=100):
        write(D + "/recipe/press_%s.json" % name, {
            "type": MOD + ":press",
            "stamp": {"item": MOD + ":stamp_" + stamp},
            "material": material,
            "result": {"id": result, "count": count},
            "energy": energy,
            "ticks": ticks,
        })

    press_recipe("plate_iron", "plate", {"item": "minecraft:iron_ingot"},
                 MOD + ":plate", 1, 1200, 60)
    press_recipe("crystal", "plate", {"item": MOD + ":raw_crystal"},
                 MOD + ":crystal", 1, 1600, 80)
    press_recipe("core_logic", "logic", {"item": MOD + ":plate"},
                 MOD + ":core_logic", 1, 3000, 120)
    press_recipe("core_memory", "memory", {"item": MOD + ":plate"},
                 MOD + ":core_memory", 1, 3000, 120)
    press_recipe("core_network", "network", {"item": MOD + ":plate"},
                 MOD + ":core_network", 1, 3000, 120)

    # Der Analysator: Redstone für das Messen, Quarz für die Anzeige, Eisen
    # für das Gehäuse. Nicht teuer — wer ihn braucht, hat schon ein Problem.
    write(D + "/recipe/network_analyser.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": [" C ", "PLP", " P "],
        "key": {
            "C": {"item": MOD + ":crystal"},
            "L": {"item": MOD + ":core_logic"},
            "P": {"item": MOD + ":plate"},
        },
        "result": {"id": MOD + ":network_analyser", "count": 1},
    })

    # Das dichte Kabel: vier gewöhnliche, um einen Eisenblock gelegt. Der
    # Preis soll die vierfache Kapazität spiegeln, ohne unerreichbar zu sein.
    write(D + "/recipe/dense_cable.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" C ", "CIC", " C "],
        "key": {
            "C": {"tag": "c:cables"},
            "I": {"item": "minecraft:iron_block"},
        },
        "result": {"id": MOD + ":dense_cable", "count": 1},
    })

    # Beide Sorten fallen als der Gegenstand ihrer Farbe.
    for sort in ("cable", "dense_cable"):
        write(D + "/loot_table/blocks/%s.json" % sort,
              {"type": "minecraft:block", "pools": []})

    # Färben: ein Farbstoff auf ein beliebiges Kabel. Über das Tag statt über
    # siebzehn Gegenstände einzeln — sonst wären es je Sorte
    # zweihundertzweiundsiebzig Rezepte statt sechzehn.
    for sort, tag in (("cable", "c:cables"), ("dense_cable", "c:dense_cables")):
        for colour in CABLE_COLOURS[1:]:
            write(D + "/recipe/%s_%s.json" % (colour, sort), {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "group": "factorynetwork_" + sort,
                "ingredients": [{"tag": tag}, {"item": "minecraft:%s_dye" % colour}],
                "result": {"id": MOD + ":%s_%s" % (colour, sort), "count": 1},
            })

        # Entfärben mit einem Wassereimer, wie bei Applied Energistics. Ohne
        # das wäre ein gefärbtes Kabel eine Sackgasse.
        write(D + "/recipe/%s_uncolour.json" % sort, {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": "factorynetwork_" + sort,
            "ingredients": [{"tag": tag}, {"item": "minecraft:water_bucket"}],
            "result": {"id": MOD + ":" + sort, "count": 1},
        })

        # Alle Farben einer Sorte unter einem Tag, damit die Rezepte jede
        # annehmen.
        write("data/c/tags/item/%ss.json" % sort, {
            "values": [MOD + ":" + sort]
                      + [MOD + ":%s_%s" % (c, sort) for c in CABLE_COLOURS[1:]],
        })

    # Spitzhacke reicht zum Abbauen.
    write(D + "/tags/block/mineable/pickaxe.json", {
        "values": [MOD + ":controller", MOD + ":cable", MOD + ":dense_cable",
                   MOD + ":connector", MOD + ":terminal", MOD + ":display",
                   MOD + ":drive", MOD + ":press", MOD + ":router",
                   MOD + ":crystal_ore",
                   MOD + ":deepslate_crystal_ore"],
    })


if __name__ == "__main__":
    print("Blockstates:")
    blockstates()
    print("Modelle:")
    models()
    print("Weltgenerierung:")
    worldgen()
    print("Loot und Rezepte:")
    loot_and_recipes()
