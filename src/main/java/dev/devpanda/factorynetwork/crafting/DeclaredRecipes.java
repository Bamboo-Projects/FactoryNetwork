package dev.devpanda.factorynetwork.crafting;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.runtime.ItemSelection;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * The recipes declared in the program.
 *
 * <p>What a foreign machine can do is not readable generically — the evidence
 * is in {@code entscheidungen.md}, „Processing-Rezepte". So the player writes
 * it down, and does so where everything in this mod lives: in the program.
 *
 * <pre>
 * recipe erz_mahlen at brecher {
 *     in 1 item:iron_ore
 *     out 2 item:iron_dust
 * }
 * </pre>
 *
 * <p><b>No pattern item.</b> The fabricator builds without one, and the reason
 * carries over here: a pattern terminal would be the second place where a
 * factory is described — the first is the program. A declaration instead
 * travels with the file to VS Code, can be versioned, and a recipe at a device
 * that does not exist reports itself when applied.
 *
 * <p><b>Fluids and chemicals are filled in, not planned.</b> The planner works
 * with items; procuring them too would mean needing a resource kind that is
 * open rather than fixed — a decision of its own. Until then the job fetches
 * them from the network storage when starting, and if too little is there, it
 * waits and says which kind is missing.
 *
 * <p><b>The first {@code out} counts.</b> A recipe may name several results —
 * a crusher yields dust and sometimes a by-product — but planning targets the
 * first. The others still land in the network when the executor collects; they
 * are a bonus and not a target.
 */
public final class DeclaredRecipes implements CraftingPlanner.Recipes<Item> {

    /** How the executor recognizes that a recipe comes from the program. */
    public static final String PREFIX = "at:";

    /** What separates the device from the recipe name within a station. */
    private static final char SEPARATOR = '#';

    /**
     * The station of a recipe at this device.
     *
     * <p>The recipe's name is included in it, separated by a hash. It is
     * needed to find the <b>extras</b> again: two recipes at the same device
     * with the same output could otherwise not be told apart, and then the
     * machine would get the other one's water.
     */
    public static String stationFor(String device, String recipe) {
        return PREFIX + device + "#" + recipe;
    }

    /** The device behind a station, or {@code null}. */
    public static String deviceOf(String station) {
        if (!station.startsWith(PREFIX)) {
            return null;
        }
        String rest = station.substring(PREFIX.length());
        int hash = rest.indexOf(SEPARATOR);
        return hash < 0 ? rest : rest.substring(0, hash);
    }

    /**
     * And the recipe's name, or empty.
     *
     * <p>Empty means: a station from a world that was saved before the extras.
     * The job carries on — it just has nothing to fill in.
     */
    public static String recipeOf(String station) {
        int hash = station.indexOf(SEPARATOR);
        return hash < 0 ? "" : station.substring(hash + 1);
    }

    /**
     * An ingredient that is not an item.
     *
     * <p><b>It is filled in, not planned.</b> The planner works with items;
     * procuring a fluid as an ingredient too would mean it had to know recipes
     * about fluids — and for that a resource kind that is open rather than
     * fixed would be needed. That is a decision of its own (see
     * {@code offene-punkte.md}).
     *
     * <p>Until then: what stands here the job fetches from the network storage
     * when starting and fills it into the machine — just like the items
     * alongside, only without the recursion behind it. If too little is there,
     * it waits and says which kind is missing.
     *
     * @param fluid whether it is a fluid; otherwise a chemical
     */
    public record Extra(boolean fluid, long amount, Expr selection) {

        /**
         * How much is needed for that many runs.
         *
         * <p>The items go into the machine all at once for all runs; the water
         * has to grow with them. Otherwise there would stand a machine with
         * four ores and one bucket.
         */
        public long needFor(long runs) {
            return runs <= 0 ? 0 : amount * runs;
        }
    }

    private final Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult;

    /** The extras per station — fluids and chemicals. */
    private final Map<String, List<Extra>> extras;

