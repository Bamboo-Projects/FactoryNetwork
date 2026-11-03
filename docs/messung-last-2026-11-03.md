# Lastmessung, zweiter Tag

Stand: 2026-11-03. Fortsetzung von `messung-last-2026-11-02.md`, nur 26.1.2,
dedizierter Entwicklungsserver **ohne Client**. Offen waren drei Befunde: eine
einzelne Worker-Spitze von 24 ms, 1,4 ms pro Worker-Schritt beim Filtern auf
ein Item mit tausenden Varianten im ME-Netz und das erste Senden beim Öffnen
des Terminals, das mit der Lagergröße wächst.

## Was an der Messung neu ist

```bash
./gradlew runServer -Pacceptance        # eigener Worktree: server-port 25596, RCON 25597
fnbench run 5000 64 4000                # über RCON
fnbench run 50000 16 100
```

- **Phasen pro Controller-Tick.** `TickCost` merkt sich jetzt pro Phase des
  Controller-Ticks (Neuaufbau, Speicherbusse, Strom, Programmdatei, Worker,
  zurückgehaltene Items, Abläufe, Crafting, Ereignisse, Senden, `setChanged`)
  die längste Zeit seit dem letzten Reset, dazu den langsamsten einzelnen
  Worker-Schritt mit Namen. Zwei Uhrzeit-Abfragen pro Phase, darf an bleiben.
  Jede `FN_BENCH_STEP`-Zeile trägt `slowestPhaseMs` und `slowestWorker`.
- **Terminal ohne Client.** Ist kein Spieler online, öffnet der Lauf das
  Terminal für einen `FakePlayer`, dessen Verbindung jedes Paket verwirft.
  Gemessen wird also, was das Senden den Server-Thread kostet; das Kodieren
  läuft bei einem echten Spieler ohnehin im Netzwerk-Thread. **Die
  Terminalzahlen von gestern kamen von einem echten Client, die von heute von
  der Attrappe**: Sie sind untereinander vergleichbar, mit gestern nur grob.
- **Neues Fenster `storageWorkersTicking`:** acht Worker vom großen Lager in
  Kisten, vier mit `filter item:paper` (alle 5.000 Sorten sind benanntes
  Papier), vier mit `filter item:iron_ingot` (eine einzige, nackte Sorte hinter
  allem Papier). Die Kisten werden jeden Tick geleert, damit die Worker laufen.
- **Neue Einzelzeiten:** `count(Item)`, die erste passende Sorte für einen
  Worker, `markChanged` (was jeder gesetzte Netzblock kostet), `contains` auf
  dem Graphen, Indexaufbau nach einem Netz-Neuaufbau.
- Vor jedem Lauf wird `build/server/world` gelöscht, sonst misst `me100` im
  zweiten Lauf schon 4.001 Sorten.

Die 50.000er-Zahlen schwanken zwischen zwei Läufen mit gleichem Code um bis zu
50 % (Indexaufbau einmal 9,9, einmal 16,8 ms). Sie stehen unten als
Größenordnung, nicht auf die Nachkommastelle.

## Befund 1: Die Worker-Spitze ist der Garbage Collector

Ein Lauf unter JFR (`-Pjfr`) zeigt GC-Pausen von 25 bis 50 ms
(`jdk.GCPhasePause`). `TickCost` misst Wanduhrzeit: Fällt eine Pause in einen
Controller-Tick, steht sie dort als Spitze, in irgendeiner Phase. So ist auch
die einmalige Spitze von 48 ms in der Phase „Abläufe“ bei 50.000 Sorten zu
lesen: Die Phase ist leer, kein Programm hat Abläufe. Gestern traf die Pause
zufällig einen Worker-Tick.

Behoben wird das nicht an der Spitze, sondern an dem, was den GC treibt. Laut
den Allokationsproben (`jdk.ObjectAllocationSample`, gewichtete Schätzung, kein
exakter Zähler) gingen im ganzen 5.000er-Lauf rund 4,4 GB auf Factory Network.
Davon:

| Stelle | MB vorher |
|---|--:|
| `Ae2Link.find` → `ItemKey.of` (ein Schlüssel pro ME-Variante) | ≈ 3.000 |
| `FactoryGraph.contains` → `atPos` (Streams über alle Connectoren) | ≈ 700 |
| Rest, davon ≈ 350 MB der Aufbau des Laufs selbst | ≈ 700 |

Nach den Änderungen unten: **≈ 1,2 GB** im gleichen Lauf, davon wieder rund
350 MB Aufbau. GC-Pausen im Lauf: 113 vorher, 68 nachher (der spätere Lauf
misst ein paar Einzelzeiten mehr, ist also nur ungefähr derselbe). Pausen über
20 ms gibt es weiter, zwölf im Lauf; woher deren Müll stammt, ist nicht
aufgeschlüsselt.

