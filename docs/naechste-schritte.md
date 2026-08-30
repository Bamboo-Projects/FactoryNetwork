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

344 GameTests grün, zuletzt am 30.08. um 03:15.
