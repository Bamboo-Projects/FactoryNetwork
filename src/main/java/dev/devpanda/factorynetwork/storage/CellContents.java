package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * What sits inside an item cell.
 *
 * <p><b>The contents live in the item, not in the drive.</b> That is the
 * reason a cell is worth something: you pull it out, carry it away and plug
 * it in somewhere else — the stored contents come along. Were they in the
 * drive, the cell would be a mere key and not storage.
 *
 * <p>The accounting is key-based, as everywhere in the network: a mapping
 * from type to amount. A list of stacks would already be a linear search per
 * access at sixty types, and there are dozens of those per tick.
 *
 * <p>The work itself lives in {@link CellFormat}, since fluids sit the same
 * way. This class is the convenient entry point for the more common case.
 */
public final class CellContents {

    private CellContents() {
    }

    public static Map<ItemKey, Long> read(ItemStack cell,
                                          net.minecraft.core.HolderLookup.Provider registries) {
        return CellFormat.ITEMS.read(cell, registries);
    }

    public static void write(ItemStack cell, Map<ItemKey, Long> contents,
                             net.minecraft.core.HolderLookup.Provider registries) {
        CellFormat.ITEMS.write(cell, contents, registries);
    }

    /** How much sits inside it in total. */
    public static long total(Map<Item, Long> contents) {
        return CellFormat.total(contents);
    }
}
