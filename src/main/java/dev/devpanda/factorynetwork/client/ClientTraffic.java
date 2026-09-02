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

    public static void accept(TrafficPacket packet) {
        perSecond = List.copyOf(packet.perSecond());
        top = List.copyOf(packet.top());
        total = packet.total();
        capacity = packet.capacity();
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
