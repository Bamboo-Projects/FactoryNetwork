---
navigation:
  title: Fehlersuche
  position: 50
---

# Fehlersuche

Mein Netz tut nichts. Diese Liste geht vom Häufigsten zum Seltensten; arbeite
sie von oben nach unten ab, dann bist du meist nach dem dritten Punkt fertig.

Vorweg der wichtigste Satz, weil er die Hälfte aller Suchen erspart: **Dass
ein Worker nichts bewegt, ist kein Fehler.** Die Quelle kann leer sein und das
Ziel voll — beides ist Normalbetrieb und meldet sich nicht.

## 1. Läuft das Netz überhaupt?

### Ist das Programm angekommen?

Nach **Strg+Eingabe** steht im Terminal entweder „Übernommen: … Worker laufen"
oder „Nicht übernommen — … Meldungen. **Das alte Programm läuft weiter.**"

Der zweite Fall ist der, den man übersieht: Deine Änderung ist nicht wirksam,
aber die Fabrik läuft trotzdem — mit dem Stand von vorher. Die Meldungen
stehen im Editor an der Zeile, an der sie entstanden sind.

### Steht ein Serverschrank?

Ohne einen **laufenden** Einschub rechnet das Netz nicht, kein Programm wird
übernommen, und kein Worker rührt sich. Im Reiter **Netz** steht dann rot
„Kein Serverschrank — das Netz rechnet nicht".

Ein Einschub läuft erst mit **Gehäuse, Rechenwerk, Speicher und Datenträger**.
Zwei von dreien tragen nichts, nicht anteilig. Ein unvollständiger Einschub
ist **gelb** — an der Front und im Fenster.

### Ist Strom da?

Im Reiter **Netz** steht der Zustand. „Kein Strom — das Netz steht" heißt
genau das: Worker, Abläufe und Speicher stehen still. Nach dem Einschalten
einer Quelle springt das Netz nicht sofort an — es sammelt erst genug, um das
Hochfahren zu überstehen und danach noch zu laufen, und braucht dann drei
Sekunden. Solange steht dort „Fährt hoch".

### Steckt eine Speicherzelle im Laufwerk?

Ohne Zelle lagert das Netz nichts, und alles, was `to storage` schreibt, hat
kein Ziel. An der Front des Laufwerks siehst du ohne Anklicken, was steckt.

## 2. Erreicht das Netz die Maschine?

### Hat der Connector einen Namen — und nur er?

Nimm die **Beschriftungspistole** in die Hand: Solange sie darin ist, schweben
die Namen über allen Connectoren in der Nähe. **Grün** heißt benannt, **grau**
unbenannt, **rot** doppelt vergeben.

Doppelt vergeben ist der bösere Fall: Dann sind **beide** unbrauchbar, weil
nicht zu entscheiden ist, welcher gemeint war.

### Kennt das Netz den Namen, den du getippt hast?

Der Editor prüft jeden Namen gegen das echte Netz. Ein Ziel, das niemand so
genannt hat, ist eine Warnung mit „meintest du …". Dasselbe gilt für
Anzeigen: `display leitstand` ohne eine Wand namens `leitstand` bleibt schwarz
— und der Hinweis stünde sonst auf der Tafel, die drei Räume weiter hängt.

**Strg+Klick** auf einen Namen springt zu seiner Erklärung. Kommt er aus der
Welt, wird der Block stattdessen markiert: ein Kasten darum und der Name
darüber, durch Wände sichtbar und eine halbe Minute lang. Das beantwortet die
Frage, welcher von vier Connectoren nebeneinander `furnace_2` ist.

### Ist noch ein Kanal frei?

**Rechtsklick auf den Controller.** Er nennt unter anderem, wie viele Geräte
ohne freien Kanal dastehen. Ein solches Gerät hängt sichtbar im Netz und ist
trotzdem nicht ansprechbar — man sucht dann einen Tippfehler, wo eine
Kapazitätsgrenze liegt.

