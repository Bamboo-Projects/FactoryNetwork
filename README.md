# Factory Network

Eine Minecraft-Mod für **1.21.1 / NeoForge**, die eine Fabrik zu einem
programmierbaren System macht.

Maschinen bekommen einen Connector und damit einen Namen. Die gesamte Logik
wird an einer Stelle geschrieben — in **Manifold**, einer Sprache, die für
Fabriken gemacht ist, nicht für Allgemeines. Was dauerhaft laufen soll, wird
als Worker beschrieben statt als Schleife. Was auf etwas wartet, überlebt
einen Serverneustart.

```
worker erz_import {
    from quarry_output
    to storage
}

worker ofen_versorgung {
    from storage
    to ofen
    filter item:coal
    maintain 16
}

on redstone_changed(tank_sensor, stärke) {
    if stärke >= 12 {
        move 512 item:iron_ore from storage to brecher
    }
}
```

Der Name der Mod ist ein Arbeitstitel, der Name der Sprache nicht.

## Stand

**Die Mod läuft im Spiel: Netzwerk, Speicher, Sprache, Worker, Abläufe,
Anzeigen und Strom — im Einzelspieler wie auf einem eigenen Server.**

| | |
|---|---|
| Sprache | Spezifikation, formale Grammatik, Lexer und Parser von Hand |
| Worker | `from`, `to`, `filter`, `maintain`, `rate`, `when`, `priority`, `strategy`, `overflow` |
| Auswahl | Gegenstände, Flüssigkeiten, Tags, Muster an jeder Stelle, `except` |
| Funktionen | Bedingungen, Schleifen, `move`, Redstone lesen und setzen, Ereignisse |
| Abläufe | `await` auf Ereignis und Zeit, mit Frist und `else` — überleben den Serverneustart |
| Globale Werte | `global`, geteilt über alle Dateien, im Netz gespeichert |
| Gruppen | als Verteilziel eines Workers, mit Strategie |
| Anlagen | `multiblock` als Vorlage, mehrfach in der Welt erkannt |
| Anzeigen | `display` an der Wand und im Terminal, mit Balken, Lämpchen und Knöpfen |
| Netzwerk | Controller, Kabel mit Bandbreitenlimit, Connector, Terminal, Laufwerke mit Zellen, Strom |
| Speicher | schlüsselbasiert, nicht slotbasiert — der Grund steht in der Referenzmessung |
| Editor | BambooCEF/Monaco am Terminal: Projektdateien, Tabs, Compilerdiagnosen, Speichern und Übernehmen; klassischer Editor als Rückfall |
| Dazu | ein Projektordner neben der Welt und eine Erweiterung für VS Code |
| Prüfung | 611 Einheitstests und 369 GameTests erfolgreich; Web-Editor zusätzlich mit Node und Chromium geprüft |

Der aktuelle Stand **0.1.2** mit Worker-Wartegründen, getrennter Testmod und
dediziertem Serverprüfstand steht in [docs/stabilisierung-0.1.2.md](docs/stabilisierung-0.1.2.md).
Die ursprüngliche BambooCEF-Integration und die Gerätegestaltung sind in
[docs/editor-integration.md](docs/editor-integration.md) dokumentiert.

[docs/offene-punkte.md](docs/offene-punkte.md) enthält offene Fragen und die
Entwicklungsgeschichte. Ältere Einträge darin sind keine aktuelle Funktionsliste;
für die jüngste Abnahme gilt der oben verlinkte Prüfstand.

## Dokumente

