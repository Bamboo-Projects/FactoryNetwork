# Programmable Factory Network Mod – Konzept & Architektur

## 1. Zielbild

Die Mod kombiniert mehrere bekannte Stärken aus existierenden Minecraft-Automationssystemen, ohne diese 1:1 zu kopieren:

- **AE2-artiges Netzwerk** mit Kabeln, Channels, digitalen Item-/Fluid-Speichern und einem zentralen Terminal.
- **SFM-artige Geräteadressierung** über benannte Connectoren: Ein Connector wird an eine Maschine gesetzt, bekommt einen Namen und macht die gesamte angeschlossene Maschine im Netzwerk verfügbar.
- **Echte zentrale Programmiersprache** statt vieler kleiner Skripte pro Maschine oder Bus.
- **Persistente Worker/Jobs** für dauerhafte oder wichtige Aufgaben wie Autocrafting und kontinuierliche Transfers.
- **Event-getriebene Automatisierung** für Redstone, Geräteänderungen, Produktionsereignisse und benutzerdefinierte Events.
- **Spielerdefinierte Multiblocks** mit eigenen internen Connectoren und wiederverwendbarer Logik.
- **Programmierbare Displays und Dashboards** für eigene UI-Ansichten.
- **Wireless Terminal + Push-Benachrichtigungen** für mobilen Zugriff und Statusmeldungen.

Der Mod soll sich eher wie ein **Factory Operating System** als wie ein reines Storage-System anfühlen.

---

## 2. Grundprinzipien

### 2.1 Zentrale Intelligenz

Die komplette Logik läuft zentral im Netzwerk bzw. im Projekt des Controllers/Terminals.

**Connectoren enthalten keine eigenen Skripte.**

Sie registrieren lediglich:

- ihren Namen,
- die angeschlossene Maschine,
- die erkannten Fähigkeiten/Capabilities,
- ihren Status,
- optional eine Projekt-/Multiblock-Zuordnung.

Beispiel:

```text
[Mekanism Crusher]
        │
   [Connector]
Name: crusher_1
        │
      Cable
```

Im Code ist anschließend z. B. verfügbar:

```text
crusher_1
```

### 2.2 Ein Connector pro Maschine

Ein Connector reicht aus, um die **gesamte Maschine** anzusprechen.

Der Connector ist nicht auf die Seite begrenzt, an der er physisch sitzt. Er verhält sich diesbezüglich wie SFM: Er kann die relevanten Seiten/Inventare/Capabilities des angeschlossenen Blocks ermitteln und darüber arbeiten.

Das bedeutet:

```text
crusher_1.insert(...)
```

kann intern eine gültige Input-Seite finden, ohne dass der Spieler mehrere Connectoren an verschiedene Seiten setzen muss.

### 2.3 Namen sind vollständig spielergesteuert

Der Spieler entscheidet selbst, wie Connectoren heißen.

Beispiele:

```text
crusher_1
mek_crusher
ore_line_a
hans
nether_crusher
```

Der Mod erzwingt kein Namensschema.

### 2.4 Chunkloading ist nicht Aufgabe des Mods

Der Mod lädt keine Chunks automatisch.

Wenn eine Maschine bzw. ein Connector in einem ungeladenen Chunk liegt, gilt er als offline/unverfügbar.

Externe Chunkloader oder Servermechaniken können dafür verwendet werden.

### 2.5 Dimensionen sind Infrastruktur, kein Programmier-Sonderfall

Dimensionen sollen für den Code möglichst transparent sein.

Eine Quantum-/Interdimensional-Verbindung verbindet Netzwerkbereiche über große Entfernung oder Dimensionsgrenzen hinweg.

Sobald die Verbindung steht, können Connectoren wie gewohnt angesprochen werden.

Der Spieler kann Namen wie `nether_crusher` nutzen, muss es aber nicht.

---

## 3. Netzwerkarchitektur

### 3.1 Kernkomponenten

Vorgesehene Grundkomponenten:

