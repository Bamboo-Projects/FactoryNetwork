# Offene Punkte — Bestandsaufnahme

Alles, was in `docs/` und im Code als unfertig steht, an einer Stelle.

Stand: 2026-08-25 (nach der zweiten Nacht, dem Protokoll und den beiden
Vorentscheidungen)

> **Nacht auf den 25.08., zweiter Durchgang:** 1.2 (`where` und `sort` stehen
> jetzt vollständig), dazu vier Dinge, die vorher niemand auf der Liste
> hatte, weil sie niemandem aufgefallen waren: ein Display, das jede Rechnung
> auf 0 fallen ließ, ein `on` mit vertipptem Ereignisnamen, das still nie
> lief, ein `await device_changed` ohne Block, das ewig wartete, und eine aus
> JEI kopierte ID, die sieben Fehlermeldungen erzeugte. Alle vier sind
> behoben, dazu ein fünftes, nach dem niemand gesucht hatte: **In einem
> Ablauf gab es die globalen Werte gar nicht.** `modus = "nacht"` warf
> „Unbekannter Name" auf jedem Weg, den ein Spieler wirklich nimmt — Knopf,
> Ereignis, `await` —, und der Hinweis riet zu einem `let`, das still ins
> Leere schreibt. Dazu `strategy priority`, das nie schreibbar war, und
> `strategy emptiest`, das es nie gab und trotzdem wirkte.
>
> Neu auf der Liste stehen 1.13 bis 1.16, 3.9, 3.10, 6.10 und 6.11 — 2.11 und
> 4.5 sind inzwischen gebaut.

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
| 1.2 | ~~Listenoperationen~~ — **fertig**, alle fünf. `where` und `sort` werten je Eintrag aus, mit `it` als diesem Eintrag | | `sprache.md` §12 | | |
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
| 1.13 | **Die JEI-Schreibweise annehmen?** `item:mekanism:steel_ingot` meldet jetzt die richtige Form, statt die Zeile zerfallen zu lassen. Sie *anzunehmen* wäre eine Änderung der Spezifikation: Lexer, Parser, EBNF, `sprache.md`, die VS-Code-Grammatik und der Guide. Dafür spricht, dass jeder Spieler IDs aus JEI kopiert | E | `Parser.parseSelector` | mittel | Entscheidung über die Spezifikation |
| 1.14 | **Gruppen sind kein Wert.** `pumps.stop()` und `crushers.members()` stehen an mehreren Stellen in `sprache.md` und `konzept.md`; die Laufzeit kennt eine Gruppe nur als Verteilziel eines Workers. Entweder Gruppen bekommen Werte-Charakter mit `members()`, oder die Spezifikation streicht die Form | E | `sprache.md:39`, `:90`, `:836` | mittel | Entscheidung |
| 1.16 | **Ein Eintrag einer Bestandsliste hat keine Angaben.** `storage.items()` liefert je Posten eine Auswahl, und `Value.Selection` kennt kein einziges Member — damit ist `where` nicht benutzbar (es gibt kein Beispiel, das läuft), `sum` wirft, und `sort` sortiert nach lauter Nullen. Der Editor bietet alle fünf trotzdem an | F | `Interpreter.java:777`, `Signatures.LIST_MEMBERS` | mittel | Entscheidung, was an einem Eintrag steht |
| 1.15 | **`move` gibt nichts zurück.** `sprache.md:444` zeigt `let bewegt = move …`; `move` ist eine Anweisung, kein Ausdruck, und beide Ausführungspfade verwerfen die Zahl. Der Guide sagt es inzwischen ehrlich | E | `sprache.md:444`, `Interpreter.java:269` | klein | Entscheidung |

