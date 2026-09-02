package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.item.ConnectorNaming;
import dev.devpanda.factorynetwork.network.ControllerRegistry;
import dev.devpanda.factorynetwork.network.FactoryGraph;
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
 * A block gets a name — from the window on the block.
 *
 * <p><b>The check happens on the server</b>, with the same rules as for the
 * label gun: a name must be an identifier, and two devices with the same name
 * make both unusable. The client repeats the check only to warn early; you may
 * not rely on it.
 */
public record SetBlockNamePacket(BlockPos position, int side, String name)
        implements CustomPacketPayload {

    /** Not a connector on a face, but a whole block. */
    public static final int NO_SIDE = -1;

    /** This is how far away the block may be — the same as in the window. */
    private static final double REACH = 8.0;

    public static final Type<SetBlockNamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "set_block_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBlockNamePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetBlockNamePacket::position,
                    ByteBufCodecs.VAR_INT, SetBlockNamePacket::side,
                    ByteBufCodecs.stringUtf8(64), SetBlockNamePacket::name,
                    SetBlockNamePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetBlockNamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = packet.position();
            // The client says which block is meant. Without this check, any
            // block on the map could be renamed.
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > REACH * REACH) {
                return;
            }
            String wanted = ConnectorNaming.normalize(packet.name());
            // If the click names a face, it holds: a cable block has up to six
            // connectors hanging off it, and the location alone would name
            // just any one of them.
            if (packet.side() != NO_SIDE) {
                var part = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                        player.level(), pos,
                        net.minecraft.core.Direction.from3DDataValue(packet.side()));
                if (part != null) {
                    nameConnector(part, wanted, player);
                }
                return;
            }
            var entity = player.level().getBlockEntity(pos);
            if (entity instanceof dev.devpanda.factorynetwork.block.entity
                    .GatewayBlockEntity gateway) {
                nameGateway(gateway, wanted, player);
                return;
            }
            if (entity instanceof DisplayBlockEntity display) {
                nameWall(display, wanted, player);
                return;
            }
            var single = dev.devpanda.factorynetwork.block.entity.Connectors.at(
                    player.level(), pos);
            if (single != null) {
                nameConnector(single, wanted, player);
            }
        });
    }

    /**
     * A display is never named alone.
     *
     * <p>The name belongs to the wall, not the panel — whoever labels a wall
     * has labelled the wall and not the one panel they happened to hit.
     */
    private static void nameWall(DisplayBlockEntity display, String wanted,
                                 ServerPlayer player) {
        if (!wanted.isEmpty() && !ConnectorNaming.isValidDeviceName(wanted)) {
            say(player, "message.factorynetwork.label_gun.invalid", wanted);
            return;
        }
        for (BlockPos member : display.wall().members()) {
            if (player.level().getBlockEntity(member) instanceof DisplayBlockEntity panel) {
                panel.setDisplayName(wanted);
            }
        }
        say(player, wanted.isEmpty()
                ? "message.factorynetwork.display.unnamed"
                : "message.factorynetwork.display.named", wanted);
    }

    /**
     * A connector additionally checks whether the name is already taken.
     *
     * <p>The same check as the gun, and for the same reason: two devices with
     * the same name can neither be addressed anymore. Unlike the gun, the
     * window suggests nothing — whoever types is told what does not work, and
     * types on.
     */
    private static void nameConnector(
            dev.devpanda.factorynetwork.block.entity.ConnectorPart connector, String wanted,
            ServerPlayer player) {
        if (wanted.isEmpty()) {
            connector.setLabel("");
            say(player, "message.factorynetwork.connector.unnamed");
            return;
        }
        FactoryGraph graph = ControllerRegistry.owning(player.level(), connector.pos())
                .map(controller -> controller.graph())
                .orElse(null);
        ConnectorNaming.Warning warning = ConnectorNaming.check(wanted, graph);
        switch (warning.kind()) {
            case TAKEN -> {
                say(player, "message.factorynetwork.label_gun.taken",
                        wanted, warning.suggestion());
                return;
            }
            case NOT_AN_IDENTIFIER -> {
                say(player, "message.factorynetwork.label_gun.invalid", wanted);
                return;
            }
            // A keyword is allowed; in code it needs backticks.
            case KEYWORD -> say(player, "message.factorynetwork.label_gun.keyword", wanted);
            default -> { }
        }
        connector.setLabel(wanted);
        say(player, "message.factorynetwork.connector.named", wanted);
    }

    /**
     * A gateway carries a plant name and no role.
     *
     * <p>Hence without a slash and without the question of whether the name is
     * already taken: two gateways with the same name are not a mistake, but
     * two parts of the same plant. Only when both claim the same device does it
     * belong to none — and that is reported by the <i>Network</i> tab.
     */
    private static void nameGateway(dev.devpanda.factorynetwork.block.entity
            .GatewayBlockEntity gateway, String wanted, ServerPlayer player) {
        if (wanted.isEmpty()) {
            gateway.setInstance("");
            say(player, "message.factorynetwork.gateway.unnamed");
            return;
        }
        if (!ConnectorNaming.isValidIdentifier(wanted)) {
            say(player, "message.factorynetwork.label_gun.invalid", wanted);
            return;
        }
        gateway.setInstance(wanted);
        say(player, "message.factorynetwork.gateway.named", wanted);
    }

    private static void say(ServerPlayer player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable(key, arguments), true);
    }
}
