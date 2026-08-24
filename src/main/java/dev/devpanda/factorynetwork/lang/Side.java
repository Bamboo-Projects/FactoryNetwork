package dev.devpanda.factorynetwork.lang;

/**
 * Eine Seite eines Blocks, aus Sicht der Sprache.
 *
 * <p><b>Warum nicht Minecrafts {@code Direction}:</b> Eine Fähigkeit lässt
 * sich auch ohne Seite anbieten, und manche Maschine bietet sie
 * ausschließlich so an. In {@code Direction} wäre das ein {@code null} —
 * als Schlüssel in einer Karte die Sorte Falle, die erst spät zuschnappt.
 * Hier ist es {@link #ANY}, ein Wert wie jeder andere.
 *
 * <p>Der zweite Grund: {@link NetworkView} und {@link NetworkCheck} kommen
 * ohne Minecraft aus, und ihre Tests laufen deshalb in Millisekunden statt
 * in einer Minute GameTest.
 */
public enum Side {
    DOWN("unten"),
    UP("oben"),
    NORTH("Norden"),
    SOUTH("Süden"),
    WEST("Westen"),
    EAST("Osten"),
    /** Ohne Seite angeboten — gilt für jede Richtung. */
    ANY("überall");

    private final String written;

    Side(String written) {
        this.written = written;
    }

    /** Wie die Seite in einer Meldung dasteht. */
    public String written() {
        return written;
    }
}
