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
    private final Set<BlockPos> cables;
    private final boolean truncated;

    private FactoryGraph(Map<String, List<BlockPos>> connectorsByName, List<BlockPos> unnamed,
                         Set<BlockPos> cables, boolean truncated) {
        this.connectorsByName = connectorsByName;
        this.unnamed = unnamed;
        this.cables = cables;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), List.of(), Set.of(), false);
    }

    /**
     * Ein Knoten der Suche: eine Stelle und ein Strang.
     *
     * <p>Ein Kabelblock ist nicht ein Knoten, sondern bis zu vier — sonst
     * liefe ein grüner Strang über einen Block, in dem auch ein roter liegt,
     * und beide wären plötzlich verbunden.
     */
    private record Node(BlockPos pos, CableColour colour) {
    }

    /** Baut den Graphen ausgehend vom Controller auf. */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, List<BlockPos>> connectors = new LinkedHashMap<>();
        List<BlockPos> unnamed = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<Node> seen = new HashSet<>();
        Set<BlockPos> visitedDevices = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();

        // Der Controller selbst ist farbneutral: Von ihm gehen alle Stränge
        // aus, die an ihm hängen.
        queue.add(new Node(controller, CableColour.NONE));
        seen.add(new Node(controller, CableColour.NONE));
        boolean truncated = false;

        while (!queue.isEmpty()) {
            if (seen.size() > MAX_NODES) {
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
                    visitCable(level, next, current, seen, queue, cables);
                } else if (state.getBlock() instanceof ConnectorBlock) {
                    if (visitedDevices.add(next.immutable())) {
                        collectConnector(level, next, connectors, unnamed);
                    }
                }
            }
        }

        Map<String, List<BlockPos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                Set.copyOf(cables), truncated);
    }

    /**
     * Geht in einen Kabelblock hinein — aber nur in die Stränge, die zur
     * Farbe passen, aus der man kommt.
     */
    private static void visitCable(Level level, BlockPos pos, Node from, Set<Node> seen,
                                   Deque<Node> queue, Set<BlockPos> cables) {
        boolean entered = false;
        for (CableColour strand : CableBlock.strandsAt(level, pos)) {
            if (!from.colour().connectsTo(strand)) {
                continue;
            }
            // Von der Standardfarbe aus behält der Strang seine eigene Farbe;
            // sonst liefe man über ein neutrales Kabel in jede Farbe hinein
            // und wieder heraus.
            Node node = new Node(pos.immutable(), strand);
            if (seen.add(node)) {
                queue.add(node);
                entered = true;
            }
        }
        if (entered) {
            cables.add(pos.immutable());
        }
    }

    /** Nimmt einen Connector auf — benannt oder nicht. */
    private static void collectConnector(Level level, BlockPos pos,
                                         Map<String, List<BlockPos>> connectors,
                                         List<BlockPos> unnamed) {
        if (!(level.getBlockEntity(pos) instanceof ConnectorBlockEntity connector)) {
            return;
        }
        String label = connector.label();
        if (label == null || label.isBlank()) {
            unnamed.add(pos.immutable());
            return;
        }
        // Nicht überschreiben: Zwei Connectoren mit demselben Namen sind ein
        // Fehler, kein Vorrang. Wer hier den letzten gewinnen ließe, machte
        // die Reihenfolge der Suche zur Bedeutung.
        connectors.computeIfAbsent(label, key -> new ArrayList<>()).add(pos.immutable());
    }

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
