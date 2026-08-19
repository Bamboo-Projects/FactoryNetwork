# Programmable Factory Network Mod — Konsolidiertes Konzept nach externer Prüfung

Stand: 2026-08-19  
Zielplattform: Minecraft 1.21.1 / NeoForge

---

# 1. Vision

Die Mod soll ein **Factory Operating System für Minecraft** werden.

Sie kombiniert bewusst mehrere Ideen, die heute meist getrennt vorkommen:

- ein digitales Netzwerk mit Kabeln, Channels, Item-/Fluid-Speichern und zentralem Terminal,
- benannte Connectoren zum Ansprechen beliebiger Maschinen im Netzwerk,
- eine zentrale Programmiersprache für die komplette Fabriklogik,
- persistente Worker und Autocrafting-Aufträge,
- ereignisgetriebene Automation,
- Redstone-Ein- und Ausgabe inklusive Signalstärken 0–15,
- spielerdefinierte Multiblocks,
- programmierbare Displays und Dashboards,
- Wireless-Zugriff und Push-Benachrichtigungen,
- eine brauchbare In-Game-IDE.

Die Mod ist ausdrücklich **kein Fork von AE2, SFM oder ComputerCraft**, sondern ein eigenständiges System.

---

# 2. Zentrale Designprinzipien

## 2.1 Eine zentrale Codebasis

Es gibt nicht für jeden Bus, Connector oder jede Maschine ein separates Script.

Der gesamte Code eines Netzwerks liegt zentral im System und wird dort bearbeitet und ausgeführt.

Der Connector ist bewusst „dumm“:

- Maschine erkennen,
- Capabilities bereitstellen,
- Items/Fluids/Chemicals/Energie/Redstone lesen oder schreiben,
- Zustandsänderungen melden,
- vom Netzwerk angeforderte Aktionen ausführen.

Die Intelligenz liegt im Netzwerk-Controller bzw. der Runtime.

## 2.2 Ein Connector pro Maschine genügt

Ein Connector wird physisch an eine Maschine gesetzt.

Er bekommt einen frei wählbaren Namen:

```text
Name: crusher_1
```

Ab diesem Zeitpunkt ist diese Maschine unter diesem Namen im Netzwerk verfügbar.

Der Connector ist **nicht auf die physische Seite beschränkt, an der er sitzt**.

Er kann – ähnlich zum SFM-Prinzip – alle relevanten Seiten und Capabilities des verbundenen Blocks ansprechen.

Beispiel:

```text
crusher_1
├─ item handlers
├─ fluid handlers
├─ chemical handlers
├─ energy
└─ redstone
```

Dadurch ist für eine Mekanism-Maschine nicht für jede Seite ein eigener Connector notwendig.

## 2.3 Namen werden nicht vorgeschrieben

Der Spieler entscheidet selbst, wie Connectoren heißen.

Gültig wären zum Beispiel:

```text
crusher_1
nether_crusher
hans
ore_line_a
maschine_links
```

Die Mod schreibt kein Namensschema vor.

Namen müssen nur innerhalb ihres jeweiligen Sichtbarkeitsbereiches eindeutig sein.

---

# 3. Netzwerk

## 3.1 Grundelemente

Das Netzwerk besteht mindestens aus:

- Controller,
- Kabeln,
- Channels,
- Connectoren,
- Storage Drives / Disks,
- Terminal,
- später Quantum-Verbindungen,
- später Wireless Access.

## 3.2 Channels

Channels bleiben bewusst Teil des Gameplays.

Geräte benötigen Netzwerkressourcen und ein physisch sinnvolles Netz bleibt relevant.

Beispiel:

```text
Controller
   │
Cable
├─ Connector crusher_1
├─ Connector furnace_1
├─ Storage Drive
└─ Terminal
```

Spätere Kabel-Tiers können unterschiedliche Channel-Kapazitäten besitzen.

## 3.3 Dimensionen

Dimensionen sollen die Programmiersprache möglichst wenig beeinflussen.

Ein Quantum-Link verbindet zwei physisch getrennte Netzbereiche.

