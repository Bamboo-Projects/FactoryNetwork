package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

import java.util.ArrayList;
import java.util.List;

/**
 * What a filter template is about.
 *
 * <p><b>Items or fluids, never both.</b> {@code move} sends water and stones
 * over different routes; a template that contained both would be something
 * different at every place it is used. Mixed is therefore an error and not a
 * silent choice — it is reported in {@link FilterCheck}, here stands only the
 * finding.
 *
 * <p>Pure tree inspection, without the registry: <b>which</b> items
 * {@code tag:c/ores} matches only the world knows — that they are items is
 * already in the program. That is why this lives here and not in
 * {@code runtime}.
 */
public enum FilterKind {

    /** Items. A {@code tag:} counts among them. */
    ITEM,

    /** Fluids. */
    FLUID,

    /** Both in one template — an error. */
    MIXED,

    /** No line that includes anything. */
    EMPTY;

    /**
     * The sort of a template.
     *
     * <p>Without an including line it is {@link #EMPTY}, no matter what stands
     * in the exceptions: there is nothing to subtract from. Otherwise <b>all</b>
     * lines have a say, the exceptions too — otherwise the sort would depend on
     * which line something stands in, and an exception that does not fit the
     * template at all would go unnoticed.
     */
    public static FilterKind of(Decl.FilterTemplate template) {
        if (template.includes().isEmpty()) {
            return EMPTY;
        }
        boolean items = false;
        boolean fluids = false;
        for (Expr entry : entries(template)) {
            for (Expr.Selector selector : selectorsOf(entry)) {
                switch (selector.kind()) {
                    case ITEM, TAG -> items = true;
                    case FLUID, FLUIDTAG -> fluids = true;
                    // Chemicals, power, and foreign kinds say nothing about
                    // item or fluid. That they have no business here is
                    // reported by FilterCheck.
                    case CHEMICAL, POWER, CUSTOM -> { }
                }
            }
        }
        if (items && fluids) {
            return MIXED;
        }
        if (fluids) {
            return FLUID;
        }
        return ITEM;
    }

    /** All lines of a template, exceptions counted in. */
    public static List<Expr> entries(Decl.FilterTemplate template) {
        List<Expr> all = new ArrayList<>(template.includes());
        all.addAll(template.excludes());
        return all;
    }

    /**
     * The selectors in a line — through {@code except} and amounts.
     *
     * <p>What does not get through here is not a selector: a name (that is, the
     * attempt to put one template inside another), a computation, a text.
     * {@link FilterCheck} recognizes that by a line yielding not a single
     * selector.
     */
    public static List<Expr.Selector> selectorsOf(Expr entry) {
        List<Expr.Selector> found = new ArrayList<>();
        collect(entry, found);
        return found;
    }

    private static void collect(Expr entry, List<Expr.Selector> found) {
        switch (entry) {
            case Expr.Selector selector -> found.add(selector);
            case Expr.Amount amount -> collect(amount.selection(), found);
            case Expr.Except except -> {
                collect(except.base(), found);
                except.exclusions().forEach(exclusion -> collect(exclusion, found));
            }
            case null, default -> { }
        }
    }
}
