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

    press_variants = {}
    for direction, rotation in (("north", {}), ("east", {"y": 90}),
                                ("south", {"y": 180}), ("west", {"y": 270})):
        entry = {"model": block("press")}
        entry.update(rotation)
        press_variants["facing=" + direction] = entry
    write(A + "/blockstates/press.json", {"variants": press_variants})

    # Router: auf allen Seiten gleich. Welche Bahn eine Seite führt, malt der
    # Renderer darüber — als Blockzustand wären es 15625 Kombinationen für
    # dieselbe Auskunft. Das Modell baut router_model().
    write(A + "/blockstates/router.json",
          {"variants": {"": {"model": block("router")}}})

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
# <b>Zweimal war er zu hohl.</b> Der erste Wurf hatte Sockel und Sturz von
# drei Blockpixeln und Ecksäulen von vier — 61 Hundertstel Material, und von
# vorn sah man mehr Luft als Block. Vier und fünf brachten 76 und waren
# immer noch ein Gerüst. Jetzt sind es fünf und sechs: 84 Hundertstel, und der
# Durchgang ist vier Blockpixel breit und sechs hoch. Ein Tor, durch das man
# sieht, aber keines, durch das man ginge.
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
GATEWAY_FOOT = 5       # bis hierhin reicht der Sockel
GATEWAY_HEAD = 11      # ab hier der Sturz
GATEWAY_POST = 6       # Kantenlänge einer Ecksäule
GATEWAY_SHOULDER = 9  # ab dieser Höhe verengen die Schultern die Öffnung
GATEWAY_REACH = 7      # bis hierhin reicht eine Schulter in die Öffnung
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
DRIVE_BEZEL = 2       # Breite der Fassung um das Schachtfeld
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
        #
        # <b>Über die volle Höhe, nicht erst über den Füßen.</b> Die beiden
        # unteren Nieten der Textur sitzen unterhalb von zwei Blockpixeln;
        # solange die Blende dort anfing, gab es sie im Modell nicht, und
        # oben standen zwei, unten keine.
        ([0, 16 - bezel, 0], [16, 16, front], {"north": "front", "*": "side"}),
        ([0, 0, 0], [16, bezel, front], {"north": "front", "*": "side"}),
        ([0, bezel, 0], [bezel, 16 - bezel, front],
         {"north": "front", "*": "side"}),
        ([16 - bezel, bezel, 0], [16, 16 - bezel, front],
         {"north": "front", "*": "side"}),

        # Das Schachtfeld, einen Blockpixel hinter der Fassung. Dass es
        # zurückliegt, ist der ganze Punkt: In der Textur war die Vertiefung
        # gemalt, jetzt ist sie da.
        ([bezel, bezel, recess], [16 - bezel, 16 - bezel, front],
         {"north": "front", "*": "side"}),
    ]

    # Vier Füße. Dazwischen sieht man unter das Gerät — daran erkennt man von
    # weitem, dass es steht und nicht in der Wand klebt.
    #
    # <b>Sie stehen unter dem Gehäuse und nicht an den Blockecken.</b> Zuerst
    # taten sie das, und weil das Gehäuse seitlich einen Blockpixel schmaler
    # ist als die Blende, ragte jeder Fuß genau diesen Blockpixel heraus. Von
    # schräg sah das nicht nach Fuß aus, sondern nach abgebrochen. Vorn
    # reichen sie bis an die Blockkante, sonst trüge die Blende sich selbst.
    for x in (inset, 16 - inset - wide):
        for z in (front, 16 - wide):
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


# Der Controller in Blockpixeln.
#
# Deckplatten oben und unten über die volle Fläche, dazwischen ein Körper, der
# ringsum einen Blockpixel zurückspringt — und an den vier senkrechten Kanten
# vier Säulen, die bis an die Blockkante reichen. Von weitem ist es ein Block
# mit Kanten und einem Schatten dazwischen, und nicht mehr ein Würfel mit
# aufgemalten Rillen.
#
# <b>Richtungslos wie zuvor.</b> Der Controller hat keine Vorderseite, und
# diese Form braucht auch keine: Sie sieht von allen vier Seiten gleich aus.
#
# Dieselben Zahlen stehen in ControllerLayout.java.
CONTROLLER_PLATE = 1   # Höhe der Deckplatten
CONTROLLER_INSET = 1   # wie weit der Körper zurückspringt
CONTROLLER_EDGE = 3    # Breite einer Kantensäule


