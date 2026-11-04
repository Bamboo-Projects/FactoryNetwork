package dev.devpanda.factorynetwork.lang;

import java.util.Map;

/**
 * The events the network triggers on its own.
 *
 * <p><b>Here and nowhere else.</b> The names stood as strings at the place that
 * triggers them, and as a second list in the editor — both lists drifted apart
 * as soon as an event was added. The editor offered an {@code inventory_changed}
 * that nobody ever triggered, and kept quiet about {@code device_changed}.
 *
 * <p>This is the most expensive kind of error this language knows: an
 * {@code on} block needs no declaration, so the compiler accepts any name.
 * Whoever makes a typo gets no message — their block hangs in the program and
 * never runs. That is why {@link EventCheck} checks against this map, and
 * whoever triggers an event takes the constant from here.
 */
public final class BuiltinEvents {

    /** A device has appeared in the network. */
    public static final String DEVICE_ONLINE = "device_online";

    /** A device has disappeared — its name is passed, not a device. */
    public static final String DEVICE_OFFLINE = "device_offline";

    /** The content at a device has changed. */
    public static final String DEVICE_CHANGED = "device_changed";

    /**
     * Something has been added in a device.
     *
     * <p><b>Not "done".</b> Whether a machine has finished its work, no one
     * knows from outside; what is measured is the difference from the last
     * look. What the network itself puts in never counts.
     */
    public static final String DEVICE_OUTPUT = "device_output";

    /** The redstone signal at a connector has become a different one. */
    public static final String REDSTONE_CHANGED = "redstone_changed";

    /**
     * A crafting job is finished.
     *
     * <p>The job is passed — today as a number, its identifier. A dedicated type
     * {@code Job} stands in {@code sprache.md} and is missing from the value
     * model; an identifier is what can honestly be said of it today.
     */
    public static final String CRAFTING_FINISHED = "crafting_finished";

    /** And one that can no longer proceed — with the reason as text. */
    public static final String CRAFTING_FAILED = "crafting_failed";

    /**
     * A job that has waited on the same reason for half a minute — with the
     * reason as text. Once per reason; a new reason may report again.
     */
    public static final String CRAFT_STUCK = "craft_stuck";

    /** Somebody walked into a factory hall: the hall and the player's name. */
    public static final String HALL_ENTERED = "hall_entered";

    /** Somebody left a hall, however — door, death, logging out: hall and name. */
    public static final String HALL_LEFT = "hall_left";

    /** The last one has left the hall: the hall. */
    public static final String HALL_EMPTY = "hall_empty";

    /**
     * How many values a block receives.
     *
     * <p>Only {@code redstone_changed} passes two — the device and the strength.
     * Whoever writes the three others with two names gets a second name that
     * stays empty forever.
     */
    public static final Map<String, Integer> ARITY = Map.ofEntries(
            Map.entry(DEVICE_ONLINE, 1),
            Map.entry(DEVICE_OFFLINE, 1),
            Map.entry(DEVICE_CHANGED, 1),
            Map.entry(DEVICE_OUTPUT, 1),
            Map.entry(REDSTONE_CHANGED, 2),
            Map.entry(CRAFTING_FINISHED, 1),
            Map.entry(CRAFTING_FAILED, 2),
            Map.entry(CRAFT_STUCK, 2),
            Map.entry(HALL_ENTERED, 2),
            Map.entry(HALL_LEFT, 2),
            Map.entry(HALL_EMPTY, 1));

    private BuiltinEvents() {
    }
}
