# Filter-Vorlagen — Umsetzungsplan

> **Für ausführende Agenten:** Aufgabe für Aufgabe. Erst der Test, dann der
> Code, dann der Lauf, dann der Commit.

**Ziel:** Eine Auswahl bekommt einen Namen und steht überall dort, wo heute
eine geschriebene Auswahl steht — im Worker, in `move`, in `count`, in
`insert`, in `has`.

**Vorgehen:** `filter <name> { … }` wird eine eigene Deklarationsart. Jede
Zeile im Block ist eine Auswahl; `except` davor nimmt weg statt dazuzulegen.
Aufgelöst wird über `ItemSelection.resolve` aus dem Syntaxbaum heraus — also
über dieselbe Stelle wie jede andere Auswahl. Zur Laufzeit wird ein
Vorlagenname zu `Value.Selection` beziehungsweise `Value.FluidSelection`;
beide Werttypen gibt es, und jede Stelle, die Auswahlen verarbeitet, kennt
sie bereits.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5, NeoForge-GameTests.

**Entwurf:** `docs/filter-vorlagen.md`

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.**
- **Echte Umlaute**, keine Unicode-Escapes.
- Das Paket `lang` bleibt ohne Minecraft-Typen in den Klassen, die Tests
  laden. Alles, was die Registry braucht, gehört nach `runtime` und wird im
  GameTest geprüft.
- Nach jeder Aufgabe committen, Meldungen deutsch, ohne Präfixe.
- `./gradlew test` für die schnellen Tests, `./gradlew runGameTestServer` für
  die Welt, `node editor/vscode/check.js` für die Erweiterung.

## Verifizierter Bestand

Alles hier wurde vor dem Schreiben im Code nachgesehen:

| Was | Wo |
|---|---|
| `filter` ist schon ein Schlüsselwort — als Worker-Angabe | `lang/TokenType.java:23`, Karte `:66` |
| `except` ist schon ein Schlüsselwort und wird im Postfix geparst | `lang/TokenType.java:35,76`, `parse/Parser.java:845` |
| `Expr.Except(base, exclusions, span)`, Ketten verschachteln | `parse/Parser.java:845-848` |
| Die Auswahl-Grammatik kennt `selection = selTerm { 'except' selTerm }` | `docs/grammatik.md:246` |
| `parseDeclaration()` schaltet über den Tokentyp | `parse/Parser.java:74-95` |
| `parseGroup()` ist die Vorlage für eine Blockdeklaration | `parse/Parser.java:222` |
| `Decl` ist versiegelt mit `name()` und `span()` | `lang/ast/Decl.java:14` |
| **Fünf erschöpfende Schalter** brechen bei einem neuen Record | `GlobalCheck:112`, `NetworkCheck:47`, `ProgramSize:38`, `Signatures:290`, `flow/BlockIndex:42` |
| `Definitions` findet die Erklärung zu einem Namen | `lang/Definitions.java` |
| `ItemSelection.resolve(Expr)` löst Selektor, `Except` und `Amount` auf | `runtime/ItemSelection.java:43` |
| `FluidSelection.resolve(Expr)` tut dasselbe für Flüssigkeiten | `runtime/FluidSelection.java:39` |
| Aufgelöst wird mit Zwischenspeicher, `invalidate()` beim Registry-Wechsel | `runtime/ItemSelection.java:36-41` |
| `Value.Selection(List<Item>, long amount)` und `Value.FluidSelection` gibt es | `runtime/Value.java:80` und `:76` |
| `WorldHost.itemsOf` nimmt `Selection` bereits an | `runtime/WorldHost.java:598` |
| `Interpreter` hält das `Program` selbst — **kein neuer Host-Zugang nötig** | `runtime/Interpreter.java:41,188` |
| Ein nackter Name wird über `resolveName` aufgelöst: Rahmen, globaler Wert, eigenes Gerät, Netzgerät, Fehler | `runtime/Interpreter.java:653,716-746` |
| **`Expr.Except` fällt im Interpreter unter den Tisch:** `evaluate(except.base())` | `runtime/Interpreter.java:659` |
| `WorkerRuntime.filterItems` löst die Worker-Angabe über den Baum auf | `runtime/WorkerRuntime.java:637` |
| Gruppen liegen im Worker als `Map<String, DeviceGroup>` und werden beim Übernehmen gefüllt | `runtime/WorkerRuntime.java:74,178-185` |
| Der Editor holt die eingebauten Namen aus dem Sprachpaket | `client/screen/Completions.java:242` |
| `signatures.json` wird aus `Signatures` geschrieben und im Test verglichen | `lang/SignaturesExportTest`, `editor/vscode/data/signatures.json` |

