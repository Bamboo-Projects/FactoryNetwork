# Offene Punkte — Bestandsaufnahme

Alles, was in `docs/` und im Code als unfertig steht, an einer Stelle.

Stand: 2026-08-26 (nach den beiden Schnitten am Wertemodell und der
Handbucharbeit)

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
> **2.9 ist gebaut**, Weg B in zwei Schnitten. Erst die Maschinen mit fester
> Form: Ofen, Schmelzofen, Räucherofen und die eigene Presse arbeiten für
> einen Auftrag mit — einlegen, warten, abholen. Der laufende Schritt wird
> gespeichert, der Plan nicht: Ein Plan ist eine Absicht, ein Erz im Ofen eine
> Tatsache über die Welt. Dann `recipe … at … { in … out … }` für alles
> andere, als Deklaration im Programm und nicht als Muster-Item.
>
> **1.4 und 7.1 sind gebaut.** `chemical:` löst sich auf, bewegt sich, wird
> gezählt und lagert in Chemikalienzellen. Der Kern spricht dabei in Texten,
> weil Java die Klassen einer Signatur beim Laden auflöst — eine Klasse mit
> einem Mekanism-Typ im Rückgabetyp ließe sich ohne die Mod nicht mehr laden.
> Dabei wurde ein Rückfall gebaut und nach einer Messung wieder
> zurückgenommen, und eine Lücke im Prüflauf benannt: Ein per `setBlock`
> gesetzter Mekanism-Tank gibt an keiner Seite eine Capability heraus.
>
> **2.9 war zuerst nur erkundet.** Automatische Erkennung fremder
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

> **Schnitt 1 aus `ressourcenarten.md` steht (26.08.).** Die drei Zwillingspaare
> im Wertemodell sind zwei Records mit einem Art-Feld geworden. Der Entwurf
> hatte Arbeit gemessen; gefunden wurde ein Fehler: **Die Frage, welchen Weg
> `move` nimmt, stand zweimal da**, und die Fassung für Chemikalien kannte die
> schon aufgelöste Auswahl nicht. Eine Chemikalie aus einer Schleife ging
> damit in die Gegenstandsauflösung, traf nichts — und keine Auswahl heißt
> dort *alles*. Die Kiste wurde leergeräumt, ohne Meldung; dasselbe in `count`
> und `gerät.count(…)`. Das ist der Fehler, den `sprache.md` den schlimmsten
> der Sprache nennt, und er ist kein Versehen, sondern das, was drei Kopien
> mit der Zeit tun.

**Status:** **F** = fehlt schlicht · **E** = wartet auf eine Entscheidung ·
**Z** = bewusst zurückgestellt, kein Versäumnis

---

## 1. Sprache

