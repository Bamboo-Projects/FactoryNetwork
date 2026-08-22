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
# Der Umfang war zu eng: von 10 bis 110 von 255 bleibt kein Platz für Tiefe.
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
CRYSTAL    = (108, 196, 214)
CRYSTAL_HI = (186, 240, 248)
PLATE_TOP  = (190, 194, 202)
PLATE_BOT  = (126, 132, 140)


def blend(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def masked_surface(mask, top=BODY_TOP, bottom=BODY_BOT, seed=1):
    """Struktur gehört ins Material, nicht in den Hintergrund.

    Gegenstände haben transparente Ränder. Wenn die Oberfläche erst auf einer
    vollen Platte entsteht und dann durch die Maske fällt, bleiben Körnung und
    Bürstung innerhalb der Silhouette und erzeugen keine Farbsäume.
    """
    layer = surface(top, bottom, seed)
    result = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    result.paste(layer, (0, 0), mask)
    return result


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
    # und dunkel, 7 und 8 tragen das Glanzlicht. Ein Saum außerhalb wäre
    # unsichtbar, denn auf das Kabel kommt nur der Bereich 5 bis 11.
    profil = {5: 0.60, 6: 0.84, 7: 1.00, 8: 1.00, 9: 0.84, 10: 0.60}
    # Außen voll hell, damit der Randbereich die Längsansicht ungedämpft
    # durchreicht: Das Minimum beider Achsen ist dort genau das Querprofil.
    outer = 1.00

    def helligkeit(t):
        return profil.get(t, outer)

    for by in range(16):
        for bx in range(16):
            # Das Minimum beider Achsen: außen bleibt die jeweils andere
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
    """Beschriftungspistole mit langem Lauf und schwerem Griff."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    grip_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(grip_mask).polygon(
        [(16, 55), (14, 45), (22, 35), (28, 37), (31, 49), (25, 57)], fill=255
    )
    img.alpha_composite(masked_surface(grip_mask, WOOD_HI, WOOD, seed=201))

    body_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(body_mask).polygon(
        [(22, 36), (35, 22), (47, 15), (56, 20), (52, 31), (38, 43), (26, 44)], fill=255
    )
    img.alpha_composite(masked_surface(body_mask, blend(BODY_TOP, LIGHT, 0.18),
                                       blend(BODY_MID, EDGE, 0.28), seed=202))
    d = ImageDraw.Draw(img)
    d.polygon([(16, 55), (14, 45), (22, 35), (28, 37), (31, 49), (25, 57)],
              outline=EDGE + (255,))
    # Die Mündung liegt vor dem Korpus; ihre Füllung muss die innere
    # Korpuskante überdecken, sonst zerreißt die Silhouette beim Herunterskalieren.
    d.polygon([(22, 36), (35, 22), (47, 15), (56, 20), (52, 31), (38, 43), (26, 44)],
              outline=EDGE + (255,))

    muzzle_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(muzzle_mask).polygon(
        [(47, 15), (55, 18), (60, 20), (58, 28), (50, 31), (48, 24)], fill=255
    )
    img.alpha_composite(masked_surface(muzzle_mask, BRASS_HI, BRASS, seed=203))
    d = ImageDraw.Draw(img)
    d.polygon([(47, 15), (55, 18), (60, 20), (58, 28), (50, 31), (48, 24)],
              outline=EDGE + (255,))

    d.line([(18, 53), (22, 39)], fill=WOOD_HI + (255,))
    d.line([(24, 37), (45, 18)], fill=SHINE + (255,))
    d.line([(25, 43), (38, 43)], fill=_dunkler(BODY_TOP + (255,), 0.35))
    d.line([(50, 17), (58, 20)], fill=BRASS_HI + (255,))
    d.line([(50, 31), (57, 28)], fill=_dunkler(BRASS + (255,), 0.35))

    d.rectangle([31, 28, 44, 38], fill=blend(BODY_MID, EDGE, 0.45) + (255,))
    recess(img, (31, 28, 44, 38), tiefe=2)
    ao(img, (31, 28, 44, 38), depth=3, strength=0.4)
    glow(img, [33, 30, 38, 35], radius=4, strength=120)
    d = ImageDraw.Draw(img)
    d.rectangle([33, 30, 38, 35], fill=EDGE + (255,))
    d.rectangle([34, 31, 37, 34], fill=ACCENT + (255,))
    d.rectangle([24, 40, 29, 43], fill=EDGE + (255,))
    d.line([(24, 40), (28, 40)], fill=LIGHT + (255,))
    d.line([(29, 41), (29, 43)], fill=_dunkler(EDGE + (255,), 0.1))

    scratches(img, count=3, seed=204)
    return img


def network_analyser():
    """Messgerät mit breitem Fenster und kurzer Sonde."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    body_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(body_mask).polygon(
        [(14, 22), (21, 15), (43, 15), (50, 22), (50, 50), (44, 56), (20, 56), (14, 50)], fill=255
    )
    img.alpha_composite(masked_surface(body_mask, blend(BODY_TOP, LIGHT, 0.12),
                                       blend(BODY_BOT, EDGE, 0.18), seed=211))

    probe_mask = Image.new("L", (N, N), 0)
    probe_draw = ImageDraw.Draw(probe_mask)
    shaft_pts = [(39, 19), (43, 16), (50, 7), (54, 10), (46, 21), (42, 23)]
    probe_draw.polygon(shaft_pts, fill=255)
    probe_draw.ellipse([49, 4, 57, 12], fill=255)
    img.alpha_composite(masked_surface(probe_mask, BRASS_HI, BRASS, seed=212))
    d = ImageDraw.Draw(img)

    d.polygon([(14, 22), (21, 15), (43, 15), (50, 22), (50, 50), (44, 56), (20, 56), (14, 50)],
              outline=EDGE + (255,))
    d.polygon(shaft_pts, outline=EDGE + (255,))
    d.ellipse([49, 4, 57, 12], outline=EDGE + (255,))
    d.line([(19, 18), (41, 18)], fill=SHINE + (255,))
    d.line([(17, 52), (41, 52)], fill=_dunkler(BODY_TOP + (255,), 0.35))
    d.line([(42, 19), (51, 8)], fill=BRASS_HI + (255,))
    d.arc([50, 5, 56, 11], 210, 330, fill=BRASS_HI + (255,))
    d.line([(46, 21), (52, 12)], fill=_dunkler(BRASS + (255,), 0.22))

    d.rectangle([18, 22, 46, 44], fill=blend(BODY_MID, EDGE, 0.42) + (255,))
    recess(img, (18, 22, 46, 44), tiefe=2)
    ao(img, (18, 22, 46, 44), depth=3, strength=0.4)
    glow(img, [21, 25, 43, 41], radius=5, strength=110)
    d = ImageDraw.Draw(img)
    d.rectangle([21, 25, 43, 41], fill=blend(ACCENT_DIM, EDGE, 0.25) + (255,))
    for a, b in (((25, 36), (31, 29)), ((31, 29), (38, 32)), ((25, 36), (38, 32)), ((38, 32), (35, 38))):
        d.line([a, b], fill=ACCENT + (255,))
    for x, y in ((25, 36), (31, 29), (38, 32), (35, 38)):
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=ACCENT_HI + (255,))

    for y in (47, 50, 53):
        d.line([(22, y), (42, y)], fill=blend(BODY_MID, EDGE, 0.5) + (255,))
    scratches(img, count=3, seed=213)
    return img


