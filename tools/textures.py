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
import random
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "factorynetwork", "textures")
N = 64

# ---- Palette -------------------------------------------------------------
# Der Umfang war zu eng: von 10 bis 110 von 255 bleibt kein Platz fuer Tiefe.
# Eine Flaeche wirkt erst plastisch, wenn Schatten und Glanzlicht weit genug
# auseinanderliegen — deshalb reicht die Palette jetzt von fast schwarz bis
# deutlich hell.
EDGE       = (8, 10, 9)
BODY_TOP   = (58, 68, 62)
BODY_BOT   = (28, 34, 30)
BODY_MID   = (44, 52, 47)
LIGHT      = (96, 112, 102)
SHINE      = (146, 168, 152)
ACCENT     = (120, 220, 140)
ACCENT_HI  = (205, 255, 215)
ACCENT_DIM = (24, 78, 44)
BRASS      = (196, 154, 74)
BRASS_HI   = (236, 200, 130)
WOOD       = (74, 56, 38)
WOOD_HI    = (104, 80, 54)


def blend(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def surface(top=BODY_TOP, bottom=BODY_BOT, seed=1):
    """Grundfläche: Verlauf, Körnung, Bürstung.

    <b>Ein reiner Verlauf sieht aus wie Plastik.</b> Metall hat eine Struktur,
    und die entsteht aus drei Dingen: einem Verlauf für die Lichtrichtung,
    feiner Körnung gegen die Gleichmässigkeit, und waagerechten Zügen, die das
    Auge als gebürstete Oberfläche liest.

    Die Körnung ist gesät, nicht zufällig — sonst erzeugt jeder Lauf des
    Skripts andere Dateien, und das Repository füllt sich mit Änderungen, die
    keine sind.
    """
    img = Image.new("RGBA", (N, N), top + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(N):
        draw.line([(0, y), (N - 1, y)], fill=blend(top, bottom, y / (N - 1)) + (255,))
    grain(img, seed=seed)
    brushed(img, seed=seed + 100)
    return img


def grain(image, amount=9, seed=1):
    """Feine Körnung über die ganze Fläche.

    Ohne sie bleibt jede Fläche glatt, und mehrere Blöcke nebeneinander sehen
    aus wie eine einzige gestrichene Wand.
    """
    rnd = random.Random(seed)
    px = image.load()
    for y in range(N):
        for x in range(N):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            ton = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + ton)), max(0, min(255, g + ton)),
                        max(0, min(255, b + ton)), a)


def brushed(image, count=26, seed=1, strength=7):
    """Waagerechte Züge, wie bei gebürstetem Metall."""
    rnd = random.Random(seed)
    px = image.load()
    for _ in range(count):
        y = rnd.randrange(N)
        x0 = rnd.randrange(0, N - 8)
        laenge = rnd.randrange(8, N - x0 + 1)
        ton = rnd.choice([-strength, strength])
        for x in range(x0, x0 + laenge):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (max(0, min(255, r + ton)), max(0, min(255, g + ton)),
                        max(0, min(255, b + ton)), a)


def ao(image, box, depth=3, strength=0.55):
    """Verschattung nach innen, wo Flächen zusammenstoßen.

    <b>Das ist der stärkste einzelne Hebel für Tiefe.</b> Eine versenkte
    Fläche wirkt erst dann versenkt, wenn ihre Ränder abdunkeln — eine harte
    Kante allein liest das Auge als aufgemalt.
    """
    x0, y0, x1, y1 = box
    px = image.load()
    for schritt in range(depth):
        anteil = strength * (1.0 - schritt / float(depth))
        for x in range(x0 + schritt, x1 - schritt + 1):
            for y in (y0 + schritt, y1 - schritt):
                if 0 <= x < N and 0 <= y < N:
                    px[x, y] = _dunkler(px[x, y], anteil)
        for y in range(y0 + schritt, y1 - schritt + 1):
            for x in (x0 + schritt, x1 - schritt):
                if 0 <= x < N and 0 <= y < N:
                    px[x, y] = _dunkler(px[x, y], anteil)


