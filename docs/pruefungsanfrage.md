# Programmable Factory Network Mod — Stand zur externen Prüfung

Dieses Dokument fasst ein Entwurfsgespräch zusammen und ist für einen zweiten,
unabhängigen Blick gedacht. Es ist ohne Vorwissen lesbar; das ausführliche
Konzept liegt daneben in `mc_factory_network_mod_concept.md` (1563 Zeilen) und
sollte bei einer gründlichen Prüfung mitgelesen werden.

Stand: 2026-08-19

---

## 1. Worum es geht

Eine neue Minecraft-Mod für **Minecraft 1.21.1 mit NeoForge**. Sie soll ein
„Factory Operating System" sein und verbindet Funktionen, die es bisher nur
getrennt in mehreren Mods gibt:

- ein **Netzwerk** mit Kabeln, Channels, digitalen Item- und Fluidspeichern und
  einem zentralen Terminal (vergleichbar mit Applied Energistics 2),
- **benannte Connectoren**: ein Block an einer Maschine, der ihr einen Namen
  gibt und sie im Netzwerk ansprechbar macht (vergleichbar mit
  SuperFactoryManager),
- eine **zentrale Programmiersprache**, in der die gesamte Fabriklogik an einer
  Stelle geschrieben wird (statt vieler kleiner Skripte),
- **persistente Worker und Aufträge**, die Serverneustarts und Codeänderungen
  überleben,
- **ereignisgetriebene Automatisierung** statt Abfrageschleifen,
- **spielerdefinierte Multiblocks**, **Dashboards** und eine **In-Game-IDE**.

Es handelt sich nicht um einen Fork, sondern um eine eigenständige Mod in einem
eigenen Repository.

**Motivation des Projektinhabers, wörtlich:** „einfach weil es mal was ist was
es noch nicht so in dem Umfang gibt." Es ist also ein Ambitionsprojekt, kein
Produkt mit identifizierter Marktlücke. Das ist eine legitime Motivation und
soll bei der Prüfung nicht als Mangel behandelt werden — wohl aber bei der
Frage, wie man ein solches Projekt am Leben hält.

---

## 2. Getroffene Entscheidungen

### 2.1 Umfang der ersten spielbaren Fassung

**Entschieden: Netzwerk, Speicher, Terminal und Sprache gemeinsam.**

Ein kleinerer Schnitt wurde ausdrücklich abgelehnt. Zwei Alternativen standen
zur Wahl und wurden verworfen:

- nur Connectoren plus Sprache, ohne digitalen Speicher (in Wochen erreichbar),
- nur Netzwerk plus Speicher plus Terminal, ohne Sprache.

Begründung des Projektinhabers: Eine Teilfassung würde sich noch nicht nach der
Vision anfühlen.

**Benanntes Risiko:** Auf einer Strecke von Monaten ohne spielbares Ergebnis
stirbt ein Projekt selten an einem technischen Problem, sondern daran, dass
nichts zurückkommt. Als Gegenmaßnahme wurde vereinbart, unterwegs Sichtbarkeit
herzustellen — Tests, die man im Spiel laufen sieht, und Kreativmodus-Aufbauten
mit funktionierenden Teilstücken.

### 2.2 Laufzeitmodell — die folgenreichste Entscheidung

**Entschieden: Wartender Code überlebt einen Serverneustart.**

Ein Ablauf, der mitten in einer Funktion auf ein Ereignis wartet, macht nach
einem Neustart genau dort weiter.

```
on OreBatchReady(item, amount) {
    crushers.distribute(item, amount)
    let result = await BatchFinished where id == jobId   // kann minutenlang warten
    storage.insert(result)                               // läuft auch nach Neustart
}
```

Zunächst war das Gegenteil entschieden (wartender Code stirbt, nur Aufträge
überleben als Daten) und wurde auf Wunsch revidiert.

**Technische Konsequenz:** Die Runtime kann kein gewöhnlicher Interpreter sein,
der Javas eigenen Aufrufstapel nutzt. Sie braucht einen eigenen, expliziten
Stapel aus Frames — lokale Variablen, Programmzähler, Aufrufkette —, der sich
vollständig serialisieren und wiederherstellen lässt. Das muss von der ersten
Zeile an so gebaut sein.

