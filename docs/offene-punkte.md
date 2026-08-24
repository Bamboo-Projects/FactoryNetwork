# Offene Punkte — Bestandsaufnahme

Alles, was in `docs/` und im Code als unfertig steht, an einer Stelle.

Stand: 2026-08-24

**Status:** **F** = fehlt schlicht · **E** = wartet auf eine Entscheidung ·
**Z** = bewusst zurückgestellt, kein Versäumnis

---

## 1. Sprache

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 1.1 | Gerätemitglieder `insert()`, `items()`, `output()`, `send()`, `busy` sind spezifiziert, der Interpreter kennt nur `online`, `name`, `redstone()`, `count()` | E/F | `sprache.md:325`, `Interpreter.java:699-747` | groß | nur `busy` blockiert — es gibt keine Capability dafür. Der Rest ist frei |
| 1.2 | Listen und Mengen: `storage.items()`, `where`, `sort`, `first`, `sum`. `Expr.Lambda` wird geparst, der Interpreter wirft | F | `sprache.md:765`, `Interpreter.java:583` | groß | — |
| 1.3 | Flüssigkeits-Tags: `tag:` löst nur Gegenstands-Tags auf | E | `umsetzung.md:182` | klein | Schreibweise (`fluidtag:` oder `tag:` durchsucht beides) |
| 1.4 | Chemikalien: Schreibweise steht, Anbindung fehlt, Laufzeit wirft | F | `WorldHost.java:461` | mittel–groß | Mekanism als Abhängigkeit |
| 1.5 | `import`/Module — reserviert, tut nichts | Z | `Parser.java:86` | — | bewusst, bis ein Projekt den Namensraum sprengt |
| 1.6 | Request/Response als eigene Form | Z | `sprache.md:992` | mittel | mit `emit`/`on`/`await` nachbaubar |
| 1.7 | **Rechte im Mehrspielerbetrieb.** Im Code gibt es *keinerlei* Berechtigungsprüfung — nur Dateisperren | F | `sprache.md:999` | mittel | — |
| 1.8 | Die Typen `Set<T>`, `Job`, `Chemical` fehlen im Wertemodell | F | `sprache.md:308`, `Value.java:13` | mittel | teils 1.2, teils 1.4 |
| 1.9 | Echter Typprüfer über Ausdrücke (heute nur Literal gegen Literal) | E | `globale-werte.md:195` | groß | eigenes Vorhaben, beträfe die ganze Sprache |
| 1.10 | Konstanten (`const rate = 64`) neben dem veränderlichen `global` | E | `globale-werte.md:198` | klein | Entscheidung |
| 1.11 | Listen und Karten als globale Werte | E | `globale-werte.md:201` | mittel | 1.2 und Entscheidung |
| 1.12 | Einstellbare Grenzen für Nutzercode. Das Schrittbudget (500) und die Schrankplätze gibt es; „jeweils einstellbar" nicht | F | `entscheidungen.md:118` | groß | 4.2 — es gibt keine Konfiguration |

## 2. Laufzeit

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 2.1 | Globale Werte: Persistenz, Anzeige, Doku | F | `plan-globale-werte.md` | mittel | nichts — Plan liegt vor |
| 2.2 | **Stromverteilung und Energiezellen: nicht begonnen.** Kein `power`-Token, keine Abgabe | F | `strom.md` | groß | drei Entscheidungen sind gefallen, siehe `entscheidungen.md` |
| 2.3 | Abgabe bei `OFF`/`BOOTING`: „Netz füllt sich langsam, während Maschinen ziehen" nicht durchgerechnet | E | `strom.md:239` | klein | 2.2 |
| 2.4 | Einheit der Abgaberate: `per tick` gegen `per 5s` | E | `strom.md:249` | klein | 2.2 |
| 2.5 | Strom als Wert in der Sprache (`crusher_1.energy`) | F | `strom.md:228` | klein | 1.1 |
| 2.6 | **`device_done`:** gebaut ist Weg (2), offen ob Weg (1) dazukommt. Vor dem Bau zu entscheiden — ein falsches Fertig-Signal lässt eine Anlage Gegenstände verlieren | E | `umsetzung.md:160` | mittel | Entscheidung |
| 2.7 | `when`-Bedingungen: nur Zahlvergleiche mit Literalen und `storage.count(...)` | F | `WorkerRuntime.java:860` | mittel | teils 1.1 |
| 2.8 | `NetworkCheck` besucht keine Anweisungen — weder Seitenwarnung noch Namensprüfung erreichen ein `move` | F | `geraeteerkennung.md:316` | mittel | Entscheidung zur Namensprüfung bei `move` |
| 2.9 | Erkennung von Maschinen-Rezepten | F | `entscheidungen.md:131` | groß | — |
| 2.10 | Autocrafting — der letzte ausgegraute Reiter | F | `umsetzung.md:221` | groß | 2.9 |