| # | Was | Status | Wo | Größe | Blockiert durch |
|---|---|---|---|---|---|
| 1.1 | ~~`output()`, `send()`, `busy`~~ — **erledigt.** `output()` und `busy` sind gestrichen (sie sagten dasselbe wie `move` beziehungsweise nichts Nachprüfbares), `send()` an einer Gruppe ist mit 1.14 gebaut | | `sprache.md` §6, §8 | | |
| 1.2 | ~~Listenoperationen~~ — **fertig**, alle fünf. `where` und `sort` werten je Eintrag aus, mit `it` als diesem Eintrag | | `sprache.md` §12 | | |
| 1.3 | ~~Flüssigkeits-Tags~~ — **fertig.** `fluidtag:c/molten` löst gegen die Fluid-Registry auf, `tag:` bleibt bei den Gegenständen. Ein Worker mit `fluidtag:` gilt als Flüssigkeits-Worker: `WorkerKind` nennt die Ressource, nicht die Schreibweise | | `FluidSelection`, `WorkerKind.resource` | | |
| 1.4 | ~~Mekanism-Anbindung~~ — **gebaut** (26.08.). `chemical:` löst sich auf, bewegt sich mit `move` und mit einem Worker, wird gezählt und lagert in Chemikalienzellen im Laufwerk. Der Kern spricht in Texten, Mekanism-Typen stehen in vier Klassen unter `compat/mekanism`. Offen als Schnitt: Chemikalien in `recipe` | | `compat/mekanism`, `ChemicalStore` | | |
| 1.5 | `import`/Module — reserviert, tut nichts | Z | `Parser.java:86` | — | bewusst, bis ein Projekt den Namensraum sprengt |
| 1.6 | Request/Response als eigene Form | Z | `sprache.md:992` | mittel | mit `emit`/`on`/`await` nachbaubar |
| 1.7 | ~~Rechte im Mehrspielerbetrieb~~ — **gebaut** (25.08.). `protection.programs` in der Serverkonfiguration: `OFF` (Vorgabe, wie bisher), `OWNER`, `OPS`. Geschützt sind Übernehmen und Entwurf speichern; die Beschriftungspistole nicht — sie ändert die Welt, und dafür gibt es Schutzmods | | `FnProtection` | | |
| 1.8 | **Zum Teil gebaut** (26.08.): `Chemical` steht im Wertemodell — eine Chemikalie ist ein Wert wie eine Gegenstandsart, trägt eine Menge, steht in einer Schleife und ist an einem Posten mit `it.chemical` abzulesen. Im Kern als Kennung und nie als Mekanism-Typ. **Offen mit Begründung:** `Job` — `craft` liefert eine Nummer, und mehr will bisher niemand ablesen; dafür gibt es `crafting_finished` und den Reiter. `Set<T>` — der Fall, der ihn braucht, fehlt weiter | F | `Value.ChemicalValue`, `sprache.md` §5 | klein | — |
| 1.19 | **Die Ressourcenart als offene Registry** statt eines festen Aufzählungswerts. **Schnitt 1, 2 und 3 sind gebaut** (26.08.). Schnitt 1: Aus sechs Records im Wertemodell wurden zwei mit einem Art-Feld, dazu `ResourceKind`; von zehn gemessenen Stellen bleiben drei, und alle drei sind Sprachfläche (§5a). Dabei fiel ein Fehler auf, nach dem niemand gesucht hatte — die Artfrage in `move` stand zweimal da, und die für Chemikalien kannte die aufgelöste Auswahl nicht: Eine Chemikalie aus einer Schleife räumte die Kiste leer. Schnitt 2: `ResourceStore` und `NetworkStores`; die Verdrahtung eines vierten Speichers ist jetzt ein Eintrag, die Klasse dahinter bleibt eine (§5b — der Entwurf hatte an dieser Stelle zu viel versprochen). Schnitt 3: `ResourceKind` ist eine Schnittstelle, `ResourceKinds` die Registry, angemeldet wird beim Laden und danach nie wieder. Eine fremde Art kostet eine Klasse und einen Aufruf — und **keine Zeile im Kern**; belegt an einer erfundenen vierten Art, nicht an den eigenen drei (§5c). **Zweite Hälfte ebenfalls gebaut** (§5d): Der Übersetzer fragt die Registry, statt vier eigene Listen zu führen — sie hatten drei verschiedene Antworten auf ein unbekanntes Wort. Dabei fiel auf, dass ein **Tippfehler** sechs Meldungen erzeugte, von denen keine ihn nannte; jetzt ist es eine mit Vorschlag. Und der Editor im Spiel bot vier Präfixe an, ohne `chemical:` — eine fünfte Kopie derselben Liste. Die Präfixe gehen über `.fn-status.json` nach VS Code, und ohne Spiel steht dort der Zusatz „ohne Spiel" statt einer stillen Halbwahrheit. **Offen bleibt allein die zweite Achse** (Maschinenzugriff), ohne die 7.5 nicht fertig wird | F | `ressourcenarten.md` §5d | mittel | zweite Achse |
| 1.20 | ~~Ein Connector, der klickt~~ — **gebaut** (26.08.), am selben Tag gewünscht. `altar.click()` an dem Connector, der ohnehin dranhängt, und kein zweiter Block: Ein- und Ausgang trennt hier schon der Code und nicht die Bauform. Läuft über den vollen Vanilla-Weg, damit Schutzmods ihre Ereignisse bekommen. Ein Fenster geht nicht auf — für einen Spieler, den es nicht gibt, ist das folgenlos | | `WorldHost.clickAt`, `Signatures.MEMBERS` | | |
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
| 2.5 | ~~Strom als Wert~~ — **fertig** (26.08.). `crusher_1.energy()` liest den Stand einer Maschine, mit Klammern wie `redstone()`. `network.power` und `network.capacity` lesen den Vorrat des Netzes, ohne Klammern: Sie liegen im Controller und sind keine Nachfrage in der Welt. Beide Editoren schlagen sie nach `network.` vor, und die Referenzseite kennt sie | | `Interpreter.member`, `Signatures.NETWORK_MEMBERS` | | |
| 2.6 | ~~Das Fertig-Signal~~ — **fertig.** `device_output` meldet, wenn in einem Gerät von einer Art mehr liegt als beim letzten Blick; was das Netz selbst einlegt, zieht die Grundlinie nach und zählt nie mit. Nicht `device_done`: gemessen wird „dazugekommen", nicht „fertig" | | `DeviceAmounts`, `NotifyingHandlers` | | |
| 2.11 | ~~`log()` sieht niemand~~ — **fertig.** Vier Stufen (`info`, `warn`, `error`, `debug`), Reiter „Log" mit Filter, Herkunft je Zeile, überlebt den Neustart. Die Hinweise der Laufzeit laufen mit hinein | | `LogTabView`, `LogEntry` | | |
| 2.7 | ~~`when`-Bedingungen~~ — **überholt.** Im laufenden Spiel wertet der echte Interpreter aus: Texte, globale Werte, Gerätezustände. Der alte Weg — Zahl gegen Zahl — greift nur ohne Host, also in Prüfungen ohne Welt. Eine kaputte Bedingung hält den Worker an | | `WorkerRuntime.conditionHolds` | | |
| 2.8 | ~~`NetworkCheck` besucht keine Anweisungen~~ — **fertig.** Ein `move` mit unbekanntem Gerätenamen wird gewarnt, in der Anweisung wie im Ausdruck. Ausgespart bleiben örtliche Namen: Parameter, `let`, Schleifenvariablen, globale Werte, Festwerte, Vorlagen, Gruppen und die Rollen eines Multiblocks | | `NetworkCheck.checkMoves` | | |
| 2.9 | ~~Erkennung von Maschinen-Rezepten~~ — **gebaut** (26.08.), Weg B in zwei Schnitten. Ofen, Schmelzofen, Räucherofen und Presse arbeiten für einen Auftrag mit: einlegen, warten, abholen, und der laufende Schritt übersteht den Neustart. Alles andere erklärt `recipe … at … { in … out … }` im Programm. Flüssigkeiten und Chemikalien dürfen als Zutat darin stehen (26.08.): Der Auftrag füllt sie beim Anfangen aus dem Netzspeicher ein, beschafft sie aber nicht — fehlen sie, wartet er und nennt Sorte und Menge, ohne die Gegenstände anzufassen. Strom bleibt draußen — **geprüft und zurückgestellt** (26.08.): Die Maschine bekommt ihn über die Stromverteilung, und eine Zahl im Rezept wäre geraten. `in 1000 power` wird beim Übernehmen gewarnt, statt still zu verschwinden | | `MachineRecipes`, `DeclaredRecipes`, `NetworkCheck.checkRecipePower` | | |
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
| 4.1 | **Zum Teil gebaut** (26.08.): Der Controller schreibt `.fn-status.json` neben die Programmdateien — Fehler mit Datei, Zeile und Spalte, dazu die Namen der Connectoren und Anzeigen. VS Code trägt sie ein. Kein Port, kein neuer Zugang, keine neue Erlaubnis: Wer die Programmdateien sieht, sieht auch diese. **Offen bleibt der Serverfall** — dort kommt niemand an die Dateien, und dafür steht die Schnittstelle mit zwei Erlaubnisstufen weiter aus | E/F | `ProgramStatus`, `extension.js` | mittel | Umfang und Technik der Schnittstelle |
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
| 6.1 | ~~Handbuch~~ — **zwanzig Seiten** (26.08.). „Sprache im Detail" ist die **Referenzseite** und wird aus `Signatures` erzeugt, damit sie nicht auseinanderläuft; dazu kamen `listen.md`, `auswahlen.md`, `fluessigkeiten.md`, `server.md`, `ereignisse.md`, `erste-anlage.md` und `analysator.md`. `fluessigkeiten.md` bündelt, was über das Bewegen von Flüssigkeiten und Gasen auf vier Seiten verstreut lag; `server.md` ist die erste Seite, die nicht an den Spieler gerichtet ist; `ereignisse.md` war die letzte große Lücke — die sieben eingebauten Ereignisse standen nirgends beisammen, und der Unterschied zwischen `device_output` und `device_changed` entscheidet, ob eine Fabrik im Kreis läuft. Alle Beispiele werden übersetzt (`DocExamplesTest`). **Fünf Stellen berichtigt** (26.08.), an denen das Handbuch dem Code widersprach: fünf Reiter statt sechs mit ausgegrauter Fertigung, zwei von vier Zellenarten, zweimal „`gerät.count(…)` auf einer Anzeige geht noch nicht" (geht seit dem 25.08.) und „eine Gruppe ist ein Ziel, kein Wert" (seit 1.14 falsch) | | `guide/ereignisse.md`, `DocExamplesTest` | |
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
| 7.1 | ~~Mekanism-Chemikalien~~ — **gebaut** (26.08.), siehe 1.4. Kompatibilitätsmodul und keine Pflicht-Abhängigkeit: Ohne Mekanism läuft alles wie vorher | | `compat/mekanism` | |
| 7.2 | ~~GuideME eingebunden~~ — **fertig**, `compat/guide` | | | |
| 7.3 | Das Fertig-Signal je Mod (Weg 3) | Z | `umsetzung.md:177` | groß |
| 7.4 | Eigene Generatoren | Z | `strom.md:220` | — |
| 7.8 | ~~**Ein Speicherbus wie in AE2**~~ — **fertig** (26.08.), alle vier Schnitte am Tag des Wunsches. `store kiste_1 { priority 5  filter tag:c/ores }`: Der Inhalt zählt zum Bestand, das Netz lagert dort ein und holt dort heraus, der Filter gilt fürs Einlagern und nicht fürs Holen. Durchgereicht statt gespiegelt, einmal je Tick gelesen. Schnitt 4 hat nichts gebaut, sondern zwei Dinge belegt: Mehrere Busse tragen (der Prüflauf lief auf Anhieb durch, wie bei 5.2), und das Tick-Lesen kostet rund 24 ns je Fach — bei 64 Kisten ein Neuntausendstel Tick. **Keine Grenze**, weil es nichts zu begrenzen gibt; die Messung steht in `speicherbus.md` §7 samt dem, was sie nicht sagt | | `speicherbus.md` §7, `StorageBus` | | |
| 7.5 | **Ars Nouveau.** ~~Source als eigene Ressourcenart~~ — **gebaut** (26.08.). Vier Klassen in `compat/ars` und ein Aufruf beim Laden; keine Zeile im Kern. Geschrieben wird `source:source` — die Präfixform ist die einzige, die eine fremde Mod bekommen kann, und warum, steht in `ressourcenarten.md` §7. Angemeldet wird auch ohne die Mod, damit die Meldung stimmt. **Offen:** die Zahl des Zwischenhalts (10 000) ist eine Annahme und eine Balancefrage, und der Weg an echten Blöcken ist ungegangen | F | `ressourcenarten.md` §7 | | Zahl des Zwischenhalts |
| 5.5 | ~~**Eine Anlage über einen Block statt über Namen**~~ — **gebaut** (26.08.), am Tag des Wunsches. Der `GatewayBlock` ist ein Kabelstück mit Namensschild: Was hinter ihm am Kabel hängt, gehört zu seiner Anlage. Die Beschriftung gewinnt, ein zweites Gateway ist die Grenze, zwei auf einem Gerät heben sich auf, Kanäle vermehrt er nicht. Der Name entsteht im Graphen — Sprache, Wertemodell und beide Editoren mussten nicht angefasst werden | | `entscheidungen.md` „Eine Anlage darf auch ein Block sein", `GatewayRegions` | | |
| 5.6 | **Der Connector im Kabelblock**, wie bei AE2. Gewünscht am 26.08., entschieden auf **Weg B** (echtes Multipart). **Drei von vier Schnitten sind gebaut** (26.08.). Schnitt 1: `ConnectorPart` ist alles, was ein Connector ist, ohne alles, was ein Block ist — kein Verhalten geändert, belegt durch den unveränderten Prüflauf. Schnitt 2: `CableBusBlockEntity` trägt bis zu sechs davon, `Connectors` ist der eine Weg, einen zu finden; `move` läuft Ende zu Ende durch einen Anschluss am Kabel. Schnitt 3: **ein Gerät ist Ort und Seite** (`DevicePos`) — zwei Anschlüsse an einem Block sind zwei Geräte, und die drei schweren Stellen sind gelöst: Redstone gibt je Fläche die eigene Stärke und auf freien Flächen die stärkste, das Benennen trägt die Fläche vom Klick bis zum Paket, `machineSide(state)` ist als Frage an einen Anschluss verschwunden. Dabei fiel auf, dass die **Anlagenerkennung** ein Kabel nur für eine Leitung hielt: Ein Anschluss daran hätte den Anlagennamen des Gateways nie bekommen. Schnitt 4: **Setzen, Treffen, Aussehen** — Rechtsklick mit einem Connector auf eine Kabelfläche setzt ihn dorthin, schleichend mit leerer Hand nimmt ihn ab, beim Abbauen des Kabels fallen alle heraus; Trefferfläche und zwölf erzeugte Modelle stehen unter demselben Test. 4b: Namenszug stapelt und nennt die Fläche, Jade fragt die getroffene Fläche (und war am Kabel gar nicht angemeldet), der Analysator fasst eine Stelle zu einem Punkt mit beiden Namen zusammen. **Weg B ist damit vollständig** — offen bleibt allein, dass nichts davon je in einem Client lief | | `connector-im-kabel.md` §8 | | |
| 7.6 | **Industrial Foregoing.** ~~Prüfen, was eigen ist~~ — **geprüft am 26.08.** an `industrialforegoing-1.21-3.6.39.jar`. Befund: **nichts fehlt.** Die Mod bringt keinen eigenen Capability-Typ mit; ihr Paket `capability` enthält zwei Klassen, und beide sind Titanium-Bausteine über den Standardschnittstellen (`SidedFluidTankComponent`, ein Energiehalter). **Pink Slime** ist kein neuer Ressourcentyp, sondern Gegenstand (`pink_slime_ball`, `_ingot`, Block) und Flüssigkeit — beides erreichen `item:` und `fluid:` heute. Die Fächer sind **seitenbezogen** (`SidedFluidTankComponent`), und genau so greift unser Connector ohnehin zu: über die Fläche, in die er sieht. **Ungespielt** — geprüft ist das Jar, nicht die Maschine | | — | | |
| 7.7 | **Applied Energistics.** ~~Entwurf~~ — **steht** (26.08.): `ae2.md`, aus `ae2-19.2.17.jar` abgelesen. **Entschieden: daneben, nicht darin** — das ME-Netz ist für uns ein Gerät wie eine Kiste, vier Gründe stehen im Entwurf. Zwei Befunde: AE2 trägt an seinen Blöcken siebzehn gewöhnliche Item-Handler, ein Connector am Pattern Provider **funktioniert also heute schon**; und das ganze Netz hängt an `AECapabilities.ME_STORAGE`, das heute niemand fragt. **Der Haken:** Ein ME-Netz als Quelle für `item:` verlangt zuerst Schnitt 3 aus `maschinenzugriff.md`. Offen ist die **Handlungsquelle** — ob unser Zugriff AE2s Sicherheitsterminal achtet; Empfehlung steht im Entwurf | E | `ae2.md` | groß | Handlungsquelle |

