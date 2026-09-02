package dev.devpanda.factorynetwork.docs;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples in the docs must run.
 *
 * <p>Docs age silently: whoever copies an example and gets an error message
 * does not believe any of them the next time.
 *
 * <p><b>What is checked is the text a player reads first.</b> For a long time
 * only {@code docs/beispiele.md} stood here — the in-game guide and the title
 * page were unchecked, and it was exactly there that the wrong examples
 * stood: a progress bar that always stayed empty, and a group call that does
 * not exist.
 *
 * <p><b>Warnings count too.</b> An {@code on inventory_changed} compiles
 * without error and never runs — this event does not exist. In an example
 * that is just as wrong as a syntax error, only quieter.
 *
 * <p>What does not show up here is everything that only fails at run time:
 * {@code pumps.stop()} compiles flawlessly. For that there are the GameTests,
 * which really run the examples.
 *
 * <p>Fragments that deliberately stand on their own — a single
 * {@code filter} line, say —, are skipped: they do not begin with a
 * declaration word.
 */
class DocExamplesTest {

    private static final List<String> DECLARATIONS = List.of(
            "worker ", "fn ", "on ", "event ", "display ", "multiblock ", "group ", "global ",
            // const was missing here ever since the word exists: an example
            // that begins with it was skipped instead of checked.
            "const ");

    /**
     * The files whose examples must be correct.
     *
     * <p>Not all of {@code docs/}: {@code konzept.md} and the specification
     * also describe what is not built yet. A draft may be one — the title
     * page and the in-game guide must not be.
     */
    private static List<Path> files() throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(Path.of("README.md"));
        files.add(Path.of("docs", "beispiele.md"));
        Path guide = Path.of("src", "main", "resources", "assets", "factorynetwork", "guide");
        try (Stream<Path> pages = Files.list(guide)) {
            pages.filter(page -> page.toString().endsWith(".md")).sorted().forEach(files::add);
        }
        return files;
    }

    @Test
    @DisplayName("Every program in title page, examples and guide compiles cleanly")
    void everyExampleCompiles() throws IOException {
        int checked = 0;
        for (Path file : files()) {
            assertTrue(Files.exists(file), () -> file + " fehlt");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String program : fencedBlocks(content)) {
                if (!isProgram(program)) {
                    continue;
                }
                checked++;
                List<Diagnostic> problems =
                        new Project(Map.of("main.mf", program)).parse().diagnostics();
                assertTrue(problems.isEmpty(),
                        () -> "Dieses Beispiel aus " + file + " stimmt nicht:\n"
                                + program + "\n" + problems);
            }
        }
        assertFalse(checked == 0, "Kein einziges Programm gefunden — stimmt das Format?");
    }

    /** Everything between two lines of three backticks. */
    private static List<String> fencedBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        for (String line : content.split("\r?\n")) {
            if (line.startsWith("```")) {
                if (current == null) {
                    current = new StringBuilder();
                } else {
                    blocks.add(current.toString());
                    current = null;
                }
                continue;
            }
            if (current != null) {
                current.append(line).append('\n');
            }
        }
        return blocks;
    }

    private static boolean isProgram(String block) {
        String trimmed = block.stripLeading();
        // <b>filter is ambiguous</b>, and that is why it does not stand in the
        // list above: "filter erze {" declares a template, "filter
        // tag:c/ores" is the entry of a worker, torn out of context. Only the
        // first form is a program — the second is a fragment, and a fragment
        // need not compile on its own.
        if (trimmed.startsWith("filter ")) {
            return trimmed.lines().findFirst().orElse("").stripTrailing().endsWith("{");
        }
        return DECLARATIONS.stream().anyMatch(trimmed::startsWith);
    }
}
