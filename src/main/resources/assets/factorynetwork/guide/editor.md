---
navigation:
  title: Der Editor
  position: 35
---

# Der Editor

Ein Editor im Spiel hat keine Menüleiste, in der man nachsieht. Deshalb hier
einmal alles, was er kann — und **F1 im großen Fenster** zeigt dieselbe Liste
noch einmal, während du davorsitzt.

## Zwei Fenster für denselben Code

Im Terminal steht der Reiter **Code**: gut für eine schnelle Änderung, für
einen Blick auf einen Namen, für „übernehmen und weiter". Das Zeichen ganz
rechts in der Dateizeile öffnet das **große Fenster** — dasselbe Programm,
aber mit Dateibaum links und Platz zum Arbeiten.

Beide bearbeiten denselben Entwurf. Was du im einen tippst, steht im anderen.

## Der Entwurf und das, was läuft

**Ein Tippfehler hält die Fabrik nicht an.** Was im Editor steht, ist ein
Entwurf; er darf kaputt sein. Erst **Strg+Eingabe** übernimmt ihn. Bis dahin
läuft weiter, was zuletzt übernommen wurde.

Abgelehnt wird ein Entwurf aus genau drei Gründen, und alle drei stehen
danach als Meldung da: Er hat **Fehler**; im Netz steht **kein
Serverschrank**; oder das Programm ist **größer, als die Datenträger fassen**.
Warnungen halten nichts auf — sie sind Hinweise, keine Verbote.

Der Entwurf geht eine Sekunde nach dem letzten Anschlag zum Server und
überlebt damit auch, wenn du das Fenster schließt oder der Server neu startet.
**Strg+S** schickt ihn sofort — für den Moment, in dem man vom Rechner
weggeht und es genau wissen will.

## Die Griffe

| | |
|---|---|
| **Strg+Leer** | Vorschläge zeigen |
| **Tab** | Vorschlag übernehmen |
| **Strg+Eingabe** | Übernehmen |
| **Strg+Klick** | Zur Erklärung springen |
| **Strg+F** | Suchen |
| **Strg+H** | Suchen und ersetzen · Alt+Eingabe ersetzt alle |
| **Strg+Z / Strg+Umschalt+Z** | Rückgängig und wieder vorwärts |
| **Strg+D** | Zeile verdoppeln |
| **Tab / Umschalt+Tab** | Ein- und ausrücken |
| **Strg+Links / Rechts** | Wortweise springen |
| **Strg+Pos1 / Ende** | An den Anfang und ans Ende |
| **Umschalt+Rollen** | Waagerecht schieben |
| **F2** | Datei umbenennen |
| **F4** | Bearbeitung anfragen |
| **Rechtsklick** | Menü im Dateibaum |
| **F1** | Diese Liste im Spiel zeigen |

## Was die Vorschläge wissen

Sie richten sich nach der Stelle, an der der Cursor steht, nicht nach dem
Block. Hinter `filter ` stehen Gegenstände, hinter `to ` die Connectoren,
hinter `strategy ` die Verteilungen, hinter `on ` die Ereignisse.

Vier Dinge, die man leicht übersieht:

- **Nach `display ` stehen die Wände, die wirklich in der Welt hängen.** Ein
  vertippter Name gibt kein Programm, das nicht übersetzt, sondern eine Tafel,
  die schwarz bleibt — der Fehler, den man am längsten sucht.
- **Nach einem Punkt hinter einem Connector** steht, was ein Gerät kann:
  `online`, `name`, `redstone()`, `count()`, `insert()`, `items()`.
- **Nach `storage.items().`** stehen die Listenoperationen `count`, `first`,
  `sum`, `where`, `sort`.
- **Namen aus allen Dateien**, nicht nur aus der offenen. Alle Dateien eines
  Projekts teilen einen Namensraum — gerade die Namen aus der Datei, die du
  nicht vor dir hast, braucht man am ehesten.

## Was der Editor über eine Maschine weiß

Zeig mit der Maus auf einen Connectornamen im Code. Das Terminal sagt dir
dann, was dort wirklich hängt:

- **Welche Maschine** dahintersteht.
- **An welchen Seiten** sie etwas annimmt, und ob Gegenstände, Flüssigkeit
  oder Strom.
- **Wie viele Fächer und Behälter** sie hat.
- **Was gerade darin liegt** — auf Anfrage, damit nicht jede Mausbewegung im
  Netz nachfragt.

Und die drei Sätze, die einen Nachmittag sparen: *„Zeigt ins Leere — dort
steht keine Maschine"*, *„Nicht geladen — über die Maschine ist nichts
bekannt"* und der Hinweis, den Connector auf die Seite zu drehen, an der die
Maschine steht.

## Strg+Klick

Auf einen Namen im Code:

- Ist es etwas, das das Programm selbst erklärt — eine Funktion, ein Worker,
  eine Gruppe —, springt der Editor **zur Erklärung**, auch in eine andere
  Datei.
- Ist es ein Connector, setzt er eine **Marke in der Welt**. Sie steht eine
  halbe Minute und ist durch Wände zu sehen; du musst also nicht erst die
  Kammer finden, in der das Gerät hängt.

## Wenn jemand anders die Datei hält

Im Mehrspielerbetrieb gehört eine Datei dem, der sie geöffnet hat — sonst
überschreibt der eine den anderen, ohne es zu merken. Der Dateibaum zeigt das
an. **F4** fragt beim anderen nach; er bekommt die Anfrage und kann die Datei
freigeben.

## Der Weg nach draußen

Neben der Welt liegt ein Ordner `factorynetwork`, und darin einer je
Controller. Was dort in `.mf`-Dateien steht, ist dasselbe Programm — mit einem
richtigen Editor bearbeitbar, versionierbar, kopierbar. Änderungen von außen
liest das Netz von selbst wieder ein.

Für **VS Code** liegt eine Erweiterung bei: Syntaxfarben, dieselben Vorschläge
wie im Spiel und die Formzeile zu jeder Angabe. Fehler meldet sie nicht — dafür
bräuchte sie den Übersetzer, und der läuft im Spiel. Das Terminal zeigt sie.
