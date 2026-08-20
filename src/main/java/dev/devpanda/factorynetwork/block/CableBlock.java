package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import dev.devpanda.factorynetwork.block.entity.CableBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
public class CableBlock extends Block implements EntityBlock {

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

    @Override
    public @org.jetbrains.annotations.Nullable BlockEntity newBlockEntity(BlockPos pos,
                                                                          BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    /**
     * Die Stränge eines Kabelblocks.
     *
     * <p>Fällt auf die Zustandsfarbe zurück, wenn keine BlockEntity da ist —
     * das passiert beim Laden einer Welt, die vor den Bündeln gebaut wurde,
     * und beim Zeichnen, bevor die Daten beim Client angekommen sind.
     */
    public static java.util.Set<CableColour> strandsAt(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            return cable.strands();
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CableBlock
                ? java.util.Set.of(state.getValue(COLOUR)) : java.util.Set.of();
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
        return state.setValue(property, connects(level, pos, neighbourPos, neighbour));
    }

    private BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            BlockPos neighbourPos = pos.relative(entry.getKey());
            BlockState neighbour = level.getBlockState(neighbourPos);
            state = state.setValue(entry.getValue(),
                    connects(level, pos, neighbourPos, neighbour));
        }
        return state;
    }

    /**
     * Verbindet sich der Block an dieser Stelle sichtbar zum Nachbarn?
     *
     * <p>Für die Optik reicht ein Strang, der passt. Welcher genau, entscheidet
     * erst das Netzwerk — der Arm im Modell steht für alle gemeinsam.
     */
    private static boolean connects(BlockGetter level, BlockPos pos, BlockPos neighbourPos,
                                    BlockState neighbour) {
        if (neighbour.getBlock() instanceof CableBlock) {
            java.util.Set<CableColour> here = strandsAt(level, pos);
            java.util.Set<CableColour> there = strandsAt(level, neighbourPos);
            return here.stream().anyMatch(a -> there.stream().anyMatch(a::connectsTo));
        }
        return neighbour.getBlock() instanceof ConnectorBlock
                || neighbour.getBlock() instanceof ControllerBlock
                || neighbour.getBlock() instanceof TerminalBlock;
    }

    /**
     * Beim Abbauen fallen alle Stränge.
     *
     * <p>Die Loot-Tabelle kann das nicht: Sie sieht den Blockzustand, aber
     * nicht die BlockEntity. Deshalb wird hier von Hand ausgeworfen — und die
     * Tabelle liefert nichts mehr, sonst käme ein Kabel doppelt.
     */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(
            BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        BlockEntity entity = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>();
        if (entity instanceof CableBlockEntity cable) {
            for (CableColour colour : cable.ordered()) {
                drops.add(new net.minecraft.world.item.ItemStack(
                        dev.devpanda.factorynetwork.registry.FnItems.CABLES.get(colour).get()));
            }
        } else {
            drops.add(new net.minecraft.world.item.ItemStack(
                    dev.devpanda.factorynetwork.registry.FnItems.CABLES
                            .get(state.getValue(COLOUR)).get()));
        }
        return drops;
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
