package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * What a selection currently resolves to.
 *
 * <p><b>The display that {@code sprache.md} promises in two places.</b> For
 * {@code except} it says "the editor shows for every pattern what it
 * currently matches", and for {@code maintain} there is no way to foresee
 * what one has committed to without it: {@code maintain 64 tag:c/ores} keeps
 * sixty-four of <i>every</i> kind, and only the pack knows how many that is.
 *
 * <p>A pattern is a search, and a search without a hit list is a promise made
 * blind.
 *
 * <p>Lives here and not in the editor because it needs the registry and no
 * screen — that way it can be verified in a real world.
 */
public final class SelectionSummary {

    /** More names would cover half the screen. */
    private static final int MAX_NAMES = 6;

    private SelectionSummary() {
    }

    /**
     * A few lines about what this selection matches.
     *
     * <p>The first names the count, the following ones the names. If it
     * matches nothing, that is exactly what is shown — the most common cause
     * is a tag this pack does not know, and in the editor it looks like any
     * other.
     */
    public static List<String> of(Expr.Selector selector) {
        List<String> lines = new ArrayList<>();
        if (selector == null) {
            return lines;
        }
        if (selector.kind() == Expr.Selector.Kind.CHEMICAL) {
            if (!dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()) {
                // No full stop at the end: the editor's box shows a piece of
                // information, not a sentence.
                lines.add("Chemikalien brauchen Mekanism");
                return lines;
            }
            List<String> ids = dev.devpanda.factorynetwork.compat.mekanism.Chemicals
                    .resolve(selector);
            return summarise(lines, ids.stream()
                    .map(dev.devpanda.factorynetwork.compat.mekanism.Chemicals::nameOf)
                    .toList());
        }
        boolean fluids = selector.kind() == Expr.Selector.Kind.FLUID
                || selector.kind() == Expr.Selector.Kind.FLUIDTAG;
        List<String> names = new ArrayList<>();
        if (fluids) {
            for (Fluid fluid : FluidSelection.resolve(selector)) {
                names.add(fluid.getFluidType().getDescription().getString());
            }
        } else {
            for (Item item : ItemSelection.resolve(selector)) {
                names.add(item.getDescription().getString());
            }
        }
        return summarise(lines, names);
    }

    /**
     * The count and the first few names.
     *
     * <p>In one place, because there are now three kinds of selection and all
     * of them should give the same information — a count, a few names, and
     * counted rather than cut off.
     */
    private static List<String> summarise(List<String> lines, List<String> names) {
        if (names.isEmpty()) {
            lines.add("trifft nichts");
            return lines;
        }
        lines.add("trifft " + names.size() + (names.size() == 1 ? " Art" : " Arten"));
        for (int i = 0; i < Math.min(names.size(), MAX_NAMES); i++) {
            lines.add(names.get(i));
        }
        if (names.size() > MAX_NAMES) {
            lines.add("… und " + (names.size() - MAX_NAMES) + " weitere");
        }
        return lines;
    }
}
