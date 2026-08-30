package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.TrafficPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Was das Netz zuletzt bewegt hat — der Stand auf dem Client.
 *
 * <p>Wie {@code ClientNetworkState}: Der Server schickt, der Client hält den
 * letzten Stand, und das Fenster zeichnet ihn. Gerechnet wird nichts hier —
 * ein Client, der selbst mitschreibt, hat Lücken, sobald das Fenster zu ist.
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

    /** Der Verlauf, ältester Punkt zuerst, in Byte je Sekunde. */
    public static List<Integer> perSecond() {
        return perSecond;
    }

    /** Die größten Verbraucher, absteigend. */
    public static List<TrafficPacket.Consumer> top() {
        return top;
    }

    /** Was seit dem Start des Netzes insgesamt bewegt wurde. */
    public static long total() {
        return total;
    }

    /**
     * Was der Controller je Tick durchlässt.
     *
     * <p>Null, solange kein Paket kam — dann zeigt der Kopf die nackte Zahl
     * statt einer Auslastung von null.
     */
    public static int capacity() {
        return capacity;
    }

    /**
     * Die Spitze des Verlaufs.
     *
     * <p>Ein Diagramm braucht einen Maßstab, und der feste Maßstab wäre bei
     * einem ruhigen Netz eine flache Linie am Boden. Mindestens ein Kilobyte,
     * damit ein leeres Netz nicht bei null durch null teilt.
     */
    public static int peak() {
        return Math.max(1000, perSecond.stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private ClientTraffic() {
    }
}
