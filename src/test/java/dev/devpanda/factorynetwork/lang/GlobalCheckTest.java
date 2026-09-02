package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can be said about global values — and what cannot.
 *
 * <p><b>The language has no type checker.</b> So only what is decidable
 * without one is checked: a fixed initial value, no duplicate names,
 * and a literal assigned to a literal of another kind. Everything
 * else surfaces at runtime, as it does everywhere else.
 */
class GlobalCheckTest {

    private static List<Diagnostic> check(String source) {
        return new Project(Map.of("main.mf", source)).parse().diagnostics();
    }

    @Test
    @DisplayName("A calculation as an initial value is reported")
    void aCalculationAsInitialValueIsReported() {
        List<Diagnostic> problems = check("global x = storage.count(item:stone)");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("festen Wert")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Two identical names are reported")
    void twoGlobalsWithTheSameNameAreReported() {
        List<Diagnostic> problems = check("""
                global modus = "tag"
                global modus = "nacht\"""");

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("modus")),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("Assigning a number to a text is a typo")
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
    @DisplayName("Even deep inside nesting")
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
    @DisplayName("A call as an assignment stays open")
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
    @DisplayName("A proper text is not worth a report")
    void assigningATextToATextIsFine() {
        List<Diagnostic> problems = check("""
                global modus = "tag"

                fn schalten() {
                    modus = "nacht"
                }""");

        assertTrue(problems.isEmpty(), () -> problems.toString());
    }

    @Test
    @DisplayName("A let of the same name shadows the global value")
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
    @DisplayName("After the block the global value applies again")
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
    @DisplayName("A decimal is not a whole number, but not a typo either")
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
    @DisplayName("A constant can be read")
    void aConstantCanBeRead() {
        assertTrue(check("""
                const stapel = 64

                fn tun() {
                    log(stapel)
                }""").isEmpty(), "einen Festwert zu lesen ist der Normalfall");
    }

    @Test
    @DisplayName("A constant cannot be written")
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
    @DisplayName("A constant and a global value must not share a name")
    void aConstantAndAGlobalCannotShareAName() {
        List<Diagnostic> problems = check("""
                global modus = "tag"
                const modus = 64""");

        assertTrue(problems.stream().anyMatch(Diagnostic::isError),
                () -> "zwei Erklärungen für einen Namen: " + problems);
    }
}
