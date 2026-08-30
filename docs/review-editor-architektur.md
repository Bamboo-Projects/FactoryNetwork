# Der Editor: Architektur-Review

**Auftrag vom 30.08.:** Unvoreingenommen prüfen, ob der eingeschlagene Weg für
die Ingame-Entwicklungsumgebung der richtige ist — ausdrücklich mit der
Möglichkeit, dass die Antwort *nein* lautet.

Dieses Dokument misst und entscheidet. Es baut nichts.

**Gegengelesen** von einem zweiten Modell (GPT-5 über Codex) mit dem
ausdrücklichen Auftrag, die These anzugreifen. Wo es recht behalten hat, steht
das hier drin.

---

## 1. Der Bestand, gezählt

| Was | Zeilen | Dateien |
|---|---|---|
| Sprache (`lang/`) — Lexer, Parser, Prüfungen, Signaturen, Faltung | 6 313 | 21 Testklassen dafür |
| Editor-Oberfläche (`client/screen/`) | 8 305 | davon `CodeEditor` 2 319 |
| VS-Code-Erweiterung (`editor/vscode/`) | 2 577 | mit fertigem `.vsix` |
| Mod insgesamt | 68 006 | 297 Klassen, 362 GameTests grün |

**Was der Ingame-Editor heute kann:** Syntaxhervorhebung, Vervollständigung,
Signaturhilfe, Diagnostics, Suchen und Ersetzen, Undo/Redo,
Klammer-Zuordnung, Gehe-zu-Deklaration, Umbenennen, Code-Faltung, mehrere
Dateien mit Reitern, Einrückhilfen, Wortnavigation.

**Was fehlt:** Minimap, mehrere Themes, LSP.

Das ist der erste Befund, und er ist unbequem für beide Lager: Der Abstand zu
„VS-Code-artig" ist **kleiner**, als die Wunschliste vermuten lässt — und die
drei fehlenden Punkte sind teurer, als sie klingen (§ 6).

---

## 2. Der Befund, der alles andere sortiert

Es gibt eine Duplikation, und sie ist im Quelltext selbst dokumentiert.
`editor/vscode/check.js` schreibt über sich:

> „Dieselben Fälle wie CompletionsTest im Mod-Projekt: Die Logik steht
> **zweimal da** — einmal in Java für den Editor im Spiel, einmal hier für
> VS Code —, und zwei Fassungen derselben Regel laufen auseinander, wenn
> niemand nachmisst."

Der Gegenleser hat nachgesehen und die Duplikation als **größer** belegt, als
dieser Kommentar zugibt: `extension.js` führt eigene Heuristiken für
Deklarationen, Projektsymbole, Gehe-zu-Deklaration, Umbenennen und
Vervollständigung — 1 076 Zeilen JavaScript, die Java-Logik nachbauen. Der
Gegenlauf `check.js` (731 Zeilen) ist ein Pflaster, keine Lösung: Er misst,
dass die zwei Fassungen noch übereinstimmen, statt die zweite überflüssig zu
machen.

**Das ist die teuerste Stelle der Architektur, und sie ist unabhängig von
jeder Rendering-Frage.** Jeder Editor — selbst gezeichnet, Monaco in CEF,
Monaco im WebView, VS Code — ist nur ein weiterer Abnehmer derselben
Sprachintelligenz. Wer Monaco einführt, ohne diese Grenze zu ziehen, hat
danach **drei** Fassungen statt zwei.

---

## 3. Die vier Wege, je am eigenen Projekt gemessen

### Weg 1 — Selbst gezeichnet (heutiger Stand)

Was dafür spricht, ist nicht das investierte Geld — versunkene Kosten zählen
nicht. Es sind drei Sacheigenschaften:

- **Rendert in die Welt.** Die Mod hat Anzeigetafeln mit klickbaren Flächen
  (`DisplayBlockEntity`, `DisplayRenderer`, per GameTest belegt). Alles, was
  auf einer Blockfläche stehen soll, muss durch Minecrafts eigenen Renderer.
- **Keine fremde Laufzeit.** Kein Download, keine Systemabhängigkeit, kein
  zweiter Prozess, keine Lizenzfrage. In einem Modpack ist das der Unterschied
  zwischen „läuft" und „läuft beim Nutzer nicht, und niemand weiß warum".
- **GUI-Scale, Fullscreen, Screenshots, Fokus** sind gelöst, weil es dieselbe
  GUI-Ebene ist wie jedes andere Minecraft-Fenster.

