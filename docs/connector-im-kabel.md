# Der Connector im Kabelblock

Gewünscht am 26.08.: „bekommen wir das hin, dass der Connector in den
Kabelblock mit reinpasst wie bei AE2?"

Dieses Dokument misst, was das kostet, und legt eine Entscheidung vor. Es baut
nichts.

---

## 1. Was heute dasteht

Ein Connector ist ein **eigener Block**. Er zeigt mit `FACING` auf eine
Maschine, hängt an einem Kabel und kostet einen Kanal. Für eine Maschine
braucht man deshalb zwei Blöcke nebeneinander: Kabel und Connector.

Bei AE2 ist das anders. Dort gibt es einen **Kabelbus**: einen Block, der in
seiner Mitte ein Kabel führt und an jeder seiner sechs Flächen ein *Teil*
tragen kann — Terminal, Interface, Bus. Ein Block, bis zu sieben Dinge.

**Die Maße stimmen schon überein.** `CableLayout` nennt sechs Blockpixel für
das gewöhnliche und zehn für das dichte Kabel — genau AE2s ummanteltes und
dense. Der Platz an den Flächen ist also da.

---

## 2. Zwei sehr verschiedene Antworten

Die Frage klingt nach einer Sache, ist aber zwei.

### A — Der Connector ist ein Kabelstück

Kein Multipart. Der Connector **verbindet sich wie ein Kabel** und trägt an
einer Seite sein Gesicht zur Maschine. Man legt keine Leitung mehr *neben*
ihn, sondern *durch* ihn.

```
heute:   ── Kabel ── Kabel ──        A:   ── Kabel ── Connector ── Kabel ──
              │                                          ▼
          Connector ── Maschine                       Maschine
              ▼
```

Was man gewinnt: **ein Block je Maschine statt zwei**, und die Kabellinie
läuft durch. Das ist der Anblick, den man von AE2 kennt, für den Normalfall.

Was man nicht gewinnt: zwei Maschinen an einem Block. Wer links und rechts
eine stehen hat, setzt weiter zwei Connectoren.

### B — Echtes Multipart

Ein Kabelblock mit bis zu sechs Connectoren, einer je Fläche. Das ist AE2s
Modell in ganz.

Was man gewinnt: **echte Dichte.** Ein Block in einer Maschinenwand bedient
sechs Nachbarn.

---

## 3. Was es kostet, gemessen

### A: klein und beherrschbar

| Was | Umfang |
|---|---|
| `ConnectorBlock` verbindet und rendert wie ein Kabel | mittel — Formen und Modelle gibt es schon, sie brauchen eine Variante mit Gesicht |
| `FactoryGraph`: der Connector leitet weiter | klein, drei Zeilen — dieselbe Änderung wie beim Gateway am 26.08. |
| `tools/assets.py` und `CableShapes` | mittel, aber der Test wacht darüber |
| Alles andere | **keine Zeile** |

**Der letzte Punkt ist der eigentliche Befund.** `ConnectorBlockEntity` steht
an **106 Stellen in 19 Dateien** — jede davon fragt `getBlockEntity(pos)` und
bekommt genau eine. Solange ein Block einen Connector trägt, bleibt das wahr,
und keine dieser Stellen wird angefasst.

### B: teuer, und der Preis liegt nicht dort, wo man ihn vermutet

| Was | Umfang |
|---|---|
| Ein Kabelbus mit sechs Teilen | groß — Teile, Speichern, Netzwerkabgleich |
| Formen je Fläche zusammensetzen, Treffer je Fläche prüfen | groß |
| Modell, das sich aus Teilen zusammensetzt | groß — heute sind es feste JSON-Dateien aus `assets.py` |
| **Die 106 Stellen** | **jede einzelne** |

Die 106 Stellen sind der Punkt. `getBlockEntity(pos) instanceof
ConnectorBlockEntity` ist keine Ausnahme, sondern die Art, wie diese Mod über
Geräte spricht — im Graphen, in der Geräteerkennung, in der Laufzeit, in den
Paketen, im Editor, in den Prüfläufen. Mit sechs Teilen je Block ist die Frage
„welcher Connector ist an dieser Stelle" nicht mehr beantwortbar, ohne eine
Seite mitzugeben.

Dazu drei Stellen, die es einzeln schwer machen:

- **Redstone.** `ConnectorBlock` ist eine Signalquelle. Bei sechs Teilen muss
  ein Block das Signal aus allen zusammenfassen — und beim Lesen entscheiden,
  welches Teil an dieser Seite spricht.
