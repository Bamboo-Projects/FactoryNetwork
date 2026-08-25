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
| 1.1 | ~~`output()`, `send()`, `busy`~~ — **erledigt.** `output()` und `busy` sind gestrichen (sie sagten dasselbe wie `move` beziehungsweise nichts Nachprüfbares), `send()` an einer Gruppe ist mit 1.14 gebaut | | `sprache.md` §6, §8 | | |
| 1.2 | ~~Listenoperationen~~ — **fertig**, alle fünf. `where` und `sort` werten je Eintrag aus, mit `it` als diesem Eintrag | | `sprache.md` §12 | | |
| 1.3 | ~~Flüssigkeits-Tags~~ — **fertig.** `fluidtag:c/molten` löst gegen die Fluid-Registry auf, `tag:` bleibt bei den Gegenständen. Ein Worker mit `fluidtag:` gilt als Flüssigkeits-Worker: `WorkerKind` nennt die Ressource, nicht die Schreibweise | | `FluidSelection`, `WorkerKind.resource` | | |
| 1.4 | **Entschieden:** Anbindung als Kompatibilitätsmodul, wie GuideME — `compileOnly` plus `runtimeOnly`, Code unter `compat/mekanism`. Ohne Mekanism läuft die Mod wie heute, `chemical:` meldet die fehlende Mod statt zu werfen | F | `WorldHost.java:461` | mittel–groß | — |
| 1.5 | `import`/Module — reserviert, tut nichts | Z | `Parser.java:86` | — | bewusst, bis ein Projekt den Namensraum sprengt |
| 1.6 | Request/Response als eigene Form | Z | `sprache.md:992` | mittel | mit `emit`/`on`/`await` nachbaubar |
| 1.7 | **Rechte im Mehrspielerbetrieb.** Im Code gibt es *keinerlei* Berechtigungsprüfung — nur Dateisperren | F | `sprache.md:999` | mittel | — |
| 1.8 | Die Typen `Set<T>`, `Job`, `Chemical` fehlen im Wertemodell | F | `sprache.md:308`, `Value.java:13` | mittel | teils 1.2, teils 1.4 |
| 1.9 | Echter Typprüfer über Ausdrücke — **zurückgestellt.** Literal gegen Literal bleibt; alles andere fällt zur Laufzeit auf, mit Meldungen, die wissen, was erwartet war | Z | `globale-werte.md:195` | groß | eigenes Vorhaben über die ganze Sprache |
| 1.10 | ~~Konstanten~~ — **fertig.** `const stapel = 64` wird gelesen wie ein globaler Wert und nie geschrieben; der Versuch ist ein Fehler beim Übernehmen. Nicht gespeichert, weil ein Wert aus dem Programm aus dem Programm wiederkommt | | `Decl.Const`, `GlobalCheck` | | |
| 1.11 | **Entschieden: kommen** — beim Bauen aufgehalten. Die Speicherung ist das kleinere Problem (`ValueCodec` kann Listen schon); es fehlt die Sprache drumherum: Es gibt **keine Schreibweise für eine Liste** (`[a, b]` kennt der Lexer nicht) und **keinen Weg, einer Liste etwas hinzuzufügen** — `count`, `first`, `sum`, `where` und `sort` liefern alle nur Neues, ändern nie. Ohne beides wäre ein globaler Listenwert einer, den man nur ganz ersetzen kann. Das ist eine Entscheidung über die Sprache und keine Baufrage | E | `Lexer`, `Interpreter.listMember` | mittel | Entscheidung: Listenliteral, und Änderung oder Ersetzung |
| 1.12 | Einstellbare Grenzen für Nutzercode. Das Schrittbudget (500) und die Schrankplätze gibt es; „jeweils einstellbar" nicht | F | `entscheidungen.md:118` | groß | 4.2 — es gibt keine Konfiguration |
| 1.13 | ~~Die JEI-Schreibweise~~ — **fertig.** `item:mekanism:steel_ingot` meint dasselbe wie `item:mekanism/steel_ingot`. Der Parser hatte die Zerlegung schon; er hat sie nur mit einer Meldung begleitet, die bei jeder kopierten ID wiederkam | | `Parser.parseSelector` | | |
| 1.14 | ~~Gruppen sind kein Wert~~ — **fertig.** `crushers.members()` liefert die Geräte, `crushers.send(…)` schickt aus dem Speicher an die Gruppe, und als Ziel steht sie überall, wo ein Gerät steht. Der Wert trägt nur den Namen — wer heute dazugehört, entscheidet das Netz | | `Value.Group`, `WorldHost.memberFor` | | |
| 1.16 | ~~Ein Eintrag einer Bestandsliste hat keine Angaben~~ — **fertig.** `it.amount` ist die Menge, `it.item` die Art (nur bei genau einer), `it.fluid` dasselbe für Flüssigkeiten. `sum()` zählt jetzt Mengen zusammen, statt zu werfen, und der Editor bietet nach `it.` nicht mehr die Gerätemitglieder an | | `Interpreter.entryMember`, `Signatures.ENTRY_MEMBERS` | | |
| 1.18 | **„Alles von A nach B" lässt sich in einem Ablauf nicht schreiben.** `move` verlangt eine Auswahl (`amount = [INT] selection`), und es gibt keine Schreibweise für „was auch immer darin liegt". Ein Worker ohne `filter` kann es; eine Funktion nicht. Aufgefallen beim Streichen von `output()` | F | `grammatik.md:197`, `WorldHost.move` | klein | Entscheidung über die Schreibweise |
| 1.17 | ~~Fächer ansprechen~~ — **fertig.** `brecher_1.slots(3)` und `slots(1..5)` lesen wie eine Liste und stehen zugleich als Quelle oder Ziel eines `move`. Über das ganze Inventar, gezählt ab null; Nummern, die es nicht gibt, fallen weg. Dazu kam die Bereichsform `1..5` in die Sprache | | `Value.DeviceSlots`, `SlotView` | | |
| 1.19 | **Fachnummern sieht man nicht.** `slots(3)` schreibt, wer weiß, was in Fach 3 liegt — im Spiel steht das nirgends. Der Ort dafür ist das Geräteprofil (3.7), das ohnehin sichtbar wird | F | `DeviceProfile`, `DeviceSnapshotPacket` | mittel | 3.7 |
| 1.15 | ~~`move` gibt nichts zurück~~ — **fertig.** `let bewegt = move …` läuft, und `if move … > 0` auch. Die Anweisungsform bleibt daneben bestehen: In einem Ablauf ist ein `move` ein Schritt, und das braucht die Fortsetzung nach einem Neustart | | `Expr.Move`, `Interpreter.doMove` | | |

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
| 2.8 | ~~`NetworkCheck` besucht keine Anweisungen~~ — **fertig.** Ein `move` mit unbekanntem Gerätenamen wird gewarnt, in der Anweisung wie im Ausdruck. Ausgespart bleiben örtliche Namen: Parameter, `let`, Schleifenvariablen, globale Werte, Festwerte, Vorlagen, Gruppen und die Rollen eines Multiblocks | | `NetworkCheck.checkMoves` | | |
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
| 3.10 | ~~Der Editor bietet an, was nichts kann~~ — **erledigt.** Beide Editoren schlagen nur noch `storage` vor; `crafting`, `world`, `network`, `workers` und `multiblocks` sind draußen, bis sie etwas tun. `power` bleibt: Die Schreibweise ist entschieden, und ein Strom-Worker hält mit einer Meldung an, die auf `strom.md` zeigt | | `Completions.BUILTINS`, `extension.js` | | |
| 3.11 | **Die Anzeige, worauf sich ein Muster auflöst, gibt es nicht.** `sprache.md` verspricht sie an zwei Stellen — bei `except` („der Editor zeigt zu jedem Muster an, was es gerade trifft") und bei `maintain`, wo ohne sie nicht abzusehen ist, was man zugesagt hat. Beide Stellen sind inzwischen gekennzeichnet | F | `sprache.md` §4, §11 | mittel | — |
| 3.9 | ~~`gerät.count(…)` auf einer Anzeige~~ — **fertig.** Eine Tafel liest jetzt auch aus einer Maschine, mit Auswahl oder ohne. Ohne Welt bleibt es beim `?`: Eine erfundene Null schickte den Spieler zur falschen Maschine | | `DisplayValues.deviceCount` | | |

## 4. VS-Code-Erweiterung

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 4.1 | Sprachserver — Fehlerprüfung und Gerätenamen außerhalb des Spiels | E/F | `umsetzung.md:493` | groß | 4.2, dazu Umfang und Technik |
| 4.2 | **Die Mod hat keine Konfiguration.** Weder Server- noch Clientteil | F | `entscheidungen.md:2313` | mittel | Voraussetzung für 4.1 und 1.12 |
| 4.3 | ~~Projektweite Symbole~~ — **fertig**, in VS Code **und** im Spiel | | | | |
| 4.4 | Die Logik steht zweimal da, gehalten durch `check.js` und den Export-Test | Z | `entscheidungen.md:2114` | — | bewusst |
| 4.5 | ~~In der `on`-Kopfzeile werden die Ereignisse nicht vorgeschlagen~~ — **überholt.** Beide Editoren tun es längst: `check.js` prüft es für VS Code, und für den Editor im Spiel steht der Fall jetzt als Test in `CompletionsTest`. Die Zeile war stehengeblieben | | `CompletionsTest`, `check.js` | | |

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
| 6.10 | **`list` auf einer Anzeige ist eine `row`.** Die Spezifikation nennt es „Aufzählung, etwa Bestände oder Aufträge"; gezeichnet wird eine einzelne Zeile wie bei `row`. **Seit 1.16 nicht mehr blockiert** — ein Posten kennt jetzt Art und Menge, eine Aufzählung lässt sich also zeichnen | F | `DisplayValues.java`, `sprache.md:814` | klein | — |
| 6.11 | ~~Tests, die nicht fehlschlagen können~~ — **fertig**, alle sieben. `abrokenRackDropsItsProcessors` prüft jetzt, dass das herausfallende Gehäuse seine Bauteile trägt (gegengeprobt: ohne `packAll` fällt er); `theAnalyserMarksFullCables` prüft die eine richtige Antwort statt zweier mit ODER, dazu den Gegenfall mit halber Last | | `FactoryNetworkGameTests` | | |
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


