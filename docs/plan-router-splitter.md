# Der Router als Splitter — erledigt am 29.08.

**Auftrag:** „ich will ein kabel wo alle farben durch gehen aber man kann
durch den Routerblock einzelne farben aus dem hauptstrang abgreifen. also wie
bei glasfaser auch" (29.08.)

**Ziel:** Eine Hauptleitung trägt alle Farben. Der Router zieht daraus einzelne
heraus.

---

## Was heute dasteht — und warum es genau umgekehrt ist

**Farben trennen bereits.** `CableColour.connectsTo`: Gleiche Farbe verbindet,
neutral verbindet mit allem, verschiedene Farben berühren sich, ohne verbunden
zu sein. **Das ist die Trennung, die du für den Hauptstrang brauchst — sie
existiert.**

**Aber der Router hebt sie auf.** In `FactoryGraph.visitRouter` steht:

> „Farbneutral: Was auf einer Bahn zusammenkommt, ist verbunden, egal in
> welcher Farbe es ankam."

Er ist heute ein **Mischer**, kein Splitter. Vier Bahnen, die Wege
berührungslos kreuzen lassen — nützlich, aber nicht das, was du willst.

**Und das neutrale Kabel ist schon der Hauptstrang.** Ein Kabel ohne Farbe
verbindet sich mit allem: Alle Farben gehen hindurch. Was fehlt, ist nur der
Weg, eine einzelne wieder herauszuziehen.

## Die neue Bauform

**Der Router bekommt je Seite eine Farbe statt einer Bahnnummer.**

| Einstellung | Was durch diese Seite geht |
|---|---|
| **Alle** (Vorgabe) | jede Farbe — die Seite zum Hauptstrang |
| **Rot** | nur das rote Teilnetz |
| **Aus** | nichts |

**Ein Router mit einer „Alle"-Seite und drei Farbseiten ist ein Splitter.** Am
Hauptstrang hängt er mit „Alle"; an den anderen drei Seiten kommt je eine
Farbe heraus. Genau das Glasfaser-Bild.

**Und er bleibt, was er war.** Zwei Seiten auf dieselbe Farbe gestellt heißt
verbunden — wie zwei Seiten auf derselben Bahn. Die alte Funktion ist ein
Sonderfall der neuen; wer nur kreuzen will, stellt Farben statt Zahlen ein.

## Der Umzug alter Router

**Bahn 1 bis 4 werden zu vier Farben.** Wer heute Bahnen benutzt, hat nach dem
Umbau Farben — die Trennung bleibt dieselbe, nur heißt sie anders. `OFF`
bleibt `OFF`.

Das Speicherformat behält seinen Namen `Lanes`, die Zahlen bedeuten nur etwas
anderes. **Kein Migrationslauf**, dieselbe Entscheidung wie beim Zellenformat.

## Was das für die Bandbreite heißt

**Jede Farbe hat ihr eigenes Budget.** Ein Hauptstrang, der vier Farben trägt,
trägt sie nebeneinander — nicht in Konkurrenz. Das entspricht dem Bild: In
einer Glasfaser stören sich die Wellenlängen nicht.

**Der Knoten trägt die Farbe schon.** `FactoryGraph.Node` ist Ort *und* Farbe,
und `TickBudget` rechnet je Knoten. **Das fällt damit von selbst richtig aus** —
kein zusätzlicher Code.

## Die Aufgaben

- [x] **1. Der Router spricht Farben.** `RouterBlockEntity`: `lane(side)` wird
      `filter(side)` und gibt eine `CableColour` oder „aus". Prüflauf: Alle
      Werte lesen und schreiben sich zurück.
- [x] **2. Der Graph trennt statt zu mischen.** `visitRouter` gibt die Farbe
      weiter, mit der es ankam — außer die Seite filtert, dann nur diese.
      **Die Stelle, an der es scharf wird:** Ein Router darf zwei Farben nicht
      verschmelzen, sonst ist er wieder ein Mischer.
- [x] **3. Das Fenster.** Statt Bahnnummern eine Farbwahl je Seite.
- [x] **4. Was man von außen sieht.** Die Seiten färben sich nach ihrer
      Einstellung — sonst muss man das Fenster öffnen, um zu sehen, was
      wohin geht.

## Eine Frage, die ich nicht entscheiden kann

**Was macht ein Router mit einer Farbe, die er nicht abgreift?**

- **A: Sie geht durch** — der Router ist ein Abzweig, der Hauptstrang läuft
  weiter. Näher am Glasfaser-Bild, und man baut weniger Blöcke.
- **B: Sie endet** — der Router ist ein Verteiler mit genau vier Ausgängen.
  Klarer zu lesen, aber jede Abzweigung kostet einen Block.

**Ich baue A**, weil es deinem Satz „aus dem Hauptstrang abgreifen"
entspricht: Ein Abgriff nimmt etwas heraus und lässt den Rest laufen. Wenn du
B willst, ist es eine Zeile in `visitRouter`.

---

## Was beim Bauen anders kam

**Ein Router ist ein Knoten, nicht vier.** Der Knoten trug die Bahnnummer der
Eintrittsseite — und reichte damit nie zu einer Seite mit anderer Nummer
hinaus. Vier Bahnen waren vier Router im selben Block. Das fiel erst auf, als
der Filter stand und trotzdem nichts durchkam.

**Der Filter prüft die Farbe des Kabels dahinter, nicht die des Strangs
davor.** Erst stand dort `connectsTo` — und neutral verbindet sich mit allem,
also kam jedes neutrale Kabel durch jeden Filter. Ein Filter, den alles
passiert, ist keiner.

**Und die Gegenprobe hat den Test entlarvt, nicht den Code.** Die erste
Fassung prüfte nur, ob der kürzeste Weg durch das andere Kabel führt — der tut
es nie, egal ob der Filter wirkt. Sie blieb grün, während der Filter
abgeschaltet war. Die scharfe Fassung hängt ein Gerät hinter einen falsch
gefilterten Ausgang: Das ist der Fall, der ohne Filter durchkommt.

**Und beim Fenster kam noch etwas dazu:** Achtzehn Einstellungen mal sechs
Seiten wären hundertacht Knöpfe gewesen. Stattdessen zeigt jede Zeile ihren
Wert, ein Klick schaltet weiter, ein Rechtsklick zurück — dieselbe Geste wie
am Block. Der Name steht daneben, weil ein Farbfeld allein bei siebzehn
Farben nichts mehr aussagt: Hellblau und Cyan liegen zwei Pixel auseinander.

Am Block dasselbe Problem, andere Lösung: Der Ring ist jetzt grau und wird
beim Zeichnen eingefärbt, wie das Kabel auch. Zwei Kacheln statt achtzehn —
eine Textur mit achtzehn Kacheln könnte niemand mehr nachzeichnen.
