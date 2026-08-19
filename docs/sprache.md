# Die Sprache — erste Fassung

Entwurf. Was hier steht, ist entschieden; was fehlt, ist als offen markiert.
Diese Datei ist maßgeblich für Compiler, Laufzeitumgebung und Editor.

Stand: 2026-08-19

---

## 1. Wofür sie gemacht ist

Die Sprache beschreibt eine Fabrik, keine Allgemeinheit. Sie wird von Spielern
geschrieben, nicht von Programmierern — aber sie ist eine echte Sprache mit
Funktionen, Bedingungen und Typen, keine Konfigurationsdatei.

Zielumgebung sind große Modpacks: dreihundert Mods, zwanzigtausend
Gegenstandsarten. Jede Entscheidung hier ist daran gemessen.

---

## 2. Zwei Welten

Die Sprache ist zweigeteilt, und die Trennung verläuft entlang einer klaren
Linie: **Was dauerhaft gilt, wird beschrieben. Was einmalig geschieht, wird
programmiert.**

### Beschrieben wird, was das System selbst am Laufen hält

```
worker quarry_import {
    from quarry_output
    to storage
    filter tag:c/ores
}

group crushers {
    members { crusher_1, crusher_2, crusher_3 }
    strategy round_robin
}
```

Solche Angaben sind keine Befehle, sondern Zusagen: Solange das hier steht,
sorgt das System dafür. Das System darf sie umsortieren, zusammenfassen und
optimieren, weil es weiß, was gemeint ist — nicht nur, was zu tun ist.

### Programmiert wird, was abläuft

```
fn keepStock(item, amount) {
    let vorhanden = storage.count(item)
    if vorhanden < amount {
        craft(item, amount - vorhanden)
    }
}

on redstone_changed(tank_sensor, strength) {
    if strength >= 12 {
        pumps.stop()
    }
}
```

Hier zählt die Reihenfolge, und das System führt aus, was dasteht.

**Warum diese Trennung:** Ein Dauertransfer, der als Schleife geschrieben ist,
zwingt das System, ihn dumm auszuführen. Als Beschreibung kann es ihn
schlafen legen, bis sich etwas ändert, und ihn bei vollem Ziel pausieren statt
erfolglos zu wiederholen. Dieselbe Aufgabe, die eine Größenordnung weniger
kostet.

---

## 3. Grundform

Blöcke in geschweiften Klammern. Keine Semikolons — das Zeilenende beendet eine
Anweisung. Bedingungen ohne runde Klammern.

```
let x = 5
let name = "Ofenlinie"

if x > 3 {
    log("mehr als drei")
} else {
    log("höchstens drei")
}

for maschine in crushers.members() {
    log(maschine.name)
}

fn verdopple(n) {
    return n * 2
}
```

Kommentare mit `//` bis zum Zeilenende.

---

## 4. Gegenstände, Flüssigkeiten, Tags

**Der Doppelpunkt trennt die Art, der Schrägstrich den Namensraum.**

```
item:iron_ingot                        // minecraft:iron_ingot
item:allthemodium/allthemodium_ingot   // allthemodium:allthemodium_ingot
fluid:water
fluid:mekanism/heavy_water
chemical:mekanism/clean_osmium
tag:c/ores
tag:forge/ingots/copper
```

Fehlt der Schrägstrich, ist `minecraft` gemeint. Das hält den häufigsten Fall
kurz.

Der Doppelpunkt trägt bewusst nur **eine** Bedeutung. Trüge er zusätzlich den
Namensraum, kollidierte er mit Typangaben (`fn craft(item: Item)`) und wäre in
einer Kennung wie `item:allthemodium:allthemodium_ingot` doppelt belegt.

`chemical:` bezeichnet Mekanisms Gase, Schlämme und Infusionen. Die
Schreibweise steht hier fest, weil sie sonst später nachträglich zwischen die
bestehenden Arten gezwängt werden müsste; die Anbindung selbst kommt erst in
Phase 8.

### Mengen

Die Menge steht voran:

```
64 item:iron_ingot
1000 fluid:water
```

Ohne Menge ist alles gemeint, was verfügbar ist:

```
move item:iron_ingot from chest to furnace     // alles, was da ist
move 64 item:iron_ingot from chest to furnace  // höchstens 64
```

### Mengen von Gegenständen

