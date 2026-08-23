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

# ---- Das dunkle Gehäuse des Terminals ------------------------------------
#
# Drei Ebenen, und <b>eine Regel für alle: Was vertieft liegt, ist dunkler als
# sein Grund und hat unten rechts eine helle Kante.</b> Blech, Scheibe, Mulde.
# Andersherum — die Mulde heller als der Grund — liest man sie nicht als
# Mulde, sondern als Fleck.
CASE       = (35, 43, 39)
CASE_HI    = (57, 68, 61)
CASE_LO    = (13, 17, 15)
CASE_TEXT  = (168, 178, 172)
GLASS      = (24, 30, 27)
GLASS_RIM  = (52, 62, 56)
WELL       = (16, 20, 18)
WELL_EDGE  = (8, 10, 9)


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
ATLAS = 512

# Die Höhe fällt aus der Rechnung heraus und wird nicht gesetzt: Scheibe,
# Reiterzeile, Suchzeile, Raster, Statuszeile — jede Zeile kennt ihre Höhe.
# Beim ersten Entwurf hatte ich die Zahlen geraten, und die Statuszeile lag
# im Raster.
SCREEN_TOP = 6
SCREEN_PAD = 3
TAB_ROW = 14
SEARCH_ROW = 13
GRID_ROWS = 6
GRID_COLUMNS = 14
STATUS_ROW = 11

SCREEN_INNER = (SCREEN_PAD + TAB_ROW + 1 + SEARCH_ROW + 2
                + GRID_ROWS * 18 + 2 + STATUS_ROW)
SCREEN_BOTTOM = SCREEN_TOP + SCREEN_INNER
HEIGHT = SCREEN_BOTTOM + 14 + 82

# Das Inventar mittig, an Vanillas Raster ausgerichtet.
INV_COLUMNS = 9
INV_SLOT = 18
INV_LEFT = (WIDTH - INV_COLUMNS * INV_SLOT) // 2

# Das Spielerinventar sitzt an den Stellen, an denen es in Minecraft immer
# sitzt — Spieler greifen blind dorthin.
INV_X = INV_LEFT
INV_Y = HEIGHT - 82      # drei Reihen plus Schnellzugriff
HOTBAR_Y = HEIGHT - 24

# Der Bereich, in den die Reiter zeichnen: innerhalb der Scheibe, unter der
# Reiterzeile und über der Statuszeile.
SCREEN_X0 = 6
SCREEN_X1 = WIDTH - 7
WORK_X = SCREEN_X0 + 3
WORK_Y = SCREEN_TOP + SCREEN_PAD + TAB_ROW + 1
WORK_W = (SCREEN_X1 - 2) - WORK_X
WORK_H = (SCREEN_BOTTOM - 2 - STATUS_ROW) - WORK_Y


