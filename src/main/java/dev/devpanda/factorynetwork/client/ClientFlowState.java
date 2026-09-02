package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.FlowStatePacket;

import java.util.List;

/**
 * The last known state of the flows.
 *
 * <p>For display only. What stands here was true at the moment of the last
 * packet — action is taken exclusively through identifiers that the server
 * verifies.
 */
public final class ClientFlowState {

    private static List<FlowStatePacket.Line> flows = List.of();
    private static List<String> globals = List.of();
    private static FlowStatePacket.Compute compute =
            new FlowStatePacket.Compute(0, 0, 0, 0, 0, 0);
    private static FlowStatePacket.Supply supply =
            new FlowStatePacket.Supply(0, 0, 0, 0, 0);

    /** The network's power, as the server last reported it. */
    public static FlowStatePacket.Supply supply() {
        return supply;
    }

    /** What the servers carry and how much of that is occupied. */
    public static FlowStatePacket.Compute compute() {
        return compute;
    }

    /** How many flows may run at the same time. */
    public static int threads() {
        return compute.threads();
    }

    /** How many slots are currently occupied. */
    public static int occupied() {
        return compute.occupied();
    }

    /** How many are queued. */
    public static int queued() {
        return compute.queued();
    }

    private ClientFlowState() {
    }

    public static void accept(FlowStatePacket packet) {
        compute = packet.compute();
        supply = packet.supply();
        flows = List.copyOf(packet.flows());
        globals = List.copyOf(packet.globals());
    }

    /** The program's global values, as "name = value". */
    public static List<String> globals() {
        return globals;
    }

    public static List<FlowStatePacket.Line> flows() {
        return flows;
    }

    public static void clear() {
        flows = List.of();
    }
}