---

## Was am meisten bringt

Stand nach dem 26.08.: **Drei Fragen liegen offen**, und keine davon hält
Arbeit auf. 5.4 (die Zahlen an den Serverbauteilen) beantwortet eine Runde
Spielen und kein Gespräch. 1.19 (die offene Registry) ist eine Haltungsfrage,
und ihre ersten beiden Schnitte sind unabhängig davon richtig — der erste ist
gebaut. 4.1 (der Serverfall der Brücke) wartet auf Umfang und Technik. Die
beiden großen Posten (2.9 und 1.4) sind gebaut. Was bleibt, ist
Schreibarbeit, benannte Schnitte und der Sprachserver.

~~**1. Die Stromverteilung** (2.2).~~ **Gebaut am 25.08.** Eine Fabrik
versorgt ihre Maschinen jetzt selbst, und der Vorrat wächst mit den
Energiezellen im Laufwerk.

~~**1. Der Controller-Multiblock** (5.1).~~ **Gebaut am 25.08.** Die 384
Geräte je Netz sind keine Grenze mehr.

**1. Weitere Handbuchseiten** (6.1). Neunzehn Seiten stehen; `listen.md`,
`auswahlen.md`, `fluessigkeiten.md`, `server.md`, `ereignisse.md` und
`erste-anlage.md` kamen am 26.08. dazu, und die Fehlersuche kennt jetzt die Meldungen der Fertigung und
der Chemikalien. Schreibarbeit ohne Risiko — und das, was die Mod für
jemanden von außen zugänglich macht.

