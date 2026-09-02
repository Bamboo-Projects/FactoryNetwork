package dev.devpanda.factorynetwork.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Breaks Manifold source text into tokens.
 *
 * <p>The lexer decides three things that would otherwise land in the parser:
 *
 * <ul>
 *   <li><b>Line breaks.</b> Manifold ends statements at the end of the line. A
 *       break only becomes a token when the line can actually end there — after
 *       an operator, or inside an open bracket, it cannot.
 *   <li><b>Durations.</b> 30s is one token, not the number 30 followed by a
 *       name. Otherwise "30 s" would mean the same thing.
 *   <li><b>Selector expressions.</b> item:iron_ingot is one token. That has to
 *       be decided here, because a type annotation begins the same way — the
 *       difference is the whitespace after the colon.
 * </ul>
 */
public final class Lexer {

    /**
     * What gets glued together into a selector.
     *
     * <p>No longer fixed, but taken from the registry: since Aug 26 foreign
     * mods may register their own kinds, and {@code source:mana} then has to be
     * one word, not three. Queried on every call — the registry is fixed once
     * loading is done, and a cache here would be a second source of truth about
     * the same list.
     */
    private static Set<String> selectorKinds() {
        return dev.devpanda.factorynetwork.runtime.ResourceKinds.selectorPrefixes();
    }
    private static final Set<String> TIME_UNITS = Set.of("t", "s", "min", "h");

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private int pos;
    private int line = 1;
    private int lineStart;
    /** Depth of open parentheses — inside them a break ends nothing. */
    private int parenDepth;

    public Lexer(String source) {
        // NFC, so that two names that look the same really are the same.
        this.source = Normalizer.normalize(source, Normalizer.Form.NFC);
    }

    public static LexResult tokenize(String source) {
        Lexer lexer = new Lexer(source);
        lexer.run();
        return new LexResult(List.copyOf(lexer.tokens), List.copyOf(lexer.diagnostics));
    }

