package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** At login: whether this server lets its players use the program share. */
public record ShareAllowedPacket(boolean allowed) implements CustomPacketPayload {

    public static final Type<ShareAllowedPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "share_allowed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShareAllowedPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ShareAllowedPacket::allowed, ShareAllowedPacket::new);

    /** What the client last heard; false until the server has said anything. */
    private static volatile boolean allowedHere;

    public static boolean allowedHere() {
        return allowedHere;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShareAllowedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> allowedHere = packet.allowed());
    }
}
