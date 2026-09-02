package dev.devpanda.factorynetwork.upgrade;

/**
 * The cards and what they raise.
 *
 * <p><b>Identical cards add up instead of upgrading in tiers.</b> A tier
 * system — Range I, II, III — makes the old card worthless the moment the new
 * one arrives. Four identical cards in four slots tie the value to the number
 * of slots, and that is the real decision.
 */
public enum Card implements Upgrade {

    /** Eight more blocks of range, per card. */
    RANGE("range_card", Stat.RANGE, 8, false),

    /**
     * Removes the range limit entirely.
     *
     * <p>A card and not a module: it creates nothing new, it lifts a limit.
     * Its numeric value is zero, because nobody reads it — whoever asks
     * {@code unlimited} no longer asks {@code value}.
     */
    INFINITY("infinity_card", Stat.RANGE, 0, true),

    /**
     * Takes a fifth of a machine's time and adds power on top.
     *
     * <p>Its step is zero: it adds nothing, it multiplies — and {@link Tuning}
     * computes that from the count, not from a sum.
     */
    ACCELERATION("acceleration_card", Stat.SPEED, 0, false),

    /** Gives a machine one more workpiece per run. */
    BATCH("batch_card", Stat.BATCH, 0, false);

    private final String id;
    private final Stat stat;
    private final int step;
    private final boolean unlimited;

    Card(String id, Stat stat, int step, boolean unlimited) {
        this.id = id;
        this.stat = stat;
        this.step = step;
        this.unlimited = unlimited;
    }

    @Override
    public String id() {
        return id;
    }

    /** What it acts on — exactly one stat, never two. */
    public Stat stat() {
        return stat;
    }

    /** By how much it raises it, per card. */
    public int step() {
        return step;
    }

    /** Whether it lifts the limit entirely. */
    public boolean unlimited() {
        return unlimited;
    }
}
