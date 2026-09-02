package dev.devpanda.factorynetwork.analyser;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads out of a controller what the analyser should show.
 *
 * <p>The network has already been scanned — the graph knows devices, cables,
 * channel load and connections. This class only rates them: what is fine, what
 * is tight, what jams. None of it is recomputed here, or else two versions of
 * the same truth would run side by side.
 */
public final class AnalyserScan {

    private AnalyserScan() {
    }

    public static AnalyserData of(ControllerBlockEntity controller) {
        FactoryGraph graph = controller.graph();
        List<AnalyserData.Node> nodes = new ArrayList<>();
        // Drawing happens at blocks: two connectors on one cable block sit at
        // the same place in space, and the point there stands for both.
        Set<BlockPos> unnamed = places(graph.unnamedConnectors());

        // Names given out twice: every place with one is unusable, not just
        // the second — so they all count as such.
        Set<BlockPos> duplicates = new HashSet<>();
        for (String name : graph.ambiguousNames()) {
            duplicates.addAll(places(graph.positionsOf(name)));
        }

        nodes.add(new AnalyserData.Node(controller.getBlockPos(),
                AnalyserData.NodeState.CONTROLLER, ""));

        // <b>A cable block is one point</b>, even when six connectors hang off
        // it: two nodes at the same place would mean two labels on top of each
        // other, and neither of them would be legible. The point therefore
        // names every name that sits there.
        java.util.Map<BlockPos, List<String>> namesAt = new java.util.LinkedHashMap<>();
        graph.connectors().forEach((name, where) ->
                namesAt.computeIfAbsent(where.pos(), key -> new ArrayList<>()).add(name));
        for (Map.Entry<BlockPos, List<String>> entry : namesAt.entrySet()) {
            BlockPos pos = entry.getKey();
            AnalyserData.NodeState state = duplicates.contains(pos)
                    ? AnalyserData.NodeState.DUPLICATE
                    : AnalyserData.NodeState.DEVICE;
            nodes.add(new AnalyserData.Node(pos, state, String.join(", ", entry.getValue())));
        }
        for (BlockPos pos : unnamed) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.UNNAMED, ""));
        }
        for (BlockPos pos : graph.displays()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.DISPLAY, ""));
        }
        // Drives, racks and routers belong to the network and were invisible
        // all the same. Whoever is looking for why nothing is stored or
        // nothing is computed is looking for exactly these.
        for (BlockPos pos : graph.drives()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.DRIVE, ""));
        }
        for (BlockPos pos : graph.racks()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.RACK, ""));
        }
        for (BlockPos pos : graph.routers()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.ROUTER, ""));
        }
        // The extension looks like a block next to the controller. Whether it
        // really belongs — whether it touches the controller — you cannot tell
        // by looking at it; here you can.
        for (BlockPos pos : graph.extensions()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.EXTENSION, ""));
        }
        // Devices without a channel do not appear in the name list, if they even

        int tight = 0;
        int full = 0;
        List<AnalyserData.Link> links = new ArrayList<>();
        // Read once and not per link: the controller is the same for all of
        // them, and what it still has to give applies to every one of them.
        int controllerCapacity = controller.bandwidth();
        int controllerLeft = controllerCapacity - controller.bandwidthUsed();
        for (FactoryGraph.Edge edge : graph.edges()) {
            // <b>What went over this spot in the last tick.</b> Not how many
            // devices hang behind it — since 29 Aug it is the throughput that
            // counts, and only the runtime knows that.
            //
            // A snapshot from one tick, not an average: whoever wants to see
            // whether a wire is tight looks more than once. A smoothed value
            // would hide exactly the peaks one is looking for.
            int capacity = dev.devpanda.factorynetwork.network.Bandwidth.at(
                    controller.getLevel(), edge.to().pos());
            int load = controller.runtime().budget().usedAt(edge.to());
            // <b>And the path before it counts too.</b> A link over which
            // nothing more gets through, because the controller is brimful, is
            // tight — even when its own cable would have room. Without this the
            // analyser would report "free" for every link while nothing flows,
            // and one would look for the bottleneck in the wrong place.
            //
            // The capacity stays that of the cable: that is the truth about
            // this link. Only the rating looks further than the link itself.
            AnalyserData.LinkState state =
                    load >= capacity || controllerLeft <= 0 ? AnalyserData.LinkState.FULL
                    : load * 4 >= capacity * 3 || controllerLeft * 4 <= controllerCapacity
                            ? AnalyserData.LinkState.TIGHT
                    : AnalyserData.LinkState.FREE;
            if (state == AnalyserData.LinkState.FULL) {
                full++;
            } else if (state == AnalyserData.LinkState.TIGHT) {
                tight++;
            }
            links.add(new AnalyserData.Link(edge.from().pos(), edge.to().pos(),
                    state, load, capacity));
        }

        AnalyserData.Summary summary = new AnalyserData.Summary(
                graph.connectorNames().size(), graph.cableCount(),
                0, unnamed.size(), duplicates.size(), tight, full);
        return new AnalyserData(List.copyOf(nodes), List.copyOf(links), summary);
    }

    /**
     * The blocks at which these devices sit.
     *
     * <p>Fewer than devices as soon as two connectors hang off one cable
     * block — for a drawing in space that is right: there stands one block.
     */
    private static Set<BlockPos> places(
            java.util.List<dev.devpanda.factorynetwork.network.DevicePos> devices) {
        Set<BlockPos> found = new HashSet<>();
        devices.forEach(device -> found.add(device.pos()));
        return found;
    }
}
