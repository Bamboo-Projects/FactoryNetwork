# Globale Werte — Umsetzungsplan

> **Für ausführende Agenten:** Aufgabe für Aufgabe. Erst der Test, dann der
> Code, dann der Lauf, dann der Commit.

**Ziel:** Ein Wert, den alle Dateien eines Projekts sehen, der sich aus
Funktionen und Ereignisblöcken ändern lässt und den Serverneustart überlebt.

**Vorgehen:** `global name = literal` wird eine eigene Deklarationsart. Der
Wert lebt im Controller und wird dem Interpreter über seinen `Host` gereicht —
dieselbe Naht, über die er auch Bestände und Redstone erreicht. Reaktivität
braucht keine Maschinerie: Anzeigen und `when` werten ohnehin je Tick aus.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5, NeoForge-GameTests.

**Entwurf:** `docs/globale-werte.md`

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.**
- **Echte Umlaute**, keine Unicode-Escapes.
- Das Paket `lang` bleibt ohne Minecraft-Typen in den Klassen, die Tests
  laden.
- Nach jeder Aufgabe committen, Meldungen deutsch, ohne Präfixe.
- `./gradlew test` für die schnellen Tests, `./gradlew runGameTestServer` für
  die Welt.

## Verifizierter Bestand

Alles hier wurde vor dem Schreiben im Code nachgesehen:

| Was | Wo |
|---|---|
| `Decl` ist ein `sealed interface` mit `name()` und `span()` | `lang/ast/Decl.java:14` |
| 21 Stellen schalten über `Decl` — in acht Dateien | u. a. `Parser`, `Definitions`, `NetworkCheck`, `ProgramSize`, `Project`, `BlockIndex`, `WorkerRuntime`, `ControllerBlockEntity` |
| Deklarationswörter stehen als `TokenType` in einer Karte | `lang/TokenType.java:20,62` |
| Der Parser schaltet über den Tokentyp | `lang/parse/Parser.java:77-82` |
| Fehlerbehebung kennt dieselben Wörter | `lang/parse/Parser.java:961` |
| `forBlock(null)` liefert heute nichts — oberste Ebene hat keine Formen | `lang/Signatures.java:233` |
| Anweisungsformen stehen in `STATEMENT`, `let` mit `NEW_NAME`, `"="`, `EXPR` | `lang/Signatures.java:177-180` |
| Der Interpreter hat einen Stapel von Karten, `find` sucht von innen | `runtime/Interpreter.java:43`, `find(String)` |
| `Interpreter.Host` ist die einzige Naht zur Welt | `runtime/Interpreter.java:78-111` |
| `ValueCodec.write(Value)` liefert einen `CompoundTag` | `runtime/flow/ValueCodec.java` |
| Der Controller speichert Projekt, Speicher, Fluide, Strom, Abläufe | `ControllerBlockEntity.saveAdditional`, Zeile 1370 |
| **Es gibt keinen Typprüfer** | `lang/` enthält keinen |

---

## Aufgabe 1: `global` als Deklaration

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/TokenType.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/ast/Decl.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/parse/Parser.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/parse/GlobalParseTest.java`

**Schnittstellen:**
- Liefert: `Decl.Global(String name, Expr value, Span span)` und
  `TokenType.GLOBAL`. Alle folgenden Aufgaben bauen darauf.

**Achtung:** `Decl` ist versiegelt. Ein neuer Record macht jedes erschöpfende
`switch` unvollständig — der Übersetzer nennt die Stellen, und das ist
erwünscht. Sie werden in Aufgabe 2 abgearbeitet.

- [x] **Schritt 1: Den fehlschlagenden Test schreiben**

`src/test/java/dev/devpanda/factorynetwork/lang/parse/GlobalParseTest.java`:

```java
package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code global} ist die einzige Deklaration ohne geschweifte Klammern.
 *
 * <p>Alle anderen öffnen einen Block; diese steht in einer Zeile, weil sie
 * einen Wert erklärt und keine Angaben sammelt.
 */
