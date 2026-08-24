package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

/**
 * Was ein Worker bewegt: Gegenstände, Flüssigkeiten oder Chemikalien.
 *
 * <p>Ein Worker trägt seine Art nicht als eigene Angabe, sondern am
 * Auswahlausdruck seines Filters — {@code filter item:iron_ore} gegen
 * {@code filter fluid:water}. Das steht hier und nicht in der Laufzeit,
 * weil die Prüfung im Editor dieselbe Regel braucht und kein Minecraft
 * mitziehen soll.
 */
public final class WorkerKind {

    private WorkerKind() {
    }

    /**
     * Die Art dieses Workers, oder {@code null}.
     *
     * <p>{@code null} heißt „unbekannt" und nicht „Gegenstände": Ohne Filter
     * lässt sich nichts sagen, und eine geratene Art führt zu einer Warnung,
     * die falsch ist.
     */
    public static Expr.Selector.Kind of(Decl.Worker worker) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        return filter == null ? null : selectorKind(filter.value());
    }

    /**
     * Die Art eines Auswahlausdrucks, durch Menge und Ausnahme hindurch.
     *
     * <p>{@code 64 item:iron_ore} und {@code tag:c/ores except item:x} tragen
     * ihre Art nicht an der Wurzel.
     */
    public static Expr.Selector.Kind selectorKind(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> selector.kind();
            case Expr.Amount amount -> selectorKind(amount.selection());
            case Expr.Except except -> selectorKind(except.base());
            case null, default -> null;
        };
    }
}
