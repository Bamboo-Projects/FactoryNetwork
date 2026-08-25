package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was sich an globalen Werten sagen lässt — und was nicht.
 *
 * <p><b>Die Sprache hat keinen Typprüfer.</b> Geprüft wird deshalb nur, was
 * ohne einen entscheidbar ist: ein fester Anfangswert, keine doppelten Namen,
 * und ein Literal, das einem Literal anderer Art zugewiesen wird. Alles
 * andere fällt zur Laufzeit auf, wie überall sonst.
 */
class GlobalCheckTest {

    private static List<Diagnostic> check(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics();
    }

    @Test
    @DisplayName("Eine Rechnung als Anfangswert wird gemeldet")
    void aCalculationAsInitialValueIsReported() {
        List<Diagnostic> problems = check("global x = storage.count(item:stone)");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("festen Wert")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Zwei gleiche Namen werden gemeldet")
    void twoGlobalsWithTheSameNameAreReported() {
        List<Diagnostic> problems = check("""
                global modus = "tag"
                global modus = "nacht\"""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Text, dem eine Zahl zugewiesen wird, ist ein Vertipper")
    void assigningANumberToATextIsReported() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = 3
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Auch tief in einer Verschachtelung")
    void alsoDeepInsideNesting() {
        List<Diagnostic> problems = check("""
                global vorrat = 0

                fn schalten() {
                    if true {
                        while true {
                            vorrat = "voll"
                        }
                    }
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("vorrat")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Aufruf als Zuweisung bleibt offen")
    void assigningACallIsNotJudged() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = berechnen()
                }

                fn berechnen() {
                    return "nacht"
                }""");

        assertTrue(problems.isEmpty(),
                () -> "ohne Typprüfer lässt sich hier nichts sagen: " + problems);
    }

    @Test
    @DisplayName("Ein richtiger Text ist keine Meldung wert")
    void assigningATextToATextIsFine() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = "nacht"
                }""");

        assertTrue(problems.isEmpty(), () -> problems.toString());
    }

    @Test
    @DisplayName("Ein gleichnamiges let verdeckt den globalen Wert")
    void aLocalLetShadowsTheGlobal() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    let modus = 3
                    modus = 5
                }""");

        assertTrue(problems.isEmpty(),
                () -> "die Zuweisung trifft das let, nicht den globalen Wert: " + problems);
    }

    @Test
    @DisplayName("Nach dem Block gilt der globale Wert wieder")
    void afterTheBlockTheGlobalIsBackInPlay() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    if true {
                        let modus = 3
                    }
                    modus = 7
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "das let galt nur im if — hier ist wieder der globale Wert gemeint: "
                        + problems);
    }

    @Test
    @DisplayName("Eine Kommazahl ist keine ganze Zahl, aber auch kein Vertipper")
    void aDecimalAssignedToAnIntIsFine() {
        List<Diagnostic> problems = check("""
                global menge = 0

                fn rechnen() {
                    menge = 2.5
                }""");

        assertTrue(problems.isEmpty(),
                () -> "Zahl bleibt Zahl — die Sprache rechnet ohnehin mit beiden: "
                        + problems);
    }

    @Test
    @DisplayName("Ein Festwert lässt sich lesen")
    void aConstantCanBeRead() {
        assertTrue(check("""
                const stapel = 64

                fn tun() {
                    log(stapel)
                }""").isEmpty(), "einen Festwert zu lesen ist der Normalfall");
    }

    @Test
    @DisplayName("Ein Festwert lässt sich nicht schreiben")
    void aConstantCannotBeWritten() {
        List<Diagnostic> problems = check("""
                const stapel = 64

                fn tun() {
                    stapel = 32
                }""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.isError() && problem.message().contains("Festwert")),
                () -> "der Versuch muss gemeldet werden: " + problems);
    }

    @Test
    @DisplayName("Ein Festwert und ein globaler Wert dürfen nicht gleich heißen")
    void aConstantAndAGlobalCannotShareAName() {
        List<Diagnostic> problems = check("""
                global modus = "tag"
                const modus = 64""");

        assertTrue(problems.stream().anyMatch(Diagnostic::isError),
                () -> "zwei Erklärungen für einen Namen: " + problems);
    }
}
