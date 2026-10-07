package dev.devpanda.factorynetwork.lang;

/**
 * A range in the source text, from {@code start} up to but excluding
 * {@code end}.
 *
 * <p>Line and column count from 1, because the value ends up in the editor and
 * people count from 1 there. The offset counts from 0.
 */
public record Span(int start, int end, int line, int column) {

    public static Span of(int start, int end, int line, int column) {
        return new Span(start, end, line, column);
    }

    /** Spans a range that covers both. */
    public Span to(Span other) {
        return new Span(Math.min(start, other.start), Math.max(end, other.end), line, column);
    }

    public int length() {
        return end - start;
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