~~Was noch fehlt, ist eine Lücke im Einstieg.~~ **Geschrieben** (26.08.):
`erste-anlage.md` geht den ganzen Weg einmal durch. Dabei fielen zwei Dinge
auf, die keine Schreibarbeit waren: Die Titelseite versprach „vier Blöcke,
dann läuft es" und verschwieg den Serverschrank — ohne ihn nimmt das Netz
kein Programm an. Und ein Ofen braucht **zwei** Connectoren: Er nimmt oben an
und gibt unten heraus, und `machineInventory()` fragt die Capability
seitengenau. Ein Beispiel mit einem Connector hätte das Erz gleich wieder aus
dem Eingang geholt.

~~**2. Autocrafting**~~ (2.10). **Fertig am 25.08.** Der letzte ausgegraute
Reiter ist keiner mehr, und `crafting` steht als Quelle in beiden Editoren.

~~**2. Die kleineren Reste.**~~ **Alle durch** (25./26.08.): 3.5 (Ordner im
Projekt), 5.3 (Schrift auf der Anzeigenwand) und 1.11 (globale Listen) sind
gebaut, 5.2 hat sich beim Nachsehen aufgelöst, und 3.6 ist geprüft und
abgelehnt.

~~**3. Rezepte an Maschinen erkennen**~~ (2.9). **Gebaut am 26.08.**, Weg B in
zwei Schnitten: Was das Spiel offenlegt, kann die Fabrik von selbst; für alles
andere schreibt der Spieler ein `recipe` ins Programm.

