package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Saves the draft on the server.
 *
 * <p><b>Unlike {@link DeployProgramPacket}, this demands no error-free
 * code.</b> That is precisely the point: applying only works when the program
 * compiles, and in the middle of a change it never does. Whoever has spent
 * twenty minutes building a worker and then crashes would, without this path,
 * have lost everything.
 *
 * <p>The draft does not run. It is only kept — the running state stays what it
 * was until someone applies. A typo does not stop the factory.
 *
 * <p>It is sent shortly after the last keystroke, not on every one. And the
 * whole draft instead of a change list: a program here is a few dozen lines,
 * and a list of positions and insertions is the kind of code where a bug only
 * shows once the text is already broken.
 */
public record SaveDraftPacket(BlockPos controller, Map<String, String> files, long sequence)
        implements CustomPacketPayload {

    /** The server accepts no more than this per file. */
    private static final int MAX_LENGTH = 64 * 1024;

    public static final Type<SaveDraftPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "save_draft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveDraftPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveDraftPacket::controller,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(Project.MAX_NAME),
                            ByteBufCodecs.stringUtf8(MAX_LENGTH)),
                    SaveDraftPacket::files,
                    ByteBufCodecs.VAR_LONG, SaveDraftPacket::sequence,
                    SaveDraftPacket::new);

    public static SaveDraftPacket of(BlockPos controller, Project project) {
        return of(controller, project, 0);
    }

    public static SaveDraftPacket of(BlockPos controller, Project project, long sequence) {
        return new SaveDraftPacket(controller, project.files(), sequence);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveDraftPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Same as for applying: the open window is the permission, not the
            // coordinate in the packet. A draft is code, and the Wireless
            // Terminal cannot do code.
            if (!(player.containerMenu instanceof dev.devpanda.factorynetwork.client.menu
                    .TerminalMenu menu)
                    || !menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            if (menu.controller(player).orElse(null)
                    instanceof ControllerBlockEntity controller) {
                // The same protection as for applying: an overwritten draft
                // is lost work, even if it never ran.
                if (!DeployProgramPacket.mayEdit(player, controller)) {
                    reply(packet, player, controller, false, "Keine Berechtigung zum Speichern. Der Text bleibt lokal erhalten.");
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.factorynetwork.protection.denied"));
                    return;
                }
                if (!menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
                    reply(packet, player, controller, false, "Nicht genug Energie zum Speichern. Der Text bleibt lokal erhalten.");
                    return;
                }
                controller.acceptDraft(new Project(packet.files()),
                        player.getUUID(), player.getGameProfile().getName());
                boolean accepted = controller.draft().files().equals(packet.files());
                reply(packet, player, controller, accepted,
                        accepted ? "" : "Ein Teil des Entwurfs ist durch eine Dateisperre geschützt. Bitte die Änderungen abgleichen.");
            }
        });
    }

    private static void reply(SaveDraftPacket request, ServerPlayer player,
                              ControllerBlockEntity controller, boolean accepted, String message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new DraftSavedPacket(request.sequence(), accepted, message,
                        ProjectStatePacket.of(controller.getBlockPos(), controller.project(),
                                controller.draft(), controller.locksFor(player.getUUID()))));
    }
}
