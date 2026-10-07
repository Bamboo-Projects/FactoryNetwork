package dev.devpanda.factorynetwork.lang;

/**
 * A token together with its place in the source text.
 *
 * <p>{@code text} is the source text as it stood — for {@code ESCAPED_NAME}
 * without the backticks, for {@code STRING} without the quotation marks. That
 * way the parser already gets the name the player meant.
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
