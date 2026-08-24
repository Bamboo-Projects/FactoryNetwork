package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein falscher Verteilungsname wird gemeldet, statt still zu wirken.
 *
 * <p><b>Der Anlass steht im eigenen Bestand.</b> Ein GameTest schrieb
 * {@code strategy emptiest} und prüfte damit angeblich die Verteilung nach dem
 * leersten Gerät. Diesen Namen gibt es nicht — {@code Strategy.of} fällt bei
 * jedem unbekannten Namen auf {@code round_robin} zurück, und zwar
 * schweigend. Der Test verteilte reihum und niemand konnte es sehen; sogar ein
 * Javadoc erklärte die Feinheit anhand des Namens, den es nicht gibt.
 *
 * <p>Ein Tippfehler, der etwas anderes tut als gemeint und dabei nichts sagt,
 * ist teurer als einer, der gar nichts tut.
 */
class StrategyNameTest {

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    @Test
    @DisplayName("Eine erfundene Verteilung an der Gruppe wird gemeldet")
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
    @DisplayName("Und am Worker genauso")
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
    @DisplayName("Ein Name ohne Ähnlichkeit bekommt die ganze Liste")
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
    @DisplayName("Alle fünf echten Verteilungen gehen durch")
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
    @DisplayName("priority kommt als Verteilung an, nicht als Worker-Angabe")
    void priorityArrivesAsAStrategy() {
        // Es ist zugleich ein Schlüsselwort. Ohne den Sonderfall parste die
        // Zeile gar nicht — geprüft wird deshalb der Wert und nicht nur, dass
        // keine Meldung kommt.
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
    @DisplayName("Die Liste im Editor ist die der Laufzeit")
    void theEditorListMatchesTheRuntime() {
        // Zwei Listen an zwei Orten laufen auseinander, sobald eine Verteilung
        // dazukommt — und dann meldet der Parser einen Namen als falsch, den
        // die Laufzeit kennt.
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
