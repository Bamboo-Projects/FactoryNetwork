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

# Ein Anschluss an einer Kabelfläche: drei Blockpixel tief, zwölf breit.
# Dieselben Zahlen stehen in CableLayout; CableLayoutTest hält sie zusammen.
PART_DEPTH = 3
PART_WIDTH = 12

# Ein zwanzigstel Blockpixel vor der Blockkante: Sonst liegt die Platte in
# derselben Ebene wie der Kabelarm, der zu ihr wächst, und die Farbe des
# Kabels flimmert quer über ihr Gesicht. Dieselbe Zahl steht in CableLayout.
PART_OVERHANG = 0.05

FACES = ("north", "south", "east", "west", "up", "down")
OPPOSITE = {"north": "south", "south": "north", "east": "west",
            "west": "east", "up": "down", "down": "up"}


def slab_box(facing, near, far, lo, hi):
    """Ein Kasten, von einer Blockfläche nach innen gemessen.

    <b>near</b> und <b>far</b> sind der Abstand von der Fläche, zu der
    <b>facing</b> zeigt; <b>lo</b> und <b>hi</b> spannen die beiden anderen
    Achsen. Wort für Wort dieselbe Rechnung wie CableShapes.slabBox — und
    genau deshalb steht sie zweimal da: Minecraft hält Modell und
    Trefferfläche getrennt, und CableLayoutTest liest beide.
    """
    if facing == "north":
        return [lo, lo, near], [hi, hi, far]
    if facing == "south":
        return [lo, lo, 16 - far], [hi, hi, 16 - near]
    if facing == "west":
        return [near, lo, lo], [far, hi, hi]
    if facing == "east":
        return [16 - far, lo, lo], [16 - near, hi, hi]
    if facing == "down":
        return [lo, near, lo], [hi, far, hi]
    return [lo, 16 - far, lo], [hi, 16 - near, hi]


def connector_part_models():
    """Der Anschluss an einer Kabelfläche — sechs Modelle je Kabelstärke.

    <b>Sechs Dateien statt eines gedrehten Modells:</b> Drehen müsste der
    BlockEntity-Renderer, und ob eine Quaternion stimmt, sieht man nur im
    Spiel. Sechs erzeugte Dateien kosten nichts und lassen sich Zahl für Zahl
    gegen CableLayout prüfen.

    Der Stiel schließt die Lücke zwischen Platte und Kabelkern. Beim dichten
    Kabel gibt es ihn nicht: Dessen Mantel beginnt bei drei, und dort endet
    die Platte schon.
    """
    wide = (16 - PART_WIDTH) // 2
    for size in (THIN, DENSE):
        prefix = "" if size == THIN else "dense_"
        for facing in FACES:
            near, far = slab_box(facing, -PART_OVERHANG, PART_DEPTH, wide, 16 - wide)
            plate = {"from": near, "to": far, "faces": {}}
            for face in FACES:
                which = "front" if face == facing else (
                    "back" if face == OPPOSITE[facing] else "side")
                entry = {"texture": "#" + which}
                if face == facing:
                    # Die Platte liegt bündig in der Blockfläche.
                    entry["cullface"] = facing
                plate["faces"][face] = entry
            # Das Statuslämpchen: ein Ring um die Platte, einen Blockpixel
            # vorstehend, damit man ihn von den Seiten sieht — die Vorderseite
            # der Platte steckt ja in der Maschine. tintindex 0 heißt: Die
            # Farbe kommt zur Laufzeit, aus dem Netzzustand des Anschlusses.
            near, far = slab_box(facing, 0.5, 2.5, 1, 15)
            ring = {"from": near, "to": far, "faces": {}}
            for face in FACES:
                if face == facing or face == OPPOSITE[facing]:
                    # Vorn steckt die Maschine, hinten die Platte: beides
                    # unsichtbar, und ein Quad dafür wäre Arbeit für nichts.
                    continue
                ring["faces"][face] = {"texture": "#status", "tintindex": 0}
            # Kein Stiel mehr zwischen Platte und Kern: Diese Strecke deckt
            # der Arm ab, den das Kabel zu jeder Fläche mit Anschluss wachsen
            # lässt — in seiner eigenen Farbe, mit sichtbarer Kreuzung.
            elements = [plate, ring]

            write(A + "/models/block/%sconnector_part_%s.json" % (prefix, facing), {
                "parent": "minecraft:block/block",
                "textures": {
                    "particle": texture("connector_side"),
                    "front": texture("connector_front"),
                    "back": texture("connector_back"),
                    "side": texture("connector_side"),
                    "status": texture("status_light"),
                },
                "elements": elements,
            })


