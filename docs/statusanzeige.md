# Die Statusanzeige am Anschluss

Gewünscht am 26.08.: „wie viel Performance frisst eine Statusanzeige an den
Connectoren?" — und nach der Antwort: nur der Netzzustand.

Dieses Dokument hält fest, was gemessen und was entschieden wurde.

---

## 1. Was man am Block bisher nicht sah

Vier Zustände sehen im Spiel gleich aus, und drei davon bedeuten „nicht
ansprechbar" aus drei verschiedenen Gründen:

| Zustand | Warum nicht ansprechbar | Wo man sucht |
|---|---|---|
| benannt und erreichbar | — | — |
| ohne Namen | im Code gibt es keinen Namen dafür | am Anschluss |
| doppelt vergeben | beide sind unbrauchbar | am zweiten Anschluss |
| ohne freien Kanal | die Leitung ist voll | an der Kanalgrenze |
| gar nicht am Netz | es führt kein Kabel hin | an der Leitung |

Jade sagte das schon, aber nur, wenn man draufsah und Jade installiert hatte.
Der Analysator sagte es, aber nur mit dem Werkzeug in der Hand. Wer vor einer
Wand aus zwölf Anschlüssen stand, sah zwölfmal dasselbe.

---

## 2. Was es kostet, in drei Töpfen

Die Frage „wie teuer ist eine Statusanzeige" hat keine Antwort, solange nicht
feststeht, **was** sie anzeigt. Es sind drei sehr verschiedene Kosten.

### Zeichnen: null

Nicht „wenig", sondern **null zusätzliche Zeichenaufrufe**. Das Lämpchen ist
kein eigenes Ding, das jemand malt, sondern ein Kasten im Modell, das ohnehin
schon gemalt wird:

- Am **Kabel** liegt der Ring im Teilmodell, das der `CableBusRenderer` seit
  Weg B ohnehin zeichnet. Vier Quads mehr im selben Aufruf.
- Am **Connectorblock** liegen vier kleine Lämpchen im gebackenen Blockmodell.
  Dort zeichnet niemand etwas — die Farbe kommt über einen `tintindex` und
  einen Farbhandler, denselben Weg, den die Kabelfarben schon gehen.

Ein BlockEntity-Renderer für den Connectorblock hätte **jeden** Connector in
die Zeichenliste gebracht. Für vier Quads, die das Modell umsonst mitbringt.

### Übertragen: nur bei Änderung

Der Zustand reist im gewöhnlichen BlockEntity-Paket mit, und `setState`
schickt nur, wenn er wirklich ein anderer ist. Der Neuaufbau läuft alle 100
Ticks und stempelt jedes Mal alles — ohne diese Prüfung ginge alle fünf
Sekunden ein Paket je Anschluss an jeden, der den Chunk verfolgt.

Zum Vergleich, warum das die entscheidende Zeile ist: Ein Paket trägt das
ganze Teil (Name, Kanalbedarf, Redstone, Zustand) und wiegt rund 200 Byte.
Hundert Anschlüsse alle fünf Sekunden wären 4 KB/s je Spieler — für nichts.
Mit der Prüfung ist es null, solange sich nichts ändert.

### Rechnen: nichts Neues

`starved`, `unnamed` und `isAmbiguous` rechnet der Graph beim Neuaufbau
ohnehin. Der Stempel liest sie nur ab.

---

## 3. Warum nicht Betrieb und Füllstand

Ein Lämpchen, das zeigt, ob gerade etwas bewegt wird, kostet in denselben
drei Töpfen völlig anders:

- **Zeichnen** bliebe gratis.
- **Rechnen** wäre erträglich: `contentsFingerprint` liest jedes Fach, und die
  Messung aus `speicherbus.md` sagt rund 24 ns je Fach — eine Kiste ist etwa
  650 ns, hundert Anschlüsse alle zehn Ticks etwa 6,5 µs je Tick.
- **Übertragen** wäre die Wand. Bei hundert Anschlüssen im Tick-Takt sind das
  400 KB/s je Spieler.

Dazu ein Nebeneffekt, der schwerer wiegt als die Zahlen: Die Schleife, die den
Inhalt der Maschinen abtastet, läuft heute **nur**, wenn ein Programm auf
`device_changed` oder `device_output` hört. Eine Statusanzeige machte aus
dieser Opt-in-Last eine dauernde für jedes Netz.

**Entschieden am 26.08.: nur der Netzzustand.**

---

## 4. Eine Falle, die naheliegt

Den Zustand in den Blockzustand zu legen klingt billiger — kein Renderer,
kein Paket, das Modell entscheidet sich selbst. Es ist teurer:

- Am **Kabel** unmöglich: sechs Flächen mal fünf Zustände, mal sechs
  Verbindungen, mal siebzehn Farben. Zustände legt Minecraft beim Start alle
  an.
- Am **Connectorblock** möglich (sechs Blickrichtungen mal fünf Zustände sind
  dreißig), aber jede Änderung baut das ganze Chunk-Segment neu auf —
  Millisekunden statt Nanosekunden. Bei etwas, das sich ändert, ist das die
  schlechteste Wahl.

---

## 5. Der Zustand ist ein Schatten

Gerechnet wird er im Graphen, aufgehoben am Anschluss. Das ist bewusst **eine**
Wahrheit mit **einer** Verzögerung:

- Der Controller stempelt beim Neuaufbau — alle 100 Ticks und bei jeder
  Änderung am Netz.
- Wer aus dem Graphen fällt, wird eigens auf `OFFLINE` zurückgesetzt. Ohne das
  stünde ein abgeschnittener Anschluss für immer auf Grün, und ein grünes
  Lämpchen an einem abgeschnittenen Gerät ist schlimmer als gar keines. Ein
  Prüflauf hält das fest, und die Gegenprobe mit ausgebauter Rückstellung hat
  bestätigt, dass er zuschlägt.
- **Jade liest denselben Stempel.** Vorher rechnete es dieselbe Frage ein
  zweites Mal aus dem Graphen — dieselbe Frage, zwei Antworten, und die eine
  hätte Verbesserungen bekommen, die der anderen fehlen. Das ist der Fehler,
  der in dieser Sitzung schon dreimal dastand.

Hängt ein Anschluss an zwei Netzen, gewinnt der Controller, der zuletzt
gestempelt hat. Derselbe Fall meldet der Reiter *Netz* als umstritten; er ist
selten genug, um ihn hier nicht eigens aufzulösen.

---

## 6. Die Farben

| Zustand | Farbe |
|---|---|
| benannt und erreichbar | hellgrün |
| ohne Namen | grau |
| doppelt vergeben | gelb |
| ohne freien Kanal | rot |
| gar nicht am Netz | dunkelgrau |

Sie stehen **an einer Stelle** (`DeviceStateColours`), weil sie an dreien
gebraucht werden: am Lämpchen des Kabelanschlusses, am Lämpchen des
Connectorblocks und am Namenszug über beiden.

---

## 7. Was ungeprüft bleibt

Alles Sichtbare. Ob der Ring an der richtigen Fläche sitzt, ob das Lämpchen am
Connectorblock zu sehen ist, ob die Farben nebeneinander unterscheidbar
bleiben — das sagt kein Prüflauf. Der Rest, also der Zustand selbst und seine
Rückstellung, steht unter Test.
