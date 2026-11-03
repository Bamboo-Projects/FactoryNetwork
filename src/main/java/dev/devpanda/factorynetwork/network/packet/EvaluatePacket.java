package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "What is this expression worth right now?" — the editor asks when the
 * pointer rests on {@code kiste.count(item:iron_ingot)} or a global value.
 *
 * <p>Only expressions that read ({@link dev.devpanda.factorynetwork.lang.ReadOnly});
 * through the open terminal, like the device snapshot.
 */
public record EvaluatePacket(String expression) implements CustomPacketPayload {

    public static final Type<EvaluatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "evaluate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EvaluatePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(512), EvaluatePacket::expression,
                    EvaluatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EvaluatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> PacketDistributor.sendToPlayer(player,
                    new EvaluatedPacket(packet.expression(), controller.evaluateReading(packet.expression()))));
        });
    }
}
