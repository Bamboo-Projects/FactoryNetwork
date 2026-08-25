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
 * Die Rekursion der Fertigung, ohne Welt.
 *
 * <p>Der Planner weiß nicht, was ein Gegenstand ist — hier sind es Texte.
 * Genau deshalb lässt sich prüfen, was sonst nur in einer geladenen Welt
 * auffiele: dass eine Kette abbricht, dass ein Kreis nicht ewig läuft, dass
 * ein Überschuss dem nächsten Schritt zugutekommt und dass zwischen zwei
 * Brettersorten die richtige gewählt wird.
 */
class CraftingPlannerTest {

    /** Eine Zutat, die nur eines zulässt. */
    private static CraftingPlanner.Need<String> one(String item, int count) {
        return new CraftingPlanner.Need<>(List.of(item), count);
    }

    /** Eine Zutat mit Auswahl — wie ein Tag im Spiel. */
    private static CraftingPlanner.Need<String> any(int count, String... options) {
        return new CraftingPlanner.Need<>(List.of(options), count);
    }

    /** Ein Rezeptbuch aus Texten: Ziel auf Rezept. */
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

    /** Ein Bestand aus Paaren: {@code stock("log", 2)}. */
    private static java.util.function.ToLongFunction<String> stock(Object... pairs) {
        Map<String, Long> have = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            have.put((String) pairs[i], ((Integer) pairs[i + 1]).longValue());
        }
        return item -> have.getOrDefault(item, 0L);
    }

    /** Die Schritte als lesbare Liste: {@code "planks x2"}. */
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
    @DisplayName("Liegen die Zutaten da, ist der Plan ein Schritt")
    void whenTheIngredientsAreThereThePlanIsOneStep() {
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 8), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("Ein Schritt sagt, was er verbraucht")
    void astepSaysWhatItConsumes() {
        // Der Schritt trägt die fertige Rechnung: Der Ausführende entnimmt,
        // was hier steht, und muss nicht noch einmal auswählen.
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 64), "chest", 2);

        assertEquals(Map.of("planks", 16L), plan.steps().get(0).consumed());
        assertEquals(2L, plan.steps().get(0).yield());
    }

    @Test
    @DisplayName("Fehlt eine Zutat, wird sie ihrerseits gebaut")
    void amissingIngredientIsCraftedInTurn() {
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("log", 2), "chest", 1);

        // Von unten nach oben: erst die Bretter, dann die Truhe.
        assertEquals(List.of("planks x2", "chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("Was im Speicher liegt, wird nicht noch einmal gebaut")
    void whatIsInStockIsNotCraftedAgain() {
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 8, "log", 64), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
    }

    @Test
    @DisplayName("Das Ziel selbst wird gebaut, auch wenn es im Speicher liegt")
    void thetargetItselfIsBuiltEvenIfItIsInStock() {
        // Wer 8 Truhen bestellt, will 8 gebaut haben. Sonst hätte der Auftrag
        // beim Anlegen schon nichts zu tun, und die Bestellung wäre stumm
        // verschwunden.
        Book book = new Book().add("chest", 1, one("planks", 8));

        CraftingPlanner.Plan<String> plan =
                plan(book, stock("chest", 64, "planks", 8), "chest", 1);

        assertEquals(List.of("chest x1"), named(plan));
    }

    @Test
    @DisplayName("Ein Durchlauf, der zu viel liefert, deckt den nächsten Bedarf mit")
    void arunThatYieldsTooMuchCoversTheNextNeed() {
        // Ohne Buchführung über das Zuviel liefe der Grundstoff zweimal: einmal
        // für den einen Zweig, einmal für den anderen.
        Book book = new Book()
                .add("machine", 1, one("gear", 1), one("plate", 1))
                .add("gear", 1, one("ingot", 2))
                .add("plate", 1, one("ingot", 1))
                .add("ingot", 4, one("ore", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("ore", 8), "machine", 1);

        assertEquals(List.of("ingot x1", "gear x1", "plate x1", "machine x1"), named(plan));
    }

    @Test
    @DisplayName("Was fehlt, wird bis zum Grundstoff durchgereicht")
    void whatIsMissingIsPassedDownToTheRawMaterial() {
        // Nicht „es fehlen 8 Bretter": Bretter kann das Netz bauen. Es fehlen
        // Stämme, und die muss jemand hinlegen.
        Book book = new Book()
                .add("chest", 1, one("planks", 8))
                .add("planks", 4, one("log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "chest", 1);

        assertFalse(plan.complete(), "der Plan geht nicht auf");
        assertEquals(Map.of("log", 2L), plan.missing());
    }

    @Test
    @DisplayName("Ein Kreis läuft nicht ewig")
    void acycleDoesNotRunForever() {
        // Barren aus Block und Block aus Barren: der Klassiker, an dem eine
        // naive Rekursion hängenbleibt.
        Book book = new Book()
                .add("block", 1, one("ingot", 9))
                .add("ingot", 9, one("block", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "block", 1);

        assertFalse(plan.complete(), "ohne Barren geht es nicht");
        // Und nicht „es fehlt 1 block": Das hat der Spieler gerade bestellt.
        assertEquals(Map.of("ingot", 9L), plan.missing());
    }

    @Test
    @DisplayName("Eine zu tiefe Kette bricht ab, statt den Server zu halten")
    void atoodeepChainStopsInsteadOfHoldingTheServer() {
        Book book = new Book()
                .add("a", 1, one("b", 1))
                .add("b", 1, one("c", 1))
                .add("c", 1, one("d", 1))
                .add("d", 1, one("e", 1));

        CraftingPlanner.Plan<String> plan = CraftingPlanner.plan(
                book.recipes(), stock(), "a", 1, 2, BUDGET);

        assertFalse(plan.complete(), "tiefer wird nicht gesucht");
        // Am Schnitt steht, was jemand hinlegen kann — das ist der Unterschied
        // zum Kreis.
        assertEquals(Map.of("c", 1L), plan.missing());
    }

    @Test
    @DisplayName("Ohne Rezept fehlt schlicht das Ziel")
    void withoutArecipeThetargetIsSimplyMissing() {
        CraftingPlanner.Plan<String> plan = plan(new Book(), stock(), "cobblestone", 5);

        assertTrue(plan.steps().isEmpty(), "kein Schritt");
        assertEquals(Map.of("cobblestone", 5L), plan.missing());
    }

    @Test
    @DisplayName("Mehr Arbeit als erlaubt bricht ab")
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
    @DisplayName("Der Bedarf des Ziels rechnet mit der Ausbeute")
    void theneedOfThetargetCountsWithTheYield() {
        // Vier Stöcke je Durchlauf, zehn bestellt: drei Durchläufe.
        Book book = new Book().add("stick", 4, one("planks", 2));

        CraftingPlanner.Plan<String> plan = plan(book, stock("planks", 64), "stick", 10);

        assertEquals(List.of("stick x3"), named(plan));
    }

    // ---- Zutaten mit Auswahl ----------------------------------------------

    @Test
    @DisplayName("Von zwei erlaubten Sorten wird die genommen, die dasteht")
    void ofTwoAllowedKindsTheOneInStockIsTaken() {
        Book book = new Book().add("chest", 1, any(8, "oak_planks", "spruce_planks"));

        CraftingPlanner.Plan<String> plan = plan(book, stock("spruce_planks", 64), "chest", 1);

        assertEquals(Map.of("spruce_planks", 8L), plan.steps().get(0).consumed());
    }

    @Test
    @DisplayName("Reicht keine Sorte allein, werden sie gemischt")
    void whenNokindAloneIsEnoughTheyAreMixed() {
        // Das Spiel erlaubt es: In einer Truhe dürfen Eiche und Fichte
        // nebeneinanderstehen. Ein Netz, das darauf besteht, alles aus einer
        // Sorte zu nehmen, verweigert eine Arbeit, die von Hand ginge.
        Book book = new Book().add("chest", 1, any(8, "oak_planks", "spruce_planks"));

        CraftingPlanner.Plan<String> plan =
                plan(book, stock("oak_planks", 5, "spruce_planks", 5), "chest", 1);

        assertEquals(Map.of("oak_planks", 5L, "spruce_planks", 3L),
                plan.steps().get(0).consumed());
    }

    @Test
    @DisplayName("Liegt keine Sorte da, wird die gebaut, die sich bauen lässt")
    void whenNokindIsInStockThebuildableOneIsMade() {
        // Der Fall, an dem eine feste Wahl scheitert: Wer nur Fichtenstämme
        // hat, bekäme „es fehlen 8 Eichenbretter" — und im Laufwerk liegt das
        // Holz.
        Book book = new Book()
                .add("chest", 1, any(8, "oak_planks", "spruce_planks"))
                .add("oak_planks", 4, one("oak_log", 1))
                .add("spruce_planks", 4, one("spruce_log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock("spruce_log", 2), "chest", 1);

        assertEquals(List.of("spruce_planks x2", "chest x1"), named(plan));
        assertTrue(plan.complete(), "nichts fehlt: " + plan.missing());
    }

    @Test
    @DisplayName("Geht keine Sorte, steht die erste in der Fehlzeile")
    void whenNokindWorksThefirstOneIsNamed() {
        Book book = new Book()
                .add("chest", 1, any(8, "oak_planks", "spruce_planks"))
                .add("oak_planks", 4, one("oak_log", 1))
                .add("spruce_planks", 4, one("spruce_log", 1));

        CraftingPlanner.Plan<String> plan = plan(book, stock(), "chest", 1);

        assertEquals(Map.of("oak_log", 2L), plan.missing());
    }

    @Test
    @DisplayName("Ein verworfener Versuch verbraucht nichts")
    void adiscardedAttemptConsumesNothing() {
        // Der Versuch, Eichenbretter zu bauen, scheitert erst tief unten —
        // nachdem er den Leim schon eingeplant hat. Was er angefasst hat, muss
        // zurück, sonst fehlt er dem Versuch, der gelingt.
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
