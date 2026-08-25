package dev.devpanda.factorynetwork.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Findet das Rezept zu einem Gegenstand und sagt, was es kostet.
 *
 * <p><b>Eine eigene Klasse und nicht drei Zeilen in der BlockEntity:</b> Die
 * Rezeptsuche ist die Stelle, an der eine Fertigung falsch liegt, ohne dass
 * es auffällt — ein anderes Rezept, eine andere Zutat, dieselbe Ausgabe. So
 * lässt sie sich in einer echten Welt prüfen, ohne einen Block zu setzen.
 *
 * <p><b>Nur Werkbank-Rezepte.</b> Was eine Maschine kann, weiß die Maschine,
 * und das herauszufinden ist eine eigene Aufgabe (Punkt 2.9). Hier geht es um
 * das, was jeder Spieler von Hand im 3x3 baut — und was ein Netz ihm deshalb
 * abnehmen kann, ohne ein einziges Muster-Item.
 *
 * <p><b>Einstufig.</b> Fehlt eine Zutat, wird sie nicht ihrerseits gebaut.
 * Das ist ein Schnitt und kein Mangel: Ein Auftrag, der stillsteht und sagt
 * „es fehlen acht Bretter", ist ehrlicher als einer, der im Hintergrund einen
 * Baum fällt, den niemand bestellt hat.
 */
public final class RecipeLookup {

    /**
     * Ein gefundenes Rezept: was herauskommt und was hineingeht.
     *
     * <p>Die Zutaten stehen als Karte, weil zwei gleiche Zutaten in einem
     * Rezept zusammengehören: Acht Bretter sind acht Bretter und nicht
     * achtmal eines.
     */
    public record Plan(Item result, int perCraft, Map<Item, Integer> ingredients) {

        /** Wie oft dieses Rezept laufen muss, um so viele zu liefern. */
        public int runsFor(int wanted) {
            return Math.max(1, (wanted + perCraft - 1) / perCraft);
        }
    }

    private RecipeLookup() {
    }

    /**
     * Das erste Rezept, dessen Zutaten der Speicher hergibt.
     *
     * <p><b>Der Bestand entscheidet mit</b>, und das ist der ganze Trick an
     * dieser Stelle: Für einen Gegenstand gibt es oft mehrere Rezepte, und
     * eines davon passt zu dem, was dasteht. Wer nur das erstbeste nähme,
     * meldete „es fehlt Fichtenholz", während ein Stapel Eichenbretter im
     * Laufwerk liegt.
     *
     * <p>Findet sich keines, dessen Zutaten vollständig da sind, kommt das
     * erste überhaupt zurück — dann steht im Auftrag, was fehlt, statt „kein
     * Rezept".
     *
     * @param available wie viel der Speicher von einer Art hat
     * @return der Plan, oder {@code null}, wenn es gar kein Rezept gibt
     */
    public static Plan find(Level level, Item target, ToLongFunction<Item> available) {
        Plan first = null;
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || result.getItem() != target) {
                continue;
            }
            Map<Item, Integer> ingredients = ingredientsOf(holder.value(), available);
            if (ingredients == null) {
                continue;
            }
            Plan plan = new Plan(target, result.getCount(), ingredients);
            if (first == null) {
                first = plan;
            }
            if (covered(ingredients, available)) {
                return plan;
            }
        }
        return first;
    }

    /**
     * Die Zutaten eines Rezepts, auf feste Gegenstände festgelegt.
     *
     * <p>Eine Zutat ist in Minecraft eine <b>Auswahl</b> — „irgendein Brett"
     * — und keine Art. Festgelegt wird auf das, wovon der Speicher am meisten
     * hat: Wer aus zwei Sorten wählen kann, nimmt die, die er ohnehin
     * loswerden will.
     *
     * @return {@code null}, wenn eine Zutat gar nichts zulässt
     */
    private static Map<Item, Integer> ingredientsOf(CraftingRecipe recipe,
                                                    ToLongFunction<Item> available) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length == 0) {
                return null;
            }
            Item chosen = candidates[0].getItem();
            long best = -1;
            for (ItemStack candidate : candidates) {
                long have = available.applyAsLong(candidate.getItem());
                if (have > best) {
                    best = have;
                    chosen = candidate.getItem();
                }
            }
            needed.merge(chosen, 1, Integer::sum);
        }
        return needed;
    }

    private static boolean covered(Map<Item, Integer> needed, ToLongFunction<Item> available) {
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            if (available.applyAsLong(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Was von den Zutaten fehlt, als lesbare Zeile.
     *
     * <p>Leer, wenn nichts fehlt. Ein Auftrag, der stillsteht, muss den Grund
     * nennen — sonst sucht man ihn beim Netz statt beim Bestand.
     */
    public static String missing(Plan plan, int runs, ToLongFunction<Item> available) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : plan.ingredients().entrySet()) {
            long need = (long) entry.getValue() * runs;
            long have = available.applyAsLong(entry.getKey());
            if (have < need) {
                lines.add((need - have) + " " + entry.getKey().getDescription().getString());
            }
        }
        return lines.isEmpty() ? "" : "es fehlt: " + String.join(", ", lines);
    }

    /**
     * Baut die Eingabe, die ein Rezept zum Rechnen braucht.
     *
     * <p>Gebraucht wird sie nicht zum Prüfen — das tut {@link #covered} über
     * die Zutatenliste —, sondern für {@code assemble}: Manche Rezepte
     * rechnen ihre Ausgabe aus der Eingabe aus, und die brauchen ein Gitter,
     * kein Verzeichnis.
     */
    public static CraftingInput inputFor(Map<Item, Integer> ingredients) {
        List<ItemStack> grid = new ArrayList<>(9);
        ingredients.forEach((item, count) -> {
            for (int i = 0; i < count && grid.size() < 9; i++) {
                grid.add(new ItemStack(item));
            }
        });
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, grid);
    }
}
