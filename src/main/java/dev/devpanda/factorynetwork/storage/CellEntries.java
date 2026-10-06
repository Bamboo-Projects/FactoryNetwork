package dev.devpanda.factorynetwork.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing the stored list of a cell, without the cell.
 *
 * <p><b>Apart from {@link CellFormat} for one reason:</b> that class holds the
 * formats for items and fluids as constants, and those reach into the game's
 * registries. This part needs nothing but the list — which is why it can be
 * checked without a running game.
 */
final class CellEntries {

    private CellEntries() {
    }

    /** Splits a stored list into what can be read and what has to be kept as it is. */
    static <T> CellFormat.Loaded<T> load(ListTag entries, String amountKey,
                                         CellFormat.Entry<T> entry,
                                         HolderLookup.Provider registries) {
        Map<T, Long> contents = new LinkedHashMap<>();
        List<CompoundTag> unreadable = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag one = entries.getCompound(i);
            long count = one.getLong(amountKey);
            if (count <= 0) {
                continue;
            }
            T key = entry.read(one, registries);
            if (key == null) {
                unreadable.add(one.copy());
                continue;
            }
            // Add up rather than overwrite: two entries can fall onto the
            // same key after a format change.
            contents.merge(key, count, Long::sum);
        }
        return new CellFormat.Loaded<>(contents, unreadable);
    }

    /** The stored list for these contents, with the unreadable entries behind them. */
    static <T> ListTag save(Map<T, Long> contents, List<CompoundTag> unreadable,
                            String amountKey, CellFormat.Entry<T> entry,
                            HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        contents.forEach((key, count) -> {
            if (count <= 0) {
                return;
            }
            CompoundTag one = new CompoundTag();
            entry.write(one, key, registries);
            one.putLong(amountKey, count);
            entries.add(one);
        });
        unreadable.forEach(one -> entries.add(one.copy()));
        return entries;
    }

    /** How much these entries add up to. */
    static long amountOf(List<CompoundTag> entries, String amountKey) {
        long amount = 0;
        for (CompoundTag one : entries) {
            amount += one.getLong(amountKey);
        }
        return amount;
    }
}
