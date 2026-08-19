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
wird intern in Ticks. Bruchteile nur, wenn sie aufgehen — `0.1s` ist ein
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
