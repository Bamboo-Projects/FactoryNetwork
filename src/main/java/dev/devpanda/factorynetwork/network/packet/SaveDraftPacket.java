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
 * Sichert den Entwurf auf dem Server.
 *
 * <p><b>Anders als {@link DeployProgramPacket} verlangt das keinen
 * fehlerfreien Code.</b> Genau darin liegt der Sinn: Übernehmen geht nur,
 * wenn das Programm übersetzt, und mitten in einer Änderung tut es das nie.
 * Wer zwanzig Minuten an einem Worker gebaut hat und dann abstürzt, hätte
 * ohne diesen Weg alles verloren.
 *
 * <p>Der Entwurf läuft nicht. Er wird nur aufgehoben — der laufende Stand
 * bleibt, was er war, bis jemand übernimmt. Ein Tippfehler hält die Fabrik
 * nicht an.
 *
 * <p>Geschickt wird kurz nach dem letzten Anschlag, nicht bei jedem. Und der
 * ganze Entwurf statt einer Änderungsliste: Ein Programm hier ist ein paar
 * Dutzend Zeilen, und eine Liste aus Positionen und Einfügungen ist die
 * Sorte Code, in der sich ein Fehler erst zeigt, wenn der Text schon kaputt
 * ist.
 */
public record SaveDraftPacket(BlockPos controller, Map<String, String> files)
        implements CustomPacketPayload {

    /** Mehr als das nimmt der Server je Datei nicht an. */
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
            // Dasselbe wie beim Übernehmen: Das offene Fenster ist die
            // Erlaubnis, nicht die Koordinate im Paket. Ein Entwurf ist Code,
            // und Code kann das Wireless Terminal nicht.
            if (!(player.containerMenu instanceof dev.devpanda.factorynetwork.client.menu
                    .TerminalMenu menu)
                    || !menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            if (menu.controller(player).orElse(null)
                    instanceof ControllerBlockEntity controller) {
                // Derselbe Schutz wie beim Übernehmen: Ein überschriebener
                // Entwurf ist verlorene Arbeit, auch wenn er nie lief.
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
