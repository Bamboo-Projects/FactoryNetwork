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
 *
 * <p><b>Mit Stelle und nicht nur mit Namen.</b> Wer im Editor
 * {@code crusher_1} liest, will wissen, welche Maschine das ist — in einer
 * Fabrik mit vierzig Öfen führte diese Frage bisher in den Keller.
 *
 * <p><b>Die Anzeigen gehören genauso dazu.</b> {@code display NAME { … }}
 * verlangt den Namen, den die Tafel in der Welt trägt — und der stand
 * nirgends, wo der Editor ihn hätte vorschlagen können. Wer seine Wand
 * benannt hatte, musste sich den Namen merken und richtig abtippen.
 *
 * <p><b>Die Profile sagen, was hinter den Connectoren steht.</b> Sie reisen
 * hier mit und nicht auf Anfrage, weil sie sich nur ändern, wenn jemand die
 * Maschine austauscht. Was gerade in den Fächern liegt, kommt dagegen über
 * {@link DeviceSnapshotPacket} — das ändert sich im Sekundentakt.
 *
 * <p><b>Sechs Felder sind die Grenze.</b> {@code StreamCodec.composite} trägt
 * nicht mehr; ein siebtes bräuchte eine von Hand geschriebene Fassung wie in
 * {@link AnalyserDataPacket}.
 *
 * <p>Der Quelltext ist hier ausgezogen. Er stand als Zeichenkette bis 64 KB
 * in jedem Netzzustand — und der geht raus, sooft sich am Netz etwas ändert,
 * während ihn seit dem Projektumbau niemand mehr liest. Er kommt über
 * {@link ProjectStatePacket}, und der geht nur beim Öffnen und beim
 * Übernehmen.
 */
public record NetworkStatePacket(List<NamedPlace> connectors, List<NamedPlace> displays,
                                 List<String> workers, List<String> plants,
                                 List<String> fluids,
                                 List<DeviceProfileCodec.Flat> profiles)
        implements CustomPacketPayload {

    public static final Type<NetworkStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "network_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    NamedPlace.STREAM_CODEC.apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::connectors,
                    NamedPlace.STREAM_CODEC.apply(ByteBufCodecs.list(256)),
                    NetworkStatePacket::displays,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::workers,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(256)),
                    NetworkStatePacket::plants,
                    ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(256)),
                    NetworkStatePacket::fluids,
                    DeviceProfileCodec.Flat.STREAM_CODEC.apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::profiles,
                    NetworkStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NetworkStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientNetworkState.accept(packet));
    }
}
