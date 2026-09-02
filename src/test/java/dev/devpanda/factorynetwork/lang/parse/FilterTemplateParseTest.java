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
 * {@code filter} appears in two places and means two things.
 *
 * <p>At the top level it declares a template, in a worker it names a
 * selection. The parser distinguishes by place, not by a second word — which
 * is why this test checks both side by side.
 */
class FilterTemplateParseTest {

    @Test
    @DisplayName("Lines without except are includes")
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
    @DisplayName("except takes away")
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
        // The exclusion stands on its own and is no Expr.Except: the word
        // belongs to the line here and not to the selection within it.
        assertInstanceOf(Expr.Selector.class, template.excludes().get(0));
    }

    @Test
    @DisplayName("A line may itself contain an except")
    void aLineMayCarryItsOwnExcept() {
        Parser.ParseResult result = Parser.parse("""
                filter ore_factory {
                    tag:c/ores except item:ancient_debris
                }""");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.FilterTemplate template = assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
        // One entry, namely an Expr.Except. That is the existing selection
        // grammar and no special rule of the template.
        assertEquals(1, template.includes().size());
        assertTrue(template.excludes().isEmpty());
        assertInstanceOf(Expr.Except.class, template.includes().get(0));
    }

    @Test
    @DisplayName("A worker still takes a filter entry")
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
    @DisplayName("Without a name it is an error")
    void withoutANameItIsAnError() {
        Parser.ParseResult result = Parser.parse("""
                filter {
                    tag:c/ores
                }""");

        assertTrue(result.hasErrors(), "eine Vorlage ohne Namen ist nicht ansprechbar");
    }

    @Test
    @DisplayName("An error in the block does not stop the next declaration")
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