CELL_TONE = {
    "1k": (150, 160, 172),
    "4k": (150, 172, 152),
    "16k": (172, 160, 140),
    "64k": (176, 148, 168),
}


# Die Umrisslinie der Kassette. Beide Zellarten teilen sie sich: Sie sollen
# als dasselbe Bauteil zu erkennen sein und sich nur im Fenster unterscheiden.
CELL_SHELL = [(18, 11), (44, 11), (50, 17), (50, 48), (45, 53), (19, 53), (14, 48), (14, 16)]


def cell_shell(ton, seed=220):
    """Das Gehäuse einer Zelle, ohne Fenster."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    shell_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(shell_mask).polygon(CELL_SHELL, fill=255)
    img.alpha_composite(masked_surface(shell_mask, blend(ton, LIGHT, 0.18),
                                       blend(ton, EDGE, 0.35), seed=seed))
    d = ImageDraw.Draw(img)
    d.polygon(CELL_SHELL, outline=EDGE + (255,))
    d.line([(19, 14), (42, 14)], fill=blend(ton, SHINE, 0.55) + (255,))
    d.line([(18, 50), (43, 50)], fill=_dunkler(ton + (255,), 0.35))
    return img


def cell_contacts(img, d):
    """Die vier Kontakte am unteren Rand."""
    for x in (21, 27, 33, 39):
        d.rectangle([x, 40, x + 3, 50], fill=BRASS + (255,))
        raised(img, (x, 40, x + 3, 50), hoehe=1)
        d.line([(x, 40), (x + 2, 40)], fill=BRASS_HI + (255,))


def storage_cell(label):
    """Speicherzelle als schwere Kassette mit Sichtfenster."""
    ton = CELL_TONE[label]
    img = cell_shell(ton)
    d = ImageDraw.Draw(img)

    d.rectangle([19, 19, 45, 37], fill=blend(BODY_MID, EDGE, 0.48) + (255,))
    recess(img, (19, 19, 45, 37), tiefe=2)
    ao(img, (19, 19, 45, 37), depth=3, strength=0.45)
    glow(img, [23, 22, 41, 34], color=blend(ACCENT, ton, 0.35), radius=4, strength=80)
    d = ImageDraw.Draw(img)
    d.rectangle([23, 22, 41, 34], fill=blend(ton, EDGE, 0.58) + (255,))
    for i, y in enumerate((24, 28, 32)):
        farbe = blend(ACCENT_HI, ton, 0.45 if i == 0 else 0.65) if i < 2 else blend(ton, EDGE, 0.25)
        d.rectangle([25, y, 39, y + 2], fill=farbe + (255,))

    cell_contacts(img, d)
    scratches(img, count=2, seed=221)
    return img


FLUID_CELL_TONE = {
    "64": (128, 152, 172),
    "256": (114, 160, 182),
    "1024": (104, 146, 190),
    "4096": (120, 132, 196),
}


def fluid_cell(label):
    """Flüssigkeitszelle: dieselbe Kassette, aber mit Schauglas.

    Das Fenster ist zu zwei Dritteln gefüllt und trägt oben eine helle Linie.
    <b>Das ist der ganze Unterschied im Bild</b> — wer eine Zelle in der Hand
    hat, soll auf einen Blick sehen, was hineingehört, ohne den Namen zu
    lesen.
    """
    ton = FLUID_CELL_TONE[label]
    img = cell_shell(ton, seed=230)
    d = ImageDraw.Draw(img)

    # Schauglas: versenkt, mit dunklem Grund.
    d.rectangle([19, 19, 45, 37], fill=blend(BODY_MID, EDGE, 0.55) + (255,))
    recess(img, (19, 19, 45, 37), tiefe=2)
    ao(img, (19, 19, 45, 37), depth=3, strength=0.45)

    # Der Stand: zwei Drittel, mit Spiegel oben und dunklerem Grund unten.
    fluessig = blend(ton, (60, 150, 220), 0.55)
    glow(img, [22, 27, 42, 35], color=fluessig, radius=4, strength=90)
    d = ImageDraw.Draw(img)
    d.rectangle([22, 27, 42, 35], fill=fluessig + (255,))
    d.rectangle([22, 32, 42, 35], fill=_dunkler(fluessig + (255,), 0.22))
    d.line([(22, 27), (42, 27)], fill=_heller(fluessig + (255,), 0.55))
    # Ein Wellenkamm, damit es nicht wie ein Balken aussieht.
    for x in range(23, 42, 6):
        d.point((x, 26), fill=_heller(fluessig + (255,), 0.35))

    # Zwei Striche als Skala.
    for y in (23, 30):
        d.line([(20, y), (23, y)], fill=blend(SHINE, ton, 0.4) + (255,))

    cell_contacts(img, d)
    scratches(img, count=2, seed=231)
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
    """Roher Kristall mit unruhigem Bruch statt sauberem Schliff."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    pts = [(24, 10), (35, 7), (46, 18), (52, 31), (42, 44), (38, 58), (23, 52), (15, 39), (12, 22)]
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    img.alpha_composite(masked_surface(mask, CRYSTAL_HI, CRYSTAL, seed=230))
    grain(img, amount=5, seed=231)
    d = ImageDraw.Draw(img)
    d.polygon(pts, outline=EDGE + (255,))

    d.polygon([(24, 10), (35, 7), (43, 18), (34, 31), (19, 24)],
              fill=blend(CRYSTAL_HI, (255, 255, 255), 0.25) + (255,))
    d.polygon([(19, 24), (34, 31), (30, 48), (18, 43), (12, 22)],
              fill=blend(CRYSTAL_HI, CRYSTAL, 0.18) + (255,))
    d.polygon([(34, 31), (43, 18), (52, 31), (42, 44), (30, 48)],
              fill=blend(CRYSTAL, EDGE, 0.2) + (255,))
    d.polygon([(30, 48), (42, 44), (38, 58), (23, 52)],
              fill=blend(CRYSTAL, EDGE, 0.22) + (255,))
    d.polygon([(34, 31), (30, 48), (23, 52), (15, 39), (18, 43)],
              fill=blend(CRYSTAL, EDGE, 0.35) + (255,))
    d.line([(26, 12), (18, 23)], fill=(255, 255, 255, 255))
    d.line([(36, 10), (44, 19)], fill=CRYSTAL_HI + (255,))
    d.line([(24, 50), (36, 55)], fill=_dunkler(CRYSTAL + (255,), 0.3))
    scratches(img, count=2, seed=232)
    return img


