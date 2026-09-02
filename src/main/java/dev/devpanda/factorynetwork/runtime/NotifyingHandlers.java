package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Inventories and tanks that report when the network itself has put
 * something in.
 *
 * <p>This is needed for {@code device_output}: what the network puts into a
 * device must not pass as the device's output on the next look. So the
 * baseline has to be advanced exactly when a write happened.
 *
 * <p><b>Why the notification hangs on the inventory and not on the call
 * site:</b> many places write — the interpreter for {@code move} and
 * {@code insert}, the worker runtime on three paths, and both put things back
 * when network storage is full. Each of those places would have to remember,
 * and a forgotten one would not be visibly broken: a flow that inserts and
 * then waits would wake itself up. Here the notification hangs on what is
 * being written to; whoever writes need not know anything about it.
 *
 * <p>Only what actually arrived is reported. A dry run ({@code simulate})
 * changes nothing and reports nothing — otherwise the editor's acceptance
 * probe would trigger events.
 */
public final class NotifyingHandlers {

    private NotifyingHandlers() {
    }

    /** The same inventory, but reporting. Unchanged without a receiver. */
    public static IItemHandler items(IItemHandler handler, Runnable onFilled) {
        return handler == null || onFilled == null ? handler : new Items(handler, onFilled);
    }

    /** The same tank, but reporting. Unchanged without a receiver. */
    public static IFluidHandler fluids(IFluidHandler handler, Runnable onFilled) {
        return handler == null || onFilled == null ? handler : new Fluids(handler, onFilled);
    }

    private record Items(IItemHandler inner, Runnable onFilled) implements IItemHandler {

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack rest = inner.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() < stack.getCount()) {
                onFilled.run();
            }
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }

    private record Fluids(IFluidHandler inner, Runnable onFilled) implements IFluidHandler {

        @Override
        public int getTanks() {
            return inner.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return inner.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return inner.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return inner.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = inner.fill(resource, action);
            if (action.execute() && filled > 0) {
                onFilled.run();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return inner.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return inner.drain(maxDrain, action);
        }
    }
}
