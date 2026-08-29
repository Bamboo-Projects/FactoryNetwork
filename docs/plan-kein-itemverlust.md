# Bei vollem Lager fällt nichts auf den Boden — Umsetzungsplan

**Auftrag:** „was passiert wenn das System voll ist vom speicherplatz her. die
items dürfen dann nicht einfach in die welt droppen das ist das dümmste was
passieren kann!" (30.08.)

**Recht hast du.** Ein Gegenstand auf dem Boden verschwindet nach fünf
Minuten, und der Fall trifft genau dann, wenn niemand zusieht.

---

## Der Befund

**`WorkerRuntime:446` nimmt heraus, bevor es fragt.**

```java
ItemStack taken = handler.extractItem(slot, wanted, false);   // erst nehmen
long rest = storage.insert(taken);                            // dann fragen
if (rest > 0) {
    ItemStack zurueck = insertInto(handler, ...);             // zurücklegen
    if (!zurueck.isEmpty()) {
        dropped.add(zurueck);                                 // ... oder fallen lassen
    }
}
```

**Wann es zuschlägt:** Das Lager ist voll, *und* die Quellkiste hat den Platz
zwischenzeitlich verloren — ein zweiter Worker hat sie aufgefüllt, oder der
Stapel war der letzte freie Platz. Selten. Und deshalb gefährlich: Wer es
einmal trifft, sucht die Ursache nie.

**Dasselbe Muster in `WorldHost:388`** — der `move`-Befehl der Sprache nimmt
ebenso zuerst.

**Und es gibt schon eine Antwort im Code, die niemand ruft:**
`NetworkStorage.room(key, wanted)` sagt, wie viel hineinpasst. Sie entstand
für Gase, weil die sich nicht zurücklegen lassen — für Gegenstände fragte
bisher niemand.

## Die Regel

**Erst fragen, dann nehmen.** Wer nichts unterbringen kann, nimmt nichts
heraus. Ein Worker an einem vollen Lager steht still und meldet das — er
verliert nichts.

**`room` zählt heute nur die Zellen** — nicht die fremden Inventare hinter
einem Speicherbus. Als Vorsicht war das gedacht, als Vorprüfung ist es ein
Fehler: Sind die Zellen voll und hat nur noch ein Bus Platz, antwortet `room`
mit null, und der Worker nähme nichts mehr. Das Netz stünde still, obwohl es
Platz hat.

**Also wird `room` erst fertig gebaut.** Ein Speicherbus kann sehr wohl
antworten, ohne etwas zu tun: `insertItem(slot, stack, true)` ist genau das
Probieren, das der Kommentar dort für unmöglich hält. Der Bus bekommt ein
`room`, das simuliert, was sein `insert` täte.

> **Nachgetragen am 30.08.:** Der ursprüngliche Plan wollte `room` nur rufen.
> Dass sie unvollständig ist, fiel erst beim Lesen auf.

## Die Aufgaben

- [x] **1. Der Prüflauf zuerst.** Laufwerk mit der kleinsten Zelle randvoll,
      Kiste mit Diamanten, Worker darauf. Danach: nichts auf dem Boden, und
      Kiste plus Lager ergeben zusammen wieder vierundsechzig.
      **Die Summe ist die eigentliche Zusicherung** — „nichts gedroppt" allein
      übersieht ein Item, das im Nirgendwo verschwindet.
- [x] **2. `room` kennt auch die Busse.** `StorageBus.room` simuliert sein
      eigenes `insert`; `NetworkStorage.room` zählt es dazu.
- [x] **3. Der Worker fragt vorher.** `deviceToStorage` begrenzt seinen Griff
      auf `storage.room(...)`.
- [x] **4. Der `move`-Befehl auch.** Dieselbe Änderung in `WorldHost`.
- [x] **5. `dropped` fällt — anders als gedacht.** Der Auffangkorb bleibt,
      nur sein Ausgang ist ein anderer: Der Controller *verwahrt* jetzt, statt
      zu werfen, und bietet jeden Tick erneut an. Ein Weg, der nie benutzt
      wird, kostet nichts; einer, der wirft, kostet die Ware.
- [x] **6. Die Meldung.** „Der Netzspeicher ist voll" steht schon da. Sie
      gehört auch ins Protokoll, nicht nur in die Statuszeile eines Workers,
      den gerade niemand ansieht.

## Was dabei herauskam

**Der Prüflauf war zuerst zahnlos.** Mit einer Kiste als Quelle rettet der
Rückweg alles: Was das Lager nicht nimmt, geht dorthin zurück, wo es herkam.
Erst ein **Ofen** als Quelle traf den Fall — von der Seite gibt er den
Brennstoffplatz her und nimmt ihn nicht wieder an. Dann waren
vierundsechzig Diamanten spurlos weg, nicht einmal als Gegenstand am Boden.

**Die Fertigung war doch betroffen.** Oben stand, sie könne nichts verlieren,
weil sie nur zurücklegt, was sie genommen hat. Falsch: Sie lagert
**Ergebnisse** ein, und was nicht hineinpasste, fiel zu Boden
(`ControllerBlockEntity:955,1371`). Beide Stellen verwahren jetzt.

**`retryHeldBack` stand im falschen Block.** Erst hing es hinter
`if (!program.workers().isEmpty())` — ein Netz ohne Worker hätte Verwahrtes
nie zurückgegeben. Der Prüflauf hat es gefunden.

## Was ich dabei nicht angefasst habe

**Die popResource-Aufrufe beim Blockabbau** (Kabel, Laufwerk, Presse,
Serverschrank, Brücke, Schraubenschlüssel). Dort steht der Spieler daneben
und sieht, was herausspringt — das ist gewöhnliches Minecraft und kein
stiller Verlust.

**Der Umschalt-Klick im Terminal** lässt seit dem 29.08. liegen, was nicht
hineinpasst. Er ist der Beleg, dass die Regel trägt.
