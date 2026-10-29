package dev.devpanda.factorynetwork.platform;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

/**
 * The recipes of the running game, in the questions this mod asks.
 *
 * <p>Since 1.21.2 the recipe manager lives on the server only, a recipe no
 * longer hands out its result directly, and an ingredient lists holders
 * instead of stacks. The mod's planners ask here and stay as they were.
 */
public final class Recipes {

    private Recipes() {
    }

    /** All recipes of a type; empty on a client, which no longer knows them. */
    public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> all(
            Level level, RecipeType<T> type) {
        var server = level.getServer();
        return server == null ? List.of() : server.getRecipeManager().recipeMap().byType(type);
    }

    /**
     * What a recipe yields, or empty if it cannot say.
     *
     * <p>Read from the recipe's display, the way the recipe book reads it. A
     * recipe of this mod that shows no display answers through its own
     * {@code resultItem}; see the callers.
     */
    public static ItemStack result(Recipe<?> recipe, Level level) {
        var context = SlotDisplayContext.fromLevel(level);
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(context);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The ingredients, one per occupied slot, as the recipe places them. */
    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        return recipe.placementInfo().ingredients();
    }

    /** The items an ingredient accepts. */
    public static List<Item> items(Ingredient ingredient) {
        return ingredient.items().map(holder -> holder.value()).toList();
    }
}
