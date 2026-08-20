package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Ein Kabelblock hält bis zu vier Stränge verschiedener Farbe.
 *
 * <p>Das ist die Antwort auf zwei Wünsche in einem: Farben trennen Netze, und
 * mehrere Netze sollen durch dieselbe Wand passen, ohne vier Blöcke breit zu
 * werden. Vorbild sind die Conduits aus EnderIO.
 *
 * <p><b>Ein einzelnes Kabel ist ein Bündel mit einem Strang.</b> Es gibt
 * bewusst keinen zweiten Blocktyp — sonst müsste jede Stelle, die Kabel
 * anfasst, für immer zwei Fälle behandeln: der Graph, das Platzieren, das
 * Abbauen, die Tests.
 */
public class CableBlockEntity extends BlockEntity {

    /** Mehr passen nicht in einen Block, ohne dass man sie verwechselt. */
    public static final int MAX_STRANDS = 4;

    private static final String KEY_STRANDS = "Strands";

    private final Set<CableColour> strands = EnumSet.noneOf(CableColour.class);

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CABLE.get(), pos, state);
        // Aus einer Welt, die vor den Bündeln gebaut wurde, kommt die Farbe
        // aus dem Blockzustand. Ohne das verlöre jedes vorhandene Kabel beim
        // Laden seine Farbe.
        strands.add(CableBlock.colourOf(state));
    }

    public Set<CableColour> strands() {
        return Set.copyOf(strands);
    }

    public boolean has(CableColour colour) {
        return strands.contains(colour);
    }

    public int count() {
        return strands.size();
    }

    public boolean isFull() {
        return strands.size() >= MAX_STRANDS;
    }

    /** Nimmt einen Strang auf. Liefert falsch, wenn kein Platz ist. */
    public boolean addStrand(CableColour colour) {
        if (strands.contains(colour) || isFull()) {
            return false;
        }
        strands.add(colour);
        changed();
        return true;
    }

    /** Entfernt einen Strang. Liefert falsch, wenn er nicht da war. */
    public boolean removeStrand(CableColour colour) {
        if (!strands.remove(colour)) {
            return false;
        }
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- Speichern und Übertragen -----------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(KEY_STRANDS)) {
            // Alter Stand ohne Stränge: Die Farbe steht dann noch im
            // Blockzustand, und die hat der Konstruktor schon übernommen.
            return;
        }
        strands.clear();
        ListTag list = tag.getList(KEY_STRANDS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String name = list.getString(i);
            for (CableColour colour : CableColour.values()) {
                if (colour.getSerializedName().equals(name)) {
                    strands.add(colour);
                    break;
                }
            }
        }
        if (strands.isEmpty()) {
            strands.add(CableColour.NONE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        strands.forEach(colour -> list.add(StringTag.valueOf(colour.getSerializedName())));
        tag.put(KEY_STRANDS, list);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    /** Der Client braucht die Stränge zum Zeichnen — also gehen sie mit. */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Die Reihenfolge, in der Stränge im Block liegen — stabil sortiert. */
    public List<CableColour> ordered() {
        List<CableColour> list = new ArrayList<>(strands);
        list.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        return list;
    }
}