def controller_boxes():
    """Die Kästen des Controllers, jeder mit seinen Texturen."""
    plate, inset, edge = CONTROLLER_PLATE, CONTROLLER_INSET, CONTROLLER_EDGE
    boxes = [
        # Die beiden Deckplatten. Nur ihre Außenseiten tragen die Deckeltextur
        # mit dem Punkt darin; ringsherum ist es Gehäuse wie überall.
        ([0, 0, 0], [16, plate, 16], {"down": "bottom", "*": "side"}),
        ([0, 16 - plate, 0], [16, 16, 16], {"up": "top", "*": "side"}),
        # Der zurückspringende Körper.
        ([inset, plate, inset], [16 - inset, 16 - plate, 16 - inset],
         {"*": "side"}),
    ]
    # Die vier Kantensäulen, bündig mit der Blockkante.
    for x in (0, 16 - edge):
        for z in (0, 16 - edge):
            boxes.append(([x, plate, z], [x + edge, 16 - plate, z + edge],
                          {"*": "side"}))
    return boxes


def controller_model():
    """Der Controller als Gerät statt als Würfel."""
    write(A + "/models/block/controller.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("controller_side"),
            "top": texture("controller_top"),
            "bottom": texture("controller_top"),
            "side": texture("controller_side"),
        },
        "elements": machine_elements(controller_boxes()),
    })


# Das Terminal in Blockpixeln, Vorderseite nach Norden.
#
# Ein Gehäuse, davor ein Rahmen, darin der Bildschirm versenkt — und unten
# eine Konsole, die noch einen Blockpixel weiter vorsteht als der Rahmen. Wo
# in der Textur die Knöpfe sitzen, ist jetzt eine Ablage, auf die man sie
# legen würde.
#
# Dieselben Zahlen stehen in TerminalLayout.java; TerminalLayoutTest hält
# beide zusammen.
TERMINAL_DESK = 2      # wie weit die Konsole vorsteht
TERMINAL_DESK_HIGH = 5  # und wie hoch sie ist
TERMINAL_BEZEL = 2     # Breite des Rahmens um den Bildschirm


def terminal_boxes():
    """Die Kästen des Terminals, jeder mit seinen Texturen."""
    desk, high = TERMINAL_DESK, TERMINAL_DESK_HIGH
    bezel = TERMINAL_BEZEL
    frame = desk - 1     # der Rahmen liegt einen Blockpixel hinter der Konsole
    screen = desk        # und der Bildschirm noch einen dahinter

    back = screen + 1  # dort fängt das Gehäuse an

    # <b>Jedes Frontteil reicht bis ans Gehäuse.</b> Zuerst endeten sie an
    # ihrer eigenen Tiefe, und hinter dem oberen Rahmen und der Konsole
    # klaffte über die volle Breite ein Schlitz — im Spiel ein schwarzer
    # Streifen, durch den man in den Block sieht.
    return [
        # Die Konsole: der unterste Teil der Front, und der einzige, der bis
        # an die Blockkante vorsteht.
        ([0, 0, 0], [16, high, back], {"north": "front", "*": "side"}),

        # Der Rahmen um den Bildschirm — oben und an beiden Seiten.
        ([0, 16 - bezel, frame], [16, 16, back], {"north": "front", "*": "side"}),
        ([0, high, frame], [bezel, 16 - bezel, back],
         {"north": "front", "*": "side"}),
        ([16 - bezel, high, frame], [16, 16 - bezel, back],
         {"north": "front", "*": "side"}),

        # Der Bildschirm, hinter dem Rahmen.
        ([bezel, high, screen], [16 - bezel, 16 - bezel, back],
         {"north": "front", "*": "side"}),

        # Das Gehäuse dahinter, über die volle Breite.
        #
        # <b>Zuerst war es seitlich einen Blockpixel schmaler.</b> Dann hing
        # die Konsole an beiden Seiten über, und über dem Bildschirm klaffte
        # ein Schlitz. Ein Gerät, das an der Wand steht, hat hinten keine
        # Fuge — die Form kommt von vorn, aus Konsole und Rahmen.
        ([0, 0, screen + 1], [16, 16, 16], {"*": "side"}),
    ]


