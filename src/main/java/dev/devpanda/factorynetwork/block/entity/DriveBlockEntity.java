package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Laufwerk mit zehn Plätzen.
 *
 * <p>Zehn wie bei Applied Energistics: genug, dass ein Laufwerk lohnt, wenig
 * genug, dass eine große Basis mehrere braucht.
 *
 * <p>Die Zellen liegen hier, ihr Inhalt aber steckt in ihnen selbst. Das
 * Laufwerk ist ein Regal, kein Speicher.
 */
public class DriveBlockEntity extends BlockEntity {

    public static final int SLOTS = 10;

    private final NonNullList<ItemStack> cells =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.DRIVE.get(), pos, state);
    }

    public ItemStack cell(int slot) {
        return slot >= 0 && slot < SLOTS ? cells.get(slot) : ItemStack.EMPTY;
    }

    public void setCell(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        cells.set(slot, stack);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public NonNullList<ItemStack> cells() {
        return cells;
    }

    /**
     * Die eingesetzten Zellen als Speicher.
     *
     * <p>Frisch gelesen bei jedem Aufruf: Der Inhalt steht im Gegenstand, und
     * ein zwischengespeicherter Blick darauf liefe der Wahrheit hinterher,
     * sobald jemand eine Zelle tauscht.
     */
    public List<CellInventory> inventories() {
        List<CellInventory> found = new ArrayList<>();
        for (ItemStack stack : cells) {
            if (stack.getItem() instanceof StorageCellItem) {
                CellInventory inventory = new CellInventory(stack);
                if (inventory.isValid()) {
                    found.add(inventory);
                }
            }
        }
        return found;
    }

    public boolean isEmpty() {
        return cells.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cells.clear();
        ContainerHelper.loadAllItems(tag, cells, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, cells, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, cells, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
