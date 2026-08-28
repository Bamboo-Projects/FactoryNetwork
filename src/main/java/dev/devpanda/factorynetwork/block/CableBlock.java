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
public class CableBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

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

    /**
     * Liegt in diesem Block ein Kabel?
     *
     * <p><b>Nein heißt: Er ist ein bloßer Halter.</b> Ein Anschluss sitzt
     * darin, aber er hängt an nichts — kein Strang, keine Leitung, keine
     * Verbindung zum Netz. Das Kabel kommt später und macht daraus eine
     * Leitung, ohne dass der Anschluss neu gesetzt werden müsste.
     *
     * <p>Die Vorgabe ist {@code true}. Jeder Block, der heute steht, bleibt
     * damit, was er ist.
     *
     * <p>So macht es AE2: Dort ist der Block der Kabelbus und das Kabel nur
     * eines der Teile darin. {@code CableBusContainer.canAddPart} lässt ein
     * Teil an jede freie Seite, „if any" Kabel — und ein leerer Bus räumt
     * sich in {@code cleanup()} selbst weg.
     */
    public static final BooleanProperty CABLE = BooleanProperty.create("cable");

    /** Trägt dieser Block ein Kabel, oder ist er nur ein Halter? */
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
    /**
     * Jeder Kabelblock trägt eine BlockEntity für seine Anschlüsse.
     *
     * <p><b>Auch wenn keiner daran sitzt.</b> Die Alternative — sie erst
     * anlegen, wenn ein Teil dazukommt — verlangt einen Zustand im
     * BlockState, der sich beim Setzen und Abbauen ändert, und damit eine
     * zweite Wahrheit darüber, ob hier Teile sitzen. AE2 legt sie ebenfalls
     * überall an.
     *
     * <p>Was das bei zehntausend Kabeln kostet, ist <b>ungemessen</b> und
     * steht als offener Punkt in {@code connector-im-kabel.md}.
     */
    @Override
    public net.minecraft.world.level.block.entity.@org.jetbrains.annotations.Nullable BlockEntity
            newBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        return new dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity(pos, state);
    }

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

    /**
     * Die Gegenstände zu dieser Kabelsorte, je Farbe einer.
     *
     * <p>Gebraucht für die Mitteltaste: Ohne das bekäme man beim Aufnehmen
     * eines roten Kabels ein neutrales, und die Verbindung, die man gerade
     * nachbauen wollte, käme nicht zustande.
     */
    protected java.util.Map<CableColour, net.neoforged.neoforge.registries.DeferredItem<
            net.minecraft.world.item.BlockItem>> items() {
        return dev.devpanda.factorynetwork.registry.FnItems.CABLES;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                       BlockPos pos, BlockState state) {
        if (!carries(state)) {
            // Ein Halter ist kein Kabel. Der Mittelklick soll das geben, was
            // dort tatsächlich sitzt.
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
            // Ein Halter bekommt auch hier keine Arme.
            //
            // Nicht nur eine Frage des Bildes: Die Bits landen im
            // gespeicherten Zustand, und hasRoomForPart liest sie. Ein Halter
            // neben einem Kabel verweigerte sonst auf dieser Fläche einen
            // zweiten Anschluss, den er nehmen müsste — und die Farbe eines
            // Halters ist neutral, also gilt das neben jedem Kabel.
            return state.setValue(property, false);
        }
        return state.setValue(property, connectsOrCarries(level, pos, direction,
                state.getValue(COLOUR), neighbour));
    }

    /**
     * Rechnet alle sechs Verbindungen für einen Zustand aus.
     *
     * <p>Öffentlich, weil der Gegenstand sie noch einmal braucht: Er setzt
     * erst die Farbe und muss danach neu rechnen lassen.
     */
    public static BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        if (!carries(state)) {
            // Ein Halter hat keine Arme. Er hält einen Anschluss und sonst
            // nichts — der wird gesondert gezeichnet.
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
     * Bekommt diese Fläche einen Arm?
     *
     * <p><b>Ein Anschluss zählt wie ein Nachbar.</b> Bis zum 26.08. galt die
     * umgekehrte Regel — eine Fläche mit Anschluss verband nicht, damit kein
     * Arm mitten durch die Platte lief. Der Preis war ein grauer Stiel
     * zwischen Platte und Kern: ein Fremdkörper in einer Leitung, die sonst
     * überall durchläuft, und an einem Kabelbündel sah es aus, als hinge der
     * Anschluss daneben statt daran.
     *
     * <p>Jetzt trägt der Arm, was der Stiel trug — er läuft in der Farbe des
     * Kabels bis unter die Platte, und am Kabel entsteht eine sichtbare
     * Kreuzung. Durch die Platte läuft er nicht: Sie hat keinen Stiel mehr,
     * vor dem er halten müsste, und ihre Vorderseite deckt ihn ab.
     */
    private static boolean connectsOrCarries(BlockGetter level, BlockPos pos,
                                             Direction direction, CableColour colour,
                                             BlockState neighbour) {
        return hasPart(level, pos, direction) || connects(colour, neighbour);
    }

    /**
     * Verbindet sich ein Kabel dieser Farbe sichtbar zu diesem Nachbarn?
     *
     * <p>Ein Block trägt genau ein Kabel in genau einer Farbe. Zwei Kabel
     * verbinden sich, wenn ihre Farben zueinander passen — neutral zu allem,
     * gleiche Farbe zueinander.
     *
     * <p><b>Die Farbe kommt aus dem Zustand, nicht aus der Welt.</b> Vorher
     * wurde sie an der eigenen Stelle nachgeschlagen; beim Setzen steht dort
     * aber noch Luft, und Luft gilt als neutral. Ein rotes Kabel rechnete
     * sich seine Verbindungen deshalb als neutrales aus und griff nach allem,
     * was danebenlag.
     */
    private static boolean connects(CableColour colour, BlockState neighbour) {
        if (neighbour.getBlock() instanceof CableBlock) {
            // An einen Halter dockt niemand an: In ihm liegt kein Kabel, und
            // ein Arm, der auf ihn zeigte, zeigte auf nichts.
            return carries(neighbour) && colour.connectsTo(colourOf(neighbour));
        }
        // Alles, was zum Netz gehört, bekommt einen Arm.
        //
        // <b>Diese Liste ist dreimal falsch gewesen.</b> Erst fehlten
        // Laufwerk und Serverschrank, dann Fabricator, Sendemast, Gateway und
        // Anbau. Der Grund ist immer derselbe: Dieselbe Frage — was gehört
        // zum Netz — steht an drei Stellen, und wer einen Block einträgt,
        // findet die anderen zwei nicht. Die beiden anderen sind
        // FactoryGraph.consumerAt und FactoryGraph.contains.
        //
        // Ein Prüflauf hält die drei jetzt gegeneinander, damit der nächste
        // Block auffällt, bevor er im Spiel neben dem Kabel in der Luft hängt.
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
        if (!carries(state)) {
            return CableShapes.holder(size, partsOf(level, pos));
        }
        return CableShapes.whole(size, connectionsOf(state), partsOf(level, pos));
    }

    /** Wie stark das Kabel an dieser Stelle ist, in Blockpixeln. */
    public static int sizeOf(BlockState state) {
        return state.getBlock() instanceof CableBlock cable ? cable.size() : CableLayout.THIN;
    }

    /** Die Flächen, an denen ein Anschluss sitzt. */
    public static java.util.Set<Direction> partsOf(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus
                ? bus.parts().keySet() : java.util.Set.of();
    }

    /**
     * Passt an diese Fläche noch ein Anschluss?
     *
     * <p>Zwei Gründe sprechen dagegen: Dort sitzt schon einer, oder das Kabel
     * läuft dorthin weiter. <b>An einer Stelle</b>, weil zwei sie brauchen —
     * das Setzen und die Vorschau davor. Zwei Fassungen wären zwei
     * Gelegenheiten, dass die Vorschau etwas verspricht, was das Setzen dann
     * ablehnt.
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
     * Welchen Anschluss dieser Klick meint.
     *
     * <p>Meist die getroffene Fläche. Aber die Platte ragt aus dem Kabel
     * heraus, und wer ihre Schmalseite trifft, bekommt von Minecraft die
     * Richtung <b>dieser Schmalseite</b> — die zeigt woandershin als das
     * Teil. Dann entscheidet, in wessen Kasten der Treffer liegt.
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

    // ---- Anschlüsse an den Flächen ----------------------------------------

    /**
     * Setzt einen Anschluss an die getroffene Fläche.
     *
     * <p><b>Warum nicht daneben:</b> Das ist der Griff, um den es bei AE2s
     * Bauform geht — ein Block bedient bis zu sechs Nachbarn, wo bisher sechs
     * Blöcke standen.
     *
     * <p><b>Warum ein Fehlschlag hier trotzdem „erledigt" meldet:</b> Damit
     * der Spieler erfährt, woran es lag. Ein {@code FAIL} fällt durch auf den
     * Gegenstand — und der ist seit dem 26.08. ein schlichter Gegenstand ohne
     * eigenes Verhalten, also passiert danach nichts, was den Klick erklären
     * würde.
     *
     * <p><b>Und schleichend klicken führt ins Leere.</b> Minecraft überspringt
     * diesen Weg dann und ruft den Gegenstand — der nichts tut. Solange es
     * den Connector als eigenen Block gab, war das die Fluchtluke, um ihn
     * doch danebenzusetzen; heute ist es ein Klick ohne Wirkung.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        // Ein Kabel auf einen Halter legt das Kabel hinein, statt einen
        // zweiten Block danebenzusetzen. Genau dafür ist der Halter da: Der
        // Anschluss sitzt schon, und das Kabel kommt nach.
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
            // Der Arm zum Anschluss steht im Blockzustand und muss jetzt
            // dazu — ohne das bliebe die Kreuzung bis zum nächsten
            // Nachbarwechsel aus.
            level.setBlock(pos, withConnections(state, level, pos), UPDATE_ALL);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Nimmt den Anschluss ab und gibt ihn zurück.
     *
     * <p>Ohne diesen Weg käme man nur an sein Teil zurück, indem man das
     * ganze Kabel abbaut — und mit ihm alle anderen Anschlüsse daran.
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
        // Ein Halter ohne Anschlüsse ist nichts mehr: kein Kabel darin,
        // kein Teil daran. Er verschwindet, statt als unsichtbarer Block
        // stehenzubleiben, den niemand mehr trifft.
        //
        // AE2 macht dasselbe in CableBusContainer.cleanup(): Ein leerer Bus
        // ruft removeBlock auf sich selbst.
        if (!carries(level.getBlockState(pos)) && bus.parts().isEmpty()) {
            level.removeBlock(pos, false);
            dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
            return;
        }
        // Die Fläche ist wieder frei: Vielleicht will das Kabel dorthin
        // jetzt einen Arm.
        level.setBlock(pos, withConnections(level.getBlockState(pos), level, pos), UPDATE_ALL);
        dev.devpanda.factorynetwork.network.ControllerRegistry.refreshAround(level, pos);
    }

    /**
     * Wer das Kabel abbaut, bekommt seine Anschlüsse zurück.
     *
     * <p><b>Nur, wenn dort wirklich ein anderer Block hinkommt.</b>
     * {@code onRemove} feuert auch bei jedem Zustandswechsel, und ein Kabel
     * wechselt seinen Zustand bei jedem Nachbarn, der auftaucht oder
     * verschwindet — ohne diese Prüfung fielen die Teile beim <i>Bauen</i>
     * der Leitung heraus.
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
     * Öffnet das Namensfenster des Anschlusses an der getroffenen Fläche.
     *
     * <p>Die Fläche entscheidet, welcher gemeint ist. Ohne sie wäre ein Klick
     * auf einen Kabelblock mit sechs Anschlüssen eine Frage ohne Antwort —
     * und eine geratene Antwort benennt das falsche Gerät.
     *
     * <p>Ein Kabel ohne Anschlüsse verhält sich wie zuvor: Der Klick geht
     * durch, damit ein Kabel in der Hand weiterhin gesetzt wird.
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = partSideAt(level, pos, hit);
        if (side == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        // Schleichen mit leeren Händen nimmt ab statt zu benennen. Minecraft
        // ruft diesen Weg auch beim Schleichen, solange beide Hände leer
        // sind — genau darauf beruht die Unterscheidung.
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
     * Gibt Redstone aus, wenn ein Anschluss daran es verlangt.
     *
     * <p><b>Eine Fläche mit Anschluss gibt genau dessen Stärke; eine freie
     * gibt die stärkste.</b> Der erste Teil ist der Sinn der Sache: Sechs
     * Anschlüsse an einem Block schalten sechs Maschinen, und mit einer
     * gemeinsamen Stärke wären es sechs Maschinen an einem Schalter. Der
     * zweite hält, was der Connectorblock schon konnte — dort kommt bei einem
     * einzigen Anschluss nach allen Seiten dasselbe heraus, und ein Lämpchen
     * neben dem Kabel leuchtet weiter.
     *
     * <p><b>Der Preis:</b> {@code isSignalSource} sieht nur den BlockState und
     * damit nicht, ob hier Teile sitzen. Es steht deshalb bedingungslos auf
     * {@code true}, und Redstonestaub zeigt auch auf ein leeres Kabel. Das
     * Gegenteil verlangte einen zweiten Zustand im BlockState — und damit
     * eine zweite Wahrheit darüber, ob ein Kabel Anschlüsse trägt.
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
        // Wer aus Richtung direction fragt, steht auf der Gegenseite: Ihm
        // sieht das Teil an der Fläche direction.getOpposite() ins Gesicht.
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