- Network Controller
- Network Cable
- Dense/Advanced Cable
- Storage Drive
- Item Storage Disk
- Fluid Storage Disk
- Connector
- Terminal
- Wireless Access Point
- Wireless Terminal
- Quantum Link / Quantum Bridge
- Crafting Unit / Crafting Matrix
- Multiblock Controller
- Programmable Display

### 3.2 Channels

Channels sollen als physische Netzwerklimitierung erhalten bleiben.

Beispielhafte Tiers:

```text
Basic Cable        -> 8 Channels
Dense Cable        -> 32 Channels
Backbone Cable     -> höhere Kapazität
Quantum Link       -> eigene Channel-Kapazität
```

Die genauen Werte sind Balancing-Sache und müssen nicht AE2 entsprechen.

### 3.3 Quantum-/Dimensionsverbindungen

Quantum Links verbinden entfernte Netzwerke bzw. Netzsegmente.

Beispiel:

```text
Overworld Network
      │
Quantum Link
      ║
Quantum Link
      │
Nether Network
```

Wichtig:

- keine automatische Chunkloading-Funktion,
- Channels werden durch den Link transportiert,
- Geräte im anderen Bereich sind nur erreichbar, wenn die relevanten Chunks geladen sind.

---

## 4. Connector-System

### 4.1 Aufgabe des Connectors

Der Connector ist die Netzwerk-Schnittstelle zu externen Blöcken und Maschinen.

Er übernimmt:

- Registrierung des Namens,
- Erkennung der angeschlossenen Maschine,
- Erkennung von Item-/Fluid-/Energy-/Chemical-/Redstone-Schnittstellen,
- Zugriff auf die gesamte Maschine und ihre gültigen Seiten,
- Statusinformationen wie online/offline,
- Bereitstellung von Events bzw. Zustandsänderungen,
- optional Processing-Rezepte für Autocrafting.

### 4.2 Mod-Kompatibilität

Kompatibilität sollte zweistufig aufgebaut sein.

#### Generic Capability Layer

Für möglichst viele Mods automatisch nutzbar:

- Items
- Fluids
- Energy
- Redstone
- weitere standardisierte Capabilities

#### Optional Mod Adapter Layer

Für tiefere Integration mit bekannten Mods:

- Mekanism
- Create
- Thermal
- GregTech-/Modern-Industrialization-artige Systeme
- weitere große Tech-Mods

Zusätzliche APIs könnten z. B. sein:

```text
machine.progress
machine.energy
machine.recipe
machine.enabled
machine.redstoneMode
machine.chemicalInput
machine.chemicalOutput
```

Unbekannte Mods sollen zumindest über den generischen Capability-Zugriff funktionieren, sofern sie Standard-Schnittstellen bereitstellen.

### 4.3 Maschinen mit Side Configuration

Viele Mods akzeptieren Items/Fluids nur auf bestimmten Seiten.

Der Connector soll deshalb intern die verfügbaren Seiten/Handler der Maschine prüfen und automatisch nur gültige Zugriffe ausführen.

Der Spieler muss physisch trotzdem nur **einen** Connector setzen.

---

## 5. Zentrales Terminal

Das Terminal ist der zentrale Arbeitsplatz für das komplette System.

Vorgesehene Tabs/Bereiche:

```text
[ Storage ] [ Crafting ] [ Code ] [ Network ] [ Dashboards ]
```

### 5.1 Storage-Ansicht

Die Bedienung darf sich stark an AE2 orientieren:

- zentrale Anzeige aller gespeicherten Items,
- Suchfeld,
- Sortierung,
- Item-Mengen,
- Einlagern und Entnehmen per Klick/Shift-Klick,
- Anzeige craftbarer Items,
- Filter wie Stored / Craftable / Both,
- EMI/JEI/REI-Integration,
- gleicher Workflow im Wireless Terminal.

Das Terminal ist für normalen Benutzerzugriff zuständig. Dafür ist **kein Script notwendig**.

### 5.2 Crafting-Ansicht

