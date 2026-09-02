package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.ItemStack;

/**
 * An energy cell as storage.
 *
 * <p>It keeps its charge in memory and only writes back when the drive saves
 * or the cell comes out — for the same reason as with the other cells: the
 * store changes every tick, and rewriting an item every tick is work for
 * nothing.
 *
 * <p>It differs from the rest of the cell world in one point, and that one
 * already stands in {@link EnergyCellTier}: <b>there are no types.</b> With
 * that the whole accounting that makes up {@link CellInventory} falls away,
 * and two lines remain — take in and give out.
 */
public final class EnergyCellView implements CellView {

    private final ItemStack cell;
    private final EnergyCellTier tier;
    private int charge;

    private EnergyCellView(ItemStack cell) {
        this.cell = cell;
        this.tier = EnergyCellItem.tierOf(cell);
        this.charge = tier == null ? 0 : EnergyCellItem.chargeOf(cell);
    }

    /** Invalid if no energy cell is inserted there. */
    public static EnergyCellView of(ItemStack cell) {
        return new EnergyCellView(cell);
    }

    @Override
    public ItemStack stack() {
        return cell;
    }

    @Override
    public boolean isValid() {
        return tier != null;
    }

    public int stored() {
        return charge;
    }

    public int capacity() {
        return tier == null ? 0 : tier.capacity();
    }

    public int room() {
        return capacity() - charge;
    }

    /** Takes in what fits, and says how much it was. */
    public int fill(int amount) {
        int taken = Math.max(0, Math.min(amount, room()));
        charge += taken;
        return taken;
    }

    /** Gives out what is there, and says how much it was. */
    public int take(int amount) {
        int given = Math.max(0, Math.min(amount, charge));
        charge -= given;
        return given;
    }

    @Override
    public void flush() {
        EnergyCellItem.setCharge(cell, charge);
    }
}
