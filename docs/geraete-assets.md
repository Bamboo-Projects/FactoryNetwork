# Gerätegrafik: Terminal und Laufwerk

Die vorhandene dunkle Metall-/Grün-Familie bleibt erhalten. Das Terminal hat
eine eingerückte Codegrafik mit Syntaxfarben, eine Tastatur auf der Ablage und
eine eigene Deckplatte mit Lüftung. Die Bildschirmgrafik ist statisch; sie
zeigt keinen laufenden Programm- oder Netzwerkzustand an.

Die Blockform und damit Kollision, Auswahl und Ausrichtung bleiben gleich.
Die neuen Texturen werden über das bestehende Blockmodell auch als Item
verwendet. Es gibt keine neuen Registry-IDs oder Rezepte.

Das Laufwerk zeigt Chemikalienzellen jetzt mit einem eigenen gelbgrünen
Einschub. Vorher wurden diese vom Renderer als leer eingeordnet. Die acht
Kacheln in `textures/misc/drive_bays.png` stehen in dieser Reihenfolge:
leer, Item 1K, 4K, 16K, 64K, Fluid, Energie, Chemikalien. Der Renderer zeigt
weiterhin den Zelltyp, keinen Füllstand. Die Grundtextur hat dunkle leere
Schächte statt zehn dauerhaft grüner Lämpchen.

## Reproduktion und Prüfung

Pillow wird benötigt. Aus dem Projektverzeichnis:

```text
python tools/device_assets.py --preview
python tools/device_assets.py --check
```

Der erste Aufruf erzeugt nur die betroffenen Geräteassets und die Vorschau
`docs/previews/devices.png`. Der zweite prüft die gespeicherten PNGs gegen
ihre Generatoren, die Terminalgeometrie sowie Texturverweise und UV-Grenzen
der drei gezeigten Modelle. `tools/assets.py` verwendet jetzt einen Pfad
relativ zu seiner eigenen Datei statt eines festen Laufwerksverzeichnisses.

Die Vorschau stammt aus echten Modellkästen und Texturen. Die Projektion des
Werkzeugs wurde korrigiert: Frontgrafiken sind nicht länger gespiegelt.
Weltbeleuchtung und laufende Block-Entity-Overlays muss ein Clienttest prüfen;
die untere Reihe zeigt den tatsächlichen Overlay-Atlas separat. Der Controller
ist unverändert als Vergleich für die bestehende Gerätefamilie abgebildet.

Im Spiel zu prüfen: Terminal in allen vier Ausrichtungen, allein und neben
vollen Blöcken; Laufwerk mit Item-, Fluid-, Energie- und Chemikalienzellen,
anschließend Zelle entnehmen und leeren Schacht prüfen.
