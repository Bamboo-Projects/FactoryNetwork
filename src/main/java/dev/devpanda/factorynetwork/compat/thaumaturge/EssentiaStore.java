package dev.devpanda.factorynetwork.compat.thaumaturge;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network's essentia: the essentia cells in its drives, and a small
 * buffer for networks without one.
 *
 * <p>The buffer keeps essentia moving from jar to jar in a network that has
 * no cell yet (see {@link EssentiaBuffer}); cells are where it is kept. In
 * goes to the cells first, out comes from the buffer first, so the buffer
 * empties itself into nothing and the cells hold what stays.
 */
public final class EssentiaStore implements ResourceStore {

    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private final EssentiaBuffer buffer = new EssentiaBuffer();
    private Runnable onChange = () -> { };

    @Override
    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
        buffer.setChangeListener(this.onChange);
    }

    @Override
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
    }

    @Override
    public boolean hasDrives() {
        return true;
    }

    private List<CellInventory<String>> cells() {
        List<CellInventory<String>> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            all.addAll(drive.essentiaCells());
        }
        return all;
    }

    @Override
    public long count(Object key) {
        if (!(key instanceof String id)) {
            return 0;
        }
        long total = buffer.count(id);
        for (CellInventory<String> cell : cells()) {
            total += cell.count(id);
        }
        return total;
    }

    @Override
    public long room(Object key, long wanted) {
        if (!(key instanceof String id) || wanted <= 0) {
            return 0;
        }
        long free = 0;
        for (CellInventory<String> cell : cells()) {
            free += cell.room(id);
            if (free >= wanted) {
                return wanted;
            }
        }
        return Math.min(wanted, free + buffer.room(id, wanted - free));
    }

    @Override
    public long insert(Object key, long amount) {
        if (!(key instanceof String id) || amount <= 0) {
            return amount;
        }
        long left = amount;
        List<CellInventory<String>> cells = cells();
        // First the cells that already hold this aspect, so it does not
        // take a type slot in every cell.
        for (CellInventory<String> cell : cells) {
            if (left > 0 && cell.count(id) > 0) {
                left -= cell.insert(id, left);
            }
        }
        for (CellInventory<String> cell : cells) {
            if (left > 0) {
                left -= cell.insert(id, left);
            }
        }
        if (left < amount) {
            drives.forEach(DriveBlockEntity::setChanged);
            onChange.run();
        }
        return left > 0 ? buffer.insert(id, left) : 0;
    }

    @Override
    public long extract(Object key, long amount) {
        if (!(key instanceof String id) || amount <= 0) {
            return 0;
        }
        long taken = buffer.extract(id, amount);
        boolean fromCells = false;
        for (CellInventory<String> cell : cells()) {
            if (taken >= amount) {
                break;
            }
            long got = cell.extract(id, amount - taken);
            if (got > 0) {
                taken += got;
                fromCells = true;
            }
        }
        if (fromCells) {
            drives.forEach(DriveBlockEntity::setChanged);
            onChange.run();
        }
        return taken;
    }

    @Override
    public Map<?, Long> contents() {
        Map<String, Long> found = new LinkedHashMap<>();
        for (CellInventory<String> cell : cells()) {
            cell.contentsView().forEach((id, amount) -> found.merge(id, amount, Long::sum));
        }
        buffer.contents().forEach((id, amount) -> found.merge((String) id, amount, Long::sum));
        return found;
    }
}