---

## Aufgabe 1: `filter` als Deklaration

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/ast/Decl.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/parse/Parser.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/parse/FilterTemplateParseTest.java`

**Schnittstellen:**
- Liefert `Decl.FilterTemplate(String name, List<Expr> includes, List<Expr> excludes, Span span)`.
  Alle folgenden Aufgaben bauen darauf.

**Achtung:** `filter` ist bereits ein Schlüsselwort — ein neuer Tokentyp wird
**nicht** gebraucht. Der Parser unterscheidet nach Ort: auf oberster Ebene
eine Deklaration, im Worker-Block eine Angabe. Und `Decl` ist versiegelt: Der
neue Record macht fünf `switch` unvollständig. Der Übersetzer nennt sie, und
das ist erwünscht — sie werden in Aufgabe 2 abgearbeitet.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
class FilterTemplateParseTest {

    @Test
    @DisplayName("Zeilen ohne except legen dazu")
    void plainLinesAreIncludes() {
        Parser.ParseResult result = Parser.parse("""
                filter ore_factory {
                    tag:c/ores
                    item:deepslate_coal_ore
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.FilterTemplate template = assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
        assertEquals("ore_factory", template.name());
        assertEquals(2, template.includes().size());
        assertTrue(template.excludes().isEmpty());
    }

    @Test
    @DisplayName("except nimmt weg")
    void exceptLinesAreExcludes() {
        // Ein Block aus "tag:c/ores" und "except item:ancient_debris":
        // includes.size() == 1, excludes.size() == 1, und der Ausschluss ist
        // ein Expr.Selector und kein Expr.Except.
    }

    @Test
    @DisplayName("Eine Zeile darf selbst ein except enthalten")
    void aLineMayCarryItsOwnExcept() {
        // "tag:c/ores except item:ancient_debris" bleibt EIN Eintrag in
        // includes, und zwar ein Expr.Except. Das ist die bestehende
        // Auswahl-Grammatik und keine Sonderregel.
    }

    @Test
    @DisplayName("Ein Worker nimmt weiter eine filter-Angabe")
    void theWorkerEntryStillParses() {
        // Beweist, dass die Deklaration die Worker-Angabe nicht verdrängt:
        // "worker x { from a \n to storage \n filter ore_factory }"
    }

    @Test
    @DisplayName("Ein Fehler im Block hält die nächste Deklaration nicht auf")
    void anErrorDoesNotStopTheNextDeclaration() {
        // Ein Block mit einer unlesbaren Zeile, darunter ein vollständiger
        // worker: result.hasErrors(), und trotzdem steht der Worker in den
        // Deklarationen.
    }
}
```

- [ ] **Schritt 2: Test laufen lassen, Fehlschlag ansehen** (`./gradlew test`)
- [ ] **Schritt 3: `Decl.FilterTemplate` ergänzen**, mit Javadoc: warum
      nackte Zeilen und nicht `members` (siehe Entwurf).
- [ ] **Schritt 4: `parseFilterTemplate()`** nach dem Muster von
      `parseGroup()`: Name, `{`, dann je Zeile — bei `except` erst
      `advance()`, dann die Auswahl in `excludes`, sonst in `includes`.
      Die Auswahl selbst kommt aus dem bestehenden Ausdrucksparser.
- [ ] **Schritt 5: `parseDeclaration()`** um `case FILTER` ergänzen; die
      Aufzählung der Deklarationswörter in der Fehlermeldung und in der
      Fehlerbehebung (`recoverToDeclaration`, `Parser:1059`) mitziehen.
- [ ] **Schritt 6: Test grün, `./gradlew test` ganz, committen.**

---

## Aufgabe 2: Die versiegelten Schalter nachziehen

**Dateien:** `GlobalCheck.java:112`, `NetworkCheck.java:47`,
`ProgramSize.java:38`, `Signatures.java:290`, `flow/BlockIndex.java:42`,
dazu `Definitions.java` und `Project.java`.

**Achtung:** Nichts hier ist Fleißarbeit — an jeder Stelle steht die Frage,
was eine Vorlage dort bedeutet. Wer sie nur mit `default -> {}` stillstellt,
verliert genau die Auskunft, für die die Klasse da ist.

- [ ] **Schritt 1:** `./gradlew compileJava` — der Übersetzer nennt die
      Stellen. Die Liste in den Commit schreiben.
