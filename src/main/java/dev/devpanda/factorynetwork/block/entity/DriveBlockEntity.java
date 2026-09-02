package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import dev.devpanda.factorynetwork.storage.EnergyCellItem;
import dev.devpanda.factorynetwork.storage.EnergyCellView;
import dev.devpanda.factorynetwork.storage.FluidCellItem;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A drive with ten slots.
 *
 * <p>Ten as in Applied Energistics: enough that one drive is worth it, few
 * enough that a large base needs several.
 *
 * <p>The cells lie here, but their contents sit inside the cells themselves.
 * The drive is a shelf, not a store.
 */
public class DriveBlockEntity extends ShelfBlockEntity {

    public static final int SLOTS = 10;

    /** The open item cells, by slot number. */
    private final java.util.Map<Integer,
            CellInventory<dev.devpanda.factorynetwork.storage.ItemKey>>
            openItems = new java.util.HashMap<>();

    /**
     * The open fluid cells, by slot number.
     *
     * <p>Two maps rather than one with a placeholder type: a shared one would
     * mean an unchecked cast on every access, and the compiler could no longer
     * help.
     */
    private final java.util.Map<Integer, CellInventory<net.minecraft.world.level.material.Fluid>>
            openFluids = new java.util.HashMap<>();

    /**
     * The open energy cells, by slot number.
     *
     * <p>The third map for the same reason as the second — and with the same
     * gain: an energy cell carries a number, not a map, and so it does not fit
     * into {@link CellInventory}.
     */
    private final java.util.Map<Integer, EnergyCellView> openEnergy = new java.util.HashMap<>();

    /**
     * The open chemical cells.
     *
     * <p>As {@link CellView} and not as {@code CellInventory<Chemical>}: the
     * type of a chemical belongs to Mekanism, and this class is always loaded.
     * Whoever really wants to read the cells brings their own factory — and it
     * lives in {@code compat/mekanism}.
     */
    private final java.util.Map<Integer, CellView> openChemicals = new java.util.HashMap<>();

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.DRIVE.get(), pos, state, SLOTS);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof StorageCellItem
                || stack.getItem() instanceof FluidCellItem
                || stack.getItem() instanceof EnergyCellItem;
    }

    @Override
    public dev.devpanda.factorynetwork.client.menu.ShelfMenu.Layout layout() {
        return dev.devpanda.factorynetwork.client.menu.ShelfMenu.DRIVE;
    }

    public ItemStack cell(int slot) {
        return getItem(slot);
    }

    public void setCell(int slot, ItemStack stack) {
        setItem(slot, stack);
    }

    public NonNullList<ItemStack> cells() {
        return parts();
    }

    /**
     * A cell leaves its slot.
     *
     * <p>Write back first, then swap: a cell that goes out takes its contents
     * with it — but only if they are stored in the item.
     */
    @Override
    protected void beforeSlotChange(int slot) {
        CellView leaving = openItems.remove(slot);
        if (leaving == null) {
            leaving = openChemicals.remove(slot);
        }
        if (leaving == null) {
            leaving = openFluids.remove(slot);
        }
        if (leaving == null) {
            leaving = openEnergy.remove(slot);
        }
        if (leaving != null) {
            leaving.flush();
        }
    }

    /**
     * The inserted cells as living stores.
     *
     * <p><b>They are held, not re-read on every access.</b> That is the
     * difference between an installation that runs and one that stutters: the
     * contents sit as NBT in the item, and unpacking them per access means, at
     * ten cells with sixty-four types each, sending several thousand entries
     * per tick through registry lookups. Applied Energistics keeps its cells
     * in memory for the same reason and writes only when the BlockEntity is
     * saved.
     *
     * <p>The binding is to the item itself, not to the slot number: whoever
     * pulls out one cell and inserts another gets a new view, even if the slot
     * is the same.
     */
    public List<CellInventory<dev.devpanda.factorynetwork.storage.ItemKey>> inventories() {
        return live(openItems, StorageCellItem.class,
                cell -> CellInventory.ofItems(cell, registries()));
    }

    /** The same view onto the fluid cells. */
    public List<CellInventory<net.minecraft.world.level.material.Fluid>> fluidInventories() {
        return live(openFluids, FluidCellItem.class,
                cell -> CellInventory.ofFluids(cell, registries()));
    }

    /**
     * Where the registries come from.
     *
     * <p>What an item carries beyond its identifier cannot be read without
     * them. A drive without a world exists only between being crafted and
     * being placed — and no cell sits in it there.
     */
    private net.minecraft.core.HolderLookup.Provider registries() {
        return level == null
                ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
    }

    /**
     * The chemical cells, opened with a factory from outside.
     *
     * <p>The factory comes from {@code compat/mekanism} and is the only way a
     * Mekanism type enters the picture here — as a return value behind
     * {@link CellView}, which the core knows. Without Mekanism no one calls
     * this method, and the cells stay unopened in the drive.
     */
    public List<CellView> chemicalCells(
            java.util.function.Function<ItemStack, CellView> open) {
        return live(openChemicals,
                dev.devpanda.factorynetwork.storage.ChemicalCellItem.class, open);
    }

    /**
     * And onto the energy cells.
     *
     * <p>They count toward the network's power reserve, not toward storage.
     * What queries them is therefore not the network index but
     * {@code NetworkPower}.
     */
    public List<EnergyCellView> energyCells() {
        return live(openEnergy, EnergyCellItem.class, EnergyCellView::of);
    }

    private <V extends CellView> List<V> live(
            java.util.Map<Integer, V> cache, Class<?> kind,
            java.util.function.Function<ItemStack, ? extends V> open) {
        List<V> found = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(slot);
            V alive = cache.get(slot);
            if (!kind.isInstance(stack.getItem())) {
                if (alive != null) {
                    cache.remove(slot);
                }
                continue;
            }
            if (alive == null || alive.stack() != stack) {
                // A different cell in the slot — the old one already wrote
                // its contents back when it was taken out.
                alive = open.apply(stack);
                if (!alive.isValid()) {
                    continue;
                }
                cache.put(slot, alive);
            }
            found.add(alive);
        }
        return found;
    }

    /**
     * Writes all open cells back into their items.
     *
     * <p>Must happen before every save and whenever a cell leaves the drive.
     * Otherwise the item holds contents from before.
     */
    public void flushCells() {
        openItems.values().forEach(CellInventory::flush);
        openFluids.values().forEach(CellInventory::flush);
        openEnergy.values().forEach(EnergyCellView::flush);
        openChemicals.values().forEach(CellView::flush);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        openItems.clear();
        openFluids.clear();
        openEnergy.clear();
        super.loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // The crux: what is held in memory must go into the items before
        // saving. Without this line the contents after a restart would be the
        // ones from before.
        flushCells();
        super.saveAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        flushCells();
        super.setRemoved();
    }
}
