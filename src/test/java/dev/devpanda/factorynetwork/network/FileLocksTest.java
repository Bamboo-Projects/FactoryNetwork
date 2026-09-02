package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may hold which file.
 *
 * <p>The rules are small and interlock: take by writing, expire by waiting,
 * release by closing. Each tested on its own — an error in it means either
 * that two people overwrite one another, or that someone stands before a file
 * that no one has open any more.
 *
 * <p>Without a server: {@link FileLocks} knows only an id and a name. A real
 * {@code ServerPlayer} would need a world, a server and half a game — and the
 * first attempt at this test failed on exactly that.
 */
class FileLocksTest {

    private static final java.util.UUID ANNA = java.util.UUID.randomUUID();
    private static final java.util.UUID BERT = java.util.UUID.randomUUID();

    @Test
    @DisplayName("Whoever writes first holds the file")
    void theFirstWriterHoldsIt() {
        FileLocks locks = new FileLocks();
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 0));
        assertFalse(locks.claim("main.mf", BERT, "Bert", 5),
                "Bert darf nicht in Annas Datei schreiben");
        assertTrue(locks.claim("worker.mf", BERT, "Bert", 5),
                "eine andere Datei geht sehr wohl");
    }

    @Test
    @DisplayName("The holder may keep writing")
    void theHolderKeepsWriting() {
        FileLocks locks = new FileLocks();
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 0));
        assertTrue(locks.claim("main.mf", ANNA, "Anna", 5000));
    }

    @Test
    @DisplayName("A lock expires when nothing came for a while")
    void aLockExpires() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        assertFalse(locks.claim("main.mf", BERT, "Bert", 20 * 30),
                "nach einer halben Minute noch nicht");
        assertTrue(locks.claim("main.mf", BERT, "Bert", 20 * 61), "nach einer Minute schon");
    }

    @Test
    @DisplayName("On closing everything is released")
    void closingReleasesEverything() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        locks.claim("worker.mf", ANNA, "Anna", 0);
        locks.release(ANNA);

        assertTrue(locks.claim("main.mf", BERT, "Bert", 1));
        assertTrue(locks.claim("worker.mf", BERT, "Bert", 1));
    }

    @Test
    @DisplayName("Your own locks are not a message")
    void yourOwnLocksAreNotReported() {
        FileLocks locks = new FileLocks();
        locks.claim("main.mf", ANNA, "Anna", 0);
        locks.claim("worker.mf", BERT, "Bert", 0);

        assertEquals(java.util.Map.of("worker.mf", "Bert"), locks.othersFor(ANNA, 1));
        assertEquals(java.util.Map.of("main.mf", "Anna"), locks.othersFor(BERT, 1));
    }
}
