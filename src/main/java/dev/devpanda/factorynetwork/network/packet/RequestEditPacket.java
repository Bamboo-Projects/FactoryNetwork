package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * "I would like the file."
 *
 * <p>The lock holds, and that is right — two people writing the same file lose
 * one of their work. But whoever is standing in front of it could until now
 * <b>only wait</b>, without knowing for how long. A knock costs nothing and
 * resolves the most common case: the other person has the file open but is no
 * longer typing at all.
 *
 * <p><b>No takeover, just a knock.</b> Taking the lock away would be exactly
 * what it was built against. Whoever gets knocked on closes the file — or
 * simply does not.
 */
public record RequestEditPacket(String file) implements CustomPacketPayload {

    public static final Type<RequestEditPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "request_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEditPacket>
            STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(256),
                    RequestEditPacket::file, RequestEditPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestEditPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer asking)
                    || !(asking.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            // Whoever does not have the Code tab does not ask for someone
            // else's source either. The tab is missing on the Wireless
            // Terminal only in the display — here stands the rule that even
            // a modified client cannot get around.
            if (!menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            ControllerBlockEntity controller = menu.controller(asking).orElse(null);
            if (controller == null || controller.getLevel() == null) {
                return;
            }
            UUID holder = controller.holderOf(packet.file());
            if (holder == null) {
                // No one holds it anymore — the request has settled itself,
                // and the asker notices at the next keystroke.
                asking.sendSystemMessage(Component.translatable(
                        "message.factorynetwork.request_edit.free", packet.file()));
                return;
            }
            if (holder.equals(asking.getUUID())) {
                return;
            }
            ServerPlayer owner = controller.getLevel().getServer()
                    .getPlayerList().getPlayer(holder);
            if (owner == null) {
                return;
            }
            // To the holder: who asks and for what.
            owner.sendSystemMessage(Component.translatable(
                    "message.factorynetwork.request_edit.asked",
                    asking.getName(), packet.file()));
            // And to the asker, so the click has an answer.
            asking.sendSystemMessage(Component.translatable(
                    "message.factorynetwork.request_edit.sent", owner.getName()));
        });
    }
}
