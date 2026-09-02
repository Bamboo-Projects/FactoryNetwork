package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a program costs on the storage medium.
 *
 * <p>The number is right in front of the player in the terminal and limits
 * what they may write. So it must not only be correct but also be explainable
 * — which is why, for every construct, what it costs is spelled out here.
 */
class ProgramSizeTest {

    /** The same, but warnings are welcome here. */
    private static int sizeOfWithWarnings(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        return ProgramSize.of(result.program());
    }

    private static int sizeOf(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        Program program = result.program();
        return ProgramSize.of(program);
    }

    @Test
    @DisplayName("An empty function costs one")
    void emptyFunction() {
        assertEquals(1, sizeOf("fn nichts() {\n}"));
    }

    @Test
    @DisplayName("Every statement costs one more")
    void oneEach() {
        assertEquals(4, sizeOf("""
                fn drei() {
                    let a = 1
                    let b = 2
                    let c = 3
                }"""));
    }

    @Test
    @DisplayName("Comments and blank lines cost nothing")
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
    @DisplayName("A nested block counts its contents too")
    void nestedBlocksCount() {
        // Function, loop and the two statements inside it.
        assertEquals(4, sizeOf("""
                fn schleife() {
                    while true {
                        let a = 1
                        break
                    }
                }"""));
    }

    @Test
    @DisplayName("The else branch counts as well")
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
    @DisplayName("A call costs one, like any other statement")
    void call() {
        assertEquals(2, sizeOf("""
                fn sagt() {
                    log("hallo")
                }"""));
    }

    /**
     * {@code log "hallo"} without parentheses costs two, because it is two
     * statements — a name and a string, both without effect. The parser
     * warns about it by now; what is counted is still what is written
     * there.
     */
    @Test
    @DisplayName("Two expressions side by side are two statements")
    void twoBareExpressions() {
        assertEquals(3, sizeOfWithWarnings("""
                fn sagt() {
                    log "hallo"
                }"""));
    }
}