- **Das Benennen.** Ein Rechtsklick öffnet heute das Fenster des Blocks. Bei
  sechs Teilen muss der Klick wissen, welche Fläche getroffen wurde.
- **`ConnectorBlock.machineSide(state)`.** Die Blickrichtung steht heute im
  BlockState. Bei sechs Teilen liegt sie im Teil, und `state` reicht nicht
  mehr.

---

## 4. Was in beiden Fällen bricht

**Bestehende Welten verlieren ihre Connectoren.** Ein Blocktyp, dessen Form
und Zustand sich ändern, ist beim Laden nicht mehr derselbe.

Das ist heute **billig**: Die Mod steht bei 0.1.0, ist ungespielt, und die
Welten sind Prüfstände. In sechs Monaten ist es das nicht mehr. **Wenn B je
kommen soll, ist jetzt der günstigste Zeitpunkt** — das ist das stärkste
Argument, das für B spricht, und es ist ein Argument über den Kalender und
nicht über die Sache.

---

## 5. Empfehlung: A, und B nicht ausschließen

Drei Gründe:

1. **A liefert den gewünschten Anblick.** Die Frage lautete „passt der
   Connector in den Kabelblock" — bei A tut er das. Die Kabellinie läuft
   durch ihn hindurch, und für eine Maschine steht ein Block statt zwei.
2. **A kostet fast nichts an der Sprache.** Keine der 106 Stellen wird
   angefasst. Der Graph bekommt dieselbe Änderung, die das Gateway am 26.08.
   bekommen hat, und die ist drei Zeilen lang.
3. **A verbaut B nicht.** Ein Connector, der sich wie ein Kabel verbindet, ist
   die halbe Strecke zu einem Kabelbus: Was danach fehlt, ist die Vervielfachung
   auf sechs Flächen — nicht die Verschmelzung mit dem Kabel.

**Gegen A spricht ein Fall:** Wer eine Maschinenwand baut, will einen Block
für sechs Nachbarn. Bei A stehen dort sechs Blöcke. Ob das in der Praxis
stört, weiß erst eine Runde Spielen — und danach lässt sich B immer noch
bauen, dann mit einem Argument aus dem Spiel statt aus dem Vergleich.

---

## 6. Wie A in Schnitte zerfällt

1. **Der Connector verbindet sich wie ein Kabel.** Form, Modell und die drei
   Zeilen im Graphen. Danach braucht eine Maschine einen Block, und die
   Kabellinie läuft durch.
2. **Die Blickrichtung beim Setzen.** Heute zeigt der Connector dorthin, wo
   man hingeklickt hat. Das bleibt richtig — nur ist er jetzt auch eine
   Leitung, und das Setzen mitten in eine Linie soll die Linie nicht
   zerschneiden.
3. **Das Aussehen.** Ein Kabel mit einem Gesicht ist ein anderes Modell als
   ein Kabel. `assets.py` erzeugt sie; `CableLayoutTest` hält Modell und
   Trefferfläche zusammen.

Jeder Schnitt ist für sich brauchbar: Nach dem ersten spart man schon einen
Block je Maschine.

---

## 7. Entschieden: B (26.08.)

**Wie bei AE2**, auf Wunsch des Projektinhabers. Die Empfehlung war A; die
Entscheidung ist B, und das Argument dafür steht in Abschnitt 4: Bestehende
Welten brechen so oder so, und heute ist das billig.

Damit gilt der Abschnitt 6 nicht — er beschrieb A. Wie B zerfällt, steht
hier.

---

## 8. Wie B in Schnitte zerfällt

Die Messung in Abschnitt 3 sagt, wo der Preis liegt: an den Stellen, die
`getBlockEntity(pos)` fragen und genau einen Connector erwarten. Es sind
**einunddreißig** echte Zugriffe, zweiundzwanzig weitere Nennungen stehen in
den Prüfläufen. Die Schnitte sind danach geordnet, wie viele davon sie
anfassen.

1. ~~**Das Teil vom Block trennen.**~~ **Gebaut** (26.08.).
   `ConnectorPart` ist alles, was ein Connector ist — Name, Kanalbedarf,
   Redstone, der Griff auf die Maschine —, ohne alles, was ein Block ist. Wer
   es hält, steht in `ConnectorPart.Host`; heute ist das die
   `ConnectorBlockEntity` mit genau einem. **Keine der einunddreißig Stellen
   angefasst**, kein Verhalten geändert; belegt durch den unveränderten
   Prüflauf.
