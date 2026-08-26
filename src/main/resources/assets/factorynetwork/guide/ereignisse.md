---
navigation:
  title: Ereignisse
  position: 68
---

# Ereignisse

Ein Worker fragt bei jedem Tick nach. Ein Ereignis meldet sich von selbst.

Für „bewege Erz aus der Kiste in den Brecher" ist der Worker die kürzere
Antwort — er soll das ja dauernd tun. Sobald aber etwas **einmal** geschehen
soll, weil etwas anderes eingetreten ist, wird das Nachfragen umständlich: Du
müsstest einen Zustand mitführen, damit die Reaktion nicht in jeder Runde neu
losgeht. Genau diesen Zustand nimmt dir ein Ereignis ab.

## `on` — der Block, der zuhört

```
on device_output(brecher) {
    move all from brecher to storage
}
```

Das läuft, sobald im Brecher etwas mehr liegt als beim letzten Blick. Der Name
in der Klammer ist deiner: Er steht für den Wert, den das Ereignis mitbringt —
hier das Gerät, an dem etwas passiert ist.

Ein `on`-Block ist **kein Worker**. Er hat kein `rate` und kein `when`; er
läuft, wenn sein Ereignis eintritt, und sonst nie.

## Die sieben, die das Netz selbst auslöst

| Ereignis | Wann | Was es mitbringt |
|---|---|---|
| `device_online` | ein Gerät ist im Netz aufgetaucht | das Gerät |
| `device_offline` | ein Gerät ist verschwunden | seinen **Namen** als Text |
| `device_changed` | in einem Gerät hat sich der Inhalt geändert | das Gerät |
| `device_output` | in einem Gerät ist etwas **dazugekommen** | das Gerät |
| `redstone_changed` | das Signal an einem Connector ist ein anderes | das Gerät **und** die Stärke |
| `crafting_finished` | ein Fertigungsauftrag ist durch | die Nummer des Auftrags |
| `crafting_failed` | ein Auftrag kommt nicht weiter | die Nummer **und** den Grund als Text |

Zwei bringen zwei Werte mit, die anderen fünf einen. Schreibst du mehr Namen
in die Klammer, als es Werte gibt, sagt es der Editor — die überzähligen
blieben sonst für immer leer.

`device_offline` gibt dir bewusst nur den Namen: Das Gerät gibt es nicht mehr,
und ein Verweis darauf ließe sich nirgends mehr auflösen.

```
on device_offline(name) {
    warn("Der Connector " + name + " ist weg")
}
```

## `device_output` gegen `device_changed`

Der Unterschied entscheidet, ob deine Fabrik im Kreis läuft.

- **`device_changed`** meldet jede Änderung. Auch die, die das Netz selbst
  verursacht hat, als es etwas eingelegt hat.
- **`device_output`** meldet nur, dass von einer Art **mehr** dasteht als
  vorher — und was das Netz selbst eingelegt hat, zählt dabei nie mit.

Deshalb ist `device_output` das, was du fast immer willst:

```
on device_output(ofen) {
    move all from ofen to storage
}
```

Mit `device_changed` an derselben Stelle würde das Herausholen selbst eine
Änderung sein, und der Block weckte sich immer wieder neu.

Der Name sagt bewusst nicht „fertig". Ob eine Maschine ihre Arbeit beendet
hat, weiß von außen niemand; gemessen wird der Unterschied zum letzten Blick.

## Wann sie kommen

**Alle zehn Ticks**, also zweimal in der Sekunde. `device_changed`,
`device_output` und `redstone_changed` werden abgefragt und nicht gemeldet —
eine fremde Maschine sagt dem Netz nicht Bescheid, wenn sich etwas in ihr tut.

`device_online` und `device_offline` hängen an etwas anderem: Sie kommen,
wenn das Netz seinen Aufbau neu bestimmt hat — nachdem du ein Kabel gesetzt,
einen Connector benannt oder etwas abgebaut hast.

**Nur, wenn jemand hinhört.** Steht kein `on device_changed` im Programm und
wartet kein Ablauf darauf, sieht das Netz gar nicht erst nach. Ein Ereignis,
auf das niemand hört, kostet nichts.

