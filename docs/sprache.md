# Manifold — die Sprache

Entwurf. Was hier steht, ist entschieden; was fehlt, ist als offen markiert.
Diese Datei ist maßgeblich für Compiler, Laufzeitumgebung und Editor.

Programme stehen in Dateien mit der Endung `.mf`.

Stand: 2026-08-19

---

## 1. Wofür Manifold gemacht ist

Manifold beschreibt eine Fabrik, keine Allgemeinheit. Die Sprache wird von
Spielern geschrieben, nicht von Programmierern — aber sie ist eine echte
Sprache mit Funktionen, Bedingungen und Typen, keine Konfigurationsdatei.

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

### Namen und Schlüsselwörter

Schlüsselwörter sind englisch. Sie stehen unmittelbar neben Registry-Namen,
und die sind es ebenfalls — `wenn crusher_1.online` mischt zwei Sprachen in
einer Zeile, und der Rest der Zeile lässt sich nicht mit übersetzen.

Die vollständige Liste, weil sie festlegt, was in Rückstriche muss:

```
Ablauf      if  else  for  in  while  break  continue  return  fn  let
Werte       true  false  it
Deklaration worker  group  multiblock  event  display  on  import
Worker      from  to  filter  maintain  rate  per  when  priority
            strategy  overflow
Gruppen     members
Multiblock  devices
Display     title  row  text  progress  indicator  list  button
Ereignisse  emit  await  where  timeout  sleep
Auswahl     move  except
Eingebaut   storage  crafting  world  network  workers  multiblocks
```

**Nach einem Punkt gilt die Liste nicht.** `crushers.where(...)` braucht keine
Rückstriche, obwohl `where` ein Schlüsselwort ist: Was hinter dem Punkt steht,
benennt ein Feld oder eine Methode, und die vergibt das System, nicht der
Spieler. Ein Zusammenstoß ist dort ausgeschlossen.

Namen, die der Spieler selbst vergibt, dürfen dagegen alles enthalten, was ein
Buchstabe ist:

```
let ofen_süd = furnaces.first()
worker erzförderung { ... }
```

Zwei Namen gelten als gleich, wenn sie nach Unicode-Normalform NFC gleich
sind. Das ist keine Spitzfindigkeit: Die Texteingabe im Spiel liefert `ü` je
nach Herkunft als ein Zeichen oder als `u` mit angehängten Punkten. Ohne
Normalisierung wären das zwei verschiedene Connectoren, die gleich aussehen —
ein Fehler, den niemand am Bildschirm finden kann.

Meldungen des Übersetzers und die Oberfläche des Editors folgen der
Spracheinstellung des Spielers. Die Sprache selbst folgt ihr nicht.

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

Flüssigkeiten werden in **Millibucket** gezählt, wie überall in NeoForge — ein
Eimer sind 1000. Damit steht im Programm dieselbe Zahl wie in jeder anderen
Mod.

Nur stehende Flüssigkeiten zählen. In der Registry stehen Wasser und fließendes
Wasser als zwei Einträge; ein Muster wie `fluid:*water*` fände sonst beide, und
die eine Sorte ließe sich nirgends lagern.

`chemical:` bezeichnet Mekanisms Gase, Schlämme und Infusionen. Die
Schreibweise steht hier fest, weil sie sonst später nachträglich zwischen die
bestehenden Arten gezwängt werden müsste; die Anbindung selbst kommt später.

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

Bei einer Auswahl über mehrere Arten zählt die Menge **insgesamt**:

```
move 64 tag:c/ores from chest to storage       // 64 Stück, egal welches Erz
```

Beim Worker bedeutet dieselbe Zahl in `maintain` dagegen *je Art*. Das ist
kein Versehen: `move` schiebt einen Stapel, `maintain` hält einen Vorrat. Wer
64 Erz bewegt, meint einen Stapel; wer 64 Kohle vorhält, meint von jeder Sorte
genug. Eine einheitliche Regel wäre in einem der beiden Fälle die falsche.

### Mengen von Gegenständen

Drei Wege, die sich kombinieren lassen:

```
tag:c/ores                                  // alle Erze
item:*_dust                                 // alle, deren Name auf _dust endet
item:mekanism/*                             // alles aus einer Mod
tag:c/ores except item:ancient_debris       // mit Ausnahme
```

**Tags gibt es zweimal:** `tag:` trifft Gegenstände, `fluidtag:` trifft
Flüssigkeiten. Zwei Wörter statt einem, weil aus der Zeile hervorgehen muss,
wovon sie handelt — ein Worker, eine Vorlage und ein `move` wählen daran ihren
Weg, und ein Tag, der beides treffen könnte, ließe diese Wahl offen.