## 3. Editor im Spiel

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 3.1 | Annahme-Probe: ob ein Fach einen Gegenstand nimmt | F | `geraeteerkennung.md:91` | mittel | — |
| 3.2 | Ob die Probe auch für Flüssigkeiten sinnvoll ist | E | `geraeteerkennung.md:336` | klein | 3.1 |
| 3.3 | Flüssigkeitsstände im Tooltip — der Rahmen steht, gefüllt ist er nicht | F | `plan-geraeteerkennung.md:2398` | klein | — |
| 3.4 | Knopf „Bearbeitung anfragen" | F | `umsetzung.md:490` | klein | — |
| 3.5 | Ordner im Projekt | E | `umsetzung.md:505` | mittel | Entscheidung |
| 3.6 | LDLib2 als UI-Grundlage — nicht einmal geprüft | E | `umsetzung.md:512` | klein | Entscheidung |
| 3.7 | Ob das Geräteprofil dem Analysator etwas zu geben hat | E | `geraeteerkennung.md:340` | klein | — |
| 3.8 | Ob der Netz-Reiter globale Werte ändern darf | E | `globale-werte.md:200` | klein | 2.1 |

## 4. VS-Code-Erweiterung

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 4.1 | Sprachserver — Fehlerprüfung und Gerätenamen außerhalb des Spiels | E/F | `umsetzung.md:493` | groß | 4.2, dazu Umfang und Technik |
| 4.2 | **Die Mod hat keine Konfiguration.** Weder Server- noch Clientteil | F | `entscheidungen.md:2313` | mittel | Voraussetzung für 4.1 und 1.12 |
| 4.3 | `extension.js` kennt keine Arbeitsbereiche — Vervollständigung arbeitet je Datei, projektweite Symbole fehlen | F | `editor/vscode/extension.js` | mittel | 4.1 löst es mit |
| 4.4 | Die Logik steht zweimal da, gehalten durch `check.js` und den Export-Test | Z | `entscheidungen.md:2114` | — | bewusst |

## 5. Blöcke und Welt

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 5.1 | **Controller-Multiblock.** Die Vorfrage ist die schwere: Wo liegt das Programm, wenn genau dieser Block abgebaut wird? Empfehlung (3): ein Erweiterungsblock ohne Zustand | E | `umsetzung.md:191` | groß | Entscheidung — danach nicht ohne Bruch änderbar |
| 5.2 | Ein eigener Speicherblock | F | `umsetzung.md:370` | mittel | — |
| 5.3 | Anzeigenwand: die Schrift wächst nicht mit der Wand | F | `umsetzung.md:156` | mittel | zeigt erst das Spielen |
| 5.4 | Die Zahlen an den Serverbauteilen sind gesetzt, nicht hergeleitet | E | `umsetzung.md:186` | klein | Spielprüfung |

## 6. Dokumentation

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 6.1 | Spielerdokumentation über GuideME | E | `entscheidungen.md:2344` | groß (Inhalt), klein (Anbindung) |
| 6.2 | **Das Projekt hat keine Lizenz** | F | — | klein |
| 6.3 | Ob die Hilfe im Spiel ins Buch wandert | E | `entscheidungen.md:2411` | klein |
| 6.4 | **Veraltet:** `WorkerRuntime`-Javadoc erklärt `maintain`, `when`, `strategy`, `overflow` für ungebaut — der Code darunter baut alle vier | F | `WorkerRuntime.java:37` | klein |
| 6.5 | **Veraltet:** `umsetzung.md` sagt, die VS-Code-README beschreibe eine Einzeldatei — sie beschreibt längst den Ordner | F | `umsetzung.md:518` | klein |
| 6.6 | **Veraltet:** „Offen, in dieser Reihenfolge zu klären" führt Punkte, die gebaut sind | F | `entscheidungen.md:116` | klein |
| 6.7 | `plan-globale-werte.md` hat keine Kästchen abgehakt | F | — | klein |
| 6.8 | `sprache.md` beschreibt durchgehend Ungebautes, ohne es zu kennzeichnen | F | `sprache.md` §6, §8, §12 | klein–mittel |
| 6.9 | **Veraltet:** Die Kabelbündel-Frage steht als offen da, ist zwei Abschnitte weiter entschieden | F | `entscheidungen.md:886` | klein |

## 7. Kompatibilität

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 7.1 | Mekanism-Chemikalien — die einzige harte Anbindung, die die Sprache verspricht und die Laufzeit verweigert | F | `WorldHost.java:461` | mittel–groß |
| 7.2 | GuideME als erste fremde Laufzeitabhängigkeit | F | `entscheidungen.md:2344` | klein |
| 7.3 | `device_done` je Mod (Weg 3) | Z | `umsetzung.md:177` | groß |
| 7.4 | Eigene Generatoren | Z | `strom.md:220` | — |

---

## Was am meisten bringt

**1. Globale Werte zu Ende bauen** (2.1). Der einzige größere Posten **ohne
offene Entscheidung** — der Plan liegt Schritt für Schritt vor.

**2. Die Gerätemitglieder ohne `busy`** (1.1). Ungewöhnlich langer Hebel: Sie
schalten die Punktvorschläge im Editor frei (heute stehen für jedes Gerät
dieselben vier Einträge), öffnen `crusher_1.energy` aus dem Strom-Entwurf und
sind Voraussetzung für die Listen aus §12. `busy` abtrennen — es ist der
einzige Teil, der eine Entscheidung braucht.

**3. Die Dokumentationsbefunde 6.4 bis 6.9.** Vier Stellen behaupten das
Gegenteil dessen, was der Code tut. Kostet fast nichts und sorgt dafür, dass
diese Liste beim nächsten Mal nicht neu erhoben werden muss.

**Vor allem anderen zu entscheiden:** `device_done` (2.6) und der
Controller-Anker (5.1). Beides ist **Entscheidung, nicht Arbeit** — und beides
ist nach dem Bau nicht mehr ohne Bruch zu ändern.
