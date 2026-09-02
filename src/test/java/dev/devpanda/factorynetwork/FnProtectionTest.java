package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may change a program.
 *
 * <p>The question stands without a world — it is a computation from three
 * inputs and not a matter of blocks. That is why it is here and not in the
 * GameTests: a permission error is the one error you do not want to try out.
 */
class FnProtectionTest {

    private static final UUID BESITZER = UUID.nameUUIDFromBytes("besitzer".getBytes());
    private static final UUID FREMDER = UUID.nameUUIDFromBytes("fremder".getBytes());

    @Test
    @DisplayName("Without protection everyone may — as before")
    void withoutProtectionEveryoneMay() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OFF, BESITZER, FREMDER, false));
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OFF, null, FREMDER, false));
    }

    @Test
    @DisplayName("With owner protection the owner may")
    void withOwnerProtectionTheOwnerMay() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, BESITZER, false));
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, FREMDER, false));
    }

    @Test
    @DisplayName("And an operator always")
    void andAnoperatorAlways() {
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, BESITZER, FREMDER, true));
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OPS, BESITZER, FREMDER, true));
    }

    @Test
    @DisplayName("With OPS ownership is not enough")
    void withOpsOwnershipIsNotEnough() {
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OPS, BESITZER, BESITZER, false));
    }

    @Test
    @DisplayName("A controller without an owner belongs to everyone")
    void acontrollerWithoutAnownerBelongsToEveryone() {
        // From an earlier world, or set by a command: assigning it to nobody
        // would be a lock that no one can lift.
        assertTrue(FnProtection.mayEdit(FnProtection.Mode.OWNER, null, FREMDER, false));
        // With OPS this does not hold: there ownership is not the question at all.
        assertFalse(FnProtection.mayEdit(FnProtection.Mode.OPS, null, FREMDER, false));
    }
}
