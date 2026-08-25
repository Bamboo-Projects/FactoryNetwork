package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wer ein Programm ändern darf.
 *
 * <p>Die Frage steht ohne Welt da — sie ist eine Rechnung aus drei Angaben
 * und keine Sache von Blöcken. Deshalb hier und nicht in den GameTests: Ein
 * Rechtefehler ist der eine Fehler, den man nicht ausprobieren möchte.
 */
class FnProtectionTest {

    private static final UUID BESITZER = UUID.nameUUIDFromBytes("besitzer".getBytes());
    private static final UUID FREMDER = UUID.nameUUIDFromBytes("fremder".getBytes());

    @Test
    @DisplayName("Ohne Schutz darf jeder — wie bisher")
    void withoutProtectionEveryoneMay() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OFF, BESITZER, FREMDER, false));
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OFF, null, FREMDER, false));
    }

    @Test
    @DisplayName("Mit Besitzerschutz darf der Besitzer")
    void withOwnerProtectionTheOwnerMay() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, BESITZER, false));
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, FREMDER, false));
    }

    @Test
    @DisplayName("Und ein Operator immer")
    void andAnoperatorAlways() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, FREMDER, true));
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OPS, BESITZER, FREMDER, true));
    }

    @Test
    @DisplayName("Bei OPS reicht Besitz nicht")
    void withOpsOwnershipIsNotEnough() {
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OPS, BESITZER, BESITZER, false));
    }

    @Test
    @DisplayName("Ein Controller ohne Besitzer gehört allen")
    void acontrollerWithoutAnownerBelongsToEveryone() {
        // Aus einer Welt von vorher, oder von einem Befehl gesetzt: Ihn
        // niemandem zuzuordnen wäre eine Sperre, die niemand aufheben kann.
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, null, FREMDER, false));
        // Bei OPS gilt das nicht: Dort ist der Besitz gar nicht die Frage.
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OPS, null, FREMDER, false));
    }
}