| Datei | Inhalt |
|---|---|
| [docs/sprache.md](docs/sprache.md) | **Die Spezifikation von Manifold.** Grundform, Schreibweise von Gegenständen, Typen, Warten und Grenzen. Maßgeblich für Compiler, Laufzeit und Editor. |
| [docs/beispiele.md](docs/beispiele.md) | Programme, die laufen — jedes davon wird von den Tests übersetzt. Der schnellste Einstieg. |
| [docs/grammatik.md](docs/grammatik.md) | **Die formale Grammatik.** EBNF für Lexer und Parser. Genauer als die Sprachspezifikation, aber ohne Begründungen. |
| [docs/entscheidungen.md](docs/entscheidungen.md) | Getroffene Entscheidungen mit Begründung und Alternativen, die verworfen wurden. Bei Widersprüchen zum Konzept gilt diese Datei, weil sie jünger ist. |
| [docs/umsetzung.md](docs/umsetzung.md) | **Stand und Anleitung.** Wie man es startet und im Spiel benutzt, und was bei der Umsetzung auffiel. |
| [docs/offene-punkte.md](docs/offene-punkte.md) | Was noch aussteht, und was davon eine Entscheidung braucht statt Arbeit. |
| [docs/strom.md](docs/strom.md) | Wie Strom durch das Netz geht und was an seiner Verteilung noch fehlt. |
| [docs/globale-werte.md](docs/globale-werte.md) | Warum es `global` gibt und wie weit die Prüfung ohne Typsystem reicht. |
| [docs/geraeteerkennung.md](docs/geraeteerkennung.md) | Wie der Editor erkennt, welche Maschine an einem Connector hängt. |
| [docs/umfeld-atm10.md](docs/umfeld-atm10.md) | Was ein großes Modpack an Nachbarn mitbringt und was davon zu unterstützen ist. |
| [docs/schrift.md](docs/schrift.md) | Wie in diesem Projekt geschrieben wird — Code, Kommentare und Doku. |
| [docs/konzept.md](docs/konzept.md) | Die ursprüngliche Vision in ganzer Breite. Vieles davon ist gebaut, manches anders entschieden — bei Widersprüchen gilt `entscheidungen.md`. |
| [docs/konzept-entwurf-erste-fassung.md](docs/konzept-entwurf-erste-fassung.md) | Die Fassung vor der externen Prüfung. Historie, nicht mehr maßgeblich. |
| [docs/pruefungsanfrage.md](docs/pruefungsanfrage.md) | Womit die externe Prüfung beauftragt wurde. Erklärt, welche Fragen offen waren. |
| [docs/referenz-messung-speicherzugriff.md](docs/referenz-messung-speicherzugriff.md) | Messung aus einem Vorprojekt: Wie teuer der Zugriff auf einen großen Netzwerkbestand wird, wenn man ihn slotbasiert modelliert. Gilt hier unverändert. |

Im Spiel steht dieselbe Auskunft als Handbuch: die Seiten unter
`assets/factorynetwork/guide/`, gerendert von GuideME.

## Die drei Entscheidungen, die alles andere bestimmen

**Wartender Code überlebt Serverneustarts.** Ein Ablauf, der auf ein Ereignis
wartet, macht nach einem Neustart genau dort weiter. Umgesetzt über
Continuations, die der Compiler erzeugt — nicht über einen serialisierten
Aufrufstapel. Das gibt es in keiner bekannten Minecraft-Mod.

**Die Sprache ist zweigeteilt.** Dauerhaftes wird deklariert und ist damit für
das System optimierbar; Abläufe werden programmiert und sind damit frei.
Typen stehen nur dort, wo ein Wert von außen hereinkommt — an den Parametern
einer Funktion und eines Ereignisses. Innerhalb eines Ablaufs schreibt sie
niemand hin.

**Die erste Fassung ist ein vertikaler Schnitt.** Netzwerk, Speicher, Terminal,
Sprache, Worker, Events und Persistenz gemeinsam — jedes minimal. Nicht erst
das Fundament und irgendwann der Rest.


## Verhältnis zu bestehenden Mods

Kein Fork, keine Abhängigkeit. Applied Energistics 2 und SuperFactoryManager
haben Teile dessen gelöst, was hier zusammengehört — das eine ein Netzwerk mit
Speicher, das andere benannte Maschinenzugriffe mit einer kleinen Sprache. Die
Idee ist, beides zusammen zu entwerfen statt zu kombinieren, und eine echte
Programmiersprache darüberzulegen.
