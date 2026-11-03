package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A port in a hall wall. It holds nothing; it is here to register itself, so
 * the network finds it without searching the shell.
 */
public class HallPortBlockEntity extends BlockEntity {

    public HallPortBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.HALL_PORT.get(), pos, state);
    }

    /** The hall this port belongs to; 0 outside the hall dimension. */
    public int hall() {
        return level != null && level.dimension() == Halls.DIMENSION ? Halls.hallAt(worldPosition) : 0;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && hall() > 0) {
            HallPorts.add(hall(), worldPosition);
            announce();
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel && hall() > 0) {
            HallPorts.remove(hall(), worldPosition);
            announce();
        }
        super.setRemoved();
    }

    /**
     * Tells the networks on both sides: the one inside finds the port as a
     * neighbour, the one outside only through the hall's block.
     */
    private void announce() {
        if (!(level instanceof ServerLevel here)) {
            return;
        }
        ControllerRegistry.markChanged(here, worldPosition);
        Halls.Entry entry = Halls.of(here.getServer()).get(hall());
        GlobalPos door = entry == null ? null : entry.door().orElse(null);
        ServerLevel outside = door == null ? null : here.getServer().getLevel(door.dimension());
        if (outside != null && outside.isLoaded(door.pos())) {
            ControllerRegistry.markChanged(outside, door.pos());
        }
    }
}
