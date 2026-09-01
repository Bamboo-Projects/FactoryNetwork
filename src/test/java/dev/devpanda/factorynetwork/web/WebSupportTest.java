package dev.devpanda.factorynetwork.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ohne Chromium sagt die Runtime das — und nimmt nichts mit.
 *
 * <p><b>Dieser Prüflauf steht genau in der Lage des Spielers ohne
 * Laufzeitumgebung.</b> Im Prüfstand liegt {@code org.cef} nicht auf dem
 * Klassenpfad: Das gebaute {@code jcef.jar} ist {@code compileOnly}, und das
 * reicht zum Übersetzen und nicht zum Laufen. Was hier passiert, passiert also
 * auch bei jedem, neben dessen Spiel die Laufzeitumgebung fehlt.
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
    @DisplayName("Ohne Laufzeitumgebung gibt es einen Zustand statt eines Absturzes")
    void withoutRuntimeThereIsAnAnswerInsteadOfACrash() {
        WebRuntimeStatus status = WebSupport.ensureStarted();

        assertNotNull(status);
        assertFalse(status.usable(), "ohne Laufzeitumgebung kann nichts nutzbar sein");
        assertFalse(WebRuntime.isAvailable());

        // Welcher der beiden Gründe es wird, hängt daran, wie weit der Griff
        // kommt: Wer den fehlenden Ordner sieht, meldet ihn; wer stattdessen
        // eine Klasse aus org.cef anfasst, bekommt einen Error und keine
        // Exception. Beide Wege sind vorgesehen, beide enden hier.
        assertTrue(status.state() == WebRuntimeState.RUNTIME_MISSING
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
