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
 * Findet die Rezepte zu einem Gegenstand und sagt, was sie kosten.
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
 * <p><b>Ein Verzeichnis, kein Durchsuchen.</b> Die Rezeptliste eines Packs
 * hat fünfstellige Länge, und der Planner fragt sie für jeden Knoten eines
 * Rezeptbaums. Deshalb wird sie einmal nach Ergebnis geordnet und dann
 * nachgeschlagen. Das Verzeichnis lebt genau einen Tick: Es nach einem
 * {@code /reload} von Hand ungültig zu machen hieße, sich auf Innenleben zu
 * verlassen, das keine Zusage ist.
 */
public final class RecipeLookup implements CraftingPlanner.Recipes<Item> {

    private final Map<Item, List<RecipeHolder<CraftingRecipe>>> byResult;
    private final HolderLookup.Provider registries;

    private RecipeLookup(Map<Item, List<RecipeHolder<CraftingRecipe>>> byResult,
                         HolderLookup.Provider registries) {
        this.byResult = byResult;
        this.registries = registries;
    }

    /** Das Verzeichnis der Werkbank-Rezepte dieser Welt. */
    public static RecipeLookup of(Level level) {
        Map<Item, List<RecipeHolder<CraftingRecipe>>> byResult = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            byResult.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(holder);
        }
        return new RecipeLookup(byResult, level.registryAccess());
    }

    /**
     * Das erste Rezept, dessen Zutaten der Bestand hergibt.
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
     * @param available wie viel von einer Art zur Verfügung steht; beim
     *                  Planen ist das nicht der Speicher, sondern der Stand
     *                  der Planung
     */
    @Override
    public CraftingPlanner.Recipe<Item> find(Item target, ToLongFunction<Item> available) {
        CraftingPlanner.Recipe<Item> first = null;
        for (RecipeHolder<CraftingRecipe> holder : byResult.getOrDefault(target, List.of())) {
            ItemStack result = holder.value().getResultItem(registries);
            List<CraftingPlanner.Need<Item>> needs = needsOf(holder.value());
            if (needs == null || needs.isEmpty()) {
                continue;
            }
            CraftingPlanner.Recipe<Item> recipe =
                    new CraftingPlanner.Recipe<>(target, result.getCount(), needs);
            if (first == null) {
                first = recipe;
            }
            if (covered(needs, available)) {
                return recipe;
            }
        }
        return first;
    }

    /** Ob es für diesen Gegenstand überhaupt ein Rezept gibt. */
    public boolean known(Item target) {
        return find(target, item -> 0L) != null;
    }

    /**
     * Die Zutaten eines Rezepts.
     *
     * <p>Eine Zutat bleibt eine <b>Auswahl</b> — „irgendein Brett" —, und
     * genau so wird sie weitergereicht. Sich hier auf eine Art festzulegen
     * war der Fehler der ersten Fassung: Sie nahm die Sorte, von der am
     * meisten dalag, und wenn von keiner etwas dalag, die erste. Wer nur
     * Fichtenstämme hatte, bekam „es fehlen 8 Eichenbretter".
     *
     * @return {@code null}, wenn eine Zutat gar nichts zulässt
     */
    private static List<CraftingPlanner.Need<Item>> needsOf(CraftingRecipe recipe) {
        // Gleiche Auswahlen gehören zusammen: Acht Bretter sind ein Bedarf
        // über acht und nicht acht Bedarfe über eines.
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
     * Ob der Bestand die Zutaten hergibt.
     *
     * <p>Grob gerechnet: Zwei Bedarfe, die dieselbe Sorte zulassen, zählen
     * sie beide. Das reicht für die Frage, die hier ansteht — welches von
     * mehreren Rezepten dem Bestand am nächsten kommt. Wie viel wirklich
     * gedeckt ist, rechnet der Planner, und der rechnet es genau.
     */
    private static boolean covered(List<CraftingPlanner.Need<Item>> needs,
                                   ToLongFunction<Item> available) {
        for (CraftingPlanner.Need<Item> need : needs) {
            long have = 0;
            for (Item option : need.options()) {
                have += available.applyAsLong(option);
            }
            if (have < need.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Was fehlt, als lesbare Zeile.
     *
     * <p>Leer, wenn nichts fehlt. Ein Auftrag, der stillsteht, muss den Grund
     * nennen — sonst sucht man ihn beim Netz statt beim Bestand. Genannt wird
     * der <b>Grundstoff</b> und nicht die Zwischenstufe: „es fehlen 8
     * Bretter" hilft niemandem, der Bretter herstellen kann.
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
