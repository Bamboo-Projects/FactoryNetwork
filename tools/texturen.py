# -*- coding: utf-8 -*-
"""Zwei Texturen, die nicht zur Familie gehörten.

<b>Vorsicht: Dieses Skript überschreibt gateway.png und status_light.png.</b>
Wer eine davon von Hand nachbessert, muss sie hier nachziehen oder den
Aufruf unten entfernen — sonst ist die Handarbeit beim nächsten Lauf weg.

Es steht hier und nicht im Papierkorb, weil seine Zahlen gemessen sind und
nicht geraten: Rahmenmaße, Körnung und Grüntöne stammen aus den Texturen der
Familie, und wer die Familie ändert, misst hier nach.


`status_light` war ein weißes Rechteck mit hellgrauem Rand — ohne Körnung,
ohne Fase, ohne einen Ton der Familie. Es sitzt an jedem Anschluss.

`gateway` hatte zwar den Rahmen der Familie, aber ein Karo als Grund. Ein
Schachbrett heißt in Minecraft „hier fehlt eine Textur", und der Torbogen lag
flach darauf statt darin.

Beides wird hier aus dem gebaut, was schon da ist: Rahmen, Nieten, Körnung und
Grüntöne stammen aus `controller_extension`.
"""
import math
import random

from PIL import Image

import os

# Vom Ort des Skripts aus, nicht von einem festen Laufwerk: Es liegt im
# Projekt und soll dort auch auf einer anderen Platte laufen.
BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources", "assets", "factorynetwork",
                    "textures", "block") + os.sep

# Gemessen an controller_extension: Der Rahmen endet in einer dunklen Rille
# bei 9/10, das schlichte Feld läuft von 11 bis 52, und bei 53 sitzt die helle
# Kante der Fase. Nachgefüllt wird nur dazwischen — sonst verschwindet die
# Fase, und das Feld sieht aus wie ein Loch.
PANEL_FROM = 11
PANEL_TO = 53

# Ebenfalls gemessen: der Grund und seine Streuung.
GROUND = (25, 30, 27)

# Das reine Korn, gemessen als Abstand zur weichgezeichneten Fassung:
# controller_extension 11,0 — connector_side 5,3 — machine_top 8,5.
GRAIN = 9.0

# Die beiden Grüntöne, die die Familie führt — und sonst keine.
GREEN = (58, 104, 68)
GREEN_LIT = (126, 157, 133)
GREEN_DIM = (34, 62, 41)
HOLLOW = (6, 8, 7)


