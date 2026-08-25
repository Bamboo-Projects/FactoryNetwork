# Beispiele

Programme, die laufen. Jedes ist so kurz wie möglich gehalten und nennt, was
in der Welt dafür stehen muss.

Zum Ausprobieren: Terminal öffnen, Reiter **Code**, hineinschreiben,
übernehmen. Was hängt oder wartet, steht danach im Reiter **Netzwerk**.

Ein Test übersetzt jedes Programm auf dieser Seite bei jedem Bau. Was er nicht
prüfen kann, sind die Namen: `item:iron_dust` und `tag:c/ores` gibt es in einem
großen Pack, in einer leeren Vanilla-Welt nicht. Dort meldet die Laufzeit dann,
dass die Auswahl nichts trifft — das ist keine Fehlfunktion, sondern die
Wahrheit über die Welt.

---

## 1. Etwas von A nach B schaffen

Zwei benannte Connectoren, einer an einer Kiste, einer an einem Ofen.

```
worker nachschub {
    from kiste
    to ofen
    filter item:coal
    maintain 16
    rate 8 per 20t
}
```

`maintain 16` heißt: Im Ofen sollen 16 Stück liegen. Nicht mehr, nicht
weniger — der Worker fährt nach, wenn etwas verbraucht ist, und hört auf, wenn
es reicht.

---

## 2. Alle Erze einsammeln

```
worker erze {
    from quarry_ausgang
    to storage
    filter tag:c/ores
}
```

`tag:c/ores` trifft in einem großen Pack ein paar hundert Sorten. Ohne `rate`
läuft der Worker mit der Voreinstellung.

Soll etwas ausgenommen bleiben:

```
filter tag:c/ores except item:cobblestone
```

---

## 3. Warten, bis eine Maschine meldet

Das ist der Kern der Sprache: Der Ablauf hält an und macht später weiter — auch
nach einem Serverneustart.

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

**Mit Frist**, falls die Maschine nicht antwortet:

```
let ergebnis = await Fertig timeout 30s else {
    log("Der Brecher meldet sich nicht")
    return
}
```

Der `else`-Zweig muss den Ablauf verlassen. Danach steht fest, dass `ergebnis`
einen Wert hat, und niemand muss ihn prüfen.

**Auf das eigene Ereignis warten**, wenn mehrere Abläufe laufen:

```
let ergebnis = await Fertig where nummer == meine_nummer
```

Ohne `where` weckt jedes `Fertig` jeden Wartenden.

---

## 4. Der Reihe nach durch eine Liste

```
event Fertig(nummer: Int)

fn alle_erze() {
    for sorte in tag:c/ores {
        move 8 sorte from lager to brecher
        let ergebnis = await Fertig
    }
}
```

Der Lauf merkt sich, wo er steht. Ein Neustart mitten in der Liste setzt ihn an
derselben Stelle fort — er fängt nicht von vorn an.

---

## 5. Eine Anlage mehrfach bauen

Eine Vorlage beschreibt Rollen. Gebaut wird sie über die Namen der Connectoren:

```
werk_1/eingang
werk_1/ausgang
werk_2/eingang
werk_2/ausgang
```

```
multiblock Werk {
    devices {
        eingang
        ausgang
    }

    fn schleusen() {
        move 3 item:cobblestone from eingang to ausgang
    }
}

fn beide() {
    werk_1.schleusen()
    werk_2.schleusen()
}
```

Innen heißt das Gerät `eingang`; welches gemeint ist, entscheidet die Anlage.
Fehlt einer Anlage ein Gerät, steht sie im Reiter **Netzwerk** mit der Angabe,
welches — und nimmt keine Aufrufe an.

---

## 6. Flüssigkeiten

Gezählt wird in Millibucket; ein Eimer sind 1000.

```
worker kuehlung {
    from wassertank
    to reaktor
    filter fluid:water
    maintain 8000
    rate 1000 per 10t
}
```

Von Hand:

```
fn abfuellen() {
    move 1000 fluid:water from bottich to kessel
}
```

Ins Netz und zurück:

```
move fluid:lava from sammler to storage
move 4000 fluid:lava from storage to generator
```

---

## 7. Eine Anzeige, die etwas auslöst

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

