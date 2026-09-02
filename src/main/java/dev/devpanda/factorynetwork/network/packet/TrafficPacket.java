package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What the network has moved over time — for the chart in the terminal.
 *
 * <p><b>Its own packet and no longer a field in {@code NetworkStatePacket}.</b>
 * That one is full: {@code StreamCodec.composite} carries six fields, and a
 * seventh would only go through a nested helper class.
 *
 * <p>Two reasons speak for it anyway: the history changes every second, the
 * network state almost never — both in one packet would mean sending the whole
 * device list every second. And it is only needed as long as someone is
 * looking.
 *
 * @param perSecond the history, oldest point first, in bytes per second
 * @param top       the largest consumers, descending
 * @param total     what has been moved in total since the network started
 * @param capacity  what the controller lets through per tick — without this
 *                  number the throughput in your head is a figure with no scale
 */
public record TrafficPacket(List<Integer> perSecond, List<Consumer> top, long total,
                            int capacity)
        implements CustomPacketPayload {

    /** A consumer with its amount. */
    public record Consumer(String name, long bytes) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Consumer> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(256), Consumer::name,
                        ByteBufCodecs.VAR_LONG, Consumer::bytes,
                        Consumer::new);
    }

    /** This many consumers appear in the ranking. */
    public static final int TOP = 8;

    public static final Type<TrafficPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "traffic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrafficPacket> STREAM_CODEC =
            StreamCodec.composite(
                    // Five minutes of history, one point per second.
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(
                            dev.devpanda.factorynetwork.network.TrafficHistory.SAMPLES)),
                    TrafficPacket::perSecond,
                    Consumer.STREAM_CODEC.apply(ByteBufCodecs.list(TOP)),
                    TrafficPacket::top,
                    ByteBufCodecs.VAR_LONG, TrafficPacket::total,
                    ByteBufCodecs.VAR_INT, TrafficPacket::capacity,
                    TrafficPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrafficPacket packet,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                dev.devpanda.factorynetwork.client.ClientTraffic.accept(packet));
    }

    /** Builds the packet from a controller's history. */
    public static TrafficPacket of(
            dev.devpanda.factorynetwork.network.TrafficHistory history, int capacity) {
        List<Consumer> top = history.top(TOP).stream()
                .map(one -> new Consumer(one.name(), one.bytes()))
                .toList();
        return new TrafficPacket(history.perSecond(), top, history.total(), capacity);
    }
}
