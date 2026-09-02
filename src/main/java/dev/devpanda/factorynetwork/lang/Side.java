package dev.devpanda.factorynetwork.lang;

/**
 * A side of a block, from the language's point of view.
 *
 * <p><b>Why not Minecraft's {@code Direction}:</b> a capability can also be
 * offered without a side, and some machines offer it only that way. In
 * {@code Direction} that would be a {@code null} — as a key in a map, the kind
 * of trap that only springs shut late. Here it is {@link #ANY}, a value like
 * any other.
 *
 * <p>The second reason: {@link NetworkView} and {@link NetworkCheck} get by
 * without Minecraft, and their tests therefore run in milliseconds instead of
 * a minute of GameTest.
 */
public enum Side {
    DOWN("unten"),
    UP("oben"),
    NORTH("Norden"),
    SOUTH("Süden"),
    WEST("Westen"),
    EAST("Osten"),
    /** Offered without a side — applies to every direction. */
    ANY("überall");

    private final String written;

    Side(String written) {
        this.written = written;
    }

    /** How the side reads in a message. */
    public String written() {
        return written;
    }
}