2. ~~**Der Kabelblock trägt Teile.**~~ **Gebaut** (26.08.).
   `CableBusBlockEntity` hält bis zu sechs `ConnectorPart`, je Fläche eine
   eigene Sicht auf denselben Block; `Connectors.at(level, pos, seite)` ist
   der eine Weg, einen Anschluss zu finden, und `Connectors.at(level, pos)`
   beantwortet die alte Frage weiter — aber nur, wenn es genau einen gibt.
   Der Graph zählt ein Kabel mit Anschlüssen als Gerät, die Laufzeit greift
   durch sie hindurch. Belegt Ende zu Ende: `amoveRunsThroughApartOnTheCable`
   holt Erz aus einer Kiste, die an einem Kabel und nicht an einem
   Connectorblock hängt.

   **Zwei Dinge stehen noch aus und sind benannt:** Zwei Anschlüsse an einem
   Block sind für den Graphen noch ein Gerät — er merkt sich einen Ort und
   keine Seite; `twopartsOnOneBlockAreTwoDevices` hält fest, was Schnitt 3 zu
   lösen hat. Und **jeder** Kabelblock trägt jetzt eine BlockEntity, auch
   ohne Teile: Was das bei zehntausend Kabeln kostet, ist ungemessen.
3. ~~**Ein Gerät ist Ort und Seite.**~~ **Gebaut** (26.08.).
   `DevicePos` ist Ort **und** Fläche, und der Graph schlüsselt damit auf.
   Zwei benannte Anschlüsse an einem Kabelblock sind seither zwei Geräte mit
   zwei Namen, zwei Kanälen und zwei Maschinen dahinter
   (`btwoNamedPartsOnOneCableAreTwoDevices`). Die Fläche ist `null`, wo es
   keine gibt: Ein Laufwerk, ein Schrank, eine Anzeige sind ganze Blöcke.

   Die drei schweren Stellen aus Abschnitt 3, jede mit ihrer Regel:

   - **Redstone.** *Eine Fläche mit Anschluss gibt genau dessen Stärke; eine
     freie gibt die stärkste.* Der erste Teil ist der Sinn der Sache — sechs
     Anschlüsse schalten sechs Maschinen, und eine gemeinsame Stärke wären
     sechs Maschinen an einem Schalter. Der zweite hält, was der
     Connectorblock schon konnte: Bei einem einzigen Anschluss kommt nach
     allen Seiten dasselbe heraus, und ein Lämpchen neben dem Kabel leuchtet
     weiter. **Gelesen** wird dagegen weiter am Block und nicht an der Fläche
     — wer einen Hebel neben einen Anschluss legt, meint diesen Anschluss,
     und die Fläche davor ist von der Maschine besetzt. Der Kommentar an
     `WorldHost.redstone` behauptete bis heute das Gegenteil von dem, was der
     Code tat; jetzt steht dort die Regel.
   - **Das Benennen.** Der Klick trägt die Fläche mit: `NameMenu` und
     `SetBlockNamePacket` führen sie, `CableBlock.useWithoutItem` liest sie
     aus dem Treffer. Der Connectorblock schickt seine eigene Blickrichtung
     mit — er hat nur eine, aber dahinter läuft derselbe Weg. Die
     Beschriftungspistole fragt erst die getroffene Fläche und dann den
     Block.
   - **`machineSide(state)`.** Ist als Frage an einen Anschluss verschwunden:
     Sie steht nur noch dort, wo es wirklich um den Connectorblock geht.
     Alles andere fragt `ConnectorPart.facing()`.

   **Nebenbei gefunden:** Die Anlagenerkennung hielt an jedem Kabel für eine
   Leitung und sammelte nur ganze Blöcke ein — ein Anschluss am Kabel hätte
   den Anlagennamen des Gateways nie bekommen und in jedem Programm gefehlt,
   das `werk_1/eingang` schreibt (`bagatewayNamesApartOnTheCable`).

   **Offen und benannt:** Der Namenszug über dem Block, Jade und der
   Analysator zeigen weiter einen Anschluss je Stelle — sitzen zwei daran,
   stehen zwei Beschriftungen aufeinander. Das ist Anzeige und keine
   Mechanik; es gehört zu Schnitt 4, wo Teile überhaupt erst zu sehen und zu
   treffen sind.
4. **Setzen, Treffen, Aussehen.** Ein Connector wird an eine Kabelfläche
   gesetzt statt daneben; Form und Modell setzen sich aus Kabel und Teilen
   zusammen. `CableLayoutTest` hält beides zusammen.

Nach Schnitt 2 gibt es Anschlüsse im Kabelblock; nach Schnitt 3 mehrere je
Block; nach Schnitt 4 sieht man es und trifft es.
