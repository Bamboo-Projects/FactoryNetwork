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
     * How many values a block receives.
     *
     * <p>Only {@code redstone_changed} passes two — the device and the strength.
     * Whoever writes the three others with two names gets a second name that
     * stays empty forever.
     */
    public static final Map<String, Integer> ARITY = Map.of(
            DEVICE_ONLINE, 1,
            DEVICE_OFFLINE, 1,
            DEVICE_CHANGED, 1,
            DEVICE_OUTPUT, 1,
            REDSTONE_CHANGED, 2,
            CRAFTING_FINISHED, 1,
            CRAFTING_FAILED, 2);

    private BuiltinEvents() {
    }
}
