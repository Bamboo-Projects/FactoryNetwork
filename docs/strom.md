# Strom leiten und speichern — Entwurf

Wie aus dem Netz, das Strom verbraucht, eines wird, das ihn auch verteilt und
vorhält.

Stand: 2026-08-24

Setzt um, was `entscheidungen.md` unter „Strom wird geleitet und gespeichert"
festhält. Dort steht das Was und Warum, hier das Wie.

---

## 1. Was heute steht, und was sich ändert

Strom ist heute die **laufende Betriebsabgabe** des Netzes. Der Controller
nimmt Forge Energy an, jedes Gerät kostet FE je Tick (`Power`), und reicht der
Vorrat nicht, geht das Netz aus und muss hochfahren (`NetworkPower` mit
`RUNNING`, `OFF`, `BOOTING`). Als Quelle gibt es die Brennkammer und die
Kreativ-Stromquelle.

Zwei Dinge kommen dazu:

- **Das Netz gibt Strom ab** — an die Maschinen hinter den Connectoren, und
  nur, wenn ein Programm das sagt.
- **Der Vorrat wächst** über Energiezellen in denselben Laufwerken, in denen
  Gegenstands- und Flüssigkeitszellen stecken.

Was bleibt: Der Eigenbedarf, die drei Zustände, die Herkunft aus dem Pack.

---

## 2. Die Abgabe als Worker

Die Form steht in `entscheidungen.md`:

```
worker versorgung {
    from network
    to crusher_1
    filter power
    rate 40 per 1t
    priority 1
}
```

### `power` braucht kein Doppelpunkt, und das ist ein Problem

Die anderen Auswahlausdrücke tragen eine Sorte hinter dem Doppelpunkt:
`item:iron_ore`, `fluid:water`. **Strom hat keine Sorten** — es gibt nur FE.
`power:` mit leerem Rest wäre eine Lüge über die Form.

Der Lexer kennt aber genau vier Wörter mit Doppelpunkt
(`Lexer.SELECTOR_KINDS`), und alles andere ist ein Name. `filter power` liest
sich damit heute als „filtere auf das Gerät namens power".

**Vorschlag:** `power` wird ein Schlüsselwort, und der Parser baut daraus an
einer Auswahlstelle einen `Expr.Selector` mit `Kind.POWER` und leerem Pfad.
Damit bleibt alles andere, wie es ist: `WorkerKind.of` liest die Art wie
gehabt aus dem Filter, und die Laufzeit unterscheidet sie wie bei
Flüssigkeiten.

Der Preis: Wer seinen Connector `power` nennt, muss ihn im Code mit einem
Rückstrich schreiben — dieselbe Regel, die für `for` schon gilt
(`sprache.md`, Abschnitt 4). Das ist die kleinere Zumutung, verglichen mit
einem Doppelpunkt, hinter dem nichts steht.

### Die Seitenwarnung gilt mit

`Selector.Kind` bekommt einen fünften Wert, und damit wird das `switch` in
`NetworkCheck.checkSide` unvollständig — der Übersetzer sagt es, und der neue
Zweig schreibt sich von selbst: **Ein Strom-Worker verlangt einen
Energiespeicher an der angeschlossenen Seite.** Das Profil aus der
Geräteerkennung weiß das bereits (`hasEnergy`), die Warnung fällt ohne
Zusatzarbeit ab.

Wer seinen Connector an eine Seite hängt, an der die Maschine keinen Strom
annimmt, erfährt es also beim Tippen — und nicht daran, dass die Maschine
stumm stehen bleibt. Dieselben Stellen betrifft es in `Value.Kind` und
`WorldHost`; auch dort meldet der Übersetzer, was fehlt.

### Beide Richtungen aus einer Form

`from network to crusher_1` versorgt. `from akku_1 to network` zieht aus einem
fremden Speicher ins Netz. Dafür braucht es keine eigene Mechanik — nur die
Prüfung, dass mindestens eine Seite `network` ist. Strom von einer Maschine
direkt in die andere zu schieben, ohne dass das Netz beteiligt ist, wäre eine
Leitung ohne Kabel.

