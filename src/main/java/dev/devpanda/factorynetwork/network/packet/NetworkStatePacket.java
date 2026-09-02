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
 * The network's state for the editor: source text, known connectors and the
 * status of the workers.
 *
 * <p>The connector list is what completion needs. It is sent on opening, not
 * continuously — a network changes less often than someone types.
 *
 * <p>The plants are included, because an incomplete one would otherwise stay
 * invisible: it does nothing and says nothing, and the player looks for the
 * fault in the program instead of at the labelling.
 *
 * <p><b>With a place and not only a name.</b> Whoever reads {@code crusher_1}
 * in the editor wants to know which machine that is — in a factory with forty
 * furnaces this question used to lead down into the basement.
 *
 * <p><b>The displays belong here just as much.</b> {@code display NAME { … }}
 * demands the name the panel carries in the world — and that stood nowhere the
 * editor could have suggested it. Whoever had named their wall had to remember
 * the name and type it out correctly.
 *
 * <p><b>The profiles say what is behind the connectors.</b> They travel along
 * here and not on request, because they only change when someone swaps out the
 * machine. What is currently in the slots, by contrast, comes over
 * {@link DeviceSnapshotPacket} — that changes every second.
 *
 * <p><b>Six fields are the limit.</b> {@code StreamCodec.composite} carries no
 * more; a seventh would need a hand-written version as in
 * {@link AnalyserDataPacket}.
 *
 * <p>The source text has been moved out of here. It stood as a string of up to
 * 64 KB in every network state — and that goes out whenever something on the
 * network changes, while since the project rework no one reads it anymore. It
 * comes over {@link ProjectStatePacket}, and that goes only on opening and on
 * applying.
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
