package dev.devpanda.factorynetwork.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interpreter and the flows run without the game.
 *
 * <p><b>Listed by name, because the package is mixed.</b> {@code runtime} also
 * holds what reaches into the world — the host, the workers, the item and
 * fluid selections. The classes below are the ones that must not: they are
 * what executes a program, and they are meant to be carried to another
 * version of the game without a change.
 *
 * <p>What they may use: the language, {@code util}, and each other. Not the
 * game, not its loader, and nothing else of this mod.
 */
class GameFreeRuntimeTest {

    private static final Path RUNTIME =
            Path.of("src/main/java/dev/devpanda/factorynetwork/runtime");

    private static final List<String> GAME_FREE = List.of(
            "Interpreter.java",
            "Kind.java",
            "Kinds.java",
            "LogLevel.java",
            "ScriptError.java",
            "Value.java",
            "flow/BlockIndex.java",
            "flow/Flow.java",
            "flow/FlowEngine.java",
            "flow/Frame.java",
            "flow/Step.java");

    /** The game and its loader. */
    private static final Pattern GAME = Pattern.compile(
            "\\b(net\\.minecraft|net\\.neoforged|com\\.mojang)\\b");

    /** Packages of this mod that reach into the world, named in full. */
    private static final Pattern MOD = Pattern.compile(
            "dev\\.devpanda\\.factorynetwork\\.(?!lang\\b|util\\b|runtime\\b)[A-Za-z]+");

    /** Classes of this very package that reach into the world. */
    private static final Pattern NEIGHBOUR = Pattern.compile(
            "\\b(ResourceKinds?|WorldHost|WorkerRuntime|ItemSelection|FluidSelection"
                    + "|FilterTemplates|ValueCodec|FlowCodec|DisplayValues|Handoffs)\\b");

    @Test
    @DisplayName("None of them names the game, the rest of the mod or a neighbour that does")
    void theyStayFree() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (String name : GAME_FREE) {
            Path source = RUNTIME.resolve(name);
            assertTrue(Files.isRegularFile(source), "missing: " + source);
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String code = line.strip();
                // Documentation may name what it is set apart from.
                if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")) {
                    continue;
                }
                for (Pattern forbidden : List.of(GAME, MOD, NEIGHBOUR)) {
                    Matcher found = forbidden.matcher(line);
                    if (found.find()) {
                        breaches.add("%s:%d — %s".formatted(name, i + 1, found.group()));
                    }
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "These classes execute a program and must not need the game:\n  "
                        + String.join("\n  ", breaches));
    }

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
}
