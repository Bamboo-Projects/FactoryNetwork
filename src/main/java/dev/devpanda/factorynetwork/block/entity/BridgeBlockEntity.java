package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.item.EntanglementItem;
import dev.devpanda.factorynetwork.network.BridgeRegistry;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A quantum bridge: one end of a connection with no cable in between.
 *
 * <p>It holds one half of an entanglement. The other half sits in a second
 * bridge, somewhere in the same world — and the two join their networks as if
 * a solid cable ran between them.
 *
 * <p><b>What it does not do: search.</b> On load it registers itself in
 * {@link BridgeRegistry} under the id of its half. A bridge that had to search
 * the world for its partner would scan millions of blocks on every query.
 */
public class BridgeBlockEntity extends BlockEntity implements Container {

    /** One slot: a bridge holds exactly one half. */
    public static final int SLOTS = 1;

    private final NonNullList<ItemStack> contents =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * The id under which this bridge is registered.
     *
     * <p>Remembered rather than read back from the slot: by the time it
     * unregisters, the half may already be gone, and then the unregistration
     * would no longer find its own entry — the bridge would linger as a ghost
     * in the registry.
     */
    private @Nullable UUID registered;

    public BridgeBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.BRIDGE.get(), pos, state);
    }

    /** The id of the half held here — or {@code null}. */
    public @Nullable UUID pair() {
        return EntanglementItem.idOf(contents.get(0));
    }

    /** The counterpart, or {@code null}: see {@link BridgeRegistry}. */
    public @Nullable BlockPos partner() {
        return level == null ? null : BridgeRegistry.partnerOf(level, worldPosition);
    }

    /**
     * Updates the registration when the half has changed.
     *
     * <p>Unregister first, then register, and both only on a real change: a
     * registry that gets rewritten on every {@code setChanged} is a registry
     * you touch on every tick.
     */
    private void syncRegistration() {
        if (level == null || level.isClientSide) {
            return;
        }
        UUID now = pair();
        if (java.util.Objects.equals(registered, now)) {
            return;
        }
        if (registered != null) {
            BridgeRegistry.remove(level, registered, worldPosition);
        }
        registered = now;
        if (now != null) {
            BridgeRegistry.add(level, now, worldPosition);
        }
        // The network looks different the moment a bridge opens or closes.
        ControllerRegistry.refreshAround(level, worldPosition);
        showLink();
    }

    /**
     * Writes the state into the block's appearance.
     *
     * <p>At the counterpart too: otherwise it would never learn that it has
     * gained a partner — nothing else touches it.
     */
    private void showLink() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos partner = partner();
        setLinked(worldPosition, partner != null);
        if (partner != null && level.isLoaded(partner)) {
            setLinked(partner, true);
        }
    }

    private void setLinked(BlockPos pos, boolean linked) {
        if (level == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock
                && state.getValue(dev.devpanda.factorynetwork.block.BridgeBlock.LINKED)
                        != linked) {
            level.setBlock(pos, state.setValue(
                    dev.devpanda.factorynetwork.block.BridgeBlock.LINKED, linked),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncRegistration();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && registered != null) {
            // Remember the counterpart before unregistration makes it
            // unfindable: it is not touched when this one is broken, and would
            // otherwise never learn that it is alone — a light with nothing
            // behind it any more.
            BlockPos partner = partner();
            BridgeRegistry.remove(level, registered, worldPosition);
            registered = null;
            if (partner != null && level.isLoaded(partner)) {
                setLinked(partner, false);
            }
            ControllerRegistry.refreshAround(level, worldPosition);
        }
        super.setRemoved();
    }

    // ---- Container ------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return contents.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return contents.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(contents, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = ContainerHelper.takeItem(contents, slot);
        setChanged();
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        contents.set(slot, stack);
        setChanged();
    }

    /** Only an entanglement fits inside — a slot for anything would be a chest. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty() || EntanglementItem.idOf(stack) != null;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        contents.clear();
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        syncRegistration();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, contents, registries);
    }
}