STAMP_TONE = {
    "plate": (150, 152, 158),
    "logic": (120, 172, 132),
    "memory": (172, 140, 116),
    "network": (124, 148, 184),
}


def stamp(kind):
    """Prägestempel mit massivem Kopf und klarer Abdruckfläche."""
    ton = STAMP_TONE[kind]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    shaft_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(shaft_mask).polygon(
        [(26, 8), (36, 8), (39, 15), (37, 28), (25, 28), (23, 15)], fill=255
    )
    img.alpha_composite(masked_surface(shaft_mask, blend(BODY_TOP, LIGHT, 0.15),
                                       blend(BODY_BOT, EDGE, 0.1), seed=240))

    head_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(head_mask).polygon(
        [(14, 28), (18, 23), (46, 23), (50, 28), (50, 45), (14, 45)], fill=255
    )
    img.alpha_composite(masked_surface(head_mask, blend(ton, LIGHT, 0.2),
                                       blend(ton, EDGE, 0.32), seed=241))
    d = ImageDraw.Draw(img)

    d.polygon([(26, 8), (36, 8), (39, 15), (37, 28), (25, 28), (23, 15)], outline=EDGE + (255,))
    d.polygon([(14, 28), (18, 23), (46, 23), (50, 28), (50, 45), (14, 45)], outline=EDGE + (255,))
    d.line([(27, 11), (35, 11)], fill=SHINE + (255,))
    d.line([(18, 26), (44, 26)], fill=blend(ton, SHINE, 0.55) + (255,))

    d.rectangle([19, 31, 45, 41], fill=blend(BODY_MID, EDGE, 0.52) + (255,))
    recess(img, (19, 31, 45, 41), tiefe=2)
    ao(img, (19, 31, 45, 41), depth=3, strength=0.45)
    d = ImageDraw.Draw(img)
    hell = blend(ton, (255, 255, 255), 0.35) + (255,)
    if kind == "plate":
        for x in range(22, 45, 5):
            d.line([(x, 33), (x, 39)], fill=hell)
    elif kind == "logic":
        d.rectangle([22, 33, 29, 39], outline=hell)
        d.line([(29, 36), (39, 36)], fill=hell)
        d.rectangle([38, 34, 41, 38], fill=hell)
    elif kind == "memory":
        for y in (34, 37):
            for x in range(22, 43, 6):
                d.rectangle([x, y, x + 3, y + 1], fill=hell)
    else:
        d.line([(22, 36), (42, 36)], fill=hell)
        for x in (26, 32, 38):
            d.line([(x, 33), (x, 39)], fill=hell)
    scratches(img, count=2, seed=242)
    return img


