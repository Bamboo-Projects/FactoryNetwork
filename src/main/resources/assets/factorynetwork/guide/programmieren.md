---
navigation:
  title: Programmieren
  position: 30
---

# Programmieren

Die Sprache heißt **Manifold**. Sie steht im Terminal im Reiter **Code**;
**Strg+Eingabe** übernimmt, was du geschrieben hast. Lehnt der Übersetzer ab,
läuft das alte Programm weiter — du kannst also jederzeit etwas ausprobieren.

## Zwei Arten von Code, und der Unterschied ist wichtig

**Was dauerhaft gilt, wird beschrieben. Was einmalig geschieht, wird
programmiert.**

Ein `worker` ist keine Schleife, sondern eine Zusage: *Solange das hier steht,
sorgt das Netz dafür.* Es darf selbst entscheiden, wann es tätig wird — den
Worker schlafen legen, bis sich etwas ändert, oder mehrere Transfers
zusammenfassen. Deshalb steht in einem Worker kein `if` und kein `move`,
sondern nur, was gelten soll:

```
worker mahlen {
    from lager
    to brecher
    filter tag:c/ores
}
```

Eine Funktion dagegen läuft ab. Hier zählt die Reihenfolge, und das Netz führt
aus, was dasteht:

```
fn nachschub_starten() {
    move 64 item:iron_ore from storage to brecher
}
```

Wer einen Dauertransfer als Schleife schreibt, hat einen Worker nachgebaut —
schlechter, weil das Netz dann nicht mehr weiß, was gemeint ist, sondern nur
noch, was zu tun ist.

## Das erste Programm, Zeile für Zeile

In der Welt stehen zwei benannte Connectoren: `kiste` an einer Kiste, `ofen`
an einem Ofen.

```
worker nachschub {
    from kiste
    to ofen
    filter item:coal
    maintain 16
    rate 8 per 20t
}
```

- `worker nachschub` — der Name. Unter ihm steht er im Reiter **Netz** mit
  seinem Zustand. Ohne Namen gibt es keinen Worker.
- `from kiste` und `to ofen` — die einzigen Pflichtangaben. Beide nehmen den
  Namen eines Connectors oder das Wort `storage` für den Netzspeicher.
- `filter item:coal` — was bewegt wird. Ohne `filter`: alles.
- `maintain 16` — im Ofen sollen 16 Stück liegen. Der Worker fährt nach, wenn
  etwas verbraucht ist, und hört auf, wenn es reicht.
- `rate 8 per 20t` — höchstens 8 Stück alle 20 Ticks.

Dann **Strg+Eingabe**. Das Terminal meldet, wie viele Worker jetzt laufen.

## `filter` — was bewegt wird

Der Doppelpunkt trennt die Art, der Schrägstrich den Namensraum. Fehlt der
Schrägstrich, ist `minecraft` gemeint.

```
filter item:coal
filter item:allthemodium/allthemodium_ingot
filter fluid:water
filter tag:c/ores
```

Ein Tag trifft alles, was darin steht — `tag:c/ores` sind in einem großen Pack
ein paar hundert Sorten. Was ausgenommen bleiben soll, steht hinter `except`:

```
filter tag:c/ores except item:cobblestone
```

Flüssigkeiten werden in Millibucket gezählt; ein Eimer sind 1000.

**Zu `maintain` gehört ein `filter`.** „Halte einen Vorrat" ohne die Angabe,
wovon, ist keine Zusage — der Worker hält an und sagt es im Reiter **Netz**.

Und `maintain` gilt **je Gegenstandsart**: `filter tag:c/coals` mit
`maintain 64` hält 64 von jeder Kohleart, nicht 64 insgesamt. Bei `move` ist
es umgekehrt — dort meint eine vorangestellte Zahl einen Stapel, egal welcher
Sorte.

## `rate` — wie schnell

```
rate 8 per 20t
rate 1000 per 10t
```

Das sind 8 Gegenstände alle 20 Ticks **am Stück**, nicht 0,4 je Tick.
Maschinen wollen in aller Regel den Stapel und nicht das Rinnsal. Ohne `rate`
läuft der Worker mit der Voreinstellung.

Zeiten schreiben sich `t` (Ticks), `s`, `min` und `h`.

