# Der Anschluss vor dem Kabel — Umsetzungsplan

**Ziel:** Einen Anschluss setzen können, wo noch kein Kabel liegt. Das Kabel
kommt später und macht aus dem Halter eine Leitung.

**Entscheidung:** Steht in `docs/entscheidungen.md`, „Ein Anschluss kann
gesetzt werden, bevor ein Kabel da ist" (Commit b43c910), bestätigt vom User
mit Blick auf AE2.

**Der Kern in einem Satz:** Der Kabelblock bekommt ein Zustandsfeld
`cable`; ist es falsch, ist er ein bloßer Halter — er leitet nicht, zeigt
keinen Strang und räumt sich weg, sobald der letzte Anschluss abgeht.

## Was die Bauform hergibt

Geprüft, bevor der Plan stand:

| Frage | Antwort | Wo |
|---|---|---|
| Werden Anschlüsse aus dem Blockmodell gezeichnet? | Nein, aus der BlockEntity | `client/render/CableBusRenderer.java:52` |
| Wie entsteht das Kabelmodell? | Multipart: `cable_core` immer, `cable_arm` je Richtung | `blockstates/cable.json` |
| Was fragt der Netzaufbau? | `instanceof CableBlock` — die Farbe interessiert ihn nicht | `network/FactoryGraph.java:291,666`, `GatewayRegions.java:128` |

**Daraus folgt:** Ein Halter braucht keine neue Optik. Kern und Arme hängen an
`cable=true`, der Anschluss wird ohnehin gesondert gezeichnet. **Aber** der
Netzaufbau muss lernen zu fragen — sonst leitet ein Halter ohne Kabel Strom.

## Die Aufgaben

- [x] **1. Das Zustandsfeld.** `CableBlock.CABLE`, Vorgabe `true`. Alles
      Bestehende bleibt damit, wie es war.
- [x] **2. Der Netzaufbau fragt.** Drei Stellen. Ein Halter ist kein Leiter,
      und sein Anschluss hängt an nichts.
- [x] **3. Das Modell.** Kern und Arme nur bei `cable=true`.
- [x] **4. Die Trefferfläche.** Ohne Kabel nur die Anschlussplatten — sonst
      klickt man auf einen unsichtbaren Kern.
- [x] **5. Der Anschluss setzt den Halter.** Rechtsklick mit dem Connector auf
      eine freie Fläche, wo kein Kabel ist.
- [x] **6. Das Kabel macht daraus eine Leitung.** Kabel auf einen Halter
      gesetzt schaltet `cable` um, statt danebenzusetzen.
- [x] **7. Der leere Halter räumt sich weg.** Wie AE2s `cleanup()`.
- [x] **8. Prüfläufe und Commit.**

### Was beim Bauen dazukam

Vier Stellen, die der Plan nicht auf der Liste hatte und die ohne ihn Löcher
gewesen wären:

- **Die Loot-Tabelle.** Ein Halter hätte beim Abbauen ein Kabel hergegeben,
  das nie in ihm lag — wer einen Anschluss setzt und wieder abbaut, bekäme
  ein Kabel geschenkt. Sie hat jetzt eine Bedingung auf `cable=true`.
- **Der Schraubenschlüssel.** Er nimmt Anschlüsse ab und musste dasselbe
  Aufräumen lernen wie der Weg über die leere Hand.
- **Der Mittelklick.** Er gab das Kabel des Blocks; auf einem Halter gibt er
  jetzt den Anschluss.
- **Die Nachbarn.** `connects` musste lernen, dass an einen Halter niemand
  andockt. Ohne das wüchse ein Kabel daneben einen Arm auf einen Block, in
  dem nichts liegt.

## Grenzen dieser Nacht

**Keine neue Optik.** Wo eine Formfrage auftaucht, wird sie minimal gelöst und
hier notiert, statt sie festzuschreiben.

**Was auffiel und nicht dazugehört:** Schleichend auf ein Kabel zu klicken
führt heute ins Leere — Minecraft überspringt den Weg des Blocks und ruft den
Connector, der kein Verhalten hat. Mit Aufgabe 5 bekommt er eines, und der
Klick tut wieder etwas. Das ist Folge, nicht Ziel.