def terminal_model():
    """Das Terminal als Gerät statt als Würfel."""
    write(A + "/models/block/terminal.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("terminal_side"),
            "front": texture("terminal_front"),
            "side": texture("terminal_side"),
        },
        "elements": machine_elements(terminal_boxes()),
    })


# Die Presse in Blockpixeln, Vorderseite nach Norden.
#
# <b>Ein Gehäuse mit einem Loch darin.</b> Zwei Seitenwände, Boden, Decke und
# eine Rückwand — dazwischen ist nichts. Vorn ist der Block offen, und was man
# durch die Öffnung sieht, ist der Amboss unten und der Stempel darüber.
#
# <b>Der Stempel steht nicht in diesem Modell.</b> Er bewegt sich: Solange die
# Presse arbeitet, fährt er herunter und wieder hoch. Ein Blockmodell kann das
# nicht, ein Blockzustand für jede Zwischenstellung wären dreißig Zustände für
# eine Bewegung. Er ist deshalb ein eigenes Modell, das der PressRenderer
# zeichnet — dasselbe Verfahren wie bei den Anschlüssen am Kabel.
#
# Die Zahlen der Front stammen aus `tools/textures.py`: Dort liegt der
# Arbeitsraum zwischen Texturpixel 10 und 54, also zwischen Blockpixel 2,5 und
# 13,5. Drei Blockpixel Wand fangen das.
PRESS_WALL = 3        # Stärke der Wände ringsum
PRESS_BACK = 4        # Stärke der Rückwand
PRESS_ANVIL = 2       # Höhe des Ambosses
PRESS_TOOL_IN = 1     # wie weit Amboss und Stempel hinter der Blockkante liegen
PRESS_TOOL_SIDE = 1   # und wie weit sie schmaler sind als der Hohlraum


def press_boxes():
    """Die Kästen der Presse — das Gehäuse, ohne den beweglichen Stempel."""
    wall, back = PRESS_WALL, PRESS_BACK
    anvil, inset = PRESS_ANVIL, PRESS_TOOL_IN
    outer = {"north": "front", "*": "side"}
    return [
        # Die beiden Seitenwände, über die volle Tiefe.
        ([0, 0, 0], [wall, 16, 16], outer),
        ([16 - wall, 0, 0], [16, 16, 16], outer),
        # Boden und Decke dazwischen.
        ([wall, 0, 0], [16 - wall, wall, 16], outer),
        ([wall, 16 - wall, 0], [16 - wall, 16, 16], outer),
        # Die Rückwand schließt den Hohlraum. Ohne sie sähe man durch den
        # Block hindurch — und griffe beim Zielen daneben.
        ([wall, wall, 16 - back], [16 - wall, 16 - wall, 16], {"*": "side"}),
        # Der Amboss: fest, auf dem Boden, einen Blockpixel hinter der Kante.
        ([wall + PRESS_TOOL_SIDE, wall, inset],
         [16 - wall - PRESS_TOOL_SIDE, wall + anvil, 16 - back],
         {"north": "front", "*": "side"}),
    ]