**Namensraum und Pfad trennt ein Schrägstrich — oder ein Doppelpunkt.**
`item:mekanism/steel_ingot` und `item:mekanism:steel_ingot` meinen dasselbe.
Die zweite Form ist die, die JEI anzeigt und die jeder von dort kopiert; sie
zurückzuweisen hieße, bei jeder kopierten ID eine Berichtigung von Hand zu
verlangen.

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

Deshalb gehört `except` zur Auswahl und nicht in die Nachbesserung.

> **Noch nicht gebaut:** die Anzeige, was ein Muster gerade trifft. Ohne sie
> ist ein Muster über zwanzigtausend Einträge nicht zu überblicken — sie
> steht als offener Punkt 3.11.

**Auflösungszeitpunkt:** Muster werden beim Übersetzen gegen die Registry
aufgelöst, nicht bei jeder Ausführung. Ein Muster über zwanzigtausend Einträge
darf den Server nicht pro Tick beschäftigen.

### Eine Auswahl mit einem Namen

Dieselbe Auswahl steht selten nur an einer Stelle. `filter` gibt ihr einen
Namen:

```
filter ore_factory {
    tag:c/ores
    item:deepslate_coal_ore
    except item:ancient_debris
}
```

Jede Zeile ohne `except` legt dazu, jede mit nimmt weg. **Erst alles
zusammen, dann die Ausnahmen** — die Reihenfolge der Zeilen ist damit
gleichgültig. Eine einzelne Zeile darf für sich schon eine vollständige
Auswahl sein, also auch `tag:c/ores except item:ancient_debris`.

Der Name steht überall, wo eine Auswahl steht:

```
worker erz_holen {
    from grube
    to storage
    filter ore_factory
}

move 64 ore_factory from brecher to storage
if storage.count(ore_factory) < 500 { … }
```

Damit kann eine Vorlage zwei Dinge, die eine geschriebene Auswahl nicht kann:
Sie steht an mehreren Stellen, ohne wiederholt zu werden, und sie legt
mehrere Auswahlen zusammen — ein Worker nimmt nur **eine** `filter`-Zeile.

Drei Festlegungen:

- **Eine Menge davor heißt insgesamt.** `64 ore_factory` sind 64 zusammen,
  nicht 64 je Art — genau wie `64 tag:c/ores`. Bei `maintain` bleibt es je
  Art, aus demselben Grund wie dort.
- **Gegenstände oder Flüssigkeiten, nie beides.** Woran es erkannt wird: an
  den Zeilen. Gemischt ist ein Fehler beim Übernehmen — `move` schickt Wasser
  und Steine über verschiedene Wege, und eine Vorlage mit beidem wäre an
  jeder Stelle etwas anderes.
- **Keine Vorlage in einer Vorlage.** Wer die gemeinsamen Zeilen in zweien
  braucht, schreibt sie in beide. Ineinandergelegte Vorlagen können sich
  gegenseitig enthalten, und dann wäre nicht mehr zu sagen, was sie
  auswählen.

Heißt eine Vorlage wie ein Gerät im Netz, **geht die Vorlage vor**, und der
Editor warnt. Gerätenamen kommen aus der Beschriftungspistole; hinge die
Bedeutung eines Programms daran, wie jemand später einen Connector benennt,
wäre es aus der Ferne nicht mehr zu lesen.

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
Duration
Device   Group    Multiblock
Job      Worker   Event
List<T>  Set<T>
```

Die Namen sind englisch, aus demselben Grund wie die Schlüsselwörter.
`Duration` ist der Typ der Zeitangaben aus Abschnitt 13 und bewusst von `Int`
getrennt.

---

## 5b. Globale Werte

Ein Wert, den alle Dateien sehen, und eine Änderung, von der der Rest des
Programms erfährt:

```
global modus = "tag"

worker erz {
    from grube
    to storage
    when modus == "tag"
}

display halle {
    row "Modus" modus
}

