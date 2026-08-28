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

> **Stand 2026-08-24:** Die Punkte 2 bis 4 sind gebaut — die Schreibweise
> steht in `sprache.md` und `grammatik.md`, das Speichermodell in den Zellen
> und Laufwerken, Topologie und Kanäle in `FactoryGraph`. Was von dieser
> Liste bleibt, steht darunter. Die vollständige Übersicht ist
> `offene-punkte.md`.

1. ~~**Grenzen für Nutzercode auf dem Server**~~ (Konzept 22.9) — **gebaut
   am 25.08.** Das Schrittbudget je Durchlauf und die Suchtiefe des
   Netzgraphen stehen in `config/factorynetwork-server.toml`. Die Plätze im
   Serverschrank bleiben draußen: Sie sind Spielinhalt und gehören zum
   Ausgleich der Mod, nicht zur Serverlast — wer sie ändert, ändert das Spiel
   und nicht seine Last.
2. ~~Konkrete Syntax~~ — gebaut.
3. ~~Speichermodell~~ — gebaut, Zellen in Laufwerken.
4. ~~Netzwerktopologie und Channels~~ — gebaut, sechzehn je dünnem Strang.
5. **Erkennung von Maschinen-Rezepten** (Konzept 22.5) — offen, aber
   **keine Voraussetzung mehr für Autocrafting.** Der Fabricator baut
   Werkbank-Rezepte, und die stehen im Server; gebraucht wird die Erkennung
   für Processing-Rezepte am Connector (Konzept §8).

---

## Der Fabricator baut ohne Muster (2026-08-25)

Der erste Schnitt des Autocraftings. Drei Entscheidungen, die zusammengehören:

**Keine Muster-Items.** Das Konzept sah sie in §9 schon nicht vor („keine
zwingenden physischen Pattern-Items"), und der Grund trägt: Was gebaut werden
kann, weiß das Spiel bereits. Ein Netz, das sich seine Rezepte erst auf
Papierschnipsel schreiben lässt, verlangt Arbeit für eine Auskunft, die schon
dasteht — und in einem großen Pack ist das die Arbeit von Stunden.

**Zuerst einstufig.** Fehlen Bretter, werden keine aus Stämmen gemacht.
Verworfen wurde der umgekehrte Weg — erst den Planner, dann den Block —, weil
Rekursion ohne sichtbaren Fabricator untestbare Logik ist und ein Fabricator
ohne Rekursion ein fertiges Feature. Der Preis, offen benannt: Wer eine Kette
will, schreibt sie vorerst selbst. *(Überholt am selben Tag — siehe „Die
Fertigung wird mehrstufig".)*

**Der Auftrag lebt am Controller.** Nicht am Gerät: Einer, der dort hinge,
wäre weg, sobald jemand es abbaut. Das Konzept nennt Aufträge in §11
ausdrücklich als Netzsache, und dieselbe Regel gilt schon für Abläufe.

### Bestellt wird im Code

`craft(64 item:chest)` und kein Knopf im Reiter. Der Reiter zeigt, was daraus
wurde — bestellt wird geschrieben, wie alles in dieser Mod. Ein Bestellknopf
wäre der erste Griff im Spiel, der eine dauerhafte Zusage erzeugt, ohne dass
sie irgendwo im Programm steht.

### Offen für später

- ~~**Die Rekursion**~~ — gebaut, siehe „Die Fertigung wird mehrstufig".
  Reservierungen für gleichzeitige Aufträge brauchte es dafür nicht: Weil der
  Plan bei jedem Takt neu gerechnet wird, sieht jeder Auftrag den Bestand, den
  der andere ihm gelassen hat.
- **`from crafting`** als Worker-Quelle. Braucht die Rekursion nicht: Ein
  `maintain`-Worker, der Aufträge anstößt, ist schon nützlich.

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

### Nachgetragen: als vier

Hier stand die Frage, ob ein Bündel für die Kanäle als ein Kabel zählt oder
als vier. **Sie ist im nächsten Abschnitt beantwortet** — vier Stränge in
einem Block tragen vier mal acht, weil sie vier getrennte Netze sind. Der
Absatz blieb stehen, obwohl die Antwort zwölf Zeilen weiter steht.

---

## Kanäle (2026-08-20)

**Je Strang, nicht je Bündel.** Ein Strang ist ein Bündel von Drähten; jedes
Gerät zieht auf seinem ganzen Weg zum Controller einen davon ab, und weiter
hinten fehlt er dann. Das Vorbild ist Applied Energistics.

> **Nachgetragen am 2026-08-25:** Hier standen acht. Der Code sagt seit
> längerem **sechzehn** je dünnem Strang und **vierundsechzig** je dichtem
> (`CableBlock.CHANNELS_THIN`, `CHANNELS_DENSE`) — das Doppelte des Vorbilds,
> mit einem Kommentar an der Stelle begründet. Die Verdopplung ist die
> gültige Zahl; dieser Absatz war die alte.

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
in jeden gehört ein Servergehäuse mit Rechenwerk, Speicher und Datenträger.

### Erst alle drei ergeben einen Server

Ein Einschub mit zwei von drei Bauteilen trägt **nichts** bei — nicht
anteilig, gar nichts. Das ist die Regel, an der der ganze Block hängt. Seit
dem Gehäuse sind es vier Teile: ohne Gehäuse gehen die Bauteile gar nicht
erst hinein.

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

### Zuerst verworfen, dann doch: der Server als tragbarer Gegenstand

**Am 22.08. abgelehnt, am 23.08. gebaut.** Die Ablehnung steht hier, weil ihr
Grund richtig war und die Lösung ihn ausräumt — eine stillschweigend gedrehte
Entscheidung ist schlimmer als eine falsche.

Abgelehnt wurde sie so: Ein Gegenstand, der andere Gegenstände hält, lässt
sich im Inventar nicht bearbeiten — das Shulkerkisten-Problem. Es bräuchte
ein zweites Fenster für den Gegenstand selbst, und das Verschieben zwischen
zwei Fenstern ist die Sorte Bedienung, die man einmal erklärt und danach
umgeht.

Der Einwand fällt weg, sobald man **nur im Schrank bearbeitet**. Steckt das
Gehäuse in einem Einschub, liegen seine drei Bauteile in den Plätzen daneben
und der Gegenstand selbst ist leer; beim Herausziehen wandern sie hinein,
beim Einsetzen wieder heraus. Es gibt kein zweites Fenster, weil man den
Gegenstand nie aufmacht — man legt ihn ab, und dann liegt sein Inhalt offen.

Was das gewinnt: Ein fertiger Server ist tragbar. Man baut ihn einmal, zieht
ihn heraus, steckt ihn in einen anderen Schrank — und was man in der Truhe
findet, ist ein Server und kein leeres Blech.

**Ohne Gehäuse nimmt ein Einschub keine Bauteile an.** Diese Regel macht den
Gegenstand erst zu einem: Ohne sie wären die drei Plätze schon der Server,
und das Gehäuse wäre etwas, das man kauft und das nichts ändert.

#### Die zwei Stellen, an denen es schiefgehen kann

Ein- und Auspacken hängen an genau zwei Punkten, und beide liegen dort, wo
der Gegenstand die Hand wechselt: `beforeSlotChange` packt ein, **solange das
alte Gehäuse noch im Platz steht** — der Aufrufer bekommt danach genau dieses
Stück Blech, mit der Hardware darin. `setItem` packt aus, nachdem das neue
eingetragen ist.

Geschrieben wird dabei direkt in die Platzliste und nicht wieder über
`setItem`: Das riefe erneut in `beforeSlotChange` hinein, und ein Aufräumen,
das sich selbst aufruft, ist ein Aufräumen, das man nicht mehr überblickt.

Der gefährliche Weg ist der Umschalt-Klick: Er nimmt heraus, schiebt, und
legt bei Misserfolg zurück. Beim Herausnehmen packt das Gehäuse ein, beim
Zurücklegen wieder aus — **passiert das Packen erst nach dem Herausnehmen,
hält der Spieler ein leeres Gehäuse und die Hardware ist weg oder doppelt
da.** Dafür steht eine eigene Prüfung mit vollem Rucksack.

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


---

## Keine Viertelkanäle mehr (2026-08-23)

Eine Anzeige kostete ein Viertel Kanal, damit vier an einer Wand sich einen
teilen. Gerechnet wurde deshalb **überall** in Vierteln, und im Spiel stand
dann „12¼ von 16".

**Das versteht niemand.** Ein Kanal ist eine Leitung; eine viertel Leitung
gibt es nicht. Die Bruchzahl war kein Detail der Anzeige, sondern hat die
ganze Rechnung angesteckt — jede Kapazität, jede Last, jeder Test.

Die Anzeige kostet jetzt **gar keinen** Kanal. Die Begründung ist dieselbe
wie beim Router: Sie leitet nichts weiter und schiebt nichts, sie liest nur
mit. Wer sich eine Wand aus zwölf Tafeln in die Halle baut, soll dafür nicht
ein Netz opfern — sie kostet Strom, und das ist der Preis.

Damit sind alle Zahlen wieder ganze, und `Channels` besteht aus fünf
Konstanten statt aus fünf Konstanten plus Umrechnung plus Bruchformatierung.


---

## Das Programm ist ein Projekt (2026-08-23)

Dreihundert Zeilen in einem Stück sind keine Übersicht — und der eingebaute
Editor zeigt neun Zeilen und vierundvierzig Spalten davon. Deshalb hält ein
Controller jetzt mehrere `.mf`-Dateien statt einer Zeichenkette.

### Ein Namensraum, keine Module

Das stand seit dem ersten Tag so da: als Fehlertext beim reservierten
`import` („Alle .mf-Dateien eines Projekts teilen ohnehin einen Namensraum")
und im Javadoc von `Program`. Ein `fn` in der einen Datei ruft ein `fn` in
der anderen, ohne dass irgendwo `import` steht. **Dateien sind Ordnung für
den Menschen, keine Grenze für die Sprache.** Echte Module mit eigenen
Namensräumen sind etwas anderes; dafür bleibt `import` reserviert.

### Jede Datei für sich übersetzt

Bekäme der Übersetzer den zusammengehängten Text, zeigte jeder Fehler ab der
zweiten Datei auf eine Zeile, die es dort nicht gibt — und der Editor
markierte die falsche. Eine Meldung trägt deshalb ihren Dateinamen.

### Dateinamen sind Nutzereingaben

Sie landen im Speicherformat, im Dateisystem und über die Leitung. `../` darin
wäre ein Weg, aus dem Weltordner herauszuschreiben. Das Projektmodell selbst
erzwingt die Regel — Kleinbuchstaben, Ziffern, Unterstrich, Endung `.mf` —,
und zwar an beiden Enden: beim Anlegen im Spiel und beim Einlesen des
Ordners. Eine fremde Datei im Ordner wird übergangen, nicht übernommen.

Kleinbuchstaben, damit zwei Dateien nicht auf einem System verschieden und
auf dem nächsten gleich heißen.

### Alphabetisch, nicht nach Anlagereihenfolge

Sonst hinge die Reihenfolge der Deklarationen daran, wie das Speicherformat
sie zurückgibt, und ein wartender Ablauf verglichen sich nach einem Neustart
mit einem anders sortierten Programm.

### Der Ordner vergleicht Inhalte, keine Zeitstempel

Das löst drei Fälle auf einmal, die einzeln zu behandeln wären: Datei
angelegt, Datei gelöscht, Datei umbenannt — Umbenennen ist von außen nichts
anderes als beides zusammen. Und „zwei Schreibvorgänge in derselben
Millisekunde" verschwindet mit.

**Extern gelöscht heißt gelöscht.** Bei einer einzelnen Datei kam sie beim
nächsten Schreiben zurück, weil man sie kaum absichtlich löscht. In einem
Projekt löscht man ein Programmstück, das man nicht mehr will.

### Alte Welten ziehen um

Die bisherige `controller_….mf` wird beim ersten Laden zur `main.mf` im
Ordner und verschwindet — sonst stünden zwei Wahrheiten nebeneinander, und
die Brücke sähe nur eine.

### Der Dateibaum liegt in einem eigenen Fenster — Umkehr

Gestern stand hier sinngemäß: Umbenennen geht im Ordner neben der Welt, ein
Fenster für einen Dateinamen wäre ein Fenster über einem Fenster, und eine
Dateispalte kostet ein Viertel der vierundvierzig Spalten. Beide Gründe waren
richtig. Die Folgerung war es nicht.

Was daraus wurde, stand als Hinweistext im Spiel: *„Neue Datei — umbenennen im
Ordner neben der Welt."* Ein Fenster, das den Spieler bittet, für einen
Handgriff das Spiel zu verlassen, hat den Handgriff nicht.

Der Fehler war die Annahme, das Terminal müsse alles können. Es ist 288 Pixel
breit, weil darunter das Spielerinventar sitzt und das Slotraster nicht
verhandelbar ist — das ist eine Eigenschaft des Speicher-Reiters, keine des
Editors. Der Editor bekommt deshalb ein eigenes Fenster ohne Inventar und
damit ohne feste Breite: links der Dateibaum, rechts vierzig statt acht
Zeilen.

Der Reiter im Terminal bleibt, was er sein kann: nachsehen und drei Zeilen
korrigieren, ohne das Inventar zu verlieren. Beide arbeiten am selben
Entwurf, der deshalb in `ClientProjectState` liegt und nicht in einer der
beiden Ansichten.

### Umbenannt wird in der Zeile, gelöscht über das Menü

Die Zeile wird zum Eingabefeld. Ein eigenes Fenster für einen Dateinamen
nähme einem beim Tippen aus den Augen, wie die anderen Dateien heißen —
genau das, wonach man sich beim Benennen richtet. Ein Name, den es schon gibt
oder der nicht ins Muster passt, steht rot da, und die Eingabetaste lässt das
Feld offen: Ein Feld, das sich bei einem Tippfehler selbst schließt, tippt man
zweimal.

Gelöscht wird nur über das Kontextmenü, dafür ohne Rückfrage. Zwei Klicks an
einer Stelle, an die man nicht aus Versehen kommt, sind die Rückfrage; ein
Bestätigungsfenster über dem Editorfenster wäre die dritte Ebene.

### Der Dateibaum nimmt keinen Tastaturgriff

Nur die Eingabe beim Umbenennen und F2. Hätte der Baum einen Griff, wanderten
die Pfeiltasten nach einem Klick auf eine Datei zwischen den Dateien statt
durch den Text, und Entfernen löschte eine Datei statt eines Zeichens. In
einem Fenster, dessen Hauptsache ein Editor ist, gehören die Pfeiltasten dem
Editor.

### Die Grammatik liegt einmal als Tabelle vor, nicht als Prosa

`grammatik.md` sagt `row STRING expr`. Im Editor stand das nirgends: Die
Vervollständigung kannte nur Wortlisten je Block und bot hinter `row` wieder
alle Schlüsselwörter an. Wer wissen wollte, was an einer Stelle hingehört,
musste die Doku danebenlegen — und in einem Spiel legt man keine Doku daneben.

`Signatures` ist dieselbe Tabelle als Daten. Sie liegt im Sprachpaket, nicht
im Editor: Sie gehört zur Sprache, und der Editor im Spiel ist nur der erste,
der sie liest. Ein Sprachserver für VS Code liest später dieselbe, und dann
gibt es die Regel weiterhin einmal und nicht zweimal.

Drei Dinge fallen daraus:

**Vorschläge nach der Stelle, nicht nach dem Block.** An einer Zielstelle
Connectoren, an einer Ausdrucksstelle Bestände, hinter `strategy` die
Verteilungen, hinter `button` die Funktionen des Projekts. An einer
Textstelle nichts — einen freien Text kann niemand vorschlagen.

**Die Form steht neben dem Vorschlag.** `row` allein ist ein Wort, das man
nachschlagen muss; `row  string expr` ist eine Anleitung.

**Die Formzeile erscheint von selbst.** Sie ist kein Vorschlag, sondern eine
Beschriftung — man fordert sie nicht an, sie steht da, solange der Cursor in
einer Angabe steht, und markiert die Stelle, die dran ist.

### Warum kein CEF, jedenfalls nicht deswegen

Der Gedanke lag nahe: Chromium einbetten, Monaco laden, und alles, was hier
von Hand steht, ist geschenkt. Er beantwortet aber nicht die Frage, die
gestellt war.

Monaco ist eine Hülle. Dass hinter `row` ein Text kommt und dann ein
Ausdruck, weiß es nicht und kann es nicht wissen — das schreibt man als
Completion-Provider selbst, in TypeScript statt in Java. Die Arbeit, die
diese Nacht gekostet hat, wäre mit CEF dieselbe gewesen; nur läge sie
woanders.

Was CEF wirklich brächte, ist der Rest: Mehrfachcursor, Faltung, Minimap,
IME, geteilte Ansicht. Das steht auf keiner Liste, die jemand vermisst hat.
Was es kostet, ist eine harte Abhängigkeit von einigen hundert Megabyte
nativer Binärdateien je Plattform — in einem Modpack die Sorte Abhängigkeit,
wegen der man eine Mod austauscht.

Die Reihenfolge ist deshalb: erst der Sprachdienst, der ohnehin nötig ist,
dann sehen, was noch weh tut. Bleibt danach „das Widget fühlt sich klobig
an", ist CEF eine ernsthafte Antwort. Ist es weg, haben wir die
Abhängigkeit gespart.

### Entwurf und laufender Stand sind zwei Dinge

Der Editor sicherte bisher nur beim Übernehmen. Übernehmen setzt aber
fehlerfreien Code voraus — und mitten in einer Änderung ist er das nie. Also
war genau der Zustand ungesichert, in dem man die meiste Zeit verbringt.

Der Controller hält deshalb zwei Projekte. Das eine läuft, das andere steht im
Editor. Der Entwurf darf kaputt sein; er wird nicht übersetzt und nicht
ausgeführt. Beim Übernehmen fallen beide zusammen.

Das ist auch die Antwort auf „ein Tippfehler darf die Fabrik nicht
anhalten": Er kann es gar nicht, weil der Entwurf nie läuft.

Geschickt wird der ganze Entwurf, nicht eine Änderungsliste. Ein Programm hier
ist ein paar Dutzend Zeilen und je Datei auf 64 KB gedeckelt; eine Liste aus
Positionen und Einfügungen wäre die Sorte Code, in der sich ein Fehler erst
zeigt, wenn der Text schon kaputt ist. Eine Sekunde nach dem letzten Anschlag
statt bei jedem: Ein Anschlag wäre ein Paket, eine Sekunde Tippen sind fünf
bis zehn — und eine Sekunde Arbeit verliert niemand ungern.

Der Takt hängt am Client, nicht am Bildschirm. Sonst verlöre man das letzte
Zeichen, wenn man das Fenster in derselben Sekunde zumacht.

### Die Griffliste ersetzt die Menüleiste

Ein Editor im Spiel hat keine. Strg+Leertaste, Strg+H und F2 waren da,
lange bevor sie jemand gefunden hätte. F1 legt eine Tafel darüber; jede
Taste schließt sie wieder.

Die Tastennamen stehen fest im Code und nicht in der Sprachdatei — sie
heißen in jeder Sprache gleich. Was sie tun, steht daneben und kommt aus der
Sprachdatei.

### Sperren je Datei, genommen durch Schreiben

Zwei Spieler an einem Controller überschrieben sich wortlos: Beide schicken
den ganzen Entwurf, und wer zuletzt tippt, gewinnt — auch über eine Datei, die
er gar nicht offen hatte.

**Je Datei und nicht je Projekt**, weil zwei Leute an einer Fabrik fast immer
an verschiedenen Stücken arbeiten. Das ganze Projekt zu sperren hieße, dass
einer wartet, obwohl nichts kollidiert.

**Genommen durch Schreiben und nicht durch Öffnen.** Wer eine Datei nur
ansieht, blockiert sie nicht, und niemand muss daran denken, sie wieder
freizugeben. Sie verfällt nach einer Minute ohne Schreiben und beim Schließen
des Terminals.

Der Server nimmt vom eingehenden Entwurf nur an, was der Absender halten darf.
Damit ist der Datenverlust auch dann weg, wenn der Client die Sperre gar nicht
kennt — die Anzeige im Editor ist Höflichkeit, die Regel steht auf dem Server.

Was fehlt: ein Knopf „Bearbeitung anfragen". Wer vor einer fremden Datei
steht, kann heute nur warten.

### Dieselbe Regel zweimal, aber nachgemessen

Die VS-Code-Erweiterung kann nicht in den Java-Code sehen — sie wird kopiert,
nicht gebaut, und ein Bauschritt hieße `npm install`, und dann kopiert sie
niemand mehr. Also liegt die Formentabelle als JSON daneben, erzeugt aus
`Signatures.java`, und ein Test hält beide gleich.

Die *Logik* darüber steht damit trotzdem zweimal da: einmal in Java, einmal in
JavaScript. Das ließ sich nicht vermeiden, also wurde es wenigstens gemessen —
`editor/vscode/check.js` prüft dieselben vierzehn Fälle wie der Java-Test, ohne
Abhängigkeiten. Zwei Fassungen derselben Regel laufen auseinander, wenn niemand
nachmisst.

---

## Manifold bleibt, kein Lua und kein JavaScript (2026-08-24)

Auf die Frage des Projektinhabers, ob eine eingebettete Sprache — Lua,
JavaScript, TypeScript — die eigene ersetzen sollte. Bis dahin war die eigene
Sprache nie gegen etwas abgewogen worden; sie stand von Anfang an im Konzept.

**Ergebnis: Manifold bleibt.** Drei Gründe, nach Gewicht.

### Die Fortsetzung nach dem Neustart hängt an der eigenen Sprache

„Wartender Code überlebt Serverneustarts" ist eine der drei Entscheidungen,
die alles bestimmen. Eingebettete Sprachen können das nicht: LuaJ serialisiert
keine Coroutinen, GraalJS keine Continuations. Im Genre sieht man beide Enden
davon — ComputerCraft persistiert gar nicht, seine Computer starten beim
Weltneustart neu, und Spieler behelfen sich mit Startskripten. OpenComputers
hat es gelöst, aber über eine native Bibliothek zur Lua-Persistenz, und damit
eine dauerhafte Fragilität eingekauft.

Der Unterbau in `runtime/flow` existiert, weil die Sprache für diese
Eigenschaft entworfen wurde.

### Eine fremde Sprache ersetzt nur die imperative Hälfte

`worker`, `display`, `group`, `on` sind Deklarationen — Daten, keine Aufrufe.
Ein Programm hat kein Hauptprogramm, das losläuft. An dieser Hälfte hängt
alles, was die Mod von einem Rechner mit Lager unterscheidet: `Signatures`,
`NetworkCheck`, die Geräteerkennung, die Stromversorgung.

In Lua würde daraus ein Tabellenaufruf, den niemand prüfen kann, bis er
läuft. Oder eine DSL darin — also wieder eine eigene Sprache, nur schlechter
geparst.

### Der Editor lebt davon, dass die Sprache Daten ist

Vervollständigung nach der Stelle, an der der Cursor steht. Namensprüfung
gegen das echte Netz. Die Formzeile. Alles aus einer Tabelle, die die Sprache
beschreibt. Mit Lua bräuchte es einen Sprachserver mit eigenen Typangaben, um
dasselbe schlechter zu erreichen — und im Spiel selbst gar nichts davon.

### Was für den Wechsel gesprochen hätte

Vertraute Schreibweise für alle, die schon programmieren. Keine Sprachpflege:
Jedes Feature kostet heute Lexer, Parser, Signaturen, Interpreter, Editor und
die Tabelle für VS Code — eine Steuer, die bei jeder Erweiterung erneut
anfällt. Dazu ein fertiges Ökosystem.

Der bessere Handel wäre es, wenn die Zielgruppe Programmierer mit großen
Programmen wären. `sprache.md` sagt das Gegenteil: `it` statt Pfeilschreibweise,
weil letztere „für Spieler ohne Programmiererfahrung die größte Hürde wäre",
und das längste Beispielprogramm hat fünfzehn Zeilen.

**Kein Argument war der Umfang des Bestehenden.** Bereits geschriebene Arbeit
ist kein Grund, an einer Entscheidung festzuhalten; die drei Gründe oben
gelten unabhängig davon, wie viel schon dasteht.

### Wann die Frage neu zu stellen wäre

Wenn die Programme wachsen und Bibliotheken nötig werden, oder wenn sich
zeigt, dass die Zielgruppe doch aus Programmierern besteht. Dann wäre nicht
der Wechsel die Antwort, sondern die Frage, ob Manifold ein Modulsystem
braucht.

---

## Strom wird geleitet und gespeichert (2026-08-24)

**Gebaut am 25.08.** Was sich dabei anders ergab — darunter, dass die
Kreativquelle nichts hergab und `priority` bis dahin nichts tat — steht in
`strom.md` §10.

Ergänzt und korrigiert den Eintrag „Strom" vom 2026-08-22. Dort stand, das
Netz nehme Strom an und gebe nichts ab — „ein Netz ist kein Akku, aus dem die
Nachbarmaschine zapft". **Dieser Satz ist überholt.** Auf Wunsch des
Projektinhabers verteilt das Netz Strom an die Maschinen und kann ihn
speichern.

Was bleibt: Strom ist weiterhin die laufende Betriebsabgabe des Netzes selbst,
und die Aufnahme kommt weiterhin als Forge Energy aus dem Pack.

### Abgegeben wird nur, was das Programm sagt

Ein Connector versorgt seine Maschine **nicht** von selbst. Ohne Code fließt
kein Strom — dieselbe Härte wie überall sonst in dieser Mod.

Verworfen: Abgabe von selbst, mit Vorrang im Code nur für den Knappheitsfall.
Sie wäre bequemer, machte das Netz aber zur Stromleitung, die man nebenbei
bekommt. Verworfen ebenfalls: Abgabe von selbst ohne jeden Vorrang — dann
entscheidet eine feste Regel, welche Maschine bei Knappheit ausgeht, und
niemand hat einen Griff daran.

### Als Worker, nicht als eigene Deklaration

Strom ist eine vierte Ressourcenart neben Gegenstand, Flüssigkeit und
Chemikalie. Versorgt wird mit demselben Worker wie alles andere:

```
worker versorgung {
    from network
    to crusher_1
    filter power
    rate 40 per tick
    priority 1
}
```

`rate`, `priority`, `when` und `maintain` gibt es bereits und bedeuten
dasselbe wie sonst; `network` ist bereits ein eingebauter Name. Zu bauen ist
die Ressourcenart, nicht die Schreibweise.

Verworfen: eine eigene Deklaration `power NAME { … }`. Kürzer zu schreiben,
aber sie müsste `rate`, `priority` und `when` ein zweites Mal erklären — zwei
Stellen mit derselben Bedeutung, die auseinanderlaufen.

Verworfen: eine Angabe am bestehenden Worker (`power 40 per tick` neben
`filter tag:c/ores`). Elegant für Maschinen, die ohnehin beliefert werden,
aber Lampen und Pumpen bekommen nie Material und hätten keinen Worker, an den
die Angabe passt.

**Beide Richtungen fallen aus einer Form.** `from akku_1 to network` zieht
Strom aus einem fremden Speicher ins Netz. Dafür braucht es keine eigene
Mechanik.

### Der Vorrat wächst über Zellen im Laufwerk

Eine dritte Zellenart neben Gegenstands- und Flüssigkeitszelle, in denselben
Laufwerken. Folgt dem Satz, der schon steht: Speicher hängt am Laufwerk. Das
Muster ist fertig — `StorageCellItem`, `FluidCellItem`, `CellTier` mit vier
Größen, das Regalfenster, die Bestückung an der Front, `Power.PER_CELL` für
die laufenden Kosten.

Verworfen: ein eigener Akkublock. Er stünde neben dem Laufwerk und täte
dasselbe in anderer Form — zwei Wege, Kapazität hinzuzufügen, mit
unterschiedlicher Bedienung und ohne eine gute Antwort auf die Frage, welchen
man nimmt.

### Die Grenze läuft über den Kabelpfad

Ein Transportlimit gibt es heute nur für die Aufnahme (`InternalBuffer` mit
`Power.MAX_INPUT`). Für die Abgabe kommt es aus derselben Rechnung wie die
Kanäle: Jedes Gerät zieht auf seinem ganzen Weg zum Controller, und
`capacityAt` fragt je Kabelsegment, wie viel dort durchpasst. Strom läuft auf
denselben Pfaden huckepack — der Weg ist ohnehin berechnet.

Dichte Kabel bekommen damit eine zweite Bedeutung: mehr Kanäle und mehr Strom.

### Offen

- Die Regel bei Knappheit. `priority` gibt die Reihenfolge vor, aber nicht,
  ob ein Gerät mit halber Rate weiterläuft oder ganz leer ausgeht.
- Der Eigenbedarf des Netzes muss vor der Abgabe bedient werden, sonst
  schaltet sich das Netz ab, während es Maschinen versorgt.
- Ob die Abgabe in `draw` mitzählt. Sie ist Durchleitung und keine
  Bereitschaft, spricht also dagegen.

---

## Die Schnittstelle für externe Editoren wird zweimal freigegeben (2026-08-24)

Ein Sprachserver für VS Code — Fehlerprüfung und Gerätenamen außerhalb des
Spiels — braucht eine Verbindung zum laufenden Spiel. Diese Verbindung ist
**nicht** von selbst da.

### Warum es die Frage überhaupt gibt

Der Ordner neben der Welt (`ProgramFolder`) liegt **serverseitig**. Im
Einzelspieler ist das der eigene Rechner, und wer will, öffnet ihn in VS Code.
Auf einem Server liegt er dort, wo der Spieler keinen Dateizugriff hat — dort
gibt es heute **keinen** Weg, in einem richtigen Editor zu arbeiten.

Die Schnittstelle reicht genau das nach. Damit gibt sie aber einen Zugang
preis, den der Serverbetreiber nie erlaubt hat, und der nicht nur liest:
Programmtext, der über sie hereinkommt, läuft anschließend in seiner Welt.

### Zwei Stufen, beide nötig

**Der Server sagt, ob es überhaupt erlaubt ist.** Verbietet er es, gibt es
keine Schnittstelle — unabhängig davon, was der Spieler eingestellt hat. Die
Auskunft kommt beim Betreten, nicht auf Nachfrage: Ein Client, der es erst
beim Verbindungsversuch erführe, hätte den Zugang in der Zwischenzeit schon
offen.

**Der Spieler schaltet sie im Client ein.** Auch wenn der Server sie erlaubt,
ist sie zunächst aus. Eine Verbindung, die man nicht aufgemacht hat, soll
nicht offenstehen, bloß weil ein Server nichts dagegen hatte.

Die Reihenfolge ist nicht beliebig: Der Server hat das letzte Wort über sein
Spiel, der Spieler über seinen Rechner. Keiner kann den anderen überstimmen.

### Was daraus folgt

Die Mod bekommt eine Konfiguration, die es bisher nicht gibt — getrennt nach
Server und Client, wie NeoForge es vorsieht. Der Serverteil trägt die
Erlaubnis, der Clientteil den Schalter.

### Sie darf lesen und schreiben — und der Betreiber entscheidet (2026-08-26)

Die kleine Fassung wäre gewesen, nur zu lesen: Namen und Fehler hinaus, mehr
nicht. Sie ist verworfen. Eine Schnittstelle, die den Entwurf nicht setzen
kann, ersetzt den Ordner neben der Welt nicht — und genau dafür gibt es sie.
Wer auf einem Server arbeitet, säße dann weiter im Fenster im Spiel und hätte
daneben eine Anzeige, die ihm sagt, was er dort falsch macht.

**Entschieden wird das aber nicht von der Mod, sondern vom Serverbetreiber, in
der Konfiguration.** Das ist dieselbe Haltung wie bei `protection.programs`:
Was in seiner Welt läuft, bestimmt er, und die Mod fragt ihn, statt es
anzunehmen.

Daraus folgt eine **dreistufige** Einstellung und kein Schalter. Der Betreiber
hat drei Antworten und nicht zwei:

| Stufe | was hinausgeht | was hereinkommt |
|---|---|---|
| aus | nichts | nichts |
| lesen | Namen, Fehler, Anzeigen | nichts |
| schreiben | dasselbe | der Entwurf |

Die mittlere Stufe ist keine Sparsamkeit: Ein Betreiber, der die Auskunft
gerne gibt, aber keinen zweiten Weg will, auf dem Programmtext in seine Welt
kommt, hat damit eine Antwort. Ohne sie müsste er zwischen „gar nichts" und
„alles" wählen, und die meisten wählten dann gar nichts.

Die zweite Stufe aus dem Abschnitt darüber bleibt daneben bestehen: Auch wo
der Server schreiben erlaubt, ist die Verbindung im Client zunächst aus. Der
Server hat das letzte Wort über sein Spiel, der Spieler über seinen Rechner.

**Weiter offen bleibt die Technik.** Wie die Verbindung aussieht — ein Port im
Client, an dem VS Code anklopft, und wer sonst noch anklopfen kann — ist damit
nicht beantwortet. Entschieden ist, *was* sie darf, nicht *wie* sie es tut.

Der **Entwurf dazu steht seit dem 26.08.** in `editorbruecke.md`: drei Wege
gemessen, Empfehlung ist ein Ordner, den der Client führt, statt eines Ports.
Der Einwand gegen den Port ist damit nicht ausgeräumt, sondern umgangen — ein
Port auf `localhost` ist für jedes Programm des Benutzers erreichbar, nicht
nur für das eine, für das er gedacht war.

### Geschaltet wird an beiden Stellen

Konfigurationsdatei **und** ein Schalter im Spiel, beide vollwertig. Auf
Wunsch des Projektinhabers.

Der Einwand dagegen war die Auffindbarkeit in der falschen Richtung: Ein
Schalter im Terminal steht neben Angaben, die nur Gegenstände bewegen, und
öffnet doch einen Port auf dem Rechner des Spielers. Er ist damit der einzige
Griff im ganzen Spiel, dessen Wirkung außerhalb des Spiels liegt.

Verworfen wurde er trotzdem nicht, denn im Einzelspieler ist er belanglos, und
eine Einstellung, die niemand findet, ist keine. **Die Bedenken entscheiden
stattdessen seine Form:** Er sagt beim Einschalten, was er tut — dass ein
Port aufgeht und wer daran horcht —, und er steht nicht zwischen den
Angaben eines Programms, sondern dort, wo es um das Terminal selbst geht.
- ~~**Was die Schnittstelle überhaupt darf.**~~ Beantwortet am 26.08., siehe
  oben: lesen und schreiben, und der Betreiber entscheidet in drei Stufen.
- **Wie sie technisch aussieht.** Ein Port im Client, an dem VS Code
  anklopft — mit der Frage, wer sonst noch anklopfen kann.

---

## Die Dokumentation für Spieler läuft über GuideME (2026-08-24)

Alle vierzehn Dateien in `docs/` sind Entwicklerdokumente — Entwürfe,
Entscheidungen, Spezifikationen. Für jemanden, der die Mod spielt, gibt es
genau eine (`beispiele.md`), und die setzt voraus, dass er das Terminal schon
gefunden hat. Wie man ein Netz baut, was ein Connector ist, warum ein Worker
nichts tut: steht nirgends.

**Gewählt: GuideME** (`org.appliedenergistics:guideme`, LGPL-3.0), auf Hinweis
des Projektinhabers geprüft.

### Warum

Es löst den Widerspruch, an dem jede Doku-Entscheidung hängt: **Markdown ist
schnell zu schreiben und zu pflegen, aber im Spiel liest es niemand.** GuideME
nimmt Markdown mit YAML-Frontmatter als Quelle und rendert es im Spiel — die
Schreibweise bleibt dieselbe wie bei allem in `docs/`, nur ist es dort
lesbar, wo man es braucht: mitten im Bauen.

Dazu kommt, was selbst zu bauen Wochen kostete: Volltextsuche, ein Griff auf
**G**, interaktive 3D-Szenen aus Structure-Dateien, Inline-Rezepte im
JEI-Stil, eine Live-Vorschau beim Schreiben und Übersetzungsunterstützung.

Die 3D-Szenen sind der eigentliche Gewinn für diese Mod: Ein Netz ist eine
räumliche Sache — Controller, Kabel, Connector an der richtigen Seite —, und
genau das lässt sich in Text schlecht erklären. Wer den Aufbau im Buch drehen
kann, versteht in zehn Sekunden, wofür ein Absatz nicht reicht.

**Version und Passung:** `v21.1.17` (Juli 2026) ist die Reihe für NeoForge
21.1, also Minecraft 1.21.1. Aktiv gepflegt, und ohne AE2 lauffähig — es
stammt von dort, hängt aber nicht daran.

**Einbindung** über Maven Central, zweigeteilt wie üblich:
`compileOnly "org.appliedenergistics:guideme:VERSION:api"` und
`runtimeOnly "org.appliedenergistics:guideme:VERSION"`. Registriert wird ein
Buch mit einer Zeile im Mod-Konstruktor.

**Ort der Seiten:** `src/main/resources/assets/factorynetwork/guide/`.

### Verworfen

**Patchouli.** Der verbreitete Weg, aber der Inhalt wird in JSON geschrieben —
eine Auszeichnungssprache, die man nur in Minecraft-Mods antrifft, und die
sich nicht nebenbei lesen lässt. GuideME bringt sogar einen Konverter davon
mit, was für die Richtung spricht.

**FTB Library.** Ebenfalls geprüft und für diesen Zweck falsch: Es ist eine
GUI-Bibliothek mit NBT-Editor und SNBT-Werkzeugen, kein Dokumentationssystem.
Dazu steht es unter proprietärer „visible source"-Lizenz mit CLA-Pflicht für
Beiträge — als Abhängigkeit eine ganz andere Entscheidung als LGPL.

**Nur Markdown im Repository.** Am schnellsten geschrieben, aber niemand liest
es, während er spielt. Und wer es dann doch als Website ausliefert, hat eine
zweite Stelle, die veraltet.

**Eine eigene Lösung.** Die Latte liegt hoch: Suche, Szenen und Rezeptanzeige
sind je für sich ein Projekt.

### Offen

- **Wann geschrieben wird.** Doku zu einer Sprache, die sich noch ändert,
  veraltet schneller als sie entsteht — `sprache.md` beschreibt heute schon
  Dinge, die es nicht gibt. Die Abhängigkeit einzurichten ist klein; der
  Inhalt gehört an einen Punkt, an dem die Sprache steht.
- **Die Lizenz des eigenen Projekts.** Es gibt keine. Solange nichts
  veröffentlicht ist, ist das folgenlos, aber mit der ersten fremden
  Abhängigkeit wird es eine Frage, die einmal zu beantworten ist.
- **Ob die Hilfe im Spiel dorthin wandert.** Die F1-Griffliste im Editor und
  die Formzeile bleiben, wo sie sind — sie beantworten eine andere Frage als
  ein Buch. Ob es daneben noch Tooltips an den Blöcken braucht, entscheidet
  sich erst, wenn das Buch steht.

---

## Das Fertig-Signal heißt `device_output` (2026-08-25)

`umsetzung.md` stellte drei Wege zur Wahl, und gebaut war der konservative:
`device_changed` meldet, dass sich am Inhalt eines Geräts etwas geändert hat,
die Deutung schreibt der Spieler. **Weg (1) kommt dazu — aber mit zwei
Änderungen gegenüber der Beschreibung dort.**

### Gemessen wird gegen den Stand beim Einlegen, nicht gegen leer

Der Einwand gegen Weg (1) lautete: „meldet zu früh, wenn im Ausgang schon
etwas von vorher lag". Er trifft die Fassung, die fragt, ob im Gerät etwas
liegt, das das Netz nicht eingelegt hat. Merkt sich das Netz beim Einlegen
den Stand des Geräts und vergleicht später dagegen, ist Altbestand kein
Thema mehr: Gemeldet wird der Unterschied, und der ist bei einem vollen
Ausgang genauso null wie bei einem leeren. Die Momentaufnahme kostet nichts,
was nicht ohnehin da wäre — `insert()` meldet seit dem 25.08., was
tatsächlich angekommen ist, und angesehen wird ein Gerät nur, solange ein
Einlegen offen ist.

### Der Name sagt, was gemessen wird

Das Ereignis kann „neuer Inhalt seit dem Einlegen" garantieren. „Fertig"
kann es nicht: Eine Maschine, die eine Ladung von acht Stück einzeln
ausgibt, ist nach dem ersten Stück nicht fertig, und keine Messung von außen
weiß, ob noch sieben kommen. **Wo Name und Messung auseinanderfallen, lügt
das Ereignis** — und ein Name, der etwas zusagt, prüft niemand nach. Deshalb
`device_output`.

Die Folge davon ist benannt und in Kauf genommen: Als Ereignis, das
wiederholt fällt, ist ein früher Wurf harmlos — der nächste holt den Rest.
In einem `await device_output` mitten in einem Ablauf läuft der Ablauf nach
dem ersten Stück weiter, und der Rest bleibt in der Maschine stehen. Genau
das ist der Verlust, vor dem die Notiz warnte; mit dem ehrlichen Namen ist
er wenigstens absehbar, statt vom Wort „done" zugedeckt zu werden.

`device_changed` bleibt unverändert daneben stehen. Es beantwortet eine
andere Frage — „hier hat sich etwas geregt" statt „hier ist etwas
dazugekommen, das ich nicht hingelegt habe" — und ist das einzige der
beiden, das ohne vorheriges Einlegen etwas melden kann.

### Verworfen

**Weg (1) unverändert.** Der Fehlalarm bei vorher gefülltem Ausgang ist
vermeidbar, also wird er vermieden.

**Der Name `device_done`.** Er liest sich in Vorlagen besser und trifft, was
der Spieler meint. Er trifft aber nicht, was die Mod weiß, und diese Lücke
fällt dem Spieler erst auf, wenn Gegenstände fehlen.

**Weg (3), die Anbindung je Mod**, bleibt zurückgestellt (`offene-punkte.md`
7.3): am genauesten, und in einem großen Pack sind es Dutzende.

### Beim Bauen beantwortet (25.08.)

**Beobachtet wird jedes Gerät, nicht erst eines nach dem Einlegen.** Die
Grundlinie ist der letzte Blick, und das eigene Einlegen frischt sie sofort
auf. Die Zusage bleibt dieselbe — ein vorher gefüllter Ausgang löst nichts
aus, die eigene Lieferung auch nicht —, aber sie hängt nicht mehr daran, dass
das Netz das Gerät vorher befüllt hat: Eine Baumfarm oder ein Quarry melden
ihren Nachschub genauso. Damit fällt auch die Frage weg, wie lange ein
Einlegen offen bleibt und was beim Serverneustart aus ihm wird.

**Eingang und Ausgang werden nicht auseinandergehalten.** Was im Gerät mehr
wird, gilt als dazugekommen. Das Netz könnte ausprobieren, ob ein Fach
Einlagerung annimmt, und nur ablehnende Fächer als Ausgang zählen — genauer,
aber es hinge daran, dass jede fremde Maschine ihre Fächer sauber beschränkt.
Tut sie es nicht, meldete sie nie etwas, und der Ablauf, der darauf wartet,
hinge ohne Meldung. Wer von Hand etwas nachlegt, löst das Ereignis also mit
aus. Ärgerlich, aber nichts geht dabei verloren.

**Übergeben wird ein Wert, das Gerät** — wie bei `device_changed`. Was
dazugekommen ist, wäre nützlicher, braucht aber einen Wert für „Posten", und
den gibt es bis `offene-punkte.md` 1.16 nicht.

**Verglichen wird nach Art, nicht nach Fach.** Maschinen schieben ihren
Inhalt zwischen Fächern hin und her; fachweise verglichen wäre jedes Umräumen
eine Ausgabe.

**Die Meldung hängt am Inventar, nicht an der Aufrufstelle.** Der Entwurf
ging davon aus, dass alle Wege durch `WorldHost.move` laufen — die Worker
schreiben aber auf eigenem Weg, und beide legen zurück, wenn der Netzspeicher
voll ist. Statt vier Stellen, von denen eine vergessene genügt, damit ein
Ablauf sich selbst weckt, gibt ein aufgelöstes Gerät jetzt ein meldendes
Inventar heraus (`NotifyingHandlers`).

---

## Der Controller bleibt ein Block (2026-08-25)

Sechs Seiten, je ein Strang, ein dichtes Kabel mit vierundsechzig Kanälen —
384 Geräte je Netz. Wer mehr braucht, setzt in AE2 mehrere Controllerblöcke
aneinander und bekommt mehr Außenflächen. Hier hält dieser Block aber
etwas, was ein AE2-Controller nicht hält: Programm, Speicherindex, laufende
Abläufe und Stromvorrat. Sobald es mehrere Blöcke sind, muss einer der
Master sein, und die Frage war, **wie er bestimmt wird.**

**Entschieden ist Weg (3): zwei Blocktypen.** Der Controller ist immer der
Master, bleibt genau einer je Netz und hält alles wie bisher. Daneben gibt es
einen Anbaublock, der ausschließlich Außenflächen für Kabel beisteuert und
nie etwas hält — ohne Controller nebenan tut er nichts. Die Master-Rolle
steht damit am Blocktyp fest statt an Position oder Reihenfolge.

### Warum nicht der Anker unter gleichen Blöcken

Der bekannte Grund: Ein wandernder Anker verlegt den gesamten Zustand in der
zentralsten Klasse der Mod, und ein Fehler dort kostet einem Spieler sein
Programm.

Der zweite Grund ist seit der ersten Empfehlung dazugekommen und wiegt
schwerer, weil er kein Risiko beschreibt, sondern einen Bruch an gebautem
Verhalten: **Das Programm liegt als Datei neben der Welt und heißt nach der
Position des Controllers** (`controller_overworld_10_64_-20.mf`). Die Brücke
zu VS Code sieht im Sekundentakt genau dort nach, und „die Datei bleibt beim
Abbauen liegen" ist eine zugesagte Eigenschaft — wer sich verklickt, setzt
den Block zurück und hat alles wieder. Wandert der Anker, wandert der
Dateiname mit. Wer die alte Datei in VS Code offen hat, schreibt ab da in
ein Programm, das niemand mehr liest, und merkt es nicht: Speichern
funktioniert, im Spiel kommt nichts an.

### Der Preis, offen benannt

Ein Block mehr im Kreativ-Reiter, und der Ausbau sieht nicht aus wie in AE2 —
dort wächst der Controller aus lauter gleichen Blöcken. Das ist der billigere
Preis: Ein zweiter Block ist Arbeit, eine wandernde Zustandshaltung ist ein
Fehler, der erst beim Spieler auffällt.

### Verworfen

**Der unterste, nördlichste Block hält alles.** Feste Regel, immer erklärbar
— aber jeder Block, den jemand darunter setzt, löst einen Umzug samt
Dateiumbenennung aus. Ein Bauhandgriff darf ein Programm nicht verlegen.

**Der zuerst gesetzte hält alles.** Verschiebt die Frage nur: Wird er
abgebaut, muss ein anderer übernehmen, und beim nächsten Laden muss die Welt
wissen, welcher es war — also doch eine gespeicherte Ankerposition, mit
allem, was daran hängt.

### Beim Bau beantwortet (25.08.)

- **Einen Kanal kostet er nicht.** Er schafft welche, statt welche zu
  verbrauchen — die Vermutung von oben trägt.
- **Strom kostet er: 1 FE/t**, wie Laufwerk und Router. Ein Ausbau, der nichts
  kostet, ist keine Entscheidung.
- **Eine Obergrenze gibt es nicht.** Begrenzt wird das von der Bauform und vom
  Strombedarf, nicht von einer Zahl im Code.
- **Er ist nur Fläche.** Kabel verbindet er nicht, Strom nimmt er nicht an.
  Zwei Anbauten nebeneinander bringen Seiten und keinen Draht.
- **Er muss den Controller berühren**, unmittelbar oder über andere Anbauten.
  Diese Frage stand oben nicht und ist die wichtigste: Ließe sich ein Anbau
  ankabeln, wäre er ein beliebig oft setzbarer Kanalvermehrer — sechs neue
  Seiten für einen Block irgendwo im Gelände, und die Kanalgrenze bedeutete
  nichts mehr.

---

## Die Entscheidungsrunde vom 25.08. (2026-08-25)

An einem Abend durchgesprochen, was auf `offene-punkte.md` als **E** stand.
Hier steht, was entschieden wurde und warum — die Zeilen dort tragen von nun
an ein **F** oder ein **Z** und keine Frage mehr.

### Strom: drei Punkte bestätigt, einer gekippt

**Bestätigt: Knappheit strikt nach `priority`.** Kleine Zahl zuerst, jeder bis
zu seiner `rate`, und wer leer ausgeht, geht leer aus. Innerhalb eines
Workers wird nicht gedeckelt — eine Maschine, die 40 FE will und 25 bekommt,
arbeitet langsamer, wie bei FE-Maschinen üblich.

**Bestätigt: `power` wird ein Schlüsselwort.** Strom hat keine Sorten;
`power:` mit leerem Rest wäre eine Lüge über die Form. Wer seinen Connector
`power` nennt, schreibt ihn in Rückstriche.

**Bestätigt: Eigenbedarf zuerst, Abgabe nur im Zustand `RUNNING`.** Ein Netz,
das sich abschaltet, während es Maschinen füttert, wäre absurd; ein Netz, das
hochfährt, versorgt niemanden. Die Abgabe zählt nicht in `draw` — sie ist
Durchleitung und keine Bereitschaft. **Damit ist auch der offene Punkt 2.3
beantwortet:** Der Fall „Netz füllt sich langsam, während Maschinen ziehen"
kann nicht eintreten, weil bei `OFF` und `BOOTING` nichts abfließt.

**Gekippt: die Transportgrenze über den Kabelpfad.** Der Entwurf sah vor,
dass jeder Strom-Worker seine Rate auf seinem Pfad anmeldet und ein zu dünnes
Kabel beim Übernehmen gemeldet wird — Symmetrie zu den Kanälen, und dichte
Kabel hätten eine zweite Bedeutung bekommen.

Der Einwand des Projektinhabers: **Warum überhaupt begrenzen?** Er trägt.
Eine Kabelgrenze wäre eine *zweite* Knappheit neben `priority`, für dieselbe
Ressource. Wer zu wenig Strom bekommt, müsste dann erst herausfinden, ob es
am Vorrat lag oder am Kabel — zwei Ursachen für dasselbe Symptom, und die
zweite sieht man nirgends.

**Es gibt damit genau eine Stromgrenze: die Aufnahme** in den Controller
(`Power.MAX_INPUT` im `InternalBuffer`), und die gibt es schon. Kabel tragen
beliebig viel; Kabelstufen entscheiden allein über Kanäle. Aus dem Entwurf
fallen die Pfadrechnung für Strom und die Meldung beim Übernehmen. Der Preis,
offen benannt: Ein einzelnes dünnes Kabel versorgt eine beliebig große
Fabrik.

**Die Rate bleibt `rate 40 per 1t`** (2.4). `1t` ist eine gültige Dauer, die
Grammatik kann es heute. Ein eigenes Wort `tick` wäre eine zweite
Schreibweise für dieselbe Sache und zöge sofort die Frage nach `per second`
nach sich. In `strom.md` steht an einer Stelle `per tick` — das ist eine
Berichtigung im Entwurf, keine Sprachänderung.

### Sprache: sechs Formen, die kommen

**Die JEI-Schreibweise wird angenommen** (1.13). `item:mekanism:steel_ingot`
neben `item:mekanism/steel_ingot`. Der Grund ist der Alltag: Jeder Spieler
kopiert IDs aus JEI, und dort steht ein Doppelpunkt. Eine Meldung, die bei
jeder kopierten ID erscheint, ist eine Meldung zu viel. Betroffen sind Lexer,
Parser, EBNF, `sprache.md`, die VS-Code-Grammatik und der Guide.

**Gruppen werden ein Wert** (1.14). `crushers.members()` liefert die Geräte,
`pumps.stop()` geht an alle. Damit läuft, was `sprache.md` und `konzept.md`
an mehreren Stellen zeigen. Die Gegenrichtung — die Formen streichen — wurde
verworfen.

**`move` gibt die bewegte Menge zurück** (1.15). Der eigentliche Grund steht
nicht in der Doku, sondern im Code: `crusher.insert(64 item:x)` und
`move 64 item:x from storage to crusher` sind **dieselbe Operation** —
`insertInto` ruft `move` auf. Die eine liefert die angekommene Menge, die
andere warf sie weg. Diese Asymmetrie war der Fehler. Dass damit
`if move … > 0` schreibbar wird, ist hingenommen: `if crusher.insert(…) > 0`
geht heute schon.

**Ein Listeneintrag bekommt `it.item` und `it.amount`** (1.16). Zwei
Angaben, mehr nicht. Damit sind `where`, `sort` und `sum` über einen Bestand
zum ersten Mal benutzbar — `sum()` zählt die Mengen zusammen, statt zu
werfen. Löst nebenbei 6.10: `list` auf einer Anzeige kann mehr werden als
eine `row`.

**Flüssigkeits-Tags heißen `fluidtag:`** (1.3). Nicht `tag:`, das beides
durchsucht. Der Grund ist technisch: An mehreren Stellen muss aus dem
geschriebenen Text allein hervorgehen, ob Gegenstände oder Flüssigkeiten
gemeint sind — `WorkerKind` entscheidet danach den Ausführungspfad,
`FilterKind` die Sorte einer Vorlage, `move` den Weg. Ein `tag:`, das beides
treffen kann, macht genau diese Entscheidung unmöglich.

**`const` kommt** (1.10) und **Listen und Karten als globale Werte** (1.11).
Beides gegen meine Empfehlung entschieden: Ich hatte `const` für eine zweite
Form ohne eigenen Zweck gehalten und globale Listen für Aufwand ohne Anlass.
Der Projektinhaber hat anders entschieden; bei den Listen kommt die
Speicherfrage damit als **Baufrage** wieder hoch — wie sie im Anfangswert
steht, wie sie neben der Welt liegt, was beim Programmwechsel mit ihr
geschieht.

**Der Typprüfer bleibt zurückgestellt** (1.9). Heute wird Literal gegen
Literal geprüft, alles andere fällt zur Laufzeit auf — mit verständlichen
Meldungen, weil jede Stelle weiß, was sie erwartet hat. Ein echter Prüfer ist
ein eigenes Vorhaben über die ganze Sprache.

**`output()` und `busy` werden gestrichen, `send()` gebaut** (1.1).
`move 64 item:x from brecher to storage` nimmt ohnehin nur, was die Maschine
herausgeben will — `extractItem` auf einem Eingangsfach liefert leer, und das
entscheidet die Maschine und nicht diese Mod. `output()` sagte damit dasselbe
noch einmal. Für `busy` gibt es keine Capability; eine Schätzung über „der
Inhalt hat sich zuletzt geändert" wäre eine Vermutung mit dem Anschein einer
Auskunft, und wer darauf wartet, wartet falsch. `send()` an einer Gruppe
fällt mit 1.14 ab, sobald Gruppen ein Wert sind.

### Chemikalien werden ein Kompatibilitätsmodul

Derselbe Weg wie bei GuideME (1.4, 7.1): `compileOnly` gegen Mekanisms API,
`runtimeOnly` fürs Spiel, der Code unter `compat/mekanism`. Ohne Mekanism
läuft die Mod wie heute, und `chemical:` meldet verständlich, dass die Mod
fehlt, statt zu werfen.

Verworfen: Mekanism zur Pflicht zu machen. Für eine Mod, die ausdrücklich
eigenständig ist, wäre das eine große Zumutung. Verworfen ebenso, die
Schreibweise zu streichen — sie steht seit dem Entwurf und wird gebraucht.

### Editor und Umfeld

- **Namen in einem `move` werden geprüft** (2.8), als Warnung wie beim
  Worker. Der Prüfer muss dabei örtliche Namen aussparen — Variablen,
  Parameter, globale Werte, Filter-Vorlagen —, sonst warnt er vor richtigen
  Programmen. Genau das war der Grund, warum es die Prüfung noch nicht gibt.
- **Die Annahme-Probe kommt auch für Flüssigkeiten** (3.2), mit
  `fill(…, SIMULATE)` und den `fluid:`-Angaben aus dem Programm. Kein eigenes
  Vorhaben: mitnehmen, wenn ohnehin an den Flüssigkeiten gearbeitet wird.
- **Ordner im Projekt kommen** (3.5), gegen meine Empfehlung. Mein Einwand —
  alle Dateien teilen einen Namensraum, ein Ordner sortiert also nur — bleibt
  gültig und ist kein Hinderungsgrund. Dateiliste, Anlegen, Umbenennen und
  die Brücke zu VS Code ziehen mit.
- **LDLib2 wird geprüft** (3.6), gegen meine Empfehlung. Ein Prüfauftrag und
  keine Zusage: Die Frage ist, ob künftige Fenster damit schneller gehen —
  nicht, ob die vorhandenen neu gebaut werden.
- **Das Geräteprofil zeigt sich im Analysator** (3.7): was an einer Seite
  hängt — Inventar, Tank, Stromspeicher —, steht neben Kanälen und Kabellast.
- **Der Netz-Reiter zeigt globale Werte nur an** (3.8). Sie dort auch ändern
  zu lassen hieße, dass der Zustand der Fabrik an zwei Stellen umgestellt
  wird und man der Anlage nicht ansieht, wer zuletzt geschaltet hat. Wer
  schalten will, baut einen Knopf auf eine Anzeige.
- **Eine Anzeigetafel darf in eine Maschine sehen** (3.9). Der Preis ist ein
  Blick in eine BlockEntity je Tafel und Sekunde — die Anzeige liest den
  Netzbestand ohnehin in diesem Takt. Ein `?` auf der Tafel, das niemand
  erklären kann, ist der schlechtere Tausch.
- **Die Hilfe im Spiel bleibt neben dem Buch** (6.3). Die F1-Griffliste
  beantwortet „was steht hier, während ich tippe", das Buch „wie funktioniert
  das überhaupt". Zwei Fragen, zwei Orte.
- **Die Lizenz ist in Kraft** (6.2): MIT, Copyright 2026 DevPanda (Florian
  Richter).

### Fächer sind ansprechbar, aber nur ausdrücklich

Aus derselben Runde, angestoßen von der Frage „wie spreche ich die Slots einer
Maschine an?". Bisher gar nicht: Der Connector holt sich die Capability für
**seine** Seite (`getCapability(…, facing.getOpposite())`), und innerhalb
dieser Seite entscheidet die Maschine, was sie hergibt und annimmt. Drei
Rollen an einem Ofen heißen drei Connectoren an drei Seiten.

SFM kann mehr: Dort greift ein Connector auf das ungeteilte Inventar und
adressiert Fächer über ihre Nummer. Technisch ist das eine Zeile —
`getCapability(…, null)` statt einer Seite.

**Entschieden: die Seite bleibt die Vorgabe, Fächer sind der ausdrückliche
Weg.** `move … from ofen` nimmt weiter das, was die Seite hergibt; bestehende
Programme ändern sich nicht, und die Maschine behält ihre eigenen Regeln. Wer
mehr will, schreibt es hin — `from ofen slot 2`, `from ofen slots 0..3` —
und greift damit auf das ungeteilte Inventar.

Der Grund für diese Teilung: Beides hat einen Preis, und der soll dort
sichtbar sein, wo er anfällt. Greift jeder Connector immer aufs ganze
Inventar, fällt der Schutz der Maschine überall weg — ein `move` ohne
Fachangabe könnte dann den Eingang leeren, und man sieht dem Programm nicht
an, dass es das tut. Mit einer Fachnummer steht es da.

Verworfen: ein Schalter am Connector („diese Seite" oder „alles"). Dann stünde
die Bedeutung eines Namens in der Welt statt im Programm, und wer den Code
liest, sähe ihm nicht an, worauf er zugreift.

### Die Form kam vom Projektinhaber: `slots(…)`

Skizziert war eine Angabe am `move` (`from ofen slot 2`). Der Vorschlag des
Projektinhabers ist besser und wurde übernommen:

```
brecher_1.slots(3)
brecher_1.slots(1..5)
```

`slots(…)` steht an derselben Stelle wie `items()` und verhält sich beim
Lesen wie eine Liste von Posten — damit greifen `count`, `sum`, `where` und
`sort` ohne Zutun. **Und dieselbe Form ist Quelle und Ziel eines `move`:**
`move 64 item:gold_ore from brecher_1.slots(3) to storage` räumt den Ausgang
ab und lässt den Eingang stehen. Eine zweite Schreibweise braucht es nicht.

Dafür kommt die Bereichsform `1..5` in die Sprache — eine Liste ganzer
Zahlen, die auch `for fach in 0..8` möglich macht.

**Verworfen: `slots(OUTPUT)`.** Ein Ausgangsfach ist von außen nicht sicher
zu erkennen; nachprüfbar wäre allein „ein Fach, das nichts annimmt", und das
hängt an der Sorgfalt der fremden Mod. Eine Maschine, die alles annimmt,
lieferte eine leere Liste — und die sieht aus wie eine leere Maschine.

**Dieselbe Form legt auch hinein.** `move 8 item:coal from storage to
ofen.slots(1)` bringt den Brennstoff ins Brennstofffach — der eigentliche
Grund, warum ein Anschluss je Maschine reicht. Der Preis ist größer als beim
Lesen: Über ein Fach gelten die Seitenregeln der Maschine nicht, und
`slots(2)` an einem Ofen legt ins Ergebnisfach. Hingenommen und in
`sprache.md` benannt: Wer eine Fachnummer schreibt, sagt damit, dass er die
Maschine kennt.

**Nicht die Antwort: ein zweiter Connector.** Der Projektinhaber hat das
zweimal zurückgewiesen, und er hat recht: Ein Connector kostet einen Kanal
auf seinem ganzen Weg zum Controller. Drei Rollen an einer Maschine über drei
Seiten zu lösen hieße drei Kanäle je Maschine, und damit wäre die
Kanalrechnung die Bremse beim Bauen. **Ein Anschluss je Maschine reicht;
welches Fach gemeint ist, entscheidet der Code.**

### Nicht entschieden, weil es sich nicht entscheiden lässt

Die Zahlen an den Serverbauteilen (5.4) bleiben offen. Ob sich Rechenwerke
von zwei bis hundertachtundzwanzig richtig anfühlen, zeigt eine Runde
Spielen und kein Gespräch.

---

## Die Fertigung wird mehrstufig (2026-08-25)

Der erste Schnitt der Fertigung baute, was aus dem Speicher zu bauen war, und
sagte sonst „es fehlen 8 Bretter" — auch dann, wenn zwei Stämme im Laufwerk
lagen und der Weg dahin ein einziges Rezept war. Der Planner geht diesen Weg.
Vier Entscheidungen sind dabei gefallen.

### Der Plan wird gerechnet, nicht gemerkt

Ein Fertigungsauftrag könnte seinen Plan beim Anlegen berechnen und danach
abarbeiten. Er tut es nicht: Der Controller rechnet ihn bei **jedem
Fertigungstakt neu** und führt davon den untersten Schritt aus.

Der Grund ist der Bestand. Ein gespeicherter Plan wäre ab dem Moment falsch,
in dem ein Worker etwas einlagert — und genau das tun Worker den ganzen Tag.
Ein Auftrag, der um 12 Uhr beschlossen hat, Bretter aus Stämmen zu machen,
soll um 12:01 die Bretter nehmen, die inzwischen aus dem Sägewerk kamen.

Dazu kommt, was nicht gebaut werden musste: Ein Plan im Speicherformat wäre
ein zweites Datenformat mit eigener Fassungsverwaltung, das einen Neustart
überstehen muss. So übersteht der Auftrag den Neustart wie bisher — Ziel,
Menge, Stand — und der Plan entsteht danach neu.

Der Preis ist Rechenzeit: einmal je Sekunde je Auftrag ein Rezeptbaum. Dagegen
steht ein Verzeichnis der Rezepte nach Ergebnis, gebaut einmal je Tick, sowie
zwei Grenzen in der Serverkonfiguration (`craftingDepth`, `craftingBudget`).

### Fehlt ein Grundstoff, wird gar nichts gebaut

Ein Plan, der nicht aufgeht, könnte trotzdem loslegen: die Bretter schon
machen und beim Leder warten. Er tut es nicht — **der Auftrag wartet
vollständig.**

Das Gegenteil ist verlockend, weil es beschäftigt aussieht. Es hinterlässt
aber Zwischenzeug, das niemand bestellt hat: Wer einen Auftrag abbricht,
dessen Leder nie kam, findet einen Stapel Bretter im Lager und weiß nicht,
woher. Und der Vorteil wäre keiner — der Auftrag hinge trotzdem.

### Genannt wird der Grundstoff, nicht die Zwischenstufe

Die Fehlzeile hat sich damit geändert: Aus „es fehlt: 8 Eichenholzbretter"
wurde „es fehlt: 2 Eichenstamm". Das ist der Punkt der ganzen Übung — genannt
wird, was ein Mensch hinlegen muss, und nicht, was das Netz selbst kann.

Zwei Sonderfälle laufen anders herum:

**Der Kreis.** „Barren aus Block" und „Block aus Barren" ist ein Rezeptpaar,
an dem eine Suche ewig läuft. Sie merkt den Kreis und meldet dann **die Ebene
darüber** — bei einem Auftrag über einen Block also die neun Barren. Dort das
zu melden, was gerade in Arbeit ist, hieße dem Spieler zu sagen, es fehle ihm
das, was er bestellt hat.

**Die Grenze.** Wo die Suche wegen `craftingDepth` oder `craftingBudget`
aufhört, steht genau das in der Zeile, wonach sie als Nächstes gesucht hätte.
Das ist etwas, das jemand hinlegen kann — anders als beim Kreis.

### Eine Zutat bleibt eine Auswahl

Eine Zutat ist in Minecraft kein Gegenstand, sondern eine Auswahl: `#planks`,
nicht „Eichenbrett". Die erste Fassung legte sich beim Planen fest — auf die
Sorte, von der am meisten dalag, und wenn von keiner etwas dalag, auf die
erste der Liste. Wer nur Fichtenstämme hatte, bekam „es fehlen 8
Eichenbretter", und im Laufwerk lag das Holz.

Der Planner trägt die Auswahl deshalb durch. Er deckt sie erst aus dem
Bestand — die reichste Sorte zuerst und **gemischt**, wenn keine allein reicht
(fünf Eiche, drei Fichte; von Hand ginge es auch) —, und was offenbleibt,
versucht er zu bauen: eine Sorte nach der anderen, bis eine aufgeht. Ein
Versuch, der scheitert, wird vollständig zurückgenommen, samt seiner
Fehlmeldungen — sonst hielte er der Sorte, die gelingt, den Grundstoff vor.

Geht keine, steht die **erste** in der Fehlzeile. Irgendeine muss es sein, und
die erste ist die, auf die auch ein Spieler zeigen würde.

Der Schritt trägt danach die fertige Entnahmeliste. Der Ausführende entnimmt,
was dort steht, und wählt nicht noch einmal aus: Sonst könnte er sich anders
entscheiden als der Plan, und der Schritt darüber fände nicht vor, was er
erwartet.

---

## `from crafting` bekommt drei Regeln (2026-08-25)

`sprache.md` zeigt Nachschub seit dem Entwurf als gewöhnlichen Worker:

```
worker keep_ingots {
    from crafting
    to storage
    filter item:iron_ingot
    maintain 256
}
```

Das ist der Grund, warum `from` eine Quelle nennt und keine Betriebsart — zwei
Deklarationsformen für „hol es aus dem Lager" und „lass es herstellen" hätten
dieselbe Bedeutung zweimal beschrieben. Beim Bauen stellten sich drei Fragen,
die der Entwurf offengelassen hatte.

### Das Ziel ist `storage`, und nur das

`to <gerät>` hält an und verweist auf `to storage`. Verlockend wäre das
Gegenteil: `from crafting to ofen` läse sich, als bestellte man direkt in die
Maschine.

Dagegen steht, dass der Fabricator kein Ziel hat. Er legt ins Netz, und alles
Weitere ist ein Transport — und für Transporte gibt es bereits einen Worker.
`from crafting to ofen` müsste deshalb entweder zwei Dinge in einer Zeile tun
(bestellen und schieben, mit zwei Zuständen und einem `maintain`, das nicht
mehr weiß, worauf es sich bezieht) oder der Fertigung ein Ziel beibringen, das
sie nicht hat. Zwei Worker sind hier die kürzere Erklärung.

### `maintain` ist Pflicht

Ohne Zahl hieße `from crafting to storage filter item:iron_ingot` „bestelle
Eisenbarren, endlos". Das ist keine sinnvolle Anweisung, sondern ein Programm,
das eine Fabrik in einer Nacht zum Stillstand bringt. Der Worker hält an und
sagt es — dieselbe Antwort wie bei `maintain` ohne `filter`.

### `rate` begrenzt nur, wenn es dasteht

Ein Worker hat einen Vorgabe-Stapel von 64. Ihn auf Bestellungen anzuwenden
hieße: `maintain 256` legt vier Aufträge über je 64 an, weil jede Runde nur
64 durchgehen. Vier Zeilen im Reiter für eine Sache, die eine ist.

Ohne `rate` wird deshalb die ganze Lücke auf einmal bestellt. Steht `rate 64
per 1s` da, gilt es: Wer es schreibt, meint „höchstens so viel je Runde", und
das ist eine Angabe über Bestellungen so gut wie über Bewegungen.

### Der Fehler, der ohne Gegenrechnung entstanden wäre

Der Bestand steigt erst, wenn ein Auftrag fertig ist. Ein Worker, der nur ihn
ansieht, bestellt jede Runde dieselbe Lücke noch einmal — bei einem Takt von
einer Sekunde sind das sechzig Aufträge in der Minute, alle über dasselbe.
Gerechnet wird deshalb gegen Bestand **und** offene Aufträge.

Das ist auch die Antwort auf die Frage nach Reservierungen, die beim ersten
Schnitt offenblieb: Weil der Plan bei jedem Takt neu gerechnet wird, sieht
jeder Auftrag den Bestand, den ihm die anderen gelassen haben. Eine
Vormerkungstabelle bräuchte es erst, wenn ein Auftrag Zutaten über mehrere
Takte hinweg festhielte — und das tut hier keiner.

---

## Ordner im Projekt sind Namen, keine Struktur (2026-08-25)

Beschlossen war „Ordner kommen" (3.5), gegen meine Empfehlung — mein Einwand,
dass alle Dateien einen Namensraum teilen und ein Ordner also nur sortiert,
bleibt richtig und war nie ein Hinderungsgrund. Vier Fragen entschied erst das
Bauen.

### Der Ordner steckt im Dateinamen

`erz/brecher.mf` ist ein Name und kein Baum. Das Projekt bleibt eine Karte von
Namen auf Quelltexte, das Speicherformat bleibt dasselbe, und alte Welten
lesen sich weiter — ein flacher Name ist ein gültiger Name mit null
Abschnitten.

Die Alternative wäre ein Knotenmodell gewesen: Ordner als eigene Sache, mit
Anlegen, Umbenennen, Verschieben und Leersein. Das ist viermal so viel Code
für dieselbe Auskunft, und es bringt eine Frage mit, die sonst gar nicht
entsteht: Was ist ein leerer Ordner in einem Speicherformat, das nur Dateien
kennt?

Deshalb gibt es auch **keinen eigenen Griff „Ordner anlegen"**. Wer einen
Ordner will, tippt einen Schrägstrich in den Dateinamen. Ein Menüpunkt, der
etwas anlegt, das man ohne Datei darin nicht sieht, wäre ein Griff, der nichts
tut.

### Der Punkt steht nicht im Alphabet

Ein Abschnitt darf Kleinbuchstaben, Ziffern und Unterstriche tragen und sonst
nichts. Damit ist `../` nicht verboten, sondern **unmöglich** — und dasselbe
gilt für den Rückstrich von Windows und den Doppelpunkt eines Laufwerks. Eine
Verbotsliste hätte man umgehen können; ein Alphabet nicht.

Dazu eine Obergrenze für den ganzen Pfad, sechsundneunzig Zeichen. Vorher lag
sie bei fünfunddreißig, weil ein Name aus genau einem Abschnitt bestand; ohne
eine neue wüchse mit jeder Ebene das Speicherformat, das Paket über die
Leitung und der Pfad im Dateisystem.

### Die Liste im Spiel bleibt flach

Kein Klappbaum. Der Schrägstrich sortiert vor Buchstaben und Ziffern, also
stehen die Dateien eines Ordners von selbst beieinander — `erz/brecher.mf`
kommt vor `erz2.mf`. Ein Baum bräuchte einen Griff mehr (auf- und zuklappen,
mit einem Zustand, der irgendwo überleben muss) für dieselbe Auskunft.

Gekürzt wird **vorn**: `…/schmelzen.mf`. Von rechts gekürzt sähen zwei Dateien
desselben Ordners gleich aus, und der Name ist das, wonach man sucht.

Die echten Ordner leben da, wo Ordner etwas tun: im Dateisystem neben der Welt
und in VS Code.

### Die Wurzel eines Projekts wird gesucht, nicht angenommen

Die Erweiterung las bisher den Ordner der offenen Datei. In `erz/brecher.mf`
wären das die Geschwister im Ordner `erz` — und nicht `main.mf` eine Ebene
höher, obwohl beide einen Namensraum teilen.

Sie geht jetzt nach oben, **solange der Ordner darüber selbst Programmdateien
enthält**, und hält in jedem Fall an einem Ordner an, dessen Name mit
`controller_` beginnt. Zwei Bedingungen, und beide werden gebraucht: Ohne die
erste liefe sie bei einer einzelnen `.mf` irgendwo auf der Platte bis zum
Stammverzeichnis hinauf; ohne die zweite liefe sie über `factorynetwork`
hinaus und mischte die Namen fremder Controller unter.

### Was dabei auffiel: der Rückstrich

`Path.relativize` liefert auf Windows `erz\brecher.mf`. Im Projekt heißt die
Datei `erz/brecher.mf`, und die Brücke vergleicht Inhalte je Name — sie sähe
also im Sekundentakt eine fremde Datei und zugleich eine fehlende und schriebe
zwei Wahrheiten gegeneinander. Der Name wird beim Lesen auf Schrägstriche
gebracht.

`ProgramFolder` hatte bis dahin keine einzige Prüfung, weil die Klasse einen
laufenden Server verlangte. Sie verlangt ihn nur, um **den Ordner zu finden**
— was mit dem Ordner geschieht, braucht nur einen Ordner. Die beiden Fragen
sind jetzt getrennt, und der Weg hin und zurück steht als Prüfung in einem
Wegwerfverzeichnis.

Ein leerer Ordner bleibt nach dem Löschen der letzten Datei darin liegen. Ihn
wegzuräumen hieße, im Weltordner aufzuräumen, was jemand anders angelegt haben
könnte — und ein leerer Ordner tut niemandem weh.

---

## Die Schrift auf der Wand wächst nicht von selbst (2026-08-26)

Punkt 5.3 stand als Mangel auf der Liste: „Die Anzeigenwand ist gebaut, die
Schrift wächst aber nicht mit." Beim Nachsehen war die Frage längst
entschieden, und die Entscheidung war richtig — sie stand nur im Javadoc des
Renderers und nicht hier.

**Sie wächst weiterhin nicht von selbst.** Der Platz einer großen Wand geht in
mehr Zeilen und längere, nicht in größere Buchstaben. Eine Wand, deren Text
mitwächst, ist aus drei Metern genauso lesbar wie eine einzelne Tafel — dafür
baut niemand zwölf Blöcke.

Der Mangel war ein anderer: Es gab keinen Weg zu dem Fall, den man wirklich
will — eine Überschrift, die man aus zwanzig Metern liest. **Also sagt es das
Programm:** `scale 4` im Display-Block. Wer nichts schreibt, bekommt genau
das, was die Tafel vorher tat.

### Eine feste Zahl, kein Ausdruck

`scale storage.count(…)` wird abgelehnt. Die Größe der Schrift ist Aufbau und
nicht Inhalt; ein Maßstab, der sich beim Zusehen ändert, bräche die Wand jedes
Mal neu um, und was dabei aus dem Bild fällt, sähe wie ein Fehler aus. Es ist
die einzige Angabe eines Displays, die keinen Ausdruck nimmt — und sie ist
auch die einzige, die nichts anzeigt.

Ein Wert außerhalb von 1 bis 8 wird gezogen statt abgelehnt. Null ist keine
Aussage über eine Tafel, sondern ein Tippfehler, und eine unsichtbare Anzeige
wäre die teuerste Art, ihn zu melden.

### Eine Zahl für die ganze Tafel, nicht je Zeile

Eine große Überschrift über kleinen Zeilen wäre der schönere Fall, und er
kostet unverhältnismäßig: ein Maßstab je Zeile heißt eine Zeilenhöhe je Zeile
im Speicherformat, im Paket zum Client und im Umbruch. Der Fall lässt sich
bauen — zwei Wände mit einer Lücke dazwischen, denn Tafeln, die sich berühren,
sind eine Wand. Das ist eine Bauentscheidung von zehn Sekunden gegen ein
Datenformat, das für immer bleibt.

### Die Sichtweite wächst mit

Sechzehn Blöcke war die Entfernung, ab der nicht mehr gezeichnet wird — bei
Normalgröße die richtige Zahl, bei vierfacher nicht. Ohne diese Kopplung wäre
`scale` ein Griff, der die Schrift vergrößert und sie genau dort verschwinden
lässt, wo man sie lesen wollte. Sie ist der Grund, warum der Maßstab am Block
liegt und nicht erst beim Zeichnen aus dem Programm geholt wird: `shouldRender`
läuft je Bild und je Tafel und darf nichts nachschlagen.

---

## LDLib2 geprüft — und nicht genommen (2026-08-26)

Punkt 3.6 war ein Prüfauftrag und keine Zusage: „Die Frage ist, ob künftige
Fenster damit schneller gehen — nicht, ob die vorhandenen neu gebaut werden."
Hier steht, was das Nachsehen ergeben hat, damit die Frage nicht in einem Jahr
noch einmal von vorn beginnt.

### Was LDLib2 ist, Stand heute

Ein vollständiger Neubau der alten LDLib, ausgelegt auf 1.21+ und NeoForge.
Für 1.21.1 gibt es 2.2.x, zuletzt vor Stunden aktualisiert; über zwei
Millionen Downloads auf Modrinth, mehr auf CurseForge. Lizenz LGPL-3.0-only,
was für eine MIT-Mod unproblematisch ist, solange die Bibliothek eine eigene
Mod daneben bleibt — und das ist sie.

Reines Java, kein Kotlin zur Laufzeit. Eine Zeile Gradle über
`maven.firstdark.dev`. Die Oberfläche wird in XML mit LSS-Stylesheets
beschrieben, mit Taffy als Layout, dazu über dreißig fertige Bausteine,
Datenbindung, RPC, HUD-Ebenen — und ein **CodeEditor-Widget mit
Syntaxhervorhebung**, also ausgerechnet das teuerste Stück, das hier von Hand
steht.

Es ist also keine schlechte Bibliothek. Die Absage hat einen anderen Grund.

### Der Grund: Eine Oberfläche lässt sich nicht weich einbinden

Diese Mod verlangt keine fremde Mod. Jade ist `optional` in der
`mods.toml` und wird über eine Anbindung geladen, die ohne Jade gar nicht
erst greift. GuideME wird mit `ModList.isLoaded("guideme")` abgefragt — ohne
GuideME startet die Mod ohne Handbuch statt gar nicht. Mekanism ist als
Kompatibilitätsmodul entschieden (1.4), aus demselben Grund.

Eine Oberfläche geht diesen Weg nicht. Ein Terminal, das es nur gibt, wenn
jemand eine zweite Mod installiert hat, ist kein Terminal. Wer LDLib2 für
Fenster nimmt, nimmt es als **Pflicht-Abhängigkeit** — und damit hängt jeder
Release der Mod an einem fremden Zeitplan, in einem Pack, das die Bibliothek
vielleicht in einer anderen Fassung mitbringt.

Das ist keine Kritik an LDLib2, sondern eine Regel dieses Projekts, die es
schon dreimal angewandt hat.

### Der zweite Grund: Die Fenster sind gebaut

Die Frage lautete, ob **künftige** Fenster schneller gehen. Inzwischen gibt es
kaum noch künftige: Terminal mit seinen Reitern, Codefenster mit Dateibaum und
Editor, Kontextmenü, Anzeigen an der Wand — alles steht. Was auf der Liste
offen ist, braucht überwiegend gar keine Oberfläche: globale Listen (1.11)
sind Sprache, Mekanism (1.4) ist eine Anbindung.

Ein Wort mehr zum CodeEditor-Widget: Es wäre vor drei Wochen ein Argument
gewesen. Heute steht der Editor, er kennt Manifold, seine Vorschläge kommen
aus `Signatures`, und er wird ausdrücklich nicht neu gebaut. Ein Baustein, der
das ersetzt, was schon läuft, spart nichts.

### Was die Entscheidung umstoßen würde

Ein großes neues Fenster mit eigener Interaktion — der wahrscheinlichste
Kandidat wäre eine Rezeptübersicht zu 2.9. Dann lohnt sich ein zweiter Blick,
und der ist billig, weil das Nachsehen jetzt aufgeschrieben ist: Stand, Preis,
Lizenz, Einbindung. Zu prüfen wäre dann vor allem, ob sich ein einzelnes
Fenster darauf bauen lässt, ohne dass die übrigen ihr Aussehen verlieren —
diese Mod hat eine eigene Formsprache aus Mulden und hellen Kanten, und zwei
Oberflächen nebeneinander sähe man sofort.

---

## Eine Liste wird ersetzt, nicht geändert (2026-08-26)

Punkt 1.11 stand als „entschieden: kommen", war beim Bauen aber wieder zur
Entscheidung geworden: Es fehlte eine Schreibweise für eine Liste, und es
fehlte ein Weg, ihr etwas hinzuzufügen. Beides ist jetzt entschieden.

### `[a, b]`, und `[]` gehört dazu

Eckige Klammern, Kommas dazwischen, ein nachgestelltes Komma erlaubt. Sie
zählen im Lexer wie runde: Zwischen ihnen trennt kein Zeilenumbruch, denn eine
Liste aus sechs Namen schreibt niemand in eine Zeile.

Die leere Liste ist der wichtigere Fall. Ein globaler Listenwert fängt fast
immer leer an, und ohne `[]` müsste man ihn mit einem Platzhalter beginnen und
den gleich wieder herausnehmen.

### Ersetzen statt Ändern

**Es gibt kein `add`.** Angehängt wird über eine Zuweisung:

```
warteschlange = warteschlange.plus("eisen")
```

Das ist wortreicher als `warteschlange.add("eisen")`, und beide Gründe dagegen
stehen schon im Code:

**`const` bewacht Zuweisungen.** `stapel = 65` ist ein Fehler beim Übernehmen,
und dieselbe Prüfung greift ohne eine Zeile Zusatzarbeit bei
`sorten = sorten.plus(…)`. Ein änderndes `add` ist keine Zuweisung und liefe
daran vorbei — genauso am Schutz fremder Programme im Mehrspielerbetrieb, der
ebenfalls am Schreibpfad hängt. Der GameTest
`aconstListCannotBeChanged` hält das fest, mitsamt der Gegenprobe, dass
dasselbe Programm ohne die Zuweisung durchgeht.

**Der Neustart trennt Verweise.** Ein wartender Ablauf überlebt ihn über den
`ValueCodec`: Werte werden geschrieben und zurückgelesen. Zwei Namen für
dieselbe Liste sind danach zwei Listen. Mit einem ändernden `add` wäre eine
Änderung vor dem Neustart durch beide Namen sichtbar und danach nur noch durch
einen — ein Unterschied, den niemand erklären kann und den auch niemand sucht.
Mit unveränderlichen Werten gibt es ihn nicht.

Dazu passt, dass alle fünf vorhandenen Operationen es ohnehin so halten:
`count`, `first`, `sum`, `where`, `sort` liefern nur Neues und ändern nie.

### Drei Operationen, kein Index

`plus(x)`, `without(x)` — jedes Vorkommen, verglichen wie mit `==` — und
`rest()`. Mit `first()` zusammen ist `rest()` die Warteschlange: nimm den
vordersten, behalte den Rest.

**`liste[2]` gibt es nicht.** Eine Liste, in die man an beliebiger Stelle
greift, will auch an beliebiger Stelle geändert werden, und dann stünde die
Frage von oben wieder da. Für die Warteschlange reichen `first` und `rest`,
für alles andere `where` und `for`.

### Eine Obergrenze, und sie steht beim Betreiber

Ein globaler Listenwert ist der einzige Wert, den ein Programm in einer
Schleife wachsen lassen kann und der den Neustart übersteht — der kürzeste Weg
zu einer gesprengten Weltdatei. `globalListSize` steht deshalb neben
`stepBudget` unter `limits`, mit derselben Begründung: Grenzen für Nutzercode
gehören dem, der den Server bezahlt.

Geprüft wird beim Zuweisen und an einer einzigen Stelle — `writeGlobal` im
Interpreter, durch die beide Wege gehen, der geradeaus laufende und die
Ablaufmaschine. Auch das ist ein Geschenk der Entscheidung oben: Solange jede
Änderung eine Zuweisung ist, gibt es genau einen Ort für solche Prüfungen.

### Nebenbei: Eine Liste sagt jetzt, was drinsteht

`describe()` lieferte „3 Einträge". Das half niemandem — nicht im Protokoll
und erst recht nicht im Netz-Reiter, der globale Werte anzeigt. Jetzt steht da
`[eisen, gold]`, ab sieben Einträgen gekürzt mit einer Zählung:
`[0, 1, 2, 3, 4, 5, … +3]`. Gezählt und nicht abgeschnitten, aus demselben
Grund wie bei der Aufzählung auf einer Anzeigetafel: Eine Liste, die still
endet, liest sich wie eine vollständige.

### Karten bleiben draußen

Für `Map<K, V>` gibt es keinen Fall in einer Fabrik, und eine Schreibweise
dafür wäre eine Entscheidung ohne Anlass. Hinzufügen lässt sich später leicht,
wegnehmen nicht.

---

## Processing-Rezepte: die Erkundung (2026-08-26)

Punkt 2.9 — „Erkennung von Maschinen-Rezepten" — ist der letzte große Posten,
und das Konzept (§8) hält drei Wege offen: automatisch erkannt, manuell
hinterlegt, oder über mod-spezifische Adapter. **Welcher es wird, ist eine
Produktentscheidung und liegt beim Projektinhaber**; hier steht, was das
Nachsehen ergeben hat, damit sie sich auf Zahlen stützen kann und nicht auf
Vermutungen.

### Automatisch geht nicht — und das lässt sich belegen

In 1.21.1 sieht die Schnittstelle `Recipe` so aus:

```java
default NonNullList<Ingredient> getIngredients() {
    return NonNullList.create();
}
```

**Eine leere Liste als Vorgabe.** Ein Maschinenrezept einer fremden Mod, das
sie nicht überschreibt, meldet „keine Zutaten" — und das tun viele, weil die
Methode für das 3x3-Gitter gedacht ist und ihre eigene Maschine sie nicht
braucht.

Vier weitere Löcher, jedes für sich schon entscheidend:

- **Eine `Ingredient` trägt keine Menge.** Sie ist ein Prüfer und kein Posten;
  bei Werkbank-Rezepten steckt die Menge in der Zahl der Gitterfelder. Ein
  Maschinenrezept über „drei Erze" ist generisch nicht von einem über eines zu
  unterscheiden. NeoForge hat dafür `SizedIngredient` nachgereicht — aber
  `getIngredients()` liefert weiterhin die schmucklose Fassung.
- **Flüssigkeiten und Strom stehen nirgends darin.** Ein Rezept, das Wasser
  und 200 FE braucht, sieht generisch aus wie eines, das nichts braucht.
- **`getResultItem` liefert genau einen Stapel.** Nebenprodukte und Ausgaben
  mit Wahrscheinlichkeit fallen weg — und ein Netz, das für zwei Staub bestellt
  und eines bekommt, wartet für immer.
- **Die Dauer fehlt.** Sie steht in der Maschine, nicht im Rezept.

Dazu kommt ein Hinweis, der schwerer wiegt als jedes Einzelargument: **Es
macht niemand.** AE2 lässt Muster im Musterterminal anlegen, Refined Storage im
Pattern Grid. Beide bieten als Abkürzung an, ein Rezept aus JEI/REI/EMI
herüberzuziehen — aber hinterlegt wird es, und zwar vom Spieler. Zwei Mods mit
zusammen zweistelligen Millionen Downloads haben denselben Weg gewählt; wer
den anderen geht, sollte einen Grund nennen können, den die beiden übersehen
haben. Es gibt keinen.

### Was doch automatisch ginge

Nicht alles ist gleich undurchsichtig. Es gibt Rezeptarten mit **fester,
bekannter Form**, und die lassen sich zuverlässig lesen:

- `AbstractCookingRecipe` — Ofen, Schmelzofen, Räucherofen, Lagerfeuer: genau
  eine Zutat, genau eine Ausgabe, Dauer und Erfahrung stehen dabei.
- Steinsäge (`StonecutterRecipe`): eine Zutat, eine Ausgabe.
- Die eigene Presse dieser Mod.

Der Ofen ist die häufigste Verarbeitungsmaschine des Spiels. Ein Netz, das
Erz zu Barren schmelzen kann, ohne dass jemand es aufschreibt, deckt einen
großen Teil des Alltags ab — und für diesen Teil sind die vier Löcher oben
zugemauert, weil die Form der Rezeptart bekannt ist.

### Der dritte Weg, den diese Mod hat und AE2 nicht

**Muster-Items passen nicht hierher.** Der Fabricator baut ohne, und das ist
eine getroffene Entscheidung mit einer Begründung, die weiterträgt: Was das
Spiel schon weiß, soll niemand abschreiben müssen. Ein Musterterminal wäre der
zweite Ort, an dem eine Fabrik erklärt wird — der erste ist das Programm.

Naheliegend wäre deshalb, ein Processing-Rezept **hinzuschreiben**, in der
Sprache, die es ohnehin gibt:

```
recipe erz_mahlen at brecher {
    in 1 item:iron_ore
    out 2 item:iron_dust
}
```

Das ist derselbe Weg wie bei allem anderen in dieser Mod: kein Klicken, kein
Gegenstand in der Hand, sondern eine Deklaration neben den Workern. Sie steht
im Projekt, sie geht mit der Datei nach VS Code, sie lässt sich versionieren,
und sie ist über den Editor prüfbar — ein Rezept, das auf einen Connector
zeigt, den es nicht gibt, meldet sich beim Übernehmen.

### Die drei Wege, wie sie hier heißen würden

| | Was der Spieler tut | Preis |
|---|---|---|
| **A — nur was sicher lesbar ist** | nichts | Ofen, Schmelzofen, Steinsäge, Presse laufen von selbst; jede Modmaschine geht gar nicht |
| **B — A plus `recipe` im Programm** | schreibt auf, was seine Maschinen können | eine Deklaration mehr in der Sprache; deckt alles ab |
| **C — Adapter je Mod** | nichts, wenn seine Mods dabei sind | ein Kompatibilitätsmodul je Mod, für immer zu pflegen; ohne Modul geht die Mod gar nicht |

**Empfohlen wird B**, und A ist der erste Schnitt davon. C bleibt daneben
möglich: Ein Mekanism-Modul (1.4) könnte seine Rezepte beisteuern, und dann
schreibt niemand sie auf — aber es ist eine Zugabe und keine Grundlage, genau
wie bei den Chemikalien entschieden.

### Entschieden am 26.08.: Weg B

Der Projektinhaber hat **B** gewählt. Das Lesbare geht von selbst, alles
andere schreibt der Spieler auf — als `recipe`-Deklaration im Programm und
nicht als Muster-Item.

### Berichtigt: Steinsäge und Lagerfeuer

Die Liste „was doch automatisch ginge" oben war um zwei Einträge zu lang, und
der Unterschied ist **lesbar** gegen **ausführbar**:

- **Die Steinsäge hat keine BlockEntity.** Ihr Rezept lässt sich lesen, aber
  in den Block hineinschieben kann niemand — es gibt kein Inventar. Sie
  läuft deshalb **am Fabricator**, wie ein Werkbank-Rezept: Beides ist
  Handarbeit ohne Maschine, beides steht im Server, beides ist deterministisch
  und in einem Zug erledigt. Der Fabricator kann damit zwei Rezeptarten — und
  das ist keine Ausweitung seiner Rolle, sondern ihre genaue Beschreibung.
- **Das Lagerfeuer fällt weg.** Kein Gegenstandsspeicher, den ein Connector
  ansprechen könnte, und es ist eine Kochstelle und keine Maschine.

Übrig als **Maschinen mit Wartezeit** bleiben Ofen, Schmelzofen und
Räucherofen — sowie die eigene Presse dieser Mod.

---

## Mekanism: das Teilstück, das jetzt geht (2026-08-26)

Punkt 1.4 ist als Kompatibilitätsmodul entschieden — `compileOnly` plus
`runtimeOnly`, Code unter `compat/mekanism`, ohne Mekanism läuft die Mod wie
heute. Eine Zusage aus diesem Eintrag ließ sich ohne jede Abhängigkeit
einlösen, der Rest nicht. Beides steht hier.

### Gebaut: die richtige Meldung

„Chemikalien sind noch nicht angebunden" stand an drei Stellen — im
Übersetzer, in der Laufzeit und in der Auflösungsanzeige des Editors — und war
die halbe Wahrheit. Es klingt nach einer Baustelle in dieser Mod; in einem
Pack ohne Mekanism gibt es die Chemikalien aber überhaupt nicht, und der
Spieler sucht den Fehler an der falschen Stelle.

`FnMekanism` beantwortet jetzt beides an einer Stelle: **ist Mekanism da**, und
**wie heißt die Meldung dann**. Ohne Mekanism: „Chemikalien brauchen
Mekanism", mit dem Hinweis auf die Modliste. Mit Mekanism bleibt es bei „noch
nicht angebunden", denn das ist dann wieder wahr.

**Ohne geladene Modliste gilt „nicht installiert".** Ein Einheitstest lädt
kein FML — dieselbe Vorsicht wie bei `FnConfig`, und dieselbe Richtung: Die
Vorgabe ist die, die niemanden in die Irre schickt.

### Nachgesehen: die Abhängigkeit gibt es

Mekanism für 1.21.1 steht bei 10.7.15.81 (August 2025) und liegt auf
**ModMaven** mit einem `:api`-Klassifikator — dasselbe Muster, das GuideME hier
schon benutzt:

```gradle
repositories { maven { url = 'https://modmaven.dev/' } }
dependencies {
    compileOnly "mekanism:Mekanism:${mekanism_version}:api"
    runtimeOnly "mekanism:Mekanism:${mekanism_version}"
}
```

Eingebaut ist das **nicht**, und zwar aus einem Grund, der nicht mir gehört:
`runtimeOnly` zieht Mekanism in **jeden** Prüflauf. Ab dann laufen alle 250
GameTests unter einer fremden Mod, jeder Mitwirkende lädt sie herunter, und
jeder Lauf dauert länger. Das ist eine Entscheidung über die
Entwicklungsumgebung und über die Zeit anderer Leute — sie gehört dem
Projektinhaber, nicht dem, der gerade an der Reihe ist.

### Offen: wie weit die Chemikalien gehen

Die zweite Frage ist Umfang, und sie ist Balance:

| | Was es kann | Was es kostet |
|---|---|---|
| **Bewegen** | `move chemical:… from a to b`, `gerät.count(chemical:…)` | ein Auflöser gegen die Chemikalien-Registry und ein Zweig im Worker |
| **Lagern** | dazu Chemikalien im Netzspeicher | eine dritte Zellenart mit eigenen Zahlen, ein Zweig im Index, eine Spalte im Terminal, ein Codec — die Fläche der Flüssigkeiten noch einmal |

Die Zahlen einer Chemikalien-Zelle — wie viele Sorten, wie viel Inhalt — sind
genau die Sorte Frage, die bei den Serverbauteilen (5.4) ausdrücklich einer
Runde Spielen überlassen wurde. Sie hier nebenbei zu setzen wäre derselbe
Fehler an einer neuen Stelle.

**Empfohlen wird der erste Schritt allein: bewegen.** Er ist für sich
brauchbar — Wasserstoff aus dem Elektrolyseur in den Tank ist die häufigste
Aufgabe —, er braucht keine neue Zahl, und er lässt die zweite Entscheidung
offen, bis jemand mit einem Pack davorsitzt.

### Entschieden am 26.08.: beides

Der Projektinhaber hat beide Fragen beantwortet. **Mekanism darf in den
Prüflauf** — die Abhängigkeit kommt als `compileOnly` plus `runtimeOnly` wie
GuideME. Und **Chemikalien werden auch gelagert**: Es kommt eine dritte
Zellenart, gegen die Empfehlung und in Kenntnis dessen, dass ihre Zahlen
gesetzt und nicht hergeleitet sind — dieselbe Lage wie bei den
Serverbauteilen (5.4).

Zwei Dinge, die daraus folgen und beim Bauen gelten:

**Die Chemikalien-Zelle wird immer registriert, funktioniert aber nur mit
Mekanism.** Eine bedingte Registrierung hieße: Wer Mekanism entfernt, verliert
die Zellen aus seiner Welt — samt Inhalt. Ein Gegenstand, den es immer gibt
und dessen Tooltip „braucht Mekanism" sagt, ist die freundlichere Antwort und
erfüllt dieselbe Zusage. Die Item-Klasse darf dafür keinen Mekanism-Typ in
einer Signatur tragen.

**Kein `CellFormat.CHEMICALS` im Kern.** Das statische Feld lüde beim
Initialisieren der Klasse Mekanism-Typen, und dann startet die Mod ohne
Mekanism nicht mehr. Das Format entsteht in `compat/mekanism`.

---

## Was das Bauen an Mekanism lehrte (2026-08-26)

Die Anbindung steht: `chemical:` löst sich auf, bewegt sich, wird gezählt und
lagert in Zellen. Drei Dinge fielen dabei auf, die keine Vermutung waren,
sondern eine Messung.

### Der Kern spricht in Texten, und zwar aus einem harten Grund

Java löst die Klassen einer Signatur beim **Laden** auf. Eine Klasse mit
`Registry<Chemical>` in einem Rückgabetyp ließe sich in einem Pack ohne
Mekanism nicht mehr laden — und mit ihr fiele alles, was sie ruft, bis hin
zum Controller.

Deshalb heißt eine Chemikalie außerhalb von `compat/mekanism`
`"mekanism:hydrogen"`, und `ChemicalStore` ist eine Schnittstelle mit
`String`-Kennungen. Mekanism-Typen stehen in genau drei Klassen, und jede wird
erst betreten, wenn die Modliste die Mod meldet.

Der Preis ist eine Registry-Suche je Frage statt eines Objektvergleichs. Bei
einem Index über ein paar Dutzend Chemikalien ist das nichts.

### Die Rechnung stand schon da

`CellInventory` und `CellFormat` sind seit den Flüssigkeiten offen für den
Typ. Was sich zwischen Gegenständen, Flüssigkeiten und Chemikalien
unterscheidet, ist die Registry und die Größe — keine Zeile der Rechnung mit
Sorten und Mengen. Der Mekanism-Teil der Zellen besteht deshalb aus einem
Format und drei Umrechnungen.

Und `MekanismAPI.CHEMICAL_REGISTRY` ist eine gewöhnliche `Registry`. Das war
die eine Annahme, die alles getragen hätte oder nicht, und sie wurde vor dem
ersten Bau im API-Jar nachgesehen.

### Die Capability wird selbst gebaut

Mekanism hält sie in `mekanism.common.capabilities.Capabilities` — im Bauch
der Mod, nicht im API-Jar. Gegen `common` zu übersetzen wäre eine
Abhängigkeit auf Innenleben ohne Zusage.

NeoForge gibt für denselben Namen und denselben Typ dieselbe Instanz zurück.
Der Name steht in Mekanisms Bytecode: `mekanism:chemical_handler`. Ändert er
sich, findet die Anbindung keine Behälter mehr — ein Ausfall, kein Absturz.

### Ein Rückfall, der zurückgenommen wurde

Beim Schreiben des Prüflaufs nahm ein frisch gesetzter Chemikalientank nichts
an. Die erste Antwort darauf war ein Rückfall: Wenn die Seite nichts hergibt,
den ungeteilten Zugriff nehmen. Sie stand eine Viertelstunde und ist wieder
weg, weil die Messung sie widerlegte — der ungeteilte Handler eines Tanks
lässt sich **lesen**, nimmt aber ebenfalls nichts an. Er ist kein
Hintereingang, sondern eine Auskunft.

Geblieben ist der seitenbezogene Zugriff, wie bei Gegenständen und
Flüssigkeiten: **Die Seitenkonfiguration gehört dem Spieler.** Wer eine Seite
auf „nichts" stellt, will dort nichts.

### Was das für den Prüflauf heißt

Ein Mekanism-Tank, den ein GameTest per `setBlock` hinstellt, gibt an keiner
der sechs Seiten eine Capability heraus — nachgemessen, alle sechs. Ihm fehlt,
was ein Spieler beim Platzieren mitbringt.

Geprüft wird deshalb die **Rechnung** und nicht die Weltverdrahtung: `move`
gegen einen Behälter aus Mekanisms API, mit allem, worauf es ankommt — erst
proben, dann entnehmen, und was der Speicher nicht fasst, bleibt im Behälter.
Wie ein Behälter in der Welt gefunden wird, ist derselbe Weg wie bei
Flüssigkeiten und dort geprüft.

Das ist eine benannte Lücke und kein Versehen: Der Weg von einem echten
Mekanism-Block zum Netz läuft erst, wenn jemand ihn im Spiel hinstellt.

---

## Der Speicherbus steht im Programm (2026-08-26)

Gewünscht war „so wie er in AE2 auch ist". Das Verhalten ist es auch: Der
Inhalt eines fremden Inventars zählt zum Netzbestand, das Netz lagert dort ein
und holt dort heraus, ein Filter sagt, was hinein darf, eine Priorität, wohin
zuerst.

**Was anders ist, ist nur der Ort der Erklärung.** Kein Block mit Fenster,
sondern `store kiste_1 { … }` im Programm — vorgelegt und so entschieden:
„wir können ihn per Code anbinden aber im Endeffekt soll er das machen was er
bei AE2 auch macht."

Der Grund ist derselbe wie bei den Rezepten: Ein Filter in einem Fenster ist
nicht versionierbar, geht nicht mit der Datei nach VS Code, und ein Vertipper
darin fällt niemandem auf. Als Zeile im Programm meldet sich ein unbekannter
Gerätename beim Übernehmen, und der Filter kann alles, was die Sprache kann —
Tags, Platzhalter, Vorlagen, `except`.

**Durchgereicht und nicht gespiegelt.** Eine Kopie ist falsch, sobald jemand
die Kiste anfasst, und ein Auftrag, der darauf rechnet, hinterließe genau den
halben Stapel Zwischenzeug, den die Fertigung vermeidet. Gelesen wird einmal
je Tick — nicht bei jeder Frage, sonst zählte jeder Worker jedes Inventar neu.

**Der Filter gilt nur fürs Einlagern.** Was schon drinliegt, gehört zum
Bestand und ist erreichbar. Es zu verschweigen, weil es nicht zum Filter
passt, wäre eine Lüge über etwas, das jeder sehen kann — und ein Bestand, aus
dem man nichts holen kann, wäre die schlimmere Hälfte davon.

**Nichts wandert.** Der Inhalt bleibt in der Kiste und wird nicht in die
Zellen gespeichert; das täte Minecraft doppelt. Wer die Zeile löscht, hat
seine Kiste zurück.

---

## Der Stromanschluss gibt auch heraus (2026-08-26)

Bisher nahm er nur an, und die Begründung stand daneben: Wer sein Kabel an den
Controller legt, soll den Vorrat füllen. Herausgeben war nicht verboten,
sondern nie gebaut.

Der Weg hinaus gab es trotzdem — ein Worker `from network to energy_cube`,
und daran der fremde Anschluss. **Der Umweg ist der Grund für diese
Änderung:** Ein Energiewürfel hat seine eigene Übertragungsrate, und die wird
zum Engpass für alles, was dahinter hängt. Auf Hinweis des Projektinhabers,
dem an der Rate mehr liegt als daran, dass niemand das Netz leersaugt.

**Keine Ratengrenze für die Abgabe.** `MAX_INPUT` galt der Aufnahme und bleibt
dort. Nach außen wäre sie genau das, was stört.

**Ein Boden statt einer Rate.** Gezogen wird bis auf die Anlaufschwelle.
Darunter ginge das Netz aus, führe drei Sekunden hoch und ginge wieder aus —
ein Flackern, das wie ein Fehler aussieht und keiner ist. Das ist keine
Sicherheitsgrenze gegen fremden Zugriff, sondern die gegen einen Zustand, den
niemand haben will. Wer den Rest auch noch will, schaltet das Netz ab.

**Wer selbst begrenzen will**, hängt statt des Kabels einen Worker mit `rate`
dazwischen. Die Grenze liegt dann beim Spieler und nicht im Block.

---

## Der Connector klickt, statt einer zu werden (2026-08-26)

Gewünscht war „eine weitere Variante von den Connector, der z. B. auch Klicks
ausführen kann". Gebaut ist die Fähigkeit und nicht die Variante:
`altar.click()` an dem Connector, der ohnehin dranhängt.

**Der Grund ist derselbe wie bei Ein- und Ausgang.** Dort war eine zweite
Bauform schon einmal die naheliegende Antwort und wurde abgelehnt: Was ein
Gerät tut, entscheidet der Code und nicht, welchen Block jemand hingestellt
hat. Eine dritte Fähigkeit ändert daran nichts — sonst stünden irgendwann drei
Connectoren an derselben Maschine, und welcher wofür ist, wüsste niemand mehr.

**Über den vollen Weg.** Nicht `useWithoutItem` direkt, sondern der Umweg über
einen Platzhalter-Spieler und `useItemOn`. Das ist umständlicher und der
einzige Weg, auf dem `PlayerInteractEvent.RightClickBlock` ausgelöst wird — und
daran hängen die Schutzmods. Diese Mod schützt die Welt nicht selbst; dann
muss sie wenigstens denen den Weg lassen, die es tun. Der Platzhalter trägt
eine feste Kennung, damit eine Claim-Mod ihn freischalten kann.

**Ein Fenster geht nicht auf**, und das steht in der Doku. Ein Klick auf eine
Kiste meldet Erfolg und tut nichts — für einen Spieler, den es nicht gibt, ist
`openMenu` folgenlos. Wer an ein Inventar will, nimmt `move`.

---

## Strom gehört nicht in ein Rezept (2026-08-26)

Als die Flüssigkeiten in `recipe` einzogen, stand Strom auf derselben Liste —
`in 1000 power`, dieselbe Zeile, dieselbe Mechanik. Geprüft und
**zurückgestellt**, aus vier Gründen:

**Die Verteilung tut es schon.** Seit dem 25.08. versorgt das Netz seine
Maschinen von selbst. Ein Rezept-Gate wäre doppelte Buchführung über dieselbe
Ressource.

**Das Argument für Flüssigkeiten trägt hier nicht.** Dort galt: Die Zeile
behauptet einen Verbrauch, also muss er stattfinden. Wasser wird je Durchgang
verbraucht und lässt sich einfüllen. Strom zieht die Maschine je Tick, und wie
viel, hängt an ihren Upgrades und an der Mod — die Zahl im Rezept wäre geraten
und nirgends nachprüfbar.

**Ohne das Feature ist das Verhalten schon ehrlich.** Eine unversorgte
Maschine liefert nicht, und der Auftrag wartet beim Abholen — derselbe Fall
wie der Ofen, dem der Spieler den Brennstoff hinlegt.

**Ein Warte-Gate hätte sich verklemmt.** Genau wie der verworfene
Nur-prüfen-Entwurf bei den Flüssigkeiten: Die Verteilung senkt laufend den
Vorrat, auf den gewartet würde.

Was bleibt, ist die **Meldung**. `in 1000 power` parst weiterhin, denn `power`
ist eine Auswahl wie jede andere; beim Übernehmen steht jetzt eine Warnung
daneben. Seit die Flüssigkeit in der Zeile darüber wirklich eingefüllt wird,
wäre das Schweigen irreführender als vorher.

---

## Der Rückweg der Brücke läuft über den Ordner (2026-08-26)

Punkt 4.1 verlangt Fehlerprüfung und Gerätenamen in VS Code. Beides kann die
Erweiterung nicht allein: Fehler kennt nur der Übersetzer, und der ist in
Java; Gerätenamen kennt nur die Welt, und die läuft im Spiel.

Die Entscheidung vom 24.08. sah dafür eine **Schnittstelle mit zwei
Erlaubnisstufen** vor — ein Port im Client, den der Server erlauben und der
Spieler einschalten muss. Sie steht weiter, und sie ist weiter offen: Was sie
darf und wie sie technisch aussieht, ist nicht entschieden.

**Gebaut ist etwas anderes, das keine dieser Fragen aufwirft.**

### Der Kanal, den es schon gibt

Die Brücke funktioniert in eine Richtung längst: Wer in VS Code speichert,
dessen Programm übernimmt der Controller im Sekundentakt. Zurück kam nichts —
ein Fehler stand im Terminal, und wer nicht im Spiel war, sah eine Datei, die
stumm nicht lief.

Der Controller schreibt jetzt `.fn-status.json` neben die Programmdateien:
die Fehler mit Datei, Zeile und Spalte, dazu die Namen der Connectoren und
Anzeigen. Die Erweiterung liest sie und trägt beides ein.

**Kein neuer Zugang.** Wer die Programmdateien sieht, sieht auch diese — mehr
gibt sie nicht preis. Damit berührt sie die Zwei-Stufen-Entscheidung nicht:
Die galt einem Port, und es gibt keinen.

**Der Preis, offen benannt:** Das gilt nur, wo jemand an die Dateien kommt —
im Einzelspieler und auf einem Server, zu dem man Dateizugriff hat. Genau die
Lücke, die die Schnittstelle einmal schließen soll. 4.1 ist damit **zum Teil
gebaut** und nicht erledigt.

### Kein zweiter Übersetzer

Der naheliegende andere Weg wäre gewesen, die Prüfung in JavaScript
nachzubauen. Das ist dieselbe Falle wie bei der Formtabelle: Zwei Fassungen
derselben Regeln laufen auseinander, sobald niemand nachmisst. Dort hilft ein
Test, der `signatures.json` aus `Signatures.java` erzeugt; hier gäbe es nichts
Vergleichbares — ein Übersetzer ist keine Tabelle.

Also rechnet der, der es ohnehin tut. Die Erweiterung übersetzt nur noch
Zahlen: Zeile und Spalte zählen im Spiel ab eins, in VS Code ab null.

### Geschrieben wird bei Änderung, nicht im Takt

Ein Merker am Controller — gesetzt beim Übernehmen, beim gescheiterten
Übernehmen und beim Neuaufbau des Netzes. Ohne ihn baute der Controller die
Datei je Sekunde neu, um sie mit sich selbst zu vergleichen. Und
`ProgramStatus` schreibt zusätzlich nur, wenn sich der Inhalt unterscheidet:
Ein Dateiwächter in VS Code würde sonst jede Sekunde geweckt.

**Gerade der gescheiterte Fall zählt.** Vorher stand dort nur eine Zeile im
Spiel-Log; wer in VS Code arbeitete, sah gar nichts.

### Von Hand geschrieben, ohne JSON-Bibliothek

Die Mod hat keine im Übersetzungspfad, und der Inhalt ist flach: zwei Listen
und eine Karte. Ein Schreiber von vierzig Zeilen ist billiger als eine
Abhängigkeit — und was ihn lesen muss, ist ohnehin JavaScript. Der Leser auf
der Java-Seite existiert nur, damit sich das Format nachmessen lässt.

## Die drei Zwillinge werden eine Form (2026-08-26)

Schnitt 1 aus `ressourcenarten.md`. `Value` trug drei Paare nebeneinander —
`ItemValue`/`Selection`, `FluidValue`/`FluidSelection`,
`ChemicalValue`/`ChemicalSelection` —, und mit ihnen drei Zweige an jeder
Stelle, die Werte behandelt. Jetzt sind es zwei Records mit einem Art-Feld:
`Resource(kind, key)` und `Selection(kind, keys, amount)`, dazu ein
`ResourceKind`, in dem steht, was je Art verschieden ist.

Die Messung stand seit dem 26.08. im Entwurf: zehn Stellen für eine neue Art,
neun davon Kopien. Was dort **nicht** stand, ist der Grund, der beim Bauen
sichtbar wurde.

### Kopien laufen auseinander, und diese waren es schon

`move` entscheidet an der Art, welchen Weg eine Ressource nimmt. Die Frage
danach stand zweimal da — `isFluidRequest` und `isChemicalRequest` —, und die
beiden kannten verschiedene Ausschnitte des Wertemodells: Die
Flüssigkeitsfrage hatte irgendwann den Nachtrag für die **schon aufgelöste**
Auswahl bekommen, die Chemikalienfrage nicht.

Damit lief das hier ins Leere:

```
for gas in chemical:mekanism/hydrogen {
    move 100 gas from kiste to depot
}
```

`gas` ist nach der Schleife eine aufgelöste Chemikalienauswahl. Die
Chemikalienfrage sah sie nicht, also ging der Aufruf in die
Gegenstandsauflösung — dort traf die Auswahl nichts, und **keine Auswahl heißt
dort „alles"**. Die Kiste wurde leergeräumt. Derselbe Fehler steckte in
`count` und in `gerät.count(…)`, und ein bloßer `ChemicalValue` ohne Menge
traf ihn ebenso wie ein bloßer `FluidValue`.

Das ist der Fehler, den `sprache.md` als den schlimmsten der Sprache
bezeichnet: Ein Programm tut etwas anderes als das, was dasteht, und sagt
nichts. Er ist nicht durch Unachtsamkeit entstanden, sondern durch die
Bauform — bei drei Kopien wird die dritte irgendwann vergessen. Jetzt gibt es
die Frage einmal (`ResourceKind.of(Value)`), und sie kann keine Art übersehen.

### Der Träger ist `Object` und kein gemeinsamer Obertyp

Einen solchen gäbe es nur, wenn alle drei aus derselben Hand kämen. Ein
Gegenstand ist ein `Item`, eine Flüssigkeit ein `Fluid` — und eine Chemikalie
ist ein `String`, weil eine Signatur mit einem Mekanism-Typ die Klasse beim
Laden auflösen würde und ohne die Mod nichts mehr liefe. Ein eigener
Umschlagtyp über allen dreien wäre eine vierte Klasse, die nichts kann außer
die Frage zu verschieben.

Was `Object` an Sicherheit kostet, holt der Konstruktor zurück: Er prüft jeden
Eintrag gegen `ResourceKind.type()`. Eine Auswahl über Wasser und Stein gibt
es damit nicht — dieselbe Regel, die `FilterKind` für Vorlagen aufstellt, nur
jetzt auch für den Wert. Wer die Ressourcen herausholt, nimmt `items()`,
`fluids()` oder `chemicals()`.

### Auf der Platte ändert sich nichts

Ein wartender Ablauf liegt in der Welt, und seine Variablen liegen darin als
NBT: `item`, `sel`, `fluid`, `fluidsel`, `chem`, `chemsel`. Die Namen sind
unregelmäßig — gewachsen, nicht entworfen —, und sie bleiben es. Sie
geradezuziehen hieße, alten Welten ihre Abläufe zu nehmen, und dafür ist eine
saubere Tabelle kein Preis wert. Jede Art trägt ihre beiden Namen jetzt selbst,
und ein Test hält sie fest, indem er ein **von Hand gebautes** Tag einliest und
wieder hinausschreibt. Ein Rundlauf durch den eigenen Schreiber hätte nichts
bewiesen: Der ist mit sich selbst immer einig, auch wenn beide Seiten
gemeinsam abgedriftet sind.

### Was Schnitt 1 ausdrücklich nicht anfasst

**Die Sprachfläche.** `item:iron_ore` heißt weiter `item:iron_ore`, `it.item`
weiter `it.item`. `Signatures`, `signatures.json`, die Referenzseite und beide
Editoren sind unverändert — hätte der Test sie neu geschrieben, wäre das ein
Zeichen für einen Fehler und nicht für den bekannten Zwei-Lauf-Umstand.

**Die drei Speicher.** `NetworkStorage`, `NetworkFluids` und `ChemicalStore`
erfüllen weiter dieselben vier Methoden dreimal, und `WorldHost` verzweigt an
drei Stellen nach der Art dorthin. Das ist Schnitt 2 und steht so im Entwurf.

**Die Registry.** Die Haltungsfrage aus Abschnitt 6 des Entwurfs ist damit
weder beantwortet noch vorweggenommen. `ResourceKind` ist ein
Aufzählungswert. Wird die Frage mit Nein beantwortet, bleibt er einer — und
der Code ist trotzdem kleiner.

## Die drei Speicher bekommen eine Schnittstelle (2026-08-26)

Schnitt 2 aus `ressourcenarten.md`. `NetworkStorage`, `NetworkFluids` und der
Chemikalienspeicher beantworten dieselben Fragen; jetzt steht die Frage
einmal (`ResourceStore`) und die Antwort dreimal. `NetworkStores` hält sie
nach `ResourceKind`.

### Die Schnittstelle war schon da

`ChemicalStore` gab es seit dem 26.08., und zwar aus einem Grund, der mit
Aufräumen nichts zu tun hatte: Der Chemikalienspeicher fasst Mekanism-Typen
an, und ein Feld mit einem solchen Typ ließe den Controller in einem Pack ohne
die Mod nicht mehr laden. Diese Not hatte bereits die richtige Form
hervorgebracht. Sie zu verallgemeinern war weniger Arbeit, als eine neue zu
entwerfen — und ihre Begründung steht weiter dort, wo sie noch gilt.

### Der Schlüssel ist ein `Object`, wie im Wertemodell

Dieselbe Entscheidung und dieselbe Begründung wie einen Commit vorher: Einen
gemeinsamen Obertyp über `Item`, `Fluid` und `String` gibt es nicht, und ein
eigener Umschlagtyp wäre eine Klasse, die nur die Frage verschiebt. Anders als
beim Wert gibt es hier keinen Konstruktor, der die Form prüft — ein falscher
Schlüssel fällt als `ClassCastException` auf. Das ist gewollt: Eine stille
Null wäre ein Bestand, der ohne Meldung nicht gefunden wird.

### `room` gibt es jetzt auch für Gegenstände

Es fehlte, und das war kein Versehen: Die Frage „wie viel ginge noch hinein"
wird gestellt, **bevor** ein Behälter geleert wird, und sie wird nur gebraucht,
wo sich nichts zurücklegen lässt. Ein Gegenstand lässt sich zurücklegen, ein
Gas nicht.

Die Schnittstelle verlangt sie trotzdem — eine vierte Art wäre eher ein Gas
als ein Gegenstand. Für Gegenstände zählt sie **die Zellen und nicht die
Speicherbusse**: Eine fremde Kiste hat kein Probieren, nur ein Ablegen. Die
Antwort ist damit zu niedrig und nie zu hoch, und das ist die Richtung, in der
sie falsch sein darf.

### `NetworkStores` steht im Netzpaket und kennt `ResourceKind`

Damit zeigt `network` auf `runtime`, das ohnehin auf `network` zeigt. Der
Preis ist ein Ring zwischen zwei Paketen; die Gegenrechnung wäre ein zweiter
Aufzählungswert im Netzpaket — also genau der Zwilling, den diese beiden
Schnitte abschaffen. Der Bestand gehört dem Netz, und deshalb steht er dort.

### Was ausdrücklich nicht angefasst wurde

**Die Maschinenseite.** `IItemHandler`, `IFluidHandler` und Mekanisms
`IChemicalHandler` gehören verschiedenen Mods und heißen an jeder Methode
anders. Sie sind die zweite Achse, und eine Registry braucht beide — hier
steht nur die erste.

**Die gemeinsame Index-Mechanik.** Die drei Speicher tun innen fast dasselbe,
und man könnte es einmal hinschreiben. Dagegen spricht der Stand: Die Commits
dieses Tages sind test-grün und ungespielt, und der Speicher ist die Stelle,
an der ein Fehler einen Bestand kostet statt einer Meldung. Der Schnitt ist
benannt und gemessen; er wartet, bis jemand gespielt hat.

**Die drei Auflöser in `WorldHost`.** `itemsOf`, `fluidsOf` und `chemicalsOf`
sehen aus wie Zwillinge, sagen aber Verschiedenes, wenn nichts getroffen wird.
Sie stehen hinter einem `keysOf`, das nach der Art aussucht; zusammengelegt
werden sie erst, wenn jemand die Meldungen gleichmachen will — und das wäre
eine Verschlechterung.

## Fremde Mods dürfen die Sprache erweitern (2026-08-26)

Die Haltungsfrage aus `ressourcenarten.md` §6, seit dem 24.08. offen, ist
beantwortet: **Ja.** Die Ressourcenart wird eine offene Registry statt eines
festen Aufzählungswerts.

### Was den Ausschlag gab

**Ars Nouveau geht sonst gar nicht.** Source ist eine Art, die der Kern nicht
kennen kann — mit einem festen Aufzählungswert käme sie nur als
Kompatibilitätsmodul in *diese* Mod. Das hieße: Für jede Mod, die jemand
anbinden will, schreibt der Projektinhaber selbst Code, oder es gibt sie
nicht.

**Zwei große Netze sind unabhängig zum selben Schluss gekommen.** AE2 hat
`AEKeyType`, Refined Storage hat `ResourceType`, beide für 1.21.1.
`Applied-Mekanistics` bringt so die Chemikalien hinein, `arseng` das Source —
und AE2 musste für keine der beiden angefasst werden. Das ist kein Beweis,
aber es ist das stärkste Argument, das ein Umfeld liefern kann.

**Der Preis war schon bezahlt.** Schnitt 1 und 2 sind am 26.08. gebaut worden,
beide unabhängig von dieser Frage. Was die Registry teuer gemacht hätte — die
Zwillinge im Wertemodell, die drei Speicher — ist durch.

### Was daran nicht umkehrbar ist, offen benannt

**Was einmal registriert werden darf, lässt sich nicht mehr einsammeln.** Ein
Programm mit `source:mana` läuft in einer Welt, deren Kern das Wort nie
gesehen hat. Wer die Öffnung später zurücknähme, bräche fremde Programme.

**Der Übersetzer kennt die gültigen Präfixe erst zur Laufzeit.** Heute weiß
er, dass `chemiacl:` ein Tippfehler ist. Danach weiß er es nur, wo die
Registry erreichbar ist — im Spiel ja, in VS Code über die Statusdatei, und
ohne sie fällt die Erweiterung auf die eingebauten vier zurück und sagt es.

Beides war vorher benannt und ist mit der Antwort angenommen.

### Was die Registry nicht mitbringt

Sie beantwortet **eine** von zwei Achsen. Ein Eintrag sagt, wie seine Art
aussieht, wie sie sich auflöst und wo sie lagert — nicht, wie man sie an einer
fremden Maschine liest und schreibt. Dafür stehen `IItemHandler`,
`IFluidHandler` und Mekanisms `IChemicalHandler` nebeneinander, und sie haben
nichts miteinander zu tun.

Das ist keine Nachlässigkeit, sondern die Grenze dieses Schnitts, und sie
steht hier, damit der Beweis aus §5 Schritt 4 richtig gelesen wird: Wenn Ars
Nouveau später an dieser Achse hängenbleibt, ist das keine unfertige Registry,
sondern die zweite Achse, die es noch nicht gibt.

## Eine Anlage darf auch ein Block sein (2026-08-26)

Bisher entstand eine Anlage allein über die Beschriftung: `werk_1/eingang`,
`werk_1/ausgang`. `anlagen.md` nannte das ausdrücklich einen Vorteil — „keinen
weiteren Block, keinen Bereich, den man abstecken müsste".

**Auf Wunsch des Projektinhabers gibt es jetzt beides.** Der Satz oben stimmt
weiter für den, der ihn nutzen will; er ist nur nicht mehr die einzige
Antwort.

### Was den Ausschlag gab

**Eine Anlage ist etwas Zusammenhängendes.** Das ist die Beobachtung, aus der
der Wunsch kam, und sie ist richtig: Zwölf Maschinen, die eine Sache tun,
stehen im Spiel beieinander. Der Namensweg verlangt, dass man den
Anlagennamen zwölfmal wiederholt — und beim Umbenennen zwölfmal hingeht.

Der Gateway macht daraus einen Block. Was hinter ihm am Kabel hängt, gehört zu
seiner Anlage.

### Die Beschriftung gewinnt

Trägt ein Gerät selbst einen Schrägstrich, gilt der, und der Gateway rührt ihn
nicht an. Das ist die wichtigste der drei Regeln: Ein hingestellter Block darf
nicht still verschieben, was ein Programm über ein Gerät sagt. Wer die
Reihenfolge umdrehte, bekäme einen Fehler, den man nur findet, indem man den
Block wieder abbaut.

### Zwei Gateways auf einem Gerät heben sich auf

Dann steht nicht fest, welche Anlage gemeint ist. Das erste zu nehmen hinge an
der Suchreihenfolge, und die ist keine Erklärung, die ein Spieler lesen kann.
Das Gerät gehört dann zu keiner Anlage — so, als stünde kein Gateway da.

### Er vermehrt keine Kanäle

Dieselbe Regel, an der schon der Controller-Anbau hängt: Ein Kanalvermehrer
zum Hinstellen machte die Kanalgrenze bedeutungslos, und die Kanalgrenze ist
der Ausgleich dieser Mod. Der Gateway trägt so viel wie ein dichtes Kabel.

### Wo der Name entsteht

Im **Graphen**, beim Aufbau — nicht im Wertemodell und nicht in der Sprache.
Für alles dahinter heißt das Gerät `werk_1/eingang`, genau wie beim
geschriebenen Namen: Der Interpreter, die Anlagenerkennung und beide Editoren
sehen keinen Unterschied und mussten nicht angefasst werden.

Das ist der Grund, warum dieser Block klein bleiben konnte. Ein zweiter
Begriff für dieselbe Sache — „Anlage aus dem Namen" gegen „Anlage aus der
Welt" — hätte jede Stelle verdoppelt, die heute mit Anlagen umgeht.

---

## Fernzugriff

Entschieden am 27.08. Der Entwurf steht in [fernzugriff.md](fernzugriff.md);
hier nur, was gegen etwas anderes entschieden wurde.

### Unterwegs kein Code

**Das Wireless Terminal kann Storage, Crafting, Network und Dashboards — den
Code nur der Laptop.** `konzept.md` §29 sagte bis eben, beide Geräte hätten
dieselben fünf Bereiche. Der Terminal-Block bleibt davon unberührt und behält
alle fünf.

Der Laptop kann alles, was das Wireless Terminal kann, und kostet mehr. Zwei
gleich starke Geräte für getrennte Aufgaben wären zwei Dinge zum Mitschleppen;
so ist das Terminal der frühe Zugang und der Laptop das Ziel.

### Modul und Karte

**Ein Modul gibt eine Fähigkeit, die vorher nicht da war. Eine Karte hebt
einen Wert an einer Fähigkeit, die schon da ist.** Zwei Wörter, zwei
Bedeutungen, und beide belegen denselben Steckplatz.

Daran hängt, warum die Infinity-Karte eine Karte ist: Sie schafft nichts
Neues, sie hebt eine Grenze auf.

**Gleiche Karten addieren sich, statt in Stufen aufzurüsten.** Ein
Stufensystem — Reichweite I, II, III — macht die alte Karte wertlos, sobald
die neue da ist. Vier gleiche Karten in vier Plätzen halten den Wert an der
Zahl der Plätze fest, und die ist die eigentliche Entscheidung.

### Der Sendemast sendet, nicht der Controller

**Ein eigener Block am Kabel**, und die Reichweite zählt auf beiden Seiten:
Mast plus Gerät. Sendete der Controller, gäbe es nur eine Stelle zum
Ausbauen, und jedes Netz hätte Funk, ob gewollt oder nicht.

**Die Infinity-Karte steckt im Mast** und gilt für alle Geräte des Netzes.
Steckte sie im Gerät, bräuchte sie auf einem Server jeder Spieler einzeln.

### Akku statt Netzstrom

**Die Geräte haben einen eigenen Akku**, angeboten als `IEnergyStorage` am
ItemStack. Damit laden Powah, Flux Networks und alles andere, was Gegenstände
im Inventar lädt, sie von selbst — dasselbe Prinzip wie bei den Connectoren.

Der Strom direkt aus dem bedienten Netz wäre einfacher gewesen und war der
erste Vorschlag. Dagegen sprach, dass es die Ladefunktionen anderer Mods
ungenutzt ließe, die es an dieser Stelle ohnehin gibt.

### Der Serverschrank bleibt außen vor

**Sein Steckplatzbehälter wird nicht zum Ausbausystem umgebaut.** Er hat feste
Rollen — CPU, RAM, Platte —, das neue System freie Plätze. Zwei
Steckplatzsysteme nebeneinander sind der Preis dafür, dass beide bleiben, was
sie sind.


---

## Der Anschluss ohne Kabel

Entschieden am 28.08., nach einem Blick in AE2 1.21.1 (geklont, nicht aus
einer Jar).

**Ein Anschluss kann gesetzt werden, bevor ein Kabel da ist.** Was dabei
entsteht, ist ein Kabelblock ohne Strang — ein Block, in dem nur der Anschluss
sitzt. Wer später ein Kabel darauf setzt, hat den Strang, und der Anschluss
hängt am Netz, ohne dass man ihn neu setzen müsste.

**Das Kabel kommt wirklich erst, wenn man es setzt.** Kein Automatismus, der
einen Strang mitliefert: Der Block ist ein Halter, und was in ihm steckt,
entscheidet der Spieler.

So macht es AE2, und der Grund ist derselbe: Dort ist der Block der
*Kabelbus*, das Kabel nur eines der Teile darin. `CableBusContainer.canAddPart`
lässt ein Teil an jede freie Seite, „if any" Kabel — und `cleanup()` am leeren
Bus ruft `removeBlock`, er räumt sich also selbst weg.

Bei uns ist der Kabelblock heute das Kabel. Die Umsetzung muss ihm beibringen,
ohne Strang zu bestehen und zu verschwinden, wenn nichts mehr an ihm hängt.

### Der Schraubenschlüssel

**Wir hören auf `c:tools/wrench`**, das Tag der NeoForge-Konvention, und
tragen unser eigenes Werkzeug dort ein. AE2 macht es genauso und nimmt
zusätzlich Immersive Engineerings Hammer als optionalen Eintrag mit. Wer einen
Konfigurator von Mekanism oder einen Schlüssel von Thermal dabeihat, kann
damit arbeiten, ohne dass wir diese Mods kennen.

**Die Geste ist Schleichen plus Rechtsklick**, wie bei AE2. Ohne Schleichen
tut der Schlüssel dort etwas anderes — er dreht —, und eine Geste, die je nach
Mod etwas anderes bedeutet, ist schlimmer als eine, die man einmal lernt.

### Was das Wireless Terminal darf

**Alles außer Code.** Der Entwurf in `fernzugriff.md` zählte vier Bereiche auf
und übersprang das Protokoll — es stammt aus `konzept.md` §29, das den Reiter
noch nicht kannte. Die Regel, die ein Spieler sich merken kann, ist aber nicht
„vier von sechs", sondern eine einzige Trennung: **Unterwegs kommt man ans
Lager, aber nicht an den Code.** Das Protokoll ist Diagnose wie die
Netzübersicht und gehört auf dieselbe Seite.

**Der Laptop darf alles.** Er ist der Grund, weiterzubauen, und hätte sonst
nur mehr Steckplätze zu bieten.

### Wer aus der Ferne Code anfassen darf

**Das offene Fenster ist die Erlaubnis, nicht die Koordinate im Paket.**

`DeployProgramPacket` verlangte bis zum 28.08. einen Terminal-Block an der
Position, die der Client mitschickte, und höchstens acht Blöcke Abstand;
`SaveDraftPacket` verlangte einen Controller und 64. Das trug, solange man vor
einem Block stehen musste. Mit dem Laptop steht man vor keinem mehr.

Beide fragen jetzt `player.containerMenu`, wie `StorageActionPacket`,
`CraftingActionPacket` und `RequestEditPacket` es schon vorher taten. Das ist
zugleich strenger: Eine Koordinate im Paket ist eine Behauptung des Clients;
das offene Fenster dagegen tickt der Server selbst, und wer aus der Reichweite
läuft, bei dem geht es zu.

**Am Block ändert sich nichts.** Dessen `stillValid` hält weiter den Abstand
von acht Blöcken. Der Fernzugriff nimmt etwas weg, er gibt nichts dazu.

Der Besitzschutz aus `FnProtection` beantwortet eine andere Frage und gilt
unverändert weiter.

### Die Grenzenlos-Karte hebt auch die Dimensionsgrenze auf

So stand es im Entwurf (`fernzugriff.md`, Abschnitt 1), und so ist es seit dem
28.08. auch gebaut. **Über eine Dimensionsgrenze reicht nur sie** — vier
Reichweitenkarten helfen dort nicht.

**Der Grund ist keine Zahl, sondern eine Art:** Zwischen zwei Dimensionen gibt
es keinen Abstand, den man messen könnte. Der Nether liegt nicht hundert
Blöcke von der Oberwelt entfernt, er liegt daneben und zugleich nirgends. Eine
Rechnung mit Koordinaten aus zwei Welten ergäbe eine Zahl ohne Bedeutung.
Reichweite ist eine Strecke; eine Dimensionsgrenze ist keine.

**Ein Gerät merkt sich seitdem die Welt und nicht nur den Ort.** Das ist auch
ohne die Karte nötig: Koordinaten wiederholen sich in jeder Dimension, und ein
Gerät, das nur `120, 64, -30` kannte, verband sich im Nether mit einem fremden
Mast, der zufällig dort stand. Vanilla hält es beim Lodestone-Kompass genauso —
`GlobalPos` statt `BlockPos`.

**Ein Mast in ungeladenem Land gilt als nicht erreichbar** und bekommt eine
eigene Meldung. Nicht dasselbe wie abgebaut: Wer „Der Sendemast steht nicht
mehr" liest, baut einen neuen — und der alte steht noch, nur schaut dort
gerade niemand hin. Ihn nachzuladen kam nicht in Frage: Die Frage wird für
jedes offene Fenster in jedem Tick gestellt, und ein Netz am anderen Ende der
Welt hielte damit dauerhaft Land offen, das niemand betritt.
