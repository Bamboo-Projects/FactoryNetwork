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

> **Tag des 25.08.:** `device_output` gebaut (2.6), der Controller-Anker
> entschieden (5.1, gebaut ist nichts), Filter-Vorlagen gebaut — eine Auswahl
> mit Namen, die überall steht, wo eine Auswahl steht. Dabei drei Dinge
> gefunden, die niemand auf der Liste hatte: `except` wirkte nur im Worker
> und nicht in `move` und `count`; eine Auswahl, die nichts trifft, hätte
> nach der Reparatur beinahe alles bewegt; und `gerät.count(…)` las den
> Netzspeicher statt das Gerät. Alle drei behoben. Neu auf der Liste steht
> 3.11, überholt sind 2.7 und 2.6.

**Status:** **F** = fehlt schlicht · **E** = wartet auf eine Entscheidung ·
**Z** = bewusst zurückgestellt, kein Versäumnis

---

## 1. Sprache

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 1.1 | **Entschieden am 25.08.:** `output()` und `busy` werden gestrichen — das erste sagt dasselbe wie `move … from gerät`, das zweite wäre eine Vermutung mit dem Anschein einer Auskunft. `send()` an einer Gruppe wird gebaut und fällt mit 1.14 ab | F | `sprache.md:325` | klein (Streichung) | 1.14 für `send()` |
| 1.2 | ~~Listenoperationen~~ — **fertig**, alle fünf. `where` und `sort` werten je Eintrag aus, mit `it` als diesem Eintrag | | `sprache.md` §12 | | |
| 1.3 | **Entschieden:** Flüssigkeits-Tags heißen `fluidtag:`. Nicht `tag:` für beides — die Sorte muss aus dem Text ablesbar bleiben, sonst kann `WorkerKind` den Ausführungspfad nicht wählen | F | `FluidSelection`, `Lexer.SELECTOR_KINDS` | mittel | — |
| 1.4 | **Entschieden:** Anbindung als Kompatibilitätsmodul, wie GuideME — `compileOnly` plus `runtimeOnly`, Code unter `compat/mekanism`. Ohne Mekanism läuft die Mod wie heute, `chemical:` meldet die fehlende Mod statt zu werfen | F | `WorldHost.java:461` | mittel–groß | — |
| 1.5 | `import`/Module — reserviert, tut nichts | Z | `Parser.java:86` | — | bewusst, bis ein Projekt den Namensraum sprengt |
| 1.6 | Request/Response als eigene Form | Z | `sprache.md:992` | mittel | mit `emit`/`on`/`await` nachbaubar |
| 1.7 | **Rechte im Mehrspielerbetrieb.** Im Code gibt es *keinerlei* Berechtigungsprüfung — nur Dateisperren | F | `sprache.md:999` | mittel | — |
| 1.8 | Die Typen `Set<T>`, `Job`, `Chemical` fehlen im Wertemodell | F | `sprache.md:308`, `Value.java:13` | mittel | teils 1.2, teils 1.4 |
| 1.9 | Echter Typprüfer über Ausdrücke — **zurückgestellt.** Literal gegen Literal bleibt; alles andere fällt zur Laufzeit auf, mit Meldungen, die wissen, was erwartet war | Z | `globale-werte.md:195` | groß | eigenes Vorhaben über die ganze Sprache |
| 1.10 | **Entschieden: `const` kommt** — gegen meine Empfehlung. Ein Festwert sagt seine Absicht hin und kostet zur Laufzeit nichts | F | `globale-werte.md:198` | klein | — |
| 1.11 | **Entschieden: kommen** — gegen meine Empfehlung. Die Speicherfrage kommt damit als Baufrage wieder: wie eine Liste im Anfangswert steht, wie sie neben der Welt liegt, was beim Programmwechsel geschieht | F | `globale-werte.md:201` | mittel | 1.16 |
| 1.12 | Einstellbare Grenzen für Nutzercode. Das Schrittbudget (500) und die Schrankplätze gibt es; „jeweils einstellbar" nicht | F | `entscheidungen.md:118` | groß | 4.2 — es gibt keine Konfiguration |
| 1.13 | **Entschieden: annehmen.** `item:mekanism:steel_ingot` meldet jetzt die richtige Form, statt die Zeile zerfallen zu lassen. Sie *anzunehmen* wäre eine Änderung der Spezifikation: Lexer, Parser, EBNF, `sprache.md`, die VS-Code-Grammatik und der Guide. Dafür spricht, dass jeder Spieler IDs aus JEI kopiert | F | `Parser.parseSelector` | mittel | — |
| 1.14 | **Entschieden: Gruppen werden ein Wert.** `pumps.stop()` und `crushers.members()` stehen an mehreren Stellen in `sprache.md` und `konzept.md`; die Laufzeit kennt eine Gruppe nur als Verteilziel eines Workers. Entweder Gruppen bekommen Werte-Charakter mit `members()`, oder die Spezifikation streicht die Form | F | `sprache.md:39`, `:90`, `:836` | mittel | — |
| 1.16 | **Entschieden: ein Eintrag bekommt `it.item` und `it.amount`.** `sum()` zählt dann Mengen zusammen, `where` und `sort` werden benutzbar, und 6.10 löst sich mit. Bisher: `storage.items()` liefert je Posten eine Auswahl, und `Value.Selection` kennt kein einziges Member — damit ist `where` nicht benutzbar (es gibt kein Beispiel, das läuft), `sum` wirft, und `sort` sortiert nach lauter Nullen. Der Editor bietet alle fünf trotzdem an | F | `Interpreter.java:777`, `Signatures.LIST_MEMBERS` | mittel | — |
| 1.18 | **„Alles von A nach B" lässt sich in einem Ablauf nicht schreiben.** `move` verlangt eine Auswahl (`amount = [INT] selection`), und es gibt keine Schreibweise für „was auch immer darin liegt". Ein Worker ohne `filter` kann es; eine Funktion nicht. Aufgefallen beim Streichen von `output()` | F | `grammatik.md:197`, `WorldHost.move` | klein | Entscheidung über die Schreibweise |
| 1.17 | **Fächer ansprechen** — entschieden am 25.08.: Die Seite bleibt die Vorgabe, `from ofen slot 2` und `from ofen slots 0..3` greifen ausdrücklich auf das ungeteilte Inventar (`getCapability(…, null)`). Offen sind die Baufragen: Schreibweise für Bereiche, Fachangabe an `insert()`, was `items()` an einem Fach liefert. **Dazu gehört, die Fächer sichtbar zu machen** — eine Nummer schreibt niemand, ohne zu wissen, was darin liegt; der Ort dafür ist das Geräteprofil (3.7) | F | `ConnectorBlockEntity`, `Parser`, `DeviceProfile` | mittel | — |
| 1.15 | **Entschieden: `move` gibt die bewegte Menge zurück.** `insert()` tut es längst, und es ist dieselbe Operation. Bisher: `sprache.md:444` zeigt `let bewegt = move …`; `move` ist eine Anweisung, kein Ausdruck, und beide Ausführungspfade verwerfen die Zahl. Der Guide sagt es inzwischen ehrlich | F | `sprache.md:444`, `Interpreter.java:269` | klein–mittel | — |

