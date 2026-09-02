package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * What a cable carries — in bytes per second.
 *
 * <p><b>One item is one kilobyte.</b> Not because an iron ingot would be a
 * kilobyte, but because the numbers then work out: an ordinary cable carries
 * 25.6 MB/s — an order of magnitude that sounds like fiber optics and is
 * still noticeable.
 *
 * <p><b>Why not the real numbers.</b> Fiber optics carry 10 Gbit/s, i.e.
 * 62.5 million bytes per tick. A very large network moves six thousand items
 * per tick. With the real numbers the limit would never be reachable — a
 * limit no one feels is decoration.
 *
 * <p><b>Why a real unit is better than a number.</b> "64 per tick" is a
 * number without an anchor: is that a lot? What is it enough for? You learn it
 * by trial and error. "1 KB/s" carries its intuition with it — everyone knows
 * that is slow and that ten of them are more. The mod need not explain what
 * the world has already explained.
 *
 * <p><b>Computed per tick, shown per second.</b> The game runs in ticks; a
 * human thinks in seconds. The conversion lives in one place, so it does not
 * happen slightly differently in five.
 */
public final class Bandwidth {

    /** Twenty ticks are one second — the one place where that is stated. */
    public static final int TICKS_PER_SECOND = 20;

    /** What an item weighs: one kilobyte. */
    public static final int PER_ITEM = 1000;

    /**
     * What a cable carries: 1280 items per tick, i.e. 25.6 MB/s.
     *
     * <p><b>One type, one number.</b> Until 30.08. there were two cables: an
     * ordinary one at 2.56 MB/s and a dense one at ten times that. The dense
     * one was the answer to a question that no longer exists — it bundled
     * channels, and channels are gone as of 29.08.
     *
     * <p>What remained is the larger number. These are fiber-optic cables; one
     * that carried two stacks per tick would not be.
     */
    public static final int CABLE = 1280 * PER_ITEM;

    /**
     * What does not conduct limits nothing either.
     *
     * <p>A drive, a rack, a mast: they are destination, not route. A limit
     * there would be a second limit on the same path.
     *
     * <p><b>The controller was in this list until 30.08.</b> It does not
     * belong here: everything in the network passes through it, which makes it
     * route — the one route every path has in common.
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * What a controller carries without an extension: as much as a cable.
     *
     * <p><b>It is the backplane.</b> In a real network every port can do
     * gigabit, but the switch carries only what it carries — whoever wants
     * more buys a bigger one or plugs in a module.
     *
     * <p><b>Why exactly one cable.</b> A network with a single trunk line runs
     * fully without an extension. Whoever branches feels the limit — and that
     * is the moment you build on. A limit you feel from the very first day is
     * a nuisance; one you never feel is decoration.
     */
    public static final int CONTROLLER = CABLE;

    /**
     * What an extension adds: half a cable.
     *
     * <p>Less than a whole one, so the second extension is still worthwhile
     * and the sixth does not become silly.
     */
    public static final int EXTENSION = CABLE / 2;

    /**
     * What a controller carries with this many extensions.
     *
     * <p>The cap is not a game rule but arithmetic: very many extensions would
     * otherwise overflow the number range, and the largest bandwidth in the
     * world would turn into a negative one.
     */
    public static int ofController(int extensions) {
        long total = (long) CONTROLLER + (long) EXTENSION * Math.max(0, extensions);
        return (int) Math.min(total, UNLIMITED);
    }

    /**
     * What passes through at this spot per tick.
     *
     * <p>Router, gateway and quantum bridge carry as much as a cable: they are
     * line, not multiplier.
     */
    public static int at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.ControllerBlock) {
            // The number lives in the BlockEntity, because it depends on the
            // extensions — and the graph counts those on rebuild, not this
            // call: it runs per slot per worker per tick.
            return level.getBlockEntity(pos)
                    instanceof dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity controller
                    ? controller.bandwidth() : CONTROLLER;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.RouterBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.GatewayBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
            return CABLE;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.CableBlock cable) {
            return cable.bandwidth();
        }
        return UNLIMITED;
    }

    /**
     * An amount per tick, readable per second.
     *
     * <p>Three tiers, and each catches a case you see in the game:
     *
     * <ul>
     *   <li><b>Below one kilobyte, in bytes.</b> "0 KB/s" for a worker that
     *       moves something would be a lie through rounding.</li>
     *   <li><b>Odd kilobytes with one decimal.</b> 1500 B/s as "1 KB/s" would
     *       lose half the information.</li>
     *   <li><b>Round ones without.</b> "10,0 KB/s" is noise.</li>
     * </ul>
     */
    public static String perSecond(int perTick) {
        long bytes = (long) perTick * TICKS_PER_SECOND;
        double wert = bytes;
        int stufe = 0;
        while (wert >= 1000 && stufe < UNITS.length - 1) {
            wert /= 1000;
            stufe++;
        }
        if (stufe == 0) {
            return (long) wert + " B/s";
        }
        if (wert == Math.floor(wert)) {
            return (long) wert + " " + UNITS[stufe] + "/s";
        }
        return String.format(Locale.GERMANY, "%.1f %s/s", wert, UNITS[stufe]);
    }

    /** What of a capacity has already been used: "0,4 von 1 KB/s". */
    /** The tiers in which a total amount is read. */
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    /**
     * A total amount, readable: 340 B, 12,4 KB, 3,1 MB.
     *
     * <p><b>Not per second, but in total.</b> A network that runs for a week
     * moves gigabytes — and "14603219 B" is not information but a string of
     * digits.
     *
     * <p>Thousand and not 1024: we are dealing in bytes per second, and there
     * the world reckons in decimal. One KB/s is a thousand bytes; otherwise
     * the conversion from the tick would already be odd.
     */
    public static String total(long bytes) {
        double wert = bytes;
        int stufe = 0;
        while (wert >= 1000 && stufe < UNITS.length - 1) {
            wert /= 1000;
            stufe++;
        }
        if (stufe == 0) {
            return (long) wert + " " + UNITS[0];
        }
        // One decimal place: "12,4 MB" says enough, "12,437 MB" is noise on a
        // number that keeps running anyway.
        return String.format(Locale.GERMANY, "%.1f %s", wert, UNITS[stufe]);
    }

    public static String usage(int usedPerTick, int capacityPerTick) {
        if (capacityPerTick >= UNLIMITED) {
            return perSecond(usedPerTick);
        }
        return perSecond(usedPerTick) + " von " + perSecond(capacityPerTick);
    }

    private Bandwidth() {
    }
}
