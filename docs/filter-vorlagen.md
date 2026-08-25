# Filter-Vorlagen — Entwurf

Stand: 2026-08-25. Umsetzungsplan: `plan-filter-vorlagen.md`.

**Ziel:** Eine Auswahl bekommt einen Namen und steht überall dort, wo heute
eine geschriebene Auswahl steht — so wie eine Gerätegruppe überall dort
steht, wo ein Gerät steht.

```
filter ore_factory {
    tag:c/ores
    item:deepslate_coal_ore
    except item:ancient_debris
}

worker erz_holen {
    from grube
    to storage
    filter ore_factory
}

move 64 ore_factory from brecher to storage
if storage.count(ore_factory) < 500 { … }
```

---

## Was die Sprache heute hat und was fehlt

`except` **gibt es schon**: `tag:c/ores except item:ancient_debris` ist eine
gültige Auswahl (`sprache.md` §4). Die Vorlage muss die Ausnahme also nicht
erfinden. Was fehlt, sind zwei andere Dinge:

- **Ein Name für eine Auswahl.** Heute steht dieselbe lange Auswahl in fünf
  Workern, und wer eine Sorte ergänzt, muss sie fünfmal finden.
- **Die Vereinigung mehrerer Auswahlen.** `tag:c/ores` *und* dazu noch ein
  einzelnes Erz aus einer anderen Mod lässt sich heute nicht schreiben. Ein
  Worker nimmt genau eine `filter`-Zeile; eine zweite würde stillschweigend
  übergangen.

Die Vorlage bringt beides in einer Form, und die Ausnahme fällt ihr in den
Schoß.

## Die Form

```
filter <name> {
    <auswahl>            legt dazu
    except <auswahl>     nimmt weg
}
```

Jede Zeile ohne `except` legt dazu, jede mit nimmt weg. **Erst alles
zusammen, dann die Ausnahmen** — die Reihenfolge der Zeilen ist damit
gleichgültig, und niemand muss beim Lesen einen Zwischenstand mitführen.

Nackte Zeilen statt `members` wie bei `group`: Eine Gruppe hat zwei Arten von
Zeilen (`members` und `strategy`) und braucht deshalb ein Wort zur
Unterscheidung. Eine Vorlage hat nur eine Art. Ein zweites Wort wäre
Zeremonie ohne Aufgabe.

Jede einzelne Zeile darf für sich schon eine vollständige Auswahl sein, also
auch `tag:c/ores except item:ancient_debris`. Das ist keine Sonderregel,
sondern die bestehende Auswahl-Grammatik.

## Wo eine Vorlage steht

Überall, wo heute eine Auswahl steht:

| Stelle | Beispiel |
|---|---|
| Worker | `filter ore_factory` |
| `move` | `move ore_factory from brecher to storage` |
| `move` mit Menge | `move 64 ore_factory from brecher to storage` |
| Bestand lesen | `storage.count(ore_factory)` |
| Einlegen | `crusher_1.insert(ore_factory)` |
| Bedingung | `if storage.has(ore_factory) { … }` |

**Eine Menge davor heißt dasselbe wie heute vor einem Tag:** `64 ore_factory`
sind 64 insgesamt, nicht 64 je Art. Bei `maintain` bleibt es je Art — das ist
schon so und ist dort auch richtig, weil ein Vorrat je Sorte gehalten wird.

## Gegenstände oder Flüssigkeiten, nie beides

Woran es erkannt wird: an den Einträgen. Eine Vorlage aus `item:` und `tag:`
ist eine Gegenstandsvorlage, eine aus `fluid:` eine für Flüssigkeiten.
Gemischt ist ein Fehler beim Übernehmen und keine stillschweigende Auswahl —
`move` schickt Wasser und Steine über verschiedene Wege, und eine Vorlage,
die beides enthielte, wäre an jeder Verwendungsstelle etwas anderes.

