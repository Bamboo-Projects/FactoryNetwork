package dev.devpanda.factorynetwork.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network's storage.
 *
 * <p><b>Key-based, not slot-based.</b> This is the one lesson from the
 * previous project: a stock modelled as a list of slots makes every search
 * linear and every filter operation quadratic. The measurement in
 * {@code docs/referenz-messung-speicherzugriff.md} shows what that costs at
 * ten thousand kinds — 67 milliseconds for a single pass, on a tick budget
 * of 50.
 *
 * <p><b>The stock lives in the cells, not here.</b> This class is the
 * network's view of it: it knows the drives and distributes among them. A
 * separate reserve alongside would be a second truth, one that drifts apart
 * the moment someone pulls a cell out.
 */
public final class NetworkStorage implements ResourceStore {

    private final List<DriveBlockEntity> drives = new ArrayList<>();

    /**
     * The stock of all cells in one place.
     *
     * <p><b>Without it every question recounts everything.</b> Ten drives with
     * ten cells each and sixty-four kinds are six thousand entries — and it is
     * asked often: by every worker, by every condition, by every display,
     * several times in the same tick.
     *
     * <p>Own deposits and withdrawals are folded in, not recounted. A recount
     * happens only when someone else has done something — pulled a cell out,
     * say. The drive's revision counter reports that.
     */
    private final Map<ItemKey, Long> index = new LinkedHashMap<>();
    private boolean indexValid;

    /** A cell and the drive it sits in. */
    private record Holder(CellInventory<ItemKey> cell, DriveBlockEntity drive) {
    }

    /**
     * Which cells hold a sort, built and kept together with {@link #index}.
     *
     * <p>Without it every insert and extract searched all cells: 320 cells at
     * 20,000 sorts, 33 µs an insert (docs/messung-last-2026-11-02.md). With it
     * the cost no longer grows with the size of the store.
     */
    private final Map<ItemKey, List<Holder>> holders = new java.util.HashMap<>();

    /**
     * The sorts that changed since {@link #drainChanges}, or {@code null} when
     * more changed than can be named (a cell pulled, a bus refreshed).
     */
    private java.util.Set<ItemKey> changed = null;
    /** The state of the drives when the index was built. */
    private long[] seenRevisions = new long[0];
    /** Called on every change — the controller then sends in a batch. */
    private Runnable onChange = () -> { };