def press_ram_boxes():
    """Der Stempel — das eine Teil, das sich bewegt.

    Er hängt in Ruhe unter der Decke. Der Renderer schiebt ihn nach unten,
    solange die Presse arbeitet, und bis auf den Amboss, wenn sie fertig ist.
    """
    wall, inset = PRESS_WALL, PRESS_TOOL_IN
    return [
        ([wall + PRESS_TOOL_SIDE, 16 - wall - PRESS_ANVIL, inset],
         [16 - wall - PRESS_TOOL_SIDE, 16 - wall, 16 - PRESS_BACK],
         {"north": "front", "*": "side"}),
    ]


def press_model():
    """Die Presse als Gehäuse mit Hohlraum, und der Stempel dazu."""
    textures = {
        "particle": texture("machine_top"),
        "front": texture("press_front"),
        "side": texture("machine_top"),
    }
    write(A + "/models/block/press.json", {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": machine_elements(press_boxes()),
    })
    # Der Stempel steht allein, ohne Blockzustand: Er gehört keinem Zustand,
    # sondern dem Fortschritt in der BlockEntity. Deshalb meldet ihn
    # FnClient eigens an.
    write(A + "/models/block/press_ram.json", {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": machine_elements(press_ram_boxes()),
    })


# Die Brennkammer in Blockpixeln, Vorderseite nach Norden.
#
# Ein Rahmen, dahinter die Klappe, dahinter das Sichtfenster — und der Griff
# steht als einziger vor der Klappe. Er sitzt links, obwohl die Textur ihn
# rechts malt: Auf einer Nordfläche läuft die Textur andersherum.
BURNER_FRAME = 3     # Breite des Rahmens ringsum
BURNER_DEPTH = 3     # wie tief der Rahmen ist


def burner_boxes():
    """Die Kästen der Brennkammer."""
    frame, deep = BURNER_FRAME, BURNER_DEPTH
    front = {"north": "front", "*": "side"}
    return [
        # Das Gehäuse. Über die volle Breite — eine Brennkammer steht in der
        # Wand und nicht frei.
        ([0, 0, deep], [16, 16, 16], {"*": "side"}),

        # Der Rahmen ringsum.
        ([0, 0, 0], [16, frame, deep], front),
        ([0, 16 - frame, 0], [16, 16, deep], front),
        ([0, frame, 0], [frame, 16 - frame, deep], front),
        ([16 - frame, frame, 0], [16, 16 - frame, deep], front),

        # Die Klappe, einen Blockpixel hinter dem Rahmen.
        ([frame, frame, 1], [16 - frame, 16 - frame, deep - 1], front),

        # Das Sichtfenster, noch einen dahinter.
        ([5, 5, deep - 1], [11, 11, deep], front),

        # Der Griff, als einziger vor der Klappe.
        ([frame, 7, 0], [frame + 1, 9, 1], front),
    ]


def burner_model():
    """Die Brennkammer, kalt und brennend — dieselbe Form, zwei Texturen."""
    for name, front in (("burner", "burner_front"),
                        ("burner_on", "burner_front_on")):
        write(A + "/models/block/%s.json" % name, {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": texture("machine_top"),
                "front": texture(front),
                "side": texture("machine_top"),
            },
            "elements": machine_elements(burner_boxes()),
        })


# Der Fabricator in Blockpixeln.
#
# Ein Deckel über die volle Fläche, darauf ein abgesetzter Aufbau, darunter
# ein Körper, der ringsum zurückspringt, und ein Sockel. Anders als beim
# Controller gibt es keine Kantensäulen — sonst sähen beide gleich aus, und
# sie tun Verschiedenes.
FABRICATOR_BASE = 2   # Höhe des Sockels
FABRICATOR_LID = 3    # Höhe des Deckels samt Aufbau
FABRICATOR_INSET = 1  # wie weit der Körper zurückspringt
FABRICATOR_TOP = 2    # wie weit der Aufbau schmaler ist als der Deckel


