package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which quantum bridges exist, by their number — across all worlds.
 *
 * <p><b>Nothing is searched for; things register.</b> A bridge that had to
 * search the world for its partner would scan millions of blocks on every
 * question — and the question comes up on every rebuild of the network. It
 * registers itself on load instead, the way the controller already does.
 *
 * <p><b>One list for all worlds.</b> The two halves of a pair may stand in
 * the overworld and in the Nether; a list per world would never bring them
 * together. Entries leave when their block unloads, and the list is emptied
 * when the server stops, so nothing outlives the world it came from.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.devpanda.factorynetwork.FactoryNetwork.MOD_ID)
public final class BridgeRegistry {

    private static final Map<UUID, Set<GlobalPos>> BY_PAIR = new HashMap<>();

    public static synchronized void add(Level level, UUID pair, BlockPos pos) {
        BY_PAIR.computeIfAbsent(pair, key -> new LinkedHashSet<>())
                .add(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    public static synchronized void remove(Level level, UUID pair, BlockPos pos) {
        Set<GlobalPos> found = BY_PAIR.get(pair);
        if (found != null) {
            found.remove(GlobalPos.of(level.dimension(), pos.immutable()));
            if (found.isEmpty()) {
                BY_PAIR.remove(pair);
            }
        }
    }

    /** At server stop: a world opened next takes nothing over. */
    @net.neoforged.bus.api.SubscribeEvent
    public static synchronized void clear(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        BY_PAIR.clear();
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
    public static synchronized @Nullable GlobalPos partnerOf(Level level, BlockPos pos) {
        GlobalPos self = GlobalPos.of(level.dimension(), pos.immutable());
        for (Set<GlobalPos> places : BY_PAIR.values()) {
            if (!places.contains(self)) {
                continue;
            }
            if (places.size() != 2) {
                return null;
            }
            for (GlobalPos other : places) {
                if (!other.equals(self)) {
                    return other;
                }
            }
        }
        return null;
    }

    private BridgeRegistry() {
    }
}
