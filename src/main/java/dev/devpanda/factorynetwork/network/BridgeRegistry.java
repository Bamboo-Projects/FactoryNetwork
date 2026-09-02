package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Which quantum bridges exist in a world, by their number.
 *
 * <p><b>Nothing is searched for; things register.</b> A bridge that had to
 * search the world for its partner would scan millions of blocks on every
 * question — and the question comes up on every rebuild of the network. It
 * registers itself on load instead, the way the controller already does.
 *
 * <p>Weak references to the worlds, so an unloaded level does not linger — the
 * same caution as in {@link ControllerRegistry}.
 */
public final class BridgeRegistry {

    private static final Map<LevelAccessor, Map<UUID, Set<BlockPos>>> BY_LEVEL =
            new WeakHashMap<>();

    public static synchronized void add(Level level, UUID pair, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, key -> new java.util.HashMap<>())
                .computeIfAbsent(pair, key -> new LinkedHashSet<>())
                .add(pos.immutable());
    }

    public static synchronized void remove(Level level, UUID pair, BlockPos pos) {
        Map<UUID, Set<BlockPos>> pairs = BY_LEVEL.get(level);
        if (pairs == null) {
            return;
        }
        Set<BlockPos> found = pairs.get(pair);
        if (found != null) {
            found.remove(pos);
            if (found.isEmpty()) {
                pairs.remove(pair);
            }
        }
    }

    /**
     * The counterpart of this bridge, or {@code null}.
     *
     * <p>Three rules, and each catches a case that occurs in the game:
     *
     * <ol>
     *   <li><b>Without a second half a bridge belongs to no one.</b></li>
     *   <li><b>A bridge is never its own partner</b> — otherwise a network
     *       would point through itself back at itself. This needs no rule of
     *       its own: what is asked for is the <i>other</i> spot.</li>
     *   <li><b>Three bridges with the same number connect nothing at all.</b>
     *       In creative mode a half can be duplicated, and "two of three, but
     *       which" is not a rule anyone can guess. Better visibly dead than
     *       secretly wrong.</li>
     * </ol>
     */
    public static synchronized @Nullable BlockPos partnerOf(Level level, BlockPos pos) {
        Map<UUID, Set<BlockPos>> pairs = BY_LEVEL.get(level);
        if (pairs == null) {
            return null;
        }
        for (Set<BlockPos> places : pairs.values()) {
            if (!places.contains(pos)) {
                continue;
            }
            if (places.size() != 2) {
                return null;
            }
            for (BlockPos other : places) {
                if (!other.equals(pos)) {
                    return other;
                }
            }
        }
        return null;
    }

    private BridgeRegistry() {
    }
}
