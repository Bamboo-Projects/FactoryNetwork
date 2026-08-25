package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.CraftingStatePacket;

import java.util.List;

/**
 * Die zuletzt gemeldeten Fertigungsaufträge.
 *
 * <p>Nur zum Anzeigen, wie {@link ClientFlowState}: Der Client urteilt nicht
 * über einen Auftrag, er zeigt ihn.
 */
public final class ClientCraftingState {

    private static List<CraftingStatePacket.Line> jobs = List.of();

    private ClientCraftingState() {
    }

    public static void accept(CraftingStatePacket packet) {
        jobs = packet.jobs();
    }

    public static List<CraftingStatePacket.Line> jobs() {
        return jobs;
    }
}