def fabricator_boxes():
    """Die Kästen des Fabricators."""
    base, lid = FABRICATOR_BASE, FABRICATOR_LID
    inset, top = FABRICATOR_INSET, FABRICATOR_TOP
    return [
        ([0, 0, 0], [16, base, 16], {"down": "bottom", "*": "side"}),
        ([inset, base, inset], [16 - inset, 16 - lid, 16 - inset], {"*": "side"}),
        ([0, 16 - lid, 0], [16, 16 - 1, 16], {"*": "side"}),
        ([top, 16 - 1, top], [16 - top, 16, 16 - top], {"up": "top", "*": "side"}),
    ]


def fabricator_model():
    """Der Fabricator als Gerät statt als Würfel."""
    write(A + "/models/block/fabricator.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("fabricator_side"),
            "top": texture("fabricator_top"),
            "bottom": texture("controller_extension"),
            "side": texture("fabricator_side"),
        },
        "elements": machine_elements(fabricator_boxes()),
    })


# Der Controller-Anbau in Blockpixeln.
#
# Ein Käfig: zwölf Leisten auf den Blockkanten, dazwischen ein Kern, der
# ringsum einen Blockpixel zurückspringt. Auf allen sechs Seiten dasselbe —
# er hat keine Vorderseite, und diese Form braucht auch keine.
EXTENSION_EDGE = 1    # Stärke einer Kantenleiste


def extension_boxes():
    """Die Kästen des Anbaus: der Kern und die zwölf Kanten."""
    edge = EXTENSION_EDGE
    far = 16 - edge
    boxes = [([edge, edge, edge], [far, far, far], {"*": "all"})]

    # Die vier senkrechten Leisten stehen in den Ecken; die waagerechten
    # fangen dahinter an, damit sich keine zwei überlappen. Zwei Flächen in
    # derselben Ebene flimmern im Spiel, und zwar nur aus manchen Winkeln.
    for x in (0, far):
        for z in (0, far):
            boxes.append(([x, 0, z], [x + edge, 16, z + edge], {"*": "all"}))
    for y in (0, far):
        for z in (0, far):
            boxes.append(([edge, y, z], [far, y + edge, z + edge], {"*": "all"}))
        for x in (0, far):
            boxes.append(([x, y, edge], [x + edge, y + edge, far], {"*": "all"}))
    return boxes


def extension_model():
    """Der Anbau als Käfig statt als Würfel."""
    write(A + "/models/block/controller_extension.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("controller_extension"),
            "all": texture("controller_extension"),
        },
        "elements": machine_elements(extension_boxes()),
    })


# Die Kreativquelle in Blockpixeln.
#
# Ein Kern, der ringsum zurückspringt, und acht Eckklötze, die bis an die
# Blockkante reichen. Von weitem ein Behälter mit verstärkten Ecken — und
# damit ohne Verwechslung mit dem Anbau, der seine Kanten führt statt seiner
# Ecken.
SOURCE_CORNER = 3   # Kantenlänge eines Eckklotzes
SOURCE_INSET = 1    # wie weit der Kern zurückspringt


def source_boxes():
    """Die Kästen der Kreativquelle."""
    corner, inset = SOURCE_CORNER, SOURCE_INSET
    boxes = [([inset, inset, inset], [16 - inset, 16 - inset, 16 - inset],
              {"*": "all"})]
    for x in (0, 16 - corner):
        for y in (0, 16 - corner):
            for z in (0, 16 - corner):
                boxes.append(([x, y, z], [x + corner, y + corner, z + corner],
                              {"*": "all"}))
    return boxes


def source_model():
    """Die Kreativquelle als Behälter statt als Würfel."""
    write(A + "/models/block/creative_source.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("creative_source"),
            "all": texture("creative_source"),
        },
        "elements": machine_elements(source_boxes()),
    })


