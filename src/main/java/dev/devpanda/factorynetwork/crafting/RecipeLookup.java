package dev.devpanda.factorynetwork.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Finds the recipes for an item and says what they cost.
 *
 * <p><b>A dedicated class and not three lines in the BlockEntity:</b> recipe
 * lookup is the place where a crafting goes wrong without anyone noticing — a
 * different recipe, a different ingredient, the same output. This way it can
 * be tested in a real world without placing a block.
 *
 * <p><b>What works without a machine.</b> Crafting table and stonecutter: both
 * are handwork, both are in the server, both are done in one go — and that is
 * why both run at the fabricator, without a single pattern item. What needs a
 * machine and time is in {@link MachineRecipes}.
 *
 * <p>The stonecutter was there first, and that was wrong: its recipe is
 * readable, but the block has no BlockEntity — no one can push into it.
 * Readable and executable are two different questions.
 *
 * <p><b>An index, not a search.</b> A pack's recipe list has a five-digit
 * length, and the planner queries it for every node of a recipe tree. So it is
 * ordered once by result and then looked up. The index lives exactly one tick:
 * invalidating it by hand after a {@code /reload} would mean relying on
 * internals that are no guarantee.
 */
public final class RecipeLookup implements CraftingPlanner.Recipes<Item> {

    private final Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult;

    private RecipeLookup(Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult) {
        this.byResult = byResult;
    }

    /** The index of what the fabricator can do. */
    public static RecipeLookup of(Level level) {
        HolderLookup.Provider registries = level.registryAccess();
        Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(registries);
            List<CraftingPlanner.Need<Item>> needs = needsOf(holder.value());
            if (result.isEmpty() || needs == null || needs.isEmpty()) {
                continue;
            }
            byResult.computeIfAbsent(result.getItem(), item -> new ArrayList<>())
                    .add(new CraftingPlanner.Recipe<>(result.getItem(),
                            result.getCount(), needs));
        }
        for (RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            ItemStack result = holder.value().getResultItem(registries);
            List<CraftingPlanner.Need<Item>> needs = needsOf(holder.value());
            if (result.isEmpty() || needs == null || needs.isEmpty()) {
                continue;
            }
            byResult.computeIfAbsent(result.getItem(), item -> new ArrayList<>())
                    .add(new CraftingPlanner.Recipe<>(result.getItem(),
                            result.getCount(), needs));
        }
        return new RecipeLookup(byResult);
    }

    /**
     * The first recipe whose ingredients the stock provides.
     *
     * <p><b>The stock has a say</b>, and that is the whole trick here: for an
     * item there are often several recipes, and one of them fits what is on
     * hand. Whoever took just the first would report "spruce wood is missing"
     * while a stack of oak planks sits in the drive.
     *
     * <p>If none is found whose ingredients are fully present, the first one at
     * all comes back — then the job says what is missing instead of "no
     * recipe".
     *
     * @param available how much of a kind is available; when planning this is
     *                  not the storage but the state of the planning
     */
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

    /** Whether there is any recipe at all for this item. */
    public boolean known(Item target) {
        return find(target, item -> 0L) != null;
    }

    /**
     * The ingredients of a recipe.
     *
     * <p>An ingredient remains a <b>choice</b> — "any plank" — and is passed on
     * exactly that way. Committing to one type here was the mistake of the
     * first version: it took the option there was the most of, and if there was
     * none of any, the first. Whoever had only spruce logs got "8 oak planks
     * are missing".
     *
     * @return {@code null} if an ingredient allows nothing at all
     */
    private static List<CraftingPlanner.Need<Item>> needsOf(
            net.minecraft.world.item.crafting.Recipe<?> recipe) {
        // Identical choices belong together: eight planks are one need for
        // eight and not eight needs for one.
        Map<List<Item>, Integer> merged = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<Item> options = new ArrayList<>();
            for (ItemStack candidate : ingredient.getItems()) {
                if (!candidate.isEmpty() && !options.contains(candidate.getItem())) {
                    options.add(candidate.getItem());
                }
            }
            if (options.isEmpty()) {
                return null;
            }
            merged.merge(List.copyOf(options), 1, Integer::sum);
        }
        List<CraftingPlanner.Need<Item>> needs = new ArrayList<>();
        merged.forEach((options, count) ->
                needs.add(new CraftingPlanner.Need<>(options, count)));
        return needs;
    }

    /**
     * What is missing, as a readable line.
     *
     * <p>Empty when nothing is missing. A job that stands still must name the
     * reason — otherwise one looks for it in the network instead of in the
     * stock. The base material is named, not the intermediate stage: "8 planks
     * are missing" helps no one who can make planks.
     */
    public static String missing(Map<Item, Long> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        missing.forEach((item, amount) ->
                lines.add(amount + " " + item.getDescription().getString()));
        return "es fehlt: " + String.join(", ", lines);
    }
}
