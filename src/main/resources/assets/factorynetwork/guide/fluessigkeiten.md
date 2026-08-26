---
navigation:
  title: Flüssigkeiten und Gase
  position: 62
---

# Flüssigkeiten und Gase

Wasser, Lava, geschmolzenes Erz, Wasserstoff — das Netz bewegt sie mit
denselben Zeilen wie Gegenstände. Was du dafür wissen musst, passt auf diese
Seite.

Wo sie lagern, steht unter *Speicher*: Flüssigkeiten in Flüssigkeitszellen,
Chemikalien in Chemikalienzellen, beide im selben Laufwerk.

## Gemessen wird in Millibucket

Ein Eimer sind **1000 mB**. Auf der Zelle steht die Zahl in Eimern, im
Programm und im Terminal in Millibucket:

```
move 1000 fluid:water from bottich to kessel
```

Das ist ein Eimer. Es gibt keine halben Eimer und keine Nachkommastellen —
alles ist eine ganze Zahl in mB.

## Der Tank hängt am Connector

Wie bei Gegenständen: Der Connector zeigt auf die Maschine, und was er dort
erreicht, entscheidet die Maschine über ihre **Seitenregeln**. Eine Maschine
kann beides haben, ein Fach und einen Tank; welches gemeint ist, entscheidet
deine Auswahl im Programm.

Zeig im Editor auf den Namen eines Geräts: Das Terminal sagt dir, was an
welcher Seite hineingeht und was herauskommt. Steht dort für die Seite deines
Connectors nichts, hilft kein Programm — dann muss der Connector woandershin.

## Ein Worker holt und bringt

```
worker kuehlwasser {
    from bottich
    to kessel
    filter fluid:water
}
```

Genau wie bei Gegenständen, mit einem Unterschied: **`filter` ist Pflicht.**

Ohne Filter wäre der Worker ein Gegenstands-Worker — an der Auswahl erkennt
das Netz, was du meinst. Und das ist auch die sichere Antwort: Ein Tank hält
meist genau eine Sorte, und die falsche herauszuziehen ist teurer als bei
Gegenständen. Ein Eimer Lava im Wasserkreislauf ist kein Sortierfehler,
sondern ein Nachmittag.

Ein Worker geht immer zwischen **Gerät und Speicher** oder zwischen zwei
Geräten — dasselbe `from`/`to` wie überall. `storage` ist der Netzspeicher:

```
worker lava_einlagern {
    from sammler
    to storage
    filter fluid:lava
}
```

## Mehrere Sorten auf einmal

`fluidtag:` nimmt alles, was in einem Tag steht:

```
worker schmelze_abholen {
    from ofen
    to storage
    filter fluidtag:c/molten
}
```

**`all` meint Gegenstände**, auch hier. Wer Flüssigkeiten meint, schreibt es
hin — so räumt niemand versehentlich einen Tank leer, weil er einen Sammelzug
gemeint hat.

## Nachsehen, wie viel drin ist

`count` fragt auch nach Flüssigkeiten:

```
fn genug_wasser() {
    return kessel.count(fluid:water) > 4000
}
```

Und für den Netzspeicher gilt dasselbe wie bei Gegenständen — der Bestand
steht im Terminal und lässt sich auf eine Anzeigenwand legen.

## Gase brauchen Mekanism

Mit **Mekanism** kommen Chemikalien dazu — Gase, Schlämme, Pigmente,
Infusionen. Seit Mekanism 10.7 sind das alles Chemikalien und nicht mehr vier
getrennte Arten, und so heißen sie auch hier:

```
worker wasserstoff {
    from elektrolyseur
    to storage
    filter chemical:mekanism/hydrogen
}
```

Ohne Namensraum ist `mekanism` gemeint — Chemikalien gibt es in Minecraft
nicht. Ohne die Mod meldet sich die Zeile beim Übernehmen und sagt, dass
Mekanism fehlt; sie tut nicht so, als sei die Maschine schuld.

**Die Seitenregeln sind bei Mekanism strenger.** Jede Maschine hat eine
Seitenkonfiguration, und das Netz hält sich daran. Steht für die Seite deines
Connectors „nichts", passiert nichts — auch dann nicht, wenn die Maschine
voll ist.

## Wenn eine Maschine Wasser braucht

Ein Rezept darf eine Flüssigkeit als Zutat nennen, und das Netz füllt sie beim
Anfangen selbst ein. Beschafft wird sie aber nicht: Fehlt sie, wartet der
Auftrag und sagt, wie viel. Das steht unter *Fertigung*.

## Was am häufigsten schiefgeht

- **Kein Laufwerk mit Flüssigkeitszelle.** Ein Netz ohne Zelle lagert keine
  Flüssigkeit — genau wie bei Gegenständen. Der Worker holt dann nichts, und
  das ist kein Fehler, sondern ein leerer Speicher.
- **Der `filter` fehlt.** Dann ist es ein Gegenstands-Worker, und der findet
  im Tank nichts.
- **Die Sorte passt nicht.** Ein Tank mit Lava nimmt kein Wasser. Das Netz
  probiert es und lässt es dann bleiben — nichts geht verloren.
- **Der Connector hängt an der falschen Seite.** Der häufigste Fall bei
  Mekanism, und der Editor zeigt es dir.

Mehr dazu unter *Fehlersuche*.
