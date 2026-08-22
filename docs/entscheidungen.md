# Programmable Factory Network Mod — getroffene Entscheidungen

Arbeitsnotiz zum Entwurfsgespräch. Wandert mit, sobald das eigene Repository
steht. Ergänzt [mc_factory_network_mod_concept.md](mc_factory_network_mod_concept.md);
bei Widersprüchen gilt diese Datei, weil sie jünger ist.

Stand: 2026-08-19

---

## Rahmen

**Eigenständige Mod, eigenes Repository.** Kein Fork. AE2SFM bleibt, was es
ist — ein SuperFactoryManager-Fork mit dessen Historie und MPL-Lizenz. Die
neue Mod erbt davon nichts.

**Minecraft 1.21.1, NeoForge.** Dort liegt das Gros der Tech-Mods, die das
Konzept als Kompatibilitätsziele nennt: Mekanism, Create, Thermal. Ohne sie
gibt es nichts zu automatisieren. 26.1 wäre technisch angenehmer
(unobfusziert), hat aber die Maschinen noch nicht.

**Das Konzeptdokument ist Diskussionsgrundlage, keine Vorgabe.** Widerspruch
ist erwünscht, aber unvoreingenommen — nicht gemessen an AE2 oder SFM.

---

## Umfang der ersten Fassung

**Die erste spielbare Fassung enthält Netzwerk, Speicher, Terminal und Sprache
gemeinsam.** Ausdrücklich gegen einen kleineren Schnitt entschieden.

Begründung des Projektinhabers: Der Reiz liegt darin, dass es etwas in diesem
Umfang noch nicht gibt. Eine Teilfassung, die sich noch nicht nach der Vision
anfühlt, würde diesen Reiz nicht bedienen.

**Das damit eingegangene Risiko, ausdrücklich benannt:** Auf einer Strecke von
Monaten ohne spielbares Ergebnis stirbt ein Projekt selten an einem
technischen Problem, sondern daran, dass nichts zurückkommt.

Gegenmaßnahme, da kleinere Etappen abgelehnt sind: Sichtbarkeit unterwegs
herstellen. Automatisierte Tests, die im Spiel laufen und die man ansehen
kann, statt bloß grüner Zahlen. Kreativmodus-Aufbauten, in denen einzelne
Teile funktionieren, bevor das Ganze fertig ist.

---

## Laufzeitmodell

**Wartender Code überlebt einen Serverneustart.** Ein Ablauf, der mitten in
einer Funktion auf ein Ereignis wartet, macht nach dem Neustart genau dort
weiter.

Zunächst war das Gegenteil entschieden und wurde revidiert. Die Wahl ist die
teurere, aber sie ist der Grund, aus dem die Mod etwas kann, das es sonst
nicht gibt: ComputerCraft verliert bei einem Serverneustart den Zustand seiner
Computer und startet die Programme neu. Ein Ablauf, der über Tage läuft und
Neustarts einfach übersteht, existiert in keiner bekannten Minecraft-Mod.

**Was das für die Runtime bedeutet:** Sie kann kein gewöhnlicher Interpreter
sein, der Javas eigenen Aufrufstapel benutzt. Sie braucht einen eigenen,
expliziten Stapel aus Frames — lokale Variablen, Programmzähler, Aufrufkette —,
der sich vollständig serialisieren und wiederherstellen lässt. Bekannte
Technik, aber sie muss von der ersten Zeile an so gebaut sein; ein Interpreter
lässt sich nachträglich nicht in eine solche Maschine verwandeln.

### Deploy bei wartendem Code

**Der Spieler wird gefragt.** Ändert ein Deploy eine Funktion, in der gerade
ein Ablauf wartet, zeigt das Terminal die betroffenen Abläufe und lässt die
Wahl: abbrechen oder mit der alten Fassung zu Ende laufen lassen.

Begründung: Nur der Spieler weiß, ob ein bestimmter wartender Ablauf gerade
wichtig ist. Ein festes Verhalten wäre in der einen Hälfte der Fälle falsch.

Technisch verlangt das beides — Versionierung des Codes, damit alte Fassungen
weiterlaufen können, und einen sauberen Abbruchweg. Der teure Teil davon ist
die Versionierung, und die wird ohnehin gebraucht.

**Noch zu klären:** Was geschieht, wenn ein Deploy ohne anwesenden Spieler
ausgelöst wird — es braucht ein Standardverhalten. Und bei vielen betroffenen
Abläufen sollte eine Entscheidung für alle möglich sein, statt einzeln zu
fragen.

---

## Sprache

**Zweigeteilt: deklarativ für Dauerhaftes, imperativ für Abläufe.**

Worker, Gruppen und Multiblocks werden deklariert — man beschreibt einen
Sollzustand, das System hält ihn. Funktionen und Ereignisbehandlung sind
imperativ, mit Bedingungen und Schleifen.

Das entspricht der Mischung, die die Beispiele im Konzept bereits zeigen.
Vorteil: Der Dauerbetrieb bleibt beschreibend und damit für das System
optimierbar, während Sonderfälle frei programmierbar sind. Genau das, was
Abschnitt 12 fordert — Ereignisse statt Polling —, ohne es dem Spieler als
Disziplin aufzubürden.

**Typen: ja, aber hergeleitet statt hingeschrieben.**

Das System kennt Typen und prüft sie vor dem Deploy, leitet sie aber selbst
her. `let count = storage.count(iron)` genügt; dass daraus eine Zahl wird,
weiß der Compiler. Angegeben werden Typen nur dort, wo sie nicht herleitbar
sind — Parameter von Funktionen und Ereignissen.

Begründung: Die IDE-Zusagen aus Abschnitt 14 — Autovervollständigung,
Hover-Infos, Meldungen wie „Unknown connector: cruhser_1, did you mean
crusher_1?" — setzen voraus, dass das System vor dem Ausführen weiß, was ein
Ausdruck bedeutet. Vollständige Typangaben wären für die Zielgruppe zu viel
Schreibarbeit; ganz ohne Typen bliebe die Autovervollständigung ein
Rateverfahren.

---

## Offen, in dieser Reihenfolge zu klären

1. **Grenzen für Nutzercode auf dem Server** (Konzept 22.9) — durch die
   Entscheidung für persistente Abläufe dringender geworden: Wer Abläufe über
   Tage am Leben halten kann, kann auch beliebig viele davon erzeugen. Nötig
   sind Grenzen für Rechenzeit je Tick, Speicher und Anzahl gleichzeitiger
   Abläufe. Lässt sich schwer nachrüsten.
2. **Konkrete Syntax** — der Charakter steht (siehe oben), die Schreibweise
   nicht.
3. **Speichermodell** — wie Bestände abgelegt und abgefragt werden, ohne bei
   jedem Zugriff alles zu durchlaufen. Die Messungen aus
   `ae2sfm/spike-me-zugriff.md` gelten hier unverändert.
4. **Netzwerktopologie und Channels** — wie Kabel, Controller und Grenzen
   technisch funktionieren.
5. **Erkennung von Maschinen-Rezepten** (Konzept 22.5) — Voraussetzung für
   Autocrafting.

---

## Nachtrag nach der externen Prüfung (2026-08-19)

Das konsolidierte Konzept
(`programmable_factory_network_konsolidiertes_konzept.md`) ersetzt die frühere
Fassung. Es übernimmt die hier festgehaltenen Entscheidungen und verbessert
zwei davon.

**Verbessert gegenüber meinem Vorschlag:**

Abschnitt 18 verwirft die Idee, einen allgemeinen Aufrufstapel zu
serialisieren. Stattdessen übersetzt der Compiler suspendierbare Funktionen in
Continuations beziehungsweise Zustandsmaschinen und persistiert nur an
definierten Haltepunkten. Das ist die Technik hinter Kotlins Coroutinen und
C#'s async — schlanker und ohne allgemeine VM-Architektur.

Abschnitt 38 beantwortet die offene Umfangsfrage besser als beide zuvor
angebotenen Alternativen: ein **vertikaler Schnitt**, der alle Kernelemente
berührt, jedes aber minimal. Damit fühlt sich die erste Fassung nach der Vision
an, ohne Monate zu brauchen.

### Gefundener Widerspruch: Haltepunkte

Abschnitt 18 und Abschnitt 33 vertragen sich nicht.

Abschnitt 18 nennt als Haltepunkte ausschließlich `await`, `sleep`,
`request wait` und Worker-Übergaben. Abschnitt 33 verspricht ein Rechenbudget
je Tick und behauptet, damit könnten Endlosschleifen den Server nicht
einfrieren.

Eine Schleife ohne `await` hat aber keinen Haltepunkt:

```
while true {
    x = x + 1
}
```

Die Zustandsmaschine kann dort nicht anhalten, der Server steht.

**Auflösung — gehört in die Sprachspezifikation, weil sie den Compiler
betrifft:** Zwei Arten von Haltepunkten unterscheiden.

*Unterbrechbar* — zusätzlich an Schleifenrückkanten und Funktionsaufrufen. Der
Zustand bleibt im Arbeitsspeicher und muss nicht serialisierbar sein. Tritt oft
ein, muss deshalb billig sein. Das trägt das Rechenbudget.

*Persistierbar* — nur an `await` und Verwandten. Der Zustand geht auf die
Platte. Tritt selten ein, darf teurer sein. Das trägt die Neustartfestigkeit.

Ohne diese Trennung bleibt nur die Wahl zwischen überall persistierbaren
Zuständen (teuer, viel Serialisierungscode für Zwischenstände, die niemand
braucht) und ungeschützten Endlosschleifen.

---

## Zielumgebung: große Modpacks

**Die Mod wird in AllTheMods-artigen Packs gespielt.** Der Projektinhaber
spielt sie selbst; das ist der Kontext, für den entworfen wird.

Was daraus folgt, und zwar für mehr als nur die Schreibweise:

- **Zwanzigtausend und mehr Gegenstandsarten** in der Registry. Jede Abfrage,
  die über alle Einträge läuft, ist damit eine Fehlerquelle. Die Messung in
  `referenz-messung-speicherzugriff.md` beschreibt keine theoretische Grenze
  mehr, sondern den Normalfall.
- **Namenskollisionen sind die Regel.** `steel_ingot` existiert in einem
  großen Pack mehrfach. Eine Schreibweise ohne Namensraum ist nur dort
  tragfähig, wo der Compiler die Mehrdeutigkeit meldet.
- **Namen sind lang und redundant.** `allthemodium:allthemodium_ingot`
  wiederholt den Modnamen; Legierungen heißen
  `vibranium_allthemodium_alloy_ingot`. Code, der solche Namen ausschreibt,
  wird unlesbar.
- **Tags sind wichtiger als Einzelnennungen.** Wer in einem großen Pack alle
  Erze verarbeiten will, kann sie nicht aufzählen.
- **Autovervollständigung muss auswählen, nicht auflisten.** Aus
  zehntausenden Kandidaten ist eine alphabetische Liste wertlos.

## Schreibweise von Gegenständen

**Der Doppelpunkt trennt die Art, der Schrägstrich den Namensraum.**

```
item:iron_ingot                        -> minecraft:iron_ingot
item:allthemodium/allthemodium_ingot   -> allthemodium:allthemodium_ingot
fluid:mekanism/heavy_water
tag:c/ores
tag:forge/ingots/copper
```

Fehlt der Schrägstrich, ist `minecraft` gemeint.

Begründung: Die Art voranzustellen macht eindeutig, worum es geht, und ist gut
zu vervollständigen — das war die gewählte Variante. Würde der Doppelpunkt
zusätzlich den Namensraum trennen, trüge er zwei Bedeutungen und kollidierte
mit Typangaben (`fn craft(item: Item)`). Der Schrägstrich hält beides
auseinander und entspricht der Schreibweise, die das Konzept bei Tags
ohnehin verwendet.

---

## Belegfall AllTheOres (2026-08-19)

