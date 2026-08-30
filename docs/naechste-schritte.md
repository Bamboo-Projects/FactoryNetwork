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

### 3. Latenz je Router und ein Kabel statt zwei

**Plan:** `plan-latenz-und-kabel.md`

**Zwei Dinge in einem Plan, weil sie zusammenhängen:** Fällt das dichte Kabel,
verliert der Router seine alte Begründung — und die Latenz gibt ihm eine neue.

**Was entschieden ist:**
- Latenz je Router (1 Tick), nicht je Block — Entfernung kostet in
  Wirklichkeit nichts
- Das dichte Kabel fällt; das verbleibende bekommt seine Bandbreite
- Die Farben bleiben, sie sind die VLANs

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

- **Das größere Terminal** (352 × 296) auf deinem Bildschirm — passt es bei
  deiner GUI-Skalierung?
- **Crafting, Code, Displays, Log** in der neuen Größe. Sie tragen keine
  festen Maße, gesehen habe ich sie aber nicht.
- **Die dunklen Gehäuse** von Presse, Brennkammer, Router und Serverschrank.
- **Ein Kabel auf einen Halter setzen** — im Prüflauf bestätigt, im Spiel
  noch nicht gesehen.

## Stand der Prüfläufe

340 GameTests grün, zuletzt am 30.08. um 02:34.
