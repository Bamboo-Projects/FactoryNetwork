package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eine aus JEI kopierte ID bekommt eine Antwort, keine Kaskade.
 *
 * <p><b>Jeder kopiert IDs aus JEI</b>, und dort steht {@code mekanism:steel_ingot}.
 * Wer daraus {@code item:mekanism:steel_ingot} macht, bekam sieben
 * Fehlermeldungen in einer Zeile — „Bei move fehlt das Ziel", „from ist ein
 * Schlüsselwort" —, von denen keine einzige den Grund nannte. Der Lexer hörte
 * am zweiten Doppelpunkt auf, und der Rest der Zeile zerfiel in Bruchstücke.
 *
 * <p>Deshalb prüft dieser Test auf <b>genau eine</b> Meldung. Ein
 * {@code anyMatch} hätte auch bestanden, als es sieben waren, und die
 * Kaskade war das eigentliche Problem.
 */
class JeiIdTest {

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    @Test
    @DisplayName("Der zweite Doppelpunkt bringt eine Meldung, nicht sieben")
    void aSecondColonYieldsOneMessage() {
        List<Diagnostic> errors = errorsOf("""
                fn t() {
                    move 1 item:mekanism:steel_ingot from lager to ofen
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).message().contains("Schrägstrich"),
                () -> "der Grund muss dastehen: " + errors.get(0));
        assertTrue(errors.get(0).hint().contains("item:mekanism/steel_ingot"),
                () -> "die richtige Zeile muss dastehen: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("Auch im Worker, wo die Meldung vorher besonders irreführend war")
    void theSameInsideAWorker() {
        // Vorher: „„:" ist keine Angabe, die ein Worker kennt."
        List<Diagnostic> errors = errorsOf("""
                worker w {
                    from lager
                    to ofen
                    filter item:mekanism:steel_ingot
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).hint().contains("item:mekanism/steel_ingot"),
                () -> "die richtige Zeile muss dastehen: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("Ein Tag aus JEI ebenso")
    void aTagFromJeiToo() {
        List<Diagnostic> errors = errorsOf("""
                fn t() {
                    move 1 tag:c:ingots/iron from lager to ofen
                }""");

        assertEquals(1, errors.size(), () -> "genau eine Meldung: " + errors);
        assertTrue(errors.get(0).hint().contains("tag:c/ingots/iron"),
                () -> "die richtige Zeile muss dastehen: " + errors.get(0).hint());
    }

    @Test
    @DisplayName("Die richtige Schreibweise bleibt fehlerfrei")
    void theCorrectFormStaysClean() {
        assertTrue(errorsOf("""
                fn t() {
                    move 1 item:mekanism/steel_ingot from lager to ofen
                    move 1 tag:c/ores from lager to ofen
                    move 1 item:iron_ore from lager to ofen
                }""").isEmpty(), "daran darf sich nichts geändert haben");
    }

    @Test
    @DisplayName("Ein Doppelpunkt ohne Auswahl dahinter frisst die Zeile nicht")
    void aTrailingColonDoesNotSwallowTheLine() {
        // Beim Tippen steht der Doppelpunkt kurz allein da. Verschluckte der
        // Lexer den Rest, liefe die Vervollständigung bei jedem Tastendruck
        // auf einem halben Ausdruck.
        List<Diagnostic> errors = errorsOf("""
                fn t() {
                    move 1 item:iron_ore: from lager to ofen
                }""");

        assertTrue(errors.stream().noneMatch(problem ->
                        problem.message().contains("Schrägstrich")),
                () -> "hier ist kein Namensraum gemeint: " + errors);
    }
}