## Änderungen, jede mit vorher/nachher

Alle Zahlen aus `fnbench`, 5.000 Sorten, wenn nicht anders angegeben.

### 1. ME-Ansicht baut Schlüssel erst, wenn ein Slot gelesen wird

`ItemStockView` hat beim Einengen auf einen Filter jede gefundene Sorte sofort
in einen `ItemKey` verwandelt: Stapel kopieren, Komponenten hashen. Bei 4.000
Varianten von Papier im ME-Netz also 4.000 Schlüssel für einen Schritt, der
dann einen Slot liest. Jetzt hält die Ansicht AE2s eigene Schlüssel
(`ItemStock.findRaw`) und baut den `ItemKey` eines Slots beim ersten Blick
darauf. Slotzahl und Reihenfolge bleiben gleich; RS und Fabrikhalle reichen
ihre Schlüssel wie bisher durch.

| Ein Worker-Schritt (einengen, nehmen, zurücklegen) | vorher | nachher |
|---|--:|--:|
| 4.000 Papiervarianten im ME | 2.481 µs | 185 µs |
| 100 Varianten | 71 µs | 31 µs |
| eine Sorte | 2–5 µs | 2–5 µs |

Die restlichen 185 µs sind das Aufzählen der 4.000 Einträge über AE2s
`findFuzzy`. Das bleibt: Die Transferschleife fragt `getSlots()`, und das muss
stimmen.

### 2. Zählen nach Item und die erste Sorte eines Workers ohne Gang übers Lager

`NetworkStorage.count(Item)` summierte den ganzen Index, für jede Bedingung,
jedes `maintain`, jede Anzeige. Ein Worker vom Lager in ein Gerät kopierte
bei jedem Schritt den ganzen Bestand (`contents()`), um die erste passende
Sorte zu finden. Jetzt gruppiert das Lager seinen Index nach Item: Summe pro
Item, und pro Item die Sorten in Index-Reihenfolge. `firstPresent(filter)`
liefert dieselbe Sorte wie vorher der Gang über die Kopie.

| | vorher | nachher |
|---|--:|--:|
| `count(paper)`, 5.000 Varianten | 106 µs | 0,3 µs |
| `count(iron)`, eine Sorte | 29 µs | 0,3 µs |
| erste Eisensorte für einen Worker | 456–489 µs (Kopie) | 0,7 µs |
| dasselbe bei 50.000 Sorten | 5,6–9,9 ms | 0,8 µs |
| acht Lager→Kiste-Worker, FN Ø / p95 / max pro Tick | 0,36 / 4,1 / 5,9 ms | 0,17 / 0,98 / 1,5 ms |

**Nachgezogen, weil die erste Fassung woanders teurer war:** Summen und Sorten
liefen zunächst bei jedem Indexaufbau mit. Der folgt jedem Netzumbau, und bei
50.000 Sorten wurde er dadurch fast doppelt so teuer (13,4 → 25,6 ms). Jetzt
entstehen die Summen erst bei der ersten Frage nach einem Item, in einem Gang
über den Index, und die Sorten eines Items erst, wenn ein Worker darauf
filtert. Danach werden beide mitgeführt; ein Indexaufbau verwirft sie.

| 50.000 Sorten | ohne Gruppierung | eifrig | bei Bedarf |
|---|--:|--:|--:|
| Neuaufbau mit Index | 13,4 ms | 25,6 ms | 9–12 ms |
| erstes `count` danach | — | — | 1,4 ms |
| erste Eisensorte danach | — | — | 2,4 ms |

Bei 5.000 Sorten steht für das erste `count` nach dem Neuaufbau 1,2–1,7 ms im
Lauf; das sind zehn Wiederholungen, also kalter Code. Ein Spieltest prüft
Menge und erste Sorte gegen einen Gang über den ganzen Bestand, nach
Einlagern, nach einer Sorte, die ganz geht und wiederkommt, und nach einem
Neuaufbau.

### 3. Gehört ein Block zum Netz: eine Menge statt Listen

`FactoryGraph.contains` lief über alle Connector-Listen und mehrere
Positionslisten, mit Streams. Gefragt wird es für **jeden** Netzblock, der
irgendwo in der Welt gesetzt oder abgebaut wird, sieben Stellen pro Controller
(`ControllerRegistry.markChanged`), und einmal pro Sekunde pro Anzeige
(`owning`). Der Graph ändert sich nach dem Aufbau nicht; die Stellen werden
beim ersten Aufruf in eine Menge gesammelt. Es zählen dieselben Stellen wie
vorher, auch die obere Hälfte eines Racks.

