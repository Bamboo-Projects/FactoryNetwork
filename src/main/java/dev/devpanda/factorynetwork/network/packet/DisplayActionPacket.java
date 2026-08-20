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
 * Ein gedrückter Knopf auf einer Anzeige.
 *
 * <p>Es reist nur, welcher Knopf es war. Was er auslöst, sucht der Server im
 * Programm — so kann kein Client eine Funktion aufrufen, die auf keiner
 * Anzeige steht.
 */
public record DisplayActionPacket(String display, int entry) implements CustomPacketPayload {

    public static final Type<DisplayActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "display_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(128), DisplayActionPacket::display,
                    ByteBufCodecs.VAR_INT, DisplayActionPacket::entry,
                    DisplayActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisplayActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            menu.controller(player).ifPresent(controller ->
                    controller.pressDisplayButton(packet.display(), packet.entry()));
        });
    }
}
