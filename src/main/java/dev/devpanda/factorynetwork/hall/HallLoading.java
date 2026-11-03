package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;

/**
 * A hall runs while its block is loaded.
 *
 * <p>The block takes a ticket for the chunks of its hall when it loads and
 * returns it when it unloads or is broken. Tickets left over from the last
 * session are thrown away at start: the blocks take them again as they load,
 * and a hall whose block no longer stands should not run on forever.
 *
 * <p>Unless FTB Chunks or the like does it, the block also holds its own
 * chunk ({@code hallBlocksLoadTheirChunk}), so a hall runs with nobody near.
 *
 * <p>The ticket's owner is the hall's corner, not the block's position: the
 * block can stand at the same coordinates in two dimensions, the corner
 * belongs to one hall only.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class HallLoading {

    private static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "hall"),
            (level, helper) -> new ArrayList<>(helper.getBlockTickets().keySet())
                    .forEach(helper::removeAllTickets));

    private HallLoading() {
    }

    @SubscribeEvent
    public static void register(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    /**
     * After a restart nobody stands next to a hall block, so nothing would
     * load it — and with it its hall. Where the setting wants it, every door
     * on the list takes its chunk again here. Loading the chunk runs the
     * block's {@code onLoad}, which takes the hall's chunks.
     */
    @SubscribeEvent
    public static void loadDoors(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!dev.devpanda.factorynetwork.FnConfig.hallBlocksLoadTheirChunk()) {
            return;
        }
        var server = event.getServer();
        Halls.of(server).all().forEach((id, entry) -> entry.door().ifPresent(door -> {
            ServerLevel level = server.getLevel(door.dimension());
            if (level == null || level.dimension() == Halls.DIMENSION) {
                return;
            }
            BlockPos at = door.pos();
            TICKETS.forceChunk(level, at, at.getX() >> 4, at.getZ() >> 4, true, true);
            if (!(level.getBlockEntity(at) instanceof HallBlockEntity block) || block.hall() != id) {
                // The list still names a door that is gone: nothing to hold here.
                TICKETS.forceChunk(level, at, at.getX() >> 4, at.getZ() >> 4, false, true);
            }
        }));
    }

    /**
     * The door's own chunk: taken while the setting wants it, returned when
     * the block goes. After a restart {@link #loadDoors} takes it again.
     */
    static void holdDoorChunk(HallBlockEntity door, boolean load) {
        if (!(door.getLevel() instanceof ServerLevel level) || level.dimension() == Halls.DIMENSION
                || door.hall() == HallBlockEntity.NONE) {
            return;
        }
        if (load && !dev.devpanda.factorynetwork.FnConfig.hallBlocksLoadTheirChunk()) {
            return;
        }
        BlockPos at = door.getBlockPos();
        TICKETS.forceChunk(level, at, at.getX() >> 4, at.getZ() >> 4, load, true);
    }

    /** Takes or returns the hall's chunks, if this block is the hall's door. */
    static void hold(HallBlockEntity door, boolean load) {
        Level here = door.getLevel();
        if (!(here instanceof ServerLevel level) || level.dimension() == Halls.DIMENSION
                || door.hall() == HallBlockEntity.NONE) {
            return;
        }
        Halls.Entry entry = Halls.of(level.getServer()).get(door.hall());
        if (entry == null || !entry.door().equals(java.util.Optional.of(
                GlobalPos.of(level.dimension(), door.getBlockPos())))) {
            return;
        }
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        if (hall == null) {
            return;
        }
        BlockPos low = Halls.origin(door.hall());
        int high = entry.size().size() + 1;
        for (int cx = low.getX() >> 4; cx <= (low.getX() + high) >> 4; cx++) {
            for (int cz = low.getZ() >> 4; cz <= (low.getZ() + high) >> 4; cz++) {
                TICKETS.forceChunk(hall, low, cx, cz, load, true);
            }
        }
    }
}