fn nachtschicht() {
    modus = "nacht"
}
```

Ein Aufruf von `nachtschicht()` legt den Worker schlafen und ändert die
Anzeige. **Ohne dass irgendwo steht, dass er das tun soll** — Anzeigen und
`when` werten ihre Ausdrücke ohnehin laufend aus.

### Die Regeln

- **`global` und nicht `let`.** Ein Programm besteht nur aus Deklarationen;
  ein `let` auf oberster Ebene sähe aus wie eine Anweisung, die niemand
  ausführt.
- **Der Anfangswert ist ein Literal.** Eine Rechnung hätte keinen festen
  Zeitpunkt — liefe sie beim Übernehmen, beim Serverstart, bei jedem Laden
  des Chunks?
- **Der Typ kommt aus dem Anfangswert.** Wer einem Text eine Zahl zuweist,
  bekommt eine Warnung, solange beide Seiten Literale sind. Weiter reicht die
  Prüfung nicht: Die Sprache hat keinen Typprüfer, und Typfehler fallen sonst
  zur Laufzeit auf.
- **Geschrieben wird aus Funktionen und Ereignisblöcken.** Ein Worker ist eine
  Zusage über einen Dauerzustand und hat keinen Ort für eine Anweisung; eine
  Anzeige zeigt an.
- **Ein gleichnamiges `let` verdeckt ihn**, wie überall.
- **Er überlebt den Serverneustart** und den Programmwechsel — solange Name
  und Art gleich bleiben. Ein Text, der zur Zahl wird, fängt neu an.

Was gerade drinsteht, zeigt das Terminal im Reiter **Netzwerk**.

---

## 6. Geräte ansprechen

Ein Connector gibt einer Maschine einen Namen. Der Name ist im Code direkt
verfügbar:

```
crusher_1.insert(64 item:iron_ore)
crusher_1.online
furnace_2.items()
```

**Was ein Gerät hat:**

| | Bedeutung |
|---|---|
| `online` | Hängt es gerade im Netz? |
| `name` | Der Name, den die Beschriftungspistole vergeben hat |
| `redstone()` / `redstone(int)` | Die Stärke, 0 bis 15 — gelesen oder gesetzt |
| `count(auswahl)` | Wie viel von einer Art **in diesem Gerät** liegt. Ohne Auswahl alles zusammen |
| `insert(auswahl)` | Legt aus dem Speicher etwas hinein. Gibt zurück, wie viel ankam — **weniger ist normal** |
| `items()` | Was gerade drinliegt. Leere Fächer fallen weg |

Gruppen verhalten sich wie ein Gerät, verteilen aber:

```
crushers.send(64 item:iron_ore)
crushers.send(64 item:iron_ore, strategy: least_filled)
```

> **Noch nicht gebaut:** `send()` an einer Gruppe. Die Verteilung über
> mehrere Ziele gibt es beim Worker, beim Aufruf noch nicht; sie kommt,
> sobald eine Gruppe ein Wert ist.
>
> **Gestrichen am 25.08.: `output()`.** `move 64 item:x from brecher to
> storage` nimmt ohnehin nur das, was die Maschine herausgeben will — ein
> Eingangsfach lehnt die Entnahme von sich aus ab, und das entscheidet die
> Maschine und nicht diese Mod. `output()` hätte dasselbe ein zweites Mal
> gesagt, und dabei stillschweigend auch noch die Quelle gesetzt.

### Redstone

Redstone ist ein Wert von 0 bis 15, kein Ja/Nein:

```
let stärke = sensor.redstone()
alarm.redstone(15)
```

```
on redstone_changed(sensor, strength) {
    if strength >= 12 {
        pumps.stop()
    }
}
```

### Material bewegen

`move` ist die einzige Anweisung mit eigener Wortstellung. Sie liest sich wie
ein Satz, weil sie in fast jedem Programm vorkommt:

```
move item:iron_ore from chest to crusher_1
move 64 item:iron_ore from chest to crusher_1
move tag:c/ores from chest to crushers
```

Sie gibt zurück, wie viel tatsächlich bewegt wurde. Weniger als gewünscht ist
normal, `0` auch — die Quelle kann leer und das Ziel voll sein. Ein Fehler ist
es erst, wenn ein Gerät nicht mehr da ist (Abschnitt 14).

```
let bewegt = move item:iron_ore from chest to crusher_1
```

`move` ist der einmalige Bruder des Workers: dieselbe Bewegung, aber jetzt und
einmal statt dauerhaft und beobachtet. Wer sie in eine Schleife setzt, hat
einen Worker nachgebaut — schlechter, weil das System dann nicht mehr weiß,
was gemeint ist.

### Verteilung auf mehrere Ziele

```
round_robin    reihum, gleichmäßig
least_filled   dorthin, wo am wenigsten liegt
fill_first     das erste Ziel voll, dann das nächste
```

Bei `send` als benanntes Argument, beim Worker als Angabe:

```
crushers.send(64 item:iron_ore, strategy: least_filled)