`progress` will eine Zahl zwischen 0 und 1 — deshalb die Division. Der Punkt in
`640.0` ist nötig: Ohne ihn wird ganzzahlig gerechnet, und der Balken bliebe
leer, bis der Vorrat vollständig ist. Neben dem Balken steht der Anteil in
Prozent.

**Eine Anzeige rechnet, aber sie ruft nichts auf.** Ablesbar sind
`storage.count(…)`, `redstone()` an einem Gerät, `online`, ein Workerzustand
und ein globaler Wert; damit darf gerechnet und verglichen werden.
`gerät.count(…)` gibt es in einer Funktion, auf einer Anzeige noch nicht —
dort steht dafür ein `?`.

Die Anzeige erscheint auf jedem Display-Block, der `leitstand` heißt, und im
Reiter **Anzeigen** des Terminals. Der Knopf wirkt an beiden Stellen; er darf
etwas anstoßen, das wartet.

---

## 8. Wenn sich etwas am Netz ändert

```
on device_online(gerät) {
    log("Neu am Netz")
}

on device_offline(name) {
    log("Verschwunden: " + name)
}
```

Beim ersten Aufbau nach einem Serverstart bleibt es still — sonst käme bei
jedem Anmelden eine Meldung je Gerät.

**Wenn sich der Inhalt eines Geräts ändert:**

```
fn eine_runde() {
    move 8 item:iron_ore from lager to brecher
    let gerät = await device_changed
    move item:iron_dust from brecher to lager
}
```

`device_changed` sagt, dass sich etwas getan hat — nicht, dass die Maschine
fertig ist. Das weiß von außen niemand: Der Ausgang kann von vorher gefüllt
sein, und jede Mod zählt anders. Was „fertig" bedeutet, schreibt man selbst
dazu.

**Achtung bei `count`:** `brecher.count(item:iron_dust)` zählt den
**Netzspeicher** und nicht den Brecher — der Gerätename davor ändert daran
nichts. Was in der Maschine liegt, sagt `brecher.items()`:

```
fn eine_runde() {
    move 8 item:iron_ore from lager to brecher
    let vorher = brecher.items().count()
    await device_changed
    move item:iron_dust from brecher to lager
}
```

Hingeschaut wird nur, wenn das Programm überhaupt darauf hört, und dann alle
zehn Ticks.

---

## 9. Ein Modus für die ganze Fabrik

```
global modus = "tag"

worker erz {
    from grube
    to storage
    filter tag:c/ores
    when modus == "tag"
}

display halle {
    title "Fabrik"
    row "Modus" modus
}

fn nachtschicht() {
    modus = "nacht"
}

fn tagschicht() {
    modus = "tag"
}
```

**Was in der Welt stehen muss:** ein Connector namens `grube`, eine
Anzeigewand namens `halle`.

Ein Aufruf von `nachtschicht()` legt den Worker schlafen und ändert die
Anzeige — ohne dass irgendwo steht, dass er das tun soll. Anzeigen und `when`
werten ihre Ausdrücke ohnehin laufend aus, und der Wert steht an einer Stelle
statt an dreien.

Der Modus überlebt den Serverneustart. Wer die Fabrik nachts verlässt, findet
sie morgens im selben Zustand vor.

**Auslösen lässt sich das von überall:** aus einem Knopf auf der Anzeige, aus
einem `on redstone_changed`, oder aus einem Ablauf, der auf die Uhrzeit
wartet.

---

## 10. Dieselbe Auswahl an mehreren Stellen

```
filter erze {
    tag:c/ores
    item:deepslate_coal_ore
    except item:ancient_debris
}

worker aus_der_grube {
    from grube
    to storage
    filter erze
}

worker aus_der_kiste {
    from sammelkiste
    to storage
    filter erze
}

fn aufraeumen() {
    move erze from brecher to storage
}
```

**Was in der Welt stehen muss:** Connectoren namens `grube`, `sammelkiste`
und `brecher`.

Zwei Worker und ein Ablauf meinen dieselben Sorten. Kommt eine Mod mit einem
neuen Erz dazu, das der Tag nicht kennt, steht die Ergänzung an einer Stelle
statt an dreien — und `except` nimmt an allen dreien dasselbe heraus.

Ohne die Vorlage ginge das nicht einmal in zwei Zeilen: Ein Worker nimmt nur
eine `filter`-Zeile, eine zweite würde stillschweigend übergangen.
