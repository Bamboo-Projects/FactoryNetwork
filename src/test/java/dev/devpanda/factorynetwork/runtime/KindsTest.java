package dev.devpanda.factorynetwork.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The interpreter's view of the kinds.
 *
 * <p>The interpreter is compiled without the game and cannot name the place
 * where items and fluids are defined. It still has to find them.
 */
class KindsTest {

    @Test
    @DisplayName("The built-in kinds are there when a program is the first thing to run")
    void theBuiltInKindsAreFound() {
        // Nothing in this test touches the registry of kinds. Whoever brings
        // them is found by name; without that, this would depend on which
        // test happened to run before.
        assertNotNull(Kinds.item());
        assertNotNull(Kinds.fluid());
        assertNotNull(Kinds.chemical());
        assertSame(Kinds.item(), Kinds.byTag("sel"));
        assertSame(Kinds.chemical(), Kinds.byTag("chemsel"));
    }

    @Test
    @DisplayName("What the interpreter finds is the kind the registry holds")
    void theSameObjectOnBothSides() {
        assertSame(ResourceKinds.ITEM, Kinds.item());
        assertSame(ResourceKinds.FLUID, Kinds.fluid());
        assertSame(ResourceKinds.CHEMICAL, Kinds.chemical());
    }
}