class GlobalParseTest {

    @Test
    @DisplayName("Ein Text als Anfangswert")
    void aTextInitialValue() {
        Parser.ParseResult result = Parser.parse("global modus = \"tag\"");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.Global global = assertInstanceOf(Decl.Global.class,
                result.program().declarations().get(0));
        assertEquals("modus", global.name());
        assertInstanceOf(Expr.Text.class, global.value());
    }

    @Test
    @DisplayName("Eine Zahl als Anfangswert")
    void aNumberInitialValue() {
        Parser.ParseResult result = Parser.parse("global vorrat = 0");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.Global global = assertInstanceOf(Decl.Global.class,
                result.program().declarations().get(0));
        assertEquals("vorrat", global.name());
    }

    @Test
    @DisplayName("Mehrere globale Werte nebeneinander")
    void severalGlobals() {
        Parser.ParseResult result = Parser.parse("""
                global modus = "tag"
                global vorrat = 0

                worker erz {
                    from grube
                    to storage
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(3, result.program().declarations().size());
    }

    @Test
    @DisplayName("Ohne Anfangswert ist es ein Fehler")
    void withoutAValueItIsAnError() {
        Parser.ParseResult result = Parser.parse("global modus");

        assertTrue(result.hasErrors(),
                "ein globaler Wert ohne Wert hat keinen Typ");
    }

    @Test
    @DisplayName("Ein Fehler in einer Zeile hält die nächste nicht auf")
    void anErrorDoesNotStopTheNextDeclaration() {
        Parser.ParseResult result = Parser.parse("""
                global kaputt =
                worker erz {
                    from grube
                    to storage
                }""");

        assertTrue(result.hasErrors());
        assertTrue(result.program().declarations().stream()
                        .anyMatch(declaration -> declaration instanceof Decl.Worker),
                "die Fehlerbehebung muss den Worker noch finden");
    }
}
```

- [x] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*GlobalParseTest*"`
Erwartet: Übersetzungsfehler — `Decl.Global` gibt es nicht.

**Hinweis:** Prüfe vorher mit `grep -n "record Text\|Text(" src/main/java/dev/devpanda/factorynetwork/lang/ast/Expr.java`, wie das Literal für Texte wirklich heißt, und setze den Test darauf. Steht dort ein anderer Name, ist der Test anzupassen — nicht der Code.

- [x] **Schritt 3: `TokenType` erweitern**

In der Aufzählung neben `WORKER, GROUP, MULTIBLOCK, EVENT, DISPLAY, ON, IMPORT`
das Wort `GLOBAL` ergänzen, und in der Schlüsselwortkarte:

```java
            Map.entry("global", GLOBAL),
```

Dazu die Stelle prüfen, an der `TokenType` entscheidet, was ein
Deklarationswort ist (dieselbe Datei, Zeile 97) — `GLOBAL` gehört dazu.

- [x] **Schritt 4: `Decl.Global` anlegen**

In `Decl.java`, bei den anderen Records:

```java
    // ---- Globaler Wert ----------------------------------------------------

    /**
     * Ein Wert, den alle Dateien sehen.
     *
     * <p><b>Die einzige Deklaration ohne Klammern.</b> Alle anderen sammeln
     * Angaben in einem Block; diese erklärt einen Wert, und dafür ist eine
     * Zeile die ehrlichere Form.
     *
     * <p>Kein {@code let} auf oberster Ebene: Ein Programm besteht nur aus
     * Deklarationen, es gibt kein Hauptprogramm, das beim Laden losläuft.
     * Ein {@code let} draußen sähe aus wie eine Anweisung, die niemand
     * ausführt.
     *
     * @param value der Anfangswert — ein Literal, siehe Aufgabe 3
     */
    record Global(String name, Expr value, Span span) implements Decl {}
```

- [x] **Schritt 5: Den Parser erweitern**

Im `switch` über den Tokentyp (Zeile 77-82) ergänzen:

```java
            case GLOBAL -> parseGlobal();
```

Und die Methode dazu, in der Nähe von `parseFn`:

```java
    /**
     * {@code global name = literal}
     *
     * <p>Ohne Block und damit ohne {@code expectBrace}: Die Zeile endet mit
     * dem Wert.
     */
    private Decl parseGlobal() {
        Token keyword = advance();
        Token name = expectName("global");
        expect(TokenType.ASSIGN, "Nach dem Namen kommt ein Gleichheitszeichen.");
        Expr value = expression();
        return new Decl.Global(name.text(), value, span(keyword, previous()));
    }
```

**Vor dem Schreiben verifizieren** (die Namen stammen aus dem Muster der
anderen Parse-Methoden, nicht aus dem Gedächtnis):

```bash
grep -n "private Token expectName\|private Token expect(\|private Span span(\|private Token previous()\|ASSIGN\|EQUALS" src/main/java/dev/devpanda/factorynetwork/lang/parse/Parser.java src/main/java/dev/devpanda/factorynetwork/lang/TokenType.java | head -20
```

Heißt das Zuweisungszeichen anders (etwa `EQ` oder `ASSIGN`), ist der Code
darauf zu setzen. Gibt es `expectName` nicht, nimm, was `parseWorker` für den
Namen benutzt.

- [x] **Schritt 6: Die Fehlerbehebung ergänzen**

In Zeile 961 steht die Liste der Wörter, an denen der Parser nach einem
Fehler wieder aufsetzt:

```java
                case WORKER, GROUP, MULTIBLOCK, EVENT, DISPLAY, FN, ON -> {
```

`GLOBAL` gehört dazu — sonst verschluckt ein Fehler in einer
`global`-Zeile die nächste Deklaration. Genau das prüft der letzte Test.

- [x] **Schritt 7: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*GlobalParseTest*"`
Erwartet: 5 Tests grün.

- [x] **Schritt 8: Committen**

```bash
git add -A
git commit -m "global erklärt einen Wert, den alle Dateien sehen"
```

---

## Aufgabe 2: Die versiegelten Schalter nachziehen

**Dateien:** alle, die der Übersetzer nach Aufgabe 1 nennt. Erwartet werden:
- `lang/Definitions.java`
- `lang/NetworkCheck.java`
- `lang/ProgramSize.java`
- `lang/Project.java`
- `runtime/flow/BlockIndex.java`
- `runtime/WorkerRuntime.java`
- `block/entity/ControllerBlockEntity.java`

**Schnittstellen:**
- Braucht: `Decl.Global` (Aufgabe 1).
- Liefert: ein übersetzbares Projekt, in dem jede Stelle weiß, was sie mit
  einem globalen Wert tut.

**Vorgehen:** `./gradlew build -x test` nennt jede unvollständige Stelle.
Für jede entscheiden — **nicht durchwinken**:

| Stelle | Was ein globaler Wert dort bedeutet |
|---|---|
| `Definitions.find/findAll` | Er **wird** hier erklärt — ein Sprung auf `modus` muss zur `global`-Zeile führen |
| `NetworkCheck` | Keine Prüfung: Ein globaler Wert ist kein Name aus dem Netz |
| `ProgramSize` | Zählt mit; wie viel, siehe unten |
| `Project` | Wie die anderen Deklarationen |
| `BlockIndex` | Kein Block, keine Anweisungen — nichts zu indizieren |
| `WorkerRuntime` | Kein Worker — übergehen |

- [x] **Schritt 1: Übersetzen und die Stellen sammeln**

Aufruf: `./gradlew build -x test`
Erwartet: Fehler wegen unvollständiger `switch`-Ausdrücke. Liste notieren.

- [x] **Schritt 2: Den Test für den Sprung schreiben**

Ans Ende von `src/test/java/dev/devpanda/factorynetwork/lang/DefinitionsTest.java`:

```java
    @Test
    @DisplayName("Ein globaler Wert wird an seiner global-Zeile erklärt")
    void aGlobalIsDeclaredAtItsLine() {
        Project project = new Project(java.util.Map.of("main.mf", """
                global modus = "tag"

                fn test() {
                    log(modus)
                }"""));

        var found = Definitions.find(project, "modus");

        assertTrue(found.isPresent(), "die Erklärung fehlt");
        assertEquals(1, found.get().line(),
                "sie steht in der ersten Zeile");
    }
```

Die Importe der Datei prüfen und ergänzen, was fehlt.

- [x] **Schritt 3: Jede Stelle abarbeiten**

`Definitions` braucht nichts, wenn es über `declaration.name()` läuft — dann
greift es von selbst, weil `Decl.Global` einen Namen hat. **Prüfen, nicht
annehmen:** Steht dort ein `switch` über die Arten, ist `Global` zu ergänzen;
läuft es über `flatten(...)`, ist zu prüfen, ob das die neue Art mitnimmt.

Für `ProgramSize`: Ein globaler Wert kostet so viel wie eine Zeile Code. Die
vorhandene Rechnung ansehen und einen Wert wählen, der zu ihr passt — nicht
null, weil sonst tausend globale Werte gratis wären.

- [x] **Schritt 4: Alles bauen und testen**

Aufruf: `./gradlew test`
Erwartet: alles grün, auch der neue Definitions-Test.

- [x] **Schritt 5: Committen**

```bash
git add -A
git commit -m "Jede Stelle weiß jetzt, was ein globaler Wert für sie bedeutet"
```

---

## Aufgabe 3: Der Anfangswert muss ein Literal sein

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/lang/GlobalCheck.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/Project.java` (dort,
  wo `NetworkCheck.run` aufgerufen wird)
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/GlobalCheckTest.java`

**Schnittstellen:**
- Braucht: `Decl.Global` (Aufgabe 1).
- Liefert: `GlobalCheck.run(Program) -> List<Diagnostic>`.

**Was geprüft wird, und warum nicht mehr:** Die Sprache hat **keinen
Typprüfer**. Geprüft wird deshalb nur, was ohne einen entscheidbar ist:

1. Der Anfangswert ist ein Literal — `global x = storage.count(...)` wäre eine
   Rechnung ohne festen Zeitpunkt.
2. Kein Name doppelt.
3. Eine Zuweisung eines Literals anderen Typs an einen globalen Wert, dessen
   Anfangswert ebenfalls ein Literal ist. Das ist der Vertipper-Fall.

Alles andere fällt zur Laufzeit auf, wie überall sonst in der Sprache.

- [x] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was sich an globalen Werten ohne Typprüfer sagen lässt.
 */
class GlobalCheckTest {

    private static List<Diagnostic> check(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics();
    }

    @Test
    @DisplayName("Eine Rechnung als Anfangswert wird gemeldet")
    void aCalculationAsInitialValueIsReported() {
        List<Diagnostic> problems = check("global x = storage.count(item:stone)");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("fester Wert")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Zwei gleiche Namen werden gemeldet")
    void twoGlobalsWithTheSameNameAreReported() {
        List<Diagnostic> problems = check("""
                global modus = "tag"
                global modus = "nacht\"""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Text, dem eine Zahl zugewiesen wird, ist ein Vertipper")
    void assigningANumberToATextIsReported() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = 3
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Aufruf als Zuweisung bleibt offen")
    void assigningACallIsNotJudged() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = berechnen()
                }

