package dev.devpanda.factorynetwork.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    /**
     * Wie stark dieses Kabel ist und wie viele Kanäle es trägt.
     *
     * <p>Zwei Sorten, dieselbe Klasse: Der Unterschied ist eine Zahl, kein
     * Verhalten. Ein eigener Typ je Stärke brächte zwei Fassungen derselben
     * Verbindungslogik, und die eine bekäme irgendwann eine Verbesserung, die
     * der anderen fehlt.
     */
    private final int size;
    private final int channels;

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
        this(properties, CableLayout.THIN, CHANNELS_THIN);
    }

    protected CableBlock(Properties properties, int size, int channels) {
        super(properties);
        this.size = size;
        this.channels = channels;
        BlockState state = stateDefinition.any()
                .setValue(COLOUR, CableColour.NONE);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    /** Das gewöhnliche Kabel trägt sechzehn Kanäle. */
    public static final int CHANNELS_THIN = 16;

    /** Das dichte vierundsechzig — viermal so viel, wie bei AE2 auch. */
    public static final int CHANNELS_DENSE = 64;

    public int size() {
        return size;
    }

    public int channels() {
        return channels;
    }

    /**
     * Wie viele Kanäle das Kabel an dieser Stelle trägt.
     *
     * <p>Null, wenn dort gar kein Kabel liegt — der Aufrufer entscheidet, was
     * das heißt.
     */
    public static int channelsAt(BlockState state) {
        return state.getBlock() instanceof CableBlock cable ? cable.channels() : 0;
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
     * <p>Ein Block trägt genau ein Kabel in genau einer Farbe. Zwei Kabel
     * verbinden sich, wenn ihre Farben zueinander passen — neutral zu allem,
     * gleiche Farbe zueinander.
     */
    private static boolean connects(BlockGetter level, BlockPos pos, BlockPos neighbourPos,
                                    BlockState neighbour) {
        if (neighbour.getBlock() instanceof CableBlock) {
            return colourOf(level.getBlockState(pos)).connectsTo(colourOf(neighbour));
        }
        // Alles, was zum Netz gehört, bekommt einen Arm. Laufwerk und
        // Serverschrank fehlten hier: Die Suche findet sie über die
        // Nachbarschaft, aber im Bild hing das Kabel daneben in der Luft.
        return neighbour.getBlock() instanceof ConnectorBlock
                || neighbour.getBlock() instanceof RouterBlock
                || neighbour.getBlock() instanceof ControllerBlock
                || neighbour.getBlock() instanceof TerminalBlock
                || neighbour.getBlock() instanceof DisplayBlock
                || neighbour.getBlock() instanceof DriveBlock
                || neighbour.getBlock() instanceof RackBlock;
    }

    /** Welche Richtungen dieser Block verbindet. */
    public static List<Direction> connectionsOf(BlockState state) {
        List<Direction> directions = new ArrayList<>();
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            if (state.getValue(entry.getValue())) {
                directions.add(entry.getKey());
            }
        }
        return directions;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return CableShapes.whole(size, connectionsOf(state));
    }
}
