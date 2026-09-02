package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What stands behind the name of a filter template.
 *
 * <p><b>First everything together, then the exclusions removed.</b> Not
 * alternating line by line: that way the order of the lines does not matter,
 * and whoever reads the template need not track an intermediate state.
 *
 * <p>Resolution goes exclusively through {@link ItemSelection} and
 * {@link FluidSelection} — the same place as for every written selection,
 * including its cache. A second version alongside would drift apart.
 */
public final class FilterTemplates {

    private FilterTemplates() {
    }

    /** The templates of a program, by name. */
    public static Map<String, Decl.FilterTemplate> of(Program program) {
        Map<String, Decl.FilterTemplate> found = new LinkedHashMap<>();
        for (Decl declaration : program.declarations()) {
            if (declaration instanceof Decl.FilterTemplate template) {
                found.put(template.name(), template);
            }
        }
        return found;
    }

    /** The item kinds of a template. */
    public static List<Item> items(Decl.FilterTemplate template) {
        Set<Item> found = new LinkedHashSet<>();
        for (Expr entry : template.includes()) {
            found.addAll(ItemSelection.resolve(entry));
        }
        for (Expr entry : template.excludes()) {
            ItemSelection.resolve(entry).forEach(found::remove);
        }
        if (found.isEmpty()) {
            throw empty(template);
        }
        return List.copyOf(found);
    }

    /** The fluid types of a template. */
    public static List<Fluid> fluids(Decl.FilterTemplate template) {
        Set<Fluid> found = new LinkedHashSet<>();
        for (Expr entry : template.includes()) {
            found.addAll(FluidSelection.resolve(entry));
        }
        for (Expr entry : template.excludes()) {
            FluidSelection.resolve(entry).forEach(found::remove);
        }
        if (found.isEmpty()) {
            throw empty(template);
        }
        return List.copyOf(found);
    }

    /**
     * The message names the template.
     *
     * <p>Without it, the message would read "the selection matches nothing" —
     * and the player would look for it at the place where they wrote the
     * name, rather than in the template.
     */
    private static ScriptError empty(Decl.FilterTemplate template) {
        return new ScriptError("Die Vorlage " + template.name() + " trifft nichts.",
                "Gibt es die Gegenstände in diesem Pack? Und nimmt eine except-Zeile "
                        + "vielleicht alles wieder heraus?");
    }
}