| | vorher | nachher |
|---|--:|--:|
| `markChanged` für eine Stelle neben keinem Netz (drei Netze) | 146 µs | 0,7–0,9 µs |
| `contains` im Netz mit 128 Connectoren | 19,3 µs | 0,08 µs |
| Neuaufbau mit 4.000 Kabeln | 12–14 ms | 13–18 ms (Rauschen, die Menge entsteht erst bei Bedarf) |

Ein Bauwerkzeug, das tausend Blöcke pro Tick setzt, kostete neben drei Netzen
vorher 146 ms pro Tick.

## Die Fenster im Ganzen

5.000 Sorten, 64 Worker, 4.000 Kabel, FN-Anteil pro Tick (Ø / p95 / max).
„Vorher“ ist der Stand von heute früh mit der neuen Messung, „nachher“ der Stand
nach allen Änderungen. Die Fenster sind kumulativ, wie gestern.

| Fenster | vorher | nachher |
|---|--:|--:|
| Lager im Leerlauf | 0,13 / 0,20 / 0,37 ms | 0,11 / 0,17 / 0,36 ms |
| Terminal offen, Bestand ändert sich | 0,13 / 0,27 / 0,92 ms | 0,11 / 0,25 / 0,41 ms |
| + acht Worker Lager→Kiste | 0,36 / 4,1 / 5,9 ms | 0,17 / 0,98 / 1,5 ms |
| + 64 Worker Kiste→Kiste | 0,37 / 2,4 / 4,3 ms | 0,29 / 2,1 / 3,3 ms |
| + 4.000 Kabel | 0,34 / 1,6 / 5,0 ms | 0,25 / 1,5 / 3,4 ms |

Der langsamste einzelne Worker-Schritt liegt jetzt bei 0,3–0,6 ms, vorher bei
1,2–1,6 ms.

## Gemessen, nicht geändert

- **Erstes Senden beim Öffnen des Terminals.** Die 35 ms von gestern bei
  50.000 Sorten enthielten den Indexaufbau, den der Lauf selbst auslöst: Er
  setzt das Terminal und baut dabei das Netz neu. Ohne das: **rund 10 ms bei
  50.000 Sorten, 1,7 ms bei 5.000**, einmal pro Öffnen. Der größte Teil ist
  die Kopie des Bestands (`contents()`, 3,4–3,9 ms als reine Map-Kopie), die
  der Abgleich danach als Ausgangsstand braucht. Ohne sie müsste der Abgleich
  auf einen Stand aus Feldern und Überlagerung umgebaut werden; das ändert,
  wie die Unterschiede entstehen und in welcher Reihenfolge ein nachzügelnder
  Spieler die Einträge bekommt. Für eine einmalige Sendung pro Öffnen nicht
  gemacht. Kopie und Eintragsliste in einem Gang zu bauen spart etwa 1 ms bei
  50.000; zu wenig für einen eigenen Schritt.
- **Indexaufbau nach jedem Netzumbau** (5–12 ms bei 50.000 Sorten). Das Lager
  verwirft den Index bei jedem `setDrives`, auch wenn es dieselben Laufwerke
  sind. Das zu überspringen ist naheliegend, aber ein Laufwerk zwischen zwei
  Netzen wird nur über diesen Neuaufbau wieder richtig gezählt: Das andere
  Netz ändert die Zellen, ohne die Revision des Laufwerks zu erhöhen.
  Abgelehnt, solange das so ist.
- **Gleichtakt der Worker.** 64 Worker, im selben Tick angewendet, laufen
  jedes Intervall im selben Tick. Daher das p95 von 2 ms im Worker-Fenster
  bei einem Mittel von 0,3 ms. Verteilen würde ändern, wann ein Worker läuft;
  das ist Verhalten.
- **Eine ganz neue Sorte einlagern** kostet bei 50.000 Sorten rund 11 µs, die
  Suche nach einer Zelle mit freiem Typplatz. Unverändert seit gestern.
- **Nicht im Lauf und deshalb nicht angefasst:** `HallLink.find` kopiert den
  Bestand der Halle bei jedem Aufruf mit Filter; `move … from storage` in
  Abläufen kopiert die Schlüssel des Lagers; `DisplayValues` (einmal pro
  Sekunde pro Anzeige) ruft `byItem()`, das über den ganzen Index geht. Keine
  Zahlen, also keine Änderung.

## Nicht geprüft

Kein Client: Die Ansicht im Terminal, das Senden über eine echte Verbindung und
Mehrspieler sind heute nicht angesehen worden. Paketinhalt und Reihenfolge
sind durch die Änderungen nicht berührt, das ist aber aus dem Code geschlossen,
nicht beobachtet.

Tests: 664 Unit-Tests, 378 Spieltests (einer neu), alle grün.