# Der Router in Blockpixeln.
#
# Ein Käfig aus zwölf Leisten, vier Blockpixel stark, und dazwischen ein Kern,
# der zwei zurückspringt. Aus der aufgemalten Buchse wird damit eine echte —
# und ein dickes Kabel, das die mittleren zehn Blockpixel einnimmt, steckt
# darin statt davorzukleben.
#
# <b>Die drei Blockpixel sind gemessen und nicht gewählt.</b> Der Renderer
# malt die Bahnkennung über die volle Fläche jeder Seite; ihr Ring läuft in
# der Textur von Blockpixel 1 bis 3 und von 12,75 bis 14,75, dazwischen ist
# sie durchsichtig. Drei Blockpixel Leiste decken den inneren Rand also genau
# ab. Dünner, und der Ring schwebt; dicker, und die Leiste verdeckt aus
# schrägem Blick die vier Kontakte in der Mitte — bei vier sah man nur noch
# einen.
ROUTER_EDGE = 3     # Stärke einer Kantenleiste
ROUTER_INSET = 1    # wie weit der Kern zurückspringt


def router_boxes():
    """Die Kästen des Routers: Kern, zwölf Kanten und sechs Kontaktplatten.

    <b>Zwei Versuche liegen dahinter.</b> Der erste sprang zwei Blockpixel
    zurück und hatte in jeder Seite ein Loch, das aussah wie ein Loch. Der
    zweite setzte eine Platte in die Mitte, damit dort wieder Material sitzt
    — und zeigte damit den Kragen der Textur ein zweites Mal, um einen
    Blockpixel versetzt gegen den, der schon auf dem Kern lag.

    Ein Blockpixel Vertiefung reicht. Jede Fläche zeigt die Textur dann genau
    einmal und an der Stelle, an der sie gemalt ist.
    """
    edge, inset = ROUTER_EDGE, ROUTER_INSET
    far = 16 - edge
    boxes = [([inset, inset, inset], [16 - inset, 16 - inset, 16 - inset],
              {"*": "all"})]
    for x in (0, far):
        for z in (0, far):
            boxes.append(([x, 0, z], [x + edge, 16, z + edge], {"*": "all"}))
    for y in (0, far):
        for z in (0, far):
            boxes.append(([edge, y, z], [far, y + edge, z + edge], {"*": "all"}))
        for x in (0, far):
            boxes.append(([x, y, edge], [x + edge, y + edge, far], {"*": "all"}))

    return boxes


def router_model():
    """Der Router als Käfig statt als Würfel."""
    write(A + "/models/block/router.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("router_side"),
            "all": texture("router_side"),
        },
        "elements": machine_elements(router_boxes()),
    })


