package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.TrafficPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * What the network last moved — the state on the client.
 *
 * <p>Like {@code ClientNetworkState}: the server sends, the client holds the
 * last state, and the window draws it. Nothing is computed here — a client
 * that keeps its own tally has gaps as soon as the window is closed.
 */
public final class ClientTraffic {

    private static List<Integer> perSecond = new ArrayList<>();
    private static List<TrafficPacket.Consumer> top = new ArrayList<>();
    private static long total;
    private static int capacity;
    private static java.util.Map<String, Integer> costs = java.util.Map.of();

    public static void accept(TrafficPacket packet) {
        perSecond = List.copyOf(packet.perSecond());
        top = List.copyOf(packet.top());
        total = packet.total();
        capacity = packet.capacity();
        java.util.Map<String, Integer> next = new java.util.HashMap<>();
        packet.costs().forEach(cost -> next.put(cost.name(), cost.micros()));
        costs = java.util.Map.copyOf(next);
    }

    /** What a worker cost in the last second, in microseconds per tick, or -1. */
    public static int costOf(String worker) {
        return costs.getOrDefault(worker, -1);
    }

    /** {@code 0,08 ms} — the cost as the profiler writes it. */
    public static String costText(int micros) {
        return String.format(java.util.Locale.GERMANY, "%.2f ms", micros / 1000.0);
    }

    /** Green below 0.05 ms, amber below 0.3 ms, red above: the same three as everywhere. */
    public static int costColour(int micros) {
        return micros < 50 ? 0xFF78DC8C : micros < 300 ? 0xFFE8AC3E : 0xFFE88388;
    }

    /** The history, oldest point first, in bytes per second. */
    public static List<Integer> perSecond() {
        return perSecond;
    }

    /** The largest consumers, descending. */
    public static List<TrafficPacket.Consumer> top() {
        return top;
    }

    /** What has been moved in total since the network started. */
    public static long total() {
        return total;
    }

    /**
     * What the controller lets through per tick.
     *
     * <p>Zero as long as no packet has come — then the header shows the bare
     * number instead of a utilization of zero.
     */
    public static int capacity() {
        return capacity;
    }

    /**
     * The peak of the history.
     *
     * <p>A chart needs a scale, and a fixed scale would be a flat line at the
     * bottom for a quiet network. At least one kilobyte, so an empty network
     * does not divide zero by zero.
     */
    public static int peak() {
        return Math.max(1000, perSecond.stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private ClientTraffic() {
    }
}
