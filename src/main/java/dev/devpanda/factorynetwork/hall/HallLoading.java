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
 * A hall runs while its block stands.
 *
 * <p>Standing, not loaded: the hall holds its own chunks in the hall
 * dimension from the moment its block is placed or entered until the block is
 * broken. The block's chunk in the overworld may unload; the hall goes on.
 * With {@code hallsLoadThemselves = false} the hall only runs while its block
 * is loaded, like a machine.
 *
 * <p>Tickets left over from the last session are thrown away at start and
 * taken again from the hall list, so a hall whose block is gone does not run
 * on forever.
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

    /** After a restart: every hall whose block stands takes its chunks again. */
    @SubscribeEvent
    public static void loadHalls(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!dev.devpanda.factorynetwork.FnConfig.hallsLoadThemselves()) {
            return;
        }
        var server = event.getServer();
        ServerLevel hall = server.getLevel(Halls.DIMENSION);
        if (hall == null) {
            return;
        }
        Halls halls = Halls.of(server);
        halls.all().forEach((id, entry) -> entry.door().ifPresent(door -> {
            // One look at the block, which loads its chunk once: a list from
            // an older version or a crash may name a door that is gone.
            ServerLevel level = server.getLevel(door.dimension());
            if (level != null && level.getBlockEntity(door.pos()) instanceof HallBlockEntity block
                    && block.hall() == id) {
                force(hall, id, entry.size(), true);
            } else {
                halls.setDoor(id, null);
            }
        }));
    }

    /** The block went for good (broken, blown up, replaced): its hall stops. */
    static void release(HallBlockEntity door) {
        if (!(door.getLevel() instanceof ServerLevel level) || level.dimension() == Halls.DIMENSION
                || door.hall() == HallBlockEntity.NONE) {
            return;
        }
        Halls halls = Halls.of(level.getServer());
        Halls.Entry entry = halls.get(door.hall());
        if (entry == null || !entry.door().equals(java.util.Optional.of(
                GlobalPos.of(level.dimension(), door.getBlockPos())))) {
            return;
        }
        halls.setDoor(door.hall(), null);
        ServerLevel hall = level.getServer().getLevel(Halls.DIMENSION);
        if (hall != null) {
            force(hall, door.hall(), entry.size(), false);
        }
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
        if (hall != null) {
            force(hall, door.hall(), entry.size(), load);
        }
    }

    private static void force(ServerLevel hall, int id, HallTier tier, boolean load) {
        BlockPos low = Halls.origin(id);
        int high = tier.size() + 1;
        for (int cx = low.getX() >> 4; cx <= (low.getX() + high) >> 4; cx++) {
            for (int cz = low.getZ() >> 4; cz <= (low.getZ() + high) >> 4; cz++) {
                TICKETS.forceChunk(hall, low, cx, cz, load, true);
            }
        }
    }
}
