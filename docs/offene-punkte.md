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

> **Später am 25.08.:** Auch der Controller-Anbau (5.1) steht. Er berührt
> den Controller, bringt sechs Seiten mit, kostet keinen Kanal und 1 FE/t;
> über ein Kabel angeschlossen tut er nichts.

> **Später am 25.08.:** Die Stromverteilung (2.2) ist gebaut — beide
> Richtungen, die Reihenfolge nach `priority`, die Energiezellen und die
> Abgabe im Netz-Reiter. Dabei fiel auf, dass `priority` bis dahin überhaupt
> nichts tat und dass die Kreativquelle nichts hergab; beides ist mit
> behoben. Siehe `strom.md` §10.

> **Abend des 25.08.:** Die ganze Entscheidungsliste durchgesprochen und
> danach abgearbeitet. Gebaut: Filter-Vorlagen, `it.item`/`it.amount` am
> Posten, `move` mit Rückgabe, die JEI-Schreibweise, `fluidtag:`, `const`,
> Gruppen als Wert samt `send()`, `slots(1..5)` mitsamt Bereichsform, die
> Namensprüfung im `move`, `count` auf der Anzeigetafel, das Geräteprofil am
> Analysator. Gestrichen: `output()`, `busy`, `pumps.stop()`. Neu gefunden und
> behoben: die `except`-Lücke, eine Auswahl, die alles bewegte, `count` am
> Gerät, das den Speicher las.
>
> **Tag des 25.08.:** `device_output` gebaut (2.6), der Controller-Anker
> entschieden (5.1, gebaut ist nichts), Filter-Vorlagen gebaut — eine Auswahl
> mit Namen, die überall steht, wo eine Auswahl steht. Dabei drei Dinge
> gefunden, die niemand auf der Liste hatte: `except` wirkte nur im Worker
> und nicht in `move` und `count`; eine Auswahl, die nichts trifft, hätte
> nach der Reparatur beinahe alles bewegt; und `gerät.count(…)` las den
> Netzspeicher statt das Gerät. Alle drei behoben. Neu auf der Liste steht
> 3.11, überholt sind 2.7 und 2.6.

