package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.item.ProcessorItem;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Serverschrank mit acht Plätzen für Prozessoren.
 *
 * <p>Dasselbe Bild wie beim Laufwerk: ein Regal, in das man Bauteile steckt.
 * Was der Schrank kann, steht in den Bauteilen, nicht in ihm — er allein
 * trägt nichts.
 */
public class RackBlockEntity extends BlockEntity {

    public static final int SLOTS = 8;

    private final NonNullList<ItemStack> processors =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * Zählt hoch, sobald sich die Bestückung ändert.
     *
     * <p>Wie beim Laufwerk: Der Controller rechnet nicht bei jedem Zugriff
     * nach, sondern nur, wenn sich etwas geändert hat.
     */
    private long revision;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.RACK.get(), pos, state);
    }

    public long revision() {
        return revision;
    }

    public ItemStack processor(int slot) {
        return slot >= 0 && slot < SLOTS ? processors.get(slot) : ItemStack.EMPTY;
    }

    public void setProcessor(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        processors.set(slot, stack);
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public NonNullList<ItemStack> contents() {
        return processors;
    }

    /** Wie viele gleichzeitige Abläufe dieser Schrank trägt. */
    public int threads() {
        int total = 0;
        for (ItemStack stack : processors) {
            total += ProcessorItem.threadsOf(stack) * stack.getCount();
        }
        return total;
    }

    /** Der erste freie Platz, oder -1. */
    public int firstFreeSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (processors.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Der letzte belegte Platz, oder -1. */
    public int lastUsedSlot() {
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!processors.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : processors) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        processors.clear();
        ContainerHelper.loadAllItems(tag, processors, registries);
        revision++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, processors, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, processors, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
