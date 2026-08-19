package dev.devpanda.factorynetwork.lang;

/**
 * Ein Bereich im Quelltext, von {@code start} bis ausschließlich {@code end}.
 *
 * <p>Zeile und Spalte zählen ab 1, weil die Angabe im Editor landet und
 * Menschen dort ab 1 zählen. Der Offset zählt ab 0.
 */
public record Span(int start, int end, int line, int column) {

    public static Span of(int start, int end, int line, int column) {
        return new Span(start, end, line, column);
    }

    /** Spannt einen Bereich auf, der beide umfasst. */
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
