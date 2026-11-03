package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.lang.BuiltinEvents;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.runtime.Value;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who is in which hall — and the events {@code hall_entered},
 * {@code hall_left} and {@code hall_empty} when that changes.
 *
 * <p><b>Looked at, not told.</b> A player leaves a hall through its wall,
 * by dying, by logging out, by {@code /tp}, by an ender pearl through a
 * portal of another mod. Hooking each of those would miss the next one. Once
 * a second, the players in the hall dimension are counted per hall; what
 * differs from the last count is the event. A second of delay is nothing
 * for lights and machines.
 *
 * <p>The event goes to every controller whose network reaches the hall:
 * through its block outside or through one of its ports inside.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class HallPresence {

    private static final int PERIOD = 20;

    /** Hall number to the names of the players last seen in it. */
    private static final Map<Integer, Set<String>> INSIDE = new HashMap<>();

    private HallPresence() {
    }

    @SubscribeEvent
    public static void count(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        ServerLevel hall = server.getLevel(Halls.DIMENSION);
        Map<Integer, Set<String>> now = new HashMap<>();
        if (hall != null) {
            for (ServerPlayer player : hall.players()) {
                int id = Halls.hallAt(player.blockPosition());
                if (id > 0) {
                    now.computeIfAbsent(id, key -> new LinkedHashSet<>())
                            .add(player.getGameProfile().name());
                }
            }
        }
        Set<Integer> halls = new LinkedHashSet<>(INSIDE.keySet());
        halls.addAll(now.keySet());
        for (int id : halls) {
            Set<String> before = INSIDE.getOrDefault(id, Set.of());
            Set<String> after = now.getOrDefault(id, Set.of());
            if (before.equals(after)) {
                continue;
            }
            List<ControllerBlockEntity> controllers = controllersOf(server, id);
            Value name = new Value.Text(nameOf(server, id));
            for (String player : after) {
                if (!before.contains(player)) {
                    fire(controllers, BuiltinEvents.HALL_ENTERED, List.of(name, new Value.Text(player)));
                }
            }
            for (String player : before) {
                if (!after.contains(player)) {
                    fire(controllers, BuiltinEvents.HALL_LEFT, List.of(name, new Value.Text(player)));
                }
            }
            if (!before.isEmpty() && after.isEmpty()) {
                fire(controllers, BuiltinEvents.HALL_EMPTY, List.of(name));
            }
        }
        INSIDE.clear();
        INSIDE.putAll(now);
    }

    @SubscribeEvent
    public static void clear(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        INSIDE.clear();
    }

    /** Who is in this hall right now, as last counted. */
    public static Set<String> playersIn(int hall) {
        return Set.copyOf(INSIDE.getOrDefault(hall, Set.of()));
    }

    /**
     * What a program calls the hall: its installation name, or
     * {@code halle_<number>} when it has none.
     */
    public static String nameOf(MinecraftServer server, int id) {
        Halls.Entry entry = Halls.of(server).get(id);
        return entry == null ? "halle_" + id : entry.name().orElse("halle_" + id);
    }

    private static List<ControllerBlockEntity> controllersOf(MinecraftServer server, int id) {
        Set<ControllerBlockEntity> found = new LinkedHashSet<>();
        Halls.Entry entry = Halls.of(server).get(id);
        GlobalPos door = entry == null ? null : entry.door().orElse(null);
        ServerLevel outside = door == null ? null : server.getLevel(door.dimension());
        if (outside != null && outside.isLoaded(door.pos())) {
            found.addAll(ControllerRegistry.allOwning(outside, door.pos()));
        }
        ServerLevel hall = server.getLevel(Halls.DIMENSION);
        if (hall != null) {
            for (var port : HallPorts.of(id)) {
                if (hall.isLoaded(port)) {
                    found.addAll(ControllerRegistry.allOwning(hall, port));
                }
            }
        }
        return List.copyOf(found);
    }

    private static void fire(List<ControllerBlockEntity> controllers, String event, List<Value> arguments) {
        for (ControllerBlockEntity controller : controllers) {
            controller.fireEvent(event, arguments);
        }
    }
}
