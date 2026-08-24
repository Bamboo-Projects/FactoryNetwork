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
        assertInstanceOf(Expr.StringLit.class, global.value());
    }

    @Test
    @DisplayName("Eine Zahl als Anfangswert")
    void aNumberInitialValue() {
        Parser.ParseResult result = Parser.parse("global vorrat = 0");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        Decl.Global global = assertInstanceOf(Decl.Global.class,
                result.program().declarations().get(0));
        assertEquals("vorrat", global.name());
        assertInstanceOf(Expr.IntLit.class, global.value());
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