Craftbare Items sollen direkt im Storage-Terminal sichtbar sein.

Beim Start eines Crafts wird zunächst ein **Crafting Plan** berechnet.

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

Die GUI sollte anzeigen:

- was bereits vorhanden ist,
- was gecraftet werden muss,
- welche Sub-Crafts nötig sind,
- welche Items fehlen,
- welche Maschinen/Worker verwendet werden,
- welche Schritte laufen oder blockiert sind.

Zusätzlich zur Dependency-Ansicht soll es eine **Workflow-Ansicht** geben:

```text
Iron Ore
   ↓
crushers [Round Robin]
   ↓
Iron Dust
   ↓
furnaces [Least Filled]
   ↓
Iron Ingot
   ↓
Crafting Unit
   ↓
Machine Frame
```

Während eines laufenden Crafts soll die Ansicht live den Fortschritt darstellen.

---

## 6. Autocrafting

### 6.1 Normales Crafting

Für 2x2/3x3- bzw. normale Crafting-Rezepte soll es ein eigenes Netzwerkgerät geben, z. B.:

```text
Crafting Matrix
```

Es speichert digitale Crafting-Rezepte. Physische Pattern-Items sind nicht zwingend notwendig.

### 6.2 Processing-Rezepte

Maschinenrezepte gehören bevorzugt zum jeweiligen Connector bzw. zu der Maschine, die sie ausführen kann.

Beispiel:

```text
Connector: crusher_1

Recipes:
Iron Ore -> Iron Dust
Gold Ore -> Gold Dust
```

Mögliche Modi:

```text
Recipe Mode:
- Auto Detect
- Manual
```

`Auto Detect` nutzt bekannte Rezeptdaten/Mod-APIs.

`Manual` ermöglicht exotische oder nicht automatisch erkennbare Abläufe.

### 6.3 Zentraler Crafting Scheduler

GUI und Code greifen auf dieselbe zentrale Crafting-API zu.

Beispiel:

```text
craft("minecraft:iron_ingot", 1000)
```

und der Klick im Terminal auf "Craft 1000" sollen intern dieselbe Logik starten.

Der Scheduler übernimmt:

- Recipe Lookup,
- Dependency-Auflösung,
- Sub-Crafts,
- Ressourcenvorbelegung/Reservations,
- Maschinen-/Gruppenauswahl,
- Job-Steuerung,
- Fehlerzustände,
- Wiederaufnahme nach Restart/Update.

---

## 7. Persistente Worker und Jobs

### 7.1 Motivation

Laufende Autocrafting-Aufträge und wichtige Daueraufgaben dürfen bei folgenden Ereignissen nicht verloren gehen:

- Serverneustart,
- Welt reload,
- Code Deploy,
- Update des Mods,
- Änderung des Projekts.

Deshalb werden relevante Aufgaben als **persistente Worker/Jobs** gespeichert.

### 7.2 Crafting Job

Beispiel:

```text
CraftJob #1842
Target: 1000 Iron Ingots
Status: Running
Progress: 642 / 1000

Steps:
✓ Inputs reserved
→ Crusher processing
→ Furnace processing
□ Final delivery
```

Ein Crafting Job besitzt einen **Plan-Snapshot**.

Bestehende Jobs sollen nicht plötzlich ihre Bedeutung ändern, nur weil der User danach den Code oder ein Rezept editiert.

Beispiel:

```text
Job #1842 -> Plan Revision 6
Job #1901 -> Plan Revision 7
```

### 7.3 Verhalten bei Deploy

Bei einem Code Deploy:

```text
Scripts        -> reload
Event Listener -> reload
Timer          -> reload

Craft Jobs     -> KEEP
Transfer Jobs  -> KEEP
Reservations   -> KEEP
```

### 7.4 Transfer Worker

Für dauerhafte Transfers sollen optimierte Worker existieren, statt alles über Polling-Schleifen zu lösen.

Beispiel Quarry-Kiste:

```text
worker quarry_import {
    from quarry_output
    to storage
}
```

