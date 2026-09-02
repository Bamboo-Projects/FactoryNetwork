package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * What a template is about is written in its lines.
 *
 * <p>Pure tree inspection, without the registry: <b>which</b> items
 * {@code tag:c/ores} matches is known only to the world — that they are
 * items is already stated here.
 */
class FilterKindTest {

    private static Decl.FilterTemplate template(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return assertInstanceOf(Decl.FilterTemplate.class,
                result.program().declarations().get(0));
    }

    @Test
    @DisplayName("Items and tags are an item template")
    void itemsAndTagsAreAnItemTemplate() {
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter ore_factory {
                    tag:c/ores
                    item:deepslate_coal_ore
                }""")));
    }

    @Test
    @DisplayName("A tag alone counts as an item")
    void aTagAloneCountsAsItems() {
        // As long as fluid tags are not resolved
        // (offene-punkte.md 1.3), tag: means items. If that changes,
        // this line changes with it.
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter erze {
                    tag:c/ores
                }""")));
    }

    @Test
    @DisplayName("Fluids are a fluid template")
    void fluidsAreAFluidTemplate() {
        assertEquals(FilterKind.FLUID, FilterKind.of(template("""
                filter kuehlmittel {
                    fluid:water
                }""")));
    }

    @Test
    @DisplayName("Both together is mixed")
    void bothTogetherIsMixed() {
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    item:iron_ingot
                    fluid:water
                }""")));
    }

    @Test
    @DisplayName("An exclusion also has a say in the kind")
    void anExclusionCountsToo() {
        // Otherwise the kind would depend on which line something is on — and
        // an exclusion that does not fit the template at all would go unnoticed.
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    item:iron_ingot
                    except fluid:water
                }""")));
    }

    @Test
    @DisplayName("Even through except and amounts")
    void throughExceptAndAmounts() {
        assertEquals(FilterKind.ITEM, FilterKind.of(template("""
                filter erze {
                    tag:c/ores except item:ancient_debris
                }""")));
    }

    @Test
    @DisplayName("An empty block selects nothing")
    void anEmptyBlockIsEmpty() {
        assertEquals(FilterKind.EMPTY, FilterKind.of(template("""
                filter leer {
                }""")));
    }

    @Test
    @DisplayName("Only exclusions is just as empty")
    void onlyExclusionsIsEmptyToo() {
        // There is nothing to subtract from. The error for that lives in
        // FilterCheck; here it only matters that the kind does not hide it.
        assertEquals(FilterKind.EMPTY, FilterKind.of(template("""
                filter leer {
                    except item:iron_ingot
                }""")));
    }

    @Test
    @DisplayName("A fluid tag makes a fluid template")
    void aFluidTagMakesAFluidTemplate() {
        assertEquals(FilterKind.FLUID, FilterKind.of(template("""
                filter kuehlmittel {
                    fluidtag:c/molten
                    except fluid:lava
                }""")));
    }

    @Test
    @DisplayName("A fluid tag next to an item is mixed")
    void aFluidTagNextToAnItemIsMixed() {
        assertEquals(FilterKind.MIXED, FilterKind.of(template("""
                filter durcheinander {
                    fluidtag:c/molten
                    item:iron_ingot
                }""")));
    }
}
