package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A junction for dense cables.
 *
 * <p>With the thin cable the colour keeps things apart, and four thin runs
 * fit side by side in one block. With the dense one that does not fit — ten
 * block pixels almost fill the block. Instead of bundling, a block stands
 * here on which each side is assigned to a lane: <b>the same lane means
 * connected, different lanes cross without touching.</b>
 *
 * <p>Clicking advances the clicked side by one lane. A side set to "off" is
 * disconnected — this is how you split a network without tearing the cable
 * down.
 *
 * <p><b>The router is colour-neutral.</b> Anyone who puts a red and a green
 * cable on the same lane has connected them — that is intentional and the
 * difference from two cables that merely share a block: here someone set it
 * up.
 */
public class RouterBlock extends Block implements EntityBlock {

    /** The outline from the boxes of the model. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            FacingShapes.whole(MachineLayouts.router());

    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    public RouterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    /**
     * A click advances the clicked side.
     *
     * <p>Afterwards the network is rebuilt at once, instead of waiting for the
     * next cycle. Five seconds between click and effect are too long to still
     * be recognised as the cause — in that time the player clicks on three
     * more times and in the end does not know what currently holds.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            // Sneaking opens the screen: a side set against the wall cannot
            // be reached, and it also shows what each lane carries.
            player.openMenu(router.menu());
            return InteractionResult.CONSUME;
        }
        Direction side = hit.getDirection();
        int lane = router.cycle(side);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.3F, lane == RouterBlockEntity.OFF ? 0.5F : 0.6F + lane * 0.15F);
        player.displayClientMessage(lane == RouterBlockEntity.OFF
                ? net.minecraft.network.chat.Component.translatable(
                        "message.factorynetwork.router.off", sideName(side))
                : net.minecraft.network.chat.Component.translatable(
                        "message.factorynetwork.router.lane", sideName(side), lane), true);
        ControllerRegistry.refreshAround(level, pos);
        return InteractionResult.CONSUME;
    }

    private static net.minecraft.network.chat.Component sideName(Direction side) {
        return net.minecraft.network.chat.Component.translatable(
                "side.factorynetwork." + side.getSerializedName());
    }

    /**
     * A router that is torn down takes the network that ran through it with
     * it.
     *
     * <p>Without this the graph would keep its old answer until the next
     * cycle, and the network analyzer would draw routes through a block that
     * no longer exists.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) {
            ControllerRegistry.refreshAround(level, pos);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            ControllerRegistry.refreshAround(level, pos);
        }
    }
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