oder später syntaktisch kürzer:

```text
quarry_output.streamTo(storage)
```

Der Worker ist persistent und intern optimiert.

### 7.5 Weitere Worker-Beispiele

```text
worker generator_fuel {
    from storage
    to generators

    item "#minecraft:coals"
    maintain 64
    strategy round_robin
}
```

```text
worker quarry_import {
    from quarry_output
    to storage

    filter "#c:ores"
    priority 10
}
```

---

## 8. Gruppen

Connectoren und ggf. Multiblock-Instanzen können zu Gruppen zusammengefasst werden.

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

Zusätzlich soll ein einzelner Aufruf die Default-Strategie überschreiben können.

Beispiel:

```text
crushers.send(item, strategy: least_filled)
```

Gruppen können später auch dynamisch über Tags aufgebaut werden.

Beispiel:

```text
group crushers {
    match tag "crushers"
}
```

---

## 9. Programmiersprache

### 9.1 Ziel

Die Sprache soll eine echte, strukturierte Programmiersprache sein und keine reine Config-Syntax.

Vorgesehene Features:

- Variablen
- Funktionen
- Conditions
- Loops
- Collections
- Modules/Imports
- Typen
- Events
- Requests/Responses
- Await
- Worker
- Gruppen
- Multiblocks
- UI-/Display-Definitionen
- System-APIs

Sie soll aber verständlicher bleiben als Java oder Lua für typische Minecraft-Automation.

### 9.2 Beispielstruktur

```text
factory/
├─ main
├─ groups
├─ ore_processing
├─ power
├─ lib/
│  └─ stock
└─ multiblocks/
   ├─ ore_plant
   └─ steel_plant
```

### 9.3 Funktionen

```text
fn keepStock(item, amount) {
    if storage.count(item) < amount {
        craft(item, amount - storage.count(item))
    }
}
```

### 9.4 Collections

Systeme sollen Collections bereitstellen:

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

Darauf sollen typische Operationen möglich sein:

```text
filter
map
sort
first
count
sum
groupBy
```

Die genaue Syntax ist noch offen.

---

## 10. Event-System

### 10.1 System Events

Beispiele:

```text
redstone_changed
item_inserted
item_removed
inventory_changed
device_online
device_offline
crafting_started
crafting_finished
crafting_failed
```

### 10.2 Eigene Events

Spieler können eigene Events definieren:

```text
event OreBatchReady(item: Item, amount: Int)
```

Auslösen:

```text
emit OreBatchReady(iron_ore, 256)
```

Reagieren:

```text
on OreBatchReady(item, amount) {
    crushers.distribute(item, amount)
}
```

Events sollten typisiert sein.

### 10.3 Await auf Events

Workflows sollen auf Events warten können:

```text
let result = await BatchFinished where id == jobId
```

Das `where` ist bereits ein Filter. Ein zusätzliches `if` ist nur notwendig, wenn alle Events angenommen und anschließend unterschiedlich behandelt werden sollen.

Timeouts sollten möglich sein:

```text
let result = await BatchFinished
    where id == jobId
    timeout 30s
```

### 10.4 Request/Response Events

Zusätzlich zu Events sollen Request/Response-Aufrufe möglich sein.

Beispiel:

```text
request NeedMachine(item: Item) -> Device
```

Aufruf:

```text
let machine = await request NeedMachine(iron_ore)
```

Handler:

```text
on request NeedMachine(item) {
    reply crushers.first_available()
}
```

Unterscheidung:

```text
Event   -> fire and forget
Request -> erwartet Antwort
Worker  -> persistente Aufgabe
```

---

## 11. Redstone

Redstone ist kein Boolean, sondern ein Wert von **0 bis 15**.

Lesen:

```text
let strength = tank_sensor.redstone()
```

Schreiben:

```text
alarm.redstone(15)
```

Event-getrieben:

```text
on tank_sensor.redstone_changed(value) {
    if value >= 12 {
        pumps.stop()
    }
}
```

