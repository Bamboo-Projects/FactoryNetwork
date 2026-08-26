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
 * Ein Block bekommt einen Namen — aus dem Fenster am Block.
 *
 * <p><b>Geprüft wird auf dem Server</b>, mit denselben Regeln wie bei der
 * Beschriftungspistole: Ein Name muss ein Bezeichner sein, und zwei Geräte
 * mit demselben Namen machen beide unbrauchbar. Der Client wiederholt die
 * Prüfung nur, um früh zu warnen; verlassen darf man sich darauf nicht.
 */
public record SetBlockNamePacket(BlockPos position, int side, String name)
        implements CustomPacketPayload {

    /** Kein Anschluss an einer Fläche, sondern ein ganzer Block. */
    public static final int NO_SIDE = -1;

    /** So weit darf der Block weg sein — dasselbe wie im Fenster. */
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
            // Der Client sagt, welcher Block gemeint ist. Ohne diese Prüfung
            // ließe sich jeder Block auf der Karte umbenennen.
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > REACH * REACH) {
                return;
            }
            String wanted = ConnectorNaming.normalize(packet.name());
            // Sagt der Klick eine Fläche, gilt sie: An einem Kabelblock
            // hängen bis zu sechs Anschlüsse, und der Ort allein benennt
            // irgendeinen davon.
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
     * Eine Anzeige heißt nie allein.
     *
     * <p>Der Name gehört der Wand, nicht der Tafel — wer eine Wand
     * beschriftet, hat die Wand beschriftet und nicht die eine Tafel, die er
     * getroffen hat.
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
     * Ein Connector prüft zusätzlich, ob der Name schon vergeben ist.
     *
     * <p>Dieselbe Prüfung wie die Pistole, und aus demselben Grund: Zwei
     * Geräte mit demselben Namen lassen sich beide nicht mehr ansprechen.
     * Anders als die Pistole schlägt das Fenster nichts vor — wer tippt,
     * bekommt gesagt, was nicht geht, und tippt weiter.
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
            // Ein Schlüsselwort ist erlaubt; im Code braucht es Rückstriche.
            case KEYWORD -> say(player, "message.factorynetwork.label_gun.keyword", wanted);
            default -> { }
        }
        connector.setLabel(wanted);
        say(player, "message.factorynetwork.connector.named", wanted);
    }

    /**
     * Ein Gateway trägt einen Anlagennamen und keine Rolle.
     *
     * <p>Deshalb ohne Schrägstrich und ohne die Frage, ob der Name schon
     * vergeben ist: Zwei Gateways mit demselben Namen sind kein Fehler,
     * sondern zwei Teile derselben Anlage. Erst wenn beide dasselbe Gerät
     * beanspruchen, gehört es zu keiner — und das meldet der Reiter
     * <i>Netz</i>.
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
