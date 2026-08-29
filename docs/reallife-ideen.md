# Mehr Netzwerk in der Netzwerk-Mod — Ideensammlung

**Auftrag:** „im endeffekt will ich die etwas reallife in die mod bringen wenn
du verstehst was ich meine" (30.08.)

**Ich verstehe es so:** Wer Netzwerke kennt, soll die Mod lesen können, ohne
sie zu lernen. Nicht Simulation — Vokabular und Verhalten.

Dies ist eine Sammlung, kein Plan. Nichts davon ist entschieden.

---

## Was schon stimmt

Damit klar ist, worauf das aufbaut:

- **Bandbreite in Byte je Sekunde**, Kabel mit verschiedenen Klassen
- **Der Router filtert** — Farben sind VLANs, nur ohne den Namen
- **Der Serverschrank** spricht Threads, Memory, Disk
- **Die Quantum-Brücke** ist eine Standleitung über Entfernung
- **Bandbreite wird geteilt**, nicht zugeteilt — eng statt tot

## Was fehlt, nach Nutzen sortiert

### 1. Latenz — die Idee mit dem größten Ertrag

**Heute ist jedes Kabel gleich schnell, egal wie lang.** In einem echten Netz
kostet Entfernung Zeit.

**Was es bringt:** Die *Form* des Netzes bekommt eine Bedeutung, die ein
Programm sieht. Ein Worker, dessen Gerät zwanzig Blöcke entfernt hängt, wartet
einen Tick länger als einer direkt am Controller. Wer schnell will, baut kurz.

**Und es erklärt die Quantum-Brücke neu:** Sie überbrückt Entfernung ohne
Latenz — das ist ihr Wert, nicht nur die Bequemlichkeit.

**Der Preis:** Jeder Griff braucht einen Zeitstempel. Das ist Aufwand in der
Laufzeit und eine Zahl mehr, die man erklären muss.

### 2. Halbduplex und Vollduplex

**Ein gewöhnliches Kabel teilt sich die Bandbreite zwischen Hin- und Rückweg,
ein dichtes nicht.** Das ist der Unterschied zwischen einem alten Hub und
einem Switch — und er wäre in einer Zeile Code beschrieben.

**Was es bringt:** Ein zweiter Grund für das dichte Kabel, der nicht nur
„mehr" heißt.

### 3. Kollisionen bei Überlast

**Heute wird es bei Überlast nur langsamer.** Echte Netze verwerfen Pakete und
senden neu — deshalb *bricht* ein überlastetes Netz ein, statt sanft
langsamer zu werden.

**Was es bringt:** Überlast wird ein Zustand, den man vermeiden will, statt
einer Zahl, die man hinnimmt.

**Was dagegen spricht:** Es verletzt „eng, nicht tot" — die Regel, die wir am
29.08. bewusst gewählt haben. **Ich würde es nicht bauen**, aber es gehört auf
die Liste, weil es das Realistischste wäre.

### 4. Namen: DNS statt Beschriftung

**Heute heißt jeder Anschluss, wie du ihn nennst.** Ein Namensdienst wäre die
nächste Stufe: eine zentrale Stelle, die Namen auf Geräte abbildet.

**Was es bringt:** Umbenennen an einer Stelle statt an zwölf — und ein
Aliasname, unter dem mehrere Geräte antworten.

**Was dagegen spricht:** Das Gateway tut das schon halb. Zwei Wege zum selben
Ziel sind einer zu viel.

### 5. Ein Monitoring-Reiter statt der Statuszeile

Der Netzwerk-Reiter zeigt jetzt Verkehr. Was fehlt, ist die zweite Hälfte:
**Verfügbarkeit über Zeit** — wie lange lief das Netz, wann stand es, warum.

**Was es bringt:** Die Frage „warum lief das über Nacht nicht" bekommt eine
Antwort, die man nachlesen kann.

### 6. Kleinigkeiten, die viel Wirkung haben

- **Das VLAN beim Namen nennen.** Die Farbe im Analysator als VLAN
  beschriften — kostet nichts, erklärt alles.
- **MTU.** Ein Kabel trägt Stapel bis zu einer Größe; größere werden
  aufgeteilt. Erklärt, warum `rate 64 per 1t` an einem dünnen Kabel anders
  läuft als an einem dichten.
- **Uptime im Kopf.** „läuft seit 3h 12min" neben dem Durchsatz.

## Was ich zuerst bauen würde

**Latenz.** Sie ist die einzige Idee auf der Liste, die etwas Neues *ins
Spiel* bringt statt nur ins Vokabular: Die Form des Netzes wird zu einer
Entscheidung, die man beim Bauen trifft und im Code sieht.

**Danach Halbduplex** — billig und gibt dem dichten Kabel einen zweiten Grund.

**Und die drei Kleinigkeiten** irgendwann nebenher; sie kosten fast nichts.

## Was ich nicht bauen würde

**Kollisionen** (siehe 3) und **DNS** (siehe 4) — beide widersprechen etwas,
das schon steht und funktioniert.
