package dev.devpanda.factorynetwork.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hält den Rechenfehler fest, den erst ein GameTest gefunden hat: Die
 * Distanz griff auf die Zelle darüber statt auf die Diagonale zu und
 * überschätzte damit jeden Abstand. Ein GameTest dafür dauert eine Minute,
 * dieser hier Millisekunden.
 */
class NameDistanceTest {

    @Test
    @DisplayName("Ein fehlender Buchstabe ist Abstand 1")
    void oneMissingLetter() {
        assertEquals(1, NameDistance.between("quary_output", "quarry_output"));
    }

    @Test
    @DisplayName("Ein vertauschter Buchstabe ist Abstand 1")
    void oneWrongLetter() {
        assertEquals(1, NameDistance.between("crusher_1", "crusher_2"));
    }

    @Test
    @DisplayName("Gleiche Namen haben Abstand 0")
    void identical() {
        assertEquals(0, NameDistance.between("crusher_1", "crusher_1"));
    }

    @Test
    @DisplayName("Zwei vertauschte Buchstaben sind ein Schritt")
    void transposition() {
        assertEquals(1, NameDistance.between("crusehr_1", "crusher_1"));
    }

    @Test
    @DisplayName("Bei kurzen Namen entscheidet der Dreher über den Vorschlag")
    void transpositionMattersForShortNames() {
        // Vorher kostete ein Dreher zwei Schritte. Bei „halle" — fünf
        // Zeichen, also ein Schritt Spielraum — fiel der Vorschlag damit
        // aus, und zwar genau in dem Fall, für den es ihn gibt.
        int abstand = NameDistance.between("halel", "halle");
        assertEquals(1, abstand);
        assertTrue(NameDistance.isCloseEnough("halel", abstand));
    }

    @Test
    void emptyAgainstFull() {
        assertEquals(5, NameDistance.between("", "chest"));
    }

    @Test
    @DisplayName("Bei kurzen Namen reicht ein Buchstabe Abstand")
    void shortNamesAreStrict() {
        assertTrue(NameDistance.isCloseEnough("ofen", 1));
        assertFalse(NameDistance.isCloseEnough("ofen", 2));
    }

    @Test
    @DisplayName("Bei langen Namen darf mehr danebengehen")
    void longNamesAreLenient() {
        assertTrue(NameDistance.isCloseEnough("quarry_output_north", 6));
    }
}