# Die Gegenstände als Körper statt als Blatt Papier.
#
# <b>Was Minecraft von allein macht.</b> Ein Modell mit dem Vorfahren
# {@code item/generated} zieht die Textur einen Blockpixel in die Tiefe und
# schneidet die Silhouette aus dem Alphakanal. Das ist für einen Schlüssel
# oder eine Feder genau richtig — für eine Speicherzelle, die ein Gehäuse aus
# Blech ist, sieht es aus wie ein Aufkleber.
#
# <b>Was hier stattdessen steht.</b> Jeder Gegenstand ist ein Quader in der
# Größe seines Umrisses. Vorder- und Rückseite tragen die Textur an ihrem
# Platz, die vier Kanten je einen Blockpixel vom Rand der Textur — dort ist
# bei allen das Gehäuse, und ein Streifen davon liest sich als Blech. Eine
# Seitenansicht malt keine dieser Texturen, und eine zu erfinden hieße, für
# vierzig Gegenstände eine zweite Textur zu pflegen.
#
# <b>Wer hier fehlt, fehlt mit Grund.</b> Kristall und Rohkristall sind keine
# Quader, und die Beschriftungspistole und der Analysator sind Werkzeuge mit
# einer Silhouette, die ein Kasten nicht trifft. Für die vier bleibt die
# Extrusion die bessere Antwort.
#
# Die Umrisse sind aus dem Alphakanal der Texturen gemessen, auf ganze
# Blockpixel nach außen gerundet.
ITEM_BODIES = {
    # Speicherzellen: ein Gehäuse, und das darf man sehen.
    "cell_k1": (3, 2, 13, 14, 4),
    "cell_k4": (3, 2, 13, 14, 4),
    "cell_k16": (3, 2, 13, 14, 4),
    "cell_k64": (3, 2, 13, 14, 4),
    "fluid_cell_64": (3, 2, 13, 14, 4),
    "fluid_cell_256": (3, 2, 13, 14, 4),
    "fluid_cell_1024": (3, 2, 13, 14, 4),
    "fluid_cell_4096": (3, 2, 13, 14, 4),
    "chemical_cell_64k": (3, 2, 13, 14, 4),
    "chemical_cell_256k": (3, 2, 13, 14, 4),
    "chemical_cell_1024k": (3, 2, 13, 14, 4),
    "chemical_cell_4096k": (3, 2, 13, 14, 4),
    "energy_cell_64k": (3, 2, 13, 14, 4),
    "energy_cell_256k": (3, 2, 13, 14, 4),
    "energy_cell_1024k": (3, 2, 13, 14, 4),
    "energy_cell_4096k": (3, 2, 13, 14, 4),

    # Bauteile: flacher, sie sind Platinen und keine Gehäuse.
    "cpu_2": (2, 2, 14, 14, 2),
    "cpu_8": (2, 2, 14, 14, 2),
    "cpu_32": (2, 2, 14, 14, 2),
    "cpu_128": (2, 2, 14, 14, 2),
    "ram_8": (0, 3, 16, 13, 2),
    "ram_32": (0, 3, 15, 13, 2),
    "ram_128": (0, 3, 16, 13, 2),
    "ram_512": (0, 3, 15, 12, 2),
    "disk_64": (2, 3, 14, 13, 2),
    "disk_256": (2, 3, 14, 13, 2),
    "disk_1024": (2, 3, 14, 13, 2),
    "disk_4096": (2, 3, 14, 13, 2),
    "core_logic": (2, 3, 15, 13, 2),
    "core_memory": (2, 3, 15, 13, 2),
    "core_network": (2, 3, 15, 13, 2),

    # Die Ausbauten. Eine Karte ist einen Blockpixel dick, ein Modul zwei:
    # Es ist ein Gerät, sie ist eine Platine.
    #
    # Der Umriss des Moduls reicht bis Blockpixel 1 hinauf, weil die Antenne
    # dort steht. Was um sie herum durchsichtig ist, fällt an den Kanten von
    # selbst weg — sie schneiden ihren Streifen aus demselben Rand.
    "range_card": (3, 4, 13, 12, 1),
    "infinity_card": (3, 4, 13, 12, 1),
    "wireless_module": (3, 1, 13, 12, 2),

    # Der Rest: Blech ist dünn, ein Stempel ist ein Werkzeug.
    "plate": (3, 4, 14, 12, 1),
    "server_chassis": (1, 4, 15, 12, 3),
    "stamp_plate": (3, 2, 13, 12, 4),
    "stamp_logic": (3, 2, 13, 12, 4),
    "stamp_memory": (3, 2, 13, 12, 4),
    "stamp_network": (3, 2, 13, 12, 4),
}


# Die Ansichten, die minecraft:item/generated mitbringt — Wort für Wort
# dieselben Zahlen.
#
# <b>Warum abgeschrieben und nicht geerbt.</b> Wer item/generated als
# Vorfahren nimmt, bekommt nicht nur die Ansichten, sondern auch dessen
# Bauart: Minecraft erkennt den Vorfahren builtin/generated und baut die
# Quads aus layer0. Ein Kind mit eigenen Kästen und ohne layer0 hätte danach
# gar keine Flächen mehr und wäre in der Hand unsichtbar. Der Serverschrank
# macht es seit jeher so — kein Vorfahre, eigene Ansichten.
ITEM_DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0],
               "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7],
             "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1],
                              "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1],
                             "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25],
                              "translation": [1.13, 3.2, 1.13],
                              "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25],
                             "translation": [1.13, 3.2, 1.13],
                             "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0],
              "scale": [1, 1, 1]},
}


