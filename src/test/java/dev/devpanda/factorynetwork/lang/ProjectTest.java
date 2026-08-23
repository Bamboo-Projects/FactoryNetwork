package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein Projekt aus mehreren Dateien.
 *
 * <p>Die wichtigste Prüfung hier ist die mit der Zeilennummer: Würde der
 * Übersetzer den zusammengehängten Text bekommen, zeigte jeder Fehler ab der
 * zweiten Datei auf eine Zeile, die es dort nicht gibt — und der Editor
 * markierte die falsche.
 */
class ProjectTest {

    @Test
    @DisplayName("Ein Fehler kennt seine Datei und seine Zeile darin")
    void diagnosticsKnowTheirFile() {
        Project project = new Project(Map.of(
                "aaa.mf", String.join("\n", "fn eins() {", "    let a = 1", "}"),
                "bbb.mf", String.join("\n", "fn zwei() {", "    let b =", "}")));
        List<Diagnostic> problems = project.parse().diagnostics();

        assertFalse(problems.isEmpty(), "der Fehler in bbb.mf fehlt");
        assertTrue(problems.stream().allMatch(problem -> problem.file().equals("bbb.mf")),
                () -> "aaa.mf ist in Ordnung: " + problems);
        assertTrue(problems.stream().allMatch(problem -> problem.span().line() <= 3),
                () -> "die Zeile muss die in bbb.mf sein, nicht die im Gesamttext: "
                        + problems);
    }

    @Test
    @DisplayName("Alle Dateien teilen einen Namensraum")
    void oneNamespace() {
        Project project = new Project(Map.of(
                "eins.mf", "fn ruft() {\n    hilfe()\n}",
                "zwei.mf", "fn hilfe() {\n    log(\"da\")\n}"));
        Parser.ParseResult result = project.parse();
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(2, result.program().functions().size(),
                "beide Funktionen gehören zum selben Programm");
    }

    @Test
    @DisplayName("Derselbe Name in zwei Dateien ist ein Fehler, der beide nennt")
    void duplicatesAcrossFiles() {
        Project project = new Project(Map.of(
                "aaa.mf", "fn doppelt() {\n}",
                "bbb.mf", "fn doppelt() {\n}"));
        Parser.ParseResult result = project.parse();
        assertTrue(result.hasErrors(), "zwei gleiche Namen sind einer zu viel");
        Diagnostic problem = result.diagnostics().get(0);
        assertEquals("bbb.mf", problem.file(), "gemeldet wird die spätere Datei");
        assertTrue(problem.message().contains("aaa.mf"),
                () -> "und sie nennt die frühere: " + problem.message());
        assertEquals(1, result.program().functions().size(),
                "aufgenommen wird nur die erste");
    }

    @Test
    @DisplayName("Zwei on-Blöcke für dasselbe Ereignis sind erlaubt")
    void handlersMayRepeat() {
        Project project = new Project(Map.of(
                "aaa.mf", "on redstone_changed(a, b) {\n}",
                "bbb.mf", "on redstone_changed(a, b) {\n}"));
        Parser.ParseResult result = project.parse();
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(2, result.program().handlers().size(), "beide laufen");
    }

    @Test
    @DisplayName("Die Reihenfolge ist alphabetisch, nicht die des Anlegens")
    void stableOrder() {
        Project project = Project.of("").with("zzz.mf", "").with("aaa.mf", "");
        assertEquals(List.of("aaa.mf", "main.mf", "zzz.mf"), project.names());
    }

    @Test
    @DisplayName("Dateinamen, die aus dem Ordner herausführen, gibt es nicht")
    void nameRules() {
        assertTrue(Project.isValidName("worker.mf"));
        assertTrue(Project.isValidName("anzeigen_2.mf"));
        assertFalse(Project.isValidName("../evil.mf"), "kein Weg nach oben");
        assertFalse(Project.isValidName("unter/ordner.mf"), "kein Pfadtrenner");
        assertFalse(Project.isValidName("Gross.mf"), "keine Großbuchstaben");
        assertFalse(Project.isValidName("ohne_endung"), "die Endung gehört dazu");
        assertFalse(Project.isValidName(""), "und leer schon gar nicht");

        // Ein ungültiger Name fällt beim Bauen heraus, statt sich
        // durchzuschmuggeln.
        Project project = new Project(Map.of("../evil.mf", "boom", "gut.mf", "ok"));
        assertEquals(List.of("gut.mf"), project.names());
    }

    @Test
    @DisplayName("Ohne Datei gibt es trotzdem eine")
    void neverEmpty() {
        assertEquals(List.of(Project.MAIN), new Project(Map.of()).names());
        assertEquals(List.of(Project.MAIN),
                Project.of("x").without(Project.MAIN).names());
    }
    @Test
    @DisplayName("Umbenennen nimmt den Inhalt mit")
    void renameCarriesTheSource() {
        Project project = new Project(Map.of("main.mf", "fn eins() { }",
                "alt.mf", "fn zwei() { }"));
        Project renamed = project.renamed("alt.mf", "worker.mf");

        assertEquals(List.of("main.mf", "worker.mf"), renamed.names());
        assertEquals("fn zwei() { }", renamed.source("worker.mf"));
    }

    @Test
    @DisplayName("Auf einen belegten oder ungültigen Namen wird nicht umbenannt")
    void renameRefusesTakenAndInvalidNames() {
        Project project = new Project(Map.of("main.mf", "a", "worker.mf", "b"));

        assertEquals(project.files(), project.renamed("worker.mf", "main.mf").files(),
                "ein belegter Name hätte den anderen Inhalt überschrieben");
        assertEquals(project.files(), project.renamed("worker.mf", "Worker.MF").files(),
                "Großbuchstaben passen nicht ins Muster");
        assertEquals(project.files(), project.renamed("worker.mf", "../weg.mf").files(),
                "ein Pfad wäre ein Weg aus dem Weltordner heraus");
        assertEquals(project.files(), project.renamed("gibtsnicht.mf", "neu.mf").files());
    }

    @Test
    @DisplayName("Der freie Name zählt eine Ziffer weiter, statt sie anzuhängen")
    void freeNameCountsUpInsteadOfAppending() {
        Project project = new Project(Map.of("main.mf", ""));
        assertEquals("worker2.mf", project.freeNameLike("worker.mf"));

        Project zwei = project.with("worker2.mf", "");
        assertEquals("worker3.mf", zwei.freeNameLike("worker2.mf"),
                "aus worker2 wird worker3 und nicht worker22");
    }

}
