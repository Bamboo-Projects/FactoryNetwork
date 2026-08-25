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
woran es hängt — „baut 8 Eichenholzbretter", „es fehlt: 2 Eichenstamm" oder
„kein Fabricator im Netz". Rechts ein **×**: Das bricht ihn ab. Was schon
gebaut wurde, bleibt im Speicher.

Was dort als fehlend steht, ist immer der **Grundstoff** und nie eine
Zwischenstufe: „es fehlen 8 Bretter" hülfe dir nicht, denn Bretter kann das
Netz selbst machen. Genannt wird, was du hinlegen musst.

## Keine Muster-Items

Was gebaut werden kann, weiß das Spiel bereits — jedes Werkbank-Rezept steht
im Server. Du musst nichts anlernen und nichts einlegen.

Welches Rezept genommen wird, **entscheidet dein Bestand**: Gibt es mehrere,
nimmt das Netz das, dessen Zutaten dastehen. Wer Eichenbretter im Laufwerk
hat, bekommt keine Meldung über fehlendes Fichtenholz.

Dasselbe gilt innerhalb eines Rezepts. Eine Truhe braucht acht **Bretter**,
nicht acht Eichenbretter — welche Sorte, entscheidet das Netz nach dem, was da
ist. Reicht keine Sorte allein, mischt es: fünf Eiche, drei Fichte. Von Hand
ginge es auch.

## Mehrstufig

**Fehlen Bretter, macht das Netz welche aus Stämmen.** Und fehlen die Stämme,
sagt es das.

Ein Auftrag über eine Truhe bei zwei Eichenstämmen im Laufwerk läuft in zwei
Schritten: erst acht Bretter, dann die Truhe. Du siehst beide im Reiter
vorbeiziehen. Wie tief das Netz dabei sucht, steht in der Serverkonfiguration
(`craftingDepth`, Vorgabe acht Ebenen) — das reicht für jede Kette, die ein
Pack kennt.

Zwei Dinge, die du dabei wissen solltest:

**Es baut nichts halb.** Geht der Plan nicht auf — irgendwo fehlt ein
Grundstoff —, dann wartet der Auftrag, statt schon einmal die Bretter zu
machen. Sonst stünde am Ende ein Stapel Zwischenzeug herum, das niemand
bestellt hat, und der Auftrag hinge trotzdem.

**Ein Kreis hält nichts auf.** „Barren aus Block" und „Block aus Barren" ist
ein Rezeptpaar, an dem eine Suche ewig laufen könnte. Sie tut es nicht: Sie
merkt den Kreis und meldet stattdessen das, was du hinlegen kannst — die
Barren.

## Nachschub, der sich selbst bestellt

Ein Auftrag ist einmalig. Wer einen Vorrat halten will, schreibt einen Worker
— denselben wie überall, nur mit `crafting` als Quelle:

```
worker eisen_vorrat {
    from crafting
    to storage
    filter item:iron_ingot
    maintain 256
}
```

Fällt der Bestand unter 256, bestellt er die Lücke. Ein zweites Mal bestellt
er sie nicht: Er rechnet gegen den Bestand **und** die offenen Aufträge —
sonst würden aus „halte 256 vor" Tausende, denn der Bestand steigt erst, wenn
gebaut ist.

Drei Dinge sind hier anders als bei einem Worker, der schiebt:

- **Das Ziel ist `storage`**, und nur das. Gefertigt wird ins Lager; wer es in
  einer Maschine haben will, schreibt einen zweiten Worker `from storage to
  maschine`.
- **`maintain` ist Pflicht.** Ohne Zahl hieße die Anweisung „bestelle endlos".
- **`rate` begrenzt die Bestellung nur, wenn du es hinschreibst.** `rate 64
  per 1s` heißt „höchstens 64 je Runde". Ohne Angabe wird die ganze Lücke auf
  einmal bestellt.

Ein `filter`, der mehrere Arten trifft, hält von **jeder** die Menge vor — wie
`maintain` überall. Bei `tag:c/ingots` ist das eine große Bestellung; das ist
gewollt, aber du solltest wissen, dass du sie aufgibst.

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