    public record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(Diagnostic::isError);
        }
    }

    private void run() {
        while (!atEnd()) {
            skipBlanksAndComments();
            if (atEnd()) {
                break;
            }
            if (peek() == '\n') {
                consumeNewline();
            } else {
                readToken();
            }
        }
        // A trailing break, so that the last statement ends.
        if (!tokens.isEmpty() && lastType() != TokenType.NL) {
            add(TokenType.NL, "\n", pos, pos);
        }
        add(TokenType.EOF, "", pos, pos);
    }

    // ---- Line breaks ------------------------------------------------------

    private void consumeNewline() {
        int start = pos;
        advance();
        line++;
        lineStart = pos;
        if (breaksStatement()) {
            add(TokenType.NL, "\n", start, start + 1);
        }
    }

    /**
     * Does a break at this point end a statement? Only if the previous token
     * can end an expression, no bracket is open, and the next line does not
     * continue with a dot or with else.
     */
    private boolean breaksStatement() {
        if (parenDepth > 0 || tokens.isEmpty()) {
            return false;
        }
        TokenType last = lastType();
        if (last == TokenType.NL) {
            return false;
        }
        boolean canEnd = last.endsExpression()
                || last == TokenType.LBRACE
                || last == TokenType.RBRACE
                || last == TokenType.BREAK
                || last == TokenType.CONTINUE
                || last == TokenType.RETURN;
        if (!canEnd) {
            return false;
        }
        return !continuesOnNextLine();
    }

    /** Looks past whitespace and comments to the next content. */
    private boolean continuesOnNextLine() {
        int look = pos;
        while (look < source.length()) {
            char c = source.charAt(look);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                look++;
            } else if (c == '/' && look + 1 < source.length() && source.charAt(look + 1) == '/') {
                while (look < source.length() && source.charAt(look) != '\n') {
                    look++;
                }
            } else {
                break;
            }
        }
        if (look >= source.length()) {
            return false;
        }
        if (source.charAt(look) == '.') {
            return true;
        }
        return source.startsWith("else", look)
                && (look + 4 >= source.length() || !isNamePart(source.charAt(look + 4)));
    }

    // ---- Token ------------------------------------------------------------

    private void readToken() {
        int start = pos;
        char c = peek();
        if (Character.isDigit(c)) {
            readNumberOrDuration(start);
        } else if (c == '"') {
            readString(start);
        } else if (c == '`') {
            readEscapedName(start);
        } else if (isNameStart(c)) {
            readNameLike(start);
        } else {
            readOperator(start);
        }
    }

    private void readNumberOrDuration(int start) {
        while (!atEnd() && Character.isDigit(peek())) {
            advance();
        }
        boolean isFloat = false;
        if (!atEnd() && peek() == '.' && pos + 1 < source.length()
                && Character.isDigit(source.charAt(pos + 1))) {
            isFloat = true;
            advance();
            while (!atEnd() && Character.isDigit(peek())) {
                advance();
            }
        }
        // A unit immediately following turns this into a duration.
        int unitStart = pos;
        while (!atEnd() && Character.isLetter(peek())) {
            advance();
        }
        String unit = source.substring(unitStart, pos);
        if (!unit.isEmpty()) {
            if (TIME_UNITS.contains(unit)) {
                add(TokenType.DURATION, source.substring(start, pos), start, pos);
            } else {
                add(TokenType.INVALID, source.substring(start, pos), start, pos);
                error(start, pos, "Unbekannte Einheit " + quote(unit) + ".",
                        "Erlaubt sind t für Ticks, s, min und h.");
            }
            return;
        }
        add(isFloat ? TokenType.FLOAT : TokenType.INT, source.substring(start, pos), start, pos);
    }

    private void readString(int start) {
        advance();
        StringBuilder text = new StringBuilder();
        while (!atEnd() && peek() != '"' && peek() != '\n') {
            text.append(peek());
            advance();
        }
        if (atEnd() || peek() == '\n') {
            add(TokenType.INVALID, text.toString(), start, pos);
            error(start, pos, "Der Text hört nicht auf.",
                    "Es fehlt ein abschließendes Anführungszeichen.");
            return;
        }
        advance();
        add(TokenType.STRING, text.toString(), start, pos);
    }

    private void readEscapedName(int start) {
        advance();
        StringBuilder text = new StringBuilder();
        while (!atEnd() && peek() != '`' && peek() != '\n') {
            text.append(peek());
            advance();
        }
        if (atEnd() || peek() == '\n') {
            add(TokenType.INVALID, text.toString(), start, pos);
            error(start, pos, "Der Name in Rückstrichen hört nicht auf.",
                    "Es fehlt ein abschließender Rückstrich.");
            return;
        }
        advance();
        add(TokenType.ESCAPED_NAME, text.toString(), start, pos);
    }

    private void readNameLike(int start) {
        while (!atEnd() && (isNamePart(peek()) || peek() == '*')) {
            advance();
        }
        String text = source.substring(start, pos);

        // item:iron_ingot is a selector, "item: Item" a type annotation.
        // The difference is the whitespace after the colon.
        if (selectorKinds().contains(text) && !atEnd() && peek() == ':'
                && pos + 1 < source.length() && isSelectorPart(source.charAt(pos + 1))) {
            advance();
            while (!atEnd() && isSelectorPart(peek())) {
                advance();
            }
            // A second colon belongs to it too — not because the language
            // knows it, but so that the parser gets to see it.
            //
            // <b>From JEI you copy mekanism:steel_ingot.</b> Anyone who turns
            // that into item:mekanism:steel_ingot got seven errors on one
            // line, none of which named the cause: "move is missing its
            // target", "from is a keyword". The lexer stopped at the colon,
            // and the rest of the line fell apart. Read as a single word it
            // becomes one message that names the correct spelling.
            //
            // Same condition as above: only read on when a selector character
            // follows. Otherwise a half-typed expression swallows the rest of
            // the line.
            if (!atEnd() && peek() == ':'
                    && pos + 1 < source.length() && isSelectorPart(source.charAt(pos + 1))) {
                advance();
                while (!atEnd() && isSelectorPart(peek())) {
                    advance();
                }
            }
            add(TokenType.SELECTOR, source.substring(start, pos), start, pos);
            return;
        }

        TokenType keyword = TokenType.keyword(text);
        if (keyword != null) {
            add(keyword, text, start, pos);
        } else if (text.indexOf('*') >= 0) {
            add(TokenType.NAME_PATTERN, text, start, pos);
        } else {
            add(TokenType.NAME, text, start, pos);
        }
    }

    private void readOperator(int start) {
        char c = peek();
        advance();
        switch (c) {
            case '(' -> { parenDepth++; add(TokenType.LPAREN, "(", start, pos); }
            case ')' -> { if (parenDepth > 0) { parenDepth--; } add(TokenType.RPAREN, ")", start, pos); }
            // Square brackets count like round ones: what stands between them
            // is an expression, and an expression does not end at a line break.
            // Nobody writes a list of six names on a single line.
            case '[' -> { parenDepth++; add(TokenType.LBRACKET, "[", start, pos); }
            case ']' -> {
                if (parenDepth > 0) {
                    parenDepth--;
                }
                add(TokenType.RBRACKET, "]", start, pos);
            }
            case '{' -> add(TokenType.LBRACE, "{", start, pos);
            case '}' -> add(TokenType.RBRACE, "}", start, pos);
            case ',' -> add(TokenType.COMMA, ",", start, pos);
            // Two dots are a range: 1..5. A decimal number never reaches
            // here — readNumberOrDuration only takes the dot along when a
            // digit follows.
            case '.' -> {
                if (!atEnd() && peek() == '.') {
                    advance();
                    add(TokenType.DOT_DOT, "..", start, pos);
                } else {
                    add(TokenType.DOT, ".", start, pos);
                }
            }
            case ':' -> add(TokenType.COLON, ":", start, pos);
            case '+' -> add(TokenType.PLUS, "+", start, pos);
            case '-' -> add(TokenType.MINUS, "-", start, pos);
            case '*' -> add(TokenType.STAR, "*", start, pos);
            case '/' -> add(TokenType.SLASH, "/", start, pos);
            case '%' -> add(TokenType.PERCENT, "%", start, pos);
            case '=' -> addTwo('=', TokenType.EQ_EQ, TokenType.EQ, start);
            case '!' -> addTwo('=', TokenType.BANG_EQ, TokenType.BANG, start);
            case '<' -> addTwo('=', TokenType.LT_EQ, TokenType.LT, start);
            case '>' -> addTwo('=', TokenType.GT_EQ, TokenType.GT, start);
            case '&' -> addPaired('&', TokenType.AND_AND, start, "&&");
            case '|' -> addPaired('|', TokenType.OR_OR, start, "||");
            default -> {
                add(TokenType.INVALID, String.valueOf(c), start, pos);
                error(start, pos,
                        "Mit dem Zeichen " + quote(String.valueOf(c)) + " kann Manifold hier nichts anfangen.",
                        null);
            }
        }
    }

    private void addTwo(char second, TokenType ifPaired, TokenType ifAlone, int start) {
        if (!atEnd() && peek() == second) {
            advance();
            add(ifPaired, source.substring(start, pos), start, pos);
        } else {
            add(ifAlone, source.substring(start, pos), start, pos);
        }
    }

    private void addPaired(char second, TokenType paired, int start, String expected) {
        if (!atEnd() && peek() == second) {
            advance();
            add(paired, expected, start, pos);
        } else {
            add(TokenType.INVALID, source.substring(start, pos), start, pos);
            error(start, pos, "Hier fehlt ein Zeichen.",
                    "Gemeint ist vermutlich " + quote(expected) + ".");
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private void skipBlanksAndComments() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
            } else {
                return;
            }
        }
    }

    private static String quote(String text) {
        return "„" + text + "“";
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isSelectorPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '*' || c == '/'
                || c == '.' || c == '-';
    }

    private boolean atEnd() {
        return pos >= source.length();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private void advance() {
        pos++;
    }

    private TokenType lastType() {
        return tokens.get(tokens.size() - 1).type();
    }

    private Span spanOf(int start, int end) {
        return new Span(start, end, line, start - lineStart + 1);
    }

    private void error(int start, int end, String message, String hint) {
        diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, spanOf(start, end), message, hint));
    }

    private void add(TokenType type, String text, int start, int end) {
        tokens.add(new Token(type, text, spanOf(start, end)));
    }
}