**Was ehrlich dagegen spricht** — und das ist der Punkt, an dem der
Gegenleser recht hat: Man besitzt damit dauerhaft ein eigenes
Texteingabeprodukt. Textlayout, Cursor, Auswahl, Scrollen, Undo, Eingabe. Und
**IME/CJK-Eingabe wird nie gut**: Minecrafts GLFW-Texteingabe ist dort schwach,
und das lässt sich in dieser Ebene nicht reparieren. Wer Chinesisch, Japanisch
oder Koreanisch tippt, wird im Ingame-Editor immer schlechter bedient als in
jedem Browser.

### Weg 2 — Native WebView (Wry / FerricOxide)

**Für dieses Projekt disqualifiziert**, und zwar aus dem README des Projekts
selbst, nicht aus einer allgemeinen Meinung:

> „on Windows the WebView is created as an HWND child of the Minecraft window
> […] **Other platforms currently fall back to a standalone window.**"

Auf Linux und macOS wäre der „Editor im Spiel" ein **separates Fenster neben
Minecraft**. Damit ist die Sache entschieden: Das ist kein Ingame-Editor mehr,
es ist ein Texteditor, den man nebenher startet — und dafür gibt es VS Code,
das ihr bereits anbindet.

Dazu kommt: Ein Child-Fenster über dem Spielfenster kann prinzipbedingt nichts
auf eine Blockfläche zeichnen, hat keine Z-Ordnung mit Minecraft-Elementen,
ignoriert GUI-Scale und taucht in Screenshots nicht auf.

### Weg 3 — CEF (MCEF)

Der einzige Weg, der Monaco **und** In-World-Rendering kann: CEF rendert
offscreen in eine Textur, die Minecraft selbst zeichnet. MCEF ist der
bewiesene Pfad — 706 727 Downloads, WebDisplays baut darauf.

Hier hat der Gegenleser meine erste These korrigiert, zu Recht: Mein
In-World-Argument schlägt **nur den Wry-Pfad**, nicht CEF.

**Aber:** siehe § 4. Die Versionsfrage entscheidet diesen Punkt, nicht die
Technik.

### Weg 4 — Der Zuschnitt statt der Renderer

Nicht „welcher Editor", sondern „welcher Editor wofür":

- Java wird ein **Sprachdienst** mit einer Grenze (LSP-förmig oder echtes LSP).
- **VS Code ist die IDE** — die Erweiterung existiert, das `.vsix` liegt im
  Repo, der Dateiweg über `.fn-status.json` läuft seit dem 26.08.
- Der **Ingame-Editor ist die Fläche vor Ort**: schnelle Änderung, Fehler
  sehen, Programm übernehmen. Nicht die Vollausstattung.

---

## 4. Die Versionsfrage entscheidet mehr als die Technik

Gemessen am 30.08. über die Modrinth-API und die Repositorien:

| | Minecraft 1.21.1 | Minecraft 26.1.2 |
|---|---|---|
| **MCEF** (CEF, In-World möglich) | ✅ 2.1.6 | ❌ **gibt es nicht** (neueste: 1.21.4, Januar 2025) |
| **FerricOxide** (Wry, nur Fenster) | ❌ zielt auf 26.1.2 / JDK 25 | ✅ aber siehe § 5 |
| **Mekanism** | ✅ 10.7.19.85 (April 2026) | ❌ heute nicht veröffentlicht |
| **AE2, Jade, GuideME, Sophisticated, Curios** | ✅ | ✅ |

**Die beiden Web-Wege schließen sich über die Minecraft-Version gegenseitig
aus.** Wer auf 26.1.2 geht, verliert CEF und bekommt nur den Wry-Pfad, der auf
zwei von drei Plattformen ein separates Fenster ist. Wer auf 1.21.1 bleibt,
behält CEF, kann FerricOxide aber nicht nutzen.

Zu deiner Bemerkung, Mekanism arbeite an 26.1.2: Das mag stimmen, veröffentlicht
ist es nicht — weder auf Modrinth noch im Hersteller-Maven (letzte Datei
`1.21.1-10.7.19.85`, 10. April 2026). „In Arbeit" ist keine Abhängigkeit, auf
die man ein Release setzt. Wenn 26.1.2 das Ziel wird, ist das eine
eigenständige Entscheidung mit eigenem Preis (acht Minecraft-Versionen
Migration, darunter der Umbau des Modellsystems in 1.21.5 und der
GUI-Rendering-Rewrite ab 1.21.6) — und sie sollte **nicht** damit begründet
werden, dass sie eine Editor-Technologie freischaltet, die dort noch weniger
verfügbar ist als hier.

---

## 5. FerricOxide im Einzelnen

