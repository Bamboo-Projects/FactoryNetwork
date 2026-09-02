package dev.devpanda.factorynetwork.crafting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recursion of crafting, without a world.
 *
 * <p>The planner does not know what an item is — here they are strings. That
 * is exactly why it is possible to check what would otherwise only show up in
 * a loaded world: that a chain breaks off, that a cycle does not run forever,
 * that a surplus benefits the next step, and that between two kinds of planks
 * the right one is chosen.
 */
class CraftingPlannerTest {

    /** An ingredient that allows only one. */
    private static CraftingPlanner.Need<String> one(String item, int count) {
        return new CraftingPlanner.Need<>(List.of(item), count);
    }

    /** An ingredient with a choice — like a tag in the game. */
    private static CraftingPlanner.Need<String> any(int count, String... options) {
        return new CraftingPlanner.Need<>(List.of(options), count);
    }

    /** A recipe book of strings: target to recipe. */
    private static final class Book {

        private final Map<String, CraftingPlanner.Recipe<String>> recipes =
                new LinkedHashMap<>();

        @SafeVarargs
        final Book add(String result, int perCraft, CraftingPlanner.Need<String>... needs) {
            recipes.put(result,
                    new CraftingPlanner.Recipe<>(result, perCraft, List.of(needs)));
            return this;
        }

        CraftingPlanner.Recipes<String> recipes() {
            return (target, available) -> recipes.get(target);
        }
    }

