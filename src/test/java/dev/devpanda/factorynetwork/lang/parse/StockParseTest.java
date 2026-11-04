package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockParseTest {

    @Test
    @DisplayName("stock reads an amount and a selection")
    void stockReadsAmountAndSelection() {
        var result = Parser.parse("stock 512 item:torch\n");
        assertFalse(result.hasErrors(), () -> "Fehler: " + result.diagnostics());
        Decl.Stock stock = assertInstanceOf(Decl.Stock.class, result.program().declarations().get(0));
        Expr.Amount amount = assertInstanceOf(Expr.Amount.class, stock.amount());
        assertEquals(512L, amount.count());
    }

    @Test
    @DisplayName("stock without an amount is an error with an example")
    void stockWithoutAmountIsAnError() {
        var result = Parser.parse("stock item:torch\n");
        assertTrue(result.hasErrors());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.hint() != null && d.hint().contains("stock 512")),
                () -> "Hinweis fehlt: " + result.diagnostics());
    }
}
