package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Hands the project from the editor to the controller.
 *
 * <p>All files at once, and not the open one alone: whoever creates a file and
 * then applies another would otherwise have lost the new one. Long, but rare —
 * it is only sent when someone presses "Apply".
 */
public record DeployProgramPacket(BlockPos terminal, Map<String, String> files)
        implements CustomPacketPayload {

    /** The server accepts no more than this per file. */
    private static final int MAX_LENGTH = 64 * 1024;

    public static final Type<DeployProgramPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "deploy_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeployProgramPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeployProgramPacket::terminal,
                    ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.stringUtf8(64),
                            ByteBufCodecs.stringUtf8(MAX_LENGTH)),
                    DeployProgramPacket::files,
                    DeployProgramPacket::new);

    /** For anything that has only a single text. */
    public static DeployProgramPacket of(BlockPos terminal,
                                         dev.devpanda.factorynetwork.lang.Project project) {
        return new DeployProgramPacket(terminal, project.files());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * May this player change anything here?
     *
     * <p>The reasoning lives in {@code FnProtection}; here are only the inputs
     * it needs. Operators are always allowed — otherwise a server would have
     * no one to get an orphaned plant running again.
     */
    static boolean mayEdit(ServerPlayer player,
                           dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity
                                   controller) {
        return dev.devpanda.factorynetwork.FnProtection.mayEdit(
                dev.devpanda.factorynetwork.FnConfig.protection(),
                controller.owner(), player.getUUID(), player.hasPermissions(2));
    }

    public static void handle(DeployProgramPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Only someone with a terminal window open may apply —
            // and only if it actually shows the code.
            //
            // Previously there was a distance check against the position
            // carried in the packet. That held up as long as you had to
            // stand in front of a block for it. With the laptop you stand in
            // front of nothing anymore, and the coordinate in the packet is
            // a claim by the client. The open window, by contrast, the
            // server ticks itself: if the player walks out of range, it
            // closes, and nothing arrives here anymore.
            if (!(player.containerMenu instanceof dev.devpanda.factorynetwork.client.menu
                    .TerminalMenu menu)
                    || !menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            if (!menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
                return;
            }
            menu.controller(player).ifPresentOrElse(controller -> {
                // Permission first, then the compiler: whoever is not allowed
                // should learn that, and not their typos first.
                if (!mayEdit(player, controller)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new DeployResultPacket(false, Component.translatable(
                                    "message.factorynetwork.protection.denied").getString()));
                    return;
                }
                boolean accepted = controller.deploy(
                        new dev.devpanda.factorynetwork.lang.Project(packet.files()));
                // The outcome belongs in the editor and not only in the chat:
                // two of the reasons — no server rack, program too large —
                // the client does not know at all, because it cannot compile
                // them itself.
                String line = accepted
                        ? Component.translatable("message.factorynetwork.deploy.accepted",
                                controller.program().workers().size()).getString()
                        : controller.diagnostics().isEmpty()
                                ? Component.translatable(
                                        "message.factorynetwork.deploy.rejected", 0).getString()
                                : controller.diagnostics().get(0).message();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new DeployResultPacket(accepted, line));
                // The full list stays in the chat: a footer has room for one
                // message, not twelve.
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
