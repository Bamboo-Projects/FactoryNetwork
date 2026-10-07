package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words before the colon, as the language knows them on its own.
 *
 * <p>That the language needs no game is not checked here: it is compiled
 * without one, as the core source set, and a reach for the game fails the
 * build. What is checked is what the language has to know without anybody
 * telling it.
 */
class SelectorWordsTest {

    @Test
    @DisplayName("The five words of the language are known without anybody registering them")
    void theBuiltInWordsAreAlwaysThere() {
        assertTrue(SelectorWords.all().containsAll(
                Set.of("item", "fluid", "chemical", "tag", "fluidtag")));
        assertEquals(Expr.Selector.Kind.ITEM, SelectorWords.kindOf("item"));
        assertEquals(Expr.Selector.Kind.FLUIDTAG, SelectorWords.kindOf("fluidtag"));
        assertNull(SelectorWords.kindOf("nosuchword"));
    }

    @Test
    @DisplayName("Without a game, chemical counts as missing its mod and the others as usable")
    void onlyChemicalNeedsAmod() {
        assertNull(SelectorWords.unavailable("item"));
        assertNull(SelectorWords.unavailable("fluid"));
        // Either the default or the kind's own answer — both say the mod is
        // missing in a run without a mod list.
        SelectorWords.Unavailable chemical = SelectorWords.unavailable("chemical");
        assertNotNull(chemical);
        assertTrue(chemical.message().contains("Mekanism"));
    }
}