def status_pad(facing, size=4, out=0.02):
    '''Ein Lämpchen, bündig auf einer Blockfläche.

    Nur die nach außen zeigende Fläche bekommt ein Quad — die anderen fünf
    stecken im Block.
    '''
    near, far = slab_box(facing, -out, out, (16 - size) / 2, (16 + size) / 2)
    return {
        "from": near, "to": far,
        "faces": {facing: {"texture": "#status", "tintindex": 0}},
    }



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
    write(A + "/blockstates/controller_extension.json",
          {"variants": {"": {"model": block("controller_extension")}}})
    write(A + "/blockstates/fabricator.json",
          {"variants": {"": {"model": block("fabricator")}}})

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

    # Brennkammer: Front mit Klappe, brennend eine zweite Textur.
    for name, front in (("burner", "burner_front"), ("burner_on", "burner_front_on")):
        write(A + "/models/block/%s.json" % name, {
            "parent": "minecraft:block/orientable",
            "textures": {
                "front": MOD + ":block/" + front,
                "side": MOD + ":block/machine_top",
                "top": MOD + ":block/machine_top",
            },
        })
    burner_variants = {}
    for lit, model in ((False, "burner"), (True, "burner_on")):
        for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                    ("south", {"y": 180}), ("west", {"y": 270})):
            entry = {"model": block(model)}
            entry.update(rotation)
            burner_variants["facing=%s,lit=%s" % (direction, str(lit).lower())] = entry
    write(A + "/blockstates/burner.json", {"variants": burner_variants})

    # Kreativ-Stromquelle: ein schlichter Würfel in der Maschinenfarbe.
    write(A + "/blockstates/creative_source.json",
          {"variants": {"": {"model": block("creative_source")}}})
    write(A + "/models/block/creative_source.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": MOD + ":block/creative_source"},
    })
    write(A + "/models/item/creative_source.json", {"parent": block("creative_source")})
    write(A + "/models/item/burner.json", {"parent": block("burner")})

    # Die beiden Erze: schlichte Würfel mit eigener Textur.
    for ore in ("crystal_ore", "deepslate_crystal_ore"):
        write(A + "/blockstates/%s.json" % ore,
              {"variants": {"": {"model": block(ore)}}})
        write(A + "/models/block/%s.json" % ore, {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": MOD + ":block/" + ore},
        })
        write(A + "/models/item/%s.json" % ore, {"parent": block(ore)})

    # Laufwerk: ein Gehaeuse auf Fuessen, davor die Blende. Das Modell baut
    # drive_model(); hier stehen nur noch die vier Drehungen.
    drive_variants = {}
    for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                ("south", {"y": 180}), ("west", {"y": 270})):
        entry = {"model": block("drive")}
        entry.update(rotation)
        drive_variants["facing=" + direction] = entry
    write(A + "/blockstates/drive.json", {"variants": drive_variants})

    # Serverschrank: zwei Blöcke hoch, mit zurückgesetzter Front.
    #
    # Kein Würfel mit aufgemalter Vorderseite, sondern ein Rahmen aus vier
    # Leisten und ein Korpus zwei Pixel dahinter. Der Renderer legt die
    # Einschübe in genau diese Vertiefung — dieselben zwei Pixel stehen dort
    # als DEPTH. Die untere Hälfte hat die Bodenleiste, die obere die
    # Deckleiste; dazwischen läuft die Öffnung durch, achtundzwanzig Pixel
    # hoch, und darin liegen die zwölf Einschübe.
    for half in ("lower", "upper"):
        write(A + "/models/block/server_rack_%s.json" % half,
              rack_model(_rack_elements(half)))
    rack_variants = {}
    for half in ("lower", "upper"):
        for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                    ("south", {"y": 180}), ("west", {"y": 270})):
            entry = {"model": block("server_rack_" + half)}
            entry.update(rotation)
            rack_variants["facing=%s,half=%s" % (direction, half)] = entry
    write(A + "/blockstates/server_rack.json", {"variants": rack_variants})

    # Connector zeigt in sechs Richtungen.
    facing = {
        "north": {},
        "south": {"y": 180},
        "east": {"y": 90},
        "west": {"y": 270},
        "up": {"x": 270},
        "down": {"x": 90},
    }
    # Display hängt flach an der Wand, in vier Richtungen — und weiß, an
    # welchen Seiten eine zweite Tafel anschließt. Vier Richtungen mal
    # sechzehn Nachbarschaften sind vierundsechzig Zustände; das klingt nach
    # viel und ist eine Schleife.
    variants = {}
    for direction, rotation in {"north": {}, "south": {"y": 180},
                                "east": {"y": 90}, "west": {"y": 270}}.items():
        for joined in range(16):
            entry = {"model": block("display_%d" % joined)}
            entry.update(rotation)
            name = ",".join([
                "facing=" + direction,
                "joined_down=" + ("true" if joined & EDGE_DOWN else "false"),
                "joined_left=" + ("true" if joined & EDGE_LEFT else "false"),
                "joined_right=" + ("true" if joined & EDGE_RIGHT else "false"),
                "joined_up=" + ("true" if joined & EDGE_UP else "false"),
            ])
            variants[name] = entry
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

# ---- Serverschrank -------------------------------------------------------

def _face(texture, uv, cull=None):
    face = {"uv": uv, "texture": texture}
    if cull:
        face["cullface"] = cull
    return face


