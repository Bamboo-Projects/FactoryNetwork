# Umsetzung — Stand und Anleitung

Was gebaut ist, wie man es ausprobiert, und was bei der Umsetzung auffiel.

Stand: 2026-08-20

---

## 1. Ausprobieren

```
./gradlew runClient          Spiel mit der Mod starten
./gradlew test               Übersetzer und Laufzeit prüfen (schnell)
./gradlew runGameTestServer  In einer echten Welt prüfen (etwa eine Minute)
./gradlew runServer          Dedizierten Server starten
./gradlew syncResources      Texturen und Texte ins laufende Spiel schieben
```

**Auf einem Server läuft sie.** Am 2026-08-24 zum ersten Mal geprüft:
`runServer` fährt hoch und lädt die Welt, ohne dass etwas fehlt. Die Trennung
hält — außerhalb von `client/` steht kein einziger Import aus
`net.minecraft.client`, und die Einstiegspunkte dort tragen
`@EventBusSubscriber(value = Dist.CLIENT)`.

Was im Mehrspieler **nicht** geht, ist der Weg über den Ordner neben der Welt:
Der liegt serverseitig, und dort hat kein Spieler Dateizugriff. In VS Code zu
arbeiten ist damit heute eine Sache des Einzelspielers. Siehe
`entscheidungen.md`, „Die Schnittstelle für externe Editoren wird zweimal
freigegeben".

### Ohne Neustart ändern

**Texturen, Modelle, Sprachdateien:** `./gradlew syncResources` in einem
zweiten Fenster, dann im Spiel **F3+T**. Minecraft lädt die Ressourcen neu,
das Spiel bleibt offen. Damit lässt sich an einer Textur so lange drehen, bis
sie sitzt.

**Java-Code, aber nur Methodenkörper:** Mit `./gradlew runClient -Pdebug`
lauscht der Client auf Port 5005 für einen Debugger. Ohne den Schalter nicht —
ein hängengebliebener Prozess würde sonst den Port belegen und den nächsten
Start verhindern. Wer sich mit IntelliJ verbindet (Run → Attach to Process, oder
eine Remote-JVM-Debug-Konfiguration auf localhost:5005), kann geänderte
Methoden mit *Build → Recompile* ins laufende Spiel schieben.

Was dabei **nicht** geht: neue Klassen, neue Methoden, neue Felder, geänderte
Signaturen. Das ist eine Grenze der Java-Laufzeitumgebung, keine von
Minecraft. Wer eine Zahl ändert oder eine Bedingung dreht, spart sich den
Neustart; wer eine Oberfläche umbaut, nicht.

**Rezepte, Loot-Tabellen und Tags:** `/reload` im Spiel.

Beim ersten Mal lädt und dekompiliert Gradle Minecraft — das dauert rund sechs
Minuten und passiert nur einmal.

### Im Spiel

Alle Blöcke liegen im Kreativ-Reiter „Factory Network".

1. **Controller** setzen. Er ist die Wurzel des Netzwerks und hält das
   Programm. **Er allein reicht nicht:** Lagern kann das Netz erst mit einem
   Laufwerk, rechnen erst mit einem Serverschrank.
2. **Kabel** vom Controller weg legen.
3. **Serverschrank** ans Kabel setzen — er ist zwei Blöcke hoch —,
   anklicken und einen Einschub mit **Rechenwerk, Speicher und Datenträger**
   bestücken. Erst alle drei zusammen sind ein Server: Ohne einen rechnet das
   Netz nicht, ein Programm wird gar nicht erst übernommen, und Worker stehen
   still.
4. **Laufwerk** ans Kabel setzen und eine **Speicherzelle** hineinlegen.
   Ohne sie lagert das Netz nichts. Für Flüssigkeiten kommt eine
   **Flüssigkeitszelle** in dasselbe Laufwerk.

   Beide öffnen sich per Klick wie eine Kiste. Im Fenster liegen die Plätze
   so, wie sie an der Front sitzen — und an der Front sieht man ohne
   Anklicken, was steckt.
5. **Connector** an ein Kabel setzen, mit der Vorderseite an eine Kiste oder
   Maschine. Die Vorderseite zeigt dorthin, wo man den Block angeklickt hat.
6. **Label-Gun** nehmen und einmal auf den **Controller** rechtsklicken —
   damit ist sie mit dem Netz verbunden. Dann auf einen Connector klicken: Er
   bekommt einen Namen aus der Maschine dahinter, durchnummeriert
   (`furnace_1`, `furnace_2`, …). Schleichen + Klick übernimmt einen
   vorhandenen Namen, nochmal derselbe Name nimmt ihn wieder weg.

   Solange die Gun in der Hand ist, schweben die Namen über allen Connectoren
   in der Nähe: grün benannt, grau unbenannt, rot doppelt vergeben.
7. **Terminal** neben den Controller setzen und anklicken.

Im Editor:

```
worker quarry_import {
    from quarry_output
    to storage
}
```

**Strg+Eingabe** übernimmt. Links stehen die Connectoren, die das Netz kennt;
**Tab** übernimmt einen Vorschlag, die Pfeile wählen aus.

Der Editor kennt außerdem:

| Taste | Was sie tut |
|---|---|
| Strg+A / C / X / V | alles auswählen, kopieren, ausschneiden, einfügen |
| Umschalt+Pfeile, Umschalt+Klick, Ziehen | auswählen |
| Doppelklick | das Wort darunter |
| Strg+Z, Strg+Y | rückgängig — mit Umschalt wieder vorwärts |
| Strg+D | Zeile verdoppeln |
| Tab, Umschalt+Tab | Auswahl ein- und ausrücken |
| Strg+Pfeil links/rechts | ein Wort weiter |
| Strg+Rücktaste | ein Wort löschen |
| Strg+Pos1 / Strg+Ende | an den Anfang oder das Ende des Programms |
| Strg+F | suchen — Eingabe weiter, Umschalt+Eingabe zurück, Escape schließt |

**Strg+Z und Strg+Y machen beide rückgängig.** GLFW meldet eine Taste nach
ihrer Lage auf einer US-Tastatur; auf einer deutschen liegt dort, wo „Z"
steht, die Meldung „Y". Beide auf dasselbe zu legen tut auf jeder Belegung
das Erwartete, statt auf einer der beiden das Gegenteil.

Ein Rechtsklick auf den Controller nennt die Zahl der Connectoren und Kabel —
nützlich, wenn ein Name im Editor nicht auftaucht.

---

## 2. Was läuft

| | |
|---|---|
| Sprache | Lexer, Parser, Fehlerbehebung — die Grammatik vollständig |
| | `global`: ein Wert, den alle Dateien sehen, mit Anzeige im Terminal |
| Worker | `from`, `to`, `filter`, `maintain`, `rate`, `when`, `strategy`, `overflow` |
| Auswahl | einzelne Gegenstände, Tags, Muster, `except` |
| | `filter name { … }`: eine Auswahl mit Namen, überall verwendbar |
| Flüssigkeiten | `move` und Worker, Bestand in Zellen, in Millibucket |
| Speicher | Laufwerke mit zehn Plätzen, Zellen in vier Größen, Bestand in der Zelle |
| | Regalfenster für Laufwerk und Schrank, Bestückung an der Front ablesbar |
| | Router: Seite anklicken schaltet weiter, Schleichen öffnet alle sechs auf einmal |
| Rechenleistung | Serverschränke, zwei Blöcke hoch, mit zwölf Einschüben |
| | Je Einschub Rechenwerk, Speicher und Datenträger in vier Stufen |
| | Erst alle drei ergeben einen Server; unfertige Einschübe tragen nichts |
| Strom | FE in den Controller, Verbrauch je Gerät, aus bei Unterversorgung |
| | Brennkammer als eigene Quelle, Kreativ-Stromquelle zum Prüfen |
| | `filter power`: aus dem Netz in eine Maschine und aus einer Maschine ins Netz |
| | Bei Knappheit bekommt der Worker mit der kleinen `priority` seine ganze Rate |
| | Energiezellen im Laufwerk, vier Größen — der Vorrat wächst mit ihnen |
| Chemikalien | Mit Mekanism: `chemical:` bewegt, zählt und lagert in Zellen |
| | Ein Worker mit `filter chemical:…` holt und bringt sie |
| Ausbau | Controller-Anbau: sechs weitere Seiten für Kabelstränge je Block |
| Fertigung | Fabricator baut Werkbank-Rezepte aus dem Netzspeicher, mehrstufig |
| | Fehlt eine Zutat, wird sie gebaut; genannt wird der Grundstoff |
| | Ofen, Schmelzofen, Räucherofen und Presse arbeiten für einen Auftrag mit |
| | `recipe … at …` erklärt, was eine fremde Maschine kann |
| | `from crafting` hält einen Vorrat, gerechnet gegen offene Aufträge |
| | `craft(64 item:chest)` im Code, Aufträge im Reiter, mit Abbruch |
| Server | `config/factorynetwork-server.toml`: Schrittbudget und Suchtiefe |
| | Schutz fremder Programme, wahlweise nach Besitzer oder Operator |
| | Ein laufender Ablauf belegt einen Platz, der Rest stellt sich an |
| | Zelle in der Hand ans Laufwerk klicken setzt sie ein, leere Hand nimmt die letzte heraus |
| Werkzeuge | Beschriftungspistole, Netzanalysator mit Sicht durch Wände |
| Funktionen | Bedingungen, Schleifen, `move`, Redstone lesen, `log` |
| Ereignisse | `redstone_changed`, `device_online/offline/changed/output`, eigene über `emit` und `on` |
| Abläufe | `sleep`, `await` mit `where`, `timeout` und `else`, `for` mit Warten je Runde |
| Listen | `[a, b]`, `plus`, `without`, `rest` — auch als globaler Wert |
| Aufrufe | Eine gerufene Funktion darf selbst warten — beide Rahmen überstehen den Neustart |
| Multiblocks | Vorlagen, gebaute Anlagen über `anlage/rolle`, Aufruf an der Instanz |
| Fortsetzen | Wartende Abläufe überleben Serverneustart und Programmwechsel |
| Netzwerk | Graph über Kabel, Speicher schlüsselbasiert, Kanäle je Strang |
| Editor | Syntaxfarben, Fehler beim Tippen, Vervollständigung nach Stelle |
| | Projekt aus mehreren Dateien, Ordner im Namen: `erz/brecher.mf` |
| | VS Code zeigt die Fehler des Spiels und kennt die Gerätenamen |
| | Anzeigenwand mit `scale`: große Schrift statt vieler Zeilen |
| Anzeigen | Am Block und im Terminal, Knöpfe starten Abläufe |
| Prüfung | 429 Einheitstests, 266 GameTests |

