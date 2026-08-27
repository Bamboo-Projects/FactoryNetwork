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
 * Übergibt das Projekt aus dem Editor an den Controller.
 *
 * <p>Alle Dateien auf einmal und nicht die offene allein: Wer eine Datei
 * anlegt und dann eine andere übernimmt, hätte sonst die neue verloren.
 * Lang, aber selten — geschickt wird es nur, wenn jemand „Übernehmen"
 * drückt.
 */
public record DeployProgramPacket(BlockPos terminal, Map<String, String> files)
        implements CustomPacketPayload {

    /** Mehr als das nimmt der Server je Datei nicht an. */
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

    /** Für alles, was nur einen Text hat. */
    public static DeployProgramPacket of(BlockPos terminal,
                                         dev.devpanda.factorynetwork.lang.Project project) {
        return new DeployProgramPacket(terminal, project.files());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Darf dieser Spieler hier etwas ändern?
     *
     * <p>Die Rechnung steht in {@code FnProtection}; hier stehen nur die
     * Angaben, die es dafür braucht. Operatoren dürfen immer — sonst hätte
     * ein Server niemanden, der eine verwaiste Anlage wieder in Gang bringt.
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
            // Nur wer ein Terminalfenster offen hat, darf übernehmen —
            // und nur, wenn es den Code überhaupt zeigt.
            //
            // Früher stand hier ein Abstand zu der Position, die im Paket
            // stand. Das trug, solange man dafür vor einem Block stehen
            // musste. Mit dem Laptop steht man nirgends mehr davor, und die
            // Koordinate im Paket ist eine Behauptung des Clients. Das
            // offene Fenster dagegen tickt der Server selbst: Läuft der
            // Spieler aus der Reichweite, geht es zu, und hier kommt nichts
            // mehr an.
            if (!(player.containerMenu instanceof dev.devpanda.factorynetwork.client.menu
                    .TerminalMenu menu)
                    || !menu.allows(dev.devpanda.factorynetwork.terminal.TerminalTab.CODE)) {
                return;
            }
            menu.controller(player).ifPresentOrElse(controller -> {
                // Erst die Erlaubnis, dann der Übersetzer: Wer nicht darf,
                // soll das erfahren und nicht erst seine Tippfehler.
                if (!mayEdit(player, controller)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new DeployResultPacket(false, Component.translatable(
                                    "message.factorynetwork.protection.denied").getString()));
                    return;
                }
                boolean accepted = controller.deploy(
                        new dev.devpanda.factorynetwork.lang.Project(packet.files()));
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