Die Annahmen über Modpack-Namen waren bis hierhin plausibel, aber nicht
belegt. Deshalb wurde [AllTheOres](https://github.com/AllTheMods/AllTheOres)
angesehen — die Mod, die in AllTheMods-Packs die Erze der übrigen Mods
vereinheitlicht. Sie ist der günstigste Testfall, weil sie nichts Exotisches
tut: Sie erzeugt genau das, was jedes Pack ohnehin hat.

**Wie sie aufgebaut ist.** Gegenstände entstehen nicht einzeln, sondern als
Matrix. Ein Material wird einmal angemeldet:

```java
public static final ATOIngotSet ALUMINUM = new ATOIngotSet("aluminum", ...);
```

Daraus werden erzeugt: fünf Erzblöcke für Stein, Deepslate, Nether, End und
Sonstiges; Rohform und Rohblock; Barren, Nugget, Stab, Zahnrad, Platte, Staub
und Block; für Mekanism zusätzlich Kristall, Scherbe, Klumpen und
Schmutzstaub; dazu eine geschmolzene Flüssigkeit samt Eimer und zwei
Chemikalien. Aus 31 solcher Materialsätze entstehen mehrere hundert
Registry-Einträge — aus einer einzigen Mod, deren erklärter Zweck das
Aufräumen fremder Erze ist.

**Was daraus für die Sprache folgt, ist mehr als eine Bestätigung.** Die
Namen sind nicht einheitlich gebaut:

```
aluminum_ingot            Form als Nachsilbe
deepslate_aluminum_ore    Steinart als Vorsilbe
raw_aluminum              Zustand als Vorsilbe
dirty_aluminum_dust       beides zugleich
molten_aluminum           andere Art (Flüssigkeit), gleicher Materialname
```

Drei Entscheidungen ergeben sich daraus unmittelbar.

### Platzhalter an jeder Stelle, auch mehrfach

`item:*_dust` und `item:mekanism/*` reichen nicht. Wer alle Formen eines
Materials ansprechen will, braucht `item:*aluminum*`; wer alle Erze über alle
Steinarten will, braucht `item:*_ore` und trifft damit sowohl `aluminum_ore`
als auch `deepslate_aluminum_ore`. Ein Platzhalter, der auf Anfang oder Ende
festgelegt ist, kann eine der drei Namensachsen — Form, Steinart, Zustand —
grundsätzlich nicht ansprechen.

Verworfen: nur Präfix- und Suffixmuster. Das wäre einfacher zu übersetzen,
scheitert aber am ersten realen Modpack.

### Muster durchsuchen alle Namensräume, literale Namen nicht

`item:iron_ingot` meint `minecraft:iron_ingot`, wie bisher festgelegt.
`item:*_dust` dagegen meint jeden Staub aus jeder Mod, nicht nur die aus
Vanilla.

Begründung: Ein literaler Name ist eine Nennung, ein Muster eine Suche. Eine
Suche, die stillschweigend bei Vanilla haltmacht, findet in einem Pack mit
dreihundert Mods fast nichts und wirkt wie ein Fehler der Mod. Wer die
Beschränkung will, schreibt `item:minecraft/*_dust`.

Verworfen: Einheitlichkeit um ihrer selbst willen, also `item:*/*_dust` für
den modübergreifenden Fall. Das ist die häufigere Schreibweise mit dem
größeren Rauschen zu belasten.

### `except` ist Teil der Auswahl, nicht eine Nachbesserung

`item:*_dust` fängt `dirty_aluminum_dust` mit — bei Mekanism kein fertiger
Staub, sondern ein Zwischenschritt der Verarbeitungskette. Ein Programm, das
„alle Stäube ins Lager" sagt, saugt damit die eigene Produktion leer, und der
Fehler zeigt sich erst im Betrieb.

Daraus folgt eine Anforderung an den Editor, die über Vervollständigung
hinausgeht: **Zu jedem Muster muss sichtbar sein, was es gerade trifft.** Ein
Muster über zwanzigtausend Einträge ist sonst nicht zu überblicken, und
`except` bleibt Raten.

### `chemical:` wird jetzt festgelegt, nicht später

AllTheOres erzeugt zu jedem Material zwei Mekanism-Chemikalien. Das Konzept
sieht Chemikalien erst in Phase 8 vor, aber die Schreibweise gehört zu den
Arten, die der Doppelpunkt trennt — sie nachträglich zwischen `item:` und
`fluid:` zu schieben, hieße alle Beispiele und die Grammatik anzufassen.
Festgelegt wird deshalb jetzt nur die Notation, nicht die Anbindung.

---

## Vier Festlegungen zur Sprache (2026-08-19)

Von den acht offenen Punkten der Sprachspezifikation sind vier geklärt. Es
sind die, an denen Grammatik und Laufzeit hängen; die übrigen vier betreffen
Komfort und können später kommen.

### Schlüsselwörter sind englisch, Spielernamen dürfen es nicht sein

`if`, `else`, `for`, `fn`, `worker`, `on`, `await`. Was der Spieler selbst
benennt — Connectoren, Variablen, Funktionen — darf jeden Buchstaben
enthalten, auch `ofen_süd`.

Begründung: Schlüsselwörter stehen unmittelbar neben Registry-Namen, und die
sind englisch. `wenn crusher_1.online` mischt zwei Sprachen in einer Zeile,
und die andere Hälfte der Zeile lässt sich nicht mitübersetzen. Dazu kommt,
dass AllTheMods-Packs international gespielt werden. Übersetzt werden
stattdessen die Fehlermeldungen und die Oberfläche des Editors — dort hilft
es, in der Sprache selbst schadet es.

Dazu gehört eine Regel, die leicht zu übersehen ist: **Namen werden nach
Unicode-Normalform NFC verglichen.** Die Texteingabe im Spiel liefert `ü` je
nach Herkunft als ein Zeichen oder als `u` mit angehängten Punkten. Ohne
Normalisierung gäbe es zwei Connectoren, die auf dem Bildschirm gleich
aussehen und es nicht sind.

Verworfen: deutsche Schlüsselwörter. Verworfen auch, Bezeichner auf ASCII zu
beschränken — der Name gehört dem Spieler.

### Namenskonflikte werden nicht verboten, sondern in Rückstriche gesetzt

Eine Maschine darf `for` heißen. Im Code steht dann `` `for`.insert(...) ``.

Begründung: Eine Liste verbotener Namen wächst mit der Sprache. Führt eine
spätere Fassung `match` ein, ginge jedes Netz kaputt, in dem eine Maschine so
heißt — auf einem Server, der seit Monaten läuft. Namen sind Spielstand,
Schlüsselwörter sind es nicht.

Der Kern der Lösung sind aber nicht die Rückstriche, sondern die Meldung: Wer
`for.insert(...)` schreibt, bekommt keinen Syntaxfehler, sondern den Hinweis,
was gemeint sein könnte und wie man es schreibt.

Verworfen: die Label-Gun Schlüsselwörter ablehnen zu lassen. Verworfen auch,
den Parser aus dem Kontext raten zu lassen (`for` gefolgt von einem Punkt ist
ein Gerät) — das funktioniert, bis die nächste Syntax dazukommt.

### Fehler halten den Ablauf an, statt ihn zu beenden

**Die Linie liegt zwischen erwartbar und unerwartet, nicht zwischen
schlimm und harmlos.** Volles Ziel, leere Quelle, beschäftigte Maschine: kein
Fehler, sondern Rückgabewert. Abgebauter Connector, gekapptes Kabel, falscher
Typ, überschrittene Grenze: Der Ablauf hält an und erscheint im Terminal mit
der Wahl abbrechen oder weiterlaufen.

Das ist bewusst dieselbe Mechanik wie nach einem Serverneustart, die weiter
oben festgelegt wurde. Ein zweites Fehlermodell danebenzustellen hieße, zwei
Dinge zu lernen, die dasselbe tun.

Drei Punkte, die dazugehören und sonst später schmerzen:

- **Ein entladener Chunk ist kein Bruch.** Der Worker pausiert und läuft
  weiter, ohne Meldung. Nur endgültiger Verlust ist ein Fehler.
- **Kein Fehlersturm.** Steht ein Ereignis-Handler wegen eines Fehlers, wird
  keine weitere Instanz gestartet. `redstone_changed` könnte sonst jeden Tick
  einen neuen angehaltenen Ablauf erzeugen und das Terminal unbenutzbar
  machen.
- **Innerhalb eines Ticks ist der Gerätezustand stabil.** Sonst wäre
  `if crusher_1.online { crusher_1.insert(...) }` eine Falle, weil zwischen
  Abfrage und Zugriff ein unterbrechbarer Haltepunkt liegt. Über eine
  Tickgrenze hinweg gilt die Zusage nicht — dann greift der Fehlerfall, und
  genau deshalb muss er anhalten statt abzustürzen.

Verworfen: stille Fehlschläge. In einer Fabrik ist eine Kette, die ohne
Meldung steht, kaum zu finden. Verworfen auch `try`/`catch` — für Spieler zu
viel Apparat für einen Fall, den das Terminal besser löst.

### Zeit ist ein eigener Typ

`5s`, `30s`, `100t`, `90min`. Einheiten sind `t`, `s`, `min`, `h`; gerechnet
wird intern in Ticks. Bruchteile nur, wenn sie aufgehen — `0.01s` ist ein
Fehler statt einer stillen Rundung. Zusammensetzungen wie `1h30min` gibt es
nicht.

Begründung: `sleep(30)` ohne Einheit ist mehrdeutig, und der Unterschied
zwischen 30 Ticks und 30 Sekunden ist Faktor 20 — das fällt im Betrieb erst
auf, wenn die Fabrik längst falsch läuft.

### Die Sprache heißt Manifold

Dateiendung `.mf`.

Ein Manifold ist ein Sammelrohr: Es verteilt einen Strom auf viele Wege und
führt Rückläufe wieder zusammen. Das ist genau das, was die Sprache mit
Material und Maschinen tut. Der Begriff ist technisch statt niedlich und
trägt als mathematische Mannigfaltigkeit einen zweiten Boden, der zum Anspruch
der Mod passt.

Verworfen: **Cog**. Kurz und fabrikhaft, aber Create besetzt „Cogwheel"
prominent und ist in AllTheMods-Packs praktisch immer dabei — eine Sprache
dieses Namens läse sich dort wie Create-Zubehör.

Verworfen: **Factory**. Risikofrei und selbsterklärend, trägt aber nichts.
Der Name steht in jeder Fehlermeldung, und „Factory: Zeile 4 — Unbekannter
Connector" sagt so viel wie „Fehler: Zeile 4".

Bekannter Vorbehalt: Im Java-Ökosystem gibt es ein Compiler-Plugin namens
Manifold. Für Spieler ist das unsichtbar, es macht nur die Websuche etwas
unschärfer.

---

## Die letzten drei Sprachpunkte (2026-08-19)

Damit ist die Spezifikation für die erste Fassung vollständig. Offen sind nur
noch die übrigen Deklarationsformen — `group`, `multiblock`, `event` — und die
sind nicht unentschieden, sondern noch nicht aufgeschrieben.

### Worker: `from` nennt eine Quelle, keine Betriebsart

`from` nimmt ein Gerät, eine Gruppe, `storage` oder `crafting`. Damit braucht
Vorratshaltung keine zweite Deklarationsform — sie ist derselbe Worker mit
`from crafting`.

Verworfen: getrennte Formen wie `worker transfer` und `worker maintain`. Sie
hätten dieselbe Sache zweimal beschrieben und jede spätere Angabe doppelt
gebraucht.

Pflicht sind `from` und `to`, alles andere hat eine Vorgabe.

Drei Festlegungen zu `maintain`, ohne die es mehrdeutig bleibt: Es gilt **pro
Zielgerät** (`to generators` mit `maintain 64` hält 64 in jedem Generator),
**pro Gegenstandsart** (`filter tag:c/coals` hält 64 von jeder Kohleart) und
**nur auffüllend** — ein Überschuss wird nicht zurückgeholt. Die zweite Regel
ist der Grund, warum die Musteranzeige im Editor keine Bequemlichkeit ist:
Ohne sie sagt niemand zu, was er zusagt.

`rate 32 per 8t` meint einen Stapel je Intervall, nicht vier Stück pro Tick.
Maschinen wollen den Stapel.

### `when` darf nur beobachtbare Zustände lesen

Redstone, Bestände, Gerätestatus, Tageszeit. Eine beliebige Rechnung wäre
auswertbar, aber nicht beobachtbar — die Runtime müsste sie in jedem Tick
wiederholen und hätte damit die Polling-Schleife zurück, gegen die Worker
überhaupt erfunden wurden. Das ist dieselbe Einsicht wie bei den Watchern im
Vorprojekt.

Ist die Bedingung falsch, geht der Worker in `WAITING_CONDITION`, die
Schwester des bereits festgelegten `WAITING_TARGET`. So steht im Terminal
nicht nur, dass ein Worker schläft, sondern warum.

### `storage` und `crafting` werden Schlüsselwörter

Folgt zwingend aus der Entscheidung, keine Namen zu verbieten: Ein Spieler
darf einen Connector `storage` nennen. Wären die eingebauten Geräte nur
vorbelegte Namen, bräuchte dieser Konflikt eine eigene Regel. Als
Schlüsselwörter greift die vorhandene Rückstrich-Regel ohne neuen Mechanismus.

### Listen: implizites `it` statt Pfeilschreibweise

```
crushers.members().where(it.busy).count()
```

Die Pfeilform (`m => m.busy`) ist für Spieler ohne Programmiererfahrung die
größte Hürde an Collections. Sie bleibt für den verschachtelten Fall, weil
sich zwei ineinanderliegende `where` kein `it` teilen können — dort wird doch
benannt.

Vorgesehen sind `where`, `sort`, `first`, `count`, `sum`. Verworfen: `map` und
`groupBy` — in einer Fabrik gibt es dafür bisher keinen Fall, und hinzufügen
lässt sich später leicht, wegnehmen nicht.

### Dateien: ein Namensraum, `import` reserviert

Alle `.mf`-Dateien eines Projekts teilen einen Namensraum; Dateien sind reine
Ordnung für den Menschen. `import` ist reserviert und tut noch nichts.

Begründung: Echte Module lohnen erst, wenn ein Projekt einen Namensraum
sprengt, und das ist bei einer Fabrik nicht abzusehen. Das Wort jetzt zu
reservieren kostet nichts — es später einzuführen, ohne es reserviert zu
haben, bräche jedes Projekt, in dem jemand eine Funktion `import` genannt hat.

### Nachträge aus dem Gegenlesen

Beim Durchsehen der fertigen Spezifikation gegen ihre eigenen Regeln fielen
zwei Widersprüche auf:

- **`world` gehört zu den Schlüsselwörtern.** `when world.is_night` setzt es
  in dieselbe Stellung wie `storage` und `crafting`, und derselbe Konflikt
  droht: Ein Spieler darf einen Connector `world` nennen.
- **Nach einem Punkt gilt die Schlüsselwortliste nicht.** Sonst müsste
  `crushers.where(...)` in Rückstriche, weil `where` zum Warten gehört. Was
  hinter dem Punkt steht, vergibt das System, nicht der Spieler — dort ist ein
  Zusammenstoß ausgeschlossen.

Dazu eine Konvention, die vorher unausgesprochen schwankte: **Eingebaute
Namen sind snake_case**, wie die Registry-Namen, neben denen sie stehen. Aus
`fillLevel` wurde `fill_level`, aus `isNight` wurde `is_night`.

---

## Die restlichen Deklarationsformen (2026-08-20)

Damit ist die Sprache vollständig beschrieben. Vier Formen kamen dazu, und
drei Widersprüche zum Konzept mussten dabei aufgelöst werden.

### Gerätemuster in Gruppen lösen sich zur Laufzeit auf

Bei Gegenständen werden Muster beim Übersetzen festgeschrieben — bei
Connectoren nicht. `members furnace_*` nimmt einen neuen Ofen auf, sobald er
im Netz ist, ohne dass jemand den Code anfasst.

Begründung: Der Grund für das Festschreiben bei Gegenständen war die Menge —
zwanzigtausend Einträge darf man nicht pro Tick durchsuchen. Connectoren gibt
es dutzendweise. Und wer einen Ofen aufstellt, will ihn nicht zweimal
anmelden.

Gestrichen: die Strategie `balanced` aus dem Konzept. Ihre Bedeutung ließ sich
nicht von `least_filled` unterscheiden, und was niemand erklären kann, wählt
auch niemand bewusst.

### Multiblocks sind Vorlagen, Instanzen entstehen in der Welt

Im Code steht, welche Rollen eine Anlage hat und was sie kann. Gebaut wird sie
in der Welt, beliebig oft, und die Connectoren aller Instanzen dürfen dieselben
Namen tragen.

Begründung: Wer drei Erzanlagen baut, will sie nicht dreimal programmieren.
Ohne die Trennung steht dieselbe Logik dreimal im Code und geht dreimal
auseinander.

Was in `devices` steht, ist intern; was `fn` ist, ist die Schnittstelle. Eine
dritte Angabe für „öffentlich" braucht es nicht, weil die Trennung mit der
zwischen Gerät und Funktion zusammenfällt.

Fehlt einer Instanz ein Gerät, nimmt sie keine Aufrufe an, statt mitten im
Ablauf aufzulaufen.

### Kein Ereignis für Bestandsänderungen

Die eingebauten Ereignisse decken Redstone, Geräte und Fertigung ab. Ein
`storage_changed` gibt es bewusst nicht: In einem Lager mit zwanzigtausend
Arten feuert es im Sekundentakt. Wer auf Bestände reagieren will, nimmt einen
Worker mit `when` — den weckt das System genau dann, wenn es nötig ist.

### Displays rechnen nicht

Ein Display nennt Werte; wann sie geholt werden, entscheidet das System. Keine
Schleife, kein `await`. Nur so kann es beobachten statt in jedem Tick zu
zeichnen — und in einem großen Netz hängen schnell dreißig an der Wand.
`button` ist die Ausnahme und auch nur einseitig: Er zeigt nichts, er löst aus.

### `try`/`catch` entfällt, `timeout` bekommt ein `else`

```
let ergebnis = await BatchFinished where id == jobId timeout 30s else {
    notify("Maschine antwortet nicht")
    return
}
```

Der `else`-Zweig muss den Ablauf verlassen; danach gilt `ergebnis` als
vorhanden. Das Konzept sah `try`/`catch Timeout` vor — dies ist der einzige
Fall, in dem es gebraucht würde, und dafür ist ein zweiter Block mit eigener
Fangregel zu viel. Alles andere hält den Ablauf an und landet im Terminal.

### Nachgezogen im Konzept

- Das Collections-Kapitel nannte `filter`, `map` und `groupBy`. `filter` heißt
  jetzt `where`, weil `filter` beim Worker schon die Auswahl der Gegenstände
  bezeichnet; `map` und `groupBy` sind gestrichen.
- `network`, `workers` und `multiblocks` sind aus demselben Grund
  Schlüsselwörter wie `storage` und `crafting`.
- Redstone fehlte in der Sprachspezifikation ganz, obwohl das Konzept ein
  eigenes Kapitel dafür hat.

---

## Der Parser wird von Hand geschrieben (2026-08-20)

Kein ANTLR, kein Generator. Die Grammatik steht als EBNF in `grammatik.md` und
ist die Vorlage — aber der Code entsteht daraus von Hand als
Recursive-Descent-Parser.

Begründung: Fehlermeldungen sind in Manifold kein Nebenprodukt, sondern ein
Versprechen. „`for` ist ein Schlüsselwort. Meinst du den Connector gleichen
Namens?" lässt sich mit einem Generator nur mühsam erreichen, weil er an der
Fehlerstelle nur weiß, welche Token dort erlaubt gewesen wären. Dazu kommt die
IDE: Sie muss in unfertigem Code vervollständigen, also nach einem Fehler
weiterlesen können. Fehlerbehebung ist die Disziplin, in der handgeschriebene
Parser am deutlichsten vorne liegen.

Zweiter Grund, kleiner: Ein Generator bringt eine Laufzeitbibliothek mit, die
ins Mod-Jar muss.

Verworfen: ANTLR. Das Vorprojekt nutzt es, das Muster wäre bekannt gewesen und
der Weg zur ersten lauffähigen Fassung kürzer. Der Preis wäre genau die
Eigenschaft, wegen der diese Mod gebaut wird.

Die Grammatik bleibt trotzdem geschrieben und gepflegt. Sie ist die
Beschreibung, gegen die der Parser geprüft wird — sie hat beim Aufschreiben
zwei Lücken aufgedeckt, die in der Prosa nicht auffielen: dass `timeout` ohne
`else` keinen sinnvollen Wert hinterlässt, und dass eine vorangestellte Menge
bei `move` etwas anderes bedeutet als bei `maintain`.

---

## Die Label-Gun (2026-08-20)

**Das Bedienmodell folgt SuperFactoryManager, das Datenmodell ist umgedreht.**
Dort trägt die Gun die Namen und der Manager holt sie sich; hier trägt sie der
Connector, und die Gun zeigt nur darauf. Der Code ist eigenständig — das
Vorbild ist die Bedienung, nicht die Umsetzung.

Aus der Umkehrung folgt zweierlei:

- **Kein Übertragen zwischen Gun und Controller.** In SFM gibt es Push und
  Pull, weil die Daten in der Gun leben und irgendwann in den Manager müssen.
  Hier steht der Name im Connector, und der Controller liest ihn beim Aufbau
  des Graphen. Was in der Gun steckt, ist eine **Zwischenablage, keine
  Datenbank** — die zuletzt benutzten Namen, zum Durchblättern.
- **Keine Ansichtsmodi.** SFM hat drei davon und ein Overlay, das erklärt, in
  welchem man gerade ist. Das ist der Preis dafür, dass man den Namen eines
  Blocks sonst nicht sieht. Hier schweben die Namen über den Connectoren,
  solange die Gun in der Hand ist, und zwar alle: benannte grün, unbenannte
  grau, doppelt vergebene rot.

### Die Gun wird mit dem Netz verknüpft

Rechtsklick auf den Controller verbindet sie. Danach kennt sie alle Namen des
Netzes und kann durchnummerieren und vor Konflikten warnen.

Verworfen: den Controller bei jedem Klick in der Umgebung suchen. Bei einem
Umkreis, der für ein gewachsenes Netz reicht, sind das über fünfzigtausend
Blockpositionen — pro Klick. Das war die erste Fassung und ist genau die Art
Fehler, die man in einer Testwelt mit fünf Blöcken nie bemerkt.

### Namen werden vorgeschlagen und durchnummeriert

Zeigt die Gun auf einen Connector ohne bereitgelegten Namen, schlägt sie einen
aus der Maschine dahinter vor: aus `minecraft:blast_furnace` wird
`blast_furnace_1`, beim nächsten `blast_furnace_2`.

**Die Nummer kommt aus dem Netzwerk, nicht aus der Gun.** Sonst vergäben zwei
Spieler mit zwei Guns denselben Namen, und wer seine Gun neu herstellt, finge
wieder bei eins an. Das ist der Punkt, an dem SFM Arbeit macht: Dort tippt man
jeden Namen selbst.

### Zwei gleiche Namen machen beide unbrauchbar

Vorher gewann im Graphen still der zuletzt gefundene — die Reihenfolge der
Suche entschied also, welche Maschine gemeint war. Jetzt zeigt ein doppelter
Name auf keinen von beiden, erscheint im Terminal als Fehler und in der Welt
rot.

Die Gun vergibt einen belegten Namen gar nicht erst, sondern nennt den
nächsten freien. Verworfen: das Umbenennen erzwingen, indem die Gun den alten
Connector stillschweigend entnennt — dann verschwindet ein Name aus einem
laufenden Programm, ohne dass jemand es merkt.

**Abgrenzung:** Das Konzept erlaubt gleiche Namen *innerhalb* verschiedener
Multiblock-Instanzen (Kapitel 23.1). Diese Regel gilt für ein Netzwerk; wenn
Multiblocks kommen, braucht die Prüfung eine Grenze entlang der Instanz.

### Die Gun prüft, was die Sprache verlangt

Sie ist der Ort, an dem Namen entstehen — was hier durchgeht, steht später im
Code. Deshalb normalisiert sie nach NFC wie der Übersetzer und lehnt ab, was
kein Bezeichner sein kann.

Ein Name, der ein Schlüsselwort ist, wird **nicht** abgelehnt, sondern nur
angemerkt: „`for` braucht im Code Rückstriche." Das folgt der Entscheidung,
keine Namen zu verbieten — Namen sind Spielstand, Schlüsselwörter sind es
nicht.

---

## Die Oberflächen (2026-08-20)

**Ein Fenster mit Reitern, nicht eine Oberfläche je Block.** So steht es im
Konzept, und es hat einen praktischen Grund: Der Speicher gehört zum Netz,
nicht zu einem Block, und wer ihn über drei verschiedene Fenster erreicht,
muss sich merken, welches welches ist.

### Inventare bleiben Minecraft, der Editor nicht

Gehäuse, Reiter, Slotraster und die Lage des Spielerinventars sind Vanilla —
dieselben Grautöne, dieselben Maße, dieselben Stellen. Ein Inventar soll sich
anfühlen wie jedes andere; was daran eigen ist, verwirrt nur.

Der Code-Reiter ist die Ausnahme: ein dunkler Bildschirm, versenkt ins helle
Gehäuse. Er ist kein Inventar, sondern ein Gerät. ComputerCraft macht seinen
Terminalschirm aus demselben Grund schwarz.

Verworfen: alles eigen zu zeichnen. Ein echter `Slot` bringt Umschalt-Klick,
Ziehen über mehrere Felder, Zahlentasten und Doppelklick-Sammeln mit. Wer das
Raster selbst malt, baut all das nach, und was fehlt, fühlt sich für den
Spieler kaputt an.

**Ein Preis:** Eigene Oberflächentexturen ziehen mit Resource Packs nicht mit.
Wer sein Spiel auf ein anderes Pack umstellt, sieht überall neue Texturen —
nur unser Terminal bleibt.

### Der Netzbestand kann keine Slots sein

Zwanzigtausend Arten lassen sich nicht als zwanzigtausend Slots anlegen. Die
Felder sehen deshalb aus wie Slots und verhalten sich wie sie, sind aber
gezeichnet und werden über eigene Nachrichten bedient. Das ist kein
Stilentscheid, sondern ein Zwang — Applied Energistics löst es genauso.

### Der Bestand wird gebündelt geschickt, nicht bei jeder Änderung

Beim Öffnen einmal vollständig, danach höchstens alle zehn Ticks die
Änderungen. Ein Worker, der in jedem Tick etwas bewegt, darf nicht in jedem
Tick ein Paket auslösen.

Geschickt wird nur an Spieler, die den Speicher-Reiter offen haben. Wer Code
liest, braucht keine Bestandsänderungen — deshalb ist der Reiterwechsel die
einzige Sache am Reiterzustand, die der Server überhaupt erfährt.

### **Der Server rechnet bei jeder Entnahme nach**

Was der Client anzeigt, ist ein Schnappschuss von vorhin; in der Zwischenzeit
kann ein Worker den Bestand geleert haben. Die angeforderte Menge wird deshalb
nie geglaubt, sondern gegen den echten Bestand geprüft. Ohne diese Regel
entstehen die Fehler, an denen sich vergleichbare Mods lange abgearbeitet
haben.

### Höchstens 4096 Arten auf einmal

**Das ist eine im Spiel sichtbare Grenze**, und sie trifft genau die Packs, um
die es geht. Ein Bestand mit mehr Arten wird abgeschnitten übertragen, und die
Fußleiste sagt es: „zeige 4096 von 21.000 — suchen zeigt mehr."

Der Grund: Gesucht wird auf dem Client, damit es sich sofort anfühlt. Das
setzt voraus, dass der Bestand dort liegt — und zwanzigtausend Einträge bei
jedem Öffnen zu übertragen, würde man merken. Die Zahl steht als Konstante in
`StorageSnapshotPacket`, nicht verstreut im Code.

Offen: ob die Suche bei abgeschnittenem Bestand zusätzlich auf dem Server
laufen soll. Das wäre die saubere Lösung, kostet aber eine Anfrage je
Tastendruck.

---

## Farbige Kabel (2026-08-20)

**Die Farbe entscheidet, was sich verbindet — sie ist nicht nur Anstrich.**
Zwei Kabel verschiedener Farbe laufen aneinander vorbei, ohne sich zu sehen.
Die Standardfarbe verbindet sich mit allem.

Der Zweck ist genau der, den Applied Energistics damit verfolgt: mehrere Netze
durch dieselbe Wand führen, jedes mit eigenen Kanälen. Würden sich farbige
Kabel verbinden, wären sie ein Netz — und dann bräuchte es keine Farben.

Dass die Standardfarbe alles annimmt, ist der Unterschied zwischen brauchbar
und lästig: Sonst müsste man sich schon beim ersten Kabel für eine Farbe
entscheiden und könnte zwei Stränge nie mehr zusammenführen.

**Auch der Graph beachtet die Farbe.** Das ist die Stelle, an der es sonst
falsch würde: Ein Strang, der sichtbar getrennt verläuft, aber im Netz doch
verbunden ist, wäre schlimmer als beides andere — man sähe die Trennung und
hätte sie nicht.

### Ein Block, siebzehn Gegenstände

Die Farbe steht im Blockzustand, nicht in einer BlockEntity: Sie ändert sich
nie und muss beim Zeichnen sofort da sein. Siebzehn eigene Blöcke wären
dieselbe Sache mit siebzehnfachem Aufwand.

Die Blockstate-Datei zählt die Kombinationen nicht auf — siebzehn Farben mal
vierundsechzig Verbindungen wären über tausend Varianten. Multipart setzt
stattdessen zusammen: Die Farbe wählt das Modell, die Verbindungen wählen die
Arme.

Gefärbt wird mit einem Farbstoff auf einem beliebigen Kabel. Dass das Rezept
über `c:cables` geht statt über die siebzehn Gegenstände einzeln, hält es bei
einem Rezept je Farbe statt bei zweihundertzweiundsiebzig.

**Entfärbt wird mit einem Wassereimer**, wie bei Applied Energistics. Ohne das
wäre ein gefärbtes Kabel eine Sackgasse — man käme nie wieder zu der
Standardfarbe zurück, die sich mit allem verbindet. Der Eimer bleibt als
leerer Eimer zurück; das macht Minecraft von selbst.

**Gefärbt wird nur die Röhre, nicht die Schelle.** Ein schwarzes Kabel sähe
sonst aus wie ein Loch in der Wand und ein weißes wie ein Fremdkörper; so
bleibt erkennbar, dass es dasselbe Bauteil ist.

### Mehrere Leitungen in einem Block: später

Nach dem Vorbild der EnderIO-Conduits — vier Leitungen im selben Blockraum,
im 2×2-Raster. Bewusst zurückgestellt: Dafür reicht kein statisches Modell
mehr, weil die Zahl der Kombinationen in die Millionen geht. Es bräuchte ein
zur Laufzeit zusammengebautes Modell und eigene Kollisionsboxen je Leitung,
damit sich eine einzelne herausbrechen lässt.

---

## Kabelbündel (2026-08-20)

**Bis zu vier Stränge in einem Block**, nach dem Vorbild der EnderIO-Conduits,
aber mit unseren Farben: Vier getrennte Netze passen durch dieselbe Wand, ohne
vier Blöcke breit zu werden.

### Ein einzelnes Kabel ist ein Bündel mit einem Strang

Es gibt bewusst keinen zweiten Blocktyp. Sonst müsste jede Stelle, die Kabel
anfasst, für immer zwei Fälle behandeln: der Netzwerkgraph, das Platzieren,
das Abbauen, jeder Test.

Dass das eine Migration dessen war, was am selben Tag entstanden ist, spricht
nicht dagegen, sondern dafür: Später wären Welten betroffen gewesen, die
jemand damit gebaut hat. Kabel aus der Zeit davor lesen ihre Farbe weiterhin
aus dem Blockzustand — der Konstruktor der BlockEntity übernimmt sie als
ersten Strang.

### Der Graph läuft über Position und Farbe, nicht über Position

Ein Bündelblock ist damit bis zu vier Knoten. Ohne diese Trennung liefe ein
grüner Strang über einen Block, in dem auch ein roter liegt, und beide wären
plötzlich verbunden — dieselbe Klasse von Fehler wie ein Strang, der sichtbar
getrennt verläuft und es doch nicht ist.

Der Controller ist farbneutral: Von ihm gehen alle Stränge aus, die an ihm
hängen. Connectoren ebenso — sie müssten sonst selbst gefärbt werden.

### Die Anzahl steht im Zustand, die Farben nicht

Vier Farbfelder mit je siebzehn Werten wären über hunderttausend
Blockzustände, für die Minecraft jeweils ein Modell backt. Im Zustand steht
deshalb nur, wie viele Stränge im Block liegen — vier Werte.

**Eingefärbt wird zur Laufzeit.** Jeder Strang hat im Modell seinen eigenen
Tintindex, und der Client liest die Farbe aus der BlockEntity. Nebeneffekt:
Eine einzige graue Kabeltextur reicht, wo vorher siebzehn dasselbe Bild in
siebzehn Tönen zeigten.

Verworfen: ein zur Laufzeit zusammengebautes Modell mit versetzten Quads. Es
wäre der allgemeinere Weg, aber die Einfärbung leistet hier dasselbe mit
Minecrafts eigenen Mitteln.

### Sechs Pixel allein, vier im Bündel

Ein einzelnes Kabel sieht aus wie bisher — kein Rückschritt für vorhandene
Bauten. Ab zwei Strängen werden alle vier Pixel dick; mehr passt nicht in
sechzehn, ohne dass sie sich berühren.

### Einzelne Stränge abbauen

Ein Schlag nimmt nur den Strang, auf den man zielt; erst der letzte nimmt den
Block. Das ist der Unterschied zwischen einem Bündel und vier Blöcken
nebeneinander: Man kommt an jeden Strang heran, ohne die anderen anzufassen.

Gezielt wird gegen die Trefferfläche jedes Strangs einzeln — Kern samt seiner
Arme. Trifft der Blick keine genau, nimmt der Schlag den ersten; das ist
besser als gar nichts zu tun.

**Die Geometrie steht an zwei Stellen**, im Modellskript und in Java, weil
Minecraft Modelle und Trefferflächen getrennt hält. Laufen sie auseinander,
greift der Spieler neben das, was er sieht — ein Fehler, den man im Spiel
spürt, aber schwer benennt. Deshalb liegen die Zahlen in `CableLayout`, einer
Klasse ohne jeden Minecraft-Bezug, und ein gewöhnlicher Test liest die
erzeugten Modelldateien und vergleicht sie. Er prüft auch, dass kein Strang
aus dem Block ragt und keine zwei sich überlappen.

### Was noch offen ist

Ob ein Bündel für die Kanäle als ein Kabel zählt oder als vier. Das ist keine
Modellfrage, sondern eine des Spielgleichgewichts — vier Stränge in einem
Block sind entweder eine Platzersparnis oder eine Verdopplung der Kapazität.
Kanäle gibt es bei uns ohnehin noch nicht.

---

## Kanäle (2026-08-20)

**Acht je Strang, nicht je Bündel.** Ein Strang ist ein Bündel von acht
Drähten; jedes Gerät zieht auf seinem ganzen Weg zum Controller einen davon
ab, und weiter hinten fehlt er dann. Das Vorbild ist Applied Energistics.

Der Unterschied zu dort: Bei uns zählt der Strang. Vier Stränge in einem Block
tragen vier mal acht, weil sie vier getrennte Netze sind — dieselbe Logik wie
bei den Farben.

### Ein Gerät weicht auf einen freien Strang aus

Daran wäre es fast gescheitert. Die erste Fassung nahm den erstgefundenen
Strang; war der voll, ging das Gerät leer aus, obwohl im selben Block ein
freier lag. Das wäre „je Bündel" gewesen, nicht „je Kabel".

Deshalb läuft die Zuteilung erst, wenn die Suche durch ist: Vorher weiß man
nicht, über welche Stränge ein Gerät überhaupt erreichbar ist. Der Test mit
zwei Strängen und zehn Geräten hat das aufgedeckt.

### Wer bei knappen Kanälen gewinnt

**Das nähere Gerät**, bei gleicher Entfernung das in der früheren Richtung —
die Breitensuche läuft in der Reihenfolge von `Direction.values()`.

Diese Regel muss feststehen und erklärbar sein. „Warum ist dieses Gerät
offline" ist die häufigste Frage an ein solches System, und eine Antwort wie
„das hängt von der Suchreihenfolge ab" ist keine.

### Kein Kanal ist ein eigener Zustand

Neben benannt, unbenannt und doppelt vergeben gibt es jetzt: hängt im Netz,
ist sichtbar, aber bekommt keinen Draht. Laufzeit und Controller melden das
getrennt — sonst sucht der Spieler einen Tippfehler, wo eine Kapazitätsgrenze
liegt.

### Die Zahlen gehören dem Graphen, nicht den Blöcken

Ein Kabel kann zu zwei Netzen gehören; schriebe jeder Controller seine Zahlen
in die BlockEntity, überschriebe einer den anderen. Dazu käme bei jedem
Neuaufbau ein Schreibvorgang je Kabel samt Übertragung zum Client.

Wer die Zahlen braucht, fragt umgekehrt: `ControllerRegistry` kennt die
Controller einer Welt, und der Graph sagt, ob eine Stelle zu ihm gehört.

### Anzeige über Jade

**Verworfen: die Belegung über die Helligkeit des Strangs zu zeigen.** Farbe
mal Helligkeit ist nicht mehr zu lesen — ist das ein helles Grün oder ein
belastetes Dunkelgrün? Und es überlädt die eine Achse, die bisher eindeutig
„welches Netz" bedeutet.

Jade beantwortet die Frage dort, wo sie entsteht: Man sieht das Kabel an und
liest je Strang, wie viele der acht Drähte belegt sind. Am Connector steht,
welcher der vier Zustände vorliegt; am Controller der Stand des ganzen Netzes.

Die Anbindung ist freiwillig: Ohne Jade fehlt nur die Anzeige. Die Klasse wird
gar nicht erst geladen, weil Jade selbst nach ihr sucht.

---

## Gruppen ausführen (2026-08-21)

Damit ist der Worker vollständig: `strategy` und `overflow` waren
spezifiziert, aber wirkungslos.

### Eine Verteilung liefert eine Reihenfolge, keine Wahl

Das ist der Punkt, an dem eine Gruppe mehr wird als eine umständliche
Schreibweise für ein Gerät: Ist das erste Mitglied voll, muss der Transfer
beim nächsten weitermachen können. Deshalb geben alle fünf Verfahren eine
vollständige Reihenfolge zurück, aus der die Laufzeit das erste nimmt, das
tatsächlich annimmt.

`first_available` und `priority` führen zum selben Ergebnis. Der Unterschied
liegt in der Absicht: Wer `priority` schreibt, meint eine Rangfolge; wer
`first_available` schreibt, meint „irgendeines, das kann". Beide Wörter zu
behalten kostet nichts und sagt beim Lesen mehr.

### Die Angabe am Worker geht der Gruppe vor

Dieselbe Gruppe kann von zwei Workern verschieden bedient werden — einer
reihum, einer nach dem leersten. Ohne diesen Vorrang bräuchte man zwei
Gruppen mit denselben Mitgliedern.

### Gruppen werden bei jedem Tick neu aufgelöst

Billig, weil es wenige Gruppen mit wenigen Mustern gibt. Und nötig: Ein Ofen,
der dazukommt, soll in seiner Gruppe landen, ohne dass jemand das Programm
erneut übernimmt. Der Zeiger von `round_robin` überlebt das — sonst finge die
Gruppe bei jedem Tick wieder beim ersten Gerät an.

Namen ohne Muster bleiben in der Gruppe, auch wenn sie gerade nicht im Netz
hängen: Ein Gerät in einem nicht geladenen Chunk gehört weiter dazu. Muster
können dagegen nur treffen, was da ist.

### overflow greift erst, wenn das Ziel wirklich voll ist

Vorher stand ein Worker bei vollem Lager still, und die Maschine davor lief
über. Jetzt geht der Überschuss ins Ausweichziel — aber nur dann, nicht
nebenbei.

---

## Displays (2026-08-21)

Ein flacher Block an der Wand, der zeigt, was im Netz vorgeht. Benannt mit
derselben Label-Gun wie ein Connector — es ist dieselbe Handlung: einem Block
sagen, wie er heißt. Der Name verweist auf eine `display`-Deklaration im
Programm.

### Ein Display rechnet nicht, es liest ab

Deshalb steht hinter ihm kein Interpreter, sondern eine kurze Liste dessen,
was ablesbar ist: ein Bestand, ein Workerzustand, eine Zahl, ein Gerätestatus.
Was nicht darin vorkommt, meldet das Display auf seiner eigenen Fläche.

Der Grund ist derselbe wie bei `when`: Nur was sich beobachten lässt, muss
nicht in jedem Tick neu ausgerechnet werden. An einer Wand hängen schnell
dreissig Displays.

### Gerechnet wird auf dem Server, gezeichnet auf dem Client

Über die Leitung gehen fertige Zeichenketten, keine Ausdrücke. Der Client soll
nicht wissen müssen, was `storage.count` bedeutet — und die Sprache nicht
zweimal existieren.

Aktualisiert wird einmal je Sekunde und nur bei Änderung übertragen. Ein
Display, dessen Zahlen stillstehen, erzeugt keine Pakete.

### Displays brauchen keinen Kanal

Sie nehmen dem Netz nichts weg, sie lesen mit. Bei Applied Energistics
verbrauchen Monitore einen Kanal; hier nicht, weil ein Display keine Ware
bewegt und niemand eine Wand voller Anzeigen mit Kanälen bezahlen sollte.

### Ein Display ohne Deklaration sagt es selbst

Eine leere Fläche ließe offen, ob das Netz steht oder der Name falsch ist.
Also steht dort „kein display <name>".

### Gefunden: ein Überlauf an drei Stellen

`lastRefresh = Long.MIN_VALUE` als Anfangswert sieht aus wie „noch nie
geschehen", ist aber ein Fehler: Die Differenz zur Spielzeit läuft über und
wird negativ, also feuert die Abfrage nie. Dieselbe Konstruktion steckte auch
im Controller — beim Neuaufbau des Netzes und beim Verschicken des Bestands.
Dort fiel es nicht auf, weil beides zusätzlich von Hand angestoßen wird.

---

## Abläufe, die warten (2026-08-21)

### Der Zustand steht in Rahmen, nicht in Javas Aufrufstapel

Eine Funktion, die `await` enthält, kann nicht als gewöhnlicher Java-Aufruf
laufen: Ein Aufrufstapel lässt sich nicht aufschreiben. Also führt eine eigene
Maschine sie aus, die Rahmen auf einen Stapel legt — Block, Zähler, Variablen.
Das sind Daten, und Daten überstehen einen Serverneustart.

**Verworfen: den Übersetzer Zustandsmaschinen erzeugen lassen.** Der übliche
Weg für `async`/`await` in Sprachen, die auf eine fremde Laufzeit müssen. Er
hätte jede Anweisung ein zweites Mal gebraucht — einmal für den geraden Weg,
einmal für den zerlegten. Zwei Fassungen derselben Bedeutung laufen
auseinander, und zwar an der Stelle, an der ein Spieler es am wenigsten
nachvollziehen kann.

Stattdessen liefert der Interpreter für jede Anweisung einen **Schritt**: was
sie tut, steht an einer Stelle; wie es weitergeht, entscheidet der Aufrufer.
Der gewöhnliche Weg ruft sich selbst auf, der Ablauf legt einen Rahmen ab.
Gegenstände bewegt beides mit demselben Code.

### Nur das Erlebte wird aufgeschrieben

`where`, der `else`-Zweig, der Rumpf einer Schleife — all das steht im Programm
und kommt von dort zurück. Auf die Platte gehört nur, was der Ablauf selbst
erlebt hat: wo er steht, was er in Händen hält, worauf er wartet.

Wiederfinden lässt sich die `await`-Anweisung dabei über den Zähler des
Rahmens: Er zeigt beim Warten schon eine Stelle weiter, damit es nach dem
Aufwachen dahinter weitergeht — die Anweisung selbst liegt also davor.

### Blöcke bekommen Nummern, keine Pfade

Ein Rahmen muss sagen können, in welchem Block er steht. Ein Pfad aus
Anweisungsnummern reicht dafür nicht: `if a { … } else { … }` hat zwei Blöcke
an derselben Nummer, und eine `else if`-Kette macht es schlimmer.

Also durchläuft ein Index das Programm in fester Reihenfolge und nummeriert
jeden Block. Aus der Nummer wird beim Laden wieder derselbe Block. Das ist
zugleich einfacher und deckt die Verzweigungen ohne Sonderfall ab.

### Was zählt, ist die Gestalt des Programms, nicht sein Wortlaut

Ob ein Ablauf nach einer Änderung noch auf dieselben Stellen zeigt, entscheidet
eine Zahl über Anzahl und Art der Anweisungen — dazu die Namen erwarteter
Ereignisse.

**Verworfen: ein Hash des Quelltextes.** Damit hätte ein hinzugefügter
Kommentar jeden wartenden Ablauf zur Nachfrage gezwungen, obwohl er weiterlaufen
könnte. Die Gestalt ist die ehrliche Bedingung: Sie ändert sich genau dann,
wenn sich die Nummern verschieben.

Eine geänderte Rechnung im Rumpf verschiebt nichts — der Ablauf läuft dann
weiter und rechnet ab hier neu. Das ist gewollt: Wer eine Zahl anpasst, will,
dass es sofort gilt.

### Ein geändertes Programm nimmt den Weg des Neustarts

Beim Übernehmen wird die Maschine aufgeschrieben, weggeworfen und
zurückgelesen. Ein Weg statt zwei, die auseinanderlaufen — und derselbe Test
deckt beide Fälle ab.

### Ereignisse werden zwischen den Schritten ausgeliefert

Ein `emit` kann mitten in einem Ablauf stehen. Sofort auszuliefern hieße,
denselben Stapel anzufassen, auf dem gerade gearbeitet wird. Also wartet das
Ereignis bis zwischen zwei Schritten; für den Spieler bleibt es derselbe Tick.

Acht Runden dürfen in einem Tick aufeinander folgen. Zwei Abläufe, die sich
gegenseitig wecken, würden den Server sonst stehen lassen. Was übrig bleibt,
wartet auf den nächsten Tick, statt verworfen zu werden.

### Der else-Zweig verlässt den Ablauf, auch wenn dort nichts steht

Die Sprache verlangt, dass er es tut. Die Maschine verlässt sich nicht darauf,
sondern merkt es sich am Rahmen: Wird er verlassen, endet der Ablauf. Sonst
stünde nach einer abgelaufenen Frist ein Wert da, den es nie gab — genau das,
wogegen der Zweig eingeführt wurde.

### Eine Variable darf sich nicht heimlich verwandeln

Fehlt der Gegenstand einer Variablen, weil eine Mod aus dem Pack genommen
wurde, scheitert der Ablauf mit klarer Meldung. Der Netzwerkspeicher überspringt
solche Posten still — dort ist es der Normalfall und niemand rechnet mit ihnen
weiter. In einer Variablen steckt eine Rechnung, und die still zu verändern
wäre schlimmer als sie anzuhalten.

### `for` behält seinen Rahmen, `while` nicht

Bei `while` steht die Bedingung im Programm und wird jede Runde neu geprüft.
Bei `for` gibt es nichts dergleichen — der Stand des Laufs steht nur im Rahmen
und muss deshalb dort bleiben und mit auf die Platte.

Das ändert auch, was `continue` tut: Bei `while` reicht es, den Rumpf zu
verlassen, damit die Bedingung erneut greift. Bei `for` würde das die Liste neu
auswerten und den Lauf von vorn beginnen lassen.

### Gescheiterte Abläufe bleiben liegen

Die letzten zehn, samt Grund. Ein Ablauf, der stirbt, verschwand sonst aus der
Liste und mit ihm die Erklärung. Wer nachts eine Anlage baut, sieht am nächsten
Morgen sonst nur, dass nichts passiert ist.

---

## Multiblocks in der Welt (2026-08-21)

### Eine Anlage entsteht aus den Namen ihrer Connectoren

`werk_1/eingang` gehört zur Anlage `werk_1` und spielt dort die Rolle
`eingang`. Wer drei Connectoren mit der Beschriftungspistole benennt, hat eine
Anlage — kein neuer Block, keine Zuordnungsmaske, kein abgesteckter Bereich.

Das erlaubt, was in großen Packs der Normalfall ist: Die Geräte einer Anlage
liegen quer durchs Gebäude verteilt, verbunden durch Rohre, die niemand in
einen Quader bekommt.

**Verworfen: ein Multiblock-Controller, der einen Bereich abtastet.** Er hätte
einen Block, eine Bereichsgrenze und eine Zuordnung gebraucht — und wäre an
jeder Anlage gescheitert, die nicht in einen Quader passt.

**Verworfen: der Vorlagenname im Label**, etwa `OrePlant:ore_plant_1/crusher`.
Das macht jeden Namen länger und bricht, sobald jemand die Vorlage umbenennt.

### Der Schrägstrich lebt nur im Label

Außen steht `werk_1`, innen `eingang`. Die Sprache sieht den Trenner nie und
muss ihn nicht parsen.

Damit fällt die Forderung der Spezifikation, dass die Geräte einer Anlage von
außen unsichtbar sind, ohne eigenes Zutun ab: `eingang` trifft nie
`werk_1/eingang`, solange nicht ausdrücklich eine Anlage im Spiel ist.

Umgekehrt bleibt der Rest des Netzes von innen erreichbar: Erst wenn die Anlage
kein Gerät dieses Namens hat, zählt der globale Name. Sonst wäre ein
Netzspeicher aus einer Vorlage heraus nicht anzusprechen.

### Zu welcher Vorlage eine Anlage gehört, wird erschlossen

Passt genau eine Vorlage ganz, gehört die Anlage ihr. Passen mehrere, ist sie
mehrdeutig und meldet sich. Passt keine ganz, gilt die mit der größten
Überschneidung als gemeint und die Anlage als unvollständig.

Der letzte Fall ist der wichtige: Wer ein Gerät vergessen hat, soll es im
Terminal lesen. Eine unvollständige Anlage nimmt keine Aufrufe an — ein halb
durchlaufener Aufruf, der in der Mitte auf ein fehlendes Gerät trifft, wäre
schlimmer als einer, der gar nicht erst beginnt.

### Ein Aufruf bekommt einen eigenen Rahmen

Damit `ore_plant_1.process(…)` überhaupt möglich ist, musste zuerst der
gewöhnliche Funktionsaufruf im Ablauf wartefähig werden. Vorher lief er im
gewöhnlichen Interpreter zu Ende, und eine Funktion mit `await` ließ sich nicht
aufrufen, sondern nur als Ablauf starten.

Die Grenze ist ehrlich gezogen: Nur ein Aufruf, der allein dasteht — als
Anweisung oder rechts von einem `let` —, wird zum Rahmen. Steckt er in einer
Rechnung wie `let x = f() + 2`, läuft er den gewöhnlichen Weg und kann dort
nicht warten. Einen halb ausgewerteten Ausdruck aufzuschreiben hieße, den
Ausdrucksbaum selbst zur Zustandsmaschine zu machen.

**Dabei fiel ein Loch auf, das es ohne Aufrufe nicht geben konnte:** Die
Namenssuche lief über alle Rahmen. Eine gerufene Funktion hätte die Variablen
ihres Rufers gesehen, und ihr Verhalten hinge davon ab, wer sie gerade aufruft.
Sie endet jetzt am Aufruf, ebenso wie `break` und `continue`.

---

## Flüssigkeiten (2026-08-21)

### Die Art steht im Selektor, und eine Stelle liest sie

`fluid:water` und `item:iron_ingot` unterscheiden sich für den Spieler nur in
einem Wort — für die Laufzeit sind es zwei Welten: ein Tank hat keine Slots,
gerechnet wird in Millibucket statt in Stück, und die Fähigkeit am Nachbarblock
ist eine andere.

`Value.Request.kind()` liest das Wort vor dem Doppelpunkt, und zwar als einzige
Stelle. Überall sonst wird nach der Art gefragt. Ohne diese Auskunft kam bei
`move 1000 fluid:water` die Meldung, es gebe kein Wasser im Pack — statt der
wahren, dass Flüssigkeiten diesen Weg gar nicht nehmen.

**Verworfen: die Art als eigenes Feld durch alles durchreichen.** Der Selektor
ist ohnehin das, was aufgeschrieben wird; ein zweites Feld daneben könnte von
ihm abweichen und wäre eine Quelle für Fehler, die es sonst nicht gibt.

### Millibucket, wie überall

Ein Eimer sind 1000. Damit steht im Programm dieselbe Zahl wie in jeder anderen
Mod, und niemand rechnet um. `rate 1000 per 1t` heißt einen Eimer pro Tick.

### Nur stehende Flüssigkeiten

In der Registry stehen Wasser und fließendes Wasser als zwei Einträge. Ein
Muster wie `fluid:*water*` fände sonst beide, und der Spieler bekäme eine Sorte
angeboten, die sich nirgends lagern lässt.

### Ein Flüssigkeits-Worker braucht ein filter

Bei Gegenständen heißt „kein Filter" sinnvoll „alles". Bei Flüssigkeiten nicht:
Ein Tank hält meist genau eine Sorte, und die falsche zu ziehen ist teurer —
wer versehentlich Lava in den Wasserkreislauf schiebt, merkt es an anderer
Stelle. Also hält der Worker an und sagt, was fehlt.

### Die Suche nach dem Ziel steht ein einziges Mal da

Gruppen, Verteilungsstrategien und alle Fehlermeldungen sind für Tanks
dieselben wie für Inventare. Was am Ende geholt wird, entscheidet der Aufrufer
über eine Funktion. Sonst hätte diese ganze Suche zweimal existiert, und die
eine Fassung bekäme Verbesserungen, die der anderen fehlen — dasselbe Argument
wie bei den Abläufen und dem Interpreter.

### Ein Lager darf einen Posten verlieren, eine Variable nicht

Ist eine Mod aus dem Pack, verschwindet ihre Flüssigkeit still aus dem Bestand.
Steckt sie dagegen in der Variablen eines wartenden Ablaufs, scheitert der
Ablauf mit klarer Meldung. Dieselbe Unterscheidung wie bei Gegenständen, aus
demselben Grund: Mit einem Lagerbestand rechnet niemand weiter, mit einer
Variablen schon.

---

## Der Router (2026-08-22)

**Beim dicken Kabel gibt es kein Bündeln, sondern einen Block.** Vier dünne
Stränge zu je vier Blockpixeln passen nebeneinander in einen Block; vier dicke
zu je zehn nicht. Statt beim dicken Kabel eine schlechtere Fassung desselben
Gedankens zu bauen, steht dort ein eigener Block: An ihm bekommt jede Seite
eine Bahn. Gleiche Bahn heißt verbunden, verschiedene Bahnen kreuzen sich
berührungslos, „aus" heißt abgeklemmt.

Damit ist das Bündeln ganz aus der Mod verschwunden — ein Kabelblock trägt
genau ein Kabel in genau einer Farbe. Der Abschnitt „Kabelbündel" weiter oben
beschreibt einen Stand, den es nicht mehr gibt; er bleibt stehen, weil dieses
Papier festhält, was entschieden wurde, und nicht, was gerade gilt.

### Vier Bahnen

Sechs Seiten und sechs Bahnen wären fast jede Seite für sich, und dafür
braucht es keinen Block. Vier ist die Zahl, bei der eine Kreuzung noch als
Kreuzung zu lesen ist.

### Die Zuordnung steht in der BlockEntity

Sechs Seiten mit je fünf Werten sind 15625 Blockzustände. Die legt Minecraft
beim Start alle an und backt für jeden ein Modell — für eine Auskunft, die
sich in einem farbigen Ring erschöpft. Also steht sie in der BlockEntity, und
ein BlockEntityRenderer malt die Ringe.

**Der Ring liegt am Rand der Fläche, nicht in ihrer Mitte.** Ein dickes Kabel
deckt die mittleren zehn Blockpixel ab. Eine Kennung dort wäre genau dann
verdeckt, wenn die Seite angeschlossen ist — also immer dann, wenn man sie
lesen will.

Farbe und Anzahl heller Ecken sagen dasselbe. Wer Farben schlecht
unterscheidet, zählt die Ecken.

### Der Router ist farbneutral

Wer ein rotes und ein grünes Kabel auf dieselbe Bahn legt, hat sie verbunden.
Das ist Absicht: Der Unterschied zu zwei Kabeln, die sich bloß einen Block
teilen, ist, dass es hier jemand eingestellt hat.

Verworfen: die Farbe mit durch den Router zu tragen. Dann wäre die Kennung
eines Knotens vom Weg abhängig, über den man ankam — dieselbe Bahn hätte je
Farbe einen eigenen Knoten, die Kanallast liefe je Farbe getrennt, und der
Netzanalysator zeichnete jede Strecke mehrfach.

### Eine Bahn trägt so viel wie ein dickes Kabel

Der Router steht in der Wegrechnung wie ein Kabelstück. Ließe man ihn heraus,
wäre eine Kreuzung die Stelle, an der die Kanalgrenze aufhört zu gelten — und
damit die Stelle, an der man sie umgeht.

### Die Bahn gilt auch für Geräte

Der Filter steht ganz oben in der Richtungsschleife, nicht erst vor dem
Kabelzweig. Ein Connector, der an einer Seite auf Bahn zwei hängt, gehört
nicht zum Netz, das über Bahn eins läuft. Sonst wäre die Bahn nur eine Regel
für Kabel, und die erste Kiste am Router hebelte sie aus.

### Ein Klick baut das Netz sofort neu auf

Der Turnus ist fünf Sekunden. Zwischen Klick und Wirkung sind das zu viele:
Der Spieler klickt in der Zeit dreimal weiter und weiß am Ende nicht, was
gerade gilt. Beim Setzen und Abbauen ebenso — sonst zeichnet der
Netzanalysator Strecken durch einen Block, den es nicht mehr gibt.

---

## Flüssigkeiten liegen in Zellen (2026-08-22)

**Für Eisen brauchte man ein Laufwerk, für Lava nicht.** Diese Ungleichheit
lässt sich niemandem erklären, und sie war nur der Stand der Arbeit: Der
Gegenstandsspeicher bekam Zellen, der Flüssigkeitsspeicher lagerte weiter
unbegrenzt im Controller.

Jetzt liegen beide in Zellen, und beide Zellarten in demselben Laufwerk. Ein
zweites Laufwerk nur für Flüssigkeiten wäre ein Block mehr für dieselbe
Handlung, und die Frage „welches nehme ich" hätte keine gute Antwort.

### Die Rechnung steht ein einziges Mal da

`CellFormat` kennt Registry und Feldnamen und erledigt Lesen und Schreiben,
`CellInventory` rechnet mit den zwei Grenzen und weiß nicht mehr, womit. Zwei
Fassungen wären zwei Orte, an denen ein Bestand verlorengehen kann — und die
eine bekäme irgendwann eine Verbesserung, die der anderen fehlt. Dasselbe
Argument wie bei den Abläufen und dem Interpreter.

### Andere Zahlen, andere Namen

Vier bis zweiunddreißig Sorten und vierundsechzig bis viertausend Eimer.
Flüssigkeiten gibt es in weniger Sorten und größeren Mengen; vierundsechzig
Sortenplätze wären ein Platz, den nie jemand füllt.

**Die Namen sagen Eimer, nicht Kilo.** Bei den Gegenstandszellen stimmt die
Zahl im Namen mit dem Inhalt überein, und der Preis folgt ihr — eine 64k
kostet vierundsechzig kleine. Eine Flüssigkeitszelle „1k" mit vierundsechzig
Eimern hätte diese Ehrlichkeit nicht.

Die Zahlen sind gesetzt, nicht hergeleitet. Sie stehen an einer Stelle und
lassen sich ändern, wenn sich das Spiel anders anfühlt als gedacht.

### Erst fragen, dann ziehen

Solange der Speicher unbegrenzt war, konnte kein Aufrufer von `insert` etwas
falsch machen — es passte immer alles. Mit einer Grenze wird jeder Aufrufer,
der den Rückgabewert wegwirft, zu einer Stelle, an der Flüssigkeit still
verschwindet.

Bei Gegenständen ist der Ausweg, den Rest zurückzulegen. Bei Flüssigkeiten
nicht: Ein Tank nimmt nicht unbedingt wieder an, was man ihm gerade entnommen
hat. Deshalb wird erst gefragt, wie viel hineinpasst, und dann genau so viel
gezogen. Der Worker meldet „Der Speicher ist voll", statt still nichts zu tun.

### Die Fülle steht im Terminal

Solange nichts begrenzt war, brauchte niemand zu wissen, wie voll das Netz
ist. Jetzt schon — und die Zahl, die man ohne Hilfe nicht sieht, sind die
freien Sortenplätze: Eine Zelle mit allen belegt nimmt nichts Neues mehr an,
obwohl sie nach Menge fast leer ist. Ohne die Anzeige merkt man es erst am
stehenden Worker und sucht den Fehler dort.

Deshalb steht rechts unten im Speicher-Reiter, wie viele Plätze frei sind,
getrennt nach Gegenständen und Flüssigkeiten, und in Warnfarbe, sobald keiner
mehr da ist.

---

## Serverschränke und Prozessoren (2026-08-22)

**Entschieden war schon: Sie werden von Anfang an verlangt.** Offen waren drei
Fragen, und die mussten beantwortet sein, bevor gebaut wird. Hier stehen meine
Antworten samt Begründung. **Einspruch kostet nur die Zahlen** — Block, Gegenstand
und Buchführung bleiben, egal wie die Zahlen ausfallen.

### 1. Was belegt einen Platz? Ein laufender Ablauf, kein Worker

Worker laufen dauernd. Kostete jeder einen Platz, stünde man nach zehn Workern
vor der Wahl, Hardware nachzubauen oder ein Programm zu löschen — und das
bestraft die falsche Sache: Wer einen großen Worker in zwei saubere zerlegt,
zahlt dafür. Eine Sprache soll Gliederung belohnen, nicht besteuern.

Abläufe sind das Gegenteil: Sie kommen und gehen, mehrere laufen nebeneinander,
und genau das ist das Bild einer CPU. **Ein Ablauf im Zustand RUNNING,
SLEEPING oder AWAITING belegt einen Platz.** Ein wartender zählt mit — er hält
seinen Rahmenstapel, und das ist der Speicher, um den es geht.

Ohne Server läuft trotzdem nichts, auch kein Worker. Der Schrank ist nicht die
Währung für Nebenläufigkeit allein, sondern die Voraussetzung dafür, dass das
Netz überhaupt rechnet — so wie ein Laufwerk die Voraussetzung dafür ist, dass
es lagert.

### 2. Überlast: Warteschlange, aber sichtbar

Der Einwand gegen eine Warteschlange war, dass sie die Anlage träge macht,
ohne dass jemand merkt warum. Das ist ein Anzeigeproblem und kein Grund für
Ablehnung.

Der Grund für die Warteschlange ist ein anderer: **Verzögerung ist
wiederherstellbar, Verlust nicht.** Ein abgelehntes `device_done` ist für
immer weg, und die Gegenstände stehen bis zum nächsten Neustart in einer
Maschine, die niemand mehr anfasst. Ein verzögertes läuft eine Sekunde später.

Also Warteschlange — begrenzt auf zweiunddreißig, und was darüber hinausgeht,
scheitert sichtbar und steht unter den letzten Fehlern. Eine unbegrenzte
Warteschlange wäre eine Anlage, die Arbeit ansammelt, die sie nie abarbeitet.

### 3. Ein Co-Prozessor bringt mehr gleichzeitige Abläufe

Nicht schnellere Abarbeitung derselben. „Schneller" hieße mehr Schritte je
Tick, und das merkt niemand — ein Ablauf, der in einem Tick fertig wird, wird
nicht sichtbar fertiger. „Mehr gleichzeitig" liest man: vier Dinge auf
einmal statt zwei.

### Die fünfhundert Schritte bleiben, wo sie sind

In `docs/umsetzung.md` stand die Notiz, die neue Grenze solle die technische
Grenze der Ablaufmaschine ersetzen. **Das war ein Irrtum meinerseits**: Die
fünfhundert Schritte gelten je Ablauf, nicht für alle zusammen. Sie sind kein
Kapazitätsmodell, sondern eine Bremse gegen die Endlosschleife — eine falsch
geschriebene `while`-Schleife soll den Server nicht anhalten.

Beides zu einem Topf zusammenzuziehen hieße, Schritte zwischen Abläufen zu
verteilen, und damit stünde sofort die Frage nach Fairness und Reihenfolge im
Raum. Zwei Grenzen für zwei Aufgaben ist die ehrlichere Antwort.

### Der Schrank ist ein Regal, wie das Laufwerk

Acht Plätze, Prozessor in der Hand hineinklicken, leere Hand nimmt den letzten
heraus. Dasselbe Bild, derselbe Griff, derselbe Code — wer ein Laufwerk
bedienen kann, kann auch einen Serverschrank bedienen.

### Wird der Schrank abgebaut, laufen die laufenden Abläufe zu Ende

Sie mittendrin zu töten hieße, Gegenstände zu verlieren, die gerade in der
Hand eines Ablaufs sind. Neue Abläufe starten nicht mehr, und die Worker
stehen still. Dieselbe bewusste Milde wie beim Laufwerk, das keinen Kanal
kostet.

### Ereignisse während eines Serverausfalls

Reißt jemand den letzten Schrank ab, stehen die Worker still, und die
Ereignisse, die aus Zustandsänderungen entstehen — Redstone, geändertes
Inventar — werden nicht mehr gemeldet. Sie kommen nach, sobald wieder ein
Schrank steht: **einmal, nicht einmal je verpasster Änderung.** Gemeldet wird
der Unterschied zum zuletzt gesehenen Zustand, nicht jeder Schritt dazwischen.

Das ist beabsichtigt. Ein Netz, das nach einer Stunde ohne Strom hundert
nachgeholte Redstone-Ereignisse abfeuert, wäre schlimmer als eines, das den
Endstand meldet.


---

## Wer einen Kanal kostet (2026-08-22)

**Was am Netz etwas tut, kostet einen Kanal.** Vorher zahlten nur die
Connectoren; Laufwerk und Serverschrank hingen gratis daran. Das war eine
Ausnahme ohne Begründung — ein Netz aus zwanzig Laufwerken kam mit einem
einzigen Kanal aus, während zwanzig Kisten zwanzig kosteten.

Der frühere Eintrag „Kosten Laufwerke einen Kanal? Hier nicht" ist damit
überholt. Er stand unter der Annahme, Lagerraum sei etwas anderes als ein
Gerät; das ist er nicht.

### Eine Anzeige kostet ein Viertel

Sie liest nur mit und schiebt nichts. Vier an einer Wand kosten zusammen
einen Kanal — eine Leitstandwand soll kein halbes Netz auffressen, aber
kostenlos ist sie auch nicht.

**Gerechnet wird deshalb in Vierteln.** Ein Viertel als kleinste Einheit
macht aus der Bruchrechnung wieder ganze Zahlen; erst die Anzeige rechnet
zurück und schreibt `12¼ / 16`. Mit Bruchzeichen statt Komma, weil ein
„12,25" in einer Zeile mit „von 16" nach Messwert aussieht statt nach Anzahl.

### Der Router kostet nichts

Er leitet weiter, wie ein Kabel — und Kabel kosten nichts. Ihn zu bepreisen
hieße, Kreuzungen zu bestrafen, und genau die soll man bauen dürfen.

### Ohne Kanal keine Wirkung

Ein Laufwerk ohne Kanal lagert nichts, ein Serverschrank ohne Kanal rechnet
nicht. Das ist die Folge daraus, dass sie einen kosten — sonst wäre die
Grenze für Lagerraum eine Anzeige ohne Wirkung.

### Die sechs Seiten des Controllers

Sechs dichte Kabel zu je vierundsechzig sind 384 Kanäle für ein Netz. Das ist
weit jenseits dessen, was eine Anlage braucht; die Grenze, die man wirklich
trifft, ist die je Kabel. Sollte sie doch binden, ist die Antwort dieselbe wie
bei AE2: ein Controller aus mehreren Blöcken, dessen Außenflächen alle Kabel
tragen.

---

## Strom (2026-08-22)

Speicher hängt am Laufwerk, Rechenleistung am Schrank — **Strom ist das dritte
Bein, und das einzige, das laufend etwas kostet** statt nur einmal beim Bauen.
Ohne ihn ist ein fertiges Netz gratis.

### FE aus dem Pack, kein eigener Generator

Der Controller nimmt Forge Energy an, wie es die Presse schon tut. Ein eigener
Energiebegriff wäre näher an Applied Energistics, bräuchte aber eine eigene
Erzeugerkette — und die Mod setzt mit der Presse ohnehin ein Pack voraus. Ein
halbherziger eigener Generator konkurriert nur mit besseren.

### Gezahlt wird für Bereitschaft, nicht für Arbeit

Ein Worker, der etwas bewegt, kostet nicht mehr als einer, der wartet.
Absichtlich grob: Verbrauch, der mit der Last schwankt, ist im Spiel nicht
nachzuvollziehen, und wer seine Anlage plant, will eine Zahl, die stillsteht.

Kabel kosten nichts. Eine Anlage hat Hunderte davon; zöge jedes auch nur ein
FE, bestimmte die Länge der Leitung den Verbrauch und nicht das, was daran
hängt. Der Router dagegen zahlt: Er schaltet aktiv und hat eine BlockEntity.

### Aus heißt aus, und danach wird hochgefahren

Reicht der Vorrat nicht, steht alles. Kommt der Strom zurück, braucht das Netz
drei Sekunden, in denen es schon zieht und noch nichts tut. **Ohne diese Zeit
wäre ein Stromausfall ein Flackern, das niemand bemerkt.**

Dazu eine Wiederanlaufschwelle: Das Netz kommt erst zurück, wenn genug
beisammen ist, um das Hochfahren zu überstehen und danach noch zu laufen. Eine
Versorgung knapp unter dem Bedarf erzeugte sonst ein Blinken im
Halbminutentakt, das wie ein Fehler aussieht statt wie zu wenig Strom.

### Einfrieren statt Zuendelaufen — auch beim Serverschrank

Gestern stand hier das Gegenteil: Wird der letzte Schrank abgebaut, sollten
die laufenden Abläufe zu Ende laufen. **Diese Regel hat einen Tag gehalten.**
Der Stromausfall hat gezeigt, dass Einfrieren die ehrlichere Antwort auch für
den Schrank ist:

- Nichts geht verloren. Ein Ablauf hält zwischen zwei Schritten keine
  Gegenstände — das Einfrieren kostet also nichts.
- Eine Regel statt zwei. „Kein Server oder kein Strom heißt: Das Netz steht
  still und läuft weiter, wo es war" ist ein Satz; zwei verschiedene
  Antworten auf dieselbe Frage sind es nicht.
- Es ist, was ein Spieler erwartet: aus und wieder an, nicht aus und
  abgeräumt.

Ereignisse, die während des Stillstands eintreffen, bleiben liegen und kommen
an, sobald das Netz wieder läuft. Verzögerung ist wiederherstellbar, Verlust
nicht — dasselbe Argument wie bei der Warteschlange.

**Eine Frist läuft dagegen weiter.** Ein `await` mit `timeout 5s`, das eine
Stunde ohne Strom stand, nimmt beim Aufwachen seinen `else`-Zweig. Die Frist
ist eine Aussage über die Welt und nicht über die Rechenzeit; wer sie anders
meint, meint eigentlich eine Anzahl Schritte.

### Zwei Fehler, die dabei ans Licht kamen

**Der Presse konnte niemand Strom geben.** Sie hatte einen FE-Puffer, aber
keine Capability-Anmeldung — kein Kabel und kein Generator hätte ihn gefunden.

**Und sie hätte ihn auch nie verbraucht.** Ein `EnergyStorage` mit
Entnahmerate null gibt nach außen nichts ab, richtig so — aber
`extractEnergy` prüft dieselbe Rate, und damit kommt auch die Maschine selbst
nicht an ihren Vorrat. Dafür gibt es jetzt `InternalBuffer` mit einem Weg nach
innen, der die Rate nicht fragt.


---

## Die Brennkammer (2026-08-22)

**Ich hatte vorher das Gegenteil empfohlen:** kein eigener Generator, der
konkurriere nur mit besseren. Dabei habe ich das nächstliegende Vorbild
übersehen — Applied Energistics hat die Vibrationskammer. Sie ist absichtlich
mittelmäßig, hat keine Ausbaustufen und wird in jedem Pack innerhalb einer
Stunde ersetzt. **Sie existiert nicht, um zu konkurrieren, sondern damit man
nicht blockiert ist.**

Und der Fall ist bei uns größer als der Strom des Netzes: Die Presse braucht
seit einem Tag FE und konnte es von nirgendwo bekommen. Damit war die ganze
Fertigungskette — Erz, Platte, Kerne, Zellen, Prozessoren — ohne Fremdmod gar
nicht zu durchlaufen. Das ist kein Ausgleichsproblem, das ist ein Loch im
Einstieg.

### Absichtlich mittelmäßig

Vierzig FE je Tick aus gewöhnlichem Ofenbrennstoff, kein Ausbau, kein
Upgrade-Pfad. Eine Kohle trägt ein kleines Netz gut eine Minute. Wer ein Pack
spielt, stellt nach der ersten Stunde etwas Besseres daneben — das ist der
Zweck und kein Mangel.

### Sie schiebt, statt bereitzustellen

Der Controller nimmt an und zieht nicht. Eine Quelle, die nur bereitstellt,
käme bei ihm nie an. Geschoben wird reihum in fester Richtungsfolge, damit
sich erklären lässt, welcher von zwei Verbrauchern zuerst bekommt.

### Nachgelegt wird nur, wenn Platz ist

Sonst verbrennt eine Kohle, während niemand abnimmt — und das merkt man erst,
wenn der Kohlenstapel weg ist. Jade sagt zusätzlich „Vorrat voll — niemand
nimmt ab", weil man einer brennenden Kammer nicht ansieht, ob sie für etwas
brennt.

### Die Kreativ-Stromquelle bleibt daneben

Sie hat kein Rezept und steht nur im Kreativ-Reiter. Zum Prüfen einer Anlage
will man keine Kohle nachlegen.


---

## Der Serverschrank (2026-08-22)

Vorher: ein Würfel mit acht Plätzen für Prozessoren, und die Prozessoren
addierten sich zu einer Zahl. Jetzt: zwei Blöcke hoch, zwölf Einschübe, und
in jeden gehören Rechenwerk, Speicher und Datenträger.

### Erst alle drei ergeben einen Server

Ein Einschub mit zwei von drei Bauteilen trägt **nichts** bei — nicht
anteilig, gar nichts. Das ist die Regel, an der der ganze Block hängt.

Zählte jedes Bauteil für sich, wäre der Schrank eine Summe von zwölf mal drei
Zahlen, und die Antwort wäre immer dieselbe: von allem das Größte einbauen.
So ist die Frage eine andere — *welcher* Einschub bekommt das große Teil.
Man kann einen Einschub auf Rechenleistung auslegen und den nächsten auf
Speicher, und beides kostet denselben Platz.

Der Preis ist eine Fehlerquelle: Ein Schrank mit elf Bauteilen, der nicht
läuft, sieht von außen aus wie ein voller. Deshalb hat der unfertige Einschub
eine eigene Farbe — gelb an der Front, gelb im Fenster, und Jade sagt
„Unvollständige Einschübe: 2". Ohne diese drei Stellen wäre die Regel eine
Falle.

### Was die drei Bauteile begrenzen

| Bauteil | Grenze | Warum diese |
|---|---|---|
| Rechenwerk | gleichzeitige Abläufe | Was ein Prozessor tut |
| Speicher | wie viele Abläufe überhaupt bestehen | Ein schlafender Ablauf steht auch irgendwo |
| Datenträger | Programmgröße in Anweisungen | Was ein Datenträger tut |

Erwogen und verworfen: **Rechenwerk als Schritte je Tick.** Das wäre die
ehrlichste Übersetzung — ein Prozessor macht Instruktionen pro Sekunde — und
sie wäre am stärksten zu spüren, weil die Fabrik damit buchstäblich schneller
liefe. Sie hätte aber die Bremse gegen die Endlosschleife mit der
Kapazitätsrechnung vermischt, und ein Netz mit einem kleinen Rechenwerk
machte zwei Schritte je Tick — das sieht nicht nach knapp aus, das sieht nach
kaputt aus.

### Gezählt werden Anweisungen, nicht Zeichen

Kommentare, Einrückung und lange Namen kosten nichts. Eine Sprache, in der
Erklären teuer ist, wird nicht erklärt — und ein Programm an der Grenze soll
man kommentieren dürfen, nicht kürzen müssen.

Die Grenze greift an zwei Stellen: Beim Übernehmen wird ein zu großes
Programm **abgelehnt**, mit der Zahl in der Meldung. Fällt der Platz später
weg, weil jemand einen Datenträger herauszieht, **friert** das Netz ein —
dieselbe Antwort wie bei Stromausfall. Nie stillschweigend kürzen.

### Die Stufen springen um das Vierfache

2 / 8 / 32 / 128 beim Rechenwerk, 8 / 32 / 128 / 512 beim Speicher,
64 / 256 / 1024 / 4096 beim Datenträger. Jede Stufe kostet vier der
vorigen — das ist linear und damit für sich genommen kein Gewinn.

**Der Gewinn ist der Platz.** Zwölf Einschübe sind die Obergrenze, und wer
darüber hinaus will, muss nach oben statt in die Breite. Ab der dritten Stufe
kostet es zusätzlich einen Diamanten, ab der vierten Netherit: Das große Teil
soll ein Ziel sein und kein Zwischenschritt.

### Zwei Blöcke hoch, ein Gerät

Ein Kanal, eine BlockEntity, ein Eintrag in der Geräteliste. Wer oben
ankabelt, kabelt denselben Schrank an — die obere Hälfte rechnet im Graphen
auf die untere um. Zählte sie für sich, kostete ein Schrank zwei Kanäle, und
die zweite BlockEntity gäbe es gar nicht.

Warum überhaupt zwei hoch: Zwölf Einschübe auf einer Würfelseite wären
Kacheln kleiner als das Fadenkreuz. Und ein Schrank, der aussieht wie ein
Kasten, ist keiner.

### Strom nach laufenden Einschüben, nicht nach Bauteilen

Zwei FE je laufendem Einschub. Ein halb bestückter rechnet nicht, also zahlt
er auch nicht — sonst kostete ein vergessenes Rechenwerk dauerhaft Strom,
ohne je etwas zu tun. **Die Stufe spielt keine Rolle:** Der Preis für Ausbau
steht in der Rezeptkette; ein zweiter Preis obendrauf verkomplizierte die
Rechnung, ohne eine Entscheidung zu ändern.

### Verworfen: der Server als tragbarer Gegenstand

Reizvoll wäre gewesen, den Server selbst zu einem Gegenstand zu machen — ein
Gehäuse, in das man CPU, RAM und Datenträger einbaut und das man dann
fertig bestückt in einen anderen Schrank umsetzt.

Dagegen sprach das Shulkerkisten-Problem: Ein Gegenstand, der andere
Gegenstände hält, lässt sich im Inventar nicht bearbeiten. Es bräuchte ein
zweites Fenster für den Gegenstand selbst, und das Verschieben zwischen
beiden Fenstern ist die Sorte Bedienung, die man einmal erklärt und danach
umgeht. **Der Einschub im Block ist dasselbe Bild ohne die
Zwischenschicht** — was man im Fenster als Zeile sieht, ist der Server.

### Ein Platz nimmt nur seine Art

Ein Rechenwerk gehört nicht auf den Datenträgerplatz. Ohne diese Regel wäre
ein Einschub mit drei Rechenwerken voll und liefe trotzdem nicht — ein
Fehler, den man beim Ansehen nicht findet, weil ja drei Bauteile drinstecken.
Und ein Platz nimmt genau eines: Vorher zählte der Schrank Stapel mit, und
sechzehn Prozessoren auf einem Platz waren sechzehnmal so viel Leistung.
Damit waren die Plätze keine Grenze, sondern eine Formalität.

### Was das für alte Welten heißt

Prozessor und Co-Prozessor gibt es nicht mehr. Ein Schrank, der in einer
Welt steht, verliert seinen Inhalt und hat keine obere Hälfte — er steht als
halber Rahmen da und trägt nichts. Neu setzen. Ein Wanderpfad dafür wäre
Arbeit für einen Zustand, den es außerhalb dieser Entwicklungswelt nicht
gibt.

---

## Die Brücke zu VS Code (2026-08-23)

Der Controller legt sein Programm als Datei neben die Welt:
`<Welt>/factorynetwork/controller_overworld_10_64_-20.mf`. Wer sie speichert,
hat sie eine Sekunde später im Spiel; wer im Terminal übernimmt, findet den
Text sofort in der Datei.

### Wer zuletzt geschrieben hat, gewinnt

Keine Konfliktauflösung, keine Zusammenführung, kein Vorrang für eine Seite.
Zwei Leute, die gleichzeitig an demselben Programm arbeiten, sind ein Problem,
das keine Regel löst — und für einen Spieler an seiner eigenen Welt ist die
Regel offensichtlich richtig.

Damit sich das nicht aufschaukelt, merkt sich die Brücke, was sie selbst
geschrieben hat. Der Zeitstempel allein reicht nicht: Manche Editoren
schreiben über eine Zwischendatei und benennen um.

**Geschrieben wird nach jedem Übernehmen, auch nach einem mit Fehlern.** Datei
und Terminal müssen denselben Text zeigen. Stünde in der Datei noch die letzte
fehlerfreie Fassung, holte der nächste Blick sie zurück und überschriebe, was
gerade eingetippt wurde.

### Nachgesehen, nicht überwacht

Ein `WatchService` wäre unmittelbar, bräuchte aber einen eigenen Thread, eine
Entprellung gegen die Doppelereignisse der Editoren und ein verlässliches
Aufräumen beim Weltwechsel. Nachsehen im Sekundentakt braucht nichts davon und
verkraftet nebenbei eine halb geschriebene Datei: Beim nächsten Blick steht sie
vollständig da.

Eine Sekunde Verzug merkt niemand. Ein hängengebliebener Thread je geladener
Welt fällt erst nach Stunden auf.

### Die Datei bleibt beim Abbauen liegen

Ein Controller, der an dieselbe Stelle zurückkommt, findet sein Programm
wieder. Wer sich verklickt hat, setzt den Block zurück und hat alles — das ist
das Gegenteil von dem, was ein versehentlicher Schlag sonst kostet.

### Die Erweiterung liegt im Repository, nicht im Marktplatz

`editor/vscode` ist eine TextMate-Grammatik, eine Klammernkonfiguration und
elf Bausteine. Dafür einen Marktplatzeintrag zu pflegen, wäre mehr Arbeit als
der Inhalt wert; kopieren nach `~/.vscode/extensions` reicht.

**Keine Fehlerprüfung im Editor.** Sie bräuchte einen Sprachserver, der weiß,
was gerade in der Welt steht — also eine Verbindung zum laufenden Spiel.
Solange es die nicht gibt, ist das Terminal die Stelle, an der Fehler stehen.

---

## Die Anzeigenwand (2026-08-23)

Sechs Tafeln nebeneinander waren bisher sechs Anzeigen: sechs Rahmen, und
wenn alle denselben Namen trugen, sechsmal derselbe Text. Das ist kein
Bildschirm, das sind sechs Zettel.

### Eine Wand, eine Schrift

Zusammen gehören Tafeln, die in dieselbe Richtung zeigen und sich in ihrer
Ebene berühren. **Nicht über Ecken:** Eine Tafel an der Nordwand und eine an
der Ostwand stoßen zwar aneinander, sind aber zwei Bildschirme — man kann
nicht beide gleichzeitig ansehen.

Geschrieben wird von der Tafel **unten links**, von vorn gesehen. Eine feste
Regel, weil sie erklärbar sein muss: „warum steht der Text bei dieser" ist
sonst nicht zu beantworten. Für eine Wand mit Loch oder Stufe gilt trotzdem
das umschließende Rechteck als Fläche.

### Die Schrift bleibt gleich groß

Der Platz einer großen Wand geht in mehr Zeilen und längere, nicht in größere
Buchstaben. Eine Wand, deren Text mit ihr wächst, ist aus drei Metern genauso
lesbar wie eine einzelne Tafel und verschenkt den ganzen Vorteil.

### Der Name gehört der Wand

Die Beschriftungspistole setzt ihn auf alle Tafeln. Wer eine Wand
beschriftet, hat die Wand beschriftet und nicht die eine Tafel, die er
getroffen hat — welche davon die schreibende ist, sieht man ihr nicht an.
Gelesen wird zusätzlich der erste Name in Leserichtung, damit auch eine
nachträglich angesetzte Tafel nichts kaputt macht.

### Vierundsechzig Blockzustände für einen Rahmen

Der Rahmen fällt weg, wo eine zweite Tafel anschließt. Dafür stehen vier
Wahrheitswerte im Blockzustand — links, rechts, oben, unten, **von vorn
gesehen** und nicht in Weltrichtungen. Mal vier Blickrichtungen sind das
vierundsechzig Zustände und sechzehn Modelle.

Das ist viel für einen Rahmen und trotzdem der richtige Weg: Was man sieht,
muss im Blockzustand stehen, sonst kann der Renderer es nicht aus dem Modell
nehmen — und dann zeichnet er jeden Rahmen selbst, bei zwanzig Tafeln
zwanzigmal je Bild.

### Auch eine Tafel, die niemand gesetzt hat, sieht sich um

`getStateForPlacement` greift nur beim Setzen aus der Hand. Ein Kolben, ein
Bauwerk oder ein `/setblock` legen die Tafel ohne Nachbarschaft ab, und die
Nachbarn erfahren zwar davon, die neue Tafel selbst aber nicht. Deshalb
rechnet auch `onPlace` die vier Seiten nach.
