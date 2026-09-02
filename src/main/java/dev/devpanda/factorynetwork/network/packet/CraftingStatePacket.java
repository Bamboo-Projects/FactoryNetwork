package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * The network's crafting jobs, for the tab.
 *
 * <p>The target's name goes across as <b>ready-made text</b> and not as an
 * identifier: the same decision as for the tanks in the device tooltip.
 * Resolving it again on the other side would be three times as much code for
 * the same line.
 */
public record CraftingStatePacket(List<Line> jobs) implements CustomPacketPayload {

    /** A job, exactly as it appears in the tab. */
    public record Line(long id, String target, int wanted, int done, String status,
                       String detail) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Line::id,
                        ByteBufCodecs.stringUtf8(128), Line::target,
                        ByteBufCodecs.VAR_INT, Line::wanted,
                        ByteBufCodecs.VAR_INT, Line::done,
                        ByteBufCodecs.stringUtf8(32), Line::status,
                        ByteBufCodecs.stringUtf8(256), Line::detail,
                        Line::new);
    }

    public static final Type<CraftingStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "crafting_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CraftingStatePacket::jobs,
                    CraftingStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftingStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                dev.devpanda.factorynetwork.client.ClientCraftingState.accept(packet));
    }
}