                fn berechnen() {
                    return "nacht"
                }""");

        assertTrue(problems.isEmpty(),
                () -> "ohne Typprüfer lässt sich hier nichts sagen: " + problems);
    }

    @Test
    @DisplayName("Ein richtiger Text ist keine Meldung wert")
    void assigningATextToATextIsFine() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = "nacht"
                }""");

        assertTrue(problems.isEmpty(), () -> problems.toString());
    }
}
```

- [x] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*GlobalCheckTest*"`

- [x] **Schritt 3: `GlobalCheck` schreiben**

Der Aufbau folgt `NetworkCheck`: eine Klasse mit privatem Konstruktor, eine
statische `run`, Meldungen als `Diagnostic.Severity.WARNING` bei Vertippern
und `ERROR`, wo die Sprache es nicht ausführen kann.

**Vor dem Schreiben nachsehen:**

```bash
grep -n "record\|sealed" src/main/java/dev/devpanda/factorynetwork/lang/ast/Expr.java | head -25
grep -n "record\|sealed" src/main/java/dev/devpanda/factorynetwork/lang/ast/Stmt.java | head -20
grep -n "Severity\|isError" src/main/java/dev/devpanda/factorynetwork/lang/Diagnostic.java
```

Damit stehen die echten Namen der Literale (`Expr.Text`, `Expr.Number`, oder
wie sie heißen) und der Zuweisung (`Stmt.Assign`) fest. **Der Plan nennt sie
absichtlich nicht** — sie sind aus dem Bestand zu nehmen.

