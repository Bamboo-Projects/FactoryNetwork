# Der Speicherbus

Ein fremdes Inventar wird Teil des Netzspeichers. Gewünscht am 26.08.:
„es fehlt noch was wie ein StorageBus um andere Storage Systeme anzubinden,
also der StorageBus so wie er in AE2 auch ist."

Dieses Dokument ist ein **Entwurf** und kein Bericht. Es sagt, was zu
entscheiden ist, und empfiehlt bei jedem Punkt eine Antwort.

---

## 1. Was er ist, und was er nicht ist

Heute hängt an einer Kiste ein Connector, und dann ist die Kiste ein **Gerät**:
`move 64 item:iron_ore from kiste to ofen`. Ihr Inhalt gehört ihr. Wer wissen
will, was drin liegt, fragt `kiste.items()`.

Ein Speicherbus macht etwas anderes: Der Inhalt gehört ab dann dem **Netz**.

- `storage.count(item:iron_ore)` zählt die Erze in der Kiste mit.
- Ein Auftrag der Fertigung rechnet mit ihnen, ohne dass jemand sie umlagert.
- Ein Worker `to storage` darf in der Kiste landen.
- Im Terminal steht der Bestand zwischen dem der Zellen.

Das ist der ganze Unterschied, und er ist größer, als er klingt: Bis heute ist
`NetworkStorage` eine **eigene Wahrheit** — die Zellen im Laufwerk, von dieser
Mod geschrieben und gelesen. Danach ist er eine **Sicht** auf Dinge, die sich
ohne das Netz ändern. Ein Spieler räumt die Kiste aus, ein Trichter füllt sie,
eine fremde Maschine leert sie im selben Tick.

**Das ist der Kern jeder Entscheidung weiter unten.**

---

## 2. Die Form: Block oder Deklaration?

Hier liegt die eine Frage, die der Projektinhaber entscheiden sollte. Beide
Antworten sind vertretbar, und beide haben im Projekt einen Präzedenzfall.

### Form A — ein eigener Block

Wie in AE2: Der Speicherbus ist ein Block, der an die Kiste kommt, und er hat
ein Fenster mit Filterfächern und einer Priorität.

**Dafür:** Es ist die Form, die jeder kennt, der AE2 gespielt hat. Sie ist im
Spiel sichtbar — man sieht an der Wand, welche Kiste zum Netz gehört, ohne ins
Programm zu sehen.

