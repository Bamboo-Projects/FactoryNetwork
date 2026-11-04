package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.platform.Nbt;
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
 * A cable block that carries connectors on its faces.
 *
 * <p><b>This is AE2's model:</b> one block, the cable in the middle, a part on
 * each of the six faces. Until now every machine had its own connector block
 * next to the cable — a wall of machines cost six blocks where one is enough.
 *
 * <p>What a connector <b>is</b> lives in {@link ConnectorPart}; here we only
 * say that there are up to six of them and which face each one sits on. The
 * split comes from the previous cut and is the reason this class can be small
 * at all.
 *
 * <p><b>Every cable block has one.</b> That is the expensive decision of this
 * cut, and it was made deliberately: creating a BlockEntity only when a part
 * is added would demand a value in the BlockState that changes on placement
 * and removal — and with it a second source of truth about whether parts sit
 * here. AE2 also creates one everywhere. What that costs at ten thousand
 * cables is unmeasured and stands as an open question.
 */
public class CableBusBlockEntity extends BlockEntity {

    private static final String KEY_PARTS = "Parts";
    private static final String KEY_SIDE = "Side";

    private final Map<Direction, ConnectorPart> parts = new EnumMap<>(Direction.class);

    public CableBusBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.CABLE_BUS.get(), pos, state);
    }

    /** The connector on this face, or {@code null}. */
    public @Nullable ConnectorPart partAt(Direction side) {
        return parts.get(side);
    }

    /** All connectors this block carries. */
    public Map<Direction, ConnectorPart> parts() {
        return parts;
    }

    public boolean hasParts() {
        return !parts.isEmpty();
    }

    /**
     * Places a connector on a face.
     *
     * @return the part, or the one already there — a face holds exactly one
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

    /** Removes the connector on this face. */
    public @Nullable ConnectorPart removePart(Direction side) {
        ConnectorPart gone = parts.remove(side);
        if (gone != null) {
            // Its upgrades come out with it — enchanted, they are worth more
            // than the connector.
            if (level != null && !level.isClientSide()) {
                gone.takeArms().forEach(stack ->
                        net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack));
            }
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
     * What a part knows about this block.
     *
     * <p>A separate view per face: the position is the same, the facing is
     * not. That is exactly what sets a cable block with six connectors apart
     * from a connector block with one — and exactly why, in the previous cut,
     * the facing could move out of the BlockState.
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

    // ---- Saving -------------------------------------------------------------

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadFrom(dev.devpanda.factorynetwork.platform.Nbt.whole(input),
                dev.devpanda.factorynetwork.platform.Nbt.registries(input));
    }

    /** What used to be the tag-based loading; the keys are unchanged. */
    private void loadFrom(CompoundTag tag, HolderLookup.Provider registries) {
        parts.clear();
        net.minecraft.nbt.ListTag saved =
                Nbt.getList(tag, KEY_PARTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = Nbt.compoundAt(saved, i);
            Direction side = Direction.from3DDataValue(Nbt.getInt(entry, KEY_SIDE));
            ConnectorPart part = new ConnectorPart(new SideHost(side));
            part.load(entry, registries);
            parts.put(side, part);
        }
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
        net.minecraft.nbt.ListTag saved = new net.minecraft.nbt.ListTag();
        parts.forEach((side, part) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SIDE, side.get3DDataValue());
            part.save(entry, registries);
            saved.add(entry);
        });
        tag.put(KEY_PARTS, saved);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveTo(tag, registries);
        return tag;
    }

    /**
     * Without this packet the client never learns of a connector.
     *
     * <p>The same bug as with the connector block, noticed on first play:
     * {@code sendBlockUpdated} sends exactly what comes back here, and the
     * default is {@code null}.
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Drops what the block holds when it is broken.
     *
     * <p>Until 1.21.1 this stood in the block's {@code onRemove}; since 1.21.5
     * the block entity does it, right before it is removed.
     */
    @Override
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos,
                                     net.minecraft.world.level.block.state.BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (ConnectorPart part : parts().values()) {
            net.minecraft.world.level.block.Block.popResource(level, pos,
                    new net.minecraft.world.item.ItemStack(
                            dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()));
            part.takeArms().forEach(stack ->
                    net.minecraft.world.level.block.Block.popResource(level, pos, stack));
        }
    }
}
