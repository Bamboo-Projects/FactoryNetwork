---
navigation:
  title: Die Blöcke
  position: 20
---

# Die Blöcke

Ein Netz besteht aus wenigen Bauteilen, und jedes tut genau eine Sache. Was
hier zu jedem Block steht: wofür er da ist, was er kostet, und der eine Satz,
den man wissen muss.

Zwei Kosten gibt es. **Kanäle** sind der Platz auf dem Kabel — jedes Gerät
zieht sich einen auf seinem ganzen Weg zum Controller. **Strom** ist FE je
Tick, den der Controller laufend abgibt, damit das Netz überhaupt bereit ist.
Beides steht ausführlich unter *Kanäle und Strom*.

## Controller

Die Wurzel. Er hält das Programm, den Index des Speichers, die wartenden
Abläufe und den Stromvorrat. **Es gibt genau einen je Netz.** An jeder seiner
sechs Seiten hängt ein Kabelstrang.

Strom kommt von außen hinein: Er nimmt Forge Energy an, puffert 20 000 FE und
nimmt bis zu 2 000 FE je Tick auf. Ein Rechtsklick nennt, wie viele
Connectoren und Kabel er kennt, wie viele davon keinen Namen haben und wie
viele ohne freien Kanal dastehen.

*Kostet: keinen Kanal, 4 FE/t.*

**Er allein reicht nicht.** Lagern kann das Netz erst mit einem Laufwerk,
rechnen erst mit einem Serverschrank.

## Controller-Anbau

Mehr Seiten für Kabel, und sonst nichts. Sechs Seiten am Controller, je ein
Strang, ein dichtes Kabel mit 64 Kanälen — das sind 384 Geräte. Wer mehr
braucht, setzt Anbauten daneben; jeder bringt sechs eigene Seiten mit, und an
jede kommt ein Strang.

**Er muss den Controller berühren** — unmittelbar oder über andere Anbauten.
Ein Anbau am Ende eines Kabels tut nichts: Sonst wäre er ein Kanalvermehrer
zum Hinstellen, und die Kanalgrenze bedeutete nichts mehr.

Er hält nichts: kein Programm, keinen Speicher, keinen Strom. Der Controller
bleibt genau einer je Netz.

*Kostet: keinen Kanal, 1 FE/t.*

## Terminal

Hier schreibst du den Code und siehst, was das Netz tut. Es muss **direkt an
den Controller** — es sucht ihn in der Nachbarschaft, nicht über das Kabel.

Sechs Reiter, in dieser Reihenfolge: **Speicher** für den Bestand,
**Fertigung** für die Aufträge, **Code** für das Programm, **Netz** für Strom,
Worker, Abläufe und globale Werte, **Anzeigen** für die Displays und **Log**
für die Meldungen. Alle sechs tun etwas.

*Kostet: keinen Kanal, keinen Strom.*

**Was du tippst, gilt erst nach Strg+Eingabe** — und wenn der Übersetzer
ablehnt, läuft das alte Programm einfach weiter. Ein Tippfehler hält die
Fabrik nicht an.

## Kabel

Sie tragen keine Gegenstände, sondern Zuständigkeit: Was am Kabel hängt,
gehört zum Netz. Zwei Stärken:

- **Kabel** — sechs Blockpixel dick, **16 Kanäle**.
- **Dichtes Kabel** — zehn Blockpixel dick, **64 Kanäle**.

Dazu jede der sechzehn Farben, je einmal dünn und einmal dicht. Gefärbt wird
mit einem Farbstoff auf einem beliebigen Kabel, entfärbt mit einem
Wassereimer.

*Kostet: keinen Kanal, keinen Strom.*

**Die Farbe entscheidet, was sich verbindet.** Zwei Kabel verschiedener Farbe
laufen aneinander vorbei, ohne sich zu sehen; die neutrale Farbe verbindet
sich mit allem. So gehen mehrere Netze durch dieselbe Wand.

## Connector

Er gibt der Maschine dahinter einen Namen im Netz. Seine Vorderseite zeigt
dorthin, wo du beim Setzen hingeklickt hast — und **an genau dieser Seite**
muss die Maschine annehmen, was du ihr schicken willst.

Benannt wird er mit der Beschriftungspistole oder per Rechtsklick mit leerer
Hand. Er gibt außerdem Redstone nach allen Seiten aus, wenn das Programm es
verlangt: `alarm.redstone(15)`.

*Kostet: 1 Kanal, 1 FE/t.*

**Ein Connector ohne Namen hängt im Netz, kostet seinen Kanal und ist im Code
nicht ansprechbar.** Erst der Name macht ihn zu etwas, worüber sich reden
lässt.

## Laufwerk

Der Lagerraum des Netzes: zehn Plätze für Zellen. Rechtsklick öffnet das
Regalfenster; die Plätze liegen darin so, wie sie an der Front sitzen, und an
der Front sieht man ohne Anklicken, was steckt. Beim Abbauen fallen die Zellen
heraus.

Vier Arten von Zellen, jede in vier Größen:

