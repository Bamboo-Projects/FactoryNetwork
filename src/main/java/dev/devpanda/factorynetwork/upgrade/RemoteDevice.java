package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;

/**
 * The two devices for remote access.
 *
 * <p><b>The separation is the whole point:</b> on the go you reach the
 * storage, but not the code — for that you need the laptop. It can do
 * everything the terminal can, and costs more.
 *
 * <p>The second difference is the slots: four against two. The laptop thereby
 * also reaches farther, because range comes from cards and cards need slots.
 * See {@code docs/fernzugriff.md} §3.
 *
 * <p><b>Why the log works remotely too.</b> The design listed four areas and
 * skipped it. But the rule a player can remember is not "four of six", it is
 * <b>everything except code</b> — and the log is diagnostics like the network
 * overview, which is uncontroversially available remotely.
 */
public enum RemoteDevice {

    /** The early access: everything except code. */
    TERMINAL("wireless_terminal", 2, false),

    /** And the goal: everything. */
    LAPTOP("laptop", 4, true);

    private final String id;
    private final int slots;
    private final boolean code;

    RemoteDevice(String id, int slots, boolean code) {
        this.id = id;
        this.slots = slots;
        this.code = code;
    }

    /** The name in the registry, without namespace. */
    public String id() {
        return id;
    }

    /** How many upgrades fit inside. */
    public int slots() {
        return slots;
    }

    /**
     * May this device show this tab?
     *
     * <p>The <b>server</b> answers the question. The screen asks the same, so
     * that it draws nothing the server would reject — a tab that opens and
     * then does nothing is worse than one that is not there at all.
     */
    public boolean allows(TerminalTab tab) {
        return tab != TerminalTab.CODE || code;
    }
}
