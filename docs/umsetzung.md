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
| Ausbau | Controller-Anbau: sechs weitere Seiten für Kabelstränge je Block |
| Fertigung | Fabricator baut Werkbank-Rezepte aus dem Netzspeicher, mehrstufig |
| | Fehlt eine Zutat, wird sie gebaut; genannt wird der Grundstoff |
| | `craft(64 item:chest)` im Code, Aufträge im Reiter, mit Abbruch |
| Server | `config/factorynetwork-server.toml`: Schrittbudget und Suchtiefe |
| | Schutz fremder Programme, wahlweise nach Besitzer oder Operator |
| | Ein laufender Ablauf belegt einen Platz, der Rest stellt sich an |
| | Zelle in der Hand ans Laufwerk klicken setzt sie ein, leere Hand nimmt die letzte heraus |
| Werkzeuge | Beschriftungspistole, Netzanalysator mit Sicht durch Wände |
| Funktionen | Bedingungen, Schleifen, `move`, Redstone lesen, `log` |
| Ereignisse | `redstone_changed`, `device_online/offline/changed/output`, eigene über `emit` und `on` |
| Abläufe | `sleep`, `await` mit `where`, `timeout` und `else`, `for` mit Warten je Runde |
| Aufrufe | Eine gerufene Funktion darf selbst warten — beide Rahmen überstehen den Neustart |
| Multiblocks | Vorlagen, gebaute Anlagen über `anlage/rolle`, Aufruf an der Instanz |
| Fortsetzen | Wartende Abläufe überleben Serverneustart und Programmwechsel |
| Netzwerk | Graph über Kabel, Speicher schlüsselbasiert, Kanäle je Strang |
| Editor | Syntaxfarben, Fehler beim Tippen, Vervollständigung nach Stelle |
| Anzeigen | Am Block und im Terminal, Knöpfe starten Abläufe |
| Prüfung | 383 Einheitstests, 237 GameTests |

## 3. Was noch nicht läuft

- **Die Anzeigenwand ist gebaut**, die Schrift wächst aber nicht mit. Wer
  eine Wand aus zwölf Tafeln baut, bekommt viel Platz für Text in
  Normalgröße — keine Überschrift, die man aus zwanzig Metern liest. Ob das
  fehlt, zeigt erst das Spielen.
- **Flüssigkeits-Tags.** `tag:` löst heute Gegenstands-Tags auf. Wie ein
  Flüssigkeits-Tag geschrieben wird, ist eine Frage an die Sprache und nicht
  nebenbei zu entscheiden — `fluidtag:c/molten` wäre eine vierte Art,
  `tag:` beide zu durchsuchen die andere Möglichkeit.
- **Die Zahlen an den Serverbauteilen.** Rechenwerke von zwei bis
  hundertachtundzwanzig, Speicher von acht bis fünfhundertzwölf, Datenträger
  von vierundsechzig bis viertausendsechsundneunzig. Sie sind gesetzt, nicht
  hergeleitet — wie sie sich anfühlen, zeigt erst das Spielen. Die
  Begründungen stehen in `entscheidungen.md` unter „Der Serverschrank".
- **`from crafting` als Worker-Quelle.** Die Fertigung selbst ist
  mehrstufig; was fehlt, ist die Vorratshaltung: ein Worker, der einen
  Auftrag aufgibt, sobald ein Bestand unter `maintain` fällt.
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

In dieser Reihenfolge, nach Abhängigkeit:

1. **Flüssigkeiten und Chemikalien.** Die Schreibweise steht seit dem Entwurf;
   die Anbindung an fremde Mods ist die eigentliche Arbeit.
2. **Ein eigener Speicherblock.** Solange der Speicher im Controller sitzt,
   gibt es keinen Grund, mehr als einen zu bauen.
3. **Autocrafting.** Der letzte ausgegraute Reiter.

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

- **Ordner im Projekt.** Heute ist der Namensraum flach, und das Namensmuster
  lässt keinen Schrägstrich zu. Unterordner wären reine Gliederung für den
  Menschen — die Sprache kennt ohnehin nur einen Namensraum. Bei drei bis
  acht Dateien tut es die alphabetische Sortierung mit Präfixen
  (`worker_erz.mf`, `worker_holz.mf`); ab etwa fünfzehn Dateien nicht mehr.
  Der Umbau beträfe das Namensmuster, den Ordner neben der Welt (rekursiv
  lesen) und die VS-Code-Seite.
- **LDLib2 als UI-Grundlage.** Noch nicht geprüft. Zu klären wäre der Stand
  für 1.21.1/NeoForge und wie die API aussieht. Die Latte liegt hoch: eine
  harte Abhängigkeit koppelt jeden Release an ein fremdes Projekt, und
  Dateibaum, Reiter und Felder sind je etwa zweihundert Zeilen von Hand.
  Docking ist das eine wirklich schwere Stück und zugleich das, was in einem
  Minecraft-Fenster niemand benutzt.
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
