---
navigation:
  title: Abläufe
  position: 70
---

# Abläufe

**Ein Ablauf, der wartet, überlebt den Serverneustart.** Er macht danach genau
dort weiter, wo er stand — mitten in einer Funktion, mit seinen Variablen, mit
dem halb abgearbeiteten Auftrag. Das ist die Zusage, um derentwillen diese Mod
gebaut ist; alles andere hier ist Beiwerk dazu.

Möglich ist das, weil der Zustand eines Ablaufs nicht in Javas Aufrufstapel
steht, sondern in Rahmen, die sich aufschreiben lassen.

## Wann eine Funktion ein Ablauf wird

Nicht jede. Wer nur rechnet und Gegenstände bewegt, läuft durch und ist fertig,
bevor der Tick vorbei ist — schneller und ohne Buchführung.

**Erst `await` und `sleep` machen einen Ablauf daraus:** etwas, das im Reiter
**Netz** steht, einen Platz belegt und einen Zustand hat.

## `sleep` — eine Zeit lang nichts tun

```
fn zyklus() {
    move 8 item:iron_ore from lager to brecher
    sleep 10s
    move item:iron_dust from brecher to lager
}
```

Zeiten schreiben sich `t` für Ticks, dazu `s`, `min` und `h`. Gerechnet wird
intern in Ticks; `1s` sind 20 davon. Bruchteile sind erlaubt, solange sie
aufgehen — `0.5s` sind 10 Ticks. Was nicht aufgeht, meldet der Übersetzer,
statt still zu runden.

**Zeit ist ein eigener Typ, keine Zahl.** `sleep(30)` ist deshalb ein Fehler
und kein Rätsel: Ob 30 Ticks oder 30 Sekunden gemeint sind, ist nicht zu
erraten, und ein Faktor 20 fällt im Betrieb erst spät auf.

Ein `sleep` gibt es nur in Abläufen. In einem Worker wäre es sinnlos — der
prüft ohnehin bei jedem Tick aufs Neue.

## Auf das eigene Ereignis warten

Ohne weitere Angabe weckt jedes `Fertig` **jeden** Wartenden. Laufen mehrere
Runden nebeneinander, gehört dazu, welche gemeint ist:

```
event Fertig(nummer: Int)

fn runde(meine_nummer: Int) {
    move 8 item:iron_ore from lager to brecher
    await Fertig where nummer == meine_nummer
    move item:iron_dust from brecher to lager
}
```

In der `where`-Klausel sind die Parameter des Ereignisses sichtbar — hier
`nummer` — und die eigenen Namen des Ablaufs gleich mit. So lässt sich das
eine mit dem anderen vergleichen.

Wer `Fertig` auslöst und was zu tun ist, wenn niemand antwortet, steht unter
*Programmieren*: `emit` auf der einen Seite, `timeout` mit `else` auf der
anderen.

## Mehrere Empfänger

Mehrere `on`-Blöcke für dasselbe Ereignis laufen **alle**, in keiner
zugesicherten Reihenfolge. Wer eine Reihenfolge braucht, hat in Wahrheit eine
Abfolge und schreibt eine Funktion.

Ein Ereignis wird dabei nicht mitten in einem Ablauf zugestellt, sondern
zwischen zwei Schritten. Für dich bleibt es derselbe Tick.

## Was ein Ablauf alles übersteht

- **Den Serverneustart.** Er wartet weiter und wird geweckt wie zuvor.
- **Eine Schleife mittendrin.** Ein `for` merkt sich, bei welchem Eintrag es
  steht. Ein Neustart in der Mitte der Liste setzt an derselben Stelle fort —
  er fängt nicht von vorn an und tut nichts ein zweites Mal.
- **Einen Aufruf.** Ruft eine Funktion eine zweite, die wartet, kommen beide
  Rahmen zurück, und es geht hinter dem Aufruf weiter.
- **Den Stillstand des Netzes.** Ohne Strom, ohne Serverschrank oder mit einem
  Programm, das nicht mehr auf die Datenträger passt, friert alles ein statt
  abzubrechen. Was dabei mit einer laufenden Frist geschieht, steht unter
  *Kanäle und Strom*.

**Was ein Ablauf nicht hält, sind Gegenstände.** Er steht zwischen zwei
Anweisungen, nicht mit vollen Händen. Deshalb kostet ein Neustart nie einen
Stapel.

## Plätze im Serverschrank

Hier hängt der Serverschrank mit den Abläufen zusammen, und zwar an zwei
verschiedenen Bauteilen.

