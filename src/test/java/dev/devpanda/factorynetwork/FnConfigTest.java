package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limits for user code, and what applies without a loaded configuration.
 *
 * <p><b>The second case is the more important one.</b> A unit test loads no
 * configuration file, and neither does a data generator, and a value that then
 * throws turns a setting into a crash in places that have nothing to do with
 * settings.
 */
class FnConfigTest {

    @Test
    @DisplayName("Without a loaded configuration the defaults apply")
    void withoutAloadedConfigTheDefaultsApply() {
        assertEquals(FnConfig.DEFAULT_STEP_BUDGET, FnConfig.stepBudget());
        assertEquals(FnConfig.DEFAULT_NETWORK_NODES, FnConfig.networkNodes());
        assertEquals(FnConfig.DEFAULT_CRAFTING_DEPTH, FnConfig.craftingDepth());
        assertEquals(FnConfig.DEFAULT_CRAFTING_BUDGET, FnConfig.craftingBudget());
    }

    @Test
    @DisplayName("The defaults are the numbers that used to be in the code")
    void thedefaultsAreTheNumbersThatUsedToBeInTheCode() {
        // A configuration must not change the game as a side effect: whoever
        // sets nothing gets exactly what the mod did before.
        assertEquals(10_000, FnConfig.DEFAULT_STEP_BUDGET);
        assertEquals(4_096, FnConfig.DEFAULT_NETWORK_NODES);
    }

    @Test
    @DisplayName("There is a server configuration")
    void thereIsAserverConfig() {
        assertTrue(FnConfig.SERVER_SPEC != null, "die Angabe fehlt");
    }
}
