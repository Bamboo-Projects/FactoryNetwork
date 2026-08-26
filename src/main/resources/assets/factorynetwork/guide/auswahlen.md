---
navigation:
  title: Auswahlen
  position: 33
---

# Auswahlen

Was ein `filter`, ein `move` oder ein `count` meint: eine Art, ein ganzer Tag,
ein Muster — oder alles.

## Die vier Arten

```
item:coal                      ein Gegenstand
tag:c/ores                     alles, was in diesem Tag steht
fluid:water                    eine Flüssigkeit
fluidtag:c/molten              alles in einem Flüssigkeits-Tag
chemical:mekanism/hydrogen     eine Chemikalie (braucht Mekanism)
```

Der **Doppelpunkt** trennt die Art, der **Schrägstrich** den Namensraum. Fehlt
der Namensraum, ist `minecraft` gemeint — bei `chemical:` dagegen `mekanism`,
denn Chemikalien gibt es in Minecraft nicht.

`tag:` sucht nur unter Gegenständen. Für Flüssigkeiten gibt es `fluidtag:`,
und das ist Absicht: Ein Tag, der beides durchsuchte, träfe je nach Pack mal
das eine und mal das andere.

## Aus JEI abschreiben geht

```
item:mekanism:steel_ingot      geht
item:mekanism/steel_ingot      dasselbe
```

JEI zeigt Kennungen mit Doppelpunkt. Wer sie herüberkopiert, soll nicht erst
ein Zeichen tauschen müssen.

## Muster

```
item:*_ingot          jeder Barren, aus jeder Mod
item:mekanism/*       alles von Mekanism
tag:c/*_ores          jeder Erz-Tag
```

Ein `*` steht für beliebig viele Zeichen und darf mehrfach vorkommen. **Ohne
Namensraum sucht ein Muster über alle** — `item:*_ingot` findet auch die
Barren aus fremden Mods. Eine Auswahl ohne Muster bleibt dagegen bei
`minecraft`.

## Ausnehmen

```
filter tag:c/ores except item:cobblestone
filter tag:c/ores except item:cobblestone except tag:c/ores/quartz
```

`except` nimmt heraus, was sonst mitkäme. Mehrere hintereinander gehen.

## Ein Name für eine Auswahl

Wer dieselbe Auswahl an mehreren Stellen braucht, gibt ihr einen Namen:

```
filter erze {
    tag:c/ores
    except item:cobblestone
}

worker mahlen {
    from grube
    to brecher
    filter erze
}

fn nachschub() {
    move 64 erze from storage to brecher
}
```

Eine **Vorlage** steht überall, wo eine Auswahl steht. Ändert sich, was
ausgenommen wird, ändert es sich an einer Stelle.

Sie verdeckt ein Gerät gleichen Namens — der Editor warnt davor. Gerätenamen
kommen aus der Beschriftungspistole, Vorlagennamen aus dem Programm; hinge die
Bedeutung eines Programms daran, wie jemand später einen Connector benennt,
wäre es aus der Ferne nicht mehr zu lesen.

## `all` und `power`

```
move all from brecher to storage      alles, was drin ist
worker strom { filter power … }        Energie
```

Beide stehen **allein**, ohne Doppelpunkt: Sie meinen keine Sorte. `all` meint
Gegenstände — Flüssigkeiten bleiben ausdrücklich, sonst zöge ein
Aufräumbefehl den Tank mit leer.

## Mengen

```
move 64 item:iron_ore from grube to storage      64 Stück insgesamt
move 64 erze from storage to brecher             dasselbe mit einer Vorlage
move 1000 fluid:water from bottich to kessel     1000 mB, also ein Eimer
```

Die Zahl steht **vorn** und meint die Gesamtmenge, nicht je Sorte. Bei
`maintain` ist es umgekehrt: Dort gilt die Zahl je Art.

Flüssigkeiten und Chemikalien zählen in **Millibucket**; ein Eimer sind 1000.

## Nachsehen, was sie trifft

Zeig im Editor mit der Maus auf eine Auswahl. Im Kasten stehen die Zahl der
Arten und die ersten Namen — und wenn sie nichts trifft, steht das in Rot da.

Das ist der Fehler, den man sonst am längsten sucht: Ein Tag, den dieses Pack
nicht kennt, sieht aus wie jeder andere. Er übersetzt fehlerfrei, und der
Worker bewegt für immer nichts.
