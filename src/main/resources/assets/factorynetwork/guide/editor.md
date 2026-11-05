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
- Ist es ein Wort der Sprache — `worker`, `filter`, `multiblock`, `on`,
  `display` und so weiter —, öffnet sich **die Seite im Handbuch**, die es
  erklärt, mit ihren 3D-Szenen. Das geht im Editor im Terminal wie im großen
  Editor.

## Werte unter der Maus

Bleibt die Maus auf einem Ausdruck stehen, zeigt der Editor seinen
**aktuellen Wert**: über `zaehler` die Zahl, die gerade drinsteht, über
`kiste.count(item:iron_ingot)` wie viel jetzt in der Kiste liegt. Der Wert
kommt vom Controller und wird jede Sekunde neu gefragt, solange die Maus dort
bleibt.

**Nur Fragen, nie Taten.** Ausgewertet wird nur, was bloß liest: Werte,
Zählungen, Rechnungen, `redstone()` ohne Zahl. Was etwas ändern würde —
`redstone(15)`, `insert`, `click`, `craft`, eine Funktion des Programms —
zeigt statt eines Werts einen Hinweis.

## Haltepunkte

Ein Klick links neben die Zeilennummer setzt einen **Haltepunkt**, ein roter
Punkt. Kommt ein Ablauf — ein `on`-Block oder eine Funktion, die wartet — an
diese Zeile, bleibt er davor stehen. Im Reiter **Netz** steht er dann unter
*Abläufe* als angehalten, mit „weiter“ und „abbrechen“. Werte unter der Maus
zeigen währenddessen, wie es gerade steht.

Haltepunkte gehören zur Sitzung: Schließt du das Terminal, sind sie weg, und
wer an einem stand, läuft weiter. Nach einem Neustart läuft ohnehin alles.

## Ordner im Projekt

Ein Dateiname darf einen Ordner nennen: `erz/brecher.mf`, auch zwei Ebenen
tief. Anlegen und Umbenennen nehmen den Pfad einfach entgegen — es gibt keinen
eigenen Griff dafür, ein Schrägstrich im Namen genügt.

**Ein Ordner ist Ordnung für dich, keine Grenze für die Sprache.** Alle
Dateien eines Projekts teilen weiterhin einen Namensraum: Ein `fn` in
`erz/brecher.mf` ruft eines in `main.mf`, ohne dass irgendwo etwas eingebunden
werden muss. Zwei gleiche Namen in zwei Ordnern sind deshalb weiterhin einer
zu viel.

Die Liste links bleibt flach und alphabetisch. Der Schrägstrich sortiert vor
Buchstaben und Ziffern, also stehen die Dateien eines Ordners von selbst
beieinander — ein Klappbaum bräuchte einen Griff mehr für dieselbe Auskunft.
Wird ein Pfad zu lang für die Spalte, wird er vorn gekürzt: `…/schmelzen.mf`.
Dort steht der Name, und darauf kommt es an.

Erlaubt sind Kleinbuchstaben, Ziffern und Unterstriche, je Abschnitt bis
zweiunddreißig Zeichen und im Ganzen bis sechsundneunzig. Ein Punkt gehört
nicht dazu — damit gibt es kein `..`, und aus dem Ordner deiner Welt führt
kein Dateiname heraus.

## Programme mitnehmen

Eine **Programmdiskette** trägt alle Dateien eines Netzes:

- **Rechtsklick** mit der Diskette auf einen Block des Netzes, etwa den
  Controller oder das Terminal, schreibt das laufende Programm darauf. Was
  vorher draufstand, ist dann weg.
- **Schleichen und Rechtsklick** liest sie in den **Entwurf** des Netzes,
  nicht gleich ins laufende Programm. Du siehst es dir im Editor an und
  startest es mit Übernehmen. Ein fremdes Programm läuft so nie ungesehen los.

Auf der Diskette steht, wie viele Dateien und Zeilen sie trägt, und die ersten
Dateinamen. Einen Namen gibst du ihr am Amboss.

## Programme teilen

Mit der Diskette in der Hand teilst du ein Programm mit allen, die
FactoryNetwork spielen, ganz ohne Konto. **Rechtsklick in die Luft** mit der
Diskette (oder `/fnshare`) öffnet das Fenster dafür: Unter **Entdecken**
suchst du in Titeln und Beschreibungen, sortierst nach neu oder beliebt und
holst ein Programm mit einem Klick auf die Diskette. Unter **Hochladen** gibst
du Titel und Beschreibung ein. Dasselbe geht auch im Chat:

- `/fnshare upload Erzsortierer` — lädt die Diskette hoch und gibt einen Code
  wie `FN-7K2Q9M` zurück. Ein Klick darauf kopiert ihn.
- `/fnshare get FN-7K2Q9M` — schreibt das Programm auf die Diskette in deiner
  Hand. In ein Netz kommt es wie immer erst über den Entwurf.
- `/fnshare list` zeigt die neuesten, `/fnshare list popular` die
  beliebtesten, `/fnshare list new erz` sucht im Titel. Ein Klick auf einen
  Eintrag holt ihn.
- `/fnshare report FN-7K2Q9M` meldet ein Programm. Nach mehreren Meldungen
  verschwindet es aus der Liste.

**Neue Fassungen unter demselben Code.** Beim ersten Hochladen merkt sich
die Diskette einen geheimen Schlüssel. Lädst du dieselbe Diskette wieder hoch,
ersetzt das die Fassung unter dem alten Code; auf der Diskette steht „Geteilt
als …“. Wer das Programm nur geholt hat, hat den Schlüssel nicht und kann
nichts ändern. `/fnshare upload new <Titel>` gibt bewusst einen neuen Code.

Wer nicht an einer Diskette hängen will, knüpft den Code an sein
Minecraft-Konto: `/fnshare upload bind <Titel>`. Der Dienst lässt sich das von
Mojang bestätigen, so wie ein Server beim Einloggen; dein Passwort sieht er
nie. Danach geht `/fnshare update <Code> <Titel>` von jeder Diskette und jedem
Rechner aus. Gespeichert wird dann deine UUID, sonst nichts — und nur, wenn du
`bind` schreibst.

Gesendet werden nur Programm und Titel, nie dein Name oder der Server.

Ein Server kann den Share abschalten: `allowProgramShare = false` im Abschnitt
`[share]` seiner `factorynetwork-server.toml`. Dann sind Fenster und
`/fnshare` gesperrt, und der Server schreibt keine geholten Programme auf
Disketten.

## Wenn jemand anders die Datei hält

Im Mehrspielerbetrieb gehört eine Datei dem, der sie geöffnet hat — sonst
überschreibt der eine den anderen, ohne es zu merken. Der Dateibaum zeigt das
an. **F4** fragt beim anderen nach; er bekommt die Anfrage und kann die Datei
freigeben.

## Der Weg nach draußen

Neben der Welt liegt ein Ordner `factorynetwork`, und darin einer je
Controller. Was dort in `.mf`-Dateien steht, ist dasselbe Programm — mit einem
richtigen Editor bearbeitbar, versionierbar, kopierbar. Änderungen von außen
liest das Netz von selbst wieder ein. Unterordner sind dort echte Ordner, und
wer einen von außen anlegt, hat ihn beim nächsten Blick auch im Spiel.

Für **VS Code** liegt eine Erweiterung bei: Syntaxfarben, dieselben Vorschläge
wie im Spiel und die Formzeile zu jeder Angabe. Fehler meldet sie nicht — dafür
bräuchte sie den Übersetzer, und der läuft im Spiel. Das Terminal zeigt sie.