> **Nacht auf den 26.08.:** Die Fertigung ist **mehrstufig** (2.10, erster
> Teil). Der Planner zerlegt eine Bestellung, bis er bei etwas ankommt, das
> dasteht — eine Truhe aus zwei Stämmen läuft in zwei Schritten. Dabei fiel
> ein Fehler auf, den erst die Rekursion sichtbar machte: **Eine Zutat ist
> eine Auswahl, und die Zutatenliste legte sich beim Planen auf eine Sorte
> fest.** Für einen einzigen Schritt fiel das nie auf, für eine Kette schon —
> wer nur Fichtenstämme hatte, bekam „es fehlen 8 Eichenbretter". Neu in der
> Serverkonfiguration: `craftingDepth` und `craftingBudget`.
>
> Danach der Rest von 2.10: **`from crafting`** hält einen Vorrat und rechnet
> dabei gegen Bestand *und* offene Aufträge — ohne das bestellte ein solcher
> Worker jede Runde dieselbe Lücke neu. Drei Formentscheidungen selbst
> getroffen (Ziel nur `storage`, `maintain` Pflicht, `rate` begrenzt nur, wenn
> es dasteht) und in `entscheidungen.md` begründet. Damit ist **2.10 fertig**
> und `crafting` steht wieder in beiden Editoren — hinter `from`, nicht
> hinter `to`.
>
> Dann 3.5: **Ordner im Projekt.** Kleiner als die offene Frage aussah — im
> Kern ein Namensmuster mit Abschnitten —, aber mit zwei Stellen, an denen es
> still schiefgeht: Auf Windows liefert `relativize` den Rückstrich, und die
> Erweiterung las den Ordner der offenen Datei statt der Wurzel des Projekts.
> `ProgramFolder` hat jetzt eine eigene Prüfung; vorher hatte es keine, weil
> die Klasse einen Server verlangte.
>
> **5.2 hat sich aufgelöst statt erfüllt.** Die Zeile verlangte einen eigenen
> Speicherblock, weil der Speicher im Controller sitze — er sitzt seit den
> Zellen in Laufwerken. Was fehlte, war der Nachweis: Alle Speichertests
> hatten genau ein Laufwerk, und damit war die Zusage „wer mehr Platz will,
> stellt eines dazu" nirgends geprüft. Der Test lief auf Anhieb durch.
>
> **5.3 ist beantwortet, aber anders als gefragt.** Die Schrift wächst
> weiterhin nicht von selbst mit der Wand — das wäre der ganze Vorteil einer
> Wand. Es gibt jetzt `scale 4` im Display-Block, eine feste Zahl für die
> ganze Tafel, und die Sichtweite wächst mit.
>
> **3.6 ist geprüft und abgelehnt.** LDLib2 ist gut — 2.2.x für 1.21.1, reines
> Java, LGPL, sogar ein CodeEditor-Widget. Zwei Gründe dagegen: Eine
> Oberfläche lässt sich nicht weich einbinden und wäre die erste
> Pflicht-Abhängigkeit der Mod, und die Fenster sind gebaut. Der Stand steht
> in `entscheidungen.md`, damit ein zweiter Blick billig bleibt.
>
> **1.11: Listen kommen, und sie werden ersetzt statt geändert.** `[a, b]`,
> `[]`, mehrzeilig; dazu `plus`, `without`, `rest` und kein `add`. Der Grund
> stand schon im Code: `const` und der Mehrspielerschutz bewachen Zuweisungen,
> und der `ValueCodec` trennt beim Neustart zwei Namen für dieselbe Liste.
> Neu in der Serverkonfiguration: `globalListSize`. Dabei fiel auf, dass der
> Netzprüfer nicht in Listenliterale hineinsah und dass `describe()` einer
> Liste nur ihre Länge nannte.
>
> **Damit ist die Reihe der kleineren Punkte leer.** Offen bleiben 2.9
> (Processing-Rezepte, groß und ohne Vorentscheidung), 6.1 (weitere
> Handbuchseiten), 1.4/7.1 (Mekanism), 4.1 (Sprachserver) und 5.4 (die einzige
> offene Entscheidung, und die beantwortet eine Runde Spielen).
>
> **1.4 hat ein Teilstück bekommen.** `chemical:` meldet die fehlende Mod
> statt einer Baustelle — die eine Zusage aus dem Eintrag, die ohne jede
> Abhängigkeit einzulösen war. Die Abhängigkeit selbst ist nachgesehen und
> nicht eingebaut: `runtimeOnly` zöge Mekanism in jeden Prüflauf, und das ist
> eine Entscheidung über die Zeit anderer Leute.
>
> **2.9 ist erkundet, nicht gebaut.** Automatische Erkennung fremder
> Maschinenrezepte ist in 1.21.1 nachweislich nicht möglich — die
> Schnittstelle gibt es nicht her, und AE2 wie Refined Storage lassen deshalb
> beide hinterlegen. Damit war aus einer Bauaufgabe eine Produktfrage
> geworden.
>
> **Die drei offenen Fragen sind am 26.08. beantwortet:** 2.9 wird **Weg B**,
> Mekanism darf in den Prüflauf, und Chemikalien werden **auch gelagert**.
> Damit steht auf der Liste nichts Offenes mehr außer 5.4 — nur noch Arbeit.
>
> **2.9 ist danach gebaut**, in zwei Schnitten: erst die Maschinen mit fester
> Form (Ofenfamilie, Presse) samt asynchronem Ausführer, dann `recipe … at …`
> für alles andere. Der laufende Schritt wird gespeichert, der Plan nicht —
> ein Plan ist eine Absicht, ein Erz im Ofen eine Tatsache über die Welt.

**Status:** **F** = fehlt schlicht · **E** = wartet auf eine Entscheidung ·
**Z** = bewusst zurückgestellt, kein Versäumnis