Danach erscheinen die verbundenen Connectoren logisch im selben Netzwerk.

Der Spieler darf selbst entscheiden, ob Namen Dimensionen enthalten:

```text
nether_crusher
```

oder eben nicht.

Chunkloading ist **nicht Aufgabe dieser Mod**.

Wenn ein Gerät oder Netzabschnitt nicht geladen ist, gilt es als nicht verfügbar/offline.

---

# 4. Chunk-Unload-Verhalten

Die Mod stellt keinen eigenen Chunkloader bereit.

Sie muss aber definieren, was passiert, wenn Geräte nicht geladen sind.

Beispiel:

```text
crusher_1.online == false
```

Persistente Worker und Jobs bleiben erhalten und wechseln in einen wartenden Zustand.

Sobald das Gerät wieder verfügbar ist, kann die Runtime weiterarbeiten.

Mögliche Worker-Zustände:

```text
RUNNING
WAITING_SOURCE
WAITING_TARGET
WAITING_DEVICE
BLOCKED
PAUSED
FAILED
COMPLETED
```

---

# 5. Storage

## 5.1 Bedienung

Die Bedienung des Storage-Terminals darf sich bewusst stark an AE2 orientieren:

- zentrale Item-Liste,
- Suchfeld,
- Sortierung,
- Item-Mengen,
- Rein-/Rauslegen,
- Shift-Klick,
- Stored / Craftable / Both,
- craftbare Items in derselben Ansicht,
- Crafting-Mengenauswahl,
- EMI/JEI/REI-Integration.

Der Unterschied liegt nicht primär in der Bedienung, sondern in der Architektur dahinter.

## 5.2 Internes Storage-Modell

Ein slotweises Durchlaufen großer Netze soll vermieden werden.

Intern wird ein schlüsselbasierter Index vorgesehen:

```text
ResourceKey -> StoredAmount
```

Zusätzlich müssen physische Informationen zu Cells/Disks verfügbar bleiben.

Wichtige API-Konzepte:

```text
stored
reserved
available
```

Beispiel:

```text
Stored:   100
Reserved: 80
Available: 20
```

Das ist besonders wichtig für parallele Autocrafting-Aufträge.

## 5.3 Item Identity

Sehr früh muss definiert werden, wann zwei ItemStacks als dieselbe Ressource gelten.

Zu berücksichtigen sind unter anderem:

- Item-ID,
- Data Components,
- Damage,
- Enchantments,
- Custom Names,
- Mod-spezifische Daten.

Ein verzaubertes Werkzeug darf nicht automatisch mit einem anderen Werkzeug derselben Basis-ID zusammenfallen.

---

# 6. Terminal

Alles soll in **einem zentralen Terminal** erreichbar sein.

Vorgesehene Hauptbereiche:

```text
[ Storage ] [ Crafting ] [ Code ] [ Network ] [ Dashboards ]
```

Das Terminal ist der zentrale Arbeitsplatz des Spielers.

---

# 7. Connectoren

## 7.1 Aufgaben

Ein Connector:

1. registriert eine Maschine im Netzwerk,
2. besitzt einen vom Spieler vergebenen Namen,
3. erkennt ihre verfügbaren Schnittstellen,
4. kann über die Runtime angesteuert werden,
5. meldet relevante Änderungen,
6. kann Processing-Rezepte für Autocrafting bereitstellen.

## 7.2 Mod-Kompatibilität

Die Basisintegration soll möglichst über generische NeoForge-/Mod-Capabilities erfolgen.

Beispiele:

- Item Handler,
- Fluid Handler,
- Energy,
- Redstone.

Darauf kann eine Adapter-Schicht aufbauen:

```text
Generic Adapter
Mekanism Adapter
Create Adapter
GregTech Adapter
...
```

Der Generic Adapter liefert grundlegenden Zugriff.

Spezifische Adapter können zusätzliche Informationen expose'n:

```text
progress
recipe
energy
redstoneMode
chemical input/output
machine state
```

---

# 8. Processing-Rezepte

Processing-Rezepte gehören zum ausführenden Connector bzw. zum Multiblock.