**Argument dafür:** ComputerCraft verliert bei Serverneustarts den Zustand
seiner Computer und startet Programme neu. Ein Ablauf, der über Tage läuft und
Neustarts übersteht, existiert in keiner bekannten Minecraft-Mod. Das wäre
tatsächlich neu.

### 2.3 Deploy bei wartendem Code

**Entschieden: Der Spieler wird gefragt.**

Ändert ein Deploy eine Funktion, in der gerade ein Ablauf wartet, zeigt das
Terminal die betroffenen Abläufe und lässt die Wahl zwischen Abbrechen und
Weiterlaufen mit der alten Fassung.

Verworfen wurden: automatisches Weiterlaufen mit der alten Fassung,
automatischer Abbruch, und der Versuch einer Migration auf die neue Fassung.

**Offen:** Verhalten bei einem Deploy ohne anwesenden Spieler; Sammelentscheidung
bei vielen betroffenen Abläufen.

### 2.4 Grenzen für Nutzercode

**Entschieden: Rechenbudget je Tick, Unterbrechung statt Blockade.**

Jeder Ablauf bekommt ein Budget an Rechenschritten pro Tick. Ist es
aufgebraucht, wird er unterbrochen und im nächsten Tick fortgesetzt. Eine
Endlosschleife blockiert den Server also nie, sie läuft nur ewig langsam.
Überschreitet ein Ablauf eine Gesamtgrenze, wird er angehalten und im Terminal
als Fehler angezeigt.

Das ist mit dem expliziten Stapel aus 2.2 nahezu geschenkt: Wer den Zustand
nach jedem Schritt speichern kann, kann auch nach jedem Schritt anhalten.

### 2.5 Charakter der Sprache

**Entschieden: zweigeteilt.**

Worker, Gruppen und Multiblocks werden **deklariert** — man beschreibt einen
Sollzustand, das System hält ihn:

```
worker quarry_import {
    from quarry_output
    to storage
    filter "#c:ores"
}
```

Funktionen und Ereignisbehandlung sind **imperativ**, mit Bedingungen und
Schleifen:

```
fn keepStock(item, amount) {
    if storage.count(item) < amount {
        craft(item, amount - storage.count(item))
    }
}
```

Vorteil: Der Dauerbetrieb bleibt beschreibend und damit für das System
optimierbar, während Sonderfälle frei programmierbar sind.

### 2.6 Typsystem

**Entschieden: Typen ja, Angabe nein.**

Das System kennt Typen und prüft sie vor dem Deploy, leitet sie aber selbst her.
`let count = storage.count(iron)` genügt. Angegeben werden Typen nur bei
Parametern von Funktionen und Ereignissen.

Begründung: Die zugesagten IDE-Funktionen — Autovervollständigung, Hover-Infos,
Fehlermeldungen wie „Unknown connector: cruhser_1, did you mean crusher_1?" —
setzen statische Kenntnis voraus. Vollständige Typangaben wären für die
Zielgruppe zu viel Schreibarbeit.

---

## 3. Was ich am Konzept für stark halte

Damit die Prüfung nicht nur nach Schwächen sucht — vier Punkte, die keine
Wunschliste sind, sondern echte Entwurfsentscheidungen:

1. **Die Schichtung** (Konzept, Abschnitt 21): Terminal → Compiler → Runtime →
   Netzwerk-API → Connectoren, mit der klaren Regel „Connectoren sind bewusst
   dumm, die Intelligenz sitzt zentral".
2. **Die drei Automationsarten** (18.1): direkte Aktionen, Ereignisse,
   persistente Worker. Besonders die Einsicht, dass Dauertransfers keine
   Nutzerschleifen sein dürfen, sondern optimierte Worker.
3. **Der Umgang mit Deployments** (7.3, 19): Laufende Aufträge überleben
   Codeänderungen, mit Plan-Snapshot und Revisionsnummer.
4. **Der Netzwerk-Snapshot für die IDE** (14.4): Autovervollständigung arbeitet
   auf einem lokalen Abbild statt auf Serverabfragen pro Tastendruck.

---

## 4. Meine Bedenken — bitte gezielt prüfen

### 4.1 Der Umfang

Das Konzept beschreibt konzeptionell fünf Projekte in einem: Netzwerk mit
Speicher (AE2s Kern), Sprache mit Compiler und Runtime (ComputerCraft),
Autocrafting mit Dependency-Auflösung und Reservierungen (AE2s komplexestes
Subsystem), eine In-Game-IDE mit Autovervollständigung, sowie spielerdefinierte
Multiblocks mit Instanzen.

