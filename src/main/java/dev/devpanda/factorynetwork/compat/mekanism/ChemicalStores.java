package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellView;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The path from the core to the chemicals — and the return ticket.
 *
 * <p>This class itself carries no Mekanism type in a signature; it is the door
 * the core goes through. What lies beyond is entered only when
 * {@link FnMekanism#installed()} is true — and only then does the JVM load the
 * classes that need Mekanism.
 *
 * <p>This is the same pattern as with GuideME and Jade, only one layer deeper:
 * there the mod constructor decides, here every single query does.
 */
public final class ChemicalStores {

    private ChemicalStores() {
    }

    /**
     * The chemical store for a network.
     *
     * <p>Without Mekanism, the one that can do nothing. It is not a placeholder
     * but the correct answer: in a pack without the mod there are no chemicals,
     * and a store that pretended otherwise would be a lie with side effects.
     */
    public static ResourceStore create() {
        return FnMekanism.installed() ? new MekChemicalStore() : ResourceStore.NONE;
    }

    /**
     * Opens a chemical cell, or {@code null} without Mekanism.
     *
     * <p>The factory {@code DriveBlockEntity} receives from outside.
     */
    public static CellView open(ItemStack cell,
                               net.minecraft.core.HolderLookup.Provider registries) {
        return FnMekanism.installed() ? MekCells.open(cell, registries) : null;
    }

    /**
     * How much of these chemicals is in the machine at this location.
     *
     * <p>Without Mekanism, zero — there are no chemicals there.
     */
    public static long amountAt(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids) {
        if (!FnMekanism.installed()) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.amountIn(handler, ids);
    }

    /**
     * How much of this chemical currently fits into the machine.
     *
     * <p>The trial before filling: a recipe whose gas doesn't fully fit should
     * not even start — otherwise the machine would be left with a half-done
     * calculation. To the outside these are only identifiers and numbers; the
     * handler stays within this package.
     */
    public static long roomFor(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            String id, long amount) {
        if (!FnMekanism.installed() || amount <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : MekTanks.fill(handler, id, amount, true);
    }

    /**
     * Pulls from a machine into the network storage.
     *
     * <p><b>Ask first, then pull.</b> Whatever the store won't take must never
     * leave the container in the first place: a gas that is out and fits
     * nowhere would be gone. The same caution as with fluids.
     *
     * @return how much arrived
     */
    public static long drainInto(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids,
            ResourceStore store, long limit) {
        if (!FnMekanism.installed() || limit <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : drainIntoHandler(handler, ids, store, limit);
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
    public static long drainIntoHandler(mekanism.api.chemical.IChemicalHandler handler,
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

    /**
     * Fills from the network storage into a machine.
     *
     * @return how much arrived
     */
    public static long fillFrom(ResourceStore store,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction side, java.util.Collection<String> ids, long limit) {
        if (!FnMekanism.installed() || limit <= 0) {
            return 0;
        }
        var handler = MekTanks.at(level, pos, side);
        return handler == null ? 0 : fillIntoHandler(store, handler, ids, limit);
    }

    /** The same, but with a handler instead of a location in the world. */
    public static long fillIntoHandler(
            ResourceStore store,
            mekanism.api.chemical.IChemicalHandler handler,
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

    /**
     * What is in a cell, as identifier to amount.
     *
     * <p>For the tooltip: the item always exists, even without Mekanism — then
     * the answer is empty, and the tooltip says why.
     */
    public static Map<String, Long> read(ItemStack cell,
                                        net.minecraft.core.HolderLookup.Provider registries) {
        return FnMekanism.installed()
                ? MekCells.read(cell, registries) : new LinkedHashMap<>();
    }
}