## 3. Was noch nicht läuft

- **Die Zahlen an den Serverbauteilen.** Rechenwerke von zwei bis
  hundertachtundzwanzig, Speicher von acht bis fünfhundertzwölf, Datenträger
  von vierundsechzig bis viertausendsechsundneunzig. Sie sind gesetzt, nicht
  hergeleitet — wie sie sich anfühlen, zeigt erst das Spielen. Die
  Begründungen stehen in `entscheidungen.md` unter „Der Serverschrank".
- **Processing-Rezepte** (2.9). Was ein Brecher aus Erz macht, weiß nur die
  Maschine. Für den Fabricator brauchte es das nicht: Werkbank-Rezepte stehen
  im Server.
- **Chemikalien** aus Mekanism. Die Schreibweise steht seit dem Entwurf.

---

## 4. Was bei der Umsetzung auffiel

Diese Punkte haben Zeit gekostet oder Entscheidungen verändert. Vor der
nächsten Stufe lesen.

### Die Spezifikation hatte drei Lücken, die erst der Code zeigte

**`item:iron_ingot` und `fn craft(item: Item)` beginnen identisch.** Vier
Buchstaben, ein Doppelpunkt. Weder Prosa noch Grammatik hatten das bemerkt.
Der Lexer entscheidet jetzt am Leerraum: Folgt direkt hinter dem Doppelpunkt
ein Pfadzeichen, ist es eine Auswahl, sonst eine Typangabe.

**`timeout` ohne `else` hinterlässt keinen sinnvollen Wert.** Fiel beim
Aufschreiben der Grammatik auf. Der `else`-Zweig ist jetzt Pflicht und muss
den Ablauf verlassen.

**Eine vorangestellte Menge bedeutet bei `move` etwas anderes als bei
`maintain`** — Stapel gegen Vorrat. Ebenfalls beim Aufschreiben der Grammatik
gefunden, jetzt festgelegt und begründet.

### Tests haben drei Fehler gefunden, die kein Übersetzer meldet

**Die Levenshtein-Distanz griff auf die falsche Zelle zu** (oben statt
diagonal) und überschätzte damit jeden Abstand. Folge: Bei `quary_output` kam
kein Vorschlag, obwohl `quarry_output` einen Buchstaben entfernt ist. Gefunden
von einem GameTest, festgehalten von einem Einheitstest — die Rechnung liegt
deshalb jetzt in `util/NameDistance`, ohne Minecraft-Bezug.

**Die hilfreiche Meldung zum einzelnen `=` schlug bei jeder Zuweisung zu.**
`summe = summe + 1` galt als misslungener Vergleich. Sie greift jetzt nur noch
in Bedingungen. Gefunden vom ersten Interpreter-Test — der Parser-Test hatte
nur den Fehlerfall geprüft, nie den Normalfall.

**Die vorangestellte Menge ging beim Auswerten verloren.** `move 64
item:iron_ore` hätte alles bewegt. Kein Übersetzungsfehler, kein Absturz —
nur ein falsches Ergebnis. Gefunden, weil ein GameTest die Zahl im Ziel
nachgezählt hat.

### Die Laufzeit tat zweimal das Gegenteil der Spezifikation

Beides fiel erst beim Gegenlesen von Code und Spezifikation auf, nicht beim
Übersetzen und nicht in den Tests — weil die Tests jeweils nur einen Fall
prüften, in dem sich die beiden Lesarten nicht unterscheiden.

**`maintain` zählte insgesamt statt je Gegenstandsart.** Die Spezifikation
sagt: `filter tag:c/coals` mit `maintain 64` hält 64 von jeder Kohleart. Die
Laufzeit summierte über alle Arten. Der Test hatte nur eine Art benutzt und
konnte den Unterschied deshalb nicht sehen. Der neue Test nimmt Kohle und
Holzkohle und verlangt acht von jeder.

**`move` mit Tag oder Muster bewegte alles.** `WorldHost` verstand nur
einzelne Gegenstände; alles andere ergab „kein Filter", und kein Filter heißt
alles. `move 4 tag:minecraft/logs` hätte das Erz mitgenommen. Beides geht
jetzt durch dieselbe Auflösung wie beim Worker.

Die Lehre: Ein Test, der nur einen Fall abdeckt, in dem sich zwei Lesarten
gleich verhalten, prüft die Lesart nicht. Bei jeder Regel, die „je" oder
„insgesamt" sagt, gehören zwei Arten in den Test.

### Fallen in der Umgebung

**`getDeclaredMethods()` löst alle Typen der Signaturen auf.** Ein Test, der
per Spiegelung auf eine Klasse zugreift, die Minecraft-Typen in ihren
Signaturen führt, scheitert mit `NoClassDefFoundError` — auch wenn die
geprüfte Methode selbst nichts davon braucht. Deshalb liegt alles, was ohne
Server prüfbar sein soll, in Klassen ohne Minecraft-Bezug.

**`@GameTestHolder` setzt den Namensraum schon.** Ein `template =
"factorynetwork:empty"` wird daraus `factorynetwork:factorynetwork:empty`.
Richtig ist `template = "empty"`.

**Strukturvorlagen lassen sich von Hand erzeugen.** Eine leere `.nbt` ist
gzip-komprimiertes NBT mit `size`, `palette`, `blocks`, `entities` und
`DataVersion` — für 1.21.1 ist das 3955. Das Skript dafür liegt unter
`tools/structure.py`; Minecraft dafür zu starten ist unnötig.

**Sehr lange Heredocs werden abgeschnitten.** Beim Schreiben großer Dateien
in Stücken arbeiten und danach die Zeilenzahl prüfen.

**`/tmp` überlebt den einzelnen Aufruf nicht.** Zwischendateien gehören in ein
Verzeichnis, das bleibt — sonst ist die Datei beim nächsten Schritt weg, und
das sieht aus wie ein Fehler im Skript.

**Übersetzerfehler kommen hier auf Deutsch.** Wer nur nach `error:` sucht, hält
eine fehlgeschlagene Übersetzung für gelungen. Immer auch `Fehler:` prüfen.

### Beim Bau der Abläufe fielen vier Dinge auf

**Der Tick kehrte früh zurück, wenn kein Worker da war.** Ein Programm darf
allein aus Funktionen bestehen, die auf Ereignisse warten — dann lief es nie.
Solche frühen Rückkehrer sind billig geschrieben und teuer zu finden: Alles
sieht richtig aus, es passiert nur nichts.

**Alle ersten Belege gingen am Spieler vorbei.** Die Tests weckten Abläufe über
den Java-Aufruf. Der Weg, den ein Spieler nimmt, ist `emit` in seinem Programm
— und der weckte niemanden, weil der Interpreter die `on`-Blöcke selbst und zu
Ende ausführte. Ein Test, der die eigene Schnittstelle prüft statt der des
Nutzers, kann grün sein, während das Versprechen nicht gilt.

