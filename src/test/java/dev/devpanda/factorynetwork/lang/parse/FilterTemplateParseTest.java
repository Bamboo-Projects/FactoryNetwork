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
 * {@code filter} steht an zwei Orten und meint zweierlei.
 *
 * <p>Auf oberster Ebene erklärt es eine Vorlage, im Worker nennt es eine
 * Auswahl. Der Parser unterscheidet nach Ort, nicht nach einem zweiten Wort —
 * deshalb prüft dieser Test beides nebeneinander.
 */
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
        Parser.ParseResult result = Parser.parse("""
                filter ore_factory {
                    tag:c/ores
                    except item:ancient_debris
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.FilterTemplate template = assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
        assertEquals(1, template.includes().size());
        assertEquals(1, template.excludes().size());
        // Der Ausschluss steht für sich und ist kein Expr.Except: Das Wort
        // gehört hier zur Zeile und nicht zur Auswahl darin.
        assertInstanceOf(Expr.Selector.class, template.excludes().get(0));
    }

    @Test
    @DisplayName("Eine Zeile darf selbst ein except enthalten")
    void aLineMayCarryItsOwnExcept() {
        Parser.ParseResult result = Parser.parse("""
                filter ore_factory {
                    tag:c/ores except item:ancient_debris
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.FilterTemplate template = assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
        // Ein Eintrag, und zwar ein Expr.Except. Das ist die bestehende
        // Auswahl-Grammatik und keine Sonderregel der Vorlage.
        assertEquals(1, template.includes().size());
        assertTrue(template.excludes().isEmpty());
        assertInstanceOf(Expr.Except.class, template.includes().get(0));
    }

    @Test
    @DisplayName("Ein Worker nimmt weiter eine filter-Angabe")
    void theWorkerEntryStillParses() {
        Parser.ParseResult result = Parser.parse("""
                filter ore_factory {
                    tag:c/ores
                }

                worker erz_holen {
                    from grube
                    to storage
                    filter ore_factory
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(2, result.program().declarations().size());
        Decl.Worker worker = assertInstanceOf(Decl.Worker.class,
                result.program().declarations().get(1));
        assertInstanceOf(Expr.Name.class,
                worker.entry(Decl.Worker.Entry.Kind.FILTER).value());
    }

    @Test
    @DisplayName("Ohne Namen ist es ein Fehler")
    void withoutANameItIsAnError() {
        Parser.ParseResult result = Parser.parse("""
                filter {
                    tag:c/ores
                }""");

        assertTrue(result.hasErrors(), "eine Vorlage ohne Namen ist nicht ansprechbar");
    }

    @Test
    @DisplayName("Ein Fehler im Block hält die nächste Deklaration nicht auf")
    void anErrorDoesNotStopTheNextDeclaration() {
        Parser.ParseResult result = Parser.parse("""
                filter kaputt {
                    ,
                }

                worker erz_holen {
                    from grube
                    to storage
                }""");

        assertTrue(result.hasErrors());
        assertTrue(result.program().declarations().stream()
                        .anyMatch(declaration -> declaration instanceof Decl.Worker),
                "der Worker dahinter muss trotzdem gelesen werden");
    }
}
