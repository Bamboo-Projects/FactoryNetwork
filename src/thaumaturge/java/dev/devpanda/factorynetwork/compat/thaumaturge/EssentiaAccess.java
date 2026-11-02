package dev.devpanda.factorynetwork.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Essentia in Thaumaturge's jars and containers, through its essentia storage
 * capability.
 *
 * <p>Ask first, then act, as everywhere in the network: how much would go
 * in or come out is tried in a transaction that is not committed; only then
 * does the store give or take, and the real transfer follows.
 */
public final class EssentiaAccess implements EssentiaBridge {

    public EssentiaAccess() {
    }

    // ---- Aspects -------------------------------------------------------------

    private static Registry<IAspect> registry() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY);
    }

    @Override
    public List<String> aspects() {
        Registry<IAspect> aspects = registry();
        if (aspects == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Identifier id : aspects.keySet()) {
            ids.add(id.toString());
        }
        return ids;
    }

    @Override
    public String name(String id) {
        Holder<IAspect> aspect = holder(id);
        if (aspect == null) {
            return id;
        }
        String tag = aspect.value().tag();
        return tag.isEmpty() ? id : Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    private static Holder<IAspect> holder(Object id) {
        Registry<IAspect> aspects = registry();
        Identifier key = id instanceof String text ? Identifier.tryParse(text) : null;
        if (aspects == null || key == null) {
            return null;
        }
        return aspects.get(ResourceKey.create(IAspect.REGISTRY_KEY, key)).orElse(null);
    }

    // ---- Machines ------------------------------------------------------------

    /**
     * The essentia storage of the container, from the connector's side if it
     * offers one there, otherwise from any side that does.
     *
     * <p>Thaumaturge's jars open only at the top, where its tubes attach. A
     * connector beside a jar would see nothing, and whoever builds a row of
     * jars would have to run cable over all of them. The fallback keeps the
     * connector wherever it fits; which side is used changes nothing about
     * what a jar holds.
     */
    private static IEssentiaStorage storageAt(Level level, BlockPos pos, Direction side) {
        IEssentiaStorage storage = level.getCapability(EssentiaCapabilities.STORAGE, pos, side);
        if (storage != null) {
            return storage;
        }
        for (Direction other : new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            storage = level.getCapability(EssentiaCapabilities.STORAGE, pos, other);
            if (storage != null) {
                return storage;
            }
        }
        return level.getCapability(EssentiaCapabilities.STORAGE, pos, null);
    }

    @Override
    public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
        IEssentiaStorage storage = storageAt(level, pos, side);
        if (storage == null) {
            return 0;
        }
        long total = 0;
        for (Object key : keys) {
            Holder<IAspect> aspect = holder(key);
            if (aspect != null) {
                total += storage.amount(aspect);
            }
        }
        return total;
    }

    @Override
    public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                     Collection<?> keys, long limit) {
        IEssentiaStorage storage = storageAt(level, pos, side);
        if (storage == null || limit <= 0) {
            return 0;
        }
        long moved = 0;
        for (Object key : keys) {
            Holder<IAspect> aspect = holder(key);
            if (aspect == null || moved >= limit) {
                continue;
            }
            int wanted = (int) Math.min(Integer.MAX_VALUE, Math.min(limit - moved, from.count(key)));
            if (wanted <= 0) {
                continue;
            }
            int room;
            try (Transaction trial = Transaction.openRoot()) {
                room = storage.insert(aspect, wanted, trial);
            }
            if (room <= 0) {
                continue;
            }
            long taken = from.extract(key, room);
            if (taken <= 0) {
                continue;
            }
            int arrived;
            try (Transaction real = Transaction.openRoot()) {
                arrived = storage.insert(aspect, (int) taken, real);
                real.commit();
            }
            if (arrived < taken) {
                // The container decided differently between trial and act.
                from.insert(key, taken - arrived);
            }
            moved += arrived;
        }
        return moved;
    }

    @Override
    public long drain(Level level, BlockPos pos, Direction side, Collection<?> keys,
                      ResourceStore into, long limit) {
        IEssentiaStorage storage = storageAt(level, pos, side);
        if (storage == null || limit <= 0) {
            return 0;
        }
        long moved = 0;
        for (Object key : keys) {
            Holder<IAspect> aspect = holder(key);
            if (aspect == null || moved >= limit) {
                continue;
            }
            long room = into.room(key, limit - moved);
            if (room <= 0) {
                continue;
            }
            int available;
            try (Transaction trial = Transaction.openRoot()) {
                available = storage.extract(aspect, (int) Math.min(Integer.MAX_VALUE, room), trial);
            }
            if (available <= 0) {
                continue;
            }
            int pulled;
            try (Transaction real = Transaction.openRoot()) {
                pulled = storage.extract(aspect, available, real);
                real.commit();
            }
            long stranded = into.insert(key, pulled);
            if (stranded > 0) {
                // Back into the container; the trial above makes this rare.
                try (Transaction back = Transaction.openRoot()) {
                    storage.insert(aspect, (int) stranded, back);
                    back.commit();
                }
            }
            moved += pulled - stranded;
        }
        return moved;
    }
}
