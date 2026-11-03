package dev.devpanda.factorynetwork.network.link;

import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ItemStock} in the shape of slots, for the transfer code.
 *
 * <p>One slot per sort found, each showing at most a stack, and one empty
 * slot at the end. Inserting works on every slot and goes to the stock as a
 * whole; extracting takes from the sort of the slot. The sorts are read once,
 * when the view is made: a view lives for one transfer.
 */
@SuppressWarnings("removal")
public final class ItemStockView implements IItemHandler, KeyedItems {

    private final ItemStock stock;
    /** The sorts as the stock handed them over; turned into keys on demand. */
    private final List<Object> raws = new ArrayList<>();
    /** The key of each slot, built on first look; {@code null} until then. */
    private ItemKey[] keys = new ItemKey[0];
    private long[] amounts = new long[8];

    private final List<Item> wanted;
    private boolean read;

    public ItemStockView(ItemStock stock, List<Item> items) {
        this.stock = stock;
        this.wanted = items;
    }

    /**
     * Reads the sorts on first use. A view made only to be narrowed
     * (see {@link KeyedItems#narrow}) never reads the whole stock.
     */
    private void read() {
        if (read) {
            return;
        }
        read = true;
        stock.findRaw(wanted, (raw, amount) -> {
            if (amount > 0) {
                if (raws.size() == amounts.length) {
                    amounts = java.util.Arrays.copyOf(amounts, amounts.length * 2);
                }
                amounts[raws.size()] = amount;
                raws.add(raw);
            }
        });
        keys = new ItemKey[raws.size()];
    }

    private ItemKey keyAt(int slot) {
        ItemKey key = keys[slot];
        if (key == null) {
            key = stock.keyOf(raws.get(slot));
            keys[slot] = key;
        }
        return key;
    }

    @Override
    public IItemHandler view(List<Item> items) {
        return new ItemStockView(stock, items);
    }

    @Override
    public int getSlots() {
        read();
        return raws.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        read();
        if (slot < 0 || slot >= raws.size()) {
            return ItemStack.EMPTY;
        }
        ItemKey key = keyAt(slot);
        return key.toStack((int) Math.min(amounts[slot], key.maxStackSize()));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long inserted = stock.insert(ItemKey.of(stack), stack.getCount(), simulate);
        return inserted >= stack.getCount() ? ItemStack.EMPTY
                : stack.copyWithCount((int) (stack.getCount() - inserted));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        read();
        if (slot < 0 || slot >= raws.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemKey key = keyAt(slot);
        long taken = stock.extract(key, Math.min(amount, key.maxStackSize()), simulate);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            amounts[slot] = Math.max(0, amounts[slot] - taken);
        }
        return key.toStack((int) taken);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
