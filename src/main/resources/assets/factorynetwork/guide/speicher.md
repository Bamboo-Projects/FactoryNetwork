---
navigation:
  title: Speicher
  position: 60
---

# Speicher

Das Netz lagert nichts von selbst. Es lagert in **Zellen**, und die stecken in
**Laufwerken**. Der Bestand gehört dabei der Zelle und nicht dem Netz — das ist
der Grund, warum man eine volle Zelle einstecken und mitnehmen kann.

## Zwei Grenzen je Zelle

Eine Zelle ist kein großer Sack. Sie hat zwei Grenzen, und beide stehen offen
da: wie viele **Arten** sie führt und wie viele **Gegenstände** sie fasst.

- **1k** — 8 Arten, 8.000 Gegenstände
- **4k** — 16 Arten, 32.000 Gegenstände
- **16k** — 32 Arten, 128.000 Gegenstände
- **64k** — 64 Arten, 512.000 Gegenstände

**Die Arten sind das Knappe.** Wer alles in eine Zelle wirft, hat sie voll,
lange bevor die Menge erreicht ist: Vierzig Sorten Kleinkram aus einer
Kramkiste sprengen eine 16k, in der keine zehntausend Gegenstände liegen.
Genau das treibt zum Sortieren — eine Zelle für Erze, eine für Barren, eine
für den Rest.

Andere Mods rechnen dafür in Bytes. Dann steht eine Zelle früher voll da, als
ihr Name vermuten lässt, und niemand kann erklären, warum. Hier sind es zwei
Zahlen, und beide stehen im Tooltip der Zelle: *3 von 16 Arten* und *12.480 von
32.000 Gegenständen*.

**Der Balken am Gegenstand zeigt die knappere von beiden.** Sonst sähe eine
Zelle mit allen Artenplätzen belegt halb leer aus, obwohl nichts Neues mehr
hineingeht.

## Flüssigkeitszellen

Dieselbe Rechnung, andere Zahlen: Flüssigkeiten gibt es in weniger Sorten und
größeren Mengen.

- **64 Eimer** — 4 Sorten
- **256 Eimer** — 8 Sorten
- **1024 Eimer** — 16 Sorten
- **4096 Eimer** — 32 Sorten

Vier Sorten in der kleinsten reichen für Wasser, Lava und zwei aus dem Pack;
vierundsechzig wären ein Platz, den nie jemand füllt.

Auf der Zelle steht die Zahl in **Eimern**, gezählt wird im Terminal und im
Programm in **Millibucket** — ein Eimer sind 1000. Eine Flüssigkeitszelle geht
in dasselbe Laufwerk wie eine Speicherzelle. Getrennt werden die beiden erst
beim Zählen: Gegenstände füllen keine Flüssigkeitszelle und umgekehrt.

Wie sie bewegt werden — Worker, Tanks, Mengen in Millibucket —, steht unter
*Flüssigkeiten und Gase*.

## Chemikalienzellen

Nur mit **Mekanism** — ohne die Mod gibt es die Zellen zwar, aber es geht
nichts hinein, und der Tooltip sagt es.

Dieselbe Rechnung, wieder andere Zahlen:

- **64k** — 4 Sorten, 64.000 mB
- **256k** — 8 Sorten, 256.000 mB
- **1024k** — 16 Sorten, 1.024.000 mB
- **4096k** — 32 Sorten, 4.096.000 mB

Gemeint sind Mekanisms Gase, Schlämme, Pigmente und Infusionen — seit
Mekanism 10.7 sind das alles **Chemikalien** und nicht mehr vier getrennte
Arten. Im Programm heißen sie `chemical:mekanism/hydrogen`; ohne Namensraum
ist `mekanism` gemeint, denn Chemikalien gibt es in Minecraft nicht.

Sie gehen in dasselbe Laufwerk wie die anderen Zellen.

Bewegt werden sie wie Flüssigkeiten — mit einem Worker:

```
worker wasserstoff {
    from elektrolyseur
    to storage
    filter chemical:mekanism/hydrogen
}
```

