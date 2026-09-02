package dev.devpanda.factorynetwork.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the calculation error that only a GameTest found: the distance
 * reached for the cell above instead of the diagonal and thus overestimated
 * every distance. A GameTest for that takes a minute, this one milliseconds.
 */
class NameDistanceTest {

    @Test
    @DisplayName("A missing letter is distance 1")
    void oneMissingLetter() {
        assertEquals(1, NameDistance.between("quary_output", "quarry_output"));
    }

    @Test
    @DisplayName("A swapped letter is distance 1")
    void oneWrongLetter() {
        assertEquals(1, NameDistance.between("crusher_1", "crusher_2"));
    }

    @Test
    @DisplayName("Identical names have distance 0")
    void identical() {
        assertEquals(0, NameDistance.between("crusher_1", "crusher_1"));
    }

    @Test
    @DisplayName("Two swapped letters are one step")
    void transposition() {
        assertEquals(1, NameDistance.between("crusehr_1", "crusher_1"));
    }

    @Test
    @DisplayName("With short names the transposition decides the suggestion")
    void transpositionMattersForShortNames() {
        // Previously a transposition cost two steps. With "halle" — five
        // characters, that is one step of leeway — the suggestion thus fell
        // away, and precisely in the case it exists for.
        int abstand = NameDistance.between("halel", "halle");
        assertEquals(1, abstand);
        assertTrue(NameDistance.isCloseEnough("halel", abstand));
    }

    @Test
    void emptyAgainstFull() {
        assertEquals(5, NameDistance.between("", "chest"));
    }

    @Test
    @DisplayName("With short names one letter of distance is enough")
    void shortNamesAreStrict() {
        assertTrue(NameDistance.isCloseEnough("ofen", 1));
        assertFalse(NameDistance.isCloseEnough("ofen", 2));
    }

    @Test
    @DisplayName("With long names more may go wrong")
    void longNamesAreLenient() {
        assertTrue(NameDistance.isCloseEnough("quarry_output_north", 6));
    }
}
