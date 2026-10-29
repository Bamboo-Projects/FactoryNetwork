package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellView;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The Mekanism side of {@link ChemicalBridge}.
 *
 * <p>Found by {@code ServiceLoader} through
 * {@code META-INF/services} in this source set, and only after
 * {@link FnMekanism#installed()} said yes. The arithmetic between a handler
 * and the network store lives here too: it is what the game tests check with
 * a handler from the API jar.
 */
public final class MekBridge implements ChemicalBridge {

    @Override
    public boolean has(Identifier id) {
        return MekRegistry.has(id);
    }

    @Override
    public List<Identifier> keys() {
        return MekRegistry.keys();
    }

    @Override
    public String name(Identifier id) {
        return MekRegistry.name(id);
    }

    @Override
    public ResourceStore newStore() {
        return new MekChemicalStore();
    }

    @Override
    public CellView open(ItemStack cell, HolderLookup.Provider registries) {
        return MekCells.open(cell, registries);
    }

    @Override
    public Map<String, Long> read(ItemStack cell, HolderLookup.Provider registries) {
        return MekCells.read(cell, registries);
    }

    @Override
    public long amountAt(Level level, BlockPos pos, Direction side, Collection<String> ids) {
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.amountIn(handler, ids);
    }

    @Override
    public long roomFor(Level level, BlockPos pos, Direction side, String id, long amount) {
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.fill(handler, id, amount, true);
    }

    @Override
    public long drainInto(Level level, BlockPos pos, Direction side, Collection<String> ids,
                          ResourceStore store, long limit) {
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : drainIntoHandler(handler, ids, store, limit);
    }

    @Override
    public long fillFrom(ResourceStore store, Level level, BlockPos pos, Direction side,
                         Collection<String> ids, long limit) {
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : fillIntoHandler(store, handler, ids, limit);
    }

    /**
     * The same, but with a handler instead of a location in the world.
     *
     * <p>Separated because the <b>calculation</b> is what is testable: a
     * Mekanism tank placed into a test run via {@code setBlock} has no side
     * configuration and therefore accepts nothing — measured and confirmed.
     * The lookup of the handler is the same as for fluids and tested
     * elsewhere; what is specific here is the back-and-forth with the store,
     * and that can be demonstrated with a handler from the API jar.
     */
    public static long drainIntoHandler(IChemicalHandler handler,
            java.util.Collection<String> ids,
            ResourceStore store, long limit) {
        long moved = 0;
        // At most eight chemicals per pass: a handler with more is drained
        // over several calls, and the loop cannot run forever if a chemical
        // neither fits nor gives way.
        for (int guard = 0; guard < 8 && moved < limit; guard++) {
            var oben = MekTanks.peek(handler, ids);
            if (oben.isEmpty()) {
                break;
            }
            String id = MekTanks.idOf(oben);
            // Ask first: only as much as the store really takes — and not a
            // drop more. Whatever is out and fits nowhere would have to go
            // back, and putting it back can fail.
            long room = store.room(id, Math.min(limit - moved, oben.getAmount()));
            if (room <= 0) {
                break;
            }
            var taken = MekTanks.drain(handler, java.util.List.of(id), room);
            if (taken.isEmpty()) {
                break;
            }
            long rest = store.insert(id, taken.getAmount());
            if (rest > 0) {
                // Should no longer happen after the query; if it does, putting
                // it back is the only answer that lets nothing vanish.
                MekTanks.fill(handler, id, rest, false);
            }
            moved += taken.getAmount() - rest;
        }
        return moved;
    }

    /** The same, but with a handler instead of a location in the world. */
    public static long fillIntoHandler(
            ResourceStore store,
            IChemicalHandler handler,
            java.util.Collection<String> ids, long limit) {
        long moved = 0;
        // With nothing specified, everything in the network. The contents come
        // out keyed by the kind's keys — here these are identifiers, and the
        // path there permits no others.
        //
        // This empty list no longer comes from the language: a worker without
        // filter halts, and a selection that matches nothing reports itself,
        // since 26 Aug, in WorldHost.chemicalsOf. Previously it did not, and
        // then a typo would fill in some arbitrary gas.
        java.util.Collection<?> wanted = ids.isEmpty() ? store.contents().keySet() : ids;
        for (Object key : wanted) {
            String id = String.valueOf(key);
            if (moved >= limit) {
                break;
            }
            long have = store.count(id);
            if (have <= 0) {
                continue;
            }
            // Trial first: whatever the machine won't take stays in the store.
            long fits = MekTanks.fill(handler, id, Math.min(have, limit - moved), true);
            if (fits <= 0) {
                continue;
            }
            long got = store.extract(id, fits);
            long placed = MekTanks.fill(handler, id, got, false);
            if (placed < got) {
                store.insert(id, got - placed);
            }
            moved += placed;
        }
        return moved;
    }
}
