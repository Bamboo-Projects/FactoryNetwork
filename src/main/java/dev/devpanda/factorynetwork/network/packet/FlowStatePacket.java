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
public record FlowStatePacket(List<Line> flows, int threads, int occupied, int queued,
                              Supply supply)
        implements CustomPacketPayload {

    // threads, occupied und queued sind die Zahlen, die man ohne Anzeige nie
    // sieht: Was eine Schleife an Rechenleistung kostet, sieht niemand — bei
    // Kanälen ist die Grenze wenigstens offensichtlich. Wer ansteht, muss
    // lesen können, warum.

    /**
     * Der Strom des Netzes.
     *
     * <p>Als eigener Satz Zahlen und nicht als vier weitere Felder oben:
     * {@code StreamCodec.composite} trägt höchstens sechs, und oben sind
     * vier schon vergeben.
     *
     * <p>{@code state} ist die laufende Nummer aus {@code NetworkPower.State}
     * — der Client soll den Zustand anzeigen und nicht darüber urteilen.
     */
    public record Supply(int state, int stored, int capacity, int draw) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Supply> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Supply::state,
                        ByteBufCodecs.VAR_INT, Supply::stored,
                        ByteBufCodecs.VAR_INT, Supply::capacity,
                        ByteBufCodecs.VAR_INT, Supply::draw,
                        Supply::new);
    }

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
                    ByteBufCodecs.VAR_INT, FlowStatePacket::threads,
                    ByteBufCodecs.VAR_INT, FlowStatePacket::occupied,
                    ByteBufCodecs.VAR_INT, FlowStatePacket::queued,
                    Supply.STREAM_CODEC, FlowStatePacket::supply,
                    FlowStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientFlowState.accept(packet));
    }
}
