package dev.devpanda.factorynetwork.press;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every demand gets its own slot.
 *
 * <p>Sounds simple and is not: whoever takes the first matching slot in order
 * boxes themselves in. A recipe of <i>any metal</i> and <i>copper</i> lies in
 * a press with copper and iron — if the first demand takes the copper, the
 * second finds nothing more, although the assignment obviously works out.
 *
 * <p>That is why the assignment stands here as its own computation, without
 * Minecraft types, and is checked against letters instead of items.
 */
class AssignmentTest {

    /** "Matches" here means: the same letter, or the demand is a star. */
    private static boolean matches(String demand, String slot) {
        return demand.equals("*") ? !slot.isEmpty() : demand.equals(slot);
    }

    private static boolean fits(List<String> demands, List<String> slots) {
        return Assignment.fits(demands, slots, AssignmentTest::matches);
    }

    @Test
    @DisplayName("What is demanded and present works out")
    void plainCase() {
        assertTrue(fits(List.of("a", "b"), List.of("a", "b", "")));
        assertTrue(fits(List.of("a", "b"), List.of("b", "a", "")));
    }

    @Test
    @DisplayName("Without a demand any stock fits")
    void noDemands() {
        assertTrue(fits(List.of(), List.of("a", "b", "")));
    }

    @Test
    @DisplayName("What is missing is missing")
    void missingIngredient() {
        assertFalse(fits(List.of("a", "c"), List.of("a", "b", "")));
    }

    @Test
    @DisplayName("Two identical demands need two slots")
    void twoOfTheSame() {
        assertTrue(fits(List.of("a", "a"), List.of("a", "a", "")));
        assertFalse(fits(List.of("a", "a"), List.of("a", "b", "")),
                "ein Platz kann nicht zweimal zählen");
    }

    @Test
    @DisplayName("The greedy assignment boxes itself in — this one does not")
    void greedyWouldFail() {
        // The star grabs first and greedily takes the "b" — then the second
        // demand finds nothing more, although "a" would have been there for
        // the star. It has to be able to step back.
        assertTrue(fits(List.of("*", "b"), List.of("b", "a", "")),
                "der Stern muss zurücktreten können");
        assertTrue(fits(List.of("*", "*", "c"), List.of("a", "c", "b")));

        // And the counter-check to the line above: with only one occupied
        // slot there is nothing to distribute for two demands.
        assertFalse(fits(List.of("*", "b"), List.of("b", "", "")),
                "zwei Forderungen brauchen zwei belegte Plätze");
    }

    @Test
    @DisplayName("The assignment also says from which slot what comes")
    void assignmentNamesTheSlots() {
        int[] wohin = Assignment.assign(List.of("b", "a"), List.of("a", "b", ""),
                AssignmentTest::matches);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {1, 0}, wohin,
                "wer verbraucht, muss wissen, woher");

        org.junit.jupiter.api.Assertions.assertNull(
                Assignment.assign(List.of("z"), List.of("a", "b", ""),
                        AssignmentTest::matches),
                "keine Zuordnung heißt null und keine leere Reihe");
    }

    @Test
    @DisplayName("More demands than slots never work out")
    void tooManyDemands() {
        assertFalse(fits(List.of("a", "b", "c", "d"), List.of("a", "b", "c")));
    }
}