def raised_dark(d, box):
    """Erhabenes Blech: oben und links hell, unten und rechts dunkel."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=CASE + (255,))
    d.line([(x0, y0), (x1 - 1, y0)], fill=CASE_HI + (255,))
    d.line([(x0, y0), (x0, y1 - 1)], fill=CASE_HI + (255,))
    d.line([(x0 + 1, y1), (x1, y1)], fill=CASE_LO + (255,))
    d.line([(x1, y0 + 1), (x1, y1)], fill=CASE_LO + (255,))


def well(d, box, fill=GLASS, light=CASE_HI, shadow=CASE_LO):
    """Eine Vertiefung: oben und links dunkel, unten und rechts hell.

    Dieselbe Form wie ein Vanilla-Slot, nur in Blech statt in Grau. <b>Die
    Maße bleiben unangetastet</b> — was man blind trifft, ist die Stelle und
    nicht die Farbe.
    """
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=fill + (255,))
    d.line([(x0, y0), (x1 - 1, y0)], fill=shadow + (255,))
    d.line([(x0, y0), (x0, y1 - 1)], fill=shadow + (255,))
    d.line([(x0 + 1, y1), (x1, y1)], fill=light + (255,))
    d.line([(x1, y0 + 1), (x1, y1)], fill=light + (255,))


def background():
    """Das Fenster: Blechgehäuse, Scheibe, Spielerinventar."""
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    raised_dark(d, (0, 0, WIDTH - 1, HEIGHT - 1))

    # Die Scheibe sitzt in einer dunklen Fuge im Blech.
    d.rectangle((SCREEN_X0 - 1, SCREEN_TOP - 1, SCREEN_X1 + 1, SCREEN_BOTTOM + 1),
                fill=WELL_EDGE + (255,))
    d.rectangle((SCREEN_X0, SCREEN_TOP, SCREEN_X1, SCREEN_BOTTOM), fill=GLASS + (255,))
    d.line([(SCREEN_X0, SCREEN_BOTTOM), (SCREEN_X1, SCREEN_BOTTOM)],
           fill=GLASS_RIM + (255,))
    d.line([(SCREEN_X1, SCREEN_TOP), (SCREEN_X1, SCREEN_BOTTOM)], fill=GLASS_RIM + (255,))

    # Die Statuszeile liegt noch tiefer als die Scheibe.
    status_y = SCREEN_BOTTOM - STATUS_ROW + 1
    d.rectangle((SCREEN_X0 + 1, status_y, SCREEN_X1 - 1, SCREEN_BOTTOM - 1),
                fill=WELL_EDGE + (255,))

    # Das Spielerinventar: dieselben Stellen wie in jedem Minecraft-Fenster.
    for row in range(3):
        for column in range(9):
            x, y = INV_X - 1 + column * 18, INV_Y - 1 + row * 18
            well(d, (x, y, x + 17, y + 17))
    for column in range(9):
        x = INV_X - 1 + column * 18
        well(d, (x, HOTBAR_Y - 1, x + 17, HOTBAR_Y + 16))
    return img


def slot_grid():
    """Das Raster für den Netzbestand.

    Eigene Textur, weil der Bestand keine echten Slots sein kann: Zwanzig-
    tausend Arten lassen sich nicht anlegen. Sie sehen aber aus wie Slots,
    damit sie sich anfühlen wie Slots — jetzt als Mulden auf der Scheibe.
    """
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for row in range(8):
        for column in range(GRID_COLUMNS):
            x, y = column * 18, row * 18
            well(d, (x, y, x + 17, y + 17), fill=WELL,
                 light=GLASS_RIM, shadow=WELL_EDGE)
    return img


def screen():
    """Der Bildschirm des Code-Reiters.

    <b>Nur noch die Fläche, kein eigener Rahmen mehr.</b> Der Code-Reiter
    zeichnet jetzt in dieselbe Scheibe wie alle anderen; hätte er weiterhin
    seine eigene Umrandung, säßen zwei Bildschirme ineinander.
    """
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle((0, 0, WORK_W, WORK_H), fill=WELL + (255,))
    return img


def widgets():
    """Kleinteile: Reiter aktiv und inaktiv, Knopf, Suchfeld, Rollbalken."""
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # (0,0) bis (63,31) ist frei: Dort lagen die Reitergrafiken, solange die
    # Reiter Karteikarten waren. Jetzt sind sie flacher Text und brauchen
    # keine.

    # Kleiner Knopf (64,0) und gedrückt (64,16), je 12x12.
    # Zwölf Pixel, weil drei davon neben das Suchfeld passen müssen — für
    # Symbole reicht das nicht, deshalb tragen sie Buchstaben.
    raised_dark(d, (64, 0, 75, 11))
    well(d, (64, 16, 75, 27))

    # Breiter Knopf (80,0) und gedrückt (80,16), je 48x16
    raised_dark(d, (80, 0, 127, 15))
    well(d, (80, 16, 127, 31))

    # Suchfeld (0,32), 96x12 — dreiteilig, mit randloser Mitte.
    #
    # Zwei Pixel Kappe links und rechts. Wird das ganze Feld gekachelt,
    # bringt jede Kachel ihren eigenen Rahmen mit, und alle 96 Pixel läuft
    # eine Naht durch die Eingabezeile.
    well(d, (0, 32, 95, 43), fill=WELL, light=GLASS_RIM, shadow=WELL_EDGE)
    d.rectangle((2, 33, 93, 42), fill=WELL + (255,))
    d.line([(2, 32), (93, 32)], fill=WELL_EDGE + (255,))
    d.line([(2, 43), (93, 43)], fill=GLASS_RIM + (255,))

    # Rollbalken: Griff (96,32) und Rinne (108,32), je 12x15
    raised_dark(d, (96, 32, 107, 46))
    well(d, (108, 32, 119, 46), fill=WELL, light=GLASS_RIM, shadow=WELL_EDGE)
    return img


def save(image, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    image.save(path)
    print("  gui/%s.png  %d B" % (name, os.path.getsize(path)))


def press_background():
    """Das Fenster der Presse.

    Ein gewöhnliches Vanilla-Inventar: Stempel oben, Material darunter, Ausgabe
    rechts. Dazwischen zwei Anzeigen — der Pfeil für den Fortschritt und ein
    Balken für den Strom.

    <b>Beide Anzeigen brauchen eine leere Fassung im Hintergrund.</b> Sonst
    kann der Bildschirm nicht zeigen, wie voll sie sind: Er blendet den vollen
    Zustand nur teilweise darüber, und ohne den leeren dahinter stünde dort ein
    Loch.
    """
    breite, hoehe = 176, 166
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    panel(d, (0, 0, breite - 1, hoehe - 1))

    # Die drei Plätze der Maschine
    slot(d, 43, 16)   # Stempel
    slot(d, 43, 52)   # Material
    slot(d, 115, 34)  # Ausgabe, gross gerahmt
    sunken(d, (110, 29, 137, 56), fill=PANEL_DARK)
    slot(d, 115, 34)

    # Spielerinventar
    for row in range(3):
        for column in range(9):
            slot(d, 7 + column * 18, 83 + row * 18)
    for column in range(9):
        slot(d, 7 + column * 18, 141)

    # Fortschrittspfeil, leer: eine Rinne von links nach rechts
    sunken(d, (69, 35, 100, 50), fill=PANEL_DARK)

    # Energiebalken, leer: schmaler senkrechter Schacht
    sunken(d, (11, 16, 21, 72), fill=PANEL_DARK)

    # Die gefüllten Fassungen liegen rechts daneben im Atlas, damit der
    # Bildschirm sie von dort holen kann.
    arrow = Image.new("RGBA", (30, 14), (0, 0, 0, 0))
    ad = ImageDraw.Draw(arrow)
    ad.polygon([(0, 3), (20, 3), (20, 0), (29, 7), (20, 13), (20, 10), (0, 10)],
               fill=(120, 190, 120, 255))
    ad.polygon([(0, 4), (19, 4), (19, 2), (27, 7), (19, 12), (19, 9), (0, 9)],
               fill=(160, 225, 150, 255))
    img.paste(arrow, (180, 0))

    energie = Image.new("RGBA", (8, 54), (0, 0, 0, 0))
    ed = ImageDraw.Draw(energie)
    for y in range(54):
        anteil = y / 53.0
        farbe = (int(220 - 60 * anteil), int(150 + 40 * anteil), 60, 255)
        ed.line([(0, y), (7, y)], fill=farbe)
    ed.line([(0, 0), (0, 53)], fill=(255, 226, 150, 255))
    img.paste(energie, (180, 20))
    return img


def burner_background():
    """Das Fenster der Brennkammer.

    Ein Platz für Brennstoff, darüber die Flamme, links der Vorrat. Weniger
    geht nicht — und das ist der Punkt: Sie ist absichtlich die einfachste
    Maschine der Mod.
    """
    breite, hoehe = 176, 166
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, (0, 0, breite - 1, hoehe - 1))

    slot(d, 79, 52)                                   # Brennstoff
    sunken(d, (79, 33, 94, 48), fill=PANEL_DARK)      # Flamme, leer
    sunken(d, (11, 16, 21, 72), fill=PANEL_DARK)      # Vorrat, leer

    for row in range(3):
        for column in range(9):
            slot(d, 7 + column * 18, 83 + row * 18)
    for column in range(9):
        slot(d, 7 + column * 18, 141)

    # Die gefüllten Fassungen liegen rechts daneben, wie bei der Presse.
    flamme = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    fd = ImageDraw.Draw(flamme)
    fd.polygon([(7, 0), (10, 5), (12, 4), (13, 9), (10, 13), (4, 13), (1, 9),
                (2, 4), (4, 5)], fill=(226, 130, 40, 255))
    fd.polygon([(7, 3), (9, 7), (10, 10), (7, 12), (4, 10), (5, 7)],
               fill=(250, 200, 90, 255))
    img.paste(flamme, (180, 0))

    energie = Image.new("RGBA", (8, 54), (0, 0, 0, 0))
    ed = ImageDraw.Draw(energie)
    for y in range(54):
        anteil = y / 53.0
        farbe = (int(220 - 60 * anteil), int(150 + 40 * anteil), 60, 255)
        ed.line([(0, y), (7, y)], fill=farbe)
    ed.line([(0, 0), (0, 53)], fill=(255, 226, 150, 255))
    img.paste(energie, (180, 20))
    return img


def router_background():
    """Das Fenster des Routers: sechs Zeilen, je eine Seite.

    Keine Plätze — ein Router nimmt nichts auf. Die Knöpfe malt der Bildschirm
    selbst, damit ihre Farben dieselben sind wie die Ringe am Block; hier
    liegt nur die Rinne darunter.
    """
    breite, hoehe = 176, 162
    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, (0, 0, breite - 1, hoehe - 1))
    for reihe in range(6):
        y = 32 + reihe * 18
        sunken(d, (7, y - 1, breite - 8, y + 16), fill=PANEL_DARK)
    return img


# ---- Regale ---------------------------------------------------------------
#
# Laufwerk und Serverschrank sind dasselbe Fenster in zwei Zuschnitten: ein
# Raster fuer die Bauteile, darunter das Spielerinventar. Die Maße richten
# sich nach dem Vanilla-Container — jede eigene Zahl waere eine, die sich
# nicht anfuehlt wie der Rest des Spiels.

SHELF_WIDTH = 176


# Das Lämpchen hinter einem Einschub: fünf Pixel, zwei Pixel Abstand davor.
LAMP = 5
LAMP_PAD = 2
LAMP_ZONE = LAMP + LAMP_PAD

# Das Spielerinventar ist überall gleich breit — neun Plätze.
INVENTORY_WIDTH = 9 * 18


def shelf_background(width, columns, rows, group=0, gap=0,
                     inner_after=0, inner_gap=0, lamps=False):
    """Ein Regalfenster mit so vielen Plätzen, wie angegeben.

    ``group`` und ``gap`` fassen Spalten zu Blöcken zusammen: Der
    Serverschrank stellt je vier Plätze als einen Einschub nebeneinander und
    lässt dann Luft. ``inner_after`` und ``inner_gap`` setzen innerhalb eines
    Blocks noch einmal eine kleinere Lücke — nach dem Gehäuse, damit die drei
    Bauteile daneben nicht aussehen wie ein viertes.

    ``lamps`` setzt hinter jeden Block eine kleine Vertiefung. Was darin
    leuchtet, malt der Bildschirm zur Laufzeit — es hängt davon ab, was
    drinsteckt.

    Die Breite steht nicht mehr fest: Ein Schrank mit vier Plätzen je
    Einschub passt nicht in die 176 einer Truhe, und ein Fenster, in dem
    alles klebt, ist kein gespartes Bild.
    """
    grid_top = 18
    inventory_y = grid_top + rows * 18 + 13
    hotbar_y = inventory_y + 58
    height = hotbar_y + 18 + 7

    def spalte_rel(column):
        luecken = (column // group) * gap if group else 0
        inner = inner_gap if (group and inner_gap and column % group > inner_after) else 0
        return column * 18 + luecken + inner

    breite = spalte_rel(columns - 1) + 18 + (LAMP_ZONE if lamps else 0)
    links = (width - breite) // 2
    inventar_links = (width - INVENTORY_WIDTH) // 2

    img = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, (0, 0, width - 1, height - 1))

    bloecke = (columns // group) if group else 1
    for row in range(rows):
        for column in range(columns):
            x = links + spalte_rel(column) - 1
            y = grid_top + row * 18 - 1
            # Der erste Platz eines Blocks ist der Gehäuseplatz: eine Spur
            # dunkler, damit man sieht, dass er zuerst dran ist.
            if group and inner_gap and column % group == 0:
                sunken(d, (x, y, x + 17, y + 17), fill=(118, 118, 118))
            else:
                slot(d, x, y)
        if not lamps:
            continue
        for b in range(bloecke):
            x = links + spalte_rel(b * group + group - 1) + 18 + LAMP_PAD
            y = grid_top + row * 18 + 6
            sunken(d, (x, y, x + LAMP - 1, y + LAMP - 1), fill=(70, 70, 70))

    for row in range(3):
        for column in range(9):
            slot(d, inventar_links + column * 18, inventory_y - 1 + row * 18)
    for column in range(9):
        slot(d, inventar_links + column * 18, hotbar_y - 1)
    return img, links, grid_top, inventory_y, hotbar_y, height, width


def main():
    print("Oberflächentexturen:")
    save(background(), "terminal")
    save(slot_grid(), "storage_grid")
    save(screen(), "screen")
    save(widgets(), "widgets")
    save(press_background(), "press")
    save(burner_background(), "burner")
    save(router_background(), "router")
    for name, breite, columns, rows, group, gap, inner_after, inner_gap, lamps in (
            ("drive", 176, 2, 5, 0, 0, 0, 0, False),
            ("rack", 192, 8, 6, 4, 12, 0, 4, True)):
        bild, links, oben, inventar, schnell, hoehe, fenster = shelf_background(
            breite, columns, rows, group, gap, inner_after, inner_gap, lamps)
        save(bild, name)
        print("      %s: Raster bei %d,%d · Inventar bei %d,%d"
              " · Schnellzugriff bei %d,%d · Fenster %dx%d"
              % (name, links, oben, (fenster - INVENTORY_WIDTH) // 2 + 1, inventar,
                 (fenster - INVENTORY_WIDTH) // 2 + 1, schnell, fenster, hoehe))
    print("Maße: Fenster %dx%d, Arbeitsfläche %dx%d bei %d,%d"
          % (WIDTH, HEIGHT, WORK_W, WORK_H, WORK_X, WORK_Y))
    print("      Inventar bei %d,%d · Schnellzugriff bei %d,%d"
          % (INV_X, INV_Y, INV_X, HOTBAR_Y))


if __name__ == "__main__":
    main()