## 2. Laufzeit

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 2.1 | ~~Globale Werte~~ — **fertig**, alle sieben Aufgaben | | `plan-globale-werte.md` | | |
| 2.2 | **Sprachseite gebaut**, Verteilung offen: `filter power` gibt es, ein Strom-Worker meldet sich als „wird noch nicht verteilt". Es fehlen die Abgabe, die Kabelgrenze und die Energiezellen | F | `strom.md` | groß | vier vorentschiedene Punkte warten auf Bestätigung |
| 2.3 | Abgabe bei `OFF`/`BOOTING`: „Netz füllt sich langsam, während Maschinen ziehen" nicht durchgerechnet | E | `strom.md:239` | klein | 2.2 |
| 2.4 | Einheit der Abgaberate: `per tick` gegen `per 5s` | E | `strom.md:249` | klein | 2.2 |
| 2.5 | Strom als Wert in der Sprache (`crusher_1.energy`) | F | `strom.md:228` | klein | 1.1 |
| 2.6 | **Entschieden am 25.08.:** Weg (1) kommt dazu — gemessen als Unterschied zum Stand beim Einlegen, damit ein vorher gefüllter Ausgang nichts auslöst, und benannt nach dem, was gemessen wird: **`device_output`**, nicht `device_done`. `device_changed` bleibt daneben stehen. Gebaut ist davon nichts | F | `entscheidungen.md` „Das Fertig-Signal heißt `device_output`" | mittel | — |
| 2.11 | ~~`log()` sieht niemand~~ — **fertig.** Vier Stufen (`info`, `warn`, `error`, `debug`), Reiter „Log" mit Filter, Herkunft je Zeile, überlebt den Neustart. Die Hinweise der Laufzeit laufen mit hinein | | `LogTabView`, `LogEntry` | | |
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
| 3.10 | **Der Editor bietet an, was nichts kann.** `crafting`, `world`, `network`, `workers` und `multiblocks` stehen an jeder Ziel- und Ausdrucksstelle zur Auswahl; ausgewertet wird allein `storage`. Wer `to crafting` schreibt, bekommt „Als Ziel taugt nur ein Name" — eine Meldung, die dem Vorschlag widerspricht, der sie ausgelöst hat. Dazu `power` an jeder Auswahlstelle, obwohl ein Strom-Worker sofort anhält | F | `Completions.java:60`, `:186`, `Parser.java:213` | klein | 2.2 und 1.1 |
| 3.9 | **`gerät.count(…)` auf einer Anzeige.** In einer Funktion geht es, auf einer Tafel steht `?`. Eine Anzeige rechnet seit dem 25.08., aber sie liest nur den Netzbestand und das Redstonesignal — ein Blick in eine Maschine ist ein Zugriff je Tafel und Sekunde | E | `DisplayValues.java` | klein | Entscheidung über die Kosten |

## 4. VS-Code-Erweiterung

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 4.1 | Sprachserver — Fehlerprüfung und Gerätenamen außerhalb des Spiels | E/F | `umsetzung.md:493` | groß | 4.2, dazu Umfang und Technik |
| 4.2 | **Die Mod hat keine Konfiguration.** Weder Server- noch Clientteil | F | `entscheidungen.md:2313` | mittel | Voraussetzung für 4.1 und 1.12 |
| 4.3 | ~~Projektweite Symbole~~ — **fertig**, in VS Code **und** im Spiel | | | | |
| 4.4 | Die Logik steht zweimal da, gehalten durch `check.js` und den Export-Test | Z | `entscheidungen.md:2114` | — | bewusst |
| 4.5 | **In der `on`-Kopfzeile werden die Ereignisse nicht vorgeschlagen.** Nach `on ` bieten beide Editoren die Deklarationswörter an. Die vier eingebauten stehen seit dem 25.08. in `BuiltinEvents` und werden bei `await` und `emit` vorgeschlagen — die Kopfzeile eines `on` erkennt bisher keiner von beiden | F | `Completions.java`, `extension.js` | klein | — |

## 5. Blöcke und Welt

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 5.1 | **Controller-Multiblock. Entschieden am 25.08.:** Weg (3) — der Controller bleibt genau ein Block und hält weiterhin alles; ein Anbaublock steuert nur Außenflächen für Kabel bei und hält nie etwas. Die Master-Rolle steht am Blocktyp fest, also kann sie nicht wandern. Gebaut ist davon nichts | F | `entscheidungen.md` „Der Controller bleibt ein Block" | groß | — |
| 5.2 | Ein eigener Speicherblock | F | `umsetzung.md:370` | mittel | — |
| 5.3 | Anzeigenwand: die Schrift wächst nicht mit der Wand | F | `umsetzung.md:156` | mittel | zeigt erst das Spielen |
| 5.4 | Die Zahlen an den Serverbauteilen sind gesetzt, nicht hergeleitet | E | `umsetzung.md:186` | klein | Spielprüfung |

