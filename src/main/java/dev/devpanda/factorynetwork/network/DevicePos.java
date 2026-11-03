package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Where a device sits in the network: a spot and, for a connector, a face.
 *
 * <p><b>A location alone was enough as long as a block carried one
 * connector.</b> Since a cable block carries up to six, "which device is at
 * this spot" is no longer an answerable question — there can be six, each with
 * its own name, its own channel demand and its own machine behind it. Up to
 * now the graph therefore remembered exactly one device per cable block and
 * swallowed the other five.
 *
 * <p>The side is {@code null} where there is none: a drive, a server rack, a
 * display are whole blocks. Only a connector faces in one direction — and it
 * always has one, even in its own connector block, where it sits in
 * {@code FACING}.
 */
public record DevicePos(BlockPos pos, @Nullable Direction side,
                        net.minecraft.resources.@Nullable ResourceKey<net.minecraft.world.level.Level> dimension) {

    public DevicePos {
        // A BlockPos from the search is often a roaming MutableBlockPos. As a
        // map key that would be a bug you only notice three steps later.
        pos = pos.immutable();
    }

    /** In the controller's own world. */
    public DevicePos(BlockPos pos, @Nullable Direction side) {
        this(pos, side, null);
    }

    /**
     * A connector in another world, reached through a quantum bridge or a
     * hall port. {@code null} is the controller's own world.
     */
    public static DevicePos of(BlockPos pos, Direction side,
                               net.minecraft.resources.@Nullable ResourceKey<net.minecraft.world.level.Level> dimension) {
        return new DevicePos(pos, side, dimension);
    }

    /**
     * The world this device stands in, seen from the controller's.
     *
     * <p>{@code null} if that world does not exist right now. The one place
     * that turns "no dimension" into "the controller's world" is
     * {@link Dimensions#levelOf}.
     */
    public net.minecraft.world.level.@Nullable Level level(net.minecraft.world.level.Level home) {
        return Dimensions.levelOf(home, dimension);
    }

    /** A whole block — drive, rack, display. */
    public static DevicePos of(BlockPos pos) {
        return new DevicePos(pos, null);
    }

    /** A connector on a face. */
    public static DevicePos of(BlockPos pos, Direction side) {
        return new DevicePos(pos, side);
    }

    /** The spot where the machine sits — for a whole block, itself. */
    public BlockPos machine() {
        return side == null ? pos : pos.relative(side);
    }

    @Override
    public String toString() {
        String at = side == null
                ? pos.getX() + "," + pos.getY() + "," + pos.getZ()
                : pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + side.getName();
        return dimension == null ? at : at + " in " + dimension.identifier();
    }
}
