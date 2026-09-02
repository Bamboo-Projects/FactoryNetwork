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
 * Connects blocks into a network.
 *
 * <p>The shape follows where connections are made — a cable out in mid-air is a
 * cube, one between two neighbours a tube. This is purely visual; routing runs
 * through {@code FactoryGraph}.
 */
public class CableBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    /**
     * How thick this cable is and how many channels it carries.
     *
     * <p>Two kinds, the same class: the difference is a number, not behaviour.
     * A dedicated type per thickness would bring two copies of the same
     * connection logic, and one of them would eventually get an improvement the
     * other lacks.
     */
    private final int size;

    /**
     * What this cable carries per tick, in bytes.
     *
     * <p>Until 29 Aug this held a channel count — how many devices were allowed
     * to hang off it. Now what counts is how much passes through.
     */
    private final int bandwidth;

    /** The colour sits in the block state, not in a BlockEntity —
     *  it never changes and must be available immediately when drawing. */
    public static final EnumProperty<CableColour> COLOUR =
            EnumProperty.create("colour", CableColour.class);

    /**
     * Does this block hold a cable?
     *
     * <p><b>No means: it is a mere holder.</b> A connector sits inside it, but
     * it hangs off nothing — no run, no conduit, no connection to the network.
     * The cable comes later and turns it into a conduit, without the connector
     * having to be placed anew.
     *
     * <p>The default is {@code true}. Every block that stands today thereby
     * stays what it is.
     *
     * <p>This is how AE2 does it: there the block is the cable bus and the cable
     * is just one of the parts inside it. {@code CableBusContainer.canAddPart}
     * allows a part on every free side, "if any" cable — and an empty bus
     * clears itself away in {@code cleanup()}.
     */
    public static final BooleanProperty CABLE = BooleanProperty.create("cable");

    /** Does this block carry a cable, or is it only a holder? */
    public static boolean carries(BlockState state) {
        return !(state.getBlock() instanceof CableBlock) || state.getValue(CABLE);
    }

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
        this(properties, CableLayout.THIN,
                dev.devpanda.factorynetwork.network.Bandwidth.CABLE);
    }

    protected CableBlock(Properties properties, int size, int bandwidth) {
        super(properties);
        this.size = size;
        this.bandwidth = bandwidth;
        BlockState state = stateDefinition.any()
                .setValue(COLOUR, CableColour.NONE);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    /** The ordinary cable carries sixteen channels. */
    /**
     * Every cable block carries a BlockEntity for its connectors.
     *
     * <p><b>Even when none sits on it.</b> The alternative — only creating it
     * once a part is added — demands a flag in the BlockState that changes on
     * placement and removal, and with it a second source of truth about whether
     * parts sit here. AE2 likewise creates one everywhere.
     *
     * <p>What that costs across ten thousand cables is <b>unmeasured</b> and
     * stands as an open item in {@code connector-im-kabel.md}.
     */
    @Override
    public net.minecraft.world.level.block.entity.@org.jetbrains.annotations.Nullable BlockEntity
            newBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        return new dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity(pos, state);
    }

    public int size() {
        return size;
    }

    /** What this cable carries per tick, in bytes. */
    public int bandwidth() {
        return bandwidth;
    }

    /**
     * The items for this kind of cable, one per colour.
     *
     * <p>Needed for the middle-click pick: without it, picking up a red cable
     * would give you a neutral one, and the connection you were about to
     * recreate would not come about.
     */
    protected java.util.Map<CableColour, net.neoforged.neoforge.registries.DeferredItem<
            net.minecraft.world.item.BlockItem>> items() {
        return dev.devpanda.factorynetwork.registry.FnItems.CABLES;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                       BlockPos pos, BlockState state) {
        if (!carries(state)) {
            // A holder is not a cable. The middle-click should give what
            // actually sits there.
            return new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get());
        }
        var entry = items().get(colourOf(state));
        return entry == null ? super.getCloneItemStack(level, pos, state)
                : new ItemStack(entry.get());
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
        builder.add(CABLE);
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
        if (!carries(state)) {
            // A holder gets no arms here either.
            //
            // Not just a matter of appearance: the bits land in the stored
            // state, and hasRoomForPart reads them. A holder next to a cable
            // would otherwise refuse a second connector on this face, one it
            // would have to take — and a holder's colour is neutral, so that
            // holds next to any cable.
            return state.setValue(property, false);
        }
        return state.setValue(property, connectsOrCarries(level, pos, direction,
                state.getValue(COLOUR), neighbour));
    }

    /**
     * Computes all six connections for a state.
     *
     * <p>Public because the item needs it once more: it first sets the colour
     * and must then have them recomputed.
     */
    public static BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        if (!carries(state)) {
            // A holder has no arms. It holds a connector and nothing else —
            // that one is drawn separately.
            for (BooleanProperty property : CONNECTIONS.values()) {
                state = state.setValue(property, false);
            }
            return state;
        }
        CableColour colour = colourOf(state);
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            BlockState neighbour = level.getBlockState(pos.relative(entry.getKey()));
            state = state.setValue(entry.getValue(), connectsOrCarries(
                    level, pos, entry.getKey(), colour, neighbour));
        }
        return state;
    }

    /**
     * Does this face get an arm?
     *
     * <p><b>A connector counts like a neighbour.</b> Until 26 Aug the opposite
     * rule applied — a face with a connector did not connect, so that no arm
     * ran through the middle of the plate. The price was a grey stalk between
     * plate and core: a foreign body in a conduit that otherwise runs through
     * everywhere, and on a cable bundle it looked as if the connector hung
     * beside it rather than on it.
     *
     * <p>Now the arm carries what the stalk carried — it runs in the cable's
     * colour up to under the plate, and a visible crossing forms at the cable.
     * It does not run through the plate: the plate no longer has a stalk it
     * would have to stop in front of, and its front face covers it.
     */
    private static boolean connectsOrCarries(BlockGetter level, BlockPos pos,
                                             Direction direction, CableColour colour,
                                             BlockState neighbour) {
        return hasPart(level, pos, direction) || connects(colour, neighbour);
    }

    /**
     * Does a cable of this colour visibly connect to this neighbour?
     *
     * <p>A block carries exactly one cable in exactly one colour. Two cables
     * connect when their colours match — neutral to anything, same colour to
     * same colour.
     *
     * <p><b>The colour comes from the state, not from the world.</b> Previously
     * it was looked up at its own position; but during placement there is still
     * air there, and air counts as neutral. A red cable therefore computed its
     * connections as a neutral one and reached for everything lying next to it.
     */
    private static boolean connects(CableColour colour, BlockState neighbour) {
        if (neighbour.getBlock() instanceof CableBlock) {
            // Nobody docks onto a holder: it holds no cable, and an arm that
            // pointed at it would point at nothing.
            return carries(neighbour) && colour.connectsTo(colourOf(neighbour));
        }
        // Everything that belongs to the network gets an arm.
        //
        // <b>This list has been wrong three times.</b> First drive and rack
        // were missing, then fabricator, mast, gateway and controller
        // extension. The reason is always the same: the same question — what
        // belongs to the network — sits in three places, and whoever adds a
        // block does not find the other two. The other two are
        // FactoryGraph.consumerAt and FactoryGraph.contains.
        //
        // A check now holds the three against one another, so that the next
        // block stands out before it hangs in the air next to the cable in the
        // game.
        return neighbour.getBlock() instanceof RouterBlock
                || neighbour.getBlock() instanceof ControllerBlock
                || neighbour.getBlock() instanceof TerminalBlock
                || neighbour.getBlock() instanceof DisplayBlock
                || neighbour.getBlock() instanceof DriveBlock
                || neighbour.getBlock() instanceof RackBlock
                || neighbour.getBlock() instanceof FabricatorBlock
                || neighbour.getBlock() instanceof MastBlock
                || neighbour.getBlock() instanceof GatewayBlock
                || neighbour.getBlock() instanceof ControllerExtensionBlock
                || neighbour.getBlock() instanceof BridgeBlock;
    }

    /** Which directions this block connects. */
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
        if (!carries(state)) {
            return CableShapes.holder(size, partsOf(level, pos));
        }
        return CableShapes.whole(size, connectionsOf(state), partsOf(level, pos));
    }

    /** How thick the cable is at this spot, in block pixels. */
    public static int sizeOf(BlockState state) {
        return state.getBlock() instanceof CableBlock cable ? cable.size() : CableLayout.THIN;
    }

    /** The faces on which a connector sits. */
    public static java.util.Set<Direction> partsOf(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus
                ? bus.parts().keySet() : java.util.Set.of();
    }

    /**
     * Does another connector fit on this face?
     *
     * <p>Two reasons argue against it: one already sits there, or the cable
     * continues in that direction. <b>In one place</b>, because two things need
     * it — the placement and the preview before it. Two copies would be two
     * chances for the preview to promise something that the placement then
     * refuses.
     */
    public static boolean hasRoomForPart(BlockState state, BlockGetter level, BlockPos pos,
                                         Direction side) {
        return !hasPart(level, pos, side) && !state.getValue(connection(side));
    }

    private static boolean hasPart(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus
                && bus.partAt(side) != null;
    }

    /**
     * Which connector this click means.
     *
     * <p>Usually the face that was hit. But the plate juts out of the cable,
     * and whoever hits its narrow side gets from Minecraft the direction of
     * <b>that narrow side</b> — which points somewhere other than the part.
     * Then what decides is whose box the hit falls in.
     */
    public static @org.jetbrains.annotations.Nullable Direction partSideAt(
            BlockGetter level, BlockPos pos, BlockHitResult hit) {
        java.util.Set<Direction> parts = partsOf(level, pos);
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.contains(hit.getDirection())) {
            return hit.getDirection();
        }
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int size = sizeOf(level.getBlockState(pos));
        for (Direction side : parts) {
            if (CableShapes.part(size, side).bounds().inflate(1.0E-4).contains(local)) {
                return side;
            }
        }
        return null;
    }

    // ---- Connectors on the faces ------------------------------------------

    /**
     * Places a connector on the face that was hit.
     *
     * <p><b>Why not beside it:</b> this is the whole point of AE2's build form —
     * one block serves up to six neighbours where six blocks used to stand.
     *
     * <p><b>Why a failure here still reports "done":</b> so that the player
     * learns what the trouble was. A {@code FAIL} falls through to the item —
     * and since 26 Aug that is a plain item with no behaviour of its own, so
     * nothing happens afterwards that would explain the click.
     *
     * <p><b>And sneak-clicking leads nowhere.</b> Minecraft then skips this path
     * and calls the item — which does nothing. As long as the connector existed
     * as its own block, this was the escape hatch to place it beside after all;
     * today it is a click with no effect.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        // A cable onto a holder puts the cable inside, instead of placing a
        // second block beside it. That is exactly what the holder is for: the
        // connector already sits there, and the cable follows.
        if (!carries(state)
                && stack.getItem() instanceof dev.devpanda.factorynetwork.item.ColouredCableItem cable) {
            if (!level.isClientSide) {
                level.setBlock(pos, withConnections(state
                                .setValue(CABLE, true)
                                .setValue(COLOUR, cable.colour()),
                        level, pos), UPDATE_ALL);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos,
                        net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.0F);
                dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.is(dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get())
                || !(level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)) {
            return net.minecraft.world.ItemInteractionResult
                    .PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Direction side = hit.getDirection();
        if (!hasRoomForPart(state, level, pos, side)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.factorynetwork.connector.no_room"), true);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            bus.addPart(side);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            // The arm to the connector lives in the block state and must be
            // added now — without it the crossing would be missing until the
            // next neighbour change.
            level.setBlock(pos, withConnections(state, level, pos), UPDATE_ALL);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Removes the connector and gives it back.
     *
     * <p>Without this path you could only get your part back by breaking the
     * whole cable — and with it every other connector on it.
     */
    private static void removePart(Level level, BlockPos pos, Direction side, Player player) {
        if (!(level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)
                || bus.removePart(side) == null) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            popResource(level, pos,
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()));
        }
        // A holder with no connectors is nothing any more: no cable inside,
        // no part on it. It disappears, instead of staying put as an invisible
        // block that nobody can hit any more.
        //
        // AE2 does the same in CableBusContainer.cleanup(): an empty bus
        // calls removeBlock on itself.
        if (!carries(level.getBlockState(pos)) && bus.parts().isEmpty()) {
            level.removeBlock(pos, false);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
            return;
        }
        // The face is free again: maybe the cable now wants an arm in that
        // direction.
        level.setBlock(pos, withConnections(level.getBlockState(pos), level, pos), UPDATE_ALL);
        dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
    }

    /**
     * Whoever breaks the cable gets their connectors back.
     *
     * <p><b>Only when a different block really takes its place.</b>
     * {@code onRemove} also fires on every state change, and a cable changes
     * its state with every neighbour that appears or disappears — without this
     * check the parts would drop out while the conduit was being <i>built</i>.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            int found = partsOf(level, pos).size();
            for (int i = 0; i < found; i++) {
                popResource(level, pos,
                        new ItemStack(dev.devpanda.factorynetwork.registry.FnItems.CONNECTOR.get()));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Opens the naming screen for the connector on the face that was hit.
     *
     * <p>The face decides which one is meant. Without it, a click on a cable
     * block with six connectors would be a question without an answer — and a
     * guessed answer names the wrong device.
     *
     * <p>A cable with no connectors behaves as before: the click passes
     * through, so that a cable in the hand still gets placed.
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = partSideAt(level, pos, hit);
        if (side == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        // Sneaking with empty hands removes instead of naming. Minecraft calls
        // this path while sneaking too, as long as both hands are empty — the
        // distinction rests exactly on that.
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                removePart(level, pos, side, player);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, owner) -> new dev.devpanda.factorynetwork.client.menu
                                .NameMenu(id, pos, side),
                        net.minecraft.network.chat.Component.translatable(
                                "screen.factorynetwork.name.title.connector")),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(side.get3DDataValue());
                });
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /**
     * Emits redstone when a connector on it calls for it.
     *
     * <p><b>A face with a connector emits exactly that connector's strength; a
     * free one emits the strongest.</b> The first part is the point of the
     * thing: six connectors on one block switch six machines, and with a single
     * shared strength it would be six machines on one switch. The second keeps
     * what the connector block could already do — there, a single connector
     * puts out the same on every side, and a little lamp next to the cable
     * keeps glowing.
     *
     * <p><b>The price:</b> {@code isSignalSource} sees only the BlockState and
     * thus not whether parts sit here. It therefore stands unconditionally at
     * {@code true}, and redstone dust points at an empty cable too. The
     * opposite would demand a second flag in the BlockState — and with it a
     * second source of truth about whether a cable carries connectors.
     */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos,
                            Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus)
                || !bus.hasParts()) {
            return 0;
        }
        // Whoever asks from direction is standing on the opposite side: the
        // part on the face direction.getOpposite() faces them.
        dev.devpanda.factorynetwork.block.entity.ConnectorPart ahead = bus.partAt(direction.getOpposite());
        if (ahead != null) {
            return ahead.emittedRedstone();
        }
        int strongest = 0;
        for (dev.devpanda.factorynetwork.block.entity.ConnectorPart part : bus.parts().values()) {
            strongest = Math.max(strongest, part.emittedRedstone());
        }
        return strongest;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos,
                                  Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
