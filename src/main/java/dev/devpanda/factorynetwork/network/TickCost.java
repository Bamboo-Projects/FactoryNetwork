package dev.devpanda.factorynetwork.network;

/**
 * What the controllers cost the server, summed over all of them.
 *
 * <p>Taken around each controller's tick, which is where the networks do
 * their work: workers, storage pushes, power. Two clock reads per controller
 * and tick, so it can stay on. Read by the load measurements and meant for
 * a status command later; server thread only.
 */
public final class TickCost {

    private static long nanos;
    private static long ticks;
    private static long slowest;
    private static net.minecraft.core.BlockPos slowestAt;
    /** The longest time per phase of a controller tick, to tell a spike's cause. */
    private static final java.util.Map<String, Long> slowestPhases = new java.util.LinkedHashMap<>();
    private static long slowestWorker;
    private static String slowestWorkerName;

    private TickCost() {
    }

    public static void add(long elapsedNanos, net.minecraft.core.BlockPos controller) {
        nanos += elapsedNanos;
        ticks++;
        if (elapsedNanos > slowest) {
            slowest = elapsedNanos;
            slowestAt = controller;
        }
    }

    /** The longest single controller tick since the last reset. */
    public static long slowest() {
        return slowest;
    }

    /** Where that controller stands, or {@code null}. */
    public static net.minecraft.core.BlockPos slowestAt() {
        return slowestAt;
    }

    /** Total controller time since the last reset, in nanoseconds. */
    public static long nanos() {
        return nanos;
    }

    /** Controller ticks since the last reset. */
    public static long ticks() {
        return ticks;
    }

    /** Notes how long one phase of a controller tick took. */
    public static void phase(String name, long elapsedNanos) {
        slowestPhases.merge(name, elapsedNanos, Math::max);
    }

    /** Notes how long one worker step took. */
    public static void worker(String name, long elapsedNanos) {
        if (elapsedNanos > slowestWorker) {
            slowestWorker = elapsedNanos;
            slowestWorkerName = name;
        }
    }

    /** The longest time per phase since the last reset, in nanoseconds. */
    public static java.util.Map<String, Long> slowestPhases() {
        return java.util.Map.copyOf(slowestPhases);
    }

    /** The longest single worker step since the last reset. */
    public static long slowestWorker() {
        return slowestWorker;
    }

    public static String slowestWorkerName() {
        return slowestWorkerName;
    }

    public static void reset() {
        slowestPhases.clear();
        slowestWorker = 0;
        slowestWorkerName = null;
        nanos = 0;
        ticks = 0;
        slowest = 0;
        slowestAt = null;
    }
}
