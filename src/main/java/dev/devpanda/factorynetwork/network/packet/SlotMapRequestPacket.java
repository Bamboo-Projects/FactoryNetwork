package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.network.SlotMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * "Which slot of this GUI is which?" — asked when a block GUI opens.
 *
 * <p>The block's position comes from the client, because a menu does not
 * know its block. The server checks that the menu is still the open one and
 * that the player stands near the block; the answer is only slot numbers and
 * sides of a block the player is looking into anyway.
 */
public record SlotMapRequestPacket(int containerId, BlockPos pos) implements CustomPacketPayload {

    public static final Type<SlotMapRequestPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "slot_map_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlotMapRequestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SlotMapRequestPacket::containerId,
                    BlockPos.STREAM_CODEC, SlotMapRequestPacket::pos,
                    SlotMapRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SlotMapRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, packetFor(player, packet));
        });
    }

    /** The whole answer: the map, and the connector label only if there is a map. */
    public static SlotMapPacket packetFor(net.minecraft.world.entity.player.Player player,
                                          SlotMapRequestPacket packet) {
        List<SlotMap.Entry> entries = answerFor(player, packet);
        String connector = entries.isEmpty() ? "" : SlotMap.connectorLabel(player.level(), packet.pos());
        return new SlotMapPacket(packet.containerId(), entries, connector);
    }

    /** The map for this player, or nothing if the question does not fit. */
    public static List<SlotMap.Entry> answerFor(net.minecraft.world.entity.player.Player player,
                                                SlotMapRequestPacket packet) {
        if (player.containerMenu.containerId != packet.containerId()
                || player.containerMenu == player.inventoryMenu
                || !player.level().isLoaded(packet.pos())
                || player.distanceToSqr(packet.pos().getCenter()) > SlotMap.REACH * SlotMap.REACH) {
            return List.of();
        }
        return SlotMap.of(player.containerMenu, player.level(), packet.pos());
    }
}
