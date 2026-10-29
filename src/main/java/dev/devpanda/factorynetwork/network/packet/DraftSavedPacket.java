package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientProjectState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A numbered acknowledgement, distinct from unsolicited project updates. */
public record DraftSavedPacket(long sequence, boolean accepted, String message, ProjectStatePacket state)
        implements CustomPacketPayload {
    public static final Type<DraftSavedPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "draft_saved"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DraftSavedPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, DraftSavedPacket::sequence,
                    ByteBufCodecs.BOOL, DraftSavedPacket::accepted,
                    ByteBufCodecs.stringUtf8(1024), DraftSavedPacket::message,
                    ProjectStatePacket.STREAM_CODEC, DraftSavedPacket::state, DraftSavedPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DraftSavedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProjectState.acknowledge(packet));
    }
}
