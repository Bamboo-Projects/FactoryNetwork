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

    private ClientFlowState() {
    }

    public static void accept(FlowStatePacket packet) {
        flows = List.copyOf(packet.flows());
    }

    public static List<FlowStatePacket.Line> flows() {
        return flows;
    }

    public static void clear() {
        flows = List.of();
    }
}
