# Der Controller-Multiblock — Entscheidungsvorlage

**Gewünscht am 28.08.:** „wie sieht es aus mit dem controller multiblock?
sonst reichen ja die channel niemals" — und auf die Rückfrage: „das ist ja
klar das der controller keine begrenzungen hat aber das Kabel. ich kann doch
nur 64 je seite. ich bekomme doch nicht mehr an eine seite einfach ran"

Dieses Dokument misst, was heute dasteht, und legt drei Bauformen vor. Es
baut nichts.

---

## 1. Was heute dasteht — gemessen, nicht geschätzt

**Die Kanalgrenze sitzt am Kabel, nicht am Controller.** Ein gewöhnliches
Kabel trägt 16 Kanäle, ein dichtes 64 (`CableBlock.CHANNELS_THIN/DENSE`). Der
Controller selbst gilt als unbegrenzt: `capacityAt` gibt für alles, was kein
Kabel ist, `Integer.MAX_VALUE`.

**Der Einwand trifft trotzdem.** Sechs Seiten mal 64 sind 384 Kanäle, und an
*eine* Seite kommt nicht mehr als ein Kabel. Wer an einer Stelle mehr braucht,
muss den Controller vergrößern — nicht seine Grenze anheben.

**Und dafür gibt es den Anbau schon.** `ControllerExtensionBlock` ist bereits
ein Multiblock, nur heißt er nicht so:

- Er wächst vom Controller aus per Suchlauf über die Nachbarschaft
  (`FactoryGraph:400-418`) — jeder Anbau, der einen Anbau berührt, gehört
  dazu, in jede Richtung, ohne feste Form.
- Er kostet Strom je Block (`Power.EXTENSION`).
- Er bietet Kabelseiten. **Das tat er bis zum 28.08. nur halb:** Ein Kabel
  wuchs ihm keinen Arm entgegen, weil er in `CableBlock.connects` fehlte. Seit
  heute ist er dort — vorher sah eine angeschlossene Seite aus wie eine leere.

**Was ein Anbau bringt, in Zahlen:** Er hat sechs Seiten, davon mindestens
eine zum Controller oder zum nächsten Anbau. Bleiben bis zu fünf Kabelseiten,
also **bis zu 320 Kanäle je Anbau**. Ein Würfel aus acht Anbauten um den
Controller liegt jenseits von zweitausend.

---

## 2. Die eigentliche Frage

Nicht „reichen die Kanäle", sondern: **Fühlt sich das an wie ein Multiblock?**

Heute ist der Anbau ein Block, den man danebenstellt. Es gibt kein Bauwerk,
das zusammenwächst, keine Rückmeldung „fertig", keinen Fortschritt, den man
sieht. Bei AE2 ist der Controller-Multiblock genau das: etwas, das man baut
und danach ansieht.

Drei Formen, wie das gehen könnte:

### Weg A: Der Anbau bleibt, bekommt aber ein Gesicht

Kein neuer Block. Der Anbau erkennt seine Nachbarn und zeigt es: durchgehende
Kanten, ein gemeinsames Leuchten, eine Naht, die an den Berührungsflächen
verschwindet. Dazu eine Zeile im Analysator: „Controller mit 6 Anbauten, 1920
Kanäle an 30 Seiten."

**Kosten:** Blockstates für die Nachbarschaft, Modelle je Fall, keine neue
Mechanik. **Was es nicht gibt:** einen Moment, in dem etwas fertig wird.

### Weg B: Eine Form, die stimmen muss

Wie AE2: Der Multiblock gilt erst, wenn die Blöcke eine erlaubte Form bilden
— ein Würfel, ein Quader in Grenzen. Vorher tut er nichts, danach leuchtet er.

**Kosten:** Eine Formprüfung samt Fehlermeldung („zu hoch", „Loch in der
Mitte"), ein Zustand „gültig", und die Frage, was mit einem Bauwerk geschieht,
dem jemand einen Block herausschlägt. **Was es bringt:** genau den Moment, den
Weg A nicht hat.

### Weg C: Kanäle statt Seiten

Der Anbau bietet keine Kabelseiten mehr, sondern hebt eine Zahl: Jeder Block
gibt dem Netz zusätzliche Kanäle, verteilbar über beliebige Seiten.

**Das bricht mit unserem Modell.** Bei uns trägt die *Leitung* die Kanäle, und
das ist der Grund, warum ein dichtes Kabel etwas wert ist. Wer die Zahl an den
Controller hängt, macht das dichte Kabel zur Verzierung. **Nicht empfohlen** —
aufgeführt, weil es AE2s Modell am nächsten kommt und die Frage sonst
offenbliebe.

---

## 3. Was ich empfehle

**Weg A zuerst**, und zwar bald: Er kostet nichts an Mechanik und behebt das,
was der Einwand wirklich trifft — dass man dem Bauwerk nicht ansieht, was es
kann. Ein Analysator, der „1920 Kanäle an 30 Seiten" sagt, beantwortet die
Frage „reicht das" endgültig.

**Weg B danach, wenn es ihn braucht.** Er ist Gameplay, keine Notwendigkeit;
die Kanäle reichen mit A. Und er hat einen Preis, den man erst im Spiel
merkt: Ein Bauwerk, das erst ab einer Form gilt, ist ein Bauwerk, das man
falsch bauen kann.

**Offen für dich:** Ob dir der Moment „jetzt ist es fertig" wichtig ist. Das
ist die ganze Frage zwischen A und B, und sie ist keine technische.
