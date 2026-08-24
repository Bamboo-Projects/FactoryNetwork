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
 * „Was liegt gerade in diesem Gerät?"
 *
 * <p>Auf Anfrage und nicht laufend: In einer Fabrik mit vierzig Connectoren
 * wäre die laufende Übertragung Dauerverkehr für etwas, das man einmal
 * ansieht. Gefragt wird, wenn der Zeiger auf einem Gerätenamen stehen bleibt.
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
     * Die Antwort für diesen Spieler, oder {@code null}.
     *
     * <p>Getrennt vom Versand, damit ein GameTest den ganzen Weg gehen kann:
     * Terminal offen, Name gefragt, Antwort da. Bliebe die Prüfung im
     * Handler, ließe sie sich nur im laufenden Spiel von Hand nachvollziehen
     * — und geprüft würde am Ende nur {@code DeviceSnapshotPacket.of}, also
     * gerade der Teil, der ohnehin am wenigsten schiefgeht.
     *
     * <p><b>Über das offene Menü und nicht über eine mitgeschickte
     * Koordinate:</b> Wer nach einem Gerät fragt, muss vor dem Terminal
     * stehen, das dazugehört. Eine Koordinate im Paket könnte jeder
     * schicken.
     */
    public static DeviceSnapshotPacket answerFor(net.minecraft.world.entity.player.Player player,
                                                 String connector) {
        if (!(player.containerMenu instanceof TerminalMenu menu)) {
            return null;
        }
        // controller(player) und nicht controller() — das Menü löst den
        // Controller über den Spieler auf, weil es selbst nur die Koordinate
        // des Terminals kennt.
        return menu.controller(player)
                .map(controller -> DeviceSnapshotPacket.of(controller, connector))
                .orElse(null);
    }
}