**`for` braucht einen anderen Rahmen als `while`.** Bei `while` steht die
Bedingung im Programm und wird jede Runde neu geprüft; der Rahmen darf also weg
und neu entstehen. Bei `for` gäbe es dann nichts mehr, woran sich der Stand
ablesen ließe — er muss in den Rahmen und damit auf die Platte. Wer das
übersieht, bekommt eine Schleife, die nach einem Neustart von vorn beginnt und
alles ein zweites Mal tut.

**Zwei Fehlertexte hatten sich überlebt.** Sie sagten noch, wartender Code
brauche Continuations und die seien nicht gebaut. Ein Einheitstest prüfte
genau diesen Wortlaut und hielt die Unwahrheit fest — deshalb prüfen Tests auf
Fehlermeldungen jetzt auf das, was der Text leisten soll, nicht auf ein
bestimmtes Wort.

---

## 5. Wo was liegt

```
lang/           Manifold: Token, Lexer, Meldungen
lang/ast/       der Syntaxbaum, versiegelt
lang/parse/     der Parser von Hand
runtime/        Interpreter, Worker, Auflösung von Auswahlen
runtime/WorldHost   alles, was der Interpreter über Minecraft weiß
network/        Graph und Speicher
block/          die vier Blöcke und ihre BlockEntities
client/screen/  Editor und Vervollständigung
test/           GameTests
```

Die Trennung, auf die es ankommt: **`runtime/Interpreter` kennt Minecraft
nicht.** Er spricht mit der Welt nur über `Interpreter.Host`. Deshalb lässt
sich die Sprache in Millisekunden prüfen statt in einer Minute GameTest, und
deshalb liegt in den Tests dort eine Welt aus Papier.

---

## 6. Was als Nächstes ansteht

Diese Liste stand hier vom ersten Tag an und ist abgearbeitet: Flüssigkeiten
laufen, der Speicher liegt in Zellen in Laufwerken, und das Autocrafting ist
mehrstufig. Was offen ist, steht in `offene-punkte.md` — dort mit Zustand und
Begründung, statt hier als Reihenfolge, die niemand nachzieht.

Der Punkt „ein eigener Speicherblock" hat sich dabei nicht erfüllt, sondern
aufgelöst: Er stand da mit der Begründung, solange der Speicher im Controller
sitze, gebe es keinen Grund für einen zweiten. Der Speicher sitzt seit den
Zellen nicht mehr im Controller. **Das Laufwerk ist der Speicherblock**, wer
mehr Platz will, stellt eines dazu — nachgeprüft in
`asecondDriveEnlargesTheStorage`, denn genau diese Zusage war bis dahin
nirgends belegt.

### Globale Werte (seit dem 24.08.)

`global modus = "tag"` erklärt einen Wert, den alle Dateien sehen. Er wird aus
Funktionen und Ereignisblöcken geschrieben, überlebt Serverneustart und
Programmwechsel, und das Terminal zeigt ihn im Reiter **Netzwerk**.

**Reaktivität kostete fast nichts.** Anzeigen und `when` werten ihre Ausdrücke
ohnehin je Tick aus — ein Worker mit `when modus == "tag"` schläft ein, sobald
der Wert kippt, ohne dass es dafür einen Beobachter bräuchte. Gebaut werden
musste nur der Wert selbst: wo er liegt, wer ihn ändern darf, was beim
Programmwechsel mit ihm geschieht.

Was dabei auffiel und im Entwurf korrigiert wurde: **Die Sprache hat keinen
Typprüfer.** Der Entwurf versprach, `modus = 3` werde beim Übersetzen
abgelehnt — das kann sie nicht. Gemeldet wird jetzt, was ohne Typsystem
entscheidbar ist: Literal gegen Literal. Der Rest fällt zur Laufzeit auf, wie
überall sonst.

### In der Nacht auf den 25.08.

**Ein Gerät kann mehr.** `insert(auswahl)` legt aus dem Netzspeicher etwas
hinein und meldet, wie viel ankam; `items()` sagt, was drinliegt. Beide gehen
denselben Weg wie `move` — dieselbe Auswahl, dieselbe Mengenrechnung, dieselbe
Unterscheidung zwischen Gegenständen und Flüssigkeiten.

**Listen können etwas.** `count()`, `first()` und `sum()`, dazu
`storage.items()` als Quelle. `where` und `sort` fehlen und melden sich als
fehlend — sie brauchen einen Eingriff in den Aufrufpfad, siehe `sprache.md`
§12.

**Die Annahme-Probe.** Beim Zeigen auf ein Gerät steht jetzt, welches Fach
aufnimmt, welches abgibt, und welcher der Gegenstände aus dem Entwurf
hineinpasst. Die Kandidaten kommen über eine Textsuche aus dem Programm
(`ItemCandidates`) — wer `item:iron_ore` tippt, fragt sich über Eisenerz
etwas.

**Strom in der Sprache.** `filter power` ist eine vierte Ressourcenart, ohne
Doppelpunkt, weil Strom keine Sorten hat. Die Seitenwarnung gilt mit: Ein
Strom-Worker verlangt einen Energiespeicher an der angeschlossenen Seite.
Verteilt wurde damit noch nichts; das kam am Abend desselben Tages dazu.

**Bearbeitung anfragen.** F4 klopft beim Halter einer gesperrten Datei an, in
beiden Fenstern. Kein Übernehmen — die Sperre wegzunehmen wäre genau das,
wogegen sie gebaut wurde.

**Das Handbuch im Spiel.** GuideME ist eingebunden (`compat/guide`), die
ersten Seiten liegen unter `assets/factorynetwork/guide/`. Markdown als
Quelle, gerendert wird im Spiel.

**Und beide Editoren kennen jetzt das ganze Projekt.** Die Vervollständigung
im Spiel las bisher nur die offene Datei — dabei teilen alle Dateien einen
Namensraum. Die VS-Code-Erweiterung liest die Nachbardateien ebenfalls.

### Die Fertigung, erster Schnitt (seit dem 25.08.)

Der Reiter *Fertigung* stand seit dem ersten Tag ausgegraut in der Leiste.
Jetzt stehen Aufträge darin.

**Keine Muster-Items.** Was gebaut werden kann, weiß das Spiel bereits — jedes
Werkbank-Rezept steht im Server. Ein Netz, das sich seine Rezepte erst auf
Papierschnipsel schreiben lässt, verlangt Arbeit für eine Auskunft, die schon
dasteht.

**Zuerst einstufig.** Fehlten Bretter, machte der Fabricator keine aus
Stämmen, sondern wartete und sagte „es fehlt: 8 Eichenholzbretter" — auch
dann, wenn zwei Stämme im Laufwerk lagen. Das war der Schnitt des ersten
Tages; der Planner hat ihn noch am selben Tag aufgehoben (siehe unten).

**Der Bestand entscheidet über das Rezept.** Für einen Gegenstand gibt es oft
mehrere, und eines davon passt zu dem, was dasteht. Wer nur das erstbeste
nähme, meldete „es fehlt Fichtenholz", während ein Stapel Eichenbretter im
Laufwerk liegt.

**Bestellt wird im Code**, nicht im Reiter: `craft(64 item:chest)`. Dieselbe
Haltung wie überall — ein Netz tut nichts von selbst. Was der Reiter
beiträgt, ist die Antwort darauf: was daraus wurde und woran es hängt.

**Der Auftrag lebt am Controller.** Einer, der am Fabricator hinge, wäre weg,
sobald jemand das Gerät abbaut — und das ist genau der Moment, in dem man
wissen will, was noch offen war.

`crafting_failed` löst nur bei einem verschwundenen Rezept aus. Fehlende
Zutaten sind kein Fehlschlag: Wer darauf wartet, wartet, und morgen liegen sie
vielleicht da.

### Die Fertigung wird mehrstufig (seit dem 25.08.)

Der Planner zerlegt eine Bestellung, bis er bei etwas ankommt, das dasteht.
Ein Auftrag über eine Truhe bei zwei Stämmen im Laufwerk läuft in zwei
Schritten: erst acht Bretter, dann die Truhe.

**Der Plan wird gerechnet, nicht gemerkt.** Bei jedem Fertigungstakt neu, und
ausgeführt wird davon der unterste Schritt. Ein gespeicherter Plan wäre ab dem
Moment falsch, in dem ein Worker etwas einlagert — und genau das tun Worker
den ganzen Tag. Nebenbei spart es ein Speicherformat: Der Auftrag übersteht
den Neustart wie bisher, der Plan entsteht danach neu.

**Eine Zutat ist eine Auswahl, und sie bleibt eine.** Das war der Fehler, den
erst die Rekursion sichtbar machte: Die Zutatenliste legte sich beim Planen
auf eine Sorte fest. Für einen einzigen Schritt fiel das nie auf — der Bestand
entschied ja mit. Für eine Kette schon: Wer nur Fichtenstämme hat, hat von
keiner Brettersorte etwas, und dann nahm die Liste die erste. „Es fehlen 8
Eichenbretter", und im Laufwerk lag das Holz. Jetzt wird die Auswahl
durchgetragen — erst aus dem Bestand gedeckt, notfalls gemischt, und was
offenbleibt, wird gebaut: eine Sorte nach der anderen, bis eine aufgeht.

