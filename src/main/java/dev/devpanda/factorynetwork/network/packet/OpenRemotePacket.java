package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.RemoteDeviceItem;
import dev.devpanda.factorynetwork.platform.Chat;
import dev.devpanda.factorynetwork.terminal.RemoteAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The key for the wireless terminal: opens it through the first device the
 * player carries, a worn one first. Without a key a device worn in a Curios
 * slot could not be used at all.
 */
public record OpenRemotePacket() implements CustomPacketPayload {

    public static final Type<OpenRemotePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "open_remote"));

    public static final StreamCodec<ByteBuf, OpenRemotePacket> STREAM_CODEC =
            StreamCodec.unit(new OpenRemotePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRemotePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var device = RemoteAccess.firstDevice(player);
            if (device == null) {
                Chat.tell(player, Component.translatable("message.factorynetwork.remote.none_carried"), true);
                return;
            }
            RemoteDeviceItem.open(player.level(), player, device);
        });
    }
}
