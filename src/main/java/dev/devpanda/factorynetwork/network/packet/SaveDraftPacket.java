package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.lang.Project;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
public record SaveDraftPacket(BlockPos controller, Map<String, String> files)
        implements CustomPacketPayload {

    /** The server accepts no more than this per file. */
    private static final int MAX_LENGTH = 64 * 1024;

    public static final Type<SaveDraftPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "save_draft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveDraftPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveDraftPacket::controller,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(64),
                            ByteBufCodecs.stringUtf8(MAX_LENGTH)),
                    SaveDraftPacket::files,
                    SaveDraftPacket::new);

    public static SaveDraftPacket of(BlockPos controller, Project project) {
        return new SaveDraftPacket(controller, project.files());
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
            if (!menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
                return;
            }
            if (menu.controller(player).orElse(null)
                    instanceof ControllerBlockEntity controller) {
                // The same protection as for applying: an overwritten draft
                // is lost work, even if it never ran.
                if (!DeployProgramPacket.mayEdit(player, controller)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.factorynetwork.protection.denied"));
                    return;
                }
                controller.acceptDraft(new Project(packet.files()),
                        player.getUUID(), player.getGameProfile().getName());
            }
        });
    }
}
