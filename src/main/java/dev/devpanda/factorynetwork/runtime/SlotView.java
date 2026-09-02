package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * An inventory that exposes only certain slots.
 *
 * <p>This is what turns {@code brecher_1.slots(2..3)} into a source for
 * {@code move}: everything that accesses it from outside sees a device with
 * exactly two slots. <b>That is the whole point of the exercise</b> — a worker
 * that is only supposed to empty the output must not take the input along,
 * and the handle for that lives in the code, not in the block layout.
 *
 * <p>Built over the machine's undivided inventory: whoever writes a slot
 * number knows it, and one connector per machine should be enough.
 *
 * <p>Numbers that do not exist are dropped at construction. Only the world
 * knows how many slots a third-party machine has, and a range that reaches
 * beyond them is not a program error.
 */
public final class SlotView implements IItemHandler {

    private final IItemHandler inner;
    private final List<Integer> slots;

    private SlotView(IItemHandler inner, List<Integer> slots) {
        this.inner = inner;
        this.slots = slots;
    }

    /** The view onto these slots, or {@code null} without an inventory. */
    public static IItemHandler of(IItemHandler inner, List<Integer> wanted) {
        if (inner == null) {
            return null;
        }
        List<Integer> valid = wanted.stream()
                .filter(slot -> slot >= 0 && slot < inner.getSlots())
                .distinct()
                .toList();
        return new SlotView(inner, valid);
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slots.get(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return inner.insertItem(slots.get(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inner.extractItem(slots.get(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slots.get(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inner.isItemValid(slots.get(slot), stack);
    }
}