---

## 3. Die Reihenfolge in einem Tick

Heute zieht `NetworkPower.tick()` den Eigenbedarf ab und entscheidet über den
Zustand. Die Abgabe kommt **danach** und nur im Zustand `RUNNING`:

1. **Eigenbedarf zuerst.** Reicht der Vorrat nicht für `draw`, geht das Netz
   aus — wie bisher. Ein Netz, das sich selbst abschaltet, während es
   Maschinen füttert, wäre absurd.
2. **Erst im Zustand `RUNNING`.** Ein Netz, das gerade hochfährt, versorgt
   niemanden. Das ist keine Einschränkung, sondern der Sinn der Hochfahrzeit:
   Man soll merken, dass die Versorgung nicht reicht.
3. **Dann die Strom-Worker**, in der Reihenfolge ihrer `priority`.

**Die Abgabe zählt nicht in `draw`.** `draw` ist, was das Netz für seine
Bereitschaft braucht — Controller, Connectoren, Laufwerke. Strom, der
durchgereicht wird, ist kein Eigenbedarf, und ihn mitzuzählen hieße, dass ein
Netz sich abschaltet, weil es zu viel liefert.

Sichtbar wird die Abgabe trotzdem: Im Netz-Reiter steht sie als eigene Zahl
neben dem Bedarf.

---

## 4. Wenn es knapp wird

`priority` gibt die Reihenfolge vor, in der die Worker bedient werden — kleine
Zahl zuerst, wie überall in der Sprache. Jeder bekommt bis zu seiner `rate`,
solange der Vorrat reicht. **Wer leer ausgeht, geht leer aus.**

Verworfen: eine anteilige Verteilung, bei der alle etwas bekommen. Sie klingt
gerechter, führt aber dazu, dass bei Knappheit *alle* Maschinen langsamer
laufen und keine fertig wird — der Zustand, in dem man am längsten sucht,
warum nichts vorangeht. Mit einer Reihenfolge läuft die wichtige Maschine voll
und die unwichtige gar nicht, und das sieht man sofort.

**Innerhalb eines Workers wird nicht gedeckelt.** Eine Maschine, die 40 FE je
Tick annimmt, bekommt 40 oder weniger, je nachdem was übrig ist — nicht „ganz
oder gar nicht". Das ist der Normalfall bei FE-Maschinen: Sie füllen ihren
Puffer langsamer und arbeiten langsamer. Ein Alles-oder-nichts wäre eine
eigene Regel, die es sonst nirgends gibt.

Ein Worker, der nichts abbekommt, steht im Terminal als `WAITING_TARGET` —
derselbe Zustand wie ein Gegenstands-Worker vor einer vollen Kiste.

---

## 5. Keine Grenze über den Kabelpfad

**Verworfen am 25.08.** Hier stand ein Transportlimit: Jeder Strom-Worker
meldet seine `rate` auf seinem Kabelpfad an, `capacityAt` fragt je Segment,
wie viel durchpasst, und wer nicht durchpasst, bekommt eine Meldung beim
Übernehmen. Dichte Kabel hätten damit eine zweite Bedeutung bekommen — mehr
Kanäle und mehr Strom.

Der Einwand des Projektinhabers: **Warum überhaupt begrenzen?** Er trägt. Eine
Kabelgrenze wäre eine *zweite* Knappheit neben `priority`, für dieselbe
Ressource. Wer zu wenig Strom bekommt, müsste dann herausfinden, ob es am
Vorrat lag oder am Kabel — zwei Ursachen für dasselbe Symptom, und die zweite
sieht man nirgends.

**Es gibt damit genau eine Stromgrenze: die Aufnahme.** `Power.MAX_INPUT` im
`InternalBuffer` deckelt, was je Tick in den Controller läuft; die gibt es
schon. Kabel tragen beliebig viel, Kabelstufen entscheiden allein über
Kanäle.

