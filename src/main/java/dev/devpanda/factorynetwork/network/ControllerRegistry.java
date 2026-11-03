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
     * Every loaded controller, in every world, with the world it stands in.
     *
     * <p>A network may reach through a quantum bridge or a hall port into
     * another world, so the question "which network is this spot in" cannot
     * stop at the spot's own world.
     */
    private static java.util.List<ControllerBlockEntity> loaded() {
        return loaded(null);
    }

    /**
     * Like {@link #loaded()}, but from worlds other than {@code here} only the
     * controllers whose network leaves their own world — the rest cannot
     * reach a spot in {@code here}, and asking every controller of every
     * world for every placed cable would cost what it costs.
     */
    private static java.util.List<ControllerBlockEntity> loaded(@org.jspecify.annotations.Nullable Level here) {
        java.util.List<ControllerBlockEntity> found = new java.util.ArrayList<>();
        java.util.List<Map.Entry<LevelAccessor, Set<BlockPos>>> entries;
        synchronized (ControllerRegistry.class) {
            entries = new java.util.ArrayList<>();
            for (Map.Entry<LevelAccessor, Set<BlockPos>> entry : BY_LEVEL.entrySet()) {
                entries.add(Map.entry(entry.getKey(), Set.copyOf(entry.getValue())));
            }
        }
        for (Map.Entry<LevelAccessor, Set<BlockPos>> entry : entries) {
            if (!(entry.getKey() instanceof Level level) || level.isClientSide()) {
                continue;
            }
            boolean foreign = here != null && level != here;
            if (foreign && !ABROAD.contains(level.dimension())) {
                continue;
            }
            for (BlockPos candidate : entry.getValue()) {
                if (foreign && !reachingAbroad(level, candidate)) {
                    continue;
                }
                if (level.isLoaded(candidate)
                        && level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller) {
                    found.add(controller);
                }
            }
        }
        return found;
    }

    /**
     * The controller whose network contains this spot.
     *
     * <p>If it belongs to several, the first one wins — for a display that is
     * enough. For execution it makes no difference: there a specific controller
     * always does the asking. Controllers in the spot's own world are asked
     * first; then those whose network reaches into it from elsewhere.
     */
    public static Optional<ControllerBlockEntity> owning(Level level, BlockPos pos) {
        for (BlockPos candidate : Set.copyOf(in(level))) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && controller.graph().contains(pos)) {
                return Optional.of(controller);
            }
        }
        if (level.isClientSide()) {
            return Optional.empty();
        }
        for (ControllerBlockEntity controller : loaded(level)) {
            if (controller.getLevel() != level && controller.graph().contains(level, pos)) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    /**
     * Every controller whose network contains this spot — more than one when
     * two controllers share a cable, for example one inside a hall and one
     * outside it.
     */
    public static java.util.List<ControllerBlockEntity> allOwning(Level level, BlockPos pos) {
        java.util.List<ControllerBlockEntity> found = new java.util.ArrayList<>();
        for (BlockPos candidate : Set.copyOf(in(level))) {
            if (level.isLoaded(candidate)
                    && level.getBlockEntity(candidate) instanceof ControllerBlockEntity controller
                    && controller.graph().contains(pos)) {
                found.add(controller);
            }
        }
        if (!level.isClientSide()) {
            for (ControllerBlockEntity controller : loaded(level)) {
                if (controller.getLevel() != level && controller.graph().contains(level, pos)) {
                    found.add(controller);
                }
            }
        }
        return found;
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
        if (level.isClientSide()) {
            return;
        }
        for (ControllerBlockEntity controller : loaded(level)) {
            if (touches(controller.graph(), level, pos)) {
                controller.rebuildNetwork();
            }
        }
    }

    /**
     * Marks the networks that touch this spot for a rebuild on one of their
     * next ticks.
     *
     * <p>For blocks that join or leave a network: they are placed by players,
     * machines and other mods alike, and only the block itself hears about all
     * of them. Bundled, unlike {@link #refreshAround}: a rebuild costs in
     * proportion to the network.
     */
    public static void markChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        for (ControllerBlockEntity controller : loaded(level)) {
            if (touches(controller.graph(), level, pos)) {
                controller.markNetworkDirty();
            }
        }
    }

    /**
     * A chunk came or went. A network running into it, or ending at its
     * border, looks different now.
     */
    public static void onChunkChange(net.neoforged.neoforge.event.level.ChunkEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        net.minecraft.world.level.ChunkPos chunk = event.getChunk().getPos();
        for (ControllerBlockEntity controller : loaded(level)) {
            if (controller.graph().nearChunk(level.dimension(), chunk.x(), chunk.z())) {
                controller.markNetworkDirty();
            }
        }
    }

    /** Controllers whose network leaves their own world, as world and position. */
    private static final Set<net.minecraft.core.GlobalPos> REACHING = new java.util.HashSet<>();
    /** The worlds that hold at least one of them. */
    private static final Set<net.minecraft.resources.ResourceKey<Level>> ABROAD = new java.util.HashSet<>();

    /** Called after every rebuild: does this controller's network leave its world? */
    public static synchronized void reachesAbroad(Level level, BlockPos pos, boolean reaches) {
        var at = net.minecraft.core.GlobalPos.of(level.dimension(), pos.immutable());
        if (reaches ? REACHING.add(at) : REACHING.remove(at)) {
            ABROAD.clear();
            REACHING.forEach(known -> ABROAD.add(known.dimension()));
        }
    }

    private static synchronized boolean reachingAbroad(Level level, BlockPos pos) {
        return REACHING.contains(net.minecraft.core.GlobalPos.of(level.dimension(), pos));
    }

    private static boolean touches(FactoryGraph graph, Level level, BlockPos pos) {
        if (graph.contains(level, pos)) {
            return true;
        }
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (graph.contains(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private ControllerRegistry() {
    }
}
