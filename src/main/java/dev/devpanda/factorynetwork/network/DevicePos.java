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
public record DevicePos(BlockPos pos, @Nullable Direction side) {

    public DevicePos {
        // A BlockPos from the search is often a roaming MutableBlockPos. As a
        // map key that would be a bug you only notice three steps later.
        pos = pos.immutable();
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
        return side == null
                ? pos.getX() + "," + pos.getY() + "," + pos.getZ()
                : pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + side.getName();
    }
}
