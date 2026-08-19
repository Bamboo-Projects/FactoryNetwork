package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private final Map<String, BlockPos> connectorsByName;
    private final Set<BlockPos> cables;
    private final boolean truncated;

    private FactoryGraph(Map<String, BlockPos> connectorsByName, Set<BlockPos> cables,
                         boolean truncated) {
        this.connectorsByName = connectorsByName;
        this.cables = cables;
        this.truncated = truncated;
    }

    public static FactoryGraph empty() {
        return new FactoryGraph(Map.of(), Set.of(), false);
    }

    /** Baut den Graphen ausgehend vom Controller auf. */
    public static FactoryGraph build(Level level, BlockPos controller) {
        Map<String, BlockPos> connectors = new LinkedHashMap<>();
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
                    cables.add(next.immutable());
                    queue.add(next.immutable());
                } else if (state.getBlock() instanceof ConnectorBlock) {
                    BlockEntity entity = level.getBlockEntity(next);
                    if (entity instanceof ConnectorBlockEntity connector) {
                        String label = connector.label();
                        if (label != null && !label.isBlank()) {
                            connectors.put(label, next.immutable());
                        }
                    }
                    // Ein Connector leitet nicht weiter: Er ist ein Endpunkt.
                }
            }
        }
        return new FactoryGraph(Map.copyOf(connectors), Set.copyOf(cables), truncated);
    }

    public Optional<BlockPos> connector(String name) {
        return Optional.ofNullable(connectorsByName.get(name));
    }

    public Set<String> connectorNames() {
        return connectorsByName.keySet();
    }

    public Map<String, BlockPos> connectors() {
        return connectorsByName;
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
            int distance = editDistance(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        // Bei zu großem Abstand ist ein Vorschlag eher verwirrend als hilfreich.
        int allowed = Math.max(1, wanted.length() / 3);
        return bestDistance <= allowed ? Optional.ofNullable(best) : Optional.empty();
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    @Override
    public String toString() {
        return "FactoryGraph[" + connectorsByName.size() + " Connectoren, "
                + cables.size() + " Kabel]";
    }
}
