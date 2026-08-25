package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Grenzen für Nutzercode, und was ohne geladene Konfiguration gilt.
 *
 * <p><b>Der zweite Fall ist der wichtigere.</b> Ein Einheitstest lädt keine
 * Konfigurationsdatei, ein Datengenerator auch nicht, und ein Wert, der dann
 * wirft, macht aus einer Einstellung einen Absturz an Stellen, die mit
 * Einstellungen nichts zu tun haben.
 */
class FnConfigTest {

    @Test
    @DisplayName("Ohne geladene Konfiguration gelten die Vorgaben")
    void withoutAloadedConfigTheDefaultsApply() {
        assertEquals(FnConfig.DEFAULT_STEP_BUDGET, FnConfig.stepBudget());
        assertEquals(FnConfig.DEFAULT_NETWORK_NODES, FnConfig.networkNodes());
    }

    @Test
    @DisplayName("Die Vorgaben sind die Zahlen, die vorher im Code standen")
    void thedefaultsAreTheNumbersThatUsedToBeInTheCode() {
        // Eine Konfiguration darf das Spiel nicht nebenbei ändern: Wer nichts
        // einstellt, bekommt genau das, was die Mod vorher tat.
        assertEquals(10_000, FnConfig.DEFAULT_STEP_BUDGET);
        assertEquals(4_096, FnConfig.DEFAULT_NETWORK_NODES);
    }

    @Test
    @DisplayName("Es gibt eine Serverkonfiguration")
    void thereIsAserverConfig() {
        assertTrue(FnConfig.SERVER_SPEC != null, "die Angabe fehlt");
    }
}