Die Wanderung über die Anweisungen braucht einen Durchgang durch Funktionen
und Ereignisblöcke. Ob es dafür schon einen Helfer gibt:

```bash
grep -rn "Stmt.Block\|walk\|visit\|forEach" src/main/java/dev/devpanda/factorynetwork/lang/ | head -10
```

Gibt es keinen, ist ein kleiner rekursiver Durchgang in `GlobalCheck` die
richtige Stelle — nicht ein allgemeiner Besucher für die ganze Sprache.

- [x] **Schritt 4: In `Project.parse` einhängen**

Dort, wo `NetworkCheck.run(...)` aufgerufen wird, die Meldungen von
`GlobalCheck.run(program)` dazunehmen. **Auch in der Fassung ohne
`NetworkView`** — diese Prüfung braucht kein Netz.

- [x] **Schritt 5: Testen**

Aufruf: `./gradlew test`
Erwartet: alles grün.

- [x] **Schritt 6: Committen**

```bash
git add -A
git commit -m "Ein globaler Wert braucht einen festen Anfangswert"
```

---

## Aufgabe 4: Lesen und Schreiben zur Laufzeit

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/runtime/Interpreter.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/runtime/GlobalRuntimeTest.java`

**Schnittstellen:**
- Braucht: `Decl.Global` (Aufgabe 1).
- Liefert: `Interpreter.Host.global(String)` und
  `Interpreter.Host.setGlobal(String, Value)`, beide mit Standardfassung.

**Der Kern:** Globale Werte liegen **nicht** im Stapel der Geltungsbereiche —
der lebt nur für einen Aufruf. Sie liegen beim Host, wie Bestände und
Redstone auch.

- [x] **Schritt 1: Den fehlschlagenden Test schreiben**

Ein Test mit einer Welt aus Papier. **Vorher ansehen**, wie die vorhandenen
Laufzeittests ihren Host bauen:

```bash
ls src/test/java/dev/devpanda/factorynetwork/runtime/
grep -rn "new Interpreter\|implements Interpreter.Host" src/test/java/ | head -5
```

Der neue Test baut darauf auf und prüft:

1. `log(modus)` liest den Anfangswert.
2. Eine Funktion, die `modus = "nacht"` setzt, ändert ihn für den nächsten
   Aufruf.
3. Ein Name, den keine `global`-Zeile erklärt, bleibt ein Fehler
   („Unbekannter Name").
4. Ein globaler Wert wird von einem gleichnamigen `let` in einer Funktion
   verdeckt, und die Zuweisung trifft dann das `let` — nicht den globalen
   Wert.

Punkt 4 ist der, an dem sich die Umsetzung beweisen muss.

- [x] **Schritt 2: Den Host erweitern**

In `Interpreter.Host`:

```java
        /**
         * Ein globaler Wert, oder {@code null}.
         *
         * <p>Nicht im Stapel der Geltungsbereiche: Der lebt für einen
         * Aufruf, ein globaler Wert für die Fabrik. Er liegt deshalb dort,
         * wo auch Bestände und Redstone liegen — beim Host.
         */
        default Value global(String name) {
            return null;
        }

        /** Setzt einen globalen Wert. */
        default void setGlobal(String name, Value value) {
        }
