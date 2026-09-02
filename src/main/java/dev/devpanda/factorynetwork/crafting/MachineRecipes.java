package dev.devpanda.factorynetwork.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * The recipes that a machine runs — and that the network can read.
 *
 * <p><b>Not all of them.</b> A machine recipe from a foreign mod is not
 * readable generically: {@code Recipe.getIngredients()} returns an empty list
 * by default, an {@code Ingredient} carries no amount, fluids and power are
 * not in it, and {@code getResultItem} knows exactly one output. The
 * reasoning with evidence is in {@code entscheidungen.md},
 * „Processing-Rezepte: die Erkundung".
 *
 * <p>What stands here are the types with a <b>fixed, known shape</b>: one
 * ingredient, one output, one duration. That is the furnace family and the
 * mod's own press — for them the four holes are walled off, because the shape
 * is known. Everything else the player writes as {@code recipe} into the
 * program.
 *
 * <p><b>The station is the recipe type, not the device.</b> Which furnace is
 * needed the executor decides only at execution — and it takes one that is
 * currently free. If a device name were already here, a job would wait in
 * front of a busy furnace while two empty ones stand beside it.
 */
public final class MachineRecipes implements CraftingPlanner.Recipes<Item> {

    /**
     * What the executor must know at a station.
     *
     * @param inputSlot  where the ingredient goes
     * @param outputSlot where the result comes from
     * @param supply     what the player must contribute, for the message
     */
    public record Station(int inputSlot, int outputSlot, String supply) {
    }

    /** Furnace, blast furnace, smoker: ingredient in 0, fuel in 1, result in 2. */
    private static final Station FURNACE = new Station(0, 2, "Brennstoff");

    /** The mod's own press: stamp in 0, material in 1, result in 2. */
    private static final Station PRESS =
            new Station(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL,
                    dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT,
                    "einen Stempel");

    /** The ids under which the stations appear in the plan. */
    public static final String SMELTING = "minecraft:smelting";
    public static final String BLASTING = "minecraft:blasting";
    public static final String SMOKING = "minecraft:smoking";
    public static final String PRESSING = "factorynetwork:press";

    private static final Map<String, Station> STATIONS = Map.of(
            SMELTING, FURNACE, BLASTING, FURNACE, SMOKING, FURNACE, PRESSING, PRESS);

    /** What the executor must know about this station, or {@code null}. */
    public static Station stationOf(String name) {
        return STATIONS.get(name);
    }

    /**
     * Does this machine fit this station?
     *
     * <p><b>By the class and not the name.</b> A furnace is named differently
     * on an English server than in a German client; its BlockEntity is the
     * same everywhere. And a blast furnace is not a subset of a furnace,
     * although both inherit from {@code AbstractFurnaceBlockEntity} — it can do
     * different recipes, and therefore the exact class is checked.
     */
    public static boolean fits(String station,
            net.minecraft.world.level.block.entity.BlockEntity machine) {
        if (machine == null) {
            return false;
        }
        return switch (station) {
            case SMELTING ->
                    machine instanceof net.minecraft.world.level.block.entity
                            .FurnaceBlockEntity;
            case BLASTING ->
                    machine instanceof net.minecraft.world.level.block.entity
                            .BlastFurnaceBlockEntity;
            case SMOKING ->
                    machine instanceof net.minecraft.world.level.block.entity
                            .SmokerBlockEntity;
            case PRESSING ->
                    machine instanceof dev.devpanda.factorynetwork.block.entity
                            .PressBlockEntity;
            default -> false;
        };
    }

    /**
     * What the machine of a station is called — for the message in the job.
     *
     * <p>German and hard-coded: this line appears in the terminal alongside
     * plenty of other German lines, and a job that is half translated reads
     * worse than one that is not translated at all.
     */
    public static String machineName(String station) {
        return switch (station) {
            case SMELTING -> "einen Ofen";
            case BLASTING -> "einen Schmelzofen";
            case SMOKING -> "einen Räucherofen";
            case PRESSING -> "eine Presse";
            default -> "eine Maschine";
        };
    }

    private final Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult;

    private MachineRecipes(Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult) {
        this.byResult = byResult;
    }

    /**
     * The index of this world's readable machine recipes.
     *
     * <p>Built once per tick like the crafting-table recipes' index, and for
     * the same reason: keeping it longer would mean relying on a
     * {@code /reload} to swap out the recipe manager.
     */
    public static MachineRecipes of(Level level) {
        Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult = new HashMap<>();
        collectCooking(level, RecipeType.SMELTING, SMELTING, byResult);
        collectCooking(level, RecipeType.BLASTING, BLASTING, byResult);
        collectCooking(level, RecipeType.SMOKING, SMOKING, byResult);
        collectPress(level, byResult);
        return new MachineRecipes(byResult);
    }

    private static void collectCooking(Level level,
            RecipeType<? extends AbstractCookingRecipe> type, String station,
            Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult) {
        HolderLookup.Provider registries = level.registryAccess();
        for (RecipeHolder<? extends AbstractCookingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(type)) {
            ItemStack result = holder.value().getResultItem(registries);
            List<CraftingPlanner.Need<Item>> needs = needsOf(holder.value());
            if (result.isEmpty() || needs == null) {
                continue;
            }
            byResult.computeIfAbsent(result.getItem(), item -> new ArrayList<>())
                    .add(new CraftingPlanner.Recipe<>(result.getItem(), result.getCount(),
                            needs, station));
        }
    }

    /**
     * The press — and its stamp does not count as an ingredient.
     *
     * <p>It is a tool: it sits in the slot and stays there. To plan it in would
     * mean ordering it anew for every run and using it up.
     */
    private static void collectPress(Level level,
            Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult) {
        HolderLookup.Provider registries = level.registryAccess();
        for (RecipeHolder<dev.devpanda.factorynetwork.press.PressRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(
                        dev.devpanda.factorynetwork.press.FnRecipes.PRESS.get())) {
            ItemStack result = holder.value().getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            // <b>Each ingredient becomes its own need.</b> Since a recipe may
            // demand several, the first alone would be a calculation that never
            // works out: the job would order copper and then stand in front of
            // a machine that is missing the redstone.
            List<CraftingPlanner.Need<Item>> needs = new ArrayList<>();
            boolean complete = true;
            for (var material : holder.value().materials()) {
                List<Item> options = optionsOf(material.ingredient());
                if (options.isEmpty()) {
                    complete = false;
                    break;
                }
                needs.add(new CraftingPlanner.Need<>(options, material.count()));
            }
            if (!complete || needs.isEmpty()) {
                continue;
            }
            byResult.computeIfAbsent(result.getItem(), item -> new ArrayList<>())
                    .add(new CraftingPlanner.Recipe<>(result.getItem(), result.getCount(),
                            needs, PRESSING));
        }
    }

    /**
     * The single ingredient of a cooking recipe.
     *
     * <p>Exactly one, and that is why these types are readable at all. Were
     * there several, no one would know how many of which — {@code Ingredient}
     * carries no amount.
     *
     * @return {@code null} if the shape is not right
     */
    private static List<CraftingPlanner.Need<Item>> needsOf(Recipe<?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .toList();
        if (ingredients.size() != 1) {
            return null;
        }
        List<Item> options = optionsOf(ingredients.get(0));
        return options.isEmpty() ? null
                : List.of(new CraftingPlanner.Need<>(options, 1));
    }

    private static List<Item> optionsOf(Ingredient ingredient) {
        List<Item> options = new ArrayList<>();
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty() && !options.contains(candidate.getItem())) {
                options.add(candidate.getItem());
            }
        }
        return options;
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
