package dev.devpanda.factorynetwork.lang;

/**
 * Ein Token mit seiner Stelle im Quelltext.
 *
 * <p>{@code text} ist der Quelltext, wie er dastand — bei {@code ESCAPED_NAME}
 * ohne die Rückstriche, bei {@code STRING} ohne die Anführungszeichen. Der
 * Parser bekommt damit schon den Namen, den der Spieler gemeint hat.
 */
public record Token(TokenType type, String text, Span span) {

    public boolean is(TokenType other) {
        return type == other;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@" + span;
    }
}