```

- [x] **Schritt 3: `find` und `assign` erweitern**

In `find(String name)`: Findet der Stapel nichts, den Host fragen — **danach**
und nicht davor, damit ein `let` gleichen Namens vorgeht.

In `assign(Stmt.Assign)`: Findet die Schleife über die Geltungsbereiche
nichts, aber der Host kennt den Namen, dann `host.setGlobal(...)` statt der
Fehlermeldung. Kennt ihn auch der Host nicht, bleibt die Meldung
„Unbekannter Name" — sie ist richtig.

- [x] **Schritt 4: Testen**

Aufruf: `./gradlew test`

- [x] **Schritt 5: Committen**

```bash
git add -A
git commit -m "Der Interpreter liest und schreibt globale Werte über seinen Host"
```

---

## Aufgabe 5: Der Wert lebt im Controller

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/block/entity/ControllerBlockEntity.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/runtime/WorldHost.java`
- Test: `src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java`

**Schnittstellen:**
- Braucht: Aufgabe 4.
- Liefert: Globale Werte, die den Serverneustart überleben.

**Die Regel beim Programmwechsel** (aus dem Entwurf):

| Fall | Was passiert |
|---|---|
| Gleicher Name, gleicher Typ | Der Wert bleibt |
| Neuer Name | Anfangswert aus der Deklaration |
| Name weg | Vergessen |
| Gleicher Name, anderer Typ | Anfangswert aus der neuen Deklaration |

