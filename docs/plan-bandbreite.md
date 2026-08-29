# Bandbreite in Byte — Umsetzungsplan

**Auftrag:** „dann machen wir das richtig mit byte und bit, dann halten wir
uns da ans reallife. also Channels komplett raus" (29.08.)

**Ziel:** Der Durchsatz heißt nicht mehr „64 je Tick", sondern **1 KB/s**. Die
Einheit ist echt, die Rechnung nachvollziehbar, und wer Netzwerk kennt, liest
sie ohne Erklärung.

---

## Warum eine echte Einheit besser ist als eine Zahl

**„64 je Tick" ist eine Zahl ohne Anker.** Ist das viel? Wofür reicht es? Man
lernt es durch Ausprobieren.

**„1 KB/s" trägt sein Gefühl mit.** Jeder weiß, dass das langsam ist und dass
64 KB/s achtmal mehr sind. Die Mod muss nichts erklären, was die Welt schon
erklärt hat.

**Und es passt zur Mod.** Hier wird programmiert; ein Netz, das in Byte
rechnet, ist dieselbe Sprache wie der Code, der darüber läuft.

## Die Umrechnung

**Ein Gegenstand ist ein Byte.** Nicht weil ein Eisenbarren ein Byte wäre,
sondern weil es die einzige Zuordnung ist, die man sich merken kann.

**Ein Tick ist eine Zwanzigstelsekunde.** Also:

| | je Tick | je Sekunde |
|---|---|---|
| Gewöhnliches Kabel | 64 B | **1,28 KB/s** |
| Dichtes Kabel | 512 B | **10,24 KB/s** |

**Krumm — und deshalb werden es glatte Zahlen:**

| | je Tick | je Sekunde |
|---|---|---|
| Gewöhnliches Kabel | 50 B | **1 KB/s** |
| Dichtes Kabel | 500 B | **10 KB/s** |

Ein gewöhnliches Kabel schafft damit knapp einen Stapel je Tick, ein dichtes
knapp acht — dieselbe Größenordnung wie heute, nur mit einer Zahl, die man
aussprechen kann.

**Angezeigt wird immer je Sekunde.** „50 B je Tick" muss man umrechnen; „1
KB/s" nicht. Gerechnet wird weiter je Tick, weil das Spiel so läuft.

## Verifizierter Bestand

| Was | Wo |
|---|---|
| Zwei Zahlen für den Durchsatz | `network/Throughput.java` — THIN/DENSE |
| Das Budget je Tick | `network/TickBudget.java` |
| Anzeige im Analysator | `analyser/AnalyserScan.java`, `AnalyserData.Link` |
| Anzeige in Jade | `compat/jade/CableInfo.java`, `RouterInfo.java` |
| **Kanal-Reste im Kabel** | `CableBlock.CHANNELS_THIN/DENSE`, `channelsAt` |
| **Kanal-Rest am Anschluss** | `ConnectorPart.channelCost` — steht im Speicherformat |

**`Throughput.at` liest heute noch `CableBlock.channelsAt`**, um Kabelarten zu
unterscheiden. Das ist der letzte Kanalrest im Rechenweg und fällt hier.

## Die Aufgaben

- [ ] **1. Die Einheit.** `Bandwidth.java` löst `Throughput.java` ab: Werte in
      Byte je Tick, dazu ein Formatierer, der KB/s daraus macht. Prüflauf: 50
      B/Tick sind 1 KB/s.
- [ ] **2. Die Kanal-Reste fallen.** `CHANNELS_THIN/DENSE` und `channelsAt`
      weichen einer Kabelart, die ihre Bandbreite selbst kennt. **Das ist der
      Rest von „Channels komplett raus".**
- [ ] **3. Die Anzeige spricht KB/s.** Analysator und Jade zeigen „0,4 von 1
      KB/s" statt roher Zahlen.

## IPv4 und VLANs — der Befund

**Die Sorge ist berechtigt, aber die Frage ist falsch gestellt: Ein VLAN gibt
es schon.**

`CableColour.connectsTo` trennt heute Netze auf derselben Leitung — zwei
Kabel verschiedener Farbe berühren sich, ohne verbunden zu sein. **Das ist
exakt, was ein VLAN tut.** Es fehlt nur der Name.

**Adressen dagegen gibt es auch schon, und sie sind besser als IPv4.** Ein
Anschluss heißt `ofen_1`. Wer ihn in `192.168.1.7` umbenennt, hat nichts
gewonnen und eine Ebene dazwischengeschoben, die kein Programm braucht.
`move 64 to ofen_1` liest sich; `move 64 to 192.168.1.7` nicht.

**Meine Empfehlung: keine Adressen, aber die Namensgebung schärfen.** Was der
echten Welt nachempfunden werden kann, ohne zu überfordern:

- **Die Farbe im Analysator „VLAN" nennen**, wo ohnehin Netztechnik draufsteht.
  Kostet nichts und erklärt, was die Farbe tut.
- **Ein Anlagenname plus Gerätename ist ein Pfad**: `werk_1/ofen_1`. Das gibt
  es über das Gateway schon.

**Was ich nicht bauen würde:** Subnetze, Routing-Tabellen, DHCP. Das sind
Ebenen für ein Problem, das diese Mod nicht hat — sie verbindet Maschinen, die
sich alle gegenseitig kennen sollen.

**Das bleibt deine Entscheidung**; dieser Plan baut es nicht.
