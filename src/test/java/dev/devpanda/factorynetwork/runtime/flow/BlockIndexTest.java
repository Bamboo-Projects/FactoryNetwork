package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index tested without Minecraft.
 *
 * <p>It decides whether a waiting flow finds itself again. A block without a
 * number means: the flow disappears when written down — which is why every
 * place where statements can stand belongs in here.
 */
class BlockIndexTest {

    private static Program parse(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Unerwartete Fehler: " + result.diagnostics());
        return result.program();
    }

    @Test
    @DisplayName("Every block of a function gets a number")
    void everyBlockOfAFunctionIsNumbered() {
        Program program = parse("""
                fn test() {
                    if true {
                        while false {
                            let x = 1
                        }
                    } else {
                        let y = 2
                    }
                }""");
        BlockIndex index = BlockIndex.of(program);
        // Body, then branch, while body, else branch.
        assertEquals(4, index.size());
        for (int i = 0; i < index.size(); i++) {
            assertNotNull(index.block(i), "Block " + i);
            assertEquals(i, index.id(index.block(i)));
        }
    }

    @Test
    @DisplayName("The else branch of an else-if chain too")
    void elseIfChainsAreCovered() {
        Program program = parse("""
                fn test() {
                    if true {
                        let a = 1
                    } else if false {
                        let b = 2
                    } else {
                        let c = 3
                    }
                }""");
        assertEquals(4, BlockIndex.of(program).size());
    }

    @Test
    @DisplayName("The functions of a template count too")
    void multiblockFunctionsAreNumbered() {
        Program program = parse("""
                multiblock OrePlant {
                    devices {
                        crusher
                    }

                    fn process() {
                        let x = 1
                    }
                }""");
        BlockIndex index = BlockIndex.of(program);
        Decl.Multiblock template = (Decl.Multiblock) program.declarations().get(0);
        // Without a number a flow inside it would disappear when written down.
        assertEquals(1, index.size());
        assertSame(template.functions().get(0).body(), index.block(0));
    }

    @Test
    @DisplayName("A comment shifts nothing, a line does")
    void theHashFollowsShapeNotWording() {
        int schlicht = BlockIndex.of(parse("""
                fn test() {
                    let a = 1
                    let b = 2
                }""")).structureHash();
        int kommentiert = BlockIndex.of(parse("""
                fn test() {
                    // erklärt, was hier geschieht
                    let a = 1
                    let b = 2
                }""")).structureHash();
        int laenger = BlockIndex.of(parse("""
                fn test() {
                    let a = 1
                    let zwischendrin = 0
                    let b = 2
                }""")).structureHash();

        assertEquals(schlicht, kommentiert, "Ein Kommentar darf niemanden aufhalten");
        assertNotEquals(schlicht, laenger, "Eine eingefügte Zeile verschiebt alles dahinter");
    }

    @Test
    @DisplayName("A different awaited event changes the shape")
    void awaitedEventNamesCount() {
        int fertig = BlockIndex.of(parse("""
                event Fertig()
                event Abgebrochen()

                fn test() {
                    let r = await Fertig
                }""")).structureHash();
        int abgebrochen = BlockIndex.of(parse("""
                event Fertig()
                event Abgebrochen()

                fn test() {
                    let r = await Abgebrochen
                }""")).structureHash();
        assertNotEquals(fertig, abgebrochen,
                "Sonst wartete ein Ablauf auf etwas anderes als das Programm meint");
    }
}
