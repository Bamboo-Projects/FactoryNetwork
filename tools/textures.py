# -*- coding: utf-8 -*-
"""Erzeugt die Texturen von Factory Network.

64x64, also vierfache Vanilla-Auflösung. Bei dieser Größe gelten andere
Regeln als bei 16x16: Verläufe tragen, Kanten dürfen mehrstufig sein, und ein
Leuchten braucht einen Hof, sonst wirkt es aufgeklebt.

Die Farbe ist dasselbe Grün, mit dem der Code-Editor Auswahlausdrücke
einfärbt — Block und Programm sprechen dieselbe Sprache.

Jeder Block hat eine eigene Formensprache, damit sich die Seitenflächen
unterscheiden lassen: Der Controller trägt eine querlaufende Fuge, das
Terminal senkrechte Streben, der Connector einen Rahmen mit Ecken, das Kabel
eine durchgehende Längsstruktur.
"""
import os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "factorynetwork", "textures")
N = 64

# ---- Palette -------------------------------------------------------------
EDGE       = (10, 13, 11)
BODY_TOP   = (41, 48, 44)
BODY_BOT   = (24, 29, 26)
BODY_MID   = (33, 39, 35)
LIGHT      = (70, 82, 74)
SHINE      = (96, 110, 100)
ACCENT     = (120, 220, 140)
ACCENT_HI  = (205, 255, 215)
ACCENT_DIM = (24, 78, 44)
BRASS      = (196, 154, 74)
BRASS_HI   = (236, 200, 130)
WOOD       = (74, 56, 38)
WOOD_HI    = (104, 80, 54)


def blend(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def surface(top=BODY_TOP, bottom=BODY_BOT):
    """Grundfläche mit senkrechtem Verlauf — Licht kommt von oben."""
    img = Image.new("RGBA", (N, N), top + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(N):
        draw.line([(0, y), (N - 1, y)], fill=blend(top, bottom, y / (N - 1)) + (255,))
    return img


def bevel(draw, box=(0, 0, N - 1, N - 1), width=3, light=LIGHT, shadow=EDGE):
    """Mehrstufige Fase. Eine einzelne Linie trägt bei 64 Pixeln nicht."""
    x0, y0, x1, y1 = box
    for i in range(width):
        t = i / max(1, width)
        draw.line([(x0 + i, y0 + i), (x1 - i, y0 + i)], fill=blend(light, shadow, t) + (255,))
        draw.line([(x0 + i, y0 + i), (x0 + i, y1 - i)], fill=blend(light, shadow, t) + (255,))
        draw.line([(x0 + i, y1 - i), (x1 - i, y1 - i)], fill=blend(shadow, light, t) + (255,))
        draw.line([(x1 - i, y0 + i), (x1 - i, y1 - i)], fill=blend(shadow, light, t) + (255,))


def glow(image, box, color=ACCENT, radius=6, strength=170):
    """Legt einen Leuchthof unter ein leuchtendes Bauteil."""
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rectangle(box, fill=color + (strength,))
    image.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))


