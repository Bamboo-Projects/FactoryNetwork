package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.network.InternalBuffer;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The burner: power from furnace fuel.
 *
 * <p><b>Deliberately mediocre.</b> It has no tiers and no upgrade path, and in
 * a pack you set something better beside it after the first hour. It is not
 * there to compete with other mods' generators, but so that you are not
 * blocked without it — the same picture as the vibration chamber in Applied
 * Energistics.
 *
 * <p>The reason is more concrete than the network's power: the press needs FE,
 * and without a source in the mod itself the whole crafting chain — ore,
 * plate, cores, cells, server parts — could not be run through without a
 * third-party mod.
 */
public class BurnerBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** How much it produces while it burns. */
    public static final int PER_TICK = 40;

    /** How much it buffers until a neighbour takes it. */
    public static final int CAPACITY = 20_000;

    public static final int SLOT_FUEL = 0;
    public static final int SLOTS = 1;

    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    private static final int DATA_SIZE = 4;

    private static final String KEY_BURN = "Burn";
    private static final String KEY_BURN_TOTAL = "BurnTotal";
    private static final String KEY_ENERGY = "Energy";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Hands nothing out on request; it pushes on its own instead.
     *
     * <p>The controller accepts but does not pull. A source that only makes
     * itself available would never reach it.
     */
    private final InternalBuffer energy = new InternalBuffer(CAPACITY, CAPACITY);

    private int burnTicks;
    private int burnTotal;

    public BurnerBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.BURNER.get(), pos, state);
    }

    public InternalBuffer energy() {
        return energy;
    }

    public boolean isBurning() {
        return burnTicks > 0;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    /**
     * One tick: burn, produce, pass on.
     *
     * <p>Fresh fuel is only lit when the buffer is not full. Otherwise a piece
     * of coal burns while no one is drawing — and you notice only once the
     * stack of coal is gone.
     */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
                                  BlockState state, BurnerBlockEntity burner) {
        boolean brannte = burner.burnTicks > 0;
        if (burner.burnTicks > 0) {
            burner.burnTicks--;
            burner.energy.charge(PER_TICK);
        } else if (burner.energy.getEnergyStored() < CAPACITY) {
            burner.light();
        }
        burner.pushToNeighbours(level, pos);
        if (brannte != burner.burnTicks > 0) {
            // The front shows the fire, and the block glows with it. Both
            // hang on the state, not on the BlockEntity: it is exactly one
            // boolean.
            level.setBlock(pos, state.setValue(
                    dev.devpanda.factorynetwork.block.BurnerBlock.LIT,
                    burner.burnTicks > 0), 3);
            burner.setChanged();
        }
    }

    /** Lights the next piece of fuel, if there is one. */
    private void light() {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) {
            return;
        }
        int burn = fuel.getBurnTime(null);
        if (burn <= 0) {
            return;
        }
        burnTicks = burn;
        burnTotal = burn;
        // A lava bucket leaves its bucket behind, just as in a furnace.
        ItemStack rest = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !rest.isEmpty()) {
            items.set(SLOT_FUEL, rest);
        }
        setChanged();
    }

    /**
     * Pushes into every neighbour that accepts.
     *
     * <p>In turn, in a fixed order of directions: whoever connects two
     * consumers should be able to explain which one gets served first.
     */
    private void pushToNeighbours(net.minecraft.world.level.Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(side), side.getOpposite());
            if (sink == null || !sink.canReceive()) {
                continue;
            }
            int taken = sink.receiveEnergy(energy.getEnergyStored(), false);
            if (taken > 0) {
                energy.consume(taken);
                setChanged();
            }
        }
    }

    /** The numbers for the screen. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN -> burnTicks;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    // ---- Container --------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_FUEL).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS) {
            items.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getBurnTime(null) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    // ---- Screen -----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new dev.devpanda.factorynetwork.client.menu.BurnerMenu(
                id, inventory, this, data);
    }

    // ---- Saving -----------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTicks = tag.getInt(KEY_BURN);
        burnTotal = Math.max(1, tag.getInt(KEY_BURN_TOTAL));
        if (tag.contains(KEY_ENERGY)) {
            energy.deserializeNBT(registries, tag.get(KEY_ENERGY));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(KEY_BURN, burnTicks);
        tag.putInt(KEY_BURN_TOTAL, burnTotal);
        tag.put(KEY_ENERGY, energy.serializeNBT(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(KEY_BURN, burnTicks);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
