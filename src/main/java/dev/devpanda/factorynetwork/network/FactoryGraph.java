package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Das Netzwerk eines Controllers: alle Connectoren, die über Kabel mit ihm
 * verbunden sind.
 *
 * <p>Der Graph wird nicht dauernd gepflegt, sondern neu aufgebaut, wenn sich
 * etwas geändert hat. Das ist für die Größenordnung, um die es geht — einige
 * hundert Blöcke — deutlich einfacher als eine mitwachsende Struktur, und ein
 * Kabel wird selten gesetzt.
 *
 * <p>Nicht geladene Chunks werden übersprungen. Ein Connector dort ist nicht
 * weg, sondern vorübergehend nicht erreichbar — der Unterschied steht in
 * {@code sprache.md}, Abschnitt 14.
 */
public final class FactoryGraph {

    /** Weiter als das sucht der Aufbau nicht; schützt vor Endlosnetzen. */
    private static final int MAX_NODES = 4096;

    /**
     * Name auf Positionen. Mehr als eine Position bedeutet: Der Name ist
     * doppelt vergeben und damit unbrauchbar — siehe {@link #isAmbiguous}.
     */
    private final Map<String, List<BlockPos>> connectorsByName;
    /**
     * Connectoren ohne Namen.
     *
     * <p>Sie lassen sich nicht ansprechen — dafür braucht es einen Namen —,
     * aber sie hängen im Netz, und der Spieler muss das sehen. Sie hier
     * wegzulassen hieß, dass der Controller „0 Connectoren" meldete, während
     * drei danebenhingen.
     */
    private final List<BlockPos> unnamed;
    /** Geräte, die im Netz hängen, aber keinen freien Kanal bekommen haben. */
    private final List<BlockPos> starved;
    private final Set<BlockPos> cables;
    /** Wie viele Kanäle jeder Kabelstrang trägt. */
    private final Map<Node, Integer> channelLoad;
    private final boolean truncated;

