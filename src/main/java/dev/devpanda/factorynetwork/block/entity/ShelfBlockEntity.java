package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A shelf with slots for parts.
 *
 * <p>The drive takes cells, the server rack takes server parts — and otherwise
 * they do not differ: both are a housing whose capability lies in what you put
 * into it. <b>That is why the bookkeeping stands here a single time.</b> Two
 * versions would be two places where a part can go missing, and one of them
 * would eventually get an improvement the other lacks.
 *
 * <p>It is a {@link Container} so that a screen can look at it. The path
 * through {@link #setItem} is the only one: there the revision counts up, and
 * there the subclass learns that it must clean up.
 */
public abstract class ShelfBlockEntity extends BlockEntity
        implements Container, net.minecraft.world.MenuProvider {

    private final NonNullList<ItemStack> parts;

    /**
     * Counts up as soon as the loadout changes.
     *
     * <p>The network index holds the contents of all cells together. When it
     * changes them itself, it folds the change in; but when a cell is pulled
     * out, it must notice. That is exactly what this number is for — it is the
     * cheapest way to say "someone else did something here".
     *
     * <p><b>Not in {@code setChanged}</b>: the store calls that itself after
     * each of its own writes, and then every index would be stale again at
     * once.
     */
    private long revision;

    protected ShelfBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                               int slots) {
        super(type, pos, state);
        this.parts = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    /** What may go into this shelf. */
    public abstract boolean accepts(ItemStack stack);

    /** How this shelf's screen is laid out. */
    public abstract dev.devpanda.factorynetwork.client.menu.ShelfMenu.Layout layout();

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return dev.devpanda.factorynetwork.client.menu.ShelfMenu.of(
                id, inventory, this, layout());
    }

    /**
     * A slot has changed.
     *
     * <p>Called <b>before</b> the new item is entered — the drive writes back
     * the contents of its open cell here, and for that the old one must still
     * be there.
     */
    protected void beforeSlotChange(int slot) {
    }

    public long revision() {
        return revision;
    }

    /**
     * Counts the revision up without going through {@link #setItem}.
     *
     * <p>For subclasses that rewrite several slots in one go — the server rack
     * unpacks three slots at once when a housing is pulled out.
     */
    protected void bumpRevision() {
        revision++;
    }

    protected NonNullList<ItemStack> parts() {
        return parts;
    }

    /** The first free slot, or -1. */
    public int firstFreeSlot() {
        for (int slot = 0; slot < parts.size(); slot++) {
            if (parts.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** The last occupied slot, or -1. */
    public int lastUsedSlot() {
        for (int slot = parts.size() - 1; slot >= 0; slot--) {
            if (!parts.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : parts) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // ---- Container --------------------------------------------------------

    @Override
    public int getContainerSize() {
        return parts.size();
    }

    @Override
    public boolean isEmpty() {
        return parts.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < parts.size() ? parts.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // A part goes whole or not at all: half a stack of cells out of one
        // slot would be a slot that holds two sets of contents.
        return removeItemNoUpdate(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = getItem(slot);
        if (!taken.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= parts.size()) {
            return;
        }
        beforeSlotChange(slot);
        parts.set(slot, stack);
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < parts.size(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
    }

    // ---- Saving -------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        parts.clear();
        ContainerHelper.loadAllItems(tag, parts, registries);
        revision++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, parts, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, parts, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
