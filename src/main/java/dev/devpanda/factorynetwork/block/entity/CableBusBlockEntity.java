package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ein Kabelblock, der an seinen Flächen Anschlüsse trägt.
 *
 * <p><b>Das ist AE2s Modell:</b> ein Block, in der Mitte die Leitung, an jeder
 * der sechs Flächen ein Teil. Bisher stand für jede Maschine ein eigener
 * Connectorblock neben dem Kabel — eine Maschinenwand kostete sechs Blöcke,
 * wo einer reicht.
 *
 * <p>Was ein Anschluss <b>ist</b>, steht in {@link ConnectorPart}; hier steht
 * nur, dass es bis zu sechs davon gibt und an welcher Fläche jeder sitzt. Die
 * Trennung stammt aus dem Schnitt davor und ist der Grund, warum dieser hier
 * überhaupt klein sein kann.
 *
 * <p><b>Jeder Kabelblock hat eine.</b> Das ist die teure Entscheidung dieses
 * Schnitts, und sie ist bewusst so gefallen: Eine BlockEntity nur dann
 * anzulegen, wenn ein Teil dazukommt, verlangt einen Zustand im BlockState,
 * der sich beim Setzen und Abbauen ändert — und damit eine zweite Wahrheit
 * darüber, ob hier Teile sitzen. AE2 legt sie ebenfalls überall an. Was das
 * bei zehntausend Kabeln kostet, ist ungemessen und steht als offener Punkt.
 */
public class CableBusBlockEntity extends BlockEntity {

    private static final String KEY_PARTS = "Parts";
    private static final String KEY_SIDE = "Side";

    private final Map<Direction, ConnectorPart> parts = new EnumMap<>(Direction.class);

    public CableBusBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CABLE_BUS.get(), pos, state);
    }

    /** Der Anschluss an dieser Fläche, oder {@code null}. */
    public @Nullable ConnectorPart partAt(Direction side) {
        return parts.get(side);
    }

    /** Alle Anschlüsse, die dieser Block trägt. */
    public Map<Direction, ConnectorPart> parts() {
        return parts;
    }

    public boolean hasParts() {
        return !parts.isEmpty();
    }

    /**
     * Setzt einen Anschluss an eine Fläche.
     *
     * @return das Teil, oder das schon vorhandene — an eine Fläche gehört eins
     */
    public ConnectorPart addPart(Direction side) {
        ConnectorPart taken = parts.get(side);
        if (taken != null) {
            return taken;
        }
        ConnectorPart part = new ConnectorPart(new SideHost(side));
        parts.put(side, part);
        changed();
        return part;
    }

    /** Nimmt den Anschluss an dieser Fläche weg. */
    public @Nullable ConnectorPart removePart(Direction side) {
        ConnectorPart gone = parts.remove(side);
        if (gone != null) {
            changed();
        }
        return gone;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Was ein Teil von diesem Block weiß.
     *
     * <p>Je Fläche eine eigene Sicht: Der Ort ist derselbe, die Blickrichtung
     * nicht. Genau darin unterscheidet sich ein Kabelblock mit sechs
     * Anschlüssen von einem Connectorblock mit einem — und genau deshalb
     * konnte die Blickrichtung im Schnitt davor aus dem BlockState wandern.
     */
    private final class SideHost implements ConnectorPart.Host {

        private final Direction side;

        private SideHost(Direction side) {
            this.side = side;
        }

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
            return side;
        }

        @Override
        public void partChanged() {
            changed();
        }

        @Override
        public void redstoneChanged() {
            setChanged();
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    // ---- Speichern ---------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        parts.clear();
        net.minecraft.nbt.ListTag saved =
                tag.getList(KEY_PARTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            Direction side = Direction.from3DDataValue(entry.getInt(KEY_SIDE));
            ConnectorPart part = new ConnectorPart(new SideHost(side));
            part.load(entry);
            parts.put(side, part);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.nbt.ListTag saved = new net.minecraft.nbt.ListTag();
        parts.forEach((side, part) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SIDE, side.get3DDataValue());
            part.save(entry);
            saved.add(entry);
        });
        tag.put(KEY_PARTS, saved);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    /**
     * Ohne dieses Paket erfährt der Client von einem Anschluss nie.
     *
     * <p>Derselbe Fehler wie beim Connectorblock, der beim ersten Spielen
     * auffiel: {@code sendBlockUpdated} schickt genau das, was hier
     * zurückkommt, und die Vorgabe ist {@code null}.
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