- [ ] **Schritt 1: Den GameTest schreiben**

Er baut ein Netz mit `buildSetup`, übernimmt ein Programm mit einem globalen
Wert, ändert ihn über einen Funktionsaufruf, speichert und lädt die
BlockEntity und prüft, dass der Wert überlebt hat.

**Vorher ansehen**, wie ein vorhandener Test ein Programm übernimmt und eine
Funktion aufruft:

```bash
grep -n "acceptDraft\|deploy\|callFunction\|runtime()" src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java | head -10
```

Und wie das Speichern und Laden geprüft wird:

```bash
grep -n "saveAdditional\|loadAdditional\|saveWithFullMetadata" src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java | head -5
```

- [ ] **Schritt 2: Die Werte im Controller halten**

Eine `Map<String, Value>` neben Programm und Entwurf, dazu:

- `globals()` für den Host
- Schreiben und Lesen in `saveAdditional`/`loadAdditional` über `ValueCodec`
- eine Methode, die beim Übernehmen eines Programms die Karte nach der Regel
  oben umstellt

Für den Typvergleich reicht ein Vergleich der `Value`-Art — `ValueCodec`
schreibt sie ohnehin als Zeichenkette (`KEY_TYPE`), und dieselbe
Unterscheidung genügt hier.

- [ ] **Schritt 3: `WorldHost` durchreichen**

`global` und `setGlobal` auf die Karte des Controllers legen. `setGlobal` muss
`setChanged()` auslösen, sonst geht der Wert beim nächsten Speichern verloren.

- [ ] **Schritt 4: GameTests laufen lassen**

Aufruf: `./gradlew runGameTestServer`

- [ ] **Schritt 5: Committen**

```bash
git add -A
git commit -m "Globale Werte überleben den Serverneustart"
```

---

