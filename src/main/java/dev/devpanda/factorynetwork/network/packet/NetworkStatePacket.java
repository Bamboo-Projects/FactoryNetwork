package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Der Zustand des Netzwerks für den Editor: Quelltext, bekannte Connectoren
 * und der Stand der Worker.
 *
 * <p>Die Connectorliste ist das, was die Vervollständigung braucht. Sie wird
 * beim Öffnen geschickt, nicht laufend — ein Netz ändert sich seltener als
 * jemand tippt.
 *
 * <p>Die Anlagen stehen mit dabei, weil eine unvollständige sonst unsichtbar
 * bliebe: Sie tut nichts und sagt nichts, und der Spieler sucht den Fehler im
 * Programm statt an der Beschriftung.
 */
public record NetworkStatePacket(String source, List<String> connectors, List<String> workers,
                                 List<String> plants) implements CustomPacketPayload {

    public static final Type<NetworkStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "network_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64 * 1024), NetworkStatePacket::source,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::connectors,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::workers,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(256)),
                    NetworkStatePacket::plants,
                    NetworkStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NetworkStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientNetworkState.accept(packet));
    }
}
