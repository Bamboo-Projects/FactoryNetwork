package dev.devpanda.factorynetwork.block.entity;

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
 * Der Name der Anlage, die hinter diesem Gateway hängt.
 *
 * <p>Mehr hält er nicht: keine Gegenstände, keinen Strom, keinen Kanal. Er
 * ist ein Kabelstück mit einem Namensschild.
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
     * Setzt den Anlagennamen.
     *
     * <p>Mit derselben Meldung zum Client wie beim Connector: Ohne
     * {@link #getUpdatePacket()} stünde das Fenster leer vor einem Gateway,
     * das längst einen Namen trägt — genau der Fehler, der beim Connector
     * beim ersten Spielen auffiel.
     */
    public void setInstance(String name) {
        this.instance = name == null ? "" : name.trim();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        instance = tag.getString(KEY_NAME);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