    @Override
    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /**
     * Which drives hang on the network.
     *
     * <p>The controller sets them on every rebuild. If nothing stands here
     * there is no space — a network without a drive stores nothing.
     */
    @Override
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
        indexValid = false;
    }

    /**
     * The foreign inventories that count toward the storage.
     *
     * <p>From the program's {@code store} lines; the controller sets them on
     * every rebuild, like the drives. Without them a chest on the network
     * remains a device and nothing more.
     */
    private final List<StorageBus> buses = new ArrayList<>();

    public void setBuses(List<StorageBus> found) {
        buses.clear();
        buses.addAll(found);
        for (StorageBus bus : buses) {
            bus.refresh();
        }
        indexValid = false;
    }

    /**
     * Re-reads the foreign inventories — once per tick.
     *
     * <p>A chest changes without the network, and it tells no one. So the
     * controller checks; here the index is discarded only when something has
     * really changed, so that not every tick recounts all cells.
     *
     * @return whether something has changed
     */
    public boolean refreshBuses() {
        boolean changed = false;
        for (StorageBus bus : buses) {
            changed |= bus.refresh();
        }
        if (changed) {
            indexValid = false;
            onChange.run();
        }
        return changed;
    }

    /** The buses, in the order in which things are stored. */
    public List<StorageBus> buses() {
        return List.copyOf(buses);
    }

    /** The stock, fresh enough. */
    private Map<ItemKey, Long> index() {
        if (!indexValid || drivesChanged()) {
            rebuildIndex();
        }
        return index;
    }

    /**
     * Has someone else worked on the drives?
     *
     * <p>A comparison of a few numbers. The alternative would be to rebuild
     * the index on every question — exactly what it saves.
     */
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
        holders.clear();
        List<CellInventory<ItemKey>> all = cells();
        for (int i = 0; i < all.size(); i++) {
            Holder holder = new Holder(all.get(i), cellOwners.get(i));
            all.get(i).contentsView().forEach((item, count) -> {
                index.merge(item, count, Long::sum);
                holders.computeIfAbsent(item, key -> new ArrayList<>(1)).add(holder);
            });
        }
        // Everything may have changed; the next send compares in full.
        changed = null;
        // And whatever lies in the foreign inventories. Nothing is read here —
        // refreshBuses does that per tick; here stands only what stood there last.
        for (StorageBus bus : buses) {
            bus.contents().forEach((item, count) -> index.merge(item, count, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    /**
     * Whether there is any space at all.
     *
     * <p>A bus counts too: a network without a drive but with a chest on the
     * {@code store} does store something after all — just there.
     */
    @Override
    public boolean hasDrives() {
        return !drives.isEmpty() || !buses.isEmpty();
    }

    // ---- The interface ---------------------------------------------------
    //
    // The detour via the common denominator. The conversion must be there:
    // without it each of these lines would call itself, because Object
    // matches Object and Item is no narrower than Object — an overflow that
    // only surfaces once someone takes the general path.

    /**
     * The general path accepts both: an item or an identifier.
     *
     * <p><b>Because both questions occur.</b> The language asks for
     * {@code eisenbarren} and means every variant; the terminal asks for
     * exactly the stack someone clicked. A single type here would mean that
     * one of the two sides must translate — and the translation belongs at
     * the stock, not at its edges.
     */
    @Override
    public long count(Object key) {
        if (key instanceof ItemKey found) {
            return count(found);
        }
        return key instanceof Item item ? count(item) : 0;
    }

    @Override
    public long insert(Object key, long amount) {
        if (key instanceof ItemKey found) {
            return insert(found, amount);
        }
        return key instanceof Item item ? insert(item, amount) : amount;
    }

    @Override
    public long extract(Object key, long amount) {
        if (key instanceof ItemKey found) {
            return extract(found, amount);
        }
        return key instanceof Item item ? extract(item, amount) : 0;
    }

    /**
     * How many more of them would fit in.
     *
     * <p><b>Cells and storage buses.</b> A bus answers via {@code insertItem}
     * with {@code simulate} — the same deposit, just without consequences.
     *
     * <p>Earlier only the cells counted here, on the grounds that a chest
     * cannot try things out. That is not true, and it would have grown
     * expensive: since a worker asks before the grab, too low an answer would
     * stall a network whose buses still have room.
     *
     * <p>The order differs from that of depositing, and that is of no
     * consequence: what is asked for is the sum, not where it lands.
     */
    @Override
    public long room(Object key, long wanted) {
        if (wanted <= 0) {
            return 0;
        }
        // As with count and insert: both questions occur. An identifier
        // without its own data is the bare item.
        ItemKey item = key instanceof ItemKey found ? found
                : key instanceof Item plain ? ItemKey.bare(plain) : null;
        if (item == null) {
            return 0;
        }
        long free = 0;
        for (CellInventory<ItemKey> cell : cells()) {
            free += cell.room(item);
            if (free >= wanted) {
                return wanted;
            }
        }
        for (StorageBus bus : buses) {
            free += bus.room(item, wanted - free);
            if (free >= wanted) {
                return wanted;
            }
        }
        return free;
    }

    /** All cells of all drives, freshly read. */
    private List<CellInventory<ItemKey>> cells() {
        List<CellInventory<ItemKey>> all = new ArrayList<>();
        cellOwners.clear();
        for (DriveBlockEntity drive : drives) {
            for (CellInventory<ItemKey> cell : drive.inventories()) {
                all.add(cell);
                cellOwners.add(drive);
            }
        }
        return all;
    }

    /**
     * The drive of each cell from the last {@link #cells()}, at the same index.
     *
     * <p>So that a change marks only the drive it happened in. Marking all of
     * them made up most of an insert: setChanged also updates the comparators
     * around every drive (docs/messung-last-2026-11-02.md).
     */
    private final List<DriveBlockEntity> cellOwners = new ArrayList<>();

    /**
     * Stores it and returns what did not fit.
     *
     * <p>First into cells that already carry this kind — otherwise a stock
     * splinters across all cells and occupies a type slot everywhere.
     */
    public long insert(ItemKey item, long count) {
        if (count <= 0) {
            return 0;
        }
        // First bring the index up to date, then compute: after that the
        // own deposit is the only change, and that is known.
        Map<ItemKey, Long> stock = index();
        long left = count;
        // Whoever pushed ahead comes before the cells.
        for (StorageBus bus : buses) {
            if (left <= 0 || bus.priority() <= 0) {
                break;
            }
            left = bus.insert(item, left);
        }
        java.util.Set<DriveBlockEntity> touched = new java.util.HashSet<>(2);
        boolean touchedBus = left < count;
        // First the cells that already hold this sort, found in the directory.
        List<Holder> holding = holders.get(item);
        if (holding != null) {
            for (int i = 0; i < holding.size() && left > 0; i++) {
                long stored = holding.get(i).cell().insert(item, left);
                if (stored > 0) {
                    left -= stored;
                    touched.add(holding.get(i).drive());
                }
            }
        }
        // Then any cell with room. Only here is a search over all cells left:
        // when the sort is new or its cells are full.
        if (left > 0) {
            List<CellInventory<ItemKey>> cells = cells();
            for (int i = 0; i < cells.size() && left > 0; i++) {
                CellInventory<ItemKey> cell = cells.get(i);
                boolean had = cell.count(item) > 0;
                long stored = cell.insert(item, left);
                if (stored > 0) {
                    left -= stored;
                    touched.add(cellOwners.get(i));
                    if (!had) {
                        holders.computeIfAbsent(item, key -> new ArrayList<>(1))
                                .add(new Holder(cell, cellOwners.get(i)));
                    }
                }
            }
        }
        long afterCells = left;
        // And last the foreign inventories — unless one has pushed ahead: a
        // priority higher than zero means "here first". On a tie the cells
        // win, because they belong to the network.
        for (StorageBus bus : buses) {
            if (left <= 0) {
                break;
            }
            left = bus.insert(item, left);
        }
        if (left < count) {
            stock.merge(item, count - left, Long::sum);
            noteChanged(item);
            if (touchedBus || left < afterCells) {
                // A bus took part: what it holds is not one drive's matter.
                markChanged();
            } else {
                markChanged(touched);
            }
        }
        return left;
    }

    public long insert(ItemStack stack) {
        return stack.isEmpty() ? 0 : insert(ItemKey.of(stack), stack.getCount());
    }

    /** Takes out and returns how much it came to. */
    public long extract(ItemKey item, long count) {
        if (count <= 0) {
            return 0;
        }
        Map<ItemKey, Long> stock = index();
        long taken = 0;
        java.util.Set<DriveBlockEntity> touched = new java.util.HashSet<>(2);
        List<Holder> holding = holders.get(item);
        if (holding != null) {
            for (var it = holding.iterator(); it.hasNext() && taken < count; ) {
                Holder holder = it.next();
                long got = holder.cell().extract(item, count - taken);
                if (got > 0) {
                    taken += got;
                    touched.add(holder.drive());
                }
                if (holder.cell().count(item) <= 0) {
                    it.remove();
                }
            }
            if (holding.isEmpty()) {
                holders.remove(item);
            }
        }
        long fromCells = taken;
        // From the foreign inventories only once the cells are empty. What
        // lies in a chest often has a reason — a Drawer as a buffer, a
        // reserve for the hand. The cells belong to the network and are the
        // place it reaches for first.
        for (StorageBus bus : buses) {
            if (taken >= count) {
                break;
            }
            taken += bus.extract(item, count - taken);
        }
        if (taken > 0) {
            long rest = stock.getOrDefault(item, 0L) - taken;
            if (rest > 0) {
                stock.put(item, rest);
            } else {
                stock.remove(item);
            }
            noteChanged(item);
            if (taken > fromCells) {
                markChanged();
            } else {
                markChanged(touched);
            }
        }
        return taken;
    }

    /**
     * How much of this identifier lies in the network, across all variants.
     *
     * <p><b>Here the language meets the store.</b> A program says
     * {@code eisenbarren} and means everything by that name — not one
     * particular variant. The store, by contrast, carries items: a named
     * pickaxe and a bare one are two entries.
     *
     * <p>The translation therefore lives here and not in the language: it is
     * a question for the stock, not a question of grammar.
     */
    public long count(Item item) {
        long found = 0;
        for (Map.Entry<ItemKey, Long> entry : index().entrySet()) {
            if (entry.getKey().item() == item) {
                found += entry.getValue();
            }
        }
        return found;
    }

    /**
     * The stock per identifier, summed across all variants.
     *
     * <p>What the language and the display boards need: there
     * {@code eisenbarren} is one number and not a dozen entries. Whoever
     * needs the individual items — the terminal — asks
     * {@link #contents()}.
     */
    public Map<Item, Long> byItem() {
        Map<Item, Long> found = new LinkedHashMap<>();
        index().forEach((key, count) -> found.merge(key.item(), count, Long::sum));
        return found;
    }

    /** Stores it as if the item had no data of its own. */
    public long insert(Item item, long count) {
        return insert(ItemKey.bare(item), count);
    }

    /**
     * Takes from this identifier, no matter which variant.
     *
     * <p>Bare ones first: whoever writes {@code move 64 eisenbarren} means
     * the reserve and not the named pickaxe that happens to carry the same
     * identifier. Only once nothing bare is left do the others get their
     * turn — and then in the order of the stock.
     */
    public long extract(Item item, long count) {
        long taken = extract(ItemKey.bare(item), count);
        if (taken >= count) {
            return taken;
        }
        for (ItemKey key : List.copyOf(index().keySet())) {
            if (taken >= count) {
                break;
            }
            if (key.item() == item && !key.isBare()) {
                taken += extract(key, count - taken);
            }
        }
        return taken;
    }

    public long count(ItemKey item) {
        return index().getOrDefault(item, 0L);
    }

    /**
     * The entire stock across all cells.
     *
     * <p>A copy, not a view: the stock is often iterated while something is
     * being moved on the side, and a view would lead there to a
     * ConcurrentModificationException at a place that has nothing to do with
     * the moving.
     */
    @Override
    public Map<ItemKey, Long> contents() {
        return new LinkedHashMap<>(index());
    }

    public int distinctTypes() {
        return index().size();
    }

    /** How many type slots are free in total — for the display. */
    public int freeTypes() {
        int free = 0;
        for (CellInventory<ItemKey> cell : cells()) {
            free += cell.freeTypes();
        }
        return free;
    }

    public void clear() {
        Map<ItemKey, Long> stock = index();
        for (CellInventory<ItemKey> cell : cells()) {
            cell.clear();
        }
        stock.clear();
        holders.clear();
        changed = null;
        markChanged();
    }

    /**
     * Reports that something has changed.
     *
     * <p><b>The drives must come along.</b> The stock lives in their cells,
     * and without this notification Minecraft does not know the chunk must be
     * saved — for a drive in a different chunk than the controller, the stock
     * after a restart would be the one from before.
     */
    private void markChanged() {
        drives.forEach(DriveBlockEntity::setChanged);
        onChange.run();
    }

    private void noteChanged(ItemKey item) {
        if (changed != null) {
            changed.add(item);
        }
    }

    /**
     * The sorts changed since the last call, or {@code null} when the caller
     * has to compare in full. Starts a new round of noting.
     */
    public java.util.Set<ItemKey> drainChanges() {
        index();
        java.util.Set<ItemKey> result = changed;
        changed = new java.util.HashSet<>();
        return result;
    }

    /** The amount of one sort, from the index. */
    public long amountOf(ItemKey item) {
        return index().getOrDefault(item, 0L);
    }

    private void markChanged(java.util.Collection<DriveBlockEntity> touched) {
        touched.forEach(DriveBlockEntity::setChanged);
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
