package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Was ein Programm auf dem Datenträger kostet.
 *
 * <p>Die Zahl steht dem Spieler im Terminal vor Augen und begrenzt, was er
 * schreiben darf. Sie muss deshalb nicht nur stimmen, sondern sich auch
 * erklären lassen — deshalb steht hier für jede Bauform, was sie kostet.
 */
class ProgramSizeTest {

    private static int sizeOf(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        Program program = result.program();
        return ProgramSize.of(program);
    }

    @Test
    @DisplayName("Eine leere Funktion kostet eins")
    void emptyFunction() {
        assertEquals(1, sizeOf("fn nichts() {\n}"));
    }

    @Test
    @DisplayName("Jede Anweisung kostet eins dazu")
    void oneEach() {
        assertEquals(4, sizeOf("""
                fn drei() {
                    let a = 1
                    let b = 2
                    let c = 3
                }"""));
    }

    @Test
    @DisplayName("Kommentare und Leerzeilen kosten nichts")
    void commentsAreFree() {
        assertEquals(sizeOf("""
                fn zwei() {
                    let a = 1
                    let b = 2
                }"""), sizeOf("""
                // Diese Funktion zählt bis zwei und erklärt sich dabei
                // ausführlich, weil Erklären nichts kosten soll.
                fn zwei() {
                    // der erste Wert
                    let a = 1

                    // der zweite Wert
                    let b = 2
                }"""));
    }

    @Test
    @DisplayName("Ein verschachtelter Block zählt seinen Inhalt mit")
    void nestedBlocksCount() {
        // Funktion, Schleife und die beiden Anweisungen darin.
        assertEquals(4, sizeOf("""
                fn schleife() {
                    while true {
                        let a = 1
                        break
                    }
                }"""));
    }

    @Test
    @DisplayName("Der else-Zweig zählt auch")
    void elseCounts() {
        assertEquals(4, sizeOf("""
                fn zweig() {
                    if true {
                        let a = 1
                    } else {
                        let b = 2
                    }
                }"""));
    }

    @Test
    @DisplayName("Ein Aufruf kostet eins, wie jede andere Anweisung")
    void call() {
        assertEquals(2, sizeOf("""
                fn sagt() {
                    log("hallo")
                }"""));
    }

    /**
     * <b>Ein Fund nebenbei:</b> {@code log "hallo"} ohne Klammern kostet
     * zwei, weil es zwei Anweisungen sind — ein Name und eine Zeichenkette,
     * beide ohne Wirkung.
     *
     * <p>Die Prüfung steht hier, damit die Zahl nicht überrascht. Dass die
     * Schreibweise überhaupt durchgeht, ist eine Frage an den Parser und
     * keine an die Größenrechnung.
     */
    @Test
    @DisplayName("Zwei Ausdrücke nebeneinander sind zwei Anweisungen")
    void twoBareExpressions() {
        assertEquals(3, sizeOf("""
                fn sagt() {
                    log "hallo"
                }"""));
    }
}
