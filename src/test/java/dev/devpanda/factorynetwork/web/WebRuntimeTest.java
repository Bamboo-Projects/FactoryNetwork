package dev.devpanda.factorynetwork.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nichts an der Runtime wirft — jeder Weg endet in einem Zustand.
 *
 * <p>Das ist die Zusicherung, unter der eine Browser-Laufzeit überhaupt in
 * diese Mod darf. Ein Prüflauf dafür ist billig und wäre teuer nachzuholen:
 * Der Fall, den man nicht prüft, ist der, bei dem jemandem der Client nicht
 * mehr startet.
 *
 * <p>MCEF wird hier nie angefasst. Der Unterbau kommt als Lambda herein, und
 * genau deshalb ist diese Schicht ohne Minecraft prüfbar — Chromium in einem
 * gewöhnlichen Prüflauf hochzufahren wäre weder möglich noch sinnvoll.
 */
class WebRuntimeTest {

    /** Ein Unterbau, der nichts tut außer mitzuzählen. */
    private static final class FakeBackend implements WebBackend {
        private final AtomicInteger closes = new AtomicInteger();
        private final RuntimeException failOnClose;

        FakeBackend() {
            this(null);
        }

        FakeBackend(RuntimeException failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public String name() {
            return "Attrappe";
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            if (failOnClose != null) {
                throw failOnClose;
            }
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        WebRuntime.shutdown();
    }

    @Test
    @DisplayName("Vor dem ersten Versuch ist nichts geschehen")
    void beforeAnythingHappens() {
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
        assertFalse(WebRuntime.isAvailable());
        assertNull(WebRuntime.backend());
    }

    @Test
    @DisplayName("Ein Unterbau, der hochkommt, macht die Runtime nutzbar")
    void aWorkingBackendMakesItUsable() {
        FakeBackend backend = new FakeBackend();
        WebRuntimeStatus status = WebRuntime.start(() -> backend);

        assertEquals(WebRuntimeState.READY, status.state());
        assertTrue(WebRuntime.isAvailable());
        assertEquals(backend, WebRuntime.backend());
    }

    @Test
    @DisplayName("Ein vorhergesehener Grund kommt unverändert durch")
    void aKnownReasonSurvives() {
        WebRuntimeStatus status = WebRuntime.start(() -> {
            throw new WebRuntimeUnavailable(WebRuntimeState.MOD_MISSING,
                    "MCEF liegt nicht in diesem Pack");
        });

        assertEquals(WebRuntimeState.MOD_MISSING, status.state());
        assertEquals("MCEF liegt nicht in diesem Pack", status.reason());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Auch ein NoClassDefFoundError bringt nur einen Zustand zurück")
    void evenAnErrorIsJustAState() {
        // Genau das passiert, wenn MCEF fehlt und trotzdem jemand die Klasse
        // anfasst: kein Exception, sondern ein Error. Wer nur Exception fängt,
        // nimmt den Client mit.
        WebRuntimeStatus status = WebRuntime.start(() -> {
            throw new NoClassDefFoundError("com/cinemamod/mcef/MCEF");
        });

        assertEquals(WebRuntimeState.FAILED, status.state());
        assertTrue(status.reason().contains("NoClassDefFoundError"),
                "der Grund muss im Text stehen, sonst sucht jemand blind");
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Ein Unterbau, der still null liefert, gilt als gescheitert")
    void aSilentNullCounts() {
        WebRuntimeStatus status = WebRuntime.start(() -> null);

        assertEquals(WebRuntimeState.FAILED, status.state());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Zweimal starten versucht es nur einmal")
    void startingTwiceTriesOnce() {
        AtomicInteger attempts = new AtomicInteger();
        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });
        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });

        assertEquals(1, attempts.get(),
                "sonst baut jeder Aufruf einen zweiten Browserprozess auf");
    }

    @Test
    @DisplayName("Ein zweiter Versuch lohnt nur, wenn der Grund sich ändern kann")
    void retryingOnlyWhereItCanHelp() {
        AtomicInteger attempts = new AtomicInteger();

        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            throw new WebRuntimeUnavailable(WebRuntimeState.UNSUPPORTED, "keine Binärdateien");
        });
        WebRuntime.retry(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });

        assertEquals(1, attempts.get(),
                "eine fehlende Plattform taucht auch beim zehnten Mal nicht auf");
        assertEquals(WebRuntimeState.UNSUPPORTED, WebRuntime.status().state());
    }

    @Test
    @DisplayName("Nach einem Fehlschlag darf man es erneut versuchen")
    void afterAFailureRetryingWorks() {
        WebRuntime.start(() -> {
            throw new WebRuntimeUnavailable(WebRuntimeState.NOT_DOWNLOADED, "kein Netz");
        });
        assertTrue(WebRuntime.status().state().worthRetrying());

        WebRuntimeStatus second = WebRuntime.retry(FakeBackend::new);
        assertEquals(WebRuntimeState.READY, second.state(),
                "ein nachgeholter Download muss ohne Neustart wirken können");
    }

    @Test
    @DisplayName("Herunterfahren schließt den Unterbau und setzt zurück")
    void shutdownReleasesEverything() {
        FakeBackend backend = new FakeBackend();
        WebRuntime.start(() -> backend);
        WebRuntime.shutdown();

        assertEquals(1, backend.closes.get());
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Ein Unterbau, der beim Schließen wirft, reißt nichts mit")
    void aThrowingCloseIsContained() {
        FakeBackend angry = new FakeBackend(new IllegalStateException("kaputt"));
        WebRuntime.start(() -> angry);

        WebRuntime.shutdown();

        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state(),
                "beim Beenden des Spiels ist eine Ausnahme aus einem Browser "
                        + "das Letzte, was jemand sehen will");
    }

    @Test
    @DisplayName("Herunterfahren ohne Start tut nichts")
    void shutdownWithoutStartIsHarmless() {
        WebRuntime.shutdown();
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
    }
}
