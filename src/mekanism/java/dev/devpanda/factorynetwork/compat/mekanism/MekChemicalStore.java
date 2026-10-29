package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network's chemical store.
 *
 * <p>The same arithmetic as for items and fluids: the stock sits in the cells,
 * this class is the network's view onto it, and an index in front of it
 * answers the questions that come often. It is recounted only when someone
 * else has done something — the drive's revision count reports that.
 *
 * <p><b>Strings to the outside.</b> The {@link ResourceStore} interface talks
 * in terms of {@code "mekanism:hydrogen"}, not {@code Chemical}. That is the
 * price of the controller being able to hold it without knowing Mekanism — and
 * it is small: between identifier and chemical lies one registry lookup, and
 * that happens once per query, not per cell.
 */
final class MekChemicalStore implements ResourceStore {

    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private Runnable onChange = () -> { };

    private final Map<Chemical, Long> index = new LinkedHashMap<>();
    private boolean indexValid;
    private long[] seenRevisions = new long[0];

    @Override
    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

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

    @SuppressWarnings("unchecked")
    private List<CellInventory<Chemical>> cells() {
        List<CellInventory<Chemical>> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            for (CellView view : drive.chemicalCells(cell -> MekCells.open(cell,
                    drive.getLevel() == null
                            ? net.minecraft.core.RegistryAccess.EMPTY
                            : drive.getLevel().registryAccess()))) {
                // The factory above builds only chemical cells; what it
                // returns is one by construction — the core just cannot
                // express that.
                all.add((CellInventory<Chemical>) view);
            }
        }
        return all;
    }

    private Map<Chemical, Long> index() {
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
        for (CellInventory<Chemical> cell : cells()) {
            cell.contentsView().forEach((chemical, amount) ->
                    index.merge(chemical, amount, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    /**
     * The chemical behind an identifier, or {@code null}.
     *
     * <p>The key comes in as an {@code Object}, because the interface serves
     * three kinds. Nothing but a string can arrive here — the path here goes
     * through {@code ResourceKind}, and the value model checks the form
     * already at creation time.
     */
    private Chemical chemical(Object key) {
        return key instanceof String id ? MekCells.chemical(id) : null;
    }

    @Override
    public long count(Object key) {
        Chemical chemical = chemical(key);
        return chemical == null ? 0 : index().getOrDefault(chemical, 0L);
    }

    @Override
    public Map<String, Long> contents() {
        Map<String, Long> found = new LinkedHashMap<>();
        index().forEach((chemical, amount) -> {
            if (amount > 0) {
                found.put(MekCells.idOf(chemical), amount);
            }
        });
        return found;
    }

    @Override
    public long room(Object key, long wanted) {
        Chemical chemical = chemical(key);
        if (chemical == null || wanted <= 0) {
            return 0;
        }
        long free = 0;
        for (CellInventory<Chemical> cell : cells()) {
            free += cell.room(chemical);
            if (free >= wanted) {
                return wanted;
            }
        }
        return free;
    }

    /**
     * Stores and returns what did not fit.
     *
     * <p>First into cells that already carry this chemical — otherwise a stock
     * splinters across all cells and occupies a type slot everywhere.
     */
    @Override
    public long insert(Object key, long amount) {
        Chemical chemical = chemical(key);
        if (chemical == null || amount <= 0) {
            return amount;
        }
        index();
        long left = amount;
        List<CellInventory<Chemical>> cells = cells();
        for (CellInventory<Chemical> cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(chemical) > 0) {
                left -= cell.insert(chemical, left);
            }
        }
        for (CellInventory<Chemical> cell : cells) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(chemical, left);
        }
        if (left < amount) {
            index.merge(chemical, amount - left, Long::sum);
            markChanged();
        }
        return left;
    }

    @Override
    public long extract(Object key, long amount) {
        Chemical chemical = chemical(key);
        if (chemical == null || amount <= 0) {
            return 0;
        }
        index();
        long taken = 0;
        for (CellInventory<Chemical> cell : cells()) {
            if (taken >= amount) {
                break;
            }
            taken += cell.extract(chemical, amount - taken);
        }
        if (taken > 0) {
            long left = index.getOrDefault(chemical, 0L) - taken;
            if (left <= 0) {
                index.remove(chemical);
            } else {
                index.put(chemical, left);
            }
            markChanged();
        }
        return taken;
    }

    private void markChanged() {
        drives.forEach(DriveBlockEntity::setChanged);
        onChange.run();
    }
}
