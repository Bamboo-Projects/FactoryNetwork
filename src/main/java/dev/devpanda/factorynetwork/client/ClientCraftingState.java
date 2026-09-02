package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.CraftingStatePacket;

import java.util.List;

/**
 * The most recently reported crafting jobs.
 *
 * <p>For display only, like {@link ClientFlowState}: the client does not judge
 * a job, it shows it.
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