Drei Wege, die sich kombinieren lassen:

```
tag:c/ores                                  // alle Erze
item:*_dust                                 // alle, deren Name auf _dust endet
item:mekanism/*                             // alles aus einer Mod
tag:c/ores except item:ancient_debris       // mit Ausnahme
```

**Warum alle drei:** In einem großen Pack sind Tags der Normalfall, aber sie
fehlen, überschneiden sich oder schneiden zu grob — es gibt eigene Mods, deren
einziger Zweck das Aufräumen dieser Überschneidungen ist. Namensmuster fangen
auf, was Tags nicht abdecken. Ausnahmen fangen die einzelne Maschine auf, die
mit genau einem Eintrag nicht klarkommt.

### Wo der Platzhalter stehen darf

`*` steht für beliebig viele Zeichen und darf an jeder Stelle des Namens
auftreten, auch mehrfach:

```
item:*_ore                 // aluminum_ore, deepslate_aluminum_ore, ...
item:raw_*                 // raw_aluminum, raw_lead, ...
item:*aluminum*            // jede Form von Aluminium, egal wie benannt
item:alltheores/*_ore      // nur die Erze einer bestimmten Mod
```

Das ist keine Bequemlichkeit, sondern von den Namen erzwungen. Modpack-Mods
benennen Varianten nicht einheitlich hinten: Die Form steht als Nachsilbe
(`aluminum_ingot`, `aluminum_dust`), die Steinart und die Dimension als
Vorsilbe (`deepslate_aluminum_ore`, `nether_aluminum_ore`,
`end_aluminum_ore`), der Rohzustand ebenfalls als Vorsilbe (`raw_aluminum`),
und Zwischenprodukte tragen beides (`dirty_aluminum_dust`). Ein Platzhalter,
der nur vorne oder nur hinten stehen darf, kann eine dieser Achsen nie
ansprechen.

### Namensraum bei Mustern

**Ein Muster ohne Namensraum durchsucht alle Namensräume, ein literaler Name
ohne Namensraum meint `minecraft`.**

```
item:iron_ingot            // genau minecraft:iron_ingot
item:*_dust                // jeder Staub aus jeder Mod
item:minecraft/*_dust      // nur die aus Vanilla
```

**Warum diese Ungleichheit gewollt ist:** Ein literaler Name ist eine Nennung
— wer `item:iron_ingot` schreibt, meint das eine Eisen und nichts sonst. Ein
Muster ist eine Suche, und eine Suche, die sich stillschweigend auf Vanilla
beschränkt, findet in einem Pack mit dreihundert Mods so gut wie nichts. Die
Regel folgt damit der Absicht statt der Schreibweise. Wer die Beschränkung
doch will, schreibt sie hin.

### Wenn ein Muster zu viel fängt

Muster greifen nach Namen, nicht nach Bedeutung, und Namen lügen. `item:*_dust`
fängt neben `aluminum_dust` auch `dirty_aluminum_dust` — das ist bei Mekanism
kein fertiger Staub, sondern ein Zwischenschritt der Verarbeitungskette. Wer
„alle Stäube ins Lager" schreibt, saugt damit die eigene Produktion leer.

```
item:*_dust except item:dirty_*
```

Deshalb gehört `except` zur Auswahl und nicht in die Nachbesserung. Der Editor
zeigt zu jedem Muster an, was es gerade trifft; ohne diese Anzeige ist ein
Muster über zwanzigtausend Einträge nicht zu überblicken.

**Auflösungszeitpunkt:** Muster werden beim Übersetzen gegen die Registry
aufgelöst, nicht bei jeder Ausführung. Ein Muster über zwanzigtausend Einträge
darf den Server nicht pro Tick beschäftigen.

---

## 5. Typen

Das System kennt Typen und prüft sie vor dem Übernehmen. Hinschreiben muss sie
niemand:

```
let anzahl = storage.count(item:iron_ingot)    // Zahl, hergeleitet
let liste = storage.items()                    // Liste von Beständen
```

Angegeben werden Typen nur, wo sie nicht herleitbar sind — bei Parametern von
Funktionen und Ereignissen:

```
fn keepStock(item: Item, amount: Int) { }

event OreBatchReady(item: Item, amount: Int)
```