- **Speicherzellen** für Gegenstände: 1k, 4k, 16k, 64k.
- **Flüssigkeitszellen** zu 64, 256, 1024 und 4096 Eimern.
- **Chemikalienzellen** für Mekanisms Gase — dieselben vier Größen wie die
  Flüssigkeitszellen. Ohne Mekanism gibt es sie nicht.
- **Energiezellen**, die den Stromvorrat des Netzes vergrößern: 64k bis 4096k
  FE. Sie haben keine Sortengrenze — es gibt nur FE.

*Kostet: 1 Kanal, 1 FE/t — und je eingesetzter Zelle noch einmal 1 FE/t.*

**Ohne Zelle lagert das Netz nichts.** Eine Zelle hat zwei Grenzen: wie viele
Arten sie führt und wie viel Menge sie fasst. Meist ist die erste die knappe.
Ein zweites Laufwerk daneben vergrößert den Speicher wie ein zweites Regal;
alle Laufwerke im Netz zählen zusammen.

## Serverschrank

Die Rechenleistung. Er ist **zwei Blöcke hoch** und hat zwölf Einschübe. In
jeden Einschub gehört ein Servergehäuse, und in das Gehäuse drei Bauteile:

- **Rechenwerk** (2 / 8 / 32 / 128) — wie viele Abläufe gleichzeitig laufen.
- **Speicher** (8 / 32 / 128 / 512) — wie viele Abläufe überhaupt bestehen.
- **Datenträger** (64 / 256 / 1024 / 4096) — wie groß das Programm sein darf,
  gezählt in Anweisungen. Kommentare und lange Namen kosten nichts.

*Kostet: 1 Kanal, 1 FE/t — und je laufendem Einschub noch einmal 2 FE/t.*

**Erst alle drei Bauteile ergeben einen Server**, und ein Einschub mit zweien
trägt nichts — nicht anteilig, gar nichts. Er steht dann gelb an der Front.
Ohne einen einzigen laufenden Server nimmt das Netz kein Programm an, und
kein Worker rührt sich.

## Router

Die Kreuzung für dichte Kabel. Er hat **vier Bahnen**, und jede seiner sechs
Seiten liegt auf einer davon. Gleiche Bahn heißt verbunden, verschiedene
Bahnen kreuzen sich berührungslos, „aus" heißt abgeklemmt. Eine Seite
anklicken schaltet sie weiter; Schleichen öffnet ein Fenster mit allen sechs
auf einmal. Jede Bahn trägt so viel wie ein dichtes Kabel, also 64 Kanäle.

*Kostet: keinen Kanal, 1 FE/t.*

**Er ist farbneutral.** Wer ein rotes und ein grünes Kabel auf dieselbe Bahn
legt, hat sie verbunden — das ist kein Versehen, sondern der Unterschied zu
zwei Kabeln, die sich bloß einen Block teilen.

## Display

Flach an der Wand, wie ein Bilderrahmen. Es zeigt den `display`-Block aus dem
Programm, der genauso heißt wie die Tafel.

Tafeln, die **in dieselbe Richtung zeigen und sich in ihrer Ebene berühren**,
sind zusammen eine Wand mit einem einzigen Text; geschrieben wird von der
Tafel unten links. Über Ecken gilt das nicht — eine Nordwand und eine Ostwand
sind zwei Bildschirme. Die Beschriftungspistole benennt immer die ganze Wand.

*Kostet: keinen Kanal, 1 FE/t.*

**Die Schrift wächst nicht mit der Wand.** Mehr Fläche heißt mehr Zeilen und
längere Zeilen, nicht größere Buchstaben.

## Brennkammer

Strom aus gewöhnlichem Ofenbrennstoff: **40 FE je Tick**, kein Ausbau, keine
Stufen. Eine Kohle trägt ein kleines Netz gut eine Minute. Sie schiebt ihren
Strom aktiv in den Nachbarn — der Controller nimmt an und zieht nicht von
selbst. Nachgelegt wird nur, wenn jemand abnimmt.

**Sie ist absichtlich mittelmäßig.** Sie ist dafür da, dass du nicht blockiert
bist, nicht dafür, mit den Generatoren deines Packs zu konkurrieren. Sobald
etwas Besseres steht, stell es daneben.

Zum Prüfen einer Anlage gibt es daneben die **Kreativ-Stromquelle**: kein
Rezept, nur im Kreativ-Reiter, dafür kein Nachlegen.

## Presse

Der Einstieg in die Fertigungskette — aus Kristall und Platten werden die
Kerne, Zellen und Serverbauteile. Drei Plätze: **Stempel, Material,
Ausgabe**. Der Stempel bleibt liegen und wird wiederverwendet, verbraucht wird
nur das Material.

Sie hängt **nicht** am Netz, sondern nimmt Forge Energy direkt an, wie jede
Maschine im Pack: 40 000 FE Puffer, bis 2 000 FE je Tick. Die Brennkammer
reicht ihr.

**Ohne Strom passiert nichts, und sie sagt es auch** — in ihrer Oberfläche und
im Tooltip. Eine Maschine, die stumm stehen bleibt, schickt einen sonst auf
die Suche nach dem falschen Fehler.