Das System muss sowohl analoge Redstone-Stärken lesen als auch ausgeben können.

---

## 12. Event Driven statt Polling

Für Custom-Logik und Systemreaktionen soll Event-Driven Design bevorzugt werden.

Schlechtes Standardmuster:

```text
every 10 ticks {
    check everything
}
```

Besser:

```text
on inventory_changed(quarry_output) {
    ...
}
```

oder:

```text
await crusher_1.output_changed
```

Für dauerhafte Hochdurchsatz-Aufgaben wird ein Worker statt einer Nutzerschleife verwendet.

Wenn ein externer Mod keine brauchbaren Änderungs-Callbacks bietet, darf der Connector intern einen optimierten Fallback verwenden. Das ist Implementierungsdetail und soll nicht auf den User abgewälzt werden.

---

## 13. Multiblocks

### 13.1 Grundidee

Multiblocks werden vom Spieler selbst gebaut und enthalten echte Maschinen/Blöcke, die innerhalb des Multiblocks eine gemeinsame Funktion erfüllen.

Beispiel:

```text
Ore Processing Plant

┌─────────────────────────────┐
│ Crusher -> Enricher -> Oven │
│                             │
│ Input                Output │
│                             │
│       Controller            │
└─────────────────────────────┘
```

### 13.2 Projekt = Multiblock-Definition

Ein Multiblock ist direkt ein eigenes wiederverwendbares Projekt.

Beispiel:

```text
OrePlant
├─ connector input
├─ connector crusher
├─ connector furnace
├─ connector output
└─ code
```

### 13.3 Instanzen

Ein Multiblock-Projekt kann mehrfach gebaut werden.

```text
OrePlant Projekt
├─ plant_1
├─ plant_2
└─ plant_3
```

Alle Instanzen nutzen denselben Code, besitzen aber eigene interne Connectoren.

### 13.4 Connector-Zuordnung

Ein Connector kann entweder netzwerkweit sichtbar sein oder einem Multiblock-Projekt zugeordnet werden.

Beispiel GUI:

```text
Name: crusher

Assignment:
- Global Network
- Multiblock Project: OrePlant
```

Ist er `OrePlant` zugeordnet, taucht er nur dort auf.

Dadurch können verschiedene Multiblock-Instanzen intern dieselben Connectornamen verwenden:

```text
plant_1
├─ crusher
├─ furnace
└─ output

plant_2
├─ crusher
├─ furnace
└─ output
```

### 13.5 Interne und externe Sicht

Innerhalb des Multiblock-Projekts:

```text
fn process(item) {
    crusher.insert(item)
    ...
    furnace.insert(...)
}
```

Im Hauptprojekt:

```text
plant_1.process(iron_ore)
plant_2.process(gold_ore)
```

Das Hauptprojekt muss die internen Geräte nicht kennen.

### 13.6 Definition der Struktur

Die genaue Strukturdefinition ist noch offen.

Mögliche Richtung:

- Multiblock Controller setzen
- Bereich/Struktur markieren
- Connectoren im Bereich einem Multiblock-Projekt zuordnen
- Projektdefinition prüft benötigte Rollen/Connectoren/Maschinen

Wichtig ist die Freiheit des Spielers: keine rein hartcodierten klassischen Multiblock-Muster als einzige Option.

---

## 14. Code Editor / Ingame IDE

### 14.1 Kein eingebettetes VS Code

Es soll keine echte VS-Code-Instanz eingebettet werden.

Stattdessen wird eine fokussierte Mini-IDE gebaut.

Ziel-Features:

- Syntax Highlighting
- Zeilennummern
- Tabs
- Projektbaum
- Autocomplete
- Typ-/Fehlerdiagnose
- Hover-Infos
- Suche
- Go-to-definition
- Find references
- Problems View
- Logs
- Runtime View
- Deploy
- Draft vs. Running Version

### 14.2 Beispiel-Layout

