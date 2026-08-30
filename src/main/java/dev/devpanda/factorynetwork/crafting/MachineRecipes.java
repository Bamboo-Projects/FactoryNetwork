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
 * Die Rezepte, die eine Maschine ausführt — und die das Netz lesen kann.
 *
 * <p><b>Nicht alle.</b> Ein Maschinenrezept einer fremden Mod ist generisch
 * nicht zu lesen: {@code Recipe.getIngredients()} liefert als Vorgabe eine
 * leere Liste, eine {@code Ingredient} trägt keine Menge, Flüssigkeiten und
 * Strom stehen nicht darin, und {@code getResultItem} kennt genau eine
 * Ausgabe. Die Begründung mit Belegen steht in {@code entscheidungen.md},
 * „Processing-Rezepte: die Erkundung".
 *
 * <p>Was hier steht, sind die Arten mit <b>fester, bekannter Form</b>: eine
 * Zutat, eine Ausgabe, eine Dauer. Das ist die Ofenfamilie und die eigene
 * Presse — für sie sind die vier Löcher zugemauert, weil die Form bekannt
 * ist. Alles andere schreibt der Spieler als {@code recipe} ins Programm.
 *
 * <p><b>Die Station ist die Rezeptart, nicht das Gerät.</b> Welcher Ofen
 * gebraucht wird, entscheidet erst der Ausführende — und der nimmt einen, der
 * gerade frei ist. Stünde hier schon ein Gerätename, wartete ein Auftrag vor
 * einem beschäftigten Ofen, während zwei leere danebenstehen.
 */
public final class MachineRecipes implements CraftingPlanner.Recipes<Item> {

    /**
     * Was der Ausführende an einer Station wissen muss.
     *
     * @param inputSlot  wohin die Zutat kommt
     * @param outputSlot woher das Ergebnis kommt
     * @param supply     was der Spieler beisteuern muss, für die Meldung
     */
    public record Station(int inputSlot, int outputSlot, String supply) {
    }

    /** Ofen, Schmelzofen, Räucherofen: Zutat in 0, Brennstoff in 1, Ergebnis in 2. */
    private static final Station FURNACE = new Station(0, 2, "Brennstoff");

    /** Die eigene Presse: Stempel in 0, Werkstoff in 1, Ergebnis in 2. */
    private static final Station PRESS =
            new Station(dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_MATERIAL,
                    dev.devpanda.factorynetwork.block.entity.PressBlockEntity.SLOT_RESULT,
                    "einen Stempel");

    /** Die Kennungen, unter denen die Stationen im Plan stehen. */
    public static final String SMELTING = "minecraft:smelting";
    public static final String BLASTING = "minecraft:blasting";
    public static final String SMOKING = "minecraft:smoking";
    public static final String PRESSING = "factorynetwork:press";

    private static final Map<String, Station> STATIONS = Map.of(
            SMELTING, FURNACE, BLASTING, FURNACE, SMOKING, FURNACE, PRESSING, PRESS);

    /** Was der Ausführende über diese Station wissen muss, oder {@code null}. */
    public static Station stationOf(String name) {
        return STATIONS.get(name);
    }

    /**
     * Passt diese Maschine zu dieser Station?
     *
     * <p><b>An der Klasse und nicht am Namen.</b> Ein Ofen heißt auf einem
     * englischen Server anders als im deutschen Client; seine BlockEntity ist
     * überall dieselbe. Und ein Schmelzofen ist keine Untermenge eines Ofens,
     * obwohl beide von {@code AbstractFurnaceBlockEntity} erben — er kann
     * andere Rezepte, und deshalb wird auf die genaue Klasse geprüft.
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
     * Wie die Maschine einer Station heißt — für die Meldung im Auftrag.
     *
     * <p>Auf Deutsch und fest: Diese Zeile steht im Terminal neben lauter
     * anderen deutschen Zeilen, und ein Auftrag, der halb übersetzt ist,
     * liest sich schlechter als einer, der es gar nicht ist.
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
     * Das Verzeichnis der lesbaren Maschinenrezepte dieser Welt.
     *
     * <p>Einmal je Tick gebaut wie das der Werkbank-Rezepte, und aus demselben
     * Grund: Länger aufzubewahren hieße, sich darauf zu verlassen, dass ein
     * {@code /reload} den Rezeptverwalter austauscht.
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
     * Die Presse — und ihr Stempel zählt nicht als Zutat.
     *
     * <p>Er ist ein Werkzeug: Er liegt im Fach und bleibt dort. Ihn
     * einzuplanen hieße, ihn für jeden Durchlauf neu zu bestellen und
     * aufzubrauchen.
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
            // <b>Jede Zutat wird ein eigener Bedarf.</b> Seit ein Rezept
            // mehrere fordern darf, wäre die erste allein eine Rechnung, die
            // nie aufgeht: Der Auftrag bestellte Kupfer und stünde dann vor
            // einer Maschine, der das Redstone fehlt.
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
     * Die eine Zutat eines Kochrezepts.
     *
     * <p>Genau eine, und das ist der Grund, warum diese Arten überhaupt
     * lesbar sind. Stünden dort mehrere, wüsste niemand, wie viele von
     * welcher — {@code Ingredient} trägt keine Menge.
     *
     * @return {@code null}, wenn die Form nicht stimmt
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
