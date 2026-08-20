package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Merkt sich den Namen, den die Label-Gun vergeben hat, und findet das
 * Inventar der Maschine dahinter.
 */
public class ConnectorBlockEntity extends BlockEntity {

    private static final String KEY_LABEL = "Label";
    private static final String KEY_COST = "ChannelCost";

    private String label = "";

    /**
     * Wie viele Kanäle dieses Gerät braucht.
     *
     * <p>Heute immer einer. Als Feld statt als feste Eins, damit ein Gerät
     * mit höherem Bedarf später keine Wanderung durch den Pfadcode nach sich
     * zieht — das kostet jetzt nichts und spart sie dann.
     */
    private int channelCost = 1;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONNECTOR.get(), pos, state);
    }

    public String label() {
        return label;
    }

    public int channelCost() {
        return channelCost;
    }

    public void setChannelCost(int cost) {
        this.channelCost = Math.max(1, cost);
        setChanged();
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label.trim();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Das Inventar der Maschine, auf die der Connector zeigt. */
    public @Nullable IItemHandler machineInventory() {
        if (level == null) {
            return null;
        }
        Direction facing = ConnectorBlock.machineSide(getBlockState());
        BlockPos target = worldPosition.relative(facing);
        if (!level.isLoaded(target)) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing.getOpposite());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        label = tag.getString(KEY_LABEL);
        channelCost = Math.max(1, tag.getInt(KEY_COST));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_LABEL, label);
        tag.putInt(KEY_COST, channelCost);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(KEY_LABEL, label);
        return tag;
    }
}
