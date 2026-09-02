package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "What is currently in this device?"
 *
 * <p>On request and not continuously: in a factory with forty connectors the
 * continuous transfer would be constant traffic for something you look at
 * once. It is asked when the pointer comes to rest on a device name.
 */
public record DeviceSnapshotRequestPacket(String connector) implements CustomPacketPayload {

    public static final Type<DeviceSnapshotRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID,
                    "device_snapshot_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshotRequestPacket>
            STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(256),
                    DeviceSnapshotRequestPacket::connector,
                    DeviceSnapshotRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeviceSnapshotRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            DeviceSnapshotPacket answer = answerFor(player, packet.connector());
            if (answer != null) {
                PacketDistributor.sendToPlayer(player, answer);
            }
        });
    }

    /**
     * The answer for this player, or {@code null}.
     *
     * <p>Kept separate from the sending so that a GameTest can go the whole
     * way: terminal open, name asked, answer there. Were the check to stay in
     * the handler, it could only be followed by hand in a running game — and
     * in the end only {@code DeviceSnapshotPacket.of} would be tested, which is
     * exactly the part that goes wrong least anyway.
     *
     * <p><b>Through the open menu and not through a coordinate sent along:</b>
     * whoever asks about a device must be standing in front of the terminal it
     * belongs to. A coordinate in the packet, anyone could send.
     */
    public static DeviceSnapshotPacket answerFor(net.minecraft.world.entity.player.Player player,
                                                 String connector) {
        if (!(player.containerMenu instanceof TerminalMenu menu)) {
            return null;
        }
        // controller(player) and not controller() — the menu resolves the
        // controller through the player, because it itself only knows the
        // terminal's coordinate.
        return menu.controller(player)
                .map(controller -> DeviceSnapshotPacket.of(controller, connector))
                .orElse(null);
    }
}
