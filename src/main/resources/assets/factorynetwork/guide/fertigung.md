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
im Server, und Steinsägen-Rezepte auch. Beides ist Handarbeit ohne Maschine,
beides macht der Fabricator. Du musst nichts anlernen und nichts einlegen.

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

## Maschinen, die mitarbeiten

Nicht alles baut der Fabricator. Ein Barren kommt aus dem **Ofen**, und das
dauert.

Hängt ein Ofen am Netz, benutzt ein Auftrag ihn von selbst: Er legt das Erz
ein, wartet, und holt den Barren ab. Du musst dafür keinen Worker schreiben —
das Netz holt selbst.

Erkannt werden **Ofen, Schmelzofen und Räucherofen** sowie die **Presse**
dieser Mod. Bei ihnen steht die Form des Rezepts fest: eine Zutat, eine
Ausgabe, eine Dauer. Für alles andere — jede Maschine aus einer anderen Mod —
schreibst du das Rezept selbst auf; wie, steht weiter unten.

**Den Brennstoff legst du hin.** Das Netz heizt nicht. Ein Worker reicht:

```
worker kohle {
    from storage
    to ofen.slots(1)
    filter item:coal
    maintain 8
}
```

Fach 1 ist das Brennstofffach eines Ofens — bei der Presse ist Fach 0 der
Stempel. Wartet ein Auftrag lange, sagt er es: *„wartet auf ofen_1 — hat er
Brennstoff?"*

**Ein Auftrag benutzt eine Maschine, die frei ist.** Steht in einem Ofen schon
etwas, geht er zum nächsten. Hängen drei am Netz, laufen drei Aufträge
nebeneinander.

**Was im Ofen liegt, überlebt den Serverneustart.** Der Plan wird jedes Mal
neu gerechnet — er ist nur eine Absicht. Ein Erz, das im Ofen liegt, ist keine
Absicht, sondern eine Tatsache; die wird aufgeschrieben.

## Maschinen aus anderen Mods

Ofen, Schmelzofen, Räucherofen und die Presse kennt das Netz. Einen Brecher
aus einer anderen Mod kennt es nicht — und es kann ihn auch nicht kennenlernen:
Minecraft gibt für ein fremdes Maschinenrezept nicht genug her, um es zu lesen.
Deshalb machen AE2 und Refined Storage dasselbe wie diese Mod: Der Spieler
schreibt es auf.

Nur schreibst du hier kein Muster auf einen Gegenstand, sondern eine Zeile ins
Programm:

```
recipe erz_mahlen at brecher {
    in 1 item:iron_ore
    out 2 item:iron_dust
}
```

Ab jetzt weiß das Netz, dass der Brecher aus einem Erz zwei Staub macht — und
benutzt ihn für jeden Auftrag, der Staub braucht, auch mitten in einer Kette.

Drei Dinge dazu:

- **`at` ist Pflicht.** Wo es läuft, ist der ganze Grund für die Zeile.
- **Die Menge steht immer da**, auch die Eins.
- **Keine Fachnummern.** Wo etwas hingehört, entscheidet die Maschine selbst.

Schreibst du ein Gerät hin, das es nicht gibt, sagt es das Terminal beim
Übernehmen — das ist der Vorteil davon, dass ein Rezept im Programm steht und
nicht auf einem Zettel.

## Wenn die Maschine Wasser braucht

Manche Maschinen wollen mehr als Gegenstände. Schreib es dazu:

```
recipe erz_waschen at washer {
    in 1 item:iron_ore
    in 1000 fluid:water
    out 2 item:iron_nugget
}
```

Das Netz füllt das Wasser beim Anfangen selbst ein — aus deinem Speicher, so
wie es auch das Erz einlegt. Chemikalien gehen genauso, wenn Mekanism dabei
ist.

Ein Unterschied bleibt, und der ist wichtig: **Wasser wird nicht beschafft.**
Fehlt ein Erz, baut das Netz es nach; fehlt das Wasser, wartet der Auftrag und
sagt dir, wie viel fehlt. Sorg also dafür, dass genug im Netz liegt — mit einem
Worker, der aus einem Tank zieht, oder von Hand mit einem Eimer.

Und er wartet, ohne etwas anzufassen. Ohne Wasser bleibt auch das Erz liegen:
Eine Maschine mit halber Rechnung fängt nie an, und dein Erz wäre weg.

Strom gehört nicht ins Rezept. Den bekommt die Maschine über die
Stromverteilung, ganz von selbst — und wie viel sie zieht, weiß sie besser als
du. Schreibst du trotzdem `in 1000 power` hin, sagt es dir das Terminal beim
Übernehmen.

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
