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
 * Der Index ohne Minecraft geprüft.
 *
 * <p>Er entscheidet, ob ein wartender Ablauf sich wiederfindet. Ein Block
 * ohne Nummer heißt: Der Ablauf verschwindet beim Aufschreiben — deshalb
 * gehört jede Stelle, an der Anweisungen stehen können, hier hinein.
 */
class BlockIndexTest {

    private static Program parse(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.isError()),
                () -> "Unerwartete Fehler: " + result.diagnostics());
        return result.program();
    }

    @Test
    @DisplayName("Jeder Block einer Funktion bekommt eine Nummer")
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
        // Rumpf, then-Zweig, while-Rumpf, else-Zweig.
        assertEquals(4, index.size());
        for (int i = 0; i < index.size(); i++) {
            assertNotNull(index.block(i), "Block " + i);
            assertEquals(i, index.id(index.block(i)));
        }
    }

    @Test
    @DisplayName("Auch der else-Zweig einer else-if-Kette")
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
    @DisplayName("Die Funktionen einer Vorlage zählen mit")
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
        // Ohne Nummer verschwände ein Ablauf darin beim Aufschreiben.
        assertEquals(1, index.size());
        assertSame(template.functions().get(0).body(), index.block(0));
    }

    @Test
    @DisplayName("Ein Kommentar verschiebt nichts, eine Zeile schon")
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
    @DisplayName("Ein anderes erwartetes Ereignis ändert die Gestalt")
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
