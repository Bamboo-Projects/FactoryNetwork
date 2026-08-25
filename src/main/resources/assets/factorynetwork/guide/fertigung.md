---
navigation:
  title: Fertigung
  position: 65
---

# Fertigung

Das Netz baut, was du bestellst — aus dem, was im Speicher liegt.

## Was du brauchst

Einen **Fabricator** am Kabel. Er hält nichts und rechnet nichts; er ist die
Erlaubnis, dass gebaut wird. Wie viele im Netz hängen, entscheidet, wie viele
Schritte je Sekunde geschehen: **Wer schneller fertigen will, stellt einen
zweiten hin.**

*Kostet: einen Kanal, 1 FE/t.*

## Bestellen

Im Code, wie alles hier:

```
fn nachschub() {
    craft(64 item:chest)
}
```

Zurück kommt die **Kennung** des Auftrags. Eine Null heißt genau eines: Für
diesen Gegenstand gibt es kein Rezept.

Die Auswahl muss **eine Art** meinen. Ein Auftrag über „irgendein Erz" hätte
keine Antwort auf die Frage, was gebaut werden soll.

## Was der Reiter zeigt

Im Terminal unter **Fertigung**: je Auftrag, wie weit er ist, und darunter,
woran es hängt — „es fehlt: 8 Eichenholzbretter" oder „kein Fabricator im
Netz". Rechts ein **×**: Das bricht ihn ab. Was schon gebaut wurde, bleibt im
Speicher.

## Keine Muster-Items

Was gebaut werden kann, weiß das Spiel bereits — jedes Werkbank-Rezept steht
im Server. Du musst nichts anlernen und nichts einlegen.

Welches Rezept genommen wird, **entscheidet dein Bestand**: Gibt es mehrere,
nimmt das Netz das, dessen Zutaten dastehen. Wer Eichenbretter im Laufwerk
hat, bekommt keine Meldung über fehlendes Fichtenholz.

## Einstufig, und warum

**Fehlen Bretter, macht das Netz keine aus Stämmen.** Der Auftrag wartet und
sagt es.

Das ist Absicht und kein Mangel. Ein Auftrag, der im Hintergrund einen Baum
fällt, den niemand bestellt hat, ist die unangenehmere Überraschung — und wer
eine Kette will, schreibt sie: Erst Bretter bestellen, dann Truhen. Mehrstufig
kommt später.

## Wenn es fertig ist

```
on crafting_finished(auftrag) {
    log("fertig: " + auftrag)
}

on crafting_failed(auftrag, grund) {
    log(grund)
}
```

`crafting_failed` meldet sich **nur**, wenn ein Auftrag nicht mehr fertig
werden kann — wenn also sein Rezept verschwunden ist. Fehlende Zutaten sind
kein Fehlschlag: Wer darauf wartet, wartet, und morgen liegen sie vielleicht
da.

## Was ein Auftrag übersteht

Einen Serverneustart. Er lebt am Controller und nicht am Fabricator: Einer,
der am Gerät hinge, wäre weg, sobald jemand es abbaut — und das ist genau der
Moment, in dem du wissen willst, was noch offen war.