worker feed { ... strategy least_filled }
```

Der Unterschied in der Schreibweise ist gewollt: Das eine ist ein Aufruf mit
Argumenten, das andere eine Deklarationsangabe.

### Wenn ein Gerät heißt wie ein Schlüsselwort

Ein Spieler darf seine Maschine `for` nennen. Die Label-Gun nimmt jeden Namen
an; es gibt keine Liste verbotener Wörter.

Im Code wird ein solcher Name in Rückstriche gesetzt:

```
`for`.insert(64 item:iron_ore)
```

**Warum nicht einfach verbieten:** Eine Liste verbotener Namen wächst mit der
Sprache. Führt eine spätere Fassung `match` ein, ginge jedes Netz kaputt, in
dem eine Maschine so heißt — auf einem Server, der seit Monaten läuft. Namen
sind Spielstand, Schlüsselwörter sind es nicht.

Der eigentliche Teil dieser Lösung ist die Fehlermeldung. Wer `for.insert(...)`
schreibt, bekommt nicht „Syntaxfehler an Position 4", sondern:

> `for` ist ein Schlüsselwort. Meinst du den Connector gleichen Namens? Dann
> schreibe ihn in Rückstriche.

Die Vervollständigung im Editor setzt sie von selbst, sobald sie nötig sind.

---

## 7. Worker

Ein Worker ist eine Zusage, keine Schleife: Solange die Deklaration im
übernommenen Code steht, hält das System sie ein. Es darf dabei selbst
entscheiden, wann es tätig wird — schlafen, bis sich etwas ändert, oder
mehrere Transfers zusammenfassen.

```
worker fuel_supply {
    from storage
    to generators
    filter tag:c/coals
    maintain 64
}
```

### Die Angaben

```
from <gerät | gruppe | storage | crafting>    Pflicht
to <gerät | gruppe | storage>                 Pflicht
filter <auswahl | vorlage>                    sonst: alles
maintain <menge>                              sonst: schieben, was geht
rate <menge> per <zeit>                        sonst: so schnell es geht
when <bedingung>                              sonst: immer
priority <zahl>                               sonst: 0
strategy <verteilung>                         sonst: round_robin
overflow to <gerät>                           sonst: pausieren
```

`from` und `to` sind Pflicht, alles andere hat eine Vorgabe. Ein Worker ohne
Namen gibt es nicht — der Name ist sein Bezug im Terminal und im Code
(`fuel_supply.pause()`).

### Nachschub ist derselbe Worker

`crafting` ist eine Quelle wie jede andere. Damit braucht Vorratshaltung keine
eigene Form:

```
worker keep_ingots {
    from crafting
    to storage
    filter item:iron_ingot
    maintain 256
}
```

Das ist der Grund, warum `from` eine Quelle nennt und nicht eine Betriebsart.
Zwei Deklarationsformen für „hol es aus dem Lager" und „lass es herstellen"
hätten dieselbe Bedeutung zweimal beschrieben.

### Was `maintain` genau heißt

Drei Festlegungen, ohne die es mehrdeutig ist:

- **Pro Zielgerät, nicht pro Gruppe.** `to generators` mit `maintain 64` hält
  64 in *jedem* Generator. Bei `to storage` fallen beide Lesarten zusammen,
  weil es ein Ziel ist.
- **Pro Gegenstandsart, nicht insgesamt.** `filter tag:c/coals` mit
  `maintain 64` hält 64 von jeder Kohleart. Was das Muster trifft, sollte der
  Editor anzeigen — diese Anzeige gibt es noch nicht (offener Punkt 3.11),
  und ohne sie ist nicht abzusehen, was man gerade zugesagt hat.
- **Nur auffüllen, nie abziehen.** Liegen 300 statt 256 im Lager, holt der
  Worker die 44 nicht zurück. Wer das will, schreibt einen zweiten Worker in
  die Gegenrichtung.

### Durchsatz

```
rate 32 per 8t
```

Das sind 32 Gegenstände alle 8 Ticks **am Stück**, nicht 4 pro Tick. Maschinen
wollen in aller Regel den Stapel, nicht das Rinnsal.

### Bedingungen müssen beobachtbar sein

```
worker night_smelting {
    from storage
    to furnaces
    when world.is_night
}

