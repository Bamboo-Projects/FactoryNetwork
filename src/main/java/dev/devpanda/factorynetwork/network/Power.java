package dev.devpanda.factorynetwork.network;

/**
 * What a block on the network costs in energy, in FE per tick.
 *
 * <p>Storage hangs on the drive, compute on the rack — energy is the third
 * leg, and the only one that costs something <b>continuously</b> rather than
 * just once at build time. Without it a finished network is free.
 *
 * <p>You pay for readiness, not for work. A worker that moves something costs
 * no more than one that waits. This is deliberately coarse: consumption that
 * varies with the load cannot be followed in-game, and anyone planning their
 * installation wants a number that stays put.
 *
 * <p>The numbers are set, not derived. They all live here and can be changed
 * in one place if the game feels different than intended.
 */
public final class Power {

    /** The controller itself. */
    public static final int CONTROLLER = 4;

    /** A connector, and thus the machine behind it. */
    public static final int CONNECTOR = 1;

    /**
     * An extension on the controller.
     *
     * <p>It creates channels rather than consuming them — so it costs no
     * channel. Energy it does cost: it is a device on the network like a drive
     * or a router, and an expansion that costs nothing is no decision.
     */
    public static final int EXTENSION = 1;

    /** A display. */
    public static final int DISPLAY = 1;

    /** A router. It switches actively and has a BlockEntity — cables do not. */
    public static final int ROUTER = 1;

    /** A fabricator. */
    public static final int FABRICATOR = 1;

    /** A drive, empty. */
    public static final int DRIVE = 1;

    /** And as much again per inserted cell. */
    public static final int PER_CELL = 1;

    /** A server rack, empty. Two blocks tall, but one device. */
    public static final int RACK = 1;

    /**
     * And twice as much again per <b>running</b> bay.
     *
     * <p>By bays and not by components: a half-populated bay does not compute,
     * so it does not pay either. Otherwise a forgotten CPU in an empty bay
     * would cost energy forever without ever doing anything.
     *
     * <p><b>The tier makes no difference.</b> A large disk costs more to make,
     * not to run — the price for expansion sits in the recipe chain, and a
     * second price on top would only complicate the bill without changing a
     * decision.
     */
    public static final int PER_SERVER = 2;

    /**
     * Cables cost nothing.
     *
     * <p>An installation has hundreds of them; if each drew even a single FE,
     * the length of the wiring would determine consumption rather than what
     * hangs off it.
     */
    /**
     * What one end of a quantum bridge draws.
     *
     * <p>Both ends pay, so double for one connection. A bridge that cost
     * nothing would replace every cable — and a network that is equally cheap
     * everywhere no longer has a shape worth thinking about.
     *
     * <p>Well above a cable and below a machine: it carries, it does not work.
     */
    public static final int BRIDGE = 6;

    /** What a mast draws with no cards. */
    public static final int MAST_BASE = 4;

    /** And what each range card in it adds. */
    public static final int MAST_PER_CARD = 6;

    /**
     * What the unlimited card costs.
     *
     * <p>More than four ordinary ones together: otherwise everyone takes it as
     * soon as they have it, and range is no longer a decision.
     */
    public static final int MAST_UNLIMITED = 40;

    /**
     * What this mast draws, per tick.
     *
     * <p>Modules cost nothing. They raise no stat, so they drive no
     * consumption either — what costs energy here is range.
     */
    public static int mast(dev.devpanda.factorynetwork.upgrade.Loadout loadout) {
        if (loadout.unlimited(dev.devpanda.factorynetwork.upgrade.Stat.RANGE)) {
            return MAST_BASE + MAST_UNLIMITED;
        }
        return MAST_BASE + loadout.count(
                dev.devpanda.factorynetwork.upgrade.Card.RANGE) * MAST_PER_CARD;
    }

    /**
     * What an open window draws from the battery per tick, from afar.
     *
     * <p>Kept small, and that on purpose: a Wireless Terminal thus stays open
     * for over half an hour. A battery that runs empty in the middle of the
     * work turns a tool into a chore — and whoever leaves the window open,
     * rather than reopening it every ten seconds, should not be punished for
     * it.
     */
    public static final int REMOTE_TICK = 5;

    /**
     * What opening it costs, once.
     *
     * <p>More than a second of keeping it open. Otherwise it would pay to flip
     * the window open and shut constantly, and no one should be rewarded for
     * clicking.
     */
    public static final int REMOTE_OPEN = 400;

    /**
     * And what a single action costs — a stack moved, a program adopted.
     *
     * <p>A hundred of these eat less than a quarter of the small battery.
     * Whoever tidies up from afar moves many stacks; if the battery ran empty
     * doing so, remote access would be useless for exactly what one builds it
     * for.
     */
    public static final int REMOTE_ACTION = 120;

    public static final int CABLE = 0;

    /**
     * What the controller can buffer.
     *
     * <p>Large enough that a brief gap in the network does not matter, small
     * enough that a permanently too-weak supply shows itself quickly.
     */
    public static final int CAPACITY = 20_000;

    /** How fast it accepts. */
    public static final int MAX_INPUT = 2_000;

    /**
     * How long the network needs to boot up, in ticks.
     *
     * <p>Three seconds in which it already draws and does nothing yet. Without
     * this time a power outage would be a flicker that no one notices.
     */
    public static final int BOOT_TICKS = 60;

    private Power() {
    }

    /**
     * At how much stored energy the network boots up again.
     *
     * <p><b>The reserve must be enough for booting and still leave something
     * over</b>, otherwise the network goes out again right after booting: a
     * supply that sits just below demand would otherwise produce a blink every
     * half minute that looks like a bug rather than like too little energy.
     */
    public static int restartThreshold(int draw) {
        return Math.max(BOOT_TICKS * draw * 2, CAPACITY / 10);
    }
}
