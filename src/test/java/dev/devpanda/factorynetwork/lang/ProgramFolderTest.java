package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The folder next to the world, without the world.
 *
 * <p>The round trip is checked, and above all one thing: that a
 * name in the folder is the same as in the project. On Windows the path is
 * {@code erz\brecher.mf} and in the project {@code erz/brecher.mf} — whoever does
 * not align that gets a bridge that, once a second, writes two truths
 * against each other.
 */
class ProgramFolderTest {

    /**
     * A throwaway folder for the tests that do not need one of their own.
     *
     * <p>As a field and not a parameter: the status-file checks
     * all work in the same folder, and a parameter per test would be the same
     * word in every signature.
     */
    @TempDir
    Path dir;

    @Test
    @DisplayName("What was written comes back the same")
    void whatWasWrittenComesBackTheSame(@TempDir Path dir) {
        ProgramFolder folder = ProgramFolder.at(dir);
        Project project = new Project(Map.of(
                "main.mf", "fn a() {}",
                "erz/brecher.mf", "fn b() {}",
                "erz/eisen/schmelzen.mf", "fn c() {}"));

        folder.write(project);

        assertEquals(project.files(), folder.read());
    }

    @Test
    @DisplayName("A file in a subfolder lands in the subfolder")
    void afileInAsubfolderLandsInAsubfolder(@TempDir Path dir) {
        ProgramFolder.at(dir).write(new Project(Map.of("erz/brecher.mf", "fn b() {}")));

        assertTrue(Files.exists(dir.resolve("erz").resolve("brecher.mf")),
                "die Datei muss im Ordner erz liegen");
    }

    @Test
    @DisplayName("A name from the folder carries slashes, not backslashes")
    void anameFromTheFolderCarriesSlashes(@TempDir Path dir) throws IOException {
        // Created by hand, the way an editor would — and not via
        // write, which set the name itself.
        Files.createDirectories(dir.resolve("erz"));
        Files.writeString(dir.resolve("erz").resolve("brecher.mf"), "fn b() {}",
                StandardCharsets.UTF_8);

        Map<String, String> found = ProgramFolder.at(dir).read();

        assertEquals(List.of("erz/brecher.mf"), List.copyOf(found.keySet()));
    }

    @Test
    @DisplayName("Whoever writes from outside is noticed on the next look")
    void whoWritesFromOutsideIsNoticed(@TempDir Path dir) throws IOException {
        ProgramFolder folder = ProgramFolder.at(dir);
        Project current = new Project(Map.of("main.mf", "fn a() {}"));
        folder.write(current);
        assertNull(folder.poll(current), "die eigene Schrift löst nichts aus");

        Files.createDirectories(dir.resolve("erz"));
        Files.writeString(dir.resolve("erz").resolve("brecher.mf"), "fn b() {}",
                StandardCharsets.UTF_8);

        Project next = folder.poll(current);
        assertNotNull(next, "die neue Datei muss auffallen");
        assertEquals(List.of("erz/brecher.mf", "main.mf"), next.names());
    }

    @Test
    @DisplayName("What does not fit the pattern is skipped")
    void whatDoesNotFitThepatternIsSkipped(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("main.mf"), "fn a() {}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("Notizen.txt"), "kein Programm",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("Erz"));
        Files.writeString(dir.resolve("Erz").resolve("gross.mf"), "fn b() {}",
                StandardCharsets.UTF_8);

        assertEquals(List.of("main.mf"), List.copyOf(ProgramFolder.at(dir).read().keySet()));
    }

    @Test
    @DisplayName("A foreign file in the folder survives a write")
    void aforeignFileSurvivesAwrite() throws IOException {
        // The folder does not belong to this project alone: next to it lies the
        // status file for VS Code, and someone may drop notes there.
        // So far only that they are skipped on reading was checked.
        ProgramFolder folder = ProgramFolder.at(dir);
        Files.writeString(dir.resolve("Notizen.txt"), "meins", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(".fn-status.json"), "{}", StandardCharsets.UTF_8);

        folder.write(new Project(Map.of("main.mf", "fn a() {}")));

        assertTrue(Files.exists(dir.resolve("Notizen.txt")), "die Notiz muss bleiben");
        assertTrue(Files.exists(dir.resolve(".fn-status.json")), "und die Statusdatei auch");
    }

