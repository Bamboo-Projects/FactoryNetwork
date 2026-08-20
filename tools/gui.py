# -*- coding: utf-8 -*-
"""Erzeugt die Oberflächentexturen im Minecraft-Stil.

Anders als die Blocktexturen bleibt das hier bewusst bei Vanilla: dieselben
Grautöne, dieselbe Kantenlogik, dasselbe 18er-Slotraster. Ein Inventar soll
sich anfühlen wie jedes andere in Minecraft — was daran eigen ist, verwirrt
nur.

Die Ausnahme ist der Bildschirm im Code-Reiter. Der ist kein Inventar,
sondern ein Gerät, und sitzt als dunkle Fläche im hellen Gehäuse.
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "factorynetwork", "textures", "gui")

# Vanillas Grautöne, abgelesen aus den Container-Texturen.
PANEL      = (198, 198, 198)
PANEL_HI   = (255, 255, 255)
PANEL_LO   = (85, 85, 85)
PANEL_DARK = (139, 139, 139)
SLOT_BG    = (139, 139, 139)
SLOT_HI    = (255, 255, 255)
SLOT_LO    = (55, 55, 55)
TEXT       = (64, 64, 64)
SCREEN     = (22, 26, 24)
SCREEN_EDGE = (10, 13, 11)


def panel(draw, box):
    """Erhabene Fläche: oben und links hell, unten und rechts dunkel."""
    x0, y0, x1, y1 = box
    draw.rectangle(box, fill=PANEL + (255,))
    draw.line([(x0, y0), (x1 - 1, y0)], fill=PANEL_HI + (255,))
    draw.line([(x0, y0), (x0, y1 - 1)], fill=PANEL_HI + (255,))
    draw.line([(x0 + 1, y1), (x1, y1)], fill=PANEL_LO + (255,))
    draw.line([(x1, y0 + 1), (x1, y1)], fill=PANEL_LO + (255,))
    # Vanilla setzt in die Ecken je einen Übergangston
    draw.point((x1, y0), fill=PANEL_DARK + (255,))
    draw.point((x0, y1), fill=PANEL_DARK + (255,))


def sunken(draw, box, fill=SLOT_BG, light=SLOT_HI, shadow=SLOT_LO):
    """Versenkte Fläche: oben und links dunkel, unten und rechts hell."""
    x0, y0, x1, y1 = box
    draw.rectangle(box, fill=fill + (255,))
    draw.line([(x0, y0), (x1 - 1, y0)], fill=shadow + (255,))
    draw.line([(x0, y0), (x0, y1 - 1)], fill=shadow + (255,))
    draw.line([(x0 + 1, y1), (x1, y1)], fill=light + (255,))
    draw.line([(x1, y0 + 1), (x1, y1)], fill=light + (255,))


def slot(draw, x, y):
    """Ein Slot ist 18x18 mit einem Pixel Rand — das Innere misst 16x16."""
    sunken(draw, (x, y, x + 17, y + 17))


# Maße des Fensters.
#
# Deutlich breiter als eine Kiste: Ein Code-Editor bei 176 Bildpunkten zeigt
# gut zwanzig Zeichen je Zeile, und die Reiterbeschriftungen wären auf vier
# Buchstaben abgeschnitten. Das Spielerinventar bleibt neun Slots breit und
# sitzt mittig darunter — es soll aussehen wie überall, nur eben in einem
# größeren Gehäuse.
WIDTH = 288
HEIGHT = 236
ATLAS = 512

# Das Inventar mittig, an Vanillas Raster ausgerichtet.
INV_COLUMNS = 9
INV_SLOT = 18
INV_LEFT = (WIDTH - INV_COLUMNS * INV_SLOT) // 2

# Das Spielerinventar sitzt an den Stellen, an denen es in Minecraft immer
# sitzt — Spieler greifen blind dorthin.
INV_X = INV_LEFT
INV_Y = HEIGHT - 82      # drei Reihen plus Schnellzugriff
HOTBAR_Y = HEIGHT - 24

# Der Arbeitsbereich darüber
TAB_HEIGHT = 18
WORK_X = 7
WORK_Y = TAB_HEIGHT + 4
WORK_W = WIDTH - 14
WORK_H = INV_Y - WORK_Y - 14


def background():
    """Das Fenster: Gehäuse, Reiterleiste, Arbeitsfläche, Spielerinventar."""
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    panel(d, (0, 0, WIDTH - 1, HEIGHT - 1))

    # Reiterleiste: eine versenkte Rinne, in der die Reiter sitzen
    sunken(d, (4, 3, WIDTH - 5, TAB_HEIGHT), fill=PANEL_DARK)

    # Arbeitsfläche
    sunken(d, (WORK_X, WORK_Y, WORK_X + WORK_W, WORK_Y + WORK_H), fill=PANEL_DARK)

    # Beschriftung des Spielerinventars steht bei Vanilla immer links darüber
    for row in range(3):
        for column in range(9):
            slot(d, INV_X - 1 + column * 18, INV_Y - 1 + row * 18)
    for column in range(9):
        slot(d, INV_X - 1 + column * 18, HOTBAR_Y - 1)
    return img


def slot_grid():
    """Das Raster für den Netzbestand — sieben Reihen zu vierzehn Slots.

    Eigene Textur, weil der Bestand keine echten Slots sein kann: Zwanzig-
    tausend Arten lassen sich nicht anlegen. Sie sehen aber aus wie Slots,
    damit sie sich anfühlen wie Slots.
    """
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for row in range(7):
        for column in range(14):
            slot(d, column * 18, row * 18)
    return img


def screen():
    """Der Bildschirm des Code-Reiters, versenkt ins Gehäuse."""
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sunken(d, (0, 0, WORK_W, WORK_H), fill=SCREEN,
           light=(96, 110, 100), shadow=SCREEN_EDGE)
    return img


def widgets():
    """Kleinteile: Reiter aktiv und inaktiv, Knopf, Suchfeld, Rollbalken."""
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Reiter inaktiv (0,0) und aktiv (0,16), je 32x16
    panel(d, (0, 0, 31, 15))
    d.rectangle((0, 0, 31, 15), outline=PANEL_LO + (255,))
    panel(d, (0, 16, 31, 31))
    d.line([(1, 31), (30, 31)], fill=PANEL + (255,))

    # Knopf normal (32,0) und gedrückt (32,16), je 48x16
    panel(d, (32, 0, 79, 15))
    sunken(d, (32, 16, 79, 31), fill=PANEL_DARK)

    # Suchfeld (0,32), 96x12
    sunken(d, (0, 32, 95, 43), fill=(30, 34, 31),
           light=(96, 110, 100), shadow=SCREEN_EDGE)

    # Rollbalken (96,32), 12x15 — Griff und Rinne
    panel(d, (96, 32, 107, 46))
    sunken(d, (108, 32, 119, 46), fill=PANEL_DARK)
    return img


def save(image, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    image.save(path)
    print("  gui/%s.png  %d B" % (name, os.path.getsize(path)))


def main():
    print("Oberflächentexturen:")
    save(background(), "terminal")
    save(slot_grid(), "storage_grid")
    save(screen(), "screen")
    save(widgets(), "widgets")
    print("Maße: Fenster %dx%d, Arbeitsfläche %dx%d bei %d,%d"
          % (WIDTH, HEIGHT, WORK_W, WORK_H, WORK_X, WORK_Y))
    print("      Inventar bei %d,%d · Schnellzugriff bei %d,%d"
          % (INV_X, INV_Y, INV_X, HOTBAR_Y))


if __name__ == "__main__":
    main()