## 6. Dokumentation

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 6.1 | **Angebunden**, Inhalt begonnen. Weitere Seiten fehlen: Sprache im Detail, Multiblocks, Abläufe | F | `assets/factorynetwork/guide/` | groß |
| 6.2 | **Lizenzdatei fehlt.** Der Text liegt fertig in `lizenz-entwurf.md` und braucht nur eine Unterschrift — Name und Jahr im Copyright trifft der Rechteinhaber selbst, nicht ich | E | `lizenz-entwurf.md` | klein |
| 6.3 | Ob die Hilfe im Spiel ins Buch wandert | E | `entscheidungen.md:2411` | klein |
| 6.4 | ~~WorkerRuntime-Javadoc~~ — **berichtigt** | | | |
| 6.5 | ~~README-Frage~~ — **entfernt**, sie war beantwortet | | | |
| 6.6 | ~~Prioritätenliste~~ — **berichtigt**, mit Vermerk | | | |
| 6.7 | ~~Plan-Kästchen~~ — **abgehakt** | | | |
| 6.10 | **`list` auf einer Anzeige ist eine `row`.** Die Spezifikation nennt es „Aufzählung, etwa Bestände oder Aufträge"; gezeichnet wird eine einzelne Zeile wie bei `row`. Solange ein Listeneintrag keine Angaben hat (1.16), lässt sich daran auch nichts ändern | F | `DisplayValues.java`, `sprache.md:814` | klein | 1.16 |
| 6.11 | **Tests, die nicht fehlschlagen können.** Fünf sind repariert. Offen bleiben zwei: `abrokenRackDropsItsProcessors` prüft nur, dass *irgendein* Gegenstand fällt — ein leeres Gehäuse genügt, und dahinter stünde der Verlust von sechsunddreißig Bauteilen; `theAnalyserMarksFullCables` legt die Last genau auf die Grenze und nimmt mit ODER beide Antworten an, während `isHealthy()` ein randvolles Kabel als „in Ordnung" meldet | F | `FactoryNetworkGameTests` | klein | — |
| 6.8 | **Teilweise.** §6 und §12 sind gekennzeichnet; §8 und der Rest noch nicht | F | `sprache.md` | klein |
| 6.9 | ~~Kabelbündel-Frage~~ — **nachgetragen** | | | |

## 7. Kompatibilität

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 7.1 | Mekanism-Chemikalien — die einzige harte Anbindung, die die Sprache verspricht und die Laufzeit verweigert | F | `WorldHost.java:461` | mittel–groß |
| 7.2 | ~~GuideME eingebunden~~ — **fertig**, `compat/guide` | | | |
| 7.3 | Das Fertig-Signal je Mod (Weg 3) | Z | `umsetzung.md:177` | groß |
| 7.4 | Eigene Generatoren | Z | `strom.md:220` | — |

---

## Was am meisten bringt

**1. Die vier Strom-Entscheidungen bestätigen oder kippen** (2.2). Sie sind
in `entscheidungen.md` festgehalten, aber vom Projektinhaber nie ausdrücklich
abgenickt: Knappheit strikt nach `priority`, `power` als Schlüsselwort,
Anmeldung beim Übernehmen, Abgabe ruht bei `OFF`. **Danach ist die
Verteilung reine Arbeit** — der Entwurf steht, die Sprachseite auch.

**2. Drei Stellen, an denen die Spezifikation mehr verspricht als der Code
kann** (1.13, 1.14, 1.15). Gruppen als Wert, `move` als Ausdruck, die
JEI-Schreibweise. Alle drei sind Entscheidungen und keine Arbeit — und
solange sie offen sind, steht in `sprache.md` Code, den niemand ausführen
kann. Ein Beispiel, das nicht läuft, kostet mehr Vertrauen als eine fehlende
Zeile.

**3. Weitere Handbuchseiten** (6.1). Die Anbindung steht, die ersten Seiten
auch. Der Rest ist Schreibarbeit ohne Risiko — und das, was die Mod für
jemanden von außen überhaupt zugänglich macht.

**Die zwei, die vor allem anderen zu entscheiden waren, sind entschieden:**
`device_output` (2.6) und der Controller-Anker (5.1), beide am 25.08. Damit
sind sie von der Entscheidungs- auf die Arbeitsliste gewandert; die
Begründungen stehen in `entscheidungen.md`. Was an ihnen jetzt noch offen
ist, sind Baufragen und keine Weichenstellungen mehr.


