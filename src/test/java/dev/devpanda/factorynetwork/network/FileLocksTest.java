package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wer welche Datei halten darf.
 *
 * <p>Die Regeln sind klein und greifen ineinander: nehmen durch Schreiben,
 * verfallen durch Warten, freigeben durch Schließen. Jede einzeln geprüft —
 * ein Fehler darin heißt entweder, dass zwei Leute einander überschreiben,
 * oder dass einer vor einer Datei steht, die niemand mehr offen hat.
 *
 * <p>Ohne Server: {@link FileLocks} kennt nur eine Kennung und einen Namen.
 * Ein echter {@code ServerPlayer} bräuchte eine Welt, einen Server und ein
 * halbes Spiel — und der erste Anlauf dieses Tests scheiterte genau daran.
 */
class FileLocksTest {

    private static final java.util.UUID ANNA = java.util.UUID.randomUUID();
    private static final java.util.UUID BERT = java.util.UUID.randomUUID();

    @Test
    @DisplayName("Wer zuerst schreibt, hält die Datei")
    void theFirstWriterHoldsIt() {
        FileLocks locks = new FileLocks();
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 0));
        assertFalse(locks.claim("main.mf", BERT, "Bert", 5),
                "Bert darf nicht in Annas Datei schreiben");
        assertTrue(locks.claim("worker.mf", BERT, "Bert", 5),
                "eine andere Datei geht sehr wohl");
    }

    @Test
    @DisplayName("Der Halter darf weiter schreiben")
    void theHolderKeepsWriting() {
        FileLocks locks = new FileLocks();
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 0));
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 5000));
    }

    @Test
    @DisplayName("Eine Sperre verfällt, wenn eine Weile nichts kam")
    void aLockExpires() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        assertFalse(locks.claim("main.mf", BERT, "Bert", 20 * 30),
                "nach einer halben Minute noch nicht");
        assertTrue(locks.claim("main.mf", BERT, "Bert", 20 * 61), "nach einer Minute schon");
    }

    @Test
    @DisplayName("Beim Schließen wird alles frei")
    void closingReleasesEverything() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        locks.claim("worker.mf", ANNA, "Anna", 0);
        locks.release(ANNA);

        assertTrue(locks.claim("main.mf", BERT, "Bert", 1));
        assertTrue(locks.claim("worker.mf", BERT, "Bert", 1));
    }

    @Test
    @DisplayName("Die eigenen Sperren sind keine Nachricht")
    void yourOwnLocksAreNotReported() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        locks.claim("worker.mf", BERT, "Bert", 0);

        assertEquals(java.util.Map.of("worker.mf", "Bert"), locks.othersFor(ANNA, 1));
        assertEquals(java.util.Map.of("main.mf", "Anna"), locks.othersFor(BERT, 1));
    }
}
