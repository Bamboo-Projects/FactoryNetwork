package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientFlowState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * What is currently waiting.
 *
 * <p>Flows live on the server; the client gets a snapshot. There is no other
 * way to have it — a flow's stack is server state, and mirroring it would mean
 * maintaining it twice.
 *
 * <p>The identifier travels along, because the player must be able to decide
 * on a {@code STALE} flow, and the choice should hit the right one — even if
 * the list has shifted between showing and clicking.
 */
public record FlowStatePacket(List<Line> flows, Compute compute, Supply supply,
                              List<String> globals)
        implements CustomPacketPayload {

    /**
     * What the servers can carry, and how much of it is occupied.
     *
     * <p>Numbers you never see without a display: what a loop costs in compute,
     * no one sees — with channels the limit is at least obvious. Whoever is
     * queued must be able to read why.
     *
     * <p>As its own set and not as six more fields above:
     * {@code StreamCodec.composite} carries at most six.
     */
    public record Compute(int threads, int occupied, int queued,
                          int memory, int program, int disk) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Compute> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Compute::threads,
                        ByteBufCodecs.VAR_INT, Compute::occupied,
                        ByteBufCodecs.VAR_INT, Compute::queued,
                        ByteBufCodecs.VAR_INT, Compute::memory,
                        ByteBufCodecs.VAR_INT, Compute::program,
                        ByteBufCodecs.VAR_INT, Compute::disk,
                        Compute::new);
    }

    /**
     * The network's power.
     *
     * <p>Its own set of numbers, for the same reason as {@link Compute}.
     *
     * <p>{@code state} is the running number from {@code NetworkPower.State}
     * — the client should show the state, not judge it.
     *
     * <p>{@code draw} and {@code supplied} are two different things: what the
     * network needs for itself, and what it passes on to machines.
     */
    public record Supply(int state, int stored, int capacity, int draw, int supplied) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Supply> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Supply::state,
                        ByteBufCodecs.VAR_INT, Supply::stored,
                        ByteBufCodecs.VAR_INT, Supply::capacity,
                        ByteBufCodecs.VAR_INT, Supply::draw,
                        ByteBufCodecs.VAR_INT, Supply::supplied,
                        Supply::new);
    }

    /** A line of the list. */
    public record Line(long id, String entry, String status, String detail) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Line::id,
                        ByteBufCodecs.stringUtf8(128), Line::entry,
                        ByteBufCodecs.stringUtf8(32), Line::status,
                        ByteBufCodecs.stringUtf8(256), Line::detail,
                        Line::new);
    }

    public static final Type<FlowStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "flow_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlowStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(256)), FlowStatePacket::flows,
                    Compute.STREAM_CODEC, FlowStatePacket::compute,
                    Supply.STREAM_CODEC, FlowStatePacket::supply,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(128)),
                    FlowStatePacket::globals,
                    FlowStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientFlowState.accept(packet));
    }
}
