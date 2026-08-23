package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Namen, die es in der Welt nicht gibt.
 *
 * <p>Der Anlass war eine schwarze Wand: {@code display test { … }} war
 * grammatisch tadellos, der Übersetzer sagte „bereit", und die Tafel blieb
 * leer, weil sie anders hieß. Ein Fehler, der aussieht wie ein kaputtes Netz
 * und ein Tippfehler ist.
 */
class NetworkCheckTest {

    /** Ein Netz mit genau diesen Namen. */
    private static NetworkView net(List<String> connectors, List<String> displays) {
        return new NetworkView() {
            @Override
            public List<String> connectors() {
                return connectors;
            }

            @Override
            public List<String> displays() {
                return displays;
            }
        };
    }

    private static List<Diagnostic> check(String source, NetworkView view) {
        return new Project(Map.of("main.mf", source)).parse(view).diagnostics();
    }

    @Test
    @DisplayName("Eine Anzeige ohne Wand wird gemeldet")
    void anUnknownDisplayIsReported() {
        List<Diagnostic> problems = check("""
                display test {
                    text "hallo"
                }""", net(List.of(), List.of("halle")));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("test") && !problem.isError()),
                () -> "die Meldung fehlt: " + problems);
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("halle")),
                () -> "der Hinweis auf die vorhandene Wand fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Vertipper bekommt einen Vorschlag")
    void aTypoGetsASuggestion() {
        List<Diagnostic> problems = check("""
                display halel {
                    text "hallo"
                }""", net(List.of(), List.of("halle")));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("Meintest du")),
                () -> problems.toString());
    }

    @Test
    @DisplayName("Eine Anzeige, die es gibt, wird nicht gemeldet")
    void aKnownDisplayIsQuiet() {
        assertTrue(check("""
                display halle {
                    text "hallo"
                }""", net(List.of(), List.of("halle"))).isEmpty());
    }

    @Test
    @DisplayName("Ein unbekanntes Ziel wird gemeldet, eine Gruppe nicht")
    void unknownTargetsAreReportedButGroupsAreNot() {
        NetworkView view = net(List.of("kiste_1", "ofen_1"), List.of());

        assertTrue(check("""
                worker haul {
                    from kiste_2
                    to ofen_1
                }""", view).stream().anyMatch(problem -> problem.message().contains("kiste_2")),
                "kiste_2 gibt es nicht");

        // Eine Gruppe steht an derselben Stelle und ist kein Connector.
        assertTrue(check("""
                group oefen {
                    members ofen_1
                }
                worker haul {
                    from kiste_1
                    to oefen
                }""", view).isEmpty(),
                "eine Gruppe aus demselben Programm ist bekannt");
    }

    @Test
    @DisplayName("Ohne bekanntes Netz wird nichts geprüft")
    void nothingIsCheckedWithoutANetwork() {
        // Ein Terminal, das gerade erst aufgeht, hat noch keine Namen. Jeden
        // davon zu melden wäre eine Zeile voller Warnungen, die eine Sekunde
        // später von selbst verschwinden.
        assertTrue(check("""
                display test {
                    text "hallo"
                }""", NetworkView.NONE).isEmpty());
    }

    @Test
    @DisplayName("Eine Warnung hält das Übernehmen nicht auf")
    void aWarningDoesNotBlockDeployment() {
        var result = new Project(Map.of("main.mf", """
                display morgen_gebaut {
                    text "hallo"
                }""")).parse(net(List.of(), List.of("halle")));

        assertFalse(result.hasErrors(),
                "eine Wand, die man erst morgen baut, darf man heute schreiben");
    }
}