Beispiel:

```text
crusher_1

Iron Ore
-> Iron Dust
```

Ein Connector kann mehrere Rezepte anbieten.

Später können Rezepte entweder:

- automatisch erkannt,
- manuell hinterlegt,
- oder durch mod-spezifische Adapter geliefert werden.

---

# 9. Normales Autocrafting

Für normales 3x3-/Crafting-Grid-Crafting wird ein eigenes Netzwerkgerät benötigt.

Arbeitstitel:

```text
Crafting Matrix
Fabricator
Crafting Unit
```

Es speichert normale Crafting-Rezepte digital.

Keine zwingenden physischen Pattern-Items.

Beispiel:

```text
8 Planks
-> Chest
```

Das Netzwerk kann dadurch dieselbe Terminal-Bedienung wie bei AE2 anbieten, ohne dessen Pattern-Architektur exakt zu kopieren.

---

# 10. Crafting Planner

Ein Crafting-Auftrag wird vor der Ausführung als Dependency-Graph geplant.

Beispiel:

```text
100 Machine Frames
├─ 400 Iron Ingots
│  └─ 400 Iron Dust
│     └─ 200 Iron Ore
├─ 200 Glass
└─ 100 Circuits
   ├─ 200 Copper Ingots
   ├─ 100 Silicon
   └─ 100 Redstone
```

Der Plan enthält:

- benötigte Ressourcen,
- vorhandene Mengen,
- zu craftende Mengen,
- fehlende Ressourcen,
- verwendete Rezepte,
- Maschinen/Gruppen/Multiblocks,
- Abhängigkeiten,
- Reservierungen.

## 10.1 Crafting-Ansicht

Vor Start eines Crafts:

```text
100x Machine Frame

Stored:
✓ 200 Glass

Will craft:
→ 280 Iron Ingots
→ 100 Circuits

Missing:
! 20 Silicon
```

Während des Crafts:

```text
[1] Ore Processing       128 / 280
[2] Smelting              64 / 280
[3] Circuit Crafting      WAITING
[4] Machine Frames        WAITING
```

Fehler sollen nachvollziehbar sein:

```text
BLOCKED

Reason:
furnace_group output blocked
```

---

# 11. Persistente Crafting Jobs

Autocrafting-Aufträge sind persistente Jobs.

Ein Job besitzt:

```text
id
target item
requested amount
completed amount
status
plan snapshot
reservations
current steps
created revision
```

Beispiel:

```text
CraftJob #1842
Target: 1000 Iron Ingots
Progress: 642 / 1000
Status: RUNNING
```

Serverneustart:

```text
save
-> restart
-> load jobs
-> reconnect devices
-> resume
```

Ein Code-Deploy darf laufende Crafting Jobs nicht automatisch zerstören.

---

# 12. Worker

Worker sind persistente, optimierte Daueraufgaben.

Sie sind kein Userscript, das permanent in einer Polling-Schleife läuft.

Beispiel:

```text
worker quarry_import {
    from quarry_output
    to storage
}
```

Der Worker wird von der Runtime als eigener persistenter Auftrag verwaltet.

Geeignet für:

- dauerhaften Import,
- dauerhaften Export,
- Maintain-Stock,
- Maschinenversorgung,
- kontinuierliche Transfers.

Weitere Optionen:

```text
filter
priority
strategy
overflow behavior
maintain amount
```

Beispiel:

```text
worker fuel_supply {
    from storage
    to generators

    filter "#minecraft:coals"
    maintain 64
    strategy round_robin
}
```

---

# 13. Event-Driven Automation

Ereignisse sollen der Standard für reaktive Logik sein.

Beispiele:

```text
on redstone_changed(sensor, strength) {
    ...
}
```

```text
on device_online(device) {
    ...
}
```

```text
on crafting_finished(job) {
    ...
}
```

Polling soll nur dort nötig sein, wo eine Fremdmod technisch keine vernünftigen Events ermöglicht.

Die Runtime darf intern Fallback-Polling benutzen, ohne dass der User dafür Schleifen schreiben muss.

---

