# Geräteerkennung — Entwurf

Was der Editor über die Maschine hinter einem Connector wissen soll, woher er
es bekommt, und wo die Grenze des technisch Möglichen verläuft.

Stand: 2026-08-24

Setzt um, was `umsetzung.md` unter „Fehlt" als Punkt 1 führt.

---

## 1. Anlass

Im Editor steht `crusher_1`, und der Editor weiß davon zwei Dinge: den Namen
und die Koordinate. Was dort in der Welt steht, was es annimmt und über welche
Seite — nichts davon. Die Folge ist eine Klasse von Fehlern, die man nur durch
Ausprobieren findet:

- Der Connector hängt an einer Seite, an der die Maschine gar nichts annimmt.
  Der Worker läuft, bewegt nichts, und meldet nichts, weil „nichts bewegt" der
  Normalfall ist (`sprache.md` §6: *„Weniger als gewünscht ist normal, `0`
  auch"*).
- Ein Gerät nimmt Flüssigkeiten und keine Gegenstände, und das Programm
  schickt Gegenstände.
- Die Maschine hat drei Slots, und welcher der Eingang ist, steht nirgends.

Alle drei sind vom Editor aus beantwortbar, weil der Server die Auskunft hat.
Er gibt sie nur nicht weiter.

---

## 2. Was erkannt wird

Je Connector eine Beschreibung der Maschine dahinter — im Code
`DeviceProfile`.

### Identität

Der `descriptionId` des Blocks, also der Übersetzungsschlüssel, nicht der
fertige Text. Der Client rendert daraus „Crusher" in seiner eigenen Sprache;
ein auf dem Server übersetzter String wäre auf einem englischen Server für
einen deutschen Spieler falsch. Dazu der Namensraum des Blocks, damit
erkennbar bleibt, aus welcher Mod das Gerät stammt — bei vier Mods mit einem
„Crusher" ist das die eigentliche Auskunft.

### Seiten

Geprobt werden **alle sechs Richtungen** des Nachbarblocks, dazu der
seitenlose Zugang (`side = null`).

Der seitenlose gehört zwingend dazu: Manche Maschinen bieten ihre Fähigkeit
ausschließlich so an. Wer nur die sechs Richtungen probt, meldet für genau
diese Geräte „nimmt nichts an" — und das ist die schlechteste Sorte Fehler,
weil sie plausibel aussieht.

Je Zugang wird festgehalten, ob es dort einen Item-Handler, einen Tank oder
einen Energiespeicher gibt. Die drei Fähigkeiten sind im Projekt schon in
Gebrauch: `Capabilities.ItemHandler.BLOCK` und
`Capabilities.FluidHandler.BLOCK` in `ConnectorBlockEntity`,
`Capabilities.EnergyStorage.BLOCK` in `CreativeSourceBlock`.

### Slots

Die Handler sind **pro Seite eigene Instanzen** — Slot 0 im Norden muss nicht
Slot 0 im Süden sein. Das Modell ist deshalb Seite → Handler → Slots und
nicht eine Slotliste mit einer Richtungsspalte.

Seiten, die dieselbe Handler-Instanz liefern, werden zusammengefasst. Sonst
steht sechsmal dasselbe da:

```
Norden, Süden, Osten, Westen: 3 Slots
Oben: 1 Slot
```

Flüssigkeiten und Energie sind analog, aber anders geformt: `IFluidHandler`
hat Tanks statt Slots, `IEnergyStorage` hat gar keine Unterteilung, nur
Inhalt, Fassungsvermögen und die beiden Flaggen „nimmt auf" und „gibt ab".

### Die angeschlossene Seite

Das Profil markiert, welche Richtung der Connector tatsächlich benutzt
(`ConnectorBlock.machineSide`). Daraus fällt die nützlichste Auskunft des
ganzen Entwurfs ab:

> Dein Connector hängt oben — dort ist kein Item-Handler. Norden und Süden
> hätten einen.

---

## 3. Die Grenze: was ein Slot annimmt

**„Was nimmt diese Maschine an" lässt sich nicht aufzählen.** Es gibt in
NeoForge keine API dafür. `isItemValid(slot, stack)` beantwortet nur die Frage
nach einem konkreten Gegenstand, und gemoddete Maschinen antworten darauf
lax — ein Ofen-Eingang nimmt oft alles an und prüft das Rezept erst beim
Verarbeiten. Eine Liste „nimmt an: Eisenerz, Golderz, …" wäre also entweder
falsch oder erfunden.

Was geht, ist die Frage umzudrehen: für **konkrete Kandidaten** einen
Einfügeversuch simulieren, `insertItem(slot, stack, true)`. Das ist das
Muster, mit dem `WorkerRuntime` ohnehin arbeitet, und es beantwortet die
Frage so verlässlich, wie die Maschine selbst sie beantworten kann.

**Die Kandidaten sind die `item:`-Literale aus dem Entwurf.** Der Entwurf
liegt seit dem Serverumbau auf dem Server; die Gegenstände darin sind die,
über die man sich gerade fragt, ob die Maschine sie nimmt. Es ist ein Dutzend,
nicht zwanzigtausend — die Probe kostet nichts.

Verworfen wurden:

- **Der Inhalt des Netzspeichers als Kandidatenmenge.** Ebenfalls billig, aber
  er trifft nicht die Frage. Wer `item:iron_ore` tippt, will über Eisenerz
  Bescheid wissen, nicht über die zwölf Sorten, die zufällig im Lager liegen.
- **Die Gegenstandsregistry.** Zwanzigtausend Einträge mal Slots mal Seiten,
  bei jedem Öffnen des Terminals. Nicht diskutabel.

**Unabhängig von der Probe wird die Struktur immer gemeldet:** ob ein Slot
überhaupt aufnimmt und ob er hergibt, ermittelt aus einem Einfügeversuch mit
dem, was drin liegt, und einem simulierten Auszug. Das ergibt ohne jeden
Kandidaten schon:

```
Slot 0: nimmt auf
Slot 1: gibt ab
```

und mit den Kandidaten aus dem Entwurf:

```
Slot 0: nimmt auf — iron_ore passt, iron_ingot nicht
Slot 1: gibt ab
```

Diese Trennung ist wichtig, weil sie ehrlich bleibt: Die Struktur ist eine
Tatsache, die Annahme-Probe ist eine Stichprobe. Sie darf nie so aussehen, als
wäre sie vollständig.

---

## 4. Transport

Zwei Kanäle, weil es zwei Sorten Auskunft sind.

### Struktur — beim Öffnen

Identität, Seiten, Slotanzahlen ändern sich nur, wenn jemand die Maschine
austauscht. Sie reisen im `NetworkStatePacket` mit, das laut seinem eigenen
Javadoc beim Öffnen geht und nicht laufend.

`NamedPlace` bekommt dafür **kein** weiteres Feld. Daneben tritt eine eigene
Liste von `DeviceProfile`: Sonst trüge jede Anzeigewand Slotfelder mit, die
sie nie füllt.

Der Aufwand fällt einmal beim Öffnen an: je Connector sieben Zugänge mal drei
Fähigkeiten, dazu die Slots der gefundenen Handler. Bei vierzig Connectoren
sind das rund achthundert Capability-Abfragen — die sind in NeoForge
zwischengespeichert und kosten in dieser Größenordnung nichts.

**Die Annahme-Probe aus Abschnitt 3 läuft hier nicht mit.** Sie gehört in die
Antwort auf Anfrage, aus zwei Gründen. Der wichtigere: Ihre Kandidaten sind
die Literale des Entwurfs, und der Entwurf ändert sich beim Tippen — eine
Sekunde nach dem letzten Anschlag steht er auf dem Server. Wer nach dem Öffnen
`item:iron_ingot` schreibt und dann fragt, ob die Maschine das nimmt, bekäme
eine Probe gegen den Entwurf von vorhin, also für genau den Gegenstand keine
Antwort, nach dem er fragt. Der zweite Grund: Kandidaten mal Slots mal
Connectoren im Öffnen-Paket wächst mit drei Faktoren gleichzeitig, während die
Anfrage immer nur ein Gerät betrifft.

**Geräte in nicht geladenen Chunks werden übergangen.** Das Profil sagt dann
nur, dass die Maschine nicht erreichbar ist — nicht, dass sie nichts kann. Der
Unterschied ist derselbe wie bei `NetworkView.knowsNetwork()`: Nichts zu
wissen ist etwas anderes, als etwas Leeres zu wissen.

### Inhalt — auf Anfrage

Was gerade in den Slots liegt, wie voll der Tank ist, wie viel Energie da ist:
ein Anfrage-Antwort-Paar nach dem Muster von `StorageSnapshotPacket`.

Ausgelöst wird die Anfrage vom Zeigen: `wordAt` liefert das Wort unter dem
Zeiger, und steht es in der Connectorliste, ist es ein Gerätename. Gefragt
wird aber erst, wenn der Zeiger eine Viertelsekunde daraufsteht — sonst
schickt jede Mausbewegung über eine Zeile mit drei Namen drei Anfragen. Bis
die Antwort da ist, steht im Tooltip, was ohnehin schon bekannt ist:
Identität, Seiten, Slotanzahlen. Der Inhalt kommt dazu, wenn er eintrifft;
der Tooltip springt dabei um eine Zeile, und das ist besser als ein Tooltip,
der eine Viertelsekunde lang gar nicht da ist.

Die Antwort wird bis zum Schließen des Terminals behalten, aber bei jedem
neuen Zeigen auf dasselbe Gerät erneuert. Ein zweites Mal hinsehen heißt
meistens: nachsehen, ob es sich geändert hat.

Ausdrücklich **nicht** laufend übertragen. Bei vierzig Connectoren wäre das
Dauerverkehr für etwas, das man einmal ansieht — der Analysator macht das
anders, aber der ist ein Werkzeug, das man in der Hand hält, während sich das
Netz vor einem ändert.

Die Antwort ist auf 64 Slots gedeckelt, und **wenn gekürzt wurde, steht das
dabei**. Ein Fassregal mit zweihundert Fächern soll den Tooltip nicht
sprengen, aber auch nicht heimlich lügen.

Die Antwort trägt die Struktur gleich mit, und daneben die Annahme-Probe gegen
den Entwurf, der in diesem Moment auf dem Server liegt. Damit ist der Fall
„Maschine wurde ausgetauscht, während das Terminal offen ist" nebenbei
erledigt, ohne dafür einen eigenen Mechanismus zu bauen.

---

## 5. Im Editor

### Zeigen

Der dritte Fall neben Signatur und Meldung. Anlass, die Tooltip-Logik
zusammenzuziehen: Sie steht heute zweimal, in `CodeTabView.renderTooltip` und
in `CodeScreen`, beide Male gleich aufgebaut. Zwei Kopien mit drei Fällen
laufen auseinander; zwei mit zweien tun es noch nicht, aber der dritte ist
genau der Anlass.

### Vorschläge hinter `from` und `to`

Die Connector-Einträge in `Completions` haben heute ein leeres `detail`. Dort
gehört hinein, was die Maschine ist und kann:

```
crusher_1    Crusher · Gegenstände, Energie
```

Die billigste Stelle mit dem größten Effekt, weil sie in jeder Vorschlagsliste
steht, ohne dass jemand etwas dafür tun muss.

### Der Punkt

`Completions` kennt heute keine Stelle nach einem `.`. Sie kommt dazu und
bietet an, was die Sprache an einem Gerät wirklich hat: `online`, `name`,
`redstone()`, `count()` — jedes mit seiner Form daneben, wie alle Vorschläge
im Editor.

**Das sind vier Einträge, und für jedes Gerät dieselben.** Gerätespezifisches
gibt es nach dem Punkt erst, wenn die Mitglieder aus `sprache.md` §6
implementiert sind (siehe Abschnitt 8). Der Ort ist damit vorbereitet: Wenn
sie kommen, sind sie ein Eintrag in `Signatures` und nichts weiter.

### Warnung bei der falschen Seite

`NetworkView` bekommt Zugriff auf die Profile, `NetworkCheck` prüft damit die
Ziele in `worker`-Blöcken.

**Die Prüfung ist an die Ressourcenart gebunden.** Ein Worker bewegt
Gegenstände oder Flüssigkeiten, je nachdem, was sein `filter` meint; ein
Fluid-Worker mit Ziel Tank wäre nach einer item-blinden Regel fälschlich
gewarnt, und eine Warnung, die im Normalfall anschlägt, schaltet man ab. Also:
Item-Worker verlangt einen Item-Handler an der angeschlossenen Seite,
Fluid-Worker einen Tank.

Die Unterscheidung gibt es schon — `WorkerRuntime.isFluidWorker` liest die Art
aus dem Selector des Filters. Sie ist reine Arbeit am Syntaxbaum und kennt
kein Minecraft; sie wandert deshalb ins Sprachpaket, damit Prüfung und
Laufzeit dieselbe benutzen und nicht zwei Regeln entstehen, die auseinander
laufen können.

Die Prüfung gilt der angeschlossenen Seite und nicht „irgendeine Seite kann
das" — sonst schweigt sie genau in dem Fall, für den sie gebaut wird.

Über `NetworkView` und nicht nur im Client, weil sonst der Editor warnt und
das Übernehmen es durchwinkt. Die Trennung „Client beim Tippen, Server beim
Übernehmen" ist im Projekt schon so gebaut, und sie gilt auch für den, der
über den Ordner neben der Welt schreibt.

Warnung und nicht Fehler, wie alles in `NetworkCheck`: Eine Maschine, die man
erst morgen hinstellt, darf man heute schon ins Programm schreiben.

---

## 6. VS Code

Dort läuft kein Spiel, also gibt es keine Profile: kein Zeigen auf Geräte,
kein `detail` an Connectoren, keine Seitenwarnung.

Die Punkt-Vervollständigung geht, weil sie aus `Signatures` kommt und die
Tabelle ohnehin nach drüben erzeugt wird — der bestehende Sync-Test deckt sie
ab.

Das steht hier ausdrücklich, damit es nicht als Lücke gilt, die jemand später
für einen Fehler hält.

---

## 7. Tests

Die Erkennung selbst braucht eine Welt, die Sprachprüfung nicht. Die Trennung,
die das Projekt schon hat, bleibt:

- **`NetworkView` liefert Profile als Daten.** Ein Test setzt sie von Hand —
  dieselbe Welt aus Papier wie in `NetworkCheckTest`. Damit laufen die
  Warnungen bei der falschen Seite in Millisekunden statt in einer Minute.
- **Nur die Cap-Probe im Connector braucht einen GameTest**: eine Kiste
  hinstellen, den Connector daneben, prüfen, dass Seiten und Slots stimmen.

---

## 8. Bewusst nicht dabei

**Die Gerätemitglieder aus `sprache.md` §6.** `insert()`, `items()`,
`output()`, `send()`, `busy` sind spezifiziert und nicht implementiert; der
Interpreter kennt an einem Gerät heute `online`, `name`, `redstone()` und
`count()`. Sie zu bauen ist Arbeit an der Laufzeit — Interpreter,
`Interpreter.Host`, `WorkerRuntime` — und nicht am Editor. Eigener Schritt,
eigene Entscheidung.

**`busy` hat keine Quelle.** Es steht in `sprache.md` nur in Beispielen (§12,
§14), und es gibt keine Capability, über die eine fremde Maschine „ich
arbeite gerade" melden würde. Bevor es implementiert wird, ist zu klären,
woher der Wert kommen soll — geraten (Eingang voll und Ausgang leer?) wäre
schlechter als gar nicht.

**Laufende Zustandsübertragung.** Siehe Abschnitt 4.

**Die Seitenwarnung erreicht `move` nicht.** `NetworkCheck` läuft heute über
Deklarationen — Display, Worker, Group — und besucht keine Anweisungen. Damit
prüft es `move item:iron_ore from chest to crusher_1` grundsätzlich nicht,
auch nicht auf unbekannte Namen; die neue Warnung erbt diese Grenze.

Das ist ausdrücklich eine Grenze und keine Absicht: `move` ist im imperativen
Teil der häufigste Weg, ein Gerät anzusprechen, und dort wäre die Warnung
genauso richtig. Sie kostet aber einen Durchgang durch die Anweisungen, den es
noch nirgends gibt, und der gehört nicht in diesen Schritt — er würde
gleichzeitig die Namensprüfung für `move` mitbringen, und die ist eine eigene
Entscheidung mit eigenen Fällen (Schleifenvariablen, Multiblock-Namen).

---

## 9. Was danach offen bleibt

- Die Gerätemitglieder, und davor die Frage nach `busy`.
- ~~Ob die Annahme-Probe auch für Flüssigkeiten sinnvoll ist.~~ **Gebaut am
  25.08.** Dieselbe Umkehrung wie bei den Fächern, mit `fill(…, SIMULATE)` und
  den `fluid:`-Angaben aus dem Entwurf. Geprobt wird mit **einem Eimer**: Mit
  einem Millibucket sagt ein Tank oft ja, der in Wahrheit nur volle Eimer
  nimmt.
- Ob das Profil auch dem Analysator etwas zu geben hat. Er zeigt heute das
  Netz, nicht die Geräte daran.
