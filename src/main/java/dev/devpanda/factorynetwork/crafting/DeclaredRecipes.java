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
 * <p><b>Flüssigkeiten und Chemikalien werden eingefüllt, nicht geplant.</b>
 * Der Planner rechnet mit Gegenständen; sie auch zu beschaffen hieße, eine
 * Ressourcenart zu brauchen, die offen ist statt fest — eine eigene
 * Entscheidung. Bis dahin holt der Auftrag sie beim Anfangen aus dem
 * Netzspeicher, und ist zu wenig da, wartet er und sagt, welche Sorte fehlt.
 *
 * <p><b>Das erste {@code out} zählt.</b> Ein Rezept darf mehrere Ergebnisse
 * nennen — ein Brecher liefert Staub und manchmal ein Nebenprodukt —, aber
 * geplant wird auf das erste. Die anderen landen trotzdem im Netz, wenn der
 * Ausführende abholt; sie sind eine Zugabe und kein Ziel.
 */
public final class DeclaredRecipes implements CraftingPlanner.Recipes<Item> {

    /** Woran der Ausführende erkennt, dass ein Rezept aus dem Programm kommt. */
    public static final String PREFIX = "at:";

    /** Was in einer Station das Gerät vom Rezeptnamen trennt. */
    private static final char SEPARATOR = '#';

    /**
     * Die Station eines Rezepts an diesem Gerät.
     *
     * <p>Der Name des Rezepts steht mit darin, getrennt durch ein
     * Doppelkreuz. Er wird gebraucht, um die <b>Nebenzutaten</b>
     * wiederzufinden: Zwei Rezepte am selben Gerät mit derselben Ausgabe
     * ließen sich sonst nicht unterscheiden, und dann bekäme die Maschine das
     * Wasser des anderen.
     */
    public static String stationFor(String device, String recipe) {
        return PREFIX + device + "#" + recipe;
    }

    /** Das Gerät hinter einer Station, oder {@code null}. */
    public static String deviceOf(String station) {
        if (!station.startsWith(PREFIX)) {
            return null;
        }
        String rest = station.substring(PREFIX.length());
        int hash = rest.indexOf(SEPARATOR);
        return hash < 0 ? rest : rest.substring(0, hash);
    }

    /**
     * Und der Name des Rezepts, oder leer.
     *
     * <p>Leer heißt: eine Station aus einer Welt, die vor den Nebenzutaten
     * gespeichert wurde. Der Auftrag läuft weiter — er hat nur nichts
     * einzufüllen.
     */
    public static String recipeOf(String station) {
        int hash = station.indexOf(SEPARATOR);
        return hash < 0 ? "" : station.substring(hash + 1);
    }

    /**
     * Eine Zutat, die kein Gegenstand ist.
     *
     * <p><b>Sie wird eingefüllt, nicht geplant.</b> Der Planner rechnet mit
     * Gegenständen; eine Flüssigkeit als Zutat auch zu <i>beschaffen</i>
     * hieße, dass er Rezepte über Flüssigkeiten kennen müsste — und dazu
     * bräuchte es eine Ressourcenart, die offen ist statt fest. Das ist eine
     * eigene Entscheidung (siehe {@code offene-punkte.md}).
     *
     * <p>Bis dahin gilt: Was hier steht, holt der Auftrag beim Anfangen aus
     * dem Netzspeicher und füllt es in die Maschine — genau wie die
     * Gegenstände daneben, nur ohne die Rekursion dahinter. Ist zu wenig da,
     * wartet er und sagt, welche Sorte fehlt.
     *
     * @param fluid ob es eine Flüssigkeit ist; sonst eine Chemikalie
     */
    public record Extra(boolean fluid, long amount, Expr selection) {

        /**
         * Wie viel für so viele Durchgänge gebraucht wird.
         *
         * <p>Die Gegenstände gehen für alle Durchgänge auf einmal in die
         * Maschine; das Wasser muss mitwachsen. Sonst stünde dort eine
         * Maschine mit vier Erzen und einem Eimer.
         */
        public long needFor(long runs) {
            return runs <= 0 ? 0 : amount * runs;
        }
    }

    private final Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult;

    /** Die Nebenzutaten je Station — Flüssigkeiten und Chemikalien. */
    private final Map<String, List<Extra>> extras;

    private DeclaredRecipes(Map<Item, List<CraftingPlanner.Recipe<Item>>> byResult,
                            Map<String, List<Extra>> extras) {
        this.byResult = byResult;
        this.extras = extras;
    }

    /** Was diese Station außer Gegenständen braucht. */
    public List<Extra> extrasOf(String station) {
        return extras.getOrDefault(station, List.of());
    }

    /** Was das Programm an Rezepten erklärt. */
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
            // Trifft die Ausgabe mehrere Arten, gilt das Rezept für jede: Wer
            // „out 2 tag:c/dusts" schreibt, hat eine Auswahl hingeschrieben,
            // und welche davon herauskommt, weiß erst die Maschine.
            int perCraft = (int) recipe.outputs().get(0).amount();
            for (Item result : results) {
                byResult.computeIfAbsent(result, item -> new ArrayList<>())
                        .add(new CraftingPlanner.Recipe<>(result, perCraft, needs, station));
            }
        }
        return new DeclaredRecipes(byResult, Map.copyOf(extras));
    }

    /**
     * Die Zutaten, die keine Gegenstände sind.
     *
     * <p>Erkannt an der Art der Auswahl, nicht daran, dass sie nichts trifft:
     * Ein {@code item:gibtsnicht} ist ein Tippfehler und keine Flüssigkeit.
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
            // Flüssigkeiten und Chemikalien gehen den anderen Weg: Sie werden
            // eingefüllt, nicht geplant.
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
