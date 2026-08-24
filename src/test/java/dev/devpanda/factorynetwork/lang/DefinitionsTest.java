package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wo ein Name erklärt wird und wo er sonst noch steht.
 *
 * <p>Alle Dateien teilen einen Namensraum. Genau deshalb braucht es die
 * Suche: Von der Stelle des Gebrauchs aus ist nicht zu sehen, in welcher
 * Datei die Erklärung liegt.
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
    @DisplayName("Eine Gruppe wird in ihrer eigenen Datei gefunden")
    void findsADeclarationInAnotherFile() {
        var found = Definitions.find(project(), "oefen").orElseThrow();

        assertEquals("aaa.mf", found.file());
        assertEquals(1, found.line());
    }

    @Test
    @DisplayName("Eine Funktion wird gefunden")
    void findsAFunction() {
        var found = Definitions.find(project(), "leeren").orElseThrow();

        assertEquals("bbb.mf", found.file());
        assertEquals(1, found.line());
    }

    @Test
    @DisplayName("Ein Name, den es nicht gibt, wird nicht erfunden")
    void unknownNamesAreNotFound() {
        assertTrue(Definitions.find(project(), "gibtsnicht").isEmpty());
        assertTrue(Definitions.find(project(), "").isEmpty());
        assertTrue(Definitions.find(project(), null).isEmpty());
    }

    @Test
    @DisplayName("Fundstellen stehen in beiden Dateien")
    void referencesSpanTheProject() {
        List<Definitions.Location> places = Definitions.references(project(), "oefen");

        assertEquals(3, places.size(), () -> "Erklärung und zwei Gebrauchsstellen: " + places);
        assertTrue(places.stream().anyMatch(place -> place.file().equals("aaa.mf")));
        assertEquals(2, places.stream().filter(place -> place.file().equals("bbb.mf")).count());
    }

    @Test
    @DisplayName("Nur ganze Wörter zählen")
    void onlyWholeWordsCount() {
        Project project = new Project(Map.of("main.mf", String.join("\n",
                "worker ofen_1 { }",
                "worker ofen_10 { }")));

        List<Definitions.Location> places = Definitions.references(project, "ofen_1");

        assertEquals(1, places.size(),
                () -> "ofen_10 ist keine Fundstelle von ofen_1: " + places);
    }

    @Test
    @DisplayName("Eine Funktion in einem Multiblock wird mitgefunden")
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
    @DisplayName("Ein globaler Wert wird an seiner global-Zeile erklärt")
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
    @DisplayName("Ein globaler Wert wird auch aus einer anderen Datei gefunden")
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