def plate():
    """Metallplatte mit gestanzter Schulter und breiter Silhouette."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    tone_top = PLATE_TOP
    tone_bottom = PLATE_BOT

    mask = Image.new("L", (N, N), 0)
    pts = [(12, 24), (18, 18), (46, 18), (52, 24), (52, 40), (46, 46), (18, 46), (12, 40)]
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    img.alpha_composite(masked_surface(mask, tone_top, tone_bottom, seed=250))
    d = ImageDraw.Draw(img)
    d.polygon(pts, outline=EDGE + (255,))
    d.line([(18, 21), (45, 21)], fill=(226, 230, 236, 255))
    d.line([(19, 43), (44, 43)], fill=_dunkler(tone_bottom + (255,), 0.32))

    d.rectangle([19, 25, 45, 39], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    recess(img, (19, 25, 45, 39), tiefe=2)
    ao(img, (19, 25, 45, 39), depth=3, strength=0.4)
    d = ImageDraw.Draw(img)
    d.rectangle([22, 28, 42, 36], outline=blend((162, 166, 172), EDGE, 0.4) + (255,))
    d.line([(24, 30), (40, 30)], fill=SHINE + (255,))
    scratches(img, count=3, seed=251)
    return img


def crystal_cut():
    """Geschliffener Kristall mit geordneten Facetten und hellem Kern."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    pts = [(32, 8), (42, 14), (48, 24), (44, 38), (32, 56), (20, 38), (16, 24), (22, 14)]
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    img.alpha_composite(masked_surface(mask, CRYSTAL_HI, CRYSTAL, seed=260))
    grain(img, amount=4, seed=261)
    d = ImageDraw.Draw(img)
    d.polygon(pts, outline=EDGE + (255,))

    glow(img, [24, 18, 40, 42], color=CRYSTAL_HI, radius=5, strength=75)
    d = ImageDraw.Draw(img)
    d.polygon([(25, 14), (39, 14), (42, 24), (32, 29), (22, 24)],
              fill=blend(CRYSTAL_HI, (255, 255, 255), 0.3) + (255,))
    d.polygon([(22, 24), (32, 29), (42, 24), (38, 39), (32, 50), (26, 39)],
              fill=blend(CRYSTAL_HI, CRYSTAL, 0.22) + (255,))
    d.polygon([(42, 24), (48, 24), (44, 38), (38, 39)],
              fill=blend(CRYSTAL, EDGE, 0.32) + (255,))
    d.polygon([(16, 24), (22, 24), (26, 39), (20, 38)],
              fill=blend(CRYSTAL_HI, CRYSTAL, 0.35) + (255,))
    d.polygon([(26, 39), (38, 39), (32, 56)],
              fill=blend(CRYSTAL, EDGE, 0.26) + (255,))
    d.line([(27, 16), (37, 16)], fill=(255, 255, 255, 255))
    d.line([(40, 16), (45, 24)], fill=CRYSTAL_HI + (255,))
    d.line([(24, 39), (31, 52)], fill=_dunkler(CRYSTAL + (255,), 0.25))
    scratches(img, count=2, seed=262)
    return img


