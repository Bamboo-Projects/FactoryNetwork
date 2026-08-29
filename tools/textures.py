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


def controller_extension():
    """Der Anbau: dieselbe Familie, aber ohne Kern.

    <b>Er darf dem Controller nicht zu ähnlich sehen.</b> Wer eine Wand aus
    beidem baut, muss auf einen Blick wissen, welcher der eine ist, den es nur
    einmal gibt — deshalb fehlt hier der leuchtende Kern, und an seiner Stelle
    sitzt ein Anschlussfeld: vier Buchsen, weil der Anbau genau dafür da ist,
    Seiten beizusteuern.
    """
    img = surface(seed=57)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Dieselbe versenkte Fassung wie beim Controller — die Verwandtschaft.
    d.rectangle([9, 9, 54, 54], fill=blend(BODY_MID, EDGE, 0.3) + (255,))
    grain(img, amount=7, seed=58)
    recess(img, (9, 9, 54, 54), tiefe=2)
    ao(img, (9, 9, 54, 54), depth=4, strength=0.4)

    # Vier Buchsen statt eines Kerns, jede mit einem schwachen Lämpchen.
    for x, y in ((17, 17), (35, 17), (17, 35), (35, 35)):
        d.rectangle([x, y, x + 11, y + 11], fill=EDGE + (255,))
        ao(img, (x, y, x + 11, y + 11), depth=2, strength=0.45)
        d = ImageDraw.Draw(img)
        d.rectangle([x + 3, y + 3, x + 8, y + 8],
                    fill=blend(ACCENT, EDGE, 0.55) + (255,))
        d.line([(x + 3, y + 3), (x + 8, y + 3)],
               fill=blend(ACCENT_HI, EDGE, 0.4) + (255,))

    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        rivet(img, x, y)
    scratches(img, count=3, seed=59)
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


# Welche Seite einer Tafel an eine andere stößt. Dieselben vier Namen wie
# im Blockzustand, und dieselbe Reihenfolge — die Zahl im Dateinamen ist
# genau diese Maske.
EDGE_UP, EDGE_DOWN, EDGE_LEFT, EDGE_RIGHT = 1, 2, 4, 8

SCREEN = (12, 16, 13)
SCREEN_SHEEN = (28, 40, 32)

# Wie breit der Rahmen ist, in Texturpixeln der 64er-Auflösung.
FRAME = 5


def display_front(joined=0):
    """Ein dunkler Schirm mit schmalem Rahmen.

    Er soll Text tragen und nicht selbst auffallen — deshalb der schmale
    Rahmen. <b>Und deshalb fällt er weg, wo eine zweite Tafel anschließt:</b>
    Sechs Tafeln nebeneinander sollen ein Bildschirm sein und kein Gitter aus
    sechs Fenstern. ``joined`` sagt, welche Seiten anschließen.
    """
    base = surface(BODY_MID, BODY_BOT)
    img = base.copy()
    d = ImageDraw.Draw(img)
    bevel(d, width=2)

    # Wo eine Nachbartafel anschließt, gibt es keine Außenkante. Ohne das
    # bleiben in der Rahmenleiste kurze helle Striche stehen, genau dort, wo
    # zwei Tafeln aneinanderstoßen — und die verraten das Raster.
    if joined & EDGE_LEFT:
        img.paste(base.crop((0, 0, 2, N)), (0, 0))
    if joined & EDGE_RIGHT:
        img.paste(base.crop((N - 2, 0, N, N)), (N - 2, 0))
    if joined & EDGE_UP:
        img.paste(base.crop((0, 0, N, 2)), (0, 0))
    if joined & EDGE_DOWN:
        img.paste(base.crop((0, N - 2, N, N)), (0, N - 2))
    d = ImageDraw.Draw(img)

    # Der Schirm wächst über den Rahmen hinaus, wo eine Nachbartafel steht.
    links = 0 if joined & EDGE_LEFT else FRAME
    rechts = N - 1 if joined & EDGE_RIGHT else N - 1 - FRAME
    oben = 0 if joined & EDGE_UP else FRAME
    unten = N - 1 if joined & EDGE_DOWN else N - 1 - FRAME

    # Die dunkle Kante nur dort, wo auch ein Rahmen ist.
    d.rectangle([max(0, links - 2), max(0, oben - 2),
                 min(N - 1, rechts + 2), min(N - 1, unten + 2)],
                fill=EDGE + (255,))
    d.rectangle([links, oben, rechts, unten], fill=SCREEN + (255,))
    # Ein feiner Schimmer am oberen Rand, damit die Fläche nicht tot wirkt.
    # Nur an der obersten Tafel: Sonst zöge sich ein heller Strich quer durch
    # die Wand, wo zwei Reihen aneinanderstoßen.
    if not joined & EDGE_UP:
        d.line([(links + 1, oben + 1), (rechts - 1, oben + 1)],
               fill=SCREEN_SHEEN + (255,))
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