    private FactoryGraph(Map<String, List<BlockPos>> connectorsByName, List<BlockPos> unnamed,
                         List<BlockPos> starved, Set<BlockPos> cables,
                         Map<Node, Integer> channelLoad, boolean truncated) {
        this.connectorsByName = connectorsByName;
        this.unnamed = unnamed;
        this.starved = starved;
        this.channelLoad = channelLoad;
        this.cables = cables;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), List.of(), List.of(), Set.of(),
                Map.of(), false);
    }

    /**
     * Ein Knoten der Suche: eine Stelle und ein Strang.
     *
     * <p>Ein Kabelblock ist nicht ein Knoten, sondern bis zu vier — sonst
     * liefe ein grüner Strang über einen Block, in dem auch ein roter liegt,
     * und beide wären plötzlich verbunden.
     */
    public record Node(BlockPos pos, CableColour colour) {
    }

    /**
     * Wie viele Kanäle ein Kabelstrang trägt.
     *
     * <p>Ein Strang ist ein Bündel von acht Drähten. Jedes Gerät zieht auf
     * seinem ganzen Weg zum Controller einen davon ab — weiter hinten fehlt
     * er dann. Das Vorbild ist Applied Energistics, mit einem Unterschied:
     * Bei uns zählt der <b>Strang</b>, nicht der Block. Vier Stränge in einem
     * Block tragen vier mal acht, weil sie vier getrennte Netze sind.
     */
    public static final int CHANNELS_PER_STRAND = 8;

    /**
     * Baut den Graphen ausgehend vom Controller auf.
     *
     * <p>Die Breitensuche geht in fester Richtungsfolge — {@code Direction}
     * in seiner eigenen Reihenfolge — und sammelt Geräte nach Entfernung.
     * <b>Damit gewinnt bei knappen Kanälen immer das nähere Gerät</b>, und bei
     * gleicher Entfernung das in der früheren Richtung. Diese Regel muss
     * feststehen und erklärbar sein: Sonst ist „warum ist dieses Gerät
     * offline" nicht zu beantworten.
     */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, List<BlockPos>> connectors = new LinkedHashMap<>();
        List<BlockPos> unnamed = new ArrayList<>();
        List<BlockPos> starved = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Map<Node, Integer> load = new HashMap<>();
        Map<Node, Node> parents = new HashMap<>();
        Set<BlockPos> visitedDevices = new HashSet<>();
        // Gerät auf die Kabelstränge, über die es erreichbar ist — in der
        // Reihenfolge, in der die Suche sie gefunden hat.
        Map<BlockPos, List<Node>> reachable = new LinkedHashMap<>();
        Deque<Node> queue = new ArrayDeque<>();

        // Der Controller selbst ist farbneutral: Von ihm gehen alle Stränge
        // aus, die an ihm hängen.
        Node root = new Node(controller, CableColour.NONE);
        queue.add(root);
        parents.put(root, null);
        boolean truncated = false;

        while (!queue.isEmpty()) {
            if (parents.size() > MAX_NODES) {
                truncated = true;
                break;
            }
            Node current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.pos().relative(direction);
                if (!level.isLoaded(next)) {
                    // Ein nicht geladener Chunk beendet den Zweig, ohne Fehler.
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CableBlock) {
                    visitCable(level, next, current, parents, queue, cables);
                } else if (state.getBlock() instanceof ConnectorBlock) {
                    // Noch nicht zuteilen: Ein Gerät kann an mehreren
                    // Strängen hängen, und welcher es trägt, entscheidet
                    // sich erst, wenn alle Wege bekannt sind.
                    reachable.computeIfAbsent(next.immutable(), key -> new ArrayList<>())
                            .add(current);
                    visitedDevices.add(next.immutable());
                }
            }
        }

        assignChannels(level, reachable, parents, load, connectors, unnamed, starved);

        Map<String, List<BlockPos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                List.copyOf(starved), Set.copyOf(cables), Map.copyOf(load), truncated);
    }

    /**
     * Geht in einen Kabelblock hinein — aber nur in die Stränge, die zur
     * Farbe passen, aus der man kommt.
     */
    private static void visitCable(Level level, BlockPos pos, Node from, Map<Node, Node> parents,
                                   Deque<Node> queue, Set<BlockPos> cables) {
        boolean entered = false;
        for (CableColour strand : CableBlock.strandsAt(level, pos)) {
            if (!from.colour().connectsTo(strand)) {
                continue;
            }
            Node node = new Node(pos.immutable(), strand);
            if (!parents.containsKey(node)) {
                parents.put(node, from);
                queue.add(node);
                entered = true;
            }
        }
        if (entered) {
            cables.add(pos.immutable());
        }
    }

    /**
     * Teilt den Geräten ihre Kanäle zu.
     *
     * <p>Läuft erst, wenn die Suche durch ist — vorher weiß man nicht, über
     * welche Stränge ein Gerät überhaupt erreichbar ist. <b>Ein Gerät nimmt
     * den ersten Strang, auf dessen Weg noch Platz ist</b>, nicht einfach den
     * erstgefundenen: Sonst bliebe ein Gerät hungrig, während im selben Block
     * ein freier Strang liegt. Genau das heißt „Kanäle je Kabel, nicht je
     * Bündel".
     *
     * <p>Die Reihenfolge ist die der Suche, also nach Entfernung: Bei knappen
     * Kanälen gewinnt das nähere Gerät.
     */
    private static void assignChannels(Level level, Map<BlockPos, List<Node>> reachable,
                                       Map<Node, Node> parents, Map<Node, Integer> load,
                                       Map<String, List<BlockPos>> connectors,
                                       List<BlockPos> unnamed, List<BlockPos> starved) {
        for (Map.Entry<BlockPos, List<Node>> entry : reachable.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!(level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            int cost = connector.channelCost();

            List<Node> chosen = null;
            for (Node entryPoint : entry.getValue()) {
                List<Node> path = pathOf(level, entryPoint, parents);
                boolean room = path.stream().allMatch(node ->
                        load.getOrDefault(node, 0) + cost <= CHANNELS_PER_STRAND);
                if (room) {
                    chosen = path;
                    break;
                }
            }
            if (chosen == null) {
                starved.add(pos);
                continue;
            }
            for (Node node : chosen) {
                load.merge(node, cost, Integer::sum);
            }

            String label = connector.label();
            if (label == null || label.isBlank()) {
                unnamed.add(pos);
            } else {
                // Nicht überschreiben: Zwei Connectoren mit demselben Namen
                // sind ein Fehler, kein Vorrang.
                connectors.computeIfAbsent(label, key -> new ArrayList<>()).add(pos);
            }
        }
    }

    /** Der Weg eines Geräts zum Controller, als Liste seiner Kabelstränge. */
    private static List<Node> pathOf(Level level, Node from, Map<Node, Node> parents) {
        List<Node> path = new ArrayList<>();
        for (Node node = from; node != null; node = parents.get(node)) {
            if (level.getBlockState(node.pos()).getBlock() instanceof CableBlock) {
                path.add(node);
            }
        }
        return path;
    }

    /**
     * Wie viele Kanäle ein Strang an dieser Stelle trägt.
     *
     * <p>Die Zahlen gehören dem Graphen, nicht den Blöcken. Ein Kabel kann zu
     * zwei Netzen gehören — schriebe jeder Controller seine Zahlen in die
     * BlockEntity, überschriebe einer den anderen.
     */
    public int channelLoad(BlockPos pos, CableColour colour) {
        return channelLoad.getOrDefault(new Node(pos, colour), 0);
    }

    public int channelsFree(BlockPos pos, CableColour colour) {
        return CHANNELS_PER_STRAND - channelLoad(pos, colour);
    }

    /** Geräte ohne freien Kanal — im Netz sichtbar, aber nicht ansprechbar. */
    public List<BlockPos> starvedConnectors() {
        return starved;
    }

    public boolean isStarved(BlockPos pos) {
        return starved.contains(pos);
    }

    /**
     * Die Position eines Connectors — leer, wenn es ihn nicht gibt <b>oder</b>
     * wenn der Name doppelt vergeben ist. Ein mehrdeutiger Name darf nicht
     * stillschweigend auf einen der beiden zeigen.
     */
    public Optional<BlockPos> connector(String name) {
        List<BlockPos> positions = connectorsByName.get(name);
        return positions != null && positions.size() == 1
                ? Optional.of(positions.get(0)) : Optional.empty();
    }

    /** Ist dieser Name mehr als einmal vergeben? */
    public boolean isAmbiguous(String name) {
        List<BlockPos> positions = connectorsByName.get(name);
        return positions != null && positions.size() > 1;
    }

    /** Alle Namen, die mehr als einmal vergeben sind. */
    public List<String> ambiguousNames() {
        return connectorsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Alle Positionen zu einem Namen — bei einem Konflikt mehr als eine. */
    public List<BlockPos> positionsOf(String name) {
        return connectorsByName.getOrDefault(name, List.of());
    }

    /** Ist dieser Name im Netz schon vergeben? */
    public boolean isTaken(String name) {
        return connectorsByName.containsKey(name);
    }

    public Set<String> connectorNames() {
        return connectorsByName.keySet();
    }

    /** Nur die eindeutigen Connectoren — die, mit denen sich arbeiten lässt. */
    public Map<String, BlockPos> connectors() {
        Map<String, BlockPos> unique = new LinkedHashMap<>();
        connectorsByName.forEach((name, positions) -> {
            if (positions.size() == 1) {
                unique.put(name, positions.get(0));
            }
        });
        return unique;
    }

    /** Connectoren ohne Namen — im Netz, aber nicht ansprechbar. */
    public List<BlockPos> unnamedConnectors() {
        return unnamed;
    }

    /** Alle Connectoren im Netz, benannt oder nicht. */
    public int connectorCount() {
        return connectorsByName.values().stream().mapToInt(List::size).sum() + unnamed.size();
    }

    public int cableCount() {
        return cables.size();
    }

    /** Wurde der Aufbau abgebrochen, weil das Netz zu groß ist? */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Findet Namen, die einem gesuchten ähneln — für die Meldung
     * „Unbekannter Connector `cruhser_1` — meintest du `crusher_1`?".
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
        // Bei zu großem Abstand ist ein Vorschlag eher verwirrend als hilfreich.
        return NameDistance.isCloseEnough(wanted, bestDistance)
                ? Optional.ofNullable(best) : Optional.empty();
    }

    @Override
    public String toString() {
        return "FactoryGraph[" + connectorsByName.size() + " Connectoren, "
                + cables.size() + " Kabel]";
    }
}
