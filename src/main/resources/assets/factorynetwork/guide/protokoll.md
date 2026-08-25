---
navigation:
  title: Protokoll
  position: 58
---

# Protokoll

Ein Programm, das nichts sagen kann, lässt beim Suchen eines Fehlers nur das
Raten übrig. Deshalb der Reiter **Protokoll** im Terminal — und vier
Funktionen, mit denen dein Programm hineinschreibt.

```
fn pruefen() {
    debug("Bestand: " + storage.count(item:coal))

    if storage.count(item:coal) < 64 {
        warn("Kohle wird knapp")
    }

    if brecher.online == false {
        error("Brecher ist weg")
    }

    info("Durchlauf fertig")
}
```

`log("…")` gibt es weiterhin und schreibt als `info`.

## Die vier Stufen

| | |
|---|---|
| `debug` | Zwischenstände beim Suchen. Im Terminal erst auf Wunsch sichtbar |
| `info` | Was gut lief |
| `warn` | Etwas stimmt nicht, die Fabrik läuft weiter |
| `error` | Etwas ist stehen geblieben |

Die Filterknöpfe oben im Reiter setzen eine **Untergrenze**: Wer auf *Warnung*
klickt, sieht Warnungen und Fehler. Voreingestellt ist *Info* — ein `debug` in
einer Schleife schreibt zwanzig Zeilen je Sekunde, und danach findet niemand
mehr die eine Meldung, auf die es ankam.

## Woher eine Zeile kommt, steht daneben

Vor jedem Text steht in Klammern, wer ihn geschrieben hat: der Worker, der
Ablauf oder die Funktion. Bei dreißig Workern ist das die halbe Auskunft — ein
„Kohle wird knapp" ohne Absender hilft nicht weiter.

## Das Netz schreibt selbst mit

Nicht nur dein Programm. Was die Laufzeit sonst nur für sich behielt, steht
jetzt auch dort:

- „maintain ohne filter — welche Art soll vorgehalten werden?"
- „die Auswahl trifft zurzeit nichts"
- „Der Knopf *Nachschub* nennt keine Funktion."

Ein Worker läuft zwanzigmal je Sekunde; sein Hinweis steht trotzdem genau
einmal da. Nach dem Übernehmen eines Programms darf er wiederkommen — dann ist
er eine neue Auskunft.

## Es bleibt über den Neustart

Das Protokoll geht mit der Welt auf die Platte, die letzten zweihundert
Zeilen. Wer morgens nachsieht, warum die Anlage nachts stehen blieb, findet
die Zeile auch dann noch, wenn der Server zwischendurch neu gestartet ist.

Ältere Zeilen fallen vorne heraus. Wenn du eine Meldung behalten willst,
schreib sie dir heraus, bevor zweihundert neue dazukommen.