## 2. Laufzeit

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 2.1 | ~~Globale Werte~~ — **fertig**, alle sieben Aufgaben | | `plan-globale-werte.md` | | |
| 2.2 | **Alle vier Entscheidungen sind gefallen** (25.08.): Knappheit nach `priority`, `power` als Schlüsselwort, Abgabe nur bei `RUNNING` — und **keine Kabelgrenze**, die Anmeldung entfällt damit. Zu bauen sind die Abgabe und die Energiezellen | F | `strom.md`, `entscheidungen.md` „Entscheidungsrunde" | groß | — |
| 2.3 | ~~Abgabe bei `OFF`/`BOOTING`~~ — **beantwortet.** Es fließt nichts ab, solange das Netz nicht läuft; der Fall kann nicht eintreten | | `strom.md` §3 | | |
| 2.4 | ~~Einheit der Abgaberate~~ — **entschieden:** `rate 40 per 1t`, keine neue Form. Ein eigenes Wort `tick` zöge sofort `per second` nach sich | | `strom.md` §9 | | |
| 2.5 | Strom als Wert in der Sprache (`crusher_1.energy`) | F | `strom.md:228` | klein | 1.1 |
| 2.6 | ~~Das Fertig-Signal~~ — **fertig.** `device_output` meldet, wenn in einem Gerät von einer Art mehr liegt als beim letzten Blick; was das Netz selbst einlegt, zieht die Grundlinie nach und zählt nie mit. Nicht `device_done`: gemessen wird „dazugekommen", nicht „fertig" | | `DeviceAmounts`, `NotifyingHandlers` | | |
| 2.11 | ~~`log()` sieht niemand~~ — **fertig.** Vier Stufen (`info`, `warn`, `error`, `debug`), Reiter „Log" mit Filter, Herkunft je Zeile, überlebt den Neustart. Die Hinweise der Laufzeit laufen mit hinein | | `LogTabView`, `LogEntry` | | |
| 2.7 | ~~`when`-Bedingungen~~ — **überholt.** Im laufenden Spiel wertet der echte Interpreter aus: Texte, globale Werte, Gerätezustände. Der alte Weg — Zahl gegen Zahl — greift nur ohne Host, also in Prüfungen ohne Welt. Eine kaputte Bedingung hält den Worker an | | `WorkerRuntime.conditionHolds` | | |
| 2.8 | **Entschieden: Namen in einem `move` werden geprüft**, als Warnung wie beim Worker. Örtliche Namen — Variablen, Parameter, globale Werte, Vorlagen — müssen ausgespart bleiben, sonst warnt der Prüfer vor richtigen Programmen | F | `geraeteerkennung.md:316` | mittel | — |
| 2.9 | Erkennung von Maschinen-Rezepten | F | `entscheidungen.md:131` | groß | — |
| 2.10 | Autocrafting — der letzte ausgegraute Reiter | F | `umsetzung.md:221` | groß | 2.9 |

