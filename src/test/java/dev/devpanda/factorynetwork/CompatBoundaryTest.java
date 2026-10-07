package dev.devpanda.factorynetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core does not know the mods it is compatible with.
 *
 * <p><b>The direction is one way.</b> What lives under {@code compat} may use
 * everything; nothing outside may reach in. A foreign mod that wants to extend
 * Factory Network goes through the same door as the adapters shipped here —
 * and that door is only proven while the shipped adapters have no other way
 * in.
 *
 * <p><b>The list below is a ratchet, not a permission.</b> It names the files
 * that still reach into {@code compat} today. A new file showing up fails the
 * run, and so does a listed file that no longer reaches in: the list has to
 * shrink with the code, and an empty list is the goal.
 *
 * <p><b>One file stays for good:</b> the mod's entry point. Somebody has to
 * tell each adapter to register itself, and that is the one place whose job
 * is to know what gets wired together.
 */
class CompatBoundaryTest {

    private static final Path ROOT = Path.of("src/main/java/dev/devpanda/factorynetwork");
    private static final Path COMPAT = ROOT.resolve("compat");
    private static final String REACH = "dev.devpanda.factorynetwork.compat.";

    /** The entry point wires the adapters; see the class documentation. */
    private static final String ENTRY_POINT = "FactoryNetwork.java";

    /** Still to be moved behind the public interface. Shrinks, never grows. */
    private static final Set<String> STILL_REACHING_IN = Set.of(
            "block/DriveBlock.java",
            "block/entity/ControllerBlockEntity.java",
            "runtime/ResourceKinds.java",
            "storage/ChemicalCellItem.java");

    private static Set<String> filesReachingIn() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(ROOT)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(COMPAT)).toList()) {
                if (Files.readString(source, StandardCharsets.UTF_8).contains(REACH)) {
                    found.add(ROOT.relativize(source).toString().replace('\\', '/'));
                }
            }
        }
        found.remove(ENTRY_POINT);
        return found;
    }

    @Test
    @DisplayName("There are sources to check, and the compat directory exists")
    void thereIsSomethingToCheck() throws IOException {
        assertTrue(Files.isDirectory(COMPAT),
                "without the compat directory this run checks nothing");
        try (Stream<Path> sources = Files.walk(ROOT)) {
            assertTrue(sources.filter(p -> p.toString().endsWith(".java")).count() > 100,
                    "the walk found barely any sources; the path is probably wrong");
        }
    }

    @Test
    @DisplayName("Only the listed files still reach into compat, and each of them still does")
    void theListMatchesTheCode() throws IOException {
        assertEquals(new TreeSet<>(STILL_REACHING_IN), filesReachingIn(),
                "A file missing on the right has been cleaned up: remove it from the list. "
                        + "A file missing on the left reaches into compat anew: it has to go "
                        + "through the public interface instead.");
    }
}