```text
┌─────────────────────────────────────────────────────────┐
│ Storage | Crafting | Code | Network | Dashboards      │
├────────────────┬────────────────────────────────────────┤
│ PROJECT        │ ore_processing                       │
│ main           │                                      │
│ groups         │ 1 fn processOre(item) {             │
│ lib/           │ 2     crushers.send(item)            │
│ multiblocks/   │ 3 }                                  │
│                │                                      │
│ NETWORK        │                                      │
│ crusher_1      │                                      │
│ crusher_2      │                                      │
│ furnace_1      │                                      │
├────────────────┴────────────────────────────────────────┤
│ Problems: 0 | Runtime: Running | Revision: 27         │
└─────────────────────────────────────────────────────────┘
```

### 14.3 Autocomplete

Bei:

```text
crusher_1.
```

z. B.:

```text
items
fluids
energy
redstone
online
insert()
extract()
```

Bei Gruppen:

```text
crushers.
```

z. B.:

```text
send()
distribute()
members()
available()
```

### 14.4 Network Schema Snapshot

Der Client soll nicht für jeden Tastendruck den Server abfragen.

Beim Öffnen/Refresh des Code-Tabs erhält der Editor einen Snapshot:

```text
NetworkSchema {
    devices
    groups
    multiblocks
    functions
    recipes
    itemIds
    types
}
```

Autocomplete und viele Diagnostics können damit lokal erfolgen.

### 14.5 Compiler Pipeline

Keine String-Hacks oder Zeilenparser.

Vorgesehene Pipeline:

```text
Source Code
   ↓
Lexer
   ↓
Parser
   ↓
AST
   ↓
Semantic Validation
   ↓
IR / Bytecode / Runtime Instructions
   ↓
Runtime
```

Fehler sollen hochwertig sein:

```text
Unknown connector: cruhser_1
Did you mean: crusher_1?
```

### 14.6 Draft und Running Version

Fehlerhafter neuer Code darf die laufende Fabrik nicht automatisch stoppen.

Workflow:

```text
Edit
↓
Compile / Validate
↓
Deploy
```

Die alte Version läuft weiter, solange die neue Version nicht erfolgreich deployed wurde.

---

## 15. Displays und Dashboards

### 15.1 Programmierbares UI-System

Displays sollen nicht primär pixelbasiert programmiert werden, sondern über UI-Komponenten.

Beispiel:

```text
display factory_status {
    title "Factory Status"

    row "Iron" storage.count(iron_ingot)

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
tree
```

### 15.2 Listen und Systemdaten

Der User soll auf Itemlisten, Worker, Crafting Jobs usw. zugreifen können.

Beispiel:

```text
display warehouse_screen {
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

Crafting Jobs:

```text
display crafting_screen {
    title "Active Crafting"

    list crafting.jobs()
        where status != completed {
        columns {
            item
            progress
            status
        }
    }
}
```

### 15.3 Crafting Tree

Der Dependency-Tree eines Crafting Jobs soll als Datenstruktur verfügbar sein:

```text
job.plan.steps
job.plan.dependencies
job.plan.missing
```

Optional soll es eine fertige UI-Komponente geben:

```text
craftingTree job
```

### 15.4 Interaktive Displays

Displays dürfen auch Buttons enthalten:

```text
button "Emergency Stop" {
    reactor_control.redstone(15)
}
```

oder:

```text
button "Start Quarry" {
    emit QuarryStart
}
```

### 15.5 Wiederverwendung auf mehreren Oberflächen

Dieselbe Dashboard-/UI-Definition soll möglichst auf mehreren Zielen nutzbar sein:

- Wanddisplay
- normales Terminal
- Wireless Terminal
- eventuell spätere HUDs

---

## 16. Wireless Terminal und Push Notifications

Das Wireless Terminal bietet dieselben Kernfunktionen wie das normale Terminal, abhängig von Reichweite bzw. Netzwerkzugriff.

Zusätzlich sollen Programme Push-Benachrichtigungen senden können.

Beispiel:

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

Benachrichtigungen können optional Aktionen anbieten:

```text
notify(
    title: "Quarry Full",
    message: "Output chest is blocked",
    actions: [
        action("Stop Quarry", emit StopQuarry)
    ]
)
```

Der Spieler soll Notification-Kategorien im Terminal konfigurieren können.

---

## 17. System-API für Code und Displays

Alles, was das System sinnvoll intern weiß, soll möglichst über eine saubere read-only API verfügbar sein.

Beispiele:

```text
storage.items()
storage.fluids()

