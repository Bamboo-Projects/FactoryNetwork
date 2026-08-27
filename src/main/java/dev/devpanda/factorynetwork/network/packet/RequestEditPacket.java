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
 * „Ich hätte die Datei gern."
 *
 * <p>Die Sperre hält, und das ist richtig — zwei Leute, die dieselbe Datei
 * schreiben, verlieren die Arbeit des einen. Aber wer davorsteht, konnte
 * bisher <b>nur warten</b>, ohne zu wissen, wie lange. Ein Klopfen kostet
 * nichts und löst den häufigsten Fall: Der andere hat die Datei offen, tippt
 * aber gar nicht mehr.
 *
 * <p><b>Kein Übernehmen, nur ein Klopfen.</b> Die Sperre wegzunehmen wäre
 * genau das, wogegen sie gebaut wurde. Wer angeklopft wird, schließt die
 * Datei — oder eben nicht.
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
            // Wer den Code-Reiter nicht hat, fragt auch nicht nach fremdem
            // Quelltext. Der Reiter fehlt am Wireless Terminal nur in der
            // Anzeige — hier steht die Regel, die auch ein veränderter
            // Client nicht umgeht.
            if (!menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            ControllerBlockEntity controller = menu.controller(asking).orElse(null);
            if (controller == null || controller.getLevel() == null) {
                return;
            }
            UUID holder = controller.holderOf(packet.file());
            if (holder == null) {
                // Niemand hält sie mehr — die Anfrage hat sich erledigt, und
                // der Fragende merkt es beim nächsten Tippen.
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
            // An den Halter: wer fragt und wonach.
            owner.sendSystemMessage(Component.translatable(
                    "message.factorynetwork.request_edit.asked",
                    asking.getName(), packet.file()));
            // Und an den Fragenden, damit der Klick eine Antwort hat.
            asking.sendSystemMessage(Component.translatable(
                    "message.factorynetwork.request_edit.sent", owner.getName()));
        });
    }
}