~~**4. Mekanism**~~ (1.4, 7.1). **Gebaut am 26.08.** `chemical:` löst sich
auf, bewegt sich, wird gezählt und lagert in Chemikalienzellen. Ohne Mekanism
läuft alles wie vorher.

### Womit die nächste Sitzung anfängt

~~**Schnitt 4 aus `connector-im-kabel.md`**~~ — **gebaut am 26.08.**
Weg B ist damit vollständig: Ein Kabelblock trägt bis zu sechs Anschlüsse,
sie lassen sich setzen, treffen, benennen, abnehmen und sehen.

~~**Schnitt 4b, die drei Anzeigen.**~~ **Gebaut am 26.08.** Namenszug,
Jade und Analysator kennen jetzt Anschlüsse an Kabelflächen.

**Die Statusanzeige am Anschluss ist gebaut** (26.08.). Jeder Anschluss trägt
ein Lämpchen: hellgrün erreichbar, grau namenlos, gelb doppelt vergeben, rot
ohne Kanal, dunkelgrau ohne Netz. **Null zusätzliche Zeichenaufrufe** — der
Ring liegt im Teilmodell, das ohnehin gezeichnet wird, und das Lämpchen am
Connectorblock im gebackenen Blockmodell. Ein Paket geht nur, wenn sich der
Zustand wirklich ändert. Betrieb und Füllstand bleiben draußen; die Rechnung
dazu steht in `statusanzeige.md` §3. Nebenbei liest Jade jetzt denselben
Stempel, statt dieselbe Frage ein zweites Mal zu rechnen.

### Womit die nächste Sitzung anfängt: echte Blockmodelle

**Gewünscht am 27.08.**, mit zwei Vorbildern aus fremden Mods: ein Podest aus
Ars Nouveau (breite Deckplatte, schmale Säule, breiter Sockel) und zwei
Rechner-Blöcke in Blockbench (Bildschirm, Tastatur, Turm mit Schächten — je
ein Dutzend Kästen). Der Satz dazu: *„sowas meine ich mit 3d models"*.

**Der Befund ist eindeutig.** Bis auf den Serverschrank und die Anschlüsse am
Kabel ist **jeder** Maschinenblock ein texturierter Würfel:

| Block | heute |
|---|---|
| Controller, Fabricator | voller Würfel (`cube_bottom_top`) |
| Gateway, Router, Controller-Anbau, Kreativquelle | voller Würfel (`cube_all`) |
| Terminal, Laufwerk, Presse, Brennkammer | voller Würfel (`orientable`) |
| Serverschrank | 4 Kästen — der einzige mit Form |
| Anschluss am Kabel | 2 Kästen (Platte und Ring) |

**Das Werkzeug dafür steht schon:** `tools/modellblick.py` zeichnet ein Modell
isometrisch mit seinen echten Texturen, in einer Sekunde statt eines
Client-Starts.

```
python tools/modellblick.py block/controller block/terminal block/drive
```

Es kennt die Vanilla-Eltern (`cube_all`, `orientable`, …), legt mehrere
Modelle übereinander (`--single`) und zeigt die **Vorderseiten**, nicht die
Rückseiten. Beleuchtung, Umgebungsverdeckung und alles Durchsichtige fehlen —
für die Frage „stimmt die Form" reicht es.

**Die Entscheidung, die vor dem ersten Kasten fällt:** Ein Modell, das nicht
mehr den ganzen Würfel füllt, braucht `noOcclusion()` am Block und eine eigene
`VoxelShape` — sonst sieht man durch die Aussparungen hindurch, und man greift
neben das, was man sieht. Zwei Wege:

1. **Relief im Würfel bleiben.** Ein voller Kern von 0 bis 16, darauf erhabene
   Rahmen, Blenden, Lüftungsgitter, Füße. Sieht dreidimensional aus, ändert
   weder Verdeckung noch Trefferfläche, kostet **keine Zeile Java**.
2. **Echte Silhouette** wie das Podest im Vorbild. Kostet je Block
   `noOcclusion`, eine `VoxelShape` und einen Prüflauf, der beide zusammenhält
   — so wie `CableLayoutTest` es für das Kabel tut.

Der Vorschlag: **Weg 1 für die Maschinen, die in einer Wand stehen**
(Laufwerk, Serverschrank, Presse, Brennkammer), **Weg 2 für die, die
freistehen und etwas darstellen** — Controller, Terminal, Gateway. Beim
Gateway ist die Öffnung ohnehin schon in der Textur; ein Bogen, durch den man
wirklich hindurchsieht, wäre der Block, der die Anlage markiert.

#### Stand am Abend des 27.08.: zehn Blöcke haben eine Form

**Weg 2 für alle**, nicht nur für die freistehenden — Weg 1, das Relief im
vollen Würfel, ist an keiner Stelle nötig gewesen.