**Was es technisch löst** (aus Quelltext und README): eine schmale Java-API
über JNI auf einen Rust-Kern mit `wry`/`tao`, eigener Event-Loop-Thread, damit
der Spielthread nie blockiert, eine JSON-Zweiwegbrücke zwischen Seite und
Java, ein `ferric://item`-Protokoll, das Minecraft-Gegenstände in die Seite
rendert, und ein Ressourcenpaket-Zugriff für HTML. Das ist sauber gedacht und
löst echte Probleme.

**Was dagegen spricht, als Fakten:**

| Befund | Wert |
|---|---|
| Erstellt | 11. August 2026 |
| Letzter Commit | 13. August 2026 — **17 Tage still** |
| Aktive Entwicklung | zwei Tage |
| Sterne / Forks / Issues / Releases | 0 / 0 / 0 / **0** |
| Lizenz | **LGPL-3.0** gegen euer MIT |
| Zielplattform | Minecraft 26.1.2, **JDK 25** (ihr: Java 21) |
| Bauvoraussetzung | Rust-Toolchain und Node.js beim Bauen |
| Nutzervoraussetzung | WebView2 (Windows), **WebKitGTK 4.1 + D-Bus** (Linux) |
| Einbettung | Child-HWND nur unter Windows, sonst eigenes Fenster |

**Als Abhängigkeit: nein.** Ein Fundament, das zwei Tage alt ist, kein Release
hat und seit zweieinhalb Wochen ruht, trägt keine 68 000 Zeilen. Die
LGPL-Frage wäre lösbar (nicht shadowen, als separate Mod laden), aber sie
kommt zu allem anderen hinzu.

**Als Lektüre: ja.** Der Aufbau — dedizierter Event-Loop-Thread, JSON-Brücke,
Protokoll-Handler für Spielressourcen — ist genau richtig gedacht und wäre die
Vorlage, falls ihr je selbst einen WebView-Pfad baut.

Rust und JNI bringen hier übrigens **keinen Eigenwert**. Der Nutzen liegt
allein darin, dass `wry` die drei Plattform-WebViews hinter einer API
versteckt. Dass es sie nur teilweise versteckt, steht in ihrem eigenen README.

---

## 6. Monaco, ehrlich gerechnet

**Was Monaco geschenkt bringt:** Minimap, Themes, Multi-Cursor, Soft-Wrap,
Sticky-Scroll, ausgereiftes Textlayout, IME, Clipboard-Feinheiten,
Barrierefreiheit. Das ist viel, und es sind genau die Punkte, an denen der
selbst gezeichnete Editor nie glänzen wird.

**Was Monaco nicht bringt:** eure Sprache. Syntaxhervorhebung braucht eine
Monarch- oder TextMate-Grammatik (die habt ihr für VS Code schon).
Vervollständigung, Diagnostics, Signaturen, Umbenennen — alles muss aus Java
über eine Brücke kommen. **Monaco kauft keine Intelligenz, es kauft eine
Textfläche.**

**CodeMirror 6** wäre leichter und modularer, kann aber weniger von dem, was
ihr wollt (keine Minimap out of the box, kein LSP-Ökosystem). Wenn schon Web,
dann Monaco.

**Große Dateien** sind für euch kein Argument: Programme sind Manifold-Dateien
neben der Welt, keine 10-MB-Logs. Monaco und der eigene Editor kommen beide
damit klar.

**Der Aufwand, bei Weg 1 zu bleiben und die Wunschliste zu erfüllen** — die
Schätzung des Gegenlesers, die ich für realistisch halte:

| Aufgabe | Personentage |
|---|---|
| Themes nur im Editor | 3–5 |
| Themes über die ganze Oberfläche (Dateibaum, Tooltips, Reiter, Statusleiste) | 6–10 |
| Minimap, brauchbar | 8–12 |
| Minimap, performant bei großen Dateien | 12–18 |
| Sprachdienst-Grenze in Java (ohne echtes LSP) | 8–14 |
| Echter LSP-Server | 20–35 |
| **Gesamt** | **30–55 PT** |

Das ist kein Wochenende. Wer das für „ein paar Abende" hält, plant falsch.

---

## 7. Was ein Wechsel kosten würde

**Bei C oder D (Monaco in CEF):**

- Verloren: ~8 300 Zeilen Oberfläche, `CodeEditorTest`, `CompletionsTest`
- Überlebt: die 6 313 Zeilen Sprache — **immer**, in jedem Szenario
- Neu zu bauen: CEF-Einbindung, Brücke Java↔JS, Monaco-Sprachanbindung,
  Fokus- und Eingabeweiterleitung, GUI-Scale-Abbildung, mehrere gleichzeitige
  Instanzen, Verteilung der Laufzeit (Fixed-Version-Runtime wäre über 250 MB;
  MCEF lädt stattdessen nach, was eigene Probleme im Modpack macht)
