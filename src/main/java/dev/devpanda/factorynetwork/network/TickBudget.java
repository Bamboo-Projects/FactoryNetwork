package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.network.FactoryGraph.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much has already gone over each cable in this tick.
 *
 * <p><b>This is where throughput actually does anything.</b> The numbers in
 * {@link Throughput} say what a cable carries; here is what it has already
 * carried today.
 *
 * <p><b>And here it gets tight, not dead.</b> A worker whose path is full
 * moves less — it does not drop out. A network at its limit keeps working,
 * just more sluggishly. That is the whole difference from the channels there
 * were until 29.08.: back then, full meant a device stayed silent.
 *
 * <p><b>Per controller and per tick.</b> Two networks do not share a budget,
 * even when they share a cable — the same separation that already held for the
 * channels: the numbers belong to the graph, not to the blocks.
 */
public final class TickBudget {

    private final Map<Node, Integer> used = new HashMap<>();

    /**
     * What a path still has to give right now.
     *
     * <p>The weakest point decides: a dense cable behind an ordinary one gains
     * nothing, since the goods have to pass through both.
     */
    public int free(net.minecraft.world.level.Level level, List<Node> path) {
        int least = Bandwidth.UNLIMITED;
        for (Node node : path) {
            int here = Bandwidth.at(level, node.pos()) - used.getOrDefault(node, 0);
            least = Math.min(least, Math.max(0, here));
        }
        return least;
    }

    /**
     * Charges off what has just gone over this path.
     *
     * <p><b>After the move and with the real amount</b>, not the planned one: a
     * worker that wanted 64 and got 3 did not tie up the cable with 64.
     */
    public void spend(List<Node> path, int amount) {
        if (amount <= 0) {
            return;
        }
        for (Node node : path) {
            used.merge(node, amount, Integer::sum);
        }
    }

    /** A new tick starts at zero. */
    public void reset() {
        used.clear();
    }

    /** What has already gone over this spot this tick — for display. */
    public int usedAt(Node node) {
        return used.getOrDefault(node, 0);
    }

    private TickBudget() {
    }

    public static TickBudget create() {
        return new TickBudget();
    }
}