- [ ] **Schritt 2: `ProgramSize`** — eine Vorlage zählt wie eine Gruppe:
      die Deklaration selbst plus je Zeile eins. Nachsehen, wie `Decl.Group`
      dort gezählt wird, und es genauso machen.
- [ ] **Schritt 3: `BlockIndex`** — eine Vorlage hat keinen Anweisungsblock,
      also nichts zu indizieren. Mit einer Zeile Kommentar, warum.
- [ ] **Schritt 4: `GlobalCheck`** — in einer Vorlage stehen nur Auswahlen,
      keine Namen. Nichts zu prüfen, mit Begründung im Code.
- [ ] **Schritt 5: `NetworkCheck`** — hier kommt in Aufgabe 4 die
      Verdeckungswarnung hin. Vorerst die Stelle mit einem `case` und einem
      Verweis auf Aufgabe 4 versehen.
- [ ] **Schritt 6: `Signatures`** — die Form der Deklaration für den Editor:
      `filter NAME { … }`. Test: `SignaturesExportTest` schreibt
      `signatures.json` neu; die Datei einchecken.
- [ ] **Schritt 7: `Definitions`** — der Name einer Vorlage muss auffindbar
      sein, sonst zeigt „wo ist das erklärt" im Editor ins Leere. Test in
      `DefinitionsTest`.
- [ ] **Schritt 8:** `./gradlew test`, committen.

---

## Aufgabe 3: Die Auflösung

**Dateien:**
- Neu: `src/main/java/dev/devpanda/factorynetwork/lang/FilterKind.java`
- Neu: `src/main/java/dev/devpanda/factorynetwork/runtime/FilterTemplates.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/FilterKindTest.java`

**Schnittstellen:**
- `FilterKind.of(Decl.FilterTemplate)` → `ITEM`, `FLUID`, `MIXED`, `EMPTY`.
  **Reine Baumbetrachtung, keine Registry** — deshalb im Paket `lang` und in
  gewöhnlichen Tests prüfbar.
- `FilterTemplates.items(Decl.FilterTemplate)` → `List<Item>`,
  `FilterTemplates.fluids(...)` → `List<Fluid>`. Vereinigung aller
  `includes`, danach jedes Ergebnis aus `excludes` entfernt. Beide werfen
  einen `ScriptError`, der **den Namen der Vorlage nennt**, wenn nichts übrig
  bleibt.

**Achtung:** Die Reihenfolge ist erst zusammenlegen, dann abziehen — nicht
zeilenweise abwechselnd. Ein `LinkedHashSet` hält dabei die Reihenfolge, in
der die Arten zuerst auftauchten; das ist die, die der Spieler geschrieben
hat.

- [ ] **Schritt 1: Der fehlschlagende Test für `FilterKind`** — eine Vorlage
      aus `item:` und `tag:` ist `ITEM`, eine aus `fluid:` ist `FLUID`, beides
      zusammen `MIXED`, ein leerer Block `EMPTY`. Ein `tag:` allein zählt als
      `ITEM`, solange Flüssigkeits-Tags nicht aufgelöst werden
      (`offene-punkte.md` 1.3) — mit dieser Begründung als Kommentar.
- [ ] **Schritt 2: `FilterKind` bauen**, Test grün.
- [ ] **Schritt 3: `FilterTemplates` bauen.** Auflösung ausschließlich über
      `ItemSelection.resolve` und `FluidSelection.resolve`; **keine zweite
      Fassung der Selektorauflösung.**
- [ ] **Schritt 4: GameTest** `aFilterTemplateResolvesToSeveralKinds` — eine
      Vorlage aus zwei Selektoren plus einer Ausnahme, geprüft an echten
      Gegenständen. Der einzige Ort, an dem die Registry dafür da ist.
- [ ] **Schritt 5:** beide Läufe, committen.

---

## Aufgabe 4: Prüfungen beim Übernehmen

**Dateien:**
- Neu: `src/main/java/dev/devpanda/factorynetwork/lang/FilterCheck.java`
- Ändern: `NetworkCheck.java`, `Project.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/FilterCheckTest.java`

**Schnittstellen:** `FilterCheck.run(Program, Map<String, Decl.FilterTemplate>)`
→ `List<Diagnostic>`, wie `EventCheck.run`.

Geprüft wird:

| Fall | Stufe | Meldung |
|---|---|---|
| Gemischt (`item:` und `fluid:`) | Fehler | „Eine Vorlage ist entweder für Gegenstände oder für Flüssigkeiten." |
| Leerer Block | Fehler | „Die Vorlage X wählt nichts aus." |
| Nur `except`-Zeilen | Fehler | dieselbe Meldung — es gibt nichts, wovon abgezogen würde |
| Eine Vorlage nennt eine andere | Fehler | „Eine Vorlage darf keine andere enthalten." plus der Hinweis, die Zeilen zu wiederholen |
| Name doppelt vergeben | Fehler | über `Project`, wie bei Worker und Gruppe |
| Ein Gerät im Netz heißt wie die Vorlage | Warnung | „Die Vorlage X verdeckt das Gerät X." — über `NetworkCheck`, das die Gerätenamen kennt |

**Achtung:** Fehler, keine Warnungen — anders als bei `EventCheck`. Eine
gemischte oder leere Vorlage wird durch eine weitere Datei nicht besser; sie
ist an Ort und Stelle falsch.

- [ ] **Schritt 1: Der fehlschlagende Test** — je ein Fall aus der Tabelle,
      der letzte (Verdeckung) im GameTest, weil er Gerätenamen braucht.
- [ ] **Schritt 2: `FilterCheck` bauen und einhängen**, wo `EventCheck`
      eingehängt ist.
- [ ] **Schritt 3: `Project`** um die Namensprüfung ergänzen.
- [ ] **Schritt 4: `NetworkCheck`** um die Verdeckungswarnung ergänzen.
- [ ] **Schritt 5:** beide Läufe, committen.

---

## Aufgabe 5: Die Vorlage im Worker

**Dateien:** `runtime/WorkerRuntime.java`

**Schnittstellen:** Die Laufzeit hält die Vorlagen wie die Gruppen —
`Map<String, Decl.FilterTemplate>`, gefüllt beim Übernehmen an derselben
Stelle wie `groups` (`WorkerRuntime:178-185`).

- [ ] **Schritt 1: GameTest** `aWorkerFiltersByTemplate` — ein Worker mit
      `filter ore_factory` bewegt genau die Arten der Vorlage und die
      ausgenommene nicht. **Der Test muss auch zeigen, dass die Ausnahme
      wirkt** — sonst prüft er nur, dass irgendetwas bewegt wurde.
- [ ] **Schritt 2: `filterItems`** ergänzen: Ist die Angabe ein
      `Expr.Name` und steht dafür eine Vorlage, über `FilterTemplates`
      auflösen; sonst wie bisher.
- [ ] **Schritt 3:** Dasselbe für den Flüssigkeits-Worker.
- [ ] **Schritt 4:** Ein unbekannter Name in `filter` meldet weiterhin
      verständlich — nicht „trifft nichts", sondern „unbekannte Vorlage",
      mit dem nächstliegenden Namen als Hinweis (`NameDistance`).
- [ ] **Schritt 5:** beide Läufe, committen.

---

## Aufgabe 6: Die Vorlage im Interpreter — und die Lücke bei `except`

**Dateien:** `runtime/Interpreter.java`

**Achtung:** Hier stecken zwei Dinge, und der Reihe nach.

**Zuerst die Lücke.** `case Expr.Except except -> evaluate(except.base())`
(`Interpreter:659`) wirft die Ausschlüsse weg. Im Worker wirkt `except`, in
`move` und `count` nicht — obwohl `sprache.md:215` es zeigt. Das ist genau
die Stelle, durch die auch die Vorlage geht.

- [ ] **Schritt 1: Der Test, der die Lücke zeigt.** GameTest:
      `move tag:… except item:… from a to b` bewegt die ausgenommene Art
      nicht. Er muss **rot** sein, bevor etwas geändert wird — wenn nicht,
      war die Annahme falsch, und dann wird der Befund im Plan berichtigt
      statt ein Fix eingebaut.
- [ ] **Schritt 2:** `Expr.Except` zu einer aufgelösten Auswahl auswerten:
      `ItemSelection.resolve` beziehungsweise `FluidSelection.resolve` auf
      den ganzen Ausdruck, Ergebnis als `Value.Selection` /
      `Value.FluidSelection`. Test grün.
- [ ] **Schritt 3: Der Test für die Vorlage** — `move ore_factory from a to b`
      und `storage.count(ore_factory)`.
- [ ] **Schritt 4: `resolveName`** ergänzen: **nach** dem globalen Wert und
      **vor** den Geräten nach einer Vorlage sehen (Begründung im Entwurf:
      Gerätenamen kommen aus der Welt, nicht aus dem Programm). Die
      Deklarationen liegen im `Program`, das der Interpreter ohnehin hält —
      kein neuer Zugang über den `Host`.