AE2 hat für seinen Teil über zehn Jahre gebraucht, SuperFactoryManager für
seinen über 2400 Commits.

Auch die im Konzept vorgeschlagene Phase 1 (Controller, Kabel, Channels,
Connector, Speicherplatten, Terminal) ist für sich bereits AE2s Kern.

### 4.2 Persistente Abläufe als Priorität

Die Entscheidung aus 2.2 ist die technisch aufwendigste des ganzen Entwurfs.
Sie verlangt eine eigene virtuelle Maschine, bevor irgendetwas anderes
funktioniert.

Die Frage, die ich nicht sicher beantworten kann: Ist ein Ablauf, der einen
Serverneustart mitten im Warten übersteht, wirklich das, was Spieler brauchen —
oder genügt in der Praxis, dass Aufträge und Worker als Daten überleben, wie es
die zunächst getroffene und dann revidierte Entscheidung vorsah?

### 4.3 Autocrafting wird unterschätzt

Das Konzept behandelt den Crafting-Scheduler (Abschnitt 6.3) in wenigen
Absätzen: Rezeptsuche, Dependency-Auflösung, Sub-Crafts, Reservierungen,
Maschinenauswahl, Fehlerzustände, Wiederaufnahme nach Neustart. Das ist in AE2
das Subsystem mit den meisten Fehlerberichten und der längsten Entwicklungszeit.

### 4.4 Die Sprache ist unterspezifiziert

Charakter und Typsystem sind jetzt entschieden, die konkrete Syntax nicht. Die
Sprache bestimmt aber Runtime, IDE, Autovervollständigung, Worker-Definitionen
und Ereignissystem gleichermaßen.

### 4.5 Kein spielbares Ergebnis auf Monate

Siehe 2.1. Ich halte das für das größte nichttechnische Risiko des Projekts.

---

## 5. Noch offen

1. Konkrete Syntax der Sprache.
2. Speichermodell: wie Bestände abgelegt und abgefragt werden, ohne bei jedem
   Zugriff alles zu durchlaufen. (Aus einem Vorprojekt liegen dazu Messungen
   vor: Ein slot-basierter Zugriff auf ein Netz mit 10.000 Ressourcenarten
   kostete 65 ms pro Auswertung — mehr als ein ganzer Server-Tick von 50 ms.
   Ein schlüsselbasierter Zugriff blieb unabhängig von der Netzgröße.)
3. Netzwerktopologie: wie Kabel, Controller, Channels und Grenzen technisch
   funktionieren.
4. Erkennung von Maschinenrezepten aus fremden Mods — Voraussetzung für
   Autocrafting.
5. Strukturdefinition für spielerdefinierte Multiblocks.

---

## 6. Konkrete Fragen an die Prüfung

1. **Ist die Entscheidung aus 2.2 (persistente Abläufe mit serialisierbarem
   Aufrufstapel) den Aufwand wert** — oder wäre sie ein klassischer Fall von
   Aufwand an der falschen Stelle, gemessen daran, was Spieler tatsächlich tun?

2. **Gibt es einen Weg, die erste spielbare Fassung kleiner zu schneiden**, der
   dem Anspruch „etwas, das es so noch nicht gibt" trotzdem gerecht wird? Der
   Projektinhaber hat zwei Vorschläge dazu abgelehnt.

3. **Übersehen wir eine bestehende Lösung?** Gibt es Mods, die Teile davon
   bereits können — insbesondere zentrale Programmierung von Maschinen über
   benannte Geräte hinweg —, an denen man sich orientieren oder die man
   erweitern könnte, statt neu zu bauen?

4. **Ist die Zweiteilung der Sprache (2.5) tragfähig**, oder führt sie dazu,
   dass Spieler ständig raten müssen, welcher Teil gerade gilt?

5. **Welche Reihenfolge der Bausteine** würde die Wahrscheinlichkeit maximieren,
   dass dieses Projekt fertig wird?

6. **Was fehlt im Konzept**, das bei einer Mod dieser Art erfahrungsgemäß
   wehtut? Kandidaten, die mir aufgefallen sind: Mehrspielerbetrieb und
   Rechteverwaltung, Verhalten bei nicht geladenen Chunks (das Konzept
   delegiert das ausdrücklich nach außen), Migration von Nutzercode bei
   Mod-Updates.
