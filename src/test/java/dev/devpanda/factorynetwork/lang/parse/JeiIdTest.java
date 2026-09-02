package dev.devpanda.factorynetwork.lang.parse;

import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ID copied from JEI is accepted.
 *
 * <p><b>Everyone copies IDs from JEI</b>, and there it reads
 * {@code mekanism:steel_ingot}. Whoever turned that into
 * {@code item:mekanism:steel_ingot} first got seven error messages on one
 * line, then one that named the correct spelling — and still had to correct
 * it by hand for every copied ID.
 *
 * <p>Since 25.08. both forms are valid. The colon separates namespace and
 * path just like the slash does; which one someone writes is their own
 * business.
 */
class JeiIdTest {

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    /** The selection from the {@code filter} line of the first worker. */
    private static Expr.Selector selectorOf(String source) {
        Parser.ParseResult result = Parser.parse(source);
        assertTrue(result.diagnostics().stream().noneMatch(Diagnostic::isError),
                () -> "unerwartete Meldung: " + result.diagnostics());
        Decl.Worker worker = assertInstanceOf(Decl.Worker.class,
                result.program().declarations().get(0));
        return assertInstanceOf(Expr.Selector.class,
                worker.entry(Decl.Worker.Entry.Kind.FILTER).value());
    }

    @Test
    @DisplayName("The JEI spelling is accepted")
    void theJeiFormIsAccepted() {
        Expr.Selector selector = selectorOf("""
                worker w {
                    from lager
                    to ofen
                    filter item:mekanism:steel_ingot
                }""");

        assertEquals(Expr.Selector.Kind.ITEM, selector.kind());
        assertEquals("mekanism", selector.namespace());
        assertEquals("steel_ingot", selector.path());
    }

    @Test
    @DisplayName("It means the same as the form with a slash")
    void bothFormsMeanTheSame() {
        Expr.Selector mitDoppelpunkt = selectorOf("""
                worker w {
                    from lager
                    to ofen
                    filter item:mekanism:steel_ingot
                }""");
        Expr.Selector mitSchraegstrich = selectorOf("""
                worker w {
                    from lager
                    to ofen
                    filter item:mekanism/steel_ingot
                }""");

        assertEquals(mitSchraegstrich.kind(), mitDoppelpunkt.kind());
        assertEquals(mitSchraegstrich.namespace(), mitDoppelpunkt.namespace());
        assertEquals(mitSchraegstrich.path(), mitDoppelpunkt.path());
    }

    @Test
    @DisplayName("A tag from JEI likewise — the path may keep slashes")
    void aTagFromJeiToo() {
        Expr.Selector selector = selectorOf("""
                worker w {
                    from lager
                    to ofen
                    filter tag:c:ingots/iron
                }""");

        assertEquals(Expr.Selector.Kind.TAG, selector.kind());
        assertEquals("c", selector.namespace());
        assertEquals("ingots/iron", selector.path());
    }

    @Test
    @DisplayName("In a move as well, without a message")
    void insideAMoveToo() {
        assertTrue(errorsOf("""
                fn t() {
                    move 1 item:mekanism:steel_ingot from lager to ofen
                }""").isEmpty(), "die JEI-Form ist kein Fehler mehr");
    }

    @Test
    @DisplayName("The form with a slash stays error-free")
    void theSlashFormStaysClean() {
        assertTrue(errorsOf("""
                fn t() {
                    move 1 item:mekanism/steel_ingot from lager to ofen
                    move 1 tag:c/ores from lager to ofen
                    move 1 item:iron_ore from lager to ofen
                }""").isEmpty(), "daran darf sich nichts geändert haben");
    }

    @Test
    @DisplayName("A colon with no selection after it stays outside the selection")
    void aTrailingColonStaysOutsideTheSelector() {
        // While typing, the colon briefly stands alone. If the lexer
        // swallowed the rest of the line, completion would run on a half
        // expression at every keystroke — which is why the second colon is
        // read along only when a selection really follows.
        var tokens = dev.devpanda.factorynetwork.lang.Lexer
                .tokenize("move 1 item:iron_ore: from a to b").tokens();
        var auswahl = tokens.stream()
                .filter(token -> token.type()
                        == dev.devpanda.factorynetwork.lang.TokenType.SELECTOR)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Die Auswahl fehlt ganz: " + tokens));

        assertEquals("item:iron_ore", auswahl.text(),
                "Der Doppelpunkt am Ende gehört nicht mehr dazu");
        assertTrue(tokens.stream().anyMatch(token -> token.type()
                        == dev.devpanda.factorynetwork.lang.TokenType.FROM),
                () -> "from muss ein from bleiben: " + tokens);
        assertTrue(tokens.stream().anyMatch(token -> token.type()
                        == dev.devpanda.factorynetwork.lang.TokenType.TO),
                () -> "und to ein to: " + tokens);
    }

    @Test
    @DisplayName("fluidtag: is a kind of its own")
    void aFluidTagIsItsOwnKind() {
        Expr.Selector selector = selectorOf("""
                worker w {
                    from bottich
                    to kessel
                    filter fluidtag:c/molten
                }""");

        assertEquals(Expr.Selector.Kind.FLUIDTAG, selector.kind());
        assertEquals("c", selector.namespace());
        assertEquals("molten", selector.path());
    }

    @Test
    @DisplayName("Also in the JEI spelling")
    void aFluidTagFromJei() {
        Expr.Selector selector = selectorOf("""
                worker w {
                    from bottich
                    to kessel
                    filter fluidtag:c:molten
                }""");

        assertEquals(Expr.Selector.Kind.FLUIDTAG, selector.kind());
        assertEquals("c", selector.namespace());
        assertEquals("molten", selector.path());
    }
}
