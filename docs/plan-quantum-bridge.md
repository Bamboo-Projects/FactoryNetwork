# Die Quantum-Bridge — Aufgaben 1 bis 4 erledigt am 29.08.

**Auftrag:** „wie bringen wir channel an andere punkte in den welten? AE2 hat
dafür die quantum bridges" (28.08.), gefolgt von „okay Quantum bridge dann
bauen".

**Ziel:** Zwei Blöcke, die ein Netz über jede Entfernung verbinden, ohne dass
dazwischen ein Kabel liegt.

**Was wir schon haben und was nicht.** Der Sendemast bringt das *Terminal* in
die Ferne — ein Spieler kann von überall aufs Lager sehen. Das *Netz* selbst
endet weiter dort, wo das Kabel endet. Ein Bergwerk fünftausend Blöcke weiter
braucht seinen eigenen Controller, und die beiden Netze wissen nichts
voneinander. Das Gateway ist trotz seines Namens keine Brücke, sondern ein
Namensgeber für Anlagen.

---

## Die Grenze, die vorher geklärt sein muss

**`FactoryGraph.build(Level level, BlockPos controller)` kennt genau eine
Welt.** Alle seine Listen sind `BlockPos` ohne Dimension — Kabel, Laufwerke,
Anschlüsse, Router. Eine Brücke zwischen zwei Dimensionen hieße, dieses
Fundament auf `GlobalPos` umzubauen: jede Liste, jeder Suchlauf, jede
Namenszuordnung, dazu die Frage, welches Level beim Aufbau geladen sein muss.

**Deshalb v1 innerhalb einer Dimension.** Das ist keine Verlegenheitslösung:
Über fünftausend Blöcke legt niemand ein Kabel, und die Grenze aus
`FnConfig.networkNodes()` erreicht man lange vorher. Eine Brücke, die zwei
weit entfernte Anlagen derselben Welt verbindet, ist der Fall, den man beim
Spielen zuerst trifft.

**Cross-Dimension bleibt offen** und steht am Ende dieses Dokuments mit dem,
was es kostet. Es ist eine eigene Entscheidung, keine Fußnote — der
Sendemast hat seine Dimensionsgrenze über eine Karte gelöst, das Netz kann
das nicht nachmachen.

---

## Die Bauform

**Ein Paar aus zwei gleichen Blöcken.** Nicht Sender und Empfänger: Wer zwei
Bauteile unterscheiden muss, baut das falsche zuerst. Beide heißen gleich,
beide tun dasselbe, und was sie verbindet, ist eine Kopplung.

**Gekoppelt wird über einen Gegenstand**, der in beide hineingeht — bei AE2
ist das die Quantum-Entangled Singularity, und der Gedanke trägt: Das Paar
entsteht beim Bauen, nicht beim Anklicken. Zwei Hälften desselben Gegenstands
gehören zusammen, egal wohin man sie trägt.

Bei uns: **eine Verschränkung**, hergestellt aus zwei Netzkernen und einem
Kristall. Das Rezept liefert **zwei** Gegenstände mit derselben Kennnummer;
jede Hälfte in eine Brücke, und die Brücken kennen einander.

**Die Kennnummer ist eine Datenkomponente** — dieselbe Technik wie beim
Ferngerät, das sich seinen Mast merkt. Zwei Hälften mit derselben Nummer
finden sich; alles andere ist ein Gegenstand ohne Partner.

## Was durch die Brücke geht

**Kanäle wie ein dichtes Kabel: vierundsechzig.** Sie ist eine Leitung und
kein Vermehrer — dieselbe Regel, die schon für Router und Gateway gilt
(`capacityAt`). Wer mehr braucht, baut ein zweites Paar.

**Strom auf beiden Seiten.** Eine Brücke, die nichts kostet, ersetzt jedes
Kabel. Der Grundverbrauch liegt beim Sendemast (`Power.MAST_BASE`), also
merklich über einem Kabel und unter einer Maschine.

**Ist die Gegenseite nicht geladen, ist die Verbindung tot.** Dieselbe
Regel wie beim Sendemast, und aus demselben Grund: `getBlockEntity` lädt
sonst nach, und die Frage fällt in jedem Tick. Wer eine Brücke dauerhaft
offen halten will, hält das Stück Welt mit den Mitteln offen, die sein Pack
dafür hat — ein eigener Chunkloader ist ein eigenes Feature und eine eigene
Entscheidung.

---

## Die Aufgaben

- [x] **1. Die Verschränkung.** Ein Gegenstand mit Kennnummer, im Rezept
      immer zu zweit. Reiner Prüflauf: Zwei aus demselben Bau gehören
      zusammen, zwei aus verschiedenen nicht.
- [x] **2. Der Block und sein Platz.** Ein Steckplatz für die Hälfte, ein
      Zustand „gekoppelt", die Verbindung als `GlobalPos` — die Arbeit vom
      Sendemast trägt hier weiter.
- [x] **3. Der Graph springt.** `visitBridge` setzt den Suchlauf an der
      Gegenseite fort, mit der Kanalgrenze eines dichten Kabels. **Die
      Stelle, an der es scharf wird:** Zwei Controller an beiden Enden dürfen
      nicht zu einem Netz verschmelzen, das sich selbst zählt.
- [x] **4. Strom und Kanäle.** Wie oben, in `Power` und `capacityAt`.
- [ ] **5. Was man im Spiel sieht.** Der Block zeigt, ob er gekoppelt ist und
      ob die Gegenseite antwortet — sonst sucht man den Fehler im Kabel.

## Cross-Dimension: was es kosten würde

Für den Fall, dass es doch gewünscht ist — gemessen, nicht geschätzt:

- `FactoryGraph` führt heute neun Listen aus `BlockPos`. Alle müssten
  `GlobalPos` werden, samt `contains`, `connectorNames` und den Suchläufen.
- `ControllerRegistry.owning` läuft über die Controller *eines* Levels.
- `GatewayRegions` und die Anlagennamen rechnen mit Nachbarschaft — über eine
  Dimensionsgrenze gibt es keine.
- Und die offene Frage: Muss die Gegenseite geladen sein, damit ein Netz
  überhaupt läuft? Beim Sendemast heißt die Antwort ja, und das ist dort
  verkraftbar — ein totes Netz ist etwas anderes als ein geschlossenes
  Fenster.

**Das ist ein eigener Abend**, und er gehört nach der Brücke v1, nicht davor.
