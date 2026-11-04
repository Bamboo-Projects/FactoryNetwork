package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChartParseTest {

    private static Decl.Display.Entry first(String source) {
        var result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> "Fehler: " + result.diagnostics());
        Decl.Display display = assertInstanceOf(Decl.Display.class, result.program().declarations().get(0));
        return display.entries().get(0);
    }

    @Test
    @DisplayName("chart reads label, value and the interval after per")
    void chartWithInterval() {
        var entry = first("display wand {\n    chart \"Eisen\" storage.count(item:iron_ingot) per 30s\n}\n");
        assertEquals(Decl.Display.Entry.Kind.CHART, entry.kind());
        assertEquals("Eisen", entry.label());
        assertEquals(600L, assertInstanceOf(Expr.DurationLit.class, entry.every()).ticks());
    }

    @Test
    @DisplayName("chart without per keeps the default interval")
    void chartWithoutInterval() {
        var entry = first("display wand {\n    chart \"Eisen\" storage.count(item:iron_ingot)\n}\n");
        assertEquals(Decl.Display.Entry.Kind.CHART, entry.kind());
        assertNull(entry.every());
    }
}
