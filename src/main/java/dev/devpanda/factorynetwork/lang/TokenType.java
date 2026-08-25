package dev.devpanda.factorynetwork.lang;

import java.util.Map;

/**
 * Die Token-Arten von Manifold.
 *
 * <p>Schlüsselwörter sind eigene Arten und nicht Namen mit einer Kennzeichnung.
 * Das kostet hier eine lange Liste, spart aber im Parser bei jedem Vergleich
 * eine Abfrage auf den Textinhalt.
 */
public enum TokenType {
    // Ablauf
    IF, ELSE, FOR, IN, WHILE, BREAK, CONTINUE, RETURN, FN, LET,

    // Werte
    TRUE, FALSE, IT,

    // Deklarationen
    WORKER, GROUP, MULTIBLOCK, EVENT, DISPLAY, ON, IMPORT, GLOBAL, CONST, POWER,

    // Worker
    FROM, TO, FILTER, MAINTAIN, RATE, PER, WHEN, PRIORITY, STRATEGY, OVERFLOW,

    // Gruppen und Multiblocks
    MEMBERS, DEVICES,

    // Display
    TITLE, ROW, TEXT, PROGRESS, INDICATOR, LIST, BUTTON,

    // Ereignisse
    EMIT, AWAIT, WHERE, TIMEOUT, SLEEP,

    // Auswahl
    MOVE, EXCEPT,

    // Eingebaute Geräte
    STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS,

    // Literale und Namen
    INT, FLOAT, STRING, DURATION, SELECTOR, NAME, ESCAPED_NAME, NAME_PATTERN,

    // Operatoren
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, EQ_EQ, BANG, BANG_EQ, LT, LT_EQ, GT, GT_EQ, AND_AND, OR_OR,

    // Zeichen
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA, DOT, COLON,

    // Struktur
    NL, EOF,

    /** Steht für einen Abschnitt, den der Lexer nicht deuten konnte. */
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
            Map.entry("power", POWER),
            Map.entry("from", FROM), Map.entry("to", TO), Map.entry("filter", FILTER),
            Map.entry("maintain", MAINTAIN), Map.entry("rate", RATE), Map.entry("per", PER),
            Map.entry("when", WHEN), Map.entry("priority", PRIORITY),
            Map.entry("strategy", STRATEGY), Map.entry("overflow", OVERFLOW),
            Map.entry("members", MEMBERS), Map.entry("devices", DEVICES),
            Map.entry("title", TITLE), Map.entry("row", ROW), Map.entry("text", TEXT),
            Map.entry("progress", PROGRESS), Map.entry("indicator", INDICATOR),
            Map.entry("list", LIST), Map.entry("button", BUTTON),
            Map.entry("emit", EMIT), Map.entry("await", AWAIT), Map.entry("where", WHERE),
            Map.entry("timeout", TIMEOUT), Map.entry("sleep", SLEEP),
            Map.entry("move", MOVE), Map.entry("except", EXCEPT),
            Map.entry("storage", STORAGE), Map.entry("crafting", CRAFTING),
            Map.entry("world", WORLD), Map.entry("network", NETWORK),
            Map.entry("workers", WORKERS), Map.entry("multiblocks", MULTIBLOCKS));

    /** Liefert die Schlüsselwortart zu einem Bezeichner, oder {@code null}. */
    public static TokenType keyword(String text) {
        return KEYWORDS.get(text);
    }

    public static boolean isKeyword(String text) {
        return KEYWORDS.containsKey(text);
    }

    /**
     * Ein Wert, der in einem Ausdruck für sich stehen kann — gebraucht für die
     * Regel, wann ein Zeilenumbruch eine Anweisung beendet.
     */
    public boolean endsExpression() {
        return switch (this) {
            case INT, FLOAT, STRING, DURATION, SELECTOR, NAME, ESCAPED_NAME, NAME_PATTERN,
                 TRUE, FALSE, IT, RPAREN, RBRACE, POWER,
                 STORAGE, CRAFTING, WORLD, NETWORK, WORKERS, MULTIBLOCKS -> true;
            default -> false;
        };
    }
}