# 14. Redstone

Redstone ist ein echter Wert von:

```text
0..15
```

Lesen:

```text
let strength = sensor.redstone()
```

Schreiben:

```text
alarm.redstone(15)
```

Event:

```text
on redstone_changed(sensor, strength) {
    if strength >= 12 {
        pumps.stop()
    }
}
```

Das System muss Redstone nicht nur als Boolean behandeln.

---

# 15. Eigene Events

Der Nutzer kann eigene Events definieren.

Beispiel:

```text
event OreBatchReady(item, amount)
```

Auslösen:

```text
emit OreBatchReady(iron_ore, 256)
```

Empfangen:

```text
on OreBatchReady(item, amount) {
    crushers.distribute(item, amount)
}
```

Custom Events sollen typisiert sein.

---

# 16. Auf Events warten

Workflows dürfen auf Ereignisse warten.

Beispiel:

```text
let result = await BatchFinished
    where id == jobId
```

`where` ist bereits der Event-Filter.

Ein zusätzliches `if` ist nur nötig, wenn danach weitere Logik geprüft werden soll.

Optional:

```text
timeout 30s
```

Beispiel:

```text
try {
    let result = await BatchFinished
        where id == jobId
        timeout 30s
}
catch Timeout {
    notify("Machine timeout")
}
```

---

# 17. Request / Response

Zusätzlich zu normalen Events gibt es Request/Response-Kommunikation.

Beispiel:

```text
request NeedMachine(item) -> Device
```

Aufruf:

```text
let machine = await request NeedMachine(iron_ore)
```

Antwort:

```text
on request NeedMachine(item) {
    reply crushers.first_available()
}
```

Trennung:

```text
event
= Nachricht ohne erwartete Antwort

request
= Anfrage mit Antwort

worker/job
= persistente Aufgabe
```

---

# 18. Persistente wartende Workflows

Wartender Code soll einen Serverneustart überleben.

Die Umsetzung sollte jedoch **nicht** davon abhängen, einen beliebigen Java-Aufrufstapel zu serialisieren.

Stattdessen wird suspendierbarer Code beim Compile intern in Continuations bzw. State Machines übersetzt.

Beispiel:

```text
fn processBatch(batch) {
    crushers.distribute(batch)

    let result = await BatchFinished
        where id == batch.id

    storage.insert(result.items)
}
```

kann intern sinngemäß werden zu:

```text
ProcessBatchState {
    state
    batch
    result
}
```

mit:

```text
STATE 0
-> distribute
-> register await
-> persist
-> suspend

STATE 1
-> receive event
-> continue
-> complete
```

Persistiert wird vor allem an definierten Suspension Points:

```text
await
sleep
request wait
worker/job handoff
```

Damit bleibt die gewünschte Persistence erhalten, ohne unnötig eine komplette allgemeine VM-Stack-Snapshot-Architektur zu erzwingen.

---

# 19. Deploy-Verhalten

Es gibt:

```text
Draft Version
Running Version
```

Code wird erst nach erfolgreicher Validierung/Compilation deployed.

Bei Syntax- oder Typfehlern läuft die bisherige Version weiter.

Beim Deploy:

```text
Event Listener
-> reload

Timer
-> reload

normale Script Tasks
-> reload

Crafting Jobs
-> keep

persistente Worker
-> keep soweit Definition kompatibel

wartende Workflows
-> spezielle Behandlung
```

Wenn eine laufende suspendierte Funktion durch einen Deploy verändert wurde, soll das Terminal betroffene Workflows anzeigen.

Mögliche Entscheidung:

```text
[ Weiter mit alter Revision ]
[ Abbrechen ]
```

Automatische Migration in neuen Code wird zunächst nicht versucht.

---

# 20. Sprachmodell

Die Sprache ist zweigeteilt.

## 20.1 Deklarativ

Top-Level-Ressourcen beschreiben Zustände oder dauerhafte Infrastruktur.

Beispiele:

```text
group crushers {
    ...
}
```

```text
worker quarry_import {
    ...
}
```

```text
multiblock OrePlant {
    ...
}
```

