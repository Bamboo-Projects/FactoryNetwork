package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the candidates for the acceptance check come from.
 */
class ItemCandidatesTest {

    @Test
    @DisplayName("What is in the filter is a candidate")
    void whatIsInAFilterIsACandidate() {
        Set<String> found = ItemCandidates.of(new Project(Map.of("main.mf", """
                worker mahlen {
                    from grube
                    to crusher_1
                    filter item:iron_ore
                }""")));

        assertEquals(Set.of("iron_ore"), found);
    }

    @Test
    @DisplayName("Also across multiple files and with a namespace")
    void acrossFilesAndWithNamespace() {
        Set<String> found = ItemCandidates.of(new Project(Map.of(
                "a.mf", "worker x {\n    filter item:mekanism/steel_dust\n}",
                "b.mf", "fn test() {\n    move 8 item:coal to ofen\n}")));

        assertTrue(found.contains("mekanism/steel_dust"), () -> found.toString());
        assertTrue(found.contains("coal"), () -> found.toString());
    }

    @Test
    @DisplayName("Tags and patterns stay out")
    void tagsAndPatternsStayOut() {
        Set<String> found = ItemCandidates.of(new Project(Map.of("main.mf", """
                worker x {
                    filter tag:c/ores
                }

                worker y {
                    filter item:*_dust
                }""")));

        assertFalse(found.contains("c/ores"), "ein Tag steht für viele Arten");
        assertTrue(found.stream().noneMatch(name -> name.contains("*")),
                () -> "ein Muster ist ein Tag in anderer Schreibweise: " + found);
    }

    @Test
    @DisplayName("A program without items yields nothing")
    void aProgramWithoutItemsGivesNothing() {
        Set<String> found = ItemCandidates.of(new Project(Map.of("main.mf", """
                fn test() {
                    log("nichts")
                }""")));

        assertTrue(found.isEmpty(), () -> found.toString());
    }

    @Test
    @DisplayName("The same item twice is one candidate")
    void theSameItemTwiceIsOneCandidate() {
        Set<String> found = ItemCandidates.of(new Project(Map.of("main.mf", """
                fn test() {
                    move 8 item:coal to ofen
                    move 8 item:coal to ofen2
                }""")));

        assertEquals(1, found.size(), () -> found.toString());
    }
}