def _rack_elements(half, lift=0):
    """Rahmen und Korpus einer Schrankhälfte.

    ``lift`` hebt alles an — das Gegenstandsmodell setzt beide Hälften
    übereinander in ein Modell, damit man in der Hand den ganzen Schrank
    sieht und nicht seine untere Hälfte.
    """
    unten = half == "lower"

    def box(x0, y0, z0, x1, y1, z1):
        return [x0, y0 + lift, z0], [x1, y1 + lift, z1]

    korpus_von, korpus_bis = box(0, 0, 2, 16, 16, 16)
    korpus = {
        "from": korpus_von,
        "to": korpus_bis,
        "faces": {
            "north": _face("#inner", [0, 0, 16, 16]),
            "south": _face("#side", [0, 0, 16, 16], "south"),
            "west": _face("#side", [2, 0, 16, 16], "west"),
            "east": _face("#side", [0, 0, 14, 16], "east"),
        },
    }
    # Die Fläche zur anderen Hälfte gibt es gar nicht. Sie wäre mit deren
    # Gegenstück deckungsgleich, und zwei Flächen an derselben Stelle
    # flimmern gegeneinander — sichtbar als zuckende Linie in der Fuge.
    aussen = "down" if unten else "up"
    korpus["faces"][aussen] = _face("#side", [0, 2, 16, 16], aussen)

    def pfosten(x0, x1, cull):
        von, bis = box(x0, 0, 0, x1, 16, 2)
        return {
            "from": von,
            "to": bis,
            "faces": {
                "north": _face("#frame", [x0, 0, x1, 16]),
                "west": _face("#frame", [14, 0, 16, 16], cull if x0 == 0 else None),
                "east": _face("#frame", [0, 0, 2, 16], cull if x0 != 0 else None),
            },
        }

    def pfosten_mit_deckel(x0, x1, cull):
        # Dieselbe Fuge wie beim Korpus: Nur die äußere Deckfläche wird
        # gezeichnet, die zur anderen Hälfte gar nicht.
        element = pfosten(x0, x1, cull)
        element["faces"][aussen] = _face("#frame", [x0, 0, x1, 2], aussen)
        return element

    elemente = [korpus, pfosten_mit_deckel(0, 2, "west"),
                pfosten_mit_deckel(14, 16, "east")]

    # Nur eine Leiste je Hälfte: unten die Sockelleiste, oben die
    # Deckleiste. Säße an beiden Hälften beides, teilte eine Doppelleiste
    # die Öffnung mitten durch.
    if unten:
        von, bis = box(2, 0, 0, 14, 2, 2)
        leiste_faces = {
            "north": _face("#frame", [2, 14, 14, 16]),
            "up": _face("#frame", [2, 0, 14, 2]),
            "down": _face("#frame", [2, 0, 14, 2], "down"),
        }
    else:
        von, bis = box(2, 14, 0, 14, 16, 2)
        leiste_faces = {
            "north": _face("#frame", [2, 0, 14, 2]),
            "up": _face("#frame", [2, 0, 14, 2], "up"),
            "down": _face("#frame", [2, 0, 14, 2]),
        }
    elemente.append({"from": von, "to": bis, "faces": leiste_faces})
    return elemente


def rack_model(elements, display=None):
    modell = {
        "textures": {
            "particle": MOD + ":block/machine_top",
            "side": MOD + ":block/machine_top",
            "frame": MOD + ":block/server_rack_frame",
            "inner": MOD + ":block/server_rack_inner",
        },
        "elements": elements,
    }
    if display:
        modell["display"] = display
    return modell


# Die Serverbauteile: Art auf ihre Stufen. Dieselben Zahlen wie in
# ServerPart.java — sie stehen an zwei Stellen, weil das eine Java ist und
# das andere Python; wer eine Stufe ergänzt, muss beide anfassen.
SERVER_PARTS = {
    "cpu": (2, 8, 32, 128),
    "ram": (8, 32, 128, 512),
    "disk": (64, 256, 1024, 4096),
}

PART_CORE = {"cpu": "core_logic", "ram": "core_memory", "disk": "core_memory"}


# Welche Seite einer Anzeigetafel an eine andere stößt — dieselben Zahlen
# wie in textures.py und in DisplayBlock.java.
EDGE_UP, EDGE_DOWN, EDGE_LEFT, EDGE_RIGHT = 1, 2, 4, 8


# Der Torbogen des Gateways in Blockpixeln.
#
# Kein Würfel mehr, sondern ein Rahmen, durch den man wirklich hindurchsieht:
# Sockel und Sturz über die volle Fläche, dazwischen vier Ecksäulen und über
# jeder Öffnung zwei Schultern, die den Durchgang nach oben verengen. Zwei
# Zwei Leuchtbänder laufen außen um ihn herum, oben am Sockel und unten am
# Sturz — daran erkennt man den Block von weitem. Dieselbe Sprache spricht der
# Controller mit seinem Band schon.
#
# <b>Der erste Wurf war zu hohl.</b> Sockel und Sturz drei Blockpixel, die
# Ecksäulen vier: Von vorn sah man mehr Luft als Block, und der Rahmen wirkte
# wie ein Gerüst statt wie ein Tor. Jetzt sind es vier und fünf — aus 61
# Hundertsteln Material werden 76, und der Durchgang bleibt sechs Blockpixel
# breit und acht hoch.
#
# <b>Zweimal lag das Licht vorher innen und war nicht zu sehen.</b> Erst als
# Säule mitten im Tor — die stand genau hinter der Ecksäule, die davor steht.
# Dann als Streifen auf dem Sockel: Der ragte über die Ecksäule hinaus, aber
# nur um einen Blockpixel, und das ist aus keiner Richtung ein Bild. Was in
# einem Durchgang liegt, sieht man nur, wenn man hindurchschaut. Außen sieht
# man es immer.
#
# <b>Offen in beide waagerechten Achsen</b>, nicht in eine: Der Block hat
# keine Vorderseite, und ein Bogen, der nur in eine Richtung zeigt, bräuchte
# einen Blockzustand für eine Auskunft, die niemand braucht.
#
# Dieselben Zahlen stehen in GatewayLayout.java; GatewayLayoutTest hält beide
# zusammen.
GATEWAY_FOOT = 4       # bis hierhin reicht der Sockel
GATEWAY_HEAD = 12      # ab hier der Sturz
GATEWAY_POST = 5       # Kantenlänge einer Ecksäule
GATEWAY_SHOULDER = 9  # ab dieser Höhe verengen die Schultern die Öffnung
GATEWAY_REACH = 6      # bis hierhin reicht eine Schulter in die Öffnung
GATEWAY_GLOW = 1       # so stark sind die beiden Leuchtbänder