**Es baut nichts halb.** Geht der Plan nicht auf, wartet der Auftrag
vollständig. Sonst stünde am Ende ein Stapel Zwischenzeug im Lager, das
niemand bestellt hat — und der Auftrag hinge trotzdem.

**Die Fehlzeile hat sich geändert.** Aus „es fehlt: 8 Eichenholzbretter" wurde
„es fehlt: 2 Eichenstamm". Genannt wird, was ein Mensch hinlegen muss.

**Ein Verzeichnis statt eines Durchsuchens.** Die Rezeptliste eines Packs hat
fünfstellige Länge, und der Planner fragt sie für jeden Knoten eines
Rezeptbaums. Sie wird deshalb einmal je Tick nach Ergebnis geordnet. Länger
aufzubewahren hieße, sich darauf zu verlassen, dass ein `/reload` den
Rezeptverwalter austauscht — und das ist keine Zusage, sondern Innenleben.

Zwei neue Grenzen stehen in der Serverkonfiguration: `craftingDepth` (acht
Ebenen) und `craftingBudget` (512 Bedarfe). Die zweite greift bei Rezept­
bäumen, die sich in viele erlaubte Sorten verzweigen — dort wächst die Suche
schneller als ihre Tiefe.

### Globale Listen (seit dem 26.08.)

Punkt 1.11 stand als „entschieden: kommen" und war beim Bauen wieder zur
Entscheidung geworden: Es fehlte eine Schreibweise für eine Liste und ein Weg,
ihr etwas hinzuzufügen.

**`[a, b]`, und `[]` gehört dazu.** Eckige Klammern zählen im Lexer wie runde:
Zwischen ihnen trennt kein Zeilenumbruch. Das kostete eine Zeile, weil der
Mechanismus für runde Klammern schon dastand.

**Ersetzen statt Ändern.** Es gibt kein `add`; angehängt wird über eine
Zuweisung. Die beiden Gründe standen schon im Code: `const` bewacht
Zuweisungen (und der Mehrspielerschutz auch), und der `ValueCodec` trennt beim
Neustart zwei Namen für dieselbe Liste in zwei Listen. Unveränderliche Werte
haben beide Probleme nicht — und alle fünf vorhandenen Operationen hielten es
ohnehin so.

**Drei Operationen, kein Index.** `plus`, `without`, `rest`. `liste[2]` gibt
es nicht: Eine Liste, in die man an beliebiger Stelle greift, will auch an
beliebiger Stelle geändert werden.

**Eine Obergrenze beim Betreiber.** `globalListSize` neben `stepBudget`, denn
ein globaler Listenwert ist der einzige Wert, der in einer Schleife wachsen
kann und den Neustart übersteht. Geprüft an einer einzigen Stelle —
`writeGlobal` —, durch die beide Ausführungswege gehen. Auch das ein Geschenk
der Entscheidung oben.

**Zwei Stellen fielen dabei auf.** Der Netzprüfer läuft über Ausdrücke, um
Gerätenamen zu finden, und ein Listenliteral ist der einzige Ausdruck, der
Ausdrücke enthält — ohne einen Zweig dorthin liefe ein Vertipper in einer
Liste von Zielen durch. Und `describe()` einer Liste lieferte „3 Einträge",
was im Protokoll und im Netz-Reiter niemandem half; jetzt steht dort
`[eisen, gold]`, ab sieben Einträgen gekürzt mit einer Zählung.

### Die Schrift auf der Anzeigenwand (seit dem 26.08.)

Der Renderer hatte die Frage schon beantwortet und die Antwort war richtig:
Die Schrift wächst **nicht** von selbst mit der Wand. Der Platz einer großen
Wand geht in mehr Zeilen und längere; eine Wand, deren Text mitwächst, ist aus
drei Metern genauso lesbar wie eine einzelne Tafel.

Was fehlte, war der Griff für den Fall, den man wirklich will: eine
Überschrift, die man aus zwanzig Metern liest. Den gibt jetzt `scale 4` im
Display-Block. Damit entscheidet der, der die Wand gebaut hat — und wer nichts
schreibt, bekommt genau das, was die Tafel vorher tat.

**Eine feste Zahl, kein Ausdruck.** Die Größe ist Aufbau und nicht Inhalt; ein
Maßstab, der sich beim Zusehen ändert, bräche die Wand jedes Mal neu um.

**Die Sichtweite wächst mit.** Sechzehn Blöcke waren die Entfernung, ab der
nicht mehr gezeichnet wird — bei Normalgröße richtig, bei vierfacher nicht.
Ohne das wäre `scale` ein Griff, der die Schrift vergrößert und sie genau
dort verschwinden lässt, wo man sie lesen wollte.

**Eine Zahl für die ganze Tafel**, nicht je Zeile. Eine große Überschrift über
kleinen Zeilen bräuchte einen Maßstab je Zeile, und damit eine Zeilenhöhe je
Zeile im Speicherformat, im Paket und im Umbruch. Der Fall lässt sich mit zwei
Wänden und einer Lücke dazwischen bauen; die Kosten der allgemeinen Lösung
stünden dazu in keinem Verhältnis.

### Ordner im Projekt (seit dem 25.08.)

`erz/brecher.mf` geht. Der Umbau war kleiner als die offene Frage aussah — im
Kern ein Namensmuster mit Abschnitten —, hatte aber zwei Stellen, an denen es
still schiefgeht.

**Der Punkt steht nicht im Alphabet eines Abschnitts.** Damit ist `../` nicht
verboten, sondern unmöglich, und dasselbe gilt für den Rückstrich von Windows
und den Doppelpunkt eines Laufwerks. Eine Verbotsliste hätte man umgehen
können; ein Alphabet nicht. Dazu eine Obergrenze für den ganzen Pfad: Vorher
lag sie bei fünfunddreißig Zeichen, weil ein Name aus genau einem Abschnitt
bestand.

**Der Rückstrich auf Windows.** `Path.relativize` liefert dort
`erz\brecher.mf`, und das entspricht keinem `erz/brecher.mf` im Projekt. Die
Brücke neben der Welt sähe im Sekundentakt eine fremde Datei und zugleich eine
fehlende und schriebe zwei Wahrheiten gegeneinander. Der Name wird deshalb
beim Lesen auf Schrägstriche gebracht. `ProgramFolder` hat dafür jetzt eine
Prüfung — es gab bisher keine, weil die Klasse einen Server brauchte; für den
Weg hin und zurück braucht sie nur einen Ordner, und den kann ein Test
hinstellen.

**Die Erweiterung sucht die Wurzel des Projekts.** Sie las bisher den Ordner
der offenen Datei; in `erz/brecher.mf` wären das die Geschwister im Ordner
`erz` und nicht `main.mf` eine Ebene höher — und der Namensraum ist einer. Sie
geht jetzt nach oben, solange der Ordner darüber selbst Programmdateien
enthält, und hält in jedem Fall an einem Ordner namens `controller_` an. Ohne
diese zweite Bedingung liefe sie über `factorynetwork` hinaus und mischte die
Namen fremder Controller unter.

**Die Liste im Spiel bleibt flach.** Der Schrägstrich sortiert vor Buchstaben
und Ziffern, also stehen die Dateien eines Ordners von selbst beieinander. Ein
Klappbaum bräuchte einen Griff mehr für dieselbe Auskunft. Was nicht in die
Spalte passt, wird vorn gekürzt — `…/schmelzen.mf` —, denn von rechts gekürzt
sähen zwei Dateien desselben Ordners gleich aus.

### Fehler und Gerätenamen in VS Code (seit dem 26.08.)

Der Einzelspieler-Schnitt von Punkt 4.1. Der Controller schreibt
`.fn-status.json` neben die Programmdateien: Fehler mit Datei, Zeile und
Spalte, dazu die Namen der Connectoren und Anzeigen. Die Erweiterung liest sie
und trägt beides ein.

**Kein neuer Zugang.** Wer die Programmdateien sieht, sieht auch diese — die
Schnittstelle mit zwei Erlaubnisstufen vom 24.08. galt einem Port, und es gibt
keinen. Sie bleibt offen für den Serverfall, wo niemand an die Dateien kommt.

**Kein zweiter Übersetzer.** Die Prüfung in JavaScript nachzubauen wäre
dieselbe Falle wie bei der Formtabelle — zwei Fassungen derselben Regeln
laufen auseinander. Nur rechnet dort ein Test die Tabelle nach; für einen
Übersetzer gäbe es nichts Vergleichbares. Also rechnet der, der es ohnehin
tut, und die Erweiterung übersetzt nur Zahlen: ab eins gegen ab null.