## `when` — ob überhaupt

`when` schaltet einen Worker an und aus, ohne dass er dafür angefasst wird:

```
global modus = "tag"

worker erz {
    from grube
    to storage
    filter tag:c/ores
    when modus == "tag"
}

fn nachtschicht() {
    modus = "nacht"
}

fn tagschicht() {
    modus = "tag"
}
```

`global` erklärt einen Wert, den alle Dateien sehen. Er überlebt
Serverneustart und Programmwechsel und steht im Reiter **Netz**. Ein Aufruf
von `nachtschicht()` legt den Worker schlafen — ohne dass irgendwo steht, dass
er das tun soll.

Trifft die Bedingung nicht zu, steht der Worker als `WAITING_CONDITION` da.
Im Terminal steht damit nicht nur, *dass* er schläft, sondern *warum*.

## Abläufe, die warten

Das ist der Kern der Sprache: Ein Ablauf hält an und macht später weiter —
auch über einen Serverneustart hinweg.

```
event Fertig(nummer: Int)

fn eine_runde() {
    move 8 item:iron_ore from lager to brecher
    let ergebnis = await Fertig
    move item:iron_dust from brecher to lager
    return ergebnis
}
```

Ausgelöst wird `Fertig` von irgendwoher im Programm:

```
on redstone_changed(gerät, stärke) {
    if stärke >= 12 {
        emit Fertig(1)
    }
}
```

Falls die Maschine nicht antwortet, gehört eine Frist dazu:

```
let ergebnis = await Fertig timeout 30s else {
    log("Der Brecher meldet sich nicht")
    return
}
```

Der `else`-Zweig muss den Ablauf verlassen. Danach steht fest, dass
`ergebnis` einen Wert hat, und niemand muss ihn prüfen.

`move` gibt übrigens zurück, wie viel wirklich bewegt wurde. Weniger als
gewünscht ist normal, `0` auch — die Quelle kann leer und das Ziel voll sein.

## Was ein Gerät kann

Hinter dem Punkt eines Connectornamens steht:

- `online` — hängt es gerade im Netz?
- `name` — der Name, den die Beschriftungspistole vergeben hat.
- `redstone()` — die Stärke, 0 bis 15. Mit einer Zahl in der Klammer wird sie
  gesetzt.
- `count(auswahl)` — wie viel von einer Art im **Netzspeicher** liegt. Am
  `storage` steht dasselbe.
- `insert(auswahl)` — legt aus dem Netzspeicher etwas hinein und meldet, wie
  viel ankam. Weniger ist normal.
- `items()` — was gerade in diesem Gerät liegt. Leere Fächer fallen weg.

Der Editor bietet das nach dem Punkt an, und zeigt hinter jedem Connector
gleich mit, welche Maschine dahintersteht.

## Eine Anzeige, die etwas auslöst

```
display leitstand {
    title "Erzlinie"
    row "Eisenerz" storage.count(item:iron_ore)
    progress "Kohlevorrat" storage.count(item:coal) / 640.0
    indicator "Brecher unter Strom" brecher.redstone() > 0
    button "Nachschub" nachschub_starten
}

fn nachschub_starten() {
    move 64 item:iron_ore from storage to brecher
}
```

`progress` will eine Zahl zwischen 0 und 1 — deshalb die Division. **Der Punkt
in `640.0` ist nötig:** ohne ihn wird ganzzahlig gerechnet, und der Balken
bliebe leer, bis der Vorrat vollständig ist.

Die Anzeige erscheint auf jeder Wand, die `leitstand` heißt, und im Reiter
**Anzeigen** des Terminals. Der Knopf wirkt an beiden Stellen, und er darf
etwas anstoßen, das wartet.

## Kleinigkeiten, die Zeit sparen

Kommentare mit `//` bis zum Zeilenende. Kein Semikolon — das Zeilenende
beendet eine Anweisung. Bedingungen ohne runde Klammern, Blöcke immer in
geschweiften.

Namen dürfen Umlaute enthalten (`ofen_süd`). Heißt ein Connector wie ein
Schlüsselwort, kommt er im Code in Rückstriche:

```
`for`.redstone(15)
```

Die Vervollständigung im Editor setzt sie von selbst, sobald sie nötig sind.
