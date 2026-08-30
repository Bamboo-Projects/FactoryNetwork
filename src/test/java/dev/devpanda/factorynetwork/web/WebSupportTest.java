package dev.devpanda.factorynetwork.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ohne MCEF sagt die Runtime das — und nimmt nichts mit.
 *
 * <p><b>Dieser Prüflauf steht genau in der Lage des Spielers ohne MCEF.</b>
 * Im Prüfstand liegt die Mod nicht auf dem Klassenpfad: {@code compileOnly}
 * reicht zum Übersetzen und nicht zum Laufen. Was hier passiert, passiert also
 * auch bei jedem, der die freiwillige Abhängigkeit nicht installiert hat.
 *
 * <p>Die Zusicherung, um die es geht, ist die wichtigste der ganzen Runtime:
 * Eine fehlende Browser-Laufzeit kostet eine Oberfläche und nicht den Client.
 */
class WebSupportTest {

    @BeforeEach
    @AfterEach
    void reset() {
        WebRuntime.shutdown();
    }

    @Test
    @DisplayName("Ohne MCEF gibt es einen Zustand statt eines Absturzes")
    void withoutMcefThereIsAnAnswerInsteadOfACrash() {
        WebRuntimeStatus status = WebSupport.ensureStarted();

        assertNotNull(status);
        assertFalse(status.usable(), "ohne MCEF kann nichts nutzbar sein");
        assertFalse(WebRuntime.isAvailable());

        // Welcher der beiden Gründe es wird, hängt daran, ob im Prüfstand ein
        // FML danebensteht: Ohne FML kann niemand die Modliste fragen, und
        // dann fällt der Griff auf die MCEF-Klasse selbst — als Error, nicht
        // als Exception. Beide Wege sind vorgesehen, beide enden hier.
        assertTrue(status.state() == WebRuntimeState.MOD_MISSING
                        || status.state() == WebRuntimeState.FAILED,
                "unerwarteter Zustand: " + status);
    }

    @Test
    @DisplayName("Und man kann es danach gefahrlos noch einmal versuchen")
    void tryingAgainIsHarmless() {
        WebSupport.ensureStarted();
        WebRuntimeStatus second = WebSupport.retry();

        assertFalse(second.usable());
        assertFalse(WebRuntime.isAvailable());
    }
}