def item_model(name, parent="minecraft:item/generated"):
    """Das Modell eines Gegenstands.

    Steht er in ITEM_BODIES, wird er ein Quader; sonst bleibt es bei der
    Extrusion, die Minecraft aus dem Alphakanal rechnet.
    """
    if name not in ITEM_BODIES:
        write(A + "/models/item/%s.json" % name, {
            "parent": parent,
            "textures": {"layer0": MOD + ":item/" + name},
        })
        return

    x0, y0, x1, y1, deep = ITEM_BODIES[name]
    near = (16 - deep) // 2
    far = near + deep

    def face(u0, v0, u1, v1):
        return {"texture": "#face", "uv": [u0, v0, u1, v1]}

    write(A + "/models/item/%s.json" % name, {
        "display": ITEM_DISPLAY,
        "textures": {
            "face": MOD + ":item/" + name,
            "particle": MOD + ":item/" + name,
        },
        "elements": [{
            # Die y-Achse zählt im Modell von unten, in der Textur von oben —
            # deshalb der Tausch.
            "from": [x0, 16 - y1, near],
            "to": [x1, 16 - y0, far],
            "faces": {
                "south": face(x0, y0, x1, y1),
                "north": face(16 - x1, y0, 16 - x0, y1),
                "west": face(x0, y0, x0 + 1, y1),
                "east": face(x1 - 1, y0, x1, y1),
                "up": face(x0, y0, x1, y0 + 1),
                "down": face(x0, y1 - 1, x1, y1),
            },
        }],
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
    cable_models()
    connector_part_models()
    gateway_model()
    drive_model()
    controller_model()
    terminal_model()
    press_model()
    burner_model()
    fabricator_model()
    extension_model()
    source_model()
    router_model()

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
    # Die beiden Werkzeuge bleiben flach: Ihre Silhouette trifft kein Kasten.
    item_model("label_gun", "minecraft:item/handheld")
    item_model("network_analyser", "minecraft:item/handheld")
    for tier in ("k1", "k4", "k16", "k64"):
        item_model("cell_" + tier)
    for tier in ("64", "256", "1024", "4096"):
        item_model("fluid_cell_" + tier)
    for tier in ("64k", "256k", "1024k", "4096k"):
        item_model("energy_cell_" + tier)
    for tier in ("64k", "256k", "1024k", "4096k"):
        item_model("chemical_cell_" + tier)
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
    item_model("server_chassis")
    for kind, tiers in SERVER_PARTS.items():
        for value in tiers:
            name = "%s_%d" % (kind, value)
            item_model(name)
    for name in ("crystal", "plate", "stamp_plate", "stamp_logic", "stamp_memory",
                 "stamp_network", "core_logic", "core_memory", "core_network"):
        item_model(name)
    write(A + "/models/item/press.json", {"parent": block("press")})
    item_model("raw_crystal")
    for name in ("range_card", "infinity_card", "wireless_module"):
        item_model(name)


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

    # Die Reichweitenkarte: Kupfer außen, ein Kristall in der Mitte, Platten
    # als Boden. Zwei je Handgriff — man braucht selten nur eine.
    write(D + "/recipe/range_card.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["CCC", "CKC", "PPP"],
        "key": {
            "C": {"item": "minecraft:copper_ingot"},
            "K": {"item": MOD + ":crystal"},
            "P": {"item": MOD + ":plate"},
        },
        "result": {"id": MOD + ":range_card", "count": 2},
    })

    # Die Grenzenlos-Karte: vier Reichweitenkarten um einen Netzkern.
    write(D + "/recipe/infinity_card.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["RKR", "KNK", "RKR"],
        "key": {
            "R": {"item": MOD + ":range_card"},
            "K": {"item": MOD + ":crystal"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":infinity_card", "count": 1},
    })

    # Das Funk-Modul: eine Reichweitenkarte in einem Gehäuse aus Platten.
    write(D + "/recipe/wireless_module.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" P ", "PRP", " P "],
        "key": {
            "P": {"item": MOD + ":plate"},
            "R": {"item": MOD + ":range_card"},
        },
        "result": {"id": MOD + ":wireless_module", "count": 1},
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
