---
navigation:
  title: Werte und Gruppen
  position: 45
---

# Werte und Gruppen

Zwei Dinge, die ein Programm kürzer und lesbarer machen: ein Wert, der überall
gilt, und ein Name für mehrere Geräte.

## Ein Wert für die ganze Fabrik

```
global modus = "tag"
```

Das ist ein Wert, den alle Dateien sehen und den jede Funktion ändern darf.
Anders als ein `let` in einer Funktion **überlebt er den Serverneustart**: Wer
die Fabrik nachts verlässt, findet sie morgens im selben Zustand vor.

Nützlich wird er, weil `when` und die Anzeigen ihre Ausdrücke ohnehin laufend
auswerten:

```
global modus = "tag"

worker erz {
    from grube
    to storage
    filter tag:c/ores
    when modus == "tag"
}

display halle {
    title "Fabrik"
    row "Modus" modus
}

fn nachtschicht() {
    modus = "nacht"
}
```

Ein Aufruf von `nachtschicht()` legt den Worker schlafen und ändert die
Anzeige — ohne dass irgendwo steht, dass er das tun soll. Der Wert steht an
einer Stelle statt an dreien.

Auslösen lässt sich das von überall: aus einem Knopf auf der Anzeige, aus
einem `on redstone_changed`, aus einem Ablauf, der auf die Uhrzeit wartet.

### Der Anfangswert muss feststehen

`global vorrat = 640` ist in Ordnung.
`global vorrat = storage.count(item:coal)` nicht.

Eine Rechnung als Anfangswert wirft die Frage auf, wann sie liefe: beim
Übernehmen, beim Serverstart, bei jedem Laden des Chunks? Ein fester Wert hat
diese Frage nicht. Rechnen darfst du danach, so oft du willst.

### Wenn du das Programm änderst

- **Gleicher Name, gleiche Art:** Der Wert bleibt. Wer `modus` auf `"nacht"`
  gestellt hat und dann einen Worker ändert, will nicht wieder bei `"tag"`
  anfangen.
- **Neuer Name:** Der Anfangswert aus der Deklaration.
- **Name weg:** Der Wert wird vergessen.
- **Gleicher Name, andere Art:** Der Anfangswert. Ein Text, der plötzlich als
  Zahl gelesen wird, ist kein erhaltenswerter Zustand.

Der Reiter **Netz** im Terminal zeigt, was gerade in den globalen Werten
steht.

## Ein Name für mehrere Geräte

```
group brecher {
    members brecher_1, brecher_2, brecher_3
    strategy round_robin
}

worker mahlen {
    from lager
    to brecher
    filter tag:c/ores
}
```

`to brecher` beliefert jetzt alle drei. Welcher wann drankommt, sagt die
`strategy`:

| | |
|---|---|
| `round_robin` | reihum, gleichmäßig |
| `first_available` | das erste, das kann |
| `least_filled` | dorthin, wo am wenigsten liegt |
| `random` | zufällig |
| `priority` | in der Reihenfolge der Mitglieder |

Ohne Angabe gilt `round_robin`. Ein Gerät, das gerade nichts annimmt, hält den
Transfer nicht auf — der Worker geht die Reihenfolge durch, bis eines etwas
nimmt.

**Dieselbe Gruppe darf von zwei Workern verschieden bedient werden:** Eine
`strategy` am Worker geht der an der Gruppe vor.

### Mitglieder über ein Muster

```
group oefen {
    members ofen_*
}
```

Wer später einen weiteren Ofen aufstellt und ihn `ofen_9` nennt, muss ihn
**nicht** im Code eintragen — die Gruppe nimmt ihn auf, sobald er im Netz
hängt. Anders als bei Gegenständen wird ein Gerätemuster nicht beim
Übersetzen festgeschrieben, sondern laufend aufgelöst.

Hängt kein einziges Mitglied im Netz, sagt der Worker das im Reiter **Netz**:
*„Die Gruppe … hat kein Mitglied im Netz"* — er steht dann still, statt still
zu sein.

### Was eine Gruppe nicht ist

Eine Gruppe ist ein Ziel, kein Wert. `brecher.online` oder
`brecher.members()` gibt es nicht: Der Name steht dort, wo ein Gerätename
steht, und nicht in einem Ausdruck. Wer über die Mitglieder laufen will,
schreibt sie heute einzeln hin.
