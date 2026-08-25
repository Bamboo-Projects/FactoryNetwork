package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Der Selektor unter dem Zeiger, und was aus seiner geschriebenen Form wird.
 *
 * <p>Beides ohne Welt prüfbar: Das eine ist Textarbeit, das andere eine
 * Zerlegung. Erst die Auflösung braucht eine Registry, und die steht in den
 * GameTests.
 */
class SelectorsTest {

    @Test
    @DisplayName("Der Zeiger mitten im Pfad meint den ganzen Selektor")
    void insideThePathTheWholeSelectorIsMeant() {
        String line = "    filter item:iron_ore";
        assertEquals("item:iron_ore", Selectors.at(line, line.indexOf("iron")));
    }

    @Test
    @DisplayName("Auch auf der Art davor")
    void alsoOnTheKind() {
        String line = "    filter tag:c/ores";
        assertEquals("tag:c/ores", Selectors.at(line, line.indexOf("tag")));
    }

    @Test
    @DisplayName("Ein Muster gehört dazu")
    void apatternBelongsToIt() {
        String line = "move item:*_ore from a to b";
        assertEquals("item:*_ore", Selectors.at(line, line.indexOf('*')));
    }

    @Test
    @DisplayName("Neben einem Selektor ist keiner")
    void besideASelectorThereIsNone() {
        String line = "    filter item:iron_ore";
        assertEquals("", Selectors.at(line, 2));
        assertEquals("", Selectors.at(line, line.length()));
    }

    @Test
    @DisplayName("Ein Wort ohne Doppelpunkt ist kein Selektor")
    void awordWithoutAcolonIsNoSelector() {
        String line = "    from quarry_output";
        assertEquals("", Selectors.at(line, line.indexOf("quarry")));
    }

    @Test
    @DisplayName("Aus der geschriebenen Form wird wieder eine Auswahl")
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
    @DisplayName("Ein Muster ohne Namensraum behält seinen Schrägstrich")
    void apatternWithoutAnamespaceKeepsItsSlash() {
        // item:*/ore ist ein Muster über alle Namensräume — der Schrägstrich
        // gehört zum Pfad und trennt hier nichts ab.
        Expr.Selector pattern = Selectors.parse("item:*_ore");
        assertEquals("*_ore", pattern.path());
        assertNull(pattern.namespace());
    }

    @Test
    @DisplayName("Was keine Auswahl ist, gibt nichts")
    void whatIsNoSelectorGivesNothing() {
        assertNull(Selectors.parse("quarry_output"));
        assertNull(Selectors.parse("unfug:etwas"));
        assertNull(Selectors.parse(""));
    }
}
