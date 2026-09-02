package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.upgrade.Loadout;
import dev.devpanda.factorynetwork.upgrade.UpgradeSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What sits in the transmission mast.
 *
 * <p>Four slots, and it has no more state than that: how far it reaches is
 * computed by {@code Range} from what lies within.
 *
 * <p><b>It is a {@link Container}, but not a {@link ShelfBlockEntity}.</b> A
 * shelf holds cells and server parts and knows what a bay is; here lie four
 * equivalent slots, and what may go in is decided by {@link UpgradeSlots}.
 * The two still share the screen — it is the same operation.
 */
public class MastBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** This many slots a mast has — see fernzugriff.md §3. */
    public static final int SLOTS = 4;

    private final UpgradeSlots slots = new UpgradeSlots(SLOTS);

    public MastBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.MAST.get(), pos, state);
    }

    public UpgradeSlots slots() {
        return slots;
    }

    /** What this mast can do and how far it reaches. */
    public Loadout loadout() {
        return slots.loadout();
    }

    // ---- Container ------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!slots.get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slots.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(slots.contents(), slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(slots.contents(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        slots.set(slot, stack);
        setChanged();
    }

    /**
     * What may go in is decided by the upgrade system.
     *
     * <p>The screen asks here before it accepts a stack — otherwise the player
     * drags it in and it springs back.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slots.accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ---- Screen ---------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.factorynetwork.mast");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory,
                                                      Player player) {
        return ShelfMenu.of(id, inventory, this, ShelfMenu.MAST);
    }

    // ---- Saving ---------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        slots.save(tag, registries);
    }

    /**
     * The contents go to the client as well.
     *
     * <p>Without it the screen shows four empty slots on opening and fills
     * them only a tick later — which looks as if something had been lost.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        slots.save(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