crafting.jobs()
crafting.recipes()
crafting.job(id)

workers.all()

network.devices()
network.groups()
network.offlineDevices()

multiblocks.instances()

events.history()
```

Beispiel `CraftJob`:

```text
CraftJob {
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
}
```

---

## 18. Laufzeitmodell

### 18.1 Drei Automationsarten

#### Direkte Aktionen

```text
move 64 iron_ingot
from storage
to furnace
```

#### Events

```text
on redstone_changed(sensor) {
    ...
}
```

#### Persistente Worker

```text
worker quarry_import {
    from quarry_output
    to storage
}
```

### 18.2 Grundsatz

```text
Event
"Etwas ist passiert"
        ↓
User Code
"Was soll passieren?"
        ↓
Worker / Job
"Diese Aufgabe muss zuverlässig erledigt werden"
```

Das verhindert, dass alle Automationen als aggressive Polling-Schleifen gebaut werden müssen.

---

## 19. Update- und Persistenzanforderungen

Eines der wichtigsten Designziele ist, dass Updates und Deployments laufende Prozesse nicht zerstören.

Persistiert werden müssen mindestens:

- Crafting Jobs
- Job-Pläne / Plan Revision
- Worker-Zustände
- Reservations
- Fortschritt
- ggf. Retry-/Fehlerzustände
- relevante Multiblock-Instanzdaten

Ein Mod-Update sollte diese Daten über saubere Versionierung/DataFixer/Migrationslogik weiterverwenden können.

User-Code wird separat versioniert und deployed.

Laufende persistente Jobs dürfen nicht stillschweigend gelöscht werden, nur weil sich der Quellcode geändert hat.

---

## 20. Beispiel: komplette kleine Fabrik

### Netzwerk

```text
Controller
├─ Storage Drives
├─ Terminal
├─ crusher_1
├─ crusher_2
├─ crusher_3
├─ quarry_output
├─ alarm
└─ ore_plant_1
```

### Gruppe

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

### Quarry Import

```text
worker quarry_import {
    from quarry_output
    to storage
}
```

### Redstone Alarm

```text
on reactor_sensor.redstone_changed(value) {
    if value >= 12 {
        alarm.redstone(15)

        notify(
            title: "Reactor Warning",
            message: "Signal strength: " + value,
            level: critical
        )
    }
}
```

### Eigene Events

```text
event OreBatchReady(item: Item, amount: Int)
```

```text
on OreBatchReady(item, amount) {
    crushers.distribute(item, amount)
}
```

### Crafting

```text
fn maintainSteel() {
    if storage.count(steel_ingot) < 1000 {
        craft(steel_ingot, 1000 - storage.count(steel_ingot))
    }
}
```

### Dashboard

```text
display factory_overview {
    title "Main Factory"

    grid {
        stat "Stored Items" storage.items().count()
        stat "Running Crafts" crafting.jobs().where(status == running).count()
        stat "Workers" workers.all().where(status == running).count()
        stat "Offline Devices" network.offlineDevices().count()
    }

    list crafting.jobs()
        where status != completed {
        item
        progress
        status
    }
}
```

---

## 21. Technische Trennung der Verantwortlichkeiten

```text
Terminal / IDE
      │
      ▼
Project Source
      │
   Compiler
      │
      ▼
Runtime Program
      │
      ├──────── Event Runtime
      ├──────── Worker Runtime
      ├──────── Crafting Scheduler
      └──────── UI/Dashboard Runtime
                 │
                 ▼
             Network API
                 │
        ┌────────┼────────┐
        ▼        ▼        ▼
   Connector   Storage  Multiblock
        │
        ▼
