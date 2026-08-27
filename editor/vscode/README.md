# Manifold in VS Code

Syntaxhervorhebung, Vervollständigung, Gliederung, Sprung zur Deklaration,
Umbenennen, Schnellkorrekturen und Fehlermarker für `.mf`-Dateien — die
Sprache, in der Factory Network programmiert wird.

## Wozu

Der Controller legt sein Programm als **Ordner** neben die Welt:

```
<Weltordner>/factorynetwork/controller_overworld_10_64_-20/
    main.mf
    worker.mf
    anzeigen.mf
```

Ein Programm ist ein Projekt aus mehreren Dateien. **Alle teilen einen
Namensraum:** Ein `fn` in der einen Datei wird in der anderen aufgerufen, ohne
dass irgendwo `import` steht. Wie man es aufteilt, entscheidet man selbst — die
Dateien sind Ordnung für den Menschen, keine Grenze für die Sprache.

Wer eine Datei speichert, hat sie eine Sekunde später im Spiel übernommen; was
im Terminal übernommen wird, steht sofort im Ordner. **Wer zuletzt geschrieben
hat, gewinnt.** Eine Datei, die hier gelöscht wird, ist auch im Spiel gelöscht
— in einem Projekt löscht man ein Programmstück absichtlich.

Den Ordner öffnet man am besten als Arbeitsbereich. Dann greifen Suchen über
alle Dateien, mehrere Cursor und Git.

## Einbauen

Ein Paket bauen und installieren — zwei Zeilen, von überall aus:

```
cd editor/vscode
npx --yes @vscode/vsce package --allow-missing-repository --skip-license
code --install-extension manifold-1.0.0.vsix --force
```

Danach VS Code neu starten. Eine `.mf`-Datei sollte farbig sein; unten rechts
steht „Manifold".

> **Den Ordner einfach zu kopieren reicht nicht mehr.** Bis VS Code 1.7x hat
> das funktioniert: Wer `editor/vscode` nach
> `%USERPROFILE%\.vscode\extensions\…` legte, hatte sie installiert.
> Heutige Fassungen führen daneben ein Verzeichnis (`extensions.json`) und
> laden nur, was darin steht — ein hingelegter Ordner bleibt unsichtbar, ohne
> dass irgendwo eine Meldung erscheint. Der Weg über das Paket trägt sich dort
> selbst ein.

Die Erweiterung selbst bleibt reines JavaScript: kein `npm install`, kein
Übersetzer, keine Abhängigkeit. `vsce` packt nur — gebraucht wird es für die
Registrierung, nicht für den Code.

## Was sie kann

**Vervollständigung nach der Stelle.** In einer Anzeige stehen `title`, `row`,
`text`, `progress`, `indicator`, `list`, `button` — und sonst nichts, denn ein
`displayEntry` enthält Ausdrücke, aber keine Anweisungen. In einem Worker seine
Angaben, in einer Funktion die Anweisungen und die Bestände.

Und innerhalb einer Angabe richtet sie sich danach, welche Stelle dran ist:
hinter `strategy` die Verteilungen, hinter `move 64` sowohl `from` als auch
`to` — die Quelle darf ja fehlen.

**Gliederung.** Was eine Datei erklärt, steht in der Übersicht — Funktionen,
Worker, Ereignisse, Anzeigen, globale Werte, Gruppen, Vorlagen und
Multiblöcke, jedes mit seiner Art. Nur die eigene Datei: Die Gliederung gehört
zum Fenster, und darin steht eine.

**Sprung zur Deklaration** (F12). Über das ganze Projekt, aus demselben Grund
wie die Vervollständigung: Der Namensraum ist einer. Ist ein Name doppelt
vergeben, kommen beide Stellen — dass er doppelt ist, meldet ohnehin der
Übersetzer, und bis dahin ist eine Auswahl ehrlicher als ein geratener Treffer.

**Umbenennen** (F2). Ebenfalls über das ganze Projekt, und ebenfalls nur als
ganzes Wort: Wer `kiste` in `kiste_1` mit umbenennt, hat ein Programm
zerschrieben, das vorher lief. **Nur erklärte Namen** lassen sich umbenennen —
ein Gerätename steht am Block in der Welt und nicht in einer Datei.

