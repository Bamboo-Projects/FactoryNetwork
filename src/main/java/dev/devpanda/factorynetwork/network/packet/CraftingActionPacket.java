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
 * Einen Fertigungsauftrag abbrechen.
 *
 * <p>Die einzige Wahl, die es an einem Auftrag gibt. Pausieren wäre eine
 * zweite, aber ein pausierter Auftrag hält nichts fest — er wartet ohnehin,
 * sobald etwas fehlt, und der Unterschied wäre keiner.
 *
 * <p>Was schon gebaut wurde, bleibt im Speicher. Ein Abbruch nimmt nichts
 * zurück: Die Truhen sind Truhen, auch wenn niemand mehr auf sie wartet.
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
                // Derselbe Schutz wie beim Programm: Einen fremden Auftrag
                // abzubrechen ändert, was die Anlage tut — und das ist
                // Umbauen und nicht Benutzen.
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
