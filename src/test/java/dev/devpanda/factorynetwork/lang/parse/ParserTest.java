package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    private static Program parseClean(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        return result.program();
    }

    private static List<Diagnostic> warningsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(diagnostic -> !diagnostic.isError()).toList();
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("Anweisungen ohne Wirkung")
    class OhneWirkung {

        @Test
        @DisplayName("log ohne Klammern warnt zweimal")
        void bareCallWarns() {
            // Ein Name und eine Zeichenkette nebeneinander: zwei
            // Anweisungen, beide ohne Wirkung. Genau die Falle, wegen der es
            // die Warnung gibt — das Programm lief und schrieb nichts.
            List<Diagnostic> warnungen = warningsOf("""
                    fn sagt() {
                        log "hallo"
                    }""");
            assertEquals(2, warnungen.size(), () -> warnungen.toString());
            assertTrue(warnungen.get(0).message().contains("bewirkt nichts"));
            assertTrue(warnungen.get(0).hint().contains("Klammern"));
        }

        @Test
        @DisplayName("Mit Klammern ist alles still")
        void properCallIsQuiet() {
            assertTrue(warningsOf("""
                    fn sagt() {
                        log("hallo")
                    }""").isEmpty());
        }

        @Test
        @DisplayName("Ein Feldzugriff kann Wirkung haben und wird nicht angemeckert")
        void memberAccessIsQuiet() {
            assertTrue(warningsOf("""
                    fn zaehlt() {
                        storage.count(item:iron_ingot)
                    }""").isEmpty());
        }

        @Test
        @DisplayName("Eine Warnung hält das Programm nicht auf")
        void warningIsNotAnError() {
            Parser.ParseResult result = Parser.parse("""
                    fn sagt() {
                        log "hallo"
                    }""");
            assertFalse(result.hasErrors(), "eine Warnung ist kein Fehler");
        }
    }

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream().filter(Diagnostic::isError).toList();
    }

    @Nested
    @DisplayName("Worker")
    class Workers {

        @Test
        void completeWorker() {
            Program program = parseClean("""
                    worker fuel_supply {
                        from storage
                        to generators
                        filter tag:c/coals
                        maintain 64
                        strategy round_robin
                    }""");
            assertEquals(1, program.workers().size());
            Decl.Worker worker = program.workers().get(0);
            assertEquals("fuel_supply", worker.name());
            assertNotNull(worker.entry(Decl.Worker.Entry.Kind.FROM));
            assertNotNull(worker.entry(Decl.Worker.Entry.Kind.MAINTAIN));
        }

        @Test
        @DisplayName("from crafting macht Vorratshaltung zum selben Worker")
        void craftingIsASource() {
            Decl.Worker worker = parseClean("""
                    worker keep_ingots {
                        from crafting
                        to storage
                        filter item:iron_ingot
                        maintain 256
                    }""").workers().get(0);
            Expr from = worker.entry(Decl.Worker.Entry.Kind.FROM).value();
            Expr.Builtin builtin = assertInstanceOf(Expr.Builtin.class, from);
            assertEquals(Expr.Builtin.Kind.CRAFTING, builtin.kind());
        }

        @Test
        void rateHasTwoParts() {
            Decl.Worker worker = parseClean("""
                    worker feed {
                        from storage
                        to crusher_1
                        rate 32 per 8t
                    }""").workers().get(0);
            Decl.Worker.Entry rate = worker.entry(Decl.Worker.Entry.Kind.RATE);
            assertInstanceOf(Expr.IntLit.class, rate.value());
            Expr.DurationLit interval = assertInstanceOf(Expr.DurationLit.class, rate.second());
            assertEquals(8, interval.ticks());
        }

        @Test
        @DisplayName("Eine unbekannte Angabe nennt die erlaubten")
        void unknownEntryListsTheAllowedOnes() {
            List<Diagnostic> errors = errorsOf("""
                    worker feed {
                        from storage
                        to crusher_1
                        speed 5
                    }""");
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).hint().contains("maintain"));
        }
    }

    @Nested
    @DisplayName("Meldungen, die weiterhelfen")
    class HelpfulErrors {

        @Test
        @DisplayName("Ein Schlüsselwort als Connector schlägt Rückstriche vor")
        void keywordAsConnectorSuggestsBackticks() {
            List<Diagnostic> errors = errorsOf("""
                    fn test() {
                        for.insert(64 item:iron_ore)
                    }""");
            assertFalse(errors.isEmpty());
            Diagnostic first = errors.get(0);
            assertTrue(first.message().contains("Schlüsselwort"), first::message);
            assertTrue(first.hint().contains("Rückstriche"), first::hint);
        }

        @Test
        @DisplayName("Ein Connector in Rückstrichen wird gelesen")
        void backtickedKeywordWorks() {
            Program program = parseClean("""
                    fn test() {
                        `for`.insert(64 item:iron_ore)
                    }""");
            assertEquals(1, program.functions().size());
        }

        @Test
        @DisplayName("timeout ohne else wird erklärt, nicht nur gemeldet")
        void timeoutWithoutElse() {
            List<Diagnostic> errors = errorsOf("""
                    fn test() {
                        let r = await BatchFinished timeout 30s
                    }""");
            assertFalse(errors.isEmpty());
            Diagnostic first = errors.get(0);
            assertTrue(first.message().contains("else"), first::message);
            assertTrue(first.hint().contains("nie gab"), first::hint);
        }

        @Test
        void timeoutWithElseIsFine() {
            parseClean("""
                    fn test() {
                        let r = await BatchFinished where id == jobId timeout 30s else {
                            return
                        }
                    }""");
        }

        @Test
        @DisplayName("Eine Zuweisung ist kein Vergleich")
        void assignmentIsNotAComparison() {
            // Dieser Fall fiel erst beim Interpreter auf: Die Meldung zum
            // einzelnen = schlug bei jeder normalen Zuweisung zu.
            parseClean("""
                    fn test() {
                        let summe = 0
                        summe = summe + 1
                    }""");
        }

        @Test
        @DisplayName("Ein einzelnes = beim Vergleich wird erkannt")
        void singleEqualsInComparison() {
            List<Diagnostic> errors = errorsOf("""
                    fn test() {
                        if a = 1 {
                        }
                    }""");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).message().contains("zwei Gleichheitszeichen"));
        }

        @Test
        @DisplayName("Eine Zeitangabe, die nicht in Ticks aufgeht, nennt die nächste")
        void durationThatDoesNotFit() {
            List<Diagnostic> errors = errorsOf("""
                    fn test() {
                        sleep 0.01s
                    }""");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).hint().contains("0t"), () -> errors.get(0).hint());
        }

        @Test
        @DisplayName("Bei on werden keine Typen geschrieben")
        void typesOnHandlerAreRejected() {
            List<Diagnostic> errors = errorsOf("""
                    on redstone_changed(sensor: Device, strength: Int) {
                    }""");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).hint().contains("Deklaration des Ereignisses"));
        }

        @Test
        @DisplayName("import sagt, dass es das noch nicht gibt")
        void importIsReserved() {
            List<Diagnostic> errors = errorsOf("import lib/smelting");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).hint().contains("Namensraum"));
        }

        @Test
        @DisplayName("Nach einem Fehler wird weitergelesen")
        void recoveryFindsLaterDeclarations() {
            Parser.ParseResult result = Parser.parse("""
                    worker broken {
                        nonsense here
                    }

                    fn good() {
                        return 1
                    }""");
            assertTrue(result.hasErrors());
            assertEquals(1, result.program().functions().size(),
                    "Die Funktion nach dem Fehler muss trotzdem im Baum stehen");
        }
    }

    @Nested
    @DisplayName("Auswahl von Gegenständen")
    class Selections {

        @Test
        void namespaceIsSplitOff() {
            Expr.Selector selector = firstSelector(
                    "worker w { from storage \n to chest \n filter item:allthemodium/allthemodium_ingot }");
            assertEquals("allthemodium", selector.namespace());
            assertEquals("allthemodium_ingot", selector.path());
        }

        @Test
        void withoutNamespaceItStaysEmpty() {
            Expr.Selector selector = firstSelector(
                    "worker w { from storage \n to chest \n filter item:iron_ingot }");
            assertNull(selector.namespace());
            assertEquals("iron_ingot", selector.path());
            assertFalse(selector.hasPattern());
        }

        @Test
        void tagKeepsItsNamespace() {
            Expr.Selector selector = firstSelector(
                    "worker w { from storage \n to chest \n filter tag:c/ores }");
            assertEquals(Expr.Selector.Kind.TAG, selector.kind());
            assertEquals("c", selector.namespace());
            assertEquals("ores", selector.path());
        }

        @Test
        @DisplayName("Ein Muster ohne Namensraum bleibt ohne")
        void patternWithoutNamespace() {
            Expr.Selector selector = firstSelector(
                    "worker w { from storage \n to chest \n filter item:*_dust }");
            assertNull(selector.namespace());
            assertTrue(selector.hasPattern());
        }

        @Test
        void exceptIsPartOfTheSelection() {
            Decl.Worker worker = parseClean(
                    "worker w { from storage \n to chest \n filter tag:c/ores except item:ancient_debris }")
                    .workers().get(0);
            Expr filter = worker.entry(Decl.Worker.Entry.Kind.FILTER).value();
            Expr.Except except = assertInstanceOf(Expr.Except.class, filter);
            assertEquals(1, except.exclusions().size());
        }

        private static Expr.Selector firstSelector(String source) {
            Decl.Worker worker = parseClean(source).workers().get(0);
            Expr filter = worker.entry(Decl.Worker.Entry.Kind.FILTER).value();
            return assertInstanceOf(Expr.Selector.class, filter);
        }
    }

    @Nested
    @DisplayName("Weitere Deklarationsformen")
    class OtherDeclarations {

        @Test
        void groupWithPattern() {
            Program program = parseClean("""
                    group furnaces {
                        members furnace_*
                        strategy least_filled
                    }""");
            Decl.Group group = (Decl.Group) program.declarations().get(0);
            assertEquals("least_filled", group.strategy());
            assertInstanceOf(Expr.NamePattern.class, group.members().get(0));
        }

        @Test
        void multiblockHasDevicesAndFunctions() {
            Program program = parseClean("""
                    multiblock OrePlant {
                        devices {
                            crusher
                            furnace
                        }

                        fn process(ore: Item) {
                            move ore to crusher
                        }
                    }""");
            Decl.Multiblock plant = (Decl.Multiblock) program.declarations().get(0);
            assertEquals(List.of("crusher", "furnace"), plant.devices());
            assertEquals(1, plant.functions().size());
        }

        @Test
        void displayEntries() {
            Program program = parseClean("""
                    display factory_status {
                        title "Fabrik"
                        row "Eisen" storage.count(item:iron_ingot)
                        indicator "Reaktor" reactor.online
                    }""");
            Decl.Display display = (Decl.Display) program.declarations().get(0);
            assertEquals(3, display.entries().size());
            assertEquals(Decl.Display.Entry.Kind.TITLE, display.entries().get(0).kind());
            assertEquals("Fabrik", display.entries().get(0).label());
        }

        @Test
        void webViewReadsAllFour() {
            Program program = parseClean("""
                    webview lager {
                        url  "https://example.org/lager"
                        at   112.5 68 -340.5
                        face south
                        size 4 x 3
                    }""");
            Decl.WebView view = (Decl.WebView) program.declarations().get(0);
            assertEquals("lager", view.name());
            assertEquals("https://example.org/lager", view.url());
            assertEquals(112.5, view.at().x());
            assertEquals(68.0, view.at().y());
            assertEquals(-340.5, view.at().z(), "ein Minus gehoert zur Zahl");
            assertEquals(Decl.WebView.Facing.SOUTH, view.face());
            assertEquals(4, view.widthBlocks());
            assertEquals(3, view.heightBlocks());
        }

        @Test
        void webViewTakesItsPartsInAnyOrder() {
            // Vier Eigenschaften, kein Aufbau: Anders als bei einem Display
            // traegt die Reihenfolge hier keine Bedeutung.
            Program program = parseClean("""
                    webview lager {
                        size 2 2
                        face up
                        at 0 70 0
                        url "https://example.org"
                    }""");
            Decl.WebView view = (Decl.WebView) program.declarations().get(0);
            assertEquals(Decl.WebView.Facing.UP, view.face());
            assertEquals(2, view.widthBlocks());
        }

        @Test
        void webViewDefaultsToOneBlockFacingNorth() {
            Program program = parseClean("""
                    webview klein {
                        url "https://example.org"
                        at 8 64 8
                    }""");
            Decl.WebView view = (Decl.WebView) program.declarations().get(0);
            assertEquals(1, view.widthBlocks());
            assertEquals(1, view.heightBlocks());
            assertEquals(Decl.WebView.Facing.NORTH, view.face());
        }

        @Test
        void webViewWithoutAddressComplains() {
            List<Diagnostic> errors = errorsOf("""
                    webview lager {
                        at 8 64 8
                    }""");
            assertTrue(errors.stream().anyMatch(d -> d.message().contains("fehlt die Adresse")),
                    errors.toString());
        }

        @Test
        void webViewWithoutPlaceComplains() {
            List<Diagnostic> errors = errorsOf("""
                    webview lager {
                        url "https://example.org"
                    }""");
            assertTrue(errors.stream().anyMatch(d -> d.message().contains("fehlt der Ort")),
                    errors.toString());
        }

        @Test
        void webViewRejectsAnUnknownDirection() {
            List<Diagnostic> errors = errorsOf("""
                    webview lager {
                        url "https://example.org"
                        at 8 64 8
                        face vorne
                    }""");
            assertTrue(errors.stream().anyMatch(
                            d -> d.message().contains("ist keine Blickrichtung")),
                    errors.toString());
        }

        @Test
        void webViewRejectsAnythingElseInside() {
            // Der Fehler nennt, was erlaubt ist. Wer raten muss, welche vier
            // Woerter gehen, sucht sie im Quelltext.
            List<Diagnostic> errors = errorsOf("""
                    webview lager {
                        url "https://example.org"
                        at 8 64 8
                        title "Lager"
                    }""");
            assertTrue(errors.stream().anyMatch(
                            d -> d.message().contains("gehoert nicht in eine webview")
                                    || d.message().contains("nicht in eine webview")),
                    errors.toString());
        }

        @Test
        void displayScale() {
            Program program = parseClean("""
                    display halle {
                        scale 4
                        title "ERZLAGER"
                    }""");
            Decl.Display display = (Decl.Display) program.declarations().get(0);
            assertEquals(Decl.Display.Entry.Kind.SCALE, display.entries().get(0).kind());
            assertEquals(4, ((Expr.IntLit) display.entries().get(0).value()).value());
        }

        @Test
        void displayScaleWantsAnumber() {
            // Eine Zahl und kein Ausdruck: Die Größe der Schrift ist Aufbau
            // und nicht Inhalt. Ein Maßstab, der sich beim Zusehen ändert,
            // wäre eine Spielerei, für die die Wand jedes Mal neu umbricht.
            assertTrue(Parser.parse("""
                    display halle {
                        scale storage.count(item:coal)
                    }""").hasErrors(), "ein Ausdruck darf nicht durchgehen");
        }

        @Test
        void listLiteral() {
            Program program = parseClean("""
                    fn zeigen() {
                        let namen = ["a", "b"]
                    }""");
            Stmt.Let let = (Stmt.Let) ((Decl.Fn) program.declarations().get(0))
                    .body().statements().get(0);
            Expr.ListLit list = assertInstanceOf(Expr.ListLit.class, let.value());
            assertEquals(2, list.entries().size());
        }

        @Test
        void anemptyListLiteral() {
            // Der häufigste Anfangswert einer globalen Liste: noch nichts
            // drin. Ohne ihn müsste man eine Liste mit einem Platzhalter
            // beginnen und den gleich wieder herausnehmen.
            Program program = parseClean("global warteschlange = []");
            Decl.Global global = (Decl.Global) program.declarations().get(0);
            assertEquals(0, assertInstanceOf(Expr.ListLit.class, global.value())
                    .entries().size());
        }

        @Test
        void amultilineListLiteral() {
            Program program = parseClean("""
                    global sorten = [
                        "eisen",
                        "gold"
                    ]""");
            Decl.Global global = (Decl.Global) program.declarations().get(0);
            assertEquals(2, ((Expr.ListLit) global.value()).entries().size());
        }

        @Test
        void anunclosedListIsAnerror() {
            assertTrue(Parser.parse("""
                    fn zeigen() {
                        let namen = ["a", "b"
                    }""").hasErrors(), "die fehlende Klammer muss auffallen");
        }

        @Test
        @DisplayName("Eine Zahl vor einem Namen ist eine Menge, nicht nur vor einer Auswahl")
        void anumberBeforeAnameIsAnamount() {
            // `move 64 erze` ging längst, `send(64 erze)` nicht — dieselbe
            // Form an zwei Stellen mit zwei Antworten. Aufgefallen ist es an
            // einem Beispiel in der Doku, das genau das versprach.
            Program program = parseClean("""
                    fn nachschub() {
                        brecher.send(64 erze)
                    }""");
            Stmt.ExprStmt statement = (Stmt.ExprStmt) ((Decl.Fn) program.declarations().get(0))
                    .body().statements().get(0);
            Expr.Call call = assertInstanceOf(Expr.Call.class, statement.expr());
            Expr.Amount amount = assertInstanceOf(Expr.Amount.class,
                    call.arguments().get(0).value());
            assertEquals(64, amount.count());
        }

        @Test
        @DisplayName("Ein Rezept nennt seine Maschine, seine Zutaten und sein Ergebnis")
        void arecipeNamesItsMachineInputsAndOutput() {
            Program program = parseClean("""
                    recipe erz_mahlen at brecher {
                        in 1 item:iron_ore
                        out 2 item:iron_dust
                    }""");
            Decl.Recipe recipe = (Decl.Recipe) program.declarations().get(0);
            assertEquals("erz_mahlen", recipe.name());
            assertEquals("brecher", recipe.device());
            assertEquals(1, recipe.inputs().size());
            assertEquals(2, recipe.outputs().get(0).amount());
        }

        @Test
        @DisplayName("Ein Rezept darf mehrere Zutaten haben")
        void arecipeMayHaveSeveralInputs() {
            Program program = parseClean("""
                    recipe legierung at mischer {
                        in 3 item:copper_ingot
                        in 1 item:tin_ingot
                        out 4 item:bronze_ingot
                    }""");
            Decl.Recipe recipe = (Decl.Recipe) program.declarations().get(0);
            assertEquals(2, recipe.inputs().size());
        }

        @Test
        @DisplayName("Ohne at fehlt die Maschine")
        void withoutAtThemachineIsMissing() {
            List<Diagnostic> errors = errorsOf("""
                    recipe erz_mahlen {
                        in 1 item:iron_ore
                        out 2 item:iron_dust
                    }""");
            assertFalse(errors.isEmpty(), "das at muss verlangt werden");
            assertTrue(errors.get(0).message().contains("at")
                            || errors.get(0).hint().contains("at"),
                    () -> errors.toString());
        }

        @Test
        @DisplayName("Ein Rezept ohne Ergebnis ist keines")
        void arecipeWithoutAnoutputIsNone() {
            assertFalse(errorsOf("""
                    recipe leer at brecher {
                        in 1 item:iron_ore
                    }""").isEmpty(), "ohne out wäre nicht zu sagen, was entsteht");
        }

        @Test
        void eventWithTypedParameters() {
            Program program = parseClean("event OreBatchReady(item: Item, amount: Int)");
            Decl.Event event = (Decl.Event) program.declarations().get(0);
            assertEquals(2, event.parameters().size());
            assertEquals("Item", event.parameters().get(0).type());
        }

        @Test
        void moveWithAmountAndSource() {
            Program program = parseClean("""
                    fn test() {
                        move 64 item:iron_ore from chest to crusher_1
                    }""");
            Stmt statement = program.functions().get(0).body().statements().get(0);
            Stmt.Move move = assertInstanceOf(Stmt.Move.class, statement);
            Expr.Amount amount = assertInstanceOf(Expr.Amount.class, move.amount());
            assertEquals(64L, amount.count());
            assertNotNull(move.from());
        }

        @Test
        @DisplayName("Listenoperationen mit implizitem it")
        void implicitIt() {
            parseClean("""
                    fn test() {
                        let busy = crushers.members().where(it.busy).count()
                    }""");
        }
    }
}
