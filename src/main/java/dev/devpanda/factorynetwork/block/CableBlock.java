package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Verbindet Blöcke zu einem Netzwerk.
 *
 * <p>Die Form richtet sich danach, wohin verbunden wird — ein Kabel mitten in
 * der Luft ist ein Würfel, eines zwischen zwei Nachbarn eine Röhre. Das ist
 * reine Optik, gelaufen wird über {@code FactoryGraph}.
 */
public class CableBlock extends Block {

    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    /** Die Farbe steckt im Blockzustand, nicht in einer BlockEntity —
     *  sie ändert sich nie und muss beim Zeichnen sofort verfügbar sein. */
    public static final EnumProperty<CableColour> COLOUR =
            EnumProperty.create("colour", CableColour.class);

    private static final Map<Direction, BooleanProperty> CONNECTIONS =
            new EnumMap<>(Map.of(
                    Direction.NORTH, BooleanProperty.create("north"),
                    Direction.SOUTH, BooleanProperty.create("south"),
                    Direction.EAST, BooleanProperty.create("east"),
                    Direction.WEST, BooleanProperty.create("west"),
                    Direction.UP, BooleanProperty.create("up"),
                    Direction.DOWN, BooleanProperty.create("down")));

    private static final double MIN = 5.0D;
    private static final double MAX = 11.0D;
    private static final VoxelShape CORE = Block.box(MIN, MIN, MIN, MAX, MAX, MAX);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(MIN, MIN, 0.0D, MAX, MAX, MIN),
            Direction.SOUTH, Block.box(MIN, MIN, MAX, MAX, MAX, 16.0D),
            Direction.WEST, Block.box(0.0D, MIN, MIN, MIN, MAX, MAX),
            Direction.EAST, Block.box(MAX, MIN, MIN, 16.0D, MAX, MAX),
            Direction.DOWN, Block.box(MIN, 0.0D, MIN, MAX, MIN, MAX),
            Direction.UP, Block.box(MIN, MAX, MIN, MAX, 16.0D, MAX)));

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(COLOUR, CableColour.NONE);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    public static CableColour colourOf(BlockState state) {
        return state.getBlock() instanceof CableBlock ? state.getValue(COLOUR) : CableColour.NONE;
    }

    public static BooleanProperty connection(Direction direction) {
        return CONNECTIONS.get(direction);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOUR);
        CONNECTIONS.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BooleanProperty property = CONNECTIONS.get(direction);
        return state.setValue(property, connectsTo(state, neighbour));
    }

    private BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            BlockState neighbour = level.getBlockState(pos.relative(entry.getKey()));
            state = state.setValue(entry.getValue(), connectsTo(state, neighbour));
        }
        return state;
    }

    /**
     * Verbinden sich diese beiden Blöcke?
     *
     * <p>Zwei Kabel nur bei passender Farbe — daran hängt der ganze Zweck der
     * Farben. Geräte und der Controller nehmen jedes Kabel an: Sie gehören zu
     * dem Netz, an dem sie hängen, und müssten sonst selbst gefärbt werden.
     */
    private static boolean connectsTo(BlockState self, BlockState neighbour) {
        if (neighbour.getBlock() instanceof CableBlock) {
            return colourOf(self).connectsTo(colourOf(neighbour));
        }
        return neighbour.getBlock() instanceof ConnectorBlock
                || neighbour.getBlock() instanceof ControllerBlock
                || neighbour.getBlock() instanceof TerminalBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            if (state.getValue(entry.getValue())) {
                shape = Shapes.or(shape, ARMS.get(entry.getKey()));
            }
        }
        return shape;
    }
}
