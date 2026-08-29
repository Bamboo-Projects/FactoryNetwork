# Eine MCP-Mod für Minecraft — Entwurf

**Auftrag:** „kannst du noch eine zweite mod entwickeln? Es wäre ja klug eine
mcp mod zu machen das du mit den client interagieren kannst" (30.08.)

**Ziel:** Ich sehe und tue im laufenden Client, was du sonst zeigen und tippen
musst — Bauen, Screenshots, Logs, Blockzustände lesen.

---

## Warum das viel Zeit spart

**Der heutige Ablauf:** Ich ändere etwas, starte den Client, du gehst hin, du
baust, du machst einen Screenshot, du beschreibst, was falsch ist. Jede Runde
kostet dich Minuten — und drei der letzten Fehler (Kanaltexte am Kabel, das
zu schmale Gitter, das fehlende Diagramm) hättest du nicht melden müssen: Ich
hätte sie selbst gesehen.

**Der Ablauf danach:** Ich baue den Aufbau selbst, mache den Screenshot selbst,
lese das Log selbst — und zeige dir das Ergebnis statt einer Frage.

## Das Vorbild liegt nebenan

`D:\Projekte\blender_mcp` macht genau das für Blender, und die Aufteilung
trägt hier genauso:

| Dort | Hier |
|---|---|
| `addon/` — Python im Blender-Prozess | eine NeoForge-Mod im Client |
| `mcp/` — der MCP-Server daneben | ein Server in Python oder Node |
| dazwischen: ein lokaler Socket | dasselbe |

**Warum zwei Teile und nicht einer:** Der MCP-Server muss laufen, wenn
Minecraft nicht läuft — sonst kann ich nicht einmal fragen, ob es läuft. Und
er darf nicht sterben, wenn der Client abstürzt.

## Was die Mod können sollte

**Nach Nutzen sortiert, nicht nach Aufwand:**

1. **Screenshot.** Das eine Werkzeug, das die meisten Runden spart. Alles
   Visuelle — Fenster, Blockmodelle, Texturen — ist heute auf dich angewiesen.
2. **Log lesen.** Die letzten N Zeilen, gefiltert. Ich lese heute
   `run/logs/latest.log` von der Platte; das geht nur, wenn der Client schon
   geschrieben hat.
3. **Blöcke setzen und lesen.** Einen Aufbau bauen, ohne dass du hingehst.
   Der Prüfaufbau für ein Brückenpaar wäre ein Werkzeugaufruf statt fünf
   Minuten Handarbeit.
4. **Fenster öffnen und anklicken.** Terminal auf, Reiter wechseln,
   Screenshot. Damit prüfe ich Oberflächen selbst.
5. **Gegenstände ins Inventar.** Kreativmodus, aber gezielt.

**Was ich bewusst weglassen würde:** Spielerbewegung, Chat, alles, was den
Anschein erweckt, ich spiele mit. Ich prüfe, ich spiele nicht.

## Die Sicherheitsfrage, die vorher geklärt sein muss

**Eine Mod, die Blöcke setzt, kann eine Welt zerstören.** Drei Regeln, die
ich einbauen würde, bevor irgendetwas anderes entsteht:

1. **Nur im Einzelspieler und nur mit `--enable-mcp`.** Kein Server, kein
   Multiplayer, keine Vorgabe.
2. **Ein Arbeitsbereich.** Ein Quader, den du festlegst; außerhalb setzt die
   Mod nichts. Der Prüfaufbau steht damit dort, wo er hingehört, und nicht in
   deinem Bergwerk.
3. **Nur lokal.** Der Socket hört auf `127.0.0.1`, nicht auf `0.0.0.0`. Eine
   Mod, die Blöcke setzt und aus dem Netz erreichbar ist, ist eine
   Sicherheitslücke.

## Der Umfang, ehrlich

**Das ist kein Abend.** Grobe Schätzung, nach dem, was `blender_mcp` an
Struktur zeigt:

| Teil | Aufwand |
|---|---|
| Mod-Gerüst plus Socket | ein Abend |
| Screenshot und Log | ein Abend |
| Blöcke setzen und lesen | ein bis zwei Abende |
| MCP-Server samt Werkzeugschemata | ein Abend |
| Fenster bedienen | offen — das ist der harte Teil |

**Und ein Haken, den ich nicht kleinreden will:** Der Client rendert im
Render-Thread. Ein Screenshot muss dort entstehen, alles andere im
Server-Thread — das ist die Sorte Fehler, die sich als „manchmal hängt es"
zeigt.

## Meine Empfehlung zur Reihenfolge

**Screenshot und Log zuerst, als eigene kleine Mod.** Sie sind der größte
Nutzen, tragen kein Risiko (nur lesend) und beantworten die Frage, ob der Weg
überhaupt trägt. **Blöcke setzen erst danach** — und erst, wenn der
Arbeitsbereich steht.

**Wo:** Ein eigenes Repo unter `D:\Projekte\McpBridge` oder ähnlich.
FactoryNetwork ist eine Spielmod; ein Werkzeug für mich gehört nicht hinein.

## Was ich von dir brauche

- **Ob überhaupt** — der Aufwand ist real, und es ist Arbeit an einem
  Werkzeug, nicht an deinem Spiel.
- **Wie weit** — nur lesen (Screenshot, Log) oder auch bauen.
- **Wann** — vor oder nach den offenen Punkten in FactoryNetwork.