def mottle(width, height, spread, seed, coarseness=2):
    """Die Körnung der Familie: fein, mit einem Hauch Wolke darunter.

    <b>Nachgemessen statt geschätzt.</b> Zieht man von jeder Textur ihre
    weichgezeichnete Fassung ab, bleibt das reine Korn — bei
    `controller_extension` mit einer Streuung von 11, bei `connector_side`
    von 5. Es ist also <i>hochfrequent</i>: Pixel für Pixel, keine Flecken.

    Ein erster Versuch hat grob gewürfelt und weich vergrößert. Das ergab
    Tarnflecken — dieselbe Streuung, die falsche Frequenz, und der Block sah
    aus, als wäre er bemalt. Darunter liegt jetzt nur noch eine sehr schwache
    Wolke, damit die Fläche nicht wie Filmkorn flimmert.
    """
    noise = random.Random(seed)
    cloud = Image.new("L", (width // 4 + 2, height // 4 + 2))
    cloud.putdata([max(0, min(255, int(128 + noise.gauss(0, spread * 0.35))))
                   for _ in range(cloud.width * cloud.height)])
    soft = cloud.resize((width, height), Image.BILINEAR).load()

    fine = random.Random(seed + 1)
    values = {}
    for y in range(height):
        for x in range(width):
            values[(x, y)] = 128 + (soft[x, y] - 128) + fine.gauss(0, spread)
    return values


def draw_gateway():
    source = Image.open(BASE + "controller_extension.png").convert("RGBA")
    out = source.copy()
    pixels = out.load()
    grain = mottle(64, 64, GRAIN, 20260827)
    fine = random.Random(99)

    def shaded(tone, x, y, strength=1.0):
        off = (grain[x, y] - 128) * strength
        return tuple(max(0, min(255, int(c + off))) for c in tone) + (255,)

    # ---- Der Grund ------------------------------------------------------
    for y in range(PANEL_FROM, PANEL_TO):
        for x in range(PANEL_FROM, PANEL_TO):
            pixels[x, y] = shaded(GROUND, x, y)

    # ---- Der Torbogen ---------------------------------------------------
    # Etwas kleiner als das Feld: Die Familie lässt ihren Motiven Luft.
    centre = 32
    spring = 30
    foot = 44
    inner = 8
    band = 4

    def reach(x, y):
        dx = abs(x - centre)
        return dx if y >= spring else math.hypot(dx, spring - y)

    for y in range(PANEL_FROM, PANEL_TO):
        for x in range(PANEL_FROM, PANEL_TO):
            if y > foot:
                continue
            distance = reach(x, y)
            if distance < inner:
                # Die Öffnung: eine Aussparung, kein Aufkleber.
                pixels[x, y] = shaded(HOLLOW, x, y, 0.25)
            elif distance < inner + band:
                # Die Laibung. Oben und links nimmt sie das Licht.
                if distance > inner + band - 1.2:
                    tone = GREEN_DIM
                elif (x - centre) < -2 or y < spring - 4:
                    tone = GREEN_LIT
                else:
                    tone = GREEN
                pixels[x, y] = shaded(tone, x, y, 0.35)

    # ---- Die Schwelle ---------------------------------------------------
    for y in range(foot + 1, foot + 3):
        for x in range(centre - inner - band, centre + inner + band + 1):
            if PANEL_FROM <= x < PANEL_TO and y < PANEL_TO:
                tone = GREEN if y == foot + 1 else GREEN_DIM
                pixels[x, y] = shaded(tone, x, y, 0.35)

    # ---- Ein Hauch Streulicht unter dem Bogen ---------------------------
    for y in range(foot + 3, min(foot + 6, PANEL_TO)):
        for x in range(centre - inner, centre + inner + 1):
            if PANEL_FROM <= x < PANEL_TO:
                fade = (foot + 6 - y) / 4.0
                base = pixels[x, y]
                pixels[x, y] = (
                    int(base[0] + (GREEN_DIM[0] - base[0]) * fade * 0.5),
                    int(base[1] + (GREEN_DIM[1] - base[1]) * fade * 0.5),
                    int(base[2] + (GREEN_DIM[2] - base[2]) * fade * 0.5),
                    255)
    _ = fine
    out.save(BASE + "gateway.png")
    print("gateway.png")


def draw_status_light():
    """Ein gleichmäßiger heller Streifen.

    <b>Kein Motiv.</b> Die Textur sitzt auf den vier Schmalseiten eines Rings,
    vierzehn mal zwei Blockpixel groß, und Minecraft schneidet die UV-Fläche
    aus der Lage des Kastens. Ein Lämpchen oder ein Rand würde dabei
    willkürlich zerteilt; gleichmäßig ist die einzige Form, die an jeder
    Schnittstelle gleich aussieht — auch an der Ober- und der Seitenfläche,
    die verschieden abgetastet werden.

    Hell, weil sie eingefärbt wird: Der Renderer multipliziert die Farbe des
    Netzzustands darauf, und was dunkel ist, bleibt dunkel.
    """
    size = 16
    out = Image.new("RGBA", (size, size))
    pixels = out.load()
    grain = mottle(size, size, 3.0, 4711)

    for y in range(size):
        for x in range(size):
            value = 234 + (grain[x, y] - 128) * 0.5
            value = max(0, min(255, int(value)))
            # Eine Spur kühler als reines Weiß: Die Familie führt keinen
            # einzigen neutralen Ton.
            pixels[x, y] = (max(0, value - 5), value, max(0, value - 2), 255)

    out.save(BASE + "status_light.png")
    print("status_light.png")


draw_gateway()
draw_status_light()