**Das Rechenwerk gibt die Plätze.** Ein Ablauf belegt seinen Platz, solange es
ihn gibt — auch während er schläft oder auf ein Ereignis wartet. Ein Rechenwerk
mit zwei Plätzen und zwei wartenden Abläufen nimmt keinen dritten an.

Was keinen Platz bekommt, wird **nicht abgelehnt, sondern angestellt**. Es
steht dann als `QUEUED` da, mit dem Grund *wartet auf ein freies Rechenwerk*,
und rückt nach, sobald einer frei wird. Der Ältere zuerst; diese Regel steht
fest, damit „warum lief meiner nicht" überhaupt zu beantworten ist.

Der Grund für das Anstellen ist einfach: **Verzögerung ist wiederherstellbar,
Verlust nicht.** Ein abgewiesenes Ereignis ist für immer weg, und die
Gegenstände stünden bis zum nächsten Neustart in einer Maschine, die niemand
mehr anfasst.

**Der Speicher ist dagegen eine Wand.** Er zählt, wie viele Abläufe überhaupt
bestehen dürfen, die angestellten mitgerechnet. Was nicht mehr hineinpasst,
scheitert sichtbar — mit einer Meldung, die sagt, wie viele hineingehen. Ein
schlafender Ablauf belegt Speicher genauso wie ein rechnender; er steht
schließlich irgendwo, mit allen seinen Variablen.

Im Reiter **Netz** steht beides übereinander:

- `2 von 8 Plätzen belegt · 1 wartet` — die Rechenwerke.
- `Speicher: 3 von 32 Abläufen` — die Speicherbauteile.

Dazu kommt eine Bremse gegen die Endlosschleife: Ein Ablauf darf je Tick nur
eine feste Zahl Schritte machen. Ein falsch geschriebenes `while true` hält
damit nicht den Server an, sondern läuft langsam vor sich hin und lässt sich
jederzeit abbrechen.

## `STALE` — das Programm hat sich geändert

Ein wartender Ablauf zeigt auf Stellen im Programm. Wird ein neues Programm
übernommen, gehen die Wartenden denselben Weg wie über einen Serverneustart:
aufschreiben, neu aufbauen, zurücklesen.

**Passt die Gestalt des Programms noch, laufen sie weiter.** Gestalt heißt: wie
viele Anweisungen in jedem Block stehen, welcher Art sie sind, und auf welche
Ereignisse gewartet wird. Aus `move 8` ein `move 16` zu machen ändert daran
nichts — jeder Wartende läuft weiter.

Eine eingefügte oder gelöschte Zeile ändert sie. Dann steht der Ablauf als
**STALE** im Reiter **Netz**, gelb, mit dem Grund *Programm geändert, während
dieser Ablauf wartete* — und mit zwei Knöpfen am Zeilenende:

- **weiter** — nimm ihn dort wieder auf, wo er stehen blieb.
- **abbrechen** — wirf ihn weg.

**Er wird weder heimlich fortgesetzt noch heimlich verworfen.** Das ist der
ganze Sinn: Eine verschobene Zeile kann bedeuten, dass die Stelle, an der er
stand, jetzt etwas anderes tut. Diese Entscheidung gehört dem Menschen, nicht
der Laufzeit. Sein Stapel bleibt so lange erhalten, wie er sich auflösen lässt,
damit du siehst, wo er stand, bevor du wählst.

Hat sich das Programm so stark geändert, dass die Stelle gar nicht mehr
existiert, gibt es nichts zu wählen: Der Ablauf scheitert und sagt es, statt zu
raten. Die zuletzt Gescheiterten bleiben zum Nachsehen in der Liste stehen,
rot, mit dem Grund dahinter.

## Wie ein Ablauf anfängt

Drei Wege, und keiner davon ist eine Schleife:

1. **Ein `on`-Block**, wenn sein Ereignis eintritt — aus der Welt
   (`redstone_changed`, `device_online`, `device_offline`, `device_changed`)
   oder aus einem `emit` im eigenen Programm.
2. **Ein Knopf auf einer Anzeige.** Er darf ausdrücklich etwas anstoßen, das
   wartet.
3. **Ein Aufruf aus einem Ablauf, der schon läuft.** Die gerufene Funktion darf
   selbst warten, ohne dass der Rufende davon wissen muss.

Was ein wartender Ablauf gerade tut, steht im Reiter **Netz** hinter seinem
Namen: `zyklus — wartet auf Fertig`, `zyklus — schläft`, `zyklus — wartet auf
ein freies Rechenwerk`.