**Gerade der gescheiterte Fall zählt.** Vorher stand bei einem Fehler im
Ordner nur eine Zeile im Spiel-Log; wer in VS Code arbeitete, sah gar nichts.

**Ein Merker statt eines Takts.** Geschrieben wird beim Übernehmen, beim
gescheiterten Übernehmen und beim Neuaufbau des Netzes — sonst baute der
Controller die Datei je Sekunde neu, um sie mit sich selbst zu vergleichen.

### Chemikalien aus Mekanism (seit dem 26.08.)

Punkt 1.4 und 7.1, als Kompatibilitätsmodul: `chemical:mekanism/hydrogen`
löst sich auf, bewegt sich mit `move`, wird gezählt und liegt in
Chemikalienzellen im Laufwerk. Ohne Mekanism gibt es nichts davon, und die
Meldung zeigt auf die Modliste.

**Der Kern spricht in Texten.** Das ist keine Vorliebe: Java löst die Klassen
einer Signatur beim Laden auf, und eine Klasse mit `Registry<Chemical>` im
Rückgabetyp ließe sich in einem Pack ohne Mekanism nicht mehr laden — mit ihr
fiele der Controller. Mekanism-Typen stehen in drei Klassen unter
`compat/mekanism`, jede wird erst betreten, wenn die Modliste die Mod meldet.

**Die Rechnung stand schon da.** `CellInventory` und `CellFormat` sind seit
den Flüssigkeiten offen für den Typ; der Mekanism-Teil der Zellen besteht aus
einem Format und drei Umrechnungen. Dass `MekanismAPI.CHEMICAL_REGISTRY` eine
gewöhnliche `Registry` ist, wurde vor dem ersten Bau im API-Jar nachgesehen —
es war die eine Annahme, die alles getragen hätte oder nicht.

**Die Capability wird selbst gebaut.** Mekanism hält sie in `common`, nicht im
API-Jar; NeoForge gibt für denselben Namen dieselbe Instanz zurück, und der
Name steht in Mekanisms Bytecode.

**Ein Rückfall wurde zurückgenommen.** Als ein frisch gesetzter Tank im
Prüflauf nichts annahm, stand kurz ein Rückfall auf den ungeteilten Zugriff
da. Die Messung widerlegte ihn: Der ungeteilte Handler lässt sich lesen, nimmt
aber ebenfalls nichts an. Geblieben ist der seitenbezogene Zugriff — die
Seitenkonfiguration gehört dem Spieler.

**Eine benannte Lücke:** Ein Mekanism-Tank, den ein GameTest per `setBlock`
hinstellt, gibt an keiner der sechs Seiten eine Capability heraus. Geprüft
wird deshalb die Rechnung gegen einen Behälter aus Mekanisms API; der Weg von
einem echten Block zum Netz läuft erst, wenn jemand ihn im Spiel hinstellt.

### Rezepte im Programm (seit dem 26.08.)

Der zweite Schnitt von Weg B, und damit ist 2.9 gebaut:

```
recipe erz_mahlen at brecher {
    in 1 item:iron_ore
    out 2 item:iron_dust
}
```

**Eine Deklaration und kein Muster-Item.** Der Fabricator baut ohne Muster,
und der Grund trägt hierher weiter: Ein Musterterminal wäre der zweite Ort, an
dem eine Fabrik erklärt wird — der erste ist das Programm. Eine Zeile im
Programm geht mit der Datei nach VS Code, lässt sich versionieren, und ein
Rezept an einem Gerät, das es nicht gibt, meldet sich beim Übernehmen. Ein
Muster mit vertipptem Ziel merkt niemand, bis die Fabrik stillsteht.

**Keine Fachnummern.** Bei der Ofenfamilie kennt diese Mod die Fächer und
greift direkt zu; bei einer fremden Maschine weiß nur sie selbst, wo was
hingehört, und dafür gibt es den seitenbezogenen Zugriff, auf den sich auch
`move` verlässt. Zwei Zugriffswege für zwei verschiedene Lagen, und beide sind
begründbar.

**Erst proben, dann entnehmen.** Ein erklärtes Rezept darf mehrere Zutaten
haben, und eine Maschine, die die zweite ablehnt, hätte sonst die erste
geschluckt und den Auftrag mit einem halben Rezept stehenlassen.

**Die erklärten Rezepte hängen am Programm, nicht am Tick.** Sie ändern sich
nur beim Übernehmen — im Gegensatz zu den Rezepten des Servers, die ein
`/reload` austauschen kann.

Offen als dokumentierter Schnitt: Strom als Zutat.

### Flüssigkeiten und Chemikalien im Rezept (seit dem 26.08.)

```
recipe erz_waschen at washer {
    in 1 item:iron_ore
    in 1000 fluid:water
    out 2 item:iron_nugget
}
```

**Eingefüllt, nicht geplant** — die Entscheidung, an der alles andere hängt.
Der Planner rechnet mit Gegenständen; ihm Flüssigkeiten beizubringen hieße,
eine Ressourcenart zu brauchen, die offen ist statt fest, und das ist eine
eigene Entscheidung (steht in `offene-punkte.md`). Der *Ausführende* dagegen
muss sie bewegen, aus zwei Gründen: Sonst behauptet die Zeile etwas, das nie
geschieht, und wer den Tank per Worker füllt, käme mit dem Netzbestand
durcheinander — der Auftrag sähe den vollen Tank nicht und wartete ewig auf
Wasser, das längst dort ist.

Der erste Entwurf prüfte deshalb nur den Bestand und ließ das Füllen dem
Spieler. Das wäre ein Selbstwiderspruch im selben Commit gewesen: derselbe
Weg, auf dem die Gegenstände eingelegt werden, hätte die Flüssigkeit daneben
liegen gelassen.

**Die Menge wächst mit den Durchgängen.** Die Gegenstände gehen für alle
Durchgänge auf einmal in die Maschine (`step.consumed()` ist schon
hochgerechnet), die Nebenzutat aber steht je Durchgang im Rezept. Ohne
`needFor(runs)` stünde dort eine Maschine mit vier Erzen und einem Eimer. Das
ist der eine Fehler, den ein Einheitstest ohne Welt fassen kann — deshalb
liegt er dort.

**Die Station trägt den Rezeptnamen mit**, `at:brecher#erz_mahlen`. Zwei
Rezepte am selben Gerät mit derselben Ausgabe ließen sich sonst nicht
unterscheiden, und dann bekäme die Maschine das Wasser des anderen. Eine
gespeicherte Station aus einer älteren Welt hat kein Doppelkreuz; sie liefert
weiterhin ihr Gerät und eine leere Zutatenliste, und der Auftrag läuft weiter.

**Die Probe steht vor jeder Bewegung** — jetzt auch für das, was kein
Gegenstand ist. Fehlt das Wasser, bleibt das Erz liegen: Eine Maschine mit
halber Rechnung fängt nie an, und das Erz wäre aus dem Netz verschwunden.

**Eine Sorte je Zutat.** Trifft die Auswahl mehrere, wird die genommen, von
der genug da ist — ein Tank hält meist genau eine.

Ungeprüft im Spiel bleiben die Chemikalien, dieselbe Lücke wie beim
Chemikalien-Worker: Ein Mekanism-Tank, der per `setBlock` in einen Prüflauf
gestellt wird, hat keine Seitenkonfiguration und nimmt nichts an — nachgemessen.
Der Weg ist derselbe wie bei Flüssigkeiten, und die Rechnung darüber ist
geprüft.

### Maschinen im Autocrafting (seit dem 26.08.)

Der erste Schnitt von Weg B (2.9): Die Rezeptarten mit **fester, bekannter
Form** laufen von selbst — Ofen, Schmelzofen, Räucherofen und die eigene
Presse. Was eine fremde Mod kann, schreibt der Spieler auf; das ist der zweite
Schnitt.

**Steinsäge und Werkbank laufen beide am Fabricator.** Das war eine
Berichtigung an der eigenen Erkundung: Ein Steinsägen-Rezept ist lesbar, aber
der Block hat keine BlockEntity — niemand kann hineinschieben. Lesbar und
ausführbar sind zwei Fragen. Am Fabricator passt es, denn beides ist Handarbeit
ohne Maschine und in einem Zug erledigt.

**Der laufende Schritt wird gespeichert, der Plan nicht.** Das ist der
Unterschied, an dem die ganze Ausführung hängt: Ein Plan ist eine Absicht und
darf veralten; ein Erz, das im Ofen liegt, ist eine Tatsache über die Welt.
Solange ein Schritt läuft, tut der Auftrag nichts anderes — sonst sähe der neu
gerechnete Plan „Erz weg, Barren nicht da" und legte ein zweites Mal ein.

