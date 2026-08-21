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

1. **Controller** setzen. Er ist die Wurzel des Netzwerks und hält Programm
   und Speicher.
2. **Kabel** vom Controller weg legen.
3. **Connector** an ein Kabel setzen, mit der Vorderseite an eine Kiste oder
   Maschine. Die Vorderseite zeigt dorthin, wo man den Block angeklickt hat.
4. **Label-Gun** nehmen und einmal auf den **Controller** rechtsklicken —
   damit ist sie mit dem Netz verbunden. Dann auf einen Connector klicken: Er
   bekommt einen Namen aus der Maschine dahinter, durchnummeriert
   (`furnace_1`, `furnace_2`, …). Schleichen + Klick übernimmt einen
   vorhandenen Namen, nochmal derselbe Name nimmt ihn wieder weg.

   Solange die Gun in der Hand ist, schweben die Namen über allen Connectoren
   in der Nähe: grün benannt, grau unbenannt, rot doppelt vergeben.
5. **Terminal** neben den Controller setzen und anklicken.

Im Editor:

```
worker quarry_import {
    from quarry_output
    to storage
}
```

**Strg+Eingabe** übernimmt. Links stehen die Connectoren, die das Netz kennt;
**Tab** übernimmt einen Vorschlag, die Pfeile wählen aus.

Ein Rechtsklick auf den Controller nennt die Zahl der Connectoren und Kabel —
nützlich, wenn ein Name im Editor nicht auftaucht.

---

## 2. Was läuft

| | |
|---|---|
| Sprache | Lexer, Parser, Fehlerbehebung — die Grammatik vollständig |
| Worker | `from`, `to`, `filter`, `maintain`, `rate`, `when`, `strategy`, `overflow` |
| Auswahl | einzelne Gegenstände, Tags, Muster, `except` |
| Flüssigkeiten | `move` und Worker, Bestand im Netz, in Millibucket |
| Funktionen | Bedingungen, Schleifen, `move`, Redstone lesen, `log` |
| Ereignisse | `redstone_changed`, `device_online/offline/changed`, eigene über `emit` und `on` |
| Abläufe | `sleep`, `await` mit `where`, `timeout` und `else`, `for` mit Warten je Runde |
| Aufrufe | Eine gerufene Funktion darf selbst warten — beide Rahmen überstehen den Neustart |
| Multiblocks | Vorlagen, gebaute Anlagen über `anlage/rolle`, Aufruf an der Instanz |
| Fortsetzen | Wartende Abläufe überleben Serverneustart und Programmwechsel |
| Netzwerk | Graph über Kabel, Speicher schlüsselbasiert, Kanäle je Strang |
| Editor | Syntaxfarben, Fehler beim Tippen, Vervollständigung nach Stelle |
| Anzeigen | Am Block und im Terminal, Knöpfe starten Abläufe |
| Prüfung | 87 Einheitstests, 78 GameTests |

## 3. Was noch nicht läuft

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
- **Flüssigkeiten im Speicher-Reiter.** Sie stehen zurzeit in der
  Netzübersicht; ein Symbolraster bräuchte das Bild der Flüssigkeit.
- **Ein eigener Speicherblock.** Der Speicher sitzt zurzeit im Controller.
- **`strategy` bei Tankgruppen.** Die Verteilung nach dem leersten Gerät misst
  Gegenstands-Slots; eine Gruppe von Tanks sortiert sie damit zufällig.
- **Ein Teilerfolg beim Füllen** hinterlässt den Worker auf `WAITING_TARGET`
  („Das Ziel nimmt nichts mehr"), obwohl etwas gelaufen ist. Die Zahl stimmt,
  die Beschreibung im Terminal nicht.
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

Zum Ausprobieren stehen lauffähige Programme in `beispiele.md` — eines je
Fähigkeit, mit der Angabe, was dafür in der Welt stehen muss.

Vorher lohnt ein Blick in `entscheidungen.md`: Dort steht zu jedem Punkt,
warum er so entschieden wurde, und was verworfen wurde.