def wireless_terminal():
    """Ein Handgerät: Bildschirm, Tastenfeld, kurze Antenne.

    <b>Warum hochkant und nicht quer.</b> Der Laptop steht daneben und ist
    breit. Zwei Geräte, die dasselbe tun, müssen sich in der Silhouette
    unterscheiden — im Inventar sieht man nichts anderes.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    body = (14, 8, 49, 58)
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle(body, radius=5, fill=255)
    img.alpha_composite(masked_surface(mask, BODY_TOP, BODY_BOT, seed=311))

    d = ImageDraw.Draw(img)
    d.rounded_rectangle(body, radius=5, outline=EDGE + (255,))

    # Der Bildschirm sitzt oben und ist der einzige helle Fleck.
    screen = (19, 13, 44, 33)
    d.rounded_rectangle(screen, radius=2, fill=ACCENT_DIM + (255,),
                        outline=EDGE + (255,))
    glow(img, screen, ACCENT, radius=5, strength=120)
    d = ImageDraw.Draw(img)
    # Drei Zeilen: das Gerät zeigt Bestände, keine Bilder.
    for i, y in enumerate((18, 23, 28)):
        d.line([(22, y), (22 + [16, 12, 18][i], y)], fill=ACCENT + (210,))

    # Tastenfeld: drei Reihen zu drei Tasten.
    for row in range(3):
        for col in range(3):
            x = 20 + col * 8
            y = 38 + row * 6
            d.rectangle((x, y, x + 5, y + 3), fill=LIGHT + (255,),
                        outline=EDGE + (255,))

    # Die Antenne macht klar, dass es ohne Kabel geht.
    d.line([(41, 8), (46, 2)], fill=PLATE_BOT + (255,), width=2)
    d.ellipse((44, 0, 49, 5), fill=ACCENT + (255,), outline=EDGE + (255,))

    bevel(d, body, width=2)
    grain(img, amount=6, seed=312)
    return img


def laptop():
    """Aufgeklappt: Bildschirm hinten, Tastatur vorn.

    Die Silhouette ist die eines aufgeschlagenen Buchs — von der des
    Handgeräts auf einen Blick zu unterscheiden, auch bei 16 Pixeln.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    # Der Deckel steht leicht nach hinten geneigt.
    lid = [(9, 30), (15, 4), (55, 4), (55, 30)]
    lid_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(lid_mask).polygon(lid, fill=255)
    img.alpha_composite(masked_surface(lid_mask, BODY_TOP, BODY_MID, seed=321))

    d = ImageDraw.Draw(img)
    d.polygon(lid, outline=EDGE + (255,))

    screen = (17, 8, 51, 27)
    d.rectangle(screen, fill=ACCENT_DIM + (255,), outline=EDGE + (255,))
    glow(img, screen, ACCENT, radius=6, strength=135)
    d = ImageDraw.Draw(img)
    # Auf dem Laptop steht Code: eingerückte Zeilen, keine Balken.
    for y, x0, x1 in ((12, 20, 40), (16, 24, 46), (20, 24, 36), (24, 20, 43)):
        d.line([(x0, y), (x1, y)], fill=ACCENT + (200,))

    # Die Grundplatte liegt flach davor.
    base = [(4, 32), (60, 32), (54, 46), (10, 46)]
    base_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(base_mask).polygon(base, fill=255)
    img.alpha_composite(masked_surface(base_mask, LIGHT, BODY_MID, seed=322))
    d = ImageDraw.Draw(img)
    d.polygon(base, outline=EDGE + (255,))

    # Tastatur: die Reihen verjüngen sich nach vorn, das gibt die Neigung.
    for row, (y, inset) in enumerate(((35, 9), (39, 11), (43, 13))):
        for col in range(6):
            step = (62 - 2 * inset) / 6.0
            x = inset + col * step
            d.rectangle((x + 1, y, x + step - 2, y + 2),
                        fill=BODY_BOT + (255,))

    # Ein Streifen Glanz auf der Vorderkante, sonst wirkt die Platte flach.
    d.line([(11, 45), (53, 45)], fill=SHINE + (150,))
    grain(img, amount=5, seed=323)
    return img


