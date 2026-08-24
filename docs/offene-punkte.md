# Offene Punkte — Bestandsaufnahme

Alles, was in `docs/` und im Code als unfertig steht, an einer Stelle.

Stand: 2026-08-25 (nach der Nacht)

> **Was in der Nacht auf den 25.08. erledigt wurde:** 2.1 (globale Werte),
> 3.1 (Annahme-Probe), 3.3 (Flüssigkeitsstände), 3.4 (Bearbeitung anfragen),
> 4.3 (Projektsymbole in VS Code — und im Spiel gleich mit), 6.4 bis 6.7 und
> 6.9 (die Doku-Widersprüche), dazu 7.2 (GuideME eingebunden). Teilweise:
> 1.1 (`insert` und `items` gebaut, `output`/`send`/`busy` offen), 1.2
> (`count`/`first`/`sum` gebaut, `where`/`sort` offen), 2.2 (Sprachseite des
> Stroms gebaut, Verteilung offen), 6.1 (Handbuch angebunden, Inhalt
> begonnen), 6.8 (§6 und §12 gekennzeichnet).
>
> Die Zeilen unten sind entsprechend angepasst.

**Status:** **F** = fehlt schlicht · **E** = wartet auf eine Entscheidung ·
**Z** = bewusst zurückgestellt, kein Versäumnis

---

## 1. Sprache

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 1.1 | **Teilweise gebaut.** `insert()` und `items()` stehen; offen sind `output()` und `send()` — beide unterspezifiziert (siehe `sprache.md` §6) — und `busy` | E | `sprache.md:325` | mittel | `output()`: was `move crusher.output() to x` als Quelle meint. `send()`: Verteilung im Aufruf. `busy`: keine Capability |
| 1.2 | **Teilweise gebaut.** `count`, `first`, `sum` und `storage.items()` stehen. `where` und `sort` fehlen: Sie werten je Element aus, und Argumente werden vorher ausgerechnet | F | `sprache.md` §12 | mittel | Eingriff in den Aufrufpfad |
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
| 2.1 | ~~Globale Werte~~ — **fertig**, alle sieben Aufgaben | | `plan-globale-werte.md` | | |
| 2.2 | **Sprachseite gebaut**, Verteilung offen: `filter power` gibt es, ein Strom-Worker meldet sich als „wird noch nicht verteilt". Es fehlen die Abgabe, die Kabelgrenze und die Energiezellen | F | `strom.md` | groß | vier vorentschiedene Punkte warten auf Bestätigung |
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
| 3.1 | ~~Annahme-Probe~~ — **fertig**, Kandidaten aus dem Entwurf | | `ItemCandidates.java` | | |
| 3.2 | Ob die Probe auch für Flüssigkeiten sinnvoll ist | E | `geraeteerkennung.md:336` | klein | 3.1 |
| 3.3 | ~~Flüssigkeitsstände im Tooltip~~ — **fertig** | | | | |
| 3.4 | ~~Bearbeitung anfragen~~ — **fertig**, F4 in beiden Fenstern | | `RequestEdit.java` | | |
| 3.5 | Ordner im Projekt | E | `umsetzung.md:505` | mittel | Entscheidung |
| 3.6 | LDLib2 als UI-Grundlage — nicht einmal geprüft | E | `umsetzung.md:512` | klein | Entscheidung |
| 3.7 | Ob das Geräteprofil dem Analysator etwas zu geben hat | E | `geraeteerkennung.md:340` | klein | — |
| 3.8 | Ob der Netz-Reiter globale Werte ändern darf | E | `globale-werte.md:200` | klein | 2.1 |

## 4. VS-Code-Erweiterung

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 4.1 | Sprachserver — Fehlerprüfung und Gerätenamen außerhalb des Spiels | E/F | `umsetzung.md:493` | groß | 4.2, dazu Umfang und Technik |
| 4.2 | **Die Mod hat keine Konfiguration.** Weder Server- noch Clientteil | F | `entscheidungen.md:2313` | mittel | Voraussetzung für 4.1 und 1.12 |
| 4.3 | ~~Projektweite Symbole~~ — **fertig**, in VS Code **und** im Spiel | | | | |
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
| 6.1 | **Angebunden**, Inhalt begonnen. Weitere Seiten fehlen: Sprache im Detail, Multiblocks, Abläufe | F | `assets/factorynetwork/guide/` | groß |
| 6.2 | **Keine Lizenzdatei.** `gradle.properties` sagt bereits `mod_license=MIT` — es fehlt nur die Datei, und MIT verträgt sich mit GuideMEs LGPL | F | `gradle.properties` | klein |
| 6.3 | Ob die Hilfe im Spiel ins Buch wandert | E | `entscheidungen.md:2411` | klein |
| 6.4 | ~~WorkerRuntime-Javadoc~~ — **berichtigt** | | | |
| 6.5 | ~~README-Frage~~ — **entfernt**, sie war beantwortet | | | |
| 6.6 | ~~Prioritätenliste~~ — **berichtigt**, mit Vermerk | | | |
| 6.7 | ~~Plan-Kästchen~~ — **abgehakt** | | | |
| 6.8 | **Teilweise.** §6 und §12 sind gekennzeichnet; §8 und der Rest noch nicht | F | `sprache.md` | klein |
| 6.9 | ~~Kabelbündel-Frage~~ — **nachgetragen** | | | |

## 7. Kompatibilität

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 7.1 | Mekanism-Chemikalien — die einzige harte Anbindung, die die Sprache verspricht und die Laufzeit verweigert | F | `WorldHost.java:461` | mittel–groß |
| 7.2 | ~~GuideME eingebunden~~ — **fertig**, `compat/guide` | | | |
| 7.3 | `device_done` je Mod (Weg 3) | Z | `umsetzung.md:177` | groß |
| 7.4 | Eigene Generatoren | Z | `strom.md:220` | — |

---

## Was am meisten bringt

**1. Die vier Strom-Entscheidungen bestätigen oder kippen** (2.2). Sie sind
in `entscheidungen.md` festgehalten, aber vom Projektinhaber nie ausdrücklich
abgenickt: Knappheit strikt nach `priority`, `power` als Schlüsselwort,
Anmeldung beim Übernehmen, Abgabe ruht bei `OFF`. **Danach ist die
Verteilung reine Arbeit** — der Entwurf steht, die Sprachseite auch.

**2. `where` und `sort`** (1.2). Sie machen `items()` erst nützlich, und sie
sind der einzige Grund, warum §12 halb dasteht. Kein Entscheidungsbedarf, nur
ein Eingriff in den Aufrufpfad: Das Argument darf nicht vorher ausgewertet
werden.

**3. Weitere Handbuchseiten** (6.1). Die Anbindung steht, die ersten Seiten
auch. Der Rest ist Schreibarbeit ohne Risiko — und das, was die Mod für
jemanden von außen überhaupt zugänglich macht.

**Vor allem anderen zu entscheiden, unverändert:** `device_done` (2.6) und der
Controller-Anker (5.1). Beides ist **Entscheidung, nicht Arbeit** — und beides
ist nach dem Bau nicht mehr ohne Bruch zu ändern.