def _dunkler(farbe, anteil):
    r, g, b, a = farbe
    return (int(r * (1 - anteil)), int(g * (1 - anteil)), int(b * (1 - anteil)), a)


def _heller(farbe, anteil):
    r, g, b, a = farbe
    return (int(r + (255 - r) * anteil), int(g + (255 - g) * anteil),
            int(b + (255 - b) * anteil), a)


def recess(image, box, tiefe=2):
    """Macht aus einem Rechteck eine versenkte Fläche.

    Licht kommt von oben links: Die obere und linke Kante liegen im Schatten,
    die untere und rechte fangen Licht. Genau umgekehrt wie bei einer
    erhabenen Fläche — und diese Umkehr ist das ganze Geheimnis.
    """
    x0, y0, x1, y1 = box
    px = image.load()
    for i in range(tiefe):
        for x in range(x0 + i, x1 - i + 1):
            if 0 <= x < N:
                if 0 <= y0 + i < N:
                    px[x, y0 + i] = _dunkler(px[x, y0 + i], 0.45)
                if 0 <= y1 - i < N:
                    px[x, y1 - i] = _heller(px[x, y1 - i], 0.18)
        for y in range(y0 + i, y1 - i + 1):
            if 0 <= y < N:
                if 0 <= x0 + i < N:
                    px[x0 + i, y] = _dunkler(px[x0 + i, y], 0.45)
                if 0 <= x1 - i < N:
                    px[x1 - i, y] = _heller(px[x1 - i, y], 0.18)


def raised(image, box, hoehe=2):
    """Dasselbe erhaben: oben und links hell, unten und rechts im Schatten."""
    x0, y0, x1, y1 = box
    px = image.load()
    for i in range(hoehe):
        for x in range(x0 + i, x1 - i + 1):
            if 0 <= x < N:
                if 0 <= y0 + i < N:
                    px[x, y0 + i] = _heller(px[x, y0 + i], 0.30)
                if 0 <= y1 - i < N:
                    px[x, y1 - i] = _dunkler(px[x, y1 - i], 0.40)
        for y in range(y0 + i, y1 - i + 1):
            if 0 <= y < N:
                if 0 <= x0 + i < N:
                    px[x0 + i, y] = _heller(px[x0 + i, y], 0.30)
                if 0 <= x1 - i < N:
                    px[x1 - i, y] = _dunkler(px[x1 - i, y], 0.40)


def rivet(image, x, y, r=3):
    """Eine Niete: Schatten unten rechts, Glanz oben links.

    Vier davon in den Ecken machen aus einer Platte ein verschraubtes Bauteil.
    Sie sind das billigste Mittel, einer Fläche Massstab zu geben.
    """
    d = ImageDraw.Draw(image)
    d.ellipse([x - r, y - r, x + r, y + r], fill=EDGE + (255,))
    d.ellipse([x - r + 1, y - r + 1, x + r - 1, y + r - 1],
              fill=blend(BODY_TOP, LIGHT, 0.5) + (255,))
    px = image.load()
    if 0 <= x - 1 < N and 0 <= y - 1 < N:
        px[x - 1, y - 1] = _heller(px[x - 1, y - 1], 0.45)