def box_uv(start, end, face):
    """Die UV-Fläche, die Minecraft einem Kasten ohne eigene UV-Angabe gäbe.

    Der Kasten schneidet die Textur so, als läge sie über dem ganzen Würfel —
    dieselbe Projektion, mit der ein Würfelmodell arbeitet. Deshalb sitzen
    Nieten, Rahmenkante und Fase danach genau dort, wo sie im Würfel saßen,
    auch wenn aus dem Würfel ein Rahmen geworden ist. Eine eigene Textur
    braucht es dafür nicht.
    """
    x0, y0, z0 = start
    x1, y1, z1 = end
    if face == "north":
        return [16 - x1, 16 - y1, 16 - x0, 16 - y0]
    if face == "south":
        return [x0, 16 - y1, x1, 16 - y0]
    if face == "west":
        return [z0, 16 - y1, z1, 16 - y0]
    if face == "east":
        return [16 - z1, 16 - y1, 16 - z0, 16 - y0]
    if face == "up":
        return [x0, z0, x1, z1]
    return [x0, 16 - z1, x1, 16 - z0]


def on_hull(start, end, face):
    """Liegt diese Fläche in der Blockhülle?

    Nur dort darf <b>cullface</b> stehen: Minecraft lässt die Fläche dann
    weg, sobald nebenan ein voller Block steht. Auf einer Fläche im Inneren
    wäre dieselbe Angabe ein Loch, das je nach Nachbarn aufgeht.
    """
    if face == "north":
        return start[2] == 0
    if face == "south":
        return end[2] == 16
    if face == "west":
        return start[0] == 0
    if face == "east":
        return end[0] == 16
    if face == "down":
        return start[1] == 0
    return end[1] == 16


def face_plane(start, end, face):
    """Die Ebene, in der eine Fläche liegt, und ihr Rechteck darin.

    Zurück kommt (Achse, Lage, Rechteck) — zwei Flächen können sich nur
    verdecken, wenn Achse und Lage übereinstimmen.
    """
    x0, y0, z0 = start
    x1, y1, z1 = end
    if face == "north":
        return "z", z0, (x0, y0, x1, y1)
    if face == "south":
        return "z", z1, (x0, y0, x1, y1)
    if face == "west":
        return "x", x0, (z0, y0, z1, y1)
    if face == "east":
        return "x", x1, (z0, y0, z1, y1)
    if face == "down":
        return "y", y0, (x0, z0, x1, z1)
    return "y", y1, (x0, z0, x1, z1)


def hidden(boxes, index, face):
    """Deckt ein anderer Kasten diese Fläche vollständig zu?

    <b>Wozu.</b> Ein Modell aus einem Dutzend Kästen hat leicht ein Drittel
    Flächen, die niemand je sieht: die Unterseite einer Säule, die auf dem
    Sockel steht, die Seite einer Schulter, die an der Säule klebt. Minecraft
    zeichnet sie, wenn sie dastehen. Von Hand abzuzählen, welche das sind, ist
    genau die Arbeit, bei der man eine übersieht — und die übersehene ist
    dann die eine, die im Spiel flimmert, weil zwei Flächen in derselben
    Ebene liegen.

    Verdeckt ist eine Fläche nur, wenn ein einzelner anderer Kasten sie ganz
    zudeckt. Zwei Kästen, die sich die Arbeit teilen, zählen nicht — das
    wäre richtig, aber es zu prüfen kostet mehr als die zwei Quads, um die es
    geht.
    """
    axis, coord, rect = face_plane(*boxes[index][:2], face)
    for other, box in enumerate(boxes):
        if other == index:
            continue
        their_axis, their_coord, their_rect = face_plane(
            box[0], box[1], OPPOSITE[face])
        if their_axis != axis or their_coord != coord:
            continue
        if (their_rect[0] <= rect[0] and their_rect[1] <= rect[1]
                and their_rect[2] >= rect[2] and their_rect[3] >= rect[3]):
            return True
    return False


