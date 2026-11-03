package dev.devpanda.factorynetwork.hall;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.registry.FnComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The door of a factory hall: it only knows the hall's number.
 *
 * <p>The number travels with the item through the {@link FnComponents#HALL}
 * component; the loot table copies it over when the block is broken. A block
 * that was never entered has no number yet: placing and breaking it costs no
 * hall.
 */
public class HallBlockEntity extends BlockEntity {

    public static final int NONE = -1;

    private int hall = NONE;

    /**
     * The hall's installation name, copied from the hall list for the
     * client: the name window shows what the hall is called. The hall list
     * stays the truth; this copy is never saved.
     */
    private String installation = "";

    public HallBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.HALL.get(), pos, state);
    }

    public int hall() {
        return hall;
    }

    void setHall(int hall) {
        this.hall = hall;
        setChanged();
    }

    /** The installation name, as far as this side knows it. */
    public String installation() {
        return installation;
    }

    /** Takes the name from the hall list and tells the client. */
    void showInstallation(String name) {
        String now = name == null ? "" : name;
        if (!now.equals(installation)) {
            installation = now;
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public HallTier tier() {
        return getBlockState().getBlock() instanceof HallBlock block ? block.tier() : HallTier.SMALL;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        HallLoading.hold(this, true);
        if (level instanceof net.minecraft.server.level.ServerLevel server && hall != NONE) {
            Halls.Entry entry = Halls.of(server.getServer()).get(hall);
            showInstallation(entry == null ? "" : entry.name().orElse(""));
        }
    }

    /** Only the block going for good stops its hall, not its chunk unloading. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        HallLoading.release(this);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void setRemoved() {
        if (!dev.devpanda.factorynetwork.FnConfig.hallsLoadThemselves()) {
            HallLoading.hold(this, false);
        }
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hall = input.getIntOr("Hall", NONE);
        installation = input.getStringOr("Installation", "");
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        if (hall != NONE) {
            tag.putInt("Hall", hall);
        }
        tag.putString("Installation", installation);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (hall != NONE) {
            output.putInt("Hall", hall);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        hall = components.getOrDefault(FnComponents.HALL.get(), NONE);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (hall != NONE) {
            components.set(FnComponents.HALL.get(), hall);
        }
    }

    /** The number lives in the component on the item; the block's own save keeps its copy. */
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Hall");
    }
}
