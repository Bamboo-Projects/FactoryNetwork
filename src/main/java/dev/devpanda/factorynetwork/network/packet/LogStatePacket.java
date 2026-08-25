package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientLogState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Das Protokoll für das Terminal.
 *
 * <p>Es liegt auf dem Server und geht als Ganzes hinüber. Zweihundert Zeilen
 * sind wenige Kilobyte, und die Alternative — nur das Neue schicken — hieße,
 * auf beiden Seiten mitzuzählen, wer was schon gesehen hat. Das Protokoll
 * ändert sich zu selten, als dass sich das lohnte.
 *
 * <p>Die Stufe wandert als Name und nicht als Zahl: Eine Zahl, die eine Seite
 * anders liest als die andere, wäre ein Fehler, den niemand sieht.
 */
public record LogStatePacket(List<Line> lines) implements CustomPacketPayload {

    /** Eine Zeile: wie ernst, wann, von wem, was. */
    public record Line(String level, long time, String source, String text) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(16), Line::level,
                        ByteBufCodecs.VAR_LONG, Line::time,
                        ByteBufCodecs.stringUtf8(64), Line::source,
                        ByteBufCodecs.stringUtf8(256), Line::text,
                        Line::new);
    }

    public static final Type<LogStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "log_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LogStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(512)), LogStatePacket::lines,
                    LogStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LogStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientLogState.accept(packet));
    }
}
