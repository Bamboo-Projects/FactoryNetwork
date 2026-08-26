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
4. ~~**Setzen, Treffen, Aussehen.**~~ **Gebaut** (26.08.).
   Rechtsklick mit einem Connector auf eine Kabelfläche setzt ihn dorthin;
   schleichend mit leerer Hand nimmt ihn wieder ab; beim Abbauen des Kabels
   fallen alle seine Anschlüsse heraus. Die Trefferfläche wächst um Platte
   und Stiel, und der Renderer zeichnet beides. <i>(Der Stiel ist am selben
   Tag wieder verschwunden — siehe unten.)</i>

   Vier Entscheidungen, jede mit ihrem Grund:

   - **Eine belegte Fläche meldet „erledigt", nicht „Fehlschlag".** Ein
     `FAIL` fällt in Minecraft durch auf den Gegenstand — und der setzt dann
     einen Connectorblock in die Lücke daneben. Wer auf eine besetzte Fläche
     klickt, bekommt deshalb einen Satz statt eines Blocks an falscher
     Stelle. Die Fluchtluke ist der schleichende Klick: Er umgeht diesen Weg
     ganz.
   - ~~**Eine Fläche mit Anschluss verbindet nicht.**~~ **Am selben Tag
     umgedreht.** Die Regel war richtig gedacht und im Spiel falsch: Der
     graue Stiel zwischen Platte und Kern war ein Fremdkörper in einer
     Leitung, die sonst überall durchläuft — am Kabelbündel sah es aus, als
     hinge der Anschluss daneben statt daran.

     **Jetzt gilt: Ein Anschluss zählt wie ein Nachbar.** Die Fläche bekommt
     einen gewöhnlichen Arm in der Farbe des Kabels, und am Kabel entsteht
     eine sichtbare Kreuzung. Durch die Platte läuft er nicht: Sie hat keinen
     Stiel mehr, vor dem er halten müsste, und ihre Vorderseite deckt ihn ab.
     Damit fällt auch `CableLayout.stemLength` weg, und ein Teilmodell hat an
     beiden Kabelstärken dieselben zwei Kästen — Platte und Lämpchenring.
   - **Zwölf Modelldateien statt zweier gedrehter.** Drehen müsste der
     Renderer, und ob eine Quaternion stimmt, sieht man erst im Spiel.
     Erzeugte Dateien lassen sich Zahl für Zahl gegen `CableLayout` prüfen —
     `CableLayoutTest` tut das, und eine Gegenprobe mit geänderter Tiefe hat
     bestätigt, dass er dabei wirklich zuschlägt.
   - **Gezeichnet statt gebacken.** Welche Flächen ein Teil tragen, steht in
     der BlockEntity. Es in den Blockzustand zu nehmen hieße sechs weitere
     Wahrheitswerte — mal sechs Verbindungen, mal siebzehn Farben: fast
     siebzigtausend Zustände je Kabelart. **Der Preis:** Mit einem
     angemeldeten Renderer landet jede Kabel-BlockEntity in der Zeichenliste,
     auch die ohne Teile; der Rücksprung steht in der ersten Zeile. Was das
     bei zehntausend Kabeln kostet, ist ungemessen — ein Wechsel auf ein
     gebackenes Modell bliebe möglich, weil hier nichts gespeichert wird.

Nach Schnitt 2 gibt es Anschlüsse im Kabelblock; nach Schnitt 3 mehrere je
Block; nach Schnitt 4 sieht man sie, trifft sie und kann sie bauen.

### 4b — die drei Anzeigen (26.08., gebaut)

Alle drei lasen einen Anschluss je Stelle. Jede bekam die Antwort, die zu ihr
passt, und die drei Antworten sind verschieden:

- **Der Namenszug** stapelt. Sechs Namen an einem Kabelblock stehen
  übereinander statt ineinander, und sobald mehr als einer da ist, steht die
  Fläche davor: `N kiste_1`. Bei einem einzigen bleibt es beim nackten Namen —
  die Angabe wäre dort nur Beiwerk.
- **Jade** fragt die getroffene Fläche. Wer auf einen Anschluss sieht, liest
  über diesen; am eigenen Connectorblock fällt der Weg still auf den einen
  zurück, der dort sitzt. Nebenbei stand Jade am Kabel bis heute gar nichts
  über Anschlüsse — der Block war nicht angemeldet.
- **Der Analysator** fasst zusammen. Er zeichnet Punkte in den Raum, und ein
  Kabelblock ist **ein** Punkt; zwei Knoten an derselben Stelle hießen zwei
  Beschriftungen aufeinander. Der Punkt nennt deshalb beide Namen
  (`btwopartsAreOnePointInThePicture`).

Damit ist Weg B vollständig.

---

## 9. Der eigene Block ist weg (26.08.)

Nach der ersten Runde Spielen: **„entfern den vollständigen Block. Es gibt nur
den kleine version davon die an das Kabel geht."**

Der Connectorblock konnte dasselbe wie das Teil und brauchte einen Platz mehr.
Solange beide Bauformen nebeneinander standen, kostete das an jeder Stelle
eine Fallunterscheidung — und an jeder Stelle die Frage, welche der beiden
gemeint ist.

**Was mit ihm verschwindet:** `ConnectorBlock`, `ConnectorBlockEntity`, sein
Blockzustand, sein Modell, seine Loot-Table, sein Eintrag im
Spitzhacken-Tag, der Farbhandler für sein Lämpchen und zwei Jade-Anmeldungen.
`Connectors` kennt nur noch eine Bauform: vier Methoden, die vorher je zwei
Zweige hatten, sind jetzt je eine Zeile. Der Gegenstand heißt weiter
`factorynetwork:connector` und ist jetzt ein gewöhnlicher `Item`.

**Was bleibt:** {@code Connectors.at(level, pos)} ohne Seite. Es war als
Übergang gedacht und ist keiner: Wer nur einen Punkt im Raum hat — der
Analysator, die Beschriftungspistole, das Namensfenster —, bekommt den
Anschluss, wenn dort genau einer sitzt.

**Der Umzug der Prüfläufe** lief in einer eigenen Vorstufe (Commit davor):
Vierzig Stellen setzen statt eines Connectorblocks ein Kabel mit einem
Anschluss, an derselben Stelle und mit derselben Blickrichtung — deshalb blieb
jede Kiste liegen, wo sie lag. Vier Prüfläufe hingen daran, dass ein Connector
eine Sackgasse ist; sie stehen in ihrem Commit einzeln beschrieben.

**Bestehende Welten verlieren ihre Connectorblöcke samt Namen.** Das war beim
Wechsel auf Weg B schon so angekündigt und ist mit dem Ausbau endgültig.