## 20.2 Imperativ

Funktionen und Event-Handler enthalten normalen Programmfluss:

```text
fn keepStock(item, amount) {
    if storage.count(item) < amount {
        craft(item, amount - storage.count(item))
    }
}
```

```text
on redstone_changed(sensor, strength) {
    if strength > 10 {
        ...
    }
}
```

Regel:

> Top-Level-Ressourcen werden deklariert. Verhalten wird programmiert.

Deklarationen enthalten keine beliebigen imperativen Kontrollstrukturen.

Funktionen erzeugen nicht dynamisch neue Top-Level-Deklarationen.

---

# 21. Typsystem

Die Sprache besitzt Typen, aber der User muss sie möglichst selten ausschreiben.

Beispiel:

```text
let count = storage.count(iron)
```

Der Compiler erkennt `count` selbst.

Explizite Typen werden vor allem bei APIs benötigt:

```text
fn keepStock(item: Item, amount: Int)
```

```text
event TankFull(tank: Device, fluid: Fluid, amount: Long)
```

Ziel:

- gute Autovervollständigung,
- Hover-Informationen,
- Compile-Time-Fehler,
- wenig Boilerplate.

---

# 22. Gruppen

Connectoren können Gruppen zugeordnet werden.

Beispiel:

```text
group crushers {
    members {
        crusher_1
        crusher_2
        crusher_3
    }

    strategy round_robin
}
```

Mögliche Strategien:

```text
round_robin
first_available
least_filled
random
priority
balanced
```

Default-Strategie kann pro Aufruf überschrieben werden.

Gruppen können später auch über Tags aufgebaut werden.

---

# 23. Multiblocks

Ein Multiblock ist gleichzeitig ein **Multiblock-Projekt**.

Beispiel:

```text
Multiblock "OrePlant"
=
Projekt "OrePlant"
```

Ein Multiblock-Projekt enthält:

- interne Connectoren,
- Gruppen,
- Funktionen,
- Events,
- öffentliche Schnittstellen.

## 23.1 Instanzen

Ein Projekt kann mehrfach gebaut werden.

```text
OrePlant
├─ plant_1
├─ plant_2
└─ plant_3
```

Alle Instanzen nutzen dieselbe Definition.

Intern dürfen die Connectoren identische Namen tragen:

```text
plant_1:
  crusher
  furnace
  output

plant_2:
  crusher
  furnace
  output
```

## 23.2 Connector-Zuordnung

Ein Connector kann entweder:

```text
Netzwerkweit
```

oder einem Multiblock-Projekt zugeordnet sein.

Ein Connector im Multiblock-Projekt taucht nur dort auf.

Dadurch bleiben große Netzwerke sauber.

## 23.3 Maschinen innerhalb von Multiblocks

Multiblocks dürfen reale Maschinen anderer Mods enthalten.

Beispiel:

```text
Crusher -> Enricher -> Furnace
```

Diese Maschinen arbeiten als interne Bestandteile des Multiblocks.

Von außen wird bevorzugt nur die öffentliche Multiblock-Schnittstelle verwendet.

Beispiel:

```text
ore_plant_1.process(iron_ore)
```

---

# 24. Multiblock-Struktur

Die Mod soll keine ausschließlich fest kodierten Strukturen erzwingen.

Ein möglicher Ansatz:

- Multiblock Controller setzen,
- Bereich/Struktur definieren,
- interne Connectoren dem Projekt zuordnen,
- öffentliche Ports definieren.

Die genaue Strukturdefinition bleibt noch zu spezifizieren.

---

# 25. Displays

Displays sind programmierbare UI-Endpunkte.

Nicht primär Pixelprogrammierung, sondern komponentenbasiertes UI.

Beispiel:

```text
display factory_status {
    title "Factory Status"

    row "Iron" storage.count(iron_ingot)
    row "Steel" storage.count(steel_ingot)

    progress "Ore Processing" {
        value ore_worker.progress
    }

    indicator "Reactor" {
        state reactor.online
    }
}
```

Mögliche Komponenten:

```text
text
number
progress
indicator
button
chart
list
grid
item
fluid
redstone
craftingTree
```

---

# 26. Collections / öffentliche System-API

Der Code soll auf zentrale Systemdaten zugreifen können.

Beispiele:

```text
storage.items()
storage.fluids()

crafting.jobs()
crafting.recipes()

workers.all()

network.devices()
network.groups()

multiblocks.instances()
```

Darauf sollen Collection-Operationen möglich sein:

```text
filter
map
sort
first
count
sum
groupBy
```

---

# 27. Itemlisten auf Displays

Beispiel:

```text
display warehouse {
    title "Warehouse"

    list storage.items() {
        columns {
            item
            amount
        }

        sort amount desc
        limit 12
    }
}
```

---

# 28. Crafting Jobs auf Displays

```text
display crafting_screen {
    title "Active Crafting"

    list crafting.jobs()
        where status != completed {
        item
        progress
        status
    }
}
```

Crafting Jobs sollen unter anderem expose'n:

```text
id
item
requested
completed
status
progress
startedAt
requester
currentStep
missingItems
plan
```

---

# 29. Wireless Terminal

Das Wireless Terminal verwendet grundsätzlich dieselben Funktionen wie das normale Terminal:

```text
Storage
Crafting
Code
Network
Dashboards
```

Reichweite und dimensionsübergreifender Zugriff werden über Netzwerkhardware gelöst.

---

# 30. Push-Benachrichtigungen

User-Code kann Notifications senden:

```text
notify(
    title: "Ore Plant",
    message: "Output blocked",
    level: warning
)
```

Prioritäten:

```text
info
success
warning
critical
```

Das Wireless Terminal kann diese anzeigen.

Später können Notifications Aktionen enthalten:

```text
[ Open Quarry ]
[ Stop Quarry ]
```

---

# 31. In-Game-IDE

Es wird kein VS Code eingebettet.

Stattdessen wird eine fokussierte eigene IDE gebaut.

Wichtige Funktionen:

- Syntax Highlighting,
- Zeilennummern,
- Tabs,
- Projektbaum,
- Autocomplete,
- Fehlerdiagnose,
- Hover-Infos,
- Suche,
- Go-to-definition,
- Find References,
- Problems-Ansicht,
- Logs,
- Deploy/Running-Version,
- Musterauflösung.

Der letzte Punkt ist nicht selbstverständlich und folgt aus der Zielumgebung:
Ein Auswahlmuster wie `item:*_dust` trifft in einem großen Pack hunderte
Einträge, und welche das sind, kann niemand im Kopf haben. Der Editor muss zu
jedem Muster anzeigen, was es gerade auflöst — sonst ist nicht erkennbar, dass
`item:*_dust` auch die Zwischenprodukte einer Verarbeitungskette einsammelt.
Siehe `entscheidungen.md`, Abschnitt „Belegfall AllTheOres".

Beispiel:

```text
┌──────────────────────────────────────────────┐
│ Storage | Crafting | Code | Network         │
├───────────────┬──────────────────────────────┤
│ PROJECT       │ main.mf                     │
│ main          │                              │
│ groups        │ fn main() {                 │
│ lib/          │     crushers.send(...)      │
│ multiblocks/  │ }                           │
│               │                              │
│ NETWORK       │                              │
│ crusher_1     │                              │
│ furnace_1     │                              │
├───────────────┴──────────────────────────────┤
│ Problems: 0 | Runtime: Running | Rev: 27    │
└──────────────────────────────────────────────┘
```

---

# 32. IDE Network Snapshot

Beim Öffnen des Editors erhält der Client einen Snapshot der Netzwerkstruktur.

Beispiel:

```text
devices
groups
multiblocks
functions
recipes
resource ids
types
```

Autocomplete arbeitet lokal auf diesem Snapshot.

Nicht bei jedem Tastendruck wird der Server abgefragt.

---

# 33. Performance-Modell

Die Runtime darf den Minecraft-Server nicht blockieren.

Jeder Ablauf erhält ein Instruction-/Step-Budget pro Tick.

Ist dieses aufgebraucht:

```text
suspend
-> next tick continue
```

Zusätzliche Limits:

```text
max workers
max suspended workflows
max event queue
max collection size
max recursion depth
max instructions per task
```

Dadurch können Endlosschleifen oder Event-Stürme den Server nicht einfrieren.

---

# 34. Event-Sturm-Schutz

Beispielproblem:

```text
on item_changed {
    emit item_changed
}
```

Die Runtime braucht Schutzmechanismen:

- Event Queue Limits,
- Recursion / Cycle Detection,
- Rate Limiting,
- Debug-/Error-Anzeige.

---

# 35. Backpressure

Worker dürfen nicht ständig erfolglos versuchen zu übertragen.

Beispiel:

```text
storage full
```

Dann:

```text
worker -> WAITING_TARGET
```

Er wird erst wieder relevant aktiviert, wenn:

- Kapazität frei wird,
- Zielstatus sich ändert,
- ein Retry-Timer ausgelöst wird.

---

# 36. Multiplayer und Rechte

Später benötigte Rechte:

```text
Network Owner
Use Terminal
View Storage
Insert/Extract
Request Craft
Cancel Craft
View Code
Edit Code
Deploy Code
Manage Connectors
Manage Multiblocks
Manage Permissions
```

Besonders `Deploy Code` ist eine mächtige Berechtigung.

---

# 37. Architekturübersicht

```text
                 TERMINAL / IDE
                       │
                       ▼
                 Project Source
                       │
                    Compiler
                       │
                       ▼
                 Runtime Program
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Events        Workers       Jobs
          │            │            │
          └────────────┼────────────┘
                       ▼
                  Network API
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
    Connector       Storage      Multiblock
        │
        ▼
 External Machine / Inventory
```

---

# 38. Erste spielbare Fassung — vertikaler Schnitt

Die erste spielbare Fassung soll **alle Kernelemente der Vision berühren**, aber jeweils minimal.

Enthalten:

```text
Controller
Cable
Channels
Connector
Item Storage
Terminal
Code Editor minimal
Compiler/Runtime minimal
Events minimal
Worker minimal
Persistence
Redstone 0..15
```

Beispielaufbau:

```text
[Quarry / Chest]
       │
Connector "quarry_output"
       │
     Cable
       │
 Controller ─ Drive ─ Terminal
```

Code:

```text
worker quarry_import {
    from quarry_output
    to storage
}
```

Event:

```text
on redstone_changed(sensor, strength) {
    log(strength)
}
```

Damit ist bereits die DNA des Mods spielbar.

Nicht zwingend in dieser ersten Fassung:

```text
Autocrafting
Quantum Bridge
Wireless Terminal
Multiblocks
Displays
Push Notifications
komplexe Groups
vollständige Fluid/Chemical-Integration
vollständige IDE
```

---

# 39. Empfohlene Entwicklungsreihenfolge

## Phase 1 — Sprache spezifizieren

Festlegen:

- Grammar,
- Variablen,
- Funktionen,
- Typen,
- Events,
- await,
- Requests,
- Worker-Deklarationen,
- Fehlersemantik,
- Collections.

## Phase 2 — Runtime/Persistence-Prototyp

Testfall:

```text
fn test() {
    let x = 123
    await TestEvent
    print(x)
}
```

Ablauf:

```text
start
-> await
-> save server
-> restart
-> fire event
-> output 123
```

Dieser Test beweist die Persistence-Architektur.

## Phase 3 — Netzwerk / Connector Registry

Implementieren:

- Controller,
- Kabel,
- Channels,
- Connector,
- Device Registry,
- Namen,
- Capability Discovery,
- Online/Offline.

## Phase 4 — Storage Backend

Implementieren:

- Item Identity,
- key-basierter Index,
- Disks,
- insert/extract,
- reservations,
- capacity.

## Phase 5 — Terminal

Minimal AE2-artige Storage-Bedienung.

## Phase 6 — Events, Redstone, Worker

Damit entsteht die erste vollständig erkennbare Version des Mods.

## Phase 7 — Groups / Strategien