def scratches(image, count=5, seed=1):
    """Ein paar feine Kratzer — Gebrauchsspuren gegen die Werksfrische.

    <b>Sehr zurückhaltend.</b> Ein sichtbarer Kratzer liest sich nicht als
    Gebrauch, sondern als Fehler in der Textur; erst wenn man sie einzeln kaum
    bemerkt, tun sie ihre Arbeit.
    """
    rnd = random.Random(seed)
    px = image.load()
    for _ in range(count):
        x0 = rnd.randrange(6, N - 14)
        y0 = rnd.randrange(6, N - 6)
        laenge = rnd.randrange(4, 10)
        hell = rnd.random() < 0.5
        for i in range(laenge):
            x = x0 + i
            y = y0 + (1 if i > laenge // 2 and rnd.random() < 0.3 else 0)
            if not (0 <= x < N and 0 <= y < N):
                continue
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = _heller((r, g, b, a), 0.12) if hell                 else _dunkler((r, g, b, a), 0.18)


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
    """Das Herz des Netzes: ein Kern in einer verschraubten Fassung.

    Der einzige Block, der von sich aus leuchtet — er soll in einer Wand aus
    Maschinen der sein, den man zuerst sieht.
    """
    img = surface(seed=51)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Fassung: versenkt, mit Verschattung
    d.rectangle([9, 9, 54, 54], fill=blend(BODY_MID, EDGE, 0.3) + (255,))
    grain(img, amount=7, seed=52)
    recess(img, (9, 9, 54, 54), tiefe=2)
    ao(img, (9, 9, 54, 54), depth=4, strength=0.4)

    # Vier Stege zum Kern hin
    for box in ((30, 12, 33, 22), (30, 41, 33, 51), (12, 30, 22, 33), (41, 30, 51, 33)):
        d.rectangle(box, fill=blend(BODY_TOP, LIGHT, 0.3) + (255,))
        raised(img, box, hoehe=1)

    # Der Kern
    d.rectangle([22, 22, 41, 41], fill=EDGE + (255,))
    ao(img, (22, 22, 41, 41), depth=3, strength=0.5)
    glow(img, [25, 25, 38, 38], radius=8, strength=190)
    d = ImageDraw.Draw(img)
    d.rectangle([25, 25, 38, 38], fill=ACCENT + (255,))
    d.rectangle([27, 27, 36, 36], fill=ACCENT_HI + (255,))
    d.rectangle([29, 29, 34, 34], fill=(255, 255, 255, 255))

    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        rivet(img, x, y)
    scratches(img, seed=53)
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
    """Die Seite, die an der Maschine sitzt: Anschlussring in einer Mulde."""
    img = surface(seed=21)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Versenkte Platte
    d.rectangle([10, 10, 53, 53], fill=blend(BODY_MID, EDGE, 0.3) + (255,))
    grain(img, amount=7, seed=22)
    recess(img, (10, 10, 53, 53), tiefe=2)
    ao(img, (10, 10, 53, 53), depth=4, strength=0.4)

    # Anschlussring, erhaben, mit dunklem Loch
    d.ellipse([16, 16, 47, 47], fill=blend(BODY_TOP, LIGHT, 0.35) + (255,))
    d.ellipse([16, 16, 47, 47], outline=EDGE + (255,))
    d.arc([16, 16, 47, 47], 180, 340, fill=SHINE + (255,))
    d.ellipse([22, 22, 41, 41], fill=blend(EDGE, BODY_BOT, 0.4) + (255,))
    ao(img, (22, 22, 41, 41), depth=3, strength=0.5)
    d.ellipse([26, 26, 37, 37], fill=blend(ACCENT_DIM, EDGE, 0.35) + (255,))

    for x, y in ((6, 6), (57, 6), (6, 57), (57, 57)):
        rivet(img, x, y, r=2)
    scratches(img, seed=23)
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
    """Die Aussenhaut aller Maschinen: verschraubte Platte mit Feld.

    Drei Ebenen statt einer: die Grundplatte, ein versenktes Feld darin, und
    Nieten in den Ecken. Jede davon bekommt ihre eigene Kantenbehandlung, und
    erst zusammen ergibt das eine Fläche, die nach Blech aussieht statt nach
    Farbe.
    """
    img = surface(seed=11)
    d = ImageDraw.Draw(img)

    # Aeussere Fase: oben hell, unten im Schatten
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Versenktes Feld in der Mitte
    d.rectangle([12, 12, 51, 51], fill=blend(BODY_MID, EDGE, 0.25) + (255,))
    grain(img, amount=7, seed=12)
    recess(img, (12, 12, 51, 51), tiefe=2)
    ao(img, (12, 12, 51, 51), depth=4, strength=0.35)

    # Zwei Querstege, die dem Feld Struktur geben
    for y in (26, 38):
        d.rectangle([16, y, 47, y + 3], fill=blend(BODY_TOP, LIGHT, 0.25) + (255,))
        raised(img, (16, y, 47, y + 3), hoehe=1)

    for x, y in ((7, 7), (56, 7), (7, 56), (56, 56)):
        rivet(img, x, y)
    scratches(img, seed=13)
    return img


def terminal_front():
    """Bildschirm in einem Rahmen, darunter eine Tastenleiste.

    Der Bildschirm ist die dunkelste Fläche der ganzen Mod — davor wirkt die
    Schrift hell, und das Gerät liest sich sofort als Anzeige.
    """
    img = surface(seed=61)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Rahmen
    d.rectangle([6, 6, 57, 44], fill=blend(BODY_MID, EDGE, 0.35) + (255,))
    recess(img, (6, 6, 57, 44), tiefe=2)

    # Bildschirm: fast schwarz, mit Zeilen
    d.rectangle([9, 9, 54, 41], fill=(12, 18, 14, 255))
    ao(img, (9, 9, 54, 41), depth=3, strength=0.55)
    for i, y in enumerate(range(14, 38, 6)):
        laenge = (34, 26, 30, 20)[i % 4]
        d.rectangle([13, y, 13 + laenge, y + 2],
                    fill=blend(ACCENT, (12, 18, 14), 0.45 if i else 0.15) + (255,))
    glow(img, [9, 9, 54, 41], radius=5, strength=60)

    # Tastenleiste
    d = ImageDraw.Draw(img)
    for x in range(9, 52, 11):
        d.rectangle([x, 48, x + 8, 55], fill=blend(BODY_TOP, LIGHT, 0.25) + (255,))
        raised(img, (x, 48, x + 8, 55), hoehe=1)
    scratches(img, seed=62)
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


def network_analyser():
    """Ein Messgerät: Gehäuse, Anzeige, kurze Sonde.

    Die Silhouette trägt: ein flaches Gehäuse mit grossem Fenster und einer
    Sonde oben rechts. So ist es im Hotbar von der Beschriftungspistole zu
    unterscheiden, die eine lange Waffenform hat.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Gehäuse
    d.rounded_rectangle([14, 20, 48, 54], radius=4,
                        fill=blend(BODY_TOP, EDGE, 0.25) + (255,), outline=EDGE + (255,))
    d.line([(17, 23), (45, 23)], fill=SHINE + (255,))

    # Sonde nach oben rechts
    d.line([(44, 22), (54, 10)], fill=EDGE + (255,), width=4)
    d.line([(44, 22), (54, 10)], fill=BRASS + (255,), width=2)
    d.ellipse([50, 6, 58, 14], fill=BRASS + (255,), outline=EDGE + (255,))
    d.ellipse([52, 8, 56, 12], fill=BRASS_HI + (255,))

    # Anzeigefenster mit einem Netzgeflecht darin
    glow(img, [19, 26, 43, 44], radius=6, strength=120)
    d = ImageDraw.Draw(img)
    d.rectangle([19, 26, 43, 44], fill=EDGE + (255,))
    d.rectangle([21, 28, 41, 42], fill=blend(ACCENT, EDGE, 0.55) + (255,))
    # Drei Knoten und ihre Verbindungen — das Werkzeug zeigt, was es tut.
    knoten = [(25, 32), (35, 31), (30, 39)]
    for a in range(len(knoten)):
        for b in range(a + 1, len(knoten)):
            d.line([knoten[a], knoten[b]], fill=ACCENT + (255,))
    for x, y in knoten:
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(240, 250, 235, 255))

    # Griffrillen unten
    for y in range(47, 53, 2):
        d.line([(20, y), (42, y)], fill=blend(BODY_TOP, EDGE, 0.55) + (255,))
    return img


CELL_TONE = {
    "1k": (150, 160, 172),
    "4k": (150, 172, 152),
    "16k": (172, 160, 140),
    "64k": (176, 148, 168),
}


def storage_cell(label):
    """Eine Speicherzelle: Gehäuse, Fenster, Beschriftung durch Farbe.

    Die vier Größen unterscheiden sich im Ton, nicht in der Form — im Hotbar
    zählt, dass man sie auseinanderhält, nicht dass man die Größe abliest.
    """
    ton = CELL_TONE[label]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Gehäuse mit Fase
    d.rounded_rectangle([16, 12, 48, 52], radius=3,
                        fill=blend(ton, EDGE, 0.2) + (255,), outline=EDGE + (255,))
    d.line([(19, 15), (45, 15)], fill=blend(ton, (255, 255, 255), 0.5) + (255,))

    # Sichtfenster mit Füllstandsstreifen
    d.rectangle([21, 19, 43, 38], fill=EDGE + (255,))
    d.rectangle([23, 21, 41, 36], fill=blend(ton, EDGE, 0.55) + (255,))
    for i, y in enumerate(range(23, 36, 4)):
        hell = blend(ACCENT, ton, 0.25) if i < 2 else blend(ton, EDGE, 0.4)
        d.rectangle([25, y, 39, y + 2], fill=hell + (255,))

    # Kontakte unten — hier steckt sie im Laufwerk
    for x in range(22, 43, 6):
        d.rectangle([x, 43, x + 3, 49], fill=BRASS + (255,), outline=EDGE + (255,))
    return img


def drive_front():
    """Zehn Schächte in zwei Reihen, jeder mit Lämpchen.

    Die Schächte sind versenkt, die Stege dazwischen erhaben — so liest man
    auf einen Blick, wo eine Zelle hineingehört.
    """
    img = surface(seed=31)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    d.rectangle([7, 9, 56, 54], fill=blend(BODY_MID, EDGE, 0.35) + (255,))
    grain(img, amount=6, seed=32)
    recess(img, (7, 9, 56, 54), tiefe=2)

    for reihe in range(5):
        for spalte in range(2):
            x = 10 + spalte * 24
            y = 12 + reihe * 8
            d.rectangle([x, y, x + 19, y + 5],
                        fill=blend(EDGE, BODY_BOT, 0.5) + (255,))
            ao(img, (x, y, x + 19, y + 5), depth=2, strength=0.45)
            # Betriebslämpchen, glimmt
            d.rectangle([x + 16, y + 1, x + 18, y + 3],
                        fill=blend(ACCENT, EDGE, 0.2) + (255,))
    for x, y in ((4, 5), (59, 5), (4, 58), (59, 58)):
        rivet(img, x, y, r=2)
    scratches(img, seed=33)
    return img


def crystal_ore(deepslate=False):
    """Erz: Einsprengsel im Gestein, keine gemalte Form.

    Vanilla-Erze sind unregelmäßige Flecken, keine Kristalle mit Kanten. Wer
    hier eine Form malt, bekommt bei vier benachbarten Blöcken ein Muster, das
    sich sichtbar wiederholt.
    """
    grund = (78, 78, 80) if deepslate else (128, 128, 128)
    img = Image.new("RGBA", (N, N), grund + (255,))
    d = ImageDraw.Draw(img)

    # Gesteinskörnung, damit die Fläche nicht flach wirkt
    rnd = random.Random(7 if deepslate else 3)
    for _ in range(700):
        x, y = rnd.randrange(N), rnd.randrange(N)
        ton = rnd.choice([-10, -6, 6, 10])
        d.point((x, y), fill=tuple(max(0, min(255, c + ton)) for c in grund) + (255,))

    # Vier Einsprengsel, jeweils ein paar zusammenhängende Punkte
    for cx, cy in ((18, 20), (42, 17), (24, 45), (46, 43)):
        for _ in range(rnd.randrange(9, 14)):
            x = cx + rnd.randrange(-7, 8)
            y = cy + rnd.randrange(-7, 8)
            hell = rnd.random() < 0.35
            d.rectangle([x, y, x + 3, y + 3],
                        fill=(CRYSTAL_HI if hell else CRYSTAL) + (255,))
    return img


def raw_crystal():
    """Der rohe Kristall: eine Scherbe, kein geschliffener Stein."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(30, 8), (46, 26), (40, 52), (22, 50), (16, 28)],
              fill=CRYSTAL + (255,), outline=EDGE + (255,))
    # Zwei Facetten, damit es nicht wie ein Fleck aussieht
    d.polygon([(30, 8), (40, 30), (28, 34), (20, 26)], fill=CRYSTAL_HI + (255,))
    d.polygon([(28, 34), (40, 30), (38, 50), (26, 48)],
              fill=blend(CRYSTAL, EDGE, 0.3) + (255,))
    d.line([(30, 10), (22, 26)], fill=(255, 255, 255, 255))
    return img


