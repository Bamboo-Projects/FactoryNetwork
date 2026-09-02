package dev.devpanda.factorynetwork.network;

/**
 * How a device stands within the network.
 *
 * <p><b>Four of these look the same in game</b>, and three of them mean "not
 * addressable" for three different reasons. Guess the wrong one and you look
 * in the wrong place: once at the name, once at the channel limit, once at the
 * line.
 *
 * <p><b>This is a shadow of the graph and not a truth of its own.</b> The
 * state is computed when the network is rebuilt; the connector merely holds
 * onto it, so that a glance at the block can answer it without asking the
 * controller.
 */
public enum DeviceState {

    /** Not on the network at all — or on none that is currently loading. */
    OFFLINE,
    /** Named and reachable. */
    ONLINE,
    /** On the network, but without a name — and so not addressable in code. */
    UNNAMED,
    /** The name is assigned more than once; all of them are unusable. */
    DUPLICATE,
    /**
     * On the network, but the line to it is saturated.
     *
     * <p><b>No longer assigned since 29.08.</b> Back then the state was called
     * "without a free channel" and meant: silent. Channels no longer exist; a
     * device on a full line works more slowly, not stopping entirely — and
     * that is not a state of its own but a number.
     *
     * <p>The value stays because it is stored in every existing world. A
     * device that brings it along on load gets its correct one at the first
     * network build.
     */
    STARVED;

    /**
     * The number under which this state is stored.
     *
     * <p>The order above is thereby fixed: change it and you change saved
     * worlds. New states belong at the end.
     */
    public byte id() {
        return (byte) ordinal();
    }

    /** The state for a stored number — anything unknown counts as offline. */
    public static DeviceState byId(int id) {
        DeviceState[] all = values();
        return id >= 0 && id < all.length ? all[id] : OFFLINE;
    }
}