**Beim ersten Sehen bleibt es still.** Wenn das Netz ein Gerät zum ersten Mal
betrachtet, hat sich nichts geändert — es war nur vorher nichts bekannt. Ohne
diese Regel gäbe es bei jedem Serverstart einen Sturm von Meldungen, und das
wäre für dich dasselbe wie Rauschen.

## Eigene Ereignisse

Was das Netz nicht von sich aus meldet, kannst du selbst melden. Dazu gehören
drei Wörter: `event` erklärt es, `emit` löst es aus, `await` wartet darauf.

```
event Schicht(name: Text)

on redstone_changed(schalter, stärke) {
    if stärke > 0 {
        emit Schicht("nacht")
    } else {
        emit Schicht("tag")
    }
}

on Schicht(welche) {
    log("Umschalten auf " + welche)
}
```

Die Typen in der `event`-Zeile stehen dort, weil der Editor sonst nicht
vervollständigen kann. `Int`, `Text`, `Bool`, `Item`, `Fluid`, `Duration`,
`Device` — dieselben wie bei den Parametern einer Funktion.

Ein Ereignis darf beides zugleich haben: einen `on`-Block, der darauf
reagiert, und einen Ablauf, der mit `await` darauf wartet. Beide werden
geweckt. Wie das Warten funktioniert, steht unter *Abläufe*.

## Mehrere Empfänger

Mehrere `on`-Blöcke für dasselbe Ereignis laufen **alle**, in keiner
zugesicherten Reihenfolge. Jeder bekommt seinen eigenen Ablauf.

Wenn du eine Reihenfolge brauchst, hast du in Wahrheit eine Abfolge — und die
schreibst du als Funktion, die zwei Dinge nacheinander tut.

## Ein `on` darf warten

Das ist der Unterschied zu einer Filterkarte in anderen Mods: Der Block ist
nicht auf einen Augenblick beschränkt.

```
on device_output(ofen) {
    sleep 2s
    move all from ofen to storage
}
```

`sleep`, `await`, ein Aufruf einer Funktion, die selbst wartet — alles
erlaubt. Damit gelten für einen `on`-Block dieselben Regeln wie für jeden
anderen Ablauf: Er übersteht den Serverneustart, er braucht einen Platz im
Serverschrank, und was er gerade tut, steht im Reiter **Netz**.

## Der Tippfehler, der nichts sagt

Ein `on`-Block braucht keine Deklaration — deshalb würde jeder Name
durchgehen. Schreib einmal `on device_outpt(brecher)` statt
`on device_output(brecher)`: Das übersetzt sauber, wird übernommen und läuft
**nie**. Es gibt keinen ersten Lauf, bei dem es auffallen könnte.

Deshalb prüft der Editor die Namen gegen die Liste oben und gegen deine
eigenen `event`-Zeilen. Steht dort etwas Unbekanntes, bekommst du eine
Warnung mit dem nächstgelegenen Namen dahinter — und wenn keiner nah genug
ist, die ganze Liste. Es bleibt bei einer Warnung: Ein Projekt aus mehreren
Dateien darf sich übernehmen lassen, bevor die letzte geschrieben ist.

## Wann ein Worker besser ist

Ein Ereignis ist nicht immer die feinere Lösung.

- **Dauernd dasselbe bewegen** — dafür ist der Worker da. Er hat `rate`,
  `when` und einen Zustand, den du im Reiter *Netz* ablesen kannst.
- **Auf einen Bestand reagieren** — ein Worker mit `when` ist ehrlicher als
  ein `on device_changed`, das bei jeder Kleinigkeit anspringt und selbst
  nachrechnen muss.
- **Einmal etwas tun, weil etwas passiert ist** — dafür das Ereignis.

Die Faustregel: Beschreibt dein Satz einen **Zustand** („es soll immer
mindestens ein Stapel Kohle im Ofen liegen"), nimm einen Worker. Beschreibt er
einen **Augenblick** („wenn der Ofen etwas ausgibt"), nimm ein Ereignis.