def save(image, folder, name):
    path = os.path.join(OUT, folder, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("  %s/%s.png  %d B" % (folder, name, os.path.getsize(path)))


# ---- Controller ----------------------------------------------------------
# Formensprache: querlaufende Fuge, Kern mit vier Leitungen.

def controller_top():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Vier Leitungen von den Kanten zum Kern
    for box in [(30, 3, 33, 18), (30, 45, 33, 60), (3, 30, 18, 33), (45, 30, 60, 33)]:
        d.rectangle(box, fill=EDGE + (255,))
        d.rectangle((box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1), fill=ACCENT_DIM + (255,))
    # Versenktes Feld
    d.rectangle([16, 16, 47, 47], fill=EDGE + (255,))
    d.rectangle([18, 18, 45, 45], fill=ACCENT_DIM + (255,))
    # Kern
    glow(img, [20, 20, 43, 43], radius=7)
    d = ImageDraw.Draw(img)
    d.rectangle([21, 21, 42, 42], fill=ACCENT + (255,))
    d.rectangle([25, 25, 38, 38], fill=ACCENT_HI + (255,))
    d.rectangle([21, 21, 42, 42], outline=ACCENT_HI + (120,))
    return img


def controller_side():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Lüftungslamellen in der oberen Hälfte
    for y in range(14, 31, 5):
        d.rectangle([12, y, 51, y + 1], fill=EDGE + (255,))
        d.line([(12, y + 2), (51, y + 2)], fill=LIGHT + (255,))
    # Trennfuge quer mit Leuchtlinie — das Kennzeichen des Controllers
    d.rectangle([0, 36, N - 1, 39], fill=EDGE + (255,))
    glow(img, [10, 37, 53, 38], radius=4)
    d = ImageDraw.Draw(img)
    d.rectangle([10, 37, 53, 38], fill=ACCENT + (255,))
    # Typenschild
    d.rectangle([16, 46, 47, 55], fill=EDGE + (255,))
    d.rectangle([17, 47, 46, 54], fill=blend(BODY_BOT, EDGE, 0.5) + (255,))
    for y in (49, 52):
        d.line([(20, y), (36, y)], fill=LIGHT + (255,))
    return img


# ---- Kabel ---------------------------------------------------------------
# Formensprache: durchgehende Längsstruktur, keine Rahmen.

def cable():
    img = surface(BODY_MID, BODY_BOT)
    d = ImageDraw.Draw(img)
    # Drei Röhren nebeneinander, jede mit Glanzkante
    for x in (6, 26, 46):
        d.rectangle([x, 0, x + 11, N - 1], fill=BODY_TOP + (255,))
        d.line([(x, 0), (x, N - 1)], fill=EDGE + (255,))
        d.line([(x + 1, 0), (x + 1, N - 1)], fill=SHINE + (255,))
        d.line([(x + 11, 0), (x + 11, N - 1)], fill=EDGE + (255,))
    # Schellen quer, damit die Röhren gehalten wirken
    for y in (14, 46):
        d.rectangle([0, y, N - 1, y + 3], fill=EDGE + (255,))
        d.line([(0, y + 1), (N - 1, y + 1)], fill=LIGHT + (255,))
    return img


# ---- Connector -----------------------------------------------------------
# Formensprache: Rahmen mit betonten Ecken, runder Anschluss.

def connector_front():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Versenkte Platte
    d.rectangle([10, 10, 53, 53], fill=EDGE + (255,))
    d.rectangle([12, 12, 51, 51], fill=blend(BODY_TOP, EDGE, 0.35) + (255,))
    # Anschlussring
    d.ellipse([16, 16, 47, 47], fill=EDGE + (255,))
    d.ellipse([18, 18, 45, 45], fill=blend(LIGHT, EDGE, 0.3) + (255,))
    d.ellipse([21, 21, 42, 42], fill=EDGE + (255,))
    # Leuchtender Kern im Ring
    glow(img, [25, 25, 38, 38], radius=6)
    d = ImageDraw.Draw(img)
    d.ellipse([24, 24, 39, 39], fill=ACCENT + (255,))
    d.ellipse([28, 28, 35, 35], fill=ACCENT_HI + (255,))
    # Vier Schrauben in den Ecken der Platte
    for cx, cy in ((15, 15), (48, 15), (15, 48), (48, 48)):
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=EDGE + (255,))
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=SHINE + (255,))
    return img


def connector_side():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Umlaufender Rahmen mit betonten Ecken — das Kennzeichen des Connectors
    d.rectangle([8, 8, 55, 55], outline=EDGE + (255,), width=2)
    d.rectangle([10, 10, 53, 53], outline=LIGHT + (255,))
    for cx, cy in ((8, 8), (55, 8), (8, 55), (55, 55)):
        d.rectangle([cx - 3, cy - 3, cx + 3, cy + 3], fill=blend(BODY_TOP, LIGHT, 0.5) + (255,))
        d.rectangle([cx - 3, cy - 3, cx + 3, cy + 3], outline=EDGE + (255,))
    # Kleine Statusanzeige, damit die Seite nicht leer wirkt
    d.rectangle([28, 28, 35, 35], fill=EDGE + (255,))
    d.rectangle([30, 30, 33, 33], fill=ACCENT_DIM + (255,))
    return img


def connector_back():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Kabeldurchführung
    d.ellipse([18, 18, 45, 45], fill=EDGE + (255,))
    d.ellipse([21, 21, 42, 42], fill=blend(BODY_BOT, EDGE, 0.5) + (255,))
    d.ellipse([26, 26, 37, 37], fill=EDGE + (255,))
    for angle_box in ([30, 12, 33, 20], [30, 43, 33, 51], [12, 30, 20, 33], [43, 30, 51, 33]):
        d.rectangle(angle_box, fill=LIGHT + (255,))
    return img


