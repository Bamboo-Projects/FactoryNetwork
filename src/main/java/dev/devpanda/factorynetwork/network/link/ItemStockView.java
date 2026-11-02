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
    private final List<ItemKey> keys = new ArrayList<>();
    private final List<Long> amounts = new ArrayList<>();

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
        stock.find(wanted, (key, amount) -> {
            if (amount > 0) {
                keys.add(key);
                amounts.add(amount);
            }
        });
    }

    @Override
    public IItemHandler view(List<Item> items) {
        return new ItemStockView(stock, items);
    }

    @Override
    public int getSlots() {
        read();
        return keys.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        read();
        if (slot < 0 || slot >= keys.size()) {
            return ItemStack.EMPTY;
        }
        ItemKey key = keys.get(slot);
        return key.toStack((int) Math.min(amounts.get(slot), key.maxStackSize()));
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
        if (slot < 0 || slot >= keys.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemKey key = keys.get(slot);
        long taken = stock.extract(key, Math.min(amount, key.maxStackSize()), simulate);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            amounts.set(slot, Math.max(0, amounts.get(slot) - taken));
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
