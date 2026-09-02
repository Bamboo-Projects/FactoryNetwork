package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "Clear" in the Log tab.
 *
 * <p>No payload: there is only one log per network, and which one is meant is
 * told by the open terminal. The server verifies that — a packet alone
 * authorises nothing.
 *
 * <p>The log is deleted, not merely hidden. Whoever clears it wants a clean
 * start for the next attempt; a filter that hides old lines would be something
 * else, and is called something else here too.
 */
public record ClearLogPacket() implements CustomPacketPayload {

    public static final Type<ClearLogPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "clear_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearLogPacket> STREAM_CODEC =
            StreamCodec.unit(new ClearLogPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearLogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> {
                controller.clearLog();
                // Send back immediately: otherwise the old list stays up
                // until the next regular push comes around, and the button
                // looks broken.
                controller.pushLogTo(player);
            });
        });
    }
}
