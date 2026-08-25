package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.Map;

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
        return of(worker, Map.of());
    }

    /**
     * Dieselbe Frage, wenn das Projekt Filter-Vorlagen kennt.
     *
     * <p><b>Ohne die Vorlagen bliebe ein {@code filter kuehlmittel}
     * unbestimmt</b>, und ein Worker für Flüssigkeiten liefe in den
     * Gegenstandspfad: Dort träfe seine Auswahl nichts, und er stünde für
     * immer auf IDLE.
     */
    public static Expr.Selector.Kind of(Decl.Worker worker,
            Map<String, Decl.FilterTemplate> templates) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        if (filter == null) {
            return null;
        }
        if (filter.value() instanceof Expr.Name name) {
            Decl.FilterTemplate template = templates.get(name.value());
            return template == null ? null : switch (FilterKind.of(template)) {
                case ITEM -> Expr.Selector.Kind.ITEM;
                case FLUID -> Expr.Selector.Kind.FLUID;
                // Gemischt und leer sind Fehler, die FilterCheck meldet. Hier
                // heißt beides „unbekannt" — geraten wird nicht.
                case MIXED, EMPTY -> null;
            };
        }
        return selectorKind(filter.value());
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
