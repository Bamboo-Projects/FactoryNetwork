package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.ControllerExtensionBlock;
import dev.devpanda.factorynetwork.block.DisplayBlock;
import dev.devpanda.factorynetwork.block.DriveBlock;
import dev.devpanda.factorynetwork.block.FabricatorBlock;
import dev.devpanda.factorynetwork.block.RackBlock;
import dev.devpanda.factorynetwork.block.RouterBlock;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.util.NameDistance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A controller's network: all connectors connected to it by cable.
 *
 * <p>The graph is not maintained continuously but rebuilt whenever something
 * has changed. For the scale in question — a few hundred blocks — that is far
 * simpler than a structure that grows along with it, and a cable is rarely
 * placed.
 *
 * <p>Unloaded chunks are skipped. A connector there is not gone, merely
 * temporarily unreachable — the difference is explained in {@code sprache.md},
 * section 14.
 */
public final class FactoryGraph {

    /**
     * The build searches no further than this; guards against endless
     * networks.
     *
     * <p>Configurable in the server config — the operator of a pack knows
     * their players and their hardware, the mod does not.
     */
    private static int maxNodes() {
        return dev.devpanda.factorynetwork.FnConfig.networkNodes();
    }

    /**
     * Name to spots. More than one means: the name is assigned twice and thus
     * unusable — see {@link #isAmbiguous}.
     *
     * <p>A spot is a place <b>and</b> a face ({@link DevicePos}): a cable block
     * carries up to six connectors, and two of them are two devices with two
     * names.
     */
    private final Map<String, List<DevicePos>> connectorsByName;
    /**
     * Connectors without a name.
     *
     * <p>They cannot be addressed — that needs a name — but they hang in the
     * network, and the player must see it. Leaving them out here would mean the
     * controller reported "0 connectors" while three hung right beside it.
     */
    private final List<DevicePos> unnamed;
    /** Devices that hang in the network but received no free channel. */
    /**
     * Which cables a device works over.
     *
     * <p>Until 29.08. this held {@code starved}: devices for which no channel
     * was free any more. They no longer exist — whoever is reachable hangs on
     * the network. What can get scarce is the throughput, and that sits along
     * the path.
     */
    private final Map<DevicePos, List<Node>> paths;
    /** Displays on the network. They only show and need no channel. */
    private final List<BlockPos> displays;
    private final Set<BlockPos> cables;
    /** Routers on the network. They forward and cost no channel themselves. */
    private final Set<BlockPos> routers;

    /**
     * Devices claimed by two gateways — they belong to no installation.
     *
     * <p>Which one would win would hang on the search order. Nothing is
     * guessed; it is reported, though.
     */
    private final Set<BlockPos> contested;
    /** Drives on the network. They carry the storage the network uses. */
    private final List<BlockPos> drives;
    /** Server racks on the network. They carry the computing power. */
    private final List<BlockPos> racks;
    /** Fabricators on the network. Each allows one crafting step per cycle. */
    private final List<BlockPos> fabricators;
    /**
     * The extensions on the controller.
     *
     * <p>They do not hang on the network, they <b>are</b> the network — each
     * brings its own sides on which a strand can begin. They are kept for the
     * energy draw and for the analyzer.
     */
    private final List<BlockPos> extensions;
    /** How many channels each cable strand carries. */

    /**
     * Who is connected to whom.
     *
     * <p>The search knows this anyway — for every node it remembers where it
     * came from. It is kept for the network analyzer, which draws the network
     * as lines into the world. Without the edges it would be left with only a
     * cloud of points.
     */
    private final List<Edge> edges;
    private final List<BlockPos> masts;
    private final Set<BlockPos> bridges;
    private final boolean truncated;

    /** A connection between two nodes of the network. */
    public record Edge(Node from, Node to) {}

