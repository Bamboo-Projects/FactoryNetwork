package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Was das Netz über die Zeit bewegt hat — für das Diagramm im Terminal.
 *
 * <p><b>Ein eigenes Paket und kein Feld mehr in {@code NetworkStatePacket}.</b>
 * Das ist voll: {@code StreamCodec.composite} trägt sechs Felder, und ein
 * siebtes ginge nur über eine verschachtelte Hilfsklasse.
 *
 * <p>Zwei Gründe sprechen ohnehin dafür: Der Verlauf ändert sich in jeder
 * Sekunde, der Netzzustand fast nie — beides in einem Paket hieße, die ganze
 * Geräteliste im Sekundentakt zu schicken. Und er wird nur gebraucht, solange
 * jemand hinsieht.
 *
 * @param perSecond der Verlauf, ältester Punkt zuerst, in Byte je Sekunde
 * @param top       die größten Verbraucher, absteigend
 * @param total     was seit dem Start des Netzes insgesamt bewegt wurde
 * @param capacity  was der Controller je Tick durchlässt — ohne diese Zahl
 *                  ist der Durchsatz im Kopf eine Zahl ohne Maßstab
 */
public record TrafficPacket(List<Integer> perSecond, List<Consumer> top, long total,
                            int capacity)
        implements CustomPacketPayload {

    /** Ein Verbraucher mit seiner Menge. */
    public record Consumer(String name, long bytes) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Consumer> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(256), Consumer::name,
                        ByteBufCodecs.VAR_LONG, Consumer::bytes,
                        Consumer::new);
    }

    /** So viele Verbraucher stehen in der Rangliste. */
    public static final int TOP = 8;

    public static final Type<TrafficPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "traffic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrafficPacket> STREAM_CODEC =
            StreamCodec.composite(
                    // Fünf Minuten Verlauf, ein Punkt je Sekunde.
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

    /** Baut das Paket aus dem Verlauf eines Controllers. */
    public static TrafficPacket of(
            dev.devpanda.factorynetwork.network.TrafficHistory history, int capacity) {
        List<Consumer> top = history.top(TOP).stream()
                .map(one -> new Consumer(one.name(), one.bytes()))
                .toList();
        return new TrafficPacket(history.perSecond(), top, history.total(), capacity);
    }
}
