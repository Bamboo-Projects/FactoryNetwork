package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Wovon eine Vorlage handelt, steht in ihren Zeilen.
 *
 * <p>Reine Baumbetrachtung, ohne Registry: <b>welche</b> Gegenstände
 * {@code tag:c/ores} trifft, weiß erst die Welt — dass es Gegenstände sind,
 * steht schon da.
 */
class FilterKindTest {

    private static Decl.FilterTemplate template(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
    }

    @Test
    @DisplayName("Gegenstände und Tags sind eine Gegenstandsvorlage")
    void itemsAndTagsAreAnItemTemplate() {
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter ore_factory {
                    tag:c/ores
                    item:deepslate_coal_ore
                }""")));
    }

    @Test
    @DisplayName("Ein Tag allein zählt als Gegenstand")
    void aTagAloneCountsAsItems() {
        // Solange Flüssigkeits-Tags nicht aufgelöst werden
        // (offene-punkte.md 1.3), meint tag: Gegenstände. Ändert sich das,
        // ändert sich diese Zeile mit.
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter erze {
                    tag:c/ores
                }""")));
    }

    @Test
    @DisplayName("Flüssigkeiten sind eine Flüssigkeitsvorlage")
    void fluidsAreAFluidTemplate() {
        assertEquals(FilterKind.FLUID, FilterKind.of(template("""
                filter kuehlmittel {
                    fluid:water
                }""")));
    }

    @Test
    @DisplayName("Beides zusammen ist gemischt")
    void bothTogetherIsMixed() {
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    item:iron_ingot
                    fluid:water
                }""")));
    }

    @Test
    @DisplayName("Auch eine Ausnahme entscheidet über die Sorte mit")
    void anExclusionCountsToo() {
        // Sonst hinge die Sorte davon ab, in welcher Zeile etwas steht — und
        // eine Ausnahme, die gar nicht zur Vorlage passt, fiele niemandem auf.
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    item:iron_ingot
                    except fluid:water
                }""")));
    }

    @Test
    @DisplayName("Auch durch except und Mengen hindurch")
    void throughExceptAndAmounts() {
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter erze {
                    tag:c/ores except item:ancient_debris
                }""")));
    }

    @Test
    @DisplayName("Ein leerer Block wählt nichts")
    void anEmptyBlockIsEmpty() {
        assertEquals(FilterKind.EMPTY, FilterKind.of(template("""
                filter leer {
                }""")));
    }

    @Test
    @DisplayName("Nur Ausnahmen ist genauso leer")
    void onlyExclusionsIsEmptyToo() {
        // Es gibt nichts, wovon abgezogen würde. Der Fehler dafür steht in
        // FilterCheck; hier zählt nur, dass die Sorte es nicht verdeckt.
        assertEquals(FilterKind.EMPTY, FilterKind.of(template("""
                filter leer {
                    except item:iron_ingot
                }""")));
    }

    @Test
    @DisplayName("Ein Flüssigkeits-Tag macht eine Flüssigkeitsvorlage")
    void aFluidTagMakesAFluidTemplate() {
        assertEquals(FilterKind.FLUID, FilterKind.of(template("""
                filter kuehlmittel {
                    fluidtag:c/molten
                    except fluid:lava
                }""")));
    }

    @Test
    @DisplayName("Ein Flüssigkeits-Tag neben einem Gegenstand ist gemischt")
    void aFluidTagNextToAnItemIsMixed() {
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    fluidtag:c/molten
                    item:iron_ingot
                }""")));
    }
}