| Block | was er jetzt ist |
|---|---|
| Gateway | Torbogen: Sockel, Sturz, vier Ecksäulen, acht Schultern, zwei Leuchtbänder außen |
| Laufwerk | Gehäuse auf vier Füßen, davor die Blende, darin das Schachtfeld versenkt |
| Controller | Deckplatten oben und unten, dazwischen ein zurückspringender Körper mit vier Kantensäulen |
| Terminal | Gehäuse, Rahmen, versenkter Bildschirm, darunter eine vorstehende Konsole |
| Presse | Rahmen, versenkter Arbeitsraum, darin Führungssäulen, Stempelkopf und Amboss |
| Brennkammer | Rahmen, Klappe, versenktes Sichtfenster, davor der Griff |
| Fabricator | Sockel, zurückspringender Körper, Deckel, abgesetzter Aufbau |
| Controller-Anbau | Käfig aus zwölf Kantenleisten um einen zurückspringenden Kern |
| Kreativquelle | acht Eckklötze um denselben Kern |
| Router | derselbe Käfig, dickere Leisten, die Buchse als echte Vertiefung |

**Keine einzige neue Textur.** Jeder Kasten schneidet aus der vorhandenen
Textur den Ausschnitt, den ein Würfelmodell an dieser Stelle genommen hätte.
Nieten, Rahmenkanten und Fasen sitzen dadurch weiter dort, wo sie saßen — und
was die Textur vorher als Vertiefung malte, ist jetzt eine. Nur beim Gateway
ist eine dazugekommen (`gateway_glow`) und eine Malerei verschwunden: Der
Torbogen war aufgemalt und ist jetzt Geometrie.

**Was dabei herauskam und für den Rest gilt:**

1. `tools/assets.py` rechnet selbst aus, welche Flächen kein Mensch sieht.
   Von Hand abgezählt hatte ich sie beim Gateway; der Helfer kommt Fläche für
   Fläche auf dasselbe Ergebnis.
2. `FacingShapes` dreht die Trefferfläche mit. Ohne das hätte ein Laufwerk
   nach Osten die Trefferfläche eines nach Norden.
3. `MachineLayoutTest` fährt die vier Grundproben für alle umgebauten Blöcke.
   Wer den nächsten umbaut, trägt ihn in die Liste ein.
4. Der Betrachter hatte zwei Fehler, und beide waren an Würfeln unsichtbar:
   die Tiefensortierung verdreht, und die Oberseite in z gespiegelt. Der
   zweite hat vier Anläufe gekostet, das Modell zu „reparieren", das gar
   nicht kaputt war — erst ein Blick in Blockbench hat es entschieden.
   **Wenn ein Detail im Betrachter nicht stimmt, ist Blockbench die
   Referenz und nicht das Modell.**

Später am selben Tag sind die übrigen fünf dazugekommen: Presse und
Brennkammer nach dem Muster von Laufwerk und Terminal, der Fabricator mit
Sockel, zurückspringendem Körper, Deckel und Aufbau, der Controller-Anbau als
Käfig aus zwölf Kantenleisten und die Kreativquelle mit acht Eckklötzen.

**Auch der Router, und der war zuerst abgeschrieben.** Sein Renderer malt die
Bahnkennung über die volle Fläche jeder der sechs Seiten — das las sich wie
„jede Form lässt den Ring irgendwo über Luft schweben". Ein Blick in
`router_lanes()` sagt etwas anderes: Der Ring liegt zwischen Blockpixel 1 und
14,75, seine Mitte ist durchsichtig, und das mit Absicht — dort steckt das
dicke Kabel. Solange die äußeren vier Blockpixel bündig bleiben, liegt jeder
sichtbare Teil auf Material. Genau so ist der Käfig gebaut, und die Buchse in
der Mitte ist jetzt eine echte Vertiefung, in der das Kabel steckt.

Die Zahl vier steht deshalb im Javadoc von `MachineLayouts.router()`: Wer die
Leisten dünner macht, lässt den Ring schweben.

**Was ein Umbau sonst noch anfassen muss:** Jeder Block mit einem
BlockEntityRenderer malt auf seine Front, und zwar in Blockkoordinaten. Wird
die Front versenkt, muss der Renderer denselben Abstand kennen — beim Laufwerk
ist genau das zunächst untergegangen, und die Zellen schwebten einen
Blockpixel vor dem Schacht. `FaceOverlay.tile` nimmt dafür einen letzten
Parameter, und die Zahl kommt aus dem Layout, nicht aus einer zweiten
Konstante. Betroffen sind Laufwerk, Serverschrank, Router und die
Anzeigetafel.

#### Später am 27.08.: die Gegenstände, und zwei Blöcke nachgebessert

**Siebenunddreißig Gegenstände haben einen Körper.** Statt der Extrusion, die
`item/generated` aus dem Alphakanal rechnet, ist jeder ein Quader in der Größe
seines Umrisses — vier Blockpixel dick bei Speicherzellen und Stempeln, zwei
bei Platinen, einer bei einem Blech. Kristall, Rohkristall,
Beschriftungspistole und Analysator bleiben flach: Ihre Silhouette trifft kein
Kasten.

Dabei eine Falle, die `ItemModelTest` jetzt bewacht: Wer eigene Kästen
mitbringt, darf nicht von `item/generated` erben. Minecraft erkennt an der
Ahnenreihe `builtin/generated`, dass es die Flächen aus `layer0` bauen soll —
ein Kind ohne `layer0` hätte danach gar keine mehr und wäre in der Hand
unsichtbar.

**Das Gateway war zum zweiten Mal zu hohl** und steht jetzt bei 86 statt 76
Hundertsteln Material.