**Schnellkorrekturen.** Wo das Spiel weiß, was gemeint war — `chemiacl:` statt
`chemical:` —, steht der Vorschlag als anwendbare Korrektur da. Der Vorschlag
kommt aus dem Übersetzer und reist in der Statusdatei mit; diese Erweiterung
rät nicht selbst. Ersetzt wird dabei nur das Wort vor dem Doppelpunkt, obwohl
die Meldung die ganze Auswahl unterstreicht: Was dahinter steht, war ja
richtig.

**Namen aus dem ganzen Projekt.** Alle `.mf`-Dateien eines Ordners teilen einen
Namensraum, und die Vervollständigung liest sie alle: Eine Funktion aus
`werte.mf` steht in `main.mf` in der Liste, ohne dass irgendwo `import` steht.
Genauso die Ereignisse hinter `emit`, Gruppen und Multiblocks hinter `from`,
`to` und `members`, globale Werte an jeder Ausdrucksstelle. Die offene Datei
zählt dabei aus dem Puffer mit — eine Funktion, die man gerade geschrieben hat,
lässt sich sofort aufrufen und nicht erst nach dem Speichern. Der Ordner selbst
wird beim Speichern neu gelesen und sonst höchstens alle zwei Sekunden: In
denselben Ordner schreibt auch das Spiel.

**Formanzeige.** Beim Tippen steht die ganze Form da, mit der aktiven Stelle
hervorgehoben: `row string expr`, `move menge [from quelle] to ziel`.

**Erklärung beim Zeigen.** Form und ein Satz dazu.

**Bausteine.** Tippen und Tabulator drücken:

| Kürzel | Was daraus wird |
|---|---|
| `worker` | ein Worker mit `from`, `to`, `filter` und `rate` |
| `workermaintain` | ein Worker, der einen Sollstand hält |
| `fn` | eine Funktion |
| `on` | ein Ereignisblock, mit Auswahl der bekannten Ereignisse |
| `event` | ein eigenes Ereignis |
| `await` | auf ein Ereignis warten |
| `awaittimeout` | warten, aber nicht ewig |
| `display` | eine Anzeigetafel |
| `group` | mehrere Connectoren unter einem Namen |
| `multiblock` | eine Anlage aus mehreren Geräten |
| `move` | ein einzelner Transport |

## Woher sie weiß, was wohin gehört

Aus `data/signatures.json`. Diese Datei wird aus `Signatures.java` im
Mod-Projekt erzeugt, und ein Test dort hält beide gleich: Wer eine Angabe
hinzufügt und die Datei vergisst, bekommt einen roten Test. Damit gibt es die
Regel „hinter `row` kommt ein Text und dann ein Ausdruck" einmal und nicht
zweimal.

Dass die Logik trotzdem doppelt dasteht — einmal in Java für den Editor im
Spiel, einmal in `extension.js` für hier —, lässt sich nicht vermeiden, solange
die Erweiterung ohne Bauschritt auskommen soll. Deshalb liegt daneben
`check.js` mit denselben Fällen wie der Java-Test:

```
node editor/vscode/check.js
```

Keine Abhängigkeiten. Zwei Fassungen derselben Regel laufen auseinander, wenn
niemand nachmisst.

## Was fehlt

**Keine Fehlerprüfung.** Dafür bräuchte es den Übersetzer, und der ist in
Java. Das Terminal im Spiel ist die Stelle, an der Fehler stehen: Was nicht
übersetzt, wird nicht übernommen, und im Reiter **Code** steht, warum.

**Keine Gerätenamen in der Vervollständigung.** Welche Connectoren und
Anzeigen es gibt, weiß nur das laufende Spiel. Im Terminal schlägt der Editor
sie vor und warnt, wenn ein Name in der Welt nicht vorkommt; hier kann er das
nicht.

Beides zusammen wäre ein Sprachserver mit Verbindung zum Spiel. Das ist eine
eigene Entscheidung und keine, die nebenbei fällt.
