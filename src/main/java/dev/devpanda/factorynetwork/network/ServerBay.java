package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import net.minecraft.world.item.ItemStack;

/**
 * A bay in the server rack: CPU, RAM, disk.
 *
 * <p><b>Only all three together make a server.</b> A bay with two components
 * contributes nothing — not proportionally, nothing at all. This is the rule
 * the whole block hangs on: install twelve CPUs and you have twelve components
 * and not a single server, and whoever wants to upgrade has to decide which
 * bay gets the big part.
 *
 * <p>The alternative — each component counts on its own — would be the same
 * number with less decision: you install the largest of everything, and the
 * twelve slots are just an upper limit.
 */
public record ServerBay(int cpu, int ram, int disk) {

    public static final ServerBay EMPTY = new ServerBay(0, 0, 0);

    /**
     * Reads a bay from its three slots.
     *
     * <p>The order is that of {@link ServerPart}, and it is also the order of
     * the slots in the window — a slot only accepts its own kind.
     */
    public static ServerBay of(ItemStack cpu, ItemStack ram, ItemStack disk) {
        return new ServerBay(
                ServerPartItem.valueOf(cpu, ServerPart.CPU),
                ServerPartItem.valueOf(ram, ServerPart.RAM),
                ServerPartItem.valueOf(disk, ServerPart.DISK));
    }

    /** Is this bay running? Only with all three components. */
    public boolean complete() {
        return cpu > 0 && ram > 0 && disk > 0;
    }

    /** Is anything installed at all? */
    public boolean occupied() {
        return cpu > 0 || ram > 0 || disk > 0;
    }

    /** What this bay contributes — nothing while it is incomplete. */
    public ServerBay contribution() {
        return complete() ? this : EMPTY;
    }

    public ServerBay plus(ServerBay other) {
        return new ServerBay(cpu + other.cpu, ram + other.ram, disk + other.disk);
    }

    /** Which component is still missing — for the message in the window. */
    public ServerPart missing() {
        if (cpu <= 0) {
            return ServerPart.CPU;
        }
        if (ram <= 0) {
            return ServerPart.RAM;
        }
        if (disk <= 0) {
            return ServerPart.DISK;
        }
        return null;
    }
}
