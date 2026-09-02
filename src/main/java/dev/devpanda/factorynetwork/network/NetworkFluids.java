package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network's fluid store.
 *
 * <p>The same construction as the item store, for the same reason: a mapping
 * from type to amount, not a compound of individual tanks. Whoever works in a
 * large pack with two dozen fluids would otherwise search through all of them
 * on every access.
 *
 * <p><b>The stock lives in fluid cells</b>, as the items do in theirs.
 * Previously the network stored fluids without limit in the controller — an
 * inequality no one can explain: for iron you needed a drive, for lava you
 * did not.
 *
 * <p>Everything is counted in millibuckets, as everywhere in NeoForge — a
 * bucket is 1000. The language writes {@code 1000 fluid:water}, so the same
 * number stands in the program as in every other mod.
 */
public final class NetworkFluids implements ResourceStore {

    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private Runnable onChange = () -> { };

    /** The stock of all cells in one place — see {@link NetworkStorage}. */
    private final Map<Fluid, Long> index = new LinkedHashMap<>();
    private boolean indexValid;
    private long[] seenRevisions = new long[0];

    @Override
    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Which drives hang on the network. The controller sets them on rebuild. */
    @Override
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
        indexValid = false;
    }

    @Override
    public boolean hasDrives() {
        return !drives.isEmpty();
    }

    // ---- The interface ---------------------------------------------------
    // See NetworkStorage: the conversion must be there, otherwise each of
    // these lines would call itself.

    @Override
    public long count(Object key) {
        return count((Fluid) key);
    }

    @Override
    public long room(Object key, long wanted) {
        return room((Fluid) key, wanted);
    }

    @Override
    public long insert(Object key, long amount) {
        return insert((Fluid) key, amount);
    }

    @Override
    public long extract(Object key, long amount) {
        return extract((Fluid) key, amount);
    }

    private List<CellInventory<Fluid>> cells() {
        List<CellInventory<Fluid>> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            all.addAll(drive.fluidInventories());
        }
        return all;
    }

    private Map<Fluid, Long> index() {
        if (!indexValid || drivesChanged()) {
            rebuildIndex();
        }
        return index;
    }

    private boolean drivesChanged() {
        if (seenRevisions.length != drives.size()) {
            return true;
        }
        for (int i = 0; i < seenRevisions.length; i++) {
            if (seenRevisions[i] != drives.get(i).revision()) {
                return true;
            }
        }
        return false;
    }

    private void rebuildIndex() {
        index.clear();
        for (CellInventory<Fluid> cell : cells()) {
            cell.contentsView().forEach((fluid, amount) -> index.merge(fluid, amount, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    /**
     * How much of this type would still fit in.
     *
     * <p>Needed before a tank is drained: what the store will not take must
     * not be pulled from the tank in the first place. With items you can put
     * the rest back, with fluids the tank might not take it again — and then
     * it would be gone.
     */
    public long room(Fluid fluid, long wanted) {
        if (wanted <= 0 || fluid == Fluids.EMPTY) {
            return 0;
        }
        long free = 0;
        for (CellInventory<Fluid> cell : cells()) {
            free += cell.room(fluid);
            if (free >= wanted) {
                return wanted;
            }
        }
        return free;
    }

    /**
     * Stores it and returns what did not fit.
     *
     * <p>First into cells that already carry this type — otherwise a stock
     * splinters across all cells and occupies a type slot everywhere.
     */
    public long insert(Fluid fluid, long amount) {
        if (amount <= 0 || fluid == Fluids.EMPTY) {
            return 0;
        }
        Map<Fluid, Long> stock = index();
        long left = amount;
        List<CellInventory<Fluid>> cells = cells();
        for (CellInventory<Fluid> cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(fluid) > 0) {
                left -= cell.insert(fluid, left);
            }
        }
        for (CellInventory<Fluid> cell : cells) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(fluid, left);
        }
        if (left < amount) {
            stock.merge(fluid, amount - left, Long::sum);
            markChanged();
        }
        return left;
    }

    /** Takes out and returns how much it came to. */
    public long extract(Fluid fluid, long amount) {
        if (amount <= 0) {
            return 0;
        }
        Map<Fluid, Long> stock = index();
        long taken = 0;
        for (CellInventory<Fluid> cell : cells()) {
            if (taken >= amount) {
                break;
            }
            taken += cell.extract(fluid, amount - taken);
        }
        if (taken > 0) {
            long rest = stock.getOrDefault(fluid, 0L) - taken;
            if (rest > 0) {
                stock.put(fluid, rest);
            } else {
                stock.remove(fluid);
            }
            markChanged();
        }
        return taken;
    }

    public long count(Fluid fluid) {
        return index().getOrDefault(fluid, 0L);
    }

    /** The entire stock. A copy — see {@link NetworkStorage#contents}. */
    @Override
    public Map<Fluid, Long> contents() {
        return new LinkedHashMap<>(index());
    }

    /** How many type slots are free in total — for the display. */
    public int freeTypes() {
        int free = 0;
        for (CellInventory<Fluid> cell : cells()) {
            free += cell.freeTypes();
        }
        return free;
    }

    public void clear() {
        Map<Fluid, Long> stock = index();
        for (CellInventory<Fluid> cell : cells()) {
            cell.clear();
        }
        stock.clear();
        markChanged();
    }

    private void markChanged() {
        drives.forEach(DriveBlockEntity::setChanged);
        onChange.run();
    }

    /**
     * Saving and loading are unnecessary.
     *
     * <p>The stock lives in the cells, and they live in the drives — both
     * save themselves. A second place would be a second truth.
     */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        // Nothing to do.
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // Nothing to do.
    }
}
