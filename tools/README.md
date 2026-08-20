# Werkzeuge

Skripte, die erzeugte Dateien im Projekt herstellen. Sie laufen selten — nur
wenn sich Texturen, Modelle oder die Testvorlage ändern sollen — und liegen
hier, damit sich das Ergebnis nachvollziehen und wiederherstellen lässt.

```
python tools/textures.py     Blocktexturen und Gegenstandstextur
python tools/assets.py       Blockstates, Modelle, Loot-Tables, Rezepte
python tools/structure.py    leere Strukturvorlage für die GameTests
python tools/texturuebersicht.py   Übersichtsseite aller Texturen nach build/
```

Gebraucht wird Pillow für `textures.py`; die anderen beiden kommen mit der
Standardbibliothek aus.

`structure.py` schreibt eine `.nbt` von Hand — gzip-komprimiertes NBT mit
`size`, `palette`, `blocks`, `entities` und `DataVersion` (3955 für 1.21.1).
Minecraft dafür zu starten und die Vorlage im Spiel zu speichern ist unnötig.
