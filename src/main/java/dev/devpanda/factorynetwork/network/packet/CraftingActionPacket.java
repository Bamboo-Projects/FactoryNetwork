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
 * Cancel a crafting job.
 *
 * <p>The only choice there is on a job. Pausing would be a second one, but a
 * paused job holds nothing back — it waits anyway as soon as something is
 * missing, and the difference would be none.
 *
 * <p>What has already been built stays in storage. A cancellation takes
 * nothing back: the chests are chests, even if no one is waiting on them
 * anymore.
 */
public record CraftingActionPacket(long id) implements CustomPacketPayload {

    public static final Type<CraftingActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "crafting_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, CraftingActionPacket::id,
                    CraftingActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftingActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            if (!menu.charge(player, dev.devpanda.factorynetwork.network.Power.REMOTE_ACTION)) {
                return;
            }
            menu.controller(player).ifPresent(controller -> {
                // The same protection as for the program: cancelling someone
                // else's job changes what the plant does — and that is
                // rebuilding, not using.
                if (!DeployProgramPacket.mayEdit(player, controller)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.factorynetwork.protection.denied"));
                    return;
                }
                controller.cancelCraft(packet.id());
                controller.pushCraftingTo(player);
            });
        });
    }
}
