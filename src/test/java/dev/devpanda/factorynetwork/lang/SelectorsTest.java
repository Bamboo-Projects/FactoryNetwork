package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The selector under the cursor, and what becomes of its written form.
 *
 * <p>Both testable without a world: one is text work, the other a
 * decomposition. Only the resolution needs a registry, and that lives in the
 * GameTests.
 */
class SelectorsTest {

    @Test
    @DisplayName("The cursor in the middle of the path means the whole selector")
    void insideThePathTheWholeSelectorIsMeant() {
        String line = "    filter item:iron_ore";
        assertEquals("item:iron_ore", Selectors.at(line, line.indexOf("iron")));
    }

    @Test
    @DisplayName("Also on the kind before it")
    void alsoOnTheKind() {
        String line = "    filter tag:c/ores";
        assertEquals("tag:c/ores", Selectors.at(line, line.indexOf("tag")));
    }

    @Test
    @DisplayName("A pattern belongs to it")
    void apatternBelongsToIt() {
        String line = "move item:*_ore from a to b";
        assertEquals("item:*_ore", Selectors.at(line, line.indexOf('*')));
    }

    @Test
    @DisplayName("Beside a selector there is none")
    void besideASelectorThereIsNone() {
        String line = "    filter item:iron_ore";
        assertEquals("", Selectors.at(line, 2));
        assertEquals("", Selectors.at(line, line.length()));
    }

    @Test
    @DisplayName("A word without a colon is no selector")
    void awordWithoutAcolonIsNoSelector() {
        String line = "    from quarry_output";
        assertEquals("", Selectors.at(line, line.indexOf("quarry")));
    }

    @Test
    @DisplayName("The written form becomes a selection again")
    void thewrittenFormBecomesASelectorAgain() {
        Expr.Selector item = Selectors.parse("item:iron_ore");
        assertEquals(Expr.Selector.Kind.ITEM, item.kind());
        assertEquals("iron_ore", item.path());

        Expr.Selector tag = Selectors.parse("tag:c/ores");
        assertEquals(Expr.Selector.Kind.TAG, tag.kind());
        assertEquals("c", tag.namespace());
        assertEquals("ores", tag.path());
    }

    @Test
    @DisplayName("A pattern without a namespace keeps its slash")
    void apatternWithoutAnamespaceKeepsItsSlash() {
        // item:*/ore is a pattern across all namespaces — the slash belongs
        // to the path and separates nothing here.
        Expr.Selector pattern = Selectors.parse("item:*_ore");
        assertEquals("*_ore", pattern.path());
        assertNull(pattern.namespace());
    }

    @Test
    @DisplayName("What is no selection gives nothing")
    void whatIsNoSelectorGivesNothing() {
        assertNull(Selectors.parse("quarry_output"));
        assertNull(Selectors.parse("unfug:etwas"));
        assertNull(Selectors.parse(""));
    }
}