---

## 1. Sprache

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 1.1 | ~~`output()`, `send()`, `busy`~~ — **erledigt.** `output()` und `busy` sind gestrichen (sie sagten dasselbe wie `move` beziehungsweise nichts Nachprüfbares), `send()` an einer Gruppe ist mit 1.14 gebaut | | `sprache.md` §6, §8 | | |
| 1.2 | ~~Listenoperationen~~ — **fertig**, alle fünf. `where` und `sort` werten je Eintrag aus, mit `it` als diesem Eintrag | | `sprache.md` §12 | | |
| 1.3 | ~~Flüssigkeits-Tags~~ — **fertig.** `fluidtag:c/molten` löst gegen die Fluid-Registry auf, `tag:` bleibt bei den Gegenständen. Ein Worker mit `fluidtag:` gilt als Flüssigkeits-Worker: `WorkerKind` nennt die Ressource, nicht die Schreibweise | | `FluidSelection`, `WorkerKind.resource` | | |
| 1.4 | **Teilstück gebaut** (26.08.): `chemical:` meldet die fehlende Mod statt einer Baustelle, aus einer Quelle (`FnMekanism`). **Beide Fragen entschieden** (26.08.): Mekanism kommt als `compileOnly` plus `runtimeOnly` in den Prüflauf, und Chemikalien werden **auch gelagert** — eine dritte Zellenart. Die Zelle wird immer registriert und funktioniert nur mit Mekanism; ohne bedingte Registrierung, sonst verschwinden Zellen samt Inhalt aus der Welt | F | `compat/mekanism` | groß | — |
| 1.5 | `import`/Module — reserviert, tut nichts | Z | `Parser.java:86` | — | bewusst, bis ein Projekt den Namensraum sprengt |
| 1.6 | Request/Response als eigene Form | Z | `sprache.md:992` | mittel | mit `emit`/`on`/`await` nachbaubar |
| 1.7 | ~~Rechte im Mehrspielerbetrieb~~ — **gebaut** (25.08.). `protection.programs` in der Serverkonfiguration: `OFF` (Vorgabe, wie bisher), `OWNER`, `OPS`. Geschützt sind Übernehmen und Entwurf speichern; die Beschriftungspistole nicht — sie ändert die Welt, und dafür gibt es Schutzmods | | `FnProtection` | | |
| 1.8 | Die Typen `Set<T>`, `Job`, `Chemical` fehlen im Wertemodell | F | `sprache.md:308`, `Value.java:13` | mittel | teils 1.2, teils 1.4 |
| 1.9 | Echter Typprüfer über Ausdrücke — **zurückgestellt.** Literal gegen Literal bleibt; alles andere fällt zur Laufzeit auf, mit Meldungen, die wissen, was erwartet war | Z | `globale-werte.md:195` | groß | eigenes Vorhaben über die ganze Sprache |
| 1.10 | ~~Konstanten~~ — **fertig.** `const stapel = 64` wird gelesen wie ein globaler Wert und nie geschrieben; der Versuch ist ein Fehler beim Übernehmen. Nicht gespeichert, weil ein Wert aus dem Programm aus dem Programm wiederkommt | | `Decl.Const`, `GlobalCheck` | | |
| 1.11 | ~~Globale Listen~~ — **fertig** (26.08.). `[a, b]` und `[]`, mehrzeilig; dazu `plus`, `without`, `rest`. Die Entscheidung lautet **ersetzen, nicht ändern**: Kein `add`, angehängt wird über eine Zuweisung — damit greifen `const` und der Mehrspielerschutz ohne Zusatzarbeit, und der Neustart trennt keine Verweise. Kein Zugriff über eine Nummer. Obergrenze in der Serverkonfiguration (`globalListSize`) | | `Expr.ListLit`, `Interpreter.writeGlobal` | | |
| 1.12 | **Halb erledigt** (25.08.): Schrittbudget und Suchtiefe des Netzgraphen stehen in der Serverkonfiguration. Die Schrankplätze bleiben bewusst draußen — sie sind Spielinhalt und gehören zum Ausgleich der Mod, nicht zur Serverlast | Z | `FnConfig` | | |
| 1.13 | ~~Die JEI-Schreibweise~~ — **fertig.** `item:mekanism:steel_ingot` meint dasselbe wie `item:mekanism/steel_ingot`. Der Parser hatte die Zerlegung schon; er hat sie nur mit einer Meldung begleitet, die bei jeder kopierten ID wiederkam | | `Parser.parseSelector` | | |
| 1.14 | ~~Gruppen sind kein Wert~~ — **fertig.** `crushers.members()` liefert die Geräte, `crushers.send(…)` schickt aus dem Speicher an die Gruppe, und als Ziel steht sie überall, wo ein Gerät steht. Der Wert trägt nur den Namen — wer heute dazugehört, entscheidet das Netz | | `Value.Group`, `WorldHost.memberFor` | | |
| 1.16 | ~~Ein Eintrag einer Bestandsliste hat keine Angaben~~ — **fertig.** `it.amount` ist die Menge, `it.item` die Art (nur bei genau einer), `it.fluid` dasselbe für Flüssigkeiten. `sum()` zählt jetzt Mengen zusammen, statt zu werfen, und der Editor bietet nach `it.` nicht mehr die Gerätemitglieder an | | `Interpreter.entryMember`, `Signatures.ENTRY_MEMBERS` | | |
| 1.18 | ~~„Alles von A nach B"~~ — **fertig** (25.08.). `move all from brecher to storage`. `all` steht allein wie `power` und meint Gegenstände, wie ein Worker ohne Filter; Flüssigkeiten bleiben ausdrücklich. Aus dem Speicher heraus ist es die eine Antwort auf „sag, was bewegt wird" | | `grammatik.md` §6, `sprache.md` §4 | | |
| 1.17 | ~~Fächer ansprechen~~ — **fertig.** `brecher_1.slots(3)` und `slots(1..5)` lesen wie eine Liste und stehen zugleich als Quelle oder Ziel eines `move`. Über das ganze Inventar, gezählt ab null; Nummern, die es nicht gibt, fallen weg. Dazu kam die Bereichsform `1..5` in die Sprache | | `Value.DeviceSlots`, `SlotView` | | |
| 1.19 | ~~Fachnummern sieht man nicht~~ — **fertig.** Der Tooltip im Editor zeigt sie längst mitsamt Inhalt; was fehlte, war die Übereinstimmung: Der Blick aufs Gerät las die Fächer der angeschlossenen Seite, `slots(3)` meint das dritte Fach der Maschine. Beides ist jetzt dasselbe | | `DeviceSnapshotPacket.of` | | |
| 1.15 | ~~`move` gibt nichts zurück~~ — **fertig.** `let bewegt = move …` läuft, und `if move … > 0` auch. Die Anweisungsform bleibt daneben bestehen: In einem Ablauf ist ein `move` ein Schritt, und das braucht die Fortsetzung nach einem Neustart | | `Expr.Move`, `Interpreter.doMove` | | |