def machine_top():
    """Deckel, den Connector und Terminal teilen. Bewusst ruhig."""
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    d.rectangle([12, 12, 51, 51], outline=EDGE + (255,))
    d.rectangle([13, 13, 50, 50], outline=blend(BODY_TOP, LIGHT, 0.4) + (255,))
    d.rectangle([26, 26, 37, 37], fill=blend(BODY_BOT, EDGE, 0.4) + (255,))
    d.rectangle([26, 26, 37, 37], outline=EDGE + (255,))
    return img


# ---- Terminal ------------------------------------------------------------
# Formensprache: senkrechte Streben, Bildschirm mit Codezeilen.

def terminal_front():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Bildschirmrahmen
    d.rectangle([5, 5, 58, 46], fill=EDGE + (255,))
    d.rectangle([7, 7, 56, 44], fill=(14, 20, 16) + (255,))
    # Codezeilen, in denselben Farben wie im Editor
    rows = [(12, 12, 30, ACCENT), (12, 18, 42, blend(LIGHT, SHINE, 0.6)),
            (18, 24, 34, ACCENT_HI), (18, 30, 26, blend(LIGHT, SHINE, 0.6)),
            (12, 36, 20, ACCENT)]
    for x, y, length, color in rows:
        d.rectangle([x, y, x + length, y + 2], fill=color + (255,))
    glow(img, [12, 12, 42, 38], radius=8, strength=60)
    d = ImageDraw.Draw(img)
    # Bedienleiste unter dem Bildschirm
    d.rectangle([5, 50, 58, 58], fill=EDGE + (255,))
    for x in range(10, 52, 8):
        d.rectangle([x, 52, x + 4, 56], fill=blend(LIGHT, EDGE, 0.3) + (255,))
    d.rectangle([50, 52, 54, 56], fill=ACCENT + (255,))
    return img


def terminal_side():
    img = surface()
    d = ImageDraw.Draw(img)
    bevel(d)
    # Senkrechte Streben — das Kennzeichen des Terminals
    for x in (14, 30, 46):
        d.rectangle([x, 8, x + 5, 55], fill=EDGE + (255,))
        d.rectangle([x + 1, 9, x + 4, 54], fill=blend(BODY_TOP, LIGHT, 0.35) + (255,))
        d.line([(x + 1, 9), (x + 1, 54)], fill=SHINE + (255,))
    # Fussleiste
    d.rectangle([0, 56, N - 1, N - 1], fill=EDGE + (255,))
    d.line([(0, 57), (N - 1, 57)], fill=LIGHT + (255,))
    return img


# ---- Label-Gun -----------------------------------------------------------

def label_gun():
    """Ein Gegenstand lebt von der Silhouette, nicht von der Fläche."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Griff
    d.polygon([(14, 56), (14, 42), (26, 34), (30, 46), (22, 56)],
              fill=WOOD + (255,), outline=EDGE + (255,))
    d.line([(17, 52), (17, 44)], fill=WOOD_HI + (255,))
    # Korpus
    d.polygon([(22, 38), (44, 18), (54, 26), (34, 46)],
              fill=blend(BODY_TOP, LIGHT, 0.4) + (255,), outline=EDGE + (255,))
    d.line([(26, 36), (45, 22)], fill=SHINE + (255,))
    # Mündung in Messing
    d.polygon([(43, 14), (55, 24), (51, 31), (39, 21)],
              fill=BRASS + (255,), outline=EDGE + (255,))
    d.line([(45, 18), (52, 25)], fill=BRASS_HI + (255,))
    # Anzeige am Korpus
    glow(img, [29, 33, 36, 39], radius=4, strength=140)
    d = ImageDraw.Draw(img)
    d.rectangle([29, 33, 36, 39], fill=EDGE + (255,))
    d.rectangle([30, 34, 35, 38], fill=ACCENT + (255,))
    return img


def main():
    print("Blocktexturen (64x64):")
    save(controller_top(), "block", "controller_top")
    save(controller_side(), "block", "controller_side")
    save(cable(), "block", "cable")
    save(connector_front(), "block", "connector_front")
    save(connector_side(), "block", "connector_side")
    save(connector_back(), "block", "connector_back")
    save(machine_top(), "block", "machine_top")
    save(terminal_front(), "block", "terminal_front")
    save(terminal_side(), "block", "terminal_side")
    print("Gegenstandstexturen:")
    save(label_gun(), "item", "label_gun")


if __name__ == "__main__":
    main()
