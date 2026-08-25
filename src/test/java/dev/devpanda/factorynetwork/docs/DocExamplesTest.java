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
 * Die Beispiele in der Doku müssen laufen.
 *
 * <p>Doku veraltet still: Wer ein Beispiel abschreibt und eine Fehlermeldung
 * bekommt, glaubt beim nächsten Mal keinem mehr.
 *
 * <p><b>Geprüft wird der Text, den ein Spieler zuerst liest.</b> Lange stand
 * hier nur {@code docs/beispiele.md} — der Guide im Spiel und die Titelseite
 * waren ungeprüft, und genau dort standen die falschen Beispiele: ein
 * Fortschrittsbalken, der immer leer blieb, und ein Gruppenaufruf, den es
 * nicht gibt.
 *
 * <p><b>Auch Warnungen zählen.</b> Ein {@code on inventory_changed} übersetzt
 * fehlerfrei und läuft nie — es gibt dieses Ereignis nicht. In einem Beispiel
 * ist das genauso falsch wie ein Syntaxfehler, nur stiller.
 *
 * <p>Was hier nicht auffällt, ist alles, was erst beim Ausführen scheitert:
 * {@code pumps.stop()} übersetzt tadellos. Dafür gibt es die GameTests, die
 * die Beispiele wirklich laufen lassen.
 *
 * <p>Bruchstücke, die absichtlich für sich stehen — eine einzelne
 * {@code filter}-Zeile etwa —, werden übersprungen: Sie beginnen nicht mit
 * einem Deklarationswort.
 */
class DocExamplesTest {

    private static final List<String> DECLARATIONS = List.of(
            "worker ", "fn ", "on ", "event ", "display ", "multiblock ", "group ", "global ",
            // const fehlte hier, seit es das Wort gibt: Ein Beispiel, das
            // damit anfängt, wurde übersprungen statt geprüft.
            "const ");

    /**
     * Die Dateien, deren Beispiele stimmen müssen.
     *
     * <p>Nicht alle {@code docs/}: {@code konzept.md} und die Spezifikation
     * beschreiben auch, was noch nicht gebaut ist. Ein Entwurf darf einer
     * sein — die Titelseite und der Guide im Spiel dürfen es nicht.
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
    @DisplayName("Jedes Programm in Titelseite, Beispielen und Guide übersetzt sauber")
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
        // <b>filter ist zweideutig</b>, und deshalb steht es nicht in der
        // Liste oben: „filter erze {" erklärt eine Vorlage, „filter
        // tag:c/ores" ist die Angabe eines Workers, aus dem Zusammenhang
        // gerissen. Nur die erste Form ist ein Programm — die zweite ist ein
        // Bruchstück, und ein Bruchstück muss nicht für sich übersetzen.
        if (trimmed.startsWith("filter ")) {
            return trimmed.lines().findFirst().orElse("").stripTrailing().endsWith("{");
        }
        return DECLARATIONS.stream().anyMatch(trimmed::startsWith);
    }
}