**Die Presse hat einen echten Hohlraum** und einen Stempel, der sich bewegt.
Er steht in einem eigenen Modell, das der `PressRenderer` zeichnet und
verschiebt — dasselbe Verfahren wie bei den Kabelanschlüssen. Die Bewegung
rechnet der Client aus der Spielzeit und nicht aus dem Fortschritt: Der
erreicht den Client nur mit einem Blockupdate, und jeden Tick eines zu
schicken wäre Netzlast für eine Animation.

**Was der Betrachter grundsätzlich nicht zeigt:** Umgebungsverdeckung,
Beleuchtung, die Ansicht in Hand und Inventar und ob `noOcclusion` wirklich
greift. Dafür braucht es einen Client-Lauf, und den hat noch keiner der zehn
Blöcke gesehen. Was dort zu prüfen ist:

1. Greift `noOcclusion` überall — kein schwarzes Loch im Torbogen, in der
   Router-Buchse, im Hohlraum der Presse, in den Fugen
2. Der Stempel der Presse: Fährt er, und trifft er den Amboss statt die Luft
   oder das Blech darunter
3. Die siebenunddreißig Gegenstände in Hand, Inventar und am Boden — dazu
   die drei Ausbauten, und beim Funk-Modul besonders: Sein Kasten reicht bis
   an die Antenne hinauf, und um sie herum ist die Textur durchsichtig. Wenn
   das im Spiel als Klotz erscheint statt als Antenne, ist der Umriss die
   falsche Antwort und die Extrusion die richtige.
4. Umgebungsverdeckung und Licht auf den versenkten Flächen
5. Die Anschlussplatten der Kabel gegen die neuen Fronten
6. Die Laufwerkszellen sitzen im Schacht statt davor

### Stand nach der Nacht auf den 27.08.

Fünf Punkte durchgezogen: die VS-Code-Erweiterung auf 1.0, Source aus Ars
Nouveau als Ressourcenart, der AE2-Entwurf, die Prüfung von Industrial
Foregoing und die Gateway-Textur. Was danach oben ansteht:

1. **Schnitt 3 aus `maschinenzugriff.md`** — die eingebauten drei Arten ziehen
   auf `machine()` um. Er stand unter „erst wenn jemand gespielt hat"; seit dem
   AE2-Entwurf hat er einen zweiten Grund: **Ohne ihn kommt kein ME-Netz als
   Quelle für `item:` herein**, außer über eine Sonderabfrage mitten im
   Gegenstandsweg.
2. **Die AE2-Brücke** (`ae2.md`, Schnitte 2 und 3). Wartet auf 1.
3. **4.1, der Serverfall der Editorbrücke** — die Technik steht noch aus.

**Womit es weitergeht: einmal spielen.** Weg B ist vollständig, und alles
daran ist test-grün und im Client nie gelaufen. Was ein Prüflauf grundsätzlich
nicht sehen kann, steht an drei Stellen:

- **Die Teile am Kabel** — Modell, Renderer, Trefferfläche im Zusammenspiel.
  Ob eine Platte an der richtigen Fläche sitzt, sagt keine Zusicherung.
- **Der Namenszug** mit mehreren Namen übereinander.
- **Die Lämpchen** — ob der Ring an der richtigen Fläche sitzt, ob das
  Lämpchen am Connectorblock zu sehen ist und ob sich die fünf Farben
  nebeneinander unterscheiden lassen.
- **Die drei alten Restrisiken**: `click()` an echten Ars-Nouveau-Blöcken, ein
  offener Stromanschluss unter einem Flux-Netz im Tick-Takt, ein Speicherbus
  an einem Drawer-Controller.

Danach zieht die Liste oben weiter — 1.19 fehlt nur noch die zweite Achse
nicht mehr, und offen ist vor allem 4.1 (Serverfall der Editorbrücke).

~~Die Gateway-Textur, vom Controller-Anbau geliehen~~ — **gezeichnet am
26.08.** Sie war Byte für Byte dieselbe; zwei Blöcke mit demselben Gesicht
sind im Spiel nicht zu unterscheiden, und das Gateway ist genau der Block, den
man in einer Anlage sucht. Der Rahmen bleibt der der Familie, eigen ist das
Motiv: ein Torbogen statt der vier Zellen.

~~**Schnitt 1 aus `ressourcenarten.md`**~~ — **gebaut am 26.08.**, als erster
Commit der Sitzung. Nachgemessen in §5a desselben Dokuments: Von zehn Stellen
bleiben drei, und alle drei sind Sprachfläche. Die Absicherungen zuerst — die
Namen auf der Platte und die Texte aus `describe()` —, weil beides ein
Übersetzer nie meldet und erst in einer alten Welt oder im Protokoll auffällt.

~~**Schnitt 2, die drei Speicher hinter eine Schnittstelle.**~~ **Gebaut am
26.08.** `ResourceStore` und `NetworkStores`; nachgemessen in §5b. In den
Speichern war nichts abgedriftet — in den **Auflösern** schon: `chemicalsOf`
gab eine leere Liste zurück, wo `itemsOf` und `fluidsOf` werfen, und leer
heißt weiter unten *alles*. Ein Tippfehler in `chemical:…` füllte irgendein
Gas ein. Behoben, mit Prüflauf. Ausdrücklich stehengeblieben sind die Maschinenseite (die
zweite Achse, die eine Registry ebenfalls braucht) und die gemeinsame
Index-Mechanik der drei Speicher: Die Commits dieses Tages sind ungespielt,
und der Speicher ist die Stelle, an der ein Fehler einen Bestand kostet statt
einer Meldung.

~~**Schnitt 4 des Speicherbusses (7.8).**~~ **Erledigt am 26.08.**, und zwar
ohne eine Zeile Code: Mehrere Busse tragen (Prüflauf lief auf Anhieb durch),
und das Tick-Lesen kostet rund 24 ns je Fach. **Keine Grenze**, weil es nichts
zu begrenzen gibt. Die Messung samt dem, was sie nicht sagt, steht in
`speicherbus.md` §7.

