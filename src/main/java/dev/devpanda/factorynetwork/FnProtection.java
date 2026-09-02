package dev.devpanda.factorynetwork;

import java.util.UUID;

/**
 * Who may change a program.
 *
 * <p>Until now anyone: whoever reached a terminal could overwrite the program
 * of someone else's factory. In singleplayer that is right, on a server it is
 * not — and it went unnoticed everywhere, because an overwritten program
 * leaves no message behind, only an installation that suddenly does something
 * else.
 *
 * <p><b>The default stays "everyone".</b> A mod that, after an update, locks
 * factories where two people build together has the same problem in the other
 * direction. Whoever wants protection sets it up — the server operator knows
 * their players, the mod does not.
 *
 * <p>Protected is whatever <b>rebuilds</b> someone else's installation: taking
 * over a program, saving a draft, cancelling a crafting job. Watching, reading
 * stocks and pressing buttons stays open to everyone — that is using, not
 * rebuilding.
 *
 * <p><b>Not included: the label gun.</b> Renaming a connector breaks programs
 * just as much, but it is an action in the world like breaking a block — and
 * for that there are protection mods that do it better than a logistics mod.
 * What stands here protects the program, not the plot of land.
 */
public final class FnProtection {

    /** How strict things are. */
    public enum Mode {
        /** Everyone may. The state before this setting. */
        OFF,
        /** Only whoever set the controller — and operators. */
        OWNER,
        /** Only operators. For servers where the factory belongs to everyone. */
        OPS
    }

    private FnProtection() {
    }

    /**
     * May this player change this controller's program?
     *
     * <p>Without Minecraft types, so that the question is testable: a
     * permission bug is the one bug you do not want to find out by trying.
     *
     * @param owner who set the controller, or {@code null}
     * @param operator whether the player has operator rights on the server
     */
    public static boolean mayEdit(Mode mode, UUID owner, UUID player, boolean operator) {
        if (operator) {
            return true;
        }
        return switch (mode) {
            case OFF -> true;
            // A controller without an owner belongs to everyone: from a world
            // from before, or set by a command. Assigning it to no one would
            // be a lock that no one can lift.
            case OWNER -> owner == null || owner.equals(player);
            case OPS -> false;
        };
    }
}