    private FactoryGraph(Map<String, List<DevicePos>> connectorsByName,
                         List<DevicePos> unnamed,
                         Map<DevicePos, List<Node>> paths, List<BlockPos> displays,
                         Set<BlockPos> cables,
                         Set<BlockPos> routers, List<Edge> edges,
                         List<BlockPos> drives, List<BlockPos> racks, List<BlockPos> extensions,
                         List<BlockPos> fabricators, Set<BlockPos> contested,
                         List<BlockPos> masts, Set<BlockPos> bridges,
                         boolean truncated) {
        this.masts = masts;
        this.bridges = bridges;
        this.contested = contested;
        this.fabricators = fabricators;
        this.extensions = extensions;
        this.routers = routers;
        this.drives = drives;
        this.racks = racks;
        this.displays = displays;
        this.connectorsByName = connectorsByName;
        this.unnamed = unnamed;
        this.paths = paths;

        this.cables = cables;
        this.edges = edges;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), List.of(), Map.of(), List.of(),
                Set.of(), Set.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Set.of(), List.of(), Set.of(), false);
    }

    /** The fabricators on the network. */
    public List<BlockPos> fabricators() {
        return fabricators;
    }

    /**
     * The transmitter masts on the network.
     *
     * <p>The controller needs them for the energy bill: what a mast draws
     * depends on what is in it.
     */
    public List<BlockPos> masts() {
        return masts;
    }

    /**
     * The quantum bridges on the network.
     *
     * <p>Always in pairs, when both ends are loaded — otherwise only the near
     * end stands in it, and the path ends there.
     */
    public Set<BlockPos> bridges() {
        return bridges;
    }

    /** The extensions on the controller. */
    public List<BlockPos> extensions() {
        return extensions;
    }

    /** The drives on the network. */
    public List<BlockPos> drives() {
        return drives;
    }

    /** The server racks on the network. */
    public List<BlockPos> racks() {
        return racks;
    }

    /** The network's connections, for the in-world display. */
    public List<Edge> edges() {
        return edges;
    }

    /**
     * A node of the search: a spot and a strand.
     *
     * <p>A cable block is not one node but up to four — otherwise a green
     * strand would run over a block that also holds a red one, and the two
     * would suddenly be connected.
     */
    public record Node(BlockPos pos, CableColour colour, int lane) {

        /**
         * A node without a lane — everything except a router.
         *
         * <p>The lane separates only within a router. A cable has none, so
         * {@link RouterBlockEntity#OFF} stands there, and the existing callers
         * stay as they are.
         */
        public Node(BlockPos pos, CableColour colour) {
            this(pos, colour, RouterBlockEntity.OFF);
        }
    }


    /**
     * Builds the graph starting from the controller.
     *
     * <p>The breadth-first search goes in a fixed order of directions —
     * {@code Direction} in its own order — and collects devices by distance.
     * <b>So with scarce channels the nearer device always wins</b>, and at
     * equal distance the one in the earlier direction. This rule must be fixed
     * and explainable: otherwise "why is this device offline" cannot be
     * answered.
     */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, List<DevicePos>> connectors = new LinkedHashMap<>();
        List<DevicePos> unnamed = new ArrayList<>();
        // Each device's path to the controller. It no longer limits who is
        // connected — it says which cables a device works over, and the
        // throughput hangs on that.
        Map<DevicePos, List<Node>> paths = new java.util.LinkedHashMap<>();
        List<BlockPos> displays = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> routers = new HashSet<>();
        List<BlockPos> masts = new ArrayList<>();
        Set<BlockPos> bridges = new HashSet<>();
        Set<BlockPos> gateways = new LinkedHashSet<>();
        List<BlockPos> drives = new ArrayList<>();
        List<BlockPos> racks = new ArrayList<>();
        List<BlockPos> fabricators = new ArrayList<>();

        Map<Node, Node> parents = new HashMap<>();
        // Device to the cable strands over which it is reachable — in the
        // order in which the search found them.
        //
        // The key is place and face and not just place: six connectors on a
        // cable block are six devices, each with its own name and its own
        // channel demand.
        Map<DevicePos, List<Node>> reachable = new LinkedHashMap<>();
        Map<DevicePos, Consumer> kinds = new LinkedHashMap<>();
        Deque<Node> queue = new ArrayDeque<>();

        // The controller and its extensions are colour-neutral: from each of
        // them radiate all the strands that hang on it. Multiple roots are
        // exactly the point of an extension — six sides per block.
        List<BlockPos> extensions = extensionsAround(level, controller);
        Node root = new Node(controller, CableColour.NONE);
        queue.add(root);
        parents.put(root, null);
        for (BlockPos extension : extensions) {
            Node node = new Node(extension, CableColour.NONE);
            queue.add(node);
            parents.put(node, null);
        }
        boolean truncated = false;

        while (!queue.isEmpty()) {
            if (parents.size() > maxNodes()) {
                truncated = true;
                break;
            }
            Node current = queue.poll();
            // If the search stands in a router, it applies to one lane only.
            // The filter must come before everything else, not just before
            // the cable branch: a device on a foreign lane does not belong
            // here either.
            // Asked at the block and not at the node: the node used to carry
            // the lane number, and that is gone — a router is now one node,
            // not four.
            boolean atRouter = level.getBlockState(current.pos()).getBlock()
                    instanceof RouterBlock;
            for (Direction direction : Direction.values()) {
                // Out of a router only where the side allows it. Previously a
                // lane comparison stood here; now the filter of the exit side
                // decides — see mayLeaveRouter.
                if (atRouter && !mayLeaveRouter(level, current.pos(), direction,
                        current.colour())) {
                    continue;
                }
                BlockPos next = current.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    // An unloaded chunk ends the branch, without error.
                    continue;
                }
                BlockState state = level.getBlockState(next);
                // A holder without a cable does not conduct. Its connector
                // sits inside it and waits for a cable — until then it belongs
                // to no network.
                if (state.getBlock() instanceof CableBlock
                        && dev.devpanda.factorynetwork.block.CableBlock.carries(state)) {
                    Node node = visitCable(level, next, current, parents, queue, cables);
                    // Every connector on a face of this cable is its own
                    // device — and its path to the controller runs over the
                    // cable itself, not over the one before it.
                    if (node != null) {
                        for (Direction side : partsAt(level, next)) {
                            DevicePos device = DevicePos.of(next, side);
                            reachable.computeIfAbsent(device, key -> new ArrayList<>())
                                    .add(node);
                            kinds.put(device, Consumer.CONNECTOR);
                        }
                    }
                } else if (state.getBlock()
                        instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
                    // It passes the strand through like a cable and multiplies
                    // nothing in doing so: its channel limit lives in
                    // capacityAt. What it does on top — giving its
                    // surroundings an installation name — is decided by the
                    // neighbourhood at the cable and not by the path to the
                    // controller; that is why it lives in GatewayRegions and
                    // not here.
                    visitCable(level, next, current, parents, queue, cables);
                    gateways.add(next.immutable());
                } else if (state.getBlock()
                        instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
                    visitBridge(level, next, current, parents, queue, bridges);
                } else if (state.getBlock() instanceof RouterBlock) {
                    visitRouter(level, next, direction, current, parents, queue, routers);
                } else {
                    // Everything that costs a channel goes the same way — do
                    // not assign yet: a device can hang on several strands,
                    // and which one carries it is decided only once all paths
                    // are known.
                    Consumer kind = consumerAt(state);
                    if (kind != null) {
                        DevicePos device = deviceAt(state, next);
                        reachable.computeIfAbsent(device, key -> new ArrayList<>())
                                .add(current);
                        kinds.put(device, kind);
                        // Displays forward. A wall of six panels is one
                        // picture and not six devices, and laying a cable
                        // behind each one is work without a decision. All
                        // other devices end here: whoever places two drives
                        // side by side means two drives and not a line.
                        if (kind == Consumer.DISPLAY) {
                            visitDisplay(next, current, parents, queue);
                        }
                    }
                }
            }
        }

        // Only now, because only now is it settled which gateways hang in the
        // network. A gateway without a cable to the controller does not belong
        // and therefore names nothing either.
        GatewayRegions regions = gateways.isEmpty()
                ? GatewayRegions.EMPTY : GatewayRegions.of(level, gateways, maxNodes());
        assignPaths(level, reachable, kinds, parents, connectors, unnamed,
                paths, displays, drives, racks, fabricators, masts, regions);

        Map<String, List<DevicePos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        List<Edge> edges = new ArrayList<>();
        parents.forEach((node, parent) -> {
            if (parent != null) {
                edges.add(new Edge(parent, node));
            }
        });
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                Map.copyOf(paths), List.copyOf(displays), Set.copyOf(cables),
                Set.copyOf(routers), List.copyOf(edges),
                List.copyOf(drives), List.copyOf(racks), List.copyOf(extensions),
                List.copyOf(fabricators), Set.copyOf(regions.contested()),
                List.copyOf(masts), Set.copyOf(bridges), truncated);
    }

    /**
     * The extensions that touch the controller — directly or through other
     * extensions.
     *
     * <p><b>Only across faces, never across a cable.</b> If an extension could
     * be cabled up, it would be a channel multiplier placeable any number of
     * times: six new sides for a block, somewhere out in the terrain, and the
     * channel limit would mean nothing any more. Whoever wants to expand
     * builds on the controller.
     *
     * <p>There is no upper limit. Six sides per block, and each extension
     * brings new ones — this is bounded by the build's shape and by the energy
     * draw, not by a number in the code.
     */
    private static List<BlockPos> extensionsAround(Level level, BlockPos controller) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(controller.immutable());
        queue.add(controller.immutable());
        while (!queue.isEmpty() && found.size() < maxNodes()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof ControllerExtensionBlock) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * What counts as a device on the network.
     *
     * <p><b>Without cost since 29.08.</b> Each of these kinds cost either one
     * channel or none; channels no longer exist. What remains is the kind
     * itself — the traversal must know which list a find belongs in.
     */
    private enum Consumer {
        CONNECTOR,
        DRIVE,
        RACK,
        FABRICATOR,
        DISPLAY,
        MAST
    }

    /**
     * Where a device counts when it occupies more than one block.
     *
     * <p>The server rack is two blocks tall and still one device: one channel,
     * one BlockEntity, one entry in the list. Whoever cables up the top cables
     * up the same rack — which is why the upper half is remapped here onto the
     * lower one, instead of counting a second time.
     *
     * <p>A face gets only one connector. Everything else is a whole block:
     * whoever cables up a drive from two sides has one drive and not two.
     */
    private static DevicePos deviceAt(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof RackBlock) {
            return DevicePos.of(RackBlock.baseOf(state, pos));
        }
        return DevicePos.of(pos);
    }

    /**
     * The faces of a cable block on which a connector sits.
     *
     * <p>Empty for every ordinary cable — and that is the normal case, which
     * the search runs through a thousand times over.
     */
    private static List<Direction> partsAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos)
                instanceof dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity bus
                ? List.copyOf(bus.parts().keySet()) : List.of();
    }

    private static Consumer consumerAt(BlockState state) {
        // A connector no longer appears here: it is not a block but a part on
        // a cable face, and the search finds that in the cable branch.
        if (state.getBlock() instanceof DriveBlock) {
            return Consumer.DRIVE;
        }
        if (state.getBlock() instanceof RackBlock) {
            return Consumer.RACK;
        }
        if (state.getBlock() instanceof FabricatorBlock) {
            return Consumer.FABRICATOR;
        }
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.MastBlock) {
            return Consumer.MAST;
        }
        if (state.getBlock() instanceof DisplayBlock) {
            return Consumer.DISPLAY;
        }
        return null;
    }

    /**
     * Steps into a cable block — but only if its colour matches the one you
     * come from.
     */
    private static Node visitCable(Level level, BlockPos pos, Node from,
                                   Map<Node, Node> parents,
                                   Deque<Node> queue, Set<BlockPos> cables) {
        CableColour colour = CableBlock.colourOf(level.getBlockState(pos));
        if (!from.colour().connectsTo(colour)) {
            return null;
        }
        Node node = new Node(pos.immutable(), colour);
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
            cables.add(pos.immutable());
        }
        // Even an already-visited cable may contribute its parts: the node is
        // the same, and their path to the controller runs through it.
        return node;
    }

    /**
     * Jumps through a quantum bridge to its counterpart.
     *
     * <p>The bridge is a piece of line that lies in two places. Whoever enters
     * it stands afterward at the other end — with the colour they reached it
     * with, because a line does not change its colour along the way.
     *
     * <p><b>If the counterpart is not loaded, the path ends here.</b> The same
     * rule as with the transmitter mast: {@code getBlockEntity} would otherwise
     * load it in, and this question comes up on every rebuild of the network.
     *
     * <p>The ping-pong between the two ends is caught by {@code parents}: a
     * node that already has a path gets no second one.
     */
    private static void visitBridge(Level level, BlockPos pos, Node from,
                                    Map<Node, Node> parents, Deque<Node> queue,
                                    Set<BlockPos> bridges) {
        Node here = new Node(pos.immutable(), from.colour());
        if (!parents.containsKey(here)) {
            parents.put(here, from);
            bridges.add(pos.immutable());
        }
        BlockPos partner = dev.devpanda.factorynetwork.network.BridgeRegistry
                .partnerOf(level, pos);
        if (partner == null || !level.isLoaded(partner)) {
            return;
        }
        Node there = new Node(partner.immutable(), from.colour());
        if (!parents.containsKey(there)) {
            parents.put(there, here);
            queue.add(there);
            bridges.add(partner.immutable());
        }
    }

    /**
     * Passes through a display.
     *
     * <p>A display carries the strand onward, with the colour it was reached
     * with. It is thus a piece of the same line: a red wall stays red, and two
     * differently coloured networks do not grow together across a display.
     *
     * <p>It carries no channel limit of its own in doing so — {@code capacityAt}
     * returns unlimited for everything that is not a cable. What a wall costs
     * is limited by the cable it hangs on, and that is the place where you
     * check it, too.
     */
    private static void visitDisplay(BlockPos pos, Node from, Map<Node, Node> parents,
                                     Deque<Node> queue) {
        Node node = new Node(pos.immutable(), from.colour());
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
        }
    }

    /**
     * Steps into a router — on the lane that the touched side carries.
     *
     * <p>The touched side is the one facing the path, i.e. the opposite of the
     * direction you went in. If it is cut off, the branch ends; that is the
     * whole purpose of a cut-off side.
     */
    private static void visitRouter(Level level, BlockPos pos, Direction direction, Node from,
                                    Map<Node, Node> parents, Deque<Node> queue,
                                    Set<BlockPos> routers) {
        Direction eintritt = direction.getOpposite();
        int lane = RouterBlockEntity.laneAt(level, pos, eintritt);
        if (lane == RouterBlockEntity.OFF) {
            return;
        }
        // <b>The router filters, it does not mix.</b> Until 29.08. the node
        // was colour-neutral: whatever came together on a lane counted as
        // connected — two separate subnetworks grew together over it.
        //
        // Now the node carries a colour. A side without a filter passes on the
        // colour it arrived with; one with a filter lets only that one
        // through. So two colours meet in the same block without touching each
        // other — the fiber-optic picture.
        CableColour filter = RouterBlockEntity.filterAt(level, pos, eintritt);
        if (filter != null && !filter.connectsTo(from.colour())) {
            return;
        }
        // <b>A router is one node, not four.</b> Previously the node carried
        // the lane number of the entry side — and thus never reached out to a
        // side with a different number. Four lanes were four routers in the
        // same block.
        //
        // Now it is a passage, and the filters sit on its sides: what may
        // enter is decided by the entry; what may leave, by the exit. A red
        // exit lets only red out.
        //
        // The node's colour is the one it arrived with — the filter decides
        // whether something may pass through, not what it is afterward.
        Node node = new Node(pos.immutable(), from.colour());
        routers.add(pos.immutable());
        if (!parents.containsKey(node)) {
            parents.put(node, from);
            queue.add(node);
        }
    }

    /**
     * May this colour leave here?
     *
     * <p>The counter-check to entry: the router has one filter per side, and
     * both directions count. Without this question a red exit would be a label
     * with no effect — out would come whatever came in.
     */
    private static boolean mayLeaveRouter(Level level, BlockPos pos, Direction side,
                                          CableColour colour) {
        if (RouterBlockEntity.laneAt(level, pos, side) == RouterBlockEntity.OFF) {
            return false;
        }
        CableColour filter = RouterBlockEntity.filterAt(level, pos, side);
        if (filter == null) {
            return true;
        }
        // <b>The colour of the cable behind it decides, not that of the strand
        // in front.</b> A red exit leads into a red cable; if a blue one lies
        // there, nothing leaves.
        //
        // connectsTo alone was not enough: neutral connects to everything, and
        // a neutral cable would pass through every filter — a filter that
        // everything passes is none.
        CableColour dahinter = CableBlock.colourOf(
                level.getBlockState(pos.relative(side)));
        return filter == dahinter;
    }

    /**
     * Assigns their channels to the devices.
     *
     * <p>Runs only once the search is through — before that you do not know
     * over which strands a device is reachable at all. <b>A device takes the
     * first strand whose path still has room</b>, not simply the first one
     * found: otherwise a device would stay starved while a free strand lies in
     * the same block. That is exactly what "channels per cable, not per
     * bundle" means.
     *
     * <p>The order is that of the search, i.e. by distance: with scarce
     * channels the nearer device wins.
     */
    private static void assignPaths(Level level, Map<DevicePos, List<Node>> reachable,
                                       Map<DevicePos, Consumer> kinds,
                                    Map<Node, Node> parents,
                                       Map<String, List<DevicePos>> connectors,
                                       List<DevicePos> unnamed,
                                       Map<DevicePos, List<Node>> paths,
                                       List<BlockPos> displays, List<BlockPos> drives,
                                       List<BlockPos> racks, List<BlockPos> fabricators,
                                       List<BlockPos> masts, GatewayRegions regions) {
        for (Map.Entry<DevicePos, List<Node>> entry : reachable.entrySet()) {
            DevicePos device = entry.getKey();
            BlockPos pos = device.pos();
            Consumer kind = kinds.get(device);
            // Via Connectors and not via the BlockEntity: a connector sits
            // either alone in a connector block or on a face of a cable. Which
            // build form is no concern of the assignment — the face is.
            dev.devpanda.factorynetwork.block.entity.ConnectorPart connector =
                    device.side() == null ? null
                            : dev.devpanda.factorynetwork.block.entity.Connectors
                                    .at(level, pos, device.side());
            if (kind == null || (kind == Consumer.CONNECTOR && connector == null)) {
                continue;
            }
            // <b>The shortest path, without a capacity question.</b> Until
            // 29.08. a check stood here: does this device still fit on this
            // strand, or must it move aside? It fell along with the channels.
            //
            // What remains is the choice of path itself — without a path there
            // is no "hangs on the network". The first one found is the
            // shortest: the search runs breadth-first.
            List<Node> chosen = entry.getValue().isEmpty() ? null
                    : pathOf(level, entry.getValue().get(0), parents);
            if (chosen == null) {
                continue;
            }
            paths.put(device, chosen);

            switch (kind) {
                case DRIVE -> drives.add(pos);
                case RACK -> racks.add(pos);
                case FABRICATOR -> fabricators.add(pos);
                case DISPLAY -> displays.add(pos);
                case MAST -> masts.add(pos);
                case CONNECTOR -> {
                    // The installation sits at the place and not at the face:
                    // a gateway names what hangs around it, and a cable block
                    // hangs there with all its connectors.
                    String label = effectiveLabel(connector.label(), regions.instanceAt(pos));
                    if (label == null || label.isBlank()) {
                        unnamed.add(device);
                    } else {
                        // Do not overwrite: two connectors with the same name
                        // are an error, not a precedence.
                        connectors.computeIfAbsent(label, key -> new ArrayList<>()).add(device);
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * The name a device goes by in the network.
     *
     * <p><b>The label wins.</b> If a slash appears in it, the installation is
     * already stated, and a gateway changes nothing about that — otherwise a
     * placed block would silently shift what a program says about a device.
     *
     * <p>Otherwise the gateway puts its name in front. For everything behind
     * it the device is thus {@code werk_1/eingang}, without it being written
     * on a single connector: the interpreter, the installation detection and
     * both editors see the same name as before.
     */
    static String effectiveLabel(String label, String instance) {
        if (label == null || label.isBlank() || instance == null || instance.isBlank()) {
            return label;
        }
        return label.indexOf(dev.devpanda.factorynetwork.runtime
                .MultiblockInstances.SEPARATOR) >= 0
                ? label
                : instance + dev.devpanda.factorynetwork.runtime
                        .MultiblockInstances.SEPARATOR + label;
    }

    /** A device's path to the controller, as a list of its cable strands. */
    private static List<Node> pathOf(Level level, Node from, Map<Node, Node> parents) {
        List<Node> path = new ArrayList<>();
        for (Node node = from; node != null; node = parents.get(node)) {
            // Whatever costs bandwidth along the path belongs in here. If the
            // router were left out, a junction would be unlimited, and a
            // network behind it would suddenly have no limit any more.
            //
            // <b>The controller has been in here since 30.08.</b>, and it is
            // the most important entry: it lies on every path, so all workers
            // share its bandwidth. That is exactly what is meant by "a network
            // is only as good as its weakest link".
            var state = level.getBlockState(node.pos());
            var block = state.getBlock();
            if ((block instanceof CableBlock
                    && dev.devpanda.factorynetwork.block.CableBlock.carries(state))
                    || block instanceof RouterBlock
                    || block instanceof dev.devpanda.factorynetwork.block.ControllerBlock
                    || block instanceof dev.devpanda.factorynetwork.block.BridgeBlock
                    || block instanceof dev.devpanda.factorynetwork.block.GatewayBlock) {
                path.add(node);
            }
        }
        return path;
    }

    /**
     * Devices claimed by two gateways.
     *
     * <p>They belong to no installation. The <i>Network</i> tab names them,
     * instead of silently choosing one of the two.
     */
    public Set<BlockPos> contested() {
        return contested;
    }

    /** The routers on the network. */
    public Set<BlockPos> routers() {
        return routers;
    }


    /**
     * How much is still free at this spot.
     *
     * <p>Needs the world, because the capacity lives at the cable. Without it,
     * only the assumption remains that it is an ordinary one — which for a
     * dense one would be off by a factor of four.
     */
    public int throughputAt(Level level, BlockPos pos) {
        return Bandwidth.at(level, pos);
    }

    /**
     * Which cables this device works over.
     *
     * <p>The path to the controller, spot by spot. The throughput hangs on it:
     * what a worker moves occupies every piece of the path.
     */
    public List<Node> pathTo(DevicePos device) {
        return paths.getOrDefault(device, List.of());
    }

    /**
     * The weakest point on this device's path, per tick.
     *
     * <p>A path is as good as its tightest piece: a dense cable behind an
     * ordinary one gains nothing.
     */
    public int throughputTo(Level level, DevicePos device) {
        int least = Bandwidth.UNLIMITED;
        for (Node node : pathTo(device)) {
            least = Math.min(least, Bandwidth.at(level, node.pos()));
        }
        return least;
    }

    /**
     * The position of a connector — empty if it does not exist <b>or</b> if
     * the name is assigned twice. An ambiguous name must not silently point to
     * one of the two.
     */
    public Optional<DevicePos> connector(String name) {
        List<DevicePos> found = connectorsByName.get(name);
        return found != null && found.size() == 1
                ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** Is this name assigned more than once? */
    public boolean isAmbiguous(String name) {
        List<DevicePos> found = connectorsByName.get(name);
        return found != null && found.size() > 1;
    }

    /** All names that are assigned more than once. */
    public List<String> ambiguousNames() {
        return connectorsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** All spots for a name — more than one in the case of a conflict. */
    public List<DevicePos> positionsOf(String name) {
        return connectorsByName.getOrDefault(name, List.of());
    }

    /** Is this name already assigned in the network? */
    public boolean isTaken(String name) {
        return connectorsByName.containsKey(name);
    }

    public Set<String> connectorNames() {
        return connectorsByName.keySet();
    }

    /** Only the unambiguous connectors — the ones you can work with. */
    public Map<String, DevicePos> connectors() {
        Map<String, DevicePos> unique = new LinkedHashMap<>();
        connectorsByName.forEach((name, found) -> {
            if (found.size() == 1) {
                unique.put(name, found.get(0));
            }
        });
        return unique;
    }

    /** Connectors without a name — in the network, but not addressable. */
    public List<DevicePos> unnamedConnectors() {
        return unnamed;
    }

    /** All connectors in the network, named or not. */
    public int connectorCount() {
        return connectorsByName.values().stream().mapToInt(List::size).sum() + unnamed.size();
    }

    /**
     * Everything in the network that is not a cable.
     *
     * <p><b>A device is more than a connector.</b> The tooltip on the
     * controller wrote "Devices" and counted {@link #connectorCount()} — a
     * network of server rack, drive, router and display wall reported "0
     * devices". That was right for the code, which can only address
     * connectors, but not for the question the tooltip answers: how large is
     * this network.
     *
     * <p>The ones without a free channel count too. They stand in the network,
     * they draw energy, they are only unreachable — and that one is missing is
     * said by its own line below.
     */
    public int deviceCount() {
        return connectorCount() + drives.size() + racks.size() + displays.size()
                + routers.size();
    }

    /** Displays on the network. */
    public List<BlockPos> displays() {
        return displays;
    }

    /** Does this spot belong to this network — as cable, device or display? */
    public boolean contains(BlockPos pos) {
        return cables.contains(pos)
                || racks.contains(pos)
                // The rack stands in the list only with its lower half; the
                // upper one belongs just as much.
                || racks.contains(pos.below())
                || drives.contains(pos)
                || routers.contains(pos)
                || displays.contains(pos)
                // What the graph collects as a consumer belongs here too. If
                // one were missing here, it would be in the network but no one
                // would find it: ControllerRegistry.owning goes through this
                // question, and on it hang the registration of a remote
                // device, the opening of the window and every display. In the
                // game the mast therefore looked as if it hung on nothing at
                // all.
                || masts.contains(pos)
                || fabricators.contains(pos)
                || bridges.contains(pos)
                // For the connectors only the place counts: the question is
                // whether this block belongs to the network, not which of its
                // faces.
                || atPos(unnamed, pos)
                || connectorsByName.values().stream()
                        .anyMatch(found -> atPos(found, pos));
    }

    private static boolean atPos(List<DevicePos> devices, BlockPos pos) {
        return devices.stream().anyMatch(device -> device.pos().equals(pos));
    }

    public int cableCount() {
        return cables.size();
    }

    /** Was the build aborted because the network is too large? */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Finds names that resemble a sought one — for the message "Unknown
     * connector `cruhser_1` — did you mean `crusher_1`?".
     */
    public Optional<String> closestName(String wanted) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : connectorsByName.keySet()) {
            int distance = NameDistance.between(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // When the distance is too large, a suggestion is more confusing than helpful.
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }

    @Override
    public String toString() {
        return "FactoryGraph[" + connectorsByName.size() + " Connectoren, "
                + cables.size() + " Kabel]";
    }
}
