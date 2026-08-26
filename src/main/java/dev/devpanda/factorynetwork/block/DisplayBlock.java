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
 * Zeigt an, was im Netz vorgeht.
 *
 * <p>Flach an der Wand, wie ein Bilderrahmen — ein Display ist keine
 * Maschine, sondern eine Auskunft. Was es zeigt, steht im Programm; welches
 * Display gemeint ist, entscheidet sein Name.
 */
public class DisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<DisplayBlock> CODEC = simpleCodec(DisplayBlock::new);

    /** Zwei Pixel tief: Es hängt an der Wand, es steht nicht davor. */
    private static final VoxelShape NORTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WEST = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 2, 16, 16);

    /**
     * An welchen Seiten eine zweite Tafel anschließt.
     *
     * <p>Links und rechts <b>von vorn gesehen</b>, nicht in Weltrichtungen:
     * Der Rahmen fällt dort weg, wo im Bild eine Nachbartafel liegt, und im
     * Bild gibt es kein Norden.
     *
     * <p>Vier Wahrheitswerte mal vier Richtungen sind vierundsechzig
     * Blockzustände. Das ist viel für einen Rahmen — und trotzdem der
     * richtige Weg: Was man sieht, muss im Blockzustand stehen, sonst kann
     * es der Renderer nicht aus dem Modell nehmen, und dann zeichnet er
     * jeden Rahmen selbst.
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
     * Wohin „rechts" zeigt, von vorn gesehen.
     *
     * <p>Bei einer Tafel nach Norden ist das Westen — wer von Norden auf sie
     * schaut, hat Westen zur Rechten. Dieselbe Regel steht in
     * {@code FaceOverlay} und in {@code DisplayWall}; sie muss überall
     * dieselbe sein, sonst sitzt der Rahmen auf der falschen Seite.
     */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Steht dort eine Tafel, die in dieselbe Richtung zeigt? */
    private boolean joins(net.minecraft.world.level.LevelReader level, BlockPos pos,
                          Direction facing) {
        BlockState neighbour = level.getBlockState(pos);
        return neighbour.getBlock() == this && neighbour.getValue(FACING) == facing;
    }

    /** Trägt die vier Nachbarschaften in einen Zustand ein. */
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
     * Auch eine Tafel, die nicht von Hand gesetzt wurde, sieht sich um.
     *
     * <p>{@code getStateForPlacement} greift nur beim Setzen aus der Hand.
     * Ein Kolben, ein Bauwerk oder ein {@code /setblock} legen die Tafel
     * ohne Nachbarschaft ab — und die Nachbarn erfahren zwar davon, die neue
     * Tafel selbst aber nicht.
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
     * Ändert sich ein Nachbar, ändert sich der Rahmen.
     *
     * <p>Alle vier auf einmal statt nur die betroffene Seite: Der Aufwand
     * ist derselbe, und eine Fallunterscheidung, die vier Fälle auf einen
     * abbildet, ist eine Fallunterscheidung, die falsch sein kann.
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
     * Rechtsklick fragt nach dem Namen.
     *
     * <p>Vorher sagte er einem den Namen, den man ohnehin an der Tafel las —
     * eine Handlung, die nichts tut. Jetzt geht das Fenster auf, in dem man
     * ihn setzt. Was die Tafel <b>zeigt</b>, steht weiterhin im Programm;
     * hier steht nur, welches Programmstück gemeint ist.
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
