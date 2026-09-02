package dev.devpanda.factorynetwork.upgrade;

/**
 * What upgrades make of a recipe.
 *
 * <p><b>Without Minecraft types</b>, like everything in this package: the
 * calculation is the part where a balance is decided, and it belongs in
 * ordinary test runs, not in a world that first has to boot up.
 *
 * <p><b>Two cards, two different things.</b> Acceleration takes time away and
 * adds power on top; the batch takes no time away but adds workpieces. Whoever
 * installs both gets both — the factors multiply.
 *
 * <p><b>Why acceleration costs disproportionately.</b> A fifth less time for
 * half again as much power: after four cards the machine runs two and a half
 * times as fast and consumes three times as much. Were the trade fair, there
 * would be no reason to ever build a second machine — and that is exactly what
 * a factory is meant to be, a plant of many parts and not one upgraded lump.
 */
public final class Tuning {

    /**
     * How many cards per kind still count.
     *
     * <p>A slot holds a whole stack, and every item in it counts — for range
     * that is right, for time it would be the end of every balance: sixty-four
     * cards would press every recipe down to one tick, and the progress bar
     * would have nothing left to show. Eight is more than fits into a
     * machine's slots individually, and still leaves room to stack them.
     */
    public static final int MOST = 8;

    /** What an acceleration card leaves of the time. */
    private static final double KEEPS = 0.8;

    /** And what it adds on top of the power. */
    private static final double COSTS = 0.5;

    private Tuning() {
    }

    public static Tuned of(Loadout loadout, int ticks, int energy) {
        int fast = Math.min(MOST, loadout.count(Card.ACCELERATION));
        int wide = Math.min(MOST, loadout.count(Card.BATCH));

        double factor = Math.pow(KEEPS, fast);
        // Round up and at least one tick: a run in zero ticks would be no run
        // but a jump.
        int fasterTicks = Math.max(1, (int) Math.round(ticks * factor));

        int batch = 1 + wide;
        // First the surcharge for speed, then the amount. Both are factors on
        // the same base price.
        double price = energy * (1.0 + COSTS * fast) * batch;
        return new Tuned(fasterTicks, (int) Math.round(price), batch);
    }
}