Was damit aus diesem Entwurf fällt: die Pfadrechnung für Strom, die Anmeldung
beim Übernehmen und jeder zweite Zahlensatz am Kabel. Der Preis, offen
benannt: Ein einzelnes dünnes Kabel versorgt eine beliebig große Fabrik.

## 6. Der Vorrat: Energiezellen im Laufwerk

Eine dritte Zellenart neben `StorageCellItem` und `FluidCellItem`, in
denselben Laufwerken, mit demselben Regalfenster und derselben Bestückung an
der Front.

**Eine Energiezelle hat nur eine Zahl.** Gegenstands- und
Flüssigkeitszellen tragen zwei Grenzen — wie viele Sorten und wie viel Menge —,
und die Sorten sind das Knappe, das zum Sortieren treibt. Bei Strom gibt es
keine Sorten. Eine Energiezelle ist damit schlichter als ihre Geschwister, und
der Reiz, der bei den anderen im Sortieren liegt, fehlt hier ganz.

Das ist kein Mangel, sondern die Sache selbst: Ein Akku ist eine Zahl. Wer
mehr will, steckt eine größere Zelle ein oder eine zweite dazu — genau wie bei
Gegenständen.

`Power.PER_CELL` gibt es schon: Eine eingesetzte Zelle kostet laufend etwas.
Für Energiezellen gilt dasselbe, und es hat hier einen zusätzlichen Reiz —
ein Akku, der Strom kostet, um Strom zu halten, ist ein Akku mit
Selbstentladung.

Der Vorrat des Netzes ist danach die Summe aus dem Puffer im Controller
(`Power.CAPACITY`) und allen Energiezellen in den Laufwerken.

---

## 7. Was man sieht

- **Im Netz-Reiter:** Bedarf und Abgabe als getrennte Zahlen, dazu der Vorrat
  gegen die Gesamtkapazität.
- **Im Zeigen auf ein Gerät** (seit der Geräteerkennung): Der Stromstand der
  Maschine steht schon da, sobald sie einen Energiespeicher hat.
- **Am Worker:** sein Zustand wie bei jedem anderen — `RUNNING`, wenn Strom
  fließt, `WAITING_TARGET`, wenn die Maschine voll ist oder nichts übrig war.

---

## 8. Bewusst nicht dabei

**Eigene Generatoren.** `entscheidungen.md` verwirft sie: „ein halbherziger
eigener Generator konkurriert nur mit besseren". Die Brennkammer bleibt der
Einstieg, alles Weitere kommt aus dem Pack. Dass das Netz jetzt verteilt,
ändert daran nichts — es macht die Frage sogar kleiner, weil fremde
Generatoren über `from generator_1 to network` einspeisen.

**Strom als Wert in der Sprache.** `crusher_1.energy` oder `network.power`
gibt es nicht. Das gehört zu den Gerätemitgliedern aus `sprache.md` §6, die
insgesamt noch fehlen.

**Verbrauchsabhängige Kosten.** Ein Worker, der viel bewegt, kostet nicht mehr
als einer, der wartet — die Regel von 2026-08-22 gilt unverändert.

---

## 9. Offene Punkte

- **Was passiert bei `OFF` mit den versorgten Maschinen?** Sie behalten, was
  in ihrem eigenen Puffer liegt, und laufen daraus weiter. Das ist
  wahrscheinlich richtig — aber es heißt, dass ein Stromausfall im Netz an den
  Maschinen erst verzögert ankommt.
- ~~Ob die Abgabe den Wiederanlauf verzögern darf.~~ **Beantwortet am
  25.08.:** Nein. Bei `OFF` und `BOOTING` fließt nichts ab, also kann das Netz
  seinen `restartThreshold` immer erreichen. Der Fall „Netz füllt sich
  langsam, während Maschinen ziehen" tritt nicht ein.
- ~~Ob `rate ... per tick` die richtige Einheit ist.~~ **Entschieden am
  25.08.:** `rate 40 per 1t`. `1t` ist eine gültige Dauer, die Grammatik kann
  es heute; ein eigenes Wort `tick` wäre eine zweite Schreibweise für dieselbe
  Sache und zöge `per second` nach sich.