STAMP_TONE = {
    "plate": (150, 152, 158),
    "logic": (120, 172, 132),
    "memory": (172, 140, 116),
    "network": (124, 148, 184),
}


def stamp(kind):
    """Ein Prägestempel: schwerer Kopf, kurzer Schaft, Muster auf der Fläche.

    Die vier unterscheiden sich im Muster der Prägefläche, nicht in der Form —
    was sie prägen, sieht man am Abdruck, nicht am Griff.
    """
    ton = STAMP_TONE[kind]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Schaft
    d.rectangle([26, 8, 38, 26], fill=blend(BODY_TOP, EDGE, 0.3) + (255,),
                outline=EDGE + (255,))
    d.line([(29, 11), (29, 24)], fill=SHINE + (255,))
    # Kopf
    d.rectangle([14, 26, 50, 46], fill=blend(ton, EDGE, 0.25) + (255,),
                outline=EDGE + (255,))
    d.line([(17, 29), (47, 29)], fill=blend(ton, (255, 255, 255), 0.45) + (255,))

    # Prägefläche mit Muster
    d.rectangle([18, 34, 46, 44], fill=EDGE + (255,))
    hell = blend(ton, (255, 255, 255), 0.35) + (255,)
    if kind == "plate":
        for x in range(21, 45, 4):
            d.line([(x, 36), (x, 42)], fill=hell)
    elif kind == "logic":
        d.rectangle([22, 36, 30, 42], outline=hell)
        d.line([(30, 39), (42, 39)], fill=hell)
        d.rectangle([40, 37, 43, 41], fill=hell)
    elif kind == "memory":
        for y in (37, 40):
            for x in range(21, 45, 6):
                d.rectangle([x, y, x + 3, y + 1], fill=hell)
    else:
        d.line([(22, 39), (42, 39)], fill=hell)
        for x in (26, 32, 38):
            d.line([(x, 36), (x, 42)], fill=hell)
    return img


