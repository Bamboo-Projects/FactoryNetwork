package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.network.FactoryGraph.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wie viel in diesem Tick schon über jedes Kabel gegangen ist.
 *
 * <p><b>Das ist die Stelle, an der der Durchsatz überhaupt etwas tut.</b> Die
 * Zahlen in {@link Throughput} sagen, was ein Kabel trägt; hier steht, was es
 * heute schon getragen hat.
 *
 * <p><b>Und hier wird es eng, nicht tot.</b> Ein Worker, dessen Weg voll ist,
 * bewegt weniger — er fällt nicht aus. Ein Netz an der Grenze arbeitet
 * weiter, nur zäher. Das ist der ganze Unterschied zu den Kanälen, die es bis
 * zum 29.08. gab: Dort hieß voll, dass ein Gerät stumm blieb.
 *
 * <p><b>Je Controller und je Tick.</b> Zwei Netze teilen sich kein Budget,
 * auch wenn sie sich ein Kabel teilen — dieselbe Trennung, die schon für die
 * Kanäle galt: Die Zahlen gehören dem Graphen, nicht den Blöcken.
 */
public final class TickBudget {

    private final Map<Node, Integer> used = new HashMap<>();

    /**
     * Was ein Weg jetzt noch hergibt.
     *
     * <p>Der schwächste Punkt entscheidet: Ein dichtes Kabel hinter einem
     * gewöhnlichen bringt nichts, denn die Ware muss durch beide.
     */
    public int free(net.minecraft.world.level.Level level, List<Node> path) {
        int least = Throughput.UNLIMITED;
        for (Node node : path) {
            int here = Throughput.at(level, node.pos()) - used.getOrDefault(node, 0);
            least = Math.min(least, Math.max(0, here));
        }
        return least;
    }

    /**
     * Bucht ab, was gerade über diesen Weg gegangen ist.
     *
     * <p><b>Nach dem Bewegen und mit der wirklichen Menge</b>, nicht mit der
     * geplanten: Ein Worker, der 64 wollte und 3 bekam, hat das Kabel nicht
     * mit 64 belegt.
     */
    public void spend(List<Node> path, int amount) {
        if (amount <= 0) {
            return;
        }
        for (Node node : path) {
            used.merge(node, amount, Integer::sum);
        }
    }

    /** Ein neuer Tick fängt bei null an. */
    public void reset() {
        used.clear();
    }

    /** Was über diese Stelle in diesem Tick schon ging — für die Anzeige. */
    public int usedAt(Node node) {
        return used.getOrDefault(node, 0);
    }

    private TickBudget() {
    }

    public static TickBudget create() {
        return new TickBudget();
    }
}
