package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.server.level.ServerLevel;

/**
 * A hall as one machine: its name is the installation name of everything
 * inside, as behind a gateway.
 *
 * <p>Unnamed, every connector in the hall goes by its own name (hall ports,
 * mode 1). Named {@code erzhalle}, the connector {@code ofen} inside is
 * {@code erzhalle/ofen} to the network outside, and a {@code multiblock}
 * template drives the hall as one installation (mode 2). A label that
 * already carries a slash keeps its own installation, as with a gateway.
 */
public final class HallNaming {

    private HallNaming() {
    }

    /** Names the hall; blank takes the name away. Returns the name now. */
    public static String set(ServerLevel level, HallBlockEntity door, String wanted) {
        Halls halls = Halls.of(level.getServer());
        if (halls.get(door.hall()) == null) {
            return "";
        }
        String now = wanted == null ? "" : wanted.trim();
        halls.setName(door.hall(), now);
        door.showInstallation(now);
        // The names of the connectors inside change: the networks touching
        // the hall's block rebuild.
        ControllerRegistry.markChanged(level, door.getBlockPos());
        return now;
    }

    /** The installation name of the hall this spot lies in, or {@code null}. */
    public static @org.jspecify.annotations.Nullable String instanceAt(ServerLevel hall,
                                                                        net.minecraft.core.BlockPos pos) {
        if (hall.dimension() != Halls.DIMENSION) {
            return null;
        }
        Halls.Entry entry = Halls.of(hall.getServer()).get(Halls.hallAt(pos));
        return entry == null ? null : entry.name().orElse(null);
    }
}
