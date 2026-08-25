package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Woran ein Worker seine Ressourcenart trägt.
 *
 * <p>Nicht an einem eigenen Feld, sondern am Auswahlausdruck seines Filters.
 * Die Laufzeit liest sie so, und die Prüfung im Editor muss dieselbe Regel
 * lesen — sonst warnt der eine, wo der andere schweigt.
 */
class WorkerKindTest {

    private static Decl.Worker firstWorker(String source) {
        return (Decl.Worker) Parser.parse(source).program().declarations().get(0);
    }

    @Test
    @DisplayName("Ein Filter auf Gegenstände macht einen Gegenstands-Worker")
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
    @DisplayName("Ein Filter auf Flüssigkeiten macht einen Flüssigkeits-Worker")
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
    @DisplayName("Eine Menge davor ändert die Art nicht")
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
    @DisplayName("Ohne Filter ist die Art unbekannt")
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
    @DisplayName("Ein Flüssigkeits-Tag macht einen Flüssigkeits-Worker")
    void aFluidTagMakesAFluidWorker() {
        Decl.Worker worker = firstWorker("""
                worker w {
                    from bottich
                    to kessel
                    filter fluidtag:c/molten
                }""");

        // Die Art ist die Ressource und nicht die Schreibweise: Wer auf einen
        // Flüssigkeits-Tag filtert, bewegt Flüssigkeiten.
        assertEquals(Expr.Selector.Kind.FLUID, WorkerKind.of(worker));
    }
}
