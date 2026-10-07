package dev.devpanda.factorynetwork.lang;

import java.util.Map;

/**
 * The token kinds of Manifold.
 *
 * <p>Keywords are their own kinds, not names carrying a marker. That costs a
 * long list here, but saves the parser a check on the text content at every
 * comparison.
 */
public enum TokenType {
    // Control flow
    IF, ELSE, FOR, IN, WHILE, BREAK, CONTINUE, RETURN, FN, LET,

    // Values
    TRUE, FALSE, IT,

    // Declarations
    WORKER, GROUP, MULTIBLOCK, EVENT, DISPLAY, ON, IMPORT, GLOBAL, CONST, POWER, ALL,

    // Worker
    FROM, TO, FILTER, MAINTAIN, RATE, PER, WHEN, PRIORITY, STRATEGY, OVERFLOW,

    // Groups and multiblocks
    MEMBERS, DEVICES,

    // Display
    TITLE, ROW, TEXT, PROGRESS, INDICATOR, LIST, BUTTON, SCALE,

    // Recipes at machines
    RECIPE, AT, OUT, STORE,

    // Events
    EMIT, AWAIT, WHERE, TIMEOUT, SLEEP,

    // Selection
    MOVE, EXCEPT,

    // Built-in devices
    STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS,

    // Literals and names
    INT, FLOAT, STRING, DURATION, SELECTOR, NAME, ESCAPED_NAME, NAME_PATTERN,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, EQ_EQ, BANG, BANG_EQ, LT, LT_EQ, GT, GT_EQ, AND_AND, OR_OR,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, DOT_DOT, COLON,

    // Structure
    NL, EOF,

    /** Stands for a section the lexer could not interpret. */
    INVALID;

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("if", IF), Map.entry("else", ELSE), Map.entry("for", FOR),
            Map.entry("in", IN), Map.entry("while", WHILE), Map.entry("break", BREAK),
            Map.entry("continue", CONTINUE), Map.entry("return", RETURN),
            Map.entry("fn", FN), Map.entry("let", LET),
            Map.entry("true", TRUE), Map.entry("false", FALSE), Map.entry("it", IT),
            Map.entry("worker", WORKER), Map.entry("group", GROUP),
            Map.entry("multiblock", MULTIBLOCK), Map.entry("event", EVENT),
            Map.entry("display", DISPLAY), Map.entry("on", ON), Map.entry("import", IMPORT),
            Map.entry("global", GLOBAL), Map.entry("const", CONST),
            Map.entry("power", POWER), Map.entry("all", ALL),
            Map.entry("from", FROM), Map.entry("to", TO), Map.entry("filter", FILTER),
            Map.entry("maintain", MAINTAIN), Map.entry("rate", RATE), Map.entry("per", PER),
            Map.entry("when", WHEN), Map.entry("priority", PRIORITY),
            Map.entry("strategy", STRATEGY), Map.entry("overflow", OVERFLOW),
            Map.entry("members", MEMBERS), Map.entry("devices", DEVICES),
            Map.entry("title", TITLE), Map.entry("row", ROW), Map.entry("text", TEXT),
            Map.entry("progress", PROGRESS), Map.entry("indicator", INDICATOR),
            Map.entry("list", LIST), Map.entry("button", BUTTON),
            Map.entry("scale", SCALE),
            Map.entry("recipe", RECIPE), Map.entry("at", AT), Map.entry("out", OUT),
            Map.entry("store", STORE),
            Map.entry("emit", EMIT), Map.entry("await", AWAIT), Map.entry("where", WHERE),
            Map.entry("timeout", TIMEOUT), Map.entry("sleep", SLEEP),
            Map.entry("move", MOVE), Map.entry("except", EXCEPT),
            Map.entry("storage", STORAGE), Map.entry("crafting", CRAFTING),
            Map.entry("world", WORLD), Map.entry("network", NETWORK),
            Map.entry("workers", WORKERS), Map.entry("multiblocks", MULTIBLOCKS));

    /**
     * Every word that belongs to the language.
     *
     * <p>Used by {@code GrammarKeywordTest}: the highlighting in VS Code keeps
     * the same list a second time, in a TextMate grammar. On Aug 27 it had
     * already fallen five words behind once — {@code store}, {@code recipe},
     * {@code scale}, {@code at} and {@code out} were missing from it, and in
     * the editor they stayed colorless. Nobody who knows the language notices
     * that, and everybody who is learning it gets confused.
     */
    public static java.util.Set<String> keywords() {
        return KEYWORDS.keySet();
    }

    /** Returns the keyword kind for an identifier, or {@code null}. */
    public static TokenType keyword(String text) {
        return KEYWORDS.get(text);
    }

    public static boolean isKeyword(String text) {
        return KEYWORDS.containsKey(text);
    }

    /**
     * A value that can stand on its own in an expression — used for the rule
     * of when a line break ends a statement.
     */
    public boolean endsExpression() {
        return switch (this) {
            case INT, FLOAT, STRING, DURATION, SELECTOR, NAME, ESCAPED_NAME, NAME_PATTERN,
                 TRUE, FALSE, IT, RPAREN, RBRACE, POWER, ALL,
                 STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS -> true;
            default -> false;
        };
    }
}
