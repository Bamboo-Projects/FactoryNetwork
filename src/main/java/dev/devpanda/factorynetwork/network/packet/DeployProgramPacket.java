package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Übergibt den Code aus dem Editor an den Controller.
 *
 * <p>Der Text kann lang sein, ist aber selten — geschickt wird er nur, wenn
 * jemand „Übernehmen" drückt.
 */
public record DeployProgramPacket(BlockPos terminal, String source) implements CustomPacketPayload {

    /** Mehr als das nimmt der Server nicht an. */
    private static final int MAX_LENGTH = 64 * 1024;

    public static final Type<DeployProgramPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "deploy_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeployProgramPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeployProgramPacket::terminal,
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), DeployProgramPacket::source,
                    DeployProgramPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeployProgramPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Nur wer nah genug dransteht, darf übernehmen.
            if (!player.level().isLoaded(packet.terminal())
                    || player.distanceToSqr(packet.terminal().getCenter()) > 64) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.terminal())
                    instanceof TerminalBlockEntity terminal)) {
                return;
            }
            terminal.controller().ifPresentOrElse(controller -> {
                boolean accepted = controller.deploy(packet.source());
                // Der Ausgang gehört in den Editor und nicht nur in den Chat:
                // Zwei der Gründe — kein Serverschrank, Programm zu groß —
                // kennt der Client gar nicht, weil er sie nicht selbst
                // übersetzen kann.
                String line = accepted
                        ? Component.translatable("message.factorynetwork.deploy.accepted",
                                controller.program().workers().size()).getString()
                        : controller.diagnostics().isEmpty()
                                ? Component.translatable(
                                        "message.factorynetwork.deploy.rejected", 0).getString()
                                : controller.diagnostics().get(0).message();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new DeployResultPacket(accepted, line));
                // Die vollständige Liste bleibt im Chat: In eine Fußzeile
                // passt eine Meldung, nicht zwölf.
                if (!accepted) {
                    controller.diagnostics().forEach(diagnostic ->
                            player.sendSystemMessage(Component.literal(diagnostic.toString())));
                }
            }, () -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new DeployResultPacket(false, Component.translatable(
                                "message.factorynetwork.deploy.no_controller").getString()));
                player.sendSystemMessage(
                        Component.translatable("message.factorynetwork.deploy.no_controller"));
            });
        });
    }
}