```text
round_robin
first_available
least_filled
priority
```

## Phase 8 — Mod-Adapter / Fluids / Chemicals

Generic Capability Layer plus ausgewählte Mod-spezifische Adapter.

## Phase 9 — Autocrafting

Erst jetzt:

- Recipe Registry,
- Dependency Planner,
- Reservation Engine,
- Job Runtime,
- Recovery,
- Crafting UI.

## Phase 10 — Multiblocks

- Projektdefinition,
- Instanzen,
- interne Connectoren,
- öffentliche API.

## Phase 11 — Quantum / Wireless

Cross-Dimension-Netzwerk und mobiler Zugriff.

## Phase 12 — Displays / Dashboards / Notifications

Programmierbare UI, Wanddisplays und Push-Nachrichten.

## Phase 13 — IDE-Komfort

- Go-to-definition,
- Find References,
- Refactor/Rename,
- Advanced Inspections,
- Debugging.

---

# 40. Bewusst nicht in Phase 1 lösen

Folgende Dinge sind wichtig, aber keine Voraussetzung für den ersten vertikalen Prototyp:

- vollständiger Autocrafting-Dependency-Scheduler,
- komplexe Multiblock-Strukturdefinition,
- modübergreifende Recipe-Erkennung für alle Mods,
- ausgefeiltes Permissions-System,
- Quantum Networking,
- vollständige IDE,
- große Display-UI-Bibliothek.

---

# 41. Wichtigste technische Risiken

## 41.1 Autocrafting

Risiken:

- zyklische Rezepte,
- alternative Rezepte,
- parallele Jobs,
- Ressourcenreservierung,
- Race Conditions,
- Maschinenblockaden,
- Rezeptänderungen,
- Wiederaufnahme nach Restart,
- inkonsistente Outputs.

## 41.2 Persistente Workflows

Risiken:

- Schema-/Code-Versionen,
- alte Revisionen nach Deploy,
- serialisierbare lokale Daten,
- nicht mehr existierende Connectoren,
- Event-Verlust,
- Mod-/Server-Updates.

## 41.3 Fremdmod-Kompatibilität

Nicht jede Maschine verhält sich gleich.

Daher:

```text
Generic API first
Adapter second
Hardcoded special case last
```

---

# 42. Offene Designentscheidungen

Noch zu spezifizieren:

1. konkrete Syntax der Sprache,
2. Name der Sprache,
3. genaue Item-Identity-Regeln,
4. Storage-Disk-Kapazitätsmodell,
5. Channel-Zahlen und Kabel-Tiers,
6. genaue Network-Topologie,
7. Recipe-Erkennung fremder Mods,
8. Multiblock-Strukturdefinition,
9. Migration alter Projektversionen,
10. Verhalten suspendierter Workflows bei Deploy ohne anwesenden Spieler,
11. genaue Security-/Permission-Architektur,
12. genaue Display-Layout-Syntax,
13. Recipe-/Crafting-Unit-Design,
14. Skin-/Model-Designsystem.

---

# 43. Zusammenfassung

Das Ziel bleibt:

```text
AE2-artiges Netzwerk / Storage / Terminal
+
SFM-artige benannte Maschinenzugriffe
+
zentrale echte Programmiersprache
+
persistente Worker und Jobs
+
Event-System
+
spielerdefinierte Multiblocks
+
programmierbare Displays
+
Wireless / Notifications
```

Die wichtigste Architekturentscheidung nach der Prüfung lautet:

> Wartender Code darf Serverneustarts überleben, aber suspendierbare Abläufe werden als explizite Continuations / State Machines persistiert, statt einen beliebigen Java-Stack zu serialisieren.

Die wichtigste Produktentscheidung lautet:

> Die Vision wird nicht in getrennte Mini-Mods zerlegt. Stattdessen entsteht zuerst ein kleiner vertikaler Slice, der Netzwerk, Storage, Connector, Terminal, Sprache, Worker, Events und Persistence bereits gemeinsam zeigt.

Damit bleibt das Projekt von Anfang an als genau die Mod erkennbar, die langfristig gebaut werden soll.
