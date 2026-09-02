package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Reports whether the storage tab is open.
 *
 * <p>Which tab is shown is otherwise purely the client's business. This one
 * exception exists because the server should only send stock changes when
 * someone is actually looking — whoever reads code needs no flood of packets.
 */
public record StorageTabPacket(boolean open) implements CustomPacketPayload {

    public static final Type<StorageTabPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "storage_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageTabPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, StorageTabPacket::open, StorageTabPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageTabPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu) {
                menu.setStorageTabOpen(player, packet.open());
            }
        });
    }
}
