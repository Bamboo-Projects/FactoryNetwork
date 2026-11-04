package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.ProgramDiskItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * After an upload: the code, and the key that may change it, onto the disk
 * in hand. The key lives only there — whoever holds the disk may update.
 */
public record DiskSharedPacket(String code, String key) implements CustomPacketPayload {

    public static final Type<DiskSharedPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "disk_shared"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskSharedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(16), DiskSharedPacket::code,
                    ByteBufCodecs.stringUtf8(128), DiskSharedPacket::key,
                    DiskSharedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiskSharedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.getMainHandItem().getItem() instanceof ProgramDiskItem) {
                ProgramDiskItem.setShare(player.getMainHandItem(),
                        new ProgramDiskItem.Share(packet.code(), packet.key()));
            }
        });
    }
}
