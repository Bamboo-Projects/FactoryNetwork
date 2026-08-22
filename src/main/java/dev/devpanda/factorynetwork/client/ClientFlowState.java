package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;

import java.util.List;

/**
 * Der letzte bekannte Stand der Abläufe.
 *
 * <p>Nur zum Anzeigen. Was hier steht, war zum Zeitpunkt des letzten Pakets
 * wahr — gehandelt wird ausschließlich über Kennungen, die der Server
 * nachprüft.
 */
public final class ClientFlowState {

    private static List<FlowStatePacket.Line> flows = List.of();
    private static int threads;
    private static int occupied;
    private static int queued;
    private static FlowStatePacket.Supply supply =
            new FlowStatePacket.Supply(0, 0, 0, 0);

    /** Der Strom des Netzes, wie ihn der Server zuletzt gemeldet hat. */
    public static FlowStatePacket.Supply supply() {
        return supply;
    }

    /** Wie viele Abläufe gleichzeitig laufen dürfen. */
    public static int threads() {
        return threads;
    }

    /** Wie viele Plätze gerade belegt sind. */
    public static int occupied() {
        return occupied;
    }

    /** Wie viele anstehen. */
    public static int queued() {
        return queued;
    }

    private ClientFlowState() {
    }

    public static void accept(FlowStatePacket packet) {
        threads = packet.threads();
        occupied = packet.occupied();
        queued = packet.queued();
        supply = packet.supply();
        flows = List.copyOf(packet.flows());
    }

    public static List<FlowStatePacket.Line> flows() {
        return flows;
    }

    public static void clear() {
        flows = List.of();
    }
}
