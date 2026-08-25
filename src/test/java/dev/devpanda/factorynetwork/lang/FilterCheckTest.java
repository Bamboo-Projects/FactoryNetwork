package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was an einer Filter-Vorlage schiefgehen kann, sagt der Übersetzer.
 *
 * <p><b>Fehler und keine Warnungen</b>, anders als bei {@link EventCheck}:
 * Eine gemischte oder leere Vorlage wird durch eine weitere Datei nicht
 * besser. Sie ist an Ort und Stelle falsch.
 */
class FilterCheckTest {

    private static List<Diagnostic> check(String source) {
        return FilterCheck.run(Parser.parse(source).program(), Map.of());
    }

    private static void hasError(List<Diagnostic> problems, String fragment) {
        assertTrue(problems.stream()
                        .anyMatch(problem -> problem.severity() == Diagnostic.Severity.ERROR
                                && problem.message().contains(fragment)),
                () -> "Erwartet wurde ein Fehler mit „" + fragment + "“, gemeldet wurde "
                        + problems);
    }

    @Test
    @DisplayName("Eine saubere Vorlage meldet nichts")
    void aSoundTemplateIsQuiet() {
        assertEquals(List.of(), check("""
                filter erze {
                    tag:c/ores
                    except item:ancient_debris
                }"""));
    }

    @Test
    @DisplayName("Gegenstände und Flüssigkeiten in einer Vorlage")
    void mixedIsAnError() {
        hasError(check("""
                filter durcheinander {
                    item:iron_ingot
                    fluid:water
                }"""), "Gegenstände oder für Flüssigkeiten");
    }

    @Test
    @DisplayName("Ein leerer Block wählt nichts aus")
    void anEmptyTemplateIsAnError() {
        hasError(check("""
                filter leer {
                }"""), "wählt nichts aus");
    }

    @Test
    @DisplayName("Nur Ausnahmen wählen auch nichts aus")
    void onlyExclusionsIsAnError() {
        hasError(check("""
                filter leer {
                    except item:iron_ingot
                }"""), "wählt nichts aus");
    }

    @Test
    @DisplayName("Eine Vorlage in einer Vorlage")
    void anestedTemplateIsAnError() {
        hasError(check("""
                filter erze {
                    tag:c/ores
                }

                filter mehr_erze {
                    erze
                    item:ancient_debris
                }"""), "keine andere Vorlage");
    }

    @Test
    @DisplayName("Chemikalien sind noch nicht angebunden")
    void chemicalsAreNotConnectedYet() {
        hasError(check("""
                filter gase {
                    chemical:mekanism/hydrogen
                }"""), "Chemikalien");
    }

    @Test
    @DisplayName("Strom ist keine Auswahl")
    void powerIsNoSelection() {
        hasError(check("""
                filter energie {
                    power
                }"""), "Strom");
    }

    @Test
    @DisplayName("Ein Name, den schon etwas anderes trägt")
    void aNameTakenBySomethingElse() {
        List<Diagnostic> problems = FilterCheck.run(Parser.parse("""
                filter pumpen {
                    fluid:water
                }""").program(), Map.of("pumpen", "Gruppe"));

        hasError(problems, "steht schon");
    }
}