def machine_elements(boxes):
    """Aus Kästen die Elemente eines Blockmodells.

    Jeder Kasten ist (Anfang, Ende, Texturen). In den Texturen steht je
    Fläche ein Name; {@code "*"} gilt für alle, die nicht einzeln genannt
    sind. Welche Flächen wegfallen, rechnet {@link hidden}; welchen
    Ausschnitt der Textur eine Fläche bekommt, {@link box_uv}; und
    {@code cullface} setzt {@link on_hull}, wo es hingehört.
    """
    elements = []
    for index, (start, end, faces) in enumerate(boxes):
        entry = {"from": list(start), "to": list(end), "faces": {}}
        for face in FACES:
            if hidden(boxes, index, face):
                continue
            which = faces.get(face, faces.get("*", "side"))
            quad = {"texture": "#" + which, "uv": box_uv(start, end, face)}
            if on_hull(start, end, face):
                quad["cullface"] = face
            entry["faces"][face] = quad
        elements.append(entry)
    return elements


def gateway_boxes():
    """Die Kästen des Torbogens, jeder mit seinen Texturen.

    Welche Flächen davon überhaupt gezeichnet werden, rechnet
    {@link machine_elements}: Die Unterseite einer Ecksäule steht auf dem
    Sockel, ihre Oberseite unter dem Sturz, und die eine Seite einer Schulter
    stößt an die Säule, aus der sie wächst.
    """
    foot, head = GATEWAY_FOOT, GATEWAY_HEAD
    post, shoulder, reach = GATEWAY_POST, GATEWAY_SHOULDER, GATEWAY_REACH
    glow = GATEWAY_GLOW
    boxes = [
        # Sockel und Sturz: die volle Grundfläche. Nach oben und unten bleibt
        # der Block geschlossen — ein Tor, kein Schacht.
        ([0, 0, 0], [16, foot - glow, 16], {"*": "outer"}),
        # Die beiden Leuchtbänder. Sie liegen in der Blockhülle und laufen
        # außen um den ganzen Block: oben auf dem Sockel, unten am Sturz.
        # Was von ihnen in den Durchgang zeigt, ist Gehäuse und kein Licht.
        ([0, foot - glow, 0], [16, foot, 16], {"*": "glow", "up": "inner"}),
        ([0, head, 0], [16, head + glow, 16], {"*": "glow", "down": "inner"}),
        ([0, head + glow, 0], [16, 16, 16], {"*": "outer"}),
    ]

    # Die vier Ecksäulen. Zwei ihrer Seiten liegen in der Blockhülle und
    # tragen die Textur samt ihren Nieten; die beiden anderen zeigen in den
    # Durchgang.
    for x in (0, 16 - post):
        for z in (0, 16 - post):
            outer_x = "west" if x == 0 else "east"
            outer_z = "north" if z == 0 else "south"
            boxes.append(([x, foot, z], [x + post, head, z + post], {
                outer_x: "outer",
                outer_z: "outer",
                "*": "inner",
            }))

    # Über jeder Öffnung zwei Schultern: die Stufe, aus der in sechzehn
    # Pixeln ein Bogen wird. Ein runder wäre ein Dutzend Kästen mehr für eine
    # Rundung, die bei dieser Auflösung ohnehin eckig ankommt.
    shoulders = []
    for lo in (post, 16 - reach):
        hi = lo + reach - post
        shoulders.append(([lo, shoulder, 0], [hi, head, post], "north"))
        shoulders.append(([lo, shoulder, 16 - post], [hi, head, 16], "south"))
        shoulders.append(([0, shoulder, lo], [post, head, hi], "west"))
        shoulders.append(([16 - post, shoulder, lo], [16, head, hi], "east"))

    for start, end, outer in shoulders:
        boxes.append((start, end, {outer: "outer", "*": "inner"}))

    return boxes


# Das Laufwerk in Blockpixeln, Vorderseite nach Norden.
#
# Ein Gehäuse auf vier Füßen, davor eine Blende, und in der Blende liegt das
# Schachtfeld versenkt. Die Blende ist seitlich einen Blockpixel breiter als
# das Gehäuse — von der Seite steht sie also vor, und in einer Reihe stoßen
# die Blenden aneinander, während zwischen den Gehäusen eine Fuge bleibt.
# Genau so sieht eine Reihe Geräte aus und nicht wie eine Wand.
#
# Dieselben Zahlen stehen in DriveLayout.java; DriveLayoutTest hält beide
# zusammen.
DRIVE_FOOT = 2        # Höhe der Füße
DRIVE_FOOT_WIDE = 3   # Grundfläche eines Fußes
DRIVE_FRONT = 2       # wie weit die Blende vor dem Gehäuse steht
DRIVE_INSET = 1       # wie weit das Gehäuse hinter der Blende zurückspringt
DRIVE_BEZEL = 1       # Breite der Fassung um das Schachtfeld
DRIVE_RECESS = 1      # wie tief das Feld in der Blende liegt