Ein `filter` ist dabei Pflicht: Ein Behälter hält meist genau eine Sorte, und
die falsche zu ziehen ist teurer als bei Gegenständen.

**Beim Anschluss zählt die Seite.** Eine Mekanism-Maschine hat eine
Seitenkonfiguration, und das Netz hält sich daran: Der Connector muss an einer
Seite hängen, die etwas herausgibt oder annimmt. Steht dort „nichts", passiert
nichts — und das ist deine Einstellung, nicht ein Fehler des Netzes.

## Energiezellen

Die dritte Zellenart, und die einfachste: **Sie hat nur eine Zahl.**

- **64k FE**
- **256k FE**
- **1024k FE**
- **4096k FE**

Bei Strom gibt es keine Sorten, also auch keine zweite Grenze. Damit fehlt hier
der Reiz, der bei den anderen im Sortieren liegt — und das ist kein Mangel,
sondern die Sache selbst: Ein Akku ist eine Zahl. Wer mehr will, steckt eine
größere Zelle ein oder eine zweite dazu.

Was in ihr liegt, gehört zum **Stromvorrat des Netzes** und nicht zum
Lagerbestand; im Reiter *Speicher* taucht sie deshalb nicht auf. Mehr dazu
steht unter *Kanäle und Strom*.

## Einsetzen und herausnehmen

**Ein Klick öffnet das Regalfenster — immer**, egal was in der Hand liegt, wie
bei einer Kiste. Zehn Plätze, und hinein geht nur, was eine Zelle ist. Im
Fenster liegen die Plätze so, wie sie an der Front sitzen; an der Front sieht
man ohne Anklicken, was steckt.

**Der Bestand reist mit.** Eine Zelle, die das Laufwerk verlässt, nimmt ihren
Inhalt mit — er steht im Gegenstand, nicht im Netz. Eine volle 64k in der
Tasche ist ein Umzugskarton. Und beim Abbauen des Laufwerks fallen alle Zellen
heraus, damit ein versehentlicher Schlag nicht das halbe Lager kostet.

Umgekehrt gilt dasselbe: Wer eine Zelle im Betrieb herauszieht, nimmt dem Netz
ihren Bestand weg. Verloren ist nichts, aber `storage.count(…)` zählt danach
weniger, und ein Worker, der von dort holen wollte, findet nichts mehr.

## Wohin etwas eingelagert wird

Das Netz legt zuerst in Zellen ab, die **diese Art schon führen**, und erst
danach in eine, in der noch ein Artenplatz frei ist.

Ohne diese Regel zersplitterte jeder Bestand über alle Zellen und belegte in
jeder einen Artenplatz — zehn Zellen wären dann nach zehn Sorten voll. Für den
Bau folgt daraus eine Faustregel: Zwei 4k führen zusammen ebenso viele Arten
wie eine 16k, nämlich 32, fassen aber nur halb so viel. Wer viel von wenigem
lagert, kauft Menge; wer wenig von vielem lagert, kauft Arten.

## Der Reiter Speicher

Der erste Reiter im Terminal zeigt den Bestand des ganzen Netzes als Raster.
Die Felder sehen aus wie Fächer, sind aber keine — zwanzigtausend Arten lassen
sich nicht als Fächer anlegen. Die Menge steht deshalb als Text neben dem Bild
und nicht als Stapelzahl: Ein Netzbestand geht weit über vierundsechzig hinaus.

- **Suchen** — das Feld links oben. Solange es tippt, gehören ihm alle Tasten;
  sonst stünde man nach dem „e" in „Eisenerz" wieder in der Welt. Der erste
  Escape verlässt die Suche, der zweite das Terminal.
- **Sortieren** — drei Knöpfe rechts daneben: **#** nach Menge, **A** nach
  Name, **M** nach Mod. Ein Klick auf den aktiven dreht die Richtung um; der
  Pfeil auf dem Knopf sagt, welche gerade gilt.
