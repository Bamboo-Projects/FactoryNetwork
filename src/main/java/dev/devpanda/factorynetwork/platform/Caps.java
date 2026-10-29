package dev.devpanda.factorynetwork.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The one door to item, fluid and energy access in the world.
 *
 * <p><b>Why it exists.</b> Since 1.21.9 NeoForge offers these through
 * transactions ({@code ResourceHandler}, {@code EnergyHandler}); the
 * interfaces this mod was written against ({@code IItemHandler},
 * {@code IFluidHandler}, {@code IEnergyStorage}) still exist, but only as
 * deprecated adapters. Everything in Factory Network asks here, and only
 * this class knows which side of that change it lives on. Moving the mod onto
 * transactions later means changing this class and the handlers behind it,
 * not every caller.
 *
 * <p>Reading other blocks goes the old way round through NeoForge's own
 * adapters. Offering this mod's storages to others needs the new shape; the
 * adapters for that are here, because NeoForge has none in that direction.
 */
public final class Caps {

    private Caps() {
    }

    // ---- Reading other blocks ----------------------------------------------

    @SuppressWarnings("removal")
    public static @Nullable IItemHandler items(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return handler == null ? null : IItemHandler.of(handler);
    }

    @SuppressWarnings("removal")
    public static @Nullable IFluidHandler fluids(Level level, BlockPos pos, @Nullable Direction side) {
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return handler == null ? null : IFluidHandler.of(handler);
    }

    @SuppressWarnings("removal")
    public static @Nullable IEnergyStorage energy(Level level, BlockPos pos, @Nullable Direction side) {
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
        return handler == null ? null : IEnergyStorage.of(handler);
    }

    /** The battery in an item stack; changes land in the stack itself. */
    @SuppressWarnings("removal")
    public static @Nullable IEnergyStorage energy(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        EnergyHandler handler = stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack));
        return handler == null ? null : IEnergyStorage.of(handler);
    }

    // ---- Offering this mod's storages ----------------------------------------

    /**
     * An energy store of this mod, in the shape others ask for.
     *
     * <p>The store is only told on commit: until then the transaction keeps
     * the pending amount itself, and an aborted transfer leaves the store
     * untouched. Within one transaction the pending amount counts, so two
     * inserts cannot together exceed what the store takes.
     */
    @SuppressWarnings("removal")
    public static EnergyHandler offer(IEnergyStorage store) {
        return new OfferedEnergy(store);
    }

    /**
     * An inventory of this mod, in the shape others ask for.
     *
     * <p>The same rule as for energy: the inventory is only changed on commit,
     * and until then each slot keeps its pending amount. The inventory's own
     * rules (which slot takes what, how much) are asked by simulating.
     */
    @SuppressWarnings("removal")
    public static ResourceHandler<ItemResource> offer(IItemHandler store) {
        return new OfferedItems(store);
    }

    @SuppressWarnings("removal")
    private static final class OfferedItems implements ResourceHandler<ItemResource> {

        private final IItemHandler store;
        private int[] pending = new int[0];
        private ItemResource[] pendingResource = new ItemResource[0];
        private final SnapshotJournal<Object[]> journal = new SnapshotJournal<>() {
            @Override
            protected Object[] createSnapshot() {
                return new Object[] {pending.clone(), pendingResource.clone()};
            }

            @Override
            protected void revertToSnapshot(Object[] snapshot) {
                pending = (int[]) snapshot[0];
                pendingResource = (ItemResource[]) snapshot[1];
            }

            @Override
            protected void onRootCommit(Object[] originalState) {
                int[] due = pending;
                ItemResource[] what = pendingResource;
                pending = new int[due.length];
                pendingResource = new ItemResource[due.length];
                for (int slot = 0; slot < due.length; slot++) {
                    if (due[slot] > 0 && what[slot] != null) {
                        store.insertItem(slot, what[slot].toStack(due[slot]), false);
                    } else if (due[slot] < 0) {
                        store.extractItem(slot, -due[slot], false);
                    }
                }
            }
        };

        OfferedItems(IItemHandler store) {
            this.store = store;
        }

        private void fit() {
            int size = store.getSlots();
            if (pending.length != size) {
                pending = java.util.Arrays.copyOf(pending, size);
                pendingResource = java.util.Arrays.copyOf(pendingResource, size);
            }
        }

        @Override
        public int size() {
            return store.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            ItemStack stack = store.getStackInSlot(index);
            if (!stack.isEmpty()) {
                return ItemResource.of(stack);
            }
            fit();
            return pending[index] > 0 && pendingResource[index] != null
                    ? pendingResource[index] : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            fit();
            return Math.max(0, store.getStackInSlot(index).getCount() + pending[index]);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int limit = store.getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && store.isItemValid(index, resource.toStack(1));
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            fit();
            if (pending[index] > 0 && pendingResource[index] != null && !pendingResource[index].equals(resource)) {
                return 0;
            }
            int ahead = Math.max(0, pending[index]);
            ItemStack rest = store.insertItem(index, resource.toStack(ahead + amount), true);
            int taken = Math.max(0, ahead + amount - rest.getCount() - ahead);
            if (taken > 0) {
                journal.updateSnapshots(transaction);
                pending[index] += taken;
                pendingResource[index] = resource;
            }
            return taken;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !resource.matches(store.getStackInSlot(index))) {
                return 0;
            }
            fit();
            int ahead = Math.max(0, -pending[index]);
            ItemStack got = store.extractItem(index, ahead + amount, true);
            int given = Math.max(0, got.getCount() - ahead);
            if (given > 0) {
                journal.updateSnapshots(transaction);
                pending[index] -= given;
            }
            return given;
        }
    }

    @SuppressWarnings("removal")
    private static final class OfferedEnergy implements EnergyHandler {

        private final IEnergyStorage store;
        /** Inserted minus extracted, not yet handed to the store. */
        private int pending;
        private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return pending;
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                pending = snapshot;
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                int due = pending;
                pending = 0;
                if (due > 0) {
                    store.receiveEnergy(due, false);
                } else if (due < 0) {
                    store.extractEnergy(-due, false);
                }
            }
        };

        OfferedEnergy(IEnergyStorage store) {
            this.store = store;
        }

        @Override
        public long getAmountAsLong() {
            return Math.max(0L, (long) store.getEnergyStored() + pending);
        }

        @Override
        public long getCapacityAsLong() {
            return store.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || !store.canReceive()) {
                return 0;
            }
            int ahead = Math.max(0, pending);
            int taken = Math.max(0, store.receiveEnergy(ahead + amount, true) - ahead);
            if (taken > 0) {
                journal.updateSnapshots(transaction);
                pending += taken;
            }
            return taken;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            if (amount <= 0 || !store.canExtract()) {
                return 0;
            }
            int ahead = Math.max(0, -pending);
            int given = Math.max(0, store.extractEnergy(ahead + amount, true) - ahead);
            if (given > 0) {
                journal.updateSnapshots(transaction);
                pending -= given;
            }
            return given;
        }
    }
}
