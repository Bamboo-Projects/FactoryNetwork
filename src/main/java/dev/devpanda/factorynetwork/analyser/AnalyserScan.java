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
 * Liest aus einem Controller heraus, was der Analysator zeigen soll.
 *
 * <p>Das Netz ist bereits durchsucht — der Graph kennt Geräte, Kabel,
 * Kanallast und Verbindungen. Diese Klasse bewertet nur: Was ist in Ordnung,
 * was ist knapp, was klemmt. Nichts davon wird hier neu berechnet, sonst
 * liefen zwei Fassungen derselben Wahrheit nebeneinander.
 */
public final class AnalyserScan {

    private AnalyserScan() {
    }

    public static AnalyserData of(ControllerBlockEntity controller) {
        FactoryGraph graph = controller.graph();
        List<AnalyserData.Node> nodes = new ArrayList<>();
        Set<BlockPos> starved = new HashSet<>(graph.starvedConnectors());
        Set<BlockPos> unnamed = new HashSet<>(graph.unnamedConnectors());

        // Doppelt vergebene Namen: Alle Stellen dazu sind unbrauchbar, nicht
        // nur die zweite — deshalb zählen sie alle als solche.
        Set<BlockPos> duplicates = new HashSet<>();
        for (String name : graph.ambiguousNames()) {
            duplicates.addAll(graph.positionsOf(name));
        }

        nodes.add(new AnalyserData.Node(controller.getBlockPos(),
                AnalyserData.NodeState.CONTROLLER, ""));

        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            BlockPos pos = entry.getValue();
            AnalyserData.NodeState state = duplicates.contains(pos)
                    ? AnalyserData.NodeState.DUPLICATE
                    : starved.contains(pos) ? AnalyserData.NodeState.STARVED
                    : AnalyserData.NodeState.DEVICE;
            nodes.add(new AnalyserData.Node(pos, state, entry.getKey()));
        }
        for (BlockPos pos : unnamed) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.UNNAMED, ""));
        }
        for (BlockPos pos : graph.displays()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.DISPLAY, ""));
        }
        // Geräte ohne Kanal stehen nicht in der Namensliste, wenn sie gar
        // nicht erst aufgenommen wurden. Sie fehlen sonst genau dort, wo man
        // sie sucht.
        for (BlockPos pos : starved) {
            if (nodes.stream().noneMatch(node -> node.pos().equals(pos))) {
                nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.STARVED, ""));
            }
        }

        int tight = 0;
        int full = 0;
        List<AnalyserData.Link> links = new ArrayList<>();
        for (FactoryGraph.Edge edge : graph.edges()) {
            int load = graph.channelLoad(edge.to());
            // Die Kapazität steht an der Stelle, nicht an der Mod: ein dickes
            // Kabel und eine Bahn im Router tragen vierundsechzig, ein
            // gewöhnliches sechzehn. Mit der festen Zahl meldete der
            // Analysator jede dichte Strecke ab dem sechzehnten Kanal als
            // voll — und wies damit auf eine Enge hin, die es nicht gab.
            int capacity = FactoryGraph.capacityAt(controller.getLevel(), edge.to().pos());
            AnalyserData.LinkState state = load >= capacity ? AnalyserData.LinkState.FULL
                    : load * 4 >= capacity * 3 ? AnalyserData.LinkState.TIGHT
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
                starved.size(), unnamed.size(), duplicates.size(), tight, full);
        return new AnalyserData(List.copyOf(nodes), List.copyOf(links), summary);
    }
}
