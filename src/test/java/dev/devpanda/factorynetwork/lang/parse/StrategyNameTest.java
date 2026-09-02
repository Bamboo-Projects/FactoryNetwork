package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wrong strategy name is reported instead of silently taking effect.
 *
 * <p><b>The occasion is in our own codebase.</b> A GameTest wrote
 * {@code strategy emptiest} and supposedly tested distribution to the
 * emptiest device with it. That name does not exist — {@code Strategy.of}
 * falls back to {@code round_robin} on every unknown name, and does so
 * silently. The test distributed round-robin and nobody could see it; even a
 * Javadoc explained the subtlety using the name that does not exist.
 *
 * <p>A typo that does something other than intended and says nothing about
 * it is more expensive than one that does nothing at all.
 */
class StrategyNameTest {

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    @Test
    @DisplayName("An invented strategy on the group is reported")
    void anInventedStrategyOnAGroupIsReported() {
        List<Diagnostic> errors = errorsOf("""
                group ziele {
                    members ziel_*
                    strategy emptiest
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).message().contains("emptiest"),
                () -> "der Name muss dastehen: " + errors.get(0));
        assertTrue(errors.get(0).hint().contains("least_filled"),
                () -> "der gemeinte Name muss vorgeschlagen werden: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("And on the worker just the same")
    void andOnAWorkerToo() {
        List<Diagnostic> errors = errorsOf("""
                worker verteilen {
                    from lager
                    to ziele
                    strategy emptiest
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).hint().contains("least_filled"),
                () -> "der gemeinte Name muss vorgeschlagen werden: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("A name without any resemblance gets the whole list")
    void anUnrelatedNameGetsTheFullList() {
        List<Diagnostic> errors = errorsOf("""
                group ziele {
                    members ziel_*
                    strategy quatsch
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).hint().contains("round_robin")
                        && errors.get(0).hint().contains("priority"),
                () -> "die Liste fehlt: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("All five real strategies pass")
    void allFiveRealStrategiesPass() {
        for (String strategy : dev.devpanda.factorynetwork.lang.Signatures.STRATEGIES) {
            assertTrue(errorsOf("""
                    group ziele {
                        members ziel_*
                        strategy %s
                    }""".formatted(strategy)).isEmpty(),
                    () -> strategy + " ist eine echte Verteilung");
        }
    }

    @Test
    @DisplayName("priority arrives as a strategy, not as a worker entry")
    void priorityArrivesAsAStrategy() {
        // It is a keyword at the same time. Without the special case the line
        // did not parse at all — so the value is checked and not merely that
        // no message arrives.
        var program = Parser.parse("""
                group ziele {
                    members ziel_*
                    strategy priority
                }""").program();

        var group = (dev.devpanda.factorynetwork.lang.ast.Decl.Group)
                program.declarations().get(0);
        assertEquals("priority", group.strategy(), "die Verteilung der Gruppe");
    }

    @Test
    @DisplayName("The list in the editor is the runtime's")
    void theEditorListMatchesTheRuntime() {
        // Two lists in two places drift apart as soon as a strategy is added
        // — and then the parser reports as wrong a name that the runtime
        // knows.
        List<String> runtime =
                java.util.Arrays.stream(
                        dev.devpanda.factorynetwork.runtime.DeviceGroup.Strategy.values())
                        .map(dev.devpanda.factorynetwork.runtime.DeviceGroup.Strategy::key)
                        .sorted()
                        .toList();
        List<String> editor = dev.devpanda.factorynetwork.lang.Signatures.STRATEGIES.stream()
                .sorted()
                .toList();

        assertEquals(runtime, editor, "Signatures.STRATEGIES und DeviceGroup.Strategy");
    }
}