def drive_boxes():
    """Die Kästen des Laufwerks, jeder mit seinen Texturen.

    Alles, was nach vorn zeigt, trägt {@code drive_front} — und zwar mit dem
    Ausschnitt, den ein Würfelmodell an dieser Stelle genommen hätte. Der
    erhabene Rahmen der Textur liegt dadurch auf der Fassung und die
    Schächte im versenkten Feld: Was gemalt ist, sitzt jetzt dort, wo die
    Form es hinstellt.
    """
    foot, wide = DRIVE_FOOT, DRIVE_FOOT_WIDE
    front, inset = DRIVE_FRONT, DRIVE_INSET
    bezel, recess = DRIVE_BEZEL, DRIVE_RECESS

    boxes = [
        # Das Gehäuse: hinten bündig, seitlich schmaler als die Blende, und
        # es fängt erst über den Füßen an.
        ([inset, foot, front], [16 - inset, 16, 16], {"*": "side"}),

        # Die Fassung — vier Kästen um das Feld herum, in der Blockhülle.
        ([0, 16 - bezel, 0], [16, 16, front], {"north": "front", "*": "side"}),
        ([0, foot, 0], [16, foot + bezel, front], {"north": "front", "*": "side"}),
        ([0, foot + bezel, 0], [bezel, 16 - bezel, front],
         {"north": "front", "*": "side"}),
        ([16 - bezel, foot + bezel, 0], [16, 16 - bezel, front],
         {"north": "front", "*": "side"}),

        # Das Schachtfeld, einen Blockpixel hinter der Fassung. Dass es
        # zurückliegt, ist der ganze Punkt: In der Textur war die Vertiefung
        # gemalt, jetzt ist sie da.
        ([bezel, foot + bezel, recess], [16 - bezel, 16 - bezel, front],
         {"north": "front", "*": "side"}),
    ]

    # Vier Füße an den Ecken. Dazwischen sieht man unter das Gerät — daran
    # erkennt man von weitem, dass es steht und nicht in der Wand klebt.
    for x in (0, 16 - wide):
        for z in (0, 16 - wide):
            boxes.append(([x, 0, z], [x + wide, foot, z + wide], {"*": "side"}))

    return boxes


def drive_model():
    """Das Laufwerk als Gerät statt als Würfel."""
    write(A + "/models/block/drive.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("machine_top"),
            "front": texture("drive_front"),
            "side": texture("machine_top"),
        },
        "elements": machine_elements(drive_boxes()),
    })


def gateway_model():
    """Das Gateway als Torbogen statt als Würfel.

    Die Außenflächen nehmen ihre Textur aus derselben {@code gateway.png} wie
    zuvor und an denselben Stellen — nur ist jetzt Luft, wo vorher der
    gemalte Bogen lag. Die Laibung bekommt das Maschinengehäuse: Sie war
    vorher nie zu sehen und hat deshalb keine eigene Textur.
    """
    write(A + "/models/block/gateway.json", {
        # Ohne den Vanilla-Vorfahren fehlen die Ansichten für Hand und
        # Inventar — die gaben cube_all und seinesgleichen bisher gratis.
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("gateway"),
            "outer": texture("gateway"),
            "inner": texture("machine_top"),
            "glow": texture("gateway_glow"),
        },
        "elements": machine_elements(gateway_boxes()),
    })
    write(A + "/blockstates/gateway.json",
          {"variants": {"": {"model": block("gateway")}}})
    write(A + "/models/item/gateway.json", {"parent": block("gateway")})


