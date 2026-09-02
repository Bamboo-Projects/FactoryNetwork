package dev.devpanda.factorynetwork.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What stands behind a connector.
 *
 * <p>Pure information, no computation: the server probes the capabilities of
 * the neighboring block, and what comes out of that stands here. The editor
 * reads it for the display, for the suggestions, and for the warning when the
 * connector hangs on a side where the machine accepts nothing.
 *
 * <p><b>The translation key and not the finished text.</b> "Crusher" is called
 * something else on an English server than in the German client, and the
 * translation happens where someone looks.
 *
 * @param descriptionId translation key of the block, e.g.
 *                      {@code block.mekanism.crusher}
 * @param namespace     the mod it comes from
 * @param connectedSide the side the connector actually hangs on
 * @param access        what works on which side; sides without an entry can do
 *                      nothing
 */
public record DeviceProfile(String descriptionId, String namespace,
                            Side connectedSide, Map<Side, Access> access) {

    /**
     * What works on a side.
     *
     * @param slots  compartments of the item storage, zero when there is none
     * @param tanks  containers of the fluid storage, zero when there is none
     * @param energy whether power goes in or comes out there
     */
    public record Access(int slots, int tanks, boolean energy) {

        /** What can be asked about. */
        public enum Ability { ITEMS, FLUIDS, ENERGY }

        public boolean has(Ability ability) {
            return switch (ability) {
                case ITEMS -> slots > 0;
                case FLUIDS -> tanks > 0;
                case ENERGY -> energy;
            };
        }
    }

    /** Several sides that can do the same. */
    public record Group(List<Side> sides, Access access) {
    }

    /**
     * A device about which nothing is known.
     *
     * <p>The chunk is not loaded, or there is nothing there at all. <b>That is
     * something other than a device that can do nothing</b> — the same
     * distinction as with {@link NetworkView#knowsNetwork()}. Whoever levels it
     * out warns about machines that work flawlessly.
     */
    public static DeviceProfile unreachable() {
        return new DeviceProfile("", "", Side.ANY, Map.of());
    }

    /** Is anything known at all? */
    public boolean reachable() {
        return !descriptionId.isEmpty();
    }

    /** What works on this side — or nothing. */
    public Access accessAt(Side side) {
        Access direct = access.get(side);
        if (direct != null) {
            return direct;
        }
        // What is offered without a side applies to every one.
        return access.get(Side.ANY);
    }

    public boolean hasItems(Side side) {
        return can(side, Access.Ability.ITEMS);
    }

    public boolean hasFluids(Side side) {
        return can(side, Access.Ability.FLUIDS);
    }

    public boolean hasEnergy(Side side) {
        return can(side, Access.Ability.ENERGY);
    }

    public boolean can(Side side, Access.Ability ability) {
        Access at = accessAt(side);
        return at != null && at.has(ability);
    }

    /**
     * The sides where this works — for "North would have one".
     *
     * <p>Without the connected one: whoever names it tells the player to hang
     * the connector where it already hangs.
     */
    public List<Side> sidesWith(Access.Ability ability) {
        List<Side> found = new ArrayList<>();
        for (Map.Entry<Side, Access> entry : access.entrySet()) {
            if (entry.getKey() != connectedSide && entry.getValue().has(ability)) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    /**
     * Sides with the same access grouped together.
     *
     * <p>A machine offers the same on four sides, and without this it would
     * stand in the tooltip four times. Grouping is by the content of the access
     * and not by the handler instance: for the display "North, South: 3 slots"
     * is right, no matter whether it is the same instance — and the instance
     * does not survive the trip to the client anyway.
     *
     * <p>The order of the sides is that of the enum, so that the same device
     * always reads the same.
     */
    public List<Group> grouped() {
        Map<Access, List<Side>> byAccess = new LinkedHashMap<>();
        for (Side side : Side.values()) {
            Access at = access.get(side);
            if (at != null) {
                byAccess.computeIfAbsent(at, key -> new ArrayList<>()).add(side);
            }
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Access, List<Side>> entry : byAccess.entrySet()) {
            groups.add(new Group(entry.getValue(), entry.getKey()));
        }
        return groups;
    }

    /**
     * What hangs on this device, in one line.
     *
     * <p>Summed over all sides and not per side: the question is "is this any
     * good at all". Which side exactly it is, the display says.
     *
     * <p>Here and not in the editor, because two places need it — the suggestion
     * list in the game and the network analyzer that runs on the server.
     */
    public String abilities() {
        if (!reachable()) {
            return "";
        }
        java.util.List<String> can = new java.util.ArrayList<>();
        for (Side side : Side.values()) {
            if (hasItems(side) && !can.contains("Gegenstände")) {
                can.add("Gegenstände");
            }
            if (hasFluids(side) && !can.contains("Flüssigkeiten")) {
                can.add("Flüssigkeiten");
            }
            if (hasEnergy(side) && !can.contains("Strom")) {
                can.add("Strom");
            }
        }
        return can.isEmpty() ? "nichts anzuschließen" : String.join(", ", can);
    }
}
