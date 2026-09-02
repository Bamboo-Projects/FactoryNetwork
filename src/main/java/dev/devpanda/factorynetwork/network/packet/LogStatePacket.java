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
 * The log for the terminal.
 *
 * <p>It lives on the server and goes across as a whole. Two hundred lines are
 * a few kilobytes, and the alternative — sending only what is new — would mean
 * counting along on both sides who has already seen what. The log changes too
 * rarely for that to be worth it.
 *
 * <p>The level travels as a name and not as a number: a number that one side
 * reads differently from the other would be a bug no one sees.
 */
public record LogStatePacket(List<Line> lines) implements CustomPacketPayload {

    /** A line: how serious, when, from whom, what. */
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
