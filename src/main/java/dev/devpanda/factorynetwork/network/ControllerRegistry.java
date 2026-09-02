package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Which controllers exist in a world.
 *
 * <p>Needed for the reverse question: a cable does not know which network it
 * belongs to — the channel counts live in the controller's graph. So whoever
 * looks at a cable must first find the controller that knows it.
 *
 * <p>The alternative would be to write each cable's controller into it. That
 * would mean touching every BlockEntity on every rebuild of the network — a
 * hundred times more writing for the same answer.
 *
 * <p>Weak references to the worlds, so an unloaded level does not linger.
 */
public final class ControllerRegistry {

    private static final Map<LevelAccessor, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> positions = BY_LEVEL.get(level);
        if (positions != null) {
            positions.remove(pos);
        }
    }

    public static synchronized Set<BlockPos> in(Level level) {
        return Collections.unmodifiableSet(
                BY_LEVEL.getOrDefault(level, Collections.emptySet()));
    }

    /**
     * The controller whose network contains this spot.
     *
     * <p>If it belongs to several, the first one wins — for a display that is
     * enough. For execution it makes no difference: there a specific controller
     * always does the asking.
     */
    public static Optional<ControllerBlockEntity> owning(Level level, BlockPos pos) {
        for (BlockPos candidate : in(level)) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && controller.graph().contains(pos)) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    /**
     * Rebuilds the networks that touch this spot.
     *
     * <p>Needed when something has changed that the graph does not notice on
     * its own — a router's lane assignment, for instance, lives in a
     * BlockEntity and triggers no neighbour update.
     *
     * <p>The six neighbours count too: a block that was only just connected is
     * not yet in any graph. Without the neighbours, the very case this is
     * needed for would go uncovered.
     */
    public static void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos candidate : Set.copyOf(in(level))) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && touches(controller.graph(), pos)) {
                controller.rebuildNetwork();
            }
        }
    }

    private static boolean touches(FactoryGraph graph, BlockPos pos) {
        if (graph.contains(pos)) {
            return true;
        }
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (graph.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private ControllerRegistry() {
    }
}
