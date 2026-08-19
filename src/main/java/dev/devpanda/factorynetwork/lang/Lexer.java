package dev.devpanda.factorynetwork.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Zerlegt Manifold-Quelltext in Token.
 *
 * <p>Drei Dinge entscheidet der Lexer, die sonst im Parser landen würden:
 *
 * <ul>
 *   <li><b>Zeilenumbrüche.</b> Manifold beendet Anweisungen mit dem
 *       Zeilenende. Ein Umbruch wird nur dann zum Token, wenn die Zeile
 *       überhaupt zu Ende sein kann — nach einem Operator oder innerhalb einer
 *       offenen Klammer ist sie es nicht.
 *   <li><b>Zeitangaben.</b> 30s ist ein Token, nicht die Zahl 30 gefolgt von
 *       einem Namen. Sonst wäre "30 s" dasselbe.
 *   <li><b>Auswahlausdrücke.</b> item:iron_ingot ist ein Token. Das muss hier
 *       entschieden werden, weil eine Typangabe genauso beginnt — der
 *       Unterschied ist der Leerraum nach dem Doppelpunkt.
 * </ul>
 */
public final class Lexer {

    private static final Set<String> SELECTOR_KINDS = Set.of("item", "fluid", "chemical", "tag");
    private static final Set<String> TIME_UNITS = Set.of("t", "s", "min", "h");

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private int pos;
    private int line = 1;
    private int lineStart;
    /** Tiefe offener runder Klammern — darin beendet ein Umbruch nichts. */
    private int parenDepth;

    public Lexer(String source) {
        // NFC, damit zwei sichtbar gleiche Namen auch gleich sind.
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
        // Ein abschließender Umbruch, damit die letzte Anweisung endet.
        if (!tokens.isEmpty() && lastType() != TokenType.NL) {
            add(TokenType.NL, "\n", pos, pos);
        }
        add(TokenType.EOF, "", pos, pos);
    }

    // ---- Zeilenumbrüche ---------------------------------------------------

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
     * Beendet ein Umbruch an dieser Stelle eine Anweisung? Nur wenn das
     * vorherige Token einen Ausdruck abschließen kann, keine Klammer offen
     * ist, und die nächste Zeile nicht mit einem Punkt oder mit else
     * weitermacht.
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

    /** Blickt über Leerraum und Kommentare hinweg auf den nächsten Inhalt. */
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
        // Eine unmittelbar folgende Einheit macht daraus eine Zeitangabe.
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

        // item:iron_ingot ist eine Auswahl, "item: Item" eine Typangabe.
        // Den Unterschied macht der Leerraum hinter dem Doppelpunkt.
        if (SELECTOR_KINDS.contains(text) && !atEnd() && peek() == ':'
                && pos + 1 < source.length() && isSelectorPart(source.charAt(pos + 1))) {
            advance();
            while (!atEnd() && isSelectorPart(peek())) {
                advance();
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
            case '{' -> add(TokenType.LBRACE, "{", start, pos);
            case '}' -> add(TokenType.RBRACE, "}", start, pos);
            case ',' -> add(TokenType.COMMA, ",", start, pos);
            case '.' -> add(TokenType.DOT, ".", start, pos);
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

    // ---- Hilfen -----------------------------------------------------------

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