def core(kind):
    """Kern als Chipgehäuse mit Pins und leuchtendem Zentrum."""
    ton = STAMP_TONE[kind]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    for y in range(20, 45, 6):
        d.rectangle([8, y, 14, y + 3], fill=BRASS + (255,))
        d.line([(8, y), (13, y)], fill=BRASS_HI + (255,))
        d.rectangle([50, y, 56, y + 3], fill=BRASS + (255,))
        d.line([(50, y), (55, y)], fill=BRASS_HI + (255,))

    shell_mask = Image.new("L", (N, N), 0)
    pts = [(17, 16), (47, 16), (50, 19), (50, 45), (47, 48), (17, 48), (14, 45), (14, 19)]
    ImageDraw.Draw(shell_mask).polygon(pts, fill=255)
    img.alpha_composite(masked_surface(shell_mask, blend(BODY_TOP, LIGHT, 0.08),
                                       blend(BODY_BOT, EDGE, 0.05), seed=270))
    d = ImageDraw.Draw(img)
    d.polygon(pts, outline=EDGE + (255,))
    d.line([(18, 19), (45, 19)], fill=LIGHT + (255,))
    d.line([(18, 45), (45, 45)], fill=_dunkler(BODY_MID + (255,), 0.3))

    d.rectangle([20, 22, 44, 42], fill=blend(BODY_MID, EDGE, 0.5) + (255,))
    recess(img, (20, 22, 44, 42), tiefe=2)
    ao(img, (20, 22, 44, 42), depth=3, strength=0.45)
    glow(img, [25, 26, 39, 38], color=ton, radius=5, strength=105)
    d = ImageDraw.Draw(img)
    d.rectangle([25, 26, 39, 38], fill=EDGE + (255,))
    d.rectangle([27, 28, 37, 36], fill=ton + (255,))
    d.line([(27, 28), (36, 28)], fill=blend(ton, (255, 255, 255), 0.55) + (255,))
    if kind == "logic":
        d.line([(29, 32), (35, 32)], fill=ACCENT_HI + (255,))
        d.line([(32, 29), (32, 35)], fill=ACCENT_HI + (255,))
    elif kind == "memory":
        for x in (29, 32, 35):
            d.line([(x, 30), (x, 34)], fill=blend(ton, ACCENT_HI, 0.45) + (255,))
    else:
        for x, y in ((29, 30), (35, 30), (32, 34)):
            d.rectangle([x, y, x + 1, y + 1], fill=ACCENT_HI + (255,))
            if x != 32:
                d.line([(32, 34), (x, y + 1)], fill=blend(ton, ACCENT_HI, 0.5) + (255,))
    scratches(img, count=2, seed=271)
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


