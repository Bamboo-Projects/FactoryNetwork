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
 * Eine aus JEI kopierte ID wird angenommen.
 *
 * <p><b>Jeder kopiert IDs aus JEI</b>, und dort steht
 * {@code mekanism:steel_ingot}. Wer daraus {@code item:mekanism:steel_ingot}
 * machte, bekam zuerst sieben Fehlermeldungen in einer Zeile, dann eine, die
 * die richtige Schreibweise nannte — und musste sie trotzdem bei jeder
 * kopierten ID von Hand berichtigen.
 *
 * <p>Seit dem 25.08. gelten beide Formen. Der Doppelpunkt trennt Namensraum
 * und Pfad genauso wie der Schrägstrich; welchen jemand schreibt, ist seine
 * Sache.
 */
class JeiIdTest {

    private static List<Diagnostic> errorsOf(String source) {
        return Parser.parse(source).diagnostics().stream()
                .filter(Diagnostic::isError)
                .toList();
    }

    /** Die Auswahl aus der {@code filter}-Zeile des ersten Workers. */
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
    @DisplayName("Die JEI-Schreibweise wird angenommen")
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
    @DisplayName("Sie meint dasselbe wie die Form mit Schrägstrich")
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
    @DisplayName("Ein Tag aus JEI ebenso — der Pfad darf Schrägstriche behalten")
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
    @DisplayName("In einem move ebenfalls, ohne Meldung")
    void insideAMoveToo() {
        assertTrue(errorsOf("""
                fn t() {
                    move 1 item:mekanism:steel_ingot from lager to ofen
                }""").isEmpty(), "die JEI-Form ist kein Fehler mehr");
    }

    @Test
    @DisplayName("Die Form mit Schrägstrich bleibt fehlerfrei")
    void theSlashFormStaysClean() {
        assertTrue(errorsOf("""
                fn t() {
                    move 1 item:mekanism/steel_ingot from lager to ofen
                    move 1 tag:c/ores from lager to ofen
                    move 1 item:iron_ore from lager to ofen
                }""").isEmpty(), "daran darf sich nichts geändert haben");
    }

    @Test
    @DisplayName("Ein Doppelpunkt ohne Auswahl dahinter bleibt außerhalb der Auswahl")
    void aTrailingColonStaysOutsideTheSelector() {
        // Beim Tippen steht der Doppelpunkt kurz allein da. Verschluckte der
        // Lexer den Rest der Zeile, liefe die Vervollständigung bei jedem
        // Tastendruck auf einem halben Ausdruck — deshalb wird der zweite
        // Doppelpunkt nur mitgelesen, wenn wirklich eine Auswahl folgt.
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
    @DisplayName("fluidtag: ist eine eigene Art")
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
    @DisplayName("Auch in der JEI-Schreibweise")
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
