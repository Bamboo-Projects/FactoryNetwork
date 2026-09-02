package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Probes what the machine behind a connector can do.
 *
 * <p>All six directions plus the side-less access: some machine offers its
 * capability only without a side, and whoever probes just the six directions
 * reports "accepts nothing" for exactly those — the worst kind of bug,
 * because it looks plausible.
 *
 * <p>The work is done when the terminal is opened, not continuously. Seven
 * accesses times three capabilities per connector; the queries are cached in
 * NeoForge and cost nothing on this scale.
 */
public final class DeviceScan {

    private DeviceScan() {
    }

    /** The profile of the machine behind this connector. */
    public static DeviceProfile of(ConnectorPart connector) {
        Level level = connector.level();
        if (level == null) {
            return DeviceProfile.unreachable();
        }
        Direction facing = connector.facing();
        BlockPos target = connector.pos().relative(facing);
        if (!level.isLoaded(target)) {
            return DeviceProfile.unreachable();
        }
        // Air is not a missing answer, but an answer: nothing is there.
        // Throwing the two together would mean telling a player "not loaded"
        // while they stand in front of it — and the message about the wrong
        // side would be missing, because it needs a profile.
        BlockState machine = level.getBlockState(target);

        Map<Side, DeviceProfile.Access> access = new EnumMap<>(Side.class);
        for (Direction direction : Direction.values()) {
            // From the machine's point of view the access comes from the opposite side.
            DeviceProfile.Access at = probe(level, target, direction.getOpposite());
            if (at != null) {
                access.put(side(direction), at);
            }
        }
        DeviceProfile.Access anySide = probe(level, target, null);
        if (anySide != null) {
            access.put(Side.ANY, anySide);
        }

        return new DeviceProfile(machine.getBlock().getDescriptionId(),
                BuiltInRegistries.BLOCK.getKey(machine.getBlock()).getNamespace(),
                side(facing), access);
    }

    /**
     * What works on a side, or {@code null} if nothing works there.
     *
     * <p>{@code null} and not an empty entry: a side that can do nothing does
     * not appear in the map at all, and the profile stays as small as the
     * machine is plain.
     */
    private static DeviceProfile.Access probe(Level level, BlockPos pos, Direction from) {
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, from);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, from);
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, from);

        int slots = items == null ? 0 : items.getSlots();
        int tanks = fluids == null ? 0 : fluids.getTanks();
        boolean power = energy != null;
        if (slots == 0 && tanks == 0 && !power) {
            return null;
        }
        return new DeviceProfile.Access(slots, tanks, power);
    }

    /** Minecraft's direction as a side of the language. */
    public static Side side(Direction direction) {
        return switch (direction) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            case EAST -> Side.EAST;
        };
    }
}
