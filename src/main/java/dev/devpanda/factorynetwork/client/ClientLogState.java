package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.LogStatePacket;
import dev.devpanda.factorynetwork.runtime.LogLevel;

import java.util.List;

/**
 * Das zuletzt gemeldete Protokoll.
 *
 * <p>Nur zum Anzeigen, wie {@link ClientFlowState}. Der Filter lebt
 * ebenfalls hier und nicht im Reiter: Wer den Reiter wechselt und
 * zurückkommt, will seine Einstellung wiederfinden.
 */
public final class ClientLogState {

    private static List<LogStatePacket.Line> lines = List.of();

    /**
     * Ab welcher Stufe gezeigt wird.
     *
     * <p>{@code INFO} als Voreinstellung: Ein {@code debug} in einer Schleife
     * schreibt zwanzig Zeilen je Sekunde, und danach findet niemand mehr die
     * eine Meldung, auf die es ankam.
     */
    private static LogLevel minimum = LogLevel.INFO;

    private ClientLogState() {
    }

    public static void accept(LogStatePacket packet) {
        lines = packet.lines();
    }

    /** Alles, was der Server geschickt hat. */
    public static List<LogStatePacket.Line> all() {
        return lines;
    }

    /** Was der Filter durchlässt, das Jüngste zuletzt. */
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

    /** Wie viele Zeilen diese Stufe hat — für die Zahl am Filterknopf. */
    public static long count(LogLevel level) {
        return lines.stream().filter(line -> level.key().equals(line.level())).count();
    }

    public static void clear() {
        lines = List.of();
    }
}
