package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Die Wahl bei einem Ablauf, der sich gemeldet hat.
 *
 * <p>Zwei Möglichkeiten, mehr gibt es nicht: weiterlaufen lassen oder
 * abbrechen. Der Server entscheidet, ob die Wahl noch gilt — der Ablauf kann
 * inzwischen von selbst zu Ende gegangen sein.
 */
public record FlowActionPacket(long id, boolean keep) implements CustomPacketPayload {

    public static final Type<FlowActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "flow_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlowActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, FlowActionPacket::id,
                    ByteBufCodecs.BOOL, FlowActionPacket::keep,
                    FlowActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> {
                if (controller.flowEngine() == null) {
                    return;
                }
                if (packet.keep()) {
                    controller.flowEngine().unstale(packet.id());
                } else {
                    controller.flowEngine().abort(packet.id());
                }
                controller.pushFlowsTo(player);
            });
        });
    }
}
