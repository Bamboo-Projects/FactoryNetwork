package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.platform.Nbt;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The name of the installation that hangs behind this gateway.
 *
 * <p>It holds no more than that: no items, no power, no channel. It is a piece
 * of cable with a name tag.
 */
public class GatewayBlockEntity extends BlockEntity {

    private static final String KEY_NAME = "Instance";

    private String instance = "";

    public GatewayBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.GATEWAY.get(), pos, state);
    }

    public String instance() {
        return instance;
    }

    /**
     * Sets the installation name.
     *
     * <p>With the same message to the client as for the connector: without
     * {@link #getUpdatePacket()} the screen would stand empty in front of a
     * gateway that has long carried a name — exactly the bug noticed at the
     * connector on first play.
     */
    public void setInstance(String name) {
        this.instance = name == null ? "" : name.trim();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadFrom(dev.devpanda.factorynetwork.platform.Nbt.whole(input),
                dev.devpanda.factorynetwork.platform.Nbt.registries(input));
    }

    /** What used to be the tag-based loading; the keys are unchanged. */
    private void loadFrom(CompoundTag tag, HolderLookup.Provider registries) {
        instance = Nbt.getString(tag, KEY_NAME);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTo(tag, dev.devpanda.factorynetwork.platform.Nbt.registries(level));
        output.store(tag);
    }

    /** What used to be the tag-based saving; the keys are unchanged. */
    private void saveTo(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(KEY_NAME, instance);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(KEY_NAME, instance);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