**Die Station im Plan ist die Rezeptart, nicht das Gerät.** Welcher Ofen es
wird, entscheidet erst der Ausführende, und er nimmt einen freien. Stünde
schon beim Planen ein Gerätename da, wartete ein Auftrag vor einem
beschäftigten Ofen, während zwei leere danebenstehen.

**Erkannt wird an der Klasse der BlockEntity**, nicht am Namen: Ein Ofen heißt
auf einem englischen Server anders als im deutschen Client.

**Der Zugriff geht über das ungeteilte Inventar**, wie bei `slots(…)`. Ein
Ofen nimmt oben an und gibt unten aus; über die Seitenregeln bräuchte ein
Auftrag zwei Connectoren, und dass einer reicht, ist eine getroffene
Entscheidung. Den Brennstoff legt weiterhin der Spieler hin — das Netz baut,
der Spieler versorgt.

### Nachschub ist derselbe Worker (seit dem 25.08.)

`from crafting` bestellt, was unter `maintain` gefallen ist. Der Grund, warum
`from` eine Quelle nennt und keine Betriebsart: „hol es aus dem Lager" und
„lass es herstellen" bekommen dieselbe Form, und Vorratshaltung braucht keine
eigene.

**Gerechnet wird gegen Bestand und offene Aufträge.** Das ist die Stelle, an
der so ein Worker sonst schiefgeht: Der Bestand steigt erst, wenn der Auftrag
fertig ist, und ein Worker, der nur ihn ansieht, bestellt jede Runde neu. Aus
„halte 256 vor" würden Tausende, und die Fabrik verstopft sich mit
Bestellungen über dasselbe.

**Drei Formentscheidungen**, alle drei selbst getroffen und in
`entscheidungen.md` begründet: Ziel ist `storage` und nur das, `maintain` ist
Pflicht, und `rate` begrenzt die Bestellung nur, wenn es dasteht.

**`crafting` steht wieder in beiden Editoren** — aber nur hinter `from`. Es
war mit Punkt 3.10 herausgeflogen, weil ein Vorschlag, der in eine
Fehlermeldung führt, schlechter ist als keiner. Jetzt führt er irgendwohin,
und hinter `to` weiterhin nicht.

### Rechte im Mehrspielerbetrieb (seit dem 25.08.)

Bis hierher durfte jeder alles: Wer an ein Terminal kam, konnte das Programm
einer fremden Fabrik überschreiben. Im Einzelspieler ist das richtig, auf
einem Server nicht — und es fiel nirgends auf, weil ein überschriebenes
Programm keine Meldung hinterlässt, sondern nur eine Anlage, die plötzlich
etwas anderes tut.

Drei Stufen in der Serverkonfiguration, **Vorgabe bleibt „jeder"**: Eine Mod,
die nach einem Update Fabriken sperrt, an denen zwei Leute gemeinsam bauen,
hat dasselbe Problem in die andere Richtung.

Der Besitzer wird beim Setzen des Controllers gemerkt — **auch dann, wenn der
Schutz aus ist.** Wer ihn erst später einschaltet, hätte sonst lauter
herrenlose Anlagen. Und ein Controller ohne Besitzer gehört allen: Ihn
niemandem zuzuordnen wäre eine Sperre, die niemand aufheben kann.

Geschützt sind die zwei Wege zum Programm — übernehmen und den Entwurf
speichern. Die Beschriftungspistole nicht: Einen Connector umzubenennen
bricht Programme genauso, ist aber eine Handlung in der Welt wie das Abbauen
eines Blocks, und dafür gibt es Schutzmods, die es besser können als eine
Logistikmod.

### Die Referenzseite im Handbuch (seit dem 25.08.)

Alles, was die Sprache kennt, auf einer Seite — und **erzeugt statt
geschrieben**, aus `Signatures`, mit demselben Verfahren wie `signatures.json`:
Der Test baut die Seite aus dem Code, vergleicht sie mit der eingecheckten,
schreibt sie bei Abweichung neu und scheitert trotzdem.

Eine Referenz ist die Seite, die niemand liest, bis sie gebraucht wird — und
dann muss sie stimmen. Von Hand gepflegt wäre sie nach der dritten neuen
Angabe die Fassung von vorletzter Woche, und man merkte es genau dann nicht,
wenn man sich darauf verlässt.

### Die Serverkonfiguration (seit dem 25.08.)

Bis dahin war jede Grenze für Nutzercode eine Zahl im Quelltext. Für den
Betreiber eines Packs ist das die falsche Stelle: Er kennt seine Spieler und
seine Hardware, die Mod nicht.

Zwei Werte in `config/factorynetwork-server.toml`: das **Schrittbudget** je
Durchlauf — die Grenze, an der eine Endlosschleife abbricht — und die
**Suchtiefe** beim Aufbau des Netzgraphen.

**Nur Grenzen, keine Spielzahlen.** Was ein Connector an Strom kostet und wie
viele Kanäle ein Kabel trägt, steht bewusst nicht dort: Das ist Spielinhalt
und gehört zum Ausgleich der Mod. Wer es ändert, ändert das Spiel und nicht
seine Serverlast.

**Kein Clientteil.** Er kommt, wenn es etwas gibt, das ihn braucht — die
Brücke zu VS Code. Leere Abschnitte auf Vorrat wären Fragen an den Betreiber,
die niemand beantworten kann.

Der Rückfall auf die Vorgaben ist dabei die eigentliche Sorgfalt: Ein
Einheitstest lädt keine Konfigurationsdatei, ein Datengenerator auch nicht,
und ein Wert, der dann wirft, macht aus einer Einstellung einen Absturz an
Stellen, die mit Einstellungen nichts zu tun haben.

### Der Controller-Anbau (seit dem 25.08.)

Der Controller hat sechs Seiten, an jeder hängt ein Strang, ein dichtes Kabel
trägt vierundsechzig Kanäle — 384 Geräte je Netz. Der **Anbau** bringt sechs
weitere Seiten mit, und die Suche im Graphen beginnt seither nicht mehr an
einem Knoten, sondern an allen Blöcken der Gruppe.

Das ist die ganze Änderung am Netz: Wer eine Wurzel mehr hat, hat sechs
Stränge mehr, und alles Weitere — Kanäle je Kabelstück, Farben, Router —
rechnet unverändert.

**Der Anbau muss den Controller berühren.** Diese Frage stand im Entwurf
nicht und ist die wichtigste: Ließe sich ein Anbau ankabeln, wäre er ein
beliebig oft setzbarer Kanalvermehrer, sechs neue Seiten für einen Block
irgendwo im Gelände, und die Kanalgrenze bedeutete nichts mehr. Der Test dazu
war der einzige, der von Anfang an grün stand — und muss es bleiben.

Er hält nichts, hat keine BlockEntity und kostet keinen Kanal. Strom kostet er
wie Laufwerk und Router: Ein Ausbau, der nichts kostet, ist keine
Entscheidung.

### Der Vorrat des Netzes als Wert (seit dem 26.08.)

```
when network.power < 5000
```

Damit ist 2.5 zu. `strom.md` §8 nannte `network.power` seit langem als das,
was es nicht gibt: `network` war ein Ziel für Strom und sonst nichts, und wer
einen Worker anhalten wollte, solange der Vorrat knapp ist, hatte keine Zahl
dafür — der Stand stand nur im Netz-Reiter, und den liest kein Programm.

**Ohne Klammern**, anders als `brecher.energy()`. Der Unterschied ist keine
Geschmacksfrage: `energy()` ist ein Blick in eine fremde Maschine und kostet
eine Abfrage; der eigene Vorrat liegt im Controller, der ihn ohnehin je Takt
fortschreibt. Damit steht er auf derselben Stufe wie `online`.

**`capacity` kommt mit**, obwohl die Punkteliste nur `power` nannte. Eine Zahl
ohne Bezugsgröße lässt sich nicht anzeigen: `progress` will einen Anteil, und
„12.000 FE" heißt in einem Netz mit einer Energiezelle etwas anderes als in
einem mit dreißig. Bei `energy()` gibt es die Bezugsgröße nicht — dort weiß
das Netz nicht, wie groß der Speicher einer fremden Maschine ist.

**Ohne Welt wird nichts erfunden.** Ein Host ohne Netz meldet sich, statt null
zu liefern — dieselbe Haltung wie bei `countIn`. Null hieße „leer", und leer
ist etwas anderes als „gibt es hier nicht".

**Der Fehler, den erst der Prüflauf zeigte:** Das erste Handbuchbeispiel
schrieb `progress "Netz" network.power / network.capacity`. Beides sind ganze
Zahlen, also teilt Java ganzzahlig, und der Balken hätte immer auf null
gestanden. Jetzt steht `* 1.0` dort, und ein Einheitstest hält beide Fälle
fest, statt es nur zu behaupten.