def models():
    write(A + "/models/block/controller.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": texture("controller_top"),
            "bottom": texture("controller_top"),
            "side": texture("controller_side"),
        },
    })

    # Der Anbau zeigt auf allen sechs Seiten dasselbe: Er hat keine
    # Vorderseite, weil jede seiner Seiten dieselbe Aufgabe hat.
    write(A + "/models/block/controller_extension.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": texture("controller_extension")},
    })

    write(A + "/models/block/fabricator.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": texture("fabricator_top"),
            "bottom": texture("controller_extension"),
            "side": texture("fabricator_side"),
        },
    })

    cable_models()
    connector_part_models()
    gateway_model()
    drive_model()

    # Das Blockmodell des Connectors ist am 26.08. mit seinem Block
    # verschwunden. Diese Erzeugung stand noch hier und legte die Datei bei
    # jedem Lauf wieder an — ein Modell für einen Block, den es nicht gibt.

    # Zwei Pixel tief an der Wand. Ein eigenes Modell statt orientable,
    # weil es kein Würfel ist.
    for joined in range(16):
        write(A + "/models/block/display_%d.json" % joined, {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": texture("display_side"),
                "front": texture("display_front_%d" % joined),
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
    # In der Hand die freistehende Tafel: Sie ist das, was man setzt.
    write(A + "/models/item/display.json", {"parent": block("display_0")})

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

    # Die Anzeigetafel fehlt hier: Ihr Blockmodell heißt display_0, weil es
    # sechzehn davon gibt. Sie steht weiter oben, wo die sechzehn entstehen.
    for name in ("controller", "controller_extension", "fabricator",
                 "terminal", "drive", "press"):
        write(A + "/models/item/" + name + ".json", {"parent": block(name)})
    # Der Anschluss hat keinen eigenen Block mehr. In der Hand zeigt er die
    # Platte, die er wird — aber <b>mittig</b> und ohne das Lämpchen.
    #
    # Vorher erbte er vom Teilmodell für die Nordfläche. Dessen Platte klebt
    # am Rand des Würfels, weil sie dort an einem Kabel sitzt; in der Hand und
    # im Rucksack hing sie deshalb schief am Rand statt in der Mitte. Und das
    # Lämpchen wäre dort weiß: Es trägt einen tintindex, den im Inventar
    # niemand einfärbt — ein Gerät, das ohne Netz in der Hand liegt, hat auch
    # keinen Zustand zu zeigen.
    wide = (16 - PART_WIDTH) // 2
    write(A + "/models/item/connector.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("connector_side"),
            "front": texture("connector_front"),
            "back": texture("connector_back"),
            "side": texture("connector_side"),
        },
        "elements": [{
            "from": [wide, wide, 8 - PART_DEPTH / 2],
            "to": [16 - wide, 16 - wide, 8 + PART_DEPTH / 2],
            "faces": {
                "north": {"texture": "#front"},
                "south": {"texture": "#back"},
                "east": {"texture": "#side"},
                "west": {"texture": "#side"},
                "up": {"texture": "#side"},
                "down": {"texture": "#side"},
            },
        }],
    })
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
    for tier in ("64", "256", "1024", "4096"):
        write(A + "/models/item/fluid_cell_%s.json" % tier, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": MOD + ":item/fluid_cell_" + tier},
        })
    for tier in ("64k", "256k", "1024k", "4096k"):
        write(A + "/models/item/energy_cell_%s.json" % tier, {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": MOD + ":item/energy_cell_" + tier},
        })
    write(A + "/models/item/drive.json", {"parent": block("drive")})
    write(A + "/models/item/router.json", {"parent": block("router")})
    # Der Schrank in der Hand: beide Hälften übereinander in einem Modell,
    # klein gerechnet. Nur die untere zu zeigen wäre ein Gegenstand, der
    # anders aussieht als das, was man setzt.
    write(A + "/models/item/server_rack.json", rack_model(
        _rack_elements("lower") + _rack_elements("upper", lift=16), {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0],
                    "scale": [0.42, 0.42, 0.42]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0],
                       "scale": [0.2, 0.2, 0.2]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0],
                      "scale": [0.42, 0.42, 0.42]},
            "thirdperson_righthand": {"rotation": [75, 45, 0],
                                      "translation": [0, 1.5, 0],
                                      "scale": [0.22, 0.22, 0.22]},
            "firstperson_righthand": {"rotation": [0, 45, 0],
                                      "translation": [0, 0, 0],
                                      "scale": [0.28, 0.28, 0.28]},
        }))
    write(A + "/models/item/server_chassis.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": MOD + ":item/server_chassis"},
    })
    for kind, tiers in SERVER_PARTS.items():
        for value in tiers:
            name = "%s_%d" % (kind, value)
            write(A + "/models/item/%s.json" % name, {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": MOD + ":item/" + name},
            })
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
    # Der Schrank hängt nicht in dieser Schleife: Er ist zwei Blöcke hoch,
    # und ohne Bedingung machte eine Explosion aus einem Schrank zwei.
    write(D + "/loot_table/blocks/server_rack.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": MOD + ":server_rack"}],
            "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {
                    "condition": "minecraft:block_state_property",
                    "block": MOD + ":server_rack",
                    "properties": {"half": "lower"},
                },
            ],
        }],
    })
    for name in ("controller", "controller_extension", "fabricator",
                 "terminal", "display", "drive", "press", "router", "burner"):
        write(D + "/loot_table/blocks/" + name + ".json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": MOD + ":" + name}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })

    # Kabel geben ihre Farbe zurück.
    #
    # Die Farbe steht im Blockzustand, der Gegenstand dagegen ist je Farbe ein
    # eigener. Ohne diese Zuordnung fiele beim Abbauen ein neutrales Kabel
    # heraus — oder, wie bisher, gar keines: Die Tabelle war leer.
    for sorte in ("cable", "dense_cable"):
        kinder = []
        for farbe in CABLE_COLOURS[1:]:
            kinder.append({
                "type": "minecraft:item",
                "name": "%s:%s_%s" % (MOD, farbe, sorte),
                "conditions": [{
                    "condition": "minecraft:block_state_property",
                    "block": "%s:%s" % (MOD, sorte),
                    "properties": {"colour": farbe},
                }],
            })
        # Ohne Bedingung und zuletzt: das neutrale Kabel als Rückfall.
        kinder.append({"type": "minecraft:item", "name": "%s:%s" % (MOD, sorte)})
        write(D + "/loot_table/blocks/%s.json" % sorte, {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:alternatives", "children": kinder}],
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

    # Die Brennkammer: bewusst billig. Sie ist der Einstieg in den Strom und
    # soll niemanden aufhalten.
    write(D + "/recipe/burner.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "IFI", "IRI"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "F": {"item": "minecraft:furnace"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": MOD + ":burner", "count": 1},
    })

    # Der Serverschrank: das Gehäuse allein, die Leistung steckt in den
    # Bauteilen, die man hineinsteckt.
    write(D + "/recipe/server_rack.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "LIL", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "L": {"item": MOD + ":core_logic"},
            "I": {"item": "minecraft:iron_block"},
        },
        "result": {"id": MOD + ":server_rack", "count": 1},
    })

    # Das Servergehäuse: ein Blech mit Steckplätzen, sonst nichts. Es soll
    # billig sein — man braucht zwölf davon je Schrank, und teuer ist die
    # Hardware, die hineinkommt.
    write(D + "/recipe/server_chassis.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "I I", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "I": {"item": "minecraft:iron_ingot"},
        },
        "result": {"id": MOD + ":server_chassis", "count": 1},
    })

    # Die Serverbauteile. Die erste Stufe kommt aus Grundstoffen, jede
    # weitere aus vier der vorigen und einem Kern — und ab der dritten
    # kostet sie zusätzlich etwas, das man sich erst holen muss.
    #
    # Viermal die vorige Stufe für viermal die Leistung ist auf den ersten
    # Blick kein Gewinn. Der Gewinn ist der Platz: Ein Schrank hat zwölf
    # Einschübe, und mehr als zwölf Bauteile je Art passen nicht hinein.
    # Wer weiterkommen will, muss nach oben und nicht in die Breite.
    write(D + "/recipe/cpu_2.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PCP", "CLC", "PCP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "L": {"item": MOD + ":core_logic"},
        },
        "result": {"id": MOD + ":cpu_2", "count": 1},
    })
    write(D + "/recipe/ram_8.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["CPC", "PMP", "CPC"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "M": {"item": MOD + ":core_memory"},
        },
        "result": {"id": MOD + ":ram_8", "count": 1},
    })
    write(D + "/recipe/disk_64.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "CMC", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "M": {"item": MOD + ":core_memory"},
        },
        "result": {"id": MOD + ":disk_64", "count": 1},
    })
    # Die Mitte des Kreuzes sagt, wie weit man ist: erst der eigene Kern,
    # dann ein Diamant, dann Netherit.
    mitte = [None,
             None,
             {"item": "minecraft:diamond"},
             {"item": "minecraft:netherite_ingot"}]
    for kind, tiers in SERVER_PARTS.items():
        for stufe in range(1, len(tiers)):
            zutat = mitte[stufe] or {"item": MOD + ":" + PART_CORE[kind]}
            write(D + "/recipe/%s_%d.json" % (kind, tiers[stufe]), {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "pattern": [" T ", "TXT", " T "],
                "key": {
                    "T": {"item": "%s:%s_%d" % (MOD, kind, tiers[stufe - 1])},
                    "X": zutat,
                },
                "result": {"id": "%s:%s_%d" % (MOD, kind, tiers[stufe]), "count": 1},
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

    # Die Flüssigkeitszellen. Dieselbe Leiter wie bei den Gegenstandszellen:
    # die kleinste aus Bauteilen, jede weitere aus vier der vorherigen. Statt
    # des Speicherkerns steckt hier ein Eimer — was hineingeht, sagt schon
    # das Rezept.
    write(D + "/recipe/fluid_cell_64.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PCP", "CBC", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "B": {"item": "minecraft:bucket"},
        },
        "result": {"id": MOD + ":fluid_cell_64", "count": 1},
    })
    for kleiner, groesser in (("64", "256"), ("256", "1024"), ("1024", "4096")):
        write(D + "/recipe/fluid_cell_%s.json" % groesser, {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": [" C ", "CIC", " C "],
            "key": {
                "C": {"item": MOD + ":fluid_cell_" + kleiner},
                "I": {"item": "minecraft:iron_ingot"},
            },
            "result": {"id": MOD + ":fluid_cell_" + groesser, "count": 1},
        })

    # Die Energiezellen. Dieselbe Leiter, dieselbe Rechnung — statt des
    # Speicherkerns steckt hier Redstone, und das Eisen in der Ausbaustufe
    # weicht der Kupferspule. Eine Zelle, die Strom hält, soll nicht
    # aussehen wie eine, die Eisenbarren zählt.
    write(D + "/recipe/energy_cell_64k.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PCP", "CRC", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
            "R": {"item": "minecraft:redstone_block"},
        },
        "result": {"id": MOD + ":energy_cell_64k", "count": 1},
    })
    for kleiner, groesser in (("64k", "256k"), ("256k", "1024k"), ("1024k", "4096k")):
        write(D + "/recipe/energy_cell_%s.json" % groesser, {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": [" C ", "CKC", " C "],
            "key": {
                "C": {"item": MOD + ":energy_cell_" + kleiner},
                "K": {"item": "minecraft:copper_ingot"},
            },
            "result": {"id": MOD + ":energy_cell_" + groesser, "count": 1},
        })

    # Der Anbau: ein Controller ohne Kern. Genau das ist er auch — dasselbe
    # Gehäuse, nur ohne das, was den Controller ausmacht, und deshalb ohne
    # den Netzkern im Rezept.
    write(D + "/recipe/controller_extension.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "PCP", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "C": {"item": MOD + ":crystal"},
        },
        "result": {"id": MOD + ":controller_extension", "count": 2},
    })

    # Der Fabricator: eine Werkbank, die das Netz bedient. Deshalb steckt
    # eine im Rezept — und ein Netzkern, weil er ohne Netz nichts tut.
    write(D + "/recipe/fabricator.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PWP", "PNP", "PPP"],
        "key": {
            "P": {"item": MOD + ":plate"},
            "W": {"item": "minecraft:crafting_table"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":fabricator", "count": 1},
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
        "values": [MOD + ":controller", MOD + ":controller_extension",
                   MOD + ":fabricator",
                   MOD + ":cable", MOD + ":dense_cable",
                   MOD + ":terminal", MOD + ":display",
                   MOD + ":drive", MOD + ":press", MOD + ":router",
                   MOD + ":server_rack", MOD + ":burner",
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