worker overflow_dump {
    from storage
    to trash_chest
    when storage.fill_level > 0.9
}
```

`when` darf nur auf Zustände zugreifen, deren Änderung das System bemerken
kann: Redstone, Bestände, Gerätestatus, Tageszeit. Eine beliebige Rechnung
wäre zwar auswertbar, aber nicht beobachtbar — das System müsste sie in jedem
Tick wiederholen und hätte damit genau die Polling-Schleife, die ein Worker
vermeiden soll.

Ist die Bedingung falsch, geht der Worker in `WAITING_CONDITION`. Das ist die
Schwester von `WAITING_TARGET`, das bei vollem Ziel greift. Im Terminal steht
damit nicht nur, *dass* ein Worker schläft, sondern *warum*.

### `storage`, `crafting` und `world` sind Schlüsselwörter

Das folgt aus der Entscheidung, keine Namen zu verbieten: Ein Spieler darf
einen Connector `storage` nennen. Wären die drei bloß vorbelegte Namen,
bräuchte es eine neue Regel für den Konflikt. Als Schlüsselwörter greift die
vorhandene:

```
`storage`.insert(64 item:iron_ingot)   // der Connector des Spielers
storage.insert(64 item:iron_ingot)     // das Netzwerklager
```

---

## 8. Gruppen

Eine Gruppe fasst Geräte zusammen und verhält sich nach außen wie ein Gerät.

```
group crushers {
    members crusher_1, crusher_2, crusher_3
    strategy round_robin
}
```

Mitglieder lassen sich auch über ein Namensmuster aufnehmen:

```
group furnaces {
    members furnace_*
}
```

Anders als bei Gegenständen wird ein Gerätemuster **nicht** beim Übersetzen
festgeschrieben. Wer einen weiteren Ofen aufstellt und ihn `furnace_9` nennt,
soll ihn nicht auch noch im Code eintragen müssen — die Gruppe nimmt ihn auf,
sobald er im Netz ist. Das geht hier, weil Connectoren dutzendweise vorkommen
und nicht zu Tausenden.

### Strategien

```
round_robin       reihum, gleichmäßig
first_available   das erste, das kann
least_filled      dorthin, wo am wenigsten liegt
random            zufällig
priority          in der Reihenfolge der Mitglieder
```

Ohne Angabe gilt `round_robin`. Beim Aufruf lässt sie sich überschreiben:

```
crushers.send(64 item:iron_ore, strategy: least_filled)
```

Das Konzept nannte zusätzlich `balanced`. Es ist gestrichen, weil sich seine
Bedeutung nicht von `least_filled` unterscheiden ließ, und eine Strategie, die
niemand erklären kann, wählt auch niemand bewusst aus.

---

## 9. Multiblocks

Ein Multiblock ist eine **Vorlage**, keine Maschine. Im Code steht, welche
Rollen eine Anlage hat und was sie kann; gebaut wird sie in der Welt, und zwar
so oft man will.

```
multiblock OrePlant {
    devices {
        crusher
        furnace
        output
    }

    fn process(ore: Item) {
        move ore to crusher
        await device_output(crusher)
        move item:*_dust from crusher to furnace
        await device_output(furnace)
        move item:*_ingot from furnace to output
    }
}
```

Verwendet wird eine gebaute Anlage über ihren Namen:

```
ore_plant_1.process(item:iron_ore)
```

### Warum Vorlage und Instanz getrennt sind

Wer drei Erzanlagen baut, will sie nicht dreimal programmieren. Trennt man
beides nicht, steht am Ende dieselbe Logik dreimal im Code und geht dreimal
auseinander.

Deshalb dürfen die Connectoren aller Instanzen **dieselben Namen tragen**.
`crusher` in `ore_plant_1` und `crusher` in `ore_plant_2` sind verschiedene
Geräte; innerhalb der Vorlage bezeichnet `crusher` immer das eigene.

### Innen und außen

Was in `devices` steht, gehört der Anlage und ist von außen nicht sichtbar.
Was als `fn` deklariert ist, ist die Schnittstelle nach außen. Eine dritte
Angabe braucht es nicht — die Trennung fällt mit der zwischen Gerät und
Funktion zusammen.

Fehlt einer Instanz ein Gerät aus `devices`, ist sie unvollständig: Sie
erscheint im Terminal als Fehler und nimmt keine Aufrufe an. Das ist besser
als ein Aufruf, der halb durchläuft und in der Mitte auf ein fehlendes Gerät
trifft.

### Wie eine Anlage in der Welt entsteht

Über die Namen ihrer Connectoren. Wer eine Anlage bauen will, benennt ihre
Geräte mit dem Namen der Anlage davor:

```
ore_plant_1/crusher
ore_plant_1/furnace
ore_plant_1/output
```

Damit gehört jedes dieser Geräte zu `ore_plant_1`, und die Anlage gilt als
`OrePlant`, weil sie deren Rollen abdeckt. Eine zweite Anlage entsteht, indem
man dieselben Rollen mit `ore_plant_2/` davor vergibt.

Der Schrägstrich steht nur in der Beschriftung. Im Code ist außen
`ore_plant_1` und innen `crusher` — geschrieben wird er nie.

Passen mehrere Vorlagen auf eine Anlage, meldet sie sich als mehrdeutig. Fehlt
ihr ein Gerät, erscheint sie im Terminal mit der Angabe, welches — und nimmt
keine Aufrufe an.

---

## 10. Ereignisse

Reaktive Logik läuft über Ereignisse, nicht über Abfragen in Schleifen.

```
on redstone_changed(sensor, strength) {
    if strength >= 12 {
        pumps.stop()
    }
}
```

### Eingebaute Ereignisse

```
redstone_changed(device, strength)   Redstone-Stärke 0..15 hat sich geändert
device_online(device)                Gerät ist erreichbar geworden
device_offline(name)                 Gerät ist verschwunden — nur noch der Name
device_changed(device)               Inhalt eines Geräts hat sich geändert
device_output(device)                im Gerät ist etwas dazugekommen
crafting_finished(job)               Fertigungsauftrag ist fertig
crafting_failed(job, reason)         Fertigungsauftrag ist gescheitert
```

`device_output` meldet ausdrücklich nicht „fertig": Ob eine Maschine ihre
Arbeit beendet hat, weiß von außen niemand. Gemessen wird der Unterschied zum
letzten Blick — ist von einer Art mehr da, ist etwas dazugekommen. Was das
Netz selbst einlegt, zählt nie mit, und gemeldet wird jeder Zuwachs: Eine
Maschine, die eine Ladung stückweise ausgibt, meldet jedes Stück.

> **Noch nicht gebaut:** `crafting_finished` und `crafting_failed` — sie warten
> auf das Autocrafting, und bis dahin weist die Prüfung sie als unbekanntes
> Ereignis zurück.

Bewusst nicht dabei ist ein Ereignis für jede Bestandsänderung. In einem Lager
mit zwanzigtausend Arten feuert das im Sekundentakt, und niemand kann darauf
sinnvoll reagieren. Wer auf Bestände reagieren will, nimmt einen Worker mit
`when` — der wird vom System genau dann geweckt, wenn es nötig ist.

### Eigene Ereignisse

```
event OreBatchReady(item: Item, amount: Int)

