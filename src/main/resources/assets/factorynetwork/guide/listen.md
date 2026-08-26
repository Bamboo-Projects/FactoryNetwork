---
navigation:
  title: Listen
  position: 47
---

# Listen

Eine Liste ist mehreres nacheinander: der Bestand deines Netzes, der Inhalt
einer Maschine, oder etwas, das du selbst hinschreibst.

## Woher Listen kommen

```
storage.items()          alles, was im Netz liegt
brecher.items()          alles, was in einer Maschine liegt
ofen.slots(0..2)         bestimmte Fächer einer Maschine
["eisen", "gold"]        eine Liste, die du selbst schreibst
```

Die ersten drei geben **Posten** zurück — jeder mit einer Art und einer Menge.

## Was du damit machen kannst

```
storage.items().count()                       wie viele Posten
storage.items().first()                       der erste, oder nichts
storage.items().sum()                         alle Mengen zusammen
storage.items().where(it.amount > 64)         nur die großen
storage.items().sort(it.amount)               der kleinste zuerst
```

`it` ist dabei der Posten, den die Liste gerade betrachtet. An ihm stehen:

| | |
|---|---|
| `it.amount` | die Menge |
| `it.item` | die Art — nur, wenn der Posten genau eine meint |
| `it.fluid` | dasselbe bei Flüssigkeiten |
| `it.chemical` | dasselbe bei Chemikalien (braucht Mekanism) |

Die Aufrufe lassen sich aneinanderhängen. Was in der Kiste am meisten stört:

```
fn aufraeumen() {
    let groesster = storage.items().sort(it.amount).first()
    log("am meisten da: " + groesster.item)
}
```

## Eine Liste hinschreiben

```
[a, b]       eine Liste mit zwei Einträgen
[]           eine leere
```

Über mehrere Zeilen geht auch — zwischen eckigen Klammern trennt kein
Zeilenumbruch:

```
const erzsorten = [
    item:iron_ore,
    item:gold_ore,
    item:copper_ore
]
```

Und darüber laufen wie über alles andere:

```
fn holen() {
    for sorte in erzsorten {
        move 64 sorte from grube to storage
    }
}
```

## Ändern gibt es nicht — es wird ersetzt

```
liste.plus(x)        dieselbe Liste mit einem mehr
liste.without(x)     dieselbe Liste ohne jedes x
liste.rest()         alles außer dem ersten
```

**Alle drei liefern eine neue Liste und lassen die alte in Ruhe.** Es gibt
kein `add`. Wer etwas anhängen will, weist zu:

```
warteschlange = warteschlange.plus("eisen")
```

Das ist ein Wort mehr zu tippen, und es hat zwei Gründe. Ein `const` schützt
davor, dass etwas überschrieben wird — und wenn Anhängen keine Zuweisung wäre,
liefe es an diesem Schutz vorbei, im Mehrspielerbetrieb genauso. Und beim
Serverneustart wird jeder Wert geschrieben und zurückgelesen; zwei Namen für
dieselbe Liste würden dabei zu zwei Listen. Solange nichts ändert, fällt das
niemandem auf.

**Einen Zugriff über die Nummer gibt es nicht.** `liste[2]` kennt die Sprache
nicht: Eine Liste, in die man an beliebiger Stelle greift, will auch an
beliebiger Stelle geändert werden — und dann stünde dieselbe Frage wieder da.

## Eine Warteschlange

Das ist der Fall, für den `first` und `rest` zusammengehören:

```
global warteschlange = []

fn anstellen(was: Text) {
    warteschlange = warteschlange.plus(was)
}

fn abarbeiten() {
    if warteschlange.count() == 0 {
        return
    }
    let naechstes = warteschlange.first()
    warteschlange = warteschlange.rest()
    log("dran: " + naechstes)
}
```

Als `global` übersteht sie den Serverneustart — eine Warteschlange, die beim
Neustart verschwindet, wäre keine.

**Sie hat eine Obergrenze.** Ein globaler Listenwert ist der einzige Wert, den
ein Programm in einer Schleife wachsen lassen kann und der in der Weltdatei
landet. Wird er zu lang, hält das Programm an und sagt es. Wie lang, steht in
der Serverkonfiguration (`globalListSize`, Vorgabe 256) — auf einem Server
entscheidet das der Betreiber.

## Was eine Liste im Protokoll zeigt

```
log(storage.items())
```

schreibt `[64 iron_ore, 3 coal]`. Ab sieben Einträgen wird gekürzt, aber
mitgezählt: `[0, 1, 2, 3, 4, 5, … +3]`. Eine Liste, die einfach aufhört, läse
sich wie eine vollständige.

## Was es nicht gibt

Kein `map`, kein `groupBy`, keine Karten (`Map`). In einer Fabrik gab es dafür
bisher keinen Fall — und hinzufügen lässt sich später leicht, wegnehmen nicht.