Dazu am Handbuch: `ereignisse.md` und `erste-anlage.md` sind neu, `const`
steht jetzt bei den Werten statt nur in einem Beispiel, und **fünf Stellen
waren dem Code hinterher** — darunter eine Titelseite, die den Serverschrank
verschwieg, obwohl ohne ihn kein Programm übernommen wird.

~~**Schnitt 3, die Registry.**~~ **Gebaut am 26.08.**, nachdem die
Haltungsfrage beantwortet war — in zwei Hälften. Erst `ResourceKind` als
Schnittstelle und `ResourceKinds` als Registry: Eine fremde Art kostet eine
Klasse und einen Aufruf und **keine Zeile im Kern**, belegt an einer
erfundenen vierten Art statt an den eigenen drei. Dann der Übersetzer, der
sie fragt, statt vier eigene Listen zu führen — die hatten drei verschiedene
Antworten auf ein unbekanntes Wort.

Dabei fielen zwei Dinge auf, die niemand gesucht hatte: Ein **Tippfehler** im
Präfix erzeugte sechs Meldungen, von denen keine ihn nannte (jetzt eine, mit
Vorschlag), und der Editor im Spiel bot vier Präfixe an, ohne `chemical:` —
eine fünfte Kopie derselben Liste. Die Präfixe gehen seitdem auch nach
VS Code, das dort vorher gar keine vorschlug.

---

**Zwei der drei Fragen sind am 26.08. beantwortet:**

1. ~~**Die Haltungsfrage**~~ (`ressourcenarten.md` §6): **Ja** — fremde Mods
   dürfen die Sprache erweitern. Damit ist Schnitt 3 frei und **Ars Nouveau**
   (7.5) hat einen Weg. Die Begründung steht in `entscheidungen.md`, samt dem,
   was daran nicht umkehrbar ist.
2. ~~**Punkt 4.1, der Serverfall:**~~ **lesen und schreiben**, und der
   Serverbetreiber entscheidet in der Konfiguration — dreistufig: aus, lesen,
   schreiben. Offen bleibt allein die **Technik**: wie die Verbindung
   aussieht und wer daran anklopfen kann.
3. **Punkt 5.4:** die Zahlen an den Serverbauteilen. Die beantwortet eine
   Runde Spielen und kein Gespräch.

**Was von den beiden Antworten noch aussteht:**

- **Die zweite Achse** (1.19, letzter Rest): Ein Registry-Eintrag sagt, wie
  seine Art aussieht, wie sie sich auflöst und wo sie lagert — nicht, wie man
  sie an einer fremden Maschine liest und schreibt. `IItemHandler`,
  `IFluidHandler` und Mekanisms `IChemicalHandler` stehen dafür nebeneinander
  und haben nichts miteinander zu tun. **Ohne sie wird 7.5 nicht fertig**, und
  das ist kein Mangel der Registry — es steht so in `entscheidungen.md`.
- **Die Technik von 4.1:** **Entwurf steht** (`editorbruecke.md`, 26.08.).
  Drei Wege gemessen, Empfehlung ist **B — ein Ordner, den der Client führt**,
  statt eines Ports auf dem Rechner des Spielers. Es macht nichts nach außen
  auf, benutzt den Weg, der seit dem 25.08. läuft, und erbt die Trennung
  zwischen Entwurf und laufendem Stand, die die Konfliktfrage schon
  beantwortet. **Zu entscheiden: ob B statt A.** Die Konfiguration kommt mit
  der Verbindung und nicht davor — dieselbe Regel wie bei 4.2.

**Was ohne weitere Antwort weitergeht:** weitere Handbuchseiten (6.1).
Zwanzig stehen; die zweite Achse und der Netzanalysator sind am 26.08.
dazugekommen. Was noch fehlt, ist die Feinarbeit — die 3D-Szenen, die GuideME
kann und die diese Mod noch nirgends nutzt.

**Nebenbei gefunden** (26.08.): `AnalyserData.Summary` reist zum Client und
wird dort **nirgends gezeichnet**. Die Zahlen — Geräte, Kabel, ohne Kanal,
ohne Namen, doppelte Namen, knappe und volle Strecken — sind da; es fehlt die
Zeile, die sie anzeigt. Deshalb steht die Zusammenfassung auch nicht im
Handbuch: Was nicht zu sehen ist, wird dort nicht versprochen.

**Und was Vorrang hat vor allem:** Bericht aus dem Spiel. Die Commits vom
26.08. sind test-grün und ungespielt, und drei Stellen tragen Restrisiko —
`click()` an echten Ars-Nouveau-Blöcken, der offene Stromanschluss unter einem
Flux-Netz, das im Tick-Takt zieht, und der Speicherbus an einem
Drawer-Controller. Zum letzten sagt `speicherbus.md` §7 jetzt, was gemessen
ist und was nicht: Der Anteil dieser Mod ist klein, was hinter fremdem
`getStackInSlot` steckt, ist ungemessen.
Die drei benannten Schnitte dieser Nacht sind durch: der Worker mit
`filter chemical:…`, Flüssigkeiten wie Chemikalien als Zutat in einem
`recipe`, und Strom als Zutat — der letzte durch eine Prüfung, die ihn
zurückstellt.

Was hier bis zum 25.08. unter „Kleines mit großer Wirkung" stand — `list` auf
einer Anzeige (6.10), die Auflösungsanzeige im Editor (3.11), die
Serverkonfiguration (4.2) —, ist gebaut.
