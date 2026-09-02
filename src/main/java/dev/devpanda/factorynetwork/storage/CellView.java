package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.ItemStack;

/**
 * An open cell in the drive.
 *
 * <p>The drive keeps its cells in memory and only writes back when it has to
 * (see {@code DriveBlockEntity.inventories}). It needs three questions for
 * that, and only these three: <b>which item do you belong to, is there
 * actually a cell inserted, and write yourself back.</b>
 *
 * <p>That is why this lives here and not as three identical methods in every
 * kind of cell: the drive manages item, fluid and energy cells with the same
 * accounting, and what sets them apart is their contents — not their
 * management.
 */
public interface CellView {

    /** The item this view belongs to. */
    ItemStack stack();

    /** Is a cell of this kind actually inserted there? */
    boolean isValid();

    /** Writes the contents back into the item. */
    void flush();
}
