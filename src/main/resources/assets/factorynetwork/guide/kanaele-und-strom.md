---
navigation:
  title: Kanäle und Strom
  position: 40
---

# Kanäle und Strom

Ein Netz hat zwei Grenzen, und man stößt an beide. Die eine ist der Platz auf
dem Kabel, die andere der laufende Verbrauch. Sie fühlen sich verschieden an:
Ein fehlender Kanal ist ein einzelnes Gerät, das stumm bleibt. Fehlender Strom
ist das ganze Netz auf einmal.

## Kanäle

Ein Kabel ist ein Bündel von Drähten. **Ein gewöhnliches Kabel trägt 16, ein
dichtes 64.**

Die Regel dazu ist kurz und hat eine Folge, die man beim ersten Mal übersieht:
**Jedes Gerät zieht auf seinem ganzen Weg zum Controller einen Kanal ab.**
Nicht nur an seinem eigenen Kabelstück, sondern an jedem Stück dazwischen.
Zwanzig Kisten am Ende eines langen Strangs belegen also zwanzig Kanäle auf
der ganzen Strecke — und das siebzehnte Gerät bekommt nichts mehr, obwohl bei
ihm hinten reichlich Platz wäre.

### Was einen Kanal kostet

Was am Netz **etwas tut**, kostet einen:

- **Connector** — 1
- **Laufwerk** — 1
- **Serverschrank** — 1, auch wenn er zwei Blöcke hoch ist

Was nur weiterleitet oder nur mitliest, kostet nichts:

- **Kabel** — 0
- **Router** — 0
- **Display** — 0. Eine Wand aus zwölf Tafeln soll kein halbes Netz
  auffressen; sie kostet Strom, und das ist der Preis.

Halbe Kanäle gibt es nicht. Ein Kanal ist eine Leitung.

### Wer bei Knappheit gewinnt

**Das nähere Gerät.** Bei gleicher Entfernung das in der früheren
Himmelsrichtung. Diese Regel steht fest, damit „warum ist dieses Gerät
offline" überhaupt zu beantworten ist.

**Ohne Kanal keine Wirkung.** Ein Connector ohne Kanal hängt sichtbar im Netz
und ist trotzdem nicht ansprechbar. Ein Laufwerk ohne Kanal lagert nichts, ein
Serverschrank ohne Kanal rechnet nicht.

### Was du tun kannst, wenn es eng wird

- **Ein dichtes Kabel legen**, wenigstens auf dem gemeinsamen Stück zum
  Controller. Vier mal so viel, und die vollen Stücke sind fast immer die
  vorderen.
- **Auf eine andere Controllerseite gehen.** Der Controller hat sechs Seiten,
  an jeder hängt ein eigener Strang. Sechs dichte Kabel sind 384 Kanäle — die
  Grenze, die man wirklich trifft, ist die je Kabel, nicht die des Netzes.
- **Farbe benutzen.** Zwei Kabel verschiedener Farbe laufen aneinander vorbei,
  ohne sich zu sehen. So gehen zwei getrennte Stränge durch dieselbe Wand,
  jeder mit eigenen Kanälen. Die neutrale Farbe verbindet sich mit allem.
- **Einen Router setzen.** Jede seiner vier Bahnen trägt so viel wie ein
  dichtes Kabel, und eine Kreuzung kostet dadurch nichts.

### Wo du es abliest

- **Rechtsklick auf den Controller** — er nennt die Geräte, die Kabel, die
  unbenannten und die **ohne freien Kanal**. Das ist die erste Stelle, an der
  man nachsieht.
- **Am Kabel und am Connector** steht es im Tooltip, wenn Jade installiert
  ist: je Kabel die Belegung, am Connector, welcher der vier Zustände vorliegt.
- **Der Netzanalysator** zeichnet die Stränge durch Wände hindurch, solange du
  ihn in der Hand hältst.

## Strom

Strom geht in den **Controller**, nicht in die einzelnen Blöcke. Er nimmt
Forge Energy an, wie jede Maschine im Pack — vom Kabel eines fremden
Generators, oder von der Brennkammer, die ihren Strom aktiv hineinschiebt. Er
puffert 20 000 FE und nimmt bis zu 2 000 FE je Tick auf.

Das Netz **verbraucht** Strom für seine eigene Bereitschaft, **gibt** ihn an
Maschinen ab, wenn ein Programm das sagt, und **hält** ihn in Energiezellen
vor. Die drei Abschnitte dazu stehen unten.

### Was ein Netz verbraucht

Alles in FE je Tick:

- **Controller** — 4
- **Connector** — 1
- **Laufwerk** — 1, und je eingesetzter Zelle noch einmal 1
- **Serverschrank** — 1, und je **laufendem** Einschub noch einmal 2
- **Display** — 1
- **Router** — 1
- **Kabel** — 0

Ein kleines Netz mit vier Connectoren, einem Laufwerk mit einer Zelle und
einem Schrank mit einem fertigen Einschub zieht damit 13 FE je Tick. Eine
Brennkammer liefert 40.

Zwei Dinge, die daraus folgen:

**Gezahlt wird für Bereitschaft, nicht für Arbeit.** Ein Worker, der einen
Ofen füttert, kostet keinen Deut mehr als einer, der wartet. Das ist
absichtlich grob: Wer eine Anlage plant, will eine Zahl, die stillsteht.

**Kabel kosten nichts.** Sonst bestimmte die Länge deiner Leitung den
Verbrauch und nicht das, was daran hängt.

### Wenn es nicht reicht

**Aus heißt aus.** Reicht der Vorrat nicht für den Bedarf, geht das Netz aus,
und dann steht alles: Worker, Abläufe, Speicherzugriff.

Verloren geht dabei nichts. Ein Ablauf, der zwischen zwei Schritten steht,
hält keine Gegenstände; er friert ein und läuft weiter, wo er war. Ereignisse,
die während des Stillstands eintreffen, bleiben liegen und kommen an, sobald
das Netz wieder läuft.

**Eine Frist läuft dagegen weiter.** Ein `await` mit `timeout 30s`, das eine
Stunde ohne Strom stand, nimmt beim Aufwachen seinen `else`-Zweig. Die Frist
ist eine Aussage über die Welt, nicht über die Rechenzeit.

### Hochfahren

Kommt der Strom zurück, braucht das Netz **drei Sekunden**, in denen es schon
zieht und noch nichts tut. Ohne diese Zeit wäre ein Stromausfall ein Flackern,
das niemand bemerkt.

Dazu kommt eine Wiederanlaufschwelle: Das Netz kehrt erst zurück, wenn genug
beisammen ist, um das Hochfahren zu überstehen **und danach noch zu laufen**.
Eine Versorgung knapp unter dem Bedarf erzeugte sonst ein Blinken im
Halbminutentakt, das wie ein Fehler aussieht statt wie zu wenig Strom.

Wenn dein Netz also nach dem Einschalten der Brennkammer nicht sofort
anspringt: Das ist kein Fehler, es sammelt.

### Strom an Maschinen abgeben

Ein Connector versorgt seine Maschine **nicht** von selbst. Ohne Code fließt
kein Strom — dieselbe Härte wie überall in dieser Mod.

```
worker versorgung {
    from network
    to schmelze
    filter power
    rate 200 per 1t
    priority 1
}
```

`filter power` steht ohne Doppelpunkt, denn Strom hat keine Sorten: Es gibt
nur FE.

**Eine Seite ist immer `network`.** Andersherum geht es auch — `from generator
to network` holt aus einem fremden Speicher ins Netz. Von einer Maschine
direkt in die andere zu schieben wäre dagegen eine Leitung ohne Kabel; dafür
gibt es Kabel.

**Es gibt keine Kabelgrenze.** Was fließt, begrenzen die Rate deines Workers,
der Vorrat des Netzes und das, was die Maschine annimmt. Ein dünnes Kabel
trägt so viel Strom wie ein dichtes — Kabelstufen entscheiden allein über
Kanäle.

Wird der Vorrat knapp, gilt die **`priority`**: kleine Zahl zuerst. Wer
drankommt, bekommt seine ganze Rate; wer leer ausgeht, geht leer aus. Das ist
Absicht. Bekämen alle etwas, liefen bei Knappheit sämtliche Maschinen langsam
und keine würde fertig — der Zustand, in dem man am längsten sucht, warum
nichts vorangeht.

Ein Worker, der nichts abbekommt, steht als `WAITING_TARGET` da — derselbe
Zustand wie ein Gegenstands-Worker vor einer vollen Kiste.

### Mehr Vorrat: Energiezellen

Die 20 000 FE im Controller sind ein Puffer, kein Speicher. Wer mehr halten
will, steckt **Energiezellen** ins Laufwerk — dieselben Regale, in denen auch
Gegenstands- und Flüssigkeitszellen stecken. Es gibt sie in vier Größen von
64k bis 4096k FE.

Der Vorrat des Netzes ist danach der Puffer **plus** alles, was in den Zellen
liegt. Gefüllt wird der Puffer zuerst, geleert auch — die Zellen sind die
Reserve.

Eine eingesetzte Energiezelle kostet 1 FE je Tick wie jede andere Zelle. Ein
Akku, der Strom kostet, um Strom zu halten, ist ein Akku mit Selbstentladung.

### Wo du es abliest

Im Terminal, Reiter **Netz**, ganz oben in den Abläufen — und in der
Statuszeile jedes Reiters. Drei Zeilen sind möglich:

- `Strom: 13 FE/t · Abgabe 40 · 18.400 von 84.000` — es läuft.
- `Fährt hoch — …` — die drei Sekunden.
- `Kein Strom — das Netz steht (13 FE/t nötig)` — in Rot.

Die mittlere Zahl ist, was gerade an Maschinen abfließt, gemittelt über eine
Sekunde. Sie zählt **nicht** zum Bedarf davor: Was das Netz durchreicht, ist
nicht, was es für sich braucht — sonst schaltete sich ein Netz ab, weil es zu
viel liefert.

Am Controller steht dasselbe im Tooltip, und an der Brennkammer, ob sie
brennt, leer ist oder ihren Vorrat nicht loswird.