def plate():
    """Gepresstes Metall: eine flache Scheibe mit Prägekante."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([12, 20, 52, 44], radius=3,
                        fill=blend((162, 166, 172), EDGE, 0.15) + (255,),
                        outline=EDGE + (255,))
    d.line([(15, 23), (49, 23)], fill=(226, 230, 236, 255))
    d.rounded_rectangle([18, 26, 46, 39], radius=2,
                        outline=blend((162, 166, 172), EDGE, 0.5) + (255,))
    return img


def crystal_cut():
    """Der geschliffene Kristall: klare Facetten statt roher Bruch."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(32, 10), (48, 26), (32, 54), (16, 26)],
              fill=CRYSTAL + (255,), outline=EDGE + (255,))
    d.polygon([(32, 10), (48, 26), (32, 30), (16, 26)], fill=CRYSTAL_HI + (255,))
    d.polygon([(32, 30), (48, 26), (32, 54)], fill=blend(CRYSTAL, EDGE, 0.35) + (255,))
    d.line([(32, 12), (20, 26)], fill=(255, 255, 255, 255))
    return img


def core(kind):
    """Ein Kern: Prozessorgehäuse mit Kontakten und farbigem Fenster."""
    ton = STAMP_TONE[kind]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Kontakte links und rechts
    for y in range(20, 45, 6):
        d.rectangle([10, y, 16, y + 3], fill=BRASS + (255,), outline=EDGE + (255,))
        d.rectangle([48, y, 54, y + 3], fill=BRASS + (255,), outline=EDGE + (255,))

    # Gehäuse
    d.rounded_rectangle([16, 16, 48, 48], radius=2,
                        fill=blend((58, 60, 64), EDGE, 0.2) + (255,), outline=EDGE + (255,))
    d.line([(19, 19), (45, 19)], fill=(96, 100, 106, 255))

    # Fenster in der Kernfarbe
    glow(img, [23, 23, 41, 41], radius=5, strength=110)
    d = ImageDraw.Draw(img)
    d.rectangle([23, 23, 41, 41], fill=EDGE + (255,))
    d.rectangle([25, 25, 39, 39], fill=ton + (255,))
    d.line([(27, 27), (37, 27)], fill=blend(ton, (255, 255, 255), 0.5) + (255,))
    return img


