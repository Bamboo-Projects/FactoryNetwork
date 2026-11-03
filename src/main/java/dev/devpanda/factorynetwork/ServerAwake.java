package dev.devpanda.factorynetwork;

import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;

/**
 * Keeps an empty server from pausing while a factory runs, if the operator
 * asks for it ({@code keepAwakeWhileFactoriesRun}, off by default).
 *
 * <p>Since 1.21.2 a dedicated server stops ticking altogether after
 * {@code pause-when-empty-seconds} without players — forced chunks included.
 * The server counts empty ticks and pauses at the threshold; every tick that
 * still runs, this resets the count while a controller is loaded. Once
 * paused, no tick runs any more, so this has to act before: a server that
 * paused before the option was switched on wakes with the next player.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID)
public final class ServerAwake {

    private static final Field EMPTY_TICKS = emptyTicksField();

    private ServerAwake() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (FnConfig.keepAwakeWhileFactoriesRun() && server.getPlayerCount() == 0) {
            holdAwake(server);
        }
    }

    /** Resets the empty count if a factory is loaded; true if it did. */
    public static boolean holdAwake(MinecraftServer server) {
        if (EMPTY_TICKS == null || !factoryLoaded(server)) {
            return false;
        }
        try {
            EMPTY_TICKS.setInt(server, 0);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static boolean factoryLoaded(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (var pos : ControllerRegistry.in(level)) {
                if (level.isLoaded(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Field emptyTicksField() {
        try {
            Field field = MinecraftServer.class.getDeclaredField("emptyTicks");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A Minecraft without the pause: nothing to keep awake.
            return null;
        }
    }

    /** The current empty count, for tests. */
    public static int emptyTicks(MinecraftServer server) throws IllegalAccessException {
        return EMPTY_TICKS == null ? -1 : EMPTY_TICKS.getInt(server);
    }

    /** Sets the empty count, for tests. */
    public static void setEmptyTicks(MinecraftServer server, int ticks) throws IllegalAccessException {
        EMPTY_TICKS.setInt(server, ticks);
    }
}
