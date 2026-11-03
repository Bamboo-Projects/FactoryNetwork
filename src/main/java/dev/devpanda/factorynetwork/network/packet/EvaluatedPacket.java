package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The answer to {@link EvaluatePacket}: the expression and its value as text. */
public record EvaluatedPacket(String expression, String value) implements CustomPacketPayload {

    public static final Type<EvaluatedPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "evaluated"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EvaluatedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(512), EvaluatedPacket::expression,
                    ByteBufCodecs.stringUtf8(2048), EvaluatedPacket::value,
                    EvaluatedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EvaluatedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> dev.devpanda.factorynetwork.client.LiveValues.accept(
                packet.expression(), packet.value()));
    }
}
