package dev.devpanda.factorynetwork.hall;

/**
 * The sizes of a factory hall: the inner edge length in blocks.
 *
 * <p>Fixed per block and not upgradable in place: a hall that grows would
 * have to move its walls through whatever stands next to them.
 */
public enum HallTier {

    SMALL("hall_16", 16),
    MEDIUM("hall_32", 32),
    LARGE("hall_48", 48);

    private final String id;
    private final int size;

    HallTier(String id, int size) {
        this.id = id;
        this.size = size;
    }

    /** The registry name of the block. */
    public String id() {
        return id;
    }

    /** Inner edge length in blocks, the same in all three directions. */
    public int size() {
        return size;
    }

    public static HallTier byOrdinal(int ordinal) {
        HallTier[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SMALL;
    }
}
