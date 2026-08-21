package dev.devpanda.factorynetwork.docs;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Beispiele in der Doku müssen laufen.
 *
 * <p>Doku veraltet still: Wer ein Beispiel abschreibt und eine Fehlermeldung
 * bekommt, glaubt beim nächsten Mal keinem mehr. Deshalb liest dieser Test die
 * Programme aus {@code docs/beispiele.md} und übersetzt sie.
 *
 * <p>Bruchstücke, die absichtlich für sich stehen — eine einzelne
 * {@code filter}-Zeile etwa —, werden übersprungen: Sie beginnen nicht mit
 * einem Deklarationswort.
 */
class BeispieleTest {

    private static final List<String> DECLARATIONS =
            List.of("worker ", "fn ", "on ", "event ", "display ", "multiblock ", "group ");

    @Test
    @DisplayName("Jedes Programm in beispiele.md übersetzt fehlerfrei")
    void everyExampleCompiles() throws IOException {
        Path file = Path.of("docs", "beispiele.md");
        assertTrue(Files.exists(file), "docs/beispiele.md fehlt");
        String content = Files.readString(file, StandardCharsets.UTF_8);

        List<String> programs = fencedBlocks(content).stream()
                .filter(BeispieleTest::isProgram)
                .toList();
        assertFalse(programs.isEmpty(), "Kein einziges Programm gefunden — stimmt das Format?");

        for (String program : programs) {
            List<Diagnostic> errors = Parser.parse(program).diagnostics().stream()
                    .filter(Diagnostic::isError)
                    .toList();
            assertTrue(errors.isEmpty(),
                    () -> "Dieses Beispiel übersetzt nicht:\n" + program + "\n" + errors);
        }
    }

    /** Alles zwischen zwei Zeilen aus drei Rückstrichen. */
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
        return DECLARATIONS.stream().anyMatch(trimmed::startsWith);
    }
}