Was hilft: ein dichtes Kabel auf dem gemeinsamen Stück, eine andere
Controllerseite, oder ein zweiter Strang in einer anderen Farbe. Ausführlich
steht das unter *Kanäle und Strom*.

### Zeigt der Connector auf die richtige Seite?

Das ist der stillste Fehler von allen: Der Worker läuft, bewegt nichts, meldet
nichts — denn „nichts bewegt" ist ja der Normalfall.

**Zeig im Editor auf einen Gerätenamen.** Das Terminal sagt dir, welche
Maschine dort steht, was sie an welcher Seite annimmt und was in ihren Fächern
liegt. Schon die Vorschlagsliste nennt es kurz:
`crusher_1 — Crusher · Gegenstände, Strom`.

Und wenn ein Worker Gegenstände an eine Seite schickt, die keine annimmt,
warnt der Editor beim Tippen — mit der Seite, an der es ginge:

*Der Connector „crusher_1" hängt oben — dort nimmt die Maschine keine
Gegenstände an. An Norden und Süden ginge es — häng den Connector dorthin.*

Die Vorderseite eines Connectors zeigt dorthin, wo du beim Setzen hingeklickt
hast. Umdrehen heißt: abbauen und neu setzen.

## 3. Oder wartet der Worker nur?

Im Reiter **Netz** steht hinter jedem Worker sein Zustand, und die Farbe sagt
schon, ob es einer ist, um den man sich kümmern muss.

- **RUNNING** — er bewegt gerade etwas. Grün.
- **IDLE** — nichts zu tun. Die Quelle ist leer, oder der Vorrat steht schon.
  Kein Fehler.
- **WAITING_TARGET** — das Ziel nimmt nichts mehr auf. Die Kiste ist voll, der
  Ofen hat seinen Stapel. Kein Fehler.
- **WAITING_CONDITION** — die Bedingung aus `when` trifft nicht zu. Genau
  dafür ist sie da.
- **HALTED** — hier musst du hinsehen. Ein Gerät fehlt, oder in der
  Deklaration steht etwas Unmögliches.

Die häufigsten zwei Gründe für **HALTED**:

- **`maintain` ohne `filter`.** „Halte einen Vorrat" ohne die Angabe, wovon,
  ist keine Zusage. Der Worker sagt es dazu.
- **Ein Gerät ist verschwunden.** Abgebaut, oder das Kabel gekappt.

Ein Connector in einem **nicht geladenen Chunk** ist dagegen kein Bruch: Der
Worker pausiert und läuft weiter, sobald der Chunk zurück ist.

### Ein Ablauf, der angehalten hat

Wartende Abläufe stehen im selben Reiter, mit zwei Knöpfen: **weiter** und
**abbrechen**. Wer den fehlenden Connector wieder setzt und „weiter" wählt,
macht an derselben Stelle weiter. Dieselbe Wahl bekommst du nach einem
Serverneustart.

Wichtig beim Warten auf eine Maschine: **`device_changed` heißt nicht
„fertig".** Es heißt, dass sich im Inventar des Geräts etwas getan hat. Ob das
Ergebnis vollständig ist, weiß von außen niemand — der Ausgang kann von vorher
gefüllt sein, und jede Mod zählt anders. Was „fertig" bedeutet, schreibst du
selbst dazu.

## 4. Und wenn es dann immer noch nichts tut

Die selteneren Ursachen, in dieser Reihenfolge:

- **Die Auswahl trifft in dieser Welt nichts.** `item:iron_dust` und
  `tag:c/ores` gibt es in einem großen Pack, in einer leeren Vanilla-Welt
  nicht. Die Laufzeit meldet dann, dass die Auswahl leer ist — das ist keine
  Fehlfunktion, sondern die Wahrheit über die Welt.
- **Das Kabel hat die falsche Farbe.** Zwei Kabel verschiedener Farbe laufen
  aneinander vorbei, ohne sich zu sehen. Der Netzanalysator zeigt durch Wände,
  was wirklich zusammenhängt. Entfärbt wird mit einem Wassereimer.