## 2. Laufzeit

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 2.1 | ~~Globale Werte~~ — **fertig**, alle sieben Aufgaben | | `plan-globale-werte.md` | | |
| 2.2 | ~~Stromverteilung~~ — **fertig** (25.08.). Abgabe und Einspeisung als Worker, Reihenfolge nach `priority`, Energiezellen im Laufwerk, Abgabe als eigene Zahl im Netz-Reiter. Was sich beim Bauen anders ergab, steht in `strom.md` §10 | | `strom.md` | | |
| 2.3 | ~~Abgabe bei `OFF`/`BOOTING`~~ — **beantwortet.** Es fließt nichts ab, solange das Netz nicht läuft; der Fall kann nicht eintreten | | `strom.md` §3 | | |
| 2.4 | ~~Einheit der Abgaberate~~ — **entschieden:** `rate 40 per 1t`, keine neue Form. Ein eigenes Wort `tick` zöge sofort `per second` nach sich | | `strom.md` §9 | | |
| 2.5 | **Halb erledigt** (25.08.): `crusher_1.energy()` liest den Stand einer Maschine, mit Klammern wie `redstone()`. Offen bleibt `network.power` — `network` ist als Wert nirgends gebaut | F | `strom.md` §8 | klein | — |
| 2.6 | ~~Das Fertig-Signal~~ — **fertig.** `device_output` meldet, wenn in einem Gerät von einer Art mehr liegt als beim letzten Blick; was das Netz selbst einlegt, zieht die Grundlinie nach und zählt nie mit. Nicht `device_done`: gemessen wird „dazugekommen", nicht „fertig" | | `DeviceAmounts`, `NotifyingHandlers` | | |
| 2.11 | ~~`log()` sieht niemand~~ — **fertig.** Vier Stufen (`info`, `warn`, `error`, `debug`), Reiter „Log" mit Filter, Herkunft je Zeile, überlebt den Neustart. Die Hinweise der Laufzeit laufen mit hinein | | `LogTabView`, `LogEntry` | | |
| 2.7 | ~~`when`-Bedingungen~~ — **überholt.** Im laufenden Spiel wertet der echte Interpreter aus: Texte, globale Werte, Gerätezustände. Der alte Weg — Zahl gegen Zahl — greift nur ohne Host, also in Prüfungen ohne Welt. Eine kaputte Bedingung hält den Worker an | | `WorkerRuntime.conditionHolds` | | |
| 2.8 | ~~`NetworkCheck` besucht keine Anweisungen~~ — **fertig.** Ein `move` mit unbekanntem Gerätenamen wird gewarnt, in der Anweisung wie im Ausdruck. Ausgespart bleiben örtliche Namen: Parameter, `let`, Schleifenvariablen, globale Werte, Festwerte, Vorlagen, Gruppen und die Rollen eines Multiblocks | | `NetworkCheck.checkMoves` | | |
| 2.9 | ~~Erkennung von Maschinen-Rezepten~~ — **gebaut** (26.08.), Weg B in zwei Schnitten. Ofen, Schmelzofen, Räucherofen und Presse arbeiten für einen Auftrag mit: einlegen, warten, abholen, und der laufende Schritt übersteht den Neustart. Alles andere erklärt `recipe … at … { in … out … }` im Programm. Offen als Schnitt: Flüssigkeiten und Strom als Zutat | | `MachineRecipes`, `DeclaredRecipes` | | |
| 2.10 | ~~Autocrafting~~ — **fertig** (25.08.), in drei Schritten an einem Tag. Fabricator und Aufträge am Controller, `craft(64 item:chest)`, der Reiter, die beiden Ereignisse, alles übersteht den Neustart. Der Planner zerlegt eine Bestellung bis zu dem, was dasteht, trägt Zutaten-Auswahlen durch und kommt mit Kreisen zurecht. `from crafting` hält einen Vorrat, gerechnet gegen Bestand und offene Aufträge | | `CraftingPlanner`, `WorkerRuntime.tickCraftingWorker` | | |

