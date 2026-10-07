package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.WorkerKind;
import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The kinds a program can name — as far as the language is concerned.
 *
 * <p><b>The game-free view of the registry.</b> {@link ResourceKinds} is where
 * a kind registers, with its store and its access to machines. Each one that
 * does is also entered here, and the interpreter looks it up here: by the
 * word before the colon and by the name it carries on disk.
 *
 * <p><b>Nothing is defined here, and that is the point.</b> Even items and
 * fluids are defined where the game's registries are; this class only knows
 * that somebody will bring them. Who that is, it learns the first time it is
 * asked, see {@link Source}.
 */
public final class Kinds {

    /**
     * Whoever brings the built-in kinds.
     *
     * <p>Found by name and not by reference: a reference from here to the
     * place where items are defined would tie this class to the game again.
     * The mod names its source in {@code META-INF/services}. In the game the
     * kinds are registered long before anybody asks; the lookup matters where
     * nothing has run yet — in a test that starts with a program.
     */
    public interface Source {

        /** Makes sure the built-in kinds are registered. */
        void load();
    }

    /** Registration order — it decides nothing, but reads better. */
    private static final Map<String, Kind> BY_PREFIX = new LinkedHashMap<>();
    private static final Map<String, Kind> BY_TAG = new LinkedHashMap<>();

    private static boolean asked;

    private Kinds() {
    }

    /**
     * Enters a kind. Called by the registry and by nobody else.
     *
     * <p>That a prefix or a name on disk is taken only once is checked there;
     * here the last entry would simply win.
     */
    static synchronized void add(Kind kind) {
        BY_PREFIX.put(kind.prefix(), kind);
        BY_TAG.put(kind.tag(), kind);
        BY_TAG.put(kind.selectionTag(), kind);
    }

    private static synchronized void ensureLoaded() {
        if (asked) {
            return;
        }
        asked = true;
        for (Source source : ServiceLoader.load(Source.class, Kinds.class.getClassLoader())) {
            source.load();
        }
    }

    /** The kind for a prefix, or {@code null}. */
    public static synchronized Kind byPrefix(String prefix) {
        ensureLoaded();
        return BY_PREFIX.get(prefix);
    }

    /** The kind that writes this name to disk, for a resource or a selection; or {@code null}. */
    public static synchronized Kind byTag(String tag) {
        ensureLoaded();
        return BY_TAG.get(tag);
    }

    /** Item kinds. A {@code tag:} resolves to this. */
    public static Kind item() {
        return builtIn("item");
    }

    /** Fluid types. A {@code fluidtag:} resolves to this. */
    public static Kind fluid() {
        return builtIn("fluid");
    }

    /** Chemicals. */
    public static Kind chemical() {
        return builtIn("chemical");
    }

    private static Kind builtIn(String prefix) {
        Kind kind = byPrefix(prefix);
        if (kind == null) {
            throw new IllegalStateException("Die eingebaute Art „" + prefix + "“ ist nicht "
                    + "angemeldet. Ohne sie lässt sich kein Programm ausführen.");
        }
        return kind;
    }

    /**
     * The kind behind a written form, or {@code null}.
     *
     * <p>{@code null} means "no resource kind" and not "items": {@code power}
     * and {@code all} carry no type, and whoever read them as items would
     * move the wrong thing for {@code power}.
     */
    public static Kind of(Expr.Selector.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> item();
            case FLUID, FLUIDTAG -> fluid();
            case CHEMICAL -> chemical();
            // A registered third-party kind cannot be determined from the
            // written form alone — which one it is stands in the prefix.
            // Whoever needs it uses of(Expr.Selector) rather than this one.
            case CUSTOM, POWER, ALL -> null;
        };
    }

    /**
     * The kind behind a selection expression, or {@code null}.
     *
     * <p>The version that also covers third-party kinds: for them the answer
     * stands in the prefix and not in the enum.
     */
    public static Kind of(Expr.Selector selector) {
        if (selector == null) {
            return null;
        }
        return selector.kind() == Expr.Selector.Kind.CUSTOM
                ? byPrefix(selector.prefix())
                : of(selector.kind());
    }

    /** The kind an expression selects, through amount and exception; or {@code null}. */
    public static Kind ofExpression(Expr expr) {
        return of(WorkerKind.selectorOf(expr));
    }

    /** The same question for a selection that is still present as text. */
    public static Kind of(Value.Request.Kind written) {
        if (written == null) {
            return null;
        }
        return switch (written) {
            case ITEM, TAG -> item();
            case FLUID, FLUIDTAG -> fluid();
            case CHEMICAL -> chemical();
            case ALL, UNKNOWN -> null;
        };
    }

    /**
     * The same question where an answer is required and {@code null} would
     * not be one.
     *
     * <p>Without a kind, <b>items</b> are meant: {@code all} says so
     * explicitly, a worker without a filter has always done so, and a written
     * form nobody knows is caught during resolution, not here.
     */
    public static Kind orItems(Value.Request.Kind written) {
        Kind kind = of(written);
        return kind == null ? item() : kind;
    }

    /** The kind a value stands for, or {@code null}. */
    public static Kind of(Value value) {
        return switch (value) {
            case Value.Resource resource -> resource.kind();
            case Value.Selection selection -> selection.kind();
            case Value.Request request -> of(request.kind());
            case null, default -> null;
        };
    }
}
