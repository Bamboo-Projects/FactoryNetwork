package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.Item;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cell as storage, with its two limits.
 *
 * <p>Inserting knows two cases, and the difference decides how the game
 * feels: a <b>new type</b> needs a free type slot, an <b>already present</b>
 * one only more amount. That is why a full cell still accepts what already
 * sits inside it — otherwise you would have to keep an eye on every cell
 * individually when tidying up.
 *
 * <p>The type is left open because fluids need the same accounting. All that
 * differs is the registry in {@link CellFormat} and the size — not a single
 * line of this accounting.
 *
 * @param <T> item or fluid
 */
public final class CellInventory<T> implements CellView {

    private final ItemStack cell;
    private final CellSize size;
    private final CellFormat<T> format;
    private final Map<T, Long> contents;

    /**
     * Where the registries come from.
     *
     * <p><b>Kept, not queried afresh on every access.</b> What an item
     * carries beyond its id can be neither read nor written without them —
     * and a cell is touched several times between opening and writing back.
     */
    private final HolderLookup.Provider registries;

    private CellInventory(ItemStack cell, CellSize size, CellFormat<T> format,
                          HolderLookup.Provider registries) {
        this.cell = cell;
        this.size = size;
        this.format = format;
        this.registries = registries;
        this.contents = new LinkedHashMap<>(format.read(cell, registries));
    }

    /**
     * A cell of any kind.
     *
     * <p>Open because there is a third kind this core must not know about:
     * chemicals belong to Mekanism. Whoever opens them brings size and format
     * along themselves — the accounting with types and amounts stays the same.
     */
    public static <T> CellInventory<T> of(ItemStack cell, CellSize size,
                                          CellFormat<T> format,
                                          HolderLookup.Provider registries) {
        return new CellInventory<>(cell, size, format, registries);
    }

    /** An item cell. Invalid if none is inserted there. */
    public static CellInventory<ItemKey> ofItems(ItemStack cell,
                                                 HolderLookup.Provider registries) {
        return new CellInventory<>(cell, StorageCellItem.tierOf(cell), CellFormat.ITEMS,
                registries);
    }

    /** A fluid cell. Invalid if none is inserted there. */
    public static CellInventory<net.minecraft.world.level.material.Fluid> ofFluids(
            ItemStack cell, HolderLookup.Provider registries) {
        return new CellInventory<>(cell, FluidCellItem.tierOf(cell), CellFormat.FLUIDS,
                registries);
    }

    /** The item this view belongs to. */
    @Override
    public ItemStack stack() {
        return cell;
    }

    @Override
    public boolean isValid() {
        return size != null;
    }

    public Map<T, Long> contents() {
        return Map.copyOf(contents);
    }

    /**
     * The same contents without a copy.
     *
     * <p>For the network index that runs over all cells: with ten drives of
     * ten cells each, one copy per cell is a hundred copies for one query.
     * Whoever keeps the view while the cell changes sees the change — that is
     * intended here and the reason both exist.
     */
    public Map<T, Long> contentsView() {
        return Collections.unmodifiableMap(contents);
    }

    public long count(T key) {
        return contents.getOrDefault(key, 0L);
    }

    public int usedTypes() {
        return contents.size();
    }

    public long usedAmount() {
        return CellFormat.total(contents);
    }

    /** How much of this type would still fit in. */
    public long room(T key) {
        if (!isValid()) {
            return 0;
        }
        boolean known = contents.containsKey(key);
        if (!known && contents.size() >= size.types()) {
            return 0;
        }
        return Math.max(0, size.amount() - usedAmount());
    }

    /** Stores and returns how much actually went in. */
    public long insert(T key, long count) {
        long room = Math.min(count, room(key));
        if (room <= 0) {
            return 0;
        }
        contents.merge(key, room, Long::sum);
        return room;
    }

    /** Takes out and returns how much it was. */
    public long extract(T key, long count) {
        Long available = contents.get(key);
        if (available == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(available, count);
        if (taken >= available) {
            contents.remove(key);
        } else {
            contents.put(key, available - taken);
        }
        return taken;
    }

    /** How many type slots are still free. */
    public int freeTypes() {
        return isValid() ? Math.max(0, size.types() - contents.size()) : 0;
    }

    public void clear() {
        contents.clear();
    }

    /** Writes back into the item. Without this, everything was only in memory. */
    public void flush() {
        format.write(cell, contents, registries);
    }
}