# ---- Router --------------------------------------------------------------
# Formensprache: ein Rahmen außen, in dem die Bahnkennung sitzt, und in der
# Mitte eine versenkte Buchse, in die das dicke Kabel laeuft.

def router_side():
    """Eine Seite des Routers.

    Alle sechs Seiten sehen gleich aus — welche Bahn eine Seite fuehrt, malt
    nicht die Textur, sondern der Renderer darüber. Sonst bräuchte es
    fünfzehntausend Blockzustände für dieselbe Auskunft.
    """
    img = surface(seed=61)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Aussenrahmen, in dem die Bahnkennung liegt: leicht versenkt, damit der
    # Ring darin sitzt statt aufgeklebt zu wirken.
    d.rectangle([3, 3, 60, 60], outline=blend(BODY_BOT, EDGE, 0.4) + (255,), width=8)
    recess(img, (3, 3, 60, 60), tiefe=2)

    # Buchse in der Mitte: erhabener Kragen, versenkter Kern.
    d.rectangle([15, 15, 48, 48], fill=blend(BODY_TOP, LIGHT, 0.25) + (255,))
    raised(img, (15, 15, 48, 48), hoehe=2)
    d.rectangle([20, 20, 43, 43], fill=blend(BODY_MID, EDGE, 0.45) + (255,))
    recess(img, (20, 20, 43, 43), tiefe=3)
    ao(img, (20, 20, 43, 43), depth=3, strength=0.5)

    # Vier Kontakte im Kern, einer je Bahn.
    for x, y in ((25, 25), (37, 25), (25, 37), (37, 37)):
        d.rectangle([x, y, x + 3, y + 3], fill=blend(BRASS, EDGE, 0.25) + (255,))
        d.point((x, y), fill=BRASS_HI + (255,))

    for x, y in ((7, 7), (56, 7), (7, 56), (56, 56)):
        rivet(img, x, y, r=2)
    scratches(img, seed=62)
    return img