**Warum überhaupt Typen:** Ohne sie kann der Editor nicht sinnvoll
vervollständigen und keine Meldung wie „Unbekannter Connector `cruhser_1` —
meintest du `crusher_1`?" geben. Bei zwanzigtausend Gegenständen ist eine
Vervollständigung ohne Typwissen wertlos.

### Vorgesehene Typen

```
Int      Float    Bool     Text
Item     Fluid    Chemical Tag
Device   Group    Multiblock
Job      Worker   Event
Liste<T> Menge<T>
```

*(Offen: Namen der Typen — deutsch oder englisch. Siehe Abschnitt 9.)*

---

## 6. Geräte ansprechen

Ein Connector gibt einer Maschine einen Namen. Der Name ist im Code direkt
verfügbar:

```
crusher_1.insert(64 item:iron_ore)
crusher_1.online
furnace_2.items()
```

Gruppen verhalten sich wie ein Gerät, verteilen aber:

```
crushers.send(64 item:iron_ore)
crushers.send(64 item:iron_ore, strategy: least_filled)
```

*(Offen: Was geschieht, wenn ein Connector so heißt wie ein Schlüsselwort oder
eine Variable. Siehe Abschnitt 9.)*

---

## 7. Warten und Nebenläufigkeit

Code kann auf Ereignisse warten:

```
let ergebnis = await BatchFinished where id == jobId
let ergebnis = await BatchFinished where id == jobId timeout 30s
```

**Wartender Code überlebt einen Serverneustart.** Der Übersetzer wandelt
Funktionen, die warten können, in Zustandsmaschinen um; an den Wartepunkten
wird der Zustand gespeichert.

### Zwei Arten von Haltepunkten

Das ist der Punkt, an dem die frühere Fassung des Konzepts sich selbst
widersprach, und er gehört in die Sprache, weil er den Übersetzer betrifft.

**Persistierbare Haltepunkte** entstehen an `await`, `sleep`, beim Warten auf
eine Antwort und bei Übergaben an Worker. Dort wird der Zustand auf die Platte
geschrieben. Sie sind selten und dürfen teuer sein.

**Unterbrechbare Haltepunkte** entstehen zusätzlich an jeder Schleifenrückkante
und bei jedem Funktionsaufruf. Dort kann die Ausführung angehalten und im
nächsten Tick fortgesetzt werden; der Zustand bleibt im Arbeitsspeicher und
muss nicht serialisierbar sein. Sie sind häufig und müssen billig sein.

**Warum beides nötig ist:** Ohne die zweite Art hätte diese Schleife keinen
einzigen Haltepunkt und würde den Server anhalten:

```
while true {
    x = x + 1
}
```

Mit ihr läuft sie nur ewig langsam vor sich hin und lässt sich jederzeit
beenden.

---

## 8. Grenzen

Jeder Ablauf hat ein Budget an Rechenschritten je Tick. Ist es aufgebraucht,
wird an einem unterbrechbaren Haltepunkt angehalten und im nächsten Tick
fortgesetzt.

Weitere Grenzen, jeweils einstellbar:

```
gleichzeitige Worker
wartende Abläufe
Länge der Ereigniswarteschlange
Größe von Listen
Verschachtelungstiefe
Rechenschritte je Ablauf insgesamt
```

Wird eine Gesamtgrenze überschritten, hält der Ablauf an und erscheint im
Terminal als Fehler — nicht stillschweigend.

---

## 9. Offen

1. **Name der Sprache.**
2. **Sprache der Schlüsselwörter und Typnamen** — englisch wie bisher in allen
   Beispielen, oder deutsch. Betrifft auch, ob Umlaute in Bezeichnern erlaubt
   sind.
3. **Namenskonflikte zwischen Connectoren und Schlüsselwörtern.** Ein Spieler
   darf seine Maschine `for` nennen. Was dann?
4. **Genaue Form der Worker-Deklaration** — welche Angaben es gibt, welche
   Pflicht sind.
5. **Collections** — welche Operationen (`filter`, `map`, `sort`, `first`,
   `count`, `sum`, `groupBy`) und wie sie geschrieben werden.
6. **Fehlerverhalten zur Laufzeit** — was geschieht, wenn ein Connector
   offline geht, während Code ihn benutzt.
7. **Module und Importe** — wie ein Projekt auf mehrere Dateien verteilt wird.
8. **Genaue Schreibweise von Zeitangaben** (`30s`, `5min`, Ticks).
