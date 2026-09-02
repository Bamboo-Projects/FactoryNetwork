package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.LogStatePacket;
import dev.devpanda.factorynetwork.runtime.LogLevel;

import java.util.List;

/**
 * The most recently reported log.
 *
 * <p>For display only, like {@link ClientFlowState}. The filter lives here too
 * and not in the tab: whoever switches tabs and comes back wants to find their
 * setting again.
 */
public final class ClientLogState {

    private static List<LogStatePacket.Line> lines = List.of();

    /**
     * From which level upward things are shown.
     *
     * <p>{@code INFO} as the default: a {@code debug} in a loop writes twenty
     * lines a second, and after that no one can find the one message that
     * mattered.
     */
    private static LogLevel minimum = LogLevel.INFO;

    private ClientLogState() {
    }

    public static void accept(LogStatePacket packet) {
        lines = packet.lines();
    }

    /** Everything the server has sent. */
    public static List<LogStatePacket.Line> all() {
        return lines;
    }

    /** What the filter lets through, the newest last. */
    public static List<LogStatePacket.Line> visible() {
        return lines.stream()
                .filter(line -> {
                    LogLevel level = LogLevel.of(line.level());
                    return level != null && level.atLeast(minimum);
                })
                .toList();
    }

    public static LogLevel minimum() {
        return minimum;
    }

    public static void setMinimum(LogLevel level) {
        minimum = level == null ? LogLevel.INFO : level;
    }

    /** How many lines this level has — for the number on the filter button. */
    public static long count(LogLevel level) {
        return lines.stream().filter(line -> level.key().equals(line.level())).count();
    }

    public static void clear() {
        lines = List.of();
    }
}