# Die vier Bahnen und „aus". Farbe und Anzahl heller Ecken sagen dasselbe —
# wer Farben schlecht unterscheidet, zählt die Ecken.
LANE_COLOURS = [
    ((52, 56, 60), (86, 90, 94)),
    ((236, 168, 48), (255, 226, 150)),
    ((64, 196, 224), (176, 244, 255)),
    ((214, 92, 196), (250, 186, 240)),
    ((132, 216, 72), (208, 255, 168)),
]


def router_lanes():
    """Ein Streifen aus fünf Kacheln: aus, Bahn 1 bis 4.

    <b>Der Ring liegt am Rand, nicht in der Mitte.</b> Ein dickes Kabel deckt
    die mittleren zehn Blockpixel ab — eine Kennung dort wäre genau dann
    verdeckt, wenn die Seite angeschlossen ist, also immer dann, wenn man sie
    lesen will.
    """
    tiles = len(LANE_COLOURS)
    strip = Image.new("RGBA", (N * tiles, N), (0, 0, 0, 0))
    for lane, (base, bright) in enumerate(LANE_COLOURS):
        tile = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        d = ImageDraw.Draw(tile)
        # Ring: außen bei 4, innen bei 12 — bleibt vor dem Kabel sichtbar.
        d.rectangle([4, 4, 59, 59], fill=base + (255,))
        d.rectangle([12, 12, 51, 51], fill=(0, 0, 0, 0))
        # Dunkle Fase innen und außen, damit der Ring Tiefe bekommt.
        d.rectangle([4, 4, 59, 59], outline=_dunkler(base + (255,), 0.45))
        d.rectangle([12, 12, 51, 51], outline=_dunkler(base + (255,), 0.55))
        # Tiefe: der Rahmen liegt erhaben auf, das Loch in der Mitte
        # geht hinunter. Ohne die Umkehr wirkt der Ring aufgemalt.
        raised(tile, (4, 4, 59, 59), hoehe=2)
        recess(tile, (11, 11, 52, 52), tiefe=2)
        # So viele Ecken hell, wie die Bahn zählt.
        corners = [(4, 4), (48, 4), (48, 48), (4, 48)]
        for i in range(lane):
            x, y = corners[i]
            d.rectangle([x, y, x + 11, y + 11], fill=bright + (255,))
            raised(tile, (x, y, x + 11, y + 11), hoehe=1)
            ao(tile, (x, y, x + 11, y + 11), depth=2, strength=0.30)
        if lane == 0:
            # „Aus" bekommt Lücken in der Mitte jeder Kante: gebrochen statt
            # nur dunkel — das liest man auch aus der Entfernung.
            for box in ([26, 2, 37, 13], [26, 50, 37, 61],
                        [2, 26, 13, 37], [50, 26, 61, 37]):
                d.rectangle(box, fill=(0, 0, 0, 0))
        strip.paste(tile, (lane * N, 0))
    return strip