Ein `tag:` kann grundsätzlich beides treffen. Er zählt als Gegenstand,
solange Flüssigkeits-Tags nicht aufgelöst werden (`offene-punkte.md` 1.3).

## Eine Vorlage in einer Vorlage: nein

Entschieden am 2026-08-25. Eine Vorlage, die eine andere einschließt, wäre
mächtiger, bringt aber Ringschlüsse mit: Wer sich selbst einschließt, muss
beim Übernehmen abgewiesen werden, und die Meldung dafür ist schwerer zu
schreiben als das Feature wert ist. Wer wirklich zwei Vorlagen zusammenlegen
will, schreibt die gemeinsamen Zeilen in beide.

Abgewiesen wird das mit einer Meldung, die den Grund nennt — nicht mit
„unbekannte Auswahl".

## Was aufgelöst wird, und wann

Aufgelöst wird über dieselbe Stelle wie heute jede Auswahl
(`ItemSelection.resolve`), also **aus dem Syntaxbaum heraus und mit
Zwischenspeicher**. Das ist wichtig, weil `except` nur nach dem Auflösen
möglich ist: Aus einem Tag etwas herauszunehmen heißt, die Menge erst zu
kennen.

Zur Laufzeit wird ein Vorlagenname zu einer **schon aufgelösten Auswahl**
(`Value.Selection` beziehungsweise `Value.FluidSelection`). Beide Werttypen
gibt es, und jede Stelle, die Auswahlen verarbeitet, kennt sie bereits. Neu
sind nur die Deklaration, das Nachschlagen des Namens und die Auflösung im
Worker.

**Verworfen: ein eigener Werttyp**, der den Namen bis zur Auflösung mitträgt.
Er brächte bessere Meldungen — „ore_factory trifft nichts" statt „0 Arten" —,
aber das Wertemodell ist versiegelt: Jede Stelle, die Werte behandelt, müsste
ihn kennen. Denselben Vorteil bringt eine Auflösung, die durch eine Stelle
geht, die den Namen kennt und den Fehler selbst wirft.

**Verworfen: Textersetzung beim Übernehmen.** Der Übersetzer könnte jeden
Vorlagennamen durch die aufgezählten Selektoren ersetzen. Dann zeigten
Fehlermeldungen aber Code, den niemand geschrieben hat — und die Vereinigung
mehrerer Auswahlen ließe sich so gar nicht ausdrücken, weil die Sprache dafür
keine Schreibweise hat.

## Namen

Ein Vorlagenname teilt sich den Namensraum mit Workern, Gruppen,
Multiblocks, Funktionen und globalen Werten. Doppelte Namen sind ein Fehler,
wie bisher.

**Geräte sind der Sonderfall:** Ihre Namen stehen nicht im Programm, sondern
kommen von der Beschriftungspistole. Ein Programm kann also eine Vorlage
`brecher_1` erklären, während im Netz ein Gerät so heißt. Regel: **Die
Vorlage geht vor**, und `NetworkCheck` meldet als Warnung, dass sie ein Gerät
gleichen Namens verdeckt. Die andere Reihenfolge wäre schlechter — dann
hinge die Bedeutung eines Programms daran, wie jemand später einen Connector
benennt.

## Nebenbefund: `except` wirkt heute nur im Worker

Beim Nachsehen für diesen Entwurf gefunden: Der Interpreter wertet
`Expr.Except` als `evaluate(except.base())` aus
(`runtime/Interpreter.java:659`) — die Ausschlüsse fallen dabei weg. Im
Worker stimmt es, dort löst `ItemSelection.resolve` den Baum auf; in `move`
und `count` steht die Ausnahme in `sprache.md`, wirkt aber nicht.

Das gehört in dieselbe Arbeit: Die Vorlage geht durch genau diese Stelle, und
eine Auflösung, die den Ausschluss kennt, repariert beides auf einmal. Erst
mit einem Test, der die Lücke zeigt.