External Mod Machines
```

Connectoren sind bewusst relativ dumm.

Controller/Runtime enthält die eigentliche Intelligenz.

---

## 22. Offene Designentscheidungen

Folgende Punkte sind noch nicht final entschieden und sollten als Nächstes konkretisiert werden:

1. **Exakte Syntax der Sprache**
   - eher SFM-artig/deklarativ,
   - eher objektorientiert,
   - oder Hybrid.

2. **Typensystem**
   - statisch vs. dynamisch,
   - Item, Fluid, Chemical, Device, Group, Job, Event etc.

3. **Runtime-Ausführungsmodell**
   - AST Interpreter,
   - eigener Bytecode,
   - VM/Coroutine-Modell,
   - Worker Scheduling.

4. **Event-Quellen und Fallbacks**
   - welche Minecraft-/NeoForge-Events sind verfügbar,
   - wann ist Polling intern notwendig.

5. **Recipe Detection**
   - welche Rezepte lassen sich automatisch aus Vanilla/NeoForge/Mods lesen,
   - wie werden Sondermaschinen abstrahiert.

6. **Multiblock-Strukturdefinition**
   - Bereichsmarkierung,
   - Rollen,
   - automatische Instanzerkennung,
   - Validierung der Struktur.

7. **IDE-Widget-Implementierung**
   - eigener Texteditor,
   - Syntax Highlighting,
   - Completion Engine,
   - Client/Server Synchronisierung.

8. **Persistenz & Migration**
   - SavedData/Attachments/Capabilities,
   - Versionsnummern,
   - Job-Migrationsstrategie.

9. **Security / Server Limits**
   - maximale CPU-Zeit pro Tick,
   - Worker Limits,
   - Speicherlimits,
   - Endlosschleifen,
   - Rechte/Ownership.

10. **Balancing**
    - Channels,
    - Kabel-Tiers,
    - Crafting-Kapazität,
    - Worker-Kapazität,
    - Quantum Links,
    - Wireless Range.

---

## 23. Empfohlene nächste Schritte für die Implementierung

### Phase 1 – Minimaler Netzwerk-Kern

- Controller
- Kabel
- Channels
- Connector
- Namensregistrierung
- Item Capability Zugriff
- Storage Disks
- einfaches Storage Terminal

### Phase 2 – Code Runtime

- Lexer/Parser
- AST
- Variablen/Funktionen
- Device API
- einfacher `move` / `insert` / `extract`
- Compilerdiagnosen
- Draft/Deploy

### Phase 3 – Events & Worker

- Event Bus
- Redstone 0–15
- eigene Events
- `await`
- persistente Transfer Worker
- Server-Restart Recovery

### Phase 4 – Autocrafting

- normales Crafting
- Processing Recipes an Connectoren
- Dependency Planner
- Reservations
- persistente Craft Jobs
- Crafting Plan GUI

### Phase 5 – Multiblocks

- Multiblock-Projekte
- Instanzen
- interne Connector-Zuordnung
- öffentliche API/Funktionen
- Gruppen von Multiblock-Instanzen

### Phase 6 – IDE & Dashboards

- Projektbaum
- Syntax Highlighting
- Autocomplete
- Hover/Diagnostics
- Find References
- Displays
- Listen/Charts/Buttons
- Wireless Dashboards
- Push Notifications

---

## 24. Kurzfassung für die Architektur

Das System soll folgende Kernidee verfolgen:

> **Hardware wird angeschlossen und benannt. Logik wird zentral programmiert. Standardabläufe laufen als persistente Worker. Änderungen werden über Events verarbeitet. Autocrafting ist ein persistenter Scheduler mit sichtbarem Dependency- und Workflow-Plan. Multiblocks sind wiederverwendbare Projekte mit eigenen internen Connectoren. Das Terminal ist gleichzeitig Storage UI, Crafting UI, IDE, Netzwerkdiagnose und Dashboard-Zentrale.**