- **Der Router trennt.** Zwei Seiten auf verschiedenen Bahnen kreuzen sich
  berührungslos, „aus" heißt abgeklemmt. Die Ringe an seinen Flächen sagen es;
  Schleichen öffnet alle sechs Seiten auf einmal.
- **Das Programm passt nicht auf den Datenträger.** Im Reiter **Netz** steht
  „Programm: … von … Anweisungen", rot, wenn es zu viel ist. Ein zu großes
  Programm wird beim Übernehmen abgelehnt; fällt der Platz später weg, weil
  jemand einen Datenträger herauszieht, friert das Netz ein — es kürzt nie
  stillschweigend.
- **Das Terminal hängt nicht am Controller.** Es sucht ihn in der direkten
  Nachbarschaft, nicht über das Kabel. Dann meldet es „An diesem Terminal
  hängt kein Controller."

## 5. Ein Fertigungsauftrag steht still

Der Reiter **Fertigung** sagt es, und die Zeile darunter ist die Antwort:

- **„es fehlt: 2 Eichenstamm"** — genannt wird immer der **Grundstoff** und
  nie eine Zwischenstufe. Bretter kann das Netz selbst machen; Stämme muss
  jemand hinlegen.
- **„wartet auf einen Ofen im Netz"** — für dieses Rezept braucht es eine
  Maschine, und keine passende hängt am Kabel oder alle sind beschäftigt.
- **„wartet auf ofen_1 — hat er Brennstoff?"** — die Zutat liegt drin, aber es
  kommt nichts zurück. **Das Netz heizt nicht.** Ein Worker mit
  `to ofen_1.slots(1)` und `filter item:coal` löst es.
- **„kein Fabricator im Netz"** — für Werkbank- und Steinsägen-Rezepte braucht
  es einen.
- **„kein Rezept mehr"** — der einzige Grund, aus dem ein Auftrag wirklich
  scheitert: Sein Rezept ist aus dem Pack verschwunden.

Und wenn ein Auftrag über etwas aus einer **fremden Maschine** gar nicht erst
angenommen wird: Das Netz kennt ihr Rezept nicht und kann es auch nicht
lernen. Schreib es auf — `recipe … at … { in … out … }`, siehe *Fertigung*.

## 6. `chemical:` tut nichts

Zwei verschiedene Meldungen, und der Unterschied sagt dir, wo du suchen musst:

- **„Chemikalien brauchen Mekanism"** — die Mod liegt nicht im Pack. Das ist
  eine Auskunft über deine Modliste, nicht über diese Mod.
- Kommt nichts an, obwohl Mekanism läuft: **Prüf die Seite.** Eine
  Mekanism-Maschine hat eine Seitenkonfiguration, und das Netz hält sich
  daran. Der Connector muss an einer Seite hängen, die etwas herausgibt oder
  annimmt — steht dort „nichts", passiert nichts.

## Was der Editor sonst noch weiß

- **Zeigen auf ein Wort** erklärt, was dort erwartet wird, und bei einem
  Gerätenamen, was hinter dem Connector steht.
- **Die Formzeile über dem Cursor** zeigt die ganze Form der Anweisung, an der
  du gerade schreibst, mit der aktiven Stelle hervorgehoben.
- **Strg+Leertaste** erzwingt die Vorschläge, **Tab** übernimmt einen.
- **F1** öffnet die Griffliste mit allen Tasten.

## Und in VS Code

Die Fehler stehen dort auch — dieselben, vom selben Übersetzer. Das Spiel
schreibt sie neben die Programmdateien, und die Erweiterung trägt sie ein;
eine Sekunde nach dem Speichern stehen sie da.

Dasselbe gilt für die **Gerätenamen**: Sie stehen in keiner Datei, sondern
kommen aus der Beschriftungspistole — hinter `from` und `to` schlägt die
Erweiterung sie trotzdem vor.

Beides braucht Zugriff auf den Ordner neben der Welt. Im Einzelspieler ist das
dein eigener Rechner; auf einem fremden Server geht es nicht.
