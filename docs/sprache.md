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
if  else  for  in  while  fn  let  return  true  false
worker  group  multiblock  event  on  import
from  to  filter  maintain  rate  per  when  priority
strategy  overflow  move  except
storage  crafting
await  where  timeout  sleep
it
```

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
Duration
Device   Group    Multiblock
Job      Worker   Event
List<T>  Set<T>
```

Die Namen sind englisch, aus demselben Grund wie die Schlüsselwörter.
`Duration` ist der Typ der Zeitangaben aus Abschnitt 9 und bewusst von `Int`
getrennt.

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
filter <auswahl>                              sonst: alles
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
  `maintain 64` hält 64 von jeder Kohleart. Der Editor zeigt an, worauf sich
  das Muster auflöst — ohne diese Anzeige wäre nicht abzusehen, was man
  gerade zugesagt hat.
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
    when world.isNight
}

worker overflow_dump {
    from storage
    to trash
    when storage.fillLevel > 0.9
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

### `storage` und `crafting` sind Schlüsselwörter

Das folgt aus der Entscheidung, keine Namen zu verbieten: Ein Spieler darf
einen Connector `storage` nennen. Wären die beiden bloß vorbelegte Namen,
bräuchte es eine neue Regel für den Konflikt. Als Schlüsselwörter greift die
vorhandene:

```
`storage`.insert(64 item:iron_ingot)   // der Connector des Spielers
storage.insert(64 item:iron_ingot)     // das Netzwerklager
```

---

## 8. Listen und Mengen

```
crushers.members().where(it.busy).count()
storage.items().sort(it.amount).first()
```

`it` ist das jeweilige Element. Das spart die Pfeilschreibweise
(`m => m.busy`), die für Spieler ohne Programmiererfahrung die größte Hürde
wäre.

Vorgesehen sind:

```
where    aussortieren
sort     ordnen
first    das erste Element
count    zählen
sum      aufaddieren
```

Mehr nicht — kein `map`, kein `groupBy`. In einer Fabrik gibt es dafür bisher
keinen Fall, und hinzufügen lässt sich später leicht, wegnehmen nicht.

**Verschachtelt braucht `it` einen Namen.** Zwei ineinandergeschachtelte
`where` können sich nicht dasselbe `it` teilen:

```
crushers.members().where(m => storage.count(m.input) > 0)
```

Innen wird also doch benannt. Das ist der Grund, warum die Pfeilschreibweise
nicht ganz verschwindet — sie ist nur nicht mehr der Normalfall.

---

## 9. Warten und Nebenläufigkeit

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

### Zeitangaben

Zeit wird mit Einheit geschrieben:

```
sleep 5s
await BatchFinished where id == jobId timeout 30s
```

Einheiten sind `t` für Ticks, `s`, `min` und `h`. Gerechnet wird intern immer
in Ticks; `1s` sind 20 davon. Bruchteile sind erlaubt, solange sie aufgehen —
`0.5s` sind 10 Ticks, `0.1s` meldet der Übersetzer als nicht darstellbar,
statt still zu runden.

Zusammensetzungen wie `1h30min` gibt es nicht. Wer sie braucht, schreibt
`90min`.

**Zeit ist ein eigener Typ, keine Zahl.** `sleep(30)` ist deshalb ein Fehler
und kein Rätsel: Ob 30 Ticks oder 30 Sekunden gemeint sind, ist nicht zu
erraten, und ein Faktor 20 fällt im Betrieb erst spät auf.

---

## 10. Wenn etwas schiefgeht

**Erwartbare Zustände sind keine Fehler. Unerwartete halten den Ablauf an.**

Erwartbar ist, was im Betrieb dauernd vorkommt: Das Ziel ist voll, die Quelle
leer, die Maschine gerade beschäftigt. Darauf antwortet die Sprache mit
Rückgabewerten und Abfragen, nicht mit Fehlern — ein Worker, der bei vollem
Ziel eine Meldung schriebe, hätte das Terminal in Minuten zugeschüttet.

```
let bewegt = move item:iron_ore from chest to crusher_1   // 0 ist normal
if crusher_1.busy { ... }
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

## 11. Grenzen

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

## 12. Dateien

Ein Projekt besteht aus `.mf`-Dateien im Projektbaum. **In der ersten Fassung
bilden alle zusammen einen Namensraum**: Was in einer Datei steht, ist in
allen sichtbar, und Dateien sind reine Ordnung für den Menschen.

`import` ist als Schlüsselwort reserviert, tut aber noch nichts.

Begründung: Echte Module lohnen sich erst, wenn ein Projekt einen Namensraum
sprengt, und das ist bei einer Fabrik nicht abzusehen. Das Wort jetzt zu
reservieren kostet nichts; es später einzuführen, ohne es reserviert zu haben,
bräche jedes Projekt, in dem jemand eine Funktion `import` genannt hat.

---

## 13. Was noch fehlt

Die acht Punkte, mit denen diese Datei begann, sind geklärt. Was hier fehlt,
ist nicht offen im Sinne von unentschieden, sondern schlicht noch nicht
beschrieben:

1. **`group`** — in Abschnitt 2 gezeigt (`members`, `strategy`), aber nie
   festgelegt. Verhält sich nach außen wie ein Gerät.
2. **`multiblock`** — im Konzept vorhanden, in der Sprache noch nicht.
3. **`event` und `on`** — beide werden benutzt, aber welche Ereignisse es
   gibt und wie eigene deklariert werden, steht nirgends.
4. **Displays** — im Konzept ein eigenes Kapitel, sprachlich unberührt.

Diese vier gehören zusammen: Es sind die restlichen Deklarationsformen. Sie
folgen demselben Muster wie `worker` — benannt, mit Angaben in geschweiften
Klammern — und sollten in einem Zug beschrieben werden, damit sie sich nicht
auseinanderentwickeln.