- **Und die Bindung an 1.21.1**, weil MCEF für 26.x nicht existiert

Versunkene Kosten sind dabei irrelevant. Was zählt, ist der Vorwärtspreis:
30–55 PT Nachrüsten gegen einen Neubau plus dauerhafte Laufzeitabhängigkeit.

---

## 8. Urteil

### **B — Architektur leicht ändern.** Rendering und Editor-Technik bleiben; die Sprachintelligenz bekommt eine Grenze.

**Die drei Schritte, in dieser Reihenfolge:**

1. **Den Sprachdienst in Java formalisieren** (8–14 PT). Eine Schnittstelle,
   die Diagnostics, Vervollständigung, Signaturen, Deklarationen und
   Umbenennen liefert. Drei Abnehmer: Ingame-Editor, VS-Code-Erweiterung,
   Serverbrücke aus `editorbruecke.md`. **Damit stirbt die Duplikation, die
   heute per Testlauf zusammengehalten wird.** Das ist die einzige Änderung,
   die sich in *jedem* Zukunftsszenario auszahlt — auch wenn ihr morgen zu
   Monaco wechselt.
2. **Themes** (3–5 PT im Editor). Billig, sichtbar, und es zwingt die
   verstreuten Hexfarben in eine Palette — dieselbe Aufräumarbeit, die heute
   schon bei den Oberflächenfarben nötig war.
3. **Minimap** (8–12 PT) — aber erst, wenn 1 und 2 stehen, und nur wenn sie
   dir nach dem Ausprobieren wirklich fehlt.

**Was ich nicht tun würde:** FerricOxide als Abhängigkeit nehmen. Auf 26.1.2
wechseln, um eine Editor-Technologie freizuschalten. Den Ingame-Editor
wegwerfen.

### Die Bedingung, unter der dieses Urteil gilt

Der Gegenleser hat den Satz geliefert, der die Sache ehrlich macht:

> „Option 1 ist nur dann vernünftig, wenn du bewusst sagst: **Der Ingame-Editor
> bleibt funktional, nicht VS-Code-paritätisch.**"

Wenn „VS-Code-artig im Spiel" wörtlich gemeint ist — Minimap, Multi-Cursor,
Themes, Erweiterungen, das ganze Gefühl —, dann ist B die falsche Antwort und
C die richtige, mit allen Kosten aus § 7.

**Meine Einschätzung, ohne Gefälligkeit:** Ihr braucht die Parität nicht,
weil ihr sie schon habt — sie heißt VS Code und liegt als `.vsix` im Repo. Der
Ingame-Editor konkurriert nicht mit ihr; er ist die Fläche für das, was man
vor der Maschine stehend tut. Diese Arbeitsteilung ist stärker als ein
Monaco-Klon, der auf zwei Plattformen ein Fremdkörper wäre.

### Das Kipp-Kriterium

Dieses Urteil fällt anders aus, sobald **Web-Inhalte selbst zum Produktziel**
werden: ein Browser im Spiel, beliebige Dashboards, fremde Web-Oberflächen auf
Anzeigetafeln. Dann ist CEF der einzige bewiesene Weg, und dann lohnt sich der
Preis — aber dann bindet ihr euch auch an 1.21.1, solange es MCEF für 26.x
nicht gibt.

**Was ich dafür anbieten kann, statt zu schätzen:** einen Spike von einem Tag.
MCEF hinter einem Schalter, eine leere Monaco-Seite, gemessen werden
Speicher, Startzeit und Bilder je Sekunde mit und ohne. Dann steht diese
Entscheidung auf Zahlen statt auf Argumenten — auch auf meinen.

---

## 9. Was in dieser Review nicht gemessen wurde

Ehrlichkeitshalber: RAM, CPU, GPU-Kosten und Startzeit von CEF und WebView
**habe ich nicht gemessen**. Das geht in dieser Sitzung nicht, und Zahlen aus
zweiter Hand wären hier wertlos — sie hängen an Seiteninhalt,
Prozessarchitektur und Plattform. Alles, was oben steht, ist entweder aus
eurem Quelltext gezählt, aus einer API abgefragt oder aus der Primärquelle des
jeweiligen Projekts zitiert. Der Spike aus § 8 wäre der Weg, die fehlenden
Zahlen zu bekommen.