## 3. Editor im Spiel

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 3.1 | ~~Annahme-Probe~~ — **fertig**, Kandidaten aus dem Entwurf | | `ItemCandidates.java` | | |
| 3.2 | **Entschieden: ja**, mit `fill(…, SIMULATE)` und den `fluid:`-Angaben aus dem Programm. Mitnehmen, wenn ohnehin an den Flüssigkeiten gearbeitet wird | F | `geraeteerkennung.md:336` | klein | — |
| 3.3 | ~~Flüssigkeitsstände im Tooltip~~ — **fertig** | | | | |
| 3.4 | ~~Bearbeitung anfragen~~ — **fertig**, F4 in beiden Fenstern | | `RequestEdit.java` | | |
| 3.5 | **Entschieden: Ordner kommen** — gegen meine Empfehlung. Dateiliste, Anlegen, Umbenennen und die Brücke zu VS Code ziehen mit | F | `umsetzung.md:505` | mittel | — |
| 3.6 | **Entschieden: prüfen** — gegen meine Empfehlung, und als Prüfauftrag, nicht als Zusage. Die Frage ist, ob künftige Fenster damit schneller gehen; die vorhandenen werden nicht neu gebaut | F | `umsetzung.md:512` | klein | — |
| 3.7 | **Entschieden: ja.** Was an einer Seite hängt — Inventar, Tank, Stromspeicher — steht neben Kanälen und Kabellast | F | `geraeteerkennung.md:340` | klein | — |
| 3.8 | ~~Ob der Netz-Reiter globale Werte ändern darf~~ — **entschieden: nur anzeigen.** Sonst wird der Zustand der Fabrik an zwei Stellen umgestellt, und niemand sieht ihr an, wer zuletzt geschaltet hat. Wer schalten will, baut einen Knopf | | `globale-werte.md:200` | | |
| 3.10 | **Der Editor bietet an, was nichts kann.** `crafting`, `world`, `network`, `workers` und `multiblocks` stehen an jeder Ziel- und Ausdrucksstelle zur Auswahl; ausgewertet wird allein `storage`. Wer `to crafting` schreibt, bekommt „Als Ziel taugt nur ein Name" — eine Meldung, die dem Vorschlag widerspricht, der sie ausgelöst hat. Dazu `power` an jeder Auswahlstelle, obwohl ein Strom-Worker sofort anhält | F | `Completions.java:60`, `:186`, `Parser.java:213` | klein | 2.2 und 1.1 |
| 3.11 | **Die Anzeige, worauf sich ein Muster auflöst, gibt es nicht.** `sprache.md` verspricht sie an zwei Stellen — bei `except` („der Editor zeigt zu jedem Muster an, was es gerade trifft") und bei `maintain`, wo ohne sie nicht abzusehen ist, was man zugesagt hat. Beide Stellen sind inzwischen gekennzeichnet | F | `sprache.md` §4, §11 | mittel | — |
| 3.9 | **Entschieden: zugelassen.** Ein Blick in eine BlockEntity je Tafel und Sekunde ist der bessere Tausch gegen ein `?`, das niemand erklären kann. Bisher: In einer Funktion geht es, auf einer Tafel steht `?`. Eine Anzeige rechnet seit dem 25.08., aber sie liest nur den Netzbestand und das Redstonesignal — ein Blick in eine Maschine ist ein Zugriff je Tafel und Sekunde | F | `DisplayValues.java` | klein | — |

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
| 6.2 | ~~Lizenzdatei~~ — **fertig.** MIT, Copyright 2026 DevPanda (Florian Richter). Der Entwurf ist aufgegangen und entfallen | | `LICENSE` | |
| 6.3 | ~~Ob die Hilfe im Spiel ins Buch wandert~~ — **entschieden: nein, nebeneinander.** Die Griffliste beantwortet „was steht hier", das Buch „wie funktioniert das" | | `entscheidungen.md` | |
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
| 7.1 | Mekanism-Chemikalien — **entschieden als Kompatibilitätsmodul** (siehe 1.4), nicht als Pflicht-Abhängigkeit | F | `compat/mekanism` (geplant) | mittel–groß |
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

**4. Was an einem Listeneintrag steht** (1.16). Es hängt mehr daran, als die
Zeile vermuten lässt: `where`, `sort` und `sum` über einen Bestand sind ohne
das nicht benutzbar, `list` auf einer Anzeige bleibt eine `row` (6.10), und
der Editor bietet alle fünf trotzdem an.

**Die zwei, die vor allem anderen zu entscheiden waren, sind entschieden:**
`device_output` (2.6) und der Controller-Anker (5.1), beide am 25.08. Damit
sind sie von der Entscheidungs- auf die Arbeitsliste gewandert; die
Begründungen stehen in `entscheidungen.md`. Was an ihnen jetzt noch offen
ist, sind Baufragen und keine Weichenstellungen mehr — beim Controller
allerdings eine große: Gebaut ist davon kein Block.


