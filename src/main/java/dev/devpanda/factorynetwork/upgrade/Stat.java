package dev.devpanda.factorynetwork.upgrade;

/** The stats that cards can raise. */
public enum Stat {

    /** How far a wireless signal reaches, in blocks. */
    RANGE,

    /**
     * How fast a machine works.
     *
     * <p>Unlike range, this is not an addition in some unit but a factor:
     * {@link Tuning} counts the cards, their step stays zero. The stat is
     * listed here nonetheless, because a slot must know what it accepts.
     */
    SPEED,

    /** How many workpieces a run holds at once. */
    BATCH
}