    // ---- The state for VS Code ---------------------------------------------

    @Test
    @DisplayName("The status file carries errors with file, line and column")
    void thestatusFileCarriesErrorsWithFileLineAndColumn() {
        // The way back: VS Code saves, the game compiles, and what the
        // compiler says arrives here. Without it an error is seen only by whoever
        // looks at the terminal in game.
        List<Diagnostic> problems = List.of(new Diagnostic(Diagnostic.Severity.ERROR,
                new Span(10, 18, 3, 5), "Nichts im Netz heißt kist.",
                "Meintest du kiste?", "erz/brecher.mf"));

        ProgramStatus.write(dir, problems, List.of("kiste", "ofen"), List.of("halle"),
                List.of("item", "fluid", "source"));

        ProgramStatus back = ProgramStatus.read(dir);
        assertEquals(List.of("kiste", "ofen"), back.connectors());
        assertEquals(List.of("halle"), back.displays());
        // The prefixes come from the game: what a foreign mod registers
        // an editor beside it cannot know.
        assertEquals(List.of("item", "fluid", "source"), back.prefixes());
        List<ProgramStatus.Problem> inFile = back.diagnostics().get("erz/brecher.mf");
        assertEquals(1, inFile.size(), () -> back.diagnostics().toString());
        assertEquals(3, inFile.get(0).line());
        assertEquals(5, inFile.get(0).column());
        assertEquals(8, inFile.get(0).length());
        assertEquals("error", inFile.get(0).severity());
        assertTrue(inFile.get(0).message().contains("kist"));
    }

    @Test
    @DisplayName("Without errors an empty list stands there, not a missing file")
    void withoutErrorsAnemptyListStandsThere() {
        // Otherwise the extension would not know whether everything is fine or whether
        // nobody has looked — and would leave old errors standing.
        ProgramStatus.write(dir, List.of(), List.of(), List.of(), List.of());

        ProgramStatus back = ProgramStatus.read(dir);
        assertTrue(back.diagnostics().isEmpty(), "keine Fehler");
        assertTrue(back.connectors().isEmpty(), "und keine Geräte");
    }

    @Test
    @DisplayName("Twice the same does not write the file twice")
    void twiceThesameDoesNotWriteTwice() throws IOException {
        // A file watcher in VS Code would otherwise wake the
        // extension every second, even though nothing has changed.
        ProgramStatus.write(dir, List.of(), List.of("kiste"), List.of(), List.of());
        java.nio.file.attribute.FileTime first =
                Files.getLastModifiedTime(dir.resolve(ProgramStatus.FILE));

        ProgramStatus.write(dir, List.of(), List.of("kiste"), List.of(), List.of());

        assertEquals(first, Files.getLastModifiedTime(dir.resolve(ProgramStatus.FILE)),
                "dieselbe Auskunft, dieselbe Datei");
    }

    @Test
    @DisplayName("A status file that does not exist is empty and not an error")
    void amissingStatusFileIsEmpty() {
        ProgramStatus back = ProgramStatus.read(dir);

        assertTrue(back.diagnostics().isEmpty());
        assertTrue(back.connectors().isEmpty());
    }

    @Test
    @DisplayName("A deleted file also disappears from the subfolder")
    void adeletedFileAlsoLeavesThesubfolder(@TempDir Path dir) {
        ProgramFolder folder = ProgramFolder.at(dir);
        folder.write(new Project(Map.of("main.mf", "", "erz/brecher.mf", "fn b() {}")));

        folder.write(new Project(Map.of("main.mf", "")));

        assertTrue(!Files.exists(dir.resolve("erz").resolve("brecher.mf")),
                "die Datei muss weg sein");
        // The empty folder stays behind. Clearing it away would mean tidying
        // up, inside the world folder, what someone else may have created —
        // and an empty folder hurts nobody.
        assertTrue(Files.exists(dir.resolve("erz")), "der leere Ordner darf bleiben");
    }
}
