# Factory Network

Eine Minecraft-Mod für **1.21.1 / NeoForge**, die eine Fabrik zu einem
programmierbaren System macht.

Maschinen bekommen einen Connector und damit einen Namen. Die gesamte Logik
wird an einer Stelle geschrieben — in **Manifold**, einer Sprache, die für
Fabriken gemacht ist, nicht für Allgemeines. Was dauerhaft laufen soll, wird
als Worker beschrieben statt als Schleife. Was auf etwas wartet, überlebt
einen Serverneustart.

```
worker quarry_import {
    from quarry_output
    to storage
}

on redstone_changed(tank_sensor, strength) {
    if strength >= 12 {
        pumps.stop()
    }
}
```

Der Name der Mod ist ein Arbeitstitel, der Name der Sprache nicht.

## Stand

**Die Sprache ist spezifiziert und übersetzt; ein erster vertikaler Schnitt
läuft im Spiel.**

Was steht:

| | |
|---|---|
| Sprache | Spezifikation und formale Grammatik vollständig |
| Übersetzer | Lexer und Parser von Hand, mit Fehlerbehebung — 54 Tests |
| Netzwerk | Controller, Kabel, Connector, Terminal; Graph über Flood-Fill |
| Speicher | schlüsselbasiert, nicht slotbasiert |
| Laufzeit | Worker bewegen Gegenstände zwischen Connector und Speicher |
| Editor | im Spiel, mit Syntaxfarben, Fehleranzeige und Vervollständigung |
| Prüfung | 6 GameTests in einer echten Welt, alle grün |

Was noch nicht läuft: der imperative Teil der Sprache — Funktionen,
Ereignisblöcke und `move` sind spezifiziert und werden geparst, aber noch
nicht ausgeführt. Ebenso Tags und Muster in Filtern, `maintain`, `when`,
Gruppen, Multiblocks und Displays.

Und die größte offene Zusage: **Wartender Code überlebt noch keinen
Serverneustart.** Die Continuations, die das leisten sollen, sind entworfen,
aber nicht gebaut.

## Dokumente

| Datei | Inhalt |
|---|---|
| [docs/konzept.md](docs/konzept.md) | **Das maßgebliche Dokument.** Vision, Architektur, Sprache, Laufzeitmodell, Entwicklungsreihenfolge. |
| [docs/sprache.md](docs/sprache.md) | **Die Spezifikation von Manifold.** Grundform, Schreibweise von Gegenständen, Typen, Warten und Grenzen. Maßgeblich für Compiler, Laufzeit und Editor. |
| [docs/grammatik.md](docs/grammatik.md) | **Die formale Grammatik.** EBNF für Lexer und Parser. Genauer als die Sprachspezifikation, aber ohne Begründungen. |
| [docs/entscheidungen.md](docs/entscheidungen.md) | Getroffene Entscheidungen mit Begründung und Alternativen, die verworfen wurden. Bei Widersprüchen zum Konzept gilt diese Datei, weil sie jünger ist. |
| [docs/konzept-entwurf-erste-fassung.md](docs/konzept-entwurf-erste-fassung.md) | Die ursprüngliche Fassung vor der externen Prüfung. Historie, nicht mehr maßgeblich. |
| [docs/pruefungsanfrage.md](docs/pruefungsanfrage.md) | Womit die externe Prüfung beauftragt wurde. Erklärt, welche Fragen offen waren. |
| [docs/referenz-messung-speicherzugriff.md](docs/referenz-messung-speicherzugriff.md) | Messung aus einem Vorprojekt: Wie teuer der Zugriff auf einen großen Netzwerkbestand wird, wenn man ihn slot-basiert modelliert. Gilt hier unverändert. |

## Die drei Entscheidungen, die alles andere bestimmen

**Wartender Code überlebt Serverneustarts.** Ein Ablauf, der auf ein Ereignis
wartet, macht nach einem Neustart genau dort weiter. Umgesetzt über
Continuations, die der Compiler erzeugt — nicht über einen serialisierten
Aufrufstapel. Das gibt es in keiner bekannten Minecraft-Mod.

**Die Sprache ist zweigeteilt.** Dauerhaftes wird deklariert und ist damit für
das System optimierbar; Abläufe werden programmiert und sind damit frei. Typen
kennt das System, hinschreiben muss sie niemand.

**Die erste Fassung ist ein vertikaler Schnitt.** Netzwerk, Speicher, Terminal,
Sprache, Worker, Events und Persistenz gemeinsam — jedes minimal. Nicht erst
das Fundament und irgendwann der Rest.

## Verhältnis zu bestehenden Mods

Kein Fork, keine Abhängigkeit. Applied Energistics 2 und SuperFactoryManager
haben Teile dessen gelöst, was hier zusammengehört — das eine ein Netzwerk mit
Speicher, das andere benannte Maschinenzugriffe mit einer kleinen Sprache. Die
Idee ist, beides zusammen zu entwerfen statt zu kombinieren, und eine echte
Programmiersprache darüberzulegen.
