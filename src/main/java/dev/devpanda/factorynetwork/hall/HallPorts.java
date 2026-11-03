package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which ports each hall has, by hall number.
 *
 * <p>Like the quantum bridges: nothing is searched for, ports register when
 * they load. A hall that runs is loaded, so its ports stand in this list
 * whenever the network asks; a search of the shell would scan up to 50,000
 * wall blocks on every rebuild.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class HallPorts {

    private static final Map<Integer, Set<BlockPos>> BY_HALL = new HashMap<>();

    private HallPorts() {
    }

    static synchronized void add(int hall, BlockPos pos) {
        BY_HALL.computeIfAbsent(hall, key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    static synchronized void remove(int hall, BlockPos pos) {
        Set<BlockPos> found = BY_HALL.get(hall);
        if (found != null) {
            found.remove(pos);
            if (found.isEmpty()) {
                BY_HALL.remove(hall);
            }
        }
    }

    /** The ports of this hall, in the hall dimension. */
    public static synchronized Set<BlockPos> of(int hall) {
        Set<BlockPos> found = BY_HALL.get(hall);
        return found == null ? Set.of() : Set.copyOf(found);
    }

    @SubscribeEvent
    public static synchronized void clear(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        BY_HALL.clear();
    }
}
