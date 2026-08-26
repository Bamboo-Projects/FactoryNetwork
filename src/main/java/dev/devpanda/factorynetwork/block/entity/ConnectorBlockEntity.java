package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Ein Block, der genau einen Anschluss trägt.
 *
 * <p><b>Was ein Connector ist, steht in {@link ConnectorPart}</b> — Name,
 * Kanalbedarf, Redstone und der Griff auf die Maschine dahinter. Hier steht
 * nur, was ein <i>Block</i> ist: speichern, laden, dem Client Bescheid geben.
 *
 * <p>Die Trennung ist der erste Schritt zum Kabelblock mit sechs Anschlüssen
 * ({@code connector-im-kabel.md}, Weg B). Solange sie hier steht, ändert sie
 * nichts: Dieser Block hält ein Teil, es zeigt dorthin, wohin sein
 * {@code FACING} zeigt, und jede Frage von außen wird durchgereicht.
 */
public class ConnectorBlockEntity extends BlockEntity implements ConnectorPart.Host {

    private final ConnectorPart part = new ConnectorPart(this);

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CONNECTOR.get(), pos, state);
    }

    /** Der Anschluss, den dieser Block trägt. */
    public ConnectorPart part() {
        return part;
    }

    // ---- Was der Anschluss von seinem Block braucht ------------------------

    @Override
    public @Nullable Level level() {
        return level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public Direction facing() {
        return ConnectorBlock.machineSide(getBlockState());
    }

    @Override
    public void partChanged() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void redstoneChanged() {
        setChanged();
        if (level != null) {
            // Nachbarn anstoßen, sonst merkt niemand die Änderung.
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- Durchgereicht -----------------------------------------------------

    public String label() {
        return part.label();
    }

    public void setLabel(String label) {
        part.setLabel(label);
    }

    public int channelCost() {
        return part.channelCost();
    }

    public void setChannelCost(int cost) {
        part.setChannelCost(cost);
    }

    public int emittedRedstone() {
        return part.emittedRedstone();
    }

    public void setEmittedRedstone(int strength) {
        part.setEmittedRedstone(strength);
    }

    public @Nullable IItemHandler machineInventoryAll() {
        return part.machineInventoryAll();
    }

    public @Nullable IItemHandler machineInventory() {
        return part.machineInventory();
    }

    public @Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler machineTank() {
        return part.machineTank();
    }

    public @Nullable net.neoforged.neoforge.energy.IEnergyStorage machineEnergy() {
        return part.machineEnergy();
    }

    public @Nullable BlockEntity machineBlockEntity() {
        return part.machineBlockEntity();
    }

    // ---- Speichern ---------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        part.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        part.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString(ConnectorPart.KEY_LABEL, part.label());
        return tag;
    }

    /**
     * Das Paket, mit dem eine Änderung beim Client ankommt.
     *
     * <p><b>Ohne diese Zeilen erfährt der Client den Namen nie.</b>
     * {@code setLabel} ruft {@code sendBlockUpdated} — aber das schickt
     * genau das, was hier zurückkommt, und die Vorgabe von
     * {@link BlockEntity} ist {@code null}. Der Name stand damit nur im
     * {@code getUpdateTag}, und das wird allein beim Laden des Klotzes
     * gelesen: Wer benannte und dann das Fenster öffnete, sah ein leeres
     * Feld vor einem Gerät, das längst einen Namen hatte.
     *
     * <p>Beim Display steht dieselbe Methode seit jeher. Der Connector war
     * die Kopie, die niemand nachgezogen hat.
     */
    @Override
    public @Nullable net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
                .create(this);
    }
}