- **Entnehmen** — Linksklick holt einen Stapel, Rechtsklick einen halben,
  Umschalt+Klick wieder den ganzen. Ist der Mauszeiger schon voll, legt ein
  Klick alles ab und ein Rechtsklick ein einzelnes Stück.
- **Zeigen** nennt die genaue Menge. Im Raster steht sie gekürzt, und der
  Unterschied zwischen drei Eimern und dreitausend Millibucket gehört gesagt.

**Flüssigkeiten lassen sich nur ansehen.** Der Eimer im Raster ist ein Bild;
ihn zu entnehmen hieße, einen Gegenstand aus dem Nichts zu holen.

In der Statuszeile steht, was da ist: `12 Arten · 4,3k Gegenstände`. Zeigen
darauf nennt dazu, wie viele Artenplätze noch frei sind, für Gegenstände und
Flüssigkeiten getrennt. Ist keiner mehr frei, tritt an die Stelle der
Zusammenfassung die Warnung `Kein Platz für neue Arten`.

Was du dabei siehst, ist ein Schnappschuss von vorhin. **Der Server rechnet
jede Entnahme gegen den echten Bestand nach**, und deshalb kann ein Klick auch
einmal weniger holen, als im Feld stand: In der Zwischenzeit war ein Worker
schneller.

## Im Programm

`storage` ist der Netzspeicher, als Ziel wie als Quelle:

```
worker erze {
    from quarry_ausgang
    to storage
    filter tag:c/ores
    when storage.count(tag:c/ores) < 20000
}
```

`storage.count(auswahl)` sagt, wie viel davon im Netz liegt. Eine Auswahl über
mehrere Arten wird **zusammengezählt**: `storage.count(tag:c/ores)` ist die
Summe über alle Erze, nicht die Zahl der Sorten. Bei Flüssigkeiten kommt die
Antwort in Millibucket.

Damit deckelt die letzte Zeile den Vorrat, ohne dass jemand zusieht: Über
zwanzigtausend Erzen hört der Worker auf und steht als `WAITING_CONDITION` da.

`storage.items()` liefert den Bestand als **Liste, je Art ein Eintrag** — in
der Reihenfolge des Speichers, nicht sortiert. Wie viele Arten das Netz führt,
steht damit in einer Zeile, und darauf lässt sich reagieren:

```
fn entlasten() {
    if storage.items().count() > 40 {
        move item:cobblestone from storage to schlucker
    }
}
```

**Was in einer Maschine liegt, sagt dagegen `gerät.items()`** — und nicht
`gerät.count(…)`. Der Gerätename vor `count` ändert nichts daran, dass gezählt
wird, was im Netzspeicher liegt.

Und wenn nichts eingelagert wird, obwohl Platz zu sein scheint: Es gibt zwei
Arten von Platz. Die Menge kann reichen und der Artenplatz trotzdem fehlen.

## Eine Kiste zum Netz machen

Du hast eine Wand voll Drawer oder eine Truhe mit Vorräten, und das Netz soll
sie sehen, ohne dass du alles umlagerst. Dafür ist der **Speicherbus** da —
hier ist er keine Kiste am Block, sondern eine Zeile im Programm:

```
store lager {
    priority 5
    filter tag:c/ores
}
```

`lager` ist der Name des Connectors, der an der Truhe hängt. Ab dieser Zeile
zählt ihr Inhalt zum Netzbestand: Er steht im Terminal, ein Auftrag rechnet
damit, und ein Worker `to storage` darf dort landen.

`filter` sagt, was hinein darf — hier nur Erze. Was schon drinliegt, zählt
trotzdem und lässt sich auch herausholen; der Filter gilt nur für das
Einlagern.

`priority` sagt, wohin zuerst. Deine Zellen stehen auf 0, also landet mit
`priority 5` das Erz in der Truhe, bevor eine Zelle es bekommt. Beides ist
freiwillig — `store lager { }` reicht, wenn die Truhe einfach alles nehmen
soll.

**Der Inhalt bleibt, wo er ist.** Nichts wandert in deine Zellen, nichts geht
verloren, wenn du die Zeile wieder löschst. Die Truhe gehört dann wieder sich
selbst.
