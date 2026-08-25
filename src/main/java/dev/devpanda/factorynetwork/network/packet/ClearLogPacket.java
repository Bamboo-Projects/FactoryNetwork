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
 * „Leeren" im Reiter Log.
 *
 * <p>Ohne Inhalt: Es gibt nur ein Protokoll je Netz, und welches gemeint ist,
 * sagt das offene Terminal. Der Server prüft das nach — ein Paket allein
 * berechtigt zu nichts.
 *
 * <p>Das Protokoll wird gelöscht und nicht nur ausgeblendet. Wer leert, will
 * einen sauberen Anfang für den nächsten Versuch; ein Filter, der alte Zeilen
 * versteckt, wäre etwas anderes und heißt hier auch anders.
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
                // Sofort zurückschicken: Sonst steht die alte Liste noch da,
                // bis das regelmäßige Schicken das nächste Mal dran ist, und
                // der Knopf sieht kaputt aus.
                controller.pushLogTo(player);
            });
        });
    }
}