def entanglement():
    """Zwei Hälften, die einander halten.

    <b>Zwei Bögen, die sich nicht berühren.</b> Das ist der ganze Gedanke:
    Was zusammengehört, muss nicht aneinanderliegen. Der Spalt dazwischen
    leuchtet, weil dort die Verbindung sitzt und nicht im Material.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Die beiden Hälften: gespiegelte Sicheln um die Mitte.
    links = [(16, 12), (30, 22), (30, 42), (16, 52), (10, 42), (10, 22)]
    rechts = [(48, 12), (34, 22), (34, 42), (48, 52), (54, 42), (54, 22)]
    for form, seed in ((links, 401), (rechts, 402)):
        mask = Image.new("L", (N, N), 0)
        ImageDraw.Draw(mask).polygon(form, fill=255)
        img.alpha_composite(masked_surface(mask, CRYSTAL_HI, CRYSTAL, seed=seed))
        ImageDraw.Draw(img).polygon(form, outline=EDGE + (255,))

    # Der Spalt in der Mitte trägt das Leuchten.
    glow(img, (30, 20, 34, 44), CRYSTAL_HI, radius=9, strength=190)
    d = ImageDraw.Draw(img)
    for y in range(22, 43, 5):
        d.line([(30, y), (34, y + 2)], fill=ACCENT_HI + (220,))

    # Zwei Glanzkanten, damit die Sicheln rund wirken.
    d.line([(14, 20), (12, 40)], fill=CRYSTAL_HI + (255,))
    d.line([(50, 20), (52, 40)], fill=CRYSTAL_HI + (255,))
    grain(img, amount=5, seed=403)
    return img


def bridge_side():
    """Der Körper: schwere Platte mit einer waagerechten Fuge und Nieten."""
    img = surface(seed=421)
    brushed(img, count=20, seed=422)
    d = ImageDraw.Draw(img)

    # Eine breite Fuge auf halber Höhe — sie trennt Sockel von Körper, auch
    # wenn beide dieselbe Textur tragen.
    recess(img, (0, 28, 63, 35), tiefe=3)
    d = ImageDraw.Draw(img)
    d.line([(0, 31), (63, 31)], fill=ACCENT_DIM + (255,))

    for x, y in ((8, 12), (55, 12), (8, 51), (55, 51)):
        rivet(img, x, y, r=3)
    bevel(ImageDraw.Draw(img), width=3)
    grain(img, amount=7, seed=423)
    return img


def bridge_socket(on=False):
    """Die Fassung: ein Ring, in dem etwas sitzt, das man nicht ganz sieht.

    Leuchtet, sobald die Gegenstelle antwortet.
    """
    img = surface(top=BODY_MID, bottom=BODY_BOT, seed=424)
    d = ImageDraw.Draw(img)

    # Ein tiefer Ring, innen leuchtend.
    d.ellipse((10, 10, 53, 53), outline=EDGE + (255,), width=3)
    recess(img, (16, 16, 47, 47), tiefe=4)
    glow(img, (20, 20, 43, 43), CRYSTAL_HI, radius=10,
         strength=240 if on else 120)

    d = ImageDraw.Draw(img)
    d.ellipse((22, 22, 41, 41),
              fill=(CRYSTAL_HI if on else CRYSTAL) + (230,),
              outline=EDGE + (255,))
    # Zwei Bögen darin: dieselbe Form wie auf der Verschränkung selbst.
    d.arc((24, 24, 39, 39), start=110, end=250, fill=CRYSTAL_HI + (255,), width=2)
    d.arc((24, 24, 39, 39), start=290, end=70, fill=CRYSTAL_HI + (255,), width=2)

    bevel(ImageDraw.Draw(img), width=3)
    grain(img, amount=5, seed=425)
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


ENERGY_CELL_TONE = {
    "64k": (168, 148, 112),
    "256k": (180, 144, 96),
    "1024k": (190, 138, 82),
    "4096k": (198, 128, 68),
}


def energy_cell(label):
    """Energiezelle: dieselbe Kassette, aber mit Ladebalken statt Schauglas.

    Vier Segmente übereinander, drei davon warm leuchtend. Wer eine Zelle in
    der Hand hat, soll auf einen Blick sehen, was hineingehört, ohne den Namen
    zu lesen — bei der Flüssigkeitszelle tut das der Stand im Glas, hier die
    Balkenreihe.
    """
    ton = ENERGY_CELL_TONE[label]
    img = cell_shell(ton, seed=240)
    d = ImageDraw.Draw(img)

    # Das Fenster: versenkt wie bei den anderen, damit die Reihe stimmt.
    d.rectangle([19, 19, 45, 37], fill=blend(BODY_MID, EDGE, 0.55) + (255,))
    recess(img, (19, 19, 45, 37), tiefe=2)
    ao(img, (19, 19, 45, 37), depth=3, strength=0.45)

    # Die Ladung: vier Segmente, das oberste dunkel.
    warm = blend(ton, (255, 190, 70), 0.62)
    for i, y in enumerate((34, 30, 26, 22)):
        if i < 3:
            glow(img, [23, y, 41, y + 2], color=warm, radius=3, strength=95)
            d = ImageDraw.Draw(img)
            d.rectangle([23, y, 41, y + 2], fill=warm + (255,))
            d.line([(23, y), (41, y)], fill=_heller(warm + (255,), 0.5))
        else:
            d.rectangle([23, y, 41, y + 2], fill=_dunkler(ton + (255,), 0.45))

    # Zwei Pole oben auf dem Gehäuse — der Unterschied auf den zweiten Blick.
    for x in (24, 38):
        d.rectangle([x, 15, x + 3, 18], fill=BRASS + (255,))
        d.line([(x, 15), (x + 2, 15)], fill=BRASS_HI + (255,))

    cell_contacts(img, d)
    scratches(img, count=2, seed=241)
    return img


def fabricator_top():
    """Die Oberseite: ein Gitter aus neun Feldern.

    <b>Das Bild sagt, was er tut.</b> Neun Felder sind die Werkbank, und wer
    sie sieht, weiß, dass hier gebaut wird — ohne den Namen zu lesen. Der Kern
    des Controllers fehlt: Der Fabricator hält nichts, er arbeitet nur.
    """
    img = surface(seed=61)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    d.rectangle([8, 8, 55, 55], fill=blend(BODY_MID, EDGE, 0.32) + (255,))
    recess(img, (8, 8, 55, 55), tiefe=2)
    ao(img, (8, 8, 55, 55), depth=3, strength=0.4)

    # Neun Felder, das mittlere hell — dort entsteht etwas.
    for reihe in range(3):
        for spalte in range(3):
            x = 11 + spalte * 15
            y = 11 + reihe * 15
            mitte = reihe == 1 and spalte == 1
            if mitte:
                glow(img, [x, y, x + 11, y + 11], color=ACCENT, radius=4, strength=110)
                d = ImageDraw.Draw(img)
                d.rectangle([x, y, x + 11, y + 11], fill=ACCENT + (255,))
                d.rectangle([x + 3, y + 3, x + 8, y + 8], fill=ACCENT_HI + (255,))
            else:
                d.rectangle([x, y, x + 11, y + 11], fill=blend(EDGE, BODY_BOT, 0.4) + (255,))
                ao(img, (x, y, x + 11, y + 11), depth=2, strength=0.45)
                d = ImageDraw.Draw(img)

    for x, y in ((4, 4), (59, 4), (4, 59), (59, 59)):
        rivet(img, x, y)
    scratches(img, count=3, seed=62)
    return img


def fabricator_side():
    """Die Seite: Lamellen oben, ein Schauglas mit Werkzeugspur darunter."""
    img = surface(seed=63)
    d = ImageDraw.Draw(img)
    bevel(d)
    for y in range(12, 27, 5):
        d.rectangle([12, y, 51, y + 1], fill=EDGE + (255,))
        d.line([(12, y + 2), (51, y + 2)], fill=LIGHT + (255,))

    d.rectangle([12, 33, 51, 52], fill=blend(BODY_MID, EDGE, 0.5) + (255,))
    recess(img, (12, 33, 51, 52), tiefe=2)
    glow(img, [16, 37, 47, 48], color=ACCENT, radius=5, strength=70)
    d = ImageDraw.Draw(img)
    d.rectangle([16, 37, 47, 48], fill=blend(ACCENT, EDGE, 0.62) + (255,))
    # Drei Striche wie Werkzeugspuren im Glas.
    for x in (22, 31, 40):
        d.line([(x, 39), (x, 46)], fill=blend(ACCENT_HI, EDGE, 0.35) + (255,))
    scratches(img, count=2, seed=64)
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
def router_lanes():
    """Zwei Kacheln: abgeklemmt und offen.

    <b>Grau, weil die Farbe beim Zeichnen kommt.</b> Seit der Router Farben
    führt statt vier Bahnen, gäbe es achtzehn Zustände — ein Streifen mit
    achtzehn Kacheln wäre eine Textur, die niemand mehr nachzeichnen kann.
    Der Ring ist grau und wird eingefärbt, wie das Kabel auch.

    <b>Der Ring liegt am Rand, nicht in der Mitte.</b> Ein dickes Kabel deckt
    die mittleren zehn Blockpixel ab — eine Kennung dort wäre genau dann
    verdeckt, wenn die Seite angeschlossen ist, also immer dann, wenn man sie
    lesen will.
    """
    base = (208, 212, 216)
    strip = Image.new("RGBA", (N * 2, N), (0, 0, 0, 0))
    for offen in (0, 1):
        tile = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        d = ImageDraw.Draw(tile)
        # Ring: außen bei 4, innen bei 12 — bleibt vor dem Kabel sichtbar.
        d.rectangle([4, 4, 59, 59], fill=base + (255,))
        d.rectangle([12, 12, 51, 51], fill=(0, 0, 0, 0))
        # Dunkle Fase innen und außen, damit der Ring Tiefe bekommt.
        d.rectangle([4, 4, 59, 59], outline=_dunkler(base + (255,), 0.45))
        d.rectangle([12, 12, 51, 51], outline=_dunkler(base + (255,), 0.55))
        # Tiefe: der Rahmen liegt erhaben auf, das Loch in der Mitte geht
        # hinunter. Ohne die Umkehr wirkt der Ring aufgemalt.
        raised(tile, (4, 4, 59, 59), hoehe=2)
        recess(tile, (11, 11, 52, 52), tiefe=2)
        if not offen:
            # „Aus" bekommt Lücken in der Mitte jeder Kante: gebrochen statt
            # nur dunkel — das liest man auch aus der Entfernung.
            for box in ([26, 2, 37, 13], [26, 50, 37, 61],
                        [2, 26, 13, 37], [50, 26, 61, 37]):
                d.rectangle(box, fill=(0, 0, 0, 0))
        strip.paste(tile, (offen * N, 0))
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


# ---- Bestückung, die der Renderer über die Front legt ---------------------
#
# Die Front zeigt, was drinsteckt. Gemalt wird sie nicht in die Textur —
# zehn Plätze mit je sechs Zuständen wären über sechzig Millionen
# Blockzustände. Der Renderer legt je Platz ein kleines Stück darüber.

# Ein Schacht des Laufwerks, in Texturpixeln der 64er-Auflösung: 20 breit,
# 6 hoch. Die Kachel hat dieselbe Form, nur doppelt so fein.
BAY_W, BAY_H = 40, 12

DRIVE_BAY_TONE = {
    "k1": (150, 160, 172),
    "k4": (150, 172, 152),
    "k16": (172, 160, 140),
    "k64": (176, 148, 168),
    "fluid": (114, 160, 182),
    "energy": (180, 144, 96),
}


def drive_bays():
    """Ein Streifen: leerer Schacht, die vier Zellgrößen, Fluid- und Energiezelle.

    <b>Die Kachel zeigt die Zelle, nicht ihren Füllstand.</b> Der Inhalt lebt
    im Laufwerk und steht erst beim Sichern im Gegenstand — was der Client
    kennt, wäre der Stand von vorhin. Lieber gar keine Anzeige als eine, die
    hinterherhinkt; wie voll es ist, sagen Jade und das Fenster.
    """
    sorten = ["leer", "k1", "k4", "k16", "k64", "fluid", "energy"]
    streifen = Image.new("RGBA", (BAY_W * len(sorten), BAY_H), (0, 0, 0, 0))
    for i, sorte in enumerate(sorten):
        kachel = Image.new("RGBA", (BAY_W, BAY_H), (0, 0, 0, 0))
        d = ImageDraw.Draw(kachel)
        if sorte == "leer":
            # Leerer Schacht: dunkel und tief, mit einem Steg in der Mitte.
            d.rectangle([0, 0, BAY_W - 1, BAY_H - 1],
                        fill=blend(EDGE, BODY_BOT, 0.35) + (255,))
            ao(kachel, (0, 0, BAY_W - 1, BAY_H - 1), depth=3, strength=0.5)
            d.line([(3, BAY_H // 2), (BAY_W - 4, BAY_H // 2)],
                   fill=blend(EDGE, BODY_BOT, 0.6) + (255,))
        else:
            ton = DRIVE_BAY_TONE[sorte]
            # Die Zelle steckt drin: heller Rücken, Griff links, Lämpchen rechts.
            d.rectangle([0, 0, BAY_W - 1, BAY_H - 1], fill=blend(ton, EDGE, 0.3) + (255,))
            raised(kachel, (0, 0, BAY_W - 1, BAY_H - 1), hoehe=1)
            d.rectangle([2, 2, 6, BAY_H - 3], fill=blend(ton, LIGHT, 0.25) + (255,))
            for x in range(10, BAY_W - 12, 4):
                d.line([(x, 3), (x, BAY_H - 4)], fill=_dunkler(ton + (255,), 0.35))
            licht = ACCENT
            if sorte == "fluid":
                licht = (96, 190, 236)
            elif sorte == "energy":
                licht = (244, 176, 72)
            glow(kachel, [BAY_W - 9, 3, BAY_W - 4, BAY_H - 4], color=licht,
                 radius=3, strength=120)
            d = ImageDraw.Draw(kachel)
            d.rectangle([BAY_W - 9, 3, BAY_W - 4, BAY_H - 4], fill=licht + (255,))
        streifen.paste(kachel, (i * BAY_W, 0))
    return streifen


# Ein Einschub des Schranks: 4 Texturpixel breit, 36 hoch — auch hier doppelt.
# Ein Einschub des Schranks, quer: zehn Blockpixel breit, zwei hoch. Bei
# achtfacher Auflösung sind das achtzig auf sechzehn.
BLADE_W, BLADE_H = 80, 16


def rack_blades():
    """Ein Streifen: leerer Einschub, angefangener, laufender.

    Der mittlere Zustand ist der wichtige. Ein Einschub mit zwei von drei
    Bauteilen sieht sonst aus wie ein voller und rechnet doch nicht — und
    dann sucht man den Fehler im Programm statt im Schrank.
    """
    streifen = Image.new("RGBA", (BLADE_W * 3, BLADE_H), (0, 0, 0, 0))
    for i in range(3):
        streifen.paste(_blade(i), (i * BLADE_W, 0))
    return streifen


def _blade(zustand):
    kachel = Image.new("RGBA", (BLADE_W, BLADE_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(kachel)
    if zustand == 0:
        # Leer: ein sichtbares Fach, kein Loch.
        #
        # Vorher war die Kachel fast schwarz — im Spiel sah ein Schrank mit
        # acht freien Einschüben aus, als fehlte ihm die halbe Front. Man
        # konnte die freien Plätze weder zählen noch als Plätze erkennen.
        # Ein Fach muss man sehen: Boden, Führungsschienen, und eine helle
        # Kante oben, an der das Licht bricht.
        d.rectangle([0, 0, BLADE_W - 1, BLADE_H - 1],
                    fill=blend(EDGE, BODY_BOT, 0.95) + (255,))
        # Der Boden liegt tiefer als die Kante — von oben nach unten heller,
        # das liest das Auge als Vertiefung.
        for y in range(2, BLADE_H - 2):
            t = (y - 2) / float(BLADE_H - 5)
            d.line([(1, y), (BLADE_W - 2, y)],
                   fill=blend(blend(EDGE, BODY_BOT, 0.7),
                              blend(BODY_BOT, LIGHT, 0.15), t) + (255,))
        # Führungsschienen links und rechts, auf denen ein Einschub säße.
        for x in (3, BLADE_W - 4):
            d.line([(x, 2), (x, BLADE_H - 3)], fill=blend(BODY_BOT, LIGHT, 0.3) + (255,))
        # Die obere Kante hell, die untere dunkel: der Fachboden.
        d.line([(0, 0), (BLADE_W - 1, 0)], fill=blend(BODY_BOT, LIGHT, 0.45) + (255,))
        d.line([(0, BLADE_H - 1), (BLADE_W - 1, BLADE_H - 1)], fill=EDGE + (255,))
        return kachel

    # Kühles Blech für den laufenden, warmes für den angefangenen. Die
    # Lämpchen sagen es genauer, aber die sind fünf Pixel breit — auf zehn
    # Metern trägt nur der Ton.
    ton = (154, 164, 176) if zustand == 2 else (132, 124, 112)
    lampe = ACCENT if zustand == 2 else (232, 172, 62)
    d.rectangle([0, 0, BLADE_W - 1, BLADE_H - 1], fill=blend(ton, EDGE, 0.4) + (255,))
    raised(kachel, (0, 0, BLADE_W - 1, BLADE_H - 1), hoehe=1)
    # Griff links, Lüftungsrippen in der Mitte, Lämpchen rechts — so herum
    # liest sich ein Einschub, den man herausziehen würde.
    d.rectangle([2, 3, 9, BLADE_H - 4], fill=blend(ton, LIGHT, 0.35) + (255,))
    d.rectangle([2, 3, 9, BLADE_H - 4], outline=_dunkler(ton + (255,), 0.45))
    for x in range(15, BLADE_W - 18, 6):
        d.line([(x, 4), (x, BLADE_H - 5)], fill=_dunkler(ton + (255,), 0.4))
    if zustand == 2:
        glow(kachel, [BLADE_W - 13, 5, BLADE_W - 5, BLADE_H - 6],
             color=lampe, radius=4, strength=150)
        d = ImageDraw.Draw(kachel)
    d.rectangle([BLADE_W - 13, 5, BLADE_W - 5, BLADE_H - 6], fill=lampe + (255,))
    d.rectangle([BLADE_W - 13, 5, BLADE_W - 5, BLADE_H - 6],
                outline=_heller(lampe + (255,), 0.4))
    return kachel


def rack_frame():
    """Der Rahmen des Schranks: gebürstetes Blech mit Kante und Nieten."""
    img = surface(top=blend(BODY_TOP, LIGHT, 0.2), bottom=BODY_BOT, seed=91)
    brushed(img, count=34, seed=92, strength=9)
    d = ImageDraw.Draw(img)
    bevel(d, (0, 0, N - 1, N - 1), width=3)
    for y in range(7, N - 6, 18):
        rivet(img, 6, y, r=2)
        rivet(img, N - 7, y, r=2)
    scratches(img, count=3, seed=93)
    return img


def rack_inner():
    """Der Boden des Schachts, hinter den Einschüben.

    Dunkel, aber nicht schwarz: Ein leerer Schrank soll wie ein leerer
    Schrank aussehen und nicht wie ein Loch. Die zwei Führungsschienen
    reichen dafür — an ihnen sieht man, dass da etwas hineingehört.
    """
    img = surface(top=blend(BODY_BOT, LIGHT, 0.12), bottom=blend(EDGE, BODY_BOT, 0.5),
                  seed=94)
    d = ImageDraw.Draw(img)
    # Zwei senkrechte Führungsschienen, auf denen die Einschübe sitzen.
    for x in (12, N - 13):
        d.rectangle([x - 2, 2, x + 2, N - 3], fill=blend(BODY_BOT, LIGHT, 0.45) + (255,))
        ao(img, (x - 2, 2, x + 2, N - 3), depth=2, strength=0.4)
    grain(img, amount=5, seed=95)
    return img


# ---- Serverbauteile ------------------------------------------------------
#
# Drei Silhouetten, damit man die Art am Umriss erkennt, und vier Töne,
# damit man die Stufe an der Farbe erkennt. Beides zusammen: Wer zwölf
# Einschübe bestückt, muss die Bauteile im Rucksack auseinanderhalten
# können, ohne jedes einzeln anzusehen.

TIER_TONE = [
    (146, 156, 166),
    (138, 180, 148),
    (132, 158, 206),
    (204, 172, 106),
]


def _tier_glow(img, box, tier):
    glow(img, list(box), color=blend(TIER_TONE[tier], (255, 255, 255), 0.35),
         radius=5, strength=90 + tier * 25)


def server_chassis():
    """Ein flaches Blech mit Griff und drei leeren Steckplätzen.

    Es muss auf einen Blick von den Bauteilen zu unterscheiden sein: Die
    haben eine volle Fläche, das Gehäuse hat Löcher. Wer im Rucksack sucht,
    erkennt den Server daran, dass man durch ihn hindurchsieht.
    """
    ton = (152, 158, 164)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([6, 18, 57, 45], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.25),
                                       blend(ton, EDGE, 0.45), seed=360))
    d = ImageDraw.Draw(img)
    d.rectangle([6, 18, 57, 45], outline=EDGE + (255,))
    raised(img, (6, 18, 57, 45), hoehe=2)

    # Griff links, wie am Einschub an der Blockfront.
    d.rectangle([9, 22, 14, 41], fill=blend(ton, LIGHT, 0.4) + (255,))
    d.rectangle([9, 22, 14, 41], outline=_dunkler(ton + (255,), 0.45))

    # Drei leere Steckplätze — der Grund, warum es ein Gehäuse heißt.
    for i in range(3):
        x = 19 + i * 13
        d.rectangle([x, 23, x + 9, 40], fill=blend(EDGE, BODY_BOT, 0.3) + (255,))
        ao(img, (x, 23, x + 9, 40), depth=2, strength=0.6)
        # Kontaktleiste am Boden des Schachts.
        for k in range(x + 2, x + 9, 2):
            d.point((k, 39), fill=BRASS + (255,))

    # Lüftungsschlitze rechts und ein Nietenpaar.
    for y in range(22, 42, 4):
        d.line([(52, y), (55, y)], fill=_dunkler(ton + (255,), 0.4))
    rivet(img, 8, 20, r=2)
    rivet(img, 8, 43, r=2)
    scratches(img, count=3, seed=361)
    return img


def cpu_item(tier):
    """Ein Chip. Je höher die Stufe, desto mehr Kerne glühen."""
    ton = TIER_TONE[tier]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([14, 14, 49, 49], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.25),
                                       blend(ton, EDGE, 0.45), seed=300 + tier))
    d = ImageDraw.Draw(img)
    d.rectangle([14, 14, 49, 49], outline=EDGE + (255,))
    raised(img, (14, 14, 49, 49), hoehe=2)

    for p in range(18, 46, 5):
        for box in ([p, 9, p + 2, 13], [p, 50, p + 2, 54],
                    [9, p, 13, p + 2], [50, p, 54, p + 2]):
            d.rectangle(box, fill=BRASS + (255,))
            d.point((box[0], box[1]), fill=BRASS_HI + (255,))

    d.rectangle([19, 19, 44, 44], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    recess(img, (19, 19, 44, 44), tiefe=2)
    ao(img, (19, 19, 44, 44), depth=3, strength=0.5)

    # Ein Kern, vier, neun, sechzehn — die Stufe steht als Quadrat da.
    seite = tier + 1
    feld = 24 // seite
    for zy in range(seite):
        for zx in range(seite):
            x0 = 20 + zx * feld
            y0 = 20 + zy * feld
            kern = (x0 + 1, y0 + 1, x0 + feld - 2, y0 + feld - 2)
            _tier_glow(img, kern, tier)
            k = ImageDraw.Draw(img)
            k.rectangle(list(kern), fill=blend(ton, EDGE, 0.2) + (255,))
            k.rectangle(list(kern), outline=_heller(ton + (255,), 0.5))
    scratches(img, count=2, seed=310 + tier)
    return img


def ram_item(tier):
    """Ein Riegel mit Kontaktleiste. Die Stufe steht in der Zahl der Bausteine."""
    ton = TIER_TONE[tier]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([8, 20, 55, 46], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.2),
                                       blend(ton, EDGE, 0.5), seed=320 + tier))
    d = ImageDraw.Draw(img)
    d.rectangle([8, 20, 55, 46], outline=EDGE + (255,))
    raised(img, (8, 20, 55, 46), hoehe=2)

    # Kontaktleiste unten, mit der Kerbe, an der man einen Riegel erkennt.
    d.rectangle([10, 41, 53, 45], fill=BRASS + (255,))
    for x in range(11, 53, 3):
        d.line([(x, 41), (x, 45)], fill=_dunkler(BRASS + (255,), 0.4))
    d.rectangle([28, 41, 33, 45], fill=blend(ton, EDGE, 0.5) + (255,))

    bausteine = 2 + tier * 2
    breite = 42 // bausteine
    for i in range(bausteine):
        x0 = 10 + i * breite
        box = (x0, 24, x0 + breite - 3, 37)
        _tier_glow(img, box, tier)
        k = ImageDraw.Draw(img)
        k.rectangle(list(box), fill=blend(ton, EDGE, 0.3) + (255,))
        k.rectangle(list(box), outline=_heller(ton + (255,), 0.45))
    scratches(img, count=2, seed=330 + tier)
    return img


def disk_item(tier):
    """Eine Platte im Gehäuse. Die Stufe steht in den Ringen der Scheibe."""
    ton = TIER_TONE[tier]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle([10, 12, 53, 51], radius=3, fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.2),
                                       blend(ton, EDGE, 0.5), seed=340 + tier))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([10, 12, 53, 51], radius=3, outline=EDGE + (255,))
    raised(img, (10, 12, 53, 51), hoehe=2)

    # Das Fenster auf die Scheibe.
    d.ellipse([16, 17, 47, 46], fill=blend(BODY_MID, EDGE, 0.45) + (255,))
    ao(img, (16, 17, 47, 46), depth=3, strength=0.5)
    for i in range(tier + 1):
        r = 3 + i * (12 // (tier + 1))
        d.ellipse([31 - r, 31 - r, 32 + r, 32 + r],
                  outline=blend(ton, ACCENT_HI, 0.3) + (255,))
    _tier_glow(img, (29, 29, 34, 34), tier)
    d = ImageDraw.Draw(img)
    d.ellipse([29, 29, 34, 34], fill=_heller(ton + (255,), 0.5))

    # Anschluss an der Seite, damit es nach Datenträger aussieht.
    d.rectangle([12, 44, 24, 49], fill=BRASS + (255,))
    for x in range(13, 24, 3):
        d.line([(x, 44), (x, 49)], fill=_dunkler(BRASS + (255,), 0.4))
    for x, y in ((13, 15), (50, 15), (13, 48), (50, 48)):
        rivet(img, x, y, r=2)
    scratches(img, count=2, seed=350 + tier)
    return img


def creative_source():
    """Ein Block, der leuchtet — er soll nach Werkzeug aussehen, nicht nach Anlage."""
    img = surface(top=(70, 96, 116), bottom=(34, 52, 68), seed=81)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)
    d.rectangle([10, 10, 53, 53], fill=blend((40, 60, 78), EDGE, 0.3) + (255,))
    recess(img, (10, 10, 53, 53), tiefe=2)
    glow(img, [16, 16, 47, 47], color=(120, 210, 255), radius=8, strength=170)
    d = ImageDraw.Draw(img)
    for i, kante in enumerate(range(16, 30, 5)):
        ton = blend((150, 230, 255), (60, 150, 220), i / 3.0)
        d.rectangle([kante, kante, 63 - kante, 63 - kante], outline=ton + (255,), width=2)
    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        rivet(img, x, y, r=2)
    scratches(img, seed=82)
    return img


def burner_front(lit=False):
    """Die Front der Brennkammer: eine Klappe mit Sichtfenster.

    Brennt sie, glüht das Fenster und die Klappe wirft einen warmen Schein.
    Ohne den Unterschied stünde eine leere Kammer aus wie eine laufende.
    """
    img = surface(seed=91)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Klappe mit Scharnieren links
    d.rectangle([10, 12, 53, 51], fill=blend(BODY_TOP, LIGHT, 0.12) + (255,))
    raised(img, (10, 12, 53, 51), hoehe=2)
    for y in (18, 45):
        d.rectangle([7, y, 12, y + 4], fill=blend(BODY_MID, EDGE, 0.2) + (255,))
        raised(img, (7, y, 12, y + 4), hoehe=1)

    # Sichtfenster
    fenster = (18, 20, 45, 43)
    d.rectangle(list(fenster), fill=blend(EDGE, BODY_BOT, 0.45) + (255,))
    recess(img, fenster, tiefe=2)
    ao(img, fenster, depth=3, strength=0.5)
    if lit:
        glow(img, [20, 22, 43, 41], color=(240, 150, 50), radius=8, strength=200)
        d = ImageDraw.Draw(img)
        d.rectangle([20, 22, 43, 41], fill=(206, 92, 30, 255))
        # Glut unten, Flamme darüber
        d.rectangle([21, 36, 42, 40], fill=(246, 176, 66, 255))
        d.polygon([(31, 24), (37, 32), (34, 39), (28, 39), (25, 32)],
                  fill=(252, 214, 128, 255))
    else:
        for x in range(21, 43, 6):
            d.line([(x, 23), (x, 40)], fill=blend(EDGE, BODY_BOT, 0.6) + (255,))

    # Griff rechts
    d.rectangle([48, 28, 51, 36], fill=BRASS + (255,))
    raised(img, (48, 28, 51, 36), hoehe=1)

    for x, y in ((4, 5), (59, 5), (4, 58), (59, 58)):
        rivet(img, x, y, r=2)
    scratches(img, seed=92)
    return img


def upgrade_card(ton, zeichen):
    """Eine Karte: Platine mit Kontaktleiste und einem Zeichen darauf.

    <b>Alle Karten teilen sich die Form</b> — was eine tut, sagt allein das
    Zeichen. Wer drei verschiedene Formen malt, macht aus einem Ausbausystem
    drei Einzelstücke, und der Spieler muss jede einzeln lernen.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([12, 16, 51, 47], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.25),
                                       blend(ton, EDGE, 0.45), seed=700))
    d = ImageDraw.Draw(img)
    d.rectangle([12, 16, 51, 47], outline=EDGE + (255,))
    raised(img, (12, 16, 51, 47), hoehe=2)

    # Die Kontaktleiste unten: daran erkennt man auf einen Blick, dass es
    # etwas zum Hineinstecken ist.
    for x in range(16, 48, 6):
        d.rectangle([x, 44, x + 3, 47], fill=BRASS + (255,))
        d.point((x, 44), fill=BRASS_HI + (255,))

    d.rectangle([18, 21, 45, 40], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    recess(img, (18, 21, 45, 40), tiefe=2)
    zeichen(d)
    scratches(img, seed=701)
    return img


def range_card():
    """Drei Bögen: ein Signal, das nach außen läuft."""
    def zeichen(d):
        for i, weite in enumerate((5, 10, 15)):
            farbe = blend(ACCENT, SCREEN, 0.15 + i * 0.25) + (255,)
            d.arc([31 - weite, 36 - weite, 31 + weite, 36 + weite],
                  start=200, end=340, fill=farbe, width=2)
        d.rectangle([30, 35, 32, 37], fill=ACCENT + (255,))
    return upgrade_card(BODY_TOP, zeichen)


def infinity_card():
    """Die liegende Acht — die Grenze, die es nicht mehr gibt."""
    def zeichen(d):
        farbe = blend(ACCENT, LIGHT, 0.35) + (255,)
        d.ellipse([21, 25, 31, 36], outline=farbe, width=2)
        d.ellipse([32, 25, 42, 36], outline=farbe, width=2)
        d.point((31, 30), fill=ACCENT_HI + (255,))
        d.point((31, 31), fill=ACCENT_HI + (255,))
    return upgrade_card(blend(BODY_TOP, ACCENT, 0.12), zeichen)


def wireless_module():
    """Ein Modul: kürzer als eine Karte, mit Antenne statt Zeichen.

    <b>Es sieht absichtlich anders aus als eine Karte.</b> Wer im Inventar
    steht, soll ohne Tooltip sehen, ob er eine Fähigkeit oder einen Wert in
    der Hand hat.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([14, 28, 49, 47], fill=255)
    img.alpha_composite(masked_surface(mask, blend(BODY_TOP, LIGHT, 0.25),
                                       blend(BODY_MID, EDGE, 0.45), seed=702))
    d = ImageDraw.Draw(img)
    d.rectangle([14, 28, 49, 47], outline=EDGE + (255,))
    raised(img, (14, 28, 49, 47), hoehe=2)
    for x in range(18, 48, 6):
        d.rectangle([x, 44, x + 3, 47], fill=BRASS + (255,))

    # Die Antenne steht über dem Korpus: die Fähigkeit, die das Modul gibt.
    d.rectangle([30, 14, 32, 30], fill=blend(BODY_TOP, LIGHT, 0.4) + (255,))
    d.rectangle([28, 30, 34, 33], fill=blend(BODY_MID, EDGE, 0.3) + (255,))
    for i, weite in enumerate((5, 9)):
        farbe = blend(ACCENT, LIGHT, 0.1 + i * 0.3) + (255,)
        d.arc([31 - weite, 15 - weite, 31 + weite, 15 + weite],
              start=200, end=340, fill=farbe, width=2)
    d.rectangle([30, 13, 32, 15], fill=ACCENT_HI + (255,))
    scratches(img, seed=703)
    return img


def mast_side():
    """Der Mast: Blech mit einer senkrechten Naht und Nieten.

    Die Naht läuft längs, weil der Block hoch ist und nicht breit — dieselbe
    Textur liegt auf Sockel, Schaft und Auslegern, und was dort quer liefe,
    sähe an jedem der drei anders aus.
    """
    img = surface(seed=81)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Die Naht in der Mitte, längs — und ohne Nieten darin.
    #
    # <b>Der erste Wurf hatte alle neun Texturpixel eine.</b> Der Mast
    # besteht aus sieben kleinen Kästen, und jeder schneidet seinen Ausschnitt
    # an seiner Weltposition: Auf einem Schaft von sechs Blockpixeln erwischte
    # fast jede Fläche eine Niete, und das Ganze sah aus wie ein Nietenteppich.
    # Die vier an den Ecken der Textur reichen — die trifft nur, wer die
    # Blockkante trifft.
    d.rectangle([28, 6, 35, 57], fill=blend(BODY_MID, EDGE, 0.35) + (255,))
    recess(img, (28, 6, 35, 57), tiefe=2)

    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        rivet(img, x, y, r=2)
    scratches(img, seed=82)
    return img



def wrench():
    """Ein Schraubenschlüssel: Maul, Schaft, Griff.

    <b>Er sieht aus wie Werkzeug und nicht wie Bauteil.</b> Die Gegenstände
    dieser Mod sind sonst Platinen und Gehäuse — flach, rechteckig, mit
    Kontaktleisten. Ein Werkzeug muss man in der Hotbar davon unterscheiden
    können, ohne den Namen zu lesen.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Der Schaft, schräg von unten links nach oben rechts.
    schaft = Image.new("L", (N, N), 0)
    ImageDraw.Draw(schaft).polygon(
        [(18, 46), (24, 52), (46, 24), (40, 18)], fill=255)
    img.alpha_composite(masked_surface(schaft, blend(BODY_TOP, LIGHT, 0.3),
                                       blend(BODY_MID, EDGE, 0.35), seed=801))

    # Das Maul oben: ein offenes Sechseck, aus dem ein Keil fehlt.
    maul = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(maul)
    md.ellipse([36, 12, 54, 30], fill=255)
    md.ellipse([41, 17, 49, 25], fill=0)
    md.polygon([(45, 10), (56, 16), (56, 10)], fill=0)
    img.alpha_composite(masked_surface(maul, blend(BODY_TOP, LIGHT, 0.35),
                                       blend(BODY_MID, EDGE, 0.3), seed=802))

    # Der Griff unten, umwickelt: zwei Töne im Wechsel, damit man sieht,
    # wo man ihn anfasst.
    griff = Image.new("L", (N, N), 0)
    ImageDraw.Draw(griff).polygon(
        [(10, 54), (16, 60), (28, 48), (22, 42)], fill=255)
    img.alpha_composite(masked_surface(griff, blend(BRASS, LIGHT, 0.2),
                                       blend(BRASS, EDGE, 0.4), seed=803))
    for i in range(4):
        d.line([(13 + i * 4, 57 - i * 4), (19 + i * 4, 51 - i * 4)],
               fill=blend(BRASS, EDGE, 0.55) + (255,))

    scratches(img, seed=804)
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
    for joined in range(16):
        save(display_front(joined), "block", "display_front_%d" % joined)
    save(display_side(), "block", "display_side")
    save(drive_front(), "block", "drive_front")
    save(press_front(), "block", "press_front")
    save(router_side(), "block", "router_side")
    save(rack_front(), "block", "server_rack_front")
    save(rack_frame(), "block", "server_rack_frame")
    save(rack_inner(), "block", "server_rack_inner")
    save(creative_source(), "block", "creative_source")
    save(burner_front(False), "block", "burner_front")
    save(burner_front(True), "block", "burner_front_on")
    save(drive_bays(), "misc", "drive_bays")
    save(rack_blades(), "misc", "rack_blades")
    save(router_lanes(), "misc", "router_lanes")
    save(crystal_ore(False), "block", "crystal_ore")
    save(crystal_ore(True), "block", "deepslate_crystal_ore")
    print("Gegenstandstexturen:")
    save(label_gun(), "item", "label_gun")
    save(mast_side(), "block", "mast_side")
    save(wrench(), "item", "wrench")
    save(range_card(), "item", "range_card")
    save(infinity_card(), "item", "infinity_card")
    save(wireless_module(), "item", "wireless_module")
    save(controller_extension(), "block", "controller_extension")
    save(fabricator_top(), "block", "fabricator_top")
    save(fabricator_side(), "block", "fabricator_side")
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
    for label in ("64k", "256k", "1024k", "4096k"):
        save(energy_cell(label), "item", "energy_cell_" + label)
    save(server_chassis(), "item", "server_chassis")
    save(entanglement(), "item", "entanglement")
    save(bridge_side(), "block", "bridge_side")
    save(bridge_socket(), "block", "bridge_socket")
    save(bridge_socket(on=True), "block", "bridge_socket_on")
    save(wireless_terminal(), "item", "wireless_terminal")
    save(laptop(), "item", "laptop")
    for tier, wert in enumerate((2, 8, 32, 128)):
        save(cpu_item(tier), "item", "cpu_%d" % wert)
    for tier, wert in enumerate((8, 32, 128, 512)):
        save(ram_item(tier), "item", "ram_%d" % wert)
    for tier, wert in enumerate((64, 256, 1024, 4096)):
        save(disk_item(tier), "item", "disk_%d" % wert)


if __name__ == "__main__":
    main()