## Aufgabe 6: Im Editor und im Terminal

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/Signatures.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/screen/Completions.java`
- Ändern: `src/test/java/dev/devpanda/factorynetwork/lang/SignaturesExportTest.java`
- Ändern: `editor/vscode/extension.js`, `editor/vscode/check.js`
- Test: `src/test/java/dev/devpanda/factorynetwork/client/screen/CompletionsTest.java`

**Schnittstellen:**
- Braucht: Aufgabe 1.

- [ ] **Schritt 1: `global` in die Vorschläge**

`DECLARATIONS` in `Completions` (Zeile 55) und die Liste in
`SignaturesExportTest.build()` kennen die Deklarationswörter. Beide brauchen
`global`.

Dazu eine Form in `Signatures`, damit die Hinweiszeile etwas sagt. Da
`forBlock(null)` heute nichts liefert, kommt ein neuer Fall dazu:

```java
    /**
     * Was auf oberster Ebene steht.
     *
     * <p>Bisher gab es hier nichts mit Form: Alle Deklarationen öffnen einen
     * Block, und die Frage „was kommt hinter dem Wort" stellte sich erst
     * darin. {@code global} ist die erste, die in einer Zeile fertig wird.
     */
    public static final List<Signature> TOP_LEVEL = List.of(
            of("global", "Ein Wert, den alle Dateien sehen.",
                    Slot.named(Kind.NEW_NAME, "name"), Slot.literal("="),
                    Slot.of(Kind.EXPR)));
```

und in `forBlock`:

```java
        if (declaration == null) {
            return TOP_LEVEL;
        }
```

**Achtung:** Das ändert das Verhalten für jede Stelle, die `forBlock(null)`
aufruft. Prüfen, wer das tut:

```bash
grep -rn "forBlock(" src/main/java/ | head
```

- [ ] **Schritt 2: Die Tabelle für VS Code**

`SignaturesExportTest` schreibt sie neu und schlägt fehl, bis sie eingecheckt
ist. `TOP_LEVEL` gehört in den Export, und `extension.js` muss die Formen auf
oberster Ebene anbieten. `check.js` bekommt einen Fall dafür.

- [ ] **Schritt 3: Die Werte im Netz-Reiter zeigen**

Ein Abschnitt mit Namen und Stand. Ohne ihn ist ein globaler Wert beim
Fehlersuchen unsichtbar, und der Umweg wäre `log()` in einer Schleife.

**Nur anzeigen, nicht ändern.** Ob der Reiter Schreibrechte bekommt, ist im
Entwurf offen und bleibt es.

Dafür braucht der Client die Werte: Sie gehören in denselben Netzzustand wie
die Workerstände. **Achtung — `NetworkStatePacket` hat sechs Felder, und
`StreamCodec.composite` trägt nicht mehr.** Ein siebtes braucht eine von Hand
geschriebene Fassung wie `AnalyserDataPacket.SUMMARY`. Das ist der Punkt, an
dem diese Aufgabe größer wird als sie aussieht — sie darf deshalb auch als
letzte stehen bleiben.

- [ ] **Schritt 4: Testen und committen**

```bash
./gradlew test && cd editor/vscode && node check.js && cd ../..
git add -A
git commit -m "Der Editor kennt global, und das Terminal zeigt die Werte"
```

---

## Aufgabe 7: Doku und Beispiele

- [ ] **Schritt 1: `sprache.md`** um einen Abschnitt zu globalen Werten
      ergänzen — sie sind Teil der Sprache und stehen dort noch nicht.
- [ ] **Schritt 2: `grammatik.md`** um `globalDecl = 'global' NAME '=' expr`
      ergänzen, bei den anderen Deklarationen.
- [ ] **Schritt 3: `beispiele.md`** um ein Programm ergänzen, das einen
      Modus schaltet. Der Test, der jedes Beispiel übersetzt, prüft es mit.
- [ ] **Schritt 4: `umsetzung.md`** nachziehen.
- [ ] **Schritt 5: Committen**

---

## Stopp-Kriterien

Diese Arbeit läuft ohne Rückfragen. Bei folgendem wird **nicht geraten**,
sondern der Punkt dokumentiert und das Thema zurückgestellt:

- Eine Entwurfslücke, die eine Entscheidung des Projektinhabers braucht — etwa
  die Frage nach Konstanten (`docs/globale-werte.md`, Abschnitt 8).
- Ein Test, der nach zwei Fixversuchen rot bleibt.
- Ein Umbau, der über den Entwurf hinausgeht — etwa ein Typprüfer.

Der Zustandsbericht am Morgen zählt mehr als der letzte Commit.
