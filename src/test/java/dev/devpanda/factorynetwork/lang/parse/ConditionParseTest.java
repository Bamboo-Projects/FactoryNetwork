package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selections narrowed by data components: enchanted, damaged, potion, named, component. */
class ConditionParseTest {

    private static Expr filterOf(String filter) {
        Parser.ParseResult result = Parser.parse(
                "worker w {\n    from a\n    to b\n    filter " + filter + "\n}");
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        Decl.Worker worker = (Decl.Worker) result.program().declarations().get(0);
        return worker.entry(Decl.Worker.Entry.Kind.FILTER).value();
    }

    @Test
    @DisplayName("enchanted alone and with an id")
    void enchanted() {
        Expr.Condition any = assertInstanceOf(Expr.Condition.class, filterOf("item:diamond_sword enchanted"));
        assertEquals(Expr.Condition.Kind.ENCHANTED, any.kind());
        assertNull(any.id());
        Expr.Condition mending = assertInstanceOf(Expr.Condition.class,
                filterOf("item:enchanted_book enchanted:mending"));
        assertEquals("mending", mending.id());
        assertInstanceOf(Expr.Selector.class, mending.base());
    }

    @Test
    @DisplayName("damaged with a bound in percent")
    void damaged() {
        Expr.Condition bound = assertInstanceOf(Expr.Condition.class, filterOf("tag:c/tools damaged > 50%"));
        assertEquals(Expr.Condition.Kind.DAMAGED, bound.kind());
        assertEquals(50.0, bound.percent());
        assertTrue(bound.above());
        Expr.Condition below = assertInstanceOf(Expr.Condition.class, filterOf("tag:c/tools damaged < 10%"));
        assertFalse(below.above());
    }

    @Test
    @DisplayName("potion, named and component; conditions chain")
    void others() {
        assertEquals("healing", ((Expr.Condition) filterOf("item:potion potion:healing")).id());
        assertEquals("Erzkiste", ((Expr.Condition) filterOf("item:* named \"Erzkiste\"")).text());
        assertEquals("mekanism/upgrade", ((Expr.Condition) filterOf("item:* component:mekanism/upgrade")).id());
        Expr.Condition outer = assertInstanceOf(Expr.Condition.class, filterOf("item:* enchanted named"));
        assertEquals(Expr.Condition.Kind.NAMED, outer.kind());
        assertInstanceOf(Expr.Condition.class, outer.base());
    }

    @Test
    @DisplayName("A condition before except, and none after it")
    void exceptStaysPlain() {
        Expr.Except except = assertInstanceOf(Expr.Except.class,
                filterOf("item:* enchanted except item:book"));
        assertInstanceOf(Expr.Condition.class, except.base());
        Parser.ParseResult wrong = Parser.parse(
                "worker w {\n    from a\n    to b\n    filter item:* except item:book enchanted\n}");
        assertTrue(wrong.hasErrors(), "eine Bedingung hinter except müsste gemeldet werden");
    }

    @Test
    @DisplayName("A device called like a condition word still works as a device")
    void namesStayNames() {
        assertInstanceOf(Expr.Name.class, filterOf("named"));
    }
}
