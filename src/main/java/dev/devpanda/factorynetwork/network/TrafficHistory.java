package dev.devpanda.factorynetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the network has moved over time.
 *
 * <p><b>Measured on the server, not in the window.</b> A history the client
 * records itself has gaps the moment someone closes the window — and that is
 * exactly when they later care why nothing ran overnight.
 *
 * <p><b>One sample per second, not per tick.</b> Twenty samples per second
 * would be twelve hundred in a minute: pointless for a chart two hundred
 * pixels wide, and expensive over the wire.
 *
 * <p><b>And the history has an end.</b> One that grows forever is a memory
 * leak with a chart.
 */
public class TrafficHistory {

    /** Five minutes of history — as far back as the question "what just happened" reaches. */
    public static final int SAMPLES = 300;

    /** A device and what it has moved. */
    public record Consumer(String name, long bytes) { }

    private final Deque<Integer> samples = new ArrayDeque<>();
    private final Map<String, Long> byDevice = new LinkedHashMap<>();

    private int currentSecond;
    private int ticks;
    private long total;

    /**
     * Records what a device has just moved.
     *
     * <p>The name and not the position: a chart that names coordinates does
     * not answer the question "what eats the most" — you would first have to
     * go there and look.
     */
    public void record(String device, int bytes) {
        if (bytes <= 0) {
            return;
        }
        currentSecond += bytes;
        total += bytes;
        byDevice.merge(device, (long) bytes, Long::sum);
    }

    /**
     * A tick passes.
     *
     * <p>After twenty of them a sample is written and the running second is
     * reset.
     */
    public void tick() {
        ticks++;
        if (ticks < Bandwidth.TICKS_PER_SECOND) {
            return;
        }
        ticks = 0;
        samples.addLast(currentSecond);
        currentSecond = 0;
        while (samples.size() > SAMPLES) {
            samples.removeFirst();
        }
    }

    /** The history, oldest sample first. */
    public List<Integer> perSecond() {
        return List.copyOf(samples);
    }

    /** What has been moved in total since the network started. */
    public long total() {
        return total;
    }

    /** What has been moved per device. */
    public Map<String, Long> byDevice() {
        return Map.copyOf(byDevice);
    }

    /**
     * The biggest consumers, descending.
     *
     * <p>The question is "what eats the most", not "what exists".
     */
    public List<Consumer> top(int count) {
        List<Consumer> found = new ArrayList<>();
        byDevice.forEach((name, bytes) -> found.add(new Consumer(name, bytes)));
        found.sort(Comparator.comparingLong(Consumer::bytes).reversed());
        return List.copyOf(found.subList(0, Math.min(count, found.size())));
    }
}
