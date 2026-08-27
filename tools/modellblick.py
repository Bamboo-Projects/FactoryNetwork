# -*- coding: utf-8 -*-
"""Zeigt ein Blockmodell isometrisch, mit seinen echten Texturen.

<b>Wozu.</b> Ob ein Modell im Spiel etwas hermacht, sieht man erst im Spiel —
und ein Client-Start kostet zwei Minuten. Dieses Werkzeug rechnet dieselbe
Ansicht in einer Sekunde: die Kästen eines Modells von schräg oben, jede
Fläche mit ihrer Textur und der Helligkeit, die Minecraft ihrer Richtung gibt.

Es ist kein Ersatz für das Spiel. Beleuchtung, Umgebungsverdeckung und alles
Durchsichtige fehlen. Für die Frage „stimmt die Form" reicht es.

Aufruf:

    python tools/modellblick.py block/controller block/drive
    python tools/modellblick.py --out vergleich.png block/terminal
"""
import argparse
import json
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets",
                      "factorynetwork")

# Die Helligkeit, die Minecraft einer Fläche nach ihrer Richtung gibt.
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8,
         "east": 0.6, "west": 0.6}

# Aus dieser Ecke sieht man genau diese drei Seiten. Norden statt Sueden,
# weil die Vorderseite eines Blocks dorthin zeigt — sonst betrachtet man
# jede Maschine von hinten.
VISIBLE = ("up", "north", "east")

_textures = {}


def texture(name):
    """Eine Textur, einmal geladen."""
    if name in _textures:
        return _textures[name]
    path = os.path.join(ASSETS, "textures", *name.split(":")[-1].split("/"))
    image = None
    if os.path.exists(path + ".png"):
        image = Image.open(path + ".png").convert("RGBA")
        # Animationsstreifen: nur das erste Bild.
        if image.height > image.width:
            image = image.crop((0, 0, image.width, image.width))
    _textures[name] = image
    return image


# Die Vanilla-Eltern, die unsere Bloecke benutzen. Sie liegen im Spiel-Jar
# und nicht bei uns; ihre Kaesten stehen deshalb hier.
def _cube(faces):
    return [{"from": [0, 0, 0], "to": [16, 16, 16],
             "faces": {side: {"texture": tex} for side, tex in faces.items()}}]


VANILLA = {
    "cube_all": _cube({s: "#all" for s in
                       ("down", "up", "north", "south", "west", "east")}),
    "cube": _cube({s: "#" + s for s in
                   ("down", "up", "north", "south", "west", "east")}),
    "cube_bottom_top": _cube({"down": "#bottom", "up": "#top",
                              "north": "#side", "south": "#side",
                              "west": "#side", "east": "#side"}),
    "orientable": _cube({"down": "#top", "up": "#top", "north": "#front",
                         "south": "#side", "west": "#side", "east": "#side"}),
    "orientable_with_bottom": _cube({"down": "#bottom", "up": "#top",
                                     "north": "#front", "south": "#side",
                                     "west": "#side", "east": "#side"}),
}


def load(model, seen=0):
    """Elemente und Texturen eines Modells, Eltern eingerechnet."""
    path = os.path.join(ASSETS, "models", *model.split(":")[-1].split("/"))
    with open(path + ".json", encoding="utf-8") as f:
        data = json.load(f)
    textures = dict(data.get("textures", {}))
    elements = data.get("elements", [])
    parent = data.get("parent")
    if parent and not elements:
        short = parent.split("/")[-1]
        if short in VANILLA:
            elements = VANILLA[short]
    if parent and not parent.startswith("minecraft:") and seen < 8:
        up_elements, up_textures = load(parent, seen + 1)
        # Das Kind gewinnt: Es überschreibt, was der Elternteil vorgibt.
        merged = dict(up_textures)
        merged.update(textures)
        textures = merged
        if not elements:
            elements = up_elements
    return elements, textures


def resolve(textures, key, seen=0):
    while key.startswith("#") and seen < 8:
        key = textures.get(key[1:], "")
        seen += 1
    return key


def default_uv(side, box):
    """Der Ausschnitt, den Minecraft ohne Angabe nimmt."""
    x1, y1, z1 = box[0]
    x2, y2, z2 = box[1]
    return {
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[side]


# Je Flaeche die vier Ecken, beginnend oben links im Bild und im Uhrzeigersinn
# — dieselbe Reihenfolge, in der die Textur gelesen wird.
CORNERS = {
    "up": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]),
                        (b[0], b[1], a[2]), (a[0], b[1], a[2])],
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]),
                           (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]),
                          (b[0], a[1], a[2]), (b[0], a[1], b[2])],
}


