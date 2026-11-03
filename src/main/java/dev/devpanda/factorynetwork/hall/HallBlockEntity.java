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

    public HallTier tier() {
        return getBlockState().getBlock() instanceof HallBlock block ? block.tier() : HallTier.SMALL;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        HallLoading.hold(this, true);
        HallLoading.holdDoorChunk(this, true);
    }

    @Override
    public void setRemoved() {
        HallLoading.hold(this, false);
        HallLoading.holdDoorChunk(this, false);
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hall = input.getIntOr("Hall", NONE);
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
