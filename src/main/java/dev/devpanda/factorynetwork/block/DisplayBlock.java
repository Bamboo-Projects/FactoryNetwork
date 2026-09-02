package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Shows what is going on in the network.
 *
 * <p>Flat against the wall, like a picture frame — a display is not a
 * machine but a readout. What it shows lives in the program; which display
 * is meant is decided by its name.
 */
public class DisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<DisplayBlock> CODEC = simpleCodec(DisplayBlock::new);

    /** Two pixels deep: it hangs on the wall, it does not stand in front of it. */
    private static final VoxelShape NORTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WEST = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 2, 16, 16);

    /**
     * On which sides a second panel adjoins.
     *
     * <p>Left and right <b>as seen from the front</b>, not in world
     * directions: the frame falls away where a neighbouring panel lies in the
     * picture, and in the picture there is no north.
     *
     * <p>Four booleans times four directions are sixty-four block states.
     * That is a lot for a frame — and still the right way: what you see has
     * to be in the block state, otherwise the renderer cannot take it from
     * the model, and then it draws every frame itself.
     */
    public static final BooleanProperty JOINED_UP = BooleanProperty.create("joined_up");
    public static final BooleanProperty JOINED_DOWN = BooleanProperty.create("joined_down");
    public static final BooleanProperty JOINED_LEFT = BooleanProperty.create("joined_left");
    public static final BooleanProperty JOINED_RIGHT = BooleanProperty.create("joined_right");

    public DisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(JOINED_UP, false)
                .setValue(JOINED_DOWN, false)
                .setValue(JOINED_LEFT, false)
                .setValue(JOINED_RIGHT, false));
    }

    /**
     * Which way "right" points, as seen from the front.
     *
     * <p>For a panel facing north that is west — someone looking at it from
     * the north has west on their right. The same rule lives in
     * {@code FaceOverlay} and in {@code DisplayWall}; it has to be the same
     * everywhere, otherwise the frame sits on the wrong side.
     */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Is there a panel there facing the same direction? */
    private boolean joins(net.minecraft.world.level.LevelReader level, BlockPos pos,
                          Direction facing) {
        BlockState neighbour = level.getBlockState(pos);
        return neighbour.getBlock() == this && neighbour.getValue(FACING) == facing;
    }

    /** Records the four adjacencies into a state. */
    private BlockState withJoins(BlockState state, net.minecraft.world.level.LevelReader level,
                                 BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction right = rightOf(facing);
        return state
                .setValue(JOINED_UP, joins(level, pos.above(), facing))
                .setValue(JOINED_DOWN, joins(level, pos.below(), facing))
                .setValue(JOINED_RIGHT, joins(level, pos.relative(right), facing))
                .setValue(JOINED_LEFT, joins(level, pos.relative(right.getOpposite()), facing));
    }

    /**
     * A panel that was not placed by hand looks around too.
     *
     * <p>{@code getStateForPlacement} only fires when placing from the hand.
     * A piston, a structure or a {@code /setblock} drop the panel with no
     * neighbourhood — and while the neighbours hear about it, the new panel
     * itself does not.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (state.is(oldState.getBlock())) {
            return;
        }
        BlockState joined = withJoins(state, level, pos);
        if (joined != state) {
            level.setBlock(pos, joined, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * When a neighbour changes, the frame changes.
     *
     * <p>All four at once instead of only the affected side: the effort is
     * the same, and a case distinction that maps four cases onto one is a
     * case distinction that can be wrong.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                     BlockPos neighbourPos) {
        return withJoins(state, level, pos);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, JOINED_UP, JOINED_DOWN, JOINED_LEFT, JOINED_RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return withJoins(placed, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == FnBlockEntities.DISPLAY.get()
                ? (l, p, s, entity) -> ((DisplayBlockEntity) entity).serverTick()
                : null;
    }

    /**
     * Right-click asks for the name.
     *
     * <p>Before, it told you the name you were already reading on the panel —
     * an action that does nothing. Now the window opens where you set it.
     * What the panel <b>shows</b> still lives in the program; this only says
     * which piece of the program is meant.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity)) {
            return InteractionResult.PASS;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                                .NameMenu(id, pos),
                        Component.translatable("screen.factorynetwork.name.title.display")),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(dev.devpanda.factorynetwork.network.packet.SetBlockNamePacket.NO_SIDE);
                });
        return InteractionResult.CONSUME;
    }
}