## 3. Editor im Spiel

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 3.1 | ~~Annahme-Probe~~ — **fertig**, Kandidaten aus dem Entwurf | | `ItemCandidates.java` | | |
| 3.2 | ~~Annahme-Probe für Flüssigkeiten~~ — **fertig** (25.08.). `fill(…, SIMULATE)` mit einem Eimer je Probe und den `fluid:`-Angaben aus dem Programm; die Zeile steht bei den Behältern im Tooltip | | `DeviceSnapshotPacket.tankProbe` | | |
| 3.3 | ~~Flüssigkeitsstände im Tooltip~~ — **fertig** | | | | |
| 3.4 | ~~Bearbeitung anfragen~~ — **fertig**, F4 in beiden Fenstern | | `RequestEdit.java` | | |
| 3.5 | ~~Ordner im Projekt~~ — **fertig** (25.08.). `erz/brecher.mf`, zwei Ebenen tief; Anlegen und Umbenennen nehmen den Pfad entgegen, ein eigener Griff dafür fehlt bewusst. Der Ordner neben der Welt liest rekursiv, VS Code sucht die Wurzel des Projekts. Der Punkt steht nicht im Alphabet eines Abschnitts — damit ist `../` unmöglich statt verboten | | `Project.NAME`, `ProgramFolder.listNames` | | |
| 3.6 | ~~LDLib2 prüfen~~ — **geprüft, nicht genommen** (26.08.). Gute Bibliothek: 2.2.x für 1.21.1, reines Java, LGPL, sogar ein CodeEditor-Widget. Absage aus zwei Gründen: Eine Oberfläche lässt sich nicht weich einbinden — sie wäre die erste Pflicht-Abhängigkeit der Mod —, und die Fenster sind gebaut. Der Stand steht in `entscheidungen.md`, damit ein zweiter Blick billig bleibt | | `entscheidungen.md` „LDLib2 geprüft" | | |
| 3.7 | ~~Ob das Geräteprofil dem Analysator etwas zu geben hat~~ — **fertig**, aber an anderer Stelle als gedacht: Die Knotenbeschriftung wird gar nicht gezeichnet, der Analysator malt Würfel. Die Auskunft hängt jetzt am Rechtsklick — „brecher_1: Gegenstände · Fächer 0–26" —, also dort, wo man ohnehin vor der Maschine steht | | `NetworkAnalyserItem.deviceLine` | | |
| 3.8 | ~~Ob der Netz-Reiter globale Werte ändern darf~~ — **entschieden: nur anzeigen.** Sonst wird der Zustand der Fabrik an zwei Stellen umgestellt, und niemand sieht ihr an, wer zuletzt geschaltet hat. Wer schalten will, baut einen Knopf | | `globale-werte.md:200` | | |
| 3.10 | ~~Der Editor bietet an, was nichts kann~~ — **erledigt.** `world`, `network`, `workers` und `multiblocks` sind in beiden Editoren draußen, bis sie etwas tun. `crafting` ist am 25.08. zurückgekommen, aber **nur hinter `from`** — es ist eine Quelle und kein Ziel. `power` blieb durchweg: Die Schreibweise ist entschieden, und ein Strom-Worker hält mit einer Meldung an, die auf `strom.md` zeigt | | `Completions.SOURCES`, `extension.js` | | |
| 3.11 | ~~Auflösungsanzeige~~ — **fertig** (25.08.) im Editor im Spiel: Zeiger auf einen Auswahlausdruck, und im Kasten stehen die Zahl der Arten und die ersten Namen; „trifft nichts" in Rot. In VS Code nicht — dort gibt es keine Registry | | `SelectionSummary`, `Selectors` | | |
| 3.9 | ~~`gerät.count(…)` auf einer Anzeige~~ — **fertig.** Eine Tafel liest jetzt auch aus einer Maschine, mit Auswahl oder ohne. Ohne Welt bleibt es beim `?`: Eine erfundene Null schickte den Spieler zur falschen Maschine | | `DisplayValues.deviceCount` | | |

