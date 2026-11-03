package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;

/**
 * The breakpoints of one file, as the editor's gutter shows them. Through
 * the open terminal, like every edit of the program.
 */
public record BreakpointsPacket(String file, List<Integer> lines) implements CustomPacketPayload {

    public static final Type<BreakpointsPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "breakpoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BreakpointsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), BreakpointsPacket::file,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), BreakpointsPacket::lines,
                    BreakpointsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BreakpointsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)
                    || !menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> {
                controller.setBreakpoints(packet.file(), new HashSet<>(packet.lines()));
                controller.pushFlowsTo(player);
            });
        });
    }
}
