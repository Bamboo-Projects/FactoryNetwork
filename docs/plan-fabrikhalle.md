# Die Fabrikhalle — Entwurf

Stand: 2026-11-02. Status: **Entwurf, nichts gebaut.**

Gewünscht am 02.11.: eine Fabrik in einer eigenen Dimension, wie bei Compact
Machines, als Ergänzung zu den Multiblöcken in der Welt. Dazu:

> „das man den block in der welt auch abbauen kann und wieder setzen kann und
> die alles erhalten bleibt und cool wäre noch wenn man die auch klonen kann
> und dann die Blöcke die drin sind auflistet und dann platziert wieder und
> konfiguriert. das geht aber nur mit mods die wir unterstützen"

Compact Machines selbst gibt es für 26.1 nicht (Stand 02.11.); eine
Anbindung daran fällt damit als billigere Alternative weg.

---

## 1. Was der Spieler sieht

1. **Der Hallenblock.** Ein Block in der Welt. Rechtsklick führt in die
   Halle, ein Block in der Halle führt zurück.
2. **Die Halle.** Ein leerer Raum in einer eigenen Dimension, mit Wänden aus
   unzerstörbarem Material. Die Größe hängt an der Stufe des Blocks (Vorschlag:
   16, 32, 48 Blöcke Kantenlänge).
3. **Abbauen und Setzen.** Der abgebaute Block nimmt seine Halle mit: Das Item
   trägt die Nummer der Halle. Wieder gesetzt, an anderer Stelle oder in
   einer anderen Dimension, ist alles noch da.
4. **Die Wand als Anschluss.** Unser Netz geht durch die Wand: Ein Kabel an
   der Außenseite des Hallenblocks und eines an der Innenwand gehören zum
   selben Netz. So reicht ein Netz draußen in die Halle hinein, oder die
   Halle hat ihr eigenes.
5. **Klonen.** Aus einer Halle wird ein **Bauplan**: ein Item, das auflistet,
   welche Blöcke drinstehen, wie viele, und wie sie eingestellt sind. Ein
   leerer Hallenblock plus Bauplan plus die Blöcke aus dem Netzlager ergibt
   eine zweite, gleiche Halle.

---

## 2. Die Bausteine

### 2.1 Die Dimension

Eine Dimension, die als Datenpaket mitkommt (`dimension_type` und
`dimension` unter `data/factorynetwork`), leer, ohne Wetter und ohne Mobs.
Die Hallen liegen in einem Raster darin, eine je 1.024 Blöcke, damit sich
ihre Chunks nie berühren.

### 2.2 Die Hallennummer

Eine fortlaufende Nummer je Welt, gespeichert in einem `SavedData` der
Halle-Dimension: Nummer → Rasterplatz, Stufe, Besitzer. Der Hallenblock und
sein Item tragen nur die Nummer (Data Component). Damit ist Abbauen und
Setzen gelöst: Die Halle bleibt, wo sie ist; der Block ist nur die Tür.

**Offen:** Was passiert mit einer Halle, deren Block zerstört wurde
(Explosion, Lava, `/setblock`)? Vorschlag: Sie bleibt, und ein Befehl für
Admins listet verwaiste Hallen.

### 2.3 Laden

Eine Halle läuft, solange ihr Block in der Welt geladen ist. NeoForge bietet
dafür Ladescheine (`TicketController`): Der Hallenblock nimmt beim Laden
einen Schein für die Chunks seiner Halle und gibt ihn beim Entladen zurück.

**Das ist die Stelle mit dem größten Risiko.** Eine Halle, die nicht lädt,
steht still, und ein Spieler merkt es erst an fehlenden Gegenständen.

### 2.4 Netz durch die Wand

Unser Netz endet heute an Dimensionsgrenzen; die Quantum-Bridge hebt das für
zwei Bridges auf (`plan-quantum-bridge.md`). Der Hallenblock wird genau so
eine Bridge, nur mit festem Gegenstück: der Innenwand an derselben Stelle.
Damit gibt es keinen neuen Netzmechanismus, sondern eine Bridge ohne Karte.

### 2.5 Der Bauplan (Klonen)

Ein Lauf über alle Blöcke der Halle. Je Block:

- **Was:** Block und Zustand (Ausrichtung usw.).
- **Wo:** Position relativ zur Halle.
- **Wie eingestellt:** nur für Blöcke, deren Einstellung wir kennen.

Beim Platzieren:

1. Der Bauplan nennt die Liste der Blöcke. Fehlt etwas im Netzlager, sagt er
   was und wie viel.
2. Sind alle da, werden sie aus dem Lager entnommen und gesetzt.
3. Dann die Einstellungen.

**„Nur mit Mods, die wir unterstützen"** heißt im Code: eine Liste von
Einstellungs-Kopierern je Blockart. Für den Anfang unsere eigenen Blöcke:

| Block | Was kopiert wird |
|---|---|
| Controller | Programm (alle Dateien) |
| Connector | Name, Seite |
| Router | Bahnen |
| Laufwerk | nichts (Zellen sind Inhalt, nicht Einstellung) |
| Display | Inhalt der Anzeige |
| Lager-Anschluss | nichts |

Fremde Blöcke werden gesetzt, aber nicht eingestellt; der Bauplan sagt das
pro Block. Später kann je unterstützter Mod ein Kopierer dazukommen.

**Was nie kopiert wird:** Inventare und Speicherinhalte. Ein Bauplan, der
Kisten samt Inhalt klont, wäre ein Vervielfältiger.

---

## 3. Aufwand, grob

| Schritt | Aufwand |
|---|--:|
| Dimension, Hallenblock, Rein und Raus, Hallennummer | 2 Tage |
| Laden über Ladescheine, Abbauen und Setzen | 1–2 Tage |
| Netz durch die Wand (Bridge mit festem Gegenstück) | 1 Tag |
| Bauplan: erfassen, Liste, Platzieren aus dem Lager | 2 Tage |
| Einstellungen für unsere Blöcke | 1 Tag |
| Gametests, Mehrspieler, Rechte | 2 Tage |

Zusammen etwa 1,5 bis 2 Wochen.

---

## 4. Entscheidungen, die vorher fallen müssen

1. **Stufen und Größen:** 16 / 32 / 48 Blöcke, oder anders?
2. **Wer darf hinein:** nur der Besitzer, oder jeder (wie eine Tür)?
3. **Klonen kostet:** nur die Blöcke, oder zusätzlich Energie oder ein
   Bauplan-Item, das verbraucht wird?
4. **Verwaiste Hallen:** behalten, wie vorgeschlagen, oder mit dem Block
   löschen?
