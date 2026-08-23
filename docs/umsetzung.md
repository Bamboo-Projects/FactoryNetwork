# Umsetzung — Stand und Anleitung

Was gebaut ist, wie man es ausprobiert, und was bei der Umsetzung auffiel.

Stand: 2026-08-20

---

## 1. Ausprobieren

```
./gradlew runClient          Spiel mit der Mod starten
./gradlew test               Übersetzer und Laufzeit prüfen (schnell)
./gradlew runGameTestServer  In einer echten Welt prüfen (etwa eine Minute)
./gradlew syncResources      Texturen und Texte ins laufende Spiel schieben
```

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
| Worker | `from`, `to`, `filter`, `maintain`, `rate`, `when`, `strategy`, `overflow` |
| Auswahl | einzelne Gegenstände, Tags, Muster, `except` |
| Flüssigkeiten | `move` und Worker, Bestand in Zellen, in Millibucket |
| Speicher | Laufwerke mit zehn Plätzen, Zellen in vier Größen, Bestand in der Zelle |
| | Regalfenster für Laufwerk und Schrank, Bestückung an der Front ablesbar |
| | Router: Seite anklicken schaltet weiter, Schleichen öffnet alle sechs auf einmal |
| Rechenleistung | Serverschränke, zwei Blöcke hoch, mit zwölf Einschüben |
| | Je Einschub Rechenwerk, Speicher und Datenträger in vier Stufen |
| | Erst alle drei ergeben einen Server; unfertige Einschübe tragen nichts |
| Strom | FE in den Controller, Verbrauch je Gerät, aus bei Unterversorgung |
| | Brennkammer als eigene Quelle, Kreativ-Stromquelle zum Prüfen |
| | Ein laufender Ablauf belegt einen Platz, der Rest stellt sich an |
| | Zelle in der Hand ans Laufwerk klicken setzt sie ein, leere Hand nimmt die letzte heraus |
| Werkzeuge | Beschriftungspistole, Netzanalysator mit Sicht durch Wände |
| Funktionen | Bedingungen, Schleifen, `move`, Redstone lesen, `log` |
| Ereignisse | `redstone_changed`, `device_online/offline/changed`, eigene über `emit` und `on` |
| Abläufe | `sleep`, `await` mit `where`, `timeout` und `else`, `for` mit Warten je Runde |
| Aufrufe | Eine gerufene Funktion darf selbst warten — beide Rahmen überstehen den Neustart |
| Multiblocks | Vorlagen, gebaute Anlagen über `anlage/rolle`, Aufruf an der Instanz |
| Fortsetzen | Wartende Abläufe überleben Serverneustart und Programmwechsel |
| Netzwerk | Graph über Kabel, Speicher schlüsselbasiert, Kanäle je Strang |
| Editor | Syntaxfarben, Fehler beim Tippen, Vervollständigung nach Stelle |
| Anzeigen | Am Block und im Terminal, Knöpfe starten Abläufe |
| Prüfung | 87 Einheitstests, 86 GameTests |

## 3. Was noch nicht läuft

- **Die Anzeigenwand ist gebaut**, die Schrift wächst aber nicht mit. Wer
  eine Wand aus zwölf Tafeln baut, bekommt viel Platz für Text in
  Normalgröße — keine Überschrift, die man aus zwanzig Metern liest. Ob das
  fehlt, zeigt erst das Spielen.
- **`device_done`.** `device_online`, `device_offline` und `device_changed`
  laufen; „diese Maschine ist **fertig**" ist die offene Frage. Der
  konservative Weg — melden, dass sich etwas geändert hat, und die Deutung dem
  Spieler überlassen — ist gebaut. Was noch fehlt, ist die Bequemlichkeit. **Sie gehört entschieden, bevor
  sie gebaut wird** — ein falsches Fertig-Signal lässt eine Anlage Gegenstände
  verlieren, und das ist schlimmer als gar kein Signal.

  Drei Wege stehen zur Wahl:

  1. **Nach dem, was das Netz eingelegt hat.** Der Controller weiß, was er in
     ein Gerät gelegt hat. Taucht dort etwas anderes auf, ist verarbeitet
     worden. Braucht keine Fremdmod zu kennen, meldet aber zu früh, wenn im
     Ausgang schon etwas von vorher lag.
  2. **Gar kein `device_done`, dafür `device_changed`.** Das Inventar eines
     Geräts hat sich geändert — was „fertig" heißt, schreibt der Spieler
     selbst. Ehrlich und nie falsch, aber jede Vorlage muss es ausformulieren.
  3. **Je Mod angebunden.** Am genauesten und am teuersten; in einem großen
     Pack sind es Dutzende.

  Gebaut ist **(2)**. Offen ist, ob **(1)** dazukommt. Eine
  Automatisierung, die einmal zu früh weiterschaltet, verliert Gegenstände in
  einer Kiste, die niemand mehr findet.
- **Flüssigkeits-Tags.** `tag:` löst heute Gegenstands-Tags auf. Wie ein
  Flüssigkeits-Tag geschrieben wird, ist eine Frage an die Sprache und nicht
  nebenbei zu entscheiden — `fluidtag:c/molten` wäre eine vierte Art,
  `tag:` beide zu durchsuchen die andere Möglichkeit.
