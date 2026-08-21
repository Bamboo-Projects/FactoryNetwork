package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.analyser.AnalyserData;
import dev.devpanda.factorynetwork.client.ClientAnalyserState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Das Netz für den Analysator.
 *
 * <p>Geht laufend an den Spieler, solange er das Werkzeug in der Hand hält —
 * ein Netz ändert sich, während man davorsteht, und ein eingefrorenes Bild
 * wäre bei der Fehlersuche das Gegenteil von hilfreich.
 *
 * <p>Die Obergrenzen sind großzügig, aber vorhanden: Ein Netz mit
 * zehntausend Kabeln würde sonst jedes Anfassen des Werkzeugs zu einer
 * Übertragung machen, die man merkt.
 */
public record AnalyserDataPacket(List<AnalyserData.Node> nodes, List<AnalyserData.Link> links,
                                 AnalyserData.Summary summary) implements CustomPacketPayload {

    public static final int MAX_NODES = 2048;
    public static final int MAX_LINKS = 8192;

    public static final Type<AnalyserDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "analyser_data"));

    private static final StreamCodec<RegistryFriendlyByteBuf, AnalyserData.Node> NODE =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AnalyserData.Node::pos,
                    ByteBufCodecs.idMapper(i -> AnalyserData.NodeState.values()[i], Enum::ordinal),
                    AnalyserData.Node::state,
                    ByteBufCodecs.stringUtf8(128), AnalyserData.Node::label,
                    AnalyserData.Node::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, AnalyserData.Link> LINK =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AnalyserData.Link::from,
                    BlockPos.STREAM_CODEC, AnalyserData.Link::to,
                    ByteBufCodecs.idMapper(i -> AnalyserData.LinkState.values()[i], Enum::ordinal),
                    AnalyserData.Link::state,
                    ByteBufCodecs.VAR_INT, AnalyserData.Link::load,
                    ByteBufCodecs.VAR_INT, AnalyserData.Link::capacity,
                    AnalyserData.Link::new);

    /**
     * Von Hand geschrieben: {@code composite} trägt höchstens sechs Felder,
     * die Zusammenfassung hat sieben.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, AnalyserData.Summary> SUMMARY =
            StreamCodec.of(
                    (buffer, summary) -> {
                        buffer.writeVarInt(summary.devices());
                        buffer.writeVarInt(summary.cables());
                        buffer.writeVarInt(summary.starved());
                        buffer.writeVarInt(summary.unnamed());
                        buffer.writeVarInt(summary.duplicates());
                        buffer.writeVarInt(summary.tightLinks());
                        buffer.writeVarInt(summary.fullLinks());
                    },
                    buffer -> new AnalyserData.Summary(
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt()));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalyserDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    NODE.apply(ByteBufCodecs.list(MAX_NODES)), AnalyserDataPacket::nodes,
                    LINK.apply(ByteBufCodecs.list(MAX_LINKS)), AnalyserDataPacket::links,
                    SUMMARY, AnalyserDataPacket::summary,
                    AnalyserDataPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnalyserDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAnalyserState.accept(packet));
    }
}
