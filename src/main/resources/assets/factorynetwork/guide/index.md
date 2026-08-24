---
navigation:
  title: Factory Network
  position: 10
---

# Factory Network

Ein Netz, das nichts von selbst tut.

Kein Bus zieht Gegenstände, kein Kabel sortiert etwas ein, keine Maschine wird
beliefert — **bis du es hinschreibst.** Was andere Logistik-Mods über Filter
und Karten lösen, steht hier als Programm im Terminal.

Das klingt nach mehr Arbeit, und für die ersten fünf Minuten ist es das auch.
Danach hast du etwas, das keine Filterkarte kann: Eine Fabrik, die auf
Ereignisse reagiert, Zustände behält und dir sagt, warum sie gerade nichts
tut.

## Das erste Netz

Vier Blöcke, dann läuft es:

1. **Controller** — das Herz. Er hält das Programm, den Speicher und den
   Stromvorrat. Es gibt genau einen je Netz.
2. **Terminal** — direkt an den Controller. Hier schreibst du den Code und
   siehst, was das Netz tut.
3. **Kabel** — vom Controller weg. Sie tragen keine Gegenstände, sondern
   Zuständigkeit: Was am Kabel hängt, gehört zum Netz.
4. **Connector** — an jede Maschine, die mitmachen soll. Er zeigt auf sie,
   und er braucht einen **Namen**.

Dazu ein **Laufwerk** mit einer Speicherzelle, sonst lagert das Netz nichts,
und eine Stromquelle — die **Brennkammer** reicht zum Anfangen.

## Warum der Name so wichtig ist

Ein Connector ohne Namen hängt im Netz, kostet einen Kanal und ist im Code
**nicht ansprechbar**. Erst der Name macht ihn zu etwas, worüber sich reden
lässt:

```
worker mahlen {
    from lager
    to brecher
    filter tag:c/ores
}
```

`lager` und `brecher` sind Namen, die du mit der **Beschriftungspistole**
vergeben hast. Rechtsklick auf einen Connector, und sie schlägt einen aus der
Maschine dahinter vor.

## Wenn nichts passiert

Die häufigsten drei Gründe, in dieser Reihenfolge:

- **Kein Strom.** Im Reiter *Netzwerk* steht der Vorrat. Ist er leer, geht das
  Netz aus und muss danach hochfahren.
- **Kein Kanal.** Jedes Gerät zieht auf seinem ganzen Weg zum Controller einen
  Kanal. Weiter hinten fehlt er dann — ein dichtes Kabel trägt mehr.
- **Der Connector zeigt auf die falsche Seite.** Zeig im Editor auf seinen
  Namen: Das Terminal sagt dir, was die Maschine an welcher Seite annimmt, und
  ob dort überhaupt etwas geht.

Dass ein Worker nichts bewegt, ist dagegen **kein** Fehler. Die Quelle kann
leer sein und das Ziel voll — beides ist normal und meldet sich nicht.
