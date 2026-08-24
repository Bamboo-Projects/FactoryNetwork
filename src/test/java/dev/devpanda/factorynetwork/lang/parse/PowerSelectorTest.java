package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.WorkerKind;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@code power} als vierte Ressourcenart.
 *
 * <p><b>Ohne Doppelpunkt</b>, anders als {@code item:} und {@code fluid:}:
 * Strom hat keine Sorten, es gibt nur FE. Ein {@code power:} mit leerem Rest
 * wäre eine Lüge über die Form.
 */
class PowerSelectorTest {

    private static Decl.Worker firstWorker(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return (Decl.Worker) result.program().declarations().get(0);
    }

    @Test
    @DisplayName("filter power macht einen Strom-Worker")
    void aPowerFilterMakesAPowerWorker() {
        Decl.Worker worker = firstWorker("""
                worker versorgung {
                    from network
                    to crusher_1
                    filter power
                    rate 40 per tick
                }""");

        assertEquals(Expr.Selector.Kind.POWER, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("power ist ein Auswahlausdruck wie die anderen")
    void powerIsASelectorLikeTheOthers() {
        Decl.Worker worker = firstWorker("""
                worker versorgung {
                    from network
                    to crusher_1
                    filter power
                }""");

        Expr filter = worker.entry(Decl.Worker.Entry.Kind.FILTER).value();
        Expr.Selector selector = assertInstanceOf(Expr.Selector.class, filter);
        assertEquals(Expr.Selector.Kind.POWER, selector.kind());
        assertEquals("", selector.path(), "Strom hat keine Sorte");
    }

    @Test
    @DisplayName("Ein Connector darf weiterhin power heißen")
    void aConnectorMayStillBeCalledPower() {
        // Mit Rückstrichen, wie bei jedem Schlüsselwort — dieselbe Regel wie
        // für einen Connector namens „for".
        Decl.Worker worker = firstWorker("""
                worker mahlen {
                    from `power`
                    to crusher_1
                    filter item:iron_ore
                }""");

        Expr from = worker.entry(Decl.Worker.Entry.Kind.FROM).value();
        Expr.Name name = assertInstanceOf(Expr.Name.class, from);
        assertEquals("power", name.value());
    }
}