**Dagegen:** Sie widerspricht einer Regel, die diese Mod bisher zweimal
durchgehalten hat. Ein- und Ausgang trennt der Code und nicht die Bauform
(`entscheidungen.md`, „Ein Connector reicht"), und was eine Maschine kann,
steht im Programm und nicht auf einem Muster-Item (`entscheidungen.md`,
„Processing-Rezepte"). Ein Filter in einem Fenster ist genau das Muster-Item,
das dort abgelehnt wurde: Er ist nicht versionierbar, geht nicht mit der Datei
nach VS Code, und ein Vertipper darin fällt niemandem auf.

### Form B — im Programm erklärt (Empfehlung)

```
store kiste_1 {
    priority 5
    filter tag:c/ores
}
```

Der Connector hängt ohnehin an der Kiste; die Zeile sagt, dass ihr Inhalt zum
Netz zählt.

**Dafür:** Dieselbe Haltung wie bei `recipe … at …`, mit denselben drei
Folgen — versionierbar, aus der Ferne lesbar, und ein Gerätename, den es nicht
gibt, meldet sich beim Übernehmen. Filter und Priorität sind Sprache und kein
Fenster; `filter` gibt es schon, samt Vorlagen und `except`. Kein neuer Block,
kein neues Rezept, keine neue Textur.

**Dagegen:** Man sieht der Wand nicht an, welche Kiste dazugehört. Das ließe
sich mit einer Anzeige am Connector lösen (er zeigt seinen Zustand ohnehin),
ist aber Mehrarbeit.

**Empfehlung: Form B.** Sie folgt dem, was das Projekt schon zweimal
entschieden hat, und sie kann von Anfang an mehr — ein Filter mit Tags,
Platzhaltern und `except` ist im Fenster eines Blocks kaum unterzubringen.

> **Ein Schlüsselwort-Hinweis:** `storage` ist als Wort vergeben — es meint
> den Netzspeicher als Ziel. Die Deklaration braucht ein anderes; `store`
> steht oben als Vorschlag, `attach` oder `extend` wären Alternativen.

---

## 3. Durchgereicht oder gespiegelt?

**Durchgereicht** heißt: Jede Frage an den Netzspeicher liest die fremden
Inventare. **Gespiegelt** heißt: Das Netz führt eine Kopie und gleicht sie
regelmäßig ab.

AE2 reicht durch, und das ist auch hier die richtige Antwort — aus einem
Grund, der schwerer wiegt als die Kosten: Eine Kopie ist **falsch**, sobald
jemand die Kiste anfasst. Ein Auftrag, der auf gespiegelte acht Bretter
rechnet, die ein Spieler gerade herausgenommen hat, hinterlässt genau den
halben Stapel Zwischenzeug, den `entscheidungen.md` bei der Fertigung
ausdrücklich verhindert.

**Die Kosten sind zu messen, nicht zu schätzen.** `storage.count(…)` läuft
heute über eine Karte im Speicher. Mit N Bussen läuft es über N Inventare, und
das je Frage — ein Worker fragt je Tick, eine Anzeige auch. Dagegen steht das
Schrittbudget aus Punkt 1.12: Es gibt schon eine Grenze für Netzarbeit je
Tick, und ein Speicherbus gehört hinein.

**Vorschlag:** Durchgereicht, mit einer Zwischenschicht, die je Tick **einmal**
liest und die Antwort für diesen Tick behält. Damit kostet ein Tick höchstens
N Inventarlesungen, egal wie viele Worker fragen, und die Antwort ist nie
älter als ein Tick.

---

## 4. Was zu klären ist, bevor eine Zeile Code entsteht

### Die Artenplätze

Eine Zelle hat zwei Grenzen: Menge und Zahl der Arten. Beide stehen im
Tooltip, und der Balken zeigt die knappere. Eine Kiste hat keine Artengrenze,
sondern 27 Fächer.

`freeTypes()` und `distinctTypes()` beantworten heute eine Frage über Zellen.
Mit einem Bus daneben ist unklar, was sie überhaupt bedeuten. **Vorschlag:**
Sie bleiben eine Auskunft über die Zellen, und der Bus wird getrennt gezählt.
Das Terminal zeigt beides untereinander statt einer Zahl, die keines von
beidem stimmt.

### Was beim Speichern passiert

Der Inhalt eines Busses darf **nicht** in die Zellen wandern. Er liegt in der
Kiste, und die speichert Minecraft selbst. Ein Fehler hier verdoppelt
Gegenstände beim ersten Neustart — die teuerste Sorte Fehler, die eine
Speichermod haben kann.

### Wer merkt, dass sich etwas geändert hat

`setChangeListener` weckt heute die Anzeigen, wenn sich der Speicher ändert.
Eine fremde Kiste meldet nichts. **Vorschlag:** Der Bus vergleicht beim
Tick-Lesen (Abschnitt 3) und meldet, wenn sich etwas geändert hat. Das ist
derselbe Merker wie beim Statusdatei-Schreiben und kostet nichts extra.

### Die Reihenfolge beim Einlagern

Wohin geht ein Gegenstand, wenn beides Platz hat — Zelle oder Bus? Dafür ist
die `priority` da. **Vorschlag:** Zellen haben Priorität 0, ein Bus ohne
Angabe ebenfalls, und bei Gleichstand gewinnen die Zellen. Wer Erz in eine
Kiste will und nicht in die Zelle, schreibt `priority 10`.

### Was beim Abhängen passiert

Nichts. Der Inhalt bleibt in der Kiste und ist ab dann nicht mehr im Netz —
kein Umlagern, kein Verlust. Das ist die einzige Antwort, die nichts
verschwinden lässt.

---

## 5. Wie er in Schnitte zerfällt

1. **Lesen.** Ein Bus, kein Filter, keine Priorität. `storage.count(…)` und
   `contents()` sehen die Kiste. Das Terminal zeigt sie.
2. **Schreiben.** `insert` darf in der Kiste landen, `extract` daraus holen.
   Reihenfolge zwischen Zellen und Bus.
3. **Filter und Priorität.** Erst hier wird die Deklaration mehr als ein Name.
4. **Mehrere Busse**, und das Tick-Lesen aus Abschnitt 3 mit Messung gegen das
   Schrittbudget.

Jeder Schnitt ist für sich brauchbar und prüfbar. Der erste allein beantwortet
schon die häufigste Frage: „Warum sieht mein Netz die Kiste nicht?"

---

## 6. Verwandtschaft zu Punkt 7.7

Ein ME-Netz anzubinden (Punkt 7.7, Applied Energistics) ist **derselbe
Mechanismus mit einer anderen Quelle**: Statt eines `IItemHandler` steht dahinter
AE2s eigene Speicherschnittstelle. Wer Abschnitt 3 sauber baut — eine
Zwischenschicht, die Bestand liest und Einlagern annimmt —, bekommt 7.7
größtenteils geschenkt.

Umgekehrt gilt: Wer den Bus fest an `IItemHandler` schreibt, baut 7.7 später
ein zweites Mal.
