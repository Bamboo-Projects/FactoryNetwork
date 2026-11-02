package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.network.SlotMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * The answer to {@link SlotMapRequestPacket}: the slots of the open GUI and
 * the label of a connector at the block, empty when none is named.
 *
 * <p>The label rides along because only the server sees the cable buses
 * around the block; the client needs it to copy a line that names the
 * machine the way the code does.
 */
public record SlotMapPacket(int containerId, List<SlotMap.Entry> entries, String connector)
        implements CustomPacketPayload {

    public static final Type<SlotMapPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "slot_map"));

    private static final StreamCodec<ByteBuf, SlotMap.Entry> ENTRY = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlotMap.Entry::menuSlot,
            ByteBufCodecs.VAR_INT, entry -> entry.number() + 1,
            ByteBufCodecs.BYTE, entry -> (byte) entry.sides(),
            ByteBufCodecs.BOOL, SlotMap.Entry::estimated,
            (menuSlot, number, sides, estimated) -> new SlotMap.Entry(menuSlot, number - 1, sides, estimated));

    public static final StreamCodec<ByteBuf, SlotMapPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlotMapPacket::containerId,
            ENTRY.apply(ByteBufCodecs.list(512)), SlotMapPacket::entries,
            ByteBufCodecs.STRING_UTF8, SlotMapPacket::connector,
            SlotMapPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SlotMapPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> dev.devpanda.factorynetwork.client.SlotOverlay.receive(packet));
    }
}
