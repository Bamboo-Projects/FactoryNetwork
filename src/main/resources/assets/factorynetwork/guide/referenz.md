---
navigation:
  title: Referenz
  position: 90
---

# Referenz

Alles, was die Sprache kennt, auf einer Seite. Zum Nachschlagen, nicht
zum Lesen — wie etwas gemeint ist, steht bei *Programmieren*.

Diese Seite wird aus dem Quelltext erzeugt. Was hier steht, kann die
Mod auch.

## Oberste Ebene

Was ganz links in einer Datei stehen darf.

| Form | Bedeutung |
|---|---|
| `global name = expr` | Ein Wert, den alle Dateien sehen. |
| `const name = expr` | Ein Wert, der sich nie ändert. |

## In einem `worker`

Ein Worker ist eine dauerhafte Zusage: Solange er dasteht, hält das Netz
sie ein.

| Form | Bedeutung |
|---|---|
| `from target` | Woher die Gegenstände kommen. |
| `to target` | Wohin sie gehen. |
| `filter selection` | Was bewegt wird. Ohne Angabe alles. |
| `maintain int` | So viele sollen am Ziel liegen bleiben. |
| `rate int per duration` | Höchstens so viele in dieser Zeit. |
| `when expr` | Nur, solange der Ausdruck wahr ist. |
| `priority int` | Wer bei knappem Nachschub zuerst drankommt. |
| `strategy strategy` | Wie auf mehrere Ziele verteilt wird. |
| `overflow to target` | Wohin, wenn das Ziel voll ist. |

## In einer `group`

Eine Gruppe fasst Geräte zusammen. Wer dazugehört, entscheidet das Netz
und nicht das Programm.

| Form | Bedeutung |
|---|---|
| `members members` | Die Connectoren der Gruppe, mit Komma getrennt. |
| `strategy strategy` | Wie auf sie verteilt wird. |

## In einem `filter`

Eine Auswahl mit Namen, überall verwendbar, wo eine Auswahl steht.

| Form | Bedeutung |
|---|---|
| `except selection` | Nimmt wieder heraus, was die Zeilen darüber einschließen. |

## Anweisungen

Was in einer Funktion oder einem `on`-Block steht.

| Form | Bedeutung |
|---|---|
| `let name = expr` | Ein neuer Wert mit Namen. |
| `if expr` | Nur, wenn der Ausdruck wahr ist. |
| `while expr` | Solange der Ausdruck wahr ist. |
| `for name in expr` | Einmal je Element. |
| `move menge [from quelle] to ziel` | Bewegt Gegenstände von einem Gerät zum anderen. |
| `emit event` | Löst ein Ereignis aus. |
| `sleep duration` | Wartet eine Zeit lang. |
| `return expr` | Gibt einen Wert zurück und beendet die Funktion. |
| `await event` | Wartet auf ein Ereignis. |

## Auf einer Anzeige

Je Zeile eine Angabe. Gezeichnet wird von oben nach unten.

| Form | Bedeutung |
|---|---|
| `title string` | Die Überschrift der Wand. |
| `row string expr` | Eine Zeile mit Beschriftung und Wert. |
| `text expr` | Eine Zeile ohne Beschriftung. |
| `progress string expr` | Ein Balken von null bis eins. |
| `indicator string expr` | Eine Lampe: an, wenn der Ausdruck wahr ist. |
| `list string expr` | Eine Aufzählung aus einem Ausdruck. |
| `button string function` | Ein Knopf, der eine Funktion aufruft. |
| `scale int` | Wie groß die Schrift ist; 1 ist normal. |

## Freie Funktionen

Ohne Punkt davor, überall aufrufbar.

| Form | Bedeutung |
|---|---|
| `log(text)` | Schreibt ins Protokoll, als info. |
| `info(text)` | Was gut lief. |
| `warn(text)` | Etwas stimmt nicht, die Fabrik läuft weiter. |
| `error(text)` | Etwas ist stehen geblieben. |
| `debug(text)` | Zwischenstände beim Suchen. Im Terminal erst auf Wunsch sichtbar. |
| `min(zahl, zahl) zahl` | Die kleinere. Auch mit mehr als zwei. |
| `max(zahl, zahl) zahl` | Die größere. Auch mit mehr als zwei. |
| `abs(zahl) zahl` | Ohne Vorzeichen. |
| `round(zahl) int` | Auf die nächste ganze Zahl. |
| `floor(zahl) int` | Abgerundet. |
| `ceil(zahl) int` | Aufgerundet. |
| `random(von, bis) int` | Eine Zahl dazwischen, beide Enden eingeschlossen. |
| `craft(auswahl) int` | Bestellt eine Fertigung und liefert die Kennung des Auftrags. Null heißt: kein Rezept. |

## Was ein Gerät hat

Hinter dem Namen eines Connectors: `brecher_1.count(item:iron_ore)`.

| Form | Bedeutung |
|---|---|
| `bool` | Ob das Gerät gerade im Netz hängt. |
| `string` | Der Name, den die Beschriftungspistole vergeben hat. |
| `redstone() int, redstone(int)` | Die Redstone-Stärke, 0 bis 15. Mit Zahl gesetzt, ohne gelesen. |
| `count(selection) int` | Wie viel von einer Art in diesem Gerät liegt. Ohne Auswahl alles. |
| `insert(selection) int` | Legt aus dem Speicher etwas hinein. Gibt zurück, wie viel ankam — weniger ist normal. |
| `items() list` | Was gerade im Gerät liegt. Leere Fächer fallen weg. |
| `slots(nummer) list` | Bestimmte Fächer — eine Nummer oder ein Bereich wie 1..5. Auch als Quelle oder Ziel eines move. |
| `energy() int` | Wie viel Strom in der Maschine steht, in FE. Ohne Speicher null. |

## Was an einer Liste steht

`where` und `sort` werten ihren Ausdruck je Eintrag aus, mit `it` als
diesem Eintrag.

| Form | Bedeutung |
|---|---|
| `count() int` | Wie viele Einträge. |
| `first() any` | Der erste Eintrag, oder nichts. |
| `sum() int` | Alle Zahlen aufaddiert. |
| `where(expr) list` | Behält, wofür der Ausdruck wahr ist. it ist der Eintrag. |
| `sort(expr) list` | Ordnet nach dem Ausdruck. it ist der Eintrag. |
| `plus(any) list` | Dieselbe Liste mit einem mehr. |
| `without(any) list` | Dieselbe Liste ohne jedes Vorkommen davon. |
| `rest() list` | Alles außer dem ersten Eintrag. |

## Was an einer Gruppe steht

Hinter dem Namen einer Gruppe.

| Form | Bedeutung |
|---|---|
| `members() list` | Die Geräte der Gruppe, in der Reihenfolge ihrer Verteilung. |
| `send(selection) int` | Schickt aus dem Speicher an die Gruppe. Wohin, entscheidet ihre strategy. |

## Was an einem Posten steht

Ein Posten ist ein Eintrag einer Bestandsliste — das, was `it` in einem
`where` gerade ist.

| Form | Bedeutung |
|---|---|
| `amount int` | Die Menge dieses Postens. |
| `item item` | Die Art dieses Postens — nur, wenn er genau eine meint. |
| `fluid fluid` | Die Sorte dieses Postens, bei einer Flüssigkeitsliste. |

## Verteilstrategien

Wohin ein Worker liefert, wenn das Ziel eine Gruppe ist.

- `round_robin`
- `first_available`
- `least_filled`
- `random`
- `priority`