- **Die Zahlen an den Serverbauteilen.** Rechenwerke von zwei bis
  hundertachtundzwanzig, Speicher von acht bis fünfhundertzwölf, Datenträger
  von vierundsechzig bis viertausendsechsundneunzig. Sie sind gesetzt, nicht
  hergeleitet — wie sie sich anfühlen, zeigt erst das Spielen. Die
  Begründungen stehen in `entscheidungen.md` unter „Der Serverschrank".
- **Der Controller-Multiblock.** Der Controller hat sechs Seiten. An jeder
  hängt höchstens ein Strang, ein dichtes Kabel trägt vierundsechzig Kanäle —
  macht 384 Geräte je Netz. Das reicht heute und soll ausbaubar sein
  („haben ist besser als brauchen"). Mehrere Controllerblöcke aneinander
  wären ein Controller mit mehr Außenflächen, also mehr Strängen.

  **Vor dem Bauen ist eine Frage zu beantworten, und sie ist die ganze
  Schwierigkeit: Wo liegt das Programm, und was passiert damit, wenn genau
  dieser Block abgebaut wird?** Der Controller hält Programm, Speicherindex,
  laufende Abläufe und Stromvorrat. Drei Wege:

  1. **Der unterste, nördlichste Block hält alles.** Feste Regel, immer
     erklärbar. Setzt jemand einen Block *darunter*, wandert der Anker — und
     mit ihm muss der ganze Zustand umziehen. Auch die Datei neben der Welt
     heißt nach der Ankerposition und müsste mitwandern.
  2. **Der zuerst gesetzte hält alles, die anderen sind Anbauten.** Kein
     Umzug, solange er steht. Wird er abgebaut, ist die Frage nur verschoben:
     Wer übernimmt, und woher weiß man es beim nächsten Laden?
  3. **Ein eigener Block für die Erweiterung**, der nie etwas hält — der
     Controller bleibt einer und bekommt Anbauten, die nur Flächen
     beisteuern. Kein Umzug, keine Ankerwahl, keine Zustandswanderung. Dafür
     ein Block mehr im Kreativ-Reiter, und der Ausbau sieht nicht aus wie in
     AE2.

  Meine Empfehlung ist **(3)**. Die anderen beiden verlagern die
  Zustandswanderung in die zentralste Klasse der Mod, und ein Fehler dort
  kostet einem Spieler sein Programm. Ein zweiter Block ist der billigere
  Preis. Zu entscheiden ist das trotzdem vorher — die drei Wege sehen im
  Spiel verschieden aus, und danach lässt sich das nicht mehr ohne Bruch
  ändern.
- **Autocrafting.** Der letzte ausgegraute Reiter.
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

1. **`device_done` entscheiden** (siehe oben) und bauen. Der Baustein, der
   Multiblocks rund macht — ohne ihn muss jede Vorlage ihre eigenen Ereignisse
   auslösen.
2. **Flüssigkeiten und Chemikalien.** Die Schreibweise steht seit dem Entwurf;
   die Anbindung an fremde Mods ist die eigentliche Arbeit.
3. **Ein eigener Speicherblock.** Solange der Speicher im Controller sitzt,
   gibt es keinen Grund, mehr als einen zu bauen.
4. **Autocrafting.** Der letzte ausgegraute Reiter.

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

**Fehlt, in dieser Reihenfolge:**

1. **Querscrollen und ein Scissor.** Eine Zeile, die über die Fensterbreite
   hinausgeht, wird heute darüber hinaus gezeichnet. Das ist kein fehlendes
   Merkmal, das ist ein Fehler. Dazu die kleinen Dinge, die den Unterschied
   zwischen „Textfeld" und „Editor" ausmachen: Tastenwiederholung beim
   Halten prüfen, tote Tasten der deutschen Belegung (`^`, `´`), Strg+Rollen
   zum Zoomen.
2. **Entwurf auf dem Server.** Heute lebt er in `ClientProjectState`, also
   nur solange der Client läuft. Ein Absturz oder das Verlassen der Welt
   nimmt ihn mit, und zwei Spieler am selben Controller überschreiben
   einander beim Übernehmen wortlos. Dazu gehören Strg+S und eine
   Dateisperre mit Lesemodus. **Das ist der wertvollste offene Punkt** — er
   behebt einen Datenverlust, alles andere ist Komfort.
3. **Sprachdienst mit Netzschnappschuss.** `Completions` und `Lexer` hinter
   eine Fassade, dann Vervollständigung aus den echten Connectoren
   (`crusher_1.` bietet nur, was das Gerät kann), Erklärung beim Zeigen mit
   Zustand und Position, Sprung zur Deklaration, Fundstellen, „im Spiel
   zeigen". Die Connectordaten liegen für den Netzreiter schon auf dem
   Client. Das ist der Punkt, an dem sich das von jedem Textfeld
   unterscheidet.
4. **Ersetzen.** Suchen gibt es, Ersetzen nicht.

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
- **Die VS-Code-Erweiterung kennt noch Einzeldateien.** Unter `editor/vscode`
  liegen Grammatik, Klammern und elf Bausteine; die README beschreibt eine
  einzelne `.mf`-Datei statt eines Ordners.

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
