package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
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

    /** Baut den Graphen ausgehend vom Controller auf. */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, List<BlockPos>> connectors = new LinkedHashMap<>();
        List<BlockPos> unnamed = new ArrayList<>();
        Set<BlockPos> cables = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        seen.add(controller);
        queue.add(controller);
        boolean truncated = false;

        while (!queue.isEmpty()) {
            if (seen.size() > MAX_NODES) {
                truncated = true;
                break;
            }
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!seen.add(next)) {
                    continue;
                }
                // Ein nicht geladener Chunk beendet den Zweig, ohne Fehler.
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CableBlock) {
                    // Die Farbe entscheidet auch hier: Was optisch nicht
                    // verbunden ist, darf auch im Netz nicht verbunden sein.
                    // Sonst liefe ein Strang sichtbar getrennt und wäre es
                    // doch nicht — der schlimmste Fall von beiden.
                    BlockState from = level.getBlockState(current);
                    if (from.getBlock() instanceof CableBlock
                            && !CableBlock.colourOf(from)
                                    .connectsTo(CableBlock.colourOf(state))) {
                        continue;
                    }
                    cables.add(next.immutable());
                    queue.add(next.immutable());
                } else if (state.getBlock() instanceof ConnectorBlock) {
                    BlockEntity entity = level.getBlockEntity(next);
                    if (entity instanceof ConnectorBlockEntity connector) {
                        String label = connector.label();
                        if (label != null && !label.isBlank()) {
                            // Nicht überschreiben: Zwei Connectoren mit
                            // demselben Namen sind ein Fehler, kein Vorrang.
                            // Wer hier den letzten gewinnen ließe, machte die
                            // Reihenfolge der Suche zur Bedeutung.
                            connectors.computeIfAbsent(label, key -> new ArrayList<>())
                                    .add(next.immutable());
                        } else {
                            unnamed.add(next.immutable());
                        }
                    }
                    // Ein Connector leitet nicht weiter: Er ist ein Endpunkt.
                }
            }
        }
        Map<String, List<BlockPos>> frozen = new LinkedHashMap<>();
        connectors.forEach((label, positions) -> frozen.put(label, List.copyOf(positions)));
        return new FactoryGraph(Map.copyOf(frozen), List.copyOf(unnamed),
                Set.copyOf(cables), truncated);
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