# ---- Serverschrank und Prozessoren ---------------------------------------
# Formensprache: senkrechte Einschübe statt der liegenden Schächte des
# Laufwerks. Man soll die beiden Blöcke im Regal auseinanderhalten können,
# ohne den Namen zu lesen.

def rack_front():
    """Acht senkrechte Einschübe mit Betriebslämpchen."""
    img = surface(seed=71)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    d.rectangle([6, 8, 57, 55], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    grain(img, amount=6, seed=72)
    recess(img, (6, 8, 57, 55), tiefe=2)

    for i in range(8):
        x = 8 + i * 6
        d.rectangle([x, 11, x + 3, 46], fill=blend(EDGE, BODY_BOT, 0.45) + (255,))
        ao(img, (x, 11, x + 3, 46), depth=2, strength=0.45)
        # Griff oben, Lämpchen unten — so herum liest man den Einschub.
        d.line([(x, 13), (x + 3, 13)], fill=blend(LIGHT, EDGE, 0.3) + (255,))
        d.rectangle([x + 1, 42, x + 2, 44], fill=blend(ACCENT, EDGE, 0.15) + (255,))

    # Lüftungsschlitze unter den Einschüben.
    for y in (49, 52):
        for x in range(9, 54, 4):
            d.line([(x, y), (x + 2, y)], fill=blend(EDGE, BODY_BOT, 0.3) + (255,))

    for x, y in ((3, 4), (60, 4), (3, 59), (60, 59)):
        rivet(img, x, y, r=2)
    scratches(img, seed=73)
    return img


def processor(gross=False):
    """Ein Chip mit Kühlkörper. Der grosse hat zwei Kerne und mehr Beine."""
    ton = (150, 172, 156) if not gross else (168, 152, 186)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([14, 14, 49, 49], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.2),
                                       blend(ton, EDGE, 0.4), seed=270))
    d = ImageDraw.Draw(img)
    d.rectangle([14, 14, 49, 49], outline=EDGE + (255,))
    raised(img, (14, 14, 49, 49), hoehe=2)

    # Beine an allen vier Seiten: Ein Chip ohne Beine liest sich als Kachel.
    beine = range(18, 46, 5) if not gross else range(17, 47, 4)
    for p in beine:
        for box in ([p, 9, p + 2, 13], [p, 50, p + 2, 54],
                    [9, p, 13, p + 2], [50, p, 54, p + 2]):
            d.rectangle(box, fill=BRASS + (255,))
            d.point((box[0], box[1]), fill=BRASS_HI + (255,))

    # Kühlkörper in der Mitte, versenkt und mit Rippen.
    d.rectangle([19, 19, 44, 44], fill=blend(BODY_MID, EDGE, 0.35) + (255,))
    recess(img, (19, 19, 44, 44), tiefe=2)
    ao(img, (19, 19, 44, 44), depth=3, strength=0.5)
    for x in range(22, 43, 3):
        d.line([(x, 22), (x, 41)], fill=blend(LIGHT, EDGE, 0.35) + (255,))

    # Der Kern glüht — einer beim kleinen, zwei beim grossen.
    kerne = [(27, 27, 36, 36)] if not gross else [(23, 23, 30, 30), (33, 33, 40, 40)]
    for kern in kerne:
        glow(img, list(kern), color=ACCENT, radius=5, strength=110)
    d = ImageDraw.Draw(img)
    for kern in kerne:
        d.rectangle(list(kern), fill=blend(ACCENT, EDGE, 0.25) + (255,))
        d.rectangle(list(kern), outline=ACCENT_HI + (255,))
    scratches(img, count=2, seed=271 if not gross else 272)
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
    save(router_side(), "block", "router_side")
    save(rack_front(), "block", "server_rack_front")
    save(router_lanes(), "misc", "router_lanes")
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
    for label in ("64", "256", "1024", "4096"):
        save(fluid_cell(label), "item", "fluid_cell_" + label)
    save(processor(False), "item", "processor")
    save(processor(True), "item", "co_processor")


if __name__ == "__main__":
    main()
