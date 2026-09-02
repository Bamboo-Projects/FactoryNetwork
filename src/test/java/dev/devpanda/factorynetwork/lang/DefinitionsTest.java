package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a name is declared and where else it occurs.
 *
 * <p>All files share a single namespace. That is exactly why the search
 * is needed: from the point of use there is no way to tell which file
 * holds the declaration.
 */
class DefinitionsTest {

    private static Project project() {
        return new Project(Map.of(
                "aaa.mf", String.join("\n",
                        "group oefen {",
                        "    members ofen_1",
                        "}"),
                "bbb.mf", String.join("\n",
                        "fn leeren() {",
                        "    move 64 to oefen",
                        "}",
                        "worker haul {",
                        "    from kiste_1",
                        "    to oefen",
                        "}")));
    }

    @Test
    @DisplayName("A group is found in its own file")
    void findsADeclarationInAnotherFile() {
        var found = Definitions.find(project(), "oefen").orElseThrow();

        assertEquals("aaa.mf", found.file());
        assertEquals(1, found.line());
    }

    @Test
    @DisplayName("A function is found")
    void findsAFunction() {
        var found = Definitions.find(project(), "leeren").orElseThrow();

        assertEquals("bbb.mf", found.file());
        assertEquals(1, found.line());
    }

    @Test
    @DisplayName("A name that does not exist is not invented")
    void unknownNamesAreNotFound() {
        assertTrue(Definitions.find(project(), "gibtsnicht").isEmpty());
        assertTrue(Definitions.find(project(), "").isEmpty());
        assertTrue(Definitions.find(project(), null).isEmpty());
    }

    @Test
    @DisplayName("Occurrences appear in both files")
    void referencesSpanTheProject() {
        List<Definitions.Location> places = Definitions.references(project(), "oefen");

        assertEquals(3, places.size(), () -> "Erklärung und zwei Gebrauchsstellen: " + places);
        assertTrue(places.stream().anyMatch(place -> place.file().equals("aaa.mf")));
        assertEquals(2, places.stream().filter(place -> place.file().equals("bbb.mf")).count());
    }

    @Test
    @DisplayName("Only whole words count")
    void onlyWholeWordsCount() {
        Project project = new Project(Map.of("main.mf", String.join("\n",
                "worker ofen_1 { }",
                "worker ofen_10 { }")));

        List<Definitions.Location> places = Definitions.references(project, "ofen_1");

        assertEquals(1, places.size(),
                () -> "ofen_10 ist keine Fundstelle von ofen_1: " + places);
    }

    @Test
    @DisplayName("A function inside a multiblock is found too")
    void functionsInsideAMultiblockCount() {
        Project project = new Project(Map.of("main.mf", String.join("\n",
                "multiblock ofen {",
                "    fn heizen() {",
                "    }",
                "}")));

        assertTrue(Definitions.find(project, "heizen").isPresent(),
                "sie steht verschachtelt, ist aber eine Erklärung");
    }

    @Test
    @DisplayName("A global value is declared at its global line")
    void aGlobalIsDeclaredAtItsLine() {
        Project project = new Project(Map.of("main.mf", String.join("\n",
                "global modus = \"tag\"",
                "",
                "fn test() {",
                "    log(modus)",
                "}")));

        var found = Definitions.find(project, "modus");

        assertTrue(found.isPresent(), "die Erklärung fehlt");
        assertEquals(1, found.get().line(), "sie steht in der ersten Zeile");
    }

    @Test
    @DisplayName("A global value is found from another file too")
    void aGlobalIsFoundAcrossFiles() {
        Project project = new Project(Map.of(
                "werte.mf", "global modus = \"tag\"",
                "main.mf", String.join("\n",
                        "fn test() {",
                        "    log(modus)",
                        "}")));

        var found = Definitions.find(project, "modus");

        assertTrue(found.isPresent(), "alle Dateien teilen einen Namensraum");
        assertEquals("werte.mf", found.get().file());
    }
}
