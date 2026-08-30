package dev.devpanda.factorynetwork.press;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jede Forderung bekommt ihren eigenen Platz.
 *
 * <p>Klingt einfach und ist es nicht: Wer der Reihe nach den ersten passenden
 * Platz nimmt, verbaut sich. Ein Rezept aus <i>irgendein Metall</i> und
 * <i>Kupfer</i> liegt in einer Presse mit Kupfer und Eisen — nimmt die erste
 * Forderung das Kupfer, findet die zweite nichts mehr, obwohl die Zuordnung
 * offensichtlich aufgeht.
 *
 * <p>Deshalb steht die Zuordnung hier als eigene Rechnung, ohne
 * Minecraft-Typen, und wird an Buchstaben geprüft statt an Gegenständen.
 */
class AssignmentTest {

    /** „Passt" heißt hier: derselbe Buchstabe, oder die Forderung ist ein Stern. */
    private static boolean matches(String demand, String slot) {
        return demand.equals("*") ? !slot.isEmpty() : demand.equals(slot);
    }

    private static boolean fits(List<String> demands, List<String> slots) {
        return Assignment.fits(demands, slots, AssignmentTest::matches);
    }

    @Test
    @DisplayName("Was gefordert ist und dasteht, geht auf")
    void plainCase() {
        assertTrue(fits(List.of("a", "b"), List.of("a", "b", "")));
        assertTrue(fits(List.of("a", "b"), List.of("b", "a", "")));
    }

    @Test
    @DisplayName("Ohne Forderung passt jeder Bestand")
    void noDemands() {
        assertTrue(fits(List.of(), List.of("a", "b", "")));
    }

    @Test
    @DisplayName("Was fehlt, fehlt")
    void missingIngredient() {
        assertFalse(fits(List.of("a", "c"), List.of("a", "b", "")));
    }

    @Test
    @DisplayName("Zwei gleiche Forderungen brauchen zwei Plätze")
    void twoOfTheSame() {
        assertTrue(fits(List.of("a", "a"), List.of("a", "a", "")));
        assertFalse(fits(List.of("a", "a"), List.of("a", "b", "")),
                "ein Platz kann nicht zweimal zählen");
    }

    @Test
    @DisplayName("Die gierige Zuordnung verbaut sich — diese nicht")
    void greedyWouldFail() {
        // Der Stern greift zuerst und nimmt gierig das „b" — dann findet die
        // zweite Forderung nichts mehr, obwohl „a" für den Stern dagewesen
        // wäre. Er muss zurücktreten können.
        assertTrue(fits(List.of("*", "b"), List.of("b", "a", "")),
                "der Stern muss zurücktreten können");
        assertTrue(fits(List.of("*", "*", "c"), List.of("a", "c", "b")));

        // Und die Gegenprobe zur Zeile darüber: Mit nur einem belegten Platz
        // gibt es für zwei Forderungen nichts zu verteilen.
        assertFalse(fits(List.of("*", "b"), List.of("b", "", "")),
                "zwei Forderungen brauchen zwei belegte Plätze");
    }

    @Test
    @DisplayName("Die Zuordnung sagt auch, aus welchem Platz was kommt")
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
    @DisplayName("Mehr Forderungen als Plätze gehen nie auf")
    void tooManyDemands() {
        assertFalse(fits(List.of("a", "b", "c", "d"), List.of("a", "b", "c")));
    }
}