Verdrahtet ist es an derselben Stelle wie der Chemikalienspeicher —
nachgereicht statt im Konstruktor. Ein vergessenes `setPower` fiele sonst
nirgends auf: Ohne Welt meldet sich der Ausdruck ehrlich, mit Welt sähe man
dieselbe Meldung und hielte sie für richtig. Deshalb steht die Verdrahtung als
eigener Prüflauf da, gegengeprobt durch Entfernen der Zeile.

### Die Stromverteilung (seit dem 25.08.)

Ein Worker mit `filter power` bewegt jetzt wirklich Strom. **Eine Seite ist
immer das Netz** — `from network to crusher_1` versorgt, `from akku_1 to
network` speist ein; Strom von Maschine zu Maschine wäre eine Leitung ohne
Kabel. Wer es doch schreibt, liest den Satz im Terminal, statt ihn zu erraten.

Es gibt **keine Kabelgrenze**. Was fließt, begrenzen die Rate des Workers, der
Vorrat des Netzes und was die Maschine annimmt. Eine zweite Knappheit neben
`priority` hätte zwei Ursachen für dasselbe Symptom bedeutet, und die zweite
sieht man nirgends.

Dabei fiel auf, dass **`priority` bis dahin überhaupt nichts tat**: Die Zahl
stand seit dem ersten Tag in der Grammatik und in jedem Beispiel, gelesen hat
sie niemand. Jetzt sortiert sie die Worker vor dem Tick — für Gegenstände und
Flüssigkeiten mit.

**Der Vorrat des Netzes liegt im Laufwerk.** Energiezellen sind die dritte
Zellenart neben Gegenstands- und Flüssigkeitszellen, in denselben Laufwerken.
Eine Energiezelle hat nur eine Zahl: Bei Strom gibt es keine Sorten, und damit
fehlt der Reiz, der bei den anderen im Sortieren liegt. Ein Akku ist eine
Zahl.

Der Puffer im Controller wird zuerst gefüllt und zuerst geleert; die Zellen
sind die Reserve. Was durchläuft, berührt damit keinen einzigen Gegenstand.

Die übrigen Befunde stehen in `strom.md` §10 — darunter der, dass die
Kreativquelle nichts hergab und die Einspeiserichtung deshalb nicht prüfbar
war.

### `device_output` (seit dem 25.08.)

`await device_output(crusher)` wartet, bis im Brecher etwas liegt, das das
Netz nicht hineingelegt hat. Verglichen werden die Mengen je Art mit denen
vom letzten Blick — alle zehn Ticks, in derselben Schleife wie
`device_changed`, und nur, wenn ein Programm zuhört. Nur mehr zählt, also
löst weder Verbrauch noch Entnahme etwas aus, und gemeldet wird jeder
Zuwachs: Eine Maschine, die eine Ladung stückweise ausgibt, meldet jedes
Stück.

**Was das Netz selbst einlegt, zählt nie mit** — jede Lieferung zieht die
Grundlinie sofort nach. Das war die Stelle, an der der Entwurf beim Bauen
nachgab: Gedacht war ein Rückruf im Interpreter, weil dort `move` und
`insert()` zusammenlaufen. Die Worker schreiben aber auf eigenem Weg, und
beide legen zurück, wenn der Netzspeicher voll ist — vier Stellen, von denen
eine vergessene genügt hätte, damit ein Ablauf sich selbst weckt. Jetzt hängt
die Meldung am Inventar (`NotifyingHandlers`): Wer ein Gerät auflöst, bekommt
ein meldendes Inventar, und wer schreibt, muss nichts davon wissen.

**Der Test, der zuerst nichts prüfen konnte.** Der erste Worker-Test lief mit
`rate 8 per 1t` und war nach acht Ticks fertig — noch vor dem ersten Blick,
und der erste Blick meldet nie. Er war grün, ohne den Fehler je fangen zu
können. Mit `rate 8 per 20t` und einer Zwischenmessung liefert der Worker
noch, während die Grundlinie längst steht; erst so schlug er fehl, und erst
danach war seine grüne Farbe etwas wert.

### Filter-Vorlagen (seit dem 25.08.)

`filter erze { … }` gibt einer Auswahl einen Namen, und der steht überall,
wo eine Auswahl steht — im Worker, in `move`, in `count`. Jede Zeile im Block
legt dazu, eine Zeile mit `except` nimmt weg; erst alles zusammen, dann die
Ausnahmen.

**Die Sprache brauchte dafür weniger, als der Entwurf annahm.** `except` gab
es schon. Neu sind nur der Name für eine Auswahl und die Vereinigung mehrerer
— ein Worker nimmt genau eine `filter`-Zeile, eine zweite wäre stillschweigend
übergangen worden.

**`except` wirkte bis dahin nur im Worker.** Der Interpreter wertete
`Expr.Except` als seine Grundlage aus und warf die Ausschlüsse weg; in `move`
und `count` stand die Ausnahme da und tat nichts, obwohl `sprache.md` sie seit
dem Entwurf zeigt. Gefunden beim Nachsehen für den Entwurf, repariert im
selben Zug — beide brauchen dieselbe Stelle, weil sich eine Ausnahme erst
nach dem Auflösen anwenden lässt.

**Nur einer der Schalter war erschöpfend.** Der Plan rechnete mit fünf
`switch` über `Decl`, die ein neuer Record unvollständig macht. Vier davon
haben ein `default` — gebrochen ist nur `ProgramSize`. Die anderen vier
mussten trotzdem angesehen werden: Ein `default`, das eine neue
Deklarationsart verschluckt, ist kein Übersetzungsfehler, sondern eine
Auskunft, die ausbleibt.

**Ein unbekannter Vorlagenname hält den Worker an.** Der naheliegende Weg —
eine leere Liste zurückgeben — hieße für ihn „kein Filter", und kein Filter
heißt „alles": Ein Tippfehler im Namen hätte das ganze Lager umgezogen.

### Der Editor, eigener Strang

Der Code-Editor läuft neben dieser Liste her; er hängt an nichts davon ab.
Stand heute:

**Steht.** Zeilennummern, Cursor, Auswahl mit Tastatur und Maus, Doppelklick
auf Wort, Ziehen mit Mitrollen, Kopieren/Einfügen/Ausschneiden, Rückgängig mit
gruppierten Tippläufen, Suche, Auto-Einrückung, Tabulator über Auswahlen,
Klammernpaare über Zeilengrenzen, Syntaxfarben, Vorschläge, Fehlermarken.
Dazu das Projekt aus mehreren Dateien, der Ordner neben der Welt als Brücke,
und der Dateibaum im eigenen Fenster (`CodeScreen`) mit Anlegen, Umbenennen,
Verdoppeln, Löschen.

**Seit der Nacht auf den 24.08. dazugekommen.** Auslöser war ein Satz, der
das Kernproblem genauer traf als meine eigene Liste: *„Ich sehe nie, was ich
wo angeben muss."* Gemessen stimmte er wörtlich — hinter `title `, hinter
`row ` und hinter `row "Bestand" ` bot der Editor jedes Mal dieselbe Liste
aller Schlüsselwörter an.

`Signatures` ist die Tabelle aus `grammatik.md` als Daten: je Schlüsselwort
die Stellen dahinter, ihre Art und ein Satz Erklärung. Sie liegt bewusst im
Sprachpaket und nicht im Editor — sie gehört zur Sprache, und ein
Sprachserver für VS Code liest später dieselbe. Daraus kommen:

- Vervollständigung nach der Stelle, die gerade dran ist (Ziel → Connectoren,
  Ausdruck → Bestände, `strategy` → Verteilungen, `button` → Funktionen des
  Projekts, Text → nichts)
- jeder Vorschlag mit seiner Form daneben: `row` steht mit `string expr`
- eine Formzeile über dem Cursor mit der ganzen Form, der aktiven Stelle
  hervorgehoben und einem Satz dazu
- Strg+Leertaste
- F1 mit einer Griffliste, weil ein Editor im Spiel keine Menüleiste hat

Dazu Querscrollen mit Beschnitt, Ersetzen (Strg+H, Alt+Eingabe für alle) und
der Dateibaum mit Umbenennen, Verdoppeln und Löschen.

Dazu der **Entwurf auf dem Server**: Der Controller hält jetzt zwei Stände —
das Programm, das läuft, und das, was im Editor steht. Der Entwurf darf kaputt
sein, der laufende Stand nicht; ein Tippfehler hält die Fabrik nicht an.
Gesichert wird eine Sekunde nach dem letzten Anschlag, bei Strg+S und beim
Schließen. Damit ist der Datenverlust weg, der vorher jedem Absturz folgte.

Und weiter in derselben Nacht:

- **Anweisungen** stehen ebenfalls in `Signatures` — `let`, `if`, `while`,
  `for`, `move`, `emit`, `sleep`, `return`, `await`. `move` war der Fall, an
  dem sich die Tabelle beweisen musste: `move menge [from quelle] to ziel`
  hat eine Stelle, die wegfallen darf.
