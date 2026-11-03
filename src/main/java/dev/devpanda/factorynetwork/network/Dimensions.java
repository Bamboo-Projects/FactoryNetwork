package dev.devpanda.factorynetwork.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A network across worlds: a quantum bridge into the Nether, a port into a
 * factory hall.
 *
 * <p>Positions in the network carry a dimension only where they leave the
 * controller's world; {@code null} means the controller's own. That keeps
 * every save and every caller from before as it was. This class is the one
 * place that turns the dimension back into a world — written out at every
 * call site, one would sooner or later look up a Nether block in the
 * overworld.
 */
public final class Dimensions {

    private Dimensions() {
    }

    /** The world a dimension names, seen from the controller's world. */
    public static @Nullable Level levelOf(Level home, @Nullable ResourceKey<Level> dimension) {
        if (dimension == null || dimension == home.dimension()) {
            return home;
        }
        var server = home.getServer();
        return server == null ? null : server.getLevel(dimension);
    }

    /** How a world is written in the network: {@code null} for the controller's own. */
    public static @Nullable ResourceKey<Level> keyOf(Level home, Level here) {
        return here.dimension() == home.dimension() ? null : here.dimension();
    }
}