- [ ] **Schritt 5:** `64 ore_factory` prüfen: Die Menge sitzt über
      `withAmount` auf der aufgelösten Auswahl und heißt 64 insgesamt.
- [ ] **Schritt 6:** beide Läufe, committen.

---

## Aufgabe 7: Der Editor im Spiel

**Dateien:** `client/screen/Completions.java`, `lang/Signatures.java`

- [ ] **Schritt 1:** `filter` steht in der Auswahl der Deklarationen auf
      oberster Ebene.
- [ ] **Schritt 2:** Wo eine Auswahl erwartet wird, stehen die Namen der
      Vorlagen des Projekts zur Wahl — neben `item:`, `fluid:` und `tag:`.
- [ ] **Schritt 3:** Im Block einer Vorlage wird `except` vorgeschlagen und
      **keine** Deklarationswörter.
- [ ] **Schritt 4:** Die Anzeige, worauf sich eine Auswahl auflöst, gilt auch
      für einen Vorlagennamen. Wenn es sie für Muster schon gibt, dieselbe
      Stelle benutzen; wenn nicht, hier keine neue bauen — dann in die
      offenen Punkte damit.
- [ ] **Schritt 5:** `./gradlew test`, committen.

---

## Aufgabe 8: Die VS-Code-Erweiterung

**Dateien:** `editor/vscode/` — Grammatik, `snippets/manifold.json`,
`check.js`, `data/signatures.json` (erzeugt)

- [ ] **Schritt 1:** `check.js` um die Fälle ergänzen: nach `filter ` auf
      oberster Ebene ein Name, im Block `except`, an einer Auswahlstelle die
      Vorlagennamen der offenen Dateien.
- [ ] **Schritt 2:** Die Grammatik prüfen — `filter` ist als Wort schon
      eingefärbt; ein Block danach darf die Einfärbung nicht verlieren.
- [ ] **Schritt 3:** Einen Baustein für die Vorlage in die Snippets.
- [ ] **Schritt 4:** `./gradlew test` schreibt `signatures.json` neu;
      einchecken. `node editor/vscode/check.js` muss grün sein.
- [ ] **Schritt 5:** committen.

---

## Aufgabe 9: Doku

**Dateien:** `docs/sprache.md`, `docs/grammatik.md`, `docs/umsetzung.md`,
`docs/offene-punkte.md`, `docs/beispiele.md`

- [ ] **Schritt 1: `grammatik.md`** — `filterDecl = 'filter' NAME '{' NL {
      filterEntry NL } '}'`, `filterEntry = [ 'except' ] selection`, und die
      Zeile bei den Deklarationen mitziehen.
- [ ] **Schritt 2: `sprache.md`** — ein Abschnitt bei den Auswahlen, mit dem
      Beispiel aus dem Entwurf und den drei Festlegungen: Menge heißt
      insgesamt, Gegenstände oder Flüssigkeiten, keine Verschachtelung.
- [ ] **Schritt 3: `umsetzung.md`** — Merkmalstabelle und ein Abschnitt
      „Filter-Vorlagen (seit dem Umsetzungstag)" mit dem, was beim Bauen
      auffiel.
- [ ] **Schritt 4: `offene-punkte.md`** — die `except`-Lücke als erledigt
      oder, falls Aufgabe 6 sie nicht bestätigt hat, den Befund berichtigen.
- [ ] **Schritt 5: `beispiele.md`** — ein Beispiel, das die Vorlage in zwei
      Workern benutzt. Es muss laufen; abgeschrieben wird nur, was geprüft
      ist.
- [ ] **Schritt 6:** committen.

---

## Stopp-Kriterien

Diese Arbeit läuft ohne Rückfragen. Bei folgendem wird **nicht geraten**,
sondern der Punkt dokumentiert und das Thema zurückgestellt:

- Die Lücke bei `except` (Aufgabe 6, Schritt 1) zeigt sich nicht — dann ist
  der Befund falsch, und der Plan wird berichtigt statt ein Fix gebaut.
- Ein Test, der nach zwei Fixversuchen rot bleibt.
- Ein Umbau, der über den Entwurf hinausgeht — insbesondere ein eigener
  Werttyp für Vorlagen oder eine zweite Fassung der Selektorauflösung.
- Eine Entwurfslücke, die eine Entscheidung des Projektinhabers braucht.

Der Zustandsbericht am Morgen zählt mehr als der letzte Commit.