emit OreBatchReady(item:iron_ore, 256)

on OreBatchReady(item, amount) {
    crushers.send(amount item)
}
```

Bei der Deklaration stehen die Typen, beim Empfangen nicht — dort sind sie
bekannt. Das ist dieselbe Regel wie bei Funktionen.

### Mehrere Empfänger

Mehrere `on`-Blöcke für dasselbe Ereignis laufen alle, in keiner zugesicherten
Reihenfolge. Wer eine Reihenfolge braucht, hat in Wahrheit eine Abfolge und
schreibt eine Funktion.

---

## 11. Displays

Ein Display ist eine Beschreibung, kein Zeichenprogramm. Es steht in der
deklarativen Hälfte der Sprache, weil es dauerhaft gilt: Was es zeigt, hält
das System aktuell.

```
display factory_status {
    title "Fabrik"

    row "Eisen" storage.count(item:iron_ingot)
    row "Stahl" storage.count(item:steel_ingot)

    progress "Erzverarbeitung" ore_import.progress
    indicator "Reaktor" reactor.online
}
```

### Bausteine

```
title <text>              Überschrift
row <text> <wert>         Beschriftung und Wert nebeneinander
text <wert>               freier Text
progress <text> <0..1>    Fortschrittsbalken
indicator <text> <bool>   Lämpchen
list <text> <liste>       Aufzählung, etwa Bestände oder Aufträge
button <text> <funktion>  löst eine Funktion aus
```

### Was ein Display nicht darf

**Ein Display rechnet nicht.** Es nennt Werte, und das System entscheidet,
wann es sie neu holt. Eine Schleife oder ein `await` im Display gibt es nicht.

Der Grund ist derselbe wie beim Worker: Nur wenn das System weiß, *welche*
Werte ein Display zeigt, kann es sie beobachten und nur bei Änderung neu
zeichnen. Ein Display, das selbst rechnet, müsste in jedem Tick laufen — und
davon hängen in einem großen Netz schnell dreißig an der Wand.

`button` ist die einzige Ausnahme, und auch nur einseitig: Er zeigt nichts an,
sondern ruft eine Funktion auf, wenn jemand ihn drückt.

---

## 12. Listen und Mengen

```
storage.items().where(it.amount > 64).count()
storage.items().sort(it.amount).first()
```

`it` ist das jeweilige Element, und an einem Eintrag stehen zwei Angaben:
`it.item` ist die Art, `it.amount` die Menge. Das spart die Pfeilschreibweise
(`m => m.amount`), die für Spieler ohne Programmiererfahrung die größte Hürde
wäre.

Vorgesehen sind:

| | | Stand |
|---|---|---|
| `count` | zählen | **gebaut** |
| `first` | das erste Element, oder nichts | **gebaut** |
| `sum` | alle Zahlen aufaddieren — an einem Bestandsposten seine Menge | **gebaut** |
| `where` | aussortieren | **gebaut** |
| `sort` | ordnen | **gebaut** |

An einem Posten stehen:

| | | |
|---|---|---|
| `it.amount` | die Menge | **gebaut** |
| `it.item` | die Art — nur, wenn der Posten genau eine meint | **gebaut** |
| `it.fluid` | dasselbe an einer Flüssigkeitsliste | **gebaut** |

`it.item` an einer Auswahl über mehrere Arten — einer Filter-Vorlage etwa —
ist ein Fehler und keine Vermutung: Welche der Arten gemeint wäre, ließe sich
nur raten.

Mehr nicht — kein `map`, kein `groupBy`. In einer Fabrik gibt es dafür bisher
keinen Fall, und hinzufügen lässt sich später leicht, wegnehmen nicht.

**`where` und `sort` werten ihren Ausdruck je Eintrag aus.** Alle anderen
Argumente werden ausgerechnet, bevor der Aufruf beginnt; diese beiden bekommen
den Ausdruck selbst und werten ihn für jeden Eintrag neu aus, mit `it` als
diesem Eintrag.

`it` lebt dabei nur im Aufruf: Es legt sich über das, was außen steht, ohne
es abzuschneiden — `where(it > grenze)` sieht beides. Und zwei Aufrufe
nacheinander sehen jeder ihr eigenes.

`it` ist ein Schlüsselwort. Wer eine Variable so nennen will, schreibt sie in
Rückstriche.

**`where` und `sort` sind auf Listen reserviert.** Sie werden im Aufrufpfad
abgefangen, bevor eigene Funktionen an die Reihe kommen — eine Funktion
dieses Namens ließe sich zwar noch schreiben, aber auf einer Liste käme sie
nie zum Zug. Nimm einen anderen Namen.

Woher eine Liste kommt: `storage.items()` für den Netzspeicher,
`crusher_1.items()` für ein Gerät.

**Verschachtelt braucht `it` einen Namen.** Zwei ineinandergeschachtelte
`where` können sich nicht dasselbe `it` teilen:

```
crushers.members().where(m => storage.count(m.input) > 0)
```

Innen wird also doch benannt. Das ist der Grund, warum die Pfeilschreibweise
nicht ganz verschwindet — sie ist nur nicht mehr der Normalfall.

---

## 13. Warten und Nebenläufigkeit

Code kann auf Ereignisse warten:

```
let ergebnis = await BatchFinished where id == jobId
let ergebnis = await BatchFinished where id == jobId timeout 30s
```

**Wartender Code überlebt einen Serverneustart.** Der Übersetzer wandelt
Funktionen, die warten können, in Zustandsmaschinen um; an den Wartepunkten
wird der Zustand gespeichert.

### Wenn die Antwort ausbleibt

`timeout` braucht einen zweiten Weg, sonst stünde nach Ablauf ein Wert da, den
es nie gab:

```
let ergebnis = await BatchFinished where id == jobId timeout 30s else {
    notify("Maschine antwortet nicht")
    return
}
```

Der `else`-Zweig muss den Ablauf verlassen — `return`, `break` oder
`continue`. Danach gilt `ergebnis` als vorhanden, und niemand muss ihn prüfen.

**Damit gibt es kein `try`/`catch`.** Das Konzept hatte es für genau diesen
Fall vorgesehen; es ist der einzige, in dem es gebraucht würde, und dafür ist
ein zweiter Block mit eigener Fangregel zu viel Apparat. Alles andere, was
schiefgehen kann, hält den Ablauf an und landet im Terminal (Abschnitt 14).

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

### Zeitangaben

Zeit wird mit Einheit geschrieben:

```
sleep 5s
await BatchFinished where id == jobId timeout 30s
```

Einheiten sind `t` für Ticks, `s`, `min` und `h`. Gerechnet wird intern immer
in Ticks; `1s` sind 20 davon. Bruchteile sind erlaubt, solange sie aufgehen —
`0.5s` sind 10 Ticks und `0.1s` sind 2. Was nicht aufgeht — `0.01s` wären ein
Fünftel Tick —, meldet der Übersetzer, statt still zu runden.

Zusammensetzungen wie `1h30min` gibt es nicht. Wer sie braucht, schreibt
`90min`.

**Zeit ist ein eigener Typ, keine Zahl.** `sleep(30)` ist deshalb ein Fehler
und kein Rätsel: Ob 30 Ticks oder 30 Sekunden gemeint sind, ist nicht zu
erraten, und ein Faktor 20 fällt im Betrieb erst spät auf.

---

## 14. Wenn etwas schiefgeht

**Erwartbare Zustände sind keine Fehler. Unerwartete halten den Ablauf an.**

Erwartbar ist, was im Betrieb dauernd vorkommt: Das Ziel ist voll, die Quelle
leer, die Maschine gerade beschäftigt. Darauf antwortet die Sprache mit
Rückgabewerten und Abfragen, nicht mit Fehlern — ein Worker, der bei vollem
Ziel eine Meldung schriebe, hätte das Terminal in Minuten zugeschüttet.

```
let bewegt = move item:iron_ore from chest to crusher_1   // 0 ist normal
if crusher_1.count(item:iron_ore) > 0 { ... }
```

Unerwartet ist, was auf einen Bruch hindeutet: Der Connector ist abgebaut, das
Kabel gekappt, der Typ passt nicht, eine Grenze ist überschritten. Dann hält
der Ablauf an — er stirbt nicht — und erscheint im Terminal mit derselben
Wahl, die es nach einem Serverneustart gibt: **abbrechen oder weiterlaufen
lassen.** Wer den Connector wieder setzt und „weiter" wählt, macht an
derselben Stelle weiter.

Dass es dieselbe Mechanik ist, ist Absicht. Ein zweites Fehlermodell daneben
zu stellen hieße, zwei Dinge zu lernen, die dasselbe tun.

### Ein entladener Chunk ist kein Bruch

Ein Connector in einem nicht geladenen Chunk ist nicht weg, sondern
vorübergehend nicht erreichbar. Ein Worker pausiert dann und läuft weiter,
sobald der Chunk zurück ist; das ist Normalbetrieb und keine Meldung wert.
Nur der endgültige Verlust — abgebaut, nicht mehr im Netz — ist ein Fehler.

### Kein Fehlersturm

Ein Ereignis wie `redstone_changed` kann in jedem Tick auslösen. Steht ein
Ablauf dieses Handlers wegen eines Fehlers, wird **keine weitere Instanz
gestartet.** Neue Auslöser reihen sich in die Warteschlange ein, solange deren
Grenze es zulässt, und fallen danach weg. Im Terminal steht ein Eintrag, nicht
vierzig gleiche.

### Was zwischen zwei Anweisungen geschehen kann

Das folgt aus den unterbrechbaren Haltepunkten und gehört hierher, weil es in
jedem Programm steckt:

```
if crusher_1.online {
    crusher_1.insert(64 item:iron_ore)
}
```

**Innerhalb eines Ticks sieht ein Ablauf keine Änderung am Zustand der
Geräte.** Was er abfragt, gilt bis zum Ende des Ticks. Wird er dagegen an
einem unterbrechbaren Haltepunkt angehalten und erst im nächsten Tick
fortgesetzt, kann inzwischen alles anders sein — dann schlägt `insert` fehl
und der Ablauf hält an, wie oben beschrieben.

Die Abfrage ist damit nützlich, aber keine Zusage. Sperren braucht trotzdem
niemand: Der Fall, gegen den man sich sperren würde, ist ohnehin abgedeckt.

### Mehrspieler

Gefragt wird, wem das Programm gehört. Ist derjenige nicht da, bleibt der
Ablauf angehalten, bis jemand mit Zugriff auf das Terminal entscheidet.

---

## 15. Grenzen

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

## 16. Dateien

Ein Projekt besteht aus `.mf`-Dateien im Projektbaum. **In der ersten Fassung
bilden alle zusammen einen Namensraum**: Was in einer Datei steht, ist in
allen sichtbar, und Dateien sind reine Ordnung für den Menschen.

`import` ist als Schlüsselwort reserviert, tut aber noch nichts.

Begründung: Echte Module lohnen sich erst, wenn ein Projekt einen Namensraum
sprengt, und das ist bei einer Fabrik nicht abzusehen. Das Wort jetzt zu
reservieren kostet nichts; es später einzuführen, ohne es reserviert zu haben,
bräche jedes Projekt, in dem jemand eine Funktion `import` genannt hat.

---


## 17. Was noch fehlt

Die Sprache ist damit für die erste Fassung beschrieben. Was fehlt, ist nicht
unentschieden, sondern noch nicht gebraucht:

1. **Request/Response** — im Konzept vorgesehen (Kapitel 17), also gerichtete
   Anfragen an ein anderes Programm mit Antwort. Mit `emit`, `on` und `await`
   lässt sich das nachbauen; eine eigene Form lohnt erst, wenn sich zeigt,
   dass es oft gebraucht wird.
2. **Wie eine Multiblock-Instanz in der Welt entsteht** — Controller, Bereich,
   Zuordnung. Das ist keine Sprachfrage, muss aber festliegen, bevor
   Multiblocks gebaut werden.
3. **Rechte im Mehrspielerbetrieb** — wer welchen Code übernehmen und welches
   Terminal bedienen darf.