def project(point, scale, origin):
    """Isometrie von vorn rechts oben: x nach rechts, z nach hinten."""
    x, y, z = point
    return (origin[0] + (x - (16 - z)) * 0.866 * scale,
            origin[1] + ((x + (16 - z)) * 0.5 - y) * scale)


def affine(dest, size):
    """Die Koeffizienten, die einen Texturausschnitt aufs Viereck legen.

    Pillow rechnet rückwärts: Es fragt für jeden Zielpunkt, woher die Farbe
    kommt. Gesucht ist also die Umkehrung der Abbildung Textur → Fläche, und
    weil die Fläche in der Isometrie ein Parallelogramm ist, gibt es sie
    genau.
    """
    (x0, y0), (x1, y1), _, (x3, y3) = dest
    ux, uy = x1 - x0, y1 - y0
    vx, vy = x3 - x0, y3 - y0
    det = ux * vy - vx * uy
    if abs(det) < 1e-9:
        return None
    width, height = size
    a = width * vy / det
    b = width * -vx / det
    d = height * -uy / det
    e = height * ux / det
    return (a, b, -(a * x0 + b * y0), d, e, -(d * x0 + e * y0))


def render(models, size=520, scale=None, background=(26, 27, 31)):
    scale = scale or size / 34.0
    origin = (size / 2, size * 0.62)
    page = Image.new("RGBA", (size, size), background + (255,))

    faces = []
    for model in models:
        elements, textures = load(model)
        for element in elements:
            box = (element["from"], element["to"])
            for side, face in element.get("faces", {}).items():
                if side not in VISIBLE:
                    continue
                faces.append((box, side, face, textures))

    # Von hinten nach vorn: Die Isometrie hat keine Tiefenprüfung.
    def depth(entry):
        box = entry[0]
        # Weit hinten heisst: grosses z, kleines x, kleines y.
        return ((box[0][2] + box[1][2]) - (box[0][0] + box[1][0])
                - (box[0][1] + box[1][1]))

    for box, side, face, textures in sorted(faces, key=depth):
        image = texture(resolve(textures, face.get("texture", "")))
        if image is None:
            continue
        uv = face.get("uv") or default_uv(side, box)
        left, top, right, bottom = [round(v * image.width / 16.0) for v in uv]
        left, right = min(left, right), max(left, right)
        top, bottom = min(top, bottom), max(top, bottom)
        patch = image.crop((left, top, max(right, left + 1), max(bottom, top + 1)))

        shade = SHADE[side]
        patch = patch.point(lambda v, s=shade: int(v * s)).convert("RGBA")
        patch.putalpha(image.crop((left, top, max(right, left + 1),
                                   max(bottom, top + 1))).getchannel("A"))

        dest = [project(p, scale, origin) for p in CORNERS[side](*box)]
        coeffs = affine(dest, patch.size)
        if coeffs is None:
            continue
        placed = patch.transform((size, size), Image.AFFINE, coeffs,
                                 resample=Image.NEAREST)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon(dest, fill=255)
        alpha = placed.getchannel("A").point(lambda v: 255 if v > 128 else 0)
        mask = Image.composite(alpha, Image.new("L", (size, size), 0), mask)
        page.paste(placed, (0, 0), mask)

    return page.convert("RGB")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("models", nargs="+",
                        help="Modelle, etwa block/controller — mehrere übereinander")
    parser.add_argument("--out", default="modellblick.png")
    parser.add_argument("--size", type=int, default=520)
    parser.add_argument("--single", action="store_true",
                        help="alle Modelle in ein Bild statt nebeneinander")
    args = parser.parse_args()

    if args.single:
        render(args.models, args.size).save(args.out)
        print(args.out)
        return

    pages = [(m, render([m], args.size)) for m in args.models]
    gap = 12
    width = len(pages) * args.size + (len(pages) + 1) * gap
    sheet = Image.new("RGB", (width, args.size + 2 * gap + 22), (20, 20, 24))
    pen = ImageDraw.Draw(sheet)
    for i, (name, page) in enumerate(pages):
        x = gap + i * (args.size + gap)
        sheet.paste(page, (x, gap))
        pen.text((x, gap + args.size + 5), name, fill=(180, 182, 186))
    sheet.save(args.out)
    print(args.out, sheet.size)


if __name__ == "__main__":
    main()
