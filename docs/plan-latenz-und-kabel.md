# Latenz und ein Kabel statt zwei — Umsetzungsplan

**Auftrag:** „wie meinst du das mit der Latenz. Die sollte sehr gering sein
durch das glasfaser. wir brauchen dann auch kein dense cable mehr finde ich.
das reicht das dünne." (30.08.)

---

## Was ich mit Latenz meinte — und warum du recht hast

**Mein Gedanke war:** Entfernung kostet Zeit, also wartet ein Gerät zwanzig
Blöcke weiter länger als eines direkt am Controller.

**Dein Einwand trifft:** Licht braucht für zwanzig Blöcke 60 Nanosekunden. Ein
Minecraft-Tick ist 50 Millisekunden — **eine Million mal länger**. Latenz aus
Entfernung wäre in dieser Mod frei erfunden.

**Was in einem echten Netz wirklich Zeit kostet, ist die Verarbeitung:** Jeder
Switch auf dem Weg packt das Paket aus, sieht nach, packt es wieder ein. Bei
Glasfaser über Kontinente ist das der größere Teil der Latenz, nicht die
Strecke.

**Also: Latenz je Router, nicht je Block.**

| | Verzögerung |
|---|---|
| Kabel, egal wie lang | keine |
| je Router auf dem Weg | 1 Tick |
| Quantum-Brücke | 1 Tick |

Ein Gerät hinter drei Routern antwortet drei Ticks später. **Das ist zugleich
das ehrlichere Bild** — und es gibt dem Router eine Eigenschaft, die man beim
Bauen abwägt: Filtern kostet.

## Ein Kabel statt zwei

**Du hast recht, und der Grund ist stärker, als du geschrieben hast:** Seit
der Router filtert statt zu bündeln, ist seine eigene Begründung überholt. Im
Code steht noch:

> „Kreuzung für dicke Kabel. Beim dünnen Kabel trennt die Farbe, und vier
> dünne Stränge passen nebeneinander in einen Block."

Das stimmt seit dem 29.08. nicht mehr. **Das dichte Kabel war die Antwort auf
eine Frage, die es nicht mehr gibt.**

**Was wegfällt:** ein Block, siebzehn Gegenstände, ein Modell, ein Rezept, eine
Loot-Tabelle — und die Erklärung, wann man welches nimmt.

### Der Einwand, den ich dir schulde

**Ein Kabel heißt: eine Bandbreite für alle.** Damit gibt es keinen Ausbauweg
mehr an der Leitung — wer mehr Durchsatz braucht, kann nichts dagegen tun
außer das Netz zu teilen.

**Das ist verkraftbar, weil der Ausbau woanders hinwandert:** Der Controller
bekommt eine Grenze und der Anbau hebt sie (`plan-controller-grenze.md`). Der
Fortschritt liegt dann am Controller statt am Kabel — an einer Stelle statt an
zweien.

**Und die verbleibende Bandbreite muss großzügiger werden.** Heute trägt das
dünne 2,5 MB/s, das dichte 25. Wenn nur eines bleibt, wäre 2,5 zu eng: Zehn
Worker mit `rate 64 per 1t` brauchen 12,8 MB/s.

**Vorschlag: 25,6 MB/s** — der Wert des dichten Kabels. Es heißt weiter
„Kabel"; was es kann, entspricht Glasfaser.

## Der Router ohne dichtes Kabel

**Er bleibt und bekommt seine Begründung neu.** Er filtert Farben aus dem
Hauptstrang — das hat mit der Kabelstärke nie etwas zu tun gehabt. Nur der
Kommentar sagt es noch.

**Und mit Latenz bekommt er einen Preis:** Ein Router kostet einen Tick. Wer
sein Netz sauber trennt, zahlt dafür — genau wie in einem echten Netz.

## Die Aufgaben

- [ ] **1. Latenz je Router.** `FactoryGraph.pathTo` liefert den Weg; die Zahl
      der Router darauf ist die Verzögerung. Ein Worker beginnt seinen Griff
      um so viele Ticks später. **Prüflauf:** Ein Gerät hinter zwei Routern
      antwortet zwei Ticks später als eines am selben Kabel.
- [ ] **2. Der Router zeigt seine Latenz.** Im Fenster und in Jade — sonst
      ist es eine Verzögerung ohne Ursache.
- [ ] **3. Das Kabel wird schneller.** `Bandwidth.THIN` auf den Wert des
      dichten. Eine Zeile.
- [ ] **4. Das dichte Kabel fällt.** Block, Gegenstände, Modell, Rezept, Loot,
      Sprachtexte. **Bestehende Welten:** Ein dichtes Kabel im Boden wird zum
      gewöhnlichen — dieselbe Bandbreite, andere Textur. Kein Verlust.
- [ ] **5. Die Begründungen nachziehen.** Der Router-Kommentar, `Bandwidth`,
      `entscheidungen.md`.

## Was ich dabei nicht anfasse

**Die Farben.** Siebzehn bleiben, sie sind die VLANs.

**Die Quantum-Brücke** trägt weiter, was ein Kabel trägt — sie ist eine
Leitung, kein Vermehrer. Nur heißt das jetzt „wie ein Kabel" statt „wie ein
dichtes".