    private DeclaredRecipes(Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult,
                            Map<String, List<Extra>> extras) {
        this.byResult = byResult;
        this.extras = extras;
    }

    /** What this station needs besides items. */
    public List<Extra> extrasOf(String station) {
        return extras.getOrDefault(station, List.of());
    }

    /** What the program declares as recipes. */
    public static DeclaredRecipes of(Program program) {
        Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult = new HashMap<>();
        Map<String, List<Extra>> extras = new HashMap<>();
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Recipe recipe) || recipe.outputs().isEmpty()) {
                continue;
            }
            List<Item> results = ItemSelection.resolve(recipe.outputs().get(0).selection());
            List<CraftingPlanner.Need<Item>> needs = needsOf(recipe);
            String station = stationFor(recipe.device(), recipe.name());
            if (results.isEmpty() || needs == null) {
                continue;
            }
            extras.put(station, extrasOf(recipe));
            // If the output matches several types, the recipe applies to each.
            // Whoever writes „out 2 tag:c/dusts" has written down a choice, and
            // which of them comes out only the machine knows.
            int perCraft = (int) recipe.outputs().get(0).amount();
            for (Item result : results) {
                byResult.computeIfAbsent(result, item -> new ArrayList<>())
                        .add(new CraftingPlanner.Recipe<>(result, perCraft, needs, station));
            }
        }
        return new DeclaredRecipes(byResult, Map.copyOf(extras));
    }

    /**
     * The ingredients that are not items.
     *
     * <p>Recognized by the kind of selection, not by matching nothing: an
     * {@code item:gibtsnicht} is a typo and not a fluid.
     */
    private static List<Extra> extrasOf(Decl.Recipe recipe) {
        List<Extra> found = new ArrayList<>();
        for (Decl.Recipe.Part part : recipe.inputs()) {
            Expr.Selector.Kind kind = kindOf(part);
            if (kind == Expr.Selector.Kind.FLUID || kind == Expr.Selector.Kind.FLUIDTAG) {
                found.add(new Extra(true, part.amount(), part.selection()));
            } else if (kind == Expr.Selector.Kind.CHEMICAL) {
                found.add(new Extra(false, part.amount(), part.selection()));
            }
        }
        return List.copyOf(found);
    }

    private static Expr.Selector.Kind kindOf(Decl.Recipe.Part part) {
        return part.selection() instanceof Expr.Selector selector ? selector.kind() : null;
    }

    /**
     * The ingredients of a declared recipe.
     *
     * <p>A choice remains a choice, as everywhere: {@code in 8
     * tag:c/plates} means eight of any plates, and which ones the planner
     * picks by stock.
     *
     * @return {@code null} if an ingredient matches nothing at all
     */
    private static List<CraftingPlanner.Need<Item>> needsOf(Decl.Recipe recipe) {
        List<CraftingPlanner.Need<Item>> needs = new ArrayList<>();
        for (Decl.Recipe.Part part : recipe.inputs()) {
            // Fluids and chemicals go the other way: they are filled in, not
            // planned.
            Expr.Selector.Kind kind = kindOf(part);
            if (kind != Expr.Selector.Kind.ITEM && kind != Expr.Selector.Kind.TAG) {
                continue;
            }
            List<Item> options = ItemSelection.resolve(part.selection());
            if (options.isEmpty()) {
                return null;
            }
            needs.add(new CraftingPlanner.Need<>(options, (int) part.amount()));
        }
        return needs.isEmpty() ? null : needs;
    }

    @Override
    public CraftingPlanner.Recipe<Item> find(Item target, ToLongFunction<Item> available) {
        CraftingPlanner.Recipe<Item> first = null;
        for (CraftingPlanner.Recipe<Item> recipe : byResult.getOrDefault(target, List.of())) {
            if (first == null) {
                first = recipe;
            }
            if (CraftingPlanner.covers(recipe, available)) {
                return recipe;
            }
        }
        return first;
    }
}
