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
 * Der Ordner neben der Welt, ohne Welt.
 *
 * <p>Geprüft wird der Weg hin und zurück, und dabei vor allem eines: dass ein
 * Name im Ordner derselbe ist wie im Projekt. Auf Windows heißt der Pfad
 * {@code erz\brecher.mf} und im Projekt {@code erz/brecher.mf} — wer das nicht
 * angleicht, bekommt eine Brücke, die im Sekundentakt zwei Wahrheiten
 * gegeneinander schreibt.
 */
class ProgramFolderTest {

    /**
     * Ein Wegwerfordner für die Tests, die keinen eigenen brauchen.
     *
     * <p>Als Feld und nicht als Parameter: Die Prüfungen der Statusdatei
     * arbeiten alle im selben Ordner, und ein Parameter je Test wäre in jeder
     * Signatur dasselbe Wort.
     */
    @TempDir
    Path dir;

    @Test
    @DisplayName("Was geschrieben wurde, kommt genauso zurück")
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
    @DisplayName("Eine Datei im Unterordner landet im Unterordner")
    void afileInAsubfolderLandsInAsubfolder(@TempDir Path dir) {
        ProgramFolder.at(dir).write(new Project(Map.of("erz/brecher.mf", "fn b() {}")));

        assertTrue(Files.exists(dir.resolve("erz").resolve("brecher.mf")),
                "die Datei muss im Ordner erz liegen");
    }

    @Test
    @DisplayName("Ein Name aus dem Ordner trägt Schrägstriche, keine Rückstriche")
    void anameFromTheFolderCarriesSlashes(@TempDir Path dir) throws IOException {
        // Von Hand angelegt, so wie es ein Editor täte — und nicht über
        // write, das den Namen ja selbst gesetzt hat.
        Files.createDirectories(dir.resolve("erz"));
        Files.writeString(dir.resolve("erz").resolve("brecher.mf"), "fn b() {}",
                StandardCharsets.UTF_8);

        Map<String, String> found = ProgramFolder.at(dir).read();

        assertEquals(List.of("erz/brecher.mf"), List.copyOf(found.keySet()));
    }

    @Test
    @DisplayName("Wer von außen schreibt, wird beim Nachsehen bemerkt")
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
    @DisplayName("Was nicht ins Muster passt, wird übergangen")
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
    @DisplayName("Eine fremde Datei im Ordner überlebt das Schreiben")
    void aforeignFileSurvivesAwrite() throws IOException {
        // Der Ordner gehört nicht allein diesem Projekt: Daneben liegt die
        // Statusdatei für VS Code, und jemand legt vielleicht Notizen ab.
        // Geprüft war bisher nur, dass sie beim Lesen übergangen werden.
        ProgramFolder folder = ProgramFolder.at(dir);
        Files.writeString(dir.resolve("Notizen.txt"), "meins", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(".fn-status.json"), "{}", StandardCharsets.UTF_8);

        folder.write(new Project(Map.of("main.mf", "fn a() {}")));

        assertTrue(Files.exists(dir.resolve("Notizen.txt")), "die Notiz muss bleiben");
        assertTrue(Files.exists(dir.resolve(".fn-status.json")), "und die Statusdatei auch");
    }

    // ---- Der Zustand für VS Code -------------------------------------------

    @Test
    @DisplayName("Die Statusdatei trägt Fehler mit Datei, Zeile und Spalte")
    void thestatusFileCarriesErrorsWithFileLineAndColumn() {
        // Der Weg zurück: VS Code speichert, das Spiel übersetzt, und was der
        // Übersetzer sagt, kommt hier an. Ohne das sieht ein Fehler nur, wer
        // im Spiel ins Terminal schaut.
        List<Diagnostic> problems = List.of(new Diagnostic(Diagnostic.Severity.ERROR,
                new Span(10, 18, 3, 5), "Nichts im Netz heißt kist.",
                "Meintest du kiste?", "erz/brecher.mf"));

        ProgramStatus.write(dir, problems, List.of("kiste", "ofen"), List.of("halle"),
                List.of("item", "fluid", "source"));

        ProgramStatus back = ProgramStatus.read(dir);
        assertEquals(List.of("kiste", "ofen"), back.connectors());
        assertEquals(List.of("halle"), back.displays());
        // Die Präfixe kommen aus dem Spiel: Was eine fremde Mod anmeldet,
        // kann ein Editor daneben nicht wissen.
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
    @DisplayName("Ohne Fehler steht eine leere Liste da, keine fehlende Datei")
    void withoutErrorsAnemptyListStandsThere() {
        // Sonst wüsste die Erweiterung nicht, ob alles stimmt oder ob nur
        // niemand nachgesehen hat — und ließe alte Fehler stehen.
        ProgramStatus.write(dir, List.of(), List.of(), List.of(), List.of());

        ProgramStatus back = ProgramStatus.read(dir);
        assertTrue(back.diagnostics().isEmpty(), "keine Fehler");
        assertTrue(back.connectors().isEmpty(), "und keine Geräte");
    }

    @Test
    @DisplayName("Zweimal dasselbe schreibt die Datei nicht zweimal")
    void twiceThesameDoesNotWriteTwice() throws IOException {
        // Ein Datei-Wächter in VS Code weckt sonst jede Sekunde die
        // Erweiterung, obwohl sich nichts geändert hat.
        ProgramStatus.write(dir, List.of(), List.of("kiste"), List.of(), List.of());
        java.nio.file.attribute.FileTime first =
                Files.getLastModifiedTime(dir.resolve(ProgramStatus.FILE));

        ProgramStatus.write(dir, List.of(), List.of("kiste"), List.of(), List.of());

        assertEquals(first, Files.getLastModifiedTime(dir.resolve(ProgramStatus.FILE)),
                "dieselbe Auskunft, dieselbe Datei");
    }

    @Test
    @DisplayName("Eine Statusdatei, die es nicht gibt, ist leer und kein Fehler")
    void amissingStatusFileIsEmpty() {
        ProgramStatus back = ProgramStatus.read(dir);

        assertTrue(back.diagnostics().isEmpty());
        assertTrue(back.connectors().isEmpty());
    }

    @Test
    @DisplayName("Eine gelöschte Datei verschwindet auch aus dem Unterordner")
    void adeletedFileAlsoLeavesThesubfolder(@TempDir Path dir) {
        ProgramFolder folder = ProgramFolder.at(dir);
        folder.write(new Project(Map.of("main.mf", "", "erz/brecher.mf", "fn b() {}")));

        folder.write(new Project(Map.of("main.mf", "")));

        assertTrue(!Files.exists(dir.resolve("erz").resolve("brecher.mf")),
                "die Datei muss weg sein");
        // Der leere Ordner bleibt liegen. Ihn wegzuräumen hieße, im
        // Weltordner aufzuräumen, was jemand anderes angelegt haben könnte —
        // und ein leerer Ordner tut niemandem weh.
        assertTrue(Files.exists(dir.resolve("erz")), "der leere Ordner darf bleiben");
    }
}
