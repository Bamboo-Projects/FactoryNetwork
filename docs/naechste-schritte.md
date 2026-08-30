# Was als Nächstes dran ist

**Stand: 30.08., nach der Planungsrunde.** Diese Datei ist der Einstieg für
eine neue Sitzung — sie sagt, was entschieden ist und in welcher Reihenfolge
gebaut wird.

---

## Entschieden, noch nicht gebaut

**In dieser Reihenfolge**, und der Grund für die Reihenfolge steht dabei.

### 1. Kein Itemverlust bei vollem Lager — erledigt am 30.08.

**Plan:** `plan-kein-itemverlust.md`

Ein Worker fragt jetzt vor dem Griff, und was trotzdem nirgends unterkommt,
verwahrt der Controller, statt es zu werfen. Drei Prüfläufe halten es fest.

### 2. Der Controller wird das schwächste Glied — erledigt am 30.08.

**Plan:** `plan-controller-grenze.md` — Variante A gebaut.

Er trägt so viel wie ein dichtes Kabel, jeder Anbau legt die Hälfte dazu.
Zu sehen im Kopf des Netzwerk-Reiters (mit Grenzlinie in der Kurve) und im
Analysator, wenn man den Controller anklickt.

### 3. Latenz je Router und ein Kabel statt zwei — erledigt am 30.08.

**Plan:** `plan-latenz-und-kabel.md`

Latenz je Router steht (verzögert den Start, nicht den Takt), das Kabel trägt
jetzt 25,6 MB/s, das dichte ist stillgelegt.

Der Router nennt seine Verzögerung im Fenster und in Jade.

### 4. Die Gegenrichtung: Ware, die entsteht — erledigt am 30.08.

Der Itemverlust hatte eine Rückseite. Überall gilt „erst einlegen, dann
entnehmen", damit nichts verschwindet; der Preis dieser Reihenfolge ist, dass
eine Quelle, die beim echten Griff weniger hergibt als beim Probelauf, den
Unterschied im Ziel stehen lässt. Er ist aus dem Nichts entstanden.

**Erreichbar war das an einer Stelle ohne jedes Zutun einer fremden Mod:**
Ein Speicherbus zählt fremde Inventare zum Bestand, und die dürfen ihren
Inhalt behalten — ein Ofen zeigt von unten sein Brennstofffach und rückt es
nicht heraus. Aus 64 Kohle wurden 128, und weil der Bestand mangels
Entnahme nicht nachzog, im nächsten Tick 192. Betroffen waren der Worker und
`move`, beide auf dem Weg aus dem Speicher in ein Gerät.

Die anderen Stellen, je mit ihrem Befund:

| Stelle | Befund |
|---|---|
| Speicher → Gerät (Worker, `move`) | **Erreichbar, behoben.** Speicherbus auf einem Ofen. |
| Gerät → Gerät (Worker, `move`) | Nur über eine fremde Maschine, die auf `simulate` anders antwortet als auf den Griff. Dass es die gibt, stand schon im Quelltext — **abgesichert**. |
| Tank → Tank (Worker, `move`) | Dasselbe, **abgesichert**. Der Rückweg ist enger: In die Quelle geht nichts zurück, also wird im Ziel abgezogen. |
| Speicher → Tank (`fillFromNetwork`, `storageToTank`) | **Nicht erreichbar.** Der Flüssigkeitsspeicher kennt keine Busse; `count` und `extract` lesen dieselben revisionsgeprüften Zellen, und dazwischen läuft nichts. |
| Tank → Speicher (`drainIntoNetwork`, `tankToStorage`) | **Nicht erreichbar.** Fragt `room()` vor dem Zug. |
| Fertigung, Schritt ohne Station | **Erreichbar, behoben.** Derselbe Ofen: Der Kohleblock entstand, die neun Kohle blieben liegen. |
| Fertigung, Rezept an einer Maschine | **Erreichbar, behoben.** Kein Dupe — das Ergebnis kommt aus der Maschine —, aber der Auftrag stand für immer auf „läuft", und das Wasser des Rezepts war schon eingefüllt. |
| Chemikalien (`ChemicalStores`), Energie (`supply`, `drawIn`) | **Nicht erreichbar.** Beide nehmen zuerst und lesen die Rückgabe; was nicht ankommt, geht zurück. |

Ein Worker an einer solchen Quelle meldet das jetzt als `HALTED` mit Grund.
`IDLE` wäre vom Setter am Tickende mit „nichts zu tun" überschrieben worden —
die falscheste aller Auskünfte über einen Worker, der jeden Tick ins Leere
greift.

Was der Rückweg nicht kann, sagt er jetzt: Nimmt das Ziel nichts zurück —
ein Eingangsfach tut das nicht —, bleibt der Rest liegen, wird nicht als
bewegt gezählt und der Worker meldet ihn mit Zahl. Eine Menge, die einmal zu
viel dasteht, statt einer, die jeden Tick nachwächst.

**Offen geblieben, gemeldet statt gebaut:** Eine Flüssigkeit mit
Datenkomponenten lässt sich zwischen zwei Tanks gar nicht bewegen. Der
Probelauf fragt mit einer Sorte ohne Komponenten, und ein Tank, der genau
vergleicht, antwortet darauf mit nichts — stumm, der Worker meldet „nichts zu
tun". Zu beheben wäre es an zwei Zeilen; es ändert aber, was die Mod kann,
und der Weg über den Netzspeicher würde die Komponenten weiterhin abstreifen.

---

## Offen, wartet auf dich

### Die MCP-Mod

**Plan:** `plan-mcp-mod.md`

**Drei Fragen darin sind unbeantwortet:** ob überhaupt, wie weit (nur lesen
oder auch bauen), und wann — vor oder nach den drei Punkten oben.

**Mein Vorschlag:** danach. Sie ist ein Werkzeug für mich, kein Feature für
dich, und die drei Punkte oben sind alle angefangen oder entschieden.

### Der Controller-Multiblock

**Vorlage:** `controller-multiblock.md`

**Halb erledigt durch Punkt 2:** Der Anbau bekommt seinen Zweck zurück. Offen
bleibt die Formfrage — soll es einen Moment „jetzt ist es fertig" geben, oder
wächst das Bauwerk formlos weiter wie heute?

### Weitere Reallife-Ideen

**Sammlung:** `reallife-ideen.md`

Nach Punkt 3 bleiben aus der Liste: Halbduplex, ein Monitoring-Reiter, und
die drei Kleinigkeiten (VLAN beim Namen nennen, MTU, Uptime im Kopf).

**Zwei Ideen stehen dort mit „würde ich nicht bauen":** Kollisionen und DNS.
Beide widersprechen etwas, das schon funktioniert.

---

## Was im Spiel noch zu prüfen ist

Aus den letzten Runden, ungeprüft:

- **Eine Welt mit gesetztem dichten Kabel** nach dem Update: Steht es noch da,
  in seiner Farbe, mit seinen Verbindungen? Das ist der einzige Punkt hier,
  der sich nicht als Prüflauf schreiben lässt.
- **Das größere Terminal** (352 × 296) auf deinem Bildschirm — passt es bei
  deiner GUI-Skalierung?
- **Crafting, Code, Displays, Log** in der neuen Größe. Sie tragen keine
  festen Maße, gesehen habe ich sie aber nicht.
- **Die dunklen Gehäuse** von Presse, Brennkammer, Router und Serverschrank.
- **Ein Kabel auf einen Halter setzen** — im Prüflauf bestätigt, im Spiel
  noch nicht gesehen.

## Stand der Prüfläufe

354 GameTests grün, zuletzt am 30.08. um 13:12.
