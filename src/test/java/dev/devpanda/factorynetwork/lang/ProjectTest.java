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
 * A project made of several files.
 *
 * <p>The most important check here is the one with the line number: if the
 * compiler were handed the concatenated text, every error from the second
 * file on would point to a line that does not exist there — and the editor
 * would mark the wrong one.
 */
class ProjectTest {

    @Test
    @DisplayName("An error knows its file and its line within it")
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
    @DisplayName("All files share one namespace")
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
    @DisplayName("The same name in two files is an error that names both")
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
    @DisplayName("Two on blocks for the same event are allowed")
    void handlersMayRepeat() {
        Project project = new Project(Map.of(
                "aaa.mf", "on redstone_changed(a, b) {\n}",
                "bbb.mf", "on redstone_changed(a, b) {\n}"));
        Parser.ParseResult result = project.parse();
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(2, result.program().handlers().size(), "beide laufen");
    }

    @Test
    @DisplayName("The order is alphabetical, not that of creation")
    void stableOrder() {
        Project project = Project.of("").with("zzz.mf", "").with("aaa.mf", "");
        assertEquals(List.of("aaa.mf", "main.mf", "zzz.mf"), project.names());
    }

    @Test
    @DisplayName("File names that lead out of the folder do not exist")
    void nameRules() {
        assertTrue(Project.isValidName("worker.mf"));
        assertTrue(Project.isValidName("anzeigen_2.mf"));
        assertFalse(Project.isValidName("../evil.mf"), "kein Weg nach oben");
        // A path separator has been allowed since folders arrived; what it
        // may not do is covered in "No name leads out of the folder".
        assertTrue(Project.isValidName("unter/ordner.mf"), "ein Ordner geht");
        assertFalse(Project.isValidName("Gross.mf"), "keine Großbuchstaben");
        assertFalse(Project.isValidName("ohne_endung"), "die Endung gehört dazu");
        assertFalse(Project.isValidName(""), "und leer schon gar nicht");

        // An invalid name drops out at construction instead of sneaking
        // through.
        Project project = new Project(Map.of("../evil.mf", "boom", "gut.mf", "ok"));
        assertEquals(List.of("gut.mf"), project.names());
    }

    @Test
    @DisplayName("Without a file there is still one")
    void neverEmpty() {
        assertEquals(List.of(Project.MAIN), new Project(Map.of()).names());
        assertEquals(List.of(Project.MAIN),
                Project.of("x").without(Project.MAIN).names());
    }
    @Test
    @DisplayName("Renaming carries the content along")
    void renameCarriesTheSource() {
        Project project = new Project(Map.of("main.mf", "fn eins() { }",
                "alt.mf", "fn zwei() { }"));
        Project renamed = project.renamed("alt.mf", "worker.mf");

        assertEquals(List.of("main.mf", "worker.mf"), renamed.names());
        assertEquals("fn zwei() { }", renamed.source("worker.mf"));
    }

    @Test
    @DisplayName("No rename onto a taken or invalid name")
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
    @DisplayName("The free name counts a digit up instead of appending one")
    void freeNameCountsUpInsteadOfAppending() {
        Project project = new Project(Map.of("main.mf", ""));
        assertEquals("worker2.mf", project.freeNameLike("worker.mf"));

        Project zwei = project.with("worker2.mf", "");
        assertEquals("worker3.mf", zwei.freeNameLike("worker2.mf"),
                "aus worker2 wird worker3 und nicht worker22");
    }

    // ---- Folders -----------------------------------------------------------

    @Test
    @DisplayName("A name may name a folder")
    void anameMayNameAfolder() {
        assertTrue(Project.isValidName("erz/brecher.mf"), "ein Ordner");
        assertTrue(Project.isValidName("erz/eisen/schmelzen.mf"), "und zwei");
        assertTrue(Project.isValidName("main.mf"), "ohne Ordner geht es weiter");
    }

    @Test
    @DisplayName("No name leads out of the folder")
    void nonameLeadsOutOfTheFolder() {
        // The dot is not in the alphabet of a segment. That makes ".." not
        // forbidden but impossible — a deny list could have been worked
        // around.
        assertFalse(Project.isValidName("../weg.mf"), "hinaus");
        assertFalse(Project.isValidName("erz/../../weg.mf"), "und hinaus über Umwege");
        assertFalse(Project.isValidName("/weg.mf"), "von der Wurzel");
        assertFalse(Project.isValidName("C:/weg.mf"), "mit Laufwerk");
        assertFalse(Project.isValidName("erz\brecher.mf"), "mit dem Trenner von Windows");
    }

    @Test
    @DisplayName("A segment without content is none")
    void anemptySegmentIsNone() {
        assertFalse(Project.isValidName("erz//brecher.mf"), "zwei Schrägstriche");
        assertFalse(Project.isValidName("erz/.mf"), "ein Ordner ohne Datei");
        assertFalse(Project.isValidName("/main.mf"), "führender Schrägstrich");
        assertFalse(Project.isValidName("erz/"), "und gar keine Datei");
    }

    @Test
    @DisplayName("A path has an upper bound")
    void apathHasAnupperBound() {
        // It used to be thirty-five characters, because a name consisted of
        // a single segment. Without a new one every packet and every file
        // path would grow without limit.
        String tief = "a/".repeat(60) + "x.mf";
        assertFalse(Project.isValidName(tief), "zu lang: " + tief.length() + " Zeichen");
    }

    @Test
    @DisplayName("The free name counts up in the last segment")
    void thefreeNameCountsUpInTheLastSegment() {
        Project project = new Project(Map.of("erz/brecher.mf", ""));

        assertEquals("erz/brecher2.mf", project.freeNameLike("erz/brecher.mf"),
                "der Ordner bleibt, die Ziffer zieht an die Datei");
    }

    @Test
    @DisplayName("A folder comes before the name that starts that way")
    void afolderComesBeforeThenameThatStartsThatWay() {
        // The slash sorts before digits and letters, so the files of a folder
        // end up next to each other on their own. That is the reason the list
        // in game may stay flat.
        Project project = new Project(Map.of(
                "erz2.mf", "", "erz/brecher.mf", "", "main.mf", ""));

        assertEquals(List.of("erz/brecher.mf", "erz2.mf", "main.mf"), project.names());
    }

    @Test
    @DisplayName("An error in a file in a folder names its whole path")
    void anerrorInAfolderNamesItsWholePath() {
        Project project = new Project(Map.of("erz/brecher.mf", "worker {"));

        assertEquals("erz/brecher.mf", project.parse().diagnostics().get(0).file());
    }

}
