package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerTest {

    private static List<Token> tokensOf(String source) {
        Lexer.LexResult result = Lexer.tokenize(source);
        assertFalse(result.hasErrors(), () -> "Unerwartete Fehler: " + result.diagnostics());
        return result.tokens();
    }

    /** Die Token-Arten ohne NL und EOF — für Tests, die nur die Form prüfen. */
    private static List<TokenType> typesOf(String source) {
        return tokensOf(source).stream()
                .map(Token::type)
                .filter(t -> t != TokenType.NL && t != TokenType.EOF)
                .toList();
    }

    @Nested
    @DisplayName("Auswahlausdrücke gegen Typangaben")
    class Selectors {

        @Test
        void selectorIsOneToken() {
            List<Token> tokens = tokensOf("item:iron_ingot");
            assertEquals(TokenType.SELECTOR, tokens.get(0).type());
            assertEquals("item:iron_ingot", tokens.get(0).text());
        }

        @Test
        void selectorWithNamespace() {
            List<Token> tokens = tokensOf("item:allthemodium/allthemodium_ingot");
            assertEquals(TokenType.SELECTOR, tokens.get(0).type());
            assertEquals("item:allthemodium/allthemodium_ingot", tokens.get(0).text());
        }

        @Test
        void selectorWithPatternInTheMiddle() {
            assertEquals("item:*aluminum*", tokensOf("item:*aluminum*").get(0).text());
            assertEquals("item:deepslate_*_ore", tokensOf("item:deepslate_*_ore").get(0).text());
        }

        @Test
        @DisplayName("item: Item ist eine Typangabe, kein Auswahlausdruck")
        void typeAnnotationIsNotASelector() {
            assertEquals(
                    List.of(TokenType.NAME, TokenType.COLON, TokenType.NAME),
                    typesOf("item: Item"));
        }

        @Test
        @DisplayName("Der Leerraum hinter dem Doppelpunkt entscheidet")
        void wholeParameterList() {
            assertEquals(
                    List.of(TokenType.FN, TokenType.NAME, TokenType.LPAREN,
                            TokenType.NAME, TokenType.COLON, TokenType.NAME,
                            TokenType.COMMA,
                            TokenType.NAME, TokenType.COLON, TokenType.NAME,
                            TokenType.RPAREN),
                    typesOf("fn keepStock(item: Item, amount: Int)"));
        }
    }

    @Nested
    @DisplayName("Zeitangaben")
    class Durations {

        @Test
        void unitsAreParts0fTheToken() {
            assertEquals(List.of(TokenType.DURATION), typesOf("30s"));
            assertEquals(List.of(TokenType.DURATION), typesOf("100t"));
            assertEquals(List.of(TokenType.DURATION), typesOf("5min"));
            assertEquals(List.of(TokenType.DURATION), typesOf("2h"));
            assertEquals(List.of(TokenType.DURATION), typesOf("0.5s"));
        }

        @Test
        @DisplayName("30 s mit Leerzeichen ist keine Zeitangabe")
        void spaceSeparatesNumberAndName() {
            assertEquals(List.of(TokenType.INT, TokenType.NAME), typesOf("30 s"));
        }

        @Test
        void unknownUnitIsReported() {
            Lexer.LexResult result = Lexer.tokenize("30x");
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().get(0).message().contains("Einheit"));
            assertTrue(result.diagnostics().get(0).hint().contains("Ticks"));
        }

        @Test
        void plainNumbersStayNumbers() {
            assertEquals(List.of(TokenType.INT), typesOf("64"));
            assertEquals(List.of(TokenType.FLOAT), typesOf("0.9"));
        }
    }

    @Nested
    @DisplayName("Zeilenumbrüche")
    class Newlines {

        private static long newlineCount(String source) {
            return tokensOf(source).stream().filter(t -> t.is(TokenType.NL)).count();
        }

        @Test
        void lineEndTerminatesStatement() {
            assertEquals(2, newlineCount("let a = 1\nlet b = 2"));
        }

        @Test
        @DisplayName("Nach einem Operator geht die Zeile weiter")
        void operatorContinuesLine() {
            assertEquals(1, newlineCount("let a = 1 +\n2"));
        }

        @Test
        @DisplayName("In offenen Klammern beendet ein Umbruch nichts")
        void openParenSuppressesNewline() {
            assertEquals(1, newlineCount("foo(1,\n2,\n3)"));
        }

        @Test
        @DisplayName("Ein Punkt am Zeilenanfang setzt die Kette fort")
        void leadingDotContinuesChain() {
            assertEquals(1, newlineCount("let a = crushers.members()\n    .where(it.busy)"));
        }

        @Test
        @DisplayName("Vor else steht nie ein Zeilenende")
        void elseContinuesLine() {
            // Nach { und } gehören Umbrüche hin — die Grammatik verlangt sie.
            // Geprüft wird deshalb genau die eine Stelle, um die es geht.
            List<Token> tokens = tokensOf("if a {\n}\nelse {\n}");
            for (int i = 1; i < tokens.size(); i++) {
                if (tokens.get(i).is(TokenType.ELSE)) {
                    assertFalse(tokens.get(i - 1).is(TokenType.NL),
                            "Vor else darf kein NL stehen, sonst endet die if-Anweisung dort");
                }
            }
        }

        @Test
        void commentsDoNotBreakTheRule() {
            assertEquals(1, newlineCount("let a = 1 + // Zwischenstand\n2"));
        }
    }

    @Nested
    @DisplayName("Namen")
    class Names {

        @Test
        void keywordsAreOwnTypes() {
            assertEquals(
                    List.of(TokenType.WORKER, TokenType.NAME, TokenType.LBRACE, TokenType.RBRACE),
                    typesOf("worker quarry_import { }"));
        }

        @Test
        @DisplayName("Ein Connector darf wie ein Schlüsselwort heißen")
        void backticksMakeAKeywordAName() {
            List<Token> tokens = tokensOf("`for`.insert(1)");
            assertEquals(TokenType.ESCAPED_NAME, tokens.get(0).type());
            assertEquals("for", tokens.get(0).text());
        }

        @Test
        @DisplayName("Umlaute sind in Namen erlaubt")
        void umlautsAreAllowed() {
            List<Token> tokens = tokensOf("let ofen_süd = 1");
            assertEquals(TokenType.NAME, tokens.get(1).type());
            assertEquals("ofen_süd", tokens.get(1).text());
        }

        @Test
        @DisplayName("Zerlegtes und zusammengesetztes ü sind derselbe Name")
        void namesAreNormalizedToNfc() {
            String composed = "ofen_süd";
            String decomposed = "ofen_süd";
            assertEquals(
                    tokensOf(composed).get(0).text(),
                    tokensOf(decomposed).get(0).text());
        }

        @Test
        void namePatternIsItsOwnType() {
            assertEquals(List.of(TokenType.NAME_PATTERN), typesOf("furnace_*"));
        }
    }

    @Nested
    @DisplayName("Meldungen")
    class Diagnostics {

        @Test
        void unterminatedStringIsReported() {
            Lexer.LexResult result = Lexer.tokenize("let a = \"offen");
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().get(0).message().contains("Text"));
        }

        @Test
        void unterminatedBacktickIsReported() {
            Lexer.LexResult result = Lexer.tokenize("let a = `offen");
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().get(0).message().contains("Rückstrichen"));
        }

        @Test
        @DisplayName("Ein einzelnes & schlägt das doppelte vor")
        void singleAmpersandSuggestsTheDouble() {
            Lexer.LexResult result = Lexer.tokenize("a & b");
            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().get(0).hint().contains("&&"));
        }

        @Test
        void positionIsLineAndColumn() {
            Lexer.LexResult result = Lexer.tokenize("let a = 1\nlet b = 30x");
            assertEquals(2, result.diagnostics().get(0).span().line());
        }
    }

    @Test
    @DisplayName("Ein vollständiger Worker zerfällt in die erwarteten Token")
    void completeWorker() {
        List<TokenType> types = typesOf("""
                worker fuel_supply {
                    from storage
                    to generators
                    filter tag:c/coals
                    maintain 64
                }""");
        assertEquals(TokenType.WORKER, types.get(0));
        assertTrue(types.contains(TokenType.FROM));
        assertTrue(types.contains(TokenType.STORAGE));
        assertTrue(types.contains(TokenType.SELECTOR));
        assertTrue(types.contains(TokenType.MAINTAIN));
    }
}
