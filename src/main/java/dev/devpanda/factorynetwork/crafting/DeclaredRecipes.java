package dev.devpanda.factorynetwork.crafting;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.runtime.ItemSelection;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Die Rezepte, die im Programm stehen.
 *
 * <p>Was eine fremde Maschine kann, ist generisch nicht zu lesen — die
 * Belege stehen in {@code entscheidungen.md}, „Processing-Rezepte". Also
 * schreibt es der Spieler auf, und zwar dort, wo bei dieser Mod alles steht:
 * im Programm.
 *
 * <pre>
 * recipe erz_mahlen at brecher {
 *     in 1 item:iron_ore
 *     out 2 item:iron_dust
 * }
 * </pre>
 *
 * <p><b>Kein Muster-Item.</b> Der Fabricator baut ohne, und der Grund trägt
 * hierher weiter: Ein Musterterminal wäre der zweite Ort, an dem eine Fabrik
 * erklärt wird — der erste ist das Programm. Eine Deklaration geht dafür mit
 * der Datei nach VS Code, lässt sich versionieren, und ein Rezept an einem
 * Gerät, das es nicht gibt, meldet sich beim Übernehmen.
 *
 * <p><b>Das erste {@code out} zählt.</b> Ein Rezept darf mehrere Ergebnisse
 * nennen — ein Brecher liefert Staub und manchmal ein Nebenprodukt —, aber
 * geplant wird auf das erste. Die anderen landen trotzdem im Netz, wenn der
 * Ausführende abholt; sie sind eine Zugabe und kein Ziel.
 */
public final class DeclaredRecipes implements CraftingPlanner.Recipes<Item> {

    /** Woran der Ausführende erkennt, dass ein Rezept aus dem Programm kommt. */
    public static final String PREFIX = "at:";

    /** Die Station eines Rezepts an diesem Gerät. */
    public static String stationFor(String device) {
        return PREFIX + device;
    }

    /** Das Gerät hinter einer Station, oder {@code null}. */
    public static String deviceOf(String station) {
        return station.startsWith(PREFIX) ? station.substring(PREFIX.length()) : null;
    }

    private final Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult;

    private DeclaredRecipes(Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult) {
        this.byResult = byResult;
    }

    /** Was das Programm an Rezepten erklärt. */
    public static DeclaredRecipes of(Program program) {
        Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult = new HashMap<>();
        for (Decl declaration : program.declarations()) {
            if (!(declaration instanceof Decl.Recipe recipe) || recipe.outputs().isEmpty()) {
                continue;
            }
            List<Item> results = ItemSelection.resolve(recipe.outputs().get(0).selection());
            List<CraftingPlanner.Need<Item>> needs = needsOf(recipe);
            if (results.isEmpty() || needs == null) {
                continue;
            }
            // Trifft die Ausgabe mehrere Arten, gilt das Rezept für jede: Wer
            // „out 2 tag:c/dusts" schreibt, hat eine Auswahl hingeschrieben,
            // und welche davon herauskommt, weiß erst die Maschine.
            int perCraft = (int) recipe.outputs().get(0).amount();
            for (Item result : results) {
                byResult.computeIfAbsent(result, item -> new ArrayList<>())
                        .add(new CraftingPlanner.Recipe<>(result, perCraft, needs,
                                stationFor(recipe.device())));
            }
        }
        return new DeclaredRecipes(byResult);
    }

    /**
     * Die Zutaten eines erklärten Rezepts.
     *
     * <p>Eine Auswahl bleibt eine Auswahl, wie überall: {@code in 8
     * tag:c/plates} heißt acht von irgendwelchen Platten, und welche, sucht
     * der Planner nach Bestand aus.
     *
     * @return {@code null}, wenn eine Zutat gar nichts trifft
     */
    private static List<CraftingPlanner.Need<Item>> needsOf(Decl.Recipe recipe) {
        List<CraftingPlanner.Need<Item>> needs = new ArrayList<>();
        for (Decl.Recipe.Part part : recipe.inputs()) {
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
