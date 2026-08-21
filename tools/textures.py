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

# Die sechzehn Farbstoffe von Minecraft, etwas gedämpft — ein Kabel soll
# neben den Blöcken liegen, nicht vor ihnen leuchten.
CABLE_COLOURS = {
    "white": (208, 212, 210), "orange": (206, 122, 48), "magenta": (170, 78, 168),
    "light_blue": (86, 148, 200), "yellow": (200, 178, 56), "lime": (124, 178, 60),
    "pink": (206, 130, 154), "gray": (78, 84, 88), "light_gray": (140, 146, 148),
    "cyan": (52, 138, 148), "purple": (122, 68, 168), "blue": (60, 78, 168),
    "brown": (110, 78, 50), "green": (96, 130, 52), "red": (162, 58, 52),
    "black": (36, 38, 40),
}


def cable(tube=None):
    """Die Oberfläche eines Kabels.

    <b>Ein Kreuz, kein gleichmäßiges Muster.</b> Zwei Fehlversuche stehen
    dahinter. Der erste malte drei Röhren mit Querschellen für eine volle
    Blockfläche — das Modell ist aber ein schmales Rohr, und die Schellen
    zerfielen in Bruchstücke. Der zweite nahm eine gleichmäßige Längsriffelung,
    damit jeder Ausschnitt gleich aussieht; im Spiel lief die Maserung dann auf
    waagerechten Bahnen quer, weil alle sechs Flächen denselben Ausschnitt
    bekommen.

    Applied Energistics löst das anders, und diese Fassung übernimmt es: Der
    <b>Randbereich</b> der Textur ist quer zur Achse gleichmäßig und liefert
    die Längsansicht eines Arms. Die <b>Mitte</b> trägt den Querschnitt und ist
    in beiden Achsen symmetrisch, also von jeder Seite gleich. Damit stimmt die
    Ausrichtung ohne eine einzige Drehung im Modell.

    Gezeichnet wird in Blockpixeln, jeder als 4x4-Feld: Die Datei ist 64x64 wie
    alle anderen, das Muster aber effektiv 16x16. Feinere Strukturen ergeben
    auf sechs Blockpixeln nur Moiré — genau das Flimmern, das die Riffelung
    hatte.
    """
    base = tube if tube else (168, 174, 170)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()

    # Der Mantel liegt zwischen Blockpixel 5 und 11. Aussen bleibt es hell,
    # damit der Randbereich als ruhige Längsfläche taugt.
    # Der Verlauf liegt ganz im Mantel: Blockpixel 5 und 10 sind seine Kanten
    # und dunkel, 7 und 8 tragen das Glanzlicht. Ein Saum ausserhalb waere
    # unsichtbar, denn auf das Kabel kommt nur der Bereich 5 bis 11.
    profil = {5: 0.60, 6: 0.84, 7: 1.00, 8: 1.00, 9: 0.84, 10: 0.60}
    # Aussen voll hell, damit der Randbereich die Laengsansicht ungedaempft
    # durchreicht: Das Minimum beider Achsen ist dort genau das Querprofil.
    aussen = 1.00

    def helligkeit(t):
        return profil.get(t, aussen)

    for by in range(16):
        for bx in range(16):
            # Das Minimum beider Achsen: aussen bleibt die jeweils andere
            # Achse ruhig, in der Mitte entsteht die Rundung.
            f = min(helligkeit(bx), helligkeit(by))
            farbe = tuple(min(255, int(c * f)) for c in base)
            for y in range(by * 4, by * 4 + 4):
                for x in range(bx * 4, bx * 4 + 4):
                    px[x, y] = farbe + (255,)
    return img


def cable_channels(satz):
    """Die Kanallinien eines smarten Kabels.

    Vier haarfeine Linien längs in der Kabelmitte, je ein Viertel Blockpixel
    breit — bei 64 Pixeln also genau ein Pixel. Satz eins liegt auf 7,0 / 7,5 /
    8,0 / 8,5, Satz zwei um ein Achtel versetzt dazwischen. Übereinandergelegt
    ergeben beide acht Bahnen; wie viele leuchten, sagt die Auslastung.

    Dieselben Positionen wie bei Applied Energistics, dort ausgemessen. Hier
    ist die vierfache Auflösung nicht Zierde, sondern Bedingung: Ein Viertel
    Blockpixel gibt es unter 64 nicht.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bahnen = [7.00, 7.50, 8.00, 8.50] if satz == 0 else [7.25, 7.75, 8.25, 8.75]
    for nr, mitte in enumerate(bahnen):
        x = int(round(mitte * 4))
        # Waagerecht und senkrecht, damit dieselbe Textur für die Längsansicht
        # und für den Querschnitt taugt — wie beim Mantel.
        d.line([(x, 0), (x, N - 1)], fill=(255, 255, 255, 255))
        d.line([(0, x), (N - 1, x)], fill=(255, 255, 255, 255))
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


def display_front():
    """Ein dunkler Schirm mit schmalem Rahmen — er soll Text tragen, nicht
    selbst auffallen."""
    img = surface(BODY_MID, BODY_BOT)
    d = ImageDraw.Draw(img)
    bevel(d, width=2)
    d.rectangle([3, 3, 60, 60], fill=EDGE + (255,))
    d.rectangle([5, 5, 58, 58], fill=(12, 16, 13) + (255,))
    # Ein feiner Schimmer am oberen Rand, damit die Fläche nicht tot wirkt
    d.line([(6, 6), (57, 6)], fill=(28, 40, 32) + (255,))
    return img


def display_side():
    """Die Kante — nur zwei Pixel breit sichtbar."""
    img = surface(BODY_TOP, BODY_BOT)
    d = ImageDraw.Draw(img)
    bevel(d, width=2)
    for y in range(12, 53, 10):
        d.line([(2, y), (61, y)], fill=blend(BODY_TOP, EDGE, 0.5) + (255,))
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
    # Eine einzige Kabeltextur, in Grau. Gefärbt wird zur Laufzeit über den
    # Tintindex — siebzehn Texturen wären dasselbe Bild in siebzehn Tönen.
    save(cable(), "block", "cable")
    save(cable_channels(0), "block", "cable_channels_a")
    save(cable_channels(1), "block", "cable_channels_b")
    save(connector_front(), "block", "connector_front")
    save(connector_side(), "block", "connector_side")
    save(connector_back(), "block", "connector_back")
    save(machine_top(), "block", "machine_top")
    save(terminal_front(), "block", "terminal_front")
    save(terminal_side(), "block", "terminal_side")
    save(display_front(), "block", "display_front")
    save(display_side(), "block", "display_side")
    print("Gegenstandstexturen:")
    save(label_gun(), "item", "label_gun")


if __name__ == "__main__":
    main()
