package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.ClientFlowState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Was gerade wartet.
 *
 * <p>Abläufe leben auf dem Server; der Client bekommt einen Schnappschuss.
 * Anders wäre es nicht zu haben — der Stapel eines Ablaufs ist Serverzustand,
 * und ihn zu spiegeln hieße, ihn zweimal zu pflegen.
 *
 * <p>Die Kennung wandert mit, weil der Spieler bei einem {@code STALE}-Ablauf
 * entscheiden können muss, und die Wahl den richtigen treffen soll — auch
 * wenn sich die Liste zwischen Anzeigen und Klicken verschoben hat.
 */
public record FlowStatePacket(List<Line> flows) implements CustomPacketPayload {

    /** Eine Zeile der Liste. */
    public record Line(long id, String entry, String status, String detail) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Line::id,
                        ByteBufCodecs.stringUtf8(128), Line::entry,
                        ByteBufCodecs.stringUtf8(32), Line::status,
                        ByteBufCodecs.stringUtf8(256), Line::detail,
                        Line::new);
    }

    public static final Type<FlowStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "flow_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlowStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(256)), FlowStatePacket::flows,
                    FlowStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientFlowState.accept(packet));
    }
}