- **Namen werden gegen das echte Netz geprüft.** `NetworkView` gibt dem
  Übersetzer wahlweise die Connectoren- und Anzeigenamen; eine Anzeige ohne
  Wand oder ein Ziel, das niemand so genannt hat, ist eine Warnung mit
  „meintest du". Der Client prüft beim Tippen, der Server beim Übernehmen —
  letzteres auch für den, der über den Ordner neben der Welt schreibt.
- **Strg+Klick** springt zur Erklärung eines Namens oder, wenn er aus der
  Welt kommt, markiert ihn dort: ein Kasten um den Block und der Name
  darüber, durch Wände sichtbar. Zeigen nennt Stelle und Fundstellen.
- **Der Entwurf liegt auf dem Server** und überlebt Absturz und Ausloggen.
- **Dateisperren.** Wer schreibt, hält die Datei; die anderen sehen sie im
  Lesemodus mit dem Namen des Halters. Eine Sperre verfällt nach einer
  Minute ohne Schreiben und beim Schließen des Terminals.
- **Die VS-Code-Erweiterung** vervollständigt nach denselben Regeln. Ihre
  Tabelle wird aus `Signatures.java` erzeugt, ein Test hält beide gleich,
  und `editor/vscode/check.js` prüft die portierte Logik gegen dieselben
  Fälle wie der Java-Test.

**Die Geräte hinter den Connectoren** (seit dem 24.08.). Der Server probt beim
Öffnen alle sechs Seiten der Maschine und den seitenlosen Zugang; daraus weiß
der Editor, was dort steht und was es an welcher Seite annimmt. Das speist
drei Stellen:

- Die **Vorschlagsliste** nennt hinter jedem Connector Maschine und
  Fähigkeiten: `crusher_1 — Crusher · Gegenstände, Strom`.
- Das **Zeigen** nennt dazu die Fächer je Seitengruppe und, auf Anfrage, ihren
  Inhalt. Gefragt wird erst, wenn der Zeiger eine Viertelsekunde stillhält.
- Ein **Worker**, der Gegenstände an eine Seite schickt, die keine annimmt,
  bekommt eine Warnung mit der Seite, an der es ginge. Nach Ressourcenart
  unterschieden: Ein Flüssigkeits-Worker am Tank ist in Ordnung.

Dazu Vorschläge nach dem Punkt: `crusher_1.` bietet `online`, `name`,
`redstone()` und `count()` an — die vier, die der Interpreter wirklich kennt.
Auch in VS Code, über dieselbe erzeugte Tabelle.

Nebenbei begradigt, alles beim Prüfen im Spiel gefunden:

- Die **Erklärung eines Namens** beim Zeigen — Stelle im Netz, Erklärungsort,
  Fundstellen — gab es nur im eigenen Fenster, nicht im Reiter. Sie steht
  jetzt in `EditorTooltip` und gilt für beide.
- **Strg+Klick** gab es ebenfalls nur im eigenen Fenster. Der neue Tooltip
  kündigte ihn im Reiter an, wo er ins Leere lief — ein Hinweistext, der auf
  etwas verweist, das es nicht gibt. Die Logik steht jetzt in `NameJump`,
  beide Fenster nutzen sie, und beide melden zurück, wo markiert wurde.
- **Luft ist keine fehlende Auskunft.** Ein Connector, der ins Leere zeigt,
  meldete „Nicht geladen" — vor einem Spieler, der davorstand. Jetzt sagt er,
  dass dort keine Maschine steht, und rät, ihn umzudrehen. Ein GameTest hatte
  den Fehler festgeschrieben.
- **Die Marke stand nie, wenn man davorstand.** Sie verschwand, sobald der
  Spieler näher als drei Blöcke war — also immer dann, wenn man vor vier
  Connectoren steht und fragt, welcher gemeint ist. Sie hält jetzt eine halbe
  Minute auf jeden Fall.
- **Und sie lag hinter den Wänden**, obwohl ihr Zweck das Gegenteil ist:
  `RenderType.lines()` bringt einen Tiefentest mit. Sie wird jetzt gezeichnet
  wie das Netz im Analysator, mit `RenderSystem.disableDepthTest()`.
- `SignaturesExportTest` schlug nach jedem Zweigwechsel fehl, weil er LF
  schreibt und Git unter Windows CRLF auscheckt. Es gibt jetzt eine
  `.gitattributes`, und der Test gleicht Zeilenenden an.

Der Entwurf dazu ist `docs/geraeteerkennung.md`, der Umsetzungsplan
`docs/plan-geraeteerkennung.md`.

**Fehlt, in dieser Reihenfolge:**

1. **Die Gerätemitglieder aus `sprache.md` §6.** `insert()`, `items()`,
   `output()`, `send()` und `busy` sind beschrieben und nicht gebaut; nach
   dem Punkt stehen deshalb für jedes Gerät dieselben vier Einträge. Bei
   `busy` ist vorher zu klären, woher der Wert kommen soll — es gibt keine
   Capability, über die eine fremde Maschine „ich arbeite gerade" meldet.
2. **„Bearbeitung anfragen".** Die Sperre hält, aber wer vor einer fremden
   Datei steht, kann nur warten. Ein Knopf, der beim Halter anklopft, wäre
   das fehlende Stück.
3. **Ein Sprachserver für VS Code.** Erst der brächte dort Fehlerprüfung und
   Gerätenamen. Er müsste den Übersetzer aus dem Mod-Projekt aufrufen und mit
   dem laufenden Spiel reden — eine eigene Entscheidung, keine, die nebenbei
   fällt.

**Geprüft und verworfen:** Tastenwiederholung beim Halten funktioniert schon,
Minecraft leitet `GLFW_REPEAT` an `keyPressed` weiter. Strg+Rollen zum Zoomen
bliebe unscharf, weil Minecrafts Schrift bei nicht ganzzahligen Maßstäben
verwischt — die Spieloberfläche hat dafür eine eigene Einstellung.

**Offene Fragen, nicht entschieden:**

- **Die Annahme-Probe.** Ob ein Fach einen bestimmten Gegenstand nimmt, lässt
  sich nur durch einen simulierten Einfügeversuch beantworten, und der braucht
  Kandidaten. Vorgesehen sind die `item:`-Literale des Entwurfs; gebaut ist es
  nicht. Das Zeigen sagt heute, welche Fächer es gibt und was drin liegt, aber
  nicht, ob `iron_ore` in Fach 0 passt. Siehe `docs/geraeteerkennung.md`,
  Abschnitt 3.

Nicht vorgesehen: Piece Table, Rope, virtualisiertes Zeichnen, inkrementelles
Lexen, Mehrfachcursor, Minimap, Faltung. Das längste Beispielprogramm hat
fünfzehn Zeilen, der beste Datenträger fasst 4096 Anweisungen, und die
Pakete deckeln bei 64 KB je Datei. Der Renderer lext heute jede sichtbare
Zeile in jedem Bild neu und kostet nichts messbares.

Zum Ausprobieren stehen lauffähige Programme in `beispiele.md` — eines je
Fähigkeit, mit der Angabe, was dafür in der Welt stehen muss.

Vorher lohnt ein Blick in `entscheidungen.md`: Dort steht zu jedem Punkt,
warum er so entschieden wurde, und was verworfen wurde.

### Falle: `super.render` am Ende einer Zeichenroutine

`Screen.render` ruft als **erstes** `renderBackground`, und das ruft
`GameRenderer.processBlurEffect` — einen Nachbearbeitungsschritt, der den
Bildpuffer weichzeichnet. In Vanilla steht der Aufruf am Anfang, also trifft
er die Welt hinter dem Fenster; das Fenster wird danach scharf darübergemalt.

Wer eigene Flächen zeichnet und **danach** `super.render(...)` aufruft, schickt
den Weichzeichner über die eigene Oberfläche. Das sieht nicht nach einem
Zeichenfehler aus, sondern nach schlechter Farbwahl: Kanten verschwinden,
Text verschmiert, alles wirkt matschig. Zwei Fenster hatten das —
`CodeScreen` und `LabelGunScreen`.

Messbar war es erst am Querschnitt durch eine Kante: Eine ein Pixel breite
helle Linie stand als zwanzig Pixel breiter Verlauf ohne jeden Höhepunkt im
Bild. Der Mauszeiger daneben war scharf, weil ihn das Betriebssystem malt und
nicht das Spiel — das ist das Erkennungszeichen.

Richtig ist eines von beidem:

- `renderBackground(...)` ganz am Anfang, `super.render(...)` danach —
  so machen es die Fenster mit Inventar, und `AbstractContainerScreen.render`
  hält sich selbst daran.
- Gar kein Hintergrund, wenn das Fenster ohnehin alles abdeckt, und statt
  `super.render(...)` nur die Schleife über `this.renderables`.