def press_front():
    """Stempel oben, Amboss unten, Führungssäulen seitlich.

    Der Zwischenraum ist der Arbeitsbereich — dort glüht das Werkstück, und
    das ist der einzige helle Fleck auf der Fläche.
    """
    img = surface(seed=41)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Arbeitsraum: versenkt
    d.rectangle([10, 10, 53, 54], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    grain(img, amount=6, seed=42)
    recess(img, (10, 10, 53, 54), tiefe=2)
    ao(img, (10, 10, 53, 54), depth=4, strength=0.45)

    # Führungssäulen, erhaben
    for x in (12, 47):
        d.rectangle([x, 12, x + 5, 52], fill=blend(BODY_TOP, LIGHT, 0.3) + (255,))
        raised(img, (x, 12, x + 5, 52), hoehe=1)

    # Stempelkopf
    d.rectangle([20, 14, 43, 27], fill=blend(BODY_TOP, LIGHT, 0.2) + (255,))
    raised(img, (20, 14, 43, 27), hoehe=2)
    for x in range(23, 42, 5):
        d.line([(x, 17), (x, 24)], fill=blend(BODY_MID, EDGE, 0.3) + (255,))

    # Amboss
    d.rectangle([18, 42, 45, 52], fill=blend(BODY_TOP, LIGHT, 0.15) + (255,))
    raised(img, (18, 42, 45, 52), hoehe=2)

    # Werkstueck dazwischen, gluehend
    glow(img, [26, 32, 38, 38], color=(232, 150, 60), radius=7, strength=170)
    d = ImageDraw.Draw(img)
    d.rectangle([26, 32, 38, 38], fill=(226, 142, 56, 255))
    d.line([(27, 33), (37, 33)], fill=(252, 208, 140, 255))
    scratches(img, seed=43)
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
    save(drive_front(), "block", "drive_front")
    save(press_front(), "block", "press_front")
    save(crystal_ore(False), "block", "crystal_ore")
    save(crystal_ore(True), "block", "deepslate_crystal_ore")
    print("Gegenstandstexturen:")
    save(label_gun(), "item", "label_gun")
    save(network_analyser(), "item", "network_analyser")
    save(raw_crystal(), "item", "raw_crystal")
    save(crystal_cut(), "item", "crystal")
    save(plate(), "item", "plate")
    for kind in ("plate", "logic", "memory", "network"):
        save(stamp(kind), "item", "stamp_" + kind)
    for kind in ("logic", "memory", "network"):
        save(core(kind), "item", "core_" + kind)
    for label in ("1k", "4k", "16k", "64k"):
        save(storage_cell(label), "item", "cell_k" + label.replace("k", ""))


if __name__ == "__main__":
    main()
