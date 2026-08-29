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
        // Gezeichnet wird an Blöcken: Zwei Anschlüsse an einem Kabelblock
        // stehen an derselben Stelle im Raum, und der Punkt dort gilt für
        // beide.
        Set<BlockPos> unnamed = places(graph.unnamedConnectors());

        // Doppelt vergebene Namen: Alle Stellen dazu sind unbrauchbar, nicht
        // nur die zweite — deshalb zählen sie alle als solche.
        Set<BlockPos> duplicates = new HashSet<>();
        for (String name : graph.ambiguousNames()) {
            duplicates.addAll(places(graph.positionsOf(name)));
        }

        nodes.add(new AnalyserData.Node(controller.getBlockPos(),
                AnalyserData.NodeState.CONTROLLER, ""));

        // <b>Ein Kabelblock ist ein Punkt</b>, auch wenn sechs Anschlüsse
        // daran hängen: Zwei Knoten an derselben Stelle hießen zwei
        // Beschriftungen übereinander, und lesbar wäre keine davon. Der
        // Punkt nennt deshalb alle Namen, die dort sitzen.
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
        // Laufwerke, Schränke und Kreuzungen gehören zum Netz und waren
        // trotzdem unsichtbar. Wer sucht, warum nichts lagert oder nichts
        // rechnet, sucht genau danach.
        for (BlockPos pos : graph.drives()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.DRIVE, ""));
        }
        for (BlockPos pos : graph.racks()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.RACK, ""));
        }
        for (BlockPos pos : graph.routers()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.ROUTER, ""));
        }
        // Der Anbau sieht aus wie ein Block neben dem Controller. Ob er
        // wirklich dazugehört — ob er den Controller berührt —, sieht man
        // ihm nicht an; hier schon.
        for (BlockPos pos : graph.extensions()) {
            nodes.add(new AnalyserData.Node(pos, AnalyserData.NodeState.EXTENSION, ""));
        }
        // Geräte ohne Kanal stehen nicht in der Namensliste, wenn sie gar

        int tight = 0;
        int full = 0;
        List<AnalyserData.Link> links = new ArrayList<>();
        for (FactoryGraph.Edge edge : graph.edges()) {
            // <b>Was hier steht, ist noch keine Auslastung.</b> Seit dem
            // 29.08. begrenzt Durchsatz statt Kanälen, und wie viel
            // tatsächlich fließt, weiß erst die Laufzeit — Aufgabe 3 in
            // plan-durchsatz-statt-kanaele.md.
            //
            // Bis dahin nennt der Analysator die Kapazität und behauptet
            // keine Enge: Eine erfundene Zahl wäre schlimmer als keine.
            int capacity = dev.devpanda.factorynetwork.network.Throughput.at(
                    controller.getLevel(), edge.to().pos());
            links.add(new AnalyserData.Link(edge.from().pos(), edge.to().pos(),
                    AnalyserData.LinkState.FREE, 0, capacity));
        }

        AnalyserData.Summary summary = new AnalyserData.Summary(
                graph.connectorNames().size(), graph.cableCount(),
                0, unnamed.size(), duplicates.size(), tight, full);
        return new AnalyserData(List.copyOf(nodes), List.copyOf(links), summary);
    }

    /**
     * Die Blöcke, an denen diese Geräte sitzen.
     *
     * <p>Weniger als Geräte, sobald zwei Anschlüsse an einem Kabelblock
     * hängen — für eine Zeichnung im Raum ist das richtig: Dort steht ein
     * Block.
     */
    private static Set<BlockPos> places(
            java.util.List<dev.devpanda.factorynetwork.network.DevicePos> devices) {
        Set<BlockPos> found = new HashSet<>();
        devices.forEach(device -> found.add(device.pos()));
        return found;
    }
}
