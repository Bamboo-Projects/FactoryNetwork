---
navigation:
  title: Anzeigen
  position: 55
---

# Anzeigen

Eine Fabrik, die nicht sagt, was sie tut, ist eine Blackbox mit Kolben. Ein
`display` ist die Antwort darauf — im Terminal und an der Wand.

```
display leitstand {
    title "Erzlinie"
    row "Eisenerz" storage.count(item:iron_ore)
    progress "Erzvorrat" storage.count(item:iron_ore) / 640.0
    indicator "Brecher unter Strom" brecher.redstone() > 0
    button "Nachschub" nachschub_starten
}

fn nachschub_starten() {
    move 64 item:iron_ore from storage to brecher
}
```

Diese Anzeige erscheint an **jeder Wand, die `leitstand` heißt**, und im
Reiter *Anzeigen* des Terminals. Der Knopf wirkt an beiden Stellen.

## Die Bausteine

| | |
|---|---|
| `title "…"` | Eine Überschrift |
| `row "…" wert` | Beschriftung und Wert nebeneinander |
| `text wert` | Nur der Wert, ohne Beschriftung |
| `progress "…" anteil` | Ein Balken aus zehn Blöcken, daneben der Anteil in Prozent |
| `indicator "…" bedingung` | Ein Lämpchen, hell oder dunkel |
| `list "…" bestand` | Eine Aufzählung: Überschrift und je Posten eine Zeile |
| `button "…" funktion` | Ein Knopf, der eine Funktion startet |
| `scale zahl` | Wie groß die Schrift ist; 1 ist normal |

Ein Knopf darf etwas anstoßen, das wartet: Die Funktion dahinter wird ein
Ablauf wie jeder andere und übersteht auch einen Serverneustart.

## Große Schrift

Eine Wand aus zwölf Tafeln gibt dir **mehr Zeilen**, keine größeren
Buchstaben. Das ist Absicht: Eine Wand, deren Text von selbst mitwächst, ist
aus drei Metern genauso lesbar wie eine einzelne Tafel — der ganze Vorteil
wäre weg.

Wenn du eine Überschrift willst, die man von weitem liest, sag es:

```
display halle {
    scale 4
    title "ERZLAGER"
}
```

Viermal so groß, ein Viertel der Zeilen — und aus viermal so weit sichtbar,
denn die Entfernung, ab der eine Tafel nicht mehr gezeichnet wird, wächst mit.
Erlaubt ist 1 bis 8.

Die Größe gilt für die **ganze Tafel**. Eine große Überschrift über kleinen
Zeilen bekommst du nicht in einem Block, sondern mit zwei Wänden und einer
Lücke dazwischen: Tafeln, die sich berühren, sind eine Wand.

## Was auf einer Anzeige ausgewertet wird

**Eine Anzeige liest ab und rechnet damit — mehr nicht.** Ablesbar sind:

- `storage.count(…)` — der Bestand im Netz
- `gerät.redstone()` — das Signal an einem Connector
- `gerät.online` — hängt es im Netz?
- ein Workername oder `worker.status` — was er gerade tut
- ein globaler Wert

Damit darf gerechnet und verglichen werden: `storage.count(item:coal) / 640.0`
ergibt einen Anteil für den Balken, `depot.redstone() > 0` ein Lämpchen, und
`modus == "tag"` prüft auch einen Text.

`gerät.count(…)` steht ebenfalls dort — eine Tafel liest auch aus einer
einzelnen Maschine, mit Auswahl oder ohne:

```
display leitstand {
    row "Im Brecher" brecher.count()
    row "Erz im Brecher" brecher.count(tag:c/ores)
}
```

Was darüber hinausgeht — ein Funktionsaufruf, eine Schleife —, erscheint als
`?` auf der Tafel. Eine leere Stelle wäre schlimmer: Dann sucht man den Fehler
im Netz statt im Programm.

## Der Balken braucht seine Obergrenze

`progress` will eine Zahl zwischen 0 und 1. Ein nackter Bestand hat keine
Obergrenze, deshalb gilt dort ein voller Stapel als voll — für alles andere
teilst du selbst:

```
display halle {
    progress "Kohle" storage.count(item:coal) / 640.0
}
```

**Der Punkt in `640.0` ist nötig.** Ohne ihn wird ganzzahlig gerechnet, und
bei 320 von 640 käme 0 heraus statt 0,5.

## Wenn die Tafel schwarz bleibt

Fast immer heißt sie anders, als im Programm steht. Der Reiter *Anzeigen*
führt deshalb auch die Tafeln auf, die in der Welt hängen und zu denen es
keinen `display`-Block gibt — samt denen ganz ohne Namen. Und der Editor
schlägt hinter `display ` nur Namen vor, die es wirklich gibt.

Gerechnet wird höchstens einmal je Sekunde. Eine Wand aus zwanzig Tafeln ist
deshalb kein Problem.
