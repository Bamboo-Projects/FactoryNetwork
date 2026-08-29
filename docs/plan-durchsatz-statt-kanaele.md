# Durchsatz statt Kanäle — Umsetzungsplan

**Auftrag:** „ich überlege die ganze Channel sache weg zu lassen … ich will
auch nicht AE2 nachmachen" (29.08.), gefolgt von „okay go".

**Ziel:** Die Grenze am Kabel ist nicht mehr, *wie viele Geräte* daran hängen,
sondern *wie viel je Tick hindurchgeht*.

---

## Warum

**In `entscheidungen.md` steht wörtlich: „Das Vorbild ist Applied
Energistics."** Die Kanäle waren ein bewusstes Zitat, und genau das soll weg.

Der tiefere Grund ist aber nicht das Zitat, sondern die Passung: **Bei AE2 ist
die Form des Netzes das Spiel** — wo das dicke Kabel liegt, wo man teilt.
**Hier ist der Code das Spiel.** Ein Programm sieht nie, welchen Weg ein Kanal
nimmt; es sieht nur, ob `move 64 per 1t` durchkommt. Eine Grenze, die das
Programm nicht spürt, ist eine Grenze am falschen Ort.

Durchsatz spürt es. `rate 64 per 1t` gegen ein Kabel, das 32 trägt, ist eine
Zahl im Code gegen eine Zahl in der Welt — und die Antwort steht im Protokoll,
nicht in einem Netzdiagramm.

---

## Verifizierter Bestand

| Was | Wo |
|---|---|
| Kanalkosten je Gerätetyp | `network/Channels.java` — neun Zeilen |
| Kapazität je Kabel: 16 / 64 | `CableBlock.CHANNELS_THIN/DENSE`, gelesen in `capacityAt` |
| Die Zuteilung samt Wegewahl | `FactoryGraph.assignChannels`, ~50 Zeilen |
| „Kein Kanal" als Gerätezustand | `starved`, angezeigt in Analysator und Jade |
| `rate N per T` gibt es in der Sprache schon | `TokenType:72`, `WorkerRuntime.batchOf` |
| Ein Worker begrenzt seinen Takt selbst | `WorkerRuntime:289,329` |
| Zwölf Dateien rechnen mit Kanälen | gemessen per grep |

**Der Schnitt ist kleiner als die Zwölf vermuten lassen.** Der Kern sind drei
Stellen: `Channels`, `capacityAt`, `assignChannels`. Der Rest ist Anzeige.

**Und die Wegewahl bleibt.** `assignChannels` tut zwei Dinge: einen Weg zum
Controller finden und prüfen, ob er Platz hat. Das Erste bleibt — ohne Weg
gibt es kein „hängt am Netz". Nur die Prüfung fällt.

---

## Die neue Regel

**Jedes Kabelstück trägt eine Menge je Tick.** Ein Worker, der Waren bewegt,
belegt auf seinem ganzen Weg zum Controller so viel davon, wie er tatsächlich
bewegt. Reicht es nicht, bewegt er weniger — er fällt nicht aus.

**Das ist der zweite Unterschied zu den Kanälen, und der wichtigere:** Kein
Kanal hieß *aus*. Zu wenig Durchsatz heißt *langsamer*. Ein Netz an der Grenze
arbeitet weiter, nur zäher — und das sieht man an den Zahlen im Protokoll,
statt an einem Gerät, das plötzlich nichts mehr tut.

**Zahlen (Vorschlag, eine Zeile zu ändern):**

| | Durchsatz je Tick |
|---|---|
| Gewöhnliches Kabel | 64 |
| Dichtes Kabel | 512 |
| Router, Gateway, Brücke | wie dicht |

Ein gewöhnliches Kabel trägt damit einen Stapel je Tick — genug für jede
einzelne Leitung, zu wenig für eine Hauptader, an der zehn Worker ziehen. Das
dichte trägt acht Stapel: der Unterschied, für den man es baut.

**Was keinen Durchsatz braucht:** Anzeigen (sie lesen), Sendemasten (sie
funken), Laufwerke und Schränke (sie *sind* das Ziel, sie bewegen nichts).
Genau die Geräte, die heute schon null oder wenig Kanäle kosten.

---

## Die Aufgaben

- [x] **1. Der Durchsatz am Kabel.** `Throughput.java` löst `Channels.java`
      ab: eine Zahl je Kabelart, gelesen wie `capacityAt`. Prüflauf: Ein
      dichtes trägt mehr als ein gewöhnliches.
- [x] **2. Die Zuteilung wird zur Wegewahl.** `assignChannels` verliert seine
      Kapazitätsprüfung und heißt `assignPaths`. Jedes erreichbare Gerät
      bekommt einen Weg. **`starved` fällt hier** — und mit ihm der einzige
      Grund, aus dem ein Gerät heute stumm bleibt.
- [x] **3. Der Weg begrenzt den Takt.** Ein Worker fragt vor dem Bewegen, wie
      viel sein Weg noch hergibt, und nimmt das Kleinere von beidem. Die
      Belegung läuft je Tick und wird am Tickende zurückgesetzt.
- [ ] **4. Die Anzeige.** Analysator und Jade zeigen Auslastung statt
      Kanalzahl: „340 von 512 je Tick". Der Zustand `STARVED` wird zu
      `CONGESTED` — nicht tot, sondern eng.
- [x] **5. Aufräumen.** `Channels.java` weg, `channelCost` am Connector weg,
      die Doku nachziehen. **Und `entscheidungen.md` bekommt den Grund**,
      warum das Zitat gefallen ist.

---

## Was dabei bewusst kaputtgeht

**Bestehende Netze ändern ihr Verhalten.** Ein Netz, in dem heute Geräte
mangels Kanal stumm sind, wird sie danach bedienen — langsamer vielleicht,
aber sie laufen. Das ist die gewollte Richtung, sollte aber im Bericht stehen,
damit es nicht wie ein Fehler aussieht.

**Der Analysator verliert seine halbe Existenz.** „Warum hängt dieses Gerät
nicht dran" beantwortet er künftig fast immer mit „tut es doch". Was bleibt,
ist die Frage nach dem Engpass — eine andere Frage, und eine, die eine andere
Ansicht verdient. **Das ist keine Nachtarbeit** und gehört auf die Liste für
den User.

**Der Anbau verliert seinen Zweck**, wenn Kabelseiten nicht mehr knapp sind.
Er hat noch keinen neuen — das gehört in dieselbe Entscheidung wie der
Multiblock (`controller-multiblock.md`) und wird hier nicht mitentschieden.