## 4. VS-Code-Erweiterung

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 4.1 | Sprachserver — Fehlerprüfung und Gerätenamen außerhalb des Spiels | E/F | `umsetzung.md:493` | groß | 4.2, dazu Umfang und Technik |
| 4.2 | **Serverteil gebaut** (25.08.): `config/factorynetwork-server.toml` mit den Grenzen für Nutzercode. Ein Clientteil kommt mit der Brücke zu VS Code — leere Abschnitte auf Vorrat wären Fragen an den Betreiber, die niemand beantworten kann | F | `FnConfig` | klein | 4.1 |
| 4.3 | ~~Projektweite Symbole~~ — **fertig**, in VS Code **und** im Spiel | | | | |
| 4.4 | Die Logik steht zweimal da, gehalten durch `check.js` und den Export-Test | Z | `entscheidungen.md:2114` | — | bewusst |
| 4.5 | ~~In der `on`-Kopfzeile werden die Ereignisse nicht vorgeschlagen~~ — **überholt.** Beide Editoren tun es längst: `check.js` prüft es für VS Code, und für den Editor im Spiel steht der Fall jetzt als Test in `CompletionsTest`. Die Zeile war stehengeblieben | | `CompletionsTest`, `check.js` | | |

## 5. Blöcke und Welt

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 5.1 | ~~Controller-Multiblock~~ — **fertig** (25.08.). Der Controller bleibt genau ein Block; der Anbau steuert Seiten bei und hält nie etwas. Er muss den Controller berühren, kostet keinen Kanal und 1 FE/t | | `entscheidungen.md` „Der Controller bleibt ein Block" | | |
| 5.2 | ~~Ein eigener Speicherblock~~ — **hat sich aufgelöst** (26.08.). Die Zeile stammt aus der Zeit, als der Speicher im Controller saß; seit den Zellen liegt er in Laufwerken, und wer mehr Platz will, stellt eines dazu. Nachgeprüft, weil die Zusage nirgends belegt war: `asecondDriveEnlargesTheStorage` | | `NetworkStorage`, `DriveBlockEntity` | | |
| 5.3 | ~~Anzeigenwand: die Schrift wächst nicht mit~~ — **beantwortet** (26.08.), aber anders als gefragt: Von selbst wächst sie weiter nicht — das wäre der ganze Vorteil einer Wand. Es gibt jetzt `scale 4` im Display-Block, dazu wächst die Sichtweite mit. Eine Zahl für die ganze Tafel, nicht je Zeile | | `DisplayRenderer`, `Decl.Display.Entry.Kind.SCALE` | | |
| 5.4 | Die Zahlen an den Serverbauteilen sind gesetzt, nicht hergeleitet | E | `umsetzung.md:186` | klein | Spielprüfung |

