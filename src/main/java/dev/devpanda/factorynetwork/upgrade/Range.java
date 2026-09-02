package dev.devpanda.factorynetwork.upgrade;

/**
 * How far a wireless signal reaches.
 *
 * <p>The numbers live in {@code docs/fernzugriff.md} §3 and here — nowhere
 * else. Whoever changes them changes them in both places, and
 * {@code RangeTest} checks them.
 */
public final class Range {

    /** A mast without cards reaches this far. */
    public static final int MAST_BASE = 16;

    /**
     * By this much a card counts more in the mast than in the device.
     *
     * <p><b>Why here and not on the card:</b> it is the same card. One that
     * raised different amounts in two places would contradict the rule from
     * the upgrade system — there a card raises its value, and that value is
     * one. The amplification is a property of the mast.
     */
    public static final int MAST_FACTOR = 2;

    /** What {@link #reach} returns when there is no longer a limit. */
    public static final int UNLIMITED = -1;

    /**
     * How far this mast reaches this device, in blocks.
     *
     * @return {@link #UNLIMITED} if the mast carries an Infinity card
     */
    public static int reach(Loadout mast, Loadout device) {
        if (mast.unlimited(Stat.RANGE)) {
            return UNLIMITED;
        }
        return MAST_BASE
                + mast.value(Stat.RANGE) * MAST_FACTOR
                + device.value(Stat.RANGE);
    }

    /**
     * Does it reach across this distance?
     *
     * <p>The limit still counts: whoever stands exactly on it is within.
     */
    public static boolean covers(Loadout mast, Loadout device, double distance) {
        return covers(mast, device, true, distance);
    }

    /**
     * Does it reach all the way there — even when that lies in another world?
     *
     * <p><b>Across a dimension boundary only the Infinity card reaches</b>,
     * and then without any calculation. Between two dimensions there is no
     * distance one could measure: the Nether does not lie a hundred blocks
     * from the Overworld, it lies alongside it and nowhere at once. A
     * calculation with coordinates from two worlds would yield a number
     * without meaning.
     *
     * <p>Even four range cards do not help there. Range is a distance, and a
     * dimension boundary is not one — that is the reason to build the card at
     * all.
     *
     * @param sameLevel whether mast and device are in the same dimension
     * @param distance the distance; meaningless if they are not
     */
    public static boolean covers(Loadout mast, Loadout device, boolean sameLevel,
                                 double distance) {
        int found = reach(mast, device);
        if (!sameLevel) {
            return found == UNLIMITED;
        }
        return found == UNLIMITED || distance <= found;
    }

    private Range() {
    }
}
