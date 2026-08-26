---
navigation:
  title: Für Serverbetreiber
  position: 85
---

# Für Serverbetreiber

Diese Seite ist für den, der den Server betreibt — nicht für den, der darauf
spielt. Sie beantwortet zwei Fragen: **Wer darf eine fremde Fabrik umbauen**,
und **wie viel darf ein Programm kosten**.

Beides steht in `config/factorynetwork-server.toml`. Die Datei entsteht beim
ersten Start und liegt bei der Welt, nicht beim Client — die Werte gelten also
für alle, die sich verbinden.

## Der Grundsatz vorweg

**Ein Programm läuft im Serverthread, und der wartet auf niemanden.** Jede
Grenze auf dieser Seite hat denselben Zweck: dass ein einzelner Spieler mit
einer Endlosschleife nicht alle anderen anhält.

Die Vorgaben sind so gewählt, dass ein normales Programm sie nie berührt. Wer
sie erhöht, sollte wissen, warum.

## Wer umbauen darf

```toml
[protection]
programs = "OFF"
```

- **`OFF`** — jeder darf. Die Vorgabe, und der Stand vor dieser Einstellung.
  Richtig für eine Welt unter Freunden.
- **`OWNER`** — nur, wer den Controller gesetzt hat, und Operatoren. Richtig
  für einen öffentlichen Server, auf dem jeder seine eigene Fabrik hat.
- **`OPS`** — nur Operatoren. Richtig, wenn die Fabrik allen gehört und
  niemand sie allein umbauen soll.

**Betroffen ist, was eine Anlage umbaut:** ein Programm übernehmen, einen
Entwurf speichern, einen Fertigungsauftrag abbrechen.

**Nicht betroffen ist das Benutzen.** Zusehen, Knöpfe auf einer Anzeigenwand
drücken, den Bestand ansehen — das bleibt allen offen. Wer einen Knopf drückt,
ruft eine Funktion auf, die jemand anders geschrieben hat; das ist Bedienung
und kein Umbau.

**Die Beschriftungspistole ist ausdrücklich nicht dabei.** Sie ändert die
Welt, und dafür gibt es Schutzmods — die kennen Claims, Regionen und
Grundstücke, und diese Mod sollte das nicht ein zweites Mal und schlechter
tun.

Dasselbe gilt für `click()`: Ein Programm, das eine Maschine anfasst, tut das
auf demselben Weg wie ein Spieler. In einer fremden Claim passiert also
nichts, wenn deine Schutzmod es nicht erlaubt.

## Was ein Programm kosten darf

```toml
[limits]
stepBudget = 10000
networkNodes = 4096
craftingDepth = 8
craftingBudget = 512
globalListSize = 256
```

### `stepBudget` — 10.000

Wie viele Anweisungen ein einzelner Durchlauf ausführen darf, bevor er mit
einer Meldung abbricht. **Das ist die Grenze, die zählt:** Eine Endlosschleife
läuft damit höchstens so lange und hält den Server nicht an.

Wer sie erhöht, verlängert im Ernstfall genau den Stillstand, gegen den sie da
ist.

### `networkNodes` — 4.096

Wie viele Blöcke der Aufbau des Netzes höchstens besucht. Ein Netz darüber
hinaus wird abgeschnitten und meldet das im Terminal.

Das begrenzt die **Suche**, nicht die Zahl der Geräte — dafür gibt es die
Kanäle, und die sind Spielinhalt und stehen nicht in dieser Datei.

### `craftingDepth` — 8 und `craftingBudget` — 512

Wie tief ein Fertigungsauftrag sucht, wenn eine Zutat fehlt, und wie viele
Bedarfe er dabei ansehen darf.

Bei `craftingDepth = 1` baut das Netz nur aus dem, was dasteht. Was jenseits
der Grenze liegt, steht als fehlend im Auftrag — **abgebrochen wird nichts**,
der Spieler sieht nur, was er selbst besorgen muss.

Das Budget greift bei Rezeptbäumen, die sich in viele erlaubte Sorten
verzweigen; dort wächst die Suche schneller als ihre Tiefe.

### `globalListSize` — 256

Wie viele Einträge ein globaler Listenwert tragen darf. Darüber hinaus hält
das Programm mit einer Meldung an.

Er ist der einzige Wert, der in einer Schleife wachsen kann **und** den
Neustart übersteht — deshalb hat er eine eigene Grenze.

## Was nicht in dieser Datei steht

**Die Schrankplätze und die Kanäle.** Sie sind Spielinhalt und gehören zum
Ausgleich der Mod, nicht zur Serverlast. Wer sie ändern will, ändert das
Spiel und nicht die Betriebssicherheit.

**Der Stromverbrauch.** Was ein Worker kostet, ist eine Frage des Spiels und
keine des Servers.

## Wenn ein Spieler an eine Grenze stößt

Er sieht es. Jede dieser Grenzen meldet sich im Terminal des betroffenen
Controllers, mit dem Namen der Grenze und dem, was sie gerade verhindert hat —
kein stiller Stillstand und kein Absturz. Wer eine Meldung meldet, kann dir
also sagen, welcher Wert ihm im Weg steht.
