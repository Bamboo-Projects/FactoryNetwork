package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can go wrong with a filter template is reported by the compiler.
 *
 * <p><b>Errors, not warnings</b>, unlike {@link EventCheck}:
 * a mixed or empty template does not get any better from another file.
 * It is wrong right where it stands.
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
    @DisplayName("A clean template reports nothing")
    void aSoundTemplateIsQuiet() {
        assertEquals(List.of(), check("""
                filter erze {
                    tag:c/ores
                    except item:ancient_debris
                }"""));
    }

    @Test
    @DisplayName("Items and fluids in one template")
    void mixedIsAnError() {
        hasError(check("""
                filter durcheinander {
                    item:iron_ingot
                    fluid:water
                }"""), "Gegenstände oder für Flüssigkeiten");
    }

    @Test
    @DisplayName("An empty block selects nothing")
    void anEmptyTemplateIsAnError() {
        hasError(check("""
                filter leer {
                }"""), "wählt nichts aus");
    }

    @Test
    @DisplayName("Only exclusions also select nothing")
    void onlyExclusionsIsAnError() {
        hasError(check("""
                filter leer {
                    except item:iron_ingot
                }"""), "wählt nichts aus");
    }

    @Test
    @DisplayName("A template inside a template")
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
    @DisplayName("Without Mekanism the message says Mekanism is missing")
    void withoutMekanismThemessageSaysSo() {
        // "Chemicals are not wired up yet" was only half the truth:
        // in a pack without Mekanism they do not exist at all, and the
        // player looks for the fault in this mod instead of in their mod list.
        // Without a loaded mod list — that is, in a unit test — "not
        // installed" applies: that is the more honest default.
        hasError(check("""
                filter gase {
                    chemical:mekanism/hydrogen
                }"""), "Mekanism");
        assertTrue(check("""
                filter gase {
                    chemical:mekanism/hydrogen
                }""").get(0).hint().contains("nicht installiert"),
                "der Hinweis muss auf die Modliste zeigen");
    }

    @Test
    @DisplayName("Power is not a selection")
    void powerIsNoSelection() {
        hasError(check("""
                filter energie {
                    power
                }"""), "Strom");
    }

    @Test
    @DisplayName("A name already carried by something else")
    void aNameTakenBySomethingElse() {
        List<Diagnostic> problems = FilterCheck.run(Parser.parse("""
                filter pumpen {
                    fluid:water
                }""").program(), Map.of("pumpen", "Gruppe"));

        hasError(problems, "steht schon");
    }
}
