package dev.devpanda.factorynetwork.network.link;

import dev.devpanda.factorynetwork.block.entity.StorageLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The storage systems a link can attach to, registered by their adapters.
 *
 * <p>Registered during loading by the entry point, one line per adapter and
 * only when its mod is present; read later from the server thread.
 */
public final class StorageLinks {

    /** A system: does it sit next to this spot, and how is it attached. */
    public record System(String id, Detector detects,
                         Function<StorageLinkBlockEntity, StorageLinkAdapter> attach) {
    }

    @FunctionalInterface
    public interface Detector {
        boolean nextTo(Level level, BlockPos pos);
    }

    private static final List<System> SYSTEMS = new ArrayList<>();

    private StorageLinks() {
    }

    public static void register(String id, Detector detects,
                                Function<StorageLinkBlockEntity, StorageLinkAdapter> attach) {
        SYSTEMS.add(new System(id, detects, attach));
    }

    /** The first system found next to the link, or {@code null}. */
    public static @Nullable System at(Level level, BlockPos pos) {
        for (System system : SYSTEMS) {
            if (system.detects().nextTo(level, pos)) {
                return system;
            }
        }
        return null;
    }
}