## 6. Dokumentation

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 6.1 | ~~Handbuch~~ — **vierzehn Seiten** (26.08.). „Sprache im Detail" ist die **Referenzseite** und wird aus `Signatures` erzeugt, damit sie nicht auseinanderläuft; neu dazu ist `listen.md`. Alle Beispiele im Handbuch werden übersetzt (`DocExamplesTest`) — die Prüfung nimmt seit dem 26.08. auch `const` und Vorlagen-Deklarationen mit und fand dabei einen Fehler | | `guide/listen.md`, `DocExamplesTest` | |
| 6.2 | ~~Lizenzdatei~~ — **fertig.** MIT, Copyright 2026 DevPanda (Florian Richter). Der Entwurf ist aufgegangen und entfallen | | `LICENSE` | |
| 6.3 | ~~Ob die Hilfe im Spiel ins Buch wandert~~ — **entschieden: nein, nebeneinander.** Die Griffliste beantwortet „was steht hier", das Buch „wie funktioniert das" | | `entscheidungen.md` | |
| 6.4 | ~~WorkerRuntime-Javadoc~~ — **berichtigt** | | | |
| 6.5 | ~~README-Frage~~ — **entfernt**, sie war beantwortet | | | |
| 6.6 | ~~Prioritätenliste~~ — **berichtigt**, mit Vermerk | | | |
| 6.7 | ~~Plan-Kästchen~~ — **abgehakt** | | | |
| 6.10 | ~~`list` auf einer Anzeige~~ — **fertig** (25.08.). Überschrift und je Posten eine Zeile, absteigend nach Menge, ab acht Posten gezählt statt weggelassen. Dabei fiel auf, dass die Knopfnummer im Paket eine Zeilennummer war und der Controller sie als Eintragsnummer las | | `DisplayValues.listing` | | |
| 6.11 | ~~Tests, die nicht fehlschlagen können~~ — **fertig**, alle sieben. `abrokenRackDropsItsProcessors` prüft jetzt, dass das herausfallende Gehäuse seine Bauteile trägt (gegengeprobt: ohne `packAll` fällt er); `theAnalyserMarksFullCables` prüft die eine richtige Antwort statt zweier mit ODER, dazu den Gegenfall mit halber Last | | `FactoryNetworkGameTests` | | |
| 6.8 | ~~Kennzeichnung der Abschnitte~~ — **fertig.** Was `sprache.md` verspricht und der Code nicht hält, trägt jetzt überall einen Vermerk: Chemikalien, `crafting` als Quelle, die drei fehlenden Typen, `send()`, `crafting_finished`, die Auflösungsanzeige | | `sprache.md` | | |
| 6.9 | ~~Kabelbündel-Frage~~ — **nachgetragen** | | | |

