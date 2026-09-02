package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The rack: without it the network does not compute.
 *
 * <p>Just as a drive is the prerequisite for the network to store, the rack
 * is the prerequisite for it to compute. Every ability of the network hangs
 * on a block you have to build.
 *
 * <p><b>Two blocks tall, one device.</b> A rack with twelve bays that is one
 * cube in size looks like a box and not like a rack — and twelve tiles on one
 * face of a cube are smaller than the crosshair. The lower half carries
 * everything: the BlockEntity, the contents, the place in the network. The
 * upper half is sheet metal that comes along.
 */
public class RackBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);

    /** Lower or upper half — as with door and bed. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<
            DoubleBlockHalf> HALF =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** The half everything hangs on. */
    public static BlockPos baseOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /**
     * Only if there is room above.
     *
     * <p>{@code null} means "do not place" — better than half a rack that you
     * afterwards can no longer tell apart from a whole one.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(),
                state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** The upper half stands only as long as the lower one stands beneath it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * If one half falls, the other goes with it.
     *
     * <p>The same pattern as with door and tall flower. Without it a floating
     * sheet-metal hood would be left standing after an explosion, able to do
     * nothing.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardsOtherHalf) {
            return neighbour.is(this) && neighbour.getValue(HALF) != half
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Striking the top still breaks the whole rack.
     *
     * <p>The item and the contents hang on the lower half — the loot table
     * only yields something there, so that an explosion does not turn one rack
     * into two. So it is broken first and in the regular way; the upper half
     * is then cleared away by {@link #updateShape} on its own.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.destroyBlock(below, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Only the lower half has a BlockEntity — it is one device. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new RackBlockEntity(pos, state)
                : null;
    }

    /**
     * A click opens the window — top or bottom.
     *
     * <p>Whoever clicks the rack wants to open it; whether they hit the upper
     * or the lower half is a matter of body height and not a decision.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(baseOf(state, pos)) instanceof ShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        player.openMenu(shelf, buffer -> buffer.writeByte(
                dev.devpanda.factorynetwork.client.menu.ShelfMenu
                        .kindOf(shelf.layout())));
        return InteractionResult.CONSUME;
    }

    /** When broken, the servers fall out. The loot table does not see them. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            // Pack up first: what falls out should be finished servers and
            // not forty-eight individual parts that you have to sort again.
            rack.packAll();
            for (ItemStack stack : rack.contents()) {
                if (!stack.isEmpty()) {
                    popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
