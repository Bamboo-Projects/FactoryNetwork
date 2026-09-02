package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where a worker carries its resource kind.
 *
 * <p>Not on a field of its own, but on the selector expression of its filter.
 * The runtime reads it that way, and the check in the editor must read the
 * same rule — otherwise one warns where the other stays silent.
 */
class WorkerKindTest {

    private static Decl.Worker firstWorker(String source) {
        return (Decl.Worker) Parser.parse(source).program().declarations().get(0);
    }

    @Test
    @DisplayName("A filter on items makes an item worker")
    void anItemFilterMakesAnItemWorker() {
        Decl.Worker worker = firstWorker("""
                worker mahlen {
                    from chest
                    to crusher_1
                    filter item:iron_ore
                }""");

        assertEquals(Expr.Selector.Kind.ITEM, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("A filter on fluids makes a fluid worker")
    void aFluidFilterMakesAFluidWorker() {
        Decl.Worker worker = firstWorker("""
                worker pumpen {
                    from tank_1
                    to boiler
                    filter fluid:water
                }""");

        assertEquals(Expr.Selector.Kind.FLUID, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("An amount in front does not change the kind")
    void anAmountInFrontKeepsTheKind() {
        Decl.Worker worker = firstWorker("""
                worker mahlen {
                    from chest
                    to crusher_1
                    filter 64 item:iron_ore
                }""");

        assertEquals(Expr.Selector.Kind.ITEM, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("Without a filter the kind is unknown")
    void withoutAFilterTheKindIsUnknown() {
        Decl.Worker worker = firstWorker("""
                worker schieben {
                    from chest
                    to crusher_1
                }""");

        assertNull(WorkerKind.of(worker),
                "ohne Filter darf nichts geraten werden");
    }

    @Test
    @DisplayName("A fluid tag makes a fluid worker")
    void aFluidTagMakesAFluidWorker() {
        Decl.Worker worker = firstWorker("""
                worker w {
                    from bottich
                    to kessel
                    filter fluidtag:c/molten
                }""");

        // The kind is the resource and not the spelling: whoever filters on a
        // fluid tag moves fluids.
        assertEquals(Expr.Selector.Kind.FLUID, WorkerKind.of(worker));
    }
}
