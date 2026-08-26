---
navigation:
  title: Der Netzanalysator
  position: 52
---

# Der Netzanalysator

Das Werkzeug für die Frage „warum kommt hinten nichts an". Es zeichnet dein
Netz als Linien und Würfel **durch Wände hindurch** — und das ist die eine
Eigenschaft, an der alles hängt: In einer gewachsenen Basis liegen die Kabel
hinter Blöcken, und ein Werkzeug, das nur zeigt, was ohnehin zu sehen ist,
hilft dir nicht.

## Einmal klicken, dann in der Hand behalten

Rechtsklick auf **irgendeinen** Teil des Netzes: ein Kabel, einen Connector,
den Controller. Das Werkzeug merkt sich das Netz.

Danach wird gezeichnet, solange du es in der Hand hältst. Du kannst
weglaufen, dich umdrehen, in den Keller gehen — das Bild bleibt und wird alle
paar Ticks nachgereicht.

Klickst du auf etwas, das zu keinem Netz gehört, sagt es das: *„Hier hängt
kein Netz."*

## Was die Farben bedeuten

**Die Strecken** — das sind die Kabel zwischen zwei Punkten — färben sich nach
ihrer Kanalauslastung:

| | |
|---|---|
| grün | Luft nach oben |
| gelb | wird knapp |
| **rot** | **voll — hier klemmt es** |

Rot ist die Antwort auf „warum kommt hinten nichts an". Ein Gerät zieht seinen
Kanal auf dem **ganzen Weg** zum Controller; wenn eine Strecke voll ist, fehlt
der Kanal weiter hinten, nicht dort, wo du gerade stehst.

**Die Würfel** sind die Punkte des Netzes:

| | |
|---|---|
| blau | der Controller |
| blasses Blau | ein Controller-Anbau |
| weiß | ein Gerät mit Namen |
| **gelb** | **ein Connector ohne Namen** — hängt im Netz, kostet seinen Kanal, ist im Code nicht ansprechbar |
| **magenta** | **zwei Geräte mit demselben Namen** — eines von beiden gewinnt, und du siehst nicht welches |
| **rot** | **kein freier Kanal** — dieses Gerät gehört nicht wirklich dazu |
| grün | ein Laufwerk |
| orange | ein Serverschrank |
| grau-violett | eine Anzeige |
| blassgrau | ein Router |

Die drei fett gedruckten sind die, wegen denen du das Werkzeug in die Hand
genommen hast.

## Rechtsklick auf einen Connector sagt mehr

Nicht am Würfel, sondern an der Maschine: Klick den Connector an, und über
deiner Werkzeugleiste steht, was dahinter geht.

```
brecher_1: Gegenstände · Fächer 0–26
```

Der Name, was die Maschine an dieser Seite kann, und wie viele Fächer sie hat.
Die Fachnummern sind dieselben, die `brecher_1.slots(3)` meint — gezählt ab
null, über das ganze Inventar.

**Warum das am Klick hängt und nicht am Würfel:** Die Frage „was kann diese
Maschine" stellst du ohnehin dort, wo du davorstehst. Beschriftungen an
tausend Würfeln durch Wände wären ein Buchstabensalat.

## Wonach du suchen solltest

In dieser Reihenfolge, weil sie den häufigsten Fehlern folgt:

1. **Rote Würfel.** Ein Gerät ohne freien Kanal ist im Netz und trotzdem
   nicht ansprechbar.
2. **Rote Strecken.** Die zeigen dir, *wo* der Kanal ausgeht — meist ein
   einfaches Kabel, wo ein dichtes hingehört.
3. **Gelbe Würfel.** Ein Connector ohne Namen kostet einen Kanal und bringt
   nichts. Beschriftungspistole drauf oder abbauen.
4. **Magenta.** Zwei Geräte mit demselben Namen sind der Fehler, den man am
   längsten sucht: Das Programm läuft, es tut nur am falschen Ort etwas.
5. **Wo die Linie aufhört.** Kein Würfel heißt kein Netz — dort ist das Kabel
   unterbrochen, oder es hat die falsche Farbe. Zwei Kabel verschiedener
   Farbe laufen aneinander vorbei, ohne sich zu sehen.

## Was es nicht tut

Es zeigt **keinen Bestand** und keine Fließrichtung. Es beantwortet die Frage
„gehört das zusammen und passt es durch", nicht „was läuft gerade". Für die
zweite gibt es den Reiter *Netz* im Terminal und das *Log*.
