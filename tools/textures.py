# -*- coding: utf-8 -*-
"""Erzeugt die Blocktexturen von Factory Network.

Gedeckte Metallpalette: Die Mod steht in Packs neben Mekanism, Thermal und
Create. Eine Textur, die dort nicht schreit, sondern sich einfügt, altert
besser als eine, die auffällt.
"""
import os
import random
from PIL import Image, ImageDraw

OUT = r"D:\Projekte\FactoryNetwork\src\main\resources\assets\factorynetwork\textures"
SIZE = 16

# Palette
DARK      = (34, 37, 41)
BASE      = (60, 65, 71)
BASE_LO   = (48, 52, 58)
BASE_HI   = (86, 92, 99)
EDGE      = (24, 26, 29)
ACCENT    = (138, 180, 248)   # blau, wie im Editor
ACCENT_LO = (74, 112, 168)
GREEN     = (163, 217, 165)
GREEN_LO  = (96, 146, 100)
BRASS     = (188, 152, 84)
BRASS_LO  = (124, 98, 52)


def new_image(color=BASE):
    return Image.new("RGBA", (SIZE, SIZE), color + (255,))


def add_grain(image, seed, strength=6):
    """Leichtes Rauschen. Ohne das wirken Flächen wie Plastik."""
    rng = random.Random(seed)
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            shift = rng.randint(-strength, strength)
            pixels[x, y] = (
                max(0, min(255, r + shift)),
                max(0, min(255, g + shift)),
                max(0, min(255, b + shift)),
                a,
            )
    return image


def bevel(draw, box=(0, 0, 15, 15), light=BASE_HI, shadow=EDGE):
    """Oben und links hell, unten und rechts dunkel — Vanilla-Konvention."""
    x0, y0, x1, y1 = box
    draw.line([(x0, y0), (x1, y0)], fill=light + (255,))
    draw.line([(x0, y0), (x0, y1)], fill=light + (255,))
    draw.line([(x0, y1), (x1, y1)], fill=shadow + (255,))
    draw.line([(x1, y0), (x1, y1)], fill=shadow + (255,))


def save(image, folder, name):
    path = os.path.join(OUT, folder, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("  " + folder + "/" + name + ".png")


def controller_side():
    """Gehäusewand mit Kühlrippen."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    for y in range(3, 13, 3):
        draw.line([(2, y), (13, y)], fill=BASE_LO + (255,))
        draw.line([(2, y + 1), (13, y + 1)], fill=BASE_HI + (255,))
    bevel(draw)
    return add_grain(image, 1)


def controller_top():
    """Oberseite mit leuchtendem Kern — der Controller ist die Wurzel."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 4, 11, 11], fill=DARK + (255,))
    draw.rectangle([5, 5, 10, 10], fill=ACCENT_LO + (255,))
    draw.rectangle([6, 6, 9, 9], fill=ACCENT + (255,))
    # Vier Zuleitungen zu den Kanten
    for points in ([(7, 0), (7, 4)], [(8, 11), (8, 15)],
                   [(0, 7), (4, 7)], [(11, 8), (15, 8)]):
        draw.line(points, fill=ACCENT_LO + (255,))
    bevel(draw)
    return add_grain(image, 2, 4)


def cable():
    """Metallrohr, längs geriffelt."""
    image = new_image(BASE_LO)
    draw = ImageDraw.Draw(image)
    for x in range(0, 16, 4):
        draw.line([(x, 0), (x, 15)], fill=BASE + (255,))
        draw.line([(x + 1, 0), (x + 1, 15)], fill=BASE_HI + (255,))
    draw.line([(0, 0), (15, 0)], fill=BASE_HI + (255,))
    draw.line([(0, 15), (15, 15)], fill=EDGE + (255,))
    return add_grain(image, 3, 5)


def connector_side():
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], outline=BASE_LO + (255,))
    bevel(draw)
    return add_grain(image, 4)


def connector_front():
    """Die Seite zur Maschine: ein Anschlussring."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=DARK + (255,))
    draw.ellipse([4, 4, 11, 11], fill=BASE_LO + (255,), outline=BASE_HI + (255,))
    draw.ellipse([6, 6, 9, 9], fill=GREEN_LO + (255,))
    draw.point((7, 7), fill=GREEN + (255,))
    draw.point((8, 8), fill=GREEN + (255,))
    bevel(draw)
    return add_grain(image, 5, 4)


def connector_back():
    """Die Seite zum Kabel."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 5, 10, 10], fill=BASE_LO + (255,), outline=EDGE + (255,))
    bevel(draw)
    return add_grain(image, 6)


def terminal_front():
    """Bildschirm mit angedeuteten Codezeilen."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 14, 12], fill=(26, 28, 31) + (255,), outline=EDGE + (255,))
    # Codezeilen: erst ein Schlüsselwort, dann Text — wie im Editor
    rows = [(3, 3, 6, ACCENT), (3, 5, 9, (140, 148, 156)),
            (5, 7, 8, GREEN), (5, 9, 6, (140, 148, 156))]
    for x, y, length, color in rows:
        draw.line([(x, y), (x + length, y)], fill=color + (255,))
    draw.line([(2, 14), (13, 14)], fill=BASE_HI + (255,))
    bevel(draw)
    return add_grain(image, 7, 3)


def terminal_side():
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.line([(4, 2), (4, 13)], fill=BASE_LO + (255,))
    draw.line([(11, 2), (11, 13)], fill=BASE_LO + (255,))
    bevel(draw)
    return add_grain(image, 8)


def machine_top():
    """Gemeinsame Ober- und Unterseite für Connector und Terminal."""
    image = new_image(BASE)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], outline=BASE_LO + (255,))
    draw.rectangle([6, 6, 9, 9], fill=BASE_LO + (255,))
    bevel(draw)
    return add_grain(image, 9)


def label_gun():
    """Werkzeug im Item-Stil: diagonal, mit Griff unten links."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Griff
    draw.polygon([(3, 14), (3, 10), (6, 8), (7, 11), (5, 14)],
                 fill=(72, 56, 40, 255), outline=(48, 36, 26, 255))
    # Korpus
    draw.polygon([(5, 9), (10, 4), (13, 6), (8, 11)],
                 fill=BASE + (255,), outline=EDGE + (255,))
    draw.line([(6, 8), (11, 5)], fill=BASE_HI + (255,))
    # Mündung, in der Akzentfarbe des Netzwerks
    draw.polygon([(10, 3), (13, 5), (12, 7), (9, 5)],
                 fill=BRASS_LO + (255,), outline=EDGE + (255,))
    draw.point((11, 4), fill=BRASS + (255,))
    draw.point((12, 5), fill=BRASS + (255,))
    # Anzeige am Korpus
    draw.point((7, 9), fill=GREEN + (255,))
    return image


def main():
    print("Blocktexturen:")
    save(controller_side(), "block", "controller_side")
    save(controller_top(), "block", "controller_top")
    save(cable(), "block", "cable")
    save(connector_side(), "block", "connector_side")
    save(connector_front(), "block", "connector_front")
    save(connector_back(), "block", "connector_back")
    save(terminal_front(), "block", "terminal_front")
    save(terminal_side(), "block", "terminal_side")
    save(machine_top(), "block", "machine_top")
    print("Gegenstandstexturen:")
    save(label_gun(), "item", "label_gun")


if __name__ == "__main__":
    main()
