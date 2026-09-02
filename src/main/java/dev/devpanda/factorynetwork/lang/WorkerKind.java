package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.Map;

/**
 * What a worker moves: items, fluids, or chemicals.
 *
 * <p>A worker does not carry its kind as a separate entry, but on the selector
 * expression of its filter — {@code filter item:iron_ore} versus
 * {@code filter fluid:water}. This lives here and not in the runtime, because
 * the check in the editor needs the same rule and should not have to drag
 * Minecraft along.
 */
public final class WorkerKind {

    private WorkerKind() {
    }

    /**
     * The kind of this worker, or {@code null}.
     *
     * <p>{@code null} means "unknown" and not "items": without a filter nothing
     * can be said, and a guessed kind leads to a warning that is wrong.
     */
    public static Expr.Selector.Kind of(Decl.Worker worker) {
        return of(worker, Map.of());
    }

    /**
     * The same question, when the project knows filter templates.
     *
     * <p><b>Without the templates a {@code filter kuehlmittel} would stay
     * undetermined</b>, and a worker for fluids would run into the item path:
     * there its selector would match nothing, and it would sit on IDLE forever.
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
                // Mixed and empty are errors that FilterCheck reports. Here
                // both mean "unknown" — nothing is guessed.
                case MIXED, EMPTY -> null;
            };
        }
        return resource(selectorKind(filter.value()));
    }

    /**
     * The resource behind a spelling.
     *
     * <p>A fluid tag moves fluids. A worker's kind asks about what it moves,
     * not about how it is written — otherwise every place that picks the
     * execution path would have to know both forms.
     */
    public static Expr.Selector.Kind resource(Expr.Selector.Kind written) {
        return written == Expr.Selector.Kind.FLUIDTAG ? Expr.Selector.Kind.FLUID : written;
    }

    /**
     * The kind of a selector expression, through amount and exception.
     *
     * <p>{@code 64 item:iron_ore} and {@code tag:c/ores except item:x} do not
     * carry their kind at the root.
     */
    public static Expr.Selector.Kind selectorKind(Expr expr) {
        Expr.Selector selector = selectorOf(expr);
        return selector == null ? null : selector.kind();
    }

    /**
     * The selector expression itself, through amount and exception.
     *
     * <p>Needed since foreign kinds exist: their spelling is {@code CUSTOM},
     * and which kind is meant is in the prefix. Anyone who has only the enum
     * can no longer tell them apart.
     */
    public static Expr.Selector selectorOf(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> selector;
            case Expr.Amount amount -> selectorOf(amount.selection());
            case Expr.Except except -> selectorOf(except.base());
            case null, default -> null;
        };
    }
}
