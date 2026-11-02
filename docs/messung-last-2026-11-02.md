# Lastmessung auf dem dedizierten Server

Stand: 2026-11-02. Gemessen auf 26.1.2 und 1.21.1, jeweils dedizierter
Entwicklungsserver mit einem unsichtbaren Client.

## So wird gemessen

```bash
./gradlew runServer -Pacceptance
./gradlew runClient -Pacceptance -Phidden -Pjoin=127.0.0.1:25576
```

Dann über RCON (127.0.0.1:25577, Passwort `fn-dev`) oder die Serverkonsole:

```
fnbench run 5000 64 4000
```

Der Lauf baut drei Netze in einer Superflat-Welt und schreibt pro Abschnitt
eine Zeile `FN_BENCH_STEP` und am Ende `FN_BENCH {json}` ins Serverlog. Er
lädt vorher alle Chunks, die er braucht, und stellt den Spieler neben das
spätere Terminal: Chunkladen in einem Messfenster würde sonst als Kosten
von Factory Network zählen.

**Zwei Zahlen pro Fenster** (200 Ticks, also 10 Sekunden):

- `avgMs` usw.: der ganze Servertick. Schwankt um 1 bis 2 ms, auch ohne
  unser Zutun (GC, Autosave, ein Client auf derselben Maschine).
- `fnAvgMs` usw.: nur die Zeit in den Ticks der Controller (`TickCost`,
  gemessen um jeden Controller-Tick). Das ist der Anteil von Factory
  Network und die belastbare Zahl.

Ein Tick hat 50 ms.

## Ergebnisse

5.000 Item-Sorten in 8 Laufwerken mit 80 Zellen (64k), 64 Worker, 4.000 Kabel.

| Szenario | 26.1.2 FN Ø | FN p95 | FN max | 1.21.1 FN Ø | FN p95 | FN max |
|---|--:|--:|--:|--:|--:|--:|
| Lager im Leerlauf | 0,14 ms | 0,67 | 1,6 | 0,16 ms | 0,73 | 1,7 |
| Lager, Terminal offen, Bestand ändert sich | 0,34 ms | 2,1 | 3,3 | 0,37 ms | 2,4 | 2,8 |
| + 64 Worker laufen | 0,46 ms | 3,2 | 6,8 | 0,44 ms | 2,6 | 5,0 |
| + Netz mit 4.000 Kabeln | 0,51 ms | 3,1 | 7,9 | 0,46 ms | 2,3 | 6,7 |

Einzeloperationen bei 5.000 Sorten (26.1.2 / 1.21.1):

| Operation | 26.1.2 | 1.21.1 |
|---|--:|--:|
| 5.000 Sorten einlagern | 103 ms | 143 ms |
| Einlagern, eine Sorte | 15 µs | 16 µs |
| Auslagern, eine Sorte | 10 µs | 8 µs |
| Bestandsliste erstellen | 0,3 ms | 0,3 ms |
| Bestand an ein Terminal schicken | 2,8 ms | 4,5 ms |
| Netz mit 4.000 Kabeln neu aufbauen | 14 ms | 16 ms |

Mehrspieler: Das Terminal auf dem Client zeigt den Bestand, ein Shift-Klick
legt 64 Stück ins Inventar. Auf beiden Linien geprüft.

## Befunde

1. **Der Durchschnitt ist gut, die Spitzen nicht.** Im Mittel kostet alles
   zusammen unter 0,5 ms pro Tick, also 1 % des Budgets. Die Spitzen von
   5 bis 8 ms kommen sehr wahrscheinlich vom periodischen Neuaufbau:
   `serverTick` baut das ganze Netz alle 100 Ticks neu auf
   (`REBUILD_INTERVAL = 100`). Belegt ist, dass der langsamste Controller
   jeweils der mit dem größten Netz ist; den Zeitpunkt der Spitzen habe ich
   nicht gegen den Neuaufbau protokolliert. Der Neuaufbau kostet linear mit der Netzgröße, bei
   4.000 Kabeln 14 ms am Stück. Bei 20.000 Kabeln wären das um die 70 ms,
   also ein spürbarer Ruckler alle fünf Sekunden.
2. **Ein offenes Terminal schickt bei jeder Änderung den ganzen Bestand**,
   höchstens alle 10 Ticks (`pushStorageIfDue`, `replace = true`). Das sind
   2,8 ms pro Spieler und Sendung bei 4.096 Einträgen. Zehn Spieler mit
   offenem Terminal an einem aktiven Netz kosten so rund 3 ms pro Tick, nur
   fürs Senden. AE2 schickt nur die Änderungen.
3. **Das Terminal zeigt höchstens 4.096 Sorten** (`StorageSnapshotPacket.MAX_ENTRIES`).
   Bei 5.000 Sorten fehlen auf dem Client 904. Kein Leistungs-, sondern ein
   Funktionsfehler: große Netze sind unvollständig sichtbar.
4. **Worker-Durchsatz ist gedeckelt, unabhängig von der Zahl der Worker:**
   16 und 64 Worker bewegen beide 6.400 Items in 10 Sekunden. Vermutlich die
   Bandbreite des Controllers aus `plan-bandbreite.md`, also gewollt. Nicht
   weiter untersucht.
5. **Einlagern mit 15 µs pro Vorgang** ist für Worker in Ordnung, aber nicht
   billig: 1.000 Einlagerungen pro Tick wären 15 ms.

## Vorschläge, nach Wirkung

1. Den periodischen Neuaufbau des Netzes streichen oder stark seltener
   machen; Umbau nur bei Änderung (`refreshAround` gibt es schon).
2. Terminal-Sync auf Änderungen umstellen: einmal den vollen Bestand, danach
   nur die geänderten Einträge.
3. Mit 2 fällt auch die Grenze von 4.096 Sorten: den vollen Bestand in
   mehreren Paketen schicken.