    /** A stock from pairs: {@code stock("log", 2)}. */
    private static java.util.function.ToLongFunction<String> stock(Object... pairs) {
        Map<String, Long> have = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            have.put((String) pairs[i], ((Integer) pairs[i + 1]).longValue());
        }
        return item -> have.getOrDefault(item, 0L);
    }

    /** The steps as a readable list: {@code "planks x2"}. */
    private static List<String> named(CraftingPlanner.Plan<String> plan) {
        List<String> lines = new ArrayList<>();
        for (CraftingPlanner.Step<String> step : plan.steps()) {
            lines.add(step.result() + " x" + step.runs());
        }
        return lines;
    }

    private static final int DEPTH = 8;
    private static final int BUDGET = 1_000;

    private static CraftingPlanner.Plan<String> plan(Book book,
            java.util.function.ToLongFunction<String> stock, String target, long amount) {
        return CraftingPlanner.plan(book.recipes(), stock, target, amount, DEPTH, BUDGET);
    }

    @Test
    @DisplayName("When the ingredients are there, the plan is one step")
    void whenTheIngredientsAreThereThePlanIsOneStep() {
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 8), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("A step says what it consumes")
    void astepSaysWhatItConsumes() {
        // The step carries the finished calculation: the executor takes what
        // stands here and does not have to choose again.
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 64), "chest", 2);

        assertEquals(Map.of("planks", 16L), plan.steps().get(0).consumed());
        assertEquals(2L, plan.steps().get(0).yield());
    }

    @Test
    @DisplayName("If an ingredient is missing, it is built in turn")
    void amissingIngredientIsCraftedInTurn() {
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("log", 2), "chest", 1);

        // From bottom to top: first the planks, then the chest.
        assertEquals(List.of("planks x2", "chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("What lies in storage is not built again")
    void whatIsInStockIsNotCraftedAgain() {
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 8, "log", 64), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
    }

    @Test
    @DisplayName("The target itself is built, even if it lies in storage")
    void thetargetItselfIsBuiltEvenIfItIsInStock() {
        // Whoever orders 8 chests wants 8 built. Otherwise the order would
        // have nothing to do at creation, and the order would have vanished
        // silently.
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan =
                plan(book, stock("chest", 64, "planks", 8), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
    }

    @Test
    @DisplayName("A run that yields too much covers the next need too")
    void arunThatYieldsTooMuchCoversTheNextNeed() {
        // Without bookkeeping of the surplus the raw material would run twice:
        // once for the one branch, once for the other.
        Book book = new Book()
                .add("machine", 1, one("gear", 1), one("plate", 1))
                .add("gear", 1, one("ingot", 2))
                .add("plate", 1, one("ingot", 1))
                .add("ingot", 4, one("ore", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("ore", 8), "machine", 1);

        assertEquals(List.of("ingot x1", "gear x1", "plate x1", "machine x1"), named(plan));
    }

    @Test
    @DisplayName("What is missing is passed down to the raw material")
    void whatIsMissingIsPassedDownToTheRawMaterial() {
        // Not "8 planks are missing": planks the network can build. Logs are
        // missing, and someone has to put those down.
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "chest", 1);

        assertFalse(plan.complete(), "der Plan geht nicht auf");
        assertEquals(Map.of("log", 2L), plan.missing());
    }

    @Test
    @DisplayName("A cycle does not run forever")
    void acycleDoesNotRunForever() {
        // Ingots from a block and a block from ingots: the classic that a
        // naive recursion gets stuck on.
        Book book = new Book()
                .add("block", 1, one("ingot", 9))
                .add("ingot", 9, one("block", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "block", 1);

        assertFalse(plan.complete(), "ohne Barren geht es nicht");
        // And not "1 block is missing": the player just ordered that.
        assertEquals(Map.of("ingot", 9L), plan.missing());
    }

    @Test
    @DisplayName("A chain that is too deep breaks off instead of holding the server")
    void atoodeepChainStopsInsteadOfHoldingTheServer() {
        Book book = new Book()
                .add("a", 1, one("b", 1))
                .add("b", 1, one("c", 1))
                .add("c", 1, one("d", 1))
                .add("d", 1, one("e", 1));

        CraftingPlanner.Plan<String> plan = CraftingPlanner.plan(
                book.recipes(), stock(), "a", 1, 2, BUDGET);

        assertFalse(plan.complete(), "tiefer wird nicht gesucht");
        // At the cut stands what someone can put down — that is the difference
        // from the cycle.
        assertEquals(Map.of("c", 1L), plan.missing());
    }

    @Test
    @DisplayName("Without a recipe the target is simply missing")
    void withoutArecipeThetargetIsSimplyMissing() {
        CraftingPlanner.Plan<String> plan = plan(new Book(), stock(), "cobblestone", 5);

        assertTrue(plan.steps().isEmpty(), "kein Schritt");
        assertEquals(Map.of("cobblestone", 5L), plan.missing());
    }

    @Test
    @DisplayName("More work than allowed breaks off")
    void moreworkThanAllowedStops() {
        Book book = new Book()
                .add("a", 1, one("b", 1), one("c", 1))
                .add("b", 1, one("d", 1))
                .add("c", 1, one("e", 1))
                .add("d", 1, one("raw", 1))
                .add("e", 1, one("raw", 1));

        CraftingPlanner.Plan<String> plan = CraftingPlanner.plan(
                book.recipes(), stock("raw", 8), "a", 1, DEPTH, 4);

        assertFalse(plan.complete(), "der Plan sagt, dass er nicht aufgeht");
    }

    @Test
    @DisplayName("The need of the target counts with the yield")
    void theneedOfThetargetCountsWithTheYield() {
        // Four sticks per run, ten ordered: three runs.
        Book book = new Book().add("stick", 4, one("planks", 2));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 64), "stick", 10);

        assertEquals(List.of("stick x3"), named(plan));
    }

    // ---- Ingredients with a choice ----------------------------------------------

    @Test
    @DisplayName("Of two allowed kinds the one in stock is taken")
    void ofTwoAllowedKindsTheOneInStockIsTaken() {
        Book book = new Book().add("chest", 1, any(8, "oak_planks", "spruce_planks"));

        CraftingPlanner.Plan<String> plan = plan(book, stock("spruce_planks", 64), "chest", 1);

        assertEquals(Map.of("spruce_planks", 8L), plan.steps().get(0).consumed());
    }

    @Test
    @DisplayName("If no kind alone is enough, they are mixed")
    void whenNokindAloneIsEnoughTheyAreMixed() {
        // The game allows it: in a chest oak and spruce may stand side by
        // side. A network that insists on taking everything from one kind
        // refuses work that would go by hand.
        Book book = new Book().add("chest", 1, any(8, "oak_planks", "spruce_planks"));

        CraftingPlanner.Plan<String> plan =
                plan(book, stock("oak_planks", 5, "spruce_planks", 5), "chest", 1);

        assertEquals(Map.of("oak_planks", 5L, "spruce_planks", 3L),
                plan.steps().get(0).consumed());
    }

    @Test
    @DisplayName("If no kind is in stock, the one that can be built is made")
    void whenNokindIsInStockThebuildableOneIsMade() {
        // The case where a fixed choice fails: whoever has only spruce logs
        // would get "8 oak planks are missing" — and the wood lies in the
        // drive.
        Book book = new Book()
                .add("chest", 1, any(8, "oak_planks", "spruce_planks"))
                .add("oak_planks", 4, one("oak_log", 1))
                .add("spruce_planks", 4, one("spruce_log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("spruce_log", 2), "chest", 1);

        assertEquals(List.of("spruce_planks x2", "chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("If no kind works, the first one stands in the missing line")
    void whenNokindWorksThefirstOneIsNamed() {
        Book book = new Book()
                .add("chest", 1, any(8, "oak_planks", "spruce_planks"))
                .add("oak_planks", 4, one("oak_log", 1))
                .add("spruce_planks", 4, one("spruce_log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "chest", 1);

        assertEquals(Map.of("oak_log", 2L), plan.missing());
    }

    @Test
    @DisplayName("A step knows where it runs")
    void astepKnowsWhereItRuns() {
        // A crafting-table recipe runs at the fabricator and is done in one
        // go; a furnace recipe needs a machine and time. The planner does not
        // distinguish the two — it only passes through what the recipe source
        // wrote down.
        CraftingPlanner.Recipes<String> book = (target, available) ->
                "ingot".equals(target)
                        ? new CraftingPlanner.Recipe<>("ingot", 1,
                                List.of(one("raw_iron", 1)), "minecraft:smelting")
                        : null;

        CraftingPlanner.Plan<String> plan = CraftingPlanner.plan(
                book, stock("raw_iron", 4), "ingot", 1, DEPTH, BUDGET);

        assertEquals("minecraft:smelting", plan.steps().get(0).station());
    }

    @Test
    @DisplayName("Without an entry a step runs at the fabricator")
    void withoutAnentryAstepRunsAtThefabricator() {
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 8), "chest", 1);

        assertEquals("", plan.steps().get(0).station(),
                "leer heißt: kein Gerät, sondern der Fabricator");
    }

    @Test
    @DisplayName("A discarded attempt consumes nothing")
    void adiscardedAttemptConsumesNothing() {
        // The attempt to build oak planks fails only deep down — after it has
        // already planned the glue. What it touched must go back, otherwise it
        // is missing for the attempt that succeeds.
        Book book = new Book()
                .add("chest", 1, any(8, "oak_planks", "spruce_planks"), one("glue", 1))
                .add("oak_planks", 4, one("glue", 1), one("oak_log", 1))
                .add("spruce_planks", 4, one("spruce_log", 1));

        CraftingPlanner.Plan<String> plan =
                plan(book, stock("spruce_log", 2, "glue", 1), "chest", 1);

        assertEquals(List.of("spruce_planks x2", "chest x1"), named(plan));
        assertTrue(plan.complete(), "der Leim reicht für die Truhe: " + plan.missing());
    }
}