## 7. Kompatibilität

| # | Was | Status | Wo | Größe |
|---|---|---|---|---|
| 7.1 | Mekanism-Chemikalien — **entschieden als Kompatibilitätsmodul** (siehe 1.4), nicht als Pflicht-Abhängigkeit. Der Ordner `compat/mekanism` steht seit dem 26.08., mit dem Teil, der ohne die Mod geht. Umfang seit dem 26.08. entschieden: bewegen **und** lagern | F | `compat/mekanism` | groß |
| 7.2 | ~~GuideME eingebunden~~ — **fertig**, `compat/guide` | | | |
| 7.3 | Das Fertig-Signal je Mod (Weg 3) | Z | `umsetzung.md:177` | groß |
| 7.4 | Eigene Generatoren | Z | `strom.md:220` | — |

---

## Was am meisten bringt

Stand nach dem 26.08.: **Auf der ganzen Liste steht nur noch eine offene
Entscheidung** — 5.4, die Zahlen an den Serverbauteilen, und die beantwortet
eine Runde Spielen und kein Gespräch. Alles andere ist Arbeit, und die beiden
großen Posten (2.9 und 1.4) haben ihre Richtung.

~~**1. Die Stromverteilung** (2.2).~~ **Gebaut am 25.08.** Eine Fabrik
versorgt ihre Maschinen jetzt selbst, und der Vorrat wächst mit den
Energiezellen im Laufwerk.

~~**1. Der Controller-Multiblock** (5.1).~~ **Gebaut am 25.08.** Die 384
Geräte je Netz sind keine Grenze mehr.

**1. Weitere Handbuchseiten** (6.1). Vierzehn Seiten stehen; `listen.md` kam
am 26.08. dazu. Schreibarbeit ohne Risiko — und das, was die Mod für jemanden
von außen zugänglich macht. Was noch fehlt, zeigt am ehesten ein Blick auf
`sprache.md`: Was dort ein eigenes Kapitel hat und im Handbuch nur nebenbei
vorkommt, ist ein Kandidat.

~~**2. Autocrafting**~~ (2.10). **Fertig am 25.08.** Der letzte ausgegraute
Reiter ist keiner mehr, und `crafting` steht als Quelle in beiden Editoren.
Offen bleibt daneben 2.9 — Processing-Rezepte an Maschinen —, aber das ist ein
eigenes Vorhaben und keine Lücke im Autocrafting.

~~**2. Die kleineren Reste.**~~ **Alle durch** (25./26.08.): 3.5 (Ordner im
Projekt), 5.3 (Schrift auf der Anzeigenwand) und 1.11 (globale Listen) sind
gebaut, 5.2 hat sich beim Nachsehen aufgelöst, und 3.6 ist geprüft und
abgelehnt.

~~**3. Rezepte an Maschinen erkennen**~~ (2.9). **Gebaut am 26.08.**, Weg B in
zwei Schnitten: Was das Spiel offenlegt, kann die Fabrik von selbst; für alles
andere schreibt der Spieler ein `recipe` ins Programm. Damit ist der letzte
große Posten der Liste zu — offen bleibt nur noch 1.4 (Mekanism).

Was hier bis zum 25.08. unter „Kleines mit großer Wirkung" stand — `list` auf
einer Anzeige (6.10), die Auflösungsanzeige im Editor (3.11), die
Serverkonfiguration (4.2) —, ist gebaut.
